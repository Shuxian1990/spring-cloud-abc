Spring Cloud 为开发者提供了一系列工具，用于在分布式系统中快速开发通用的模板，比如配置管理、服务发现、断路器、智能路由、微代理、控制总线、一次性证书token、全局锁、主从选举、分布式会话、集群状态）

本系列我们会把这些组件用起来，为此，要让这些技术应用得合理，我们需要虚拟一个家庭计划管理项目，架构大致如下：

1. 基础服务 Eureka、Config Server
2. 用户服务
3. 计划服务
4. Web接入层（门面层）

Web接入层负责接收所有请求并代理转发到其它服务，而其它的服务一律不接收从客户端过来的请求。

首先我们先尝试把用户服务搭建起来，让Web接入层有可以转发的地方。

# 预置条件

假设读者有一定的Spring、Spring Boot基础知识。具体maven pom文件依赖可以参考[本文代码][本文代码]，这里不花篇幅说明配置。

## pom依赖与应用

首先依赖spring-cloud全家桶maven，这样可以省去查找相关依赖包的麻烦：

```xml
<dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud-version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
</dependencyManagement>
```

# 用户服务

## 创建项目

可以到[SPRING INITIALIZR](https://start.spring.io/)上初始化一个空白项目，也可以到参考[分支][本章分支]自行构建。

主体目录结构：

```log
└─spring-cloud-home-plan-account-center  // 用户服务子pom目录
    ├─src
    │  └─main
    │      ├─java
    │      │  └─com
    │      │      └─printfcoder
    │      │          └─abc
    │      │              └─springcloud
    │      │                  └─accountcenter
    │      │                      ├─controller  // API接口
    │      │                      ├─domain      // 对象领域
    │      │                      ├─launch      // 启动器，main所在目录
    │      │                      ├─repository  // mapper
    │      │                      └─service     // 服务类
    │      └─resources
```

我们不使用过多的`interface`比如`service`与`serviceImpl`等。

## 依赖

创建maven项目，基础依赖：

```yml
<dependencies>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
        </dependency>

        <!-- test dependencies -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
</dependencies>
```

## 创建数据库

我们使用PG的第三方发行版本，[BigSQL][BigSQL]。比起原生的PG好用一些，参考官网装上。

装好后配置数据库：

```yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/usercenter
    username: postgres
    password: postgres
    schema: classpath:import.sql
    # 创建数据库后可关闭，重启时不再创建
    initialize: false
```

如果PG没有装在本地，那么把上面的`localhost`改成相应的地址即可。`import.sql`是初始化脚本，如果不执行可以把`spring.datasource.initialize`设置成`false`。

import.sql如下：

```sql
CREATE TABLE IF NOT EXISTS public.account (
  id           serial PRIMARY KEY,
  account_name VARCHAR(50)  NOT NULL,
  login_name   VARCHAR(50),
  pwd          VARCHAR(128) NOT NULL
);

INSERT INTO public.account (account_name, login_name, pwd)
VALUES ('test', 'test', '123') ON CONFLICT DO NOTHING;
```

public是默认的schema。

## domain 和 mapper

在`com.printfcoder.abc.springcloud.accountcenter.domain`创建`Account`类：

```java
package com.printfcoder.abc.springcloud.accountcenter.domain;

import lombok.Data;

@Data
public class Account {

    private String loginName;

    /**
     * 暂时先用明文
     */
    private String pwd;

    private String accountName;

}
```

为了不然本项目复杂化，密码先用明文

到`com.printfcoder.abc.springcloud.accountcenter.repository`包下创建接口`AccountMapper`，增加根据登录名查询账户的方法`queryAccountByLoginName`：

```java
package com.printfcoder.abc.springcloud.accountcenter.repository;

import com.printfcoder.abc.springcloud.accountcenter.domain.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AccountMapper {

    @Select("SELECT\n"
        + "  id,\n"
        + "  account_name AS accountName,\n"
        + "  login_name   AS loginName,\n"
        + "  pwd\n"
        + "FROM account\n"
        + "WHERE login_name = #{loginName}")
    Account queryAccountByLoginName(@Param("loginName") String loginName);
}
```

## service

在`com.printfcoder.abc.springcloud.accountcenter.service`下创建`AccountService`：

```java
package com.printfcoder.abc.springcloud.accountcenter.service;

import com.printfcoder.abc.springcloud.accountcenter.domain.Account;
import com.printfcoder.abc.springcloud.accountcenter.repository.AccountMapper;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    @Resource
    private AccountMapper accountMapper;

    public Account queryAccountByLoginName(String loginName) {
        return accountMapper.queryAccountByLoginName(loginName);
    }
}
```

## controller

```java
package com.printfcoder.abc.springcloud.accountcenter.controller;
...
@RestController
@RequestMapping("account")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @GetMapping("get-account-by-loginname-and-pwd")
    public Account getAccountByLoginNameAndPwd(@RequestParam("loginName") String loginName, @RequestParam("pwd") String pwd) {
        Account account = accountService.queryAccountByLoginName(loginName);
        // 先只进行简单对比，暂不处理加密等
        if (account != null && pwd.equals(account.getPwd())) {
            return account;
        }
        return null;
    }
}

```

## Application

Mapper、Service、Contrller创建好之后，下面创建Application。因为我们基于Spring Boot创建应用，所以，这一步只需要几行代码、几个标记就可以把服务跑起来：

```java
package com.printfcoder.abc.springcloud.accountcenter.launch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan("com.printfcoder.abc.springcloud.accountcenter")
@MapperScan("com.printfcoder.abc.springcloud.accountcenter.repository")
@SpringBootApplication
public class AccountCenterServer {
    public static void main(String[] args) {
        SpringApplication.run(AccountCenterServer.class, args);
    }
}
```

标记`@ComponentScan`用于告诉Spring去扫描其参数路径下面打了组件标记（如`@Controller`、`@RestController`、`@Service`等）的类，这些类会被创建成`Spring Bean`。而`@MapperScan`是告诉Mybatis扫描路径下打了`@Mapper`（默认是`@Mapper`标记）的interface，并生成代理类来操作数据库。

## 运行

```shell
$mvn spring-boot:run -o
```

`-o` 是`offline`的意思，指定maven不要下载，使用离线模式。这样maven只会在第一次编译时下载必要的依赖包与文件描述文件，以后如果没有更新依赖就不会下载了。

不过，建议使用IDE导入项目，这样有效率得多，比如IDEA、Eclipse等。

打开浏览器窗口，输入`http://localhost:8080/account/get-account-by-loginname-and-pwd?loginName=test&pwd=123`

# 总结

本章我们引入了Spring Cloud的概念及其主要特性，但是我们还没有用它的特性来实现任何业务，只是基于Spring Boot实现了一个简单的Web MVC应用，也就是`用户服务`，但是这是必须的，因为下一章交付介绍`Netflix Eureka`，`用户服务`会向`Eureka注册`，所以我们得先实现一个服务让其去注册并对外提供服务。

# 相关链接

1. [本文代码][本章分支]
2. [BigSQL][BigSQL]

## 本系列文章

1. [开篇][第一篇]
2. [Netflit Eureka][第二篇]

[第一篇]: https://printfcoder.github.io/myblog/spring/2018/04/12/abc-spring-cloud-part-1/
[第二篇]: https://printfcoder.github.io/myblog/spring/2018/04/13/abc-spring-cloud-part-2-netflix-eureka/

[BigSQL]: http://www.openscg.com/bigsql/
[本章分支]: https://github.com/printfcoder/spring-cloud-abc/tree/basic-part1