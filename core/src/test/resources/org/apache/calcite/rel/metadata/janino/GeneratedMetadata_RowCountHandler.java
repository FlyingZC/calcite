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
package org.apache.calcite.rel.metadata.janino; // 包声明，该类属于org.apache.calcite.rel.metadata.janino包，用于存放Janino编译器生成的元数据处理类

// GeneratedMetadata_RowCountHandler类是一个自动生成的元数据处理器类，用于处理关系代数节点的行数统计元数据
// 该类实现了BuiltInMetadata.RowCount.Handler接口，是Calcite元数据系统的核心组件之一
// 主要功能：为不同类型的RelNode（关系节点）提供行数统计(row count)的计算能力
// 工作原理：通过类型分发机制，根据RelNode的具体类型调用对应的provider方法来计算行数
// 性能优化：使用缓存机制避免重复计算相同的元数据，通过methodKey0作为缓存键
// 异常处理：能够检测循环依赖并抛出CyclicMetadataException，防止元数据计算陷入死循环
public final class GeneratedMetadata_RowCountHandler // 定义一个final类，表示该类不能被继承，确保元数据处理的稳定性
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.RowCount.Handler { // 实现BuiltInMetadata.RowCount.Handler接口，提供行数统计的处理能力
  // methodKey0是缓存键对象，用于在元数据缓存中标识getRowCount()方法的计算结果
  // 使用DescriptiveCacheKey而不是普通的Object，便于调试时识别缓存内容
  // 该键在getRowCount方法中用于从mq.map中获取或存储计算结果
  private final Object methodKey0 = // 声明一个final类型的Object成员变量，作为方法缓存键，初始化后不可更改
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Double Handler.getRowCount()"); // 创建描述性缓存键，包含方法的签名信息，便于调试和问题追踪
  // provider0是实际的元数据提供者，包含各种RelNode类型的行数计算逻辑
  // 该对象通过构造方法注入，实现了具体的业务逻辑（如Aggregate、Filter、Join等节点的行数计算）
  // GeneratedMetadata_RowCountHandler主要作为分发器，将请求委托给provider0的对应方法
  public final org.apache.calcite.rel.metadata.RelMdRowCount provider0; // 声明一个public final类型的RelMdRowCount成员变量，作为元数据提供者，存储实际的计算逻辑
  // 构造方法：创建GeneratedMetadata_RowCountHandler实例
  // 参数provider0：RelMdRowCount类型的元数据提供者，包含所有RelNode类型的行数计算实现
  // 该构造方法将provider0赋值给成员变量，使后续方法可以调用provider0的计算逻辑
  public GeneratedMetadata_RowCountHandler( // 构造方法声明，接受RelMdRowCount类型的参数
      org.apache.calcite.rel.metadata.RelMdRowCount provider0) { // 构造方法参数，元数据提供者对象
    this.provider0 = provider0; // 将传入的provider0参数赋值给成员变量provider0，保存引用以便后续使用
  }
  // getDef方法：获取元数据定义
  // 返回值：MetadataDef对象，描述该元数据的类型、名称等信息
  // 该方法直接委托给provider0.getDef()，因为元数据定义由provider0持有
  public org.apache.calcite.rel.metadata.MetadataDef getDef() { // 方法声明，返回MetadataDef类型，获取元数据定义
    return provider0.getDef(); // 调用provider0的getDef方法并返回结果，实现委托模式
  }
  // getRowCount方法：获取关系节点的行数统计（带缓存和循环检测）
  // 参数r：RelNode类型，表示要计算行数的关系节点
  // 参数mq：RelMetadataQuery类型，元数据查询对象，包含缓存和上下文信息
  // 返回值：Double类型，表示该关系节点的行数估计值，可能为null表示无法估计
  // 主要功能：
  //   1. 处理DelegatingMetadataRel类型的委托节点，获取实际的委托目标
  //   2. 从缓存中查找已计算的结果，避免重复计算
  //   3. 检测循环依赖，如果发现循环则抛出CyclicMetadataException
  //   4. 如果缓存未命中，调用getRowCount_进行实际计算并缓存结果
  //   5. 异常处理：如果计算过程中出现异常，清除该节点的所有缓存
  public java.lang.Double getRowCount( // 方法声明，返回Double类型，获取关系节点的行数
      org.apache.calcite.rel.RelNode r, // 参数r：关系节点对象，表示要计算行数的目标节点
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq：元数据查询对象，提供缓存和查询上下文
    // 循环处理DelegatingMetadataRel类型的节点，这类节点会委托给其他节点进行元数据计算
    // 通过不断解包，最终获取到实际需要计算元数据的目标节点
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) { // while循环：只要r是DelegatingMetadataRel类型就继续处理
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 获取委托的目标节点，赋值给r继续循环
    }
    // 声明缓存键变量，用于从缓存中查找或存储计算结果
    final Object key; // 声明final类型的Object变量key，作为缓存键
    key = methodKey0; // 将methodKey0赋值给key，使用预先定义好的方法键作为缓存键
    // 从元数据缓存中查找是否已经计算过该节点的行数
    // mq.map是一个二维映射，key1是RelNode，key2是methodKey，value是计算结果
    final Object v = mq.map.get(r, key); // 从缓存中获取r节点对应methodKey的值，赋值给v
    // 如果缓存中存在结果（v不为null），则进行结果解析
    if (v != null) { // if判断：检查缓存值v是否不为null
      // 检查是否是ACTIVE标记，表示该元数据正在计算中（循环依赖检测）
      // 如果发现ACTIVE标记，说明存在循环依赖，抛出异常
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // if判断：检查v是否等于ACTIVE标记
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出循环元数据异常，防止无限递归
      }
      // 检查是否是INSTANCE标记，表示该元数据值为null（因为缓存不能存储null值）
      // 如果是INSTANCE标记，直接返回null
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // if判断：检查v是否等于INSTANCE标记
        return null; // 返回null，表示该元数据的值为null
      }
      // 缓存命中，返回已计算的结果（转换为Double类型）
      return (java.lang.Double) v; // 将缓存值v强制转换为Double类型并返回
    }
    // 缓存未命中，先在缓存中放置ACTIVE标记，表示该元数据正在计算中
    // 这样可以检测循环依赖：如果后续计算过程中再次请求该元数据，会抛出异常
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 在缓存中放入ACTIVE标记，标记为计算中
    try { // try块：执行实际的元数据计算
      // 调用内部方法getRowCount_进行实际的行数计算
      // 该方法会根据r的具体类型调用provider0中对应的计算方法
      final java.lang.Double x = getRowCount_(r, mq); // 调用getRowCount_方法计算行数，结果赋值给x
      // 将计算结果存入缓存，使用NullSentinel.mask处理null值
      // 如果x为null，mask会返回INSTANCE标记；否则返回x本身
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果x存入缓存，处理null值
      return x; // 返回计算结果x
    } catch (java.lang.Exception e) { // catch块：捕获计算过程中可能出现的异常
      // 如果计算过程中出现异常，清除该节点的所有缓存
      // 这样可以避免缓存不一致的问题，下次查询会重新计算
      mq.map.row(r).clear(); // 清除r节点在缓存中的所有条目
      throw e; // 重新抛出捕获的异常
    }
  }

  // getRowCount_方法：内部方法，根据RelNode的具体类型分发到对应的计算逻辑
  // 参数r：RelNode类型，表示要计算行数的关系节点
  // 参数mq：RelMetadataQuery类型，元数据查询对象
  // 返回值：Double类型，表示该关系节点的行数估计值
  // 主要功能：通过if-else链进行类型检查，根据r的具体类型调用provider0中对应的getRowCount重载方法
  // 设计模式：这是典型的类型分发模式（Type Dispatch），类似于访问者模式的简化版本
  // 执行顺序：从具体类型到通用类型，确保最匹配的类型被优先处理
  private java.lang.Double getRowCount_( // 方法声明，返回Double类型，内部方法用于类型分发
      org.apache.calcite.rel.RelNode r, // 参数r：关系节点对象
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq：元数据查询对象
    // 检查r是否是EnumerableLimit类型（可枚举的限制操作节点）
    // EnumerableLimit用于限制结果集的行数，通常对应SQL中的LIMIT子句
    if (r instanceof org.apache.calcite.adapter.enumerable.EnumerableLimit) { // if判断：检查r是否为EnumerableLimit类型
      return provider0.getRowCount((org.apache.calcite.adapter.enumerable.EnumerableLimit) r, mq); // 调用provider0的EnumerableLimit重载方法计算行数
    } else if (r instanceof org.apache.calcite.plan.volcano.RelSubset) { // else if：检查r是否为RelSubset类型（Volcano优化器的子集节点）
      // RelSubset表示Volcano优化器中的等价关系节点集合，包含多个等价的物理实现
      return provider0.getRowCount((org.apache.calcite.plan.volcano.RelSubset) r, mq); // 调用provider0的RelSubset重载方法计算行数
    } else if (r instanceof org.apache.calcite.rel.core.Aggregate) { // else if：检查r是否为Aggregate类型（聚合操作节点）
      // Aggregate用于GROUP BY聚合操作，会减少行数
      return provider0.getRowCount((org.apache.calcite.rel.core.Aggregate) r, mq); // 调用provider0的Aggregate重载方法计算行数
    } else if (r instanceof org.apache.calcite.rel.core.Calc) { // else if：检查r是否为Calc类型（计算操作节点）
      // Calc用于表达式计算和投影，可能改变行数（通过过滤）
      return provider0.getRowCount((org.apache.calcite.rel.core.Calc) r, mq); // 调用provider0的Calc重载方法计算行数
    } else if (r instanceof org.apache.calcite.rel.core.Exchange) { // else if：检查r是否为Exchange类型（数据交换节点）
      // Exchange用于分布式系统中的数据重分布，行数通常不变
      return provider0.getRowCount((org.apache.calcite.rel.core.Exchange) r, mq); // 调用provider0的Exchange重载方法计算行数
    } else if (r instanceof org.apache.calcite.rel.core.Filter) { // else if：检查r是否为Filter类型（过滤操作节点）
      // Filter用于WHERE条件过滤，通常会减少行数
      return provider0.getRowCount((org.apache.calcite.rel.core.Filter) r, mq); // 调用provider0的Filter重载方法计算行数
    } else if (r instanceof org.apache.calcite.rel.core.Intersect) { // else if：检查r是否为Intersect类型（交集操作节点）
      // Intersect用于INTERSECT集合操作，行数通常小于等于最小输入
      return provider0.getRowCount((org.apache.calcite.rel.core.Intersect) r, mq); // 调用provider0的Intersect重载方法计算行数
    } else if (r instanceof org.apache.calcite.rel.core.Join) { // else if：检查r是否为Join类型（连接操作节点）
      // Join用于表连接，行数通常是左右表行数的乘积（笛卡尔积）或更少（有连接条件）
      return provider0.getRowCount((org.apache.calcite.rel.core.Join) r, mq); // 调用provider0的Join重载方法计算行数
    } else if (r instanceof org.apache.calcite.rel.core.Minus) { // else if：检查r是否为Minus类型（差集操作节点）
      // Minus用于EXCEPT集合操作，行数通常小于等于左表行数
      return provider0.getRowCount((org.apache.calcite.rel.core.Minus) r, mq); // 调用provider0的Minus重载方法计算行数
    } else if (r instanceof org.apache.calcite.rel.core.Project) { // else if：检查r是否为Project类型（投影操作节点）
      // Project用于SELECT列投影，行数通常不变（除非有常量表达式导致行数变化）
      return provider0.getRowCount((org.apache.calcite.rel.core.Project) r, mq); // 调用provider0的Project重载方法计算行数
    } else if (r instanceof org.apache.calcite.rel.core.Sample) { // else if：检查r是否为Sample类型（采样操作节点）
      // Sample用于TABLESAMPLE采样，行数是按比例采样后的结果
      return provider0.getRowCount((org.apache.calcite.rel.core.Sample) r, mq); // 调用provider0的Sample重载方法计算行数
    } else if (r instanceof org.apache.calcite.rel.core.Sort) { // else if：检查r是否为Sort类型（排序操作节点）
      // Sort用于ORDER BY排序，行数通常不变
      return provider0.getRowCount((org.apache.calcite.rel.core.Sort) r, mq); // 调用provider0的Sort重载方法计算行数
    } else if (r instanceof org.apache.calcite.rel.core.TableModify) { // else if：检查r是否为TableModify类型（表修改操作节点）
      // TableModify用于INSERT、UPDATE、DELETE操作，行数取决于操作类型
      return provider0.getRowCount((org.apache.calcite.rel.core.TableModify) r, mq); // 调用provider0的TableModify重载方法计算行数
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) { // else if：检查r是否为TableScan类型（表扫描节点）
      // TableScan用于扫描基表，行数通常是表的统计信息
      return provider0.getRowCount((org.apache.calcite.rel.core.TableScan) r, mq); // 调用provider0的TableScan重载方法计算行数
    } else if (r instanceof org.apache.calcite.rel.core.Union) { // else if：检查r是否为Union类型（并集操作节点）
      // Union用于UNION集合操作，行数通常是所有输入行数之和（UNION ALL）或去重后（UNION）
      return provider0.getRowCount((org.apache.calcite.rel.core.Union) r, mq); // 调用provider0的Union重载方法计算行数
    } else if (r instanceof org.apache.calcite.rel.core.Values) { // else if：检查r是否为Values类型（常量值节点）
      // Values用于VALUES子句，行数是常量值的行数
      return provider0.getRowCount((org.apache.calcite.rel.core.Values) r, mq); // 调用provider0的Values重载方法计算行数
    } else if (r instanceof org.apache.calcite.rel.SingleRel) { // else if：检查r是否为SingleRel类型（单输入关系节点）
      // SingleRel是只有一个输入的关系节点基类，作为通用处理
      return provider0.getRowCount((org.apache.calcite.rel.SingleRel) r, mq); // 调用provider0的SingleRel重载方法计算行数
    } else if (r instanceof org.apache.calcite.rel.RelNode) { // else if：检查r是否为RelNode类型（通用关系节点）
      // RelNode是所有关系节点的基类，这是最后的兜底处理
      return provider0.getRowCount((org.apache.calcite.rel.RelNode) r, mq); // 调用provider0的RelNode重载方法计算行数
    } else { // else：以上所有类型都不匹配
            // 抛出IllegalArgumentException异常，表示没有找到对应的处理器
            // 异常信息包含方法签名和实际类型，帮助开发者定位问题
            // 建议创建一个catch-all（RelNode）处理器来处理未知类型
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.lang.Double org.apache.calcite.rel.metadata.BuiltInMetadata$RowCount$Handler.getRowCount(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出异常，提供详细的错误信息
    }
  }

} // 类结束，GeneratedMetadata_RowCountHandler类定义结束
