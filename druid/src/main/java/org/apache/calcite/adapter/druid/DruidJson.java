/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，查看随本工作分发的NOTICE文件
 * this work for additional information regarding copyright ownership.  // 获取有关版权所有权的更多信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF根据Apache许可证2.0版授权给您
 * (the "License"); you may not use this file except in compliance with    // ("许可证")；除非遵守许可证，否则您不得使用此文件
 * the License.  You may obtain a copy of the License at                 // 您可以在以下位置获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0                             // 许可证网址
 *
 * Unless required by applicable law or agreed to in writing, software     // 除非适用法律要求或书面同意，否则
 * distributed under the License is distributed on an "AS IS" BASIS,     // 根据许可证分发的软件是按"原样"基础分发的
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不附任何明示或暗示的担保或条件
 * See the License for the specific language governing permissions and   // 请参阅许可证以了解管理权限和
 * limitations under the License.                                        // 限制的具体语言
 */
package org.apache.calcite.adapter.druid; // 声明包名：org.apache.calcite.adapter.druid，表示这是Calcite的Druid适配器包

import com.fasterxml.jackson.core.JsonGenerator; // 导入Jackson库的JsonGenerator类，用于生成JSON数据

import java.io.IOException; // 导入Java IO异常类，用于处理输入输出异常

/** Object that knows how to write itself to a // 这是一个接口，定义了能够将自己写入到
 * {@link com.fasterxml.jackson.core.JsonGenerator}. */ // JsonGenerator中的对象，即将对象序列化为JSON格式
public interface DruidJson { // 定义DruidJson接口，这是Druid适配器中所有需要序列化为JSON的类的公共接口
  void write(JsonGenerator generator) throws IOException; // 定义write方法，将当前对象写入到JsonGenerator中，可能抛出IOException异常
} // 接口定义结束
