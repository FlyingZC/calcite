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
package org.apache.calcite.adapter.druid; // 声明包名，该类属于org.apache.calcite.adapter.druid包，用于Druid适配器相关的功能

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，用于表示关系数据类型，描述表结构中列的类型信息
import org.apache.calcite.rex.RexCall; // 导入RexCall类，用于表示行表达式调用，即函数调用表达式
import org.apache.calcite.rex.RexNode; // 导入RexNode类，用于表示行表达式节点，是所有行表达式的基类
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator类，用于表示SQL操作符，如函数、运算符等

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值

import java.util.List; // 导入List接口，用于存储有序的元素集合

/**
 * Direct operator conversion for expression like Function(exp_1,...exp_n)
 * // 类作用：直接操作符转换器，用于将Calcite中的函数调用表达式直接转换为Druid表达式
 * // 该类实现了DruidSqlOperatorConverter接口，提供了一种简单直接的方式将Calcite的SQL操作符映射到Druid的函数
 * // 主要处理形如Function(exp_1,...exp_n)的表达式转换，其中Function是Druid中的函数名，exp_1到exp_n是函数参数
 * // 这种转换方式适用于Calcite操作符和Druid函数之间一一对应的情况，不需要复杂的转换逻辑
 * // 例如：Calcite的ABS函数可以直接映射到Druid的abs函数，只需要将参数表达式转换为Druid表达式即可
 */
public class DirectOperatorConversion implements DruidSqlOperatorConverter { // 定义DirectOperatorConversion类，实现DruidSqlOperatorConverter接口，用于直接将Calcite操作符转换为Druid表达式
  private final SqlOperator operator; // 成员变量：存储Calcite的SQL操作符对象，表示要转换的Calcite操作符（如ABS、UPPER等函数）
  private final String druidFunctionName; // 成员变量：存储对应的Druid函数名称字符串，表示在Druid中使用的函数名（如"abs"、"upper"等）

  public DirectOperatorConversion(final SqlOperator operator, final String druidFunctionName) { // 构造方法：创建DirectOperatorConversion实例，初始化Calcite操作符和对应的Druid函数名
    this.operator = operator; // 将传入的Calcite操作符赋值给成员变量operator，保存要转换的操作符引用
    this.druidFunctionName = druidFunctionName; // 将传入的Druid函数名字符串赋值给成员变量druidFunctionName，保存目标Druid函数名
  }

  @Override public SqlOperator calciteOperator() { // 方法：重写DruidSqlOperatorConverter接口的calciteOperator方法，返回此转换器关联的Calcite操作符
    return operator; // 返回成员变量operator，即此转换器负责转换的Calcite SQL操作符对象
  }

  @Override public @Nullable String toDruidExpression(RexNode rexNode, // 方法：重写DruidSqlOperatorConverter接口的toDruidExpression方法，将Calcite的RexNode表达式转换为Druid表达式字符串
      RelDataType rowType, DruidQuery druidQuery) { // 参数：rowType表示输入行的数据类型信息，druidQuery表示Druid查询上下文对象，包含查询相关的元数据和配置
    final RexCall call = (RexCall) rexNode; // 将传入的RexNode强制转换为RexCall类型，因为DirectOperatorConversion处理的是函数调用表达式
    final List<String> druidExpressions = // 声明一个字符串列表，用于存储转换后的Druid表达式
        DruidExpressions.toDruidExpressions(druidQuery, rowType, // 调用DruidExpressions工具类的toDruidExpressions方法，将RexCall的所有操作数转换为Druid表达式列表
            call.getOperands()); // 获取RexCall的操作数列表（即函数参数），将这些参数表达式转换为Druid表达式
    if (druidExpressions == null) { // 检查转换结果是否为null，如果为null表示转换失败（可能因为某些Calcite表达式无法转换为Druid表达式）
      return null; // 返回null表示无法转换为Druid表达式，调用方需要处理这种情况
    }
    return DruidExpressions.functionCall(druidFunctionName, druidExpressions); // 调用DruidExpressions工具类的functionCall方法，使用Druid函数名和参数列表生成最终的Druid函数调用表达式字符串
  }
}
