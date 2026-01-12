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
package org.apache.calcite.test; // org.apache.calcite.test包，包含Calcite框架的测试类

import org.apache.calcite.schema.TableFactory; // 导入TableFactory接口，用于创建自定义表工厂
import org.apache.calcite.test.schemata.orderstream.InfiniteOrdersStreamTableFactory; // 导入无限订单流表工厂，用于生成无限流数据
import org.apache.calcite.test.schemata.orderstream.OrdersStreamTableFactory; // 导入订单流表工厂，用于生成订单流数据
import org.apache.calcite.test.schemata.orderstream.ProductsTableFactory; // 导入产品表工厂，用于生成产品数据
import org.apache.calcite.util.TestUtil; // 导入测试工具类，提供测试辅助方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import org.hamcrest.comparator.ComparatorMatcherBuilder; // 导入Hamcrest比较器匹配器构建器，用于构建比较断言
import org.junit.jupiter.api.Disabled; // 导入JUnit5的Disabled注解，用于禁用测试方法
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法
import org.junit.jupiter.api.Timeout; // 导入JUnit5的Timeout注解，用于设置测试超时时间

import java.sql.ResultSet; // 导入JDBC的ResultSet接口，表示数据库结果集
import java.sql.ResultSetMetaData; // 导入JDBC的ResultSetMetaData接口，用于获取结果集元数据
import java.sql.SQLException; // 导入JDBC的SQLException类，表示SQL异常
import java.util.function.Consumer; // 导入Java函数式接口Consumer，用于消费结果集

import static org.hamcrest.CoreMatchers.equalTo; // 导入Hamcrest的equalTo匹配器，用于相等断言
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest的is匹配器，用于包装其他匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的断言方法
import static org.junit.jupiter.api.Assertions.fail; // 导入JUnit5的fail方法，用于使测试失败

/**
 * Tests for streaming queries.
 * 流查询测试类：测试Calcite框架对SQL流查询的支持，包括流表的基本操作、过滤、聚合、排序、连接等功能
 * 流查询是Calcite的重要特性，允许对实时数据流进行SQL查询和处理
 * 本类通过一系列测试方法验证流查询的各种场景和功能
 */
public class StreamTest { // StreamTest类：流查询测试类，包含所有流查询相关的测试方法
  public static final String STREAM_SCHEMA_NAME = "STREAMS"; // 流模式名称常量，标识包含有限流数据的模式
  public static final String INFINITE_STREAM_SCHEMA_NAME = "INFINITE_STREAMS"; // 无限流模式名称常量，标识包含无限流数据的模式
  public static final String STREAM_JOINS_SCHEMA_NAME = "STREAM_JOINS"; // 流连接模式名称常量，标识包含流表和关系表用于连接测试的模式

  private static String schemaFor(String name, Class<? extends TableFactory> clazz) { // schemaFor方法：根据给定的模式名称和表工厂类生成JSON格式的模式定义字符串
    return "     {\n" // 返回JSON格式的模式定义字符串
      + "       name: '" + name + "',\n" // 设置模式名称
      + "       tables: [ {\n" // 开始定义表列表
      + "         type: 'custom',\n" // 指定表类型为自定义表
      + "         name: 'ORDERS',\n" // 设置表名为ORDERS
      + "         stream: {\n" // 开始流配置
      + "           stream: true\n" // 标记该表为流表
      + "         },\n" // 结束流配置
      + "         factory: '" + clazz.getName() + "'\n" // 指定表工厂类的全限定名
      + "       } ]\n" // 结束表定义
      + "     }"; // 结束模式定义
  }

  private static final String STREAM_JOINS_MODEL = "{\n" // STREAM_JOINS_MODEL常量：流连接测试用的模型JSON字符串，包含流表和关系表
      + "  version: '1.0',\n" // 模型版本号
      + "  defaultSchema: 'STREAM_JOINS',\n" // 设置默认模式为STREAM_JOINS
      + "   schemas: [\n" // 开始定义模式列表
      + "     {\n" // 开始定义STREAM_JOINS模式
      + "       name: 'STREAM_JOINS',\n" // 模式名称
      + "       tables: [ {\n" // 开始定义表列表
      + "         type: 'custom',\n" // 表类型为自定义表
      + "         name: 'ORDERS',\n" // 表名为ORDERS（流表）
      + "         stream: {\n" // 开始流配置
      + "           stream: true\n" // 标记为流表
      + "         },\n" // 结束流配置
      + "         factory: '" + OrdersStreamTableFactory.class.getName() + "'\n" // 使用订单流表工厂
      + "       },\n" // 结束ORDERS表定义
      + "       {\n" // 开始定义PRODUCTS表
      + "         type: 'custom',\n" // 表类型为自定义表
      + "         name: 'PRODUCTS',\n" // 表名为PRODUCTS（关系表）
      + "         factory: '" + ProductsTableFactory.class.getName() + "'\n" // 使用产品表工厂
      + "       }]\n" // 结束PRODUCTS表定义和表列表
      + "     }]}"; // 结束模式定义和模型定义

  public static final String STREAM_MODEL = "{\n" // STREAM_MODEL常量：流查询测试用的模型JSON字符串，包含有限流和无限流模式
      + "  version: '1.0',\n" // 模型版本号
      + "  defaultSchema: 'foodmart',\n" // 设置默认模式为foodmart
      + "   schemas: [\n" // 开始定义模式列表
      + schemaFor(STREAM_SCHEMA_NAME, OrdersStreamTableFactory.class) // 添加有限流模式，使用订单流表工厂
      + ",\n" // 模式分隔符
      + schemaFor(INFINITE_STREAM_SCHEMA_NAME, InfiniteOrdersStreamTableFactory.class) // 添加无限流模式，使用无限订单流表工厂
      + "\n" // 换行
      + "   ]\n" // 结束模式列表
      + "}"; // 结束模型定义

  @Test void testStream() { // testStream方法：测试基本的流查询功能，验证流表的基本SELECT操作
    CalciteAssert.model(STREAM_MODEL) // 使用STREAM_MODEL创建Calcite断言
        .withDefaultSchema("STREAMS") // 设置默认模式为STREAMS（有限流）
        .query("select stream * from orders") // 执行流查询：选择orders流表的所有列
        .convertContains("LogicalDelta\n" // 验证逻辑计划包含LogicalDelta节点（流查询特有的Delta操作符）
            + "  LogicalProject(ROWTIME=[$0], ID=[], PRODUCT=[$2], UNITS=[$3])\n" // 验证包含投影操作，输出所有字段
            + "    LogicalTableScan(table=[[STREAMS, ORDERS]])\n") // 验证包含表扫描操作
        .explainContains("EnumerableInterpreter\n" // 验证物理计划包含解释器节点
            + "  BindableTableScan(table=[[STREAMS, ORDERS, (STREAM)]])") // 验证包含可绑定的表扫描，标记为流表
        .returns( // 验证查询结果
            startsWith( // 使用startsWith辅助方法验证结果的前几行
                "ROWTIME=2015-02-15 10:15:00; ID=1; PRODUCT=paint; UNITS=10", // 第一行期望结果
                "ROWTIME=2015-02-15 10:24:15; ID=2; PRODUCT=paper; UNITS=5")); // 第二行期望结果
  }

  @Test void testStreamFilterProject() { // testStreamFilterProject方法：测试流查询的过滤和投影功能
    CalciteAssert.model(STREAM_MODEL) // 使用STREAM_MODEL创建Calcite断言
        .withDefaultSchema("STREAMS") // 设置默认模式为STREAMS
        .query("select stream product from orders where units > 6") // 执行流查询：选择units大于6的产品名称
        .convertContains( // 验证逻辑计划
            "LogicalDelta\n" // 包含Delta操作符
                + "  LogicalProject(PRODUCT=[])\n" // 包含投影操作，只输出PRODUCT字段
                + "    LogicalFilter(condition=[>($2, 6)])\n" // 包含过滤操作，条件是units > 6
                + "      LogicalProject(ROWTIME=[$0], PRODUCT=[$2], UNITS=[$3])\n" // 包含初始投影，提取需要的字段
                + "        LogicalTableScan(table=[[STREAMS, ORDERS]])\n") // 包含表扫描
        .explainContains( // 验证物理计划
            "EnumerableCalc(expr#0..3=[{inputs}], expr#4=[6], expr#5=[>($t3, $t4)], PRODUCT=[$t2], $condition=[$t5])\n" // 包含计算节点，实现过滤和投影
                + "  EnumerableInterpreter\n" // 包含解释器
                + "    BindableTableScan(table=[[STREAMS, ORDERS, (STREAM)]])") // 包含可绑定的表扫描
        .returns( // 验证查询结果
            startsWith("PRODUCT=paint", // 第一行期望结果
                "PRODUCT=brush")); // 第二行期望结果
  }

  @Test void testStreamGroupByHaving() { // testStreamGroupByHaving方法：测试流查询的分组聚合和HAVING过滤功能
    CalciteAssert.model(STREAM_MODEL) // 使用STREAM_MODEL创建Calcite断言
        .withDefaultSchema("STREAMS") // 设置默认模式为STREAMS
        .query("select stream floor(rowtime to hour) as rowtime,\n" // 执行流查询：按小时分组统计订单
            + "  product, count(*) as c\n" // 统计每个产品每小时的数量
            + "from orders\n" // 从orders表
            + "group by floor(rowtime to hour), product\n" // 按小时和产品分组
            + "having count(*) > 1") // 过滤出数量大于1的分组
        .convertContains( // 验证逻辑计划
            "LogicalDelta\n" // 包含Delta操作符
                + "  LogicalFilter(condition=[>($2, 1)])\n" // 包含HAVING过滤条件
                + "    LogicalAggregate(group=[{0, 1}], C=[COUNT()])\n" // 包含聚合操作，按小时和产品分组，计算COUNT
                + "      LogicalProject(ROWTIME=[FLOOR($0, FLAG(HOUR))], PRODUCT=[$2])\n" // 包含投影操作，计算小时时间戳
                + "        LogicalTableScan(table=[[STREAMS, ORDERS]])\n") // 包含表扫描
        .explainContains( // 验证物理计划
            "EnumerableCalc(expr#0..2=[{inputs}], expr#3=[1:BIGINT], expr#4=[>($t2, $t3)], proj#0..2=[{exprs}], $condition=[$t4])\n" // 包含HAVING过滤的计算节点
                + "  EnumerableAggregate(group=[{0, 1}], C=[COUNT()])\n" // 包含聚合节点
                + "    EnumerableCalc(expr#0..3=[{inputs}], expr#4=[FLAG(HOUR)], expr#5=[FLOOR($t0, $t4)], ROWTIME=[$t5], PRODUCT=[$t2])\n" // 包含计算小时时间戳的计算节点
                + "      EnumerableInterpreter\n" // 包含解释器
                + "        BindableTableScan(table=[[STREAMS, ORDERS, (STREAM)]])") // 包含可绑定的表扫描
        .returns( // 验证查询结果
            startsWith("ROWTIME=2015-02-15 10:00:00; PRODUCT=paint; C=2")); // 期望结果：paint产品在10点有2个订单
  }

  @Test void testStreamOrderBy() { // testStreamOrderBy方法：测试流查询的排序功能
    CalciteAssert.model(STREAM_MODEL) // 使用STREAM_MODEL创建Calcite断言
        .withDefaultSchema("STREAMS") // 设置默认模式为STREAMS
        .query("select stream floor(rowtime to hour) as rowtime,\n" // 执行流查询：按小时分组并排序
            + "  product, units\n" // 选择产品名称和数量
            + "from orders\n" // 从orders表
            + "order by floor(orders.rowtime to hour), product desc") // 按小时升序，产品名称降序排序
        .convertContains( // 验证逻辑计划
            "LogicalDelta\n" // 包含Delta操作符
                + "  LogicalSort(sort0=[$0], sort1=[], dir0=[ASC], dir1=[DESC])\n" // 包含排序操作，第一列升序，第二列降序
                + "    LogicalProject(ROWTIME=[FLOOR($0, FLAG(HOUR))], PRODUCT=[$2], UNITS=[$3])\n" // 包含投影操作
                + "      LogicalTableScan(table=[[STREAMS, ORDERS]])\n") // 包含表扫描
        .explainContains( // 验证物理计划
            "EnumerableSort(sort0=[$0], sort1=[], dir0=[ASC], dir1=[DESC])\n" // 包含物理排序节点
                + "  EnumerableCalc(expr#0..3=[{inputs}], expr#4=[FLAG(HOUR)], expr#5=[FLOOR($t0, $t4)], ROWTIME=[$t5], PRODUCT=[$t2], UNITS=[$t3])\n" // 包含计算节点
                + "    EnumerableInterpreter\n" // 包含解释器
                + "        BindableTableScan(table=[[STREAMS, ORDERS, (STREAM)]])") // 包含可绑定的表扫描
        .returns( // 验证查询结果
            startsWith("ROWTIME=2015-02-15 10:00:00; PRODUCT=paper; UNITS=5", // 第一行：paper产品，按字母序在paint之后
                "ROWTIME=2015-02-15 10:00:00; PRODUCT=paint; UNITS=10", // 第二行：paint产品，数量10
                "ROWTIME=2015-02-15 10:00:00; PRODUCT=paint; UNITS=3")); // 第三行：paint产品，数量3
  }

  @Disabled // @Disabled注解：禁用此测试方法，因为流UNION ALL的排序功能尚未完全实现
  @Test void testStreamUnionAllOrderBy() { // testStreamUnionAllOrderBy方法：测试流查询的UNION ALL和排序功能（已禁用）
    CalciteAssert.model(STREAM_MODEL) // 使用STREAM_MODEL创建Calcite断言
        .withDefaultSchema("STREAMS") // 设置默认模式为STREAMS
        .query("select stream *\n" // 执行流查询：对两个orders表的UNION ALL结果进行排序
            + "from (\n" // 子查询开始
            + "  select rowtime, product\n" // 第一个查询：选择时间戳和产品
            + "  from orders\n" // 从orders表
            + "  union all\n" // UNION ALL操作
            + "  select rowtime, product\n" // 第二个查询：选择时间戳和产品
            + "  from orders)\n" // 从orders表
            + "order by rowtime\n") // 按时间戳排序
        .convertContains( // 验证逻辑计划
            "LogicalDelta\n" // 包含Delta操作符
                + "  LogicalSort(sort0=[$0], dir0=[ASC])\n" // 包含排序操作
                + "    LogicalProject(ROWTIME=[$0], PRODUCT=[])\n" // 包含投影操作
                + "      LogicalUnion(all=[true])\n" // 包含UNION ALL操作
                + "        LogicalProject(ROWTIME=[$0], PRODUCT=[$2])\n" // 第一个分支的投影
                + "          EnumerableTableScan(table=[[STREAMS, ORDERS]])\n" // 第一个分支的表扫描
                + "        LogicalProject(ROWTIME=[$0], PRODUCT=[$2])\n" // 第二个分支的投影
                + "          EnumerableTableScan(table=[[STREAMS, ORDERS]])\n") // 第二个分支的表扫描
        .explainContains( // 验证物理计划
            "EnumerableSort(sort0=[$0], sort1=[], dir0=[ASC], dir1=[DESC])\n" // 包含物理排序节点
                + "  EnumerableCalc(expr#0..3=[{inputs}], expr#4=[FLAG(HOUR)], expr#5=[FLOOR($t0, $t4)], ROWTIME=[$t5], PRODUCT=[$t2], UNITS=[$t3])\n" // 包含计算节点
                + "    EnumerableInterpreter\n" // 包含解释器
                + "        BindableTableScan(table=[[]])") // 包含可绑定的表扫描
        .returns( // 验证查询结果
            startsWith("ROWTIME=2015-02-15 10:00:00; PRODUCT=paper; UNITS=5", // 期望结果
                "ROWTIME=2015-02-15 10:00:00; PRODUCT=paint; UNITS=10",
                "ROWTIME=2015-02-15 10:00:00; PRODUCT=paint; UNITS=3"));
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-809">[CALCITE-809]
   * TableScan does not support large/infinite scans</a>.
   * 测试方法：验证无限流表不会将所有数据缓冲到内存中
   * 这个测试解决了CALCITE-809问题，确保流表扫描不会因为数据量大或无限而导致内存溢出
   */
  @Test void testInfiniteStreamsDoNotBufferInMemory() { // testInfiniteStreamsDoNotBufferInMemory方法：测试无限流表不会在内存中缓冲所有数据
    CalciteAssert.model(STREAM_MODEL) // 使用STREAM_MODEL创建Calcite断言
        .withDefaultSchema(INFINITE_STREAM_SCHEMA_NAME) // 设置默认模式为INFINITE_STREAMS（无限流）
        .query("select stream * from orders") // 执行流查询：选择所有数据
        .limit(100) // 限制结果为100行，避免无限数据
        .explainContains("EnumerableInterpreter\n" // 验证物理计划包含解释器
            + "  BindableTableScan(table=[[INFINITE_STREAMS, ORDERS, (STREAM)]])") // 验证包含可绑定的表扫描，标记为流表
        .returnsCount(100); // 验证返回100行结果，证明流表扫描不会缓冲所有数据
  }

  @Test @Timeout(10) public void testStreamCancel() { // testStreamCancel方法：测试流查询的取消功能，超时时间设置为10秒
    final String explain = "EnumerableInterpreter\n" // 定义期望的物理计划字符串
        + "  BindableTableScan(table=[[INFINITE_STREAMS, ORDERS, (STREAM)]])"; // 包含解释器和流表扫描
    CalciteAssert.model(STREAM_MODEL) // 使用STREAM_MODEL创建Calcite断言
        .withDefaultSchema(INFINITE_STREAM_SCHEMA_NAME) // 设置默认模式为INFINITE_STREAMS（无限流）
        .query("select stream * from orders") // 执行流查询：选择所有数据（无限流）
        .explainContains(explain) // 验证物理计划符合预期
        .returns(resultSet -> { // 使用自定义结果集验证器
          int n = 0; // 初始化行计数器
          try { // 开始try块
            while (resultSet.next()) { // 循环读取结果集
              if (++n == 5) { // 当读取到第5行时
                new Thread(() -> { // 创建新线程
                  try { // 开始try块
                    Thread.sleep(3); // 睡眠3毫秒
                    resultSet.getStatement().cancel(); // 取消当前语句
                  } catch (InterruptedException | SQLException e) { // 捕获中断和SQL异常
                    // ignore // 忽略异常
                  }
                }).start(); // 启动线程
              }
            }
            fail("expected cancel, got end-of-data"); // 如果循环正常结束，测试失败（应该被取消）
          } catch (SQLException e) { // 捕获SQL异常
            assertThat(e.getMessage(), is("Statement canceled")); // 验证异常消息为"Statement canceled"
          }
          // With a 3 millisecond delay, typically n is between 200 - 400
          // before cancel takes effect.
          // 注释说明：3毫秒延迟后，通常在200-400行时取消生效
          assertThat(n, // 断言行数n
              ComparatorMatcherBuilder.<Integer>usingNaturalOrdering().greaterThan(5)); // 验证n大于5，证明在取消前读取了多行
        });
  }

  @Test void testStreamToRelationJoin() { // testStreamToRelationJoin方法：测试流表与关系表的连接功能
    CalciteAssert.model(STREAM_JOINS_MODEL) // 使用STREAM_JOINS_MODEL创建Calcite断言
        .withDefaultSchema(STREAM_JOINS_SCHEMA_NAME) // 设置默认模式为STREAM_JOINS
        .query("select stream " // 执行流查询：连接流表和关系表
            + "orders.rowtime as rowtime, orders.id as orderId, products.supplier as supplierId " // 选择订单时间、订单ID和供应商ID
            + "from orders join products on orders.product = products.id") // 连接orders流表和products关系表，条件是产品ID匹配
        .convertContains("LogicalDelta\n" // 验证逻辑计划包含Delta操作符
            + "  LogicalProject(ROWTIME=[$0], ORDERID=[], SUPPLIERID=[$4])\n" // 验证包含投影操作
            + "    LogicalJoin(condition=[=($2, $3)], joinType=[inner])\n" // 验证包含内连接操作
            + "      LogicalProject(ROWTIME=[$0], ID=[], PRODUCT0=[CAST($2):VARCHAR(32) NOT NULL])\n" // 验证左侧投影，包含类型转换
            + "        LogicalTableScan(table=[[STREAM_JOINS, ORDERS]])\n" // 验证左侧表扫描（流表）
            + "      LogicalTableScan(table=[[STREAM_JOINS, PRODUCTS]])\n") // 验证右侧表扫描（关系表）
        .explainContains("" // 验证物理计划
            + "EnumerableCalc(expr#0..4=[{inputs}], proj#0..1=[{exprs}], SUPPLIERID=[$t4])\n" // 包含计算节点，提取供应商ID
            + "  EnumerableMergeJoin(condition=[=($2, $3)], joinType=[inner])\n" // 包含归并连接节点（流连接使用归并连接）
            + "    EnumerableSort(sort0=[$2], dir0=[ASC])\n" // 包含左侧排序节点
            + "      EnumerableCalc(expr#0..3=[{inputs}], expr#4=[CAST($t2):VARCHAR(32) NOT NULL], proj#0..1=[{exprs}], PRODUCT0=[$t4])\n" // 包含计算节点，类型转换
            + "        EnumerableInterpreter\n" // 包含解释器
            + "          BindableTableScan(table=[[STREAM_JOINS, ORDERS, (STREAM)]])\n" // 包含可绑定的流表扫描
            + "    EnumerableSort(sort0=[$0], dir0=[ASC])\n" // 包含右侧排序节点
            + "      EnumerableTableScan(table=[[STREAM_JOINS, PRODUCTS]])\n") // 包含关系表扫描
        .returns( // 验证查询结果
            startsWith("ROWTIME=2015-02-15 10:24:45; ORDERID=3; SUPPLIERID=1", // 第一行结果
                "ROWTIME=2015-02-15 10:15:00; ORDERID=1; SUPPLIERID=1", // 第二行结果
                "ROWTIME=2015-02-15 10:58:00; ORDERID=4; SUPPLIERID=1")); // 第三行结果
  }

  @Disabled // @Disabled注解：禁用此测试方法
  @Test void testTumbleViaOver() { // testTumbleViaOver方法：测试通过窗口函数实现滚动窗口功能（已禁用）
    String sql = "WITH HourlyOrderTotals (rowtime, productId, c, su) AS (\n" // 定义公共表表达式（CTE）
        + "  SELECT FLOOR(rowtime TO HOUR),\n" // 选择小时时间戳
        + "    productId,\n" // 选择产品ID
        + "    COUNT(*),\n" // 计算数量
        + "    SUM(units)\n" // 计算总数
        + "  FROM Orders\n" // 从Orders表
        + "  GROUP BY FLOOR(rowtime TO HOUR), productId)\n" // 按小时和产品分组
        + "SELECT STREAM rowtime,\n" // 选择流数据
        + "  productId,\n" // 选择产品ID
        + "  SUM(su) OVER w AS su,\n" // 使用窗口函数计算滑动窗口的总和
        + "  SUM(c) OVER w AS c\n" // 使用窗口函数计算滑动窗口的数量
        + "FROM HourlyTotals\n" // 从HourlyTotals CTE
        + "WINDOW w AS (\n" // 定义窗口w
        + "  ORDER BY rowtime\n" // 按时间排序
        + "  PARTITION BY productId\n" // 按产品分区
        + "  RANGE INTERVAL '2' HOUR PRECEDING)\n"; // 定义范围为前2小时
    String sql2 = "" // 定义第二个SQL语句
        + "SELECT STREAM rowtime, productId, SUM(units) AS su, COUNT(*) AS c\n" // 选择流数据
        + "FROM Orders\n" // 从Orders表
        + "GROUP BY TUMBLE(rowtime, INTERVAL '1' HOUR)"; // 使用TUMBLE窗口函数按小时分组
    // sql and sql2 should give same result
    // 注释说明：两个SQL语句应该产生相同的结果
    CalciteAssert.model(STREAM_JOINS_MODEL) // 使用STREAM_JOINS_MODEL创建Calcite断言
        .query(sql); // 执行第一个SQL查询
  }

  private Consumer<ResultSet> startsWith(String... rows) { // startsWith方法：创建一个结果集消费者，用于验证结果集的前几行是否匹配预期
    final ImmutableList<String> rowList = ImmutableList.copyOf(rows); // 将预期行列表转换为不可变列表
    return resultSet -> { // 返回一个Consumer<ResultSet>函数
      try { // 开始try块
        final CalciteAssert.ResultSetFormatter formatter = // 创建结果集格式化器
            new CalciteAssert.ResultSetFormatter(); // 实例化格式化器
        final ResultSetMetaData metaData = resultSet.getMetaData(); // 获取结果集元数据
        for (String expectedRow : rowList) { // 遍历预期行列表
          if (!resultSet.next()) { // 如果结果集已结束
            throw new AssertionError("input ended too soon"); // 抛出断言错误：输入结束过早
          }
          formatter.rowToString(resultSet, metaData); // 将当前行格式化为字符串
          String actualRow = formatter.string(); // 获取格式化后的字符串
          assertThat(actualRow, equalTo(expectedRow)); // 断言实际行等于预期行
        }
      } catch (SQLException e) { // 捕获SQL异常
        throw TestUtil.rethrow(e); // 重新抛出异常
      }
    };
  }


} // StreamTest类结束
