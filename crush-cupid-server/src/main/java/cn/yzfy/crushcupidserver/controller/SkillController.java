package cn.yzfy.crushcupidserver.controller;

import cn.yzfy.crushcupidserver.common.Result;
import cn.yzfy.crushcupidserver.model.vo.SkillCatalogVO;
import cn.yzfy.crushcupidserver.model.vo.SkillMetaVO;
import cn.yzfy.crushcupidserver.skill.SkillCatalogService;
import cn.yzfy.crushcupidserver.skill.SkillMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Skill 目录接口：暴露远端 GitHub skill 的元信息与可用 prompt。
 */
@RestController
@RequestMapping("/api/skill")
@RequiredArgsConstructor
public class SkillController {

    private final SkillCatalogService skillCatalogService;

    @GetMapping("/catalog")
    public Result<SkillCatalogVO> catalog() {
        SkillMeta meta = skillCatalogService.getSkillMeta();
        SkillMetaVO metaVO = new SkillMetaVO();
        BeanUtils.copyProperties(meta, metaVO);

        SkillCatalogVO vo = new SkillCatalogVO();
        vo.setSkill(metaVO);
        vo.setPrompts(skillCatalogService.listPrompts());
        return Result.ok(vo);
    }

    @GetMapping("/prompt/{name}")
    public Result<String> prompt(@PathVariable String name) {
        return Result.ok(skillCatalogService.getPrompt(name));
    }
}
