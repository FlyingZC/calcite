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
package org.apache.calcite.rel.logical; // 逻辑关系表达式包，包含逻辑层面的关系算子实现

import org.apache.calcite.plan.Convention; // 导入约定接口，定义关系表达式的调用约定
import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群，包含优化器的上下文信息
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合，定义关系表达式的物理属性
import org.apache.calcite.rel.RelCollationTraitDef; // 导入排序特征定义，用于定义排序属性
import org.apache.calcite.rel.RelCollations; // 导入排序工具类，提供创建和操作排序的方法
import org.apache.calcite.rel.RelInput; // 导入关系输入接口，用于反序列化关系表达式
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，所有关系表达式的基类
import org.apache.calcite.rel.RelShuttle; // 导入关系穿梭器接口，用于遍历和修改关系表达式树
import org.apache.calcite.rel.core.CorrelationId; // 导入关联标识符，用于标记相关子查询中的变量
import org.apache.calcite.rel.core.Project; // 导入投影关系表达式基类，提供投影功能的核心实现
import org.apache.calcite.rel.hint.RelHint; // 导入关系提示接口，用于向优化器提供提示信息
import org.apache.calcite.rel.metadata.RelMdCollation; // 导入排序元数据提供者，用于计算排序属性
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入元数据查询接口，用于查询关系表达式的元数据
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，定义关系表达式的数据类型
import org.apache.calcite.rex.RexNode; // 导入行表达式节点接口，表示行级别的表达式
import org.apache.calcite.rex.RexUtil; // 导入行表达式工具类，提供表达式操作的工具方法
import org.apache.calcite.sql.validate.SqlValidatorUtil; // 导入SQL验证工具类，提供SQL验证相关的工具方法
import org.apache.calcite.util.Util; // 导入通用工具类，提供各种通用工具方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类
import com.google.common.collect.ImmutableSet; // 导入Google Guava的不可变集合类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的值

import java.util.List; // 导入Java标准列表接口
import java.util.Set; // 导入Java标准集合接口

/**
 * LogicalProject类：逻辑投影关系表达式
 * 
 * 类作用说明：
 * LogicalProject是Calcite查询优化器中表示投影（Projection）操作的逻辑关系表达式节点。
 * 
 * 核心功能：
 * 1. 投影操作：从输入关系表达式中选择特定的列或计算新的列
 * 2. 表达式计算：对输入列应用各种表达式（如算术运算、函数调用等）
 * 3. 列重命名：为输出列指定新的名称
 * 4. 列重排序：改变输出列的顺序
 * 
 * 在SQL中的对应：
 * 对应SQL SELECT语句中的SELECT子句，例如：
 * SELECT emp_id, salary * 1.1 AS new_salary, dept_name
 * FROM employees
 * WHERE dept_id = 10
 * 
 * 这个例子中的SELECT部分就是一个投影操作，它：
 * - 选择emp_id列
 * - 计算salary * 1.1并命名为new_salary
 * - 选择dept_name列
 * 
 * 继承关系：
 * 继承自Project抽象基类，Project提供了投影操作的通用实现框架
 * 
 * 特点：
 * - 不绑定到任何特定的执行引擎或调用约定（Convention.NONE）
 * - 是逻辑层面的关系表达式，不包含物理执行细节
 * - 在查询优化过程中会被转换为物理投影算子
 * 
 * 成员变量（继承自Project基类）：
 * - input: RelNode类型，输入的关系表达式节点
 * - projects: List<RexNode>类型，投影表达式列表，每个表达式对应一个输出列
 * - rowType: RelDataType类型，输出行的数据类型，包含所有输出列的类型信息
 * - variablesSet: Set<CorrelationId>类型，相关变量集合，用于处理相关子查询
 * - hints: List<RelHint>类型，提示列表，用于向优化器提供优化建议
 * 
 * 典型使用场景：
 * 1. 查询重写：在优化过程中合并多个投影操作
 * 2. 表达式下推：将投影表达式下推到数据源
 * 3. 列裁剪：移除不需要的列以减少数据传输
 * 4. 表达式简化：简化复杂的投影表达式
 * 
 * Sub-class of {@link org.apache.calcite.rel.core.Project} not
 * targeted at any particular engine or calling convention.
 */
public final class LogicalProject extends Project { // 声明LogicalProject类，继承自Project基类，final表示不能被继承
  //~ Constructors -----------------------------------------------------------

  /**
   * 主构造方法：创建LogicalProject实例
   *
   * <p>Use {@link #create} unless you know what you're doing.
   * 除非你清楚自己在做什么，否则应该使用静态工厂方法create()来创建实例
   *
   * 参数详细说明：
   * @param cluster  Cluster this relational expression belongs to
   *                 关系优化集群，包含优化器的上下文信息（如类型工厂、元数据查询等）
   *                 每个关系表达式都属于一个特定的RelOptCluster
   * 
   * @param traitSet Traits of this relational expression
   *                 关系特征集合，定义此关系表达式的物理属性
   *                 包含约定（Convention）、排序（Collation）、分区（Distribution）等特征
   *                 对于LogicalProject，必须包含Convention.NONE（无特定调用约定）
   * 
   * @param hints    Hints of this relational expression
   *                 提示列表，用于向优化器提供优化建议
   *                 例如：提示优化器使用特定的索引或执行策略
   *                 可以为空列表，表示没有提示
   * 
   * @param input    Input relational expression
   *                 输入的关系表达式节点，通常是表扫描、过滤、连接等操作
   *                 投影操作会从输入中读取数据并应用投影表达式
   *                 例如：输入可能是一个LogicalFilter，表示经过过滤的数据
   * 
   * @param projects List of expressions for the input columns
   *                 投影表达式列表，每个表达式对应一个输出列
   *                 表达式可以是简单的列引用（如$0表示第一列）
   *                 也可以是复杂的表达式（如salary * 1.1, UPPER(name)等）
   *                 列表中的表达式顺序决定了输出列的顺序
   *                 例如：[$0, $1 * 1.1, UPPER($2)]表示输出三列
   * 
   * @param rowType  Output row type
   *                 输出行的数据类型，包含所有输出列的名称和类型信息
   *                 必须与projects列表中的表达式数量和类型匹配
   *                 例如：StructType{emp_id:INTEGER, new_salary:DECIMAL, dept_name:VARCHAR}
   * 
   * @param variablesSet Correlation variables set by this relational expression
   *                     to be used by nested expressions
   *                     相关变量集合，用于处理相关子查询
   *                     如果此投影操作设置了相关变量（如EXISTS子查询中使用的变量），
   *                     这些变量可以在嵌套的表达式中引用
   *                     通常为空集合，除非处理相关子查询
   * 
   * 构造方法执行流程：
   * 1. 调用父类Project的构造方法，初始化所有成员变量
   * 2. 断言traitSet包含Convention.NONE（如果适用），确保这是逻辑投影
   * 
   * 使用示例：
   * RelOptCluster cluster = input.getCluster();
   * RelTraitSet traitSet = cluster.traitSetOf(Convention.NONE);
   * List<RexNode> projects = ImmutableList.of(rexBuilder.makeInputRef(input, 0));
   * RelDataType rowType = cluster.getTypeFactory().createStructType(...);
   * LogicalProject project = new LogicalProject(cluster, traitSet, hints, input, projects, rowType, variablesSet);
   */
  public LogicalProject(
      RelOptCluster cluster, // 关系优化集群参数
      RelTraitSet traitSet, // 关系特征集合参数
      List<RelHint> hints, // 提示列表参数
      RelNode input, // 输入关系表达式参数
      List<? extends RexNode> projects, // 投影表达式列表参数
      RelDataType rowType, // 输出行类型参数
      Set<CorrelationId> variablesSet) { // 相关变量集合参数
    super(cluster, traitSet, hints, input, projects, rowType, variablesSet); // 调用父类Project的构造方法初始化所有成员变量
    assert traitSet.containsIfApplicable(Convention.NONE); // 断言特征集合包含Convention.NONE（如果适用），确保这是逻辑投影而非物理投影
  }

  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  /**
   * 废弃的构造方法：不包含variablesSet参数的版本
   * 
   * 废弃原因：
   * 为了统一接口，所有构造方法都应该包含variablesSet参数
   * 建议使用包含所有参数的主构造方法
   * 
   * @param cluster 关系优化集群
   * @param traitSet 关系特征集合
   * @param hints 提示列表
   * @param input 输入关系表达式
   * @param projects 投影表达式列表
   * @param rowType 输出行类型
   * 
   * 注意：此方法内部调用主构造方法，并将variablesSet设为空集合
   */
  public LogicalProject(
      RelOptCluster cluster, // 关系优化集群参数
      RelTraitSet traitSet, // 关系特征集合参数
      List<RelHint> hints, // 提示列表参数
      RelNode input, // 输入关系表达式参数
      List<? extends RexNode> projects, // 投影表达式列表参数
      RelDataType rowType) { // 输出行类型参数
    this(cluster, traitSet, hints, input, projects, rowType, ImmutableSet.of()); // 调用主构造方法，variablesSet设为空集合
  }

  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  /**
   * 废弃的构造方法：不包含hints和variablesSet参数的版本
   * 
   * 废弃原因：
   * 为了统一接口，所有构造方法都应该包含hints和variablesSet参数
   * 建议使用包含所有参数的主构造方法
   * 
   * @param cluster 关系优化集群
   * @param traitSet 关系特征集合
   * @param input 输入关系表达式
   * @param projects 投影表达式列表
   * @param rowType 输出行类型
   * 
   * 注意：此方法内部调用主构造方法，hints设为空列表，variablesSet设为空集合
   */
  public LogicalProject(RelOptCluster cluster, RelTraitSet traitSet, // 关系优化集群和特征集合参数
      RelNode input, // 输入关系表达式参数
      List<? extends RexNode> projects, // 投影表达式列表参数
      RelDataType rowType) { // 输出行类型参数
    this(cluster, traitSet, ImmutableList.of(), input, projects, rowType, ImmutableSet.of()); // 调用主构造方法，hints设为空列表，variablesSet设为空集合
  }

  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  /**
   * 废弃的构造方法：包含flags参数的版本
   * 
   * 废弃原因：
   * flags参数不再使用，已被移除
   * 建议使用包含所有参数的主构造方法
   * 
   * @param cluster 关系优化集群
   * @param traitSet 关系特征集合
   * @param input 输入关系表达式
   * @param projects 投影表达式列表
   * @param rowType 输出行类型
   * @param flags 标志位（已废弃，不再使用）
   * 
   * 注意：此方法内部调用主构造方法，hints和variablesSet都设为空集合，flags参数被忽略
   */
  public LogicalProject(RelOptCluster cluster, RelTraitSet traitSet, // 关系优化集群和特征集合参数
      RelNode input, // 输入关系表达式参数
      List<? extends RexNode> projects, // 投影表达式列表参数
      RelDataType rowType, // 输出行类型参数
      int flags) { // 标志位参数（已废弃，不再使用）
    this(cluster, traitSet, ImmutableList.of(), input, projects, rowType, ImmutableSet.of()); // 调用主构造方法，hints和variablesSet都设为空集合
    Util.discard(flags); // 忽略flags参数，避免编译器警告
  }

  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  /**
   * 废弃的构造方法：使用fieldNames而非rowType的版本
   * 
   * 废弃原因：
   * 为了统一接口，应该直接使用rowType参数
   * 建议使用包含所有参数的主构造方法或create工厂方法
   * 
   * @param cluster 关系优化集群
   * @param input 输入关系表达式
   * @param projects 投影表达式列表
   * @param fieldNames 字段名称列表，用于生成rowType
   * @param flags 标志位（已废弃，不再使用）
   * 
   * 注意：此方法会根据fieldNames自动创建rowType，flags参数被忽略
   */
  public LogicalProject(RelOptCluster cluster, RelNode input, // 关系优化集群和输入关系表达式参数
      List<RexNode> projects, // 投影表达式列表参数
      @Nullable List<? extends @Nullable String> fieldNames, // 字段名称列表（可为null）
      int flags) { // 标志位参数（已废弃，不再使用）
    this(cluster, cluster.traitSetOf(RelCollations.EMPTY), // 调用主构造方法，使用空排序特征
        ImmutableList.of(), input, projects, // hints设为空列表
        RexUtil.createStructType(cluster.getTypeFactory(), projects, // 根据projects和fieldNames创建rowType
            fieldNames, null), ImmutableSet.of()); // variablesSet设为空集合
    Util.discard(flags); // 忽略flags参数，避免编译器警告
  }

  /**
   * 反序列化构造方法：从序列化输出创建LogicalProject
   * 
   * 用途说明：
   * 此构造方法用于从JSON或其他序列化格式反序列化LogicalProject对象
   * 通常用于跨进程传输、持久化存储或从文件加载查询计划
   * 
   * @param input 关系输入对象，包含序列化的LogicalProject信息
   *               RelInput封装了从序列化格式解析出的所有属性：
   *               - cluster: 关系优化集群
   *               - traitSet: 关系特征集合
   *               - hints: 提示列表
   *               - input: 输入关系表达式
   *               - projects: 投影表达式列表
   *               - rowType: 输出行类型
   *               - variablesSet: 相关变量集合
   * 
   * 执行流程：
   * 1. 调用父类Project的反序列化构造方法
   * 2. 父类会从RelInput对象中提取所有属性并初始化成员变量
   * 
   * 使用场景：
   * - 从JSON文件加载查询计划
   * - 跨进程传输关系表达式树
   * - 持久化查询计划到数据库
   * - 分布式查询优化器之间的计划交换
   */
  public LogicalProject(RelInput input) { // 关系输入对象，包含序列化的LogicalProject信息
    super(input); // 调用父类Project的反序列化构造方法，从RelInput中提取属性并初始化成员变量
  }

  //~ Methods ----------------------------------------------------------------

  /**
   * 静态工厂方法：创建LogicalProject实例（使用fieldNames）
   * 
   * 方法作用：
   * 提供便捷的方式创建LogicalProject实例，自动根据fieldNames生成rowType
   * 这是创建LogicalProject的推荐方式，比直接使用构造方法更安全
   * 
   * @param input 输入关系表达式，投影操作的数据源
   * @param hints 提示列表，用于向优化器提供优化建议
   * @param projects 投影表达式列表，每个表达式对应一个输出列
   * @param fieldNames 字段名称列表，用于命名输出列
   *                  如果为null，则使用默认的列名（如EXPR$0, EXPR$1等）
   *                  如果列表中的某个元素为null，则使用默认列名
   * 
   * @return 创建的LogicalProject实例
   * 
   * @deprecated Use {@link #create(RelNode, List, List, List, Set)} instead
   * 废弃原因：建议使用包含variablesSet参数的create方法
   */
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public static LogicalProject create(final RelNode input, List<RelHint> hints, // 输入关系表达式和提示列表参数
      final List<? extends RexNode> projects, // 投影表达式列表参数
      @Nullable List<? extends @Nullable String> fieldNames) { // 字段名称列表参数（可为null）
    return create(input, hints, projects, fieldNames, ImmutableSet.of()); // 调用包含variablesSet参数的create方法，variablesSet设为空集合
  }

  /**
   * 静态工厂方法：创建LogicalProject实例（包含variablesSet）
   * 
   * 方法作用：
   * 创建LogicalProject实例的推荐方法，自动根据fieldNames生成rowType
   * 此方法会自动计算排序特征（Collation），优化查询性能
   * 
   * 参数详细说明：
   * @param input 输入关系表达式，投影操作的数据源
   *               可以是任何RelNode，如表扫描、过滤、连接等
   * 
   * @param hints 提示列表，用于向优化器提供优化建议
   *               例如：提示使用特定的索引或执行策略
   *               可以为空列表
   * 
   * @param projects 投影表达式列表，每个表达式对应一个输出列
   *                 表达式可以是：
   *                 - 简单的列引用：$0, $1, $2等
   *                 - 算术表达式：salary * 1.1, price + tax等
   *                 - 函数调用：UPPER(name), CONCAT(first, last)等
   *                 - 字面量：'constant', 123, true等
   * 
   * @param fieldNames 字段名称列表，用于命名输出列
   *                  如果为null，则使用默认的列名（如EXPR$0, EXPR$1等）
   *                  如果列表中的某个元素为null，则使用默认列名
   *                  例如：["emp_id", "new_salary", "dept_name"]
   * 
   * @param variablesSet 相关变量集合，用于处理相关子查询
   *                     如果此投影操作设置了相关变量（如EXISTS子查询中使用的变量），
   *                     这些变量可以在嵌套的表达式中引用
   *                     通常为空集合，除非处理相关子查询
   * 
   * @return 创建的LogicalProject实例
   * 
   * 执行流程：
   * 1. 从输入节点获取关系优化集群
   * 2. 根据projects和fieldNames创建输出行类型（rowType）
   * 3. 调用另一个create方法，传入rowType
   * 4. 该方法会自动计算排序特征并创建LogicalProject实例
   * 
   * 使用示例：
   * List<RexNode> projects = ImmutableList.of(
   *     rexBuilder.makeInputRef(input, 0),  // emp_id
   *     rexBuilder.makeCall(SqlStdOperatorTable.MULTIPLY,
   *         rexBuilder.makeInputRef(input, 1),  // salary
   *         rexBuilder.makeExactLiteral(BigDecimal.valueOf(1.1))),  // 1.1
   *     rexBuilder.makeInputRef(input, 2)  // dept_name
   * );
   * List<String> fieldNames = ImmutableList.of("emp_id", "new_salary", "dept_name");
   * LogicalProject project = LogicalProject.create(input, hints, projects, fieldNames, variablesSet);
   */
  public static LogicalProject create(final RelNode input, List<RelHint> hints, // 输入关系表达式和提示列表参数
      final List<? extends RexNode> projects, // 投影表达式列表参数
      @Nullable List<? extends @Nullable String> fieldNames, // 字段名称列表参数（可为null）
      final Set<CorrelationId> variablesSet) { // 相关变量集合参数
    final RelOptCluster cluster = input.getCluster(); // 从输入节点获取关系优化集群
    final RelDataType rowType = // 创建输出行类型变量
        RexUtil.createStructType(cluster.getTypeFactory(), projects, // 根据projects和fieldNames创建结构类型
            fieldNames, SqlValidatorUtil.F_SUGGESTER); // 使用字段名建议器处理字段名
    return create(input, hints, projects, rowType, variablesSet); // 调用包含rowType参数的create方法
  }

  /**
   * 静态工厂方法：创建LogicalProject实例（使用rowType，不包含variablesSet）
   * 
   * 方法作用：
   * 提供便捷的方式创建LogicalProject实例，直接使用提供的rowType
   * 此方法会自动计算排序特征（Collation），优化查询性能
   * 
   * @param input 输入关系表达式，投影操作的数据源
   * @param hints 提示列表，用于向优化器提供优化建议
   * @param projects 投影表达式列表，每个表达式对应一个输出列
   * @param rowType 输出行类型，包含所有输出列的名称和类型信息
   *               必须与projects列表中的表达式数量和类型匹配
   * 
   * @return 创建的LogicalProject实例
   * 
   * @deprecated Use {@link #create(RelNode, List, List, RelDataType, Set)} instead
   * 废弃原因：建议使用包含variablesSet参数的create方法
   */
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public static LogicalProject create(final RelNode input, List<RelHint> hints, // 输入关系表达式和提示列表参数
      final List<? extends RexNode> projects, // 投影表达式列表参数
      RelDataType rowType) { // 输出行类型参数
    return create(input, hints, projects, rowType, ImmutableSet.of()); // 调用包含variablesSet参数的create方法，variablesSet设为空集合
  }

  /**
   * 静态工厂方法：创建LogicalProject实例（推荐使用）
   * 
   * 方法作用：
   * 创建LogicalProject实例的最完整和推荐的方法
   * 此方法会自动计算排序特征（Collation），优化查询性能
   * 
   * 参数详细说明：
   * @param input 输入关系表达式，投影操作的数据源
   *               可以是任何RelNode，如表扫描、过滤、连接等
   * 
   * @param hints 提示列表，用于向优化器提供优化建议
   *               例如：提示使用特定的索引或执行策略
   *               可以为空列表
   * 
   * @param projects 投影表达式列表，每个表达式对应一个输出列
   *                 表达式可以是：
   *                 - 简单的列引用：$0, $1, $2等
   *                 - 算术表达式：salary * 1.1, price + tax等
   *                 - 函数调用：UPPER(name), CONCAT(first, last)等
   *                 - 字面量：'constant', 123, true等
   * 
   * @param rowType 输出行类型，包含所有输出列的名称和类型信息
   *               必须与projects列表中的表达式数量和类型匹配
   *               例如：StructType{emp_id:INTEGER, new_salary:DECIMAL, dept_name:VARCHAR}
   * 
   * @param variablesSet 相关变量集合，用于处理相关子查询
   *                     如果此投影操作设置了相关变量（如EXISTS子查询中使用的变量），
   *                     这些变量可以在嵌套的表达式中引用
   *                     通常为空集合，除非处理相关子查询
   * 
   * @return 创建的LogicalProject实例
   * 
   * 执行流程：
   * 1. 从输入节点获取关系优化集群
   * 2. 获取元数据查询对象，用于查询元数据
   * 3. 创建特征集合：
   *    a. 设置约定为Convention.NONE（逻辑投影）
   *    b. 如果可能，计算并设置排序特征（Collation）
   *       - 使用RelMdCollation.project方法计算投影操作的排序特征
   *       - 如果投影保留了输入的排序，则设置相应的排序特征
   *       - 如果投影改变了排序，则可能不设置排序特征
   * 4. 使用计算出的特征集合创建LogicalProject实例
   * 
   * 排序特征计算说明：
   * RelMdCollation.project方法会分析投影表达式，判断是否保留了输入的排序
   * 例如：
   * - 如果输入按col1排序，投影只选择col1，则保留排序
   * - 如果输入按col1排序，投影选择col2，则不保留排序
   * - 如果输入按col1排序，投影选择col1和col2，则保留col1的排序
   * 
   * 使用示例：
   * List<RexNode> projects = ImmutableList.of(
   *     rexBuilder.makeInputRef(input, 0),  // emp_id
   *     rexBuilder.makeCall(SqlStdOperatorTable.MULTIPLY,
   *         rexBuilder.makeInputRef(input, 1),  // salary
   *         rexBuilder.makeExactLiteral(BigDecimal.valueOf(1.1)))  // 1.1
   * );
   * RelDataType rowType = typeFactory.createStructType(
   *     ImmutableList.of(typeFactory.createSqlType(SqlTypeName.INTEGER),
   *                      typeFactory.createSqlType(SqlTypeName.DECIMAL)),
   *     ImmutableList.of("emp_id", "new_salary")
   * );
   * LogicalProject project = LogicalProject.create(input, hints, projects, rowType, variablesSet);
   */
  public static LogicalProject create(final RelNode input, List<RelHint> hints, // 输入关系表达式和提示列表参数
      final List<? extends RexNode> projects, // 投影表达式列表参数
      RelDataType rowType, // 输出行类型参数
      final Set<CorrelationId> variablesSet) { // 相关变量集合参数
    final RelOptCluster cluster = input.getCluster(); // 从输入节点获取关系优化集群
    final RelMetadataQuery mq = cluster.getMetadataQuery(); // 获取元数据查询对象，用于查询元数据
    final RelTraitSet traitSet = // 创建关系特征集合变量
        cluster.traitSet().replace(Convention.NONE) // 设置约定为Convention.NONE（逻辑投影）
            .replaceIfs(RelCollationTraitDef.INSTANCE, // 如果可能，替换排序特征
                () -> RelMdCollation.project(mq, input, projects)); // 使用元数据计算投影操作的排序特征
    return new LogicalProject(cluster, traitSet, hints, input, projects, rowType, variablesSet); // 创建并返回LogicalProject实例
  }

  /**
   * 复制方法：创建此LogicalProject的副本
   * 
   * 方法作用：
   * 创建一个新的LogicalProject实例，可以修改某些属性
   * 这是关系表达式树变换的核心方法，用于在优化过程中创建新的节点
   * 
   * @Override 覆盖父类Project的copy方法
   * 
   * 参数详细说明：
   * @param traitSet 新的关系特征集合，可以与原始特征集合不同
   *                 例如：可以修改排序特征、分区特征等
   *                 如果为null，则使用原始特征集合
   * 
   * @param input 新的输入关系表达式，可以与原始输入不同
   *              例如：可以将投影操作应用在不同的输入上
   *              如果为null，则使用原始输入
   * 
   * @param projects 新的投影表达式列表，可以与原始表达式列表不同
   *                 例如：可以添加、删除或修改投影表达式
   *                 如果为null，则使用原始表达式列表
   * 
   * @param rowType 新的输出行类型，可以与原始行类型不同
   *               例如：可以修改列名或列类型
   *               必须与projects列表中的表达式数量和类型匹配
   *               如果为null，则使用原始行类型
   * 
   * @return 新创建的LogicalProject实例
   * 
   * 执行流程：
   * 1. 获取当前节点的集群（getCluster()）
   * 2. 使用当前节点的提示（hints）和相关变量集合（variablesSet）
   * 3. 使用传入的traitSet、input、projects、rowType创建新的LogicalProject实例
   * 
   * 使用场景：
   * 1. 查询优化：在优化过程中创建新的投影节点
   * 2. 表达式重写：修改投影表达式后创建新节点
   * 3. 特征修改：改变排序特征、分区特征等
   * 4. 输入替换：将投影操作应用在不同的输入上
   * 
   * 使用示例：
   * // 创建一个只选择前两列的新投影
   * List<RexNode> newProjects = projects.subList(0, 2);
   * RelDataType newRowType = ...; // 创建对应的行类型
   * LogicalProject newProject = project.copy(project.traitSet, project.input, newProjects, newRowType);
   * 
   * // 创建一个修改排序特征的新投影
   * RelTraitSet newTraitSet = project.traitSet.replace(RelCollations.of(0)); // 按第一列排序
   * LogicalProject newProject = project.copy(newTraitSet, project.input, project.projects, project.rowType);
   */
  @Override public LogicalProject copy(RelTraitSet traitSet, RelNode input, // 覆盖父类copy方法，接收新的特征集合和输入
      List<RexNode> projects, RelDataType rowType) { // 接收新的投影表达式列表和行类型
    return new LogicalProject(getCluster(), traitSet, hints, input, projects, rowType, // 创建新的LogicalProject实例，使用当前集群、提示和相关变量集合
        variablesSet); // 使用当前的相关变量集合
  }

  /**
   * 接受方法：接受关系穿梭器（RelShuttle）访问
   * 
   * 方法作用：
   * 实现访问者模式，允许RelShuttle遍历和修改关系表达式树
   * 这是关系表达式树遍历和变换的核心方法
   * 
   * @Override 覆盖父类Project的accept方法
   * 
   * @param shuttle 关系穿梭器，用于遍历和修改关系表达式树
   *                RelShuttle可以：
   *                - 遍历关系表达式树
   *                - 修改关系表达式树中的节点
   *                - 收集关系表达式树的信息
   *                - 验证关系表达式树的正确性
   * 
   * @return 穿梭器访问后的关系表达式节点
   *         如果穿梭器修改了此节点，则返回修改后的节点
   *         如果穿梭器没有修改此节点，则返回此节点本身
   * 
   * 执行流程：
   * 1. 调用shuttle的visit方法，传入this（当前LogicalProject节点）
   * 2. shuttle会根据其实现决定如何处理此节点：
   *    - 可能会递归访问子节点（input）
   *    - 可能会修改此节点的属性
   *    - 可能会替换此节点为其他节点
   *    - 可能会收集此节点的信息
   * 3. 返回处理后的结果
   * 
   * 使用场景：
   * 1. 查询重写：使用RelShuttle重写查询计划
   * 2. 表达式收集：使用RelShuttle收集表达式信息
   * 3. 节点替换：使用RelShuttle替换特定节点
   * 4. 树遍历：使用RelShuttle遍历关系表达式树
   * 
   * 使用示例：
   * // 创建一个收集所有投影表达式的穿梭器
   * class ProjectCollector extends RelShuttleImpl {
   *     final List<RexNode> projects = new ArrayList<>();
   *     public RelNode visit(LogicalProject project) {
   *         projects.addAll(project.getProjects());
   *         return super.visit(project);
   *     }
   * }
   * ProjectCollector collector = new ProjectCollector();
   * RelNode result = project.accept(collector);
   * 
   * // 创建一个重写投影表达式的穿梭器
   * class ProjectRewriter extends RelShuttleImpl {
   *     public RelNode visit(LogicalProject project) {
   *         List<RexNode> newProjects = rewriteProjects(project.getProjects());
   *         return project.copy(project.traitSet, project.input, newProjects, project.rowType);
   *     }
   * }
   */
  @Override public RelNode accept(RelShuttle shuttle) { // 覆盖父类accept方法，接收关系穿梭器参数
    return shuttle.visit(this); // 调用穿梭器的visit方法，传入当前节点，返回处理后的结果
  }

  /**
   * 设置提示方法：创建具有新提示列表的LogicalProject副本
   * 
   * 方法作用：
   * 创建一个新的LogicalProject实例，使用新的提示列表
   * 其他属性保持不变
   * 
   * @Override 覆盖父类Project的withHints方法
   * 
   * @param hintList 新的提示列表，用于向优化器提供优化建议
   *                 例如：提示使用特定的索引或执行策略
   *                 可以为空列表，表示没有提示
   * 
   * @return 具有新提示列表的LogicalProject实例
   * 
   * 执行流程：
   * 1. 获取当前节点的集群（getCluster()）
   * 2. 使用当前节点的特征集合（traitSet）
   * 3. 使用传入的hintList作为新的提示列表
   * 4. 使用当前节点的输入（input）、投影表达式（getProjects()）、行类型（getRowType()）和相关变量集合（variablesSet）
   * 5. 创建新的LogicalProject实例
   * 
   * 使用场景：
   * 1. 添加提示：向现有投影节点添加优化提示
   * 2. 移除提示：从现有投影节点移除优化提示
   * 3. 修改提示：修改现有投影节点的优化提示
   * 
   * 使用示例：
   * // 添加一个提示
   * List<RelHint> newHints = ImmutableList.of(
   *     RelHint.builder("index_hint").hintOption("use_index", "idx_emp_id").build()
   * );
   * LogicalProject newProject = project.withHints(newHints);
   * 
   * // 清除所有提示
   * LogicalProject newProject = project.withHints(ImmutableList.of());
   */
  @Override public RelNode withHints(List<RelHint> hintList) { // 覆盖父类withHints方法，接收新的提示列表参数
    return new LogicalProject(getCluster(), traitSet, hintList, // 创建新的LogicalProject实例，使用当前集群、特征集合和新提示列表
        input, getProjects(), getRowType(), variablesSet); // 使用当前输入、投影表达式、行类型和相关变量集合
  }

  /**
   * 深度相等比较方法：比较此LogicalProject与另一个对象是否深度相等
   * 
   * 方法作用：
   * 比较两个LogicalProject实例的所有属性是否相等
   * 包括：输入节点、投影表达式、行类型、相关变量集合、提示等
   * 
   * @Override 覆盖父类Project的deepEquals方法
   * 
   * @param obj 要比较的对象，可以是任何对象
   *            如果为null，则返回false
   *            如果不是LogicalProject实例，则返回false
   * 
   * @return 如果两个LogicalProject实例的所有属性都相等，则返回true
   *         否则返回false
   * 
   * 执行流程：
   * 1. 调用父类的deepEquals0方法进行比较
   * 2. deepEquals0方法会比较：
   *    - 对象类型是否相同
   *    - 输入节点是否相同（使用deepEquals递归比较）
   *    - 投影表达式列表是否相同（逐个比较）
   *    - 行类型是否相同
   *    - 相关变量集合是否相同
   *    - 提示列表是否相同
   * 
   * 使用场景：
   * 1. 测试断言：在单元测试中验证两个投影节点是否相等
   * 2. 缓存键值：使用投影节点作为缓存的键
   * 3. 重复检测：检测查询计划中是否有重复的投影节点
   * 
   * 使用示例：
   * LogicalProject project1 = LogicalProject.create(input, hints, projects, rowType, variablesSet);
   * LogicalProject project2 = LogicalProject.create(input, hints, projects, rowType, variablesSet);
   * assertTrue(project1.deepEquals(project2)); // 应该返回true
   */
  @Override public boolean deepEquals(@Nullable Object obj) { // 覆盖父类deepEquals方法，接收要比较的对象参数
    return deepEquals0(obj); // 调用父类的deepEquals0方法进行深度比较，返回比较结果
  }

  /**
   * 深度哈希码方法：计算此LogicalProject的深度哈希码
   * 
   * 方法作用：
   * 计算LogicalProject实例的哈希码，基于所有属性
   * 包括：输入节点、投影表达式、行类型、相关变量集合、提示等
   * 
   * @Override 覆盖父类Project的deepHashCode方法
   * 
   * @return 基于所有属性的哈希码值
   * 
   * 执行流程：
   * 1. 调用父类的deepHashCode0方法计算哈希码
   * 2. deepHashCode0方法会基于以下属性计算哈希码：
   *    - 输入节点的哈希码（使用deepHashCode递归计算）
   *    - 投影表达式列表的哈希码（逐个计算并组合）
   *    - 行类型的哈希码
   *    - 相关变量集合的哈希码
   *    - 提示列表的哈希码
   * 
   * 注意事项：
   * - 如果两个对象deepEquals返回true，则它们的deepHashCode必须相同
   * - 如果两个对象的deepHashCode不同，则它们的deepEquals必须返回false
   * - 哈希码应该尽可能均匀分布，以避免哈希冲突
   * 
   * 使用场景：
   * 1. 哈希表键值：将投影节点作为哈希表的键
   * 2. 缓存键值：使用投影节点作为缓存的键
   * 3. 快速比较：通过哈希码快速判断两个节点是否可能相等
   * 
   * 使用示例：
   * Map<LogicalProject, String> cache = new HashMap<>();
   * cache.put(project1, "value1");
   * String value = cache.get(project2); // 如果project1和project2相等，则能获取到"value1"
   */
  @Override public int deepHashCode() { // 覆盖父类deepHashCode方法
    return deepHashCode0(); // 调用父类的deepHashCode0方法计算深度哈希码，返回哈希码值
  }
} // LogicalProject类定义结束
