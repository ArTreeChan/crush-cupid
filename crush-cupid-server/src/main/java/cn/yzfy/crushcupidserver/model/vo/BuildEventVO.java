package cn.yzfy.crushcupidserver.model.vo;

import lombok.Data;

/**
 * build 过程的 SSE 事件
 */
@Data
public class BuildEventVO {

    /** progress / done / error */
    private String type;

    private String message;

    private BuildResultVO result;
}
