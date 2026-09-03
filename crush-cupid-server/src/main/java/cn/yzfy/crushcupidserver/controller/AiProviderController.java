package cn.yzfy.crushcupidserver.controller;

import cn.hutool.core.util.StrUtil;
import cn.yzfy.crushcupidserver.common.Result;
import cn.yzfy.crushcupidserver.config.ChatClientProvider;
import cn.yzfy.crushcupidserver.config.ChatModelRegistry;
import cn.yzfy.crushcupidserver.config.VoiceProviderRegistry;
import cn.yzfy.crushcupidserver.exception.BizException;
import cn.yzfy.crushcupidserver.model.converter.AiProviderConverter;
import cn.yzfy.crushcupidserver.model.dto.AiProviderDTO;
import cn.yzfy.crushcupidserver.model.entity.AiProvider;
import cn.yzfy.crushcupidserver.service.AiProviderService;
import cn.yzfy.crushcupidserver.model.vo.AiProviderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * @className AiProviderController
 * @description 自定义大模型 API 供应商管理（运行时增删改查）。
 * 每次变更后刷新 {@link ChatModelRegistry} 与 {@link ChatClientProvider}，立即生效，无需重启。
 * @author crush-cupid
 * @code controller
 * @createTime 2026-08-31
 */
@RestController
@RequestMapping("/api/ai-provider")
@RequiredArgsConstructor
@Slf4j
public class AiProviderController {

    private final AiProviderService aiProviderService;
    private final ChatModelRegistry chatModelRegistry;
    private final ChatClientProvider chatClientProvider;
    private final VoiceProviderRegistry voiceProviderRegistry;

    @GetMapping
    public Result<List<AiProviderVO>> list() {
        List<AiProviderVO> list = aiProviderService.list().stream()
                .map(AiProviderConverter::toVO)
                .toList();
        return Result.ok(list);
    }

    @GetMapping("/{id}")
    public Result<AiProviderVO> get(@PathVariable Long id) {
        AiProvider p = aiProviderService.getById(id);
        if (p == null) {
            throw BizException.notFound("未找到自定义供应商 id=" + id);
        }
        return Result.ok(AiProviderConverter.toVO(p));
    }

    @PostMapping
    public Result<AiProviderVO> create(@RequestBody AiProviderDTO dto) {
        validate(dto);
        // 语音大模型新建时自动生成 name 和 providerKey（用户只需填 API KEY）
        if ("voice".equals(dto.getType())) {
            if (StrUtil.isBlank(dto.getProviderKey())) {
                dto.setProviderKey("voice-" + System.currentTimeMillis());
            }
            if (StrUtil.isBlank(dto.getName())) {
                dto.setName("语音供应商 " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
            }
        }
        if (aiProviderService.getByProviderKey(dto.getProviderKey()) != null) {
            throw BizException.badRequest("供应商代号已存在：" + dto.getProviderKey());
        }
        AiProvider entity = AiProviderConverter.toEntity(dto);
        // type 为空默认 chat（对话大模型）
        if (StrUtil.isBlank(entity.getType())) {
            entity.setType("chat");
        }
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            clearDefaultFlags(entity.getId(), entity.getType());
        }
        aiProviderService.save(entity);
        refresh();
        return Result.ok(AiProviderConverter.toVO(entity));
    }

    @PutMapping("/{id}")
    public Result<AiProviderVO> update(@PathVariable Long id, @RequestBody AiProviderDTO dto) {
        AiProvider entity = aiProviderService.getById(id);
        if (entity == null) {
            throw BizException.notFound("未找到自定义供应商 id=" + id);
        }
        // 如修改了 providerKey，需校验唯一（排除自身）
        if (StrUtil.isNotBlank(dto.getProviderKey())
                && !dto.getProviderKey().equals(entity.getProviderKey())
                && aiProviderService.getByProviderKey(dto.getProviderKey()) != null) {
            throw BizException.badRequest("供应商代号已存在：" + dto.getProviderKey());
        }
        performValidation(entity, dto);
        // 更新时如果传了 type 则以传入为准，否则保留原值
        String effectiveType = StrUtil.isNotBlank(dto.getType()) ? dto.getType() : entity.getType();
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            clearDefaultFlags(id, effectiveType);
        }
        AiProviderConverter.apply(entity, dto);
        entity.setUpdatedAt(new Date());
        aiProviderService.updateById(entity);
        refresh();
        return Result.ok(AiProviderConverter.toVO(entity));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        aiProviderService.removeById(id);
        refresh();
        return Result.ok();
    }

    /** 把同类型其它供应商的 is_default 清零（对话/语音各自独立一个默认） */
    private void clearDefaultFlags(Long excludeId, String type) {
        aiProviderService.lambdaUpdate()
                .eq(AiProvider::getIsDefault, true)
                .eq(AiProvider::getType, type)
                .ne(AiProvider::getId, excludeId)
                .set(AiProvider::getIsDefault, false)
                .update();
    }

    private void validate(AiProviderDTO dto) {
        boolean isVoice = "voice".equals(dto.getType());
        if (isVoice) {
            // 语音大模型只填 API KEY，name / providerKey / baseUrl / model 均不校验（自动生成或从 yml 读）
            if (StrUtil.isBlank(dto.getApiKey())) {
                throw BizException.badRequest("apiKey 不能为空");
            }
            return;
        }
        if (StrUtil.isBlank(dto.getName())) {
            throw BizException.badRequest("name 不能为空");
        }
        if (StrUtil.isBlank(dto.getProviderKey())) {
            throw BizException.badRequest("providerKey（供应商代号）不能为空");
        }
        if (StrUtil.isBlank(dto.getBaseUrl())) {
            throw BizException.badRequest("baseUrl 不能为空");
        }
        if (StrUtil.isBlank(dto.getModel())) {
            throw BizException.badRequest("model 不能为空");
        }
    }

    /** 更新时：只对将要生效的字段做非空校验，允许部分更新 */
    private void performValidation(AiProvider entity, AiProviderDTO dto) {
        // 语音大模型：只校验 apiKey，baseUrl / model 从 yml 读，允许为空
        String effectiveType = StrUtil.isNotBlank(dto.getType()) ? dto.getType() : entity.getType();
        if ("voice".equals(effectiveType)) {
            if (dto.getApiKey() != null && dto.getApiKey().isBlank()) {
                throw BizException.badRequest("apiKey 不能为空");
            }
            if (dto.getApiKey() == null && StrUtil.isBlank(entity.getApiKey())) {
                throw BizException.badRequest("apiKey 不能为空");
            }
            return;
        }
        if (dto.getName() != null && dto.getName().isBlank()) {
            throw BizException.badRequest("name 不能为空");
        }
        if (dto.getProviderKey() != null && dto.getProviderKey().isBlank()) {
            throw BizException.badRequest("providerKey 不能为空");
        }
        if (dto.getBaseUrl() != null && dto.getBaseUrl().isBlank()) {
            throw BizException.badRequest("baseUrl 不能为空");
        }
        if (dto.getModel() != null && dto.getModel().isBlank()) {
            throw BizException.badRequest("model 不能为空");
        }
        // 若某必填字段本次不提供修改，则实体上必须已存在
        if (dto.getBaseUrl() == null && StrUtil.isBlank(entity.getBaseUrl())) {
            throw BizException.badRequest("baseUrl 不能为空");
        }
        if (dto.getModel() == null && StrUtil.isBlank(entity.getModel())) {
            throw BizException.badRequest("model 不能为空");
        }
    }

    /** 变更后刷新对话注册表、语音注册表与 ChatClient 缓存，即时生效 */
    private void refresh() {
        try {
            chatModelRegistry.reload();
        } catch (IllegalStateException e) {
            log.warn("供应商变更后对话注册表重建失败：{}", e.getMessage());
            throw BizException.badRequest(e.getMessage());
        }
        voiceProviderRegistry.reload();
        chatClientProvider.refresh();
    }
}
