package cn.yzfy.crushcupidserver.model.dto;

import lombok.Data;

/**
 * 对话入参
 */
@Data
public class ChatRequestDTO {

    /** 暗恋对象 slug */
    private String crushSlug;

    /** 用户消息 */
    private String message;
}
