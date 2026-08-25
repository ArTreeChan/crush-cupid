package cn.yzfy.crushcupidserver.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 原材料来源
 *
 * @TableName chat_source
 */
@Data
@TableName("chat_source")
public class ChatSource {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long crushId;

    private String fileName;

    private String filePath;

    private String fileType;

    private String fileFormat;

    private Integer messageCount;

    /** 原始分析结果（JSON 文本） */
    private String rawAnalysis;

    private Date parsedAt;

    private Date createdAt;
}
