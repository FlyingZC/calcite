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
 */ // Apache许可证声明，说明代码的授权和使用条款
package org.apache.calcite.adapter.druid; // 定义包名，该类属于org.apache.calcite.adapter.druid包，是Calcite适配Druid数据源的适配器包

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，用于表示关系数据类型，描述表或查询结果的行结构
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式节点，是Calcite中用于表示SQL表达式的抽象语法树节点
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator类，表示SQL操作符，如加减乘除、函数调用等

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值或参数

/**
 * Defines how to convert a {@link RexNode} with a given Calcite SQL operator to
 * a Druid expression.
 */ // 接口文档注释：定义如何将带有给定Calcite SQL操作符的RexNode转换为Druid表达式
public interface DruidSqlOperatorConverter { // 定义一个公共接口DruidSqlOperatorConverter，用于将Calcite的SQL操作符转换为Druid表达式

  /**
   * Returns the calcite SQL operator corresponding to Druid operator.
   *
   * @return operator
   */ // 方法文档注释：返回与Druid操作符对应的Calcite SQL操作符
  SqlOperator calciteOperator(); // 声明一个方法，返回SqlOperator对象，用于获取对应的Calcite SQL操作符


  /**
   * Translate rexNode to valid Druid expression.
   *
   * @param rexNode rexNode to translate to Druid expression
   * @param rowType row type associated with rexNode
   * @param druidQuery druid query used to figure out configs/fields related like timeZone
   *
   * @return valid Druid expression or null if it can not convert the rexNode
   */ // 方法文档注释：将rexNode转换为有效的Druid表达式
  @Nullable String toDruidExpression(RexNode rexNode, RelDataType rowType, DruidQuery druidQuery); // 声明一个方法，将Calcite的RexNode转换为Druid表达式字符串，参数包括：rexNode要转换的表达式节点，rowType相关的行类型，druidQuery用于获取配置信息如时区，返回Druid表达式字符串或null（如果无法转换）
} // 接口定义结束
