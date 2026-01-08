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
package org.apache.calcite.adapter.enumerable; // 声明包名，该类属于org.apache.calcite.adapter.enumerable包，是Calcite可枚举适配器的一部分

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系表达式集群，包含类型系统、RexBuilder等共享对象
import org.apache.calcite.plan.RelOptRuleCall; // 导入RelOptRuleCall类，表示规则调用，包含规则匹配的上下文信息
import org.apache.calcite.plan.RelRule; // 导入RelRule类，是所有优化规则的基类，提供规则匹配和转换的框架
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系表达式，是Calcite中所有关系算子的基类
import org.apache.calcite.rel.core.CorrelationId; // 导入CorrelationId类，表示关联ID，用于标识子查询中的关联变量
import org.apache.calcite.rel.core.Join; // 导入Join类，表示连接操作的关系表达式基类
import org.apache.calcite.rel.core.JoinRelType; // 导入JoinRelType类，表示连接类型枚举（内连接、左外连接等）
import org.apache.calcite.rel.logical.LogicalJoin; // 导入LogicalJoin类，表示逻辑连接操作，未经过物理优化的连接
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder类，用于构建行表达式（RexNode）的工具类
import org.apache.calcite.rex.RexCorrelVariable; // 导入RexCorrelVariable类，表示关联变量，用于访问子查询中的外部字段
import org.apache.calcite.rex.RexInputRef; // 导入RexInputRef类，表示对输入字段的引用，如$0表示第一个输入字段
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式的基类，所有表达式都继承自此类
import org.apache.calcite.rex.RexShuttle; // 导入RexShuttle类，表示表达式访问器，用于遍历和转换表达式树
import org.apache.calcite.tools.RelBuilder; // 导入RelBuilder类，用于构建关系表达式的工具类，提供链式API
import org.apache.calcite.tools.RelBuilderFactory; // 导入RelBuilderFactory类，用于创建RelBuilder实例的工厂类
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，表示不可变的位集合，用于高效存储字段索引集合

import org.immutables.value.Value; // 导入Immutables库的Value注解，用于生成不可变值对象

import java.util.ArrayList; // 导入ArrayList类，表示动态数组，用于存储可变长度的元素列表
import java.util.HashSet; // 导入HashSet类，表示哈希集合，用于存储不重复的元素
import java.util.List; // 导入List接口，表示有序集合，定义列表操作的标准方法
import java.util.Set; // 导入Set接口，表示集合，定义集合操作的标准方法

/** Rule to convert a {@link LogicalJoin} to an {@link EnumerableBatchNestedLoopJoin}.
 * You may provide a custom config to convert other nodes that extend {@link Join}.
 * 
 * 这是一个规则类，用于将逻辑连接（LogicalJoin）转换为可枚举的批量嵌套循环连接（EnumerableBatchNestedLoopJoin）。
 * 批量嵌套循环连接是一种连接算法，它将左表的行分批处理，每批包含多行，然后对每批中的所有行与右表进行嵌套循环连接。
 * 这种方法可以减少右表的扫描次数，提高性能，特别是在右表较小或可以使用索引的情况下。
 * 
 * 该规则支持自定义配置，可以转换其他继承自Join类的节点，而不仅仅是LogicalJoin。
 * 
 * @see EnumerableRules#ENUMERABLE_BATCH_NESTED_LOOP_JOIN_RULE 引用EnumerableRules中定义的批量嵌套循环连接规则常量
 */
@Value.Enclosing // 标记注解，表示该类包含一个或多个嵌套的值类型（Value.Immutable接口）
public class EnumerableBatchNestedLoopJoinRule // 声明类名，可枚举批量嵌套循环连接规则类
    extends RelRule<EnumerableBatchNestedLoopJoinRule.Config> { // 继承自RelRule基类，泛型参数为该规则的配置接口类型
  /** Creates an EnumerableBatchNestedLoopJoinRule. */ // 创建一个EnumerableBatchNestedLoopJoinRule实例的构造方法文档注释
  protected EnumerableBatchNestedLoopJoinRule(Config config) { // 受保护的构造方法，接受一个Config配置对象作为参数
    super(config); // 调用父类RelRule的构造方法，传入配置对象，初始化规则的基本属性
  } // 构造方法结束

  @Deprecated // to be removed before 2.0 // 标记为已过时，将在2.0版本之前移除，建议使用基于Config的构造方法
  protected EnumerableBatchNestedLoopJoinRule(Class<? extends Join> clazz, // 受保护的构造方法，接受Join的子类类型、RelBuilderFactory和批大小三个参数
      RelBuilderFactory relBuilderFactory, int batchSize) { // relBuilderFactory用于创建RelBuilder，batchSize指定每批处理的行数
    this(Config.DEFAULT.withRelBuilderFactory(relBuilderFactory) // 使用默认配置，设置RelBuilderFactory
        .withOperandSupplier(b -> b.operand(clazz).anyInputs()) // 设置操作数提供者，指定该规则匹配的Join子类类型，anyInputs表示不限制输入
        .as(Config.class) // 将配置转换为Config接口类型
        .withBatchSize(batchSize)); // 设置批大小参数
  } // 构造方法结束

  @Deprecated // to be removed before 2.0 // 标记为已过时，将在2.0版本之前移除，建议使用基于Config的构造方法
  public EnumerableBatchNestedLoopJoinRule(RelBuilderFactory relBuilderFactory) { // 公开的构造方法，只接受RelBuilderFactory参数
    this(Config.DEFAULT.withRelBuilderFactory(relBuilderFactory) // 使用默认配置，设置RelBuilderFactory
        .as(Config.class)); // 将配置转换为Config接口类型，使用默认的批大小
  } // 构造方法结束

  @Deprecated // to be removed before 2.0 // 标记为已过时，将在2.0版本之前移除，建议使用基于Config的构造方法
  public EnumerableBatchNestedLoopJoinRule(RelBuilderFactory relBuilderFactory, // 公开的构造方法，接受RelBuilderFactory和批大小两个参数
      int batchSize) { // batchSize指定每批处理的行数
    this(Config.DEFAULT.withRelBuilderFactory(relBuilderFactory) // 使用默认配置，设置RelBuilderFactory
        .as(Config.class) // 将配置转换为Config接口类型
        .withBatchSize(batchSize)); // 设置批大小参数
  } // 构造方法结束

  @Override public boolean matches(RelOptRuleCall call) { // 重写父类RelRule的matches方法，判断规则是否匹配给定的规则调用
    Join join = call.rel(0); // 从规则调用中获取第一个关系表达式，即Join节点
    JoinRelType joinType = join.getJoinType(); // 获取连接节点的连接类型（内连接、左外连接等）
    return joinType == JoinRelType.INNER // 判断连接类型是否为内连接，内连接返回两个表中匹配的行
        || joinType == JoinRelType.LEFT // 或者判断连接类型是否为左外连接，左外连接返回左表所有行和右表匹配的行
        || joinType == JoinRelType.ANTI // 或者判断连接类型是否为反连接，反连接返回左表中不与右表匹配的行
        || joinType == JoinRelType.SEMI; // 或者判断连接类型是否为半连接，半连接返回左表中与右表匹配的行（去重）
  } // matches方法结束，返回布尔值表示是否匹配

  @Override public void onMatch(RelOptRuleCall call) { // 重写父类RelRule的onMatch方法，当规则匹配时执行转换逻辑
    final Join join = call.rel(0); // 从规则调用中获取第一个关系表达式，即Join节点
    final int leftFieldCount = join.getLeft().getRowType().getFieldCount(); // 获取左表的字段数量，用于区分左表和右表的字段引用
    final RelOptCluster cluster = join.getCluster(); // 获取关系表达式集群，包含类型系统、RexBuilder等共享对象
    final RexBuilder rexBuilder = cluster.getRexBuilder(); // 从集群中获取RexBuilder，用于构建行表达式
    final RelBuilder relBuilder = call.builder(); // 从规则调用中创建RelBuilder，用于构建新的关系表达式

    final Set<CorrelationId> correlationIds = new HashSet<>(); // 创建一个集合，用于存储批量处理所需的关联ID
    final List<RexNode> corrVarList = new ArrayList<>(); // 创建一个列表，用于存储批量处理所需的关联变量表达式

    final int batchSize = config.batchSize(); // 从配置中获取批大小，即每批处理的左表行数
    for (int i = 0; i < batchSize; i++) { // 循环创建批大小数量的关联ID和关联变量
      CorrelationId correlationId = cluster.createCorrel(); // 在集群中创建一个新的关联ID，用于标识一个关联变量
      correlationIds.add(correlationId); // 将创建的关联ID添加到集合中
      corrVarList.add( // 将关联变量表达式添加到列表中
          rexBuilder.makeCorrel(join.getLeft().getRowType(), // 创建一个关联变量表达式，类型为左表的行类型
              correlationId)); // 使用刚创建的关联ID
    } // 循环结束，创建了批大小数量的关联ID和关联变量
    final RexNode corrVar0 = corrVarList.get(0); // 获取第一个关联变量，用于生成第一个条件表达式

    final ImmutableBitSet.Builder requiredColumns = ImmutableBitSet.builder(); // 创建一个不可变位集合构建器，用于记录左表中需要的字段

    // Generate first condition // 注释：生成第一个条件表达式
    final RexNode condition = join.getCondition().accept(new RexShuttle() { // 获取连接条件，并使用RexShuttle访问器遍历和转换表达式树
      @Override public RexNode visitInputRef(RexInputRef input) { // 重写visitInputRef方法，处理输入字段引用
        int field = input.getIndex(); // 获取字段引用的索引值
        if (field >= leftFieldCount) { // 判断字段索引是否大于等于左表字段数量，即是否为右表的字段引用
          return rexBuilder.makeInputRef(input.getType(), // 如果是右表字段，创建一个新的输入引用，类型不变
              input.getIndex() - leftFieldCount); // 索引减去左表字段数量，使其相对于右表
        } // 如果是右表字段的分支结束
        requiredColumns.set(field); // 如果是左表字段，将该字段索引添加到必需字段集合中
        return rexBuilder.makeFieldAccess(corrVar0, field); // 返回对关联变量的字段访问表达式，用于在右表过滤中引用左表字段
      } // visitInputRef方法结束
    }); // RexShuttle匿名类定义和条件转换结束

    final List<RexNode> conditionList = new ArrayList<>(); // 创建一个列表，用于存储批处理的所有条件表达式
    conditionList.add(condition); // 将第一个条件表达式添加到条件列表中

    // Add batchSize-1 other conditions // 注释：添加批大小减1个其他条件表达式
    for (int i = 1; i < batchSize; i++) { // 循环从1开始到批大小减1，生成剩余的条件表达式
      final int corrIndex = i; // 捕获当前循环索引，用于在匿名类中使用
      final RexNode condition2 = condition.accept(new RexShuttle() { // 基于第一个条件表达式，使用RexShuttle访问器创建新的条件表达式
        @Override public RexNode visitCorrelVariable(RexCorrelVariable variable) { // 重写visitCorrelVariable方法，处理关联变量引用
          return variable.equals(corrVar0) ? corrVarList.get(corrIndex) : variable; // 如果关联变量是第一个关联变量，则替换为当前索引对应的关联变量，否则保持不变
        } // visitCorrelVariable方法结束
      }); // RexShuttle匿名类定义和条件转换结束
      conditionList.add(condition2); // 将生成的条件表达式添加到条件列表中
    } // 循环结束，生成了批大小数量的条件表达式

    // Push a filter with batchSize disjunctions // 注释：推入一个包含批大小数量析取（OR）条件的过滤器
    relBuilder.push(join.getRight()).filter(relBuilder.or(conditionList)); // 将右表推入RelBuilder栈，然后添加一个过滤条件，该条件是所有条件表达式的OR组合
    final RelNode right = relBuilder.build(); // 构建经过过滤的右表关系表达式

    call.transformTo( // 调用规则转换方法，将原始Join节点转换为EnumerableBatchNestedLoopJoin节点
        EnumerableBatchNestedLoopJoin.create( // 创建一个可枚举批量嵌套循环连接节点
            convert(call.getPlanner(), join.getLeft(), join.getLeft().getTraitSet() // 转换左表关系表达式，将特征集替换为可枚举约定
                .replace(EnumerableConvention.INSTANCE)), // 使用可枚举约定替换原有特征
            convert(call.getPlanner(), right, right.getTraitSet() // 转换右表关系表达式，将特征集替换为可枚举约定
                .replace(EnumerableConvention.INSTANCE)), // 使用可枚举约定替换原有特征
            join.getCondition(), // 传入原始的连接条件
            requiredColumns.build(), // 传入必需的字段集合
            correlationIds, // 传入关联ID集合
            join.getJoinType())); // 传入连接类型
  } // onMatch方法结束

  /** Rule configuration. */ // 注释：规则配置接口的文档注释
  @Value.Immutable // 使用Immutables注解，表示该接口将生成一个不可变的实现类
  public interface Config extends RelRule.Config { // 定义Config接口，继承自RelRule.Config接口，用于配置规则的各种属性
    Config DEFAULT = ImmutableEnumerableBatchNestedLoopJoinRule.Config.of() // 定义默认配置实例，使用Immutables生成的工厂方法创建
        .withOperandSupplier(b -> b.operand(LogicalJoin.class).anyInputs()) // 设置操作数提供者，指定规则匹配LogicalJoin类，anyInputs表示不限制输入
        .withDescription("EnumerableBatchNestedLoopJoinRule"); // 设置规则的描述信息

    @Override default EnumerableBatchNestedLoopJoinRule toRule() { // 重写toRule方法，将配置转换为规则实例
      return new EnumerableBatchNestedLoopJoinRule(this); // 使用当前配置创建一个新的EnumerableBatchNestedLoopJoinRule实例
    } // toRule方法结束

    /** Batch size.
     *
     * <p>Warning: if the batch size is around or bigger than 1000 there
     * can be an error because the generated code exceeds the size limit. */ // 批大小的文档注释，警告批大小过大可能导致生成代码超出限制
    @Value.Default // 使用Immutables注解，表示该属性有默认值
    default int batchSize() { // 定义批大小属性，返回每批处理的行数
      return 100; // 返回默认批大小为100
    } // batchSize方法结束

    /** Sets {@link #batchSize()}. */ // 设置批大小方法的文档注释
    Config withBatchSize(int batchSize); // 定义withBatchSize方法，用于设置批大小并返回新的配置实例（不可变模式）
  } // Config接口定义结束
} // EnumerableBatchNestedLoopJoinRule类定义结束
