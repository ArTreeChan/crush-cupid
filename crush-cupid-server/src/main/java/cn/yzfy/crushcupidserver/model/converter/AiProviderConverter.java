package cn.yzfy.crushcupidserver.model.converter;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.yzfy.crushcupidserver.model.dto.AiProviderDTO;
import cn.yzfy.crushcupidserver.model.entity.AiProvider;
import cn.yzfy.crushcupidserver.model.vo.AiProviderVO;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @className AiProviderConverter
 * @description 自定义大模型供应商 实体/DTO/VO 互转
 * @author crush-cupid
 * @code converter
 * @createTime 2026-08-31
 */
public final class AiProviderConverter {

    private AiProviderConverter() {
    }

    public static AiProviderVO toVO(AiProvider entity) {
        AiProviderVO vo = new AiProviderVO();
        BeanUtil.copyProperties(entity, vo, "capabilities");
        vo.setCapabilities(parseCapabilities(entity.getCapabilities()));
        return vo;
    }

    public static AiProvider toEntity(AiProviderDTO dto) {
        AiProvider p = new AiProvider();
        apply(p, dto);
        p.setCreatedAt(new Date());
        p.setUpdatedAt(new Date());
        return p;
    }

    /** 新建/更新字段合并（null 不覆盖） */
    public static void apply(AiProvider entity, AiProviderDTO dto) {
        if (dto.getName() != null) entity.setName(dto.getName());
        if (dto.getProviderKey() != null) entity.setProviderKey(dto.getProviderKey());
        if (dto.getBaseUrl() != null) entity.setBaseUrl(dto.getBaseUrl());
        if (dto.getApiKey() != null) entity.setApiKey(dto.getApiKey());
        if (dto.getModel() != null) entity.setModel(dto.getModel());
        if (dto.getTemperature() != null) entity.setTemperature(dto.getTemperature());
        if (dto.getTopP() != null) entity.setTopP(dto.getTopP());
        if (dto.getMaxTokens() != null) entity.setMaxTokens(dto.getMaxTokens());
        if (dto.getCapabilities() != null) entity.setCapabilities(joinCapabilities(dto.getCapabilities()));
        if (dto.getType() != null) entity.setType(dto.getType());
        if (dto.getVoice() != null) entity.setVoice(dto.getVoice());
        if (dto.getIsDefault() != null) entity.setIsDefault(dto.getIsDefault());
    }

    /** 逗号分隔字符串 → List */
    public static List<String> parseCapabilities(String capabilities) {
        if (StrUtil.isBlank(capabilities)) return List.of();
        return Arrays.stream(capabilities.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /** List → 逗号分隔字符串 */
    public static String joinCapabilities(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) return "";
        return String.join(",", capabilities);
    }
}
