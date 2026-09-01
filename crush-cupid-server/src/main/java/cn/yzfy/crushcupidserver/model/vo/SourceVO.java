package cn.yzfy.crushcupidserver.model.vo;

import lombok.Data;

import java.util.Date;

/**
 * 原材料出参
 */
@Data
public class SourceVO {

    private Long id;

    private Long crushId;

    private String type;

    private String fileName;

    private String content;

    /** LLM 理解解析结果（rawAnalysis），用于展示「解析后」的提炼内容 */
    private String analysis;

    private Integer messageCount;

    private Date createdAt;
}
