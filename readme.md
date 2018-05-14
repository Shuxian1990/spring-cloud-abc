Feign是用于web service的声明式客户端，旨在简化请求代码。要使用Feign。它提供插件化支持Feign或JAX-RS风格的标记、编码与解码。Spring Cloud为Feign提供了基于Ribbon及Eureka一体化的负载均衡http客户端。

本篇我们不过多阐述Feign，只是讲解怎么去使用它。未来会有进阶章节，专门讲Feign的高级特性比如修改默认行为、头处理、回调、日志、编解码等。

前面几篇，我们把基础服务与用户服务搭建起来，我们所有服务如下：

1. 基础服务 Eureka、Config Server     已经实现
2. 用户服务                           已经实现
3. 计划服务                           未实现
4. Web接入层（门面层）                已经实现

为此，我们要实现`计划服务`。在`计划服务`中加入Feign，使用Feign去请求`用户服务`的接口。

# 如何引入

feign项目依赖group为`org.springframework.cloud`，artifact为`spring-cloud-starter-openfeign`

将`用户服务`的pom中的依赖全部搬到`计划服务`中，二者在项目中差不多，依赖是一样的，但是下一要给plan加上feign依赖：

```xml
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
  </dependency>
```

其它依赖详见本文[代码分支][本文代码]，这里不赘述。

只需要在main类中加入标记即可使用feign：

```java
@ComponentScan("com.printfcoder.abc.springcloud.plancenter")
@EnableFeignClients(basePackages = {"com.printfcoder.abc.springcloud.plancenter.**.client"})
@SpringBootApplication
public class PlanCenterServer {

    public static void main(String[] args) {
        SpringApplication.run(PlanCenterServer.class, args);
    }
}
```

`@EnableFeignClients`标记就是用来激活Feign客户端，它的`basePackages`配置是告诉feign启动器只扫描匹配这个包名下的类。

如果要调用其它的restful接口，可以像下面这样，比如我们要调用`用户服务`的`get-account-by-loginname-and-pwd`接口：

```java
@FeignClient(name = "${account.api.application-name}")
@RequestMapping(value = "account")
public interface AccountClient {

    @RequestMapping(method = RequestMethod.GET, value = "get-account-by-loginname-and-pwd")
    Account getAccountByLoginNameAndPwd(@RequestParam("loginName") String loginName, @RequestParam("pwd") String pwd);
}
```

只需要声明一个`interface`，然后打上标记`@FeignClient`，并指定`name`来告诉feign要用哪个服务的接口，`@RequestMapping`可以标记在类或者方法上，用于定义请求路径。
`name`指定的服务id或服务名就是用于给Ribbon的负载均衡器使用的。

Feign不需要引用服务的api包，只需要知道服务的URL即可。这样子可以把对服务依赖降到最小。

在Spring Cloud的环境中运行时，feign中包含了Ribbon机制，所以在应用向Eureka注册了之后，在配置正确的情况下，feign客户端可以自动拥有负载均衡的能力。

这样，Feign简单的客户端就设置完成。

# 运行

```shell
$cd ${project.home}/spring-cloud-plan-center/spring-cloud-plan-server
$mvn spring-boot:run
```

或者使用idea或eclipse等IDE运行（推荐）

# 总结

本章简单介绍了Feign的初级用法，鉴于本系列我们主要用上Spring Cloud的知名组件，因而不进入进阶阶段，所以本章不介绍过多复杂的配置，比如自定义Feign客户端，支持`Hystrix`等，进阶阶段我们会再来讨论这些。

# 相关链接

## 参考阅读

## 代码

1. [本文代码][本文代码]

## 本系列文章

1. [开篇][第一篇]
2. [Netflix Eureka][第二篇]
3. [Netflix Zuul][第三篇]
4. [Cloud Config][第四篇]
5. [Feign][第五篇]

[第一篇]: https://printfcoder.github.io/myblog/spring/2018/04/12/abc-spring-cloud-part-1/
[第二篇]: https://printfcoder.github.io/myblog/spring/2018/04/13/abc-spring-cloud-part-2-netflix-eureka/
[第三篇]: https://printfcoder.github.io/myblog/spring/2018/04/15/abc-spring-cloud-part-3-netflix-zuul/
[第四篇]: https://printfcoder.github.io/myblog/spring/2018/04/17/abc-spring-cloud-part-4-config/
[第五篇]: https://printfcoder.github.io/myblog/spring/2018/04/28/abc-spring-cloud-part-5-rpc-with-feign/

[本文代码]: https://github.com/printfcoder/spring-cloud-abc/tree/basic-part5-cloud-feign