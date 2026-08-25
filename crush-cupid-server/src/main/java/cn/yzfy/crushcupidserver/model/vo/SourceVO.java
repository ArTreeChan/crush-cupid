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

    private Integer messageCount;

    private Date createdAt;
}
