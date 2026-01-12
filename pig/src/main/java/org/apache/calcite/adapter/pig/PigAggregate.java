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
// Apache Calcite 是一个动态数据管理框架，提供 SQL 解析、优化、执行等功能
// Pig 是 Apache 的一个数据分析平台，使用类似 SQL 的 Pig Latin 语言进行数据处理
// 本包实现了 Calcite 到 Pig 的适配器，将 SQL 查询转换为 Pig Latin 脚本
package org.apache.calcite.adapter.pig; // 声明包名，表示此类属于 Pig 适配器包

// 导入 Calcite 核心类：RelOptCluster 表示关系代数优化器的集群，包含所有共享资源
import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群类，用于管理优化过程中的共享资源
// 导入 RelOptTable 表示关系表，包含表的元数据信息
import org.apache.calcite.plan.RelOptTable; // 导入关系优化表类，提供表的元数据和统计信息
// 导入 RelTraitSet 表示关系特征的集合，如物理实现方式、排序规则等
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合类，定义关系节点的物理属性
// 导入 RelNode 表示关系代数节点，是所有关系操作符的基类
import org.apache.calcite.rel.RelNode; // 导入关系节点基类，所有关系操作符都继承此类
// 导入 Aggregate 表示聚合操作符，实现 GROUP BY、聚合函数等功能
import org.apache.calcite.rel.core.Aggregate; // 导入聚合操作符类，实现 SQL 聚合功能
// 导入 AggregateCall 表示聚合函数调用，包含聚合函数名、参数、是否去重等信息
import org.apache.calcite.rel.core.AggregateCall; // 导入聚合调用类，描述单个聚合函数的调用信息
// 导入 RelDataTypeField 表示关系数据类型的字段，包含字段名和类型信息
import org.apache.calcite.rel.type.RelDataTypeField; // 导入关系数据类型字段类，描述字段的元数据
// 导入 ImmutableBitSet 表示不可变的位集合，用于高效地表示字段索引集合
import org.apache.calcite.util.ImmutableBitSet; // 导入不可变位集合类，用于存储和操作字段索引集合

// 导入 Pig 脚本类，用于执行 Pig Latin 脚本
import org.apache.pig.scripting.Pig; // 导入 Pig 脚本类，提供 Pig Latin 脚本的执行能力

// 导入 ImmutableList 表示不可变的列表，提供线程安全的列表操作
import com.google.common.collect.ImmutableList; // 导入不可变列表类，提供高效的不可变列表实现

// 导入 Java 集合框架类
import java.util.ArrayList; // 导入动态数组类，提供可变大小的数组实现
import java.util.HashSet; // 导入哈希集合类，提供基于哈希表的集合实现
import java.util.List; // 导入列表接口，定义有序集合的行为
import java.util.Set; // 导入集合接口，定义不重复元素集合的行为

/** Implementation of {@link org.apache.calcite.rel.core.Aggregate} in
 * {@link PigRel#CONVENTION Pig calling convention}. */
// PigAggregate 类是 Calcite 中 Aggregate 操作符在 Pig 调用约定下的实现
// 它将 SQL 的聚合操作（GROUP BY、SUM、COUNT 等）转换为 Pig Latin 脚本
// Pig 调用约定表示此操作符会被转换为 Pig Latin 代码执行，而不是传统的数据库执行引擎
// 此类继承自 Aggregate，复用了聚合操作的语义，但实现了 Pig 特定的代码生成逻辑
public class PigAggregate extends Aggregate implements PigRel { // 类声明，继承 Aggregate 聚合操作符，实现 PigRel 接口

  // 常量：用于标记 DISTINCT 聚合函数的字段后缀
  // 在 Pig 中，COUNT(DISTINCT col) 需要先执行 DISTINCT 操作，然后对结果进行 COUNT
  // 为了避免字段名冲突，会为 DISTINCT 操作的结果字段添加此后缀
  // 例如：COUNT(DISTINCT owner) 会生成 owner_DISTINCT = DISTINCT owner; COUNT(owner_DISTINCT)
  public static final String DISTINCT_FIELD_SUFFIX = "_DISTINCT"; // 定义 DISTINCT 字段后缀常量，值为 "_DISTINCT"

  /** Creates a PigAggregate. */
  // 构造方法：创建一个 PigAggregate 实例
  // 参数说明：
  //   - cluster: 关系优化集群，包含类型工厂、表达式构建器等共享资源
  //   - traitSet: 关系特征集合，定义此节点的物理属性（如调用约定为 PigRel.CONVENTION）
  //   - input: 输入关系节点，表示聚合操作的数据源
  //   - groupSet: 分组字段的位集合，指定哪些字段用于 GROUP BY
  //   - groupSets: 分组集合列表，支持 GROUPING SETS、ROLLUP、CUBE 等高级分组
  //   - aggCalls: 聚合函数调用列表，包含 SUM、COUNT、AVG 等聚合函数的定义
  // 此构造方法调用父类 Aggregate 的构造方法，传入空列表表示不使用 indicator 字段（已废弃）
  // 断言确保此节点的调用约定是 PigRel.CONVENTION，保证是 Pig 实现
  public PigAggregate(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法开始，接收集群、特征集、输入节点等参数
      RelNode input, ImmutableBitSet groupSet, // 接收输入节点和分组字段集合
      List<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls) { // 接收分组集合列表和聚合调用列表
    super(cluster, traitSet, ImmutableList.of(), input, groupSet, groupSets, aggCalls); // 调用父类构造方法，传入空列表作为 indicator 参数
    assert getConvention() == PigRel.CONVENTION; // 断言检查，确保调用约定是 PigRel.CONVENTION
  } // 构造方法结束

  @Deprecated // to be removed before 2.0
  // 已废弃的构造方法，包含 indicator 参数
  // indicator 参数曾用于标识分组字段是否为 null，用于 GROUPING SETS 实现
  // 此参数在 Calcite 2.0 版本前将被移除，现在使用 groupSets 参数替代
  // 此构造方法内部调用新的构造方法，并检查 indicator 参数必须为 false
  public PigAggregate(RelOptCluster cluster, RelTraitSet traitSet, // 废弃的构造方法开始
      RelNode input, boolean indicator, ImmutableBitSet groupSet, // 接收 indicator 参数（已废弃）
      List<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls) { // 接收分组集合和聚合调用列表
    this(cluster, traitSet, input, groupSet, groupSets, aggCalls); // 调用新的构造方法，忽略 indicator 参数
    checkIndicator(indicator); // 检查 indicator 参数必须为 false，确保向后兼容
  } // 废弃的构造方法结束

  // copy 方法：创建此节点的副本，可以修改部分参数
  // 这是 Calcite 关系代数节点的标准方法，用于优化器在重写规则中创建新的节点实例
  // 参数说明：
  //   - traitSet: 新的特征集合，可以修改调用约定、排序等属性
  //   - input: 新的输入节点，可以替换数据源
  //   - groupSet: 新的分组字段集合，可以修改分组字段
  //   - groupSets: 新的分组集合列表，可以修改高级分组
  //   - aggCalls: 新的聚合调用列表，可以修改聚合函数
  // 返回值：返回新的 PigAggregate 实例，包含修改后的参数
  @Override public Aggregate copy(RelTraitSet traitSet, RelNode input, // 重写 copy 方法，用于创建节点副本
      ImmutableBitSet groupSet, List<ImmutableBitSet> groupSets, // 接收新的分组字段集合和分组集合列表
      List<AggregateCall> aggCalls) { // 接收新的聚合调用列表
    return new PigAggregate(input.getCluster(), traitSet, input, groupSet, // 创建并返回新的 PigAggregate 实例
        groupSets, aggCalls); // 传入新的分组集合和聚合调用列表
  } // copy 方法结束

  // implement 方法：实现 Pig Latin 代码生成
  // 这是 PigRel 接口的核心方法，负责将关系节点转换为可执行的 Pig Latin 脚本
  // 参数说明：
  //   - implementor: Pig 实现器，维护 Pig Latin 脚本的生成状态和上下文
  // 实现逻辑：
  //   1. 首先访问子节点（输入关系），生成子节点的 Pig Latin 代码
  //   2. 然后生成当前聚合节点的 Pig Latin 语句（GROUP BY 和 FOREACH）
  //   3. 将生成的语句添加到实现器的语句列表中
  // 生成的 Pig Latin 代码示例：
  //   A = LOAD 'data' USING PigStorage(',');
  //   A = GROUP A BY owner;
  //   A = FOREACH A GENERATE group, SUM(A.pet_num);
  @Override public void implement(Implementor implementor) { // 重写 implement 方法，实现 Pig Latin 代码生成
    implementor.visitChild(0, getInput()); // 访问第一个子节点（输入关系），生成子节点的 Pig Latin 代码
    implementor.addStatement(getPigAggregateStatement(implementor)); // 生成聚合语句并添加到实现器
  } // implement 方法结束

  /**
   * Generates a GROUP BY statement, followed by an optional FOREACH statement
   * for all aggregate functions used. e.g.
   * <pre>
   * {@code
   * A = GROUP A BY owner;
   * A = FOREACH A GENERATE group, SUM(A.pet_num);
   * }
   * </pre>
   */
  // getPigAggregateStatement 方法：生成完整的 Pig 聚合语句
  // 此方法将 GROUP BY 语句和 FOREACH 语句组合在一起，形成完整的聚合操作
  // 参数说明：
  //   - implementor: Pig 实现器，提供关系别名等上下文信息
  // 返回值：返回完整的 Pig Latin 聚合语句字符串
  // 语句结构：
  //   1. GROUP BY 语句：按指定字段分组
  //   2. FOREACH 语句：生成分组字段和聚合函数的结果
  // 示例输出：
  //   A = GROUP A BY owner;
  //   A = FOREACH A {
  //     GENERATE group AS owner, SUM(A.pet_num) AS sum_pet_num;
  //   };
  private String getPigAggregateStatement(Implementor implementor) { // 生成完整的 Pig 聚合语句
    return getPigGroupBy(implementor) + '\n' + getPigForEachGenerate(implementor); // 组合 GROUP BY 语句和 FOREACH 语句
  } // getPigAggregateStatement 方法结束

  /**
   * Override this method so it looks down the tree to find the table this node
   * is acting on.
   */
  // getTable 方法：获取此节点操作的目标表
  // 重写父类方法，向下遍历关系树以找到实际的表
  // PigAggregate 本身不直接对应表，而是对输入数据进行聚合操作
  // 因此需要从输入节点获取表信息
  // 返回值：返回输入节点对应的表（RelOptTable）
  // 注意：此方法在 Pig 适配器中很重要，因为需要知道源表来生成正确的 Pig Latin 代码
  @Override public RelOptTable getTable() { // 重写 getTable 方法，获取目标表
    return getInput().getTable(); // 从输入节点获取表信息
  } // getTable 方法结束

  /**
   * Generates the GROUP BY statement, e.g.
   * <code>A = GROUP A BY (f1, f2);</code>
   */
  // getPigGroupBy 方法：生成 Pig GROUP BY 语句
  // 此方法根据 groupSet 生成分组语句，支持单字段和多字段分组
  // 参数说明：
  //   - implementor: Pig 实现器，提供关系别名等上下文信息
  // 返回值：返回 GROUP BY 语句字符串
  // 生成逻辑：
  //   1. 获取当前关系的别名（如 "A"）
  //   2. 获取输入关系的所有字段列表
  //   3. 获取分组字段的索引列表（从 groupSet 中提取）
  //   4. 如果没有分组字段（groupSet 为空），生成 "GROUP ALL" 语句（全局聚合）
  //   5. 如果有分组字段，生成 "GROUP BY (f1, f2, ...)" 语句
  // 示例输出：
  //   - 无分组：A = GROUP A ALL;
  //   - 单字段：A = GROUP A BY owner;
  //   - 多字段：A = GROUP A BY (owner, pet_type);
  private String getPigGroupBy(Implementor implementor) { // 生成 GROUP BY 语句
    final String relAlias = implementor.getPigRelationAlias(this); // 获取当前关系的别名（如 "A"）
    final List<RelDataTypeField> allFields = getInput().getRowType().getFieldList(); // 获取输入关系的所有字段列表
    final List<Integer> groupedFieldIndexes = groupSet.asList(); // 将分组字段的位集合转换为索引列表
    if (groupedFieldIndexes.size() < 1) { // 如果没有分组字段（列表为空）
      return relAlias + " = GROUP " + relAlias + " ALL;"; // 生成全局聚合语句，不分组
    } else { // 如果有分组字段
      final List<String> groupedFieldNames = new ArrayList<>(groupedFieldIndexes.size()); // 创建分组字段名列表，预分配大小
      for (int fieldIndex : groupedFieldIndexes) { // 遍历分组字段索引
        groupedFieldNames.add(allFields.get(fieldIndex).getName()); // 根据索引获取字段名，添加到列表中
      } // 遍历结束
      return relAlias + " = GROUP " + relAlias + " BY (" // 生成 GROUP BY 语句开始部分
          + String.join(", ", groupedFieldNames) + ");"; // 用逗号连接所有分组字段名，完成语句
    } // if-else 结束
  } // getPigGroupBy 方法结束

  /**
   * Generates a FOREACH statement containing invocation of aggregate functions
   * and projection of grouped fields. e.g.
   * <code>A = FOREACH A GENERATE group, SUM(A.pet_num);</code>
   *
   * @see Pig documentation for special meaning of the "group" field after GROUP
   *      BY.
   */
  // getPigForEachGenerate 方法：生成 Pig FOREACH 语句
  // 此方法生成 FOREACH 嵌套块，包含聚合函数调用和分组字段投影
  // 参数说明：
  //   - implementor: Pig 实现器，提供关系别名等上下文信息
  // 返回值：返回 FOREACH 语句字符串
  // 语句结构：
  //   1. FOREACH 关系别名 { ... }
  //   2. 在嵌套块中，先生成 DISTINCT 语句（如果需要）
  //   3. 然后生成 GENERATE 语句，输出分组字段和聚合结果
  // 关于 "group" 字段：
  //   在 Pig 中，GROUP BY 操作后，会自动生成一个名为 "group" 的字段
  //   - 单字段分组：group 字段直接包含分组值
  //   - 多字段分组：group 字段是一个元组，包含所有分组字段
  // 示例输出：
  //   A = FOREACH A {
  //     owner_DISTINCT = DISTINCT A.owner;
  //     GENERATE group AS owner, SUM(A.pet_num) AS sum_pet_num, COUNT(owner_DISTINCT) AS distinct_owner_count;
  //   };
  private String getPigForEachGenerate(Implementor implementor) { // 生成 FOREACH 语句
    final String relAlias = implementor.getPigRelationAlias(this); // 获取当前关系的别名
    final String generateCall = getPigGenerateCall(implementor); // 生成 GENERATE 子句
    final List<String> distinctCalls = getDistinctCalls(implementor); // 获取 DISTINCT 语句列表（如果需要）
    return relAlias + " = FOREACH " + relAlias + " {\n" // 生成 FOREACH 语句开始部分
        + String.join(";\n", distinctCalls) + generateCall + "\n};"; // 组合 DISTINCT 语句和 GENERATE 语句
  } // getPigForEachGenerate 方法结束

  // getPigGenerateCall 方法：生成 GENERATE 子句
  // 此方法生成 FOREACH 语句中的 GENERATE 部分，包含分组字段和聚合函数调用
  // 参数说明：
  //   - implementor: Pig 实现器，提供关系别名等上下文信息
  // 返回值：返回 GENERATE 子句字符串
  // 生成逻辑：
  //   1. 处理分组字段：
  //      - 单字段分组：使用 "group AS field_name"
  //      - 多字段分组：使用 "group.field_name AS field_name"
  //   2. 处理聚合函数调用：生成 "FUNC(relAlias.field) AS alias"
  //   3. 将所有字段用逗号连接
  // 示例输出：
  //   - 单字段：GENERATE group AS owner, SUM(A.pet_num) AS sum_pet;
  //   - 多字段：GENERATE group.owner AS owner, group.pet_type AS pet_type, COUNT(A.id) AS count_id;
  private String getPigGenerateCall(Implementor implementor) { // 生成 GENERATE 子句
    final List<Integer> groupedFieldIndexes = groupSet.asList(); // 获取分组字段索引列表
    Set<String> groupFields = new HashSet<>(groupedFieldIndexes.size()); // 创建分组字段集合，预分配大小
    for (int fieldIndex : groupedFieldIndexes) { // 遍历分组字段索引
      final String fieldName = getInputFieldName(fieldIndex); // 获取字段名
      // Pig appends group field name if grouping by multiple fields
      // 根据分组字段数量生成不同的 group 字段引用
      // 单字段分组：直接使用 "group"
      // 多字段分组：使用 "group.field_name" 访问元组中的字段
      final String groupField = (groupedFieldIndexes.size() == 1 ? "group" : ("group." + fieldName)) // 生成 group 字段引用
          + " AS " + fieldName; // 添加 AS 别名，保持原始字段名

      groupFields.add(groupField); // 将生成的 group 字段引用添加到集合中
    } // 遍历结束
    final List<String> pigAggCalls = getPigAggregateCalls(implementor); // 获取所有聚合函数调用字符串
    List<String> allFields = new ArrayList<>(groupFields.size() + pigAggCalls.size()); // 创建所有字段列表，预分配大小
    allFields.addAll(groupFields); // 添加分组字段
    allFields.addAll(pigAggCalls); // 添加聚合函数调用
    return "  GENERATE " + String.join(", ", allFields) + ';'; // 生成完整的 GENERATE 语句
  } // getPigGenerateCall 方法结束

  // getPigAggregateCalls 方法：获取所有聚合函数调用的字符串列表
  // 此方法遍历所有聚合调用，为每个调用生成对应的 Pig Latin 聚合函数字符串
  // 参数说明：
  //   - implementor: Pig 实现器，提供关系别名等上下文信息
  // 返回值：返回聚合函数调用字符串列表
  // 示例输出：["SUM(A.pet_num) AS sum_pet_num", "COUNT(A.id) AS count_id"]
  private List<String> getPigAggregateCalls(Implementor implementor) { // 获取聚合函数调用列表
    final String relAlias = implementor.getPigRelationAlias(this); // 获取当前关系的别名
    final List<String> result = new ArrayList<>(aggCalls.size()); // 创建结果列表，预分配大小为聚合调用数量
    for (AggregateCall ac : aggCalls) { // 遍历所有聚合调用
      result.add(getPigAggregateCall(relAlias, ac)); // 为每个聚合调用生成 Pig Latin 字符串并添加到结果
    } // 遍历结束
    return result; // 返回聚合函数调用列表
  } // getPigAggregateCalls 方法结束

  // getPigAggregateCall 方法：生成单个聚合函数调用的 Pig Latin 字符串
  // 此方法将 Calcite 的 AggregateCall 转换为 Pig Latin 的聚合函数调用
  // 参数说明：
  //   - relAlias: 关系别名（如 "A"）
  //   - aggCall: 聚合调用对象，包含聚合函数类型、参数、别名等信息
  // 返回值：返回 Pig Latin 聚合函数调用字符串
  // 生成逻辑：
  //   1. 将 Calcite 聚合函数转换为 Pig 聚合函数（SUM -> SUM, COUNT -> COUNT 等）
  //   2. 获取聚合函数的别名
  //   3. 获取聚合函数的参数字段名
  //   4. 组合为 "FUNC(arg1, arg2, ...) AS alias" 格式
  // 示例输出："SUM(A.pet_num) AS sum_pet_num"
  private String getPigAggregateCall(String relAlias, AggregateCall aggCall) { // 生成单个聚合函数调用
    final PigAggFunction aggFunc = toPigAggFunc(aggCall); // 将 Calcite 聚合函数转换为 Pig 聚合函数枚举
    final String alias = aggCall.getName(); // 获取聚合函数的别名（输出字段名）
    final String fields = String.join(", ", getArgNames(relAlias, aggCall)); // 获取所有参数字段名，用逗号连接
    return aggFunc.name() + "(" + fields + ") AS " + alias; // 组合为完整的 Pig Latin 聚合函数调用
  } // getPigAggregateCall 方法结束

  // toPigAggFunc 方法：将 Calcite 聚合函数转换为 Pig 聚合函数枚举
  // 此方法根据聚合函数的类型和参数数量，返回对应的 Pig 聚合函数枚举值
  // 参数说明：
  //   - aggCall: 聚合调用对象
  // 返回值：返回 PigAggFunction 枚举值
  // 转换逻辑：
  //   - 根据 aggCall.getAggregation().getKind() 获取聚合函数类型
  //   - 根据 aggCall.getArgList().size() 判断是否有参数（COUNT(*) 没有参数）
  //   - 返回对应的 PigAggFunction 枚举值
  private static PigAggFunction toPigAggFunc(AggregateCall aggCall) { // 转换为 Pig 聚合函数枚举
    return PigAggFunction.valueOf(aggCall.getAggregation().getKind(), // 根据聚合函数类型和参数数量获取枚举值
        aggCall.getArgList().size() < 1); // 如果参数列表为空，则为 COUNT(*) 类型
  } // toPigAggFunc 方法结束

  // getArgNames 方法：获取聚合函数的参数字段名列表
  // 此方法遍历聚合函数的所有参数，返回对应的 Pig Latin 字段引用
  // 参数说明：
  //   - relAlias: 关系别名（如 "A"）
  //   - aggCall: 聚合调用对象
  // 返回值：返回参数字段名列表
  // 示例输出：["A.pet_num", "A.age"]
  private List<String> getArgNames(String relAlias, AggregateCall aggCall) { // 获取聚合函数参数字段名列表
    final List<String> result = new ArrayList<>(aggCall.getArgList().size()); // 创建结果列表，预分配大小
    for (int fieldIndex : aggCall.getArgList()) { // 遍历聚合函数的所有参数字段索引
      result.add(getInputFieldNameForAggCall(relAlias, aggCall, fieldIndex)); // 为每个字段生成 Pig Latin 引用
    } // 遍历结束
    return result; // 返回参数字段名列表
  } // getArgNames 方法结束

  // getInputFieldNameForAggCall 方法：为聚合函数调用生成输入字段的 Pig Latin 引用
  // 此方法根据是否为 DISTINCT 聚合，生成不同的字段引用
  // 参数说明：
  //   - relAlias: 关系别名（如 "A"）
  //   - aggCall: 聚合调用对象
  //   - fieldIndex: 字段索引
  // 返回值：返回字段引用字符串
  // 生成逻辑：
  //   - 如果是 DISTINCT 聚合（如 COUNT(DISTINCT owner)），返回 "owner_DISTINCT"
  //   - 如果不是 DISTINCT 聚合，返回 "relAlias.field_name"
  // 示例输出：
  //   - 非 DISTINCT："A.pet_num"
  //   - DISTINCT："owner_DISTINCT"
  private String getInputFieldNameForAggCall(String relAlias, AggregateCall aggCall, // 生成输入字段引用
      int fieldIndex) { // 接收字段索引
    final String inputField = getInputFieldName(fieldIndex); // 获取原始字段名
    return aggCall.isDistinct() ? (inputField + DISTINCT_FIELD_SUFFIX) // 如果是 DISTINCT 聚合，添加后缀
        : (relAlias + '.' + inputField); // 否则，生成 "relAlias.field_name" 格式
  } // getInputFieldNameForAggCall 方法结束

  /**
   * Returns the calls to aggregate functions that have the {@code DISTINT} flag.
   *
   * <p>An aggregate function call like <code>COUNT(DISTINCT COL)</code> in Pig
   * is achieved via two statements in a {@code FOREACH} that follows a
   * {@code GROUP} statement:
   *
   * <blockquote>
   * <code>
   * TABLE = GROUP TABLE ALL;<br>
   * TABLE = FOREACH TABLE {<br>
   * &nbsp;&nbsp;<b>COL.DISTINCT = DISTINCT COL;<br>
   * &nbsp;&nbsp;GENERATE COUNT(COL.DISTINCT) AS C;</b><br>
   * }</code>
   * </blockquote>
   */
  // getDistinctCalls 方法：获取所有 DISTINCT 聚合函数的预处理语句
  // 在 Pig 中，COUNT(DISTINCT col) 需要两步实现：
  //   1. 先执行 DISTINCT 操作，生成去重后的字段
  //   2. 再对去重后的字段进行 COUNT
  // 此方法生成第一步的 DISTINCT 语句
  // 参数说明：
  //   - implementor: Pig 实现器，提供关系别名等上下文信息
  // 返回值：返回 DISTINCT 语句字符串列表
  // 生成逻辑：
  //   1. 遍历所有聚合调用
  //   2. 如果是 DISTINCT 聚合，为每个参数字段生成 DISTINCT 语句
  //   3. 语句格式："field_DISTINCT = DISTINCT relAlias.field;"
  // 示例输出：
  //   ["  owner_DISTINCT = DISTINCT A.owner;\n", "  pet_type_DISTINCT = DISTINCT A.pet_type;\n"]
  private List<String> getDistinctCalls(Implementor implementor) { // 获取 DISTINCT 聚合的预处理语句
    final String relAlias = implementor.getPigRelationAlias(this); // 获取当前关系的别名
    final List<String> result = new ArrayList<>(); // 创建结果列表
    for (AggregateCall aggCall : aggCalls) { // 遍历所有聚合调用
      if (aggCall.isDistinct()) { // 如果是 DISTINCT 聚合
        for (int fieldIndex : aggCall.getArgList()) { // 遍历聚合函数的所有参数字段
          String fieldName = getInputFieldName(fieldIndex); // 获取字段名
          result.add("  " + fieldName + DISTINCT_FIELD_SUFFIX + " = DISTINCT " // 生成 DISTINCT 语句
              + relAlias + '.' + fieldName + ";\n"); // 完成语句，添加换行符
        } // 遍历参数结束
      } // if 结束
    } // 遍历结束
    return result; // 返回 DISTINCT 语句列表
  } // getDistinctCalls 方法结束

  // getInputFieldName 方法：根据字段索引获取输入关系的字段名
  // 此方法从输入关系的行类型中获取指定索引的字段名
  // 参数说明：
  //   - fieldIndex: 字段索引（从 0 开始）
  // 返回值：返回字段名字符串
  // 示例：fieldIndex = 0，返回 "owner"
  private String getInputFieldName(int fieldIndex) { // 根据索引获取字段名
    return getInput().getRowType().getFieldList().get(fieldIndex).getName(); // 从输入关系中获取字段名
  } // getInputFieldName 方法结束
} // 类定义结束
