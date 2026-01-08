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
 */ // Apache软件基金会许可证声明，规定代码的使用条件和限制
package org.apache.calcite.plan.volcano; // 定义包名，这是Calcite框架中火山优化器(VolcanoPlanner)相关的元数据提供者所在的包

import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式的基础接口，是Calcite中所有关系操作节点的基类
import org.apache.calcite.rel.metadata.Metadata; // 导入Metadata接口，表示元数据的基础接口，用于存储和访问关系节点的元数据信息
import org.apache.calcite.rel.metadata.MetadataDef; // 导入MetadataDef类，用于定义元数据类型的元数据定义
import org.apache.calcite.rel.metadata.MetadataHandler; // 导入MetadataHandler接口，表示元数据处理器，用于处理特定类型的元数据请求
import org.apache.calcite.rel.metadata.RelMetadataProvider; // 导入RelMetadataProvider接口，表示关系元数据提供者，用于为关系节点提供元数据
import org.apache.calcite.rel.metadata.UnboundMetadata; // 导入UnboundMetadata接口，表示未绑定的元数据，需要绑定到具体的关系节点后才能使用

import com.google.common.collect.ImmutableList; // 导入Google Guava库的ImmutableList类，用于创建不可变的列表
import com.google.common.collect.ImmutableMultimap; // 导入Google Guava库的ImmutableMultimap类，用于创建不可变的多重映射
import com.google.common.collect.Multimap; // 导入Google Guava库的Multimap接口，表示一个键可以映射到多个值的映射结构

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Checker框架的Nullable注解，用于标记可能为null的值，帮助进行空值检查

import java.lang.reflect.Method; // 导入Java反射的Method类，用于表示类的方法
import java.util.List; // 导入Java集合框架的List接口，表示有序的元素集合

import static java.util.Objects.requireNonNull; // 导入Java Objects类的requireNonNull静态方法，用于检查对象是否为null

/**
 * VolcanoRelMetadataProvider implements the {@link RelMetadataProvider}
 * interface by combining metadata from the rels making up an equivalence class.
 */ // VolcanoRelMetadataProvider类实现了RelMetadataProvider接口，通过组合等价类中所有关系节点的元数据来提供元数据服务
// 这个类专门为火山优化器(VolcanoPlanner)设计，因为火山优化器使用等价类(EquivalenceClass)来管理逻辑上等价但实现不同的关系表达式
// 当查询某个RelSubset(等价类子集)的元数据时，这个提供者会尝试从等价类中的所有关系节点中获取最合适的元数据
// 例如，对于一个RelSubset，它会优先使用当前最佳实现(best)的元数据，如果不可用，则会遍历等价类中的所有关系节点尝试获取元数据
// 这样可以确保在优化过程中能够获取到最准确和可靠的元数据信息，用于成本估算和优化决策
@Deprecated // to be removed before 2.0 // 标记这个类已废弃，将在2.0版本之前移除，表示这个类不再推荐使用，可能存在更好的替代方案
public class VolcanoRelMetadataProvider implements RelMetadataProvider { // 声明VolcanoRelMetadataProvider类，实现RelMetadataProvider接口，成为关系元数据提供者
  //~ Methods ---------------------------------------------------------------- // 方法区域分隔符，表示下面开始定义类的成员方法

  @Override public boolean equals(@Nullable Object obj) { // 重写Object类的equals方法，用于比较两个VolcanoRelMetadataProvider对象是否相等
    return obj instanceof VolcanoRelMetadataProvider; // 判断传入的对象obj是否是VolcanoRelMetadataProvider类的实例，如果是则返回true，否则返回false
  } // 这个实现表示所有VolcanoRelMetadataProvider实例都被认为是相等的，因为它们的行为是相同的，不依赖于任何内部状态

  @Override public int hashCode() { // 重写Object类的hashCode方法，用于生成对象的哈希码
    return 103; // 返回固定的哈希码值103，与equals方法保持一致，因为所有VolcanoRelMetadataProvider实例都被认为是相等的
  } // 返回固定值确保所有实例的哈希码相同，这是与equals方法实现相匹配的正确做法

  @Deprecated // to be removed before 2.0 // 标记这个方法已废弃，将在2.0版本之前移除
  @Override public <@Nullable M extends @Nullable Metadata> @Nullable UnboundMetadata<M> apply( // 重写RelMetadataProvider接口的apply方法，用于为指定的关系节点类和元数据类提供未绑定的元数据
      Class<? extends RelNode> relClass, // 参数：关系节点类，表示要查询元数据的关系节点类型，例如RelSubset.class
      final Class<? extends M> metadataClass) { // 参数：元数据类，表示要查询的元数据类型，例如RowCount.class或Selectivity.class等
    if (relClass != RelSubset.class) { // 判断传入的关系节点类是否是RelSubset类，如果不是则进入if块
      // let someone else further down the chain sort it out // 注释说明：让元数据提供者链中的其他提供者来处理这种情况
      return null; // 返回null，表示这个元数据提供者无法处理非RelSubset类型的关系节点，将请求传递给链中的下一个提供者
    } // 这个设计允许在元数据提供者链中组合多个提供者，每个提供者处理特定类型的关系节点

    return (rel, mq) -> { // 返回一个lambda表达式，这个表达式实现了UnboundMetadata<M>接口，用于将元数据绑定到具体的关系节点
      // 参数rel：要绑定元数据的关系节点，实际类型应该是RelSubset
      // 参数mq：MetadataQuery对象，用于查询元数据
      final RelSubset subset = (RelSubset) rel; // 将传入的关系节点强制转换为RelSubset类型，因为这个提供者只处理RelSubset
      final RelMetadataProvider provider = // 声明一个RelMetadataProvider变量，用于获取实际的元数据提供者
          requireNonNull(rel.getCluster().getMetadataProvider(), // 从关系节点的Cluster中获取元数据提供者，并使用requireNonNull确保不为null
              "metadataProvider"); // 如果元数据提供者为null，则抛出NullPointerException，错误信息为"metadataProvider"

      // REVIEW jvs 29-Mar-2006: I'm not sure what the correct precedence
      // should be here.  Letting the current best plan take the first shot is
      // probably the right thing to do for physical estimates such as row
      // count.  Dunno about others, and whether we need a way to
      // discriminate. // 审查注释：作者不确定这里的优先级是否正确，让当前最佳计划先尝试可能是正确的做法，特别是对于行数等物理估算，但对于其他元数据不确定，可能需要一种区分方式

      // First, try current best implementation.  If it knows how to answer
      // this query, treat it as the most reliable. // 首先尝试当前最佳实现，如果它知道如何回答这个查询，则将其视为最可靠的答案
      if (subset.best != null) { // 检查RelSubset是否有当前最佳实现(best字段不为null)，如果有则尝试使用最佳实现获取元数据
        RelNode best = subset.best; // 获取RelSubset的当前最佳实现，这是一个RelNode对象
        final UnboundMetadata<M> function = // 声明一个未绑定的元数据对象，用于存储从元数据提供者获取的元数据函数
            provider.apply(best.getClass(), metadataClass); // 调用元数据提供者的apply方法，传入最佳实现的类和元数据类，获取未绑定的元数据
        if (function != null) { // 检查是否成功获取到未绑定的元数据(不为null)
          final M metadata = function.bind(best, mq); // 如果获取成功，则将元数据绑定到最佳实现和MetadataQuery对象上，得到绑定的元数据
          if (metadata != null) { // 检查绑定后的元数据是否不为null
            return metadata; // 如果元数据不为null，则返回这个元数据，这是最可靠的答案，因为来自当前最佳实现
          } // 如果元数据为null，说明最佳实现无法提供这个元数据，继续尝试其他方式
        } // 如果未绑定的元数据为null，说明元数据提供者无法为最佳实现提供这个元数据，继续尝试其他方式
      } // 结束对最佳实现的尝试，如果成功返回元数据则不会执行后续代码

      // Otherwise, try rels in same logical equivalence class to see if any
      // of them have a good answer.  We use the full logical equivalence
      // class rather than just the subset because many metadata providers
      // only know about logical metadata. // 否则，尝试在同一个逻辑等价类中的所有关系节点，看看是否有任何一个能够提供好的答案。我们使用完整的逻辑等价类而不仅仅是子集，因为许多元数据提供者只知道逻辑元数据

      // Equivalence classes can get tangled up in interesting ways, so avoid
      // an infinite loop.  REVIEW: There's a chance this will cause us to
      // fail on metadata queries which invoke other queries, e.g.
      // PercentageOriginalRows -> Selectivity.  If we implement caching at
      // this level, we could probably kill two birds with one stone (use
      // presence of pending cache entry to detect re-entrancy at the correct
      // granularity). // 等价类可能会以有趣的方式纠缠在一起，所以避免无限循环。审查：这可能会导致我们在调用其他查询的元数据查询上失败，例如PercentageOriginalRows -> Selectivity。如果在这个级别实现缓存，我们可能一石二鸟（使用待处理缓存条目的存在来检测正确粒度的重入）
      if (subset.set.inMetadataQuery) { // 检查等价类是否正在进行元数据查询(inMetadataQuery标志为true)，如果是则进入if块
        return null; // 返回null，表示无法提供元数据，防止无限循环
      } // 这个标志用于检测重入，避免在元数据查询过程中再次触发元数据查询导致无限递归

      subset.set.inMetadataQuery = true; // 设置等价类的inMetadataQuery标志为true，表示正在进行元数据查询
      try { // 开始try块，确保无论是否成功，都会在finally块中重置标志
        for (RelNode relCandidate : subset.set.rels) { // 遍历等价类中的所有关系节点(rels列表)，尝试从每个节点获取元数据
          final UnboundMetadata<M> function = // 声明一个未绑定的元数据对象，用于存储从元数据提供者获取的元数据函数
              provider.apply(relCandidate.getClass(), metadataClass); // 调用元数据提供者的apply方法，传入候选关系节点的类和元数据类，获取未绑定的元数据
          if (function != null) { // 检查是否成功获取到未绑定的元数据(不为null)
            final M result = function.bind(relCandidate, mq); // 如果获取成功，则将元数据绑定到候选关系节点和MetadataQuery对象上，得到绑定的元数据
            if (result != null) { // 检查绑定后的元数据是否不为null
              return result; // 如果元数据不为null，则立即返回这个元数据，这是从等价类中找到的第一个有效答案
            } // 如果元数据为null，继续尝试下一个候选关系节点
          } // 如果未绑定的元数据为null，继续尝试下一个候选关系节点
        } // 遍历完所有候选关系节点后，如果都没有找到有效答案，则退出循环
      } finally { // finally块确保无论try块中是否发生异常，都会执行以下代码
        subset.set.inMetadataQuery = false; // 重置等价类的inMetadataQuery标志为false，表示元数据查询已完成，允许后续的查询进行
      } // finally块确保标志被正确重置，避免标志一直为true导致后续查询被阻止

      // Give up. // 放弃尝试
      return null; // 返回null，表示无法提供这个元数据，已经尝试了最佳实现和等价类中的所有关系节点，都没有找到有效的元数据
    }; // 结束lambda表达式，返回一个UnboundMetadata<M>对象
  } // 结束apply方法

  @Deprecated // 标记这个方法已废弃，表示不再推荐使用
  @Override public <M extends Metadata> Multimap<Method, MetadataHandler<M>> handlers( // 重写RelMetadataProvider接口的handlers方法，用于获取处理指定元数据定义的处理器映射
      MetadataDef<M> def) { // 参数：元数据定义，指定要获取处理器的元数据类型定义
    return ImmutableMultimap.of(); // 返回一个空的不可变多重映射，表示这个提供者不提供任何元数据处理器
  } // 这个方法已废弃，因为新的元数据系统使用不同的机制来注册和获取元数据处理器，这个方法返回空映射是为了向后兼容

  @Override public List<MetadataHandler<?>> handlers( // 重写RelMetadataProvider接口的handlers方法，用于获取指定处理器类的处理器列表
      Class<? extends MetadataHandler<?>> handlerClass) { // 参数：处理器类，指定要获取的处理器类型
    return ImmutableList.of(); // 返回一个空的不可变列表，表示这个提供者不提供任何元数据处理器
  } // 这个方法返回空列表，因为VolcanoRelMetadataProvider不直接提供元数据处理器，而是通过委托给其他元数据提供者来获取元数据
} // 结束VolcanoRelMetadataProvider类的定义
