package com.gameplat.admin.controller.open.activity;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.gameplat.admin.model.domain.ActivityLobby;
import com.gameplat.admin.model.dto.ActivityLobbyAddDTO;
import com.gameplat.admin.model.dto.ActivityLobbyDTO;
import com.gameplat.admin.model.dto.ActivityLobbyUpdateDTO;
import com.gameplat.admin.model.dto.ActivityLobbyUpdateStatusDTO;
import com.gameplat.admin.model.vo.ActivityLobbyVO;
import com.gameplat.admin.service.ActivityLobbyService;
import com.gameplat.base.common.exception.ServiceException;
import com.gameplat.base.common.util.StringUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户活动大厅
 *
 * @author kenvin
 */
@Api(tags = "活动大厅管理")
@Slf4j
@RestController
@RequestMapping("/api/admin/activity/lobby")
public class ActivityLobbyController {

    @Autowired
    private ActivityLobbyService activityLobbyService;

    /**
     * 活动大厅列表
     */
    @ApiOperation(value = "活动大厅列表")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('activity:lobby:list')")
    public IPage<ActivityLobbyVO> list(PageDTO<ActivityLobby> page, ActivityLobbyDTO activityLobbyDTO) {
        return activityLobbyService.findActivityLobbyList(page, activityLobbyDTO);
    }

    /**
     * 新增活动大厅
     *
     * @param activityLobbyAddDTO
     */
    @ApiOperation(value = "新增活动大厅")
    @PostMapping("/add")
    @PreAuthorize("hasAuthority('activity:lobby:add')")
    public void add(@RequestBody ActivityLobbyAddDTO activityLobbyAddDTO) {
        if (StringUtils.isNull(activityLobbyAddDTO.getStatisDate())) {
            throw new ServiceException("请选择统计日期");
        }
        if (activityLobbyAddDTO.getApplyWay() == 2 && activityLobbyAddDTO.getNextDayApply() == 0) {
            throw new ServiceException("自动申请的活动必须勾选隔天申请");
        }
        activityLobbyService.add(activityLobbyAddDTO);
    }

    /**
     * 修改活动大厅
     *
     * @param activityLobbyUpdateDTO
     */
    @ApiOperation(value = "修改活动大厅")
    @PutMapping("/update")
    @PreAuthorize("hasAuthority('activity:lobby:edit')")
    public void update(@RequestBody ActivityLobbyUpdateDTO activityLobbyUpdateDTO) {
        if (activityLobbyUpdateDTO.getId() == null || activityLobbyUpdateDTO.getId() == 0) {
            throw new ServiceException("id不能为空");
        }
        if (StringUtils.isNull(activityLobbyUpdateDTO.getStatisDate())) {
            throw new ServiceException("请选择统计日期");
        }
        if (activityLobbyUpdateDTO.getApplyWay() == 2 && activityLobbyUpdateDTO.getNextDayApply() == 0) {
            throw new ServiceException("自动申请的活动必须勾选隔天申请");
        }
        activityLobbyService.update(activityLobbyUpdateDTO);
    }

    /**
     * 删除活动大厅
     *
     * @param ids
     */
    @ApiOperation(value = "删除活动大厅")
    @DeleteMapping("/delete")
    @PreAuthorize("hasAuthority('activity:lobby:remove')")
    public void remove(@RequestBody String ids) {
        if (StringUtils.isBlank(ids)) {
            throw new ServiceException("ids不能为空");
        }
        activityLobbyService.remove(ids);
    }


    /**
     * 更新状态
     *
     * @param activityLobbyUpdateStatusDTO
     */
    @ApiOperation(value = "更新活动大厅状态")
    @PutMapping("/updateStatus")
    @PreAuthorize("hasAuthority('activity:lobby:edit')")
    public void updateStatus(@RequestBody ActivityLobbyUpdateStatusDTO activityLobbyUpdateStatusDTO) {
        if (activityLobbyUpdateStatusDTO.getId() == null || activityLobbyUpdateStatusDTO.getId() == 0) {
            throw new ServiceException("id不能为空");
        }
        activityLobbyService.updateStatus(activityLobbyUpdateStatusDTO);
    }

    /**
     * 查询未绑定的活动大厅列表
     */
    @ApiOperation(value = "查询未绑定的活动大厅列表")
    @GetMapping("/findUnboundLobbyList")
    @PreAuthorize("hasAuthority('activity:lobby:list')")
    public List<ActivityLobbyVO> findUnboundLobbyList() {
        return activityLobbyService.findUnboundLobbyList();
    }

}
