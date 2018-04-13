# 配置说明


## 配置文件


### application.yml

应用配置

### bootstrap.yml

应用引导配置

### bootstrap_branchname_label.yml

以label作为标识，向config server获取指定分支配置的配置文件。配置中的`branch-name`就是git repo中的分支名，
这个label用来告诉Config Server以这个名字来路由到指定的分支。

所以，这个label并不是在Config Server中设置的，而是在Client中。

Config Server中提供label相关的endpoint有：

|url|
|--|
|/{label}/{name}-{profiles}.{json|properties|yaml|yml}|



### bootstrap_commiid_label.yml

以label作为标识，向config server获取指定commit_id配置的配置文件，与`branch-name`一样，`commit-id`也是标识，它指向git repo中该提交id版本的配置。


findOne(String application, String profile, String label)