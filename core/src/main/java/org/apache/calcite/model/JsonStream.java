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
package org.apache.calcite.model; // 定义包名，该类属于 org.apache.calcite.model 包，用于 Calcite 模型相关的 JSON 配置

import com.fasterxml.jackson.annotation.JsonCreator; // 导入 Jackson 注解，标记构造方法用于 JSON 反序列化，Jackson 库会使用此注解来创建对象实例
import com.fasterxml.jackson.annotation.JsonProperty; // 导入 Jackson 注解，标记属性用于 JSON 序列化和反序列化，将 JSON 字段映射到 Java 对象属性

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 CheckerFramework 注解，标记参数可能为 null，用于静态空值检查

/**
 * Information about whether a table allows streaming.
 * 关于表是否允许流式处理的信息类
 *
 * <p>Occurs within {@link JsonTable#stream}.
 * 该类作为 JsonTable 类的 stream 属性出现，用于在 JSON 模型配置中描述表的流式处理特性
 *
 * @see org.apache.calcite.model.JsonRoot Description of schema elements
 * @see org.apache.calcite.model.JsonTable#stream
 */
public class JsonStream { // 定义 JsonStream 类，用于封装表流式处理相关的配置信息
  /** Whether the table allows streaming.
   * 标识表是否允许流式处理的布尔标志
   *
   * <p>Optional; default true.
   * 该字段是可选的，默认值为 true，表示如果 JSON 配置中未指定该字段，则默认表支持流式处理
   */
  public final boolean stream; // 定义公共 final 成员变量 stream，表示表是否支持流式查询，final 表示一旦赋值不可修改

  /** Whether the history of the table is available.
   * 标识表的历史记录是否可用的布尔标志
   *
   * <p>Optional; default false.
   * 该字段是可选的，默认值为 false，表示如果 JSON 配置中未指定该字段，则默认表的历史记录不可用
   */
  public final boolean history; // 定义公共 final 成员变量 history，表示表的历史数据是否可用，用于支持时间旅行查询等功能

  @JsonCreator // 使用 Jackson 的 JsonCreator 注解标记构造方法，指示 Jackson 在从 JSON 反序列化时使用此构造方法创建对象实例
  public JsonStream( // 定义构造方法，接收 stream 和 history 两个可选参数，用于创建 JsonStream 对象
      @JsonProperty("stream") @Nullable Boolean stream, // 参数 stream：使用 JsonProperty 注解映射 JSON 中的 "stream" 字段，@Nullable 表示该参数可以为 null
      @JsonProperty("history") @Nullable Boolean history) { // 参数 history：使用 JsonProperty 注解映射 JSON 中的 "history" 字段，@Nullable 表示该参数可以为 null
    this.stream = stream == null || stream; // 赋值 stream 成员变量：如果传入的 stream 参数为 null，则使用默认值 true；否则使用传入的值（null || true = true，null || false = false）
    this.history = history != null && history; // 赋值 history 成员变量：只有当传入的 history 参数不为 null 且值为 true 时，才设置为 true；否则设置为 false（确保默认值为 false）
  }
}
