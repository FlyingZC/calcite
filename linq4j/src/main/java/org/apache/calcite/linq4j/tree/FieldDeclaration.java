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
 */ // Apache许可证声明，定义了代码的使用权限和限制
package org.apache.calcite.linq4j.tree; // 声明当前类所属的包，属于Calcite的LINQ4J模块的tree子包

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的注解，用于标记可能为null的值，帮助进行空值检查

import java.lang.reflect.Modifier; // 导入Java反射包中的Modifier类，用于处理访问修饰符（如public、private、static等）
import java.util.Objects; // 导入Java工具类Objects，提供了一些对象操作的静态方法，如equals、hashCode等

import static java.util.Objects.requireNonNull; // 静态导入Objects.requireNonNull方法，用于空值检查

/**
 * Declaration of a field.
 */ // 类的Javadoc注释：表示这是一个字段的声明类
public class FieldDeclaration extends MemberDeclaration { // FieldDeclaration类，继承自MemberDeclaration基类，用于表示Java类中的字段声明
  public final int modifier; // 字段的访问修饰符，使用整数表示（如public=1、private=2、static=8等），final表示该字段不可变
  public final ParameterExpression parameter; // 字段的参数表达式，包含字段的类型和名称信息，ParameterExpression封装了字段的元数据
  public final @Nullable Expression initializer; // 字段的初始化表达式，可能为null（表示没有初始化表达式），@Nullable注解表示该字段可以为空

  public FieldDeclaration(int modifier, ParameterExpression parameter, // 构造方法，用于创建字段声明对象，接收三个参数：修饰符、参数表达式和初始化表达式
      @Nullable Expression initializer) { // 构造方法的第二个参数，初始化表达式，可以为null
    this.modifier = modifier; // 将传入的修饰符参数赋值给实例变量modifier，保存字段的访问修饰符信息
    this.parameter = requireNonNull(parameter, "parameter"); // 使用requireNonNull方法检查parameter参数是否为null，如果为null则抛出NullPointerException，确保字段参数不为空
    this.initializer = initializer; // 将传入的初始化表达式参数赋值给实例变量initializer，保存字段的初始化逻辑
  } // 构造方法结束，完成FieldDeclaration对象的初始化

  @Override public MemberDeclaration accept(Shuttle shuttle) { // 重写accept方法，接受Shuttle访问者对象，用于遍历和转换表达式树，返回转换后的MemberDeclaration
    shuttle = shuttle.preVisit(this); // 调用Shuttle的preVisit方法进行前置访问，允许在正式访问前进行预处理
    // do not visit parameter - visit may not return a ParameterExpression // 注释说明：不访问parameter参数，因为访问可能不会返回ParameterExpression类型
    final Expression initializer = // 声明一个final变量initializer，用于存储处理后的初始化表达式
        this.initializer == null ? null : this.initializer.accept(shuttle); // 如果当前初始化表达式不为null，则调用accept方法让Shuttle访问者处理它，否则保持为null
    return shuttle.visit(this, initializer); // 调用Shuttle的visit方法，传入当前FieldDeclaration对象和处理后的initializer，让访问者完成访问并返回结果
  } // accept方法结束，返回经过Shuttle访问者处理后的MemberDeclaration对象

  @Override public <R> R accept(Visitor<R> visitor) { // 重写accept方法，接受泛型Visitor访问者对象，用于访问表达式树并返回类型为R的结果
    return visitor.visit(this); // 调用Visitor的visit方法，传入当前FieldDeclaration对象，让访问者执行访问操作并返回结果
  } // accept方法结束，返回Visitor访问者处理后的结果

  @Override public void accept(ExpressionWriter writer) { // 重写accept方法，接受ExpressionWriter对象，用于将字段声明写入输出流
    String modifiers = Modifier.toString(modifier); // 使用Modifier类的toString方法，将整数形式的修饰符转换为字符串形式（如"public static final"）
    writer.append(modifiers); // 将修饰符字符串追加到表达式写入器中
    if (!modifiers.isEmpty()) { // 如果修饰符字符串不为空（即字段有修饰符）
      writer.append(' '); // 则在修饰符后追加一个空格，与后续内容分隔
    } // if语句结束，完成修饰符的空格分隔处理
    writer.append(parameter.type).append(' ').append(parameter.name); // 追加字段的类型、空格和字段名到表达式写入器中，构建"类型 名称"格式
    if (initializer != null) { // 如果初始化表达式不为null（即字段有初始值）
      writer.append(" = ").append(initializer); // 则追加" = "和初始化表达式到表达式写入器中，构建" = 初始值"格式
    } // if语句结束，完成初始化表达式的写入
    writer.append(';'); // 追加分号，表示字段声明的结束
    writer.newlineAndIndent(); // 调用表达式写入器的换行和缩进方法，格式化输出，使下一行代码有正确的缩进
  } // accept方法结束，完成字段声明到表达式写入器的输出

  @Override public boolean equals(@Nullable Object o) { // 重写equals方法，用于比较两个FieldDeclaration对象是否相等，@Nullable表示参数o可以为null
    if (this == o) { // 如果当前对象与参数o是同一个对象（内存地址相同）
      return true; // 则直接返回true，表示相等
    } // if语句结束，完成对象引用相等的判断
    if (o == null || getClass() != o.getClass()) { // 如果参数o为null，或者o的类与当前对象的类不同
      return false; // 则返回false，表示不相等
    } // if语句结束，完成类型相等的判断

    FieldDeclaration that = (FieldDeclaration) o; // 将参数o强制转换为FieldDeclaration类型，赋值给that变量，准备进行字段比较
    return modifier == that.modifier // 比较两个对象的modifier字段是否相等
        && Objects.equals(initializer, that.initializer) // 比较两个对象的initializer字段是否相等（使用Objects.equals可以正确处理null值）
        && parameter.equals(that.parameter); // 比较两个对象的parameter字段是否相等（直接调用parameter的equals方法）
  } // equals方法结束，返回比较结果

  @Override public int hashCode() { // 重写hashCode方法，用于生成FieldDeclaration对象的哈希码，与equals方法保持一致
    return Objects.hash(modifier, parameter, initializer); // 使用Objects.hash方法，基于modifier、parameter和initializer三个字段生成哈希码
  } // hashCode方法结束，返回计算得到的哈希值
} // FieldDeclaration类结束
