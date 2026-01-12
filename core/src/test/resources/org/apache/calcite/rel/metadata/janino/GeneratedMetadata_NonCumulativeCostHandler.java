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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.rel.metadata.janino; // 声明包名，该类属于org.apache.calcite.rel.metadata.janino包

// GeneratedMetadata_NonCumulativeCostHandler类：这是Calcite元数据处理器生成的类，用于处理NonCumulativeCost（非累积成本）元数据
// 该类实现了BuiltInMetadata.NonCumulativeCost.Handler接口，是Calcite优化器中用于计算关系节点非累积成本的核心处理器
// 非累积成本是指不包含子节点成本的独立成本，主要用于某些特定的优化场景
// 该类通过Janino在运行时动态生成，提供了缓存机制以避免重复计算
public final class GeneratedMetadata_NonCumulativeCostHandler // 定义一个final类，不能被继承
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.NonCumulativeCost.Handler { // 实现NonCumulativeCost.Handler接口，提供获取非累积成本的功能
  private final Object methodKey0 = // 定义一个私有的final成员变量，用于作为缓存键，标识getNonCumulativeCost方法
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("RelOptCost Handler.getNonCumulativeCost()"); // 创建一个描述性缓存键对象，键值为方法签名字符串，用于在缓存中唯一标识这个元数据方法
  public final org.apache.calcite.rel.metadata.RelMdPercentageOriginalRows$RelMdNonCumulativeCost provider0; // 定义一个公共的final成员变量，provider0是实际提供非累积成本计算逻辑的实现类，RelMdPercentageOriginalRows$RelMdNonCumulativeCost是内部类
  public GeneratedMetadata_NonCumulativeCostHandler( // 构造方法：创建NonCumulativeCostHandler实例
      org.apache.calcite.rel.metadata.RelMdPercentageOriginalRows$RelMdNonCumulativeCost provider0) { // 构造方法参数：provider0是实际提供非累积成本计算的提供者对象
    this.provider0 = provider0; // 将传入的provider0参数赋值给成员变量provider0，保存引用以便后续调用
  }
  public org.apache.calcite.rel.metadata.MetadataDef getDef() { // getDef方法：获取元数据定义，返回MetadataDef对象
    return provider0.getDef(); // 委托给provider0对象获取元数据定义，返回其定义信息
  }
  public org.apache.calcite.plan.RelOptCost getNonCumulativeCost( // getNonCumulativeCost方法：获取指定关系节点的非累积成本，这是对外公开的入口方法，包含缓存逻辑
      org.apache.calcite.rel.RelNode r, // 参数r：要计算非累积成本的关系节点（RelNode）
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq：元数据查询对象，用于访问元数据缓存和其他元数据
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) { // 如果r是DelegatingMetadataRel类型（委托元数据关系节点），则循环处理
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 获取实际的委托关系节点，跳过代理节点，找到真正的目标节点
    }
    final Object key; // 声明缓存键变量
    key = methodKey0; // 将methodKey0赋值给key，用于在缓存中查找
    final Object v = mq.map.get(r, key); // 从元数据缓存map中获取r节点对应的key的缓存值
    if (v != null) { // 如果缓存值不为空，说明已经计算过
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // 如果缓存值是ACTIVE标记，表示当前正在计算中（检测到循环依赖）
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出循环元数据异常，防止无限递归
      }
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // 如果缓存值是INSTANCE标记，表示计算结果为null
        return null; // 返回null
      }
      return (org.apache.calcite.plan.RelOptCost) v; // 否则返回缓存中的RelOptCost对象
    }
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 在缓存中放入ACTIVE标记，表示开始计算，用于检测循环依赖
    try { // 开始try块，用于捕获异常并清理缓存
      final org.apache.calcite.plan.RelOptCost x = getNonCumulativeCost_(r, mq); // 调用实际的计算方法getNonCumulativeCost_获取非累积成本
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果x放入缓存，使用mask方法处理null值
      return x; // 返回计算结果
    } catch (java.lang.Exception e) { // 捕获所有异常
      mq.map.row(r).clear(); // 清理该节点r的所有缓存，避免留下不一致的状态
      throw e; // 重新抛出异常
    }
  }

  private org.apache.calcite.plan.RelOptCost getNonCumulativeCost_( // getNonCumulativeCost_方法：实际执行非累积成本计算的私有方法，不包含缓存逻辑
      org.apache.calcite.rel.RelNode r, // 参数r：要计算非累积成本的关系节点
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq：元数据查询对象
    if (r instanceof org.apache.calcite.rel.RelNode) { // 如果r是RelNode类型（所有关系节点都继承自RelNode）
      return provider0.getNonCumulativeCost((org.apache.calcite.rel.RelNode) r, mq); // 委托给provider0对象调用其getNonCumulativeCost方法进行实际计算，传入r和mq参数
    } else { // 如果r不是RelNode类型（理论上不应该发生）
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract org.apache.calcite.plan.RelOptCost org.apache.calcite.rel.metadata.BuiltInMetadata$NonCumulativeCost$Handler.getNonCumulativeCost(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出非法参数异常，提示没有找到对应的处理器，并建议创建一个catch-all处理器
    }
  }

} // 类定义结束
