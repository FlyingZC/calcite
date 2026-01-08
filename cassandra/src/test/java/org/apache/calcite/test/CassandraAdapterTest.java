/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Apache许可证声明，说明代码的开源协议和版权信息
// 声明本软件包属于org.apache.calcite.test测试包
package org.apache.calcite.test;

// 导入Cassandra驱动程序的核心会话类，用于与Cassandra数据库建立连接
import com.datastax.oss.driver.api.core.CqlSession;
// 导入Google Guava库的不可变Map类，用于创建不可变的键值对映射
import com.google.common.collect.ImmutableMap;

// 导入Cassandra单元测试工具的CQL数据加载器，用于在测试中加载CQL脚本数据
import org.cassandraunit.CQLDataLoader;
// 导入Cassandra单元测试工具的类路径CQL数据集，用于从类路径加载CQL脚本文件
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
// 导入JUnit5的BeforeAll注解，标记在所有测试方法执行前只运行一次的方法
import org.junit.jupiter.api.BeforeAll;
// 导入JUnit5的Test注解，标记测试方法
import org.junit.jupiter.api.Test;
// 导入JUnit5的ExtendWith注解，用于注册测试扩展
import org.junit.jupiter.api.extension.ExtendWith;
// 导入JUnit5的Execution注解，用于控制测试方法的执行模式
import org.junit.jupiter.api.parallel.Execution;
// 导入JUnit5的ExecutionMode枚举，提供不同的执行模式选项
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Tests for the {@code org.apache.calcite.adapter.cassandra} package.
 * // 对org.apache.calcite.adapter.cassandra包的测试类
 *
 * <p>Will start embedded cassandra cluster and populate it from local {@code twissandra.cql} file.
 * // 将启动嵌入式Cassandra集群并从本地的twissandra.cql文件填充数据
 * All configuration files are located in test classpath.
 * // 所有配置文件都位于测试类路径中
 *
 * <p>Note that tests will be skipped if running on JDK11+
 * // 注意：如果在JDK11+上运行，测试将被跳过
 * (which is not yet supported by cassandra) see
 * // （Cassandra尚未支持JDK11+）请参考
 * <a href="https://issues.apache.org/jira/browse/CASSANDRA-9608">CASSANDRA-9608</a>.
 * // CASSANDRA-9608问题追踪链接
 *
 */
// 使用SAME_THREAD执行模式，确保所有测试方法在同一线程中顺序执行
@Execution(ExecutionMode.SAME_THREAD)
// 使用CassandraExtension扩展，提供Cassandra测试环境的支持
@ExtendWith(CassandraExtension.class)
// 定义Cassandra适配器测试类，用于测试Calcite的Cassandra适配器功能
class CassandraAdapterTest {

  /** Connection factory based on the "mongo-zips" model. */
  // 基于mongo-zips模型的连接工厂，存储Cassandra连接配置的不可变Map
  // 这里实际是基于"twissandra"模型，包含键空间和表结构信息
  private static final ImmutableMap<String, String> TWISSANDRA =
          // 调用CassandraExtension的静态方法getDataset，从/model.json文件读取数据集配置
          // 该配置包含Cassandra的连接信息和表结构定义
          CassandraExtension.getDataset("/model.json");

  // BeforeAll注解标记在所有测试方法执行前只运行一次的静态方法
  @BeforeAll
  // 定义静态方法load，用于在测试开始前加载测试数据到Cassandra
  // 参数session：CqlSession对象，表示与Cassandra的会话连接
  static void load(CqlSession session) {
    // 创建CQLDataLoader对象，传入Cassandra会话，用于执行CQL脚本
    // 该对象负责将CQL脚本加载到Cassandra数据库中
    new CQLDataLoader(session)
        // 调用load方法，加载ClassPathCQLDataSet对象
        // ClassPathCQLDataSet从类路径中读取twissandra.cql文件
        // 该CQL文件包含创建表和插入测试数据的SQL语句
        .load(new ClassPathCQLDataSet("twissandra.cql"));
  }

  // Test注解标记这是一个测试方法，测试基本的SELECT查询功能
  @Test void testSelect() {
    // 创建CalciteAssert断言对象，这是Calcite提供的测试工具类
    // that()是静态工厂方法，返回一个断言构建器
    CalciteAssert.that()
        // with方法配置测试环境，传入TWISSANDRA配置
        // 该配置包含Cassandra的连接信息和表结构
        .with(TWISSANDRA)
        // query方法指定要执行的SQL查询语句
        // 这里查询users表的所有数据
        .query("select * from \"users\"")
        // returnsCount方法验证查询结果返回的行数
        // 验证查询结果应该返回10行数据
        .returnsCount(10);
  }

  // Test注解标记这是一个测试方法，测试WHERE过滤条件功能
  @Test void testFilter() {
    // 创建CalciteAssert断言对象用于测试
    CalciteAssert.that()
        // 配置Cassandra连接环境
        .with(TWISSANDRA)
        // 执行带WHERE条件的查询，从userline表中查询username为'!PUBLIC!'的记录
        .query("select * from \"userline\" where \"username\"='!PUBLIC!'")
        // limit方法限制返回结果的数量为1行
        .limit(1)
        // returns方法验证查询结果的具体内容
        // 期望返回一行数据，包含username、time和tweet_id三个字段
        // time字段是UUID类型，tweet_id也是UUID类型
        .returns("username=!PUBLIC!; time=e8754000-80b8-1fe9-8e73-e3698c967ddd; "
            + "tweet_id=f3c329de-d05b-11e5-b58b-90e2ba530b12\n")
        // explainContains方法验证查询的执行计划
        // 验证执行计划中包含特定的操作符
        // PLAN=CassandraToEnumerableConverter：将Cassandra关系转换为可枚举的关系
        // CassandraFilter：Cassandra过滤器操作，应用WHERE条件过滤数据
        // condition=[=($0, '!PUBLIC!')]：过滤条件是第一个字段等于'!PUBLIC!'
        // CassandraTableScan：Cassandra表扫描操作，从twissandra.userline表读取数据
        .explainContains("PLAN=CassandraToEnumerableConverter\n"
           + "  CassandraFilter(condition=[=($0, '!PUBLIC!')])\n"
           + "    CassandraTableScan(table=[[twissandra, userline]]");
  }

  // Test注解标记这是一个测试方法，测试UUID类型字段的过滤功能
  @Test void testFilterUUID() {
    // 创建CalciteAssert断言对象
    CalciteAssert.that()
        // 配置Cassandra连接环境
        .with(TWISSANDRA)
        // 执行查询，从tweets表中查询tweet_id为指定UUID的记录
        // tweet_id字段是UUID类型，查询时使用字符串形式的UUID
        .query("select * from \"tweets\" where \"tweet_id\"='f3cd759c-d05b-11e5-b58b-90e2ba530b12'")
        // 限制返回结果为1行
        .limit(1)
        // 验证查询结果的内容
        // 期望返回包含tweet_id、body和username三个字段的记录
        // body字段是推文内容，username字段是用户名
        .returns("tweet_id=f3cd759c-d05b-11e5-b58b-90e2ba530b12; "
            + "body=Lacus augue pede posuere.; username=JmuhsAaMdw\n")
        // 验证执行计划
        // PLAN=CassandraToEnumerableConverter：转换操作
        // CassandraFilter：过滤器操作
        // condition=[=(CAST($0):CHAR(36), 'f3cd759c-d05b-11e5-b58b-90e2ba530b12')]：
        //   过滤条件是将第一个字段（tweet_id）强制转换为CHAR(36)类型后与UUID字符串比较
        //   CAST操作是因为Cassandra的UUID类型需要转换为字符串才能与查询字符串比较
        // CassandraTableScan：从twissandra.tweets表扫描数据
        .explainContains("PLAN=CassandraToEnumerableConverter\n"
           + "  CassandraFilter(condition=[=(CAST($0):CHAR(36), 'f3cd759c-d05b-11e5-b58b-90e2ba530b12')])\n"
           + "    CassandraTableScan(table=[[twissandra, tweets]]");
  }

  // Test注解标记这是一个测试方法，测试ORDER BY排序功能
  @Test void testSort() {
    // 创建CalciteAssert断言对象
    CalciteAssert.that()
        // 配置Cassandra连接环境
        .with(TWISSANDRA)
        // 执行带排序的查询
        // 从userline表中查询username为'!PUBLIC!'的记录，按time字段降序排序
        // desc表示降序排列
        .query("select * from \"userline\" where \"username\" = '!PUBLIC!' order by \"time\" desc")
        // 验证查询结果返回146行数据
        .returnsCount(146)
        // 验证执行计划
        // PLAN=CassandraToEnumerableConverter：转换操作
        // CassandraSort：Cassandra排序操作
        // sort0=[$1]：按第二个字段（time字段）排序
        // dir0=[DESC]：排序方向为降序
        // CassandraFilter：先应用WHERE条件过滤数据
        .explainContains("PLAN=CassandraToEnumerableConverter\n"
            + "  CassandraSort(sort0=[$1], dir0=[DESC])\n"
            + "    CassandraFilter(condition=[=($0, '!PUBLIC!')])\n");
  }

  // Test注解标记这是一个测试方法，测试SELECT投影功能（选择特定字段）
  @Test void testProject() {
    // 创建CalciteAssert断言对象
    CalciteAssert.that()
        // 配置Cassandra连接环境
        .with(TWISSANDRA)
        // 执行投影查询，只选择tweet_id字段
        // 从userline表中查询username为'!PUBLIC!'的记录，限制返回2行
        .query("select \"tweet_id\" from \"userline\" where \"username\" = '!PUBLIC!' limit 2")
        // 验证查询结果，期望返回两行数据，每行只包含tweet_id字段
        .returns("tweet_id=f3c329de-d05b-11e5-b58b-90e2ba530b12\n"
               + "tweet_id=f3dbb03a-d05b-11e5-b58b-90e2ba530b12\n")
        // 验证执行计划
        // PLAN=CassandraToEnumerableConverter：转换操作
        // CassandraLimit：限制操作，获取前2行数据
        // fetch=[2]：获取2行
        // CassandraProject：投影操作，只选择tweet_id字段
        // tweet_id=[$2]：选择第三个字段（索引为2，即tweet_id字段）
        // CassandraFilter：先应用WHERE条件过滤
        .explainContains("PLAN=CassandraToEnumerableConverter\n"
                + "  CassandraLimit(fetch=[2])\n"
                + "    CassandraProject(tweet_id=[$2])\n"
                + "      CassandraFilter(condition=[=($0, '!PUBLIC!')])\n");
  }

  // Test注解标记这是一个测试方法，测试字段别名功能
  @Test void testProjectAlias() {
    // 创建CalciteAssert断言对象
    CalciteAssert.that()
        // 配置Cassandra连接环境
        .with(TWISSANDRA)
        // 执行带别名的投影查询
        // 将tweet_id字段重命名为foo
        // 从userline表中查询username为'!PUBLIC!'的记录，限制返回1行
        .query("select \"tweet_id\" as \"foo\" from \"userline\" "
                + "where \"username\" = '!PUBLIC!' limit 1")
        // 验证查询结果，期望返回一行数据，字段名为foo而非tweet_id
        .returns("foo=f3c329de-d05b-11e5-b58b-90e2ba530b12\n");
  }

  // Test注解标记这是一个测试方法，测试常量投影功能
  @Test void testProjectConstant() {
    // 创建CalciteAssert断言对象
    CalciteAssert.that()
        // 配置Cassandra连接环境
        .with(TWISSANDRA)
        // 执行常量投影查询
        // 返回常量字符串'foo'，并命名为bar
        // 从userline表中查询，限制返回1行
        // 这会为每一行返回相同的常量值
        .query("select 'foo' as \"bar\" from \"userline\" limit 1")
        // 验证查询结果，期望返回一行数据，bar字段的值为'foo'
        .returns("bar=foo\n");
  }

  // Test注解标记这是一个测试方法，测试LIMIT限制功能
  @Test void testLimit() {
    // 创建CalciteAssert断言对象
    CalciteAssert.that()
        // 配置Cassandra连接环境
        .with(TWISSANDRA)
        // 执行带LIMIT的查询
        // 从userline表中查询username为'!PUBLIC!'的记录的tweet_id字段
        // 限制返回8行数据
        .query("select \"tweet_id\" from \"userline\" where \"username\" = '!PUBLIC!' limit 8")
        // 验证执行计划中包含CassandraLimit操作
        // fetch=[8]表示获取8行数据
        .explainContains("CassandraLimit(fetch=[8])\n");
  }

  // Test注解标记这是一个测试方法，测试排序和限制的组合功能
  @Test void testSortLimit() {
    // 创建CalciteAssert断言对象
    CalciteAssert.that()
        // 配置Cassandra连接环境
        .with(TWISSANDRA)
        // 执行带排序和限制的查询
        // 从userline表中查询username为'!PUBLIC!'的记录
        // 按time字段降序排序，限制返回10行
        .query("select * from \"userline\" where \"username\"='!PUBLIC!' "
             + "order by \"time\" desc limit 10")
        // 验证执行计划
        // PLAN=CassandraToEnumerableConverter：转换操作
        // CassandraLimit：限制操作，获取前10行
        // fetch=[10]：获取10行
        // CassandraSort：排序操作，按time字段降序排序
        // sort0=[$1]：按第二个字段（time字段）排序
        // dir0=[DESC]：排序方向为降序
        .explainContains("  CassandraLimit(fetch=[10])\n"
                       + "    CassandraSort(sort0=[$1], dir0=[DESC])");
  }

  // Test注解标记这是一个测试方法，测试排序、偏移和限制的组合功能
  @Test void testSortOffset() {
    // 创建CalciteAssert断言对象
    CalciteAssert.that()
        // 配置Cassandra连接环境
        .with(TWISSANDRA)
        // 执行带排序、偏移和限制的查询
        // 从userline表中查询username为'!PUBLIC!'的记录的tweet_id字段
        // 限制返回2行，跳过第1行（offset 1）
        // 注意：这里实际上offset会跳过第一行，返回第2和第3行
        .query("select \"tweet_id\" from \"userline\" where "
             + "\"username\"='!PUBLIC!' limit 2 offset 1")
        // 验证执行计划中包含CassandraLimit操作，带有offset和fetch参数
        // offset=[1]：跳过1行
        // fetch=[2]：获取2行
        .explainContains("CassandraLimit(offset=[1], fetch=[2])")
        // 验证查询结果
        // 由于offset=1，跳过了第一条记录（f3c329de-d05b-11e5-b58b-90e2ba530b12）
        // 返回第二条和第三条记录
        .returns("tweet_id=f3dbb03a-d05b-11e5-b58b-90e2ba530b12\n"
               + "tweet_id=f3e4182e-d05b-11e5-b58b-90e2ba530b12\n");
  }

  // Test注解标记这是一个测试方法，测试物化视图功能
  @Test void testMaterializedView() {
    // 创建CalciteAssert断言对象
    CalciteAssert.that()
        // 配置Cassandra连接环境
        .with(TWISSANDRA)
        // 执行查询，从tweets表中查询指定username和tweet_id的记录
        // 这个查询可以利用Cassandra的物化视图Tweets_By_User
        // 物化视图预先按照username和tweet_id组织数据，可以提高查询性能
        .query("select \"tweet_id\" from \"tweets\" where "
            + "\"username\"='JmuhsAaMdw' and \"tweet_id\"='f3d3d4dc-d05b-11e5-b58b-90e2ba530b12'")
        // enableMaterializations方法启用物化视图优化
        // 参数true表示允许查询优化器使用物化视图
        .enableMaterializations(true)
        // 验证执行计划
        // 验证查询使用了物化视图Tweets_By_User而不是原始的tweets表
        // CassandraTableScan(table=[[twissandra, Tweets_By_User]])表示从物化视图扫描数据
        // 这证明查询优化器成功识别并使用了物化视图来优化查询性能
        .explainContains("CassandraTableScan(table=[[twissandra, Tweets_By_User]])");
  }
}