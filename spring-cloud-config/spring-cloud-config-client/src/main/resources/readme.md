# 配置说明


## 配置文件


### application.yml

应用配置

### bootstrap.yml

应用引导配置

### bootstrap_branchname_label.yml

以label作为标识，向config server获取指定分支配置的配置文件。配置中的`branch-name`就是git repo中的分支名，
这个label用来告诉Config Server以这个名字来路由到指定的分支。

### bootstrap_commiid_label.yml

以label作为标识，向config server获取指定commit_id配置的配置文件，与`branch-name`一样，`commit-id`也是标识，它指向git repo中该提交id版本的配置。


