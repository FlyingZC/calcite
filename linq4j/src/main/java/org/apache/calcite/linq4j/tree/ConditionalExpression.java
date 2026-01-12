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
// 包声明，表示这个类属于org.apache.calcite.linq4j.tree包，这是Calcite LINQ4J模块中用于表示表达式树的包
package org.apache.calcite.linq4j.tree;

// 导入Checker框架的注解，用于标记可能为null的参数
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入Java反射中的Type类，用于表示类型信息
import java.lang.reflect.Type;
// 导入Java集合框架中的List接口，用于存储表达式列表
import java.util.List;
// 导入Java工具类中的Objects类，用于equals和hashCode方法
import java.util.Objects;

// 静态导入Objects.requireNonNull方法，用于参数非空校验
import static java.util.Objects.requireNonNull;

/**
 * Represents an expression that has a conditional operator.
 * 表示一个具有条件运算符的表达式，这是Calcite表达式树中表示条件表达式（如if-else语句）的核心类
 *
 * <p>With an odd number of expressions
 * {c0, e0, c1, e1, ..., c<sub>n-1</sub>, e<sub>n-1</sub>, e<sub>n</sub>}
 * represents "if (c0) e0 else if (c1) e1 ... else e<sub>n</sub>";
 * 当表达式数量为奇数时，{c0, e0, c1, e1, ..., c<sub>n-1</sub>, e<sub>n-1</sub>, e<sub>n</sub>}表示"if (c0) e0 else if (c1) e1 ... else e<sub>n</sub>"
 * 其中c表示条件（condition），e表示表达式（expression），最后一个表达式作为else分支的默认值
 *
 * <p>with an even number of expressions
 * {c0, e0, c1, e1, ..., c<sub>n-1</sub>, e<sub>n-1</sub>}
 * represents
 * "if (c0) e0 else if (c1) e1 ... else if (c<sub>n-1</sub>) e<sub>n-1</sub>".
 * 当表达式数量为偶数时，{c0, e0, c1, e1, ..., c<sub>n-1</sub>, e<sub>n-1</sub>}表示"if (c0) e0 else if (c1) e1 ... else if (c<sub>n-1</sub>) e<sub>n-1</sub>"
 * 这种情况下没有else默认分支，最后一个条件为true时执行最后一个表达式，否则返回类型的默认值
 *
 * 这个类是Calcite LINQ4J表达式树系统的重要组成部分，用于在运行时生成和操作条件表达式
 * 它支持多分支条件判断，类似于Java中的if-else if-else结构
 */
// ConditionalExpression类继承自AbstractNode，表示一个条件表达式节点，是表达式树中的一个节点类型
public class ConditionalExpression extends AbstractNode {
  // 成员变量：表达式列表，存储条件表达式的所有子节点
  // 这个列表按照[条件0, 表达式0, 条件1, 表达式1, ..., 条件n, 表达式n]或者[条件0, 表达式0, 条件1, 表达式1, ..., 条件n, 表达式n, 默认表达式]的顺序存储
  // 列表长度为奇数时，最后一个元素是else分支的默认表达式；列表长度为偶数时，没有else分支
  // 使用final修饰表示一旦初始化后不能被修改，保证了表达式树的不可变性
  final List<Node> expressionList;

  // 构造方法：创建一个新的条件表达式实例
  // 参数expressionList：表达式列表，包含条件和对应的表达式，按顺序排列
  // 参数type：整个条件表达式的返回类型，所有分支表达式必须能兼容这个类型
  // 这个构造方法会调用父类AbstractNode的构造函数，传入表达式类型（Conditional）和返回类型
  public ConditionalExpression(List<Node> expressionList, Type type) {
    // 调用父类AbstractNode的构造函数，传入表达式类型为Conditional，表示这是一个条件表达式
    // 同时传入type参数，指定整个条件表达式的返回类型
    super(ExpressionType.Conditional, type);
    // 使用requireNonNull方法校验expressionList参数不为null，如果为null则抛出NullPointerException
    // 将校验后的expressionList赋值给成员变量，使用this关键字区分成员变量和参数
    this.expressionList = requireNonNull(expressionList, "expressionList");
  }

  // 接受访问者模式的方法，允许外部访问者遍历和处理这个表达式节点
  // 参数<R>：访问者方法的返回类型，使用泛型支持不同的返回类型
  // 参数visitor：访问者对象，实现了Visitor接口，定义了如何访问不同类型的表达式节点
  // 返回值：访问者处理后的结果，类型由访问者决定
  // 这是访问者模式的核心方法，使得可以在不修改表达式类的情况下添加新的操作
  @Override public <R> R accept(Visitor<R> visitor) {
    // 调用访问者的visit方法，传入this（当前条件表达式对象）
    // 访问者会根据表达式类型调用相应的处理逻辑，并返回处理结果
    return visitor.visit(this);
  }

  // 接受表达式写入器的方法，将条件表达式转换为代码字符串输出
  // 参数writer：表达式写入器，用于构建和格式化输出代码
  // 参数lprec：左侧运算符优先级，用于确定是否需要添加括号
  // 参数rprec：右侧运算符优先级，用于确定是否需要添加括号
  // 这个方法负责将条件表达式树转换为可读的代码文本，用于代码生成场景
  @Override void accept(ExpressionWriter writer, int lprec, int rprec) {
    // 遍历表达式列表，每次递增2，因为每个条件-表达式对占用两个位置
    // i=0时访问条件0，i=1时访问表达式0，i=2时访问条件1，i=3时访问表达式1，以此类推
    for (int i = 0; i < expressionList.size(); i += 2) {
      // 向写入器追加条件语句，如果是第一个条件（i==0），输出"if ("；否则输出" else if ("
      // 这样可以构建出if-else if链条的结构
      writer.append(i > 0 ? " else if (" : "if (")
          // 追加当前条件表达式（expressionList.get(i)是条件）
          .append(expressionList.get(i))
          // 追加右括号和空格，结束条件部分
          .append(") ")
          // 追加对应的表达式（expressionList.get(i+1)是条件为true时要执行的表达式）
          // 使用Blocks.toBlock将表达式转换为代码块，确保单条语句也能正确处理
          .append(Blocks.toBlock(expressionList.get(i + 1)));
    }
    // 检查表达式列表的大小是否为奇数，如果是奇数，说明有else分支的默认表达式
    // 偶数表示最后一个条件没有对应的else分支
    if (expressionList.size() % 2 == 1) {
      // 追加" else "关键字，表示else分支的开始
      writer.append(" else ")
          // 追加else分支的默认表达式（列表最后一个元素）
          // 使用Blocks.toBlock将表达式转换为代码块
          .append(
              Blocks.toBlock(expressionList.get(expressionList.size() - 1)));
    }
  }

  // 重写equals方法，用于比较两个条件表达式是否相等
  // 参数o：要比较的对象，可能为null
  // 返回值：如果两个条件表达式相等返回true，否则返回false
  // 相等的条件是：同一个对象、类型相同、父类相等、表达式列表相等
  @Override public boolean equals(@Nullable Object o) {
    // 首先检查是否是同一个对象引用，如果是则直接返回true
    // 这是equals方法的优化，避免不必要的比较
    if (this == o) {
      return true;
    }
    // 检查要比较的对象是否为null，或者类型是否不同
    // 如果对象为null或类型不匹配，直接返回false
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    // 调用父类的equals方法，比较父类中的字段（如nodeType和type）是否相等
    // 如果父类不相等，直接返回false
    if (!super.equals(o)) {
      return false;
    }

    // 将对象o强制转换为ConditionalExpression类型，以便访问其成员变量
    ConditionalExpression that = (ConditionalExpression) o;

    // 比较两个条件表达式的expressionList成员变量是否相等
    // 使用List的equals方法比较列表内容是否相同
    if (!expressionList.equals(that.expressionList)) {
      return false;
    }

    // 所有比较都通过，返回true表示两个条件表达式相等
    return true;
  }

  // 重写hashCode方法，用于计算条件表达式的哈希值
  // 返回值：基于nodeType、type和expressionList计算出的哈希值
  // 这个方法必须与equals方法保持一致，即相等的对象必须有相同的哈希值
  @Override public int hashCode() {
    // 使用Objects.hash方法计算哈希值，传入nodeType、type和expressionList
    // Objects.hash会自动处理null值，并组合多个对象的哈希值
    // 这样可以确保相等的条件表达式具有相同的哈希值
    return Objects.hash(nodeType, type, expressionList);
  }
}
