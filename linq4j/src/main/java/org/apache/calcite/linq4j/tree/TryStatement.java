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
package org.apache.calcite.linq4j.tree; // 声明包名，表示TryStatement类属于org.apache.calcite.linq4j.tree包，这是LINQ4J表达式树的核心包

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的Nullable注解，用于标记可能为null的字段或参数

import java.util.ArrayList; // 导入ArrayList类，用于创建动态数组列表
import java.util.List; // 导入List接口，用于表示有序集合
import java.util.Objects; // 导入Objects工具类，用于equals和hashCode等操作

import static java.util.Objects.requireNonNull; // 静态导入Objects.requireNonNull方法，用于非空校验

/**
 * Represents a {@code try ... catch ... finally} block. // 类注释：表示一个try...catch...finally代码块，用于在LINQ4J表达式树中表示Java的异常处理语句
 * TryStatement是Statement的子类，用于在代码生成过程中构建try-catch-finally结构
 * 它包含三个主要部分：try块体（body）、catch块列表（catchBlocks）和finally块（fynally）
 * 这个类是LINQ4J表达式树的一部分，用于将Java代码表示为抽象语法树（AST）的形式
 */
public class TryStatement extends Statement { // 定义TryStatement类，继承自Statement基类，表示一个try-catch-finally语句节点
  public final Statement body; // try块的主体语句，表示try代码块中要执行的代码，final修饰表示不可变
  public final List<CatchBlock> catchBlocks; // catch块的列表，用于处理try块中可能抛出的异常，可以有多个catch块，final修饰表示不可变
  public final @Nullable Statement fynally; // finally块语句，表示无论是否发生异常都会执行的代码块，可能为null，@Nullable表示允许为null，注意字段名拼写为fynally以避免与Java关键字finally冲突

  public TryStatement(Statement body, List<CatchBlock> catchBlocks, // 构造方法：创建一个TryStatement实例，参数包括try块体、catch块列表和finally块
      @Nullable Statement fynally) { // 构造方法参数：finally块语句，可能为null，使用@Nullable注解标记
    super(ExpressionType.Try, body.getType()); // 调用父类Statement的构造方法，传入表达式类型Try和try块体的返回类型
    this.body = requireNonNull(body, "body"); // 初始化body字段，使用requireNonNull进行非空校验，如果为null则抛出NullPointerException
    this.catchBlocks = requireNonNull(catchBlocks, "catchBlocks"); // 初始化catchBlocks字段，使用requireNonNull进行非空校验，如果为null则抛出NullPointerException
    this.fynally = fynally; // 初始化fynally字段，finally块可以为null，所以不需要非空校验
  }

  @Override public Statement accept(Shuttle shuttle) { // 接受访问者模式中的Shuttle访问器，用于遍历和转换表达式树，返回转换后的Statement
    shuttle = shuttle.preVisit(this); // 调用shuttle的preVisit方法，在访问当前节点前执行预处理，可能返回修改后的shuttle实例
    Statement body1 = body.accept(shuttle); // 使用shuttle访问并转换try块体，得到转换后的body1
    List<CatchBlock> catchBlocks1 = new ArrayList<>(); // 创建新的catch块列表，用于存储转换后的catch块
    for (CatchBlock cb : catchBlocks) { // 遍历原始catch块列表中的每个catch块
      Statement cbBody = cb.body.accept(shuttle); // 使用shuttle访问并转换当前catch块的body，得到转换后的cbBody
      catchBlocks1.add( // 将转换后的catch块添加到新列表中
          Expressions.catch_(cb.parameter, cbBody)); // 使用Expressions工具类创建新的CatchBlock，保留原参数，使用转换后的body
    }
    Statement fynally1 = // 声明转换后的finally块变量
        fynally == null ? null : fynally.accept(shuttle); // 如果finally块不为null，则使用shuttle访问并转换它，否则保持null
    return shuttle.visit(this, body1, catchBlocks1, fynally1); // 调用shuttle的visit方法，传入原始节点和转换后的子节点，返回最终转换后的TryStatement
  }

  @Override public <R> R accept(Visitor<R> visitor) { // 接受访问者模式中的Visitor访问器，用于类型安全的访问和操作表达式树，返回泛型R类型的结果
    return visitor.visit(this); // 调用visitor的visit方法，传入当前TryStatement实例，执行特定的访问逻辑
  }

  @Override void accept0(ExpressionWriter writer) { // 接受ExpressionWriter访问器，用于将表达式树转换为Java源代码字符串，无返回值
    writer.append("try ").append(Blocks.toBlock(body)); // 向writer追加"try "关键字，然后将try块体转换为代码块并追加
    for (CatchBlock catchBlock : catchBlocks) { // 遍历所有catch块
      writer.backUp(); // 回退一个字符位置，用于格式化输出，确保catch关键字和try块之间有适当的间距
      writer.append(" catch (").append(catchBlock.parameter.declString()) // 追加" catch ("关键字，然后追加catch块的参数声明字符串（如"Exception e"）
          .append(") ").append(Blocks.toBlock(catchBlock.body)); // 追加") "，然后将catch块体转换为代码块并追加
    }
    if (fynally != null) { // 如果finally块不为null
      writer.backUp(); // 回退一个字符位置，用于格式化输出
      writer.append(" finally ").append(Blocks.toBlock(fynally)); // 追加" finally "关键字，然后将finally块体转换为代码块并追加
    }
  }

  @Override public boolean equals(@Nullable Object o) { // 重写equals方法，用于比较两个TryStatement对象是否相等，@Nullable表示参数可能为null
    if (this == o) { // 如果两个对象引用相同，则直接返回true
      return true;
    }
    if (o == null || getClass() != o.getClass()) { // 如果o为null或者o的类型不是TryStatement，则返回false
      return false;
    }
    if (!super.equals(o)) { // 调用父类Statement的equals方法，如果父类不相等则返回false
      return false;
    }

    TryStatement that = (TryStatement) o; // 将o强制转换为TryStatement类型

    if (!body.equals(that.body)) { // 比较try块体是否相等，如果不相等则返回false
      return false;
    }
    if (!catchBlocks.equals(that.catchBlocks)) { // 比较catch块列表是否相等，如果不相等则返回false
      return false;
    }
    if (fynally != null ? !fynally.equals(that.fynally) : that.fynally // 比较finally块是否相等，使用三元运算符处理null情况
        != null) { // 如果this.fynally不为null且不等于that.fynally，或者this.fynally为null而that.fynally不为null，则返回false
      return false;
    }

    return true; // 所有字段都相等，返回true
  }

  @Override public int hashCode() { // 重写hashCode方法，用于支持HashMap等基于哈希的集合，返回对象的哈希码
    return Objects.hash(nodeType, type, body, catchBlocks, fynally); // 使用Objects.hash方法计算哈希码，包含所有关键字段：节点类型、类型、try块体、catch块列表和finally块
  }
}
