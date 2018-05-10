package com.printfcoder.abc.springcloud.accountcenter.account.domain;

import lombok.Data;

@Data
public class Account {

    private String loginName;

    /**
     * 暂时先用明文
     */
    private String pwd;

    private String accountName;

}


