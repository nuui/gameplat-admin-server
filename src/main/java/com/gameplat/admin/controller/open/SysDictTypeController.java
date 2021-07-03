package com.gameplat.admin.controller.open;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gameplat.admin.model.dto.SysDictTypeAddDto;
import com.gameplat.admin.model.dto.SysDictTypeEditDto;
import com.gameplat.admin.model.dto.SysDictTypeQueryDto;
import com.gameplat.admin.model.entity.SysDictType;
import com.gameplat.admin.model.vo.SysDictTypeVo;
import com.gameplat.admin.service.SysDictTypeService;
import com.gameplat.common.constant.ServiceApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ServiceApi.OPEN_API + "/dictType")
public class SysDictTypeController {

  @Autowired private SysDictTypeService sysDictTypeService;

  @GetMapping(value = "/queryAll")
  public IPage<SysDictTypeVo> queryAll(IPage<SysDictType> page, SysDictTypeQueryDto queryDto) {
    return sysDictTypeService.queryPage(page, queryDto);
  }

  @PostMapping(value = "/save")
  public void save(SysDictTypeAddDto sysDictTypeAddDto) {
    sysDictTypeService.save(sysDictTypeAddDto);
  }

  @PostMapping(value = "/update")
  public void update(SysDictTypeEditDto sysDictTypeEditDto) {
    sysDictTypeService.update(sysDictTypeEditDto);
  }

  @DeleteMapping(value = "/delete/{id}")
  public void delete(@PathVariable("id") Long id) {
    sysDictTypeService.delete(id);
  }
}
