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

/**
 * 提供克隆适配器的实用类集合。
 * 
 * <h2>包概述</h2>
 * 
 * 本包(org.apache.calcite.adapter.clone)是 Apache Calcite 框架中的克隆适配器模块，
 * 主要用于将外部数据源的数据"克隆"或"复制"到内存中，以便进行高效的查询处理。
 * 
 * <h3>核心功能</h3>
 * 
 * <ul>
 *   <li><b>数据克隆</b>：将外部数据源的数据加载到内存中，创建数据的副本</li>
 *   <li><b>缓存机制</b>：提供数据缓存功能，避免重复访问外部数据源</li>
 *   <li><b>查询优化</b>：通过内存中的数据副本提高查询性能</li>
 *   <li><b>数据转换</b>：将外部数据格式转换为 Calcite 可处理的内部格式</li>
 * </ul>
 * 
 * <h3>主要组件</h3>
 * 
 * <ul>
 *   <li><b>CloneSchema</b>：克隆模式，定义了克隆数据的模式结构</li>
 *   <li><b>ColumnLoader</b>：列加载器，负责将数据按列加载到内存中</li>
 *   <li><b>ArrayTable</b>：数组表，使用数组结构存储表数据</li>
 *   <li><b>ListTable</b>：列表表，使用列表结构存储表数据</li>
 * </ul>
 * 
 * <h3>使用场景</h3>
 * 
 * <ul>
 *   <li><b>小型数据集</b>：适合处理可以完全加载到内存的小型数据集</li>
 *   <li><b>频繁查询</b>：对于需要多次查询的数据，克隆到内存可以显著提高性能</li>
 *   <li><b>外部数据源</b>：访问速度较慢的外部数据源，如 CSV 文件、远程数据库等</li>
 *   <li><b>测试环境</b>：在测试和开发环境中快速创建数据副本</li>
 * </ul>
 * 
 * <h3>工作原理</h3>
 * 
 * <ol>
 *   <li>从外部数据源读取原始数据</li>
 *   <li>将数据转换为 Calcite 内部表示格式</li>
 *   <li>将转换后的数据存储在内存中（使用数组或列表结构）</li>
 *   <li>创建表的元数据（schema），包括列名、数据类型等</li>
 *   <li>通过 Calcite 的查询优化器对内存数据进行查询处理</li>
 * </ol>
 * 
 * <h3>数据存储结构</h3>
 * 
 * 本包提供了两种主要的数据存储结构：
 * 
 * <ul>
 *   <li><b>ArrayTable</b>：使用原始类型数组存储数据，提供最佳的内存效率和访问性能
 *     <ul>
 *       <li>适合存储大量同类型数据</li>
 *       <li>支持快速随机访问</li>
 *       <li>内存占用较小</li>
 *     </ul>
 *   </li>
 *   <li><b>ListTable</b>：使用 Java 列表（List）存储数据，提供更灵活的数据访问方式
 *     <ul>
 *       <li>适合存储结构化数据或复杂类型</li>
 *       <li>支持动态添加和删除数据</li>
 *       <li>使用更简单直观</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h3>性能考虑</h3>
 * 
 * <ul>
 *   <li><b>内存消耗</b>：需要将完整数据集加载到内存，适合小型数据集</li>
 *   <li><b>加载时间</b>：初始加载数据需要时间，但后续查询速度很快</li>
 *   <li><b>查询性能</b>：内存查询比外部数据源查询快几个数量级</li>
 *   <li><b>数据一致性</b>：克隆的是数据快照，不会反映外部数据源的实时变化</li>
 * </ul>
 * 
 * <h3>与其他适配器的关系</h3>
 * 
 * 本包是 Calcite 适配器体系的一部分，与其他适配器（如 CSV、JDBC、MongoDB 等）协同工作：
 * 
 * <ul>
 *   <li>可以从其他适配器读取数据并克隆到内存</li>
 *   <li>可以作为其他适配器的缓存层</li>
 *   <li>支持与其他适配器进行联合查询</li>
 * </ul>
 * 
 * <h3>示例用法</h3>
 * 
 * <pre>{@code
 * // 创建克隆模式
 * CloneSchema cloneSchema = CloneSchema.create(sourceSchema);
 * 
 * // 添加表到克隆模式
 * cloneSchema.addTable("myTable", sourceTable);
 * 
 * // 使用克隆模式进行查询
 * Connection connection = DriverManager.getConnection("jdbc:calcite:", info);
 * CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
 * calciteConnection.getRootSchema().add("clone", cloneSchema);
 * }</pre>
 * 
 * <h3>注意事项</h3>
 * 
 * <ul>
 *   <li>克隆的数据是静态快照，不会自动更新</li>
 *   <li>大数据集可能导致内存不足</li>
 *   <li>数据加载过程可能较慢，特别是对于大型数据集</li>
 *   <li>需要手动管理内存，避免内存泄漏</li>
 * </ul>
 * 
 * @see org.apache.calcite.adapter.clone.CloneSchema
 * @see org.apache.calcite.adapter.clone.ColumnLoader
 * @see org.apache.calcite.adapter.clone.ArrayTable
 * @see org.apache.calcite.adapter.clone.ListTable
 */
package org.apache.calcite.adapter.clone;
