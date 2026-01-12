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
// Apache许可证头，声明此代码遵循Apache 2.0许可证
package org.apache.calcite.adapter.csv; // 定义包名，CSV适配器包，包含CSV文件相关的适配器实现

// 导入Calcite核心枚举约定，用于定义物理实现特征
import org.apache.calcite.adapter.enumerable.EnumerableConvention;
// 导入可枚举关系表达式接口，表示可以被枚举执行的关系节点
import org.apache.calcite.adapter.enumerable.EnumerableRel;
// 导入可枚举关系表达式实现器，用于生成可执行代码
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor;
// 导入物理类型接口，表示Java物理类型
import org.apache.calcite.adapter.enumerable.PhysType;
// 导入物理类型实现类，提供物理类型的具体实现
import org.apache.calcite.adapter.enumerable.PhysTypeImpl;
// 导入代码块工具类，用于生成Java代码块
import org.apache.calcite.linq4j.tree.Blocks;
// 导入表达式工具类，用于构建Java表达式树
import org.apache.calcite.linq4j.tree.Expressions;
// 导入基本类型工具类，用于处理基本类型转换
import org.apache.calcite.linq4j.tree.Primitive;
// 导入关系优化集群，用于管理关系表达式集合和共享上下文
import org.apache.calcite.plan.RelOptCluster;
// 导入关系优化成本接口，表示查询计划的执行成本
import org.apache.calcite.plan.RelOptCost;
// 导入关系优化规划器接口，用于优化查询计划
import org.apache.calcite.plan.RelOptPlanner;
// 导入关系优化表接口，表示表元数据
import org.apache.calcite.plan.RelOptTable;
// 导入关系特征集合，表示关系节点的物理特征
import org.apache.calcite.plan.RelTraitSet;
// 导入关系节点接口，表示查询计划中的节点
import org.apache.calcite.rel.RelNode;
// 导入关系写入器接口，用于生成查询计划的解释文本
import org.apache.calcite.rel.RelWriter;
// 导入表扫描基类，表示从表中扫描数据的叶子节点
import org.apache.calcite.rel.core.TableScan;
// 导入关系元数据查询接口，用于查询关系节点的元数据
import org.apache.calcite.rel.metadata.RelMetadataQuery;
// 导入关系数据类型接口，表示Calcite中的数据类型
import org.apache.calcite.rel.type.RelDataType;
// 导入关系数据类型工厂接口，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory;
// 导入关系数据类型字段接口，表示关系类型的字段
import org.apache.calcite.rel.type.RelDataTypeField;

// 导入Google Guava的不可变列表工具类
import com.google.common.collect.ImmutableList;

// 导入可空注解，用于标记可能为null的返回值
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入Java列表接口
import java.util.List;

// 静态导入Objects.requireNonNull方法，用于空值检查
import static java.util.Objects.requireNonNull;

/**
 * Relational expression representing a scan of a CSV file.
 * 表示扫描CSV文件的关系表达式
 *
 * <p>Like any table scan, it serves as a leaf node of a query tree.
 * 像任何表扫描一样，它作为查询树的叶子节点
 * 
 * 这个类是Calcite适配器模式的关键实现，负责将CSV文件扫描操作转换为可执行的关系表达式
 * 它继承自TableScan基类，实现了EnumerableRel接口，表示可以被枚举执行的关系节点
 * 
 * 主要功能：
 * 1. 定义CSV表扫描的物理实现
 * 2. 支持字段投影（只读取需要的字段）
 * 3. 计算扫描操作的执行成本
 * 4. 生成可执行的Java代码来读取CSV文件
 * 
 * 成员变量说明：
 * - csvTable: CSV可翻译表对象，包含CSV表的元数据和实现细节
 * - fields: 字段索引数组，指定需要扫描的字段，支持字段投影优化
 */
public class CsvTableScan extends TableScan implements EnumerableRel { // CsvTableScan继承自TableScan，实现EnumerableRel接口，表示CSV表扫描的关系表达式
  final CsvTranslatableTable csvTable; // CSV可翻译表对象，包含CSV表的元数据（如文件路径、编码、分隔符等）和实现方法，用于生成可执行代码
  final int[] fields; // 字段索引数组，指定需要扫描的字段位置，支持字段投影优化，避免读取不需要的字段，提高查询性能

  // 构造方法：创建CSV表扫描节点
  protected CsvTableScan(RelOptCluster cluster, RelOptTable table, // 参数：cluster-关系优化集群，提供类型工厂等共享服务；table-关系优化表，包含表元数据
      CsvTranslatableTable csvTable, int[] fields) { // 参数：csvTable-CSV可翻译表对象；fields-字段索引数组，指定要扫描的字段
    super(cluster, cluster.traitSetOf(EnumerableConvention.INSTANCE), ImmutableList.of(), table); // 调用父类构造方法，设置集群、特征集（使用可枚举约定）、输入列表（空，因为表扫描是叶子节点）和表
    this.csvTable = requireNonNull(csvTable, "csvTable"); // 初始化CSV表对象，使用requireNonNull确保csvTable不为null，否则抛出NullPointerException
    this.fields = fields; // 初始化字段索引数组，存储需要扫描的字段位置
  }

  // 复制方法：创建当前关系节点的副本，用于查询优化过程中的节点转换
  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 参数：traitSet-新的关系特征集合；inputs-输入节点列表
    assert inputs.isEmpty(); // 断言输入列表为空，因为表扫描是叶子节点，不应该有输入节点
    return new CsvTableScan(getCluster(), table, csvTable, fields); // 返回一个新的CsvTableScan对象，保持原有的集群、表、CSV表对象和字段索引不变
  }

  // 解释方法：生成查询计划的解释文本，用于调试和查询计划展示
  @Override public RelWriter explainTerms(RelWriter pw) { // 参数：pw-关系写入器，用于写入解释文本
    return super.explainTerms(pw) // 调用父类方法，生成基础的解释信息（如表名、特征等）
        .item("fields", Primitive.asList(fields)); // 添加字段信息到解释文本，将字段索引数组转换为列表形式显示
  }

  // 推导行类型方法：根据字段索引数组推导出实际的行数据类型
  @Override public RelDataType deriveRowType() { // 返回值：推导出的关系数据类型，包含投影后的字段信息
    final List<RelDataTypeField> fieldList = table.getRowType().getFieldList(); // 获取表的原始字段列表，包含所有字段的类型信息
    final RelDataTypeFactory.Builder builder = // 创建类型构建器，用于构建新的关系数据类型
        getCluster().getTypeFactory().builder(); // 通过集群获取类型工厂，然后创建类型构建器
    for (int field : fields) { // 遍历字段索引数组中的每个字段位置
      builder.add(fieldList.get(field)); // 将指定位置的字段添加到构建器中，实现字段投影
    }
    return builder.build(); // 构建并返回新的关系数据类型，只包含需要的字段
  }

  // 注册方法：向优化规划器注册规则，使优化器能够应用特定的优化规则
  @Override public void register(RelOptPlanner planner) { // 参数：planner-关系优化规划器，负责查询优化
    planner.addRule(CsvRules.PROJECT_SCAN); // 向规划器添加PROJECT_SCAN规则，该规则用于优化CSV表扫描上的投影操作
  }

  // 计算自身成本方法：计算当前关系节点的执行成本，用于查询优化器选择最优执行计划
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 参数：planner-关系优化规划器；mq-关系元数据查询接口，用于查询元数据
      RelMetadataQuery mq) {
    // Multiply the cost by a factor that makes a scan more attractive if it
    // has significantly fewer fields than the original scan.
    // 将成本乘以一个因子，如果扫描的字段数明显少于原始扫描，则使该扫描更具吸引力
    //
    // The "+ 2D" on top and bottom keeps the function fairly smooth.
    // 顶部和底部的"+ 2D"使函数保持相对平滑，避免极端值
    //
    // For example, if table has 3 fields, project has 1 field,
    // then factor = (1 + 2) / (3 + 2) = 0.6
    // 例如，如果表有3个字段，投影有1个字段，则因子 = (1 + 2) / (3 + 2) = 0.6
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq)); // 调用父类方法计算基础成本，确保结果不为null
    return cost // 返回调整后的成本
        .multiplyBy(((double) fields.length + 2D) // 计算调整因子：分子是当前字段数加2
            / ((double) table.getRowType().getFieldCount() + 2D)); // 分母是原始字段总数加2，字段越少成本越低
  }

  // 实现方法：生成可执行的Java代码，用于执行CSV表扫描操作
  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 参数：implementor-实现器，负责生成可执行代码；pref-偏好设置，指定是否优先使用数组
    PhysType physType = // 创建物理类型对象，描述Java代码的返回类型
        PhysTypeImpl.of( // 使用PhysTypeImpl工厂方法创建物理类型
            implementor.getTypeFactory(), // 获取类型工厂，用于创建Java类型
            getRowType(), // 获取当前的行类型（投影后的类型）
            pref.preferArray()); // 根据偏好设置决定是否使用数组作为返回类型

    return implementor.result( // 返回实现结果，包含生成的Java代码
        physType, // 设置返回的物理类型
        Blocks.toBlock( // 将表达式转换为代码块
            Expressions.call( // 创建方法调用表达式，调用CSV表的project方法
                requireNonNull(table.getExpression(CsvTranslatableTable.class)), // 获取CSV表的表达式，确保不为null
                "project", implementor.getRootExpression(), // 调用project方法，传入根表达式
                Expressions.constant(fields)))); // 传入字段索引数组作为常量参数
  }
}
