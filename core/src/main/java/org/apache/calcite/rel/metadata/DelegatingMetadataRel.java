/*
 * Licensed to the Apache Software Foundation (ASF) under one or more  // Apache软件基金会许可证声明，说明该代码遵循Apache 2.0许可证
 * contributor license agreements.  See the NOTICE file distributed with  // 贡献者许可协议，NOTICE文件包含额外的版权信息
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF根据Apache 2.0许可证授权您使用此文件
 * (the "License"); you may not use this file except in compliance with  // "许可证"）；除非符合许可证，否则您不得使用此文件
 * the License.  You may obtain a copy of the License at  // 您可以在以下位置获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // Apache许可证的官方网址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意，否则软件
 * distributed under the License is distributed on an "AS IS" BASIS,  // 按原样分发，不提供任何明示或暗示的担保
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 不提供任何形式的担保或条件，无论是明示的还是暗示的
 * See the License for the specific language governing permissions and  // 请参阅许可证以了解管理权限和
 * limitations under the License.  // 许可证下的限制的语言
 */
package org.apache.calcite.rel.metadata;  // 声明此接口属于org.apache.calcite.rel.metadata包，这是Calcite中处理关系表达式元数据的核心包

import org.apache.calcite.rel.RelNode;  // 导入RelNode类，这是Calcite中所有关系表达式节点的基类，代表查询计划中的一个操作节点

/**
 * Interface for {@link RelNode} where the metadata is derived from another node.  // 这是一个RelNode接口，其元数据是从另一个节点派生而来的
 * 详细说明：DelegatingMetadataRel是一个标记接口，用于标识那些元数据委托给其他RelNode处理的RelNode实现类。
 * 在Calcite的查询优化过程中，元数据（如行数、列统计信息、成本等）对于优化器做出正确的决策至关重要。
 * 有些RelNode节点本身不直接维护元数据，而是将其元数据请求委托给底层的输入节点或其他相关节点。
 * 实现此接口的类需要提供getMetadataDelegateRel()方法，返回实际负责提供元数据的RelNode。
 * 这种设计模式允许在查询计划中创建包装节点或代理节点，这些节点可以修改查询行为但复用底层节点的元数据。
 * 
 * 典型使用场景：
 * 1. 当一个RelNode只是另一个RelNode的包装或装饰器时，可以委托元数据请求
 * 2. 在某些优化规则中创建的临时节点，其元数据应该与原节点保持一致
 * 3. 当需要在不改变元数据的情况下修改查询计划结构时
 * 
 * 实现此接口的类示例：
 * - 一些特殊的RelNode实现，如Filter、Project等，可能选择委托部分元数据
 * - 自定义的RelNode包装器，用于添加额外的逻辑但保持元数据不变
 * 
 * 注意事项：
 * - 实现类必须确保返回的委托节点与当前节点在语义上相关
 * - 委托元数据可以提高性能，避免重复计算
 * - 但在某些情况下，委托元数据可能不准确，需要谨慎使用
 */
public interface DelegatingMetadataRel {  // 定义DelegatingMetadataRel接口，这是一个公共接口，任何RelNode都可以实现它来表明其元数据是委托的
  RelNode getMetadataDelegateRel();  // 抽象方法，要求实现类返回实际负责提供元数据的RelNode节点
  // 方法详细说明：
  // - 返回值：RelNode对象，表示当前节点的元数据应该从哪个节点获取
  // - 作用：当Calcite的元数据系统需要获取当前节点的元数据时，会调用此方法找到真正的元数据提供者
  // - 实现要求：返回的节点应该是当前节点的输入节点或与其语义相关的节点
  // - 使用场景：在RelMetadataQuery查询元数据时，如果发现RelNode实现了此接口，会使用返回的委托节点来获取元数据
  // - 性能优化：通过委托，可以避免重复计算相同的元数据，提高查询优化器的效率
  // - 注意：返回的节点不能为null，否则会导致元数据查询失败
}
