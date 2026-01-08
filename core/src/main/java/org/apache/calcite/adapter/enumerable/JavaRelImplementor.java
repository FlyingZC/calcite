/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，查看随此工作分发的NOTICE文件以获取
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的其他信息
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache许可证2.0版授权您使用此文件
 * (the "License"); you may not use this file except in compliance with // ("许可证")；除非遵守许可证，否则您不得使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下位置获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // Apache许可证2.0的URL地址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则根据许可证分发的软件
 * distributed under the License is distributed on an "AS IS" BASIS, // 是按"原样"基础分发的，不附带任何明示或暗示的担保或条件
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 无论是明示的还是暗示的
 * See the License for the specific language governing permissions and // 请参阅许可证以了解许可证下的特定语言管理权限和
 * limitations under the License. // 限制
 */
package org.apache.calcite.adapter.enumerable; // 声明包名，表示该类属于org.apache.calcite.adapter.enumerable包

import org.apache.calcite.DataContext; // 导入DataContext类，Calcite的数据上下文接口，用于在执行时传递运行时信息
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入JavaTypeFactory接口，Java类型工厂，用于创建和管理Java类型
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入ParameterExpression类，LINQ4J树中的参数表达式，表示方法参数
import org.apache.calcite.plan.RelImplementor; // 导入RelImplementor接口，关系表达式实现器接口，定义了实现关系操作的契约
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder类，行表达式构建器，用于构建行表达式（RexNode）

/**
 * Abstract base class for implementations of {@link RelImplementor} // {@link RelImplementor}实现的抽象基类
 * that generate java code. // 用于生成Java代码的RelImplementor实现
 * // 这个类是所有生成Java代码的关系表达式实现器的抽象基类
 * // 它提供了生成Java代码所需的基础设施和通用方法
 * // 子类需要实现具体的代码生成逻辑
 */
public abstract class JavaRelImplementor implements RelImplementor { // 声明抽象类JavaRelImplementor，实现RelImplementor接口
  private final RexBuilder rexBuilder; // 成员变量：RexBuilder实例，用于构建行表达式（RexNode），final表示不可变

  protected JavaRelImplementor(RexBuilder rexBuilder) { // 构造方法：受保护的构造函数，接收RexBuilder参数
    this.rexBuilder = rexBuilder; // 将传入的rexBuilder参数赋值给成员变量rexBuilder
    assert rexBuilder.getTypeFactory() instanceof JavaTypeFactory // 断言：确保rexBuilder的类型工厂是JavaTypeFactory的实例
        : "Type factory of rexBuilder should be a JavaTypeFactory"; // 如果断言失败，抛出错误提示信息
  }

  public RexBuilder getRexBuilder() { // 公共方法：获取RexBuilder实例
    return rexBuilder; // 返回成员变量rexBuilder
  }

  public JavaTypeFactory getTypeFactory() { // 公共方法：获取JavaTypeFactory类型工厂
    return (JavaTypeFactory) rexBuilder.getTypeFactory(); // 从rexBuilder获取类型工厂并强制转换为JavaTypeFactory类型返回
  }

  /**
   * Returns the expression used to access // 返回用于访问的表达式
   * {@link org.apache.calcite.DataContext}. // {@link org.apache.calcite.DataContext}数据上下文
   * // 这个方法返回一个参数表达式，用于在生成的Java代码中访问DataContext
   * // DataContext是Calcite在执行查询时传递运行时信息（如参数、表数据源等）的接口
   * // 返回的ROOT表达式是一个特殊的参数表达式，代表DataContext的根对象
   *
   * @return expression used to access {@link org.apache.calcite.DataContext}. // 用于访问DataContext的表达式
   */
  public ParameterExpression getRootExpression() { // 公共方法：获取根表达式（DataContext的访问表达式）
    return DataContext.ROOT; // 返回DataContext.ROOT，这是一个预定义的ParameterExpression，表示DataContext的根参数
  }
}
