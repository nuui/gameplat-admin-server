package com.gameplat.admin.controller.open.report;

import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.gameplat.admin.model.dto.AgentReportQueryDTO;
import com.gameplat.admin.model.vo.MemberDayReportVo;
import com.gameplat.admin.model.vo.PageDtoVO;
import com.gameplat.admin.service.MemberDayReportService;
import com.gameplat.common.constant.ServiceName;
import com.gameplat.log.annotation.Log;
import com.gameplat.model.entity.member.MemberDayReport;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

/**
 * @Description : 代理报表
 * @Author : cc
 * @Date : 2022/3/10
 */
@Slf4j
@Api(tags = "代理报表")
@RestController
@RequestMapping("/api/admin/proxy/report")
public class ProxyMemberReportController {
    @Autowired
    private MemberDayReportService memberDayReportService;

    @GetMapping("/list")
    public PageDtoVO<MemberDayReportVo> list(PageDTO<MemberDayReport> page, AgentReportQueryDTO dto) {
        return memberDayReportService.agentReportList(page, dto);
    }

    @GetMapping("/export")
    @ApiOperation("导出财务报表")
    @PreAuthorize("hasAuthority('agent:report:export')")
    @Log(module = ServiceName.ADMIN_SERVICE, desc = "代理报表导出")
    public void list(AgentReportQueryDTO dto,HttpServletResponse response) {
        memberDayReportService.exportAgentReport(dto, response);
    }
}