package com.printfcoder.abc.springcloud.plancenter.launch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan("com.printfcoder.abc.springcloud.plancenter")
@EnableFeignClients(basePackages = {"com.printfcoder.abc.springcloud.plancenter.**.client"})
@SpringBootApplication
public class PlanCenterServer {

    public static void main(String[] args) {
        SpringApplication.run(PlanCenterServer.class, args);
    }
}