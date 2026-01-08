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
 /* Apache许可证声明,说明该代码遵循Apache 2.0许可证,允许自由使用和修改 */
package org.apache.calcite.config; // 定义包名,该类位于org.apache.calcite.config配置包中

import com.google.common.collect.ImmutableSet; // 导入Google Guava库中的不可变集合类,用于存储允许的属性值集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入注解,用于标记可能为null的值

import java.io.File; // 导入文件操作类,用于检查文件是否存在
import java.io.IOException; // 导入IO异常类,用于处理文件读取异常
import java.io.InputStream; // 导入输入流类,用于读取属性文件
import java.util.Locale; // 导入本地化类,用于字符串大小写转换
import java.util.Properties; // 导入属性类,用于存储键值对配置
import java.util.Set; // 导入集合接口,用于存储允许的属性值
import java.util.function.Function; // 导入函数式接口,用于定义属性值解析函数
import java.util.function.IntPredicate; // 导入整数谓词接口,用于验证整数值
import java.util.stream.Stream; // 导入流接口,用于处理属性集合的合并

import static com.google.common.base.MoreObjects.firstNonNull; // 导入工具方法,用于获取第一个非null值

import static java.lang.Boolean.parseBoolean; // 导入静态方法,用于解析布尔值
import static java.lang.Integer.parseInt; // 导入静态方法,用于解析整数值
import static java.util.Objects.requireNonNull; // 导入静态方法,用于检查对象是否为null

/**
 * A Calcite specific system property that is used to configure various aspects of the framework.
 * Calcite特定的系统属性,用于配置框架的各个方面
 *
 * <p>Calcite system properties must always be in the "calcite" root namespace.
 * Calcite系统属性必须始终位于"calcite"根命名空间中
 *
 * @param <T> the type of the property value // 泛型参数T,表示属性值的类型
 */
public final class CalciteSystemProperty<T> { // 定义一个不可变的最终类CalciteSystemProperty,使用泛型T表示属性值类型
  /**
   * Holds all system properties related with the Calcite.
   * 保存所有与Calcite相关的系统属性
   *
   * <p>Deprecated <code>"saffron.properties"</code> (in namespaces"saffron" and "net.sf.saffron")
   * are also kept here but under "calcite" namespace.
   * 已弃用的"saffron.properties"(在"saffron"和"net.sf.saffron"命名空间中)也保存在此处,但在"calcite"命名空间下
   */
  private static final Properties PROPERTIES = loadProperties(); // 定义静态常量属性集合,通过loadProperties()方法加载所有属性

  /**
   * Whether to run Calcite in debug mode.
   * 是否在调试模式下运行Calcite
   *
   * <p>When debug mode is activated significantly more information is gathered and printed to
   * STDOUT. It is most commonly used to print and identify problems in generated java code. Debug
   * mode is also used to perform more verifications at runtime, which are not performed during
   * normal execution.
   * 当激活调试模式时,会收集并打印大量信息到标准输出。最常用于打印和识别生成的Java代码中的问题。
   * 调试模式还用于在运行时执行更多验证,这些验证在正常执行期间不会执行
   */
  public static final CalciteSystemProperty<Boolean> DEBUG = // 定义公共静态常量DEBUG属性,类型为CalciteSystemProperty<Boolean>
      booleanProperty("calcite.debug", false); // 调用booleanProperty方法创建布尔属性,键为"calcite.debug",默认值为false

  /**
   * Whether to exploit join commutative property.
   * 是否利用连接的交换律属性
   */
  // TODO review zabetak:
  // Does the property control join commutativity or rather join associativity? The property is
  // associated with {@link org.apache.calcite.rel.rules.JoinAssociateRule} and not with
  // {@link org.apache.calcite.rel.rules.JoinCommuteRule}.
  // TODO:需要审查,该属性控制的是连接交换性还是连接结合性?该属性与JoinAssociateRule关联而不是JoinCommuteRule
  public static final CalciteSystemProperty<Boolean> COMMUTE = // 定义公共静态常量COMMUTE属性,类型为CalciteSystemProperty<Boolean>
      booleanProperty("calcite.enable.join.commute", false); // 调用booleanProperty方法创建布尔属性,键为"calcite.enable.join.commute",默认值为false

  /** Whether to enable the collation trait in the default planner configuration.
   * 是否在默认规划器配置中启用排序特征
   *
   * <p>Some extra optimizations are possible if enabled, but queries should
   * work either way. At some point this will become a preference, or we will
   * run multiple phases: first disabled, then enabled. */
  // 如果启用,可以进行一些额外的优化,但查询无论是否启用都应该能正常工作。在某些时候这将成为一个偏好设置,
  // 或者我们将运行多个阶段:首先禁用,然后启用
  public static final CalciteSystemProperty<Boolean> ENABLE_COLLATION_TRAIT = // 定义公共静态常量ENABLE_COLLATION_TRAIT属性
      booleanProperty("calcite.enable.collation.trait", true); // 调用booleanProperty方法创建布尔属性,键为"calcite.enable.collation.trait",默认值为true

  /** Whether the enumerable convention is enabled in the default planner configuration. */
  // 是否在默认规划器配置中启用可枚举约定
  public static final CalciteSystemProperty<Boolean> ENABLE_ENUMERABLE = // 定义公共静态常量ENABLE_ENUMERABLE属性
      booleanProperty("calcite.enable.enumerable", true); // 调用booleanProperty方法创建布尔属性,键为"calcite.enable.enumerable",默认值为true

  /** Whether the EnumerableTableScan should support ARRAY fields. */
  // EnumerableTableScan是否应该支持ARRAY字段
  public static final CalciteSystemProperty<Boolean> ENUMERABLE_ENABLE_TABLESCAN_ARRAY = // 定义公共静态常量ENUMERABLE_ENABLE_TABLESCAN_ARRAY属性
      booleanProperty("calcite.enable.enumerable.tablescan.array", false); // 调用booleanProperty方法创建布尔属性,键为"calcite.enable.enumerable.tablescan.array",默认值为false

  /** Whether the EnumerableTableScan should support MAP fields. */
  // EnumerableTableScan是否应该支持MAP字段
  public static final CalciteSystemProperty<Boolean> ENUMERABLE_ENABLE_TABLESCAN_MAP = // 定义公共静态常量ENUMERABLE_ENABLE_TABLESCAN_MAP属性
      booleanProperty("calcite.enable.enumerable.tablescan.map", false); // 调用booleanProperty方法创建布尔属性,键为"calcite.enable.enumerable.tablescan.map",默认值为false

  /** Whether the EnumerableTableScan should support MULTISET fields. */
  // EnumerableTableScan是否应该支持MULTISET字段
  public static final CalciteSystemProperty<Boolean> ENUMERABLE_ENABLE_TABLESCAN_MULTISET = // 定义公共静态常量ENUMERABLE_ENABLE_TABLESCAN_MULTISET属性
      booleanProperty("calcite.enable.enumerable.tablescan.multiset", false); // 调用booleanProperty方法创建布尔属性,键为"calcite.enable.enumerable.tablescan.multiset",默认值为false

  /** Whether streaming is enabled in the default planner configuration. */
  // 是否在默认规划器配置中启用流式处理
  public static final CalciteSystemProperty<Boolean> ENABLE_STREAM = // 定义公共静态常量ENABLE_STREAM属性
      booleanProperty("calcite.enable.stream", true); // 调用booleanProperty方法创建布尔属性,键为"calcite.enable.stream",默认值为true

  /** Whether RexNode digest should be normalized (e.g. call operands ordered).
   * RexNode摘要是否应该规范化(例如调用操作数排序)
   *
   * <p>Normalization helps to treat $0=$1 and $1=$0 expressions equal, thus it
   * saves efforts on planning. */
  // 规范化有助于将$0=$1和$1=$0表达式视为相等,从而节省规划工作的精力
  public static final CalciteSystemProperty<Boolean> ENABLE_REX_DIGEST_NORMALIZE = // 定义公共静态常量ENABLE_REX_DIGEST_NORMALIZE属性
      booleanProperty("calcite.enable.rexnode.digest.normalize", true); // 调用booleanProperty方法创建布尔属性,键为"calcite.enable.rexnode.digest.normalize",默认值为true

  /**
   * Whether to follow the SQL standard strictly.
   * 是否严格遵循SQL标准
   */
  public static final CalciteSystemProperty<Boolean> STRICT = // 定义公共静态常量STRICT属性
      booleanProperty("calcite.strict.sql", false); // 调用booleanProperty方法创建布尔属性,键为"calcite.strict.sql",默认值为false

  /**
   * Whether to include a GraphViz representation when dumping the state of the
   * Volcano planner.
   * 在转储Volcano规划器状态时是否包含GraphViz表示
   */
  public static final CalciteSystemProperty<Boolean> DUMP_GRAPHVIZ = // 定义公共静态常量DUMP_GRAPHVIZ属性
      booleanProperty("calcite.volcano.dump.graphviz", true); // 调用booleanProperty方法创建布尔属性,键为"calcite.volcano.dump.graphviz",默认值为true

  /**
   * Whether to include <code>RelSet</code> information when dumping the state
   * of the Volcano planner.
   * 在转储Volcano规划器状态时是否包含RelSet信息
   */
  public static final CalciteSystemProperty<Boolean> DUMP_SETS = // 定义公共静态常量DUMP_SETS属性
      booleanProperty("calcite.volcano.dump.sets", true); // 调用booleanProperty方法创建布尔属性,键为"calcite.volcano.dump.sets",默认值为true

  /**
   * Whether to enable top-down optimization. This config can be overridden
   * by {@link CalciteConnectionProperty#TOPDOWN_OPT}.
   * 是否启用自顶向下优化。此配置可以被CalciteConnectionProperty#TOPDOWN_OPT覆盖
   *
   * <p>Note: Enabling top-down optimization will automatically disable
   * the use of AbstractConverter and related rules.
   * 注意:启用自顶向下优化将自动禁用AbstractConverter和相关规则的使用
   */
  public static final CalciteSystemProperty<Boolean> TOPDOWN_OPT = // 定义公共静态常量TOPDOWN_OPT属性
      booleanProperty("calcite.planner.topdown.opt", false); // 调用booleanProperty方法创建布尔属性,键为"calcite.planner.topdown.opt",默认值为false

  /**
   * Whether to run integration tests.
   * 是否运行集成测试
   */
  // TODO review zabetak:
  // The property is used in only one place and it is associated with mongodb. Should we drop this
  // property and just use TEST_MONGODB?
  // TODO:该属性只在一个地方使用,并且与mongodb相关。我们应该删除这个属性,只使用TEST_MONGODB吗?
  public static final CalciteSystemProperty<Boolean> INTEGRATION_TEST = // 定义公共静态常量INTEGRATION_TEST属性
      booleanProperty("calcite.integrationTest", false); // 调用booleanProperty方法创建布尔属性,键为"calcite.integrationTest",默认值为false

  /**
   * Which database to use for tests that require a JDBC data source.
   * 对于需要JDBC数据源的测试,使用哪个数据库
   *
   * <p>The property can take one of the following values:
   * 该属性可以采用以下值之一:
   *
   * <ul>
   *   <li>HSQLDB (default)</li>
   *   <li>H2</li>
   *   <li>MYSQL</li>
   *   <li>ORACLE</li>
   *   <li>POSTGRESQL</li>
   * </ul>
   *
   * <p>If the specified value is not included in the previous list, the default
   * is used.
   * 如果指定的值不包含在上面的列表中,则使用默认值
   *
   * <p>We recommend that casual users use hsqldb, and frequent Calcite
   * developers use MySQL. The test suite runs faster against the MySQL database
   * (mainly because of the 0.1 second versus 6 seconds startup time). You have
   * to populate MySQL manually with the foodmart data set, otherwise there will
   * be test failures.
   * 我们建议临时用户使用hsqldb,而频繁的Calcite开发者使用MySQL。测试套件在MySQL数据库上运行得更快
   * (主要是因为0.1秒对比6秒的启动时间)。你必须手动用foodmart数据集填充MySQL,否则会有测试失败
   */
  public static final CalciteSystemProperty<String> TEST_DB = // 定义公共静态常量TEST_DB属性,类型为CalciteSystemProperty<String>
      stringProperty("calcite.test.db", "HSQLDB", // 调用stringProperty方法创建字符串属性,键为"calcite.test.db",默认值为"HSQLDB"
          ImmutableSet.of( // 创建不可变集合,包含所有允许的数据库名称
              "HSQLDB", // 允许的数据库:HSQLDB
              "H2", // 允许的数据库:H2
              "MYSQL", // 允许的数据库:MYSQL
              "ORACLE", // 允许的数据库:ORACLE
              "POSTGRESQL", // 允许的数据库:POSTGRESQL
              "STARROCKS", // 允许的数据库:STARROCKS
              "DORIS")); // 允许的数据库:DORIS

  /**
   * Path to the dataset file that should used for integration tests.
   * 集成测试应该使用的数据集文件的路径
   *
   * <p>If a path is not set, then one of the following values will be used:
   * 如果未设置路径,则将使用以下值之一:
   *
   * <ul>
   *   <li>../calcite-test-dataset</li>
   *   <li>../../calcite-test-dataset</li>
   *   <li>.</li>
   * </ul>
   * The first valid path that exists in the filesystem will be chosen.
   * 将选择文件系统中存在的第一个有效路径
   */
  public static final CalciteSystemProperty<String> TEST_DATASET_PATH = // 定义公共静态常量TEST_DATASET_PATH属性
      new CalciteSystemProperty<>("calcite.test.dataset", v -> { // 创建CalciteSystemProperty实例,键为"calcite.test.dataset",使用lambda表达式解析值
        if (v != null) { // 如果属性值不为null
          return v; // 直接返回该值
        }
        final String[] dirs = { // 定义可能的目录路径数组
            "../calcite-test-dataset", // 第一个可能的路径
            "../../calcite-test-dataset" // 第二个可能的路径
        };
        for (String s : dirs) { // 遍历每个可能的路径
          try { // 尝试检查路径是否存在
            if (new File(s).exists() && new File(s, "vm").exists()) { // 如果路径存在且包含vm子目录
              return s; // 返回该路径
            }
          } catch (SecurityException ignore) { // 捕获安全异常
            // Ignore SecurityException on purpose because if
            // we can't get to the file we fall through.
            // 忽略SecurityException,因为如果我们无法访问文件,则继续尝试下一个路径
          }
        }
        return "."; // 如果没有找到有效路径,返回当前目录
      });

  /**
   * Whether to run Arrow tests.
   * 是否运行Arrow测试
   */
  public static final CalciteSystemProperty<Boolean> TEST_ARROW = // 定义公共静态常量TEST_ARROW属性
      booleanProperty("calcite.test.arrow", true); // 调用booleanProperty方法创建布尔属性,键为"calcite.test.arrow",默认值为true

  /**
   * Whether to run MongoDB tests.
   * 是否运行MongoDB测试
   */
  public static final CalciteSystemProperty<Boolean> TEST_MONGODB = // 定义公共静态常量TEST_MONGODB属性
      booleanProperty("calcite.test.mongodb", true); // 调用booleanProperty方法创建布尔属性,键为"calcite.test.mongodb",默认值为true

  /**
   * Whether to run Splunk tests.
   * 是否运行Splunk测试
   *
   * <p>Disabled by default, because we do not expect Splunk to be installed
   * and populated with the data set necessary for testing.
   * 默认情况下禁用,因为我们不期望Splunk已安装并填充了测试所需的数据集
   */
  public static final CalciteSystemProperty<Boolean> TEST_SPLUNK = // 定义公共静态常量TEST_SPLUNK属性
      booleanProperty("calcite.test.splunk", false); // 调用booleanProperty方法创建布尔属性,键为"calcite.test.splunk",默认值为false

  /**
   * Whether to run Druid tests.
   * 是否运行Druid测试
   */
  public static final CalciteSystemProperty<Boolean> TEST_DRUID = // 定义公共静态常量TEST_DRUID属性
      booleanProperty("calcite.test.druid", false); // 调用booleanProperty方法创建布尔属性,键为"calcite.test.druid",默认值为false

  /**
   * Whether to run Cassandra tests.
   * 是否运行Cassandra测试
   */
  public static final CalciteSystemProperty<Boolean> TEST_CASSANDRA = // 定义公共静态常量TEST_CASSANDRA属性
      booleanProperty("calcite.test.cassandra", true); // 调用booleanProperty方法创建布尔属性,键为"calcite.test.cassandra",默认值为true

  /**
   * Whether to run InnoDB tests.
   * 是否运行InnoDB测试
   */
  public static final CalciteSystemProperty<Boolean> TEST_INNODB = // 定义公共静态常量TEST_INNODB属性
      booleanProperty("calcite.test.innodb", true); // 调用booleanProperty方法创建布尔属性,键为"calcite.test.innodb",默认值为true

  /**
   * Whether to run Redis tests.
   * 是否运行Redis测试
   */
  public static final CalciteSystemProperty<Boolean> TEST_REDIS = // 定义公共静态常量TEST_REDIS属性
      booleanProperty("calcite.test.redis", true); // 调用booleanProperty方法创建布尔属性,键为"calcite.test.redis",默认值为true

  /**
   * Whether to use
   * <a href="https://www.testcontainers.org/">Docker containers</a> in tests.
   * 是否在测试中使用Docker容器
   *
   * <p>If the property is set to <code>true</code>, affected tests will attempt
   * to start Docker containers; when Docker is not available tests fallback to
   * other execution modes and if it's not possible they are skipped entirely.
   * 如果该属性设置为true,受影响的测试将尝试启动Docker容器;当Docker不可用时,测试将回退到其他执行模式,
   * 如果不可能则完全跳过
   *
   * <p>If the property is set to <code>false</code>, Docker containers are not
   * used at all and affected tests either fallback to other execution modes or
   * skipped entirely.
   * 如果该属性设置为false,则完全不使用Docker容器,受影响的测试要么回退到其他执行模式,要么完全跳过
   *
   * <p>Users can override the default behavior to force non-Dockerized
   * execution even when Docker is installed on the machine; this can be useful
   * for replicating an issue that appears only in non-docker test mode or for
   * running tests both with and without containers in CI.
   * 用户可以覆盖默认行为,强制非Docker化执行,即使机器上安装了Docker;这对于复制仅在非Docker测试模式下出现的问题,
   * 或在CI中使用和不使用容器运行测试都很有用
   */
  public static final CalciteSystemProperty<Boolean> TEST_WITH_DOCKER_CONTAINER = // 定义公共静态常量TEST_WITH_DOCKER_CONTAINER属性
      booleanProperty("calcite.test.docker", true); // 调用booleanProperty方法创建布尔属性,键为"calcite.test.docker",默认值为true

  /**
   * A list of ids designating the queries
   * (from query.json in new.hydromatic:foodmart-queries:0.4.1)
   * that should be run as part of FoodmartTest.
   * 指定查询的ID列表(来自new.hydromatic:foodmart-queries:0.4.1中的query.json),这些查询应该作为FoodmartTest的一部分运行
   */
  // TODO review zabetak:
  // The name of the property is not appropriate. A better alternative would be
  // calcite.test.foodmart.queries.ids. Moreover, I am not in favor of using system properties for
  // parameterized tests.
  // TODO:属性名不合适。更好的替代方案是calcite.test.foodmart.queries.ids。此外,我不赞成使用系统属性进行参数化测试
  public static final CalciteSystemProperty<@Nullable String> TEST_FOODMART_QUERY_IDS = // 定义公共静态常量TEST_FOODMART_QUERY_IDS属性,可能为null
      new CalciteSystemProperty<>("calcite.ids", Function.<@Nullable String>identity()); // 创建属性实例,键为"calcite.ids",使用恒等函数解析值

  /**
   * Whether the optimizer will consider adding converters of infinite cost in
   * order to convert a relational expression from one calling convention to
   * another.
   * 优化器是否会考虑添加无限成本的转换器,以便将关系表达式从一个调用约定转换为另一个
   */
  public static final CalciteSystemProperty<Boolean> ALLOW_INFINITE_COST_CONVERTERS = // 定义公共静态常量ALLOW_INFINITE_COST_CONVERTERS属性
      booleanProperty("calcite.opt.allowInfiniteCostConverters", true); // 调用booleanProperty方法创建布尔属性,键为"calcite.opt.allowInfiniteCostConverters",默认值为true

  /**
   * The name of the default character set.
   * 默认字符集的名称
   *
   * <p>It is used by {@link org.apache.calcite.sql.validate.SqlValidator}.
   * 它被SqlValidator使用
   */
  // TODO review zabetak:
  // What happens if a wrong value is specified?
  // TODO:如果指定了错误的值会发生什么?
  public static final CalciteSystemProperty<String> DEFAULT_CHARSET = // 定义公共静态常量DEFAULT_CHARSET属性
      stringProperty("calcite.default.charset", "ISO-8859-1"); // 调用stringProperty方法创建字符串属性,键为"calcite.default.charset",默认值为"ISO-8859-1"

  /**
   * The name of the default national character set.
   * 默认国家字符集的名称
   *
   * <p>It is used with the N'string' construct in
   * {@link org.apache.calcite.sql.SqlLiteral}
   * and may be different from the {@link #DEFAULT_CHARSET}.
   * 它与SqlLiteral中的N'string'构造一起使用,可能与DEFAULT_CHARSET不同
   */
  // TODO review zabetak:
  // What happens if a wrong value is specified?
  // TODO:如果指定了错误的值会发生什么?
  public static final CalciteSystemProperty<String> DEFAULT_NATIONAL_CHARSET = // 定义公共静态常量DEFAULT_NATIONAL_CHARSET属性
      stringProperty("calcite.default.nationalcharset", "ISO-8859-1"); // 调用stringProperty方法创建字符串属性,键为"calcite.default.nationalcharset",默认值为"ISO-8859-1"

  /**
   * The name of the default collation.
   * 默认排序规则的名称
   *
   * <p>It is used in {@link org.apache.calcite.sql.SqlCollation} and
   * {@link org.apache.calcite.sql.SqlLiteral}.
   * 它在SqlCollation和SqlLiteral中使用
   */
  // TODO review zabetak:
  // What happens if a wrong value is specified?
  // TODO:如果指定了错误的值会发生什么?
  public static final CalciteSystemProperty<String> DEFAULT_COLLATION = // 定义公共静态常量DEFAULT_COLLATION属性
      stringProperty("calcite.default.collation.name", "ISO-8859-1$en_US"); // 调用stringProperty方法创建字符串属性,键为"calcite.default.collation.name",默认值为"ISO-8859-1$en_US"

  /**
   * The strength of the default collation.
   * 默认排序规则的强度
   * Allowed values (as defined in {@link java.text.Collator}) are: primary, secondary,
   * tertiary, identical.
   * 允许的值(如java.text.Collator中所定义)为:primary(主要)、secondary(次要)、tertiary(第三级)、identical(相同)
   *
   * <p>It is used in {@link org.apache.calcite.sql.SqlCollation} and
   * {@link org.apache.calcite.sql.SqlLiteral}.
   * 它在SqlCollation和SqlLiteral中使用
   */
  // TODO review zabetak:
  // What happens if a wrong value is specified?
  // TODO:如果指定了错误的值会发生什么?
  public static final CalciteSystemProperty<String> DEFAULT_COLLATION_STRENGTH = // 定义公共静态常量DEFAULT_COLLATION_STRENGTH属性
      stringProperty("calcite.default.collation.strength", "primary"); // 调用stringProperty方法创建字符串属性,键为"calcite.default.collation.strength",默认值为"primary"

  /**
   * The maximum size of the cache of metadata handlers.
   * 元数据处理器缓存的最大大小
   *
   * <p>A typical value is the number of queries being concurrently prepared
   * multiplied by the number of types of metadata.
   * 典型值是并发准备的查询数乘以元数据类型的数量
   *
   * <p>If the value is less than 0, there is no limit.
   * 如果值小于0,则没有限制
   */
  public static final CalciteSystemProperty<Integer> METADATA_HANDLER_CACHE_MAXIMUM_SIZE = // 定义公共静态常量METADATA_HANDLER_CACHE_MAXIMUM_SIZE属性
      intProperty("calcite.metadata.handler.cache.maximum.size", 1000); // 调用intProperty方法创建整数属性,键为"calcite.metadata.handler.cache.maximum.size",默认值为1000

  /**
   * The maximum size of the cache used for storing Bindable objects,
   * instantiated via dynamically generated Java classes.
   * 用于存储Bindable对象的缓存的最大大小,这些对象通过动态生成的Java类实例化
   *
   * <p>The default value is 0.
   * 默认值为0
   *
   * <p>The property can take any value between [0, {@link Integer#MAX_VALUE}]
   * inclusive. If the value is not valid (or not specified) then the default
   * value is used.
   * 该属性可以采用[0, Integer.MAX_VALUE]之间的任何值(包含)。如果值无效(或未指定),则使用默认值
   *
   * <p>The cached objects may be quite big so it is suggested to use a rather
   * small cache size (e.g., 1000). For the most common use cases a number close
   * to 1000 should be enough to alleviate the performance penalty of compiling
   * and loading classes.
   * 缓存的对象可能相当大,因此建议使用较小的缓存大小(例如1000)。对于最常见的用例,接近1000的数字应该足以减轻编译和加载类的性能损失
   *
   * <p>Setting this property to 0 disables the cache.
   * 将此属性设置为0将禁用缓存
   */
  public static final CalciteSystemProperty<Integer> BINDABLE_CACHE_MAX_SIZE = // 定义公共静态常量BINDABLE_CACHE_MAX_SIZE属性
      intProperty("calcite.bindable.cache.maxSize", 0, v -> v >= 0); // 调用intProperty方法创建整数属性,键为"calcite.bindable.cache.maxSize",默认值为0,验证条件为v >= 0

  /**
   * The concurrency level of the cache used for storing Bindable objects,
   * instantiated via dynamically generated Java classes.
   * 用于存储Bindable对象的缓存的并发级别,这些对象通过动态生成的Java类实例化
   *
   * <p>The default value is 1.
   * 默认值为1
   *
   * <p>The property can take any value between [1, {@link Integer#MAX_VALUE}]
   * inclusive. If the value is not valid (or not specified) then the default
   * value is used.
   * 该属性可以采用[1, Integer.MAX_VALUE]之间的任何值(包含)。如果值无效(或未指定),则使用默认值
   *
   * <p>This property has no effect if the cache is disabled (i.e.,
   * {@link #BINDABLE_CACHE_MAX_SIZE} set to 0.
   * 如果禁用了缓存(即BINDABLE_CACHE_MAX_SIZE设置为0),则此属性无效
   */
  public static final CalciteSystemProperty<Integer> BINDABLE_CACHE_CONCURRENCY_LEVEL = // 定义公共静态常量BINDABLE_CACHE_CONCURRENCY_LEVEL属性
      intProperty("calcite.bindable.cache.concurrencyLevel", 1, v -> v >= 1); // 调用intProperty方法创建整数属性,键为"calcite.bindable.cache.concurrencyLevel",默认值为1,验证条件为v >= 1

  /**
   * The maximum number of items in a function-level cache.
   * 函数级缓存中的最大项数
   *
   * <p>A few SQL functions have expensive processing that, if its results are
   * cached, can be reused by future calls to the function. One such function
   * is {@code RLIKE}, whose arguments are a regular expression and a string.
   * The regular expression needs to be compiled to a
   * {@link java.util.regex.Pattern}. Compilation is expensive, and within a
   * particular query, the arguments are often the same string, or a small
   * number of distinct strings, so caching makes sense.
   * 一些SQL函数有昂贵的处理,如果缓存其结果,可以被函数的未来调用重用。其中一个函数是RLIKE,
   * 其参数是正则表达式和字符串。正则表达式需要被编译为Pattern。编译是昂贵的,
   * 并且在特定查询中,参数通常是相同的字符串或少量不同的字符串,因此缓存是有意义的
   *
   * <p>Therefore, functions such as {@code RLIKE}, {@code SIMILAR TO},
   * {@code PARSE_URL}, {@code PARSE_TIMESTAMP}, {@code FORMAT_DATE} have a
   * function-level cache. The cache is created in the code generated for the
   * query, at the call site of the function, and expires when the query has
   * finished executing. Such caches do not need time-based expiration, but
   * we need to cap the size of the cache to deal with scenarios such as a
   * billion-row table where every row has a distinct regular expression.
   * 因此,诸如RLIKE、SIMILAR TO、PARSE_URL、PARSE_TIMESTAMP、FORMAT_DATE等函数具有函数级缓存。
   * 缓存是在为查询生成的代码中创建的,位于函数的调用站点,并在查询完成执行时过期。
   * 此类缓存不需要基于时间的过期,但我们需要限制缓存的大小以处理诸如十亿行表每行都有不同正则表达式的情况
   *
   * <p>Because of how Calcite generates and executes code in Enumerable
   * convention, each function object is used from a single thread. Therefore,
   * non thread-safe objects such as {@link java.text.DateFormat} can be safely
   * cached.
   * 由于Calcite在Enumerable约定中生成和执行代码的方式,每个函数对象都从单个线程使用。
   * 因此,非线程安全的对象(如DateFormat)可以安全地缓存
   *
   * <p>The value of this parameter limits the size of every function-level
   * cache in Calcite. The default value is 1,000.
   * 此参数的值限制了Calcite中每个函数级缓存的大小。默认值为1,000
   */
  public static final CalciteSystemProperty<Integer> FUNCTION_LEVEL_CACHE_MAX_SIZE = // 定义公共静态常量FUNCTION_LEVEL_CACHE_MAX_SIZE属性
      intProperty("calcite.function.cache.maxSize", 1_000, v -> v >= 0); // 调用intProperty方法创建整数属性,键为"calcite.function.cache.maxSize",默认值为1000,验证条件为v >= 0

  /**
   * Minimum numbers of fields in a Join result that will trigger the "compact code generation".
   * 触发"紧凑代码生成"的Join结果中的最小字段数
   * This feature reduces the risk of running into a compilation error due to the code of a
   * dynamically generated method growing beyond the 64KB limit.
   * 此功能减少了由于动态生成方法的代码增长超过64KB限制而导致编译错误的风险
   *
   * <p>Note that the compact code makes use of arraycopy operations when possible,
   * instead of using a static array initialization. For joins with a large number of fields
   * the resulting code should be faster, but it can be slower for joins with a very small number
   * of fields.
   * 请注意,紧凑代码尽可能使用arraycopy操作,而不是使用静态数组初始化。对于具有大量字段的连接,
   * 生成的代码应该更快,但对于具有非常少字段的连接,它可能会更慢
   *
   * <p>The default value is 100, a negative value disables completely the "compact code" feature.
   * 默认值为100,负值将完全禁用"紧凑代码"功能
   *
   * @see org.apache.calcite.adapter.enumerable.EnumUtils
   */
  public static final CalciteSystemProperty<Integer> JOIN_SELECTOR_COMPACT_CODE_THRESHOLD = // 定义公共静态常量JOIN_SELECTOR_COMPACT_CODE_THRESHOLD属性
      intProperty("calcite.join.selector.compact.code.threshold", 100); // 调用intProperty方法创建整数属性,键为"calcite.join.selector.compact.code.threshold",默认值为100

  private static CalciteSystemProperty<Boolean> booleanProperty(String key, // 定义私有静态方法booleanProperty,用于创建布尔类型的系统属性
      boolean defaultValue) { // 参数key:属性键,参数defaultValue:默认值
    // Note that "" -> true (convenient for command-lines flags like '-Dflag')
    // 注意:"" -> true(对于像'-Dflag'这样的命令行标志很方便)
    return new CalciteSystemProperty<>(key, // 创建CalciteSystemProperty实例,传入属性键
        v -> v == null ? defaultValue // 使用lambda表达式解析值:如果值为null,返回默认值
            : v.isEmpty() || parseBoolean(v)); // 否则,如果值为空字符串或解析为true,则返回true
  }

  private static CalciteSystemProperty<Integer> intProperty(String key, int defaultValue) { // 定义私有静态方法intProperty,用于创建整数类型的系统属性(无验证)
    return intProperty(key, defaultValue, v -> true); // 调用带验证器的intProperty方法,验证器始终返回true(不验证)
  }

  /**
   * Returns the value of the system property with the specified name as {@code
   * int}. If any of the conditions below hold, returns the
   * <code>defaultValue</code>:
   * 返回具有指定名称的系统属性的值作为int。如果满足以下任何条件,则返回defaultValue:
   *
   * <ol>
   * <li>the property is not defined;
   * 属性未定义
   * <li>the property value cannot be transformed to an int;
   * 属性值无法转换为int
   * <li>the property value does not satisfy the checker.
   * 属性值不满足验证器
   * </ol>
   */
  private static CalciteSystemProperty<Integer> intProperty(String key, int defaultValue, // 定义私有静态方法intProperty,用于创建整数类型的系统属性(带验证)
      IntPredicate valueChecker) { // 参数key:属性键,参数defaultValue:默认值,参数valueChecker:整数值验证器
    return new CalciteSystemProperty<>(key, v -> { // 创建CalciteSystemProperty实例,使用lambda表达式解析值
      if (v == null) { // 如果属性值为null
        return defaultValue; // 返回默认值
      }
      try { // 尝试解析整数值
        int intVal = parseInt(v); // 将字符串解析为整数
        return valueChecker.test(intVal) ? intVal : defaultValue; // 如果值通过验证器测试,返回该值,否则返回默认值
      } catch (NumberFormatException nfe) { // 捕获数字格式异常
        return defaultValue; // 返回默认值
      }
    });
  }

  private static CalciteSystemProperty<String> stringProperty(String key, String defaultValue) { // 定义私有静态方法stringProperty,用于创建字符串类型的系统属性(无验证)
    return new CalciteSystemProperty<>(key, v -> v == null ? defaultValue : v); // 创建CalciteSystemProperty实例,使用lambda表达式:如果值为null返回默认值,否则返回该值
  }

  private static CalciteSystemProperty<String> stringProperty( // 定义私有静态方法stringProperty,用于创建字符串类型的系统属性(带允许值验证)
      String key, // 参数key:属性键
      String defaultValue, // 参数defaultValue:默认值
      Set<String> allowedValues) { // 参数allowedValues:允许的值集合
    return new CalciteSystemProperty<>(key, v -> { // 创建CalciteSystemProperty实例,使用lambda表达式解析值
      if (v == null) { // 如果属性值为null
        return defaultValue; // 返回默认值
      }
      String normalizedValue = v.toUpperCase(Locale.ROOT); // 将值转换为大写(使用ROOT语言环境)
      return allowedValues.contains(normalizedValue) ? normalizedValue : defaultValue; // 如果值在允许的集合中,返回该值,否则返回默认值
    });
  }

  private static Properties loadProperties() { // 定义私有静态方法loadProperties,用于加载所有Calcite相关的系统属性
    Properties saffronProperties = new Properties(); // 创建Properties对象,用于存储saffron.properties文件中的属性
    ClassLoader classLoader = // 获取类加载器,用于读取资源文件
        firstNonNull(Thread.currentThread().getContextClassLoader(), // 首先尝试获取当前线程的上下文类加载器
            CalciteSystemProperty.class.getClassLoader()); // 如果为null,则使用CalciteSystemProperty类的类加载器
    // Read properties from the file "saffron.properties", if it exists in classpath
    // 从类路径中读取"saffron.properties"文件中的属性(如果存在)
    try (InputStream stream = requireNonNull(classLoader, "classLoader") // 使用try-with-resources,检查类加载器不为null
        .getResourceAsStream("saffron.properties")) { // 获取saffron.properties文件的输入流
      if (stream != null) { // 如果流不为null(文件存在)
        saffronProperties.load(stream); // 加载属性文件内容到saffronProperties对象
      }
    } catch (IOException e) { // 捕获IO异常
      throw new RuntimeException("while reading from saffron.properties file", e); // 抛出运行时异常,包装原始异常
    } catch (SecurityException ignore) { // 捕获安全异常
      // Ignore SecurityException on purpose because if
      // we can't get to the file we fall through.
      // 故意忽略SecurityException,因为如果我们无法访问文件,则继续执行
    }

    // Merge system and saffron properties, mapping deprecated saffron
    // namespaces to calcite
    // 合并系统和saffron属性,将已弃用的saffron命名空间映射到calcite
    final Properties allProperties = new Properties(); // 创建最终的Properties对象,用于存储所有合并后的属性
    Stream.concat( // 合并两个流
        saffronProperties.entrySet().stream(), // 第一个流:saffronProperties的所有条目
        System.getProperties().entrySet().stream()) // 第二个流:系统属性的所有条目
        .forEach(prop -> { // 遍历每个属性条目
          String deprecatedKey = (String) prop.getKey(); // 获取属性键(可能已弃用)
          String newKey = deprecatedKey // 创建新的属性键,替换已弃用的命名空间
              .replace("net.sf.saffron.", "calcite.") // 将"net.sf.saffron."替换为"calcite."
              .replace("saffron.", "calcite."); // 将"saffron."替换为"calcite."
          if (newKey.startsWith("calcite.")) { // 如果新键以"calcite."开头
            allProperties.setProperty(newKey, (String) prop.getValue()); // 将属性设置到allProperties中,使用新的键
          }
        });
    return allProperties; // 返回合并后的所有属性
  }

  private final T value; // 定义私有最终成员变量value,存储解析后的属性值

  private CalciteSystemProperty(String key, // 定义私有构造方法,创建CalciteSystemProperty实例
      Function<? super @Nullable String, ? extends T> valueParser) { // 参数key:属性键,参数valueParser:值解析函数,用于将字符串解析为类型T
    this.value = valueParser.apply(PROPERTIES.getProperty(key)); // 使用解析函数解析属性值,从PROPERTIES中获取指定键的值并解析,存储到value字段
  }

  /**
   * Returns the value of this property.
   * 返回此属性的值
   *
   * @return the value of this property or <code>null</code> if a default value has not been
   * defined for this property.
   * 返回此属性的值,如果未为此属性定义默认值,则返回null
   */
  public T value() { // 定义公共方法value,用于获取属性值
    return value; // 返回存储的属性值
  }
} // 类定义结束
