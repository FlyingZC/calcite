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
package org.apache.calcite.adapter.druid; // 包声明：定义该接口所属的包为org.apache.calcite.adapter.druid，这是Calcite项目中用于适配Druid数据源的包

/**
 * Interface for Druid extraction functions. // Druid提取函数的接口定义
 *
 * <p>Extraction functions define the transformation applied to each dimension value. // 提取函数定义了对每个维度值应用的转换规则，用于在Druid查询中提取和转换维度数据
 */ // 类级注释结束：该接口是Druid适配器中用于表示提取函数的抽象接口，所有Druid提取函数都需要实现这个接口
public interface ExtractionFunction extends DruidJson { // 公共接口定义：ExtractionFunction接口继承自DruidJson接口，用于表示Druid中的提取函数，可以序列化为JSON格式
} // 接口定义结束：这是一个标记接口，本身不定义任何方法，主要用于类型标识和JSON序列化
