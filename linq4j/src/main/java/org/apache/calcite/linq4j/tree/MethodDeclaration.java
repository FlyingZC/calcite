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
package org.apache.calcite.linq4j.tree; // 声明包名，该类属于 org.apache.calcite.linq4j.tree 包，是 LINQ4J 表达式树的一部分

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Checker Framework 的可空注解，用于静态空值检查

import java.lang.reflect.Modifier; // 导入 Modifier 类，用于处理方法的修饰符（如 public、private、static 等）
import java.lang.reflect.Type; // 导入 Type 接口，表示 Java 类型系统中的类型
import java.util.Iterator; // 导入 Iterator 接口，用于遍历集合
import java.util.List; // 导入 List 接口，表示有序集合
import java.util.Objects; // 导入 Objects 工具类，提供对象操作方法（如 equals、hashCode）

import static java.util.Objects.requireNonNull; // 静态导入 requireNonNull 方法，用于参数非空检查

/**
 * Declaration of a method. // 方法声明类，用于表示 Java 方法声明，是 LINQ4J 表达式树中方法声明的抽象表示
 * 该类继承自 MemberDeclaration，表示类成员声明的一种（方法声明）
 * 用于在代码生成和表达式树操作中表示方法的完整结构，包括修饰符、名称、返回类型、参数列表和方法体
 */
public class MethodDeclaration extends MemberDeclaration { // 定义 MethodDeclaration 类，继承自 MemberDeclaration 基类
  public final int modifier; // 方法的修饰符，使用 int 表示，可以是 public、private、protected、static、final 等的组合，通过 Modifier 类的常量进行位运算组合
  public final String name; // 方法的名称，存储为字符串，表示方法在类中的标识符
  public final Type resultType; // 方法的返回类型，使用 Type 接口表示，可以是基本类型、对象类型、泛型类型等
  public final List<ParameterExpression> parameters; // 方法的参数列表，使用 List 存储参数表达式，每个参数用一个 ParameterExpression 对象表示，参数按声明顺序排列
  public final BlockStatement body; // 方法体，使用 BlockStatement 表示，包含方法执行时的代码块，由一系列语句组成

  // 构造方法：创建一个方法声明对象
  // 参数：modifier - 方法修饰符，name - 方法名称，resultType - 返回类型，parameters - 参数列表，body - 方法体
  public MethodDeclaration(int modifier, String name, Type resultType, // 构造方法签名，接收方法的所有组成部分作为参数
      List<ParameterExpression> parameters, BlockStatement body) { // 构造方法参数续行，参数列表和方法体
    this.modifier = modifier; // 将传入的修饰符赋值给实例变量 modifier
    this.name = requireNonNull(name, "name"); // 验证 name 参数非空，如果为 null 抛出 NullPointerException，并赋值给实例变量 name
    this.resultType = requireNonNull(resultType, "resultType"); // 验证 resultType 参数非空，如果为 null 抛出 NullPointerException，并赋值给实例变量 resultType
    this.parameters = requireNonNull(parameters, "parameters"); // 验证 parameters 参数非空，如果为 null 抛出 NullPointerException，并赋值给实例变量 parameters
    this.body = requireNonNull(body, "body"); // 验证 body 参数非空，如果为 null 抛出 NullPointerException，并赋值给实例变量 body
  }

  // 接受访问者模式中的 Shuttle 对象，用于遍历和转换表达式树
  // Shuttle 是表达式树的转换器，可以修改表达式树的结构
  // 返回值：转换后的 MemberDeclaration 对象
  @Override public MemberDeclaration accept(Shuttle shuttle) { // 重写 accept 方法，接受 Shuttle 访问者进行表达式树转换
    shuttle = shuttle.preVisit(this); // 调用 shuttle 的 preVisit 方法，在访问当前节点前进行预处理，可能返回修改后的 shuttle
    // do not visit parameters // 注释说明：不访问参数列表，参数表达式不参与转换过程
    final BlockStatement body = this.body.accept(shuttle); // 接受 shuttle 访问方法体，对方法体进行转换，返回转换后的 BlockStatement
    return shuttle.visit(this, body); // 调用 shuttle 的 visit 方法，使用转换后的方法体创建新的 MethodDeclaration 对象并返回
  }

  // 接受访问者模式中的 Visitor 对象，用于遍历表达式树并执行特定操作
  // Visitor 是表达式树的访问者，可以收集信息或执行分析操作
  // 泛型 R：访问方法的返回类型，由具体的 Visitor 实现决定
  // 返回值：Visitor 访问后的结果
  @Override public <R> R accept(Visitor<R> visitor) { // 重写 accept 方法，接受 Visitor 访问者进行表达式树访问
    return visitor.visit(this); // 调用 visitor 的 visit 方法，将当前 MethodDeclaration 对象传递给 visitor 进行处理，并返回处理结果
  }

  // 接受 ExpressionWriter 对象，将方法声明转换为 Java 源代码字符串并输出
  // ExpressionWriter 是表达式树的代码生成器，用于生成可编译的 Java 代码
  // 该方法负责将方法声明格式化为标准的 Java 方法声明语法
  @Override public void accept(ExpressionWriter writer) { // 重写 accept 方法，接受 ExpressionWriter 进行代码生成
    String modifiers = Modifier.toString(modifier); // 将修饰符整数转换为字符串表示，例如 1 转换为 "public"，9 转换为 "public static"
    writer.append(modifiers); // 将修饰符字符串追加到输出流中
    if (!modifiers.isEmpty()) { // 检查修饰符字符串是否为空
      writer.append(' '); // 如果修饰符不为空，追加一个空格分隔符
    }
    //noinspection unchecked // 抑制未检查的类型转换警告，因为泛型类型擦除导致的类型转换无法在编译时检查
    writer // 链式调用，开始构建方法声明的各个部分
        .append(resultType) // 追加返回类型到输出流
        .append(' ') // 追加空格分隔符
        .append(name) // 追加方法名称到输出流
        .list("(", ", ", ")", // 使用 list 方法生成参数列表，参数用逗号分隔，整体用括号包围
            () -> (Iterator) parameters.stream().map(ParameterExpression::declString).iterator()) // 将参数表达式流转换为声明字符串的迭代器，每个参数调用 declString 方法生成声明字符串
        .append(' ') // 追加空格分隔符
        .append(body); // 追加方法体到输出流
    writer.newlineAndIndent(); // 在方法声明后添加换行和缩进，使生成的代码格式规范
  }

  // 重写 equals 方法，用于比较两个 MethodDeclaration 对象是否相等
  // 相等的条件：所有字段（modifier、name、resultType、parameters、body）都相等
  // 参数 o：要比较的对象
  // 返回值：如果相等返回 true，否则返回 false
  @Override public boolean equals(@Nullable Object o) { // 重写 equals 方法，使用 @Nullable 注解表示参数可以为 null
    if (this == o) { // 检查是否是同一个对象引用
      return true; // 如果是同一个引用，直接返回 true
    }
    if (o == null || getClass() != o.getClass()) { // 检查对象是否为 null 或类型是否相同
      return false; // 如果对象为 null 或类型不同，返回 false
    }

    MethodDeclaration that = (MethodDeclaration) o; // 将对象强制转换为 MethodDeclaration 类型
    return modifier == that.modifier // 比较 modifier 字段是否相等
        && body.equals(that.body) // 比较 body 字段是否相等
        && name.equals(that.name) // 比较 name 字段是否相等
        && parameters.equals(that.parameters) // 比较 parameters 字段是否相等
        && resultType.equals(that.resultType); // 比较 resultType 字段是否相等
  }

  // 重写 hashCode 方法，用于生成 MethodDeclaration 对象的哈希码
  // 哈希码基于所有字段（modifier、name、resultType、parameters、body）生成
  // 返回值：对象的哈希码整数值
  @Override public int hashCode() { // 重写 hashCode 方法
    return Objects.hash(modifier, name, resultType, parameters, body); // 使用 Objects.hash 方法基于所有字段生成哈希码，确保与 equals 方法一致
  }
}
