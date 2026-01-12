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

// 这是一个自动生成的元数据处理器类，用于处理NodeTypes（节点类型）元数据的查询
// 该类实现了BuiltInMetadata.NodeTypes.Handler接口，是Calcite元数据系统的核心组件之一
// 主要功能：为关系表达式(RelNode)提供节点类型信息的查询能力，支持缓存机制以提高性能
// 该类通过Janino编译器在运行时动态生成，根据RelMdNodeTypes接口定义的方法自动生成对应的分发逻辑
// 设计模式：采用了访问者模式(Visitor Pattern)的变体，通过instanceof判断RelNode的具体类型并调用相应的处理方法
public final class GeneratedMetadata_NodeTypesHandler
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.NodeTypes.Handler {
  // 缓存键对象，用于在元数据缓存中标识getNodeTypes()方法的结果
  // 使用DescriptiveCacheKey包装，提供可读性更好的缓存键描述信息
  // 该键在缓存中与特定的RelNode关联，用于存储和检索该节点的NodeTypes元数据
  private final Object methodKey0 =
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Multimap Handler.getNodeTypes()");
  // 元数据提供者对象，实际包含各种RelNode类型的getNodeTypes()方法实现
  // 该对象是RelMdNodeTypes接口的实例，包含了所有支持的RelNode类型的元数据计算逻辑
  // 通过provider0可以调用针对不同RelNode类型的具体实现方法
  public final org.apache.calcite.rel.metadata.RelMdNodeTypes provider0;
  // 构造方法：初始化NodeTypes元数据处理器
  // 参数provider0：元数据提供者对象，包含所有RelNode类型的getNodeTypes()方法实现
  // 该构造方法将provider0保存到成员变量中，供后续方法调用使用
  public GeneratedMetadata_NodeTypesHandler(
      org.apache.calcite.rel.metadata.RelMdNodeTypes provider0) {
    this.provider0 = provider0;
  }
  // 获取元数据定义信息
  // 返回值：MetadataDef对象，描述了该元数据的定义信息，包括元数据类型、方法签名等
  // 该方法直接委托给provider0对象调用其getDef()方法，返回NodeTypes元数据的定义
  public org.apache.calcite.rel.metadata.MetadataDef getDef() {
    return provider0.getDef();
  }
  // 获取指定关系节点的节点类型信息（公共入口方法，带缓存机制）
  // 参数r：关系节点(RelNode)，要查询节点类型信息的目标节点
  // 参数mq：元数据查询对象(RelMetadataQuery)，用于管理元数据缓存和查询上下文
  // 返回值：Multimap对象，键为RelNode类型，值为该类型节点的出现次数或相关信息
  // 该方法实现了缓存机制，避免重复计算相同的元数据，提高查询性能
  // 同时处理了循环依赖检测和异常情况
  public com.google.common.collect.Multimap getNodeTypes(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) {
    // 处理委托元数据关系节点(DelegatingMetadataRel)，找到实际的目标节点
    // DelegatingMetadataRel是一种包装节点，它会将元数据查询委托给内部的另一个节点
    // 通过循环解包，直到找到非委托节点为止
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) {
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel();
    }
    final Object key;
    // 使用预定义的缓存键methodKey0
    key = methodKey0;
    // 尝试从缓存中获取已计算的结果
    // mq.map是元数据缓存表，以RelNode和缓存键为索引存储计算结果
    final Object v = mq.map.get(r, key);
    // 如果缓存中存在结果
    if (v != null) {
      // 检查是否是ACTIVE标记，表示当前正在计算该元数据（检测循环依赖）
      // ACTIVE标记用于防止元数据计算过程中的循环调用
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) {
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException();
      }
      // 检查是否是INSTANCE标记，表示缓存中存储的是null值
      // NullSentinel.INSTANCE用于区分真正的null值和缓存未命中
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) {
        return null;
      }
      // 返回缓存中的结果
      return (com.google.common.collect.Multimap) v;
    }
    // 在开始计算前，先在缓存中放入ACTIVE标记，表示正在计算
    // 这样可以检测到循环依赖：如果再次查询到ACTIVE，说明存在循环
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE);
    try {
      // 调用实际的计算方法getNodeTypes_()来计算节点类型信息
      final com.google.common.collect.Multimap x = getNodeTypes_(r, mq);
      // 将计算结果存入缓存，使用mask()方法处理null值
      // mask()方法会将null值转换为NullSentinel.INSTANCE，非null值保持不变
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x));
      return x;
    } catch (java.lang.Exception e) {
      // 如果计算过程中发生异常，清除该节点的所有缓存条目
      // 这样可以避免缓存不一致的问题，下次查询会重新计算
      mq.map.row(r).clear();
      throw e;
    }
  }

  // 获取指定关系节点的节点类型信息（实际计算方法，不带缓存）
  // 参数r：关系节点(RelNode)，要查询节点类型信息的目标节点
  // 参数mq：元数据查询对象(RelMetadataQuery)，用于管理元数据查询上下文
  // 返回值：Multimap对象，键为RelNode类型，值为该类型节点的出现次数或相关信息
  // 该方法通过instanceof判断RelNode的具体类型，并调用provider0中对应的实现方法
  // 这是一个分发方法，根据RelNode的实际类型将请求路由到正确的处理逻辑
  private com.google.common.collect.Multimap getNodeTypes_(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) {
    // 如果是RelSubset类型（Volcano优化器中的等价关系集合）
    // RelSubset表示在优化过程中等价的多个RelNode的集合
    if (r instanceof org.apache.calcite.plan.volcano.RelSubset) {
      return provider0.getNodeTypes((org.apache.calcite.plan.volcano.RelSubset) r, mq);
    // 如果是Aggregate类型（聚合操作节点）
    // Aggregate节点用于执行GROUP BY、SUM、COUNT等聚合操作
    } else if (r instanceof org.apache.calcite.rel.core.Aggregate) {
      return provider0.getNodeTypes((org.apache.calcite.rel.core.Aggregate) r, mq);
    // 如果是Calc类型（计算操作节点）
    // Calc节点用于执行表达式计算和行级转换
    } else if (r instanceof org.apache.calcite.rel.core.Calc) {
      return provider0.getNodeTypes((org.apache.calcite.rel.core.Calc) r, mq);
    // 如果是Correlate类型（相关联接节点）
    // Correlate节点用于处理相关子查询，将子查询与外部查询关联起来
    } else if (r instanceof org.apache.calcite.rel.core.Correlate) {
      return provider0.getNodeTypes((org.apache.calcite.rel.core.Correlate) r, mq);
    // 如果是Exchange类型（数据交换节点）
    // Exchange节点用于分布式环境下的数据重新分区和交换
    } else if (r instanceof org.apache.calcite.rel.core.Exchange) {
      return provider0.getNodeTypes((org.apache.calcite.rel.core.Exchange) r, mq);
    // 如果是Filter类型（过滤操作节点）
    // Filter节点用于执行WHERE条件过滤，筛选符合条件的行
    } else if (r instanceof org.apache.calcite.rel.core.Filter) {
      return provider0.getNodeTypes((org.apache.calcite.rel.core.Filter) r, mq);
    // 如果是Intersect类型（交集操作节点）
    // Intersect节点用于执行INTERSECT集合操作，返回两个结果集的交集
    } else if (r instanceof org.apache.calcite.rel.core.Intersect) {
      return provider0.getNodeTypes((org.apache.calcite.rel.core.Intersect) r, mq);
    // 如果是Join类型（联接操作节点）
    // Join节点用于执行INNER/LEFT/RIGHT/FULL JOIN等联接操作
    } else if (r instanceof org.apache.calcite.rel.core.Join) {
      return provider0.getNodeTypes((org.apache.calcite.rel.core.Join) r, mq);
    // 如果是Match类型（模式匹配节点）
    // Match节点用于执行MATCH_RECOGNIZE模式匹配操作，识别数据中的模式
    } else if (r instanceof org.apache.calcite.rel.core.Match) {
      return provider0.getNodeTypes((org.apache.calcite.rel.core.Match) r, mq);
    // 如果是Minus类型（差集操作节点）
    // Minus节点用于执行EXCEPT集合操作，返回第一个结果集减去第二个结果集的差
    } else if (r instanceof org.apache.calcite.rel.core.Minus) {
      return provider0.getNodeTypes((org.apache.calcite.rel.core.Minus) r, mq);
    // 如果是Project类型（投影操作节点）
    // Project节点用于执行SELECT操作，计算输出表达式和列投影
    } else if (r instanceof org.apache.calcite.rel.core.Project) {
      return provider0.getNodeTypes((org.apache.calcite.rel.core.Project) r, mq);
    // 如果是Sample类型（采样操作节点）
    // Sample节点用于执行TABLESAMPLE采样操作，从表中随机抽取样本
    } else if (r instanceof org.apache.calcite.rel.core.Sample) {
      return provider0.getNodeTypes((org.apache.calcite.rel.core.Sample) r, mq);
    // 如果是Sort类型（排序操作节点）
    // Sort节点用于执行ORDER BY排序操作，对结果集进行排序
    } else if (r instanceof org.apache.calcite.rel.core.Sort) {
      return provider0.getNodeTypes((org.apache.calcite.rel.core.Sort) r, mq);
    // 如果是TableModify类型（表修改操作节点）
    // TableModify节点用于执行INSERT/UPDATE/DELETE等数据修改操作
    } else if (r instanceof org.apache.calcite.rel.core.TableModify) {
      return provider0.getNodeTypes((org.apache.calcite.rel.core.TableModify) r, mq);
    // 如果是TableScan类型（表扫描节点）
    // TableScan节点用于扫描表数据，是关系代数树的叶子节点
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) {
      return provider0.getNodeTypes((org.apache.calcite.rel.core.TableScan) r, mq);
    // 如果是Union类型（并集操作节点）
    // Union节点用于执行UNION集合操作，合并多个结果集（去重或不去重）
    } else if (r instanceof org.apache.calcite.rel.core.Union) {
      return provider0.getNodeTypes((org.apache.calcite.rel.core.Union) r, mq);
    // 如果是Values类型（值节点）
    // Values节点用于表示常量值集合，如VALUES (1,2), (3,4)
    } else if (r instanceof org.apache.calcite.rel.core.Values) {
      return provider0.getNodeTypes((org.apache.calcite.rel.core.Values) r, mq);
    // 如果是Window类型（窗口函数节点）
    // Window节点用于执行窗口函数操作，如OVER、PARTITION BY、ORDER BY等
    } else if (r instanceof org.apache.calcite.rel.core.Window) {
      return provider0.getNodeTypes((org.apache.calcite.rel.core.Window) r, mq);
    // 兜底情况：任意RelNode类型
    // 如果RelNode不属于上述任何特定类型，则使用通用的RelNode处理方法
    // 这是一个catch-all处理器，确保所有RelNode类型都有对应的处理逻辑
    } else if (r instanceof org.apache.calcite.rel.RelNode) {
      return provider0.getNodeTypes((org.apache.calcite.rel.RelNode) r, mq);
    // 如果RelNode类型不在上述任何已知类型中，抛出异常
    // 这种情况通常表示出现了新的RelNode类型，但没有相应的处理方法
    // 异常信息建议开发者创建一个catch-all (RelNode)处理器来处理这种情况
    } else {
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract com.google.common.collect.Multimap org.apache.calcite.rel.metadata.BuiltInMetadata$NodeTypes$Handler.getNodeTypes(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler");
    }
  }

}
