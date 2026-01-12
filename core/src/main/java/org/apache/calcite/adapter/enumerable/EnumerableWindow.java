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
package org.apache.calcite.adapter.enumerable; // 声明包名，属于Calcite的可枚举适配器包，用于实现可枚举调用约定的关系算子

import org.apache.calcite.adapter.enumerable.impl.WinAggAddContextImpl; // 导入窗口聚合添加上下文实现类，用于窗口聚合函数的添加操作
import org.apache.calcite.adapter.enumerable.impl.WinAggResetContextImpl; // 导入窗口聚合重置上下文实现类，用于窗口聚合函数的重置操作
import org.apache.calcite.adapter.enumerable.impl.WinAggResultContextImpl; // 导入窗口聚合结果上下文实现类，用于窗口聚合函数的结果获取
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂，用于创建Java类型
import org.apache.calcite.config.CalciteSystemProperty; // 导入Calcite系统属性，用于配置系统行为
import org.apache.calcite.linq4j.tree.BinaryExpression; // 导入LINQ4j二进制表达式，表示二元运算表达式
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入代码块构建器，用于构建代码块
import org.apache.calcite.linq4j.tree.BlockStatement; // 导入代码块语句，表示一个代码块
import org.apache.calcite.linq4j.tree.DeclarationStatement; // 导入声明语句，用于变量声明
import org.apache.calcite.linq4j.tree.Expression; // 导入表达式基类，表示各种表达式
import org.apache.calcite.linq4j.tree.Expressions; // 导入表达式工具类，用于创建表达式
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入参数表达式，表示方法参数
import org.apache.calcite.linq4j.tree.Primitive; // 导入基本类型工具类，处理基本类型
import org.apache.calcite.linq4j.tree.Statement; // 导入语句基类，表示各种语句
import org.apache.calcite.linq4j.tree.Types; // 导入类型工具类，处理Java类型
import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群，包含优化器的共享资源
import org.apache.calcite.plan.RelOptCost; // 导入关系优化成本，表示查询执行成本
import org.apache.calcite.plan.RelOptPlanner; // 导入关系优化器，用于查询优化
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合，定义关系算子的特征
import org.apache.calcite.rel.RelFieldCollation; // 导入关系字段排序，定义字段排序方式
import org.apache.calcite.rel.RelNode; // 导入关系节点基类，表示关系代数算子
import org.apache.calcite.rel.core.AggregateCall; // 导入聚合调用，表示聚合函数调用
import org.apache.calcite.rel.core.Window; // 导入窗口算子基类，表示窗口操作
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入关系元数据查询，用于查询关系算子的元数据
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型，表示关系表的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂，用于创建关系数据类型
import org.apache.calcite.rex.RexInputRef; // 导入Rex输入引用，表示对输入字段的引用
import org.apache.calcite.rex.RexLiteral; // 导入Rex字面量，表示常量值
import org.apache.calcite.rex.RexNode; // 导入Rex节点基类，表示行表达式
import org.apache.calcite.rex.RexWindowBound; // 导入窗口边界，定义窗口的上下边界
import org.apache.calcite.rex.RexWindowExclusion; // 导入窗口排除，定义窗口中要排除的行
import org.apache.calcite.runtime.SortedMultiMap; // 导入排序多重映射，用于存储排序后的分区数据
import org.apache.calcite.sql.SqlAggFunction; // 导入SQL聚合函数，表示SQL聚合函数
import org.apache.calcite.sql.validate.SqlConformance; // 导入SQL一致性，定义SQL方言的一致性规则
import org.apache.calcite.util.BuiltInMethod; // 导入内置方法，表示Calcite内置的方法
import org.apache.calcite.util.ImmutableBitSet; // 导入不可变位集合，用于表示字段索引集合
import org.apache.calcite.util.Pair; // 导入键值对，用于存储两个相关值
import org.apache.calcite.util.Util; // 导入工具类，提供各种实用方法

import com.google.common.collect.ImmutableList; // 导入Google不可变列表，提供不可变的列表实现

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可空类型

import java.lang.reflect.Modifier; // 导入反射修饰符，用于访问类、方法、字段的修饰符
import java.lang.reflect.Type; // 导入反射类型，表示Java类型
import java.util.ArrayList; // 导入ArrayList动态数组实现
import java.util.Arrays; // 导入数组工具类，提供数组操作方法
import java.util.Iterator; // 导入迭代器接口，用于遍历集合
import java.util.List; // 导入List接口，表示有序集合
import java.util.Optional; // 导入Optional类，用于处理可能为空的值
import java.util.function.Function; // 导入函数式接口，表示函数

import static org.apache.calcite.linq4j.Nullness.castNonNull; // 静态导入非空转换方法，用于类型转换

import static java.util.Objects.requireNonNull; // 静态导入非空检查方法，用于参数验证

/** Implementation of {@link org.apache.calcite.rel.core.Window} in
 * {@link org.apache.calcite.adapter.enumerable.EnumerableConvention enumerable calling convention}. */
// 类注释：这是Window关系算子在可枚举调用约定下的实现类
// Window算子用于SQL窗口函数（如SUM() OVER、ROW_NUMBER()等）的执行
// EnumerableConvention表示该算子可以通过LINQ风格的Java代码来执行
public class EnumerableWindow extends Window implements EnumerableRel { // 类声明：继承Window基类，实现EnumerableRel接口
  /** Creates an EnumerableWindowRel. */
  // 构造方法注释：创建一个EnumerableWindow关系算子实例
  EnumerableWindow(RelOptCluster cluster, // 参数：关系优化集群，包含优化器的共享资源
      RelTraitSet traits, // 参数：关系特征集合，定义该算子的特征（如调用约定）
      RelNode child, // 参数：子关系节点，表示窗口操作的输入数据源
      List<RexLiteral> constants, // 参数：常量列表，窗口函数中使用的常量值
      RelDataType rowType, // 参数：输出行类型，定义窗口操作后输出的数据类型
      List<Group> groups) { // 参数：分组列表，包含窗口分组信息（如PARTITION BY、ORDER BY、窗口范围等）
    super(cluster, traits, child, constants, rowType, groups); // 调用父类Window的构造方法初始化
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 方法重写：复制当前关系算子，用于优化器生成新的算子实例
    return new EnumerableWindow(getCluster(), traitSet, sole(inputs), // 返回：创建新的EnumerableWindow实例，使用新的特征集合和输入
        constants, getRowType(), groups); // 保持原有的常量、行类型和分组信息不变
  }

  @Override public Window copy(List<RexLiteral> constants) { // 方法重写：复制当前窗口算子，只改变常量列表
    return new EnumerableWindow(getCluster(), getTraitSet(), getInput(), // 返回：创建新的EnumerableWindow实例，使用新的常量列表
        constants, getRowType(), groups); // 保持原有的集群、特征、输入、行类型和分组信息不变
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 方法重写：计算当前算子的执行成本，用于优化器选择最优执行计划
      RelMetadataQuery mq) { // 参数：元数据查询对象，用于获取关系算子的元数据信息
    RelOptCost cost = super.computeSelfCost(planner, mq); // 调用父类方法计算基础成本
    if (cost == null) { // 如果成本计算失败
      return null; // 返回null表示无法计算成本
    }
    return cost.multiplyBy(EnumerableConvention.COST_MULTIPLIER); // 返回：将成本乘以可枚举约定的成本乘数，因为可枚举实现通常比原生实现稍慢
  }

  /** Implementation of {@link RexToLixTranslator.InputGetter}
   * suitable for generating implementations of windowed aggregate
   * functions. */
  // 内部类注释：实现RexToLixTranslator.InputGetter接口，用于生成窗口聚合函数实现时获取输入字段
  // 该类负责将Rex表达式中的字段引用转换为LINQ表达式中的字段访问
  private static class WindowRelInputGetter // 内部类声明：窗口关系输入获取器
      implements RexToLixTranslator.InputGetter { // 实现InputGetter接口，用于获取输入字段
    private final Expression row; // 成员变量：表示当前行的表达式，用于访问行数据
    private final PhysType rowPhysType; // 成员变量：行的物理类型，包含行的Java类型和格式信息
    private final int actualInputFieldCount; // 成员变量：实际输入字段数量，用于区分输入字段和常量字段
    private final List<Expression> constants; // 成员变量：常量表达式列表，用于访问窗口函数中的常量

    private WindowRelInputGetter(Expression row, // 构造方法：创建窗口关系输入获取器实例
        PhysType rowPhysType, // 参数：行的物理类型
        int actualInputFieldCount, // 参数：实际输入字段数量
        List<Expression> constants) { // 参数：常量表达式列表
      this.row = row; // 初始化当前行表达式
      this.rowPhysType = rowPhysType; // 初始化行的物理类型
      this.actualInputFieldCount = actualInputFieldCount; // 初始化实际输入字段数量
      this.constants = constants; // 初始化常量列表
    }

    @Override public Expression field(BlockBuilder list, int index, @Nullable Type storageType) { // 方法重写：获取指定索引的字段表达式
      if (index < actualInputFieldCount) { // 如果索引在输入字段范围内
        Expression current = list.append("current", row); // 将当前行表达式添加到代码块构建器中
        return rowPhysType.fieldReference(current, index, storageType); // 返回：创建对当前行指定字段的引用表达式
      }
      return constants.get(index - actualInputFieldCount); // 返回：从常量列表中获取对应的常量表达式（索引减去输入字段数量）
    }
  }

  @SuppressWarnings({"unused", "nullness"}) // 抑制警告：忽略未使用变量和空值检查的警告
  private static void sampleOfTheGeneratedWindowedAggregate() { // 方法：生成的窗口聚合代码示例，用于理解代码生成逻辑
    // Here's overview of the generated code
    // For each list of rows that have the same partitioning key, evaluate
    // all of the windowed aggregate functions.
    // 注释：以下是生成代码的概览，对于具有相同分区键的行列表，评估所有窗口聚合函数

    // builder
    Iterator<Integer[]> iterator = null; // 注释：分区迭代器，用于遍历每个分区的行数组

    // builder3
    Integer[] rows = iterator.next(); // 注释：获取当前分区的所有行

    int prevStart = -1; // 注释：记录上一个窗口的起始位置，用于优化窗口计算
    int prevEnd = -1; // 注释：记录上一个窗口的结束位置，用于优化窗口计算

    for (int i = 0; i < rows.length; i++) { // 注释：遍历当前分区的每一行
      // builder4
      Integer row = rows[i]; // 注释：获取当前行

      int start = 0; // 注释：计算当前行的窗口起始位置
      int end = 100; // 注释：计算当前行的窗口结束位置
      if (start != prevStart || end != prevEnd) { // 注释：如果窗口范围发生变化，需要重新计算
        // builder5
        int actualStart = 0; // 注释：确定实际需要处理的起始位置
        if (start != prevStart || end < prevEnd) { // 注释：如果窗口起始位置变化或窗口缩小，需要完全重新计算
          // builder6
          // recompute
          actualStart = start; // 注释：从窗口起始位置开始重新计算
          // implementReset
          // 注释：重置聚合函数的状态
        } else { // must be start == prevStart && end > prevEnd
          // 注释：窗口起始位置不变但窗口扩大，只需处理新增的行
          actualStart = prevEnd + 1; // 注释：从上一个窗口结束位置的下一位开始处理
        }
        prevStart = start; // 注释：更新当前窗口起始位置
        prevEnd = end; // 注释：更新当前窗口结束位置

        if (start != -1) { // 注释：如果窗口有效（起始位置不为-1）
          for (int j = actualStart; j <= end; j++) { // 注释：遍历窗口内的每一行
            // builder7
            // implementAdd
            // 注释：将当前行添加到聚合函数中
          }
        }
        // implementResult
        // list.add(new Xxx(row.deptno, row.empid, sum, count));
        // 注释：获取聚合结果并添加到输出列表中
      }
    }
    // multiMap.clear(); // allows gc
    // 注释：清空多重映射，允许垃圾回收
    // source = Linq4j.asEnumerable(list);
    // 注释：将结果列表转换为可枚举对象
  }

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 方法重写：实现窗口算子，生成可执行的Java代码
    final JavaTypeFactory typeFactory = implementor.getTypeFactory(); // 获取Java类型工厂，用于创建Java类型
    final EnumerableRel child = (EnumerableRel) getInput(); // 获取子关系节点（输入数据源）
    final BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器，用于构建生成的代码
    final Result result = implementor.visitChild(this, 0, child, pref); // 访问子节点，生成子节点的实现代码
    Expression source_ = builder.append("source", result.block); // 将子节点的结果添加到代码块中，命名为"source"

    final List<Expression> translatedConstants = // 创建常量表达式列表
        new ArrayList<>(constants.size()); // 初始化列表大小为常量数量
    for (RexLiteral constant : constants) { // 遍历所有常量
      translatedConstants.add( // 将常量转换为LINQ表达式
          RexToLixTranslator.translateLiteral(constant, constant.getType(), // 调用翻译器将Rex字面量转换为Java表达式
              typeFactory, RexImpTable.NullAs.NULL)); // 空值作为NULL处理
    }

    PhysType inputPhysType = result.physType; // 获取输入数据的物理类型

    ParameterExpression prevStart = // 创建参数表达式，用于记录上一个窗口的起始位置
        Expressions.parameter(int.class, builder.newName("prevStart")); // 类型为int，名称自动生成
    ParameterExpression prevEnd = // 创建参数表达式，用于记录上一个窗口的结束位置
        Expressions.parameter(int.class, builder.newName("prevEnd")); // 类型为int，名称自动生成

    builder.add(Expressions.declare(0, prevStart, null)); // 声明prevStart变量，初始值为null
    builder.add(Expressions.declare(0, prevEnd, null)); // 声明prevEnd变量，初始值为null

    for (int windowIdx = 0; windowIdx < groups.size(); windowIdx++) { // 遍历所有窗口分组（一个SQL语句可能有多个窗口定义）
      Group group = groups.get(windowIdx); // 获取当前窗口分组，包含分区键、排序键、窗口范围等信息
      // Comparator:
      // final Comparator<JdbcTest.Employee> comparator =
      //    new Comparator<JdbcTest.Employee>() {
      //      public int compare(JdbcTest.Employee o1,
      //          JdbcTest.Employee o2) {
      //        return Integer.compare(o1.empid, o2.empid);
      //      }
      //    };
      // 注释：生成比较器，用于根据ORDER BY子句对行进行排序
      final Expression comparator_ = // 创建比较器表达式
          builder.append("comparator", // 将比较器添加到代码块中，命名为"comparator"
              inputPhysType.generateComparator(group.collation())); // 根据分组中的排序规则生成比较器

      Pair<Expression, Expression> partitionIterator = // 获取分区迭代器，用于遍历每个分区的数据
          getPartitionIterator(builder, source_, inputPhysType, group, // 调用方法生成分区迭代器代码
              comparator_); // 传入比较器用于排序
      final Expression collectionExpr = partitionIterator.left; // 获取分区集合表达式（存储所有分区）
      final Expression iterator_ = partitionIterator.right; // 获取分区迭代器表达式（用于遍历分区）

      List<AggImpState> aggs = new ArrayList<>(); // 创建聚合实现状态列表，用于跟踪每个聚合函数的实现状态
      List<AggregateCall> aggregateCalls = group.getAggregateCalls(this); // 获取当前分组中的所有聚合调用（如SUM、COUNT等）
      for (int aggIdx = 0; aggIdx < aggregateCalls.size(); aggIdx++) { // 遍历所有聚合调用
        AggregateCall call = aggregateCalls.get(aggIdx); // 获取当前聚合调用
        if (call.ignoreNulls()) { // 如果聚合函数指定了IGNORE NULLS
          throw new UnsupportedOperationException("IGNORE NULLS not supported"); // 抛出不支持异常（当前实现不支持）
        }
        aggs.add(new AggImpState(aggIdx, call, true)); // 创建聚合实现状态并添加到列表中，标记为窗口聚合
      }

      // The output from this stage is the input plus the aggregate functions.
      // 注释：当前阶段的输出是输入字段加上聚合函数的结果字段
      final RelDataTypeFactory.Builder typeBuilder = typeFactory.builder(); // 创建关系数据类型构建器
      typeBuilder.addAll(inputPhysType.getRowType().getFieldList()); // 添加所有输入字段到输出类型
      for (AggImpState agg : aggs) { // 遍历所有聚合状态
        // [CALCITE-4326] NullPointerException possible in EnumerableWindow when
        // agg.call.name is null
        // 注释：防止聚合名称为null导致空指针异常
        String name = // 获取聚合函数的名称
            requireNonNull(agg.call.name, () -> "agg.call.name for " + agg.call); // 非空检查，如果为null抛出异常
        typeBuilder.add(name, agg.call.type); // 将聚合函数的结果字段添加到输出类型
      }
      RelDataType outputRowType = typeBuilder.build(); // 构建输出行类型
      final PhysType outputPhysType = // 创建输出物理类型
          PhysTypeImpl.of(typeFactory, outputRowType, pref.prefer(result.format)); // 根据类型工厂、输出行类型和首选格式创建

      final Expression list_ = // 创建结果列表表达式，用于存储窗口计算的结果
          builder.append("list", // 将列表添加到代码块中，命名为"list"
              Expressions.new_(ArrayList.class, // 创建ArrayList实例
                  Expressions.call(collectionExpr, // 根据分区集合的大小初始化ArrayList
                      BuiltInMethod.COLLECTION_SIZE.method)), // 调用size方法获取集合大小
              false); // 不优化内联，防止在循环中重复创建

      Pair<@Nullable Expression, @Nullable Expression> collationKey = // 获取行的排序键，用于RANGE窗口类型
          getRowCollationKey(builder, inputPhysType, group, windowIdx); // 调用方法生成排序键选择器和比较器
      Expression keySelector = collationKey.left; // 获取键选择器表达式（用于提取排序键）
      Expression keyComparator = collationKey.right; // 获取键比较器表达式（用于比较排序键）
      final BlockBuilder builder3 = new BlockBuilder(); // 创建代码块构建器3，用于构建处理每个分区的代码
      final Expression rows_ = // 获取当前分区的所有行
          builder3.append("rows", // 将行数组添加到代码块中，命名为"rows"
              Expressions.convert_( // 转换表达式类型
                  Expressions.call(iterator_, BuiltInMethod.ITERATOR_NEXT.method), // 调用迭代器的next方法获取下一个分区
                  Object[].class), // 转换为Object[]类型
              false); // 不优化内联

      builder3.add( // 初始化prevStart为-1，表示还没有处理过窗口
          Expressions.statement(
              Expressions.assign(prevStart, Expressions.constant(-1)))); // 赋值语句：prevStart = -1
      builder3.add( // 初始化prevEnd为最大值，表示窗口可以扩展到任意位置
          Expressions.statement(
              Expressions.assign(prevEnd, // 赋值语句
                  Expressions.constant(Integer.MAX_VALUE)))); // prevEnd = Integer.MAX_VALUE

      final BlockBuilder builder4 = new BlockBuilder(); // 创建代码块构建器4，用于构建处理每一行的代码

      final ParameterExpression i_ = // 创建循环索引参数表达式
          Expressions.parameter(int.class, builder4.newName("i")); // 类型为int，名称自动生成

      final Expression row_ = // 获取当前行的表达式
          builder4.append("row", // 将行表达式添加到代码块中，命名为"row"
              EnumUtils.convert(Expressions.arrayIndex(rows_, i_), // 从行数组中获取索引为i的行
                  inputPhysType.getJavaRowType())); // 转换为输入物理类型的Java行类型

      final RexToLixTranslator.InputGetter inputGetter = // 创建输入获取器，用于将Rex表达式中的字段引用转换为Java表达式
          new WindowRelInputGetter(row_, inputPhysType, // 使用当前行和输入物理类型创建
              result.physType.getRowType().getFieldCount(), // 传入实际输入字段数量
              translatedConstants); // 传入翻译后的常量列表

      final RexToLixTranslator translator = // 创建Rex到LINQ表达式翻译器，用于将Rex表达式转换为Java表达式
          RexToLixTranslator.forAggregation(typeFactory, builder4, // 为聚合创建翻译器
              inputGetter, implementor.getConformance()); // 传入输入获取器和SQL一致性规则

      final List<Expression> outputRow = new ArrayList<>(); // 创建输出行表达式列表，用于构建输出记录
      int fieldCountWithAggResults = // 计算包含聚合结果的字段数量
          inputPhysType.getRowType().getFieldCount(); // 等于输入字段数量
      for (int i = 0; i < fieldCountWithAggResults; i++) { // 遍历所有输入字段
        outputRow.add( // 将输入字段添加到输出行中
            inputPhysType.fieldReference(row_, i, // 创建对当前行第i个字段的引用
                outputPhysType.getJavaFieldType(i))); // 使用输出物理类型的Java字段类型
      }

      declareAndResetState(typeFactory, builder, result, windowIdx, aggs, // 声明并重置聚合函数的状态变量
          outputPhysType, outputRow, group.exclude); // 传入输出物理类型、输出行和窗口排除规则

      // There are assumptions that minX==0. If ever change this, look for
      // frameRowCount, bounds checking, etc
      // 注释：假设minX总是为0，如果改变这个假设，需要检查frameRowCount和边界检查等逻辑
      final Expression minX = Expressions.constant(0); // 创建最小索引常量，总是为0（数组从0开始）
      final Expression partitionRowCount = // 获取当前分区的行数
          builder3.append("partRows", Expressions.field(rows_, "length")); // 获取行数组的length字段
      final Expression maxX = // 计算最大索引（最后一个元素的索引）
          builder3.append("maxX", // 添加到代码块3中，命名为"maxX"
              Expressions.subtract(partitionRowCount, Expressions.constant(1))); // 行数减1

      final Expression startUnchecked = // 计算窗口的起始位置（未检查边界）
          builder4.append("start", // 添加到代码块4中，命名为"start"
              translateBound(translator, i_, row_, minX, maxX, rows_, // 调用翻译边界方法
                  group, true, inputPhysType, keySelector, keyComparator)); // true表示计算下边界
      final Expression endUnchecked = // 计算窗口的结束位置（未检查边界）
          builder4.append("end", // 添加到代码块4中，命名为"end"
              translateBound(translator, i_, row_, minX, maxX, rows_, // 调用翻译边界方法
                  group, false, inputPhysType, keySelector, keyComparator)); // false表示计算上边界

      final Expression startX; // 最终的窗口起始位置表达式
      final Expression endX; // 最终的窗口结束位置表达式
      final Expression hasRows; // 表示窗口是否包含行（有效）的表达式
      if (group.isAlwaysNonEmpty()) { // 如果窗口总是非空的（如UNBOUNDED PRECEDING到UNBOUNDED FOLLOWING）
        startX = startUnchecked; // 直接使用未检查的起始位置
        endX = endUnchecked; // 直接使用未检查的结束位置
        hasRows = Expressions.constant(true); // 窗口总是有行
      } else { // 窗口可能为空，需要进行边界检查
        Expression startTmp = // 计算临时起始位置
            group.lowerBound.isUnbounded() || startUnchecked == i_ // 如果下边界无界或等于当前行
                ? startUnchecked // 直接使用未检查的起始位置
                : builder4.append("startTmp", // 否则需要用max确保不小于最小索引
                    Expressions.call(null, BuiltInMethod.MATH_MAX.method, // 调用Math.max方法
                        startUnchecked, minX)); // 取startUnchecked和minX的较大值
        Expression endTmp = // 计算临时结束位置
            group.upperBound.isUnbounded() || endUnchecked == i_ // 如果上边界无界或等于当前行
                ? endUnchecked // 直接使用未检查的结束位置
                : builder4.append("endTmp", // 否则需要用min确保不大于最大索引
                    Expressions.call(null, BuiltInMethod.MATH_MIN.method, // 调用Math.min方法
                        endUnchecked, maxX)); // 取endUnchecked和maxX的较小值

        ParameterExpression startPe = // 声明经过检查的起始位置变量
            Expressions.parameter(0, int.class, builder4.newName("startChecked")); // 类型为int，名称自动生成
        ParameterExpression endPe = // 声明经过检查的结束位置变量
            Expressions.parameter(0, int.class, builder4.newName("endChecked")); // 类型为int，名称自动生成
        builder4.add(Expressions.declare(Modifier.FINAL, startPe, null)); // 声明startPe为final变量，初始值为null
        builder4.add(Expressions.declare(Modifier.FINAL, endPe, null)); // 声明endPe为final变量，初始值为null

        hasRows = // 判断窗口是否有效（起始位置不大于结束位置）
            builder4.append("hasRows", Expressions.lessThanOrEqual(startTmp, endTmp)); // startTmp <= endTmp
        builder4.add( // 根据窗口是否有效设置起始和结束位置
            Expressions.ifThenElse(hasRows, // 如果窗口有效
                Expressions.block( // 则设置有效的起始和结束位置
                    Expressions.statement(
                        Expressions.assign(startPe, startTmp)), // startPe = startTmp
                    Expressions.statement(
                      Expressions.assign(endPe, endTmp))), // endPe = endTmp
            Expressions.block( // 否则设置无效的起始和结束位置
                Expressions.statement(
                    Expressions.assign(startPe, Expressions.constant(-1))), // startPe = -1
                Expressions.statement(
                    Expressions.assign(endPe, Expressions.constant(-1)))))); // endPe = -1
        startX = startPe; // 使用经过检查的起始位置
        endX = endPe; // 使用经过检查的结束位置
      }

      final BlockBuilder builder5 = new BlockBuilder(true, builder4); // 创建代码块构建器5，用于构建窗口变化的处理代码，继承builder4

      BinaryExpression rowCountWhenNonEmpty = // 计算窗口中的行数（当窗口非空时）
          Expressions.add(startX == minX ? endX : Expressions.subtract(endX, startX), // 如果起始位置是最小索引，直接用结束位置；否则用结束位置减去起始位置
              Expressions.constant(1)); // 加1，因为索引从0开始

      final Expression frameRowCount; // 窗口行数表达式

      if (hasRows.equals(Expressions.constant(true))) { // 如果窗口总是非空
        frameRowCount = // 直接使用计算的行数
            builder4.append("totalRows", rowCountWhenNonEmpty); // 添加到代码块4中，命名为"totalRows"
      } else { // 窗口可能为空
        frameRowCount = // 根据窗口是否有效返回行数或0
            builder4.append("totalRows", // 添加到代码块4中，命名为"totalRows"
                Expressions.condition(hasRows, rowCountWhenNonEmpty, // 如果窗口有效，返回计算的行数
                    Expressions.constant(0))); // 否则返回0
      }

      ParameterExpression actualStart = // 声明实际起始位置变量，用于优化窗口计算
          Expressions.parameter(0, int.class, builder5.newName("actualStart")); // 类型为int，名称自动生成

      final BlockBuilder builder6 = new BlockBuilder(true, builder5); // 创建代码块构建器6，用于构建重置状态的代码，继承builder5
      builder6.add( // 初始化actualStart为窗口起始位置
          Expressions.statement(Expressions.assign(actualStart, startX))); // actualStart = startX

      for (final AggImpState agg : aggs) { // 遍历所有聚合状态
        List<Expression> aggState = requireNonNull(agg.state, "agg.state"); // 获取聚合状态变量列表
        agg.implementor.implementReset(requireNonNull(agg.context, "agg.context"), // 调用聚合实现器的重置方法
            new WinAggResetContextImpl(builder6, aggState, i_, startX, endX, // 创建窗口聚合重置上下文实现
                hasRows, frameRowCount, partitionRowCount)); // 传入窗口相关信息
      }

      Expression lowerBoundCanChange = // 判断窗口下边界是否会变化
          group.lowerBound.isUnboundedPreceding() // 如果下边界是UNBOUNDED PRECEDING
          ? Expressions.constant(false) // 则不会变化
          : Expressions.notEqual(startX, prevStart); // 否则比较当前起始位置和上一个起始位置是否不同

      Expression isExcluding = // 判断是否有EXCLUDE子句
          Expressions.constant(group.exclude != RexWindowExclusion.EXCLUDE_NO_OTHER); // 如果排除规则不是"不排除其他"，则为true

      // If there's exclude clause we need to recompute the window every time, as rows can affect
      // differently for the same frame.
      // 注释：如果有EXCLUDE子句，每次都需要重新计算窗口，因为相同的窗口帧可能对不同的行产生不同的影响
      Expression needRecomputeWindow = // 判断是否需要重新计算窗口
          Expressions.orElse(Expressions.orElse(isExcluding, lowerBoundCanChange), // 如果有排除子句或下边界变化，需要重新计算
              Expressions.lessThan(endX, prevEnd)); // 或者窗口缩小（结束位置小于上一个结束位置），也需要重新计算

      BlockStatement resetWindowState = builder6.toBlock(); // 将builder6转换为代码块语句，包含重置聚合状态的代码
      if (resetWindowState.statements.size() == 1) { // 如果重置代码块只有一个语句（通常是空语句）
        builder5.add( // 优化：直接使用条件表达式设置actualStart，避免不必要的if语句
            Expressions.declare(0, actualStart, // 声明actualStart变量
                Expressions.condition(needRecomputeWindow, startX, // 如果需要重新计算，使用startX
                    Expressions.add(prevEnd, Expressions.constant(1))))); // 否则从上一个结束位置的下一位开始
      } else { // 如果重置代码块有多个语句
        builder5.add( // 需要使用if语句来决定是否执行重置代码
            Expressions.declare(0, actualStart, null)); // 声明actualStart变量，初始值为null
        builder5.add( // 添加if语句
            Expressions.ifThenElse(needRecomputeWindow, // 如果需要重新计算窗口
                resetWindowState, // 执行重置代码块
                Expressions.statement( // 否则只更新actualStart
                    Expressions.assign(actualStart, // actualStart =
                        Expressions.add(prevEnd, Expressions.constant(1)))))); // prevEnd + 1
      }

      if (lowerBoundCanChange instanceof BinaryExpression) { // 如果下边界可能会变化（不是常量false）
        builder5.add( // 更新上一个窗口的起始位置
            Expressions.statement(Expressions.assign(prevStart, startX))); // prevStart = startX
      }
      builder5.add( // 更新上一个窗口的结束位置
          Expressions.statement(Expressions.assign(prevEnd, endX))); // prevEnd = endX

      final BlockBuilder builder7 = new BlockBuilder(true, builder5); // 创建代码块构建器7，用于构建窗口内行遍历的代码，继承builder5
      final DeclarationStatement jDecl = // 声明循环变量j，用于遍历窗口内的行
          Expressions.declare(0, "j", actualStart); // 类型为int，初始值为actualStart

      final PhysType inputPhysTypeFinal = inputPhysType; // 保存输入物理类型的最终引用（用于lambda表达式）
      final Function<BlockBuilder, WinAggFrameResultContext> // 创建函数，用于构建窗口聚合结果上下文
          resultContextBuilder = // 该函数接收BlockBuilder，返回WinAggFrameResultContext
          getBlockBuilderWinAggFrameResultContextFunction(typeFactory, // 调用方法生成上下文构建函数
              implementor.getConformance(), result, translatedConstants, // 传入一致性规则、结果和常量
              comparator_, rows_, i_, startX, endX, minX, maxX, // 传入比较器、行数组、当前索引、窗口边界等
              hasRows, frameRowCount, partitionRowCount, // 传入窗口有效性、行数等信息
              jDecl, inputPhysTypeFinal); // 传入循环变量声明和输入物理类型

      final Function<AggImpState, List<RexNode>> rexArguments = agg -> { // 创建函数，用于获取聚合函数的Rex参数列表
        List<Integer> argList = agg.call.getArgList(); // 获取聚合函数的字段索引列表
        List<RelDataType> inputTypes = // 获取这些字段的数据类型
            EnumUtils.fieldRowTypes(result.physType.getRowType(), constants, // 从物理类型中获取字段类型
                argList); // 传入字段索引列表和常量
        List<RexNode> args = new ArrayList<>(inputTypes.size()); // 创建Rex节点列表
        for (int i = 0; i < argList.size(); i++) { // 遍历所有字段索引
          Integer idx = argList.get(i); // 获取当前字段索引
          args.add(new RexInputRef(idx, inputTypes.get(i))); // 创建Rex输入引用节点
        }
        return args; // 返回Rex参数列表
      };

      implementAdd(aggs, builder7, resultContextBuilder, rexArguments, jDecl); // 实现聚合函数的添加操作
      BlockStatement forBlock = builder7.toBlock(); // 将builder7转换为代码块语句，包含窗口内行的遍历和聚合添加代码

      // Don't run the aggregate function if current row is excluded
      // 注释：如果当前行被排除（如EXCLUDE CURRENT ROW），则不运行聚合函数
      Statement exclude = buildExcludeGuard(group, comparator_, i_, jDecl, rows_, forBlock); // 构建排除守卫语句
      if (!forBlock.statements.isEmpty()) { // 如果for循环代码块不为空
        // For instance, row_number does not use for loop to compute the value
        // 注释：例如，row_number函数不需要for循环来计算值
        Statement forAggLoop = // 创建for循环语句，遍历窗口内的所有行
            Expressions.for_(Arrays.asList(jDecl), // 初始化语句：j = actualStart
                Expressions.lessThanOrEqual(jDecl.parameter, endX), // 循环条件：j <= endX
                Expressions.preIncrementAssign(jDecl.parameter), // 迭代语句：++j
                exclude); // 循环体：排除守卫保护的聚合添加代码块
        if (!hasRows.equals(Expressions.constant(true))) { // 如果窗口可能为空
          forAggLoop = Expressions.ifThen(hasRows, forAggLoop); // 只有在窗口有效时才执行for循环
        }
        builder5.add(forAggLoop); // 将for循环添加到builder5中
      }

      if (implementResult(aggs, builder5, resultContextBuilder, rexArguments, // 实现缓存版本的聚合结果获取（当窗口不变时使用）
              true)) { // true表示缓存版本
        builder4.add( // 如果有缓存版本的实现，添加if语句
            Expressions.ifThen( // 当窗口变化时才执行
                Expressions.orElse(lowerBoundCanChange, // 下边界变化
                    Expressions.notEqual(endX, prevEnd)), // 或上边界变化
                builder5.toBlock())); // 执行builder5中的代码
      }

      implementResult(aggs, builder4, resultContextBuilder, rexArguments, // 实现非缓存版本的聚合结果获取（总是执行）
          false); // false表示非缓存版本

      builder4.add( // 将输出行添加到结果列表中
          Expressions.statement(
              Expressions.call(list_, BuiltInMethod.COLLECTION_ADD.method, // 调用ArrayList的add方法
                  outputPhysType.record(outputRow)))); // 将输出行表达式列表转换为记录对象

      builder3.add( // 添加for循环，遍历当前分区的所有行
          Expressions.for_( // 创建for循环语句
              Expressions.declare(0, i_, Expressions.constant(0)), // 初始化：i = 0
              Expressions.lessThan(i_, Expressions.field(rows_, "length")), // 条件：i < rows.length
              Expressions.preIncrementAssign(i_), // 迭代：++i
              builder4.toBlock())); // 循环体：处理每一行的代码块

      builder.add( // 添加while循环，遍历所有分区
          Expressions.while_( // 创建while循环语句
              Expressions.call(iterator_, BuiltInMethod.ITERATOR_HAS_NEXT.method), // 条件：iterator.hasNext()
              builder3.toBlock())); // 循环体：处理当前分区的代码块
      builder.add( // 清空分区映射，允许垃圾回收
          Expressions.statement(
              Expressions.call(collectionExpr, BuiltInMethod.MAP_CLEAR.method))); // 调用clear方法

      // We're not assigning to "source". For each group, create a new
      // final variable called "source" or "sourceN".
      // 注释：我们不覆盖"source"变量。对于每个分组，创建一个新的final变量，命名为"source"或"sourceN"
      source_ = // 更新source表达式，指向结果列表
          builder.append("source", // 添加到代码块中，命名为"source"
              Expressions.call(BuiltInMethod.AS_ENUMERABLE.method, list_)); // 将列表转换为可枚举对象

      inputPhysType = outputPhysType; // 更新输入物理类型为输出物理类型（用于下一个窗口分组）
    }

    //   return Linq4j.asEnumerable(list);
    // 注释：返回可枚举的结果列表
    builder.add( // 添加return语句
        Expressions.return_(null, source_)); // 返回source表达式
    return implementor.result(inputPhysType, builder.toBlock()); // 返回实现结果，包含物理类型和代码块
  }

  private static Statement buildExcludeGuard(Group group, Expression comparator, // 方法：构建排除守卫语句，用于根据EXCLUDE子句过滤窗口中的行
      ParameterExpression currentRow, // 参数：当前行索引表达式
      DeclarationStatement jDecl, // 参数：窗口内行索引的声明语句
      Expression rows, // 参数：行数组表达式
      BlockStatement forBlock) { // 参数：for循环代码块
    if (group.exclude == RexWindowExclusion.EXCLUDE_CURRENT_ROW) { // 如果排除当前行（EXCLUDE CURRENT ROW）
      return Expressions.ifThen(Expressions.notEqual(currentRow, jDecl.parameter), forBlock); // 当j不等于当前行时才执行for循环
    } else if (group.exclude == RexWindowExclusion.EXCLUDE_GROUP) { // 如果排除同组行（EXCLUDE GROUP）
      return Expressions.ifThen( // 当当前行和j行不同组时才执行for循环
          Expressions.notEqual(Expressions.constant(0), // 比较结果不为0表示不同组
              Expressions.call(comparator, BuiltInMethod.COMPARATOR_COMPARE.method, // 调用比较器的compare方法
                  Expressions.arrayIndex(rows, currentRow), // 获取当前行
                  Expressions.arrayIndex(rows, jDecl.parameter))), forBlock); // 获取j行
    } else if (group.exclude == RexWindowExclusion.EXCLUDE_TIES) { // 如果排除同行行（EXCLUDE TIES）
      return Expressions.ifThen( // 当j行与当前行同行时才执行for循环
          Expressions.or(Expressions.equal(currentRow, jDecl.parameter), // j等于当前行（同一行）
              Expressions.notEqual(Expressions.constant(0), // 或不同组（不同行）
                  Expressions.call(comparator, BuiltInMethod.COMPARATOR_COMPARE.method, // 调用比较器的compare方法
                      Expressions.arrayIndex(rows, currentRow), // 获取当前行
                      Expressions.arrayIndex(rows, jDecl.parameter)))), forBlock); // 获取j行
    } else { // 如果不排除任何行（EXCLUDE NO OTHER）
      return forBlock; // 直接返回for循环代码块，不做任何过滤
    }
  }

  private static Function<BlockBuilder, WinAggFrameResultContext> // 方法：创建窗口聚合帧结果上下文构建函数
      getBlockBuilderWinAggFrameResultContextFunction( // 该函数接收BlockBuilder，返回WinAggFrameResultContext实例
      final JavaTypeFactory typeFactory, final SqlConformance conformance, // 参数：Java类型工厂和SQL一致性规则
      final Result result, final List<Expression> translatedConstants, // 参数：子节点结果和翻译后的常量
      final Expression comparator_, // 参数：比较器表达式
      final Expression rows_, final ParameterExpression i_, // 参数：行数组和当前行索引
      final Expression startX, final Expression endX, // 参数：窗口的起始和结束位置
      final Expression minX, final Expression maxX, // 参数：分区的最小和最大索引
      final Expression hasRows, final Expression frameRowCount, // 参数：窗口有效性和行数
      final Expression partitionRowCount, // 参数：分区行数
      final DeclarationStatement jDecl, // 参数：窗口内行索引的声明
      final PhysType inputPhysType) { // 参数：输入物理类型
    return block -> new WinAggFrameResultContext() { // 返回：lambda表达式，创建WinAggFrameResultContext匿名实现
      @Override public RexToLixTranslator rowTranslator(Expression rowIndex) { // 方法重写：为指定行创建Rex到LINQ表达式翻译器
        Expression row = // 获取指定索引的行表达式
            getRow(rowIndex); // 调用getRow方法获取行
        final RexToLixTranslator.InputGetter inputGetter = // 创建输入获取器
            new WindowRelInputGetter(row, inputPhysType, // 使用当前行和输入物理类型创建
                result.physType.getRowType().getFieldCount(), // 传入实际输入字段数量
                translatedConstants); // 传入翻译后的常量

        return RexToLixTranslator.forAggregation(typeFactory, // 为聚合创建翻译器
            block, inputGetter, conformance); // 传入代码块、输入获取器和一致性规则
      }

      @Override public Expression computeIndex(Expression offset, // 方法重写：根据偏移量和查找类型计算行索引
          WinAggImplementor.SeekType seekType) { // 参数：偏移量表达式和查找类型
        Expression index; // 声明索引表达式
        if (seekType == WinAggImplementor.SeekType.AGG_INDEX) { // 如果查找类型是聚合索引（当前窗口内正在处理的行）
          index = jDecl.parameter; // 使用j参数（窗口内行索引）
        } else if (seekType == WinAggImplementor.SeekType.SET) { // 如果查找类型是SET（当前正在计算结果的行）
          index = i_; // 使用i参数（当前分区内的行索引）
        } else if (seekType == WinAggImplementor.SeekType.START) { // 如果查找类型是START（窗口起始位置）
          index = startX; // 使用startX表达式
        } else if (seekType == WinAggImplementor.SeekType.END) { // 如果查找类型是END（窗口结束位置）
          index = endX; // 使用endX表达式
        } else { // 不支持的查找类型
          throw new IllegalArgumentException("SeekSet " + seekType // 抛出非法参数异常
              + " is not supported"); // 提示查找类型不被支持
        }
        if (!Expressions.constant(0).equals(offset)) { // 如果偏移量不为0
          index = block.append("idx", Expressions.add(index, offset)); // 计算偏移后的索引
        }
        return index; // 返回计算后的索引表达式
      }

      private Expression checkBounds(Expression rowIndex, // 私有方法：检查行索引是否在指定范围内
          Expression minIndex, Expression maxIndex) { // 参数：行索引、最小索引、最大索引
        if (rowIndex == i_ || rowIndex == startX || rowIndex == endX) { // 如果行索引是i、startX或endX
          // No additional bounds check required
          // 注释：不需要额外的边界检查，因为这些索引已经被验证过
          return hasRows; // 直接返回窗口有效性表达式
        }

        return block.append("rowInFrame", // 否则需要检查边界
            Expressions.foldAnd( // 创建AND表达式，组合所有条件
                ImmutableList.of(hasRows, // 窗口有效
                    Expressions.greaterThanOrEqual(rowIndex, minIndex), // 行索引 >= 最小索引
                    Expressions.lessThanOrEqual(rowIndex, maxIndex)))); // 行索引 <= 最大索引
      }

      @Override public Expression rowInFrame(Expression rowIndex) { // 方法重写：检查行是否在窗口帧内
        return checkBounds(rowIndex, startX, endX); // 使用窗口的起始和结束位置检查
      }

      @Override public Expression rowInPartition(Expression rowIndex) { // 方法重写：检查行是否在分区内
        return checkBounds(rowIndex, minX, maxX); // 使用分区的最小和最大索引检查
      }

      @Override public Expression compareRows(Expression a, Expression b) { // 方法重写：比较两行
        return Expressions.call(comparator_, // 调用比较器的compare方法
            BuiltInMethod.COMPARATOR_COMPARE.method, // 使用内置的COMPARATOR_COMPARE方法
            getRow(a), getRow(b)); // 获取两行并比较
      }

      public Expression getRow(Expression rowIndex) { // 方法：获取指定索引的行表达式
        return block.append("jRow", // 将行表达式添加到代码块中，命名为"jRow"
            EnumUtils.convert( // 转换表达式类型
                Expressions.arrayIndex(rows_, rowIndex), // 从行数组中获取指定索引的行
                inputPhysType.getJavaRowType())); // 转换为输入物理类型的Java行类型
      }

      @Override public Expression index() { // 方法重写：获取当前行索引
        return i_; // 返回i参数（当前分区内的行索引）
      }

      @Override public Expression startIndex() { // 方法重写：获取窗口起始位置
        return startX; // 返回startX表达式
      }

      @Override public Expression endIndex() { // 方法重写：获取窗口结束位置
        return endX; // 返回endX表达式
      }

      @Override public Expression hasRows() { // 方法重写：获取窗口有效性
        return hasRows; // 返回hasRows表达式
      }

      @Override public Expression getFrameRowCount() { // 方法重写：获取窗口行数
        return frameRowCount; // 返回frameRowCount表达式
      }

      @Override public Expression getPartitionRowCount() { // 方法重写：获取分区行数
        return partitionRowCount; // 返回partitionRowCount表达式
      }
    }; // 结束WinAggFrameResultContext匿名实现
  } // 结束getBlockBuilderWinAggFrameResultContextFunction方法

  private static Pair<Expression, Expression> getPartitionIterator( // 方法：获取分区迭代器，用于遍历每个分区的数据
      BlockBuilder builder, // 参数：代码块构建器
      Expression source_, // 参数：源数据表达式
      PhysType inputPhysType, // 参数：输入物理类型
      Group group, // 参数：窗口分组信息
      Expression comparator_) { // 参数：比较器表达式
    // Populate map of lists, one per partition
    //   final Map<Integer, List<Employee>> multiMap =
    //     new SortedMultiMap<Integer, List<Employee>>();
    //    source.foreach(
    //      new Function1<Employee, Void>() {
    //        public Void apply(Employee v) {
    //          final Integer k = v.deptno;
    //          multiMap.putMulti(k, v);
    //          return null;
    //        }
    //      });
    //   final List<Xxx> list = new ArrayList<Xxx>(multiMap.size());
    //   Iterator<Employee[]> iterator = multiMap.arrays(comparator);
    //
    // 注释：填充映射表，每个分区一个列表
    // 1. 创建排序多重映射，键为分区键，值为该分区的所有行
    // 2. 遍历源数据，将每行添加到对应分区的列表中
    // 3. 创建结果列表
    // 4. 获取迭代器，遍历每个分区的行数组
    if (group.keys.isEmpty()) { // 如果没有分区键（PARTITION BY为空）
      // If partition key is empty, no need to partition.
      //
      //   final List<Employee> tempList =
      //       source.into(new ArrayList<Employee>());
      //   Iterator<Employee[]> iterator =
      //       SortedMultiMap.singletonArrayIterator(comparator, tempList);
      //   final List<Xxx> list = new ArrayList<Xxx>(tempList.size());
      // 注释：如果分区键为空，不需要分区
      // 1. 将源数据转换为临时列表
      // 2. 创建单例数组迭代器（所有数据作为一个分区）
      // 3. 创建结果列表

      final Expression tempList_ = // 创建临时列表表达式
          builder.append("tempList", // 添加到代码块中，命名为"tempList"
              Expressions.convert_( // 转换表达式类型
                  Expressions.call(source_, // 调用源数据的into方法
                      BuiltInMethod.INTO.method, // 使用内置的INTO方法
                      Expressions.new_(ArrayList.class)), // 创建新的ArrayList实例
                  List.class), // 转换为List类型
              false); // 不优化内联
      return Pair.of(tempList_, // 返回键值对：临时列表和迭代器
          builder.append("iterator", // 添加迭代器到代码块中
              Expressions.call(null, // 调用静态方法
                  BuiltInMethod.SORTED_MULTI_MAP_SINGLETON.method, // 使用内置的SORTED_MULTI_MAP_SINGLETON方法
                  comparator_, // 传入比较器
                  tempList_))); // 传入临时列表
    }
    Expression multiMap_ = // 创建排序多重映射表达式
        builder.append("multiMap", Expressions.new_(SortedMultiMap.class)); // 添加到代码块中，创建SortedMultiMap实例
    final BlockBuilder builder2 = new BlockBuilder(); // 创建代码块构建器2，用于构建lambda表达式
    final ParameterExpression v_ = // 创建参数表达式，表示源数据中的每一行
        Expressions.parameter(inputPhysType.getJavaRowType(), // 类型为输入物理类型的Java行类型
            builder2.newName("v")); // 名称自动生成

    Pair<Type, List<Expression>> selector = // 创建键选择器，用于从行中提取分区键
        inputPhysType.selector(v_, group.keys.asList(), JavaRowFormat.CUSTOM); // 使用自定义格式生成选择器
    final ParameterExpression key_; // 声明键参数表达式
    if (selector.left instanceof Types.RecordType) { // 如果键是记录类型（多个分区键）
      Types.RecordType keyJavaType = (Types.RecordType) selector.left; // 获取键的Java记录类型
      List<Expression> initExpressions = selector.right; // 获取键字段的初始化表达式列表
      key_ = Expressions.parameter(keyJavaType, "key"); // 创建键参数表达式
      builder2.add(Expressions.declare(0, key_, null)); // 声明键变量，初始值为null
      builder2.add( // 创建键对象
          Expressions.statement(
              Expressions.assign(key_, Expressions.new_(keyJavaType)))); // key = new KeyType()
      List<Types.RecordField> fieldList = keyJavaType.getRecordFields(); // 获取记录字段列表
      for (int i = 0; i < initExpressions.size(); i++) { // 遍历所有字段
        Expression right = initExpressions.get(i); // 获取字段的初始化表达式
        builder2.add( // 设置字段值
            Expressions.statement(
                Expressions.assign(
                    Expressions.field(key_, fieldList.get(i)), right))); // key.field = value
      }
    } else { // 如果键是单个字段
      DeclarationStatement declare = // 声明键变量
          Expressions.declare(0, "key", selector.right.get(0)); // 使用选择器的第一个表达式初始化
      builder2.add(declare); // 添加声明到代码块
      key_ = declare.parameter; // 获取键参数表达式
    }
    builder2.add( // 将行添加到多重映射中
        Expressions.statement(
            Expressions.call(multiMap_, // 调用多重映射的putMulti方法
                BuiltInMethod.SORTED_MULTI_MAP_PUT_MULTI.method, key_, v_))); // 传入键和值
    builder2.add( // 返回null（lambda表达式需要返回值）
        Expressions.return_(null, Expressions.constant(null))); // return null

    builder.add( // 添加foreach语句，遍历源数据

            Expressions.statement(

                Expressions.call(source_, BuiltInMethod.ENUMERABLE_FOREACH.method, // 调用源数据的foreach方法



                    Expressions.lambda(builder2.toBlock(), v_)))); // 传入lambda表达式（builder2的代码块）



        return Pair.of(multiMap_, // 返回键值对：多重映射和迭代器

          builder.append("iterator", // 添加迭代器到代码块中

            Expressions.call(multiMap_, BuiltInMethod.SORTED_MULTI_MAP_ARRAYS.method, // 调用多重映射的arrays方法

                comparator_))); // 传入比较器用于排序

      } // 结束getPartitionIterator方法

  private static Pair<@Nullable Expression, @Nullable Expression> getRowCollationKey( // 方法：获取行的排序键，用于RANGE窗口类型
      BlockBuilder builder, PhysType inputPhysType, // 参数：代码块构建器和输入物理类型
      Group group, int windowIdx) { // 参数：窗口分组和窗口索引
    if (!(group.isRows // 如果不是ROWS窗口类型
        || (group.upperBound.isUnbounded() && group.lowerBound.isUnbounded()))) { // 且不是无界窗口
      Pair<Expression, Expression> pair = // 生成排序键选择器和比较器
          inputPhysType.generateCollationKey( // 调用物理类型的生成排序键方法
              group.collation().getFieldCollations()); // 传入排序字段列表
      // optimize=false to prevent inlining of object create into for-loops
      // 注释：optimize=false防止对象创建内联到for循环中
      return Pair.of( // 返回键值对
          builder.append("keySelector" + windowIdx, pair.left, false), // 添加键选择器，不优化内联
          builder.append("keyComparator" + windowIdx, pair.right, false)); // 添加键比较器，不优化内联
    } else { // 如果是ROWS窗口或无界窗口
      return Pair.of(null, null); // 返回null，不需要排序键
    }
  }

  private void declareAndResetState(final JavaTypeFactory typeFactory, // 方法：声明并重置聚合函数的状态变量
      BlockBuilder builder, final Result result, int windowIdx, // 参数：代码块构建器、子节点结果、窗口索引
      List<AggImpState> aggs, PhysType outputPhysType, // 参数：聚合状态列表、输出物理类型
      List<Expression> outputRow, RexWindowExclusion exclusion) { // 参数：输出行表达式列表、窗口排除规则
    for (final AggImpState agg : aggs) { // 遍历所有聚合状态
      agg.context = // 创建窗口聚合上下文
          new WinAggContext() { // 匿名实现WinAggContext接口
            @Override public SqlAggFunction aggregation() { // 方法重写：获取聚合函数
              return agg.call.getAggregation(); // 返回聚合调用的聚合函数
            }

            @Override public RelDataType returnRelType() { // 方法重写：获取返回的关系数据类型
              return agg.call.type; // 返回聚合调用的类型
            }

            @Override public Type returnType() { // 方法重写：获取返回的Java类型
              return EnumUtils.javaClass(typeFactory, returnRelType()); // 将关系类型转换为Java类
            }

            @Override public List<? extends Type> parameterTypes() { // 方法重写：获取参数的Java类型列表
              return EnumUtils.fieldTypes(typeFactory, // 调用工具方法获取字段类型
                  parameterRelTypes()); // 传入参数的关系类型
            }

            @Override public List<? extends RelDataType> parameterRelTypes() { // 方法重写：获取参数的关系数据类型列表
              return EnumUtils.fieldRowTypes(result.physType.getRowType(), // 调用工具方法获取字段类型
                  constants, agg.call.getArgList()); // 传入常量和参数索引列表
            }

            @Override public List<ImmutableBitSet> groupSets() { // 方法重写：获取分组集合
              throw new UnsupportedOperationException(); // 窗口聚合不支持分组集合
            }

            @Override public List<Integer> keyOrdinals() { // 方法重写：获取键序号
              throw new UnsupportedOperationException(); // 窗口聚合不支持键序号
            }

            @Override public List<? extends RelDataType> keyRelTypes() { // 方法重写：获取键的关系类型
              throw new UnsupportedOperationException(); // 窗口聚合不支持键类型
            }

            @Override public List<? extends Type> keyTypes() { // 方法重写：获取键的Java类型
              throw new UnsupportedOperationException(); // 窗口聚合不支持键类型
            }

            @Override public RexWindowExclusion getExclude() { // 方法重写：获取窗口排除规则
              return exclusion; // 返回排除规则
            }
          }; // 结束WinAggContext匿名实现
      String aggName = "a" + agg.aggIdx; // 生成聚合函数的名称（如"a0", "a1"等）
      if (CalciteSystemProperty.DEBUG.value()) { // 如果开启了调试模式
        aggName = Util.toJavaId(agg.call.getAggregation().getName(), 0) // 使用聚合函数的实际名称
            .substring("ID$0$".length()) + aggName; // 去掉前缀并添加索引
      }
      List<Type> state = agg.implementor.getStateType(agg.context); // 获取聚合函数的状态类型列表
      final List<Expression> decls = new ArrayList<>(state.size()); // 创建状态变量声明列表
      for (int i = 0; i < state.size(); i++) { // 遍历所有状态类型
        Type type = state.get(i); // 获取当前状态类型
        ParameterExpression pe = // 创建状态变量参数表达式
            Expressions.parameter(type, // 类型为当前状态类型
                builder.newName(aggName // 名称格式：聚合名 + "s" + 状态索引 + "w" + 窗口索引
                    + "s" + i + "w" + windowIdx));
        builder.add(Expressions.declare(0, pe, null)); // 声明状态变量，初始值为null
        decls.add(pe); // 添加到声明列表
      }
      agg.state = decls; // 保存状态变量列表
      Type aggHolderType = agg.context.returnType(); // 获取聚合函数的返回类型
      Type aggStorageType = // 获取输出行中的存储类型
          outputPhysType.getJavaFieldType(outputRow.size()); // 根据输出行的大小获取字段类型
      if (Primitive.is(aggHolderType) && !Primitive.is(aggStorageType)) { // 如果返回类型是基本类型但存储类型不是
        aggHolderType = Primitive.box(aggHolderType); // 将基本类型装箱为包装类型
      }
      ParameterExpression aggRes = // 创建聚合结果变量
          Expressions.parameter(0, aggHolderType, // 类型为可能的装箱类型
              builder.newName(aggName + "w" + windowIdx)); // 名称格式：聚合名 + "w" + 窗口索引

      builder.add( // 声明聚合结果变量
          Expressions.declare(0, aggRes, // 声明变量
              Expressions.constant( // 初始值为类型的默认值
                  Optional.ofNullable(Primitive.of(aggRes.getType())) // 如果是基本类型
                      .map(x -> x.defaultValue) // 获取默认值
                      .orElse(null), // 否则为null
                  aggRes.getType()))); // 指定类型
      agg.result = aggRes; // 保存结果变量
      outputRow.add(aggRes); // 将结果变量添加到输出行中
      agg.implementor.implementReset(agg.context, // 调用聚合实现器的重置方法
          new WinAggResetContextImpl(builder, agg.state, // 创建窗口聚合重置上下文实现
              castNonNull(null), castNonNull(null), castNonNull(null), castNonNull(null), // 传入null参数（初始重置）
              castNonNull(null), castNonNull(null))); // 传入null参数（初始重置）
    }
  } // 结束declareAndResetState方法

  private static void implementAdd(List<AggImpState> aggs, // 方法：实现聚合函数的添加操作
      final BlockBuilder builder7, // 参数：代码块构建器7（窗口内行遍历代码块）
      final Function<BlockBuilder, WinAggFrameResultContext> frame, // 参数：窗口帧结果上下文构建函数
      final Function<AggImpState, List<RexNode>> rexArguments, // 参数：Rex参数构建函数
      final DeclarationStatement jDecl) { // 参数：窗口内行索引的声明
    for (final AggImpState agg : aggs) { // 遍历所有聚合状态
      final WinAggAddContext addContext = // 创建窗口聚合添加上下文
          new WinAggAddContextImpl(builder7, requireNonNull(agg.state, "agg.state"), frame) { // 匿名实现WinAggAddContext接口
            @Override public Expression currentPosition() { // 方法重写：获取当前处理的位置
              return jDecl.parameter; // 返回j参数（窗口内行索引）
            }

            @Override public List<RexNode> rexArguments() { // 方法重写：获取Rex参数列表
              return rexArguments.apply(agg); // 调用Rex参数构建函数
            }

            @Override public @Nullable RexNode rexFilterArgument() { // 方法重写：获取Rex过滤参数
              return null; // REVIEW: 当前实现不支持过滤参数
            }
          };
      agg.implementor.implementAdd(requireNonNull(agg.context, "agg.context"), addContext); // 调用聚合实现器的添加方法
    }
  }

  private static boolean implementResult(List<AggImpState> aggs, // 方法：实现聚合函数的结果获取
      final BlockBuilder builder, // 参数：代码块构建器
      final Function<BlockBuilder, WinAggFrameResultContext> frame, // 参数：窗口帧结果上下文构建函数
      final Function<AggImpState, List<RexNode>> rexArguments, // 参数：Rex参数构建函数
      boolean cachedBlock) { // 参数：是否为缓存版本（true表示只在窗口变化时执行）
    boolean nonEmpty = false; // 标记是否有非空的实现代码
    for (final AggImpState agg : aggs) { // 遍历所有聚合状态
      boolean needCache = true; // 默认需要缓存
      if (agg.implementor instanceof WinAggImplementor) { // 如果实现器是窗口聚合实现器
        WinAggImplementor imp = (WinAggImplementor) agg.implementor; // 强制转换为窗口聚合实现器
        needCache = imp.needCacheWhenFrameIntact(); // 调用方法判断是否需要缓存
      }
      if (needCache ^ cachedBlock) { // 如果needCache和cachedBlock不同（异或）
        // Regular aggregates do not change when the windowing frame keeps
        // the same. Ths
        // 注释：常规聚合在窗口帧保持不变时不会改变
        continue; // 跳过当前聚合
      }
      nonEmpty = true; // 标记为有非空实现
      Expression res = // 获取聚合结果表达式
          agg.implementor.implementResult( // 调用聚合实现器的结果方法
              requireNonNull(agg.context, "agg.context"), // 传入聚合上下文
              new WinAggResultContextImpl(builder, // 创建窗口聚合结果上下文实现
                  requireNonNull(agg.state, "agg.state"), frame) { // 匿名实现WinAggResultContext接口
                @Override public List<RexNode> rexArguments() { // 方法重写：获取Rex参数列表
                  return rexArguments.apply(agg); // 调用Rex参数构建函数
                }
              });
      // Several count(a) and count(b) might share the result
      // 注释：多个count(a)和count(b)可能共享结果
      Expression result = // 获取结果变量
          requireNonNull(agg.result, () -> "agg.result for " + agg.call); // 非空检查
      Expression aggRes = // 转换结果类型
          builder.append("a" + agg.aggIdx + "res", // 添加到代码块中，命名为"a0res", "a1res"等
              EnumUtils.convert(res, result.getType())); // 将结果转换为结果变量的类型
      builder.add(Expressions.statement(Expressions.assign(result, aggRes))); // 赋值：result = aggRes
    }
    return nonEmpty; // 返回是否有非空实现
  }

  private static Expression translateBound(RexToLixTranslator translator, // 方法：翻译窗口边界表达式，将Rex窗口边界转换为Java表达式
      ParameterExpression i_, Expression row_, Expression min_, Expression max_, // 参数：当前行索引、当前行、最小索引、最大索引
      Expression rows_, Group group, boolean lower, PhysType physType, // 参数：行数组、窗口分组、是否为下边界、物理类型
      @Nullable Expression keySelector, @Nullable Expression keyComparator) { // 参数：键选择器、键比较器（用于RANGE窗口）
    RexWindowBound bound = lower ? group.lowerBound : group.upperBound; // 根据lower参数获取下边界或上边界
    if (bound.isUnbounded()) { // 如果边界是无界的
      return bound.isPreceding() ? min_ : max_; // 如果是UNBOUNDED PRECEDING返回最小值，否则返回最大值
    }
    if (group.isRows) { // 如果是ROWS窗口类型（基于行数）
      if (bound.isCurrentRow()) { // 如果边界是CURRENT ROW
        return i_; // 返回当前行索引
      }
      RexNode node = bound.getOffset(); // 获取偏移量Rex节点
      Expression offs = translator.translate(node); // 将偏移量翻译为Java表达式
      // Floating offset does not make sense since we refer to array index.
      // Nulls do not make sense as well.
      // 注释：浮点偏移量没有意义，因为我们引用数组索引。空值也没有意义。
      offs = EnumUtils.convert(offs, int.class); // 将偏移量转换为int类型

      Expression b = i_; // 从当前行开始
      if (bound.isFollowing()) { // 如果是FOLLOWING（向前）
        b = Expressions.add(b, offs); // 当前行 + 偏移量
      } else { // 如果是PRECEDING（向后）
        b = Expressions.subtract(b, offs); // 当前行 - 偏移量
      }
      return b; // 返回计算后的边界表达式
    }
    Expression searchLower = min_; // 初始化搜索下界为最小索引
    Expression searchUpper = max_; // 初始化搜索上界为最大索引
    if (bound.isCurrentRow()) { // 如果边界是CURRENT ROW
      if (lower) { // 如果是下边界
        searchUpper = i_; // 搜索上界为当前行（查找从最小值到当前行的第一个）
      } else { // 如果是上边界
        searchLower = i_; // 搜索下界为当前行（查找从当前行到最大值的第一个）
      }
    }

    List<RelFieldCollation> fieldCollations = // 获取排序字段列表
        group.collation().getFieldCollations();
    if (bound.isCurrentRow() && fieldCollations.size() != 1) { // 如果边界是CURRENT ROW且有多个排序字段
      return Expressions.call( // 使用二分查找5版本（比较整个行）
          (lower // 根据是否为下边界选择方法
              ? BuiltInMethod.BINARY_SEARCH5_LOWER // 下边界查找
              : BuiltInMethod.BINARY_SEARCH5_UPPER).method, // 上边界查找
          rows_, row_, searchLower, searchUpper, // 传入行数组、当前行、搜索范围
          requireNonNull(keySelector, "keySelector"), // 传入键选择器
          requireNonNull(keyComparator, "keyComparator")); // 传入键比较器
    }
    assert fieldCollations.size() == 1 // 断言：使用RANGE窗口时，ORDER BY应该只有一个表达式
        : "When using range window specification, ORDER BY should have"
        + " exactly one expression."
        + " Actual collation is " + group.collation(); // 否则抛出断言错误
    // isRange
    int orderKey = // 获取排序键的字段索引
        fieldCollations.get(0).getFieldIndex(); // 获取第一个排序字段的索引
    RelDataType keyType = // 获取排序键的数据类型
        physType.getRowType().getFieldList().get(orderKey).getType(); // 从行类型中获取字段类型
    Type desiredKeyType = translator.typeFactory.getJavaClass(keyType); // 获取排序键的Java类类型
    if (bound.getOffset() == null) { // 如果偏移量为null（CURRENT ROW）
      desiredKeyType = Primitive.box(desiredKeyType); // 将基本类型装箱为包装类型
    }
    Expression val = // 创建值表达式（用于二分查找）
        translator.translate(new RexInputRef(orderKey, keyType), desiredKeyType); // 将Rex输入引用翻译为Java表达式
    if (!bound.isCurrentRow()) { // 如果边界不是CURRENT ROW
      RexNode node = bound.getOffset(); // 获取偏移量Rex节点
      Expression offs = translator.translate(node); // 将偏移量翻译为Java表达式
      // TODO: support date + interval somehow
      // 注释：TODO：需要支持日期+间隔的运算
      if (bound.isFollowing()) { // 如果是FOLLOWING
        val = Expressions.add(val, offs); // 值 + 偏移量
      } else { // 如果是PRECEDING
        val = Expressions.subtract(val, offs); // 值 - 偏移量
      }
    }
    return Expressions.call( // 使用二分查找6版本（比较单个键值）
        (lower // 根据是否为下边界选择方法
            ? BuiltInMethod.BINARY_SEARCH6_LOWER // 下边界查找
            : BuiltInMethod.BINARY_SEARCH6_UPPER).method, // 上边界查找
        rows_, val, searchLower, searchUpper, // 传入行数组、值、搜索范围
        requireNonNull(keySelector, "keySelector"), // 传入键选择器
        requireNonNull(keyComparator, "keyComparator")); // 传入键比较器
  }
} // 结束EnumerableWindow类
