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
package org.apache.calcite.adapter.druid; // Druid适配器包，包含Druid数据源相关的所有类

import org.apache.calcite.schema.Table; // Calcite表接口，定义表的基本行为
import org.apache.calcite.schema.impl.AbstractSchema; // Calcite抽象Schema基类，提供了Schema的通用实现
import org.apache.calcite.sql.type.SqlTypeName; // Calcite SQL类型名称枚举，定义所有SQL类型

import com.google.common.cache.CacheBuilder; // Google Guava缓存构建器，用于创建缓存实例
import com.google.common.cache.CacheLoader; // Google Guava缓存加载器，用于定义缓存加载逻辑
import com.google.common.collect.ImmutableMap; // Google Guava不可变Map，提供线程安全的只读Map
import com.google.common.collect.ImmutableSet; // Google Guava不可变Set，提供线程安全的只读Set
import com.google.common.collect.Maps; // Google Guava Maps工具类，提供Map操作的静态方法

import org.checkerframework.checker.nullness.qual.Nullable; // CheckerFramework注解，标记可能为null的字段

import java.util.HashMap; // Java标准HashMap实现
import java.util.LinkedHashMap; // Java标准LinkedHashMap实现，保持插入顺序
import java.util.LinkedHashSet; // Java标准LinkedHashSet实现，保持插入顺序
import java.util.List; // Java标准List接口
import java.util.Map; // Java标准Map接口
import java.util.Set; // Java标准Set接口

import static java.util.Objects.requireNonNull; // Java Objects工具类的静态导入，用于非空检查

/**
 * Schema mapped onto a Druid instance. // 映射到Druid实例的Schema类
 * // 这个类是Calcite适配器模式的核心实现，将Druid数据源抽象为Calcite的Schema
 * // Schema在数据库术语中是表的集合，这里代表一个Druid集群中的所有数据表
 * // 通过继承AbstractSchema，这个类可以无缝集成到Calcite的查询优化器中
 * // Druid是一个分布式列式存储系统，专门用于实时OLAP（在线分析处理）查询
 * // 这个类负责管理到Druid的连接，并将Druid的datasource（数据源）映射为Calcite的Table对象
 * // 主要功能包括：
 * // 1. 维护Druid服务的连接信息（查询服务和协调器服务）
 * // 2. 自动发现或手动配置Druid中的数据表
 * // 3. 使用缓存机制提高表元数据的访问性能
 * // 4. 将Druid的datasource转换为Calcite可识别的Table对象
 */
public class DruidSchema extends AbstractSchema { // DruidSchema类，继承AbstractSchema实现Druid数据源的Schema映射
  final String url; // Druid查询REST服务的URL，用于执行SQL查询，例如"http://localhost:8082"
  // 这个URL指向Druid的Broker节点，Broker节点负责接收查询、路由查询和合并结果
  // 在Druid架构中，Broker是查询的入口点，客户端通过Broker与集群交互
  // final修饰符表示这个字段在构造后不可变，保证连接URL的线程安全性

  final String coordinatorUrl; // Druid协调器REST服务的URL，用于获取元数据信息，例如"http://localhost:8081"
  // 这个URL指向Druid的Coordinator节点，Coordinator负责集群管理和数据段（segment）的分配
  // Coordinator维护着集群中所有datasource的元数据信息，包括表名、字段信息等
  // 通过coordinatorUrl可以查询集群中有哪些datasource可用
  // final修饰符表示这个字段在构造后不可变

  private final boolean discoverTables; // 是否自动发现表的标志位，true表示自动从Druid查询所有表，false表示只使用模型中显式定义的表
  // 当discoverTables为true时，Calcite会在需要时连接Druid Coordinator获取所有可用的datasource列表
  // 当discoverTables为false时，Calcite只使用在模型文件（JSON或YAML）中明确声明的表
  // 这个设计允许用户在两种模式间选择：
  // - 自动发现模式：适合快速开发，自动获取所有表，但可能包含不需要的表
  // - 手动配置模式：适合生产环境，精确控制哪些表可用，提高性能和安全性
  // private final修饰符表示这个字段只能在类内部访问，且构造后不可变

  private @Nullable Map<String, Table> tableMap; // 表名到Table对象的映射，使用延迟初始化，@Nullable表示可能为null
  // 这个Map缓存了从Druid加载的所有表信息，键是表名（即Druid的datasource名称），值是对应的Table对象
  // 使用延迟加载（lazy loading）策略：只有第一次需要时才会从Druid加载表元数据
  // 懒加载的好处：
  // 1. 避免不必要的Druid连接，提高启动速度
  // 2. 只加载实际需要访问的表，节省内存
  // 3. 减少对Druid集群的压力
  // @Nullable注解说明这个字段可能为null，表示尚未初始化
  // private修饰符确保封装性，外部代码无法直接修改这个map

  /**
   * Creates a Druid schema. // 创建Druid Schema对象的构造方法
   *
   * @param url URL of query REST service, e.g. "http://localhost:8082" // 参数url：Druid Broker查询服务的地址
   * // 这个URL是Druid Broker节点的REST API端点，用于接收和执行查询请求
   * // 典型的Broker端口是8082，但可以根据配置改变
   * // 示例：生产环境可能是"http://druid-broker.prod.example.com:8082"
   *
   * @param coordinatorUrl URL of coordinator REST service, // 参数coordinatorUrl：Druid Coordinator元数据服务的地址
   *                       e.g. "http://localhost:8081" // 典型的Coordinator端口是8081
   * // Coordinator负责维护集群的元数据，包括：
  // // 1. 数据源（datasource）列表
  * // 2. 每个datasource的schema信息（字段、类型等）
   * // 3. 数据段（segment）的分布和状态
   * // 通过这个URL可以查询集群中有哪些表可用
   *
   * @param discoverTables If true, ask Druid what tables exist; // 参数discoverTables：是否自动发现表的布尔标志
   *                       if false, only create tables explicitly in the model // true表示从Druid查询所有表，false表示只使用模型中定义的表
   * // 这个参数控制表发现策略：
   * // - true：自动发现模式，调用Druid的Metadata API获取所有datasource
   * // - false：手动配置模式，只在模型文件中声明的表才会被创建
   * // 自动发现适合开发和测试，手动配置适合生产环境
   */
  public DruidSchema(String url, String coordinatorUrl, // 构造方法开始，接收三个参数
      boolean discoverTables) { // discoverTables参数，决定表发现策略
    this.url = requireNonNull(url, "url"); // 使用requireNonNull检查url参数不为null，如果为null则抛出NullPointerException，错误信息为"url"
    // requireNonNull是Java Objects类的静态方法，用于参数校验
    // 这种防御性编程可以尽早发现配置错误，避免后续出现NullPointerException
    // 错误信息"url"帮助开发者快速定位问题

    this.coordinatorUrl = requireNonNull(coordinatorUrl, "coordinatorUrl"); // 使用requireNonNull检查coordinatorUrl参数不为null，错误信息为"coordinatorUrl"
    // 同样进行非空检查，确保coordinatorUrl有效
    // Coordinator URL对于获取表元数据是必需的，不能为空

    this.discoverTables = discoverTables; // 直接赋值discoverTables字段，不需要非空检查因为boolean类型默认为false
    // 保存表发现策略标志，后续getTableMap()方法会根据这个标志决定是否自动发现表
  } // 构造方法结束，DruidSchema对象创建完成

  @Override protected Map<String, Table> getTableMap() { // 重写父类AbstractSchema的getTableMap()方法，返回表名到Table对象的映射
    // 这个方法由Calcite框架调用，用于获取Schema中所有可用的表
    // @Override注解表明这是重写父类的方法
    // protected访问权限表示只有子类和同一包内的类可以访问
    // 返回类型Map<String, Table>：键是表名，值是Table对象

    if (!discoverTables) { // 如果discoverTables为false，表示不自动发现表
      return ImmutableMap.of(); // 返回一个空的不可变Map，表示没有表
      // ImmutableMap.of()创建一个空的不可变Map
      // 这种情况下，表必须通过其他方式（如模型文件）显式添加
      // 返回空Map而不是null，避免NullPointerException
    } // 条件判断结束

    if (tableMap == null) { // 如果tableMap尚未初始化（第一次调用）
      // 延迟初始化逻辑：只在第一次需要时才加载表信息
      // 这种懒加载模式提高了启动性能，减少了不必要的Druid连接

      final DruidConnectionImpl connection = // 创建Druid连接实现对象，用于与Druid服务通信
          new DruidConnectionImpl(url, coordinatorUrl); // 使用构造函数传入的url和coordinatorUrl创建连接
      // DruidConnectionImpl封装了与Druid REST API的交互逻辑
      // 它负责发送HTTP请求并解析响应
      // final修饰符表示这个局部变量不可重新赋值

      Set<String> tableNames = connection.tableNames(); // 通过连接对象获取所有表名（datasource名称）
      // tableNames()方法会向Druid Coordinator发送HTTP请求，查询所有可用的datasource
      // 返回的Set包含所有datasource的名称，例如["sales", "users", "events"]
      // 这个操作可能比较耗时，因为涉及网络通信

      tableMap = // 初始化tableMap字段，使用Guava的Maps.asMap创建懒加载的Map
          Maps.asMap(ImmutableSet.copyOf(tableNames), // 将表名集合转换为不可变Set，作为Map的键集合
              CacheBuilder.newBuilder() // 创建缓存构建器，用于配置缓存行为
                  .build(CacheLoader.from(name -> table(name, connection)))); // 构建缓存，使用CacheLoader.from创建加载器
      // Maps.asMap创建一个视图Map，当访问某个键时才加载对应的值
      // ImmutableSet.copyOf(tableNames)创建表名的不可变副本，确保键集合不会改变
      // CacheBuilder.newBuilder()配置缓存，可以设置过期时间、最大大小等
      // CacheLoader.from(name -> table(name, connection))定义了值的加载逻辑：
      //   - 当访问某个表名时，调用table()方法创建对应的Table对象
      //   - 使用lambda表达式(name -> table(name, connection))作为加载器
      //   - connection对象被所有表共享，避免重复创建连接
      // 这种设计实现了真正的懒加载：只有访问某个表时才会加载它的元数据
    } // tableMap初始化结束

    return tableMap; // 返回表映射Map
    // 如果tableMap已初始化，直接返回缓存的Map
    // 如果tableMap为null且discoverTables为true，会先初始化再返回
    // 返回的Map是懒加载的，访问表名时才会创建对应的Table对象
  } // getTableMap()方法结束

  private Table table(String tableName, DruidConnectionImpl connection) { // 私有方法，根据表名创建对应的Table对象
    // 这个方法由CacheLoader调用，用于懒加载单个表的元数据
    // 参数tableName：要创建的表名（Druid datasource名称）
    // 参数connection：已建立的Druid连接对象，用于查询元数据
    // 返回值：创建的DruidTable对象

    final Map<String, SqlTypeName> fieldMap = new LinkedHashMap<>(); // 创建字段映射Map，键是字段名，值是SQL类型
    // LinkedHashMap保持字段的插入顺序，这对于某些查询优化很重要
    // 这个Map将存储表的所有字段及其对应的SQL类型，例如{"id": "BIGINT", "name": "VARCHAR", "timestamp": "TIMESTAMP"}
    // final修饰符表示这个局部变量不可重新赋值

    final Set<String> metricNameSet = new LinkedHashSet<>(); // 创建度量名称集合，用于存储Druid的metric字段
    // Druid中的metric是预聚合的字段，如count、sum、avg等
    // LinkedHashSet保持插入顺序，确保metric的顺序一致
    // 这个Set将存储所有度量字段的名称，例如["count", "sum_revenue", "avg_duration"]
    // final修饰符表示这个局部变量不可重新赋值

    final Map<String, List<ComplexMetric>> complexMetrics = new HashMap<>(); // 创建复杂度量映射Map，键是字段名，值是复杂度量列表
    // Druid支持复杂类型的metric，如HyperUniqueCardinality、Sketch等
    // 这些复杂度量需要特殊处理，不能像普通字段那样直接查询
    // HashMap不保证顺序，因为复杂度量的顺序通常不重要
    // final修饰符表示这个局部变量不可重新赋值

    connection.metadata(tableName, DruidTable.DEFAULT_TIMESTAMP_COLUMN, // 调用连接对象的metadata方法，获取表的元数据
        null, fieldMap, metricNameSet, complexMetrics); // 参数：表名、默认时间戳列、null、字段Map、度量集合、复杂度量Map
    // metadata方法会向Druid发送HTTP请求，查询指定表的schema信息
    // 参数说明：
    //   - tableName：要查询的表名
    //   - DruidTable.DEFAULT_TIMESTAMP_COLUMN：默认的时间戳列名（通常是"__time"）
    //   - null：表示不限制查询的列
    //   - fieldMap：输出参数，方法会填充这个Map，包含所有字段及其类型
    //   - metricNameSet：输出参数，方法会填充这个Set，包含所有度量名称
    //   - complexMetrics：输出参数，方法会填充这个Map，包含所有复杂度量信息
    // 这个方法会修改传入的三个集合对象，将查询结果填充进去

    return DruidTable.create(DruidSchema.this, tableName, null, // 创建并返回DruidTable对象
        fieldMap, metricNameSet, DruidTable.DEFAULT_TIMESTAMP_COLUMN, // 传入字段映射、度量集合、默认时间戳列
        complexMetrics); // 传入复杂度量映射
    // DruidTable.create是静态工厂方法，用于创建DruidTable实例
    // 参数说明：
    //   - DruidSchema.this：当前DruidSchema对象的引用，DruidTable需要持有Schema引用
    //   - tableName：表名
    //   - null：表示不使用特定的视图或过滤器
    //   - fieldMap：字段的类型映射，从Druid查询得到
    //   - metricNameSet：度量名称集合，从Druid查询得到
    //   - DruidTable.DEFAULT_TIMESTAMP_COLUMN：时间戳列名，Druid每个表都有时间戳列
    //   - complexMetrics：复杂度量映射，从Druid查询得到
    // DruidTable是Table接口的实现，代表Druid中的一个datasource
    // 创建的Table对象会被缓存，后续访问同一表时直接使用缓存的Table对象
  } // table()方法结束
} // DruidSchema类定义结束
