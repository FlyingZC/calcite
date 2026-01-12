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
package org.apache.calcite.adapter.druid; // 包声明：Druid适配器包，包含与Druid数据源交互的核心类

import com.fasterxml.jackson.core.JsonGenerator; // Jackson库：用于JSON生成，将Druid查询对象序列化为JSON格式

import org.checkerframework.checker.nullness.qual.Nullable; // CheckerFramework注解：标记可空类型，用于静态分析

import java.io.IOException; // Java IO异常类：处理输入输出操作中的异常

import static org.apache.calcite.adapter.druid.DruidQuery.writeField; // 静态导入：Druid查询工具方法，用于写入字段到JSON
import static org.apache.calcite.adapter.druid.DruidQuery.writeFieldIf; // 静态导入：Druid查询工具方法，条件性地写入字段到JSON
import static org.apache.calcite.util.DateTimeStringUtils.ISO_DATETIME_FRACTIONAL_SECOND_FORMAT; // 静态导入：ISO日期时间格式常量，包含秒的小数部分

import static java.util.Objects.requireNonNull; // 静态导入：Objects工具方法，用于检查对象非空

/**
 * Implementation of extraction function DimensionSpec. // 类说明：提取函数维度规范的实现类
 *
 * <p>The extraction function implementation returns dimension values transformed // 提取函数实现返回经过给定提取函数转换后的维度值
 * using the given extraction function. // 使用指定的提取函数对维度值进行转换
 */
public class ExtractionDimensionSpec implements DimensionSpec { // 类定义：提取维度规范类，实现DimensionSpec接口，用于定义Druid查询中如何提取和转换维度数据
  private final String dimension; // 成员变量：维度名称，指定要从Druid数据源中提取的原始维度字段名
  private final ExtractionFunction extractionFunction; // 成员变量：提取函数对象，定义如何转换维度值的逻辑（如时间提取、格式化等）
  private final String outputName; // 成员变量：输出名称，指定转换后的维度在结果集中的字段名（可为null）
  private final DruidType outputType; // 成员变量：输出类型，指定转换后维度值的Druid数据类型（如STRING、LONG等）

  public ExtractionDimensionSpec(String dimension, ExtractionFunction extractionFunction, // 构造方法：创建提取维度规范对象，默认输出类型为STRING
      String outputName) { // 参数：dimension维度名称，extractionFunction提取函数，outputName输出名称
    this(dimension, extractionFunction, outputName, DruidType.STRING); // 调用全参构造方法，使用默认的STRING类型作为输出类型
  }

  public ExtractionDimensionSpec(String dimension, ExtractionFunction extractionFunction, // 构造方法：创建提取维度规范对象，可指定输出类型
      String outputName, DruidType outputType) { // 参数：dimension维度名称，extractionFunction提取函数，outputName输出名称，outputType输出类型
    this.dimension = requireNonNull(dimension, "dimension"); // 初始化维度名称，使用requireNonNull确保dimension不为null，否则抛出NullPointerException
    this.extractionFunction = // 初始化提取函数，使用requireNonNull确保extractionFunction不为null
        requireNonNull(extractionFunction, "extractionFunction"); // 否则抛出NullPointerException
    this.outputName = outputName; // 初始化输出名称，可为null表示使用默认名称
    this.outputType = outputType == null ? DruidType.STRING : outputType; // 初始化输出类型，如果为null则使用默认的STRING类型
  }

  @Override public String getOutputName() { // 方法：获取输出名称，实现DimensionSpec接口方法
    return outputName; // 返回输出名称字段值
  }

  @Override public DruidType getOutputType() { // 方法：获取输出类型，实现DimensionSpec接口方法
    return outputType; // 返回输出类型字段值
  }

  @Override public ExtractionFunction getExtractionFn() { // 方法：获取提取函数，实现DimensionSpec接口方法
    return extractionFunction; // 返回提取函数对象
  }

  @Override public String getDimension() { // 方法：获取维度名称，实现DimensionSpec接口方法
    return dimension; // 返回维度名称
  }

  @Override public void write(JsonGenerator generator) throws IOException { // 方法：将此维度规范序列化为JSON格式，实现DimensionSpec接口方法
    generator.writeStartObject(); // 开始写入JSON对象，生成"{"字符
    generator.writeStringField("type", "extraction"); // 写入type字段，值为"extraction"，标识这是提取类型的维度规范
    generator.writeStringField("dimension", dimension); // 写入dimension字段，值为维度名称
    writeFieldIf(generator, "outputName", outputName); // 条件性地写入outputName字段，仅当outputName不为null时写入
    writeField(generator, "extractionFn", extractionFunction); // 写入extractionFn字段，值为提取函数对象（会递归调用提取函数的write方法）
    generator.writeEndObject(); // 结束JSON对象，生成"}"字符
  }

  /** Returns a valid {@link Granularity} of floor extract, or null when not // 方法说明：返回有效的floor提取粒度，如果无法转换则返回null
   * possible. // 当无法转换为粒度时返回null
   *
   * @param dimensionSpec Druid Dimension specification // 参数说明：Druid维度规范对象
   */
  public static @Nullable Granularity toQueryGranularity(DimensionSpec dimensionSpec) { // 静态方法：尝试将维度规范转换为Druid查询粒度，用于优化时间维度查询
    if (!DruidTable.DEFAULT_TIMESTAMP_COLUMN.equals(dimensionSpec.getDimension())) { // 检查维度是否为默认时间列"__time"
      // Only __time column can be substituted by granularity // 只有__time时间列可以被粒度替代
      return null; // 如果不是时间列，返回null表示无法转换
    }
    final ExtractionFunction extractionFunction = dimensionSpec.getExtractionFn(); // 获取维度规范中的提取函数
    if (extractionFunction == null) { // 检查提取函数是否存在
      // No Extract thus no Granularity // 没有提取函数就无法转换为粒度
      return null; // 返回null表示无法转换
    }
    if (extractionFunction instanceof TimeExtractionFunction) { // 检查提取函数是否是时间提取函数
      Granularity granularity = ((TimeExtractionFunction) extractionFunction).getGranularity(); // 获取时间提取函数中的粒度（如DAY、HOUR等）
      String format = ((TimeExtractionFunction) extractionFunction).getFormat(); // 获取时间提取函数中的格式字符串
      if (!ISO_DATETIME_FRACTIONAL_SECOND_FORMAT.equals(format)) { // 检查格式是否为ISO标准日期时间格式（包含秒的小数部分）
        return null; // 如果格式不是ISO标准格式，返回null表示无法转换
      }
      return granularity; // 返回提取出的粒度对象，可用于优化Druid查询
    }
    return null; // 如果提取函数不是时间提取函数，返回null表示无法转换
  }

}
