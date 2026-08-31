package cn.yzfy.crushcupidserver.model.service;

import cn.yzfy.crushcupidserver.model.entity.AiProvider;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * @className AiProviderService
 * @description 自定义大模型供应商 Service（自带 MyBatis-Plus CRUD）
 * @author crush-cupid
 * @code service
 * @createTime 2026-08-31
 */
public interface AiProviderService extends IService<AiProvider> {

    /** 按供应商代号查询 */
    AiProvider getByProviderKey(String providerKey);
}
