# Apache Calcite

## 编译和安装到本地 Maven 仓库

执行以下命令将项目编译并安装到本地 Maven 仓库：

```bash
./gradlew publishToMavenLocal -x checkstyleMain -x checkstyleTest -x javadoc
```

### 命令说明

- `publishToMavenLocal`: 将项目发布到本地 Maven 仓库（通常是 `~/.m2/repository`）
- `-x checkstyleMain`: 跳过 checkstyle 主代码检查
- `-x checkstyleTest`: 跳过 checkstyle 测试代码检查
- `-x javadoc`: 跳过 javadoc 生成

## 版本信息

当前版本：`1.4.0.0-dev`