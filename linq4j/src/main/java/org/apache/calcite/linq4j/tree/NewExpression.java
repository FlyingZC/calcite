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
package org.apache.calcite.linq4j.tree; // 声明包名，该类属于org.apache.calcite.linq4j.tree包，是LINQ4J表达式树的一部分

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的空值注解，用于标记可能为null的字段或参数

import java.lang.reflect.Type; // 导入Java反射Type类，用于表示Java类型
import java.util.List; // 导入List接口，用于存储表达式和成员声明列表
import java.util.Objects; // 导入Objects工具类，用于哈希码计算和对象比较

/**
 * Represents a constructor call. // 表示一个构造函数调用表达式，用于在表达式树中表示new操作
 *
 * <p>If {@link #memberDeclarations} is not null (even if empty) represents // 如果memberDeclarations不为null（即使为空列表），则表示这是一个匿名类
 * an anonymous class. // 匿名类可以在创建对象时同时定义类体，包含字段、方法等成员声明
 */
public class NewExpression extends Expression { // NewExpression类继承自Expression基类，表示构造函数调用表达式
  @SuppressWarnings("HidingField") // 抑制"隐藏字段"警告，因为字段type与父类Expression的type字段同名
  public final Type type; // 要实例化的类型，表示new操作符后面跟的类类型，如new Person()中的Person类
  public final List<Expression> arguments; // 构造函数的参数列表，每个参数都是一个Expression对象，表示传递给构造函数的实际参数表达式
  public final @Nullable List<MemberDeclaration> memberDeclarations; // 匿名类的成员声明列表，可为null；如果不为null则表示这是匿名类定义，包含字段、方法等成员
  /** Cached hash code for the expression. */ // 缓存的哈希码，用于提高性能，避免重复计算
  private int hash; // 哈希码缓存字段，初始值为0，首次调用hashCode()时计算并缓存

  public NewExpression(Type type, List<Expression> arguments, // 构造方法：创建一个NewExpression实例，表示构造函数调用
      @Nullable List<MemberDeclaration> memberDeclarations) { // 参数：type是要实例化的类型，arguments是构造函数参数列表，memberDeclarations是匿名类成员声明（可为null）
    super(ExpressionType.New, type); // 调用父类Expression的构造方法，传入表达式类型New和结果类型type
    this.type = type; // 将传入的type参数赋值给当前对象的type字段，记录要实例化的类型
    this.arguments = arguments; // 将传入的arguments参数赋值给当前对象的arguments字段，记录构造函数参数列表
    this.memberDeclarations = memberDeclarations; // 将传入的memberDeclarations参数赋值给当前对象的memberDeclarations字段，记录匿名类成员声明
  }

  @Override public Expression accept(Shuttle shuttle) { // 接受访问者模式中的Shuttle访问器，用于遍历和转换表达式树
    shuttle = shuttle.preVisit(this); // 调用Shuttle的preVisit方法进行前置访问，允许在访问子节点前进行预处理
    final List<Expression> arguments = // 接收Shuttle处理后的参数列表，Shuttle可以转换或替换参数表达式
        Expressions.acceptExpressions(this.arguments, shuttle); // 使用Expressions工具类批量处理参数表达式列表，让Shuttle访问每个参数
    final List<MemberDeclaration> memberDeclarations = // 接收Shuttle处理后的成员声明列表，Shuttle可以转换或替换成员声明
        this.memberDeclarations == null // 如果当前对象的memberDeclarations为null，则保持为null
            ? null // 返回null，表示没有成员声明需要处理
            : Expressions.acceptMemberDeclarations(this.memberDeclarations, // 否则使用Expressions工具类批量处理成员声明列表
                shuttle); // 让Shuttle访问每个成员声明，进行可能的转换或替换
    return shuttle.visit(this, arguments, memberDeclarations); // 调用Shuttle的visit方法，传入处理后的参数和成员声明，返回转换后的表达式
  }

  @Override public <R> R accept(Visitor<R> visitor) { // 接受访问者模式中的Visitor访问器，用于遍历表达式树并返回特定类型的结果
    return visitor.visit(this); // 调用Visitor的visit方法，传入当前NewExpression对象，由Visitor决定如何处理并返回结果
  }

  @Override void accept(ExpressionWriter writer, int lprec, int rprec) { // 接受ExpressionWriter访问器，用于将表达式树转换为Java源代码字符串
    writer.append("new ").append(type).list("(\n", ",\n", ")", arguments); // 写入"new "关键字和类型名，然后以列表形式写入参数，参数间用逗号分隔
    if (memberDeclarations != null) { // 如果存在成员声明（即这是一个匿名类）
      writer.list("{\n", "", "}", memberDeclarations); // 则写入匿名类的类体，用大括号包围成员声明列表
    } // 匿名类的成员声明包括字段、方法等，会被完整写入到生成的Java代码中
  }

  @Override public boolean equals(@Nullable Object o) { // 重写equals方法，用于比较两个NewExpression对象是否相等
    if (this == o) { // 如果传入的对象就是当前对象本身
      return true; // 直接返回true，对象自反性
    } // 这是equals方法的第一步快速检查
    if (o == null || getClass() != o.getClass()) { // 如果传入对象为null，或者类型不是NewExpression类
      return false; // 返回false，对象不匹配
    } // 确保比较的两个对象类型相同
    if (!super.equals(o)) { // 调用父类Expression的equals方法检查父类字段是否相等
      return false; // 如果父类字段不相等，返回false
    } // 确保父类定义的相等性条件也满足

    NewExpression that = (NewExpression) o; // 将传入对象强制转换为NewExpression类型，准备比较字段

    if (arguments != null ? !arguments.equals(that.arguments) // 如果当前arguments不为null且不等于that的arguments
        : that.arguments != null) { // 或者当前arguments为null但that的arguments不为null
      return false; // 返回false，参数列表不相等
    } // 比较构造函数参数列表是否相等
    if (memberDeclarations // 比较成员声明列表是否相等
        != null ? !memberDeclarations.equals(that.memberDeclarations) // 如果当前memberDeclarations不为null且不等于that的memberDeclarations
        : that.memberDeclarations != null) { // 或者当前memberDeclarations为null但that的memberDeclarations不为null
      return false; // 返回false，成员声明列表不相等
    } // 比较匿名类的成员声明是否相等
    if (!type.equals(that.type)) { // 如果类型不相等
      return false; // 返回false，类型不匹配
    } // 比较要实例化的类型是否相同

    return true; // 所有字段都相等，返回true，两个NewExpression对象相等
  }

  @Override public int hashCode() { // 重写hashCode方法，用于计算NewExpression对象的哈希码，与equals方法保持一致
    int result = hash; // 获取缓存的哈希码值
    if (result == 0) { // 如果哈希码还未计算（初始值为0）
      result = // 计算哈希码并赋值给result
          Objects.hash(nodeType, super.type, type, arguments, memberDeclarations); // 使用Objects.hash方法基于所有关键字段计算哈希码
      if (result == 0) { // 如果计算结果恰好为0
        result = 1; // 将哈希码设为1，避免与初始值0混淆，确保缓存有效
      } // 防止哈希码为0导致每次都重新计算
      hash = result; // 将计算好的哈希码缓存到hash字段中，下次直接使用
    } // 延迟初始化哈希码，只在第一次调用时计算
    return result; // 返回哈希码值
  } // 哈希码用于在HashMap、HashSet等集合中快速定位对象
}
