package com.gameplat.admin.controller.open.activity;

import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.gameplat.admin.enums.ActivityInfoEnum;
import com.gameplat.admin.model.domain.ActivityInfo;
import com.gameplat.admin.model.dto.ActivityInfoAddDTO;
import com.gameplat.admin.model.dto.ActivityInfoQueryDTO;
import com.gameplat.admin.model.dto.ActivityInfoUpdateDTO;
import com.gameplat.admin.model.dto.ActivityInfoUpdateSortDTO;
import com.gameplat.admin.model.vo.ActivityInfoVO;
import com.gameplat.admin.model.vo.ValueDataVO;
import com.gameplat.admin.service.ActivityInfoService;
import com.gameplat.admin.service.ConfigService;
import com.gameplat.base.common.exception.ServiceException;
import com.gameplat.base.common.util.DateUtil;
import com.gameplat.base.common.util.StringUtils;
import com.gameplat.common.enums.DictDataEnum;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 活动管理
 *
 * @author kenvin
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/activity/info")
@Api(tags = "活动发布管理")
public class ActivityInfoController {

    @Autowired
    private ActivityInfoService activityInfoService;

    @Autowired
    private ConfigService configService;

    /**
     * 活动列表
     *
     * @param page
     * @param activityInfoQueryDTO
     * @return
     */
    @ApiOperation(value = "活动列表")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('activity:info:page')")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "current", value = "分页参数：当前页", defaultValue = "1"),
            @ApiImplicitParam(name = "size", value = "每页条数"),
    })
    public IPage<ActivityInfoVO> list(
            @ApiIgnore PageDTO<ActivityInfo> page, ActivityInfoQueryDTO activityInfoQueryDTO) {
        return activityInfoService.list(page, activityInfoQueryDTO);
    }

    /**
     * 活动详情
     *
     * @param id
     * @return
     */
    @ApiOperation(value = "活动详情")
    @GetMapping("/detail")
    @PreAuthorize("hasAuthority('activity:info:detail')")
    public ActivityInfoVO detail(Long id) {
        if (id == null || id == 0) {
            throw new ServiceException("活动id不能为空");
        }
        return activityInfoService.detail(id);
    }

    /**
     * 新增活动
     *
     * @param activityInfoAddDTO
     * @param country
     */
    @ApiOperation(value = "新增活动")
    @PostMapping("/add")
    @PreAuthorize("hasAuthority('activity:info:add')")
    public void add(
            @RequestBody ActivityInfoAddDTO activityInfoAddDTO,
            @RequestHeader(value = "country", required = false, defaultValue = "zh-CN") String country) {
        checkActivityInfo(activityInfoAddDTO.getValidStatus(), activityInfoAddDTO.getEndTime(), activityInfoAddDTO.getBeginTime(), activityInfoAddDTO.getActivityLobbyId(), activityInfoAddDTO.getId());
        activityInfoService.add(activityInfoAddDTO, country);
    }

    /**
     * 检查活动是否满足条件
     *
     * @param validStatus
     * @param endTime
     * @param beginTime
     * @param activityLobbyId
     * @param id
     */
    private void checkActivityInfo(Integer validStatus, String endTime, String beginTime, Long activityLobbyId, Long id) {
        if (validStatus == ActivityInfoEnum.ValidStatus.TIME_LIMIT.value()) {
            if (DateUtil.strToDate(endTime, "yyyy-MM-dd")
                    .before(DateUtil.strToDate(beginTime, "yyyy-MM-dd"))) {
                throw new ServiceException("活动结束时间不能小于活动开始时间");
            }
        }
        if (StringUtils.isNotNull(activityLobbyId)) {
            activityInfoService.checkActivityLobbyId(activityLobbyId, id);
        }
    }

    /**
     * 编辑活动
     *
     * @param activityInfoUpdateDTO
     * @param country
     */
    @ApiOperation(value = "编辑活动")
    @PostMapping("/update")
    @PreAuthorize("hasAuthority('activity:info:update')")
    public void update(
            @RequestBody ActivityInfoUpdateDTO activityInfoUpdateDTO,
            @RequestHeader(value = "country", required = false, defaultValue = "zh-CN") String country) {
        checkActivityInfo(activityInfoUpdateDTO.getValidStatus(), activityInfoUpdateDTO.getEndTime(), activityInfoUpdateDTO.getBeginTime(), activityInfoUpdateDTO.getActivityLobbyId(), activityInfoUpdateDTO.getId());
        activityInfoService.update(activityInfoUpdateDTO, country);
    }

    /**
     * 修改活动排序
     *
     * @param activityInfoUpdateSortDTO
     */
    @ApiOperation(value = "修改活动排序")
    @PostMapping("/updateSort")
    @PreAuthorize("hasAuthority('activity:info:updateSort')")
    public void updateSort(@RequestBody ActivityInfoUpdateSortDTO activityInfoUpdateSortDTO) {
        if (activityInfoUpdateSortDTO.getId() == null || activityInfoUpdateSortDTO.getId() == 0) {
            throw new ServiceException("活动id不能为空");
        }
        if (activityInfoUpdateSortDTO.getSort() == null) {
            throw new ServiceException("活动排序sort不能为空");
        }
        activityInfoService.updateSort(activityInfoUpdateSortDTO);
    }

    /**
     * 删除活动
     *
     * @param ids
     */
    @ApiOperation(value = "删除活动")
    @DeleteMapping("/delete")
    @PreAuthorize("hasAuthority('activity:info:remove')")
    public void delete(@RequestBody String ids) {
        if (StringUtils.isBlank(ids)) {
            throw new ServiceException("ids不能为空");
        }
        activityInfoService.delete(ids);
    }

    /**
     * 获取全部活动
     *
     * @return
     */
    @ApiOperation(value = "获取全部活动")
    @GetMapping("/getAllActivity")
    @PreAuthorize("hasAuthority('activity:info:getAllActivity')")
    public List<ActivityInfoVO> getAllActivity() {
        List<ActivityInfoVO> activityList = activityInfoService.getAllActivity();
        if (CollectionUtils.isEmpty(activityList)) {
            return new ArrayList<>();
        }
        ArrayList<ActivityInfoVO> result = new ArrayList<>();
        long nowTime = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        activityList.forEach(
                o -> {
                    if (o.getValidStatus() == ActivityInfoEnum.ValidStatus.PERMANENT.value()) {
                        result.add(o);
                    } else if (o.getValidStatus() == ActivityInfoEnum.ValidStatus.TIME_LIMIT.value()) {
                        String beginDate = o.getBeginTime().concat(" 00:00:00");
                        String endDate = o.getEndTime().concat(" 23:59:59");
                        try {
                            if (nowTime > sdf.parse(beginDate).getTime()
                                    && nowTime < sdf.parse(endDate).getTime()) {
                                result.add(o);
                            }
                        } catch (ParseException e) {
                            log.info("获取关联了活动规则的全部活动信息,时间转换报错，原因{}", e);
                        }
                    }
                });
        return result;
    }

    /**
     * 活动排序值列表
     *
     * @return
     */
    @ApiOperation(value = "活动排序值列表")
    @GetMapping("/sortList")
    @PreAuthorize("hasAuthority('activity:info:sortList')")
    public List<ValueDataVO> sortList() {
        String activitySortConfig = configService.getValue(DictDataEnum.ACTIVITY_SORT_CONFIG);
        if (StringUtils.isBlank(activitySortConfig)) {
            throw new ServiceException("活动排序值没有配置");
        }
        return JSONArray.parseArray(activitySortConfig, ValueDataVO.class);
    }
}
