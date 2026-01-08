/*
 * Licensed to the Apache Software Foundation (ASF) under one or more  // 声明本代码根据Apache许可证授权给Apache软件基金会（ASF）
 * contributor license agreements.  See the NOTICE file distributed with  // 参与者许可协议，查看随本工作分发的NOTICE文件
 * this work for additional information regarding copyright ownership.  // 获取关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF根据Apache 2.0版许可证将此文件授权给您
 * (the "License"); you may not use this file except in compliance with  // （"许可证"）；除非遵守许可证，否则您不得使用此文件
 * the License.  You may obtain a copy of the License at  // 您可以在以下位置获取许可证的副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // Apache许可证2.0版本的官方网址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意，否则
 * distributed under the License is distributed on an "AS IS" BASIS,  // 根据许可证分发的软件按"原样"基础分发
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 不提供任何形式的明示或暗示的保证或条件
 * See the License for the specific language governing permissions and  // 查看许可证以了解管理权限和
 * limitations under the License.  // 限制的特定语言
 */

/**
 * Query provider that reads from Arrow files.  // 这是一个查询提供器，用于从Arrow文件中读取数据
 * Arrow是Apache Arrow项目定义的列式内存格式，专门用于高效的数据交换和分析
 * 此包提供了Calcite框架与Arrow数据源之间的适配器实现
 */
package org.apache.calcite.adapter.arrow;  // 包声明：定义此包属于org.apache.calcite.adapter.arrow
// org.apache.calcite：Apache Calcite项目的根包，Calcite是一个动态数据管理框架
// adapter：适配器包，包含各种数据源的适配器实现
// arrow：Arrow适配器包，专门处理Apache Arrow格式数据的适配器
// 此包包含的类：ArrowTable（Arrow表实现）、ArrowSchema（Arrow模式）、ArrowRules（优化规则）、
// ArrowProject（投影操作）、ArrowFilter（过滤操作）、ArrowToEnumerableConverter（枚举转换器）等
// 这些类共同构成了Calcite查询Arrow文件的完整适配器实现