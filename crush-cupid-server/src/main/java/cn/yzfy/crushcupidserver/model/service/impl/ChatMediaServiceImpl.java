package cn.yzfy.crushcupidserver.model.service.impl;

import cn.yzfy.crushcupidserver.model.entity.ChatMedia;
import cn.yzfy.crushcupidserver.model.mapper.ChatMediaMapper;
import cn.yzfy.crushcupidserver.model.service.ChatMediaService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * @className ChatMediaServiceImpl
 * @description 对话图片/媒体 URL 的数据库操作 Service 实现。
 * @author 一朝风月
 * @code serviceImpl
 * @createTime 2026-08-28
 */
@Service
public class ChatMediaServiceImpl extends ServiceImpl<ChatMediaMapper, ChatMedia>
        implements ChatMediaService {

}
