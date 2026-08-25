package cn.yzfy.crushcupidserver.controller;

import cn.hutool.core.util.StrUtil;
import cn.yzfy.crushcupidserver.common.Result;
import cn.yzfy.crushcupidserver.exception.BizException;
import cn.yzfy.crushcupidserver.model.converter.CrushConverter;
import cn.yzfy.crushcupidserver.model.dto.CrushCreateDTO;
import cn.yzfy.crushcupidserver.model.dto.CrushUpdateDTO;
import cn.yzfy.crushcupidserver.model.entity.Crush;
import cn.yzfy.crushcupidserver.model.service.CrushService;
import cn.yzfy.crushcupidserver.model.vo.CrushVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 暗恋对象 CRUD
 */
@RestController
@RequestMapping("/api/crush")
@RequiredArgsConstructor
public class CrushController {

    private final CrushService crushService;

    @GetMapping
    public Result<List<CrushVO>> list() {
        List<CrushVO> list = crushService.list().stream().map(CrushConverter::toVO).toList();
        return Result.ok(list);
    }

    @GetMapping("/{id}")
    public Result<CrushVO> get(@PathVariable Long id) {
        Crush crush = crushService.getById(id);
        if (crush == null) {
            throw BizException.notFound("未找到暗恋对象 id=" + id);
        }
        return Result.ok(CrushConverter.toVO(crush));
    }

    @PostMapping
    public Result<CrushVO> create(@RequestBody CrushCreateDTO dto) {
        if (StrUtil.isBlank(dto.getName())) {
            throw BizException.badRequest("name 不能为空");
        }
        if (StrUtil.isBlank(dto.getSlug())) {
            throw BizException.badRequest("slug 不能为空");
        }
        if (crushService.getBySlug(dto.getSlug()) != null) {
            throw BizException.badRequest("slug 已存在：" + dto.getSlug());
        }
        Crush crush = CrushConverter.toEntity(dto);
        crushService.save(crush);
        return Result.ok(CrushConverter.toVO(crush));
    }

    @PutMapping("/{id}")
    public Result<CrushVO> update(@PathVariable Long id, @RequestBody CrushUpdateDTO dto) {
        Crush crush = crushService.getById(id);
        if (crush == null) {
            throw BizException.notFound("未找到暗恋对象 id=" + id);
        }
        CrushConverter.update(crush, dto);
        crushService.updateById(crush);
        return Result.ok(CrushConverter.toVO(crush));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        crushService.removeById(id);
        return Result.ok();
    }
}
