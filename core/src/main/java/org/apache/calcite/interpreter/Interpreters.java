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
 */ // Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.interpreter; // 声明当前类所在的包，属于org.apache.calcite.interpreter包，该包包含解释器相关的功能

import org.apache.calcite.DataContext; // 导入DataContext类，提供执行查询时所需的数据上下文信息（如数据源、变量等）
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，表示可枚举的数据集合，是LINQ4J框架的核心接口，用于处理查询结果
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式节点，是Calcite中所有关系操作符的基类
import org.apache.calcite.runtime.ArrayBindable; // 导入ArrayBindable接口，表示可以绑定到数据上下文并返回Object数组集合的可绑定对象

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值，帮助进行空值检查

/**
 * Utilities relating to {@link org.apache.calcite.interpreter.Interpreter}
 * and {@link org.apache.calcite.interpreter.InterpretableConvention}.
 */ // 类的JavaDoc注释：说明这是与Interpreter（解释器）和InterpretableConvention（可解释约定）相关的工具类
// Interpreter是Calcite中用于直接解释执行关系代数表达式的引擎
// InterpretableConvention是一种调用约定，表示关系节点可以通过解释器方式执行
// 这个工具类提供了创建解释器绑定对象的工厂方法
public class Interpreters { // 定义Interpreters工具类，所有方法都是静态的，不需要实例化
  private Interpreters() {} // 私有构造方法，防止类被实例化，因为这是一个纯工具类，所有方法都是静态的

  /** Creates a {@link org.apache.calcite.runtime.Bindable} that interprets a
   * given relational expression. */ // 方法的JavaDoc注释：创建一个可绑定对象，该对象可以解释执行给定的关系表达式
  // 这个方法将关系代数表达式（RelNode）包装成一个ArrayBindable对象
  // ArrayBindable是Calcite中用于执行查询的接口，可以绑定到数据上下文并返回结果集
  public static ArrayBindable bindable(final RelNode rel) { // 定义静态工厂方法bindable，接收一个关系代数节点作为参数，返回一个ArrayBindable对象
    // final修饰符表示参数在方法内部不会被修改
    if (rel instanceof ArrayBindable) { // 检查传入的关系节点是否已经实现了ArrayBindable接口
      // E.g. if rel instanceof BindableRel // 例如，如果rel是BindableRel的实例（BindableRel是实现了ArrayBindable的接口）
      return (ArrayBindable) rel; // 如果rel已经是ArrayBindable类型，直接返回它，避免重复包装
    } // 结束if语句块
    return new ArrayBindable() { // 如果rel不是ArrayBindable类型，创建一个匿名内部类实现ArrayBindable接口
      // 这个匿名内部类将使用解释器来执行关系表达式
      @Override public Enumerable<@Nullable Object[]> bind(DataContext dataContext) { // 实现ArrayBindable接口的bind方法，接收数据上下文参数，返回可枚举的对象数组集合
        // @Override注解表示这是重写接口的方法
        // @Nullable注解表示Object数组可能包含null值
        // bind方法负责将关系表达式绑定到具体的数据上下文并开始执行
        return new Interpreter(dataContext, rel); // 创建一个新的Interpreter实例，传入数据上下文和关系节点
        // Interpreter类会解释执行关系代数表达式，并返回结果集作为Enumerable
        // 解释器会遍历关系树，逐个执行每个节点的操作（如扫描、过滤、投影、连接等）
      } // 结束bind方法

      @Override public Class<Object[]> getElementType() { // 实现ArrayBindable接口的getElementType方法，返回结果元素的类型
        return Object[].class; // 返回Object[].class，表示结果集的每一行都是一个Object数组
        // Object数组可以存储不同类型的值，对应SQL查询结果中的各个列
      } // 结束getElementType方法
    }; // 结束匿名内部类定义并返回该实例
  } // 结束bindable方法
} // 结束Interpreters类定义
