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
package org.apache.calcite.adapter.mongodb; // 包声明：MongoDB适配器测试类所在的包

import org.apache.calcite.schema.Schema; // 导入Schema接口：Calcite中的Schema定义
import org.apache.calcite.schema.SchemaFactory; // 导入SchemaFactory接口：用于创建Schema的工厂接口
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口：Schema的扩展接口，允许添加子Schema
import org.apache.calcite.test.CalciteAssert; // 导入CalciteAssert：Calcite测试工具类，用于构建测试断言
import org.apache.calcite.test.MongoAssertions; // 导入MongoAssertions：MongoDB特定的断言工具
import org.apache.calcite.util.Bug; // 导入Bug类：用于跟踪已知bug的工具类
import org.apache.calcite.util.TestUtil; // 导入TestUtil类：测试工具类
import org.apache.calcite.util.Util; // 导入Util类：通用工具类

import com.google.common.io.LineProcessor; // 导入LineProcessor：Guava库中的行处理器接口
import com.google.common.io.Resources; // 导入Resources：Guava库中的资源读取工具
import com.mongodb.client.MongoCollection; // 导入MongoCollection：MongoDB的集合接口
import com.mongodb.client.MongoDatabase; // 导入MongoDatabase：MongoDB的数据库接口

import net.hydromatic.foodmart.data.json.FoodmartJson; // 导入FoodmartJson：FoodMart测试数据的JSON资源

import org.bson.BsonArray; // 导入BsonArray：BSON数组类型
import org.bson.BsonBinary; // 导入BsonBinary：BSON二进制数据类型
import org.bson.BsonDateTime; // 导入BsonDateTime：BSON日期时间类型
import org.bson.BsonDocument; // 导入BsonDocument：BSON文档类型
import org.bson.BsonInt32; // 导入BsonInt32：BSON 32位整数类型
import org.bson.BsonString; // 导入BsonString：BSON字符串类型
import org.bson.Document; // 导入Document：MongoDB文档类型
import org.bson.json.JsonWriterSettings; // 导入JsonWriterSettings：JSON写入设置
import org.junit.jupiter.api.BeforeAll; // 导入BeforeAll：JUnit 5注解，在所有测试方法执行前执行一次
import org.junit.jupiter.api.Disabled; // 导入Disabled：JUnit 5注解，用于禁用测试方法
import org.junit.jupiter.api.Test; // 导入Test：JUnit 5注解，标记测试方法
import org.junit.jupiter.api.extension.RegisterExtension; // 导入RegisterExtension：JUnit 5注解，用于注册扩展

import java.io.IOException; // 导入IOException：IO异常类
import java.io.UncheckedIOException; // 导入UncheckedIOException：未检查的IO异常类
import java.net.URL; // 导入URL：统一资源定位符类
import java.nio.charset.StandardCharsets; // 导入StandardCharsets：标准字符集
import java.sql.SQLException; // 导入SQLException：SQL异常类
import java.time.Instant; // 导入Instant：时间戳类
import java.time.LocalDate; // 导入LocalDate：本地日期类
import java.time.ZoneOffset; // 导入ZoneOffset：时区偏移量类
import java.util.Arrays; // 导入Arrays：数组工具类
import java.util.List; // 导入List：列表接口
import java.util.Locale; // 导入Locale：区域设置类
import java.util.Map; // 导入Map：映射接口
import java.util.Objects; // 导入Objects：对象工具类
import java.util.function.Consumer; // 导入Consumer：消费者函数式接口
import java.util.function.Function; // 导入Function：函数式接口
import java.util.stream.Collectors; // 导入Collectors：流收集器工具类

import static org.hamcrest.CoreMatchers.is; // 导入is：Hamcrest匹配器，判断相等
import static org.hamcrest.CoreMatchers.nullValue; // 导入nullValue：Hamcrest匹配器，判断为null
import static org.hamcrest.MatcherAssert.assertThat; // 导入assertThat：Hamcrest断言方法
import static org.junit.jupiter.api.Assertions.fail; // 导入fail：JUnit断言失败方法

import static java.util.Objects.requireNonNull; // 导入requireNonNull：对象非空检查方法

/**
 * Testing mongo adapter functionality. By default, runs with
 * Mongo Java Server unless {@code IT} maven profile is enabled
 * (via {@code $ mvn -Pit install}).
 *
 * @see MongoDatabasePolicy
 */ // 类注释：测试MongoDB适配器功能的测试类。默认情况下使用Mongo Java Server运行，除非启用了IT Maven配置文件（通过$ mvn -Pit install命令）。该类实现了SchemaFactory接口，用于创建MongoDB Schema。
public class MongoAdapterTest implements SchemaFactory { // 类定义：MongoDB适配器测试类，实现SchemaFactory接口以提供Schema创建功能

  /** Connection factory based on the "mongo-zips" model. */ // 成员变量注释：基于"mongo-zips"模型的连接工厂，存储模型配置文件的URL
  protected static final URL MODEL = // 成员变量定义：受保护的静态常量URL，存储MongoDB模型配置文件的路径
      requireNonNull(MongoAdapterTest.class.getResource("/mongo-model.json"), // 获取类路径下的mongo-model.json资源文件URL，如果为null则抛出NullPointerException
          "url"); // requireNonNull的第二个参数，用于NullPointerException的错误消息

  /** Number of records in local file. */ // 成员变量注释：本地文件中的记录数量，表示zips集合中的文档数量
  protected static final int ZIPS_SIZE = 149; // 成员变量定义：受保护的静态常量整数，表示zips集合中有149条记录

  @RegisterExtension // JUnit 5注解：注册扩展，用于在测试生命周期中管理MongoDatabasePolicy
  public static final MongoDatabasePolicy POLICY = MongoDatabasePolicy.create(); // 成员变量定义：公共静态常量MongoDatabasePolicy，通过create()方法创建MongoDB数据库策略实例，用于管理测试数据库的生命周期

  private static MongoSchema schema; // 成员变量定义：私有静态MongoSchema实例，用于缓存MongoDB Schema以避免重复初始化开销

  @BeforeAll // JUnit 5注解：在所有测试方法执行前执行一次的设置方法
  public static void setUp() throws Exception { // 方法定义：公共静态设置方法，抛出异常，用于初始化测试环境
    MongoDatabase database = POLICY.database(); // 获取MongoDatabase实例：从POLICY中获取MongoDB数据库连接

    populate(database.getCollection("zips"), // 填充zips集合：从zips-mini.json文件加载邮政编码数据到MongoDB的zips集合中
        requireNonNull(MongoAdapterTest.class.getResource("/zips-mini.json"), // 获取zips-mini.json资源文件URL，确保不为null
            "url")); // requireNonNull的错误消息参数
    populate(database.getCollection("store"), // 填充store集合：从store.json文件加载商店数据到MongoDB的store集合中
        requireNonNull(FoodmartJson.class.getResource("/store.json"), // 获取store.json资源文件URL，确保不为null
            "url")); // requireNonNull的错误消息参数
    populate(database.getCollection("warehouse"), // 填充warehouse集合：从warehouse.json文件加载仓库数据到MongoDB的warehouse集合中
        requireNonNull(FoodmartJson.class.getResource("/warehouse.json"), // 获取warehouse.json资源文件URL，确保不为null
            "url")); // requireNonNull的错误消息参数

    // Manually insert data for data-time test. // 注释：手动插入数据用于日期时间类型测试
    MongoCollection<BsonDocument> datatypes = database.getCollection("datatypes") // 获取datatypes集合：从数据库中获取名为"datatypes"的MongoDB集合，指定文档类型为BsonDocument
        .withDocumentClass(BsonDocument.class); // 设置文档类：指定集合的文档类型为BsonDocument，以便使用BSON类型操作
    if (datatypes.countDocuments() > 0) { // 条件判断：如果datatypes集合中已有文档，则清空该集合以确保测试环境干净
      datatypes.deleteMany(new BsonDocument()); // 删除所有文档：使用空BsonDocument作为过滤条件删除集合中的所有文档
    }

    BsonDocument doc = new BsonDocument(); // 创建BSON文档：创建一个新的BsonDocument对象用于存储测试数据
    Instant instant = LocalDate.of(2012, 9, 5).atStartOfDay(ZoneOffset.UTC).toInstant(); // 创建时间戳：创建2012年9月5日UTC时区的午夜时间戳
    doc.put("date", new BsonDateTime(instant.toEpochMilli())); // 添加日期字段：将时间戳转换为毫秒数并作为BsonDateTime类型添加到文档中
    doc.put("value", new BsonInt32(1231)); // 添加整数字段：添加值为1231的BsonInt32类型字段
    doc.put("ownerId", new BsonString("531e7789e4b0853ddb861313")); // 添加所有者ID字段：添加MongoDB ObjectId字符串
    doc.put("arr", new BsonArray(Arrays.asList(new BsonString("a"), new BsonString("b")))); // 添加数组字段：创建包含字符串"a"和"b"的BsonArray
    doc.put("binaryData", new BsonBinary("binaryData".getBytes(StandardCharsets.UTF_8))); // 添加二进制数据字段：将字符串"binaryData"转换为UTF-8字节数组并作为BsonBinary类型添加
    datatypes.insertOne(doc); // 插入文档：将构建的BsonDocument插入到datatypes集合中

    schema = new MongoSchema(database); // 创建MongoSchema实例：使用MongoDatabase创建MongoSchema对象并缓存到静态变量中
  }

  private static void populate(MongoCollection<Document> collection, URL resource) // 方法定义：私有静态方法，用于从资源文件填充MongoDB集合，参数为MongoDB集合和资源URL，可能抛出IOException异常
      throws IOException { // 异常声明：方法可能抛出IO异常
    requireNonNull(collection, "collection"); // 参数校验：确保collection参数不为null，否则抛出NullPointerException

    if (collection.countDocuments() > 0) { // 条件判断：如果集合中已有文档，则清空集合
      // delete any existing documents (run from a clean set) // 注释：删除任何现有文档（从干净的数据集开始运行）
      collection.deleteMany(new BsonDocument()); // 删除所有文档：使用空BsonDocument作为过滤条件删除集合中的所有文档
    }

    MongoCollection<BsonDocument> bsonCollection = collection.withDocumentClass(BsonDocument.class); // 转换集合类型：将MongoCollection<Document>转换为MongoCollection<BsonDocument>以便使用BSON类型操作
    Resources.readLines(resource, StandardCharsets.UTF_8, new LineProcessor<Void>() { // 读取资源文件：使用Guava的Resources工具读取资源文件的所有行，使用UTF-8编码，并使用自定义的LineProcessor处理每一行
      @Override public boolean processLine(String line) { // 方法重写：处理每一行文本的方法
        bsonCollection.insertOne(BsonDocument.parse(line)); // 插入文档：将JSON字符串解析为BsonDocument并插入到MongoDB集合中
        return true; // 返回值：返回true表示继续处理下一行
      }

      @Override public Void getResult() { // 方法重写：获取处理结果的方法
        return null; // 返回值：返回null，因为此处理器不需要返回结果
      }
    }); // LineProcessor匿名类结束
  }

  /** Returns always the same schema to avoid initialization costs. */ // 方法注释：始终返回相同的schema以避免初始化开销，这是SchemaFactory接口的实现方法
  @Override public Schema create(SchemaPlus parentSchema, String name, // 方法定义：实现SchemaFactory接口的create方法，参数包括父Schema、Schema名称和操作数映射
      Map<String, Object> operand) { // 参数：operand是包含Schema配置参数的Map
    return schema; // 返回值：返回缓存的静态MongoSchema实例，避免重复创建
  }

  private CalciteAssert.AssertThat assertModel(String model) { // 方法定义：私有辅助方法，根据模型字符串创建CalciteAssert断言构建器
    // ensure that Schema from this instance is being used // 注释：确保使用此实例的Schema
    model = model.replace(MongoSchemaFactory.class.getName(), MongoAdapterTest.class.getName()); // 字符串替换：将模型中的MongoSchemaFactory类名替换为MongoAdapterTest类名，以便使用测试类作为Schema工厂

    return CalciteAssert.that() // 创建断言：创建CalciteAssert实例
        .withModel(model); // 设置模型：使用指定的模型字符串配置CalciteAssert
  }

  private CalciteAssert.AssertThat assertModel(URL url) { // 方法定义：私有辅助方法，根据模型URL创建CalciteAssert断言构建器
    requireNonNull(url, "url"); // 参数校验：确保url参数不为null，否则抛出NullPointerException
    try { // 异常处理：尝试读取URL内容
      return assertModel(Resources.toString(url, StandardCharsets.UTF_8)); // 读取URL：将URL内容读取为UTF-8字符串并调用assertModel(String)方法
    } catch (IOException e) { // 捕获IO异常
      throw new UncheckedIOException(e); // 抛出未检查的IO异常：将受检的IOException包装为未检查的UncheckedIOException
    }
  }

  @Test void testSort() { // 方法定义：测试方法，测试MongoDB适配器的排序功能
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select * from zips order by state") // 执行查询：查询zips集合中所有记录并按state字段升序排序
        .returnsCount(ZIPS_SIZE) // 验证结果：验证返回的记录数为ZIPS_SIZE（149条）
        .explainContains("PLAN=MongoToEnumerableConverter\n" // 验证执行计划：验证执行计划包含MongoToEnumerableConverter
            + "  MongoSort(sort0=[$4], dir0=[ASC])\n" // 验证排序节点：验证存在MongoSort节点，按第4列（state）升序排序
            + "    MongoProject(CITY=[CAST(ITEM($0, 'city')):VARCHAR(20)], LONGITUDE=[CAST(ITEM(ITEM($0, 'loc'), 0)):FLOAT], LATITUDE=[CAST(ITEM(ITEM($0, 'loc'), 1)):FLOAT], POP=[CAST(ITEM($0, 'pop')):INTEGER], STATE=[CAST(ITEM($0, 'state')):VARCHAR(2)], ID=[CAST(ITEM($0, '_id')):VARCHAR(5)])\n" // 验证投影节点：验证存在MongoProject节点，将MongoDB文档字段映射为Calcite关系字段
            + "      MongoTableScan(table=[[mongo_raw, zips]])"); // 验证表扫描节点：验证存在MongoTableScan节点，扫描mongo_raw.zips表
  }

  @Test void testSortLimit() { // 方法定义：测试方法，测试MongoDB适配器的排序、偏移和限制功能
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select state, id from zips\n" // 执行查询：查询zips集合的state和id字段
            + "order by state, id offset 2 rows fetch next 3 rows only") // 查询条件：按state和id升序排序，跳过前2行，取接下来的3行
        .returnsOrdered("STATE=AK; ID=99801", // 验证结果：验证返回的结果按顺序匹配这些值
            "STATE=AL; ID=35215", // 第二行结果
            "STATE=AL; ID=35401") // 第三行结果
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$project: {STATE: '$state', ID: '$_id'}}", // 验证投影阶段：提取state和_id字段
                "{$sort: {STATE: 1, ID: 1}}", // 验证排序阶段：按STATE和ID升序排序
                "{$skip: 2}", // 验证跳过阶段：跳过前2条记录
                "{$limit: 3}")); // 验证限制阶段：限制返回3条记录
  }

  @Test void testOffsetLimit() { // 方法定义：测试方法，测试MongoDB适配器的偏移和限制功能（不排序）
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select state, id from zips\n" // 执行查询：查询zips集合的state和id字段
            + "offset 2 fetch next 3 rows only") // 查询条件：跳过前2行，取接下来的3行
        .runs() // 执行查询：运行查询并验证成功执行
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$skip: 2}", // 验证跳过阶段：跳过前2条记录
                "{$limit: 3}", // 验证限制阶段：限制返回3条记录
                "{$project: {STATE: '$state', ID: '$_id'}}")); // 验证投影阶段：提取state和_id字段
  }

  @Test void testLimit() { // 方法定义：测试方法，测试MongoDB适配器的限制功能（不偏移）
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select state, id from zips\n" // 执行查询：查询zips集合的state和id字段
            + "fetch next 3 rows only") // 查询条件：只取前3行
        .runs() // 执行查询：运行查询并验证成功执行
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$limit: 3}", // 验证限制阶段：限制返回3条记录
                "{$project: {STATE: '$state', ID: '$_id'}}")); // 验证投影阶段：提取state和_id字段
  }

  @Test void testJoin() { // 方法定义：测试方法，测试MongoDB适配器的连接功能
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select b.state, a.id from zips as a join zips as b on a.id=b.id where a.id='02401' " // 执行查询：自连接zips集合，连接条件是id相等，过滤条件是a.id='02401'
            + "fetch next 3 rows only") // 查询条件：只取前3行
        .returnsOrdered("STATE=MA; ID=02401") // 验证结果：验证返回的结果按顺序匹配这个值
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker("{$match: {_id: \"02401\"}}", // 验证匹配阶段：过滤_id为"02401"的文档
                "{$project: {ID: '$_id'}}", // 验证投影阶段：提取_id字段并重命名为ID
                "{$sort: {ID: 1}}")); // 验证排序阶段：按ID升序排序

    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select b.state, a.id from zips as a join zips as b on a.id=b.id " // 执行查询：自连接zips集合，连接条件是id相等，没有WHERE过滤
            + "fetch next 3 rows only") // 查询条件：只取前3行
        .returnsOrdered("STATE=MA; ID=01701", // 验证结果：验证返回的第一行结果
            "STATE=MA; ID=02154", // 验证结果：验证返回的第二行结果
            "STATE=MA; ID=02401") // 验证结果：验证返回的第三行结果
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker("{$project: {ID: '$_id'}}", // 验证投影阶段：提取_id字段并重命名为ID
                "{$sort: {ID: 1}}")); // 验证排序阶段：按ID升序排序
  }

  @Disabled // JUnit注解：禁用此测试方法，因为存在已知问题
  @Test void testFilterSort() { // 方法定义：测试方法，测试MongoDB适配器的过滤和排序功能，验证WHERE条件和ORDER BY子句的正确转换
    // LONGITUDE and LATITUDE are null because of CALCITE-194. // 注释：经度和纬度为null是因为CALCITE-194这个已知的bug
    Util.discard(Bug.CALCITE_194_FIXED); // 丢弃返回值：调用Bug.CALCITE_194_FIXED以标记此测试依赖于该bug的修复状态
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select * from zips\n" // 执行查询：查询zips集合中所有记录
            + "where city = 'SPRINGFIELD' and id >= '70000'\n" // 查询条件：城市为SPRINGFIELD且id大于等于70000
            + "order by state, id") // 排序条件：按state升序，state相同时按id升序
        .returns("" // 验证结果：验证返回的结果匹配这些值
            + "CITY=SPRINGFIELD; LONGITUDE=null; LATITUDE=null; POP=752; STATE=AR; ID=72157\n" // 第一行结果：阿肯色州斯普林菲尔德
            + "CITY=SPRINGFIELD; LONGITUDE=null; LATITUDE=null; POP=1992; STATE=CO; ID=81073\n" // 第二行结果：科罗拉多州斯普林菲尔德
            + "CITY=SPRINGFIELD; LONGITUDE=null; LATITUDE=null; POP=5597; STATE=LA; ID=70462\n" // 第三行结果：路易斯安那州斯普林菲尔德
            + "CITY=SPRINGFIELD; LONGITUDE=null; LATITUDE=null; POP=32384; STATE=OR; ID=97477\n" // 第四行结果：俄勒冈州斯普林菲尔德
            + "CITY=SPRINGFIELD; LONGITUDE=null; LATITUDE=null; POP=27521; STATE=OR; ID=97478\n") // 第五行结果：俄勒冈州另一个斯普林菲尔德
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{\n" // 验证匹配阶段：$match阶段过滤符合条件的文档
                    + "  $match: {\n" // $match操作符开始
                    + "    city: \"SPRINGFIELD\",\n" // 过滤条件：city字段等于SPRINGFIELD
                    + "    _id: {\n" // 嵌套条件：对_id字段进行条件判断
                    + "      $gte: \"70000\"\n" // $gte操作符：大于等于70000
                    + "    }\n" // 嵌套条件结束
                    + "  }\n" // $match操作符结束
                    + "}", // $match阶段结束
                "{$project: {CITY: '$city', LONGITUDE: '$loc[0]', LATITUDE: '$loc[1]', POP: '$pop', STATE: '$state', ID: '$_id'}}", // 验证投影阶段：$project阶段提取并重命名字段，loc数组的第一元素为经度，第二元素为纬度
                "{$sort: {STATE: 1, ID: 1}}")) // 验证排序阶段：$sort阶段按STATE升序（1表示升序），STATE相同时按ID升序
        .explainContains("PLAN=MongoToEnumerableConverter\n" // 验证执行计划：验证执行计划包含MongoToEnumerableConverter节点
            + "  MongoSort(sort0=[$4], sort1=[$5], dir0=[ASC], dir1=[ASC])\n" // 验证排序节点：MongoSort节点按第4列（STATE）和第5列（ID）升序排序
            + "    MongoProject(CITY=[CAST(ITEM($0, 'city')):VARCHAR(20)], LONGITUDE=[CAST(ITEM(ITEM($0, 'loc'), 0)):FLOAT], LATITUDE=[CAST(ITEM(ITEM($0, 'loc'), 1)):FLOAT], POP=[CAST(ITEM($0, 'pop')):INTEGER], STATE=[CAST(ITEM($0, 'state')):VARCHAR(2)], ID=[CAST(ITEM($0, '_id')):VARCHAR(5)])\n" // 验证投影节点：MongoProject节点将MongoDB文档字段映射为Calcite关系字段并进行类型转换
            + "      MongoFilter(condition=[AND(=(CAST(ITEM($0, 'city')):VARCHAR(20), 'SPRINGFIELD'), >=(CAST(ITEM($0, '_id')):VARCHAR(5), '70000'))])\n" // 验证过滤节点：MongoFilter节点应用AND条件，city等于SPRINGFIELD且_id大于等于70000
            + "        MongoTableScan(table=[[mongo_raw, zips]])"); // 验证表扫描节点：MongoTableScan节点扫描mongo_raw.zips表
  }

  @Test void testFilterSortDesc() { // 方法定义：测试方法，测试MongoDB适配器的过滤和降序排序功能
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select * from zips\n" // 执行查询：查询zips集合中所有记录
            + "where pop BETWEEN 45000 AND 46000\n" // 查询条件：人口在45000到46000之间
            + "order by state desc, pop") // 排序条件：按state降序，pop升序排序
        .limit(4) // 限制结果：只取前4行
        .returnsOrdered( // 验证结果：验证返回的结果按顺序匹配这些值
            "CITY=BECKLEY; LONGITUDE=null; LATITUDE=null; POP=45196; STATE=WV; ID=25801", // 第一行结果
            "CITY=ROCKERVILLE; LONGITUDE=null; LATITUDE=null; POP=45328; STATE=SD; ID=57701", // 第二行结果
            "CITY=PAWTUCKET; LONGITUDE=null; LATITUDE=null; POP=45442; STATE=RI; ID=02860", // 第三行结果
            "CITY=LAWTON; LONGITUDE=null; LATITUDE=null; POP=45542; STATE=OK; ID=73505"); // 第四行结果
  }

  @Disabled("broken; [CALCITE-2115] is logged to fix it") // JUnit注解：禁用此测试方法，因为存在CALCITE-2115这个已知问题
  @Test void testUnionPlan() { // 方法定义：测试方法，测试MongoDB适配器的UNION ALL操作，验证两个集合的合并查询
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select * from \"sales_fact_1997\"\n" // 执行查询：查询sales_fact_1997集合中所有记录
            + "union all\n" // UNION ALL操作：合并两个查询结果，保留所有记录包括重复项
            + "select * from \"sales_fact_1998\"") // 第二个查询：查询sales_fact_1998集合中所有记录
        .explainContains("PLAN=EnumerableUnion(all=[true])\n" // 验证执行计划：验证执行计划包含EnumerableUnion节点，all=true表示保留重复项
            + "  MongoToEnumerableConverter\n" // 第一个分支：MongoDB到可枚举转换器
            + "    MongoProject(product_id=[CAST(ITEM($0, 'product_id')):DOUBLE])\n" // 第一个投影节点：提取product_id字段并转换为DOUBLE类型
            + "      MongoTableScan(table=[[_foodmart, sales_fact_1997]])\n" // 第一个表扫描节点：扫描_foodmart.sales_fact_1997表
            + "  MongoToEnumerableConverter\n" // 第二个分支：MongoDB到可枚举转换器
            + "    MongoProject(product_id=[CAST(ITEM($0, 'product_id')):DOUBLE])\n" // 第二个投影节点：提取product_id字段并转换为DOUBLE类型
            + "      MongoTableScan(table=[[_foodmart, sales_fact_1998]])") // 第二个表扫描节点：扫描_foodmart.sales_fact_1998表
        .limit(2) // 限制结果：只取前2行
        .returns( // 验证结果：验证返回的结果
            MongoAssertions.checkResultUnordered( // 使用MongoAssertions检查无序结果
                "product_id=337", "product_id=1512")); // 期望结果包含product_id为337和1512的记录
  }

  @Disabled( // JUnit注解：禁用此测试方法
      "java.lang.ClassCastException: java.lang.Integer cannot be cast to java.lang.Double") // 禁用原因：存在类型转换异常，Integer无法转换为Double
  @Test void testFilterUnionPlan() { // 方法定义：测试方法，测试MongoDB适配器的UNION ALL结合过滤条件，验证在UNION ALL结果上应用WHERE过滤
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select * from (\n" // 执行查询：从子查询中选择所有记录
            + "  select * from \"sales_fact_1997\"\n" // 子查询第一部分：查询sales_fact_1997集合中所有记录
            + "  union all\n" // UNION ALL操作：合并两个查询结果
            + "  select * from \"sales_fact_1998\")\n" // 子查询第二部分：查询sales_fact_1998集合中所有记录
            + "where \"product_id\" = 1") // 过滤条件：只选择product_id等于1的记录
        .runs(); // 执行查询：运行查询并验证成功执行
  }

  /**
   * Tests that mongo query is empty when filter simplified to false.
   */
  @Test void testFilterRedundant() { // 方法定义：测试方法，测试MongoDB适配器对冗余过滤条件的处理，验证当过滤条件简化为false时MongoDB查询为空
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query( // 执行查询：查询zips集合
            "select * from zips where state > 'CA' and state < 'AZ' and state = 'OK'") // 查询条件：state大于CA且state小于AZ且state等于OK，这个条件永远不会满足，是冗余的
        .runs() // 执行查询：运行查询并验证成功执行
        .queryContains(mongoChecker()); // 验证MongoDB查询：验证生成的MongoDB聚合管道为空（因为过滤条件简化为false）
  }

  @Test void testSelectWhere() { // 方法定义：测试方法，测试MongoDB适配器的SELECT和WHERE功能，验证简单查询和过滤条件的正确转换
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query( // 执行查询：查询warehouse集合
            "select * from \"warehouse\" where \"warehouse_state_province\" = 'CA'") // 查询条件：选择warehouse_state_province等于CA的所有记录
        .explainContains("PLAN=MongoToEnumerableConverter\n" // 验证执行计划：验证执行计划包含MongoToEnumerableConverter节点
            + "  MongoProject(warehouse_id=[CAST(ITEM($0, 'warehouse_id')):DOUBLE], warehouse_state_province=[CAST(ITEM($0, 'warehouse_state_province')):VARCHAR(20)])\n" // 验证投影节点：MongoProject节点提取warehouse_id和warehouse_state_province字段并进行类型转换
            + "    MongoFilter(condition=[=(CAST(ITEM($0, 'warehouse_state_province')):VARCHAR(20), 'CA')])\n" // 验证过滤节点：MongoFilter节点应用warehouse_state_province等于CA的条件
            + "      MongoTableScan(table=[[mongo_raw, warehouse]])") // 验证表扫描节点：MongoTableScan节点扫描mongo_raw.warehouse表
        .returns( // 验证结果：验证返回的结果
            MongoAssertions.checkResultUnordered( // 使用MongoAssertions检查无序结果
                "warehouse_id=6; warehouse_state_province=CA", // 第一条结果：仓库ID为6，州为CA
                "warehouse_id=7; warehouse_state_province=CA", // 第二条结果：仓库ID为7，州为CA
                "warehouse_id=14; warehouse_state_province=CA", // 第三条结果：仓库ID为14，州为CA
                "warehouse_id=24; warehouse_state_province=CA")) // 第四条结果：仓库ID为24，州为CA
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            // Per https://issues.apache.org/jira/browse/CALCITE-164, // 注释：根据CALCITE-164 JIRA问题
            // $match must occur before $project for good performance. // 注释：$match必须在$project之前执行以获得更好的性能
            mongoChecker( // 创建MongoDB查询检查器
                "{\n" // 验证匹配阶段：$match阶段过滤符合条件的文档
                    + "  \"$match\": {\n" // $match操作符开始
                    + "    \"warehouse_state_province\": \"CA\"\n" // 过滤条件：warehouse_state_province字段等于CA
                    + "  }\n" // $match操作符结束
                    + "}", // $match阶段结束
                "{$project: {warehouse_id: 1, warehouse_state_province: 1}}")); // 验证投影阶段：$project阶段只保留warehouse_id和warehouse_state_province字段
  }

  @Test void testInPlan() { // 方法定义：测试方法，测试MongoDB适配器的IN操作符，验证WHERE子句中IN条件的正确转换
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select \"store_id\", \"store_name\" from \"store\"\n" // 执行查询：查询store集合的store_id和store_name字段
            + "where \"store_name\" in ('Store 1', 'Store 10', 'Store 11', 'Store 15', 'Store 16', 'Store 24', 'Store 3', 'Store 7')") // 查询条件：store_name在指定列表中
        .returns( // 验证结果：验证返回的结果
            MongoAssertions.checkResultUnordered( // 使用MongoAssertions检查无序结果
                "store_id=1; store_name=Store 1", // 第一条结果
                "store_id=3; store_name=Store 3", // 第二条结果
                "store_id=7; store_name=Store 7", // 第三条结果
                "store_id=10; store_name=Store 10", // 第四条结果
                "store_id=11; store_name=Store 11", // 第五条结果
                "store_id=15; store_name=Store 15", // 第六条结果
                "store_id=16; store_name=Store 16", // 第七条结果
                "store_id=24; store_name=Store 24")) // 第八条结果
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{\n" // 验证匹配阶段：$match阶段使用$or操作符实现IN条件
                    + "  \"$match\": {\n" // $match操作符开始
                    + "    \"$or\": [\n" // $or操作符：多个条件中满足任意一个即可
                    + "      {\n" // 第一个条件
                    + "        \"store_name\": \"Store 1\"\n" // store_name等于Store 1
                    + "      },\n" // 第一个条件结束
                    + "      {\n" // 第二个条件
                    + "        \"store_name\": \"Store 10\"\n" // store_name等于Store 10
                    + "      },\n" // 第二个条件结束
                    + "      {\n" // 第三个条件
                    + "        \"store_name\": \"Store 11\"\n" // store_name等于Store 11
                    + "      },\n" // 第三个条件结束
                    + "      {\n" // 第四个条件
                    + "        \"store_name\": \"Store 15\"\n" // store_name等于Store 15
                    + "      },\n" // 第四个条件结束
                    + "      {\n" // 第五个条件
                    + "        \"store_name\": \"Store 16\"\n" // store_name等于Store 16
                    + "      },\n" // 第五个条件结束
                    + "      {\n" // 第六个条件
                    + "        \"store_name\": \"Store 24\"\n" // store_name等于Store 24
                    + "      },\n" // 第六个条件结束
                    + "      {\n" // 第七个条件
                    + "        \"store_name\": \"Store 3\"\n" // store_name等于Store 3
                    + "      },\n" // 第七个条件结束
                    + "      {\n" // 第八个条件
                    + "        \"store_name\": \"Store 7\"\n" // store_name等于Store 7
                    + "      }\n" // 第八个条件结束
                    + "    ]\n" // $or操作符的数组结束
                    + "  }\n" // $match操作符结束
                    + "}", // $match阶段结束
                "{$project: {store_id: 1, store_name: 1}}")); // 验证投影阶段：$project阶段只保留store_id和store_name字段
  }

  /** Simple query based on the "mongo-zips" model. */ // 方法注释：基于"mongo-zips"模型的简单查询测试
  @Test void testZips() { // 方法定义：测试方法，测试MongoDB适配器的基本查询功能，验证简单的SELECT查询
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select state, city from zips") // 执行查询：查询zips集合的state和city字段
        .returnsCount(ZIPS_SIZE); // 验证结果：验证返回的记录数为ZIPS_SIZE（149条）
  }

  @Test void testCountGroupByEmpty() { // 方法定义：测试方法，测试MongoDB适配器的COUNT聚合函数，验证不带GROUP BY的COUNT(*)查询
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select count(*) from zips") // 执行查询：统计zips集合中的记录总数
        .returns(String.format(Locale.ROOT, "EXPR$0=%d\n", ZIPS_SIZE)) // 验证结果：验证返回的计数值为ZIPS_SIZE（149）
        .explainContains("PLAN=MongoToEnumerableConverter\n" // 验证执行计划：验证执行计划包含MongoToEnumerableConverter节点
            + "  MongoAggregate(group=[{}], EXPR$0=[COUNT()])\n" // 验证聚合节点：MongoAggregate节点执行空分组（group=[{}]）的COUNT操作
            + "    MongoTableScan(table=[[mongo_raw, zips]])") // 验证表扫描节点：MongoTableScan节点扫描mongo_raw.zips表
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$group: {_id: {}, 'EXPR$0': {$sum: 1}}}")); // 验证分组阶段：$group阶段使用空_id（表示不分组），使用$sum:1计算文档数量
  }

  @Test void testCountGroupByEmptyMultiplyBy2() { // 方法定义：测试方法，测试MongoDB适配器的COUNT聚合函数与算术运算，验证COUNT(*)乘以常数的查询
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select count(*)*2 from zips") // 执行查询：统计zips集合中的记录总数并乘以2
        .returns(String.format(Locale.ROOT, "EXPR$0=%d\n", ZIPS_SIZE * 2)) // 验证结果：验证返回的计数值为ZIPS_SIZE乘以2（298）
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$group: {_id: {}, _0: {$sum: 1}}}", // 验证分组阶段：$group阶段使用空_id，使用$sum:1计算文档数量并存储到临时字段_0
                "{$project: {'EXPR$0': {$multiply: ['$_0', {$literal: 2}]}}}")); // 验证投影阶段：$project阶段使用$multiply将_0字段乘以字面量2，结果存储到EXPR$0字段
  }

  @Test void testGroupByOneColumnNotProjected() { // 方法定义：测试方法，测试MongoDB适配器的GROUP BY功能，验证分组字段未出现在SELECT列表中的情况
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select count(*) from zips group by state order by 1") // 执行查询：按state分组统计每组的记录数，并按计数值排序
        .limit(2) // 限制结果：只取前2行
        .returnsUnordered("EXPR$0=2", // 验证结果：验证返回的结果（无序）
            "EXPR$0=2") // 第二行结果
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$project: {STATE: '$state'}}", // 验证投影阶段：$project阶段提取state字段并重命名为STATE
                "{$group: {_id: '$STATE', 'EXPR$0': {$sum: 1}}}", // 验证分组阶段：$group阶段按STATE分组，使用$sum:1计算每组的文档数量
                "{$project: {STATE: '$_id', 'EXPR$0': '$EXPR$0'}}", // 验证投影阶段：$project阶段将_id重命名为STATE，保留EXPR$0字段
                "{$project: {'EXPR$0': 1}}", // 验证投影阶段：$project阶段只保留EXPR$0字段（因为SELECT列表中没有state）
                "{$sort: {EXPR$0: 1}}")); // 验证排序阶段：$sort阶段按EXPR$0升序排序
  }

  @Test void testGroupByOneColumn() { // 方法定义：测试方法，测试MongoDB适配器的单列GROUP BY功能，验证按单列分组并统计的基本操作
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query( // 执行查询：按state分组统计每组的记录数
            "select state, count(*) as c from zips group by state order by state") // 查询语句：选择state和count(*)，按state分组，按state排序
        .limit(3) // 限制结果：只取前3行
        .returns("STATE=AK; C=3\nSTATE=AL; C=3\nSTATE=AR; C=3\n") // 验证结果：验证返回的结果按顺序匹配这些值
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$project: {STATE: '$state'}}", // 验证投影阶段：$project阶段提取state字段并重命名为STATE
                "{$group: {_id: '$STATE', C: {$sum: 1}}}", // 验证分组阶段：$group阶段按STATE分组，使用$sum:1计算每组的文档数量并命名为C
                "{$project: {STATE: '$_id', C: '$C'}}", // 验证投影阶段：$project阶段将_id重命名为STATE，保留C字段
                "{$sort: {STATE: 1}}")); // 验证排序阶段：$sort阶段按STATE升序排序
  }

  @Test void testGroupByOneColumnReversed() { // 方法定义：测试方法，测试MongoDB适配器的单列GROUP BY功能，验证SELECT列表中字段顺序与GROUP BY字段顺序不同的情况
    // Note extra $project compared to testGroupByOneColumn. // 注释：注意与testGroupByOneColumn相比多了一个$project阶段
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query( // 执行查询：按state分组统计每组的记录数
            "select count(*) as c, state from zips group by state order by state") // 查询语句：选择count(*)和state，注意字段顺序与testGroupByOneColumn相反
        .limit(2) // 限制结果：只取前2行
        .returns("C=3; STATE=AK\nC=3; STATE=AL\n") // 验证结果：验证返回的结果按顺序匹配这些值
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$project: {STATE: '$state'}}", // 验证投影阶段：$project阶段提取state字段并重命名为STATE
                "{$group: {_id: '$STATE', C: {$sum: 1}}}", // 验证分组阶段：$group阶段按STATE分组，使用$sum:1计算每组的文档数量并命名为C
                "{$project: {STATE: '$_id', C: '$C'}}", // 验证投影阶段：$project阶段将_id重命名为STATE，保留C字段
                "{$project: {C: 1, STATE: 1}}", // 验证投影阶段：$project阶段调整字段顺序以匹配SELECT列表（C在前，STATE在后）
                "{$sort: {STATE: 1}}")); // 验证排序阶段：$sort阶段按STATE升序排序
  }

  @Test void testGroupByAvg() { // 方法定义：测试方法，测试MongoDB适配器的AVG聚合函数，验证按列分组并计算平均值的功能
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query( // 执行查询：按state分组计算每组的平均人口
            "select state, avg(pop) as a from zips group by state order by state") // 查询语句：选择state和avg(pop)，按state分组，按state排序
        .limit(2) // 限制结果：只取前2行
        .returns("STATE=AK; A=26856\nSTATE=AL; A=43383\n") // 验证结果：验证返回的结果按顺序匹配这些值
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$project: {STATE: '$state', POP: '$pop'}}", // 验证投影阶段：$project阶段提取state和pop字段，分别重命名为STATE和POP
                "{$group: {_id: '$STATE', A: {$avg: '$POP'}}}", // 验证分组阶段：$group阶段按STATE分组，使用$avg计算每组的POP平均值并命名为A
                "{$project: {STATE: '$_id', A: '$A'}}", // 验证投影阶段：$project阶段将_id重命名为STATE，保留A字段
                "{$sort: {STATE: 1}}")); // 验证排序阶段：$sort阶段按STATE升序排序
  }

  @Test void testGroupByAvgSumCount() { // 方法定义：测试方法，测试MongoDB适配器的多个聚合函数，验证同时使用AVG、SUM和COUNT的功能
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query( // 执行查询：按state分组计算每组的平均人口、总人数和记录数
            "select state, avg(pop) as a, sum(pop) as s, count(pop) as c from zips group by state order by state") // 查询语句：选择state、avg(pop)、sum(pop)和count(pop)，按state分组，按state排序
        .limit(2) // 限制结果：只取前2行
        .returns("STATE=AK; A=26856; S=80568; C=3\n" // 验证结果：验证返回的第一行结果
            + "STATE=AL; A=43383; S=130151; C=3\n") // 验证结果：验证返回的第二行结果
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$project: {STATE: '$state', POP: '$pop'}}", // 验证投影阶段：$project阶段提取state和pop字段，分别重命名为STATE和POP
                "{$group: {_id: '$STATE', _1: {$sum: '$POP'}, _2: {$sum: {$cond: [ {$eq: ['POP', null]}, 0, 1]}}}}", // 验证分组阶段：$group阶段按STATE分组，_1存储POP总和，_2使用$cond和$sum计算非null的POP数量
                "{$project: {STATE: '$_id', _1: '$_1', _2: '$_2'}}", // 验证投影阶段：$project阶段将_id重命名为STATE，保留_1和_2字段
                "{$project: {STATE: 1, A: {$divide: [{$cond:[{$eq: ['$_2', {$literal: 0}]},null,'$_1']}, '$_2']}, S: {$cond:[{$eq: ['$_2', {$literal: 0}]},null,'$_1']}, C: '$_2'}}", // 验证投影阶段：$project阶段计算A（平均值，使用$divide），S（总和，使用$cond处理除零），C（计数，直接使用_2）
                "{$sort: {STATE: 1}}")); // 验证排序阶段：$sort阶段按STATE升序排序
  }

  @Test void testGroupByHaving() { // 方法定义：测试方法，测试MongoDB适配器的HAVING子句，验证分组后的过滤条件
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select state, count(*) as c from zips\n" // 执行查询：按state分组统计每组的记录数
            + "group by state having count(*) > 2 order by state") // 查询条件：分组后过滤count(*)大于2的组，按state排序
        .returnsCount(47) // 验证结果：验证返回的记录数为47
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$project: {STATE: '$state'}}", // 验证投影阶段：$project阶段提取state字段并重命名为STATE
                "{$group: {_id: '$STATE', C: {$sum: 1}}}", // 验证分组阶段：$group阶段按STATE分组，使用$sum:1计算每组的文档数量并命名为C
                "{$project: {STATE: '$_id', C: '$C'}}", // 验证投影阶段：$project阶段将_id重命名为STATE，保留C字段
                "{\n" // 验证匹配阶段：$match阶段实现HAVING过滤条件
                    + "  \"$match\": {\n" // $match操作符开始
                    + "    \"C\": {\n" // 对C字段进行条件判断
                    + "      \"$gt\": 2\n" // $gt操作符：大于2
                    + "    }\n" // 条件判断结束
                    + "  }\n" // $match操作符结束
                    + "}", // $match阶段结束
                "{$sort: {STATE: 1}}")); // 验证排序阶段：$sort阶段按STATE升序排序
  }

  @Disabled("https://issues.apache.org/jira/browse/CALCITE-270") // JUnit注解：禁用此测试方法，因为存在CALCITE-270这个已知问题
  @Test void testGroupByHaving2() { // 方法定义：测试方法，测试MongoDB适配器的HAVING子句，验证在HAVING中使用聚合函数作为过滤条件
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select state, count(*) as c from zips\n" // 执行查询：按state分组统计每组的记录数
            + "group by state having sum(pop) > 12000000") // 查询条件：分组后过滤sum(pop)大于12000000的组
        .returns("STATE=NY; C=1596\n" // 验证结果：验证返回的第一行结果（纽约州）
            + "STATE=TX; C=1676\n" // 验证结果：验证返回的第二行结果（德克萨斯州）
            + "STATE=FL; C=826\n" // 验证结果：验证返回的第三行结果（佛罗里达州）
            + "STATE=CA; C=1523\n") // 验证结果：验证返回的第四行结果（加利福尼亚州）
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$project: {STATE: '$state', POP: '$pop'}}", // 验证投影阶段：$project阶段提取state和pop字段，分别重命名为STATE和POP
                "{$group: {_id: '$STATE', C: {$sum: 1}, _2: {$sum: '$POP'}}}", // 验证分组阶段：$group阶段按STATE分组，C存储文档数量，_2存储POP总和
                "{$project: {STATE: '$_id', C: '$C', _2: '$_2'}}", // 验证投影阶段：$project阶段将_id重命名为STATE，保留C和_2字段
                "{\n" // 验证匹配阶段：$match阶段实现HAVING过滤条件
                    + "  $match: {\n" // $match操作符开始
                    + "    _2: {\n" // 对_2字段（POP总和）进行条件判断
                    + "      $gt: 12000000\n" // $gt操作符：大于12000000
                    + "    }\n" // 条件判断结束
                    + "  }\n" // $match操作符结束
                    + "}", // $match阶段结束
                "{$project: {STATE: 1, C: 1}}")); // 验证投影阶段：$project阶段只保留STATE和C字段
  }

  @Test void testGroupByMinMaxSum() { // 方法定义：测试方法，测试MongoDB适配器的多个聚合函数，验证同时使用COUNT、MIN、MAX和SUM的功能
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select count(*) as c, state,\n" // 执行查询：按state分组计算每组的记录数、最小人口、最大人口和总人数
            + " min(pop) as min_pop, max(pop) as max_pop, sum(pop) as sum_pop\n" // 聚合函数：计算最小值、最大值和总和
            + "from zips group by state order by state") // 查询条件：按state分组，按state排序
        .limit(2) // 限制结果：只取前2行
        .returns("C=3; STATE=AK; MIN_POP=23238; MAX_POP=32383; SUM_POP=80568\n" // 验证结果：验证返回的第一行结果（阿拉斯加州）
            + "C=3; STATE=AL; MIN_POP=42124; MAX_POP=44165; SUM_POP=130151\n") // 验证结果：验证返回的第二行结果（阿拉巴马州）
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$project: {STATE: '$state', POP: '$pop'}}", // 验证投影阶段：$project阶段提取state和pop字段，分别重命名为STATE和POP
                "{$group: {_id: '$STATE', C: {$sum: 1}, MIN_POP: {$min: '$POP'}, MAX_POP: {$max: '$POP'}, SUM_POP: {$sum: '$POP'}}}", // 验证分组阶段：$group阶段按STATE分组，C使用$sum:1计算数量，MIN_POP使用$min计算最小值，MAX_POP使用$max计算最大值，SUM_POP使用$sum计算总和
                "{$project: {STATE: '$_id', C: '$C', MIN_POP: '$MIN_POP', MAX_POP: '$MAX_POP', SUM_POP: '$SUM_POP'}}", // 验证投影阶段：$project阶段将_id重命名为STATE，保留C、MIN_POP、MAX_POP和SUM_POP字段
                "{$project: {C: 1, STATE: 1, MIN_POP: 1, MAX_POP: 1, SUM_POP: 1}}", // 验证投影阶段：$project阶段调整字段顺序以匹配SELECT列表
                "{$sort: {STATE: 1}}")); // 验证排序阶段：$sort阶段按STATE升序排序
  }

  @Test void testGroupComposite() { // 方法定义：测试方法，测试MongoDB适配器的复合GROUP BY功能，验证按多列分组并排序
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select count(*) as c, state, city from zips\n" // 执行查询：按state和city分组统计每组的记录数
            + "group by state, city\n" // 分组条件：按state和city两列分组
            + "order by c desc, city\n" // 排序条件：按c降序，c相同时按city升序
            + "limit 2") // 限制结果：只取前2行
        .returns("C=1; STATE=SD; CITY=ABERDEEN\n" // 验证结果：验证返回的第一行结果
            + "C=1; STATE=SC; CITY=AIKEN\n") // 验证结果：验证返回的第二行结果
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$project: {STATE: '$state', CITY: '$city'}}", // 验证投影阶段：$project阶段提取state和city字段，分别重命名为STATE和CITY
                "{$group: {_id: {STATE: '$STATE', CITY: '$CITY'}, C: {$sum: 1}}}", // 验证分组阶段：$group阶段按STATE和CITY复合键分组，使用$sum:1计算每组的文档数量并命名为C
                "{$project: {_id: 0, STATE: '$_id.STATE', CITY: '$_id.CITY', C: '$C'}}", // 验证投影阶段：$project阶段从_id中提取STATE和CITY，设置_id为0（表示不输出_id），保留C字段
                "{$sort: {C: -1, CITY: 1}}", // 验证排序阶段：$sort阶段按C降序（-1表示降序），C相同时按CITY升序
                "{$limit: 2}", // 验证限制阶段：$limit阶段限制返回2条记录
                "{$project: {C: 1, STATE: 1, CITY: 1}}")); // 验证投影阶段：$project阶段调整字段顺序以匹配SELECT列表
  }

  @Disabled("broken; [CALCITE-2115] is logged to fix it") // JUnit注解：禁用此测试方法，因为存在CALCITE-2115这个已知问题
  @Test void testDistinctCount() { // 方法定义：测试方法，测试MongoDB适配器的COUNT DISTINCT功能，验证去重计数
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select state, count(distinct city) as cdc from zips\n" // 执行查询：按state分组统计每组的唯一城市数量
            + "where state in ('CA', 'TX') group by state order by state") // 查询条件：state在CA和TX中，按state分组，按state排序
        .returns("STATE=CA; CDC=1072\n" // 验证结果：验证返回的第一行结果（加利福尼亚州有1072个唯一城市）
            + "STATE=TX; CDC=1233\n") // 验证结果：验证返回的第二行结果（德克萨斯州有1233个唯一城市）
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{\n" // 验证匹配阶段：$match阶段使用$or操作符实现IN条件
                    + "  \"$match\": {\n" // $match操作符开始
                    + "    \"$or\": [\n" // $or操作符：多个条件中满足任意一个即可
                    + "      {\n" // 第一个条件
                    + "        \"state\": \"CA\"\n" // state等于CA
                    + "      },\n" // 第一个条件结束
                    + "      {\n" // 第二个条件
                    + "        \"state\": \"TX\"\n" // state等于TX
                    + "      }\n" // 第二个条件结束
                    + "    ]\n" // $or操作符的数组结束
                    + "  }\n" // $match操作符结束
                    + "}", // $match阶段结束
                "{$project: {CITY: '$city', STATE: '$state'}}", // 验证投影阶段：$project阶段提取city和state字段，分别重命名为CITY和STATE
                "{$group: {_id: {CITY: '$CITY', STATE: '$STATE'}}}", // 验证分组阶段：$group阶段按CITY和STATE复合键分组，实现去重
                "{$project: {_id: 0, CITY: '$_id.CITY', STATE: '$_id.STATE'}}", // 验证投影阶段：$project阶段从_id中提取CITY和STATE，设置_id为0
                "{$group: {_id: '$STATE', CDC: {$sum: {$cond: [ {$eq: ['CITY', null]}, 0, 1]}}}}", // 验证分组阶段：$group阶段按STATE分组，CDC使用$sum和$cond计算非null的CITY数量（即去重后的城市数）
                "{$project: {STATE: '$_id', CDC: '$CDC'}}", // 验证投影阶段：$project阶段将_id重命名为STATE，保留CDC字段
                "{$sort: {STATE: 1}}")); // 验证排序阶段：$sort阶段按STATE升序排序
  }

  @Test void testDistinctCountOrderBy() { // 方法定义：测试方法，测试MongoDB适配器的COUNT DISTINCT功能与排序，验证去重计数后按结果排序
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select state, count(distinct city) as cdc\n" // 执行查询：按state分组统计每组的唯一城市数量
            + "from zips\n" // 数据源：zips集合
            + "group by state\n" // 分组条件：按state分组
            + "order by cdc desc, state\n" // 排序条件：按cdc降序，cdc相同时按state升序
            + "limit 5") // 限制结果：只取前5行
        .returns("STATE=AK; CDC=3\n" // 验证结果：验证返回的第一行结果
            + "STATE=AL; CDC=3\n" // 验证结果：验证返回的第二行结果
            + "STATE=AR; CDC=3\n" // 验证结果：验证返回的第三行结果
            + "STATE=AZ; CDC=3\n" // 验证结果：验证返回的第四行结果
            + "STATE=CA; CDC=3\n") // 验证结果：验证返回的第五行结果
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$project: {CITY: '$city', STATE: '$state'}}", // 验证投影阶段：$project阶段提取city和state字段，分别重命名为CITY和STATE
                "{$group: {_id: {CITY: '$CITY', STATE: '$STATE'}}}", // 验证分组阶段：$group阶段按CITY和STATE复合键分组，实现去重
                "{$project: {_id: 0, CITY: '$_id.CITY', STATE: '$_id.STATE'}}", // 验证投影阶段：$project阶段从_id中提取CITY和STATE，设置_id为0
                "{$group: {_id: '$STATE', CDC: {$sum: {$cond: [ {$eq: ['CITY', null]}, 0, 1]}}}}", // 验证分组阶段：$group阶段按STATE分组，CDC使用$sum和$cond计算非null的CITY数量（即去重后的城市数）
                "{$project: {STATE: '$_id', CDC: '$CDC'}}", // 验证投影阶段：$project阶段将_id重命名为STATE，保留CDC字段
                "{$sort: {CDC: -1, STATE: 1}}", // 验证排序阶段：$sort阶段按CDC降序（-1表示降序），CDC相同时按STATE升序
                "{$limit: 5}")); // 验证限制阶段：$limit阶段限制返回5条记录
  }

  @Disabled("broken; [CALCITE-2115] is logged to fix it") // JUnit注解：禁用此测试方法，因为存在CALCITE-2115这个已知问题
  @Test void testProject() { // 方法定义：测试方法，测试MongoDB适配器的PROJECT功能，验证SELECT列表中包含常量值的查询
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select state, city, 0 as zero from zips order by state, city") // 执行查询：查询zips集合的state和city字段，并添加一个常量列zero值为0
        .limit(2) // 限制结果：只取前2行
        .returns("STATE=AK; CITY=AKHIOK; ZERO=0\n" // 验证结果：验证返回的第一行结果
            + "STATE=AK; CITY=AKIACHAK; ZERO=0\n") // 验证结果：验证返回的第二行结果
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$project: {CITY: '$city', STATE: '$state'}}", // 验证投影阶段：$project阶段提取city和state字段，分别重命名为CITY和STATE
                "{$sort: {STATE: 1, CITY: 1}}", // 验证排序阶段：$sort阶段按STATE和CITY升序排序
                "{$project: {STATE: 1, CITY: 1, ZERO: {$literal: 0}}}")); // 验证投影阶段：$project阶段使用$literal添加常量列ZERO，值为0
  }

  @Test void testFilter() { // 方法定义：测试方法，测试MongoDB适配器的简单过滤功能，验证WHERE子句的基本用法
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select state, city from zips where state = 'CA'") // 执行查询：查询zips集合中state等于CA的记录，返回state和city字段
        .limit(3) // 限制结果：只取前3行
        .returnsUnordered("STATE=CA; CITY=LOS ANGELES", // 验证结果：验证返回的结果（无序）
            "STATE=CA; CITY=BELL GARDENS", // 第二条结果
            "STATE=CA; CITY=NORWALK") // 第三条结果
        .explainContains("PLAN=MongoToEnumerableConverter\n" // 验证执行计划：验证执行计划包含MongoToEnumerableConverter节点
            + "  MongoProject(STATE=[CAST(ITEM($0, 'state')):VARCHAR(2)], CITY=[CAST(ITEM($0, 'city')):VARCHAR(20)])\n" // 验证投影节点：MongoProject节点提取state和city字段并进行类型转换
            + "    MongoFilter(condition=[=(CAST(CAST(ITEM($0, 'state')):VARCHAR(2)):CHAR(2), 'CA')])\n" // 验证过滤节点：MongoFilter节点应用state等于CA的条件
            + "      MongoTableScan(table=[[mongo_raw, zips]])"); // 验证表扫描节点：MongoTableScan节点扫描mongo_raw.zips表
  }

  /** MongoDB's predicates are handed (they can only accept literals on the
   * right-hand size) so it's worth testing that we handle them right both
   * ways around. */ // 方法注释：MongoDB的谓词是有方向的（它们只能在右侧接受字面量），因此值得测试我们是否能正确处理两种方向
  @Test void testFilterReversed() { // 方法定义：测试方法，测试MongoDB适配器的反向过滤条件，验证当字面量在左侧时的处理
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select state, city from zips where 'WI' < state order by state, city") // 执行查询：查询zips集合中state大于'WI'的记录，注意字面量'WI'在左侧
        .limit(3) // 限制结果：只取前3行
        .returnsOrdered("STATE=WV; CITY=BECKLEY", // 验证结果：验证返回的第一行结果
            "STATE=WV; CITY=ELM GROVE", // 验证结果：验证返回的第二行结果
            "STATE=WV; CITY=STAR CITY"); // 验证结果：验证返回的第三行结果

    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select state, city from zips where state > 'WI' order by state, city") // 执行查询：查询zips集合中state大于'WI'的记录，注意字面量'WI'在右侧（正常写法）
        .limit(3) // 限制结果：只取前3行
        .returnsOrdered("STATE=WV; CITY=BECKLEY", // 验证结果：验证返回的第一行结果
            "STATE=WV; CITY=ELM GROVE", // 验证结果：验证返回的第二行结果
            "STATE=WV; CITY=STAR CITY"); // 验证结果：验证返回的第三行结果
  }

  /** MongoDB's predicates are handed (they can only accept literals on the
   * right-hand size) so it's worth testing that we handle them right both
   * ways around.
   *
   * <p>Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-740">[CALCITE-740]
   * Redundant WHERE clause causes wrong result in MongoDB adapter</a>. */ // 方法注释：测试MongoDB适配器对冗余WHERE子句的处理，验证CALCITE-740问题
  @Test void testFilterPair() { // 方法定义：测试方法，测试MongoDB适配器对多个过滤条件的组合处理，验证AND和OR条件的正确性
    final int gt9k = 148; // 常量定义：pop大于9000的预期记录数
    final int lt9k = 1; // 常量定义：pop小于9000的预期记录数
    final int gt8k = 148; // 常量定义：pop大于8000的预期记录数
    final int lt8k = 1; // 常量定义：pop小于8000的预期记录数
    checkPredicate(gt9k, "where pop > 8000 and pop > 9000"); // 检查谓词：pop大于8000且大于9000，等价于pop大于9000，预期返回148条
    checkPredicate(gt9k, "where pop > 9000"); // 检查谓词：pop大于9000，预期返回148条
    checkPredicate(lt9k, "where pop < 9000"); // 检查谓词：pop小于9000，预期返回1条
    checkPredicate(gt8k, "where pop > 8000"); // 检查谓词：pop大于8000，预期返回148条
    checkPredicate(lt8k, "where pop < 8000"); // 检查谓词：pop小于8000，预期返回1条
    checkPredicate(gt9k, "where pop > 9000 and pop > 8000"); // 检查谓词：pop大于9000且大于8000（顺序相反），预期返回148条
    checkPredicate(gt8k, "where pop > 9000 or pop > 8000"); // 检查谓词：pop大于9000或大于8000，等价于pop大于8000，预期返回148条
    checkPredicate(gt8k, "where pop > 8000 or pop > 9000"); // 检查谓词：pop大于8000或大于9000（顺序相反），预期返回148条
    checkPredicate(lt8k, "where pop < 8000 and pop < 9000"); // 检查谓词：pop小于8000且小于9000，等价于pop小于8000，预期返回1条
  }

  private void checkPredicate(int expected, String q) { // 方法定义：私有辅助方法，用于验证过滤条件的正确性，参数为预期记录数和WHERE子句
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select count(*) as c from zips\n" // 执行查询：统计满足过滤条件的记录数
            + q) // 添加WHERE子句
        .returns("C=" + expected + "\n"); // 验证结果：验证返回的计数值等于预期值
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select * from zips\n" // 执行查询：查询满足过滤条件的所有记录
            + q) // 添加WHERE子句
        .returnsCount(expected); // 验证结果：验证返回的记录数等于预期值
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-286">[CALCITE-286]
   * Error casting MongoDB date</a>. */ // 方法注释：测试MongoDB日期类型转换，验证CALCITE-286问题
  @Test void testDate() { // 方法定义：测试方法，测试MongoDB适配器的日期类型转换功能
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select cast(_MAP['date'] as DATE) from \"mongo_raw\".\"datatypes\"") // 执行查询：将datatypes集合中的date字段转换为DATE类型
        .returnsUnordered("EXPR$0=2012-09-05"); // 验证结果：验证返回的日期值为2012-09-05
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5405">[CALCITE-5405]
   * Error casting MongoDB dates to TIMESTAMP</a>. */ // 方法注释：测试MongoDB日期转换为TIMESTAMP类型，验证CALCITE-5405问题
  @Test void testDateConversion() { // 方法定义：测试方法，测试MongoDB适配器的日期到TIMESTAMP类型转换功能
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select cast(_MAP['date'] as TIMESTAMP) from \"mongo_raw\".\"datatypes\"") // 执行查询：将datatypes集合中的date字段转换为TIMESTAMP类型
        .returnsUnordered("EXPR$0=2012-09-05 00:00:00"); // 验证结果：验证返回的时间戳值为2012-09-05 00:00:00
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5407">[CALCITE-5407]
   * Error casting MongoDB array to VARCHAR ARRAY</a>. */ // 方法注释：测试MongoDB数组类型转换，验证CALCITE-5407问题
  @Test void testArrayConversion() { // 方法定义：测试方法，测试MongoDB适配器的数组类型转换功能
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select cast(_MAP['arr'] as VARCHAR ARRAY) from \"mongo_raw\".\"datatypes\"") // 执行查询：将datatypes集合中的arr字段转换为VARCHAR ARRAY类型
        .returnsUnordered("EXPR$0=[a, b]"); // 验证结果：验证返回的数组值为[a, b]
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-665">[CALCITE-665]
   * ClassCastException in MongoDB adapter</a>. */ // 方法注释：测试MongoDB适配器COUNT返回值的类型，验证CALCITE-665问题
  @Test void testCountViaInt() { // 方法定义：测试方法，测试MongoDB适配器的COUNT函数返回值类型，验证可以通过getInt方法获取
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select count(*) from zips") // 执行查询：统计zips集合中的记录总数
        .returns(input -> { // 验证结果：使用自定义验证函数
          try { // 异常处理：尝试执行验证
            assertThat(input.next(), is(true)); // 断言：验证有下一行记录
            assertThat(input.getInt(1), is(ZIPS_SIZE)); // 断言：验证第一列的整数值为ZIPS_SIZE（149）
          } catch (SQLException e) { // 捕获SQL异常
            throw TestUtil.rethrow(e); // 抛出未检查异常：将SQLException包装为RuntimeException
          }
        });
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6623">[CALCITE-6623]
   * MongoDB adapter throws a java.lang.ClassCastException when Decimal128 or Binary types are
   * used, or when a primitive value is cast to a string</a>. */ // 方法注释：测试MongoDB适配器的运行时类型转换，验证CALCITE-6623问题
  @Test void testRuntimeTypes() { // 方法定义：测试方法，测试MongoDB适配器对各种数据类型的转换处理
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select cast(_MAP['loc'] AS varchar) " // 执行查询：将zips集合中的loc数组字段转换为VARCHAR类型
            + "from \"mongo_raw\".\"zips\" where _MAP['_id']='99801'") // 查询条件：_id等于99801
        .returnsCount(1) // 验证结果：验证返回的记录数为1
        .returnsValue("[-134.529429, 58.362767]"); // 验证结果：验证返回的loc数组值

    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select cast(_MAP['warehouse_postal_code'] AS bigint) AS postal_code_as_bigint" // 执行查询：将warehouse集合中的warehouse_postal_code字段转换为BIGINT类型
            + " from \"mongo_raw\".\"warehouse\" where _MAP['warehouse_id']=1") // 查询条件：warehouse_id等于1
        .returnsCount(1) // 验证结果：验证返回的记录数为1
        .returnsValue("55555") // 验证结果：验证返回的邮政编码值为55555
        .typeIs("[POSTAL_CODE_AS_BIGINT BIGINT]"); // 验证结果类型：验证返回的列类型为BIGINT

    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select cast(_MAP['warehouse_postal_code'] AS varchar) AS postal_code_as_varchar" // 执行查询：将warehouse集合中的warehouse_postal_code字段转换为VARCHAR类型
            + " from \"mongo_raw\".\"warehouse\" where _MAP['warehouse_id']=1") // 查询条件：warehouse_id等于1
        .returnsCount(1) // 验证结果：验证返回的记录数为1
        .returnsValue("55555") // 验证结果：验证返回的邮政编码值为55555
        .typeIs("[POSTAL_CODE_AS_VARCHAR VARCHAR]"); // 验证结果类型：验证返回的列类型为VARCHAR

    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select cast(_MAP['binaryData'] AS binary) from \"mongo_raw\".\"datatypes\"") // 执行查询：将datatypes集合中的binaryData字段转换为BINARY类型
        .returnsCount(1) // 验证结果：验证返回的记录数为1
        .returns(resultSet -> { // 验证结果：使用自定义验证函数
          try { // 异常处理：尝试执行验证
            resultSet.next(); // 移动到下一行记录
            //CHECKSTYLE: IGNORE 1 // 注释：忽略CheckStyle检查
            assertThat(new String(resultSet.getBytes(1), StandardCharsets.UTF_8), is("binaryData")); // 断言：验证第一列的二进制数据转换为字符串后为"binaryData"
          } catch (SQLException e) { // 捕获SQL异常
            throw TestUtil.rethrow(e); // 抛出未检查异常：将SQLException包装为RuntimeException
          }
        });

    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select cast(_MAP['loc'] AS bigint) " // 执行查询：将zips集合中的loc数组字段转换为BIGINT类型（这是一个无效的转换）
            + "from \"mongo_raw\".\"zips\" where _MAP['_id']='99801'") // 查询条件：_id等于99801
        .throws_("Invalid field:"); // 验证结果：验证抛出包含"Invalid field:"的异常
  }

  /**
   * Returns a function that checks that a particular MongoDB query
   * has been called.
   *
   * @param expected Expected query (as array)
   * @return validation function
   */ // 方法注释：返回一个函数，用于检查是否调用了特定的MongoDB查询
  private static Consumer<List> mongoChecker(final String... expected) { // 方法定义：私有静态方法，创建MongoDB查询检查器，参数为预期的MongoDB聚合管道阶段数组
    return actual -> { // 返回Consumer函数：接受实际的MongoDB查询列表
      if (expected == null) { // 条件判断：如果预期查询为null
        assertThat("null mongo Query", actual, nullValue()); // 断言：验证实际查询也为null
        return; // 返回：结束验证
      }

      if (expected.length == 0) { // 条件判断：如果预期查询数组长度为0
        CalciteAssert.assertArrayEqual("empty Mongo query", expected, // 断言：验证实际查询也为空数组
            actual.toArray(new Object[0])); // 将实际查询列表转换为数组
        return; // 返回：结束验证
      }

      // comparing list of Bsons (expected and actual) // 注释：比较BSON文档列表（预期和实际）
      final List<BsonDocument> expectedBsons = Arrays.stream(expected).map(BsonDocument::parse) // 将预期字符串数组解析为BsonDocument列表
          .collect(Collectors.toList()); // 收集为List

      final List<BsonDocument> actualBsons =  ((List<?>) actual.get(0)) // 获取实际查询列表的第一个元素
          .stream() // 转换为流
          .map(Objects::toString) // 转换为字符串
          .map(BsonDocument::parse) // 解析为BsonDocument
          .collect(Collectors.toList()); // 收集为List

      // compare Bson (not string) representation // 注释：比较BSON表示（不是字符串）
      if (!expectedBsons.equals(actualBsons)) { // 条件判断：如果预期和实际BSON列表不相等
        final JsonWriterSettings settings = JsonWriterSettings.builder().indent(true).build(); // 创建JSON写入设置：启用缩进格式
        // outputs Bson in pretty Json format (with new lines) // 注释：以美观的JSON格式输出BSON（带换行）
        // so output is human friendly in IDE diff tool // 注释：使输出在IDE差异工具中易于阅读
        final Function<List<BsonDocument>, String> prettyFn = bsons -> bsons.stream() // 创建格式化函数：将BsonDocument列表转换为格式化的JSON字符串
            .map(b -> b.toJson(settings)).collect(Collectors.joining("\n")); // 使用换行符连接各个BsonDocument的JSON表示

        // used to pretty print Assertion error // 注释：用于美化打印断言错误
        assertThat("expected and actual Mongo queries (pipelines) do not match", // 断言：验证预期和实际MongoDB查询（管道）不匹配
            prettyFn.apply(actualBsons), is(prettyFn.apply(expectedBsons))); // 比较格式化后的实际和预期查询

        fail("Should have failed previously because expected != actual is known to be true"); // 失败：抛出断言失败错误（理论上不应该执行到这里）
      }
    };
  }

  @Test void testColumnQuoting() { // 方法定义：测试方法，测试MongoDB适配器的列名引号处理，验证带引号的列名和别名
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select state as \"STATE\", avg(pop) as \"AVG(pop)\" " // 执行查询：查询state并别名为"STATE"，计算avg(pop)并别名为"AVG(pop)"
            + "from zips " // 数据源：zips集合
            + "group by \"STATE\" " // 分组条件：按"STATE"分组
            + "order by \"AVG(pop)\"") // 排序条件：按"AVG(pop)"排序
        .limit(2) // 限制结果：只取前2行
        .returns("STATE=VT; AVG(pop)=26408\nSTATE=AK; AVG(pop)=26856\n"); // 验证结果：验证返回的结果按顺序匹配这些值
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2109">[CALCITE-2109]
   * Mongo adapter: unable to translate (A AND B) conditional case</a>. */ // 方法注释：测试MongoDB适配器的复合条件转换，验证CALCITE-2109问题
  @Test void testTranslateAndInCondition() { // 方法定义：测试方法，测试MongoDB适配器对AND和IN条件的组合处理
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select state, city from zips " // 执行查询：查询zips集合的state和city字段
            + "where city='LEWISTON' and state in ('ME', 'VT') " // 查询条件：city等于LEWISTON且state在ME或VT中
            + "order by state") // 排序条件：按state排序
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
            "{$match: {$and: [{$or: [{state: \"ME\"}, {state: \"VT\"}]}, {city: \"LEWISTON\"}]}}", // 验证匹配阶段：$match阶段使用$and组合$or和等值条件，实现AND和IN的组合
            "{$project: {STATE: '$state', CITY: '$city'}}", // 验证投影阶段：$project阶段提取state和city字段，分别重命名为STATE和CITY
            "{$sort: {STATE: 1}}")) // 验证排序阶段：$sort阶段按STATE升序排序
        .returns("STATE=ME; CITY=LEWISTON\n"); // 验证结果：验证返回的记录
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2109">[CALCITE-2109]
   * Mongo adapter: unable to translate (A AND B) conditional case</a>. */ // 方法注释：测试MongoDB适配器的复合条件转换，验证CALCITE-2109问题
  @Test void testTranslateOrAndCondition() { // 方法定义：测试方法，测试MongoDB适配器对OR和AND条件的组合处理
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select state, city from zips " // 执行查询：查询zips集合的state和city字段
            + "where (state = 'MI' or state = 'VT') and city='TAYLOR' " // 查询条件：state等于MI或VT，且city等于TAYLOR
            + "order by state") // 排序条件：按state排序
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$match: {$and: [{$or: [{state: \"MI\"}, {state: \"VT\"}]}, {city: \"TAYLOR\"}]}}", // 验证匹配阶段：$match阶段使用$and组合$or和等值条件，实现OR和AND的组合
                "{$project: {STATE: '$state', CITY: '$city'}}", // 验证投影阶段：$project阶段提取state和city字段，分别重命名为STATE和CITY
                "{$sort: {STATE: 1}}")) // 验证排序阶段：$sort阶段按STATE升序排序
        .returns("STATE=MI; CITY=TAYLOR\n"); // 验证结果：验证返回的记录
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2109">[CALCITE-2109]
   * Mongo adapter: unable to translate (A AND B) conditional case</a>. */ // 方法注释：测试MongoDB适配器的常量条件优化，验证CALCITE-2109问题
  @Test void testAndAlwaysFalseCondition() { // 方法定义：测试方法，测试MongoDB适配器对永远为false的条件的优化处理
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select state, city from zips " // 执行查询：查询zips集合的state和city字段
            + "where city='LEWISTON' and 1=0 " // 查询条件：city等于LEWISTON且1=0（永远为false）
            + "order by state") // 排序条件：按state排序
        .explainContains("PLAN=EnumerableValues(tuples=[[]])") // 验证执行计划：验证执行计划被优化为EnumerableValues（空结果集）
        .returns(""); // 验证结果：验证返回空字符串（无结果）
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2109">[CALCITE-2109]
   * Mongo adapter: unable to translate (A AND B) conditional case</a>. */ // 方法注释：测试MongoDB适配器的CNF（合取范式）条件转换，验证CALCITE-2109问题
  @Test void testCNFCondition() { // 方法定义：测试方法，测试MongoDB适配器对CNF形式条件的处理
    assertModel(MODEL) // 创建断言：使用MODEL常量创建CalciteAssert断言
        .query("select state, city from zips " // 执行查询：查询zips集合的state和city字段
            + "where (state='ME' OR state='VT') AND (city='LEWISTON' OR city='BRATTLEBORO') " // 查询条件：CNF形式，(A OR B) AND (C OR D)
            + "order by state") // 排序条件：按state排序
        .queryContains( // 验证MongoDB查询：验证生成的MongoDB聚合管道包含这些阶段
            mongoChecker( // 创建MongoDB查询检查器
                "{$match: {$and: [{$or: [{state: \"ME\"}, {state: \"VT\"}]}, {$or: [{city: \"BRATTLEBORO\"}, {city: \"LEWISTON\"}]}]}}", // 验证匹配阶段：$match阶段使用$and组合两个$or条件，实现CNF形式
                "{$project: {STATE: '$state', CITY: '$city'}}", // 验证投影阶段：$project阶段提取state和city字段，分别重命名为STATE和CITY
                "{$sort: {STATE: 1}}")) // 验证排序阶段：$sort阶段按STATE升序排序
        .returns("STATE=ME; CITY=LEWISTON\nSTATE=VT; CITY=BRATTLEBORO\n"); // 验证结果：验证返回的记录
  }
}
