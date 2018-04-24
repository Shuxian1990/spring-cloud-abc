[前一篇][第一篇]我们介绍了Spring Cloud的基本概念，并实现了初步实现了`用户服务`。

Spring Cloud Netflix为Spring Boot应用提供[Netflix OSS（开源软件服务）][netflix_oss]集成与自动配置，和其它Spring应用一样的使用风格。只需要极少的标记就能快速激活和配置通用的组件，通过它们使用久经考验的Netflix组件来构建大型的分布式应用系统。

这些组件提供包括服务发现（Eureka）、断路器（Hystrix）、智能路由（Zuul）和客户端负载均衡（Ribbon）。

# 服务发现 Eureka

服务发现是基于微服务架构的关键点。管理每一个客户端或者基于某种形式的约定非常困难而且做起来很脆弱。Eureka是Netflix服务发现的服务端与客户端。服务端可以配置和部署达到高可用，它们之间会共享注册上来的服务的状态

## Eureka Server

Eureka Server只需要加入一个依赖：

```xml
<dependencies>
     <dependency>
         <groupId>org.springframework.cloud</groupId>
         <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
     </dependency>
</dependencies>
```

创建服务也非常简单：

```java
@EnableEurekaServer
@SpringBootApplication
public class SpringCloudNetflixEurekaServer {
    public static void main(String[] args) {
        SpringApplication.run(SpringCloudNetflixEurekaServer.class, args);
    }
}
```

启动后用浏览器打开[本地8761](http://localhost:8761)， 就可以看到Eureka后台。

### 高可用、区域和分区（Zone & Region）

Eureka服务并不会去存储状态，而是所有应用服务向Eureka发送心跳，以一定的频率保持注册在线，这一切都是在内存中保存的。客户端也会自己维护从Eureka上获取来的注册信息，所以客户端没有必要每次向服务端的请求都要获取整个注册表信息。

默认情况下，Eureka Server端同时也是Eureka Client，并且需要至少一个的service-url指向配对的Eureka Server Zone和Region。如果不提供这个URL，服务本身也是会运行的，只是日志中会有一堆警告显示不能向对方注册。

### 独立模式

客户端与服务端缓存结合和检测心跳可以让独立的Eureka Server有弹性地适应故障，就像有些监控和弹性运行时服务一样（比如Cloud Foundry），独立模式中：

```yml
eureka:
  instance:
    hostname: localhost
  client:
    register-with-eureka: false
    fetch-registry: false
    service-url:
      default-zone: http://${eureka.instance.hostname}:${server.port}/eureka/
```

可以看到`register-with-eureka`和`fetch-registry`都设置成了`false`，这个就是让Eureka工作在独立模式中，让它不要尝试去连接他的配对Eureka Server并且没有找到时也不要报错误。

### 服务互发现

Eureka通过多实例部署、互相注册达到弹性与高可用。默认情况下，互相注册是打开的，通过`serviceUrl`向其它实例注册。

下面这段是两个Eureka Server的`application.yml`配置

```yml
---
spring:
  profiles: peer1
eureka:
  instance:
    hostname: peer1
  client:
    serviceUrl:
      defaultZone: http://peer2/eureka/

---
spring:
  profiles: peer2
eureka:
  instance:
    hostname: peer2
  client:
    serviceUrl:
      defaultZone: http://peer1/eureka/
```

Peer1与Peer2互相注册。运行的时候可以在host文件中配上peer1和peer2域名地址。`eureka.instance.hostname`占位符在单机部署时其实是可以不用的，因为通过`java.net.InetAddress`可以找到自身的hostname。

Peer之间通过直连同步注册信息。

下面是三个Eureka Server实例的`application.yml`

```yml
eureka:
  client:
    serviceUrl:
      defaultZone: http://peer1/eureka/,http://peer2/eureka/,http://peer3/eureka/

---
spring:
  profiles: peer1
eureka:
  instance:
    hostname: peer1

---
spring:
  profiles: peer2
eureka:
  instance:
    hostname: peer2

---
spring:
  profiles: peer3
eureka:
  instance:
    hostname: peer3
```

为了减少篇幅，公用的部分在最上面，不一样的在`---`分行占位符之间。

### 使用IP地址

有些情况下，让Eureka通知应用使用ip而不是用hostname。可以设置`eureka.instance.preferIpAddress=true`，然后应用注册到Eureka时，它使用的就是它的ip地址，而不是hostname。

## Eureka Client

客户端注册到Eureka服务时，它需要像提供主机名、应用名、端口、主页、健康检测URL等等元数据。Eureka会接收到从每一个客户端实例中发送来的心跳消息。如果心跳在配置好的时间表中没有发送过去，Eureka就会把这个实例从注册表中移除。

我们用[前一篇][第一篇]中实现的`用户服务`来充当客户端。

### 导入依赖

使用Eureka Client也非常简单，在项目的maven pom.xml引入下面的依赖：

```xml
<dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
</dependencies>
```

### 注册

然后通过`@EnableEurekaClient`就可以启用：

```java
@ComponentScan("com.printfcoder.abc.springcloud.accountcenter")
@MapperScan("com.printfcoder.abc.springcloud.accountcenter.repository")
@EnableEurekaClient
@SpringBootApplication
public class AccountCenterServer {
    public static void main(String[] args) {
        SpringApplication.run(AccountCenterServer.class, args);
    }
}
```

上面这个示例是完整的Spring Boot风格的app。这个例子中，使用了`@EnableEurekaClient`标记，也并非只有Eureka服务发现可用，也可以使用`@EnableDiscoveryClient`。然后配置注册到Eureka Server的URL：

```yml
eureka:
  client:
    service-url:
      default-zone: http://localhost:8761/eureka/
```

`default-zone`比较神奇字符串值，它告诉客户端使用这个作为默认的Eureka服务地址。

> `Discovery Service`服务发现的实现中，像eureka, consul, zookeeper都有实现，而`@EnableDiscoveryClient`是spring-cloud-common中的实现。`@EnableEurekaClient`则只是给Eureka配套的服务发现。

`spring-cloud-starter-netflix-eureka-client`依赖中的jar包会让应用初始化为Eureka实例（instance）与客户端（client）。可以通过`eureka.instance.*`来控制，只要保证`spring.application.name`不为空，这些默认的配置就可以不用管。

关闭Eureka客户端发现功能，可以设置`eureka.client.enabled`为`false`。

启动后用浏览器打开[本地8761](http://localhost:8761)， 就可以看到Eureka后台有名叫`USER_CENTER_SERVICE`服务注册到了它上面。

### Eureka 服务验证

Eureka支持在`default-zone`URL中指定基础的HTTP验证，比如`http://user:password@localhost:8761/eureka`。如果要更复杂的方式，可以实现`DiscoveryClientOptionalArgs`,把`ClientFilter`注入到它里面，所有从客户端到服务端的调用都会应用到。

> 受限于Eureka的实现，目前不能做到给每个Eureka服务都声明各自的验证方式，只有第一个被发现的服务的验证方式会生效。

### 状态页与健康指示器

Spring Boot应用依赖Spring Boot Actuator后，默认就会有两个endpoint页面：状态页（/info）和健康指示器（/health)。如果要改动这个url，特别是在应用被发布在其它目录（比如 `server.servletPath=/foo`）或者管理路由（`management.contextPath=/admin`）时可以通过配置来修改,让其指向其它目录：

```yml
eureka:
  instance:
    status-page-url: ${management.context-path}/info
    health-check-url: ${management.context-path}/health
```

### 注册安全的应用

如果app要通过HTTPS连接，那么要分别设置`eureka.instance.[non-secure-port-enabled,secure-port-enabled]=[false,true]`。
这样配置会让Eureka发布实现信息时偏向于使用安全链接。Spring Cloud的`DiscoveryClient`就会返回`https://...`的URL，并且Eureka实例健康检测URL也是安全链接。

### 健康检测

默认情况下，Eureka使用客户端心跳来判定是否客户端仍在工作。除了特殊情况，Discovery Client不会传播的每个Spring Boot Actuator当前应用的健康检测状态。也就是说，成功注册到Eureka后，就会声明该应用处于`工作`状态。不过可以通过声明Eureka健康检测改变这个默认方式，健康检测会把应用的状态传送给Eureka，该配置会把应用的状态传播到Eureka。

```yml
eureka:
  client:
    health-check:
      enabled: true
```

> `eureka.client.health-check.enabled=true` 只能在`application.yml(properties)`中配置，如果在`bootstrap.yml`会导致不好的影响，比如注册到Eureka时，状态会是`UNKNOWN`。

可以实现`com.netflix.appinfo.HealthCheckHandler`来完成更复杂的健康检测。

## 为什么注册服务会慢

实例启动后，会有周期性的心跳发送到Eureka服务（通过`service-url`指定)，默认的周期是30s。服务在启动后客户端无法访问，直到客户端、实例、服务三者之间的缓存都更新了之后，客户端才能访问到服务接口，所以，可能会花掉3*30这么长的周期。可以通过设置`eureka.instance.lease-renewal-interval-in-seconds`调小时加快客户端可以向服务端访问的进度。在生产环境中，建议保留默认值，因为30秒是比较合适的值，服务会假设这个是更新周期进行一系列内部计算。

# 总结

本章简单介绍了Eureka的服务端与客户端基本配置与部署，顺带讲了健康检测等特性，后面随着章节的深入，会加入分区、健康检测（深入讲解）等。敬请期待！

# 相关链接

1. [netflix 上手1][参考文章1]
2. [netflix 上手2][参考文章2]

## 本系列文章

1. [开篇][第一篇]
2. [Netflix Eureka][第二篇]

[第一篇]: https://printfcoder.github.io/myblog/spring/2018/04/12/abc-spring-cloud-part-1/
[第二篇]: https://printfcoder.github.io/myblog/spring/2018/04/13/abc-spring-cloud-part-2-netflix-eureka/

[netflix_oss]: https://netflix.github.io/
[参考文章1]: https://cloud.spring.io/spring-cloud-netflix/multi/multi__service_discovery_eureka_clients.html
[参考文章2]: https://cloud.spring.io/spring-cloud-static/spring-cloud.html#_spring_cloud_netflix

[本文代码]: https://github.com/printfcoder/spring-cloud-abc/tree/basic-part2-netflix-eureka