package cn.yzfy.crushcupidserver.agent.report;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 关系报告自动生成调度配置（{@code crush.report.*}）。
 *
 * @author 一朝风月
 */
@Data
@ConfigurationProperties(prefix = "crush.report")
public class ReportProperties {

    /** 是否启用报告自动生成 */
    private boolean enabled = true;

    /** 每天定时生成报告的 cron（默认每日 09:00） */
    private String cron = "0 0 9 * * ?";
}
