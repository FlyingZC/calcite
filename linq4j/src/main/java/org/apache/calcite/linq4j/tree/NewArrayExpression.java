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
package org.apache.calcite.linq4j.tree;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

/**
 * Represents creating a new array and possibly initializing the elements of the
 * new array.
 * // 表示创建一个新数组并可能初始化新数组的元素的表达式类
 * // 这个类继承自 Expression，是 LINQ4J 表达式树的一部分，用于表示 Java 代码中的数组创建操作
 * // 例如：new int[10]、new String[]{"a", "b", "c"}、new int[2][3] 等
 * // 支持多维数组的创建和初始化
 */
public class NewArrayExpression extends Expression {
  // 数组的维度，1 表示一维数组，2 表示二维数组，以此类推
  public final int dimension;
  // 数组的大小边界表达式，仅对一维数组有效，表示数组的长度
  // 例如 new int[10] 中的 10，对于多维数组或数组初始化列表的情况，此值为 null
  public final @Nullable Expression bound;
  // 数组初始化表达式列表，用于数组初始化语法
  // 例如 new int[]{1, 2, 3} 中的 {1, 2, 3}，对于使用 bound 指定大小的情况，此值为 null
  public final @Nullable List<Expression> expressions;
  /** Cached hash code for the expression. */
  // 缓存的哈希码，用于提高 equals 和 hashCode 方法的性能，避免重复计算
  private int hash;

  // 构造函数：创建一个新的数组表达式
  // 参数 type: 数组元素的类型，例如 int.class、String.class 等
  // 参数 dimension: 数组的维度，1 表示一维数组，2 表示二维数组等
  // 参数 bound: 数组的大小边界表达式，可以为 null（当使用初始化列表时）
  // 参数 expressions: 数组初始化表达式列表，可以为 null（当使用 bound 指定大小时）
  public NewArrayExpression(Type type, int dimension, @Nullable Expression bound,
      @Nullable List<Expression> expressions) {
    // 调用父类 Expression 的构造函数，设置节点类型为 NewArrayInit，并计算完整的数组类型
    // Types.arrayType(type, dimension) 会根据元素类型和维度生成完整的数组类型，如 int[][] 等
    super(ExpressionType.NewArrayInit, Types.arrayType(type, dimension));
    // 保存数组维度
    this.dimension = dimension;
    // 保存数组大小边界表达式
    this.bound = bound;
    // 保存数组初始化表达式列表
    this.expressions = expressions;
  }

  // 接受访问者模式中的 Shuttle 访问器，用于遍历和转换表达式树
  // Shuttle 是一个表达式树的访问器，可以在遍历过程中修改或替换表达式节点
  // 返回转换后的表达式，可能是当前对象，也可能是新的表达式对象
  @Override public Expression accept(Shuttle shuttle) {
    // 首先调用 preVisit 方法，让访问器在访问当前节点前进行预处理
    shuttle = shuttle.preVisit(this);
    // 如果当前表达式有初始化列表，则递归地让访问器处理每个初始化表达式
    // Expressions.acceptExpressions 会遍历表达式列表并对每个表达式调用 accept 方法
    List<Expression> expressions =
        this.expressions == null
            ? null
            : Expressions.acceptExpressions(this.expressions, shuttle);
    // 如果当前表达式有边界表达式，则让访问器处理该边界表达式
    Expression bound =
        this.bound == null
            ? null
            : this.bound.accept(shuttle);
    // 调用访问器的 visit 方法，传入处理后的参数，让访问器决定如何处理当前节点
    // 访问器可能会返回一个新的 NewArrayExpression 对象，或者返回当前对象
    return shuttle.visit(this, dimension, bound, expressions);
  }

  // 接受访问者模式中的 Visitor 访问器，用于对表达式树进行只读访问
  // 泛型 R 表示访问方法的返回类型
  // 返回访问器处理后的结果，具体类型由访问器决定
  @Override public <R> R accept(Visitor<R> visitor) {
    // 调用访问器的 visit 方法，让访问器处理当前 NewArrayExpression 节点
    return visitor.visit(this);
  }

  // 接受表达式写入器，将当前表达式转换为 Java 代码字符串
  // 参数 writer: 表达式写入器，用于构建代码字符串
  // 参数 lprec: 左结合优先级，用于确定是否需要添加括号
  // 参数 rprec: 右结合优先级，用于确定是否需要添加括号
  @Override void accept(ExpressionWriter writer, int lprec, int rprec) {
    // 首先写入 "new " 关键字，然后写入数组的组件类型（去掉数组维度的元素类型）
    // Types.getComponentTypeN 会获取数组的元素类型，例如 int[][] 会返回 int
    writer.append("new ").append(Types.getComponentTypeN(type));
    // 根据维度循环写入数组的维度标记
    for (int i = 0; i < dimension; i++) {
      // 如果是第一个维度且存在边界表达式（即使用 new int[10] 这种形式）
      if (i == 0 && bound != null) {
        // 写入 [bound] 的形式，例如 [10]
        writer.append('[').append(bound).append(']');
      } else {
        // 否则写入 [] 的形式，用于后续维度或没有边界的情况
        writer.append("[]");
      }
    }
    // 如果存在初始化表达式列表，则写入数组初始化块
    if (expressions != null) {
      // 使用 list 方法写入初始化列表，格式为 {表达式1, 表达式2, ...}
      // 第一个参数是开始符 " {\n"，第二个参数是分隔符 ",\n"，第三个参数是结束符 "}"
      writer.list(" {\n", ",\n", "}", expressions);
    }
  }

  // 判断当前对象是否与另一个对象相等
  // 参数 o: 要比较的对象
  // 返回 true 如果相等，false 如果不相等
  @Override public boolean equals(@Nullable Object o) {
    // 如果是同一个对象引用，直接返回 true
    if (this == o) {
      return true;
    }
    // 如果对象为 null 或类型不同，返回 false
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    // 调用父类的 equals 方法，比较父类定义的字段
    if (!super.equals(o)) {
      return false;
    }

    // 将对象转换为 NewArrayExpression 类型
    NewArrayExpression that = (NewArrayExpression) o;

    // 比较维度是否相同
    if (dimension != that.dimension) {
      return false;
    }
    // 比较边界表达式是否相同（考虑 null 值的情况）
    if (bound != null ? !bound.equals(that.bound) : that.bound != null) {
      return false;
    }
    // 比较初始化表达式列表是否相同（考虑 null 值的情况）
    if (expressions != null ? !expressions.equals(that.expressions) : that
        .expressions != null) {
      return false;
    }

    // 所有字段都相等，返回 true
    return true;
  }

  // 计算对象的哈希码，用于哈希集合（如 HashSet、HashMap）中快速查找
  // 返回对象的哈希码值
  @Override public int hashCode() {
    // 首先检查是否已经计算过哈希码
    int result = hash;
    // 如果哈希码尚未计算（初始值为 0），则进行计算
    if (result == 0) {
      // 使用 Objects.hash 方法计算哈希码，考虑所有关键字段
      // 包括节点类型、类型、维度、边界表达式和初始化表达式列表
      result = Objects.hash(nodeType, type, dimension, bound, expressions);
      // 如果计算结果为 0（虽然不太可能），则设置为 1 以避免与未计算状态混淆
      if (result == 0) {
        result = 1;
      }
      // 缓存计算结果，避免重复计算
      hash = result;
    }
    // 返回缓存的哈希码
    return result;
  }
}
