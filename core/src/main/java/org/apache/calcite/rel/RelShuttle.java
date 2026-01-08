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
// Apache许可证声明，说明代码的版权和使用许可
package org.apache.calcite.rel; // 定义包名，该接口位于org.apache.calcite.rel包中，属于Calcite关系表达式核心包

// 导入表函数扫描相关的类
import org.apache.calcite.rel.core.TableFunctionScan; // 导入表函数扫描类，用于处理表函数的扫描操作
import org.apache.calcite.rel.core.TableScan; // 导入表扫描类，用于处理表的扫描操作
// 导入逻辑关系表达式相关的类
import org.apache.calcite.rel.logical.LogicalAggregate; // 导入逻辑聚合类，用于处理GROUP BY等聚合操作
import org.apache.calcite.rel.logical.LogicalAsofJoin; // 导入逻辑as-of连接类，用于处理as-of连接操作
import org.apache.calcite.rel.logical.LogicalCalc; // 导入逻辑计算类，用于处理计算表达式和过滤条件
import org.apache.calcite.rel.logical.LogicalCorrelate; // 导入逻辑关联类，用于处理关联子查询
import org.apache.calcite.rel.logical.LogicalExchange; // 导入逻辑交换类，用于处理数据交换和重分布
import org.apache.calcite.rel.logical.LogicalFilter; // 导入逻辑过滤类，用于处理WHERE条件过滤
import org.apache.calcite.rel.logical.LogicalIntersect; // 导入逻辑交集类，用于处理INTERSECT集合操作
import org.apache.calcite.rel.logical.LogicalJoin; // 导入逻辑连接类，用于处理JOIN连接操作
import org.apache.calcite.rel.logical.LogicalMatch; // 导入逻辑匹配类，用于处理MATCH_RECOGNIZE模式匹配
import org.apache.calcite.rel.logical.LogicalMinus; // 导入逻辑差集类，用于处理EXCEPT集合操作
import org.apache.calcite.rel.logical.LogicalProject; // 导入逻辑投影类，用于处理SELECT字段列表
import org.apache.calcite.rel.logical.LogicalRepeatUnion; // 导入逻辑重复联合类，用于处理递归查询
import org.apache.calcite.rel.logical.LogicalSort; // 导入逻辑排序类，用于处理ORDER BY排序操作
import org.apache.calcite.rel.logical.LogicalTableModify; // 导入逻辑表修改类，用于处理INSERT/UPDATE/DELETE操作
import org.apache.calcite.rel.logical.LogicalUnion; // 导入逻辑联合类，用于处理UNION集合操作
import org.apache.calcite.rel.logical.LogicalValues; // 导入逻辑值类，用于处理VALUES子句

/**
 * Visitor that has methods for the common logical relational expressions.
 * 这是一个访问者接口，定义了访问常见逻辑关系表达式的方法
 * 
 * RelShuttle是Calcite中的关系表达式穿梭器（Relational Shuttle），它采用访问者模式（Visitor Pattern）
 * 来遍历和转换关系表达式树。每个visit方法对应一种特定的关系表达式类型，允许子类实现自定义的
 * 遍历和转换逻辑。
 * 
 * 主要作用：
 * 1. 提供统一的接口来访问各种关系表达式节点
 * 2. 支持对关系表达式树进行深度优先遍历
 * 3. 允许在遍历过程中对节点进行转换或修改
 * 4. 实现访问者模式，将遍历逻辑与节点类型解耦
 * 
 * 使用场景：
 * - 关系表达式的优化和重写
 * - 关系表达式的验证和分析
 * - 关系表达式的转换和适配
 * - 关系表达式的元数据收集
 * 
 * 实现类通常需要：
 * 1. 实现所有visit方法，定义对每种关系表达式的处理逻辑
 * 2. 在visit方法中递归调用子节点的visit方法，实现深度优先遍历
 * 3. 返回转换后的关系表达式节点，支持原地修改或创建新节点
 * 
 * 注意事项：
 * - 这是一个接口，具体的遍历逻辑由实现类提供
 * - visit方法可以返回原始节点或转换后的新节点
 * - 遍历顺序通常是深度优先，自底向上
 * - 实现类需要处理所有关系表达式类型，包括自定义类型
 */
public interface RelShuttle { // 定义RelShuttle接口，这是一个访问者模式的接口，用于遍历和访问关系表达式树
  RelNode visit(TableScan scan); // 访问表扫描节点，TableScan表示从数据库表中读取数据，visit方法返回处理后的RelNode

  RelNode visit(TableFunctionScan scan); // 访问表函数扫描节点，TableFunctionScan表示调用表函数生成数据，visit方法返回处理后的RelNode

  RelNode visit(LogicalValues values); // 访问逻辑值节点，LogicalValues表示VALUES子句生成的常量行，visit方法返回处理后的RelNode

  RelNode visit(LogicalFilter filter); // 访问逻辑过滤节点，LogicalFilter表示WHERE条件过滤操作，visit方法返回处理后的RelNode

  RelNode visit(LogicalCalc calc); // 访问逻辑计算节点，LogicalCalc表示计算表达式和过滤条件的组合，visit方法返回处理后的RelNode

  RelNode visit(LogicalProject project); // 访问逻辑投影节点，LogicalProject表示SELECT字段列表和计算表达式，visit方法返回处理后的RelNode

  RelNode visit(LogicalJoin join); // 访问逻辑连接节点，LogicalJoin表示JOIN连接操作，包括内连接、外连接等，visit方法返回处理后的RelNode

  RelNode visit(LogicalCorrelate correlate); // 访问逻辑关联节点，LogicalCorrelate表示关联子查询，用于处理相关子查询，visit方法返回处理后的RelNode

  RelNode visit(LogicalUnion union); // 访问逻辑联合节点，LogicalUnion表示UNION集合操作，合并多个查询结果，visit方法返回处理后的RelNode

  RelNode visit(LogicalIntersect intersect); // 访问逻辑交集节点，LogicalIntersect表示INTERSECT集合操作，返回多个查询结果的交集，visit方法返回处理后的RelNode

  RelNode visit(LogicalMinus minus); // 访问逻辑差集节点，LogicalMinus表示EXCEPT集合操作，返回第一个查询减去第二个查询的结果，visit方法返回处理后的RelNode

  RelNode visit(LogicalAggregate aggregate); // 访问逻辑聚合节点，LogicalAggregate表示GROUP BY聚合操作，包括SUM、COUNT等聚合函数，visit方法返回处理后的RelNode

  RelNode visit(LogicalMatch match); // 访问逻辑匹配节点，LogicalMatch表示MATCH_RECOGNIZE模式匹配操作，用于行模式识别，visit方法返回处理后的RelNode

  RelNode visit(LogicalSort sort); // 访问逻辑排序节点，LogicalSort表示ORDER BY排序操作，包括LIMIT和OFFSET，visit方法返回处理后的RelNode

  RelNode visit(LogicalExchange exchange); // 访问逻辑交换节点，LogicalExchange表示数据交换操作，用于数据重分布和并行处理，visit方法返回处理后的RelNode

  RelNode visit(LogicalTableModify modify); // 访问逻辑表修改节点，LogicalTableModify表示INSERT/UPDATE/DELETE操作，用于修改表数据，visit方法返回处理后的RelNode

  RelNode visit(LogicalAsofJoin logicalAsofJoin); // 访问逻辑as-of连接节点，LogicalAsofJoin表示as-of连接操作，用于连接最接近的时间点，visit方法返回处理后的RelNode

  RelNode visit(LogicalRepeatUnion logicalRepeatUnion); // 访问逻辑重复联合节点，LogicalRepeatUnion表示递归查询操作，用于处理WITH RECURSIVE，visit方法返回处理后的RelNode

  RelNode visit(RelNode other); // 访问其他关系表达式节点，这是兜底方法，处理所有其他类型的RelNode，visit方法返回处理后的RelNode
} // 接口定义结束
