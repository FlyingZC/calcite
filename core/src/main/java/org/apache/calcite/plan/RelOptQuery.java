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
// Apache许可证声明，表明此代码遵循Apache 2.0许可证开源协议
package org.apache.calcite.plan; // 定义包名，属于calcite的plan包，负责查询优化相关的功能

import org.apache.calcite.rel.RelNode; // 引入RelNode接口，代表关系代数表达式（关系运算节点）
import org.apache.calcite.rel.core.CorrelationId; // 引入CorrelationId类，用于标识相关变量（关联变量）
import org.apache.calcite.rel.type.RelDataTypeFactory; // 引入RelDataTypeFactory接口，用于创建关系数据类型
import org.apache.calcite.rex.RexBuilder; // 引入RexBuilder类，用于构建行表达式（Rex表达式）

import org.checkerframework.checker.nullness.qual.Nullable; // 引入注解，表示可能为null的返回值

import java.util.HashMap; // 引入HashMap类，用于存储键值对映射
import java.util.Map; // 引入Map接口，定义映射数据结构
import java.util.concurrent.atomic.AtomicInteger; // 引入AtomicInteger类，提供原子操作的整数计数器

import static java.lang.Integer.parseInt; // 静态导入parseInt方法，用于将字符串转换为整数

/**
 * A <code>RelOptQuery</code> represents a set of
 * {@link RelNode relational expressions} which derive from the same
 * <code>select</code> statement.
 */
// 类说明：RelOptQuery表示一组来自同一个select语句的关系代数表达式（RelNode）
// 在Calcite中，一个SQL查询会被转换为一棵关系代数表达式树，RelOptQuery用于管理这棵树
// 它主要处理相关子查询（correlated subqueries），维护相关变量的映射关系
public class RelOptQuery { // 定义RelOptQuery类，表示查询优化的查询对象
  //~ Static fields/initializers ---------------------------------------------

  /**
   * Prefix to the name of correlating variables.
   */
  // 常量说明：相关变量名称的前缀，所有相关变量的名称都以"$cor"开头，例如"$cor0"、"$cor1"等
  public static final String CORREL_PREFIX = CorrelationId.CORREL_PREFIX; // 定义相关变量名的前缀常量

  //~ Instance fields --------------------------------------------------------

  /**
   * Maps name of correlating variable (e.g. "$cor3") to the {@link RelNode}
   * which implements it.
   */
  // 成员变量说明：mapCorrelToRel是一个映射表，存储相关变量名到对应关系代数节点的映射
  // 例如：当SQL中出现相关子查询时，系统会为每个相关子查询分配一个相关变量名（如"$cor3"）
  // 该映射表记录了"$cor3"这个相关变量名对应的具体关系代数节点（RelNode），即相关子查询的实现
  final Map<String, RelNode> mapCorrelToRel; // 定义相关变量名到RelNode映射关系的Map

  // 成员变量说明：planner是关系代数优化器，负责对关系代数表达式树进行优化
  // RelOptPlanner是Calcite的核心优化器接口，实现了基于规则的优化（RBO）和基于成本的优化（CBO）
  // 它可以应用各种优化规则（如谓词下推、投影消除等）来生成最优的执行计划
  private final RelOptPlanner planner; // 定义关系代数优化器成员变量

  // 成员变量说明：nextCorrel是原子计数器，用于生成唯一的相关变量序号
  // AtomicInteger保证了多线程环境下的线程安全，每次调用getAndIncrement()都会返回当前值并自增
  // 这样可以确保每个相关变量都有唯一的序号，避免命名冲突
  final AtomicInteger nextCorrel; // 定义原子计数器，用于生成相关变量的唯一序号

  //~ Constructors -----------------------------------------------------------

  /**
   * Creates a query.
   *
   * @param planner Planner
   */
  // 构造方法说明：已废弃的公共构造方法，用于创建一个RelOptQuery对象
  // 参数planner是关系代数优化器，用于后续的查询优化工作
  // 此构造方法内部会初始化相关变量计数器从0开始，并创建一个空的HashMap来存储相关变量映射
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public RelOptQuery(RelOptPlanner planner) { // 定义公共构造方法，接收优化器参数
    this(planner, new AtomicInteger(0), new HashMap<>()); // 调用私有构造方法，初始化计数器为0，创建空的映射表
  }

  /** For use by RelOptCluster only. */
  // 构造方法说明：私有构造方法，仅由RelOptCluster类使用
  // RelOptCluster是关系代数表达式的集群对象，一个RelOptQuery对应一个RelOptCluster
  // 这个构造方法允许共享nextCorrel计数器和mapCorrelToRel映射表，实现多个对象间的状态共享
  // 参数说明：
  // - planner: 关系代数优化器
  // - nextCorrel: 原子计数器，用于生成相关变量序号
  // - mapCorrelToRel: 相关变量名到RelNode的映射表
  RelOptQuery(RelOptPlanner planner, AtomicInteger nextCorrel, // 定义私有构造方法，接收优化器、计数器和映射表
      Map<String, RelNode> mapCorrelToRel) { // 接收相关变量映射表参数
    this.planner = planner; // 将传入的优化器赋值给成员变量
    this.nextCorrel = nextCorrel; // 将传入的计数器赋值给成员变量
    this.mapCorrelToRel = mapCorrelToRel; // 将传入的映射表赋值给成员变量
  }

  //~ Methods ----------------------------------------------------------------

  /**
   * Converts a correlating variable name into an ordinal, unique within the
   * query.
   *
   * @param correlName Name of correlating variable
   * @return Correlating variable ordinal
   */
  // 方法说明：静态方法，将相关变量名转换为序号（整数）
  // 例如：将相关变量名"$cor3"转换为整数3，便于在内部处理时使用
  // 这个序号在整个查询中是唯一的，用于标识不同的相关变量
  // 参数correlName是相关变量名，必须以CORREL_PREFIX（"$cor"）开头
  // 返回值是相关变量的序号，即去掉前缀后的数字部分
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public static int getCorrelOrdinal(String correlName) { // 定义静态方法，接收相关变量名字符串
    assert correlName.startsWith(CORREL_PREFIX); // 断言相关变量名必须以"$cor"前缀开头，否则抛出异常
    return parseInt(correlName.substring(CORREL_PREFIX.length())); // 截取前缀后的字符串并转换为整数返回
  }

  /**
   * Creates a cluster.
   *
   * @param typeFactory Type factory
   * @param rexBuilder  Expression builder
   * @return New cluster
   */
  // 方法说明：创建一个RelOptCluster对象
  // RelOptCluster是关系代数表达式的集群，包含类型工厂、表达式构建器、优化器等共享资源
  // 在Calcite中，同一个查询的所有关系代数节点共享同一个RelOptCluster
  // 参数说明：
  // - typeFactory: 关系数据类型工厂，用于创建各种数据类型（如VARCHAR、INTEGER等）
  // - rexBuilder: 行表达式构建器，用于构建Rex表达式（如条件表达式、计算表达式等）
  // 返回值是新创建的RelOptCluster对象，其中包含了当前RelOptQuery的优化器、计数器和映射表
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public RelOptCluster createCluster( // 定义方法，用于创建RelOptCluster对象
      RelDataTypeFactory typeFactory, // 接收类型工厂参数
      RexBuilder rexBuilder) { // 接收表达式构建器参数
    return new RelOptCluster(planner, typeFactory, rexBuilder, nextCorrel, // 创建并返回RelOptCluster对象，传入优化器、类型工厂、表达式构建器、计数器和映射表
        mapCorrelToRel); // 继续传入相关变量映射表
  }

  /**
   * Constructs a new name for a correlating variable. It is unique within the
   * whole query.
   *
   * @deprecated Use {@link RelOptCluster#createCorrel()}
   */
  // 方法说明：创建一个新的相关变量名，在整个查询中保证唯一
  // 相关变量用于标识相关子查询，例如在SQL中：SELECT * FROM emp WHERE deptno IN (SELECT deptno FROM dept WHERE emp.sal > avg_sal)
  // 这里的emp.sal引用了外部查询的emp表，需要使用相关变量来标识这种依赖关系
  // 方法会生成类似"$cor0"、"$cor1"、"$cor2"这样的唯一名称
  // 返回值是新生成的相关变量名字符串
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public String createCorrel() { // 定义方法，用于创建相关变量名
    int n = nextCorrel.getAndIncrement(); // 原子性地获取当前计数器值并自增，确保多线程环境下序号的唯一性
    return CORREL_PREFIX + n; // 将前缀"$cor"与序号拼接，生成完整的相关变量名并返回
  }

  /**
   * Returns the relational expression which populates a correlating variable.
   */
  // 方法说明：根据相关变量名查找对应的关系代数节点（RelNode）
  // 当处理相关子查询时，系统需要知道某个相关变量对应的具体关系代数表达式
  // 例如：相关变量"$cor3"可能对应一个扫描dept表的RelNode
  // 参数name是相关变量名，如"$cor3"
  // 返回值是对应的RelNode对象，如果不存在则返回null（通过@Nullable注解标注）
  public @Nullable RelNode lookupCorrel(String name) { // 定义方法，接收相关变量名字符串，返回可能为null的RelNode
    return mapCorrelToRel.get(name); // 从映射表中查找并返回对应的相关变量名对应的RelNode
  }

  /**
   * Maps a correlating variable to a {@link RelNode}.
   */
  // 方法说明：将相关变量名映射到关系代数节点（RelNode）
  // 当创建或发现相关子查询时，需要建立相关变量名与对应关系代数节点的映射关系
  // 这样在后续的优化和执行过程中，可以通过相关变量名快速找到对应的RelNode
  // 参数说明：
  // - name: 相关变量名，如"$cor3"
  // - rel: 关系代数节点，代表相关子查询的具体实现
  public void mapCorrel( // 定义方法，用于建立相关变量名到RelNode的映射
      String name, // 接收相关变量名字符串参数
      RelNode rel) { // 接收关系代数节点参数
    mapCorrelToRel.put(name, rel); // 将相关变量名和RelNode的映射关系存入映射表
  }
} // 类定义结束
