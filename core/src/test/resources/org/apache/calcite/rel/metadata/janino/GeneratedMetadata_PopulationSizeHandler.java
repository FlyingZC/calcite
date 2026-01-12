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

// 本类是由Calcite的元数据处理器生成器自动生成的代码，用于处理PopulationSize元数据查询
// PopulationSize元数据表示关系表达式(RelNode)在指定列集合上的不同值数量（基数统计信息）
// 本类实现了BuiltInMetadata.PopulationSize.Handler接口，作为PopulationSize元数据的处理器
// 主要功能：1) 提供缓存机制避免重复计算 2) 检测循环依赖防止无限递归 3) 根据RelNode类型分发到对应的计算方法
public final class GeneratedMetadata_PopulationSizeHandler
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.PopulationSize.Handler {
  // 方法缓存键，用于在元数据缓存中标识getPopulationSize方法
  // DescriptiveCacheKey是一个描述性的缓存键，包含方法签名的完整信息，便于调试和问题排查
  // 键值内容：方法返回类型Double、处理器类名Handler、方法名getPopulationSize、参数列表(RelNode, RelMetadataQuery, ImmutableBitSet)
  private final Object methodKey0 =
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Double Handler.getPopulationSize(RelNode, RelMetadataQuery, ImmutableBitSet)");
  // PopulationSize元数据的实际提供者，包含各种RelNode类型的PopulationSize计算逻辑
  // RelMdPopulationSize是Calcite内置的元数据提供者类，实现了针对不同RelNode类型的PopulationSize计算规则
  // provider0是final字段，在构造函数中初始化后不可更改，保证线程安全
  public final org.apache.calcite.rel.metadata.RelMdPopulationSize provider0;
  // 构造函数：初始化PopulationSize元数据处理器
  // 参数provider0：RelMdPopulationSize实例，包含实际的PopulationSize计算逻辑
  // 该构造函数由元数据处理器生成器在运行时调用，传入预先配置好的元数据提供者
  public GeneratedMetadata_PopulationSizeHandler(
      org.apache.calcite.rel.metadata.RelMdPopulationSize provider0) {
    // 将传入的元数据提供者实例保存到成员变量中
    // 这个provider0包含了针对不同RelNode类型(Aggregate, Join, Filter等)的PopulationSize计算方法
    this.provider0 = provider0;
  }
  // 获取元数据定义信息，返回PopulationSize元数据的元数据定义对象
  // MetadataDef包含了元数据的类型、名称、处理类等信息，用于元数据系统的注册和查找
  // 该方法直接委托给provider0的getDef方法，因为元数据定义由RelMdPopulationSize类统一管理
  public org.apache.calcite.rel.metadata.MetadataDef getDef() {
    return provider0.getDef();
  }
  // 获取指定关系表达式在给定列集合上的PopulationSize（不同值数量）
  // 参数r：待查询的关系表达式(RelNode)，可能是TableScan, Join, Filter等各种类型
  // 参数mq：RelMetadataQuery元数据查询上下文对象，包含缓存和其他元数据查询所需的状态信息
  // 参数a2：ImmutableBitSet对象，表示要统计PopulationSize的列集合（列索引的位集合）
  // 返回值：Double类型，表示指定列集合的不同值数量，如果无法确定则返回null
  // 实现机制：1) 检查缓存 2) 检测循环依赖 3) 调用实际计算方法 4) 缓存结果
  public java.lang.Double getPopulationSize(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq,
      org.apache.calcite.util.ImmutableBitSet a2) {
    // 处理委托类型的关系表达式，获取实际的底层关系表达式
    // DelegatingMetadataRel是一个包装器类型，用于在优化过程中添加额外的元数据信息
    // 通过while循环层层解包，直到找到非委托类型的实际RelNode，确保后续计算基于真实的节点
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) {
      // 获取委托关系表达式的实际底层节点，替换当前r变量继续循环
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel();
    }
    // 声明缓存键变量，用于在元数据缓存中查找或存储计算结果
    final Object key;
    // 构建缓存键：使用FlatLists.of创建包含方法键和参数的复合键
    // methodKey0标识是getPopulationSize方法，NullSentinel.mask(a2)处理参数a2（可能是null）
    // FlatLists.of创建一个不可变的列表对象，作为缓存键的组成部分
    key = org.apache.calcite.runtime.FlatLists.of(methodKey0, org.apache.calcite.rel.metadata.NullSentinel.mask(a2));
    // 从元数据缓存中查找是否已经计算过该RelNode在指定参数下的PopulationSize
    // mq.map是一个二维缓存，第一维是RelNode，第二维是方法参数组合的key
    final Object v = mq.map.get(r, key);
    // 检查缓存中是否已存在结果
    if (v != null) {
      // 检查缓存值是否为ACTIVE标记，表示当前正在计算中（循环依赖检测）
      // NullSentinel.ACTIVE是一个特殊标记，表示该元数据正在被计算但尚未完成
      // 如果发现ACTIVE标记，说明存在循环调用，抛出CyclicMetadataException异常
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) {
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException();
      }
      // 检查缓存值是否为INSTANCE标记，表示计算结果为null
      // NullSentinel.INSTANCE是一个特殊标记，用于在缓存中表示null值（因为缓存底层可能不支持null）
      // 如果缓存值为INSTANCE，返回null表示该RelNode的PopulationSize无法确定
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) {
        return null;
      }
      // 缓存命中，返回缓存中的计算结果（强制转换为Double类型）
      // v是实际的PopulationSize值（Double对象），直接返回即可
      return (java.lang.Double) v;
    }
    // 缓存未命中，在开始计算前先将缓存值设置为ACTIVE，标记为计算中状态
    // 这样可以检测循环依赖：如果计算过程中再次调用getPopulationSize(r, ...)，会检测到ACTIVE并抛出异常
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE);
    // 使用try-catch块保护计算过程，确保异常时能清理缓存状态
    try {
      // 调用内部方法getPopulationSize_进行实际的PopulationSize计算
      // getPopulationSize_方法会根据RelNode的具体类型调用provider0中对应的计算方法
      final java.lang.Double x = getPopulationSize_(r, mq, a2);
      // 计算成功，将结果存入缓存
      // NullSentinel.mask(x)处理null值，如果x为null则存储INSTANCE标记，否则存储x本身
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x));
      // 返回计算结果
      return x;
    } catch (java.lang.Exception e) {
      // 计算过程中发生异常，清理该RelNode的所有缓存条目
      // 清理缓存是为了避免缓存ACTIVE状态或部分计算结果，影响后续查询
      // row(r)获取该RelNode的所有缓存键值对，clear()清除所有条目
      mq.map.row(r).clear();
      // 重新抛出异常，让上层调用者处理
      throw e;
    }
  }

  // 内部方法：根据RelNode的具体类型调用对应的PopulationSize计算方法
  // 参数r：待查询的关系表达式(RelNode)
  // 参数mq：RelMetadataQuery元数据查询上下文对象
  // 参数a2：ImmutableBitSet对象，表示要统计PopulationSize的列集合
  // 返回值：Double类型，表示指定列集合的不同值数量
  // 该方法使用if-else链进行类型检查和分发，根据r的实际类型调用provider0中对应重载方法
  private java.lang.Double getPopulationSize_(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq,
      org.apache.calcite.util.ImmutableBitSet a2) {
    // 检查r是否为Aggregate（聚合）节点类型
    // Aggregate节点执行GROUP BY聚合操作，PopulationSize需要考虑分组列和聚合函数
    if (r instanceof org.apache.calcite.rel.core.Aggregate) {
      // 调用provider0中针对Aggregate类型的PopulationSize计算方法
      // 强制类型转换后传入，确保调用正确的重载方法
      return provider0.getPopulationSize((org.apache.calcite.rel.core.Aggregate) r, mq, a2);
    } else if (r instanceof org.apache.calcite.rel.core.Exchange) {
      // 检查r是否为Exchange（数据交换）节点类型
      // Exchange节点用于数据重分布（如广播、分区），PopulationSize通常与输入相同
      return provider0.getPopulationSize((org.apache.calcite.rel.core.Exchange) r, mq, a2);
    } else if (r instanceof org.apache.calcite.rel.core.Filter) {
      // 检查r是否为Filter（过滤）节点类型
      // Filter节点执行WHERE条件过滤，PopulationSize可能小于输入（取决于过滤条件的选择性）
      return provider0.getPopulationSize((org.apache.calcite.rel.core.Filter) r, mq, a2);
    } else if (r instanceof org.apache.calcite.rel.core.Join) {
      // 检查r是否为Join（连接）节点类型
      // Join节点执行表连接操作，PopulationSize取决于连接类型和连接条件
      return provider0.getPopulationSize((org.apache.calcite.rel.core.Join) r, mq, a2);
    } else if (r instanceof org.apache.calcite.rel.core.Project) {
      // 检查r是否为Project（投影）节点类型
      // Project节点执行列选择和表达式计算，PopulationSize在列集合不变时通常与输入相同
      return provider0.getPopulationSize((org.apache.calcite.rel.core.Project) r, mq, a2);
    } else if (r instanceof org.apache.calcite.rel.core.Sort) {
      // 检查r是否为Sort（排序）节点类型
      // Sort节点执行ORDER BY排序，PopulationSize与输入完全相同（排序不改变行数或列值）
      return provider0.getPopulationSize((org.apache.calcite.rel.core.Sort) r, mq, a2);
    } else if (r instanceof org.apache.calcite.rel.core.TableModify) {
      // 检查r是否为TableModify（表修改）节点类型
      // TableModify节点执行INSERT/UPDATE/DELETE操作，PopulationSize取决于操作类型和影响行数
      return provider0.getPopulationSize((org.apache.calcite.rel.core.TableModify) r, mq, a2);
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) {
      // 检查r是否为TableScan（表扫描）节点类型
      // TableScan节点读取数据表，PopulationSize通常来自表统计信息或采样估计
      return provider0.getPopulationSize((org.apache.calcite.rel.core.TableScan) r, mq, a2);
    } else if (r instanceof org.apache.calcite.rel.core.Union) {
      // 检查r是否为Union（联合）节点类型
      // Union节点执行UNION ALL操作，PopulationSize通常为各输入PopulationSize之和（可能有重复）
      return provider0.getPopulationSize((org.apache.calcite.rel.core.Union) r, mq, a2);
    } else if (r instanceof org.apache.calcite.rel.core.Values) {
      // 检查r是否为Values（值）节点类型
      // Values节点包含常量值列表，PopulationSize可以通过分析常量值准确计算
      return provider0.getPopulationSize((org.apache.calcite.rel.core.Values) r, mq, a2);
    } else if (r instanceof org.apache.calcite.rel.RelNode) {
      // 兜底处理：如果r是RelNode类型但不是上述任何具体子类
      // 这是一个catch-all处理器，用于处理未明确列出的RelNode类型
      // 建议为特定类型创建专门的处理器以提高准确性
      return provider0.getPopulationSize((org.apache.calcite.rel.RelNode) r, mq, a2);
    } else {
      // 异常处理：r的类型不是RelNode或其任何已知子类
      // 这表明传入了一个不支持的类型，可能是编程错误或扩展问题
      // 抛出IllegalArgumentException并提供详细错误信息，包括方法和参数类型
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.lang.Double org.apache.calcite.rel.metadata.BuiltInMetadata$PopulationSize$Handler.getPopulationSize(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery,org.apache.calcite.util.ImmutableBitSet)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler");
    }
  }

}
