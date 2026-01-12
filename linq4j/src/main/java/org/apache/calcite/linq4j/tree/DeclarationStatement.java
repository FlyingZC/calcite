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
package org.apache.calcite.linq4j.tree; // 声明包名，该类属于Apache Calcite的LINQ4J树形结构包，用于表示表达式树

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空类型注解，用于标记可能为null的字段或参数

import java.lang.reflect.Modifier; // 导入Java反射中的Modifier类，用于处理访问修饰符（如public、private等）
import java.util.Objects; // 导入Objects工具类，用于equals和hashCode等操作

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空校验

/**
 * Expression that declares and optionally initializes a variable.
 * 表示声明并可选择初始化变量的表达式语句，是LINQ4J表达式树中表示变量声明的重要节点
 */
public class DeclarationStatement extends Statement { // 声明语句类，继承自Statement基类，用于表示Java中的变量声明语句（如 int x = 10;）
  public final int modifiers; // 变量的访问修饰符，使用整数编码（如Modifier.PUBLIC、Modifier.PRIVATE等），final表示不可修改
  public final ParameterExpression parameter; // 参数表达式对象，封装了变量的名称、类型等信息，是声明语句的核心组成部分
  public final @Nullable Expression initializer; // 初始化表达式，可选字段，用于表示变量的初始值（如 int x = 10 中的 10），可为null表示未初始化

  public DeclarationStatement(int modifiers, ParameterExpression parameter, // 构造方法，创建一个变量声明语句
      @Nullable Expression initializer) { // 参数：modifiers-访问修饰符，parameter-参数表达式（包含变量名和类型），initializer-初始化表达式（可选）
    super(ExpressionType.Declaration, Void.TYPE); // 调用父类Statement构造方法，指定表达式类型为Declaration，返回类型为void
    this.modifiers = modifiers; // 保存访问修饰符到成员变量
    this.parameter = requireNonNull(parameter, "parameter"); // 保存参数表达式，使用requireNonNull确保parameter不为null
    this.initializer = initializer; // 保存初始化表达式，可以为null
  }

  @Override public DeclarationStatement accept(Shuttle shuttle) { // 接受访问者模式的Shuttle对象，用于遍历和转换表达式树
    shuttle = shuttle.preVisit(this); // 预访问当前节点，允许Shuttle在访问子节点前进行预处理
    // do not visit parameter - visit may not return a ParameterExpression // 不访问parameter节点，因为访问后可能不返回ParameterExpression类型
    Expression initializer = this.initializer != null // 检查是否有初始化表达式
        ? this.initializer.accept(shuttle) // 如果有，递归访问初始化表达式节点，返回转换后的表达式
        : null; // 如果没有，保持为null
    return shuttle.visit(this, initializer); // 调用Shuttle的visit方法，传入当前节点和转换后的初始化表达式，返回新的DeclarationStatement
  }

  @Override public <R> R accept(Visitor<R> visitor) { // 接受访问者模式的Visitor对象，用于遍历表达式树并返回指定类型的结果
    return visitor.visit(this); // 调用Visitor的visit方法，传入当前DeclarationStatement节点，执行特定逻辑并返回结果
  }

  @Override void accept0(ExpressionWriter writer) { // 接受表达式写入器，将当前声明语句转换为Java源代码字符串
    final String modifiers = Modifier.toString(this.modifiers); // 将修饰符整数转换为字符串表示（如 "public final"）
    if (!modifiers.isEmpty()) { // 检查是否有修饰符
      writer.append(modifiers).append(' '); // 如果有，写入修饰符并添加空格
    }
    writer.append(parameter.type).append(' ').append(parameter.name); // 写入变量类型、空格和变量名（如 "int x"）
    if (initializer != null) { // 检查是否有初始化表达式
      writer.append(" = ").append(initializer); // 如果有，写入赋值符号和初始化表达式（如 " = 10"）
    }
    writer.append(';'); // 写入分号，结束声明语句
    writer.newlineAndIndent(); // 写入换行符并缩进，保持代码格式
  }

  public void accept2(ExpressionWriter writer, boolean withType) { // 另一种写入方法，用于在特定上下文中写入声明语句（如方法参数列表）
    if (withType) { // 检查是否需要写入类型信息
      final String modifiers = Modifier.toString(this.modifiers); // 将修饰符转换为字符串
      if (!modifiers.isEmpty()) { // 检查是否有修饰符
        writer.append(modifiers).append(' '); // 如果有，写入修饰符和空格
      }
      writer.append(parameter.type).append(' '); // 写入变量类型和空格
    } else { // 如果不需要类型信息
      writer.append(", "); // 写入逗号和空格，用于分隔多个参数
    }
    writer.append(parameter.name); // 写入变量名
    if (initializer != null) { // 检查是否有初始化表达式
      writer.append(" = ").append(initializer); // 如果有，写入赋值符号和初始化表达式
    }
  }

  @Override public boolean equals(@Nullable Object o) { // 重写equals方法，用于比较两个DeclarationStatement对象是否相等
    if (this == o) { // 检查是否是同一个对象引用
      return true; // 如果是，直接返回true
    }
    if (o == null || getClass() != o.getClass()) { // 检查对象是否为null或类型不同
      return false; // 如果是，返回false
    }
    if (!super.equals(o)) { // 调用父类equals方法检查父类字段是否相等
      return false; // 如果父类字段不相等，返回false
    }

    DeclarationStatement that = (DeclarationStatement) o; // 将对象强转为DeclarationStatement类型
    return modifiers == that.modifiers // 比较修饰符是否相等
        && Objects.equals(initializer, that.initializer) // 比较初始化表达式是否相等（使用Objects.equals处理null情况）
        && parameter.equals(that.parameter); // 比较参数表达式是否相等
  }

  @Override public int hashCode() { // 重写hashCode方法，用于在集合中正确使用对象
    return Objects.hash(nodeType, type, modifiers, parameter, initializer); // 使用Objects.hash方法基于所有关键字段生成哈希码
  }
}
