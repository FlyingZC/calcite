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
package org.apache.calcite.rel.metadata.janino;

// GeneratedMetadata_DistributionHandler是Calcite框架中自动生成的RelDistribution（关系分布）元数据处理器类
// 该类实现了BuiltInMetadata.Distribution.Handler接口，用于处理关系算子的数据分布信息查询
// RelDistribution描述了数据在分布式环境中的分布方式（如单机分布、广播分布、哈希分布等）
// 该类通过Janino编译器在运行时动态生成，实现了元数据查询的缓存机制和循环依赖检测
// 主要功能：为不同类型的关系算子（BiRel、Exchange、Project等）提供distribution元数据的查询入口
// 缓存机制：使用RelMetadataQuery中的map缓存查询结果，避免重复计算
// 循环检测：通过Active标记检测元数据计算过程中的循环依赖
public final class GeneratedMetadata_DistributionHandler
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.Distribution.Handler { // 实现Distribution.Handler接口，提供distribution元数据查询能力
  private final Object methodKey0 = // methodKey0是distribution方法的缓存键，用于在元数据缓存中标识distribution方法的查询结果
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("RelDistribution Handler.distribution()"); // 创建描述性缓存键，包含方法描述信息便于调试
  public final org.apache.calcite.rel.metadata.RelMdDistribution provider0; // provider0是RelDistribution元数据的实际提供者，包含各种关系算子的distribution计算逻辑
  public GeneratedMetadata_DistributionHandler( // 构造方法：创建DistributionHandler实例
      org.apache.calcite.rel.metadata.RelMdDistribution provider0) { // 参数provider0：RelDistribution元数据提供者，包含具体的distribution计算实现
    this.provider0 = provider0; // 将传入的provider0赋值给成员变量，用于后续distribution计算
  }
  public org.apache.calcite.rel.metadata.MetadataDef getDef() { // getDef方法：获取元数据定义，描述该元数据的类型和特征
    return provider0.getDef(); // 委托给provider0获取MetadataDef，返回Distribution元数据的定义信息
  }
  public org.apache.calcite.rel.RelDistribution distribution( // distribution方法：查询指定关系算子的数据分布信息（带缓存和循环检测）
      org.apache.calcite.rel.RelNode r, // 参数r：要查询分布信息的关系算子节点
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq：元数据查询对象，包含缓存map和查询上下文
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) { // 循环检查：如果当前节点是代理元数据关系节点，则解包到实际的委托节点
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 获取代理节点委托的实际关系节点，避免在代理节点上查询元数据
    }
    final Object key; // 声明缓存键变量
    key = methodKey0; // 将methodKey0赋值给key，用于在缓存中查找distribution结果
    final Object v = mq.map.get(r, key); // 从缓存map中获取当前关系节点r的distribution缓存值
    if (v != null) { // 如果缓存中存在值
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // 检查是否为ACTIVE标记，表示正在计算中（循环依赖检测）
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出循环元数据异常，表示检测到元数据计算的循环依赖
      }
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // 检查是否为INSTANCE标记，表示缓存值为null
        return null; // 返回null，表示该关系节点的distribution为null
      }
      return (org.apache.calcite.rel.RelDistribution) v; // 返回缓存中的distribution对象（强制类型转换）
    }
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 在缓存中标记为ACTIVE，表示开始计算distribution（用于循环检测）
    try { // 开始try块，捕获计算过程中的异常
      final org.apache.calcite.rel.RelDistribution x = distribution_(r, mq); // 调用distribution_方法实际计算distribution值
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果存入缓存，使用mask处理null值
      return x; // 返回计算得到的distribution对象
    } catch (java.lang.Exception e) { // 捕获计算过程中发生的任何异常
      mq.map.row(r).clear(); // 清除该关系节点在缓存中的所有元数据，避免缓存脏数据
      throw e; // 重新抛出异常，让上层调用者处理
    }
  }

  private org.apache.calcite.rel.RelDistribution distribution_( // distribution_方法：实际执行distribution计算的内部方法（无缓存逻辑）
      org.apache.calcite.rel.RelNode r, // 参数r：要计算分布信息的关系算子节点
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq：元数据查询对象，传递给provider0进行递归查询
    if (r instanceof org.apache.calcite.rel.BiRel) { // 类型判断：如果关系节点是BiRel（双输入关系算子，如Join）
      return provider0.distribution((org.apache.calcite.rel.BiRel) r, mq); // 委托给provider0的BiRel重载方法计算distribution
    } else if (r instanceof org.apache.calcite.rel.core.Exchange) { // 类型判断：如果关系节点是Exchange（数据交换算子，用于重分布数据）
      return provider0.distribution((org.apache.calcite.rel.core.Exchange) r, mq); // 委托给provider0的Exchange重载方法计算distribution
    } else if (r instanceof org.apache.calcite.rel.core.Project) { // 类型判断：如果关系节点是Project（投影算子，用于表达式计算和列选择）
      return provider0.distribution((org.apache.calcite.rel.core.Project) r, mq); // 委托给provider0的Project重载方法计算distribution
    } else if (r instanceof org.apache.calcite.rel.core.SetOp) { // 类型判断：如果关系节点是SetOp（集合操作算子，如Union、Intersect、Minus）
      return provider0.distribution((org.apache.calcite.rel.core.SetOp) r, mq); // 委托给provider0的SetOp重载方法计算distribution
    } else if (r instanceof org.apache.calcite.rel.core.TableModify) { // 类型判断：如果关系节点是TableModify（表修改算子，用于Insert/Update/Delete）
      return provider0.distribution((org.apache.calcite.rel.core.TableModify) r, mq); // 委托给provider0的TableModify重载方法计算distribution
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) { // 类型判断：如果关系节点是TableScan（表扫描算子，用于从表中读取数据）
      return provider0.distribution((org.apache.calcite.rel.core.TableScan) r, mq); // 委托给provider0的TableScan重载方法计算distribution
    } else if (r instanceof org.apache.calcite.rel.core.Values) { // 类型判断：如果关系节点是Values（常量值算子，用于生成常量数据）
      return provider0.distribution((org.apache.calcite.rel.core.Values) r, mq); // 委托给provider0的Values重载方法计算distribution
    } else if (r instanceof org.apache.calcite.rel.SingleRel) { // 类型判断：如果关系节点是SingleRel（单输入关系算子，如Filter、Sort等）
      return provider0.distribution((org.apache.calcite.rel.SingleRel) r, mq); // 委托给provider0的SingleRel重载方法计算distribution
    } else if (r instanceof org.apache.calcite.rel.RelNode) { // 类型判断：兜底情况，如果关系节点是RelNode（通用关系节点接口）
      return provider0.distribution((org.apache.calcite.rel.RelNode) r, mq); // 委托给provider0的RelNode重载方法计算distribution（通用实现）
    } else { // 如果以上所有类型都不匹配（理论上不应该发生）
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract org.apache.calcite.rel.RelDistribution org.apache.calcite.rel.metadata.BuiltInMetadata$Distribution$Handler.distribution(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出非法参数异常，提示缺少对该类型节点的处理方法，建议添加RelNode兜底处理
    }
  }

}
