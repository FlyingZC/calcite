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
package org.apache.calcite.adapter.arrow;  // 定义包名，该类属于 Apache Calcite 的 Arrow 适配器模块

import org.apache.calcite.plan.RelOptCluster;  // 导入 RelOptCluster 类，表示关系表达式集群，包含共享的环境信息
import org.apache.calcite.plan.RelOptCost;  // 导入 RelOptCost 类，用于表示关系操作符的执行代价
import org.apache.calcite.plan.RelOptPlanner;  // 导入 RelOptPlanner 类，表示关系优化器，用于生成执行计划
import org.apache.calcite.plan.RelTraitSet;  // 导入 RelTraitSet 类，表示关系表达式的特征集合（如物理实现方式）
import org.apache.calcite.rel.RelNode;  // 导入 RelNode 接口，表示关系代数中的关系表达式（抽象语法树节点）
import org.apache.calcite.rel.core.Project;  // 导入 Project 类，表示投影操作（SELECT 子句中的字段选择和计算）
import org.apache.calcite.rel.metadata.RelMetadataQuery;  // 导入 RelMetadataQuery 类，用于查询关系表达式的元数据（如行数、大小等）
import org.apache.calcite.rel.type.RelDataType;  // 导入 RelDataType 类，表示关系数据的类型信息（字段名和类型）
import org.apache.calcite.rex.RexInputRef;  // 导入 RexInputRef 类，表示对输入字段的引用（如 SELECT col1 中的 col1）
import org.apache.calcite.rex.RexNode;  // 导入 RexNode 类，表示行表达式（Row Expression），用于描述计算逻辑

import com.google.common.collect.ImmutableList;  // 导入 Google Guava 的 ImmutableList 类，表示不可变的列表
import com.google.common.collect.ImmutableSet;  // 导入 Google Guava 的 ImmutableSet 类，表示不可变的集合

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入 Checker Framework 的注解，用于标记可空类型

import java.util.ArrayList;  // 导入 Java 标准库的 ArrayList 类，表示动态数组
import java.util.List;  // 导入 Java 标准库的 List 接口，表示有序集合

import static java.util.Objects.requireNonNull;  // 导入 Java 的 Objects.requireNonNull 静态方法，用于非空检查

/**
 * Implementation of {@link org.apache.calcite.rel.core.Project}
 * relational expression in Arrow.
 * ArrowProject 类：实现了 Project（投影）关系表达式在 Arrow 数据库中的具体实现
 * 
 * 核心作用：
 * 1. Project 操作是关系代数中的基本操作之一，对应 SQL 中的 SELECT 子句
 * 2. 负责从输入数据中选择指定的列，并可能对这些列进行计算（如 SELECT col1, col2*2 AS col3）
 * 3. 在 Arrow 适配器中，这个类将 Calcite 的逻辑投影操作转换为 Arrow 的物理实现
 * 4. 支持两种投影：简单投影（仅选择列）和计算投影（对列进行表达式计算）
 * 
 * 关键概念：
 * - Arrow：Apache Arrow 是一个跨语言的内存列式数据格式，用于高效的数据处理
 * - ArrowRel：Arrow 适配器的核心接口，所有 Arrow 相关的关系表达式都实现此接口
 * - Project：关系代数中的投影操作，用于选择和转换输出列
 * - RexNode：行表达式节点，用于描述投影中的计算逻辑
 * - RexInputRef：输入字段引用，表示直接选择输入中的某列（最简单的投影）
 * 
 * 使用场景：
 * - 当执行 SQL 查询如 "SELECT col1, col2 FROM table" 时，会创建 ArrowProject 节点
 * - 当执行 SQL 查询如 "SELECT col1, col2*2 AS double_col FROM table" 时，会创建包含表达式计算的 ArrowProject 节点
 * - 在查询优化过程中，Project 节点可能被下推到数据源，以提高查询效率
 */
class ArrowProject extends Project implements ArrowRel {  // ArrowProject 类继承自 Project 基类，并实现 ArrowRel 接口，表示 Arrow 中的投影操作

  /** Creates an ArrowProject. */
  /** 构造方法：创建一个 ArrowProject 实例
   * 
   * 参数说明：
   * @param cluster - 关系表达式集群，包含优化器和类型工厂等共享环境信息
   * @param traitSet - 特征集合，定义该关系表达式的物理特征（如使用 Arrow 约定）
   * @param input - 输入关系表达式，表示投影操作的数据来源（如 TableScan 或其他操作）
   * @param projects - 投影表达式列表，每个 RexNode 表示一个输出列，可以是字段引用或复杂表达式
   * @param rowType - 输出行类型，定义投影结果的字段名和类型
   * 
   * 构造逻辑：
   * 1. 调用父类 Project 的构造方法，传入所有必要参数
   * 2. ImmutableList.of() 表示没有指示器（indicator）字段，这是 Calcite 的历史特性
   * 3. ImmutableSet.of() 表示没有标记为常量的字段
   * 4. 通过断言确保当前节点和输入节点都使用 Arrow 约定（CONVENTION）
   * 
   * Arrow 约定说明：
   * - ArrowRel.CONVENTION 是一个特殊的特征，表示该关系表达式将在 Arrow 引擎上执行
   * - 所有 Arrow 相关的关系表达式都必须使用此约定
   * - 优化器会根据约定将逻辑计划转换为特定引擎的物理计划
   */
  ArrowProject(RelOptCluster cluster, RelTraitSet traitSet,  // 参数：关系表达式集群和特征集合
      RelNode input, List<? extends RexNode> projects, RelDataType rowType) {  // 参数：输入节点、投影表达式列表、输出行类型
    super(cluster, traitSet, ImmutableList.of(), input, projects, rowType, ImmutableSet.of());  // 调用父类构造方法，创建投影关系表达式，传入空列表表示没有指示器字段，传入空集合表示没有常量字段
    assert getConvention() == ArrowRel.CONVENTION;  // 断言：确保当前节点的约定是 Arrow 约定，防止类型不匹配
    assert getConvention() == input.getConvention();  // 断言：确保输入节点的约定也是 Arrow 约定，保证整个计划的一致性
  }

  /** copy 方法：创建当前 ArrowProject 的副本，用于优化器的规则重写和计划转换
   * 
   * 参数说明：
   * @param traitSet - 新的特征集合，可能在规则应用后发生变化（如并行度、排序等）
   * @param input - 新的输入关系表达式，可能在规则应用后发生变化（如谓词下推）
   * @param projects - 新的投影表达式列表，可能在规则应用后发生变化（如投影消除、常量折叠）
   * @param rowType - 新的输出行类型，与新的投影表达式对应
   * 
   * 返回值：
   * @return 返回一个新的 ArrowProject 实例，包含指定的特征、输入、投影和行类型
   * 
   * 使用场景：
   * 1. 当优化器应用规则（如 ProjectMergeRule）时，需要创建新的 Project 节点
   * 2. 当优化器进行代价估算和计划选择时，可能需要创建多个副本进行比较
   * 3. 当进行物理计划转换时，可能需要修改特征集合并创建新实例
   * 
   * 实现细节：
   * - 使用 getCluster() 获取当前节点的集群，保持环境信息一致
   * - 创建新的 ArrowProject 实例，而不是修改当前实例（不可变对象模式）
   * - 不可变对象模式使得并发优化更加安全
   */
  @Override public Project copy(RelTraitSet traitSet, RelNode input,  // 方法签名：重写父类的 copy 方法，返回新的 Project 节点
      List<RexNode> projects, RelDataType rowType) {  // 参数：新的特征集合、输入节点、投影表达式列表、输出行类型
    return new ArrowProject(getCluster(), traitSet, input, projects,  // 返回：创建新的 ArrowProject 实例，使用当前节点的集群和新的参数
        rowType);  // 传入新的行类型参数
  }

  /** computeSelfCost 方法：计算当前 ArrowProject 节点的执行代价
   * 
   * 参数说明：
   * @param planner - 关系优化器，提供代价计算的环境和配置
   * @param mq - 元数据查询接口，用于获取输入的统计信息（如行数、大小等）
   * 
   * 返回值：
   * @return 返回 RelOptCost 对象，表示该节点的执行代价（CPU、IO、内存等）
   * 
   * 代价计算逻辑：
   * 1. 调用父类的 computeSelfCost 方法，获取基础代价
   * 2. 将基础代价乘以 0.1，表示 Arrow 的投影操作非常高效
   * 3. 乘以 0.1 的原因是 Arrow 是列式存储，投影操作只需要读取需要的列，不需要扫描所有数据
   * 
   * 代价模型说明：
   * - CPU 代价：与处理的行数和表达式的复杂度成正比
   * - IO 代价：与读取的数据量成正比，Arrow 的列式存储使得投影的 IO 代价很低
   * - 内存代价：通常较小，因为投影操作不需要额外的缓冲区
   * 
   * 优化器使用：
   * - 优化器会比较不同执行计划的代价，选择代价最小的计划
   * - 较低的代价使得 Project 节点更容易被下推到数据源
   * - 代价计算影响谓词下推、投影消除等优化规则的决策
   */
  @Override public RelOptCost computeSelfCost(RelOptPlanner planner,  // 方法签名：重写父类的 computeSelfCost 方法，计算执行代价
      RelMetadataQuery mq) {  // 参数：优化器和元数据查询接口
    final RelOptCost cost = super.computeSelfCost(planner, mq);  // 调用父类方法计算基础代价，基于输入的行数和投影表达式的复杂度
    return requireNonNull(cost, "cost").multiplyBy(0.1);  // 返回：将基础代价乘以 0.1，表示 Arrow 的投影操作非常高效（列式存储优势），requireNonNull 确保代价不为 null
  }

  /** implement 方法：实现 ArrowProject 的物理执行逻辑，将逻辑投影转换为 Arrow 的物理操作
   * 
   * 参数说明：
   * @param implementor - 实现器对象，负责遍历关系表达式树并生成 Arrow 的执行代码
   * 
   * 实现逻辑：
   * 1. 首先访问输入节点（递归处理输入），确保输入节点也被实现
   * 2. 获取投影字段列表，判断是否为简单投影（仅选择列，不包含表达式计算）
   * 3. 如果是简单投影，将投影字段信息添加到实现器中，用于生成 Arrow 的列选择代码
   * 4. 如果不是简单投影（包含表达式计算），则不添加投影字段信息，需要其他机制处理
   * 
   * 简单投影 vs 计算投影：
   * - 简单投影：如 "SELECT col1, col2 FROM table"，投影表达式都是 RexInputRef
   * - 计算投影：如 "SELECT col1, col2*2 AS double_col FROM table"，投影表达式包含计算
   * - 简单投影可以直接映射到 Arrow 的列选择，效率极高
   * - 计算投影需要额外的表达式求值逻辑，可能需要使用 Arrow 的计算引擎
   * 
   * 实现器（Implementor）的作用：
   * - 实现器是访问者模式的实现，遍历关系表达式树
   * - 收集每个节点的执行信息，生成最终的执行代码
   * - visitInput 方法确保输入节点先被处理，保证执行顺序正确
   * - addProjectFields 方法记录需要选择的列，用于生成 Arrow 的列读取代码
   */
  @Override public void implement(Implementor implementor) {  // 方法签名：重写 ArrowRel 接口的 implement 方法，实现物理执行逻辑
    implementor.visitInput(0, getInput());  // 访问第一个输入节点（Project 只有一个输入），递归实现输入节点，确保输入先被处理
    List<Integer> projectedFields = getProjectFields(getProjects());  // 调用 getProjectFields 方法，将投影表达式列表转换为字段索引列表，判断是否为简单投影
    if (projectedFields != null) {  // 判断：如果投影字段列表不为 null，说明是简单投影（所有投影表达式都是 RexInputRef）
      implementor.addProjectFields(projectedFields);  // 将投影字段索引列表添加到实现器中，用于生成 Arrow 的列选择代码
    }  // 如果投影字段列表为 null，说明包含表达式计算，需要其他机制处理（可能需要在 Arrow 中执行表达式求值）
  }

  /** getProjectFields 方法：静态辅助方法，将投影表达式列表转换为字段索引列表
   * 
   * 参数说明：
   * @param exps - 投影表达式列表，每个 RexNode 表示一个输出列
   * 
   * 返回值：
   * @return 如果所有投影表达式都是 RexInputRef（简单投影），返回字段索引列表；否则返回 null（包含表达式计算）
   * 
   * 方法逻辑：
   * 1. 创建一个空的字段索引列表
   * 2. 遍历每个投影表达式
   * 3. 如果表达式是 RexInputRef，提取其索引（表示输入中的第几列）并添加到列表
   * 4. 如果遇到非 RexInputRef 的表达式，立即返回 null（表示不是简单投影）
   * 5. 如果所有表达式都是 RexInputRef，返回字段索引列表
   * 
   * RexInputRef 说明：
   * - RexInputRef 是对输入字段的引用，如 "SELECT col1" 中的 col1
   * - getIndex() 方法返回字段在输入中的索引（从 0 开始）
   * - 例如：输入有 3 列 [id, name, age]，"SELECT name, age" 的投影表达式为 [RexInputRef(1), RexInputRef(2)]
   * 
   * 返回值的意义：
   * - 返回字段索引列表：表示简单投影，可以直接映射到 Arrow 的列选择
   * - 返回 null：表示包含表达式计算，需要在 Arrow 中执行表达式求值
   * 
   * 优化意义：
   * - 简单投影可以在 Arrow 中直接通过列索引读取，无需额外的计算开销
   * - 计算投影需要更复杂的处理，可能影响查询性能
   * - 优化器可以基于此信息决定是否进行投影下推等优化
   */
  static @Nullable List<Integer> getProjectFields(List<RexNode> exps) {  // 方法签名：静态方法，接收投影表达式列表，返回字段索引列表或 null
    final List<Integer> fields = new ArrayList<>();  // 创建空的字段索引列表，用于存储简单投影的字段索引
    for (final RexNode exp : exps) {  // 遍历每个投影表达式
      if (exp instanceof RexInputRef) {  // 判断：如果表达式是 RexInputRef（输入字段引用）
        fields.add(((RexInputRef) exp).getIndex());  // 提取字段索引并添加到列表中，getIndex() 返回字段在输入中的位置
      } else {  // 如果表达式不是 RexInputRef（包含计算，如 col1*2）
        return null;  // 返回 null，表示这不是简单投影，包含表达式计算，需要特殊处理
      }  // 结束 else 分支
    }  // 结束 for 循环
    return fields;  // 返回字段索引列表，所有表达式都是 RexInputRef，这是一个简单投影
  }  // 结束 getProjectFields 方法
}  // 结束 ArrowProject 类
