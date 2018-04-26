---
title: Spring Cloud ABC 之 Spring Cloud Netflix Zuul
date: 2018-04-15 08:40
categories:
 - Spring 
tags:
 - Spring Cloud
---

[前一篇][第二篇]我们介绍了Netflix Eureka的基本用法，并实现了初步实现了`服务注册`。

对于内部微服务的接口路由，比如`/`一般会定位到你的Web应用，而像`/api/users`或者`/api/plan`，则会被路由到`user`服务或`plan`服务。Netflix使用Zuul来实现请求的路由，Zuul是基于JVM实现的路由及服务端负载均衡器。

[Netflix主要用Zuul][Netflix_uses_zuul]来实现：

1. 认证（Authentication）
2. 检查（Insights）
3. 压测（Stress Testing）
4. 灰测（Canary Testing）
5. 动态路由（Dynamic Routing）
6. 服务迁移（Service Migration）
7. 负载限制（Load Shedding）
8. 安全（Security）
9. 静态响应处理（Static Response handling）
10. 主动流向管理（Active/Active traffic management）

> 金丝雀测试也就是灰度发布测试的初期阶段，历史上早期煤矿矿工在矿洞中前行时，用金丝雀来检测一氧化碳，当矿工带着金丝雀走到一氧化碳多的地方时，它就会敏感而躁动，所以引申而来，希望第一批用户在使用时可以充分反馈（主动或者通过流量日志等）以达到判断是否可以继续发布的标准。

Zuul的规则引擎支持基于JVM的语言编写的规则与过滤器，比如Java、Groovy。
> `zuul.max.host.connections`已经被改成两个配置，
`zuul.host.maxTotalConnections`和`zuul.host.maxPerRouteConnections`，默认值分别是200、20

# Zuul反向代理

我们会使用Zuul反向代理，把请求转发对应的后台应用和服务，这样可以避免后台服务直接暴露在外网，也顺便解决了单一接入层的跨域问题。

只需要在应用上main类加上`@EnableZuulProxy`标记即可，这个代理会把请求转向到对应的服务。一般而言，我们会把向`/user/***`的请求转到应用id为`user`的服务。代理使用`Ribbon`定位到它通过服务发现找到的服务。

> Zuul Starter并不包含有服务发现客户端（discovery client），所以要基于Service ID路由到服务，得在classpath中提供一个发现客户端（比如Eureka）

Zuul会自动添加服务，但是也可以选择忽略某些服务，通过配置`zuul.ignored-services`，在这个配置上配置好服务id的模式列表即可。如果服务的id匹配了这个模式，那么就会被忽略，但是在路由表中显式配置上了这个服务，那么不会被忽略。

比如，使用模式`*`，它会匹配所有的服务id：

```yml
 zuul:
  ignored-services: '*'
  routes:
    users: /myusers/**
```

上面的这个例子中，`users`不会被忽略，其它都会被忽略

对于细粒度的控制，可以单独给路径配置特殊的服务id：

```yml
zuul:
  routes:
    users:
      path: /myusers/**
      service-id: users_service
```

上面的这个例子中，所有请求以`/myusers`开头的都会被转向`users_service`。注意，`/myusers/*`只会匹配一层路由，而`/myusers/**`会匹配所有层级。

被代理服务可以通过service-id转向，也可以通过`url`（实实在在的地址）：

```yml
zuul:
  routes:
    users:
      path: /myusers/**
      url: http://example.com/users_service
```

但是，URL路由是不会经过`HystrixCommand`指令跳转的，也不用不了Ribbon的负载均衡。为了达到这点，得再行配置Service，比较麻烦，超出本节讨论的范围，后面进阶章节会再讨论。

# Cookies和Headers

你可以在同一系统的不同服务之间共享Headers，但是把请求的Headers泄露到外部应用就非常危险。可以在路由配置中忽略指定的header。像Cookies这种敏感的头部信息就比较特殊，如果你的应用是通过浏览器请求的，那么Cookies对于下游的服务就会引发问题，因为这些Cookies混杂在一起，让所有下游的服务都会认为它们来自同一个地方。

设计服务时就要小心，比如，只有一个服务会设置Cookies，那么你可能会让这些Cookies全程从后台传到调用者。而且，如果代理设置了Cookies，那么所有在同一个系统中的后台服务都可以共享它们，比如通过Spring Session把这些共享的状态串联起来。除了这些，对于在服务设置的Cookie，看上去对于调用者是没有什么用的，所以，推荐在配置中把不属于你的服务域的`Set-Cookie`和`Cookie`设为敏感。甚至对于域中的路由，也要考虑清楚Cookies在路由与代理之间传递有什么意思。

敏感的headers可以通过逗号分隔的字符串列表来配置：

```yml
zuul:
  routes:
    users:
      path: /myusers/**
      sensitive-headers: Cookie,Set-Cookie,Authorization
      url: https://downstream
```

> 上面的sensitive-headers是默认值，所以如果只是这些敏感的话，是不用配置的，除非有不一样的地方。这是Spring Cloud Netflix1.1中加的新特性，1.0是不能控制Headers的，而所有的Cookies都会两点之间传送）。

`sensitive-headers`其实就是黑名单，而默认值不是空。因此，要让Zuul一个都不忽略地向下游服务传送，就必须显式设置它为空，如果要把Cookies或认证头信息往下传是有必要这么设置的：

```yml
zuul:
  routes:
    users:
      path: /myusers/**
      sensitive-headers:
      url: https://downstream
```

也可以设置全局的`sensitive-headers`，通过设置`zuul.sensitive-headers`。但是显式设置路由的`sensitive-headers`，那么它会覆盖全局配置。

# 忽略头部信息

作为基于路由的敏感头配置补充，Spring Cloud Netflix给在下游服务之间交互时忽略头部增加了配置`zuul.ignored-headers`，包含request和response的头部。默认情况下，如果没有依赖Spring Security，这值就是空的。其它情况下，它们会被Spring Security设置成`security`安全头，比如缓存。

这么做是假定下游服务可能会增加自已的头信息，而我们希望这些头信息应该由代理填充。当依赖了Spring Security时，如果不想丢弃某些常见知名用安全头信息，可以设置`zuul.ignore-security-headers`为`false`。对于不想让Spring Security来处理响应安全头，而要让下游的服务处理时，就可以这么设置。

# 管理Endpoint接口

如果使用`@EnableZuulProxy`的同时也引入Spring Boot Actuator，那么会激活下面两个endpoint：

1. Routes
2. Filters

## Routes Endpoint

请求`/routes`时就会返回如下结构的数据：

```json
{
  "/stores/**": "http://localhost:8081"
}
```

在queryString中增加参数`?format=details`，返回信息可以更详细：

```json
{
  "/stores/**": {
    "id": "stores",
    "fullPath": "/stores/**",
    "location": "http://localhost:8081",
    "path": "/**",
    "prefix": "/stores",
    "retryable": false,
    "customSensitiveHeaders": false,
    "prefixStripped": true
  }
}
```

向`/route`发送post请求会强制刷新现有的路由（比如服务目录有变化时）。可以通过`endpoints.routes.enabled=false`关闭这个接口。

> 路由应该自动响应服务目录的变化，但是对于向`/routes`发送`post`请求也是一种立即更新变化的方式。

## Filters Endpoint

`/filters`提供返回Zuul过滤器`Map<Type,Filter>`的`Get`请求，Map中每一个过滤器类型Key对应的Value就是这个类型的过滤器与它们的详情（执行顺序、是否激活等）:

```json
{
    "error": [
        {
            "class": "org.springframework.cloud.netflix.zuul.filters.post.SendErrorFilter",
            "order": 0,
            "disabled": false,
            "static": true
        }
    ],
    "post": [
        {
            "class": "org.springframework.cloud.netflix.zuul.filters.post.SendResponseFilter",
            "order": 1000,
            "disabled": false,
            "static": true
        }
    ],
    "pre": [
        {
            "class": "org.springframework.cloud.netflix.zuul.filters.pre.DebugFilter",
            "order": 1,
            "disabled": false,
            "static": true
        }
    ]
}
```

# 上传文件

如果使用了`@EnableZuulProxy`代理，对于上传小文件到后台服务，问题不大。后面进阶部分我们再处理。

# QueryString编码 

请求中传入的QueryString，在经过Zuul过滤器时会被解码，这样，Zuul才有可能更改这些值。在发向后端的请求中会被重新编码，所以与原始输入查询字符串可能有差异，比如原始的是基于JavaScript的`encodeURIComponent`方法编码的，解码后再使用Java编码时可能就会有差异。对于大部分情况下，这不会引起大问题，但是还是有些Web服务器会比较挑剔。

可以通过配置`zuul.force-original-query-string-encoding=true`让queryString和原始值一样，但是这个标记只在`SimpleHostRoutingFilter`中才会生效，并且也不再能简单地通过`RequestContext.getCurrentContext().setRequestQueryParams(someOverriddenParameters)`来重写参数，因为QueryString直接从原始的`HttpServletRequest`中获取的。

# 内置纯净版Zuul

如果使用`@EnableZuulServer`（注意不是`@EnableZuulProxy`)，就可以运行只有Zuul Server的网关，没有代理或者其它可选的代理平台。所有在应用中创建的`ZuulFilter`类型的bean都会被自动装配到应用中，就像使用`@EnableZuulProxy`，但是`代理`过滤器就不会自动加到应用中。

如此看来，Zuul中的路由仍然对`zuul.routes`配置也是有响应的，但是没有服务发现与代理的能力。因此，`service-id`和`url`即使加到配置里也是无效的，下面的例子把所有的`/api/**`路径映射到Zuul过滤器链中：

```yml
zuul:
  routes:
    api: /api/**
```

本节我们不会讨论纯Zuul Server的使用场景，到进阶章节中会单独讨论。

# 关闭Zuul路由

Spring Cloud的Zuul组件在代理（proxy）和服务（server）模式中默认自带有几个`ZuulFilter`。可以查看[Zuul Filter包](https://github.com/spring-cloud/spring-cloud-netflix/tree/master/spring-cloud-netflix-zuul/src/main/java/org/springframework/cloud/netflix/zuul/filters)翻阅这些Filter。如果要关闭某个，只需这样设置即可`zuul.<SimpleClassName>.<filterType>.disable=true`。习惯上，`filters`后面的包就是Zuul过滤器类型。比如关闭`org.springframework.cloud.netflix.zuul.filters.post.SendResponseFilter`，那么要这么设置：

```properties
zuul.SendResponseFilter.post.disable=true
```

# Timeout 超时

要设置Zuul创建Socket连接超时和代理的请求读取超时，根据配置的情况有两个方式供选择：

1. 如果Zuul使用了服务发现，那么需要配置`ribbon.ReadTimeout`和`ribbon.SocketTimeout`两个属性
2. 如果Zuul配置了特殊的URL路由，那么就需要配置`zuul.host.connect-timeout-millis`、`zuul.host.socket-timeout-millis`

# 开始写代码

我们在上面的篇幅中介绍了Zuul的功能与如何主要特性设置，但是本节我们先不用到过滤器，也不做过度复杂的路由代理。我们只要让Zuul代理能把后台挂载在Eureka上的服务通过8080端口封装起来给外部使用。在我们的项目中，我们把`用户服务`（端口8090）通过Zuul代理（8080端口）向外界提供服务，只要外界能通过8080端口请求到`用户服务`的接口时，我们就Zuul代理服务就配置成功。本节我们只需要作简单的代理，因为登录功能还不完善，在后面进阶章节会逐步加入过滤器、鉴权、文件上传、头信息处理等特性。

## 创建项目加入依赖

具体代码目录，请查看本节代码[分支][本文代码]

首先我们把Zuul Starter加到项目中：

```xml
  <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-netflix-zuul</artifactId>
  </dependency>

  <!-- 注册到Eureka，让ribbon可以读取挂载到Eureka的服务 -->
  <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
  </dependency>

  <!-- 方便查看及管理路由等信息 -->
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
  </dependency>
```

eureka-client主要是用来让zuul中的Ribbon可以向EurekaServer读取注册的服务，以实现负载均衡等特性。

application.yml配置：

```yml
zuul:
  routes:
    user:
      path: /account/**
      service-id: User_Center_Service
      # 是否把前缀忽略掉，先不忽略
      strip-prefix: false
eureka:
  client:
    service-url:
      default-zone: http://localhost:8761/eureka/
    health-check:
      enabled: true
spring:
  application:
    name: netflix-zuul
management:
  security:
    # 暂时关闭安全检测，方便查看endpoint，外网时不要禁掉！！！
    enabled: false
```

在上面的配置中，我们把向本Zuul代理请求路径为`/account/*`的都转到应用名为`User_Center_Service`的服务上，`strip-prefix=false`是指`/account`前缀不会被去掉。

main 类：

```java
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
```

## 运行

这里假设你已经运行起Eureka Server和AccountCenterServer，否则服务无法注册与无法发现Zuul路由要转向的服务

切到zuul项目根目录下，建议使用IDE编译运行，方便调试

```shell
$mvn spring-boot:run -o
```

## 负载均衡

我们运行两台`User_Center_Service`服务，都在本机运行，分别是8090、8091端口，这样，来测试Zuul代理是否将请求均衡发到不同的服务上。

我们在AccountController上增加日志打印，只打印本服务的端口即可，这样我们在本机启动两个`用户服务`应用时就能打印出不同的端口以证明两台服务都收到请求：

```java
...
public class AccountController {

  Logger logger = LoggerFactory.getLogger(AccountController.class);

  @Value("${server.port}")
  private Integer serverPort;
  ...
  @GetMapping("get-account-by-loginname-and-pwd")
  public Account getAccountByLoginNameAndPwd(@RequestParam("loginName") String loginName, @RequestParam("pwd") String pwd) {

    logger.info("[getAccountByLoginNameAndPwd] 收到请求，本服务端口：{}", serverPort);
    ...
  }
}
```

运行两台服务后，在浏览器中请求`http://localhost:8080/account/get-account-by-loginname-and-pwd?loginName=test&pwd=123`**4**次：

```log
-- server:8091
2018-04-26 11:10:45.897  INFO 15880 --- [nio-8091-exec-2] c.p.a.s.a.controller.AccountController   : [getAccountByLoginNameAndPwd] 收到请求，本服务端口：8091
2018-04-26 11:10:48.658  INFO 15880 --- [nio-8091-exec-4] c.p.a.s.a.controller.AccountController   : [getAccountByLoginNameAndPwd] 收到请求，本服务端口：8091

-- server:8090
2018-04-26 11:10:47.546  INFO 19232 --- [nio-8090-exec-6] c.p.a.s.a.controller.AccountController   : [getAccountByLoginNameAndPwd] 收到请求，本服务端口：8090
2018-04-26 11:10:49.449  INFO 19232 --- [io-8090-exec-10] c.p.a.s.a.controller.AccountController   : [getAccountByLoginNameAndPwd] 收到请求，本服务端口：8090
```

可以看到，`8090`和`8091`交替接收到请求。

# 总结

本章大致讲了Zuul主要特性与主要配置，但是对于像过滤器、头信息处理等都没去实现，主要是因为不是本篇的主题，本章旨在运行Zuul并代理后台服务，进阶部分才会一一对这些比较高阶的特性进行介绍。不知道大家有没有感觉到，现在我们有了Eureka Server、AccountCenterServer、Zuul Proxy，三个服务的配置各自管理，当服务一多起来时，配置越来越隔离，管理起来就开始麻烦、松散，越来越乱，所以下一章，我们会讲Config Server。敬请期待！

# 相关链接

## 参考阅读

1. [Spring Netflix Zuul][参考文章1]
2. [Netflix公司如何使用Zuul][Netflix_uses_zuul]

## 代码

1. [本文代码][本文代码]
2. [zuul][zuul]

## 本系列文章

1. [开篇][第一篇]
2. [Netflix Eureka][第二篇]
3. [Netflix Zuul][第三篇]

[第一篇]: https://printfcoder.github.io/myblog/spring/2018/04/12/abc-spring-cloud-part-1/
[第二篇]: https://printfcoder.github.io/myblog/spring/2018/04/13/abc-spring-cloud-part-2-netflix-eureka/
[第三篇]: https://printfcoder.github.io/myblog/spring/2018/04/15/abc-spring-cloud-part-3-netflix-zuul/

[zuul]: https://github.com/Netflix/zuul
[Netflix_uses_zuul]: https://www.slideshare.net/MikeyCohen1/edge-architecture-ieee-international-conference-on-cloud-engineering-32240146/27
[参考文章1]: https://cloud.spring.io/spring-cloud-netflix/multi/multi__router_and_filter_zuul.html

[本文代码]: https://github.com/printfcoder/spring-cloud-abc/tree/basic-part3-netflix-zuul-server