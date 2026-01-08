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
package org.apache.calcite.rel.hint; // 声明包名，表示该类属于org.apache.calcite.rel.hint包，这是Calcite框架中处理关系表达式提示(hint)的包

import org.apache.calcite.rel.RelNode; // 导入RelNode类，这是Calcite中所有关系表达式节点的基类
import org.apache.calcite.rel.core.Aggregate; // 导入Aggregate类，表示聚合操作的关系节点
import org.apache.calcite.rel.core.Calc; // 导入Calc类，表示计算操作的关系节点(结合了Project和Filter)
import org.apache.calcite.rel.core.Correlate; // 导入Correlate类，表示相关子查询的关系节点
import org.apache.calcite.rel.core.Filter; // 导入Filter类，表示过滤操作的关系节点
import org.apache.calcite.rel.core.Join; // 导入Join类，表示连接操作的关系节点
import org.apache.calcite.rel.core.Project; // 导入Project类，表示投影操作的关系节点
import org.apache.calcite.rel.core.SetOp; // 导入SetOp类，表示集合操作(Union/Intersect/Minus)的关系节点基类
import org.apache.calcite.rel.core.Snapshot; // 导入Snapshot类，表示快照操作的关系节点
import org.apache.calcite.rel.core.Sort; // 导入Sort类，表示排序操作的关系节点
import org.apache.calcite.rel.core.TableFunctionScan; // 导入TableFunctionScan类，表示表函数扫描的关系节点
import org.apache.calcite.rel.core.TableScan; // 导入TableScan类，表示表扫描的关系节点
import org.apache.calcite.rel.core.Values; // 导入Values类，表示值列表的关系节点
import org.apache.calcite.rel.core.Window; // 导入Window类，表示窗口操作的关系节点

import static java.util.Objects.requireNonNull; // 静态导入Objects.requireNonNull方法，用于空值检查

/**
 * A hint predicate that specifies which kind of relational
 * expression the hint can be applied to.
 * 这是一个提示谓词，用于指定提示(hint)可以应用于哪种类型的关系表达式
 *
 * 类作用说明：
 * NodeTypeHintPredicate是Calcite框架中用于判断提示(hint)是否应该传播到特定类型关系节点的谓词实现
 * 它实现了HintPredicate接口，提供了基于节点类型的匹配逻辑
 *
 * 核心功能：
 * 1. 定义了16种不同的关系节点类型(通过NodeType枚举)
 * 2. 根据节点类型判断提示是否应该应用到该节点
 * 3. 支持提示的传播机制，控制提示在查询计划中的传播范围
 *
 * 使用场景：
 * - 在SQL查询优化过程中，用户可以通过提示(hint)来影响优化器的决策
 * - 例如：/*+ INDEX_JOIN(t1, idx1)  这样的提示需要应用到特定的Join节点
 * - NodeTypeHintPredicate用于判断该提示是否应该传播到当前的关系节点
 *
 * 设计模式：
 * - 使用策略模式：不同的节点类型对应不同的处理策略
 * - 使用枚举类型：NodeType枚举封装了所有支持的节点类型及其对应的Class对象
 */
public class NodeTypeHintPredicate implements HintPredicate { // 定义NodeTypeHintPredicate类，实现HintPredicate接口，表示这是一个基于节点类型的提示谓词

  /**
   * Enumeration of the relational expression types that the hints
   * may be propagated to.
   * 枚举定义了提示可能传播到的关系表达式类型
   *
   * NodeType枚举作用：
   * 定义了Calcite中所有支持提示传播的关系节点类型
   * 每个枚举值对应一种特定的关系节点类型，并关联该节点的Class对象
   * 通过枚举可以方便地进行类型匹配和提示传播控制
   *
   * 枚举值说明：
   * - SET_VAR: 用于整个查询的配置，不传播
   * - JOIN: 连接操作节点
   * - TABLE_SCAN: 表扫描节点
   * - PROJECT: 投影操作节点
   * - AGGREGATE: 聚合操作节点
   * - CALC: 计算操作节点
   * - CORRELATE: 相关子查询节点
   * - FILTER: 过滤操作节点
   * - SETOP: 集合操作节点
   * - SORT: 排序操作节点
   * - VALUES: 值列表节点
   * - WINDOW: 窗口操作节点
   * - SNAPSHOT: 快照操作节点
   * - TABLE_FUNCTION_SCAN: 表函数扫描节点
   */
  enum NodeType { // 定义NodeType枚举，表示关系表达式的类型
    /**
     * The hint is used for the whole query, kind of like a query config.
     * This kind of hints would never be propagated.
     * 该提示用于整个查询，类似于查询配置
     * 这种类型的提示永远不会传播
     *
     * SET_VAR说明：
     * - SET_VAR是"SET VARIABLE"的缩写，表示设置变量的提示
     * - 这类提示作用于整个查询，而不是特定的关系节点
     * - 因此它不会传播到任何子节点
     * - 例如：/*+ SET_VAR('some_config', 'value')  这样的提示
     * - 在apply方法中，SET_VAR类型直接返回false，表示不应用也不传播
     */
    SET_VAR(RelNode.class), // 定义SET_VAR枚举值，关联RelNode.class作为基类，表示该提示用于整个查询配置，不传播

    /**
     * The hint would be propagated to the Join nodes.
     * 该提示会传播到Join节点
     *
     * JOIN说明：
     * - Join节点表示两个或多个表的连接操作
     * - 支持的连接类型：INNER JOIN、LEFT JOIN、RIGHT JOIN、FULL JOIN等
     * - 提示可以影响连接算法的选择(如Hash Join、Merge Join、Nested Loop Join)
     * - 例如：/*+ HASH_JOIN(t1, t2)  提示使用Hash Join算法
     */
    JOIN(Join.class), // 定义JOIN枚举值，关联Join.class，表示该提示会传播到Join节点

    /**
     * The hint would be propagated to the TableScan nodes.
     * 该提示会传播到TableScan节点
     *
     * TABLE_SCAN说明：
     * - TableScan节点表示从表中读取数据
     * - 提示可以影响表的访问方式(如全表扫描、索引扫描)
     * - 例如：/*+ INDEX(t1, idx1)  提示使用索引idx1扫描表t1
     */
    TABLE_SCAN(TableScan.class), // 定义TABLE_SCAN枚举值，关联TableScan.class，表示该提示会传播到TableScan节点

    /**
     * The hint would be propagated to the Project nodes.
     * 该提示会传播到Project节点
     *
     * PROJECT说明：
     * - Project节点表示投影操作，用于选择、重命名、计算列
     * - 提示可以影响投影的优化策略
     * - 例如：/*+ PROJECT_MERGE  提示合并多个Project操作
     */
    PROJECT(Project.class), // 定义PROJECT枚举值，关联Project.class，表示该提示会传播到Project节点

    /**
     * The hint would be propagated to the Aggregate nodes.
     * 该提示会传播到Aggregate节点
     *
     * AGGREGATE说明：
     * - Aggregate节点表示聚合操作，如SUM、COUNT、AVG等
     * - 提示可以影响聚合算法的选择(如Hash Aggregate、Sort Aggregate)
     * - 例如：/*+ HASH_AGG  提示使用Hash聚合算法
     */
    AGGREGATE(Aggregate.class), // 定义AGGREGATE枚举值，关联Aggregate.class，表示该提示会传播到Aggregate节点

    /**
     * The hint would be propagated to the Calc nodes.
     * 该提示会传播到Calc节点
     *
     * CALC说明：
     * - Calc节点是Calcite特有的节点，结合了Project和Filter的功能
     * - 提示可以影响Calc节点的优化策略
     * - 例如：/*+ CALC_MERGE  提示合并Calc操作
     */
    CALC(Calc.class), // 定义CALC枚举值，关联Calc.class，表示该提示会传播到Calc节点

    /**
     * The hint would be propagated to the Correlate nodes.
     * 该提示会传播到Correlate节点
     *
     * CORRELATE说明：
     * - Correlate节点表示相关子查询，即子查询引用了外部查询的列
     * - 提示可以影响相关子查询的去相关策略
     * - 例如：/*+ UNNEST  提示对相关子查询进行去相关
     */
    CORRELATE(Correlate.class), // 定义CORRELATE枚举值，关联Correlate.class，表示该提示会传播到Correlate节点

    /**
     * The hint would be propagated to the Filter nodes.
     * 该提示会传播到Filter节点
     *
     * FILTER说明：
     * - Filter节点表示过滤操作，用于根据条件过滤行
     * - 提示可以影响过滤条件的下推策略
     * - 例如：/*+ FILTER_PUSHDOWN  提示将过滤条件尽可能下推
     */
    FILTER(Filter.class), // 定义FILTER枚举值，关联Filter.class，表示该提示会传播到Filter节点

    /**
     * The hint would be propagated to the SetOp(Union, Intersect, Minus) nodes.
     * 该提示会传播到SetOp(Union、Intersect、Minus)节点
     *
     * SETOP说明：
     * - SetOp节点表示集合操作，包括UNION、INTERSECT、MINUS(EXCEPT)
     * - 提示可以影响集合操作的执行策略
     * - 例如：/*+ UNION_ALL  提示使用UNION ALL而不是UNION DISTINCT
     */
    SETOP(SetOp.class), // 定义SETOP枚举值，关联SetOp.class，表示该提示会传播到SetOp(Union、Intersect、Minus)节点

    /**
     * The hint would be propagated to the Sort nodes.
     * 该提示会传播到Sort节点
     *
     * SORT说明：
     * - Sort节点表示排序操作，用于ORDER BY子句
     * - 提示可以影响排序算法的选择
     * - 例如：/*+ TOP_N  提示使用Top-N排序优化
     */
    SORT(Sort.class), // 定义SORT枚举值，关联Sort.class，表示该提示会传播到Sort节点

    /**
     * The hint would be propagated to the Values nodes.
     * 该提示会传播到Values节点
     *
     * VALUES说明：
     * - Values节点表示值列表，用于VALUES子句
     * - 提示可以影响Values节点的优化策略
     * - 例如：/*+ VALUES_MERGE  提示合并多个Values操作
     */
    VALUES(Values.class), // 定义VALUES枚举值，关联Values.class，表示该提示会传播到Values节点

    /**
     * The hint would be propagated to the Window nodes.
     * 该提示会传播到Window节点
     *
     * WINDOW说明：
     * - Window节点表示窗口函数操作，用于OVER子句
     * - 提示可以影响窗口函数的计算策略
     * - 例如：/*+ WINDOW_MERGE  提示合并多个Window操作
     */
    WINDOW(Window.class), // 定义WINDOW枚举值，关联Window.class，表示该提示会传播到Window节点

    /**
     * The hint would be propagated to the Snapshot nodes.
     * 该提示会传播到Snapshot节点
     *
     * SNAPSHOT说明：
     * - Snapshot节点表示快照操作，用于时态查询
     * - 提示可以影响快照的获取策略
     * - 例如：/*+ SNAPSHOT_AS_OF  提示使用特定时间点的快照
     */
    SNAPSHOT(Snapshot.class), // 定义SNAPSHOT枚举值，关联Snapshot.class，表示该提示会传播到Snapshot节点

    /**
     * The hint would be propagated to the TableFunctionScan nodes.
     * 该提示会传播到TableFunctionScan节点
     *
     * TABLE_FUNCTION_SCAN说明：
     * - TableFunctionScan节点表示表函数扫描，用于调用表值函数
     * - 提示可以影响表函数的执行策略
     * - 例如：/*+ TABLE_FUNCTION  提示优化表函数的执行
     */
    TABLE_FUNCTION_SCAN(TableFunctionScan.class); // 定义TABLE_FUNCTION_SCAN枚举值，关联TableFunctionScan.class，表示该提示会传播到TableFunctionScan节点

    /** Relational expression clazz that the hint can apply to. */
    @SuppressWarnings("ImmutableEnumChecker") // 抑制ImmutableEnumChecker警告，因为虽然枚举应该是不可变的，但relClazz字段是可变的Class对象
    private final Class<?> relClazz; // 定义成员变量relClazz，类型为Class<?>，表示该提示可以应用的关系表达式类的Class对象，使用final修饰表示不可变

    NodeType(Class<?> relClazz) { // 定义NodeType枚举的构造方法，接收一个Class<?>类型的参数relClazz
      this.relClazz = relClazz; // 将传入的relClazz参数赋值给成员变量relClazz，保存该节点类型对应的Class对象
    } // 构造方法结束
  } // NodeType枚举定义结束

  private final NodeType nodeType; // 定义成员变量nodeType，类型为NodeType，表示该谓词对应的节点类型，使用final修饰表示不可变

  public NodeTypeHintPredicate(NodeType nodeType) { // 定义构造方法，接收一个NodeType类型的参数nodeType，用于创建NodeTypeHintPredicate实例
    this.nodeType = requireNonNull(nodeType, "nodeType"); // 使用requireNonNull方法检查nodeType参数是否为null，如果为null则抛出NullPointerException，异常信息为"nodeType"
  } // 构造方法结束

  @Override public boolean apply(RelHint hint, RelNode rel) { // 重写HintPredicate接口的apply方法，用于判断提示是否应该应用到给定的关系节点，接收RelHint和RelNode两个参数，返回boolean类型
    switch (this.nodeType) { // 使用switch语句根据nodeType的值进行分支判断
    // Hints of SET_VAR type never propagate.
    case SET_VAR: // 如果nodeType是SET_VAR类型
      return false; // 直接返回false，表示SET_VAR类型的提示永远不会传播，不应用也不传播到任何节点
    default: // 对于其他所有节点类型
      return this.nodeType.relClazz.isInstance(rel); // 使用isInstance方法判断rel对象是否是nodeType.relClazz类或其子类的实例，返回判断结果
    } // switch语句结束
  } // apply方法结束
} // NodeTypeHintPredicate类定义结束
