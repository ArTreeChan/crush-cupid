package cn.yzfy.crushcupidserver.model.dto;

import lombok.Data;

/**
 * @className: OCRRequest
 * @description: 阿里云第三方文字识别
 * @author: 一朝风月
 * @code: 面向自己, 面向未来
 * @createTime: 2026-08-27 16:31
 */
@Data
public class OCRRequest {

    private String image;
    private Configure configure;
}

@Data
class  Configure{
    private Integer minSize;
    //是否输入文字框
    private Boolean outPutProb;
    private Boolean outPutKeyPoints;
    //是否跳过文字检测
    private Boolean skipDetection;
    // 默认true
    private String language;

}