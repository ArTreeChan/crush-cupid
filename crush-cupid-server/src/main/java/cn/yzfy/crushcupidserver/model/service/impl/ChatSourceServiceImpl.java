package cn.yzfy.crushcupidserver.model.service.impl;

import cn.yzfy.crushcupidserver.model.entity.ChatSource;
import cn.yzfy.crushcupidserver.model.mapper.ChatSourceMapper;
import cn.yzfy.crushcupidserver.model.service.ChatSourceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 针对表【chat_source】的数据库操作 Service 实现
 */
@Service
public class ChatSourceServiceImpl extends ServiceImpl<ChatSourceMapper, ChatSource>
        implements ChatSourceService {

    @Override
    public List<ChatSource> listByCrushId(Long crushId) {
        return list(new LambdaQueryWrapper<ChatSource>()
                .eq(ChatSource::getCrushId, crushId)
                .orderByAsc(ChatSource::getId));
    }
}
