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
 */ // Apache许可证声明，允许在遵守Apache 2.0许可证的前提下使用此代码
package org.apache.calcite.adapter.druid; // 定义包名，该接口属于Calcite项目的Druid适配器模块

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的可空注解，用于标记可能为null的返回值

/**
 * Interface for Druid DimensionSpec.
 * Druid维度规范接口，定义了Druid中维度值的转换规则
 *
 * <p>DimensionSpecs define how dimension values get transformed prior to aggregation.
 * 维度规范定义了在进行聚合之前，维度值如何被转换
 */ // 接口文档注释，说明此接口的作用和用途
public interface DimensionSpec extends DruidJson { // 定义DimensionSpec接口，继承DruidJson接口，使其可以被序列化为JSON格式
  String getOutputName(); // 获取维度转换后的输出名称，即转换后的维度在结果集中的字段名
  DruidType getOutputType(); // 获取维度转换后的输出数据类型，返回DruidType枚举值表示的数据类型
  @Nullable ExtractionFunction getExtractionFn(); // 获取用于提取和转换维度值的提取函数，可能为null表示不进行特殊转换，@Nullable注解表示返回值可能为null
  String getDimension(); // 获取原始维度的名称，即数据源中实际存在的维度字段名
} // 接口定义结束
