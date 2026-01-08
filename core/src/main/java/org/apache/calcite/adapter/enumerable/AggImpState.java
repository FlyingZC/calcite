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
// 声明包名:org.apache.calcite.adapter.enumerable,表示这个类属于Calcite框架中的可枚举适配器包
package org.apache.calcite.adapter.enumerable;

// 导入Expression类,用于表示LINQ4J表达式树中的表达式节点
import org.apache.calcite.linq4j.tree.Expression;
// 导入AggregateCall类,表示SQL中的聚合函数调用(如SUM、COUNT、AVG等)
import org.apache.calcite.rel.core.AggregateCall;

// 导入MonotonicNonNull注解,表示该字段初始为null,但一旦被赋值后就不会再变回null
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

// 导入List接口,用于存储表达式列表
import java.util.List;

/**
 * Represents internal state when implementing aggregate functions.
 * 表示实现聚合函数时的内部状态对象
 * 
 * 这个类是Calcite框架中用于跟踪聚合函数实现状态的核心类
 * 在将SQL聚合操作转换为可执行的Java代码时,每个聚合函数调用都会对应一个AggImpState实例
 * 它保存了从SQL解析到代码生成过程中的所有必要信息
 * 
 * 主要作用:
 * 1. 存储聚合函数的调用信息(如聚合类型、参数等)
 * 2. 维护代码生成过程中的中间状态(如累加器、结果表达式等)
 * 3. 关联具体的聚合函数实现器(AggImplementor)
 * 4. 支持窗口聚合和普通聚合两种上下文
 * 
 * 使用场景:
 * - 在EnumerableAggregateRel实现聚合操作时创建
 * - 在代码生成过程中逐步填充各个字段
 * - 通过AggImplementor生成最终的聚合实现代码
 */
public class AggImpState {
  // 聚合函数在聚合调用列表中的索引位置
  // 用于标识当前聚合函数是第几个聚合调用,在多个聚合函数并列时(如SELECT SUM(a), COUNT(b) FROM t)
  // aggIdx=0表示SUM(a),aggIdx=1表示COUNT(b)
  // 这个索引在生成代码时用于引用正确的聚合结果
  public final int aggIdx;
  
  // 聚合函数调用对象,包含聚合函数的完整信息
  // AggregateCall是Calcite中表示SQL聚合函数调用的核心类
  // 包含信息:聚合函数类型(SUM/COUNT/AVG/MAX/MIN等)、参数列表、是否distinct、过滤条件等
  // 例如:SUM(DISTINCT salary) OVER (PARTITION BY dept_id) 会产生一个AggregateCall对象
  public final AggregateCall call;
  
  // 聚合函数实现器,负责生成具体的聚合实现代码
  // AggImplementor是接口,每个聚合函数类型都有一个对应的实现器
  // 例如:SumImplementor、CountImplementor、AvgImplementor等
  // 实现器知道如何为特定的聚合函数生成Java代码,包括:
  // - 初始化累加器
  // - 处理每条输入记录
  // - 计算最终结果
  public final AggImplementor implementor;
  
  // 聚合上下文对象,提供聚合实现过程中需要的各种信息
  // @MonotonicNonNull表示该字段初始为null,赋值后不再为null
  // AggContext包含:类型系统、RelNode树信息、实现方式等上下文数据
  // 在聚合实现器的实现方法中被初始化,用于访问运行时信息
  public @MonotonicNonNull AggContext context;
  
  // 聚合结果表达式,表示聚合计算的最终结果
  // @MonotonicNonNull表示该字段初始为null,赋值后不再为null
  // Expression是LINQ4J表达式树的节点,代表一段可执行的代码片段
  // 例如:对于SUM(a),result可能是一个访问累加器最终值的表达式
  // 在代码生成完成后,这个表达式会被插入到最终生成的代码中
  public @MonotonicNonNull Expression result;
  
  // 聚合状态列表,存储聚合计算过程中的中间状态变量
  // @MonotonicNonNull表示该字段初始为null,赋值后不再为null
  // List<Expression>表示可能有多个状态变量(如AVG需要存储sum和count两个状态)
  // 每个Expression代表一个状态变量的访问表达式
  // 例如:AVG的实现需要两个状态:sum(累加和)和count(计数)
  // state列表中的表达式对应这些状态变量的引用
  public @MonotonicNonNull List<Expression> state;
  
  // 累加器添加器表达式,用于将新值添加到累加器的代码片段
  // @MonotonicNonNull表示该字段初始为null,赋值后不再为null
  // Expression表示一段代码,通常是一个方法调用或表达式语句
  // 例如:对于SUM,accumulatorAdder可能是类似"sum += value"的表达式
  // 在处理每条输入记录时,会执行这个表达式来更新累加器
  // 这个表达式在AggImplementor的implementAdd方法中生成
  public @MonotonicNonNull Expression accumulatorAdder;

  // 构造方法:创建AggImpState实例
  // 参数aggIdx:聚合函数在聚合列表中的索引位置
  // 参数call:聚合函数调用对象,包含聚合函数的完整定义
  // 参数windowContext:是否在窗口上下文中使用(窗口函数vs普通聚合)
  public AggImpState(int aggIdx, AggregateCall call, boolean windowContext) {
    // 保存聚合索引位置,用于后续引用
    this.aggIdx = aggIdx;
    // 保存聚合调用对象,包含聚合函数的所有信息
    this.call = call;
    // 从RexImpTable中获取对应的聚合实现器
    // RexImpTable是Calcite中注册所有聚合函数实现器的注册表
    // get方法根据聚合函数类型和窗口上下文返回合适的实现器
    // call.getAggregation()返回聚合函数类型(如SqlStdOperatorTable.SUM)
    // windowContext参数决定是使用窗口聚合实现器还是普通聚合实现器
    AggImplementor implementor = RexImpTable.INSTANCE.get(call.getAggregation(), windowContext);
    // 如果找不到对应的实现器,抛出异常
    // 这可能发生在:聚合函数未注册、不支持的聚合类型、窗口上下文不匹配等情况
    if (implementor == null) {
      throw new IllegalArgumentException(
          "Unable to get aggregate implementation for aggregate "  // 错误信息前缀
          + call.getAggregation()  // 拼接具体的聚合函数名称
          + (windowContext ? " in window context" : ""));  // 如果是窗口上下文,添加提示信息
    }
    // 保存获取到的聚合实现器,用于后续生成代码
    this.implementor = implementor;
  }

  // 重写toString方法,返回对象的字符串表示
  // 用于调试和日志输出,显示AggImpState的关键信息
  @Override public String toString() {
    // 返回格式化的字符串,包含聚合索引、调用信息和实现器信息
    // 格式:AggImpState{aggIdx=0, call=SUM($1), implementor=SumImplementor}
    return "AggImpState{aggIdx=" + aggIdx + ", call=" + call  // 拼接索引和调用信息
        + ", implementor=" + implementor + "}";  // 拼接实现器信息并闭合字符串
  }
}
