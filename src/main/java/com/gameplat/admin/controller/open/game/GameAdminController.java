package com.gameplat.admin.controller.open.game;

import com.gameplat.admin.model.dto.GameBalanceQueryDTO;
import com.gameplat.admin.model.dto.OperGameTransferRecordDTO;
import com.gameplat.admin.model.vo.GameBalanceVO;
import com.gameplat.admin.model.vo.GameRecycleVO;
import com.gameplat.admin.service.GameAdminService;
import com.gameplat.admin.service.MemberService;
import com.gameplat.common.constant.ServiceName;
import com.gameplat.log.annotation.Log;
import com.gameplat.log.enums.LogType;
import com.gameplat.redis.api.RedisService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/admin/game")
public class GameAdminController {
  @Autowired private GameAdminService gameAdminService;
  @Autowired private MemberService memberService;
  @Autowired private RedisService redisService;

  /** 查询单个游戏平台余额 */
  @GetMapping(value = "/selectGameBalance")
  @PreAuthorize("hasAuthority('game:gameAdmin:select')")
  @Log(
      module = ServiceName.ADMIN_SERVICE,
      type = LogType.ADMIN,
      desc = "'查询会员游戏金额，账号：'+#dto.account+',游戏平台编码：'+#dto.platformCode")
  public GameBalanceVO selectGameBalance(GameBalanceQueryDTO dto) throws Exception {
    return gameAdminService.selectGameBalance(dto);
  }

  /** 转账到游戏 */
  @PostMapping(value = "/transferToGame")
  @PreAuthorize("hasAuthority('game:gameAdmin:transfer')")
  @Log(
      module = ServiceName.ADMIN_SERVICE,
      type = LogType.ADMIN,
      desc =
          "'转换会员余额到游戏，账号：'+#record.account+',游戏平台编码：'+#record.platformCode+',金额：'+#record.amount")
  public void transferToGame(@RequestBody OperGameTransferRecordDTO record) throws Exception {
    gameAdminService.transferToGame(record);
  }

  /** 回收单个游戏金额 */
  @PostMapping(value = "/recyclingAmount")
  @PreAuthorize("hasAuthority('game:gameAdmin:recycle')")
  @Log(
      module = ServiceName.ADMIN_SERVICE,
      type = LogType.ADMIN,
      desc = "'回收会员游戏金额，账号：'+#dto.account+',游戏平台编码：'+#dto.platformCode")
  public GameRecycleVO recyclingAmount(@RequestBody GameBalanceQueryDTO dto) {
    return gameAdminService.recyclingAmount(dto);
  }

  /** 没收游戏余额 */
  @RequestMapping(value = "/confiscated", method = RequestMethod.POST)
  @PreAuthorize("hasAuthority('game:gameAdmin:confiscated')")
  @Log(
      module = ServiceName.ADMIN_SERVICE,
      type = LogType.ADMIN,
      desc = "'没收会员游戏金额，账号='+#dto.account+',游戏平台编码：'+#dto.platformCode")
  public GameRecycleVO confiscatedAmount(@RequestBody GameBalanceQueryDTO dto) {
    return gameAdminService.confiscatedAmount(dto);
  }

  /** 没收所有游戏平台余额 */
  @PostMapping(value = "/confiscatedGameByAccount")
  @PreAuthorize("hasAuthority('game:gameAdmin:confiscatedAll')")
  @Log(
      module = ServiceName.ADMIN_SERVICE,
      type = LogType.ADMIN,
      desc = "'没收会员所有游戏平台金额，账号='+#dto.account")
  public List<GameRecycleVO> confiscatedGameByAccount(@RequestBody GameBalanceQueryDTO dto) {
    return gameAdminService.confiscatedGameByAccount(dto.getAccount());
  }

  /** 查询所有平台余额 一键查询 */
  @PostMapping(value = "/selectGameBalanceByAccount")
  @PreAuthorize("hasAuthority('game:gameAdmin:selectAll')")
  @Log(
      module = ServiceName.ADMIN_SERVICE,
      type = LogType.ADMIN,
      desc = "'查询会员所有游戏平台金额，账号='+#dto.account")
  public List<GameBalanceVO> selectGameBalanceByAccount(@RequestBody GameBalanceQueryDTO dto) {
    return gameAdminService.selectGameBalanceByAccount(dto.getAccount());
  }

  /** 一键回收 */
  @PostMapping(value = "/recyclingAmountByAccount")
  @PreAuthorize("hasAuthority('game:gameAdmin:recycleAll')")
  @Log(
      module = ServiceName.ADMIN_SERVICE,
      type = LogType.ADMIN,
      desc = "'回收会员所有游戏平台金额，account='+#dto.account")
  public List<GameRecycleVO> reclaimLiveAmountAsync(@RequestBody GameBalanceQueryDTO dto) {
    return gameAdminService.recyclingAmountByAccount(dto.getAccount());
  }
}
