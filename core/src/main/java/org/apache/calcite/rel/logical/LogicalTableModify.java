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
 */ // Apache许可证2.0版本，允许自由使用、修改和分发
package org.apache.calcite.rel.logical; // 逻辑关系表达式包，包含逻辑层面的关系代数操作类（不涉及具体物理实现）

import org.apache.calcite.plan.Convention; // 调用约定：定义关系表达式的物理实现方式（如逻辑层、Enumerable层等）
import org.apache.calcite.plan.RelOptCluster; // 关系表达式集群：包含同一查询中的所有关系表达式，共享类型工厂和优化器
import org.apache.calcite.plan.RelOptTable; // 关系优化表：表示表的定义和元数据（包含表名、列、类型等信息）
import org.apache.calcite.plan.RelTraitSet; // 关系特征集合：定义关系表达式的物理属性（如调用约定、排序方式等）
import org.apache.calcite.prepare.Prepare; // 查询准备相关类：包含CatalogReader等用于查询准备的工具
import org.apache.calcite.rel.RelInput; // 关系输入：用于从序列化数据创建关系表达式（支持反序列化）
import org.apache.calcite.rel.RelNode; // 关系表达式接口：所有关系代数操作的基类，定义关系表达式的基本行为
import org.apache.calcite.rel.RelShuttle; // 关系表达式访问器：用于遍历和转换关系表达式树（访问者模式）
import org.apache.calcite.rel.core.TableModify; // 表修改操作基类：表示INSERT/UPDATE/DELETE/MERGE等DML操作的抽象类
import org.apache.calcite.rex.RexNode; // 行表达式节点：表示表达式树中的节点（如列引用、常量、函数调用等）

import org.checkerframework.checker.nullness.qual.Nullable; // 注解：表示值可以为null（用于静态类型检查）

import java.util.List; // Java集合框架List接口：用于存储有序的元素列表

/**
 * Sub-class of {@link org.apache.calcite.rel.core.TableModify} // TableModify的子类：逻辑表修改操作
 * not targeted at any particular engine or calling convention. // 不针对任何特定引擎或调用约定（纯逻辑层，不涉及物理实现细节）
 * 
 * <p>LogicalTableModify是Calcite中表示表修改操作的逻辑关系表达式类。它继承自TableModify抽象类，
 * 用于表示INSERT、UPDATE、DELETE、MERGE等数据操作语言（DML）操作。作为逻辑层的实现，
 * 它不包含任何特定引擎的物理实现细节，只关注操作的语义和逻辑结构。</p>
 * 
 * <p>主要特点：</p>
 * <ul>
 * <li>使用Convention.NONE调用约定，表示这是纯逻辑层的操作</li>
 * <li>不直接执行实际的表修改操作，而是作为优化器转换的中间表示</li>
 * <li>通过规则转换，可以转换为物理层的实现（如EnumerableTableModify等）</li>
 * <li>支持所有四种DML操作：INSERT（插入）、UPDATE（更新）、DELETE（删除）、MERGE（合并）</li>
 * </ul>
 * 
 * <p>使用场景：</p>
 * <ul>
 * <li>SQL语句解析后生成初始的逻辑计划</li>
 * <li>查询优化过程中的规则转换</li>
 * <li>作为物理实现的逻辑基础</li>
 * </ul>
 * 
 * <p>示例：</p>
 * <pre>
 * // INSERT操作示例
 * LogicalTableModify insert = LogicalTableModify.create(
 *     table,           // 目标表
 *     schema,          // 目录读取器
 *     inputRel,        // 输入关系表达式（提供要插入的行）
 *     Operation.INSERT,// 操作类型
 *     null,            // 更新列列表（INSERT不需要）
 *     null,            // 源表达式列表（INSERT不需要）
 *     false);          // 是否扁平化
 * 
 * // UPDATE操作示例
 * LogicalTableModify update = LogicalTableModify.create(
 *     table,           // 目标表
 *     schema,          // 目录读取器
 *     inputRel,        // 输入关系表达式（提供旧行和新值）
 *     Operation.UPDATE,// 操作类型
 *     updateColumns,   // 更新列列表（如["name", "age"]）
 *     sourceExprs,     // 源表达式列表（如新值的表达式）
 *     false);          // 是否扁平化
 * </pre>
 */
public final class LogicalTableModify extends TableModify { // final类：逻辑表修改操作，继承自TableModify，表示逻辑层的表修改操作
  //~ Constructors ----------------------------------------------------------- // 构造方法部分标记

  /**
   * Creates a LogicalTableModify. // 创建逻辑表修改关系表达式
   *
   * <p>Use {@link #create} unless you know what you're doing. // 除非你明确知道自己在做什么，否则建议使用create静态工厂方法
   * 
   * <p>构造方法详细说明：</p>
   * <ul>
   * <li>这是主要的构造方法，用于创建LogicalTableModify实例</li>
   * <li>所有参数都会传递给父类TableModify的构造方法</li>
   * <li>特征集合必须包含Convention.NONE，表示这是逻辑层的操作</li>
   * <li>对于UPDATE操作，updateColumnList和sourceExpressionList必须非空且长度相等</li>
   * <li>对于INSERT和DELETE操作，updateColumnList和sourceExpressionList必须为null</li>
   * </ul>
   * 
   * @param cluster 关系表达式集群：包含类型工厂、优化器等共享资源
   * @param traitSet 特征集合：定义关系表达式的物理属性，必须包含Convention.NONE
   * @param table 目标表：要修改的表，包含表的元数据信息
   * @param schema 目录读取器：提供对表、列等元数据的访问
   * @param input 输入子节点：提供要修改的行数据的关系表达式
   * @param operation 操作类型：INSERT/UPDATE/DELETE/MERGE之一
   * @param updateColumnList 更新列列表：UPDATE操作中要更新的列名列表，非UPDATE操作时为null
   * @param sourceExpressionList 源表达式列表：UPDATE操作中要设置的新值表达式列表，非UPDATE操作时为null
   * @param flattened 是否扁平化：指示是否将输入行类型扁平化（展开嵌套结构）
   */
  public LogicalTableModify(RelOptCluster cluster, RelTraitSet traitSet, // 参数：关系表达式集群和特征集合
      RelOptTable table, Prepare.CatalogReader schema, RelNode input, // 参数：目标表、目录读取器、输入子节点
      Operation operation, @Nullable List<String> updateColumnList, // 参数：操作类型、更新列列表（可为null）
      @Nullable List<RexNode> sourceExpressionList, boolean flattened) { // 参数：源表达式列表（可为null）、是否扁平化
    super(cluster, traitSet, table, schema, input, operation, updateColumnList, // 调用父类TableModify的构造方法，传递所有参数
        sourceExpressionList, flattened); // 继续传递源表达式列表和扁平化标志
  }

  /**
   * Creates a LogicalTableModify by parsing serialized output. // 通过解析序列化输出来创建LogicalTableModify
   * 
   * <p>此构造方法用于从序列化数据（如JSON、XML等格式）恢复LogicalTableModify对象。
   * 主要用于分布式计算、持久化或跨进程传输场景。</p>
   * 
   * <p>工作原理：</p>
   * <ul>
   * <li>从RelInput对象中提取序列化的数据</li>
   * <li>调用父类TableModify的构造方法进行反序列化</li>
   * <li>RelInput包含集群、特征集合、表、输入子节点、操作类型等所有必要信息</li>
   * </ul>
   * 
   * @param input 关系输入对象：包含序列化的LogicalTableModify数据
   */
  public LogicalTableModify(RelInput input) { // 参数：关系输入对象，包含序列化的数据
    super(input); // 调用父类TableModify的构造方法，从序列化数据恢复对象
  }

  @Deprecated // to be removed before 2.0 // 已废弃：将在2.0版本之前移除，不建议使用
  public LogicalTableModify(RelOptCluster cluster, RelOptTable table, // 废弃的构造方法：参数较少，缺少traitSet和sourceExpressionList
      Prepare.CatalogReader schema, RelNode input, Operation operation, // 参数：目录读取器、输入子节点、操作类型
      List<String> updateColumnList, boolean flattened) { // 参数：更新列列表、是否扁平化
    this(cluster, // 调用完整的构造方法
        cluster.traitSetOf(Convention.NONE), // 自动创建特征集合，设置为Convention.NONE（逻辑层）
        table, // 传递目标表
        schema, // 传递目录读取器
        input, // 传递输入子节点
        operation, // 传递操作类型
        updateColumnList, // 传递更新列列表
        null, // 源表达式列表设为null（此废弃构造方法不支持）
        flattened); // 传递扁平化标志
  }

  /** Creates a LogicalTableModify. */ // 创建逻辑表修改关系表达式的静态工厂方法
  /**
   * 静态工厂方法：创建LogicalTableModify实例
   * 
   * <p>这是推荐的创建LogicalTableModify实例的方式，相比直接使用构造方法有以下优势：</p>
   * <ul>
   * <li>自动设置正确的特征集合（Convention.NONE）</li>
   * <li>从输入子节点获取集群信息，避免参数冗余</li>
   * <li>提供更简洁的API，减少出错可能性</li>
   * </ul>
   * 
   * <p>实现细节：</p>
   * <ul>
   * <li>从输入子节点获取RelOptCluster</li>
   * <li>使用cluster.traitSetOf(Convention.NONE)创建特征集合</li>
   * <li>调用完整的构造方法创建实例</li>
   * </ul>
   * 
   * @param table 目标表：要修改的表
   * @param schema 目录读取器：提供对表、列等元数据的访问
   * @param input 输入子节点：提供要修改的行数据的关系表达式
   * @param operation 操作类型：INSERT/UPDATE/DELETE/MERGE之一
   * @param updateColumnList 更新列列表：UPDATE操作中要更新的列名列表，非UPDATE操作时为null
   * @param sourceExpressionList 源表达式列表：UPDATE操作中要设置的新值表达式列表，非UPDATE操作时为null
   * @param flattened 是否扁平化：指示是否将输入行类型扁平化
   * @return 创建的LogicalTableModify实例
   */
  public static LogicalTableModify create(RelOptTable table, // 静态方法：创建LogicalTableModify实例
      Prepare.CatalogReader schema, RelNode input, // 参数：目录读取器、输入子节点
      Operation operation, @Nullable List<String> updateColumnList, // 参数：操作类型、更新列列表（可为null）
      @Nullable List<RexNode> sourceExpressionList, boolean flattened) { // 参数：源表达式列表（可为null）、是否扁平化
    final RelOptCluster cluster = input.getCluster(); // 从输入子节点获取关系表达式集群
    final RelTraitSet traitSet = cluster.traitSetOf(Convention.NONE); // 创建特征集合，设置为Convention.NONE（逻辑层）
    return new LogicalTableModify(cluster, traitSet, table, schema, input, // 调用构造方法创建实例
        operation, updateColumnList, sourceExpressionList, flattened); // 传递操作类型、更新列列表、源表达式列表、扁平化标志
  }

  //~ Methods ---------------------------------------------------------------- // 方法部分标记

  /**
   * 复制关系表达式：创建具有新特征集合的LogicalTableModify副本
   * 
   * <p>此方法是RelNode接口的核心方法之一，用于在优化过程中创建关系表达式的变体。
   * 优化器会通过改变特征集合来探索不同的执行计划。</p>
   * 
   * <p>工作原理：</p>
   * <ul>
   * <li>验证新的特征集合包含Convention.NONE（逻辑层要求）</li>
   * <li>从输入列表中提取唯一的输入子节点（使用sole方法）</li>
   * <li>创建新的LogicalTableModify实例，保留所有原有参数，只改变特征集合</li>
   * </ul>
   * 
   * <p>使用场景：</p>
   * <ul>
   * <li>优化器在规则转换过程中创建新的关系表达式</li>
   * <li>改变调用约定（例如从逻辑层转换为物理层）</li>
   * <li>应用其他物理属性（如排序、分区等）</li>
   * </ul>
   * 
   * @param traitSet 新的特征集合：定义关系表达式的新物理属性
   * @param inputs 输入子节点列表：包含新的输入关系表达式（通常只有一个）
   * @return 具有新特征集合的LogicalTableModify副本
   */
  @Override public LogicalTableModify copy(RelTraitSet traitSet, // 重写：复制方法，创建具有新特征集合的副本
      List<RelNode> inputs) { // 参数：新的输入子节点列表
    assert traitSet.containsIfApplicable(Convention.NONE); // 断言：特征集合必须包含Convention.NONE（逻辑层要求）
    return new LogicalTableModify(getCluster(), traitSet, table, catalogReader, // 创建新实例，保留集群、特征集合、表、目录读取器
        sole(inputs), getOperation(), getUpdateColumnList(), // 从输入列表提取唯一子节点，保留操作类型、更新列列表
        getSourceExpressionList(), isFlattened()); // 保留源表达式列表和扁平化标志
  }

  /**
   * 接受关系表达式访问器：允许RelShuttle遍历和转换此关系表达式
   * 
   * <p>此方法是访问者模式（Visitor Pattern）的实现，允许RelShuttle遍历关系表达式树。
   * RelShuttle可以访问、修改或替换关系表达式树中的节点。</p>
   * 
   * <p>工作原理：</p>
   * <ul>
   * <li>将当前LogicalTableModify实例传递给RelShuttle的visit方法</li>
   * <li>RelShuttle根据其实现决定如何处理此节点（访问、修改、替换等）</li>
   * <li>返回处理后的结果（可能是原节点、新节点或null）</li>
   * </ul>
   * 
   * <p>使用场景：</p>
   * <ul>
   * <li>遍历关系表达式树以进行分析或统计</li>
   * <li>转换关系表达式树（如规则应用、优化等）</li>
   * <li>收集关系表达式树中的信息（如使用的表、列等）</li>
   * </ul>
   * 
   * <p>示例：</p>
   * <pre>
   * // 使用RelShuttle遍历关系表达式树
   * RelShuttle shuttle = new MyCustomShuttle();
   * RelNode result = logicalTableModify.accept(shuttle);
   * </pre>
   * 
   * @param shuttle 关系表达式访问器：用于遍历和转换关系表达式树的访问者
   * @return 访问器处理后的结果（可能是新的RelNode或原RelNode）
   */
  @Override public RelNode accept(RelShuttle shuttle) { // 重写：接受访问器方法，允许RelShuttle遍历此节点
    return shuttle.visit(this); // 调用RelShuttle的visit方法，将当前实例传递给访问者
  }
}
