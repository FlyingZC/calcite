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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.linq4j.tree; // 包声明，该类属于org.apache.calcite.linq4j.tree包，这是LINQ4J项目中用于表示抽象语法树(AST)的包

import org.apache.calcite.linq4j.Ord; // 导入Ord类，用于为集合元素提供索引包装，方便在遍历时获取元素索引

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的字段或参数，进行空值检查

import java.util.List; // 导入List接口，用于存储声明语句列表
import java.util.Objects; // 导入Objects工具类，用于equals和hashCode方法中的对象比较和哈希计算

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空校验

/**
 * Represents an infinite loop. It can be exited with "break".
 * // 表示一个for循环语句，继承自Statement基类，是LINQ4J抽象语法树中表示for循环的节点
 * // 注意：文档注释说"无限循环"是不准确的，实际上它表示标准的for循环语句，包括初始化、条件、后置操作和循环体
 * // for循环的语法结构：for (初始化; 条件; 后置操作) { 循环体 }
 * // 该类用于在代码生成和转换过程中表示for循环语句，是LINQ4J表达式树的核心组成部分
 */
public class ForStatement extends Statement { // ForStatement类继承自Statement，表示一个for循环语句
  public final List<DeclarationStatement> declarations; // 循环初始化部分的声明语句列表，对应for循环的初始化部分，例如：int i = 0; 可以包含多个声明，也可能为空
  public final @Nullable Expression condition; // 循环条件表达式，对应for循环的条件部分，例如：i < 10; 可以为null表示无限循环
  public final @Nullable Expression post; // 循环后置操作表达式，对应for循环的每次迭代后执行的操作，例如：i++ 或 i += 2; 可以为null
  public final Statement body; // 循环体语句，对应for循环的循环体部分，可以是任意语句，通常是Block语句包含多条语句，也可以是单条语句
  /** Cached hash code for the expression. */
  private int hash; // 缓存的哈希码值，用于优化hashCode方法的性能，避免重复计算，采用延迟初始化策略

  public ForStatement(List<DeclarationStatement> declarations, // ForStatement构造方法，用于创建for循环语句对象
      @Nullable Expression condition, @Nullable Expression post, // 参数：condition-循环条件表达式，可以为null；post-后置操作表达式，可以为null
      Statement body) { // 参数：body-循环体语句，不能为null
    super(ExpressionType.For, Void.TYPE); // 调用父类Statement的构造方法，指定节点类型为For，返回类型为Void（因为for循环是语句不是表达式）
    this.declarations = // 初始化declarations字段
        requireNonNull(declarations, "declarations"); // 使用requireNonNull确保declarations不为null，"declarations"是错误提示信息，但允许列表为空
    this.condition = condition; // 初始化condition字段，可以为null（表示无限循环）
    this.post = post; // 初始化post字段，可以为null（表示没有后置操作）
    this.body = requireNonNull(body, "body"); // 使用requireNonNull确保body不为null，"body"是错误提示信息，循环体可以是空块但不能为null
  } // 构造方法结束

  @Override public ForStatement accept(Shuttle shuttle) { // 接受访问者模式中的Shuttle访问器，用于遍历和转换表达式树，返回转换后的ForStatement对象
    shuttle = shuttle.preVisit(this); // 调用shuttle的preVisit方法进行前置访问，允许在访问子节点前进行预处理，返回可能修改后的shuttle
    List<DeclarationStatement> decls1 = // 处理声明语句列表，使用Expressions工具类的acceptDeclarations方法批量处理
        Expressions.acceptDeclarations(declarations, shuttle); // 对每个声明语句应用shuttle访问器进行转换，返回转换后的声明语句列表
    final Expression condition1 = // 处理条件表达式，如果condition不为null则进行转换
        condition == null ? null : condition.accept(shuttle); // 如果condition不为null，调用其accept方法应用shuttle访问器进行转换；否则保持null
    final Expression post1 = post == null ? null : post.accept(shuttle); // 处理后置操作表达式，如果post不为null则调用accept方法应用shuttle访问器进行转换；否则保持null
    final Statement body1 = body.accept(shuttle); // 处理循环体语句，调用其accept方法应用shuttle访问器进行转换
    return shuttle.visit(this, decls1, condition1, post1, body1); // 调用shuttle的visit方法完成访问，传入转换后的所有子节点，返回最终转换后的ForStatement对象
  } // accept方法结束

  @Override public <R> R accept(Visitor<R> visitor) { // 接受访问者模式中的Visitor访问器，用于类型安全的访问和操作表达式树，支持泛型返回值
    return visitor.visit(this); // 调用visitor的visit方法访问当前ForStatement节点，返回访问器定义的返回值R
  } // accept方法结束

  @Override void accept0(ExpressionWriter writer) { // 接受表达式写入器，将for循环语句转换为可读的Java源代码字符串并写入writer
    writer.append("for ("); // 写入for循环的起始关键字和左括号
    for (Ord<DeclarationStatement> declaration : Ord.zip(declarations)) { // 遍历声明语句列表，使用Ord.zip为每个声明添加索引
      declaration.e.accept2(writer, declaration.i == 0); // 调用每个声明语句的accept2方法，传入writer和是否是第一个声明的标志（用于控制逗号输出）
    } // 循环结束，所有初始化声明已写入
    writer.append("; "); // 写入初始化部分的分号和空格，分隔初始化和条件部分
    if (condition != null) { // 如果条件表达式不为null
      writer.append(condition); // 写入条件表达式
    } // 条件部分处理完成
    writer.append("; "); // 写入条件部分的分号和空格，分隔条件和后置操作部分
    if (post != null) { // 如果后置操作表达式不为null
      writer.append(post); // 写入后置操作表达式
    } // 后置操作部分处理完成
    writer.append(") ").append(Blocks.toBlock(body)); // 写入右括号和空格，然后使用Blocks.toBlock确保body是块语句形式并写入
  } // accept0方法结束

  @Override public boolean equals(@Nullable Object o) { // 重写equals方法，用于比较两个ForStatement对象是否相等
    if (this == o) { // 如果是同一个对象引用
      return true; // 直接返回true
    } // 同一对象检查完成
    if (o == null || getClass() != o.getClass()) { // 如果o为null或类型不同
      return false; // 返回false，不相等
    } // 类型检查完成
    if (!super.equals(o)) { // 调用父类的equals方法检查父类字段是否相等
      return false; // 如果父类字段不相等，返回false
    } // 父类字段检查完成

    ForStatement that = (ForStatement) o; // 将o强制转换为ForStatement类型
    return body.equals(that.body) // 比较循环体是否相等
        && Objects.equals(condition, that.condition) // 使用Objects.equals比较条件表达式（可处理null）
        && declarations.equals(that.declarations) // 比较声明语句列表是否相等
        && Objects.equals(post, that.post); // 使用Objects.equals比较后置操作表达式（可处理null）
  } // equals方法结束

  @Override public int hashCode() { // 重写hashCode方法，用于支持HashMap和HashSet等基于哈希的集合
    int result = hash; // 从缓存的hash字段读取哈希码值
    if (result == 0) { // 如果缓存值为0（表示尚未计算或哈希码恰好为0）
      result = // 计算哈希码
          Objects.hash(nodeType, type, declarations, condition, post, body); // 使用Objects.hash方法组合所有字段的哈希值
      if (result == 0) { // 如果计算结果恰好为0
        result = 1; // 将结果设为1，避免与未计算状态混淆（因为0表示未计算）
      } // 零值处理完成
      hash = result; // 将计算结果缓存到hash字段中
    } // 哈希码计算完成
    return result; // 返回哈希码值
  } // hashCode方法结束
} // ForStatement类结束
