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
package org.apache.calcite.rel.metadata.janino; // 声明包名，该类位于org.apache.calcite.rel.metadata.janino包下，这是用于存放通过Janino编译器生成的元数据处理器的包

// GeneratedMetadata_ExpressionLineageHandler类：这是一个自动生成的元数据处理器类，专门用于处理表达式血缘（ExpressionLineage）元数据查询
// 表达式血缘是指追踪数据表达式的来源和依赖关系，即一个表达式最终来源于哪些原始列或表达式
// 该类实现了BuiltInMetadata.ExpressionLineage.Handler接口，是Calcite元数据系统中的核心组件之一
// 该类的作用是在运行时根据不同的RelNode类型调用对应的getExpressionLineage方法，实现表达式的血缘追踪
// 该类是通过RelMetadataHandlerGenerator工具自动生成的，代码结构清晰，采用分发器模式来处理不同类型的RelNode
public final class GeneratedMetadata_ExpressionLineageHandler // 声明一个final类，表示该类不能被继承，用于处理表达式血缘元数据的生成和查询
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.ExpressionLineage.Handler { // 实现BuiltInMetadata.ExpressionLineage.Handler接口，该接口定义了表达式血缘元数据查询的标准方法

  // methodKey0：缓存键对象，用于在元数据查询缓存中唯一标识getExpressionLineage方法
  // 该键使用DescriptiveCacheKey类创建，包含方法签名信息"Set Handler.getExpressionLineage(RelNode, RelMetadataQuery, RexNode)"
  // 在元数据查询过程中，这个键用于存储和检索计算结果，避免重复计算，提高性能
  // 当查询相同的RelNode和RexNode组合时，可以通过这个键快速返回缓存的结果
  private final Object methodKey0 = // 声明一个final类型的Object成员变量，作为缓存键使用，final表示该变量在构造后不可修改
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Set Handler.getExpressionLineage(RelNode, RelMetadataQuery, RexNode)"); // 创建DescriptiveCacheKey对象，传入方法签名字符串，用于描述和标识该元数据查询方法

  // provider0：表达式血缘元数据提供者对象，该对象包含了各种RelNode类型的getExpressionLineage方法的实现
  // 这是一个RelMdExpressionLineage类型的实例，是Calcite中内置的表达式血缘元数据提供者
  // 该provider对象是实际执行表达式血缘计算的核心，包含了对不同RelNode类型（如Aggregate、Project、Filter等）的具体处理逻辑
  // 通过provider0，当前类可以委托给实际的实现类来完成元数据计算工作
  public final org.apache.calcite.rel.metadata.RelMdExpressionLineage provider0; // 声明一个public final类型的RelMdExpressionLineage对象，作为元数据提供者

  // 构造方法：GeneratedMetadata_ExpressionLineageHandler的构造函数
  // 该构造方法接收一个RelMdExpressionLineage类型的provider0参数，并将其赋值给成员变量
  // 这个构造方法的作用是初始化元数据处理器，将实际的元数据计算逻辑提供者注入到处理器中
  // 通过依赖注入的方式，使得该处理器可以灵活地使用不同的元数据提供者实现
  public GeneratedMetadata_ExpressionLineageHandler( // 声明构造方法，方法名与类名相同
      org.apache.calcite.rel.metadata.RelMdExpressionLineage provider0) { // 构造方法参数，接收一个RelMdExpressionLineage类型的对象
    this.provider0 = provider0; // 将传入的provider0参数赋值给当前对象的provider0成员变量，完成初始化
  }

  // getDef方法：获取元数据定义（MetadataDef）
  // 该方法返回当前处理器所处理的元数据的定义信息
  // MetadataDef包含了元数据的类型、名称等元数据信息，是元数据系统的核心描述对象
  // 该方法直接委托给provider0.getDef()，返回RelMdExpressionLineage类的元数据定义
  // 这个方法在元数据注册和查询过程中被使用，用于识别和匹配元数据处理器
  public org.apache.calcite.rel.metadata.MetadataDef getDef() { // 声明public方法，返回MetadataDef对象
    return provider0.getDef(); // 调用provider0的getDef方法，返回表达式血缘元数据的定义信息
  }

  // getExpressionLineage方法：获取表达式血缘关系的公共入口方法
  // 该方法是MetadataHandler接口的核心方法，用于计算指定RelNode中给定RexNode的表达式血缘
  // 参数说明：
  //   - r: 要查询的RelNode关系节点，表示某个关系代数操作（如Project、Filter、Join等）
  //   - mq: RelMetadataQuery对象，元数据查询上下文，用于管理元数据缓存和查询状态
  //   - a2: RexNode对象，要追踪血缘的表达式节点
  // 返回值：Set对象，包含该表达式的血缘信息，即该表达式依赖于哪些原始表达式或列
  // 该方法实现了元数据的缓存机制和循环依赖检测，避免无限递归和重复计算
  public java.util.Set getExpressionLineage( // 声明public方法，返回Set集合，表示表达式血缘信息
      org.apache.calcite.rel.RelNode r, // 参数1：RelNode关系节点，表示要查询的关系代数操作
      org.apache.calcite.rel.metadata.RelMetadataQuery mq, // 参数2：RelMetadataQuery对象，元数据查询上下文，管理缓存和状态
      org.apache.calcite.rex.RexNode a2) { // 参数3：RexNode表达式节点，要追踪血缘的表达式

    // 处理委托元数据关系节点：如果当前RelNode是DelegatingMetadataRel类型，则获取其委托的RelNode
    // DelegatingMetadataRel是一个包装器RelNode，它将元数据查询委托给另一个RelNode
    // 这个while循环会一直解包，直到找到非委托类型的RelNode
    // 这样可以确保元数据查询在真正的RelNode上进行，而不是在包装器上进行
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) { // 检查r是否是DelegatingMetadataRel的实例
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 如果是，则获取其委托的RelNode并赋值给r
    }

    // 创建缓存键：使用methodKey0和参数a2（RexNode）创建缓存键
    // FlatLists.of方法创建一个不可变的列表，包含methodKey0和a2
    // 这个键用于在元数据缓存中唯一标识这次查询（RelNode + 方法 + RexNode的组合）
    // 通过缓存键可以快速查找是否已经计算过相同的元数据查询
    final Object key; // 声明final类型的Object变量key，用于存储缓存键
    key = org.apache.calcite.runtime.FlatLists.of(methodKey0, a2); // 使用FlatLists创建包含methodKey0和a2的列表作为缓存键

    // 查询缓存：从RelMetadataQuery的缓存map中查找是否有已计算的结果
    // mq.map是一个二维缓存，以RelNode和key为键，存储元数据查询结果
    // 如果缓存中存在结果，则直接返回，避免重复计算，提高性能
    final Object v = mq.map.get(r, key); // 从缓存中获取RelNode r和key对应的值

    // 检查缓存结果：如果缓存中存在结果（v不为null）
    if (v != null) { // 判断缓存值v是否不为null
      // 检查是否是活跃状态：如果缓存值是ACTIVE标记，说明当前查询正在进行中（循环依赖）
      // ACTIVE标记用于检测元数据查询的循环依赖，防止无限递归
      // 如果检测到循环依赖，则抛出CyclicMetadataException异常
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // 检查v是否等于ACTIVE标记
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出循环元数据异常，表示检测到循环依赖
      }
      // 检查是否是空值标记：如果缓存值是INSTANCE标记，说明查询结果为null
      // NullSentinel.INSTANCE用于在缓存中表示null值，因为缓存不能直接存储null
      // 如果检测到INSTANCE标记，则返回null
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // 检查v是否等于INSTANCE标记
        return null; // 返回null，表示表达式血缘为空
      }
      // 返回缓存结果：将缓存值v转换为Set类型并返回
      // 此时v已经是计算好的表达式血缘结果，直接返回即可
      return (java.util.Set) v; // 将v强制转换为Set类型并返回
    }

    // 标记查询为活跃状态：在开始计算之前，将缓存值设置为ACTIVE
    // 这样可以检测到循环依赖，如果后续再次查询相同的RelNode和key，就会检测到ACTIVE标记
    // ACTIVE标记表示当前查询正在进行中，尚未完成
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 将RelNode r和key映射到ACTIVE标记

    // 计算元数据：调用内部方法getExpressionLineage_进行实际的元数据计算
    // 使用try-catch块来处理计算过程中可能出现的异常
    // 如果计算成功，则将结果存入缓存并返回
    // 如果计算失败，则清除缓存并重新抛出异常
    try { // 开始try块，用于捕获可能的异常
      final java.util.Set x = getExpressionLineage_(r, mq, a2); // 调用内部方法getExpressionLineage_计算表达式血缘，结果存储在x中
      // 将计算结果存入缓存：使用NullSentinel.mask方法处理结果
      // NullSentinel.mask方法会将null值转换为INSTANCE标记，非null值保持不变
      // 这样可以确保缓存中不会存储真正的null值，便于后续判断
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果x处理后存入缓存
      return x; // 返回计算结果x
    } catch (java.lang.Exception e) { // 捕获所有类型的异常
      // 清除缓存：如果计算过程中出现异常，则清除该RelNode的所有缓存
      // 这样可以避免缓存不完整或错误的结果
      // 清除缓存后，下次查询会重新计算
      mq.map.row(r).clear(); // 清除RelNode r的所有缓存条目
      throw e; // 重新抛出捕获的异常
    }
  }

  // getExpressionLineage_方法：内部方法，实际执行表达式血缘计算
  // 该方法根据RelNode的具体类型，调用provider0中对应的getExpressionLineage方法
  // 这是典型的分发器模式，根据不同的RelNode类型分发到不同的处理逻辑
  // 参数说明：
  //   - r: RelNode关系节点
  //   - mq: RelMetadataQuery对象，元数据查询上下文
  //   - a2: RexNode表达式节点
  // 返回值：Set对象，包含该表达式的血缘信息
  // 该方法处理了Calcite中所有支持表达式血缘查询的RelNode类型
  private java.util.Set getExpressionLineage_( // 声明private方法，返回Set集合，实际执行表达式血缘计算
      org.apache.calcite.rel.RelNode r, // 参数1：RelNode关系节点
      org.apache.calcite.rel.metadata.RelMetadataQuery mq, // 参数2：RelMetadataQuery对象，元数据查询上下文
      org.apache.calcite.rex.RexNode a2) { // 参数3：RexNode表达式节点

    // 处理RelSubset类型：如果RelNode是RelSubset类型，则调用provider0的RelSubset重载方法
    // RelSubset是VolcanoPlanner中使用的特殊RelNode，表示一组等价的RelNode集合
    // RelSubset的表达式血缘需要特殊处理，因为它可能包含多个等价的RelNode
    if (r instanceof org.apache.calcite.plan.volcano.RelSubset) { // 检查r是否是RelSubset的实例
      return provider0.getExpressionLineage((org.apache.calcite.plan.volcano.RelSubset) r, mq, a2); // 调用provider0的RelSubset重载方法，计算表达式血缘
    // 处理Aggregate类型：如果RelNode是Aggregate类型，则调用provider0的Aggregate重载方法
    // Aggregate是聚合操作节点，包含GROUP BY和聚合函数
    // Aggregate的表达式血缘需要追踪聚合函数的来源和分组列的来源
    } else if (r instanceof org.apache.calcite.rel.core.Aggregate) { // 检查r是否是Aggregate的实例
      return provider0.getExpressionLineage((org.apache.calcite.rel.core.Aggregate) r, mq, a2); // 调用provider0的Aggregate重载方法，计算表达式血缘
    // 处理Calc类型：如果RelNode是Calc类型，则调用provider0的Calc重载方法
    // Calc是Calcite中的计算节点，类似于Project和Filter的组合
    // Calc的表达式血缘需要追踪计算表达式的来源
    } else if (r instanceof org.apache.calcite.rel.core.Calc) { // 检查r是否是Calc的实例
      return provider0.getExpressionLineage((org.apache.calcite.rel.core.Calc) r, mq, a2); // 调用provider0的Calc重载方法，计算表达式血缘
    // 处理Exchange类型：如果RelNode是Exchange类型，则调用provider0的Exchange重载方法
    // Exchange是数据交换节点，用于分布式查询中的数据重分布
    // Exchange的表达式血缘通常直接传递，不改变表达式
    } else if (r instanceof org.apache.calcite.rel.core.Exchange) { // 检查r是否是Exchange的实例
      return provider0.getExpressionLineage((org.apache.calcite.rel.core.Exchange) r, mq, a2); // 调用provider0的Exchange重载方法，计算表达式血缘
    // 处理Filter类型：如果RelNode是Filter类型，则调用provider0的Filter重载方法
    // Filter是过滤节点，根据条件过滤数据行
    // Filter的表达式血缘需要追踪过滤条件中表达式的来源
    } else if (r instanceof org.apache.calcite.rel.core.Filter) { // 检查r是否是Filter的实例
      return provider0.getExpressionLineage((org.apache.calcite.rel.core.Filter) r, mq, a2); // 调用provider0的Filter重载方法，计算表达式血缘
    // 处理Join类型：如果RelNode是Join类型，则调用provider0的Join重载方法
    // Join是连接节点，将两个或多个表根据连接条件连接在一起
    // Join的表达式血缘需要追踪连接条件和输出列的来源，可能来自多个输入表
    } else if (r instanceof org.apache.calcite.rel.core.Join) { // 检查r是否是Join的实例
      return provider0.getExpressionLineage((org.apache.calcite.rel.core.Join) r, mq, a2); // 调用provider0的Join重载方法，计算表达式血缘
    // 处理Project类型：如果RelNode是Project类型，则调用provider0的Project重载方法
    // Project是投影节点，用于计算新的表达式列
    // Project的表达式血缘需要追踪每个投影表达式的来源，这是最常见的情况
    } else if (r instanceof org.apache.calcite.rel.core.Project) { // 检查r是否是Project的实例
      return provider0.getExpressionLineage((org.apache.calcite.rel.core.Project) r, mq, a2); // 调用provider0的Project重载方法，计算表达式血缘
    // 处理Sample类型：如果RelNode是Sample类型，则调用provider0的Sample重载方法
    // Sample是采样节点，用于从数据中随机抽取样本
    // Sample的表达式血缘通常直接传递，不改变表达式
    } else if (r instanceof org.apache.calcite.rel.core.Sample) { // 检查r是否是Sample的实例
      return provider0.getExpressionLineage((org.apache.calcite.rel.core.Sample) r, mq, a2); // 调用provider0的Sample重载方法，计算表达式血缘
    // 处理Snapshot类型：如果RelNode是Snapshot类型，则调用provider0的Snapshot重载方法
    // Snapshot是快照节点，用于获取表的某个时间点的快照数据
    // Snapshot的表达式血缘需要追踪快照列的来源
    } else if (r instanceof org.apache.calcite.rel.core.Snapshot) { // 检查r是否是Snapshot的实例
      return provider0.getExpressionLineage((org.apache.calcite.rel.core.Snapshot) r, mq, a2); // 调用provider0的Snapshot重载方法，计算表达式血缘
    // 处理Sort类型：如果RelNode是Sort类型，则调用provider0的Sort重载方法
    // Sort是排序节点，根据指定的排序键对数据进行排序
    // Sort的表达式血缘通常直接传递，不改变表达式
    } else if (r instanceof org.apache.calcite.rel.core.Sort) { // 检查r是否是Sort的实例
      return provider0.getExpressionLineage((org.apache.calcite.rel.core.Sort) r, mq, a2); // 调用provider0的Sort重载方法，计算表达式血缘
    // 处理TableModify类型：如果RelNode是TableModify类型，则调用provider0的TableModify重载方法
    // TableModify是表修改节点，用于INSERT、UPDATE、DELETE操作
    // TableModify的表达式血缘需要追踪修改操作的来源表达式
    } else if (r instanceof org.apache.calcite.rel.core.TableModify) { // 检查r是否是TableModify的实例
      return provider0.getExpressionLineage((org.apache.calcite.rel.core.TableModify) r, mq, a2); // 调用provider0的TableModify重载方法，计算表达式血缘
    // 处理TableScan类型：如果RelNode是TableScan类型，则调用provider0的TableScan重载方法
    // TableScan是表扫描节点，表示从表中读取数据
    // TableScan的表达式血缘通常直接是表的列，不需要进一步追踪
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) { // 检查r是否是TableScan的实例
      return provider0.getExpressionLineage((org.apache.calcite.rel.core.TableScan) r, mq, a2); // 调用provider0的TableScan重载方法，计算表达式血缘
    // 处理Union类型：如果RelNode是Union类型，则调用provider0的Union重载方法
    // Union是联合节点，将多个输入的结果合并在一起
    // Union的表达式血缘需要追踪来自不同输入的表达式来源
    } else if (r instanceof org.apache.calcite.rel.core.Union) { // 检查r是否是Union的实例
      return provider0.getExpressionLineage((org.apache.calcite.rel.core.Union) r, mq, a2); // 调用provider0的Union重载方法，计算表达式血缘
    // 处理通用RelNode类型：如果RelNode是普通的RelNode类型（不是上述任何特定类型），则调用provider0的通用RelNode重载方法
    // 这是一个catch-all处理，用于处理所有其他类型的RelNode
    // 通用方法通常返回默认的表达式血缘或抛出异常
    } else if (r instanceof org.apache.calcite.rel.RelNode) { // 检查r是否是RelNode的实例
      return provider0.getExpressionLineage((org.apache.calcite.rel.RelNode) r, mq, a2); // 调用provider0的RelNode重载方法，计算表达式血缘
    // 处理未知类型：如果RelNode不是任何已知的类型，则抛出IllegalArgumentException异常
    // 这种情况通常表示元数据处理器没有为该RelNode类型实现处理逻辑
    // 异常信息中包含方法签名和RelNode的实际类型，帮助开发者定位问题
    // 建议创建一个catch-all（RelNode）处理方法来处理这种情况
    } else { // 如果r不属于任何已知的RelNode类型
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.util.Set org.apache.calcite.rel.metadata.BuiltInMetadata$ExpressionLineage$Handler.getExpressionLineage(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery,org.apache.calcite.rex.RexNode)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出IllegalArgumentException异常，提示没有找到对应的处理方法
    }
  }

} // 类结束