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
package org.apache.calcite.piglet; // 指定当前类所在的包路径，org.apache.calcite.piglet是Piglet适配器的包

// 导入Calcite框架中可枚举解释器类，用于将关系代数节点解释为可枚举的LINQ表达式
import org.apache.calcite.adapter.enumerable.EnumerableInterpreter;
// 导入LINQ4J表达式工具类，用于创建和操作表达式树
import org.apache.calcite.linq4j.tree.Expressions;
// 导入关系字段排序类，表示关系代数中字段的排序方向（升序或降序）
import org.apache.calcite.rel.RelFieldCollation;
// 导入关系节点基类，是所有关系代数操作节点的父类
import org.apache.calcite.rel.RelNode;
// 导入聚合操作节点类，表示SQL中的GROUP BY聚合操作
import org.apache.calcite.rel.core.Aggregate;
// 导入投影操作节点类，表示SQL中的SELECT投影操作
import org.apache.calcite.rel.core.Project;
// 导入窗口操作节点类，表示SQL中的窗口函数操作（OVER子句）
import org.apache.calcite.rel.core.Window;
// 导入关系代数到SQL转换器基类，本类继承自该类并扩展其功能
import org.apache.calcite.rel.rel2sql.RelToSqlConverter;
// 导入Rex节点类，表示Calcite中的行表达式（Row Expression），用于表示SQL表达式
import org.apache.calcite.rex.RexNode;
// 导入SQL方言类，用于处理不同数据库系统的SQL语法差异
import org.apache.calcite.sql.SqlDialect;
// 导入SQL字面量类，表示SQL中的常量值
import org.apache.calcite.sql.SqlLiteral;
// 导入SQL节点基类，是所有SQL语法节点的父类
import org.apache.calcite.sql.SqlNode;
// 导入SQL节点列表类，用于存储一组SQL节点
import org.apache.calcite.sql.SqlNodeList;
// 导入SQL窗口类，表示SQL中的窗口定义（OVER子句）
import org.apache.calcite.sql.SqlWindow;
// 导入SQL标准操作符表，包含了所有标准SQL操作符
import org.apache.calcite.sql.fun.SqlStdOperatorTable;

// 导入Google Guava库中的不可变集合类，用于创建不可修改的集合
import com.google.common.collect.ImmutableSet;

// 导入Java标准库中的动态数组类，用于存储可变长度的元素列表
import java.util.ArrayList;
// 导入Java标准库中的列表接口，是所有列表类型的父接口
import java.util.List;

/**
 * PigRelToSqlConverter类：Pig脚本到SQL转换器
 * 
 * 类作用：这是一个RelToSqlConverter的扩展类，专门用于将从Pig脚本转换而来的关系代数树转换为SQL语句
 * 
 * 核心功能：
 * 1. 继承自RelToSqlConverter，复用其关系代数到SQL的基础转换能力
 * 2. 针对Pig脚本的特殊需求，扩展了聚合操作（Aggregate）的转换逻辑
 * 3. 支持Pig中的CUBE和ROLLUP操作，将其转换为SQL中的相应语法
 * 4. 扩展了窗口函数（Window）的转换逻辑，处理Pig中的窗口操作
 * 
 * 工作原理：
 * - 输入：一个已经经过优化器优化的关系代数树（RelNode），该树是从Pig脚本转换而来
 * - 处理：遍历关系代数树，将每个节点转换为对应的SQL语法
 * - 输出：一个完整的SQL语句，可以在目标数据库上执行
 * 
 * 特殊处理：
 * - 对于包含多个分组集合（Group Sets）的聚合操作，会自动识别并转换为ROLLUP或CUBE语法
 * - 支持处理嵌套的Project和EnumerableInterpreter节点
 * - 正确处理窗口函数的分区、排序和边界定义
 */
public class PigRelToSqlConverter extends RelToSqlConverter { // 定义PigRelToSqlConverter类，继承自RelToSqlConverter基类

  /**
   * 构造方法：PigRelToSqlConverter
   * 
   * 方法作用：创建一个Pig关系代数到SQL的转换器实例
   * 
   * 参数说明：
   * @param dialect SQL方言对象，指定目标数据库系统的SQL语法规则（如MySQL、PostgreSQL、Oracle等）
   * 
   * 实现逻辑：
   * 1. 接收一个SQL方言参数，用于确定生成的SQL语句的语法格式
   * 2. 调用父类RelToSqlConverter的构造方法，初始化基础转换功能
   * 3. 父类会保存方言对象，供后续转换过程中使用
   * 
   * 使用场景：
   * - 在将Pig脚本转换为SQL之前，需要创建此转换器实例
   * - 需要指定目标数据库类型，以便生成符合该数据库语法的SQL语句
   */
  PigRelToSqlConverter(SqlDialect dialect) { // 构造方法，接收SQL方言参数
    super(dialect); // 调用父类RelToSqlConverter的构造方法，传入方言对象进行初始化
  }

  /**
   * 方法：visit
   * 
   * 方法作用：访问并转换聚合操作节点（Aggregate）为SQL语句
   * 
   * 方法签名：@Override public Result visit(Aggregate e)
   * 
   * 参数说明：
   * @param e 聚合操作节点，包含分组信息、聚合函数等信息
   * 
   * 返回值：Result 转换结果，包含生成的SQL语句和相关上下文信息
   * 
   * 实现逻辑：
   * 1. 检查聚合节点的输入是否是Project节点或包含Project的EnumerableInterpreter节点
   * 2. 递归访问输入节点，获取转换结果
   * 3. 构建GROUP BY子句和SELECT子句的SQL节点列表
   * 4. 处理多个分组集合的情况，转换为ROLLUP或CUBE语法
   * 5. 构建完整的聚合SQL语句并返回
   * 
   * 特殊处理：
   * - 检测并处理Pig中的CUBE和ROLLUP操作
   * - 当分组集合数量等于分组字段数+1时，转换为ROLLUP
   * - 当分组集合数量等于2的分组字段数次方时，转换为CUBE
   */
  @Override public Result visit(Aggregate e) { // 重写visit方法，处理聚合节点
    // 判断聚合节点的输入是否是Project节点，或者是包含Project的EnumerableInterpreter节点
    // isProjectOutput标志用于确定是否需要特殊处理投影输出
    final boolean isProjectOutput = e.getInput() instanceof Project // 检查输入是否是Project节点
        || (e.getInput() instanceof EnumerableInterpreter // 或者输入是EnumerableInterpreter节点
            && ((EnumerableInterpreter) e.getInput()).getInput() // 并且该解释器的输入
                instanceof Project); // 是一个Project节点
    // 访问聚合节点的输入（索引为0），获取转换结果
    // isAnon()判断是否是匿名结果，isProjectOutput表示是否是投影输出
    // ImmutableSet.of(Clause.GROUP_BY)表示当前上下文包含GROUP BY子句
    final Result x = // 声明结果变量x
        visitInput(e, 0, isAnon(), isProjectOutput, // 访问输入节点，传入节点、索引、匿名标志和投影输出标志
            ImmutableSet.of(Clause.GROUP_BY)); // 传入GROUP BY子句集合
    // 基于结果x创建一个Builder对象，用于构建SQL语句
    final Builder builder = x.builder(e); // 创建Builder对象，传入当前聚合节点

    // 创建GROUP BY子句的SQL节点列表，用于存储分组字段
    final List<SqlNode> groupByList = Expressions.list(); // 使用Expressions工具类创建空列表
    // 创建SELECT子句的SQL节点列表，用于存储选择的字段和聚合函数
    final List<SqlNode> selectList = new ArrayList<>(); // 创建ArrayList实例
    // 调用父类方法，构建聚合分组列表和选择列表
    // 这个方法会填充groupByList和selectList两个列表
    buildAggGroupList(e, builder, groupByList, selectList); // 构建分组和选择列表

    // 获取分组集合的数量，用于判断是否需要转换为ROLLUP或CUBE
    final int groupSetSize = e.getGroupSets().size(); // 获取分组集合的大小
    // 创建GROUP BY的SQL节点列表，传入分组字段列表和位置信息
    SqlNodeList groupBy = new SqlNodeList(groupByList, POS); // 创建SqlNodeList对象
    // 如果有多个分组集合，说明需要转换为ROLLUP或CUBE语法
    if (groupSetSize > 1) { // 判断分组集合数量是否大于1
      // 多个分组集合通常是由Pig的CUBE或ROLLUP操作转换而来
      // If there are multiple group sets, this should be a result of converting a
      // Pig CUBE/cube or Pig CUBE/rollup
      final List<SqlNode> cubeRollupList = Expressions.list(); // 创建用于存储ROLLUP或CUBE的列表
      // 如果分组集合数量等于分组字段数+1，说明是ROLLUP操作
      if (groupSetSize == groupByList.size() + 1) { // 判断是否满足ROLLUP条件
        // 创建ROLLUP函数调用，传入分组字段列表
        cubeRollupList.add(SqlStdOperatorTable.ROLLUP.createCall(groupBy)); // 添加ROLLUP调用
      } else { // 否则应该是CUBE操作
        // 断言分组集合数量等于2的分组字段数次方，这是CUBE的特征
        assert groupSetSize == Math.round(Math.pow(2, groupByList.size())); // 验证CUBE条件
        // 创建CUBE函数调用，传入分组字段列表
        cubeRollupList.add(SqlStdOperatorTable.CUBE.createCall(groupBy)); // 添加CUBE调用
      }
      // 将ROLLUP或CUBE调用作为新的GROUP BY子句
      groupBy = new SqlNodeList(cubeRollupList, POS); // 更新groupBy为包含ROLLUP/CUBE的列表
    }

    // 构建完整的聚合SQL语句并返回结果
    // buildAggregate方法会组装SELECT、FROM、GROUP BY等子句
    return buildAggregate(e, builder, selectList, groupBy).result(); // 构建聚合语句并返回结果
  }

  // CHECKSTYLE: IGNORE 1 // 忽略代码风格检查的第1条规则
  /**
   * 方法：visit
   * 
   * 方法作用：访问并转换窗口函数节点（Window）为SQL语句
   * 
   * 方法签名：@Override public Result visit(Window e)
   * 
   * 参数说明：
   * @param e 窗口操作节点，包含窗口分组、排序、边界和聚合函数等信息
   * 
   * 返回值：Result 转换结果，包含生成的SQL语句和相关上下文信息
   * 
   * 实现逻辑：
   * 1. 访问窗口节点的输入，获取基础转换结果
   * 2. 遍历窗口节点中的每个窗口分组（Window.Group）
   * 3. 为每个窗口分组构建PARTITION BY子句（分区字段列表）
   * 4. 为每个窗口分组构建ORDER BY子句（排序字段列表）
   * 5. 处理窗口边界（lowerBound和upperBound），转换为SQL语法
   * 6. 为每个窗口聚合函数创建OVER子句
   * 7. 将所有窗口函数添加到SELECT子句中
   * 
   * 特殊处理：
   * - 当没有ORDER BY且是RANGE窗口时，简化为OVER ()
   * - 正确处理窗口边界的各种情况（UNBOUNDED PRECEDING、CURRENT ROW等）
   * - 支持多个窗口分组的处理
   * 
   * @see #dispatch 引用dispatch方法，说明此方法通过dispatch方法被调用
   */
  @Override public Result visit(Window e) { // 重写visit方法，处理窗口节点
    // 访问窗口节点的输入（索引为0），指定上下文为SELECT子句
    final Result x = visitInput(e, 0, Clause.SELECT); // 访问输入节点，传入节点、索引和SELECT子句
    // 基于结果x创建一个Builder对象，用于构建SQL语句
    final Builder builder = x.builder(e); // 创建Builder对象，传入当前窗口节点
    // 创建SELECT子句的SQL节点列表，初始化为当前上下文的字段列表
    // 这样可以保留原有的字段，然后添加窗口函数
    final List<SqlNode> selectList = // 声明选择列表
        new ArrayList<>(builder.context.fieldList()); // 从上下文获取字段列表并创建ArrayList

    // 遍历窗口节点中的每个窗口分组
    for (Window.Group winGroup : e.groups) { // 循环处理每个窗口分组
      // 创建PARTITION BY子句的SQL节点列表，用于存储分区字段
      final List<SqlNode> partitionList = Expressions.list(); // 创建空列表
      // 遍历窗口分组的键（分区字段索引）
      for (int i : winGroup.keys) { // 循环处理每个分区键
        // 将分区字段添加到分区列表中
        partitionList.add(builder.context.field(i)); // 根据索引获取字段并添加到列表
      }

      // 创建ORDER BY子句的SQL节点列表，用于存储排序字段
      final List<SqlNode> orderList = Expressions.list(); // 创建空列表
      // 遍历窗口分组的排序规则
      for (RelFieldCollation orderKey : winGroup.collation().getFieldCollations()) { // 循环处理每个排序键
        // 将排序字段转换为SQL节点并添加到排序列表中
        orderList.add(builder.context.toSql(orderKey)); // 转换排序键为SQL并添加到列表
      }

      // 将窗口下边界转换为SQL节点
      SqlNode lowerBound = builder.context.toSql(winGroup.lowerBound); // 获取下边界的SQL表示
      // 将窗口上边界转换为SQL节点
      SqlNode upperBound = builder.context.toSql(winGroup.upperBound); // 获取上边界的SQL表示
      // 特殊情况处理：如果没有ORDER BY且不是ROWS窗口，则简化窗口定义
      if (orderList.isEmpty() && !winGroup.isRows) { // 判断是否有排序且是否是ROWS窗口
        // With no ORDER BY, all RANGE windows are equivalent to OVER (),
        // so simplify.
        // 没有ORDER BY的RANGE窗口等价于OVER ()，因此简化处理
        lowerBound = upperBound = null; // 将上下边界都设置为null
      }
      // 创建SQL窗口定义对象
      final SqlNode sqlWindow = // 声明SQL窗口变量
          SqlWindow.create(null, // 窗口声明名称，null表示没有命名窗口
              null, // 窗口引用名称，null表示不引用已定义的窗口
              new SqlNodeList(partitionList, POS), // PARTITION BY子句
              new SqlNodeList(orderList, POS), // ORDER BY子句
              SqlLiteral.createBoolean(winGroup.isRows, POS), // 窗口类型：ROWS或RANGE
              lowerBound, // 窗口下边界
              upperBound, // 窗口上边界
              null, // allowPartial标志，null表示使用默认值
              builder.context.toSql(winGroup.exclude), // EXCLUDE子句
              POS); // 解析位置信息

      // 遍历窗口分组中的每个窗口聚合函数
      for (Window.RexWinAggCall winFunc : winGroup.aggCalls) { // 循环处理每个窗口聚合函数
        // 创建窗口函数操作数的SQL节点列表
        final List<SqlNode> winFuncOperands = Expressions.list(); // 创建空列表
        // 遍历窗口函数的每个操作数
        for (RexNode operand : winFunc.getOperands()) { // 循环处理每个操作数
          // 将操作数转换为SQL节点并添加到操作数列表中
          winFuncOperands.add(builder.context.toSql(null, operand)); // 转换操作数为SQL并添加
        }
        // 创建窗口函数调用节点
        SqlNode aggFunc = winFunc.getOperator().createCall(new SqlNodeList(winFuncOperands, POS)); // 创建函数调用
        // 创建OVER子句，将窗口函数与窗口定义关联，并添加到选择列表中
        selectList.add(SqlStdOperatorTable.OVER.createCall(POS, aggFunc, sqlWindow)); // 添加OVER调用到SELECT
      }
      // 设置SELECT子句，包含所有字段和窗口函数
      builder.setSelect(new SqlNodeList(selectList, POS)); // 更新Builder的SELECT子句
    }
    // 返回构建完成的SQL语句结果
    return builder.result(); // 返回最终结果
  }
} // 类定义结束
