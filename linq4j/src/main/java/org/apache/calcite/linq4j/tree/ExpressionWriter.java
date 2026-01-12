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
package org.apache.calcite.linq4j.tree; // 声明包名，该类属于 org.apache.calcite.linq4j.tree 包

import org.apache.calcite.avatica.util.Spacer; // 导入 Spacer 类，用于处理缩进格式

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空类型注解

import java.lang.reflect.Type; // 导入 Type 类，用于表示 Java 类型
import java.util.Iterator; // 导入 Iterator 接口，用于遍历集合

/**
 * Converts an expression to Java code. // 将表达式树转换为 Java 代码字符串的写入器
 * 
 * ExpressionWriter 是 linq4j 框架中的核心类之一，负责将表达式树（Expression Tree）转换为可编译的 Java 源代码字符串。
 * 它实现了访问者模式，通过 accept 方法遍历表达式树，并逐步构建出对应的 Java 代码。
 * 
 * 主要功能：
 * 1. 将各种表达式节点（如方法调用、字段访问、二元运算等）转换为 Java 代码
 * 2. 处理代码格式化，包括缩进、换行等
 * 3. 根据运算符优先级智能添加括号，确保生成的代码语义正确
 * 4. 支持泛型类型信息的保留或移除
 * 5. 提供链式调用接口，方便代码构建
 * 
 * 使用场景：
 * - 在运行时动态生成 Java 代码
 * - 将 LINQ 查询表达式转换为 Java 代码
 * - 代码生成器和编译器的前端处理
 */
class ExpressionWriter { // ExpressionWriter 类定义，将表达式树转换为 Java 代码字符串
  /** How many spaces to indent Java code. */ // Java 代码缩进的空格数常量
  private static final int INDENT = 2; // 每级缩进使用 2 个空格

  private final Spacer spacer = new Spacer(0); // 缩进管理器，用于跟踪当前的缩进级别，初始为 0
  private final StringBuilder buf = new StringBuilder(); // 字符串构建器，用于存储生成的 Java 代码
  private boolean indentPending; // 标记是否需要在下次写入时添加缩进，用于延迟缩进处理
  private final boolean generics; // 布尔标志，指示是否在输出中保留泛型类型信息

  ExpressionWriter() { // 默认构造方法，创建一个支持泛型的 ExpressionWriter 实例
    this(true); // 调用带参数的构造方法，传入 true 表示支持泛型
  }

  ExpressionWriter(boolean generics) { // 带参数的构造方法，创建 ExpressionWriter 实例
    this.generics = generics; // 初始化 generics 标志，控制是否在输出中保留泛型类型
  }

  public void write(Node expression) { // 写入方法，将表达式节点写入到输出缓冲区
    if (expression instanceof Expression) { // 如果节点是表达式类型
      Expression expression1 = (Expression) expression; // 将节点强制转换为表达式
      expression1.accept(this, 0, 0); // 调用表达式的 accept 方法，传入当前写入器和优先级参数
    } else { // 如果节点不是表达式类型
      expression.accept(this); // 调用节点的 accept 方法，传入当前写入器
    }
  }

  @Override public String toString() { // 重写 toString 方法，返回生成的 Java 代码字符串
    return buf.toString(); // 将缓冲区内容转换为字符串返回
  }

  /**
   * If parentheses are required, writes this expression out with
   * parentheses and returns true. If they are not required, does nothing
   * and returns false.
   * // 如果需要括号，则将表达式用括号包裹并返回 true；如果不需要，则不做任何操作并返回 false
   * 
   * 该方法根据运算符优先级判断是否需要添加括号：
   * - lprec: 左侧上下文的优先级
   * - rprec: 右侧上下文的优先级
   * - expressionNodeType.lprec: 当前表达式左结合的优先级
   * - expressionNodeType.rprec: 当前表达式右结合的优先级
   * 
   * 判断逻辑：如果左侧上下文优先级小于表达式左优先级，且表达式右优先级大于等于右侧上下文优先级，
   * 则不需要括号；否则需要添加括号以确保正确的运算顺序
   */
  public boolean requireParentheses(Expression expression, int lprec, // 根据优先级判断是否需要添加括号
      int rprec) { // 右侧上下文的优先级
    if (lprec < expression.nodeType.lprec // 如果左侧上下文优先级小于表达式左优先级
        && expression.nodeType.rprec >= rprec) { // 且表达式右优先级大于等于右侧上下文优先级
      return false; // 不需要添加括号，返回 false
    }
    buf.append("("); // 添加左括号
    expression.accept(this, 0, 0); // 递归写入表达式内容
    buf.append(")"); // 添加右括号
    return true; // 返回 true 表示已添加括号
  }

  /**
   * Increases the indentation level.
   * // 增加缩进级别，用于生成嵌套的代码块（如方法体、if 语句等）
   */
  public void begin() { // 开始一个新的缩进级别
    spacer.add(INDENT); // 向缩进管理器添加一个缩进级别（2 个空格）
  }

  /**
   * Decreases the indentation level.
   * // 减少缩进级别，用于结束嵌套的代码块
   */
  public void end() { // 结束当前的缩进级别
    spacer.subtract(INDENT); // 从缩进管理器减去一个缩进级别（2 个空格）
  }

  public ExpressionWriter newlineAndIndent() { // 添加换行符并标记需要缩进
    buf.append("\n"); // 在缓冲区添加换行符
    indentPending = true; // 设置缩进待处理标志，下次写入时会自动添加缩进
    return this; // 返回当前对象，支持链式调用
  }

  public ExpressionWriter indent() { // 立即添加当前缩进级别的空格
    spacer.spaces(buf); // 将当前缩进级别的空格写入缓冲区
    return this; // 返回当前对象，支持链式调用
  }

  public ExpressionWriter begin(String s) { // 写入字符串并开始新的缩进级别
    append(s); // 先写入字符串
    begin(); // 然后增加缩进级别
    indentPending = s.endsWith("\n"); // 如果字符串以换行符结尾，则标记需要缩进
    return this; // 返回当前对象，支持链式调用
  }

  public ExpressionWriter end(String s) { // 结束缩进级别并写入字符串
    end(); // 先减少缩进级别
    append(s); // 然后写入字符串
    indentPending = s.endsWith("\n"); // 如果字符串以换行符结尾，则标记需要缩进
    return this; // 返回当前对象，支持链式调用
  }

  public ExpressionWriter append(char c) { // 追加单个字符到缓冲区
    checkIndent(); // 检查并处理待处理的缩进
    buf.append(c); // 将字符追加到缓冲区
    return this; // 返回当前对象，支持链式调用
  }

  public ExpressionWriter append(Type type) { // 追加 Java 类型到缓冲区
    checkIndent(); // 检查并处理待处理的缩进
    if (!generics) { // 如果不保留泛型信息
      type = Types.stripGenerics(type); // 则移除类型中的泛型参数
    }
    buf.append(Types.className(type)); // 将类型名称追加到缓冲区
    return this; // 返回当前对象，支持链式调用
  }

  public ExpressionWriter append(AbstractNode o) { // 追加抽象节点到缓冲区
    o.accept0(this); // 调用节点的 accept0 方法进行访问
    return this; // 返回当前对象，支持链式调用
  }

  public ExpressionWriter append(@Nullable Object o) { // 追加任意对象到缓冲区
    checkIndent(); // 检查并处理待处理的缩进
    buf.append(o); // 将对象的字符串表示追加到缓冲区
    return this; // 返回当前对象，支持链式调用
  }

  public ExpressionWriter append(@Nullable String s) { // 追加字符串到缓冲区
    checkIndent(); // 检查并处理待处理的缩进
    buf.append(s); // 将字符串追加到缓冲区
    return this; // 返回当前对象，支持链式调用
  }

  private void checkIndent() { // 检查并处理待处理的缩进
    if (indentPending) { // 如果有待处理的缩进
      spacer.spaces(buf); // 将当前缩进级别的空格写入缓冲区
      indentPending = false; // 清除缩进待处理标志
    }
  }

  public StringBuilder getBuf() { // 获取内部的字符串构建器
    checkIndent(); // 先检查并处理待处理的缩进
    return buf; // 返回字符串构建器
  }

  public ExpressionWriter list(String begin, String sep, String end, // 将集合元素格式化为列表形式
      Iterable<?> list) { // 要格式化的集合
    final Iterator<?> iterator = list.iterator(); // 获取集合的迭代器
    if (iterator.hasNext()) { // 如果集合不为空
      begin(begin); // 写入列表的开始标记（如 "(" 或 "{"）
      for (;;) { // 无限循环，用于遍历集合
        Object o = iterator.next(); // 获取下一个元素
        if (o instanceof Expression) { // 如果元素是表达式
          ((Expression) o).accept(this, 0, 0); // 调用表达式的 accept 方法写入
        } else if (o instanceof MemberDeclaration) { // 如果元素是成员声明
          ((MemberDeclaration) o).accept(this); // 调用成员声明的 accept 方法写入
        } else if (o instanceof Type) { // 如果元素是类型
          append((Type) o); // 追加类型名称
        } else { // 其他类型的元素
          append(o); // 直接追加对象的字符串表示
        }
        if (!iterator.hasNext()) { // 如果没有更多元素
          break; // 退出循环
        }
        buf.append(sep); // 追加分隔符（如 ", "）
        if (sep.endsWith("\n")) { // 如果分隔符以换行符结尾
          indentPending = true; // 标记需要缩进
        }
      }
      end(end); // 写入列表的结束标记（如 ")" 或 "}"）
    } else { // 如果集合为空
      while (begin.endsWith("\n")) { // 移除开始标记末尾的所有换行符
        begin = begin.substring(0, begin.length() - 1); // 截去最后一个字符
      }
      buf.append(begin).append(end); // 直接拼接开始和结束标记（如 "()"）
    }
    return this; // 返回当前对象，支持链式调用
  }

  public void backUp() { // 回退操作，移除最后的换行符
    if (buf.lastIndexOf("\n") == buf.length() - 1) { // 如果缓冲区最后一个字符是换行符
      buf.delete(buf.length() - 1, buf.length()); // 删除最后的换行符
      indentPending = false; // 清除缩进待处理标志
    }
  }
}
