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
package org.apache.calcite.adapter.elasticsearch; // 声明包名，该类属于Elasticsearch适配器包

import com.fasterxml.jackson.databind.node.ObjectNode; // 导入Jackson库的ObjectNode类，用于表示JSON对象节点
import com.google.common.collect.AbstractSequentialIterator; // 导入Guava库的抽象顺序迭代器，用于实现自定义的顺序迭代逻辑
import com.google.common.collect.Iterators; // 导入Guava库的Iterators工具类，提供迭代器的转换和组合操作

import java.util.Collections; // 导入Java集合工具类，用于创建不可修改的集合
import java.util.Iterator; // 导入Java迭代器接口
import java.util.function.Consumer; // 导入Java函数式接口，用于定义接受单个参数的操作

import static com.google.common.base.Preconditions.checkArgument; // 导入Guava的前置条件检查工具

import static java.util.Objects.requireNonNull; // 导入Java对象验证工具

/**
 * "Iterator" which retrieves results lazily and in batches. Uses
 * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html">Elastic Scrolling API</a>
 * to optimally consume large search results.
 * 
 * 该类是一个迭代器，用于以延迟和批处理的方式检索结果。使用Elasticsearch的滚动API
 * 来优化消费大型搜索结果集。滚动API允许在大量数据分页时保持搜索上下文，
 * 从而避免在深度分页时出现性能问题。
 * 
 * <p>This class is <strong>not thread safe</strong>.
 * 该类不是线程安全的，不能在多线程环境下使用。
 */
class Scrolling { // 定义Scrolling类，用于封装Elasticsearch的滚动查询功能

  private final ElasticsearchTransport transport; // Elasticsearch传输层对象，用于与Elasticsearch集群进行通信，执行搜索和滚动操作
  private final int fetchSize; // 每次滚动请求获取的文档数量，即批处理大小

  Scrolling(ElasticsearchTransport transport) { // 构造方法，创建Scrolling实例
    this.transport = requireNonNull(transport, "transport"); // 验证transport参数不为null，否则抛出NullPointerException
    final int fetchSize = transport.fetchSize; // 从transport对象中获取配置的批量获取大小
    checkArgument(fetchSize > 0, // 验证fetchSize必须大于0，否则抛出IllegalArgumentException
        "invalid fetch size. Expected %s > 0", fetchSize); // 错误消息提示期望fetchSize大于0
    this.fetchSize = fetchSize; // 将验证过的fetchSize赋值给成员变量
  }

  Iterator<ElasticsearchJson.SearchHit> query(ObjectNode query) { // 执行查询并返回搜索结果的迭代器
    requireNonNull(query, "query"); // 验证query参数不为null，否则抛出NullPointerException
    final long limit; // 定义查询结果的最大数量限制
    if (query.has("size")) { // 检查查询对象中是否包含size参数（指定返回结果的最大数量）
      limit = query.get("size").asLong(); // 从查询中获取size参数的值作为limit
      if (fetchSize > limit) { // 如果批量获取大小大于限制数量
        // don't use scrolling when batch size is greater than limit // 当批量大小大于限制时，不需要使用滚动机制
        return transport.search().apply(query).searchHits().hits().iterator(); // 直接执行普通搜索并返回结果迭代器
      }
    } else { // 如果查询中没有指定size参数
      limit = Long.MAX_VALUE; // 将limit设置为最大值，表示不限制返回结果数量
    }

    query.put("size", fetchSize); // 将查询的size参数设置为批量获取大小，控制每次滚动返回的文档数
    final ElasticsearchJson.Result first = transport // 执行第一次搜索请求
        .search(Collections.singletonMap("scroll", "1m")).apply(query); // 设置滚动上下文保持时间为1分钟

    AutoClosingIterator iterator = // 创建自动关闭的迭代器包装器
        new AutoClosingIterator(new SequentialIterator(first, transport, limit), // 内部使用顺序迭代器来处理滚动逻辑
            scrollId -> transport.closeScroll(Collections.singleton(scrollId))); // 定义关闭滚动上下文的操作，用于在迭代结束时释放资源

    Iterator<ElasticsearchJson.SearchHit> result = flatten(iterator); // 将多个Result对象展平为单个SearchHit迭代器
    // apply limit // 应用数量限制
    if (limit != Long.MAX_VALUE) { // 如果限制不是最大值（即有限制）
      result = Iterators.limit(result, (int) limit); // 对结果迭代器应用限制，最多返回limit个结果
    }

    return result; // 返回最终的搜索结果迭代器
  }

  /**
   * Combines lazily multiple {@link ElasticsearchJson.Result} into a single iterator of
   * {@link ElasticsearchJson.SearchHit}.
   * 
   * 该方法将多个ElasticsearchJson.Result对象延迟地组合成一个单一的SearchHit迭代器。
   * 每个Result对象包含一批搜索结果，该方法将所有批次的结果串联起来，
   * 提供一个统一的迭代器接口，使得调用者可以透明地遍历所有结果。
   */
  private static Iterator<ElasticsearchJson.SearchHit> flatten( // 定义静态方法，用于展平结果迭代器
      Iterator<ElasticsearchJson.Result> results) { // 参数是Result对象的迭代器
    final Iterator<Iterator<ElasticsearchJson.SearchHit>> inputs = // 创建一个迭代器的迭代器
        Iterators.transform(results, // 对results迭代器中的每个Result对象进行转换
            input -> input.searchHits().hits().iterator()); // 将每个Result转换为包含SearchHit的迭代器
    return Iterators.concat(inputs); // 使用Guava的concat方法将所有SearchHit迭代器连接成一个统一的迭代器
  }

  /**
   * Observes when existing iterator has ended and clears context (scroll) if any.
   * 
   * 该类是一个自动关闭的迭代器包装器，用于观察底层迭代器何时结束，
   * 并在结束时自动清理滚动上下文（如果存在）。这确保了Elasticsearch的
   * 滚动上下文资源能够被正确释放，避免内存泄漏。
   */
  private static class AutoClosingIterator implements Iterator<ElasticsearchJson.Result>, // 定义内部类，实现Iterator接口和AutoCloseable接口
      AutoCloseable { // AutoCloseable接口允许在try-with-resources语句中使用
    private final Iterator<ElasticsearchJson.Result> delegate; // 委托的迭代器，实际的迭代逻辑由它执行
    private final Consumer<String> closer; // 用于关闭滚动上下文的消费者函数，接受scrollId作为参数

    /** Returns whether {@link #closer} consumer was already called. */
    private boolean closed; // 标记是否已经调用过关闭操作，防止重复关闭

    /** Keeps last value of {@code scrollId} in memory so scroll can be released
     * upon termination. */
    private String scrollId; // 保存最近的scrollId，以便在迭代结束时能够释放滚动上下文

    private AutoClosingIterator( // 构造方法，创建AutoClosingIterator实例
        final Iterator<ElasticsearchJson.Result> delegate, // 委托的迭代器参数
        final Consumer<String> closer) { // 关闭操作的消费者参数
      this.delegate = delegate; // 保存委托迭代器
      this.closer = closer; // 保存关闭操作
    }

    @Override public void close() { // 实现AutoCloseable接口的close方法
      if (!closed && scrollId != null) { // 如果尚未关闭且scrollId不为空
        // close once (if scrollId is present) // 只关闭一次（当scrollId存在时）
        closer.accept(scrollId); // 调用关闭操作，释放滚动上下文
      }
      closed = true; // 标记为已关闭，防止重复关闭
    }

    @Override public boolean hasNext() { // 实现Iterator接口的hasNext方法，检查是否还有下一个元素
      final boolean hasNext = delegate.hasNext(); // 委托给底层迭代器检查是否有下一个元素
      if (!hasNext) { // 如果没有更多元素
        close(); // 自动调用close方法释放滚动上下文
      }
      return hasNext; // 返回是否有下一个元素
    }

    @Override public ElasticsearchJson.Result next() { // 实现Iterator接口的next方法，获取下一个元素
      ElasticsearchJson.Result next = delegate.next(); // 委托给底层迭代器获取下一个Result对象
      next.scrollId().ifPresent(id -> scrollId = id); // 如果Result中包含scrollId，则保存它以便后续关闭使用
      return next; // 返回获取到的Result对象
    }
  }

  /**
   * Iterator which consumes current {@code scrollId} until full search result is fetched
   * or {@code limit} is reached.
   * 
   * 该类是一个顺序迭代器，通过消费当前的scrollId来获取完整的搜索结果，
   * 直到获取到所有结果或达到指定的限制数量。它继承自AbstractSequentialIterator，
   * 实现了按顺序计算下一个结果的逻辑。
   */
  private static class SequentialIterator // 定义内部类，继承自AbstractSequentialIterator
      extends AbstractSequentialIterator<ElasticsearchJson.Result> { // 泛型参数为ElasticsearchJson.Result

    private final ElasticsearchTransport transport; // Elasticsearch传输层对象，用于执行滚动请求
    private final long limit; // 获取结果的最大数量限制
    private long count; // 已获取的结果计数器

    private SequentialIterator(final ElasticsearchJson.Result first, // 构造方法，创建SequentialIterator实例
        final ElasticsearchTransport transport, final long limit) { // 参数：第一个结果、传输层对象、限制数量
      super(first); // 调用父类构造方法，传入第一个结果作为初始值
      this.transport = transport; // 保存传输层对象
      checkArgument(limit >= 0, // 验证limit必须大于等于0，否则抛出IllegalArgumentException
          "limit: %s >= 0", limit); // 错误消息提示期望limit大于等于0
      this.limit = limit; // 保存限制数量
    }

    @Override protected ElasticsearchJson.Result computeNext( // 实现抽象方法，计算下一个结果
        final ElasticsearchJson.Result previous) { // 参数是前一个结果对象
      final int hits = previous.searchHits().hits().size(); // 获取前一个结果中包含的文档数量
      if (hits == 0 || count >= limit) { // 如果没有更多结果或已达到限制数量
        // stop (re-)requesting when limit is reached or no more results // 当达到限制或没有更多结果时停止请求
        return null; // 返回null表示迭代结束
      }

      count += hits; // 将当前批次的文档数量累加到计数器
      final String scrollId = previous.scrollId() // 获取前一个结果中的scrollId
          .orElseThrow(() -> new IllegalStateException("scrollId has to be present")); // 如果scrollId不存在则抛出异常

      return transport.scroll().apply(scrollId); // 使用scrollId执行滚动请求，获取下一批结果
    }
  }
}
