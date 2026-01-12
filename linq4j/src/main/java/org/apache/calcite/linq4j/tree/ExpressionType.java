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
// Apache许可证声明，说明此代码遵循Apache 2.0许可证，允许自由使用、修改和分发
package org.apache.calcite.linq4j.tree; // 定义包名，表示此类属于Calcite的LINQ4J表达式树模块

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空类型注解，用于标记可能为null的字符串类型

/** Analogous to LINQ's System.Linq.Expressions.ExpressionType. */ // 类文档注释：此枚举类类似于.NET LINQ中的System.Linq.Expressions.ExpressionType，用于表示表达式树的节点类型
public enum ExpressionType { // 定义枚举类ExpressionType，用于表示各种表达式操作类型

  // Operator precedence and associativity is as follows. // 注释：以下是运算符优先级和结合性的说明
  //
  //  Priority Operators  Operation // 优先级、运算符和操作类型的对应关系表格
  //  ======== ========== ========================================
  //  1 left   [ ]        array index // 优先级1，左结合：数组索引操作
  //           ()         method call // 方法调用操作
  //           .          member access // 成员访问操作
  //  2 right  ++         pre- or postfix increment // 优先级2，右结合：前缀或后缀自增
  //           --         pre- or postfix decrement // 前缀或后缀自减
  //           + -        unary plus, minus // 一元加和一元减
  //           ~          bitwise NOT // 按位取反
  //           !          boolean (logical) NOT // 逻辑非
  //           (type)     type cast // 类型转换
  //           new        object creation // 对象创建
  //  3 left   * / %      multiplication, division, remainder // 优先级3，左结合：乘法、除法、取模
  //  4 left   + -        addition, subtraction // 优先级4，左结合：加法、减法
  //           +          string concatenation // 字符串连接
  //  5 left   <<         signed bit shift left // 优先级5，左结合：有符号左移
  //           >>         signed bit shift right // 有符号右移
  //           >>>        unsigned bit shift right // 无符号右移
  //  6 left   < <=       less than, less than or equal to // 优先级6，左结合：小于、小于等于
  //           > >=       greater than, greater than or equal to // 大于、大于等于
  //           instanceof reference test // 类型引用测试
  //  7 left   ==         equal to // 优先级7，左结合：相等比较
  //           !=         not equal to // 不等于比较
  //  8 left   &          bitwise AND // 优先级8，左结合：按位与
  //           &          boolean (logical) AND // 逻辑与
  //  9 left   ^          bitwise XOR // 优先级9，左结合：按位异或
  //           ^          boolean (logical) XOR // 逻辑异或
  //  10 left  |          bitwise OR // 优先级10，左结合：按位或
  //           |          boolean (logical) OR // 逻辑或
  //  11 left  &&         boolean (logical) AND // 优先级11，左结合：短路逻辑与
  //  12 left  ||         boolean (logical) OR // 优先级12，左结合：短路逻辑或
  //  13 right ? :        conditional right // 优先级13，右结合：条件三元运算符
  //  14 right =          assignment // 优先级14，右结合：赋值运算
  //           *= /= += -= %= // 复合赋值运算符：乘等、除等、加等、减等、模等
  //           <<= >>= >>>= // 移位复合赋值：左移等、右移等、无符号右移等
  //           &= ^= |=   combined assignment // 位运算复合赋值：与等、异或等、或等

  /**
   * An addition operation, such as a + b, without overflow
   * checking, for numeric operands.
   */ // 枚举常量文档注释：表示加法操作，如a + b，用于数值操作数，不进行溢出检查
  Add(" + ", false, 4, false), // 加法操作，操作符为" + "，不是后缀操作，优先级为4，不是右结合

  /**
   * An addition operation, such as (a + b), with overflow
   * checking, for numeric operands.
   */ // 枚举常量文档注释：表示带溢出检查的加法操作，如(a + b)，用于数值操作数
  AddChecked(" + ", false, 4, false), // 带溢出检查的加法操作，操作符为" + "，不是后缀操作，优先级为4，不是右结合

  /**
   * A bitwise or logical AND operation, such as {@code a & b} in Java.
   */ // 枚举常量文档注释：表示按位或逻辑与操作，如Java中的a & b
  And(" & ", false, 8, false), // 按位与操作，操作符为" & "，不是后缀操作，优先级为8，不是右结合

  /**
   * A conditional AND operation that evaluates the second operand
   * only if the first operand evaluates to true. It corresponds to
   * {@code a && b} in Java.
   */ // 枚举常量文档注释：表示条件与操作（短路与），只有当第一个操作数为true时才计算第二个操作数，对应Java中的a && b
  AndAlso(" && ", false, 11, false), // 短路逻辑与操作，操作符为" && "，不是后缀操作，优先级为11，不是右结合

  /**
   * An operation that obtains the length of a one-dimensional
   * array, such as array.Length.
   */ // 枚举常量文档注释：表示获取一维数组长度的操作，如array.Length
  ArrayLength, // 数组长度操作，使用默认构造函数，无操作符

  /**
   * An indexing operation in a one-dimensional array, such as
   * {@code array[index]} in Java.
   */ // 枚举常量文档注释：表示一维数组索引操作，如Java中的array[index]
  ArrayIndex, // 数组索引操作，使用默认构造函数，无操作符

  /**
   * A method call, such as in the {@code obj.sampleMethod()}
   * expression.
   */ // 枚举常量文档注释：表示方法调用操作，如obj.sampleMethod()表达式
  Call(".", false, 1, false), // 方法调用操作，操作符为"."，不是后缀操作，优先级为1，不是右结合

  /**
   * A node that represents a null coalescing operation, such
   * as (a ?? b) in C# or If(a, b) in Visual Basic.
   */ // 枚举常量文档注释：表示空值合并操作，如C#中的(a ?? b)或Visual Basic中的If(a, b)
  Coalesce, // 空值合并操作，使用默认构造函数，无操作符

  /**
   * A conditional operation, such as {@code a > b ? a : b} in Java.
   */ // 枚举常量文档注释：表示条件三元操作，如Java中的a > b ? a : b
  Conditional(" ? ", " : ", false, 13, true), // 条件三元操作，第一个操作符为" ? "，第二个操作符为" : "，不是后缀操作，优先级为13，是右结合

  /**
   * A constant value.
   */ // 枚举常量文档注释：表示常量值
  Constant, // 常量操作，使用默认构造函数，无操作符

  /**
   * A cast or conversion operation, such as {@code (SampleType) obj} in
   * Java. For a numeric
   * conversion, if the converted value is too large for the
   * destination type, no exception is thrown.
   */ // 枚举常量文档注释：表示类型转换操作，如Java中的(SampleType) obj，对于数值转换，如果转换后的值对于目标类型太大，不会抛出异常
  Convert(null, false, 2, true), // 类型转换操作，无操作符，不是后缀操作，优先级为2，是右结合

  /**
   * A cast or conversion operation, such as {@code (SampleType) obj} in
   * Java. For a numeric
   * conversion, if the converted value does not fit the
   * destination type, an exception is thrown.
   */ // 枚举常量文档注释：表示带检查的类型转换操作，如Java中的(SampleType) obj，对于数值转换，如果转换后的值不适合目标类型，会抛出异常
  ConvertChecked, // 带检查的类型转换操作，使用默认构造函数，无操作符

  /**
   * A division operation, such as (a / b), for numeric
   * operands.
   */ // 枚举常量文档注释：表示除法操作，如(a / b)，用于数值操作数
  Divide(" / ", false, 3, false), // 除法操作，操作符为" / "，不是后缀操作，优先级为3，不是右结合

  /**
   * A checked division operation, such as (a / b), for numeric
   * operands.
   */ // 枚举常量文档注释：表示带检查的除法操作，如(a / b)，用于数值操作数
  DivideChecked(" / ", false, 3, false), // 带检查的除法操作，操作符为" / "，不是后缀操作，优先级为3，不是右结合

  /**
   * A percent remainder operation, such as (a % b), for numeric
   * operands.
   */ // 枚举常量文档注释：表示取模操作，如(a % b)，用于数值操作数
  Mod(" % ", false, 3, false), // 取模操作，操作符为" % "，不是后缀操作，优先级为3，不是右结合

  /**
   * A node that represents an equality comparison, such as {@code a == b} in
   * Java.
   */ // 枚举常量文档注释：表示相等比较操作，如Java中的a == b
  Equal(" == ", false, 7, false), // 相等比较操作，操作符为" == "，不是后缀操作，优先级为7，不是右结合

  /**
   * A bitwise or logical XOR operation, such as {@code a ^ b} in Java.
   */ // 枚举常量文档注释：表示按位或逻辑异或操作，如Java中的a ^ b
  ExclusiveOr(" ^ ", false, 9, false), // 异或操作，操作符为" ^ "，不是后缀操作，优先级为9，不是右结合

  /**
   * A "greater than" comparison, such as (a &gt; b).
   */ // 枚举常量文档注释：表示大于比较操作，如(a > b)
  GreaterThan(" > ", false, 6, false), // 大于比较操作，操作符为" > "，不是后缀操作，优先级为6，不是右结合

  /**
   * A "greater than or equal to" comparison, such as (a &gt;=
   * b).
   */ // 枚举常量文档注释：表示大于等于比较操作，如(a >= b)
  GreaterThanOrEqual(" >= ", false, 6, false), // 大于等于比较操作，操作符为" >= "，不是后缀操作，优先级为6，不是右结合

  /**
   * An operation that invokes a delegate or lambda expression,
   * such as sampleDelegate.Invoke().
   */ // 枚举常量文档注释：表示调用委托或lambda表达式的操作，如sampleDelegate.Invoke()
  Invoke, // 委托调用操作，使用默认构造函数，无操作符

  /**
   * A lambda expression, such as {@code a -> a + a} in Java.
   */ // 枚举常量文档注释：表示lambda表达式，如Java中的a -> a + a
  Lambda, // Lambda表达式操作，使用默认构造函数，无操作符

  /**
   * A bitwise left-shift operation, such as {@code a << b} in Java.
   */ // 枚举常量文档注释：表示按位左移操作，如Java中的a << b
  LeftShift(" << ", false, 5, false), // 左移操作，操作符为" << "，不是后缀操作，优先级为5，不是右结合

  /**
   * A "less than" comparison, such as (a &lt; b).
   */ // 枚举常量文档注释：表示小于比较操作，如(a < b)
  LessThan(" < ", false, 6, false), // 小于比较操作，操作符为" < "，不是后缀操作，优先级为6，不是右结合

  /**
   * A "less than or equal to" comparison, such as (a &lt;= b).
   */ // 枚举常量文档注释：表示小于等于比较操作，如(a <= b)
  LessThanOrEqual(" <= ", false, 6, false), // 小于等于比较操作，操作符为" <= "，不是后缀操作，优先级为6，不是右结合

  /**
   * An operation that creates a new IEnumerable object and
   * initializes it from a list of elements, such as new
   * List&lt;SampleType&gt;(){ a, b, c } in C# or Dim sampleList = {
   * a, b, c } in Visual Basic.
   */ // 枚举常量文档注释：表示创建新的IEnumerable对象并从元素列表初始化的操作，如C#中的new List<SampleType>(){ a, b, c }
  ListInit, // 列表初始化操作，使用默认构造函数，无操作符

  /**
   * An operation that reads from a field or property, such as
   * obj.SampleProperty.
   */ // 枚举常量文档注释：表示读取字段或属性的操作，如obj.SampleProperty
  MemberAccess(".", false, 1, false), // 成员访问操作，操作符为"."，不是后缀操作，优先级为1，不是右结合

  /**
   * An operation that creates a new object and initializes one
   * or more of its members, such as new Point { X = 1, Y = 2 }
   * in C# or New Point With {.X = 1, .Y = 2} in Visual
   * Basic.
   */ // 枚举常量文档注释：表示创建新对象并初始化一个或多个成员的操作，如C#中的new Point { X = 1, Y = 2 }
  MemberInit, // 成员初始化操作，使用默认构造函数，无操作符

  /**
   * An arithmetic remainder operation, such as (a % b) in C#
   * or (a Mod b) in Visual Basic.
   */ // 枚举常量文档注释：表示算术取模操作，如C#中的(a % b)或Visual Basic中的(a Mod b)
  Modulo(" % ", false, 3, false), // 取模操作，操作符为" % "，不是后缀操作，优先级为3，不是右结合

  /**
   * A multiplication operation, such as (a * b), without
   * overflow checking, for numeric operands.
   */ // 枚举常量文档注释：表示乘法操作，如(a * b)，用于数值操作数，不进行溢出检查
  Multiply(" * ", false, 3, false), // 乘法操作，操作符为" * "，不是后缀操作，优先级为3，不是右结合

  /**
   * An multiplication operation, such as (a * b), that has
   * overflow checking, for numeric operands.
   */ // 枚举常量文档注释：表示带溢出检查的乘法操作，如(a * b)，用于数值操作数
  MultiplyChecked(" * ", false, 3, false), // 带溢出检查的乘法操作，操作符为" * "，不是后缀操作，优先级为3，不是右结合

  /**
   * An arithmetic negation operation, such as (-a). The object
   * a should not be modified in place.
   */ // 枚举常量文档注释：表示算术取反操作，如(-a)，对象a不应原地修改
  Negate("- ", false, 2, true), // 取反操作，操作符为"- "，不是后缀操作，优先级为2，是右结合

  /**
   * A unary plus operation, such as (+a). The result of a
   * predefined unary plus operation is the value of the
   * operand, but user-defined implementations might have
   * unusual results.
   */ // 枚举常量文档注释：表示一元加操作，如(+a)，预定义的一元加操作结果是操作数的值，但用户定义的实现可能有异常结果
  UnaryPlus("+ ", false, 2, true), // 一元加操作，操作符为"+ "，不是后缀操作，优先级为2，是右结合

  /**
   * An arithmetic negation operation, such as (-a), that has
   * overflow checking. The object a should not be modified in
   * place.
   */ // 枚举常量文档注释：表示带溢出检查的算术取反操作，如(-a)，对象a不应原地修改
  NegateChecked("-", false, 2, true), // 带检查的取反操作，操作符为"-"，不是后缀操作，优先级为2，是右结合

  /**
   * An operation that calls a constructor to create a new
   * object, such as new SampleType().
   */ // 枚举常量文档注释：表示调用构造函数创建新对象的操作，如new SampleType()
  New, // 对象创建操作，使用默认构造函数，无操作符

  /**
   * An operation that creates a new one-dimensional array and
   * initializes it from a list of elements, such as new
   * SampleType[]{a, b, c} in C# or New SampleType(){a, b, c} in
   * Visual Basic.
   */ // 枚举常量文档注释：表示创建新的一维数组并从元素列表初始化的操作，如C#中的new SampleType[]{a, b, c}
  NewArrayInit, // 数组初始化操作，使用默认构造函数，无操作符

  /**
   * An operation that creates a new array, in which the bounds
   * for each dimension are specified, such as new
   * SampleType[dim1, dim2] in C# or New SampleType(dim1, dim2)
   * in Visual Basic.
   */ // 枚举常量文档注释：表示创建新数组的操作，其中指定了每个维度的边界，如C#中的new SampleType[dim1, dim2]
  NewArrayBounds, // 数组边界创建操作，使用默认构造函数，无操作符

  /**
   * A bitwise complement or logical negation operation. In C#,
   * it is equivalent to (~a) for integral types and to (!a) for
   * Boolean values. In Visual Basic, it is equivalent to (Not
   * a). The object a should not be modified in place.
   */ // 枚举常量文档注释：表示按位补码或逻辑非操作，在C#中等同于(~a)用于整数类型和(!a)用于布尔值
  Not("!", false, 2, true), // 逻辑非操作，操作符为"!"，不是后缀操作，优先级为2，是右结合

  /**
   * An inequality comparison, such as (a != b) in C# or (a &lt;&gt;
   * b) in Visual Basic.
   */ // 枚举常量文档注释：表示不等比较操作，如C#中的(a != b)或Visual Basic中的(a <> b)
  NotEqual(" != ", false, 7, false), // 不等比较操作，操作符为" != "，不是后缀操作，优先级为7，不是右结合

  /**
   * A bitwise or logical OR operation, such as (a | b) in C#
   * or (a Or b) in Visual Basic.
   */ // 枚举常量文档注释：表示按位或逻辑或操作，如C#中的(a | b)或Visual Basic中的(a Or b)
  Or(" | ", false, 10, false), // 按位或操作，操作符为" | "，不是后缀操作，优先级为10，不是右结合

  /**
   * A short-circuiting conditional OR operation, such as (a ||
   * b) in C# or (a OrElse b) in Visual Basic.
   */ // 枚举常量文档注释：表示短路条件或操作，如C#中的(a || b)或Visual Basic中的(a OrElse b)
  OrElse(" || ", false, 12, false), // 短路逻辑或操作，操作符为" || "，不是后缀操作，优先级为12，不是右结合

  /**
   * A reference to a parameter or variable that is defined in
   * the context of the expression. For more information, see
   * ParameterExpression.
   */ // 枚举常量文档注释：表示对表达式上下文中定义的参数或变量的引用，更多信息参见ParameterExpression
  Parameter, // 参数引用操作，使用默认构造函数，无操作符

  /**
   * A mathematical operation that raises a number to a power,
   * such as (a ^ b) in Visual Basic.
   */ // 枚举常量文档注释：表示幂运算操作，如Visual Basic中的(a ^ b)
  Power, // 幂运算操作，使用默认构造函数，无操作符

  /**
   * An expression that has a constant value of type
   * Expression. A Quote node can contain references to
   * parameters that are defined in the context of the
   * expression it represents.
   */ // 枚举常量文档注释：表示具有Expression类型常量值的表达式，Quote节点可以包含对它所表示的表达式上下文中定义的参数的引用
  Quote, // 引用操作，使用默认构造函数，无操作符

  /**
   * A bitwise right-shift operation, such as (a &gt;*gt; b).
   */ // 枚举常量文档注释：表示按位右移操作，如(a >> b)
  RightShift(" >> ", false, 5, false), // 右移操作，操作符为" >> "，不是后缀操作，优先级为5，不是右结合

  /**
   * A subtraction operation, such as (a - b), without overflow
   * checking, for numeric operands.
   */ // 枚举常量文档注释：表示减法操作，如(a - b)，用于数值操作数，不进行溢出检查
  Subtract(" - ", false, 4, false), // 减法操作，操作符为" - "，不是后缀操作，优先级为4，不是右结合

  /**
   * An arithmetic subtraction operation, such as (a - b), that
   * has overflow checking, for numeric operands.
   */ // 枚举常量文档注释：表示带溢出检查的算术减法操作，如(a - b)，用于数值操作数
  SubtractChecked(" - ", false, 4, false), // 带检查的减法操作，操作符为" - "，不是后缀操作，优先级为4，不是右结合

  /**
   * An explicit reference or boxing conversion in which null
   * is supplied if the conversion fails, such as (obj as
   * SampleType) in C# or TryCast(obj, SampleType) in Visual
   * Basic.
   */ // 枚举常量文档注释：表示显式引用或装箱转换，如果转换失败则提供null，如C#中的(obj as SampleType)
  TypeAs, // 类型转换操作，使用默认构造函数，无操作符

  /**
   * A type test, such as obj is SampleType in C# or TypeOf obj
   * is SampleType in Visual Basic.
   */ // 枚举常量文档注释：表示类型测试操作，如C#中的obj is SampleType或Visual Basic中的TypeOf obj is SampleType
  TypeIs(" instanceof ", false, 6, false), // 类型测试操作，操作符为" instanceof "，不是后缀操作，优先级为6，不是右结合

  /**
   * An assignment operation, such as (a = b).
   */ // 枚举常量文档注释：表示赋值操作，如(a = b)
  Assign(" = ", null, false, 14, true, true), // 赋值操作，操作符为" = "，无第二个操作符，不是后缀操作，优先级为14，是右结合，会修改左值

  /**
   * A block of expressions.
   */ // 枚举常量文档注释：表示表达式块
  Block, // 代码块操作，使用默认构造函数，无操作符

  /**
   * Debugging information.
   */ // 枚举常量文档注释：表示调试信息
  DebugInfo, // 调试信息操作，使用默认构造函数，无操作符

  /**
   * A unary decrement operation, such as (a - 1) in C# and
   * Visual Basic. The object a should not be modified in
   * place.
   */ // 枚举常量文档注释：表示一元减量操作，如C#和Visual Basic中的(a - 1)，对象a不应原地修改
  Decrement, // 减量操作，使用默认构造函数，无操作符

  /**
   * A dynamic operation.
   */ // 枚举常量文档注释：表示动态操作
  Dynamic, // 动态操作，使用默认构造函数，无操作符

  /**
   * A default value.
   */ // 枚举常量文档注释：表示默认值
  Default, // 默认值操作，使用默认构造函数，无操作符

  /**
   * An extension expression.
   */ // 枚举常量文档注释：表示扩展表达式
  Extension, // 扩展操作，使用默认构造函数，无操作符

  /**
   * A "go to" expression, such as goto Label in C# or GoTo
   * Label in Visual Basic.
   */ // 枚举常量文档注释：表示跳转表达式，如C#中的goto Label或Visual Basic中的GoTo Label
  Goto, // 跳转操作，使用默认构造函数，无操作符

  /**
   * A unary increment operation, such as (a + 1) in C# and
   * Visual Basic. The object a should not be modified in
   * place.
   */ // 枚举常量文档注释：表示一元增量操作，如C#和Visual Basic中的(a + 1)，对象a不应原地修改
  Increment, // 增量操作，使用默认构造函数，无操作符

  /**
   * An index operation or an operation that accesses a
   * property that takes arguments.
   */ // 枚举常量文档注释：表示索引操作或访问带参数的属性的操作
  Index, // 索引操作，使用默认构造函数，无操作符

  /**
   * A label.
   */ // 枚举常量文档注释：表示标签
  Label, // 标签操作，使用默认构造函数，无操作符

  /**
   * A list of run-time variables. For more information, see
   * RuntimeVariablesExpression.
   */ // 枚举常量文档注释：表示运行时变量列表，更多信息参见RuntimeVariablesExpression
  RuntimeVariables, // 运行时变量操作，使用默认构造函数，无操作符

  /**
   * A loop, such as for or while.
   */ // 枚举常量文档注释：表示循环，如for或while
  Loop, // 循环操作，使用默认构造函数，无操作符

  /**
   * A switch operation, such as switch in C# or Select Case in
   * Visual Basic.
   */ // 枚举常量文档注释：表示switch操作，如C#中的switch或Visual Basic中的Select Case
  Switch, // 选择操作，使用默认构造函数，无操作符

  /**
   * An operation that throws an exception, such as throw new
   * Exception().
   */ // 枚举常量文档注释：表示抛出异常的操作，如throw new Exception()
  Throw, // 抛出异常操作，使用默认构造函数，无操作符

  /**
   * A try-catch expression.
   */ // 枚举常量文档注释：表示try-catch表达式
  Try, // 异常捕获操作，使用默认构造函数，无操作符

  /**
   * An unbox value type operation, such as unbox and unbox.any
   * instructions in MSIL.
   */ // 枚举常量文档注释：表示拆箱值类型操作，如MSIL中的unbox和unbox.any指令
  Unbox, // 拆箱操作，使用默认构造函数，无操作符

  /**
   * An addition compound assignment operation, such as (a +=
   * b), without overflow checking, for numeric operands.
   */ // 枚举常量文档注释：表示加法复合赋值操作，如(a += b)，用于数值操作数，不进行溢出检查
  AddAssign(" += ", null, false, 14, true, true), // 加法赋值操作，操作符为" += "，无第二个操作符，不是后缀操作，优先级为14，是右结合，会修改左值

  /**
   * A bitwise or logical AND compound assignment operation,
   * such as (a &amp;= b) in C#.
   */ // 枚举常量文档注释：表示按位或逻辑与复合赋值操作，如C#中的(a &= b)
  AndAssign(" &= ", null, false, 14, true, true), // 与赋值操作，操作符为" &= "，无第二个操作符，不是后缀操作，优先级为14，是右结合，会修改左值

  /**
   * An division compound assignment operation, such as (a /=
   * b), for numeric operands.
   */ // 枚举常量文档注释：表示除法复合赋值操作，如(a /= b)，用于数值操作数
  DivideAssign(" /= ", null, false, 14, true, true), // 除法赋值操作，操作符为" /= "，无第二个操作符，不是后缀操作，优先级为14，是右结合，会修改左值

  /**
   * A bitwise or logical XOR compound assignment operation,
   * such as (a ^= b) in C#.
   */ // 枚举常量文档注释：表示按位或逻辑异或复合赋值操作，如C#中的(a ^= b)
  ExclusiveOrAssign(" ^= ", null, false, 14, true, true), // 异或赋值操作，操作符为" ^= "，无第二个操作符，不是后缀操作，优先级为14，是右结合，会修改左值

  /**
   * A bitwise left-shift compound assignment, such as (a &lt;&lt;=
   * b).
   */ // 枚举常量文档注释：表示按位左移复合赋值操作，如(a <<= b)
  LeftShiftAssign(" <<= ", null, false, 14, true, true), // 左移赋值操作，操作符为" <<= "，无第二个操作符，不是后缀操作，优先级为14，是右结合，会修改左值

  /**
   * An arithmetic remainder compound assignment operation,
   * such as (a %= b) in C#.
   */ // 枚举常量文档注释：表示算术取模复合赋值操作，如C#中的(a %= b)
  ModuloAssign(" %= ", null, false, 14, true, true), // 取模赋值操作，操作符为" %= "，无第二个操作符，不是后缀操作，优先级为14，是右结合，会修改左值

  /**
   * A multiplication compound assignment operation, such as (a
   * *= b), without overflow checking, for numeric operands.
   */ // 枚举常量文档注释：表示乘法复合赋值操作，如(a *= b)，用于数值操作数，不进行溢出检查
  MultiplyAssign(" *= ", null, false, 14, true, true), // 乘法赋值操作，操作符为" *= "，无第二个操作符，不是后缀操作，优先级为14，是右结合，会修改左值

  /**
   * A bitwise or logical OR compound assignment, such as (a |=
   * b) in C#.
   */ // 枚举常量文档注释：表示按位或逻辑或复合赋值操作，如C#中的(a |= b)
  OrAssign(" |= ", null, false, 14, true, true), // 或赋值操作，操作符为" |= "，无第二个操作符，不是后缀操作，优先级为14，是右结合，会修改左值

  /**
   * A compound assignment operation that raises a number to a
   * power, such as (a ^= b) in Visual Basic.
   */ // 枚举常量文档注释：表示幂运算复合赋值操作，如Visual Basic中的(a ^= b)
  PowerAssign, // 幂赋值操作，使用默认构造函数，无操作符

  /**
   * A bitwise right-shift compound assignment operation, such
   * as (a &gt;&gt;= b).
   */ // 枚举常量文档注释：表示按位右移复合赋值操作，如(a >>= b)
  RightShiftAssign(" >>= ", null, false, 14, true, true), // 右移赋值操作，操作符为" >>= "，无第二个操作符，不是后缀操作，优先级为14，是右结合，会修改左值

  /**
   * A subtraction compound assignment operation, such as (a -=
   * b), without overflow checking, for numeric operands.
   */ // 枚举常量文档注释：表示减法复合赋值操作，如(a -= b)，用于数值操作数，不进行溢出检查
  SubtractAssign(" -= ", null, false, 14, true, true), // 减法赋值操作，操作符为" -= "，无第二个操作符，不是后缀操作，优先级为14，是右结合，会修改左值

  /**
   * An addition compound assignment operation, such as (a +=
   * b), with overflow checking, for numeric operands.
   */ // 枚举常量文档注释：表示带溢出检查的加法复合赋值操作，如(a += b)，用于数值操作数
  AddAssignChecked(" += ", null, false, 14, true), // 带检查的加法赋值操作，操作符为" += "，无第二个操作符，不是后缀操作，优先级为14，是右结合

  /**
   * A multiplication compound assignment operation, such as (a
   * *= b), that has overflow checking, for numeric operands.
   */ // 枚举常量文档注释：表示带溢出检查的乘法复合赋值操作，如(a *= b)，用于数值操作数
  MultiplyAssignChecked(" *= ", null, false, 14, true, true), // 带检查的乘法赋值操作，操作符为" *= "，无第二个操作符，不是后缀操作，优先级为14，是右结合，会修改左值

  /**
   * A subtraction compound assignment operation, such as (a -=
   * b), that has overflow checking, for numeric operands.
   */ // 枚举常量文档注释：表示带溢出检查的减法复合赋值操作，如(a -= b)，用于数值操作数
  SubtractAssignChecked(" -= ", null, false, 14, true, true), // 带检查的减法赋值操作，操作符为" -= "，无第二个操作符，不是后缀操作，优先级为14，是右结合，会修改左值

  /**
   * A unary prefix increment, such as (++a). The object a
   * should be modified in place.
   */ // 枚举常量文档注释：表示一元前缀增量，如(++a)，对象a应该原地修改
  PreIncrementAssign("++", null, false, 2, true, true), // 前缀自增赋值操作，操作符为"++"，无第二个操作符，不是后缀操作，优先级为2，是右结合，会修改左值

  /**
   * A unary prefix decrement, such as (--a). The object a
   * should be modified in place.
   */ // 枚举常量文档注释：表示一元前缀减量，如(--a)，对象a应该原地修改
  PreDecrementAssign("--", null, false, 2, true, true), // 前缀自减赋值操作，操作符为"--"，无第二个操作符，不是后缀操作，优先级为2，是右结合，会修改左值

  /**
   * A unary postfix increment, such as (a++). The object a
   * should be modified in place.
   */ // 枚举常量文档注释：表示一元后缀增量，如(a++)，对象a应该原地修改
  PostIncrementAssign("++", null, true, 2, true, true), // 后缀自增赋值操作，操作符为"++"，无第二个操作符，是后缀操作，优先级为2，是右结合，会修改左值

  /**
   * A unary postfix decrement, such as (a--). The object a
   * should be modified in place.
   */ // 枚举常量文档注释：表示一元后缀减量，如(a--)，对象a应该原地修改
  PostDecrementAssign("--", null, true, 2, true, true), // 后缀自减赋值操作，操作符为"--"，无第二个操作符，是后缀操作，优先级为2，是右结合，会修改左值

  /**
   * An exact type test.
   */ // 枚举常量文档注释：表示精确类型测试
  TypeEqual, // 精确类型测试操作，使用默认构造函数，无操作符

  /**
   * A ones complement operation, such as (~a) in C#.
   */ // 枚举常量文档注释：表示按位补码操作，如C#中的(~a)
  OnesComplement("~", false, 2, true), // 按位补码操作，操作符为"~"，不是后缀操作，优先级为2，是右结合

  /**
   * A true condition value.
   */ // 枚举常量文档注释：表示真条件值
  IsTrue, // 真值测试操作，使用默认构造函数，无操作符

  /**
   * A false condition value.
   */ // 枚举常量文档注释：表示假条件值
  IsFalse, // 假值测试操作，使用默认构造函数，无操作符

  /**
   * Declaration of a variable.
   */ // 枚举常量文档注释：表示变量声明
  Declaration, // 变量声明操作，使用默认构造函数，无操作符

  /**
   * For loop.
   */ // 枚举常量文档注释：表示for循环
  For, // for循环操作，使用默认构造函数，无操作符

  /** For-each loop, "for (Type i : expression) body". */ // 枚举常量文档注释：表示for-each循环，如"for (Type i : expression) body"
  ForEach, // for-each循环操作，使用默认构造函数，无操作符

  /**
   * While loop.
   */ // 枚举常量文档注释：表示while循环
  While; // while循环操作，使用默认构造函数，无操作符

  final @Nullable String op; // 成员变量：表示操作符字符串，如"+", "-", "*"等，可为null
  final @Nullable String op2; // 成员变量：表示第二个操作符字符串，主要用于三元运算符的" : "部分，可为null
  final boolean postfix; // 成员变量：表示是否为后缀操作符，如a++中的++就是后缀操作符，true表示是后缀
  final int lprec; // 成员变量：表示左优先级，用于确定运算符在表达式树中左边的优先级，数值越大优先级越高
  final int rprec; // 成员变量：表示右优先级，用于确定运算符在表达式树中右边的优先级，数值越大优先级越高
  final boolean modifiesLvalue; // 成员变量：表示是否修改左值，true表示该操作会修改左操作数的值（如赋值操作）

  ExpressionType() { // 无参构造函数：用于创建没有操作符和优先级的表达式类型
    this(null, false, 0, false); // 调用四参数构造函数，操作符为null，不是后缀，优先级为0，不是右结合
  } // 无参构造函数结束

  ExpressionType(@Nullable String op, boolean postfix, int prec, boolean right) { // 四参数构造函数：创建指定操作符、是否后缀、优先级和是否右结合的表达式类型
    this(op, null, postfix, prec, right); // 调用五参数构造函数，第二个操作符为null
  } // 四参数构造函数结束

  ExpressionType(@Nullable String op, @Nullable String op2, boolean postfix, int prec,
      boolean right) { // 五参数构造函数：创建指定两个操作符、是否后缀、优先级和是否右结合的表达式类型
    this(op, op2, postfix, prec, right, false); // 调用六参数构造函数，不修改左值
  } // 五参数构造函数结束

  ExpressionType(@Nullable String op, @Nullable String op2, boolean postfix, int prec,
      boolean right, boolean modifiesLvalue) { // 六参数构造函数：完整的构造函数，设置所有成员变量
    this.op = op; // 初始化操作符字符串
    this.op2 = op2; // 初始化第二个操作符字符串
    this.postfix = postfix; // 初始化是否为后缀操作符
    this.modifiesLvalue = modifiesLvalue; // 初始化是否修改左值
    this.lprec = (20 - prec) * 2 + (right ? 1 : 0); // 计算左优先级：优先级越高，数值越大；右结合时左优先级+1
    this.rprec = (20 - prec) * 2 + (right ? 0 : 1); // 计算右优先级：优先级越高，数值越大；右结合时右优先级+0
  } // 六参数构造函数结束
} // 枚举类ExpressionType定义结束
