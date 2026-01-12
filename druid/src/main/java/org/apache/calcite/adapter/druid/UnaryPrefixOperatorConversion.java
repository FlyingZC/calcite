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
package org.apache.calcite.adapter.druid; // 声明包名，该类位于org.apache.calcite.adapter.druid包下，属于Calcite的Druid适配器模块

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，用于表示关系数据类型，描述行的结构
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示行表达式中的函数调用
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式的基类，所有行表达式都继承自此类
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator类，表示SQL操作符，如NOT、MINUS等

import com.google.common.collect.Iterables; // 导入Google Guava的Iterables工具类，用于集合操作

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值

import java.util.List; // 导入List接口，用于存储有序的元素集合

/**
 * Unary prefix Operator conversion class; used to convert expressions like
 * Unary NOT and Minus.
 * // 一元前缀操作符转换类；用于将Calcite中的一元前缀表达式（如NOT、MINUS等）转换为Druid表达式
 * // 该类实现了DruidSqlOperatorConverter接口，提供了将Calcite的行表达式转换为Druid表达式的能力
 * // 典型应用场景包括：将SQL中的NOT操作符转换为Druid的not函数，将负号操作符转换为Druid的unaryMinus函数
 * // 该转换器处理的是一元操作符，即只有一个操作数的操作符，且操作符位于操作数之前（前缀）
 */
public class UnaryPrefixOperatorConversion implements DruidSqlOperatorConverter { // 定义类名，实现DruidSqlOperatorConverter接口

  private final SqlOperator operator; // 成员变量：存储Calcite的SQL操作符对象，如SqlStdOperatorTable.NOT或SqlStdOperatorTable.UNARY_MINUS
  private final String druidOperator; // 成员变量：存储对应的Druid操作符名称字符串，如"not"或"unaryMinus"，用于构建Druid表达式

  public UnaryPrefixOperatorConversion(final SqlOperator operator, final String druidOperator) { // 构造方法：创建一元前缀操作符转换器实例
    this.operator = operator; // 将传入的Calcite操作符赋值给成员变量，用于后续识别和匹配
    this.druidOperator = druidOperator; // 将传入的Druid操作符名称赋值给成员变量，用于构建Druid表达式
  }

  @Override public SqlOperator calciteOperator() { // 重写接口方法：返回此转换器对应的Calcite操作符
    return operator; // 返回成员变量中存储的Calcite操作符对象，用于在转换过程中识别和匹配
  }

  @Override public @Nullable String toDruidExpression(RexNode rexNode, // 重写接口方法：将Calcite的行表达式转换为Druid表达式字符串
      RelDataType rowType, DruidQuery druidQuery) { // 参数：rowType表示输入行的数据类型，druidQuery表示Druid查询上下文，返回值可能为null表示无法转换

    final RexCall call = (RexCall) rexNode; // 将传入的rexNode强制转换为RexCall类型，因为一元操作符表达式是函数调用的一种特殊形式

    final List<String> druidExpressions = // 声明一个字符串列表，用于存储操作数转换后的Druid表达式
        DruidExpressions.toDruidExpressions(druidQuery, rowType, // 调用DruidExpressions工具类，将RexCall的操作数转换为Druid表达式列表
            call.getOperands()); // 获取RexCall的所有操作数（一元操作符只有一个操作数），传入转换方法

    if (druidExpressions == null) { // 检查转换结果是否为null，null表示操作数无法转换为Druid表达式
      return null; // 返回null表示整个表达式转换失败
    }

    return DruidQuery // 返回构建好的Druid表达式字符串
        .format("(%s %s)", druidOperator, Iterables.getOnlyElement(druidExpressions)); // 使用format方法格式化Druid表达式，格式为"(操作符 操作数)"，getOnlyElement获取列表中唯一的元素（一元操作符只有一个操作数）
  } // 方法结束，返回格式化后的Druid表达式字符串，如"(not expr)"或"(unaryMinus expr)"
} // 类定义结束
