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
// 声明包名，表示该类属于 org.apache.calcite.linq4j.tree 包，这是 Calcite LINQ4J 框架中用于表示表达式树的包
package org.apache.calcite.linq4j.tree;

// 导入 CheckerFramework 的空值检查注解，用于标记可能为 null 的参数，帮助静态分析工具检测潜在的空指针问题
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入 Java 的 Objects 工具类，提供用于对象操作的静态方法，如 equals、hashCode、hash 等
import java.util.Objects;

/**
 * Represents a {@code throw} statement.
 * 表示一个 throw 语句，用于在代码中抛出异常
 * 这个类是 LINQ4J 表达式树的一部分，用于以面向对象的方式表示 Java 代码中的 throw 语句
 * 通过这个类，可以在运行时动态构建和操作 throw 语句，实现代码生成、转换等功能
 */
// 定义 ThrowStatement 类，继承自 Statement 基类，表示一个 throw 语句节点
public class ThrowStatement extends Statement {
  // 声明一个公共的 final 成员变量，表示要抛出的异常表达式
  // final 表示该字段一旦初始化就不能被修改，保证 throw 语句的不可变性
  // Expression 类型表示这是一个表达式，可以是任何类型的表达式，通常是一个创建异常对象的表达式
  public final Expression expression;

  // 构造方法：创建一个 ThrowStatement 实例
  // 参数：expression - 要抛出的异常表达式，例如 new RuntimeException("error")
  // 该构造方法调用父类 Statement 的构造方法，传入 ExpressionType.Throw 表示这是一个 throw 语句，Void.TYPE 表示返回类型为 void
  public ThrowStatement(Expression expression) {
    super(ExpressionType.Throw, Void.TYPE); // 调用父类构造方法，设置节点类型为 Throw，返回类型为 void
    this.expression = expression; // 将传入的异常表达式赋值给成员变量
  }

  // 接受一个 Shuttle 访问者，用于转换表达式树中的节点
  // Shuttle 是一种访问者模式，可以遍历并转换表达式树中的节点
  // 返回值：转换后的 Statement 对象，可能是新的 ThrowStatement，也可能是其他类型的 Statement
  @Override public Statement accept(Shuttle shuttle) {
    shuttle = shuttle.preVisit(this); // 在访问节点之前调用 preVisit 方法，让 Shuttle 有机会在访问前做预处理
    Expression expression = this.expression.accept(shuttle); // 递归地让 Shuttle 访问并转换异常表达式，得到可能转换后的表达式
    return shuttle.visit(this, expression); // 调用 Shuttle 的 visit 方法，传入当前节点和转换后的表达式，让 Shuttle 完成访问并返回转换结果
  }

  // 接受一个 Visitor 访问者，用于遍历表达式树中的节点
  // Visitor 是另一种访问者模式，主要用于读取和分析表达式树，而不是转换
  // 泛型 R 表示访问者的返回类型
  // 返回值：Visitor 处理后的结果，类型由 Visitor 决定
  @Override public <R> R accept(Visitor<R> visitor) {
    return visitor.visit(this); // 调用 Visitor 的 visit 方法，传入当前节点，让 Visitor 处理并返回结果
  }

  // 接受一个 ExpressionWriter，用于将当前节点写入输出流，生成对应的 Java 代码
  // 这是一个内部方法，用于实现表达式树的代码生成功能
  // 参数：writer - 表达式写入器，用于构建输出代码
  @Override void accept0(ExpressionWriter writer) {
    writer.append("throw ").append(expression).append(';').newlineAndIndent(); // 向写入器追加 "throw " 关键字，然后追加异常表达式，最后添加分号和换行缩进，生成完整的 throw 语句
  }

  // 重写 equals 方法，用于比较两个 ThrowStatement 对象是否相等
  // 参数：o - 要比较的对象，使用 @Nullable 注解表示可能为 null
  // 返回值：如果相等返回 true，否则返回 false
  @Override public boolean equals(@Nullable Object o) {
    if (this == o) { // 首先检查是否是同一个对象引用
      return true; // 如果是同一个引用，直接返回 true
    }
    if (o == null || getClass() != o.getClass()) { // 检查对象是否为 null 或类型是否相同
      return false; // 如果为 null 或类型不同，返回 false
    }
    if (!super.equals(o)) { // 调用父类的 equals 方法比较父类部分是否相等
      return false; // 如果父类部分不相等，返回 false
    }

    ThrowStatement that = (ThrowStatement) o; // 将对象强转为 ThrowStatement 类型

    if (expression != null ? !expression.equals(that.expression) : that // 比较异常表达式是否相等
        .expression != null) { // 如果当前 expression 不为 null 且不等于对方的 expression，或者当前 expression 为 null 但对方的 expression 不为 null
      return false; // 返回 false 表示不相等
    }

    return true; // 所有比较都通过，返回 true 表示相等
  }

  // 重写 hashCode 方法，用于计算 ThrowStatement 对象的哈希值
  // 返回值：对象的哈希码，用于哈希表等数据结构
  // 使用 Objects.hash 方法组合多个字段的哈希值，确保相等的对象有相同的哈希码
  @Override public int hashCode() {
    return Objects.hash(nodeType, type, expression); // 使用 Objects.hash 方法计算 nodeType、type 和 expression 的组合哈希值
  }
}
