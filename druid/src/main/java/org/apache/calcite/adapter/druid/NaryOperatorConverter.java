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
package org.apache.calcite.adapter.druid; // 包声明：定义该类属于org.apache.calcite.adapter.druid包，该包包含Calcite到Druid适配器的相关实现

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，用于表示关系表达式的数据类型
import org.apache.calcite.rex.RexCall; // 导入RexCall类，用于表示行表达式的函数调用
import org.apache.calcite.rex.RexNode; // 导入RexNode类，用于表示行表达式的抽象节点
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator类，用于表示SQL操作符

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值

import java.util.List; // 导入List接口，用于存储操作数的Druid表达式列表

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空校验

/**
 * Converts Calcite n-ary operators to Druid expressions, for example
 * {@code arg1 Op arg2 Op arg3}.
 * // 类作用：将Calcite的n元操作符（n-ary operator）转换为Druid表达式，例如将arg1 Op arg2 Op arg3这样的操作转换为Druid格式
 * // 该类实现了DruidSqlOperatorConverter接口，提供了将Calcite的SQL操作符转换为Druid表达式的能力
 * // n元操作符是指可以接受两个或更多操作数的操作符，如AND、OR、加法、乘法等
 */
public class NaryOperatorConverter implements DruidSqlOperatorConverter { // 类定义：NaryOperatorConverter类，实现DruidSqlOperatorConverter接口，用于将Calcite的n元操作符转换为Druid表达式
  private final SqlOperator operator; // 成员变量：存储Calcite的SQL操作符对象，表示需要被转换的Calcite操作符（如AND、OR、PLUS等）
  private final String druidOperatorName; // 成员变量：存储对应的Druid操作符名称，表示在Druid中使用的操作符标识符（如"and"、"or"、"plus"等）

  public NaryOperatorConverter(SqlOperator operator, String druidOperatorName) { // 构造方法：创建NaryOperatorConverter实例，初始化Calcite操作符和对应的Druid操作符名称
    this.operator = requireNonNull(operator, "operator"); // 初始化operator成员变量，使用requireNonNull进行非空校验，确保传入的operator参数不为null，否则抛出NullPointerException
    this.druidOperatorName = // 初始化druidOperatorName成员变量，使用requireNonNull进行非空校验，确保传入的druidOperatorName参数不为null
        requireNonNull(druidOperatorName, "druidOperatorName"); // 继续初始化druidOperatorName，如果参数为null则抛出NullPointerException
  }

  @Override public SqlOperator calciteOperator() { // 方法：返回该转换器对应的Calcite操作符，实现了DruidSqlOperatorConverter接口的方法
    return operator; // 返回成员变量operator，即该转换器所处理的Calcite SQL操作符对象
  }

  @Override public @Nullable String toDruidExpression(RexNode rexNode, // 方法：将Calcite的RexNode节点转换为Druid表达式字符串，实现了DruidSqlOperatorConverter接口的方法，返回值可能为null表示转换失败
      RelDataType rowType, DruidQuery druidQuery) { // 参数：rowType表示行数据类型信息，druidQuery表示Druid查询上下文对象，包含转换所需的元数据信息
    final RexCall call = (RexCall) rexNode; // 将RexNode强制转换为RexCall类型，因为n元操作符在Calcite中表现为函数调用形式，RexCall包含操作符和操作数列表
    final List<String> druidExpressions = // 声明并初始化druidExpressions列表，用于存储所有操作数转换为Druid表达式后的字符串列表
        DruidExpressions.toDruidExpressions(druidQuery, rowType, // 调用DruidExpressions的静态方法toDruidExpressions，将RexCall的所有操作数转换为Druid表达式列表
            call.getOperands()); // 获取RexCall的所有操作数（operands），这些操作数将被递归转换为Druid表达式
    if (druidExpressions == null) { // 判断转换结果是否为null，如果为null表示至少有一个操作数无法转换为Druid表达式
      return null; // 返回null表示整个表达式转换失败，Druid无法处理该表达式
    }
    return DruidExpressions.nAryOperatorCall(druidOperatorName, druidExpressions); // 调用DruidExpressions的静态方法nAryOperatorCall，将Druid操作符名称和操作数表达式列表组合成最终的Druid表达式字符串并返回
  }
}
