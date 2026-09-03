package cn.yzfy.crushcupidserver.model.vo;

import lombok.Data;

import java.util.Date;

/**
 * 对话消息出参
 */
@Data
public class ChatMessageVO {

    private String role;

    private String content;

    private Date createdAt;
}
