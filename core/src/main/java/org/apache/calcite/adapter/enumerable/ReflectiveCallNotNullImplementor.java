/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache许可证声明,用于版权保护,说明此代码由ASF授权使用
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议,详见NOTICE文件了解版权信息
 * this work for additional information regarding copyright ownership.  // 关于版权所有者的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache 2.0许可证授权给您使用此文件
 * (the "License"); you may not use this file except in compliance with // (许可证);除非遵守许可证,否则您不能使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // Apache 2.0许可证的URL地址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意,否则
 * distributed under the License is distributed on an "AS IS" BASIS, // 根据许可证分发的软件按"原样"基础分发
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不提供任何形式的保证或条件,无论明示还是暗示
 * See the License for the specific language governing permissions and // 请参阅许可证了解特定语言的许可和
 * limitations under the License. // 许可证下的限制
 */
package org.apache.calcite.adapter.enumerable; // 包声明,此类属于Calcite的enumerable适配器包,用于处理可枚举的查询执行

import org.apache.calcite.linq4j.tree.Expression; // 导入LINQ4j的Expression类,用于表示表达式树中的表达式节点
import org.apache.calcite.linq4j.tree.Expressions; // 导入LINQ4j的Expressions工具类,用于创建各种表达式
import org.apache.calcite.rex.RexCall; // 导入RexCall类,表示Calcite的关系表达式(Relational Expression)调用

import java.lang.reflect.Method; // 导入Java反射的Method类,用于表示方法对象
import java.util.List; // 导入Java集合框架的List接口,用于存储有序的元素列表

import static org.apache.calcite.util.ReflectUtil.isStatic; // 静态导入ReflectUtil的isStatic方法,用于判断方法是否为静态方法

/**
 * Implementation of // 这是NotNullImplementor接口的实现类
 * {@link org.apache.calcite.adapter.enumerable.NotNullImplementor} // 该接口用于实现非空值的操作
 * that calls a given {@link java.lang.reflect.Method}. // 通过调用给定的Java反射方法来实现
 *
 * <p>When method is not static, a new instance of the required class is // 当方法不是静态方法时,会创建所需类的新实例
 * created. // 用于实例方法调用
 */ // 类的JavaDoc注释结束
public class ReflectiveCallNotNullImplementor implements NotNullImplementor { // 类定义:反射调用非空实现器,实现了NotNullImplementor接口,用于通过反射调用方法来实现Calcite操作
  protected final Method method; // 成员变量:存储要调用的Java反射方法对象,使用protected修饰符允许子类访问,final修饰符表示该方法引用不可改变

  /**
   * Constructor of {@link ReflectiveCallNotNullImplementor}. // 构造方法的JavaDoc注释:ReflectiveCallNotNullImplementor类的构造函数
   *
   * @param method Method that is used to implement the call // 参数说明:method参数是用于实现调用的Java反射方法对象
   */ // 构造方法注释结束
  public ReflectiveCallNotNullImplementor(Method method) { // 构造方法定义:接收一个Method对象参数,用于初始化此实现器
    this.method = method; // 将传入的方法引用赋值给成员变量method,保存要调用的方法
  } // 构造方法结束

  @Override public Expression implement(RexToLixTranslator translator, // 实现NotNullImplementor接口的implement方法,将Rex表达式转换为LINQ表达式,使用@Override注解表示重写接口方法
      RexCall call, List<Expression> translatedOperands) { // 参数列表:translator是Rex到LINQ的转换器,call是Rex调用表达式,translatedOperands是已转换的操作数表达式列表
    translatedOperands = // 开始处理操作数,将操作数转换为适合目标方法参数的类型
        EnumUtils.fromInternal(method.getParameterTypes(), translatedOperands); // 使用EnumUtils工具类将内部表示的操作数转换为Java类型,根据目标方法的参数类型进行转换
    translatedOperands = // 再次处理操作数,确保类型兼容性
        EnumUtils.convertAssignableTypes(method.getParameterTypes(), translatedOperands); // 使用EnumUtils将操作数转换为可赋值给目标方法参数的类型,处理类型转换和兼容性
    final Expression callExpr; // 声明最终要生成的调用表达式变量,final修饰符表示该变量只能赋值一次
    if (isStatic(method)) { // 判断目标方法是否为静态方法,使用ReflectUtil的isStatic工具方法
      callExpr = Expressions.call(method, translatedOperands); // 如果是静态方法,直接调用Expressions.call创建静态方法调用表达式,传入方法对象和转换后的操作数
    } else { // 如果不是静态方法(即实例方法)
      final Expression target = // 声明目标对象表达式,用于实例方法调用
          translator.functionInstance(call, method); // 使用转换器获取方法所属类的实例表达式,translator会创建或获取函数实例
      callExpr = Expressions.call(target, method, translatedOperands); // 创建实例方法调用表达式,传入目标对象、方法对象和转换后的操作数
    } // if-else块结束,已完成方法调用表达式的创建
    if (!containsCheckedException(method)) { // 检查方法是否声明了受检异常(Checked Exception),使用containsCheckedException私有方法判断
      return callExpr; // 如果没有受检异常,直接返回方法调用表达式,不需要异常处理包装
    } // if块结束
    return translator.handleMethodCheckedExceptions(callExpr); // 如果有受检异常,使用转换器的handleMethodCheckedExceptions方法包装调用表达式,添加异常处理逻辑
  } // implement方法结束

  private static boolean containsCheckedException(Method method) { // 私有静态方法:检查方法是否声明了受检异常,参数为要检查的方法对象
    Class[] exceptions = method.getExceptionTypes(); // 获取方法声明的所有异常类型,返回Class数组
    if (exceptions == null || exceptions.length == 0) { // 如果异常数组为空或长度为0,表示方法没有声明任何异常
      return false; // 返回false,表示没有受检异常
    } // if块结束
    for (Class clazz : exceptions) { // 遍历所有声明的异常类型
      if (!RuntimeException.class.isAssignableFrom(clazz)) { // 检查异常类型是否不是RuntimeException的子类,如果不是RuntimeException子类,则是受检异常
        return true; // 返回true,表示方法包含受检异常
      } // if块结束
    } // for循环结束
    return false; // 所有异常都是RuntimeException或其子类,返回false,表示没有受检异常
  } // containsCheckedException方法结束
} // 类定义结束
