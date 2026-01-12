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
package org.apache.calcite.adapter.druid; // 声明包名，该类属于org.apache.calcite.adapter.druid包，是Calcite适配Druid数据源的核心包

import com.fasterxml.jackson.core.JsonGenerator; // 导入Jackson库的JsonGenerator类，用于生成JSON格式的输出

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的Nullable注解，用于标记可能为null的参数或返回值

import java.io.IOException; // 导入Java IO异常类，用于处理JSON生成过程中可能出现的IO异常

import static java.util.Objects.requireNonNull; // 静态导入Objects.requireNonNull方法，用于参数非空校验

/**
 * DefaultDimensionSpec类是DimensionSpec接口的默认实现
 * 
 * <p>该类用于定义Druid查询中的维度规范，提供了最基础的维度提取功能
 * 
 * <p>主要功能：
 * 1. 返回维度值本身，不进行任何转换或提取操作
 * 2. 支持可选的维度重命名功能，可以为维度指定不同的输出名称
 * 3. 支持指定输出类型，默认为STRING类型
 * 4. 实现了DimensionSpec接口，可以序列化为JSON格式供Druid使用
 * 
 * <p>使用场景：
 * - 当查询需要直接使用原始维度值时
 * - 当需要将维度重命名为更友好的名称时
 * - 当需要指定维度值的输出类型时
 * 
 * <p>典型JSON输出示例：
 * {
 *   "type": "default",
 *   "dimension": "country",
 *   "outputName": "国家",
 *   "outputType": "STRING"
 * }
 */
public class DefaultDimensionSpec implements DimensionSpec { // 定义DefaultDimensionSpec类，实现DimensionSpec接口

  private final String dimension; // 维度字段名，指定要从数据源中提取的维度列名，例如"country"、"city"等
  private final String outputName; // 输出字段名，指定该维度在查询结果中的显示名称，可以与dimension相同也可以不同
  private final DruidType outputType; // 输出数据类型，指定维度值的数据类型，默认为STRING类型，支持多种Druid数据类型

  /**
   * 全参数构造方法
   * 
   * @param dimension 维度字段名，不能为null，指定要提取的原始维度列名
   * @param outputName 输出字段名，不能为null，指定该维度在结果集中的显示名称
   * @param outputType 输出数据类型，可以为null，如果为null则默认使用STRING类型
   * 
   * 该构造方法创建一个完整的DefaultDimensionSpec实例，允许自定义所有属性
   */
  public DefaultDimensionSpec(String dimension, String outputName, // 构造方法声明，接收维度名、输出名和输出类型三个参数
      @Nullable DruidType outputType) { // outputType参数使用@Nullable注解，表示可以为null
    this.dimension = requireNonNull(dimension, "dimension"); // 使用requireNonNull校验dimension参数不为null，否则抛出NullPointerException
    this.outputName = requireNonNull(outputName, "outputName"); // 使用requireNonNull校验outputName参数不为null，否则抛出NullPointerException
    this.outputType = outputType == null ? DruidType.STRING : outputType; // 如果outputType为null则使用默认值STRING，否则使用指定的类型
  }

  /**
   * 简化构造方法
   * 
   * @param dimension 维度字段名，同时用作输出字段名
   * 
   * 该构造方法创建一个DefaultDimensionSpec实例，输出名称与维度名称相同，输出类型使用默认的STRING类型
   * 这是一个便捷方法，用于不需要重命名维度和指定特殊类型的场景
   */
  public DefaultDimensionSpec(String dimension) { // 构造方法声明，只接收一个维度名参数
    this(dimension, dimension, null); // 调用全参数构造方法，将dimension同时作为dimension和outputName，outputType设为null（将使用默认值STRING）
  }

  /**
   * 将DimensionSpec序列化为JSON格式
   * 
   * @param generator JSON生成器，用于输出JSON格式的数据
   * @throws IOException 如果JSON生成过程中发生IO错误
   * 
   * 该方法实现DimensionSpec接口的write方法，将当前DimensionSpec实例序列化为Druid可识别的JSON格式
   * 生成的JSON包含type、dimension、outputName和outputType四个字段
   */
  @Override public void write(JsonGenerator generator) throws IOException { // 重写DimensionSpec接口的write方法，接收JsonGenerator参数，可能抛出IOException
    generator.writeStartObject(); // 开始写入JSON对象，输出"{"字符
    generator.writeStringField("type", "default"); // 写入type字段，值为"default"，表示这是默认的维度规范实现
    generator.writeStringField("dimension", dimension); // 写入dimension字段，值为当前实例的dimension属性，指定要提取的维度列名
    generator.writeStringField("outputName", outputName); // 写入outputName字段，值为当前实例的outputName属性，指定输出字段名
    generator.writeStringField("outputType", outputType.name()); // 写入outputType字段，值为outputType的名称（如"STRING"、"LONG"等）
    generator.writeEndObject(); // 结束写入JSON对象，输出"}"字符
  }

  /**
   * 获取输出字段名
   * 
   * @return 输出字段名字符串
   * 
   * 该方法实现DimensionSpec接口的getOutputName方法，返回该维度在查询结果中的显示名称
   */
  @Override public String getOutputName() { // 重写DimensionSpec接口的getOutputName方法
    return outputName; // 返回outputName属性的值
  }

  /**
   * 获取输出数据类型
   * 
   * @return DruidType枚举值，表示维度值的数据类型
   * 
   * 该方法实现DimensionSpec接口的getOutputType方法，返回该维度值的数据类型
   * 常见的类型包括STRING、LONG、FLOAT等
   */
  @Override public DruidType getOutputType() { // 重写DimensionSpec接口的getOutputType方法
    return outputType; // 返回outputType属性的值
  }

  /**
   * 获取提取函数
   * 
   * @return null，因为默认实现不使用提取函数
   * 
   * 该方法实现DimensionSpec接口的getExtractionFn方法
   * DefaultDimensionSpec是基础实现，不进行任何值转换，因此总是返回null
   * 其他实现（如ExtractionDimensionSpec）可能会返回非null的提取函数
   */
  @Override public @Nullable ExtractionFunction getExtractionFn() { // 重写DimensionSpec接口的getExtractionFn方法，返回值可能为null
    return null; // 返回null，表示没有提取函数，直接使用原始维度值
  }

  /**
   * 获取维度字段名
   * 
   * @return 维度字段名字符串
   * 
   * 该方法实现DimensionSpec接口的getDimension方法，返回要从数据源中提取的原始维度列名
   */
  @Override public String getDimension() { // 重写DimensionSpec接口的getDimension方法
    return dimension; // 返回dimension属性的值
  }
}
