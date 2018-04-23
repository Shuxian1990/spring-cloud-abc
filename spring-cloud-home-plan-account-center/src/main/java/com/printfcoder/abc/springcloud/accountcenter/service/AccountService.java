package com.printfcoder.abc.springcloud.accountcenter.service;

import com.printfcoder.abc.springcloud.accountcenter.domain.Account;
import com.printfcoder.abc.springcloud.accountcenter.repository.AccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    @Autowired
    private AccountMapper accountMapper;

    public Account queryAccountByLoginName(String loginName) {
        return accountMapper.queryAccountByLoginName(loginName);
    }
}
