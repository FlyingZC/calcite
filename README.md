# Apache Calcite

## 编译和安装到本地 Maven 仓库

执行以下命令将项目编译并安装到本地 Maven 仓库：

```bash
./gradlew publishToMavenLocal -x checkstyleMain -x checkstyleTest -x javadoc -x test
```

### 命令说明

- `publishToMavenLocal`: 将项目发布到本地 Maven 仓库（通常是 `~/.m2/repository`）
- `-x checkstyleMain`: 跳过 checkstyle 主代码检查
- `-x checkstyleTest`: 跳过 checkstyle 测试代码检查
- `-x javadoc`: 跳过 javadoc 生成
- `-x test`: 跳过测试执行

### 指定本地 Maven 仓库地址

如需指定自定义的本地 Maven 仓库地址，可以在 `gradle.properties` 或执行命令时添加 `MAVEN_REPO_LOCAL` 参数：

```bash
./gradlew publishToMavenLocal -PMAVEN_REPO_LOCAL=/home/flyingzc/.m2/sphereex-repository -x checkstyleMain -x checkstyleTest -x javadoc -x test
```

或在项目根目录创建/修改 `gradle.properties` 文件：

```properties
MAVEN_REPO_LOCAL=/path/to/custom/repo
```

## 版本信息

当前版本：`1.4.0.0-dev`
