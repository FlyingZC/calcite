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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.rel.metadata.janino; // 声明包名，该类位于org.apache.calcite.rel.metadata.janino包下，用于Janino编译生成的元数据处理器

public final class GeneratedMetadata_ExplainVisibilityHandler // 这是一个final类，表示不可被继承，类名为GeneratedMetadata_ExplainVisibilityHandler，由Janino编译器自动生成的元数据处理器
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.ExplainVisibility.Handler { // 实现BuiltInMetadata.ExplainVisibility.Handler接口，该接口定义了处理ExplainVisibility元数据的方法
  private final Object methodKey0Null = // 定义一个私有的final成员变量，用于缓存当参数a2为null时的缓存键，final表示初始化后不可修改
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Boolean Handler.isVisibleInExplain(null)"); // 创建一个描述性缓存键对象，键值为"Boolean Handler.isVisibleInExplain(null)"，表示当SqlExplainLevel参数为null时的缓存标识
  private final Object[] methodKey0 = // 定义一个私有的final对象数组，用于缓存不同SqlExplainLevel枚举值对应的缓存键
      org.apache.calcite.rel.metadata.janino.CacheUtil.generateEnum("Boolean isVisibleInExplain", org.apache.calcite.sql.SqlExplainLevel.values()); // 使用CacheUtil工具类生成枚举缓存键数组，第一个参数是方法描述，第二个参数是SqlExplainLevel的所有枚举值，每个枚举值对应一个缓存键
  public final org.apache.calcite.rel.metadata.RelMdExplainVisibility provider0; // 定义一个公有的final成员变量，引用RelMdExplainVisibility提供者实例，该提供者包含实际的元数据计算逻辑
  public GeneratedMetadata_ExplainVisibilityHandler( // 构造方法，用于创建GeneratedMetadata_ExplainVisibilityHandler实例
      org.apache.calcite.rel.metadata.RelMdExplainVisibility provider0) { // 构造方法参数，接收RelMdExplainVisibility提供者实例
    this.provider0 = provider0; // 将传入的provider0参数赋值给成员变量provider0，保存元数据提供者引用
  }
  public org.apache.calcite.rel.metadata.MetadataDef getDef() { // 公有方法，返回元数据定义对象MetadataDef，用于描述该元数据处理器的基本信息
    return provider0.getDef(); // 委托给provider0调用其getDef()方法，返回元数据定义
  }
  public java.lang.Boolean isVisibleInExplain( // 公有方法，判断关系节点在指定解释级别下是否可见，返回Boolean类型结果
      org.apache.calcite.rel.RelNode r, // 参数r：关系节点对象，表示要检查的RelNode
      org.apache.calcite.rel.metadata.RelMetadataQuery mq, // 参数mq：元数据查询对象，用于缓存和查询元数据
      org.apache.calcite.sql.SqlExplainLevel a2) { // 参数a2：SQL解释级别枚举，表示查询计划解释的详细程度
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) { // while循环：检查r是否是DelegatingMetadataRel类型的实例，如果是则继续循环
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 将r设置为委托关系节点，通过getMetadataDelegateRel()方法获取实际要处理的关系节点，跳过委托层
    }
    final Object key; // 声明一个final对象变量key，用于存储缓存键
    if (a2 == null) { // 判断参数a2是否为null
      key = methodKey0Null; // 如果a2为null，则使用methodKey0Null作为缓存键
    } else { // 如果a2不为null
      key = methodKey0[a2.ordinal()]; // 使用a2的序数作为索引，从methodKey0数组中获取对应的缓存键
    }
    final Object v = mq.map.get(r, key); // 从元数据查询对象的缓存map中，根据关系节点r和缓存键key获取缓存的值
    if (v != null) { // 如果缓存值v不为null，说明缓存命中
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // 检查缓存值是否等于ACTIVE标记，表示当前正在计算中，存在循环依赖
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出循环元数据异常，说明检测到元数据计算循环
      }
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // 检查缓存值是否等于INSTANCE标记，表示计算结果为null
        return null; // 返回null，表示该关系节点在指定解释级别下不可见
      }
      return (java.lang.Boolean) v; // 缓存值是实际计算结果，强制转换为Boolean类型并返回
    }
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 将ACTIVE标记放入缓存，表示开始计算该元数据，用于检测循环依赖
    try { // try块开始，用于捕获计算过程中的异常
      final java.lang.Boolean x = isVisibleInExplain_(r, mq, a2); // 调用私有方法isVisibleInExplain_进行实际计算，结果保存在变量x中
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果存入缓存，使用NullSentinel.mask()方法处理null值
      return x; // 返回计算结果x
    } catch (java.lang.Exception e) { // catch块捕获所有类型的异常
      mq.map.row(r).clear(); // 发生异常时，清除该关系节点r的所有缓存值，防止缓存被污染
      throw e; // 重新抛出捕获的异常
    }
  }

  private java.lang.Boolean isVisibleInExplain_( // 私有方法，实际执行isVisibleInExplain逻辑的核心实现，通过类型分发调用provider0的不同重载方法
      org.apache.calcite.rel.RelNode r, // 参数r：关系节点对象
      org.apache.calcite.rel.metadata.RelMetadataQuery mq, // 参数mq：元数据查询对象
      org.apache.calcite.sql.SqlExplainLevel a2) { // 参数a2：SQL解释级别
    if (r instanceof org.apache.calcite.rel.core.TableScan) { // 判断关系节点r是否是TableScan类型
      return provider0.isVisibleInExplain((org.apache.calcite.rel.core.TableScan) r, mq, a2); // 调用provider0的TableScan重载方法，将r强制转换为TableScan类型
    } else if (r instanceof org.apache.calcite.rel.RelNode) { // 判断关系节点r是否是RelNode类型（通用类型，作为兜底处理）
      return provider0.isVisibleInExplain((org.apache.calcite.rel.RelNode) r, mq, a2); // 调用provider0的RelNode通用重载方法，将r强制转换为RelNode类型
    } else { // 如果关系节点r不属于任何已知的处理类型
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.lang.Boolean org.apache.calcite.rel.metadata.BuiltInMetadata$ExplainVisibility$Handler.isVisibleInExplain(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery,org.apache.calcite.sql.SqlExplainLevel)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出非法参数异常，提示没有找到对应类型[" + r.getClass() + "]的处理方法，建议创建一个通用的RelNode处理器
    }
  }

} // 类结束，花括号结束GeneratedMetadata_ExplainVisibilityHandler类的定义
