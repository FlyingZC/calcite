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
package org.apache.calcite.rel.metadata.janino; // 定义包名，该类位于 org.apache.calcite.rel.metadata.janino 包下，这是 Calcite 元数据系统中使用 Janino 编译器动态生成的代码所在的包

// GeneratedMetadata_ColumnOriginHandler 类：这是一个由 Calcite 元数据系统自动生成的处理器类
// 作用：实现了 BuiltInMetadata.ColumnOrigin.Handler 接口，用于处理列起源(Column Origin)元数据查询
// 列起源元数据用于追踪查询结果中每一列的来源，即该列最终来自基表的哪些列
// 该类是 Calcite 元数据框架的核心组件之一，通过动态生成代码来提高元数据查询的性能
// 该类采用 final 修饰，表示不可被继承，确保生成的代码不会被修改
// 实现了 Handler 接口，提供了 getColumnOrigins 方法来获取指定列的起源信息
// 该类内部实现了缓存机制，避免重复计算相同的元数据
// 该类通过类型检查和分发，将元数据查询委托给具体的 RelMdColumnOrigins 提供者
public final class GeneratedMetadata_ColumnOriginHandler
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.ColumnOrigin.Handler { // 实现 BuiltInMetadata.ColumnOrigin.Handler 接口，该接口定义了获取列起源元数据的方法

  // methodKey0：方法缓存键，用于标识 getColumnOrigins 方法
  // 作用：作为元数据缓存的基础键，配合参数值一起组成完整的缓存键
  // 类型：Object，实际上是 DescriptiveCacheKey 实例
  // 值：描述性缓存键，包含方法签名信息 "Set Handler.getColumnOrigins(RelNode, RelMetadataQuery, int)"
  // 说明：这个键在缓存查找时作为基础标识，需要结合参数值才能唯一确定一个缓存条目
  private final Object methodKey0 =
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Set Handler.getColumnOrigins(RelNode, RelMetadataQuery, int)"); // 创建描述性缓存键对象，包含方法签名信息

  // methodKey0FlyWeight：享元模式的缓存键数组，用于优化小范围内的参数值缓存
  // 作用：为 -256 到 255 范围内的列索引预先生成缓存键，避免为每个参数值都创建新的键对象
  // 类型：Object[]，包含 512 个预生成的缓存键对象
  // 说明：这种优化技术称为享元模式(Flyweight Pattern)，可以显著减少对象创建和内存分配
  // 范围：-256 到 255，覆盖了大多数常见的列索引范围
  // 使用：当列索引在 -256 到 255 之间时，直接使用预生成的键，否则动态创建新键
  private final Object[] methodKey0FlyWeight =
      org.apache.calcite.rel.metadata.janino.CacheUtil.generateRange("java.util.Set getColumnOrigins", -256, 256); // 调用 CacheUtil 工具类生成享元缓存键数组，范围从 -256 到 255

  // provider0：列起源元数据提供者，实际执行元数据计算的对象
  // 作用：包含各种关系操作符(Aggregate、Filter、Project 等)的列起源计算逻辑
  // 类型：RelMdColumnOrigins，这是 Calcite 中定义的列起源元数据提供者类
  // 说明：该对象包含了针对不同类型关系节点的列起源计算方法，如 Aggregate、Filter、Project 等
  // 重要性：这是整个元数据查询的核心，所有实际的列起源计算都委托给这个对象
  // 设计模式：委托模式，将具体的计算逻辑委托给专门的处理类
  public final org.apache.calcite.rel.metadata.RelMdColumnOrigins provider0; // 声明列起源元数据提供者对象，使用 public final 修饰确保不可被修改

  // GeneratedMetadata_ColumnOriginHandler 构造方法：创建列起源元数据处理器实例
  // 作用：初始化处理器，设置元数据提供者
  // 参数：provider0 - RelMdColumnOrigins 类型的元数据提供者对象
  // 说明：构造方法接收一个 RelMdColumnOrigins 对象，将其保存到成员变量 provider0 中
  // 依赖注入：通过构造函数注入依赖，确保处理器拥有正确的元数据提供者
  // 设计原则：依赖注入原则，便于测试和模块化
  public GeneratedMetadata_ColumnOriginHandler( // 构造方法声明，接收 RelMdColumnOrigins 类型参数
      org.apache.calcite.rel.metadata.RelMdColumnOrigins provider0) { // 参数声明：provider0 是列起源元数据提供者对象
    this.provider0 = provider0; // 将传入的 provider0 参数赋值给成员变量 provider0，完成依赖注入
  }

  // getDef 方法：获取元数据定义
  // 作用：返回该处理器处理的元数据的定义信息
  // 返回值：MetadataDef 类型，包含元数据的名称、类型等信息
  // 说明：元数据定义描述了元数据的特征，如名称、参数类型、返回类型等
  // 委托：直接调用 provider0.getDef() 方法，委托给元数据提供者
  // 用途：用于元数据注册、查询和验证
  public org.apache.calcite.rel.metadata.MetadataDef getDef() { // 方法声明，返回 MetadataDef 类型
    return provider0.getDef(); // 调用 provider0 的 getDef 方法，返回元数据定义
  }

  // getColumnOrigins 方法：获取指定关系节点中某列的起源信息
  // 作用：这是元数据查询的入口方法，实现了缓存机制和循环检测
  // 参数：
  //   r - RelNode 类型，要查询的关系节点(如 TableScan、Filter、Project 等)
  //   mq - RelMetadataQuery 类型，元数据查询上下文，包含缓存等信息
  //   a2 - int 类型，要查询的列索引
  // 返回值：Set 类型，包含该列的起源信息(通常是 RelColumnOrigin 对象的集合)
  // 说明：
  //   1. 首先处理委托节点，获取实际的底层节点
  //   2. 然后检查缓存，如果缓存命中则直接返回
  //   3. 如果缓存未命中，则标记为计算中(防止循环依赖)
  //   4. 调用内部方法 getColumnOrigins_ 进行实际计算
  //   5. 将计算结果存入缓存
  //   6. 如果发生异常，清除该节点的所有缓存
  // 核心功能：实现了元数据查询的缓存、循环检测和异常处理
  public java.util.Set getColumnOrigins( // 方法声明，返回 Set 类型
      org.apache.calcite.rel.RelNode r, // 参数1：关系节点，表示要查询的关系操作符
      org.apache.calcite.rel.metadata.RelMetadataQuery mq, // 参数2：元数据查询上下文，包含缓存和查询状态
      int a2) { // 参数3：列索引，表示要查询哪一列的起源信息
    // 循环处理委托节点，获取实际的底层节点
    // 作用：DelegatingMetadataRel 是一种包装节点，它会将元数据查询委托给内部的实际节点
    // 说明：需要不断解包，直到找到非委托的底层节点
    // 目的：确保查询的是真正的关系节点，而不是包装节点
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) { // 当 r 是 DelegatingMetadataRel 类型时循环
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 获取内部的实际节点，继续循环检查
    }

    // 声明缓存键变量
    // 作用：用于在缓存中查找或存储元数据结果
    // 类型：Object，可能是预生成的键或动态生成的键
    final Object key; // 声明缓存键变量，使用 final 修饰表示不可重新赋值

    // 根据列索引范围选择缓存键生成策略
    // 作用：使用享元模式优化，小范围内的索引使用预生成的键，大范围索引动态生成键
    // 条件：a2 >= -256 && a2 < 256，即列索引在 -256 到 255 之间
    // 优化：预生成的键可以减少对象创建和内存分配，提高性能
    if (a2 >= -256 && a2 < 256) { // 如果列索引在享元范围内
      key = methodKey0FlyWeight[a2 + 256]; // 从预生成的键数组中获取对应的键，a2 + 256 是数组索引偏移量
    } else { // 如果列索引超出享元范围
      key = org.apache.calcite.runtime.FlatLists.of(methodKey0, a2); // 动态生成缓存键，使用 FlatLists.of 方法创建包含方法键和参数的列表
    }

    // 从缓存中查找元数据结果
    // 作用：检查缓存中是否已经计算过该元数据
    // 参数：r - 关系节点，key - 缓存键
    // 返回值：缓存中的值，可能为 null、ACTIVE 标记、INSTANCE 标记或实际结果
    final Object v = mq.map.get(r, key); // 调用缓存 map 的 get 方法，查找缓存值

    // 检查缓存是否命中
    // 作用：如果缓存中有值，需要判断值的类型并采取相应操作
    if (v != null) { // 如果缓存值不为 null
      // 检查是否为正在计算标记(ACTIVE)
      // 作用：检测循环依赖，防止无限递归
      // 说明：如果值为 ACTIVE，说明该元数据正在计算中，再次调用说明存在循环依赖
      // 异常：抛出 CyclicMetadataException，表示检测到循环元数据依赖
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // 如果缓存值是 ACTIVE 标记
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出循环元数据异常
      }

      // 检查是否为 null 值标记(INSTANCE)
      // 作用：区分真正的 null 值和缓存未命中
      // 说明：NullSentinel.INSTANCE 用于在缓存中表示 null 值
      // 返回：返回 null，表示该列没有起源信息
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // 如果缓存值是 INSTANCE 标记
        return null; // 返回 null，表示该列没有起源信息
      }

      // 返回缓存中的实际结果
      // 作用：缓存命中，直接返回之前计算的结果
      // 类型转换：将 Object 类型强制转换为 Set 类型
      return (java.util.Set) v; // 返回缓存中的 Set 类型结果
    }

    // 标记该元数据为正在计算状态
    // 作用：在开始计算前，将缓存值设置为 ACTIVE，用于检测循环依赖
    // 参数：r - 关系节点，key - 缓存键，ACTIVE - 正在计算标记
    // 说明：这一步必须在开始计算之前完成，这样才能正确检测循环
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 将缓存值设置为 ACTIVE 标记

    // 执行实际计算，并处理异常
    // 作用：调用内部方法进行元数据计算，并处理可能的异常
    // 说明：使用 try-catch 确保异常时能正确清理缓存
    try { // 开始 try 块
      // 调用内部方法进行实际的元数据计算
      // 作用：根据关系节点类型，调用对应的列起源计算逻辑
      // 方法：getColumnOrigins_ 是内部方法，实现了类型分发和具体计算
      final java.util.Set x = getColumnOrigins_(r, mq, a2); // 调用内部方法获取列起源信息，结果存储在变量 x 中

      // 将计算结果存入缓存
      // 作用：缓存计算结果，避免重复计算
      // 参数：r - 关系节点，key - 缓存键，masked(x) - 掩码处理后的结果
      // 说明：使用 NullSentinel.mask 方法处理结果，将 null 转换为 INSTANCE 标记
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果存入缓存，处理可能的 null 值

      // 返回计算结果
      return x; // 返回列起源信息集合
    } catch (java.lang.Exception e) { // 捕获所有异常
      // 清除该节点的所有缓存
      // 作用：异常发生时，清除该节点的所有缓存条目，避免使用不完整或错误的数据
      // 说明：row(r) 获取该节点的所有缓存条目，clear() 清除所有条目
      // 原因：异常可能导致计算中断，缓存的数据可能不完整或错误
      mq.map.row(r).clear(); // 清除该关系节点的所有缓存

      // 重新抛出异常
      throw e; // 重新抛出捕获的异常
    }
  }

  // getColumnOrigins_ 方法：内部方法，根据关系节点类型执行实际的列起源计算
  // 作用：这是类型分发方法，根据关系节点的具体类型，调用对应的列起源计算方法
  // 参数：
  //   r - RelNode 类型，要查询的关系节点
  //   mq - RelMetadataQuery 类型，元数据查询上下文
  //   a2 - int 类型，列索引
  // 返回值：Set 类型，列起源信息集合
  // 说明：
  //   该方法使用 if-else 链进行类型检查和分发
  //   每种关系操作符类型都有对应的列起源计算逻辑
  //   最后有一个通用的 RelNode 处理器作为兜底
  //   如果没有匹配的类型，抛出 IllegalArgumentException
  // 设计模式：访问者模式的变体，通过类型检查实现分发
  private java.util.Set getColumnOrigins_( // 方法声明，私有方法，返回 Set 类型
      org.apache.calcite.rel.RelNode r, // 参数1：关系节点
      org.apache.calcite.rel.metadata.RelMetadataQuery mq, // 参数2：元数据查询上下文
      int a2) { // 参数3：列索引
    // 检查是否为 Aggregate 聚合节点
    // 作用：聚合节点对数据进行分组和聚合，列起源需要特殊处理
    // 说明：聚合列通常来自 GROUP BY 的列或聚合函数的参数列
    // 分发：调用 provider0 的 getColumnOrigins 方法，传入 Aggregate 类型的节点
    if (r instanceof org.apache.calcite.rel.core.Aggregate) { // 如果 r 是 Aggregate 类型
      return provider0.getColumnOrigins((org.apache.calcite.rel.core.Aggregate) r, mq, a2); // 调用 provider0 的 Aggregate 版本方法
    } else if (r instanceof org.apache.calcite.rel.core.Calc) { // 如果 r 是 Calc 类型(计算节点)
      // 检查是否为 Calc 计算节点
      // 作用：Calc 节点执行表达式计算，列起源需要追踪表达式的输入列
      // 说明：Calc 节点类似于 Project，但使用更复杂的表达式语言
      // 分发：调用 provider0 的 getColumnOrigins 方法，传入 Calc 类型的节点
      return provider0.getColumnOrigins((org.apache.calcite.rel.core.Calc) r, mq, a2); // 调用 provider0 的 Calc 版本方法
    } else if (r instanceof org.apache.calcite.rel.core.Exchange) { // 如果 r 是 Exchange 类型(交换节点)
      // 检查是否为 Exchange 交换节点
      // 作用：Exchange 节点用于数据重分布，列起源直接传递
      // 说明：Exchange 不改变列的内容，只改变数据的分布
      // 分发：调用 provider0 的 getColumnOrigins 方法，传入 Exchange 类型的节点
      return provider0.getColumnOrigins((org.apache.calcite.rel.core.Exchange) r, mq, a2); // 调用 provider0 的 Exchange 版本方法
    } else if (r instanceof org.apache.calcite.rel.core.Filter) { // 如果 r 是 Filter 类型(过滤节点)
      // 检查是否为 Filter 过滤节点
      // 作用：Filter 节点根据条件过滤行，列起源直接传递
      // 说明：Filter 不改变列的内容，只过滤行
      // 分发：调用 provider0 的 getColumnOrigins 方法，传入 Filter 类型的节点
      return provider0.getColumnOrigins((org.apache.calcite.rel.core.Filter) r, mq, a2); // 调用 provider0 的 Filter 版本方法
    } else if (r instanceof org.apache.calcite.rel.core.Join) { // 如果 r 是 Join 类型(连接节点)
      // 检查是否为 Join 连接节点
      // 作用：Join 节点连接两个表，列起源需要根据连接条件确定
      // 说明：Join 的列可能来自左表、右表或两者的组合
      // 分发：调用 provider0 的 getColumnOrigins 方法，传入 Join 类型的节点
      return provider0.getColumnOrigins((org.apache.calcite.rel.core.Join) r, mq, a2); // 调用 provider0 的 Join 版本方法
    } else if (r instanceof org.apache.calcite.rel.core.Project) { // 如果 r 是 Project 类型(投影节点)
      // 检查是否为 Project 投影节点
      // 作用：Project 节点计算新的列，列起源需要追踪表达式的输入列
      // 说明：Project 是最常见的节点，用于计算派生列
      // 分发：调用 provider0 的 getColumnOrigins 方法，传入 Project 类型的节点
      return provider0.getColumnOrigins((org.apache.calcite.rel.core.Project) r, mq, a2); // 调用 provider0 的 Project 版本方法
    } else if (r instanceof org.apache.calcite.rel.core.Sample) { // 如果 r 是 Sample 类型(采样节点)
      // 检查是否为 Sample 采样节点
      // 作用：Sample 节点对数据进行采样，列起源直接传递
      // 说明：Sample 不改变列的内容，只选择部分行
      // 分发：调用 provider0 的 getColumnOrigins 方法，传入 Sample 类型的节点
      return provider0.getColumnOrigins((org.apache.calcite.rel.core.Sample) r, mq, a2); // 调用 provider0 的 Sample 版本方法
    } else if (r instanceof org.apache.calcite.rel.core.SetOp) { // 如果 r 是 SetOp 类型(集合操作节点)
      // 检查是否为 SetOp 集合操作节点
      // 作用：SetOp 节点执行 UNION、INTERSECT、EXCEPT 等集合操作
      // 说明：集合操作的列起源需要合并多个输入的列起源
      // 分发：调用 provider0 的 getColumnOrigins 方法，传入 SetOp 类型的节点
      return provider0.getColumnOrigins((org.apache.calcite.rel.core.SetOp) r, mq, a2); // 调用 provider0 的 SetOp 版本方法
    } else if (r instanceof org.apache.calcite.rel.core.Snapshot) { // 如果 r 是 Snapshot 类型(快照节点)
      // 检查是否为 Snapshot 快照节点
      // 作用：Snapshot 节点用于时态查询，列起源直接传递
      // 说明：Snapshot 不改变列的内容，只提供特定时间点的数据
      // 分发：调用 provider0 的 getColumnOrigins 方法，传入 Snapshot 类型的节点
      return provider0.getColumnOrigins((org.apache.calcite.rel.core.Snapshot) r, mq, a2); // 调用 provider0 的 Snapshot 版本方法
    } else if (r instanceof org.apache.calcite.rel.core.Sort) { // 如果 r 是 Sort 类型(排序节点)
      // 检查是否为 Sort 排序节点
      // 作用：Sort 节点对数据进行排序，列起源直接传递
      // 说明：Sort 不改变列的内容，只改变行的顺序
      // 分发：调用 provider0 的 getColumnOrigins 方法，传入 Sort 类型的节点
      return provider0.getColumnOrigins((org.apache.calcite.rel.core.Sort) r, mq, a2); // 调用 provider0 的 Sort 版本方法
    } else if (r instanceof org.apache.calcite.rel.core.TableFunctionScan) { // 如果 r 是 TableFunctionScan 类型(表函数扫描节点)
      // 检查是否为 TableFunctionScan 表函数扫描节点
      // 作用：TableFunctionScan 节点扫描表函数的结果
      // 说明：表函数的列起源通常由表函数的定义决定
      // 分发：调用 provider0 的 getColumnOrigins 方法，传入 TableFunctionScan 类型的节点
      return provider0.getColumnOrigins((org.apache.calcite.rel.core.TableFunctionScan) r, mq, a2); // 调用 provider0 的 TableFunctionScan 版本方法
    } else if (r instanceof org.apache.calcite.rel.core.TableModify) { // 如果 r 是 TableModify 类型(表修改节点)
      // 检查是否为 TableModify 表修改节点
      // 作用：TableModify 节点执行 INSERT、UPDATE、DELETE 等操作
      // 说明：表修改节点的列起源取决于具体的操作类型
      // 分发：调用 provider0 的 getColumnOrigins 方法，传入 TableModify 类型的节点
      return provider0.getColumnOrigins((org.apache.calcite.rel.core.TableModify) r, mq, a2); // 调用 provider0 的 TableModify 版本方法
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) { // 如果 r 是 TableScan 类型(表扫描节点)
      // 检查是否为 TableScan 表扫描节点
      // 作用：TableScan 节点扫描基表，列起源就是基表的列
      // 说明：这是列起源的源头，基表的列起源指向自身
      // 分发：调用 provider0 的 getColumnOrigins 方法，传入 TableScan 类型的节点
      return provider0.getColumnOrigins((org.apache.calcite.rel.core.TableScan) r, mq, a2); // 调用 provider0 的 TableScan 版本方法
    } else if (r instanceof org.apache.calcite.rel.RelNode) { // 如果 r 是通用的 RelNode 类型
      // 兜底处理：通用的 RelNode 类型
      // 作用：处理所有其他类型的关系节点
      // 说明：这是最后的兜底处理器，应该能处理所有类型的节点
      // 分发：调用 provider0 的 getColumnOrigins 方法，传入通用的 RelNode 类型
      return provider0.getColumnOrigins((org.apache.calcite.rel.RelNode) r, mq, a2); // 调用 provider0 的通用 RelNode 版本方法
    } else { // 如果没有匹配的类型
      // 抛出异常：没有找到对应的处理器
      // 作用：当传入的节点类型没有被任何 if 分支处理时，抛出异常
      // 说明：这通常表示代码生成时遗漏了某种节点类型
      // 建议：异常信息建议创建一个通用的 RelNode 处理器
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.util.Set org.apache.calcite.rel.metadata.BuiltInMetadata$ColumnOrigin$Handler.getColumnOrigins(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery,int)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出 IllegalArgumentException，包含详细的错误信息和建议
    }
  }

} // 类结束
