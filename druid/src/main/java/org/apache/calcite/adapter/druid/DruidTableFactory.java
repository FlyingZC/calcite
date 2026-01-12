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
// Apache许可证头声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.adapter.druid; // 声明包名，该类位于org.apache.calcite.adapter.druid包下，是Calcite的Druid适配器包

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，用于表示关系数据类型，定义表的结构信息
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类，表示Calcite中的Schema包装器，支持动态添加表
import org.apache.calcite.schema.Table; // 导入Table接口，是Calcite中表的抽象表示
import org.apache.calcite.schema.TableFactory; // 导入TableFactory接口，用于创建表实例的工厂接口
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义SQL标准类型名称如VARCHAR、TIMESTAMP等
import org.apache.calcite.util.Util; // 导入Util工具类，提供各种实用方法如first()用于获取第一个非空值

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList，用于创建不可变的列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的参数
import org.joda.time.Interval; // 导入Joda Time的Interval类，表示时间间隔，用于指定Druid查询的时间范围
import org.joda.time.chrono.ISOChronology; // 导入ISOChronology类，用于处理ISO标准的时间系统

import java.util.ArrayList; // 导入ArrayList类，动态数组实现
import java.util.HashMap; // 导入HashMap类，哈希表实现，用于存储键值对
import java.util.LinkedHashMap; // 导入LinkedHashMap类，保持插入顺序的哈希表
import java.util.LinkedHashSet; // 导入LinkedHashSet类，保持插入顺序的集合
import java.util.List; // 导入List接口，列表集合
import java.util.Map; // 导入Map接口，键值对映射
import java.util.Set; // 导入Set接口，集合

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于检查参数不为null

/**
 * Implementation of {@link TableFactory} for Druid.
 *
 * <p>A table corresponds to what Druid calls a "data source".
 */
// Druid的TableFactory接口实现类，用于创建Druid数据源对应的Calcite表
// 在Druid中，一个表对应一个"数据源"（data source），这是Druid的核心数据组织概念
// 该工厂类负责根据配置信息创建DruidTable实例，包括维度、指标、时间戳等元数据
public class DruidTableFactory implements TableFactory { // 声明DruidTableFactory类，实现TableFactory接口
  @SuppressWarnings("unused") // 抑制"未使用"警告，虽然INSTANCE被标记为未使用，但它可能被反射调用
  public static final DruidTableFactory INSTANCE = new DruidTableFactory(); // 创建DruidTableFactory的单例实例，静态常量，供外部使用

  private DruidTableFactory() {} // 私有构造方法，防止外部创建实例，确保单例模式

  // name that is also the same name as a complex metric // 注释说明：name可能与复杂指标同名
  @Override public Table create(SchemaPlus schema, String name, Map operand, // 重写create方法，根据配置创建Druid表实例，schema是父Schema，name是表名，operand是配置参数Map，rowType是可选的行类型
      @Nullable RelDataType rowType) { // rowType参数可能为null，用于指定表的行类型定义
    final DruidSchema druidSchema = schema.unwrap(DruidSchema.class); // 从SchemaPlus中解包获取底层的DruidSchema实例，用于访问Druid连接信息
    // If "dataSource" operand is present it overrides the table name. // 如果配置中指定了dataSource参数，则覆盖表名
    final String dataSource = (String) operand.get("dataSource"); // 从operand中获取dataSource参数，这是Druid数据源名称，如果为null则使用name作为数据源名
    final Set<String> metricNameBuilder = new LinkedHashSet<>(); // 创建LinkedHashSet用于构建指标名称集合，使用LinkedHashSet保持插入顺序且自动去重
    final Map<String, SqlTypeName> fieldBuilder = new LinkedHashMap<>(); // 创建LinkedHashMap用于构建字段映射，键为字段名，值为SQL类型，使用LinkedHashMap保持字段定义顺序
    final Map<String, List<ComplexMetric>> complexMetrics = new HashMap<>(); // 创建HashMap用于存储复杂指标，键为字段名，值为该字段对应的复杂指标列表
    final String timestampColumnName; // 声明时间戳列名称变量
    final SqlTypeName timestampColumnType; // 声明时间戳列类型变量
    final Object timestampInfo = operand.get("timestampColumn"); // 从operand中获取timestampColumn配置，可能为String或Map类型
    if (timestampInfo != null) { // 如果配置了timestampColumn参数
      if (timestampInfo instanceof Map) { // 如果timestampInfo是Map类型，表示提供了详细的列配置
        Map map = (Map) timestampInfo; // 将timestampInfo强转为Map类型
        if (!(map.get("name") instanceof String)) { // 检查map中的name字段是否为String类型
          throw new IllegalArgumentException("timestampColumn array must have name"); // 如果不是String则抛出异常，要求必须提供name字段
        }
        timestampColumnName = (String) map.get("name"); // 从map中获取时间戳列的名称
        if (!(map.get("type") instanceof String) // 检查type字段是否为String类型
            || map.get("type").equals("timestamp with local time zone")) { // 或者type值为"timestamp with local time zone"
          timestampColumnType = SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE; // 设置时间戳类型为带本地时区的时间戳
        } else if (map.get("type").equals("timestamp")) { // 如果type值为"timestamp"
          timestampColumnType = SqlTypeName.TIMESTAMP; // 设置时间戳类型为普通时间戳
        } else { // 如果type值不是预期值
          throw new IllegalArgumentException("unexpected type for timestampColumn array"); // 抛出异常，提示类型不匹配
        }
      } else { // 如果timestampInfo不是Map类型
        // String (for backwards compatibility) // 则认为是String类型，保持向后兼容性
        timestampColumnName = (String) timestampInfo; // 直接将timestampInfo作为时间戳列名
        timestampColumnType = SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE; // 默认使用带本地时区的时间戳类型
      }
    } else { // 如果没有配置timestampColumn参数
      timestampColumnName = DruidTable.DEFAULT_TIMESTAMP_COLUMN; // 使用DruidTable的默认时间戳列名（通常是"__time"）
      timestampColumnType = SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE; // 使用默认的带本地时区的时间戳类型
    }
    fieldBuilder.put(timestampColumnName, timestampColumnType); // 将时间戳列名和类型添加到字段构建器中，这是Druid表必须的时间字段
    final Object dimensionsRaw = operand.get("dimensions"); // 从operand中获取dimensions配置，维度是Druid中的分组字段
    if (dimensionsRaw instanceof List) { // 如果dimensionsRaw是List类型
      // noinspection unchecked // 抑制未检查的类型转换警告
      final List<String> dimensions = (List<String>) dimensionsRaw; // 将dimensionsRaw强转为String列表，包含所有维度名称
      for (String dimension : dimensions) { // 遍历每个维度名称
        fieldBuilder.put(dimension, SqlTypeName.VARCHAR); // 将维度添加到字段构建器，类型为VARCHAR，维度在Druid中通常用于GROUP BY操作
      }
    }

    // init the complex metric map // 初始化复杂指标映射
    final Object complexMetricsRaw = operand.get("complexMetrics"); // 从operand中获取complexMetrics配置，复杂指标是Druid中的特殊指标类型如HyperUniqueCardinality
    if (complexMetricsRaw instanceof List) { // 如果complexMetricsRaw是List类型
      // noinspection unchecked // 抑制未检查的类型转换警告
      final List<String> complexMetricList = (List<String>) complexMetricsRaw; // 将complexMetricsRaw强转为String列表，包含所有复杂指标字段名
      for (String metric : complexMetricList) { // 遍历每个复杂指标字段名
        complexMetrics.put(metric, new ArrayList<>()); // 在complexMetrics映射中为每个复杂指标字段创建一个空列表，用于存储该字段上的所有复杂指标
      }
    }

    final Object metricsRaw = operand.get("metrics"); // 从operand中获取metrics配置，指标是Druid中的聚合字段如count、sum等
    if (metricsRaw instanceof List) { // 如果metricsRaw是List类型
      final List metrics = (List) metricsRaw; // 将metricsRaw强转为List，包含所有指标配置，每个指标可以是String或Map
      for (Object metric : metrics) { // 遍历每个指标配置
        DruidType druidType = DruidType.LONG; // 初始化Druid类型为LONG，这是默认的指标类型
        final String metricName; // 声明指标名称变量
        String fieldName = null; // 声明字段名变量，用于复杂指标的字段引用

        if (metric instanceof Map) { // 如果指标配置是Map类型，表示提供了详细的指标配置
          Map map2 = (Map) metric; // 将metric强转为Map类型
          if (!(map2.get("name") instanceof String)) { // 检查map中的name字段是否为String类型
            throw new IllegalArgumentException("metric must have name"); // 如果不是String则抛出异常，要求指标必须有name字段
          }
          metricName = (String) map2.get("name"); // 从map中获取指标名称

          final String type = (String) map2.get("type"); // 从map中获取指标类型，如"longSum"、"doubleSum"等
          fieldName = (String) map2.get("fieldName"); // 从map中获取字段名，对于复杂指标这是必需的

          druidType = DruidType.getTypeFromMetric(type); // 根据指标类型字符串获取对应的DruidType枚举值，如"longSum"对应LONG
        } else { // 如果指标配置不是Map类型
          metricName = (String) metric; // 则直接将metric作为指标名称，使用默认类型LONG
        }

        if (!druidType.isComplex()) { // 如果Druid类型不是复杂类型（即简单类型如LONG、FLOAT等）
          fieldBuilder.put(metricName, druidType.sqlType); // 将指标名称和对应的SQL类型添加到字段构建器中，如count指标添加为BIGINT类型
          metricNameBuilder.add(metricName); // 将指标名称添加到指标名称集合中，用于后续查询时识别可用的指标
        } else { // 如果Druid类型是复杂类型（如HyperUniqueCardinality、ApproximateHistogram等）
          requireNonNull(fieldName, "fieldName"); // 检查fieldName不为null，复杂指标必须指定字段名
          // Only add the complex metric if there exists an alias for it // 只有当complexMetrics映射中存在该字段的别名时才添加复杂指标
          if (complexMetrics.containsKey(fieldName)) { // 检查complexMetrics映射中是否包含该字段名
            SqlTypeName type = fieldBuilder.get(fieldName); // 获取该字段当前的类型
            if (type != SqlTypeName.VARCHAR) { // 如果字段类型不是VARCHAR
              fieldBuilder.put(fieldName, SqlTypeName.VARBINARY); // 将字段类型设置为VARBINARY，因为复杂指标通常以二进制形式存储
              // else, this complex metric is also a dimension, so it's type should remain as // 否则，该复杂指标同时也是维度，所以类型应保持为VARCHAR
              // VARCHAR, but it'll also be added as a complex metric. // 但它也会被添加为复杂指标
            }
            complexMetrics.get(fieldName).add(new ComplexMetric(metricName, druidType)); // 在该字段的复杂指标列表中添加新的ComplexMetric对象，包含指标名称和Druid类型
          }
        }
      }
    }
    final Object interval = operand.get("interval"); // 从operand中获取interval配置，用于指定查询的时间范围
    final List<Interval> intervals; // 声明时间间隔列表变量
    if (interval instanceof String) { // 如果interval是String类型
      intervals = // 创建包含单个时间间隔的不可变列表
          ImmutableList.of( // 使用Guava的ImmutableList创建不可变列表
              new Interval(interval, ISOChronology.getInstanceUTC())); // 根据interval字符串创建Interval对象，使用UTC时区
    } else { // 如果interval不是String类型
      intervals = null; // 将intervals设置为null，表示不限制时间范围
    }

    final String dataSourceName = Util.first(dataSource, name); // 使用Util.first方法获取第一个非空值，优先使用dataSource配置，如果为null则使用name

    if (dimensionsRaw == null || metricsRaw == null) { // 如果维度或指标配置为null，说明需要从Druid自动推断元数据
      DruidConnectionImpl connection = // 创建Druid连接实现，用于访问Druid元数据服务
          new DruidConnectionImpl(druidSchema.url, // 使用DruidSchema的URL（通常是Broker节点，端口8082）
              druidSchema.url.replace(":8082", ":8081")); // 将Broker URL替换为Coordinator URL（端口8081）以获取元数据
      return DruidTable.create(druidSchema, dataSourceName, intervals, // 创建DruidTable实例，传入所有参数包括连接对象，用于自动推断维度和指标
          fieldBuilder, metricNameBuilder, timestampColumnName, connection,
          complexMetrics);
    } else { // 如果维度和指标都已配置
      return DruidTable.create(druidSchema, dataSourceName, intervals, // 创建DruidTable实例，使用已配置的维度和指标，不需要连接对象
          fieldBuilder, metricNameBuilder, timestampColumnName, complexMetrics);
    }
  } // create方法结束，返回创建的Table实例
} // DruidTableFactory类结束
