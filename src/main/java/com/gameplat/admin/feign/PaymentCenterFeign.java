package com.gameplat.admin.feign;

import com.gameplat.admin.model.bean.ReturnMessage;
import com.gameplat.base.common.web.Result;
import com.gameplat.basepay.proxypay.thirdparty.ProxyCallbackContext;
import com.gameplat.basepay.proxypay.thirdparty.ProxyDispatchContext;
import com.gameplat.basepay.proxypay.thirdparty.ProxyPayBackResult;
import com.gameplat.web.config.feign.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "payment-service", configuration = FeignClientConfig.class)
public interface PaymentCenterFeign {

  @PostMapping("/api/internal/proxyPay/onlineProxyPay/{code}/{name}")
  Result<ProxyPayBackResult> onlineProxyPay(
      @RequestBody ProxyDispatchContext context,
      @PathVariable("code") String code,
      @PathVariable("name") String name);

  @PostMapping("/api/internal/proxyPay/onlineQueryProxyPay/{code}/{name}")
  Result<ReturnMessage> onlineQueryProxyPay(
      @RequestBody ProxyDispatchContext context,
      @PathVariable("code") String code,
      @PathVariable("name") String name);

  @PostMapping("/api/internal/proxyPay/asyncCallbackProxyPay/{code}/{name}")
  Result<ProxyPayBackResult> asyncCallbackProxyPay(
      @RequestBody ProxyCallbackContext context,
      @PathVariable("code") String code,
      @PathVariable("name") String name);
}
