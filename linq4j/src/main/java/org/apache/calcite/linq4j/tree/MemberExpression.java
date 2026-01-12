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
package org.apache.calcite.linq4j.tree; // 定义包路径，属于LINQ4J的树形表达式模块

import org.checkerframework.checker.nullness.qual.Nullable; // 引入可空类型注解，用于标记可能为null的类型

import java.lang.reflect.Field; // 引入反射Field类，用于表示Java类的字段
import java.lang.reflect.Modifier; // 引入反射Modifier类，用于访问字段的修饰符（如static、public等）
import java.util.Objects; // 引入工具类，用于对象的equals和hashCode操作

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空校验

/**
 * Represents accessing a field or property.
 * 表示访问字段或属性的表达式类，这是LINQ4J表达式树中的节点类型之一
 * MemberExpression用于表示对对象字段的访问，例如"person.name"或"Math.PI"这样的表达式
 * 它可以处理实例字段访问（需要提供对象表达式）和静态字段访问（对象表达式为null）
 * 该类继承自Expression基类，是表达式树结构中的一个重要组成部分
 */
public class MemberExpression extends Expression { // MemberExpression类定义，继承自Expression基类
  public final @Nullable Expression expression; // 成员变量：表达式对象，表示拥有该字段的目标对象，对于静态字段为null，实例字段必须提供
  public final PseudoField field; // 成员变量：伪字段对象，封装了字段的类型、修饰符、名称等信息，使用PseudoField抽象层以支持动态字段

  public MemberExpression(Expression expression, Field field) { // 构造方法：使用Java反射Field对象创建MemberExpression，方便从反射API直接创建表达式
    this(expression, Types.field(field)); // 调用主构造方法，将Field对象转换为PseudoField对象
  }

  public MemberExpression(@Nullable Expression expression, PseudoField field) { // 主构造方法：使用表达式和PseudoField对象创建MemberExpression，这是完整的构造方法
    super(ExpressionType.MemberAccess, field.getType()); // 调用父类构造方法，设置表达式类型为MemberAccess（成员访问），并设置返回类型为字段的类型
    this.expression = expression; // 初始化目标对象表达式，对于静态字段访问可能为null
    this.field = requireNonNull(field, "field"); // 初始化字段对象，使用requireNonNull确保field参数不为null，否则抛出NullPointerException
    if (!Modifier.isStatic(field.getModifiers())) { // 检查字段是否为静态字段，如果不是静态字段则需要提供表达式对象
      requireNonNull(expression, // 如果字段不是静态的，则表达式对象不能为null，否则抛出异常
          "must specify expression if field is not static"); // 异常消息：如果字段不是静态的，必须指定表达式
    }
  }

  @Override public Expression accept(Shuttle shuttle) { // accept方法：接受Shuttle访问者，用于遍历和转换表达式树，实现访问者模式
    shuttle = shuttle.preVisit(this); // 调用shuttle的preVisit方法，在访问当前节点前执行预处理操作，返回可能修改后的shuttle
    Expression expression1 = expression == null // 检查目标对象表达式是否为null
        ? null // 如果为null（静态字段），则expression1保持为null
        : expression.accept(shuttle); // 如果不为null，则递归调用accept方法处理表达式，返回转换后的表达式
    return shuttle.visit(this, expression1); // 调用shuttle的visit方法，传入当前节点和转换后的表达式，返回最终转换后的表达式节点
  }

  @Override public <R> R accept(Visitor<R> visitor) { // accept方法：接受泛型Visitor访问者，用于类型安全的访问者模式遍历
    return visitor.visit(this); // 调用visitor的visit方法，传入当前MemberExpression节点，返回访问者处理后的结果
  }

  @Override public @Nullable Object evaluate(Evaluator evaluator) { // evaluate方法：在运行时评估表达式，返回字段的实际值
    final Object o = expression == null // 检查目标对象表达式是否为null
        ? null // 如果为null（静态字段访问），则目标对象为null
        : expression.evaluate(evaluator); // 如果不为null，则递归评估表达式，获取目标对象的实际值
    try { // 开始try-catch块，处理字段访问可能抛出的异常
      return field.get(o); // 使用PseudoField的get方法，从目标对象o中获取字段的值，对于静态字段o参数会被忽略
    } catch (IllegalAccessException e) { // 捕获非法访问异常，当字段访问权限不足时抛出
      throw new RuntimeException("error while evaluating " + this, e); // 将检查异常转换为运行时异常，包含当前表达式信息
    }
  }

  @Override void accept(ExpressionWriter writer, int lprec, int rprec) { // accept方法：将表达式转换为代码字符串，用于代码生成场景
    if (writer.requireParentheses(this, lprec, rprec)) { // 检查是否需要添加括号，根据左右优先级和当前节点类型判断
      return; // 如果需要括号但已经添加了，则直接返回
    }
    if (expression != null) { // 检查目标对象表达式是否不为null（实例字段访问）
      expression.accept(writer, lprec, nodeType.lprec); // 递归调用accept方法生成目标对象的代码，传入左优先级和当前节点类型的左优先级
    } else { // 如果目标对象表达式为null（静态字段访问）
      assert (field.getModifiers() & Modifier.STATIC) != 0; // 断言字段是静态的，确保静态字段访问时表达式为null
      writer.append(field.getDeclaringClass()); // 将字段声明的类名写入输出，生成如"Math"这样的类名
    }
    writer.append('.').append(field.getName()); // 写入点号和字段名，生成如".PI"这样的字段访问表达式
  }

  @Override public boolean equals(@Nullable Object o) { // equals方法：判断两个MemberExpression对象是否相等
    if (this == o) { // 检查是否是同一个对象引用
      return true; // 如果是同一个引用，直接返回true
    }
    if (o == null || getClass() != o.getClass()) { // 检查对象是否为null或类型是否不同
      return false; // 如果为null或类型不同，返回false
    }
    if (!super.equals(o)) { // 调用父类的equals方法检查父类部分是否相等
      return false; // 如果父类部分不相等，返回false
    }

    MemberExpression that = (MemberExpression) o; // 将对象强制转换为MemberExpression类型

    if (expression != null ? !expression.equals(that.expression) : that // 检查expression字段是否相等，处理null情况
        .expression != null) { // 如果一个为null另一个不为null，则不相等
      return false; // 返回false
    }
    if (!field.equals(that.field)) { // 检查field字段是否相等
      return false; // 如果不相等，返回false
    }

    return true; // 所有检查都通过，返回true
  }

  @Override public int hashCode() { // hashCode方法：计算对象的哈希码，用于HashMap等数据结构
    return Objects.hash(nodeType, type, expression, field); // 使用Objects.hash方法，基于nodeType、type、expression和field计算哈希码
  }
}
