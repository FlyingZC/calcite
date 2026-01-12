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
package org.apache.calcite.adapter.pig;

import org.apache.calcite.adapter.enumerable.EnumerableRules;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.rules.CoreRules;
import org.apache.calcite.rel.type.RelDataTypeField;

import org.apache.pig.data.DataType;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/** Implementation of {@link org.apache.calcite.rel.core.TableScan} in
 * {@link PigRel#CONVENTION Pig calling convention}. */ // 本类是 Pig 适配器中 TableScan 的实现，用于在 Pig 调用约定下执行表扫描操作，将 Calcite 的表扫描转换为 Pig Latin 的 LOAD 语句
public class PigTableScan extends TableScan implements PigRel { // 继承自 TableScan 表示这是一个表扫描操作，实现 PigRel 接口表示遵循 Pig 的调用约定

  /** Creates a PigTableScan. */ // 创建 PigTableScan 实例的构造方法
  public PigTableScan(RelOptCluster cluster, RelTraitSet traitSet, RelOptTable table) { // 参数：cluster-关系表达式集群，traitSet-特征集合，table-要扫描的表
    super(cluster, traitSet, ImmutableList.of(), table); // 调用父类 TableScan 的构造方法，传入集群、特征集、空的输入列表和表对象
    assert getConvention() == PigRel.CONVENTION; // 断言当前关系表达式的调用约定必须是 Pig 调用约定，确保类型安全
  }

  @Override public void implement(Implementor implementor) { // 实现 PigRel 接口的 implement 方法，将此表扫描操作转换为 Pig Latin 语句
    final PigTable pigTable = getPigTable(implementor.getTableName(this)); // 根据表名获取对应的 PigTable 对象，该对象包含文件路径等信息
    final String alias = implementor.getPigRelationAlias(this); // 获取此关系表达式对应的 Pig 关系别名，用于在 Pig Latin 中引用此关系
    final String schema = '(' + getSchemaForPigStatement(implementor) // 构建 Pig Latin 的模式定义字符串，格式为 (field1:type1, field2:type2, ...)
        + ')'; // 闭合模式定义的括号
    final String statement = alias + " = LOAD '" + pigTable.getFilePath() // 生成完整的 Pig Latin LOAD 语句，格式为 alias = LOAD 'filepath' USING PigStorage() AS (schema);
        + "' USING PigStorage() AS " + schema + ';'; // 使用 PigStorage 作为默认的加载函数，指定模式定义
    implementor.addStatement(statement); // 将生成的 Pig Latin 语句添加到实现器中，用于最终输出 Pig 脚本
  }

  private PigTable getPigTable(String name) { // 根据表名获取对应的 PigTable 对象，该方法用于从 Calcite 的 schema 中查找表并转换为 PigTable
    final CalciteSchema schema = getTable().unwrapOrThrow(CalciteSchema.class); // 从当前表对象中解包获取 CalciteSchema，如果无法解包则抛出异常
    return (PigTable) requireNonNull(schema.getTable(name, false)) // 从 schema 中获取指定名称的表，requireNonNull 确保表不为空，false 表示不区分大小写
        .getTable(); // 从 CalciteSchema.Table 中获取实际的表对象，并强制转换为 PigTable 类型
  }

  private String getSchemaForPigStatement(Implementor implementor) { // 生成 Pig Latin 语句中的模式定义字符串，将表的所有字段转换为 Pig 的字段名和类型格式
    final List<String> fieldNamesAndTypes = // 创建一个列表用于存储所有字段的名称和类型字符串
        new ArrayList<>(getTable().getRowType().getFieldList().size()); // 初始化列表，大小与表的字段数量相同
    for (RelDataTypeField f : getTable().getRowType().getFieldList()) { // 遍历表的所有字段
      fieldNamesAndTypes.add(getConcatenatedFieldNameAndTypeForPigSchema(implementor, f)); // 将每个字段转换为 Pig 格式的 "字段名:类型" 字符串并添加到列表中
    }
    return String.join(", ", fieldNamesAndTypes); // 将所有字段的字符串用逗号和空格连接，生成完整的模式定义字符串
  }

  private String getConcatenatedFieldNameAndTypeForPigSchema(Implementor implementor, // 生成单个字段的 Pig 模式字符串，格式为 "字段名:类型"
      RelDataTypeField field) { // 参数：implementor-实现器对象，field-要转换的字段
    final PigDataType pigDataType = PigDataType.valueOf(field.getType().getSqlTypeName()); // 将 Calcite 的 SQL 类型转换为 Pig 数据类型，通过 PigDataType 枚举进行映射
    final String fieldName = implementor.getFieldName(this, field.getIndex()); // 从实现器中获取字段在 Pig 中的名称，使用当前关系表达式和字段索引
    return fieldName + ':' + DataType.findTypeName(pigDataType.getPigType()); // 返回格式为 "字段名:类型" 的字符串，使用 DataType.findTypeName 将 Pig 类型代码转换为类型名称
  }

  @Override public void register(RelOptPlanner planner) { // 注册 Pig 适配器所需的规则到优化器中，确保优化器使用 Pig 特定的规则而非默认规则
    planner.addRule(PigToEnumerableConverterRule.INSTANCE); // 添加 Pig 到 Enumerable 的转换规则，用于将 Pig 关系表达式转换为可执行的 Enumerable 表达式
    for (RelOptRule rule : PigRules.ALL_PIG_OPT_RULES) { // 遍历所有 Pig 优化规则
      planner.addRule(rule); // 将每个 Pig 优化规则添加到优化器中，这些规则用于优化 Pig 关系表达式树
    }
    // Don't move Aggregates around, otherwise PigAggregate.implement() won't
    // know how to correctly procuce Pig Latin // 不要移动聚合操作，否则 PigAggregate.implement() 将无法正确生成 Pig Latin 代码
    planner.removeRule(CoreRules.AGGREGATE_EXPAND_DISTINCT_AGGREGATES); // 移除聚合展开规则，防止优化器重写聚合操作，确保 Pig 聚合的正确实现
    // Make sure planner picks PigJoin over EnumerableHashJoin. Should there be
    // a rule for this instead for removing ENUMERABLE_JOIN_RULE here? // 确保优化器选择 PigJoin 而不是 EnumerableHashJoin，是否应该用规则替代这里直接移除 ENUMERABLE_JOIN_RULE？
    planner.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除 Enumerable 连接规则，防止优化器使用 Enumerable 的连接实现，强制使用 Pig 的连接实现
  }
}
