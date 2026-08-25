package cn.yzfy.crushcupidserver.model.service.impl;

 import cn.yzfy.crushcupidserver.model.entity.Conversation;
import cn.yzfy.crushcupidserver.model.service.ConversationService;
import cn.yzfy.crushcupidserver.model.mapper.ConversationMapper;
 import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
 import org.springframework.stereotype.Service;

/**
* @author 27800
* @description 针对表【conversation】的数据库操作Service实现
* @createDate 2026-08-25 22:03:03
*/
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation>
    implements ConversationService{

}




