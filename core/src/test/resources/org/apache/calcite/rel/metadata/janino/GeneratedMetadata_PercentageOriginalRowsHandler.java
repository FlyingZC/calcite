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
// 包声明：该类属于org.apache.calcite.rel.metadata.janino包，是Calcite框架中用于元数据处理的Janino编译器生成的代码
package org.apache.calcite.rel.metadata.janino;

// 这是一个自动生成的类，实现了BuiltInMetadata.PercentageOriginalRows.Handler接口
// 类的作用：作为PercentageOriginalRows元数据的处理器，负责计算关系代数节点（RelNode）的原始行百分比
// 原始行百分比表示该RelNode输出的行数相对于其输入行数的比例，用于查询优化器估算数据量
public final class GeneratedMetadata_PercentageOriginalRowsHandler
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.PercentageOriginalRows.Handler {
  // 成员变量：方法缓存键，用于在元数据缓存中标识getPercentageOriginalRows方法
  // 使用DescriptiveCacheKey包装方法签名，便于调试和识别缓存项
  private final Object methodKey0 =
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Double Handler.getPercentageOriginalRows()");
  // 成员变量：实际的元数据提供者，包含各种RelNode类型的getPercentageOriginalRows实现
  // 这个provider0是RelMdPercentageOriginalRows$RelMdPercentageOriginalRowsHandler类型的实例，委托给具体的实现类
  public final org.apache.calcite.rel.metadata.RelMdPercentageOriginalRows$RelMdPercentageOriginalRowsHandler provider0;
  // 构造方法：接收一个RelMdPercentageOriginalRows$RelMdPercentageOriginalRowsHandler实例作为参数
  // 参数provider0：实际的元数据提供者，包含各种RelNode类型的元数据计算逻辑
  public GeneratedMetadata_PercentageOriginalRowsHandler(
      org.apache.calcite.rel.metadata.RelMdPercentageOriginalRows$RelMdPercentageOriginalRowsHandler provider0) {
    // 将传入的provider0赋值给成员变量，保存元数据提供者的引用
    this.provider0 = provider0;
  }
  // 方法：获取元数据定义信息
  // 返回值：MetadataDef对象，包含PercentageOriginalRows元数据的定义信息（如名称、返回类型等）
  public org.apache.calcite.rel.metadata.MetadataDef getDef() {
    // 委托给provider0获取元数据定义
    return provider0.getDef();
  }
  // 方法：获取RelNode的原始行百分比（带缓存和循环检测）
  // 参数r：关系代数节点（RelNode），表示要计算元数据的关系表达式
  // 参数mq：元数据查询上下文（RelMetadataQuery），包含元数据缓存和其他查询信息
  // 返回值：Double类型，表示该RelNode输出的行数相对于输入行数的百分比，范围[0.0, 1.0]
  public java.lang.Double getPercentageOriginalRows(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) {
    // 循环处理：如果RelNode是DelegatingMetadataRel类型，则获取其委托的RelNode
    // DelegatingMetadataRel是一个包装器，需要逐层解包直到找到实际的RelNode
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) {
      // 获取委托的RelNode，继续循环检查
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel();
    }
    // 声明缓存键变量
    final Object key;
    // 将methodKey0赋值给key，用于在缓存中查找元数据值
    key = methodKey0;
    // 从元数据缓存中查找该RelNode和方法键对应的缓存值
    // mq.map是一个RelMetadataQuery内部的缓存Map，键是(RelNode, methodKey)，值是计算结果
    final Object v = mq.map.get(r, key);
    // 如果缓存中存在该值（v不为null），则进行缓存命中处理
    if (v != null) {
      // 检查是否是ACTIVE标记，表示正在计算中，检测到循环依赖
      // 如果是ACTIVE，说明在计算该元数据的过程中又请求了该元数据，形成了循环
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) {
        // 抛出循环元数据异常，防止无限递归
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException();
      }
      // 检查是否是INSTANCE标记，表示缓存的值是null
      // NullSentinel.INSTANCE用于在Map中表示null值（因为Map不允许null值）
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) {
        // 返回null，表示该RelNode的原始行百分比无法计算或不存在
        return null;
      }
      // 缓存命中，返回缓存中的Double值
      return (java.lang.Double) v;
    }
    // 缓存未命中，在开始计算前先放入ACTIVE标记，表示正在计算中
    // 这样可以检测循环依赖：如果在计算过程中又请求该元数据，会检测到ACTIVE并抛出异常
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE);
    // 使用try-catch块，确保计算失败时清理缓存
    try {
      // 调用内部方法getPercentageOriginalRows_进行实际的元数据计算
      // 该方法会根据r的具体类型调用provider0中对应的实现
      final java.lang.Double x = getPercentageOriginalRows_(r, mq);
      // 计算完成后，将结果放入缓存
      // NullSentinel.mask(x)会将null转换为NullSentinel.INSTANCE，非null值保持不变
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x));
      // 返回计算结果
      return x;
    } catch (java.lang.Exception e) {
      // 如果计算过程中发生异常，清理该RelNode的所有缓存项
      // 这是为了避免缓存中残留ACTIVE标记或部分计算结果
      mq.map.row(r).clear();
      // 重新抛出异常，让调用者处理
      throw e;
    }
  }

  // 私有方法：实际计算RelNode的原始行百分比（根据具体类型分发）
  // 参数r：关系代数节点（RelNode），已经解包到实际类型
  // 参数mq：元数据查询上下文（RelMetadataQuery）
  // 返回值：Double类型，表示该RelNode的原始行百分比
  private java.lang.Double getPercentageOriginalRows_(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) {
    // 类型检查：如果是聚合操作（Aggregate）
    // Aggregate节点会对输入数据进行分组和聚合，通常会减少行数
    if (r instanceof org.apache.calcite.rel.core.Aggregate) {
      // 调用provider0中专门处理Aggregate类型的getPercentageOriginalRows方法
      // 传入强制类型转换后的Aggregate对象和元数据查询上下文
      return provider0.getPercentageOriginalRows((org.apache.calcite.rel.core.Aggregate) r, mq);
    // 类型检查：如果是连接操作（Join）
    // Join节点会将两个表连接，输出行数取决于连接条件和输入行数
    } else if (r instanceof org.apache.calcite.rel.core.Join) {
      // 调用provider0中专门处理Join类型的getPercentageOriginalRows方法
      return provider0.getPercentageOriginalRows((org.apache.calcite.rel.core.Join) r, mq);
    // 类型检查：如果是表扫描（TableScan）
    // TableScan节点直接从数据源读取数据，通常返回原始行数的100%
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) {
      // 调用provider0中专门处理TableScan类型的getPercentageOriginalRows方法
      return provider0.getPercentageOriginalRows((org.apache.calcite.rel.core.TableScan) r, mq);
    // 类型检查：如果是联合操作（Union）
    // Union节点合并多个输入的数据，输出行数通常等于各输入行数之和
    } else if (r instanceof org.apache.calcite.rel.core.Union) {
      // 调用provider0中专门处理Union类型的getPercentageOriginalRows方法
      return provider0.getPercentageOriginalRows((org.apache.calcite.rel.core.Union) r, mq);
    // 类型检查：如果是任意RelNode类型（兜底情况）
    // 这是一个catch-all处理器，处理所有未明确列出的RelNode类型
    } else if (r instanceof org.apache.calcite.rel.RelNode) {
      // 调用provider0中处理通用RelNode类型的getPercentageOriginalRows方法
      return provider0.getPercentageOriginalRows((org.apache.calcite.rel.RelNode) r, mq);
    // 如果不是任何已知的RelNode类型，抛出异常
    } else {
      // 抛出IllegalArgumentException，说明没有找到匹配的处理器
      // 异常信息中包含方法签名和实际的RelNode类型，帮助开发者定位问题
      // 建议创建一个catch-all (RelNode)处理器来处理这种情况
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.lang.Double org.apache.calcite.rel.metadata.BuiltInMetadata$PercentageOriginalRows$Handler.getPercentageOriginalRows(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler");
    }
  }

}
