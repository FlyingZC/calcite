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
package org.apache.calcite.adapter.druid; // 定义Druid适配器所在的包路径，该包包含Calcite与Druid数据源集成的相关类

/**
 * Connection to Druid. // Druid连接接口，定义了Calcite与Druid数据源之间的连接契约
 * 
 * Druid是一个高性能的实时分析型数据库，主要用于OLAP（联机分析处理）场景。
 * 该接口作为Calcite查询引擎与Druid数据源之间的抽象层，提供了标准化的连接规范。
 * 
 * Druid的主要特点：
 * 1. 高性能：针对大规模数据的快速查询进行了优化
 * 2. 实时性：支持实时数据摄入和查询
 * 3. 列式存储：采用列式存储格式，提高查询效率
 * 4. 分布式：支持分布式部署，可横向扩展
 * 5. 索引支持：内置多种索引结构（如倒排索引、位图索引等）
 * 
 * 在Calcite中，DruidConnection的作用：
 * 1. 提供统一的连接接口，屏蔽底层Druid的具体实现细节
 * 2. 支持将SQL查询转换为Druid的原生查询语言（Native Query）
 * 3. 管理与Druid集群的连接生命周期
 * 4. 提供元数据访问能力，如表结构、列信息等
 * 
 * 该接口通常由具体的实现类（如DruidConnectionImpl）来实现，
 * 提供实际的连接管理、查询执行和元数据操作功能。
 */
public interface DruidConnection { // 定义Druid连接接口，所有Druid连接实现都需要实现此接口
} // 接口定义结束，目前为空接口，可能用于类型标记或未来扩展
