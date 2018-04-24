package com.printfcoder.abc.springcloud.accountcenter.service;

import com.printfcoder.abc.springcloud.accountcenter.domain.Account;
import com.printfcoder.abc.springcloud.accountcenter.repository.AccountMapper;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    @Resource
    private AccountMapper accountMapper;

    public Account queryAccountByLoginName(String loginName) {
        return accountMapper.queryAccountByLoginName(loginName);
    }
}
