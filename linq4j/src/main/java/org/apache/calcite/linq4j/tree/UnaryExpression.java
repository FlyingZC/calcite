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
package org.apache.calcite.linq4j.tree; // 定义包名,该类属于linq4j的tree子包,用于表示表达式树中的节点

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性检查注解,用于标记可能为null的参数

import java.lang.reflect.Type; // 导入Type类,用于表示Java类型
import java.util.Objects; // 导入Objects工具类,用于equals和hashCode方法

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法,用于非空校验

/**
 * Represents an expression that has a unary operator. // 表示一个具有一元运算符的表达式
 * // 一元运算符是指只作用于一个操作数的运算符,例如:负号(-)、逻辑非(!)、类型转换、自增(++)、自减(--)等
 * // 该类继承自Expression基类,是表达式树中的一个节点类型,用于表示各种一元操作
 * // 在Calcite的linq4j模块中,表达式树用于表示Java代码的抽象语法树(AST),可以动态生成和操作Java代码
 * // 该类支持的一元运算符类型由ExpressionType枚举定义,包括:Convert(类型转换)、ConvertChecked(带检查的类型转换)、
 * // Negate(取负)、Not(逻辑非)、UnaryPlus(一元加)、PostDecrementAssign(后置自减赋值)、PostIncrementAssign(后置自增赋值)等
 */
public class UnaryExpression extends Expression { // 定义UnaryExpression类,继承自Expression基类
  public final Expression expression; // 定义一元表达式的操作数,即被一元运算符作用的表达式对象,final修饰表示不可变

  UnaryExpression(ExpressionType nodeType, Type type, Expression expression) { // 构造方法,用于创建一元表达式对象
    super(nodeType, type); // 调用父类Expression的构造方法,传入表达式类型和结果类型
    this.expression = requireNonNull(expression, "expression"); // 初始化操作数表达式,使用requireNonNull确保expression不为null,否则抛出NullPointerException
  }

  @Override public Expression accept(Shuttle shuttle) { // 重写accept方法,接受Shuttle访问者并返回转换后的表达式
    shuttle = shuttle.preVisit(this); // 调用shuttle的preVisit方法进行预处理,可能返回修改后的shuttle对象
    Expression expression = this.expression.accept(shuttle); // 递归调用操作数表达式的accept方法,让shuttle访问操作数并返回转换后的表达式
    return shuttle.visit(this, expression); // 调用shuttle的visit方法,传入当前一元表达式和转换后的操作数表达式,返回最终转换后的表达式
  }

  @Override public <R> R accept(Visitor<R> visitor) { // 重写accept方法,接受泛型Visitor访问者并返回访问结果
    return visitor.visit(this); // 调用visitor的visit方法,传入当前一元表达式对象,返回访问者处理后的结果
  }

  @Override void accept(ExpressionWriter writer, int lprec, int rprec) { // 重写accept方法,将一元表达式写入到ExpressionWriter中,生成Java代码
    switch (nodeType) { // 根据节点类型进行不同的处理
    case Convert: // 如果是普通类型转换操作
      if (!writer.requireParentheses(this, lprec, rprec)) { // 检查是否需要添加括号,如果不需要则直接生成转换代码
        writer.append("(").append(type).append(") "); // 生成类型转换的Java代码,格式为"(Type) ",例如"(int) "
        expression.accept(writer, nodeType.rprec, rprec); // 递归写入操作数表达式,传入右结合优先级
      }
      return; // 处理完成,直接返回
    case ConvertChecked: // 如果是带检查的类型转换操作
      // This is ugly, but Java does not seem to have any facilities // 这是丑陋的代码,但Java似乎没有提供
      // to perform checked cast between scalar types! // 在标量类型之间执行带检查的转换的设施!
      // So we use the existing linq4j Primitive.numberValue method // 所以我们使用现有的linq4j Primitive.numberValue方法
      // which does overflow checking. // 该方法会进行溢出检查
      if (!writer.requireParentheses(this, lprec, rprec)) { // 检查是否需要添加括号
        // Generate Java code that looks like e.g., // 生成的Java代码看起来像这样,例如:
        // ((Number)org.apache.calcite.linq4j.tree.Primitive.of(int.class) // ((Number)org.apache.calcite.linq4j.tree.Primitive.of(int.class)
        //     .numberValueRoundDown(literal_value)).intValue(); //     .numberValueRoundDown(literal_value)).intValue();
        writer.append("((Number)") // 添加强制转换为Number的代码
            .append("org.apache.calcite.linq4j.tree.Primitive.of(") // 添加获取Primitive对象的代码
            .append(type) // 添加目标类型
            .append(".class)") // 添加.class后缀,获取Class对象
            .append(".numberValueRoundDown("); // 添加调用numberValueRoundDown方法的代码,该方法会进行向下取整和溢出检查
        expression.accept(writer, nodeType.rprec, rprec); // 递归写入操作数表达式
        writer.append(")).").append(type).append("Value()"); // 添加调用类型特定Value方法的代码,例如intValue()、doubleValue()等
      }
      return; // 处理完成,直接返回
    default: // 其他类型的一元运算符
      break; // 跳出switch语句
    }
    if (nodeType.postfix) { // 如果是后缀运算符(例如i++, i--)
      expression.accept(writer, lprec, nodeType.rprec); // 先写入操作数表达式
      writer.append(nodeType.op); // 再写入运算符
    } else { // 如果是前缀运算符(例如-i, !b, ++i, --i)
      writer.append("("); // 添加左括号
      writer.append(nodeType.op); // 写入运算符
      expression.accept(writer, nodeType.lprec, rprec); // 写入操作数表达式
      writer.append(")"); // 添加右括号
    }
  }

  @Override public boolean equals(@Nullable Object o) { // 重写equals方法,用于比较两个UnaryExpression对象是否相等
    if (this == o) { // 如果是同一个对象引用
      return true; // 直接返回true
    }
    if (o == null || getClass() != o.getClass()) { // 如果o为null或者类型不同
      return false; // 返回false
    }
    if (!super.equals(o)) { // 调用父类的equals方法比较父类部分
      return false; // 如果父类部分不相等,返回false
    }

    UnaryExpression that = (UnaryExpression) o; // 将o强制转换为UnaryExpression类型
    return expression.equals(that.expression); // 比较操作数表达式是否相等
  }

  @Override public int hashCode() { // 重写hashCode方法,用于生成对象的哈希码
    return Objects.hash(nodeType, type, expression); // 使用Objects.hash方法基于节点类型、类型和操作数表达式生成哈希码
  }
}
