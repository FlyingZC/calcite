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
// Apache许可证声明,说明该代码遵循Apache 2.0许可证,可以免费使用和修改
package org.apache.calcite.test; // 包声明,该类属于org.apache.calcite.test测试包

import org.apache.calcite.avatica.util.DateTimeUtils; // 导入Calcite的日期时间工具类,用于时间戳转换

import com.datastax.oss.driver.api.core.CqlSession; // 导入Cassandra驱动核心会话类,用于与Cassandra数据库交互
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs; // 导入Cassandra类型编解码器,用于数据类型转换
import com.google.common.collect.ImmutableMap; // 导入Google Guava的不可变Map类,用于存储配置信息

import org.cassandraunit.CQLDataLoader; // 导入CassandraUnit测试工具的数据加载器,用于加载CQL脚本
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet; // 导入CassandraUnit的类路径CQL数据集,用于从类路径加载CQL文件
import org.junit.jupiter.api.BeforeAll; // 导入JUnit5的BeforeAll注解,标记在所有测试方法执行前运行一次的方法
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解,标记测试方法
import org.junit.jupiter.api.extension.ExtendWith; // 导入JUnit5的ExtendWith注解,用于注册测试扩展
import org.junit.jupiter.api.parallel.Execution; // 导入JUnit5的Execution注解,用于配置测试执行模式
import org.junit.jupiter.api.parallel.ExecutionMode; // 导入JUnit5的ExecutionMode枚举,定义测试执行模式

import static java.util.Objects.requireNonNull; // 导入Java Objects工具类的静态方法,用于空值检查

/**
 * Tests for the {@code org.apache.calcite.adapter.cassandra} package related to data types.
 * 针对{@code org.apache.calcite.adapter.cassandra}包中数据类型相关的测试类
 *
 * <p>Will start embedded cassandra cluster and populate it from local {@code datatypes.cql} file.
 * 该测试将启动嵌入式Cassandra集群,并从本地的{@code datatypes.cql}文件中加载数据
 * All configuration files are located in test classpath.
 * 所有配置文件都位于测试类路径中
 *
 * <p>Note that tests will be skipped if running on JDK11+
 * 注意:如果在JDK11+版本上运行,测试将被跳过
 * (which is not yet supported by cassandra) see
 * (因为Cassandra还不支持JDK11+版本) 请参考
 * <a href="https://issues.apache.org/jira/browse/CASSANDRA-9608">CASSANDRA-9608</a>.
 * 该JIRA链接提供了详细的版本支持说明
 *
 */
@Execution(ExecutionMode.SAME_THREAD) // 设置测试执行模式为同一线程,确保测试顺序执行,避免并发问题
@ExtendWith(CassandraExtension.class) // 使用CassandraExtension扩展,该扩展负责启动和管理嵌入式Cassandra集群
class CassandraAdapterDataTypesTest { // 测试类定义,用于测试Cassandra适配器的数据类型映射

  /** Connection factory based on the "mongo-zips" model. */
  // 基于数据类型模型的连接工厂配置,使用不可变Map存储Cassandra连接配置信息
  // 该配置从/model-datatypes.json文件中读取,包含了测试所需的表结构和数据映射
  private static final ImmutableMap<String, String> DTCASSANDRA =
          CassandraExtension.getDataset("/model-datatypes.json"); // 通过CassandraExtension获取数据集配置,返回包含表名、列名等元数据的映射

  @BeforeAll // JUnit5注解,标记该方法在所有测试方法执行前只运行一次
  static void load(CqlSession session) { // 静态加载方法,接收Cassandra会话对象作为参数
    // 创建CQL数据加载器,使用传入的Cassandra会话
    // 从类路径加载datatypes.cql文件,该文件包含创建表和插入测试数据的CQL语句
    // 这些语句会在嵌入式Cassandra集群中执行,初始化测试环境
    new CQLDataLoader(session)
        .load(new ClassPathCQLDataSet("datatypes.cql")); // 加载CQL脚本文件,执行其中的DDL和DML语句
  }

  @Test // JUnit5注解,标记这是一个测试方法
  void testSimpleTypesRowType() { // 测试方法:验证简单数据类型的行类型映射是否正确
    // 使用Calcite断言工具链式调用
    // that():创建CalciteAssert实例
    // with(DTCASSANDRA):使用之前定义的Cassandra数据集配置
    // query("select * from \"test_simple\""):执行SQL查询,查询test_simple表的所有数据
    // typeIs(...):验证查询结果的行类型(字段类型)是否与预期一致
    CalciteAssert.that()
            .with(DTCASSANDRA)
            .query("select * from \"test_simple\"")
            .typeIs("[f_int INTEGER" // 验证f_int字段映射为INTEGER类型
                + ", f_ascii VARCHAR" // 验证f_ascii字段映射为VARCHAR类型(Cassandra的ASCII类型)
                + ", f_bigint BIGINT" // 验证f_bigint字段映射为BIGINT类型
                + ", f_blob VARBINARY" // 验证f_blob字段映射为VARBINARY类型(二进制数据)
                + ", f_boolean BOOLEAN" // 验证f_boolean字段映射为BOOLEAN类型
                + ", f_date DATE" // 验证f_date字段映射为DATE类型
                + ", f_decimal DOUBLE" // 验证f_decimal字段映射为DOUBLE类型(注意:这里用DOUBLE而不是DECIMAL)
                + ", f_double DOUBLE" // 验证f_double字段映射为DOUBLE类型
                + ", f_duration ANY" // 验证f_duration字段映射为ANY类型(不支持的类型用ANY表示)
                + ", f_float REAL" // 验证f_float字段映射为REAL类型(单精度浮点数)
                + ", f_inet ANY" // 验证f_inet字段映射为ANY类型(IP地址类型,Calcite中用ANY表示)
                + ", f_int_null INTEGER" // 验证f_int_null字段映射为INTEGER类型(可为空的整型)
                + ", f_smallint SMALLINT" // 验证f_smallint字段映射为SMALLINT类型
                + ", f_text VARCHAR" // 验证f_text字段映射为VARCHAR类型(文本类型)
                + ", f_time BIGINT" // 验证f_time字段映射为BIGINT类型(时间以纳秒形式存储)
                + ", f_timestamp TIMESTAMP" // 验证f_timestamp字段映射为TIMESTAMP类型
                + ", f_timeuuid CHAR" // 验证f_timeuuid字段映射为CHAR类型(时间UUID)
                + ", f_tinyint TINYINT" // 验证f_tinyint字段映射为TINYINT类型
                + ", f_uuid CHAR" // 验证f_uuid字段映射为CHAR类型(UUID)
                + ", f_varchar VARCHAR" // 验证f_varchar字段映射为VARCHAR类型
                + ", f_varint INTEGER]"); // 验证f_varint字段映射为INTEGER类型(任意精度整数)
  }

  @Test // JUnit5注解,标记这是一个测试方法
  void testFilterWithNonStringLiteral() { // 测试方法:验证使用非字符串字面量进行过滤的功能
    // 测试1:使用整数字面量等于号过滤
    // 查询test_type表中f_id等于1的记录
    // 期望返回空字符串(表示没有匹配的记录)
    CalciteAssert.that()
        .with(DTCASSANDRA)
        .query("select * from \"test_type\" where \"f_id\" = 1")
        .returns(""); // 断言结果为空

    // 测试2:使用整数字面量大于号过滤
    // 查询test_type表中f_id大于1的记录
    // 期望返回一条记录,f_id=3000000000,f_user=ANNA
    // 这验证了Cassandra适配器能正确处理数值比较和过滤
    CalciteAssert.that()
        .with(DTCASSANDRA)
        .query("select * from \"test_type\" where \"f_id\" > 1")
        .returns("f_id=3000000000; f_user=ANNA\n"); // 断言返回指定格式的记录

    // 测试3:使用日期字符串字面量过滤
    // 查询test_date_type表中f_date等于'2015-05-03'的记录
    // 验证日期字面量的正确解析和比较
    CalciteAssert.that()
        .with(DTCASSANDRA)
        .query("select * from \"test_date_type\" where \"f_date\" = '2015-05-03'")
        .returns("f_date=2015-05-03; f_user=ANNA\n"); // 断言返回指定日期的记录

    // 测试4:使用带时区转换的时间戳过滤
    // 查询test_timestamp_type表,将f_timestamp转换为带时区的时间戳后比较
    // cast(... as timestamp with local time zone):类型转换,考虑本地时区
    // 验证时间戳类型转换和时区处理的正确性
    CalciteAssert.that()
        .with(DTCASSANDRA)
        .query("select * from \"test_timestamp_type\" where cast(\"f_timestamp\" as timestamp "
            + "with local time zone) = '2011-02-03 04:05:00 UTC'")
        .returns("f_timestamp=2011-02-03 04:05:00; f_user=ANNA\n"); // 断言返回指定时间戳的记录

    // 测试5:直接使用时间戳字符串字面量过滤(不带类型转换)
    // 查询test_timestamp_type表中f_timestamp等于指定时间戳字符串的记录
    // 验证时间戳字面量的直接解析能力
    CalciteAssert.that()
        .with(DTCASSANDRA)
        .query("select * from \"test_timestamp_type\" where \"f_timestamp\""
            + " = '2011-02-03 04:05:00'")
        .returns("f_timestamp=2011-02-03 04:05:00; f_user=ANNA\n"); // 断言返回指定时间戳的记录
  }

  @Test // JUnit5注解,标记这是一个测试方法
  void testSimpleTypesValues() { // 测试方法:验证简单数据类型的实际值是否正确读取和转换
    // 查询test_simple表的所有数据,验证每个字段的实际值是否正确
    // returns()方法验证返回的值是否与预期完全匹配
    CalciteAssert.that()
        .with(DTCASSANDRA)
        .query("select * from \"test_simple\"")
        .returns("f_int=0" // 验证整数值为0
            + "; f_ascii=abcdefg" // 验证ASCII字符串值为abcdefg
            + "; f_bigint=3000000000" // 验证大整数值为3000000000(超过32位整数范围)
            + "; f_blob=20" // 验证二进制数据显示为20(可能是字节数组的十六进制表示)
            + "; f_boolean=true" // 验证布尔值为true
            + "; f_date=2015-05-03" // 验证日期值为2015-05-03
            + "; f_decimal=2.1" // 验证十进制值为2.1
            + "; f_double=2.0" // 验证双精度浮点数为2.0
            + "; f_duration=89h9m9s" // 验证时间段值为89小时9分9秒
            + "; f_float=5.1" // 验证单精度浮点数为5.1
            + "; f_inet=/192.168.0.1" // 验证IP地址值为192.168.0.1(前缀/表示网络地址)
            + "; f_int_null=null" // 验证整型null值正确处理
            + "; f_smallint=5" // 验证短整数值为5
            + "; f_text=abcdefg" // 验证文本值为abcdefg
            + "; f_time=48654234000000" // 验证时间值为48654234000000(纳秒表示)
            + "; f_timestamp=2011-02-03 04:05:00" // 验证时间戳值为2011-02-03 04:05:00
            + "; f_timeuuid=8ac6d1dc-fbeb-11e9-8f0b-362b9e155667" // 验证时间UUID值为指定的UUID字符串
            + "; f_tinyint=0" // 验证微整数值为0
            + "; f_uuid=123e4567-e89b-12d3-a456-426655440000" // 验证UUID值为指定的UUID字符串
            + "; f_varchar=abcdefg" // 验证varchar值为abcdefg
            + "; f_varint=10\n"); // 验证任意精度整数值为10
  }

  @Test // JUnit5注解,标记这是一个测试方法
  void testCounterRowType() { // 测试方法:验证Counter(计数器)类型的行类型映射
    // 查询test_counter表的所有数据,验证counter类型的字段映射
    // Counter是Cassandra的特殊类型,只能递增或递减,不能直接赋值
    CalciteAssert.that()
            .with(DTCASSANDRA)
            .query("select * from \"test_counter\"")
            .typeIs("[f_int INTEGER, f_counter BIGINT]"); // 验证f_int映射为INTEGER,f_counter映射为BIGINT
  }

  @Test // JUnit5注解,标记这是一个测试方法
  void testCounterValues() { // 测试方法:验证Counter类型的实际值读取
    // 查询test_counter表的所有数据,验证counter字段的实际值
    CalciteAssert.that()
        .with(DTCASSANDRA)
        .query("select * from \"test_counter\"")
        .returns("f_int=1; f_counter=1\n"); // 验证f_int=1,f_counter=1
  }

  @Test // JUnit5注解,标记这是一个测试方法
  void testCollectionsRowType() { // 测试方法:验证集合类型的行类型映射
    // 查询test_collections表的所有数据,验证集合类型的字段映射
    // Cassandra支持四种集合类型:List, Map, Set, Tuple
    CalciteAssert.that()
            .with(DTCASSANDRA)
            .query("select * from \"test_collections\"")
            .typeIs("[f_int INTEGER" // 普通整型字段
                + ", f_list INTEGER ARRAY" // List类型映射为INTEGER ARRAY(整数数组)
                + ", f_map (VARCHAR, VARCHAR) MAP" // Map类型映射为(VARCHAR, VARCHAR) MAP(键值对都是字符串)
                + ", f_set DOUBLE MULTISET" // Set类型映射为DOUBLE MULTISET(双精度浮点数多重集)
                + ", f_tuple STRUCT]"); // Tuple类型映射为STRUCT(结构体)
  }

  @Test // JUnit5注解,标记这是一个测试方法
  void testCollectionsValues() { // 测试方法:验证集合类型的实际值读取和格式化
    // 查询test_collections表的所有数据,验证集合字段的实际值和显示格式
    CalciteAssert.that()
        .with(DTCASSANDRA)
        .query("select * from \"test_collections\"")
        .returns("f_int=0" // 整型值
            + "; f_list=[1, 2, 3]" // List显示为[1, 2, 3]格式
            + "; f_map={k1=v1, k2=v2}" // Map显示为{k1=v1, k2=v2}格式
            + "; f_set=[2.0, 3.1]" // Set显示为[2.0, 3.1]格式(虽然是无序的,但显示为数组形式)
            + "; f_tuple={3000000000, 30ff87, 2015-05-03 13:30:54.234}" // Tuple显示为{值1, 值2, 值3}格式
            + "\n"); // 包含三个元素:bigint, blob, timestamp
  }

  @Test // JUnit5注解,标记这是一个测试方法
  void testCollectionsInnerRowType() { // 测试方法:验证集合内部元素类型的行类型映射
    // 查询集合内部元素,验证访问集合元素后的类型映射
    // "f_list"[1]:访问List数组的第2个元素(索引从0开始)
    // "f_map"['k1']:访问Map中键为'k1'的值
    // "test_collections"."f_tuple"."1":访问Tuple的第2个元素(使用数字索引)
    CalciteAssert.that()
        .with(DTCASSANDRA)
        .query("select \"f_list\"[1], " // 查询List的第2个元素
            + "\"f_map\"['k1'], " // 查询Map中键为k1的值
            + "\"test_collections\".\"f_tuple\".\"1\", " // 查询Tuple的第2个元素
            + "\"test_collections\".\"f_tuple\".\"2\", " // 查询Tuple的第3个元素
            + "\"test_collections\".\"f_tuple\".\"3\"" // 查询Tuple的第4个元素
            + " from \"test_collections\"")
        .typeIs("[EXPR$0 INTEGER" // List元素映射为INTEGER类型(EXPR$0是自动生成的列名)
            + ", EXPR$1 VARCHAR" // Map值映射为VARCHAR类型
            + ", 1 BIGINT" // Tuple第2个元素映射为BIGINT类型(列名为数字索引1)
            + ", 2 VARBINARY" // Tuple第3个元素映射为VARBINARY类型(列名为数字索引2)
            + ", 3 TIMESTAMP]"); // Tuple第4个元素映射为TIMESTAMP类型(列名为数字索引3)
  }

  @Test // JUnit5注解,标记这是一个测试方法
  void testCollectionsInnerValues() { // 测试方法:验证集合内部元素的实际值
    // timestamp retrieval depends on the user timezone, we must compute the expected result
    // 时间戳的获取依赖于用户的时区,必须计算期望的结果
    // 使用Cassandra的TypeCodecs解析时间戳字符串,转换为毫秒时间戳
    long v =
        requireNonNull(TypeCodecs.TIMESTAMP.parse("'2015-05-03 13:30:54.234'")) // 解析时间戳字符串,返回Instant对象,requireNonNull确保非空
            .toEpochMilli(); // 将Instant转换为从1970-01-01T00:00:00Z开始的毫秒数
    // 将毫秒时间戳转换为Calcite的字符串格式,考虑时区信息
    String expectedTimestamp = DateTimeUtils.unixTimestampToString(v); // 使用Calcite的工具类转换为可读的时间戳字符串

    // 查询集合内部元素,验证实际值是否正确
    CalciteAssert.that()
        .with(DTCASSANDRA)
        .query("select \"f_list\"[1], " // 查询List的第2个元素,期望值为1
            + "\"f_map\"['k1'], " // 查询Map中键为k1的值,期望值为v1
            + "\"test_collections\".\"f_tuple\".\"1\", " // 查询Tuple的第2个元素,期望值为3000000000
            + "\"test_collections\".\"f_tuple\".\"2\", " // 查询Tuple的第3个元素,期望值为30ff87(blob的十六进制表示)
            + "\"test_collections\".\"f_tuple\".\"3\"" // 查询Tuple的第4个元素,期望值为转换后的时间戳字符串
            + " from \"test_collections\"")
        .returns("EXPR$0=1" // List的第2个元素值为1
            + "; EXPR$1=v1" // Map中k1对应的值为v1
            + "; 1=3000000000" // Tuple的第2个元素值为3000000000
            + "; 2=30ff87" // Tuple的第3个元素值为30ff87(十六进制blob)
            + "; 3=" + expectedTimestamp + "\n"); // Tuple的第4个元素值为转换后的时间戳
  }

  // frozen collections should not affect the row type
  // frozen(冻结)集合不应该影响行类型映射
  // frozen集合是Cassandra的特性,表示集合内容不可修改,但在Calcite中的类型映射应该与非frozen集合相同
  @Test // JUnit5注解,标记这是一个测试方法
  void testFrozenCollectionsRowType() { // 测试方法:验证frozen集合类型的行类型映射
    // 查询test_frozen_collections表的所有数据,验证frozen集合的类型映射
    // frozen关键字不应该改变集合在Calcite中的类型表示
    CalciteAssert.that()
        .with(DTCASSANDRA)
        .query("select * from \"test_frozen_collections\"")
        .typeIs("[f_int INTEGER" // 普通整型字段
            + ", f_list INTEGER ARRAY" // frozen的List仍然映射为INTEGER ARRAY
            + ", f_map (VARCHAR, VARCHAR) MAP" // frozen的Map仍然映射为(VARCHAR, VARCHAR) MAP
            + ", f_set DOUBLE MULTISET" // frozen的Set仍然映射为DOUBLE MULTISET
            + ", f_tuple STRUCT]"); // frozen的Tuple仍然映射为STRUCT
    // we should test (BIGINT, VARBINARY, TIMESTAMP) STRUCT but inner types are not exposed
    // 我们应该测试(BIGINT, VARBINARY, TIMESTAMP) STRUCT,但内部类型未暴露
    // 注释说明:frozen tuple的内部类型(BIGINT, VARBINARY, TIMESTAMP)无法直接测试,因为类型信息未完全暴露
  }

  // frozen collections should not affect the result set
  // frozen集合不应该影响结果集的显示
  @Test // JUnit5注解,标记这是一个测试方法
  void testFrozenCollectionsValues() { // 测试方法:验证frozen集合类型的实际值读取
    // 查询test_frozen_collections表的所有数据,验证frozen集合的值读取
    // frozen关键字不应该影响数据的读取和显示格式
    CalciteAssert.that()
        .with(DTCASSANDRA)
        .query("select * from \"test_frozen_collections\"")
        .returns("f_int=0" // 整型值
            + "; f_list=[1, 2, 3]" // frozen的List值显示为[1, 2, 3]
            + "; f_map={k1=v1, k2=v2}" // frozen的Map值显示为{k1=v1, k2=v2}
            + "; f_set=[2.0, 3.1]" // frozen的Set值显示为[2.0, 3.1]
            + "; f_tuple={3000000000, 30ff87, 2015-05-03 13:30:54.234}" // frozen的Tuple值显示为{3000000000, 30ff87, 2015-05-03 13:30:54.234}
            + "\n"); // 值格式与非frozen集合完全相同
  }
}