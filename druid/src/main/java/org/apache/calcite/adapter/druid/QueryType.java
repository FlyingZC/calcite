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
package org.apache.calcite.adapter.druid; // Druid适配器包，包含将Calcite查询转换为Druid查询的相关类

/** Type of Druid query. */ // Druid查询类型枚举类，用于标识和支持Druid支持的不同查询类型
public enum QueryType { // 枚举类定义，表示Druid支持的各种查询类型
  SELECT("select"), // SELECT查询类型，对应Druid的select查询，用于执行通用的选择查询操作，支持过滤、聚合等
  TOP_N("topN"), // TOP_N查询类型，对应Druid的topN查询，用于快速获取按某个维度排序的前N条记录，性能优于group by
  GROUP_BY("groupBy"), // GROUP_BY查询类型，对应Druid的groupBy查询，用于执行分组聚合操作，支持多个维度和度量
  TIMESERIES("timeseries"), // TIMESERIES查询类型，对应Druid的timeseries查询，用于执行时间序列聚合，专门针对时间维度优化
  SCAN("scan"); // SCAN查询类型，对应Druid的scan查询，用于扫描原始数据，支持分页和排序，适合获取大量原始数据

  private final String queryName; // 成员变量：存储查询类型的字符串名称，用于与Druid查询API进行映射

  QueryType(String queryName) { // 构造方法：初始化枚举实例，接收查询名称字符串参数
    this.queryName = queryName; // 将传入的查询名称字符串赋值给成员变量queryName
  }

  public String getQueryName() { // 公共方法：获取当前查询类型的字符串名称，用于构建Druid查询请求
    return this.queryName; // 返回存储的查询名称字符串
  }
}
