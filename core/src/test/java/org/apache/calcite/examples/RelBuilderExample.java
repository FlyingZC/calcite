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
package org.apache.calcite.examples; // 声明包名，该类位于 org.apache.calcite.examples 包下

import org.apache.calcite.plan.RelOptUtil; // 导入 RelOptUtil 工具类，用于关系代数节点的字符串表示转换
import org.apache.calcite.rel.RelNode; // 导入 RelNode 接口，表示关系代数树的节点
import org.apache.calcite.rel.core.JoinRelType; // 导入 JoinRelType 枚举，定义连接类型（内连接、左外连接等）
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入标准 SQL 操作符表，提供 SQL 标准操作符如 GREATER_THAN
import org.apache.calcite.test.RelBuilderTest; // 导入 RelBuilderTest 测试类，提供测试配置
import org.apache.calcite.tools.FrameworkConfig; // 导入 FrameworkConfig 接口，定义框架配置
import org.apache.calcite.tools.RelBuilder; // 导入 RelBuilder 类，用于构建关系代数表达式树的构建器

/**
 * Example that uses {@link org.apache.calcite.tools.RelBuilder}
 * to create various relational expressions.
 * 使用 RelBuilder 创建各种关系代数表达式的示例类
 * 
 * RelBuilder 是 Apache Calcite 提供的流式 API，用于以编程方式构建关系代数树
 * 关系代数树是 SQL 查询优化和执行的核心数据结构
 * 
 * 该类演示了如何使用 RelBuilder 构建以下常见的关系代数操作：
 * 1. VALUES 操作 - 创建常量值集合
 * 2. 表扫描 - 从表中读取数据
 * 3. 投影 - 选择特定列
 * 4. 聚合 - GROUP BY 和聚合函数
 * 5. 过滤 - WHERE/HAVING 条件
 * 6. 连接 - 多表连接操作
 * 
 * RelBuilder 采用栈式操作模式，每个操作都会在栈上构建新的关系节点
 * 这种设计使得构建复杂查询变得直观和可读
 */
public class RelBuilderExample { // 定义 RelBuilderExample 类，用于演示 RelBuilder 的各种用法
  private final boolean verbose; // 成员变量：是否输出详细信息的标志，true 表示打印关系代数树的字符串表示

  public RelBuilderExample(boolean verbose) { // 构造方法：创建 RelBuilderExample 实例
    this.verbose = verbose; // 初始化 verbose 成员变量，控制是否输出详细信息
  }

  public static void main(String[] args) { // 主方法：程序的入口点
    new RelBuilderExample(true).runAllExamples(); // 创建 RelBuilderExample 实例并运行所有示例，verbose=true 表示输出详细信息
  }

  public void runAllExamples() { // 方法：运行所有示例方法
    // Create a builder. The config contains a schema mapped
    // to the SCOTT database, with tables EMP and DEPT.
    final FrameworkConfig config = RelBuilderTest.config().build(); // 创建框架配置，包含 SCOTT 数据库的 schema 映射，包含 EMP 和 DEPT 表
    final RelBuilder builder = RelBuilder.create(config); // 使用配置创建 RelBuilder 实例，用于构建关系代数树
    for (int i = 0; i <= 4; i++) { // 循环执行 5 个示例（0 到 4）
      doExample(builder, i); // 调用 doExample 方法执行第 i 个示例
      final RelNode node = builder.build(); // 构建关系代数树并获取根节点
      if (verbose) { // 如果 verbose 为 true
        System.out.println(RelOptUtil.toString(node)); // 打印关系代数树的字符串表示，用于调试和可视化
      }
    }
  }

  private RelBuilder doExample(RelBuilder builder, int i) { // 方法：根据索引执行对应的示例方法
    switch (i) { // 根据 i 的值选择执行哪个示例
    case 0: // 如果 i 为 0
      return example0(builder); // 调用 example0 方法，演示 VALUES 操作
    case 1: // 如果 i 为 1
      return example1(builder); // 调用 example1 方法，演示表扫描
    case 2: // 如果 i 为 2
      return example2(builder); // 调用 example2 方法，演示表扫描和投影
    case 3: // 如果 i 为 3
      return example3(builder); // 调用 example3 方法，演示聚合和过滤
    case 4: // 如果 i 为 4
      return example4(builder); // 调用 example4 方法，演示复杂的连接操作
    default: // 如果 i 不是 0-4 中的任何值
      throw new AssertionError("unknown example" + i); // 抛出断言错误，表示未知的示例索引
    }
  }

  /**
   * Creates a relational expression for a values.
   * It is equivalent to
   *
   * <blockquote><pre>VALUES((1, TRUE), (NULL, FALSE))</pre></blockquote>
   * 创建 VALUES 关系表达式示例
   * 等价于 SQL: VALUES((1, TRUE), (NULL, FALSE))
   * 
   * VALUES 操作用于创建一个包含常量值的临时表
   * 在关系代数中，这对应于一个 ValuesRel 节点
   * 
   * @param builder RelBuilder 实例，用于构建关系代数树
   * @return 配置好 VALUES 操作的 RelBuilder 实例
   */
  private RelBuilder example0(RelBuilder builder) { // 方法：创建 VALUES 关系表达式
    return builder // 返回 RelBuilder 实例，支持链式调用
        .values(new String[] {"a", "b"}, 1, true, null, false); // 创建包含两列（a, b）的 VALUES 节点，包含两行数据：(1, TRUE) 和 (NULL, FALSE)
  }

  /**
   * Creates a relational expression for a table scan.
   * It is equivalent to
   *
   * <blockquote><pre>SELECT *
   * FROM emp</pre></blockquote>
   * 创建表扫描关系表达式示例
   * 等价于 SQL: SELECT * FROM emp
   * 
   * 表扫描是关系代数树的最底层操作，用于从数据源读取数据
   * 在关系代数中，这对应于一个 TableScanRel 节点
   * 
   * @param builder RelBuilder 实例，用于构建关系代数树
   * @return 配置好表扫描的 RelBuilder 实例
   */
  private RelBuilder example1(RelBuilder builder) { // 方法：创建表扫描关系表达式
    return builder // 返回 RelBuilder 实例，支持链式调用
        .scan("EMP"); // 扫描 EMP 表，将表的所有列加载到关系代数树中
  }

  /**
   * Creates a relational expression for a table scan and project.
   * It is equivalent to
   *
   * <blockquote><pre>SELECT deptno, ename
   * FROM emp</pre></blockquote>
   * 创建表扫描和投影关系表达式示例
   * 等价于 SQL: SELECT deptno, ename FROM emp
   * 
   * 投影操作用于选择特定的列，是关系代数的基本操作之一
   * 在关系代数中，这对应于一个 ProjectRel 节点
   * 
   * @param builder RelBuilder 实例，用于构建关系代数树
   * @return 配置好表扫描和投影的 RelBuilder 实例
   */
  private RelBuilder example2(RelBuilder builder) { // 方法：创建表扫描和投影关系表达式
    return builder // 返回 RelBuilder 实例，支持链式调用
        .scan("EMP") // 扫描 EMP 表
        .project(builder.field("DEPTNO"), builder.field("ENAME")); // 投影操作，只选择 DEPTNO 和 ENAME 两列
  }

  /**
   * Creates a relational expression for a table scan, aggregate, filter.
   * It is equivalent to
   *
   * <blockquote><pre>SELECT deptno, count(*) AS c, sum(sal) AS s
   * FROM emp
   * GROUP BY deptno
   * HAVING count(*) &gt; 10</pre></blockquote>
   * 创建表扫描、聚合和过滤关系表达式示例
   * 等价于 SQL: SELECT deptno, count(*) AS c, sum(sal) AS s FROM emp GROUP BY deptno HAVING count(*) > 10
   * 
   * 该示例展示了完整的聚合查询流程：
   * 1. 扫描表
   * 2. 按 DEPTNO 分组
   * 3. 计算每个组的 COUNT(*) 和 SUM(sal)
   * 4. 使用 HAVING 子句过滤聚合结果
   * 
   * 在关系代数中，这对应于 TableScanRel -> AggregateRel -> FilterRel 的节点链
   * 
   * @param builder RelBuilder 实例，用于构建关系代数树
   * @return 配置好聚合和过滤的 RelBuilder 实例
   */
  private RelBuilder example3(RelBuilder builder) { // 方法：创建表扫描、聚合和过滤关系表达式
    return builder // 返回 RelBuilder 实例，支持链式调用
        .scan("EMP") // 扫描 EMP 表
        .aggregate(builder.groupKey("DEPTNO"), // 按 DEPTNO 列分组
            builder.count(false, "C"), // 计算 COUNT(*) 并别名为 C，false 表示不使用 distinct
            builder.sum(false, "S", builder.field("SAL"))) // 计算 SUM(sal) 并别名为 S，false 表示不使用 distinct
        .filter( // 过滤聚合结果，相当于 HAVING 子句
            builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field("C"), // 创建大于比较表达式：C > 10
                builder.literal(10))); // 字面量 10
  }

  /**
   * Sometimes the stack becomes so deeply nested it gets confusing. To keep
   * things straight, you can remove expressions from the stack. For example,
   * here we are building a bushy join:
   *
   * <blockquote><pre>
   *                join
   *              /      \
   *         join          join
   *       /      \      /      \
   *     EMP     DEPT  EMP    BONUS
   * </pre></blockquote>
   *
   * <p>We build it in three stages. Store the intermediate results in variables
   * `left` and `right`, and use `push()` to put them back on the stack when it
   * is time to create the final `Join`.
   * 创建复杂的灌木状连接关系表达式示例
   * 
   * 该示例展示了如何构建复杂的连接树，特别是当栈变得太深时如何管理
   * 灌木状连接是指连接树是平衡的，而不是线性的
   * 
   * 构建过程分为三个阶段：
   * 1. 构建左侧连接：EMP JOIN DEPT ON DEPTNO
   * 2. 构建右侧连接：EMP JOIN BONUS ON ENAME
   * 3. 将左右两个连接结果再连接：(EMP JOIN DEPT) JOIN (EMP JOIN BONUS) ON ENAME
   * 
   * 关键技术点：
   * - 使用 build() 方法从栈中移除表达式并保存到变量
   * - 使用 push() 方法将表达式重新压入栈
   * - 这种方式可以避免栈过深，使代码更清晰
   * 
   * @param builder RelBuilder 实例，用于构建关系代数树
   * @return 配置好复杂连接的 RelBuilder 实例
   */
  private RelBuilder example4(RelBuilder builder) { // 方法：创建复杂的灌木状连接关系表达式
    final RelNode left = builder // 声明并初始化左侧连接节点
        .scan("EMP") // 扫描 EMP 表
        .scan("DEPT") // 扫描 DEPT 表
        .join(JoinRelType.INNER, "DEPTNO") // 执行内连接，连接条件为 DEPTNO
        .build(); // 从栈中移除并构建左侧连接节点，保存到 left 变量

    final RelNode right = builder // 声明并初始化右侧连接节点
        .scan("EMP") // 扫描 EMP 表
        .scan("BONUS") // 扫描 BONUS 表
        .join(JoinRelType.INNER, "ENAME") // 执行内连接，连接条件为 ENAME
        .build(); // 从栈中移除并构建右侧连接节点，保存到 right 变量

    return builder // 返回 RelBuilder 实例
        .push(left) // 将左侧连接节点压入栈
        .push(right) // 将右侧连接节点压入栈
        .join(JoinRelType.INNER, "ENAME"); // 执行内连接，连接左右两个子树，连接条件为 ENAME
  }
}
