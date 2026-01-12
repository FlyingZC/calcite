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
package org.apache.calcite.test.catalog;

import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable类，表示关系代数中的表对象，包含表的元数据信息
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示关系表达式节点，是Calcite中表达式树的节点类型
import org.apache.calcite.schema.ColumnStrategy; // 导入ColumnStrategy枚举，定义列的生成策略（如存储列、虚拟列等）
import org.apache.calcite.sql.SqlNode; // 导入SqlNode类，表示SQL语法树中的节点，是SQL解析后的抽象语法树节点
import org.apache.calcite.sql.parser.SqlParser; // 导入SqlParser类，用于将SQL字符串解析为SqlNode语法树
import org.apache.calcite.sql2rel.InitializerContext; // 导入InitializerContext接口，提供列初始化的上下文信息，包含解析、验证和转换表达式的方法
import org.apache.calcite.sql2rel.NullInitializerExpressionFactory; // 导入NullInitializerExpressionFactory基类，提供列默认值初始化的空实现

/**
 * Define column strategies for the "VIRTUALCOLUMNS" table.
 * 定义"VIRTUALCOLUMNS"表的列策略，该类用于为测试中的虚拟列表指定列的生成策略和默认值表达式
 * 继承自NullInitializerExpressionFactory，重写了列生成策略和默认值表达式的方法
 * 主要用于测试Calcite对虚拟列（VIRTUAL）和存储列（STORED）的支持
 */
public class VirtualColumnsExpressionFactory extends NullInitializerExpressionFactory { // 继承NullInitializerExpressionFactory基类，实现自定义的列初始化逻辑
  @Override public ColumnStrategy generationStrategy(RelOptTable table, int iColumn) { // 重写generationStrategy方法，返回指定列的生成策略，table参数表示表对象，iColumn参数表示列索引（从0开始）
    switch (iColumn) { // 根据列索引选择不同的生成策略
    case 3: // 如果是第4列（索引为3）
      return ColumnStrategy.STORED; // 返回STORED策略，表示该列为存储列，值会被实际存储在表中
    case 4: // 如果是第5列（索引为4）
      return ColumnStrategy.VIRTUAL; // 返回VIRTUAL策略，表示该列为虚拟列，值不会存储，而是在查询时通过表达式计算得出
    default: // 其他列
      return super.generationStrategy(table, iColumn); // 调用父类方法，返回默认的列生成策略
        }
    } // 类定义结束
      @Override public RexNode newColumnDefaultValue( // 重写newColumnDefaultValue方法，返回指定列的默认值表达式，table参数表示表对象，iColumn参数表示列索引，context参数提供初始化上下文
      RelOptTable table, int iColumn, InitializerContext context) { // 参数说明：table-关系表对象，iColumn-列索引，context-初始化上下文，提供解析、验证和转换表达式的方法
    if (iColumn == 4) { // 如果是第5列（索引为4，即虚拟列）
      final SqlNode node = context.parseExpression(SqlParser.Config.DEFAULT, "A + 1"); // 使用默认解析器配置解析表达式字符串"A + 1"为SqlNode语法树节点，该表达式表示列A的值加1
      // Actually we should validate the node with physical schema, // 实际上我们应该使用物理模式（不包含虚拟列）来验证节点
      // here full table schema(includes the virtual columns) also works // 这里使用完整的表模式（包含虚拟列）也可以工作
      // because the expression "A + 1" does not reference any virtual column. // 因为表达式"A + 1"没有引用任何虚拟列，只引用了物理列A
      final SqlNode validated = context.validateExpression(table.getRowType(), node); // 使用表的行类型（包含所有列的元数据）验证表达式节点，确保表达式中的列名和类型正确
      return context.convertExpression(validated); // 将验证后的SqlNode转换为RexNode（关系表达式节点），RexNode是Calcite内部使用的表达式表示形式
    } else { // 其他列
      return super.newColumnDefaultValue(table, iColumn, context); // 调用父类方法，返回默认的列默认值表达式（通常为null）
    }
  }
}
