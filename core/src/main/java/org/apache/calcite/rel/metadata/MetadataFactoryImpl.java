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
package org.apache.calcite.rel.metadata; // 元数据包，包含关系表达式元数据相关的类和接口

import org.apache.calcite.rel.RelNode; // 关系表达式接口，表示关系代数中的操作
import org.apache.calcite.util.Pair; // 工具类，用于存储键值对
import org.apache.calcite.util.Util; // 工具类，提供各种实用方法

import com.google.common.cache.CacheBuilder; // Google Guava缓存构建器，用于创建缓存
import com.google.common.cache.CacheLoader; // Google Guava缓存加载器，用于加载缓存数据
import com.google.common.cache.LoadingCache; // Google Guava加载缓存接口，支持自动加载
import com.google.common.util.concurrent.UncheckedExecutionException; // 未检查的执行异常

import org.checkerframework.checker.nullness.qual.Nullable; // 注解，标记可为null的类型

import java.util.concurrent.ExecutionException; // 执行异常，当任务执行失败时抛出

/**
 * Implementation of {@link MetadataFactory} that gets providers from a
 * {@link RelMetadataProvider} and stores them in a cache.
 * MetadataFactory接口的实现类，从RelMetadataProvider获取提供者并存储在缓存中
 *
 * <p>The cache does not store metadata. It remembers which providers can
 * provide which kinds of metadata, for which kinds of relational
 * expressions.
 * 缓存不存储元数据本身，它记住哪些提供者可以为哪些类型的关系表达式提供哪些类型的元数据
 * 这样可以避免重复查询RelMetadataProvider，提高性能
 *
 * @deprecated Use {@link RelMetadataQuery}.
 * 已废弃：请使用RelMetadataQuery替代
 */
@Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
public class MetadataFactoryImpl implements MetadataFactory { // 实现MetadataFactory接口的元数据工厂
  @SuppressWarnings("unchecked") // 抑制未检查的类型转换警告
  public static final UnboundMetadata<@Nullable Metadata> DUMMY = (rel, mq) -> null; // 虚拟的未绑定元数据对象，用于缓存中标记不存在的提供者，避免重复查询

  private final LoadingCache< // 使用Google Guava的LoadingCache实现缓存
      Pair<Class<RelNode>, Class<Metadata>>, // 缓存键：关系表达式类和元数据类的键值对
      UnboundMetadata<@Nullable Metadata>> cache; // 缓存值：未绑定的元数据对象，可以绑定到具体的关系表达式

  public MetadataFactoryImpl(RelMetadataProvider provider) { // 构造方法，接收一个RelMetadataProvider提供者
    this.cache = CacheBuilder.newBuilder().build(loader(provider)); // 使用CacheBuilder构建缓存，并使用指定的加载器
  }

  private static CacheLoader<Pair<Class<RelNode>, Class<Metadata>>, // 创建缓存加载器，用于在缓存未命中时加载提供者
      UnboundMetadata<@Nullable Metadata>> loader(final RelMetadataProvider provider) { // 返回一个CacheLoader实例
    //noinspection RedundantTypeArguments // 抑制冗余类型参数警告
    return CacheLoader.<Pair<Class<RelNode>, Class<Metadata>>, // 使用CacheLoader.from方法创建加载器
        UnboundMetadata<@Nullable Metadata>>from(key -> { // 定义加载逻辑，接收一个键并返回值
          final UnboundMetadata<@Nullable Metadata> function = // 从提供者中获取未绑定的元数据函数
              provider.apply(key.left, key.right); // key.left是关系表达式类，key.right是元数据类
          // Return DUMMY, not null, so the cache knows to not ask again.
          // 返回DUMMY而不是null，这样缓存就知道不需要再次询问，避免重复查询
          return function != null ? function : DUMMY; // 如果提供者存在则返回，否则返回DUMMY占位符
        });
  }

  @Override public <@Nullable M extends @Nullable Metadata> M query( // 查询元数据方法，泛型M是元数据类型
      RelNode rel, RelMetadataQuery mq, // 参数：关系表达式和元数据查询上下文
      Class<M> metadataClazz) { // 参数：要查询的元数据类
    try { // 尝试执行查询
      //noinspection unchecked // 抑制未检查的类型转换警告
      final Pair<Class<RelNode>, Class<Metadata>> key = // 创建缓存键，包含关系表达式类和元数据类
          Pair.of((Class<RelNode>) rel.getClass(), (Class<Metadata>) metadataClazz); // 使用Pair.of创建键值对
      final Metadata apply = cache.get(key).bind(rel, mq); // 从缓存获取未绑定元数据，然后绑定到具体的关系表达式和查询上下文
      return metadataClazz.cast(apply); // 将结果转换为请求的元数据类型并返回
    } catch (UncheckedExecutionException | ExecutionException e) { // 捕获执行异常
      throw Util.throwAsRuntime(Util.causeOrSelf(e)); // 将异常包装为运行时异常抛出
    }
  }
}
