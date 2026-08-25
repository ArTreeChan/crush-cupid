package cn.yzfy.crushcupidserver.model.service.impl;

import cn.yzfy.crushcupidserver.model.entity.CrushVersion;
import cn.yzfy.crushcupidserver.model.mapper.CrushVersionMapper;
import cn.yzfy.crushcupidserver.model.service.CrushVersionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 针对表【crush_version】的数据库操作 Service 实现
 */
@Service
public class CrushVersionServiceImpl extends ServiceImpl<CrushVersionMapper, CrushVersion>
        implements CrushVersionService {

    @Override
    public List<CrushVersion> listByCrushId(Long crushId) {
        return list(new LambdaQueryWrapper<CrushVersion>()
                .eq(CrushVersion::getCrushId, crushId)
                .orderByDesc(CrushVersion::getVersion));
    }
}
