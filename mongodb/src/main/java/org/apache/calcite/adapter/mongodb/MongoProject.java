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
package org.apache.calcite.adapter.mongodb; // 声明包名，该类属于org.apache.calcite.adapter.mongodb包，是Calcite MongoDB适配器的一部分

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂，用于创建和管理Java类型
import org.apache.calcite.plan.RelOptCluster; // 导入关系表达式优化集群，包含查询优化所需的全局信息
import org.apache.calcite.plan.RelOptCost; // 导入关系表达式成本模型，用于估算执行成本
import org.apache.calcite.plan.RelOptPlanner; // 导入关系表达式优化器接口
import org.apache.calcite.plan.RelTraitSet; // 导入关系表达式特征集合，定义物理属性如约定、排序等
import org.apache.calcite.rel.RelNode; // 导入关系表达式节点接口，所有关系操作符的基类
import org.apache.calcite.rel.core.Project; // 导入Project关系操作符基类，用于投影操作（选择列、计算表达式）
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入关系元数据查询接口，用于获取统计信息等
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型，描述行的结构
import org.apache.calcite.rex.RexNode; // 导入行表达式节点，代表SQL表达式
import org.apache.calcite.util.Pair; // 导入键值对工具类
import org.apache.calcite.util.Util; // 导入通用工具类

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表
import com.google.common.collect.ImmutableSet; // 导入Google Guava的不可变集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入空值检查注解

import java.util.ArrayList; // 导入Java ArrayList动态数组
import java.util.List; // 导入Java List接口

/**
 * Implementation of {@link org.apache.calcite.rel.core.Project}
 * relational expression in MongoDB.
 */ // 类文档注释：这是MongoDB中Project关系表达式的实现类，用于将Calcite的投影操作转换为MongoDB的$project聚合操作
public class MongoProject extends Project implements MongoRel { // MongoProject类继承自Project基类并实现MongoRel接口，表示MongoDB适配器中的投影操作节点
  public MongoProject(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法参数：cluster-关系表达式集群，包含优化器等全局信息；traitSet-特征集合，定义物理属性
      RelNode input, List<? extends RexNode> projects, RelDataType rowType) { // input-输入关系节点；projects-投影表达式列表；rowType-输出行类型
    super(cluster, traitSet, ImmutableList.of(), input, projects, rowType, ImmutableSet.of()); // 调用父类Project构造方法，ImmutableList.of()表示没有变量，ImmutableSet.of()表示没有指示器
    assert getConvention() == MongoRel.CONVENTION; // 断言：当前节点的约定必须是MongoRel.CONVENTION，确保是MongoDB物理计划
    assert getConvention() == input.getConvention(); // 断言：当前节点的约定必须与输入节点的约定一致，确保整个计划链的约定一致
  }

  @Deprecated // to be removed before 2.0 // 标记为已弃用，将在2.0版本前移除
  public MongoProject(RelOptCluster cluster, RelTraitSet traitSet, // 已弃用的构造方法，增加了flags参数（但不再使用）
      RelNode input, List<RexNode> projects, RelDataType rowType, int flags) { // flags参数已废弃，不再使用
    this(cluster, traitSet, input, projects, rowType); // 调用主构造方法，忽略flags参数
    Util.discard(flags); // 显式丢弃flags参数，避免编译器警告，表示该参数不再使用
  }

  @Override public Project copy(RelTraitSet traitSet, RelNode input, // 重写copy方法：用于创建当前节点的副本，可以修改特征集合、输入、投影表达式和行类型
      List<RexNode> projects, RelDataType rowType) { // 参数：traitSet-新的特征集合；input-新的输入节点；projects-新的投影表达式列表；rowType-新的行类型
    return new MongoProject(getCluster(), traitSet, input, projects, // 返回新的MongoProject实例，保持原有的cluster，使用新的参数
        rowType); // 使用新的行类型创建副本
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法：计算当前节点的执行成本，用于优化器选择最优执行计划
      RelMetadataQuery mq) { // 参数：planner-优化器实例；mq-元数据查询对象，用于获取统计信息
    return super.computeSelfCost(planner, mq).multiplyBy(0.1); // 调用父类的成本计算方法，然后将结果乘以0.1，表示MongoDB的投影操作成本较低，鼓励优化器使用MongoDB投影
  }

  @Override public void implement(Implementor implementor) { // 重写implement方法：将Calcite的投影操作转换为MongoDB的查询操作，这是MongoProject最核心的方法
    implementor.visitChild(0, getInput()); // 访问第一个子节点（输入节点），确保输入节点的MongoDB操作已经实现并添加到implementor中

    final MongoRules.RexToMongoTranslator translator = // 创建Rex表达式到MongoDB表达式的转换器，用于将Calcite的RexNode转换为MongoDB的表达式
        new MongoRules.RexToMongoTranslator( // 构造转换器，需要两个参数：类型工厂和字段名称列表
            (JavaTypeFactory) getCluster().getTypeFactory(), // 获取集群中的类型工厂并转换为JavaTypeFactory，用于处理Java类型映射
            MongoRules.mongoFieldNames(getInput().getRowType())); // 获取输入节点的行类型，并转换为MongoDB字段名称列表
    final List<String> items = new ArrayList<>(); // 创建字符串列表，用于存储每个投影项的MongoDB表达式
    for (Pair<RexNode, String> pair : getNamedProjects()) { // 遍历所有命名投影项，每个pair包含RexNode表达式和对应的字段名
      final String name = pair.right; // 获取投影项的字段名称（输出的列名）
      final String expr = pair.left.accept(translator); // 使用转换器将RexNode表达式转换为MongoDB表达式字符串
      items.add(expr.equals("'$" + name + "'") // 判断转换后的表达式是否是简单的字段引用（如'$fieldName'）
          ? MongoRules.maybeQuote(name) + ": 1" // 如果是简单字段引用，则使用MongoDB的包含语法（fieldName: 1）表示选择该字段
          : MongoRules.maybeQuote(name) + ": " + expr); // 如果是复杂表达式，则使用字段名映射到表达式的语法（fieldName: expression）
    } // 循环结束，所有投影项都已转换为MongoDB表达式
    final String findString = Util.toString(items, "{", ", ", "}"); // 将所有投影项拼接成MongoDB的find查询字符串，格式为{field1: expr1, field2: expr2}
    final String aggregateString = "{$project: " + findString + "}"; // 将find字符串包装成MongoDB的$project聚合操作字符串
    final Pair<String, String> op = Pair.of(findString, aggregateString); // 创建键值对，第一个元素是find字符串，第二个元素是聚合字符串
    implementor.add(op.left, op.right); // 将生成的MongoDB操作添加到implementor中，用于最终生成MongoDB查询
  }
} // 类定义结束
