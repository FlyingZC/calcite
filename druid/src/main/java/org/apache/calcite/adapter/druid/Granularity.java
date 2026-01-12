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
 */ // Apache 许可证头部，声明版权和使用条款
package org.apache.calcite.adapter.druid; // 声明包名，该类属于 Druid 适配器包

import java.util.Locale; // 导入 Locale 类，用于处理区域相关的字符串转换

/**
 * A strategy by which Druid rolls up rows into sub-totals based on their
 * timestamp values.
 *
 * <p>Typical granularities are based upon time units (e.g. 1 day or
 * 15 minutes). A special granularity, all, combines all rows into a single
 * total.
 *
 * <p>A Granularity instance is immutable, and generates a JSON string as
 * part of a Druid query.
 *
 * @see Granularities
 */ // Granularity 接口：Druid 根据时间戳值将行聚合为子总计的策略接口
public interface Granularity extends DruidJson { // 定义 Granularity 接口，继承 DruidJson 接口，使其可以生成 JSON 格式的查询
  /** Type of supported periods for granularity. */ // Type 枚举：支持的粒度时间段类型
  enum Type { // 定义 Type 枚举，列出所有支持的粒度类型
    ALL, // ALL 粒度：将所有行聚合成一个总计，不按时间分组
    YEAR, // YEAR 粒度：按年份分组聚合数据
    QUARTER, // QUARTER 粒度：按季度分组聚合数据
    MONTH, // MONTH 粒度：按月份分组聚合数据
    WEEK, // WEEK 粒度：按周分组聚合数据
    DAY, // DAY 粒度：按天分组聚合数据
    HOUR, // HOUR 粒度：按小时分组聚合数据
    MINUTE, // MINUTE 粒度：按分钟分组聚合数据
    SECOND; // SECOND 粒度：按秒分组聚合数据

    /** Lower-case name, e.g. "all", "minute". */ // lowerName 字段：枚举值的小写名称，例如 "all", "minute"
    public final String lowerName = name().toLowerCase(Locale.ROOT); // 将枚举名称转换为小写，使用 ROOT Locale 确保一致性，用于 JSON 序列化
  } // Type 枚举结束

  Type getType(); // getType 方法：获取当前粒度的类型，返回 Type 枚举值
} // Granularity 接口结束
