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
 */ // Apache许可证声明，说明代码遵循Apache 2.0协议
package org.apache.calcite.rel.metadata.janino; // 声明该类属于org.apache.calcite.rel.metadata.janino包，这个包包含了使用Janino代码生成器生成的元数据处理类

public final class GeneratedMetadata_SelectivityHandler // 定义一个public final类，类名为GeneratedMetadata_SelectivityHandler，这是自动生成的Selectivity元数据处理器
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.Selectivity.Handler { // 实现BuiltInMetadata.Selectivity.Handler接口，该接口定义了获取选择性(selectivity)元数据的处理器
  private final Object methodKey0 = // 定义一个私有的final成员变量methodKey0，用于缓存方法的键，用于元数据缓存系统
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Double Handler.getSelectivity(RelNode, RelMetadataQuery, RexNode)"); // 创建一个描述性缓存键对象，键值为方法签名的字符串表示，用于在缓存中唯一标识getSelectivity方法
  public final org.apache.calcite.rel.metadata.RelMdSelectivity provider0; // 定义一个public final成员变量provider0，类型为RelMdSelectivity，这是实际提供选择性元数据计算逻辑的实现类
  public GeneratedMetadata_SelectivityHandler( // 定义构造方法，用于初始化处理器实例
      org.apache.calcite.rel.metadata.RelMdSelectivity provider0) { // 构造方法接收一个RelMdSelectivity类型的参数provider0
    this.provider0 = provider0; // 将传入的provider0参数赋值给成员变量provider0，保存元数据提供者引用
  } // 构造方法结束
  public org.apache.calcite.rel.metadata.MetadataDef getDef() { // 定义public方法getDef，返回元数据定义对象，用于描述这个元数据处理器
    return provider0.getDef(); // 直接调用provider0的getDef方法，返回元数据定义信息
  } // getDef方法结束
  public java.lang.Double getSelectivity( // 定义public方法getSelectivity，用于获取关系表达式的选择性(selectivity)，选择性表示谓词条件过滤后保留数据的比例
      org.apache.calcite.rel.RelNode r, // 参数r表示要计算选择性的关系节点(RelNode)，可以是任意类型的SQL关系操作
      org.apache.calcite.rel.metadata.RelMetadataQuery mq, // 参数mq表示元数据查询上下文对象，包含元数据缓存和其他查询相关信息
      org.apache.calcite.rex.RexNode a2) { // 参数a2表示RexNode表达式，通常是过滤条件谓词，用于计算该条件的选择性
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) { // 当r是DelegatingMetadataRel类型时，说明这是一个委托元数据关系节点，需要获取其委托的真正关系节点
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 调用getMetadataDelegateRel方法获取真正的委托关系节点，避免在包装节点上计算元数据
    } // while循环结束，此时r已经是真正的底层数据节点
    final Object key; // 声明一个final类型的key变量，用于构建缓存键
    key = org.apache.calcite.runtime.FlatLists.of(methodKey0, a2); // 使用FlatLists.of方法创建缓存键，包含方法键methodKey0和过滤条件a2，确保不同的过滤条件有独立的缓存
    final Object v = mq.map.get(r, key); // 从元数据缓存mq.map中根据关系节点r和缓存键key获取已缓存的结果v
    if (v != null) { // 如果缓存中存在结果v不为null
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // 检查v是否等于ACTIVE标记，ACTIVE表示当前元数据正在计算中，说明存在循环依赖
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出CyclicMetadataException异常，表示检测到元数据计算的循环依赖
      } // if语句结束
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // 检查v是否等于INSTANCE标记，INSTANCE表示缓存中存储的是null值
        return null; // 返回null，表示选择性未知或无法计算
      } // if语句结束
      return (java.lang.Double) v; // 将缓存值v强制转换为Double类型并返回，这是缓存的选择性结果
    } // if语句结束，缓存未命中
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 在缓存中放入ACTIVE标记，表示当前元数据正在计算中，用于检测循环依赖
    try { // 开始try块，用于执行元数据计算并捕获异常
      final java.lang.Double x = getSelectivity_(r, mq, a2); // 调用内部方法getSelectivity_计算实际的选择性值，结果存储在变量x中
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果x放入缓存，使用mask方法处理null值情况
      return x; // 返回计算得到的选择性值x
    } catch (java.lang.Exception e) { // 捕获计算过程中可能抛出的任何异常
      mq.map.row(r).clear(); // 发生异常时清空该关系节点的所有缓存，避免缓存污染
      throw e; // 重新抛出异常，让上层调用者处理
    } // try-catch块结束
  } // getSelectivity方法结束

  private java.lang.Double getSelectivity_( // 定义private方法getSelectivity_，这是实际执行选择性计算的内部方法，根据不同的关系节点类型分发到对应的处理逻辑
      org.apache.calcite.rel.RelNode r, // 参数r表示要计算选择性的关系节点
      org.apache.calcite.rel.metadata.RelMetadataQuery mq, // 参数mq表示元数据查询上下文对象
      org.apache.calcite.rex.RexNode a2) { // 参数a2表示RexNode过滤条件表达式
    if (r instanceof org.apache.calcite.rel.core.Aggregate) { // 如果r是Aggregate类型，表示聚合操作节点
      return provider0.getSelectivity((org.apache.calcite.rel.core.Aggregate) r, mq, a2); // 调用provider0的getSelectivity方法，传入Aggregate类型的r，计算聚合操作的选择性
    } else if (r instanceof org.apache.calcite.rel.core.Calc) { // 如果r是Calc类型，表示计算节点(类似Filter和Project的组合)
      return provider0.getSelectivity((org.apache.calcite.rel.core.Calc) r, mq, a2); // 调用provider0的getSelectivity方法，传入Calc类型的r，计算计算节点的选择性
    } else if (r instanceof org.apache.calcite.rel.core.Filter) { // 如果r是Filter类型，表示过滤节点
      return provider0.getSelectivity((org.apache.calcite.rel.core.Filter) r, mq, a2); // 调用provider0的getSelectivity方法，传入Filter类型的r，计算过滤条件的选择性
    } else if (r instanceof org.apache.calcite.rel.core.Join) { // 如果r是Join类型，表示连接操作节点
      return provider0.getSelectivity((org.apache.calcite.rel.core.Join) r, mq, a2); // 调用provider0的getSelectivity方法，传入Join类型的r，计算连接操作的选择性
    } else if (r instanceof org.apache.calcite.rel.core.Project) { // 如果r是Project类型，表示投影节点
      return provider0.getSelectivity((org.apache.calcite.rel.core.Project) r, mq, a2); // 调用provider0的getSelectivity方法，传入Project类型的r，计算投影操作的选择性
    } else if (r instanceof org.apache.calcite.rel.core.Sort) { // 如果r是Sort类型，表示排序节点
      return provider0.getSelectivity((org.apache.calcite.rel.core.Sort) r, mq, a2); // 调用provider0的getSelectivity方法，传入Sort类型的r，计算排序操作的选择性
    } else if (r instanceof org.apache.calcite.rel.core.TableModify) { // 如果r是TableModify类型，表示表修改操作节点(INSERT/UPDATE/DELETE)
      return provider0.getSelectivity((org.apache.calcite.rel.core.TableModify) r, mq, a2); // 调用provider0的getSelectivity方法，传入TableModify类型的r，计算表修改操作的选择性
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) { // 如果r是TableScan类型，表示表扫描节点
      return provider0.getSelectivity((org.apache.calcite.rel.core.TableScan) r, mq, a2); // 调用provider0的getSelectivity方法，传入TableScan类型的r，计算表扫描的选择性
    } else if (r instanceof org.apache.calcite.rel.core.Union) { // 如果r是Union类型，表示联合操作节点
      return provider0.getSelectivity((org.apache.calcite.rel.core.Union) r, mq, a2); // 调用provider0的getSelectivity方法，传入Union类型的r，计算联合操作的选择性
    } else if (r instanceof org.apache.calcite.rel.RelNode) { // 如果r是RelNode类型，这是最后的兜底case，处理所有其他类型的关系节点
      return provider0.getSelectivity((org.apache.calcite.rel.RelNode) r, mq, a2); // 调用provider0的getSelectivity方法，传入通用的RelNode类型r，使用默认的选择性计算逻辑
    } else { // 如果r不属于以上任何已知类型
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.lang.Double org.apache.calcite.rel.metadata.BuiltInMetadata$Selectivity$Handler.getSelectivity(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery,org.apache.calcite.rex.RexNode)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出IllegalArgumentException异常，提示用户没有找到对应类型的关系节点处理器，并建议创建一个catch-all(RelNode)处理器来处理未知类型
    } // else语句结束
  } // getSelectivity_方法结束

} // 类定义结束