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
package org.apache.calcite.rel.metadata; // 元数据包，包含关系表达式元数据相关的所有类和接口

import org.apache.calcite.rel.RelNode; // 导入关系表达式接口，这是Calcite中关系代数的基本构建块

/**
 * Metadata about a relational expression. // 关于关系表达式的元数据接口，这是所有元数据类型的基接口
 *
 * <p>For particular types of metadata, a sub-class defines one of more methods
 * to query that metadata. Then a {@link RelMetadataProvider} can offer those
 * kinds of metadata for particular sub-classes of {@link RelNode}. // 对于特定类型的元数据，子类会定义一个或多个方法来查询该元数据。然后RelMetadataProvider可以为特定的RelNode子类提供这些类型的元数据
 *
 * <p>User code (typically in a planner rule or an implementation of
 * {@link RelNode#computeSelfCost(org.apache.calcite.plan.RelOptPlanner, RelMetadataQuery)})
 * acquires a {@code Metadata} instance by calling {@link RelNode#metadata}. // 用户代码（通常在优化器规则或RelNode的computeSelfCost方法实现中）通过调用RelNode#metadata方法获取Metadata实例
 *
 * <p>A {@code Metadata} instance already knows which particular {@code RelNode}
 * it is describing, so the methods do not pass in the {@code RelNode}. In fact,
 * quite a few metadata methods have no extra parameters. For instance, you can
 * get the row-count as follows: // Metadata实例已经知道它描述的是哪个特定的RelNode，因此方法不需要传入RelNode参数。实际上，很多元数据方法没有额外的参数。例如，你可以这样获取行数：
 *
 * <blockquote><pre><code>
 * RelNode rel; // 定义一个关系表达式变量
 * double rowCount = rel.metadata(RowCount.class).rowCount(); // 通过调用metadata方法获取RowCount元数据实例，然后调用rowCount()方法获取行数
 * </code></pre></blockquote>
 */
public interface Metadata { // 定义Metadata接口，这是所有元数据类型的基接口，提供了获取关联关系表达式的方法
  /** Returns the relational expression that this metadata is about. */ // 返回该元数据所描述的关系表达式
  RelNode rel(); // 抽象方法，返回当前元数据对象所关联的RelNode关系表达式实例
}
