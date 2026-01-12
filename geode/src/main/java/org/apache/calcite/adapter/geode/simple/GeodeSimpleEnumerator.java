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
package org.apache.calcite.adapter.geode.simple; // 指定当前类所在的包路径，该包用于Geode适配器的简单枚举器实现

import org.apache.calcite.linq4j.Enumerator; // 导入Calcite的LINQ4J枚举器接口，用于实现数据枚举功能

import org.apache.geode.cache.client.ClientCache; // 导入Geode客户端缓存接口，用于连接和操作Geode集群

import org.apache.geode.cache.query.QueryService; // 导入Geode查询服务接口，用于执行OQL查询

import org.apache.geode.cache.query.SelectResults; // 导入Geode查询结果接口，用于封装查询返回的数据集

import org.checkerframework.checker.nullness.qual.Nullable; // 导入空值检查注解，用于标记可能为null的字段

import java.util.Iterator; // 导入Java迭代器接口，用于遍历查询结果集

/**
 * Geode Simple Enumerator. // Geode简单枚举器类，用于从Geode数据存储中枚举数据
 * // 该类是一个抽象类，实现了Calcite的Enumerator接口，提供了从Geode Region中查询数据并转换为Calcite可识别格式的基础功能
 * // 它通过执行OQL（Object Query Language）查询来获取Geode Region中的所有数据，并提供了迭代访问这些数据的能力
 * // 子类需要实现convert方法来定义如何将Geode中的原始对象转换为目标类型E
 *
 * @param <E> Element type // 泛型参数E表示枚举器返回的元素类型，由子类根据具体业务需求指定
 */
public abstract class GeodeSimpleEnumerator<E> implements Enumerator<E> { // 定义抽象类GeodeSimpleEnumerator，实现Enumerator接口，提供Geode数据枚举的基础实现

  private @Nullable Iterator results; // 成员变量：存储Geode查询结果的迭代器，用于遍历查询返回的数据，使用@Nullable注解表示可能为null

  private E current; // 成员变量：存储当前迭代位置的元素对象，类型为泛型E，由current()方法返回给调用者

  protected GeodeSimpleEnumerator(ClientCache clientCache, String regionName) { // 构造方法：接收Geode客户端缓存和Region名称作为参数，初始化枚举器并执行查询
    QueryService queryService = clientCache.getQueryService(); // 从客户端缓存中获取查询服务对象，用于创建和执行OQL查询
    String oql = "select * from /" + regionName.trim(); // 构建OQL查询语句，查询指定Region中的所有数据，regionName.trim()去除Region名称两端的空格
    try { // 开始try-catch块，捕获查询执行过程中可能出现的异常
      results = ((SelectResults) queryService.newQuery(oql).execute()).iterator(); // 创建查询对象，执行OQL查询，将结果转换为SelectResults，然后获取其迭代器并赋值给results成员变量
    } catch (Exception e) { // 捕获查询执行过程中的所有异常
      e.printStackTrace(); // 打印异常堆栈信息，便于调试和问题定位
      results = null; // 将results设置为null，表示查询失败，后续moveNext()方法会抛出IllegalStateException
    } // 结束try-catch块
  } // 结束构造方法

  @Override public E current() { // 实现Enumerator接口的current()方法，返回当前迭代位置的元素
    return current; // 直接返回current成员变量的值，该值由moveNext()方法在迭代过程中设置
  } // 结束current()方法

  @Override public boolean moveNext() { // 实现Enumerator接口的moveNext()方法，移动迭代器到下一个位置并判断是否还有数据
    if (results == null) { // 检查results迭代器是否为null（即查询是否失败）
      throw new IllegalStateException(); // 如果results为null，抛出IllegalStateException异常，表示枚举器处于非法状态
    } // 结束if判断
    if (results.hasNext()) { // 检查迭代器是否还有下一个元素
      current = convert(results.next()); // 如果有下一个元素，调用results.next()获取下一个元素，然后调用抽象方法convert()将其转换为目标类型E，并赋值给current成员变量
      return true; // 返回true，表示成功移动到下一个元素，current()方法可以返回有效值
    } // 结束if判断
    current = null; // 如果迭代器没有下一个元素，将current设置为null，表示已到达数据末尾
    return false; // 返回false，表示没有更多数据可供枚举
  } // 结束moveNext()方法

  @Override public void reset() { // 实现Enumerator接口的reset()方法，用于重置迭代器到初始位置
    throw new UnsupportedOperationException(); // 抛出UnsupportedOperationException异常，表示不支持重置操作，Geode查询结果迭代器通常不支持重置
  } // 结束reset()方法

  @Override public void close() { // 实现Enumerator接口的close()方法，用于关闭枚举器并释放相关资源
    /*clientCache.close(); */ // 注释掉的代码：原本用于关闭客户端缓存，但被注释掉，因为客户端缓存可能被多个枚举器共享，不应在此处关闭
  } // 结束close()方法

  public abstract E convert(Object obj); // 抽象方法：将Geode查询结果中的原始对象转换为目标类型E，由子类根据具体业务需求实现转换逻辑
} // 结束GeodeSimpleEnumerator类定义
