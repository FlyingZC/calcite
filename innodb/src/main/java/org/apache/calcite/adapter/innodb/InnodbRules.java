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
// Apache许可证声明，定义了代码的使用权限和限制
package org.apache.calcite.adapter.innodb; // 声明当前类所属的包，位于Calcite框架的InnoDB适配器模块中

import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 导入可枚举约定，用于表示可以枚举的结果集
import org.apache.calcite.plan.Convention; // 导入约定接口，定义关系代数操作的调用约定
import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群，包含共享的优化器状态
import org.apache.calcite.plan.RelOptRule; // 导入关系优化规则基类
import org.apache.calcite.plan.RelOptRuleCall; // 导入规则调用对象，包含规则匹配时的上下文信息
import org.apache.calcite.plan.RelRule; // 导入关系规则基类，使用配置对象模式
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合，定义关系的物理属性
import org.apache.calcite.rel.RelCollation; // 导入排序规则，定义字段的排序顺序和方向
import org.apache.calcite.rel.RelCollations; // 导入排序规则工具类，提供创建和操作排序规则的静态方法
import org.apache.calcite.rel.RelFieldCollation; // 导入字段排序规则，定义单个字段的排序方向和空值处理
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，所有关系操作的基类
import org.apache.calcite.rel.convert.ConverterRule; // 导入转换规则基类，用于将关系表达式从一种约定转换为另一种
import org.apache.calcite.rel.core.Sort; // 导入排序关系节点，表示排序操作
import org.apache.calcite.rel.logical.LogicalFilter; // 导入逻辑过滤节点，表示过滤操作
import org.apache.calcite.rel.logical.LogicalProject; // 导入逻辑投影节点，表示字段投影操作
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型，描述结果集的类型信息
import org.apache.calcite.rex.RexInputRef; // 导入行表达式输入引用，引用输入行中的字段
import org.apache.calcite.rex.RexNode; // 导入行表达式节点基类，所有表达式的父接口
import org.apache.calcite.rex.RexUtil; // 导入行表达式工具类，提供表达式操作的静态方法
import org.apache.calcite.rex.RexVisitorImpl; // 导入行表达式访问者实现类，用于遍历表达式树
import org.apache.calcite.sql.validate.SqlValidatorUtil; // 导入SQL验证器工具类，提供SQL验证相关的静态方法

import com.alibaba.innodb.java.reader.schema.TableDef; // 导入阿里巴巴InnoDB Java阅读器的表定义类，描述表的结构信息
import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类，提供线程安全的不可变集合

import org.immutables.value.Value; // 导入不可变值注解，用于生成不可变的配置对象

import java.util.List; // 导入Java标准库的List接口

/**
 * Rules and relational operators for {@link InnodbRel#CONVENTION}
 * calling convention.
 */
// 类注释：定义了InnoDB适配器的规则集合和关系操作符，专门用于处理InnoDB调用约定
// InnodbRel#CONVENTION表示InnoDB特有的调用约定，定义了如何在InnoDB数据源上执行查询
// 这个类包含了将逻辑关系表达式转换为InnoDB物理执行计划的所有规则
public class InnodbRules { // 类定义：InnoDB规则集合类，包含所有InnoDB适配器的优化规则
  private InnodbRules() { // 私有构造方法：防止实例化，因为这是一个工具类，只包含静态成员
  } // 构造方法体为空，确保该类不能被实例化

  /** Rule to convert a relational expression from
   * {@link InnodbRel#CONVENTION} to {@link EnumerableConvention}. */
  // 成员变量注释：定义将关系表达式从InnoDB约定转换为可枚举约定的规则
  // 这是优化的最后一步，将InnoDB特定的物理计划转换为Calcite可执行的可枚举结果集
  public static final InnodbToEnumerableConverterRule TO_ENUMERABLE = // 声明公共静态常量规则对象
      InnodbToEnumerableConverterRule.DEFAULT_CONFIG // 使用默认配置创建规则
          .toRule(InnodbToEnumerableConverterRule.class); // 将配置转换为规则实例

  /** Rule to convert a {@link org.apache.calcite.rel.logical.LogicalProject}
   * to a {@link InnodbProject}. */
  // 成员变量注释：定义将逻辑投影节点转换为InnoDB投影节点的规则
  // 逻辑投影表示字段选择和重命名，转换后可以在InnoDB数据源上直接执行
  public static final InnodbProjectRule PROJECT = // 声明公共静态常量规则对象
      InnodbProjectRule.DEFAULT_CONFIG.toRule(InnodbProjectRule.class); // 使用默认配置创建规则

  /** Rule to convert a {@link org.apache.calcite.rel.logical.LogicalFilter} to
   * a {@link InnodbFilter}. */
  // 成员变量注释：定义将逻辑过滤节点转换为InnoDB过滤节点的规则
  // 逻辑过滤表示WHERE条件，转换后可以将条件下推到InnoDB数据源执行，提高性能
  public static final InnodbFilterRule FILTER = // 声明公共静态常量规则对象
      InnodbFilterRule.InnodbFilterRuleConfig.DEFAULT.toRule(); // 使用默认配置创建规则

  /** Rule to convert a {@link org.apache.calcite.rel.core.Sort} with a
   * {@link org.apache.calcite.rel.core.Filter} to a
   * {@link InnodbSort}. */
  // 成员变量注释：定义将排序节点与过滤节点组合转换为InnoDB排序节点的规则
  // 当排序操作紧跟在过滤操作之后时，可以利用过滤后数据的隐式排序特性来优化排序
  public static final InnodbSortFilterRule SORT_FILTER = // 声明公共静态常量规则对象
      InnodbSortFilterRule.InnodbSortFilterRuleConfig.DEFAULT.toRule(); // 使用默认配置创建规则

  /** Rule to convert a {@link org.apache.calcite.rel.core.Sort} to a
   * {@link InnodbSort} based on InnoDB table clustering index. */
  // 成员变量注释：定义将排序节点转换为基于InnoDB表聚簇索引的排序节点的规则
  // InnoDB表按聚簇索引组织存储，如果查询的排序顺序与聚簇索引一致，可以避免额外的排序操作
  public static final InnodbSortTableScanRule SORT_SCAN = // 声明公共静态常量规则对象
      InnodbSortTableScanRule.InnodbSortTableScanRuleConfig.DEFAULT.toRule(); // 使用默认配置创建规则

  public static final List<RelOptRule> RULES = // 声明公共静态常量规则列表，包含所有InnoDB优化规则
      ImmutableList.of(PROJECT, // 创建不可变列表，包含投影规则
          FILTER, // 添加过滤规则
          SORT_FILTER, // 添加排序过滤组合规则
          SORT_SCAN); // 添加基于表扫描的排序规则

  static List<String> innodbFieldNames(final RelDataType rowType) { // 静态方法：从关系数据类型中提取并去重字段名称列表
    // 参数rowType：关系数据类型对象，包含结果集的字段信息
    // 返回值：去重后的字段名称列表，确保字段名唯一
    return SqlValidatorUtil.uniquify(rowType.getFieldNames(), // 获取字段名称列表并进行去重处理
        SqlValidatorUtil.EXPR_SUGGESTER, // 使用表达式建议器来生成唯一的字段名
        true); // 第三个参数true表示区分大小写，确保字段名的精确匹配
  } // 方法结束

  /** Translator from {@link RexNode} to strings in InnoDB's expression
   * language. */
  // 内部类注释：行表达式到InnoDB表达式语言的转换器
  // 这个类负责将Calcite的行表达式（RexNode）转换为InnoDB可以理解的字符串表达式
  // 继承自RexVisitorImpl，使用访问者模式遍历表达式树并生成对应的InnoDB表达式字符串
  static class RexToInnodbTranslator extends RexVisitorImpl<String> { // 类定义：行表达式到InnoDB的转换器
    private final List<String> inFields; // 成员变量：输入字段名称列表，用于将字段索引映射到字段名

    protected RexToInnodbTranslator(List<String> inFields) { // 构造方法：创建转换器实例
      super(true); // 调用父类构造方法，参数true表示深度优先遍历表达式树
      this.inFields = inFields; // 保存输入字段名称列表，用于后续字段引用的转换
    } // 构造方法结束

    @Override public String visitInputRef(RexInputRef inputRef) { // 重写方法：访问输入引用节点
      // 参数inputRef：输入引用节点，表示对输入行中某个字段的引用
      // 返回值：字段名称字符串，对应InnoDB表达式中的字段引用
      return inFields.get(inputRef.getIndex()); // 根据字段索引获取对应的字段名称
    } // 方法结束
  } // 内部类结束

  /**
   * Base class for planner rules that convert a relational expression to
   * Innodb calling convention.
   */
  // 内部类注释：InnoDB转换规则的基类
  // 这个抽象类为所有将关系表达式转换为InnoDB调用约定的规则提供公共基础
  // 继承自ConverterRule，专门用于处理约定转换的优化规则
  abstract static class InnodbConverterRule extends ConverterRule { // 类定义：InnoDB转换规则基类
    InnodbConverterRule(Config config) { // 构造方法：创建转换规则实例
      super(config); // 调用父类ConverterRule的构造方法，传入配置对象
    } // 构造方法结束
  } // 内部类结束

  /**
   * Rule to convert a {@link org.apache.calcite.rel.logical.LogicalProject}
   * to a {@link InnodbProject}.
   *
   * @see #PROJECT
   */
  // 内部类注释：逻辑投影到InnoDB投影的转换规则
  // 这个规则负责将逻辑投影节点转换为InnoDB投影节点，使投影操作可以在InnoDB数据源上执行
  // 只有当投影表达式都是简单的字段引用时才能转换，复杂表达式需要保持逻辑投影
  public static class InnodbProjectRule extends InnodbConverterRule { // 类定义：InnoDB投影规则
    /** Default configuration. */
    // 成员变量注释：默认配置对象，定义了规则的转换条件和行为
    private static final Config DEFAULT_CONFIG = Config.INSTANCE // 使用默认配置实例
        .withConversion(LogicalProject.class, Convention.NONE, // 设置转换源：逻辑投影节点，无约定
            InnodbRel.CONVENTION, "InnodbProjectRule") // 设置转换目标：InnoDB约定，规则名称
        .withRuleFactory(InnodbProjectRule::new); // 设置规则工厂，用于创建规则实例

    protected InnodbProjectRule(Config config) { // 构造方法：创建投影规则实例
      super(config); // 调用父类InnodbConverterRule的构造方法，传入配置对象
    } // 构造方法结束

    @Override public boolean matches(RelOptRuleCall call) { // 重写方法：检查规则是否匹配
      // 参数call：规则调用对象，包含匹配的上下文信息
      // 返回值：true表示规则可以匹配并应用，false表示不匹配
      LogicalProject project = call.rel(0); // 获取第一个关系节点，即逻辑投影节点
      for (RexNode e : project.getProjects()) { // 遍历投影中的所有表达式
        if (!(e instanceof RexInputRef)) { // 检查表达式是否为输入引用
          return false; // 如果不是输入引用，则不能转换，返回false
        } // if语句结束
      } // for循环结束
      return project.getVariablesSet().isEmpty(); // 检查变量集合是否为空，确保没有动态变量
    } // 方法结束

    @Override public RelNode convert(RelNode rel) { // 重写方法：执行转换操作
      // 参数rel：要转换的关系节点
      // 返回值：转换后的InnoDB投影节点
      final LogicalProject project = (LogicalProject) rel; // 将关系节点转换为逻辑投影节点
      final RelTraitSet traitSet = project.getTraitSet().replace(out); // 创建新的特征集合，替换为InnoDB约定
      return new InnodbProject(project.getCluster(), traitSet, // 创建InnoDB投影节点，传入集群和特征集合
          convert(project.getInput(), out), project.getProjects(), // 转换输入节点，传入投影表达式
          project.getRowType()); // 传入行类型信息
    } // 方法结束
  } // 内部类结束

  /**
   * Rule to convert a {@link org.apache.calcite.rel.logical.LogicalFilter} to a
   * {@link InnodbFilter}.
   *
   * @see #FILTER
   */
  // 内部类注释：逻辑过滤到InnoDB过滤的转换规则
  // 这个规则负责将逻辑过滤节点转换为InnoDB过滤节点，支持下推条件到InnoDB数据源
  // 通过分析过滤条件，将可以利用索引的部分条件下推，剩余条件保留在逻辑过滤中
  public static class InnodbFilterRule extends RelRule<InnodbFilterRule.InnodbFilterRuleConfig> { // 类定义：InnoDB过滤规则
    /** Creates a InnodbFilterRule. */
    // 构造方法注释：创建InnoDB过滤规则实例
    protected InnodbFilterRule(InnodbFilterRuleConfig config) { // 构造方法：创建过滤规则实例
      super(config); // 调用父类RelRule的构造方法，传入配置对象
    } // 构造方法结束

    @Override public void onMatch(RelOptRuleCall call) { // 重写方法：规则匹配时执行
      // 参数call：规则调用对象，包含匹配的节点信息
      // 返回值：无返回值，直接转换关系树
      LogicalFilter filter = call.rel(0); // 获取第一个关系节点，即逻辑过滤节点
      InnodbTableScan scan = call.rel(1); // 获取第二个关系节点，即InnoDB表扫描节点
      if (filter.getTraitSet().contains(Convention.NONE)) { // 检查过滤节点的特征集合是否包含无约定
        final RelNode converted = convert(filter, scan); // 调用转换方法，将过滤节点转换为InnoDB过滤节点
        call.transformTo(converted); // 将转换后的节点应用到关系树中
      } // if语句结束
    } // 方法结束

    RelNode convert(LogicalFilter filter, InnodbTableScan scan) { // 方法：执行过滤节点的转换
      // 参数filter：逻辑过滤节点，包含过滤条件
      // 参数scan：InnoDB表扫描节点，提供表定义和索引信息
      // 返回值：转换后的关系节点，可能是InnoDB过滤节点或逻辑过滤节点
      final RelTraitSet traitSet = filter.getTraitSet().replace(InnodbRel.CONVENTION); // 创建新的特征集合，替换为InnoDB约定

      final TableDef tableDef = scan.innodbTable.getTableDef(); // 获取InnoDB表的定义对象，包含表结构信息
      final RelOptCluster cluster = filter.getCluster(); // 获取优化集群，包含共享的优化器状态
      final InnodbFilterTranslator translator = // 创建InnoDB过滤条件转换器
          new InnodbFilterTranslator(cluster.getRexBuilder(), // 传入行表达式构建器
              filter.getRowType(), tableDef, scan.getForceIndexName()); // 传入行类型、表定义和强制索引名
      final IndexCondition indexCondition = // 调用转换器，将过滤条件转换为索引条件
          translator.translateMatch(filter.getCondition()); // 传入过滤条件，分析哪些部分可以利用索引

      RexNode condition = // 创建行表达式节点，表示可以下推的过滤条件
          RexUtil.composeConjunction(cluster.getRexBuilder(), // 使用行表达式构建器组合多个条件
              indexCondition.getPushDownConditions()); // 传入可以下推的条件列表
      InnodbFilter innodbFilter = // 创建InnoDB过滤节点
          InnodbFilter.create(cluster, traitSet, // 传入集群和特征集合
              convert(filter.getInput(), InnodbRel.CONVENTION), // 转换输入节点为InnoDB约定
              condition, indexCondition, tableDef, // 传入过滤条件、索引条件和表定义
              scan.getForceIndexName()); // 传入强制索引名称

      // if some conditions can be pushed down, we left the remainder conditions
      // in the original filter and create a subsidiary filter
      // 注释：如果部分条件可以下推，我们将剩余条件保留在原始过滤中并创建一个子过滤
      if (innodbFilter.indexCondition.canPushDown()) { // 检查是否有条件可以下推到InnoDB
        return LogicalFilter.create(innodbFilter, // 创建逻辑过滤节点，包装InnoDB过滤节点
            RexUtil.composeConjunction(cluster.getRexBuilder(), // 组合剩余的过滤条件
                indexCondition.getRemainderConditions())); // 传入无法下推的剩余条件
      } // if语句结束
      return filter; // 如果没有条件可以下推，返回原始的过滤节点
    } // 方法结束

    /** Rule configuration. */
    // 内部接口注释：规则配置接口，使用不可变值模式
    @Value.Immutable(singleton = false) // 注解：生成不可变的配置实现类，不使用单例模式
    public interface InnodbFilterRuleConfig extends RelRule.Config { // 接口定义：InnoDB过滤规则配置
      InnodbFilterRuleConfig DEFAULT = ImmutableInnodbFilterRuleConfig.builder() // 创建默认配置实例
          .withOperandSupplier(b0 -> // 设置操作数提供器，定义规则的匹配模式
              b0.operand(LogicalFilter.class) // 第一个操作数是逻辑过滤节点
                  .oneInput(b1 -> b1.operand(InnodbTableScan.class) // 第二个操作数是InnoDB表扫描节点
                      .noInputs())) // 表扫描节点没有输入
          .build(); // 构建配置对象

      @Override default InnodbFilterRule toRule() { // 默认方法：将配置转换为规则实例
        return new InnodbFilterRule(this); // 使用当前配置创建新的规则实例
      } // 方法结束
    } // 内部接口结束
  } // 内部类结束

  /**
   * Rule to convert a {@link org.apache.calcite.rel.core.Sort} to a
   * {@link InnodbSort}.
   *
   * @param <C> The rule configuration type.
   */
  // 内部类注释：排序到InnoDB排序的抽象转换规则基类
  // 这个抽象类为所有将排序节点转换为InnoDB排序节点的规则提供公共基础
  // 支持利用InnoDB的隐式排序特性（如聚簇索引、索引扫描结果）来优化排序操作
  // 泛型参数C：规则配置类型，必须是RelRule.Config的子类
  private static class AbstractInnodbSortRule<C extends RelRule.Config> // 类定义：抽象InnoDB排序规则
      extends RelRule<C> { // 继承自RelRule，使用配置对象模式

    AbstractInnodbSortRule(C config) { // 构造方法：创建排序规则实例
      super(config); // 调用父类RelRule的构造方法，传入配置对象
    } // 构造方法结束

    RelNode convert(Sort sort) { // 方法：执行排序节点的转换
      // 参数sort：排序节点，包含排序规则信息
      // 返回值：转换后的InnoDB排序节点
      final RelTraitSet traitSet = // 创建新的特征集合
          sort.getTraitSet().replace(InnodbRel.CONVENTION) // 替换为InnoDB约定
              .replace(sort.getCollation()); // 替换为当前的排序规则
      return new InnodbSort(sort.getCluster(), traitSet, // 创建InnoDB排序节点，传入集群和特征集合
          convert(sort.getInput(), traitSet.replace(RelCollations.EMPTY)), // 转换输入节点，清除排序规则
          sort.getCollation()); // 传入排序规则
    } // 方法结束

    /**
     * Check if it is possible to exploit sorting for a given collation.
     *
     * @return true if it is possible to achieve this sort in Innodb data source
     */
    // 方法注释：检查是否可以利用给定的隐式排序规则来满足排序需求
    // 这个方法比较请求的排序规则和数据源隐含的排序规则，判断是否可以避免额外的排序
    // 参数sortCollation：请求的排序规则，即查询需要的排序顺序和方向
    // 参数implicitCollation：隐式的排序规则，即数据源天然具有的排序顺序（如聚簇索引）
    // 返回值：true表示可以利用隐式排序，false表示需要额外的排序操作
    protected boolean collationsCompatible(RelCollation sortCollation, // 方法定义：检查排序规则兼容性
        RelCollation implicitCollation) { // 参数：隐式排序规则
      List<RelFieldCollation> sortFieldCollations = sortCollation.getFieldCollations(); // 获取请求排序的字段排序列表
      List<RelFieldCollation> implicitFieldCollations = implicitCollation.getFieldCollations(); // 获取隐式排序的字段排序列表

      if (sortFieldCollations.size() > implicitFieldCollations.size()) { // 检查请求排序的字段数是否超过隐式排序的字段数
        return false; // 如果请求排序的字段更多，则无法利用隐式排序
      } // if语句结束
      if (sortFieldCollations.isEmpty()) { // 检查请求排序的字段列表是否为空
        return true; // 如果没有排序要求，则总是兼容
      } // if语句结束

      // check if we need to reverse the order of the implicit collation
      // 注释：检查是否需要反转隐式排序的顺序
      boolean reversed = sortFieldCollations.get(0).getDirection().reverse().lax() // 获取第一个字段的排序方向并反转
          == implicitFieldCollations.get(0).getDirection(); // 比较反转后的方向是否与隐式方向一致

      for (int i = 0; i < sortFieldCollations.size(); i++) { // 遍历所有请求排序的字段
        RelFieldCollation sorted = sortFieldCollations.get(i); // 获取当前请求排序的字段排序规则
        RelFieldCollation implied = implicitFieldCollations.get(i); // 获取当前隐式排序的字段排序规则

        // check that the fields being sorted match
        // 注释：检查排序的字段是否匹配
        if (sorted.getFieldIndex() != implied.getFieldIndex()) { // 比较字段索引是否相同
          return false; // 如果字段不同，则无法利用隐式排序
        } // if语句结束

        // either all fields must be sorted in the same direction
        // or the opposite direction based on whether we decided
        // if the sort direction should be reversed above
        // 注释：所有字段必须按相同方向排序，或者根据是否反转决定按相反方向排序
        RelFieldCollation.Direction sortDirection = sorted.getDirection(); // 获取请求的排序方向
        RelFieldCollation.Direction implicitDirection = implied.getDirection(); // 获取隐式的排序方向
        if ((!reversed && sortDirection != implicitDirection) // 如果不反转，检查方向是否相同
            || (reversed && sortDirection.reverse().lax() != implicitDirection)) { // 如果反转，检查反转后方向是否相同
          return false; // 如果排序方向不匹配，则无法利用隐式排序
        } // if语句结束
      } // for循环结束

      return true; // 所有检查都通过，返回true表示可以利用隐式排序
    } // 方法结束

    @Override public void onMatch(RelOptRuleCall call) { // 重写方法：规则匹配时执行
      // 参数call：规则调用对象，包含匹配的节点信息
      // 返回值：无返回值，直接转换关系树
      final Sort sort = call.rel(0); // 获取第一个关系节点，即排序节点
      final RelNode converted = convert(sort); // 调用转换方法，将排序节点转换为InnoDB排序节点
      call.transformTo(converted); // 将转换后的节点应用到关系树中
    } // 方法结束
  } // 内部类结束

  /**
   * Rule to convert a {@link org.apache.calcite.rel.core.Sort} to a
   * {@link InnodbSort}.
   *
   * @see #SORT_FILTER
   */
  // 内部类注释：排序过滤组合到InnoDB排序的转换规则
  // 这个规则专门处理排序节点紧跟在过滤节点之后的情况
  // 利用过滤后数据的隐式排序特性（如索引扫描结果）来优化排序操作
  // 当排序规则与过滤节点隐含的排序规则兼容时，可以避免额外的排序
  public static class InnodbSortFilterRule // 类定义：InnoDB排序过滤规则
      extends AbstractInnodbSortRule<InnodbSortFilterRule.InnodbSortFilterRuleConfig> { // 继承抽象排序规则基类
    /** Creates a InnodbSortFilterRule. */
    // 构造方法注释：创建InnoDB排序过滤规则实例
    protected InnodbSortFilterRule(InnodbSortFilterRuleConfig config) { // 构造方法：创建规则实例
      super(config); // 调用父类AbstractInnodbSortRule的构造方法，传入配置对象
    } // 构造方法结束

    @Override public boolean matches(RelOptRuleCall call) { // 重写方法：检查规则是否匹配
      // 参数call：规则调用对象，包含匹配的上下文信息
      // 返回值：true表示规则可以匹配并应用，false表示不匹配
      final Sort sort = call.rel(0); // 获取第一个关系节点，即排序节点
      final InnodbFilter filter = call.rel(2); // 获取第三个关系节点，即InnoDB过滤节点
      return collationsCompatible(sort.getCollation(), filter.getImplicitCollation()); // 检查排序规则与隐式排序规则是否兼容
    } // 方法结束

    /** Rule configuration. */
    // 内部接口注释：规则配置接口，使用不可变值模式
    @Value.Immutable(singleton = false) // 注解：生成不可变的配置实现类，不使用单例模式
    public interface InnodbSortFilterRuleConfig extends RelRule.Config { // 接口定义：InnoDB排序过滤规则配置
      InnodbSortFilterRuleConfig DEFAULT = ImmutableInnodbSortFilterRuleConfig.builder() // 创建默认配置实例
          .withOperandSupplier(b0 -> // 设置操作数提供器，定义规则的匹配模式
              b0.operand(Sort.class) // 第一个操作数是排序节点
                  .predicate(sort -> true) // 排序节点的谓词，总是返回true，表示匹配所有排序节点
                  .oneInput(b1 -> // 排序节点的输入
                      b1.operand(InnodbToEnumerableConverter.class) // 第二个操作数是InnoDB到可枚举的转换器
                          .oneInput(b2 -> // 转换器的输入
                              b2.operand(InnodbFilter.class) // 第三个操作数是InnoDB过滤节点
                                  .predicate(innodbFilter -> true) // 过滤节点的谓词，总是返回true
                                  .anyInputs()))) // 过滤节点可以有任何输入
          .build(); // 构建配置对象

      @Override default InnodbSortFilterRule toRule() { // 默认方法：将配置转换为规则实例
        return new InnodbSortFilterRule(this); // 使用当前配置创建新的规则实例
      } // 方法结束
    } // 内部接口结束
  } // 内部类结束

  /**
   * Rule to convert a {@link org.apache.calcite.rel.core.Sort} to a
   * {@link InnodbSort} based on InnoDB table clustering index.
   *
   * @see #SORT_SCAN
   */
  // 内部类注释：基于InnoDB表聚簇索引的排序到InnoDB排序的转换规则
  // 这个规则专门处理排序节点紧跟在表扫描节点之后的情况
  // 利用InnoDB表的聚簇索引特性（表数据按聚簇索引组织存储）来优化排序操作
  // 当排序规则与聚簇索引的排序规则兼容时，可以避免额外的排序操作
  public static class InnodbSortTableScanRule // 类定义：基于表扫描的InnoDB排序规则
      extends AbstractInnodbSortRule<InnodbSortTableScanRule.InnodbSortTableScanRuleConfig> { // 继承抽象排序规则基类
    /** Creates a InnodbSortTableScanRule. */
    // 构造方法注释：创建基于表扫描的InnoDB排序规则实例
    protected InnodbSortTableScanRule(InnodbSortTableScanRuleConfig config) { // 构造方法：创建规则实例
      super(config); // 调用父类AbstractInnodbSortRule的构造方法，传入配置对象
    } // 构造方法结束

    @Override public boolean matches(RelOptRuleCall call) { // 重写方法：检查规则是否匹配
      // 参数call：规则调用对象，包含匹配的上下文信息
      // 返回值：true表示规则可以匹配并应用，false表示不匹配
      final Sort sort = call.rel(0); // 获取第一个关系节点，即排序节点
      final InnodbTableScan tableScan = call.rel(2); // 获取第三个关系节点，即InnoDB表扫描节点
      return collationsCompatible(sort.getCollation(), tableScan.getImplicitCollation()); // 检查排序规则与表扫描的隐式排序规则是否兼容
    } // 方法结束

    /** Rule configuration. */
    // 内部接口注释：规则配置接口，使用不可变值模式
    @Value.Immutable(singleton = false) // 注解：生成不可变的配置实现类，不使用单例模式
    public interface InnodbSortTableScanRuleConfig extends RelRule.Config { // 接口定义：基于表扫描的InnoDB排序规则配置
      InnodbSortTableScanRuleConfig DEFAULT = ImmutableInnodbSortTableScanRuleConfig.builder() // 创建默认配置实例
          .withOperandSupplier(b0 -> // 设置操作数提供器，定义规则的匹配模式
              b0.operand(Sort.class) // 第一个操作数是排序节点
                  .predicate(sort -> true) // 排序节点的谓词，总是返回true，表示匹配所有排序节点
                  .oneInput(b1 -> // 排序节点的输入
                      b1.operand(InnodbToEnumerableConverter.class) // 第二个操作数是InnoDB到可枚举的转换器
                          .oneInput(b2 -> // 转换器的输入
                              b2.operand(InnodbTableScan.class) // 第三个操作数是InnoDB表扫描节点
                                  .predicate(tableScan -> true) // 表扫描节点的谓词，总是返回true
                                  .anyInputs()))) // 表扫描节点可以有任何输入
          .build(); // 构建配置对象

      @Override default InnodbSortTableScanRule toRule() { // 默认方法：将配置转换为规则实例
        return new InnodbSortTableScanRule(this); // 使用当前配置创建新的规则实例
      } // 方法结束
    } // 内部接口结束
  } // 内部类结束
} // 类定义结束：InnodbRules类结束
