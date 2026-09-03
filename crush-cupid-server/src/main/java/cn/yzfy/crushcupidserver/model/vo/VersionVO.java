package cn.yzfy.crushcupidserver.model.vo;

import lombok.Data;

import java.util.Date;

/**
 * 版本快照出参
 */
@Data
public class VersionVO {

    private Long id;

    private Long crushId;

    private Integer version;

    private String reason;

    /** 快照（JSON 字符串） */
    private String snapshot;

    private Date createdAt;
}
