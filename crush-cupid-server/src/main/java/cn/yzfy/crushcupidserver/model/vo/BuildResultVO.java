package cn.yzfy.crushcupidserver.model.vo;

import lombok.Data;

/**
 * 构建结果出参
 */
@Data
public class BuildResultVO {

    private Long crushId;

    private Integer version;

    private String status;

    /** 关系记忆摘要 */
    private String memorySummary;

    /** 人格摘要 */
    private String personaSummary;
}
