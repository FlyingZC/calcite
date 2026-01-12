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
package org.apache.calcite.rel.metadata.janino; // 声明包名，该类属于Calcite的Janino元数据处理器包，Janino是一个轻量级的Java编译器，用于运行时动态编译代码

// GeneratedMetadata_CumulativeCostHandler类：这是Calcite框架中自动生成的元数据处理器类，专门用于处理累积成本（CumulativeCost）元数据
// 该类实现了BuiltInMetadata.CumulativeCost.Handler接口，提供了计算关系节点累积成本的能力
// 累积成本是指从查询计划根节点到当前节点的所有操作成本总和，是查询优化器评估执行计划性能的重要指标
// 该类使用缓存机制避免重复计算，并支持循环依赖检测，确保元数据计算的效率和正确性
public final class GeneratedMetadata_CumulativeCostHandler // 声明一个final类，表示该类不能被继承，这是自动生成的代码通常采用的设计模式
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.CumulativeCost.Handler { // 实现CumulativeCost.Handler接口，该接口定义了获取累积成本的方法签名

  // methodKey0：用于缓存的方法键对象，类型为Object，实际是DescriptiveCacheKey实例
  // 该键用于在元数据缓存中唯一标识getCumulativeCost方法的计算结果
  // 使用缓存可以避免对同一个关系节点重复计算累积成本，显著提升查询优化器的性能
  // DescriptiveCacheKey包含描述性信息，便于调试和问题排查
  private final Object methodKey0 = // 声明私有的final成员变量，final表示该变量初始化后不可修改
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("RelOptCost Handler.getCumulativeCost()"); // 创建DescriptiveCacheKey对象，传入描述性字符串，标识这是getCumulativeCost方法的缓存键

  // provider0：元数据提供者对象，类型为RelMdPercentageOriginalRows$RelMdCumulativeCost
  // 该对象是实际执行累积成本计算的提供者，包含了针对不同类型关系节点的具体计算逻辑
  // GeneratedMetadata_CumulativeCostHandler作为代理，通过调用provider0的方法来获取实际的计算结果
  // 这种设计将缓存逻辑与计算逻辑分离，提高了代码的可维护性
  public final org.apache.calcite.rel.metadata.RelMdPercentageOriginalRows$RelMdCumulativeCost provider0; // 声明公共的final成员变量，final确保引用不可变

  // GeneratedMetadata_CumulativeCostHandler构造方法：初始化元数据处理器
  // 参数provider0：元数据提供者对象，用于实际执行累积成本计算
  // 该构造方法接收一个RelMdPercentageOriginalRows$RelMdCumulativeCost实例，并将其保存到成员变量中
  // 这是依赖注入模式的体现，使得处理器可以灵活地使用不同的提供者实现
  public GeneratedMetadata_CumulativeCostHandler( // 构造方法声明，接收一个RelMdPercentageOriginalRows$RelMdCumulativeCost类型的参数
      org.apache.calcite.rel.metadata.RelMdPercentageOriginalRows$RelMdCumulativeCost provider0) { // 参数声明，provider0是元数据提供者对象
    this.provider0 = provider0; // 将传入的provider0参数赋值给成员变量provider0，保存引用供后续方法使用
  }

  // getDef方法：获取元数据定义对象
  // 返回值：MetadataDef对象，描述了该元数据的定义信息，包括名称、方法签名等
  // 该方法直接委托给provider0.getDef()，因为元数据定义信息由提供者维护
  // MetadataDef用于元数据系统的注册和查找，是元数据框架的重要组成部分
  public org.apache.calcite.rel.metadata.MetadataDef getDef() { // 方法声明，返回MetadataDef类型
    return provider0.getDef(); // 调用provider0的getDef方法，返回元数据定义对象
  }

  // getCumulativeCost方法：获取关系节点的累积成本（公共入口方法）
  // 参数r：要计算累积成本的关系节点（RelNode）
  // 参数mq：元数据查询对象（RelMetadataQuery），用于访问缓存和触发其他元数据计算
  // 返回值：RelOptCost对象，表示累积成本，如果无法计算则返回null
  // 该方法实现了缓存机制、循环依赖检测和委托计算三个核心功能
  // 缓存机制：通过methodKey0作为键在mq.map中缓存计算结果，避免重复计算
  // 循环依赖检测：使用NullSentinel.ACTIVE标记正在计算的状态，检测到循环时抛出异常
  // 委托计算：实际计算委托给getCumulativeCost_私有方法执行
  public org.apache.calcite.plan.RelOptCost getCumulativeCost( // 方法声明，返回RelOptCost类型
      org.apache.calcite.rel.metadata.RelNode r, // 参数r：关系节点对象
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq：元数据查询对象

    // 处理委托元数据关系：如果r是DelegatingMetadataRel的实例，需要获取其实际委托的关系节点
    // DelegatingMetadataRel是一个包装器，用于在不修改原始关系节点的情况下添加额外的元数据处理逻辑
    // 通过while循环可以处理多层嵌套的委托关系，确保最终获取到真正的关系节点
    // 这种设计允许在查询计划中插入元数据相关的代理节点，而不影响原始计划结构
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) { // while循环：当r是DelegatingMetadataRel实例时继续循环
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 将r更新为委托关系节点，通过getMetadataDelegateRel方法获取实际的关系节点
    }

    // key：缓存键对象，用于在缓存中查找或存储计算结果
    // 这里直接使用methodKey0作为键，确保同一个方法的计算结果使用相同的缓存键
    final Object key; // 声明final变量key，表示缓存键
    key = methodKey0; // 将methodKey0赋值给key，准备用于缓存操作

    // v：从缓存中获取的值，可能为null、ACTIVE标记、INSTANCE标记或实际的RelOptCost对象
    // mq.map是一个映射表，以关系节点和缓存键为组合键，存储对应的元数据值
    // 使用缓存可以显著提升性能，特别是对于复杂的查询计划，避免重复计算相同的元数据
    final Object v = mq.map.get(r, key); // 从缓存map中获取关系节点r和方法键key对应的值，赋值给v

    // 检查缓存值是否不为null，表示缓存命中
    if (v != null) { // if条件：如果缓存值v不为null
      // 检查是否为ACTIVE标记，表示当前元数据正在计算中，检测到循环依赖
      // NullSentinel.ACTIVE是一个特殊对象，用于标记元数据正在计算的状态
      // 当出现循环依赖时，说明A依赖B，B又依赖A，形成死循环，必须抛出异常中断计算
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // if条件：如果v是ACTIVE标记，表示检测到循环依赖
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出循环元数据异常，终止计算并通知调用者存在循环依赖
      }

      // 检查是否为INSTANCE标记，表示该元数据的计算结果为null
      // NullSentinel.INSTANCE是一个特殊的单例对象，用于在缓存中表示null值
      // 因为Java的Map不允许存储null值作为特殊标记，所以使用单例对象来代表null
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // if条件：如果v是INSTANCE标记，表示计算结果为null
        return null; // 返回null，表示该关系节点的累积成本无法计算或不存在
      }

      // 缓存命中且值为实际的RelOptCost对象，直接返回缓存的结果
      // 使用强制类型转换将Object转换为RelOptCost，因为缓存中存储的是Object类型
      // 这样避免了重复计算，直接使用之前计算并缓存的结果，提升性能
      return (org.apache.calcite.plan.RelOptCost) v; // 强制转换并返回RelOptCost对象，即缓存的累积成本结果
    }

    // 缓存未命中，在开始计算前先将当前标记设置为ACTIVE
    // 这样可以检测循环依赖：如果在计算getCumulativeCost的过程中又触发了getCumulativeCost，就会检测到ACTIVE标记
    // mq.map.put是缓存写入操作，使用关系节点和方法键作为组合键
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 在缓存中设置ACTIVE标记，表示开始计算

    // 使用try-catch块进行异常处理
    // try块中执行实际的计算逻辑，catch块中处理异常情况
    // 如果计算过程中发生异常，需要清理缓存中的ACTIVE标记，避免影响后续计算
    try { // try块开始
      // 调用getCumulativeCost_私有方法执行实际的累积成本计算
      // getCumulativeCost_方法包含具体的类型判断和计算逻辑
      // x变量存储计算得到的RelOptCost结果
      final org.apache.calcite.plan.RelOptCost x = getCumulativeCost_(r, mq); // 调用私有方法getCumulativeCost_执行实际计算，结果赋值给x

      // 将计算结果存入缓存，使用mask方法处理null值
      // NullSentinel.mask方法会将null转换为INSTANCE单例对象，非null值保持不变
      // 这样确保缓存中始终存储非null值，便于后续判断
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果x存入缓存，使用mask处理null值

      // 返回计算得到的累积成本
      return x; // 返回计算结果x
    } catch (java.lang.Exception e) { // catch块：捕获所有异常
      // 发生异常时，清理该关系节点的所有缓存条目
      // mq.map.row(r).clear()会清除关系节点r的所有缓存数据，避免脏数据影响后续计算
      // 这是一种异常恢复机制，确保缓存的一致性
      mq.map.row(r).clear(); // 清理关系节点r的所有缓存条目

      // 重新抛出异常，让上层调用者处理
      // 使用throw e保持原始异常信息，包括堆栈跟踪
      throw e; // 重新抛出捕获的异常
    }
  }

  // getCumulativeCost_方法：实际执行累积成本计算的私有方法
  // 参数r：要计算累积成本的关系节点（RelNode）
  // 参数mq：元数据查询对象（RelMetadataQuery），用于访问缓存和触发其他元数据计算
  // 返回值：RelOptCost对象，表示累积成本
  // 该方法根据关系节点的具体类型，调用provider0中对应的重载方法进行计算
  // 使用if-else链进行类型判断，按照从具体到一般的顺序检查类型
  // 这是典型的多态分发模式，通过类型判断调用不同的计算逻辑
  private org.apache.calcite.plan.RelOptCost getCumulativeCost_( // 方法声明，私有方法，返回RelOptCost类型
      org.apache.calcite.rel.metadata.RelNode r, // 参数r：关系节点对象
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq：元数据查询对象

    // 检查关系节点是否为EnumerableInterpreter类型
    // EnumerableInterpreter是Calcite中的一种特殊节点，用于将可枚举的关系解释为可执行代码
    // 这是第一个检查的类型，因为它是具体的子类，需要优先匹配
    if (r instanceof org.apache.calcite.adapter.enumerable.EnumerableInterpreter) { // if条件：检查r是否是EnumerableInterpreter实例
      // 调用provider0的getCumulativeCost方法，传入EnumerableInterpreter类型的具体参数
      // provider0是RelMdPercentageOriginalRows$RelMdCumulativeCost实例，包含针对EnumerableInterpreter的专门计算逻辑
      // 强制类型转换确保调用正确的方法重载版本
      return provider0.getCumulativeCost((org.apache.calcite.adapter.enumerable.EnumerableInterpreter) r, mq); // 调用provider0的getCumulativeCost方法，传入EnumerableInterpreter类型参数，返回计算结果
    } else if (r instanceof org.apache.calcite.rel.RelNode) { // else if：检查r是否是RelNode实例（这是所有关系节点的基类，作为兜底处理）

      // 调用provider0的getCumulativeCost方法，传入RelNode类型的参数
      // 这是一个通用的处理方法，适用于所有RelNode及其子类
      // 当前面的具体类型检查都不匹配时，使用这个通用方法
      // 强制类型转换确保调用正确的方法重载版本
      return provider0.getCumulativeCost((org.apache.calcite.rel.RelNode) r, mq); // 调用provider0的getCumulativeCost方法，传入RelNode类型参数，返回计算结果
    } else { // else：如果r不属于任何已知的类型

      // 抛出IllegalArgumentException异常，表示没有找到对应的处理器
      // 异常消息详细说明了缺失的处理器信息，包括方法签名和实际类型
      // 这有助于开发者在添加新的关系节点类型时发现需要添加对应的处理器
      // 消息建议创建一个catch-all（RelNode）处理器来处理通用情况
      throw new java.lang.IllegalArgumentException("No handler for method [public abstract org.apache.calcite.plan.RelOptCost org.apache.calcite.rel.metadata.BuiltInMetadata$CumulativeCost$Handler.getCumulativeCost(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出异常，提示缺少处理器
    }
  }

} // 类定义结束