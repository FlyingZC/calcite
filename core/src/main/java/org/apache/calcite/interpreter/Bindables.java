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
package org.apache.calcite.interpreter; // 定义包名，该类属于org.apache.calcite.interpreter包，用于解释器相关的功能

import org.apache.calcite.DataContext; // 导入DataContext类，用于数据上下文，提供执行环境
import org.apache.calcite.adapter.enumerable.AggImplementor; // 导入AggImplementor接口，用于聚合函数的实现
import org.apache.calcite.adapter.enumerable.RexImpTable; // 导入RexImpTable类，用于Rex表达式实现表
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，用于可枚举的数据集合
import org.apache.calcite.plan.Convention; // 导入Convention类，用于定义关系表达式的调用约定
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，用于关系表达式优化集群
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost接口，用于关系表达式优化成本
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner接口，用于关系表达式优化器
import org.apache.calcite.plan.RelOptRule; // 导入RelOptRule类，用于关系表达式优化规则
import org.apache.calcite.plan.RelOptRuleCall; // 导入RelOptRuleCall类，用于优化规则调用
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable类，用于优化表对象
import org.apache.calcite.plan.RelRule; // 导入RelRule类，用于关系规则基类
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于关系特征集合
import org.apache.calcite.rel.InvalidRelException; // 导入InvalidRelException类，用于无效关系表达式异常
import org.apache.calcite.rel.RelCollation; // 导入RelCollation类，用于关系排序
import org.apache.calcite.rel.RelCollationTraitDef; // 导入RelCollationTraitDef类，用于排序特征定义
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，用于关系表达式节点
import org.apache.calcite.rel.RelWriter; // 导入RelWriter接口，用于关系表达式写入器
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类，用于转换规则基类
import org.apache.calcite.rel.core.Aggregate; // 导入Aggregate类，用于聚合操作
import org.apache.calcite.rel.core.AggregateCall; // 导入AggregateCall类，用于聚合调用
import org.apache.calcite.rel.core.CorrelationId; // 导入CorrelationId类，用于关联ID
import org.apache.calcite.rel.core.Filter; // 导入Filter类，用于过滤操作
import org.apache.calcite.rel.core.Intersect; // 导入Intersect类，用于交集操作
import org.apache.calcite.rel.core.Join; // 导入Join类，用于连接操作
import org.apache.calcite.rel.core.JoinRelType; // 导入JoinRelType枚举，用于连接类型
import org.apache.calcite.rel.core.Match; // 导入Match类，用于模式匹配操作
import org.apache.calcite.rel.core.Minus; // 导入Minus类，用于差集操作
import org.apache.calcite.rel.core.Project; // 导入Project类，用于投影操作
import org.apache.calcite.rel.core.SetOp; // 导入SetOp类，用于集合操作基类
import org.apache.calcite.rel.core.Sort; // 导入Sort类，用于排序操作
import org.apache.calcite.rel.core.TableScan; // 导入TableScan类，用于表扫描操作
import org.apache.calcite.rel.core.Union; // 导入Union类，用于并集操作
import org.apache.calcite.rel.core.Values; // 导入Values类，用于常量值操作
import org.apache.calcite.rel.core.Window; // 导入Window类，用于窗口函数操作
import org.apache.calcite.rel.logical.LogicalAggregate; // 导入LogicalAggregate类，用于逻辑聚合
import org.apache.calcite.rel.logical.LogicalFilter; // 导入LogicalFilter类，用于逻辑过滤
import org.apache.calcite.rel.logical.LogicalIntersect; // 导入LogicalIntersect类，用于逻辑交集
import org.apache.calcite.rel.logical.LogicalJoin; // 导入LogicalJoin类，用于逻辑连接
import org.apache.calcite.rel.logical.LogicalMatch; // 导入LogicalMatch类，用于逻辑模式匹配
import org.apache.calcite.rel.logical.LogicalProject; // 导入LogicalProject类，用于逻辑投影
import org.apache.calcite.rel.logical.LogicalTableScan; // 导入LogicalTableScan类，用于逻辑表扫描
import org.apache.calcite.rel.logical.LogicalUnion; // 导入LogicalUnion类，用于逻辑并集
import org.apache.calcite.rel.logical.LogicalValues; // 导入LogicalValues类，用于逻辑常量值
import org.apache.calcite.rel.logical.LogicalWindow; // 导入LogicalWindow类，用于逻辑窗口函数
import org.apache.calcite.rel.metadata.RelMdCollation; // 导入RelMdCollation类，用于排序元数据
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于元数据查询
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，用于关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于数据类型工厂
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField类，用于数据类型字段
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类，用于Rex常量表达式
import org.apache.calcite.rex.RexNode; // 导入RexNode接口，用于行表达式节点
import org.apache.calcite.schema.FilterableTable; // 导入FilterableTable接口，用于可过滤的表
import org.apache.calcite.schema.ProjectableFilterableTable; // 导入ProjectableFilterableTable接口，用于可投影可过滤的表
import org.apache.calcite.schema.ScannableTable; // 导入ScannableTable接口，用于可扫描的表
import org.apache.calcite.schema.Table; // 导入Table接口，用于表接口
import org.apache.calcite.tools.RelBuilderFactory; // 导入RelBuilderFactory接口，用于关系表达式构建器工厂
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，用于不可变位集合
import org.apache.calcite.util.ImmutableIntList; // 导入ImmutableIntList类，用于不可变整数列表

import com.google.common.collect.ImmutableList; // 导入Guava的ImmutableList，用于不可变列表
import com.google.common.collect.ImmutableSet; // 导入Guava的ImmutableSet，用于不可变集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可空类型
import org.immutables.value.Value; // 导入Immutables的Value注解，用于生成不可变类

import java.util.List; // 导入List接口，用于列表
import java.util.Map; // 导入Map接口，用于映射
import java.util.Set; // 导入Set接口，用于集合
import java.util.SortedSet; // 导入SortedSet接口，用于有序集合

import static com.google.common.base.Preconditions.checkArgument; // 导入Preconditions的checkArgument方法，用于参数校验

import static java.util.Objects.requireNonNull; // 导入Objects的requireNonNull方法，用于非空检查

/**
 * Utilities pertaining to {@link BindableRel} and {@link BindableConvention}.
 * 与BindableRel和BindableConvention相关的工具类
 * 
 * 这个类提供了将逻辑关系表达式转换为可绑定（Bindable）关系表达式的工具方法和规则
 * Bindable是一种特殊的调用约定，允许关系表达式通过解释器执行
 * 
 * 主要功能：
 * 1. 提供各种转换规则，将逻辑操作符（如Filter、Project、Join等）转换为Bindable版本
 * 2. 提供BindableRel接口的实现类，这些类可以被解释器执行
 * 3. 提供工具方法帮助执行Bindable关系表达式
 * 
 * BindableConvention的特点：
 * - 允许关系表达式在解释器中执行，而不是生成代码
 * - 适用于无法生成代码或者需要灵活执行的场景
 * - 通过Interpreter类逐步解释执行关系表达式
 */
@Value.Enclosing // Value.Enclosing注解，标记这是一个包含内部Value类的封闭类
public class Bindables { // 定义Bindable工具类
  private Bindables() {} // 私有构造函数，防止实例化，这是一个纯工具类

  public static final RelOptRule BINDABLE_TABLE_SCAN_RULE = // 定义常量：表扫描转换规则，将LogicalTableScan转换为BindableTableScan
      BindableTableScanRule.Config.DEFAULT.toRule(); // 从默认配置创建规则实例

  public static final RelOptRule BINDABLE_FILTER_RULE = // 定义常量：过滤转换规则，将LogicalFilter转换为BindableFilter
      BindableFilterRule.DEFAULT_CONFIG.toRule(BindableFilterRule.class); // 从默认配置创建规则实例

  public static final RelOptRule BINDABLE_PROJECT_RULE = // 定义常量：投影转换规则，将LogicalProject转换为BindableProject
      BindableProjectRule.DEFAULT_CONFIG.toRule(BindableProjectRule.class); // 从默认配置创建规则实例

  public static final RelOptRule BINDABLE_SORT_RULE = // 定义常量：排序转换规则，将Sort转换为BindableSort
      BindableSortRule.DEFAULT_CONFIG.toRule(BindableSortRule.class); // 从默认配置创建规则实例

  public static final RelOptRule BINDABLE_JOIN_RULE = // 定义常量：连接转换规则，将LogicalJoin转换为BindableJoin
      BindableJoinRule.DEFAULT_CONFIG.toRule(BindableJoinRule.class); // 从默认配置创建规则实例

  public static final RelOptRule BINDABLE_SET_OP_RULE = // 定义常量：集合操作转换规则，将SetOp转换为Bindable版本
      BindableSetOpRule.DEFAULT_CONFIG.toRule(BindableSetOpRule.class); // 从默认配置创建规则实例

  // CHECKSTYLE: IGNORE 1 // 忽略Checkstyle检查
  /** @deprecated Use {@link #BINDABLE_SET_OP_RULE}. */ // 已废弃，使用BINDABLE_SET_OP_RULE替代
  @SuppressWarnings("MissingSummary") // 抑制缺少摘要的警告
  public static final RelOptRule BINDABLE_SETOP_RULE = // 定义常量：旧名称的集合操作规则（已废弃）
      BINDABLE_SET_OP_RULE; // 直接引用BINDABLE_SET_OP_RULE

  public static final RelOptRule BINDABLE_VALUES_RULE = // 定义常量：常量值转换规则，将LogicalValues转换为BindableValues
      BindableValuesRule.DEFAULT_CONFIG.toRule(BindableValuesRule.class); // 从默认配置创建规则实例

  public static final RelOptRule BINDABLE_AGGREGATE_RULE = // 定义常量：聚合转换规则，将LogicalAggregate转换为BindableAggregate
      BindableAggregateRule.DEFAULT_CONFIG.toRule(BindableAggregateRule.class); // 从默认配置创建规则实例

  public static final RelOptRule BINDABLE_WINDOW_RULE = // 定义常量：窗口函数转换规则，将LogicalWindow转换为BindableWindow
      BindableWindowRule.DEFAULT_CONFIG.toRule(BindableWindowRule.class); // 从默认配置创建规则实例

  public static final RelOptRule BINDABLE_MATCH_RULE = // 定义常量：模式匹配转换规则，将LogicalMatch转换为BindableMatch
      BindableMatchRule.DEFAULT_CONFIG.toRule(BindableMatchRule.class); // 从默认配置创建规则实例

  /** Rule that converts a relational expression from // 将关系表达式从NONE约定转换为Bindable约定的规则
   * {@link org.apache.calcite.plan.Convention#NONE} // 从NONE约定（默认逻辑约定）
   * to {@link org.apache.calcite.interpreter.BindableConvention}. */ // 转换为Bindable约定
  public static final NoneToBindableConverterRule FROM_NONE_RULE = // 定义常量：NONE到Bindable的转换规则
      NoneToBindableConverterRule.DEFAULT_CONFIG // 获取默认配置
          .toRule(NoneToBindableConverterRule.class); // 创建规则实例

  /** All rules that convert logical relational expression to bindable. */ // 将逻辑关系表达式转换为Bindable的所有规则的集合
  public static final ImmutableList<RelOptRule> RULES = // 定义不可变列表，包含所有Bindable转换规则
      ImmutableList.of(FROM_NONE_RULE, // 包含NONE到Bindable的转换规则
          BINDABLE_TABLE_SCAN_RULE, // 包含表扫描规则
          BINDABLE_FILTER_RULE, // 包含过滤规则
          BINDABLE_PROJECT_RULE, // 包含投影规则
          BINDABLE_SORT_RULE, // 包含排序规则
          BINDABLE_JOIN_RULE, // 包含连接规则
          BINDABLE_SET_OP_RULE, // 包含集合操作规则
          BINDABLE_VALUES_RULE, // 包含常量值规则
          BINDABLE_AGGREGATE_RULE, // 包含聚合规则
          BINDABLE_WINDOW_RULE, // 包含窗口函数规则
          BINDABLE_MATCH_RULE); // 包含模式匹配规则

  /** Helper method that converts a bindable relational expression into a // 辅助方法，将可绑定关系表达式转换为记录迭代器
   * record iterator. // 转换为记录迭代器（Enumerable）
   *
   * <p>Any bindable can be compiled; if its input is also bindable, it becomes // 任何Bindable都可以被编译；如果它的输入也是Bindable，它将成为
   * part of the same compilation unit. */ // 同一编译单元的一部分
   private static Enumerable<@Nullable Object[]> help(DataContext dataContext, // 私有静态方法：帮助方法，接收数据上下文和Bindable关系表达式
      BindableRel rel) { // 参数：BindableRel关系表达式
    return new Interpreter(dataContext, rel); // 创建解释器实例，返回可枚举的结果集
  }

  /** Rule that converts a {@link org.apache.calcite.rel.core.TableScan} // 将表扫描操作转换为Bindable约定的规则
   * to bindable convention. // 转换为Bindable约定
   *
   * @see #BINDABLE_TABLE_SCAN_RULE */ // 参见BINDABLE_TABLE_SCAN_RULE常量
  public static class BindableTableScanRule // 定义BindableTableScanRule类，继承RelRule
      extends RelRule<BindableTableScanRule.Config> { // 泛型参数为BindableTableScanRule.Config配置接口
    /** Called from Config. */ // 从配置调用的构造函数
    protected BindableTableScanRule(Config config) { // 受保护的构造函数，接收配置对象
      super(config); // 调用父类RelRule的构造函数
    }

    @Deprecated // to be removed before 2.0 // 已废弃，将在2.0版本前移除
    public BindableTableScanRule(RelBuilderFactory relBuilderFactory) { // 废弃的构造函数，接收RelBuilderFactory
      this(Config.DEFAULT.withRelBuilderFactory(relBuilderFactory) // 使用默认配置并设置RelBuilderFactory
          .as(Config.class)); // 转换为Config类型
    }

    @Override public void onMatch(RelOptRuleCall call) { // 重写onMatch方法，当规则匹配时调用
      final LogicalTableScan scan = call.rel(0); // 获取规则调用的第一个关系表达式（LogicalTableScan）
      final RelOptTable table = scan.getTable(); // 获取表扫描操作的表对象
      if (BindableTableScan.canHandle(table)) { // 检查表是否可以被BindableTableScan处理
        call.transformTo( // 转换关系表达式
            BindableTableScan.create(scan.getCluster(), table)); // 创建BindableTableScan实例
      }
    }

    /** Rule configuration. */ // 规则配置接口
    @Value.Immutable // 使用Immutables注解，自动生成不可变实现类
    public interface Config extends RelRule.Config { // 定义Config接口，继承RelRule.Config
      Config DEFAULT = ImmutableBindables.Config.of() // 定义默认配置实例
          .withOperandSupplier(b -> // 设置操作数提供器
              b.operand(LogicalTableScan.class).noInputs()); // 匹配LogicalTableScan且没有输入的操作数

      @Override default BindableTableScanRule toRule() { // 重写toRule方法，创建规则实例
        return new BindableTableScanRule(this); // 返回新的BindableTableScanRule实例
      }
    }
  }

  /** Scan of a table that implements {@link ScannableTable} and therefore can // 表扫描操作，针对实现了ScannableTable接口的表，因此可以
   * be converted into an {@link Enumerable}. */ // 转换为可枚举集合（Enumerable）
  public static class BindableTableScan // 定义BindableTableScan类，继承TableScan并实现BindableRel接口
      extends TableScan implements BindableRel { // 继承TableScan类，实现BindableRel接口
    public final ImmutableList<RexNode> filters; // 成员变量：过滤条件列表，用于下推到表扫描的过滤条件
    public final ImmutableIntList projects; // 成员变量：投影字段列表，用于下推到表扫描的投影字段索引

    /** Creates a BindableTableScan. // 创建BindableTableScan实例
     *
     * <p>Use {@link #create} unless you know what you are doing. */ // 除非你知道自己在做什么，否则使用create静态方法
    BindableTableScan(RelOptCluster cluster, RelTraitSet traitSet, // 构造函数，接收集群、特征集、表、过滤条件和投影列表
        RelOptTable table, ImmutableList<RexNode> filters, // 参数：表对象、过滤条件列表
        ImmutableIntList projects) { // 参数：投影字段索引列表
      super(cluster, traitSet, ImmutableList.of(), table); // 调用父类TableScan的构造函数
      this.filters = requireNonNull(filters, "filters"); // 初始化filters，确保不为null
      this.projects = requireNonNull(projects, "projects"); // 初始化projects，确保不为null
      checkArgument(canHandle(table)); // 检查表是否可以被处理
    }

    /** Creates a BindableTableScan. */ // 创建BindableTableScan实例的静态方法（简化版）
    public static BindableTableScan create(RelOptCluster cluster, // 参数：关系表达式优化集群
        RelOptTable relOptTable) { // 参数：优化表对象
      return create(cluster, relOptTable, ImmutableList.of(), // 调用完整版create方法，过滤条件为空
          identity(relOptTable)); // 投影为所有字段（identity）
    }

    /** Creates a BindableTableScan. */ // 创建BindableTableScan实例的静态方法（完整版）
    public static BindableTableScan create(RelOptCluster cluster, // 参数：关系表达式优化集群
        RelOptTable relOptTable, List<RexNode> filters, // 参数：优化表对象、过滤条件列表
        List<Integer> projects) { // 参数：投影字段索引列表
      final Table table = relOptTable.unwrap(Table.class); // 解包获取Table接口实例
      final RelTraitSet traitSet = // 创建特征集
          cluster.traitSetOf(BindableConvention.INSTANCE) // 设置为BindableConvention约定
              .replaceIfs(RelCollationTraitDef.INSTANCE, () -> { // 替换排序特征
                if (table != null) { // 如果表不为null
                  return table.getStatistic().getCollations(); // 返回表的统计信息中的排序
                }
                return ImmutableList.of(); // 否则返回空列表
              });
      return new BindableTableScan(cluster, traitSet, relOptTable, // 创建并返回BindableTableScan实例
          ImmutableList.copyOf(filters), ImmutableIntList.copyOf(projects)); // 将过滤条件和投影列表转为不可变集合
    }

    @Override public RelDataType deriveRowType() { // 重写deriveRowType方法，推导行类型
      final RelDataTypeFactory.Builder builder = // 创建数据类型构建器
          getCluster().getTypeFactory().builder(); // 从集群获取类型工厂并创建构建器
      final List<RelDataTypeField> fieldList = // 获取表行类型的字段列表
          table.getRowType().getFieldList(); // 从表对象获取行类型的所有字段
      for (int project : projects) { // 遍历投影字段索引
        builder.add(fieldList.get(project)); // 将对应的字段添加到构建器
      }
      return builder.build(); // 构建并返回行类型
    }

    @Override public Class<Object[]> getElementType() { // 重写getElementType方法，获取元素类型
      return Object[].class; // 返回Object数组类型，表示每行数据是一个对象数组
    }

    @Override public RelWriter explainTerms(RelWriter pw) { // 重写explainTerms方法，解释关系表达式
      return super.explainTerms(pw) // 调用父类的explainTerms方法
          .itemIf("filters", filters, !filters.isEmpty()) // 如果有过滤条件，添加filters项
          .itemIf("projects", projects, !projects.equals(identity())); // 如果有投影，添加projects项
    }

    @Override public double estimateRowCount(RelMetadataQuery mq) { // 重写estimateRowCount方法，估算行数
      double f = filters.isEmpty() ? 1d : 0.5d; // 如果有过滤条件，估算行数乘以0.5
      return super.estimateRowCount(mq) * f; // 返回父类估算结果乘以过滤因子
    }

    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法，计算自身成本
        RelMetadataQuery mq) { // 参数：优化器和元数据查询
      boolean noPushing = filters.isEmpty() // 判断是否没有下推任何过滤和投影
              && projects.size() == table.getRowType().getFieldCount(); // 检查投影是否包含所有字段
      RelOptCost cost = super.computeSelfCost(planner, mq); // 调用父类方法计算基础成本
      if (noPushing || cost == null) { // 如果没有下推或成本为null
        return cost; // 直接返回基础成本
      }
      // Cost factor for pushing filters // 过滤条件下推的成本因子
      double f = filters.isEmpty() ? 1d : 0.5d; // 有过滤条件时，成本因子为0.5

      // Cost factor for pushing fields // 投影下推的成本因子
      // The "+ 2d" on top and bottom keeps the function fairly smooth. // 上下都加2保持函数平滑
      double p = ((double) projects.size() + 2d) // 计算投影字段的成本比例
          / ((double) table.getRowType().getFieldCount() + 2d); // 除以总字段数

      // Multiply the cost by a factor that makes a scan more attractive if // 将成本乘以一个因子，使得当过滤和投影下推到表扫描时
      // filters and projects are pushed to the table scan // 表扫描更有吸引力
      return cost.multiplyBy(f * p * 0.01d); // 返回成本乘以因子后的结果
    }

    public static boolean canHandle(RelOptTable table) { // 静态方法：检查表是否可以被BindableTableScan处理
      return table.maybeUnwrap(ScannableTable.class).isPresent() // 检查表是否实现了ScannableTable接口
          || table.maybeUnwrap(FilterableTable.class).isPresent() // 或是否实现了FilterableTable接口
          || table.maybeUnwrap(ProjectableFilterableTable.class).isPresent(); // 或是否实现了ProjectableFilterableTable接口
    }

    @Override public Enumerable<@Nullable Object[]> bind(DataContext dataContext) { // 重写bind方法，绑定数据上下文并执行
      return help(dataContext, this); // 调用help方法，返回可枚举的结果集
    }

    @Override public Node implement(InterpreterImplementor implementor) { // 重写implement方法，实现解释器节点
      throw new UnsupportedOperationException(); // TODO: // 抛出不支持操作异常（TODO：未实现）
    }
  }

  /** Rule that converts a {@link Filter} to bindable convention. // 将过滤操作转换为Bindable约定的规则
   *
   * @see #BINDABLE_FILTER_RULE */ // 参见BINDABLE_FILTER_RULE常量
  public static class BindableFilterRule extends ConverterRule { // 定义BindableFilterRule类，继承ConverterRule
    /** Default configuration. */ // 默认配置
    public static final Config DEFAULT_CONFIG = Config.INSTANCE // 定义默认配置常量
        .withConversion(LogicalFilter.class, f -> !f.containsOver(), // 设置转换：LogicalFilter且不包含窗口函数
            Convention.NONE, BindableConvention.INSTANCE, // 从NONE约定转换为BindableConvention约定
            "BindableFilterRule") // 规则名称
        .withRuleFactory(BindableFilterRule::new); // 设置规则工厂

    /** Called from the Config. */ // 从配置调用的构造函数
    protected BindableFilterRule(Config config) { // 受保护的构造函数，接收配置对象
      super(config); // 调用父类ConverterRule的构造函数
    }

    @Override public RelNode convert(RelNode rel) { // 重写convert方法，转换关系表达式
      final LogicalFilter filter = (LogicalFilter) rel; // 将输入转换为LogicalFilter
      return BindableFilter.create( // 创建BindableFilter实例
          convert(filter.getInput(), // 转换输入关系表达式
              filter.getInput().getTraitSet() // 获取输入的特征集
                  .replace(BindableConvention.INSTANCE)), // 替换为BindableConvention约定
          filter.getCondition()); // 传递过滤条件
    }
  }

  /** Implementation of {@link org.apache.calcite.rel.core.Filter} // Filter操作在Bindable约定中的实现
   * in bindable convention. */ // 在Bindable约定中实现
  public static class BindableFilter extends Filter implements BindableRel { // 定义BindableFilter类，继承Filter并实现BindableRel
    public BindableFilter(RelOptCluster cluster, RelTraitSet traitSet, // 构造函数，接收集群、特征集、输入和过滤条件
        RelNode input, RexNode condition) { // 参数：输入关系表达式、过滤条件表达式
      super(cluster, traitSet, input, condition); // 调用父类Filter的构造函数
      assert getConvention() instanceof BindableConvention; // 断言约定是BindableConvention
    }

    /** Creates a BindableFilter. */ // 创建BindableFilter实例的静态方法
    public static BindableFilter create(final RelNode input, // 参数：输入关系表达式
        RexNode condition) { // 参数：过滤条件表达式
      final RelOptCluster cluster = input.getCluster(); // 获取输入的集群
      final RelMetadataQuery mq = cluster.getMetadataQuery(); // 获取元数据查询对象
      final RelTraitSet traitSet = // 创建特征集
          cluster.traitSetOf(BindableConvention.INSTANCE) // 设置为BindableConvention约定
              .replaceIfs(RelCollationTraitDef.INSTANCE, // 替换排序特征
                  () -> RelMdCollation.filter(mq, input)); // 根据过滤操作计算排序
      return new BindableFilter(cluster, traitSet, input, condition); // 创建并返回BindableFilter实例
    }

    @Override public BindableFilter copy(RelTraitSet traitSet, RelNode input, // 重写copy方法，复制过滤节点
        RexNode condition) { // 参数：新的特征集、输入、过滤条件
      return new BindableFilter(getCluster(), traitSet, input, condition); // 创建并返回新的BindableFilter实例
    }

    @Override public Class<Object[]> getElementType() { // 重写getElementType方法，获取元素类型
      return Object[].class; // 返回Object数组类型
    }

    @Override public Enumerable<@Nullable Object[]> bind(DataContext dataContext) { // 重写bind方法，绑定数据上下文并执行
      return help(dataContext, this); // 调用help方法，返回可枚举的结果集
    }

    @Override public Node implement(InterpreterImplementor implementor) { // 重写implement方法，实现解释器节点
      return new FilterNode(implementor.compiler, this); // 创建并返回FilterNode节点
    }
  }

  /**
   * Rule to convert a {@link org.apache.calcite.rel.logical.LogicalProject} // 将逻辑投影转换为BindableProject的规则
   * to a {@link BindableProject}. // 转换为BindableProject
   *
   * @see #BINDABLE_PROJECT_RULE // 参见BINDABLE_PROJECT_RULE常量
   */
  public static class BindableProjectRule extends ConverterRule { // 定义BindableProjectRule类，继承ConverterRule
    /** Default configuration. */ // 默认配置
    public static final Config DEFAULT_CONFIG = Config.INSTANCE // 定义默认配置常量
        .withConversion(LogicalProject.class, p -> !p.containsOver(), // 设置转换：LogicalProject且不包含窗口函数
            Convention.NONE, BindableConvention.INSTANCE, // 从NONE约定转换为BindableConvention约定
            "BindableProjectRule") // 规则名称
        .withRuleFactory(BindableProjectRule::new); // 设置规则工厂

    /** Called from the Config. */ // 从配置调用的构造函数
    protected BindableProjectRule(Config config) { // 受保护的构造函数，接收配置对象
      super(config); // 调用父类ConverterRule的构造函数
    }

    @Override public boolean matches(RelOptRuleCall call) { // 重写matches方法，检查规则是否匹配
      final LogicalProject project = call.rel(0); // 获取规则调用的第一个关系表达式（LogicalProject）
      return project.getVariablesSet().isEmpty(); // 检查变量集合是否为空（不包含相关变量）
    }

    @Override public RelNode convert(RelNode rel) { // 重写convert方法，转换关系表达式
      final LogicalProject project = (LogicalProject) rel; // 将输入转换为LogicalProject
      return new BindableProject(rel.getCluster(), // 创建BindableProject实例
          rel.getTraitSet().replace(BindableConvention.INSTANCE), // 替换特征集为BindableConvention约定
          convert(project.getInput(), // 转换输入关系表达式
              project.getInput().getTraitSet() // 获取输入的特征集
                  .replace(BindableConvention.INSTANCE)), // 替换为BindableConvention约定
          project.getProjects(), // 传递投影表达式列表
          project.getRowType()); // 传递行类型
    }
  }

  /** Implementation of {@link org.apache.calcite.rel.core.Project} in // Project操作在Bindable约定中的实现
   * bindable calling convention. */ // 在Bindable调用约定中实现
  public static class BindableProject extends Project implements BindableRel { // 定义BindableProject类，继承Project并实现BindableRel
    public BindableProject(RelOptCluster cluster, RelTraitSet traitSet, // 构造函数，接收集群、特征集、输入、投影列表和行类型
        RelNode input, List<? extends RexNode> projects, RelDataType rowType) { // 参数：输入关系表达式、投影表达式列表、行类型
      super(cluster, traitSet, ImmutableList.of(), input, projects, rowType, ImmutableSet.of()); // 调用父类Project的构造函数
      assert getConvention() instanceof BindableConvention; // 断言约定是BindableConvention
    }

    @Override public BindableProject copy(RelTraitSet traitSet, RelNode input, // 重写copy方法，复制投影节点
        List<RexNode> projects, RelDataType rowType) { // 参数：新的特征集、输入、投影列表、行类型
      return new BindableProject(getCluster(), traitSet, input, // 创建并返回新的BindableProject实例
          projects, rowType); // 传递投影列表和行类型
    }

    @Override public Class<Object[]> getElementType() { // 重写getElementType方法，获取元素类型
      return Object[].class; // 返回Object数组类型
    }

    @Override public Enumerable<@Nullable Object[]> bind(DataContext dataContext) { // 重写bind方法，绑定数据上下文并执行
      return help(dataContext, this); // 调用help方法，返回可枚举的结果集
    }

    @Override public Node implement(InterpreterImplementor implementor) { // 重写implement方法，实现解释器节点
      return new ProjectNode(implementor.compiler, this); // 创建并返回ProjectNode节点
    }
  }

  /**
   * Rule to convert an {@link org.apache.calcite.rel.core.Sort} to a // 将排序操作转换为BindableSort的规则
   * {@link org.apache.calcite.interpreter.Bindables.BindableSort}. // 转换为BindableSort
   *
   * @see #BINDABLE_SORT_RULE // 参见BINDABLE_SORT_RULE常量
   */
  public static class BindableSortRule extends ConverterRule { // 定义BindableSortRule类，继承ConverterRule
    /** Default configuration. */ // 默认配置
    public static final Config DEFAULT_CONFIG = Config.INSTANCE // 定义默认配置常量
        .withConversion(Sort.class, Convention.NONE, // 设置转换：Sort类
            BindableConvention.INSTANCE, "BindableSortRule") // 从NONE约定转换为BindableConvention约定
        .withRuleFactory(BindableSortRule::new); // 设置规则工厂

    /** Called from the Config. */ // 从配置调用的构造函数
    protected BindableSortRule(Config config) { // 受保护的构造函数，接收配置对象
      super(config); // 调用父类ConverterRule的构造函数
    }

    @Override public RelNode convert(RelNode rel) { // 重写convert方法，转换关系表达式
      final Sort sort = (Sort) rel; // 将输入转换为Sort
      final RelTraitSet traitSet = // 创建特征集
          sort.getTraitSet().replace(BindableConvention.INSTANCE); // 替换为BindableConvention约定
      final RelNode input = sort.getInput(); // 获取输入关系表达式
      return new BindableSort(rel.getCluster(), traitSet, // 创建BindableSort实例
          convert(input, // 转换输入关系表达式
              input.getTraitSet().replace(BindableConvention.INSTANCE)), // 替换输入的特征集为BindableConvention约定
          sort.getCollation(), sort.offset, sort.fetch); // 传递排序规则、偏移量和获取数量
    }
  }

  /** Implementation of {@link org.apache.calcite.rel.core.Sort} // Sort操作在Bindable约定中的实现
   * bindable calling convention. */ // 在Bindable调用约定中实现
  public static class BindableSort extends Sort implements BindableRel { // 定义BindableSort类，继承Sort并实现BindableRel
    public BindableSort(RelOptCluster cluster, RelTraitSet traitSet, // 构造函数，接收集群、特征集、输入、排序规则、偏移量和获取数量
        RelNode input, RelCollation collation, @Nullable RexNode offset, @Nullable RexNode fetch) { // 参数：输入关系表达式、排序规则、偏移量表达式、获取数量表达式
      super(cluster, traitSet, input, collation, offset, fetch); // 调用父类Sort的构造函数
      assert getConvention() instanceof BindableConvention; // 断言约定是BindableConvention
    }

    @Override public BindableSort copy(RelTraitSet traitSet, RelNode newInput, // 重写copy方法，复制排序节点
        RelCollation newCollation, @Nullable RexNode offset, @Nullable RexNode fetch) { // 参数：新的特征集、输入、排序规则、偏移量、获取数量
      return new BindableSort(getCluster(), traitSet, newInput, newCollation, // 创建并返回新的BindableSort实例
          offset, fetch); // 传递偏移量和获取数量
    }

    @Override public Class<Object[]> getElementType() { // 重写getElementType方法，获取元素类型
      return Object[].class; // 返回Object数组类型
    }

    @Override public Enumerable<@Nullable Object[]> bind(DataContext dataContext) { // 重写bind方法，绑定数据上下文并执行
      return help(dataContext, this); // 调用help方法，返回可枚举的结果集
    }

    @Override public Node implement(InterpreterImplementor implementor) { // 重写implement方法，实现解释器节点
      return new SortNode(implementor.compiler, this); // 创建并返回SortNode节点
    }
  }

  /**
   * Rule to convert a {@link org.apache.calcite.rel.logical.LogicalJoin} // 将逻辑连接转换为BindableJoin的规则
   * to a {@link BindableJoin}. // 转换为BindableJoin
   *
   * @see #BINDABLE_JOIN_RULE // 参见BINDABLE_JOIN_RULE常量
   */
  public static class BindableJoinRule extends ConverterRule { // 定义BindableJoinRule类，继承ConverterRule
    /** Default configuration. */ // 默认配置
    public static final Config DEFAULT_CONFIG = Config.INSTANCE // 定义默认配置常量
        .withConversion(LogicalJoin.class, Convention.NONE, // 设置转换：LogicalJoin类
            BindableConvention.INSTANCE, "BindableJoinRule") // 从NONE约定转换为BindableConvention约定
        .withRuleFactory(BindableJoinRule::new); // 设置规则工厂

    /** Called from the Config. */ // 从配置调用的构造函数
    protected BindableJoinRule(Config config) { // 受保护的构造函数，接收配置对象
      super(config); // 调用父类ConverterRule的构造函数
    }

    @Override public RelNode convert(RelNode rel) { // 重写convert方法，转换关系表达式
      final LogicalJoin join = (LogicalJoin) rel; // 将输入转换为LogicalJoin
      final BindableConvention out = BindableConvention.INSTANCE; // 获取BindableConvention实例
      final RelTraitSet traitSet = join.getTraitSet().replace(out); // 创建特征集并替换为BindableConvention约定
      return new BindableJoin(rel.getCluster(), traitSet, // 创建BindableJoin实例
          convert(join.getLeft(), // 转换左输入关系表达式
              join.getLeft().getTraitSet() // 获取左输入的特征集
                  .replace(BindableConvention.INSTANCE)), // 替换为BindableConvention约定
          convert(join.getRight(), // 转换右输入关系表达式
              join.getRight().getTraitSet() // 获取右输入的特征集
                  .replace(BindableConvention.INSTANCE)), // 替换为BindableConvention约定
          join.getCondition(), join.getVariablesSet(), join.getJoinType()); // 传递连接条件、变量集合和连接类型
    }
  }

  /** Implementation of {@link org.apache.calcite.rel.core.Join} in // Join操作在Bindable约定中的实现
   * bindable calling convention. */ // 在Bindable调用约定中实现
  public static class BindableJoin extends Join implements BindableRel { // 定义BindableJoin类，继承Join并实现BindableRel
    /** Creates a BindableJoin. */ // 创建BindableJoin实例
    protected BindableJoin(RelOptCluster cluster, RelTraitSet traitSet, // 受保护的构造函数，接收集群、特征集、左右输入、连接条件、变量集合和连接类型
        RelNode left, RelNode right, RexNode condition, // 参数：左输入关系表达式、右输入关系表达式、连接条件表达式
        Set<CorrelationId> variablesSet, JoinRelType joinType) { // 参数：相关变量集合、连接类型
      super(cluster, traitSet, ImmutableList.of(), left, right, // 调用父类Join的构造函数
          condition, variablesSet, joinType); // 传递连接条件、变量集合和连接类型
    }

    @Deprecated // to be removed before 2.0 // 已废弃，将在2.0版本前移除
    protected BindableJoin(RelOptCluster cluster, RelTraitSet traitSet, // 废弃的构造函数，使用旧的变量停止机制
        RelNode left, RelNode right, RexNode condition, JoinRelType joinType, // 参数：左右输入、连接条件、连接类型
        Set<String> variablesStopped) { // 参数：变量停止集合（已废弃）
      this(cluster, traitSet, left, right, condition, // 调用新的构造函数
          CorrelationId.setOf(variablesStopped), joinType); // 将字符串集合转换为CorrelationId集合
    }

    @Override public BindableJoin copy(RelTraitSet traitSet, RexNode conditionExpr, // 重写copy方法，复制连接节点
        RelNode left, RelNode right, JoinRelType joinType, // 参数：新的特征集、连接条件、左右输入、连接类型
        boolean semiJoinDone) { // 参数：半连接完成标志（未使用）
      return new BindableJoin(getCluster(), traitSet, left, right, // 创建并返回新的BindableJoin实例
          conditionExpr, variablesSet, joinType); // 传递连接条件、变量集合和连接类型
    }

    @Override public Class<Object[]> getElementType() { // 重写getElementType方法，获取元素类型
      return Object[].class; // 返回Object数组类型
    }

    @Override public Enumerable<@Nullable Object[]> bind(DataContext dataContext) { // 重写bind方法，绑定数据上下文并执行
      return help(dataContext, this); // 调用help方法，返回可枚举的结果集
    }

    @Override public Node implement(InterpreterImplementor implementor) { // 重写implement方法，实现解释器节点
      return new JoinNode(implementor.compiler, this); // 创建并返回JoinNode节点
    }
  }

  /**
   * Rule to convert an {@link SetOp} to a {@link BindableUnion} // 将集合操作转换为BindableUnion、BindableIntersect或BindableMinus的规则
   * or {@link BindableIntersect} or {@link BindableMinus}. // 或转换为BindableIntersect或BindableMinus
   *
   * @see #BINDABLE_SET_OP_RULE // 参见BINDABLE_SET_OP_RULE常量
   */
  public static class BindableSetOpRule extends ConverterRule { // 定义BindableSetOpRule类，继承ConverterRule
    /** Default configuration. */ // 默认配置
    public static final Config DEFAULT_CONFIG = Config.INSTANCE // 定义默认配置常量
        .withConversion(SetOp.class, Convention.NONE, // 设置转换：SetOp类
            BindableConvention.INSTANCE, "BindableSetOpRule") // 从NONE约定转换为BindableConvention约定
        .withRuleFactory(BindableSetOpRule::new); // 设置规则工厂

    /** Called from the Config. */ // 从配置调用的构造函数
    protected BindableSetOpRule(Config config) { // 受保护的构造函数，接收配置对象
      super(config); // 调用父类ConverterRule的构造函数
    }

    @Override public RelNode convert(RelNode rel) { // 重写convert方法，转换关系表达式
      final SetOp setOp = (SetOp) rel; // 将输入转换为SetOp
      final BindableConvention out = BindableConvention.INSTANCE; // 获取BindableConvention实例
      final RelTraitSet traitSet = setOp.getTraitSet().replace(out); // 创建特征集并替换为BindableConvention约定
      if (setOp instanceof LogicalUnion) { // 如果是逻辑并集
        return new BindableUnion(rel.getCluster(), traitSet, // 创建BindableUnion实例
            convertList(setOp.getInputs(), out), setOp.all); // 转换输入列表，传递all标志
      } else if (setOp instanceof LogicalIntersect) { // 如果是逻辑交集
        return new BindableIntersect(rel.getCluster(), traitSet, // 创建BindableIntersect实例
            convertList(setOp.getInputs(), out), setOp.all); // 转换输入列表，传递all标志
      } else { // 否则是逻辑差集
        return new BindableMinus(rel.getCluster(), traitSet, // 创建BindableMinus实例
            convertList(setOp.getInputs(), out), setOp.all); // 转换输入列表，传递all标志
      }
    }
  }

  /** Implementation of {@link org.apache.calcite.rel.core.Union} in // Union操作在Bindable约定中的实现
   * bindable calling convention. */ // 在Bindable调用约定中实现
  public static class BindableUnion extends Union implements BindableRel { // 定义BindableUnion类，继承Union并实现BindableRel
    public BindableUnion(RelOptCluster cluster, RelTraitSet traitSet, // 构造函数，接收集群、特征集、输入列表和all标志
        List<RelNode> inputs, boolean all) { // 参数：输入关系表达式列表、是否保留重复行
      super(cluster, traitSet, inputs, all); // 调用父类Union的构造函数
    }

    @Override public BindableUnion copy(RelTraitSet traitSet, List<RelNode> inputs, // 重写copy方法，复制并集节点
        boolean all) { // 参数：新的特征集、输入列表、是否保留重复行
      return new BindableUnion(getCluster(), traitSet, inputs, all); // 创建并返回新的BindableUnion实例
    }

    @Override public Class<Object[]> getElementType() { // 重写getElementType方法，获取元素类型
      return Object[].class; // 返回Object数组类型
    }

    @Override public Enumerable<@Nullable Object[]> bind(DataContext dataContext) { // 重写bind方法，绑定数据上下文并执行
      return help(dataContext, this); // 调用help方法，返回可枚举的结果集
    }

    @Override public Node implement(InterpreterImplementor implementor) { // 重写implement方法，实现解释器节点
      return new SetOpNode(implementor.compiler, this); // 创建并返回SetOpNode节点
    }
  }

  /** Implementation of {@link org.apache.calcite.rel.core.Intersect} in // Intersect操作在Bindable约定中的实现
   * bindable calling convention. */ // 在Bindable调用约定中实现
  public static class BindableIntersect extends Intersect implements BindableRel { // 定义BindableIntersect类，继承Intersect并实现BindableRel
    public BindableIntersect(RelOptCluster cluster, RelTraitSet traitSet, // 构造函数，接收集群、特征集、输入列表和all标志
        List<RelNode> inputs, boolean all) { // 参数：输入关系表达式列表、是否保留重复行
      super(cluster, traitSet, inputs, all); // 调用父类Intersect的构造函数
    }

    @Override public BindableIntersect copy(RelTraitSet traitSet, List<RelNode> inputs, // 重写copy方法，复制交集节点
        boolean all) { // 参数：新的特征集、输入列表、是否保留重复行
      return new BindableIntersect(getCluster(), traitSet, inputs, all); // 创建并返回新的BindableIntersect实例
    }

    @Override public Class<Object[]> getElementType() { // 重写getElementType方法，获取元素类型
      return Object[].class; // 返回Object数组类型
    }

    @Override public Enumerable<@Nullable Object[]> bind(DataContext dataContext) { // 重写bind方法，绑定数据上下文并执行
      return help(dataContext, this); // 调用help方法，返回可枚举的结果集
    }

    @Override public Node implement(InterpreterImplementor implementor) { // 重写implement方法，实现解释器节点
      return new SetOpNode(implementor.compiler, this); // 创建并返回SetOpNode节点
    }
  }

  /** Implementation of {@link org.apache.calcite.rel.core.Minus} in // Minus操作在Bindable约定中的实现
   * bindable calling convention. */ // 在Bindable调用约定中实现
  public static class BindableMinus extends Minus implements BindableRel { // 定义BindableMinus类，继承Minus并实现BindableRel
    public BindableMinus(RelOptCluster cluster, RelTraitSet traitSet, // 构造函数，接收集群、特征集、输入列表和all标志
        List<RelNode> inputs, boolean all) { // 参数：输入关系表达式列表、是否保留重复行
      super(cluster, traitSet, inputs, all); // 调用父类Minus的构造函数
    }

    @Override public BindableMinus copy(RelTraitSet traitSet, List<RelNode> inputs, boolean all) { // 重写copy方法，复制差集节点
      return new BindableMinus(getCluster(), traitSet, inputs, all); // 创建并返回新的BindableMinus实例
    }

    @Override public Class<Object[]> getElementType() { // 重写getElementType方法，获取元素类型
      return Object[].class; // 返回Object数组类型
    }

    @Override public Enumerable<@Nullable Object[]> bind(DataContext dataContext) { // 重写bind方法，绑定数据上下文并执行
      return help(dataContext, this); // 调用help方法，返回可枚举的结果集
    }

    @Override public Node implement(InterpreterImplementor implementor) { // 重写implement方法，实现解释器节点
      return new SetOpNode(implementor.compiler, this); // 创建并返回SetOpNode节点
    }
  }

  /** Implementation of {@link org.apache.calcite.rel.core.Values} // Values操作在Bindable约定中的实现
   * in bindable calling convention. */ // 在Bindable调用约定中实现
  public static class BindableValues extends Values implements BindableRel { // 定义BindableValues类，继承Values并实现BindableRel
    BindableValues(RelOptCluster cluster, RelDataType rowType, // 构造函数，接收集群、行类型、元组列表和特征集
        ImmutableList<ImmutableList<RexLiteral>> tuples, RelTraitSet traitSet) { // 参数：元组列表（每个元组是常量列表）、特征集
      super(cluster, rowType, tuples, traitSet); // 调用父类Values的构造函数
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，复制Values节点
      assert inputs.isEmpty(); // 断言输入列表为空（Values没有输入）
      return new BindableValues(getCluster(), getRowType(), tuples, traitSet); // 创建并返回新的BindableValues实例
    }

    @Override public Class<Object[]> getElementType() { // 重写getElementType方法，获取元素类型
      return Object[].class; // 返回Object数组类型
    }

    @Override public Enumerable<@Nullable Object[]> bind(DataContext dataContext) { // 重写bind方法，绑定数据上下文并执行
      return help(dataContext, this); // 调用help方法，返回可枚举的结果集
    }

    @Override public Node implement(InterpreterImplementor implementor) { // 重写implement方法，实现解释器节点
      return new ValuesNode(implementor.compiler, this); // 创建并返回ValuesNode节点
    }
  }

  /** Rule that converts a {@link Values} to bindable convention. // 将Values操作转换为Bindable约定的规则
   *
   * @see #BINDABLE_VALUES_RULE */ // 参见BINDABLE_VALUES_RULE常量
  public static class BindableValuesRule extends ConverterRule { // 定义BindableValuesRule类，继承ConverterRule
    /** Default configuration. */ // 默认配置
    public static final Config DEFAULT_CONFIG = Config.INSTANCE // 定义默认配置常量
        .withConversion(LogicalValues.class, Convention.NONE, // 设置转换：LogicalValues类
            BindableConvention.INSTANCE, "BindableValuesRule") // 从NONE约定转换为BindableConvention约定
        .withRuleFactory(BindableValuesRule::new); // 设置规则工厂

    /** Called from the Config. */ // 从配置调用的构造函数
    protected BindableValuesRule(Config config) { // 受保护的构造函数，接收配置对象
      super(config); // 调用父类ConverterRule的构造函数
    }

    @Override public RelNode convert(RelNode rel) { // 重写convert方法，转换关系表达式
      LogicalValues values = (LogicalValues) rel; // 将输入转换为LogicalValues
      return new BindableValues(values.getCluster(), values.getRowType(), // 创建BindableValues实例
          values.getTuples(), // 传递元组列表
          values.getTraitSet().replace(BindableConvention.INSTANCE)); // 替换特征集为BindableConvention约定
    }
  }

  /** Implementation of {@link org.apache.calcite.rel.core.Aggregate} // Aggregate操作在Bindable约定中的实现
   * in bindable calling convention. */ // 在Bindable调用约定中实现
  public static class BindableAggregate extends Aggregate // 定义BindableAggregate类，继承Aggregate
      implements BindableRel { // 实现BindableRel接口
    public BindableAggregate( // 构造函数，用于创建BindableAggregate实例
        RelOptCluster cluster, // 参数：关系表达式优化集群
        RelTraitSet traitSet, // 参数：特征集
        RelNode input, // 参数：输入关系表达式
        ImmutableBitSet groupSet, // 参数：分组字段集合
        @Nullable List<ImmutableBitSet> groupSets, // 参数：分组字段集合列表（用于多级分组）
        List<AggregateCall> aggCalls) // 参数：聚合调用列表
        throws InvalidRelException { // 可能抛出无效关系表达式异常
      super(cluster, traitSet, ImmutableList.of(), input, groupSet, groupSets, aggCalls); // 调用父类Aggregate的构造函数
      assert getConvention() instanceof BindableConvention; // 断言约定是BindableConvention

      for (AggregateCall aggCall : aggCalls) { // 遍历所有聚合调用
        if (aggCall.isDistinct()) { // 如果是DISTINCT聚合
          throw new InvalidRelException( // 抛出异常
              "distinct aggregation not supported"); // DISTINCT聚合不被支持
        }
        if (aggCall.distinctKeys != null) { // 如果有distinctKeys
          throw new InvalidRelException( // 抛出异常
              "within-distinct aggregation not supported"); // WITHIN DISTINCT聚合不被支持
        }
        AggImplementor implementor2 = // 获取聚合实现器
            RexImpTable.INSTANCE.get(aggCall.getAggregation(), false); // 从RexImpTable获取实现器
        if (implementor2 == null) { // 如果找不到实现器
          throw new InvalidRelException( // 抛出异常
              "aggregation " + aggCall.getAggregation() + " not supported"); // 该聚合函数不被支持
        }
      }
    }

    @Deprecated // to be removed before 2.0 // 已废弃，将在2.0版本前移除
    public BindableAggregate(RelOptCluster cluster, RelTraitSet traitSet, // 废弃的构造函数，使用旧的indicator标志
        RelNode input, boolean indicator, ImmutableBitSet groupSet, // 参数：输入、indicator标志、分组字段集合
        List<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls) // 参数：分组集合列表、聚合调用列表
        throws InvalidRelException { // 可能抛出无效关系表达式异常
      this(cluster, traitSet, input, groupSet, groupSets, aggCalls); // 调用新的构造函数
      checkIndicator(indicator); // 检查indicator标志
    }

    @Override public BindableAggregate copy(RelTraitSet traitSet, RelNode input, // 重写copy方法，复制聚合节点
        ImmutableBitSet groupSet, // 参数：新的特征集、输入、分组字段集合
        @Nullable List<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls) { // 参数：分组集合列表、聚合调用列表
      try { // 尝试创建新实例
        return new BindableAggregate(getCluster(), traitSet, input, // 创建并返回新的BindableAggregate实例
            groupSet, groupSets, aggCalls); // 传递所有参数
      } catch (InvalidRelException e) { // 捕获无效关系表达式异常
        // Semantic error not possible. Must be a bug. Convert to // 语义错误不可能发生，必须是bug。转换为
        // internal error. // 内部错误
        throw new AssertionError(e); // 抛出断言错误
      }
    }

    @Override public Class<Object[]> getElementType() { // 重写getElementType方法，获取元素类型
      return Object[].class; // 返回Object数组类型
    }

    @Override public Enumerable<@Nullable Object[]> bind(DataContext dataContext) { // 重写bind方法，绑定数据上下文并执行
      return help(dataContext, this); // 调用help方法，返回可枚举的结果集
    }

    @Override public Node implement(InterpreterImplementor implementor) { // 重写implement方法，实现解释器节点
      return new AggregateNode(implementor.compiler, this); // 创建并返回AggregateNode节点
    }
  }

  /** Rule that converts an {@link Aggregate} to bindable convention. // 将Aggregate操作转换为Bindable约定的规则
   *
   * @see #BINDABLE_AGGREGATE_RULE */ // 参见BINDABLE_AGGREGATE_RULE常量
  public static class BindableAggregateRule extends ConverterRule { // 定义BindableAggregateRule类，继承ConverterRule
    /** Default configuration. */ // 默认配置
    public static final Config DEFAULT_CONFIG = Config.INSTANCE // 定义默认配置常量
        .withConversion(LogicalAggregate.class, Convention.NONE, // 设置转换：LogicalAggregate类
            BindableConvention.INSTANCE, "BindableAggregateRule") // 从NONE约定转换为BindableConvention约定
        .withRuleFactory(BindableAggregateRule::new); // 设置规则工厂

    /** Called from the Config. */ // 从配置调用的构造函数
    protected BindableAggregateRule(Config config) { // 受保护的构造函数，接收配置对象
      super(config); // 调用父类ConverterRule的构造函数
    }

    @Override public @Nullable RelNode convert(RelNode rel) { // 重写convert方法，转换关系表达式，返回可能为null的结果
      final LogicalAggregate agg = (LogicalAggregate) rel; // 将输入转换为LogicalAggregate
      final RelTraitSet traitSet = // 创建特征集
          agg.getTraitSet().replace(BindableConvention.INSTANCE); // 替换为BindableConvention约定
      try { // 尝试转换
        return new BindableAggregate(rel.getCluster(), traitSet, // 创建BindableAggregate实例
            convert(agg.getInput(), traitSet), false, agg.getGroupSet(), // 转换输入，传递false（indicator）、分组集合
            agg.getGroupSets(), agg.getAggCallList()); // 传递分组集合列表和聚合调用列表
      } catch (InvalidRelException e) { // 捕获无效关系表达式异常
        RelOptPlanner.LOGGER.debug(e.toString()); // 记录调试日志
        return null; // 返回null，表示转换失败
      }
    }
  }

  /** Implementation of {@link org.apache.calcite.rel.core.Window} // Window操作在Bindable约定中的实现

     * in bindable convention. */ // 在Bindable约定中实现

    public static class BindableWindow extends Window implements BindableRel { // 定义BindableWindow类，继承Window并实现BindableRel

      /** Creates a BindableWindow. */ // 创建BindableWindow实例

      BindableWindow(RelOptCluster cluster, RelTraitSet traitSet, RelNode input, // 构造函数，接收集群、特征集、输入、常量、行类型和分组

          List<RexLiteral> constants, RelDataType rowType, List<Group> groups) { // 参数：常量列表、行类型、分组列表

        super(cluster, traitSet, input, constants, rowType, groups); // 调用父类Window的构造函数

      }

  

      @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，复制窗口节点

        return new BindableWindow(getCluster(), traitSet, sole(inputs), // 创建并返回新的BindableWindow实例

            constants, getRowType(), groups); // 传递常量、行类型和分组

      }

  

      @Override public Window copy(List<RexLiteral> constants) { // 重写copy方法，复制窗口节点（仅改变常量）

        return new BindableWindow(getCluster(), traitSet, getInput(), // 创建并返回新的BindableWindow实例

            constants, getRowType(), groups); // 传递新的常量

      }

  

      @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法，计算自身成本，返回可能为null的结果

          RelMetadataQuery mq) { // 参数：优化器和元数据查询

        RelOptCost cost = super.computeSelfCost(planner, mq); // 调用父类方法计算基础成本

        if (cost == null) { // 如果成本为null

          return null; // 直接返回null

        }

        return cost.multiplyBy(BindableConvention.COST_MULTIPLIER); // 返回成本乘以BindableConvention的成本乘数

      }

  

      @Override public Class<Object[]> getElementType() { // 重写getElementType方法，获取元素类型

        return Object[].class; // 返回Object数组类型

      }

  

      @Override public Enumerable<@Nullable Object[]> bind(DataContext dataContext) { // 重写bind方法，绑定数据上下文并执行

        return help(dataContext, this); // 调用help方法，返回可枚举的结果集

      }

  

      @Override public Node implement(InterpreterImplementor implementor) { // 重写implement方法，实现解释器节点

        return new WindowNode(implementor.compiler, this); // 创建并返回WindowNode节点

      }

    }

  

    /** Rule to convert a {@link org.apache.calcite.rel.logical.LogicalWindow} // 将逻辑窗口函数转换为BindableWindow的规则

     * to a {@link BindableWindow}. // 转换为BindableWindow

     *

     * @see #BINDABLE_WINDOW_RULE // 参见BINDABLE_WINDOW_RULE常量

     */

    public static class BindableWindowRule extends ConverterRule { // 定义BindableWindowRule类，继承ConverterRule

      /** Default configuration. */ // 默认配置

      public static final Config DEFAULT_CONFIG = Config.INSTANCE // 定义默认配置常量

          .withConversion(LogicalWindow.class, Convention.NONE, // 设置转换：LogicalWindow类

              BindableConvention.INSTANCE, "BindableWindowRule") // 从NONE约定转换为BindableConvention约定

          .withRuleFactory(BindableWindowRule::new); // 设置规则工厂

  

      /** Called from the Config. */ // 从配置调用的构造函数

      protected BindableWindowRule(Config config) { // 受保护的构造函数，接收配置对象

        super(config); // 调用父类ConverterRule的构造函数

      }

  

      @Override public RelNode convert(RelNode rel) { // 重写convert方法，转换关系表达式

        final LogicalWindow winAgg = (LogicalWindow) rel; // 将输入转换为LogicalWindow

        final RelTraitSet traitSet = // 创建特征集

            winAgg.getTraitSet().replace(BindableConvention.INSTANCE); // 替换为BindableConvention约定

        final RelNode input = winAgg.getInput(); // 获取输入关系表达式

        final RelNode convertedInput = // 转换输入关系表达式

            convert(input, // 转换输入

                input.getTraitSet().replace(BindableConvention.INSTANCE)); // 替换输入的特征集为BindableConvention约定

        return new BindableWindow(rel.getCluster(), traitSet, convertedInput, // 创建BindableWindow实例

            winAgg.getConstants(), winAgg.getRowType(), winAgg.groups); // 传递常量、行类型和分组

      }

    }

  /** Implementation of {@link org.apache.calcite.rel.core.Match} // Match操作（模式匹配）在Bindable约定中的实现
   * in bindable convention. */ // 在Bindable约定中实现
  public static class BindableMatch extends Match implements BindableRel { // 定义BindableMatch类，继承Match并实现BindableRel
    /** Singleton instance of BindableMatch. */ // BindableMatch的单例实例（注释不准确，实际不是单例）
    BindableMatch(RelOptCluster cluster, RelTraitSet traitSet, RelNode input, // 构造函数，接收大量参数用于模式匹配
        RelDataType rowType, RexNode pattern, boolean strictStart, // 参数：行类型、模式表达式、严格开始标志
        boolean strictEnd, Map<String, RexNode> patternDefinitions, // 参数：严格结束标志、模式定义映射
        Map<String, RexNode> measures, RexNode after, // 参数：度量映射、AFTER子句
        Map<String, ? extends SortedSet<String>> subsets, boolean allRows, // 参数：子集映射、是否返回所有行
        ImmutableBitSet partitionKeys, RelCollation orderKeys, // 参数：分区键集合、排序规则
        @Nullable RexNode interval) { // 参数：时间间隔表达式（可能为null）
      super(cluster, traitSet, input, rowType, pattern, strictStart, strictEnd, // 调用父类Match的构造函数
          patternDefinitions, measures, after, subsets, allRows, partitionKeys, // 传递所有参数
          orderKeys, interval); // 传递排序规则和时间间隔
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，复制模式匹配节点
      return new BindableMatch(getCluster(), traitSet, inputs.get(0), getRowType(), // 创建并返回新的BindableMatch实例
          pattern, strictStart, strictEnd, patternDefinitions, measures, after, // 传递所有模式匹配参数
          subsets, allRows, partitionKeys, orderKeys, interval); // 传递分区键、排序和时间间隔
    }

    @Override public Class<Object[]> getElementType() { // 重写getElementType方法，获取元素类型
      return Object[].class; // 返回Object数组类型
    }

    @Override public Enumerable<@Nullable Object[]> bind(DataContext dataContext) { // 重写bind方法，绑定数据上下文并执行
      return help(dataContext, this); // 调用help方法，返回可枚举的结果集
    }

    @Override public Node implement(InterpreterImplementor implementor) { // 重写implement方法，实现解释器节点
      return new MatchNode(implementor.compiler, this); // 创建并返回MatchNode节点
    }
  }

  /**
   * Rule to convert a {@link org.apache.calcite.rel.logical.LogicalMatch} // 将逻辑模式匹配转换为BindableMatch的规则
   * to a {@link BindableMatch}. // 转换为BindableMatch
   *
   * @see #BINDABLE_MATCH_RULE // 参见BINDABLE_MATCH_RULE常量
   */
  public static class BindableMatchRule extends ConverterRule { // 定义BindableMatchRule类，继承ConverterRule
    /** Default configuration. */ // 默认配置
    public static final Config DEFAULT_CONFIG = Config.INSTANCE // 定义默认配置常量
        .withConversion(LogicalMatch.class, Convention.NONE, // 设置转换：LogicalMatch类
            BindableConvention.INSTANCE, "BindableMatchRule") // 从NONE约定转换为BindableConvention约定
        .withRuleFactory(BindableMatchRule::new); // 设置规则工厂

    /** Called from the Config. */ // 从配置调用的构造函数
    protected BindableMatchRule(Config config) { // 受保护的构造函数，接收配置对象
      super(config); // 调用父类ConverterRule的构造函数
    }

    @Override public RelNode convert(RelNode rel) { // 重写convert方法，转换关系表达式
      final LogicalMatch match = (LogicalMatch) rel; // 将输入转换为LogicalMatch
      final RelTraitSet traitSet = // 创建特征集
          match.getTraitSet().replace(BindableConvention.INSTANCE); // 替换为BindableConvention约定
      final RelNode input = match.getInput(); // 获取输入关系表达式
      final RelNode convertedInput = // 转换输入关系表达式
          convert(input, // 转换输入
              input.getTraitSet().replace(BindableConvention.INSTANCE)); // 替换输入的特征集为BindableConvention约定
      return new BindableMatch(rel.getCluster(), traitSet, convertedInput, // 创建BindableMatch实例
          match.getRowType(), match.getPattern(), match.isStrictStart(), // 传递行类型、模式、严格开始标志
          match.isStrictEnd(), match.getPatternDefinitions(), // 传递严格结束标志、模式定义
          match.getMeasures(), match.getAfter(), match.getSubsets(), // 传递度量、AFTER子句、子集
          match.isAllRows(), match.getPartitionKeys(), match.getOrderKeys(), // 传递是否返回所有行、分区键、排序规则
          match.getInterval()); // 传递时间间隔
    }
  }

} // 类定义结束
