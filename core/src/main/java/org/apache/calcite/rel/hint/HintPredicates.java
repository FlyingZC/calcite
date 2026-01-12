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
package org.apache.calcite.rel.hint; // 包声明：包含Hint提示谓词相关的类，用于定义和管理SQL查询优化器的提示条件

/**
 * A collection of hint predicates. // 类作用：HintPredicates是一个抽象类，作为提示谓词的集合工厂，提供了各种预定义的提示谓词常量和组合方法
 * // 这个类主要用于定义查询优化器中Hint（提示）的适用条件，通过谓词来限制Hint只能应用到特定类型的RelNode节点上
 * // Hint是SQL优化器的重要机制，允许用户通过注释或特殊语法影响查询执行计划的选择
 * // 该类提供了针对不同RelNode类型（如JOIN、PROJECT、FILTER等）的预定义谓词，以及AND/OR组合谓词的方法
 */
public abstract class HintPredicates { // 抽象类：不能实例化，只作为静态工厂提供预定义的Hint谓词常量和组合方法
  /** A hint predicate that indicates a hint can only be used to // 成员变量作用：SET_VAR是一个静态常量，表示Hint谓词，用于限制Hint只能应用于整个查询（不针对特定节点）
   * the whole query(no specific nodes). */ // 这种Hint通常用于设置查询级别的变量或配置，如会话参数、执行模式等全局性设置
  public static final HintPredicate SET_VAR = // 静态常量：HintPredicate接口实例，使用NodeTypeHintPredicate创建，节点类型为SET_VAR
      new NodeTypeHintPredicate(NodeTypeHintPredicate.NodeType.SET_VAR); // 创建NodeTypeHintPredicate实例，指定节点类型为SET_VAR，表示该Hint适用于查询级别而非特定RelNode

  /** A hint predicate that indicates a hint can only be used to // 成员变量作用：JOIN是一个静态常量，表示Hint谓词，用于限制Hint只能应用于Join类型的RelNode节点
   * {@link org.apache.calcite.rel.core.Join} nodes. */ // Join节点表示两个或多个表之间的连接操作，Hint可以影响连接算法选择、连接顺序等
  public static final HintPredicate JOIN = // 静态常量：HintPredicate接口实例，使用NodeTypeHintPredicate创建，节点类型为JOIN
      new NodeTypeHintPredicate(NodeTypeHintPredicate.NodeType.JOIN); // 创建NodeTypeHintPredicate实例，指定节点类型为JOIN，表示该Hint只适用于Join类型的节点

  /** A hint predicate that indicates a hint can only be used to // 成员变量作用：TABLE_SCAN是一个静态常量，表示Hint谓词，用于限制Hint只能应用于TableScan类型的RelNode节点
   * {@link org.apache.calcite.rel.core.TableScan} nodes. */ // TableScan节点表示从数据源扫描表数据，Hint可以影响扫描方式、索引使用、并行度等
  public static final HintPredicate TABLE_SCAN = // 静态常量：HintPredicate接口实例，使用NodeTypeHintPredicate创建，节点类型为TABLE_SCAN
      new NodeTypeHintPredicate(NodeTypeHintPredicate.NodeType.TABLE_SCAN); // 创建NodeTypeHintPredicate实例，指定节点类型为TABLE_SCAN，表示该Hint只适用于表扫描节点

  /** A hint predicate that indicates a hint can only be used to // 成员变量作用：PROJECT是一个静态常量，表示Hint谓词，用于限制Hint只能应用于Project类型的RelNode节点
   * {@link org.apache.calcite.rel.core.Project} nodes. */ // Project节点表示投影操作，用于选择、重命名或计算列，Hint可以影响列裁剪、表达式下推等优化
  public static final HintPredicate PROJECT = // 静态常量：HintPredicate接口实例，使用NodeTypeHintPredicate创建，节点类型为PROJECT
      new NodeTypeHintPredicate(NodeTypeHintPredicate.NodeType.PROJECT); // 创建NodeTypeHintPredicate实例，指定节点类型为PROJECT，表示该Hint只适用于投影节点

  /** A hint predicate that indicates a hint can only be used to // 成员变量作用：AGGREGATE是一个静态常量，表示Hint谓词，用于限制Hint只能应用于Aggregate类型的RelNode节点
   * {@link org.apache.calcite.rel.core.Aggregate} nodes. */ // Aggregate节点表示聚合操作（如SUM、COUNT、AVG等），Hint可以影响聚合算法、分组策略等
  public static final HintPredicate AGGREGATE = // 静态常量：HintPredicate接口实例，使用NodeTypeHintPredicate创建，节点类型为AGGREGATE
      new NodeTypeHintPredicate(NodeTypeHintPredicate.NodeType.AGGREGATE); // 创建NodeTypeHintPredicate实例，指定节点类型为AGGREGATE，表示该Hint只适用于聚合节点

  /** A hint predicate that indicates a hint can only be used to // 成员变量作用：CALC是一个静态常量，表示Hint谓词，用于限制Hint只能应用于Calc类型的RelNode节点
   * {@link org.apache.calcite.rel.core.Calc} nodes. */ // Calc节点是Calcite特有的节点，可以同时表示投影和过滤操作，Hint可以影响计算优化策略
  public static final HintPredicate CALC = // 静态常量：HintPredicate接口实例，使用NodeTypeHintPredicate创建，节点类型为CALC
      new NodeTypeHintPredicate(NodeTypeHintPredicate.NodeType.CALC); // 创建NodeTypeHintPredicate实例，指定节点类型为CALC，表示该Hint只适用于Calc节点

  /** A hint predicate that indicates a hint can only be used to // 成员变量作用：CORRELATE是一个静态常量，表示Hint谓词，用于限制Hint只能应用于Correlate类型的RelNode节点
   * {@link org.apache.calcite.rel.core.Correlate} nodes. */ // Correlate节点表示相关联接操作，通常用于子查询相关，Hint可以影响关联策略
  public static final HintPredicate CORRELATE = // 静态常量：HintPredicate接口实例，使用NodeTypeHintPredicate创建，节点类型为CORRELATE
      new NodeTypeHintPredicate(NodeTypeHintPredicate.NodeType.CORRELATE); // 创建NodeTypeHintPredicate实例，指定节点类型为CORRELATE，表示该Hint只适用于关联节点

  /** A hint predicate that indicates a hint can only be used to // 成员变量作用：FILTER是一个静态常量，表示Hint谓词，用于限制Hint只能应用于Filter类型的RelNode节点
   * {@link org.apache.calcite.rel.core.Filter} nodes. */ // Filter节点表示过滤操作，用于根据条件过滤行，Hint可以影响谓词下推、索引使用等
  public static final HintPredicate FILTER = // 静态常量：HintPredicate接口实例，使用NodeTypeHintPredicate创建，节点类型为FILTER
          new NodeTypeHintPredicate(NodeTypeHintPredicate.NodeType.FILTER); // 创建NodeTypeHintPredicate实例，指定节点类型为FILTER，表示该Hint只适用于过滤节点

  /** A hint predicate that indicates a hint can only be used to // 成员变量作用：SETOP是一个静态常量，表示Hint谓词，用于限制Hint只能应用于SetOp（集合操作）类型的RelNode节点
   * {@link org.apache.calcite.rel.core.SetOp} nodes. */ // SetOp节点表示集合操作（如UNION、INTERSECT、EXCEPT），Hint可以影响集合操作算法
  public static final HintPredicate SETOP = // 静态常量：HintPredicate接口实例，使用NodeTypeHintPredicate创建，节点类型为SETOP
          new NodeTypeHintPredicate(NodeTypeHintPredicate.NodeType.SETOP); // 创建NodeTypeHintPredicate实例，指定节点类型为SETOP，表示该Hint只适用于集合操作节点

  /** A hint predicate that indicates a hint can only be used to // 成员变量作用：SORT是一个静态常量，表示Hint谓词，用于限制Hint只能应用于Sort类型的RelNode节点
   * {@link org.apache.calcite.rel.core.Sort} nodes. */ // Sort节点表示排序操作，Hint可以影响排序算法、内存使用等
  public static final HintPredicate SORT = // 静态常量：HintPredicate接口实例，使用NodeTypeHintPredicate创建，节点类型为SORT
          new NodeTypeHintPredicate(NodeTypeHintPredicate.NodeType.SORT); // 创建NodeTypeHintPredicate实例，指定节点类型为SORT，表示该Hint只适用于排序节点

  /** A hint predicate that indicates a hint can only be used to // 成员变量作用：VALUES是一个静态常量，表示Hint谓词，用于限制Hint只能应用于Values类型的RelNode节点
   * {@link org.apache.calcite.rel.core.Values} nodes. */ // Values节点表示常量值集合，通常用于测试或提供常量数据，Hint可以影响Values节点的处理方式
  public static final HintPredicate VALUES = // 静态常量：HintPredicate接口实例，使用NodeTypeHintPredicate创建，节点类型为VALUES
      new NodeTypeHintPredicate(NodeTypeHintPredicate.NodeType.VALUES); // 创建NodeTypeHintPredicate实例，指定节点类型为VALUES，表示该Hint只适用于Values节点

  /** A hint predicate that indicates a hint can only be used to // 成员变量作用：WINDOW是一个静态常量，表示Hint谓词，用于限制Hint只能应用于Window（窗口函数）类型的RelNode节点
   * {@link org.apache.calcite.rel.core.Window} nodes. */ // Window节点表示窗口函数操作（如ROW_NUMBER、RANK、LEAD等），Hint可以影响窗口函数计算策略
  public static final HintPredicate WINDOW = // 静态常量：HintPredicate接口实例，使用NodeTypeHintPredicate创建，节点类型为WINDOW
      new NodeTypeHintPredicate(NodeTypeHintPredicate.NodeType.WINDOW); // 创建NodeTypeHintPredicate实例，指定节点类型为WINDOW，表示该Hint只适用于窗口函数节点

  /** A hint predicate that indicates a hint can only be used to // 成员变量作用：SNAPSHOT是一个静态常量，表示Hint谓词，用于限制Hint只能应用于Snapshot（快照）类型的RelNode节点
   * {@link org.apache.calcite.rel.core.Snapshot} nodes. */ // Snapshot节点表示表快照操作，通常用于时间旅行查询，Hint可以影响快照读取策略
  public static final HintPredicate SNAPSHOT = // 静态常量：HintPredicate接口实例，使用NodeTypeHintPredicate创建，节点类型为SNAPSHOT
      new NodeTypeHintPredicate(NodeTypeHintPredicate.NodeType.SNAPSHOT); // 创建NodeTypeHintPredicate实例，指定节点类型为SNAPSHOT，表示该Hint只适用于快照节点

  /** A hint predicate that indicates a hint can only be used to // 成员变量作用：TABLE_FUNCTION_SCAN是一个静态常量，表示Hint谓词，用于限制Hint只能应用于TableFunctionScan类型的RelNode节点
   * {@link org.apache.calcite.rel.core.TableFunctionScan} nodes. */ // TableFunctionScan节点表示表函数扫描，用于调用返回表的用户定义函数，Hint可以影响表函数执行策略
  public static final HintPredicate TABLE_FUNCTION_SCAN = // 静态常量：HintPredicate接口实例，使用NodeTypeHintPredicate创建，节点类型为TABLE_FUNCTION_SCAN
      new NodeTypeHintPredicate(NodeTypeHintPredicate.NodeType.TABLE_FUNCTION_SCAN); // 创建NodeTypeHintPredicate实例，指定节点类型为TABLE_FUNCTION_SCAN，表示该Hint只适用于表函数扫描节点

  /**
   * Returns a composed hint predicate that represents a short-circuiting logical // 方法作用：and方法用于创建一个组合Hint谓词，表示多个Hint谓词的逻辑"与"关系
   * AND of an array of hint predicates {@code hintPredicates}.  When evaluating the composed // 参数说明：hintPredicates是可变参数数组，包含需要组合的多个HintPredicate对象
   * predicate, if a predicate is {@code false}, then all the left // 短路求值特性：采用短路求值策略，当某个谓词返回false时，立即停止评估剩余谓词，提高性能
   * predicates are not evaluated. */ // 返回值：返回一个CompositeHintPredicate对象，表示组合后的AND谓词
   // *
  // * <p>The predicates are evaluated in sequence. */ // 评估顺序：谓词按照传入数组的顺序依次评估，顺序可能影响性能和结果（对于有副作用的谓词）
  // */ // 使用场景：当Hint需要同时满足多个条件时使用，例如Hint只能应用于特定表且特定类型的节点
  public static HintPredicate and(HintPredicate... hintPredicates) { // 静态方法：创建AND组合谓词，使用可变参数接收多个HintPredicate对象
    return new CompositeHintPredicate(CompositeHintPredicate.Composition.AND, hintPredicates); // 创建CompositeHintPredicate实例，组合类型为AND，传入所有要组合的谓词
  } // 方法结束：返回组合后的AND谓词，该谓词只有当所有输入谓词都返回true时才返回true

  /**
   * Returns a composed hint predicate that represents a short-circuiting logical // 方法作用：or方法用于创建一个组合Hint谓词，表示多个Hint谓词的逻辑"或"关系
   * OR of an array of hint predicates {@code hintPredicates}.  When evaluating the composed // 参数说明：hintPredicates是可变参数数组，包含需要组合的多个HintPredicate对象
   * predicate, if a predicate is {@code true}, then all the left // 短路求值特性：采用短路求值策略，当某个谓词返回true时，立即停止评估剩余谓词，提高性能
   * predicates are not evaluated. */ // 返回值：返回一个CompositeHintPredicate对象，表示组合后的OR谓词
  //*
  //* <p>The predicates are evaluated in sequence. */ // 评估顺序：谓词按照传入数组的顺序依次评估，顺序可能影响性能（对于有副作用的谓词）
  //*/ // 使用场景：当Hint可以应用于多种不同类型的节点时使用，例如Hint可以应用于JOIN或FILTER节点
  public static HintPredicate or(HintPredicate... hintPredicates) { // 静态方法：创建OR组合谓词，使用可变参数接收多个HintPredicate对象
    return new CompositeHintPredicate(CompositeHintPredicate.Composition.OR, hintPredicates); // 创建CompositeHintPredicate实例，组合类型为OR，传入所有要组合的谓词
  } // 方法结束：返回组合后的OR谓词，该谓词只要有一个输入谓词返回true就返回true
} // 类结束：HintPredicates抽象类结束，该类提供了完整的Hint谓词定义和组合能力
