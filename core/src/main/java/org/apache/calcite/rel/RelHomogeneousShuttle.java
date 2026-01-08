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
package org.apache.calcite.rel;

import org.apache.calcite.rel.core.TableFunctionScan; // 导入表函数扫描节点类，用于访问表函数
import org.apache.calcite.rel.core.TableScan; // 导入表扫描节点类，用于访问数据库表
import org.apache.calcite.rel.logical.LogicalAggregate; // 导入逻辑聚合节点类，用于GROUP BY操作
import org.apache.calcite.rel.logical.LogicalCalc; // 导入逻辑计算节点类，用于过滤和投影的组合操作
import org.apache.calcite.rel.logical.LogicalCorrelate; // 导入逻辑关联节点类，用于关联子查询
import org.apache.calcite.rel.logical.LogicalExchange; // 导入逻辑交换节点类，用于数据重分布
import org.apache.calcite.rel.logical.LogicalFilter; // 导入逻辑过滤节点类，用于WHERE条件
import org.apache.calcite.rel.logical.LogicalIntersect; // 导入逻辑交集节点类，用于INTERSECT操作
import org.apache.calcite.rel.logical.LogicalJoin; // 导入逻辑连接节点类，用于JOIN操作
import org.apache.calcite.rel.logical.LogicalMatch; // 导入逻辑匹配节点类，用于MATCH_RECOGNIZE操作
import org.apache.calcite.rel.logical.LogicalMinus; // 导入逻辑差集节点类，用于EXCEPT操作
import org.apache.calcite.rel.logical.LogicalProject; // 导入逻辑投影节点类，用于SELECT列表
import org.apache.calcite.rel.logical.LogicalSort; // 导入逻辑排序节点类，用于ORDER BY操作
import org.apache.calcite.rel.logical.LogicalTableModify; // 导入逻辑表修改节点类，用于INSERT/UPDATE/DELETE操作
import org.apache.calcite.rel.logical.LogicalUnion; // 导入逻辑并集节点类，用于UNION操作
import org.apache.calcite.rel.logical.LogicalValues; // 导入逻辑常量值节点类，用于VALUES子句

/**
 * RelHomogeneousShuttle（关系节点同构穿梭器）类：以同构的方式访问所有关系节点
 * 该类的作用是提供一个统一的访问模式，将所有特定类型的访问方法都重定向到通用的accept(RelNode)方法
 * 同构(homogeneous)意味着对所有关系节点类型都采用相同的处理方式，不区分具体类型
 * 这种设计模式简化了关系树的遍历逻辑，使得我们可以用统一的方式处理所有类型的RelNode
 * 该类继承自RelShuttleImpl，实现了访问者模式(Visitor Pattern)的一个变体
 * 主要应用场景：需要进行统一遍历但不关心具体节点类型的场景，如统计、验证、转换等操作
 * Visits all the relations in a homogeneous way: always redirects calls to
 * {@code accept(RelNode)}.
 */
public class RelHomogeneousShuttle extends RelShuttleImpl { // 定义RelHomogeneousShuttle类，继承自RelShuttleImpl基类，继承基类的所有默认实现
  @Override public RelNode visit(LogicalAggregate aggregate) { // 重写访问逻辑聚合节点的方法，@Override表示覆盖父类方法，接收LogicalAggregate类型参数aggregate
    return visit((RelNode) aggregate); // 将LogicalAggregate节点向上转型为RelNode，调用通用的visit(RelNode)方法，实现同构访问
  }

  @Override public RelNode visit(LogicalMatch match) { // 重写访问逻辑匹配节点的方法，用于处理MATCH_RECOGNIZE模式匹配操作
    return visit((RelNode) match); // 将LogicalMatch节点向上转型为RelNode，调用通用的visit(RelNode)方法，实现同构访问
  }

  @Override public RelNode visit(TableScan scan) { // 重写访问表扫描节点的方法，用于处理从数据库表中读取数据的操作
    return visit((RelNode) scan); // 将TableScan节点向上转型为RelNode，调用通用的visit(RelNode)方法，实现同构访问
  }

  @Override public RelNode visit(TableFunctionScan scan) { // 重写访问表函数扫描节点的方法，用于处理表函数调用返回的结果集
    return visit((RelNode) scan); // 将TableFunctionScan节点向上转型为RelNode，调用通用的visit(RelNode)方法，实现同构访问
  }

  @Override public RelNode visit(LogicalValues values) { // 重写访问逻辑常量值节点的方法，用于处理VALUES子句生成的常量数据
    return visit((RelNode) values); // 将LogicalValues节点向上转型为RelNode，调用通用的visit(RelNode)方法，实现同构访问
  }

  @Override public RelNode visit(LogicalFilter filter) { // 重写访问逻辑过滤节点的方法，用于处理WHERE条件过滤操作
    return visit((RelNode) filter); // 将LogicalFilter节点向上转型为RelNode，调用通用的visit(RelNode)方法，实现同构访问
  }

  @Override public RelNode visit(LogicalProject project) { // 重写访问逻辑投影节点的方法，用于处理SELECT列表的列选择和表达式计算
    return visit((RelNode) project); // 将LogicalProject节点向上转型为RelNode，调用通用的visit(RelNode)方法，实现同构访问
  }

  @Override public RelNode visit(LogicalJoin join) { // 重写访问逻辑连接节点的方法，用于处理INNER JOIN/LEFT JOIN等连接操作
    return visit((RelNode) join); // 将LogicalJoin节点向上转型为RelNode，调用通用的visit(RelNode)方法，实现同构访问
  }

  @Override public RelNode visit(LogicalCorrelate correlate) { // 重写访问逻辑关联节点的方法，用于处理关联子查询，支持子查询与外层查询的关联
    return visit((RelNode) correlate); // 将LogicalCorrelate节点向上转型为RelNode，调用通用的visit(RelNode)方法，实现同构访问
  }

  @Override public RelNode visit(LogicalUnion union) { // 重写访问逻辑并集节点的方法，用于处理UNION ALL操作，合并多个查询结果
    return visit((RelNode) union); // 将LogicalUnion节点向上转型为RelNode，调用通用的visit(RelNode)方法，实现同构访问
  }

  @Override public RelNode visit(LogicalIntersect intersect) { // 重写访问逻辑交集节点的方法，用于处理INTERSECT操作，返回多个查询结果的交集
    return visit((RelNode) intersect); // 将LogicalIntersect节点向上转型为RelNode，调用通用的visit(RelNode)方法，实现同构访问
  }

  @Override public RelNode visit(LogicalMinus minus) { // 重写访问逻辑差集节点的方法，用于处理EXCEPT操作，返回第一个查询结果减去第二个查询结果
    return visit((RelNode) minus); // 将LogicalMinus节点向上转型为RelNode，调用通用的visit(RelNode)方法，实现同构访问
  }

  @Override public RelNode visit(LogicalSort sort) { // 重写访问逻辑排序节点的方法，用于处理ORDER BY操作和LIMIT子句
    return visit((RelNode) sort); // 将LogicalSort节点向上转型为RelNode，调用通用的visit(RelNode)方法，实现同构访问
  }

  @Override public RelNode visit(LogicalExchange exchange) { // 重写访问逻辑交换节点的方法，用于处理数据重分布操作，如分布式环境下的数据交换
    return visit((RelNode) exchange); // 将LogicalExchange节点向上转型为RelNode，调用通用的visit(RelNode)方法，实现同构访问
  }

  @Override public RelNode visit(LogicalCalc calc) { // 重写访问逻辑计算节点的方法，用于处理过滤和投影的组合操作，是Calcite特有的优化节点
    return visit((RelNode) calc); // 将LogicalCalc节点向上转型为RelNode，调用通用的visit(RelNode)方法，实现同构访问
  }

  @Override public RelNode visit(LogicalTableModify modify) { // 重写访问逻辑表修改节点的方法，用于处理INSERT/UPDATE/DELETE等数据修改操作
    return visit((RelNode) modify); // 将LogicalTableModify节点向上转型为RelNode，调用通用的visit(RelNode)方法，实现同构访问
  }
}
