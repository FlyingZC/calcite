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
// 声明当前类所在的包，org.apache.calcite.adapter.enumerable 是Calcite中可枚举适配器包，包含所有可枚举关系节点的实现
package org.apache.calcite.adapter.enumerable;

// 导入RelOptTable类，表示优化器中的表对象，包含表的元数据信息
import org.apache.calcite.plan.RelOptTable;
// 导入RelCollation类，表示排序规则，定义了字段的排序顺序和方向
import org.apache.calcite.rel.RelCollation;
// 导入RelNode接口，是Calcite中所有关系节点(Relational Node)的基类，代表关系代数操作
import org.apache.calcite.rel.RelNode;
// 导入CorrelationId类，用于标识关联变量，在处理子查询和关联查询时使用
import org.apache.calcite.rel.core.CorrelationId;
// 导入RelHint类，表示关系节点的提示(Hint)，用于影响优化器的决策
import org.apache.calcite.rel.hint.RelHint;
// 导入RelDataType类，表示关系数据类型，描述表或结果集的行类型结构
import org.apache.calcite.rel.type.RelDataType;
// 导入RexNode类，是Calcite中行表达式(Row Expression)的基类，代表可以在行上计算的表达式
import org.apache.calcite.rex.RexNode;
// 导入RexUtil工具类，提供行表达式相关的实用方法，如创建结构类型等
import org.apache.calcite.rex.RexUtil;
// 导入SqlValidatorUtil工具类，提供SQL验证相关的实用方法，如字段名建议等
import org.apache.calcite.sql.validate.SqlValidatorUtil;

// 导入Nullable注解，用于标记参数或返回值可能为null，来自CheckerFramework框架
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入List接口，Java集合框架的列表接口，用于存储有序的元素集合
import java.util.List;
// 导入Set接口，Java集合框架的集合接口，用于存储不重复的元素集合
import java.util.Set;

// 静态导入checkArgument方法，用于检查参数条件，如果条件不满足则抛出IllegalArgumentException异常
import static com.google.common.base.Preconditions.checkArgument;

/**
 * Contains factory interface and default implementation for creating various
 * rel nodes.
 * 这个类包含用于创建各种关系节点(RelNode)的工厂接口和默认实现
 * 它是Calcite可枚举适配器中创建标准关系节点的核心工厂类
 * 提供了表扫描、投影、过滤、排序等基本操作的工厂实现
 * 这些工厂实现了org.apache.calcite.rel.core.RelFactories中定义的工厂接口
 * 使得优化器可以按照统一的方式创建不同类型的关系节点
 */
public class EnumerableRelFactories { // 定义EnumerableRelFactories类，这是一个工具类，包含各种工厂的静态实例

  // 定义一个公共静态常量，类型为TableScanFactory接口，用于创建表扫描节点
  // 这个工厂实现了RelFactories.TableScanFactory接口
  // 初始化为TableScanFactoryImpl的实例，这是TableScanFactory接口的默认实现
  // TableScanFactory负责创建EnumerableTableScan节点，用于从数据源读取数据
  // ENUMERABLE_TABLE_SCAN_FACTORY是Calcite中创建表扫描节点的标准工厂
  public static final org.apache.calcite.rel.core.RelFactories.TableScanFactory // 声明公共静态常量的类型为TableScanFactory接口
      ENUMERABLE_TABLE_SCAN_FACTORY = new TableScanFactoryImpl(); // 常量名称，初始化为TableScanFactoryImpl实例

  // 定义一个公共静态常量，类型为ProjectFactory接口，用于创建投影节点
  // 这个工厂实现了RelFactories.ProjectFactory接口
  // 初始化为ProjectFactoryImpl的实例，这是ProjectFactory接口的默认实现
  // ProjectFactory负责创建EnumerableProject节点，用于选择、重命名和计算字段
  // 投影操作对应SQL中的SELECT子句，用于从输入数据中选择和转换列
  public static final org.apache.calcite.rel.core.RelFactories.ProjectFactory // 声明公共静态常量的类型为ProjectFactory接口
      ENUMERABLE_PROJECT_FACTORY = new ProjectFactoryImpl(); // 常量名称，初始化为ProjectFactoryImpl实例

  // 定义一个公共静态常量，类型为FilterFactory接口，用于创建过滤节点
  // 这个工厂实现了RelFactories.FilterFactory接口
  // 初始化为FilterFactoryImpl的实例，这是FilterFactory接口的默认实现
  // FilterFactory负责创建EnumerableFilter节点，用于根据条件过滤行
  // 过滤操作对应SQL中的WHERE子句，用于从输入数据中筛选满足条件的行
  public static final org.apache.calcite.rel.core.RelFactories.FilterFactory // 声明公共静态常量的类型为FilterFactory接口
      ENUMERABLE_FILTER_FACTORY = new FilterFactoryImpl(); // 常量名称，初始化为FilterFactoryImpl实例

  // 定义一个公共静态常量，类型为SortFactory接口，用于创建排序节点
  // 这个工厂实现了RelFactories.SortFactory接口
  // 初始化为SortFactoryImpl的实例，这是SortFactory接口的默认实现
  // SortFactory负责创建EnumerableSort节点，用于对数据进行排序和限制
  // 排序操作对应SQL中的ORDER BY子句，以及LIMIT和OFFSET子句
  public static final org.apache.calcite.rel.core.RelFactories.SortFactory // 声明公共静态常量的类型为SortFactory接口
      ENUMERABLE_SORT_FACTORY = new SortFactoryImpl(); // 常量名称，初始化为SortFactoryImpl实例

  /**
   * Implementation of {@link org.apache.calcite.rel.core.RelFactories.TableScanFactory} that
   * returns a vanilla {@link EnumerableTableScan}.
   * 这是TableScanFactory接口的实现类，用于创建标准的EnumerableTableScan节点
   * "vanilla"表示这是一个基础的、未经过特殊修改的实现
   * TableScanFactoryImpl实现了RelFactories.TableScanFactory接口
   * 它是私有静态内部类，只能通过ENUMERABLE_TABLE_SCAN_FACTORY常量访问
   */
  private static class TableScanFactoryImpl // 定义私有静态内部类TableScanFactoryImpl，实现TableScanFactory接口
      implements org.apache.calcite.rel.core.RelFactories.TableScanFactory { // 实现TableScanFactory接口，该接口定义了创建表扫描节点的方法
    // 重写createScan方法，用于创建表扫描节点
    // 参数toRelContext：转换上下文，包含创建关系节点所需的环境信息，如集群(Cluster)等
    // 参数table：要扫描的表对象，包含表的元数据信息，如表名、字段、统计信息等
    // 返回值：返回创建的RelNode对象，具体类型为EnumerableTableScan
    @Override public RelNode createScan(RelOptTable.ToRelContext toRelContext, RelOptTable table) { // 方法签名，接收转换上下文和表对象作为参数
      // 调用EnumerableTableScan.create静态方法创建表扫描节点
      // toRelContext.getCluster()获取集群对象，集群包含类型工厂、规则集等共享资源
      // table参数指定要扫描的表对象
      // 返回创建的EnumerableTableScan节点
      return EnumerableTableScan.create(toRelContext.getCluster(), table); // 返回创建的表扫描节点
    } // createScan方法结束
  } // TableScanFactoryImpl类定义结束

  /**
   * Implementation of {@link org.apache.calcite.rel.core.RelFactories.ProjectFactory} that
   * returns a vanilla {@link EnumerableProject}.
   * 这是ProjectFactory接口的实现类，用于创建标准的EnumerableProject节点
   * ProjectFactoryImpl实现了RelFactories.ProjectFactory接口
   * 它是私有静态内部类，只能通过ENUMERABLE_PROJECT_FACTORY常量访问
   */
  private static class ProjectFactoryImpl // 定义私有静态内部类ProjectFactoryImpl，实现ProjectFactory接口
      implements org.apache.calcite.rel.core.RelFactories.ProjectFactory { // 实现ProjectFactory接口，该接口定义了创建投影节点的方法
    // 重写createProject方法，用于创建投影节点
    // 参数input：输入关系节点，即投影操作的数据源
    // 参数hints：提示列表，用于影响优化器的决策，可以为空
    // 参数childExprs：子表达式列表，每个表达式对应输出的一列，这些表达式在输入行上计算
    // 参数fieldNames：字段名称列表，指定输出列的名称，可以为null，null时会自动生成名称
    // 参数variablesSet：关联变量集合，用于处理子查询中的关联条件，在可枚举投影中必须为空
    // 返回值：返回创建的RelNode对象，具体类型为EnumerableProject
    @Override public RelNode createProject(RelNode input, List<RelHint> hints, // 方法签名开始，接收输入节点、提示列表
        List<? extends RexNode> childExprs, // 接收子表达式列表，每个表达式对应输出的一列
        @Nullable List<? extends @Nullable String> fieldNames, // 接收字段名称列表，可以为null，元素也可以为null
        Set<CorrelationId> variablesSet) { // 接收关联变量集合，用于处理子查询关联
      // 检查variablesSet是否为空集合
      // 如果不为空，抛出IllegalArgumentException异常
      // 这是因为EnumerableProject不支持关联变量，关联变量需要在其他节点中处理
      checkArgument(variablesSet.isEmpty(), // 检查关联变量集合是否为空
          "EnumerableProject does not allow variables"); // 如果不为空，抛出异常并显示错误信息
      // 创建投影节点的行类型(RowType)
      // 使用RexUtil.createStructType方法根据子表达式和字段名创建结构类型
      // input.getCluster().getTypeFactory()获取类型工厂，用于创建数据类型
      // childExprs是子表达式列表，每个表达式对应输出的一列
      // fieldNames是字段名称列表，指定输出列的名称
      // SqlValidatorUtil.F_SUGGESTER是字段名建议器，当字段名为null时自动生成名称
      // rowType描述了投影节点的输出行结构，包括字段名和类型
      final RelDataType rowType = // 声明行类型变量
          RexUtil.createStructType(input.getCluster().getTypeFactory(), childExprs, // 调用RexUtil创建结构类型
              fieldNames, SqlValidatorUtil.F_SUGGESTER); // 传入字段名和建议器
      // 调用EnumerableProject.create静态方法创建投影节点
      // input参数指定输入关系节点
      // childExprs参数指定投影表达式列表
      // rowType参数指定输出行类型
      // 返回创建的EnumerableProject节点
      return EnumerableProject.create(input, childExprs, rowType); // 返回创建的投影节点
    } // createProject方法结束
  } // ProjectFactoryImpl类定义结束

  /**
   * Implementation of {@link org.apache.calcite.rel.core.RelFactories.FilterFactory} that
   * returns a vanilla {@link EnumerableFilter}.
   * 这是FilterFactory接口的实现类，用于创建标准的EnumerableFilter节点
   * FilterFactoryImpl实现了RelFactories.FilterFactory接口
   * 它是私有静态内部类，只能通过ENUMERABLE_FILTER_FACTORY常量访问
   */
  private static class FilterFactoryImpl // 定义私有静态内部类FilterFactoryImpl，实现FilterFactory接口
      implements org.apache.calcite.rel.core.RelFactories.FilterFactory { // 实现FilterFactory接口，该接口定义了创建过滤节点的方法
    // 重写createFilter方法，用于创建过滤节点
    // 参数input：输入关系节点，即过滤操作的数据源
    // 参数condition：过滤条件，是一个RexNode表达式，对于每一行计算结果为true的行会被保留
    // 参数variablesSet：关联变量集合，用于处理子查询中的关联条件，在可枚举过滤中会被忽略
    // 返回值：返回创建的RelNode对象，具体类型为EnumerableFilter
    @Override public RelNode createFilter(RelNode input, RexNode condition, // 方法签名开始，接收输入节点和过滤条件
                         Set<CorrelationId> variablesSet) { // 接收关联变量集合，用于处理子查询关联
      // 调用EnumerableFilter.create静态方法创建过滤节点
      // input参数指定输入关系节点
      // condition参数指定过滤条件表达式
      // variablesSet参数在可枚举过滤中未被使用
      // 返回创建的EnumerableFilter节点
      return EnumerableFilter.create(input, condition); // 返回创建的过滤节点
    } // createFilter方法结束
  } // FilterFactoryImpl类定义结束

  /**
   * Implementation of {@link org.apache.calcite.rel.core.RelFactories.SortFactory} that
   * returns a vanilla {@link EnumerableSort}.
   * 这是SortFactory接口的实现类，用于创建标准的EnumerableSort节点
   * SortFactoryImpl实现了RelFactories.SortFactory接口
   * 它是私有静态内部类，只能通过ENUMERABLE_SORT_FACTORY常量访问
   */
  private static class SortFactoryImpl // 定义私有静态内部类SortFactoryImpl，实现SortFactory接口
      implements org.apache.calcite.rel.core.RelFactories.SortFactory { // 实现SortFactory接口，该接口定义了创建排序节点的方法
    // 重写createSort方法，用于创建排序节点
    // 参数input：输入关系节点，即排序操作的数据源
    // 参数collation：排序规则(RelCollation)，定义了排序的字段和方向(升序或降序)
    // 参数offset：偏移量表达式，表示跳过的行数，对应SQL中的OFFSET子句，可以为null
    // 参数fetch：获取行数表达式，表示返回的最大行数，对应SQL中的LIMIT子句，可以为null
    // 返回值：返回创建的RelNode对象，具体类型为EnumerableSort
    @Override public RelNode createSort(RelNode input, RelCollation collation, // 方法签名开始，接收输入节点和排序规则
        @Nullable RexNode offset, @Nullable RexNode fetch) { // 接收偏移量和获取行数表达式，都可以为null
      // 调用EnumerableSort.create静态方法创建排序节点
      // input参数指定输入关系节点
      // collation参数指定排序规则
      // offset参数指定偏移量表达式，可以为null表示不跳过任何行
      // fetch参数指定获取行数表达式，可以为null表示不限制行数
      // 返回创建的EnumerableSort节点
      return EnumerableSort.create(input, collation, offset, fetch); // 返回创建的排序节点
    } // createSort方法结束
  } // SortFactoryImpl类定义结束

  // 私有构造方法，防止实例化
  // 这是一个工具类，所有成员都是静态的，不需要创建实例
  // 通过私有构造方法确保该类不能被实例化
  private EnumerableRelFactories() { // 私有构造方法，不接受任何参数
  } // 构造方法为空，不做任何操作
} // EnumerableRelFactories类定义结束
