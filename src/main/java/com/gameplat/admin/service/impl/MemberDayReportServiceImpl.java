package com.gameplat.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gameplat.admin.mapper.GameMemberReportMapper;
import com.gameplat.admin.mapper.MemberDayReportMapper;
import com.gameplat.admin.model.dto.AgentReportQueryDTO;
import com.gameplat.admin.model.vo.MemberDayReportVo;
import com.gameplat.admin.model.vo.PageDtoVO;
import com.gameplat.admin.service.MemberDayReportService;
import com.gameplat.admin.service.RecommendConfigService;
import com.gameplat.model.entity.member.MemberDayReport;
import com.gameplat.model.entity.proxy.RecommendConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(isolation = Isolation.DEFAULT, rollbackFor = Throwable.class)
public class MemberDayReportServiceImpl extends ServiceImpl<MemberDayReportMapper, MemberDayReport>
        implements MemberDayReportService {

    @Autowired private RecommendConfigService recommendConfigService;

    @Autowired private GameMemberReportMapper memberReportMapper;

    @Override
    public PageDtoVO<MemberDayReportVo> agentReportList(PageDTO<MemberDayReport> page, AgentReportQueryDTO dto) {
        PageDtoVO<MemberDayReportVo> pageDtoVO = new PageDtoVO();
        Map<String, Object> otherData = new HashMap<>();
        if (StrUtil.isEmpty(dto.getStartDate()) || StrUtil.isEmpty(dto.getEndDate())) {
            dto.setStartDate(DateUtil.format(new Date(), "YYYY-MM-dd"));
            dto.setEndDate(DateUtil.format(new Date(), "YYYY-MM-dd"));
        }
        if (dto.getIsIncludeProxy() == null) {
            dto.setIsIncludeProxy(true);
        }
        // 获取有效会员配置
        RecommendConfig recommendConfig = recommendConfigService.getRecommendConfig();
        // 充值额
        BigDecimal rechargeAmountLimit = recommendConfig.getRechargeAmountLimit();
        // 有效投注额
        BigDecimal validAmountLimit = recommendConfig.getValidAmountLimit();
        // 总计
        MemberDayReportVo total = memberReportMapper.agentReportSummary(
                dto.getAgentName(),
                dto.getStartDate(),
                dto.getEndDate(),
                dto.getIsIncludeProxy(),
                rechargeAmountLimit,
                validAmountLimit
        );
        MemberDayReportVo totalMemberAndProxy = memberReportMapper.getTotalMemberAndProxy(dto.getAgentName());
        MemberDayReportVo registerSum = memberReportMapper.getMemberAndProxySum(
                dto.getAgentName(),
                dto.getStartDate(),
                dto.getEndDate()
        );
        if(totalMemberAndProxy == null){
            pageDtoVO.setPage(new Page<>());
            otherData.put("totalData", new MemberDayReportVo());
            pageDtoVO.setOtherData(otherData);
            return pageDtoVO;
        }
        total.setAgentTotalNum(totalMemberAndProxy.getAgentTotalNum());
        total.setMemberTotalNum(totalMemberAndProxy.getMemberTotalNum());
        total.setRegisterNum(registerSum.getRegisterNum());
        total.setRegisterAgentNum(registerSum.getRegisterAgentNum());
        // 明细
        Page<MemberDayReportVo> returnPage = memberReportMapper.agentReport(
                page,
                dto.getAgentName(),
                dto.getStartDate(),
                dto.getEndDate(),
                dto.getIsIncludeProxy(),
                rechargeAmountLimit,
                validAmountLimit
        );
        // 查询出所有代理
        List<MemberDayReportVo> list = memberReportMapper.getMemberAndProxy(
                dto.getAgentName(),
                dto.getStartDate(),
                dto.getEndDate()
        );
        if (CollectionUtil.isNotEmpty(returnPage.getRecords())) {
            Map<String, MemberDayReportVo> poMap
                    = list.stream()
                    .collect(
                            Collectors.toMap(MemberDayReportVo::getParentName, MemberDayReportPO -> MemberDayReportPO)
                    );
            for (MemberDayReportVo po : returnPage.getRecords()) {
                MemberDayReportVo obj = poMap.get(po.getParentName());
                if (BeanUtil.isEmpty(obj)) {
                    continue;
                }
                po.setMemberTotalNum(obj.getMemberTotalNum());
                po.setAgentTotalNum(obj.getAgentTotalNum());
                po.setRegisterNum(obj.getRegisterNum());
                po.setRegisterAgentNum(obj.getRegisterAgentNum());
                po.setAgentLevel(obj.getAgentLevel());
            }
        }
        pageDtoVO.setPage(returnPage);
        otherData.put("totalData", total);
        pageDtoVO.setOtherData(otherData);
        return pageDtoVO;

    }
}
