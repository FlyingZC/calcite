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
package org.apache.calcite.plan;

import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.avatica.util.ByteString;
import org.apache.calcite.avatica.util.TimeUnit;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.prepare.Prepare;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelDistribution;
import org.apache.calcite.rel.RelDistributionTraitDef;
import org.apache.calcite.rel.RelDistributions;
import org.apache.calcite.rel.RelInput;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelShuttleImpl;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.core.TableModify;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.externalize.RelJson;
import org.apache.calcite.rel.externalize.RelJsonReader;
import org.apache.calcite.rel.externalize.RelJsonWriter;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalCalc;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.logical.LogicalTableModify;
import org.apache.calcite.rel.logical.LogicalTableScan;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexCorrelVariable;
import org.apache.calcite.rex.RexDynamicParam;
import org.apache.calcite.rex.RexFieldCollation;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexProgramBuilder;
import org.apache.calcite.rex.RexWindowBounds;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlExplainFormat;
import org.apache.calcite.sql.SqlExplainLevel;
import org.apache.calcite.sql.SqlIntervalQualifier;
import org.apache.calcite.sql.fun.SqlLibraryOperators;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.fun.SqlTrimFunction;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.test.MockSqlOperatorTable;
import org.apache.calcite.test.RelBuilderTest;
import org.apache.calcite.test.schemata.hr.HrSchema;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.DateString;
import org.apache.calcite.util.Holder;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.JsonBuilder;
import org.apache.calcite.util.NlsString;
import org.apache.calcite.util.TestUtil;
import org.apache.calcite.util.TimeString;
import org.apache.calcite.util.TimestampString;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.apache.calcite.test.Matchers.isLinux;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static java.util.Objects.requireNonNull;

/**
 * Unit test for {@link org.apache.calcite.rel.externalize.RelJson}.
 * // RelJson的单元测试类，用于测试关系表达式树的JSON序列化和反序列化功能
 * // 这个类主要测试：
 * // 1. RelNode（关系表达式节点）的JSON序列化和反序列化
 * // 2. RexNode（行表达式节点）的JSON序列化和反序列化
 * // 3. RelDataType（关系数据类型）的JSON序列化和反序列化
 * // 4. 各种特殊操作符和类型的序列化支持（如窗口函数、聚合函数、时间戳、UUID等）
 * // 5. 不同格式的输出（TEXT、DOT、JSON）
 * // 6. 表修改操作（INSERT、UPDATE、DELETE、MERGE）的序列化
 * // 7. 分布式排序交换操作的序列化
 */
@SuppressWarnings("ConcatenationWithEmptyString")
class RelWriterTest {
  public static final String XX = "{\n"
      + "  \"rels\": [\n"
      + "    {\n"
      + "      \"id\": \"0\",\n"
      + "      \"relOp\": \"LogicalTableScan\",\n"
      + "      \"table\": [\n"
      + "        \"hr\",\n"
      + "        \"emps\"\n"
      + "      ],\n"
      + "      \"inputs\": []\n"
      + "    },\n"
      + "    {\n"
      + "      \"id\": \"1\",\n"
      + "      \"relOp\": \"LogicalFilter\",\n"
      + "      \"condition\": {\n"
      + "        \"op\": {\n"
      + "          \"name\": \"=\",\n"
      + "          \"kind\": \"EQUALS\",\n"
      + "          \"syntax\": \"BINARY\"\n"
      + "        },\n"
      + "        \"operands\": [\n"
      + "          {\n"
      + "            \"input\": 1,\n"
      + "            \"name\": \"$1\"\n"
      + "          },\n"
      + "          {\n"
      + "            \"literal\": 10,\n"
      + "            \"type\": {\n"
      + "              \"type\": \"INTEGER\",\n"
      + "              \"nullable\": false\n"
      + "            }\n"
      + "          }\n"
      + "        ]\n"
      + "      }\n"
      + "    },\n"
      + "    {\n"
      + "      \"id\": \"2\",\n"
      + "      \"relOp\": \"LogicalAggregate\",\n"
      + "      \"group\": [\n"
      + "        0\n"
      + "      ],\n"
      + "      \"aggs\": [\n"
      + "        {\n"
      + "          \"agg\": {\n"
      + "            \"name\": \"COUNT\",\n"
      + "            \"kind\": \"COUNT\",\n"
      + "            \"syntax\": \"FUNCTION_STAR\"\n"
      + "          },\n"
      + "          \"type\": {\n"
      + "            \"type\": \"BIGINT\",\n"
      + "            \"nullable\": false\n"
      + "          },\n"
      + "          \"distinct\": true,\n"
      + "          \"operands\": [\n"
      + "            1\n"
      + "          ],\n"
      + "          \"name\": \"c\"\n"
      + "        },\n"
      + "        {\n"
      + "          \"agg\": {\n"
      + "            \"name\": \"COUNT\",\n"
      + "            \"kind\": \"COUNT\",\n"
      + "            \"syntax\": \"FUNCTION_STAR\"\n"
      + "          },\n"
      + "          \"type\": {\n"
      + "            \"type\": \"BIGINT\",\n"
      + "            \"nullable\": false\n"
      + "          },\n"
      + "          \"distinct\": false,\n"
      + "          \"operands\": [],\n"
      + "          \"name\": \"d\"\n"
      + "        }\n"
      + "      ]\n"
      + "    }\n"
      + "  ]\n"
      + "}";  // XX常量：包含一个简单关系表达式树的JSON字符串，用于测试序列化和反序列化
         // 包含三个节点：LogicalTableScan（表扫描）、LogicalFilter（过滤）、LogicalAggregate（聚合）
         // 聚合包含两个COUNT函数：一个DISTINCT COUNT和一个普通COUNT

  public static final String XXNULL = "{\n"
      + "  \"rels\": [\n"
      + "    {\n"
      + "      \"id\": \"0\",\n"
      + "      \"relOp\": \"LogicalTableScan\",\n"
      + "      \"table\": [\n"
      + "        \"hr\",\n"
      + "        \"emps\"\n"
      + "      ],\n"
      + "      \"inputs\": []\n"
      + "    },\n"
      + "    {\n"
      + "      \"id\": \"1\",\n"
      + "      \"relOp\": \"LogicalFilter\",\n"
      + "      \"condition\": {\n"
      + "        \"op\": {"  // XXNULL常量：包含NULL字面量的关系表达式树JSON字符串，用于测试NULL值的序列化和反序列化
      + "            \"name\": \"=\",\n"
      + "            \"kind\": \"EQUALS\",\n"
      + "            \"syntax\": \"BINARY\"\n"
      + "          },\n"
      + "        \"operands\": [\n"
      + "          {\n"
      + "            \"input\": 1,\n"
      + "            \"name\": \"$1\"\n"
      + "          },\n"
      + "          {\n"
      + "            \"literal\": null,\n"
      + "            \"type\": \"INTEGER\"\n"
      + "          }\n"
      + "        ]\n"
      + "      }\n"
      + "    },\n"
      + "    {\n"
      + "      \"id\": \"2\",\n"
      + "      \"relOp\": \"LogicalAggregate\",\n"
      + "      \"group\": [\n"
      + "        0\n"
      + "      ],\n"
      + "      \"aggs\": [\n"
      + "        {\n"
      + "        \"agg\": {\n"
      + "            \"name\": \"COUNT\",\n"
      + "            \"kind\": \"COUNT\",\n"
      + "            \"syntax\": \"FUNCTION_STAR\"\n"
      + "          },\n"
      + "          \"type\": {\n"
      + "            \"type\": \"BIGINT\",\n"
      + "            \"nullable\": false\n"
      + "          },\n"
      + "          \"distinct\": true,\n"
      + "          \"operands\": [\n"
      + "            1\n"
      + "          ]\n"
      + "        },\n"
      + "        {\n"
      + "        \"agg\": {\n"
      + "            \"name\": \"COUNT\",\n"
      + "            \"kind\": \"COUNT\",\n"
      + "            \"syntax\": \"FUNCTION_STAR\"\n"
      + "          },\n"
      + "          \"type\": {\n"
      + "            \"type\": \"BIGINT\",\n"
      + "            \"nullable\": false\n"
      + "          },\n"
      + "          \"distinct\": false,\n"
      + "          \"operands\": []\n"
      + "        }\n"
      + "      ]\n"
      + "    }\n"
      + "  ]\n"
      + "}";

  public static final String XX2 = "{\n"
      + "  \"rels\": [\n"
      + "    {\n"
      + "      \"id\": \"0\",\n"
      + "      \"relOp\": \"LogicalTableScan\",\n"
      + "      \"table\": [\n"
      + "        \"hr\",\n"
      + "        \"emps\"\n"
      + "      ],\n"
      + "      \"inputs\": []\n"
      + "    },\n"
      + "    {\n"
      + "      \"id\": \"1\",\n"
      + "      \"relOp\": \"LogicalProject\",\n"
      + "      \"fields\": [\n"
      + "        \"field0\",\n"
      + "        \"field1\",\n"
      + "        \"field2\"\n"
      + "      ],\n"
      + "      \"exprs\": [\n"
      + "        {\n"
      + "          \"input\": 0,\n"
      + "          \"name\": \"$0\"\n"
      + "        },\n"
      + "        {\n"
      + "          \"op\": {\n"
      + "            \"name\": \"COUNT\",\n"
      + "            \"kind\": \"COUNT\",\n"
      + "            \"syntax\": \"FUNCTION_STAR\"\n"
      + "          },\n"
      + "          \"operands\": [\n"
      + "            {\n"
      + "              \"input\": 0,\n"
      + "              \"name\": \"$0\"\n"
      + "            }\n"
      + "          ],\n"
      + "          \"distinct\": false,\n"
      + "          \"type\": {\n"
      + "            \"type\": \"BIGINT\",\n"
      + "            \"nullable\": false\n"
      + "          },\n"
      + "          \"window\": {\n"
      + "            \"partition\": [\n"
      + "              {\n"
      + "                \"input\": 2,\n"
      + "                \"name\": \"$2\"\n"
      + "              }\n"
      + "            ],\n"
      + "            \"order\": [\n"
      + "              {\n"
      + "                \"expr\": {\n"
      + "                  \"input\": 1,\n"
      + "                  \"name\": \"$1\"\n"
      + "                },\n"
      + "                \"direction\": \"ASCENDING\",\n"
      + "                \"null-direction\": \"LAST\"\n"
      + "              }\n"
      + "            ],\n"
      + "            \"rows-lower\": {\n"
      + "              \"type\": \"UNBOUNDED_PRECEDING\"\n"
      + "            },\n"
      + "            \"rows-upper\": {\n"
      + "              \"type\": \"CURRENT_ROW\"\n"
      + "            }\n"
      + "          }\n"
      + "        },\n"
      + "        {\n"
      + "          \"op\": {\n"
      + "            \"name\": \"SUM\",\n"
      + "            \"kind\": \"SUM\",\n"
      + "            \"syntax\": \"FUNCTION\"\n"
      + "          },\n"
      + "          \"operands\": [\n"
      + "            {\n"
      + "              \"input\": 0,\n"
      + "              \"name\": \"$0\"\n"
      + "            }\n"
      + "          ],\n"
      + "          \"distinct\": false,\n"
      + "          \"type\": {\n"
      + "            \"type\": \"BIGINT\",\n"
      + "            \"nullable\": false\n"
      + "          },\n"
      + "          \"window\": {\n"
      + "            \"partition\": [\n"
      + "              {\n"
      + "                \"input\": 2,\n"
      + "                \"name\": \"$2\"\n"
      + "              }\n"
      + "            ],\n"
      + "            \"order\": [\n"
      + "              {\n"
      + "                \"expr\": {\n"
      + "                  \"input\": 1,\n"
      + "                  \"name\": \"$1\"\n"
      + "                },\n"
      + "                \"direction\": \"ASCENDING\",\n"
      + "                \"null-direction\": \"LAST\"\n"
      + "              }\n"
      + "            ],\n"
      + "            \"range-lower\": {\n"
      + "              \"type\": \"CURRENT_ROW\"\n"
      + "            },\n"
      + "            \"range-upper\": {\n"
      + "              \"type\": \"FOLLOWING\",\n"
      + "              \"offset\": {\n"
      + "                \"literal\": 1,\n"
      + "                \"type\": {\n"
      + "                  \"type\": \"INTEGER\",\n"
      + "                  \"nullable\": false\n"
      + "                }\n"
      + "              }\n"
      + "            }\n"
      + "          }\n"
      + "        }\n"
      + "      ]\n"
      + "    }\n"
      + "  ]\n"
      + "}";  // XX2常量：包含窗口函数（COUNT OVER和SUM OVER）的JSON字符串，用于测试窗口函数的序列化
         // 包含两个窗口函数：一个ROWS窗口和一个RANGE窗口

  public static final String XX3 = "{\n"
      + "  \"rels\": [\n"
      + "    {\n"
      + "      \"id\": \"0\",\n"
      + "      \"relOp\": \"LogicalTableScan\",\n"
      + "      \"table\": [\n"
      + "        \"scott\",\n"
      + "        \"EMP\"\n"
      + "      ],\n"
      + "      \"inputs\": []\n"
      + "    },\n"
      + "    {\n"
      + "      \"id\": \"1\",\n"
      + "      \"relOp\": \"LogicalSortExchange\",\n"
      + "      \"distribution\": {\n"
      + "        \"type\": \"HASH_DISTRIBUTED\",\n"
      + "        \"keys\": [\n"
      + "          0\n"
      + "        ]\n"
      + "      },\n"
      + "      \"collation\": [\n"
      + "        {\n"
      + "          \"field\": 0,\n"
      + "          \"direction\": \"ASCENDING\",\n"
      + "          \"nulls\": \"LAST\"\n"
      + "        }\n"
      + "      ]\n"
      + "    }\n"
      + "  ]\n"
      + "}";  // XX3常量：包含排序交换（LogicalSortExchange）的JSON字符串，用于测试分布式排序操作的序列化
         // 包含哈希分布和排序属性

  public static final String HASH_DIST_WITHOUT_KEYS = "{\n"
      + "  \"rels\": [\n"
      + "    {\n"
      + "      \"id\": \"0\",\n"
      + "      \"relOp\": \"LogicalTableScan\",\n"
      + "      \"table\": [\n"
      + "        \"scott\",\n"
      + "        \"EMP\"\n"
      + "      ],\n"
      + "      \"inputs\": []\n"
      + "    },\n"
      + "    {\n"
      + "      \"id\": \"1\",\n"
      + "      \"relOp\": \"LogicalSortExchange\",\n"
      + "      \"distribution\": {\n"
      + "        \"type\": \"HASH_DISTRIBUTED\"\n"
      + "      },\n"
      + "      \"collation\": [\n"
      + "        {\n"
      + "          \"field\": 0,\n"
      + "          \"direction\": \"ASCENDING\",\n"
      + "          \"nulls\": \"LAST\"\n"
      + "        }\n"
      + "      ]\n"
      + "    }\n"
      + "  ]\n"
      + "}";  // HASH_DIST_WITHOUT_KEYS常量：包含没有键的哈希分布的JSON字符串，用于测试无键哈希分布的序列化

  static Stream<SqlExplainFormat> explainFormats() {  // explainFormats方法：提供参数化测试的格式数据源，返回TEXT和DOT两种格式
    return Stream.of(SqlExplainFormat.TEXT, SqlExplainFormat.DOT);
  }

  /** Creates a fixture. */
  private static Fixture relFn(Function<RelBuilder, RelNode> relFn) {  // relFn方法：创建一个测试夹具（Fixture），用于构建和测试关系表达式
    return new Fixture(relFn, false, SqlExplainFormat.TEXT);
  }

  /** Unit test for {@link RelJson#toJson(Object)} for an object of type
   * {@link RelDataType}. */
  @Test void testTypeJson() {  // testTypeJson方法：测试RelDataType（关系数据类型）的JSON序列化和反序列化
    int i = Frameworks.withPlanner((cluster, relOptSchema, rootSchema) -> {  // 使用框架创建规划器并执行测试逻辑
      final RelDataTypeFactory typeFactory = cluster.getTypeFactory();  // 获取类型工厂，用于创建SQL类型
      final RelDataType type = typeFactory.builder()  // 构建一个复杂的关系数据类型
          .add("i", typeFactory.createSqlType(SqlTypeName.INTEGER))  // 添加INTEGER类型的字段"i"
          .nullable(false)  // 设置该字段不可为空
          .add("v", typeFactory.createSqlType(SqlTypeName.VARCHAR, 9))  // 添加VARCHAR(9)类型的字段"v"
          .nullable(true)  // 设置该字段可为空
          .add("r", typeFactory.builder()  // 添加嵌套的记录类型字段"r"
              .add("d", typeFactory.createSqlType(SqlTypeName.DATE))  // 嵌套记录中包含DATE类型的字段"d"
              .nullable(false)  // 嵌套字段不可为空
              .build())
          .nullableRecord(false)  // 整个记录类型不可为空
          .build();
      final JsonBuilder jsonBuilder = new JsonBuilder();  // 创建JSON构建器
      final RelJson json = RelJson.create().withJsonBuilder(jsonBuilder);  // 创建RelJson实例并关联JSON构建器
      final Object o = json.toJson(type);  // 将RelDataType对象序列化为JSON对象
      assertThat(o, notNullValue());  // 断言JSON对象不为空
      final String s = jsonBuilder.toJsonString(o);  // 将JSON对象转换为字符串
      final String expectedJson = "{\n"  // 期望的JSON字符串
          + "  \"fields\": [\n"
          + "    {\n"
          + "      \"type\": \"INTEGER\",\n"
          + "      \"nullable\": false,\n"
          + "      \"name\": \"i\"\n"
          + "    },\n"
          + "    {\n"
          + "      \"type\": \"VARCHAR\",\n"
          + "      \"nullable\": true,\n"
          + "      \"precision\": 9,\n"
          + "      \"name\": \"v\"\n"
          + "    },\n"
          + "    {\n"
          + "      \"fields\": [\n"
          + "        {\n"
          + "          \"type\": \"DATE\",\n"
          + "          \"nullable\": false,\n"
          + "          \"name\": \"d\"\n"
          + "        }\n"
          + "      ],\n"
          + "      \"nullable\": false,\n"
          + "      \"name\": \"r\"\n"
          + "    }\n"
          + "  ],\n"
          + "  \"nullable\": false\n"
          + "}";
      assertThat(s, is(expectedJson));  // 断言实际JSON字符串与期望值一致
      final RelDataType type2 = json.toType(typeFactory, o);  // 将JSON对象反序列化为RelDataType对象
      assertThat(type2, is(type));  // 断言反序列化后的类型与原始类型相同
      return 0;  // 返回测试结果
    });
    assertThat(i, is(0));  // 断言返回值为0
  }

  /**
   * Unit test for {@link org.apache.calcite.rel.externalize.RelJsonWriter} on
   * a simple tree of relational expressions, consisting of a table and a
   * project including window expressions.
   */
  @Test void testWriter() {  // testWriter方法：测试RelJsonWriter对简单关系表达式树的JSON序列化
    String s =
        Frameworks.withPlanner((cluster, relOptSchema, rootSchema) -> {  // 使用框架创建规划器
          rootSchema.add("hr",
              new ReflectiveSchema(new HrSchema()));  // 添加HR模式到根模式
          final RelOptTable table =
              requireNonNull(
                  relOptSchema.getTableForMember(Arrays.asList("hr", "emps")));  // 获取hr.emps表
          LogicalTableScan scan =
              LogicalTableScan.create(cluster, table, ImmutableList.of());  // 创建表扫描节点
          final RexBuilder rexBuilder = cluster.getRexBuilder();  // 获取Rex构建器
          LogicalFilter filter =
              LogicalFilter.create(scan,  // 创建过滤节点，过滤条件为deptno=10
                  rexBuilder.makeCall(
                      SqlStdOperatorTable.EQUALS,
                      rexBuilder.makeFieldAccess(
                          rexBuilder.makeRangeReference(scan),
                          "deptno", true),
                      rexBuilder.makeExactLiteral(BigDecimal.TEN)));
          final RelJsonWriter writer = new RelJsonWriter();  // 创建JSON写入器
          final RelDataType bigIntType =
              cluster.getTypeFactory().createSqlType(SqlTypeName.BIGINT);  // 创建BIGINT类型
          LogicalAggregate aggregate =
              LogicalAggregate.create(filter,  // 创建聚合节点，按第0列分组，包含两个COUNT函数
                  ImmutableList.of(),
                  ImmutableBitSet.of(0),
                  null,
                  ImmutableList.of(
                      AggregateCall.create(SqlStdOperatorTable.COUNT,  // 创建DISTINCT COUNT聚合调用
                          true, false, false, ImmutableList.of(),
                          ImmutableList.of(1), -1, null,
                          RelCollations.EMPTY, bigIntType, "c"),
                      AggregateCall.create(SqlStdOperatorTable.COUNT,  // 创建普通COUNT聚合调用
                          false, false, false, ImmutableList.of(),
                          ImmutableList.of(), -1, null,
                          RelCollations.EMPTY, bigIntType, "d")));
          aggregate.explain(writer);  // 将聚合节点序列化为JSON
          return writer.asString();  // 返回JSON字符串
        });
    assertThat(s, is(XX));  // 断言JSON字符串与XX常量一致
  }

  static final String BINARY_LITERAL = "{\n"
      + "  \"rels\": [\n"
      + "    {\n"
      + "      \"id\": \"0\",\n"
      + "      \"relOp\": \"LogicalValues\",\n"
      + "      \"type\": [\n"
      + "        {\n"
      + "          \"type\": \"BINARY\",\n"
      + "          \"nullable\": false,\n"
      + "          \"precision\": 2,\n"
      + "          \"name\": \"$f0\"\n"
      + "        }\n"
      + "      ],\n"
      + "      \"tuples\": [\n"
      + "        [\n"
      + "          {\n"
      + "            \"literal\": \"0a4b\",\n"
      + "            \"type\": {\n"
      + "              \"type\": \"BINARY\",\n"
      + "              \"nullable\": false,\n"
      + "              \"precision\": 2\n"
      + "            }\n"
      + "          }\n"
      + "        ]\n"
      + "      ],\n"
      + "      \"inputs\": []\n"
      + "    }\n"
      + "  ]\n"
      + "}";  // BINARY_LITERAL常量：包含二进制字面量的JSON字符串，用于测试BINARY类型的序列化

  /** Test case for <a href="https://issues.apache.org/jira/browse/CALCITE-6980">
   * [CALCITE 6980] RelJson cannot serialize binary literals</a>. */
  @Test void testVarbinary() {  // testVarbinary方法：测试二进制字面量的JSON序列化（修复CALCITE-6980）
    final Function<RelBuilder, RelNode> relFn = b -> {  // 定义关系表达式构建函数
      RelDataType rowType = b.getTypeFactory().builder()  // 构建行类型
          .add("a", SqlTypeName.INTEGER)  // 添加INTEGER类型的字段"a"
          .build();
      return b.values(rowType, 0)  // 创建Values节点
          .project(b.getRexBuilder().makeBinaryLiteral(new ByteString(new byte[]{0xA, 0x4B})))  // 投影二进制字面量0x0A4B
          .build();  // 构建关系表达式
    };
    relFn(relFn)
        .assertThatJson(isLinux(BINARY_LITERAL));  // 断言JSON输出与BINARY_LITERAL常量一致
  }

  static final String UUID_LITERAL = "{\n"
      + "  \"rels\": [\n"
      + "    {\n"
      + "      \"id\": \"0\",\n"
      + "      \"relOp\": \"LogicalValues\",\n"
      + "      \"type\": [\n"
      + "        {\n"
      + "          \"type\": \"UUID\",\n"
      + "          \"nullable\": false,\n"
      + "          \"name\": \"$f0\"\n"
      + "        }\n"
      + "      ],\n"
      + "      \"tuples\": [\n"
      + "        [\n"
      + "          {\n"
      + "            \"literal\": \"123e4567-e89b-12d3-a456-426655440000\",\n"
      + "            \"type\": {\n"
      + "              \"type\": \"UUID\",\n"
      + "              \"nullable\": false\n"
      + "            }\n"
      + "          }\n"
      + "        ]\n"
      + "      ],\n"
      + "      \"inputs\": []\n"
      + "    }\n"
      + "  ]\n"
      + "}";  // UUID_LITERAL常量：包含UUID字面量的JSON字符串，用于测试UUID类型的序列化

  /** Test case for <a href="https://issues.apache.org/jira/browse/CALCITE-6992">
   * [CALCITE 6992] RelJson cannot serialize UUID literals</a>. */
  @Test void testUuid() {  // testUuid方法：测试UUID字面量的JSON序列化（修复CALCITE-6992）
    final Function<RelBuilder, RelNode> relFn = b -> {  // 定义关系表达式构建函数
      RelDataType rowType = b.getTypeFactory().builder()  // 构建行类型
          .add("a", SqlTypeName.INTEGER)  // 添加INTEGER类型的字段"a"
          .build();
      return b.values(rowType, 0).project(  // 创建Values节点并投影
          b.getRexBuilder().makeUuidLiteral(  // 创建UUID字面量
              UUID.fromString("123e4567-e89b-12d3-a456-426655440000")))
          .build();  // 构建关系表达式
    };
    relFn(relFn)
        .assertThatJson(isLinux(UUID_LITERAL));  // 断言JSON输出与UUID_LITERAL常量一致
  }

  /**
   * Unit test for {@link org.apache.calcite.rel.externalize.RelJsonWriter} on
   * a simple tree of relational expressions, consisting of a table, a filter
   * and an aggregate node.
   */
  @Test void testWriter2() {  // testWriter2方法：测试RelJsonWriter对包含窗口函数的关系表达式树的JSON序列化
    String s =
        Frameworks.withPlanner((cluster, relOptSchema, rootSchema) -> {  // 使用框架创建规划器
          rootSchema.add("hr",
              new ReflectiveSchema(new HrSchema()));  // 添加HR模式到根模式
          final RelOptTable table =
              requireNonNull(
                  relOptSchema.getTableForMember(Arrays.asList("hr", "emps")));  // 获取hr.emps表
          LogicalTableScan scan =
              LogicalTableScan.create(cluster, table, ImmutableList.of());  // 创建表扫描节点
          final RexBuilder rexBuilder = cluster.getRexBuilder();  // 获取Rex构建器
          final RelDataType bigIntType =
              cluster.getTypeFactory().createSqlType(SqlTypeName.BIGINT);  // 创建BIGINT类型
          LogicalProject project =
              LogicalProject.create(scan,  // 创建投影节点，包含两个窗口函数
                  ImmutableList.of(),
                  ImmutableList.of(
                      rexBuilder.makeInputRef(scan, 0),  // 第0列：输入引用
                      rexBuilder.makeOver(bigIntType,  // 第1列：COUNT OVER窗口函数
                          SqlStdOperatorTable.COUNT,
                          ImmutableList.of(rexBuilder.makeInputRef(scan, 0)),
                          ImmutableList.of(rexBuilder.makeInputRef(scan, 2)),  // PARTITION BY $2
                          ImmutableList.of(
                              new RexFieldCollation(
                                  rexBuilder.makeInputRef(scan, 1), ImmutableSet.of())),  // ORDER BY $1
                          RexWindowBounds.UNBOUNDED_PRECEDING,  // ROWS BETWEEN UNBOUNDED PRECEDING
                          RexWindowBounds.CURRENT_ROW,  // AND CURRENT ROW
                          true, true, false, false, false),
                      rexBuilder.makeOver(bigIntType,  // 第2列：SUM OVER窗口函数
                          SqlStdOperatorTable.SUM,
                          ImmutableList.of(rexBuilder.makeInputRef(scan, 0)),
                          ImmutableList.of(rexBuilder.makeInputRef(scan, 2)),  // PARTITION BY $2
                          ImmutableList.of(
                              new RexFieldCollation(
                                  rexBuilder.makeInputRef(scan, 1), ImmutableSet.of())),  // ORDER BY $1
                          RexWindowBounds.CURRENT_ROW,  // RANGE BETWEEN CURRENT ROW
                          RexWindowBounds.following(
                              rexBuilder.makeExactLiteral(BigDecimal.ONE)),  // AND 1 FOLLOWING
                          false, true, false, false, false)),
                  ImmutableList.of("field0", "field1", "field2"),
                  ImmutableSet.of());
          final RelJsonWriter writer = new RelJsonWriter();  // 创建JSON写入器
          project.explain(writer);  // 将投影节点序列化为JSON
          return writer.asString();  // 返回JSON字符串
        });
    assertThat(s, is(XX2));  // 断言JSON字符串与XX2常量一致
  }

  @Test void testExchange() {  // testExchange方法：测试Exchange（数据交换）节点的序列化和反序列化
    final Function<RelBuilder, RelNode> relFn = b ->  // 定义关系表达式构建函数
        b.scan("EMP")  // 扫描EMP表
            .exchange(RelDistributions.hash(ImmutableList.of(0, 1)))  // 创建哈希分布的交换节点，按第0和第1列哈希
            .build();  // 构建关系表达式
    final String expected = ""  // 期望的执行计划文本
        + "LogicalExchange(distribution=[hash[0, 1]])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatPlan(isLinux(expected));  // 断言执行计划与期望值一致
  }

  @Test public void testExchangeWithDistributionTraitDef() {  // testExchangeWithDistributionTraitDef方法：测试带有分布特征定义的Exchange节点
    final Function<RelBuilder, RelNode> relFn = b ->  // 定义关系表达式构建函数
        b.scan("EMP")  // 扫描EMP表
            .exchange(RelDistributions.hash(ImmutableList.of(0, 1)))  // 创建哈希分布的交换节点
            .build();  // 构建关系表达式
    final String expected = ""  // 期望的执行计划文本
        + "LogicalExchange(distribution=[hash[0, 1]])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .withDistribution(true)  // 启用分布特征
        .assertThatPlan(isLinux(expected));  // 断言执行计划与期望值一致
  }

  /**
   * Unit test for {@link org.apache.calcite.rel.externalize.RelJsonReader}.
   */
  @Test void testReader() {  // testReader方法：测试RelJsonReader的JSON反序列化功能
    String s =
        Frameworks.withPlanner((cluster, relOptSchema, rootSchema) -> {  // 使用框架创建规划器
          SchemaPlus schema =
              rootSchema.add("hr",
                  new ReflectiveSchema(new HrSchema()));  // 添加HR模式到根模式
          final RelJsonReader reader =
              new RelJsonReader(cluster, relOptSchema, schema);  // 创建JSON读取器
          RelNode node;
          try {
            node = reader.read(XX);  // 从JSON字符串读取关系表达式
          } catch (IOException e) {
            throw TestUtil.rethrow(e);  // 抛出IO异常
          }
          return RelOptUtil.dumpPlan("", node, SqlExplainFormat.TEXT,  // 转储执行计划为文本格式
              SqlExplainLevel.EXPPLAN_ATTRIBUTES);
        });

    assertThat(s,  // 断言执行计划与期望值一致
        isLinux("LogicalAggregate(group=[{0}], c=[COUNT(DISTINCT $1)], d=[COUNT()])\n"
            + "  LogicalFilter(condition=[=($1, 10)])\n"
            + "    LogicalTableScan(table=[[hr, emps]])\n"));
  }

  @Test void testReader1() {  // testReader1方法：测试二进制字面量的JSON反序列化
    String s =
        Frameworks.withPlanner((cluster, relOptSchema, rootSchema) -> {  // 使用框架创建规划器
          SchemaPlus schema =
              rootSchema.add("hr",
                  new ReflectiveSchema(new HrSchema()));  // 添加HR模式到根模式
          final RelJsonReader reader =
              new RelJsonReader(cluster, relOptSchema, schema);  // 创建JSON读取器
          RelNode node;
          try {
            node = reader.read(BINARY_LITERAL);  // 从JSON字符串读取包含二进制字面量的关系表达式
          } catch (IOException e) {
            throw TestUtil.rethrow(e);  // 抛出IO异常
          }
          return RelOptUtil.dumpPlan("", node, SqlExplainFormat.TEXT,  // 转储执行计划为文本格式
              SqlExplainLevel.EXPPLAN_ATTRIBUTES);
        });

    assertThat(s,  // 断言执行计划与期望值一致
        isLinux("LogicalValues(tuples=[[{ X'0a4b' }]])\n"));
  }

  @Test void testReaderUuid() {  // testReaderUuid方法：测试UUID字面量的JSON反序列化
    String s =
        Frameworks.withPlanner((cluster, relOptSchema, rootSchema) -> {  // 使用框架创建规划器
          SchemaPlus schema =
              rootSchema.add("hr",
                  new ReflectiveSchema(new HrSchema()));  // 添加HR模式到根模式
          final RelJsonReader reader =
              new RelJsonReader(cluster, relOptSchema, schema);  // 创建JSON读取器
          RelNode node;
          try {
            node = reader.read(UUID_LITERAL);  // 从JSON字符串读取包含UUID字面量的关系表达式
          } catch (IOException e) {
            throw TestUtil.rethrow(e);  // 抛出IO异常
          }
          return RelOptUtil.dumpPlan("", node, SqlExplainFormat.TEXT,  // 转储执行计划为文本格式
              SqlExplainLevel.EXPPLAN_ATTRIBUTES);
        });

    assertThat(s,  // 断言执行计划与期望值一致
        isLinux("LogicalValues(tuples=[[{ 123e4567-e89b-12d3-a456-426655440000 }]])\n"));
  }

  /**
   * Unit test for {@link org.apache.calcite.rel.externalize.RelJsonReader}.
   */
  @Test void testReader2() {  // testReader2方法：测试包含窗口函数的关系表达式的JSON反序列化
    String s =
        Frameworks.withPlanner((cluster, relOptSchema, rootSchema) -> {  // 使用框架创建规划器
          SchemaPlus schema =
              rootSchema.add("hr",
                  new ReflectiveSchema(new HrSchema()));  // 添加HR模式到根模式
          final RelJsonReader reader =
              new RelJsonReader(cluster, relOptSchema, schema);  // 创建JSON读取器
          RelNode node;
          try {
            node = reader.read(XX2);  // 从JSON字符串读取包含窗口函数的关系表达式
          } catch (IOException e) {
            throw TestUtil.rethrow(e);  // 抛出IO异常
          }
          return RelOptUtil.dumpPlan("", node, SqlExplainFormat.TEXT,  // 转储执行计划为文本格式
              SqlExplainLevel.EXPPLAN_ATTRIBUTES);
        });

    assertThat(s,
        isLinux("LogicalProject(field0=[$0],"
            + " field1=[COUNT($0) OVER (PARTITION BY $2 ORDER BY $1 NULLS LAST "
            + "ROWS UNBOUNDED PRECEDING)],"
            + " field2=[SUM($0) OVER (PARTITION BY $2 ORDER BY $1 NULLS LAST "
            + "RANGE BETWEEN CURRENT ROW AND 1 FOLLOWING)])\n"
            + "  LogicalTableScan(table=[[hr, emps]])\n"));
  }

  /**
   * Unit test for {@link org.apache.calcite.rel.externalize.RelJsonReader}.
   */
  @Test void testReaderNull() {  // testReaderNull方法：测试包含NULL字面量的关系表达式的JSON反序列化
    String s =
        Frameworks.withPlanner((cluster, relOptSchema, rootSchema) -> {  // 使用框架创建规划器
          SchemaPlus schema =
              rootSchema.add("hr",
                  new ReflectiveSchema(new HrSchema()));  // 添加HR模式到根模式
          final RelJsonReader reader =
              new RelJsonReader(cluster, relOptSchema, schema);  // 创建JSON读取器
          RelNode node;
          try {
            node = reader.read(XXNULL);  // 从JSON字符串读取包含NULL值的关系表达式
          } catch (IOException e) {
            throw TestUtil.rethrow(e);  // 抛出IO异常
          }
          return RelOptUtil.dumpPlan("", node, SqlExplainFormat.TEXT,  // 转储执行计划为文本格式
              SqlExplainLevel.EXPPLAN_ATTRIBUTES);
        });

    assertThat(s,  // 断言执行计划与期望值一致
        isLinux("LogicalAggregate(group=[{0}], agg#0=[COUNT(DISTINCT $1)], agg#1=[COUNT()])\n"
            + "  LogicalFilter(condition=[=($1, null:INTEGER)])\n"
            + "    LogicalTableScan(table=[[hr, emps]])\n"));
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4893">[CALCITE-4893]
   * JsonParseException happens when externalizing expressions with escape
   * character from JSON</a>. */
  @Test void testEscapeCharacter() {  // testEscapeCharacter方法：测试转义字符的JSON序列化（修复CALCITE-4893）
    final Function<RelBuilder, RelNode> relFn = b -> b  // 定义关系表达式构建函数
        .scan("EMP")  // 扫描EMP表
        .project(
            b.call(new MockSqlOperatorTable.SplitFunction(),  // 调用SPLIT函数
                b.field("ENAME"), b.literal("\r")))  // 参数：ENAME字段和回车符字面量
        .build();  // 构建关系表达式
    final String expected = ""  // 期望的执行计划文本
        + "LogicalProject($f0=[SPLIT($1, '\r')])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatPlan(isLinux(expected));  // 断言执行计划与期望值一致
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6703">[CALCITE-6703]
   * RelJson cannot handle timestamps prior to 1970-01-25 20:31:23.648</a>. */
  @Test void testJsonToRexForTimestamp() {  // testJsonToRexForTimestamp方法：测试时间戳的JSON反序列化（修复CALCITE-6703）
    // Below Integer.MAX_VALUE
    final String timestampRepresentedAsInt = "{\n"  // 低于Integer.MAX_VALUE的时间戳
          + "  \"literal\": 2129400000,\n"
          + "  \"type\": {\n"
          + "    \"type\": \"TIMESTAMP\",\n"
          + "    \"nullable\": false\n"
          + "  }\n"
          + "}\n";
    // Above Integer.MAX_VALUE
    final String timestampRepresentedAsLong = "{\n"  // 高于Integer.MAX_VALUE的时间戳
          + "  \"literal\": 3129400000,\n"
          + "  \"type\": {\n"
          + "    \"type\": \"TIMESTAMP\",\n"
          + "    \"nullable\": false\n"
          + "  }\n"
          + "}\n";

    // These timestamps were verified using BigQuery's UNIX_MILLIS function.
    assertThatReadExpressionResult(timestampRepresentedAsInt, is("1970-01-25 15:30:00"));  // 断言时间戳正确解析
    assertThatReadExpressionResult(timestampRepresentedAsLong, is("1970-02-06 05:16:40"));  // 断言时间戳正确解析
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6703">[CALCITE-6703]
   * RelJson cannot handle timestamps prior to 1970-01-25 20:31:23.648</a>. */
  @Test void testJsonToRexForTimestampWithLocalTimeZone() {  // testJsonToRexForTimestampWithLocalTimeZone方法：测试带本地时区的时间戳的JSON反序列化
    // Below Integer.MAX_VALUE
    final String timestampWithLocalTzRepresentedAsInt = "{\n"  // 低于Integer.MAX_VALUE的带本地时区的时间戳
          + "  \"literal\": 2129400000,\n"
          + "  \"type\": {\n"
          + "    \"type\": \"TIMESTAMP_WITH_LOCAL_TIME_ZONE\",\n"
          + "    \"nullable\": false\n"
          + "  }\n"
          + "}\n";
    // Above Integer.MAX_VALUE
    final String timestampWithLocalTzRepresentedAsLong = "{\n"  // 高于Integer.MAX_VALUE的带本地时区的时间戳
          + "  \"literal\": 3129400000,\n"
          + "  \"type\": {\n"
          + "    \"type\": \"TIMESTAMP_WITH_LOCAL_TIME_ZONE\",\n"
          + "    \"nullable\": false\n"
          + "  }\n"
          + "}\n";

    // These timestamps were verified using BigQuery's UNIX_MILLIS function.
    assertThatReadExpressionResult(timestampWithLocalTzRepresentedAsInt,
          is("1970-01-25 15:30:00:TIMESTAMP_WITH_LOCAL_TIME_ZONE(0)"));  // 断言时间戳正确解析
    assertThatReadExpressionResult(timestampWithLocalTzRepresentedAsLong,
          is("1970-02-06 05:16:40:TIMESTAMP_WITH_LOCAL_TIME_ZONE(0)"));  // 断言时间戳正确解析
  }


  @Test void testJsonToRex() {  // testJsonToRex方法：测试RexNode（行表达式）的JSON反序列化
    // Test simple literal without inputs
    final String jsonString1 = "{\n"
        + "  \"literal\": 10,\n"
        + "  \"type\": {\n"
        + "    \"type\": \"INTEGER\",\n"
        + "    \"nullable\": false\n"
        + "  }\n"
        + "}\n";

    assertThatReadExpressionResult(jsonString1, is("10"));

    // Test binary operator ('+') with an input and a literal
    final String jsonString2 = "{ \"op\": \n"
        + "  { \"name\": \"+\",\n"
        + "    \"kind\": \"PLUS\",\n"
        + "    \"syntax\": \"BINARY\"\n"
        + "  },\n"
        + "  \"operands\": [\n"
        + "    {\n"
        + "      \"input\": 1,\n"
        + "      \"name\": \"$1\"\n"
        + "    },\n"
        + "    {\n"
        + "      \"literal\": 2,\n"
        + "      \"type\": { \"type\": \"INTEGER\", \"nullable\": false }\n"
        + "    }\n"
        + "  ]\n"
        + "}";
    assertThatReadExpressionResult(jsonString2, is("+(1001, 2)"));
  }

  private void assertThatReadExpressionResult(String json, Matcher<String> matcher) {  // assertThatReadExpressionResult方法：断言JSON表达式反序列化后的结果与匹配器一致
    final FrameworkConfig config = RelBuilderTest.config().build();  // 构建框架配置
    final RelBuilder builder = RelBuilder.create(config);  // 创建关系表达式构建器
    final RelOptCluster cluster = builder.getCluster();  // 获取优化集群
    final ObjectMapper mapper = new ObjectMapper();  // 创建Jackson对象映射器
    final TypeReference<LinkedHashMap<String, Object>> typeRef =
        new TypeReference<LinkedHashMap<String, Object>>() {
        };
    final Map<String, Object> o;
    try {
      o = mapper
          .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)  // 配置使用BigDecimal处理浮点数
          .readValue(json, typeRef);  // 将JSON字符串解析为LinkedHashMap
    } catch (JsonProcessingException e) {
      throw TestUtil.rethrow(e);  // 抛出JSON处理异常
    }
    final RelJson relJson = RelJson.create()
        .withInputTranslator(RelWriterTest::translateInput)  // 配置输入翻译器
        .withLibraryOperatorTable();  // 配置库操作符表
    final RexNode e = relJson.toRex(cluster, o);  // 将JSON对象转换为RexNode
    assertThat(e, hasToString(matcher));  // 断言RexNode的字符串表示与匹配器一致
  }

  /** Intended as an instance of {@link RelJson.InputTranslator},
   * translates input {@code input} into an INTEGER literal
   * "{@code 1000 + input}". */
  private static RexNode translateInput(RelJson relJson, int input,  // translateInput方法：将输入引用转换为字面量（1000+input），用于测试
      Map<String, @Nullable Object> map, RelInput relInput) {
    final RexBuilder rexBuilder = relInput.getCluster().getRexBuilder();  // 获取Rex构建器
    return rexBuilder.makeExactLiteral(BigDecimal.valueOf(1000 + input));  // 返回值为1000+input的精确字面量
  }

  @Test void testTrim() {  // testTrim方法：测试TRIM函数的JSON序列化和反序列化
    final Function<RelBuilder, RelNode> relFn = b ->  // 定义关系表达式构建函数
        b.scan("EMP")  // 扫描EMP表
            .project(
                b.alias(
                    b.call(SqlStdOperatorTable.TRIM,  // 调用TRIM函数
                        b.literal(SqlTrimFunction.Flag.BOTH),  // BOTH标志：去除两端的空格
                        b.literal(" "),  // 去除的字符：空格
                        b.field("ENAME")),  // 要处理的字段：ENAME
                    "trimmed_ename"))  // 别名：trimmed_ename
            .build();  // 构建关系表达式
    final String expected = ""  // 期望的执行计划文本
        + "LogicalProject(trimmed_ename=[TRIM(FLAG(BOTH), ' ', $1)])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatPlan(isLinux(expected));  // 断言执行计划与期望值一致
  }

  @Test void testPlusOperator() {  // testPlusOperator方法：测试加法操作符的JSON序列化和反序列化
    final Function<RelBuilder, RelNode> relFn = b ->  // 定义关系表达式构建函数
        b.scan("EMP")  // 扫描EMP表
            .project(
                b.call(SqlStdOperatorTable.PLUS,  // 调用加法操作符
                    b.field("SAL"),  // 第一个操作数：SAL字段
                    b.literal(10)))  // 第二个操作数：字面量10
            .build();  // 构建关系表达式
    final String expected = ""  // 期望的执行计划文本
        + "LogicalProject($f0=[+($5, 10)])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatPlan(isLinux(expected));  // 断言执行计划与期望值一致
  }

  @Test void testSearchOperator() {  // testSearchOperator方法：测试SEARCH操作符（BETWEEN、IN等）的JSON序列化和反序列化
    final FrameworkConfig config = RelBuilderTest.config().build();
    final RelBuilder b = RelBuilder.create(config);
    final RexBuilder rexBuilder = b.getRexBuilder();

    // Test toJson -> toRex -> toJson is the same.
    final JsonBuilder jsonBuilder = new JsonBuilder();
    final RelJson relJson = RelJson.create().withJsonBuilder(jsonBuilder);
    final Consumer<RexNode> consumer = node -> {
      Object jsonRepresentation = relJson.toJson(node);
      assertThat(jsonRepresentation, notNullValue());

      RexNode deserialized = relJson.toRex(b.getCluster(), jsonRepresentation);
      assertThat(node, is(deserialized));
      assertThat(jsonRepresentation, is(relJson.toJson(deserialized)));

      // Test that toRex is the same as toJsonString -> readRex
      final String s = jsonBuilder.toJsonString(jsonRepresentation);
      RexNode deserialized2;
      try {
        deserialized2 = RelJsonReader.readRex(b.getCluster(), s);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      assertThat(deserialized2, is(deserialized));
    };

    // Commented out but we should also get this passing! SEARCH in a RelNode
    // using the JSON writer also leads to failures.
    if (false) {
      final RelNode rel = b
          .scan("EMP")
          .project(b.between(b.field("DEPTNO"), b.literal(20), b.literal(30)))
          .build();
      final RelJsonWriter jsonWriter =
          new RelJsonWriter(new JsonBuilder(), RelJson::withLibraryOperatorTable);
      rel.explain(jsonWriter);
      String relJsonString = jsonWriter.asString();
      String result = deserializeAndDumpToTextFormat(getSchema(rel), relJsonString);
      final String expected = "<TODO>";
      assertThat(result, isLinux(expected));
    }

    RexNode betweenDoubles =
        rexBuilder.makeBetween(b.literal(45),
            b.literal(20.0000000000000049),
            b.literal(30.0000000000000049));
    consumer.accept(betweenDoubles);

    RexNode betweenDecimal =
        rexBuilder.makeBetween(b.literal(45),
            b.literal(BigDecimal.valueOf(20.0)),
            b.literal(BigDecimal.valueOf(30.0)));
    consumer.accept(betweenDecimal);

    RexNode between =
        rexBuilder.makeBetween(b.literal(45),
            b.literal(20),
            b.literal(30));
    consumer.accept(between);

    RexNode inNode =
        rexBuilder.makeIn(b.literal(12),
        ImmutableList.of(
          b.literal(20),
          b.literal(14)));
    consumer.accept(inNode);

    // Test Calcite DateString class works in a Range
    final DateString d1 =
        DateString.fromCalendarFields(
            new TimestampString(1970, 2, 1, 1, 1, 0).toCalendar());
    final DateString d2 = DateString.fromDaysSinceEpoch(100);
    final DateString d3 = DateString.fromDaysSinceEpoch(1000);
    RexNode dateNode =
        rexBuilder.makeBetween(rexBuilder.makeDateLiteral(d2),
            rexBuilder.makeDateLiteral(d1),
            rexBuilder.makeDateLiteral(d3));
    consumer.accept(dateNode);

    // Test Calcite TimeString
    final RexLiteral t1 = rexBuilder.makeTimeLiteral(new TimeString(1, 0, 0), 0);
    final RexLiteral t2 = rexBuilder.makeTimeLiteral(new TimeString(2, 2, 2), 6);
    final RexLiteral t3 = rexBuilder.makeTimeLiteral(new TimeString(3, 3, 3), 9);

    RexNode timeNode = rexBuilder.makeBetween(t2, t1, t3);
    consumer.accept(timeNode);

    // Test Calcite TimestampString
    final TimestampString ts1 = TimestampString.fromMillisSinceEpoch(79056000000L);
    final TimestampString ts2 = TimestampString.fromMillisSinceEpoch(184982400000L);
    final TimestampString ts3 = TimestampString.fromMillisSinceEpoch(184982400000L);

    final RexLiteral tsr1 = rexBuilder.makeTimestampLiteral(ts1, 0);
    final RexLiteral tsr2 = rexBuilder.makeTimestampLiteral(ts2, 0);
    final RexLiteral tsr3 = rexBuilder.makeTimestampLiteral(ts3, 0);
    RexNode tsNode =
        rexBuilder.makeIn(tsr1, ImmutableList.of(tsr2, tsr3));
    consumer.accept(tsNode);

    // Test Calcite NlsString
    final NlsString nls1 = new NlsString("one", null, null);
    final NlsString nls2 = new NlsString("ten", null, null);
    final NlsString nls3 = new NlsString("sixteen", null, null);
    RexNode nlsNode =
        rexBuilder.makeIn(
            rexBuilder.makeCharLiteral(nls2),
            ImmutableList.of(rexBuilder.makeCharLiteral(nls1),
                rexBuilder.makeCharLiteral(nls3)));
    consumer.accept(nlsNode);
  }

  @ParameterizedTest
  @MethodSource("explainFormats")
  void testAggregateWithAlias(SqlExplainFormat format) {  // testAggregateWithAlias方法：测试带别名的聚合函数的JSON序列化和反序列化
    final FrameworkConfig config = RelBuilderTest.config().build();
    final RelBuilder builder = RelBuilder.create(config);
    // The rel node stands for sql: SELECT max(SAL) as max_sal from EMP group by JOB;
    final RelNode rel = builder
        .scan("EMP")
        .project(
            builder.field("JOB"),
            builder.field("SAL"))
        .aggregate(
            builder.groupKey("JOB"),
            builder.max("max_sal", builder.field("SAL")))
        .project(
            builder.field("max_sal"))
        .build();
    final RelJsonWriter jsonWriter = new RelJsonWriter();
    rel.explain(jsonWriter);
    final String relJson = jsonWriter.asString();
    String s = deserializeAndDump(getSchema(rel), relJson, format);
    final String expected;
    switch (format) {
    case TEXT:
      expected = ""
          + "LogicalProject(max_sal=[$1])\n"
          + "  LogicalAggregate(group=[{0}], max_sal=[MAX($1)])\n"
          + "    LogicalProject(JOB=[$2], SAL=[$5])\n"
          + "      LogicalTableScan(table=[[scott, EMP]])\n";
      break;
    case DOT:
      expected = "digraph {\n"
          + "\"LogicalAggregate\\ngroup = {0}\\nmax_sal = MAX($1)\\n\" -> "
          + "\"LogicalProject\\nmax_sal = $1\\n\" [label=\"0\"]\n"
          + "\"LogicalProject\\nJOB = $2\\nSAL = $5\\n\" -> \"LogicalAggregate\\ngroup = "
          + "{0}\\nmax_sal = MAX($1)\\n\" [label=\"0\"]\n"
          + "\"LogicalTableScan\\ntable = [scott, EMP]\\n\" -> \"LogicalProject\\nJOB = $2\\nSAL = "
          + "$5\\n\" [label=\"0\"]\n"
          + "}\n";
      break;
    default:
      throw new AssertionError();
    }
    assertThat(s, isLinux(expected));
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4804">[CALCITE-4804]
   * Support Snapshot operator serialization and deserialization</a>. */
  @Test void testSnapshot() {  // testSnapshot方法：测试SNAPSHOT操作符的JSON序列化和反序列化（修复CALCITE-4804）
    // Equivalent SQL:
    //   SELECT *
    //   FROM products_temporal FOR SYSTEM_TIME AS OF TIMESTAMP '2011-07-20 12:34:56'
    final RelBuilder builder = RelBuilder.create(RelBuilderTest.config().build());
    RelNode root =
        builder.scan("products_temporal")
            .snapshot(
                builder.getRexBuilder().makeTimestampLiteral(
                    new TimestampString("2011-07-20 12:34:56"), 0))
            .build();

    RelJsonWriter jsonWriter = new RelJsonWriter();
    root.explain(jsonWriter);
    String relJson = jsonWriter.asString();
    String s = deserializeAndDumpToTextFormat(getSchema(root), relJson);
    String expected = "LogicalSnapshot(period=[2011-07-20 12:34:56])\n"
        + "  LogicalTableScan(table=[[scott, products_temporal]])\n";
    assertThat(s, isLinux(expected));
  }

  @Test void testDeserializeInvalidOperatorName() {  // testDeserializeInvalidOperatorName方法：测试反序列化无效操作符名称时的异常处理
    final FrameworkConfig config = RelBuilderTest.config().build();
    final RelBuilder builder = RelBuilder.create(config);
    final RelNode rel = builder
        .scan("EMP")
        .project(
            builder.field("JOB"),
            builder.field("SAL"))
        .aggregate(
            builder.groupKey("JOB"),
            builder.max("max_sal", builder.field("SAL")),
            builder.min("min_sal", builder.field("SAL")))
        .project(
            builder.field("max_sal"),
            builder.field("min_sal"))
        .build();
    final RelJsonWriter jsonWriter = new RelJsonWriter();
    rel.explain(jsonWriter);
    // mock a non exist SqlOperator
    String relJson = jsonWriter.asString().replace("\"name\": \"MAX\"", "\"name\": \"MAXS\"");
    assertThrows(RuntimeException.class,
        () -> deserializeAndDumpToTextFormat(getSchema(rel), relJson),
        "org.apache.calcite.runtime.CalciteException: "
            + "No operator for 'MAXS' with kind: 'MAX', syntax: 'FUNCTION' during JSON deserialization");
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5349">[CALCITE-5349]
   * RelJson deserialization should support SqlLibraryOperators</a>. Before the
   * fix, non-standard operators such as BigQuery's
   * {@link SqlLibraryOperators#CURRENT_DATETIME} would throw during
   * deserialization. */
  @Test void testDeserializeNonStandardOperator() {  // testDeserializeNonStandardOperator方法：测试非标准操作符（如BigQuery的CURRENT_DATETIME）的反序列化（修复CALCITE-5349）
    final FrameworkConfig config = RelBuilderTest.config().build();
    final RelBuilder builder = RelBuilder.create(config);
    final RelNode rel = builder
        .scan("EMP")
        .project(builder.field("JOB"),
            builder.call(SqlLibraryOperators.CURRENT_DATETIME))
        .build();
    final RelJsonWriter jsonWriter =
        new RelJsonWriter(new JsonBuilder(), RelJson::withLibraryOperatorTable);
    rel.explain(jsonWriter);
    String relJson = jsonWriter.asString();
    String result = deserializeAndDumpToTextFormat(getSchema(rel), relJson);
    final String expected = ""
        + "LogicalProject(JOB=[$2], $f1=[CURRENT_DATETIME])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    assertThat(result, isLinux(expected));
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5607">[CALCITE-5607]
   * Datetime MINUS throws ArrayIndexOutOfBounds error when serializing toRex</a>.
   */
  @Test void testDeserializeMinusDateOperator() {  // testDeserializeMinusDateOperator方法：测试日期减法操作符的反序列化（修复CALCITE-5607）
    final FrameworkConfig config = RelBuilderTest.config().build();
    final RelBuilder builder = RelBuilder.create(config);
    final RexBuilder rb = builder.getRexBuilder();
    final RelDataTypeFactory typeFactory = rb.getTypeFactory();
    final SqlIntervalQualifier qualifier =
        new SqlIntervalQualifier(TimeUnit.MONTH, null, SqlParserPos.ZERO);
    final RexNode op1 = rb.makeTimestampLiteral(new TimestampString("2012-12-03 12:34:44"), 0);
    final RexNode op2 = rb.makeTimestampLiteral(new TimestampString("2014-12-23 12:34:44"), 0);
    final RelDataType intervalType =
        typeFactory.createTypeWithNullability(
            typeFactory.createSqlIntervalType(qualifier),
            op1.getType().isNullable() || op2.getType().isNullable());
    final RelNode rel = builder
        .scan("EMP")
        .project(builder.field("JOB"),
            rb.makeCall(intervalType, SqlStdOperatorTable.MINUS_DATE,
                ImmutableList.of(op2, op1))).build();
    final RelJsonWriter jsonWriter =
        new RelJsonWriter(new JsonBuilder(), RelJson::withLibraryOperatorTable);
    rel.explain(jsonWriter);
    String relJson = jsonWriter.asString();
    String result = deserializeAndDumpToTextFormat(getSchema(rel), relJson);
    final String expected =
        "LogicalProject(JOB=[$2], $f1=[-(2014-12-23 12:34:44, 2012-12-03 12:34:44)])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    assertThat(result, isLinux(expected));
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6323">[CALCITE-6323]</a>
   *
   * <p>Before the fix, RelJson.toRex would throw an ArrayIndexOutOfBounds error
   * when deserializing SAFE_CAST due to type inference requiring 2 operands.
   *
   * <p>The solution is to add in 'type' when serializing to JSON.
   */
  @Test void testDeserializeSafeCastOperator() {  // testDeserializeSafeCastOperator方法：测试SAFE_CAST操作符的反序列化（修复CALCITE-6323）
    final FrameworkConfig config = RelBuilderTest.config().build();
    final RelBuilder builder = RelBuilder.create(config);
    final RexBuilder rb = builder.getRexBuilder();
    final RelDataTypeFactory typeFactory = rb.getTypeFactory();
    final RelDataType type = typeFactory.createSqlType(SqlTypeName.DATE);
    final RelNode rel = builder
        .scan("EMP")
        .project(builder.field("JOB"),
            rb.makeCall(type, SqlLibraryOperators.SAFE_CAST,
                ImmutableList.of(builder.field("SAL")))).build();
    final RelJsonWriter jsonWriter =
        new RelJsonWriter(new JsonBuilder(), RelJson::withLibraryOperatorTable);
    rel.explain(jsonWriter);
    String relJson = jsonWriter.asString();
    String result = deserializeAndDumpToTextFormat(getSchema(rel), relJson);
    final String expected =
        "LogicalProject(JOB=[$2], $f1=[SAFE_CAST($5)])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    assertThat(result, isLinux(expected));
  }

  @Test void testAggregateWithoutAlias() {  // testAggregateWithoutAlias方法：测试不带别名的聚合函数的JSON序列化和反序列化
    final FrameworkConfig config = RelBuilderTest.config().build();
    final RelBuilder builder = RelBuilder.create(config);
    // The rel node stands for sql: SELECT max(SAL) from EMP group by JOB;
    final RelNode rel = builder
        .scan("EMP")
        .project(
            builder.field("JOB"),
            builder.field("SAL"))
        .aggregate(
            builder.groupKey("JOB"),
            builder.max(builder.field("SAL")))
        .project(
            builder.field(1))
        .build();
    final RelJsonWriter jsonWriter = new RelJsonWriter();
    rel.explain(jsonWriter);
    final String relJson = jsonWriter.asString();
    String s = deserializeAndDumpToTextFormat(getSchema(rel), relJson);
    final String expected = ""
        + "LogicalProject($f1=[$1])\n"
        + "  LogicalAggregate(group=[{0}], agg#0=[MAX($1)])\n"
        + "    LogicalProject(JOB=[$2], SAL=[$5])\n"
        + "      LogicalTableScan(table=[[scott, EMP]])\n";

    assertThat(s, isLinux(expected));
  }

  @Test void testCalc() {  // testCalc方法：测试Calc（计算）节点的JSON序列化和反序列化
    final Function<RelBuilder, RelNode> relFn = b ->
        b.scan("EMP")
            .let(b2 -> {
              final RexBuilder rexBuilder = b2.getRexBuilder();
              final RelNode scan = b2.build();
              final RelDataType rowType = scan.getRowType();
              final RexProgramBuilder programBuilder =
                  new RexProgramBuilder(rowType, rexBuilder);
              final RelDataTypeField field =
                  rowType.getField("SAL", false, false);
              assertThat(field, notNullValue());
              programBuilder.addIdentity();
              programBuilder.addCondition(
                  rexBuilder.makeCall(SqlStdOperatorTable.GREATER_THAN,
                      new RexInputRef(field.getIndex(), field.getType()),
                      b2.literal(10)));
              return LogicalCalc.create(scan, programBuilder.getProgram());
            });
    final String expected = ""
        + "LogicalCalc(expr#0..7=[{inputs}], expr#8=[10], expr#9=[>($t5, $t8)],"
        + " proj#0..7=[{exprs}], $condition=[$t9])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatPlan(isLinux(expected));
  }

  @ParameterizedTest
  @MethodSource("explainFormats")
  void testCorrelateQuery(SqlExplainFormat format) {  // testCorrelateQuery方法：测试关联查询（Correlate）的JSON序列化和反序列化
    final Holder<RexCorrelVariable> v = Holder.empty();
    final Function<RelBuilder, RelNode> relFn = b -> b.scan("EMP")
        .variable(v::set)
        .scan("DEPT")
        .filter(b.equals(b.field(0), b.field(v.get(), "DEPTNO")))
        .correlate(JoinRelType.INNER, v.get().id, b.field(2, 0, "DEPTNO"))
        .build();
    final String expected;
    switch (format) {
    case TEXT:
      expected = ""
          + "LogicalCorrelate(correlation=[$cor0], joinType=[inner], requiredColumns=[{7}])\n"
          + "  LogicalTableScan(table=[[scott, EMP]])\n"
          + "  LogicalFilter(condition=[=($0, $cor0.DEPTNO)])\n"
          + "    LogicalTableScan(table=[[scott, DEPT]])\n";
      break;
    case DOT:
      expected = "digraph {\n"
          + "\"LogicalTableScan\\ntable = [scott, EMP]\\n\" -> \"LogicalCorrelate\\ncorrelation = "
          + "$cor0\\njoinType = inner\\nrequiredColumns = {7\\n}\\n\" [label=\"0\"]\n"
          + "\"LogicalFilter\\ncondition = =($0, $c\\nor0.DEPTNO)\\n\" -> "
          + "\"LogicalCorrelate\\ncorrelation = $cor0\\njoinType = inner\\nrequiredColumns = "
          + "{7\\n}\\n\" [label=\"1\"]\n"
          + "\"LogicalTableScan\\ntable = [scott, DEPT\\n]\\n\" -> \"LogicalFilter\\ncondition = ="
          + "($0, $c\\nor0.DEPTNO)\\n\" [label=\"0\"]\n"
          + "}\n";
      break;
    default:
      throw new AssertionError(format);
    }
    relFn(relFn)
        .withFormat(format)
        .assertThatPlan(isLinux(expected));
  }

  @Test void testOverWithoutPartition() {  // testOverWithoutPartition方法：测试不带分区的窗口函数的JSON序列化和反序列化
    // Equivalent SQL:
    //   SELECT count(*) OVER (ORDER BY deptno) FROM emp
    final Function<RelBuilder, RelNode> relFn = b ->
        mockCountOver(b, "EMP", ImmutableList.of(), ImmutableList.of("DEPTNO"));
    final String expected = ""
        + "LogicalProject($f0=[COUNT() OVER (ORDER BY $7 NULLS LAST)])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatPlan(isLinux(expected));
  }

  @Test void testProjectionWithCorrelationVariables() {  // testProjectionWithCorrelationVariables方法：测试带关联变量的投影的JSON序列化和反序列化
    final Function<RelBuilder, RelNode> relFn = b -> b.scan("EMP")
        .project(
            ImmutableList.of(b.field("ENAME")),
            ImmutableList.of("ename"),
            true,
            ImmutableSet.of(b.getCluster().createCorrel()))
        .build();

    final String expected = "LogicalProject(variablesSet=[[$cor0]], ename=[$1])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatPlan(isLinux(expected));
  }

  @Test void testOverWithoutOrderKey() {  // testOverWithoutOrderKey方法：测试不带排序键的窗口函数的JSON序列化和反序列化
    // Equivalent SQL:
    //   SELECT count(*) OVER (PARTITION BY deptno) FROM emp
    final Function<RelBuilder, RelNode> relFn = b ->
        mockCountOver(b, "EMP", ImmutableList.of("DEPTNO"), ImmutableList.of());
    final String expected = ""
        + "LogicalProject($f0=[COUNT() OVER (PARTITION BY $7)])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatPlan(isLinux(expected));
  }

  @Test void testInterval() {  // testInterval方法：测试间隔类型（INTERVAL）的JSON序列化和反序列化
    SqlIntervalQualifier sqlIntervalQualifier =
        new SqlIntervalQualifier(TimeUnit.DAY, TimeUnit.DAY, SqlParserPos.ZERO);
    BigDecimal value = new BigDecimal(86400000);
    final Function<RelBuilder, RelNode> relFn = b -> b.scan("EMP")
        .project(
            b.call(SqlStdOperatorTable.TUMBLE_END,
                b.field("HIREDATE"),
                b.getRexBuilder()
                    .makeIntervalLiteral(value, sqlIntervalQualifier)))
        .build();
    final String expected = ""
        + "LogicalProject($f0=[TUMBLE_END($4, 86400000:INTERVAL DAY)])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatPlan(isLinux(expected));
  }

  @Test void testUdf() {  // testUdf方法：测试用户自定义函数（UDF）的JSON序列化和反序列化
    final Function<RelBuilder, RelNode> relFn = b ->
        b.scan("EMP")
            .project(
                b.call(new MockSqlOperatorTable.MyFunction(),
                    b.field("EMPNO")))
            .build();
    final String expected = ""
        + "LogicalProject($f0=[MYFUN($0)])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatPlan(isLinux(expected));
  }

  @ParameterizedTest
  @MethodSource("explainFormats")
  void testUDAF(SqlExplainFormat format) {  // testUDAF方法：测试用户自定义聚合函数（UDAF）的JSON序列化和反序列化
    final Function<RelBuilder, RelNode> relFn = b ->
        b.scan("EMP")
            .project(b.field("ENAME"), b.field("DEPTNO"))
            .aggregate(b.groupKey("ENAME"),
                b.aggregateCall(new MockSqlOperatorTable.MyAggFunc(),
                    b.field("DEPTNO")))
        .build();
    final String expected;
    switch (format) {
    case TEXT:
      expected = ""
          + "LogicalAggregate(group=[{0}], agg#0=[myAggFunc($1)])\n"
          + "  LogicalProject(ENAME=[$1], DEPTNO=[$7])\n"
          + "    LogicalTableScan(table=[[scott, EMP]])\n";
      break;
    case DOT:
      expected = "digraph {\n"
          + "\"LogicalProject\\nENAME = $1\\nDEPTNO = $7\\n\" -> \"LogicalAggregate\\ngroup = "
          + "{0}\\nagg#0 = myAggFunc($1\\n)\\n\" [label=\"0\"]\n"
          + "\"LogicalTableScan\\ntable = [scott, EMP]\\n\" -> \"LogicalProject\\nENAME = "
          + "$1\\nDEPTNO = $7\\n\" [label=\"0\"]\n"
          + "}\n";
      break;
    default:
      throw new AssertionError(format);
    }
    relFn(relFn)
        .withFormat(format)
        .assertThatPlan(isLinux(expected));
  }

  @Test void testArrayType() {  // testArrayType方法：测试数组类型的JSON序列化和反序列化
    final Function<RelBuilder, RelNode> relFn = b ->
        b.scan("EMP")
            .project(
                b.call(new MockSqlOperatorTable.SplitFunction(),
                    b.field("ENAME"), b.literal(",")))
            .build();
    final String expected = ""
        + "LogicalProject($f0=[SPLIT($1, ',')])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatPlan(isLinux(expected));
  }

  @Test void testMapType() {  // testMapType方法：测试MAP类型的JSON序列化和反序列化
    final Function<RelBuilder, RelNode> relFn = b ->
        b.scan("EMP")
            .project(
                b.call(new MockSqlOperatorTable.MapFunction(),
                    b.literal("key"), b.literal("value")))
            .build();
    final String expected = ""
        + "LogicalProject($f0=[MAP('key', 'value')])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatPlan(isLinux(expected));
  }

  /** Returns the schema of a {@link org.apache.calcite.rel.core.TableScan}
   * in this plan, or null if there are no scans. */
  private static RelOptSchema getSchema(RelNode rel) {  // getSchema方法：从关系表达式树中提取表扫描节点的模式
    final Holder<@Nullable RelOptSchema> schemaHolder = Holder.empty();  // 创建模式持有器
    rel.accept(
        new RelShuttleImpl() {  // 创建关系表达式穿梭器
          @Override public RelNode visit(TableScan scan) {  // 访问表扫描节点
            schemaHolder.set(scan.getTable().getRelOptSchema());  // 设置模式
            return super.visit(scan);  // 调用父类方法
          }
        });
    return requireNonNull(schemaHolder.get());  // 返回模式
  }

  /**

     * Deserialize a relnode from the json string by {@link RelJsonReader},

     * and dump it to the given format.

     */

  

    private static String deserializeAndDump(RelOptSchema schema, String relJson,  // deserializeAndDump方法：从JSON字符串反序列化关系表达式并转储为指定格式

        SqlExplainFormat format) {

      return Frameworks.withPlanner((cluster, relOptSchema, rootSchema) -> {  // 使用框架创建规划器

        final RelJsonReader reader =

            new RelJsonReader(cluster, schema, rootSchema,  // 创建JSON读取器

                RelJson::withLibraryOperatorTable);  // 配置库操作符表

        RelNode node;

        try {

          node = reader.read(relJson);  // 从JSON字符串读取关系表达式

        } catch (IOException e) {

          throw TestUtil.rethrow(e);  // 抛出IO异常

        }

        return RelOptUtil.dumpPlan("", node, format,  // 转储执行计划为指定格式

            SqlExplainLevel.EXPPLAN_ATTRIBUTES);

      });

    }

  

    private static String deserializeAndDump(RelOptCluster cluster,  // deserializeAndDump方法重载：使用指定集群反序列化关系表达式

        RelOptSchema schema, String relJson, SqlExplainFormat format) {

      final RelJsonReader reader = new RelJsonReader(cluster, schema, null);  // 创建JSON读取器

      RelNode node;

      try {

        node = reader.read(relJson);  // 从JSON字符串读取关系表达式

      } catch (IOException e) {

        throw TestUtil.rethrow(e);  // 抛出IO异常

      }

      return RelOptUtil.dumpPlan("", node, format,  // 转储执行计划为指定格式

          SqlExplainLevel.EXPPLAN_ATTRIBUTES);

    }

  

    /**

     * Deserialize a relnode from the json string by {@link RelJsonReader},

     * and dump it to text format.

     */

    private static String deserializeAndDumpToTextFormat(RelOptSchema schema,  // deserializeAndDumpToTextFormat方法：从JSON字符串反序列化关系表达式并转储为文本格式

        String relJson) {

      return deserializeAndDump(schema, relJson, SqlExplainFormat.TEXT);  // 调用重载方法，使用TEXT格式

    }

  /**
   * Creates a mock {@link RelNode} that contains OVER. The SQL is as follows:
   *
   * <blockquote>
   * select count(*) over (partition by {@code partitionKeyNames}<br>
   * order by {@code orderKeyNames}) from {@code table}
   * </blockquote>
   *
   * @param table Table name
   * @param partitionKeyNames Partition by column names, may empty, can not be
   * null
   * @param orderKeyNames Order by column names, may empty, can not be null
   * @return RelNode for the SQL
   */
  private RelNode mockCountOver(RelBuilder builder, String table,  // mockCountOver方法：创建包含窗口函数的模拟关系表达式
      List<String> partitionKeyNames, List<String> orderKeyNames) {
    final RexBuilder rexBuilder = builder.getRexBuilder();  // 获取Rex构建器
    final RelDataType type = rexBuilder.getTypeFactory().createSqlType(SqlTypeName.BIGINT);  // 创建BIGINT类型
    List<RexNode> partitionKeys = new ArrayList<>(partitionKeyNames.size());  // 创建分区键列表
    builder.scan(table);  // 扫描表
    for (String partitionkeyName : partitionKeyNames) {
      partitionKeys.add(builder.field(partitionkeyName));  // 添加分区键
    }
    List<RexFieldCollation> orderKeys = new ArrayList<>(orderKeyNames.size());  // 创建排序键列表
    for (String orderKeyName : orderKeyNames) {
      orderKeys.add(new RexFieldCollation(builder.field(orderKeyName), ImmutableSet.of()));  // 添加排序键
    }
    return builder
        .project(
            rexBuilder.makeOver(  // 创建窗口函数
                type,  // 返回类型
                SqlStdOperatorTable.COUNT,  // COUNT函数
                ImmutableList.of(),  // 操作数列表（空）
                partitionKeys,  // 分区键
                ImmutableList.copyOf(orderKeys),  // 排序键
                RexWindowBounds.UNBOUNDED_PRECEDING,  // 窗口下界：无界前驱
                RexWindowBounds.CURRENT_ROW,  // 窗口上界：当前行
                false, true, false, false, false))  // 窗口标志
        .build();  // 构建关系表达式
  }

  @Test void testHashDistributionWithoutKeys() {  // testHashDistributionWithoutKeys方法：测试无键哈希分布的JSON序列化和反序列化
    final Function<RelBuilder, RelNode> relFn = b ->
        createSortPlan(b, RelDistributions.hash(Collections.emptyList()));
    final String expected =
        "LogicalSortExchange(distribution=[hash], collation=[[0]])\n"
            + "  LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatJson(is(HASH_DIST_WITHOUT_KEYS))
        .assertThatPlan(isLinux(expected));
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6200">[CALCITE-6200]
   * RelJson throw UnsupportedOperationException for RexDynamicParam</a>. */
  @Test void testDynamicParam() {  // testDynamicParam方法：测试动态参数（RexDynamicParam）的JSON序列化和反序列化（修复CALCITE-6200）
    final Function<RelBuilder, RelNode> relFn = relBuilder -> {
      final RexBuilder rexBuilder = relBuilder.getRexBuilder();
      final RelDataTypeFactory typeFactory = relBuilder.getTypeFactory();
      final RelDataType intType = typeFactory.createSqlType(SqlTypeName.INTEGER);
      final RexDynamicParam dynamicParam = rexBuilder.makeDynamicParam(intType, 0);
      return relBuilder
          .scan("EMP")
          .sortLimit(null, dynamicParam, relBuilder.fields(RelCollations.EMPTY))
          .build();
    };

    final String expectedJson = "{\n"
        + "  \"rels\": [\n"
        + "    {\n"
        + "      \"id\": \"0\",\n"
        + "      \"relOp\": \"LogicalTableScan\",\n"
        + "      \"table\": [\n"
        + "        \"scott\",\n"
        + "        \"EMP\"\n"
        + "      ],\n"
        + "      \"inputs\": []\n"
        + "    },\n"
        + "    {\n"
        + "      \"id\": \"1\",\n"
        + "      \"relOp\": \"LogicalSort\",\n"
        + "      \"collation\": [],\n"
        + "      \"fetch\": {\n"
        + "        \"dynamicParam\": 0,\n"
        + "        \"type\": {\n"
        + "          \"type\": \"INTEGER\",\n"
        + "          \"nullable\": false\n"
        + "        }\n"
        + "      }\n"
        + "    }\n"
        + "  ]\n"
        + "}";
    final String expectedPlan = "LogicalSort(fetch=[?0])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatJson(is(expectedJson))
        .assertThatPlan(isLinux(expectedPlan));
  }

  @Test void testWriteSortExchangeWithHashDistribution() {  // testWriteSortExchangeWithHashDistribution方法：测试带哈希分布的排序交换的JSON序列化
    final Function<RelBuilder, RelNode> relFn = b ->
        createSortPlan(b, RelDistributions.hash(Lists.newArrayList(0)));
    final String expected = ""
        + "LogicalSortExchange(distribution=[hash[0]], collation=[[0]])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatJson(is(XX3))
        .assertThatPlan(isLinux(expected));
  }

  @Test void testWriteSortExchangeWithRandomDistribution() {  // testWriteSortExchangeWithRandomDistribution方法：测试随机分布的排序交换的JSON序列化
    final Function<RelBuilder, RelNode> relFn = b ->
        createSortPlan(b, RelDistributions.RANDOM_DISTRIBUTED);
    final String expected = ""
        + "LogicalSortExchange(distribution=[random], collation=[[0]])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatPlan(isLinux(expected));
  }

  @Test void testTableModifyInsert() {  // testTableModifyInsert方法：测试表修改操作（INSERT）的JSON序列化和反序列化
    final Function<RelBuilder, RelNode> relFn = b ->
        b.scan("EMP")
        .project(b.fields(), ImmutableList.of(), true)
            .let(b2 -> {
              final RelNode input = b2.build();
              final RelOptTable table =
                  requireNonNull(input.getInput(0).getTable());
              final Prepare.CatalogReader schema =
                  (Prepare.CatalogReader)
                      requireNonNull(table.getRelOptSchema());
              final LogicalTableModify modify =
                  LogicalTableModify.create(table,
                      schema,
                      input,
                      TableModify.Operation.INSERT,
                      null,
                      null,
                      false);
              return b2.push(modify);
            })
        .build();
    final String expected = ""
        + "LogicalTableModify(table=[[scott, EMP]], operation=[INSERT], flattened=[false])\n"
        + "  LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], HIREDATE=[$4], SAL=[$5], "
        + "COMM=[$6], DEPTNO=[$7])\n"
        + "    LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatPlan(isLinux(expected));
  }

  @Test void testTableModifyUpdate() {  // testTableModifyUpdate方法：测试表修改操作（UPDATE）的JSON序列化和反序列化
    final Function<RelBuilder, RelNode> relFn = b ->
        b.scan("EMP")
            .filter(
                b.equals(b.field("JOB"), b.literal("c")))
            .let(b2 -> {
              final RelNode filter = b2.build();
              final RelOptTable table =
                  requireNonNull(filter.getInput(0).getTable());
              final Prepare.CatalogReader schema =
                  (Prepare.CatalogReader)
                      requireNonNull(table.getRelOptSchema());
              final LogicalTableModify modify =
                  LogicalTableModify.create(table,
                      schema,
                      filter,
                      TableModify.Operation.UPDATE,
                      ImmutableList.of("ENAME"),
                      ImmutableList.of(b2.literal("a")),
                      false);
              return b2.push(modify);
            })
            .build();
    final String expected = ""
        + "LogicalTableModify(table=[[scott, EMP]], operation=[UPDATE], updateColumnList=[[ENAME]],"
        + " sourceExpressionList=[['a']], flattened=[false])\n"
        + "  LogicalFilter(condition=[=($2, 'c')])\n"
        + "    LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatPlan(isLinux(expected));
  }

  @Test void testTableModifyDelete() {  // testTableModifyDelete方法：测试表修改操作（DELETE）的JSON序列化和反序列化
    final Function<RelBuilder, RelNode> relFn = b ->
        b.scan("EMP")
            .filter(b.equals(b.field("JOB"), b.literal("c")))
            .let(b2 -> {
              final RelNode filter = b2.build();
              final RelOptTable table =
                  requireNonNull(filter.getInput(0).getTable());
              final Prepare.CatalogReader schema =
                  (Prepare.CatalogReader)
                      requireNonNull(table.getRelOptSchema());
              LogicalTableModify modify =
                  LogicalTableModify.create(table,
                      schema,
                      filter,
                      TableModify.Operation.DELETE,
                      null,
                      null,
                      false);
              return b2.push(modify);
            })
            .build();
    final String expected = ""
        + "LogicalTableModify(table=[[scott, EMP]], operation=[DELETE], flattened=[false])\n"
        + "  LogicalFilter(condition=[=($2, 'c')])\n"
        + "    LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatPlan(isLinux(expected));
  }

  @Test void testTableModifyMerge() {  // testTableModifyMerge方法：测试表修改操作（MERGE）的JSON序列化和反序列化
    final Holder<RelOptTable> emp = Holder.empty();
    final Holder<RelOptTable> dept = Holder.empty();
    final Function<RelBuilder, RelNode> relFn = b ->
        b.scan("DEPT")
            .let(b2 -> {
              dept.set(requireNonNull(b2.peek().getTable()));
              return b2;
            })
            .scan("EMP")
            .let(b2 -> {
              emp.set(requireNonNull(b2.peek().getTable()));
              return b2;
            })
            .join(JoinRelType.LEFT,
                b.equals(b.field(2, 0, "DEPTNO"), b.field(2, 1, "DEPTNO")))
            .project(b.literal(0),
                b.literal("x"),
                b.literal("x"),
                b.literal(0),
                b.literal("20200501 10:00:00"),
                b.literal(0),
                b.literal(0),
                b.literal(0),
                b.literal("false"),
                b.field(1, 0, 2),
                b.field(1, 0, 3),
                b.field(1, 0, 4),
                b.field(1, 0, 5),
                b.field(1, 0, 6),
                b.field(1, 0, 7),
                b.field(1, 0, 8),
                b.field(1, 0, 9),
                b.field(1, 0, 10),
                b.literal("a"))
            .let(b2 -> {
              // For SQL:
              //   MERGE INTO emp USING dept ON emp.deptno = dept.deptno
              //   WHEN MATCHED THEN
              //     UPDATE SET job = 'a'
              //   WHEN NOT MATCHED THEN
              //     INSERT VALUES (0, 'x', 'x', 0, '20200501 10:00:00',
              //         0, 0, 0, 0)
              final RelNode project = b.build();
              final Prepare.CatalogReader schema =
                  (Prepare.CatalogReader)
                      requireNonNull(emp.get().getRelOptSchema());
              LogicalTableModify modify =
                  LogicalTableModify.create(emp.get(),
                      schema,
                      project,
                      TableModify.Operation.MERGE,
                      ImmutableList.of("ENAME"),
                      null,
                      false);
              return b2.push(modify);
            })
            .build();
    final String expected = ""
        + "LogicalTableModify(table=[[scott, EMP]], operation=[MERGE], "
        + "updateColumnList=[[ENAME]], flattened=[false])\n"
        + "  LogicalProject($f0=[0], $f1=['x'], $f2=['x'], $f3=[0], $f4=['20200501 10:00:00'], "
        + "$f5=[0], $f6=[0], $f7=[0], $f8=['false'], LOC=[$2], EMPNO=[$3], ENAME=[$4], JOB=[$5], "
        + "MGR=[$6], HIREDATE=[$7], SAL=[$8], COMM=[$9], DEPTNO=[$10], $f18=['a'])\n"
        + "    LogicalJoin(condition=[=($0, $10)], joinType=[left])\n"
        + "      LogicalTableScan(table=[[scott, DEPT]])\n"
        + "      LogicalTableScan(table=[[scott, EMP]])\n";
    relFn(relFn)
        .assertThatPlan(isLinux(expected));
  }

  private RelNode createSortPlan(RelBuilder builder, RelDistribution distribution) {  // createSortPlan方法：创建排序交换计划
    return builder.scan("EMP")  // 扫描EMP表
            .sortExchange(distribution,  // 创建排序交换节点，使用指定的分布策略
                RelCollations.of(0))  // 按第0列排序
            .build();  // 构建关系表达式
  }

  /** Test fixture. */
  static class Fixture {  // Fixture类：测试夹具类，用于构建和测试关系表达式
    final Function<RelBuilder, RelNode> relFn;  // 关系表达式构建函数
    final boolean distribution;  // 是否启用分布特征
    final SqlExplainFormat format;  // 解释格式

    Fixture(Function<RelBuilder, RelNode> relFn, boolean distribution,  // Fixture构造方法：创建测试夹具
        SqlExplainFormat format) {
      this.relFn = relFn;  // 初始化关系表达式构建函数
      this.distribution = distribution;  // 初始化分布特征标志
      this.format = format;  // 初始化解释格式
    }

    Fixture withDistribution(boolean distribution) {  // withDistribution方法：设置分布特征并返回新的夹具
      if (distribution == this.distribution) {
        return this;
      }
      return new Fixture(relFn, distribution, format);
    }

    Fixture withFormat(SqlExplainFormat format) {  // withFormat方法：设置解释格式并返回新的夹具
      if (format == this.format) {
        return this;
      }
      return new Fixture(relFn, distribution, format);
    }

    Fixture assertThatJson(Matcher<String> matcher) {  // assertThatJson方法：断言JSON输出与匹配器一致
      final FrameworkConfig config = RelBuilderTest.config().build();  // 构建框架配置
      final RelBuilder b = RelBuilder.create(config);  // 创建关系表达式构建器
      RelNode rel = relFn.apply(b);  // 应用构建函数创建关系表达式
      final String relJson =
          RelOptUtil.dumpPlan("", rel, SqlExplainFormat.JSON,  // 转储执行计划为JSON格式
              SqlExplainLevel.EXPPLAN_ATTRIBUTES);
      assertThat(relJson, matcher);  // 断言JSON字符串与匹配器一致
      return this;  // 返回当前夹具以支持链式调用
    }

    @SuppressWarnings("UnusedReturnValue")
    Fixture assertThatPlan(Matcher<String> matcher) {  // assertThatPlan方法：断言执行计划与匹配器一致
      final FrameworkConfig config = RelBuilderTest.config().build();  // 构建框架配置
      final RelBuilder b = RelBuilder.create(config);  // 创建关系表达式构建器
      RelNode rel = relFn.apply(b);  // 应用构建函数创建关系表达式
      final String relJson =
          RelOptUtil.dumpPlan("", rel, SqlExplainFormat.JSON,  // 转储执行计划为JSON格式
              SqlExplainLevel.EXPPLAN_ATTRIBUTES);
      final String plan;
      if (distribution) {  // 如果启用分布特征
        VolcanoPlanner planner = new VolcanoPlanner();  // 创建火山规划器
        planner.addRelTraitDef(RelDistributionTraitDef.INSTANCE);  // 添加分布特征定义
        RelOptCluster cluster =
            RelOptCluster.create(planner, b.getRexBuilder());  // 创建优化集群
        plan = deserializeAndDump(cluster, getSchema(rel), relJson, format);  // 使用集群反序列化并转储
      } else {
        plan = deserializeAndDump(getSchema(rel), relJson, format);  // 直接反序列化并转储
      }
      assertThat(plan, matcher);  // 断言执行计划与匹配器一致
      return this;  // 返回当前夹具以支持链式调用
    }
  }
}
