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
package org.apache.calcite.adapter.splunk; // 指定该类所在的包，属于Calcite的Splunk适配器模块

import org.apache.calcite.adapter.splunk.search.SplunkConnection; // 导入Splunk连接类，用于与Splunk服务器建立连接和通信
import org.apache.calcite.adapter.splunk.util.StringUtils; // 导入字符串工具类，用于字符串编码和处理
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入抽象可枚举类，LINQ4J框架中的基础类，用于实现可枚举的数据集合
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历数据集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的参数或字段

import java.util.HashMap; // 导入HashMap类，用于存储键值对
import java.util.List; // 导入List接口，用于存储有序的元素集合
import java.util.Map; // 导入Map接口，用于存储键值对映射

import static java.util.Objects.requireNonNull; // 导入Objects工具类的requireNonNull方法，用于参数非空验证

/**
 * Query against Splunk. // Splunk查询类，用于构建和执行对Splunk的查询操作
 * // 该类是Calcite适配器Splunk的核心组件之一，负责将Calcite的查询转换为Splunk的搜索语言并执行
 * // 它继承自AbstractEnumerable，实现了可枚举的数据集合接口，使得查询结果可以通过LINQ4J框架进行迭代
 * // 该类封装了与Splunk服务器通信的连接对象、搜索语句、时间范围和字段列表等查询参数
 * // 通过enumerator()方法返回一个枚举器，用于逐行遍历查询结果
 *
 * @param <T> Element type // 泛型参数T，表示查询结果中每行数据的元素类型，通常是Map<String, Object>类型
 */
public class SplunkQuery<T> extends AbstractEnumerable<T> { // 定义SplunkQuery类，继承AbstractEnumerable，实现可枚举功能
  private final SplunkConnection splunkConnection; // Splunk连接对象，用于与Splunk服务器建立连接并执行搜索操作，是查询的基础
  private final String search; // Splunk搜索语句字符串，使用Splunk搜索语言编写的查询条件，是查询的核心内容
  private final @Nullable String earliest; // 查询的最早时间，可选参数，用于限定查询的时间范围起始点，格式为Splunk时间格式
  private final @Nullable String latest; // 查询的最晚时间，可选参数，用于限定查询的时间范围结束点，格式为Splunk时间格式
  private final @Nullable List<String> fieldList; // 查询返回的字段列表，可选参数，用于指定需要返回哪些字段，为null时返回所有字段

  /** Creates a SplunkQuery. */ // 构造方法注释：创建一个SplunkQuery实例，初始化所有查询参数
  public SplunkQuery( // 构造方法，用于创建SplunkQuery对象并初始化查询参数
      SplunkConnection splunkConnection, // 参数：Splunk连接对象，不能为null，用于与Splunk服务器通信
      String search, // 参数：搜索语句字符串，不能为null，指定要执行的Splunk搜索查询
      @Nullable String earliest, // 参数：最早时间，可为null，用于限定查询的时间范围起始点
      @Nullable String latest, // 参数：最晚时间，可为null，用于限定查询的时间范围结束点
      @Nullable List<String> fieldList) { // 参数：字段列表，可为null，用于指定需要返回的字段
    this.splunkConnection = // 将传入的Splunk连接对象赋值给成员变量
        requireNonNull(splunkConnection, "splunkConnection"); // 验证splunkConnection参数不为null，否则抛出NullPointerException
    this.search = requireNonNull(search, "search"); // 验证search参数不为null，否则抛出NullPointerException，并赋值给成员变量
    this.earliest = earliest; // 将earliest参数赋值给成员变量，可能为null
    this.latest = latest; // 将latest参数赋值给成员变量，可能为null
    this.fieldList = fieldList; // 将fieldList参数赋值给成员变量，可能为null
  }

  @Override public String toString() { // 重写toString方法，用于返回对象的字符串表示
    return "SplunkQuery {" + search + "}"; // 返回包含搜索语句的字符串表示，格式为"SplunkQuery {搜索语句}"
  }

  @Override public Enumerator<T> enumerator() { // 实现AbstractEnumerable的enumerator方法，用于创建并返回查询结果的枚举器
    //noinspection unchecked // 忽略未检查的类型转换警告，因为泛型擦除导致运行时无法检查类型
    return (Enumerator<T>) splunkConnection.getSearchResultEnumerator( // 调用Splunk连接对象的getSearchResultEnumerator方法执行搜索并返回结果枚举器
        search, // 传入搜索语句
        getArgs(), // 传入查询参数映射（包含时间范围和字段列表等）
        fieldList); // 传入字段列表，用于指定返回的字段
  }

  private Map<String, String> getArgs() { // 私有方法，用于构建查询参数映射，将查询参数转换为Splunk API所需的格式
    Map<String, String> args = new HashMap<>(); // 创建一个HashMap用于存储查询参数的键值对
    if (fieldList != null) { // 如果字段列表不为null，则处理字段列表参数
      String fields = // 将字段列表编码为逗号分隔的字符串
          StringUtils.encodeList(fieldList, ',').toString(); // 使用StringUtils工具类将字段列表编码为逗号分隔的字符串
      args.put("field_list", fields); // 将编码后的字段列表字符串放入参数映射中，键为"field_list"
    }
    if (earliest != null) { // 如果最早时间不为null，则处理最早时间参数
      args.put("earliest_time", earliest); // 将最早时间放入参数映射中，键为"earliest_time"
    }
    if (latest != null) { // 如果最晚时间不为null，则处理最晚时间参数
      args.put("latest_time", latest); // 将最晚时间放入参数映射中，键为"latest_time"
    }
    return args; // 返回构建好的查询参数映射
  }
}
