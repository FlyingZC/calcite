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
// Apache Calcite是一个动态数据管理框架，提供SQL解析、优化、执行等功能
// 本文件位于metadata（元数据）模块的janino子包中，janino是一个轻量级Java编译器，用于运行时动态编译Java代码
// 本类是GeneratedMetadata_DistinctRowCountHandler，是Calcite元数据系统自动生成的处理器类
package org.apache.calcite.rel.metadata.janino;

// 这是一个自动生成的类，用于处理DistinctRowCount（不同行数统计）元数据的计算
// 实现了BuiltInMetadata.DistinctRowCount.Handler接口，该接口定义了获取不同行数的方法
// DistinctRowCount元数据用于估算关系代数节点中指定列的不同值的数量，这对查询优化器进行成本估算和选择最优执行计划非常重要
// 例如：对于表中有100万行数据，某列有50万个不同值，这个信息可以帮助优化器决定是否使用索引、选择哪种连接算法等
// 该类使用Janino编译器在运行时动态生成，通过反射和类型检查将调用分发给对应的具体实现类
public final class GeneratedMetadata_DistinctRowCountHandler
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.DistinctRowCount.Handler {
  // 方法键对象，用于在元数据缓存中标识getDistinctRowCount方法
  // DescriptiveCacheKey是一个描述性的缓存键，包含方法签名信息"Double Handler.getDistinctRowCount(RelNode, RelMetadataQuery, ImmutableBitSet, RexNode)"
  // 这个键用于在缓存中唯一标识这个元数据方法的调用，确保不同参数组合的缓存项不会冲突
  // methodKey0是final常量，在对象构造时初始化，后续不可修改
  private final Object methodKey0 =
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Double Handler.getDistinctRowCount(RelNode, RelMetadataQuery, ImmutableBitSet, RexNode)");
  // 元数据提供者对象，实际执行不同行数计算的核心类
  // RelMdDistinctRowCount是Calcite内置的元数据处理器，包含了各种RelNode类型的getDistinctRowCount方法的具体实现
  // provider0是public final，意味着它对外可见且不可修改，在构造函数中初始化
  // 这个对象是委托模式的核心，本类作为代理，将实际计算委托给provider0
  public final org.apache.calcite.rel.metadata.RelMdDistinctRowCount provider0;
  // 构造方法，用于创建GeneratedMetadata_DistinctRowCountHandler实例
  // 参数provider0：RelMdDistinctRowCount类型的元数据提供者对象，包含了各种RelNode类型的不同行数计算逻辑
  // 构造方法将传入的provider0保存到成员变量中，供后续方法调用使用
  // 这个构造方法通常由Janino编译器在运行时调用，provider0参数由Calcite的元数据系统传入
  public GeneratedMetadata_DistinctRowCountHandler(
      org.apache.calcite.rel.metadata.RelMdDistinctRowCount provider0) {
    // 将传入的provider0参数赋值给成员变量this.provider0
    // this关键字用于区分成员变量和参数变量（虽然两者名称相同，但this.provider0指成员变量，provider0指参数）
    this.provider0 = provider0;
  }
  // 获取元数据定义的方法
  // 返回值：MetadataDef对象，描述了该元数据的类型、名称、方法签名等信息
  // MetadataDef是元数据的定义类，包含了元数据的完整描述信息，用于元数据系统的注册和查找
  // 该方法直接委托给provider0.getDef()，因为元数据定义信息由provider0持有
  public org.apache.calcite.rel.metadata.MetadataDef getDef() {
    // 调用provider0的getDef方法，获取DistinctRowCount元数据的定义信息
    // 返回的MetadataDef包含了该元数据的所有描述信息，如元数据类型、处理类、方法签名等
    return provider0.getDef();
  }
  // 获取关系节点的不同行数（DistinctRowCount）的核心方法
  // 参数r：RelNode关系节点，表示要计算不同行数的关系代数表达式（如TableScan、Filter、Join等）
  // 参数mq：RelMetadataQuery元数据查询对象，提供了访问其他元数据的接口，以及元数据缓存功能
  // 参数a2：ImmutableBitSet不可变位集合，表示要统计不同值的列索引集合（例如：{0,2}表示第0列和第2列）
  // 参数a3：RexNode谓词表达式，表示可选的过滤条件，用于在统计不同行数时应用额外的过滤（可为null）
  // 返回值：Double类型，表示指定列在满足谓词条件下的不同行数估计值，如果无法估计则返回null
  // 该方法实现了缓存机制和循环依赖检测，是元数据系统的公共入口点
  public java.lang.Double getDistinctRowCount(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq,
      org.apache.calcite.util.ImmutableBitSet a2,
      org.apache.calcite.rex.RexNode a3) {
    // 处理委托元数据关系节点（DelegatingMetadataRel）
    // DelegatingMetadataRel是一个包装器节点，它本身不包含数据，而是将元数据请求委托给内部的真实节点
    // while循环会一直解包装，直到找到非DelegatingMetadataRel的真实节点
    // 这样可以避免在缓存中存储委托节点的元数据，减少缓存冗余
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) {
      // 将r转换为DelegatingMetadataRel类型，并调用getMetadataDelegateRel方法获取被委托的真实节点
      // 强制类型转换是安全的，因为前面已经通过instanceof检查了类型
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel();
    }
    // 声明缓存键对象key，用于在元数据缓存中查找或存储计算结果
    // key是final变量，赋值后不可修改
    final Object key;
    // 构建缓存键，使用FlatLists.of方法创建一个扁平列表作为键
    // FlatLists.of是一个工具方法，用于创建不可变的列表，适合作为缓存键
    // 缓存键包含三个部分：methodKey0（方法标识）、NullSentinel.mask(a2)（处理后的列集合）、a3（谓词表达式）
    // NullSentinel.mask(a2)将a2包装，处理null值的情况，确保null也能作为有效的缓存键部分
    key = org.apache.calcite.runtime.FlatLists.of(methodKey0, org.apache.calcite.rel.metadata.NullSentinel.mask(a2), a3);
    // 从元数据缓存中查找是否已经计算过该元数据
    // mq.map是一个双键映射（RelNode -> Key -> Value），第一个键是关系节点r，第二个键是方法参数key
    // 如果缓存中存在该元数据，则直接返回缓存值，避免重复计算，提高性能
    final Object v = mq.map.get(r, key);
    // 检查缓存中是否存在该元数据（v不为null表示已存在）
    if (v != null) {
      // 检查缓存值是否为ACTIVE标记，ACTIVE表示该元数据正在计算中（存在循环依赖）
      // NullSentinel.ACTIVE是一个特殊标记，用于检测元数据计算过程中的循环引用
      // 如果发现循环依赖，说明元数据计算逻辑中存在循环调用，需要抛出异常终止计算
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) {
        // 抛出循环元数据异常，表示检测到元数据计算的循环依赖
        // CyclicMetadataException是Calcite的运行时异常，用于报告元数据系统的循环依赖问题
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException();
      }
      // 检查缓存值是否为INSTANCE标记，INSTANCE表示该元数据的计算结果为null
      // NullSentinel.INSTANCE是一个单例对象，用于在缓存中表示null值（因为缓存不允许存储null）
      // 如果缓存值是INSTANCE，说明之前计算的结果就是null，直接返回null
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) {
        // 返回null，表示无法计算该元数据
        return null;
      }
      // 缓存值既不是ACTIVE也不是INSTANCE，说明是实际的计算结果
      // 将缓存值强制转换为Double类型并返回
      // 强制类型转换是安全的，因为缓存中存储的值必定是Double类型
      return (java.lang.Double) v;
    }
    // 缓存中不存在该元数据，需要在计算前先放入ACTIVE标记，表示开始计算
    // 这样可以防止其他线程或递归调用重复计算，同时用于检测循环依赖
    // mq.map.put将ACTIVE标记放入缓存，键是(r, key)
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE);
    // 使用try-catch块保护元数据计算过程
    // try块中执行实际的元数据计算，catch块中处理异常情况
    try {
      // 调用内部方法getDistinctRowCount_执行实际的元数据计算
      // getDistinctRowCount_是私有方法，包含了针对不同RelNode类型的分发逻辑
      // 计算结果存储在变量x中
      final java.lang.Double x = getDistinctRowCount_(r, mq, a2, a3);
      // 将计算结果x存入缓存，使用NullSentinel.mask(x)处理null值
      // NullSentinel.mask(x)会将null转换为INSTANCE对象，非null值保持不变
      // 这样下次查询时可以直接从缓存中获取结果，避免重复计算
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x));
      // 返回计算结果x
      return x;
    } catch (java.lang.Exception e) {
      // 如果计算过程中抛出异常，需要清理该关系节点的所有缓存条目
      // mq.map.row(r).clear()会清除关系节点r的所有元数据缓存
      // 这样可以避免缓存中留下不完整或错误的数据
      mq.map.row(r).clear();
      // 重新抛出异常，让调用者处理
      // 异常类型保持不变，确保调用者能够正确识别和处理异常
      throw e;
    }
  }

  // 私有方法，实际执行不同行数计算的内部实现
  // 该方法根据RelNode的具体类型，将调用分发给provider0中对应的重载方法
  // 参数和返回值与公共方法getDistinctRowCount相同
  // 使用if-else链进行类型检查和方法分发，这是Janino生成的典型模式
  private java.lang.Double getDistinctRowCount_(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq,
      org.apache.calcite.util.ImmutableBitSet a2,
      org.apache.calcite.rex.RexNode a3) {
    // 检查r是否为RelSubset类型（Volcano优化器中的等价关系集合）
    // RelSubset是Volcano优化器的核心概念，表示逻辑上等价的物理实现集合
    // 如果是RelSubset，调用provider0中针对RelSubset的重载方法
    if (r instanceof org.apache.calcite.plan.volcano.RelSubset) {
      // 将r强制转换为RelSubset类型，调用provider0的getDistinctRowCount方法
      // RelSubset的DistinctRowCount计算需要考虑集合中所有等价实现的最优值
      return provider0.getDistinctRowCount((org.apache.calcite.plan.volcano.RelSubset) r, mq, a2, a3);
    } else if (r instanceof org.apache.calcite.rel.core.Aggregate) {
      // 检查r是否为Aggregate类型（聚合操作节点，如GROUP BY）
      // Aggregate节点会根据分组键对数据进行分组，并应用聚合函数
      // 如果是Aggregate，调用provider0中针对Aggregate的重载方法
      return provider0.getDistinctRowCount((org.apache.calcite.rel.core.Aggregate) r, mq, a2, a3);
    } else if (r instanceof org.apache.calcite.rel.core.Exchange) {
      // 检查r是否为Exchange类型（数据交换节点，用于分布式执行）
      // Exchange节点用于在分布式环境中重新分区数据
      // 如果是Exchange，调用provider0中针对Exchange的重载方法
      return provider0.getDistinctRowCount((org.apache.calcite.rel.core.Exchange) r, mq, a2, a3);
    } else if (r instanceof org.apache.calcite.rel.core.Filter) {
      // 检查r是否为Filter类型（过滤节点，表示WHERE条件）
      // Filter节点根据条件过滤输入数据，输出满足条件的数据行
      // 如果是Filter，调用provider0中针对Filter的重载方法
      return provider0.getDistinctRowCount((org.apache.calcite.rel.core.Filter) r, mq, a2, a3);
    } else if (r instanceof org.apache.calcite.rel.core.Join) {
      // 检查r是否为Join类型（连接节点，表示表连接操作）
      // Join节点根据连接条件将两个或多个关系节点连接在一起
      // 如果是Join，调用provider0中针对Join的重载方法
      return provider0.getDistinctRowCount((org.apache.calcite.rel.core.Join) r, mq, a2, a3);
    } else if (r instanceof org.apache.calcite.rel.core.Project) {
      // 检查r是否为Project类型（投影节点，表示SELECT列表）
      // Project节点对输入数据进行列投影和表达式计算
      // 如果是Project，调用provider0中针对Project的重载方法
      return provider0.getDistinctRowCount((org.apache.calcite.rel.core.Project) r, mq, a2, a3);
    } else if (r instanceof org.apache.calcite.rel.core.Sort) {
      // 检查r是否为Sort类型（排序节点，表示ORDER BY）
      // Sort节点对输入数据进行排序，可能包含LIMIT和OFFSET
      // 如果是Sort，调用provider0中针对Sort的重载方法
      return provider0.getDistinctRowCount((org.apache.calcite.rel.core.Sort) r, mq, a2, a3);
    } else if (r instanceof org.apache.calcite.rel.core.TableModify) {
      // 检查r是否为TableModify类型（表修改节点，表示INSERT/UPDATE/DELETE）
      // TableModify节点表示对表的修改操作
      // 如果是TableModify，调用provider0中针对TableModify的重载方法
      return provider0.getDistinctRowCount((org.apache.calcite.rel.core.TableModify) r, mq, a2, a3);
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) {
      // 检查r是否为TableScan类型（表扫描节点，表示FROM子句中的表）
      // TableScan节点表示从数据源读取数据，通常是数据库表或文件
      // 如果是TableScan，调用provider0中针对TableScan的重载方法
      return provider0.getDistinctRowCount((org.apache.calcite.rel.core.TableScan) r, mq, a2, a3);
    } else if (r instanceof org.apache.calcite.rel.core.Union) {
      // 检查r是否为Union类型（联合节点，表示UNION ALL操作）
      // Union节点将多个输入节点的数据合并在一起（不去重）
      // 如果是Union，调用provider0中针对Union的重载方法
      return provider0.getDistinctRowCount((org.apache.calcite.rel.core.Union) r, mq, a2, a3);
    } else if (r instanceof org.apache.calcite.rel.core.Values) {
      // 检查r是否为Values类型（值节点，表示常量行集合）
      // Values节点表示内联的常量数据，如VALUES (1, 'a'), (2, 'b')
      // 如果是Values，调用provider0中针对Values的重载方法
      return provider0.getDistinctRowCount((org.apache.calcite.rel.core.Values) r, mq, a2, a3);
    } else if (r instanceof org.apache.calcite.rel.RelNode) {
      // 检查r是否为RelNode类型（所有关系节点的基类）
      // 这是一个兜底分支，处理所有未在前面明确列出的RelNode子类型
      // 如果是RelNode（且不是前面的任何特定类型），调用provider0中针对RelNode的通用重载方法
      return provider0.getDistinctRowCount((org.apache.calcite.rel.RelNode) r, mq, a2, a3);
    } else {
      // 如果r不是任何已知的RelNode类型，抛出IllegalArgumentException异常
      // 这种情况理论上不应该发生，因为所有RelNode都应该是RelNode的子类
      // 异常消息包含方法签名和r的实际类型，帮助开发者定位问题
      // 建议创建一个catch-all（RelNode）处理器来处理未知类型
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.lang.Double org.apache.calcite.rel.metadata.BuiltInMetadata$DistinctRowCount$Handler.getDistinctRowCount(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery,org.apache.calcite.util.ImmutableBitSet,org.apache.calcite.rex.RexNode)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler");
    }
  }

}
