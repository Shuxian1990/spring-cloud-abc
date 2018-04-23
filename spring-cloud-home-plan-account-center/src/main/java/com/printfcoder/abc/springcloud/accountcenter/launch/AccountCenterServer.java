package com.printfcoder.abc.springcloud.accountcenter.launch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan("com.printfcoder.abc.springcloud.accountcenter")
@MapperScan("com.printfcoder.abc.springcloud.accountcenter")
@SpringBootApplication
public class AccountCenterServer {
    public static void main(String[] args) {
        SpringApplication.run(AccountCenterServer.class, args);
    }
}
