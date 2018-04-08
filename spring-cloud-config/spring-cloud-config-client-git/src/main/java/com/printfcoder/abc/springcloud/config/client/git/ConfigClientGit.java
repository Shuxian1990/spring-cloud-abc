package com.printfcoder.abc.springcloud.config.client.git;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class ConfigClientGit {

    public static void main(String[] args) {
        SpringApplication.run(ConfigClientGit.class, args);
    }
}


@RefreshScope
@RestController
class MessageRestController {

    @Value("${printfcoder.name}")
    private String message;

    @RequestMapping("/message")
    String getMessage() {
        return this.message;
    }
}