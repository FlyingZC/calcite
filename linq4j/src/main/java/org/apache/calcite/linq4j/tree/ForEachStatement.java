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
package org.apache.calcite.linq4j.tree; // linq4j包下的tree子包，包含用于表示LINQ表达式树的类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入空值检查注解，用于标记可能为null的参数

import java.util.Objects; // 导入Objects工具类，用于equals和hashCode计算

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于非空检查

/**
 * Represents a "for-each" loop, "for (T v : iterable) { f(v); }". // 表示一个for-each循环语句，类似于Java的增强for循环语法，用于遍历集合或数组
 *
 * ForEachStatement类是LINQ4J表达式树的一部分，用于表示增强for循环语句结构。
 * 它封装了for-each循环的三个核心组成部分：循环变量声明、可迭代对象和循环体。
 * 这个类在代码生成和表达式树转换过程中非常重要，特别是在将LINQ查询转换为Java代码时。
 *
 * 典型使用场景：
 * - 在LINQ查询转换过程中，表示对集合的迭代操作
 * - 用于代码生成，将抽象的查询表达式转换为具体的Java for-each循环
 * - 作为表达式树节点，支持访问者模式进行遍历和转换
 *
 * 示例代码结构：
 * for (String item : items) {
 *     System.out.println(item);
 * }
 *
 * 其中：
 * - item是parameter（参数表达式）
 * - items是iterable（可迭代表达式）
 * - System.out.println(item)是body（循环体语句）
 */
public class ForEachStatement extends Statement { // ForEachStatement继承自Statement基类，表示一个for-each循环语句节点
  public final ParameterExpression parameter; // 循环变量参数表达式，表示for-each循环中的迭代变量（如for (String item : items)中的item），final修饰表示不可变
  public final Expression iterable; // 可迭代对象表达式，表示要遍历的集合或数组（如for (String item : items)中的items），final修饰表示不可变
  public final Statement body; // 循环体语句，表示每次迭代要执行的代码块（如for循环的大括号内的所有语句），final修饰表示不可变

  /** Cached hash code for the expression. */ // 表达式的缓存哈希码，用于优化hashCode方法的性能，避免重复计算
  private int hash; // 私有成员变量，存储对象的哈希码缓存值，初始化为0，首次调用hashCode时计算并缓存

  public ForEachStatement(ParameterExpression parameter, Expression iterable, // 构造方法，创建一个新的ForEachStatement实例，接收循环变量、可迭代对象和循环体三个参数
      Statement body) { // 循环体语句参数
    super(ExpressionType.ForEach, Void.TYPE); // 调用父类Statement的构造方法，传入表达式类型为ForEach，返回类型为Void（for-each循环不返回值）
    this.parameter = requireNonNull(parameter, "parameter"); // 设置循环变量，使用requireNonNull确保parameter不为null，否则抛出NullPointerException
    this.iterable = requireNonNull(iterable, "iterable"); // 设置可迭代对象，使用requireNonNull确保iterable不为null，否则抛出NullPointerException
    this.body = requireNonNull(body, "body"); // 设置循环体，使用requireNonNull确保body不为null，否则抛出NullPointerException；注意body可以是空代码块，但不能是null
  }

  @Override public ForEachStatement accept(Shuttle shuttle) { // 重写父类Statement的accept方法，接收一个Shuttle访问者对象，用于遍历和转换表达式树
    shuttle = shuttle.preVisit(this); // 调用shuttle的preVisit方法进行前置访问，允许在访问子节点前进行预处理
    final Expression iterable1 = iterable.accept(shuttle); // 使用shuttle访问iterable表达式，可能返回转换后的新表达式（如重命名变量、优化表达式等）
    final Statement body1 = body.accept(shuttle); // 使用shuttle访问body语句，可能返回转换后的新语句（如内联变量、优化控制流等）
    return shuttle.visit(this, parameter, iterable1, body1); // 调用shuttle的visit方法，传入当前对象和可能被修改的子节点，返回可能被转换的新ForEachStatement对象
  }

  @Override public <R> R accept(Visitor<R> visitor) { // 重写父类Statement的accept方法，接收一个泛型Visitor访问者对象，用于只读访问表达式树
    return visitor.visit(this); // 调用visitor的visit方法，传入当前ForEachStatement对象，返回访问者计算的结果R
  }

  @Override void accept0(ExpressionWriter writer) { // 重写父类Statement的accept0方法，接收ExpressionWriter对象，将for-each循环语句转换为Java代码字符串
    writer.append("for (") // 向writer追加for循环的起始关键字和左括号
        .append(parameter.type) // 追加循环变量的类型（如String、Integer等）
        .append(" ") // 追加一个空格分隔类型和变量名
        .append(parameter) // 追加循环变量的名称（如item、element等）
        .append(" : ") // 追加冒号和空格，表示增强for循环的语法分隔符
        .append(iterable) // 追加要遍历的可迭代对象（如集合、数组等）
        .append(") ") // 追加右括号和空格，结束for循环的声明部分
        .append(Blocks.toBlock(body)); // 追加循环体，使用Blocks.toBlock确保body是一个代码块（如果body不是Block语句会自动包装成Block）
  }

  @Override public boolean equals(@Nullable Object o) { // 重写Object的equals方法，用于判断两个ForEachStatement对象是否相等，@Nullable注解表示参数o可能为null
    return this == o // 首先检查是否是同一个对象引用（地址相等），如果是则直接返回true
        || o instanceof ForEachStatement // 如果不是同一个对象，检查o是否是ForEachStatement类型的实例
        && parameter.equals(((ForEachStatement) o).parameter) // 检查循环变量parameter是否相等
        && iterable.equals(((ForEachStatement) o).iterable) // 检查可迭代对象iterable是否相等
        && body.equals(((ForEachStatement) o).body); // 检查循环体body是否相等
  }

  @Override public int hashCode() { // 重写Object的hashCode方法，返回对象的哈希码，用于在哈希集合（如HashMap、HashSet）中快速查找和比较
    int result = hash; // 首先尝试使用缓存的哈希码值，避免重复计算
    if (result == 0) { // 如果缓存的哈希码为0（表示尚未计算过），则进行计算
      result = // 使用Objects.hash方法计算哈希码，该方法会组合所有相关字段的哈希值
          Objects.hash(nodeType, type, parameter, iterable, body); // 将节点类型、返回类型、循环变量、可迭代对象和循环体的哈希值组合起来
      if (result == 0) { // 如果计算结果恰好为0（虽然概率很低），需要特殊处理
        result = 1; // 将哈希码设置为1，避免与未计算状态混淆（0表示未计算，1表示计算结果为0的特殊情况）
      }
      hash = result; // 将计算好的哈希码缓存到hash字段中，下次调用hashCode时直接返回缓存值，提高性能
    }
    return result; // 返回缓存或计算得到的哈希码
  }
}
