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
package org.apache.calcite.linq4j.tree; // 包声明：BlockStatement类属于org.apache.calcite.linq4j.tree包，该包用于构建表达式树和语句树

import org.checkerframework.checker.initialization.qual.UnderInitialization; // 导入CheckerFramework的初始化检查注解，用于标记对象处于未完全初始化状态
import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的可空性检查注解，用于标记可能为null的值

import java.lang.reflect.Type; // 导入Type接口，用于表示Java类型
import java.util.HashSet; // 导入HashSet集合类，用于存储不重复的元素
import java.util.List; // 导入List接口，用于表示有序列表
import java.util.Objects; // 导入Objects工具类，提供对象操作方法
import java.util.Set; // 导入Set接口，用于表示不重复元素的集合

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于检查对象引用不为null

/**
 * Represents a block that contains a sequence of expressions where variables
 * can be defined.
 * 表示一个代码块，包含一系列语句，可以在其中定义变量
 * BlockStatement是LINQ4J表达式树中的一个重要节点类型，用于表示Java代码中的语句块（即大括号{}包裹的代码段）
 * 它继承自Statement基类，用于构建和操作表达式树，是代码生成和转换的核心组件
 * 典型用途包括：方法体、if语句的分支、循环体、try-catch块等需要包含多条语句的场景
 */
public class BlockStatement extends Statement { // BlockStatement类定义：继承自Statement基类，表示一个包含多个语句的代码块
  public final List<Statement> statements; // 成员变量：语句列表，存储代码块中的所有语句，按执行顺序排列，final修饰表示初始化后不可修改
  /** Cached hash code for the expression. */
  private int hash; // 私有成员变量：缓存的哈希码，用于优化equals和hashCode方法的性能，避免重复计算

  BlockStatement(List<Statement> statements, Type type) { // 构造方法：创建BlockStatement实例，参数statements为语句列表，type为返回类型
    super(ExpressionType.Block, type); // 调用父类Statement的构造方法，设置节点类型为Block，指定返回类型
    this.statements = requireNonNull(statements, "statements"); // 初始化statements成员变量，使用requireNonNull检查参数不为null，否则抛出NullPointerException
    assert distinctVariables(true); // 断言检查：确保代码块中的变量名不重复，调用distinctVariables方法进行验证，fail参数为true表示如果发现重复变量会抛出断言错误
  }

  private boolean distinctVariables( // 私有方法：检查代码块中的变量名是否唯一，防止重复声明
      @UnderInitialization(BlockStatement.class) BlockStatement this, // CheckerFramework注解：标记this对象可能处于未完全初始化状态
      boolean fail) { // 参数fail：如果为true，发现重复变量时抛出断言错误；如果为false，仅返回false表示有重复
    Set<String> names = new HashSet<>(); // 创建一个HashSet集合，用于存储已经遇到过的变量名
    for (Statement statement : statements) { // 遍历代码块中的所有语句
      if (statement instanceof DeclarationStatement) { // 检查当前语句是否是变量声明语句
        String name = ((DeclarationStatement) statement).parameter.name; // 获取声明语句中的变量名
        if (!names.add(name)) { // 尝试将变量名添加到集合中，如果返回false表示该变量名已存在（重复声明）
          assert !fail : "duplicate variable " + name; // 如果fail为true，抛出断言错误，提示重复的变量名
          return false; // 返回false表示存在重复变量
        }
      }
    }
    return true; // 所有变量名都是唯一的，返回true
  }

  @Override public BlockStatement accept(Shuttle shuttle) { // 重写accept方法：使用访问者模式遍历和转换表达式树，参数shuttle是表达式穿梭器
    shuttle = shuttle.preVisit(this); // 调用shuttle的preVisit方法，在访问当前节点前执行预处理，可能返回修改后的shuttle
    List<Statement> newStatements = // 声明变量，用于存储转换后的语句列表
        Expressions.acceptStatements(statements, shuttle); // 使用Expressions工具类的acceptStatements方法，让shuttle遍历并转换所有语句，返回新的语句列表
    return shuttle.visit(this, newStatements); // 调用shuttle的visit方法，传入当前节点和转换后的语句列表，返回可能被修改的新BlockStatement节点
  }

  @Override public <R> R accept(Visitor<R> visitor) { // 重写accept方法：使用访问者模式访问表达式树，泛型R表示访问方法的返回类型
    return visitor.visit(this); // 调用visitor的visit方法，传入当前BlockStatement节点，执行访问操作并返回结果
  }

  @Override void accept0(ExpressionWriter writer) { // 重写accept0方法：将代码块写入表达式编写器，参数writer用于生成代码文本
    if (statements.isEmpty()) { // 检查语句列表是否为空
      writer.append("{}"); // 如果为空，直接输出空的大括号"{}"
      return; // 提前返回，不执行后续代码
    }
    writer.begin("{\n"); // 调用writer的begin方法，开始写入代码块，输出"{"并换行
    for (Statement node : statements) { // 遍历代码块中的所有语句
      node.accept(writer, 0, 0); // 调用每个语句的accept方法，将其写入writer，后两个参数0表示缩进级别
    }
    writer.end("}\n"); // 调用writer的end方法，结束代码块，输出"}"并换行
  }

  @Override public @Nullable Object evaluate(Evaluator evaluator) { // 重写evaluate方法：执行代码块并返回最后一个语句的结果，参数evaluator是表达式求值器
    Object o = null; // 初始化返回值对象为null，用于存储最后一个语句的执行结果
    for (Statement statement : statements) { // 遍历代码块中的所有语句
      o = statement.evaluate(evaluator); // 依次执行每个语句，并将执行结果赋值给o，最终o会保存最后一个语句的结果
    }
    return o; // 返回最后一个语句的执行结果，如果代码块为空则返回null
  }

  @Override public boolean equals(@Nullable Object o) { // 重写equals方法：比较两个BlockStatement对象是否相等，参数o是要比较的对象，可能为null
    if (this == o) { // 检查是否是同一个对象引用
      return true; // 如果是同一个对象，直接返回true
    }
    if (o == null || getClass() != o.getClass()) { // 检查o是否为null，或者两者的类类型是否不同
      return false; // 如果o为null或类型不同，返回false
    }
    if (!super.equals(o)) { // 调用父类的equals方法，比较父类部分是否相等
      return false; // 如果父类部分不相等，返回false
    }

    BlockStatement that = (BlockStatement) o; // 将o强制转换为BlockStatement类型，赋值给that变量

    if (!statements.equals(that.statements)) { // 比较两个对象的statements列表是否相等
      return false; // 如果语句列表不相等，返回false
    }

    return true; // 所有比较都通过，返回true表示两个对象相等
  }

  @Override public int hashCode() { // 重写hashCode方法：计算BlockStatement对象的哈希码，用于哈希表等数据结构
    int result = hash; // 获取缓存的哈希码值
    if (result == 0) { // 检查缓存的哈希码是否为0（表示尚未计算或计算结果为0）
      result = Objects.hash(nodeType, type, statements); // 使用Objects.hash方法计算哈希码，基于节点类型、类型和语句列表
      if (result == 0) { // 检查计算结果是否为0（虽然概率很低，但可能发生）
        result = 1; // 如果计算结果为0，将其设置为1，避免与未计算的状态混淆
      }
      hash = result; // 将计算好的哈希码缓存到hash成员变量中
    }
    return result; // 返回哈希码值
  }
}
