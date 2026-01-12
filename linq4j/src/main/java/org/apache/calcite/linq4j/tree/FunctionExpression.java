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
package org.apache.calcite.linq4j.tree; // 定义包名，属于Calcite的LINQ4J模块中的表达式树包

import org.apache.calcite.linq4j.function.Function; // 导入函数接口基类
import org.apache.calcite.linq4j.function.Functions; // 导入函数工具类

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类
import com.google.common.collect.Lists; // 导入Google Guava的列表工具类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性注解

import java.lang.reflect.Method; // 导入方法反射类
import java.lang.reflect.Proxy; // 导入动态代理类
import java.lang.reflect.Type; // 导入类型反射接口
import java.lang.reflect.TypeVariable; // 导入类型变量反射类
import java.util.ArrayList; // 导入动态数组列表类
import java.util.List; // 导入列表接口
import java.util.Objects; // 导入对象工具类

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法

/**
 * Represents a strongly typed lambda expression as a data structure in the form
 * of an expression tree. This class cannot be inherited.
 * 表示以表达式树形式存在的强类型Lambda表达式数据结构。此类不能被继承。
 * 
 * 这个类是Calcite LINQ4J框架的核心类之一，用于将Lambda表达式表示为可操作的数据结构。
 * 它允许在运行时分析、修改和执行Lambda表达式，而不需要编译它们。
 * 
 * 主要功能：
 * 1. 将Lambda表达式表示为表达式树，可以遍历和修改
 * 2. 支持从表达式树动态生成可调用函数
 * 3. 支持生成Java代码，用于编译时或运行时的代码生成
 * 4. 处理类型擦除和桥接方法，确保泛型类型的正确性
 * 5. 支持原始类型和包装类型的自动转换
 *
 * @param <F> Function type - 函数类型参数，必须是Function接口的子类
 */
public final class FunctionExpression<F extends Function<?>> // 定义最终的函数表达式类，泛型F必须是Function的子类
    extends LambdaExpression { // 继承自LambdaExpression基类，表示Lambda表达式
  public final @Nullable F function; // 可空的函数对象，如果已提供则可以直接调用，否则需要从body生成
  public final @Nullable BlockStatement body; // 可空的代码块语句，表示函数体，如果为null则使用function
  public final List<ParameterExpression> parameterList; // 参数表达式列表，存储函数的所有参数信息
  private @Nullable F dynamicFunction; // 私有的动态生成的函数对象，通过代理方式从body编译生成
  /** Cached hash code for the expression. */
  private int hash; // 缓存的哈希码，用于提高equals和hashCode的性能

  // 私有构造函数，用于创建FunctionExpression实例
  private FunctionExpression(Class<F> type, @Nullable F function, // 函数类型Class对象，可选的函数对象
      @Nullable BlockStatement body, // 可选的代码块语句
      List<ParameterExpression> parameterList) { // 参数表达式列表
    super(ExpressionType.Lambda, type); // 调用父类构造函数，设置为Lambda表达式类型
    if (function == null && body == null) { // 如果函数和代码块都为null，抛出异常
      throw new IllegalArgumentException( // 抛出非法参数异常
          "both function and body should not be null"); // 提示函数和代码块不能同时为null
    }
    this.function = function; // 保存函数对象
    this.body = body; // 保存代码块语句
    this.parameterList = requireNonNull(parameterList, "parameterList"); // 保存参数列表，确保不为null
  }

  // 构造函数，从现有的函数对象创建FunctionExpression
  @SuppressWarnings({"unchecked", "rawtypes"}) // 抑制未检查的转换警告
  public FunctionExpression(F function) { // 接受一个函数对象作为参数
    this((Class) function.getClass(), function, null, ImmutableList.of()); // 调用私有构造函数，body为null，参数列表为空
  }

  // 构造函数，从代码块和参数列表创建FunctionExpression
  public FunctionExpression(Class<F> type, BlockStatement body, // 函数类型Class对象和代码块语句
      List<ParameterExpression> parameters) { // 参数表达式列表
    this(type, null, body, parameters); // 调用私有构造函数，function为null
  }

  // 接受访问者模式中的Shuttle访问器，用于遍历和转换表达式树
  @Override public Expression accept(Shuttle shuttle) { // 接受Shuttle访问器
    shuttle = shuttle.preVisit(this); // 先进行前置访问
    BlockStatement body = this.body == null ? null : this.body.accept(shuttle); // 如果body不为null，则接受访问器处理
    return shuttle.visit(this, body); // 访问器访问当前节点并返回可能修改后的表达式
  }

  // 接受访问者模式中的Visitor访问器，用于只读访问表达式树
  @Override public <R> R accept(Visitor<R> visitor) { // 接受Visitor访问器，返回类型R
    return visitor.visit(this); // 访问器访问当前节点并返回结果
  }

  // 编译函数表达式，返回一个可调用的Invokable对象
  public Invokable compile() { // 编译方法
    return args -> { // 返回一个lambda表达式，接受参数数组
      final Evaluator evaluator = new Evaluator(); // 创建表达式求值器
      for (int i = 0; i < args.length; i++) { // 遍历所有参数
        evaluator.push(parameterList.get(i), args[i]); // 将参数压入求值器的栈中
      }
      return evaluator.evaluate(requireNonNull(body, "body")); // 求值器执行body并返回结果
    };
  }

  // 获取函数对象，如果不存在则动态生成
  public F getFunction() { // 获取函数对象
    if (function != null) { // 如果函数对象已存在
      return function; // 直接返回
    }
    if (dynamicFunction == null) { // 如果动态函数还未生成
      final Invokable x = compile(); // 编译生成Invokable对象

      ClassLoader classLoader = requireNonNull(getClass().getClassLoader()); // 获取类加载器
      //noinspection unchecked // 抑制未检查的转换警告
      dynamicFunction = // 使用动态代理创建函数对象
          (F) Proxy.newProxyInstance(classLoader, // 使用类加载器创建代理实例
              new Class[]{Types.toClass(type)}, // 代理实现的接口
              (proxy, method, args) -> x.dynamicInvoke(args)); // 代理方法调用委托给Invokable的dynamicInvoke
    }
    return dynamicFunction; // 返回动态生成的函数对象
  }

  // 接受表达式写入器，将函数表达式转换为Java代码字符串
  @Override void accept(ExpressionWriter writer, int lprec, int rprec) { // 接受表达式写入器，lprec和rprec为左右优先级
    // "new Function1() {
    //    public Result apply(T1 p1, ...) {
    //        <body>
    //    }
    //    // bridge method
    //    public Object apply(Object p1, ...) {
    //        return apply((T1) p1, ...);
    //    }
    // }
    //
    // if any arguments are primitive there is an extra bridge method:
    //
    //  new Function1() {
    //    public double apply(double p1, int p2) {
    //      <body>
    //    }
    //    // box bridge method
    //    public Double apply(Double p1, Integer p2) {
    //      return apply(p1.doubleValue(), p2.intValue());
    //    }
    //    // bridge method
    //    public Object apply(Object p1, Object p2) {
    //      return apply((Double) p1, (Integer) p2);
    //    }
    List<String> params = new ArrayList<>(); // 创建参数声明字符串列表
    List<String> bridgeParams = new ArrayList<>(); // 创建桥接方法参数声明字符串列表
    List<String> bridgeArgs = new ArrayList<>(); // 创建桥接方法调用参数字符串列表
    List<String> boxBridgeParams = new ArrayList<>(); // 创建装箱桥接方法参数声明字符串列表
    List<String> boxBridgeArgs = new ArrayList<>(); // 创建装箱桥接方法调用参数字符串列表
    for (ParameterExpression parameterExpression : parameterList) { // 遍历所有参数表达式
      final Type parameterType = parameterExpression.getType(); // 获取参数类型
      final Type parameterBoxType = Types.box(parameterType); // 获取参数的装箱类型
      final String parameterBoxTypeName = Types.className(parameterBoxType); // 获取装箱类型的类名
      params.add(parameterExpression.declString()); // 添加参数声明字符串
      bridgeParams.add(parameterExpression.declString(Object.class)); // 添加桥接方法参数声明（使用Object类型）
      bridgeArgs.add("(" + parameterBoxTypeName + ") " // 添加桥接方法调用参数（需要类型转换）
          + parameterExpression.name);

      boxBridgeParams.add(parameterExpression.declString(parameterBoxType)); // 添加装箱桥接方法参数声明
      boxBridgeArgs.add(parameterExpression.name // 添加装箱桥接方法调用参数
          + (Primitive.is(parameterType) // 如果是原始类型
          ? "." + requireNonNull(Primitive.of(parameterType)).primitiveName + "Value()" // 则调用xxxValue()方法拆箱
          : "")); // 否则直接使用
    }
    requireNonNull(body, "body"); // 确保body不为null
    Type bridgeResultType = Functions.FUNCTION_RESULT_TYPES.get(this.type); // 从函数结果类型映射中获取桥接结果类型
    if (bridgeResultType == null) { // 如果映射中没有找到
      bridgeResultType = body.getType(); // 则使用body的类型
    }
    Type resultType2 = bridgeResultType; // 初始化结果类型2
    if (bridgeResultType == Object.class // 如果桥接结果类型是Object
        && !params.equals(bridgeParams) // 且参数列表不等于桥接参数列表
        && !(body.getType() instanceof TypeVariable)) { // 且body类型不是类型变量
      resultType2 = body.getType(); // 则使用body的类型作为结果类型
    }
    String methodName = getAbstractMethodName(); // 获取抽象方法名（通常是apply）
    writer.append("new ") // 开始生成匿名类代码
        .append(type) // 添加函数类型
        .append("()") // 添加构造函数调用
        .begin(" {\n") // 开始匿名类体
        .append("public ") // 添加public修饰符
        .append(Types.className(resultType2)) // 添加返回类型类名
        .list(" " + methodName + "(", // 添加方法名和左括号
                ", ", ") ", params) // 添加参数列表和右括号
        .append(Blocks.toFunctionBlock(body)); // 添加方法体

    // Generate an intermediate bridge method if at least one parameter is
    // primitive.
    // 如果至少有一个参数是原始类型，则生成中间桥接方法
    final String bridgeResultTypeName = // 计算桥接方法返回类型名
        isAbstractMethodPrimitive() // 如果抽象方法返回原始类型
            ? Types.className(bridgeResultType) // 则使用原始类型名
            : Types.className(Types.box(bridgeResultType)); // 否则使用装箱类型名
    if (!boxBridgeParams.equals(params)) { // 如果装箱桥接参数不等于原始参数
      writer // 生成装箱桥接方法
          .append("public ") // 添加public修饰符
          .append(bridgeResultTypeName) // 添加返回类型
          .list(" " + methodName + "(", ", ", ") ", boxBridgeParams) // 添加方法签名
          .begin("{\n") // 开始方法体
          .list("return " + methodName + "(\n", ",\n", ");\n", boxBridgeArgs) // 添加return语句
          .end("}\n"); // 结束方法体
    }

    // Generate a bridge method. Argument types are looser (as if every
    // type parameter is set to 'Object').
    // 生成桥接方法。参数类型更宽松（就像每个类型参数都设置为'Object'）。
    //
    // Skip the bridge method if there are no arguments. It would have the
    // same overload as the regular method.
    // 如果没有参数，则跳过桥接方法。它将与常规方法具有相同的重载。
    if (!bridgeParams.equals(params)) { // 如果桥接参数不等于原始参数
      writer // 生成桥接方法
        .append("public ") // 添加public修饰符
        .append(bridgeResultTypeName) // 添加返回类型
        .list(" " + methodName + "(", ", ", ") ", bridgeParams) // 添加方法签名
        .begin("{\n") // 开始方法体
        .list("return " + methodName + "(\n", ",\n", ");\n", bridgeArgs) // 添加return语句
        .end("}\n"); // 结束方法体
    }

    writer.end("}\n"); // 结束匿名类
  }

  // 判断抽象方法的返回类型是否为原始类型
  private boolean isAbstractMethodPrimitive() { // 私有方法，判断是否为原始类型
    Method method = getAbstractMethod(); // 获取抽象方法
    return Primitive.is(method.getReturnType()); // 检查返回类型是否为原始类型
  }

  // 获取抽象方法的名称
  private String getAbstractMethodName() { // 私有方法，获取抽象方法名
    final Method abstractMethod = getAbstractMethod(); // 获取抽象方法
    return abstractMethod.getName(); // 返回方法名
  }

  // 获取函数接口中的抽象方法
  private Method getAbstractMethod() { // 私有方法，获取抽象方法
    if (type instanceof Class // 如果type是Class对象
        && ((Class) type).isInterface()) { // 并且是接口
      final List<Method> declaredMethods = // 获取接口中声明的所有方法
          Lists.newArrayList(((Class) type).getDeclaredMethods()); 
      declaredMethods.removeIf(m -> (m.getModifiers() & 0x00001000) != 0); // 移除合成方法（编译器生成的方法）
      if (declaredMethods.size() == 1) { // 如果只剩下一个方法
        return declaredMethods.get(0); // 返回这个方法（应该是抽象方法）
      }
    }
    throw new IllegalStateException("Method not found, type = " + type); // 如果找不到方法，抛出异常
  }

  // 判断两个FunctionExpression是否相等
  @Override public boolean equals(@Nullable Object o) { // 重写equals方法
    if (this == o) { // 如果是同一个对象
      return true; // 返回true
    }
    if (o == null || getClass() != o.getClass()) { // 如果为null或类型不同
      return false; // 返回false
    }
    if (!super.equals(o)) { // 如果父类equals返回false
      return false; // 返回false
    }

    FunctionExpression that = (FunctionExpression) o; // 强制转换为FunctionExpression
    return Objects.equals(body, that.body) // 比较body是否相等
        && Objects.equals(function, that.function) // 比较function是否相等
        && parameterList.equals(that.parameterList); // 比较parameterList是否相等
  }

  // 计算FunctionExpression的哈希码
  @Override public int hashCode() { // 重写hashCode方法
    int result = hash; // 获取缓存的哈希码
    if (result == 0) { // 如果哈希码还未计算
      result = Objects.hash(nodeType, type, function, body, parameterList); // 计算哈希码
      if (result == 0) { // 如果计算结果为0
        result = 1; // 则设置为1（避免0与未计算混淆）
      }
      hash = result; // 缓存哈希码
    }
    return result; // 返回哈希码
  }

  /** Function that can be invoked with a variable number of arguments. */
  // 可变参数调用的函数接口
  public interface Invokable { // 定义可调用接口
    @Nullable Object dynamicInvoke(@Nullable Object... args); // 动态调用方法，接受可变参数，返回可空对象
  }
}
