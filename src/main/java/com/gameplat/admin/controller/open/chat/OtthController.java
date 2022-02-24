package com.gameplat.admin.controller.open.chat;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplat.admin.config.TenantConfig;
import com.gameplat.admin.model.bean.RoomMember;
import com.gameplat.admin.model.dto.PushCPBetMessageReq;
import com.gameplat.admin.model.vo.ChatUserVO;
import com.gameplat.admin.model.vo.LotteryCodeVo;
import com.gameplat.admin.model.vo.PushLottWinVo;
import com.gameplat.admin.service.*;
import com.gameplat.base.common.context.DyDataSourceContextHolder;
import com.gameplat.base.common.enums.EnableEnum;
import com.gameplat.base.common.exception.ServiceException;
import com.gameplat.base.common.json.JsonUtils;
import com.google.common.base.Joiner;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author lily
 * @description
 * @date 2022/2/16
 */

@Slf4j
@Api(tags = "聊天室侧滑菜单管理")
@RestController
@RequestMapping("/api/admin/chat")
@Transactional(isolation = Isolation.DEFAULT, rollbackFor = Throwable.class)
public class OtthController {

    @Autowired
    private OtthService otthService;
    @Autowired
    private TenantDomainService tenantDomainService;
    @Autowired
    private GameConfigService gameConfigService;
    @Autowired
    private ChatLeaderBoardService chatLeaderBoardService;
    @Autowired
    private SysTenantSettingService sysTenantSettingService;
    @Autowired
    private ChatPushPlanService chatPushPlanService;
    @Autowired
    private TenantConfig tenantConfig;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String ROOM_MEMBER_BATCHADD_URL = "api_room_batchAddMember";
    public static final String API_PLAT_UPDATE = "api_plat_update";
    private static final String API_ROOM_UPDATE = "api_room_update";

    /** 聊天室排行榜热任务 */
    @GetMapping(value = "/chatLeaderBoardTask", produces = {"application/json;charset=UTF-8"})
    public void get(HttpServletRequest request, HttpServletResponse response) throws ServiceException {
        chatLeaderBoardService.creatLeaderBoard(null);
    }

    @ApiOperation(value = "平台聊天室限制配置/聊天室成员管理/关键词管理/聊天室房间管理/角色管理/聊天室自定义消息管理")
    @PostMapping(value = "/{url}", produces = {"application/json;charset=UTF-8"})
    public String post(@PathVariable String url, @RequestBody String body, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String apiUrl = getApiUrl(url);
        //获取当前租户标识
        String dbSuffix = tenantConfig.getTenantCode();
        //聊天室批量添加请求特殊处理处理
        if (StringUtils.contains(url, ROOM_MEMBER_BATCHADD_URL)) {
            body = dealAddRoomMenberBody(body, dbSuffix);
        }
        //聊天室修改平台开关特殊处理处理
        if (StringUtils.contains(url, API_PLAT_UPDATE)) {
            otthService.pushChatOpen(body);
            updateChatEnable(body);
        }
        //聊天室房间管理特殊处理
        if (StringUtils.contains(url, API_ROOM_UPDATE)) {
            body=chekGameStuats(body);
        }
        log.info("API=" + apiUrl + ":" + body);
        return otthService.otthProxyHttpPost(apiUrl, body, request, dbSuffix);
    }

    @ApiOperation(value = "平台聊天室限制配置/聊天室成员管理/关键词管理/聊天室房间管理/角色管理/聊天室自定义消息管理")
    @GetMapping(value = "/{url}", produces = {"application/json;charset=UTF-8"})
    public Object get(@PathVariable String url, HttpServletRequest request, HttpServletResponse response, PageDTO page) throws Exception {
        String apiUrl = getApiUrl(url);
        return otthService.otthProxyHttpGet(apiUrl, request, response, page);
    }

    @ApiOperation(value = "获取彩票游戏类型")
    @GetMapping(value = "/getLottTypeList", produces = {"application/json;charset=UTF-8"})
    public List<LotteryCodeVo> getLottTypeList() throws Exception {
        return otthService.getLottTypeList();
    }

    /** 中奖推送接口 */
    @PostMapping("/pushLotteryWin")
    public void pushLotteryWin(@RequestBody List<PushLottWinVo> lottWinVos, HttpServletRequest request) {
        otthService.pushLotteryWin(lottWinVos, request);
    }

    /** 分享彩票下注 */
    @RequestMapping(value = "/cpbet", method = RequestMethod.POST)
    public void cpbet(@RequestBody List<PushCPBetMessageReq> req, HttpServletRequest request) {
        otthService.cpbet(req, request);
    }

    /** 修改平台聊天室开关 */
    private void updateChatEnable(String body) {
        JSONObject json = JSONObject.parseObject(body);
        Integer chatOpen = json.getInteger("chatOpen");
        String cpChatEnable = null;
        if (EnableEnum.DISABLED.code() == chatOpen) {
            cpChatEnable = "off";
        } else {
            cpChatEnable = "on";
        }
        sysTenantSettingService.updateChatEnable(cpChatEnable);
    }

    /** 查找聊天室会员 */
    @RequestMapping(value = "/getChatUser", method = RequestMethod.GET)
    public ChatUserVO getChatUser(String account) throws Exception {
        return otthService.getChatUser(account);
    }

    /** 给游戏调用的更新游戏状态 */
    @PostMapping("updateGameStatus")
    public void updateGameStuats(String gameId, int gameStatus) {
        //游戏维护更新自定义中奖推送
        chatPushPlanService.updatePushPlan(gameId, gameStatus);
        //更新房间管理
        otthService.updateGameStuats(gameId,gameStatus);
    }

    private String getApiUrl(String url){
        String chatDomain = tenantDomainService.getChatDomain();
        url = chatDomain + "/" + url.replace("_", "/");
        return url;
    }

    private String dealAddRoomMenberBody(String body, String plateCode) throws Exception{

        List<RoomMember> roomMembers = JSONArray.parseArray(body, RoomMember.class);

        roomMembers.forEach(roomMember -> {
            roomMember.setPlatCode(plateCode);
            // 設置會員層級與平臺層級一樣
            roomMember.setLevel(1);
        });
        return JsonUtils.writeValue(roomMembers);
    }

    private String chekGameStuats(String body) {
        List<LotteryCodeVo> lottTypeList = otthService.getLottTypeList();
        List<String> lottCodes = lottTypeList.stream().map(LotteryCodeVo::getLottCode).collect(Collectors.toList());
        JSONObject jsonObject = JSONObject.parseObject(body);
        String game = jsonObject.getString("planGame");
        if (StringUtils.isNotBlank(game)){
            List<String> list=new ArrayList<>();
            Collections.addAll(list, game.split(","));
            list.removeIf(x->!lottCodes.contains(x));
            jsonObject.put("planGame", Joiner.on(",").join(list));
        }
        return jsonObject.toJSONString();
    }

}