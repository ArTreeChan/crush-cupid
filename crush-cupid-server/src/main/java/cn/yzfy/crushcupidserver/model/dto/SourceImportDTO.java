package cn.yzfy.crushcupidserver.model.dto;

import cn.yzfy.crushcupidserver.model.enums.SourceType;
import lombok.Data;

/**
 * 导入原材料入参
 */
@Data
public class SourceImportDTO {

    /** 原材料类型，默认 TEXT */
    private SourceType type = SourceType.TEXT;

    /** 文件名（上传文件时使用，可选） */
    private String fileName;

    /** 内容（TEXT 类型直接粘贴文本） */
    private String content;
}
