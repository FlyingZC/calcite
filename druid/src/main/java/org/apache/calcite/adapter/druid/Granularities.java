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
package org.apache.calcite.adapter.druid; // 声明包名，该类属于org.apache.calcite.adapter.druid包，是Calcite适配Druid数据源的核心组件之一

import org.apache.calcite.avatica.util.TimeUnitRange; // 导入TimeUnitRange类，表示时间单位范围（如年、月、日、时、分、秒等），用于定义时间粒度的时间单位

import com.fasterxml.jackson.core.JsonGenerator; // 导入JsonGenerator类，用于生成JSON格式的输出，Druid查询需要将Granularity对象序列化为JSON格式发送给Druid服务器

import java.io.IOException; // 导入IOException类，处理JSON生成过程中可能出现的输入输出异常

import static org.apache.calcite.adapter.druid.DruidQuery.writeFieldIf; // 导入静态方法writeFieldIf，用于在JSON生成时有条件地写入字段（当字段值不为空时才写入）

import static java.util.Objects.requireNonNull; // 导入静态方法requireNonNull，用于检查对象是否为null，如果为null则抛出NullPointerException，用于参数校验

/**
 * Factory methods and helpers for {@link Granularity}. // Granularities类是Granularity对象的工厂类和辅助工具类
 * // 该类提供了创建各种粒度对象的工厂方法，包括ALL粒度（将所有行聚合为一行）和基于时间单位的粒度（如年、月、日等）
 * // Granularity是Druid查询中的核心概念，用于定义时间维度的聚合粒度，决定了数据在时间轴上的分组方式
 * // 例如：DAY粒度会将数据按天分组，HOUR粒度会将数据按小时分组，ALL粒度会将所有数据聚合成一行
 * // 该类是工具类，所有方法都是静态的，不需要实例化，因此私有构造方法防止被实例化
 */
public class Granularities { // 定义Granularities类，这是一个工具类，提供创建Granularity对象的工厂方法
  // Private constructor for utility class // 私有构造方法，防止工具类被实例化，遵循工具类的设计模式
  private Granularities() {} // 私有无参构造方法，抛出异常防止通过反射实例化，确保该类只能通过静态方法使用

  /** Returns a Granularity that causes all rows to be rolled up into one. */ // 返回一个ALL粒度的Granularity对象，该粒度会将所有行聚合为一行
  public static Granularity all() { // 定义静态工厂方法all()，返回ALL粒度的Granularity实例
    return AllGranularity.INSTANCE; // 返回AllGranularity枚举的单例实例INSTANCE，AllGranularity是Granularity接口的实现类，表示将所有数据聚合为一个结果
  } // 方法结束，返回ALL粒度对象，用于不需要时间分组的聚合查询

  /** Creates a Granularity based on a time unit. // 根据时间单位创建Granularity对象，支持年、季度、月、周、日、时、分、秒等时间粒度
   *
   * <p>When used in a query, Druid will rollup and round time values based on // 当在查询中使用时，Druid会根据指定的周期和时区对时间值进行聚合和四舍五入
   * specified period and timezone. */ // 例如：DAY粒度会将时间戳四舍五入到天，HOUR粒度会将时间戳四舍五入到小时
  public static Granularity createGranularity(TimeUnitRange timeUnit, // 定义静态工厂方法createGranularity，参数timeUnit是时间单位范围（如YEAR、MONTH、DAY等）
      String timeZone) { // 参数timeZone是时区字符串（如"UTC"、"Asia/Shanghai"），用于时间计算和四舍五入
    switch (timeUnit) { // 根据timeUnit的值选择对应的时间粒度类型
    case YEAR: // 如果时间单位是年
      return new PeriodGranularity(Granularity.Type.YEAR, "P1Y", timeZone); // 创建PeriodGranularity对象，类型为YEAR，周期为"P1Y"（1年），使用指定的时区
    case QUARTER: // 如果时间单位是季度
      return new PeriodGranularity(Granularity.Type.QUARTER, "P3M", timeZone); // 创建PeriodGranularity对象，类型为QUARTER，周期为"P3M"（3个月），使用指定的时区
    case MONTH: // 如果时间单位是月
      return new PeriodGranularity(Granularity.Type.MONTH, "P1M", timeZone); // 创建PeriodGranularity对象，类型为MONTH，周期为"P1M"（1个月），使用指定的时区
    case WEEK: // 如果时间单位是周
      return new PeriodGranularity(Granularity.Type.WEEK, "P1W", timeZone); // 创建PeriodGranularity对象，类型为WEEK，周期为"P1W"（1周），使用指定的时区
    case DAY: // 如果时间单位是天
      return new PeriodGranularity(Granularity.Type.DAY, "P1D", timeZone); // 创建PeriodGranularity对象，类型为DAY，周期为"P1D"（1天），使用指定的时区
    case HOUR: // 如果时间单位是小时
      return new PeriodGranularity(Granularity.Type.HOUR, "PT1H", timeZone); // 创建PeriodGranularity对象，类型为HOUR，周期为"PT1H"（1小时），使用指定的时区
    case MINUTE: // 如果时间单位是分钟
      return new PeriodGranularity(Granularity.Type.MINUTE, "PT1M", timeZone); // 创建PeriodGranularity对象，类型为MINUTE，周期为"PT1M"（1分钟），使用指定的时区
    case SECOND: // 如果时间单位是秒
      return new PeriodGranularity(Granularity.Type.SECOND, "PT1S", timeZone); // 创建PeriodGranularity对象，类型为SECOND，周期为"PT1S"（1秒），使用指定的时区
    default: // 如果时间单位不是以上任何一种（理论上不应该发生，因为TimeUnitRange枚举只包含以上值）
      throw new AssertionError(timeUnit); // 抛出AssertionError，表示遇到了未预期的时间单位，这是代码逻辑错误
    } // switch语句结束
  } // 方法结束，返回创建的PeriodGranularity对象

  /** Implementation of {@link Granularity} for {@link Granularity.Type#ALL}. // AllGranularity是Granularity接口的实现类，用于表示ALL粒度类型
   * A singleton. */ // 该类是单例模式，只有一个实例INSTANCE，因为ALL粒度不需要任何参数，所有实例都相同
  private enum AllGranularity implements Granularity { // 定义私有枚举类AllGranularity，实现Granularity接口，表示将所有数据聚合为一个结果的粒度
    INSTANCE; // 定义枚举常量INSTANCE，这是AllGranularity的唯一实例，通过Granularities.all()方法获取

    @Override public void write(JsonGenerator generator) throws IOException { // 重写write方法，将ALL粒度序列化为JSON格式
      generator.writeObject("all"); // 向JSON生成器写入字符串"all"，表示这是一个ALL粒度，Druid会识别这个字符串并将所有行聚合为一行
    } // write方法结束，完成了ALL粒度的JSON序列化

    @Override public Type getType() { // 重写getType方法，返回粒度的类型
      return Type.ALL; // 返回Type.ALL，表示这是ALL类型的粒度
    } // getType方法结束，返回粒度类型
  } // AllGranularity枚举类结束

  /** Implementation of {@link Granularity} based on a time unit. // PeriodGranularity是Granularity接口的实现类，基于时间单位（年、月、日等）定义粒度
   * Corresponds to PeriodGranularity in Druid. */ // 该类对应Druid中的PeriodGranularity概念，使用ISO-8601格式的周期字符串（如P1D表示1天）来定义时间粒度
  private static class PeriodGranularity implements Granularity { // 定义私有静态内部类PeriodGranularity，实现Granularity接口，表示基于时间周期的粒度
    private final Type type; // 成员变量type：粒度类型，枚举值包括YEAR、QUARTER、MONTH、WEEK、DAY、HOUR、MINUTE、SECOND等
    private final String period; // 成员变量period：时间周期字符串，使用ISO-8601格式，如"P1Y"（1年）、"P1D"（1天）、"PT1H"（1小时）
    private final String timeZone; // 成员变量timeZone：时区字符串，如"UTC"、"Asia/Shanghai"，用于时间计算和四舍五入

    private PeriodGranularity(Type type, String period, String timeZone) { // 私有构造方法，创建PeriodGranularity实例
      this.type = requireNonNull(type, "type"); // 初始化type成员变量，使用requireNonNull检查type参数不为null，否则抛出NullPointerException
      this.period = requireNonNull(period, "period"); // 初始化period成员变量，使用requireNonNull检查period参数不为null，否则抛出NullPointerException
      this.timeZone = requireNonNull(timeZone, "timeZone"); // 初始化timeZone成员变量，使用requireNonNull检查timeZone参数不为null，否则抛出NullPointerException
    } // 构造方法结束，完成PeriodGranularity对象的初始化

    @Override public void write(JsonGenerator generator) throws IOException { // 重写write方法，将PeriodGranularity序列化为JSON格式，用于发送给Druid服务器
      generator.writeStartObject(); // 开始写入JSON对象，对应Druid查询中的粒度配置
      generator.writeStringField("type", "period"); // 写入type字段，值为"period"，表示这是一个PeriodGranularity类型的粒度
      writeFieldIf(generator, "period", period); // 有条件地写入period字段，如果period不为null则写入，值如"P1D"、"PT1H"等
      writeFieldIf(generator, "timeZone", timeZone); // 有条件地写入timeZone字段，如果timeZone不为null则写入，值如"UTC"、"Asia/Shanghai"等
      generator.writeEndObject(); // 结束JSON对象的写入，完成PeriodGranularity的JSON序列化
    } // write方法结束，完成了PeriodGranularity的JSON序列化

    @Override public Type getType() { // 重写getType方法，返回粒度的类型
      return type; // 返回type成员变量的值，即粒度类型（YEAR、MONTH、DAY等）
    } // getType方法结束，返回粒度类型
  } // PeriodGranularity类结束
} // Granularities类结束
