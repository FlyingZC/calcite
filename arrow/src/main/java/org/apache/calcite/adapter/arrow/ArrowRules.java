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
// Apache许可证声明，说明此代码遵循Apache 2.0许可证
package org.apache.calcite.adapter.arrow; // 定义包名，表示这是Calcite Arrow适配器包

import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 导入可枚举约定接口，用于定义可枚举的RelNode特征集
import org.apache.calcite.plan.Convention; // 导入约定接口，用于定义RelNode的调用约定（如物理实现方式）
import org.apache.calcite.plan.RelOptRule; // 导入优化规则基类，所有优化规则都继承此类
import org.apache.calcite.plan.RelOptRuleCall; // 导入规则调用类，封装规则匹配时的上下文信息
import org.apache.calcite.plan.RelRule; // 导入规则基类，提供基于配置的规则实现框架
import org.apache.calcite.plan.RelTraitSet; // 导入特征集类，表示RelNode的一组特征（如约定、排序等）
import org.apache.calcite.rel.RelNode; // 导入关系表达式接口，所有关系代数节点都实现此接口
import org.apache.calcite.rel.convert.ConverterRule; // 导入转换规则基类，用于在不同约定之间转换RelNode
import org.apache.calcite.rel.core.Filter; // 导入过滤器关系节点，表示过滤操作（WHERE子句）
import org.apache.calcite.rel.core.Project; // 导入投影关系节点，表示投影操作（SELECT子句）
import org.apache.calcite.rel.logical.LogicalFilter; // 导入逻辑过滤器，表示逻辑层的过滤操作
import org.apache.calcite.rel.logical.LogicalProject; // 导入逻辑投影，表示逻辑层的投影操作
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型，描述行的结构（字段名和类型）
import org.apache.calcite.sql.validate.SqlValidatorUtil; // 导入SQL验证工具类，提供字段名去重等功能

import com.google.common.collect.ImmutableList; // 导入不可变列表类，用于创建不可修改的列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的值
import org.immutables.value.Value; // 导入不可变值注解，用于生成不可变配置类

import java.util.List; // 导入List接口，用于集合操作

/** Planner rules relating to the Arrow adapter. */
// 类作用：ArrowRules是Arrow适配器的优化规则集合类，包含了所有与Arrow适配器相关的优化规则
// 这个类定义了如何将逻辑操作转换为Arrow特定的物理实现，以及如何将Arrow操作转换为可枚举操作
public class ArrowRules {
  private ArrowRules() {} // 私有构造方法，防止实例化，因为这是一个工具类，只包含静态成员

  /** Rule that matches a {@link org.apache.calcite.rel.core.Project} on
   * an {@link ArrowTableScan} and pushes down projects if possible. */
  // 成员变量作用：PROJECT_SCAN是一个静态的投影规则实例，用于将逻辑投影操作推送到ArrowTableScan中
  // 这个规则优化查询性能，通过只读取需要的列来减少数据传输量
  public static final ArrowProjectRule PROJECT_SCAN =
      ArrowProjectRule.DEFAULT_CONFIG.toRule(ArrowProjectRule.class); // 使用默认配置创建投影规则实例

  public static final ArrowFilterRule FILTER_SCAN =
      ArrowFilterRule.Config.DEFAULT.toRule(); // 成员变量作用：FILTER_SCAN是过滤规则实例，用于将逻辑过滤操作转换为Arrow过滤器

  public static final ConverterRule TO_ENUMERABLE =
      ArrowToEnumerableConverterRule.DEFAULT_CONFIG
          .toRule(ArrowToEnumerableConverterRule.class); // 成员变量作用：TO_ENUMERABLE是转换规则，用于将Arrow约定转换为可枚举约定，使结果可以被迭代

  public static final List<RelOptRule> RULES = ImmutableList.of(PROJECT_SCAN, FILTER_SCAN);
  // 成员变量作用：RULES是所有优化规则的列表，包含投影下推规则和过滤下推规则，供优化器注册和使用

  static List<String> arrowFieldNames(final RelDataType rowType) {
    // 方法作用：arrowFieldNames是一个静态工具方法，用于从关系数据类型中提取字段名列表，并确保字段名唯一
    // 参数说明：rowType表示行的数据类型，包含字段名和类型信息
    // 返回值：返回去重后的字段名列表
    return SqlValidatorUtil.uniquify(rowType.getFieldNames(), // 获取所有字段名
        SqlValidatorUtil.EXPR_SUGGESTER, true); // 使用表达式建议器对重复字段名进行重命名，true表示严格模式
  }

  /** Base class for planner rules that convert a relational expression to
   * the Arrow calling convention. */
  // 内部类作用：ArrowConverterRule是转换规则的抽象基类，用于将关系表达式转换为Arrow约定
  // 这个基类封装了从通用约定到Arrow约定的转换逻辑，子类只需实现具体的转换细节
  abstract static class ArrowConverterRule extends ConverterRule {
    ArrowConverterRule(Config config) { // 构造方法作用：初始化转换规则，接收配置对象
      super(config); // 调用父类ConverterRule的构造方法，传入配置
    }
  }

  /**
   * Rule to convert a {@link org.apache.calcite.rel.core.Filter} to an
   * {@link ArrowFilter}.
   */
  // 内部类作用：ArrowFilterRule是将逻辑过滤器转换为Arrow过滤器的优化规则
  // 这个规则实现了过滤下推优化，将过滤条件尽可能推到数据源端执行，减少处理的数据量
  public static class ArrowFilterRule extends RelRule<ArrowFilterRule.Config> {

    /** Creates an ArrowFilterRule. */
    // 构造方法作用：创建ArrowFilterRule实例，初始化规则配置
    // 参数说明：config是规则的配置对象，包含匹配条件和转换逻辑
    protected ArrowFilterRule(Config config) {
      super(config); // 调用父类RelRule的构造方法，传入配置
    }

    @Override public void onMatch(RelOptRuleCall call) {
      // 方法作用：onMatch是规则匹配成功时的回调方法，执行实际的转换逻辑
      // 参数说明：call是规则调用对象，包含匹配到的关系节点和转换上下文
      final Filter filter = call.rel(0); // 从调用对象中获取第一个关系节点（即逻辑过滤器）

      if (filter.getTraitSet().contains(Convention.NONE)) { // 检查过滤器的特征集是否包含NONE约定（逻辑约定）
        try {
          final RelNode converted = convert(filter); // 尝试将逻辑过滤器转换为Arrow过滤器
          call.transformTo(converted); // 将转换后的节点注册到优化器，替换原节点
        } catch (UnsupportedOperationException e) { // 捕获不支持的操作异常
          // skip rule application when hitting an unsupported feature,
          // allowing a plan in the Enumerable convention to be generated
          // 异常处理说明：当遇到不支持的特性时跳过此规则，允许生成可枚举约定的计划作为备选方案
        }
      }
    }

    RelNode convert(Filter filter) {
      // 方法作用：convert执行实际的转换逻辑，将逻辑过滤器转换为Arrow过滤器
      // 参数说明：filter是待转换的逻辑过滤器节点
      // 返回值：返回转换后的Arrow过滤器节点
      final RelTraitSet traitSet =
          filter.getTraitSet().replace(ArrowRel.CONVENTION); // 创建新的特征集，将约定替换为Arrow约定
      return new ArrowFilter(filter.getCluster(), traitSet, // 创建ArrowFilter实例，传入集群、特征集
          convert(filter.getInput(), ArrowRel.CONVENTION), // 递归转换输入节点为Arrow约定
          filter.getCondition()); // 保留过滤条件
    }

    /** Rule configuration. */
    // 内部接口作用：Config是规则的配置接口，使用不可变值模式定义规则配置
    // 这个接口定义了规则的匹配条件和规则工厂方法
    @Value.Immutable
    public interface Config extends RelRule.Config { // 继承RelRule.Config接口，复用基础配置
      Config DEFAULT = ImmutableConfig.builder() // 创建默认配置实例
          .withOperandSupplier(b0 -> // 配置操作数提供者，定义规则的匹配模式
              b0.operand(LogicalFilter.class).oneInput(b1 -> // 匹配LogicalFilter节点，且有一个输入
                  b1.operand(ArrowTableScan.class).noInputs())) // 输入是ArrowTableScan节点，且没有更多输入
          .build(); // 构建配置对象

      @Override default ArrowFilterRule toRule() { // 默认方法：创建规则实例
        return new ArrowFilterRule(this); // 使用当前配置创建ArrowFilterRule实例
      }
    }
  }

  /**
   * Planner rule that projects from an {@link ArrowTableScan} just the columns
   * needed to satisfy a projection. If the projection's expressions are
   * trivial, the projection is removed.
   *
   * @see ArrowRules#PROJECT_SCAN
   */
  // 内部类作用：ArrowProjectRule是投影下推规则，从ArrowTableScan中只读取满足投影所需的列
  // 这个规则优化查询性能，通过列剪裁减少数据读取量；如果投影表达式是简单的字段引用，则移除投影节点
  public static class ArrowProjectRule extends ArrowConverterRule {

    /** Default configuration. */
    // 成员变量作用：DEFAULT_CONFIG是投影规则的默认配置，定义了从逻辑投影到Arrow约定的转换规则
    protected static final Config DEFAULT_CONFIG = Config.INSTANCE
        .withConversion(LogicalProject.class, Convention.NONE, // 配置转换源：逻辑投影，约定为NONE
            ArrowRel.CONVENTION, "ArrowProjectRule") // 配置转换目标：Arrow约定，规则名为"ArrowProjectRule"
        .withRuleFactory(ArrowProjectRule::new); // 配置规则工厂，使用构造函数引用创建规则实例

    /** Creates an ArrowProjectRule. */
    // 构造方法作用：创建ArrowProjectRule实例，初始化投影转换规则
    // 参数说明：config是规则的配置对象
    protected ArrowProjectRule(Config config) {
      super(config); // 调用父类ArrowConverterRule的构造方法，传入配置
    }

    @Override public @Nullable RelNode convert(RelNode rel) {
      // 方法作用：convert执行投影转换，将逻辑投影转换为Arrow投影
      // 参数说明：rel是待转换的关系节点（逻辑投影）
      // 返回值：返回转换后的Arrow投影节点，如果无法转换则返回null
      final Project project = (Project) rel; // 将关系节点强转为Project类型
      @Nullable List<Integer> fields =
          ArrowProject.getProjectFields(project.getProjects()); // 提取投影表达式中引用的字段索引
      if (fields == null) { // 如果fields为null，说明投影表达式包含复杂表达式（不仅仅是字段引用）
        // Project contains expressions more complex than just field references.
        // 注释说明：投影包含比字段引用更复杂的表达式，无法下推
        return null; // 返回null表示无法转换，放弃此规则
      }
      final RelTraitSet traitSet =
          project.getTraitSet().replace(ArrowRel.CONVENTION); // 创建新的特征集，将约定替换为Arrow约定
      return new ArrowProject(project.getCluster(), traitSet, // 创建ArrowProject实例，传入集群、特征集
          convert(project.getInput(), ArrowRel.CONVENTION), // 递归转换输入节点为Arrow约定
          project.getProjects(), project.getRowType()); // 保留投影表达式和行类型
    }
  }

  /**
   * Rule to convert a relational expression from
   * {@link ArrowRel#CONVENTION} to {@link EnumerableConvention}.
   */
  // 内部类作用：ArrowToEnumerableConverterRule是将Arrow约定转换为可枚举约定的转换规则
  // 这个规则是优化器的最后一个阶段，将Arrow物理计划转换为可执行的Java迭代器形式
  static class ArrowToEnumerableConverterRule extends ConverterRule {

    /** Default configuration. */
    // 成员变量作用：DEFAULT_CONFIG是转换规则的默认配置，定义了从Arrow约定到可枚举约定的转换
    public static final Config DEFAULT_CONFIG = Config.INSTANCE
        .withConversion(RelNode.class, ArrowRel.CONVENTION, // 配置转换源：任意关系节点，约定为Arrow
            EnumerableConvention.INSTANCE, "ArrowToEnumerableConverterRule") // 配置转换目标：可枚举约定
        .withRuleFactory(ArrowToEnumerableConverterRule::new); // 配置规则工厂，使用构造函数引用创建规则实例

    /** Creates an ArrowToEnumerableConverterRule. */
    // 构造方法作用：创建ArrowToEnumerableConverterRule实例，初始化转换规则
    // 参数说明：config是规则的配置对象
    protected ArrowToEnumerableConverterRule(Config config) {
      super(config); // 调用父类ConverterRule的构造方法，传入配置
    }

    @Override public RelNode convert(RelNode rel) {
      // 方法作用：convert执行约定转换，将Arrow约定的关系节点转换为可枚举约定
      // 参数说明：rel是待转换的Arrow约定关系节点
      // 返回值：返回转换后的可枚举约定关系节点
      RelTraitSet newTraitSet = rel.getTraitSet().replace(getOutConvention()); // 创建新的特征集，替换为目标约定
      return new ArrowToEnumerableConverter(rel.getCluster(), newTraitSet, rel); // 创建转换器节点，封装原节点
    }
  }
}
