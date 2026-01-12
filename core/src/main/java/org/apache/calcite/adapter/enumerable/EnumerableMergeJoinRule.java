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
// 声明包名：该类位于org.apache.calcite.adapter.enumerable包中，属于可枚举适配器模块
package org.apache.calcite.adapter.enumerable;

// 导入Ord类：用于为集合元素添加索引，便于在迭代时获取元素的位置信息
import org.apache.calcite.linq4j.Ord;
// 导入Convention接口：表示关系代数表达式的调用约定，定义了如何执行关系操作
import org.apache.calcite.plan.Convention;
// 导入RelOptCluster类：表示关系优化集群，包含共享的优化器上下文信息
import org.apache.calcite.plan.RelOptCluster;
// 导入RelTraitSet类：表示关系节点的特征集合，包含调用约定、排序方式等属性
import org.apache.calcite.plan.RelTraitSet;
// 导入RelCollation类：表示关系的排序规范，定义了字段如何排序
import org.apache.calcite.rel.RelCollation;
// 导入RelCollations类：提供创建和操作排序规范的静态工具方法
import org.apache.calcite.rel.RelCollations;
// 导入RelFieldCollation类：表示单个字段的排序规范，包含排序方向和空值处理方式
import org.apache.calcite.rel.RelFieldCollation;
// 导入RelNode接口：表示关系代数表达式树中的一个节点
import org.apache.calcite.rel.RelNode;
// 导入ConverterRule抽象类：表示转换规则的基类，用于将一种关系节点转换为另一种
import org.apache.calcite.rel.convert.ConverterRule;
// 导入Join类：表示连接操作的关系节点基类，包含连接条件和类型等
import org.apache.calcite.rel.core.Join;
// 导入JoinInfo类：封装连接条件的分析结果，包含等值连接键对和非等值条件
import org.apache.calcite.rel.core.JoinInfo;
// 导入LogicalJoin类：表示逻辑连接操作的关系节点，尚未转换为物理实现
import org.apache.calcite.rel.logical.LogicalJoin;
// 导入RexBuilder类：用于构建RexNode表达式树的构建器
import org.apache.calcite.rex.RexBuilder;
// 导入RexNode接口：表示行表达式（Row Expression），是Calcite中表达式的抽象
import org.apache.calcite.rex.RexNode;
// 导入RexUtil类：提供操作RexNode表达式的静态工具方法
import org.apache.calcite.rex.RexUtil;

// 导入Nullable注解：用于标记可能为null的返回值或参数，进行静态空值检查
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入ArrayList类：动态数组实现，用于存储可变长度的元素列表
import java.util.ArrayList;
// 导入Arrays类：提供操作数组的静态工具方法
import java.util.Arrays;
// 导入List接口：表示有序集合，存储可重复的元素
import java.util.List;

/**
 * Planner rule that converts a
 * {@link LogicalJoin} relational expression
 * {@link EnumerableConvention enumerable calling convention}.
 * You may provide a custom config to convert other nodes that extend {@link Join}.
 *
 * @see EnumerableJoinRule
 * @see EnumerableRules#ENUMERABLE_MERGE_JOIN_RULE
 */
// 类注释：这是一个优化器规则，用于将LogicalJoin（逻辑连接）关系表达式转换为EnumerableConvention（可枚举调用约定）
// 该规则专门用于生成可执行的合并连接（Merge Join）实现，适用于已排序的数据集
// 可以提供自定义配置来转换其他继承自Join的节点
// 参见EnumerableJoinRule：另一个连接转换规则
// 参见EnumerableRules#ENUMERABLE_MERGE_JOIN_RULE：该规则的预定义实例
// 该类继承自ConverterRule，是一个转换规则，用于在优化过程中将逻辑节点转换为物理实现节点
class EnumerableMergeJoinRule extends ConverterRule {
  /** Default configuration. */
  // 常量：默认配置对象，定义了该规则的基本属性
  // Config.INSTANCE：使用默认的配置实例
  // .withConversion：配置转换规则，指定：
  //   - LogicalJoin.class：要转换的源节点类型（逻辑连接）
  //   - Convention.NONE：源节点的调用约定（无约定，表示逻辑层）
  //   - EnumerableConvention.INSTANCE：目标调用约定（可枚举约定，表示可执行层）
  //   - "EnumerableMergeJoinRule"：规则的描述性名称
  // .withRuleFactory：设置规则工厂，使用方法引用EnumerableMergeJoinRule::new来创建规则实例
  static final Config DEFAULT_CONFIG = Config.INSTANCE
      .withConversion(LogicalJoin.class, Convention.NONE,
          EnumerableConvention.INSTANCE, "EnumerableMergeJoinRule")
      .withRuleFactory(EnumerableMergeJoinRule::new);

  /** Called from the Config. */
  // 构造方法：受保护的构造函数，由Config配置对象调用
  // 参数config：规则配置对象，包含转换规则的所有配置信息
  // super(config)：调用父类ConverterRule的构造方法，初始化规则配置
  // 这个构造方法通常是反射调用，通过规则工厂方法创建
  protected EnumerableMergeJoinRule(Config config) {
    super(config);
  }

  // 方法：convert - 核心转换方法，将输入的关系节点转换为可枚举的合并连接节点
  // 参数rel：要转换的关系节点，预期是Join或其子类（如LogicalJoin）
  // 返回值：转换后的RelNode，如果转换不成功则返回null
  // @Nullable注解表示该方法可能返回null
  @Override public @Nullable RelNode convert(RelNode rel) {
    // 将输入节点强制转换为Join类型，因为该规则只处理连接操作
    Join join = (Join) rel;
    // 调用连接节点的analyzeCondition方法分析连接条件
    // 返回JoinInfo对象，包含：
    //   - pairs：等值连接的字段对列表
    //   - keys：左右表的连接键
    //   - nonEquiConditions：非等值连接条件列表
    //   - isEqui：是否为纯等值连接
    final JoinInfo info = join.analyzeCondition();
    // 检查当前连接类型是否支持合并连接
    // 调用EnumerableMergeJoin.isMergeJoinSupported静态方法验证
    // 合并连接通常只支持INNER、LEFT、RIGHT等特定连接类型，不支持FULL OUTER等
    if (!EnumerableMergeJoin.isMergeJoinSupported(join.getJoinType())) {
      // 如果连接类型不支持，返回null表示该规则不适用，优化器会尝试其他规则
      // EnumerableMergeJoin only supports certain join types.
      return null;
    }
    // 检查是否存在等值连接键对（pairs为空表示没有等值连接条件）
    // info.pairs().isEmpty()：true表示没有等值连接，可能是笛卡尔积
    if (info.pairs().isEmpty()) {
      // 如果没有等值连接条件，虽然合并连接可以支持笛卡尔积，但当前实现中禁用了它
      // 返回null表示跳过该规则，让其他规则处理笛卡尔积
      // EnumerableMergeJoin CAN support cartesian join, but disable it for now.
      return null;
    }
    // 创建新的输入节点列表，用于存储转换后的左右子节点
    final List<RelNode> newInputs = new ArrayList<>();
    // 创建排序规范列表，用于存储左右子节点所需的排序规范
    final List<RelCollation> collations = new ArrayList<>();
    // offset变量：用于计算字段索引偏移量，因为右表的字段索引需要加上左表的字段数
    int offset = 0;
    // 遍历连接节点的所有输入（左表和右表）
    // Ord.zip：为输入列表添加索引，返回Ord<RelNode>列表，每个元素包含索引i和节点e
    for (Ord<RelNode> ord : Ord.zip(join.getInputs())) {
      // 获取当前输入节点的特征集，并将其调用约定替换为可枚举约定
      // ord.e：当前的关系节点
      // .getTraitSet()：获取节点的特征集
      // .replace(EnumerableConvention.INSTANCE)：将调用约定替换为可枚举约定
      RelTraitSet traits = ord.e.getTraitSet()
          .replace(EnumerableConvention.INSTANCE);
      // 如果存在等值连接键对（即不是笛卡尔积）
      if (!info.pairs().isEmpty()) {
        // 创建字段排序规范列表，用于存储当前输入节点中参与连接的字段的排序要求
        final List<RelFieldCollation> fieldCollations = new ArrayList<>();
        // 遍历当前输入节点（左表或右表）的所有连接键
        // info.keys().get(ord.i)：获取当前输入节点的连接键索引列表
        // ord.i为0表示左表，为1表示右表
        for (int key : info.keys().get(ord.i)) {
          // 为每个连接键创建字段排序规范
          // 参数说明：
          //   - key：字段索引
          //   - RelFieldCollation.Direction.ASCENDING：升序排序（合并连接需要有序数据）
          //   - RelFieldCollation.NullDirection.LAST：空值排在最后
          fieldCollations.add(
              new RelFieldCollation(key, RelFieldCollation.Direction.ASCENDING,
                  RelFieldCollation.NullDirection.LAST));
        }
        // 根据字段排序规范列表创建排序规范对象
        final RelCollation collation = RelCollations.of(fieldCollations);
        // 将排序规范添加到collations列表中
        // RelCollations.shift(collation, offset)：调整字段索引，考虑偏移量
        // 对于右表，需要将其字段索引加上左表的字段数，使其在整个连接结果中的索引正确
        collations.add(RelCollations.shift(collation, offset));
        // 将排序规范添加到特征集中，确保子节点按照连接键排序
        traits = traits.replace(collation);
      }
      // 转换当前输入节点，使用新的特征集（包含可枚举约定和排序规范）
      // convert方法会递归应用规则，将子节点也转换为可枚举实现
      newInputs.add(convert(ord.e, traits));
      // 更新偏移量：加上当前节点的字段数
      // 这样处理右表时，其字段索引会从左表字段数之后开始
      offset += ord.e.getRowType().getFieldCount();
    }
    // 从转换后的输入列表中获取左表（第一个输入）
    final RelNode left = newInputs.get(0);
    // 从转换后的输入列表中获取右表（第二个输入）
    final RelNode right = newInputs.get(1);
    // 获取连接节点的集群对象，包含共享的优化器上下文信息
    final RelOptCluster cluster = join.getCluster();

    // 创建连接节点的特征集
    // 从原连接节点的特征集开始
    // 将调用约定替换为可枚举约定
    RelTraitSet traitSet = join.getTraitSet()
        .replace(EnumerableConvention.INSTANCE);
    // 如果存在排序规范（即不是笛卡尔积）
    if (!collations.isEmpty()) {
      // 将排序规范添加到特征集中
      // 这样生成的EnumerableMergeJoin节点就知道输入数据是按照什么顺序排序的
      traitSet = traitSet.replace(collations);
    }
    // Re-arrange condition: first the equi-join elements, then the non-equi-join ones (if any);
    // this is not strictly necessary but it will be useful to avoid spurious errors in the
    // unit tests when verifying the plan.
    // 注释说明：重新排列连接条件，先放等值连接部分，再放非等值连接部分（如果有）
    // 这不是严格必需的，但在单元测试中验证计划时有助于避免虚假错误
    // 获取RexBuilder对象，用于构建表达式
    final RexBuilder rexBuilder = join.getCluster().getRexBuilder();
    // 构建等值连接条件表达式
    // info.getEquiCondition：根据连接键对生成等值连接的RexNode表达式
    // 参数：左表、右表、rexBuilder
    final RexNode equi = info.getEquiCondition(left, right, rexBuilder);
    // 声明最终的条件表达式变量
    final RexNode condition;
    // 判断是否为纯等值连接（没有非等值条件）
    if (info.isEqui()) {
      // 如果是纯等值连接，条件就是等值连接表达式
      condition = equi;
    } else {
      // 如果不是纯等值连接，需要组合等值和非等值条件
      // 构建非等值连接条件表达式
      // RexUtil.composeConjunction：将多个条件用AND连接成一个复合条件
      // info.nonEquiConditions：非等值条件列表
      final RexNode nonEqui = RexUtil.composeConjunction(rexBuilder, info.nonEquiConditions);
      // 将等值条件和非等值条件用AND连接起来
      // Arrays.asList(equi, nonEqui)：创建包含两个条件的列表
      condition = RexUtil.composeConjunction(rexBuilder, Arrays.asList(equi, nonEqui));
    }
    // 创建并返回新的EnumerableMergeJoin节点
    // 参数说明：
    //   - cluster：优化器集群，包含共享上下文
    //   - traitSet：特征集，包含可枚举约定和排序规范
    //   - left：左表节点（已转换为可枚举实现）
    //   - right：右表节点（已转换为可枚举实现）
    //   - condition：连接条件（等值+非等值）
    //   - join.getVariablesSet()：连接中使用的变量集合（用于半连接等特殊连接）
    //   - join.getJoinType()：连接类型（INNER、LEFT、RIGHT等）
    return new EnumerableMergeJoin(cluster, traitSet, left, right, condition,
        join.getVariablesSet(), join.getJoinType());
  }
}
