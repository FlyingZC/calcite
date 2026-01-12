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
package org.apache.calcite.linq4j.tree; // 包声明，定义该类所属的包为org.apache.calcite.linq4j.tree，属于LINQ4J树形表达式结构包

import org.checkerframework.checker.nullness.qual.Nullable; // 导入注解，用于标记可空对象，帮助静态分析工具检测空指针

import java.lang.reflect.Type; // 导入Type类，用于表示Java类型，支持泛型类型信息
import java.util.Objects; // 导入Objects工具类，提供对象操作方法如equals、hashCode、hash等

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于非空校验

/**
 * Represents an expression that has a ternary operator. // 类文档注释：表示一个包含三元运算符的表达式
 * 三元表达式是LINQ4J表达式树中的一种特殊表达式类型，包含三个子表达式和两个运算符
 * 典型应用场景包括条件运算符(?:)等需要三个操作数的表达式
 * 该类继承自Expression基类，是表达式树节点体系的一部分
 */
public class TernaryExpression extends Expression { // 定义TernaryExpression类，继承自Expression基类，表示三元表达式节点
  public final Expression expression0; // 第一个表达式成员变量，使用public final修饰，表示三元表达式的第一个操作数，通常是条件表达式或左操作数
  public final Expression expression1; // 第二个表达式成员变量，使用public final修饰，表示三元表达式的第二个操作数，通常是条件为真时的结果或中间操作数
  public final Expression expression2; // 第三个表达式成员变量，使用public final修饰，表示三元表达式的第三个操作数，通常是条件为假时的结果或右操作数

  TernaryExpression(ExpressionType nodeType, Type type, Expression expression0, // 构造方法开始，接收表达式类型、返回类型和三个表达式参数
      Expression expression1, Expression expression2) { // 构造方法参数续行，接收第二和第三个表达式参数
    super(nodeType, type); // 调用父类Expression的构造方法，初始化节点类型和返回类型
    this.expression0 = requireNonNull(expression0, "expression0"); // 初始化第一个表达式，使用requireNonNull进行非空校验，如果为null抛出NullPointerException
    this.expression1 = requireNonNull(expression1, "expression1"); // 初始化第二个表达式，使用requireNonNull进行非空校验，如果为null抛出NullPointerException
    this.expression2 = requireNonNull(expression2, "expression2"); // 初始化第三个表达式，使用requireNonNull进行非空校验，如果为null抛出NullPointerException
  } // 构造方法结束

  @Override public Expression accept(Shuttle shuttle) { // 重写accept方法，接收Shuttle访问器对象，用于遍历和转换表达式树
    shuttle = shuttle.preVisit(this); // 调用shuttle的preVisit方法进行前置访问，允许在访问子节点前进行预处理
    Expression expression0 = this.expression0.accept(shuttle); // 让第一个表达式接受shuttle访问，可能返回转换后的新表达式
    Expression expression1 = this.expression1.accept(shuttle); // 让第二个表达式接受shuttle访问，可能返回转换后的新表达式
    Expression expression2 = this.expression2.accept(shuttle); // 让第三个表达式接受shuttle访问，可能返回转换后的新表达式
    return shuttle.visit(this, expression0, expression1, expression2); // 调用shuttle的visit方法，传入当前对象和三个子表达式，返回可能被修改后的新表达式
  } // accept方法结束

  @Override public <R> R accept(Visitor<R> visitor) { // 重写accept方法，接收泛型Visitor访问器对象，用于带返回值的表达式树访问
    return visitor.visit(this); // 调用visitor的visit方法，传入当前TernaryExpression对象，返回访问器定义的返回值类型R
  } // accept方法结束

  @Override void accept(ExpressionWriter writer, int lprec, int rprec) { // 重写accept方法，接收ExpressionWriter用于将表达式写入输出流，lprec和rprec分别表示左右上下文优先级
    if (writer.requireParentheses(this, lprec, rprec)) { // 检查是否需要添加括号，根据当前表达式和上下文优先级判断
      return; // 如果需要括号且已由writer处理，则直接返回
    } // if语句结束
    expression0.accept(writer, lprec, nodeType.lprec); // 将第一个表达式写入writer，使用左上下文优先级和当前节点类型的左优先级
    writer.append(nodeType.op); // 写入第一个运算符（op），通常是三元表达式的问号或其他分隔符
    expression1.accept(writer, nodeType.rprec, nodeType.lprec); // 将第二个表达式写入writer，使用当前节点类型的右优先级和左优先级
    writer.append(nodeType.op2); // 写入第二个运算符（op2），通常是三元表达式的冒号或其他分隔符
    expression2.accept(writer, nodeType.rprec, rprec); // 将第三个表达式写入writer，使用当前节点类型的右优先级和右上下文优先级
  } // accept方法结束

  @Override public boolean equals(@Nullable Object o) { // 重写equals方法，用于比较两个TernaryExpression对象是否相等，参数可为空
    if (this == o) { // 首先检查是否是同一个对象引用
      return true; // 如果是同一个对象，直接返回true
    } // if语句结束
    if (o == null || getClass() != o.getClass()) { // 检查对象是否为null或类型是否匹配
      return false; // 如果对象为null或类型不同，返回false
    } // if语句结束
    if (!super.equals(o)) { // 调用父类的equals方法检查父类部分是否相等
      return false; // 如果父类部分不相等，返回false
    } // if语句结束

    TernaryExpression that = (TernaryExpression) o; // 将对象o强制转换为TernaryExpression类型

    if (!expression0.equals(that.expression0)) { // 比较第一个表达式是否相等
      return false; // 如果不相等，返回false
    } // if语句结束
    if (!expression1.equals(that.expression1)) { // 比较第二个表达式是否相等
      return false; // 如果不相等，返回false
    } // if语句结束
    if (!expression2.equals(that.expression2)) { // 比较第三个表达式是否相等
      return false; // 如果不相等，返回false
    } // if语句结束

    return true; // 所有比较都通过，返回true表示对象相等
  } // equals方法结束

  @Override public int hashCode() { // 重写hashCode方法，用于生成对象的哈希码，与equals方法保持一致
    return Objects.hash(nodeType, type, expression0, expression1, expression2); // 使用Objects.hash方法，将节点类型、返回类型和三个表达式组合生成哈希码
  } // hashCode方法结束
} // TernaryExpression类结束
