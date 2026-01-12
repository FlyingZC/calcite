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
package org.apache.calcite.adapter.elasticsearch;

import org.apache.calcite.test.Unsafe;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.calcite.adapter.elasticsearch.QueryBuilders.boolQuery;
import static org.apache.calcite.adapter.elasticsearch.QueryBuilders.matchesQuery;
import static org.apache.calcite.adapter.elasticsearch.QueryBuilders.rangeQuery;
import static org.apache.calcite.adapter.elasticsearch.QueryBuilders.termQuery;
import static org.apache.calcite.adapter.elasticsearch.QueryBuilders.termsQuery;
import static org.apache.calcite.adapter.elasticsearch.QueryBuildersTest.JsonMatcher.hasJson;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * QueryBuildersTest 是 Elasticsearch 适配器的测试类
 * 用于验证内部查询是否正确转换为 Elasticsearch 搜索查询（JSON 格式）
 * 测试 QueryBuilders 工具类生成的各种查询构建器（QueryBuilder）的序列化结果是否符合预期
 * 包括 term 查询、terms 查询、bool 查询、exists 查询、range 查询、matchAll 查询、match 查询等
 */
class QueryBuildersTest {

  /**
   * 测试简单的标量项查询（term 查询）
   * term 查询用于精确匹配字段值，适用于布尔值、整数、浮点数、字符串等基本数据类型
   * 测试了多种数据类型：字符串、字符、布尔值、字节、长整型、短整型、双精度、单精度、BigDecimal、BigInteger、原子类型等
   * 每个 assertThat 验证 termQuery 方法生成的 QueryBuilder 序列化为 JSON 后是否符合预期格式
   * JSON 格式为：{"term":{"字段名":"值"}} 或 {"term":{"字段名":数值}}
   */
  @Test void term() {
    assertThat(termQuery("foo", "bar"), // 测试字符串类型的 term 查询，字段 foo 的值为 bar
        hasJson("{\"term\":{\"foo\":\"bar\"}}")); // 期望生成的 JSON 为 {"term":{"foo":"bar"}}
    assertThat(termQuery("bar", "foo"), // 测试字符串类型的 term 查询，字段 bar 的值为 foo
        hasJson("{\"term\":{\"bar\":\"foo\"}}")); // 期望生成的 JSON 为 {"term":{"bar":"foo"}}
    assertThat(termQuery("foo", 'A'), // 测试字符类型的 term 查询，字段 foo 的值为字符 'A'
        hasJson("{\"term\":{\"foo\":\"A\"}}")); // 期望生成的 JSON 为 {"term":{"foo":"A"}}
    assertThat(termQuery("foo", true), // 测试布尔值 true 的 term 查询，字段 foo 的值为 true
        hasJson("{\"term\":{\"foo\":true}}")); // 期望生成的 JSON 为 {"term":{"foo":true}}
    assertThat(termQuery("foo", false), // 测试布尔值 false 的 term 查询，字段 foo 的值为 false
        hasJson("{\"term\":{\"foo\":false}}")); // 期望生成的 JSON 为 {"term":{"foo":false}}
    assertThat(termQuery("foo", (byte) 0), // 测试字节类型的 term 查询，字段 foo 的值为 0
        hasJson("{\"term\":{\"foo\":0}}")); // 期望生成的 JSON 为 {"term":{"foo":0}}
    assertThat(termQuery("foo", (long) 123), // 测试长整型的 term 查询，字段 foo 的值为 123
        hasJson("{\"term\":{\"foo\":123}}")); // 期望生成的 JSON 为 {"term":{"foo":123}}
    assertThat(termQuery("foo", (short) 41), // 测试短整型的 term 查询，字段 foo 的值为 41
        hasJson("{\"term\":{\"foo\":41}}")); // 期望生成的 JSON 为 {"term":{"foo":41}}
    assertThat(termQuery("foo", 42.42D), // 测试双精度浮点数的 term 查询，字段 foo 的值为 42.42
        hasJson("{\"term\":{\"foo\":42.42}}")); // 期望生成的 JSON 为 {"term":{"foo":42.42}}
    assertThat(termQuery("foo", 1.1F), // 测试单精度浮点数的 term 查询，字段 foo 的值为 1.1
        hasJson("{\"term\":{\"foo\":1.1}}")); // 期望生成的 JSON 为 {"term":{"foo":1.1}}
    assertThat(termQuery("foo", new BigDecimal(1)), // 测试 BigDecimal 类型的 term 查询，字段 foo 的值为 1
        hasJson("{\"term\":{\"foo\":1}}")); // 期望生成的 JSON 为 {"term":{"foo":1}}
    assertThat(termQuery("foo", new BigInteger("121")), // 测试 BigInteger 类型的 term 查询，字段 foo 的值为 121
        hasJson("{\"term\":{\"foo\":121}}")); // 期望生成的 JSON 为 {"term":{"foo":121}}
    assertThat(termQuery("foo", new AtomicLong(111)), // 测试 AtomicLong 类型的 term 查询，字段 foo 的值为 111
        hasJson("{\"term\":{\"foo\":111}}")); // 期望生成的 JSON 为 {"term":{"foo":111}}
    assertThat(termQuery("foo", new AtomicInteger(222)), // 测试 AtomicInteger 类型的 term 查询，字段 foo 的值为 222
        hasJson("{\"term\":{\"foo\":222}}")); // 期望生成的 JSON 为 {"term":{"foo":222}}
    assertThat(termQuery("foo", new AtomicBoolean(true)), // 测试 AtomicBoolean 类型的 term 查询，字段 foo 的值为 true
        hasJson("{\"term\":{\"foo\":true}}")); // 期望生成的 JSON 为 {"term":{"foo":true}}
  }

  @Test void terms() {
    assertThat(termsQuery("foo", Collections.emptyList()), // 测试空列表的 terms 查询，字段 foo 的值为空数组
        hasJson("{\"terms\":{\"foo\":[]}}")); // 期望生成的 JSON 为 {"terms":{"foo":[]}}

    assertThat(termsQuery("bar", Collections.emptySet()), // 测试空集合的 terms 查询，字段 bar 的值为空数组
        hasJson("{\"terms\":{\"bar\":[]}}")); // 期望生成的 JSON 为 {"terms":{"bar":[]}}

    assertThat(termsQuery("singleton", Collections.singleton(0)), // 测试单个元素的 terms 查询，字段 singleton 的值为 [0]
        hasJson("{\"terms\":{\"singleton\":[0]}}")); // 期望生成的 JSON 为 {"terms":{"singleton":[0]}}

    assertThat(termsQuery("foo", Collections.singleton(true)), // 测试单个布尔值的 terms 查询，字段 foo 的值为 [true]
        hasJson("{\"terms\":{\"foo\":[true]}}")); // 期望生成的 JSON 为 {"terms":{"foo":[true]}}

    assertThat(termsQuery("foo", Collections.singleton("bar")), // 测试单个字符串的 terms 查询，字段 foo 的值为 ["bar"]
        hasJson("{\"terms\":{\"foo\":[\"bar\"]}}")); // 期望生成的 JSON 为 {"terms":{"foo":["bar"]}}

    assertThat(termsQuery("foo", Collections.singletonList("bar")), // 测试单元素列表的 terms 查询，字段 foo 的值为 ["bar"]
        hasJson("{\"terms\":{\"foo\":[\"bar\"]}}")); // 期望生成的 JSON 为 {"terms":{"foo":["bar"]}}

    assertThat(termsQuery("foo", Arrays.asList(true, false)), // 测试多个布尔值的 terms 查询，字段 foo 的值为 [true, false]
        hasJson("{\"terms\":{\"foo\":[true,false]}}")); // 期望生成的 JSON 为 {"terms":{"foo":[true,false]}}

    assertThat(termsQuery("foo", Arrays.asList(1, 2, 3)), // 测试多个整数的 terms 查询，字段 foo 的值为 [1, 2, 3]
        hasJson("{\"terms\":{\"foo\":[1,2,3]}}")); // 期望生成的 JSON 为 {"terms":{"foo":[1,2,3]}}

    assertThat(termsQuery("foo", Arrays.asList(1.1, 2.2, 3.3)), // 测试多个浮点数的 terms 查询，字段 foo 的值为 [1.1, 2.2, 3.3]
        hasJson("{\"terms\":{\"foo\":[1.1,2.2,3.3]}}")); // 期望生成的 JSON 为 {"terms":{"foo":[1.1,2.2,3.3]}}
  }

  @Test void bool() {
    assertThat(boolQuery() // 创建一个 bool 查询构建器
            .must(termQuery("foo", "bar")), // 添加一个 must 条件，字段 foo 必须等于 bar
        hasJson("{\"bool\":{\"must\":{\"term\":{\"foo\":\"bar\"}}}}")); // 期望生成的 JSON 为 {"bool":{"must":{"term":{"foo":"bar"}}}}

    assertThat(boolQuery() // 创建一个 bool 查询构建器
            .must(termQuery("f1", "v1")) // 添加第一个 must 条件，字段 f1 必须等于 v1
            .must(termQuery("f2", "v2")), // 添加第二个 must 条件，字段 f2 必须等于 v2
        hasJson("{\"bool\":{\"must\":[{\"term\":{\"f1\":\"v1\"}}," // 期望生成的 JSON 包含 must 数组
            + "{\"term\":{\"f2\":\"v2\"}}]}}")); // 完整的 JSON 为 {"bool":{"must":[{"term":{"f1":"v1"}},{"term":{"f2":"v2"}}]}}

    assertThat(boolQuery() // 创建一个 bool 查询构建器
            .mustNot(termQuery("f1", "v1")), // 添加一个 must_not 条件，字段 f1 不能等于 v1
        hasJson("{\"bool\":{\"must_not\":{\"term\":{\"f1\":\"v1\"}}}}")); // 期望生成的 JSON 为 {"bool":{"must_not":{"term":{"f1":"v1"}}}}
  }

  @Test void exists() {
    assertThat(QueryBuilders.existsQuery("foo"), // 测试 exists 查询，检查字段 foo 是否存在
        hasJson("{\"exists\":{\"field\":\"foo\"}}")); // 期望生成的 JSON 为 {"exists":{"field":"foo"}}
  }

  @Test void range() {
    assertThat(rangeQuery("f").lt(0), // 测试范围查询的 lt 条件，字段 f 必须小于 0
        hasJson("{\"range\":{\"f\":{\"lt\":0}}}")); // 期望生成的 JSON 为 {"range":{"f":{"lt":0}}}
    assertThat(rangeQuery("f").gt(0), // 测试范围查询的 gt 条件，字段 f 必须大于 0
        hasJson("{\"range\":{\"f\":{\"gt\":0}}}")); // 期望生成的 JSON 为 {"range":{"f":{"gt":0}}}
    assertThat(rangeQuery("f").gte(0), // 测试范围查询的 gte 条件，字段 f 必须大于等于 0
        hasJson("{\"range\":{\"f\":{\"gte\":0}}}")); // 期望生成的 JSON 为 {"range":{"f":{"gte":0}}}
    assertThat(rangeQuery("f").lte(0), // 测试范围查询的 lte 条件，字段 f 必须小于等于 0
        hasJson("{\"range\":{\"f\":{\"lte\":0}}}")); // 期望生成的 JSON 为 {"range":{"f":{"lte":0}}}
    assertThat(rangeQuery("f").gt(1).lt(2), // 测试范围查询的 gt 和 lt 组合条件，字段 f 必须大于 1 且小于 2
        hasJson("{\"range\":{\"f\":{\"gt\":1,\"lt\":2}}}")); // 期望生成的 JSON 为 {"range":{"f":{"gt":1,"lt":2}}}
    assertThat(rangeQuery("f").lt(0).gt(11), // 测试范围查询的 lt 和 gt 组合条件，字段 f 必须小于 0 且大于 11（无效范围，但测试 JSON 生成）
        hasJson("{\"range\":{\"f\":{\"gt\":11,\"lt\":0}}}")); // 期望生成的 JSON 为 {"range":{"f":{"gt":11,"lt":0}}}
    assertThat(rangeQuery("f").gt(1).lte(2), // 测试范围查询的 gt 和 lte 组合条件，字段 f 必须大于 1 且小于等于 2
        hasJson("{\"range\":{\"f\":{\"gt\":1,\"lte\":2}}}")); // 期望生成的 JSON 为 {"range":{"f":{"gt":1,"lte":2}}}
    assertThat(rangeQuery("f").gte(1).lte("zz"), // 测试范围查询的 gte 和 lte 组合条件，字段 f 必须大于等于 1 且小于等于 "zz"（混合类型）
        hasJson("{\"range\":{\"f\":{\"gte\":1,\"lte\":\"zz\"}}}")); // 期望生成的 JSON 为 {"range":{"f":{"gte":1,"lte":"zz"}}}
    assertThat(rangeQuery("f").gte(1), // 测试范围查询的 gte 条件，字段 f 必须大于等于 1
        hasJson("{\"range\":{\"f\":{\"gte\":1}}}")); // 期望生成的 JSON 为 {"range":{"f":{"gte":1}}}
    assertThat(rangeQuery("f").gte("zz"), // 测试范围查询的 gte 条件，字段 f 必须大于等于 "zz"（字符串类型）
        hasJson("{\"range\":{\"f\":{\"gte\":\"zz\"}}}")); // 期望生成的 JSON 为 {"range":{"f":{"gte":"zz"}}}
    assertThat(rangeQuery("f").gt("a").lt("z"), // 测试范围查询的 gt 和 lt 组合条件，字段 f 必须大于 "a" 且小于 "z"（字符串类型）
        hasJson("{\"range\":{\"f\":{\"gt\":\"a\",\"lt\":\"z\"}}}")); // 期望生成的 JSON 为 {"range":{"f":{"gt":"a","lt":"z"}}}
    assertThat(rangeQuery("f").gt(1).gt(2).gte(3), // 测试多个 gt 条件，最终取最大值 gte(3)
        hasJson("{\"range\":{\"f\":{\"gte\":3}}}")); // 期望生成的 JSON 为 {"range":{"f":{"gte":3}}}
    assertThat(rangeQuery("f").lt(1).lt(2).lte(3), // 测试多个 lt 条件，最终取最小值 lte(3)
        hasJson("{\"range\":{\"f\":{\"lte\":3}}}")); // 期望生成的 JSON 为 {"range":{"f":{"lte":3}}}
  }

  @Test void matchAll() {
    assertThat(QueryBuilders.matchAll(), // 测试 match_all 查询，匹配所有文档
        hasJson("{\"match_all\":{}}")); // 期望生成的 JSON 为 {"match_all":{}}
  }

  @Test void match() {
    assertThat(matchesQuery("foo", Collections.singleton("bar")), // 测试 match 查询，字段 foo 匹配 "bar"
        hasJson("{\"match\":{\"foo\":[\"bar\"]}}")); // 期望生成的 JSON 为 {"match":{"foo":["bar"]}}

    assertThat(matchesQuery("foo", Collections.singleton(true)), // 测试 match 查询，字段 foo 匹配 true
        hasJson("{\"match\":{\"foo\":[true]}}")); // 期望生成的 JSON 为 {"match":{"foo":[true]}}
  }

  /** Matcher that succeeds if a
   * {@link org.apache.calcite.adapter.elasticsearch.QueryBuilders.QueryBuilder}
   * yields JSON that matches a given string.
   * JsonMatcher 是一个自定义的 Hamcrest 匹配器，用于验证 QueryBuilder 生成的 JSON 字符串是否符合预期
   * 继承自 TypeSafeMatcher<QueryBuilders.QueryBuilder>，确保类型安全
   * 该匹配器将 QueryBuilder 对象序列化为 JSON 字符串，然后与预期的 JSON 字符串进行比较
   */
  static class JsonMatcher extends TypeSafeMatcher<QueryBuilders.QueryBuilder> {
    private final Matcher<String> matcher; // 内部使用的字符串匹配器，用于比较 JSON 字符串

    protected JsonMatcher(Matcher<String> matcher) { // JsonMatcher 构造方法，接收一个字符串匹配器
      super(QueryBuilders.QueryBuilder.class); // 调用父类构造方法，指定泛型类型为 QueryBuilder
      this.matcher = matcher; // 保存传入的字符串匹配器
    }

    static Matcher<QueryBuilders.QueryBuilder> hasJson(String s) { // 静态工厂方法，创建一个 JsonMatcher 实例
      return new JsonMatcher(is(s)); // 使用 is(s) 创建一个精确匹配的字符串匹配器，然后创建 JsonMatcher
    }

    private static String toJson(QueryBuilders.QueryBuilder builder, // 将 QueryBuilder 对象转换为 JSON 字符串的私有方法
        ObjectMapper mapper) { // 参数：builder-要转换的 QueryBuilder 对象，mapper-用于 JSON 序列化的 ObjectMapper
      try {
        StringWriter writer = new StringWriter(); // 创建字符串写入器，用于捕获 JSON 输出
        JsonGenerator gen = mapper.getFactory().createGenerator(writer); // 创建 JSON 生成器，输出到字符串写入器
        builder.writeJson(gen); // 调用 QueryBuilder 的 writeJson 方法，将查询写入 JSON 生成器
        gen.flush(); // 刷新生成器，确保所有内容写入
        gen.close(); // 关闭生成器
        return writer.toString(); // 返回生成的 JSON 字符串
      } catch (IOException e) { // 捕获 IO 异常
        throw new RuntimeException(e); // 将 IO 异常包装为运行时异常抛出
      }
    }

    @Override protected boolean matchesSafely(QueryBuilders.QueryBuilder builder) { // 实现父类的匹配方法
      final String json = toJson(builder, new ObjectMapper()); // 将 QueryBuilder 转换为 JSON 字符串
      return Unsafe.matches(matcher, json); // 使用 Unsafe.matches 方法比较生成的 JSON 是否匹配预期的 JSON
    }

    @Override public void describeTo(Description description) { // 实现描述方法，用于生成匹配失败的错误信息
      description.appendText("json ").appendDescriptionOf(matcher); // 在描述前添加 "json " 前缀，然后追加内部匹配器的描述
    }
  }
}
