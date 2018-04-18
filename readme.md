
# basic-netflix-eureka

本分支是netflix-eureka基础示例，里面包含Netflix Server与Client，配置使用的是匹配中心Config Server。

# 服务发现 Eureka 

## Eureka 启动顺序

1. 启动Config Server，在spring-cloud-config/spring-cloud-config-server
2. 启动Netflix Eureka Server，在spring-cloud-netflix/spring-cloud-netflix-eureka-server
3. 启动Netflix Eureka Client，在spring-cloud-netflix/spring-cloud-netflix-eureka-client

# 相关链接

1. [Spring-Cloud-Config示例说明](https://printfcoder.github.io/myblog/spring/2018/03/28/spring-cloud-config/)
2. [示例说明参考](https://printfcoder.github.io/myblog/spring/2018/04/13/spring-cloud-netflix/)
