package cn.yzfy.crushcupidserver.model.service.impl;

import cn.yzfy.crushcupidserver.model.entity.AiProvider;
import cn.yzfy.crushcupidserver.model.mapper.AiProviderMapper;
import cn.yzfy.crushcupidserver.model.service.AiProviderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * @className AiProviderServiceImpl
 * @description 自定义大模型供应商 Service 实现
 * @author crush-cupid
 * @code service impl
 * @createTime 2026-08-31
 */
@Service
public class AiProviderServiceImpl extends ServiceImpl<AiProviderMapper, AiProvider>
        implements AiProviderService {

    @Override
    public AiProvider getByProviderKey(String providerKey) {
        return getOne(new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getProviderKey, providerKey));
    }
}
