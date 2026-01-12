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
package org.apache.calcite.rel.metadata.janino; // 包声明:属于org.apache.calcite.rel.metadata.janino包,这是Calcite框架中用于Janino代码生成器的元数据处理包

// 类声明:GeneratedMetadata_SizeHandler是一个由Janino代码生成器自动生成的元数据处理器类
// 该类实现了BuiltInMetadata.Size.Handler接口,专门用于处理与Size相关的元数据查询
// Size元数据包括:平均列大小(averageColumnSizes)和平均行大小(averageRowSize)
// 该类的作用是作为元数据查询的入口点,提供缓存机制和类型分发功能,将元数据请求委托给底层的RelMdSize提供者
public final class GeneratedMetadata_SizeHandler // 修饰符public表示该类可以被任何地方访问,final表示该类不能被继承
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.Size.Handler { // 实现BuiltInMetadata.Size.Handler接口,该接口定义了Size元数据的处理方法

  // 成员变量:methodKey0是用于缓存averageColumnSizes方法结果的缓存键
  // 该键使用DescriptiveCacheKey包装,描述为"List Handler.averageColumnSizes()"
  // 在元数据查询缓存中,这个键用于标识和检索特定RelNode的averageColumnSizes元数据结果
  // 使用Object类型是因为缓存系统需要统一的键类型,实际类型是DescriptiveCacheKey
  private final Object methodKey0 = // private表示该成员变量只能在类内部访问,final表示该引用一旦初始化就不能改变
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("List Handler.averageColumnSizes()"); // 创建一个DescriptiveCacheKey实例,参数是描述性字符串,用于标识averageColumnSizes方法

  // 成员变量:methodKey1是用于缓存averageRowSize方法结果的缓存键
  // 该键使用DescriptiveCacheKey包装,描述为"Double Handler.averageRowSize()"
  // 在元数据查询缓存中,这个键用于标识和检索特定RelNode的averageRowSize元数据结果
  // 使用Object类型是因为缓存系统需要统一的键类型,实际类型是DescriptiveCacheKey
  private final Object methodKey1 = // private表示该成员变量只能在类内部访问,final表示该引用一旦初始化就不能改变
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Double Handler.averageRowSize()"); // 创建一个DescriptiveCacheKey实例,参数是描述性字符串,用于标识averageRowSize方法

  // 成员变量:provider1是实际提供Size元数据计算逻辑的提供者对象
  // 该对象是RelMdSize类型,包含了针对不同RelNode类型的averageColumnSizes和averageRowSize方法的具体实现
  // GeneratedMetadata_SizeHandler作为代理,将实际的元数据计算委托给provider1
  // public修饰符表示该变量可以被外部访问,final表示该引用一旦初始化就不能改变
  public final org.apache.calcite.rel.metadata.RelMdSize provider1; // provider1是RelMdSize类型,是Size元数据的实际计算提供者

  // 构造方法:用于初始化GeneratedMetadata_SizeHandler实例
  // 该构造方法接收一个RelMdSize类型的provider1参数,并将其赋值给成员变量
  // 参数provider1是实际执行元数据计算的对象,GeneratedMetadata_SizeHandler负责缓存和分发
  public GeneratedMetadata_SizeHandler( // 构造方法声明,访问修饰符为public,可以被任何地方调用
      org.apache.calcite.rel.metadata.RelMdSize provider1) { // 参数provider1:RelMdSize类型的元数据提供者,包含具体的元数据计算逻辑
    this.provider1 = provider1; // 将传入的provider1参数赋值给成员变量provider1,保存对元数据提供者的引用
  }

  // 方法:getDef() - 获取元数据定义
  // 该方法返回Size元数据的定义信息,包括元数据的名称、类型等信息
  // 实际实现委托给provider1.getDef(),因为元数据定义由RelMdSize提供者维护
  // 返回值:MetadataDef对象,描述了Size元数据的定义
  public org.apache.calcite.rel.metadata.MetadataDef getDef() { // public方法,返回MetadataDef类型
    return provider1.getDef(); // 直接返回provider1的getDef()方法结果,即获取Size元数据的定义信息
  }

  // 方法:averageColumnSizes() - 获取关系节点的平均列大小列表
  // 该方法是Size元数据的核心方法之一,用于计算和缓存RelNode的平均列大小
  // 参数r:要查询元数据的RelNode关系节点
  // 参数mq:RelMetadataQuery元数据查询上下文,包含缓存和其他查询信息
  // 返回值:List<Integer>列表,表示每个列的平均大小(字节数),如果无法计算则返回null
  // 该方法实现了缓存机制,避免重复计算,并检测循环依赖
  public java.util.List averageColumnSizes( // public方法,返回List类型,表示列的平均大小列表
      org.apache.calcite.rel.RelNode r, // 参数r:RelNode类型,表示要查询元数据的关系节点
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq:RelMetadataQuery类型,表示元数据查询上下文,包含缓存map等

    // 循环处理:跳过DelegatingMetadataRel类型的包装节点
    // DelegatingMetadataRel是一种特殊的RelNode,它将元数据请求委托给内部的另一个RelNode
    // 通过循环解包,找到真正实现元数据计算的RelNode
    // 这样可以避免在包装层进行不必要的缓存和计算
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) { // while循环,条件是r是DelegatingMetadataRel的实例
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 将r设置为委托的目标RelNode,继续解包
    }

    // 变量声明:key是用于缓存的键对象
    // 该键将在后续用于从缓存中读取或写入元数据结果
    final Object key; // 声明一个final类型的Object变量key,用于存储缓存键
    key = methodKey0; // 将methodKey0赋值给key,methodKey0是averageColumnSizes方法的专用缓存键

    // 缓存查询:从元数据缓存map中获取当前RelNode和方法键对应的缓存值
    // mq.map是一个二维缓存,以RelNode和方法键为索引存储元数据结果
    // 如果缓存命中,可以直接返回缓存值,避免重复计算
    final Object v = mq.map.get(r, key); // 调用缓存map的get方法,传入RelNode和缓存键,获取缓存值v

    // 条件判断:检查缓存值是否不为null
    // 如果缓存命中(v != null),则需要进行后续处理
    if (v != null) { // if条件判断,如果缓存值v不为null,表示缓存命中

      // 循环依赖检测:检查缓存值是否为ACTIVE标记
      // ACTIVE标记表示当前元数据正在计算中,如果再次遇到ACTIVE,说明存在循环依赖
      // 循环依赖会导致无限递归,必须抛出异常中断查询
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // 检查v是否等于ACTIVE标记
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出CyclicMetadataException异常,表示检测到循环依赖
      }

      // null值处理:检查缓存值是否为INSTANCE标记
      // INSTANCE标记表示缓存的值是null(因为缓存map不能存储null值)
      // 如果遇到INSTANCE标记,直接返回null,表示该元数据无法计算
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // 检查v是否等于INSTANCE标记
        return null; // 返回null,表示该元数据值为null
      }

      // 缓存命中返回:将缓存值强制转换为List类型并返回
      // 此时v是有效的缓存结果,可以直接返回
      return (java.util.List) v; // 将Object类型的v强制转换为List类型并返回
    }

    // 缓存标记:在开始计算前,将缓存值设置为ACTIVE标记
    // ACTIVE标记表示该元数据正在计算中,用于检测循环依赖
    // 如果在计算过程中再次查询相同元数据,会检测到ACTIVE并抛出异常
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 将缓存值设置为ACTIVE,标记计算开始

    // 异常处理:使用try-catch块捕获计算过程中的异常
    // 如果计算过程中发生异常,需要清除缓存,避免缓存被污染
    try { // try块开始,执行元数据计算逻辑

      // 实际计算:调用averageColumnSizes_方法进行实际的元数据计算
      // averageColumnSizes_是私有方法,根据RelNode的具体类型分发到provider1的相应方法
      // 计算结果存储在变量x中
      final java.util.List x = averageColumnSizes_(r, mq); // 调用私有方法averageColumnSizes_进行实际计算,结果存储在x中

      // 缓存更新:将计算结果存入缓存
      // NullSentinel.mask(x)方法用于处理null值,如果x为null,则返回INSTANCE标记,否则直接返回x
      // 这样可以确保缓存map中不存储null值,同时保留null语义
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果x存入缓存,使用mask方法处理null值
      return x; // 返回计算结果x
    } catch (java.lang.Exception e) { // 捕获所有Exception类型的异常

      // 缓存清理:如果发生异常,清除当前RelNode的所有缓存
      // 这样可以避免部分计算或异常状态污染缓存
      // 清除后,后续查询会重新计算,而不是使用错误的缓存值
      mq.map.row(r).clear(); // 清除缓存map中当前RelNode的所有缓存项
      throw e; // 重新抛出捕获的异常,让上层调用者处理
    }
  }

  // 方法:averageColumnSizes_() - 平均列大小的实际计算和分发方法(私有)
  // 该方法是一个分发器,根据RelNode的具体类型,将计算请求委托给provider1的相应重载方法
  // 这是Calcite元数据系统的核心机制,通过类型分发实现多态性
  // 参数r:要计算元数据的RelNode关系节点
  // 参数mq:RelMetadataQuery元数据查询上下文
  // 返回值:List<Integer>列表,表示每个列的平均大小
  private java.util.List averageColumnSizes_( // private方法,只能在类内部调用,返回List类型
      org.apache.calcite.rel.RelNode r, // 参数r:RelNode类型,表示要计算元数据的关系节点
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq:RelMetadataQuery类型,表示元数据查询上下文

    // 类型检查和分发:检查r是否为Aggregate类型
    // Aggregate是聚合操作的关系节点,包含GROUP BY和聚合函数
    // 如果是Aggregate,调用provider1针对Aggregate特化的averageColumnSizes方法
    if (r instanceof org.apache.calcite.rel.core.Aggregate) { // if条件判断,检查r是否为Aggregate的实例
      return provider1.averageColumnSizes((org.apache.calcite.rel.core.Aggregate) r, mq); // 调用provider1的Aggregate重载方法,传入强制转换后的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.Calc) { // 否则检查r是否为Calc类型

      // 类型检查和分发:检查r是否为Calc类型
      // Calc是计算操作的关系节点,类似于Project但更通用,可以包含表达式计算
      // 如果是Calc,调用provider1针对Calc特化的averageColumnSizes方法
      return provider1.averageColumnSizes((org.apache.calcite.rel.core.Calc) r, mq); // 调用provider1的Calc重载方法,传入强制转换后的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.Exchange) { // 否则检查r是否为Exchange类型

      // 类型检查和分发:检查r是否为Exchange类型
      // Exchange是数据交换操作的关系节点,用于分布式查询中的数据重分布
      // 如果是Exchange,调用provider1针对Exchange特化的averageColumnSizes方法
      return provider1.averageColumnSizes((org.apache.calcite.rel.core.Exchange) r, mq); // 调用provider1的Exchange重载方法,传入强制转换后的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.Filter) { // 否则检查r是否为Filter类型

      // 类型检查和分发:检查r是否为Filter类型
      // Filter是过滤操作的关系节点,根据条件过滤行
      // 如果是Filter,调用provider1针对Filter特化的averageColumnSizes方法
      return provider1.averageColumnSizes((org.apache.calcite.rel.core.Filter) r, mq); // 调用provider1的Filter重载方法,传入强制转换后的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.Intersect) { // 否则检查r是否为Intersect类型

      // 类型检查和分发:检查r是否为Intersect类型
      // Intersect是交集操作的关系节点,计算多个输入的交集
      // 如果是Intersect,调用provider1针对Intersect特化的averageColumnSizes方法
      return provider1.averageColumnSizes((org.apache.calcite.rel.core.Intersect) r, mq); // 调用provider1的Intersect重载方法,传入强制转换后的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.Join) { // 否则检查r是否为Join类型

      // 类型检查和分发:检查r是否为Join类型
      // Join是连接操作的关系节点,根据条件连接两个输入
      // 如果是Join,调用provider1针对Join特化的averageColumnSizes方法
      return provider1.averageColumnSizes((org.apache.calcite.rel.core.Join) r, mq); // 调用provider1的Join重载方法,传入强制转换后的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.Minus) { // 否则检查r是否为Minus类型

      // 类型检查和分发:检查r是否为Minus类型
      // Minus是差集操作的关系节点(即EXCEPT),计算第一个输入减去第二个输入
      // 如果是Minus,调用provider1针对Minus特化的averageColumnSizes方法
      return provider1.averageColumnSizes((org.apache.calcite.rel.core.Minus) r, mq); // 调用provider1的Minus重载方法,传入强制转换后的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.Project) { // 否则检查r是否为Project类型

      // 类型检查和分发:检查r是否为Project类型
      // Project是投影操作的关系节点,计算表达式并输出指定的列
      // 如果是Project,调用provider1针对Project特化的averageColumnSizes方法
      return provider1.averageColumnSizes((org.apache.calcite.rel.core.Project) r, mq); // 调用provider1的Project重载方法,传入强制转换后的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.Sort) { // 否则检查r是否为Sort类型

      // 类型检查和分发:检查r是否为Sort类型
      // Sort是排序操作的关系节点,根据指定的排序键对输入进行排序
      // 如果是Sort,调用provider1针对Sort特化的averageColumnSizes方法
      return provider1.averageColumnSizes((org.apache.calcite.rel.core.Sort) r, mq); // 调用provider1的Sort重载方法,传入强制转换后的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.TableModify) { // 否则检查r是否为TableModify类型

      // 类型检查和分发:检查r是否为TableModify类型
      // TableModify是表修改操作的关系节点,包括INSERT、UPDATE、DELETE等操作
      // 如果是TableModify,调用provider1针对TableModify特化的averageColumnSizes方法
      return provider1.averageColumnSizes((org.apache.calcite.rel.core.TableModify) r, mq); // 调用provider1的TableModify重载方法,传入强制转换后的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) { // 否则检查r是否为TableScan类型

      // 类型检查和分发:检查r是否为TableScan类型
      // TableScan是表扫描操作的关系节点,从数据源读取数据
      // 如果是TableScan,调用provider1针对TableScan特化的averageColumnSizes方法
      return provider1.averageColumnSizes((org.apache.calcite.rel.core.TableScan) r, mq); // 调用provider1的TableScan重载方法,传入强制转换后的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.Union) { // 否则检查r是否为Union类型

      // 类型检查和分发:检查r是否为Union类型
      // Union是并集操作的关系节点,合并多个输入的结果
      // 如果是Union,调用provider1针对Union特化的averageColumnSizes方法
      return provider1.averageColumnSizes((org.apache.calcite.rel.core.Union) r, mq); // 调用provider1的Union重载方法,传入强制转换后的r和mq
    } else if (r instanceof org.apache.calcite.rel.core.Values) { // 否则检查r是否为Values类型

      // 类型检查和分发:检查r是否为Values类型
      // Values是常量值操作的关系节点,直接输出指定的常量行
      // 如果是Values,调用provider1针对Values特化的averageColumnSizes方法
      return provider1.averageColumnSizes((org.apache.calcite.rel.core.Values) r, mq); // 调用provider1的Values重载方法,传入强制转换后的r和mq
    } else if (r instanceof org.apache.calcite.rel.RelNode) { // 否则检查r是否为RelNode类型

      // 类型检查和分发:检查r是否为RelNode类型
      // RelNode是所有关系节点的基类,这个分支是兜底处理
      // 如果是RelNode,调用provider1针对RelNode基类的averageColumnSizes方法
      // 这个方法应该提供通用的默认实现
      return provider1.averageColumnSizes((org.apache.calcite.rel.RelNode) r, mq); // 调用provider1的RelNode重载方法,传入强制转换后的r和mq
    } else { // 如果以上所有类型都不匹配

      // 异常抛出:抛出IllegalArgumentException,表示没有找到对应的处理器
      // 这通常意味着传入了一个未知的RelNode类型
      // 异常消息中包含了方法签名和实际类型信息,方便调试
      // 建议用户创建一个catch-all(RelNode)处理器来处理这种情况
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.util.List org.apache.calcite.rel.metadata.BuiltInMetadata$Size$Handler.averageColumnSizes(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出异常,包含详细的方法签名和类型信息
    }
  }

  // 方法:averageRowSize() - 获取关系节点的平均行大小
  // 该方法是Size元数据的另一个核心方法,用于计算和缓存RelNode的平均行大小
  // 参数r:要查询元数据的RelNode关系节点
  // 参数mq:RelMetadataQuery元数据查询上下文,包含缓存和其他查询信息
  // 返回值:Double类型,表示平均每行的大小(字节数),如果无法计算则返回null
  // 该方法的缓存机制和averageColumnSizes完全相同,都使用ACTIVE标记检测循环依赖
  public java.lang.Double averageRowSize( // public方法,返回Double类型,表示平均行大小
      org.apache.calcite.rel.RelNode r, // 参数r:RelNode类型,表示要查询元数据的关系节点
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq:RelMetadataQuery类型,表示元数据查询上下文,包含缓存map等

    // 循环处理:跳过DelegatingMetadataRel类型的包装节点
    // DelegatingMetadataRel是一种特殊的RelNode,它将元数据请求委托给内部的另一个RelNode
    // 通过循环解包,找到真正实现元数据计算的RelNode
    // 这样可以避免在包装层进行不必要的缓存和计算
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) { // while循环,条件是r是DelegatingMetadataRel的实例
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel(); // 将r设置为委托的目标RelNode,继续解包
    }

    // 变量声明:key是用于缓存的键对象
    // 该键将在后续用于从缓存中读取或写入元数据结果
    final Object key; // 声明一个final类型的Object变量key,用于存储缓存键
    key = methodKey1; // 将methodKey1赋值给key,methodKey1是averageRowSize方法的专用缓存键

    // 缓存查询:从元数据缓存map中获取当前RelNode和方法键对应的缓存值
    // mq.map是一个二维缓存,以RelNode和方法键为索引存储元数据结果
    // 如果缓存命中,可以直接返回缓存值,避免重复计算
    final Object v = mq.map.get(r, key); // 调用缓存map的get方法,传入RelNode和缓存键,获取缓存值v

    // 条件判断:检查缓存值是否不为null
    // 如果缓存命中(v != null),则需要进行后续处理
    if (v != null) { // if条件判断,如果缓存值v不为null,表示缓存命中

      // 循环依赖检测:检查缓存值是否为ACTIVE标记
      // ACTIVE标记表示当前元数据正在计算中,如果再次遇到ACTIVE,说明存在循环依赖
      // 循环依赖会导致无限递归,必须抛出异常中断查询
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // 检查v是否等于ACTIVE标记
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 抛出CyclicMetadataException异常,表示检测到循环依赖
      }

      // null值处理:检查缓存值是否为INSTANCE标记
      // INSTANCE标记表示缓存的值是null(因为缓存map不能存储null值)
      // 如果遇到INSTANCE标记,直接返回null,表示该元数据无法计算
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // 检查v是否等于INSTANCE标记
        return null; // 返回null,表示该元数据值为null
      }

      // 缓存命中返回:将缓存值强制转换为Double类型并返回
      // 此时v是有效的缓存结果,可以直接返回
      return (java.lang.Double) v; // 将Object类型的v强制转换为Double类型并返回
    }

    // 缓存标记:在开始计算前,将缓存值设置为ACTIVE标记
    // ACTIVE标记表示该元数据正在计算中,用于检测循环依赖
    // 如果在计算过程中再次查询相同元数据,会检测到ACTIVE并抛出异常
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 将缓存值设置为ACTIVE,标记计算开始

    // 异常处理:使用try-catch块捕获计算过程中的异常
    // 如果计算过程中发生异常,需要清除缓存,避免缓存被污染
    try { // try块开始,执行元数据计算逻辑

      // 实际计算:调用averageRowSize_方法进行实际的元数据计算
      // averageRowSize_是私有方法,根据RelNode的具体类型分发到provider1的相应方法
      // 计算结果存储在变量x中
      final java.lang.Double x = averageRowSize_(r, mq); // 调用私有方法averageRowSize_进行实际计算,结果存储在x中

      // 缓存更新:将计算结果存入缓存
      // NullSentinel.mask(x)方法用于处理null值,如果x为null,则返回INSTANCE标记,否则直接返回x
      // 这样可以确保缓存map中不存储null值,同时保留null语义
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果x存入缓存,使用mask方法处理null值
      return x; // 返回计算结果x
    } catch (java.lang.Exception e) { // 捕获所有Exception类型的异常

      // 缓存清理:如果发生异常,清除当前RelNode的所有缓存
      // 这样可以避免部分计算或异常状态污染缓存
      // 清除后,后续查询会重新计算,而不是使用错误的缓存值
      mq.map.row(r).clear(); // 清除缓存map中当前RelNode的所有缓存项
      throw e; // 重新抛出捕获的异常,让上层调用者处理
    }
  }

  // 方法:averageRowSize_() - 平均行大小的实际计算和分发方法(私有)
  // 该方法是一个分发器,根据RelNode的具体类型,将计算请求委托给provider1的相应重载方法
  // 与averageColumnSizes_不同,averageRowSize_只有一个RelNode类型的分支
  // 这是因为平均行大小的计算逻辑对所有RelNode类型都是通用的
  // 参数r:要计算元数据的RelNode关系节点
  // 参数mq:RelMetadataQuery元数据查询上下文
  // 返回值:Double类型,表示平均每行的大小
  private java.lang.Double averageRowSize_( // private方法,只能在类内部调用,返回Double类型
      org.apache.calcite.rel.RelNode r, // 参数r:RelNode类型,表示要计算元数据的关系节点
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) { // 参数mq:RelMetadataQuery类型,表示元数据查询上下文

    // 类型检查和分发:检查r是否为RelNode类型
    // RelNode是所有关系节点的基类,这个分支处理所有类型的RelNode
    // 如果是RelNode,调用provider1的averageRowSize方法
    // 该方法应该提供通用的默认实现,适用于所有RelNode类型
    if (r instanceof org.apache.calcite.rel.RelNode) { // if条件判断,检查r是否为RelNode的实例
      return provider1.averageRowSize((org.apache.calcite.rel.RelNode) r, mq); // 调用provider1的averageRowSize方法,传入强制转换后的r和mq
    } else { // 如果类型不匹配

      // 异常抛出:抛出IllegalArgumentException,表示没有找到对应的处理器
      // 这通常意味着传入了一个未知的RelNode类型
      // 异常消息中包含了方法签名和实际类型信息,方便调试
      // 建议用户创建一个catch-all(RelNode)处理器来处理这种情况
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.lang.Double org.apache.calcite.rel.metadata.BuiltInMetadata$Size$Handler.averageRowSize(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出异常,包含详细的方法签名和类型信息
    }
  }

} // 类定义结束,GeneratedMetadata_SizeHandler类的右花括号