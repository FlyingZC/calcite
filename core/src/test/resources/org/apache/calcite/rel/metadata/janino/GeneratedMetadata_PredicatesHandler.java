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

// 本类是由Calcite的元数据处理器生成器自动生成的类，用于处理关系表达式(RelNode)的谓词(Predicates)元数据查询
// 该类实现了BuiltInMetadata.Predicates.Handler接口，提供了获取关系表达式谓词列表的功能
// 谓词(Predicates)是指可以在关系表达式上推导出的布尔条件，这些条件可以用于查询优化，如谓词下推、条件消除等
// 该类使用缓存机制来避免重复计算，同时支持循环依赖检测，防止元数据计算过程中的无限递归
// 该类通过类型分发模式，根据输入RelNode的具体类型调用provider0中对应的getPredicates方法
// provider0是RelMdPredicates类型的对象，包含了各种RelNode类型的谓词计算逻辑
public final class GeneratedMetadata_PredicatesHandler
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.Predicates.Handler {
  // 缓存键对象，用于在元数据缓存中标识getPredicates方法的计算结果
  // 使用DescriptiveCacheKey包装方法签名，便于调试和缓存管理
  // 该键是final的，一旦初始化就不能改变，确保缓存键的稳定性
  private final Object methodKey0 =
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("RelOptPredicateList Handler.getPredicates()");
  // 谓词元数据提供者对象，包含了各种RelNode类型的谓词计算实现
  // 该对象是public final的，表示对外可见且不可变
  // provider0是RelMdPredicates类型的实例，它为不同类型的RelNode提供了具体的getPredicates重载方法
  // 例如：getPredicates(Filter, RelMetadataQuery)、getPredicates(Join, RelMetadataQuery)等
  // 该类通过调用provider0的不同重载方法来计算不同类型关系表达式的谓词列表
  public final org.apache.calcite.rel.metadata.RelMdPredicates provider0;
  // 构造方法，用于初始化GeneratedMetadata_PredicatesHandler实例
  // 参数provider0是RelMdPredicates类型的对象，包含了谓词计算的实际实现逻辑
  // 该构造方法接收一个RelMdPredicates提供者并将其存储到成员变量provider0中
  // 这样当需要计算谓词时，就可以通过provider0调用相应的实现方法
  public GeneratedMetadata_PredicatesHandler(
      org.apache.calcite.rel.metadata.RelMdPredicates provider0) {
    // 将传入的provider0参数赋值给成员变量provider0
    // 这样后续的方法调用就可以使用这个provider0来执行实际的谓词计算
    this.provider0 = provider0;
  }
  // 获取元数据定义的方法，返回该处理器所处理的元数据的定义信息
  // MetadataDef包含了元数据的名称、返回类型、参数类型等信息
  // 该方法直接委托给provider0.getDef()，因为provider0包含了元数据的完整定义
  // 返回的MetadataDef用于在Calcite的元数据系统中注册和查找该处理器
  public org.apache.calcite.rel.metadata.MetadataDef getDef() {
    // 直接返回provider0的元数据定义
    // provider0.getDef()返回BuiltInMetadata.Predicates的定义
    // 该定义描述了getPredicates方法的签名和返回类型
    return provider0.getDef();
  }
  // 获取关系表达式的谓词列表，这是该类的核心方法
  // 参数r：要查询谓词的RelNode关系表达式
  // 参数mq：RelMetadataQuery对象，用于访问元数据查询的上下文和缓存
  // 返回值：RelOptPredicateList对象，包含该关系表达式上可以推导出的所有谓词条件
  // 该方法实现了缓存机制和循环依赖检测，确保元数据查询的高效性和正确性
  public org.apache.calcite.plan.RelOptPredicateList getPredicates(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) {
    // 处理委托元数据关系表达式(DelegatingMetadataRel)
    // DelegatingMetadataRel是一种特殊的RelNode，它将元数据查询委托给另一个RelNode
    // 通过while循环不断解包，直到找到真正实现元数据查询的底层RelNode
    // 这样可以确保我们查询的是实际的关系表达式，而不是包装器
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) {
      // 将r设置为委托的目标关系表达式
      // getMetadataDelegateRel()返回该DelegatingMetadataRel所委托的RelNode
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel();
    }
    // 声明缓存键变量
    // 该变量用于在元数据缓存中查找或存储计算结果
    final Object key;
    // 将缓存键设置为methodKey0
    // methodKey0是预先定义的DescriptiveCacheKey，标识getPredicates方法
    key = methodKey0;
    // 尝试从缓存中获取该RelNode的谓词计算结果
    // mq.map是元数据缓存映射，以(RelNode, key)为键存储计算结果
    // 如果缓存中已有结果，直接返回，避免重复计算
    final Object v = mq.map.get(r, key);
    // 检查缓存中是否存在结果
    if (v != null) {
      // 如果缓存值是ACTIVE，说明正在计算该RelNode的谓词，检测到循环依赖
      // NullSentinel.ACTIVE是一个特殊的标记值，表示该元数据正在计算中
      // 如果再次遇到ACTIVE，说明计算过程中又请求了该元数据，形成循环
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) {
        // 抛出循环元数据异常，终止计算
        // CyclicMetadataException表示元数据计算过程中存在循环依赖
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException();
      }
      // 如果缓存值是INSTANCE，说明之前计算的结果为null
      // NullSentinel.INSTANCE是用于表示null值的特殊对象
      // 因为缓存映射的值类型是Object，不能直接存储null值
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) {
        // 返回null，表示该RelNode没有可推导的谓词
        return null;
      }
      // 缓存命中，返回缓存中的谓词列表
      // 将缓存值强制转换为RelOptPredicateList类型并返回
      return (org.apache.calcite.plan.RelOptPredicateList) v;
    }
    // 缓存未命中，在开始计算前先放入ACTIVE标记
    // 这样可以检测到循环依赖：如果在计算过程中再次请求该RelNode的谓词，会检测到ACTIVE
    // ACTIVE标记表示该元数据正在计算中
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE);
    // 使用try-catch块进行实际的谓词计算
    // 如果计算过程中发生异常，需要清理缓存状态
    try {
      // 调用内部方法getPredicates_进行实际的谓词计算
      // getPredicates_方法根据RelNode的具体类型调用相应的实现
      // 计算结果存储在变量x中
      final org.apache.calcite.plan.RelOptPredicateList x = getPredicates_(r, mq);
      // 将计算结果存入缓存
      // NullSentinel.mask(x)将结果转换为可缓存的对象
      // 如果x为null，mask返回NullSentinel.INSTANCE；否则返回x本身
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x));
      // 返回计算得到的谓词列表
      return x;
    } catch (java.lang.Exception e) {
      // 如果计算过程中发生异常，清理该RelNode的所有缓存条目
      // 这样可以确保缓存中不会存储不完整或错误的结果
      // row(r)获取该RelNode在缓存中的行，clear()清除所有条目
      mq.map.row(r).clear();
      // 重新抛出异常，让调用者处理
      throw e;
    }
  }

  // 内部方法，根据RelNode的具体类型调用相应的谓词计算实现
  // 该方法使用if-else链进行类型分发，根据r的实际类型调用provider0的对应重载方法
  // 参数r：要查询谓词的RelNode关系表达式
  // 参数mq：RelMetadataQuery对象，用于访问元数据查询的上下文和缓存
  // 返回值：RelOptPredicateList对象，包含该关系表达式上可以推导出的所有谓词条件
  private org.apache.calcite.plan.RelOptPredicateList getPredicates_(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) {
    // 如果r是RelSubset类型，调用provider0的getPredicates(RelSubset, RelMetadataQuery)方法
    // RelSubset是VolcanoPlanner中的概念，表示等价关系表达式集合中的一个子集
    // RelSubset的谓词计算需要考虑子集中所有等价RelNode的谓词
    if (r instanceof org.apache.calcite.plan.volcano.RelSubset) {
      // 将r转换为RelSubset类型并调用provider0的对应方法
      return provider0.getPredicates((org.apache.calcite.plan.volcano.RelSubset) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Aggregate) {
      // 如果r是Aggregate类型（聚合操作），调用对应的getPredicates方法
      // Aggregate的谓词主要来自GROUP BY列的等值条件和HAVING子句
      return provider0.getPredicates((org.apache.calcite.rel.core.Aggregate) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Correlate) {
      // 如果r是Correlate类型（关联操作），调用对应的getPredicates方法
      // Correlate是特殊的Join，用于处理相关子查询
      return provider0.getPredicates((org.apache.calcite.rel.core.Correlate) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Exchange) {
      // 如果r是Exchange类型（数据交换操作），调用对应的getPredicates方法
      // Exchange用于分布式场景下的数据重分布，谓词通常直接来自输入
      return provider0.getPredicates((org.apache.calcite.rel.core.Exchange) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Filter) {
      // 如果r是Filter类型（过滤操作），调用对应的getPredicates方法
      // Filter的谓词主要来自过滤条件本身，以及输入的谓词
      return provider0.getPredicates((org.apache.calcite.rel.core.Filter) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Intersect) {
      // 如果r是Intersect类型（交集操作），调用对应的getPredicates方法
      // Intersect的谓词是所有输入谓词的交集
      return provider0.getPredicates((org.apache.calcite.rel.core.Intersect) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Join) {
      // 如果r是Join类型（连接操作），调用对应的getPredicates方法
      // Join的谓词来自连接条件以及左右输入的谓词
      // Join的谓词计算是查询优化的关键，用于谓词下推
      return provider0.getPredicates((org.apache.calcite.rel.core.Join) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Minus) {
      // 如果r是Minus类型（差集操作，即EXCEPT），调用对应的getPredicates方法
      // Minus的谓词主要来自第一个输入
      return provider0.getPredicates((org.apache.calcite.rel.core.Minus) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Project) {
      // 如果r是Project类型（投影操作），调用对应的getPredicates方法
      // Project的谓词需要将输入谓词中的列引用映射到输出列
      return provider0.getPredicates((org.apache.calcite.rel.core.Project) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Sample) {
      // 如果r是Sample类型（采样操作），调用对应的getPredicates方法
      // Sample的谓词通常直接来自输入
      return provider0.getPredicates((org.apache.calcite.rel.core.Sample) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Sort) {
      // 如果r是Sort类型（排序操作），调用对应的getPredicates方法
      // Sort的谓词主要来自输入，排序条件本身不是谓词
      return provider0.getPredicates((org.apache.calcite.rel.core.Sort) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.TableModify) {
      // 如果r是TableModify类型（表修改操作，如INSERT/UPDATE/DELETE），调用对应的getPredicates方法
      // TableModify的谓词主要来自修改条件
      return provider0.getPredicates((org.apache.calcite.rel.core.TableModify) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) {
      // 如果r是TableScan类型（表扫描操作），调用对应的getPredicates方法
      // TableScan的谓词可能来自表的约束或统计信息
      return provider0.getPredicates((org.apache.calcite.rel.core.TableScan) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Union) {
      // 如果r是Union类型（并集操作），调用对应的getPredicates方法
      // Union的谓词是所有输入谓词的交集
      return provider0.getPredicates((org.apache.calcite.rel.core.Union) r, mq);
    } else if (r instanceof org.apache.calcite.rel.core.Values) {
      // 如果r是Values类型（常量值操作），调用对应的getPredicates方法
      // Values的谓词可以从常量值中推导出来
      return provider0.getPredicates((org.apache.calcite.rel.core.Values) r, mq);
    } else if (r instanceof org.apache.calcite.rel.RelNode) {
      // 如果r是通用的RelNode类型（兜底情况），调用对应的getPredicates方法
      // 这是一个catch-all处理器，用于处理所有未明确列出的RelNode类型
      // 建议为特定类型创建专门的处理器以获得更好的性能
      return provider0.getPredicates((org.apache.calcite.rel.RelNode) r, mq);
    } else {
      // 如果r的类型不匹配任何已知类型，抛出异常
      // 这种情况通常意味着没有为该类型实现谓词处理器
      // 异常消息中包含方法签名和实际的类型信息，便于调试
      // 建议用户创建一个catch-all(RelNode)处理器来处理这种情况
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract org.apache.calcite.plan.RelOptPredicateList org.apache.calcite.rel.metadata.BuiltInMetadata$Predicates$Handler.getPredicates(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler");
    }
  }

}
