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
// 声明包名，该类属于 Calcite 框架中元数据处理相关的 janino 包
package org.apache.calcite.rel.metadata.janino;

// GeneratedMetadata_MinRowCountHandler 类：这是一个由 Janino 代码生成器自动生成的元数据处理器类
// 该类实现了 BuiltInMetadata.MinRowCount.Handler 接口，用于处理关系代数表达式（RelNode）的最小行数（MinRowCount）元数据查询
// Calcite 使用元数据系统来存储和查询关系算子的统计信息，最小行数是优化器进行代价估算的重要依据
// 该类采用了缓存机制来避免重复计算，并提供了循环检测以防止元数据计算过程中的无限递归
// 该类是一个不可变（final）类，不能被继承，确保了元数据处理器的稳定性
public final class GeneratedMetadata_MinRowCountHandler
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.MinRowCount.Handler {
  // methodKey0：用于缓存查找的键对象，标识了当前处理的元数据方法（getMinRowCount）
  // 该键使用 DescriptiveCacheKey 包装，包含了方法的描述信息 "Double Handler.getMinRowCount()"
  // 在元数据缓存系统中，每个元数据方法都需要一个唯一的键来存储和检索计算结果
  // final 修饰符确保该键在对象创建后不能被修改，保证了缓存键的稳定性
  private final Object methodKey0 =
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Double Handler.getMinRowCount()");
  // provider0：实际的元数据提供者对象，类型为 RelMdMinRowCount
  // 该对象包含了针对不同类型 RelNode 的 getMinRowCount 方法的具体实现
  // public 修饰符允许外部访问这个提供者，final 修饰符确保提供者在对象创建后不会被修改
  // 这是委托模式的典型应用：GeneratedMetadata_MinRowCountHandler 负责缓存和调用委托，provider0 负责实际的元数据计算逻辑
  public final org.apache.calcite.rel.metadata.RelMdMinRowCount provider0;
  // 构造方法：创建 GeneratedMetadata_MinRowCountHandler 实例
  // 参数 provider0：RelMdMinRowCount 类型的元数据提供者对象，包含实际的元数据计算实现
  // 该构造方法将传入的提供者对象保存到成员变量 provider0 中，供后续方法调用使用
  // 这种设计允许在运行时动态注入不同的元数据提供者实现，提高了系统的灵活性
  public GeneratedMetadata_MinRowCountHandler(
      org.apache.calcite.rel.metadata.RelMdMinRowCount provider0) {
    // 将传入的 provider0 参数赋值给成员变量 this.provider0
    // this 关键字用于区分成员变量和构造方法参数
    this.provider0 = provider0;
  }
  // getDef 方法：获取元数据定义信息
  // 返回值：MetadataDef 类型的对象，包含了 MinRowCount 元数据的定义信息（如名称、返回类型等）
  // 该方法直接委托给 provider0.getDef()，说明元数据定义由提供者维护
  // 元数据定义是 Calcite 元数据系统的核心概念，用于描述元数据的类型和特征
  public org.apache.calcite.rel.metadata.MetadataDef getDef() {
    // 调用 provider0 的 getDef 方法并返回其结果
    // 这是一个简单的委托调用，实际的元数据定义由 RelMdMinRowCount 类提供
    return provider0.getDef();
  }
  // getMinRowCount 方法：获取指定关系节点的最小行数元数据（公共入口方法）
  // 参数 r：RelNode 类型的关系节点，表示要查询元数据的关系代数表达式（如表扫描、连接、过滤等）
  // 参数 mq：RelMetadataQuery 类型的元数据查询上下文对象，包含了元数据缓存和其他查询相关信息
  // 返回值：Double 类型的最小行数，可能为 null（表示无法确定最小行数）
  // 该方法实现了完整的缓存逻辑、循环检测和异常处理，是元数据查询的入口点
  public java.lang.Double getMinRowCount(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) {
    // while 循环：处理委托元数据关系节点（DelegatingMetadataRel）
    // DelegatingMetadataRel 是一种特殊的关系节点，它会将元数据查询委托给其内部的实际关系节点
    // 这个循环会一直遍历，直到找到非委托的关系节点，确保我们在正确的节点上计算元数据
    // 这种设计允许在关系树中插入代理节点，用于拦截或修改元数据查询行为
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) {
      // 将 r 强制转换为 DelegatingMetadataRel 类型，并调用其 getMetadataDelegateRel 方法获取实际的关系节点
      // 然后将 r 更新为实际的关系节点，继续循环检查
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel();
    }
    // 声明缓存键变量 key，用于在元数据缓存中查找或存储计算结果
    final Object key;
    // 将 methodKey0 赋值给 key，methodKey0 是在类初始化时创建的缓存键
    // 这里使用局部变量 key 是为了后续代码的可读性和一致性
    key = methodKey0;
    // 从元数据缓存中查找是否存在已计算的结果
    // mq.map 是一个双向映射（RelNode -> Key -> Value），用于存储元数据计算结果
    // 使用 r（关系节点）和 key（方法键）作为查询条件，查找对应的缓存值
    final Object v = mq.map.get(r, key);
    // if 语句：检查缓存中是否已存在计算结果（v != null）
    if (v != null) {
      // 内层 if：检查缓存值是否为 ACTIVE 标记
      // ACTIVE 标记表示当前元数据正在计算中，检测到这种情况说明存在循环依赖
      // 循环依赖是指元数据 A 的计算需要元数据 B，而元数据 B 的计算又需要元数据 A
      // 这种情况下必须抛出异常，否则会导致无限递归和栈溢出
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) {
        // 抛出循环元数据异常，通知调用者检测到循环依赖
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException();
      }
      // 内层 if：检查缓存值是否为 INSTANCE 标记
      // INSTANCE 标记表示缓存中存储的是 null 值（因为 HashMap 不能直接存储 null）
      // NullSentinel 是一个特殊的单例对象，用于在缓存中表示 null 值
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) {
        // 返回 null，表示无法确定该关系节点的最小行数
        return null;
      }
      // 如果缓存值既不是 ACTIVE 也不是 INSTANCE，说明是实际的计算结果
      // 将缓存值强制转换为 Double 类型并返回
      return (java.lang.Double) v;
    }
    // 如果缓存中没有找到结果，则在缓存中设置 ACTIVE 标记，表示开始计算该元数据
    // 这一步非常重要，它为后续的循环检测提供了标记
    // 在计算过程中，如果再次查询相同元数据，就会检测到 ACTIVE 标记并抛出循环异常
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE);
    // try-catch 块：执行实际的元数据计算，并处理可能的异常
    try {
      // 调用内部方法 getMinRowCount_ 进行实际的元数据计算
      // 该方法会根据关系节点的具体类型，调用 provider0 中对应的实现方法
      // 计算结果存储在局部变量 x 中
      final java.lang.Double x = getMinRowCount_(r, mq);
      // 将计算结果 x 存入缓存，使用 NullSentinel.mask 方法处理 null 值
      // NullSentinel.mask 方法会将 null 转换为 NullSentinel.INSTANCE，非 null 值保持不变
      // 这样可以确保缓存中不会存储真正的 null 值，避免与"未缓存"混淆
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x));
      // 返回计算结果 x
      return x;
    } catch (java.lang.Exception e) {
      // 如果在计算过程中发生异常，需要清理缓存中与该关系节点相关的所有元数据
      // 这是为了避免缓存中存储不完整或错误的数据
      // mq.map.row(r) 获取该关系节点在缓存中的行，clear() 清空该行的所有条目
      mq.map.row(r).clear();
      // 重新抛出异常，让调用者处理
      throw e;
    }
  }

  // getMinRowCount_ 方法：获取指定关系节点的最小行数元数据（内部实现方法）
  // 参数 r：RelNode 类型的关系节点，表示要查询元数据的关系代数表达式
  // 参数 mq：RelMetadataQuery 类型的元数据查询上下文对象
  // 返回值：Double 类型的最小行数，可能为 null
  // 该方法根据关系节点的具体类型，调用 provider0 中对应的重载方法
  // 这是典型的多态分发模式，通过 instanceof 检查和类型转换来实现方法分发
  private java.lang.Double getMinRowCount_(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) {
    // 检查关系节点 r 是否为 EnumerableLimit 类型（可枚举的限制算子）
    // EnumerableLimit 表示限制结果集行数的操作，类似于 SQL 中的 LIMIT 子句
    if (r instanceof org.apache.calcite.adapter.enumerable.EnumerableLimit) {
      // 调用 provider0 中针对 EnumerableLimit 类型的 getMinRowCount 方法
      // 将 r 强制转换为 EnumerableLimit 类型并传递给方法
      return provider0.getMinRowCount((org.apache.calcite.adapter.enumerable.EnumerableLimit) r, mq);
    } else if (r instanceof org.apache.calcite.plan.volcano.RelSubset) {
      // 检查关系节点 r 是否为 RelSubset 类型（关系子集）
      // RelSubset 是 Volcano 优化器中的概念，表示等价的关系表达式集合
      return provider0.getMinRowCount((org.apache.calcite.plan.volcano.RelSubset) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Aggregate) {
      // 检查关系节点 r 是否为 Aggregate 类型（聚合算子）
      // Aggregate 表示 GROUP BY 聚合操作，如 SUM、COUNT、AVG 等
      return provider0.getMinRowCount((org.apache.calcite.rel.core.Aggregate) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Calc) {
      // 检查关系节点 r 是否为 Calc 类型（计算算子）
      // Calc 表示投影和过滤的组合操作，类似于 Project + Filter
      return provider0.getMinRowCount((org.apache.calcite.rel.core.Calc) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Exchange) {
      // 检查关系节点 r 是否为 Exchange 类型（交换算子）
      // Exchange 表示数据重新分布操作，常用于并行查询中的数据重分区
      return provider0.getMinRowCount((org.apache.calcite.rel.core.Exchange) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Filter) {
      // 检查关系节点 r 是否为 Filter 类型（过滤算子）
      // Filter 表示 WHERE 条件过滤操作，用于根据谓词过滤行
      return provider0.getMinRowCount((org.apache.calcite.rel.core.Filter) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Intersect) {
      // 检查关系节点 r 是否为 Intersect 类型（交集算子）
      // Intersect 表示集合交集操作，类似于 SQL 中的 INTERSECT
      return provider0.getMinRowCount((org.apache.calcite.rel.core.Intersect) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Join) {
      // 检查关系节点 r 是否为 Join 类型（连接算子）
      // Join 表示表连接操作，如 INNER JOIN、LEFT JOIN 等
      return provider0.getMinRowCount((org.apache.calcite.rel.core.Join) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Minus) {
      // 检查关系节点 r 是否为 Minus 类型（差集算子）
      // Minus 表示集合差集操作，类似于 SQL 中的 EXCEPT 或 MINUS
      return provider0.getMinRowCount((org.apache.calcite.rel.core.Minus) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Project) {
      // 检查关系节点 r 是否为 Project 类型（投影算子）
      // Project 表示列选择和计算操作，类似于 SQL 中的 SELECT 子句
      return provider0.getMinRowCount((org.apache.calcite.rel.core.Project) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Sample) {
      // 检查关系节点 r 是否为 Sample 类型（采样算子）
      // Sample 表示表采样操作，用于随机抽取部分数据进行查询
      return provider0.getMinRowCount((org.apache.calcite.rel.core.Sample) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Sort) {
      // 检查关系节点 r 是否为 Sort 类型（排序算子）
      // Sort 表示排序操作，类似于 SQL 中的 ORDER BY
      return provider0.getMinRowCount((org.apache.calcite.rel.core.Sort) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.TableModify) {
      // 检查关系节点 r 是否为 TableModify 类型（表修改算子）
      // TableModify 表示 INSERT、UPDATE、DELETE 等 DML 操作
      return provider0.getMinRowCount((org.apache.calcite.rel.core.TableModify) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) {
      // 检查关系节点 r 是否为 TableScan 类型（表扫描算子）
      // TableScan 表示从数据源（如表）读取数据的操作，是查询计划的叶子节点
      return provider0.getMinRowCount((org.apache.calcite.rel.core.TableScan) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Union) {
      // 检查关系节点 r 是否为 Union 类型（并集算子）
      // Union 表示集合并集操作，类似于 SQL 中的 UNION
      return provider0.getMinRowCount((org.apache.calcite.rel.core.Union) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Values) {
      // 检查关系节点 r 是否为 Values 类型（值算子）
      // Values 表示常量值集合，类似于 SQL 中的 VALUES 子句
      return provider0.getMinRowCount((org.apache.calcite.rel.core.Values) r, mq);
    } else if (r instanceof org.apache.calcite.rel.RelNode) {
      // 兜底情况：检查关系节点 r 是否为 RelNode 类型（通用关系节点）
      // 这个分支会匹配所有 RelNode 的子类，作为默认的处理方式
      // 它必须放在所有具体类型检查之后，作为 catch-all 处理器
      return provider0.getMinRowCount((org.apache.calcite.rel.RelNode) r, mq);
    } else {
      // 如果关系节点 r 不匹配任何已知类型，抛出 IllegalArgumentException 异常
      // 这种情况通常表示遇到了未知的关系节点类型，需要添加对应的处理分支
      // 异常消息包含了方法签名和实际类型信息，便于调试
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.lang.Double org.apache.calcite.rel.metadata.BuiltInMetadata$MinRowCount$Handler.getMinRowCount(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler");
    }
  }

}
