/*
 * Licensed to the Apache Software Foundation (ASF) under one or more  // Apache软件基金会许可证声明：本文件在Apache许可证2.0版本下授权
 * contributor license agreements.  See the NOTICE file distributed with  // 参与者许可协议：查看随本工作分发的NOTICE文件以获取版权所有权信息
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF根据Apache许可证2.0版本将本文件授权给您
 * (the "License"); you may not use this file except in compliance with  // ("许可证")：除非遵守许可证，否则您不得使用此文件
 * the License.  You may obtain a copy of the License at  // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // 许可证在线地址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意
 * distributed under the License is distributed on an "AS IS" BASIS,  // 根据许可证分发的软件是按"原样"基础分发的
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 不带任何明示或暗示的保证或条件
 * See the License for the specific language governing permissions and  // 请参阅许可证以获取管理权限和
 * limitations under the License.  // 许可下的限制的特定语言
 */

/**
 * Facilities to externalize {@link org.apache.calcite.rel.RelNode}s to and from  // 提供将Calcite关系表达式节点(RelNode)从/到XML和JSON格式进行外部化的功能
 * XML and JSON format.  // XML和JSON格式的序列化和反序列化能力
 *
 * <p>本包(package)的作用和功能说明：</p>  // 包的详细功能说明开始
 * <ul>  // 无序列表开始
 *   <li>提供RelNode关系树的序列化功能，将内存中的关系表达式树转换为XML或JSON格式的文本表示</li>  // 功能1：关系树序列化
 *   <li>提供RelNode关系树的反序列化功能，将XML或JSON格式的文本转换为内存中的关系表达式树</li>  // 功能2：关系树反序列化
 *   <li>支持关系树的持久化存储，可以将查询计划保存到文件或数据库中</li>  // 功能3：关系树持久化
 *   <li>支持关系树的跨进程传输，可以在不同系统或服务之间传递查询计划</li>  // 功能4：跨进程传输
 *   <li>支持关系树的文本化展示，方便开发者调试和理解查询计划结构</li>  // 功能5：文本化展示
 *   <li>提供可扩展的外部化机制，支持自定义的RelNode类型和属性</li>  // 功能6：可扩展机制
 * </ul>  // 无序列表结束
 *
 * <p>核心类说明：</p>  // 核心类说明开始
 * <ul>  // 无序列表开始
 *   <li><b>RelWriter</b>：关系树写入器接口，定义了将RelNode关系树输出为文本格式的基本方法</li>  // RelWriter接口说明
 *   <li><b>RelWriterImpl</b>：RelWriter的默认实现类，提供了关系树写入的具体逻辑</li>  // RelWriterImpl实现类说明
 *   <li><b>RelXmlWriter</b>：专门用于将RelNode关系树序列化为XML格式的写入器</li>  // RelXmlWriter类说明
 *   <li><b>RelJsonWriter</b>：专门用于将RelNode关系树序列化为JSON格式的写入器</li>  // RelJsonWriter类说明
 *   <li><b>RelXmlReader</b>：专门用于从XML格式反序列化RelNode关系树的读取器</li>  // RelXmlReader类说明
 *   <li><b>RelJsonReader</b>：专门用于从JSON格式反序列化RelNode关系树的读取器</li>  // RelJsonReader类说明
 * </ul>  // 无序列表结束
 *
 * <p>使用场景：</p>  // 使用场景说明开始
 * <ul>  // 无序列表开始
 *   <li>查询计划调试：将优化后的关系树输出为文本，便于开发者理解和分析</li>  // 场景1：查询计划调试
 *   <li>查询计划缓存：将查询计划序列化后缓存到外部存储，避免重复优化</li>  // 场景2：查询计划缓存
 *   <li>查询计划传输：在分布式系统中将查询计划从调度节点传输到执行节点</li>  // 场景3：查询计划传输
 *   <li>查询计划持久化：将重要的查询计划保存到数据库，用于审计或复用</li>  // 场景4：查询计划持久化
 *   <li>查询计划可视化：将关系树转换为结构化格式，用于前端可视化展示</li>  // 场景5：查询计划可视化
 *   <li>查询计划测试：将预期的查询计划保存为文件，用于自动化测试验证</li>  // 场景6：查询计划测试
 * </ul>  // 无序列表结束
 *
 * <p>序列化格式说明：</p>  // 序列化格式说明开始
 * <ul>  // 无序列表开始
 *   <li><b>XML格式</b>：使用XML标签表示关系树结构，具有良好的可读性和可扩展性，适合人类阅读和编辑</li>  // XML格式特点
 *   <li><b>JSON格式</b>：使用JSON对象表示关系树结构，更紧凑且易于程序解析，适合机器处理和网络传输</li>  // JSON格式特点
 *   <li>两种格式都完整保留了RelNode的类型、属性、输入关系等所有信息</li>  // 格式完整性
 *   <li>两种格式都支持嵌套结构，可以表示任意深度的关系树</li>  // 格式嵌套性
 *   <li>两种格式都支持自定义扩展，可以添加额外的元数据信息</li>  // 格式扩展性
 * </ul>  // 无序列表结束
 *
 * <p>技术实现要点：</p>  // 技术实现说明开始
 * <ul>  // 无序列表开始
 *   <li>使用访问者模式(Visitor Pattern)遍历关系树，每个RelNode节点负责输出自己的信息</li>  // 技术点1：访问者模式
 *   <li>利用反射机制动态获取RelNode的类型和属性信息，实现通用序列化逻辑</li>  // 技术点2：反射机制
 *   <li>通过递归调用处理嵌套的子节点，保持关系树的层次结构</li>  // 技术点3：递归处理
 *   <li>使用类型注册表(TypeRegistry)管理所有RelNode类型，支持动态扩展</li>  // 技术点4：类型注册
 *   <li>提供缩进和格式化选项，控制输出文本的可读性</li>  // 技术点5：格式化选项
 *   <li>支持自定义的RelWriter实现，满足不同的序列化需求</li>  // 技术点6：自定义实现
 * </ul>  // 无序列表结束
 *
 * <p>注意事项：</p>  // 注意事项说明开始
 * <ul>  // 无序列表开始
 *   <li>序列化和反序列化过程中需要保持类型兼容性，不同版本的Calcite可能不兼容</li>  // 注意1：版本兼容性
 *   <li>某些特殊的RelNode实现可能包含无法序列化的状态（如文件句柄、数据库连接等）</li>  // 注意2：不可序列化状态
 *   <li>序列化后的文件可能包含敏感信息，需要妥善保管</li>  // 注意3：信息安全
 *   <li>大型关系树的序列化可能消耗较多内存，需要注意性能优化</li>  // 注意4：性能考虑
 *   <li>反序列化时需要确保相关的RelNode类和规则在类路径中可用</li>  // 注意5：类路径依赖
 * </ul>  // 无序列表结束
 *
 * @see org.apache.calcite.rel.RelNode  // 参见RelNode接口：关系表达式节点的核心接口
 * @see org.apache.calcite.rel.RelWriter  // 参见RelWriter接口：关系树写入器接口
 * @see org.apache.calcite.rel.externalize.RelXmlWriter  // 参见RelXmlWriter：XML格式的关系树写入器
 * @see org.apache.calcite.rel.externalize.RelJsonWriter  // 参见RelJsonWriter：JSON格式的关系树写入器
 * @see org.apache.calcite.rel.externalize.RelXmlReader  // 参见RelXmlReader：XML格式的关系树读取器
 * @see org.apache.calcite.rel.externalize.RelJsonReader  // 参见RelJsonReader：JSON格式的关系树读取器
 */  // JavaDoc注释结束
package org.apache.calcite.rel.externalize;  // 声明此包属于org.apache.calcite.rel.externalize包，包含关系表达式外部化相关的类和接口
