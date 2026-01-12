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
package org.apache.calcite.adapter.geode.rel; // 指定包路径：org.apache.calcite.adapter.geode.rel，表示这是Geode适配器中rel（关系表达式）包下的类

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，用于表示关系表达式集群，包含查询优化器的相关信息
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost类，用于表示关系操作符的代价估算
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner类，表示查询优化器，用于选择最优的执行计划
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合，如约定、排序等物理属性
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数中的节点，是所有关系表达式的基础接口
import org.apache.calcite.rel.core.Aggregate; // 导入Aggregate类，表示聚合操作的关系表达式，如GROUP BY、SUM、COUNT等
import org.apache.calcite.rel.core.AggregateCall; // 导入AggregateCall类，表示聚合函数调用，包含聚合函数类型和参数信息
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系表达式的元数据信息
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据的类型，包含字段信息
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField类，表示关系数据类型中的单个字段
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，表示不可变的位集合，用于高效地表示字段索引集合
import org.apache.calcite.util.Util; // 导入Util工具类，提供各种实用方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，表示不可变的列表
import com.google.common.collect.ImmutableMap; // 导入Google Guava的ImmutableMap类，表示不可变的映射

import org.checkerframework.checker.nullness.qual.Nullable; // 导入注解，用于标记可能为null的返回值

import java.util.ArrayList; // 导入ArrayList类，表示动态数组列表
import java.util.List; // 导入List接口，表示有序集合

import static java.util.Objects.requireNonNull; // 静态导入Objects.requireNonNull方法，用于检查对象是否为null

/**
 * Implementation of
 * {@link org.apache.calcite.rel.core.Aggregate} relational expression
 * in Geode.
 * 
 * GeodeAggregate类：这是Apache Calcite框架中针对Geode数据源的聚合操作实现类
 * 
 * 【类的作用】：
 * 1. 继承自Aggregate基类，实现GeodeRel接口，表示在Geode数据源上执行的聚合操作
 * 2. 负责将SQL中的聚合操作（如GROUP BY、SUM、COUNT、MAX、MIN、AVG等）转换为Geode OQL（Object Query Language）查询
 * 3. 作为关系表达式树中的一个节点，接收输入关系表达式（如表扫描、过滤等），对其数据进行分组和聚合计算
 * 4. 处理Geode特有的OQL语法限制，例如处理count(*)的特殊情况
 * 5. 提供代价估算方法，帮助查询优化器选择最优的执行计划
 * 
 * 【核心功能】：
 * - 将Calcite的逻辑聚合操作转换为Geode的OQL聚合查询
 * - 支持GROUP BY分组操作
 * - 支持常见的聚合函数（SUM、COUNT、AVG、MAX、MIN等）
 * - 处理聚合函数的参数和别名
 * - 提供代价计算，使优化器能够评估该操作的执行成本
 * 
 * 【使用场景】：
 * 当用户执行包含GROUP BY或聚合函数的SQL查询时，Calcite查询优化器会创建GeodeAggregate节点
 * 例如：SELECT dept_id, COUNT(*) FROM emp GROUP BY dept_id
 */
public class GeodeAggregate extends Aggregate implements GeodeRel { // 定义GeodeAggregate类，继承Aggregate并实现GeodeRel接口

  /** Creates a GeodeAggregate. */ // 创建GeodeAggregate的构造方法文档注释
  public GeodeAggregate(RelOptCluster cluster, // 参数：cluster - 关系表达式集群，包含查询优化器和类型工厂等共享资源
      RelTraitSet traitSet, // 参数：traitSet - 特征集合，定义物理属性（如约定、排序规则）
      RelNode input, // 参数：input - 输入关系表达式，通常是表扫描或过滤操作的结果
      ImmutableBitSet groupSet, // 参数：groupSet - 分组字段的位集合，表示GROUP BY子句中的字段索引
      List<ImmutableBitSet> groupSets, // 参数：groupSets - 分组集合列表，支持GROUPING SETS（高级分组功能）
      List<AggregateCall> aggCalls) { // 参数：aggCalls - 聚合函数调用列表，包含所有要执行的聚合操作（如SUM、COUNT等）
    super(cluster, traitSet, ImmutableList.of(), input, groupSet, groupSets, aggCalls); // 调用父类Aggregate的构造方法，ImmutableList.of()表示没有indicator字段（已废弃）

    assert getConvention() == GeodeRel.CONVENTION; // 断言：验证当前节点的约定是Geode约定，确保这是Geode适配器的节点
    assert getConvention() == this.input.getConvention(); // 断言：验证输入节点的约定与当前节点一致，确保约定传播正确
    assert getConvention() == input.getConvention(); // 断言：再次验证输入节点的约定一致性（冗余检查）
    assert this.groupSets.size() == 1 : "Grouping sets not supported"; // 断言：确保groupSets只有一个元素，Geode不支持GROUPING SETS高级分组功能

    for (AggregateCall aggCall : aggCalls) { // 遍历所有聚合函数调用
      if (aggCall.isDistinct()) { // 检查是否是DISTINCT聚合（如COUNT(DISTINCT col)）
        System.out.println("DISTINCT based aggregation!"); // 输出警告信息，表示检测到DISTINCT聚合（注意：这只是打印，实际可能不支持）
      } // 结束if语句
    } // 结束for循环
  } // 结束构造方法

  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public GeodeAggregate(RelOptCluster cluster, // 参数：cluster - 关系表达式集群
      RelTraitSet traitSet, // 参数：traitSet - 特征集合
      RelNode input, // 参数：input - 输入关系表达式
      boolean indicator, // 参数：indicator - 指示器标志（已废弃，不再使用）
      ImmutableBitSet groupSet, // 参数：groupSet - 分组字段位集合
      List<ImmutableBitSet> groupSets, // 参数：groupSets - 分组集合列表
      List<AggregateCall> aggCalls) { // 参数：aggCalls - 聚合函数调用列表
    this(cluster, traitSet, input, groupSet, groupSets, aggCalls); // 调用新的构造方法（不带indicator参数）
    checkIndicator(indicator); // 调用父类方法检查indicator参数（应为false）
  } // 结束废弃的构造方法

  @Override public Aggregate copy(RelTraitSet traitSet, RelNode input, // 重写copy方法：创建当前节点的副本，用于优化器重写
      ImmutableBitSet groupSet, List<ImmutableBitSet> groupSets, // 参数：groupSet - 新的分组字段集合，groupSets - 新的分组集合列表
      List<AggregateCall> aggCalls) { // 参数：aggCalls - 新的聚合函数调用列表
    return new GeodeAggregate(getCluster(), traitSet, input, groupSet, // 返回新的GeodeAggregate实例，使用传入的新参数
        groupSets, aggCalls); // 传入groupSets和aggCalls参数
  } // 结束copy方法

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法：计算当前节点的执行代价
      RelMetadataQuery mq) { // 参数：planner - 查询优化器，mq - 元数据查询对象
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq)); // 调用父类方法计算基础代价，requireNonNull确保结果不为null
    return cost.multiplyBy(0.1); // 将代价乘以0.1，表示Geode聚合操作相对廉价，鼓励优化器选择此实现
  } // 结束computeSelfCost方法

  @Override public void implement(GeodeImplementContext geodeImplementContext) { // 重写implement方法：将聚合操作转换为Geode OQL查询
    geodeImplementContext.visitChild(getInput()); // 访问子节点（输入关系表达式），先生成子节点的OQL

    List<String> inputFields = fieldNames(getInput().getRowType()); // 获取输入关系表达式的所有字段名称列表

    List<String> groupByFields = new ArrayList<>(); // 创建列表用于存储GROUP BY子句中的字段名

    for (int group : groupSet) { // 遍历分组字段位集合中的每个字段索引
      groupByFields.add(inputFields.get(group)); // 根据索引获取字段名，添加到GROUP BY字段列表中
    } // 结束for循环

    geodeImplementContext.addGroupBy(groupByFields); // 将GROUP BY字段列表添加到实现上下文中

    // Find the aggregate functions (e.g. MAX, SUM ...) // 查找聚合函数（如MAX、SUM等）
    ImmutableMap.Builder<String, String> aggregateFunctionMap = ImmutableMap.builder(); // 创建不可变Map构建器，用于存储聚合函数名到OQL表达式的映射
    for (AggregateCall aggCall : aggCalls) { // 遍历所有聚合函数调用

      List<String> aggCallFieldNames = new ArrayList<>(); // 创建列表用于存储当前聚合函数的参数字段名
      for (int i : aggCall.getArgList()) { // 遍历聚合函数的参数索引列表
        aggCallFieldNames.add(inputFields.get(i)); // 根据索引获取字段名，添加到参数列表中
      } // 结束内层for循环
      String functionName = aggCall.getAggregation().getName(); // 获取聚合函数的名称（如"COUNT"、"SUM"等）

      // Workaround to handle count(*) case. Geode doesn't allow "AS" aliases on
      // 'count(*)' but allows it for count('any column name'). So we are
      // converting the count(*) into count (first input ColumnName).
      // 处理count(*)的变通方案。Geode不允许在count(*)上使用"AS"别名，
      // 但允许在count('任意列名')上使用。因此我们将count(*)转换为count(第一列的列名)。
      if ("COUNT".equalsIgnoreCase(functionName) && aggCallFieldNames.isEmpty()) { // 如果是COUNT函数且没有参数（即count(*)的情况）
        aggCallFieldNames.add(inputFields.get(0)); // 将第一个输入字段名添加到参数列表中，转换为count(第一列)
      } // 结束if语句

      String oqlAggregateCall = // 构建OQL聚合函数调用字符串
          Util.toString(aggCallFieldNames, functionName + "(", ", ", ")"); // 使用Util工具将字段列表格式化为"函数名(字段1, 字段2, ...)"的形式

      aggregateFunctionMap.put(aggCall.getName(), oqlAggregateCall); // 将聚合函数的别名（输出列名）映射到OQL表达式
    } // 结束外层for循环

    geodeImplementContext.addAggregateFunctions(aggregateFunctionMap.build()); // 将构建好的聚合函数映射添加到实现上下文中

  } // 结束implement方法

  private static List<String> fieldNames(RelDataType relDataType) { // 私有静态方法：从关系数据类型中提取所有字段名称
    ArrayList<String> names = new ArrayList<>(); // 创建动态数组列表用于存储字段名

    for (RelDataTypeField rdtf : relDataType.getFieldList()) { // 遍历关系数据类型中的所有字段
      names.add(rdtf.getName()); // 获取每个字段的名称，添加到列表中
    } // 结束for循环
    return names; // 返回字段名称列表
  } // 结束fieldNames方法
} // 结束GeodeAggregate类
