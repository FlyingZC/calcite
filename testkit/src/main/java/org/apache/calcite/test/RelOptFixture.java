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
package org.apache.calcite.test; // 测试包，包含 Calcite 框架的测试类

import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 可枚举调用约定，用于生成可执行的 Java 代码
import org.apache.calcite.plan.Context; // 计划上下文接口，提供运行时信息
import org.apache.calcite.plan.ConventionTraitDef; // 调用约定特征定义，定义关系表达式的调用约定
import org.apache.calcite.plan.RelOptCluster; // 关系优化集群，包含共享的优化器组件如 RexBuilder
import org.apache.calcite.plan.RelOptPlanner; // 关系优化计划器接口，用于执行查询优化
import org.apache.calcite.plan.RelOptRule; // 关系优化规则接口，定义如何转换关系表达式
import org.apache.calcite.plan.RelOptUtil; // 关系优化工具类，提供各种实用方法
import org.apache.calcite.plan.hep.HepPlanner; // Hep（启发式）计划器，基于规则的优化器
import org.apache.calcite.plan.hep.HepProgram; // Hep 程序，定义要应用的一组规则
import org.apache.calcite.plan.hep.HepProgramBuilder; // Hep 程序构建器，用于构建 Hep 程序
import org.apache.calcite.plan.volcano.VolcanoPlanner; // Volcano 计划器，基于成本的优化器
import org.apache.calcite.rel.RelNode; // 关系节点接口，代表关系代数表达式
import org.apache.calcite.rel.core.RelFactories; // 关系工厂，用于创建各种关系节点
import org.apache.calcite.rel.metadata.ChainedRelMetadataProvider; // 链式元数据提供者，组合多个元数据提供者
import org.apache.calcite.rel.metadata.DefaultRelMetadataProvider; // 默认元数据提供者，提供基础的元数据计算
import org.apache.calcite.rel.metadata.RelMetadataProvider; // 关系元数据提供者接口，提供关系节点的元数据信息
import org.apache.calcite.rel.rules.CoreRules; // 核心规则集，包含常用的优化规则
import org.apache.calcite.runtime.FlatLists; // 扁平列表工具类，提供高效的列表操作
import org.apache.calcite.runtime.Hook; // 钩子机制，允许在特定点插入自定义逻辑
import org.apache.calcite.sql.test.SqlTestFactory; // SQL 测试工厂，用于创建测试环境
import org.apache.calcite.sql.test.SqlTester; // SQL 测试器接口，提供 SQL 测试功能
import org.apache.calcite.sql.util.SqlOperatorTables; // SQL 操作符表工具，提供操作符查找功能
import org.apache.calcite.sql.validate.SqlConformance; // SQL 合规性接口，定义 SQL 方言的符合程度
import org.apache.calcite.sql2rel.RelDecorrelator; // 关系去相关器，用于消除子查询的相关性
import org.apache.calcite.sql2rel.SqlToRelConverter; // SQL 到关系转换器，将 SQL 转换为关系代数表达式
import org.apache.calcite.test.catalog.MockCatalogReaderDynamic; // 动态模拟目录读取器，用于测试
import org.apache.calcite.tools.RelBuilder; // 关系构建器，用于构建关系表达式树
import org.apache.calcite.util.Closer; // 关闭工具，用于自动管理资源清理

import com.google.common.collect.ImmutableMap; // Google Guava 的不可变映射实现

import org.checkerframework.checker.nullness.qual.Nullable; // CheckerFramework 的可空类型注解，用于空值检查

import java.util.ArrayList; // Java 集合框架的动态数组实现
import java.util.List; // Java 集合框架的列表接口
import java.util.Map; // Java 集合框架的映射接口
import java.util.function.BiFunction; // Java 8 的双参数函数接口
import java.util.function.Consumer; // Java 8 的消费者函数接口
import java.util.function.Function; // Java 8 的单参数函数接口
import java.util.function.UnaryOperator; // Java 8 的一元操作符函数接口

import static org.apache.calcite.test.Matchers.relIsValid; // 测试匹配器，验证关系节点是否有效
import static org.apache.calcite.test.SqlToRelTestBase.NL; // 测试基类的换行符常量

import static org.hamcrest.CoreMatchers.is; // Hamcrest 断言匹配器
import static org.hamcrest.CoreMatchers.notNullValue; // Hamcrest 断言匹配器
import static org.hamcrest.MatcherAssert.assertThat; // Hamcrest 断言工具
import static org.junit.jupiter.api.Assertions.assertNotNull; // JUnit 5 断言方法

import static java.util.Objects.requireNonNull; // Java 对象工具类，用于非空检查

/**
 * 用于测试优化器规则的测试夹具类
 *
 * <p>这个类为测试 Calcite 的查询优化器规则提供了一个灵活的测试框架
 * 它允许开发者设置不同的优化器配置、规则集合，并验证优化前后的计划变化
 *
 * <p>它提供了流式 API，你可以通过链式方法调用来编写测试代码
 * 例如：fixture.sql("SELECT * FROM emp").withRule(rule).check()
 *
 * <p>fixture 是不可变的。如果你有两个测试用例需要类似的设置（例如，相同的 SQL 表达式和优化器规则集合），
 * 使用同一个 fixture 对象作为两个测试的起点是安全的，每个 with 方法都会返回一个新的 fixture 实例
 *
 * <p>这个类是 Calcite 测试框架的核心组件之一，广泛用于测试各种优化规则的正确性
 * 它支持多种优化器类型（VolcanoPlanner、HepPlanner），支持规则组合、钩子机制、去相关等高级功能
 *
 * <p>使用场景示例：
 * 1. 测试单个优化规则的效果
 * 2. 测试多个规则的组合效果
 * 3. 验证优化前后的计划是否符合预期
 * 4. 测试去相关功能
 * 5. 测试子查询转换规则
 */
public class RelOptFixture { // 关系优化测试夹具类，提供测试优化器规则的完整框架
  static final RelOptFixture DEFAULT = // 默认的测试夹具实例，使用标准配置，包含常用的测试设置
      new RelOptFixture(SqlToRelFixture.TESTER, SqlTestFactory.INSTANCE, // 使用默认的 SQL 测试器和测试工厂
          null, RelSupplier.NONE, null, null, // 初始时不设置差异仓库、关系供应器、预程序和计划器
          ImmutableMap.of(), (f, r) -> r, (f, r) -> r, false, false) // 空钩子映射、恒等转换函数、不启用去相关
          .withFactory(f -> // 配置测试工厂
              f.withValidatorConfig(c -> c.withIdentifierExpansion(true)) // 启用标识符展开
                  .withSqlToRelConfig(c -> c.withExpand(false))) // 禁用子查询展开
          .withRelBuilderConfig(b -> b.withPruneInputOfAggregate(false)); // 配置关系构建器，不修剪聚合的输入

  /**
   * 用于此测试的 SQL 测试器。这个字段是遗留的；没有 {@code withTester} 方法，总是使用相同的测试器
   * 测试器负责执行 SQL 并验证结果，是测试框架的核心组件
   */
  final SqlTester tester; // SQL 测试器，用于执行 SQL 查询和验证结果，是测试环境的核心组件
  final RelSupplier relSupplier; // 关系节点供应器，负责提供要测试的关系节点（从 SQL 或 RelBuilder 构建）
  final SqlTestFactory factory; // SQL 测试工厂，用于创建和配置测试环境的各种组件（验证器、转换器等）
  final @Nullable DiffRepository diffRepos; // 差异仓库，用于存储和比较测试的预期输出与实际输出，支持测试结果的版本控制
  final @Nullable HepProgram preProgram; // 预处理的 Hep 程序，在主优化器执行之前应用，用于准备关系表达式树
  final RelOptPlanner planner; // 关系优化计划器，负责执行优化规则并生成最优执行计划（可以是 VolcanoPlanner 或 HepPlanner）
  final ImmutableMap<Hook, Consumer<Object>> hooks; // 钩子映射，定义在特定执行点要调用的回调函数，用于监控和调试
  final BiFunction<RelOptFixture, RelNode, RelNode> before; // 优化前的转换函数，在主优化执行前对关系节点进行自定义转换
  final BiFunction<RelOptFixture, RelNode, RelNode> after; // 优化后的转换函数，在主优化执行后对关系节点进行自定义转换
  final boolean decorrelate; // 是否启用去相关，将相关子查询转换为不相关的形式（默认为 false）
  final boolean lateDecorrelate; // 是否启用延迟去相关，在优化后进行去相关操作（默认为 false）

  /**
 * RelOptFixture 的私有构造函数
 *
 * <p>这个构造函数创建一个新的测试夹具实例，初始化所有必要的配置参数
 * 由于 fixture 是不可变的，所有配置都通过构造函数设置，后续的修改都通过 with 方法创建新实例
 *
 * <p>构造函数对关键参数进行非空检查，确保测试环境的完整性
 *
 * @param tester SQL 测试器，用于执行 SQL 查询和验证结果（不能为 null）
 * @param factory SQL 测试工厂，用于创建和配置测试环境（可以为 null）
 * @param diffRepos 差异仓库，用于存储和比较测试输出（可以为 null）
 * @param relSupplier 关系节点供应器，提供要测试的关系节点（不能为 null）
 * @param preProgram 预处理的 Hep 程序，在主优化前执行（可以为 null）
 * @param planner 关系优化计划器，执行优化规则（可以为 null）
 * @param hooks 钩子映射，定义回调函数（不能为 null）
 * @param before 优化前的转换函数（不能为 null）
 * @param after 优化后的转换函数（不能为 null）
 * @param decorrelate 是否启用去相关
 * @param lateDecorrelate 是否启用延迟去相关
 */
RelOptFixture(SqlTester tester, SqlTestFactory factory, // 接收 SQL 测试器和测试工厂作为参数
      @Nullable DiffRepository diffRepos, RelSupplier relSupplier, // 接收差异仓库和关系供应器
      @Nullable HepProgram preProgram, RelOptPlanner planner, // 接收预处理程序和优化计划器
      ImmutableMap<Hook, Consumer<Object>> hooks, // 接收钩子映射
      BiFunction<RelOptFixture, RelNode, RelNode> before, // 接收优化前转换函数
      BiFunction<RelOptFixture, RelNode, RelNode> after, // 接收优化后转换函数
      boolean decorrelate, boolean lateDecorrelate) { // 接收去相关标志
    this.tester = requireNonNull(tester, "tester"); // 设置测试器，非空检查确保不为 null
    this.factory = factory; // 设置测试工厂
    this.diffRepos = diffRepos; // 设置差异仓库
    this.relSupplier = requireNonNull(relSupplier, "relSupplier"); // 设置关系供应器，非空检查
    this.before = requireNonNull(before, "before"); // 设置优化前转换函数，非空检查
    this.after = requireNonNull(after, "after"); // 设置优化后转换函数，非空检查
    this.preProgram = preProgram; // 设置预处理程序
    this.planner = planner; // 设置优化计划器
    this.hooks = requireNonNull(hooks, "hooks"); // 设置钩子映射，非空检查
    this.decorrelate = decorrelate; // 设置去相关标志
    this.lateDecorrelate = lateDecorrelate; // 设置延迟去相关标志
  }

  /**
 * 设置差异仓库并返回新的测试夹具实例
 *
 * <p>差异仓库用于存储和比较测试的预期输出与实际输出，支持测试结果的版本控制
 * 通过使用差异仓库，可以轻松地验证优化规则的效果是否符合预期
 *
 * <p>这个方法遵循不可变对象模式，不会修改当前实例，而是返回一个新的实例
 * 如果传入的差异仓库与当前相同，则直接返回当前实例，避免不必要的对象创建
 *
 * <p>使用场景：当需要使用特定的差异仓库来验证测试结果时调用此方法
 *
 * @param diffRepos 要设置的差异仓库，用于存储和比较测试输出
 * @return 新的测试夹具实例，包含指定的差异仓库；如果差异仓库相同则返回当前实例
 */
public RelOptFixture withDiffRepos(DiffRepository diffRepos) { // 设置差异仓库并返回新的 fixture 实例
    if (diffRepos.equals(this.diffRepos)) { // 如果传入的差异仓库与当前相同
      return this; // 直接返回当前实例，避免创建新对象
    }
    return new RelOptFixture(tester, factory, diffRepos, relSupplier, // 创建新的 fixture 实例，使用新的差异仓库
        preProgram, planner, hooks, before, after, decorrelate, // 保持其他配置不变
        lateDecorrelate); // 保持延迟去相关配置不变
  }

  /**
 * 设置关系节点供应器并返回新的测试夹具实例
 *
 * <p>关系节点供应器负责提供要测试的关系节点，可以从 SQL 查询或 RelBuilder 构建
 * 通过设置不同的供应器，可以测试不同来源的关系表达式
 *
 * <p>这个方法遵循不可变对象模式，不会修改当前实例，而是返回一个新的实例
 * 如果传入的供应器与当前相同，则直接返回当前实例，避免不必要的对象创建
 *
 * <p>使用场景：当需要测试特定的关系节点时调用此方法
 *
 * @param relSupplier 要设置的关系节点供应器，提供要测试的关系节点
 * @return 新的测试夹具实例，包含指定的关系供应器；如果供应器相同则返回当前实例
 */
public RelOptFixture withRelSupplier(RelSupplier relSupplier) { // 设置关系供应器并返回新的 fixture 实例
    if (relSupplier.equals(this.relSupplier)) { // 如果传入的供应器与当前相同
      return this; // 直接返回当前实例，避免创建新对象
    }
    return new RelOptFixture(tester, factory, diffRepos, relSupplier, // 创建新的 fixture 实例，使用新的供应器
        preProgram, planner, hooks, before, after, decorrelate, // 保持其他配置不变
        lateDecorrelate); // 保持延迟去相关配置不变
  }

  /**
 * 设置 SQL 查询字符串并返回新的测试夹具实例
 *
 * <p>这是一个便捷方法，用于从 SQL 查询创建关系节点供应器
 * SQL 查询会被解析并转换为关系代数表达式，然后用于测试
 *
 * <p>这个方法是测试的入口点之一，允许开发者直接使用 SQL 进行测试
 * 例如：fixture.sql("SELECT * FROM emp WHERE deptno = 10").check()
 *
 * <p>使用场景：当需要测试特定 SQL 查询的优化效果时调用此方法
 *
 * @param sql 要测试的 SQL 查询字符串，会被解析为关系表达式
 * @return 新的测试夹具实例，包含从 SQL 创建的关系供应器
 */
public RelOptFixture sql(String sql) { // 设置 SQL 查询并返回新的 fixture 实例
    return withRelSupplier(RelSupplier.of(sql)); // 从 SQL 创建关系供应器并设置到 fixture
  }

  /**
 * 设置关系构建器函数并返回新的测试夹具实例
 *
 * <p>这是一个便捷方法，用于从 RelBuilder 函数创建关系节点供应器
 * RelBuilder 函数接收一个 RelBuilder 对象并返回构建的关系节点
 * 这种方式比 SQL 更灵活，可以直接构建复杂的关系表达式树
 *
 * <p>这个方法允许开发者以编程方式构建关系表达式，而不需要使用 SQL
 * 例如：fixture.relFn(b -> b.scan("EMP").filter(b.call(SqlStdOperatorTable.EQUAL, ...))
 *
 * <p>使用场景：当需要测试通过 RelBuilder 构建的关系表达式时调用此方法
 *
 * @param relFn 关系构建器函数，接收 RelBuilder 并返回 RelNode
 * @return 新的测试夹具实例，包含从函数创建的关系供应器
 */
RelOptFixture relFn(Function<RelBuilder, RelNode> relFn) { // 设置关系构建器函数并返回新的 fixture 实例
    return withRelSupplier(RelSupplier.of(relFn)); // 从函数创建关系供应器并设置到 fixture
  }

  /**
 * 设置优化前的转换函数并返回新的测试夹具实例
 *
 * <p>这个方法允许在主优化执行之前对关系节点进行自定义转换
 * 转换函数会被链式调用，新的转换会先执行，然后执行之前的转换
 *
 * <p>使用场景包括：
 * 1. 在优化前对关系表达式进行预处理
 * 2. 验证优化前的状态
 * 3. 添加额外的优化前检查或修改
 *
 * @param transform 转换函数，接收 fixture 和关系节点，返回转换后的关系节点
 * @return 新的测试夹具实例，包含链式的优化前转换函数
 */
public RelOptFixture withBefore( // 设置优化前的转换函数并返回新的 fixture 实例
      BiFunction<RelOptFixture, RelNode, RelNode> transform) { // 接收双参数函数，转换 fixture 和关系节点
    BiFunction<RelOptFixture, RelNode, RelNode> before0 = this.before; // 保存当前的优化前转换函数
    final BiFunction<RelOptFixture, RelNode, RelNode> before = // 创建新的链式转换函数
        (sql, r) -> transform.apply(this, before0.apply(this, r)); // 先应用新的转换，再应用原来的转换
    return new RelOptFixture(tester, factory, diffRepos, relSupplier, // 创建新的 fixture 实例，使用新的转换函数
        preProgram, planner, hooks, before, after, decorrelate, // 保持其他配置不变
        lateDecorrelate); // 保持延迟去相关配置不变
  }

  /**
 * 设置优化后的转换函数并返回新的测试夹具实例
 *
 * <p>这个方法允许在主优化执行之后对关系节点进行自定义转换
 * 转换函数会被链式调用，新的转换会在之前的转换之后执行
 *
 * <p>使用场景包括：
 * 1. 在优化后对关系表达式进行后处理
 * 2. 验证优化后的状态
 * 3. 添加额外的优化后检查或修改
 *
 * @param transform 转换函数，接收 fixture 和关系节点，返回转换后的关系节点
 * @return 新的测试夹具实例，包含链式的优化后转换函数
 */
public RelOptFixture withAfter( // 设置优化后的转换函数并返回新的 fixture 实例
      BiFunction<RelOptFixture, RelNode, RelNode> transform) { // 接收双参数函数，转换 fixture 和关系节点
    final BiFunction<RelOptFixture, RelNode, RelNode> after0 = this.after; // 保存当前的优化后转换函数
    final BiFunction<RelOptFixture, RelNode, RelNode> after = // 创建新的链式转换函数
        (sql, r) -> transform.apply(this, after0.apply(this, r)); // 先应用原来的转换，再应用新的转换
    return new RelOptFixture(tester, factory, diffRepos, relSupplier, // 创建新的 fixture 实例，使用新的转换函数
        preProgram, planner, hooks, before, after, decorrelate, // 保持其他配置不变
        lateDecorrelate); // 保持延迟去相关配置不变
  }

  /**
 * 启用动态表支持并返回新的测试夹具实例
 *
 * <p>动态表是指在运行时可以动态修改的表，支持动态添加或删除列
 * 这个方法会设置目录读取器工厂为 MockCatalogReaderDynamic，以支持动态表功能
 *
 * <p>使用场景：当需要测试动态表相关的功能时调用此方法
 *
 * @return 新的测试夹具实例，启用动态表支持
 */
public RelOptFixture withDynamicTable() { // 启用动态表支持并返回新的 fixture 实例
    return withCatalogReaderFactory(MockCatalogReaderDynamic::create); // 设置目录读取器工厂为动态目录读取器
  }

  /**
 * 设置 SQL 测试工厂并返回新的测试夹具实例
 *
 * <p>SQL 测试工厂负责创建和配置测试环境的各种组件，如验证器、转换器等
 * 通过这个方法，可以自定义测试工厂的配置，例如修改验证器行为、添加自定义规则等
 *
 * <p>这个方法遵循不可变对象模式，不会修改当前实例，而是返回一个新的实例
 * 如果转换后的工厂与当前相同，则直接返回当前实例，避免不必要的对象创建
 *
 * <p>使用场景：当需要自定义测试工厂的配置时调用此方法
 *
 * @param transform 转换函数，接收当前的测试工厂并返回转换后的测试工厂
 * @return 新的测试夹具实例，包含转换后的测试工厂；如果工厂相同则返回当前实例
 */
public RelOptFixture withFactory(UnaryOperator<SqlTestFactory> transform) { // 设置测试工厂并返回新的 fixture 实例
    final SqlTestFactory factory = transform.apply(this.factory); // 应用转换函数到当前工厂
    if (factory.equals(this.factory)) { // 如果转换后的工厂与当前相同
      return this; // 直接返回当前实例，避免创建新对象
    }
    return new RelOptFixture(tester, factory, diffRepos, relSupplier, // 创建新的 fixture 实例，使用新的工厂
        preProgram, planner, hooks, before, after, decorrelate, // 保持其他配置不变
        lateDecorrelate); // 保持延迟去相关配置不变
  }

  /**
 * 设置预处理 Hep 程序并返回新的测试夹具实例
 *
 * <p>预处理程序在主优化器执行之前运行，用于准备关系表达式树
 * Hep（启发式）计划器会按照程序中定义的规则顺序执行，不进行成本比较
 *
 * <p>这个方法遵循不可变对象模式，不会修改当前实例，而是返回一个新的实例
 * 如果传入的程序与当前相同，则直接返回当前实例，避免不必要的对象创建
 *
 * <p>使用场景：当需要在主优化之前应用特定的规则时调用此方法
 *
 * @param preProgram 要设置的预处理 Hep 程序，定义要在主优化前执行的规则
 * @return 新的测试夹具实例，包含指定的预处理程序；如果程序相同则返回当前实例
 */
public RelOptFixture withPre(HepProgram preProgram) { // 设置预处理程序并返回新的 fixture 实例
    if (preProgram.equals(this.preProgram)) { // 如果传入的程序与当前相同
      return this; // 直接返回当前实例，避免创建新对象
    }
    return new RelOptFixture(tester, factory, diffRepos, relSupplier, // 创建新的 fixture 实例，使用新的预处理程序
        preProgram, planner, hooks, before, after, decorrelate, // 保持其他配置不变
        lateDecorrelate); // 保持延迟去相关配置不变
  }

  /**
 * 设置预处理规则并返回新的测试夹具实例
 *
 * <p>这是一个便捷方法，用于从规则数组创建预处理 Hep 程序
 * 规则会按照传入的顺序添加到程序中，并在主优化之前执行
 *
 * <p>使用场景：当需要在主优化之前应用特定的规则时调用此方法
 * 例如：fixture.withPreRule(CoreRules.PROJECT_TO_CALC).check()
 *
 * @param rules 要添加到预处理程序的规则数组，按顺序执行
 * @return 新的测试夹具实例，包含包含指定规则的预处理程序
 */
public RelOptFixture withPreRule(RelOptRule... rules) { // 设置预处理规则并返回新的 fixture 实例
    final HepProgramBuilder builder = HepProgram.builder(); // 创建 Hep 程序构建器
    for (RelOptRule rule : rules) { // 遍历所有规则
      builder.addRuleInstance(rule); // 将每个规则添加到程序中
    }
    return withPre(builder.build()); // 构建程序并设置为预处理程序
  }

  /**
 * 设置优化计划器并返回新的测试夹具实例
 *
 * <p>优化计划器负责执行优化规则并生成最优执行计划
 * Calcite 支持多种计划器类型，包括 VolcanoPlanner（基于成本的优化）和 HepPlanner（基于规则的优化）
 *
 * <p>这个方法遵循不可变对象模式，不会修改当前实例，而是返回一个新的实例
 * 如果传入的计划器与当前相同，则直接返回当前实例，避免不必要的对象创建
 *
 * <p>使用场景：当需要使用特定的优化计划器时调用此方法
 *
 * @param planner 要设置的优化计划器，负责执行优化规则
 * @return 新的测试夹具实例，包含指定的优化计划器；如果计划器相同则返回当前实例
 */
public RelOptFixture withPlanner(RelOptPlanner planner) { // 设置优化计划器并返回新的 fixture 实例
    if (planner.equals(this.planner)) { // 如果传入的计划器与当前相同
      return this; // 直接返回当前实例，避免创建新对象
    }
    return new RelOptFixture(tester, factory, diffRepos, relSupplier, // 创建新的 fixture 实例，使用新的计划器
        preProgram, planner, hooks, before, after, decorrelate, // 保持其他配置不变
        lateDecorrelate); // 保持延迟去相关配置不变
  }

  /**
 * 设置 Hep 程序并返回新的测试夹具实例
 *
 * <p>这是一个便捷方法，用于从 Hep 程序创建 HepPlanner
 * HepPlanner 会按照程序中定义的规则顺序执行，不进行成本比较
 *
 * <p>使用场景：当需要使用 HepPlanner 执行特定的规则时调用此方法
 * 例如：fixture.withProgram(HepProgram.builder().addRuleInstance(rule).build()).check()
 *
 * @param program 要设置的 Hep 程序，定义要执行的规则
 * @return 新的测试夹具实例，包含使用该程序的 HepPlanner
 */
public RelOptFixture withProgram(HepProgram program) { // 设置 Hep 程序并返回新的 fixture 实例
    return withPlanner(new HepPlanner(program)); // 创建 HepPlanner 并设置为优化计划器
  }

  /**
 * 设置优化规则并返回新的测试夹具实例
 *
 * <p>这是一个便捷方法，用于从规则数组创建 HepPlanner
 * 规则会按照传入的顺序添加到程序中，并由 HepPlanner 执行
 *
 * <p>使用场景：当需要测试特定的优化规则时调用此方法
 * 例如：fixture.withRule(CoreRules.FILTER_SCAN).check()
 *
 * @param rules 要添加到 HepPlanner 的规则数组，按顺序执行
 * @return 新的测试夹具实例，包含包含指定规则的 HepPlanner
 */
public RelOptFixture withRule(RelOptRule... rules) { // 设置优化规则并返回新的 fixture 实例
    final HepProgramBuilder builder = HepProgram.builder(); // 创建 Hep 程序构建器
    for (RelOptRule rule : rules) { // 遍历所有规则
      builder.addRuleInstance(rule); // 将每个规则添加到程序中
    }
    return withProgram(builder.build()); // 构建程序并创建 HepPlanner
  }

  /**
   * 添加钩子和对应的处理函数并返回新的测试夹具实例
   *
   * <p>钩子机制允许在特定的执行点插入自定义逻辑，用于监控和调试
   * Calcite 会在运行查询之前创建线程钩子（通过调用 {@link Hook#addThread(Consumer)}），
   * 并在查询执行完成后移除钩子
   *
   * <p>这个方法遵循不可变对象模式，不会修改当前实例，而是返回一个新的实例
   * 如果添加的钩子与当前相同，则直接返回当前实例，避免不必要的对象创建
   *
   * <p>使用场景：当需要在特定执行点执行自定义逻辑时调用此方法
   * 例如：fixture.withHook(Hook.PLANNER, p -> System.out.println(p)).check()
   *
   * @param <T> 钩子处理函数的参数类型
   * @param hook 要添加的钩子，定义执行点
   * @param handler 钩子处理函数，在钩子触发时执行
   * @return 新的测试夹具实例，包含添加的钩子；如果钩子相同则返回当前实例
   */
  @SuppressWarnings({"rawtypes", "unchecked"}) // 抑制类型转换警告，因为需要将泛型转换为 Object
  public <T> RelOptFixture withHook(Hook hook, Consumer<T> handler) { // 添加钩子和处理函数并返回新的 fixture 实例
      final ImmutableMap<Hook, Consumer<Object>> hooks = // 创建新的钩子映射
          FlatLists.append((Map) this.hooks, hook, (Consumer) handler); // 将新钩子添加到映射中
      if (hooks.equals(this.hooks)) { // 如果新的钩子映射与当前相同
        return this; // 直接返回当前实例，避免创建新对象
      }
      return new RelOptFixture(tester, factory, diffRepos, relSupplier, // 创建新的 fixture 实例，使用新的钩子映射
          preProgram, planner, hooks, before, after, decorrelate, // 保持其他配置不变
          lateDecorrelate); // 保持延迟去相关配置不变
    }
  /**
 * 设置钩子属性值并返回新的测试夹具实例
 *
 * <p>这是一个便捷方法，用于设置钩子的属性值
 * 属性值会被包装成处理函数，在钩子触发时设置该值
 *
 * <p>使用场景：当需要设置特定的属性值时调用此方法
 * 例如：fixture.withProperty(Hook.REL_BUILDER_SIMPLIFY, true).check()
 *
 * @param <V> 属性值的类型
 * @param hook 要设置的钩子
 * @param value 要设置的属性值
 * @return 新的测试夹具实例，包含设置的属性值
 */
public <V> RelOptFixture withProperty(Hook hook, V value) { // 设置钩子属性值并返回新的 fixture 实例
    return withHook(hook, Hook.propertyJ(value)); // 将属性值包装成处理函数并添加到钩子
  }

  /**
 * 设置 RelBuilder 简化标志并返回新的测试夹具实例
 *
 * <p>RelBuilder 简化功能会在构建关系表达式时自动应用简化规则
 * 例如：消除冗余的投影、合并连续的过滤等
 *
 * <p>使用场景：当需要启用或禁用 RelBuilder 的简化功能时调用此方法
 *
 * @param simplify 是否启用 RelBuilder 简化功能
 * @return 新的测试夹具实例，包含设置的简化标志
 */
public RelOptFixture withRelBuilderSimplify(boolean simplify) { // 设置 RelBuilder 简化标志并返回新的 fixture 实例
    return withProperty(Hook.REL_BUILDER_SIMPLIFY, simplify); // 设置 REL_BUILDER_SIMPLIFY 钩子的属性值
  }

  /**
 * 设置子查询展开标志并返回新的测试夹具实例
 *
 * <p>子查询展开是指将子查询转换为连接或半连接等更高效的形式
 * 禁用展开时，子查询会被转换为相关子查询
 *
 * <p>使用场景：当需要启用或禁用子查询展开时调用此方法
 *
 * @param expand 是否启用子查询展开
 * @return 新的测试夹具实例，包含设置的展开标志
 */
public RelOptFixture withExpand(final boolean expand) { // 设置子查询展开标志并返回新的 fixture 实例
    return withConfig(c -> c.withExpand(expand)); // 设置 SqlToRelConverter 的展开配置
  }

  /**
 * 设置 IN 子查询阈值并返回新的测试夹具实例
 *
 * <p>IN 子查询阈值决定了 IN 子查询何时会被转换为连接
 * 如果子查询的结果集大小小于阈值，会被转换为 IN 列表
 * 否则会被转换为半连接
 *
 * <p>使用场景：当需要调整 IN 子查询的转换策略时调用此方法
 *
 * @param inSubQueryThreshold IN 子查询的阈值，决定转换策略
 * @return 新的测试夹具实例，包含设置的阈值
 */
public RelOptFixture withInSubQueryThreshold(final int inSubQueryThreshold) { // 设置 IN 子查询阈值并返回新的 fixture 实例
    return withConfig(c -> c.withInSubQueryThreshold(inSubQueryThreshold)); // 设置 SqlToRelConverter 的 IN 子查询阈值
  }

  /**
 * 设置 SQL 到关系转换器配置并返回新的测试夹具实例
 *
 * <p>SQL 到关系转换器配置定义了 SQL 解析和转换的各种参数
 * 包括子查询展开、字段修剪、去相关等
 *
 * <p>使用场景：当需要自定义 SQL 到关系转换器的配置时调用此方法
 *
 * @param transform 转换函数，接收当前的配置并返回转换后的配置
 * @return 新的测试夹具实例，包含转换后的配置
 */
public RelOptFixture withConfig( // 设置 SQL 到关系转换器配置并返回新的 fixture 实例
      UnaryOperator<SqlToRelConverter.Config> transform) { // 接收配置转换函数
    return withFactory(f -> f.withSqlToRelConfig(transform)); // 应用转换函数到测试工厂的 SqlToRelConverter 配置
  }

  /**
 * 设置 RelBuilder 配置并返回新的测试夹具实例
 *
 * <p>RelBuilder 配置定义了关系构建器的各种参数
 * 包括简化、修剪、投影消除等
 *
 * <p>使用场景：当需要自定义 RelBuilder 的配置时调用此方法
 *
 * @param transform 转换函数，接收当前的配置并返回转换后的配置
 * @return 新的测试夹具实例，包含转换后的配置
 */
public RelOptFixture withRelBuilderConfig( // 设置 RelBuilder 配置并返回新的 fixture 实例
      UnaryOperator<RelBuilder.Config> transform) { // 接收配置转换函数
    return withConfig(c -> c.addRelBuilderConfigTransform(transform)); // 将转换函数添加到 SqlToRelConverter 的配置中
  }

  /**
 * 设置延迟去相关标志并返回新的测试夹具实例
 *
 * <p>延迟去相关是指在优化完成后进行去相关操作
 * 与普通去相关不同，延迟去相关可以在优化后更好地利用优化规则
 *
 * <p>这个方法遵循不可变对象模式，不会修改当前实例，而是返回一个新的实例
 * 如果传入的标志与当前相同，则直接返回当前实例，避免不必要的对象创建
 *
 * <p>使用场景：当需要在优化后进行去相关时调用此方法
 *
 * @param lateDecorrelate 是否启用延迟去相关
 * @return 新的测试夹具实例，包含设置的延迟去相关标志；如果标志相同则返回当前实例
 */
public RelOptFixture withLateDecorrelate(final boolean lateDecorrelate) { // 设置延迟去相关标志并返回新的 fixture 实例
    if (lateDecorrelate == this.lateDecorrelate) { // 如果传入的标志与当前相同
      return this; // 直接返回当前实例，避免创建新对象
    }
    return new RelOptFixture(tester, factory, diffRepos, relSupplier, // 创建新的 fixture 实例，使用新的延迟去相关标志
        preProgram, planner, hooks, before, after, decorrelate, // 保持其他配置不变
        lateDecorrelate); // 使用新的延迟去相关标志
  }

  /**
 * 设置去相关标志并返回新的测试夹具实例
 *
 * <p>去相关是指将相关子查询转换为不相关的形式，通过添加连接条件来实现
 * 这可以提高优化器的优化空间，因为不相关的表达式更容易优化
 *
 * <p>这个方法遵循不可变对象模式，不会修改当前实例，而是返回一个新的实例
 * 如果传入的标志与当前相同，则直接返回当前实例，避免不必要的对象创建
 *
 * <p>使用场景：当需要启用或禁用去相关功能时调用此方法
 *
 * @param decorrelate 是否启用去相关
 * @return 新的测试夹具实例，包含设置的去相关标志；如果标志相同则返回当前实例
 */
public RelOptFixture withDecorrelate(final boolean decorrelate) { // 设置去相关标志并返回新的 fixture 实例
    if (decorrelate == this.decorrelate) { // 如果传入的标志与当前相同
      return this; // 直接返回当前实例，避免创建新对象
    }
    return new RelOptFixture(tester, factory, diffRepos, relSupplier, // 创建新的 fixture 实例，使用新的去相关标志
        preProgram, planner, hooks, before, after, decorrelate, // 使用新的去相关标志
        lateDecorrelate); // 保持延迟去相关配置不变
  }

  /**
 * 设置字段修剪标志并返回新的测试夹具实例
 *
 * <p>字段修剪是指移除未使用的字段，以减少数据传输和计算开销
 * 启用后，只有查询中实际使用的字段会被保留
 *
 * <p>使用场景：当需要启用或禁用字段修剪时调用此方法
 *
 * @param trim 是否启用字段修剪
 * @return 新的测试夹具实例，包含设置的修剪标志
 */
public RelOptFixture withTrim(final boolean trim) { // 设置字段修剪标志并返回新的 fixture 实例
    return withConfig(c -> c.withTrimUnusedFields(trim)); // 设置 SqlToRelConverter 的字段修剪配置
  }

  /**
 * 设置目录读取器工厂并返回新的测试夹具实例
 *
 * <p>目录读取器负责读取和解析数据库目录信息，包括表、列、视图等
 * 通过设置不同的工厂，可以自定义目录的读取方式
 *
 * <p>使用场景：当需要使用自定义的目录读取器时调用此方法
 *
 * @param factory 目录读取器工厂，用于创建目录读取器
 * @return 新的测试夹具实例，包含设置的目录读取器工厂
 */
public RelOptFixture withCatalogReaderFactory( // 设置目录读取器工厂并返回新的 fixture 实例
      SqlTestFactory.CatalogReaderFactory factory) { // 接收目录读取器工厂
    return withFactory(f -> f.withCatalogReader(factory)); // 设置测试工厂的目录读取器
  }

  /**
 * 设置 SQL 合规性并返回新的测试夹具实例
 *
 * <p>SQL 合规性定义了 SQL 方言的符合程度，例如支持哪些语法特性
 * 如果合规性允许几何类型，会自动添加空间操作符表
 *
 * <p>使用场景：当需要测试不同的 SQL 方言时调用此方法
 *
 * @param conformance SQL 合规性，定义 SQL 方言的符合程度
 * @return 新的测试夹具实例，包含设置的合规性
 */
public RelOptFixture withConformance(final SqlConformance conformance) { // 设置 SQL 合规性并返回新的 fixture 实例
    return withFactory(f -> // 配置测试工厂
        f.withValidatorConfig(c -> c.withConformance(conformance)) // 设置验证器的合规性
            .withOperatorTable(t -> // 配置操作符表
                conformance.allowGeometry() // 如果允许几何类型
                    ? SqlOperatorTables.chain(t, // 将原始操作符表与空间操作符表链接
                    SqlOperatorTables.spatialInstance()) // 添加空间操作符表
                    : t)); // 否则保持原操作符表
  }

  /**
 * 设置计划器上下文并返回新的测试夹具实例
 *
 * <p>计划器上下文提供运行时信息，例如当前的统计信息、配置参数等
 * 通过设置不同的上下文，可以自定义优化器的行为
 *
 * <p>使用场景：当需要自定义计划器上下文时调用此方法
 *
 * @param transform 转换函数，接收当前的上下文并返回转换后的上下文
 * @return 新的测试夹具实例，包含转换后的上下文
 */
public RelOptFixture withContext(final UnaryOperator<Context> transform) { // 设置计划器上下文并返回新的 fixture 实例
    return withFactory(f -> f.withPlannerContext(transform)); // 设置测试工厂的计划器上下文
  }

  /**
 * 将 SQL 或 RelBuilder 函数转换为关系节点
 *
 * <p>这个方法使用关系节点供应器来生成关系节点
 * 供应器可能是从 SQL 解析，也可能是从 RelBuilder 构建
 *
 * <p>这是测试的核心方法之一，用于获取要测试的关系表达式
 *
 * @return 生成的关系节点，代表关系代数表达式
 */
public RelNode toRel() { // 将 SQL 或 RelBuilder 函数转换为关系节点
    return relSupplier.apply(this); // 应用关系供应器，传入当前 fixture 作为参数
  }

  /**
   * 检查 SQL 语句在执行给定规则前后的计划
   *
   * <p>这个方法会执行以下步骤：
   * 1. 生成初始关系节点
   * 2. 应用预处理程序（如果设置了）
   * 3. 应用优化前转换
   * 4. 执行优化器
   * 5. 应用延迟去相关（如果启用）
   * 6. 应用优化后转换
   * 7. 验证优化前后的计划
   *
   * <p>使用场景：当需要验证优化规则的效果时调用此方法
   * 例如：fixture.sql("SELECT * FROM emp").withRule(rule).check()
   */
  public void check() { // 检查 SQL 语句在执行规则前后的计划
      check(false); // 调用内部检查方法，不要求计划不变
    }
  /**
   * 检查计划在执行给定计划器前后是否保持不变
   *
   * <p>这个方法用于验证规则不应该触发的情况
   * 如果计划发生了变化，会抛出断言错误
   *
   * <p>使用场景：当需要验证规则不应该触发时调用此方法
   * 例如：fixture.sql("SELECT * FROM emp").withRule(rule).checkUnchanged()
   */
  public void checkUnchanged() { // 检查计划在执行计划器前后是否保持不变
      check(true); // 调用内部检查方法，要求计划不变
    }
  /**
 * 内部检查方法，执行计划检查
 *
 * <p>这个方法会：
 * 1. 创建 Closer 来管理资源
 * 2. 注册所有钩子到当前线程
 * 3. 执行计划检查
 * 4. 自动清理钩子
 *
 * @param unchanged 是否要求计划不变
 */
private void check(boolean unchanged) { // 内部检查方法，执行计划检查
    try (Closer closer = new Closer()) { // 创建 Closer 来管理资源，确保钩子被正确清理
      for (Map.Entry<Hook, Consumer<Object>> entry : hooks.entrySet()) { // 遍历所有钩子
        closer.add(entry.getKey().addThread(entry.getValue())); // 将钩子添加到当前线程，并注册到 Closer
      }
      checkPlanning(unchanged); // 执行计划检查
    }
  }

  /**
   * 检查给定关系节点供应器在执行规则前后的计划
   *
   * <p>这个方法会执行以下步骤：
   * 1. 生成初始关系节点
   * 2. 设置元数据提供者
   * 3. 应用预处理程序（如果设置了）
   * 4. 应用优化前转换
   * 5. 记录优化前的计划
   * 6. 执行优化器
   * 7. 应用延迟去相关（如果启用）
   * 8. 应用优化后转换
   * 9. 验证优化后的计划
   *
   * <p>为了便于调试，这个方法使用多个 final 变量来保存中间状态
   * 而不是使用单个可变的 RelNode 变量
   *
   * @param unchanged 是否要求规则没有效果（计划不变）
   */
  private void checkPlanning(boolean unchanged) { // 检查关系节点供应器在执行规则前后的计划
      final RelNode relInitial = toRel(); // 生成初始关系节点
  
      assertNotNull(relInitial); // 验证初始关系节点不为 null
      List<RelMetadataProvider> list = new ArrayList<>(); // 创建元数据提供者列表
      list.add(DefaultRelMetadataProvider.INSTANCE); // 添加默认元数据提供者
      RelMetadataProvider plannerChain = // 创建链式元数据提供者
          ChainedRelMetadataProvider.of(list); // 组合所有元数据提供者
      final RelOptCluster cluster = relInitial.getCluster(); // 获取关系优化集群
      cluster.setMetadataProvider(plannerChain); // 设置集群的元数据提供者
  
      // Rather than a single mutable 'RelNode r', this method uses lots of
      // final variables (relInitial, r1, relBefore, and so forth) so that the
      // intermediate states of planning are visible in the debugger.
      // 为了便于调试，这个方法使用多个 final 变量来保存中间状态
      // 而不是使用单个可变的 RelNode 变量
      final RelNode r1; // 声明第一个中间关系节点
      if (preProgram == null) { // 如果没有设置预处理程序
        r1 = relInitial; // 直接使用初始关系节点
      } else { // 如果设置了预处理程序
        HepPlanner prePlanner = new HepPlanner(preProgram); // 创建 Hep 计划器
        prePlanner.setRoot(relInitial); // 设置计划器的根节点
        r1 = prePlanner.findBestExp(); // 执行预处理程序，获取结果
      }
      final RelNode relBefore = before.apply(this, r1); // 应用优化前转换
      assertThat(relBefore, notNullValue()); // 验证优化前的关系节点不为 null
  
      final String planBefore = NL + relToString(relBefore); // 将优化前的计划转换为字符串
      final DiffRepository diffRepos = diffRepos(); // 获取差异仓库
      diffRepos.assertEquals("planBefore", "${planBefore}", planBefore); // 验证优化前的计划与预期一致
      assertThat(relBefore, relIsValid()); // 验证优化前的关系节点有效
  
      final RelNode r2; // 声明第二个中间关系节点
      if (planner instanceof VolcanoPlanner) { // 如果是 Volcano 计划器
        r2 = // 转换特征集合
            planner.changeTraits(relBefore, // 将关系节点的调用约定转换为可枚举约定
                relBefore.getTraitSet().replace(EnumerableConvention.INSTANCE));
      } else { // 如果不是 Volcano 计划器
        r2 = relBefore; // 直接使用优化前的关系节点
      }
      planner.setRoot(r2); // 设置计划器的根节点
      final RelNode r3 = planner.findBestExp(); // 执行优化器，获取最优计划
  
      final RelNode r4; // 声明第三个中间关系节点
      if (lateDecorrelate) { // 如果启用了延迟去相关
        final String planMid = NL + relToString(r3); // 将中间计划转换为字符串
        diffRepos.assertEquals("planMid", "${planMid}", planMid); // 验证中间计划与预期一致
        assertThat(r3, relIsValid()); // 验证中间关系节点有效
        final RelBuilder relBuilder = // 创建关系构建器
            RelFactories.LOGICAL_BUILDER.create(cluster, null); // 使用逻辑构建器工厂
        r4 = RelDecorrelator.decorrelateQuery(r3, relBuilder); // 执行去相关操作
      } else { // 如果没有启用延迟去相关
        r4 = r3; // 直接使用优化后的关系节点
      }
      final RelNode relAfter = after.apply(this, r4); // 应用优化后转换
      final String planAfter = NL + relToString(relAfter); // 将优化后的计划转换为字符串
      if (unchanged) { // 如果要求计划不变
        final String expandedPlanAfter = diffRepos.expand("planAfter", "${planAfter}"); // 展开计划字符串
        if (!"${planAfter}".equals(expandedPlanAfter)) { // 如果展开后的字符串不是占位符
          throw new AssertionError("Expected planAfter must not be present when using unchanged=true " // 抛出断言错误
              + "or calling checkUnchanged."); // 提示应该使用 unchanged=true 或 checkUnchanged
        }
        assertThat(planAfter, is(planBefore)); // 验证优化后的计划与优化前相同
      } else { // 如果不要求计划不变
        diffRepos.assertEquals("planAfter", "${planAfter}", planAfter); // 验证优化后的计划与预期一致
        if (planBefore.equals(planAfter)) { // 如果优化前后计划相同
          throw new AssertionError("Expected plan before and after is the same.\n" // 抛出断言错误
              + "You must use unchanged=true or call checkUnchanged"); // 提示应该使用 unchanged=true 或 checkUnchanged
        }
      }
      assertThat(relAfter, relIsValid()); // 验证优化后的关系节点有效
    }
  /**
 * 将关系节点转换为字符串，屏蔽 Apple Silicon (arm64) 和 AMD/Intel (x86_64) 之间的浮点值差异
 *
 * <p>这个差异出现在 {@code RelOptRulesTest#testSpatialReduce} 和
 * {@code testSpatialContainsPoint} 生成的 POLYGON 值中
 * 可能是 JTS {@code Geometry.buffer} 方法的一个 bug
 *
 * <p>通过替换特定的浮点值，可以确保在不同架构上测试结果一致
 *
 * @param r 要转换的关系节点
 * @return 关系节点的字符串表示，已屏蔽架构差异
 */
private static String relToString(RelNode r) { // 将关系节点转换为字符串，屏蔽架构差异
    final String s = RelOptUtil.toString(r); // 使用 RelOptUtil 将关系节点转换为字符串
    return s.replace("-0.9238795325112868", "-0.9238795325112867") // 替换第一个浮点差异
        .replace("4.456722804932279", "4.45672280493228") // 替换第二个浮点差异
        .replace("0.3826834323650896", "0.3826834323650897"); // 替换第三个浮点差异
  }

  /**
 * 设置 Volcano 计划器并返回新的测试夹具实例
 *
 * <p>Volcano 计划器是 Calcite 的基于成本的优化器，使用动态规划算法
 * 它会探索多个可能的计划，并选择成本最低的计划
 *
 * <p>这个方法会注册默认的优化规则，但不包括规则转换和强制规则
 *
 * @param topDown 是否使用自顶向下的优化策略
 * @return 新的测试夹具实例，包含 Volcano 计划器
 */
public RelOptFixture withVolcanoPlanner(boolean topDown) { // 设置 Volcano 计划器并返回新的 fixture 实例
    return withVolcanoPlanner(topDown, p -> // 调用重载方法，传入初始化函数
        RelOptUtil.registerDefaultRules(p, false, false)); // 注册默认规则，不包括规则转换和强制规则
  }

  /**
 * 设置 Volcano 计划器并返回新的测试夹具实例
 *
 * <p>Volcano 计划器是 Calcite 的基于成本的优化器，使用动态规划算法
 * 它会探索多个可能的计划，并选择成本最低的计划
 *
 * <p>这个方法允许自定义计划器的初始化，例如添加特定的规则
 *
 * @param topDown 是否使用自顶向下的优化策略
 * @param init 计划器初始化函数，用于自定义计划器配置
 * @return 新的测试夹具实例，包含 Volcano 计划器
 */
public RelOptFixture withVolcanoPlanner(boolean topDown, // 设置 Volcano 计划器并返回新的 fixture 实例
      Consumer<VolcanoPlanner> init) { // 接收计划器初始化函数
    final VolcanoPlanner planner = new VolcanoPlanner(); // 创建 Volcano 计划器
    planner.setTopDownOpt(topDown); // 设置优化策略（自顶向下或自底向上）
    planner.addRelTraitDef(ConventionTraitDef.INSTANCE); // 添加调用约定特征定义
    init.accept(planner); // 应用初始化函数
    return withPlanner(planner) // 设置计划器
        .withDecorrelate(true) // 启用去相关
        .withFactory(f -> // 配置测试工厂
            f.withCluster(cluster -> // 配置集群
                RelOptCluster.create(planner, cluster.getRexBuilder()))); // 使用新的计划器创建集群
  }

  /**
 * 启用子查询转换规则并返回新的测试夹具实例
 *
 * <p>这个方法会禁用子查询展开，并启用将子查询转换为相关子查询的规则
 * 包括：
 * 1. PROJECT_SUB_QUERY_TO_CORRELATE：将投影中的子查询转换为相关子查询
 * 2. FILTER_SUB_QUERY_TO_CORRELATE：将过滤中的子查询转换为相关子查询
 * 3. JOIN_SUB_QUERY_TO_CORRELATE：将连接中的子查询转换为相关子查询
 *
 * <p>使用场景：当需要测试子查询转换规则时调用此方法
 *
 * @return 新的测试夹具实例，启用了子查询转换规则
 */
public RelOptFixture withSubQueryRules() { // 启用子查询转换规则并返回新的 fixture 实例
    return withExpand(false) // 禁用子查询展开
        .withRule(CoreRules.PROJECT_SUB_QUERY_TO_CORRELATE, // 添加投影子查询转换规则
            CoreRules.FILTER_SUB_QUERY_TO_CORRELATE, // 添加过滤子查询转换规则
            CoreRules.JOIN_SUB_QUERY_TO_CORRELATE); // 添加连接子查询转换规则
  }

  /**
   * 返回差异仓库，检查它不为 null
   *
   * <p>差异仓库用于存储和比较测试的预期输出与实际输出
   * 支持测试结果的版本控制，可以方便地验证优化规则的效果
   *
   * <p>虽然差异仓库允许为 null（因为有些测试不使用差异仓库），
   * 但这个方法会检查它不为 null，如果为 null 会抛出异常
   *
   * @return 差异仓库，用于存储和比较测试输出
   * @throws NullPointerException 如果差异仓库为 null
   */
  public DiffRepository diffRepos() { // 返回差异仓库，检查它不为 null
      return DiffRepository.castNonNull(diffRepos); // 将差异仓库转换为非空类型，如果为 null 则抛出异常
    }} // RelOptFixture 类结束
