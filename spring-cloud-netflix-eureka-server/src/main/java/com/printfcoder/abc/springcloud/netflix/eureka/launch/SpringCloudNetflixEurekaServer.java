package com.printfcoder.abc.springcloud.netflix.eureka.launch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@EnableEurekaServer
@SpringBootApplication
public class SpringCloudNetflixEurekaServer {

    public static void main(String[] args) {
        SpringApplication.run(SpringCloudNetflixEurekaServer.class, args);
    }
}