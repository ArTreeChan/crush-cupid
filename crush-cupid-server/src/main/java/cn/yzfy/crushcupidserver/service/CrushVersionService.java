package cn.yzfy.crushcupidserver.service;

import cn.yzfy.crushcupidserver.model.entity.CrushVersion;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * 针对表【crush_version】的数据库操作 Service
 */
public interface CrushVersionService extends IService<CrushVersion> {

    /**
     * 查询某个暗恋对象的版本历史
     */
    List<CrushVersion> listByCrushId(Long crushId);
}
