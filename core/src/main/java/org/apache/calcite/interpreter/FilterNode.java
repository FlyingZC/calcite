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
package org.apache.calcite.interpreter; // 声明包名,该类位于org.apache.calcite.interpreter包中,属于Calcite的解释器模块

import org.apache.calcite.rel.core.Filter; // 导入Filter类,这是Calcite中表示过滤操作的RelNode(关系表达式节点)

import com.google.common.collect.ImmutableList; // 导入Google Guava库的ImmutableList,用于创建不可变的列表

/**
 * Interpreter node that implements a
 * {@link org.apache.calcite.rel.core.Filter}.
 * 解释器节点,实现了Calcite的Filter关系操作
 * FilterNode是Calcite解释器模式中用于执行过滤操作的节点
 * 它负责对输入的数据行应用过滤条件,只保留满足条件的行
 * 解释器模式是Calcite执行SQL的一种方式,通过直接解释执行RelNode树来实现查询
 */
public class FilterNode extends AbstractSingleNode<Filter> { // FilterNode继承自AbstractSingleNode,泛型参数为Filter,表示这是一个处理Filter关系的单子节点
  // condition成员变量:存储编译后的过滤条件表达式
  // Scalar是Calcite中用于表示标量表达式的接口,可以被执行并返回结果
  // final关键字表示该变量在构造后不可修改,确保过滤条件在运行时不会被改变
  private final Scalar condition; // 过滤条件,是一个编译后的标量表达式,用于判断每一行数据是否满足过滤条件
  
  // context成员变量:存储执行上下文
  // Context是Calcite解释器执行时的上下文环境,包含了执行表达式所需的环境信息
  // 例如变量值、函数调用等运行时信息
  // final关键字表示该变量在构造后不可修改
  private final Context context; // 执行上下文,用于在执行过滤条件表达式时提供必要的运行时环境

  // FilterNode的构造方法,用于创建一个FilterNode实例
  // 参数compiler:编译器对象,负责将RelNode编译为可执行的代码
  // 参数rel:Filter关系节点,包含过滤条件和行类型等信息
  public FilterNode(Compiler compiler, Filter rel) { // 构造方法,初始化FilterNode实例
    super(compiler, rel); // 调用父类AbstractSingleNode的构造方法,初始化source(输入源)和sink(输出目标)等基础信息
    // 编译过滤条件表达式
    // compiler.compile()方法将RelNode中的条件表达式编译为可执行的Scalar对象
    // ImmutableList.of(rel.getCondition())将过滤条件封装成不可变列表
    // rel.getRowType()获取输入行的类型信息,用于编译时类型检查
    this.condition = // 将编译结果赋值给condition成员变量
        compiler.compile(ImmutableList.of(rel.getCondition()), // 调用编译器的compile方法,编译过滤条件表达式
            rel.getRowType()); // 传入行类型信息,用于类型推断和验证
    // 创建执行上下文
    // compiler.createContext()创建一个新的Context对象
    // 该Context对象将在执行过滤条件时使用,用于存储变量值等运行时信息
    this.context = compiler.createContext(); // 调用编译器的createContext方法,创建执行上下文并赋值给context成员变量
  }

  // run方法:执行过滤操作的核心方法
  // 该方法从source(输入源)接收数据行,对每一行应用过滤条件,将满足条件的行发送到sink(输出目标)
  // throws InterruptedException:表示该方法可能被中断,支持线程中断机制
  @Override public void run() throws InterruptedException { // 重写父类的run方法,实现过滤逻辑
    Row row; // 声明一个Row类型的变量,用于存储从输入源接收到的数据行
    // 循环从source接收数据行,直到source返回null表示没有更多数据
    // source.receive()从输入源获取下一行数据,如果返回null表示数据流结束
    // 这个循环实现了数据流的逐行处理
    while ((row = source.receive()) != null) { // 当从输入源接收到的行不为null时,继续循环处理
      // 将当前行的值设置到执行上下文中
      // row.getValues()获取当前行的所有字段值
      // context.values用于存储当前行的值,供过滤条件表达式访问
      context.values = row.getValues(); // 将当前行的字段值赋值给上下文的values属性,使过滤条件表达式可以访问这些值
      // 执行过滤条件表达式,判断当前行是否满足条件
      // condition.execute(context)在给定的上下文中执行过滤条件表达式
      // 返回值转换为Boolean类型,表示过滤条件的计算结果
      // 如果条件为true,表示该行应该被保留;如果为false或null,表示该行应该被过滤掉
      Boolean b = (Boolean) condition.execute(context); // 执行过滤条件表达式,获取布尔结果
      // 检查过滤条件的结果
      // b != null:确保结果不为null(null表示条件无法确定,通常视为false)
      // b:确保结果为true(只有true才保留该行)
      if (b != null && b) { // 如果过滤条件结果为true(非null且为布尔值true)
        // 将满足条件的行发送到输出目标
        // sink.send(row)将当前行发送到下游节点
        // 这样实现了数据流的传递,只有满足条件的行才会继续向下传递
        sink.send(row); // 调用输出目标的send方法,将当前行发送到下游
      } // 结束if语句,不满足条件的行会被自动丢弃,不会发送到下游
    } // 结束while循环,所有数据行处理完毕
  } // 结束run方法
} // 结束FilterNode类定义
