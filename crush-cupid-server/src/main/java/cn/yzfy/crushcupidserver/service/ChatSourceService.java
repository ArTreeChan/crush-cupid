package cn.yzfy.crushcupidserver.service;

import cn.yzfy.crushcupidserver.model.entity.ChatSource;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * 针对表【chat_source】的数据库操作 Service
 */
public interface ChatSourceService extends IService<ChatSource> {

    /**
     * 查询某个暗恋对象的所有原材料
     */
    List<ChatSource> listByCrushId(Long crushId);
}
