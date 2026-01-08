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
// Apache许可证声明，版权归属Apache软件基金会

package org.apache.calcite.adapter.enumerable.impl; // 声明包名：位于Calcite的可枚举适配器实现包中，该包提供了可枚举适配器的具体实现类

import org.apache.calcite.adapter.enumerable.RexToLixTranslator; // 导入RexToLixTranslator类：用于将Calcite的关系表达式(RexNode)转换为Linq4j的表达式(Expression)，这是代码生成过程的关键转换器
import org.apache.calcite.adapter.enumerable.WinAggAddContext; // 导入WinAggAddContext接口：窗口聚合累加上下文接口，定义了窗口聚合函数在累加阶段所需的所有上下文信息
import org.apache.calcite.adapter.enumerable.WinAggFrameResultContext; // 导入WinAggFrameResultContext接口：窗口帧结果上下文接口，提供了访问窗口帧内数据的方法
import org.apache.calcite.adapter.enumerable.WinAggImplementor; // 导入WinAggImplementor接口：窗口聚合实现器接口，定义了窗口聚合函数的实现规范
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入BlockBuilder类：代码块构建器，用于构建Java代码块，是Linq4j代码生成工具的核心组件
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression类：表达式类，表示Linq4j中的表达式树节点，用于构建Java代码表达式
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions工具类：提供了创建各种类型表达式的静态工厂方法，如常量、方法调用、变量引用等

import java.util.List; // 导入List接口：Java集合框架的列表接口，用于存储有序的元素集合
import java.util.function.Function; // 导入Function接口：Java 8的函数式接口，表示接受一个参数并产生结果的函数

/**
 * Implementation of
 * {@link org.apache.calcite.adapter.enumerable.WinAggAddContext}.
 */
// 类的JavaDoc文档注释，说明该类的作用：
// 1. 这是WinAggAddContext接口的实现类
// 2. WinAggAddContext是窗口聚合函数累加阶段的上下文接口
// 3. 该类继承自WinAggResultContextImpl，复用了窗口聚合结果上下文的实现
// 4. 该类是抽象类，需要子类实现特定的窗口聚合逻辑
// 5. 该类提供了窗口聚合函数累加操作所需的上下文信息，包括行翻译器、参数访问等

public abstract class WinAggAddContextImpl extends WinAggResultContextImpl // 定义WinAggAddContextImpl抽象类，继承自WinAggResultContextImpl，表示窗口聚合累加上下文的实现
    implements WinAggAddContext { // 实现WinAggAddContext接口，承诺提供窗口聚合累加所需的所有方法

  /**
   * Creates window aggregate add context.
   *
   * @param block code block that will contain the added initialization
   * @param accumulator accumulator variables that store the intermediate
   *                    aggregate state
   * @param frame window frame context
   */
  // 构造方法的JavaDoc文档注释，说明构造方法的参数：
  // 1. block：代码块构建器，用于包含生成的初始化代码
  // 2. accumulator：累加器变量列表，用于存储聚合函数的中间状态，例如SUM累加器、COUNT计数器等
  // 3. frame：窗口帧上下文构建函数，用于创建访问窗口帧数据的上下文对象

  protected WinAggAddContextImpl(BlockBuilder block, List<Expression> accumulator, // 构造方法声明：protected修饰，允许子类访问，接收代码块构建器、累加器列表和窗口帧上下文构建函数作为参数
      Function<BlockBuilder, WinAggFrameResultContext> frame) { // 参数frame是一个函数，接收BlockBuilder并返回WinAggFrameResultContext，用于延迟创建窗口帧结果上下文
    super(block, accumulator, frame); // 调用父类WinAggResultContextImpl的构造方法，将参数传递给父类进行初始化，父类会存储这些参数以供后续使用
  } // 构造方法结束

  @SuppressWarnings("Guava") // 注解：抑制Guava相关的警告，因为下面的构造方法使用了Guava的Function接口，这是为了兼容性

  @Deprecated // to be removed before 2.0 // 注解：标记为已弃用，计划在Calcite 2.0版本之前移除
  // 弃用原因：Guava的com.google.common.base.Function接口已被Java 8的java.util.function.Function取代

  protected WinAggAddContextImpl(BlockBuilder block, List<Expression> accumulator, // 构造方法声明：protected修饰，这是向后兼容的构造方法，使用Guava的Function接口
      com.google.common.base.Function<BlockBuilder, WinAggFrameResultContext> frame) { // 参数frame使用Guava的Function接口，这是为了保持与旧版本的兼容性
    this(block, accumulator, // 调用当前类的主构造方法，传递block和accumulator参数
        (Function<BlockBuilder, WinAggFrameResultContext>) frame); // 将Guava的Function强制转换为Java 8的Function接口，然后传递给主构造方法
  } // 构造方法结束

  /**
   * Returns a {@link RexToLixTranslator} for the row at the current
   * position in the aggregation loop.
   */
  // 方法的JavaDoc文档注释，说明该方法的作用：
  // 1. 返回一个RexToLixTranslator对象，用于在聚合循环的当前位置翻译关系表达式
  // 2. 该翻译器会将RexNode（Calcite的逻辑表达式）转换为Linq4j的Expression（Java代码表达式）
  // 3. 转换后的表达式可以直接用于生成可执行的Java代码
  // 4. 这是实现窗口聚合函数的关键步骤，因为它允许在生成的代码中访问当前行的列值

  @Override public final RexToLixTranslator rowTranslator() { // 方法声明：public final修饰，覆盖父类或接口的rowTranslator方法，final表示不能被子类重写
    return rowTranslator( // 调用父类的rowTranslator方法，传入一个行索引参数，该方法会返回针对指定行的翻译器
        computeIndex(Expressions.constant(0), // 调用computeIndex方法计算行索引，传入常量0作为偏移量，表示从当前聚合位置开始
            WinAggImplementor.SeekType.AGG_INDEX)); // 传入SeekType.AGG_INDEX作为定位类型，表示使用聚合索引作为基准点进行定位
  } // 方法结束，返回针对当前聚合位置的行翻译器

  /**
   * Returns the arguments of the aggregate function for the row at the
   * current position in the aggregation loop.
   */
  // 方法的JavaDoc文档注释，说明该方法的作用：
  // 1. 返回聚合函数在当前聚合位置的参数列表
  // 2. 这些参数是Linq4j的表达式列表，每个表达式对应聚合函数的一个参数
  // 3. 例如：对于SUM(salary)函数，返回的结果是包含salary列当前值的表达式
  // 4. 对于COUNT(*)函数，返回的结果是包含常量1的表达式
  // 5. 这些表达式可以直接用于生成累加操作的Java代码

  @Override public final List<Expression> arguments() { // 方法声明：public final修饰，覆盖WinAggAddContext接口的arguments方法，final表示不能被子类重写
    return rowTranslator().translateList(rexArguments()); // 调用rowTranslator()方法获取当前行的翻译器，然后调用translateList方法将RexNode参数列表转换为Linq4j表达式列表
  } // 方法结束，返回聚合函数参数的表达式列表
} // 类定义结束
