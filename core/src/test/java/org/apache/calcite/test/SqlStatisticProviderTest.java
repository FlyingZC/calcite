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
// 声明包名：org.apache.calcite.test，表示这个类属于Calcite测试模块
package org.apache.calcite.test;

// 导入Calcite系统配置类，用于获取系统属性配置
import org.apache.calcite.config.CalciteSystemProperty;
// 导入SQL统计信息提供者接口，定义了获取表统计信息的标准方法
import org.apache.calcite.materialize.SqlStatisticProvider;
// 导入关系表达式表接口，代表优化器中的表对象
import org.apache.calcite.plan.RelOptTable;
// 导入关系特征定义接口，用于定义物理属性如排序、分布等
import org.apache.calcite.plan.RelTraitDef;
// 导入关系节点接口，代表关系代数表达式树中的节点
import org.apache.calcite.rel.RelNode;
// 导入SchemaPlus接口，代表可扩展的数据库模式
import org.apache.calcite.schema.SchemaPlus;
// 导入SQL解析器配置类，用于配置SQL解析行为
import org.apache.calcite.sql.parser.SqlParser;
// 导入缓存SQL统计信息提供者，用于包装其他提供者并添加缓存功能
import org.apache.calcite.statistic.CachingSqlStatisticProvider;
// 导入基于Map的SQL统计信息提供者，使用预定义的统计信息Map
import org.apache.calcite.statistic.MapSqlStatisticProvider;
// 导入基于查询的SQL统计信息提供者，通过执行SQL查询获取统计信息
import org.apache.calcite.statistic.QuerySqlStatisticProvider;
// 导入框架工具类，用于创建Calcite框架配置
import org.apache.calcite.tools.Frameworks;
// 导入程序集合类，包含预定义的优化规则集和程序
import org.apache.calcite.tools.Programs;
// 导入关系构建器，用于构建关系表达式树
import org.apache.calcite.tools.RelBuilder;
// 导入工具类，提供各种实用方法
import org.apache.calcite.util.Util;

// 导入Google Guava的Cache接口，用于缓存统计信息结果
import com.google.common.cache.Cache;
// 导入Google Guava的CacheBuilder类，用于构建缓存对象
import com.google.common.cache.CacheBuilder;

// 导入JUnit 5的Test注解，标记测试方法
import org.junit.jupiter.api.Test;

// 导入Java标准库中的Arrays类，提供数组操作方法
import java.util.Arrays;
// 导入Java标准库中的List接口，表示有序集合
import java.util.List;
// 导入时间单位枚举，用于配置缓存过期时间
import java.util.concurrent.TimeUnit;
// 导入原子整数类，用于线程安全的计数器
import java.util.concurrent.atomic.AtomicInteger;
// 导入Consumer函数式接口，表示接受单个参数的操作
import java.util.function.Consumer;
// 导入Collectors类，提供流收集的常用方法
import java.util.stream.Collectors;

// 导入Hamcrest的is匹配器，用于断言值相等
import static org.hamcrest.CoreMatchers.is;
// 导入Hamcrest的断言工具类，用于编写测试断言
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link org.apache.calcite.materialize.SqlStatisticProvider}
 * and implementations of it.
 */
// 类级JavaDoc注释：这是SqlStatisticProvider及其实现类的单元测试
// SqlStatisticProvider是Calcite中用于获取表统计信息的接口，包括表行数、主键、外键等信息
// 本测试类验证了三种SQL统计信息提供者实现的功能：
// 1. MapSqlStatisticProvider：基于预定义Map的提供者
// 2. QuerySqlStatisticProvider：通过执行SQL查询获取统计信息的提供者
// 3. CachingSqlStatisticProvider：带有缓存功能的包装提供者
class SqlStatisticProviderTest {
  /** Creates a config based on the "foodmart" schema. */
  // 静态方法：创建基于foodmart模式的配置构建器
  // foodmart是Calcite测试中常用的示例数据库，包含产品、销售、员工等表
  // 返回值：Frameworks.ConfigBuilder对象，用于构建Calcite框架配置
  public static Frameworks.ConfigBuilder config() {
    // 创建根Schema，参数true表示添加内置函数
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true);
    // 创建新的配置构建器并设置各项配置
    return Frameworks.newConfigBuilder()
        // 设置SQL解析器配置为默认配置
        .parserConfig(SqlParser.Config.DEFAULT)
        // 设置默认Schema为foodmart数据库的JDBC连接
        // CalciteAssert.addSchema将JDBC_FOODMART模式添加到根Schema中
        .defaultSchema(
            CalciteAssert.addSchema(rootSchema,
                CalciteAssert.SchemaSpec.JDBC_FOODMART))
        // 设置特征定义为null，使用默认特征定义
        .traitDefs((List<RelTraitDef>) null)
        // 设置优化程序为启发式连接顺序优化
        // RULE_SET是默认规则集，true表示启用，2是迭代次数
        .programs(Programs.heuristicJoinOrder(Programs.RULE_SET, true, 2));
  }

  // 测试方法：测试MapSqlStatisticProvider的功能
  // MapSqlStatisticProvider使用预定义的统计信息Map，不执行实际查询
  @Test void testMapProvider() {
    // 调用check方法验证MapSqlStatisticProvider的统计信息是否正确
    check(MapSqlStatisticProvider.INSTANCE);
  }

  // 测试方法：测试QuerySqlStatisticProvider的功能
  // QuerySqlStatisticProvider通过执行SQL查询来获取统计信息
  @Test void testQueryProvider() {
    // 获取调试标志，决定是否打印执行的SQL语句
    final boolean debug = CalciteSystemProperty.DEBUG.value();
    // 创建SQL消费者，根据调试标志决定是打印SQL还是丢弃
    // debug为true时打印到控制台，false时使用Util.discard丢弃
    final Consumer<String> sqlConsumer =
        debug ? System.out::println : Util::discard;
    // 创建QuerySqlStatisticProvider并传入SQL消费者，然后验证其功能
    check(new QuerySqlStatisticProvider(sqlConsumer));
  }

  // 测试方法：测试带缓存的QuerySqlStatisticProvider功能
  // 验证缓存机制能减少重复查询，提高性能
  @Test void testQueryProviderWithCache() {
    // 创建缓存对象，设置访问后5分钟过期
    // CacheList是泛型缓存，键是List（列索引列表），值是Object（统计结果）
    Cache<List, Object> cache = CacheBuilder.newBuilder()
        // 设置缓存项在最后一次访问后5分钟过期
        .expireAfterAccess(5, TimeUnit.MINUTES)
        // 构建缓存对象
        .build();
    // 创建原子计数器，用于统计执行的SQL查询次数
    final AtomicInteger counter = new AtomicInteger();
    // 创建QuerySqlStatisticProvider，传入一个每次查询时递增计数器的消费者
    // 这样可以统计实际执行的查询次数
    QuerySqlStatisticProvider provider =
        new QuerySqlStatisticProvider(sql -> counter.incrementAndGet());
    // 使用CachingSqlStatisticProvider包装QuerySqlStatisticProvider，添加缓存功能
    final SqlStatisticProvider cachingProvider =
        new CachingSqlStatisticProvider(provider, cache);
    // 第一次调用check方法，会执行6次查询获取统计信息
    check(cachingProvider);
    // 预期的查询次数是6次（表行数、主键、外键等统计信息）
    final int expectedQueryCount = 6;
    // 验证计数器值是否为6，确认第一次执行了6次查询
    assertThat(counter.get(), is(expectedQueryCount));
    // 第二次调用check方法，由于缓存命中，不应该再执行查询
    check(cachingProvider);
    // 验证计数器值仍然为6，确认第二次没有执行新查询
    assertThat(counter.get(), is(expectedQueryCount)); // no more queries
  }

  // 私有方法：验证SQL统计信息提供者返回的统计信息是否正确
  // 参数：provider - 要测试的SqlStatisticProvider实现
  private void check(SqlStatisticProvider provider) {
    // 使用配置创建关系构建器，用于构建关系表达式树
    final RelBuilder relBuilder = RelBuilder.create(config().build());
    // 扫描product表，构建扫描节点
    final RelNode productScan = relBuilder.scan("product").build();
    // 从扫描节点获取表对象，代表product表的元数据
    final RelOptTable productTable = productScan.getTable();
    // 扫描sales_fact_1997表，构建扫描节点
    final RelNode salesScan = relBuilder.scan("sales_fact_1997").build();
    // 从扫描节点获取表对象，代表销售事实表的元数据
    final RelOptTable salesTable = salesScan.getTable();
    // 扫描employee表，构建扫描节点
    final RelNode employeeScan = relBuilder.scan("employee").build();
    // 从扫描节点获取表对象，代表员工表的元数据
    final RelOptTable employeeTable = employeeScan.getTable();
    // 验证product表的行数是否为1560
    assertThat(provider.tableCardinality(productTable), is(1_560.0d));
    // 验证product表的product_id列是否是主键
    // columns方法将列名转换为列索引列表
    assertThat(
        provider.isKey(productTable, columns(productTable, "product_id")),
        is(true));
    // 验证sales表的product_id列是否是主键（应该是false，因为销售表中product_id会重复）
    assertThat(
        provider.isKey(salesTable, columns(salesTable, "product_id")),
        is(false));
    // 验证sales表的product_id是否是外键，引用product表的product_id
    // 这应该是true，因为销售记录中的产品必须存在于产品表中
    assertThat(
        provider.isForeignKey(salesTable, columns(salesTable, "product_id"),
            productTable, columns(productTable, "product_id")),
        is(true));
    // Not a foreign key; product has some ids that are not referenced by any
    // sale
    // 验证product表的product_id是否是外键，引用sales表的product_id
    // 这应该是false，因为产品表中有些产品可能没有被销售过
    assertThat(
        provider.isForeignKey(
            productTable, columns(productTable, "product_id"),
            salesTable, columns(salesTable, "product_id")),
        is(false));
    // There is one supervisor_id, 0, which is not an employee_id
    // 验证employee表的supervisor_id是否是外键，引用employee表的employee_id
    // 这应该是false，因为有一个supervisor_id为0的记录，而employee表中没有id为0的员工
    assertThat(
        provider.isForeignKey(
            employeeTable, columns(employeeTable, "supervisor_id"),
            employeeTable, columns(employeeTable, "employee_id")),
        is(false));
  }

  // 私有方法：将列名转换为列索引列表
  // 参数：table - 表对象，columnNames - 可变参数的列名数组
  // 返回值：列索引的整数列表
  private List<Integer> columns(RelOptTable table, String... columnNames) {
    // 使用流处理将列名数组转换为列索引列表
    return Arrays.stream(columnNames)
        // 对每个列名，在表的字段名列表中查找其索引位置
        .map(columnName ->
            table.getRowType().getFieldNames().indexOf(columnName))
        // 将流收集为List
        .collect(Collectors.toList());
  }
}
