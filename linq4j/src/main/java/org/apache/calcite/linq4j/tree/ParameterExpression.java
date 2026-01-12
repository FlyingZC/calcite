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
package org.apache.calcite.linq4j.tree; // 声明包名，该类属于 org.apache.calcite.linq4j.tree 包，这是 LINQ4J 表达式树的核心包

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性检查注解，用于标记可能为 null 的返回值

import java.lang.reflect.Modifier; // 导入反射工具类，用于处理 Java 修饰符（如 public、private、final 等）
import java.lang.reflect.Type; // 导入 Type 接口，表示 Java 类型（包括泛型类型）
import java.util.concurrent.atomic.AtomicInteger; // 导入原子整数类，用于线程安全的计数器

import static com.google.common.base.Preconditions.checkArgument; // 导入 Google Guava 的前置条件检查方法，用于参数校验

import static java.util.Objects.requireNonNull; // 导入 Java Objects 类的 requireNonNull 方法，用于非空检查

/**
 * Represents a named parameter expression. // 表示一个命名参数表达式，这是 LINQ4J 表达式树中用于表示方法参数、局部变量等命名参数的节点类
 * ParameterExpression 是表达式树中的叶子节点，用于引用方法参数、lambda 表达式的参数或局部变量
 * 它在代码生成过程中会被转换为实际的 Java 变量引用
 * 例如：在 lambda 表达式 (x, y) -> x + y 中，x 和 y 就是 ParameterExpression
 */
public class ParameterExpression extends Expression { // ParameterExpression 类继承自 Expression 基类，是表达式树的一种具体实现
  private static final AtomicInteger SEQ = new AtomicInteger(); // 静态原子计数器，用于自动生成唯一的参数名称（如 p0, p1, p2...），保证参数名的唯一性

  public final int modifier; // 修饰符字段，存储 Java 修饰符（如 public、private、final、static 等），使用 int 位掩码表示，可通过 Modifier 类解析
  public final String name; // 参数名字段，存储参数的名称，用于在代码生成时引用该参数

  public ParameterExpression(Type type) { // 构造方法：仅指定类型的构造函数，用于创建默认修饰符（0）和自动生成名称的参数表达式
    this(0, type, "p" + SEQ.getAndIncrement()); // 调用完整构造函数，使用修饰符 0（无修饰符），指定类型，自动生成名称（p + 递增序列号）
  }

  public ParameterExpression(int modifier, Type type, String name) { // 完整构造方法：指定修饰符、类型和名称的构造函数，用于创建自定义参数表达式
    super(ExpressionType.Parameter, type); // 调用父类 Expression 的构造方法，指定表达式类型为 Parameter，并设置参数的类型
    checkArgument(Character.isJavaIdentifierStart(name.charAt(0)), // 参数校验：检查参数名称的第一个字符是否是有效的 Java 标识符起始字符（字母、下划线或美元符号）
        "parameter name should be valid java identifier: %s. " // 错误消息第一部分：提示参数名称应该是有效的 Java 标识符
            + "The first character is invalid.", // 错误消息第二部分：提示第一个字符无效
        name); // 错误消息的参数：显示无效的参数名称
    this.modifier = modifier; // 初始化修饰符字段，保存传入的修饰符值
    this.name = requireNonNull(name, "name"); // 初始化名称字段，使用 requireNonNull 确保名称不为 null，如果为 null 则抛出 NullPointerException
  }

  @Override public Expression accept(Shuttle shuttle) { // 重写 accept 方法：接受表达式访问器（Shuttle）的访问，用于遍历和转换表达式树
    return shuttle.visit(this); // 调用访问器的 visit 方法，将当前 ParameterExpression 对象传递给访问器，访问器可以对其进行转换或分析
  }

  @Override public <R> R accept(Visitor<R> visitor) { // 重写 accept 方法：接受泛型访问器（Visitor）的访问，用于对表达式树进行自定义操作
    return visitor.visit(this); // 调用访问器的 visit 方法，将当前 ParameterExpression 对象传递给访问器，返回访问器处理后的结果（类型 R）
  }

  @Override public @Nullable Object evaluate(Evaluator evaluator) { // 重写 evaluate 方法：在表达式求值时执行，用于获取参数表达式的实际值
    return evaluator.peek(this); // 从求值器（Evaluator）中获取当前参数的值，peek 方法表示从求值器的上下文中查找并返回该参数对应的值
  }

  @Override void accept(ExpressionWriter writer, int lprec, int rprec) { // 重写 accept 方法：接受表达式写入器（ExpressionWriter），用于将表达式转换为代码字符串
    writer.append(name); // 将参数名称写入表达式写入器，生成该参数的代码表示（如 "x" 或 "paramName"）
  }

  String declString() { // declString 方法：生成参数的声明字符串，用于在代码生成时输出参数的完整声明（包括修饰符、类型和名称）
    return declString(type); // 调用重载的 declString 方法，使用当前参数的类型生成声明字符串
  }

  String declString(Type type) { // 重载的 declString 方法：根据指定的类型生成参数的声明字符串
    final String modifiers = Modifier.toString(modifier); // 将修饰符整数值转换为字符串表示（如 "public final" 或 ""）
    return modifiers + (modifiers.isEmpty() ? "" : " ") + Types.className(type) // 拼接修饰符、空格（如果有修饰符）、类型名称和参数名称，生成完整的声明字符串
        + " " + name; // 继续拼接空格和参数名称，完成声明字符串
  }

  @Override public boolean equals(@Nullable Object o) { // 重写 equals 方法：判断两个 ParameterExpression 对象是否相等
    return this == o; // 使用引用相等性比较，只有当两个对象是同一个实例时才认为相等，这确保了参数表达式的唯一性
  }

  @Override public int hashCode() { // 重写 hashCode 方法：生成参数表达式的哈希码
    return System.identityHashCode(this); // 使用对象的系统标识哈希码，确保与 equals 方法的一致性（引用相等）
  }
}
