package com.printfcoder.abc.springcloud.plancenter.accountdemo.service;

import com.printfcoder.abc.springcloud.accountcenter.account.domain
    .Account;
import com.printfcoder.abc.springcloud.plancenter.account.client.AccountClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AccountDemoService {


    @Autowired
    private AccountClient accountClient;

    public Account getAccountByLoginNameAndPwd(String loginName, String pwd) {
        return accountClient.getAccountByLoginNameAndPwd(loginName, pwd);
    }
}
