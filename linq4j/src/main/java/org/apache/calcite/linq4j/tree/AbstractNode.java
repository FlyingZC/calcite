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
package org.apache.calcite.linq4j.tree; // 声明包名，表示这个类属于org.apache.calcite.linq4j.tree包，这是LINQ4J表达式树的核心包

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空类型注解，用于标记可能为null的返回值或参数

import java.lang.reflect.Type; // 导入Type接口，用于表示Java类型
import java.util.Objects; // 导入Objects工具类，用于实现equals和hashCode方法

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空校验

/**
 * Abstract implementation of {@link Node}. // Node接口的抽象实现类，是LINQ4J表达式树中所有节点的基类
 * 这个类为表达式树节点提供了通用的功能实现，包括节点类型管理、类型系统、访问者模式支持、序列化和比较等基础功能
 * 所有具体的表达式节点（如BinaryExpression、MethodCallExpression等）都继承自这个抽象类
 * 它实现了表达式树的核心抽象，使得节点可以被遍历、转换、序列化和比较
 */
public abstract class AbstractNode implements Node { // 定义抽象类AbstractNode，实现Node接口，是所有表达式节点的基类
  public final ExpressionType nodeType; // 节点类型枚举，标识这个节点的类型（如Add、Subtract、MethodCall等），用于在表达式树中区分不同的操作类型
  public final Type type; // 表达式的静态类型，表示这个表达式计算结果的Java类型（如int.class、String.class等），用于类型检查和代码生成

  AbstractNode(ExpressionType nodeType, Type type) { // 构造方法，初始化节点类型和表达式类型，由子类调用
    this.type = requireNonNull(type, "type"); // 设置表达式类型，使用requireNonNull确保type参数不为null，否则抛出NullPointerException
    this.nodeType = requireNonNull(nodeType, "nodeType"); // 设置节点类型，使用requireNonNull确保nodeType参数不为null，否则抛出NullPointerException
  }

  /**
   * Gets the node type of this Expression. // 获取此表达式的节点类型
   * @return 返回节点的ExpressionType枚举值，用于识别表达式的操作类型
   */
  public ExpressionType getNodeType() { // 获取节点类型的方法，返回ExpressionType枚举
    return nodeType; // 返回存储的节点类型枚举值
  }

  /**
   * Gets the static type of the expression that this Expression // 获取此表达式所表示的静态类型
   * represents. // 返回表达式计算结果的Java类型，用于类型系统和代码生成
   * @return 返回表达式的Java类型对象
   */
  public Type getType() { // 获取表达式类型的方法，返回Type对象
    return type; // 返回存储的表达式类型
  }

  @Override public String toString() { // 重写toString方法，将表达式节点转换为字符串表示，用于调试和日志输出
    ExpressionWriter writer = new ExpressionWriter(true); // 创建表达式写入器对象，参数true表示使用完整格式输出
    accept(writer, 0, 0); // 调用accept方法将表达式写入writer，左右优先级都设为0表示不添加括号
    return writer.toString(); // 返回writer构建的字符串表示
  }

  @Override public void accept(ExpressionWriter writer) { // 实现Node接口的accept方法，接受表达式写入器访问
    accept(writer, 0, 0); // 调用内部accept方法，左右优先级都设为0，表示默认情况下不添加括号
  }

  void accept0(ExpressionWriter writer) { // 内部方法，接受表达式写入器访问，不带优先级参数的版本
    accept(writer, 0, 0); // 调用带优先级参数的accept方法，左右优先级都设为0
  }

  void accept(ExpressionWriter writer, int lprec, int rprec) { // 内部方法，接受表达式写入器访问，并指定左右优先级
    // lprec：左优先级，用于判断是否需要在左侧添加括号
    // rprec：右优先级，用于判断是否需要在右侧添加括号
    // 这个方法应该被子类重写以实现具体的序列化逻辑
    throw new RuntimeException( // 如果子类没有重写此方法，抛出运行时异常
        "un-parse not supported: " + getClass() + ":" + nodeType); // 异常信息包含类名和节点类型，提示此节点类型不支持反解析
  }

  @Override public Node accept(Shuttle shuttle) { // 实现Node接口的accept方法，接受访问者（Shuttle）进行节点遍历和转换
    // Shuttle是访问者模式的实现，用于遍历表达式树并对节点进行转换
    // 这个方法应该被子类重写以实现具体的访问者逻辑
    throw new RuntimeException( // 如果子类没有重写此方法，抛出运行时异常
        "visit not supported: " + getClass() + ":" + nodeType); // 异常信息包含类名和节点类型，提示此节点类型不支持访问
  }

  public @Nullable Object evaluate(Evaluator evaluator) { // 评估表达式并返回计算结果，使用给定的评估器
    // evaluator：表达式评估器，用于在运行时计算表达式的值
    // 返回值可能为null，使用@Nullable注解标记
    // 这个方法应该被子类重写以实现具体的求值逻辑
    throw new RuntimeException( // 如果子类没有重写此方法，抛出运行时异常
        "evaluation not supported: " + getClass() + ":" + nodeType); // 异常信息包含类名和节点类型，提示此节点类型不支持求值
  }

  @Override public boolean equals(@Nullable Object o) { // 重写equals方法，判断两个节点是否相等
    if (this == o) { // 如果是同一个对象引用
      return true; // 直接返回true
    }
    if (o == null || getClass() != o.getClass()) { // 如果o为null或者o的类型与当前对象的类型不同
      return false; // 返回false
    }

    AbstractNode that = (AbstractNode) o; // 将o强制转换为AbstractNode类型
    return nodeType == that.nodeType // 比较节点类型是否相同
        && type.equals(that.type); // 比较表达式类型是否相同
  }

  @Override public int hashCode() { // 重写hashCode方法，与equals方法保持一致
    return Objects.hash(nodeType, type); // 使用Objects.hash方法基于nodeType和type生成哈希码
  }
}
