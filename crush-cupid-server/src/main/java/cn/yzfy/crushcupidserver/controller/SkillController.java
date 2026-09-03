package cn.yzfy.crushcupidserver.controller;

import cn.yzfy.crushcupidserver.common.Result;
import cn.yzfy.crushcupidserver.exception.BizException;
import cn.yzfy.crushcupidserver.model.entity.CrushReport;
import cn.yzfy.crushcupidserver.service.CrushReportService;
import cn.yzfy.crushcupidserver.model.vo.CrushReportVO;
import cn.yzfy.crushcupidserver.model.vo.SkillCatalogVO;
import cn.yzfy.crushcupidserver.model.vo.SkillMetaVO;
import cn.yzfy.crushcupidserver.skill.SkillAdvisorService;
import cn.yzfy.crushcupidserver.skill.SkillCatalogService;
import cn.yzfy.crushcupidserver.skill.SkillMeta;
import cn.yzfy.crushcupidserver.skill.SkillReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Skill 目录接口：暴露远端 GitHub skill 的元信息与可用 prompt。
 * <p>
 * 另承载「军师模式」子命令列表/调用，以及「关系报告」生成、下载与历史管理。
 */
@RestController
@RequestMapping("/api/skill")
@RequiredArgsConstructor
public class SkillController {

    private final SkillCatalogService skillCatalogService;
    private final SkillAdvisorService skillAdvisorService;
    private final SkillReportService skillReportService;
    private final CrushReportService crushReportService;

    @GetMapping("/catalog")
    public Result<SkillCatalogVO> catalog() {
        SkillMeta meta = skillCatalogService.getSkillMeta();
        SkillMetaVO metaVO = new SkillMetaVO();
        BeanUtils.copyProperties(meta, metaVO);

        SkillCatalogVO vo = new SkillCatalogVO();
        vo.setSkill(metaVO);
        vo.setPrompts(skillCatalogService.listPrompts());
        return Result.ok(vo);
    }

    @GetMapping("/prompt/{name}")
    public Result<String> prompt(@PathVariable String name) {
        return Result.ok(skillCatalogService.getPrompt(name));
    }

    /** 军师模式子命令列表（供前端渲染子命令卡片，含触发词/说明） */
    @GetMapping("/advisor")
    public Result<List<SkillAdvisorService.AdvisorDescriptor>> advisorCommands() {
        return Result.ok(skillAdvisorService.listDescriptors());
    }

    /** 调用军师子命令，让 LLM 以军师角色回应；requiresCrush 子命令（report）需 crushSlug */
    @PostMapping("/advisor/invoke")
    public Result<String> invokeAdvisor(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String question = body.get("question");
        String crushSlug = body.get("crushSlug");

        SkillAdvisorService.AdvisorDescriptor desc = skillAdvisorService.getDescriptor(name);
        if (desc != null && desc.requiresCrush()) {
            return Result.ok(skillReportService.generate(crushSlug));
        }
        return Result.ok(skillAdvisorService.invoke(name, question, null));
    }

    /** 生成关系报告（Markdown）并落库，供前端预览；返回含 id 的报告详情 */
    @PostMapping("/advisor/report")
    public Result<CrushReportVO> generateReport(@RequestBody Map<String, String> body) {
        String crushSlug = body.get("crushSlug");
        if (crushSlug == null || crushSlug.isBlank()) {
            throw BizException.badRequest("缺少 crushSlug");
        }
        CrushReport report = skillReportService.generateAndSave(crushSlug, "manual");
        return Result.ok(toVO(report, true));
    }

    /** 某暗恋对象的关系报告历史（列表，新→旧） */
    @GetMapping("/report/list")
    public Result<List<CrushReportVO>> listReports(@RequestParam String crushSlug) {
        var crush = skillReportService.lookupCrush(crushSlug);
        return Result.ok(crushReportService.listByCrushId(crush.getId())
                .stream().map(r -> toVO(r, false)).toList());
    }

    /** 报告详情（含 markdown 全文） */
    @GetMapping("/report/{id}")
    public Result<CrushReportVO> reportDetail(@PathVariable Long id) {
        CrushReport report = crushReportService.getById(id);
        if (report == null) {
            throw BizException.notFound("报告不存在：" + id);
        }
        return Result.ok(toVO(report, true));
    }

    /** 删除一条报告历史 */
    @DeleteMapping("/report/{id}")
    public Result<Void> deleteReport(@PathVariable Long id) {
        crushReportService.removeById(id);
        return Result.ok();
    }

    /**
     * 下载某条已保存报告 .docx（读库中的 markdown，不再调用 LLM）。
     */
    @GetMapping("/report/{id}/download")
    public ResponseEntity<byte[]> downloadSaved(@PathVariable Long id) {
        CrushReport report = crushReportService.getById(id);
        if (report == null) {
            throw BizException.notFound("报告不存在：" + id);
        }
        byte[] bytes = skillReportService.toDocx(report.getMarkdown());
        String date = report.getReportDate() == null ? "" : report.getReportDate().toString();
        String filename = "关系报告_" + escapeFilename(report.getCrushName() == null ? "" : report.getCrushName())
                + (date.isBlank() ? "" : "_" + date) + ".docx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(bytes);
    }

    /**
     * 下载关系报告 .docx。
     * crushSlug 必填；可选 md 参数直接使用已生成的 Markdown（避免重复调用 LLM），否则现场生成。
     */
    @GetMapping("/advisor/report/download")
    public ResponseEntity<byte[]> downloadReport(@RequestParam String crushSlug,
                                                 @RequestParam(required = false) String md) {
        String markdown = (md == null || md.isBlank()) ? skillReportService.generate(crushSlug) : md;
        byte[] bytes = skillReportService.toDocx(markdown);
        String filename = "关系报告_" + escapeFilename(crushSlug) + "_"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".docx";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(bytes);
    }

    private CrushReportVO toVO(CrushReport report, boolean withMarkdown) {
        CrushReportVO vo = new CrushReportVO();
        BeanUtils.copyProperties(report, vo);
        if (!withMarkdown) {
            vo.setMarkdown(null);
        }
        return vo;
    }

    private String escapeFilename(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
