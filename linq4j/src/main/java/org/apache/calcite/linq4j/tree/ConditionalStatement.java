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
package org.apache.calcite.linq4j.tree; // 声明包名，该类属于org.apache.calcite.linq4j.tree包，用于LINQ4J表达式树的构建

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的参数或返回值

import java.util.List; // 导入List接口，用于存储表达式列表
import java.util.Objects; // 导入Objects工具类，用于equals和hashCode方法

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空校验

/**
 * Represents an expression that has a conditional operator. // 类功能说明：表示具有条件运算符的表达式，用于构建if-else语句的抽象语法树节点
 *
 * <p>With an odd number of expressions // 当表达式数量为奇数时的说明
 * {c0, e0, c1, e1, ..., c<sub>n-1</sub>, e<sub>n-1</sub>, e<sub>n</sub>} // 表达式列表格式：条件0,表达式0,条件1,表达式1,...,条件n-1,表达式n-1,表达式n
 * represents "if (c0) e0 else if (c1) e1 ... else e<sub>n</sub>"; // 对应的Java代码：if (c0) e0 else if (c1) e1 ... else en，最后一个表达式是else分支
 * with an even number of expressions // 当表达式数量为偶数时的说明
 * {c0, e0, c1, e1, ..., c<sub>n-1</sub>, e<sub>n-1</sub>} // 表达式列表格式：条件0,表达式0,条件1,表达式1,...,条件n-1,表达式n-1
 * represents // 对应的Java代码
 * "if (c0) e0 else if (c1) e1 ... else if (c<sub>n-1</sub>) e<sub>n-1</sub>". // if (c0) e0 else if (c1) e1 ... else if (c_{n-1}) e_{n-1}，没有else分支
 */
public class ConditionalStatement extends Statement { // ConditionalStatement类继承自Statement基类，表示条件语句节点
  public final List<Node> expressionList; // 成员变量：表达式列表，存储条件和对应的执行语句，奇数位置是条件表达式，偶数位置是执行语句

  public ConditionalStatement(List<Node> expressionList) { // 构造方法：创建条件语句对象
    super(ExpressionType.Conditional, Void.TYPE); // 调用父类构造方法，设置节点类型为Conditional，返回类型为Void（语句没有返回值）
    this.expressionList = requireNonNull(expressionList, "expressionList"); // 初始化表达式列表，使用requireNonNull确保参数不为null，否则抛出NullPointerException
  }

  @Override public Statement accept(Shuttle shuttle) { // 方法：接受访问者模式的Shuttle对象，用于遍历和转换表达式树节点
    shuttle = shuttle.preVisit(this); // 调用shuttle的preVisit方法，在访问当前节点前执行预处理，可能返回修改后的shuttle
    List<Node> list = Expressions.acceptNodes(expressionList, shuttle); // 使用shuttle遍历表达式列表中的所有子节点，可能对子节点进行转换
    return shuttle.visit(this, list); // 调用shuttle的visit方法，传入当前节点和转换后的子节点列表，返回可能被替换的新Statement对象
  }

  @Override public <R> R accept(Visitor<R> visitor) { // 方法：接受访问者模式的Visitor对象，用于遍历表达式树并执行特定操作
    return visitor.visit(this); // 调用visitor的visit方法，将当前节点传递给visitor，visitor根据具体实现返回结果类型R
  }

  @Override void accept0(ExpressionWriter writer) { // 方法：将条件语句转换为Java代码字符串并写入ExpressionWriter
    for (int i = 0; i < expressionList.size() - 1; i += 2) { // 遍历表达式列表，每次步进2，处理条件-语句对
      if (i > 0) { // 如果不是第一个条件
        writer.backUp(); // 回退一个位置，用于处理else if的格式
        writer.append(" else "); // 追加" else "字符串，连接多个if语句
      }
      writer.append("if (") // 追加"if ("字符串，开始if语句
          .append(expressionList.get(i)) // 追加条件表达式（奇数位置的条件）
          .append(") ") // 追加") "字符串，结束条件部分
          .append(Blocks.toBlock(expressionList.get(i + 1))); // 追加执行语句（偶数位置的语句），使用Blocks.toBlock确保语句被花括号包围
    }
    if (expressionList.size() % 2 == 1) { // 如果表达式列表长度为奇数，说明有else分支
      writer.backUp(); // 回退一个位置，用于处理else的格式
      writer.append(" else ") // 追加" else "字符串
          .append(Blocks.toBlock(last(expressionList))); // 追加else分支的执行语句（最后一个表达式），使用Blocks.toBlock确保语句被花括号包围
    }
  }

  private static <E> E last(List<E> collection) { // 私有静态方法：获取列表中的最后一个元素
    return collection.get(collection.size() - 1); // 返回列表中索引为size-1的元素，即最后一个元素
  }

  @Override public boolean equals(@Nullable Object o) { // 方法：重写equals方法，用于比较两个ConditionalStatement对象是否相等
    if (this == o) { // 如果是同一个对象引用
      return true; // 直接返回true
    }
    if (o == null || getClass() != o.getClass()) { // 如果o为null或o的类型不是ConditionalStatement
      return false; // 返回false
    }
    if (!super.equals(o)) { // 调用父类的equals方法，如果父类比较不相等
      return false; // 返回false
    }

    ConditionalStatement that = (ConditionalStatement) o; // 将o强制转换为ConditionalStatement类型
    return expressionList.equals(that.expressionList); // 比较两个对象的expressionList是否相等
  }

  @Override public int hashCode() { // 方法：重写hashCode方法，用于支持HashMap等基于哈希的集合
    return Objects.hash(nodeType, type, expressionList); // 使用Objects.hash方法计算哈希值，基于nodeType、type和expressionList
  }
}
