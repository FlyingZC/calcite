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
package org.apache.calcite.linq4j.tree;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.Type;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Represents an expression that has a binary operator.
 */
// BinaryExpression类：表示具有二元操作符的表达式节点，用于表示各种二元运算表达式，如加、减、乘、除、比较运算等
// 该类继承自Expression基类，是LINQ4J表达式树系统中的重要组成部分
// 支持的操作符包括：Add(加)、Subtract(减)、Multiply(乘)、Divide(除)、AndAlso(逻辑与)、OrElse(逻辑或)、Equal(等于)、NotEqual(不等于)、GreaterThan(大于)、LessThan(小于)、GreaterThanOrEqual(大于等于)、LessThanOrEqual(小于等于)
public class BinaryExpression extends Expression {
  public final Expression expression0; // 二元运算的第一个操作数表达式（左操作数），可以是任意类型的Expression子类
  public final Expression expression1; // 二元运算的第二个操作数表达式（右操作数），可以是任意类型的Expression子类
  private final @Nullable Primitive primitive; // 操作数的原始类型包装类，用于优化数值运算，如果操作数不是基本类型则为null

  BinaryExpression(ExpressionType nodeType, Type type, Expression expression0,
      Expression expression1) {
    super(nodeType, type); // 调用父类Expression的构造方法，设置节点类型和返回值类型
    this.expression0 = requireNonNull(expression0, "expression0"); // 设置左操作数，使用requireNonNull确保expression0不为null，否则抛出NullPointerException
    this.expression1 = requireNonNull(expression1, "expression1"); // 设置右操作数，使用requireNonNull确保expression1不为null，否则抛出NullPointerException
    this.primitive = Primitive.of(expression0.getType()); // 根据左操作数的类型获取对应的Primitive枚举值，用于后续的数值运算优化，如果不是基本类型则为null
  }

  @Override public Expression accept(Shuttle visitor) {
    visitor = visitor.preVisit(this); // 调用访问者的preVisit方法，在访问子节点前进行预处理，可能返回修改后的访问者
    Expression expression0 = this.expression0.accept(visitor); // 让左操作数表达式接受访问者，可能返回修改后的表达式
    Expression expression1 = this.expression1.accept(visitor); // 让右操作数表达式接受访问者，可能返回修改后的表达式
    return visitor.visit(this, expression0, expression1); // 调用访问者的visit方法，传入当前二元表达式和两个操作数，返回可能被修改后的表达式
  }

  @Override public <R> R accept(Visitor<R> visitor) {
    return visitor.visit(this); // 调用类型为R的访问者的visit方法，让访问者处理当前二元表达式，返回类型为R的结果
  }

  @Override public Object evaluate(Evaluator evaluator) {
    switch (nodeType) { // 根据节点类型（操作符类型）进行分发，执行相应的二元运算
    case AndAlso: // 处理逻辑与运算（&&）
      return evaluateBoolean(evaluator, expression0) // 评估左操作数表达式，转换为布尔值
             && evaluateBoolean(evaluator, expression1); // 如果左操作数为true，则评估右操作数表达式，否则短路返回false
    case Add: // 处理加法运算（+）
      if (primitive == null) { // 如果操作数不是基本类型，无法进行数值运算
        throw cannotEvaluate(); // 抛出运行时异常，表示无法评估该表达式
      }
      switch (primitive) { // 根据基本类型进行分发，执行相应类型的加法运算
      case INT: // 处理int类型加法
        return evaluateInt(expression0, evaluator) + evaluateInt(expression1, evaluator); // 将两个操作数评估为int值后相加
      case SHORT: // 处理short类型加法
        return evaluateShort(expression0, evaluator) + evaluateShort(expression1, evaluator); // 将两个操作数评估为short值后相加
      case BYTE: // 处理byte类型加法
        return evaluateByte(expression0, evaluator) + evaluateByte(expression1, evaluator); // 将两个操作数评估为byte值后相加
      case FLOAT: // 处理float类型加法
        return evaluateFloat(expression0, evaluator) + evaluateFloat(expression1, evaluator); // 将两个操作数评估为float值后相加
      case DOUBLE: // 处理double类型加法
        return evaluateDouble(expression0, evaluator) + evaluateDouble(expression1, evaluator); // 将两个操作数评估为double值后相加
      case LONG: // 处理long类型加法
        return evaluateLong(expression0, evaluator) + evaluateLong(expression1, evaluator); // 将两个操作数评估为long值后相加
      default: // 其他基本类型不支持
        throw cannotEvaluate(); // 抛出运行时异常
      }
    case Divide: // 处理除法运算（/）
      if (primitive == null) { // 如果操作数不是基本类型，无法进行数值运算
        throw cannotEvaluate(); // 抛出运行时异常，表示无法评估该表达式
      }
      switch (primitive) { // 根据基本类型进行分发，执行相应类型的除法运算
      case INT: // 处理int类型除法
        return evaluateInt(expression0, evaluator) / evaluateInt(expression1, evaluator); // 将两个操作数评估为int值后相除
      case SHORT: // 处理short类型除法
        return evaluateShort(expression0, evaluator) / evaluateShort(expression1, evaluator); // 将两个操作数评估为short值后相除
      case BYTE: // 处理byte类型除法
        return evaluateByte(expression0, evaluator) / evaluateByte(expression1, evaluator); // 将两个操作数评估为byte值后相除
      case FLOAT: // 处理float类型除法
        return evaluateFloat(expression0, evaluator) / evaluateFloat(expression1, evaluator); // 将两个操作数评估为float值后相除
      case DOUBLE: // 处理double类型除法
        return evaluateDouble(expression0, evaluator) / evaluateDouble(expression1, evaluator); // 将两个操作数评估为double值后相除
      case LONG: // 处理long类型除法
        return evaluateLong(expression0, evaluator) / evaluateLong(expression1, evaluator); // 将两个操作数评估为long值后相除
      default: // 其他基本类型不支持
        throw cannotEvaluate(); // 抛出运行时异常
      }
    case Equal: // 处理相等比较运算（==）
      return Objects.equals(expression0.evaluate(evaluator), expression1.evaluate(evaluator)); // 评估两个操作数，使用Objects.equals进行相等比较，支持null值
    case GreaterThan: // 处理大于比较运算（>）
      if (primitive == null) { // 如果操作数不是基本类型，无法进行数值比较
        throw cannotEvaluate(); // 抛出运行时异常，表示无法评估该表达式
      }
      switch (primitive) { // 根据基本类型进行分发，执行相应类型的大于比较
      case INT: // 处理int类型比较
        return evaluateInt(expression0, evaluator) > evaluateInt(expression1, evaluator); // 评估两个int值，判断左操作数是否大于右操作数
      case SHORT: // 处理short类型比较
        return evaluateShort(expression0, evaluator) > evaluateShort(expression1, evaluator); // 评估两个short值，判断左操作数是否大于右操作数
      case BYTE: // 处理byte类型比较
        return evaluateByte(expression0, evaluator) > evaluateByte(expression1, evaluator); // 评估两个byte值，判断左操作数是否大于右操作数
      case FLOAT: // 处理float类型比较
        return evaluateFloat(expression0, evaluator) > evaluateFloat(expression1, evaluator); // 评估两个float值，判断左操作数是否大于右操作数
      case DOUBLE: // 处理double类型比较
        return evaluateDouble(expression0, evaluator) > evaluateDouble(expression1, evaluator); // 评估两个double值，判断左操作数是否大于右操作数
      case LONG: // 处理long类型比较
        return evaluateLong(expression0, evaluator) > evaluateLong(expression1, evaluator); // 评估两个long值，判断左操作数是否大于右操作数
      default: // 其他基本类型不支持
        throw cannotEvaluate(); // 抛出运行时异常
      }
    case GreaterThanOrEqual: // 处理大于等于比较运算（>=）
      if (primitive == null) { // 如果操作数不是基本类型，无法进行数值比较
        throw cannotEvaluate(); // 抛出运行时异常，表示无法评估该表达式
      }
      switch (primitive) { // 根据基本类型进行分发，执行相应类型的大于等于比较
      case INT: // 处理int类型比较
        return evaluateInt(expression0, evaluator) >= evaluateInt(expression1, evaluator); // 评估两个int值，判断左操作数是否大于等于右操作数
      case SHORT: // 处理short类型比较
        return evaluateShort(expression0, evaluator) >= evaluateShort(expression1, evaluator); // 评估两个short值，判断左操作数是否大于等于右操作数
      case BYTE: // 处理byte类型比较
        return evaluateByte(expression0, evaluator) >= evaluateByte(expression1, evaluator); // 评估两个byte值，判断左操作数是否大于等于右操作数
      case FLOAT: // 处理float类型比较
        return evaluateFloat(expression0, evaluator) >= evaluateFloat(expression1, evaluator); // 评估两个float值，判断左操作数是否大于等于右操作数
      case DOUBLE: // 处理double类型比较
        return evaluateDouble(expression0, evaluator) >= evaluateDouble(expression1, evaluator); // 评估两个double值，判断左操作数是否大于等于右操作数
      case LONG: // 处理long类型比较
        return evaluateLong(expression0, evaluator) >= evaluateLong(expression1, evaluator); // 评估两个long值，判断左操作数是否大于等于右操作数
      default: // 其他基本类型不支持
        throw cannotEvaluate(); // 抛出运行时异常
      }
    case LessThan: // 处理小于比较运算（<）
      if (primitive == null) { // 如果操作数不是基本类型，无法进行数值比较
        throw cannotEvaluate(); // 抛出运行时异常，表示无法评估该表达式
      }
      switch (primitive) { // 根据基本类型进行分发，执行相应类型的小于比较
      case INT: // 处理int类型比较
        return evaluateInt(expression0, evaluator) < evaluateInt(expression1, evaluator); // 评估两个int值，判断左操作数是否小于右操作数
      case SHORT: // 处理short类型比较
        return evaluateShort(expression0, evaluator) < evaluateShort(expression1, evaluator); // 评估两个short值，判断左操作数是否小于右操作数
      case BYTE: // 处理byte类型比较
        return evaluateByte(expression0, evaluator) < evaluateByte(expression1, evaluator); // 评估两个byte值，判断左操作数是否小于右操作数
      case FLOAT: // 处理float类型比较
        return evaluateFloat(expression0, evaluator) < evaluateFloat(expression1, evaluator); // 评估两个float值，判断左操作数是否小于右操作数
      case DOUBLE: // 处理double类型比较
        return evaluateDouble(expression0, evaluator) < evaluateDouble(expression1, evaluator); // 评估两个double值，判断左操作数是否小于右操作数
      case LONG: // 处理long类型比较
        return evaluateLong(expression0, evaluator) < evaluateLong(expression1, evaluator); // 评估两个long值，判断左操作数是否小于右操作数
      default: // 其他基本类型不支持
        throw cannotEvaluate(); // 抛出运行时异常
      }
    case LessThanOrEqual: // 处理小于等于比较运算（<=）
      if (primitive == null) { // 如果操作数不是基本类型，无法进行数值比较
        throw cannotEvaluate(); // 抛出运行时异常，表示无法评估该表达式
      }
      switch (primitive) { // 根据基本类型进行分发，执行相应类型的小于等于比较
      case INT: // 处理int类型比较
        return evaluateInt(expression0, evaluator) <= evaluateInt(expression1, evaluator); // 评估两个int值，判断左操作数是否小于等于右操作数
      case SHORT: // 处理short类型比较
        return evaluateShort(expression0, evaluator) <= evaluateShort(expression1, evaluator); // 评估两个short值，判断左操作数是否小于等于右操作数
      case BYTE: // 处理byte类型比较
        return evaluateByte(expression0, evaluator) <= evaluateByte(expression1, evaluator); // 评估两个byte值，判断左操作数是否小于等于右操作数
      case FLOAT: // 处理float类型比较
        return evaluateFloat(expression0, evaluator) <= evaluateFloat(expression1, evaluator); // 评估两个float值，判断左操作数是否小于等于右操作数
      case DOUBLE: // 处理double类型比较
        return evaluateDouble(expression0, evaluator) <= evaluateDouble(expression1, evaluator); // 评估两个double值，判断左操作数是否小于等于右操作数
      case LONG: // 处理long类型比较
        return evaluateLong(expression0, evaluator) <= evaluateLong(expression1, evaluator); // 评估两个long值，判断左操作数是否小于等于右操作数
      default: // 其他基本类型不支持
        throw cannotEvaluate(); // 抛出运行时异常
      }
    case Multiply: // 处理乘法运算（*）
      if (primitive == null) { // 如果操作数不是基本类型，无法进行数值运算
        throw cannotEvaluate(); // 抛出运行时异常，表示无法评估该表达式
      }
      switch (primitive) { // 根据基本类型进行分发，执行相应类型的乘法运算
      case INT: // 处理int类型乘法
        return evaluateInt(expression0, evaluator) * evaluateInt(expression1, evaluator); // 将两个操作数评估为int值后相乘
      case SHORT: // 处理short类型乘法
        return evaluateShort(expression0, evaluator) * evaluateShort(expression1, evaluator); // 将两个操作数评估为short值后相乘
      case BYTE: // 处理byte类型乘法
        return evaluateByte(expression0, evaluator) * evaluateByte(expression1, evaluator); // 将两个操作数评估为byte值后相乘
      case FLOAT: // 处理float类型乘法
        return evaluateFloat(expression0, evaluator) * evaluateFloat(expression1, evaluator); // 将两个操作数评估为float值后相乘
      case DOUBLE: // 处理double类型乘法
        return evaluateDouble(expression0, evaluator) * evaluateDouble(expression1, evaluator); // 将两个操作数评估为double值后相乘
      case LONG: // 处理long类型乘法
        return evaluateLong(expression0, evaluator) * evaluateLong(expression1, evaluator); // 将两个操作数评估为long值后相乘
      default: // 其他基本类型不支持
        throw cannotEvaluate(); // 抛出运行时异常
      }
    case NotEqual: // 处理不等比较运算（!=）
      return !Objects.equals(expression0.evaluate(evaluator), expression1.evaluate(evaluator)); // 评估两个操作数，使用Objects.equals进行相等比较后取反，支持null值
    case OrElse: // 处理逻辑或运算（||）
      return evaluateBoolean(evaluator, expression0) // 评估左操作数表达式，转换为布尔值
             || evaluateBoolean(evaluator, expression1); // 如果左操作数为false，则评估右操作数表达式，否则短路返回true
    case Subtract: // 处理减法运算（-）
      if (primitive == null) { // 如果操作数不是基本类型，无法进行数值运算
        throw cannotEvaluate(); // 抛出运行时异常，表示无法评估该表达式
      }
      switch (primitive) { // 根据基本类型进行分发，执行相应类型的减法运算
      case INT: // 处理int类型减法
        return evaluateInt(expression0, evaluator) - evaluateInt(expression1, evaluator); // 将两个操作数评估为int值后相减
      case SHORT: // 处理short类型减法
        return evaluateShort(expression0, evaluator) - evaluateShort(expression1, evaluator); // 将两个操作数评估为short值后相减
      case BYTE: // 处理byte类型减法
        return evaluateByte(expression0, evaluator) - evaluateByte(expression1, evaluator); // 将两个操作数评估为byte值后相减
      case FLOAT: // 处理float类型减法
        return evaluateFloat(expression0, evaluator) - evaluateFloat(expression1, evaluator); // 将两个操作数评估为float值后相减
      case DOUBLE: // 处理double类型减法
        return evaluateDouble(expression0, evaluator) - evaluateDouble(expression1, evaluator); // 将两个操作数评估为double值后相减
      case LONG: // 处理long类型减法
        return evaluateLong(expression0, evaluator) - evaluateLong(expression1, evaluator); // 将两个操作数评估为long值后相减
      default: // 其他基本类型不支持
        throw cannotEvaluate(); // 抛出运行时异常
      }
    default: // 不支持的操作符类型
      throw cannotEvaluate(); // 抛出运行时异常，表示无法评估该表达式
    }
  }

  @Override void accept(ExpressionWriter writer, int lprec, int rprec) {
    if (writer.requireParentheses(this, lprec, rprec)) { // 检查是否需要添加括号，根据优先级决定
      return; // 如果需要括号，writer已经处理，直接返回
    }
    expression0.accept(writer, lprec, nodeType.lprec); // 让左操作数接受writer，传入左优先级和操作符的左优先级
    writer.append(nodeType.op); // 追加操作符字符串到writer
    expression1.accept(writer, nodeType.rprec, rprec); // 让右操作数接受writer，传入操作符的右优先级和右优先级
  }

  private RuntimeException cannotEvaluate() {
    return new RuntimeException("cannot evaluate " + this + ", nodeType=" // 创建运行时异常，包含当前表达式信息
      + nodeType + ", primitive=" + primitive); // 包含节点类型和基本类型信息，用于调试
  }

  private static boolean evaluateBoolean(Evaluator evaluator, Expression expression) {
    return (Boolean) requireNonNull( // 评估表达式并确保结果不为null，然后强制转换为Boolean
        expression.evaluate(evaluator), // 使用evaluator评估表达式
        () -> "boolean expected, got null while evaluating " + expression); // 如果结果为null，抛出异常提示期望布尔值
  }

  private static Number evaluateNumber(Expression expression, Evaluator evaluator) {
    return (Number) requireNonNull( // 评估表达式并确保结果不为null，然后强制转换为Number
        expression.evaluate(evaluator), // 使用evaluator评估表达式
        () -> "number expected, got null while evaluating " + expression); // 如果结果为null，抛出异常提示期望数值
  }

  private static int evaluateInt(Expression expression, Evaluator evaluator) {
    return evaluateNumber(expression, evaluator).intValue(); // 评估表达式为Number，然后转换为int值返回
  }

  private static short evaluateShort(Expression expression, Evaluator evaluator) {
    return evaluateNumber(expression, evaluator).shortValue(); // 评估表达式为Number，然后转换为short值返回
  }

  private static long evaluateLong(Expression expression, Evaluator evaluator) {
    return evaluateNumber(expression, evaluator).longValue(); // 评估表达式为Number，然后转换为long值返回
  }

  private static byte evaluateByte(Expression expression, Evaluator evaluator) {
    return evaluateNumber(expression, evaluator).byteValue(); // 评估表达式为Number，然后转换为byte值返回
  }

  private static float evaluateFloat(Expression expression, Evaluator evaluator) {
    return evaluateNumber(expression, evaluator).floatValue(); // 评估表达式为Number，然后转换为float值返回
  }

  private static double evaluateDouble(Expression expression, Evaluator evaluator) {
    return evaluateNumber(expression, evaluator).doubleValue(); // 评估表达式为Number，然后转换为double值返回
  }

  @Override public boolean equals(@Nullable Object o) {
    if (this == o) { // 如果是同一个对象引用，直接返回true
      return true;
    }
    if (o == null || getClass() != o.getClass()) { // 如果对象为null或类型不同，返回false
      return false;
    }
    if (!super.equals(o)) { // 调用父类的equals方法，检查父类字段是否相等
      return false;
    }

    BinaryExpression that = (BinaryExpression) o; // 将对象转换为BinaryExpression类型

    if (!expression0.equals(that.expression0)) { // 比较左操作数表达式是否相等
      return false;
    }
    if (!expression1.equals(that.expression1)) { // 比较右操作数表达式是否相等
      return false;
    }
    if (primitive != that.primitive) { // 比较基本类型是否相等
      return false;
    }

    return true; // 所有字段都相等，返回true
  }

  @Override public int hashCode() {
    return Objects.hash(nodeType, type, expression0, expression1, primitive); // 使用Objects.hash方法计算哈希值，包含所有关键字段：节点类型、返回类型、左操作数、右操作数、基本类型
  }
}
