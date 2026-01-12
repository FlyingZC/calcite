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
// 声明当前类的包路径，位于Geode适配器的rel模块下，表示关系表达式相关类
package org.apache.calcite.adapter.geode.rel;

// 导入RexToGeodeTranslator类，用于将Calcite的Rex表达式转换为Geode查询表达式
import org.apache.calcite.adapter.geode.rel.GeodeRules.RexToGeodeTranslator;
// 导入RelOptCluster类，表示关系代数优化集群，包含查询优化器的共享信息
import org.apache.calcite.plan.RelOptCluster;
// 导入RelOptCost类，表示关系表达式的代价，用于查询优化
import org.apache.calcite.plan.RelOptCost;
// 导入RelOptPlanner类，表示关系代数优化器，用于执行查询优化
import org.apache.calcite.plan.RelOptPlanner;
// 导入RelTraitSet类，表示关系表达式的特征集合，如约定、排序等
import org.apache.calcite.plan.RelTraitSet;
// 导入RelNode接口，表示关系表达式树的节点，是所有关系表达式的基类
import org.apache.calcite.rel.RelNode;
// 导入Project类，表示投影操作的关系表达式，用于选择和计算输出列
import org.apache.calcite.rel.core.Project;
// 导入RelMetadataQuery类，用于查询关系表达式的元数据信息
import org.apache.calcite.rel.metadata.RelMetadataQuery;
// 导入RelDataType类，表示关系数据类型，描述行或列的类型信息
import org.apache.calcite.rel.type.RelDataType;
// 导入RexNode类，表示行表达式，是Calcite中表达式的抽象语法树节点
import org.apache.calcite.rex.RexNode;
// 导入Pair类，表示键值对，用于存储两个相关联的对象
import org.apache.calcite.util.Pair;

// 导入ImmutableList类，Google Guava提供的不可变列表实现
import com.google.common.collect.ImmutableList;
// 导入ImmutableSet类，Google Guava提供的不可变集合实现
import com.google.common.collect.ImmutableSet;

// 导入Nullable注解，用于标记可能为null的值，由CheckerFramework提供
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入LinkedHashMap类，保持插入顺序的Map实现，用于存储字段映射
import java.util.LinkedHashMap;
// 导入List接口，表示有序集合
import java.util.List;
// 导入Map接口，表示键值对映射
import java.util.Map;

// 导入requireNonNull静态方法，用于检查对象非空，如果为null则抛出NullPointerException
import static java.util.Objects.requireNonNull;

/**
 * GeodeProject类是Project关系表达式在Geode适配器中的具体实现
 * 
 * 【类作用详解】：
 * 1. Project操作是SQL中最常用的操作之一，对应SQL中的SELECT子句，用于：
 *    - 从输入数据源中选择特定的列（字段投影）
 *    - 对列进行计算和转换（如表达式计算、函数调用等）
 *    - 重命名列（使用AS关键字）
 * 
 * 2. 在Calcite关系代数中，Project是一个关系运算符，它接收一个输入关系节点，
 *    并产生一个输出关系，输出关系的每一行都是通过计算输入行的表达式得到的
 * 
 * 3. GeodeProject专门针对Apache Geode数据存储进行了优化，它将Calcite的抽象
 *    投影操作转换为Geode OQL（Object Query Language）的SELECT子句
 * 
 * 4. 实现GeodeRel接口，表明这是一个Geode特定的关系表达式，可以使用Geode
 *    的查询引擎来执行
 * 
 * 5. 该类在查询优化过程中会被VolcanoPlanner等优化器使用，用于生成针对Geode
 *    的最优执行计划
 * 
 * 【设计模式】：
 * - 继承Project基类，复用了Calcite关系代数的通用逻辑
 * - 实现GeodeRel接口，提供了Geode特定的实现
 * - 遵循访问者模式，通过implement方法接受GeodeImplementContext访问者
 * 
 * 【使用场景】：
 * - 当查询需要选择特定的字段时（SELECT col1, col2 FROM table）
 * - 当查询需要对字段进行计算时（SELECT col1 + col2 AS sum FROM table）
 * - 当查询需要重命名字段时（SELECT col1 AS new_name FROM table）
 * - 与其他Geode关系表达式（如GeodeFilter、GeodeScan）组合使用，构建完整的查询计划
 */
public class GeodeProject extends Project implements GeodeRel {

  // 【构造方法】GeodeProject的构造函数，用于创建一个新的GeodeProject关系表达式节点
  // 参数cluster: 关系优化集群，包含类型工厂、表达式构建器等共享资源
  GeodeProject(RelOptCluster cluster, RelTraitSet traitSet,
      RelNode input, List<? extends RexNode> projects, RelDataType rowType) {
    // 调用父类Project的构造函数，初始化Project关系表达式
    // 参数说明：
    //   - cluster: 关系优化集群，传递给父类
    //   - traitSet: 特征集合，传递给父类，包含约定、排序等特征
    //   - ImmutableList.of(): 空的变量列表，Project通常不需要变量
    //   - input: 输入关系节点，表示投影操作的数据源
    //   - projects: 投影表达式列表，每个RexNode表示一个输出列的计算逻辑
    //   - rowType: 输出行的类型，描述投影结果的行结构
    //   - ImmutableSet.of(): 空的指示器集合，Project通常不需要指示器
    super(cluster, traitSet, ImmutableList.of(), input, projects, rowType, ImmutableSet.of());
    // 断言当前节点的约定必须是GeodeRel.CONVENTION，确保这是一个Geode特定的关系表达式
    // GeodeRel.CONVENTION是一个特殊的约定，标识该关系表达式由Geode适配器处理
    assert getConvention() == GeodeRel.CONVENTION;
    // 断言输入节点的约定也必须是GeodeRel.CONVENTION
    // 这确保了整个关系表达式树都使用Geode约定，避免约定不匹配
    // 在关系代数优化中，不同约定的节点不能直接连接，需要通过规则进行转换
    assert getConvention() == input.getConvention();
  }

  // 【copy方法】创建当前GeodeProject节点的副本，可以修改部分属性
  // 这是关系表达式树的标准方法，用于在优化过程中创建修改后的节点
  // @Override注解表示重写了父类Project的方法
  @Override public Project copy(RelTraitSet traitSet, RelNode input,
      List<RexNode> projects, RelDataType rowType) {
    // 创建并返回一个新的GeodeProject对象
    // 参数说明：
    //   - getCluster(): 获取当前节点的集群对象，保持不变
    //   - traitSet: 新的特征集合，可能包含不同的约定、排序等
    //   - input: 新的输入关系节点，可能指向不同的数据源
    //   - projects: 新的投影表达式列表，可能包含不同的列或表达式
    //   - rowType: 新的输出行类型，描述新的投影结果的行结构
    // 返回值: 新创建的GeodeProject对象，是当前节点的修改副本
    return new GeodeProject(getCluster(), traitSet, input, projects, rowType);
  }

  // 【computeSelfCost方法】计算当前GeodeProject节点的执行代价
  // 在查询优化过程中，优化器需要比较不同执行计划的代价，选择最优方案
  // @Override注解表示重写了父类的方法
  // @Nullable注解表示返回值可能为null（虽然在这个实现中不会返回null）
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) {
    // 调用父类Project的computeSelfCost方法，计算基础执行代价
    // 参数说明：
    //   - planner: 关系优化器，用于访问代价计算相关的信息
    //   - mq: 元数据查询对象，用于获取关系的元数据（如行数、大小等）
    // requireNonNull确保返回的cost不为null，如果为null则抛出异常
    // 父类计算的代价通常基于：CPU代价（表达式计算）+ IO代价（数据读取）
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq));
    // 将代价乘以0.1，降低GeodeProject的相对代价
    // 【设计意图】：
    // 1. 相比于其他存储系统（如传统数据库），Geode的投影操作代价较低
    // 2. Geode是内存数据网格，数据访问速度快，投影操作开销小
    // 3. 降低代价可以鼓励优化器优先使用Geode适配器
    // 4. 这是一种启发式优化，基于对Geode性能特性的了解
    // 5. 代价系数0.1是经验值，可以根据实际性能测试调整
    // 返回值: 调整后的执行代价对象
    return cost.multiplyBy(0.1);
  }

  // 【implement方法】实现GeodeProject节点，将其转换为Geode OQL查询语句
  // 这是关系表达式执行的关键方法，将Calcite的抽象计划转换为具体的数据源查询
  // @Override注解表示实现了GeodeRel接口的方法
  @Override public void implement(GeodeImplementContext geodeImplementContext) {
    // 访问并实现子节点（输入关系节点）
    // 这会递归地处理整个关系表达式树，从叶子节点开始向上构建查询
    // 例如：如果输入是GeodeScan，会生成FROM子句；如果是GeodeFilter，会生成WHERE子句
    // geodeImplementContext维护了查询构建的上下文状态
    geodeImplementContext.visitChild(getInput());

    // 创建RexToGeodeTranslator转换器，用于将Calcite的Rex表达式转换为Geode表达式
    // 参数说明：
    //   - GeodeRules.geodeFieldNames(getInput().getRowType()): 获取输入行的Geode字段名列表
    //     - getInput().getRowType(): 获取输入节点的行类型
    //     - GeodeRules.geodeFieldNames(): 将Calcite字段名转换为Geode字段名
    // 这个转换器知道如何将Calcite的表达式语法转换为Geode OQL语法
    final RexToGeodeTranslator translator =
        new RexToGeodeTranslator(
            GeodeRules.geodeFieldNames(getInput().getRowType()));
    // 创建LinkedHashMap存储字段映射关系
    // 使用LinkedHashMap保持插入顺序，确保SELECT子句中字段的顺序与原始查询一致
    // 键: 原始字段名或表达式（来自输入）
    // 值: 输出字段名（可能重命名）
    final Map<String, String> fields = new LinkedHashMap<>();
    // 遍历所有命名的投影表达式
    // getNamedProjects()返回Pair列表，每个Pair包含：
    //   - left: RexNode表达式（表示如何计算该列）
    //   - right: 输出列名（AS后面指定的名称，或默认名称）
    for (Pair<RexNode, String> pair : getNamedProjects()) {
      // 获取输出列名（Pair的right部分）
      // 这是用户在SQL中指定的列别名，或者默认的列名
      final String name = pair.right;
      // 将RexNode表达式转换为Geode表达式字符串
      // pair.left是RexNode，表示Calcite的表达式抽象语法树
      // accept(translator)使用访问者模式，让translator遍历表达式树并生成Geode字符串
      // 转换示例：
      //   - RexLiteral(10) -> "10"
      //   - RexInputRef(0) -> "field1"
      //   - RexCall(+, field1, field2) -> "field1 + field2"
      final String originalName = pair.left.accept(translator);
      // 将字段映射添加到map中
      // 键: 原始表达式字符串（如"field1 + field2"或"field1"）
      // 值: 输出列名（如"sum"或"field1"）
      // 这个映射用于构建SELECT子句
      fields.put(originalName, name);
    }
    // 将SELECT字段添加到Geode实现上下文中
    // 这会在OQL查询中生成SELECT子句，如：SELECT field1, field2 + field3 AS sum FROM ...
    // geodeImplementContext会根据之前添加的FROM、WHERE等子句，构建完整的OQL查询
    geodeImplementContext.addSelectFields(fields);
  }
} // GeodeProject类定义结束
