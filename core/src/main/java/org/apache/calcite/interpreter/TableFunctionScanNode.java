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
package org.apache.calcite.interpreter; // 声明包名，该类属于interpreter包，用于解释器模式下的节点实现

import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，用于表示可枚举的数据集合，这是LINQ4J库的核心接口
import org.apache.calcite.linq4j.Enumerator; // 导入Enumerator接口，用于遍历Enumerable集合中的元素
import org.apache.calcite.linq4j.function.Function1; // 导入Function1函数式接口，表示接受一个参数并返回一个结果的函数
import org.apache.calcite.rel.core.TableFunctionScan; // 导入TableFunctionScan关系表达式，表示表函数扫描操作
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型，描述表的结构
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示行表达式的函数调用
import org.apache.calcite.rex.RexNode; // 导入RexNode接口，表示行表达式节点，是所有行表达式的基类
import org.apache.calcite.schema.Function; // 导入Function接口，表示Calcite中的函数定义
import org.apache.calcite.schema.impl.TableFunctionImpl; // 导入TableFunctionImpl类，表示表函数的具体实现
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator接口，表示SQL操作符
import org.apache.calcite.sql.validate.SqlUserDefinedTableFunction; // 导入SqlUserDefinedTableFunction类，表示用户定义的表函数

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList，用于创建不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值

/**
 * Interpreter node that implements a
 * {@link TableFunctionScan}.
 * 解释器节点，用于实现表函数扫描操作
 * TableFunctionScanNode是Calcite解释器模式中的一个节点，专门负责执行表函数扫描
 * 表函数是一种特殊的函数，它返回一个结果集（表），而不是单个值
 * 例如：UNNEST、GENERATE_SERIES等都是表函数的典型例子
 * 该类通过调用表函数并处理返回的结果集，将结果发送到下游节点
 */
public class TableFunctionScanNode implements Node { // 定义TableFunctionScanNode类，实现Node接口，表示解释器中的一个可执行节点
  private final Scalar scalar; // 标量表达式编译器，用于编译和执行表函数调用表达式，Scalar接口封装了可执行的表达式
  private final Context context; // 执行上下文，保存执行过程中需要的环境信息，如变量、参数等
  private final Sink sink; // 结果接收器，用于将执行结果发送到下游节点，Sink接口定义了数据流动的出口
  private final Function1<?, Row> mapFn; // 映射函数，用于将表函数返回的结果转换为Row对象，支持单列和多列两种情况

  private TableFunctionScanNode(Compiler compiler, TableFunctionScan rel) { // 私有构造方法，通过create工厂方法创建实例，compiler是编译器，rel是表函数扫描关系表达式
    final RelDataType rowType = rel.getRowType(); // 获取表函数返回的行类型，描述结果集的结构（列名、列类型等）
    this.scalar = compiler.compile(ImmutableList.of(rel.getCall()), rowType); // 编译表函数调用表达式，生成可执行的Scalar对象，ImmutableList.of确保不可变
    this.context = compiler.createContext(); // 创建执行上下文，用于存储执行过程中的变量和状态
    this.sink = compiler.sink(rel); // 创建结果接收器，用于将结果发送到下游，根据关系表达式配置sink
    if (rowType.getFieldCount() == 1 // 判断结果集是否只有一列，单列情况下使用不同的映射策略
        && rel.getElementType() != Object[].class) { // 并且元素类型不是Object数组，说明是单值而非数组
      this.mapFn = (Function1<Object, Row>) Row::of; // 单列情况：使用Row::of方法将单个对象包装成Row对象，Row::of是方法引用
    } else { // 多列情况或数组元素情况
      this.mapFn = (Function1<@Nullable Object[], Row>) Row::asCopy; // 多列情况：使用Row::asCopy方法将对象数组转换为Row对象，创建行的副本
    }
  }

  @Override public void run() throws InterruptedException { // 重写Node接口的run方法，执行表函数扫描操作，可能抛出中断异常
    final Object o = scalar.execute(context); // 执行标量表达式，调用表函数并获取返回结果，结果通常是Enumerable集合
    if (o instanceof Enumerable) { // 检查返回结果是否为Enumerable类型，确保可以进行枚举操作
      for (@SuppressWarnings({"unchecked", "rawtypes"}) // 抑制未检查类型转换和原始类型使用警告，因为泛型类型擦除
           final Enumerator<Row> enumerator = // 创建行枚举器，用于遍历结果集
           ((Enumerable) o).select(mapFn).enumerator(); // 将表函数返回的Enumerable通过mapFn映射转换为Row的Enumerable，然后获取枚举器
           enumerator.moveNext();) { // 遍历枚举器，moveNext()移动到下一个元素，返回false表示遍历结束
        sink.send(enumerator.current()); // 将当前行数据发送到下游节点，current()获取当前行的数据
      }
    }
  }

  /** Creates a TableFunctionScanNode.
   *  创建TableFunctionScanNode实例的工厂方法
   *  该方法会验证表函数是否支持解释器执行，只有TableFunctionImpl类型的表函数才能被解释器执行
   */
  static TableFunctionScanNode create(Compiler compiler, TableFunctionScan rel) { // 静态工厂方法，创建TableFunctionScanNode实例
    RexNode call = rel.getCall(); // 获取表函数调用的行表达式节点，RexNode是所有行表达式的基类
    if (call instanceof RexCall) { // 检查调用节点是否为RexCall类型，即函数调用表达式
      SqlOperator operator = ((RexCall) call).getOperator(); // 获取SQL操作符，即表函数的操作符对象
      if (operator instanceof SqlUserDefinedTableFunction) { // 检查操作符是否为用户定义的表函数
        Function function = ((SqlUserDefinedTableFunction) operator).function; // 获取表函数的底层函数实现
        if (function instanceof TableFunctionImpl) { // 检查函数是否为TableFunctionImpl类型，这是解释器可以执行的表函数实现
          return new TableFunctionScanNode(compiler, rel); // 符合条件，创建并返回TableFunctionScanNode实例
        }
      }
    }
    throw new AssertionError("cannot convert table function scan " // 如果不符合条件，抛出断言错误，表示无法转换为可执行的枚举形式
        + rel.getCall() + " to enumerable"); // 错误信息包含表函数调用，帮助调试
  }
}
