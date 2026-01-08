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
package org.apache.calcite.plan; // 声明包名，该类位于 org.apache.calcite.plan 包下，属于 Calcite 查询优化器的核心包

import org.apache.calcite.rel.RelNode; // 导入 RelNode 接口，代表关系代数表达式，是 Calcite 中所有关系运算符的基类
import org.apache.calcite.rel.convert.ConverterRule; // 导入 ConverterRule 类，用于定义从一个调用约定转换到另一个调用约定的规则
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入 RelMetadataQuery 类，用于查询关系表达式的元数据信息（如成本、行数等）
import org.apache.calcite.util.Pair; // 导入 Pair 工具类，用于存储键值对
import org.apache.calcite.util.graph.DefaultDirectedGraph; // 导入 DefaultDirectedGraph 类，用于表示有向图结构
import org.apache.calcite.util.graph.DefaultEdge; // 导入 DefaultEdge 类，表示有向图中的边
import org.apache.calcite.util.graph.DirectedGraph; // 导入 DirectedGraph 接口，定义有向图的基本操作
import org.apache.calcite.util.graph.Graphs; // 导入 Graphs 工具类，提供图的算法和操作（如路径查找、最短距离等）

import com.google.common.cache.CacheBuilder; // 导入 Google Guava 的 CacheBuilder，用于构建缓存
import com.google.common.cache.CacheLoader; // 导入 CacheLoader，用于定义缓存的加载逻辑
import com.google.common.cache.LoadingCache; // 导入 LoadingCache，支持自动加载的缓存接口
import com.google.common.collect.HashMultimap; // 导入 HashMultimap，支持一对多映射的集合
import com.google.common.collect.Multimap; // 导入 Multimap 接口，定义一对多映射的基本操作

import org.checkerframework.checker.nullness.qual.MonotonicNonNull; // 导入注解，表示字段在初始化后不会变为 null
import org.checkerframework.checker.nullness.qual.Nullable; // 导入注解，表示返回值可能为 null

import java.util.List; // 导入 List 接口，用于存储有序的元素列表

import static java.util.Objects.requireNonNull; // 静态导入 requireNonNull 方法，用于空值检查

/**
 * Definition of the convention trait. // 类的简要说明：定义约定（Convention）特征的实现
 * A new set of conversion information is created for // 为每个注册了至少一个 ConverterRule 的规划器创建一组新的转换信息
 * each planner that registers at least one {@link ConverterRule} instance.
 *
 * <p>Conversion data is held in a {@link LoadingCache} // 转换数据存储在 LoadingCache 中
 * with weak keys so that the JVM's garbage // 使用弱键，以便在规划器被垃圾回收后，JVM 的垃圾回收器可以回收转换数据
 * collector may reclaim the conversion data after the planner itself has been
 * garbage collected. The conversion information consists of a graph of // 转换信息包括一个转换图（从一个调用约定到另一个）和一个从图边到 ConverterRule 的映射
 * conversions (from one calling convention to another) and a map of graph arcs
 * to {@link ConverterRule}s.
 */
public class ConventionTraitDef extends RelTraitDef<Convention> { // ConventionTraitDef 类继承自 RelTraitDef<Convention>，管理 Convention 特征的定义和转换
  //~ Static fields/initializers --------------------------------------------- // 静态字段和初始化器区域分隔符

  public static final ConventionTraitDef INSTANCE = // 声明一个静态常量 INSTANCE，作为 ConventionTraitDef 的单例实例
      new ConventionTraitDef(); // 创建 ConventionTraitDef 的实例，采用单例模式确保全局唯一

  //~ Instance fields -------------------------------------------------------- // 实例字段区域分隔符

  /**
   * Weak-key cache of RelOptPlanner to ConversionData. The idea is that when // RelOptPlanner 到 ConversionData 的弱键缓存，当规划器被回收时缓存条目也会被回收
   * the planner goes away, so does the cache entry.
   */
  private final LoadingCache<RelOptPlanner, ConversionData> conversionCache = // 声明一个最终的加载缓存，键为 RelOptPlanner，值为 ConversionData
      CacheBuilder.newBuilder().weakKeys() // 创建缓存构建器，设置弱键以便在规划器不可达时自动清理缓存
          .build(CacheLoader.from(ConversionData::new)); // 构建缓存，使用 ConversionData 的构造函数作为缓存加载器

  //~ Constructors ----------------------------------------------------------- // 构造方法区域分隔符

  private ConventionTraitDef() { // 私有构造方法，防止外部实例化，确保单例模式
    super(); // 调用父类 RelTraitDef 的构造方法进行初始化
  }

  //~ Methods ---------------------------------------------------------------- // 方法区域分隔符

  // implement RelTraitDef // 实现 RelTraitDef 接口的方法

  @Override public Class<Convention> getTraitClass() { // 重写 getTraitClass 方法，返回该特征定义管理的特征类型
    return Convention.class; // 返回 Convention.class，表示该特征定义管理的是 Convention 类型
  }

  @Override public String getSimpleName() { // 重写 getSimpleName 方法，返回特征的简单名称
    return "convention"; // 返回字符串 "convention"，用于标识该特征的名称
  }

  @Override public Convention getDefault() { // 重写 getDefault 方法，返回特征的默认值
    return Convention.NONE; // 返回 Convention.NONE，表示默认的约定是 NONE（无约定）
  }

  @Override public void registerConverterRule( // 重写 registerConverterRule 方法，向规划器注册转换规则
      RelOptPlanner planner, // 参数：规划器实例，用于管理查询优化过程
      ConverterRule converterRule) { // 参数：要注册的转换规则，定义如何从一个约定转换到另一个约定
    if (converterRule.isGuaranteed()) { // 检查转换规则是否保证能够转换（isGuaranteed 为 true）
      ConversionData conversionData = getConversionData(planner); // 获取该规划器的转换数据，如果不存在则自动创建

      final Convention inConvention = // 获取转换规则的输入约定（源约定）
          (Convention) converterRule.getInTrait(); // 调用 converterRule.getInTrait() 获取输入特征并转换为 Convention 类型
      final Convention outConvention = // 获取转换规则的输出约定（目标约定）
          (Convention) converterRule.getOutTrait(); // 调用 converterRule.getOutTrait() 获取输出特征并转换为 Convention 类型
      conversionData.conversionGraph.addVertex(inConvention); // 将输入约定作为顶点添加到转换图中
      conversionData.conversionGraph.addVertex(outConvention); // 将输出约定作为顶点添加到转换图中
      conversionData.conversionGraph.addEdge(inConvention, outConvention); // 在转换图中添加从输入约定到输出约定的边，表示可转换

      conversionData.mapArcToConverterRule.put( // 将转换规则映射到对应的约定对上
          Pair.of(inConvention, outConvention), converterRule); // 使用 Pair.of 创建键（输入约定，输出约定），值为转换规则
    }
  }

  @Override public void deregisterConverterRule( // 重写 deregisterConverterRule 方法，从规划器中注销转换规则
      RelOptPlanner planner, // 参数：规划器实例
      ConverterRule converterRule) { // 参数：要注销的转换规则
    if (converterRule.isGuaranteed()) { // 检查转换规则是否保证能够转换
      ConversionData conversionData = getConversionData(planner); // 获取该规划器的转换数据

      final Convention inConvention = // 获取转换规则的输入约定
          (Convention) converterRule.getInTrait(); // 调用 getInTrait() 获取输入特征并转换
      final Convention outConvention = // 获取转换规则的输出约定
          (Convention) converterRule.getOutTrait(); // 调用 getOutTrait() 获取输出特征并转换

      final boolean removed = // 尝试从转换图中移除对应的边
          conversionData.conversionGraph.removeEdge( // 调用 removeEdge 方法移除边
              inConvention, outConvention); // 参数：源顶点和目标顶点
      assert removed; // 断言边已被成功移除，如果失败则抛出 AssertionError
      conversionData.mapArcToConverterRule.remove( // 从规则映射中移除对应的转换规则
          Pair.of(inConvention, outConvention), converterRule); // 使用约定对作为键，移除指定的转换规则
    }
  }

  // implement RelTraitDef // 实现 RelTraitDef 接口的方法

  @Override public @Nullable RelNode convert( // 重写 convert 方法，将关系表达式从当前约定转换到目标约定
      RelOptPlanner planner, // 参数：规划器实例，用于成本计算和转换执行
      RelNode rel, // 参数：要转换的关系表达式
      Convention toConvention, // 参数：目标约定，要将关系表达式转换到的约定
      boolean allowInfiniteCostConverters) { // 参数：是否允许使用无限成本的转换器
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象，用于查询关系表达式的元数据（如成本）
    final ConversionData conversionData = getConversionData(planner); // 获取规划器的转换数据

    final Convention fromConvention = // 获取关系表达式的当前约定
        requireNonNull(rel.getConvention(), // 调用 getConvention() 获取当前约定，并通过 requireNonNull 确保不为 null
            () -> "convention is null for rel " + rel); // 如果为 null，抛出异常并附带错误信息

    List<List<Convention>> conversionPaths = // 获取从源约定到目标约定的所有可能转换路径
        conversionData.getPaths(fromConvention, toConvention); // 每个路径是一个 Convention 列表，表示转换的中间步骤

  loop: // 定义循环标签，用于在嵌套循环中跳出到外层循环
    for (List<Convention> conversionPath : conversionPaths) { // 遍历所有转换路径
      assert conversionPath.get(0) == fromConvention; // 断言路径的起点是源约定
      assert conversionPath.get(conversionPath.size() - 1) // 断言路径的终点是目标约定
          == toConvention;
      RelNode converted = rel; // 初始化转换后的关系表达式为原始关系表达式
      Convention previous = null; // 初始化前一个约定为 null，用于跟踪转换路径中的上一个约定
      for (Convention arc : conversionPath) { // 遍历当前转换路径中的每个约定（弧）
        RelOptCost cost = planner.getCost(converted, mq); // 获取当前关系表达式的成本
        if ((cost == null || cost.isInfinite()) // 如果成本为 null 或无限大
            && !allowInfiniteCostConverters) { // 并且不允许使用无限成本的转换器
          continue loop; // 跳出当前路径，尝试下一条路径
        }
        if (previous != null) { // 如果前一个约定不为 null（即不是路径的第一个节点）
          converted = // 调用 changeConvention 方法执行约定转换
              changeConvention( // 方法名：改变约定
                  converted, previous, arc, // 参数：当前关系表达式、源约定、目标约定
                  conversionData.mapArcToConverterRule); // 参数：规则映射，用于查找适用的转换规则
          if (converted == null) { // 如果转换失败（返回 null）
            throw new AssertionError("Converter from " + previous + " to " + arc // 抛出断言错误，因为规则保证能够转换任何关系表达式
                + " guaranteed that it could convert any relexp");
          }
        }
        previous = arc; // 更新前一个约定为当前约定，准备下一次转换
      }
      return converted; // 返回成功转换后的关系表达式
    }

    return null; // 如果所有路径都无法转换，返回 null
  }

  /**
   * Tries to convert a relational expression to the target convention of an // 尝试将关系表达式转换到目标约定
   * arc.
   */
  private static @Nullable RelNode changeConvention( // 私有静态方法，尝试将关系表达式从源约定转换到目标约定
      RelNode rel, // 参数：要转换的关系表达式
      Convention source, // 参数：源约定（关系表达式当前的约定）
      Convention target, // 参数：目标约定（要转换到的约定）
      final Multimap<Pair<Convention, Convention>, ConverterRule> // 参数：规则映射，键为约定对，值为对应的转换规则集合
          mapArcToConverterRule) {
    assert source == rel.getConvention(); // 断言源约定等于关系表达式的当前约定

    // Try to apply each converter rule for this arc's source/target calling // 尝试应用每个适用于该约定对的转换规则
    // conventions.
    final Pair<Convention, Convention> key = Pair.of(source, target); // 创建约定对作为键，用于查找转换规则
    for (ConverterRule rule : mapArcToConverterRule.get(key)) { // 遍历所有适用于该约定对的转换规则
      assert rule.getInTrait() == source; // 断言规则的输入特征等于源约定
      assert rule.getOutTrait() == target; // 断言规则的输出特征等于目标约定
      RelNode converted = rule.convert(rel); // 调用规则的 convert 方法执行转换
      if (converted != null) { // 如果转换成功（返回非 null）
        return converted; // 返回转换后的关系表达式
      }
    }
    return null; // 如果所有规则都无法转换，返回 null
  }

  @Override public boolean canConvert( // 重写 canConvert 方法，检查是否可以从源约定转换到目标约定
      RelOptPlanner planner, // 参数：规划器实例
      Convention fromConvention, // 参数：源约定
      Convention toConvention) { // 参数：目标约定
    ConversionData conversionData = getConversionData(planner); // 获取规划器的转换数据
    return fromConvention.canConvertConvention(toConvention) // 检查源约定本身是否支持转换到目标约定
        || conversionData.getShortestDistance(fromConvention, toConvention) != -1; // 或者在转换图中是否存在从源约定到目标约定的路径（最短距离不为 -1）
  }

  private ConversionData getConversionData(RelOptPlanner planner) { // 私有方法，获取规划器的转换数据
    return conversionCache.getUnchecked(planner); // 从缓存中获取规划器的转换数据，如果不存在则自动创建
  }

  //~ Inner Classes ---------------------------------------------------------- // 内部类区域分隔符

  /** Workspace for converting from one convention to another. */ /** 用于从一个约定转换到另一个约定的工作空间 */
  private static final class ConversionData { // 私有静态内部类，存储规划器的转换信息
    final DirectedGraph<Convention, DefaultEdge> conversionGraph = // 声明一个有向图，顶点是约定，边表示可转换关系
        DefaultDirectedGraph.create(); // 创建默认的有向图实例

    /**
     * For a given source/target convention, there may be several possible // 对于给定的源/目标约定，可能有多个可能的转换规则
     * conversion rules. Maps {@link DefaultEdge} to a // 将边映射到转换规则集合
     * collection of {@link ConverterRule} objects.
     */
    final Multimap<Pair<Convention, Convention>, ConverterRule> mapArcToConverterRule = // 声明一个多值映射，键为约定对，值为转换规则集合
        HashMultimap.create(); // 创建基于哈希的多值映射实例

    private Graphs.@MonotonicNonNull FrozenGraph<Convention, DefaultEdge> pathMap; // 声明一个冻结的图实例，用于快速查询路径和距离，延迟初始化

    public List<List<Convention>> getPaths( // 公共方法，获取从源约定到目标约定的所有路径
        Convention fromConvention, // 参数：源约定
        Convention toConvention) { // 参数：目标约定
      return getPathMap().getPaths(fromConvention, toConvention); // 调用 getPathMap() 获取冻结图，然后查询所有路径
    }

    private Graphs.FrozenGraph<Convention, DefaultEdge> getPathMap() { // 私有方法，获取冻结的图实例（延迟初始化）
      if (pathMap == null) { // 如果 pathMap 尚未初始化
        pathMap = Graphs.makeImmutable(conversionGraph); // 将转换图转换为不可变的冻结图，用于高效的路径查询
      }
      return pathMap; // 返回冻结的图实例
    }

    public int getShortestDistance( // 公共方法，获取从源约定到目标约定的最短距离
        Convention fromConvention, // 参数：源约定
        Convention toConvention) { // 参数：目标约定
      return getPathMap().getShortestDistance(fromConvention, toConvention); // 调用 getPathMap() 获取冻结图，然后查询最短距离
    }
  }
}
