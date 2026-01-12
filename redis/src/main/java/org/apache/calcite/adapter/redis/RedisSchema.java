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
package org.apache.calcite.adapter.redis; // 指定当前类所在的包路径，Redis适配器包

import org.apache.calcite.model.JsonCustomTable; // 导入JsonCustomTable类，用于表示自定义表的JSON模型
import org.apache.calcite.schema.Table; // 导入Table接口，Calcite中的表抽象
import org.apache.calcite.schema.impl.AbstractSchema; // 导入AbstractSchema抽象类，作为Schema的基类

import org.apache.commons.lang3.StringUtils; // 导入StringUtils工具类，用于字符串操作

import com.google.common.cache.CacheBuilder; // 导入CacheBuilder，用于构建缓存
import com.google.common.cache.CacheLoader; // 导入CacheLoader，用于缓存加载
import com.google.common.collect.ImmutableSet; // 导入ImmutableSet，不可变集合
import com.google.common.collect.Maps; // 导入Maps工具类，用于Map操作

import java.util.ArrayList; // 导入ArrayList动态数组
import java.util.Arrays; // 导入Arrays数组工具类
import java.util.LinkedHashMap; // 导入LinkedHashMap，保持插入顺序的Map
import java.util.List; // 导入List接口
import java.util.Map; // 导入Map接口
import java.util.Set; // 导入Set接口
import java.util.stream.Collectors; // 导入流式处理工具

import static java.util.Objects.requireNonNull; // 导入requireNonNull静态方法，用于空值检查

/**
 * Schema mapped onto a set of URLs / HTML tables. Each table in the schema
 * is an HTML table on a URL.
 */ // 类的JavaDoc注释：映射到一组URL/HTML表的Schema，Schema中的每个表都是URL上的HTML表
class RedisSchema extends AbstractSchema { // RedisSchema类继承自AbstractSchema，表示Redis数据库的Schema（数据库模式）
  public final String host; // 成员变量：Redis服务器的主机地址（IP或域名），使用final修饰表示不可变
  public final int port; // 成员变量：Redis服务器的端口号，默认为6379，使用final修饰表示不可变
  public final int database; // 成员变量：Redis数据库编号（0-15），使用final修饰表示不可变
  public final String password; // 成员变量：Redis服务器的密码，如果Redis设置了密码则需要提供，使用final修饰表示不可变
  public final List<Map<String, Object>> tables; // 成员变量：表定义列表，每个Map表示一个表的配置信息（包括表名、字段、数据格式等），使用final修饰表示不可变

  RedisSchema(String host, // 构造方法：创建RedisSchema实例，host参数指定Redis服务器主机地址
      int port, // 构造方法参数：port指定Redis服务器端口号
      int database, // 构造方法参数：database指定Redis数据库编号
      String password, // 构造方法参数：password指定Redis服务器密码
      List<Map<String, Object>> tables) { // 构造方法参数：tables指定表定义列表
    this.host = host; // 将传入的host参数赋值给成员变量host
    this.port = port; // 将传入的port参数赋值给成员变量port
    this.database = database; // 将传入的database参数赋值给成员变量database
    this.password = password; // 将传入的password参数赋值给成员变量password
    this.tables = tables; // 将传入的tables参数赋值给成员变量tables
  } // 构造方法结束

  @Override protected Map<String, Table> getTableMap() { // 重写AbstractSchema的getTableMap方法，返回Schema中所有表的映射（表名->Table对象）
    JsonCustomTable[] jsonCustomTables = new JsonCustomTable[tables.size()]; // 创建JsonCustomTable数组，大小与tables列表相同，用于存储表定义
    Set<String> tableNames = Arrays.stream(tables.toArray(jsonCustomTables)) // 将tables列表转换为数组流，然后处理每个表定义
        .map(e -> e.name).collect(Collectors.toSet()); // 提取每个表的name属性，收集到Set集合中（去重）
    return Maps.asMap(ImmutableSet.copyOf(tableNames), // 返回一个不可变的Map，键为表名，值为通过缓存加载器创建的Table对象
        CacheBuilder.newBuilder() // 创建一个新的缓存构建器
            .build(CacheLoader.from(this::table))); // 构建缓存，使用table方法作为缓存加载器，当访问表名时自动创建对应的Table对象
  } // getTableMap方法结束

  private Table table(String tableName) { // 私有方法：根据表名创建并返回对应的Table对象（RedisTable实例）
    RedisConfig redisConfig = new RedisConfig(host, port, database, password); // 创建RedisConfig配置对象，封装Redis连接信息（主机、端口、数据库、密码）
    return RedisTable.create(RedisSchema.this, tableName, redisConfig, null); // 调用RedisTable的静态工厂方法create创建RedisTable实例，传入当前Schema、表名、Redis配置和null（表示无额外字段信息）
  } // table方法结束

  public RedisTableFieldInfo getTableFieldInfo(String tableName) { // 公共方法：根据表名获取表的字段信息（包括字段列表、数据格式、键分隔符等）
    RedisTableFieldInfo tableFieldInfo = new RedisTableFieldInfo(); // 创建RedisTableFieldInfo对象，用于存储表的字段信息
    List<LinkedHashMap<String, Object>> fields = new ArrayList<>(); // 创建字段列表，每个LinkedHashMap表示一个字段的详细信息（名称、类型等）
    String dataFormat = ""; // 初始化数据格式字符串，用于存储Redis数据的存储格式（如raw、hash等）
    String keyDelimiter = ""; // 初始化键分隔符字符串，用于存储Redis键的分隔符（如":"、"_"等）
    @SuppressWarnings({"unchecked", "rawtypes"}) // 抑制编译器警告，因为需要进行类型转换
    List<JsonCustomTable> jsonCustomTables = // 将tables列表强制转换为List<JsonCustomTable>类型
        (List<JsonCustomTable>) (List) this.tables; // 类型转换：先转为List，再转为List<JsonCustomTable>
    for (JsonCustomTable jsonCustomTable : jsonCustomTables) { // 遍历所有表定义，查找与tableName匹配的表
      if (jsonCustomTable.name.equals(tableName)) { // 如果当前表的名称与传入的tableName匹配
        Map<String, Object> map = // 获取表的operand属性（包含表的配置信息），并检查是否为null
            requireNonNull(jsonCustomTable.operand, "operand"); // 如果operand为null则抛出NullPointerException
        if (map.get("dataFormat") == null) { // 检查dataFormat配置项是否存在
          throw new RuntimeException("dataFormat is null"); // 如果dataFormat为null则抛出运行时异常
        } // dataFormat检查结束
        if (map.get("fields") == null) { // 检查fields配置项是否存在
          throw new RuntimeException("fields is null"); // 如果fields为null则抛出运行时异常
        } // fields检查结束
        dataFormat = map.get("dataFormat").toString(); // 从配置中获取dataFormat值并转换为字符串
        fields = (List<LinkedHashMap<String, Object>>) map.get("fields"); // 从配置中获取fields字段列表并强制转换为指定类型
        if (map.get("keyDelimiter") != null) { // 检查keyDelimiter配置项是否存在（可选配置）
          keyDelimiter = map.get("keyDelimiter").toString(); // 如果keyDelimiter存在则获取其值并转换为字符串
        } // keyDelimiter检查结束
        break; // 找到匹配的表后退出循环
      } // 表名匹配判断结束
    } // for循环结束
    tableFieldInfo.setTableName(tableName); // 将表名设置到tableFieldInfo对象中
    tableFieldInfo.setDataFormat(dataFormat); // 将数据格式设置到tableFieldInfo对象中
    tableFieldInfo.setFields(fields); // 将字段列表设置到tableFieldInfo对象中
    if (StringUtils.isNotEmpty(keyDelimiter)) { // 如果keyDelimiter不为空（使用StringUtils工具类检查）
      tableFieldInfo.setKeyDelimiter(keyDelimiter); // 将键分隔符设置到tableFieldInfo对象中
    } // keyDelimiter设置结束
    return tableFieldInfo; // 返回填充完整字段信息的tableFieldInfo对象
  } // getTableFieldInfo方法结束
} // RedisSchema类结束
