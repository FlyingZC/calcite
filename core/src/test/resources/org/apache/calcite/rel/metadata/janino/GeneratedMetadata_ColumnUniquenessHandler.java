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

// 这是一个由Janino代码生成器自动生成的类,用于处理列唯一性(ColumnUniqueness)元数据查询
// 该类实现了BuiltInMetadata.ColumnUniqueness.Handler接口,作为元数据处理器
// 主要功能包括:1)缓存元数据查询结果以提高性能 2)检测循环依赖 3)将查询分发到具体的RelNode类型处理器
// 该类是Calcite元数据系统的核心组件之一,通过缓存机制避免重复计算
// 使用双重检查锁定模式来处理并发访问,确保线程安全
public final class GeneratedMetadata_ColumnUniquenessHandler
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.ColumnUniqueness.Handler {
  // 方法缓存键,用于在元数据缓存中唯一标识areColumnsUnique方法
  // 使用DescriptiveCacheKey包装方法签名,便于调试和错误追踪
  // 该键包含方法名和参数类型信息,确保不同方法调用不会混淆
  private final Object methodKey0 =
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Boolean Handler.areColumnsUnique(RelNode, RelMetadataQuery, ImmutableBitSet, boolean)");
  // 元数据提供者,实际执行列唯一性计算的核心对象
  // 该对象包含针对不同RelNode类型的具体实现逻辑
  // 通过委托模式,将实际计算工作交给RelMdColumnUniqueness处理
  public final org.apache.calcite.rel.metadata.RelMdColumnUniqueness provider0;
  // 构造方法,初始化列唯一性处理器
  // 参数provider0:元数据提供者,包含各种RelNode类型的列唯一性计算逻辑
  // 该构造方法简单地将传入的provider0保存到成员变量中,供后续方法调用使用
  public GeneratedMetadata_ColumnUniquenessHandler(
      org.apache.calcite.rel.metadata.RelMdColumnUniqueness provider0) {
    this.provider0 = provider0; // 将传入的元数据提供者保存到成员变量
  }
  // 获取元数据定义,返回该处理器处理的元数据类型描述
  // 返回值:MetadataDef对象,描述ColumnUniqueness元数据的定义信息
  // 该方法直接委托给provider0,因为元数据定义是由provider维护的
  public org.apache.calcite.rel.metadata.MetadataDef getDef() {
    return provider0.getDef(); // 返回provider中维护的元数据定义
  }
  // 判断指定列集合是否唯一(即这些列的组合值是否不重复)
  // 这是元数据查询的入口方法,包含缓存逻辑和循环依赖检测
  // 参数r:关系表达式节点,表示要查询的RelNode
  // 参数mq:元数据查询上下文,包含缓存映射和其他查询状态信息
  // 参数a2:ImmutableBitSet对象,表示要检查唯一性的列索引集合
  // 参数a3:布尔值,表示是否只考虑非空列(当为true时,空值不参与唯一性判断)
  // 返回值:Boolean对象,true表示列唯一,false表示不唯一,null表示无法确定
  public java.lang.Boolean areColumnsUnique(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq,
      org.apache.calcite.util.ImmutableBitSet a2,
      boolean a3) {
    // 循环处理委托元数据关系节点,直到找到实际的关系节点
    // DelegatingMetadataRel是一种包装器,用于增强或修改元数据行为
    // 通过不断解包,确保最终查询到真正的关系节点
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) {
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 获取被委托的关系节点
    }
    final Object key; // 声明缓存键变量
    // 构建缓存键,包含方法标识和参数
    // methodKey0标识这是areColumnsUnique方法
    // NullSentinel.mask(a2)将列集合转换为可缓存的形式(处理null值)
    // a3直接作为参数的一部分
    key = org.apache.calcite.runtime.FlatLists.of(methodKey0, org.apache.calcite.rel.metadata.NullSentinel.mask(a2), a3);
    final Object v = mq.map.get(r, key); // 从缓存中查找是否已有计算结果
    if (v != null) { // 如果缓存中存在结果
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // 检查是否为活跃状态(表示正在计算中)
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出循环依赖异常,防止无限递归
      }
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // 检查是否为null标记(表示结果为null)
        return null; // 返回null,表示无法确定列唯一性
      }
      return (java.lang.Boolean) v; // 返回缓存的计算结果(转换为Boolean类型)
    }
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 将缓存状态设置为活跃,表示开始计算
    try {
      final java.lang.Boolean x = areColumnsUnique_(r, mq, a2, a3); // 调用内部方法实际计算列唯一性
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果存入缓存(使用mask处理null值)
      return x; // 返回计算结果
    } catch (java.lang.Exception e) { // 捕获计算过程中可能出现的异常
      mq.map.row(r).clear(); // 清除该关系节点的所有缓存条目,避免脏数据
      throw e; // 重新抛出异常
    }
  }

  // 私有方法,实际执行列唯一性计算的内部实现
  // 该方法根据关系节点的具体类型,将调用分发到provider0中对应的重载方法
  // 使用if-else链进行类型检查和分发,确保每种RelNode类型都有对应的处理逻辑
  // 参数含义与areColumnsUnique方法相同
  private java.lang.Boolean areColumnsUnique_(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq,
      org.apache.calcite.util.ImmutableBitSet a2,
      boolean a3) {
    // 检查是否为RelSubset类型(Volcano优化器中的等价关系集合)
    // RelSubset包含多个等价的物理实现,需要特殊处理
    if (r instanceof org.apache.calcite.plan.volcano.RelSubset) {
      return provider0.areColumnsUnique((org.apache.calcite.plan.volcano.RelSubset) r, mq, a2, a3); // 调用RelSubset专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.convert.Converter) { // 检查是否为Converter类型(用于trait转换的节点)
      return provider0.areColumnsUnique((org.apache.calcite.rel.convert.Converter) r, mq, a2, a3); // 调用Converter专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Aggregate) { // 检查是否为Aggregate类型(聚合操作节点)
      return provider0.areColumnsUnique((org.apache.calcite.rel.core.Aggregate) r, mq, a2, a3); // 调用Aggregate专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Calc) { // 检查是否为Calc类型(计算节点,包含表达式计算)
      return provider0.areColumnsUnique((org.apache.calcite.rel.core.Calc) r, mq, a2, a3); // 调用Calc专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Correlate) { // 检查是否为Correlate类型(相关联接节点)
      return provider0.areColumnsUnique((org.apache.calcite.rel.core.Correlate) r, mq, a2, a3); // 调用Correlate专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Exchange) { // 检查是否为Exchange类型(数据交换节点,用于分布式场景)
      return provider0.areColumnsUnique((org.apache.calcite.rel.core.Exchange) r, mq, a2, a3); // 调用Exchange专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Filter) { // 检查是否为Filter类型(过滤节点,应用WHERE条件)
      return provider0.areColumnsUnique((org.apache.calcite.rel.core.Filter) r, mq, a2, a3); // 调用Filter专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Intersect) { // 检查是否为Intersect类型(交集操作节点)
      return provider0.areColumnsUnique((org.apache.calcite.rel.core.Intersect) r, mq, a2, a3); // 调用Intersect专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Join) { // 检查是否为Join类型(联接操作节点)
      return provider0.areColumnsUnique((org.apache.calcite.rel.core.Join) r, mq, a2, a3); // 调用Join专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Minus) { // 检查是否为Minus类型(差集操作节点,即EXCEPT)
      return provider0.areColumnsUnique((org.apache.calcite.rel.core.Minus) r, mq, a2, a3); // 调用Minus专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Project) { // 检查是否为Project类型(投影节点,用于列选择和计算)
      return provider0.areColumnsUnique((org.apache.calcite.rel.core.Project) r, mq, a2, a3); // 调用Project专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.SetOp) { // 检查是否为SetOp类型(集合操作基类)
      return provider0.areColumnsUnique((org.apache.calcite.rel.core.SetOp) r, mq, a2, a3); // 调用SetOp专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Sort) { // 检查是否为Sort类型(排序节点,用于ORDER BY)
      return provider0.areColumnsUnique((org.apache.calcite.rel.core.Sort) r, mq, a2, a3); // 调用Sort专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.TableModify) { // 检查是否为TableModify类型(表修改节点,INSERT/UPDATE/DELETE)
      return provider0.areColumnsUnique((org.apache.calcite.rel.core.TableModify) r, mq, a2, a3); // 调用TableModify专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) { // 检查是否为TableScan类型(表扫描节点,从表中读取数据)
      return provider0.areColumnsUnique((org.apache.calcite.rel.core.TableScan) r, mq, a2, a3); // 调用TableScan专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Values) { // 检查是否为Values类型(常量值节点,如VALUES (1,2),(3,4))
      return provider0.areColumnsUnique((org.apache.calcite.rel.core.Values) r, mq, a2, a3); // 调用Values专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.RelNode) { // 最后的兜底处理,检查是否为通用的RelNode类型
      return provider0.areColumnsUnique((org.apache.calcite.rel.RelNode) r, mq, a2, a3); // 调用通用的RelNode处理方法
    } else { // 如果没有匹配到任何已知类型,抛出异常
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.lang.Boolean org.apache.calcite.rel.metadata.BuiltInMetadata$ColumnUniqueness$Handler.areColumnsUnique(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery,org.apache.calcite.util.ImmutableBitSet,boolean)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出异常,提示缺少处理器,建议创建通用的RelNode处理器
    }
  }

}
