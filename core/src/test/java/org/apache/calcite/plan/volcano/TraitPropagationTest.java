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
// Apache许可证声明，指定代码的使用权限和限制条件
package org.apache.calcite.plan.volcano; // 声明包名，该测试类位于Volcano计划器包中

import org.apache.calcite.adapter.java.JavaTypeFactory; // Java类型工厂，用于创建Java类型
import org.apache.calcite.config.CalciteSystemProperty; // Calcite系统属性配置
import org.apache.calcite.jdbc.CalcitePrepare; // Calcite准备接口，用于SQL准备和编译
import org.apache.calcite.plan.Convention; // 调用约定trait，定义RelNode的物理实现方式
import org.apache.calcite.plan.ConventionTraitDef; // 调用约定trait定义
import org.apache.calcite.plan.RelOptAbstractTable; // 抽象优化表
import org.apache.calcite.plan.RelOptCluster; // 优化集群，包含计划器、RexBuilder等共享对象
import org.apache.calcite.plan.RelOptCost; // 优化代价接口，用于衡量执行计划的成本
import org.apache.calcite.plan.RelOptPlanner; // 优化计划器接口
import org.apache.calcite.plan.RelOptRule; // 优化规则接口
import org.apache.calcite.plan.RelOptRuleCall; // 优化规则调用上下文
import org.apache.calcite.plan.RelOptSchema; // 优化模式接口
import org.apache.calcite.plan.RelOptUtil; // 优化工具类，提供计划操作和格式化功能
import org.apache.calcite.plan.RelRule; // 基础规则类
import org.apache.calcite.plan.RelTrait; // 关系trait接口
import org.apache.calcite.plan.RelTraitSet; // 关系trait集合
import org.apache.calcite.plan.volcano.AbstractConverter.ExpandConversionRule; // 抽象转换器的扩展转换规则
import org.apache.calcite.prepare.CalciteCatalogReader; // Calcite目录读取器
import org.apache.calcite.rel.AbstractRelNode; // 抽象关系节点
import org.apache.calcite.rel.RelCollation; // 排序collation（排序属性）
import org.apache.calcite.rel.RelCollationTraitDef; // 排序collation trait定义
import org.apache.calcite.rel.RelCollations; // 排序collation工具类
import org.apache.calcite.rel.RelFieldCollation; // 字段排序规则
import org.apache.calcite.rel.RelNode; // 关系表达式节点接口
import org.apache.calcite.rel.convert.ConverterRule; // 转换规则基类
import org.apache.calcite.rel.core.Aggregate; // 聚合操作节点
import org.apache.calcite.rel.core.AggregateCall; // 聚合调用
import org.apache.calcite.rel.core.Project; // 投影操作节点
import org.apache.calcite.rel.core.Sort; // 排序操作节点
import org.apache.calcite.rel.logical.LogicalAggregate; // 逻辑聚合节点
import org.apache.calcite.rel.logical.LogicalProject; // 逻辑投影节点
import org.apache.calcite.rel.logical.LogicalTableScan; // 逻辑表扫描节点
import org.apache.calcite.rel.metadata.RelMdCollation; // 排序collation元数据提供者
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 元数据查询接口
import org.apache.calcite.rel.rules.CoreRules; // 核心规则集合
import org.apache.calcite.rel.type.RelDataType; // 关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 关系数据类型工厂
import org.apache.calcite.rex.RexBuilder; // 行表达式构建器
import org.apache.calcite.rex.RexNode; // 行表达式节点
import org.apache.calcite.schema.SchemaPlus; // Schema包装类，提供额外功能
import org.apache.calcite.schema.Statistic; // 表统计信息
import org.apache.calcite.schema.Statistics; // 统计信息工具类
import org.apache.calcite.schema.Table; // 表接口
import org.apache.calcite.schema.impl.AbstractTable; // 抽象表实现
import org.apache.calcite.server.CalciteServerStatement; // Calcite服务器语句
import org.apache.calcite.sql.SqlExplainFormat; // SQL解释格式
import org.apache.calcite.sql.SqlExplainLevel; // SQL解释详细程度
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // SQL标准操作符表
import org.apache.calcite.sql.type.SqlTypeName; // SQL类型名称枚举
import org.apache.calcite.tools.FrameworkConfig; // 框架配置
import org.apache.calcite.tools.Frameworks; // 框架工具类
import org.apache.calcite.tools.RuleSet; // 规则集合接口
import org.apache.calcite.tools.RuleSets; // 规则集合工具类
import org.apache.calcite.util.ImmutableBitSet; // 不可变位集合

import com.google.common.collect.ImmutableList; // Guava不可变列表
import com.google.common.collect.ImmutableSet; // Guava不可变集合

import org.checkerframework.checker.nullness.qual.Nullable; // 可空性注解
import org.immutables.value.Value; // 不可变值注解
import org.junit.jupiter.api.Test; // JUnit测试注解

import java.sql.Connection; // JDBC连接接口
import java.sql.DriverManager; // JDBC驱动管理器
import java.util.Collections; // Java集合工具类
import java.util.List; // Java列表接口
import java.util.Properties; // Java属性类

import static org.junit.jupiter.api.Assertions.assertEquals; // 断言工具

/**
 * Tests that determine whether trait propagation work in Volcano Planner.
 */
// 测试类：验证Volcano计划器中trait（特征）传播机制是否正常工作
// Trait传播是指RelNode的属性（如排序、调用约定等）在计划优化过程中如何从子节点传播到父节点
class TraitPropagationTest { // Trait传播测试类
  static final Convention PHYSICAL = // 定义物理调用约定trait，表示物理执行计划
      new Convention.Impl("PHYSICAL", Phys.class); // 创建Convention实现，名称为"PHYSICAL"，关联Phys接口
  static final RelCollation COLLATION = // 定义排序collation trait，表示数据按第0列升序排列
      RelCollations.of( // 创建排序collation
          new RelFieldCollation(0, // 第0列（第一列）
              RelFieldCollation.Direction.ASCENDING, // 升序方向
              RelFieldCollation.NullDirection.FIRST)); // 空值排在最前面

  static final RuleSet RULES = // 定义规则集合，包含所有转换规则
      RuleSets.ofList( // 创建规则列表
          PhysAggRule.INSTANCE, // 物理聚合规则实例
          PhysProjRule.INSTANCE, // 物理投影规则实例
          PhysTableRule.INSTANCE, // 物理表扫描规则实例
          PhysSortRule.INSTANCE, // 物理排序规则实例
          CoreRules.SORT_REMOVE, // 核心规则：移除冗余排序
          ExpandConversionRule.INSTANCE); // 扩展转换规则实例

  @Test void testOne() throws Exception { // 测试方法：验证trait传播功能是否正常工作
    RelNode planned = run(new PropAction(), RULES); // 运行PropAction并应用规则集合，生成优化后的计划
    if (CalciteSystemProperty.DEBUG.value()) { // 如果开启了调试模式
      System.out.println( // 打印逻辑计划
          RelOptUtil.dumpPlan("LOGICAL PLAN", planned, SqlExplainFormat.TEXT, // 使用文本格式输出
              SqlExplainLevel.ALL_ATTRIBUTES)); // 显示所有属性
    }
    final RelMetadataQuery mq = planned.getCluster().getMetadataQuery(); // 获取元数据查询对象
    assertEquals(3, 0, mq.getCumulativeCost(planned).getRows(), // 断言：验证排序trait是否被正确传播（期望行数为3）
        "Sortedness was not propagated"); // 如果失败，提示排序未被传播
  }

  /**
   * Materialized anonymous class for simplicity.
   */
  // PropAction类：用于构建逻辑关系表达式树的匿名类
  private static class PropAction { // PropAction：计划构建动作类
    public RelNode apply(RelOptCluster cluster, RelOptSchema relOptSchema, // apply方法：构建逻辑关系表达式树
        SchemaPlus rootSchema) { // 参数：优化集群、优化模式、根schema
      final RelDataTypeFactory typeFactory = cluster.getTypeFactory(); // 获取类型工厂
      final RexBuilder rexBuilder = cluster.getRexBuilder(); // 获取行表达式构建器
      final RelOptPlanner planner = cluster.getPlanner(); // 获取优化计划器

      final RelDataType stringType = typeFactory.createJavaType(String.class); // 创建String类型
      final RelDataType integerType = typeFactory.createJavaType(Integer.class); // 创建Integer类型
      final RelDataType sqlBigInt = // 创建SQL BIGINT类型
          typeFactory.createSqlType(SqlTypeName.BIGINT);

      // SELECT * from T;
      // 创建表对象，包含s(String)和i(Integer)两列
      final Table table = new AbstractTable() { // 抽象表实现
        public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 获取行类型
          return typeFactory.builder() // 构建类型
              .add("s", stringType) // 添加s列，String类型
              .add("i", integerType).build(); // 添加i列，Integer类型
        }

        @Override public Statistic getStatistic() { // 获取统计信息
          return Statistics.of(100d, ImmutableList.of(), // 统计信息：100行，无唯一键，有排序collation
              ImmutableList.of(COLLATION)); // 表数据按第0列升序排列
        }
      };

      final RelOptAbstractTable t1 = // 创建优化表对象
          new RelOptAbstractTable(relOptSchema, "t1", // 表名为t1
              table.getRowType(typeFactory)) { // 使用表的行类型
            @Override public <T> T unwrap(Class<T> clazz) { // 解包方法，用于获取底层表对象
              return clazz.isInstance(table) // 如果请求的类型匹配表类型
                  ? clazz.cast(table) // 返回转换后的表对象
                  : super.unwrap(clazz); // 否则调用父类方法
            }
          };

      final RelNode rt1 = LogicalTableScan.create(cluster, t1, ImmutableList.of()); // 创建逻辑表扫描节点

      // project s column
      // 创建投影节点，选择s和i两列
      RelNode project = // 投影节点
          LogicalProject.create(rt1, ImmutableList.of(), // 输入为表扫描，无hint
              ImmutableList.of((RexNode) rexBuilder.makeInputRef(stringType, 0), // 投影第0列(s)
                  rexBuilder.makeInputRef(integerType, 1)), // 投影第1列(i)
              typeFactory.builder().add("s", stringType).add("i", integerType) // 输出行类型
                  .build(),
              ImmutableSet.of()); // 无特殊标记

      // aggregate on s, count
      // 创建聚合调用：按s列分组，计算count(i)
      AggregateCall aggCall = // 聚合调用
          AggregateCall.create(SqlStdOperatorTable.COUNT, // 使用COUNT函数
              false, false, false, ImmutableList.of(), // 不去重、忽略null、近似聚合
              Collections.singletonList(1), -1, // 聚合参数为第1列(i)
              null, RelCollations.EMPTY, sqlBigInt, "cnt"); // 聚合结果类型为BIGINT，别名为cnt
      RelNode agg = // 创建逻辑聚合节点
          new LogicalAggregate(cluster, // 集群
              cluster.traitSetOf(Convention.NONE), ImmutableList.of(), project, // trait为NONE，无hint，输入为project
              ImmutableBitSet.of(0), null, Collections.singletonList(aggCall)); // 按第0列(s)分组，无groupSets，包含count聚合

      final RelNode rootRel = agg; // 根关系节点为聚合节点

      RelOptUtil.dumpPlan("LOGICAL PLAN", rootRel, SqlExplainFormat.TEXT, // 打印逻辑计划摘要
          SqlExplainLevel.DIGEST_ATTRIBUTES); // 显示摘要属性

      RelTraitSet desiredTraits = rootRel.getTraitSet().replace(PHYSICAL); // 设置期望的trait集合：替换为物理调用约定
      final RelNode rootRel2 = planner.changeTraits(rootRel, desiredTraits); // 改变根节点的trait集合
      planner.setRoot(rootRel2); // 设置计划器的根节点
      return planner.findBestExp(); // 查找最佳执行计划
    }
  }

  // RULES

  /** Rule for PhysAgg. */
  // PhysAggRule：将逻辑聚合节点转换为物理聚合节点的规则
  public static class PhysAggRule extends RelRule<PhysAggRule.Config> { // 物理聚合规则，继承自RelRule
    static final PhysAggRule INSTANCE = ImmutablePhysAggRuleConfig.builder() // 规则单例实例
        .build() // 构建配置
        .withOperandSupplier(b -> // 设置操作数提供者
            b.operand(LogicalAggregate.class).anyInputs()) // 匹配逻辑聚合节点，输入任意
        .withDescription("PhysAgg") // 设置规则描述
        .as(Config.class) // 转换为Config类型
        .toRule(); // 转换为规则

    PhysAggRule(Config config) { // 构造函数
      super(config); // 调用父类构造函数
    }

    @Override public void onMatch(RelOptRuleCall call) { // 规则匹配时的处理方法
      RelTraitSet empty = call.getPlanner().emptyTraitSet(); // 获取空的trait集合
      LogicalAggregate rel = call.rel(0); // 获取逻辑聚合节点
      assert rel.getGroupSet().cardinality() == 1; // 断言：只有一个分组列
      int aggIndex = rel.getGroupSet().iterator().next(); // 获取分组列的索引
      RelTrait collation = // 创建排序collation trait
          RelCollations.of( // 按分组列升序排列
              new RelFieldCollation(aggIndex, // 分组列索引
                  RelFieldCollation.Direction.ASCENDING, // 升序
                  RelFieldCollation.NullDirection.FIRST)); // 空值在前
      RelTraitSet desiredTraits = empty.replace(PHYSICAL).replace(collation); // 设置期望的trait：物理调用约定+排序
      RelNode convertedInput = convert(rel.getInput(), desiredTraits); // 转换输入节点为期望的trait
      call.transformTo( // 转换为物理聚合节点
          new PhysAgg(rel.getCluster(), empty.replace(PHYSICAL), // 创建物理聚合节点，trait为物理调用约定
              convertedInput, rel.getGroupSet(), // 输入节点、分组集合
              rel.getGroupSets(), rel.getAggCallList())); // 分组集合列表、聚合调用列表
    }

    /** Rule configuration. */
    // 规则配置接口
    @Value.Immutable // 不可变值注解
    @Value.Style(init = "with*", typeImmutable = "ImmutablePhysAggRuleConfig") // 设置样式
    public interface Config extends RelRule.Config { // 配置接口，继承自RelRule.Config
      @Override default PhysAggRule toRule() { // 转换为规则的方法
        return new PhysAggRule(this); // 返回PhysAggRule实例
      }
    }
  }

  /** Rule for PhysProj. */
  // PhysProjRule：将逻辑投影节点转换为物理投影节点的规则
  public static class PhysProjRule extends RelRule<PhysProjRule.Config> { // 物理投影规则，继承自RelRule
    static final PhysProjRule INSTANCE = // 规则单例实例
        ImmutablePhysProjRuleConfig.builder() // 构建配置
            .withSubsetHack(false) // 设置subsetHack为false
            .build() // 构建配置
            .withOperandSupplier(b0 -> // 设置操作数提供者
                b0.operand(LogicalProject.class).oneInput(b1 -> // 匹配逻辑投影节点，有一个输入
                    b1.operand(RelNode.class).anyInputs())) // 输入可以是任意RelNode
            .withDescription("PhysProj") // 设置规则描述
            .as(Config.class) // 转换为Config类型
            .toRule(); // 转换为规则

    protected PhysProjRule(Config config) { // 构造函数
      super(config); // 调用父类构造函数
    }

    @Override public void onMatch(RelOptRuleCall call) { // 规则匹配时的处理方法
      LogicalProject rel = call.rel(0); // 获取逻辑投影节点
      RelNode rawInput = call.rel(1); // 获取原始输入节点
      RelNode input = convert(rawInput, PHYSICAL); // 转换输入节点为物理调用约定

      if (config.subsetHack() && input instanceof RelSubset) { // 如果启用了subsetHack且输入是RelSubset
        RelSubset subset = (RelSubset) input; // 转换为RelSubset
        for (RelNode child : subset.getRels()) { // 遍历subset中的所有RelNode
          // skip logical nodes
          if (child.getTraitSet().getTrait(ConventionTraitDef.INSTANCE) // 跳过逻辑节点
              == Convention.NONE) { // 如果调用约定为NONE
            continue; // 跳过
          } else { // 否则处理物理节点
            RelTraitSet outcome = child.getTraitSet().replace(PHYSICAL); // 设置trait为物理调用约定
            call.transformTo( // 转换为物理投影节点
                new PhysProj(rel.getCluster(), outcome, convert(child, outcome), // 创建物理投影
                    rel.getProjects(), rel.getRowType())); // 投影表达式、行类型
          }
        }
      } else { // 否则使用标准转换
        call.transformTo( // 转换为物理投影节点
            PhysProj.create(input, rel.getProjects(), rel.getRowType())); // 使用PhysProj的工厂方法创建
      }
    }

    /** Rule configuration. */
    // 规则配置接口
    @Value.Immutable // 不可变值注解
    @Value.Style(init = "with*", typeImmutable = "ImmutablePhysProjRuleConfig") // 设置样式
    public interface Config extends RelRule.Config { // 配置接口，继承自RelRule.Config
      @Override default PhysProjRule toRule() { // 转换为规则的方法
        return new PhysProjRule(this); // 返回PhysProjRule实例
      }

      boolean subsetHack(); // subsetHack属性：是否使用subset hack模式

      /** Sets {@link #subsetHack()}. */
      Config withSubsetHack(boolean subsetHack); // 设置subsetHack属性的方法

    }
  }

  /** Rule for PhysSort. */
  // PhysSortRule：将逻辑排序节点转换为物理排序节点的规则
  private static class PhysSortRule extends ConverterRule { // 物理排序规则，继承自ConverterRule
    static final PhysSortRule INSTANCE = Config.INSTANCE // 规则单例实例
        .withConversion(Sort.class, Convention.NONE, PHYSICAL, "PhysSortRule") // 设置转换：从Sort节点，NONE约定转换为PHYSICAL约定
        .withRuleFactory(PhysSortRule::new) // 设置规则工厂
        .toRule(PhysSortRule.class); // 转换为规则

    PhysSortRule(Config config) { // 构造函数
      super(config); // 调用父类构造函数
    }

    @Override public RelNode convert(RelNode rel) { // 转换方法
      final Sort sort = (Sort) rel; // 转换为Sort节点
      final RelNode input = // 转换输入节点
          convert(sort.getInput(), rel.getCluster().traitSetOf(PHYSICAL)); // 将输入转换为物理调用约定
      return new PhysSort(rel.getCluster(), // 返回物理排序节点
          input.getTraitSet().plus(sort.getCollation()), // trait集合加上排序collation
          convert(input, input.getTraitSet().replace(PHYSICAL)), // 转换输入节点
          sort.getCollation(), // 排序collation
          null, // offset为null
          null); // fetch为null
    }
  }

  /** Rule for PhysTable. */
  // PhysTableRule：将逻辑表扫描节点转换为物理表扫描节点的规则
  public static class PhysTableRule // 物理表规则，继承自RelRule
      extends RelRule<PhysTableRule.Config> {
    static final PhysTableRule INSTANCE = ImmutablePhysTableRuleConfig.builder().build() // 规则单例实例
        .withOperandSupplier(b -> // 设置操作数提供者
            b.operand(LogicalTableScan.class).noInputs()) // 匹配逻辑表扫描节点，无输入
        .withDescription("PhysScan") // 设置规则描述
        .as(Config.class) // 转换为Config类型
        .toRule(); // 转换为规则

    PhysTableRule(Config config) { // 构造函数
      super(config); // 调用父类构造函数
    }

    @Override public void onMatch(RelOptRuleCall call) { // 规则匹配时的处理方法
      LogicalTableScan rel = call.rel(0); // 获取逻辑表扫描节点
      call.transformTo(new PhysTable(rel.getCluster())); // 转换为物理表扫描节点
    }

    /** Rule configuration. */
    // 规则配置接口
    @Value.Immutable // 不可变值注解
    @Value.Style(init = "with*", typeImmutable = "ImmutablePhysTableRuleConfig") // 设置样式
    public interface Config extends RelRule.Config { // 配置接口，继承自RelRule.Config
      @Override default PhysTableRule toRule() { // 转换为规则的方法
        return new PhysTableRule(this); // 返回PhysTableRule实例
      }
    }
  }

  /* RELS */
  /** Market interface for Phys nodes. */
  // Phys接口：标记接口，用于标识物理RelNode
  private interface Phys extends RelNode { // 物理节点接口，继承自RelNode

  }

  /** Physical Aggregate RelNode. */
  // PhysAgg：物理聚合RelNode实现
  private static class PhysAgg extends Aggregate implements Phys { // 物理聚合类，继承自Aggregate并实现Phys接口
    PhysAgg(RelOptCluster cluster, RelTraitSet traitSet, RelNode input, // 构造函数
        ImmutableBitSet groupSet, // 参数：集群、trait集合、输入节点、分组集合
        List<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls) { // 分组集合列表、聚合调用列表
      super(cluster, traitSet, ImmutableList.of(), input, groupSet, groupSets, aggCalls); // 调用父类构造函数
    }

    public Aggregate copy(RelTraitSet traitSet, RelNode input, // copy方法：创建副本
        ImmutableBitSet groupSet, // 参数：trait集合、输入节点、分组集合
        @Nullable List<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls) { // 分组集合列表、聚合调用列表
      return new PhysAgg(getCluster(), traitSet, input, groupSet, // 返回新的PhysAgg实例
          groupSets, aggCalls); // 使用新参数创建
    }

    public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // computeSelfCost方法：计算自身代价
        RelMetadataQuery mq) { // 参数：计划器、元数据查询
      return planner.getCostFactory().makeCost(1, 1, 1); // 返回固定代价：CPU=1, IO=1, ROW=1
    }
  }

  /** Physical Project RelNode. */
  // PhysProj：物理投影RelNode实现
  private static class PhysProj extends Project implements Phys { // 物理投影类，继承自Project并实现Phys接口
    PhysProj(RelOptCluster cluster, RelTraitSet traits, RelNode child, // 构造函数
        List<RexNode> exps, RelDataType rowType) { // 参数：集群、trait集合、子节点、投影表达式、行类型
      super(cluster, traits, ImmutableList.of(), child, exps, rowType, ImmutableSet.of()); // 调用父类构造函数
    }

    public static PhysProj create(final RelNode input, // create工厂方法：创建物理投影节点
        final List<RexNode> projects, RelDataType rowType) { // 参数：输入节点、投影表达式、行类型
      final RelOptCluster cluster = input.getCluster(); // 获取集群
      final RelMetadataQuery mq = cluster.getMetadataQuery(); // 获取元数据查询对象
      final RelTraitSet traitSet = // 构建trait集合
          cluster.traitSet().replace(PHYSICAL) // 设置物理调用约定
              .replaceIfs( // 条件替换trait
                  RelCollationTraitDef.INSTANCE, // 排序collation trait定义
                  () -> RelMdCollation.project(mq, input, projects)); // 从输入和投影表达式推导排序collation
      return new PhysProj(cluster, traitSet, input, projects, rowType); // 返回新的PhysProj实例
    }

    public PhysProj copy(RelTraitSet traitSet, RelNode input, // copy方法：创建副本
        List<RexNode> exps, RelDataType rowType) { // 参数：trait集合、输入节点、投影表达式、行类型
      return new PhysProj(getCluster(), traitSet, input, exps, rowType); // 返回新的PhysProj实例
    }

    public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // computeSelfCost方法：计算自身代价
        RelMetadataQuery mq) { // 参数：计划器、元数据查询
      return planner.getCostFactory().makeCost(1, 1, 1); // 返回固定代价：CPU=1, IO=1, ROW=1
    }
  }

  /** Physical Sort RelNode. */
  // PhysSort：物理排序RelNode实现
  private static class PhysSort extends Sort implements Phys { // 物理排序类，继承自Sort并实现Phys接口
    PhysSort(RelOptCluster cluster, RelTraitSet traits, RelNode child, // 构造函数
        RelCollation collation, RexNode offset, // 参数：集群、trait集合、子节点、排序collation、offset、fetch
        RexNode fetch) {
      super(cluster, traits, child, collation, offset, fetch); // 调用父类构造函数

    }

    public PhysSort copy(RelTraitSet traitSet, RelNode newInput, // copy方法：创建副本
        RelCollation newCollation, RexNode offset, // 参数：trait集合、新输入、新排序collation、offset、fetch
        RexNode fetch) {
      return new PhysSort(getCluster(), traitSet, newInput, newCollation, // 返回新的PhysSort实例
          offset, fetch); // 使用新参数创建
    }

    public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // computeSelfCost方法：计算自身代价
        RelMetadataQuery mq) { // 参数：计划器、元数据查询
      return planner.getCostFactory().makeCost(1, 1, 1); // 返回固定代价：CPU=1, IO=1, ROW=1
    }
  }

  /** Physical Table RelNode. */
  // PhysTable：物理表扫描RelNode实现
  private static class PhysTable extends AbstractRelNode implements Phys { // 物理表类，继承自AbstractRelNode并实现Phys接口
    PhysTable(RelOptCluster cluster) { // 构造函数
      super(cluster, cluster.traitSet().replace(PHYSICAL).replace(COLLATION)); // 调用父类构造函数，设置trait为物理调用约定和排序collation
      RelDataTypeFactory typeFactory = cluster.getTypeFactory(); // 获取类型工厂
      final RelDataType stringType = typeFactory.createJavaType(String.class); // 创建String类型
      final RelDataType integerType = typeFactory.createJavaType(Integer.class); // 创建Integer类型
      this.rowType = typeFactory.builder().add("s", stringType) // 构建行类型：s列(String)
          .add("i", integerType).build(); // i列(Integer)
    }

    public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // computeSelfCost方法：计算自身代价
        RelMetadataQuery mq) { // 参数：计划器、元数据查询
      return planner.getCostFactory().makeCost(1, 1, 1); // 返回固定代价：CPU=1, IO=1, ROW=1
    }
  }

  // Created so that we can control when the TraitDefs are defined (e.g.
  // before the cluster is created).
  // run方法：运行PropAction并返回优化后的RelNode，用于控制TraitDefs的定义时机（例如在创建cluster之前）
    private static RelNode run(PropAction action, RuleSet rules) // run方法：执行计划构建和优化
        throws Exception { // 可能抛出异常
  
      FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置
          .ruleSets(rules).build(); // 设置规则集合并构建配置
  
      final Properties info = new Properties(); // 创建属性对象
      final Connection connection = DriverManager // 创建Calcite连接
          .getConnection("jdbc:calcite:", info); // 使用Calcite JDBC驱动
      final CalciteServerStatement statement = connection // 获取服务器语句
          .createStatement().unwrap(CalciteServerStatement.class); // 解包为CalciteServerStatement
      final CalcitePrepare.Context prepareContext = // 获取准备上下文
            statement.createPrepareContext(); // 创建准备上下文
      final JavaTypeFactory typeFactory = prepareContext.getTypeFactory(); // 获取Java类型工厂
      CalciteCatalogReader catalogReader = // 创建目录读取器
            new CalciteCatalogReader(prepareContext.getRootSchema(), // 根schema
                prepareContext.getDefaultSchemaPath(), // 默认schema路径
                typeFactory, // 类型工厂
                prepareContext.config()); // 配置
      final RexBuilder rexBuilder = new RexBuilder(typeFactory); // 创建行表达式构建器
      final RelOptPlanner planner = // 创建Volcano计划器
          new VolcanoPlanner(config.getCostFactory(), config.getContext()); // 使用配置中的代价工厂和上下文
  
      // set up rules before we generate cluster
      // 在生成cluster之前设置规则
      planner.clearRelTraitDefs(); // 清除所有RelTraitDef
      planner.addRelTraitDef(RelCollationTraitDef.INSTANCE); // 添加排序collation trait定义
      planner.addRelTraitDef(ConventionTraitDef.INSTANCE); // 添加调用约定trait定义
  
      planner.clear(); // 清除计划器状态
      for (RelOptRule r : rules) { // 遍历所有规则
        planner.addRule(r); // 添加规则到计划器
      }
  
      final RelOptCluster cluster = RelOptCluster.create(planner, rexBuilder); // 创建优化集群
      return action.apply(cluster, catalogReader, // 应用PropAction并返回优化后的RelNode
          prepareContext.getRootSchema().plus()); // 传递根schema
    }
  } // TraitPropagationTest类结束
