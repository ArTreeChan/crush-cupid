package cn.yzfy.crushcupidserver.service.impl;

import cn.yzfy.crushcupidserver.mapper.CrushMapper;
import cn.yzfy.crushcupidserver.model.entity.Crush;
import cn.yzfy.crushcupidserver.service.CrushService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
* @author 27800
* @description 针对表【crush】的数据库操作Service实现
* @createDate 2026-08-25 22:03:03
*/
@Service
public class CrushServiceImpl extends ServiceImpl<CrushMapper, Crush>
    implements CrushService {

    @Override
    public Crush getBySlug(String slug) {
        return getOne(new LambdaQueryWrapper<Crush>().eq(Crush::getSlug, slug));
    }
}




