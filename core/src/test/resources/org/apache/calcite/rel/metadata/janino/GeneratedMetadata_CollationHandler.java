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
package org.apache.calcite.rel.metadata.janino; // 包声明：该类属于Calcite项目的元数据janino包，janino是Calcite用于运行时代码生成的工具

// GeneratedMetadata_CollationHandler: 这是一个自动生成的元数据处理器类，专门用于处理Collation（排序规则）元数据查询
// 该类实现了BuiltInMetadata.Collation.Handler接口，为各种RelNode类型提供排序规则信息的查询能力
// Collation元数据描述了关系代数操作符输出的数据排序特性，例如哪些字段是已排序的、排序方向等
// 该类通过RelMdCollation提供者来实际计算不同RelNode类型的排序规则，并使用缓存机制提高性能
// 该类是在编译时通过Janino代码生成器自动生成的，包含了Calcite支持的所有RelNode类型的Collation元数据处理逻辑
public final class GeneratedMetadata_CollationHandler // 类声明：这是一个final类，表示不能被继承，它是自动生成的Collation元数据处理器
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.Collation.Handler { // 实现Collation.Handler接口，该接口定义了查询Collation元数据的标准方法

  // methodKey0: 缓存键对象，用于在元数据缓存中标识collations()方法的查询结果
  // 使用DescriptiveCacheKey包装，描述符为"ImmutableList Handler.collations()"，便于调试和日志输出
  // 该键用于在RelMetadataQuery的缓存map中存储和检索Collation元数据，避免重复计算
  // 缓存键是final的，一旦初始化就不会改变，确保缓存的一致性
  private final Object methodKey0 = // 声明一个final类型的Object成员变量，作为缓存键，初始化为DescriptiveCacheKey对象
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("ImmutableList Handler.collations()"); // 创建DescriptiveCacheKey实例，传入描述性字符串"ImmutableList Handler.collations()"，用于标识这个元数据查询方法

  // provider0: Collation元数据的实际提供者，类型为RelMdCollation
  // RelMdCollation是Calcite中负责计算各种RelNode类型的Collation元数据的核心类
  // 该提供者包含了针对不同RelNode类型（如Sort、Project、Filter等）的Collation计算逻辑
  // GeneratedMetadata_CollationHandler作为包装器，通过provider0委托实际的Collation计算工作
  // 该成员是public final的，确保在构造后不会被修改，保证线程安全
  public final org.apache.calcite.rel.metadata.RelMdCollation provider0; // 声明public final类型的RelMdCollation成员变量，作为Collation元数据的实际计算提供者

  // 构造方法：初始化GeneratedMetadata_CollationHandler实例
  // 参数provider0: RelMdCollation类型的Collation元数据提供者，将被存储到provider0成员变量中
  // 该构造方法接收一个RelMdCollation实例，用于后续的Collation元数据计算委托
  // 构造方法简洁，只进行成员变量的赋值，没有其他初始化逻辑
  public GeneratedMetadata_CollationHandler( // 构造方法声明，接收RelMdCollation类型的参数
      org.apache.calcite.rel.metadata.RelMdCollation provider0) { // 参数声明：provider0是RelMdCollation类型的Collation元数据提供者
    this.provider0 = provider0; // 将传入的provider0参数赋值给成员变量provider0，保存Collation元数据提供者的引用
  }

  // getDef方法：获取该元数据处理器的定义信息
  // 返回值：MetadataDef对象，描述了该处理器提供的元数据类型和相关信息
  // 该方法直接委托给provider0.getDef()，因为元数据定义信息由RelMdCollation提供者维护
  // MetadataDef包含了元数据的名称、类型、Handler类等信息，用于元数据系统的注册和查找
  public org.apache.calcite.rel.metadata.MetadataDef getDef() { // 方法声明：返回MetadataDef类型，无参数
    return provider0.getDef(); // 调用provider0的getDef()方法，返回Collation元数据的定义信息
  }

  // collations方法：查询指定RelNode的Collation（排序规则）元数据
  // 参数r: 要查询Collation元数据的关系节点（RelNode）
  // 参数mq: RelMetadataQuery对象，提供元数据查询的上下文和缓存支持
  // 返回值：ImmutableList对象，表示该RelNode输出的排序规则列表，可能为null
  // 该方法实现了缓存机制，避免重复计算相同RelNode的Collation元数据
  // 还实现了循环检测，防止元数据计算过程中的循环依赖导致无限递归
  public com.google.common.collect.ImmutableList collations( // 方法声明：返回ImmutableList类型，接收RelNode和RelMetadataQuery两个参数
      org.apache.calcite.rel.metadata.RelNode r, // 参数r：要查询Collation元数据的关系节点
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq：元数据查询对象，提供缓存和上下文支持

    // 处理DelegatingMetadataRel类型的节点，获取其委托的真正RelNode
    // DelegatingMetadataRel是一个包装器节点，需要穿透到实际的RelNode进行元数据查询
    // 使用while循环是因为可能存在多层嵌套的DelegatingMetadataRel包装
    // 这样可以确保最终查询到真正的RelNode的Collation元数据，而不是包装器的元数据
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) { // while循环：检查r是否是DelegatingMetadataRel类型
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 将r更新为委托的RelNode，穿透包装层
    }

    // 声明缓存键变量key，用于在缓存中查找或存储Collation元数据
    // key将使用前面定义的methodKey0作为缓存键
    final Object key; // 声明final类型的Object变量key，用于存储缓存键
    key = methodKey0; // 将methodKey0赋值给key，使用预定义的缓存键"ImmutableList Handler.collations()"

    // 尝试从RelMetadataQuery的缓存map中获取已计算的Collation元数据
    // mq.map是一个二维缓存，key1是RelNode，key2是方法键，value是缓存的元数据结果
    // 如果缓存命中，可以避免重复计算，显著提高元数据查询性能
    final Object v = mq.map.get(r, key); // 从缓存map中获取RelNode r和方法键key对应的缓存值v

    // 检查缓存中是否已有结果
    if (v != null) { // if判断：如果缓存值v不为null，表示缓存命中

      // 检查是否是ACTIVE标记，表示正在计算中，检测到循环依赖
      // NullSentinel.ACTIVE是一个特殊标记，表示该元数据正在被计算
      // 如果遇到ACTIVE标记，说明存在循环依赖，需要抛出异常中断计算
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // if判断：如果v等于ACTIVE标记
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出CyclicMetadataException异常，表示检测到循环依赖
      }

      // 检查是否是INSTANCE标记，表示缓存的值是null
      // NullSentinel.INSTANCE是一个特殊标记，用于在缓存中表示null值
      // 因为缓存map不能存储null值，所以用INSTANCE标记来代表null
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // if判断：如果v等于INSTANCE标记
        return null; // 返回null，表示该RelNode没有Collation元数据
      }

      // 缓存命中且不是特殊标记，直接返回缓存的Collation值
      // 将缓存的Object类型强制转换为ImmutableList类型返回
      // 这样避免了重复计算，直接使用之前计算好的结果
      return (com.google.common.collect.ImmutableList) v; // 强制转换v为ImmutableList类型并返回
    }

    // 缓存未命中，将ACTIVE标记放入缓存，表示开始计算该元数据
    // 这样可以检测后续是否出现循环依赖
    // 如果在计算过程中再次请求相同的元数据，会检测到ACTIVE标记并抛出异常
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 在缓存map中放入ACTIVE标记，标记该元数据正在计算

    // 使用try-catch块保护元数据计算过程
    // 在try块中进行实际的Collation计算，并在成功后更新缓存
    // 如果计算过程中抛出异常，会清除该RelNode的所有缓存，避免脏数据
    try { // try块开始：保护元数据计算过程

      // 调用collations_方法进行实际的Collation计算
      // collations_是内部方法，根据RelNode的具体类型调用相应的provider0方法
      // 计算结果存储在变量x中
      final com.google.common.collect.ImmutableList x = collations_(r, mq); // 调用collations_方法进行实际计算，结果存储在x中

      // 将计算结果x放入缓存，使用NullSentinel.mask()处理null值
      // NullSentinel.mask()会将null转换为INSTANCE标记，非null值保持不变
      // 这样缓存中可以正确表示null值，避免NullPointerException
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果x（经过mask处理）放入缓存

      // 返回计算结果x
      return x; // 返回计算得到的Collation列表
    } catch (java.lang.Exception e) { // catch块：捕获计算过程中可能抛出的任何异常

      // 清除该RelNode的所有缓存，避免脏数据
      // 因为计算过程中出现异常，缓存的状态可能不一致，需要清除
      // 使用row(r).clear()清除该RelNode对应的所有缓存条目
      mq.map.row(r).clear(); // 清除RelNode r的所有缓存条目

      // 重新抛出捕获的异常，让调用者处理
      throw e; // 重新抛出异常
    }
  }

  // collations_方法：根据RelNode的具体类型调用相应的Collation计算方法
  // 参数r: 要查询Collation元数据的关系节点（RelNode）
  // 参数mq: RelMetadataQuery对象，提供元数据查询的上下文和缓存支持
  // 返回值：ImmutableList对象，表示该RelNode输出的排序规则列表
  // 该方法使用一系列if-else if语句判断RelNode的具体类型，并调用provider0中对应的计算方法
  // 这种类型分发机制是在代码生成时自动构建的，包含了Calcite支持的所有RelNode类型
  // 最后有一个catch-all分支，处理未明确列出的RelNode类型
  private com.google.common.collect.ImmutableList collations_( // 方法声明：返回ImmutableList类型，接收RelNode和RelMetadataQuery两个参数
      org.apache.calcite.rel.RelNode r, // 参数r：要查询Collation元数据的关系节点
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq：元数据查询对象，提供缓存和上下文支持

    // 检查r是否是EnumerableCorrelate类型（可枚举的关联操作符）
    // Correlate是用于处理相关子查询的操作符，需要特殊的Collation处理逻辑
    // 如果是，调用provider0的collations方法，传入类型转换后的参数
    if (r instanceof org.apache.calcite.adapter.enumerable.EnumerableCorrelate) { // if判断：检查r是否是EnumerableCorrelate类型
      return provider0.collations((org.apache.calcite.adapter.enumerable.EnumerableCorrelate) r, mq); // 调用provider0的collations方法，传入EnumerableCorrelate类型的r和mq
    } else if (r instanceof org.apache.calcite.adapter.enumerable.EnumerableHashJoin) { // else if：检查r是否是EnumerableHashJoin（哈希连接）类型

      // 检查r是否是EnumerableHashJoin类型（基于哈希的可枚举连接）
      // HashJoin使用哈希表进行连接，通常不保持输入的排序特性
      // 调用provider0的collations方法计算HashJoin的Collation
      return provider0.collations((org.apache.calcite.adapter.enumerable.EnumerableHashJoin) r, mq); // 调用provider0的collations方法，传入EnumerableHashJoin类型的r和mq
    } else if (r instanceof org.apache.calcite.adapter.enumerable.EnumerableLimit) { // else if：检查r是否是EnumerableLimit（限制操作符）类型

      // 检查r是否是EnumerableLimit类型（限制结果集大小的操作符）
      // Limit操作通常保持输入的Collation，只是限制行数
      // 调用provider0的collations方法计算Limit的Collation
      return provider0.collations((org.apache.calcite.adapter.enumerable.EnumerableLimit) r, mq); // 调用provider0的collations方法，传入EnumerableLimit类型的r和mq
    } else if (r instanceof org.apache.calcite.adapter.enumerable.EnumerableMergeJoin) { // else if：检查r是否是EnumerableMergeJoin（归并连接）类型

      // 检查r是否是EnumerableMergeJoin类型（基于归并的可枚举连接）
      // MergeJoin需要输入有序，并保持输出的有序性
      // 调用provider0的collations方法计算MergeJoin的Collation
      return provider0.collations((org.apache.calcite.adapter.enumerable.EnumerableMergeJoin) r, mq); // 调用provider0的collations方法，传入EnumerableMergeJoin类型的r和mq
    } else if (r instanceof org.apache.calcite.adapter.enumerable.EnumerableMergeUnion) { // else if：检查r是否是EnumerableMergeUnion（归并联合）类型

      // 检查r是否是EnumerableMergeUnion类型（基于归并的可枚举联合操作符）
      // MergeUnion需要对输入进行排序才能正确执行归并操作
      // 调用provider0的collations方法计算MergeUnion的Collation
      return provider0.collations((org.apache.calcite.adapter.enumerable.EnumerableMergeUnion) r, mq); // 调用provider0的collations方法，传入EnumerableMergeUnion类型的r和mq
    } else if (r instanceof org.apache.calcite.adapter.enumerable.EnumerableNestedLoopJoin) { // else if：检查r是否是EnumerableNestedLoopJoin（嵌套循环连接）类型

      // 检查r是否是EnumerableNestedLoopJoin类型（嵌套循环连接操作符）
      // NestedLoopJoin通常不保持输入的排序特性
      // 调用provider0的collations方法计算NestedLoopJoin的Collation
      return provider0.collations((org.apache.calcite.adapter.enumerable.EnumerableNestedLoopJoin) r, mq); // 调用provider0的collations方法，传入EnumerableNestedLoopJoin类型的r和mq
    } else if (r instanceof org.apache.calcite.adapter.jdbc.JdbcToEnumerableConverter) { // else if：检查r是否是JdbcToEnumerableConverter（JDBC到可枚举转换器）类型

      // 检查r是否是JdbcToEnumerableConverter类型（JDBC适配器转换器）
      // 该转换器将JDBC数据源转换为可枚举的数据源，Collation取决于底层JDBC查询
      // 调用provider0的collations方法计算转换器的Collation
      return provider0.collations((org.apache.calcite.adapter.jdbc.JdbcToEnumerableConverter) r, mq); // 调用provider0的collations方法，传入JdbcToEnumerableConverter类型的r和mq
    } else if (r instanceof org.apache.calcite.plan.hep.HepRelVertex) { // else if：检查r是否是HepRelVertex（HepPlanner的顶点）类型

      // 检查r是否是HepRelVertex类型（HepPlanner的规划顶点）
      // HepRelVertex是HepPlanner使用的包装节点，需要获取其内部的RelNode
      // 调用provider0的collations方法计算HepRelVertex的Collation
      return provider0.collations((org.apache.calcite.plan.hep.HepRelVertex) r, mq); // 调用provider0的collations方法，传入HepRelVertex类型的r和mq
    } else if (r instanceof org.apache.calcite.plan.volcano.RelSubset) { // else if：检查r是否是RelSubset（VolcanoPlanner的子集）类型

      // 检查r是否是RelSubset类型（VolcanoPlanner的关系子集）
      // RelSubset表示等价的RelNode集合，Collation需要根据最佳RelNode确定
      // 调用provider0的collations方法计算RelSubset的Collation
      return provider0.collations((org.apache.calcite.plan.volcano.RelSubset) r, mq); // 调用provider0的collations方法，传入RelSubset类型的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.Calc) { // else if：检查r是否是Calc（计算操作符）类型

      // 检查r是否是Calc类型（计算操作符，类似Project但更通用）
      // Calc操作可能改变或保持Collation，取决于其投影和过滤逻辑
      // 调用provider0的collations方法计算Calc的Collation
      return provider0.collations((org.apache.calcite.rel.core.Calc) r, mq); // 调用provider0的collations方法，传入Calc类型的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.Filter) { // else if：检查r是否是Filter（过滤操作符）类型

      // 检查r是否是Filter类型（过滤操作符）
      // Filter通常保持输入的Collation，因为只是过滤行而不改变顺序
      // 调用provider0的collations方法计算Filter的Collation
      return provider0.collations((org.apache.calcite.rel.core.Filter) r, mq); // 调用provider0的collations方法，传入Filter类型的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.Match) { // else if：检查r是否是Match（模式匹配操作符）类型

      // 检查r是否是Match类型（模式匹配操作符，用于识别数据模式）
      // Match操作的Collation取决于模式匹配的实现和输出
      // 调用provider0的collations方法计算Match的Collation
      return provider0.collations((org.apache.calcite.rel.core.Match) r, mq); // 调用provider0的collations方法，传入Match类型的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.Project) { // else if：检查r是否是Project（投影操作符）类型

      // 检查r是否是Project类型（投影操作符，用于选择和计算列）
      // Project可能改变Collation，特别是当投影表达式涉及计算或重排列时
      // 调用provider0的collations方法计算Project的Collation
      return provider0.collations((org.apache.calcite.rel.core.Project) r, mq); // 调用provider0的collations方法，传入Project类型的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.Sort) { // else if：检查r是否是Sort（排序操作符）类型

      // 检查r是否是Sort类型（排序操作符）
      // Sort操作会明确设置输出的Collation，是其最重要的特性
      // 调用provider0的collations方法计算Sort的Collation
      return provider0.collations((org.apache.calcite.rel.core.Sort) r, mq); // 调用provider0的collations方法，传入Sort类型的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.SortExchange) { // else if：检查r是否是SortExchange（排序交换）类型

      // 检查r是否是SortExchange类型（排序交换操作符）
      // SortExchange在分布式环境中用于重新分区和排序数据
      // 调用provider0的collations方法计算SortExchange的Collation
      return provider0.collations((org.apache.calcite.rel.core.SortExchange) r, mq); // 调用provider0的collations方法，传入SortExchange类型的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.TableModify) { // else if：检查r是否是TableModify（表修改操作符）类型

      // 检查r是否是TableModify类型（表修改操作符，如INSERT、UPDATE、DELETE）
      // TableModify的Collation通常不重要，因为它是修改操作而非查询
      // 调用provider0的collations方法计算TableModify的Collation
      return provider0.collations((org.apache.calcite.rel.core.TableModify) r, mq); // 调用provider0的collations方法，传入TableModify类型的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) { // else if：检查r是否是TableScan（表扫描操作符）类型

      // 检查r是否是TableScan类型（表扫描操作符）
      // TableScan的Collation取决于表的物理存储特性（如索引）
      // 调用provider0的collations方法计算TableScan的Collation
      return provider0.collations((org.apache.calcite.rel.core.TableScan) r, mq); // 调用provider0的collations方法，传入TableScan类型的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.Values) { // else if：检查r是否是Values（常量值操作符）类型

      // 检查r是否是Values类型（常量值操作符，用于生成常量行集）
      // Values操作符的Collation取决于常量值的排列顺序
      // 调用provider0的collations方法计算Values的Collation
      return provider0.collations((org.apache.calcite.rel.core.Values) r, mq); // 调用provider0的collations方法，传入Values类型的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.Window) { // else if：检查r是否是Window（窗口函数操作符）类型

      // 检查r是否是Window类型（窗口函数操作符）
      // Window操作需要特定的分区和排序，Collation由窗口定义决定
      // 调用provider0的collations方法计算Window的Collation
      return provider0.collations((org.apache.calcite.rel.core.Window) r, mq); // 调用provider0的collations方法，传入Window类型的r和mq
    } else if (r instanceof org.apache.calcite.rel.RelNode) { // else if：检查r是否是RelNode类型（通用关系节点）

      // catch-all分支：处理所有其他RelNode类型
      // 这是一个兜底分支，处理未在前面明确列出的RelNode类型
      // 调用provider0的通用collations方法，接受RelNode基类型
      return provider0.collations((org.apache.calcite.rel.RelNode) r, mq); // 调用provider0的通用collations方法，传入RelNode基类型的r和mq
    } else { // else分支：处理未匹配任何已知类型的情况

      // 抛出IllegalArgumentException异常，表示没有找到对应的处理器
      // 异常消息包含方法签名和实际的RelNode类型，便于调试
      // 建议用户创建一个catch-all (RelNode)处理器来处理未预期的类型
      throw new java.lang.IllegalArgumentException("No handler for method [public abstract com.google.common.collect.ImmutableList org.apache.calcite.rel.metadata.BuiltInMetadata$Collation$Handler.collations(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出异常，说明没有找到对应的处理器
    }
  }

} // 类结束：GeneratedMetadata_CollationHandler类定义结束