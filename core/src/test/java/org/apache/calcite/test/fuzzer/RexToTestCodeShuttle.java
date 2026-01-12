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
package org.apache.calcite.test.fuzzer; // 定义包名，该类属于fuzzer测试工具包，用于模糊测试

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示Rex表达式中的函数调用
import org.apache.calcite.rex.RexFieldAccess; // 导入RexFieldAccess类，表示Rex表达式中的字段访问
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类，表示Rex表达式中的字面量
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示Rex表达式的基类
import org.apache.calcite.rex.RexVisitorImpl; // 导入RexVisitorImpl类，Rex访问者的默认实现
import org.apache.calcite.sql.SqlKind; // 导入SqlKind类，表示SQL操作符的种类
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator类，表示SQL操作符
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类，包含标准SQL操作符表
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName类，表示SQL类型名称

import com.google.common.collect.ImmutableMap; // 导入Google Guava的不可变Map类

import java.util.List; // 导入List接口，表示有序列表
import java.util.Map; // 导入Map接口，表示键值对映射

/**
 * Converts {@link RexNode} into a string form usable for inclusion into
 * {@link RexProgramFuzzyTest}.
 * For instance, it converts {@code AND(=(?0.bool0, true), =(?0.bool1, true))} to
 * {@code isTrue(and(eq(vBool(0), trueLiteral), eq(vBool(1), trueLiteral)))}.
 */
// 类注释：RexToTestCodeShuttle是一个Rex表达式访问者，负责将RexNode转换为可用于RexProgramFuzzyTest测试代码的字符串形式
// 例如：将 AND(=(?0.bool0, true), =(?0.bool1, true)) 转换为 isTrue(and(eq(vBool(0), trueLiteral), eq(vBool(1), trueLiteral)))
// 这个类主要用于模糊测试场景，帮助生成测试代码
class RexToTestCodeShuttle extends RexVisitorImpl<String> { // 继承RexVisitorImpl<String>，实现Rex表达式访问者模式，返回字符串类型
  private static final Map<SqlOperator, String> OP_METHODS = // 定义静态常量映射表，将SQL操作符映射到对应的测试方法名
      ImmutableMap.<SqlOperator, String>builder() // 使用Guava的ImmutableMap构建器创建不可变映射
          .put(SqlStdOperatorTable.AND, "and") // AND操作符映射到"and"方法
          .put(SqlStdOperatorTable.OR, "or") // OR操作符映射到"or"方法
          .put(SqlStdOperatorTable.CASE, "case_") // CASE操作符映射到"case_"方法
          .put(SqlStdOperatorTable.CAST, "abstractCast") // CAST操作符映射到"abstractCast"方法
          .put(SqlStdOperatorTable.COALESCE, "coalesce") // COALESCE操作符映射到"coalesce"方法
          .put(SqlStdOperatorTable.IS_NULL, "isNull") // IS_NULL操作符映射到"isNull"方法
          .put(SqlStdOperatorTable.IS_NOT_NULL, "isNotNull") // IS_NOT_NULL操作符映射到"isNotNull"方法
          .put(SqlStdOperatorTable.IS_UNKNOWN, "isUnknown") // IS_UNKNOWN操作符映射到"isUnknown"方法
          .put(SqlStdOperatorTable.IS_TRUE, "isTrue") // IS_TRUE操作符映射到"isTrue"方法
          .put(SqlStdOperatorTable.IS_NOT_TRUE, "isNotTrue") // IS_NOT_TRUE操作符映射到"isNotTrue"方法
          .put(SqlStdOperatorTable.IS_FALSE, "isFalse") // IS_FALSE操作符映射到"isFalse"方法
          .put(SqlStdOperatorTable.IS_NOT_FALSE, "isNotFalse") // IS_NOT_FALSE操作符映射到"isNotFalse"方法
          .put(SqlStdOperatorTable.IS_DISTINCT_FROM, "isDistinctFrom") // IS_DISTINCT_FROM操作符映射到"isDistinctFrom"方法
          .put(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, "isNotDistinctFrom") // IS_NOT_DISTINCT_FROM操作符映射到"isNotDistinctFrom"方法
          .put(SqlStdOperatorTable.NULLIF, "nullIf") // NULLIF操作符映射到"nullIf"方法
          .put(SqlStdOperatorTable.NOT, "not") // NOT操作符映射到"not"方法
          .put(SqlStdOperatorTable.GREATER_THAN, "gt") // GREATER_THAN操作符映射到"gt"方法
          .put(SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, "ge") // GREATER_THAN_OR_EQUAL操作符映射到"ge"方法
          .put(SqlStdOperatorTable.LESS_THAN, "lt") // LESS_THAN操作符映射到"lt"方法
          .put(SqlStdOperatorTable.LESS_THAN_OR_EQUAL, "le") // LESS_THAN_OR_EQUAL操作符映射到"le"方法
          .put(SqlStdOperatorTable.EQUALS, "eq") // EQUALS操作符映射到"eq"方法
          .put(SqlStdOperatorTable.NOT_EQUALS, "ne") // NOT_EQUALS操作符映射到"ne"方法
          .put(SqlStdOperatorTable.PLUS, "plus") // PLUS操作符映射到"plus"方法
          .put(SqlStdOperatorTable.UNARY_PLUS, "unaryPlus") // UNARY_PLUS操作符映射到"unaryPlus"方法
          .put(SqlStdOperatorTable.MINUS, "sub") // MINUS操作符映射到"sub"方法
          .put(SqlStdOperatorTable.UNARY_MINUS, "unaryMinus") // UNARY_MINUS操作符映射到"unaryMinus"方法
          .put(SqlStdOperatorTable.MULTIPLY, "mul") // MULTIPLY操作符映射到"mul"方法
          .build(); // 构建不可变映射表


  protected RexToTestCodeShuttle() { // 构造方法，protected访问权限，只能在包内或子类中访问
    super(true); // 调用父类RexVisitorImpl的构造方法，传入true表示深度遍历（深度优先遍历Rex表达式树）
  }

  @Override public String visitCall(RexCall call) { // 重写visitCall方法，处理RexCall类型的节点（函数调用节点）
    SqlOperator operator = call.getOperator(); // 获取当前调用的操作符（如AND、OR、+等）
    String method = OP_METHODS.get(operator); // 从映射表中查找该操作符对应的测试方法名

    StringBuilder sb = new StringBuilder(); // 创建StringBuilder用于构建测试代码字符串
    if (method != null) { // 如果映射表中存在该方法名
      sb.append(method); // 添加方法名到字符串构建器
      sb.append('('); // 添加左括号，开始方法调用
    } else { // 如果映射表中不存在该方法名（非标准操作符）
      sb.append("rexBuilder.makeCall("); // 添加通用的rexBuilder.makeCall调用
      sb.append("SqlStdOperatorTable."); // 添加操作符表前缀
      sb.append(operator.getName().replace(' ', '_')); // 添加操作符名称，并将空格替换为下划线
      sb.append(", "); // 添加逗号和空格，分隔参数
    }
    List<RexNode> operands = call.getOperands(); // 获取当前调用的所有操作数（参数）
    for (int i = 0; i < operands.size(); i++) { // 遍历所有操作数
      RexNode operand = operands.get(i); // 获取当前操作数
      if (i > 0) { // 如果不是第一个操作数
        sb.append(", "); // 添加逗号和空格，分隔参数
      }
      sb.append(operand.accept(this)); // 递归访问当前操作数，将其转换为测试代码字符串
    }
    if (operator.kind == SqlKind.CAST) { // 如果当前操作符是CAST类型转换操作符
      sb.append(", t"); // 添加类型参数前缀
      appendSqlType(sb, call.getType()); // 调用appendSqlType方法添加目标类型名称
      sb.append('('); // 添加左括号，开始类型参数
      if (call.getType().isNullable()) { // 如果目标类型可为空
        sb.append("true"); // 添加true表示可为空
      }
      sb.append(')'); // 添加右括号，结束类型参数
    }
    sb.append(')'); // 添加右括号，结束方法调用
    return sb.toString(); // 返回构建好的测试代码字符串
  }

  @Override public String visitLiteral(RexLiteral literal) { // 重写visitLiteral方法，处理RexLiteral类型的节点（字面量节点）
    RelDataType type = literal.getType(); // 获取字面量的数据类型

    if (type.getSqlTypeName() == SqlTypeName.BOOLEAN) { // 如果是布尔类型
      if (literal.isNull()) { // 如果是null值
        return "nullBool"; // 返回"nullBool"表示null布尔值
      }
      return literal.toString() + "Literal"; // 返回字面量字符串加上"Literal"后缀，如"trueLiteral"
    }
    if (type.getSqlTypeName() == SqlTypeName.INTEGER) { // 如果是整数类型
      if (literal.isNull()) { // 如果是null值
        return "nullInt"; // 返回"nullInt"表示null整数值
      }
      return "literal(" + literal.getValue() + ")"; // 返回literal(值)格式，如"literal(5)"
    }
    if (type.getSqlTypeName() == SqlTypeName.VARCHAR) { // 如果是字符串类型
      if (literal.isNull()) { // 如果是null值
        return "nullVarchar"; // 返回"nullVarchar"表示null字符串值
      }
    }
    return "/*" + literal.getTypeName().getName() + "*/" + literal.toString(); // 对于其他类型，返回类型注释和字面量字符串
  }

  @Override public String visitFieldAccess(RexFieldAccess fieldAccess) { // 重写visitFieldAccess方法，处理RexFieldAccess类型的节点（字段访问节点）
    StringBuilder sb = new StringBuilder(); // 创建StringBuilder用于构建测试代码字符串
    sb.append("v"); // 添加"v"前缀，表示变量访问
    RelDataType type = fieldAccess.getType(); // 获取字段的数据类型
    appendSqlType(sb, type); // 调用appendSqlType方法添加类型名称
    if (!type.isNullable()) { // 如果字段类型不可为空
      sb.append("NotNull"); // 添加"NotNull"后缀
    }
    sb.append("("); // 添加左括号，开始参数
    sb.append(fieldAccess.getField().getIndex() % 10); // 添加字段索引，使用模10运算确保索引在0-9范围内
    sb.append(")"); // 添加右括号，结束参数
    return sb.toString(); // 返回构建好的测试代码字符串，如"vBool(0)"或"vIntNotNull(5)"
  }

  private void appendSqlType(StringBuilder sb, RelDataType type) { // 私有方法，将SQL类型名称追加到StringBuilder中
    switch (type.getSqlTypeName()) { // 根据SQL类型名称进行分支处理
    case BOOLEAN: // 如果是布尔类型
      sb.append("Bool"); // 添加"Bool"
      break; // 跳出switch
    case INTEGER: // 如果是整数类型
      sb.append("Int"); // 添加"Int"
      break; // 跳出switch
    case VARCHAR: // 如果是字符串类型
      sb.append("Varchar"); // 添加"Varchar"
      break; // 跳出switch
    }
  }
}
