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
package org.apache.calcite.test.catalog; // 声明包路径，该类位于org.apache.calcite.test.catalog包下，属于测试工具包中的目录相关类

import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable类，代表关系代数表对象，包含表的元数据信息
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory类，用于创建关系数据类型
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder类，用于构建RexNode表达式树
import org.apache.calcite.rex.RexNode; // 导入RexNode类，代表关系表达式节点，是Calcite中表达式的抽象表示
import org.apache.calcite.schema.ColumnStrategy; // 导入ColumnStrategy枚举，定义列的生成策略（如默认值、可为空等）
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义SQL标准数据类型名称
import org.apache.calcite.sql2rel.InitializerContext; // 导入InitializerContext接口，提供初始化表达式所需的上下文信息
import org.apache.calcite.sql2rel.NullInitializerExpressionFactory; // 导入NullInitializerExpressionFactory基类，提供默认的空值初始化表达式工厂实现

import java.math.BigDecimal; // 导入BigDecimal类，用于精确的十进制数值计算

/** Default values for the "EMPDEFAULTS" table. */ // 类级别的Javadoc注释：定义"EMPDEFAULTS"表的默认值，该类是一个初始化表达式工厂，用于为员工表提供列的默认值和生成策略
class EmpInitializerExpressionFactory // 定义类名：EmpInitializerExpressionFactory，员工初始化表达式工厂类，用于生成员工表的列默认值
    extends NullInitializerExpressionFactory { // 继承NullInitializerExpressionFactory基类，获得基础的空值初始化功能，并在此之上扩展自定义默认值逻辑
  @Override public ColumnStrategy generationStrategy(RelOptTable table, // 重写父类方法：generationStrategy，用于确定指定列的生成策略，返回ColumnStrategy枚举值
      int iColumn) { // 参数iColumn：列索引，从0开始，标识要查询策略的列位置
    switch (iColumn) { // 使用switch语句根据列索引判断不同列的生成策略
    case 0: // 第0列，通常是员工ID列
    case 1: // 第1列，通常是员工姓名列
    case 5: // 第5列，通常是员工部门ID或其他特定列
      return ColumnStrategy.DEFAULT; // 返回DEFAULT策略，表示该列使用默认值生成
    default: // 其他列（非0、1、5的列）
      return super.generationStrategy(table, iColumn); // 调用父类的generationStrategy方法，使用默认的空值策略
    }
  }

  @Override public RexNode newColumnDefaultValue(RelOptTable table, // 重写父类方法：newColumnDefaultValue，为指定列创建默认值的RexNode表达式节点
      int iColumn, InitializerContext context) { // 参数iColumn：列索引；参数context：初始化上下文，提供RexBuilder等工具
    final RexBuilder rexBuilder = context.getRexBuilder(); // 从上下文中获取RexBuilder对象，用于构建各种RexNode表达式
    final RelDataTypeFactory typeFactory = rexBuilder.getTypeFactory(); // 从RexBuilder中获取RelDataTypeFactory对象，用于创建SQL数据类型
    switch (iColumn) { // 使用switch语句根据列索引为不同列生成对应的默认值表达式
    case 0: // 第0列，员工ID列
      return rexBuilder.makeExactLiteral(new BigDecimal(123), // 创建精确数值字面量表达式，值为123的BigDecimal对象
          typeFactory.createSqlType(SqlTypeName.INTEGER)); // 指定数据类型为INTEGER，确保字面量类型匹配列类型
    case 1: // 第1列，员工姓名列
      return rexBuilder.makeLiteral("Bob"); // 创建字符串字面量表达式，值为"Bob"，RexBuilder会自动推断VARCHAR类型
    case 5: // 第5列，可能是部门ID或其他数值列
      return rexBuilder.makeExactLiteral(new BigDecimal(555), // 创建精确数值字面量表达式，值为555的BigDecimal对象
          typeFactory.createSqlType(SqlTypeName.INTEGER)); // 指定数据类型为INTEGER，确保字面量类型匹配列类型
    default: // 其他列（非0、1、5的列）
      return super.newColumnDefaultValue(table, iColumn, context); // 调用父类的newColumnDefaultValue方法，返回默认的空值表达式
    }
  }
} // 类结束，该类通过继承和重写父类方法，为EMPDEFAULTS表的特定列（0、1、5列）提供了自定义的默认值和生成策略
