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
// Apache许可证头部声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.linq4j.tree; // 声明包名，该类属于org.apache.calcite.linq4j.tree包，是LINQ4J表达式树的一部分

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的空值注解，用于标记可能为null的参数

import java.util.Objects; // 导入Java工具类Objects，用于equals和hashCode等通用方法

/**
 * Represents a catch statement in a try block.
 * 表示try块中的catch语句，用于捕获和处理异常
 * 在LINQ4J的表达式树中，CatchBlock是构建异常处理逻辑的基本单元
 * 它对应Java语法中的catch子句，包含异常参数和异常处理体
 */
public class CatchBlock { // CatchBlock类定义，表示catch语句块的抽象语法树节点
  public final ParameterExpression parameter; // 成员变量：异常参数表达式，表示catch块中捕获的异常参数（如catch(Exception e)中的e），使用final修饰表示不可变
  public final Statement body; // 成员变量：catch块的处理体语句，表示捕获异常后执行的代码块，使用final修饰表示不可变

  public CatchBlock(ParameterExpression parameter, // 构造方法：创建CatchBlock实例，parameter参数表示异常参数表达式
      Statement body) { // body参数表示catch块的处理体语句
    this.parameter = parameter; // 将传入的参数表达式赋值给成员变量parameter
    this.body = body; // 将传入的处理体语句赋值给成员变量body
  } // 构造方法结束，完成CatchBlock对象的初始化

  @Override public boolean equals(@Nullable Object o) { // 重写equals方法，用于比较两个CatchBlock对象是否相等，@Nullable注解表示参数o可能为null
    if (this == o) { // 首先检查引用是否相同，如果当前对象和参数o是同一个对象引用
      return true; // 直接返回true，同一个对象肯定相等
    } // 引用相同检查结束
    if (o == null || getClass() != o.getClass()) { // 检查参数o是否为null，或者两者的类类型是否不同
      return false; // 如果o为null或者类型不同，返回false，不相等
    } // null和类型检查结束

    CatchBlock that = (CatchBlock) o; // 将参数o强制转换为CatchBlock类型，赋值给局部变量that，用于后续比较

    if (body != null ? !body.equals(that.body) : that.body != null) { // 比较body成员变量：先判断当前body是否为null，如果不为null则调用equals方法比较，如果为null则判断that.body是否为null
      return false; // 如果body不相等（一个为null一个不为null，或者equals返回false），返回false
    } // body比较结束
    if (parameter != null ? !parameter.equals(that.parameter) : that // 比较parameter成员变量：先判断当前parameter是否为null，如果不为null则调用equals方法比较，如果为null则判断that.parameter是否为null
        .parameter != null) { // 继续判断that.parameter是否为null（续行）
      return false; // 如果parameter不相等（一个为null一个不为null，或者equals返回false），返回false
    } // parameter比较结束

    return true; // 所有比较都通过，返回true，表示两个CatchBlock对象相等
  } // equals方法结束

  @Override public int hashCode() { // 重写hashCode方法，用于计算CatchBlock对象的哈希值，与equals方法配套使用
    return Objects.hash(parameter, body); // 使用Objects.hash工具方法，基于parameter和body成员变量计算哈希值，确保相等的对象有相同的哈希码
  } // hashCode方法结束
} // CatchBlock类定义结束
