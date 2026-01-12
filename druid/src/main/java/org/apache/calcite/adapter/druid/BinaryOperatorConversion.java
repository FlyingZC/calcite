/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this software except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */ // Apache许可证头，声明软件的使用权限和限制
package org.apache.calcite.adapter.druid; // 指定包名，该类属于Druid适配器包

import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，用于描述表或表达式的数据类型
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示行表达式调用（函数调用或操作符调用）
import org.apache.calcite.rex.RexNode; // 导入RexNode基类，表示行表达式树的节点
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator类，表示SQL操作符（如+、-、*、/等）

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值

import java.util.List; // 导入List接口，用于存储操作数列表

/**
 * Binary operator conversion utility class; used to convert expressions like
 * {@code exp1 Operator exp2}.
 */ // 类注释：二元操作符转换工具类，用于将形如"表达式1 操作符 表达式2"的Calcite表达式转换为Druid表达式
public class BinaryOperatorConversion implements DruidSqlOperatorConverter { // 定义BinaryOperatorConversion类，实现DruidSqlOperatorConverter接口，提供二元操作符的转换功能
  private final SqlOperator operator; // 成员变量：存储Calcite的SQL操作符对象（如加法、减法、乘法、除法等操作符）
  private final String druidOperator; // 成员变量：存储对应的Druid操作符字符串表示（如"+", "-", "*", "/"等）

  public BinaryOperatorConversion(final SqlOperator operator, final String druidOperator) { // 构造方法：创建二元操作符转换器实例，初始化Calcite操作符和Druid操作符
    this.operator = operator; // 将传入的Calcite操作符赋值给成员变量operator
    this.druidOperator = druidOperator; // 将传入的Druid操作符字符串赋值给成员变量druidOperator
  } // 构造方法结束

  @Override public SqlOperator calciteOperator() { // 重写接口方法：返回此转换器对应的Calcite操作符
    return operator; // 返回成员变量operator，即Calcite的SQL操作符
  } // calciteOperator方法结束

  @Override public @Nullable String toDruidExpression(RexNode rexNode, // 重写接口方法：将Calcite的RexNode表达式转换为Druid表达式字符串
      RelDataType rowType, DruidQuery druidQuery) { // 参数：rexNode-要转换的Calcite表达式节点；rowType-输入行的数据类型；druidQuery-Druid查询上下文对象

    final RexCall call = (RexCall) rexNode; // 将RexNode强制转换为RexCall类型，因为二元操作符调用是RexCall的一种

    final List<String> druidExpressions = // 声明一个字符串列表，用于存储操作数转换后的Druid表达式
        DruidExpressions.toDruidExpressions(druidQuery, rowType, // 调用DruidExpressions工具类的静态方法，将RexCall的所有操作数转换为Druid表达式
            call.getOperands()); // 获取RexCall的操作数列表（二元操作符应该有2个操作数）
    if (druidExpressions == null) { // 检查转换结果，如果返回null表示转换失败（可能因为操作数不支持的类型）
      return null; // 返回null表示无法转换为Druid表达式
    } // if语句结束
    if (druidExpressions.size() != 2) { // 检查操作数数量，二元操作符必须恰好有2个操作数
      throw new IllegalStateException( // 如果操作数数量不是2，抛出异常，因为这是非法状态
          DruidQuery.format("Got binary operator[%s] with %s args?", operator.getName(), // 格式化错误消息，显示操作符名称和实际操作数数量
              druidExpressions.size())); // 填入实际操作数数量
    } // if语句结束

    return DruidQuery // 返回转换后的Druid表达式字符串
        .format("(%s %s %s)", druidExpressions.get(0), druidOperator, druidExpressions.get(1)); // 格式化为"(左操作数 操作符 右操作数)"的形式，例如"(a + b)"
  } // toDruidExpression方法结束
} // BinaryOperatorConversion类结束