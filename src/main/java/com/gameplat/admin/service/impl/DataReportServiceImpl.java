package com.gameplat.admin.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gameplat.admin.mapper.DataReportMapper;
import com.gameplat.admin.model.dto.GameRWDataReportDto;
import com.gameplat.admin.model.vo.*;
import com.gameplat.admin.service.DataReportService;
import com.gameplat.base.common.util.DateUtils;
import com.gameplat.base.common.util.StringUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author kb
 * @Date 2022/3/2 21:49
 * @Version 1.0
 */
@Service
@Log4j2
@Transactional(isolation = Isolation.DEFAULT, rollbackFor = Throwable.class)
public class DataReportServiceImpl extends ServiceImpl<DataReportMapper, GameRechDataReportVO> implements DataReportService {


    //转账出款
    private final String bank_count = "a";
    //在线支付
    private final String online_count = "b";
    //人工入款
    private final String hand_rech_count = "c";
    //虚拟币入款
    private final String virtual_recharge_number = "d";
    //非正常入款
    private final String exception_recharge_amount = "e";

    //会员提现
    private final String withdraw_count = "a";
    //人工提现
    private final String hand_withdraw_count = "b";
    //非正常出款
    private final String exception_withdraw_amount = "c";
    //虚拟币出款
    private final String virtual_withdraw_number = "d";

    //'类型：0 升级奖励  1：周俸禄  2：月俸禄  3：生日礼金 4：每月红包'
    private final String upgrade = "0";

    private final String weekFenglu = "1";

    private final String monthlyFenglu = "2";

    private final String birthdayGift = "3";

    private final String monthlyRed = "4";


    @Autowired(required = false)
    private DataReportMapper dataReportMapper;


    @Override
    public GameRechDataReportVO findRechReport(GameRWDataReportDto dto) {
        GameRechDataReportVO rechReport = dataReportMapper.findRechReport(dto);
        log.info("查询充值数据：{}", JSON.toJSONString(rechReport));
        List<Map<String, Object>> rechReportNum = dataReportMapper.findRechReportNums(dto);
        rechReportNum.stream().forEach(a -> {
           if (bank_count.equalsIgnoreCase(Convert.toStr(a.get("rechCode")))) {
               rechReport.setBankCount(Convert.toInt(a.get("rechNum")));
               return;
           }
            if (online_count.equalsIgnoreCase(Convert.toStr(a.get("rechCode")))) {
                rechReport.setOnlineCount(Convert.toInt(a.get("rechNum")));
                return;
            }
            if (hand_rech_count.equalsIgnoreCase(Convert.toStr(a.get("rechCode")))) {
                rechReport.setHandCount(Convert.toInt(a.get("rechNum")));
                return;
            }
            if (virtual_recharge_number.equalsIgnoreCase(Convert.toStr(a.get("rechCode")))) {
                rechReport.setVirtualRechCount(Convert.toInt(a.get("rechNum")));
                return;
            }
            if (exception_recharge_amount.equalsIgnoreCase(Convert.toStr(a.get("rechCode")))) {
                rechReport.setExceptionRechCount(Convert.toInt(a.get("rechNum")));
                return;
            }
        });
        return rechReport;
    }




    @Override
    public GameWithDataReportVO findWithReport(GameRWDataReportDto dto) {
        GameWithDataReportVO withReport = dataReportMapper.findWithReport(dto);
        log.info("查询充值数据：{}", JSON.toJSONString(withReport));
        List<Map<String, Object>> rechReportNum = dataReportMapper.findWithReportNums(dto);
        rechReportNum.stream().forEach(a -> {
            if (withdraw_count.equalsIgnoreCase(Convert.toStr(a.get("withCode")))) {
                withReport.setWithdrawCount(Convert.toInt(a.get("withNum")));
                return;
            }
            if (hand_withdraw_count.equalsIgnoreCase(Convert.toStr(a.get("withCode")))) {
                withReport.setHandWithdrawCount(Convert.toInt(a.get("withNum")));
                return;
            }
            if (exception_withdraw_amount.equalsIgnoreCase(Convert.toStr(a.get("withCode")))) {
                withReport.setExceptionWithdCount(Convert.toInt(a.get("withNum")));
                return;
            }
            if (virtual_withdraw_number.equalsIgnoreCase(Convert.toStr(a.get("withCode")))) {
                withReport.setVirtualWithdCount(Convert.toInt(a.get("withNum")));
                return;
            }
        });
        return withReport;
    }

    @Override
    public GameDataReportVO findGameReport(GameRWDataReportDto dto) {

        GameDataReportVO gameDataReportVO = new GameDataReportVO();

        //游戏数据
        List<GameBetDataReportVO> gameReport = dataReportMapper.findGameReport(dto);
        //总输赢
        BigDecimal allWinAmount = gameReport.stream().map(GameBetDataReportVO::getWinAmount).reduce(BigDecimal.ZERO,BigDecimal::add);

        gameDataReportVO.setAllWinAmount(allWinAmount);
        Map<String ,List<GameBetDataReportVO>> mapGameData = gameReport.stream().collect(Collectors.groupingBy(GameBetDataReportVO :: getGameType));
        //返水数据
        List<GameWaterDataReportVO> gameWaterReport = dataReportMapper.findGameWaterReport(dto);
        //总返水
        BigDecimal allWaterAmount = gameWaterReport.stream().map(GameWaterDataReportVO::getWaterAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        gameDataReportVO.setAllWaterAmount(allWaterAmount);
        Map<String ,List<GameWaterDataReportVO>> mapGameWaterData = gameWaterReport.stream().collect(Collectors.groupingBy(GameWaterDataReportVO :: getGameType));

        //游戏人数
        int num = 0;
        List<String> gameDataUserNum = dataReportMapper.findGameDataUserNum(dto);
        if (StringUtils.isNotEmpty(gameDataUserNum)) {
            num = gameDataUserNum.size();
        }
        gameDataReportVO.setAllWinNum(num);

        mapGameData.keySet().forEach(a ->{
            List<GameWaterDataReportVO> gameWaterDataReportVOS = mapGameWaterData.get(a);
            if (StringUtils.isNotEmpty(gameWaterDataReportVOS)) {
                mapGameData.get(a).get(0).setWaterAmount(gameWaterDataReportVOS.get(0).getWaterAmount());
            }
        });
        gameDataReportVO.setList(mapGameData);

        return gameDataReportVO;
    }


    @Override
    public GameAccountDataReportVo findMemberReport(Page<AccountReportVo> page, GameRWDataReportDto dto) {
        GameAccountDataReportVo accountReportVo = new GameAccountDataReportVo();

        //注册人数
        int regNum = dataReportMapper.findReportMemberRegNum(dto);
        log.info("注册人数：{}",regNum);
        accountReportVo.setRegNum(regNum);
        //登录人数
        int logNum = dataReportMapper.findReportMemberLogNum(dto);
        log.info("登录人数：{}",logNum);
        accountReportVo.setLogNum(logNum);

        //全部余额
        BigDecimal allBalance = dataReportMapper.findReportMemberAllBalance(dto);
        log.info("全部余额：{}",allBalance);
        accountReportVo.setGoodMoney(allBalance);
        //当前页面条数大小
        long pageSize =  page.getSize();;
        //当前页面数
        long pageCurrent = page.getCurrent();


        pageCurrent = (pageCurrent - 1) * pageSize;
        List<AccountReportVo> reportMemberBalance = dataReportMapper.findReportMemberBalance(dto,pageSize,pageCurrent);
        int count = dataReportMapper.findReportMemberBalanceCount(dto);
        accountReportVo.setList(reportMemberBalance);
        accountReportVo.setAccountNum(count);
        return accountReportVo;
    }


    @Override
    public GameDividendDataVo findDividendtDataReport(GameRWDataReportDto dto) {
        GameDividendDataVo gameDividendDataVo = new GameDividendDataVo();
        //优惠金额、彩金
        Map<String, BigDecimal> dividendtDataReport = dataReportMapper.findDiscountDataReport(dto);
        log.info("彩金数据：{}",dividendtDataReport);
        gameDividendDataVo.setDisAcountAmount(dividendtDataReport.get("rechDiscount"));
        gameDividendDataVo.setJackpot(dividendtDataReport.get("otherDiscount"));

        dto.setStartTime(dto.getStartTime() +" 00:00:00");
        dto.setEndTime(dto.getEndTime()+" 23:59:59");

        //VIP红利
        BigDecimal dividendDataReport = dataReportMapper.findDividendDataReport(dto);
        log.info("VIP红利数据：{}",dividendtDataReport);
        gameDividendDataVo.setVipDividend(dividendDataReport);


        //活动数据
        BigDecimal activityDataReport = dataReportMapper.findActivityDataReport(dto);
        log.info("活动数据：{}",dividendtDataReport);
        gameDividendDataVo.setActivityDividend(activityDataReport);

        //转时间戳
        long endTime = DateUtil.parse(dto.getEndTime()).getTime();
        log.info("结束时间转时间戳:{}",endTime);
        dto.setEndTime(Convert.toStr(endTime));
        long startTime = DateUtil.parse(dto.getStartTime()).getTime();
        log.info("开始时间转时间戳:{}",startTime);
        dto.setStartTime(Convert.toStr(startTime));

        BigDecimal redDataReport = dataReportMapper.findRedDataReport(dto);
        log.info("红包数据：{}",redDataReport);

        gameDividendDataVo.setRedEnvelope(redDataReport);
        return gameDividendDataVo;
    }
}
