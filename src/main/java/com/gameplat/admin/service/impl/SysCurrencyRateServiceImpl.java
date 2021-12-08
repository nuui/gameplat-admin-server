package com.gameplat.admin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gameplat.admin.mapper.SysCurrencyRateMapper;
import com.gameplat.admin.model.domain.SysCurrencyRate;
import com.gameplat.admin.service.SysCurrencyRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author lily
 * @description 货币比例
 * @date 2021/11/27
 */

@Service
@RequiredArgsConstructor
public class SysCurrencyRateServiceImpl extends ServiceImpl<SysCurrencyRateMapper, SysCurrencyRate> implements SysCurrencyRateService {

    @Autowired private SysCurrencyRateMapper sysCurrencyRateMapper;

    @Override
    public SysCurrencyRate info() {
        SysCurrencyRate sysCurrencyRate = sysCurrencyRateMapper.getConfig();
        return sysCurrencyRate;
    }
}