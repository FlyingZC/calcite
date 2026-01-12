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
 * Represents an operation between an expression and a type. // 表示一个表达式与类型之间的操作，这是LINQ4J表达式树中的一个节点类，用于表示类型相关的二元表达式
 */ // 例如：instanceof操作、类型转换等涉及类型检查的操作都使用此类表示
public class TypeBinaryExpression extends Expression { // 继承自Expression基类，成为表达式树的一部分，表示类型二元表达式节点
  public final Expression expression; // 左侧操作数，即要被检查或转换的表达式，final表示初始化后不可修改
  @SuppressWarnings("HidingField") // 抑制编译器警告，因为type字段会隐藏父类Expression中的type字段
  public final Type type; // 右侧操作数，即要进行操作的目标类型，final表示初始化后不可修改

  public TypeBinaryExpression(ExpressionType nodeType, Expression expression, // 构造方法：创建一个新的类型二元表达式实例，用于表示表达式与类型之间的操作
      Type type) { // nodeType参数：指定操作的类型（如InstanceOf、TypeEqual等），expression参数：要被检查的表达式，type参数：目标类型
    super(nodeType, Boolean.TYPE); // 调用父类构造方法，设置节点类型和返回类型为布尔值（类型操作通常返回boolean）
    this.expression = requireNonNull(expression, "expression"); // 使用requireNonNull方法确保expression参数不为null，否则抛出NullPointerException
    this.type = type; // 直接赋值type字段，允许为null（某些类型操作可能允许null类型）
  } // 构造方法结束

  @Override public Expression accept(Shuttle shuttle) { // 接受表达式访问器(Shuttle)的访问，实现访问者模式，shuttle参数：表达式穿梭器，用于遍历和修改表达式树
    shuttle = shuttle.preVisit(this); // 调用穿梭器的preVisit方法进行前置访问，允许在访问子节点前进行处理
    Expression expression = this.expression.accept(shuttle); // 递归访问内部的expression表达式，让穿梭器处理子表达式，返回可能被修改后的表达式
    return shuttle.visit(this, expression); // 调用穿梭器的visit方法访问当前节点并传入处理后的表达式，返回访问后的结果表达式
  } // accept方法结束

  @Override public <R> R accept(Visitor<R> visitor) { // 接受通用访问器(Visitor)的访问，实现访问者模式，泛型R表示访问方法的返回类型
    return visitor.visit(this); // 调用访问器的visit方法访问当前TypeBinaryExpression节点，返回访问结果
  } // accept方法结束

  @Override void accept(ExpressionWriter writer, int lprec, int rprec) { // 接受表达式写入器的访问，用于将表达式转换为代码字符串，writer参数：表达式写入器，lprec参数：左侧上下文优先级，rprec参数：右侧上下文优先级
    if (writer.requireParentheses(this, lprec, rprec)) { // 检查是否需要添加括号，根据当前节点和左右优先级判断
      return; // 如果需要括号且已经由writer处理，则直接返回
    } // if语句结束
    expression.accept(writer, lprec, nodeType.lprec); // 递归写入左侧表达式，传入左侧优先级和当前节点类型的左优先级
    writer.append(nodeType.op); // 写入操作符（如instanceof关键字），nodeType.op存储了操作符的字符串表示
    writer.append(type); // 写入类型信息，将Type对象转换为字符串形式输出
  } // accept方法结束

  @Override public boolean equals(@Nullable Object o) { // 重写equals方法用于比较两个TypeBinaryExpression对象是否相等，o参数：要比较的对象，@Nullable表示可能为null
    if (this == o) { // 首先检查是否是同一个对象引用
      return true; // 如果是同一个引用，直接返回true
    } // if语句结束
    if (o == null || getClass() != o.getClass()) { // 检查对象是否为null或类类型是否不同
      return false; // 如果为null或不是同一个类，返回false
    } // if语句结束
    if (!super.equals(o)) { // 调用父类的equals方法比较父类部分的属性
      return false; // 如果父类部分不相等，返回false
    } // if语句结束

    TypeBinaryExpression that = (TypeBinaryExpression) o; // 将对象强制转换为TypeBinaryExpression类型
    return expression.equals(that.expression) // 比较expression字段是否相等
        && type.equals(that.type); // 同时比较type字段是否相等，两者都相等才返回true
  } // equals方法结束

  @Override public int hashCode() { // 重写hashCode方法，与equals方法保持一致，用于哈希集合中正确存储和查找对象
    return Objects.hash(nodeType, super.type, type, expression); // 使用Objects.hash方法组合所有关键字段的哈希值，确保相等的对象有相同的哈希码
  } // hashCode方法结束
}
