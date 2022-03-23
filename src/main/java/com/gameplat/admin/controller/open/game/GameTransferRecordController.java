package com.gameplat.admin.controller.open.game;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplat.admin.model.dto.GameTransferRecordQueryDTO;
import com.gameplat.admin.model.dto.OperGameTransferRecordDTO;
import com.gameplat.admin.model.vo.PageDtoVO;
import com.gameplat.admin.service.GameAdminService;
import com.gameplat.admin.service.GamePlatformService;
import com.gameplat.admin.service.GameTransferRecordService;
import com.gameplat.model.entity.game.GamePlatform;
import com.gameplat.model.entity.game.GameTransferRecord;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/admin/game/gameTransferRecord")
public class GameTransferRecordController {

  @Autowired private GameTransferRecordService gameTransferRecordService;

  @Autowired private GameAdminService gameAdminService;

  @Autowired private GamePlatformService gamePlatformService;

  @GetMapping(value = "/queryPage")
  public PageDtoVO<GameTransferRecord> queryGameTransferRecord(
      Page<GameTransferRecord> page, GameTransferRecordQueryDTO dto) {
    return gameTransferRecordService.queryGameTransferRecord(page, dto);
  }

  /** 后台补单 */
  @PostMapping(value = "/fillOrders")
  public void fillOrders(@RequestBody OperGameTransferRecordDTO dto) throws Exception {
    gameAdminService.fillOrders(dto);
  }

  @GetMapping(value = "/queryPlatformByMemberId/{memberId}")
  public List<GamePlatform> queryPlatformByMemberId(@PathVariable("memberId") Long memberId) {
    List<String> platformCodeList =
        gameTransferRecordService.findPlatformCodeList(memberId).stream()
            .map(GameTransferRecord::getPlatformCode)
            .collect(Collectors.toList());
    return gamePlatformService.queryByTransfer().stream()
        .filter(item -> platformCodeList.contains(item.getCode()))
        .collect(Collectors.toList());
  }
}
