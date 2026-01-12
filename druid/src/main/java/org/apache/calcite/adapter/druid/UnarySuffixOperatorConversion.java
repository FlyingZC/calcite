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
// Apache Calcite是一个动态数据管理框架，提供SQL解析、优化、查询执行等功能
// 本包(druid.adapter)专门负责将Calcite的SQL查询转换为Druid查询引擎能够理解的表达式
package org.apache.calcite.adapter.druid; // 定义包名，位于Druid适配器模块中

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，用于表示关系数据类型
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示Rex表达式中的函数调用
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式的抽象基类
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator类，表示SQL操作符的基类

import com.google.common.collect.Iterables; // 导入Google Guava库的Iterables工具类，用于集合操作

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性注解，用于标记可能为null的返回值

import java.util.List; // 导入Java集合框架的List接口

/**
 * Unary suffix operator conversion; used to convert function likes expression
 * Unary_Operator.
 */
// 类作用说明：一元后缀操作符转换器，用于将Calcite中的一元后缀操作符转换为Druid表达式
// 一元后缀操作符是指操作符位于操作数之后的操作符，例如某些数据库中的"IS NOT NULL"可以表示为"expr IS NOT NULL"
// 实现了DruidSqlOperatorConverter接口，该接口定义了将Calcite SQL操作符转换为Druid表达式的契约
// 主要用于处理类似"expression operator"这种形式的表达式，其中operator是后缀操作符
// 例如：在某些场景下，将Calcite的IS NOT NULL操作符转换为Druid的对应表达式
public class UnarySuffixOperatorConversion implements DruidSqlOperatorConverter { // 定义类名，实现DruidSqlOperatorConverter接口
  private final SqlOperator operator; // 成员变量：存储Calcite的SQL操作符对象，表示需要被转换的操作符（如IS NOT NULL等）
  private final String druidOperator; // 成员变量：存储Druid中对应的操作符字符串，表示转换后在Druid查询中使用的操作符名称

  public UnarySuffixOperatorConversion(SqlOperator operator, String druidOperator) { // 构造方法：创建一元后缀操作符转换器实例
    this.operator = operator; // 将传入的Calcite操作符赋值给成员变量operator，用于后续识别和转换
    this.druidOperator = druidOperator; // 将传入的Druid操作符字符串赋值给成员变量druidOperator，用于生成Druid表达式
  } // 构造方法结束

  @Override public SqlOperator calciteOperator() { // 重写接口方法：获取此转换器对应的Calcite SQL操作符
    return operator; // 返回成员变量operator，即此转换器负责转换的Calcite操作符
  } // 方法结束：用于识别哪些Calcite操作符应该使用此转换器进行转换

  @Override public @Nullable String toDruidExpression(RexNode rexNode, // 重写接口方法：将Calcite的Rex表达式节点转换为Druid表达式字符串
      RelDataType rowType, DruidQuery druidQuery) { // 参数说明：rexNode是要转换的Rex表达式节点，rowType是输入行的数据类型，druidQuery是Druid查询上下文
    final RexCall call = (RexCall) rexNode; // 将RexNode强制转换为RexCall类型，因为一元操作符在Rex中表现为函数调用形式

    final List<String> druidExpressions = // 声明变量用于存储操作数转换后的Druid表达式列表
        DruidExpressions.toDruidExpressions(druidQuery, rowType, // 调用DruidExpressions工具类将操作数转换为Druid表达式
            call.getOperands()); // 获取RexCall的所有操作数，对于一元操作符这里只有一个操作数

    if (druidExpressions == null) { // 检查转换结果是否为null，null表示转换失败（例如操作数无法转换为Druid表达式）
      return null; // 返回null表示无法将此表达式转换为Druid表达式，调用者需要处理这种情况
    } // 条件判断结束：如果转换失败则直接返回null

    return DruidQuery.format("(%s %s)", // 使用DruidQuery.format方法格式化字符串，生成Druid表达式
        Iterables.getOnlyElement(druidExpressions), druidOperator); // 获取唯一的操作数表达式，并将其与Druid操作符组合成"操作数 操作符"的格式
  } // 方法结束：返回格式化后的Druid表达式字符串，例如"(column IS NOT NULL)"
} // 类定义结束
