package cn.yzfy.crushcupidserver.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 关系进展报告（军师 LLM 生成，落库供历史查看）。
 *
 * @TableName crush_report
 */
@Data
@TableName("crush_report")
public class CrushReport implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long crushId;

    private String crushName;

    /** 报告标题（含日期，如「关系进展报告：小美」） */
    private String title;

    /** 报告 Markdown 全文 */
    private String markdown;

    /** 生成来源：manual(手动) / scheduled(定时) */
    private String source;

    /** 报告归属日期（用于每日去重） */
    private java.sql.Date reportDate;

    private Date createdAt;
}
