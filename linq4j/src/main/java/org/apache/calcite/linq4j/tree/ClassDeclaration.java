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
package org.apache.calcite.linq4j.tree; // 包声明，定义该类所属的包为org.apache.calcite.linq4j.tree

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的类型

import java.lang.reflect.Modifier; // 导入Modifier类，用于处理Java修饰符（如public、private等）
import java.lang.reflect.Type; // 导入Type接口，用于表示Java类型
import java.util.List; // 导入List接口，用于表示有序集合
import java.util.Objects; // 导入Objects工具类，提供对象操作的静态方法

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空校验

/**
 * Declaration of a class. // 类声明，用于表示一个Java类的声明信息
 * // 该类是linq4j框架中用于表示类声明的核心类，它封装了类的所有关键信息
 * // 包括修饰符、类名、继承关系、实现的接口以及类的成员（字段、方法等）
 * // 这个类主要用于代码生成和AST（抽象语法树）表示，是表达式树（Expression Tree）的重要组成部分
 * // 通过这个类，可以在运行时动态生成Java类定义，并将其转换为字符串形式的Java源代码
 */
public class ClassDeclaration extends MemberDeclaration { // ClassDeclaration类，继承自MemberDeclaration，表示一个类的声明
  public final int modifier; // 类的访问修饰符（如public、private、protected等），使用int类型存储，可以通过Modifier类解析
  public final String classClass = "class"; // 固定字符串"class"，表示这是一个类声明（而不是接口或枚举）
  public final String name; // 类的名称，必须非空，用于标识这个类
  public final List<MemberDeclaration> memberDeclarations; // 类的成员声明列表，包括字段、方法、构造函数等所有类成员
  public final @Nullable Type extended; // 该类继承的父类类型，可以为null（表示没有继承任何类，默认继承Object）
  public final List<Type> implemented; // 该类实现的接口类型列表，可以为空列表（表示没有实现任何接口）

  // 构造方法，用于创建ClassDeclaration实例
  // 参数说明：
  // - modifier: 类的访问修饰符，如Modifier.PUBLIC
  // - name: 类的名称，不能为null
  // - extended: 继承的父类类型，可以为null
  // - implemented: 实现的接口列表
  // - memberDeclarations: 类的成员声明列表
  public ClassDeclaration(int modifier, String name, @Nullable Type extended, // 构造方法开始，接收修饰符、类名、父类类型参数
      List<Type> implemented, List<MemberDeclaration> memberDeclarations) { // 继续接收接口列表和成员声明列表参数
    this.modifier = modifier; // 初始化类的修饰符字段
    this.name = requireNonNull(name, "name"); // 初始化类名，使用requireNonNull确保name不为null，否则抛出NullPointerException
    this.memberDeclarations = memberDeclarations; // 初始化成员声明列表
    this.extended = extended; // 初始化父类类型，可以为null
    this.implemented = implemented; // 初始化实现的接口列表
  } // 构造方法结束

  // 接受访问者模式中的ExpressionWriter，将类声明写入到输出流中
  // 这个方法用于将ClassDeclaration对象转换为字符串形式的Java源代码
  // 参数writer: ExpressionWriter对象，用于构建和输出Java代码
  @Override public void accept(ExpressionWriter writer) { // 重写accept方法，接收ExpressionWriter参数
    String modifiers = Modifier.toString(modifier); // 将int类型的修饰符转换为字符串形式（如"public"、"private static"等）
    writer.append(modifiers); // 将修饰符字符串写入输出流
    if (!modifiers.isEmpty()) { // 如果修饰符字符串不为空
      writer.append(' '); // 在修饰符后面添加一个空格，与后续内容分隔
    } // 结束if判断
    writer.append(classClass).append(' ').append(name); // 输出"class"关键字、空格和类名
    if (extended != null) { // 如果父类类型不为null（即该类继承了某个父类）
      writer.append(" extends ").append(extended); // 输出"extends"关键字、空格和父类类型名称
    } // 结束if判断
    if (!implemented.isEmpty()) { // 如果实现的接口列表不为空
      writer.list(" implements ", ", ", "", implemented); // 输出"implements"关键字，然后以逗号分隔列出所有接口
    } // 结束if判断
    writer.list(" {\n", "", "}", memberDeclarations); // 输出类的成员声明，前后分别加上" {\n"和"}"，表示类体
    writer.newlineAndIndent(); // 输出换行并保持缩进格式
  } // accept方法结束

  // 接受Shuttle访问者，用于遍历和转换表达式树
  // Shuttle是linq4j中用于遍历和转换表达式树的访问者模式实现
  // 这个方法允许对类声明及其成员进行转换操作
  // 参数shuttle: Shuttle对象，用于遍历和转换表达式树
  // 返回值: 转换后的ClassDeclaration对象
  @Override public ClassDeclaration accept(Shuttle shuttle) { // 重写accept方法，接收Shuttle参数，返回ClassDeclaration
    shuttle = shuttle.preVisit(this); // 调用shuttle的preVisit方法，在访问子节点前进行预处理
    final List<MemberDeclaration> members1 = // 声明一个新的成员声明列表，用于存储转换后的成员
        Expressions.acceptMemberDeclarations(memberDeclarations, shuttle); // 使用Expressions工具类对成员声明列表进行转换
    return shuttle.visit(this, members1); // 调用shuttle的visit方法，传入当前对象和转换后的成员列表，返回新的ClassDeclaration
  } // accept方法结束

  // 接受泛型Visitor访问者，用于访问类声明并返回自定义结果
  // 这是访问者模式的另一种实现，允许访问者返回任意类型的结果
  // 参数visitor: Visitor对象，泛型参数R表示返回值类型
  // 返回值: 由visit方法返回的自定义结果
  @Override public <R> R accept(Visitor<R> visitor) { // 重写accept方法，接收泛型Visitor参数，返回泛型类型R
    return visitor.visit(this); // 调用visitor的visit方法，传入当前对象，返回访问者定义的结果
  } // accept方法结束

  // 重写equals方法，用于比较两个ClassDeclaration对象是否相等
  // 比较依据包括：修饰符、类名、父类、实现的接口、成员声明列表
  // 参数o: 要比较的对象
  // 返回值: 如果相等返回true，否则返回false
  @Override public boolean equals(@Nullable Object o) { // 重写equals方法，接收Object参数，返回boolean
    if (this == o) { // 如果当前对象和参数对象是同一个引用
      return true; // 直接返回true
    } // 结束if判断
    if (o == null || getClass() != o.getClass()) { // 如果参数对象为null，或者类型不匹配
      return false; // 返回false
    } // 结束if判断

    ClassDeclaration that = (ClassDeclaration) o; // 将参数对象强制转换为ClassDeclaration类型

    if (modifier != that.modifier) { // 比较修饰符是否相同
      return false; // 如果修饰符不同，返回false
    } // 结束if判断
    if (!classClass.equals(that.classClass)) { // 比较classClass字段是否相同（虽然这个字段固定为"class"）
      return false; // 如果不同，返回false
    } // 结束if判断
    return Objects.equals(extended, that.extended) // 比较父类类型是否相同（使用Objects.equals处理null情况）
        && implemented.equals(that.implemented) // 比较实现的接口列表是否相同
        && memberDeclarations.equals(that.memberDeclarations) // 比较成员声明列表是否相同
        && name.equals(that.name); // 比较类名是否相同
  } // equals方法结束

  // 重写hashCode方法，用于计算ClassDeclaration对象的哈希值
  // 哈希值基于所有关键字段：修饰符、classClass、类名、成员声明、父类、实现的接口
  // 返回值: 对象的哈希码
  @Override public int hashCode() { // 重写hashCode方法，返回int类型的哈希码
    return Objects.hash(modifier, classClass, name, memberDeclarations, // 使用Objects.hash方法计算哈希值，传入所有关键字段
        extended, implemented); // 继续传入父类和实现的接口
  } // hashCode方法结束
} // ClassDeclaration类结束
