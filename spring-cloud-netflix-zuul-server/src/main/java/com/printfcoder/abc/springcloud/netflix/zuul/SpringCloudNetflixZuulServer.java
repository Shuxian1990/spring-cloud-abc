package com.printfcoder.abc.springcloud.netflix.zuul;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;

@EnableZuulProxy
@SpringBootApplication
public class SpringCloudNetflixZuulServer {
    public static void main(String[] args) {
        SpringApplication.run(SpringCloudNetflixZuulServer.class, args);
    }
}