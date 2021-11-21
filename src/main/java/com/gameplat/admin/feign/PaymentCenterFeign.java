package com.gameplat.admin.feign;


import com.gameplat.admin.model.bean.ProxyCallbackContext;
import com.gameplat.admin.model.bean.ProxyDispatchContext;
import com.gameplat.admin.model.bean.ProxyPayBackResult;
import com.gameplat.admin.model.bean.ReturnMessage;
import com.gameplat.web.config.feign.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "payment-service",configuration = FeignClientConfig.class)
public interface PaymentCenterFeign {

  @PostMapping("/api/internal/proxyPay/onlineProxyPay/{code}/{name}")
  String onlineProxyPay(@RequestBody ProxyDispatchContext context, @PathVariable String code,
      @PathVariable String name);

  @PostMapping("/api/internal/proxyPay/onlineQueryProxyPay/{code}/{name}")
  ReturnMessage onlineQueryProxyPay(@RequestBody ProxyDispatchContext context,
      @PathVariable String code, @PathVariable String name);

  @PostMapping("/api/internal/proxyPay/asyncCallbackProxyPay/{code}/{name}")
  ProxyPayBackResult asyncCallbackProxyPay(@RequestBody ProxyCallbackContext context,
      @PathVariable String code, @PathVariable String name);
}