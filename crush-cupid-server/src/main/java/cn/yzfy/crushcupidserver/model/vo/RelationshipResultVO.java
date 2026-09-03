package cn.yzfy.crushcupidserver.model.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * 关系分析结果出参：全量统计 + AI 深度鉴定 + HTML 报告访问地址。
 */
@Data
public class RelationshipResultVO {

    private Long crushId;

    /** 联系人显示名 */
    private String contact;

    /** 全量统计（stats.json） */
    private JsonNode stats;

    /** AI 深度鉴定结果（analysis.json） */
    private JsonNode analysis;

    /** HTML 报告访问 URL */
    private String reportUrl;

    /** 主动指数（0-100） */
    private Integer initiative;

    /** 被爱指数（0-100） */
    private Integer lovedIndex;

    /** 冷淡指数（0-100） */
    private Integer coldIndex;

    /** 消息总数 */
    private Integer totalMessages;

    /** 降级提示（AI 鉴定失败时返回，说明保留的统计与缺失部分） */
    private String errorMessage;

    /** 是否为历史缓存结果（聊天记录未变时直接复用，不再重新分析） */
    private Boolean cached;
}
