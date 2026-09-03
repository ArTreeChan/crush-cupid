package cn.yzfy.crushcupidserver.controller;

import cn.yzfy.crushcupidserver.agent.RelationshipService;
import cn.yzfy.crushcupidserver.common.Result;
import cn.yzfy.crushcupidserver.model.vo.RelationshipResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 关系分析接口：调用「她不一样」引擎（统计 + AI 鉴定 + HTML 报告）。
 */
@Slf4j
@RestController
@RequestMapping("/api/relationship")
@RequiredArgsConstructor
public class RelationshipController {

    private final RelationshipService relationshipService;

    /**
     * 对指定暗恋对象执行一次完整的关系分析（统计 + AI 鉴定 + 报告生成）。
     * 同步返回，耗时约 1~3 分钟，前端需耐心等待。
     */
    @PostMapping("/{crushId}/analyze")
    public Result<RelationshipResultVO> analyze(@PathVariable Long crushId) {
        return Result.ok(relationshipService.analyze(crushId));
    }
}
