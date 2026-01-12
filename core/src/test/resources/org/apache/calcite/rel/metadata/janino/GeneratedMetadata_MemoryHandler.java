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
// Apache 许可证头部，声明版权信息和使用条款
package org.apache.calcite.rel.metadata.janino; // 包声明：该类属于 org.apache.calcite.rel.metadata.janino 包，用于存放通过 Janino 动态生成的元数据处理相关代码

// GeneratedMetadata_MemoryHandler 类：这是一个自动生成的处理器类，实现了 BuiltInMetadata.Memory.Handler 接口
// 主要功能：作为 Memory 元数据的处理器，负责缓存和分发内存相关的元数据计算请求
// 核心作用：通过缓存机制优化元数据查询性能，避免重复计算，同时支持循环依赖检测
// 生成方式：该类由 RelMetadataHandlerGenerator 在运行时通过 Janino 编译器动态生成
// 设计模式：采用装饰器模式和缓存代理模式，在 provider1 之上添加了缓存和循环检测功能
public final class GeneratedMetadata_MemoryHandler // 声明为 final 类，防止被继承，确保生成的元数据处理器的行为一致性
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.Memory.Handler { // 实现 BuiltInMetadata.Memory.Handler 接口，提供内存元数据的处理能力
  // methodKey0：用于缓存 cumulativeMemoryWithinPhase 方法的缓存键
  // 作用：在元数据缓存中唯一标识 cumulativeMemoryWithinPhase 方法的计算结果
  // 类型：Object 类型，实际使用 DescriptiveCacheKey 对象，包含方法签名的描述信息
  // 初始化：创建一个描述性缓存键，键值为 "Double Handler.cumulativeMemoryWithinPhase()"
  // 缓存机制：该键与 RelNode 对象一起作为复合键，用于在 mq.map 中存储和检索计算结果
  private final Object methodKey0 = // 声明为 final，确保缓存键在对象生命周期内不变，保证缓存一致性
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Double Handler.cumulativeMemoryWithinPhase()"); // 创建 DescriptiveCacheKey 对象，参数为方法的描述字符串，用于调试和日志输出
  // methodKey1：用于缓存 cumulativeMemoryWithinPhaseSplit 方法的缓存键
  // 作用：在元数据缓存中唯一标识 cumulativeMemoryWithinPhaseSplit 方法的计算结果
  // 类型：Object 类型，实际使用 DescriptiveCacheKey 对象
  // 初始化：创建一个描述性缓存键，键值为 "Double Handler.cumulativeMemoryWithinPhaseSplit()"
  private final Object methodKey1 = // 声明为 final，确保缓存键在对象生命周期内不变
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Double Handler.cumulativeMemoryWithinPhaseSplit()"); // 创建 DescriptiveCacheKey 对象，参数为方法的描述字符串
  // methodKey2：用于缓存 memory 方法的缓存键
  // 作用：在元数据缓存中唯一标识 memory 方法的计算结果
  // 类型：Object 类型，实际使用 DescriptiveCacheKey 对象
  // 初始化：创建一个描述性缓存键，键值为 "Double Handler.memory()"
  private final Object methodKey2 = // 声明为 final，确保缓存键在对象生命周期内不变
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Double Handler.memory()"); // 创建 DescriptiveCacheKey 对象，参数为方法的描述字符串
  // provider1：实际的元数据提供者对象，负责执行真正的元数据计算逻辑
  // 作用：持有 RelMdMemory 实例，该实例包含所有内存元数据计算的具体实现
  // 类型：RelMdMemory 类型，声明为 public final，确保引用不可变但可被外部访问
  // 关系：这是委托对象，GeneratedMetadata_MemoryHandler 在 provider1 之上添加了缓存和循环检测功能
  // 初始化：通过构造函数注入，支持依赖注入模式，便于测试和扩展
  public final org.apache.calcite.rel.metadata.RelMdMemory provider1; // 声明为 public final，允许外部访问但不可修改引用
  // 构造方法：初始化 GeneratedMetadata_MemoryHandler 实例
  // 参数：provider1 - RelMdMemory 类型的元数据提供者对象，包含实际的元数据计算逻辑
  // 作用：将传入的 provider1 保存到成员变量中，建立委托关系
  // 设计：采用依赖注入模式，提高了代码的可测试性和灵活性
  public GeneratedMetadata_MemoryHandler( // 构造方法声明，接收 RelMdMemory 类型的参数
      org.apache.calcite.rel.metadata.RelMdMemory provider1) { // 参数：provider1，实际的元数据提供者对象
    this.provider1 = provider1; // 将传入的 provider1 赋值给成员变量，建立委托关系，后续所有元数据计算都通过 provider1 执行
  }
  // getDef 方法：获取元数据定义
  // 返回值：MetadataDef 对象，描述该元数据处理的定义信息
  // 作用：返回 provider1 的元数据定义，用于元数据注册和查询
  // 委托：直接委托给 provider1.getDef() 方法，不添加额外逻辑
  public org.apache.calcite.rel.metadata.MetadataDef getDef() { // 方法声明，返回 MetadataDef 类型
    return provider1.getDef(); // 直接调用 provider1 的 getDef 方法，返回元数据定义对象
  }
  // cumulativeMemoryWithinPhase 方法：计算关系节点在当前阶段内的累积内存使用量（带缓存和循环检测）
  // 参数：r - RelNode 类型的关系节点，表示要计算元数据的关系表达式
  // 参数：mq - RelMetadataQuery 类型的元数据查询对象，用于访问元数据缓存和执行其他元数据查询
  // 返回值：Double 类型，表示累积内存使用量，如果无法计算则返回 null
  // 作用：计算并缓存关系节点在当前阶段内的累积内存使用量
  // 缓存策略：使用 methodKey0 作为缓存键，在 mq.map 中缓存计算结果
  // 循环检测：通过 ACTIVE 标记检测循环依赖，避免无限递归
  // 异常处理：捕获计算异常，清除缓存后重新抛出
  public java.lang.Double cumulativeMemoryWithinPhase( // 方法声明，接收 RelNode 和 RelMetadataQuery 参数，返回 Double 类型
      org.apache.calcite.rel.RelNode r, // 参数：r，关系节点对象，表示要计算元数据的关系表达式
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数：mq，元数据查询对象，提供缓存访问和其他元数据查询功能
    // 循环处理委托元数据关系节点
    // 作用：如果关系节点是 DelegatingMetadataRel 类型，则获取其委托的真正关系节点
    // 原因：DelegatingMetadataRel 是包装器，需要解包到底层的真实关系节点才能计算元数据
    // 循环：使用 while 循环，因为可能存在多层包装
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) { // 检查 r 是否为 DelegatingMetadataRel 类型
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 获取委托的真正关系节点，解包一层
    }
    final Object key; // 声明缓存键变量，用于在缓存中存储和检索计算结果
    key = methodKey0; // 将 methodKey0 赋值给 key，使用预先定义的缓存键
    final Object v = mq.map.get(r, key); // 从缓存中获取计算结果，使用关系节点 r 和键 key 作为复合键
    // 检查缓存命中情况
    // 作用：判断缓存中是否已有计算结果
    // 逻辑：如果 v 不为 null，说明缓存命中，需要进一步判断是哪种结果
    if (v != null) { // 检查缓存值是否不为 null
      // 检测循环依赖
      // 作用：如果缓存值为 ACTIVE，说明当前方法正在计算中，存在循环依赖
      // 处理：抛出 CyclicMetadataException 异常，中断计算
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // 检查缓存值是否为 ACTIVE 标记
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出循环元数据异常，表示存在循环依赖
      }
      // 检测 null 结果
      // 作用：如果缓存值为 INSTANCE，说明之前计算的结果为 null
      // 处理：返回 null，表示无法计算该元数据
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // 检查缓存值是否为 INSTANCE 标记
        return null; // 返回 null，表示元数据无法计算
      }
      return (java.lang.Double) v; // 缓存命中且结果有效，将缓存值转换为 Double 类型并返回
    }
    // 标记计算开始
    // 作用：在缓存中放置 ACTIVE 标记，表示当前方法正在计算
    // 目的：用于循环依赖检测，如果后续再次访问该键，说明存在循环
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 将 ACTIVE 标记放入缓存，使用关系节点 r 和键 key
    try { // 开始 try 块，用于捕获计算过程中的异常
      final java.lang.Double x = cumulativeMemoryWithinPhase_(r, mq); // 调用实际的计算方法 cumulativeMemoryWithinPhase_，传入关系节点和元数据查询对象
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果存入缓存，使用 mask 方法处理 null 值
      return x; // 返回计算结果
    } catch (java.lang.Exception e) { // 捕获计算过程中的任何异常
      mq.map.row(r).clear(); // 清除该关系节点的所有缓存，避免缓存不一致
      throw e; // 重新抛出异常，让调用者处理
    }
  }

  // cumulativeMemoryWithinPhase_ 方法：实际执行累积内存计算的方法（内部实现）
  // 参数：r - RelNode 类型的关系节点
  // 参数：mq - RelMetadataQuery 类型的元数据查询对象
  // 返回值：Double 类型，表示累积内存使用量
  // 作用：委托给 provider1 执行真正的元数据计算
  // 逻辑：检查关系节点类型，调用 provider1 的对应方法
  // 异常：如果关系节点类型不匹配，抛出 IllegalArgumentException
  private java.lang.Double cumulativeMemoryWithinPhase_( // 私有方法，表示内部实现，不对外暴露
      org.apache.calcite.rel.RelNode r, // 参数：r，关系节点对象
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数：mq，元数据查询对象
    if (r instanceof org.apache.calcite.rel.RelNode) { // 检查 r 是否为 RelNode 类型（总是为 true，这是类型检查的示例）
      return provider1.cumulativeMemoryWithinPhase((org.apache.calcite.rel.RelNode) r, mq); // 委托给 provider1 执行计算，传入关系节点和元数据查询对象
    } else { // 如果类型不匹配（理论上不会执行）
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.lang.Double org.apache.calcite.rel.metadata.BuiltInMetadata$Memory$Handler.cumulativeMemoryWithinPhase(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出异常，提示没有对应的处理器
    }
  }
  // cumulativeMemoryWithinPhaseSplit 方法：计算关系节点在当前阶段内的累积内存使用量（带拆分）（带缓存和循环检测）
  // 参数：r - RelNode 类型的关系节点
  // 参数：mq - RelMetadataQuery 类型的元数据查询对象
  // 返回值：Double 类型，表示累积内存使用量（带拆分）
  // 作用：计算并缓存关系节点在当前阶段内的累积内存使用量（带拆分）
  // 缓存策略：使用 methodKey1 作为缓存键
  // 循环检测：通过 ACTIVE 标记检测循环依赖
  public java.lang.Double cumulativeMemoryWithinPhaseSplit( // 方法声明，接收 RelNode 和 RelMetadataQuery 参数，返回 Double 类型
      org.apache.calcite.rel.RelNode r, // 参数：r，关系节点对象
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数：mq，元数据查询对象
    // 循环处理委托元数据关系节点
    // 作用：解包 DelegatingMetadataRel 包装器，获取底层的关系节点
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) { // 检查是否为委托类型
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 获取委托的真正关系节点
    }
    final Object key; // 声明缓存键变量
    key = methodKey1; // 将 methodKey1 赋值给 key
    final Object v = mq.map.get(r, key); // 从缓存中获取计算结果
    // 检查缓存命中情况
    if (v != null) { // 检查缓存值是否不为 null
      // 检测循环依赖
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // 检查是否为 ACTIVE 标记
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出循环异常
      }
      // 检测 null 结果
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // 检查是否为 INSTANCE 标记
        return null; // 返回 null
      }
      return (java.lang.Double) v; // 返回缓存的结果
    }
    // 标记计算开始
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 将 ACTIVE 标记放入缓存
    try { // 开始 try 块
      final java.lang.Double x = cumulativeMemoryWithinPhaseSplit_(r, mq); // 调用实际的计算方法
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果存入缓存
      return x; // 返回计算结果
    } catch (java.lang.Exception e) { // 捕获异常
      mq.map.row(r).clear(); // 清除缓存
      throw e; // 重新抛出异常
    }
  }

  // cumulativeMemoryWithinPhaseSplit_ 方法：实际执行累积内存计算（带拆分）的方法（内部实现）
  // 参数：r - RelNode 类型的关系节点
  // 参数：mq - RelMetadataQuery 类型的元数据查询对象
  // 返回值：Double 类型，表示累积内存使用量（带拆分）
  // 作用：委托给 provider1 执行真正的元数据计算
  private java.lang.Double cumulativeMemoryWithinPhaseSplit_( // 私有方法，内部实现
      org.apache.calcite.rel.RelNode r, // 参数：r，关系节点对象
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数：mq，元数据查询对象
    if (r instanceof org.apache.calcite.rel.RelNode) { // 检查类型
      return provider1.cumulativeMemoryWithinPhaseSplit((org.apache.calcite.rel.RelNode) r, mq); // 委托给 provider1 执行计算
    } else { // 类型不匹配
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.lang.Double org.apache.calcite.rel.metadata.BuiltInMetadata$Memory$Handler.cumulativeMemoryWithinPhaseSplit(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出异常
    }
  }
  // memory 方法：计算关系节点的内存使用量（带缓存和循环检测）
  // 参数：r - RelNode 类型的关系节点
  // 参数：mq - RelMetadataQuery 类型的元数据查询对象
  // 返回值：Double 类型，表示内存使用量
  // 作用：计算并缓存关系节点的内存使用量
  // 缓存策略：使用 methodKey2 作为缓存键
  // 循环检测：通过 ACTIVE 标记检测循环依赖
  public java.lang.Double memory( // 方法声明，接收 RelNode 和 RelMetadataQuery 参数，返回 Double 类型
      org.apache.calcite.rel.RelNode r, // 参数：r，关系节点对象
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数：mq，元数据查询对象
    // 循环处理委托元数据关系节点
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) { // 检查是否为委托类型
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 获取委托的真正关系节点
    }
    final Object key; // 声明缓存键变量
    key = methodKey2; // 将 methodKey2 赋值给 key
    final Object v = mq.map.get(r, key); // 从缓存中获取计算结果
    // 检查缓存命中情况
    if (v != null) { // 检查缓存值是否不为 null
      // 检测循环依赖
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // 检查是否为 ACTIVE 标记
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出循环异常
      }
      // 检测 null 结果
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // 检查是否为 INSTANCE 标记
        return null; // 返回 null
      }
      return (java.lang.Double) v; // 返回缓存的结果
    }
    // 标记计算开始
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 将 ACTIVE 标记放入缓存
    try { // 开始 try 块
      final java.lang.Double x = memory_(r, mq); // 调用实际的计算方法
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果存入缓存
      return x; // 返回计算结果
    } catch (java.lang.Exception e) { // 捕获异常
      mq.map.row(r).clear(); // 清除缓存
      throw e; // 重新抛出异常
    }
  }

  // memory_ 方法：实际执行内存计算的方法（内部实现）
  // 参数：r - RelNode 类型的关系节点
  // 参数：mq - RelMetadataQuery 类型的元数据查询对象
  // 返回值：Double 类型，表示内存使用量
  // 作用：委托给 provider1 执行真正的元数据计算
  private java.lang.Double memory_( // 私有方法，内部实现
      org.apache.calcite.rel.RelNode r, // 参数：r，关系节点对象
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数：mq，元数据查询对象
    if (r instanceof org.apache.calcite.rel.RelNode) { // 检查类型
      return provider1.memory((org.apache.calcite.rel.RelNode) r, mq); // 委托给 provider1 执行计算
    } else { // 类型不匹配
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.lang.Double org.apache.calcite.rel.metadata.BuiltInMetadata$Memory$Handler.memory(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出异常
    }
  }

} // 类定义结束
