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
package org.apache.calcite.linq4j.tree; // 声明包路径，该类属于org.apache.calcite.linq4j.tree包，是LINQ4J表达式树的一部分

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空类型注解，用于标记可能为null的参数或返回值

import java.lang.reflect.Modifier; // 导入Java反射的Modifier类，用于处理类、方法、字段的访问修饰符
import java.lang.reflect.Type; // 导入Java反射的Type接口，表示Java类型
import java.util.Iterator; // 导入迭代器接口，用于遍历集合
import java.util.List; // 导入List接口，表示有序集合
import java.util.Objects; // 导入Objects工具类，提供equals、hashCode等静态方法

import static java.util.Objects.requireNonNull; // 静态导入Objects.requireNonNull方法，用于非空校验

/**
 * Declaration of a constructor.
 * 构造器声明类：用于表示Java类的构造函数声明，是LINQ4J表达式树的一部分
 * 该类封装了构造函数的所有信息，包括修饰符、返回类型(即构造器所属的类类型)、参数列表和方法体
 * 继承自MemberDeclaration，表示它是一个成员声明
 */
public class ConstructorDeclaration extends MemberDeclaration {
  public final int modifier; // 构造函数的访问修饰符（如public、private、protected等），使用Java反射Modifier类的常量表示
  public final Type resultType; // 构造函数的返回类型，在构造器中这实际上就是构造器所属的类类型
  public final List<ParameterExpression> parameters; // 构造函数的参数列表，每个参数都是一个ParameterExpression表达式对象
  public final BlockStatement body; // 构造函数的方法体，是一个块语句，包含构造函数执行的代码
  /** Cached hash code for the expression. */ // 缓存的哈希码，用于优化equals和hashCode方法的性能
  private int hash;

  public ConstructorDeclaration(int modifier, Type declaredAgainst, // 构造函数：创建一个新的构造器声明对象，modifier是访问修饰符，declaredAgainst是构造器所属的类类型
      List<ParameterExpression> parameters, BlockStatement body) { // parameters是参数列表，body是方法体
    this.modifier = modifier; // 保存构造函数的访问修饰符
    this.resultType = requireNonNull(declaredAgainst, "declaredAgainst"); // 保存构造器所属的类类型，使用requireNonNull确保非空
    this.parameters = requireNonNull(parameters, "parameters"); // 保存参数列表，使用requireNonNull确保非空
    this.body = requireNonNull(body, "body"); // 保存方法体，使用requireNonNull确保非空
  }

  @Override public MemberDeclaration accept(Shuttle shuttle) { // 接受一个Shuttle访问者，用于遍历和转换表达式树，返回转换后的成员声明
    shuttle = shuttle.preVisit(this); // 调用shuttle的preVisit方法进行前置访问处理
    // do not visit parameters // 不访问参数列表（参数表达式不需要转换）
    final BlockStatement body = this.body.accept(shuttle); // 使用shuttle访问并转换方法体，生成新的块语句
    return shuttle.visit(this, body); // 调用shuttle的visit方法访问当前构造器声明，并返回转换后的结果
  }

  @Override public <R> R accept(Visitor<R> visitor) { // 接受一个类型为R的泛型访问者，用于访问表达式树并返回指定类型的结果
    return visitor.visit(this); // 调用visitor的visit方法访问当前构造器声明，并返回访问结果
  }

  @Override public void accept(ExpressionWriter writer) { // 接受一个表达式写入器，用于将构造器声明转换为Java源代码字符串并写入输出流
    String modifiers = Modifier.toString(modifier); // 将修饰符整数转换为字符串（如"public"、"private"等）
    writer.append(modifiers); // 将修饰符字符串写入输出流
    if (!modifiers.isEmpty()) { // 如果修饰符字符串不为空
      writer.append(' '); // 在修饰符后添加一个空格
    }
    //noinspection unchecked // 抑制未检查的类型转换警告
    writer
        .append(resultType) // 写入返回类型（即构造器所属的类名）
        .list("(", ", ", ")", // 写入参数列表，使用括号包围，参数之间用逗号分隔
            () -> (Iterator) parameters.stream().map(parameter -> { // 使用流处理每个参数，生成参数声名字符串
              final String modifiers1 = // 获取当前参数的修饰符字符串
                  Modifier.toString(parameter.modifier);
              return modifiers1 + (modifiers1.isEmpty() ? "" : " ") // 如果有修饰符则添加空格
                  + Types.className(parameter.getType()) + " " // 添加参数类型和空格
                  + parameter.name; // 添加参数名
            }).iterator()) // 将流转换为迭代器
        .append(' ').append(body); // 添加空格和方法体
    writer.newlineAndIndent(); // 写入换行并缩进
  }

  @Override public boolean equals(@Nullable Object o) { // 重写equals方法，用于比较两个构造器声明对象是否相等
    if (this == o) { // 如果是同一个对象引用
      return true; // 直接返回true
    }
    if (o == null || getClass() != o.getClass()) { // 如果对象为null或类型不同
      return false; // 返回false
    }

    ConstructorDeclaration that = (ConstructorDeclaration) o; // 将对象强制转换为ConstructorDeclaration类型
    return modifier == that.modifier // 比较修饰符是否相等
        && body.equals(that.body) // 比较方法体是否相等
        && parameters.equals(that.parameters) // 比较参数列表是否相等
        && resultType.equals(that.resultType); // 比较返回类型是否相等
  }

  @Override public int hashCode() { // 重写hashCode方法，用于支持基于哈希的集合操作，采用延迟计算和缓存策略
    int result = hash; // 尝试使用缓存的哈希码
    if (result == 0) { // 如果缓存为0（表示尚未计算或计算结果为0）
      result = Objects.hash(modifier, resultType, parameters, body); // 使用Objects.hash方法计算所有字段的哈希码
      if (result == 0) { // 如果计算结果恰好为0
        result = 1; // 将哈希码设置为1，避免与未计算状态混淆
      }
      hash = result; // 将计算结果缓存到hash字段
    }
    return result; // 返回哈希码
  }
}
