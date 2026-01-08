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
// 包声明：定义这个类属于 org.apache.calcite.adapter.arrow 包，这是 Calcite 适配器中的 Arrow 数据源适配器包
package org.apache.calcite.adapter.arrow;

// 导入 EnumerableRel 接口：这是 Calcite 可枚举关系表达式接口，定义了可枚举执行的关系表达式规范
import org.apache.calcite.adapter.enumerable.EnumerableRel;
// 导入 EnumerableRelImplementor 类：这是可枚举关系表达式的实现器，负责将关系表达式转换为可执行的 LINQ4J 代码
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor;
// 导入 PhysType 接口：表示物理类型，描述了关系表达式在执行时的物理类型信息
import org.apache.calcite.adapter.enumerable.PhysType;
// 导入 PhysTypeImpl 类：PhysType 接口的实现类，提供了物理类型的具体实现
import org.apache.calcite.adapter.enumerable.PhysTypeImpl;
// 导入 Blocks 类：LINQ4J 树构建工具类，用于构建代码块表达式
import org.apache.calcite.linq4j.tree.Blocks;
// 导入 Expressions 类：LINQ4J 表达式工厂类，用于创建各种表达式节点
import org.apache.calcite.linq4j.tree.Expressions;
// 导入 ConventionTraitDef 类：约定特征定义，定义了关系表达式的约定特征（如物理约定）
import org.apache.calcite.plan.ConventionTraitDef;
// 导入 RelOptCluster 类：关系表达式优化集群，包含了关系表达式的共享上下文信息
import org.apache.calcite.plan.RelOptCluster;
// 导入 RelOptCost 接口：关系表达式优化代价接口，用于估算执行代价
import org.apache.calcite.plan.RelOptCost;
// 导入 RelOptPlanner 接口：关系表达式优化器接口，定义了优化器的规范
import org.apache.calcite.plan.RelOptPlanner;
// 导入 RelOptTable 类：关系表达式优化表，表示优化过程中的表元数据
import org.apache.calcite.plan.RelOptTable;
// 导入 RelTraitSet 类：关系特征集合，表示关系表达式的一组特征（如约定、排序等）
import org.apache.calcite.plan.RelTraitSet;
// 导入 RelNode 接口：关系节点接口，是所有关系表达式节点的基接口
import org.apache.calcite.rel.RelNode;
// 导入 ConverterImpl 类：转换器实现类，是关系表达式转换器的基类
import org.apache.calcite.rel.convert.ConverterImpl;
// 导入 RelMetadataQuery 类：关系元数据查询类，用于查询关系表达式的元数据信息
import org.apache.calcite.rel.metadata.RelMetadataQuery;
// 导入 BuiltInMethod 类：内置方法枚举，包含了 Calcite 内置的方法引用
import org.apache.calcite.util.BuiltInMethod;

// 导入 Ints 类：Google Guava 库中的基本类型工具类，用于处理 int 类型的数组转换等操作
import com.google.common.primitives.Ints;

// 导入 List 接口：Java 集合框架中的列表接口
import java.util.List;

// 静态导入 requireNonNull 方法：用于检查对象是否为 null，如果为 null 则抛出 NullPointerException
import static java.util.Objects.requireNonNull;

/**
 * Relational expression representing a scan of a table in an Arrow data source.
 * 关系表达式，表示对 Arrow 数据源中表的扫描操作
 * 
 * 这个类是 Calcite 查询优化器中的一个关键转换器节点，它的主要作用是：
 * 1. 将 Arrow 特定约定（ArrowConvention）的关系表达式转换为可枚举约定（EnumerableConvention）的关系表达式
 * 2. 作为逻辑计划到物理执行计划转换的桥梁，将 Arrow 数据源的查询转换为可执行的 LINQ4J 代码
 * 3. 在 Calcite 的优化过程中，这个转换器负责将逻辑层面的 Arrow 表扫描转换为物理层面的可枚举执行
 * 
 * 继承关系：
 * - 继承自 ConverterImpl：表示这是一个转换器节点，负责将一种约定转换为另一种约定
 * - 实现 EnumerableRel 接口：表示转换后的节点是可枚举的，可以生成 LINQ4J 代码执行
 * 
 * 工作流程：
 * 1. 在查询优化过程中，当优化器决定将 Arrow 关系表达式转换为可枚举执行时，会创建这个转换器
 * 2. 转换器会遍历输入的 Arrow 关系表达式，收集查询信息（如选择的字段、过滤条件等）
 * 3. 生成对应的 LINQ4J 代码表达式，调用 ArrowTable 的查询方法执行实际的数据查询
 * 4. 返回一个 Result 对象，包含了生成的代码和物理类型信息
 */
class ArrowToEnumerableConverter
    extends ConverterImpl implements EnumerableRel {  // 继承 ConverterImpl 并实现 EnumerableRel 接口

  /**
   * 构造方法：创建 Arrow 到可枚举的转换器
   * 
   * @param cluster 关系表达式优化集群，包含了查询的共享上下文信息，如类型工厂、Rex 节点池等
   * @param traitSet 关系特征集合，定义了这个节点的特征，包括约定（Convention）等
   * @param input 输入的关系节点，通常是 ArrowTableScan 或其他 Arrow 关系表达式
   * 
   * 构造方法说明：
   * - 这个构造方法被 protected 修饰，通常由规则（ArrowToEnumerableConverterRule）调用创建
   * - 调用父类 ConverterImpl 的构造方法，传入 ConventionTraitDef.INSTANCE 表示使用约定特征定义
   * - 约定特征定义是 Calcite 中用于管理关系表达式约定的核心组件
   * - 输入节点必须是 Arrow 约定的关系表达式，输出将是 Enumerable 约定的关系表达式
   */
  protected ArrowToEnumerableConverter(RelOptCluster cluster,  // 参数：关系表达式优化集群，提供查询上下文
      RelTraitSet traitSet, RelNode input) {  // 参数：关系特征集合和输入关系节点
    super(cluster, ConventionTraitDef.INSTANCE, traitSet, input);  // 调用父类构造方法，初始化转换器
  }

  /**
   * 复制方法：创建这个关系节点的副本
   * 
   * @param traitSet 新的关系特征集合，用于替换当前节点的特征
   * @param inputs 新的输入节点列表
   * @return 新的 ArrowToEnumerableConverter 实例，具有指定的特征和输入
   * 
   * 方法说明：
   * - 这是 RelNode 接口的核心方法，用于创建节点的副本
   * - 在查询优化过程中，优化器会频繁调用这个方法来创建应用了新特征的节点
   * - 例如，当优化器应用新的排序或分布特征时，会调用 copy 方法创建新版本
   * - sole(inputs) 方法确保只有一个输入节点，因为转换器节点通常只有一个输入
   * - 保持与原节点相同的集群信息，只更新特征集合和输入节点
   */
  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {  // 重写 copy 方法
    return new ArrowToEnumerableConverter(getCluster(), traitSet, sole(inputs));  // 创建新的转换器实例
  }

  /**
   * 计算自身代价方法：估算这个转换器节点的执行代价
   * 
   * @param planner 关系表达式优化器，用于获取代价计算的上下文
   * @param mq 关系元数据查询对象，用于获取关系的元数据信息
   * @return 计算出的执行代价对象
   * 
   * 方法说明：
   * - 代价计算是查询优化器的核心，优化器根据代价选择最优的执行计划
   * - 这个方法计算转换器本身的代价，不包括输入节点的代价
   * - 首先调用父类的 computeSelfCost 方法获取基础代价
   * - 然后将代价乘以 0.1，表示这个转换操作的代价相对较低
   * - 乘以 0.1 的原因是：转换操作本身不涉及实际的数据处理，只是改变执行约定
   * - 优化器会根据这个代价决定是否应用这个转换规则
   * - requireNonNull 确保 cost 不为 null，如果为 null 则抛出异常
   */
  @Override public RelOptCost computeSelfCost(RelOptPlanner planner,  // 重写代价计算方法
      RelMetadataQuery mq) {  // 参数：元数据查询对象
    RelOptCost cost = super.computeSelfCost(planner, mq);  // 调用父类方法获取基础代价
    return requireNonNull(cost, "cost").multiplyBy(0.1);  // 将代价乘以 0.1 后返回，表示转换代价较低
  }

  /**
   * 实现方法：将这个关系表达式转换为可执行的代码
   * 
   * @param implementor 可枚举关系表达式实现器，负责生成 LINQ4J 代码
   * @param pref 偏好设置，表示对生成代码的偏好（如是否偏好数组类型）
   * @return Result 对象，包含了生成的代码块和物理类型信息
   * 
   * 方法说明：
   * - 这是 EnumerableRel 接口的核心方法，负责将关系表达式转换为可执行的 LINQ4J 代码
   * - 这个方法是整个转换过程的核心，实现了从逻辑计划到物理执行计划的转换
   * 
   * 执行流程：
   * 1. 创建 ArrowRel.Implementor 实例：这是一个内部实现器，用于遍历和收集 Arrow 查询信息
   * 2. 调用 visitInput 方法：遍历输入节点，收集查询信息（如选择的字段、过滤条件等）
   * 3. 创建 PhysType 对象：描述输出行的物理类型，包括 Java 类型和格式偏好
   * 4. 获取表元数据：从实现器中获取表对象，确保表不为 null
   * 5. 获取字段数量：从表的行类型中获取字段总数
   * 6. 生成代码表达式：创建调用 ArrowTable.arrowQuery 方法的表达式
   *    - 传入根表达式、字段索引列表和过滤条件
   *    - 如果有选择的字段，使用 IMMUTABLE_INT_LIST_COPY_OF 创建字段索引列表
   *    - 如果没有选择的字段，使用 IMMUTABLE_INT_LIST_IDENTITY 创建全字段索引列表
   * 7. 返回 Result 对象：包含物理类型和生成的代码块
   * 
   * 技术细节：
   * - Expressions.call：创建方法调用表达式，表示在运行时调用指定方法
   * - table.getExpression(ArrowTable.class)：获取表的 ArrowTable 表达式
   * - ArrowMethod.ARROW_QUERY.method：获取 ArrowTable.arrowQuery 方法的引用
   * - implementor.getRootExpression()：获取根表达式，用于代码生成的上下文
   * - Ints.toArray：将 Integer 集合转换为 int 数组
   * - Expressions.constant：创建常量表达式
   * - Blocks.toBlock：将表达式转换为代码块
   * 
   * 生成的代码示例（伪代码）：
   * return table.arrowQuery(rootExpression, [0, 2, 4], filterCondition);
   * 这表示查询表的第 0、2、4 列，并应用指定的过滤条件
   */
  @Override public Result implement(EnumerableRelImplementor implementor,  // 重写实现方法
      Prefer pref) {  // 参数：代码生成偏好设置
    // 创建 ArrowRel 的内部实现器，用于收集查询信息
    final ArrowRel.Implementor arrowImplementor = new ArrowRel.Implementor();
    // 访问输入节点（索引为 0），遍历关系表达式树，收集查询信息
    arrowImplementor.visitInput(0, getInput());
    // 创建物理类型对象，描述输出行的 Java 类型和格式偏好
    PhysType physType =  // 物理类型变量
        PhysTypeImpl.of(  // 使用 PhysTypeImpl 创建物理类型
            implementor.getTypeFactory(),  // 使用实现器的类型工厂
            getRowType(),  // 获取当前节点的行类型
            pref.preferArray());  // 根据偏好设置是否使用数组类型

    // 从实现器中获取表对象，确保表不为 null
    final RelOptTable table = requireNonNull(arrowImplementor.table, "table");  // 获取表元数据
    // 获取表的字段总数，用于生成字段索引列表
    final int fieldCount = table.getRowType().getFieldCount();  // 字段数量
    // 返回实现结果，包含物理类型和生成的代码块
    return implementor.result(physType,  // 传入物理类型
        Blocks.toBlock(  // 将表达式转换为代码块
            Expressions.call(table.getExpression(ArrowTable.class),  // 创建方法调用表达式，调用 ArrowTable 的方法
                ArrowMethod.ARROW_QUERY.method, implementor.getRootExpression(),  // 调用 arrowQuery 方法，传入根表达式
                arrowImplementor.selectFields != null  // 判断是否有字段选择
                    ? Expressions.call(  // 如果有字段选择，创建字段索引列表
                        BuiltInMethod.IMMUTABLE_INT_LIST_COPY_OF.method,  // 调用 copyOf 方法创建不可变列表
                        Expressions.constant(  // 创建常量表达式
                            Ints.toArray(arrowImplementor.selectFields)))  // 将 Integer 集合转换为 int 数组
                    : Expressions.call(  // 如果没有字段选择，创建全字段索引列表
                        BuiltInMethod.IMMUTABLE_INT_LIST_IDENTITY.method,  // 调用 identity 方法创建 0 到 n-1 的索引列表
                        Expressions.constant(fieldCount)),  // 传入字段总数
                Expressions.constant(arrowImplementor.whereClause))));  // 传入过滤条件表达式
  }
}  // 类结束
