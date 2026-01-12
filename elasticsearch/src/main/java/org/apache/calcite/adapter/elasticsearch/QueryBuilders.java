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
package org.apache.calcite.adapter.elasticsearch; // 声明包名，表示这个类属于 org.apache.calcite.adapter.elasticsearch 包

import com.fasterxml.jackson.core.JsonGenerator; // 导入 Jackson 的 JsonGenerator 类，用于生成 JSON 数据

import java.io.IOException; // 导入 IOException 类，用于处理 IO 异常
import java.util.ArrayList; // 导入 ArrayList 类，用于动态数组
import java.util.List; // 导入 List 接口，用于列表操作

import static java.util.Objects.requireNonNull; // 导入 requireNonNull 静态方法，用于空值检查

/**
 * Utility class to generate elastic search queries. Most query builders have
 * been copied from ES distribution. The reason we have separate definition is
 * high-level client dependency on core modules (like lucene, netty, XContent etc.) which
 * is not compatible between different major versions.
 *
 * <p>The goal of ES adapter is to
 * be compatible with any elastic version or even to connect to clusters with different
 * versions simultaneously.
 *
 * <p>Jackson API is used to generate ES query as JSON document.
 */
// 工具类，用于生成 Elasticsearch 查询。大部分查询构建器是从 ES 发行版复制的。
// 我们需要单独定义的原因是：高级客户端依赖核心模块（如 lucene、netty、XContent 等），
// 这些模块在不同主要版本之间不兼容。ES 适配器的目标是与任何 Elasticsearch 版本兼容，
// 甚至同时连接到不同版本的集群。使用 Jackson API 将 ES 查询生成为 JSON 文档。
class QueryBuilders { // 定义 QueryBuilders 类，这是一个工具类，用于构建 Elasticsearch 查询

  private QueryBuilders() {} // 私有构造方法，防止实例化，因为这是一个纯工具类

  /**
   * A Query that matches documents containing a term.
   *
   * @param name  The name of the field
   * @param value The value of the term
   */
  // 创建一个精确匹配查询，匹配包含指定词项的文档，参数为字符串类型
  static TermQueryBuilder termQuery(String name, String value) { // 静态方法，创建 TermQueryBuilder 对象
    return new TermQueryBuilder(name, value); // 返回一个新的 TermQueryBuilder 实例
  }

  /**
   * A Query that matches documents containing a term.
   *
   * @param name  The name of the field
   * @param value The value of the term
   */
  // 创建一个精确匹配查询，匹配包含指定词项的文档，参数为整数类型
  static TermQueryBuilder termQuery(String name, int value) { // 静态方法，创建 TermQueryBuilder 对象
    return new TermQueryBuilder(name, value); // 返回一个新的 TermQueryBuilder 实例
  }

  /**
   * A Query that matches documents containing a single character term.
   *
   * @param name  The name of the field
   * @param value The value of the term
   */
  // 创建一个精确匹配查询，匹配包含单个字符词项的文档，参数为字符类型
  static TermQueryBuilder termQuery(String name, char value) { // 静态方法，创建 TermQueryBuilder 对象
    return new TermQueryBuilder(name, value); // 返回一个新的 TermQueryBuilder 实例
  }

  /**
   * A Query that matches documents containing a term.
   *
   * @param name  The name of the field
   * @param value The value of the term
   */
  // 创建一个精确匹配查询，匹配包含指定词项的文档，参数为长整型
  static TermQueryBuilder termQuery(String name, long value) { // 静态方法，创建 TermQueryBuilder 对象
    return new TermQueryBuilder(name, value); // 返回一个新的 TermQueryBuilder 实例
  }

  /**
   * A Query that matches documents containing a term.
   *
   * @param name  The name of the field
   * @param value The value of the term
   */
  // 创建一个精确匹配查询，匹配包含指定词项的文档，参数为浮点型
  static TermQueryBuilder termQuery(String name, float value) { // 静态方法，创建 TermQueryBuilder 对象
    return new TermQueryBuilder(name, value); // 返回一个新的 TermQueryBuilder 实例
  }

  /**
   * A Query that matches documents containing a term.
   *
   * @param name  The name of the field
   * @param value The value of the term
   */
  // 创建一个精确匹配查询，匹配包含指定词项的文档，参数为双精度浮点型
  static TermQueryBuilder termQuery(String name, double value) { // 静态方法，创建 TermQueryBuilder 对象
    return new TermQueryBuilder(name, value); // 返回一个新的 TermQueryBuilder 实例
  }

  /**
   * A Query that matches documents containing a term.
   *
   * @param name  The name of the field
   * @param value The value of the term
   */
  // 创建一个精确匹配查询，匹配包含指定词项的文档，参数为布尔型
  static TermQueryBuilder termQuery(String name, boolean value) { // 静态方法，创建 TermQueryBuilder 对象
    return new TermQueryBuilder(name, value); // 返回一个新的 TermQueryBuilder 实例
  }

  /**
   * A Query that matches documents containing a term.
   *
   * @param name  The name of the field
   * @param value The value of the term
   */
  // 创建一个精确匹配查询，匹配包含指定词项的文档，参数为对象类型
  static TermQueryBuilder termQuery(String name, Object value) { // 静态方法，创建 TermQueryBuilder 对象
    return new TermQueryBuilder(name, value); // 返回一个新的 TermQueryBuilder 实例
  }

  /**
   * A filer for a field based on several terms matching on any of them.
   *
   * @param name   The field name
   * @param values The terms
   */
  // 创建一个匹配查询，基于多个词项中的任意一个进行匹配（matches 查询）
  static MatchesQueryBuilder matchesQuery(String name, Iterable<?> values) { // 静态方法，创建 MatchesQueryBuilder 对象
    return new MatchesQueryBuilder(name, values); // 返回一个新的 MatchesQueryBuilder 实例
  }

  /**
   * A Query that matches documents containing a term.
   *
   * @param name  The name of the field
   * @param value The value of the term
   */
  // 创建一个匹配查询，匹配包含指定词项的文档（match 查询，会进行分词分析）
  static MatchQueryBuilder matchQuery(String name, Object value) { // 静态方法，创建 MatchQueryBuilder 对象
    return new MatchQueryBuilder(name, value); // 返回一个新的 MatchQueryBuilder 实例
  }

  /**
   * A filer for a field based on several terms matching on any of them.
   *
   * @param name   The field name
   * @param values The terms
   */
  // 创建一个词项查询，基于多个词项中的任意一个进行精确匹配（terms 查询）
  static TermsQueryBuilder termsQuery(String name, Iterable<?> values) { // 静态方法，创建 TermsQueryBuilder 对象
    return new TermsQueryBuilder(name, values); // 返回一个新的 TermsQueryBuilder 实例
  }

  /**
   * A Query that matches documents within an range of terms.
   *
   * @param name The field name
   */
  // 创建一个范围查询，匹配指定字段在指定范围内的文档
  static RangeQueryBuilder rangeQuery(String name) { // 静态方法，创建 RangeQueryBuilder 对象
    return new RangeQueryBuilder(name); // 返回一个新的 RangeQueryBuilder 实例
  }

  /**
   * A Query that matches documents containing terms with a specified regular expression.
   *
   * @param name   The name of the field
   * @param regexp The regular expression
   */
  // 创建一个正则表达式查询，匹配包含符合指定正则表达式词项的文档
  static RegexpQueryBuilder regexpQuery(String name, String regexp) { // 静态方法，创建 RegexpQueryBuilder 对象
    return new RegexpQueryBuilder(name, regexp); // 返回一个新的 RegexpQueryBuilder 实例
  }


  /**
   * A Query that matches documents matching boolean combinations of other queries.
   */
  // 创建一个布尔查询，可以组合多个查询条件（包括 must、must_not、should、filter）
  static BoolQueryBuilder boolQuery() { // 静态方法，创建 BoolQueryBuilder 对象
    return new BoolQueryBuilder(); // 返回一个新的 BoolQueryBuilder 实例
  }

  /**
   * A query that wraps another query and simply returns a constant score equal to the
   * query boost for every document in the query.
   *
   * @param queryBuilder The query to wrap in a constant score query
   */
  // 创建一个常量分数查询，包装另一个查询，为查询中的每个文档返回相同的分数
  static ConstantScoreQueryBuilder constantScoreQuery(QueryBuilder queryBuilder) { // 静态方法，创建 ConstantScoreQueryBuilder 对象
    return new ConstantScoreQueryBuilder(queryBuilder); // 返回一个新的 ConstantScoreQueryBuilder 实例
  }

  /**
   * A query that wraps another query and simply returns a dismax score equal to the
   * query boost for every document in the query.
   *
   * @param queryBuilder The query to wrap in a constant score query
   */
  // 创建一个 DisMax 查询（Disjunction Max），包装另一个查询，返回多个子查询中的最大分数
  static DisMaxQueryBuilder disMaxQueryBuilder(QueryBuilder queryBuilder) { // 静态方法，创建 DisMaxQueryBuilder 对象
    return new DisMaxQueryBuilder(queryBuilder); // 返回一个新的 DisMaxQueryBuilder 实例
  }

  /**
   * A filter to filter only documents where a field exists in them.
   *
   * @param name The name of the field
   */
  // 创建一个存在性查询，只返回指定字段存在的文档
  static ExistsQueryBuilder existsQuery(String name) { // 静态方法，创建 ExistsQueryBuilder 对象
    return new ExistsQueryBuilder(name); // 返回一个新的 ExistsQueryBuilder 实例
  }

  /**
   * A query that matches on all documents.
   */
  // 创建一个匹配所有文档的查询
  static MatchAllQueryBuilder matchAll() { // 静态方法，创建 MatchAllQueryBuilder 对象
    return new MatchAllQueryBuilder(); // 返回一个新的 MatchAllQueryBuilder 实例
  }

  /**
   * Base class to build Elasticsearch queries.
   */
  // 所有查询构建器的抽象基类，用于构建 Elasticsearch 查询
  abstract static class QueryBuilder { // 抽象静态内部类，定义查询构建器的通用接口

    /**
     * Converts an existing query to JSON format using jackson API.
     *
     * @param generator used to generate JSON elements
     * @throws IOException if IO error occurred
     */
    // 将查询转换为 JSON 格式，使用 Jackson API 实现
    abstract void writeJson(JsonGenerator generator) throws IOException; // 抽象方法，子类必须实现，用于将查询写入 JSON 生成器
  } // 结束 QueryBuilder 类定义

  /**
   * Query for boolean logic.
   */
  // 布尔查询构建器，用于组合多个查询条件，实现逻辑运算
  static class BoolQueryBuilder extends QueryBuilder { // 静态内部类，继承自 QueryBuilder
    private final List<QueryBuilder> mustClauses = new ArrayList<>(); // must 子句列表：文档必须匹配这些查询条件
    private final List<QueryBuilder> mustNotClauses = new ArrayList<>(); // must_not 子句列表：文档不能匹配这些查询条件
    private final List<QueryBuilder> filterClauses = new ArrayList<>(); // filter 子句列表：文档必须匹配这些条件（不计入评分）
    private final List<QueryBuilder> shouldClauses = new ArrayList<>(); // should 子句列表：文档应该匹配这些条件（如果没有 must 子句，则至少匹配一个）

    BoolQueryBuilder must(QueryBuilder queryBuilder) { // 添加 must 条件：文档必须满足此查询
      requireNonNull(queryBuilder, "queryBuilder"); // 检查参数是否为空
      mustClauses.add(queryBuilder); // 将查询构建器添加到 must 子句列表
      return this; // 返回当前对象，支持链式调用
    }

    BoolQueryBuilder filter(QueryBuilder queryBuilder) { // 添加 filter 条件：文档必须满足此条件（不影响评分）
      requireNonNull(queryBuilder, "queryBuilder"); // 检查参数是否为空
      filterClauses.add(queryBuilder); // 将查询构建器添加到 filter 子句列表
      return this; // 返回当前对象，支持链式调用
    }

    BoolQueryBuilder mustNot(QueryBuilder queryBuilder) { // 添加 must_not 条件：文档必须不满足此查询
      requireNonNull(queryBuilder, "queryBuilder"); // 检查参数是否为空
      mustNotClauses.add(queryBuilder); // 将查询构建器添加到 must_not 子句列表
      return this; // 返回当前对象，支持链式调用
    }

    BoolQueryBuilder should(QueryBuilder queryBuilder) { // 添加 should 条件：文档应该满足此查询
      requireNonNull(queryBuilder, "queryBuilder"); // 检查参数是否为空
      shouldClauses.add(queryBuilder); // 将查询构建器添加到 should 子句列表
      return this; // 返回当前对象，支持链式调用
    }

    @Override protected void writeJson(JsonGenerator gen) throws IOException { // 重写 writeJson 方法，将布尔查询写入 JSON
      gen.writeStartObject(); // 开始写入 JSON 对象
      gen.writeFieldName("bool"); // 写入字段名 "bool"
      gen.writeStartObject(); // 开始写入 bool 对象
      writeJsonArray("must", mustClauses, gen); // 写入 must 子句数组
      writeJsonArray("filter", filterClauses, gen); // 写入 filter 子句数组
      writeJsonArray("must_not", mustNotClauses, gen); // 写入 must_not 子句数组
      writeJsonArray("should", shouldClauses, gen); // 写入 should 子句数组
      gen.writeEndObject(); // 结束 bool 对象
      gen.writeEndObject(); // 结束外层对象
    }

    private static void writeJsonArray(String field, List<QueryBuilder> clauses, JsonGenerator gen) // 私有静态方法，将查询子句写入 JSON 数组
        throws IOException { // 声明可能抛出 IOException
      if (clauses.isEmpty()) { // 如果子句列表为空
        return; // 直接返回，不写入任何内容
      }

      if (clauses.size() == 1) { // 如果子句列表只有一个元素
        gen.writeFieldName(field); // 写入字段名
        clauses.get(0).writeJson(gen); // 直接写入单个查询对象
      } else { // 如果子句列表有多个元素
        gen.writeArrayFieldStart(field); // 开始写入数组字段
        for (QueryBuilder clause : clauses) { // 遍历所有子句
          clause.writeJson(gen); // 写入每个查询对象
        }
        gen.writeEndArray(); // 结束数组
      }
    }
  } // 结束 BoolQueryBuilder 类定义

  /**
   * A Query that matches documents containing a term.
   */
  // 词项查询构建器，用于精确匹配指定字段的值（不进行分词分析）
  static class TermQueryBuilder extends QueryBuilder { // 静态内部类，继承自 QueryBuilder
    private final String fieldName; // 字段名，表示要查询的字段
    private final Object value; // 字段值，表示要匹配的值

    private TermQueryBuilder(final String fieldName, final Object value) { // 私有构造方法，创建 TermQueryBuilder 实例
      this.fieldName = requireNonNull(fieldName, "fieldName"); // 初始化字段名，检查是否为空
      this.value = requireNonNull(value, "value"); // 初始化字段值，检查是否为空
    }

    @Override void writeJson(final JsonGenerator generator) throws IOException { // 重写 writeJson 方法，将词项查询写入 JSON
      generator.writeStartObject(); // 开始写入 JSON 对象
      generator.writeFieldName("term"); // 写入字段名 "term"
      generator.writeStartObject(); // 开始写入 term 对象
      generator.writeFieldName(fieldName); // 写入字段名
      writeObject(generator, value); // 写入字段值
      generator.writeEndObject(); // 结束 term 对象
      generator.writeEndObject(); // 结束外层对象
    }
  } // 结束 TermQueryBuilder 类定义

  /**
   * A filter for a field based on several terms matching on any of them.
   */
  // 词项列表查询构建器，用于匹配字段值在给定列表中的文档（精确匹配多个值中的任意一个）
  private static class TermsQueryBuilder extends QueryBuilder { // 私有静态内部类，继承自 QueryBuilder
    private final String fieldName; // 字段名，表示要查询的字段
    private final Iterable<?> values; // 字段值列表，表示要匹配的多个值

    private TermsQueryBuilder(final String fieldName, final Iterable<?> values) { // 私有构造方法，创建 TermsQueryBuilder 实例
      this.fieldName = requireNonNull(fieldName, "fieldName"); // 初始化字段名，检查是否为空
      this.values = requireNonNull(values, "values"); // 初始化值列表，检查是否为空
    }

    @Override void writeJson(final JsonGenerator generator) throws IOException { // 重写 writeJson 方法，将词项列表查询写入 JSON
      generator.writeStartObject(); // 开始写入 JSON 对象
      generator.writeFieldName("terms"); // 写入字段名 "terms"
      generator.writeStartObject(); // 开始写入 terms 对象
      generator.writeFieldName(fieldName); // 写入字段名
      generator.writeStartArray(); // 开始写入数组
      for (Object value : values) { // 遍历所有值
        writeObject(generator, value); // 写入每个值
      }
      generator.writeEndArray(); // 结束数组
      generator.writeEndObject(); // 结束 terms 对象
      generator.writeEndObject(); // 结束外层对象
    }
  } // 结束 TermsQueryBuilder 类定义



  /**
   * A Query that matches documents containing a term.
   */
  // 匹配查询构建器，用于全文搜索匹配（会对查询词进行分词分析）
  static class MatchQueryBuilder extends QueryBuilder { // 静态内部类，继承自 QueryBuilder
    private final String fieldName; // 字段名，表示要查询的字段
    private final Object value; // 字段值，表示要匹配的值

    private MatchQueryBuilder(final String fieldName, final Object value) { // 私有构造方法，创建 MatchQueryBuilder 实例
      this.fieldName = requireNonNull(fieldName, "fieldName"); // 初始化字段名，检查是否为空
      this.value = requireNonNull(value, "value"); // 初始化字段值，检查是否为空
    }

    @Override void writeJson(final JsonGenerator generator) throws IOException { // 重写 writeJson 方法，将匹配查询写入 JSON
      generator.writeStartObject(); // 开始写入 JSON 对象
      generator.writeFieldName("match"); // 写入字段名 "match"
      generator.writeStartObject(); // 开始写入 match 对象
      generator.writeFieldName(fieldName); // 写入字段名
      writeObject(generator, value); // 写入字段值
      generator.writeEndObject(); // 结束 match 对象
      generator.writeEndObject(); // 结束外层对象
    }
  } // 结束 MatchQueryBuilder 类定义


  /**
   * A filter for a field based on several terms matching on any of them.
   */
  // 匹配列表查询构建器，用于全文搜索匹配多个值中的任意一个（会对查询词进行分词分析）
  private static class MatchesQueryBuilder extends QueryBuilder { // 私有静态内部类，继承自 QueryBuilder
    private final String fieldName; // 字段名，表示要查询的字段
    private final Iterable<?> values; // 字段值列表，表示要匹配的多个值

    private MatchesQueryBuilder(final String fieldName, final Iterable<?> values) { // 私有构造方法，创建 MatchesQueryBuilder 实例
      this.fieldName = requireNonNull(fieldName, "fieldName"); // 初始化字段名，检查是否为空
      this.values = requireNonNull(values, "values"); // 初始化值列表，检查是否为空
    }

    @Override void writeJson(final JsonGenerator generator) throws IOException { // 重写 writeJson 方法，将匹配列表查询写入 JSON
      generator.writeStartObject(); // 开始写入 JSON 对象
      generator.writeFieldName("match"); // 写入字段名 "match"
      generator.writeStartObject(); // 开始写入 match 对象
      generator.writeFieldName(fieldName); // 写入字段名
      generator.writeStartArray(); // 开始写入数组
      for (Object value : values) { // 遍历所有值
        writeObject(generator, value); // 写入每个值
      }
      generator.writeEndArray(); // 结束数组
      generator.writeEndObject(); // 结束 match 对象
      generator.writeEndObject(); // 结束外层对象
    }
  } // 结束 MatchesQueryBuilder 类定义

  /**
   * Write usually simple (scalar) value (string, number, boolean or null) to json output.
   * In case of complex objects delegates to jackson serialization.
   *
   * @param generator api to generate JSON document
   * @param value JSON value to write
   * @throws IOException if can't write to output
   */
  // 将简单标量值（字符串、数字、布尔或 null）写入 JSON 输出，对于复杂对象委托给 Jackson 序列化
  private static void writeObject(JsonGenerator generator, Object value) throws IOException { // 私有静态方法，用于写入对象到 JSON
    generator.writeObject(value); // 使用 Jackson 的 writeObject 方法写入值
  } // 结束 writeObject 方法定义

  /**
   * A Query that matches documents within an range of terms.
   */
  // 范围查询构建器，用于匹配字段值在指定范围内的文档
  static class RangeQueryBuilder extends QueryBuilder { // 静态内部类，继承自 QueryBuilder
    private final String fieldName; // 字段名，表示要查询的字段

    private Object lt; // 上界值（小于，less than）
    private boolean lte; // 是否包含上界（小于等于，less than or equal）
    private Object gt; // 下界值（大于，greater than）
    private boolean gte; // 是否包含下界（大于等于，greater than or equal）

    private String format; // 日期格式，用于日期类型的范围查询

    private RangeQueryBuilder(final String fieldName) { // 私有构造方法，创建 RangeQueryBuilder 实例
      this.fieldName = requireNonNull(fieldName, "fieldName"); // 初始化字段名，检查是否为空
    }

    private RangeQueryBuilder to(Object value, boolean lte) { // 私有方法，设置上界值
      this.lt = requireNonNull(value, "value"); // 设置上界值，检查是否为空
      this.lte = lte; // 设置是否包含上界
      return this; // 返回当前对象，支持链式调用
    }

    private RangeQueryBuilder from(Object value, boolean gte) { // 私有方法，设置下界值
      this.gt = requireNonNull(value, "value"); // 设置下界值，检查是否为空
      this.gte = gte; // 设置是否包含下界
      return this; // 返回当前对象，支持链式调用
    }

    RangeQueryBuilder lt(Object value) { // 设置上界为小于（不包含）
      return to(value, false); // 调用 to 方法，设置 lte 为 false
    }

    RangeQueryBuilder lte(Object value) { // 设置上界为小于等于（包含）
      return to(value, true); // 调用 to 方法，设置 lte 为 true
    }

    RangeQueryBuilder gt(Object value) { // 设置下界为大于（不包含）
      return from(value, false); // 调用 from 方法，设置 gte 为 false
    }

    RangeQueryBuilder gte(Object value) { // 设置下界为大于等于（包含）
      return from(value, true); // 调用 from 方法，设置 gte 为 true
    }

    RangeQueryBuilder format(String format) { // 设置日期格式
      this.format = format; // 设置格式字符串
      return this; // 返回当前对象，支持链式调用
    }

    @Override void writeJson(final JsonGenerator generator) throws IOException { // 重写 writeJson 方法，将范围查询写入 JSON
      if (lt == null && gt == null) { // 如果上下界都未设置
        throw new IllegalStateException("Either lower or upper bound should be provided"); // 抛出异常，要求至少设置一个边界
      }

      generator.writeStartObject(); // 开始写入 JSON 对象
      generator.writeFieldName("range"); // 写入字段名 "range"
      generator.writeStartObject(); // 开始写入 range 对象
      generator.writeFieldName(fieldName); // 写入字段名
      generator.writeStartObject(); // 开始写入字段对象

      if (gt != null) { // 如果设置了下界
        final String op = gte ? "gte" : "gt"; // 根据是否包含下界确定操作符
        generator.writeFieldName(op); // 写入操作符字段名
        writeObject(generator, gt); // 写入下界值
      }

      if (lt != null) { // 如果设置了上界
        final String op = lte ? "lte" : "lt"; // 根据是否包含上界确定操作符
        generator.writeFieldName(op); // 写入操作符字段名
        writeObject(generator, lt); // 写入上界值
      }

      if (format != null) { // 如果设置了日期格式
        generator.writeStringField("format", format); // 写入格式字符串
      }

      generator.writeEndObject(); // 结束字段对象
      generator.writeEndObject(); // 结束 range 对象
      generator.writeEndObject(); // 结束外层对象
    }
  } // 结束 RangeQueryBuilder 类定义

  /**
   * A Query that does fuzzy matching for a specific value.
   */
  // 正则表达式查询构建器，用于匹配符合指定正则表达式的值（当前实现未完成）
  static class RegexpQueryBuilder extends QueryBuilder { // 静态内部类，继承自 QueryBuilder
    @SuppressWarnings("unused") // 抑制未使用警告
    private final String fieldName; // 字段名，表示要查询的字段
    @SuppressWarnings("unused") // 抑制未使用警告
    private final String value; // 正则表达式值

    RegexpQueryBuilder(final String fieldName, final String value) { // 构造方法，创建 RegexpQueryBuilder 实例
      this.fieldName = fieldName; // 初始化字段名
      this.value = value; // 初始化正则表达式值
    }

    @Override void writeJson(final JsonGenerator generator) { // 重写 writeJson 方法
      throw new UnsupportedOperationException(); // 抛出不支持操作异常，表示此功能尚未实现
    }
  } // 结束 RegexpQueryBuilder 类定义

  /**
   * Constructs a query that only match on documents that the field has a value in them.
   */
  // 存在性查询构建器，用于匹配指定字段存在的文档（字段有值）
  static class ExistsQueryBuilder extends QueryBuilder { // 静态内部类，继承自 QueryBuilder
    private final String fieldName; // 字段名，表示要检查的字段

    ExistsQueryBuilder(final String fieldName) { // 构造方法，创建 ExistsQueryBuilder 实例
      this.fieldName = requireNonNull(fieldName, "fieldName"); // 初始化字段名，检查是否为空
    }

    @Override void writeJson(final JsonGenerator generator) throws IOException { // 重写 writeJson 方法，将存在性查询写入 JSON
      generator.writeStartObject(); // 开始写入 JSON 对象
      generator.writeFieldName("exists"); // 写入字段名 "exists"
      generator.writeStartObject(); // 开始写入 exists 对象
      generator.writeStringField("field", fieldName); // 写入 field 字段，值为字段名
      generator.writeEndObject(); // 结束 exists 对象
      generator.writeEndObject(); // 结束外层对象
    }
  } // 结束 ExistsQueryBuilder 类定义

  /**
   * A query that wraps a filter and simply returns a constant score equal to the
   * query boost for every document in the filter.
   */
  // 常量分数查询构建器，包装另一个查询，为查询中的每个文档返回相同的分数（忽略原始评分）
  static class ConstantScoreQueryBuilder extends QueryBuilder { // 静态内部类，继承自 QueryBuilder

    private final QueryBuilder builder; // 内部查询构建器，表示要包装的查询

    private ConstantScoreQueryBuilder(final QueryBuilder builder) { // 私有构造方法，创建 ConstantScoreQueryBuilder 实例
      this.builder = requireNonNull(builder, "builder"); // 初始化内部查询构建器，检查是否为空
    }

    @Override void writeJson(final JsonGenerator generator) throws IOException { // 重写 writeJson 方法，将常量分数查询写入 JSON
      generator.writeStartObject(); // 开始写入 JSON 对象
      generator.writeFieldName("constant_score"); // 写入字段名 "constant_score"
      generator.writeStartObject(); // 开始写入 constant_score 对象
      generator.writeFieldName("filter"); // 写入 filter 字段名
      builder.writeJson(generator); // 写入内部查询
      generator.writeEndObject(); // 结束 constant_score 对象
      generator.writeEndObject(); // 结束外层对象
    }
  } // 结束 ConstantScoreQueryBuilder 类定义

  /**
   * A query that wraps a filter and simply returns a dismax score equal to the
   * query boost for every document in the filter.
   */
  // DisMax 查询构建器（Disjunction Max），包装另一个查询，返回多个子查询中的最大分数
  // DisMax 用于在多个字段中搜索最佳匹配，而不是将所有分数相加
  static class DisMaxQueryBuilder extends QueryBuilder { // 静态内部类，继承自 QueryBuilder

    private final QueryBuilder builder; // 内部查询构建器，表示要包装的查询

    private DisMaxQueryBuilder(final QueryBuilder builder) { // 私有构造方法，创建 DisMaxQueryBuilder 实例
      this.builder = requireNonNull(builder, "builder"); // 初始化内部查询构建器，检查是否为空
    }

    @Override void writeJson(final JsonGenerator generator) throws IOException { // 重写 writeJson 方法，将 DisMax 查询写入 JSON
      generator.writeStartObject(); // 开始写入 JSON 对象
      generator.writeFieldName("dis_max"); // 写入字段名 "dis_max"
      generator.writeStartObject(); // 开始写入 dis_max 对象
      generator.writeFieldName("queries"); // 写入 queries 字段名
      generator.writeStartArray(); // 开始写入数组
      builder.writeJson(generator); // 写入内部查询
      generator.writeEndArray(); // 结束数组
      generator.writeEndObject(); // 结束 dis_max 对象
      generator.writeEndObject(); // 结束外层对象
    }
  } // 结束 DisMaxQueryBuilder 类定义



  /**
   * A query that matches on all documents.
   * <pre>
   *   {
   *     "match_all": {}
   *   }
   * </pre>
   */
  // 匹配所有文档的查询构建器，返回索引中的所有文档
  // 生成的 JSON 格式示例: {"match_all": {}}
  static class MatchAllQueryBuilder extends QueryBuilder { // 静态内部类，继承自 QueryBuilder

    private MatchAllQueryBuilder() {} // 私有构造方法，防止外部直接实例化

    @Override void writeJson(final JsonGenerator generator) throws IOException { // 重写 writeJson 方法，将匹配所有查询写入 JSON
      generator.writeStartObject(); // 开始写入 JSON 对象
      generator.writeFieldName("match_all"); // 写入字段名 "match_all"
      generator.writeStartObject(); // 开始写入 match_all 对象（空对象）
      generator.writeEndObject(); // 结束 match_all 对象
      generator.writeEndObject(); // 结束外层对象
    }
  } // 结束 MatchAllQueryBuilder 类定义
} // 结束 QueryBuilders 类定义
