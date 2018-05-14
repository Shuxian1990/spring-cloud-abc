package com.printfcoder.abc.springcloud.plancenter.account.client;

import com.printfcoder.abc.springcloud.accountcenter.account.domain.Account;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "${account.api.application-name}")
@RequestMapping(value = "account")
public interface AccountClient {

    @RequestMapping(method = RequestMethod.GET, value = "get-account-by-loginname-and-pwd")
    Account getAccountByLoginNameAndPwd(@RequestParam("loginName") String loginName, @RequestParam("pwd") String pwd);
}