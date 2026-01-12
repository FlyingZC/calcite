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
package org.apache.calcite.rel.metadata.janino; // 声明包名，该类属于org.apache.calcite.rel.metadata.janino包，用于处理元数据相关的Janino代码生成

// GeneratedMetadata_ParallelismHandler类：这是一个自动生成的元数据处理器类，用于处理并行度相关的元数据查询
// 该类实现了BuiltInMetadata.Parallelism.Handler接口，提供了两个核心方法：isPhaseTransition和splitCount
// isPhaseTransition方法用于判断关系节点是否是阶段转换节点（如Exchange操作）
// splitCount方法用于获取关系节点的分片数量
// 该类通过缓存机制提高元数据查询的性能，并支持循环依赖检测
public final class GeneratedMetadata_ParallelismHandler // 声明一个final类，表示该类不能被继承，GeneratedMetadata_ParallelismHandler是并行度元数据处理器
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.Parallelism.Handler { // 实现BuiltInMetadata.Parallelism.Handler接口，该接口定义了并行度元数据处理的规范
  private final Object methodKey0 = // 声明一个私有的final对象methodKey0，用于作为isPhaseTransition方法在缓存中的键，final表示该引用不可变
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Boolean Handler.isPhaseTransition()"); // 创建一个描述性缓存键，键的描述为"Boolean Handler.isPhaseTransition()"，用于在缓存中标识isPhaseTransition方法的查询结果
  private final Object methodKey1 = // 声明一个私有的final对象methodKey1，用于作为splitCount方法在缓存中的键，final表示该引用不可变
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Integer Handler.splitCount()"); // 创建一个描述性缓存键，键的描述为"Integer Handler.splitCount()"，用于在缓存中标识splitCount方法的查询结果
  public final org.apache.calcite.rel.metadata.RelMdParallelism provider1; // 声明一个公共的final成员变量provider1，类型为RelMdParallelism，这是实际的元数据提供者，包含计算并行度元数据的具体逻辑
  public GeneratedMetadata_ParallelismHandler( // 声明构造方法，用于创建GeneratedMetadata_ParallelismHandler实例
      org.apache.calcite.rel.metadata.RelMdParallelism provider1) { // 构造方法接收一个RelMdParallelism类型的参数provider1，这是元数据提供者
    this.provider1 = provider1; // 将传入的provider1参数赋值给成员变量provider1，保存元数据提供者的引用
  }
  public org.apache.calcite.rel.metadata.MetadataDef getDef() { // 声明getDef方法，返回MetadataDef对象，该方法用于获取元数据定义
    return provider1.getDef(); // 调用provider1的getDef方法，返回元数据定义，该方法直接委托给底层的元数据提供者
  }
  public java.lang.Boolean isPhaseTransition( // 声明isPhaseTransition方法，返回Boolean类型，该方法用于判断关系节点是否是阶段转换节点
      org.apache.calcite.rel.RelNode r, // 参数r：关系节点，表示要查询的RelNode对象
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq：元数据查询对象，用于访问元数据缓存和执行元数据查询
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) { // while循环：检查r是否是DelegatingMetadataRel类型，如果是则说明这是一个委托元数据关系节点
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 获取委托关系节点的实际委托节点，将r替换为委托的目标节点，继续循环检查直到找到非委托节点
    }
    final Object key; // 声明一个final的Object类型变量key，用于存储缓存键
    key = methodKey0; // 将methodKey0赋值给key，methodKey0是isPhaseTransition方法的缓存键
    final Object v = mq.map.get(r, key); // 从元数据查询对象的缓存map中获取关系节点r和键key对应的缓存值v
    if (v != null) { // if判断：检查缓存值v是否不为null，如果不为null说明缓存中存在该元数据值
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // 内层if：检查v是否等于ACTIVE标记，ACTIVE表示该元数据正在计算中
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出循环元数据异常，说明检测到循环依赖，元数据计算出现了循环调用
      }
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // 内层if：检查v是否等于INSTANCE标记，INSTANCE表示该元数据值为null
        return null; // 返回null，表示该元数据值为null
      }
      return (java.lang.Boolean) v; // 将缓存值v强制转换为Boolean类型并返回，直接使用缓存的结果
    }
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 将关系节点r、键key和ACTIVE标记放入缓存，表示该元数据正在计算中，用于检测循环依赖
    try { // try块：开始执行元数据计算
      final java.lang.Boolean x = isPhaseTransition_(r, mq); // 调用isPhaseTransition_私有方法计算实际的元数据值，将结果存储在变量x中
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果x放入缓存，使用mask方法处理null值，如果x为null则放入INSTANCE标记
      return x; // 返回计算结果x
    } catch (java.lang.Exception e) { // catch块：捕获计算过程中可能抛出的异常
      mq.map.row(r).clear(); // 清除关系节点r在缓存中的所有数据，因为计算失败，缓存数据不可靠
      throw e; // 重新抛出异常，让调用者处理
    }
  }

  private java.lang.Boolean isPhaseTransition_( // 声明私有的isPhaseTransition_方法，返回Boolean类型，这是实际执行元数据计算的实现方法
      org.apache.calcite.rel.RelNode r, // 参数r：关系节点，表示要查询的RelNode对象
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq：元数据查询对象，用于访问元数据缓存和执行元数据查询
    if (r instanceof org.apache.calcite.rel.core.Exchange) { // if判断：检查r是否是Exchange类型，Exchange是数据交换操作节点
      return provider1.isPhaseTransition((org.apache.calcite.rel.core.Exchange) r, mq); // 调用provider1的isPhaseTransition方法，传入Exchange类型的关系节点，返回是否是阶段转换节点
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) { // else if判断：检查r是否是TableScan类型，TableScan是表扫描操作节点
      return provider1.isPhaseTransition((org.apache.calcite.rel.core.TableScan) r, mq); // 调用provider1的isPhaseTransition方法，传入TableScan类型的关系节点，返回是否是阶段转换节点
    } else if (r instanceof org.apache.calcite.rel.core.Values) { // else if判断：检查r是否是Values类型，Values是常量值操作节点
      return provider1.isPhaseTransition((org.apache.calcite.rel.core.Values) r, mq); // 调用provider1的isPhaseTransition方法，传入Values类型的关系节点，返回是否是阶段转换节点
    } else if (r instanceof org.apache.calcite.rel.RelNode) { // else if判断：检查r是否是RelNode类型，这是通用的关系节点类型，作为兜底处理
      return provider1.isPhaseTransition((org.apache.calcite.rel.RelNode) r, mq); // 调用provider1的isPhaseTransition方法，传入RelNode类型的关系节点，返回是否是阶段转换节点
    } else { // else分支：如果没有匹配到任何已知的类型
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.lang.Boolean org.apache.calcite.rel.metadata.BuiltInMetadata$Parallelism$Handler.isPhaseTransition(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出非法参数异常，说明没有找到匹配的处理器，并建议创建一个通用的RelNode处理器
    }
  }
  public java.lang.Integer splitCount( // 声明splitCount方法，返回Integer类型，该方法用于获取关系节点的分片数量
      org.apache.calcite.rel.RelNode r, // 参数r：关系节点，表示要查询的RelNode对象
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq：元数据查询对象，用于访问元数据缓存和执行元数据查询
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) { // while循环：检查r是否是DelegatingMetadataRel类型，如果是则说明这是一个委托元数据关系节点
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 获取委托关系节点的实际委托节点，将r替换为委托的目标节点，继续循环检查直到找到非委托节点
    }
    final Object key; // 声明一个final的Object类型变量key，用于存储缓存键
    key = methodKey1; // 将methodKey1赋值给key，methodKey1是splitCount方法的缓存键
    final Object v = mq.map.get(r, key); // 从元数据查询对象的缓存map中获取关系节点r和键key对应的缓存值v
    if (v != null) { // if判断：检查缓存值v是否不为null，如果不为null说明缓存中存在该元数据值
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // 内层if：检查v是否等于ACTIVE标记，ACTIVE表示该元数据正在计算中
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出循环元数据异常，说明检测到循环依赖，元数据计算出现了循环调用
      }
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // 内层if：检查v是否等于INSTANCE标记，INSTANCE表示该元数据值为null
        return null; // 返回null，表示该元数据值为null
      }
      return (java.lang.Integer) v; // 将缓存值v强制转换为Integer类型并返回，直接使用缓存的结果
    }
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 将关系节点r、键key和ACTIVE标记放入缓存，表示该元数据正在计算中，用于检测循环依赖
    try { // try块：开始执行元数据计算
      final java.lang.Integer x = splitCount_(r, mq); // 调用splitCount_私有方法计算实际的元数据值，将结果存储在变量x中
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果x放入缓存，使用mask方法处理null值，如果x为null则放入INSTANCE标记
      return x; // 返回计算结果x
    } catch (java.lang.Exception e) { // catch块：捕获计算过程中可能抛出的异常
      mq.map.row(r).clear(); // 清除关系节点r在缓存中的所有数据，因为计算失败，缓存数据不可靠
      throw e; // 重新抛出异常，让调用者处理
    }
  }

  private java.lang.Integer splitCount_( // 声明私有的splitCount_方法，返回Integer类型，这是实际执行元数据计算的实现方法
      org.apache.calcite.rel.RelNode r, // 参数r：关系节点，表示要查询的RelNode对象
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq：元数据查询对象，用于访问元数据缓存和执行元数据查询
    if (r instanceof org.apache.calcite.rel.RelNode) { // if判断：检查r是否是RelNode类型，这是通用的关系节点类型，作为兜底处理
      return provider1.splitCount((org.apache.calcite.rel.RelNode) r, mq); // 调用provider1的splitCount方法，传入RelNode类型的关系节点，返回分片数量
    } else { // else分支：如果没有匹配到任何已知的类型
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.lang.Integer org.apache.calcite.rel.metadata.BuiltInMetadata$Parallelism$Handler.splitCount(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出非法参数异常，说明没有找到匹配的处理器，并建议创建一个通用的RelNode处理器
    }
  }

}
