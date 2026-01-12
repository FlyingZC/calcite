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
// 本包janino包含使用Janino编译器动态生成的元数据处理相关类
package org.apache.calcite.rel.metadata.janino;

/**
 * GeneratedMetadata_LowerBoundCostHandler - 下界成本处理器
 * 
 * 【类作用】：
 * 这是一个由Janino编译器动态生成的类，实现了BuiltInMetadata.LowerBoundCost.Handler接口
 * 主要用于处理关系表达式(RelNode)的下界成本(Lower Bound Cost)元数据计算
 * 
 * 下界成本是Volcano优化器中的一个重要概念，表示某个RelNode的最小可能成本
 * 优化器使用下界成本来剪枝搜索空间，避免探索明显次优的执行计划
 * 
 * 该类采用缓存机制来避免重复计算相同的元数据，提高优化器性能
 * 同时支持循环检测，防止元数据计算过程中出现无限递归
 * 
 * 【核心功能】：
 * 1. 提供getLowerBoundCost方法计算RelNode的下界成本
 * 2. 使用缓存机制存储计算结果，避免重复计算
 * 3. 支持循环依赖检测，防止元数据计算死循环
 * 4. 根据RelNode的实际类型分发到不同的处理方法
 * 
 * 【设计模式】：
 * - 委托模式：将实际的元数据计算委托给RelMdLowerBoundCost提供者
 * - 缓存模式：使用RelMetadataQuery的map缓存计算结果
 * - 模板方法：getLowerBoundCost提供缓存和循环检测框架，getLowerBoundCost_实现具体逻辑
 */
public final class GeneratedMetadata_LowerBoundCostHandler
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.LowerBoundCost.Handler {
  // methodKey0 - 方法缓存键
  // 作用：作为缓存键的第一部分，用于标识getLowerBoundCost方法
  // 使用DescriptiveCacheKey包装，包含方法签名的描述信息，便于调试和问题追踪
  // 该键与参数一起组成完整的缓存键，确保不同参数组合有独立的缓存条目
  private final Object methodKey0 =
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("RelOptCost Handler.getLowerBoundCost(RelNode, RelMetadataQuery, VolcanoPlanner)");
  // provider0 - 元数据提供者
  // 作用：持有RelMdLowerBoundCost实例，它是实际执行元数据计算的核心类
  // 该提供者包含各种RelNode类型的getLowerBoundCost方法实现
  // 通过委托模式，本类只负责缓存和分发逻辑，实际计算由provider0完成
  public final org.apache.calcite.rel.metadata.RelMdLowerBoundCost provider0;
  /**
   * 构造方法 - 初始化下界成本处理器
   * 
   * 【参数说明】：
   * @param provider0 - RelMdLowerBoundCost实例，负责实际的元数据计算逻辑
   *                  包含针对不同RelNode类型的getLowerBoundCost方法实现
   * 
   * 【构造逻辑】：
   * 1. 保存元数据提供者引用，用于后续委托调用
   * 2. methodKey0在声明时初始化，标识getLowerBoundCost方法
   * 
   * 【使用场景】：
   * 在RelMetadataQuery初始化时创建，用于处理所有RelNode的下界成本查询
   */
  public GeneratedMetadata_LowerBoundCostHandler(
      org.apache.calcite.rel.metadata.RelMdLowerBoundCost provider0) {
    // 保存元数据提供者引用，后续所有实际计算都委托给这个provider
    this.provider0 = provider0;
  }
  /**
   * getDef - 获取元数据定义
   * 
   * 【方法作用】：
   * 返回LowerBoundCost元数据的定义信息，包括元数据名称、方法签名等
   * 
   * 【返回值】：
   * @return MetadataDef - 元数据定义对象，描述LowerBoundCost元数据的元信息
   * 
   * 【实现逻辑】：
   * 直接委托给provider0.getDef()，因为定义信息由provider维护
   * 
   * 【使用场景】：
   * 元数据系统注册和查询时使用，用于标识和描述元数据类型
   */
  public org.apache.calcite.rel.metadata.MetadataDef getDef() {
    // 委托给provider获取元数据定义，保持定义信息的一致性
    return provider0.getDef();
  }
  /**
   * getLowerBoundCost - 获取关系节点的下界成本
   * 
   * 【方法作用】：
   * 计算并返回指定RelNode的下界成本，使用缓存机制避免重复计算
   * 下界成本表示执行该RelNode所需的最小可能成本，用于优化器剪枝
   * 
   * 【参数说明】：
   * @param r - RelNode，要计算下界成本的关系节点
   * @param mq - RelMetadataQuery，元数据查询上下文，包含缓存和其他元数据访问方法
   * @param a2 - VolcanoPlanner，火山优化器实例，用于访问优化器特定的成本信息
   * 
   * 【返回值】：
   * @return RelOptCost - 下界成本对象，包含CPU、IO、内存等成本指标
   *                     如果无法计算则返回null
   * 
   * 【核心逻辑流程】：
   * 1. 解包委托节点：如果r是DelegatingMetadataRel，获取其实际的委托节点
   * 2. 构建缓存键：使用methodKey0和参数a2生成唯一缓存键
   * 3. 检查缓存：从mq.map中查找是否已有计算结果
   * 4. 缓存命中处理：
   *    - 如果是ACTIVE标记，说明正在计算中，抛出循环依赖异常
   *    - 如果是INSTANCE标记，说明结果为null，返回null
   *    - 否则返回缓存的成本对象
   * 5. 缓存未命中处理：
   *    - 先放入ACTIVE标记，表示开始计算
   *    - 调用getLowerBoundCost_进行实际计算
   *    - 将结果放入缓存（使用mask处理null值）
   *    - 返回计算结果
   * 6. 异常处理：如果计算过程中抛出异常，清除该RelNode的所有缓存条目
   * 
   * 【缓存机制】：
   * - 使用双键缓存：RelNode作为第一键，(methodKey0, 参数)作为第二键
   * - ACTIVE标记：用于检测循环依赖，防止无限递归
   * - NullSentinel.INSTANCE：用于缓存null结果，避免重复计算
   * 
   * 【循环检测】：
   * 当元数据A的计算依赖元数据B，而B的计算又依赖A时，会抛出CyclicMetadataException
   * 这通过ACTIVE标记实现：在计算前设置ACTIVE，再次遇到时说明有循环
   * 
   * 【性能优化】：
   * 缓存机制避免了相同参数的重复计算，显著提升优化器性能
   * 尤其在复杂的查询优化中，同一RelNode可能被多次查询成本
   */
  public org.apache.calcite.plan.RelOptCost getLowerBoundCost(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq,
      org.apache.calcite.plan.volcano.VolcanoPlanner a2) {
    // 解包委托节点：DelegatingMetadataRel是包装器，需要获取其内部的实际节点
    // 循环解包直到找到非委托节点，确保操作的是真实的RelNode
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) {
      // 获取委托节点，这是实际需要计算成本的RelNode
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel();
    }
    // 声明缓存键变量，用于存储生成的缓存键
    final Object key;
    // 构建缓存键：使用FlatLists.of创建不可变列表
    // methodKey0标识方法，NullSentinel.mask(a2)处理参数（包括null值）
    // 这样确保不同参数组合有独立的缓存条目
    key = org.apache.calcite.runtime.FlatLists.of(methodKey0, org.apache.calcite.rel.metadata.NullSentinel.mask(a2));
    // 从缓存中查找是否已有计算结果
    // mq.map是RelMetadataQuery中的缓存映射，以RelNode和key为双键
    final Object v = mq.map.get(r, key);
    // 如果缓存中有值，进行判断处理
    if (v != null) {
      // 如果是ACTIVE标记，说明当前正在计算这个元数据
      // 再次遇到ACTIVE表示存在循环依赖，抛出异常防止无限递归
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) {
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException();
      }
      // 如果是INSTANCE标记，表示之前计算的结果是null
      // 使用INSTANCE标记是因为Map不能存储null值
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) {
        return null;
      }
      // 缓存中有实际结果，直接返回
      // 强制转换为RelOptCost类型
      return (org.apache.calcite.plan.RelOptCost) v;
    }
    // 缓存未命中，先放入ACTIVE标记表示开始计算
    // 这样可以在递归调用时检测循环依赖
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE);
    // 使用try-catch确保异常时清理缓存
    try {
      // 调用内部方法getLowerBoundCost_进行实际计算
      // 该方法根据RelNode类型分发到不同的处理逻辑
      final org.apache.calcite.plan.RelOptCost x = getLowerBoundCost_(r, mq, a2);
      // 计算完成，将结果存入缓存
      // NullSentinel.mask处理null值：null转为INSTANCE，非null直接存储
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x));
      // 返回计算结果
      return x;
    } catch (java.lang.Exception e) {
      // 计算过程中抛出异常，清除该RelNode的所有缓存条目
      // 避免部分计算结果影响后续查询
      mq.map.row(r).clear();
      // 重新抛出异常，由上层处理
      throw e;
    }
  }

  /**
   * getLowerBoundCost_ - 内部方法：执行下界成本计算
   * 
   * 【方法作用】：
   * 根据RelNode的实际类型，调用provider0中对应的getLowerBoundCost方法
   * 这是实际执行元数据计算的方法，不包含缓存逻辑
   * 
   * 【参数说明】：
   * @param r - RelNode，要计算下界成本的关系节点（已解包，非DelegatingMetadataRel）
   * @param mq - RelMetadataQuery，元数据查询上下文
   * @param a2 - VolcanoPlanner，火山优化器实例
   * 
   * 【返回值】：
   * @return RelOptCost - 计算得到的下界成本，可能为null
   * 
   * 【核心逻辑流程】：
   * 1. 类型检查：判断r的具体类型
   * 2. 方法分发：
   *    - 如果是RelSubset，调用针对RelSubset的getLowerBoundCost方法
   *    - 如果是普通RelNode，调用通用的getLowerBoundCost方法
   *    - 否则抛出异常，表示没有对应的处理方法
   * 
   * 【类型分发机制】：
   * RelSubset是Volcano优化器中的特殊节点，表示一组等价的物理实现
   * 它的下界成本计算逻辑与普通RelNode不同，需要特殊处理
   * 
   * 【异常处理】：
   * 如果遇到未支持的RelNode类型，抛出IllegalArgumentException
   * 异常消息包含方法签名和实际类型，便于调试
   * 建议创建catch-all(RelNode)处理器来处理所有类型
   * 
   * 【设计考虑】：
   * 使用if-else链而不是多态，因为这是动态生成的代码
   * 生成器会根据RelMdLowerBoundCost中的方法生成对应的类型检查分支
   * 
   * 【性能考虑】：
   * 类型检查使用instanceof，性能开销很小
   * 类型判断顺序由生成器决定，通常按类型继承层次排列
   */
  private org.apache.calcite.plan.RelOptCost getLowerBoundCost_(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq,
      org.apache.calcite.plan.volcano.VolcanoPlanner a2) {
    // 检查是否为RelSubset类型
    // RelSubset是Volcano优化器中表示等价RelNode集合的特殊节点
    // 它的下界成本计算需要考虑所有等价实现的最小成本
    if (r instanceof org.apache.calcite.plan.volcano.RelSubset) {
      // 调用provider0中针对RelSubset的getLowerBoundCost方法
      // 该方法会计算RelSubset中所有等价RelNode的最小下界成本
      return provider0.getLowerBoundCost((org.apache.calcite.plan.volcano.RelSubset) r, mq, a2);
    } else if (r instanceof org.apache.calcite.rel.RelNode) {
      // 普通RelNode的通用处理
      // 调用provider0中针对RelNode基类的getLowerBoundCost方法
      // 这是catch-all处理器，适用于所有其他类型的RelNode
      return provider0.getLowerBoundCost((org.apache.calcite.rel.RelNode) r, mq, a2);
    } else {
      // 遇到未支持的类型，抛出异常
      // 这种情况理论上不应该发生，因为所有RelNode都应该继承自RelNode基类
      // 但为了代码健壮性，还是添加了这个else分支
      throw new java.lang.IllegalArgumentException("No handler for method [public abstract org.apache.calcite.plan.RelOptCost org.apache.calcite.rel.metadata.BuiltInMetadata$LowerBoundCost$Handler.getLowerBoundCost(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery,org.apache.calcite.plan.volcano.VolcanoPlanner)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler");
    }
  }

}
