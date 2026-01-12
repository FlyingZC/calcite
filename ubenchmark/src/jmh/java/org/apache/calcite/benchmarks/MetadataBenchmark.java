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
package org.apache.calcite.benchmarks;

import org.apache.calcite.jdbc.Driver;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.metadata.DefaultRelMetadataProvider;
import org.apache.calcite.rel.metadata.JaninoRelMetadataProvider;
import org.apache.calcite.rel.metadata.ProxyingMetadataHandlerProvider;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.runtime.Hook;
import org.apache.calcite.test.CalciteAssert;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A benchmark to compare metadata retrieval time for a complex query.
 * // 这是一个基准测试类，用于比较复杂查询中元数据检索的时间性能
 *
 * <p>Compares metadata retrieval performance on a large query.
 * // 对大型查询的元数据检索性能进行比较，主要测试三种不同的元数据提供者实现
 * // 1. JaninoRelMetadataProvider（默认实例，使用静态缓存）
 * // 2. JaninoRelMetadataProvider（每次重新编译）
 * // 3. ProxyingMetadataHandlerProvider（代理模式）
 */
@Fork(value = 1, jvmArgsPrepend = "-Xmx2048m") // Fork: 在单独的JVM进程中运行基准测试，value=1表示fork 1次，jvmArgsPrepend设置JVM最大堆内存为2048MB
@State(Scope.Benchmark) // State: 定义基准测试实例的作用域为Benchmark级别，所有线程共享同一个实例
@Measurement(iterations = 10, time = 100, timeUnit = TimeUnit.MILLISECONDS) // Measurement: 实际测量配置，执行10次迭代，每次100毫秒
@Warmup(iterations = 10, time = 100, timeUnit = TimeUnit.MILLISECONDS) // Warmup: 预热配置，执行10次迭代，每次100毫秒，用于JVM预热优化
@Threads(1) // Threads: 使用1个线程执行基准测试
@OutputTimeUnit(TimeUnit.MILLISECONDS) // OutputTimeUnit: 输出时间单位为毫秒
@BenchmarkMode(Mode.AverageTime) // BenchmarkMode: 基准测试模式为平均时间，计算每次操作的平均耗时
public class MetadataBenchmark { // 类名：元数据基准测试类

  @Setup // JMH注解：表示在基准测试开始前执行的方法，用于初始化测试环境
  public void setup() throws SQLException { // setup方法：初始化方法，注册Calcite JDBC驱动
    DriverManager.registerDriver(new Driver()); // 注册Calcite JDBC驱动到DriverManager，使得可以通过JDBC连接到Calcite
  }

  private void test(final Supplier<RelMetadataQuery> supplier) { // test方法：核心测试方法，接收一个RelMetadataQuery的供应者函数式接口，用于测试不同元数据提供者的性能
    CalciteAssert.that() // CalciteAssert: 创建一个断言构建器，用于测试SQL查询和关系代数表达式
        .with(CalciteAssert.Config.FOODMART_CLONE) // with: 配置使用FOODMART_CLONE配置，这是一个预定义的测试数据集配置（FoodMart销售数据仓库）
        .query("select \"store\".\"store_country\" as \"c0\",\n" // query: 定义要测试的SQL查询，这是一个复杂的聚合查询，涉及多表连接和分组
            + " \"time_by_day\".\"the_year\" as \"c1\",\n" // SQL: 选择时间表中的年份字段，别名为c1
            + " \"product_class\".\"product_family\" as \"c2\",\n" // SQL: 选择产品类别表中的产品族字段，别名为c2
            + " count(\"sales_fact_1997\".\"product_id\") as \"m0\"\n" // SQL: 聚合函数，统计销售事实表中的产品ID数量，别名为m0
            + "from \"store\" as \"store\",\n" // SQL: 从store表开始，别名为store
            + " \"sales_fact_1997\" as \"sales_fact_1997\",\n" // SQL: 连接sales_fact_1997销售事实表，别名为sales_fact_1997
            + " \"time_by_day\" as \"time_by_day\",\n" // SQL: 连接time_by_day时间维度表，别名为time_by_day
            + " \"product_class\" as \"product_class\",\n" // SQL: 连接product_class产品类别表，别名为product_class
            + " \"product\" as \"product\"\n" // SQL: 连接product产品表，别名为product
            + "where \"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\"\n" // SQL: 连接条件：销售事实表的store_id等于store表的store_id
            + "and \"store\".\"store_country\" = 'USA'\n" // SQL: 过滤条件：只选择美国的store
            + "and \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\"\n" // SQL: 连接条件：销售事实表的time_id等于时间表的time_id
            + "and \"time_by_day\".\"the_year\" = 1997\n" // SQL: 过滤条件：只选择1997年的数据
            + "and \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\"\n" // SQL: 连接条件：销售事实表的product_id等于产品表的product_id
            + "and \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\"\n" // SQL: 连接条件：产品表的product_class_id等于产品类别表的product_class_id
            + "group by \"store\".\"store_country\",\n" // SQL: 分组条件：按store的store_country字段分组
            + " \"time_by_day\".\"the_year\",\n" // SQL: 分组条件：按time_by_day的the_year字段分组
            + " \"product_class\".\"product_family\"") // SQL: 分组条件：按product_class的product_family字段分组
        .withHook(Hook.CONVERTED, (Consumer<RelNode>) rel -> { // withHook: 在SQL转换为关系代数节点后执行的钩子函数，CONVERTED表示转换完成后的钩子
          rel.getCluster().setMetadataQuerySupplier(supplier); // setMetadataQuerySupplier: 设置RelMetadataQuery的供应者，用于提供元数据查询实例，这里传入测试的supplier参数
          rel.getCluster().invalidateMetadataQuery(); // invalidateMetadataQuery: 使缓存的元数据查询失效，强制重新创建，确保使用新设置的supplier
        })
        .explainContains("" // explainContains: 验证生成的物理执行计划包含预期的操作符树
            + "EnumerableAggregate(group=[{1, 6, 10}], m0=[COUNT()])\n" // 物理计划：聚合操作，按字段1、6、10分组，计算COUNT()聚合值
            + "  EnumerableMergeJoin(condition=[=($2, $8)], joinType=[inner])\n" // 物理计划：归并连接，条件是字段2等于字段8，内连接
            + "    EnumerableSort(sort0=[$2], dir0=[ASC])\n" // 物理计划：排序操作，按字段2升序排序，为归并连接做准备
            + "      EnumerableMergeJoin(condition=[=($3, $5)], joinType=[inner])\n" // 物理计划：另一个归并连接，条件是字段3等于字段5，内连接
            + "        EnumerableSort(sort0=[$3], dir0=[ASC])\n" // 物理计划：排序操作，按字段3升序排序
            + "          EnumerableHashJoin(condition=[=($0, $4)], joinType=[inner])\n" // 物理计划：哈希连接，条件是字段0等于字段4，内连接
            + "            EnumerableCalc(expr#0..23=[{inputs}], expr#24=['USA':VARCHAR(30)], " // 物理计划：Calc表达式节点，执行计算和投影，包含输入表达式和常量
            + "expr#25=[=($t9, $t24)], store_id=[$t0], store_country=[$t9], $condition=[$t25])\n" // 物理计划：Calc节点的详细表达式，包括过滤条件store_country='USA'
            + "              EnumerableTableScan(table=[[foodmart2, store]])\n" // 物理计划：表扫描，扫描foodmart2数据库的store表
            + "            EnumerableCalc(expr#0..7=[{inputs}], proj#0..1=[{exprs}], " // 物理计划：Calc表达式节点，执行投影操作
            + "store_id=[$t4])\n" // 物理计划：投影出store_id字段
            + "              EnumerableTableScan(table=[[foodmart2, sales_fact_1997]])\n" // 物理计划：表扫描，扫描foodmart2数据库的sales_fact_1997表
            + "        EnumerableCalc(expr#0..9=[{inputs}], expr#10=[CAST($t4):INTEGER], " // 物理计划：Calc表达式节点，执行类型转换
            + "expr#11=[1997], expr#12=[=($t10, $t11)], time_id=[$t0], the_year=[$t4], " // 物理计划：包含常量1997和过滤条件the_year=1997
            + "$condition=[$t12])\n" // 物理计划：过滤条件表达式
            + "          EnumerableTableScan(table=[[foodmart2, time_by_day]])\n" // 物理计划：表扫描，扫描foodmart2数据库的time_by_day表
            + "    EnumerableHashJoin(condition=[=($0, $2)], joinType=[inner])\n" // 物理计划：哈希连接，条件是字段0等于字段2，内连接
            + "      EnumerableCalc(expr#0..14=[{inputs}], proj#0..1=[{exprs}])\n" // 物理计划：Calc表达式节点，执行投影操作
            + "        EnumerableTableScan(table=[[foodmart2, product]])\n" // 物理计划：表扫描，扫描foodmart2数据库的product表
            + "      EnumerableCalc(expr#0..4=[{inputs}], product_class_id=[$t0], " // 物理计划：Calc表达式节点，执行投影操作
            + "product_family=[$t4])\n" // 物理计划：投影出product_class_id和product_family字段
            + "        EnumerableTableScan(table=[[foodmart2, product_class]])") // 物理计划：表扫描，扫描foodmart2数据库的product_class表
        .returns("c0=USA; c1=1997; c2=Non-Consumable; m0=16414\n" // returns: 验证查询返回的结果集，第一行：美国、1997年、非消耗品类、产品数量16414
            + "c0=USA; c1=1997; c2=Drink; m0=7978\n" // returns: 第二行：美国、1997年、饮料类、产品数量7978
            + "c0=USA; c1=1997; c2=Food; m0=62445\n"); // returns: 第三行：美国、1997年、食品类、产品数量62445
  } // test方法结束

  @Benchmark // JMH注解：标记这是一个基准测试方法，JMH会多次执行此方法来测量性能
  public void janino() { // janino方法：测试使用默认JaninoRelMetadataProvider实例的性能（使用静态缓存）
    test(RelMetadataQuery::instance); // 调用test方法，传入RelMetadataQuery::instance方法引用，使用默认的元数据查询实例，利用静态缓存优化性能
  }

  @Benchmark // JMH注解：标记这是一个基准测试方法
  public void janinoWithCompile() { // janinoWithCompile方法：测试每次重新编译JaninoRelMetadataProvider的性能（不使用静态缓存）
    JaninoRelMetadataProvider.clearStaticCache(); // clearStaticCache: 清除JaninoRelMetadataProvider的静态缓存，强制每次都重新编译
    test(() -> // 调用test方法，传入一个Lambda表达式作为Supplier
        new RelMetadataQuery(JaninoRelMetadataProvider.of(DefaultRelMetadataProvider.INSTANCE))); // 创建新的RelMetadataQuery实例，使用JaninoRelMetadataProvider包装DefaultRelMetadataProvider，每次都会重新编译
  }

  @Benchmark // JMH注解：标记这是一个基准测试方法
  public void proxying() { // proxying方法：测试使用ProxyingMetadataHandlerProvider的性能（代理模式，不使用Janino编译）
    test( // 调用test方法，传入一个Lambda表达式作为Supplier
        () -> new RelMetadataQuery( // 创建新的RelMetadataQuery实例
            new ProxyingMetadataHandlerProvider( // 使用ProxyingMetadataHandlerProvider包装DefaultRelMetadataProvider，通过代理方式提供元数据，不使用Janino编译
        DefaultRelMetadataProvider.INSTANCE))); // 使用默认的元数据提供者实例
  }

} // MetadataBenchmark类结束
