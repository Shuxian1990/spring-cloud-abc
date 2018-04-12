package com.printfcoder.abc.springcloud.config.server;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.ActuatorMetricWriter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.bootstrap.encrypt.RsaProperties;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigServer
@ActuatorMetricWriter
public class SpringCloudConfigServer {

    @Bean
    public RsaProperties rsaProperties() {
        return new RsaProperties();
    }

    public static void main(String[] args) {
        SpringApplication.run(SpringCloudConfigServer.class, args);
    }
}
