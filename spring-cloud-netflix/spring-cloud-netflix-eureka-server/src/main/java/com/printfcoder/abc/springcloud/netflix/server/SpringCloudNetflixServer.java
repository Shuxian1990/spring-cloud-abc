package com.printfcoder.abc.springcloud.netflix.server;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@EnableEurekaServer
@SpringBootApplication
public class SpringCloudNetflixServer {

    public static void main(String[] args) {
        SpringApplication.run(SpringCloudNetflixServer.class, args);
    }
}
