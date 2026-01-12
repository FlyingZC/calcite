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
package org.apache.calcite.rel.metadata.janino; // 声明包名，该类位于janino子包中，janino是Calcite使用的运行时Java编译器

// GeneratedMetadata_MaxRowCountHandler类：这是一个由Janino代码生成器自动生成的元数据处理器类
// 作用：实现BuiltInMetadata.MaxRowCount.Handler接口，用于处理关系代数节点的最大行数（MaxRowCount）元数据查询
// 最大行数元数据：表示一个关系节点可能产生的最大行数，用于查询优化器进行代价估算和计划选择
// 该类通过缓存机制提高元数据查询性能，避免重复计算，并支持检测循环依赖
// 该类是一个final类，不允许被继承，确保生成的代码不会被意外修改
public final class GeneratedMetadata_MaxRowCountHandler
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.MaxRowCount.Handler { // 实现MaxRowCount.Handler接口，提供获取最大行数的能力
  // methodKey0：方法缓存键，用于在元数据缓存中标识getMaxRowCount方法的计算结果
  // 类型：Object，实际是DescriptiveCacheKey类型，包含方法的描述信息
  // 作用：作为缓存的键值，用于存储和检索特定关系节点的最大行数计算结果
  // 使用场景：在getMaxRowCount方法中，使用这个键从RelMetadataQuery的map中获取或存储缓存值
  // 值："Double Handler.getMaxRowCount()"，描述了这是Handler接口的getMaxRowCount方法，返回Double类型
  private final Object methodKey0 = // 声明一个final成员变量，表示方法缓存键，final表示初始化后不可修改
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Double Handler.getMaxRowCount()"); // 创建DescriptiveCacheKey对象，传入方法签名描述字符串
  // provider0：元数据提供者，实际执行最大行数计算的实现对象
  // 类型：RelMdMaxRowCount，是Calcite内置的最大行数元数据提供者
  // 作用：包含各种关系节点类型（如Filter、Project、Join等）的最大行数计算逻辑
  // 访问权限：public final，外部可以访问但不可修改
  // 使用场景：在getMaxRowCount_方法中，根据关系节点的具体类型，调用provider0对应的重载方法进行计算
  public final org.apache.calcite.rel.metadata.RelMdMaxRowCount provider0; // 声明公共final成员变量，引用实际的元数据提供者对象
  // 构造方法：GeneratedMetadata_MaxRowCountHandler
  // 作用：初始化处理器，设置元数据提供者
  // 参数：provider0 - RelMdMaxRowCount类型的元数据提供者对象，包含各种节点类型的最大行数计算逻辑
  // 执行流程：将传入的provider0参数赋值给成员变量provider0，使处理器能够调用实际的计算方法
  public GeneratedMetadata_MaxRowCountHandler( // 构造方法声明，接收RelMdMaxRowCount类型的参数
      org.apache.calcite.rel.metadata.RelMdMaxRowCount provider0) { // 参数provider0：元数据提供者对象
    this.provider0 = provider0; // 将传入的provider0参数赋值给成员变量provider0，保存引用以便后续使用
  }
  // getDef方法：获取元数据定义
  // 作用：返回此处理器所处理的元数据的定义信息（MetadataDef对象）
  // 返回值：MetadataDef对象，包含元数据的名称、类型等信息
  // 使用场景：元数据系统使用此方法获取元数据的定义，用于注册和查找元数据处理器
  public org.apache.calcite.rel.metadata.MetadataDef getDef() { // 方法声明，返回MetadataDef类型
    return provider0.getDef(); // 委托给provider0对象，调用其getDef方法获取元数据定义
  }
  // getMaxRowCount方法：获取关系节点的最大行数（带缓存和循环检测）
  // 作用：查询指定关系节点的最大行数，使用缓存机制提高性能，并检测循环依赖
  // 参数：
  //   r - RelNode类型，要查询最大行数的关系节点
  //   mq - RelMetadataQuery类型，元数据查询上下文，包含缓存和其他元数据查询信息
  // 返回值：Double类型，表示最大行数，可能为null表示未知
  // 核心逻辑：
  //   1. 处理委托元数据关系节点，获取实际的委托节点
  //   2. 检查缓存，如果已计算则直接返回缓存值
  //   3. 检测循环依赖，如果发现循环则抛出异常
  //   4. 将当前计算标记为ACTIVE，防止循环
  //   5. 调用getMaxRowCount_进行实际计算
  //   6. 将计算结果存入缓存
  //   7. 发生异常时清理缓存
  public java.lang.Double getMaxRowCount( // 方法声明，返回Double类型的最大行数
      org.apache.calcite.rel.RelNode r, // 参数r：要查询的关系节点
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq：元数据查询上下文对象
    // 循环处理委托元数据关系节点
    // 作用：有些关系节点会委托给其他节点处理元数据，需要找到实际的委托节点
    // DelegatingMetadataRel：表示委托元数据的关系节点接口
    // 执行流程：如果r是委托节点，则获取其委托节点并继续检查，直到找到非委托节点
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) { // while循环：检查r是否为委托元数据关系节点
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 获取委托节点并赋值给r，继续循环检查
    }
    // 声明缓存键变量
    // 作用：用于存储从缓存中检索或存储计算结果的键
    final Object key; // 声明final类型的key变量
    key = methodKey0; // 将methodKey0赋值给key，使用预定义的方法缓存键
    // 从缓存中获取已计算的结果
    // 作用：检查是否已经计算过该关系节点的最大行数，避免重复计算
    // mq.map：元数据缓存映射表，键是(RelNode, Object)，值是计算结果
    // 执行流程：使用关系节点r和键key从缓存中查找值
    final Object v = mq.map.get(r, key); // 调用map的get方法，传入关系节点和键，获取缓存值
    // 检查缓存值是否存在
    if (v != null) { // 如果缓存值不为null，说明已经计算过
      // 检查是否为ACTIVE标记，表示正在计算中（检测循环依赖）
      // NullSentinel.ACTIVE：特殊标记值，表示该元数据正在被计算
      // 作用：如果发现当前节点正在被计算，说明存在循环依赖，需要抛出异常
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // 检查缓存值是否为ACTIVE标记
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出循环元数据异常，表示检测到循环依赖
      }
      // 检查是否为INSTANCE标记，表示计算结果为null
      // NullSentinel.INSTANCE：特殊标记值，用于在缓存中表示null值（因为缓存不允许真正的null）
      // 作用：区分"未计算"和"计算结果为null"两种情况
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // 检查缓存值是否为INSTANCE标记
        return null; // 返回null，表示最大行数未知
      }
      // 返回缓存中的实际计算结果
      // 作用：避免重复计算，直接使用已缓存的结果
      return (java.lang.Double) v; // 将缓存值强制转换为Double类型并返回
    }
    // 将当前计算标记为ACTIVE，表示正在计算中
    // 作用：防止循环依赖，如果后续计算再次需要该节点的元数据，会检测到ACTIVE标记并抛出异常
    // 执行流程：在缓存中放入ACTIVE标记，表示该节点正在被计算
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 调用map的put方法，放入ACTIVE标记
    // 执行实际计算，并处理异常
    try { // try块：捕获计算过程中可能发生的异常
      // 调用私有方法getMaxRowCount_进行实际计算
      // 作用：根据关系节点的具体类型，调用provider0对应的计算方法
      final java.lang.Double x = getMaxRowCount_(r, mq); // 调用getMaxRowCount_方法，传入关系节点和元数据查询上下文，获取计算结果
      // 将计算结果存入缓存
      // NullSentinel.mask(x)：将计算结果转换为可缓存的值（如果x为null则返回INSTANCE，否则返回x本身）
      // 作用：缓存计算结果，下次查询时可以直接使用
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 调用map的put方法，存储计算结果
      return x; // 返回计算结果
    } catch (java.lang.Exception e) { // catch块：捕获计算过程中发生的异常
      // 清理该关系节点的所有缓存
      // 作用：计算失败时，清理缓存以确保下次重新计算，避免使用错误或不完整的缓存值
      // 执行流程：获取该关系节点的缓存行并清空
      mq.map.row(r).clear(); // 调用map的row方法获取该节点的缓存行，然后调用clear方法清空
      throw e; // 重新抛出异常，让上层调用者处理
    }
  }

  // getMaxRowCount_方法：根据关系节点类型分发到具体的计算方法（内部实现方法）
  // 作用：根据关系节点的具体类型，调用provider0中对应的重载getMaxRowCount方法进行计算
  // 参数：
  //   r - RelNode类型，要计算最大行数的关系节点
  //   mq - RelMetadataQuery类型，元数据查询上下文
  // 返回值：Double类型，表示最大行数
  // 核心逻辑：使用if-else链检查关系节点的具体类型，调用provider0对应的类型特定方法
  // 设计模式：这是典型的访问者模式（Visitor Pattern）的简化实现，通过类型检查分发到不同的处理逻辑
  // 为什么需要这个方法：Java不支持方法重载的动态分发，需要在运行时检查类型并调用正确的方法
  private java.lang.Double getMaxRowCount_( // 私有方法声明，返回Double类型
      org.apache.calcite.rel.RelNode r, // 参数r：关系节点
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq：元数据查询上下文
    // 检查是否为EnumerableLimit节点
    // EnumerableLimit：可枚举的Limit操作，限制结果集行数
    // 最大行数逻辑：Limit的最大行数是其输入的最大行数与limit值的较小值
    if (r instanceof org.apache.calcite.adapter.enumerable.EnumerableLimit) { // 类型检查：是否为EnumerableLimit
      return provider0.getMaxRowCount((org.apache.calcite.adapter.enumerable.EnumerableLimit) r, mq); // 类型转换后调用provider0的EnumerableLimit重载方法
    } else if (r instanceof org.apache.calcite.plan.volcano.RelSubset) { // 检查是否为RelSubset节点
      // RelSubset：Volcano优化器中的关系子集，表示等价的关系节点集合
      // 最大行数逻辑：取子集中所有等价计划的最大行数的最小值（最优计划）
      return provider0.getMaxRowCount((org.apache.calcite.plan.volcano.RelSubset) r, mq); // 类型转换后调用provider0的RelSubset重载方法
    } else if (r instanceof org.apache.calcite.rel.core.Aggregate) { // 检查是否为Aggregate节点
      // Aggregate：聚合操作（GROUP BY、聚合函数）
      // 最大行数逻辑：聚合的最大行数不超过其输入的最大行数，具体取决于分组键的基数
      return provider0.getMaxRowCount((org.apache.calcite.rel.core.Aggregate) r, mq); // 类型转换后调用provider0的Aggregate重载方法
    } else if (r instanceof org.apache.calcite.rel.core.Calc) { // 检查是否为Calc节点
      // Calc：计算节点，类似于Project但用于优化器内部
      // 最大行数逻辑：Calc不改变行数，最大行数等于输入的最大行数
      return provider0.getMaxRowCount((org.apache.calcite.rel.core.Calc) r, mq); // 类型转换后调用provider0的Calc重载方法
    } else if (r instanceof org.apache.calcite.rel.core.Exchange) { // 检查是否为Exchange节点
      // Exchange：数据交换节点，用于分布式查询中的数据重分布
      // 最大行数逻辑：Exchange不改变行数，最大行数等于输入的最大行数
      return provider0.getMaxRowCount((org.apache.calcite.rel.core.Exchange) r, mq); // 类型转换后调用provider0的Exchange重载方法
    } else if (r instanceof org.apache.calcite.rel.core.Filter) { // 检查是否为Filter节点
      // Filter：过滤操作（WHERE条件）
      // 最大行数逻辑：Filter的最大行数不超过输入的最大行数，具体取决于过滤条件的选择率
      return provider0.getMaxRowCount((org.apache.calcite.rel.core.Filter) r, mq); // 类型转换后调用provider0的Filter重载方法
    } else if (r instanceof org.apache.calcite.rel.core.Intersect) { // 检查是否为Intersect节点
      // Intersect：交集操作（INTERSECT）
      // 最大行数逻辑：交集的最大行数不超过所有输入中最大行数的最小值
      return provider0.getMaxRowCount((org.apache.calcite.rel.core.Intersect) r, mq); // 类型转换后调用provider0的Intersect重载方法
    } else if (r instanceof org.apache.calcite.rel.core.Join) { // 检查是否为Join节点
      // Join：连接操作（INNER JOIN、LEFT JOIN等）
      // 最大行数逻辑：Join的最大行数通常不超过两个输入最大行数的乘积，具体取决于连接条件
      return provider0.getMaxRowCount((org.apache.calcite.rel.core.Join) r, mq); // 类型转换后调用provider0的Join重载方法
    } else if (r instanceof org.apache.calcite.rel.core.Minus) { // 检查是否为Minus节点
      // Minus：差集操作（EXCEPT）
      // 最大行数逻辑：差集的最大行数不超过第一个输入的最大行数
      return provider0.getMaxRowCount((org.apache.calcite.rel.core.Minus) r, mq); // 类型转换后调用provider0的Minus重载方法
    } else if (r instanceof org.apache.calcite.rel.core.Project) { // 检查是否为Project节点
      // Project：投影操作（SELECT列、计算表达式）
      // 最大行数逻辑：Project不改变行数，最大行数等于输入的最大行数
      return provider0.getMaxRowCount((org.apache.calcite.rel.core.Project) r, mq); // 类型转换后调用provider0的Project重载方法
    } else if (r instanceof org.apache.calcite.rel.core.Sample) { // 检查是否为Sample节点
      // Sample：采样操作（TABLESAMPLE）
      // 最大行数逻辑：Sample的最大行数取决于采样比例
      return provider0.getMaxRowCount((org.apache.calcite.rel.core.Sample) r, mq); // 类型转换后调用provider0的Sample重载方法
    } else if (r instanceof org.apache.calcite.rel.core.Sort) { // 检查是否为Sort节点
      // Sort：排序操作（ORDER BY）
      // 最大行数逻辑：Sort不改变行数，最大行数等于输入的最大行数
      return provider0.getMaxRowCount((org.apache.calcite.rel.core.Sort) r, mq); // 类型转换后调用provider0的Sort重载方法
    } else if (r instanceof org.apache.calcite.rel.core.TableModify) { // 检查是否为TableModify节点
      // TableModify：表修改操作（INSERT、UPDATE、DELETE）
      // 最大行数逻辑：取决于操作类型，DELETE可能返回受影响的行数
      return provider0.getMaxRowCount((org.apache.calcite.rel.core.TableModify) r, mq); // 类型转换后调用provider0的TableModify重载方法
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) { // 检查是否为TableScan节点
      // TableScan：表扫描操作（FROM表）
      // 最大行数逻辑：从表统计信息获取，如果没有统计信息则返回null（未知）
      return provider0.getMaxRowCount((org.apache.calcite.rel.core.TableScan) r, mq); // 类型转换后调用provider0的TableScan重载方法
    } else if (r instanceof org.apache.calcite.rel.core.Union) { // 检查是否为Union节点
      // Union：并集操作（UNION ALL）
      // 最大行数逻辑：Union的最大行数是所有输入最大行数的和（对于UNION ALL）
      return provider0.getMaxRowCount((org.apache.calcite.rel.core.Union) r, mq); // 类型转换后调用provider0的Union重载方法
    } else if (r instanceof org.apache.calcite.rel.core.Values) { // 检查是否为Values节点
      // Values：值常量操作（VALUES子句）
      // 最大行数逻辑：Values的最大行数就是其包含的行数
      return provider0.getMaxRowCount((org.apache.calcite.rel.core.Values) r, mq); // 类型转换后调用provider0的Values重载方法
    } else if (r instanceof org.apache.calcite.rel.RelNode) { // 检查是否为通用的RelNode节点
      // RelNode：所有关系节点的基类，这是catch-all处理
      // 最大行数逻辑：默认实现通常返回null（未知）
      // 作用：处理所有未在上述if-else链中明确列出的节点类型
      return provider0.getMaxRowCount((org.apache.calcite.rel.RelNode) r, mq); // 类型转换后调用provider0的RelNode重载方法
    } else { // 如果节点类型不匹配任何已知类型
      // 抛出非法参数异常
      // 作用：当遇到未知的关系节点类型时，提供明确的错误信息
      // 建议：错误信息建议用户创建一个catch-all（RelNode）处理器来处理未知类型
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.lang.Double org.apache.calcite.rel.metadata.BuiltInMetadata$MaxRowCount$Handler.getMaxRowCount(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出异常，包含方法签名和实际类型信息
    }
  }

} // 类结束
