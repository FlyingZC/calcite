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
package org.apache.calcite.plan; // 声明包名,该类位于org.apache.calcite.plan包下,属于Calcite优化器核心包

import org.apache.calcite.schema.Wrapper; // 导入Wrapper接口,Context继承该接口以获得类型转换能力

/**
 * Provides library users a way to store data within the planner session and
 * access it within rules. Frameworks can implement their own implementation
 * of Context and pass that as part of the FrameworkConfig.
 * // 为库用户提供一种在优化器会话中存储数据并在规则中访问的方式。框架可以实现自己的Context实现,并将其作为FrameworkConfig的一部分传递。
 *
 * <p>Simply implement the {@link #unwrap} method to return any sub-objects
 * that you wish to provide.
 * // 只需实现unwrap方法来返回你希望提供的任何子对象。
 *
 * 【类作用详解】:
 * Context接口是Calcite优化器框架中的一个核心接口,它提供了在优化过程中传递和访问上下文信息的机制。
 *
 * 1. **数据传递机制**: 
 *    - 允许用户在优化器会话中存储自定义数据
 *    - 这些数据可以在优化规则(RelOptRule)中被访问和使用
 *    - 实现了优化器组件之间的解耦,规则可以通过Context获取所需信息而无需直接依赖具体实现
 *
 * 2. **扩展性设计**:
 *    - 框架使用者可以实现自己的Context实现类
 *    - 通过FrameworkConfig将自定义Context注入到优化器中
 *    - 支持多种上下文信息的传递,如配置信息、元数据、用户定义的数据等
 *
 * 3. **类型安全转换**:
 *    - 继承自Wrapper接口,提供了类型安全的转换机制
 *    - 通过unwrap方法可以获取Context内部封装的具体对象
 *    - 避免了强制类型转换带来的ClassCastException风险
 *
 * 4. **典型使用场景**:
 *    - 传递优化器配置参数
 *    - 存储表统计信息
 *    - 传递用户定义的优化提示(hints)
 *    - 在规则之间共享中间结果
 *    - 传递连接池、数据源等运行时资源
 *
 * 5. **设计模式**:
 *    - 采用了Wrapper包装器模式,提供了灵活的对象访问方式
 *    - 接口设计简洁,只要求实现unwrap方法
 *    - 支持多层嵌套的上下文信息传递
 *
 * 【与Wrapper接口的关系】:
 * - Context继承自Wrapper,Wrapper接口定义了unwrap方法
 * - unwrap方法允许将Context对象转换为其内部封装的指定类型对象
 * - 这种设计使得Context可以包装多种类型的对象,同时保持类型安全
 * - 例如:一个Context可能包装了StatisticsProvider,通过unwrap可以获取该对象
 *
 * 【实现示例】:
 * ```java
 * public class MyContext implements Context {
 *     private final Map<String, Object> data;
 *     
 *     public MyContext() {
 *         this.data = new HashMap<>();
 *     }
 *     
 *     public void put(String key, Object value) {
 *         data.put(key, value);
 *     }
 *     
 *     public <T> T unwrap(Class<T> clazz) {
 *         // 检查是否为Context本身
 *         if (clazz.isInstance(this)) {
 *             return clazz.cast(this);
 *         }
 *         // 检查data中的值
 *         for (Object value : data.values()) {
 *             if (clazz.isInstance(value)) {
 *                 return clazz.cast(value);
 *             }
 *         }
 *         return null;
 *     }
 * }
 * ```
 */
public interface Context extends Wrapper { // 定义Context接口,继承Wrapper接口以获得类型转换能力;这是一个标记接口,主要作用是约定上下文传递的标准
} // 接口定义结束,Context接口本身不定义任何方法,所有方法都继承自Wrapper接口
