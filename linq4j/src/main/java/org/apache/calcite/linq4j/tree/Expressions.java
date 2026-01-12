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
package org.apache.calcite.linq4j.tree; // 包声明：定义该类属于org.apache.calcite.linq4j.tree包，这是Calcite LINQ4J表达式树的包

import org.apache.calcite.linq4j.Extensions; // 导入Extensions类，用于标记未实现的方法
import org.apache.calcite.linq4j.function.Function; // 导入Function接口，表示函数式接口
import org.apache.calcite.linq4j.function.Function0; // 导入Function0接口，表示无参函数
import org.apache.calcite.linq4j.function.Function1; // 导入Function1接口，表示单参数函数
import org.apache.calcite.linq4j.function.Function2; // 导入Function2接口，表示双参数函数
import org.apache.calcite.linq4j.function.Predicate1; // 导入Predicate1接口，表示单参数谓词函数
import org.apache.calcite.linq4j.function.Predicate2; // 导入Predicate2接口，表示双参数谓词函数

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类，用于创建不可修改的列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性注解，用于标记可能为null的值

import java.lang.reflect.Constructor; // 导入反射Constructor类，用于表示构造函数
import java.lang.reflect.Field; // 导入反射Field类，用于表示类的字段
import java.lang.reflect.Member; // 导入反射Member接口，表示类的成员（字段、方法、构造函数）
import java.lang.reflect.Method; // 导入反射Method类，用于表示类的方法
import java.lang.reflect.Type; // 导入反射Type接口，表示Java类型
import java.math.BigDecimal; // 导入BigDecimal类，用于高精度十进制计算
import java.math.BigInteger; // 导入BigInteger类，用于高精度整数计算
import java.math.RoundingMode; // 导入RoundingMode枚举，用于定义舍入模式
import java.util.ArrayList; // 导入ArrayList类，用于动态数组实现
import java.util.Arrays; // 导入Arrays工具类，用于数组操作
import java.util.Collection; // 导入Collection接口，表示集合
import java.util.Collections; // 导入Collections工具类，用于集合操作
import java.util.List; // 导入List接口，表示有序列表
import java.util.UUID; // 导入UUID类，用于生成唯一标识符

import static com.google.common.base.Preconditions.checkNotNull; // 导入Google Guava的前置条件检查方法，用于参数校验

import static java.util.Objects.requireNonNull; // 导入Java Objects的requireNonNull方法，用于参数非空检查

/**
 * Utility methods for expressions, including a lot of factory methods.
 * 表达式工具类，包含大量的工厂方法，用于创建各种类型的表达式树节点
 * 
 * 这个类是Calcite LINQ4J框架的核心工具类，提供了创建表达式树节点的静态工厂方法
 * 表达式树是一种用于表示代码逻辑的树形结构，可以动态地构建、修改和执行代码
 * 
 * 主要功能包括：
 * 1. 创建各种类型的表达式（二元表达式、一元表达式、常量表达式等）
 * 2. 创建语句（if语句、for循环、while循环等）
 * 3. 创建方法调用、字段访问、类型转换等操作
 * 4. 支持Lambda表达式和函数式编程
 * 5. 提供表达式树的遍历和转换功能
 * 
 * 该类模仿了.NET的Expression类，提供了类似的API用于构建表达式树
 * 所有方法都是静态的，通过工厂方法模式创建各种表达式节点
 */
public abstract class Expressions { // 抽象工具类，不能被实例化，所有方法都是静态的
  private Expressions() {} // 私有构造函数，防止实例化，确保该类只能通过静态方法使用

  /**
   * Converts a list of expressions to Java source code, optionally emitting
   * extra type information in generics.
   * 将表达式列表转换为Java源代码，可选择在泛型中发出额外的类型信息
   */
  public static String toString(List<? extends Node> expressions, String sep, // 参数：表达式列表、分隔符、是否输出泛型类型信息
      boolean generics) { // 参数：是否在输出中包含泛型类型信息
    final ExpressionWriter writer = new ExpressionWriter(generics); // 创建表达式写入器，根据generics参数决定是否输出泛型信息
    for (Node expression : expressions) { // 遍历表达式列表中的每个节点
      writer.write(expression); // 将当前表达式写入写入器
      writer.append(sep); // 在表达式后添加分隔符
    }   // 结束方法
    return writer.toString(); // 返回写入器生成的字符串形式的Java源代码
  }   // 结束方法

  /**
   * Converts an expression to Java source code.
   * 将单个表达式转换为Java源代码
   */
  public static String toString(Node expression) { // 参数：要转换的表达式节点
    return toString(Collections.singletonList(expression), "", true); // 调用重载方法，使用空分隔符并启用泛型输出
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an arithmetic
   * addition operation that does not have overflow checking.
   * 创建一个表示算术加法运算的二元表达式，不进行溢出检查
   */
  public static BinaryExpression add(Expression left, Expression right) { // 参数：左操作数、右操作数
    return makeBinary(ExpressionType.Add, left, right); // 调用makeBinary方法创建Add类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an arithmetic
   * addition operation that does not have overflow checking. The
   * implementing method can be specified.
   * 创建一个表示算术加法运算的二元表达式，不进行溢出检查，可以指定实现方法
   */
  public static BinaryExpression add(Expression left, Expression right, // 参数：左操作数、右操作数
      Method method) { // 参数：实现加法运算的方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an addition
   * assignment operation that does not have overflow checking.
   * 创建一个表示加法赋值运算的二元表达式，不进行溢出检查（如 += 运算符）
   */
  public static BinaryExpression addAssign(Expression left, Expression right) { // 参数：左操作数（目标）、右操作数
    return makeBinary(ExpressionType.AddAssign, left, right); // 调用makeBinary方法创建AddAssign类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an addition
   * assignment operation that does not have overflow checking.
   * 创建一个表示加法赋值运算的二元表达式，不进行溢出检查，可以指定实现方法
   */
  public static BinaryExpression addAssign(Expression left, Expression right, // 参数：左操作数、右操作数
      Method method) { // 参数：实现加法赋值运算的方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an addition
   * assignment operation that does not have overflow checking.
   * 创建一个表示加法赋值运算的二元表达式，不进行溢出检查，可以指定实现方法和Lambda表达式
   */
  public static BinaryExpression addAssign(Expression left, Expression right, // 参数：左操作数、右操作数
      Method method, LambdaExpression lambdaLeft, // 参数：实现方法、左Lambda表达式
      LambdaExpression lambdaRight) { // 参数：右Lambda表达式
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an addition
   * assignment operation that has overflow checking.
   * 创建一个表示加法赋值运算的二元表达式，进行溢出检查
   */
  public static BinaryExpression addAssignChecked(Expression left, // 参数：左操作数
      Expression right) { // 参数：右操作数
    return makeBinary(ExpressionType.AddAssignChecked, left, right); // 调用makeBinary方法创建AddAssignChecked类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an addition
   * assignment operation that has overflow checking.
   * 创建一个表示加法赋值运算的二元表达式，进行溢出检查，可以指定实现方法
   */
  public static BinaryExpression addAssignChecked(Expression left, // 参数：左操作数
      Expression right, Method method) { // 参数：右操作数、实现方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an addition
   * assignment operation that has overflow checking.
   * 创建一个表示加法赋值运算的二元表达式，进行溢出检查，可以指定实现方法和Lambda表达式
   */
  public static BinaryExpression addAssignChecked(Expression left, // 参数：左操作数
      Expression right, Method method, LambdaExpression lambdaExpression) { // 参数：右操作数、实现方法、Lambda表达式
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an arithmetic
   * addition operation that has overflow checking.
   * 创建一个表示算术加法运算的二元表达式，进行溢出检查
   */
  public static BinaryExpression addChecked(Expression left, Expression right) { // 参数：左操作数、右操作数
    return makeBinary(ExpressionType.AddChecked, left, right); // 调用makeBinary方法创建AddChecked类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an arithmetic
   * addition operation that has overflow checking. The implementing
   * method can be specified.
   * 创建一个表示算术加法运算的二元表达式，进行溢出检查，可以指定实现方法
   */
  public static BinaryExpression addChecked(Expression left, Expression right, // 参数：左操作数、右操作数
      Method method) { // 参数：实现加法运算的方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise AND
   * operation.
   * 创建一个表示按位与运算的二元表达式（& 运算符）
   */
  public static BinaryExpression and(Expression left, Expression right) { // 参数：左操作数、右操作数
    return makeBinary(ExpressionType.And, left, right); // 调用makeBinary方法创建And类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise AND
   * operation. The implementing method can be specified.
   * 创建一个表示按位与运算的二元表达式，可以指定实现方法
   */
  public static BinaryExpression and(Expression left, Expression right, // 参数：左操作数、右操作数
      Method method) { // 参数：实现按位与运算的方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a conditional AND
   * operation that evaluates the second operand only if the first
   * operand evaluates to true.
   * 创建一个表示条件与运算的二元表达式（&& 运算符），只有第一个操作数为true时才计算第二个操作数
   */
  public static BinaryExpression andAlso(Expression left, Expression right) { // 参数：左操作数、右操作数
    return makeBinary(ExpressionType.AndAlso, left, right); // 调用makeBinary方法创建AndAlso类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a conditional AND
   * operation that evaluates the second operand only if the first
   * operand is resolved to true. The implementing method can be
   * specified.
   * 创建一个表示条件与运算的二元表达式，可以指定实现方法
   */
  public static BinaryExpression andAlso(Expression left, Expression right, // 参数：左操作数、右操作数
      Method method) { // 参数：实现条件与运算的方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise AND
   * assignment operation.
   * 创建一个表示按位与赋值运算的二元表达式（&= 运算符）
   */
  public static BinaryExpression andAssign(Expression left, Expression right) { // 参数：左操作数（目标）、右操作数
    return makeBinary(ExpressionType.AndAssign, left, right); // 调用makeBinary方法创建AndAssign类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise AND
   * assignment operation.
   * 创建一个表示按位与赋值运算的二元表达式，可以指定实现方法
   */
  public static BinaryExpression andAssign(Expression left, Expression right, // 参数：左操作数、右操作数
      Method method) { // 参数：实现按位与赋值运算的方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise AND
   * assignment operation.
   * 创建一个表示按位与赋值运算的二元表达式，可以指定实现方法和Lambda表达式
   */
  public static BinaryExpression andAssign(Expression left, Expression right, // 参数：左操作数、右操作数
      Method method, LambdaExpression lambdaExpression) { // 参数：实现方法、Lambda表达式
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates an expression that represents applying an array
   * index operator to an array of rank one.
   * 创建一个表示对一维数组应用索引操作符的表达式（如 array[index]）
   */
  public static IndexExpression arrayIndex(Expression array, // 参数：数组表达式
      Expression indexExpression) { // 参数：索引表达式
    return new IndexExpression(array, // 创建索引表达式，传入数组和索引
        Collections.singletonList(indexExpression)); // 将索引表达式包装为单元素列表
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents an expression for
   * obtaining the length of a one-dimensional array.
   * 创建一个表示获取一维数组长度的一元表达式（如 array.length）
   */
  public static UnaryExpression arrayLength(Expression array) { // 参数：数组表达式
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an assignment
   * operation.
   * 创建一个表示赋值运算的二元表达式（= 运算符）
   */
  public static BinaryExpression assign(Expression left, Expression right) { // 参数：左操作数（目标）、右操作数（值）
    return makeBinary(ExpressionType.Assign, left, right); // 调用makeBinary方法创建Assign类型的二元表达式
  }   // 结束方法

  /**
   * Creates a MemberAssignment that represents the initialization
   * of a field or property.
   * 创建一个表示字段或属性初始化的成员赋值表达式
   */
  public static MemberAssignment bind(Member member, Expression right) { // 参数：成员（字段或属性）、赋值表达式
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a MemberAssignment that represents the initialization
   * of a member by using a property accessor method.
   * 创建一个表示使用属性访问器方法初始化成员的成员赋值表达式
   */
  public static MemberAssignment bind(Method method, Expression expression) { // 参数：属性访问器方法、初始化表达式
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BlockExpression that contains the given statements.
   * 创建一个包含给定语句的代码块表达式
   */
  public static BlockStatement block( // 方法名：创建代码块
      Iterable<? extends Statement> statements) { // 参数：语句的可迭代集合
    return block((Type) null, statements); // 调用重载方法，类型参数为null，自动推断
  }   // 结束方法

  /**
   * Creates a BlockExpression that contains the given statements,
   * using varargs.
   * 创建一个包含给定语句的代码块表达式，使用可变参数
   */
  public static BlockStatement block(Statement... statements) { // 参数：可变数量的语句
    return block(toList(statements)); // 将语句数组转换为列表后调用重载方法
  }   // 结束方法

  /**
   * Creates a BlockExpression that contains the given expressions,
   * has no variables and has specific result type.
   * 创建一个包含给定表达式的代码块，没有变量声明，具有指定的结果类型
   */
  public static BlockStatement block(@Nullable Type type, // 参数：代码块的结果类型，可为null自动推断
      Iterable<? extends Statement> expressions) { // 参数：语句的可迭代集合
    List<Statement> list = toList(expressions); // 将可迭代语句转换为列表
    if (type == null) { // 如果类型参数为null
      if (!list.isEmpty()) { // 如果语句列表不为空
        type = list.get(list.size() - 1).getType(); // 使用最后一个语句的类型作为代码块类型
      } else { // 如果语句列表为空
        type = Void.TYPE; // 使用void类型
      }   // 结束方法
    }   // 结束方法
    return new BlockStatement(list, type); // 创建并返回代码块语句对象
  }   // 结束方法

  /**
   * Creates a BlockExpression that contains the given statements
   * and has a specific result type, using varargs.
   * 创建一个包含给定语句的代码块，具有指定的结果类型，使用可变参数
   */
  public static BlockStatement block(@Nullable Type type, // 参数：代码块的结果类型
      Statement... statements) { // 参数：可变数量的语句
    return block(type, toList(statements)); // 将语句数组转换为列表后调用重载方法
  }   // 结束方法

  /**
   * Creates a GotoExpression representing a break statement.
   * 创建一个表示break语句的跳转表达式
   */
  public static GotoStatement break_(@Nullable LabelTarget labelTarget) { // 参数：跳转标签目标，可为null
    return new GotoStatement(GotoExpressionKind.Break, null, null); // 创建Break类型的跳转语句，无目标和表达式
  }   // 结束方法

  /**
   * Creates a GotoExpression representing a break statement. The
   * value passed to the label upon jumping can be specified.
   * 创建一个表示break语句的跳转表达式，可以指定跳转时传递的值
   */
  public static GotoStatement break_(@Nullable LabelTarget labelTarget, // 参数：跳转标签目标
      Expression expression) { // 参数：跳转时传递的表达式值
    return new GotoStatement(GotoExpressionKind.Break, null, expression); // 创建Break类型的跳转语句，带表达式值
  }   // 结束方法

  /**
   * Creates a GotoExpression representing a break statement with
   * the specified type.
   * 创建一个表示break语句的跳转表达式，具有指定的类型
   */
  public static GotoStatement break_(LabelTarget labelTarget, Type type) { // 参数：标签目标、类型
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a GotoExpression representing a break statement with
   * the specified type. The value passed to the label upon jumping
   * can be specified.
   * 创建一个表示break语句的跳转表达式，具有指定的类型和跳转值
   */
  public static GotoStatement break_(LabelTarget labelTarget, // 参数：标签目标
      Expression expression, Type type) { // 参数：跳转表达式值、类型
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a MethodCallExpression that represents a call to a
   * static method that has arguments.
   * 创建一个表示调用带参数的静态方法的表达式
   */
  public static MethodCallExpression call(Method method, // 参数：要调用的静态方法
      Iterable<? extends Expression> arguments) { // 参数：参数表达式的可迭代集合
    return new MethodCallExpression(method, null, toList(arguments)); // 创建方法调用表达式，目标表达式为null表示静态方法
  }   // 结束方法

  /**
   * Creates a MethodCallExpression that represents a call to a
   * static method that has arguments, using varargs.
   * 创建一个表示调用带参数的静态方法的表达式，使用可变参数
   */
  public static MethodCallExpression call(Method method, // 参数：要调用的静态方法
      Expression... arguments) { // 参数：可变数量的参数表达式
    return new MethodCallExpression(method, null, toList(arguments)); // 创建方法调用表达式，目标表达式为null表示静态方法
  }   // 结束方法

  /**
   * Creates a MethodCallExpression that represents a call to a
   * method that takes arguments.
   * 创建一个表示调用带参数的实例方法的表达式
   */
  public static MethodCallExpression call(@Nullable Expression expression, Method method, // 参数：目标对象表达式、要调用的方法
      Iterable<? extends Expression> arguments) { // 参数：参数表达式的可迭代集合
    return new MethodCallExpression(method, expression, toList(arguments)); // 创建方法调用表达式，包含目标对象和方法
  }   // 结束方法

  /**
   * Creates a MethodCallExpression that represents a call to a
   * method that takes arguments, using varargs.
   * 创建一个表示调用带参数的实例方法的表达式，使用可变参数
   */
  public static MethodCallExpression call(@Nullable Expression expression, Method method, // 参数：目标对象表达式、要调用的方法
      Expression... arguments) { // 参数：可变数量的参数表达式
    return new MethodCallExpression(method, expression, toList(arguments)); // 创建方法调用表达式，包含目标对象和方法
  }   // 结束方法

  /**
   * Creates a MethodCallExpression that represents a call to a
   * method that takes arguments, with an explicit return type.
   *
   * <p>The return type must be consistent with the return type of the method,
   * but may contain extra information, such as type parameters.
   *
   * <p>The {@code expression} argument may be null if and only if the method
   * is static.
   * 创建一个表示调用带参数的方法的表达式，具有显式的返回类型
   * 返回类型必须与方法返回类型一致，但可以包含额外信息，如类型参数
   * expression参数当且仅当方法是静态时可以为null
   */
  public static MethodCallExpression call(Type returnType, // 参数：显式指定的返回类型
      @Nullable Expression expression, Method method, // 参数：目标对象表达式、要调用的方法
      Iterable<? extends Expression> arguments) { // 参数：参数表达式的可迭代集合
    return new MethodCallExpression(returnType, method, expression, // 创建方法调用表达式，包含返回类型、方法、目标对象和参数
        toList(arguments)); // 将参数转换为列表
  }   // 结束方法

  /**
   * Creates a MethodCallExpression that represents a call to a
   * method that takes arguments, with an explicit return type, with varargs.
   *
   * <p>The return type must be consistent with the return type of the method,
   * but may contain extra information, such as type parameters.
   *
   * <p>The {@code expression} argument may be null if and only if the method
   * is static.
   * 创建一个表示调用带参数的方法的表达式，具有显式的返回类型，使用可变参数
   */
  public static MethodCallExpression call(Type returnType, // 参数：显式指定的返回类型
      @Nullable Expression expression, Method method, // 参数：目标对象表达式、要调用的方法
      Expression... arguments) { // 参数：可变数量的参数表达式
    return new MethodCallExpression(returnType, method, expression, // 创建方法调用表达式，包含返回类型、方法、目标对象和参数
        toList(arguments)); // 将参数转换为列表
  }   // 结束方法

  /**
   * Creates a MethodCallExpression that represents a call to an
   * instance method by calling the appropriate factory method.
   * 创建一个表示调用实例方法的表达式，通过方法名动态查找方法
   */
  public static MethodCallExpression call(Expression target, String methodName, // 参数：目标对象表达式、方法名
      Iterable<? extends Expression> arguments) { // 参数：参数表达式的可迭代集合
    Method method; // 声明方法变量
    try {
      //noinspection unchecked
      method = Types.toClass(target.getType()) // 获取目标对象的类
          .getMethod(methodName, Types.toClassArray(arguments)); // 根据方法名和参数类型获取方法
    } catch (NoSuchMethodException e) { // 捕获方法不存在异常
      throw new RuntimeException("while resolving method '" + methodName // 抛出运行时异常，包含错误信息
          + "' in class " + target.getType(), e); // 包含方法名和类名
    }   // 结束方法
    return call(target, method, arguments); // 调用重载方法创建方法调用表达式
  }   // 结束方法

  /**
   * Creates a MethodCallExpression that represents a call to an
   * instance method by calling the appropriate factory method, using varargs.
   * 创建一个表示调用实例方法的表达式，通过方法名动态查找方法，使用可变参数
   */
  public static MethodCallExpression call(Expression target, String methodName, // 参数：目标对象表达式、方法名
      Expression... arguments) { // 参数：可变数量的参数表达式
    return call(target, methodName, toList(arguments)); // 将参数转换为列表后调用重载方法
  }   // 结束方法

  /**
   * Creates a MethodCallExpression that represents a call to a
   * static method by calling the
   * appropriate factory method.
   * 创建一个表示调用静态方法的表达式，通过类型和方法名查找方法
   */
  public static MethodCallExpression call(Type type, String methodName, // 参数：类型、方法名
      Iterable<? extends Expression> arguments) { // 参数：参数表达式的可迭代集合
    Method method = // 声明方法变量
        Types.lookupMethod(Types.toClass(type), methodName, // 根据类型、方法名和参数类型查找方法
            Types.toClassArray(arguments)); // 将参数表达式转换为类数组
    return new MethodCallExpression(method, null, toList(arguments)); // 创建方法调用表达式，目标为null表示静态方法
  }   // 结束方法

  /**
   * Creates a MethodCallExpression that represents a call to a
   * static method by calling the
   * appropriate factory method, using varargs.
   * 创建一个表示调用静态方法的表达式，通过类型和方法名查找方法，使用可变参数
   */
  public static MethodCallExpression call(Type type, String methodName, // 参数：类型、方法名
      Expression... arguments) { // 参数：可变数量的参数表达式
    return call(type, methodName, toList(arguments)); // 将参数转换为列表后调用重载方法
  }   // 结束方法

  /**
   * Creates a CatchBlock representing a catch statement with a
   * reference to the caught Exception object for use in the handler
   * body.
   * 创建一个表示catch语句的catch块，包含对捕获的异常对象的引用，可在处理体中使用
   */
  public static CatchBlock catch_(ParameterExpression parameter, // 参数：表示异常参数的参数表达式
      Statement statement) { // 参数：catch块的处理语句
    return new CatchBlock(parameter, statement); // 创建并返回catch块对象
  }   // 结束方法

  /**
   * Creates a DebugInfoExpression for clearing a sequence
   * point.
   * 创建一个用于清除序列点的调试信息表达式
   */
  public static void clearDebugInfo() { // 无参数方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a coalescing
   * operation.
   * 创建一个表示空值合并运算的二元表达式（如 C# 的 ?? 运算符）
   */
  public static BinaryExpression coalesce(Expression left, Expression right) { // 参数：左操作数、右操作数
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a coalescing
   * operation, given a conversion function.
   * 创建一个表示空值合并运算的二元表达式，给定转换函数
   */
  public static BinaryExpression coalesce(Expression left, Expression right, // 参数：左操作数、右操作数
      LambdaExpression lambdaExpression) { // 参数：转换函数的Lambda表达式
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a ConditionalExpression that represents a conditional
   * statement.
   * 创建一个表示条件语句的条件表达式（三元运算符 ?:）
   */
  public static Expression condition(Expression test, Expression ifTrue, // 参数：测试条件、条件为真时的表达式
      Expression ifFalse) { // 参数：条件为假时的表达式
    return makeTernary(ExpressionType.Conditional, test, ifTrue, ifFalse); // 调用makeTernary方法创建条件表达式
  }   // 结束方法

  /** Returns whether an expression always evaluates to null.
   * 返回表达式是否总是求值为null
   */
  public static boolean isConstantNull(Expression e) { // 参数：要检查的表达式
    return e instanceof ConstantExpression // 检查表达式是否为常量表达式
           && ((ConstantExpression) e).value == null; // 并且其值为null
  }   // 结束方法

  /**
   * Creates a ConditionalExpression that represents a conditional
   * statement.
   *
   * <p>This method allows explicitly unifying the result type of the
   * conditional expression in cases where the types of ifTrue and ifFalse
   * expressions are not equal. Types of both ifTrue and ifFalse must be
   * implicitly reference assignable to the result type. The type is allowed
   * to be {@link Void#TYPE void}.
   * 创建一个表示条件语句的条件表达式，可以显式统一结果类型
   * 当ifTrue和ifFalse表达式的类型不相等时，此方法允许显式统一条件表达式的结果类型
   * ifTrue和ifFalse的类型必须可以隐式引用赋值给结果类型，类型允许为void
   */
  public static ConditionalExpression condition(Expression test, // 参数：测试条件
      Expression ifTrue, Expression ifFalse, Type type) { // 参数：真值表达式、假值表达式、结果类型
    return new ConditionalExpression(Arrays.asList(test, ifFalse, ifTrue), // 创建条件表达式，注意参数顺序：test、ifFalse、ifTrue
        type); // 使用指定的结果类型
  }   // 结束方法

  /**
   * Creates a ConstantExpression that has the Value property set
   * to the specified value.
   *
   * <p>Does the right thing for null, String, primitive values (e.g. int 12,
   * short 12, double 3.14 and boolean false), boxed primitives
   * (e.g. Integer.valueOf(12)), enums, classes, BigDecimal, BigInteger,
   * classes that have a constructor with a parameter for each field, and
   * arrays.
   * 创建一个常量表达式，其Value属性设置为指定值
   * 正确处理null、String、基本类型值（如int 12、short 12、double 3.14和boolean false）、
   * 装箱基本类型（如Integer.valueOf(12)）、枚举、类、BigDecimal、BigInteger、
   * 具有字段参数构造函数的类以及数组
   */
  public static ConstantExpression constant(@Nullable Object value) { // 参数：常量值，可为null
    if (value == null) { // 如果值为null
      return ConstantUntypedNull.INSTANCE; // 返回未类型化的null常量实例
    }   // 结束方法
    Class<?> type = Primitive.unbox(value.getClass()); // 获取值的基本类型（如果是包装类型则拆箱）
    return new ConstantExpression(type, value); // 创建并返回常量表达式
  }   // 结束方法

  /**
   * Creates a ConstantExpression that has the Value and Type
   * properties set to the specified values.
   * 创建一个常量表达式，其Value和Type属性设置为指定值
   */
  public static ConstantExpression constant(@Nullable Object value, Type type) { // 参数：常量值、类型
    return constant(value, type, RoundingMode.DOWN); // 调用重载方法，使用向下舍入模式
  }   // 结束方法

  /**
   * Creates a ConstantExpression that has the Value 、Type 、RoundingMode
   * properties set to the specified values.
   * 创建一个常量表达式，其Value、Type、RoundingMode属性设置为指定值
   */
  public static ConstantExpression constant(@Nullable Object value, Type type, // 参数：常量值、类型
      RoundingMode roundingMode) { // 参数：舍入模式
    if (value != null && type instanceof Class) { // 如果值不为null且类型是Class
      // Fix up value so that it matches type.
      Class<?> clazz = (Class<?>) type; // 将类型转换为Class对象
      Primitive primitive = Primitive.ofBoxOr(clazz); // 获取类型对应的基本类型
      if (primitive != null) { // 如果是基本类型
        clazz = requireNonNull(primitive.boxClass, "boxClass"); // 使用包装类
      }   // 结束方法
      if ((clazz == Float.class || clazz == Double.class) // 如果是Float或Double类型
          && value instanceof BigDecimal) { // 并且值是BigDecimal
        // Don't try to convert the value of float and double literals.
        // We'd experience rounding, e.g. 3.2 becomes 3.1999998.
        // 不尝试转换float和double字面量的值，否则会经历舍入，如3.2变成3.1999998
      } else if (!clazz.isInstance(value)) { // 如果值不是该类的实例
        String stringValue = String.valueOf(value); // 将值转换为字符串
        if (type == BigDecimal.class) { // 如果目标类型是BigDecimal
          value = new BigDecimal(stringValue); // 从字符串创建BigDecimal
        }   // 结束方法
        if (type == BigInteger.class) { // 如果目标类型是BigInteger
          value = new BigInteger(stringValue); // 从字符串创建BigInteger
        }   // 结束方法
        if (primitive != null) { // 如果是基本类型
          if (value instanceof Number) { // 如果值是数字
            Number valueNumber = (Number) value; // 转换为Number类型
            value = primitive.numberValue(valueNumber, roundingMode); // 使用指定舍入模式转换值
            if (value == null) { // 如果转换结果为null
              value = primitive.parse(stringValue); // 解析字符串值
            }   // 结束方法
          } else { // 如果值不是数字
            value = primitive.parse(stringValue); // 解析字符串值
          }   // 结束方法
        }   // 结束方法
      }   // 结束方法
    }   // 结束方法
    return new ConstantExpression(type, value); // 创建并返回常量表达式
  }   // 结束方法

  /**
   * Creates a GotoExpression representing a continue statement.
   * 创建一个表示continue语句的跳转表达式
   */
  public static GotoStatement continue_(LabelTarget labelTarget) { // 参数：跳转标签目标
    return new GotoStatement(GotoExpressionKind.Continue, null, null); // 创建Continue类型的跳转语句
  }   // 结束方法

  /**
   * Creates a GotoExpression representing a continue statement
   * with the specified type.
   * 创建一个表示continue语句的跳转表达式，具有指定的类型
   */
  public static GotoStatement continue_(LabelTarget labelTarget, Type type) { // 参数：标签目标、类型
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents a type conversion
   * operation.
   * 创建一个表示类型转换操作的一元表达式
   */
  public static UnaryExpression convert_(Expression expression, Type type) { // 参数：要转换的表达式、目标类型
    return new UnaryExpression(ExpressionType.Convert, type, expression); // 创建Convert类型的一元表达式
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents a conversion
   * operation for which the implementing method is specified.
   * 创建一个表示类型转换操作的一元表达式，可以指定实现方法
   */
  public static UnaryExpression convert_(Expression expression, Type type, // 参数：要转换的表达式、目标类型
      Method method) { // 参数：实现转换的方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents a conversion
   * operation that throws an exception if the target type is
   * overflowed.
   * 创建一个表示类型转换操作的一元表达式，如果目标类型溢出则抛出异常
   */
  public static Expression convertChecked(Expression expression, Type type) { // 参数：要转换的表达式、目标类型
    return new UnaryExpression(ExpressionType.ConvertChecked, type, expression); // 创建ConvertChecked类型的一元表达式
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents a conversion
   * operation that throws an exception if the target type is
   * overflowed and for which the implementing method is
   * specified.
   * 创建一个表示类型转换操作的一元表达式，如果目标类型溢出则抛出异常，可以指定实现方法
   */
  public static UnaryExpression convertChecked_(Expression expression, // 参数：要转换的表达式
      Type type, Method method) { // 参数：目标类型、实现转换的方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a DebugInfoExpression with the specified span.
   * 创建一个具有指定范围的调试信息表达式
   */
  public static void debugInfo() { // 无参数方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents the decrementing of
   * the expression by 1.
   * 创建一个表示将表达式值减1的一元表达式（-- 运算符）
   */
  public static UnaryExpression decrement(Expression expression) { // 参数：要递减的表达式
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents the decrementing of
   * the expression by 1.
   * 创建一个表示将表达式值减1的一元表达式，可以指定实现方法
   */
  public static UnaryExpression decrement(Expression expression, // 参数：要递减的表达式
      Method method) { // 参数：实现递减的方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a DefaultExpression that has the Type property set to
   * the specified type.
   * 创建一个默认表达式，其Type属性设置为指定类型
   */
  public static DefaultExpression default_() { // 无参数方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an arithmetic
   * division operation.
   * 创建一个表示算术除法运算的二元表达式（/ 运算符）
   */
  public static BinaryExpression divide(Expression left, Expression right) { // 参数：左操作数（被除数）、右操作数（除数）
    return makeBinary(ExpressionType.Divide, left, right); // 调用makeBinary方法创建Divide类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an arithmetic
   * division operation. The implementing method can be
   * specified.
   * 创建一个表示算术除法运算的二元表达式，可以指定实现方法
   */
  public static BinaryExpression divide(Expression left, Expression right, // 参数：左操作数、右操作数
      Method method) { // 参数：实现除法运算的方法
    return makeBinary(ExpressionType.Divide, left, right, // 调用makeBinary方法创建Divide类型的二元表达式
        shouldLift(left, right, method), method); // 判断是否需要提升操作，传入方法参数
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a division
   * assignment operation that does not have overflow checking.
   * 创建一个表示除法赋值运算的二元表达式，不进行溢出检查（/= 运算符）
   */
  public static BinaryExpression divideAssign(Expression left, // 参数：左操作数（目标）
      Expression right) { // 参数：右操作数
    return makeBinary(ExpressionType.DivideAssign, left, right); // 调用makeBinary方法创建DivideAssign类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a division
   * assignment operation that does not have overflow checking.
   * 创建一个表示除法赋值运算的二元表达式，不进行溢出检查，可以指定实现方法
   */
  public static BinaryExpression divideAssign(Expression left, // 参数：左操作数
      Expression right, Method method) { // 参数：右操作数、实现方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a division
   * assignment operation that does not have overflow checking.
   * 创建一个表示除法赋值运算的二元表达式，不进行溢出检查，可以指定实现方法和Lambda表达式
   */
  public static BinaryExpression divideAssign(Expression left, // 参数：左操作数
      Expression right, Method method, LambdaExpression lambdaExpression) { // 参数：右操作数、实现方法、Lambda表达式
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a DynamicExpression that represents a dynamic
   * operation bound by the provided CallSiteBinder.
   * 创建一个表示动态操作的动态表达式，由提供的CallSiteBinder绑定
   */
  public static DynamicExpression dynamic(CallSiteBinder binder, Type type, // 参数：调用站点绑定器、类型
      Iterable<? extends Expression> expressions) { // 参数：表达式集合
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a {@code DynamicExpression} that represents a dynamic
   * operation bound by the provided {@code CallSiteBinder}, using varargs.
   * 创建一个表示动态操作的动态表达式，由提供的CallSiteBinder绑定，使用可变参数
   */
  public static DynamicExpression dynamic(CallSiteBinder binder, Type type, // 参数：调用站点绑定器、类型
      Expression... expression) { // 参数：可变数量的表达式
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates an {@code ElementInit}, given an {@code Iterable<T>} as the second
   * argument.
   * 创建一个元素初始化表达式，给定一个可迭代集合作为第二个参数
   */
  public static ElementInit elementInit(Method method, // 参数：初始化方法
      Iterable<? extends Expression> expressions) { // 参数：表达式集合
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates an ElementInit, given an array of values as the second
   * argument, using varargs.
   * 创建一个元素初始化表达式，给定一个值数组作为第二个参数，使用可变参数
   */
  public static ElementInit elementInit(Method method, // 参数：初始化方法
      Expression... expressions) { // 参数：可变数量的表达式
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates an empty expression that has Void type.
   * 创建一个具有Void类型的空表达式
   */
  public static DefaultExpression empty() { // 无参数方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an equality
   * comparison.
   * 创建一个表示相等比较的二元表达式（== 运算符）
   */
  public static BinaryExpression equal(Expression left, Expression right) { // 参数：左操作数、右操作数
    return makeBinary(ExpressionType.Equal, left, right); // 调用makeBinary方法创建Equal类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an equality
   * comparison. The implementing method can be specified.
   * 创建一个表示相等比较的二元表达式，可以指定实现方法
   */
  public static BinaryExpression equal(Expression expression0, // 参数：第一个表达式
      Expression expression1, boolean liftToNull, Method method) { // 参数：第二个表达式、是否提升到null、实现方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise XOR
   * operation, using op_ExclusiveOr for user-defined types.
   * 创建一个表示按位异或运算的二元表达式（^ 运算符），对用户定义类型使用op_ExclusiveOr
   */
  public static BinaryExpression exclusiveOr(Expression left, // 参数：左操作数
      Expression right) { // 参数：右操作数
    return makeBinary(ExpressionType.ExclusiveOr, left, right); // 调用makeBinary方法创建ExclusiveOr类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise XOR
   * operation, using op_ExclusiveOr for user-defined types. The
   * implementing method can be specified.
   * 创建一个表示按位异或运算的二元表达式，可以指定实现方法
   */
  public static BinaryExpression exclusiveOr(Expression left, // 参数：左操作数
      Expression right, Method method) { // 参数：右操作数、实现方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise XOR
   * assignment operation, using op_ExclusiveOr for user-defined
   * types.
   * 创建一个表示按位异或赋值运算的二元表达式（^= 运算符）
   */
  public static BinaryExpression exclusiveOrAssign(Expression left, // 参数：左操作数（目标）
      Expression right) { // 参数：右操作数
    return makeBinary(ExpressionType.ExclusiveOrAssign, left, right); // 调用makeBinary方法创建ExclusiveOrAssign类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise XOR
   * assignment operation, using op_ExclusiveOr for user-defined
   * types.
   * 创建一个表示按位异或赋值运算的二元表达式，可以指定实现方法
   */
  public static BinaryExpression exclusiveOrAssign(Expression left, // 参数：左操作数
      Expression right, Method method) { // 参数：右操作数、实现方法
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise XOR
   * assignment operation, using op_ExclusiveOr for user-defined
   * types.
   * 创建一个表示按位异或赋值运算的二元表达式，可以指定实现方法和Lambda表达式
   */
  public static BinaryExpression exclusiveOrAssign(Expression left, // 参数：左操作数
      Expression right, Method method, LambdaExpression lambdaExpression) { // 参数：右操作数、实现方法、Lambda表达式
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a MemberExpression that represents accessing a field.
   * 创建一个表示访问字段的成员表达式
   */
  public static MemberExpression field(@Nullable Expression expression, Field field) { // 参数：目标对象表达式、字段
    return makeMemberAccess(expression, Types.field(field)); // 调用makeMemberAccess方法创建成员访问表达式
  }   // 结束方法

  /**
   * Creates a MemberExpression that represents accessing a field.
   * 创建一个表示访问伪字段的成员表达式
   */
  public static MemberExpression field(@Nullable Expression expression, // 参数：目标对象表达式
      PseudoField field) { // 参数：伪字段
    return makeMemberAccess(expression, field); // 调用makeMemberAccess方法创建成员访问表达式
  }   // 结束方法

  /**
   * Creates a MemberExpression that represents accessing a field
   * given the name of the field.
   * 创建一个表示通过字段名访问字段的成员表达式
   */
  public static MemberExpression field(Expression expression, // 参数：目标对象表达式
      String fieldName) { // 参数：字段名
    PseudoField field = Types.getField(fieldName, expression.getType()); // 根据字段名和类型获取字段
    return makeMemberAccess(expression, field); // 调用makeMemberAccess方法创建成员访问表达式
  }   // 结束方法

  /**
   * Creates a MemberExpression that represents accessing a field.
   * 创建一个表示访问字段的成员表达式，可以指定类型
   */
  public static MemberExpression field(@Nullable Expression expression, Type type, // 参数：目标对象表达式、类型
      String fieldName) { // 参数：字段名
    PseudoField field = Types.getField(fieldName, type); // 根据字段名和类型获取字段
    return makeMemberAccess(expression, field); // 调用makeMemberAccess方法创建成员访问表达式
  }   // 结束方法

  /**
   * Creates a Type object that represents a generic System.Action
   * delegate type that has specific type arguments.
   * 创建一个表示具有特定类型参数的泛型System.Action委托类型的Type对象
   */
  public static Class getActionType(Class... typeArgs) { // 参数：类型参数数组
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Gets a Type object that represents a generic System.Func or
   * System.Action delegate type that has specific type
   * arguments.
   * 获取一个表示具有特定类型参数的泛型System.Func或System.Action委托类型的Type对象
   */
  public static Class getDelegateType(Class... typeArgs) { // 参数：类型参数数组
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a Type object that represents a generic System.Func
   * delegate type that has specific type arguments. The last type
   * argument specifies the return type of the created delegate.
   * 创建一个表示具有特定类型参数的泛型System.Func委托类型的Type对象，最后一个类型参数指定委托的返回类型
   */
  public static Class getFuncType(Class... typeArgs) { // 参数：类型参数数组
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a GotoExpression representing a "go to" statement.
   * 创建一个表示goto语句的跳转表达式
   */
  public static GotoStatement goto_(LabelTarget labelTarget) { // 参数：跳转标签目标
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a GotoExpression representing a "go to" statement. The
   * value passed to the label upon jumping can be specified.
   * 创建一个表示goto语句的跳转表达式，可以指定跳转时传递的值
   */
  public static GotoStatement goto_(LabelTarget labelTarget, // 参数：跳转标签目标
      Expression expression) { // 参数：跳转时传递的表达式值
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a GotoExpression representing a "go to" statement with
   * the specified type.
   * 创建一个表示goto语句的跳转表达式，具有指定的类型
   */
  public static GotoStatement goto_(LabelTarget labelTarget, Type type) { // 参数：标签目标、类型
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a GotoExpression representing a "go to" statement with
   * the specified type. The value passed to the label upon jumping
   * can be specified.
   * 创建一个表示goto语句的跳转表达式，具有指定的类型和跳转值
   */
  public static GotoStatement goto_(LabelTarget labelTarget, // 参数：标签目标
      Expression expression, Type type) { // 参数：跳转表达式值、类型
    throw Extensions.todo(); // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a "greater than"
   * numeric comparison.
   * 创建一个表示"大于"数值比较的二元表达式（> 运算符）
   */
  public static BinaryExpression greaterThan(Expression left, // 参数：左操作数
      Expression right) { // 参数：右操作数
    return makeBinary(ExpressionType.GreaterThan, left, right); // 调用makeBinary方法创建GreaterThan类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a "greater than"
   * numeric comparison. The implementing method can be
   * specified.
   */
  public static BinaryExpression greaterThan(Expression left, Expression right,       // 参数：左操作数、右操作数
      boolean liftToNull, Method method) {     // 参数：是否提升到null、实现方法
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a "greater than or
   * equal" numeric comparison.
   */
  public static BinaryExpression greaterThanOrEqual(Expression left,       // 参数：左操作数
      Expression right) {     // 参数：右操作数
    return makeBinary(ExpressionType.GreaterThanOrEqual, left, right);     // 调用makeBinary方法创建GreaterThanOrEqual类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a "greater than or
   * equal" numeric comparison.
   */
  public static BinaryExpression greaterThanOrEqual(Expression left,       // 参数：左操作数
      Expression right, boolean liftToNull, Method method) {     // 参数：右操作数、是否提升到null、实现方法
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a ConditionalExpression that represents a conditional
   * block with an if statement.
   */
  public static ConditionalStatement ifThen(Expression test, Node ifTrue) {     // 参数：测试条件、条件为真时的语句
    return new ConditionalStatement(Arrays.asList(test, ifTrue));     // 创建条件语句，包含测试条件和真值语句
  }   // 结束方法

  /**
   * Creates a ConditionalExpression that represents a conditional
   * block with if and else statements.
   */
  public static ConditionalStatement ifThenElse(Expression test, Node ifTrue,     // 参数：测试条件、真值语句、假值语句
      Node ifFalse) {     // 参数：条件为假时的语句
    return new ConditionalStatement(Arrays.asList(test, ifTrue, ifFalse));     // 创建条件语句，包含测试条件、真值语句和假值语句
  }   // 结束方法

  /**
   * Creates a ConditionalExpression that represents a conditional
   * block with if and else statements:
   * <code>if (test) stmt1 [ else if (test2) stmt2 ]... [ else stmtN ]</code>.
   */
  public static ConditionalStatement ifThenElse(Expression test,     // 参数：测试条件
      Node... nodes) {     // 参数：可变数量的节点（真值语句和假值语句）
    return ifThenElse(new FluentArrayList<Node>().append(test)     // 创建流式列表，添加测试条件
        .appendAll(nodes));         // 添加所有节点
  }   // 结束方法

  /**
   * Creates a ConditionalExpression that represents a conditional
   * block with if and else statements:
   * <code>if (test) stmt1 [ else if (test2) stmt2 ]... [ else stmtN ]</code>.
   */
  public static ConditionalStatement ifThenElse(     // 方法名：创建if-else-if-else条件语句
      Iterable<? extends Node> nodes) {     // 参数：节点的可迭代集合
    List<Node> list = toList(nodes);     // 将节点集合转换为列表
    assert list.size() >= 2 : "At least one test and one statement is required";     // 断言至少需要一个测试条件和一个语句
    return new ConditionalStatement(list);     // 创建并返回条件语句
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents the incrementing of
   * the expression value by 1.
   */
  public static UnaryExpression increment(Expression expression) {     // 参数：要递增的表达式
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents the incrementing of
   * the expression by 1.
   */
  public static UnaryExpression increment(Expression expression,     // 参数：要递增的表达式
      Method method) {     // 参数：实现取模运算的方法
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates an InvocationExpression that applies a delegate or
   * lambda expression to a list of argument expressions.
   */
  public static InvocationExpression invoke(Expression expression,     // 参数：要调用的表达式
      Iterable<? extends Expression> arguments) {     // 参数：参数表达式集合
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates an InvocationExpression that applies a delegate or
   * lambda expression to a list of argument expressions, using varargs.
   */
  public static InvocationExpression invoke(Expression expression,     // 参数：要调用的表达式
      Expression... arguments) {     // 参数：可变数量的参数表达式
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Returns whether the expression evaluates to false.
   */
  public static UnaryExpression isFalse(Expression expression) {     // 参数：要检查的表达式
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Returns whether the expression evaluates to false.
   */
  public static UnaryExpression isFalse(Expression expression, Method method) {     // 参数：要检查的表达式、实现方法
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Returns whether the expression evaluates to true.
   */
  public static UnaryExpression isTrue(Expression expression) {     // 参数：要检查的表达式
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Returns whether the expression evaluates to true.
   */
  public static UnaryExpression isTrue(Expression expression, Method method) {     // 参数：要检查的表达式、实现方法
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a LabelTarget representing a label with X type and
   * no name.
   */
  public static LabelTarget label() {     // 无参数方法
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a LabelExpression representing a label without a
   * default value.
   */
  public static LabelStatement label(LabelTarget labelTarget) {     // 参数：标签目标
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a LabelTarget representing a label with X type and
   * the given name.
   */
  public static LabelTarget label(String name) {     // 参数：标签名称
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a LabelTarget representing a label with the given
   * type.
   */
  public static LabelTarget label(Type type) {     // 参数：标签类型
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a LabelExpression representing a label with the given
   * default value.
   */
  public static LabelStatement label(LabelTarget labelTarget,     // 参数：标签目标
      Expression expression) {     // 参数：操作数
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a LabelTarget representing a label with the given type
   * and name.
   */
  public static LabelTarget label(Type type, String name) {     // 参数：标签类型、标签名称
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a FunctionExpression from an actual function.
   */
  public static <F extends Function<?>> FunctionExpression<F> lambda(     // 泛型方法：F是Function的子类型
      F function) {     // 参数：实际的函数对象
    // REVIEW: Check that that function class is non-inner, has a public
    // default constructor, etc.?

    //noinspection unchecked
    return new FunctionExpression<>(function);     // 创建并返回函数表达式
  }   // 结束方法

  /**
   * Creates a LambdaExpression by first constructing a delegate
   * type.
   */
  public static <F extends Function<?>> FunctionExpression<F> lambda(     // 泛型方法：F是Function的子类型
      BlockStatement body,     // 参数：函数体代码块
      Iterable<? extends ParameterExpression> parameters) {     // 参数：参数集合
    final List<ParameterExpression> parameterList = toList(parameters);     // 将参数集合转换为列表
    @SuppressWarnings("unchecked")     // 抑制未检查转换警告
    Class<F> type = deduceType(parameterList, body.getType());     // 根据参数列表和返回类型推断函数类型
    return new FunctionExpression<>(type, body, parameterList);     // 创建并返回函数表达式
  }   // 结束方法

  /**
   * Creates a LambdaExpression by first constructing a delegate
   * type, using varargs.
   */
  public static <F extends Function<?>> FunctionExpression<F> lambda(     // 泛型方法：F是Function的子类型
      BlockStatement body, ParameterExpression... parameters) {     // 参数：函数体代码块
    return lambda(body, toList(parameters));     // 将参数数组转换为列表后调用重载方法
  }   // 结束方法

  /**
   * Creates an Expression where the delegate type {@code F} is
   * known at compile time.
   */
  public static <F extends Function<?>> FunctionExpression<F> lambda(     // 泛型方法：F是Function的子类型
      Expression body, Iterable<? extends ParameterExpression> parameters) {     // 参数：函数体表达式、参数集合
    return lambda(Blocks.toFunctionBlock(body), parameters);     // 将表达式转换为函数块后调用重载方法
  }   // 结束方法

  /**
   * Creates an Expression where the delegate type {@code F} is
   * known at compile time, using varargs.
   */
  public static <F extends Function<?>> FunctionExpression<F> lambda(     // 泛型方法：F是Function的子类型
      Expression body, ParameterExpression... parameters) {     // 参数：函数体表达式、可变数量的参数
    return lambda(Blocks.toFunctionBlock(body), toList(parameters));     // 将表达式转换为函数块，参数转换为列表后调用重载方法
  }   // 结束方法

  /**
   * Creates a LambdaExpression by first constructing a delegate
   * type.
   *
   * <p>It can be used when the delegate type is not known at compile time.
   */
  public static <T, F extends Function<? extends T>> FunctionExpression<F>       // 泛型方法：T是返回类型，F是Function的子类型
      lambda(Class<F> type, BlockStatement body,     // 参数：函数类型、函数体代码块
      Iterable<? extends ParameterExpression> parameters) {     // 参数：参数集合
    return new FunctionExpression<>(type, body, toList(parameters));     // 创建并返回函数表达式
  }   // 结束方法

  /**
   * Creates a LambdaExpression by first constructing a delegate
   * type, using varargs.
   *
   * <p>It can be used when the delegate type is not known at compile time.
   */
  public static <T, F extends Function<? extends T>> FunctionExpression<F> lambda(       // 泛型方法：T是返回类型，F是Function的子类型
      Class<F> type, BlockStatement body, ParameterExpression... parameters) {     // 参数：函数类型、函数体、可变参数
    return lambda(type, body, toList(parameters));     // 将参数数组转换为列表后调用重载方法
  }   // 结束方法

  /**
   * Creates a LambdaExpression by first constructing a delegate
   * type.
   *
   * <p>It can be used when the delegate type is not known at compile time.
   */
  public static <T, F extends Function<? extends T>> FunctionExpression<F> lambda(       // 泛型方法：T是返回类型，F是Function的子类型
      Class<F> type, Expression body,
      Iterable<? extends ParameterExpression> parameters) {     // 参数：参数集合
    return lambda(type, Blocks.toFunctionBlock(body), toList(parameters));     // 将表达式转换为函数块，参数转换为列表后调用重载方法
  }   // 结束方法

  /**
   * Creates a LambdaExpression by first constructing a delegate
   * type, using varargs.
   *
   * <p>It can be used when the delegate type is not known at compile time.
   */
  public static <T, F extends Function<? extends T>> FunctionExpression<F> lambda(       // 泛型方法：T是返回类型，F是Function的子类型
      Class<F> type, Expression body, ParameterExpression... parameters) {     // 参数：函数类型、函数体表达式、可变参数
    return lambda(type, Blocks.toFunctionBlock(body), toList(parameters));     // 将表达式转换为函数块，参数转换为列表后调用重载方法
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise
   * left-shift operation.
   */
  public static BinaryExpression leftShift(Expression left, Expression right) {     // 参数：左操作数、右操作数
    return makeBinary(ExpressionType.LeftShift, left, right);     // 调用makeBinary方法创建LeftShift类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise
   * left-shift operation.
   */
  public static BinaryExpression leftShift(Expression left, Expression right,     // 参数：左操作数、右操作数
      Method method) {     // 参数：实现取模运算的方法
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise
   * left-shift assignment operation.
   */
  public static BinaryExpression leftShiftAssign(Expression left,     // 参数：左操作数
      Expression right) {     // 参数：右操作数
    return makeBinary(ExpressionType.LeftShiftAssign, left, right);     // 调用makeBinary方法创建LeftShiftAssign类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise
   * left-shift assignment operation.
   */
  public static BinaryExpression leftShiftAssign(Expression left,     // 参数：左操作数
      Expression right, Method method) {     // 参数：右操作数、实现方法
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise
   * left-shift assignment operation.
   */
  public static BinaryExpression leftShiftAssign(Expression left,     // 参数：左操作数
      Expression right, Method method, LambdaExpression lambdaExpression) {     // 参数：右操作数、实现方法、Lambda表达式
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a "less than"
   * numeric comparison.
   */
  public static BinaryExpression lessThan(Expression left, Expression right) {     // 参数：左操作数、右操作数
    return makeBinary(ExpressionType.LessThan, left, right);     // 调用makeBinary方法创建LessThan类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a "less than"
   * numeric comparison.
   */
  public static BinaryExpression lessThan(Expression left, Expression right,     // 参数：左操作数、右操作数
      boolean liftToNull, Method method) {     // 参数：是否提升到null、实现方法
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a " less than or
   * equal" numeric comparison.
   */
  public static BinaryExpression lessThanOrEqual(Expression left,     // 参数：左操作数
      Expression right) {     // 参数：右操作数
    return makeBinary(ExpressionType.LessThanOrEqual, left, right);     // 调用makeBinary方法创建LessThanOrEqual类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a "less than or
   * equal" numeric comparison.
   */
  public static BinaryExpression lessThanOrEqual(Expression left,     // 参数：左操作数
      Expression right, boolean liftToNull, Method method) {     // 参数：右操作数、是否提升到null、实现方法
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a MemberListBinding where the member is a field or
   * property.
   */
  public static MemberListBinding listBind(Member member,     // 参数：成员
      Iterable<? extends ElementInit> elementInits) {     // 参数：元素初始化器集合
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a MemberListBinding where the member is a field or
   * property, using varargs.
   */
  public static MemberListBinding listBind(Member member,     // 参数：成员
      ElementInit... elementInits) {     // 参数：可变数量的元素初始化器
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a MemberListBinding based on a specified property
   * accessor method.
   */
  public static MemberListBinding listBind(Method method,     // 参数：属性访问器方法
      Iterable<? extends ElementInit> elementInits) {     // 参数：元素初始化器集合
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a MemberListBinding object based on a specified
   * property accessor method, using varargs.
   */
  public static MemberListBinding listBind(Method method,     // 参数：属性访问器方法
      ElementInit... elementInits) {     // 参数：可变数量的元素初始化器
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a ListInitExpression that uses specified ElementInit
   * objects to initialize a collection.
   */
  public static ListInitExpression listInit(NewExpression newExpression,     // 参数：新对象表达式
      Iterable<? extends ElementInit> elementInits) {     // 参数：元素初始化器集合
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a ListInitExpression that uses specified ElementInit
   * objects to initialize a collection, using varargs.
   */
  public static ListInitExpression listInit(NewExpression newExpression,     // 参数：新对象表达式
      ElementInit... elementInits) {     // 参数：可变数量的元素初始化器
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a ListInitExpression that uses a method named "Add" to
   * add elements to a collection.
   */
  public static ListInitExpression listInitE(NewExpression newExpression,     // 参数：新对象表达式
      Iterable<? extends Expression> arguments) {     // 参数：参数表达式集合
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a ListInitExpression that uses a method named "Add" to
   * add elements to a collection, using varargs.
   */
  public static ListInitExpression listInit(NewExpression newExpression,     // 参数：新对象表达式
      Expression... arguments) {     // 参数：可变数量的参数表达式
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a ListInitExpression that uses a specified method to
   * add elements to a collection.
   */
  public static ListInitExpression listInit(NewExpression newExpression,     // 参数：新对象表达式
      Method method, Iterable<? extends Expression> arguments) {     // 参数：添加方法、参数表达式集合
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a ListInitExpression that uses a specified method to
   * add elements to a collection, using varargs.
   */
  public static ListInitExpression listInit(NewExpression newExpression,     // 参数：新对象表达式
      Method method, Expression... arguments) {     // 参数：添加方法、可变数量的参数表达式
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a LoopExpression with the given body.
   */
  public static ForStatement for_(     // 方法名：创建for循环语句
      Iterable<? extends DeclarationStatement> declarations,     // 参数：声明语句的可迭代集合（初始化部分）
      @Nullable Expression condition, @Nullable Expression post, Statement body) {     // 参数：循环条件、循环后操作、循环体
    return new ForStatement(toList(declarations), condition, post, body);     // 创建并返回for循环语句
  }   // 结束方法

  /**
   * Creates a LoopExpression with the given body.
   */
  public static ForStatement for_(     // 方法名：创建for循环语句
      DeclarationStatement declaration,     // 参数：单个声明语句（初始化部分）
      @Nullable Expression condition, @Nullable Expression post, Statement body) {     // 参数：循环条件、循环后操作、循环体
    return new ForStatement(Collections.singletonList(declaration), condition,     // 创建for循环语句，将声明语句包装为单元素列表
        post, body);         // 传入循环后操作和循环体
  }   // 结束方法

  /**
   * Creates a ForEachExpression with the given body.
   */
  public static ForEachStatement forEach(     // 方法名：创建foreach循环语句
      ParameterExpression parameter, Expression iterable, Statement body) {     // 参数：循环变量、可迭代对象、循环体
    return new ForEachStatement(parameter, iterable, body);     // 创建并返回foreach循环语句
  }   // 结束方法

  /**
   * Creates a BinaryExpression, given the left and right operands,
   * by calling an appropriate factory method.
   */
  public static BinaryExpression makeBinary(ExpressionType binaryType,     // 参数：二元表达式类型
      Expression left, Expression right) {     // 参数：左操作数、右操作数
    final Type type;     // 声明结果类型变量
    switch (binaryType) {     // 根据二元表达式类型确定结果类型
    case Equal:     // 相等比较
    case NotEqual:     // 不等比较
    case LessThan:     // 小于比较
    case LessThanOrEqual:     // 小于等于比较
    case GreaterThan:     // 大于比较
    case GreaterThanOrEqual:     // 大于等于比较
    case AndAlso:     // 条件与
    case OrElse:     // 条件或
      type = Boolean.TYPE;       // 结果类型为boolean
      break;       // 跳出switch
    default:     // 其他一元表达式类型
      type = larger(left.type, right.type);       // 使用larger方法确定结果类型
      break;       // 跳出switch
    }   // 结束方法
    return new BinaryExpression(binaryType, type, left, right);     // 创建并返回二元表达式
  }   // 结束方法

  /** Returns an expression to box the value of a primitive expression.
   * E.g. {@code box(e, Primitive.INT)} returns {@code Integer.valueOf(e)}. */
  public static Expression box(Expression expression, Primitive primitive) {     // 参数：要装箱的表达式、基本类型
    return call(requireNonNull(primitive.boxClass), "valueOf", expression);     // 调用包装类的valueOf方法进行装箱
  }   // 结束方法

  /** Converts e.g. "anInteger" to "Integer.valueOf(anInteger)". */
  public static Expression box(Expression expression) {     // 参数：要装箱的表达式
    Primitive primitive = Primitive.of(expression.getType());     // 获取表达式类型对应的基本类型
    if (primitive == null) {     // 如果不是包装类型
      return expression;       // 直接返回原表达式
    }   // 结束方法
    return box(expression, primitive);     // 调用重载方法进行装箱
  }   // 结束方法

  /** Returns an expression to unbox the value of a boxed-primitive expression exactly.
   * E.g. {@code unboxExact(e, Primitive.INT)} returns {@code e.intValueExact()}.
   * It is assumed that e is of the right box type (or {@link Number})."Value */
  public static Expression unboxExact(Expression expression, Primitive primitive) {     // 参数：包装类型表达式、基本类型
    return call(expression, requireNonNull(primitive.primitiveName) + "ValueExact");     // 调用exact方法进行精确拆箱
  }   // 结束方法

  /** Returns an expression to unbox the value of a boxed-primitive expression.
   * E.g. {@code unbox(e, Primitive.INT)} returns {@code e.intValue()}.
   * It is assumed that e is of the right box type (or {@link Number})."Value */
  public static Expression unbox(Expression expression, Primitive primitive) {     // 参数：包装类型表达式、基本类型
    return call(expression, requireNonNull(primitive.primitiveName) + "Value");     // 调用value方法进行拆箱
  }   // 结束方法

  /** Converts e.g. "anInteger" to "anInteger.intValue()". */
  public static Expression unbox(Expression expression) {     // 参数：要拆箱的表达式
    Primitive primitive = Primitive.ofBox(expression.getType());     // 获取表达式类型对应的基本类型
    if (primitive == null) {     // 如果不是包装类型
      return expression;       // 直接返回原表达式
    }   // 结束方法
    return unbox(expression, primitive);     // 调用重载方法进行拆箱
  }   // 结束方法

  private static Type larger(Type type0, Type type1) {     // 私有方法：返回两个类型中较大的类型
    // curiously, "short + short" has type "int".
    // similarly, "byte + byte" has type "int".
    // "byte / long" has type "long".
    if (type0 == double.class     // 如果任一类型是double
        || type0 == Double.class     // 或Double
        || type1 == double.class     // 或double
        || type1 == Double.class) {     // 或Double
      return double.class;       // 返回double类型
    }   // 结束方法
    if (type0 == float.class     // 如果任一类型是float
        || type0 == Float.class     // 或Float
        || type1 == float.class     // 或float
        || type1 == Float.class) {     // 或Float
      return float.class;       // 返回float类型
    }   // 结束方法
    if (type0 == long.class     // 如果任一类型是long
        || type0 == Long.class     // 或Long
        || type1 == long.class     // 或long
        || type1 == Long.class) {     // 或Long
      return long.class;       // 返回long类型
    }   // 结束方法
    return int.class;     // 默认返回int类型
  }   // 结束方法

  /**
   * Creates a BinaryExpression, given the left operand, right
   * operand and implementing method, by calling the appropriate
   * factory method.
   */
  public static BinaryExpression makeBinary(ExpressionType binaryType,     // 参数：二元表达式类型
      Expression left, Expression right, boolean liftToNull, Method method) {     // 参数：左操作数、右操作数、是否提升到null、实现方法
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression, given the left operand, right
   * operand, implementing method and type conversion function, by
   * calling the appropriate factory method.
   */
  public static BinaryExpression makeBinary(ExpressionType binaryType,     // 参数：二元表达式类型
      Expression left, Expression right, boolean liftToNull, Method method,     // 参数：左操作数、右操作数、是否提升到null、实现方法
      LambdaExpression lambdaExpression) {     // 参数：Lambda表达式
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a TernaryExpression, given the left and right operands,
   * by calling an appropriate factory method.
   */
  public static TernaryExpression makeTernary(ExpressionType ternaryType,     // 参数：三元表达式类型
      Expression e0, Expression e1, Expression e2) {     // 参数：三个操作数
    final Type type;     // 声明结果类型变量
    switch (ternaryType) {     // 根据三元表达式类型确定结果类型
    case Conditional:     // 条件表达式
      if (e1 instanceof ConstantUntypedNull) {       // 如果第二个操作数是未类型化的null
        type = Primitive.box(e2.getType());         // 使用第三个操作数的包装类型
        if (e1.getType() != type) {         // 如果类型不匹配
          e1 = constant(null, type);           // 创建正确类型的null常量
        }   // 结束方法
      } else if (e2 instanceof ConstantUntypedNull) {   // 结束方法
        type = Primitive.box(e1.getType());         // 使用第二个操作数的包装类型
        if (e2.getType() != type) {         // 如果类型不匹配
          e2 = constant(null, type);           // 创建正确类型的null常量
        }   // 结束方法
      } else {   // 结束方法
        type = Types.gcd(e1.getType(), e2.getType());         // 使用两个类型的最大公约数类型
      }   // 结束方法
      break;       // 跳出switch
    default:     // 其他一元表达式类型
      type = e1.getType();       // 使用第二个操作数的类型
    }   // 结束方法
    return new TernaryExpression(ternaryType, type, e0, e1, e2);     // 创建并返回三元表达式
  }   // 结束方法

  /**
   * Creates a CatchBlock representing a catch statement with the
   * specified elements.
   */
  public static CatchBlock makeCatchBlock(Type type,     // 参数：异常类型
      ParameterExpression variable, Expression body, Expression filter) {     // 参数：异常变量、处理体、过滤器
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a DynamicExpression that represents a dynamic
   * operation bound by the provided CallSiteBinder.
   */
  public static DynamicExpression makeDynamic(Type type, CallSiteBinder binder,     // 参数：类型、调用站点绑定器
      Iterable<? extends Expression> arguments) {     // 参数：参数表达式集合
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a DynamicExpression that represents a dynamic
   * operation bound by the provided CallSiteBinder, using varargs.
   */
  public static DynamicExpression makeDynamic(Type type, CallSiteBinder binder,     // 参数：类型、调用站点绑定器
      Expression... arguments) {     // 参数：可变数量的参数表达式
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a GotoExpression representing a jump of the specified
   * GotoExpressionKind. The value passed to the label upon jumping
   * can also be specified.
   */
  public static GotoStatement makeGoto(GotoExpressionKind kind,     // 参数：跳转表达式类型
      LabelTarget target, Expression value, Type type) {     // 参数：目标标签、跳转值、类型
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a MemberExpression that represents accessing a field.
   */
  public static MemberExpression makeMemberAccess(@Nullable Expression expression,     // 参数：目标对象表达式
      PseudoField member) {     // 参数：伪字段成员
    return new MemberExpression(expression, member);     // 创建并返回成员访问表达式
  }   // 结束方法

  /**
   * Creates a TryExpression representing a try block with the
   * specified elements.
   */
  public static TryStatement makeTry(Type type, Expression body,     // 参数：类型、try块体
      Expression finally_, Expression fault,     // 参数：finally块、fault块
      Iterable<? extends CatchBlock> handlers) {     // 参数：catch块集合
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a TryExpression representing a try block with the
   * specified elements, using varargs.
   */
  public static TryStatement makeTry(Type type, Expression body,     // 参数：类型、try块体
      Expression finally_, Expression fault,     // 参数：finally块、fault块
      CatchBlock... handlers) {     // 参数：可变数量的catch块
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a UnaryExpression, given an operand, by calling the
   * appropriate factory method.
   */
  public static UnaryExpression makeUnary(ExpressionType expressionType,     // 参数：一元表达式类型
      Expression expression) {     // 参数：操作数
    Type type = expression.getType();     // 获取操作数的类型
    switch (expressionType) {     // 根据一元表达式类型调整结果类型
    case Negate:     // 取负运算
      if (type == byte.class || type == short.class) {       // 如果是byte或short类型
        type = int.class;         // 结果类型提升为int
      }   // 结束方法
      break;       // 跳出switch
    default:     // 其他一元表达式类型
      break;       // 跳出switch
    }   // 结束方法
    return new UnaryExpression(expressionType, type, expression);     // 创建并返回一元表达式
  }   // 结束方法

  /**
   * Creates a UnaryExpression, given an operand and implementing
   * method, by calling the appropriate factory method.
   */
  public static UnaryExpression makeUnary(ExpressionType expressionType,     // 参数：一元表达式类型
      Expression expression, Type type, @Nullable Method method) {     // 参数：操作数、结果类型、实现方法
    return new UnaryExpression(expressionType, type, expression);     // 创建并返回一元表达式
  }   // 结束方法

  /**
   * Creates a MemberMemberBinding that represents the recursive
   * initialization of members of a field or property.
   */
  public static MemberMemberBinding memberBind(Member member,     // 参数：成员
      Iterable<? extends MemberBinding> bindings) {     // 参数：成员绑定集合
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a MemberMemberBinding that represents the recursive
   * initialization of members of a field or property, using varargs.
   */
  public static MemberMemberBinding memberBind(Member member,     // 参数：成员
      MemberBinding... bindings) {     // 参数：可变数量的成员绑定
    return memberBind(member, toList(bindings));     // 将绑定数组转换为列表后调用重载方法
  }   // 结束方法

  /**
   * Creates a MemberMemberBinding that represents the recursive
   * initialization of members of a member that is accessed by using
   * a property accessor method.
   */
  public static MemberMemberBinding memberBind(Method method,     // 参数：属性访问器方法
      Iterable<? extends MemberBinding> bindings) {     // 参数：成员绑定集合
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a MemberMemberBinding that represents the recursive
   * initialization of members of a member that is accessed by using
   * a property accessor method, using varargs.
   */
  public static MemberMemberBinding memberBind(Method method,     // 参数：属性访问器方法
      MemberBinding... bindings) {     // 参数：可变数量的成员绑定
    return memberBind(method, toList(bindings));
  }   // 结束方法

  /**
   * Represents an expression that creates a new object and
   * initializes a property of the object.
   */
  public static MemberInitExpression memberInit(NewExpression newExpression,     // 参数：新对象表达式
      Iterable<? extends MemberBinding> bindings) {     // 参数：成员绑定集合
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Represents an expression that creates a new object and
   * initializes a property of the object, using varargs.
   */
  public static MemberInitExpression memberInit(NewExpression newExpression,     // 参数：新对象表达式
      MemberBinding... bindings) {     // 参数：可变数量的成员绑定
    return memberInit(newExpression, toList(bindings));     // 将绑定数组转换为列表后调用重载方法
  }   // 结束方法

  /**
   * Declares a method.
   */
  public static MethodDeclaration methodDecl(int modifier, Type resultType,     // 参数：修饰符、返回类型
      String name, Iterable<? extends ParameterExpression> parameters,     // 参数：方法名、参数集合
      BlockStatement body) {     // 参数：构造函数体
    return new MethodDeclaration(modifier, name, resultType, toList(parameters),     // 创建并返回方法声明
        body);         // 传入方法体
  }   // 结束方法

  /**
   * Declares a constructor.
   */
  public static ConstructorDeclaration constructorDecl(int modifier,     // 参数：修饰符
      Type declaredAgainst, Iterable<? extends ParameterExpression> parameters,     // 参数：声明类型、参数集合
      BlockStatement body) {     // 参数：构造函数体
    return new ConstructorDeclaration(modifier, declaredAgainst,     // 创建并返回构造函数声明
        toList(parameters), body);         // 传入参数列表和构造函数体
  }   // 结束方法

  /**
   * Declares a field with an initializer.
   */
  public static FieldDeclaration fieldDecl(int modifier,     // 参数：修饰符
      ParameterExpression parameter, @Nullable Expression initializer) {     // 参数：字段参数、初始化表达式
    return new FieldDeclaration(modifier, parameter, initializer);     // 创建并返回字段声明
  }   // 结束方法

  /**
   * Declares a field.
   */
  public static FieldDeclaration fieldDecl(int modifier,     // 参数：修饰符
      ParameterExpression parameter) {     // 参数：字段参数
    return new FieldDeclaration(modifier, parameter, null);     // 创建字段声明，初始化表达式为null
  }   // 结束方法

  /**
   * Declares a class.
   */
  public static ClassDeclaration classDecl(int modifier, String name,     // 参数：修饰符、类名
      @Nullable Type extended, List<Type> implemented,     // 参数：父类、实现的接口列表
      List<MemberDeclaration> memberDeclarations) {     // 参数：成员声明列表
    return new ClassDeclaration(modifier, name, extended, implemented,     // 创建并返回类声明
        memberDeclarations);         // 传入成员声明列表
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an arithmetic
   * remainder operation.
   */
  public static BinaryExpression modulo(Expression left, Expression right) {     // 参数：左操作数（被除数）、右操作数（除数）
    return makeBinary(ExpressionType.Modulo, left, right);     // 调用makeBinary方法创建Modulo类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an arithmetic
   * remainder operation.
   */
  public static BinaryExpression modulo(Expression left, Expression right,     // 参数：左操作数、右操作数
      Method method) {     // 参数：实现取模运算的方法
    return makeBinary(ExpressionType.Modulo, left, right,     // 调用makeBinary方法创建Modulo类型的二元表达式
        shouldLift(left, right, method), method);         // 判断是否需要提升操作，传入方法参数
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a remainder
   * assignment operation.
   */
  public static BinaryExpression moduloAssign(Expression left,     // 参数：左操作数
      Expression right) {     // 参数：右操作数
    return makeBinary(ExpressionType.ModuloAssign, left, right);     // 调用makeBinary方法创建ModuloAssign类型的二元表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a remainder
   * assignment operation.
   */
  public static BinaryExpression moduloAssign(Expression left, Expression right,     // 参数：左操作数
      Method method) {     // 参数：实现取模运算的方法
    return makeBinary(ExpressionType.ModuloAssign, left, right, false, method);     // 调用makeBinary方法，不提升操作
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a remainder
   * assignment operation.
   */
  public static BinaryExpression moduloAssign(Expression left, Expression right,     // 参数：左操作数
      Method method, LambdaExpression lambdaExpression) {
    return makeBinary(ExpressionType.ModuloAssign, left, right, false, method,     // 调用makeBinary方法，不提升操作
        lambdaExpression);         // 传入Lambda表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an arithmetic
   * multiplication operation that does not have overflow
   * checking.
   */
  public static BinaryExpression multiply(Expression left, Expression right) {
    return makeBinary(ExpressionType.Multiply, left, right);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an arithmetic
   * multiplication operation that does not have overflow
   * checking.
   */
  public static BinaryExpression multiply(Expression left, Expression right,
      Method method) {     // 参数：实现取模运算的方法
    return makeBinary(ExpressionType.Multiply, left, right,
        shouldLift(left, right, method), method);         // 判断是否需要提升操作，传入方法参数
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a multiplication
   * assignment operation that does not have overflow checking.
   */
  public static BinaryExpression multiplyAssign(Expression left,
      Expression right) {     // 参数：右操作数
    return makeBinary(ExpressionType.MultiplyAssign, left, right);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a multiplication
   * assignment operation that does not have overflow checking.
   */
  public static BinaryExpression multiplyAssign(Expression left,
      Expression right, Method method) {     // 参数：右操作数、实现方法
    return makeBinary(ExpressionType.MultiplyAssign, left, right, false,
        method);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a multiplication
   * assignment operation that does not have overflow checking.
   */
  public static BinaryExpression multiplyAssign(Expression left,
      Expression right, Method method, LambdaExpression lambdaExpression) {     // 参数：右操作数、实现方法、Lambda表达式
    return makeBinary(ExpressionType.MultiplyAssign, left, right, false, method,
        lambdaExpression);         // 传入Lambda表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a multiplication
   * assignment operation that has overflow checking.
   */
  public static BinaryExpression multiplyAssignChecked(Expression left,
      Expression right) {     // 参数：右操作数
    return makeBinary(ExpressionType.MultiplyAssignChecked, left, right);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a multiplication
   * assignment operation that has overflow checking.
   */
  public static BinaryExpression multiplyAssignChecked(Expression left,
      Expression right, Method method) {     // 参数：右操作数、实现方法
    return makeBinary(ExpressionType.MultiplyAssignChecked, left, right, false,
        method);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a multiplication
   * assignment operation that has overflow checking.
   */
  public static BinaryExpression multiplyAssignChecked(Expression left,
      Expression right, Method method, LambdaExpression lambdaExpression) {     // 参数：右操作数、实现方法、Lambda表达式
    return makeBinary(
        ExpressionType.MultiplyAssignChecked,
        left,
        right,
        false,
        method,
        lambdaExpression);         // 传入Lambda表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an arithmetic
   * multiplication operation that has overflow checking.
   */
  public static BinaryExpression multiplyChecked(Expression left,
      Expression right) {     // 参数：右操作数
    return makeBinary(ExpressionType.MultiplyChecked, left, right);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an arithmetic
   * multiplication operation that has overflow checking.
   */
  public static BinaryExpression multiplyChecked(Expression left,
      Expression right, Method method) {     // 参数：右操作数、实现方法
    return makeBinary(ExpressionType.MultiplyChecked, left, right,
        shouldLift(left, right, method), method);         // 判断是否需要提升操作，传入方法参数
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents an arithmetic
   * negation operation.
   */
  public static UnaryExpression negate(Expression expression) {
    return makeUnary(ExpressionType.Negate, expression);
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents an arithmetic
   * negation operation.
   */
  public static UnaryExpression negate(Expression expression, Method method) {
    // TODO: use method
    return negate(expression);
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents an arithmetic
   * negation operation that has overflow checking.
   */
  public static UnaryExpression negateChecked(Expression expression) {
    return makeUnary(ExpressionType.NegateChecked, expression);
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents an arithmetic
   * negation operation that has overflow checking. The implementing
   * method can be specified.
   */
  public static UnaryExpression negateChecked(Expression expression,
      Method method) {     // 参数：实现取模运算的方法
    throw new UnsupportedOperationException("not implemented");
    // return makeUnary(ExpressionType.NegateChecked, expression, null, method);
  }   // 结束方法

  /**
   * Creates a NewExpression that represents calling the specified
   * constructor that takes no arguments.
   */
  public static NewExpression new_(Constructor constructor) {
    return new_(constructor.getDeclaringClass(), ImmutableList.of());
  }   // 结束方法

  /**
   * Creates a NewExpression that represents calling the
   * parameterless constructor of the specified type.
   */
  public static NewExpression new_(Type type) {
    return new_(type, ImmutableList.of());
  }   // 结束方法

  /**
   * Creates a NewExpression that represents calling the constructor of the
   * specified type whose parameters are assignable from the specified
   * arguments.
   */
  public static NewExpression new_(Type type,
      Iterable<? extends Expression> arguments) {     // 参数：参数表达式集合
    // Note that the last argument is not an empty list. That would cause
    // an anonymous inner-class with no members to be generated.
    return new NewExpression(type, toList(arguments), null);
  }   // 结束方法

  /**
   * Creates a NewExpression that represents calling the constructor of the
   * specified type whose parameters are assignable from the specified
   * arguments, using varargs.
   */
  public static NewExpression new_(Type type, Expression... arguments) {
    // Note that the last argument is not an empty list. That would cause
    // an anonymous inner-class with no members to be generated.
    return new NewExpression(type, toList(arguments), null);
  }   // 结束方法

  /**
   * Creates a NewExpression that represents calling the constructor of the
   * specified type whose parameters are assignable from the specified
   * arguments.
   */
  public static NewExpression new_(Type type,
      Iterable<? extends Expression> arguments,
      @Nullable Iterable<? extends MemberDeclaration> memberDeclarations) {
    return new NewExpression(type, toList(arguments),
        memberDeclarations == null ? null : toList(memberDeclarations));
  }   // 结束方法

  /**
   * Creates a NewExpression that represents calling the constructor of the
   * specified type whose parameters are assignable from the specified
   * arguments, using varargs.
   */
  public static NewExpression new_(Type type,
      Iterable<? extends Expression> arguments,
      MemberDeclaration... memberDeclarations) {
    return new NewExpression(type, toList(arguments),
        toList(memberDeclarations));
  }   // 结束方法

  /**
   * Creates a NewExpression that represents calling the specified
   * constructor with the specified arguments.
   */
  public static NewExpression new_(Constructor constructor,
      Iterable<? extends Expression> expressions) {
    // Note that the last argument is not an empty list. That would cause
    // an anonymous inner-class with no members to be generated.
    return new NewExpression(constructor.getDeclaringClass(),
        toList(expressions), null);
  }   // 结束方法

  /**
   * Creates a NewExpression that represents calling the specified
   * constructor with the specified arguments, using varargs.
   */
  public static NewExpression new_(Constructor constructor,
      Expression... expressions) {
    return new NewExpression(constructor.getDeclaringClass(),
        toList(expressions), null);
  }   // 结束方法

  /**
   * Creates a NewExpression that represents calling the specified
   * constructor with the specified arguments.
   *
   * <p>The members that access the constructor initialized fields are
   * specified.
   */
  public static NewExpression new_(Constructor constructor,
      Iterable<? extends Expression> expressions,
      Iterable<? extends MemberDeclaration> memberDeclarations) {
    return new_(constructor.getDeclaringClass(), toList(expressions),
        toList(memberDeclarations));
  }   // 结束方法

  /**
   * Creates a NewExpression that represents calling the specified
   * constructor with the specified arguments, using varargs.
   *
   * <p>The members that access the constructor initialized fields are
   * specified.
   */
  public static NewExpression new_(Constructor constructor,
      Iterable<? extends Expression> expressions,
      MemberDeclaration... memberDeclarations) {
    return new_(constructor.getDeclaringClass(), toList(expressions),
        toList(memberDeclarations));
  }   // 结束方法

  /**
   * Creates a NewArrayExpression that represents creating an array
   * that has a specified rank.
   *
   * <p>For example,
   * {@code newArrayBounds(int.class, 1, constant(8))}
   * yields {@code new int[8]};
   * {@code newArrayBounds(int.class, 3, constant(8))}
   * yields {@code new int[8][][]};
   *
   * @param type Element type of the array
   * @param dimension Dimension of the array
   * @param bound Size of the first dimension
   */
  public static NewArrayExpression newArrayBounds(Type type, int dimension,
      @Nullable Expression bound) {
    return new NewArrayExpression(type, dimension, bound, null);
  }   // 结束方法

  /**
   * Creates a NewArrayExpression that represents creating a
   * one-dimensional array and initializing it from a list of
   * elements.
   *
   * <p>For example, "{@code newArrayInit(int.class,
   * Arrays.asList(constant(1), constant(2))}"
   * yields "{@code new int[] {1, 2}}".
   *
   * @param type Element type of the array
   * @param expressions Initializer expressions
   */
  public static NewArrayExpression newArrayInit(Type type,
      Iterable<? extends Expression> expressions) {
    return new NewArrayExpression(type, 1, null, toList(expressions));
  }   // 结束方法

  /**
   * Creates a NewArrayExpression that represents creating a
   * one-dimensional array and initializing it from a list of
   * elements, using varargs.
   *
   * <p>For example, "{@code newArrayInit(int.class, constant(1), constant(2)}"
   * yields "{@code new int[] {1, 2}}".
   *
   * @param type Element type of the array
   * @param expressions Initializer expressions
   */
  public static NewArrayExpression newArrayInit(Type type,
      Expression... expressions) {
    return new NewArrayExpression(type, 1, null, toList(expressions));
  }   // 结束方法

  /**
   * Creates a NewArrayExpression that represents creating a
   * n-dimensional array and initializing it from a list of
   * elements.
   *
   * <p>For example, "{@code newArrayInit(int.class, 2, Arrays.asList())}"
   * yields "{@code new int[][] {}}".
   *
   * @param type Element type of the array
   * @param dimension Dimension of the array
   * @param expressions Initializer expressions
   */
  public static NewArrayExpression newArrayInit(Type type, int dimension,
      Iterable<? extends Expression> expressions) {
    return new NewArrayExpression(type, dimension, null, toList(expressions));
  }   // 结束方法

  /**
   * Creates a NewArrayExpression that represents creating an
   * n-dimensional array and initializing it from a list of
   * elements, using varargs.
   *
   * <p>For example, "{@code newArrayInit(int.class, 2)}"
   * yields "{@code new int[][] {}}".
   *
   * @param type Element type of the array
   * @param dimension Dimension of the array
   * @param expressions Initializer expressions
   */
  public static NewArrayExpression newArrayInit(Type type, int dimension,
      Expression... expressions) {
    return new NewArrayExpression(type, dimension, null, toList(expressions));
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents a bitwise complement
   * operation.
   */
  public static UnaryExpression not(Expression expression) {
    return makeUnary(ExpressionType.Not, expression);
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents a bitwise complement
   * operation. The implementing method can be specified.
   */
  public static UnaryExpression not(Expression expression, Method method) {
    // TODO: use method
    return not(expression);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an inequality
   * comparison.
   */
  public static BinaryExpression notEqual(Expression left, Expression right) {
    return makeBinary(ExpressionType.NotEqual, left, right);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an inequality
   * comparison.
   */
  public static BinaryExpression notEqual(Expression left, Expression right,
      boolean liftToNull, Method method) {     // 参数：是否提升到null、实现方法
    return makeBinary(ExpressionType.NotEqual, left, right, liftToNull, method);
  }   // 结束方法

  /**
   * Returns the expression representing the ones complement.
   */
  public static UnaryExpression onesComplement(Expression expression) {
    return makeUnary(ExpressionType.OnesComplement, expression);
  }   // 结束方法

  /**
   * Returns the expression representing the ones complement.
   */
  public static UnaryExpression onesComplement(Expression expression,
      Method method) {     // 参数：实现取模运算的方法
    return makeUnary(ExpressionType.OnesComplement, expression,
        expression.getType(), method);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise OR
   * operation.
   */
  public static BinaryExpression or(Expression left, Expression right) {
    return makeBinary(ExpressionType.Or, left, right);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise OR
   * operation.
   */
  public static BinaryExpression or(Expression left, Expression right,
      Method method) {     // 参数：实现取模运算的方法
    return makeBinary(ExpressionType.Or, left, right, false, method);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise OR
   * assignment operation.
   */
  public static BinaryExpression orAssign(Expression left, Expression right) {
    return makeBinary(ExpressionType.OrAssign, left, right);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise OR
   * assignment operation.
   */
  public static BinaryExpression orAssign(Expression left, Expression right,
      Method method) {     // 参数：实现取模运算的方法
    return makeBinary(ExpressionType.OrAssign, left, right);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise OR
   * assignment operation.
   */
  public static BinaryExpression orAssign(Expression left, Expression right,
      Method method, LambdaExpression lambdaExpression) {
    return makeBinary(ExpressionType.OrAssign, left, right, false, method,
        lambdaExpression);         // 传入Lambda表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a conditional OR
   * operation that evaluates the second operand only if the first
   * operand evaluates to false.
   */
  public static BinaryExpression orElse(Expression left, Expression right) {
    return makeBinary(ExpressionType.OrElse, left, right);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a conditional OR
   * operation that evaluates the second operand only if the first
   * operand evaluates to false.
   */
  public static BinaryExpression orElse(Expression left, Expression right,
      Method method) {     // 参数：实现取模运算的方法
    return makeBinary(ExpressionType.OrElse, left, right, false, method);
  }   // 结束方法

  /**
   * Creates a ParameterExpression node that can be used to
   * identify a parameter or a variable in an expression tree.
   */
  public static ParameterExpression parameter(Type type) {
    return new ParameterExpression(type);
  }   // 结束方法

  /**
   * Creates a ParameterExpression node that can be used to
   * identify a parameter or a variable in an expression tree.
   */
  public static ParameterExpression parameter(Type type, String name) {
    return new ParameterExpression(0, type, name);
  }   // 结束方法

  /**
   * Creates a ParameterExpression.
   */
  public static ParameterExpression parameter(int modifiers, Type type,
      String name) {
    return new ParameterExpression(modifiers, type, name);
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents the assignment of
   * the expression followed by a subsequent decrement by 1 of the
   * original expression.
   */
  public static UnaryExpression postDecrementAssign(Expression expression) {
    return makeUnary(ExpressionType.PostDecrementAssign, expression);
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents the assignment of
   * the expression followed by a subsequent decrement by 1 of the
   * original expression.
   */
  public static UnaryExpression postDecrementAssign(Expression expression,
      Method method) {     // 参数：实现取模运算的方法
    return makeUnary(ExpressionType.PostDecrementAssign, expression,
        expression.getType(), method);
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents the assignment of
   * the expression followed by a subsequent increment by 1 of the
   * original expression.
   */
  public static UnaryExpression postIncrementAssign(Expression expression) {
    return makeUnary(ExpressionType.PostIncrementAssign, expression);
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents the assignment of
   * the expression followed by a subsequent increment by 1 of the
   * original expression.
   */
  public static UnaryExpression postIncrementAssign(Expression expression,
      Method method) {     // 参数：实现取模运算的方法
    return makeUnary(ExpressionType.PostIncrementAssign, expression,
        expression.getType(), method);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents raising a number to
   * a power.
   */
  // REVIEW: In Java this is a call to a lib function, Math.pow.
  public static BinaryExpression power(Expression left, Expression right) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents raising a number to
   * a power.
   */
  // REVIEW: In Java this is a call to a lib function, Math.pow.
  public static BinaryExpression power(Expression left, Expression right,
      Method method) {     // 参数：实现取模运算的方法
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents raising an
   * expression to a power and assigning the result back to the
   * expression.
   */
  // REVIEW: In Java this is a call to a lib function, Math.pow.
  public static BinaryExpression powerAssign(Expression left,
      Expression right) {     // 参数：右操作数
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents raising an
   * expression to a power and assigning the result back to the
   * expression.
   */
  // REVIEW: In Java this is a call to a lib function, Math.pow.
  public static BinaryExpression powerAssign(Expression left, Expression right,
      Method method) {     // 参数：实现取模运算的方法
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents raising an
   * expression to a power and assigning the result back to the
   * expression.
   */
  public static BinaryExpression powerAssign(Expression left, Expression right,
      Method method, LambdaExpression lambdaExpression) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a UnaryExpression that decrements the expression by 1
   * and assigns the result back to the expression.
   */
  public static UnaryExpression preDecrementAssign(Expression expression) {
    return makeUnary(ExpressionType.PreDecrementAssign, expression);
  }   // 结束方法

  /**
   * Creates a UnaryExpression that decrements the expression by 1
   * and assigns the result back to the expression.
   */
  public static UnaryExpression preDecrementAssign(Expression expression,
      Method method) {     // 参数：实现取模运算的方法
    return makeUnary(ExpressionType.PreDecrementAssign, expression,
        expression.getType(), method);
  }   // 结束方法

  /**
   * Creates a UnaryExpression that increments the expression by 1
   * and assigns the result back to the expression.
   */
  public static UnaryExpression preIncrementAssign(Expression expression) {
    return makeUnary(ExpressionType.PreIncrementAssign, expression);
  }   // 结束方法

  /**
   * Creates a UnaryExpression that increments the expression by 1
   * and assigns the result back to the expression.
   */
  public static UnaryExpression preIncrementAssign(Expression expression,
      Method method) {     // 参数：实现取模运算的方法
    return makeUnary(ExpressionType.PreIncrementAssign, expression,
        expression.getType(), method);
  }   // 结束方法

  /**
   * Creates a MemberExpression that represents accessing a
   * property by using a property accessor method.
   */
  // REVIEW: No equivalent to properties in Java.
  public static MemberExpression property(Expression expression,
      Method method) {     // 参数：实现取模运算的方法
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a MemberExpression that represents accessing a
   * property.
   */
  // REVIEW: No equivalent to properties in Java.
  public static MemberExpression property(Expression expression,
      PropertyInfo property) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a MemberExpression that represents accessing a
   * property.
   */
  // REVIEW: No equivalent to properties in Java.
  public static MemberExpression property(Expression expression, String name) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates an IndexExpression representing the access to an
   * indexed property.
   */
  // REVIEW: No equivalent to properties in Java.
  public static IndexExpression property(Expression expression,
      PropertyInfo property, Iterable<? extends Expression> arguments) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates an IndexExpression representing the access to an
   * indexed property, using varargs.
   */
  // REVIEW: No equivalent to properties in Java.
  public static IndexExpression property(Expression expression,
      PropertyInfo property, Expression... arguments) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates an IndexExpression representing the access to an
   * indexed property.
   */
  // REVIEW: No equivalent to properties in Java.
  public static IndexExpression property(Expression expression, String name,
      Expression... arguments) {     // 参数：可变数量的参数表达式
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a MemberExpression accessing a property.
   */
  public static MemberExpression property(Expression expression, Type type,
      String name) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a MemberExpression that represents accessing a
   * property or field.
   */
  // REVIEW: Java does not have properties; can only be a field name.
  public static MemberExpression propertyOrField(Expression expression,
      String propertyOfFieldName) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents an expression that
   * has a constant value of type Expression.
   */
  public static UnaryExpression quote(Expression expression) {
    return makeUnary(ExpressionType.Quote, expression);
  }   // 结束方法

  /**
   * Reduces this node to a simpler expression. If CanReduce
   * returns true, this should return a valid expression. This
   * method can return another node which itself must be reduced.
   */
  public static Expression reduce(Expression expression) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Reduces this node to a simpler expression. If CanReduce
   * returns true, this should return a valid expression. This
   * method can return another node which itself must be reduced.
   */
  public static Expression reduceAndCheck(Expression expression) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Reduces the expression to a known node type (that is not an
   * Extension node) or just returns the expression if it is already
   * a known type.
   */
  public static Expression reduceExtensions(Expression expression) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a reference
   * equality comparison.
   */
  public static Expression referenceEqual(Expression left, Expression right) {
    return makeBinary(ExpressionType.Equal, left, right);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a reference
   * inequality comparison.
   */
  public static Expression referenceNotEqual(Expression left,
      Expression right) {     // 参数：右操作数
    return makeBinary(ExpressionType.NotEqual, left, right);
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents a rethrowing of an
   * exception.
   */
  public static UnaryExpression rethrow() {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents a rethrowing of an
   * exception with a given type.
   */
  public static UnaryExpression rethrow(Type type) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a GotoExpression representing a return statement.
   */
  public static GotoStatement return_(@Nullable LabelTarget labelTarget) {
    return return_(labelTarget, (Expression) null);
  }   // 结束方法

  /**
   * Creates a GotoExpression representing a return statement. The
   * value passed to the label upon jumping can be specified.
   */
  public static GotoStatement return_(@Nullable LabelTarget labelTarget,
      @Nullable Expression expression) {
    return makeGoto(GotoExpressionKind.Return, labelTarget, expression);
  }   // 结束方法

  public static GotoStatement makeGoto(GotoExpressionKind kind,     // 参数：跳转表达式类型
      @Nullable LabelTarget labelTarget, @Nullable Expression expression) {
    return new GotoStatement(kind, labelTarget, expression);
  }   // 结束方法

  /**
   * Creates a GotoExpression representing a return statement with
   * the specified type.
   */
  public static GotoStatement return_(LabelTarget labelTarget, Type type) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a GotoExpression representing a return statement with
   * the specified type. The value passed to the label upon jumping
   * can be specified.
   */
  public static GotoStatement return_(LabelTarget labelTarget,
      Expression expression, Type type) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise
   * right-shift operation.
   */
  public static BinaryExpression rightShift(Expression left, Expression right) {
    return makeBinary(ExpressionType.RightShift, left, right);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise
   * right-shift operation.
   */
  public static BinaryExpression rightShift(Expression left, Expression right,
      Method method) {     // 参数：实现取模运算的方法
    return makeBinary(ExpressionType.RightShift, left, right, false, method);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise
   * right-shift assignment operation.
   */
  public static BinaryExpression rightShiftAssign(Expression left,
      Expression right) {     // 参数：右操作数
    return makeBinary(ExpressionType.RightShiftAssign, left, right);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise
   * right-shift assignment operation.
   */
  public static BinaryExpression rightShiftAssign(Expression left,
      Expression right, Method method) {     // 参数：右操作数、实现方法
    return makeBinary(ExpressionType.RightShiftAssign, left, right, false,
        method);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a bitwise
   * right-shift assignment operation.
   */
  public static BinaryExpression rightShiftAssign(Expression left,
      Expression right, Method method, LambdaExpression lambdaExpression) {     // 参数：右操作数、实现方法、Lambda表达式
    return makeBinary(ExpressionType.RightShiftAssign, left, right, false,
        method, lambdaExpression);
  }   // 结束方法

  /**
   * Creates an instance of RuntimeVariablesExpression.
   */
  public static RuntimeVariablesExpression runtimeVariables(
      Iterable<? extends ParameterExpression> expressions) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates an instance of RuntimeVariablesExpression, using varargs.
   */
  public static RuntimeVariablesExpression runtimeVariables(
      ParameterExpression... arguments) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an arithmetic
   * subtraction operation that does not have overflow checking.
   */
  public static BinaryExpression subtract(Expression left, Expression right) {
    return makeBinary(ExpressionType.Subtract, left, right);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an arithmetic
   * subtraction operation that does not have overflow checking.
   */
  public static BinaryExpression subtract(Expression left, Expression right,
      Method method) {     // 参数：实现取模运算的方法
    return makeBinary(ExpressionType.Subtract, left, right,
        shouldLift(left, right, method), method);         // 判断是否需要提升操作，传入方法参数
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a subtraction
   * assignment operation that does not have overflow checking.
   */
  public static BinaryExpression subtractAssign(Expression left,
      Expression right) {     // 参数：右操作数
    return makeBinary(ExpressionType.SubtractAssign, left, right);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a subtraction
   * assignment operation that does not have overflow checking.
   */
  public static BinaryExpression subtractAssign(Expression left,
      Expression right, Method method) {     // 参数：右操作数、实现方法
    return makeBinary(ExpressionType.SubtractAssign, left, right, false,
        method);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a subtraction
   * assignment operation that does not have overflow checking.
   */
  public static BinaryExpression subtractAssign(Expression left,
      Expression right, Method method, LambdaExpression lambdaExpression) {     // 参数：右操作数、实现方法、Lambda表达式
    return makeBinary(ExpressionType.SubtractAssign, left, right, false, method,
        lambdaExpression);         // 传入Lambda表达式
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a subtraction
   * assignment operation that has overflow checking.
   */
  public static BinaryExpression subtractAssignChecked(Expression left,
      Expression right) {     // 参数：右操作数
    return makeBinary(ExpressionType.SubtractAssignChecked, left, right);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a subtraction
   * assignment operation that has overflow checking.
   */
  public static BinaryExpression subtractAssignChecked(Expression left,
      Expression right, Method method) {     // 参数：右操作数、实现方法
    return makeBinary(ExpressionType.SubtractAssignChecked, left, right, false,
        method);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents a subtraction
   * assignment operation that has overflow checking.
   */
  public static BinaryExpression subtractAssignChecked(Expression left,
      Expression right, Method method, LambdaExpression lambdaExpression) {     // 参数：右操作数、实现方法、Lambda表达式
    return makeBinary(ExpressionType.SubtractAssignChecked, left, right, false,
        method, lambdaExpression);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an arithmetic
   * subtraction operation that has overflow checking.
   */
  public static BinaryExpression subtractChecked(Expression left,
      Expression right) {     // 参数：右操作数
    return makeBinary(ExpressionType.SubtractChecked, left, right);
  }   // 结束方法

  /**
   * Creates a BinaryExpression that represents an arithmetic
   * subtraction operation that has overflow checking.
   */
  public static BinaryExpression subtractChecked(Expression left,
      Expression right, Method method) {     // 参数：右操作数、实现方法
    return makeBinary(ExpressionType.SubtractChecked, left, right,
        shouldLift(left, right, method), method);         // 判断是否需要提升操作，传入方法参数
  }   // 结束方法

  /**
   * Creates a SwitchExpression that represents a switch statement
   * without a default case.
   */
  @SuppressWarnings("nullness")
  public static SwitchStatement switch_(Expression switchValue,
      SwitchCase... cases) {
    return switch_(switchValue, null, null, toList(cases));
  }   // 结束方法

  /**
   * Creates a SwitchExpression that represents a switch statement
   * that has a default case.
   */
  @SuppressWarnings("nullness")
  public static SwitchStatement switch_(Expression switchValue,
      Expression defaultBody, SwitchCase... cases) {
    return switch_(switchValue, defaultBody, null, toList(cases));
  }   // 结束方法

  /**
   * Creates a SwitchExpression that represents a switch statement
   * that has a default case.
   */
  public static SwitchStatement switch_(Expression switchValue,
      @Nullable Expression defaultBody, @Nullable Method method,
      Iterable<? extends SwitchCase> cases) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a SwitchExpression that represents a switch statement
   * that has a default case, using varargs.
   */
  public static SwitchStatement switch_(Expression switchValue,
      Expression defaultBody, Method method, SwitchCase... cases) {
    return switch_(switchValue, defaultBody, method, toList(cases));
  }   // 结束方法

  /**
   * Creates a SwitchExpression that represents a switch statement
   * that has a default case.
   */
  public static SwitchStatement switch_(Type type, Expression switchValue,
      Expression defaultBody, Method method,
      Iterable<? extends SwitchCase> cases) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a SwitchExpression that represents a switch statement
   * that has a default case, using varargs.
   */
  public static SwitchStatement switch_(Type type, Expression switchValue,
      Expression defaultBody, Method method, SwitchCase... cases) {
    return switch_(type, switchValue, defaultBody, method, toList(cases));
  }   // 结束方法

  /**
   * Creates a SwitchCase for use in a SwitchExpression.
   */
  public static SwitchCase switchCase(Expression expression,
      Iterable<? extends Expression> body) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a SwitchCase for use in a SwitchExpression, with varargs.
   */
  public static SwitchCase switchCase(Expression expression,
      Expression... body) {
    return switchCase(expression, toList(body));
  }   // 结束方法

  /**
   * Creates an instance of SymbolDocumentInfo.
   */
  public static SymbolDocumentInfo symbolDocument(String fileName) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates an instance of SymbolDocumentInfo.
   */
  public static SymbolDocumentInfo symbolDocument(String fileName,
      UUID language) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates an instance of SymbolDocumentInfo.
   */
  public static SymbolDocumentInfo symbolDocument(String fileName,
      UUID language, UUID vendor) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates an instance of SymbolDocumentInfo.
   */
  public static SymbolDocumentInfo symbolDocument(String filename,
      UUID language, UUID vendor, UUID documentType) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Create an expression from a statement.
   */
  public static Expression fromStatement(Statement statement) {
    FunctionExpression<Function<?>> lambda =
        Expressions.lambda(
            Blocks.toFunctionBlock(statement),
            Collections.emptyList());

    return Expressions.call(lambda, "apply");
  }   // 结束方法

  /**
   * Creates a statement that represents the throwing of an exception.
   */
  public static ThrowStatement throw_(Expression expression) {
    return new ThrowStatement(expression);
  }   // 结束方法

  /**
   * Creates a TryExpression representing a try block with any
   * number of catch statements and neither a fault nor finally
   * block.
   */
  public static TryStatement tryCatch(Statement body,
      Iterable<? extends CatchBlock> handlers) {     // 参数：catch块集合
    return new TryStatement(body, toList(handlers), null);
  }   // 结束方法

  /**
   * Creates a TryExpression representing a try block with any
   * number of catch statements and neither a fault nor finally
   * block, with varargs.
   */
  public static TryStatement tryCatch(Statement body,
      CatchBlock... handlers) {     // 参数：可变数量的catch块
    return new TryStatement(body, toList(handlers), null);
  }   // 结束方法

  /**
   * Creates a TryExpression representing a try block with any
   * number of catch statements and a finally block.
   */
  public static TryStatement tryCatchFinally(Statement body,
      Iterable<? extends CatchBlock> handlers, Statement finally_) {
    return new TryStatement(body, toList(handlers), finally_);
  }   // 结束方法

  /**
   * Creates a TryExpression representing a try block with any
   * number of catch statements and a finally block, with varargs.
   */
  public static TryStatement tryCatchFinally(Statement body, Statement finally_,
      CatchBlock... handlers) {     // 参数：可变数量的catch块
    return new TryStatement(body, toList(handlers), finally_);
  }   // 结束方法

  /**
   * Creates a TryExpression representing a try block with a
   * finally block and no catch statements.
   */
  public static TryStatement tryFinally(Statement body, Statement finally_) {
    return new TryStatement(body, ImmutableList.of(), finally_);
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents an explicit
   * reference or boxing conversion where null is supplied if the
   * conversion fails.
   */
  public static UnaryExpression typeAs(Expression expression, Type type) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a TypeBinaryExpression that compares run-time type
   * identity.
   */
  public static TypeBinaryExpression typeEqual(Expression expression,
      Type type) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a TypeBinaryExpression.
   */
  public static TypeBinaryExpression typeIs(Expression expression, Type type) {
    return new TypeBinaryExpression(ExpressionType.TypeIs, expression, type);
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents a unary plus
   * operation.
   */
  public static UnaryExpression unaryPlus(Expression expression) {
    return makeUnary(ExpressionType.UnaryPlus, expression);
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents a unary plus
   * operation.
   */
  public static UnaryExpression unaryPlus(Expression expression,
      Method method) {     // 参数：实现取模运算的方法
    return makeUnary(ExpressionType.UnaryPlus, expression, expression.getType(),
        method);
  }   // 结束方法

  /**
   * Creates a UnaryExpression that represents an explicit
   * unboxing.
   */
  public static UnaryExpression unbox(Expression expression, Type type) {
    return new UnaryExpression(ExpressionType.Unbox, type, expression);
  }   // 结束方法

  /**
   * Creates a ParameterExpression node that can be used to
   * identify a parameter or a variable in an expression tree.
   */
  public static ParameterExpression variable(Type type) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a ParameterExpression node that can be used to
   * identify a parameter or a variable in an expression tree.
   */
  public static ParameterExpression variable(Type type, String name) {
    return new ParameterExpression(0, type, name);
  }   // 结束方法

  /**
   * Reduces the node and then calls the visitor delegate on the
   * reduced expression. The method throws an exception if the node
   * is not reducible.
   */
  public static Expression visitChildren(ExpressionVisitor visitor) {
    throw Extensions.todo();     // 抛出未实现异常，标记此功能待实现
  }   // 结束方法

  /**
   * Creates a WhileExpression representing a while loop.
   */
  public static WhileStatement while_(Expression condition, Statement body) {
    return new WhileStatement(condition, body);
  }   // 结束方法

  /**
   * Creates a statement that declares a variable.
   */
  public static DeclarationStatement declare(int modifiers,
      ParameterExpression parameter, @Nullable Expression initializer) {     // 参数：字段参数、初始化表达式
    return new DeclarationStatement(modifiers, parameter, initializer);
  }   // 结束方法

  /**
   * Creates an expression that declares and initializes a variable. No
   * type is required; it is assumed that the variable is the same type as
   * the initializer. You can retrieve the {@link ParameterExpression} from
   * the {@link DeclarationStatement#parameter} field of the result.
   */
  public static DeclarationStatement declare(int modifiers, String name,
      Expression initializer) {
    checkNotNull(initializer,
        "empty initializer for variable declaration with name '%s', "
            + "modifiers %s. Please use declare(int, ParameterExpression, "
            + "initializer) instead", name, modifiers);
    return declare(modifiers, parameter(initializer.getType(), name),
        initializer);
  }   // 结束方法

  /**
   * Creates a statement that executes an expression.
   */
  public static Statement statement(@Nullable Expression expression) {
    return new GotoStatement(GotoExpressionKind.Sequence, null, expression);
  }   // 结束方法

  /** Combines a list of expressions using AND.
   * Returns TRUE if the list is empty.
   * Returns FALSE if any of the conditions are constant FALSE;
   * otherwise returns NULL if any of the conditions are constant NULL. */
  public static Expression foldAnd(List<Expression> conditions) {
    Expression e = null;
    int nullCount = 0;
    for (Expression condition : conditions) {
      if (condition instanceof ConstantExpression) {
        final Boolean value = (Boolean) ((ConstantExpression) condition).value;
        if (value == null) {
          ++nullCount;
          continue;
        } else if (value) {   // 结束方法
          continue;
        } else {   // 结束方法
          return constant(false);
        }   // 结束方法
      }   // 结束方法
      if (e == null) {
        e = condition;
      } else {   // 结束方法
        e = andAlso(e, condition);
      }   // 结束方法
    }   // 结束方法
    if (nullCount > 0) {
      return constant(null);
    }   // 结束方法
    if (e == null) {
      return constant(true);
    }   // 结束方法
    return e;
  }   // 结束方法

  /** Combines a list of expressions using OR.
   * Returns FALSE if the list is empty.
   * Returns TRUE if any of the conditions are constant TRUE;
   * otherwise returns NULL if all of the conditions are constant NULL. */
  public static Expression foldOr(List<Expression> conditions) {
    Expression e = null;
    int nullCount = 0;
    for (Expression condition : conditions) {
      if (condition instanceof ConstantExpression) {
        final Boolean value = (Boolean) ((ConstantExpression) condition).value;
        if (value == null) {
          ++nullCount;
          continue;
        } else if (value) {   // 结束方法
          return constant(true);
        } else {   // 结束方法
          continue;
        }   // 结束方法
      }   // 结束方法
      if (e == null) {
        e = condition;
      } else {   // 结束方法
        e = orElse(e, condition);
      }   // 结束方法
    }   // 结束方法
    if (e == null) {
      if (nullCount > 0) {
        return constant(null);
      }   // 结束方法
      return constant(false);
    }   // 结束方法
    return e;
  }   // 结束方法

  /**
   * Creates an empty fluent list.
   */
  public static <T> FluentList<T> list() {
    return new FluentArrayList<>();
  }   // 结束方法

  /**
   * Creates a fluent list with given elements.
   */
  @SafeVarargs public static <T> FluentList<T> list(T... ts) {
    return new FluentArrayList<>(Arrays.asList(ts));
  }   // 结束方法

  /**
   * Creates a fluent list with elements from the given collection.
   */
  public static <T> FluentList<T> list(Iterable<T> ts) {
    return new FluentArrayList<>(toList(ts));
  }   // 结束方法

  /**
   * Evaluates an expression and returns the result.
   */
  public static @Nullable Object evaluate(Node node) {
    requireNonNull(node, "node");
    final Evaluator evaluator = new Evaluator();
    return ((AbstractNode) node).evaluate(evaluator);
  }   // 结束方法

  // ~ Private helper methods ------------------------------------------------

  @SuppressWarnings("unused")
  private static boolean shouldLift(Expression left, Expression right,
      Method method) {     // 参数：实现取模运算的方法
    // FIXME: Implement the rules in modulo
    return true;
  }   // 结束方法

  private static Class deduceType(List<ParameterExpression> parameterList,
      Type type) {
    switch (parameterList.size()) {
    case 0:
      return Function0.class;
    case 1:
      return type == Boolean.TYPE ? Predicate1.class : Function1.class;
    case 2:
      return type == Boolean.TYPE ? Predicate2.class : Function2.class;
    default:     // 其他一元表达式类型
      return Function.class;
    }   // 结束方法
  }   // 结束方法

  /** Converts an Iterable to a List. */
  private static <T> List<T> toList(Iterable<? extends T> iterable) {
    if (iterable instanceof List) {
      return (List<T>) iterable;
    }   // 结束方法
    final List<T> list = new ArrayList<>();
    for (T parameter : iterable) {
      list.add(parameter);
    }   // 结束方法
    return list;
  }   // 结束方法

  private static <T> List<T> toList(T[] ts) {
    if (ts.length == 0) {
      return Collections.emptyList();
    } else {   // 结束方法
      return Arrays.asList(ts);
    }   // 结束方法
  }   // 结束方法

  private static <T> Collection<T> toCollection(Iterable<T> iterable) {
    if (iterable instanceof Collection) {
      return (Collection<T>) iterable;
    }   // 结束方法
    return toList(iterable);
  }   // 结束方法

  static List<Statement> acceptStatements(List<Statement> statements,
      Shuttle shuttle) {
    if (statements.isEmpty()) {
      return statements; // short cut
    }   // 结束方法
    final List<Statement> statements1 = new ArrayList<>();
    for (Statement statement : statements) {
      Statement newStatement = statement.accept(shuttle);
      if (newStatement instanceof GotoStatement) {
        GotoStatement goto_ = (GotoStatement) newStatement;
        if (goto_.kind == GotoExpressionKind.Sequence
            && goto_.expression == null) {
          // ignore empty statements
          continue;
        }   // 结束方法
      }   // 结束方法
      statements1.add(newStatement);
    }   // 结束方法
    return statements1;
  }   // 结束方法

  static List<Node> acceptNodes(List<Node> nodes, Shuttle shuttle) {
    if (nodes.isEmpty()) {
      return nodes; // short cut
    }   // 结束方法
    final List<Node> statements1 = new ArrayList<>();
    for (Node node : nodes) {
      statements1.add(node.accept(shuttle));
    }   // 结束方法
    return statements1;
  }   // 结束方法

  static List<Expression> acceptParameterExpressions(
      List<ParameterExpression> parameterExpressions, Shuttle shuttle) {
    if (parameterExpressions.isEmpty()) {
      return Collections.emptyList(); // short cut
    }   // 结束方法
    final ImmutableList.Builder<Expression> parameterExpressions1 = new ImmutableList.Builder<>();
    for (ParameterExpression parameterExpression : parameterExpressions) {
      parameterExpressions1.add(parameterExpression.accept(shuttle));
    }   // 结束方法
    return parameterExpressions1.build();
  }   // 结束方法

  static List<DeclarationStatement> acceptDeclarations(
      List<DeclarationStatement> declarations, Shuttle shuttle) {
    if (declarations.isEmpty()) {
      return declarations; // short cut
    }   // 结束方法
    final List<DeclarationStatement> declarations1 = new ArrayList<>();
    for (DeclarationStatement declaration : declarations) {
      declarations1.add(declaration.accept(shuttle));
    }   // 结束方法
    return declarations1;
  }   // 结束方法

  static List<MemberDeclaration> acceptMemberDeclarations(
      List<MemberDeclaration> memberDeclarations, Shuttle shuttle) {
    if (memberDeclarations.isEmpty()) {
      return memberDeclarations; // short cut
    }   // 结束方法
    final List<MemberDeclaration> memberDeclarations1 = new ArrayList<>();
    for (MemberDeclaration memberDeclaration : memberDeclarations) {
      memberDeclarations1.add(memberDeclaration.accept(shuttle));
    }   // 结束方法
    return memberDeclarations1;
  }   // 结束方法

  static List<Expression> acceptExpressions(List<Expression> expressions,
      Shuttle shuttle) {
    if (expressions.isEmpty()) {
      return expressions; // short cut
    }   // 结束方法
    final List<Expression> expressions1 = new ArrayList<>();
    for (Expression expression : expressions) {
      expressions1.add(expression.accept(shuttle));
    }   // 结束方法
    return expressions1;
  }   // 结束方法

  static <R> @Nullable R acceptNodes(@Nullable List<? extends Node> nodes,
      Visitor<R> visitor) {
    R r = null;
    if (nodes != null) {
      for (Node node : nodes) {
        r = node.accept(visitor);
      }   // 结束方法
    }   // 结束方法
    return r;
  }   // 结束方法

  // ~ Classes and interfaces ------------------------------------------------

  // Some interfaces we'd rather not implement yet. They don't seem relevant
  // in the Java world.

  /** Property info. */
  interface PropertyInfo {
  }   // 结束方法

  /** Runtime variables expression. */
  interface RuntimeVariablesExpression {
  }   // 结束方法

  /** Symbol document info. */
  interface SymbolDocumentInfo {
  }   // 结束方法

  /** Fluent list.
   *
   * @param <T> element type */
  public interface FluentList<T> extends List<T> {
    FluentList<T> append(T t);

    FluentList<T> appendIf(boolean condition, T t);

    FluentList<T> appendIfNotNull(@Nullable T t);

    FluentList<T> appendAll(Iterable<T> ts);

    FluentList<T> appendAll(T... ts);
  }   // 结束方法

  /** Fluent array list.
   *
   * @param <T> element type */
  private static class FluentArrayList<T> extends ArrayList<T>
      implements FluentList<T> {
    FluentArrayList() {
      super();
    }   // 结束方法

    FluentArrayList(Collection<? extends T> c) {
      super(c);
    }   // 结束方法

    @Override public FluentList<T> append(T t) {
      add(t);
      return this;
    }   // 结束方法

    @Override public FluentList<T> appendIf(boolean condition, T t) {
      if (condition) {
        add(t);
      }   // 结束方法
      return this;
    }   // 结束方法

    @Override public FluentList<T> appendIfNotNull(@Nullable T t) {
      if (t != null) {
        add(t);
      }   // 结束方法
      return this;
    }   // 结束方法

    @Override public FluentList<T> appendAll(Iterable<T> ts) {
      addAll(toCollection(ts));
      return this;
    }   // 结束方法

    @Override public FluentList<T> appendAll(T... ts) {
      addAll(Arrays.asList(ts));
      return this;
    }   // 结束方法
  }   // 结束方法
}
