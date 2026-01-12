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
package org.apache.calcite.linq4j.tree; // 定义包名，该类属于org.apache.calcite.linq4j.tree包，是LINQ4J表达式树的一部分

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性检查注解，用于标记可能为null的参数

import java.util.Objects; // 导入Objects工具类，用于equals和hashCode方法

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空校验

/**
 * Represents a "while" statement. // 表示一个while循环语句，这是LINQ4J表达式树中表示while循环的节点类
 * 该类用于在表达式树中表示Java的while循环语句，包含循环条件和循环体两部分
 * WhileStatement继承自Statement基类，是表达式树中的一种语句类型
 * 它可以用于代码生成、表达式转换等场景，特别是在LINQ4J框架中将查询转换为Java代码时
 */
public class WhileStatement extends Statement { // 定义WhileStatement类，继承自Statement基类，表示while循环语句
  public final Expression condition; // 循环条件表达式，必须是boolean类型，当条件为true时继续执行循环体
  // 该字段是final的，表示一旦创建就不能修改，确保表达式树的不可变性
  public final Statement body; // 循环体语句，当条件为true时反复执行的语句块
  // 可以是单个语句，也可以是BlockStatement包含多个语句
  // 该字段是final的，表示一旦创建就不能修改，确保表达式树的不可变性

  public WhileStatement(Expression condition, Statement body) { // 构造方法，创建一个新的WhileStatement实例
    super(ExpressionType.While, Void.TYPE); // 调用父类Statement的构造方法，指定节点类型为While，返回类型为Void（while语句不返回值）
    this.condition = requireNonNull(condition, "condition"); // 初始化循环条件，使用requireNonNull确保condition不为null，否则抛出NullPointerException
    this.body = requireNonNull(body, "body"); // 初始化循环体，使用requireNonNull确保body不为null，否则抛出NullPointerException
  }

  @Override public Statement accept(Shuttle shuttle) { // 接受访问者模式中的Shuttle访问器，用于遍历和转换表达式树
    // Shuttle是一个表达式树遍历器，可以访问和修改表达式树中的节点
    shuttle = shuttle.preVisit(this); // 先进行前置访问，允许Shuttle在访问子节点前进行预处理，返回可能被修改的Shuttle实例
    final Expression condition1 = condition.accept(shuttle); // 让循环条件表达式接受Shuttle的访问，可能返回修改后的条件表达式
    final Statement body1 = body.accept(shuttle); // 让循环体语句接受Shuttle的访问，可能返回修改后的循环体语句
    return shuttle.visit(this, condition1, body1); // 调用Shuttle的visit方法处理WhileStatement节点，传入当前节点和可能被修改后的条件、循环体，返回新的WhileStatement实例
  }

  @Override public <R> R accept(Visitor<R> visitor) { // 接受访问者模式中的Visitor访问器，用于对表达式树进行特定操作
    // Visitor是一个泛型访问器，可以返回任意类型的结果R
    return visitor.visit(this); // 调用Visitor的visit方法处理WhileStatement节点，返回访问结果
    // 这是一个模板方法，具体的Visitor实现会定义如何处理WhileStatement节点
  }

  @Override void accept0(ExpressionWriter writer) { // 将while语句写入到ExpressionWriter中，用于生成Java代码字符串
    // ExpressionWriter是一个代码生成器，可以将表达式树转换为Java源代码字符串
    writer.append("while (").append(condition).append(") ").append( // 追加while关键字、左括号、条件表达式和右括号
        Blocks.toBlock(body)); // 将循环体语句转换为块（如果还不是块的话），并追加到代码中，形成完整的while语句
  }

  @Override public boolean equals(@Nullable Object o) { // 重写equals方法，用于比较两个WhileStatement对象是否相等
    if (this == o) { // 如果是同一个对象引用，直接返回true
      return true;
    }
    if (o == null || getClass() != o.getClass()) { // 如果o为null或者o的类型不是WhileStatement，返回false
      return false;
    }
    if (!super.equals(o)) { // 调用父类Statement的equals方法进行比较，如果父类不相等则返回false
      return false;
    }

    WhileStatement that = (WhileStatement) o; // 将o转换为WhileStatement类型
    return body.equals(that.body) // 比较循环体是否相等
        && condition.equals(that.condition); // 比较循环条件是否相等，只有两者都相等才返回true
  }

  @Override public int hashCode() { // 重写hashCode方法，用于支持基于WhileStatement的哈希集合操作
    return Objects.hash(nodeType, type, condition, body); // 使用Objects.hash方法计算哈希值，包含节点类型、返回类型、循环条件和循环体
    // 这样可以确保相等的WhileStatement对象具有相同的哈希码
  }
}
