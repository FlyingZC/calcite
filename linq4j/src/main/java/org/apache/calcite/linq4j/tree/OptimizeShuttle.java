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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.calcite.linq4j.tree.ExpressionType.Equal;
import static org.apache.calcite.linq4j.tree.ExpressionType.NotEqual;

/**
 * 用于优化表达式的访问器(Shuttle)类。
 *
 * <p>这个类继承自Shuttle基类，用于遍历和优化表达式树。优化过程是必要的，而不是微调。
 * 如果没有优化，像 {@code false == null} 这样的表达式会保留下来，这对Janino编译器是无效的
 * （因为它不会自动对基本类型进行装箱操作）。
 *
 * <p>优化内容包括：
 * - 常量折叠：将常量表达式预先计算
 * - 布尔表达式简化：简化逻辑运算
 * - 三元运算符优化：简化条件表达式
 * - 类型转换优化：移除不必要的类型转换
 * - 条件语句优化：移除永远不会执行的分支
 *
 * <p>该类通过重写Shuttle的各种visit方法来实现表达式树的遍历和优化。
 */
public class OptimizeShuttle extends Shuttle {
  // 静态常量：表示false的常量表达式，用于优化时的常量替换
  public static final ConstantExpression FALSE_EXPR =
      Expressions.constant(false);
  // 静态常量：表示true的常量表达式，用于优化时的常量替换
  public static final ConstantExpression TRUE_EXPR =
      Expressions.constant(true);
  // 静态常量：表示装箱后的false（Boolean.FALSE）的成员表达式
  public static final MemberExpression BOXED_FALSE_EXPR =
      Expressions.field(null, Boolean.class, "FALSE");
  // 静态常量：表示装箱后的true（Boolean.TRUE）的成员表达式
  public static final MemberExpression BOXED_TRUE_EXPR =
      Expressions.field(null, Boolean.class, "TRUE");
  // 静态常量：空语句，用于优化时替换无用的代码块
  public static final Statement EMPTY_STATEMENT = Expressions.statement(null);

  // 静态集合：存储已知永远不会返回null的方法，这些方法主要是包装类的valueOf静态方法
  // 包括Boolean、Byte、Short、Integer、Long、String等类的valueOf方法
  private static final Set<Method> KNOWN_NON_NULL_METHODS = new HashSet<>();

  // 静态初始化块：初始化KNOWN_NON_NULL_METHODS集合
  static {
    // 遍历常用的包装类，收集它们的valueOf静态方法
    for (Class aClass : new Class[]{Boolean.class, Byte.class, Short.class,
        Integer.class, Long.class, String.class}) {
      // 获取该类的所有公共方法
      for (Method method : aClass.getMethods()) {
        // 如果方法名是valueOf且是静态方法，则添加到集合中
        if ("valueOf".equals(method.getName())
            && Modifier.isStatic(method.getModifiers())) {
          KNOWN_NON_NULL_METHODS.add(method);
        }
      }
    }
  }

  // 静态映射：存储二元运算符的否定互补关系，用于优化逻辑表达式
  // 例如：Equal的否定是NotEqual，GreaterThanOrEqual的否定是LessThan
  private static final Map<ExpressionType, ExpressionType> NOT_BINARY_COMPLEMENT =
      new EnumMap<>(ExpressionType.class);

  // 静态初始化块：初始化NOT_BINARY_COMPLEMENT映射
  static {
    // 添加相等运算符的互补关系：Equal和NotEqual互为否定
    addComplement(ExpressionType.Equal, ExpressionType.NotEqual);
    // 添加大于等于运算符的互补关系：GreaterThanOrEqual和LessThan互为否定
    addComplement(ExpressionType.GreaterThanOrEqual, ExpressionType.LessThan);
    // 添加大于运算符的互补关系：GreaterThan和LessThanOrEqual互为否定
    addComplement(ExpressionType.GreaterThan, ExpressionType.LessThanOrEqual);
  }

  // 私有静态方法：向NOT_BINARY_COMPLEMENT映射中添加互补的运算符对
  // eq和ne互为否定，所以互相添加到对方的映射中
  private static void addComplement(ExpressionType eq, ExpressionType ne) {
    NOT_BINARY_COMPLEMENT.put(eq, ne);
    NOT_BINARY_COMPLEMENT.put(ne, eq);
  }

  // 静态常量：Boolean.valueOf(boolean)方法的Method对象，用于优化时识别这个方法调用
  private static final Method BOOLEAN_VALUEOF_BOOL =
      Types.lookupMethod(Boolean.class, "valueOf", boolean.class);

  // 重写visit方法：访问和优化三元表达式（条件运算符 ? : ）
  // ternary: 三元表达式节点
  // expression0: 条件部分（?前面的表达式）
  // expression1: 真值分支（:前面的表达式）
  // expression2: 假值分支（:后面的表达式）
  @Override public Expression visit(
      TernaryExpression ternary,
      Expression expression0,
      Expression expression1,
      Expression expression2) {
    // 优化：移除真值分支和假值分支中多余的null类型转换
    Expression tmpExpression1 = skipNullCast(expression1);
    Expression tmpExpression2 = skipNullCast(expression2);
    // 如果移除了null转换，则更新表达式并重建三元表达式
    if (tmpExpression1 != expression1 || tmpExpression2 != expression2) {
      expression1 = tmpExpression1;
      expression2 = tmpExpression2;
      ternary =
          new TernaryExpression(ternary.getNodeType(), ternary.getType(),
              expression0, expression1, expression2);
    }
    // 根据三元表达式的类型进行不同的优化
    switch (ternary.getNodeType()) {
    case Conditional: // 处理条件运算符 ? :
      // 检查条件表达式是否总是为true或false
      Boolean always = always(expression0);
      if (always != null) {
        // 如果条件总是为true，则直接返回真值分支：true ? y : z  ===  y
        // 如果条件总是为false，则直接返回假值分支：false ? y : z  === z
        return always
            ? expression1
            : expression2;
      }
      // 优化：如果两个分支相同，则直接返回该分支：a ? b : b   ===   b
      if (expression1.equals(expression2)) {
        return expression1;
      }
      // 优化：处理条件为否定的情况：!a ? b : c == a ? c : b（交换真值和假值分支）
      if (expression0 instanceof UnaryExpression) {
        UnaryExpression una = (UnaryExpression) expression0;
        if (una.getNodeType() == ExpressionType.Not) {
          return Expressions.makeTernary(ternary.getNodeType(),
              una.expression, expression2, expression1);
        }
      }

      // 优化：将三元表达式转换为逻辑运算符
      // a ? true : b  === a || b（如果条件为true，则整个表达式为true；否则为b）
      // a ? false : b === !a && b（如果条件为true，则整个表达式为false；否则为b）
      always = always(expression1);
      if (always != null && isKnownNotNull(expression2)) {
        return (always
                 ? Expressions.orElse(expression0, expression2)
                 : Expressions.andAlso(Expressions.not(expression0),
            expression2)).accept(this);
      }

      // 优化：将三元表达式转换为逻辑运算符
      // a ? b : true  === !a || b（如果条件为false，则整个表达式为true；否则为b）
      // a ? b : false === a && b（如果条件为false，则整个表达式为false；否则为b）
      always = always(expression2);
      if (always != null && isKnownNotNull(expression1)) {
        return (always
                 ? Expressions.orElse(Expressions.not(expression0),
                    expression1)
                 : Expressions.andAlso(expression0, expression1)).accept(this);
      }

      // 优化：处理条件是相等或不相等比较的情况
      if (expression0 instanceof BinaryExpression
          && (expression0.getNodeType() == ExpressionType.Equal
              || expression0.getNodeType() == ExpressionType.NotEqual)) {
        BinaryExpression cmp = (BinaryExpression) expression0;
        Expression expr = null;
        // 情况1：a == b ? b : a，如果a==b则返回a，否则返回b
        if (eq(cmp.expression0, expression2)
            && eq(cmp.expression1, expression1)) {
          // a == b ? b : a === a (hint: if a==b, then a == b ? a : a)
          // a != b ? b : a === b (hint: if a==b, then a != b ? b : b)
          expr = expression0.getNodeType() == ExpressionType.Equal
              ? expression2 : expression1;
        }
        // 情况2：a == b ? a : b，如果a==b则返回b，否则返回a
        if (eq(cmp.expression0, expression1)
            && eq(cmp.expression1, expression2)) {
          // a == b ? a : b === b (hint: if a==b, then a == b ? b : b)
          // a != b ? a : b === a (hint: if a==b, then a == b ? a : a)
          expr = expression0.getNodeType() == ExpressionType.Equal
              ? expression2 : expression1;
        }
        if (expr != null) {
          return expr;
        }
      }
      break;
    default:
      break;
    }
    // 如果没有应用优化，则调用父类的visit方法继续遍历
    return super.visit(ternary, expression0, expression1, expression2);
  }

  // 重写visit方法：访问和优化二元表达式
  // binary: 二元表达式节点
  // expression0: 左操作数
  // expression1: 右操作数
  @Override public Expression visit(
      BinaryExpression binary,
      Expression expression0,
      Expression expression1) {
    // 用于存储优化后的结果
    Expression result;
    // 第一轮优化：针对特定类型的二元表达式进行预处理
    switch (binary.getNodeType()) {
    case Assign: // 赋值运算符
      // 优化：移除赋值表达式中多余的null类型转换
      expression1 = skipNullCast(expression1);
      break;
    case AndAlso: // 逻辑与运算符 &&
    case OrElse: // 逻辑或运算符 ||
      // 优化：如果两个操作数相同，则直接返回该操作数：a && a === a, a || a === a
      if (eq(expression0, expression1)) {
        return expression0;
      }
      break;
    default:
      break;
    }
    // 第二轮优化：针对特定类型的二元表达式进行深度优化
    switch (binary.getNodeType()) {
    case Equal: // 相等运算符 ==
    case NotEqual: // 不相等运算符 !=
      // 优化：如果两个操作数相等，则根据运算符类型返回true或false
      if (eq(expression0, expression1)) {
        return binary.getNodeType() == Equal ? TRUE_EXPR : FALSE_EXPR;
      // 优化：如果两个操作数都是常量且类型相同（或都不是基本类型），则可以确定结果
      } else if (expression0 instanceof ConstantExpression && expression1
          instanceof ConstantExpression) {
        ConstantExpression c0 = (ConstantExpression) expression0;
        ConstantExpression c1 = (ConstantExpression) expression1;
        if (c0.getType() == c1.getType()
            || !(Primitive.is(c0.getType()) || Primitive.is(c1.getType()))) {
          return binary.getNodeType() == NotEqual ? TRUE_EXPR : FALSE_EXPR;
        }
      }
      // 优化：处理左操作数是三元表达式的情况
      if (expression0 instanceof TernaryExpression
          && expression0.getNodeType() == ExpressionType.Conditional) {
        TernaryExpression ternary = (TernaryExpression) expression0;
        Expression expr = null;
        // 情况1：(a ? b : c) == b === a || c == b（如果三元表达式的真值分支等于右操作数）
        if (eq(ternary.expression1, expression1)) {
          // (a ? b : c) == b === a || c == b
          expr =
              Expressions.orElse(ternary.expression0,
                  Expressions.equal(ternary.expression2, expression1));
        // 情况2：(a ? b : c) == c === !a || b == c（如果三元表达式的假值分支等于右操作数）
        } else if (eq(ternary.expression2, expression1)) {
          // (a ? b : c) == c === !a || b == c
          expr =
              Expressions.orElse(Expressions.not(ternary.expression0),
                  Expressions.equal(ternary.expression1, expression1));
        }
        if (expr != null) {
          // 如果原运算符是不相等，则对结果取反
          if (binary.getNodeType() == ExpressionType.NotEqual) {
            expr = Expressions.not(expr);
          }
          // 递归优化结果表达式
          return expr.accept(this);
        }
      }
      // 继续处理逻辑运算符的优化
      // fall through
    case AndAlso: // 逻辑与运算符 &&
    case OrElse: // 逻辑或运算符 ||
      // 尝试用左操作数优化二元表达式
      result = visit0(binary, expression0, expression1);
      if (result != null) {
        return result;
      }
      // 尝试用右操作数优化二元表达式（交换顺序）
      result = visit0(binary, expression1, expression0);
      if (result != null) {
        return result;
      }
      break;
    default:
      break;
    }
    // 如果没有应用优化，则调用父类的visit方法继续遍历
    return super.visit(binary, expression0, expression1);
  }

  // 私有辅助方法：用于visit方法中优化二元表达式
  // binary: 二元表达式节点
  // expression0: 第一个操作数（可能是左操作数或右操作数）
  // expression1: 第二个操作数（可能是右操作数或左操作数）
  // 返回值：优化后的表达式，如果无法优化则返回null
  private @Nullable Expression visit0(
      BinaryExpression binary,
      Expression expression0,
      Expression expression1) {
    Boolean always;
    // 根据二元表达式的类型进行优化
    switch (binary.getNodeType()) {
    case AndAlso: // 逻辑与运算符 &&
      // 检查第一个操作数是否总是为true或false
      always = always(expression0);
      if (always != null) {
        // 如果第一个操作数总是为true，则返回第二个操作数：true && x === x
        // 如果第一个操作数总是为false，则返回false：false && x === false
        return always
            ? expression1
            : FALSE_EXPR;
      }
      break;
    case OrElse: // 逻辑或运算符 ||
      // 检查第一个操作数是否总是为true或false
      always = always(expression0);
      if (always != null) {
        // true or x  --> true（如果第一个操作数总是为true）
        // false or x --> x（如果第一个操作数总是为false）
        return always
            ? TRUE_EXPR
            : expression1;
      }
      break;
    case Equal: // 相等运算符 ==
      // 优化：如果右操作数是null常量，且左操作数已知不为null，则返回false
      if (isConstantNull(expression1)
          && isKnownNotNull(expression0)) {
        return FALSE_EXPR;
      }
      // 优化：简化与布尔常量的比较
      // a == true  -> a（如果a总是为true或false，则直接返回结果）
      // a == false -> !a
      always = always(expression0);
      if (always != null) {
        return always ? expression1 : Expressions.not(expression1);
      }
      break;
    case NotEqual: // 不相等运算符 !=
      // 优化：如果右操作数是null常量，且左操作数已知不为null，则返回true
      if (isConstantNull(expression1)
          && isKnownNotNull(expression0)) {
        return TRUE_EXPR;
      }
      // 优化：简化与布尔常量的比较
      // a != true  -> !a
      // a != false -> a
      always = always(expression0);
      if (always != null) {
        return always ? Expressions.not(expression1) : expression1;
      }
      break;
    default:
      break;
    }
    // 如果无法优化，返回null
    return null;
  }

  // 重写visit方法：访问和优化一元表达式
  // unaryExpression: 一元表达式节点
  // expression: 操作数
  @Override public Expression visit(UnaryExpression unaryExpression,
      Expression expression) {
    // 根据一元表达式的类型进行优化
    switch (unaryExpression.getNodeType()) {
    case Convert: // 类型转换运算符
      // 优化：如果源类型和目标类型相同，则移除转换
      if (expression.getType() == unaryExpression.getType()) {
        return expression;
      }
      // 优化：如果操作数是常量，则直接转换常量的值和类型
      if (expression instanceof ConstantExpression) {
        return Expressions.constant(((ConstantExpression) expression).value,
            unaryExpression.getType());
      }
      break;
    case Not: // 逻辑非运算符 !
      // 检查操作数是否总是为true或false
      Boolean always = always(expression);
      if (always != null) {
        // 如果操作数总是为true，则返回false：!true === false
        // 如果操作数总是为false，则返回true：!false === true
        return always ? FALSE_EXPR : TRUE_EXPR;
      }
      // 优化：双重否定：!!a === a
      if (expression instanceof UnaryExpression) {
        UnaryExpression arg = (UnaryExpression) expression;
        if (arg.getNodeType() == ExpressionType.Not) {
          return arg.expression;
        }
      }
      // 优化：对二元比较表达式取反，转换为相反的比较运算符
      // 例如：!(a == b) === a != b，!(a > b) === a <= b
      if (expression instanceof BinaryExpression) {
        BinaryExpression bin = (BinaryExpression) expression;
        ExpressionType comp = NOT_BINARY_COMPLEMENT.get(bin.getNodeType());
        if (comp != null) {
          return Expressions.makeBinary(comp, bin.expression0, bin.expression1);
        }
      }
      break;
    default:
      break;
    }
    // 如果没有应用优化，则调用父类的visit方法继续遍历
    return super.visit(unaryExpression, expression);
  }

  // 重写visit方法：访问和优化条件语句（if-else语句）
  // conditionalStatement: 条件语句节点
  // list: 条件语句的列表，包含交替的条件和语句块，以及最后的else块（如果有）
  //       格式：[条件1, 语句块1, 条件2, 语句块2, ..., 条件N, 语句块N, else块]
  @Override public Statement visit(ConditionalStatement conditionalStatement,
      List<Node> list) {
    // 优化目标：移除永远不会执行的分支
    // if (false) { <-- 移除这个分支，因为它永远不会执行
    // } if (true) { <-- 在这里停止，因为后续分支永远不会执行
    // } else if (...)
    // } else {...}
    boolean optimal = true;
    // 第一次遍历：检查是否所有条件都无法优化
    for (int i = 0; i < list.size() - 1 && optimal; i += 2) {
      // 获取条件表达式（列表中偶数索引位置）
      Boolean always = always((Expression) list.get(i));
      if (always == null) {
        // 如果条件不是常量，继续检查下一个条件
        continue;
      }
      // 特殊情况：如果第一个条件总是为true，则直接返回其语句块
      if (i == 0 && always) {
        // when the very first test is always true, just return its statement
        return (Statement) list.get(1);
      }
      // 如果有任何一个条件可以优化，则标记为需要优化
      optimal = false;
    }
    // 如果所有条件都无法优化，则调用父类的visit方法
    if (optimal) {
      // Nothing to optimize
      return super.visit(conditionalStatement, list);
    }
    // 创建新的列表用于存储优化后的条件语句
    List<Node> newList = new ArrayList<>(list.size());
    // 第二次遍历：迭代所有条件，除了最后的"else"块
    for (int i = 0; i < list.size() - 1; i += 2) {
      Expression test = (Expression) list.get(i);
      Node stmt = list.get(i + 1);
      // 检查条件是否总是为true或false
      Boolean always = always(test);
      if (always == null) {
        // 如果条件不是常量，保留这个条件和语句块
        newList.add(test);
        newList.add(stmt);
        continue;
      }
      // 如果条件总是为true，则添加这个语句块并停止（后续分支永远不会执行）
      if (always) {
        // No need to verify other tests
        newList.add(stmt);
        break;
      }
      // 如果条件总是为false，则跳过这个条件和语句块（移除这个分支）
    }
    // 处理可能的悬挂"else"块
    // 如果列表只有一个元素，说明是 if (false) else if (false) else if (true) {...} 的情况
    // We might have dangling "else", however if we have just single item
    // it means we have if (false) else if (false) else if (true) {...} code.
    // Then we just return statement from true branch
    if (list.size() == 1) {
      return (Statement) list.get(0);
    }
    // 如果原始列表有else块（奇数个元素），且新列表有偶数个元素（只有条件和语句块），则添加else块
    // Add "else" from original list
    if (newList.size() % 2 == 0 && list.size() % 2 == 1) {
      Node elseBlock = list.get(list.size() - 1);
      // 如果新列表为空，说明所有条件都是false，直接返回else块
      if (newList.isEmpty()) {
        return (Statement) elseBlock;
      }
      newList.add(elseBlock);
    }
    // 如果新列表为空，说明没有可执行的分支，返回空语句
    if (newList.isEmpty()) {
      return EMPTY_STATEMENT;
    }
    // 递归优化新列表中的语句
    return super.visit(conditionalStatement, newList);
  }

  // 重写visit方法：访问和优化方法调用表达式
  // methodCallExpression: 方法调用表达式节点
  // targetExpression: 目标对象（实例方法调用时使用），静态方法调用时为null
  // expressions: 方法参数列表
  @Override public Expression visit(MethodCallExpression methodCallExpression,
      @Nullable Expression targetExpression,
      List<Expression> expressions) {
    // 优化：Boolean.valueOf(boolean)方法调用
    if (BOOLEAN_VALUEOF_BOOL.equals(methodCallExpression.method)) {
      // 检查参数是否总是为true或false
      Boolean always = always(expressions.get(0));
      if (always != null) {
        // 如果参数总是为true，则返回TRUE_EXPR
        // 如果参数总是为false，则返回FALSE_EXPR
        return always ? TRUE_EXPR : FALSE_EXPR;
      }
    }
    // 如果没有应用优化，则调用父类的visit方法继续遍历
    return super.visit(methodCallExpression, targetExpression, expressions);
  }

  // 私有静态方法：检查表达式是否为null常量
  // expression: 要检查的表达式
  // 返回值：如果表达式是值为null的常量表达式，则返回true；否则返回false
  private static boolean isConstantNull(Expression expression) {
    return expression instanceof ConstantExpression
        && ((ConstantExpression) expression).value == null;
  }

  // 私有静态方法：移除多余的null类型转换
  // null值可能有不同的类型表示，这个方法将它们统一为ConstantUntypedNull.INSTANCE
  // Remove redundant null casts.
  private static Expression skipNullCast(Expression expression) {
    // 如果表达式是值为null的常量表达式，则返回无类型的null常量
    if (expression instanceof ConstantExpression
        && ((ConstantExpression) expression).value == null) {
      return ConstantUntypedNull.INSTANCE;
    } else {
      // 否则返回原表达式
      return expression;
    }
  }

  /**
   * 判断表达式是否总是求值为true或false。
   * 假设表达式已经被优化过。
   *
   * <p>这个方法检查表达式是否为常量布尔值（true或false）。
   * 支持基本类型和包装类型的布尔常量。
   *
   * @param x 要检查的表达式
   * @return 如果表达式总是为true，则返回Boolean.TRUE；
   *         如果表达式总是为false，则返回Boolean.FALSE；
   *         如果表达式不是常量布尔值，则返回null
   */
  private static @Nullable Boolean always(Expression x) {
    // 检查表达式是否等于false常量（基本类型或包装类型）
    if (x.equals(FALSE_EXPR) || x.equals(BOXED_FALSE_EXPR)) {
      return Boolean.FALSE;
    }
    // 检查表达式是否等于true常量（基本类型或包装类型）
    if (x.equals(TRUE_EXPR) || x.equals(BOXED_TRUE_EXPR)) {
      return Boolean.TRUE;
    }
    // 如果表达式不是常量布尔值，返回null
    return null;
  }

  /**
   * 判断表达式是否总是返回非null结果。
   * 例如，基本类型不能包含null值。
   *
   * <p>这个方法检查表达式是否已知不会返回null，用于优化时的空值检查。
   * 以下情况被认为是已知非null的：
   * <ul>
   *   <li>表达式的类型是基本类型（int, boolean等）</li>
   *   <li>表达式是常量布尔值（true或false）</li>
   *   <li>表达式是已知非null方法的调用（如Boolean.valueOf）</li>
   * </ul>
   *
   * @param expression 要测试的表达式
   * @return 当表达式已知为非null时返回true，否则返回false
   */
  protected boolean isKnownNotNull(Expression expression) {
    // 检查表达式的类型是否是基本类型（基本类型不能为null）
    return Primitive.is(expression.getType())
        // 或者表达式是常量布尔值（true或false）
        || always(expression) != null
        // 或者表达式是已知非null方法的调用（如Boolean.valueOf）
        || (expression instanceof MethodCallExpression
            && KNOWN_NON_NULL_METHODS.contains(
                ((MethodCallExpression) expression).method));
  }

  /** 比较两个表达式是否相等，即使它们表示不同类型的null也视为相等。
   *
   * <p>这个方法用于表达式优化时的相等性检查。普通的equals方法会考虑null的类型，
   * 但在优化时，我们需要忽略null的类型差异，将所有null常量视为相等。
   *
   * <p>例如：
   * <ul>
   *   <li>两个完全相同的表达式：eq(a, a) 返回true</li>
   *   <li>两个值相同的常量表达式：eq(constant(5), constant(5)) 返回true</li>
   *   <li>两个null常量（即使类型不同）：eq(nullString, nullInteger) 返回true</li>
   * </ul>
   *
   * @param a 第一个表达式
   * @param b 第二个表达式
   * @return 如果两个表达式相等，或者都是值为null的常量表达式，则返回true；否则返回false
   */
  private static boolean eq(Expression a, Expression b) {
    // 首先使用普通的equals方法比较
    return a.equals(b)
        // 如果普通equals返回false，则检查是否都是值为null的常量表达式
        // 对于null常量，忽略类型差异，只要值都是null就视为相等
        || (a instanceof ConstantExpression
            && b instanceof ConstantExpression
            && ((ConstantExpression) a).value
                == ((ConstantExpression) b).value);
  }
}
