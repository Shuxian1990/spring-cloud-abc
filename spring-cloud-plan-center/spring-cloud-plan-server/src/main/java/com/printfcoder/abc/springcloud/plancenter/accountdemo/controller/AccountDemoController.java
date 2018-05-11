package com.printfcoder.abc.springcloud.plancenter.accountdemo.controller;


import com.printfcoder.abc.springcloud.accountcenter.account.domain.Account;
import com.printfcoder.abc.springcloud.plancenter.accountdemo.service.AccountDemoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("plan-account-demo")
public class AccountDemoController {

    @Autowired
    private AccountDemoService accountDemoService;

    @GetMapping("get-account-by-loginname-and-pwd")
    public Account getAccountByLoginNameAndPwd(@RequestParam("loginName") String loginName, @RequestParam("pwd") String pwd) {
        return accountDemoService.getAccountByLoginNameAndPwd(loginName, pwd);
    }
}
