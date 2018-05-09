---
title: Spring Cloud ABC 之 Spring Cloud Config 示例
date: 2018-04-17 08:40
categories:
 - Spring 
tags:
 - Spring Cloud
---

本文重点介绍Config Server的配置基于Git仓库，基实Config Server是支持文本、本地文件、JDBC、GIT、SVN等仓库存储形式的，但是由于篇幅与文章中心不过度发散的原因，这里只挑git作为仓库，还有，对于加密、vault、webhook等也不会过多介绍，读者有兴趣可以到官方网站查阅[资料]([原文])

对于前面的章节我们实现服务，它们的配置都是放在自己的环境中，如果自身应用多点部署，每改动一次配置就要每个节点都要去改动，这很不友好，也不安全不完备，容易漏掉或者弄错。同时，对于管理不同的应用配置，如果有一个地方可以包干，不是很好吗？

于是，引入Clond Config是很有必要的。

Spring Cloud Config 提供了基于HTTP的API扩展配置（名称-值或与YAML的等价的内容）。该服务内置在Spring Boot应用中，通过`@EnableConfigServer`就可以启用。所以，如果应用像下面一样打开标记，那么该应用就是一个Config Server。

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServer {
  public static void main(String[] args) {
    SpringApplication.run(ConfigServer.class, args);
  }
}
```

和所有的Spring Boot应用一样，默认下是打开8080端口。但是也有多种方式可以和传统一样打开8888端口，最简单当然是默认的配置仓库了，在Config Server的jar包中有默认配置文件[`configserver.yml`](https://github.com/spring-cloud/spring-cloud-config/blob/master/spring-cloud-config-server/src/main/resources/configserver.yml)，它会声明`spring.config.name=configserver`（也即服务名为“configserver”）启动Config Server。

其它方式使用自己的`application.properties`，比如：

```properties
server.port: 8888
spring.cloud.config.server.git.uri: file://${user.home}/config-repo
```

上面的`${user.home}/config-repo`就是存放有YAML、properties的文件。

> 注意，在windows系统下，如果uri目录是有盘符前缀的绝对地址，那么需要多加一个“/”在url里，比如“file:///${user.home}/config-repo”

下面示范生成该配置目录的方式，仅是示例：

```shell
 $cd /path/to/you/want/to/keep/
 $mkdir config-repo
 $cd config-repo
 $git init .
 $echo info.foo: bar > application.properties
 $git add -A .
 $git commit -m "Add application.properties"
```

使用本地文件作为git仓库只是为了测试。在生产环境中理应要有配置仓库服务器

> 如果只有文本在仓库中，那么初始化时克隆是非常快的，但是，如果存储的是二进制文件，尤其是特别大的，那在第一次请求配置得花费比较长的时间或者会发生内存溢出错误。

# Environment Repository 环境仓库

Config Server的策略是把配置数据存放在`EnvironmentRepository`中管理，通过它的`findOne`方法提供指定应用或环境`Environment`（包含`propertySources`，里面有主要的特性）的浅拷贝。`findOne`需要三个参数返回`Environment`：

```java
package org.springframework.cloud.config.server.environment;
// ...
public interface EnvironmentRepository {
  Environment findOne(String application, String profile, String label);
}
```

1. {application}，映射客户端的`spring.application.name`
2. {profile}，映射客户端的`spring.profiles.active`配置，多个值则通过半角逗号“,”隔开
3. {label}，服务端的特性标签，标记配置文件的版本信息

仓库实现通常的Spring Boot应用功能，从`spring.config.name`（`{application}`）和`spring.profiles.active`（`{profiles}`）加载配置文件。优先级和通常的Spring Boot应用一样：有active活跃声明的配置要比默认的高，而且，如果有多个配置文件，后面的优先级高（和在map中塞重复值一样，后面的顶掉前面的）。

下面是一个有自己引导配置的客户端应用例子：

bootstrap.yml

```yml
spring:
  application:
    name: foo
  profiles:
    active: dev,mysql
```

(Spring Boot应用通常也能从环境变量或者命令行接口设置这些属性)

如果仓库是基于文件的，那么配置服务会基于`application.yml（在所有客户端中共享的）`、`foo.yml`（掌有优先权）创建`Environment`。如果在YAML中包含有其它文档指向Spring配置，它们会有更高的优先级（它们之间的优先级又是基于在配置中出现的顺序）。如果有特定的配置YAML（或properies）文件，它们也会有比默认配置更高的优先级。拥有更高的优先级会更早转译到`Environment`中前面列出的`PropertySource`。在独立的Spring Boot应用中，也是相同的规则。

## git 后台

默认的`EnvironmentRepository`环境仓库实现使用的是Git后台，git对于管理升级和物理环境，还有审核都非常方便。（**我们也会花大量篇幅来介绍Config Server与git仓库结合，其它的方式会尽可能详尽，但是不会像git这样细，因为git综合来看不管是管理、分支、目录、权限上都可以得到很好的控制**）

可以在Config Server的`spring.cloud.config.server.git.uri`来定位git仓库地址（比如`application.yml`）。而如果设置打头的是`file:`，意味着它只是在本地的仓库运行，相比通过git服务器而言，这样可以有更快的启动速度，也更简单。但是，这样看来，服务器的操作直接在本地而不需要克隆，因为配置服务器并没有在“remote远端”仓库造成改动，所以这样裸奔无关紧要。

现在放大Config Server的量级，然后又要让它高可用，要把所有的实例都指向同一个仓库，所以，得有一个共享系统来完成这事。即便在这个情况下，使用`ssh:`协议来共享文件系统仓库要好一写，服务也可以克隆到本地，像缓存一样使用本地的副本。

仓库的实现映射HTTP的`{label}`参数到git的标识，比如（提交id，分支，标签）。如果git分支或者标签名包含斜线“/”，那么这个在HTTP URL中的标记就应该要替换成特殊的字符串`$$_$$`，这么做是为了避免和其它URL路径产生歧义。举个栗子，如果标识是`foo/bar`，那么，把斜杠替换掉，就变成`foo($$_$$)bar`。`($$_$$)`也可以用于`{application}`参数。如果使用命令行客户端，比如“curl”，当心URL中的圆括号，得把它们转义成单引号('')。

### git URI中的占位符

Config Server 支持使用占位符的git仓库URL，像`{application}`、`{profile}`和`{label}`，但是要记住，`{label}`是用来当git的标识的（提交id、分支等）。所以你可以在像下面结构那样支持“一个应用一个仓库”的策略：

```yml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/组织名/{application}
```

当然也是可以支持“一个属性一个仓库”策略，方式也是相同的，只是用的是`{profile}`标识，下面的多仓库中再示例。

另外，在`{application}`使用特殊的“($$_$$)”字符串参数就会激活支持多组织，比如：

```yml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/{application}
```

在请求时，会以`organization($$_$$)application`样的格式传入`{application}`。

#### 模式匹配和多仓库

Spring Cloud Config也支持模式匹配复杂的application和profile。模式的格式是用逗号隔开的带通配符的`{application}/{profile}`名称列表，注意模式如果以通配符打头，那么得括起来，比如：

```yml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/spring-cloud-samples/config-repo
          repos:
            simple: https://github.com/simple/config-repo
            special:
              pattern: special*/dev*,*special*/dev*
              uri: https://github.com/special/config-repo
            local:
              pattern: local*
              uri: file:/home/configsvc/config-repo
```

如果`{application}/{profile}`没有匹配到任何模式，那么会使用默认的URI`spring.cloud.config.server.git.uri`。上面的例子中，“simple”仓库，匹配模式是`simple/*`（在所有的属性中，只匹配了`simple`）。“local”分支匹配所有以`local`开头的应用名(`/*`后缀会自动加在没有属性匹配符的模式上)。

> simple 例子中只用了一行来声明仓库，这种情况下，只有当属性会被设成这个URL时才有用。如果要设置其它的（比如证书、模式等）就得使用完整的格式。

`pattern`属性实质上是一个数组，你可以使用YAML数组（或者`[0]`、`[1]`等等加在属性文件里的后缀）绑定多个模式。当有应用需要有多个属性资源时，可以参考下面的例子：

```yml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/spring-cloud-samples/config-repo
          repos:
            development:
              pattern:
                - '*/development'
                - '*/staging'
              uri: https://github.com/development/config-repo
            staging:
              pattern:
                - '*/qa'
                - '*/production'
              uri: https://github.com/staging/config-repo
```

> Spring Cloud 会猜测即使模式中没有以`*`结尾，那也会匹配以这个模式开头的属性，比如`*/staging`就会匹配到`"*/staging"和"*/staging,*"等等`。这比较常见，比如，在本地你要以“开发”模式属性运行程序，但是在远端时就使用“云”属性。

配置库也可选择地在子目录中存放配置文件，匹配模式通过指定`searchPaths`找到这些目录。比如，下面示例在顶级目录的配置文件：

```yml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/spring-cloud-samples/config-repo
          searchPaths: foo,bar*
```

刚才的这个例子中，服务在顶级目录、foo、及所有名称以`bar`开头的目录。

默认情况下，服务会在第一次请求中克隆远程仓库中的配置。服务可以配置在启动时才克隆配置，像下面的顶级目录配置：

```yml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://git/common/config-repo.git
          repos:
            team-a:
                pattern: team-a-*
                cloneOnStart: true
                uri: http://git/team-a/config-repo.git
            team-b:
                pattern: team-b-*
                cloneOnStart: false
                uri: http://git/team-b/config-repo.git
            team-c:
                pattern: team-c-*
                uri: http://git/team-a/config-repo.git
```

这个例子中，服务在启动时克隆了team-a的配置仓库，这发生在它接收请求之前。其它的仓库并没有被克隆，直到该仓库中的配置被请求。

> 在Config Server启动中，配置启动时从仓库获取配置对快速辨别是否缺失配置资源很有帮助。如果配置资源没有激活`cloneOnStart`的话，缺失或者有非法配置文件的Config Server也会启动成功，并且也不会察觉出错误，直到应用请求这些配置资源才报出来。

### 如何使用label和profile

其实label和profile都是客户端传给Config Server的，Server会把这些当成参数来配合pattern等查找合适的配置。如下面Server的endpoint接口都是想找配置的接口

|url|
|--|
|/{label}/{name}-{profiles}.{json|properties|yaml|yml}|
|/{name}-{profiles}.{json|properties|yaml|yml}|
|/{name}/{profiles:.*[^-].*}|
|/{name}/{profiles}/{label:.*}|
|/{name}/{profile}/{label}/**|

name相当于应用名，即`spring.application.name`；profile是`spring.profiles.active`，多个以逗号隔开。label是想要拉取的分支、tag或者commit-id。

### 认证

在配置仓库中使用基于HTTP的基础验证，可以增加`username`，`password`属性，隔开设置，不是设置在URL中：

```yml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/spring-cloud-samples/config-repo
          username: trolley
          password: strongpassword
```

如果说没有使用HTTPS，而是用户证书、SSH，如果它存放在默认目录`~/.ssh`，并且URI指向SSH的地址比如`git@github.com:configuration/cloud-configuration`，那也是能立即生效的。需要说明的是，`~/.ssh/known_hosts`中能找到该git地址的`ssh-rsa`格式的记录很重要，其它的格式如`ecdsa-sha2-nistp256`还不支持。为了避免这个情况，得保证这个git服务器在`known_hosts`文件中一条记录是`ssh-rsa`格式的，并且它的地址也要能和在Config Server中配置的匹配。如果在URL中用的是主机名，得精确地（不是IP）在`known_hosts`中记录下来。仓库会被[JGit][JGit]访问，所以能找到的任何文档都得是可用的。如果要设置HTTPS代理，可以在`~/.git/config`中设置或者（和其它的JVM程序一样）设置系统属性`-Dhttps.proxyHost`和`-Dhttps.proxyPort`。

> 如果找不到git默认的配置目录，一般是`~/.git`。可以通过`git config --global`来操作，但是还是建议使用配置文件。
>
> 如果出现`Caused by: com.jcraft.jsch.JSchException: reject HostKey:` 错误，可能是证书算法错误，通过下面指令
> $ssh-keyscan -t rsa {hostname or IP} >> ~/.ssh/known_hosts
> 把rsa算法的hostkey加到known_hosts中。

### AWS认证（忽略）

中国大陆用阿里云还是比较多或者自己部署，AWS比较少。暂先忽略本节

### 在属性中配置SSH

默认情况下，如果提供的地址是SSH的URI，Spring Cloud Config中的[JGit][JGit]就会读取SSH配置文件像`~/.ssh/known_hosts`和`/etc/ssh/ssh_config`，用来和Git仓库连接。在云端环境中，本地文件系统可能转瞬即逝，或者说并不能轻易访问，为了应对这种情况，JGit的SSH配置可以使用Java属性，为了使用基于Java配置，`spring.cloud.config.server.git.ignoreLocalSshSettings`忽略本地配置得设置成`true`，比如：

```yml
spring:
    cloud:
      config:
        server:
          git:
            uri: git@gitserver.com:team/repo1.git
            ignoreLocalSshSettings: true
            hostKey: someHostKey
            hostKeyAlgorithm: ssh-rsa
            privateKey: |
                         -----BEGIN RSA PRIVATE KEY-----
                         MIIEpgIBAAKCAQEAx4UbaDzY5xjW6hc9jwN0mX33XpTDVW9WqHp5AKaRbtAC3DqX
                         IXFMPgw3K45jxRb93f8tv9vL3rD9CUG1Gv4FM+o7ds7FRES5RTjv2RT/JVNJCoqF
                         ol8+ngLqRZCyBtQN7zYByWMRirPGoDUqdPYrj2yq+ObBBNhg5N+hOwKjjpzdj2Ud
                         1l7R+wxIqmJo1IYyy16xS8WsjyQuyC0lL456qkd5BDZ0Ag8j2X9H9D5220Ln7s9i
                         oezTipXipS7p7Jekf3Ywx6abJwOmB0rX79dV4qiNcGgzATnG1PkXxqt76VhcGa0W
                         DDVHEEYGbSQ6hIGSh0I7BQun0aLRZojfE3gqHQIDAQABAoIBAQCZmGrk8BK6tXCd
                         fY6yTiKxFzwb38IQP0ojIUWNrq0+9Xt+NsypviLHkXfXXCKKU4zUHeIGVRq5MN9b
                         BO56/RrcQHHOoJdUWuOV2qMqJvPUtC0CpGkD+valhfD75MxoXU7s3FK7yjxy3rsG
                         EmfA6tHV8/4a5umo5TqSd2YTm5B19AhRqiuUVI1wTB41DjULUGiMYrnYrhzQlVvj
                         5MjnKTlYu3V8PoYDfv1GmxPPh6vlpafXEeEYN8VB97e5x3DGHjZ5UrurAmTLTdO8
                         +AahyoKsIY612TkkQthJlt7FJAwnCGMgY6podzzvzICLFmmTXYiZ/28I4BX/mOSe
                         pZVnfRixAoGBAO6Uiwt40/PKs53mCEWngslSCsh9oGAaLTf/XdvMns5VmuyyAyKG
                         ti8Ol5wqBMi4GIUzjbgUvSUt+IowIrG3f5tN85wpjQ1UGVcpTnl5Qo9xaS1PFScQ
                         xrtWZ9eNj2TsIAMp/svJsyGG3OibxfnuAIpSXNQiJPwRlW3irzpGgVx/AoGBANYW
                         dnhshUcEHMJi3aXwR12OTDnaLoanVGLwLnkqLSYUZA7ZegpKq90UAuBdcEfgdpyi
                         PhKpeaeIiAaNnFo8m9aoTKr+7I6/uMTlwrVnfrsVTZv3orxjwQV20YIBCVRKD1uX
                         VhE0ozPZxwwKSPAFocpyWpGHGreGF1AIYBE9UBtjAoGBAI8bfPgJpyFyMiGBjO6z
                         FwlJc/xlFqDusrcHL7abW5qq0L4v3R+FrJw3ZYufzLTVcKfdj6GelwJJO+8wBm+R
                         gTKYJItEhT48duLIfTDyIpHGVm9+I1MGhh5zKuCqIhxIYr9jHloBB7kRm0rPvYY4
                         VAykcNgyDvtAVODP+4m6JvhjAoGBALbtTqErKN47V0+JJpapLnF0KxGrqeGIjIRV
                         cYA6V4WYGr7NeIfesecfOC356PyhgPfpcVyEztwlvwTKb3RzIT1TZN8fH4YBr6Ee
                         KTbTjefRFhVUjQqnucAvfGi29f+9oE3Ei9f7wA+H35ocF6JvTYUsHNMIO/3gZ38N
                         CPjyCMa9AoGBAMhsITNe3QcbsXAbdUR00dDsIFVROzyFJ2m40i4KCRM35bC/BIBs
                         q0TY3we+ERB40U8Z2BvU61QuwaunJ2+uGadHo58VSVdggqAo0BSkH58innKKt96J
                         69pcVH/4rmLbXdcmNYGm6iu+MlPQk4BUZknHSmVHIFdJ0EPupVaQ8RHT
                         -----END RSA PRIVATE KEY-----
```

下面的表描述了SSH配置属性：

|属性名|说明|
|--|--|
|ignoreLocalSshSettings|当为`true`时，使用基于属性配置而不是文件配置。必须设置在`spring.cloud.config.server.git.ignoreLocalSshSettings`上，而不是仓库中定义|
|privateKey|验证SSH私钥，但是必须在`ignoreLocalSshSettings`为`true`且Git的URI是SSH格式的|
|hostKey|验证SSH主机密钥，`hostKeyAlgorithm`得一起设置|
|hostKeyAlgorithm|`ssh-dss, ssh-rsa, ecdsa-sha2-nistp256, ecdsa-sha2-nistp384, or ecdsa-sha2-nistp521`算法中的一个，但是得在`hostKey`被设置了才有用|
|strictHostKeyChecking|false则忽略主机密钥错误|
|knownHostsFile|`.known_hosts`文件的位置|
|preferredAuthentications|重写服务认证方法的顺序。如果服务器有键盘交互的认证，那么要避免在公钥认证之前出现登录提示|

### 目录中的占位符

Config Server也支持占位符，支持`{application}，{profile}，{label}`：

```yml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/spring-cloud-samples/config-repo
          searchPaths: '{application}'
```

上面的这个例子会把仓库的地址作为目录（也是顶级目录）搜索配置。在搜索路径（search path）中加入带有占位符的通配符也是合法的，任何匹配的路径都会在搜索范围内。

### 强制拉取Git仓库

前面提到，Config Server会把远程的Git仓库下载到本地作为副本，万一改变文件（比如操作系统），那么Config Server可能就不能从远程更新副本。

为了解决该问题，有一个强制拉取的属性`force-pull`，如果本地有脏文件，该配置可以让Config Server能强制从远程拉取配置：

```yml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/spring-cloud-samples/config-repo
          force-pull: true
```

如果有多个配置库，那么可以给每个配置库加上`force-pull`：

```yml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://git/common/config-repo.git
          force-pull: true
          repos:
            team-a:
                pattern: team-a-*
                uri: http://git/team-a/config-repo.git
                force-pull: true
            team-b:
                pattern: team-b-*
                uri: http://git/team-b/config-repo.git
                force-pull: true
            team-c:
                pattern: team-c-*
                uri: http://git/team-a/config-repo.git
```

配置很敏感，任何敏感东西的自动或强制改动属性都是`false`。`force-pull`也不例外，默认值是false。记住，这个配置并不是让Config Server在每次接收请求时都获取最新的配置，而Config Server不用配置这项也是每次接收都会获取最新的。这个配置只是让Config Server要保证拉下来如果变脏了则清除。

### 在Git仓库中删除未跟踪的分支

Config Server在克隆了远程仓库的配置，检出分支到本地仓库之后，它就会一直保存或直到下次服务重启（重启会创建新的本地仓库）。所以要注意当远程分支被删除后，本地持有的副本仍然可以取到的情况。如果Config Server客户端服务启动时加上`--spring.cloud.config.label=被删除了的远程分支,master`，这会从本地获取该已经被删除的分支（远程不存在，但是本地还有），而不是从master获取。

为了能本地的仓库分支版本干净并且跟上远程仓库，可以使用`deleteUntrackedBranches`属性。该配置可以强制删除本地没有被跟踪的分支：

```yml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/spring-cloud-samples/config-repo
          deleteUntrackedBranches: true
```

同样，`deleteUntrackedBranches`默认是`false`。

## 使用版本控制后台系统

> 使用VCS-based（基于版本控制系统的）的后台，文件会被从远程检出或者克隆。默认情况下，它们会被放在系统的临时目录，文件夹前缀是`config-repo-`。在Linux中，就是`/tmp/config-repo-<randomid>`。有些操作系统会[定期例行清理](https://serverfault.com/questions/377348/when-does-tmp-get-cleared/377349#377349)临时目录。这会引发意外，比如配置丢失。为了避免这个问题，得改变Config Server的本地仓库目录，通过设置`spring.cloud.config.server.git.basedir`或者`spring.cloud.config.server.svn.basedir`来绕开系统临时目录。

# 文件系统后台

Config Server也有原生的属性可以不使用Git，而是使用类路径（classpath）或文件系统，可以使用`spring.cloud.config.server.native.search-locations`来指定静态的URL配置目录，还要加上告诉Config Server的使用本地配置：`spring.profiles.active=native`。

> 记得使用`file:`前缀在资源路径前面，因为默认的是在classpath中查找。和其它Spring Boot配置一样，也可心加上`${}`风格的环境占位符，但是Windows下的记住绝对路径需要多加一个`/`斜杠，即：`file:///${user.home}/config-repo`。
>
> `searchLocations`的默认值和本地Spring Boot应用的是一样的（如`[classpath:/, classpath:/config, file:./, file:./config]`）。这不会把Config Server 自己的`application.properties`发送到客户端，因为当前服务的属性资源已经在传送到客户端前被移掉了。
>
> 文件系统对于入门或测试来说比较方便。要用这一功能，得保证文件系统是稳定的，且可以在所有Config Server实例之间共享。

检索路径可以包含像`{application}`，`, {profile}`，`{label}`等占位符。所以，可以把不同的目录分开，然后根据自己的策略来选择不同的路径作为不同应用的配置，比如，使用一个应用一个子目录或每个profile一个子目录。

如果不在检索路径中使用占位符，仓库也会把客户端请求资源的参数`{label}`加到检索路径后面，所以每个位置加载了的属性文件并且其子目录名会被当成`label`（带有label的属性在Spring Enviroment中拥有优先权）。因此，没有占位符的默认情况下和增加`/{label}/`后缀来检索路径中一样。比如，`file:/tmp/config`和`file:/tmp/config,file:/tmp/config/{label}`是一样的。不过也可以关掉这个这个动作：`spring.cloud.config.server.native.addLabelLocations=false`设置该属性即可。

## 后端加密

Config Server 支持[Vault][Vault]。

> Vault 是一个安全访问加密工具。保密是任何你想严格控制的东西，比如API的key、密码、证书或者其它敏感信息。Vault在提供严格访问控制和详细审查日志的同时，也为任何秘密提供统一的接口。

详情请访问，[Vault快速入门](https://www.vaultproject.io/intro/index.html)

默认情况下，Config Server会假设Vault Server运行在`http://127.0.0.1:8200`上。也会假设服务名是`secret`及key是`application`。这些都可以配置在Config Server的`application.properties`。下面的表列出可配置的Vault属性：

|属性名|默认值|说明|
|---|---|默认值|
|host|127.0.0.1|主机地址|
|port|8200|端口|
|scheme|http|协议类型|
|backend|secret|应用名|
|defaultKey|application|密钥|
|profileSeparator|,|属性分隔符|

> !!! 以上这些属性都必须挂在（即前缀）`spring.cloud.config.server.vault`下面。

所以可配置的属性可以在类`org.springframework.cloud.config.server.environment.VaultEnvironmentRepository`中找到：

```java
package org.springframework.cloud.config.server.environment;
...
@ConfigurationProperties("spring.cloud.config.server.vault")
@Validated
public class VaultEnvironmentRepository implements EnvironmentRepository, Ordered {

  public static final String VAULT_TOKEN = "X-Vault-Token";

  /** Vault host. Defaults to 127.0.0.1. */
  @NotEmpty
  private String host = "127.0.0.1";

  /** Vault port. Defaults to 8200. */
  @Range(min = 1, max = 65535)
  private int port = 8200;

  /** Vault scheme. Defaults to http. */
  private String scheme = "http";

  /** Vault backend. Defaults to secret. */
  @NotEmpty
  private String backend = "secret";

  /** The key in vault shared by all applications. Defaults to application. Set to empty to disable. */
  private String defaultKey = "application";

  /** Vault profile separator. Defaults to comma. */
  @NotEmpty
  private String profileSeparator = ",";

  ...
```

Config Server启动完成后，就可以使用http请求Config Server让Vault后台把值恢复，不过需要给Valut服务器token。

首先，在Vault中放点数据：

```shell
$vault write secret/application foo=bar baz=bam
$vault write secret/myapp foo=myappsbar
```

然后发起一个HTTP请求到Config Server中把值恢复：

```shell
$curl -X "GET" "http://localhost:8888/myapp/default" -H "X-Config-Token: yourtoken"
```

会收到像下面这样的回复：

```json
{
   "name":"myapp",
   "profiles":[
      "default"
   ],
   "label":null,
   "version":null,
   "state":null,
   "propertySources":[
      {
         "name":"vault:myapp",
         "source":{
            "foo":"myappsbar"
         }
      },
      {
         "name":"vault:application",
         "source":{
            "baz":"bam",
            "foo":"bar"
         }
      }
   ]
}
```

### 多属性资源

使用Vault也可以支持多个属性资源，比如，如果你已经在下面的路径中写入数据：

```log
secret/myApp,dev
secret/myApp
secret/application,dev
secret/application
```

写到`secret/application`的属性可以被所有使用Config Server的应用读到。而应用名为`myApp`的应用，可能会有一些属性会写到`secret/myApp`，`secret/application`也是可以的。当`myApp`有`dev`属性是激活的，那么上面所有的路径对它而言都是可访问的，第一条路径比其它有更高的优先权。

## 所有应用共享配置

在所有应用之间共享配置根据采用的方法而有所差异，详见下面两篇文章：

1. [基于文件的仓库](https://cloud.spring.io/spring-cloud-config/single/spring-cloud-config.html#spring-cloud-config-server-file-based-repositories)
2. [Vault Server](https://cloud.spring.io/spring-cloud-config/single/spring-cloud-config.html#spring-cloud-config-server-vault-server)

### 基于文件的仓库

基于文件的（git,svn和native）仓库，资源文件名如`application*（application.properties, application.yml, application-*.properties等等）`这样的是在所有客户端应用之间共享的。可以使用这些文件来配置全局默认配置，必要时也可以根据特殊的应用来重写。

像*#_property_overrides*特性也可以用来设置全局默认配置，可以使用占位符在本地重写

> 对于“native”原生的属性（本地文件系统），得使用显式的检索位置，而不是Config Server自己的配置所在的位置。另外，在默认目录的`application*`资源会被移除，因为它们是Config Server的一部分。

### Vault Server

使用**Vault**作为后台，可以通过替换`secret/application`在应用之间共享配置。比如，如果要运行下面的Valut命令，所有使用Config Server的应用就可以使用`foo`和`baz`两个属性。

```shell
$vault write secret/application foo=bar baz=bam
```

## JDBC 后台 [不提倡，暂不用]

## 混合的环境仓库

有一些场景中，可以会从不同的仓库中获取配置数据。为了实现这种需求，可以在你的Config Server配置中激活多属性，比如，想从git或svn中获取配置：

```yml
spring:
  profiles:
    active: git, svn
  cloud:
    config:
      server:
        svn:
          uri: file:///path/to/svn/repo
          order: 2
        git:
          uri: file:///path/to/git/repo
          order: 1
```

除此之外，也可以给每个仓库指定特殊的URI，也可以特定一个`order`属性。`order`属性可以给所有的仓库配置优先级。`order`值越小，优先级越高。仓库的优先级帮助解决不同配置库中有相同资源时的情况。

> 从仓库中恢复的值遇到失败都会造成整个混合环境动作的失败
>
> 当使用混合环境时，所有的仓库要包含相同的label（标签）非常重要。如果有一个环境和之前的例子相似，而此时向`master`请求，但是svn里没有这个`similar`分支，则整个请求会失败。

### 属性重写

Config Server有重写的特性让操作者提供配置给全部的应用。在Spring Boot的生命周期中，重写了的属性不能突然修改。声明重写，要在`spring.cloud.config.server.overrides`增加“名称-值”对：

```yml
spring:
  cloud:
    config:
      server:
        overrides:
          foo: bar
```

这个例子让所有的应用读取`foo=bar`，并不依赖它们的配置，也就是说配置如果在Client中存在，那么会被Config Server的值重写，Client的相同配置会失效，比如上面例子中的`foo=bar`，如果客户端有`foo=rab`也会失效。

> 配置系统是不能强制让应用使用特定的配置数据。因此，重写并不是强制的。但是，对于客户端而言，还是可以提供默认的动作，这点比较有用。
>
> 一般情况下，Spring 的环境占位符`${}`可以使用反斜杠`\`转译。比如，`\${app.foo:bar}`会解析为`bar`，除非应用有自己的`app.foo`。
>
> YAML文件中，不需要自行转译反斜杠，但是，当在Config Server配置重写时，在properties文件中，就需要转译。

通过在远程仓库中设置`spring.cloud.config.overrideNone=true`可以改变所有在客户端重写的优先级，就像默认值，让应用自身提供它们自己的环境变量或者系统属性。

## 告一段落

上面我们介绍了Config Server的基础配置，在[示例](基础示例)中有比较详情的客户端与服务端示例供参考。下面会讲解高阶一些的内容，比如健康指示、加密配置等。

## 健康指示器!!!例子未清楚

Config Server有健康指示器（Health Indicator）的功能，用来检测配置的`EnvironmentRepository`是否正常。默认情况下，指示器向`EnvironmentRepository`请求一个叫`app`的应用，默认的属性（profile）、默认的标签（label）就是`EnvironmentRepository`的实现提供的。

可以使用自定义的属性、标签，像下面的例子一样配置健康指示来检测更多的应用：

```yml
spring:
  cloud:
    config:
      server:
        health:
          repositories:
            myservice:
              label: mylabel
            myservice-dev:
              name: myservice
              profiles: development
```

可以关掉健康指示器，设置`spring.cloud.config.server.health.enabled=false`即可。

## 安全

对于敏感的地方，可以使用任何方式来保护Config Server，从物理的网络安全到OAuth2 token都可以，因为Spring Security和Spring Boot支持很多安全管理。

使用默认的Spring Boot配置基本的HTTP安全，包括类路径中的Spring Security（比如，通过`spring-boot-starter-security`）。默认的是使用用户名和随机的密码。随机密码在实际应用中并不好，强烈建议配置密码（spring.security.user.password），并加密这个密码，下面介绍怎么加密。

## 加密与解密

> **重点**：
> 使用加密和解密需要JCE支持，所以得确定JVM中是否安装了这个插件。如果没有，可以到网上找教程安装。

如果远程属性包含了加密了的内容（内容值以`{cipher}`开头），它们在通过HTTP发送到客户端前会被解密。这么做主要的好处就是有些静态不动（如放在git仓库中的配置密码）的属性值是不应该明文保存的。如果值不能被解密，就会在属性资源中移除这个值，然后再加上一个名称一样的key，但是前缀被加上`invalid`，也就是说“不可用（not applicable)”（一般是`<n/a>`）。
很大程度上是为了防止密文被当成密码使用和意外泄露。

如果给客户端应用设置了远程配置仓库，那仓库中可能会含有`application.yml`如下：

application.yml

```yml
spring:
  datasource:
    username: dbuser
    password: '{cipher}FKSAJDFGYOS8F7GLHAKERGFHLSAJ'
```

如果是在properties文件中，是不需要引号的。另外，这里的值没有解密。

application.properties

```properties
spring.datasource.username: dbuser
spring.datasource.password: {cipher}FKSAJDFGYOS8F7GLHAKERGFHLSAJ
```

这样就可以大胆把这段明文推送到git仓库了。

Config Server也公开`/encrypt`和`/decrypt`两个接口（假设这两个接口是安全的且仅提供给有权限的人访问）。如果编辑了远程文件，可以通过POST到`/encrypt`让Config Server加密它:

```shell
$curl localhost:8888/encrypt -d mysecret
682bc583f4641835fa2db009355293665d2647dade3375c0ee201de2a49f7bda
```

> 如果加密的值中有需要URL编码（URL encoded），得使用`--data-urlencode`选项让`curl`确定它们是要被编码的属性。
>
> 要确保加密的值中没包含任何curl指令统计。把值输出到文件可以帮助避免这个问题。

反向操作可以通过`/decrypt`接口（只要Config Server是配置了对称的密钥或者全密钥对）：

```shell
$ curl localhost:8888/decrypt -d 682bc583f4641835fa2db009355293665d2647dade3375c0ee201de2a49f7bda
mysecret
```

> 如果使用curl来测试，那得用`--data-urlencode`选项（而不是`-d`）或者在头部显式设置`Content-Type: text/plain`让curl在有特殊字符（+号比较敏感）时正确编码数据。

在提交和推送到远端（可能不安全）存储之前，先把加密了的值加上`{cipher}`前缀放到YML或properties配置中。

`/encrypt`和`/decrypt`也可以接收路径像`/*/{name}/{profiles}`这种形式的请求，这样可以针对不同的应用和属性（profile）使用不同的路径。

> 要控制密码到这种粒度，得提供一个类型为`TextEncryptorLocator`的bean，它用来根据名称或属性创建不同的加密器。默认的提供的不需要实现（所有加密都使用一个key）。

`spring`命令行客户端（需要安装Spring Cloud CLI扩展）也可以用来加解密：

```shell
$spring encrypt mysecret --key foo
682bc583f4641835fa2db009355293665d2647dade3375c0ee201de2a49f7bda
$spring decrypt --key foo 682bc583f4641835fa2db009355293665d2647dade3375c0ee201de2a49f7bda
mysecret
```

如果要用RSA公钥，加上`@`前缀在公钥文件的位置：

```shell
$spring encrypt mysecret --key @${HOME}/.ssh/id_rsa.pub
AQAjPgt3eFZQXwt8tsHAVv/QHiY5sI2dRcR+...
```

> 这个命令中，虽然有`--`选项前缀，但是`--key`选项是必填的。

## 密钥管理

Config Server可以使用对称（即共享的）或者不对称的（RSA密钥对）加密。不对称的方式当然是首选，但是使用对称的密钥又非常方便，因为在`bootstrap.properties`中就有一个单独的属性值来配置。

配置对称的密钥，需要设置`encrypt.key`，或者使用在环境变量（Environment）中设置`ENCRYPT_KEY`，这样可以不在配置文件中存放明文。

非对称的密钥，可以设置PEM-encoded文本的key或者使用keystore（通过keytool生成）。下面是keystore属性的描述：

|属性|说明|
|--|--|
|encrypt.keyStore.location|keystore资源文件的位置|
|encrypt.keyStore.password|可以解锁keystore的密钥|
|encrypt.keyStore.alias|识别哪一个key可以用来解密keystore|

加密过程由公钥完成，私钥用于解密。因此，原则上，如果你只是要加密的话，只需要配置公钥（因为可以在本地自行用私钥解密）。在实际中，可能不会在本机上解密，因为这把私钥分散到所有客户端，而不是集中在Config Server中管理。其它方面来说，但是如果你的Config Server相当不安全，并且只有少数的客户端需要加密属性时就是个有用的选则。

## 创建测试用的KeyStore

使用下面的命令来创建keyStore：

```shell
$ keytool -genkeypair -alias mytestkey -keyalg RSA \
  -dname "CN=Web Server,OU=Unit,O=Organization,L=City,S=State,C=US" \
  -keypass changeme -keystore server.jks -storepass letmein
```

把`server.jks`放到classpath中，在Config Server的bootstrap.yml中增加配置：

```yml
encrypt:
  keyStore:
    location: classpath:/server.jks
    password: letmein
    alias: mytestkey
    secret: changeme
```

## 多密钥与密钥轮值

除了在加密属性值使用`{cipher}`前缀，Config Server还会查找0个或多个以`{name:value}`开头基于Base64编码过的密文文本。这些找到文件keys会被传到`TextEncryptorLocator`，这个定位器给密文执行任何指定的逻辑来定位到密文的`TextEncryptor`。如果配置了keystore（`encrypt.keystore.location`），默认的定位器会使用`key`的前缀提供的别名（aliases）寻找密钥，如：

```yml
foo:
  bar: `{cipher}{key:testkey}...`
```

这个定位器寻找一个叫“testkey”的key。secret也可以通过`{secret:…​}`前缀来指定。但是，如果没有提供，默认会使用keystore的密码（在创建keystore时得到的，并且不要指定secret）。如果提供了secret，那么得用自定义的`SecretLocator`加密这个secret。

当密钥只是被用在加密一小部分配置数据时（也就是说，没有在所有的地方用到），密钥轮值就不是很必要。不过，可能会偶尔需要去改变密钥（比如安全问题）。这种情况下，所有客户端需要改变它们的配置资源文件然后在所有密文中使用新的`{key:...}`前缀。记住客户端需要先检测key的别名在Config Server的keystore中是否可用。

> 如果要让Config Server负责加密和解密，也可以增加`{name:value}`前缀作为明文post到`/encrypt`接口。

## 提供加密过的属性

有时需要客户端在本地解密，而不是在服务器上。此时，如果提供`encrypt.*`配置来定位key，`/encrypt`和`/decrypt`仍然会打开，但是需要显式关掉属性的解密，通过`spring.cloud.config.server.encrypt.enabled=false`可以关闭。如果不关心这些接口，而不配置key或enabled标记，`/encrypt`和`/decrypt`会打开。

## 可选格式

endpoint接口默认的JSON格式对于Spring Boot应用来说非常友好，因为与`Enviroment`直接映射。根据需要，也可以自定义相同的数据组装成不同的格式，像YML或者Java属性，通过在资源路径后面加上".yml", ".yaml" or ".properties"即可。对于有些应用不关心endpoint接口的数据格式很有用，比如非Spring的应用。

YAML和properties也有另外的标签（通过在queryString参数中加上`resolvePlaceholders=true`即可）可以把占位符（标准的Spring`${...}`格式）在返回前转换成真实值。对于不知道Spring占位符约定的消费端十分有用。

> 使用YAML或者properties格式也有一些短板，主要是元数据丢弃。比如，JSON结构是排好序的property资源列表，名称与资源相关联。YAML和properties格式合并到一个单一的map中，甚至于原始值是多资源的，而原始资源文件的名称也会丢失。还有，YAML呈现的也不一定与它在配置资源仓库中的一样，它基于扁平的属性资源构建，并且会针对key的格式作出假设。

## 嵌入Config Server

Config Server可以作为单独的服务部署，但是如果需要，也是可以与其它服务一起集成，在main函数的类上打`@EnableConfigServer`标记即可。对于该被嵌入的应用，可以加入`spring.cloud.config.server.bootstrap`引导标识来指示应用是否用远程仓库作为配置，默认值是`false`，因为会拖慢启动速度，但是打开的话应用就和其它应用一样使用同源配置。

> 如果使用引导标识，配置服务器就需要在`bootstrap.yml`中声明它的名称和仓库URI

如果要改变应用的管理接口，可以设置`spring.cloud.config.server.prefix`前缀（比如：`/config`），那管理接口都是在`/config`目录下了，这个前缀值可以以`/`开始，但是不能以它结束。

如果不需要管理接口endpoint，仅仅是让应用从仓库中读取配置，也可以不使用`@EnableConfigServer`标记，只需要打开`spring.cloud.config.server.bootstrap=true`。

# 推送通知及Spring Cloud Bus

很多源码仓库提供商（像Github、Gitlab、Gitee还有Bitbucket）会通过webhook钩子提醒你改动。可以通过提供商的接口配置webhook来满足你感兴趣的通知。比如[Github](https://developer.github.com/v3/activity/events/types/#pushevent)的推送事件`push`，它的头部中含有`X-Github-Event`，body中的JSON格式文本则包含提交的列表信息。

在Config Server中依赖`spring-cloud-config-monitor`库和激活Spring Cloud Bus就可以打开`/monitor`endpoint。

当webhook激活后，Config Server会发送`RefreshRemoteApplicationEvent`事件到它认为会发生改变的应用。改变的检测方式可以设定策略。但是，默认情况下，改变的文件名会匹配应用名（比如，`foo.properties`就是指向foo应用，而`application.properties`就会匹配全部应用）。可以重写`PropertyPathNotificationExtractor`来改变这个策略，它会接收把请求的headers与body当成参数，然后返回改变了的文件路径列表。

默认的配置在Github、Gitlab、Gitee还有Bitbucket中是即配即用的。除了来自这些知名服务的通知，也可以向`monitor`发送请求，使用form-encoded编码过的像`path={name}`这样的body参数，然后Config Server就会向应用名匹配这个`{name}`的应用广播消息，`{name}`也可以使用通配符。

> `RefreshRemoteApplicationEvent`会使用`spring-cloud-bus`来推送，所以要保证`spring-cloud-bus`在Config Server和Client中都是激活的。
> 默认配置也会检测本地文件系统造成的改动，此时，webhook不会被用到，但是，当编辑配置文件时，刷新事件就会被广播。

# 开始代码

我们花了大量的篇幅介绍Config Server的配置，这和其它章节风格不太相同，一来其它章节的配置很多比较冷门或者用不上，但是对于Config Server大多属性配置平常用得还是比较多的。下面我们开始写代码。基于之前的章节，其实我们已经有了：

1. 基础服务 Eureka Server
2. 用户服务
3. 计划服务
4. Web接入层（门面层）（基于Zuul）

那么我们就用这几个服务作为Config Client客户端。为了简单，我们每个应用都使用一个Git仓库的目录作为配置源，再配合`spring.profiles.active=指定profile`达到不同版本使用不同目录的效果，因为如果使用分支、提交id、tag等这些标记作为识别会让配置仓库管理上显得过于复杂、臃肿、为了使用而使用。有兴趣的同学可以使用不同的仓库作为不同的应用配置源，然后再用不同的目录作为开发、测试、线上版本的配置（线上版本可能需要使用受限访问的分支）。

## 用户服务配置

在`classpath`中创建`bootstrap.yml`文件，并写入以下配置：

```yml
spring:
  profiles:
    # 该profile可以用来匹配仓库，见spring.cloud.config.server.git.pattern用法
    active: i-am-a-test-service
  cloud:
    config:
      url: http://localhost:8888
  application:
    name: User_Center_Service
```

上面的`active`我们设置了标识`i-am-a-test-service`，用这个指识帮助Config Server拉取有这个标识配置（`spring.cloud.config.server.git.pattern`）对应的仓库。

在git仓库中创建目录**home-plan-account-center**，再把原来的`application.yml`剪切到这个目录，修改为如下：

```yml
server:
  port: 8090
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/usercenter
    username: postgres
    password: postgres
    schema: classpath:import.sql
    # 创建数据库后可关闭，重启时不再创建
    initialize: false
eureka:
  client:
    service-url:
      default-zone: http://localhost:8761/eureka/
    health-check:
      enabled: true
```

自行推送到**推送到Git仓库**：

```shell
$git add  home-plan-account-center/*
$git commit -m 增加用户服务目录及配置文件
$git push
```

在`用户服务`的`pom.xml`配置中加上cloud-config依赖：

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
```

启动Config Server，再启动用户服务，我们可以在用户服务打印的日志中看到配置请求信息：





# 相关链接

## 参考阅读

1. [JGit][JGit]
2. [Vault][Vault]
3. [基础示例][基础示例]
4. [原文][原文]

## 代码

1. [本文代码][本文代码]

## 本系列文章

1. [开篇][第一篇]
2. [Netflix Eureka][第二篇]
3. [Netflix Zuul][第三篇]
4. [Cloud Config][第四篇]

[第一篇]: https://printfcoder.github.io/myblog/spring/2018/04/12/abc-spring-cloud-part-1/
[第二篇]: https://printfcoder.github.io/myblog/spring/2018/04/13/abc-spring-cloud-part-2-netflix-eureka/
[第三篇]: https://printfcoder.github.io/myblog/spring/2018/04/15/abc-spring-cloud-part-3-netflix-zuul/
[第四篇]: https://printfcoder.github.io/myblog/spring/2018/04/17/abc-spring-cloud-part-4-config/

[原文]: https://cloud.spring.io/spring-cloud-config/single/spring-cloud-config.html
[JGit]: https://github.com/eclipse/jgit
[Vault]: https://www.vaultproject.io/
[基础示例]: https://github.com/printfcoder/spring-cloud-abc/tree/basic-config-and-client

[本文代码]: https://github.com/printfcoder/spring-cloud-abc/tree/basic-config-and-client/spring-cloud-config