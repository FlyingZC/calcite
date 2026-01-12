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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

import static java.util.Objects.requireNonNull;

/**
 * Represents a call to either a static or an instance method.
 * 表示对静态方法或实例方法的调用表达式，是LINQ表达式树中方法调用节点的抽象表示
 * 
 * 该类是Calcite LINQ4J表达式树的核心组件之一，用于在编译时表示方法调用操作，
 * 支持静态方法和实例方法两种调用方式。通过反射机制在运行时实际执行方法调用。
 * 
 * 主要功能：
 * 1. 封装方法调用的所有信息：方法对象、目标对象、参数列表
 * 2. 支持表达式树的遍历和转换（通过访问者模式）
 * 3. 支持运行时评估（通过反射调用实际方法）
 * 4. 支持表达式树的序列化和反序列化（通过ExpressionWriter）
 * 
 * 使用场景：
 * - 在构建LINQ查询时表示方法调用操作
 * - 在代码生成过程中生成方法调用代码
 * - 在表达式优化过程中分析和转换方法调用
 * 
 * 设计特点：
 * - 不可变对象：所有字段都是final的，确保线程安全
 * - 延迟哈希计算：hashCode值在首次使用时计算并缓存
 * - 类型安全：通过泛型和反射确保类型正确性
 * - 访问者模式：支持Shuttle和Visitor两种访问模式
 */
public class MethodCallExpression extends Expression { // 继承自Expression基类，表示表达式树中的方法调用节点
  public final Method method; // 要调用的方法对象，包含方法的完整反射信息（方法名、参数类型、返回类型等）
  public final @Nullable Expression targetExpression; // 目标表达式，对于实例方法调用表示调用该方法的对象表达式，对于静态方法调用为null
  public final List<Expression> expressions; // 方法调用的参数表达式列表，每个元素对应一个方法参数的表达式树节点
  /** Cached hash code for the expression. */
  private int hash; // 缓存的哈希码值，用于优化equals和hashCode方法的性能，避免重复计算

  MethodCallExpression(Type returnType, Method method, // 构造方法：创建方法调用表达式，指定返回类型、方法对象、目标表达式和参数列表
      @Nullable Expression targetExpression, List<Expression> expressions) { // 参数：returnType-方法返回的类型，method-要调用的方法对象，targetExpression-目标对象表达式（静态方法为null），expressions-方法参数表达式列表
    super(ExpressionType.Call, returnType); // 调用父类构造方法，设置表达式类型为Call（方法调用），并指定返回类型
    checkArgument((targetExpression == null) // 验证参数：检查目标表达式是否为null与方法是否为静态方法的一致性
        == Modifier.isStatic(method.getModifiers()), // 如果方法是非静态的，则targetExpression不能为null；如果方法是静态的，则targetExpression必须为null
        "static method requires target expression " // 错误信息：静态方法调用时目标表达式状态不匹配
            + "[static: %s, targetExpression: %s]", // 格式化错误信息，显示方法的静态状态和目标表达式的值
        Modifier.isStatic(method.getModifiers()), // 获取方法的修饰符，判断是否为静态方法
        targetExpression); // 传入目标表达式用于错误信息显示
    checkArgument(Types.toClass(returnType) == method.getReturnType()); // 验证参数：检查指定的返回类型与方法实际的返回类型是否一致
    this.method = requireNonNull(method, "method"); // 初始化方法对象，确保method不为null，否则抛出NullPointerException
    this.targetExpression = targetExpression; // 初始化目标表达式，实例方法调用时为对象表达式，静态方法调用时为null
    this.expressions = requireNonNull(expressions, "expressions"); // 初始化参数表达式列表，确保expressions不为null，否则抛出NullPointerException
  }

  MethodCallExpression(Method method, @Nullable Expression targetExpression, // 构造方法：创建方法调用表达式，从方法对象自动推断返回类型
      List<Expression> expressions) { // 参数：method-要调用的方法对象，targetExpression-目标对象表达式（静态方法为null），expressions-方法参数表达式列表
    this(method.getReturnType(), method, targetExpression, expressions); // 调用另一个构造方法，从method对象中获取返回类型作为returnType参数
  }

  @Override public Expression accept(Shuttle shuttle) { // 接受访问者模式中的Shuttle访问器，用于遍历和转换表达式树
    shuttle = shuttle.preVisit(this); // 预访问：在真正访问当前节点之前，让shuttle有机会进行预处理或状态设置
    Expression targetExpression = // 处理目标表达式：递归地让shuttle访问目标表达式节点（如果存在）
        this.targetExpression == null // 如果当前是静态方法调用，目标表达式为null
            ? null // 直接返回null，无需进一步处理
            : this.targetExpression.accept(shuttle); // 如果是实例方法调用，递归调用shuttle访问目标表达式节点，可能返回转换后的表达式
    List<Expression> expressions = // 处理参数表达式列表：递归地让shuttle访问所有参数表达式节点
        Expressions.acceptExpressions(this.expressions, shuttle); // 使用工具方法批量访问表达式列表中的每个表达式，返回转换后的表达式列表
    return shuttle.visit(this, targetExpression, expressions); // 让shuttle访问当前节点，传入原始this和可能已转换的目标表达式及参数列表，返回可能的新表达式节点
  }

  @Override public <R> R accept(Visitor<R> visitor) { // 接受访问者模式中的Visitor访问器，用于类型安全的访问和操作表达式树
    return visitor.visit(this); // 调用visitor的visit方法，将当前MethodCallExpression对象传入，返回visitor定义的泛型结果类型R
  }

  @Override public @Nullable Object evaluate(Evaluator evaluator) { // 评估方法：在运行时实际执行方法调用，返回方法调用的结果
    final Object target; // 声明目标对象变量，用于存储方法调用的目标对象（实例方法）或null（静态方法）
    if (targetExpression == null) { // 判断是否为静态方法调用
      target = null; // 静态方法调用时，目标对象为null
    } else { // 实例方法调用
      target = targetExpression.evaluate(evaluator); // 递归评估目标表达式，获取实际的目标对象实例
    }
    final @Nullable Object[] args = new Object[expressions.size()]; // 创建参数数组，用于存储方法调用时传入的实际参数值
    for (int i = 0; i < expressions.size(); i++) { // 遍历所有参数表达式
      Expression expression = expressions.get(i); // 获取当前位置的参数表达式
      args[i] = expression.evaluate(evaluator); // 递归评估参数表达式，将结果存入参数数组
    }
    try { // 尝试执行方法调用
      return method.invoke(target, args); // 使用Java反射机制调用方法，传入目标对象和参数数组，返回方法执行结果
    } catch (IllegalAccessException | InvocationTargetException e) { // 捕获反射调用时可能出现的异常
      throw new RuntimeException("error while evaluating " + this, e); // 将反射异常包装为运行时异常抛出，包含当前表达式信息
    }
  }

  @Override void accept(ExpressionWriter writer, int lprec, int rprec) { // 接受表达式写入器，将方法调用表达式转换为可读的代码字符串
    if (writer.requireParentheses(this, lprec, rprec)) { // 检查是否需要添加括号（根据左右优先级判断）
      return; // 如果需要括号且已由writer处理，则直接返回
    }
    if (targetExpression != null) { // 判断是否为实例方法调用
      // instance method
      targetExpression.accept(writer, lprec, nodeType.lprec); // 写入目标表达式（对象），传入左优先级和当前节点类型的左优先级
    } else { // 静态方法调用
      // static method
      writer.append(method.getDeclaringClass()); // 写入声明该方法的类名（静态方法调用使用类名）
    }
    writer.append('.').append(method.getName()).append('('); // 写入点号、方法名和左括号，形成方法调用的开始部分
    int k = 0; // 初始化参数计数器，用于在参数之间添加逗号分隔符
    for (Expression expression : expressions) { // 遍历所有参数表达式
      if (k++ > 0) { // 判断是否为第一个参数
        writer.append(", "); // 如果不是第一个参数，写入逗号和空格作为分隔符
      }
      expression.accept(writer, 0, 0); // 递归写入参数表达式，使用0优先级（最低优先级）
    }
    writer.append(')'); // 写入右括号，完成方法调用的输出
  }

  @Override public boolean equals(@Nullable Object o) { // 重写equals方法：判断两个MethodCallExpression对象是否相等
    if (this == o) { // 判断是否为同一个对象引用
      return true; // 如果是同一个引用，直接返回true
    }
    if (o == null || getClass() != o.getClass()) { // 判断对象是否为null或类型是否相同
      return false; // 如果为null或类型不同，返回false
    }
    if (!super.equals(o)) { // 调用父类的equals方法，检查父类定义的字段是否相等
      return false; // 如果父类字段不相等，返回false
    }

    MethodCallExpression that = (MethodCallExpression) o; // 将对象转换为MethodCallExpression类型
    return expressions.equals(that.expressions) // 比较参数表达式列表是否相等
        && method.equals(that.method) // 比较方法对象是否相等（方法名、参数类型、返回类型等）
        && Objects.equals(targetExpression, that.targetExpression); // 比较目标表达式是否相等（使用Objects.equals处理null情况）
  }

  @Override public int hashCode() { // 重写hashCode方法：返回对象的哈希码值，用于支持基于哈希的集合操作
    int result = hash; // 获取缓存的哈希码值
    if (result == 0) { // 判断是否需要计算哈希码（0表示未计算或计算结果为0）
      result = // 计算新的哈希码值
          Objects.hash(nodeType, type, method, targetExpression, expressions); // 使用Objects.hash方法组合所有关键字段的哈希值
      if (result == 0) { // 判断计算结果是否为0（合法的哈希值）
        result = 1; // 如果结果为0，则使用1代替，避免与未计算状态混淆
      }
      hash = result; // 将计算结果缓存到hash字段中，避免重复计算
    }
    return result; // 返回哈希码值
  }
}
