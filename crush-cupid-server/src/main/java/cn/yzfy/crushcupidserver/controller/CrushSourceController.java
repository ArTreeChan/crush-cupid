package cn.yzfy.crushcupidserver.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.yzfy.crushcupidserver.agent.CrushBuildService;
import cn.yzfy.crushcupidserver.agent.OcrService;
import cn.yzfy.crushcupidserver.agent.SourceAnalysisService;
import cn.yzfy.crushcupidserver.common.DocumentTextExtractor;
import cn.yzfy.crushcupidserver.common.Result;
import cn.yzfy.crushcupidserver.common.TextExtractor;
import cn.yzfy.crushcupidserver.exception.BizException;
import cn.yzfy.crushcupidserver.model.dto.SourceImportDTO;
import cn.yzfy.crushcupidserver.model.entity.ChatSource;
import cn.yzfy.crushcupidserver.model.enums.SourceType;
import cn.yzfy.crushcupidserver.service.ChatSourceService;
import cn.yzfy.crushcupidserver.service.CrushService;
import cn.yzfy.crushcupidserver.service.CrushVersionService;
import cn.yzfy.crushcupidserver.model.vo.BuildEventVO;
import cn.yzfy.crushcupidserver.model.vo.BuildResultVO;
import cn.yzfy.crushcupidserver.model.vo.SourceVO;
import cn.yzfy.crushcupidserver.model.vo.VersionVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * crush 生命周期子资源：原材料导入（文本 / 文件）、构建、版本历史。
 */
@Slf4j
@RestController
@RequestMapping("/api/crush/{crushId}")
@RequiredArgsConstructor
public class CrushSourceController {

    private final CrushService crushService;
    private final ChatSourceService chatSourceService;
    private final CrushVersionService crushVersionService;
    private final CrushBuildService crushBuildService;
    private final OcrService ocrService;
    private final SourceAnalysisService sourceAnalysisService;
    private final ObjectMapper objectMapper;

    @PostMapping("/sources")
    public Result<SourceVO> importSource(@PathVariable Long crushId, @RequestBody SourceImportDTO dto) {
        requireCrush(crushId);
        if (StrUtil.isBlank(dto.getContent())) {
            throw BizException.badRequest("内容不能为空");
        }
        ChatSource source = new ChatSource();
        source.setCrushId(crushId);
        BeanUtil.copyProperties(dto, source);
        long updated = System.currentTimeMillis();
        String raw = TextExtractor.sanitize(dto.getContent());
        source.setContent(raw);
        source.setFileName(StrUtil.blankToDefault(dto.getFileName(), null));
        source.setFileType(dto.getType() == null ? SourceType.TEXT.name() : dto.getType().name());
        source.setMessageCount(0);
        source.setCreatedAt(new Date());
        source.setRawAnalysis(sourceAnalysisService.analyze(crushService.getById(crushId), source.getFileName(), raw));
        source.setParsedAt(new Date(updated));
        chatSourceService.save(source);
        return Result.ok(toSourceVO(source));
    }

    @PostMapping("/sources/upload")
    public Result<SourceVO> uploadSource(@PathVariable Long crushId,
                                         @RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "type", required = false) String type) {
        requireCrush(crushId);
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("文件不能为空");
        }
        boolean image = isImage(file);
        String content;
        try {
            if (image && ocrService.available()) {
                // 图片 + OCR 已配置：走百炼 MCP OCR 提取文字
                content = ocrService.recognize(readFileBytes(file));
            } else {
                // 文档（pdf/docx）走专用解析，其余按文本文件读取
                content = readFileContent(file);
            }
        } catch (Exception e) {
            throw new BizException("解析文件失败：" + e.getMessage());
        }
        String fileName = file.getOriginalFilename();
        String rawContent = TextExtractor.sanitize(content);
        ChatSource source = new ChatSource();
        source.setCrushId(crushId);
        source.setFileType(StrUtil.blankToDefault(type,
                image ? SourceType.PHOTO.name() : inferType(fileName)));
        source.setFileName(fileName);
        source.setContent(rawContent);
        source.setMessageCount(0);
        source.setCreatedAt(new Date());
        long parsedAt = System.currentTimeMillis();
        source.setRawAnalysis(sourceAnalysisService.analyze(crushService.getById(crushId), fileName, rawContent));
        source.setParsedAt(new Date(parsedAt));
        chatSourceService.save(source);
        return Result.ok(toSourceVO(source));
    }

    /** 判断上传文件是否图片（contentType 或扩展名） */
    private boolean isImage(MultipartFile file) {
        String ct = file.getContentType();
        if (ct != null && ct.toLowerCase().startsWith("image/")) {
            return true;
        }
        String name = file.getOriginalFilename();
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".bmp") || lower.endsWith(".gif") || lower.endsWith(".tiff")
                || lower.endsWith(".webp");
    }

    /** 读取文件原始字节（OCR 用） */
    private byte[] readFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BizException("读取文件失败：" + e.getMessage());
        }
    }

    @GetMapping("/sources")
    public Result<List<SourceVO>> listSources(@PathVariable Long crushId) {
        requireCrush(crushId);
        return Result.ok(chatSourceService.listByCrushId(crushId).stream().map(this::toSourceVO).toList());
    }

    @DeleteMapping("/sources/{sourceId}")
    public Result<Void> deleteSource(@PathVariable Long crushId, @PathVariable Long sourceId) {
        chatSourceService.removeById(sourceId);
        return Result.ok();
    }

    @PostMapping(value = "/build", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter build(@PathVariable Long crushId) {
        requireCrush(crushId);
        SseEmitter emitter = new SseEmitter(300_000L);
        // 防重复 complete/回调导致的 SseEmitter 二次发送异常
        java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean(false);
        Runnable finish = () -> {
            if (finished.compareAndSet(false, true)) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // 已关闭
                }
            }
        };
        emitter.onCompletion(finish);
        emitter.onTimeout(() -> {
            log.warn("构建 SSE 客户端超时，释放连接 crushId={}", crushId);
            finish.run();
        });
        emitter.onError(e -> {
            log.warn("构建 SSE 客户端异常，释放连接 crushId={} err={}", crushId, e.getMessage());
            finish.run();
        });

        // 后台线程执行，避免阻塞请求线程；通过 SSE 推进度 + 最终结果。
        CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
            try {
                BuildResultVO result = crushBuildService.build(crushId, msg -> sendEvent(emitter, progress(msg)));
                sendEvent(emitter, done(result));
                finish.run();
            } catch (Exception e) {
                sendEvent(emitter, error(e.getMessage() == null ? "构建失败" : e.getMessage()));
                finish.run();
            }
        });
        // 兜底超时：后台构建超过 300s 也主动释放连接，避免客户端永久挂起。
        task.orTimeout(300_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    if (!finished.get()) {
                        log.warn("构建耗时超过 300s，中断 SSE crushId={} err={}", crushId, ex.getMessage());
                        sendEvent(emitter, error("构建超时"));
                        finish.run();
                    }
                    return null;
                });
        return emitter;
    }

    @GetMapping("/versions")
    public Result<List<VersionVO>> listVersions(@PathVariable Long crushId) {
        requireCrush(crushId);
        List<VersionVO> list = crushVersionService.listByCrushId(crushId).stream().map(v -> {
            VersionVO vo = new VersionVO();
            BeanUtils.copyProperties(v, vo);
            return vo;
        }).toList();
        return Result.ok(list);
    }

    private void requireCrush(Long crushId) {
        if (crushService.getById(crushId) == null) {
            throw BizException.notFound("未找到暗恋对象 id=" + crushId);
        }
    }

    private SourceVO toSourceVO(ChatSource s) {
        SourceVO vo = new SourceVO();
        BeanUtils.copyProperties(s, vo);
        vo.setType(s.getFileType());
        vo.setAnalysis(s.getRawAnalysis());
        return vo;
    }

    private String inferType(String filename) {
        if (filename == null) {
            return SourceType.TEXT.name();
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".json") || lower.endsWith(".html") || lower.endsWith(".htm")
                || lower.endsWith(".csv")) {
            return SourceType.WECHAT.name();
        }
        return SourceType.TEXT.name();
    }

    /**
     * 读取非图片文件内容：pdf/docx 走 {@link DocumentTextExtractor} 专用解析，
     * 其余文本文件委托 {@link TextExtractor} 按 BOM 检测编码并清洗 NUL 字节。
     */
    private String readFileContent(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String name = file.getOriginalFilename();
        if (name != null && (name.toLowerCase().endsWith(".pdf")
                || name.toLowerCase().endsWith(".docx"))) {
            return TextExtractor.sanitize(DocumentTextExtractor.extract(name, bytes));
        }
        return TextExtractor.extract(bytes);
    }

    private void sendEvent(SseEmitter emitter, BuildEventVO event) {
        try {
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(event)));
        } catch (Exception ignored) {
            // 客户端断开，忽略
        }
    }

    private BuildEventVO progress(String message) {
        BuildEventVO vo = new BuildEventVO();
        vo.setType("progress");
        vo.setMessage(message);
        return vo;
    }

    private BuildEventVO done(BuildResultVO result) {
        BuildEventVO vo = new BuildEventVO();
        vo.setType("done");
        vo.setResult(result);
        return vo;
    }

    private BuildEventVO error(String message) {
        BuildEventVO vo = new BuildEventVO();
        vo.setType("error");
        vo.setMessage(message);
        return vo;
    }
}
