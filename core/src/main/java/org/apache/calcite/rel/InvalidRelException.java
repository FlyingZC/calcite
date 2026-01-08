/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明，表明该代码遵循Apache 2.0许可证
 * contributor license agreements.  See the NOTICE file distributed with // 参与者许可协议，详见分发的NOTICE文件以获取版权信息
 * this work for additional information regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache 2.0许可证授权您使用此文件
 * (the "License"); you may not use this file except in compliance with // 您只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // 许可证的网络地址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则
 * distributed under the License is distributed on an "AS IS" BASIS, // 根据许可证分发的软件是基于"原样"基础的
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不提供任何明示或暗示的担保或条件
 * See the License for the specific language governing permissions and // 请参阅许可证以了解具体的权限和
 * limitations under the License. // 使用限制
 */
package org.apache.calcite.rel; // 声明该类属于org.apache.calcite.rel包，这是Calcite关系表达式（Relational Expression）的核心包

/**
 * Exception that indicates that a relational expression would be invalid // 异常类，表示在给定参数下关系表达式（Relational Expression）将是无效的
 * with given parameters. // 当使用特定参数创建关系表达式时，如果参数不符合要求，就会抛出此异常
 *
 * <p>This exception is thrown by the constructor of a subclass of // 此异常由RelNode子类的构造函数抛出
 * {@link RelNode} when given parameters it cannot accept. For example, // 当构造函数接收到无法接受的参数时抛出。例如：
 * {@code EnumerableJoinRel} can only implement equi-joins, so its constructor // EnumerableJoinRel只能实现等值连接（equi-joins），因此其构造函数
 * throws {@code InvalidRelException} when given the condition // 当遇到类似input0.x - input1.y = 2这样的非等值连接条件时，会抛出InvalidRelException
 * {@code input0.x - input1.y = 2}.
 *
 * <p>Because the exception is checked (i.e. extends {@link Exception} but not // 由于这是一个受检异常（即继承Exception而非RuntimeException）
 * {@link RuntimeException}), constructors that throw this exception will // 抛出此异常的构造函数必须在throws子句中声明该异常
 * declare this exception in their {@code throws} clause, and rules that create // 而创建这些关系表达式的规则（Rule）需要处理该异常
 * those relational expressions will need to handle it. Usually a rule will // 通常规则不会亲自处理异常，而是会匹配失败
 * not take the exception personally, and will fail to match. The burden of // 这样就将检查的负担从规则中移除，意味着规则编写者需要维护的代码更少
 * checking is removed from the rule, which means less code for the author of
 * the rule to maintain.
 *
 * <p>The caller that receives an {@code InvalidRelException} (typically a rule // 接收到InvalidRelException的调用者（通常是尝试创建关系表达式的规则）
 * attempting to create a relational expression) should log it at // 应该在DEBUG级别记录该异常，以便调试时查看详细信息
 * the DEBUG level.
 */
public class InvalidRelException extends Exception { // 定义InvalidRelException类，继承自Exception，表示这是一个受检异常，专门用于关系表达式验证失败的场景
  /**
   * Creates an InvalidRelException. // 构造函数说明：创建一个InvalidRelException异常实例
   */
  public InvalidRelException(String message) { // 构造函数：接收一个错误消息字符串，用于描述关系表达式无效的原因
    super(message); // 调用父类Exception的构造函数，传入错误消息，将消息存储到异常对象中
  }

  /**
   * Creates an InvalidRelException with a cause. // 构造函数说明：创建一个带有原因的InvalidRelException异常实例
   */
  public InvalidRelException(String message, Throwable cause) { // 构造函数：接收错误消息字符串和原因Throwable对象，用于描述关系表达式无效的原因和根本原因
    super(message, cause); // 调用父类Exception的构造函数，传入错误消息和原因，将两者都存储到异常对象中，便于追踪异常链
  }
}
