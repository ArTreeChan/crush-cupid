package cn.yzfy.crushcupidserver.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 对话记录
 *
 * @TableName conversation
 */
@Data
@TableName("conversation")
public class Conversation implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long crushId;

    /** user / assistant */
    private String role;

    private String content;

    private Date createdAt;
}
