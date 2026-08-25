package cn.yzfy.crushcupidserver.model.service;

import cn.yzfy.crushcupidserver.model.entity.Crush;
import com.baomidou.mybatisplus.spring.service.IService;

/**
* @author 27800
* @description 针对表【crush】的数据库操作Service
* @createDate 2026-08-25 22:03:03
*/
public interface CrushService extends IService<Crush> {

    /**
     * 根据 slug 查询暗恋对象
     */
    Crush getBySlug(String slug);
}
