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

// GeneratedMetadata_UniqueKeysHandler: 唯一键(UniqueKeys)元数据处理器,由Janino动态生成
// 作用: 实现BuiltInMetadata.UniqueKeys.Handler接口,负责处理各种RelNode(关系表达式节点)的唯一键元数据查询
// 唯一键(UniqueKeys)是指关系中能够唯一标识每一行记录的列集合,通常是主键或候选键
// 该类通过类型分发机制,将getUniqueKeys调用委托给RelMdUniqueKeys中对应的具体实现方法
// 使用缓存机制避免重复计算,并检测循环依赖
public final class GeneratedMetadata_UniqueKeysHandler
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.UniqueKeys.Handler {
  // methodKey0True: 当a2参数为true时的缓存键,用于标识getUniqueKeys(true)的缓存条目
  // DescriptiveCacheKey是可描述的缓存键,包含方法签名的描述信息,便于调试
  private final Object methodKey0True =
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Set Handler.getUniqueKeys(true)");
  // methodKey0False: 当a2参数为false时的缓存键,用于标识getUniqueKeys(false)的缓存条目
  // a2参数表示是否忽略null值,不同的参数值需要不同的缓存键
  private final Object methodKey0False =
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Set Handler.getUniqueKeys(false)");
  // provider0: 唯一键元数据的提供者,包含各种RelNode类型的getUniqueKeys具体实现
  // RelMdUniqueKeys是Calcite内置的元数据提供者类,实现了所有关系运算符的唯一键计算逻辑
  public final org.apache.calcite.rel.metadata.RelMdUniqueKeys provider0;
  // 构造方法: 初始化唯一键处理器
  // 参数provider0: 唯一键元数据提供者实例,包含各种RelNode类型的唯一键计算实现
  public GeneratedMetadata_UniqueKeysHandler(
      org.apache.calcite.rel.metadata.RelMdUniqueKeys provider0) {
    this.provider0 = provider0; // 保存元数据提供者引用
  }
  // getDef: 获取元数据定义信息
  // 返回值: MetadataDef对象,描述此元数据的类型、名称等信息
  public org.apache.calcite.rel.metadata.MetadataDef getDef() {
    return provider0.getDef(); // 委托给provider0获取元数据定义
  }
  // getUniqueKeys: 获取指定关系节点的唯一键集合(带缓存和循环检测的公共入口方法)
  // 参数r: 目标关系节点(RelNode),可以是任何类型的关系运算符
  // 参数mq: 元数据查询上下文(RelMetadataQuery),包含缓存和其他查询相关信息
  // 参数a2: 是否忽略null值的布尔标志,true表示忽略null值,false表示考虑null值
  // 返回值: 唯一键集合(Set<ImmutableBitSet>),每个ImmutableBitSet表示一个唯一键的列索引集合
  public java.util.Set getUniqueKeys(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq,
      boolean a2) {
    // 处理委托元数据关系节点: 如果r是DelegatingMetadataRel类型,需要获取其真正的委托目标
    // DelegatingMetadataRel是装饰器模式的实现,用于在查询执行过程中添加额外的元数据信息
    // 通过while循环确保最终获取到最底层的非委托关系节点
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) {
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 获取委托的目标关系节点
    }
    final Object key; // 声明缓存键变量
    key = a2 ? methodKey0True : methodKey0False; // 根据a2参数选择对应的缓存键(true用methodKey0True,false用methodKey0False)
    final Object v = mq.map.get(r, key); // 从缓存中查找该关系节点的唯一键结果
    if (v != null) { // 如果缓存中存在结果
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // 检查是否是活跃标记,表示正在计算中(存在循环依赖)
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出循环元数据异常,防止无限递归
      }
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // 检查是否是空值哨兵,表示缓存中存储的是null结果
        return null; // 返回null,表示该关系节点没有唯一键
      }
      return (java.util.Set) v; // 缓存命中,直接返回缓存的唯一键集合
    }
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 缓存未命中,先放入活跃标记,表示开始计算
    try {
      final java.util.Set x = getUniqueKeys_(r, mq, a2); // 调用内部方法getUniqueKeys_进行实际计算
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果存入缓存,使用mask方法处理null值
      return x; // 返回计算得到的唯一键集合
    } catch (java.lang.Exception e) { // 捕获计算过程中的异常
      mq.map.row(r).clear(); // 清除该关系节点的所有缓存,避免脏数据
      throw e; // 重新抛出异常
    }
  }

  // getUniqueKeys_: 获取指定关系节点的唯一键集合(内部实现方法,通过类型分发调用provider0的具体方法)
  // 参数r: 目标关系节点(RelNode),可以是任何类型的关系运算符
  // 参数mq: 元数据查询上下文(RelMetadataQuery)
  // 参数a2: 是否忽略null值的布尔标志
  // 返回值: 唯一键集合(Set<ImmutableBitSet>)
  // 该方法使用if-else链进行类型分发,根据r的具体类型调用provider0中对应的重载方法
  private java.util.Set getUniqueKeys_(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq,
      boolean a2) {
    if (r instanceof org.apache.calcite.rel.core.Aggregate) { // 如果是聚合节点
      return provider0.getUniqueKeys((org.apache.calcite.rel.core.Aggregate) r, mq, a2); // 调用聚合节点的唯一键计算方法
    } else if (r instanceof org.apache.calcite.rel.core.Calc) { // 如果是计算节点(Calc是Project和Filter的组合)
      return provider0.getUniqueKeys((org.apache.calcite.rel.core.Calc) r, mq, a2); // 调用计算节点的唯一键计算方法
    } else if (r instanceof org.apache.calcite.rel.core.Correlate) { // 如果是关联节点(Correlate用于处理相关子查询)
      return provider0.getUniqueKeys((org.apache.calcite.rel.core.Correlate) r, mq, a2); // 调用关联节点的唯一键计算方法
    } else if (r instanceof org.apache.calcite.rel.core.Filter) { // 如果是过滤节点
      return provider0.getUniqueKeys((org.apache.calcite.rel.core.Filter) r, mq, a2); // 调用过滤节点的唯一键计算方法
    } else if (r instanceof org.apache.calcite.rel.core.Intersect) { // 如果是交集节点(INTERSECT集合运算)
      return provider0.getUniqueKeys((org.apache.calcite.rel.core.Intersect) r, mq, a2); // 调用交集节点的唯一键计算方法
    } else if (r instanceof org.apache.calcite.rel.core.Join) { // 如果是连接节点(内连接、外连接等)
      return provider0.getUniqueKeys((org.apache.calcite.rel.core.Join) r, mq, a2); // 调用连接节点的唯一键计算方法
    } else if (r instanceof org.apache.calcite.rel.core.Minus) { // 如果是差集节点(EXCEPT集合运算)
      return provider0.getUniqueKeys((org.apache.calcite.rel.core.Minus) r, mq, a2); // 调用差集节点的唯一键计算方法
    } else if (r instanceof org.apache.calcite.rel.core.Project) { // 如果是投影节点(列投影和表达式计算)
      return provider0.getUniqueKeys((org.apache.calcite.rel.core.Project) r, mq, a2); // 调用投影节点的唯一键计算方法
    } else if (r instanceof org.apache.calcite.rel.core.Sort) { // 如果是排序节点
      return provider0.getUniqueKeys((org.apache.calcite.rel.core.Sort) r, mq, a2); // 调用排序节点的唯一键计算方法
    } else if (r instanceof org.apache.calcite.rel.core.TableModify) { // 如果是表修改节点(INSERT/UPDATE/DELETE)
      return provider0.getUniqueKeys((org.apache.calcite.rel.core.TableModify) r, mq, a2); // 调用表修改节点的唯一键计算方法
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) { // 如果是表扫描节点
      return provider0.getUniqueKeys((org.apache.calcite.rel.core.TableScan) r, mq, a2); // 调用表扫描节点的唯一键计算方法
    } else if (r instanceof org.apache.calcite.rel.core.Union) { // 如果是并集节点(UNION集合运算)
      return provider0.getUniqueKeys((org.apache.calcite.rel.core.Union) r, mq, a2); // 调用并集节点的唯一键计算方法
    } else if (r instanceof org.apache.calcite.rel.core.Values) { // 如果是常量值节点(VALUES子句)
      return provider0.getUniqueKeys((org.apache.calcite.rel.core.Values) r, mq, a2); // 调用常量值节点的唯一键计算方法
    } else if (r instanceof org.apache.calcite.rel.RelNode) { // 兜底情况,任何RelNode的基类
      return provider0.getUniqueKeys((org.apache.calcite.rel.RelNode) r, mq, a2); // 调用通用RelNode的唯一键计算方法
    } else { // 如果关系节点类型不匹配任何已知类型
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.util.Set org.apache.calcite.rel.metadata.BuiltInMetadata$UniqueKeys$Handler.getUniqueKeys(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery,boolean)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出异常,提示没有对应的处理器,建议添加一个通用的(RelNode)处理器
    }
  }

}
