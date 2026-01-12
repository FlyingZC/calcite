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
package org.apache.calcite.test; // 包声明：该类位于org.apache.calcite.test测试包中
import org.apache.calcite.plan.RelOptCluster; // 导入：关系表达式集群类，用于管理关系表达式和元数据提供者
import org.apache.calcite.plan.RelOptCost; // 导入：关系表达式成本类，表示执行计划的成本估计
import org.apache.calcite.plan.RelOptPlanner; // 导入：关系表达式优化器接口，用于执行查询优化
import org.apache.calcite.plan.RelOptTable; // 导入：关系表达式表类，表示表在优化器中的抽象
import org.apache.calcite.plan.RelOptUtil; // 导入：关系表达式工具类，提供关系表达式操作的实用方法
import org.apache.calcite.plan.volcano.VolcanoPlanner; // 导入：火山优化器，Calcite默认的基于成本的优化器实现
import org.apache.calcite.rel.RelNode; // 导入：关系表达式接口，所有关系操作节点的基类
import org.apache.calcite.rel.core.Project; // 导入：投影操作关系表达式，用于选择和计算输出列
import org.apache.calcite.rel.logical.LogicalCalc; // 导入：逻辑Calc关系表达式，用于表示计算逻辑
import org.apache.calcite.rel.metadata.DefaultRelMetadataProvider; // 导入：默认元数据提供者，提供基本的元数据实现
import org.apache.calcite.rel.metadata.JaninoRelMetadataProvider; // 导入：基于Janino编译的元数据提供者，提供高性能的元数据查询
import org.apache.calcite.rel.metadata.MetadataHandlerProvider; // 导入：元数据处理器提供者接口，用于获取元数据处理器
import org.apache.calcite.rel.metadata.ProxyingMetadataHandlerProvider; // 导入：代理元数据处理器提供者，提供代理功能的元数据处理器
import org.apache.calcite.rel.metadata.RelColumnOrigin; // 导入：关系列来源类，表示列的原始来源信息
import org.apache.calcite.rel.metadata.RelMetadataProvider; // 导入：元数据提供者接口，用于提供关系表达式的元数据
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入：元数据查询类，用于查询关系表达式的各种元数据信息
import org.apache.calcite.rex.RexNode; // 导入：行表达式节点接口，表示行级别的表达式
import org.apache.calcite.rex.RexProgram; // 导入：行表达式程序类，表示一组行表达式的程序
import org.apache.calcite.runtime.SqlFunctions; // 导入：SQL函数工具类，提供SQL相关的实用函数
import org.apache.calcite.sql.SqlExplainLevel; // 导入：SQL解释级别枚举，控制解释计划的详细程度
import org.apache.calcite.sql.test.SqlTestFactory; // 导入：SQL测试工厂类，用于创建测试环境
import org.apache.calcite.sql.test.SqlTester; // 导入：SQL测试器接口，用于执行SQL测试
import org.apache.calcite.sql2rel.SqlToRelConverter; // 导入：SQL到关系表达式转换器，将SQL转换为关系表达式树
import org.apache.calcite.tools.RelBuilder; // 导入：关系表达式构建器，用于构建关系表达式树
import org.apache.calcite.util.ImmutableBitSet; // 导入：不可变位集合类，用于表示列索引集合

import com.google.common.collect.ImmutableMap; // 导入：Google Guava不可变Map类
import com.google.common.collect.ImmutableSortedSet; // 导入：Google Guava不可变排序集合类
import com.google.common.collect.Iterables; // 导入：Google Guava可迭代工具类
import com.google.common.collect.Multimap; // 导入：Google Guava多值映射接口

import org.hamcrest.Matcher; // 导入：Hamcrest匹配器接口，用于断言匹配

import java.util.Collection; // 导入：Java集合接口
import java.util.HashMap; // 导入：Java HashMap类，哈希表实现的Map
import java.util.List; // 导入：Java List接口，有序集合
import java.util.Map; // 导入：Java Map接口，键值对集合
import java.util.Set; // 导入：Java Set接口，不重复元素集合
import java.util.function.Consumer; // 导入：Java函数式Consumer接口，接受一个参数不返回结果
import java.util.function.Function; // 导入：Java函数式Function接口，接受一个参数返回结果
import java.util.function.Supplier; // 导入：Java函数式Supplier接口，不接受参数返回结果
import java.util.function.UnaryOperator; // 导入：Java函数式UnaryOperator接口，接受和返回相同类型

import static com.google.common.base.Preconditions.checkArgument; // 导入：Google Guava前置条件检查工具

import static org.hamcrest.CoreMatchers.equalTo; // 导入：Hamcrest等于匹配器
import static org.hamcrest.CoreMatchers.is; // 导入：Hamcrest是匹配器
import static org.hamcrest.CoreMatchers.not; // 导入：Hamcrest非匹配器
import static org.hamcrest.CoreMatchers.notNullValue; // 导入：Hamcrest非空匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 导入：Hamcrest断言工具
import static org.hamcrest.Matchers.hasSize; // 导入：Hamcrest大小匹配器
import static org.junit.jupiter.api.Assertions.assertNotNull; // 导入：JUnit5非空断言
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入：JUnit5真值断言

/**
 * Parameters for a Metadata test. // 元数据测试的参数类，用于配置和执行关系表达式元数据的测试
 */
public class RelMetadataFixture { // 定义RelMetadataFixture类：元数据测试的固定装置类，提供测试环境的配置和断言方法
  /** Default fixture. // 默认的测试固定装置，提供预配置的测试环境
   *
   * <p>Use this, or call the {@code withXxx} methods to make one with the // 可以直接使用此默认装置，或调用withXxx方法创建具有所需属性的装置副本
   * properties you need. Fixtures are immutable, so whatever your test does // 装置是不可变的，因此测试对此装置的任何修改都不会影响其他测试
   * to this fixture, it won't break other tests. */
  public static final RelMetadataFixture DEFAULT = // 默认固定装置常量，使用Janino元数据提供者，启用标识符扩展和聚合唯一性
      new RelMetadataFixture(SqlToRelFixture.TESTER, // 创建RelMetadataFixture实例，传入SQL测试器
          SqlTestFactory.INSTANCE, MetadataConfig.JANINO, RelSupplier.NONE, // 使用Janino元数据配置，无关系表达式提供者
          false, r -> r) // 不转换为Calc，使用恒等变换
          .withFactory(f -> // 配置工厂，设置验证器和SQL到关系转换器的配置
              f.withValidatorConfig(c -> c.withIdentifierExpansion(true)) // 启用标识符扩展
                  .withSqlToRelConfig(c -> // 配置SQL到关系转换器
                      c.withRelBuilderConfigTransform(b -> // 配置RelBuilder
                          b.withAggregateUnique(true) // 启用聚合唯一性推断
                              .withPruneInputOfAggregate(false)))); // 禁用聚合输入剪枝

  public final SqlTester tester; // SQL测试器，用于执行SQL转换和测试
  public final SqlTestFactory factory; // SQL测试工厂，用于创建测试相关的组件
  public final MetadataConfig metadataConfig; // 元数据配置，指定使用哪种元数据提供者（Janino、Proxying或Nop）
  public final RelSupplier relSupplier; // 关系表达式提供者，用于提供测试用的关系表达式
  public final boolean convertAsCalc; // 是否将Project节点转换为LogicalCalc节点的标志
  public final UnaryOperator<RelNode> relTransform; // 关系表达式转换函数，用于对生成的关系表达式进行额外转换

  private RelMetadataFixture(SqlTester tester, // 私有构造方法，创建RelMetadataFixture实例，参数为SQL测试器
      SqlTestFactory factory, MetadataConfig metadataConfig, // SQL测试工厂和元数据配置
      RelSupplier relSupplier, // 关系表达式提供者
      boolean convertAsCalc, UnaryOperator<RelNode> relTransform) { // 是否转换为Calc标志和关系表达式转换函数
    this.tester = tester; // 初始化SQL测试器成员变量
    this.factory = factory; // 初始化SQL测试工厂成员变量
    this.metadataConfig = metadataConfig; // 初始化元数据配置成员变量
    this.relSupplier = relSupplier; // 初始化关系表达式提供者成员变量
    this.convertAsCalc = convertAsCalc; // 初始化转换为Calc标志成员变量
    this.relTransform = relTransform; // 初始化关系表达式转换函数成员变量
  }

  //~ 'With' methods ---------------------------------------------------------
  // Each method returns a copy of this fixture, changing the value of one // 'With'方法组：每个方法返回该装置的副本，只修改一个属性的值，保持不可变性
  // property.

  /** Creates a copy of this fixture that uses a given SQL query. // 创建使用给定SQL查询的装置副本
   */
  public RelMetadataFixture withSql(String sql) { // withSql方法：设置SQL查询字符串
    final RelSupplier relSupplier = RelSupplier.of(sql); // 从SQL字符串创建关系表达式提供者
    if (relSupplier.equals(this.relSupplier)) { // 如果新的提供者与当前相同
      return this; // 返回当前装置，不创建新实例
    }
    return new RelMetadataFixture(tester, factory, metadataConfig, relSupplier, // 创建新的RelMetadataFixture实例，使用新的关系表达式提供者
        convertAsCalc, relTransform); // 保持其他配置不变
  }

  public RelMetadataFixture withConfig( // withConfig方法：配置SQL到关系转换器的配置
          UnaryOperator<SqlToRelConverter.Config> transform) { // 接受转换器配置的转换函数
    return withFactory(f -> f.withSqlToRelConfig(transform)); // 通过配置工厂来设置SQL到关系转换器的配置
  }

  public RelMetadataFixture withRelBuilderConfig( // withRelBuilderConfig方法：配置RelBuilder的配置
          UnaryOperator<RelBuilder.Config> transform) { // 接受RelBuilder配置的转换函数
    return withConfig(c -> c.addRelBuilderConfigTransform(transform)); // 通过配置转换器来添加RelBuilder配置转换
  }

  /** Creates a copy of this fixture that uses a given function to create a // 创建使用给定函数创建关系表达式的装置副本
   * {@link RelNode}. */
  public RelMetadataFixture withRelFn(Function<RelBuilder, RelNode> relFn) { // withRelFn方法：设置使用RelBuilder创建关系表达式的函数
    final RelSupplier relSupplier = // 创建关系表达式提供者
        RelSupplier.of(builder -> { // 使用RelBuilder创建关系表达式
          metadataConfig.applyMetadata(builder.getCluster()); // 应用元数据配置到RelBuilder的集群
          return relFn.apply(builder); // 应用用户提供的函数创建关系表达式
        });
    if (relSupplier.equals(this.relSupplier)) { // 如果新的提供者与当前相同
      return this; // 返回当前装置，不创建新实例
    }
    return new RelMetadataFixture(tester, factory, metadataConfig, relSupplier, // 创建新的RelMetadataFixture实例，使用新的关系表达式提供者
        convertAsCalc, relTransform); // 保持其他配置不变
  }

  public RelMetadataFixture withFactory( // withFactory方法：配置SQL测试工厂
      UnaryOperator<SqlTestFactory> transform) { // 接受SQL测试工厂的转换函数
    final SqlTestFactory factory = transform.apply(this.factory); // 应用转换函数到当前工厂
    return new RelMetadataFixture(tester, factory, metadataConfig, relSupplier, // 创建新的RelMetadataFixture实例，使用新的工厂
        convertAsCalc, relTransform); // 保持其他配置不变
  }

  public RelMetadataFixture withTester(UnaryOperator<SqlTester> transform) { // withTester方法：配置SQL测试器
    final SqlTester tester = transform.apply(this.tester); // 应用转换函数到当前测试器
    return new RelMetadataFixture(tester, factory, metadataConfig, relSupplier, // 创建新的RelMetadataFixture实例，使用新的测试器
        convertAsCalc, relTransform); // 保持其他配置不变
  }

  public RelMetadataFixture withMetadataConfig(MetadataConfig metadataConfig) { // withMetadataConfig方法：配置元数据配置
    if (metadataConfig.equals(this.metadataConfig)) { // 如果新的配置与当前相同
      return this; // 返回当前装置，不创建新实例
    }
    return new RelMetadataFixture(tester, factory, metadataConfig, relSupplier, // 创建新的RelMetadataFixture实例，使用新的元数据配置
        convertAsCalc, relTransform); // 保持其他配置不变
  }

  public RelMetadataFixture convertingProjectAsCalc() { // convertingProjectAsCalc方法：启用将Project节点转换为LogicalCalc节点
    if (convertAsCalc) { // 如果已经启用转换
      return this; // 返回当前装置，不创建新实例
    }
    return new RelMetadataFixture(tester, factory, metadataConfig, relSupplier, // 创建新的RelMetadataFixture实例，启用Calc转换
        true, relTransform); // 设置convertAsCalc为true
  }

  public RelMetadataFixture withCatalogReaderFactory( // withCatalogReaderFactory方法：配置目录读取器工厂
      SqlTestFactory.CatalogReaderFactory catalogReaderFactory) { // 接受目录读取器工厂
    return withFactory(t -> t.withCatalogReader(catalogReaderFactory)); // 通过配置工厂来设置目录读取器
  }

  public RelMetadataFixture withCluster(UnaryOperator<RelOptCluster> factory) { // withCluster方法：配置关系表达式集群
    return withFactory(f -> f.withCluster(factory)); // 通过配置工厂来设置集群
  }

  public RelMetadataFixture withRelTransform(UnaryOperator<RelNode> relTransform) { // withRelTransform方法：配置关系表达式转换函数
    final UnaryOperator<RelNode> relTransform1 = // 组合当前的转换函数和新的转换函数
        this.relTransform.andThen(relTransform)::apply; // 先应用当前转换，再应用新转换
    return new RelMetadataFixture(tester, factory, metadataConfig, relSupplier, // 创建新的RelMetadataFixture实例，使用组合后的转换函数
        convertAsCalc, relTransform1); // 使用新的转换函数
  }

  //~ Helper methods ---------------------------------------------------------
  // Don't use them too much. Write an assertXxx method if possible. // 辅助方法组：尽量少用，尽可能编写assertXxx方法

  /** Only for use by RelSupplier. Must be package-private. // 仅由RelSupplier使用，必须是包私有的
   */
  RelNode sqlToRel(String sql) { // sqlToRel方法：将SQL字符串转换为关系表达式
    return tester.convertSqlToRel(factory, sql, false, false).rel; // 使用测试器将SQL转换为关系表达式，不验证，不展开
  }

  /** Creates a {@link RelNode} from this fixture's supplier // 从该装置的提供者创建关系表达式
   * (see {@link #withSql(String)} and {@link #withRelFn(Function)}). // 参见withSql和withRelFn方法
   */
  public RelNode toRel() { // toRel方法：根据装置配置创建关系表达式
    final RelNode rel = relSupplier.apply2(this); // 使用关系表达式提供者创建关系表达式
    metadataConfig.applyMetadata(rel.getCluster()); // 应用元数据配置到关系表达式的集群
    if (convertAsCalc) { // 如果启用转换为Calc
      Project project = (Project) rel; // 将关系表达式转换为Project
      checkArgument(project.getVariablesSet().isEmpty(), // 检查Project没有变量集合
          "Calc does not allow variables"); // Calc不允许变量
      RexProgram program = // 创建Rex程序
          RexProgram.create(project.getInput().getRowType(), // 输入行类型
              project.getProjects(), // 投影表达式列表
              null, // 条件表达式（null表示无条件）
              project.getRowType(), // 输出行类型
              project.getCluster().getRexBuilder()); // Rex构建器
      return LogicalCalc.create(project.getInput(), program); // 创建LogicalCalc节点
    }
    return relTransform.apply(rel); // 应用关系表达式转换函数
  }

  //~ Methods that execute tests ---------------------------------------------
  // 执行测试的方法组：提供各种断言方法来验证元数据

  /** Checks the CPU component of // 检查关系表达式自成本的CPU组件
   * {@link RelNode#computeSelfCost(RelOptPlanner, RelMetadataQuery)}. // 参见RelNode的computeSelfCost方法
   */
  @SuppressWarnings({"UnusedReturnValue"}) // 抑制未使用返回值的警告
  public RelMetadataFixture assertCpuCost(Matcher<Double> matcher, // assertCpuCost方法：断言CPU成本匹配给定的匹配器
      String reason) { // 断言失败时的原因描述
    RelNode rel = toRel(); // 创建关系表达式
    RelOptCost cost = computeRelSelfCost(rel); // 计算关系表达式的自成本
    assertThat(reason + "\n" // 断言CPU成本匹配，附加SQL和计划信息
            + "sql:" + relSupplier + "\n"
            + "plan:" + RelOptUtil.toString(rel, SqlExplainLevel.ALL_ATTRIBUTES),
        cost.getCpu(), matcher); // 获取CPU成本并断言匹配
    return this; // 返回this以支持链式调用
  }

  private static RelOptCost computeRelSelfCost(RelNode rel) { // computeRelSelfCost方法：计算关系表达式的自成本
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象
    RelOptPlanner planner = new VolcanoPlanner(); // 创建火山优化器
    return rel.computeSelfCost(planner, mq); // 使用优化器和元数据查询计算自成本
  }

  /** Checks {@link RelMetadataQuery#areRowsUnique(RelNode)} for all // 检查行是否唯一，对ignoreNulls的所有值进行检查
   * values of {@code ignoreNulls}. */
  @SuppressWarnings({"UnusedReturnValue"}) // 抑制未使用返回值的警告
  public RelMetadataFixture assertRowsUnique(Matcher<Boolean> matcher, // assertRowsUnique方法：断言行是否唯一，检查ignoreNulls为false和true两种情况
      String reason) { // 断言失败时的原因描述
    return assertRowsUnique(false, matcher, reason) // 检查ignoreNulls为false的情况
        .assertRowsUnique(true, matcher, reason); // 检查ignoreNulls为true的情况
  }

  /** Checks {@link RelMetadataQuery#areRowsUnique(RelNode)}. // 检查行是否唯一
   */
  public RelMetadataFixture assertRowsUnique(boolean ignoreNulls, // assertRowsUnique方法：断言行是否唯一
      Matcher<Boolean> matcher, String reason) { // 接受匹配器和原因描述
    RelNode rel = toRel(); // 创建关系表达式
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象
    Boolean rowsUnique = mq.areRowsUnique(rel, ignoreNulls); // 查询行是否唯一
    assertThat(reason + "\n" // 断言结果匹配，附加SQL和计划信息
            + "sql:" + relSupplier + "\n"
            + "plan:" + RelOptUtil.toString(rel, SqlExplainLevel.ALL_ATTRIBUTES),
        rowsUnique, matcher); // 断言结果匹配
    return this; // 返回this以支持链式调用
  }

  /** Checks {@link RelMetadataQuery#getPercentageOriginalRows(RelNode)}. // 检查保留原始行的百分比
   */
  @SuppressWarnings({"UnusedReturnValue"}) // 抑制未使用返回值的警告
  public RelMetadataFixture assertPercentageOriginalRows(Matcher<Double> matcher) { // assertPercentageOriginalRows方法：断言保留原始行的百分比
    RelNode rel = toRel(); // 创建关系表达式
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象
    Double result = mq.getPercentageOriginalRows(rel); // 查询保留原始行的百分比
    assertNotNull(result); // 断言结果不为null
    assertThat(result, matcher); // 断言结果匹配
    return this; // 返回this以支持链式调用
  }

  private RelMetadataFixture checkColumnOrigin( // checkColumnOrigin方法：检查列来源的私有辅助方法
      Consumer<Set<RelColumnOrigin>> action) { // 接受对列来源集合的操作
    RelNode rel = toRel(); // 创建关系表达式
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象
    final Set<RelColumnOrigin> columnOrigins = mq.getColumnOrigins(rel, 0); // 查询第0列的来源
    action.accept(columnOrigins); // 执行对列来源集合的操作
    return this; // 返回this以支持链式调用
  }

  /** Checks that {@link RelMetadataQuery#getColumnOrigins(RelNode, int)} // 检查第0列的来源为空
   * for column 0 returns no origins. */
  @SuppressWarnings({"UnusedReturnValue"}) // 抑制未使用返回值的警告
  public RelMetadataFixture assertColumnOriginIsEmpty() { // assertColumnOriginIsEmpty方法：断言列来源为空
    return checkColumnOrigin(result -> { // 检查列来源集合
      assertNotNull(result); // 断言结果不为null
      assertTrue(result.isEmpty()); // 断言结果为空
    });
  }

  private static void checkColumnOrigin( // checkColumnOrigin方法：检查列来源的静态辅助方法
      RelColumnOrigin rco, // 列来源对象
      String expectedTableName, // 期望的表名
      String expectedColumnName, // 期望的列名
      boolean expectedDerived) { // 期望的派生标志
    RelOptTable actualTable = rco.getOriginTable(); // 获取来源表
    List<String> actualTableName = actualTable.getQualifiedName(); // 获取表的限定名称
    assertThat( // 断言表名匹配
        Iterables.getLast(actualTableName), // 获取表名的最后一部分（非限定名）
        equalTo(expectedTableName)); // 断言等于期望的表名
    assertThat( // 断言列名匹配
        actualTable.getRowType() // 获取行类型
            .getFieldList() // 获取字段列表
            .get(rco.getOriginColumnOrdinal()) // 获取来源列序号对应的字段
            .getName(), // 获取字段名称
        equalTo(expectedColumnName)); // 断言等于期望的列名
    assertThat(rco.isDerived(), equalTo(expectedDerived)); // 断言派生标志等于期望值
  }

  /** Checks that {@link RelMetadataQuery#getColumnOrigins(RelNode, int)} // 检查第0列的来源只有一个
   * for column 0 returns one origin. */
  @SuppressWarnings({"UnusedReturnValue"}) // 抑制未使用返回值的警告
  public RelMetadataFixture assertColumnOriginSingle(String expectedTableName, // assertColumnOriginSingle方法：断言列来源只有一个
      String expectedColumnName, boolean expectedDerived) { // 接受期望的表名、列名和派生标志
    return checkColumnOrigin(result -> { // 检查列来源集合
      assertNotNull(result); // 断言结果不为null
      assertThat(result, hasSize(1)); // 断言结果大小为1
      RelColumnOrigin rco = result.iterator().next(); // 获取唯一的列来源
      checkColumnOrigin(rco, expectedTableName, expectedColumnName, // 检查列来源的详细信息
          expectedDerived);
    });
  }

  /** Checks that {@link RelMetadataQuery#getColumnOrigins(RelNode, int)} // 检查第0列的来源有两个
   * for column 0 returns two origins. */
  @SuppressWarnings({"UnusedReturnValue"}) // 抑制未使用返回值的警告
  public RelMetadataFixture assertColumnOriginDouble( // assertColumnOriginDouble方法：断言列来源有两个
      String expectedTableName1, String expectedColumnName1, // 第一个来源的表名和列名
      String expectedTableName2, String expectedColumnName2, // 第二个来源的表名和列名
      boolean expectedDerived) { // 期望的派生标志
    assertThat("required so that the test mechanism works", expectedTableName1, // 断言两个表名不同，确保测试机制正常工作
        not(is(expectedTableName2)));
    return checkColumnOrigin(result -> { // 检查列来源集合
      assertNotNull(result); // 断言结果不为null
      assertThat(result, hasSize(2)); // 断言结果大小为2
      for (RelColumnOrigin rco : result) { // 遍历每个列来源
        RelOptTable actualTable = rco.getOriginTable(); // 获取来源表
        List<String> actualTableName = actualTable.getQualifiedName(); // 获取表的限定名称
        String actualUnqualifiedName = Iterables.getLast(actualTableName); // 获取非限定表名
        if (actualUnqualifiedName.equals(expectedTableName1)) { // 如果匹配第一个表名
          checkColumnOrigin(rco, expectedTableName1, expectedColumnName1, // 检查列来源的详细信息
              expectedDerived);
        } else { // 否则匹配第二个表名
          checkColumnOrigin(rco, expectedTableName2, expectedColumnName2, // 检查列来源的详细信息
              expectedDerived);
        }
      }
    });
  }

  @SuppressWarnings({"UnusedReturnValue"}) // 抑制未使用返回值的警告
  public RelMetadataFixture assertThatUniqueKeysAre(ImmutableBitSet... expectedUniqueKeys) { // assertThatUniqueKeysAre方法：断言唯一键，默认ignoreNulls为false
    return assertThatUniqueKeysAre(false, expectedUniqueKeys); // 调用重载方法，ignoreNulls为false
  }

  /** Checks result of getting unique keys for SQL. // 检查SQL的唯一键获取结果
   */
  @SuppressWarnings({"UnusedReturnValue"}) // 抑制未使用返回值的警告
  public RelMetadataFixture assertThatUniqueKeysAre(boolean ignoreNulls, // assertThatUniqueKeysAre方法：断言唯一键，接受ignoreNulls参数
      ImmutableBitSet... expectedUniqueKeys) { // 接受期望的唯一键数组
    RelNode rel = toRel(); // 创建关系表达式
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象
    Set<ImmutableBitSet> result = mq.getUniqueKeys(rel, ignoreNulls); // 查询唯一键集合
    assertThat(result, notNullValue()); // 断言结果不为null
    assertThat("unique keys, sql: " + relSupplier // 断言唯一键集合匹配，附加SQL和计划信息
            + ", rel: " + RelOptUtil.toString(rel),
        ImmutableSortedSet.copyOf(result), // 将结果转换为不可变排序集合
        is(ImmutableSortedSet.copyOf(expectedUniqueKeys))); // 断言等于期望的排序集合
    checkUniqueConsistent(rel, ignoreNulls); // 检查唯一键的一致性
    return this; // 返回this以支持链式调用
  }

  /**
   * Asserts that {@link RelMetadataQuery#getUniqueKeys(RelNode)} // 断言getUniqueKeys和areColumnsUnique返回一致的结果
   * and {@link RelMetadataQuery#areColumnsUnique(RelNode, ImmutableBitSet)}
   * return consistent results.
   */
  private static void checkUniqueConsistent(RelNode rel, boolean ignoreNulls) { // checkUniqueConsistent方法：检查唯一键一致性
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象
    final Set<ImmutableBitSet> uniqueKeys = mq.getUniqueKeys(rel, ignoreNulls); // 获取唯一键集合
    assertThat(uniqueKeys, notNullValue()); // 断言唯一键集合不为null
    for (ImmutableBitSet key : uniqueKeys) { // 遍历每个唯一键
      Boolean result2 = mq.areColumnsUnique(rel, key, ignoreNulls); // 查询列是否唯一
      assertThat("areColumnsUnique. key: " + key // 断言结果一致，附加详细错误信息
          + ", uniqueKeys: " + uniqueKeys
          + ", rel: " + RelOptUtil.toString(rel),
          SqlFunctions.isTrue(result2), is(isUnique(uniqueKeys, key))); // 断言结果匹配
    }
  }

  /**
   * Returns whether {@code key} is unique, that is, whether it or a subset // 返回键是否唯一，即它或其子集是否在唯一键集合中
   * is in {@code uniqueKeys}.
   */
  private static boolean isUnique(Set<ImmutableBitSet> uniqueKeys, // isUnique方法：判断键是否唯一
      ImmutableBitSet key) { // 接受唯一键集合和待检查的键
    for (ImmutableBitSet uniqueKey : uniqueKeys) { // 遍历每个唯一键
      if (key.contains(uniqueKey)) { // 如果待检查的键包含某个唯一键
        return true; // 返回true，表示键唯一
      }
    }
    return false; // 返回false，表示键不唯一
  }

  /** Checks {@link RelMetadataQuery#getRowCount(RelNode)}, // 检查行数、最小行数和最大行数
   * {@link RelMetadataQuery#getMaxRowCount(RelNode)},
   * and {@link RelMetadataQuery#getMinRowCount(RelNode)}. */
  @SuppressWarnings({"UnusedReturnValue"}) // 抑制未使用返回值的警告
  public RelMetadataFixture assertThatRowCount(Matcher<Number> rowCountMatcher, // assertThatRowCount方法：断言行数相关信息
      Matcher<Number> minRowCountMatcher, Matcher<Number> maxRowCountMatcher) { // 接受行数、最小行数和最大行数的匹配器
    final RelNode rel = toRel(); // 创建关系表达式
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象

    final Double rowCount = mq.getRowCount(rel); // 查询行数
    assertThat(rowCount, notNullValue()); // 断言行数不为null
    assertThat(rowCount, rowCountMatcher); // 断言行数匹配

    final Double min = mq.getMinRowCount(rel); // 查询最小行数
    assertThat(min, notNullValue()); // 断言最小行数不为null
    assertThat(min, minRowCountMatcher); // 断言最小行数匹配

    final Double max = mq.getMaxRowCount(rel); // 查询最大行数
    assertThat(max, notNullValue()); // 断言最大行数不为null
    assertThat(max, maxRowCountMatcher); // 断言最大行数匹配
    return this; // 返回this以支持链式调用
  }

  /** Checks {@link RelMetadataQuery#getSelectivity(RelNode, RexNode)}. // 检查选择率
   */
  public RelMetadataFixture assertThatSelectivity(Matcher<Double> matcher) { // assertThatSelectivity方法：断言选择率
    RelNode rel = toRel(); // 创建关系表达式
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象
    Double result = mq.getSelectivity(rel, null); // 查询选择率，null谓词表示整体选择率
    assertThat(result, notNullValue()); // 断言结果不为null
    assertThat(result, matcher); // 断言结果匹配
    return this; // 返回this以支持链式调用
  }

  /** Checks // 检查不同行数，使用null谓词
   * {@link RelMetadataQuery#getDistinctRowCount(RelNode, ImmutableBitSet, RexNode)}
   * with a null predicate. */
  public RelMetadataFixture assertThatDistinctRowCount(ImmutableBitSet groupKey, // assertThatDistinctRowCount方法：断言不同行数，接受组键
      Matcher<Double> matcher) { // 接受匹配器
    return assertThatDistinctRowCount(r -> groupKey, matcher); // 调用重载方法，返回常量组键
  }

  /** Checks // 检查不同行数，使用null谓词，从关系表达式派生组键
   * {@link RelMetadataQuery#getDistinctRowCount(RelNode, ImmutableBitSet, RexNode)}
   * with a null predicate, deriving the group key from the {@link RelNode}. */
  public RelMetadataFixture assertThatDistinctRowCount( // assertThatDistinctRowCount方法：断言不同行数，接受组键函数
      Function<RelNode, ImmutableBitSet> groupKeyFn, // 接受从关系表达式派生组键的函数
      Matcher<Double> matcher) { // 接受匹配器
    final RelNode rel = toRel(); // 创建关系表达式
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象
    final ImmutableBitSet groupKey = groupKeyFn.apply(rel); // 应用函数获取组键
    Double result = mq.getDistinctRowCount(rel, groupKey, null); // 查询不同行数，null谓词
    assertThat(result, matcher); // 断言结果匹配
    return this; // 返回this以支持链式调用
  }

  /** Checks the {@link RelNode} produced by {@link #toRel}. // 检查toRel方法产生的关系表达式
   */
  public RelMetadataFixture assertThatRel(Matcher<RelNode> matcher) { // assertThatRel方法：断言关系表达式
    final RelNode rel = toRel(); // 创建关系表达式
    assertThat(rel, matcher); // 断言关系表达式匹配
    return this; // 返回this以支持链式调用
  }

  /** Shorthand for a call to {@link #assertThatNodeTypeCount(Matcher)} // 调用assertThatNodeTypeCount的简写形式，使用常量映射
   * with a constant map. */
  @SuppressWarnings({"rawtypes", "unchecked", "UnusedReturnValue"}) // 抑制未检查和未使用返回值的警告
  public RelMetadataFixture assertThatNodeTypeCountIs( // assertThatNodeTypeCountIs方法：断言节点类型数量，接受键值对
      Class<? extends RelNode> k0, Integer v0, Object... rest) { // 接受第一个节点类型和数量，以及剩余的键值对
    final ImmutableMap.Builder<Class<? extends RelNode>, Integer> b = // 创建不可变Map构建器
        ImmutableMap.builder();
    b.put(k0, v0); // 添加第一个键值对
    for (int i = 0; i < rest.length;) { // 遍历剩余参数
      b.put((Class) rest[i++], (Integer) rest[i++]); // 添加键值对
    }
    return assertThatNodeTypeCount(is(b.build())); // 调用assertThatNodeTypeCount方法
  }

  /** Checks the number of each sub-class of {@link RelNode}, // 检查关系表达式每个子类的数量
   * calling {@link RelMetadataQuery#getNodeTypes(RelNode)}. */
  public RelMetadataFixture assertThatNodeTypeCount( // assertThatNodeTypeCount方法：断言节点类型数量
      Matcher<Map<Class<? extends RelNode>, Integer>> matcher) { // 接受节点类型到数量的映射匹配器
    final RelNode rel = toRel(); // 创建关系表达式
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象
    final Multimap<Class<? extends RelNode>, RelNode> result = mq.getNodeTypes(rel); // 查询节点类型的多值映射
    assertThat(result, notNullValue()); // 断言结果不为null
    final Map<Class<? extends RelNode>, Integer> resultCount = new HashMap<>(); // 创建结果计数Map
    for (Map.Entry<Class<? extends RelNode>, Collection<RelNode>> e : result.asMap().entrySet()) { // 遍历节点类型到节点集合的映射
      resultCount.put(e.getKey(), e.getValue().size()); // 统计每种节点类型的数量
    }
    assertThat(resultCount, matcher); // 断言结果计数匹配
    return this; // 返回this以支持链式调用
  }

  /** Checks {@link RelMetadataQuery#getUniqueKeys(RelNode)}. // 检查唯一键
   */
  public RelMetadataFixture assertThatUniqueKeys( // assertThatUniqueKeys方法：断言唯一键
      Matcher<Iterable<ImmutableBitSet>> matcher) { // 接受不可变位集合可迭代对象的匹配器
    final RelNode rel = toRel(); // 创建关系表达式
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象
    final Set<ImmutableBitSet> result = mq.getUniqueKeys(rel); // 查询唯一键集合
    assertThat(result, matcher); // 断言结果匹配
    return this; // 返回this以支持链式调用
  }

  /** Checks {@link RelMetadataQuery#areColumnsUnique(RelNode, ImmutableBitSet)}. // 检查列是否唯一
   */
  public RelMetadataFixture assertThatAreColumnsUnique(ImmutableBitSet columns, // assertThatAreColumnsUnique方法：断言列是否唯一，接受列集合
      Matcher<Boolean> matcher) { // 接受布尔匹配器
    return assertThatAreColumnsUnique(r -> columns, r -> r, matcher); // 调用重载方法，使用恒等函数
  }

  /** Checks {@link RelMetadataQuery#areColumnsUnique(RelNode, ImmutableBitSet)}, // 检查列是否唯一，通过函数派生参数
   * deriving parameters via functions. */
  public RelMetadataFixture assertThatAreColumnsUnique( // assertThatAreColumnsUnique方法：断言列是否唯一，接受派生函数
      Function<RelNode, ImmutableBitSet> columnsFn, // 接受从关系表达式派生列集合的函数
      UnaryOperator<RelNode> relFn, // 接受关系表达式转换函数
      Matcher<Boolean> matcher) { // 接受布尔匹配器
    RelNode rel = toRel(); // 创建关系表达式
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象
    final ImmutableBitSet columns = columnsFn.apply(rel); // 应用函数获取列集合
    final RelNode rel2 = relFn.apply(rel); // 应用函数转换关系表达式
    final Boolean areColumnsUnique = mq.areColumnsUnique(rel2, columns); // 查询列是否唯一
    assertThat(areColumnsUnique, matcher); // 断言结果匹配
    return this; // 返回this以支持链式调用
  }

  /** Checks {@link RelMetadataQuery#areRowsUnique(RelNode)}. // 检查行是否唯一
   */
  @SuppressWarnings({"UnusedReturnValue"}) // 抑制未使用返回值的警告
  public RelMetadataFixture assertThatAreRowsUnique(Matcher<Boolean> matcher) { // assertThatAreRowsUnique方法：断言行是否唯一
    RelNode rel = toRel(); // 创建关系表达式
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象
    final Boolean areRowsUnique = mq.areRowsUnique(rel); // 查询行是否唯一
    assertThat(areRowsUnique, matcher); // 断言结果匹配
    return this; // 返回this以支持链式调用
  }

  /**
   * A configuration that describes how metadata should be configured. // 描述如何配置元数据的配置类
   */
  public static class MetadataConfig { // MetadataConfig内部类：元数据配置类，定义不同的元数据提供者配置
    static final MetadataConfig JANINO = // JANINO配置：使用Janino编译的元数据提供者，高性能
        new MetadataConfig("Janino", // 配置名称为"Janino"
            JaninoRelMetadataProvider::of, // 使用JaninoRelMetadataProvider的of方法创建处理器提供者
            RelMetadataQuery.THREAD_PROVIDERS::get, // 使用线程本地提供者
            true); // 启用缓存

    static final MetadataConfig PROXYING = // PROXYING配置：使用代理元数据处理器提供者
        new MetadataConfig("Proxying", // 配置名称为"Proxying"
            ProxyingMetadataHandlerProvider::new, // 使用ProxyingMetadataHandlerProvider构造函数
            () -> DefaultRelMetadataProvider.INSTANCE, // 使用默认元数据提供者实例
            false); // 不启用缓存

    static final MetadataConfig NOP = // NOP配置：无操作配置，不应用元数据
        new MetadataConfig("Nop", // 配置名称为"Nop"
            ProxyingMetadataHandlerProvider::new, // 使用ProxyingMetadataHandlerProvider构造函数
            () -> DefaultRelMetadataProvider.INSTANCE, // 使用默认元数据提供者实例
            false) { // 不启用缓存
          @Override void applyMetadata(RelOptCluster cluster, // 重写applyMetadata方法，不应用元数据
              RelMetadataProvider provider,
              Function<MetadataHandlerProvider, RelMetadataQuery> supplierFactory) {
            // do nothing // 不做任何操作
          }
        };

    public final String name; // 配置名称
    public final Function<RelMetadataProvider, MetadataHandlerProvider> converter; // 元数据提供者到处理器提供者的转换函数
    public final Supplier<RelMetadataProvider> defaultProviderSupplier; // 默认元数据提供者的供应者
    public final boolean isCaching; // 是否启用缓存的标志

    public MetadataConfig(String name, // MetadataConfig构造方法，创建元数据配置
        Function<RelMetadataProvider, MetadataHandlerProvider> converter, // 接受转换函数
        Supplier<RelMetadataProvider> defaultProviderSupplier, // 接受默认提供者供应者
        boolean isCaching) { // 接受缓存标志
      this.name = name; // 初始化配置名称
      this.converter = converter; // 初始化转换函数
      this.defaultProviderSupplier = defaultProviderSupplier; // 初始化默认提供者供应者
      this.isCaching = isCaching; // 初始化缓存标志
    }

    public MetadataHandlerProvider getDefaultHandlerProvider() { // getDefaultHandlerProvider方法：获取默认的元数据处理器提供者
      return converter.apply(defaultProviderSupplier.get()); // 应用转换函数到默认提供者
    }

    void applyMetadata(RelOptCluster cluster) { // applyMetadata方法：应用元数据到集群，使用默认提供者
      applyMetadata(cluster, defaultProviderSupplier.get()); // 调用重载方法
    }

    void applyMetadata(RelOptCluster cluster, // applyMetadata方法：应用元数据到集群，接受提供者
        RelMetadataProvider provider) { // 接受元数据提供者
      applyMetadata(cluster, provider, RelMetadataQuery::new); // 调用重载方法，使用默认的RelMetadataQuery构造函数
    }

    void applyMetadata(RelOptCluster cluster, // applyMetadata方法：应用元数据到集群，接受提供者和供应者工厂
        RelMetadataProvider provider,
        Function<MetadataHandlerProvider, RelMetadataQuery> supplierFactory) { // 接受RelMetadataQuery供应者工厂
      cluster.setMetadataProvider(provider); // 设置集群的元数据提供者
      cluster.setMetadataQuerySupplier(() -> // 设置集群的元数据查询供应者
          supplierFactory.apply(converter.apply(provider))); // 应用转换函数和供应者工厂
      cluster.invalidateMetadataQuery(); // 使元数据查询失效，强制重新创建
    }

    public boolean isCaching() { // isCaching方法：返回是否启用缓存
      return isCaching; // 返回缓存标志
    }

    @Override public String toString() { // toString方法：返回配置名称
      return name; // 返回配置名称
    }
  }
} // RelMetadataFixture类结束
