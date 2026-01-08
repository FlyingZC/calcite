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
package org.apache.calcite.rel; // 定义包名，该类属于org.apache.calcite.rel包，是Calcite关系表达式相关类的核心包

import org.apache.calcite.linq4j.Ord; // 导入Ord工具类，用于在遍历集合时获取元素及其索引位置
import org.apache.calcite.rel.core.TableFunctionScan; // 导入TableFunctionScan类，表示表函数扫描操作
import org.apache.calcite.rel.core.TableScan; // 导入TableScan类，表示表扫描操作
import org.apache.calcite.rel.logical.LogicalAggregate; // 导入LogicalAggregate类，表示逻辑聚合操作（GROUP BY）
import org.apache.calcite.rel.logical.LogicalAsofJoin; // 导入LogicalAsofJoin类，表示逻辑as-of连接操作
import org.apache.calcite.rel.logical.LogicalCalc; // 导入LogicalCalc类，表示逻辑计算操作（类似于SELECT中的表达式计算）
import org.apache.calcite.rel.logical.LogicalCorrelate; // 导入LogicalCorrelate类，表示逻辑关联操作
import org.apache.calcite.rel.logical.LogicalExchange; // 导入LogicalExchange类，表示逻辑交换操作（用于分布式数据交换）
import org.apache.calcite.rel.logical.LogicalFilter; // 导入LogicalFilter类，表示逻辑过滤操作（WHERE条件）
import org.apache.calcite.rel.logical.LogicalIntersect; // 导入LogicalIntersect类，表示逻辑交集操作（INTERSECT）
import org.apache.calcite.rel.logical.LogicalJoin; // 导入LogicalJoin类，表示逻辑连接操作（JOIN）
import org.apache.calcite.rel.logical.LogicalMatch; // 导入LogicalMatch类，表示逻辑匹配操作（MATCH_RECOGNIZE）
import org.apache.calcite.rel.logical.LogicalMinus; // 导入LogicalMinus类，表示逻辑差集操作（EXCEPT）
import org.apache.calcite.rel.logical.LogicalProject; // 导入LogicalProject类，表示逻辑投影操作（SELECT字段列表）
import org.apache.calcite.rel.logical.LogicalRepeatUnion; // 导入LogicalRepeatUnion类，表示逻辑重复联合操作（用于递归查询）
import org.apache.calcite.rel.logical.LogicalSort; // 导入LogicalSort类，表示逻辑排序操作（ORDER BY）
import org.apache.calcite.rel.logical.LogicalTableModify; // 导入LogicalTableModify类，表示逻辑表修改操作（INSERT/UPDATE/DELETE）
import org.apache.calcite.rel.logical.LogicalUnion; // 导入LogicalUnion类，表示逻辑联合操作（UNION）
import org.apache.calcite.rel.logical.LogicalValues; // 导入LogicalValues类，表示逻辑值操作（VALUES子句）

import java.util.ArrayDeque; // 导入ArrayDeque类，用于实现双端队列，作为栈来使用
import java.util.ArrayList; // 导入ArrayList类，用于动态数组列表
import java.util.Deque; // 导入Deque接口，表示双端队列
import java.util.List; // 导入List接口，表示有序列表

/**
 * Basic implementation of {@link RelShuttle} that calls // RelShuttle接口的基础实现类
 * {@link RelNode#accept(RelShuttle)} on each child, and // 对每个子节点调用accept方法让子节点接受此shuttle访问
 * {@link RelNode#copy(org.apache.calcite.plan.RelTraitSet, java.util.List)} if // 如果任何子节点发生变化
 * any children change. // 则调用copy方法创建父节点的新副本
 * 
 * 该类是RelShuttle接口的默认实现，采用了访问者模式（Visitor Pattern）来遍历和转换关系表达式树（RelNode树）。
 * 它的核心功能是：1）深度优先遍历整个关系表达式树；2）对每个节点进行访问；3）如果子节点发生变化，
 * 则创建父节点的新副本，保持不可变性原则。这是Calcite中实现规则转换、优化规则应用等操作的基础工具。
 * 
 * 使用场景：
 * - 规则转换：在优化器中应用转换规则时，使用RelShuttle遍历并修改关系表达式树
 * - 树形结构分析：分析或验证关系表达式树的结构
 * - 节点替换：将特定类型的节点替换为其他节点
 * - 树形结构复制：创建关系表达式树的深拷贝
 * 
 * 工作原理：
 * - 使用栈（stack）来跟踪当前访问路径，便于在递归遍历时知道父节点信息
 * - 对每个节点，先访问其所有子节点，然后根据子节点是否变化决定是否重建当前节点
 * - 采用自底向上的处理方式，先处理子节点，再处理父节点
 * - 保持不可变性：任何修改都通过创建新节点实现，不修改原有节点
 */
public class RelShuttleImpl implements RelShuttle { // 定义RelShuttleImpl类，实现RelShuttle接口
  protected final Deque<RelNode> stack = new ArrayDeque<>(); // 定义栈结构，用于在遍历过程中保存父节点信息，便于追踪当前访问路径；使用ArrayDeque作为栈实现，性能优于Stack类；protected修饰符允许子类访问和扩展

  /**
   * Visits a particular child of a parent. // 访问父节点的特定子节点
   * 
   * 该方法是RelShuttleImpl的核心方法之一，负责访问父节点的第i个子节点。
   * 
   * 处理流程：
   * 1. 将父节点压入栈中，记录当前访问路径
   * 2. 调用子节点的accept方法，让子节点接受此shuttle的访问（递归调用）
   * 3. 比较访问后的子节点与原子节点是否相同
   * 4. 如果子节点发生变化，创建父节点的新副本，将变化后的子节点替换进去
   * 5. 如果子节点未变化，直接返回原父节点
   * 6. 无论是否发生异常，都要从栈中弹出父节点，保持栈的正确性
   * 
   * 参数说明：
   * @param parent 父节点，即包含该子节点的RelNode
   * @param i 子节点在父节点的输入列表中的索引位置（从0开始）
   * @param child 要访问的子节点
   * 
   * 返回值说明：
   * @return 如果子节点发生变化，返回包含新子节点的父节点副本；否则返回原父节点
   * 
   * 设计要点：
   * - 使用try-finally确保栈操作的原子性，避免异常导致栈状态不一致
   * - 采用不可变对象模式，通过copy方法创建新节点而非修改原节点
   * - 保留父节点的traitSet（特征集合），确保节点属性不丢失
   * - 只在必要时创建新节点，优化性能
   */
  protected RelNode visitChild(RelNode parent, int i, RelNode child) { // 定义visitChild方法，访问父节点的第i个子节点
    stack.push(parent); // 将父节点压入栈顶，记录当前访问路径，便于后续处理时知道父节点信息
    try { // 使用try块确保无论是否发生异常都能正确清理栈
      RelNode child2 = child.accept(this); // 调用子节点的accept方法，让子节点接受此shuttle的访问；这里会触发递归调用，深度优先遍历子树
      if (child2 != child) { // 检查子节点是否发生了变化（即accept返回的新节点与原节点不是同一个对象）
        final List<RelNode> newInputs = new ArrayList<>(parent.getInputs()); // 如果子节点变化，创建新的输入列表，复制父节点的所有输入
        newInputs.set(i, child2); // 将新子节点设置到输入列表的第i个位置，替换原子节点
        return parent.copy(parent.getTraitSet(), newInputs); // 调用父节点的copy方法，使用原有的traitSet和新的输入列表创建父节点的新副本，保持不可变性
      } // 结束if块
      return parent; // 如果子节点未变化，直接返回原父节点，不需要创建新节点
    } finally { // finally块确保无论是否发生异常都会执行
      stack.pop(); // 从栈顶弹出父节点，恢复栈的状态，确保栈的正确性
    } // 结束finally块
  } // 结束visitChild方法

  /**
   * 访问关系节点的所有子节点
   * 
   * 该方法遍历指定关系节点的所有子节点，对每个子节点调用visitChild方法进行访问。
   * 
   * 处理流程：
   * 1. 使用Ord.zip将输入列表转换为带索引的Ord对象列表
   * 2. 遍历每个子节点，调用visitChild方法
   * 3. 如果visitChild返回新的父节点（表示子节点发生变化），则更新rel变量
   * 4. 最终返回处理后的关系节点（可能是原节点，也可能是新节点）
   * 
   * 参数说明：
   * @param rel 要访问其所有子节点的关系节点
   * 
   * 返回值说明：
   * @return 处理后的关系节点，如果任何子节点发生变化则返回新节点，否则返回原节点
   * 
   * 设计要点：
   * - 使用Ord.zip获取子节点的索引，便于在visitChild中指定子节点位置
   * - 采用迭代方式处理所有子节点，而非递归，避免栈溢出
   * - 每次迭代都可能产生新的父节点，需要更新rel变量
   * - 该方法是visitChildren的通用实现，适用于大多数有多个子节点的RelNode
   */
  protected RelNode visitChildren(RelNode rel) { // 定义visitChildren方法，访问关系节点的所有子节点
    for (Ord<RelNode> input : Ord.zip(rel.getInputs())) { // 遍历关系节点的所有输入（子节点），使用Ord.zip获取每个输入及其索引
      rel = visitChild(rel, input.i, input.e); // 对每个子节点调用visitChild方法，传入当前rel、子节点索引和子节点本身；将返回值赋给rel，以便后续处理使用更新后的节点
    } // 结束for循环
    return rel; // 返回处理后的关系节点，可能是原节点也可能是新节点
  } // 结束visitChildren方法

  /**
   * 访问逻辑聚合节点
   * 
   * 该方法处理LogicalAggregate节点（对应SQL中的GROUP BY操作）。
   * 
   * 处理逻辑：
   * - LogicalAggregate只有一个子节点（即要聚合的输入关系）
   * - 调用visitChild访问该子节点
   * - 如果子节点发生变化，返回包含新子节点的Aggregate副本
   * 
   * 参数说明：
   * @param aggregate 逻辑聚合节点
   * 
   * 返回值说明：
   * @return 处理后的聚合节点
   */
  @Override public RelNode visit(LogicalAggregate aggregate) { // 重写visit方法，处理LogicalAggregate节点
    return visitChild(aggregate, 0, aggregate.getInput()); // 调用visitChild访问聚合节点的第0个（也是唯一的）子节点，即输入关系
  } // 结束visit方法

  /**
   * 访问逻辑匹配节点
   * 
   * 该方法处理LogicalMatch节点（对应SQL中的MATCH_RECOGNIZE子句，用于模式匹配）。
   * 
   * 处理逻辑：
   * - LogicalMatch只有一个子节点（即要匹配的输入关系）
   * - 调用visitChild访问该子节点
   * - 如果子节点发生变化，返回包含新子节点的Match副本
   * 
   * 参数说明：
   * @param match 逻辑匹配节点
   * 
   * 返回值说明：
   * @return 处理后的匹配节点
   */
  @Override public RelNode visit(LogicalMatch match) { // 重写visit方法，处理LogicalMatch节点
    return visitChild(match, 0, match.getInput()); // 调用visitChild访问匹配节点的第0个（也是唯一的）子节点，即输入关系
  } // 结束visit方法

  /**
   * 访问表扫描节点
   * 
   * 该方法处理TableScan节点（对应SQL中的表扫描操作，即FROM子句中的表）。
   * 
   * 处理逻辑：
   * - TableScan是叶子节点，没有子节点
   * - 直接返回原节点，不需要任何处理
   * 
   * 参数说明：
   * @param scan 表扫描节点
   * 
   * 返回值说明：
   * @return 原表扫描节点（不做任何修改）
   */
  @Override public RelNode visit(TableScan scan) { // 重写visit方法，处理TableScan节点
    return scan; // 直接返回原表扫描节点，因为表扫描是叶子节点，没有子节点需要处理
  } // 结束visit方法

  /**
   * 访问表函数扫描节点
   * 
   * 该方法处理TableFunctionScan节点（对应SQL中的表函数调用，如UNNEST）。
   * 
   * 处理逻辑：
   * - TableFunctionScan可能有多个子节点（作为表函数的参数）
   * - 调用visitChildren访问所有子节点
   * - 如果任何子节点发生变化，返回包含新子节点的TableFunctionScan副本
   * 
   * 参数说明：
   * @param scan 表函数扫描节点
   * 
   * 返回值说明：
   * @return 处理后的表函数扫描节点
   */
  @Override public RelNode visit(TableFunctionScan scan) { // 重写visit方法，处理TableFunctionScan节点
    return visitChildren(scan); // 调用visitChildren访问表函数扫描节点的所有子节点
  } // 结束visit方法

  /**
   * 访问逻辑值节点
   * 
   * 该方法处理LogicalValues节点（对应SQL中的VALUES子句，用于构造常量行）。
   * 
   * 处理逻辑：
   * - LogicalValues是叶子节点，没有子节点
   * - 直接返回原节点，不需要任何处理
   * 
   * 参数说明：
   * @param values 逻辑值节点
   * 
   * 返回值说明：
   * @return 原逻辑值节点（不做任何修改）
   */
  @Override public RelNode visit(LogicalValues values) { // 重写visit方法，处理LogicalValues节点
    return values; // 直接返回原逻辑值节点，因为VALUES是叶子节点，没有子节点需要处理
  } // 结束visit方法

  /**
   * 访问逻辑过滤节点
   * 
   * 该方法处理LogicalFilter节点（对应SQL中的WHERE条件）。
   * 
   * 处理逻辑：
   * - LogicalFilter只有一个子节点（即要过滤的输入关系）
   * - 调用visitChild访问该子节点
   * - 如果子节点发生变化，返回包含新子节点的Filter副本
   * 
   * 参数说明：
   * @param filter 逻辑过滤节点
   * 
   * 返回值说明：
   * @return 处理后的过滤节点
   */
  @Override public RelNode visit(LogicalFilter filter) { // 重写visit方法，处理LogicalFilter节点
    return visitChild(filter, 0, filter.getInput()); // 调用visitChild访问过滤节点的第0个（也是唯一的）子节点，即输入关系
  } // 结束visit方法

  /**
   * 访问逻辑计算节点
   * 
   * 该方法处理LogicalCalc节点（对应SQL中的计算表达式，类似于SELECT中的表达式计算）。
   * 
   * 处理逻辑：
   * - LogicalCalc可能有多个子节点（作为计算表达式的输入）
   * - 调用visitChildren访问所有子节点
   * - 如果任何子节点发生变化，返回包含新子节点的Calc副本
   * 
   * 参数说明：
   * @param calc 逻辑计算节点
   * 
   * 返回值说明：
   * @return 处理后的计算节点
   */
  @Override public RelNode visit(LogicalCalc calc) { // 重写visit方法，处理LogicalCalc节点
    return visitChildren(calc); // 调用visitChildren访问计算节点的所有子节点
  } // 结束visit方法

  /**
   * 访问逻辑投影节点
   * 
   * 该方法处理LogicalProject节点（对应SQL中的SELECT字段列表）。
   * 
   * 处理逻辑：
   * - LogicalProject只有一个子节点（即要投影的输入关系）
   * - 调用visitChild访问该子节点
   * - 如果子节点发生变化，返回包含新子节点的Project副本
   * 
   * 参数说明：
   * @param project 逻辑投影节点
   * 
   * 返回值说明：
   * @return 处理后的投影节点
   */
  @Override public RelNode visit(LogicalProject project) { // 重写visit方法，处理LogicalProject节点
    return visitChild(project, 0, project.getInput()); // 调用visitChild访问投影节点的第0个（也是唯一的）子节点，即输入关系
  } // 结束visit方法

  /**
   * 访问逻辑连接节点
   * 
   * 该方法处理LogicalJoin节点（对应SQL中的JOIN操作，包括INNER JOIN、LEFT JOIN等）。
   * 
   * 处理逻辑：
   * - LogicalJoin通常有两个子节点（左表和右表）
   * - 调用visitChildren访问所有子节点
   * - 如果任何子节点发生变化，返回包含新子节点的Join副本
   * 
   * 参数说明：
   * @param join 逻辑连接节点
   * 
   * 返回值说明：
   * @return 处理后的连接节点
   */
  @Override public RelNode visit(LogicalJoin join) { // 重写visit方法，处理LogicalJoin节点
    return visitChildren(join); // 调用visitChildren访问连接节点的所有子节点（通常是左表和右表）
  } // 结束visit方法

  /**
   * 访问逻辑关联节点
   * 
   * 该方法处理LogicalCorrelate节点（用于处理关联子查询）。
   * 
   * 处理逻辑：
   * - LogicalCorrelate通常有两个子节点（左输入和右输入）
   * - 调用visitChildren访问所有子节点
   * - 如果任何子节点发生变化，返回包含新子节点的Correlate副本
   * 
   * 参数说明：
   * @param correlate 逻辑关联节点
   * 
   * 返回值说明：
   * @return 处理后的关联节点
   */
  @Override public RelNode visit(LogicalCorrelate correlate) { // 重写visit方法，处理LogicalCorrelate节点
    return visitChildren(correlate); // 调用visitChildren访问关联节点的所有子节点
  } // 结束visit方法

  /**
   * 访问逻辑联合节点
   * 
   * 该方法处理LogicalUnion节点（对应SQL中的UNION操作）。
   * 
   * 处理逻辑：
   * - LogicalUnion可能有多个子节点（多个要联合的关系）
   * - 调用visitChildren访问所有子节点
   * - 如果任何子节点发生变化，返回包含新子节点的Union副本
   * 
   * 参数说明：
   * @param union 逻辑联合节点
   * 
   * 返回值说明：
   * @return 处理后的联合节点
   */
  @Override public RelNode visit(LogicalUnion union) { // 重写visit方法，处理LogicalUnion节点
    return visitChildren(union); // 调用visitChildren访问联合节点的所有子节点
  } // 结束visit方法

  /**
   * 访问逻辑交集节点
   * 
   * 该方法处理LogicalIntersect节点（对应SQL中的INTERSECT操作）。
   * 
   * 处理逻辑：
   * - LogicalIntersect可能有多个子节点（多个要求交的关系）
   * - 调用visitChildren访问所有子节点
   * - 如果任何子节点发生变化，返回包含新子节点的Intersect副本
   * 
   * 参数说明：
   * @param intersect 逻辑交集节点
   * 
   * 返回值说明：
   * @return 处理后的交集节点
   */
  @Override public RelNode visit(LogicalIntersect intersect) { // 重写visit方法，处理LogicalIntersect节点
    return visitChildren(intersect); // 调用visitChildren访问交集节点的所有子节点
  } // 结束visit方法

  /**
   * 访问逻辑差集节点
   * 
   * 该方法处理LogicalMinus节点（对应SQL中的EXCEPT操作）。
   * 
   * 处理逻辑：
   * - LogicalMinus可能有多个子节点（多个要求差的关系）
   * - 调用visitChildren访问所有子节点
   * - 如果任何子节点发生变化，返回包含新子节点的Minus副本
   * 
   * 参数说明：
   * @param minus 逻辑差集节点
   * 
   * 返回值说明：
   * @return 处理后的差集节点
   */
  @Override public RelNode visit(LogicalMinus minus) { // 重写visit方法，处理LogicalMinus节点
    return visitChildren(minus); // 调用visitChildren访问差集节点的所有子节点
  } // 结束visit方法

  /**
   * 访问逻辑排序节点
   * 
   * 该方法处理LogicalSort节点（对应SQL中的ORDER BY操作）。
   * 
   * 处理逻辑：
   * - LogicalSort可能有多个子节点（主要是要排序的输入关系，也可能包含offset和fetch）
   * - 调用visitChildren访问所有子节点
   * - 如果任何子节点发生变化，返回包含新子节点的Sort副本
   * 
   * 参数说明：
   * @param sort 逻辑排序节点
   * 
   * 返回值说明：
   * @return 处理后的排序节点
   */
  @Override public RelNode visit(LogicalSort sort) { // 重写visit方法，处理LogicalSort节点
    return visitChildren(sort); // 调用visitChildren访问排序节点的所有子节点
  } // 结束visit方法

  /**
   * 访问逻辑交换节点
   * 
   * 该方法处理LogicalExchange节点（用于分布式系统中的数据交换操作）。
   * 
   * 处理逻辑：
   * - LogicalExchange可能有多个子节点（作为数据交换的输入）
   * - 调用visitChildren访问所有子节点
   * - 如果任何子节点发生变化，返回包含新子节点的Exchange副本
   * 
   * 参数说明：
   * @param exchange 逻辑交换节点
   * 
   * 返回值说明：
   * @return 处理后的交换节点
   */
  @Override public RelNode visit(LogicalExchange exchange) { // 重写visit方法，处理LogicalExchange节点
    return visitChildren(exchange); // 调用visitChildren访问交换节点的所有子节点
  } // 结束visit方法

  /**
   * 访问逻辑表修改节点
   * 
   * 该方法处理LogicalTableModify节点（对应SQL中的INSERT、UPDATE、DELETE操作）。
   * 
   * 处理逻辑：
   * - LogicalTableModify可能有多个子节点（作为修改操作的输入）
   * - 调用visitChildren访问所有子节点
   * - 如果任何子节点发生变化，返回包含新子节点的TableModify副本
   * 
   * 参数说明：
   * @param modify 逻辑表修改节点
   * 
   * 返回值说明：
   * @return 处理后的表修改节点
   */
  @Override public RelNode visit(LogicalTableModify modify) { // 重写visit方法，处理LogicalTableModify节点
    return visitChildren(modify); // 调用visitChildren访问表修改节点的所有子节点
  } // 结束visit方法

  /**
   * 访问逻辑as-of连接节点
   * 
   * 该方法处理LogicalAsofJoin节点（一种特殊的连接操作，用于基于时间戳的as-of连接）。
   * 
   * 处理逻辑：
   * - LogicalAsofJoin通常有两个子节点（左表和右表）
   * - 调用visitChildren访问所有子节点
   * - 如果任何子节点发生变化，返回包含新子节点的AsofJoin副本
   * 
   * 参数说明：
   * @param logicalAsofJoin 逻辑as-of连接节点
   * 
   * 返回值说明：
   * @return 处理后的as-of连接节点
   */
  @Override public RelNode visit(LogicalAsofJoin logicalAsofJoin) { // 重写visit方法，处理LogicalAsofJoin节点
    return visitChildren(logicalAsofJoin); // 调用visitChildren访问as-of连接节点的所有子节点
  } // 结束visit方法

  /**
   * 访问逻辑重复联合节点
   * 
   * 该方法处理LogicalRepeatUnion节点（用于递归查询，如WITH RECURSIVE）。
   * 
   * 处理逻辑：
   * - LogicalRepeatUnion可能有多个子节点（包括基准部分和递归部分）
   * - 调用visitChildren访问所有子节点
   * - 如果任何子节点发生变化，返回包含新子节点的RepeatUnion副本
   * 
   * 参数说明：
   * @param logicalRepeatUnion 逻辑重复联合节点
   * 
   * 返回值说明：
   * @return 处理后的重复联合节点
   */
  @Override public RelNode visit(LogicalRepeatUnion logicalRepeatUnion) { // 重写visit方法，处理LogicalRepeatUnion节点
    return visitChildren(logicalRepeatUnion); // 调用visitChildren访问重复联合节点的所有子节点
  } // 结束visit方法

  /**
   * 访问其他类型的关系节点
   * 
   * 该方法是所有visit方法的兜底实现，处理上述特定类型之外的其他关系节点。
   * 
   * 处理逻辑：
   * - 对于任何未在上述特定visit方法中处理的RelNode类型
   * - 调用visitChildren访问其所有子节点
   * - 如果任何子节点发生变化，返回包含新子节点的RelNode副本
   * 
   * 参数说明：
   * @param other 其他类型的关系节点
   * 
   * 返回值说明：
   * @return 处理后的关系节点
   * 
   * 设计要点：
   * - 该方法提供了扩展性，使得RelShuttleImpl可以处理新增的RelNode类型
   * - 子类可以重写该方法来添加特定类型的处理逻辑
   * - 默认实现使用通用的visitChildren方法，适用于大多数RelNode类型
   */
  @Override public RelNode visit(RelNode other) { // 重写visit方法，处理其他类型的关系节点
    return visitChildren(other); // 调用visitChildren访问该节点的所有子节点
  } // 结束visit方法
} // 结束RelShuttleImpl类
