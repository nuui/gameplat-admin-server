package com.gameplat.admin.controller.open.game;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplat.admin.enums.GameRebatePeriodStatus;
import com.gameplat.admin.enums.GameRebateReportStatus;
import com.gameplat.admin.model.domain.GameRebateDetail;
import com.gameplat.admin.model.domain.GameRebatePeriod;
import com.gameplat.admin.model.dto.GameRebatePeriodQueryDTO;
import com.gameplat.admin.model.dto.OperGameRebatePeriodDTO;
import com.gameplat.admin.model.vo.GameRebatePeriodVO;
import com.gameplat.admin.service.GameRebateDetailService;
import com.gameplat.admin.service.GameRebatePeriodService;
import com.gameplat.admin.service.GameRebateReportService;
import com.gameplat.base.common.exception.ServiceException;
import com.gameplat.redis.api.RedisService;
import com.gameplat.redis.exception.RedisOpsResultIsNullException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/game/gameRebatePeriod/")
public class GameRebatePeriodController {

  private static final String GAME_REBATE_PAY_REDIS_LOCK = "game_rebate_pay_redis_lock";
  private static final String GAME_REBATE_RUNNING_TASK_NAME = "game_rebate_running_task_name";

  @Autowired
  private GameRebatePeriodService gameRebatePeriodService;

  @Autowired
  private GameRebateDetailService gameRebateDetailService;

  @Autowired
  private GameRebateReportService gameRebateReportService;

  @Autowired
  private RedisService redisService;

  @GetMapping(value = "queryAll")
  public IPage<GameRebatePeriodVO> queryGameRebatePeriod(Page<GameRebatePeriod> page, GameRebatePeriodQueryDTO dto) {
    return gameRebatePeriodService.queryGameRebatePeriod(page,dto);
  }


  @PostMapping(value = "add")
  public void add(@RequestBody OperGameRebatePeriodDTO dto) {
    gameRebatePeriodService.addGameRebatePeriod(dto);
  }

  @PutMapping(value = "update")
  public void update(@RequestBody OperGameRebatePeriodDTO dto){
    gameRebatePeriodService.updateGameRebatePeriod(dto);
  }

  @PostMapping(value = "delete")
  public void delete(@RequestBody OperGameRebatePeriodDTO dto){
    gameRebatePeriodService.deleteGameRebatePeriod(dto.getId(), dto.getOnly());
  }


  @PostMapping(value = "settle")
  public void settle(@RequestBody OperGameRebatePeriodDTO dto){
    // 正在发放、回收，不允许进行结算操作
    try {
      redisService.getStringOps().setEx(GAME_REBATE_PAY_REDIS_LOCK, 0, 3600, TimeUnit.SECONDS);
      GameRebatePeriod gameRebatePeriod = gameRebatePeriodService.getById(dto.getId());
      if (gameRebatePeriod == null) {
        redisService.getKeyOps().delete(GAME_REBATE_PAY_REDIS_LOCK);
        throw new ServiceException("真人返水期号不存在");
      }
      if (gameRebatePeriod.getStatus() != GameRebatePeriodStatus.UNSETTLED.getValue()) {
        redisService.getKeyOps().delete(GAME_REBATE_PAY_REDIS_LOCK);
        throw new ServiceException("期号状态不是未结算,不能进入结算操作");
      }
      try{
        gameRebatePeriodService.settle(dto.getId());
      } finally {
        redisService.getKeyOps().delete(GAME_REBATE_PAY_REDIS_LOCK);
      }
    } catch (RedisOpsResultIsNullException e) {
      String taskName = (String) redisService.getStringOps().get(GAME_REBATE_RUNNING_TASK_NAME);
      throw new ServiceException(String.format("正在执行[%s]，请稍后重试！", taskName));
    }
  }


  @PostMapping(value = "batchAccept")
  public void accept(@RequestBody OperGameRebatePeriodDTO dto){
    GameRebatePeriod rebatePeriod = gameRebatePeriodService.getById(dto.getId());
    if (rebatePeriod == null) {
      throw new ServiceException("返水期号不存在");
    }
    if (rebatePeriod.getStatus() != GameRebatePeriodStatus.SETTLED.getValue()) {
      throw new ServiceException("期号状态不是结算状态,不能进入派发操作");
    }
    String taskName = String.format("发放 %s", dto.getName());
    asyncAndSingleTask(taskName, () -> {
      List<GameRebateDetail> gameRebateDetailList = gameRebateDetailService
          .gameRebateDetailByStatus(dto.getId(), GameRebateReportStatus.UNACCEPTED.getValue());
      String statTime = "";
      for (GameRebateDetail gameRebateDetail : gameRebateDetailList) {
        try {
          if (StringUtils.isEmpty(statTime)) {
            statTime = gameRebateDetail.getStatTime();
          }
          gameRebateReportService.accept(dto.getId(), gameRebateDetail.getMemberId(),
              gameRebateDetail.getRealRebateMoney(),
              gameRebateDetail.getPeriodName() + "-真人返水");
        } catch (Exception e) {
          log.error("派发异常: " + JSONUtil.toJsonStr(gameRebateDetail));
          throw new RuntimeException("派发异常", e);
        }
      }

      //更新状态
      GameRebatePeriod gameRebatePeriod = new GameRebatePeriod();
      gameRebatePeriod.setStatus(GameRebatePeriodStatus.ACCEPTED.getValue());
      LambdaUpdateWrapper<GameRebatePeriod> updateWrapper = Wrappers.lambdaUpdate();
      updateWrapper.eq(GameRebatePeriod::getId,dto.getId());
      if(!gameRebatePeriodService.update(gameRebatePeriod,updateWrapper)){
        throw new ServiceException("更新真人期数配置失败！");
      }
      // TODO 添加真人返水每日统计
      //this.userBusDayReportManager.userBusDayReportQueue(BusReportType.LIVE_REBATE.getValue(), statTime);
    });
  }

  /**
   * 真人返水回收:期数
   */
  @RequestMapping(value = "rollBack", method = RequestMethod.POST)
  public void rollBack(@RequestBody OperGameRebatePeriodDTO dto){
    GameRebatePeriod rebatePeriod = gameRebatePeriodService.getById(dto.getId());
    if (rebatePeriod == null) {
      throw new ServiceException("返水期号不存在");
    }
    if (rebatePeriod.getStatus() != GameRebatePeriodStatus.ACCEPTED.getValue()) {
      throw new ServiceException("期号状态不是派发状态,不能进入回收操作");
    }
    String taskName = String.format("回收 %s", dto.getName());
    asyncAndSingleTask(taskName, () -> {
      String statTime = "";
      List<GameRebateDetail> gameRebateDetailList = gameRebateDetailService
          .gameRebateDetailByStatus(dto.getId(), GameRebateReportStatus.ACCEPTED.getValue());
      for (GameRebateDetail gameRebateDetail : gameRebateDetailList) {
        try {
          if (StringUtils.isEmpty(statTime)) {
            statTime = gameRebateDetail.getStatTime();
          }
          gameRebateReportService
              .rollBack(gameRebateDetail.getMemberId(), gameRebateDetail.getPeriodId(),
                  gameRebateDetail.getPeriodName(), gameRebateDetail.getRealRebateMoney(),
                  gameRebateDetail.getRemark());
        } catch (Exception e) {
          log.error("回收异常: " + JSONUtil.toJsonStr(gameRebateDetail));
          throw new RuntimeException("回收异常", e);
        }
      }

      //更新状态
      GameRebatePeriod gameRebatePeriod = new GameRebatePeriod();
      gameRebatePeriod.setStatus(GameRebatePeriodStatus.ROLLBACKED.getValue());
      LambdaUpdateWrapper<GameRebatePeriod> updateWrapper = Wrappers.lambdaUpdate();
      updateWrapper.eq(GameRebatePeriod::getId,dto.getId());
      gameRebatePeriodService.update(gameRebatePeriod,updateWrapper);

      // TODO 添加真人返水每日统计
      //this.userBusDayReportManager.userBusDayReportQueue(BusReportType.LIVE_REBATE.getValue(), statTime);
    });
  }


  private void asyncAndSingleTask(String taskName, Runnable task) throws ServiceException {
    try {
      redisService.getStringOps().setEx(GAME_REBATE_PAY_REDIS_LOCK, 0, 3600,TimeUnit.SECONDS);
      redisService.getStringOps().set(GAME_REBATE_RUNNING_TASK_NAME, taskName);
      new Thread(() -> {
        try {
          task.run();
        } catch (Throwable e) {
          e.printStackTrace();
        } finally {
          redisService.getKeyOps().delete(GAME_REBATE_PAY_REDIS_LOCK);
        }
      }).start();
    } catch (RedisOpsResultIsNullException e) {
      String name = (String) redisService.getStringOps().get(GAME_REBATE_RUNNING_TASK_NAME);
      throw new ServiceException(String.format("正在执行[%s]，请稍后重试！", name));
    }
  }

}