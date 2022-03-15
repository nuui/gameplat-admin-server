package com.gameplat.admin.service.impl;


import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gameplat.admin.config.TenantConfig;
import com.gameplat.admin.convert.GameFinancialReportConvert;
import com.gameplat.admin.mapper.GameFinancialReportMapper;
import com.gameplat.admin.model.dto.GameFinancialReportQueryDTO;
import com.gameplat.admin.model.vo.GameFinancialReportVO;
import com.gameplat.admin.model.vo.GameReportExportVO;
import com.gameplat.admin.model.vo.PageDtoVO;
import com.gameplat.admin.model.vo.TotalGameFinancialReportVO;
import com.gameplat.admin.service.GameFinancialReportService;
import com.gameplat.admin.util.JxlsExcelUtils;
import com.gameplat.base.common.util.DateUtils;
import com.gameplat.base.common.util.StringUtils;
import com.gameplat.base.common.util.UUIDUtils;
import com.gameplat.common.util.ZipUtils;
import com.gameplat.model.entity.report.GameFinancialReport;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author aBen
 * @date 2022/3/6 17:25
 * @desc
 */
@Slf4j
@Service
@Transactional(isolation = Isolation.DEFAULT, rollbackFor = Throwable.class)
public class GameFinancialReportServiceImpl extends ServiceImpl<GameFinancialReportMapper, GameFinancialReport>
        implements GameFinancialReportService {

    @Autowired
    private GameFinancialReportMapper gameFinancialReportMapper;

    @Autowired
    private TenantConfig tenantConfig;

    @Autowired
    private GameFinancialReportConvert gameFinancialReportConvert;

    /**
     * 财务报表模板
     */
    private static final String GAME_FINANCIAL_REPORT = "gameFinancialReport.xlsx";

    @Override
    public List<GameFinancialReportVO> findGameFinancialReportList(GameFinancialReportQueryDTO dto) {
        List<GameFinancialReportVO> gameFinancialReportVOList = gameFinancialReportMapper.findGameFinancialReportList(dto);
        assembleKgNewLottery(gameFinancialReportVOList);
        return gameFinancialReportVOList;
    }

    @Override
    public PageDtoVO<GameFinancialReportVO> findReportPage(Page<GameFinancialReport> page, GameFinancialReportQueryDTO queryDTO) {
        PageDtoVO<GameFinancialReportVO> pageDtoVO = new PageDtoVO<>();
        Page<GameFinancialReportVO> result = gameFinancialReportMapper.findGameFinancialReportPage(page, queryDTO);

        //查询总计
        QueryWrapper<GameFinancialReport> queryOne = Wrappers.query();
        queryOne.select("sum(valid_amount) as valid_amount,sum(win_amount) as win_amount,sum(accumulate_win_amount) as accumulateWinAmount");
        fillQueryWrapper(queryDTO, queryOne);
        GameFinancialReport gameFinancialReport = gameFinancialReportMapper.selectOne(queryOne);
        TotalGameFinancialReportVO totalGameFinancialReport = gameFinancialReportConvert.toTotalVO(gameFinancialReport);
        Map<String, Object> otherData = new HashMap<>();
        otherData.put("totalData", totalGameFinancialReport);
        pageDtoVO.setPage(result);
        pageDtoVO.setOtherData(otherData);
        return pageDtoVO;
    }

    @Override
    public void initGameFinancialReport(String statisticsTime) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("statistics_time", statisticsTime);
        // 先删除统计月份的所有数据
        this.removeByMap(map);

        List<GameFinancialReport> allGameFinancialReportList = statisticsGameReportList(statisticsTime);

        // 入库
        if (CollectionUtil.isNotEmpty(allGameFinancialReportList)) {
            this.saveBatch(allGameFinancialReportList);
        }
    }

    @Override
    public void exportGameFinancialReport(String statisticsTime, HttpServletResponse response) {
        // 根据年月查询需要导出报表数据
        GameFinancialReportQueryDTO queryDTO = new GameFinancialReportQueryDTO();
        List<GameFinancialReportVO> reportList = gameFinancialReportMapper.findGameFinancialReportList(queryDTO);

        // 对KG新彩票的三个彩种做特殊处理
        assembleKgNewLottery(reportList);

        // 根据游戏类型编号对List进行分组
        Map<String, List<GameFinancialReportVO>> listMap = reportList.stream().collect(Collectors.groupingBy(GameFinancialReportVO::getGameTypeName));
        List<GameReportExportVO> gameReportExportVOList = new ArrayList<>();
        // 组装模板渲染对象
        for (String key : listMap.keySet()) {
            GameReportExportVO gameReportExportVO = new GameReportExportVO();
            gameReportExportVO.setGameTypeId(listMap.get(key).get(0).getGameTypeId());
            gameReportExportVO.setGameTypeName(key);
            gameReportExportVO.setGameFinancialReportList(listMap.get(key));
            gameReportExportVOList.add(gameReportExportVO);
        }
        List<GameReportExportVO> finalExportList = gameReportExportVOList.stream().sorted(Comparator.comparing(GameReportExportVO::getGameTypeId)).collect(Collectors.toList());
        Map<String, Object> map = new HashMap<>();
        map.put("statisticsTime", statisticsTime);
        map.put("gameList", finalExportList);
        final BigDecimal[] bigDecimal = {new BigDecimal(0), new BigDecimal(0), new BigDecimal(0)};
        reportList.forEach(a -> {
            bigDecimal[0] = bigDecimal[0].add(a.getValidAmount());
            bigDecimal[1] = bigDecimal[1].add(a.getWinAmount());
            bigDecimal[2] = bigDecimal[2].add(a.getAccumulateWinAmount());
        });
        map.put("totalValidAmount", bigDecimal[0]);
        map.put("totalWinAmount", bigDecimal[1]);
        map.put("totalAccumulateWinAmount", bigDecimal[2]);
        // 将数据渲染到excel模板上
        // 定义ZIP包的包名
        String zipFileName = statisticsTime + "财务报表导出";

        try {
            response.setHeader("Content-Disposition", "attachment;fileName=" + URLEncoder.encode(zipFileName + ".zip", "UTF-8"));
            response.setContentType("application/zip");

            final File dir = new File(System.getProperty("java.io.tmpdir") + File.separator + "excel-" + UUIDUtils.getUUID32());
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 单个Excel文件的fileName
            String fileName = new StringBuilder()
                    .append("(")
                    .append("财务报表")
                    .append(")")
                    .append("-")
                    .append(statisticsTime)
                    .append(".xlsx")
                    .toString();
            FileOutputStream fo = null;
            try {
                fo = new FileOutputStream(new File(dir + File.separator + fileName));
            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
            }
            try {
                JxlsExcelUtils.downLoadExcel(map, GAME_FINANCIAL_REPORT, fo);
            } catch (InvalidFormatException | IOException e1) {
                e1.printStackTrace();
            } finally {
                if (fo != null) {
                    try {
                        fo.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            OutputStream out = response.getOutputStream();
            ZipUtils.zipDir(out, dir);
            ZipUtils.del(dir);
            out.flush();
        } catch (IOException e) {
            log.error("财务报表导出IO错误", e);
        }

    }

    /**
     * 统计三方游戏的财务报表数据
     *
     * @param statisticsTime
     * @return
     */
    public List<GameFinancialReport> statisticsGameReportList(String statisticsTime) {
        String tenant = tenantConfig.getTenantCode();
        // 统计开始时间
        String startTime = statisticsTime + "-01";
        // 当前统计月份的最后一天的日期
        Date endDate = DateUtil.endOfMonth(DateUtils.parseDate(startTime, "yyyy-MM-dd"));
        // 统计结束时间
        String endTime = DateUtils.formatDate(endDate, "yyyy-MM-dd");

        List<GameFinancialReport> allGameFinancialReportList = new ArrayList<>();

        // 统计所有游戏的财务报表数据
        List<GameFinancialReport> gameFinancialReportList = gameFinancialReportMapper.initGameFinancialReport(statisticsTime, startTime, endTime, tenant);
        if (CollectionUtil.isNotEmpty(gameFinancialReportList)) {
            allGameFinancialReportList.addAll(gameFinancialReportList);
        }

        return allGameFinancialReportList;
    }



    /**
     * 对KG新彩票的三个彩种做特殊处理
     *
     * @param list
     */
    public void assembleKgNewLottery(List<GameFinancialReportVO> list) {
        if (StringUtils.isNotEmpty(list)) {
            for (GameFinancialReportVO vo : list) {
                if (vo.getGameKind().equals("kgnl_lottery_official") || vo.getGameKind().equals("kgnl_lottery_self") || vo.getGameKind().equals("kgnl_lottery_lhc")) {
                    vo.setGameTypeName("KG新彩票");
                    vo.setGameTypeId(9);
                    if (vo.getGameKind().equals("kgnl_lottery_official")) {
                        vo.setGameName("新官方彩");
                    }
                    if (vo.getGameKind().equals("kgnl_lottery_self")) {
                        vo.setGameName("新自营彩");
                    }
                    if (vo.getGameKind().equals("kgnl_lottery_lhc")) {
                        vo.setGameName("新六合彩");
                    }
                }
            }
        }
    }

    private void fillQueryWrapper(GameFinancialReportQueryDTO queryDTO, QueryWrapper<GameFinancialReport> queryWrapper) {
        queryWrapper.eq(ObjectUtils.isNotEmpty(queryDTO.getStatisticsTime()), "statistics_time", queryDTO.getStatisticsTime());
        queryWrapper.eq(ObjectUtils.isNotEmpty(queryDTO.getGameType()), "game_type", queryDTO.getGameType());
        queryWrapper.eq(ObjectUtils.isNotEmpty(queryDTO.getPlatformCode()), "platform_code", queryDTO.getPlatformCode());
    }
}