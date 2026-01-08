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
package org.apache.calcite.plan.hep; // 包声明：HepRelMetadataProvider位于org.apache.calcite.plan.hep包中，这是HEP（HepPlanner）相关的包

import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数节点，是Calcite中所有关系表达式的基础接口
import org.apache.calcite.rel.metadata.Metadata; // 导入Metadata接口，表示元数据接口，用于存储和访问关系节点的元数据信息
import org.apache.calcite.rel.metadata.MetadataDef; // 导入MetadataDef类，表示元数据定义，描述元数据的类型和如何获取
import org.apache.calcite.rel.metadata.MetadataHandler; // 导入MetadataHandler接口，表示元数据处理器，负责实际计算元数据值
import org.apache.calcite.rel.metadata.RelMetadataProvider; // 导入RelMetadataProvider接口，表示关系元数据提供者，用于提供关系节点的元数据
import org.apache.calcite.rel.metadata.UnboundMetadata; // 导入UnboundMetadata接口，表示未绑定的元数据，需要绑定到具体的关系节点后才能使用

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList，表示不可变的列表
import com.google.common.collect.ImmutableMultimap; // 导入Google Guava的ImmutableMultimap，表示不可变的多重映射（一个键可以对应多个值）
import com.google.common.collect.Multimap; // 导入Google Guava的Multimap接口，表示多重映射数据结构

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Checker Framework的Nullable注解，用于标记可能为null的值

import java.lang.reflect.Method; // 导入Java反射的Method类，用于表示方法
import java.util.List; // 导入Java集合框架的List接口

import static java.util.Objects.requireNonNull; // 静态导入Objects.requireNonNull方法，用于检查对象是否为null，如果为null则抛出NullPointerException

/**
 * HepRelMetadataProvider implements the {@link RelMetadataProvider} interface
 * by combining metadata from the rels inside of a {@link HepRelVertex}.
 * HepRelMetadataProvider实现了RelMetadataProvider接口，通过组合HepRelVertex内部的rel的元数据来提供元数据服务。
 * 
 * 【类的作用】：
 * 这个类是HEP（HepPlanner，基于启发式规则的优化器）专用的元数据提供者。
 * 在HEP优化器中，关系表达式被包装在HepRelVertex中，HepRelVertex是HEP优化器的图结构中的顶点。
 * 当需要获取某个HepRelVertex的元数据时，这个提供者会：
 * 1. 检查输入的关系节点是否是HepRelVertex类型
 * 2. 如果是，则从HepRelVertex中剥离出实际的关系节点（通过stripped()方法）
 * 3. 使用实际关系节点的类来获取对应的元数据提供者
 * 4. 将元数据绑定到实际的关系节点上并返回
 * 
 * 【为什么需要这个类】：
 * 在HEP优化器中，关系节点被包装在HepRelVertex中，但元数据计算需要基于实际的关系节点。
 * 这个类起到了桥接作用，将HepRelVertex的元数据请求转发给内部实际关系节点的元数据提供者。
 * 
 * 【已弃用说明】：
 * 这个类已经被标记为@Deprecated，计划在2.0版本之前移除，说明HEP优化器可能正在重构或被新的优化器替代。
 */
@Deprecated // to be removed before 2.0 // 标记为已弃用，计划在2.0版本之前移除
class HepRelMetadataProvider implements RelMetadataProvider { // 类定义：HepRelMetadataProvider实现了RelMetadataProvider接口，用于提供关系元数据
  //~ Methods ---------------------------------------------------------------- // 方法区域标记（分隔符，用于代码组织）

  @Override public boolean equals(@Nullable Object obj) { // 重写equals方法，用于比较两个HepRelMetadataProvider对象是否相等
    return obj instanceof HepRelMetadataProvider; // 只要对象是HepRelMetadataProvider类型就返回true，所有HepRelMetadataProvider实例都相等
  }

  @Override public int hashCode() { // 重写hashCode方法，用于生成对象的哈希码
    return 107; // 返回固定的哈希值107，因为所有HepRelMetadataProvider实例都相等，所以它们的哈希码也应该相同
  }

  @Deprecated // to be removed before 2.0 // 标记为已弃用，计划在2.0版本之前移除
  @Override public <@Nullable M extends @Nullable Metadata> UnboundMetadata<M> apply( // 实现RelMetadataProvider接口的apply方法，用于获取未绑定的元数据
      Class<? extends RelNode> relClass, // 参数1：关系节点的类类型，表示要查询元数据的关系节点类型
      final Class<? extends M> metadataClass) { // 参数2：元数据的类类型，表示要查询的元数据类型（如RowCount、DistinctRowCount等）
    return (rel, mq) -> { // 返回一个lambda表达式，该表达式接受一个关系节点和元数据查询对象，返回绑定后的元数据
      if (!(rel instanceof HepRelVertex)) { // 检查输入的关系节点是否是HepRelVertex类型
        return null; // 如果不是HepRelVertex，返回null，表示无法提供元数据
      }
      final RelNode rel2 = rel.stripped(); // 如果是HepRelVertex，调用stripped()方法剥离出实际的关系节点（去掉HepRelVertex包装）
      UnboundMetadata<M> function = // 声明一个未绑定的元数据函数变量
          requireNonNull(rel.getCluster().getMetadataProvider(), "metadataProvider") // 获取关系节点的Cluster中的元数据提供者，并检查是否为null
              .apply(rel2.getClass(), metadataClass); // 使用实际关系节点的类和元数据类型调用元数据提供者的apply方法，获取未绑定的元数据函数
      return requireNonNull( // 检查元数据函数是否为null，如果为null则抛出异常
          function, // 要检查的元数据函数
          () -> "no metadata provider for class " + metadataClass) // 异常消息：如果没有找到对应元数据类的提供者
          .bind(rel2, mq); // 将未绑定的元数据绑定到实际的关系节点和元数据查询对象上，返回可用的元数据实例
    }; // lambda表达式结束
  }

  @Deprecated // to be removed before 2.0 // 标记为已弃用，计划在2.0版本之前移除
  @Override public <M extends Metadata> Multimap<Method, MetadataHandler<M>> handlers( // 实现RelMetadataProvider接口的handlers方法，用于获取元数据处理器映射
      MetadataDef<M> def) { // 参数：元数据定义对象，描述元数据的类型和方法
    return ImmutableMultimap.of(); // 返回一个空的不可变多重映射，表示这个提供者不提供任何元数据处理器
  }

  @Override public List<MetadataHandler<?>> handlers( // 重载的handlers方法，用于获取指定处理器类的处理器列表
      Class<? extends MetadataHandler<?>> handlerClass) { // 参数：元数据处理器的类类型
    return ImmutableList.of(); // 返回一个空的不可变列表，表示这个提供者不提供任何元数据处理器
  }
}
