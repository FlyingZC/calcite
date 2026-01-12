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
// Apache许可证声明，表明该文件遵循Apache 2.0许可证
package org.apache.calcite.adapter.geode.rel; // 声明包名，该类属于Geode适配器的rel（关系表达式）包

// 导入Calcite核心规划相关的类
import org.apache.calcite.plan.Convention; // 导入Convention类，用于表示关系操作符的调用约定（如物理实现方式）
import org.apache.calcite.plan.RelOptRule; // 导入RelOptRule类，优化规则的基类
import org.apache.calcite.plan.RelOptRuleCall; // 导入RelOptRuleCall类，表示优化规则的调用上下文
import org.apache.calcite.plan.RelOptUtil; // 导入RelOptUtil类，提供关系表达式操作的工具方法
import org.apache.calcite.plan.RelRule; // 导入RelRule类，基于配置的关系规则基类
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合

// 导入Calcite关系表达式相关的类
import org.apache.calcite.rel.RelCollations; // 导入RelCollations类，提供排序相关的常量和方法
import org.apache.calcite.rel.RelNode; // 导入RelNode类，关系表达式的基类
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类，用于将关系表达式从一种约定转换为另一种约定的规则基类
import org.apache.calcite.rel.core.Sort; // 导入Sort类，表示排序关系操作符
import org.apache.calcite.rel.logical.LogicalAggregate; // 导入LogicalAggregate类，表示逻辑聚合操作符
import org.apache.calcite.rel.logical.LogicalFilter; // 导入LogicalFilter类，表示逻辑过滤操作符
import org.apache.calcite.rel.logical.LogicalProject; // 导入LogicalProject类，表示逻辑投影操作符
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型

// 导入Calcite行表达式（Rex）相关的类
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示行表达式中的函数调用
import org.apache.calcite.rex.RexInputRef; // 导入RexInputRef类，表示对输入字段的引用
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类，表示行表达式中的字面量
import org.apache.calcite.rex.RexNode; // 导入RexNode类，行表达式的基类
import org.apache.calcite.rex.RexVisitorImpl; // 导入RexVisitorImpl类，行表达式访问者的默认实现

// 导入Calcite SQL相关的类
import org.apache.calcite.sql.SqlKind; // 导入SqlKind类，表示SQL操作的类型（如SELECT、WHERE等）
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类，包含标准SQL操作符
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName类，表示SQL类型名称
import org.apache.calcite.sql.validate.SqlValidatorUtil; // 导入SqlValidatorUtil类，提供SQL验证相关的工具方法

// 导入第三方库
import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的值
import org.immutables.value.Value; // 导入Immutables库的Value注解，用于生成不可变值对象

import java.util.ArrayList; // 导入Java的ArrayList类，动态数组实现
import java.util.List; // 导入Java的List接口，列表集合

import static com.google.common.base.Preconditions.checkArgument; // 静态导入Google Guava的前置条件检查方法

/**
 * Rules and relational operators for {@link GeodeRel#CONVENTION}
 * calling convention.
 */
// 类的功能说明：该类包含了用于Apache Geode适配器的优化规则和关系操作符
// 这些规则负责将Calcite的逻辑关系表达式转换为Geode特定的物理实现
// Geode是一个内存数据网格，该类定义了如何将SQL查询下推到Geode执行
public class GeodeRules { // 定义GeodeRules类，包含所有Geode相关的优化规则

  // 定义Geode适配器的规则数组，包含了所有可用的转换规则
  // 这些规则按照优先级顺序排列，优化器会依次尝试应用这些规则
  static final RelOptRule[] RULES = { // 规则数组，包含4个核心规则
      GeodeSortLimitRule.INSTANCE, // 排序和限制规则，用于处理ORDER BY和LIMIT
      GeodeFilterRule.INSTANCE,    // 过滤规则，用于处理WHERE条件
      GeodeProjectRule.INSTANCE,   // 投影规则，用于处理SELECT子句
      GeodeAggregateRule.INSTANCE, // 聚合规则，用于处理GROUP BY和聚合函数
  }; // 规则数组结束


  // 私有构造函数，防止实例化该工具类
  // 该类只包含静态方法和静态成员，不需要创建实例
  private GeodeRules() { // 私有构造函数
  } // 构造函数体为空

  /**
   * Returns 'string' if it is a call to item['string'], null otherwise.
   */
  // 方法功能说明：检查给定的RexCall是否是对item['string']形式的访问
  // 这种形式通常用于访问Map或JSON中的字段，例如在Geode中访问嵌套字段
  // 如果是item访问且索引是字符串字面量，则返回该字符串；否则返回null
  static @Nullable String isItem(RexCall call) { // 静态方法，接收一个RexCall参数，返回可空的字符串
    // 检查操作符是否是ITEM操作符（用于访问数组或Map元素）
    if (call.getOperator() != SqlStdOperatorTable.ITEM) { // 如果不是ITEM操作符
      return null; // 返回null，表示不是item访问
    } // if结束
    // 获取ITEM操作符的第一个操作数（通常是被访问的对象）
    final RexNode op0 = call.getOperands().get(0); // 获取第一个操作数
    // 获取ITEM操作符的第二个操作数（通常是索引或键）
    final RexNode op1 = call.getOperands().get(1); // 获取第二个操作数

    // 检查第一个操作数是否是输入引用且索引为0（表示引用整个记录）
    // 检查第二个操作数是否是字符串字面量
    if (op0 instanceof RexInputRef // 如果第一个操作数是输入引用
        && ((RexInputRef) op0).getIndex() == 0 // 且索引为0
        && op1 instanceof RexLiteral // 且第二个操作数是字面量
        && ((RexLiteral) op1).getValue2() instanceof String) { // 且值类型是String
      // 返回字符串字面量的值，即访问的字段名
      return (String) ((RexLiteral) op1).getValue2(); // 返回字段名字符串
    } // if结束
    // 如果不满足item['string']的形式，返回null
    return null; // 返回null
  } // 方法结束

  // 方法功能说明：根据关系数据类型获取Geode字段名称列表
  // 该方法确保字段名称是唯一的，避免重复字段名导致的问题
  // 参数rowType表示行的数据类型，包含所有字段的信息
  // 返回去重后的字段名称列表
  static List<String> geodeFieldNames(final RelDataType rowType) { // 静态方法，接收关系数据类型参数
    // 使用SqlValidatorUtil工具类对字段名进行去重处理
    // 第二个参数true表示在重复名称后添加数字后缀使其唯一
    return SqlValidatorUtil.uniquify(rowType.getFieldNames(), true); // 返回去重后的字段名列表
  } // 方法结束

  /**
   * Translator from {@link RexNode} to strings in Geode's expression language.
   */
  // 内部类功能说明：将Calcite的行表达式（RexNode）转换为Geode OQL表达式字符串
  // 这是一个访问者模式的实现，遍历RexNode树并生成对应的Geode查询语言字符串
  // Geode OQL（Object Query Language）是Geode的查询语言，类似于SQL但面向对象
  static class RexToGeodeTranslator extends RexVisitorImpl<String> { // 继承RexVisitorImpl，将RexNode转换为字符串

    // 输入字段名称列表，用于将字段索引映射到实际的字段名
    // 例如：索引0对应字段"id"，索引1对应字段"name"
    private final List<String> inFields; // 保存输入字段名称列表

    // 构造函数：初始化转换器，并传入输入字段名称列表
    // 参数inFields：输入关系表达式的字段名称列表
    protected RexToGeodeTranslator(List<String> inFields) { // 构造函数
      super(true); // 调用父类构造函数，参数true表示深度遍历
      this.inFields = inFields; // 保存输入字段列表
    } // 构造函数结束

    // 方法功能说明：访问输入引用（RexInputRef），将其转换为字段名字符串
    // RexInputRef表示对输入字段的引用，例如"SELECT id FROM table"中的"id"
    // 参数inputRef：输入引用节点
    // 返回：对应字段名的字符串
    @Override public String visitInputRef(RexInputRef inputRef) { // 重写访问输入引用的方法
      // 根据索引从字段名列表中获取对应的字段名
      return inFields.get(inputRef.getIndex()); // 返回字段名字符串
    } // 方法结束

    // 方法功能说明：访问函数调用（RexCall），将其转换为Geode OQL表达式字符串
    // RexCall表示函数调用，例如"item['name']"或"salary * 1.1"
    // 参数call：函数调用节点
    // 返回：对应的Geode OQL表达式字符串
    @Override public String visitCall(RexCall call) { // 重写访问函数调用的方法
      // 创建字符串列表，用于保存操作数转换后的字符串
      final List<String> strings = new ArrayList<>(); // 创建字符串列表
      // 递归访问所有操作数，将它们转换为字符串并保存到列表中
      visitList(call.operands, strings); // 访问操作数列表

      // 特殊处理ITEM操作符，用于访问Map或数组的元素
      if (call.getOperator() == SqlStdOperatorTable.ITEM) { // 如果是ITEM操作符
        // 获取ITEM的第二个操作数（索引或键）
        final RexNode op1 = call.getOperands().get(1); // 获取第二个操作数
        // 检查第二个操作数是否是字面量
        if (op1 instanceof RexLiteral) { // 如果是字面量
          // 如果索引是整数类型，生成数组访问语法，例如"field[0]"
          if (op1.getType().getSqlTypeName() == SqlTypeName.INTEGER) { // 如果是整数类型
            // 去掉引号并添加数组索引语法
            return stripQuotes(strings.get(0)) + "[" + ((RexLiteral) op1).getValue2() + "]"; // 返回数组访问表达式
          // 如果索引是字符类型，生成字段访问语法，例如"field.name"
          } else if (op1.getType().getSqlTypeName() == SqlTypeName.CHAR) { // 如果是字符类型
            // 去掉引号并添加点号访问语法
            return stripQuotes(strings.get(0)) + "." + ((RexLiteral) op1).getValue2(); // 返回字段访问表达式
          } // if结束
        } // if结束
      } // if结束

      // 对于其他操作符，调用父类的默认处理逻辑
      return super.visitCall(call); // 返回父类生成的字符串
    } // 方法结束

    // 方法功能说明：去除字符串两端的单引号
    // 参数s：可能包含引号的字符串
    // 返回：去除引号后的字符串，如果没有引号则返回原字符串
    private static String stripQuotes(String s) { // 私有静态方法
      // 检查字符串是否以单引号开头和结尾
      return s.startsWith("'") && s.endsWith("'") ? s.substring(1, s.length() - 1) : s; // 去除引号或返回原字符串
    } // 方法结束
  } // 内部类结束

  /**
   * Rule to convert a {@link LogicalProject} to a {@link GeodeProject}.
   */
  // 内部类功能说明：将逻辑投影（LogicalProject）转换为Geode投影（GeodeProject）的规则
  // 投影操作对应SQL中的SELECT子句，用于选择和计算输出字段
  // 该规则负责判断哪些投影操作可以下推到Geode执行
  private static class GeodeProjectRule extends GeodeConverterRule { // 继承GeodeConverterRule
    // 规则的单例实例，使用配置模式创建
    // 将LogicalProject（无约定）转换为GeodeProject（Geode约定）
    private static final GeodeProjectRule INSTANCE = Config.INSTANCE // 创建配置实例
        .withConversion(LogicalProject.class, Convention.NONE, // 指定输入类型为LogicalProject，约定为NONE
            GeodeRel.CONVENTION, "GeodeProjectRule") // 指定输出约定为GeodeRel.CONVENTION，规则名称
        .withRuleFactory(GeodeProjectRule::new) // 指定规则工厂方法
        .toRule(GeodeProjectRule.class); // 转换为规则实例

    // 构造函数：接收配置对象并初始化规则
    // 参数config：规则配置对象
    protected GeodeProjectRule(Config config) { // 构造函数
      super(config); // 调用父类构造函数
    } // 构造函数结束

    // 方法功能说明：判断该规则是否匹配给定的规则调用
    // 该方法用于过滤掉不支持的投影操作
    // 参数call：规则调用对象，包含待转换的关系表达式
    // 返回：如果规则匹配返回true，否则返回false
    @Override public boolean matches(RelOptRuleCall call) { // 重写匹配判断方法
      // 获取第一个关系表达式（LogicalProject）
      LogicalProject project = call.rel(0); // 获取投影节点
      // 遍历投影中的所有表达式
      for (RexNode e : project.getProjects()) { // 遍历投影表达式列表
        // 检查表达式类型是否为GEOMETRY（几何类型）
        if (e.getType().getSqlTypeName() == SqlTypeName.GEOMETRY) { // 如果是几何类型
          // 几何类型函数不支持下推到Geode，需要回退到Calcite的Enumerable实现
          return false; // 返回false，规则不匹配
        } // if结束
      } // for结束
      // 检查投影是否包含变量（用于高级功能如窗口函数）
      // Geode不支持变量，所以必须为空
      return project.getVariablesSet().isEmpty(); // 返回变量集是否为空
    } // 方法结束

    // 方法功能说明：执行实际的转换，将LogicalProject转换为GeodeProject
    // 参数rel：待转换的关系表达式（LogicalProject）
    // 返回：转换后的GeodeProject节点
    @Override public RelNode convert(RelNode rel) { // 重写转换方法
      // 将输入转换为LogicalProject类型
      final LogicalProject project = (LogicalProject) rel; // 强制类型转换
      // 检查变量集是否为空，Geode不支持变量
      checkArgument(project.getVariablesSet().isEmpty(), // 前置条件检查
          "GeodeProject does now allow variables"); // 错误消息
      // 创建新的特征集，将约定替换为Geode约定
      final RelTraitSet traitSet = // 创建特征集
          project.getTraitSet().replace(getOutConvention()); // 替换约定
      // 创建并返回GeodeProject节点
      return new GeodeProject( // 创建GeodeProject实例
          project.getCluster(), // 传入集群信息
          traitSet, // 传入特征集
          convert(project.getInput(), getOutConvention()), // 转换输入节点为Geode约定
          project.getProjects(), // 传入投影表达式列表
          project.getRowType()); // 传入输出行类型
    } // 方法结束
  } // 内部类结束

  /**
   * Rule to convert {@link org.apache.calcite.rel.core.Aggregate} to a
   * {@link GeodeAggregate}.
   */
  // 内部类功能说明：将逻辑聚合（LogicalAggregate）转换为Geode聚合（GeodeAggregate）的规则
  // 聚合操作对应SQL中的GROUP BY和聚合函数（SUM、COUNT、AVG等）
  // 该规则负责将聚合操作下推到Geode执行
  private static class GeodeAggregateRule extends GeodeConverterRule { // 继承GeodeConverterRule
    // 规则的单例实例，使用配置模式创建
    // 将LogicalAggregate（无约定）转换为GeodeAggregate（Geode约定）
    private static final GeodeAggregateRule INSTANCE = Config.INSTANCE // 创建配置实例
        .withConversion(LogicalAggregate.class, Convention.NONE, // 指定输入类型为LogicalAggregate，约定为NONE
            GeodeRel.CONVENTION, "GeodeAggregateRule") // 指定输出约定为GeodeRel.CONVENTION，规则名称
        .withRuleFactory(GeodeAggregateRule::new) // 指定规则工厂方法
        .toRule(GeodeAggregateRule.class); // 转换为规则实例

    // 构造函数：接收配置对象并初始化规则
    // 参数config：规则配置对象
    protected GeodeAggregateRule(Config config) { // 构造函数
      super(config); // 调用父类构造函数
    } // 构造函数结束

    // 方法功能说明：执行实际的转换，将LogicalAggregate转换为GeodeAggregate
    // 参数rel：待转换的关系表达式（LogicalAggregate）
    // 返回：转换后的GeodeAggregate节点
    @Override public RelNode convert(RelNode rel) { // 重写转换方法
      // 将输入转换为LogicalAggregate类型
      final LogicalAggregate aggregate = (LogicalAggregate) rel; // 强制类型转换
      // 创建新的特征集，将约定替换为Geode约定
      final RelTraitSet traitSet = // 创建特征集
          aggregate.getTraitSet().replace(getOutConvention()); // 替换约定
      // 创建并返回GeodeAggregate节点
      return new GeodeAggregate( // 创建GeodeAggregate实例
          aggregate.getCluster(), // 传入集群信息
          traitSet, // 传入特征集
          convert(aggregate.getInput(), traitSet.simplify()), // 转换输入节点并简化特征集
          aggregate.getGroupSet(), // 传入分组字段集合
          aggregate.getGroupSets(), // 传入分组集合列表（用于GROUPING SETS）
          aggregate.getAggCallList()); // 传入聚合函数调用列表
    } // 方法结束
  } // 内部类结束

  /**
   * Rule to convert the Limit in {@link org.apache.calcite.rel.core.Sort} to a
   * {@link GeodeSort}.
   */
  // 内部类功能说明：将Sort中的Limit部分转换为GeodeSort的规则
  // Sort操作对应SQL中的ORDER BY和LIMIT/OFFSET
  // 注意：Geode OQL不支持OFFSET，所以该规则只处理没有OFFSET的情况
  public static class GeodeSortLimitRule // 公共静态内部类
      extends RelRule<GeodeSortLimitRule.GeodeSortLimitRuleConfig> { // 继承RelRule，使用配置接口

    // 规则的单例实例，使用Immutables构建器模式创建
    private static final GeodeSortLimitRule INSTANCE = // 创建规则实例
        ImmutableGeodeSortLimitRuleConfig.builder() // 创建配置构建器
            .withOperandSupplier(b -> // 配置操作数提供者
                b.operand(Sort.class) // 指定操作数为Sort类型
                    // 谓词：只匹配没有OFFSET的Sort节点
                    // Geode OQL不支持OFFSET语法（如LIMIT 10 OFFSET 500）
                    .predicate(sort -> sort.offset == null) // 只处理offset为null的情况
                    .anyInputs()) // 接受任意输入
            .build() // 构建配置对象
            .toRule(); // 转换为规则实例

    /** Creates a GeodeSortLimitRule. */
    // 构造函数：创建GeodeSortLimitRule实例
    // 参数config：规则配置对象
    protected GeodeSortLimitRule(GeodeSortLimitRuleConfig config) { // 构造函数
      super(config); // 调用父类构造函数
    } // 构造函数结束

    // 方法功能说明：当规则匹配时执行转换
    // 参数call：规则调用对象，包含待转换的Sort节点
    @Override public void onMatch(RelOptRuleCall call) { // 重写匹配处理方法
      // 获取Sort节点（第一个关系表达式）
      final Sort sort = call.rel(0); // 获取Sort节点

      // 创建新的特征集，将约定替换为Geode约定，并保留排序规则
      final RelTraitSet traitSet = sort.getTraitSet() // 获取原特征集
          .replace(GeodeRel.CONVENTION) // 替换为Geode约定
          .replace(sort.getCollation()); // 保留排序规则

      // 创建GeodeSort节点
      // 注意：输入节点的排序规则被替换为空，因为GeodeSort会重新应用排序
      GeodeSort geodeSort = // 创建GeodeSort实例
          new GeodeSort(sort.getCluster(), traitSet, // 传入集群信息和特征集
              convert(call.getPlanner(), sort.getInput(), traitSet.replace(RelCollations.EMPTY)), // 转换输入节点，清空排序规则
              sort.getCollation(), sort.fetch); // 传入排序规则和fetch（LIMIT值）

      // 将转换结果通知给优化器
      call.transformTo(geodeSort); // 转换为GeodeSort节点
    } // 方法结束

    /** Rule configuration. */
    // 配置接口：定义GeodeSortLimitRule的配置
    @Value.Immutable(singleton = false) // 使用Immutables生成不可变配置类
    public interface GeodeSortLimitRuleConfig extends RelRule.Config { // 继承RelRule.Config接口
      @Override default GeodeSortLimitRule toRule() { // 默认方法：将配置转换为规则实例
        return new GeodeSortLimitRule(this); // 创建并返回规则实例
      } // 方法结束
    } // 接口结束
  } // 内部类结束

  /**
   * Rule to convert a {@link LogicalFilter} to a
   * {@link GeodeFilter}.
   */
  // 内部类功能说明：将逻辑过滤器（LogicalFilter）转换为Geode过滤器（GeodeFilter）的规则
  // 过滤操作对应SQL中的WHERE子句
  // 该规则负责判断哪些过滤条件可以下推到Geode执行
  public static class GeodeFilterRule // 公共静态内部类
      extends RelRule<GeodeFilterRule.GeodeFilterRuleConfig> { // 继承RelRule，使用配置接口

    // 规则的单例实例，使用Immutables构建器模式创建
    private static final GeodeFilterRule INSTANCE = // 创建规则实例
        ImmutableGeodeFilterRuleConfig.builder() // 创建配置构建器
            .withOperandSupplier(b0 -> // 配置操作数提供者
                b0.operand(LogicalFilter.class).oneInput(b1 -> // LogicalFilter有一个输入
                    b1.operand(GeodeTableScan.class).noInputs())) // 输入必须是GeodeTableScan，且没有子节点
            .build() // 构建配置对象
            .toRule(); // 转换为规则实例

    /** Creates a GeodeFilterRule. */
    // 构造函数：创建GeodeFilterRule实例
    // 参数config：规则配置对象
    protected GeodeFilterRule(GeodeFilterRuleConfig config) { // 构造函数
      super(config); // 调用父类构造函数
    } // 构造函数结束

    // 方法功能说明：判断该规则是否匹配给定的规则调用
    // 该方法用于过滤掉不支持的过滤条件
    // 参数call：规则调用对象，包含待转换的LogicalFilter节点
    // 返回：如果规则匹配返回true，否则返回false
    @Override public boolean matches(RelOptRuleCall call) { // 重写匹配判断方法
      // Get the condition from the filter operation
      // 获取LogicalFilter节点（第一个关系表达式）
      LogicalFilter filter = call.rel(0); // 获取过滤器节点
      // 获取过滤条件（WHERE子句对应的表达式）
      RexNode condition = filter.getCondition(); // 获取条件表达式

      // 获取输入节点的字段名称列表
      List<String> fieldNames = GeodeRules.geodeFieldNames(filter.getInput().getRowType()); // 获取字段名列表

      // 将条件分解为OR连接的多个子条件（析取范式）
      List<RexNode> disjunctions = RelOptUtil.disjunctions(condition); // 获取OR连接的条件列表
      // 如果有多个OR条件，允许匹配（Geode支持OR）
      if (disjunctions.size() != 1) { // 如果OR条件数量不为1
        return true; // 返回true，规则匹配
      // 如果只有一个条件（没有OR），需要进一步检查
      } else { // else分支
        // Check that all conjunctions are primary field conditions.
        // 获取唯一的条件
        condition = disjunctions.get(0); // 获取第一个（也是唯一的）条件
        // 将条件分解为AND连接的多个子条件
        for (RexNode predicate : RelOptUtil.conjunctions(condition)) { // 遍历AND连接的条件列表
          // 检查每个AND条件是否都是支持的主键条件
          if (!isEqualityOnKey(predicate, fieldNames)) { // 如果不是支持的等值条件
            return false; // 返回false，规则不匹配
          } // if结束
        } // for结束
      } // else结束

      // 所有条件都通过检查，规则匹配
      return true; // 返回true，规则匹配
    } // 方法结束

    /**
     * Check if the node is a supported predicate (primary field condition).
     *
     * @param node       Condition node to check
     * @param fieldNames Names of all columns in the table
     * @return True if the node represents an equality predicate on a primary key
     */
    // 方法功能说明：检查节点是否是支持的主键条件
    // 支持的条件包括：布尔列引用、等值比较、IN操作等
    // 参数node：待检查的条件节点
    // 参数fieldNames：表中所有列的名称列表
    // 返回：如果是支持的条件返回true，否则返回false
    private static boolean isEqualityOnKey(RexNode node, List<String> fieldNames) { // 私有静态方法

      // 检查是否是布尔列引用（如"WHERE active"）
      if (isBooleanColumnReference(node, fieldNames)) { // 如果是布尔列引用
        return true; // 返回true，支持
      } // if结束

      // 检查是否是比较操作符（=、<、>等）或SEARCH操作符（IN）
      if (!SqlKind.COMPARISON.contains(node.getKind()) // 如果不是比较操作符
          && node.getKind() != SqlKind.SEARCH) { // 且不是SEARCH操作符
        return false; // 返回false，不支持
      } // if结束

      // 将节点转换为函数调用
      RexCall call = (RexCall) node; // 强制类型转换
      // 获取左操作数
      final RexNode left = call.operands.get(0); // 获取第一个操作数
      // 获取右操作数
      final RexNode right = call.operands.get(1); // 获取第二个操作数

      // 检查左操作数是否是输入引用且右操作数是字面量（如"WHERE id = 1"）
      if (checkConditionContainsInputRefOrLiterals(left, right, fieldNames)) { // 如果满足条件
        return true; // 返回true，支持
      } // if结束
      // 检查右操作数是否是输入引用且左操作数是字面量（如"WHERE 1 = id"）
      return checkConditionContainsInputRefOrLiterals(right, left, fieldNames); // 返回检查结果

    } // 方法结束

    // 方法功能说明：检查节点是否是布尔列引用
    // 布尔列引用是指直接使用布尔列名作为条件，如"WHERE active"
    // 参数node：待检查的节点
    // 参数fieldNames：表中所有列的名称列表
    // 返回：如果是布尔列引用返回true，否则返回false
    private static boolean isBooleanColumnReference(RexNode node, List<String> fieldNames) { // 私有静态方法
      // FIXME Ignore casts for rel and assume they aren't really necessary
      // 剥离NOT、CAST、IS_NOT_NULL等包装操作符，获取底层的输入引用
      while (node.isA(ImmutableList.of(SqlKind.NOT, SqlKind.CAST, SqlKind.IS_NOT_NULL))) { // 循环剥离包装操作符
        node = ((RexCall) node).getOperands().get(0); // 获取第一个操作数
      } // while结束
      // 检查是否是输入引用
      if (node.isA(SqlKind.INPUT_REF)) { // 如果是输入引用
        // 检查数据类型是否是布尔类型
        if (node.getType().getSqlTypeName() == SqlTypeName.BOOLEAN) { // 如果是布尔类型
          // 获取输入引用
          final RexInputRef left1 = (RexInputRef) node; // 强制类型转换
          // 根据索引获取字段名
          String name = fieldNames.get(left1.getIndex()); // 获取字段名
          // 检查字段名是否存在
          return name != null; // 返回字段名是否不为null
        } // if结束
      } // if结束
      // 不是布尔列引用
      return false; // 返回false
    } // 方法结束

    /**
     * Checks whether a condition contains input refs of literals.
     *
     * @param left       Left operand of the equality
     * @param right      Right operand of the equality
     * @param fieldNames Names of all columns in the table
     * @return Whether condition is supported
     */
    // 方法功能说明：检查条件是否包含输入引用或字面量
    // 支持的模式包括：列=字面量、列=列、item['field']=字面量
    // 参数left：等式的左操作数
    // 参数right：等式的右操作数
    // 参数fieldNames：表中所有列的名称列表
    // 返回：如果条件支持返回true，否则返回false
    private static boolean checkConditionContainsInputRefOrLiterals(RexNode left, // 私有静态方法
        RexNode right, List<String> fieldNames) { // 参数列表
      // FIXME Ignore casts for rel and assume they aren't really necessary
      // 剥离左操作数的CAST包装
      if (left.isA(SqlKind.CAST)) { // 如果左操作数是CAST
        left = ((RexCall) left).getOperands().get(0); // 获取CAST的第一个操作数
      } // if结束

      // 剥离右操作数的CAST包装
      if (right.isA(SqlKind.CAST)) { // 如果右操作数是CAST
        right = ((RexCall) right).getOperands().get(0); // 获取CAST的第一个操作数
      } // if结束

      // 检查是否是"列 = 字面量"模式（如"WHERE id = 1"）
      if (left.isA(SqlKind.INPUT_REF) && right.isA(SqlKind.LITERAL)) { // 如果左是输入引用，右是字面量
        // 获取输入引用
        final RexInputRef left1 = (RexInputRef) left; // 强制类型转换
        // 根据索引获取字段名
        String name = fieldNames.get(left1.getIndex()); // 获取字段名
        // 检查字段名是否存在
        return name != null; // 返回字段名是否不为null
      // 检查是否是"列 = 列"模式（如"WHERE a = b"）
      } else if (left.isA(SqlKind.INPUT_REF) && right.isA(SqlKind.INPUT_REF)) { // 如果两边都是输入引用

        // 获取左边的输入引用
        final RexInputRef left1 = (RexInputRef) left; // 强制类型转换
        // 获取左边的字段名
        String leftName = fieldNames.get(left1.getIndex()); // 获取字段名

        // 获取右边的输入引用（注意：这里有个bug，应该是RexInputRef而不是RexNode）
        final RexInputRef right1 = (RexInputRef) right; // 强制类型转换
        // 获取右边的字段名
        String rightName = fieldNames.get(right1.getIndex()); // 获取字段名

        // 检查两个字段名是否都存在
        return (leftName != null) && (rightName != null); // 返回两个字段名是否都不为null
      // 检查是否是"item['field'] = 字面量"模式（如"WHERE item['name'] = 'John'"）
      } else if (left.isA(SqlKind.ITEM) && right.isA(SqlKind.LITERAL)) { // 如果左是ITEM，右是字面量
        return true; // 返回true，支持
      } // if结束

      // 其他模式不支持
      return false; // 返回false
    } // 方法结束

    // 方法功能说明：当规则匹配时执行转换
    // 参数call：规则调用对象，包含待转换的LogicalFilter节点
    @Override public void onMatch(RelOptRuleCall call) { // 重写匹配处理方法
      // 获取LogicalFilter节点
      LogicalFilter filter = call.rel(0); // 获取过滤器节点
      // 检查特征集是否包含NONE约定（表示这是逻辑节点）
      if (filter.getTraitSet().contains(Convention.NONE)) { // 如果包含NONE约定
        // 执行转换
        final RelNode converted = convert(filter); // 转换为GeodeFilter
        // 将转换结果通知给优化器
        call.transformTo(converted); // 转换为GeodeFilter节点
      } // if结束
    } // 方法结束

    // 方法功能说明：执行实际的转换，将LogicalFilter转换为GeodeFilter
    // 参数filter：待转换的LogicalFilter节点
    // 返回：转换后的GeodeFilter节点
    private static RelNode convert(LogicalFilter filter) { // 私有静态方法
      // 创建新的特征集，将约定替换为Geode约定
      final RelTraitSet traitSet = filter.getTraitSet().replace(GeodeRel.CONVENTION); // 替换约定
      // 创建并返回GeodeFilter节点
      return new GeodeFilter( // 创建GeodeFilter实例
          filter.getCluster(), // 传入集群信息
          traitSet, // 传入特征集
          convert(filter.getInput(), GeodeRel.CONVENTION), // 转换输入节点为Geode约定
          filter.getCondition()); // 传入过滤条件
    } // 方法结束

    /** Rule configuration. */
    // 配置接口：定义GeodeFilterRule的配置
    @Value.Immutable(singleton = false) // 使用Immutables生成不可变配置类
    public interface GeodeFilterRuleConfig extends RelRule.Config { // 继承RelRule.Config接口
      @Override default GeodeFilterRule toRule() { // 默认方法：将配置转换为规则实例
        return new GeodeFilterRule(this); // 创建并返回规则实例
      } // 方法结束
    } // 接口结束
  } // 内部类结束

  /**
   * Base class for planner rules that convert a relational
   * expression to Geode calling convention.
   */
  // 内部类功能说明：Geode转换规则的基类
  // 所有将关系表达式转换为Geode约定的规则都继承此类
  // 该类继承自ConverterRule，提供了转换规则的基本框架
  abstract static class GeodeConverterRule extends ConverterRule { // 抽象静态内部类，继承ConverterRule
    // 构造函数：接收配置对象并初始化规则
    // 参数config：规则配置对象
    protected GeodeConverterRule(Config config) { // 构造函数
      super(config); // 调用父类构造函数
    } // 构造函数结束
  } // 内部类结束
} // 类结束
