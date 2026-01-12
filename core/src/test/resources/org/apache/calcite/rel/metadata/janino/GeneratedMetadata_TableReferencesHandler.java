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
// Apache Calcite开源框架的元数据包，包含元数据查询和处理相关的类
package org.apache.calcite.rel.metadata.janino;

// GeneratedMetadata_TableReferencesHandler: 这是一个自动生成的处理器类，用于处理表引用(TableReferences)元数据查询
// 该类实现了BuiltInMetadata.TableReferences.Handler接口，是Calcite元数据系统中的核心组件
// 主要功能：为不同类型的RelNode(关系表达式节点)提供获取表引用的元数据查询能力
// 设计模式：使用了缓存机制和类型分发模式，通过instanceof判断调用相应的处理方法
// 自动生成：这个类通常由RelMetadataHandlerGenerator工具自动生成，无需手动编写
// 性能优化：通过缓存机制避免重复计算，通过类型分发实现高效的元数据查询
public final class GeneratedMetadata_TableReferencesHandler
  implements org.apache.calcite.rel.metadata.BuiltInMetadata.TableReferences.Handler {
  // methodKey0: 元数据方法的缓存键，用于在元数据缓存中唯一标识getTableReferences()方法
  // 使用DescriptiveCacheKey包装，包含描述性信息"Set Handler.getTableReferences()"
  // 这个键在缓存中用于存储和检索计算结果，避免重复计算
  private final Object methodKey0 =
      new org.apache.calcite.rel.metadata.janino.DescriptiveCacheKey("Set Handler.getTableReferences()");
  // provider0: 实际的元数据提供者，包含各种RelNode类型的getTableReferences()方法实现
  // 这个对象存储了针对不同RelNode类型(Aggregate, Filter, Join等)的具体元数据计算逻辑
  // 通过provider0可以将元数据查询委托给具体的实现类处理
  public final org.apache.calcite.rel.metadata.RelMdTableReferences provider0;
  // 构造方法：初始化TableReferences处理器
  // 参数provider0: 元数据提供者实例，包含所有RelNode类型的getTableReferences()实现
  // 这个构造方法将provider0保存到成员变量中，供后续方法使用
  public GeneratedMetadata_TableReferencesHandler(
      org.apache.calcite.rel.metadata.RelMdTableReferences provider0) {
    this.provider0 = provider0; // 将传入的元数据提供者保存到成员变量
  }
  // getDef(): 获取元数据定义，返回MetadataDef对象
  // MetadataDef包含了元数据的元信息，如元数据名称、处理类等
  // 这个方法直接委托给provider0.getDef()获取定义信息
  // 返回值: MetadataDef对象，描述TableReferences元数据的定义
  public org.apache.calcite.rel.metadata.MetadataDef getDef() {
    return provider0.getDef(); // 委托给provider0获取元数据定义
  }
  // getTableReferences(): 获取关系表达式节点所引用的表集合
  // 参数r: 要查询的RelNode关系表达式节点，可能是任何类型的RelNode(Aggregate, Filter, Join等)
  // 参数mq: RelMetadataQuery元数据查询对象，用于访问元数据缓存和执行查询
  // 返回值: Set集合，包含该RelNode节点引用的所有表的标识符
  // 实现机制：
  // 1. 首先跳过DelegatingMetadataRel类型的代理节点
  // 2. 检查缓存中是否已有计算结果
  // 3. 如果缓存中有，直接返回缓存结果
  // 4. 检测循环依赖(如果发现ACTIVE标记则抛出异常)
  // 5. 如果缓存中没有，计算结果并缓存
  // 6. 处理异常情况，清理缓存
  public java.util.Set getTableReferences(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) {
    // 处理委托元数据节点：如果r是DelegatingMetadataRel类型，则获取其委托的真正RelNode
    // DelegatingMetadataRel是一个包装器，实际的RelNode通过getMetadataDelegateRel()获取
    // 使用while循环是因为可能有多层嵌套的委托关系
    while (r instanceof org.apache.calcite.rel.metadata.DelegatingMetadataRel) {
      r = ((org.apache.calcite.rel.metadata.DelegatingMetadataRel) r).getMetadataDelegateRel();
    }
    final Object key; // 声明缓存键变量
    key = methodKey0; // 使用预定义的methodKey0作为缓存键
    final Object v = mq.map.get(r, key); // 从缓存中获取(r, key)对应的值
    if (v != null) { // 如果缓存中存在值
      if (v == org.apache.calcite.rel.metadata.NullSentinel.ACTIVE) { // 检查是否为ACTIVE标记，表示正在计算中
        throw new org.apache.calcite.rel.metadata.CyclicMetadataException(); // 如果发现循环依赖，抛出循环元数据异常
      }
      if (v == org.apache.calcite.rel.metadata.NullSentinel.INSTANCE) { // 检查是否为INSTANCE标记，表示缓存值为null
        return null; // 返回null
      }
      return (java.util.Set) v; // 返回缓存中的实际结果
    }
    mq.map.put(r, key,org.apache.calcite.rel.metadata.NullSentinel.ACTIVE); // 将缓存值设置为ACTIVE，表示开始计算
    try {
      final java.util.Set x = getTableReferences_(r, mq); // 调用实际计算方法getTableReferences_获取结果
      mq.map.put(r, key, org.apache.calcite.rel.metadata.NullSentinel.mask(x)); // 将计算结果存入缓存，使用mask处理null值
      return x; // 返回计算结果
    } catch (java.lang.Exception e) { // 捕获计算过程中的异常
      mq.map.row(r).clear(); // 清理该RelNode的所有缓存项，避免脏数据
      throw e; // 重新抛出异常
    }
  }

  // getTableReferences_(): 内部方法，根据RelNode的具体类型调用相应的getTableReferences实现
  // 这是类型分发的核心方法，通过instanceof判断RelNode的实际类型
  // 参数r: 关系表达式节点，此时已经跳过了DelegatingMetadataRel代理
  // 参数mq: 元数据查询对象
  // 返回值: 该RelNode引用的表集合
  // 实现机制：使用if-else if链进行类型检查和分发，将查询委托给provider0的对应重载方法
  // 注意：最后有一个catch-all的else分支处理RelNode基类型，确保所有类型都有处理
  private java.util.Set getTableReferences_(
      org.apache.calcite.rel.RelNode r,
      org.apache.calcite.rel.metadata.RelMetadataQuery mq) {
    if (r instanceof org.apache.calcite.plan.volcano.RelSubset) { // 检查是否为RelSubset类型(Volcano优化器中的等价关系集合)
      return provider0.getTableReferences((org.apache.calcite.plan.volcano.RelSubset) r, mq); // 调用RelSubset专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Aggregate) { // 检查是否为Aggregate聚合节点
      return provider0.getTableReferences((org.apache.calcite.rel.core.Aggregate) r, mq); // 调用Aggregate专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Calc) { // 检查是否为Calc计算节点
      return provider0.getTableReferences((org.apache.calcite.rel.core.Calc) r, mq); // 调用Calc专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Exchange) { // 检查是否为Exchange交换节点(用于分布式数据交换)
      return provider0.getTableReferences((org.apache.calcite.rel.core.Exchange) r, mq); // 调用Exchange专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Filter) { // 检查是否为Filter过滤节点
      return provider0.getTableReferences((org.apache.calcite.rel.core.Filter) r, mq); // 调用Filter专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Join) { // 检查是否为Join连接节点
      return provider0.getTableReferences((org.apache.calcite.rel.core.Join) r, mq); // 调用Join专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Project) { // 检查是否为Project投影节点
      return provider0.getTableReferences((org.apache.calcite.rel.core.Project) r, mq); // 调用Project专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Sample) { // 检查是否为Sample采样节点
      return provider0.getTableReferences((org.apache.calcite.rel.core.Sample) r, mq); // 调用Sample专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.SetOp) { // 检查是否为SetOp集合操作节点(UNION, INTERSECT等)
      return provider0.getTableReferences((org.apache.calcite.rel.core.SetOp) r, mq); // 调用SetOp专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Sort) { // 检查是否为Sort排序节点
      return provider0.getTableReferences((org.apache.calcite.rel.core.Sort) r, mq); // 调用Sort专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.TableModify) { // 检查是否为TableModify表修改节点(INSERT, UPDATE, DELETE)
      return provider0.getTableReferences((org.apache.calcite.rel.core.TableModify) r, mq); // 调用TableModify专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.TableScan) { // 检查是否为TableScan表扫描节点
      return provider0.getTableReferences((org.apache.calcite.rel.core.TableScan) r, mq); // 调用TableScan专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.core.Window) { // 检查是否为Window窗口函数节点
      return provider0.getTableReferences((org.apache.calcite.rel.core.Window) r, mq); // 调用Window专用的处理方法
    } else if (r instanceof org.apache.calcite.rel.RelNode) { // catch-all分支：处理所有其他RelNode类型
      return provider0.getTableReferences((org.apache.calcite.rel.RelNode) r, mq); // 调用通用的RelNode处理方法
    } else { // 如果没有匹配任何已知类型，抛出异常
            throw new java.lang.IllegalArgumentException("No handler for method [public abstract java.util.Set org.apache.calcite.rel.metadata.BuiltInMetadata$TableReferences$Handler.getTableReferences(org.apache.calcite.rel.RelNode,org.apache.calcite.rel.metadata.RelMetadataQuery)] applied to argument of type [" + r.getClass() + "]; we recommend you create a catch-all (RelNode) handler"); // 抛出异常，提示用户创建catch-all处理器
    }
  }

}
