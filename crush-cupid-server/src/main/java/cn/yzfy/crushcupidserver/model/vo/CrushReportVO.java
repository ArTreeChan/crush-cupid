package cn.yzfy.crushcupidserver.model.vo;

import lombok.Data;

import java.util.Date;

/**
 * 关系报告出参：列表用轻量字段（不含 markdown 全文，/detail 单独取）。
 */
@Data
public class CrushReportVO {

    private Long id;

    private Long crushId;

    private String crushName;

    private String title;

    private String source;

    private java.sql.Date reportDate;

    /** 列表时为空，/detail/{id} 才填充 */
    private String markdown;

    private Date createdAt;
}
