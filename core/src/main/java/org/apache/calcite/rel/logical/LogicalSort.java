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
package org.apache.calcite.rel.logical;  // 包声明：LogicalSort类位于org.apache.calcite.rel.logical包下，是Calcite框架中逻辑层的排序关系表达式

import org.apache.calcite.plan.Convention;  // 导入Convention：约定接口，定义了关系代数表达式的调用约定（如物理实现方式）
import org.apache.calcite.plan.RelOptCluster;  // 导入RelOptCluster：关系优化集群，包含了优化器需要的共享资源（如RexBuilder、类型工厂等）
import org.apache.calcite.plan.RelTraitSet;  // 导入RelTraitSet：关系特征集合，定义了关系表达式的一组特征（如排序、分区等物理属性）
import org.apache.calcite.rel.RelCollation;  // 导入RelCollation：排序规范，定义了字段的排序方向和排序顺序
import org.apache.calcite.rel.RelCollationTraitDef;  // 导入RelCollationTraitDef：排序特征定义，用于规范化和验证排序特征
import org.apache.calcite.rel.RelInput;  // 导入RelInput：关系输入接口，用于从序列化数据中反序列化关系表达式
import org.apache.calcite.rel.RelNode;  // 导入RelNode：关系节点接口，是所有关系表达式（如TableScan、Filter、Project等）的基类
import org.apache.calcite.rel.RelShuttle;  // 导入RelShuttle：关系穿梭器接口，用于遍历和转换关系表达式树
import org.apache.calcite.rel.core.Sort;  // 导入Sort：排序关系表达式的核心基类，LogicalSort继承此类
import org.apache.calcite.rel.hint.RelHint;  // 导入RelHint：关系提示，用于向优化器提供额外的优化提示信息
import org.apache.calcite.rex.RexNode;  // 导入RexNode：行表达式节点接口，表示关系代数中的表达式（如条件、计算等）

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入Nullable：可空类型注解，标记参数或返回值可为null

import java.util.Collections;  // 导入Collections：集合工具类，提供不可变集合、排序等方法
import java.util.List;  // 导入List：列表接口，表示有序集合

/**
 * LogicalSort类：逻辑排序关系表达式
 * 
 * 类作用说明：
 * 1. 这是Sort类的逻辑层子类，表示一个不针对任何特定引擎或调用约定的排序操作
 * 2. LogicalSort是Calcite关系代数树中的一个逻辑节点，代表SQL中的ORDER BY、OFFSET、FETCH等操作
 * 3. 它定义了排序操作的行为，包括排序字段、偏移量(offset)和获取行数(fetch)，但不涉及具体的物理实现
 * 4. 逻辑节点通过规则匹配和转换规则可以转换为物理节点（如EnumerableSort、JdbcSort等）
 * 5. 作为final类，不允许被继承，确保排序操作的逻辑表示是固定和一致的
 * 
 * 核心概念：
 * - RelCollation（排序规范）：定义了按哪些字段排序、升序还是降序
 * - offset（偏移量）：跳过前N行，对应SQL的OFFSET子句
 * - fetch（获取行数）：返回N行，对应SQL的FETCH FIRST子句或LIMIT
 * - Convention.NONE：逻辑约定，表示这是逻辑节点，尚未转换为物理实现
 * 
 * 使用场景：
 * - SQL解析器解析ORDER BY子句后创建LogicalSort节点
 * - 优化器在规则匹配时识别并处理排序操作
 * - 通过规则将LogicalSort转换为物理实现（如内存排序、外部排序等）
 */
public final class LogicalSort extends Sort {  // 声明LogicalSort类为final，继承自Sort核心基类
  // 私有构造方法1：创建不带提示的LogicalSort实例
  // 参数说明：
  // - cluster: 关系优化集群，包含优化器所需的共享资源（如RexBuilder用于构建表达式）
  // - traitSet: 关系特征集合，定义了此节点的物理属性（如排序约定）
  // - input: 输入关系节点，即要排序的数据源
  // - collation: 排序规范，定义了排序字段和排序方向
  // - offset: 偏移量表达式，表示跳过前N行（可为null，表示不跳过）
  // - fetch: 获取行数表达式，表示返回N行（可为null，表示返回所有行）
  private LogicalSort(RelOptCluster cluster, RelTraitSet traitSet,
      RelNode input, RelCollation collation, @Nullable RexNode offset, @Nullable RexNode fetch) {
    // 调用重载构造方法，传入空列表作为hints参数
    this(cluster, traitSet, Collections.emptyList(), input, collation, offset, fetch);
  }

  // 私有构造方法2：创建带提示的LogicalSort实例（完整构造方法）
  // 参数说明：
  // - cluster: 关系优化集群，包含优化器所需的共享资源
  // - traitSet: 关系特征集合，定义了此节点的物理属性
  // - hints: 关系提示列表，用于向优化器提供额外的优化建议
  // - input: 输入关系节点，即要排序的数据源
  // - collation: 排序规范，定义了排序字段和排序方向
  // - offset: 偏移量表达式，表示跳过前N行（可为null）
  // - fetch: 获取行数表达式，表示返回N行（可为null）
  private LogicalSort(RelOptCluster cluster, RelTraitSet traitSet, List<RelHint> hints,
      RelNode input, RelCollation collation, @Nullable RexNode offset, @Nullable RexNode fetch) {
    // 调用父类Sort的构造方法，初始化排序关系表达式的基本属性
    super(cluster, traitSet, hints, input, collation, offset, fetch);
    // 断言：确保traitSet包含Convention.NONE（逻辑约定），保证这是逻辑节点而非物理节点
    assert traitSet.containsIfApplicable(Convention.NONE);
  }

  /**
   * 公共构造方法：通过解析序列化输出来创建LogicalSort实例
   * 
   * 方法作用：
   * 1. 用于从序列化的JSON或其他格式中反序列化LogicalSort对象
   * 2. 典型场景包括：从持久化存储中恢复关系代数树、跨进程传输关系表达式
   * 3. RelInput对象包含了重建LogicalSort所需的所有信息（cluster、traitSet、input、collation、offset、fetch）
   * 4. 直接调用父类Sort的构造方法来初始化所有属性
   * 
   * 参数说明：
   * - input: 关系输入对象，包含从序列化数据中解析出的所有属性
   */
  public LogicalSort(RelInput input) {
    // 调用父类Sort的构造方法，使用RelInput对象初始化所有属性
    super(input);
  }

  /**
   * 静态工厂方法：创建LogicalSort实例
   * 
   * 方法作用：
   * 1. 这是创建LogicalSort对象的推荐方式，封装了创建逻辑
   * 2. 自动处理排序规范的规范化（canonize），确保排序特征的一致性
   * 3. 自动构建正确的traitSet，包含Convention.NONE逻辑约定和排序特征
   * 4. 简化了调用者的代码，隐藏了复杂的构造细节
   * 
   * 创建流程：
   * 1. 从输入节点获取RelOptCluster（优化集群）
   * 2. 规范化排序规范（RelCollation），确保排序特征的标准格式
   * 3. 构建traitSet：从输入节点的traitSet开始，替换为Convention.NONE，然后添加排序特征
   * 4. 调用构造方法创建LogicalSort实例
   * 
   * 参数说明：
   * - input: 输入关系表达式，即要排序的数据源（如TableScan、Filter、Project等）
   * - collation: 排序规范数组，定义了按哪些字段排序以及排序方向（升序/降序）
   * - offset: 偏移量表达式，表示在返回第一行之前要丢弃的行数（可为null，表示不跳过）
   * - fetch: 获取行数表达式，表示要返回的行数（可为null，表示返回所有行）
   * 
   * 返回值：
   * - 返回新创建的LogicalSort实例
   */
  public static LogicalSort create(RelNode input, RelCollation collation,
      @Nullable RexNode offset, @Nullable RexNode fetch) {
    // 从输入节点获取RelOptCluster，确保使用相同的优化集群
    RelOptCluster cluster = input.getCluster();
    // 规范化排序规范：将排序特征转换为标准格式，消除冗余和等价的不同表示
    collation = RelCollationTraitDef.INSTANCE.canonize(collation);
    // 构建traitSet：从输入节点的traitSet开始，替换为Convention.NONE（逻辑约定），然后添加排序特征
    RelTraitSet traitSet =
        input.getTraitSet().replace(Convention.NONE).replace(collation);
    // 创建并返回新的LogicalSort实例
    return new LogicalSort(cluster, traitSet, input, collation, offset, fetch);
  }

  //~ Methods ----------------------------------------------------------------  // 方法分隔符注释

  /**
   * copy方法：创建LogicalSort的副本
   * 
   * 方法作用：
   * 1. 创建当前LogicalSort节点的副本，但可以修改部分属性
   * 2. 这是关系表达式树转换的核心方法，优化器通过此方法创建转换后的节点
   * 3. 支持修改traitSet、输入节点、排序规范、偏移量和获取行数
   * 4. 保持原有的hints（优化提示）不变
   * 
   * 使用场景：
   * - 优化器应用规则时，创建转换后的节点
   * - 修改输入节点（如Filter下推到TableScan）
   * - 修改排序规范（如合并多个排序操作）
   * - 修改offset和fetch（如Limit下推）
   * 
   * 参数说明：
   * - traitSet: 新的关系特征集合，可能包含不同的物理属性
   * - newInput: 新的输入关系节点
   * - newCollation: 新的排序规范
   * - offset: 偏移量表达式（可为null）
   * - fetch: 获取行数表达式（可为null）
   * 
   * 返回值：
   * - 返回新的LogicalSort实例（Sort类型）
   */
  @Override public Sort copy(RelTraitSet traitSet, RelNode newInput,
      RelCollation newCollation, @Nullable RexNode offset, @Nullable RexNode fetch) {
    // 创建并返回新的LogicalSort实例，使用新的参数但保持原有的hints
    return new LogicalSort(getCluster(), traitSet, hints, newInput,
        newCollation, offset, fetch);
  }

  /**
   * accept方法：接受关系穿梭器（RelShuttle）访问
   * 
   * 方法作用：
   * 1. 实现访问者模式，允许RelShuttle遍历和转换关系表达式树
   * 2. 是关系表达式树遍历的核心机制，支持深度优先遍历
   * 3. 将当前节点传递给shuttle的visit方法，让shuttle决定如何处理
   * 
   * 使用场景：
   * - 优化器遍历关系表达式树进行规则匹配
   * - 关系表达式树的分析和统计
   * - 关系表达式树的转换和重写
   * - 打印和调试关系表达式树
   * 
   * 参数说明：
   * - shuttle: 关系穿梭器，实现了访问者模式，可以遍历和转换关系表达式树
   * 
   * 返回值：
   * - 返回shuttle处理后的关系节点（可能是当前节点，也可能是转换后的新节点）
   */
  @Override public RelNode accept(RelShuttle shuttle) {
    // 调用shuttle的visit方法访问当前LogicalSort节点，返回处理后的节点
    return shuttle.visit(this);
  }

  /**
   * withHints方法：创建带有新提示列表的LogicalSort副本
   * 
   * 方法作用：
   * 1. 创建当前LogicalSort节点的副本，但使用新的hints列表
   * 2. 允许动态修改优化提示，而不影响原始节点
   * 3. 保持其他属性（cluster、traitSet、input、collation、offset、fetch）不变
   * 
   * 使用场景：
   * - 优化器根据统计信息添加优化提示
   * - 用户通过配置添加特定的优化建议
   * - 规则匹配时添加转换提示
   * 
   * 参数说明：
   * - hintList: 新的关系提示列表，包含优化器可以使用的提示信息
   * 
   * 返回值：
   * - 返回带有新hints列表的新LogicalSort实例（RelNode类型）
   */
  @Override public RelNode withHints(List<RelHint> hintList) {
    // 创建并返回新的LogicalSort实例，使用新的hints列表但保持其他属性不变
    return new LogicalSort(getCluster(), traitSet, hintList,
        input, collation, offset, fetch);
  }
}  // 类结束
