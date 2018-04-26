package com.printfcoder.abc.springcloud.accountcenter.controller;

import com.printfcoder.abc.springcloud.accountcenter.domain.Account;
import com.printfcoder.abc.springcloud.accountcenter.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("account")
public class AccountController {

    Logger logger = LoggerFactory.getLogger(AccountController.class);

    @Value("${server.port}")
    private Integer serverPort;

    @Autowired
    private AccountService accountService;

    @GetMapping("get-account-by-loginname-and-pwd")
    public Account getAccountByLoginNameAndPwd(@RequestParam("loginName") String loginName, @RequestParam("pwd") String pwd) {

        logger.info("[getAccountByLoginNameAndPwd] 收到请求，本服务端口：{}", serverPort);

        Account account = accountService.queryAccountByLoginName(loginName);
        // 先只进行简单对比，暂不处理加密等
        if (account != null && pwd.equals(account.getPwd())) {
            return account;
        }
        return null;
    }
}
