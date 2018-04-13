package com.printfcoder.abc.springcloud.netflix.client;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@SpringBootApplication
@EnableEurekaClient
public class SpringCloudNetflixClient {

    public static void main(String[] args) {
        SpringApplication.run(SpringCloudNetflixClient.class, args);
    }
}
