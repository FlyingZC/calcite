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
package org.apache.calcite.rel.metadata;  // 定义元数据处理器提供者接口所在的包，属于Calcite的关系表达式元数据模块

import org.apache.calcite.rel.RelNode;  // 导入RelNode类，Calcite中所有关系表达式节点的基类，用于表示查询计划中的操作
import org.apache.calcite.util.ControlFlowException;  // 导入控制流异常类，用于非异常情况下的控制流转移，不影响正常异常处理

/**
 * Provides {@link MetadataHandler} call sites for
 * {@link RelMetadataQuery}. The handlers provided are responsible for
 * updating the cache stored in {@link RelMetadataQuery}.
 * 元数据处理器提供者接口，为RelMetadataQuery提供MetadataHandler的调用点
 * 提供的处理器负责更新存储在RelMetadataQuery中的缓存
 * 
 * 核心作用：
 * 1. 作为元数据处理器的工厂接口，用于获取特定类型的元数据处理器
 * 2. 支持元数据处理器的动态生成和更新（通过revise方法）
 * 3. 在Calcite的元数据查询机制中起到桥梁作用，连接RelMetadataQuery和具体的元数据处理器实现
 * 4. 实现了元数据处理的延迟加载和按需生成策略，提高查询优化效率
 * 
 * 使用场景：
 * - 在查询优化过程中，当需要获取某个RelNode的元数据（如行数、选择性、列统计信息等）时
 * - 通过RelMetadataQuery来访问元数据，而RelMetadataQuery通过此接口获取具体的处理器
 * - 处理器负责实际计算或缓存查询结果
 * 
 * 设计模式：
 * - 工厂模式：提供创建MetadataHandler实例的接口
 * - 策略模式：不同的元数据类型对应不同的处理策略
 * - 缓存策略：通过RelMetadataQuery缓存计算结果，避免重复计算
 */
public interface MetadataHandlerProvider {  // 定义元数据处理器提供者接口，是Calcite元数据系统的核心接口之一

  /**
   * Provide a handler for the requested metadata class.
   * 为请求的元数据类提供处理器
   *
   * @param handlerClass The handler interface expected 期望的处理器接口类型，指定需要哪种元数据处理器
   * @param <MH> The metadata type the handler relates to. 处理器相关的元数据类型泛型参数，继承自MetadataHandler
   * @return The handler implementation. 返回具体的处理器实现对象，用于处理特定类型的元数据查询
   * 
   * 方法作用：
   * - 这是工厂方法的核心，根据传入的处理器接口类型返回对应的处理器实现
   * - 支持泛型设计，可以处理任意继承自MetadataHandler的处理器类型
   * - 返回的处理器会被RelMetadataQuery用于实际的元数据计算和缓存管理
   * 
   * 使用示例：
   * - handler(RowCountMetadataHandler.class) 返回行数元数据处理器
   * - handler(CollationMetadataHandler.class) 返回排序规则元数据处理器
   * - handler(DistinctMetadataHandler.class) 返回去重元数据处理器
   * 
   * 实现要点：
   * - 实现类需要维护一个处理器缓存或工厂映射
   * - 支持懒加载，只在第一次请求时创建处理器
   * - 返回的处理器必须是线程安全的（因为可能在多线程环境下使用）
   */
  <MH extends MetadataHandler<?>> MH handler(Class<MH> handlerClass);  // 根据处理器类类型获取对应的元数据处理器实例

  /** Re-generates the handler for a given kind of metadata.  */
  /**
   * Revise the handler for a given kind of metadata.
   * 修订给定类型的元数据处理器
   *
   * <p>Should be invoked if the existing handler throws a {@link NoHandler} exception.
   * 如果现有处理器抛出NoHandler异常，应该调用此方法
   *
   * @param handlerClass The type of class to revise. 需要修订的处理器类类型
   * @param <MH> The type metadata the handler provides. 处理器提供的元数据类型泛型参数
   * @return A new handler that should be used instead of any previous handler provided. 返回新的处理器实例，替代之前提供的处理器
   * 
   * 方法作用：
   * - 当现有处理器无法处理某个RelNode时（抛出NoHandler异常），调用此方法重新生成处理器
   * - 支持动态处理器生成，可以根据新的RelNode类型生成适配的处理器
   * - 提供了处理器更新的机制，使得元数据处理系统具有自适应能力
   * 
   * 使用场景：
   * - 当遇到新的RelNode子类型，现有处理器不支持时
   * - 当需要为特定RelNode类型生成专门优化的处理器时
   * - 在运行时根据RelNode的实际类型动态调整处理策略
   * 
   * 实现要点：
   * - 默认实现抛出UnsupportedOperationException，表示不支持处理器修订
   * - 具体实现类可以重写此方法，支持动态处理器生成
   * - 修订后的处理器应该能够处理之前失败的RelNode类型
   * - 可能涉及代码生成或动态代理技术
   * 
   * 注意事项：
   * - 这是一个默认方法，不是所有实现都必须支持
   * - 调用此方法意味着之前的处理器失效，需要使用新返回的处理器
   * - 修订操作可能比较耗时，应该谨慎使用
   */
  default <MH extends MetadataHandler<?>> MH revise(Class<MH> handlerClass) {  // 默认实现：修订元数据处理器，默认不支持
    throw new UnsupportedOperationException("This provider doesn't support handler revision.");  // 抛出不支持操作异常，表示当前提供者不支持处理器修订功能
  }  // revise方法结束

  /** Exception that indicates there there should be a handler for
   * this class but there is not. The action is probably to
   * re-generate the handler class. */
  /**
   * NoHandler异常类，表示应该存在某个RelNode类的处理器但实际上不存在
   * 通常的解决方法是重新生成处理器类
   * 
   * 异常作用：
   * - 作为控制流异常，用于指示处理器缺失的情况
   * - 继承自ControlFlowException，表示这不是一个真正的错误，而是控制流的一部分
   * - 触发处理器重新生成机制，使系统能够自适应新的RelNode类型
   * 
   * 使用场景：
   * - 当某个RelNode类型没有对应的元数据处理器时抛出
   * - 当处理器无法处理特定类型的RelNode时抛出
   * - 作为触发revise方法调用的信号
   * 
   * 设计意图：
   * - 不是真正的异常，而是一种控制流机制
   * - 允许系统在运行时动态扩展处理器能力
   * - 避免在编译时需要知道所有可能的RelNode类型
   */
  class NoHandler extends ControlFlowException {  // 定义NoHandler内部类，继承自控制流异常，用于表示处理器缺失的情况
    public final Class<? extends RelNode> relClass;  // 成员变量：存储缺失处理器的RelNode类类型，用于标识是哪个RelNode类型缺少处理器

    /**
     * NoHandler构造方法，创建一个NoHandler异常实例
     * 
     * @param relClass 缺少处理器的RelNode类类型，用于标识哪个RelNode类型没有对应的元数据处理器
     * 
     * 构造方法作用：
     * - 初始化异常对象，记录缺失处理器的RelNode类型
     * - 保存relClass信息，供上层调用者使用，以便生成对应的处理器
     * 
     * 使用示例：
     * - throw new NoHandler(MyCustomRelNode.class) 表示MyCustomRelNode类型缺少处理器
     * - 捕获此异常后，可以根据relClass信息调用revise方法生成新处理器
     */
    public NoHandler(Class<? extends RelNode> relClass) {  // 构造方法：接收RelNode类类型作为参数
      this.relClass = relClass;  // 将传入的RelNode类类型保存到成员变量中，用于后续处理
    }  // 构造方法结束
  }  // NoHandler内部类结束

}  // MetadataHandlerProvider接口结束
