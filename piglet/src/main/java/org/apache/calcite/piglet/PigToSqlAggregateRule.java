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
package org.apache.calcite.piglet; // 包声明，该类属于org.apache.calcite.piglet包，用于Piglet相关功能

import org.apache.calcite.plan.RelOptRuleCall; // 导入RelOptRuleCall类，用于规则调用时的上下文信息
import org.apache.calcite.plan.RelRule; // 导入RelRule基类，Calcite优化规则的抽象基类
import org.apache.calcite.rel.core.Aggregate; // 导入Aggregate关系节点，表示聚合操作
import org.apache.calcite.rel.core.Project; // 导入Project关系节点，表示投影操作
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField类，表示关系数据类型中的字段
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder类，用于构建行表达式(RexNode)
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示函数调用表达式
import org.apache.calcite.rex.RexFieldAccess; // 导入RexFieldAccess类，表示字段访问表达式
import org.apache.calcite.rex.RexInputRef; // 导入RexInputRef类，表示输入字段引用表达式
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类，表示字面量表达式
import org.apache.calcite.rex.RexNode; // 导入RexNode类，行表达式的基类
import org.apache.calcite.rex.RexShuttle; // 导入RexShuttle类，用于遍历和修改表达式树
import org.apache.calcite.rex.RexVisitorImpl; // 导入RexVisitorImpl类，表达式访问者的基础实现
import org.apache.calcite.sql.SqlAggFunction; // 导入SqlAggFunction接口，表示SQL聚合函数
import org.apache.calcite.sql.SqlKind; // 导入SqlKind枚举，定义SQL操作的类型
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类，包含标准SQL操作符
import org.apache.calcite.tools.RelBuilder; // 导入RelBuilder类，用于构建关系表达式树

import org.immutables.value.Value; // 导入Immutables注解，用于生成不可变值对象

import java.math.BigDecimal; // 导入BigDecimal类，用于高精度数值计算
import java.util.ArrayList; // 导入ArrayList类，动态数组实现
import java.util.HashMap; // 导入HashMap类，哈希表实现
import java.util.List; // 导入List接口，列表集合
import java.util.Map; // 导入Map接口，键值对集合

import static org.apache.calcite.piglet.PigTypes.TYPE_FACTORY; // 静态导入PigTypes的类型工厂

/**
 * Planner rule that converts Pig aggregate UDF calls to built-in SQL
 * aggregates.
 * // 优化器规则，用于将Pig聚合UDF调用转换为内置SQL聚合函数
 *
 * <p>This rule is applied for logical relational algebra plan that is
 * the result of Pig translation. In Pig, aggregate calls are separate
 * from grouping where we create a bag of all tuples in each group
 * first then apply the Pig aggregate UDF later.  It is inefficient to
 * do that in SQL.
 * // 该规则应用于Pig转换后的逻辑关系代数计划。在Pig中，聚合调用与分组是分离的，
 * // 我们先为每个组创建一个包含所有元组的包(bag)，然后再应用Pig聚合UDF。
 * // 在SQL中这样做效率很低。
 */
@Value.Enclosing // Immutables注解，标记包含嵌套不可变类的类
public class PigToSqlAggregateRule // 类定义：Pig到SQL聚合转换规则
    extends RelRule<PigToSqlAggregateRule.Config> { // 继承自RelRule基类，配置类型为PigToSqlAggregateRule.Config
  private static final String MULTISET_PROJECTION = "MULTISET_PROJECTION"; // 常量：MULTISET投影操作的名称标识符

  public static final PigToSqlAggregateRule INSTANCE = // 静态常量：该规则的单例实例
      ImmutablePigToSqlAggregateRule.Config.builder() // 使用Immutables构建器创建配置
          .withOperandSupplier(b0 -> // 设置操作数提供器，定义规则的匹配模式
              b0.operand(Project.class).oneInput(b1 -> // 最外层是Project节点
                  b1.operand(Project.class).oneInput(b2 -> // 第二层是Project节点
                      b2.operand(Aggregate.class).oneInput(b3 -> // 第三层是Aggregate节点
                          b3.operand(Project.class).anyInputs())))) // 最内层是Project节点，可以有任意输入
          .build() // 构建配置对象
          .toRule(); // 将配置转换为规则实例

  /** Creates a PigToSqlAggregateRule. */
  // 创建PigToSqlAggregateRule实例的构造函数
  protected PigToSqlAggregateRule(Config config) { // 受保护的构造函数，接收配置对象
    super(config); // 调用父类RelRule的构造函数，初始化规则配置
  }

  /**
   * Visitor that finds all Pig aggregate UDFs or multiset
   * projection called in an expression and also whether a column is
   * referred in that expression.
   * // 访问者类，用于在表达式中查找所有Pig聚合UDF或multiset投影调用，
   * // 并检测某列是否在该表达式被引用
   */
  private static class PigAggUdfFinder extends RexVisitorImpl<Void> { // 内部类：Pig聚合UDF查找器，继承自RexVisitorImpl
    // Index of the column
    private final int projectCol; // 成员变量：要检测的投影列的索引
    // List of all Pig aggregate UDFs found in the expression
    private final List<RexCall> pigAggCalls; // 成员变量：在表达式中找到的所有Pig聚合UDF调用列表
    // True iff the column is referred in the expression
    private boolean projectColReferred; // 成员变量：标志位，表示该列是否在表达式被引用
    // True to ignore multiset projection inside a PigUDF
    private boolean ignoreMultisetProject = false; // 成员变量：标志位，是否忽略PigUDF内部的multiset投影

    PigAggUdfFinder(int projectCol) { // 构造函数：接收要检测的列索引
      super(true); // 调用父类构造函数，参数true表示深入遍历
      this.projectCol = projectCol; // 初始化要检测的列索引
      pigAggCalls = new ArrayList<>(); // 初始化Pig聚合UDF调用列表
      projectColReferred = false; // 初始化列引用标志为false
    }

    @Override public Void visitCall(RexCall call) { // 重写visitCall方法：访问函数调用节点
      if (PigRelUdfConverter.getSqlAggFuncForPigUdf(call) != null) { // 检查该调用是否是Pig UDF
        pigAggCalls.add(call); // 如果是Pig UDF，添加到调用列表
        ignoreMultisetProject = true; // 设置忽略multiset投影标志，避免重复处理
      } else if (isMultisetProjection(call) && !ignoreMultisetProject) { // 检查是否是multiset投影且未被忽略
        pigAggCalls.add(call); // 如果是multiset投影，添加到调用列表
      }
      visitEach(call.operands); // 递归访问所有操作数
      return null; // 返回null，因为返回类型是Void
    }

    @Override public Void visitInputRef(RexInputRef inputRef) { // 重写visitInputRef方法：访问输入引用节点
      if (inputRef.getIndex() == projectCol) { // 检查输入引用的索引是否匹配目标列
        projectColReferred = true; // 如果匹配，设置列引用标志为true
      }
      return null; // 返回null
    }
  }

  /**
   * Helper class to replace each {@link RexCall} by a corresponding
   * {@link RexNode}, defined in a given map, for an expression.
   *
   * <p>It also replaces a projection by a new projection.
   * // 辅助类，用于将表达式中的每个RexCall替换为给定映射中对应的RexNode。
   * // 同时也支持将投影替换为新的投影。
   */
  private static class RexCallReplacer extends RexShuttle { // 内部类：表达式调用替换器，继承自RexShuttle
    private final Map<RexNode, RexNode> replacementMap; // 成员变量：从旧RexNode到新RexNode的替换映射
    private final RexBuilder builder; // 成员变量：用于构建新表达式的RexBuilder
    private final int oldProjectCol; // 成员变量：旧的投影列索引
    private final RexNode newProjectCol; // 成员变量：新的投影列表达式

    RexCallReplacer(RexBuilder builder, Map<RexNode, RexNode> replacementMap, // 构造函数：接收RexBuilder、替换映射和列信息
        int oldProjectCol, RexNode newProjectCol) { // 参数：旧的列索引和新的列表达式
      this.replacementMap = replacementMap; // 初始化替换映射
      this.builder = builder; // 初始化RexBuilder
      this.oldProjectCol = oldProjectCol; // 初始化旧列索引
      this.newProjectCol = newProjectCol; // 初始化新列表达式
    }

    RexCallReplacer(RexBuilder builder, Map<RexNode, RexNode> replacementMap) { // 简化构造函数：只接收RexBuilder和替换映射
      this(builder, replacementMap, -1, null); // 调用完整构造函数，列参数设为默认值
    }

    @Override public RexNode visitCall(RexCall call) { // 重写visitCall方法：访问并可能替换函数调用
      if (replacementMap.containsKey(call)) { // 检查该调用是否在替换映射中
        return replacementMap.get(call); // 如果存在，返回映射中的替换节点
      }

      List<RexNode> newOperands = new ArrayList<>(); // 创建新的操作数列表
      for (RexNode operand : call.operands) { // 遍历原调用的所有操作数
        if (replacementMap.containsKey(operand)) { // 检查操作数是否在替换映射中
          newOperands.add(replacementMap.get(operand)); // 如果存在，添加替换后的操作数
        } else {
          newOperands.add(operand.accept(this)); // 否则，递归处理该操作数
        }
      }
      return builder.makeCall(call.type, call.op, newOperands); // 使用新操作数重建调用表达式
    }

    @Override public RexNode visitInputRef(RexInputRef inputRef) { // 重写visitInputRef方法：访问并可能替换输入引用
      if (inputRef.getIndex() == oldProjectCol // 检查索引是否匹配旧列
          && newProjectCol != null // 检查新列表达式是否存在
          && inputRef.getType() == newProjectCol.getType()) { // 检查类型是否匹配
        return newProjectCol; // 如果条件满足，返回新的列表达式
      }
      return inputRef; // 否则，返回原输入引用
    }
  }

  @Override public void onMatch(RelOptRuleCall call) { // 重写onMatch方法：当规则匹配时执行
    final Project oldTopProject = call.rel(0); // 获取最外层的Project节点（索引0）
    final Project oldMiddleProject = call.rel(1); // 获取中间层的Project节点（索引1）
    final Aggregate oldAgg = call.rel(2); // 获取Aggregate节点（索引2）
    final Project oldBottomProject = call.rel(3); // 获取最内层的Project节点（索引3）
    final RelBuilder relBuilder = call.builder(); // 获取RelBuilder实例用于构建新的关系表达式

    if (oldAgg.getAggCallList().size() != 1 // 检查聚合调用列表大小是否为1
        || oldAgg.getAggCallList().get(0).getAggregation().getKind() != SqlKind.COLLECT) { // 检查聚合类型是否为COLLECT
      // Prevent the rule to be re-applied. Nothing to do here
      // 防止规则被重复应用，这里不做任何处理直接返回
      return; // 直接返回，不进行转换
    }

    // Step 0: Find all target Pig aggregate UDFs to rewrite
    // 步骤0：查找所有需要重写的目标Pig聚合UDF
    final List<RexCall> pigAggUdfs = new ArrayList<>(); // 创建列表存储所有Pig聚合UDF
    // Whether we need to keep the grouping aggregate call in the new aggregate
    // 标志位：是否需要在新的聚合中保留分组聚合调用
    boolean needGroupingCol = false; // 初始化为false
    for (RexNode rex : oldTopProject.getProjects()) { // 遍历顶层Project的所有投影表达式
      PigAggUdfFinder udfVisitor = new PigAggUdfFinder(1); // 创建UDF查找器，检测列索引1
      rex.accept(udfVisitor); // 让表达式接受访问者，查找Pig UDF
      if (!udfVisitor.pigAggCalls.isEmpty()) { // 如果找到Pig聚合调用
        for (RexCall pigAgg : udfVisitor.pigAggCalls) { // 遍历所有找到的Pig聚合调用
          if (!pigAggUdfs.contains(pigAgg)) { // 检查是否已存在
            pigAggUdfs.add(pigAgg); // 如果不存在，添加到列表
          }
        }
      } else if (udfVisitor.projectColReferred) { // 如果没有Pig UDF但引用了目标列
        needGroupingCol = true; // 设置需要保留分组列的标志
      }
    }


    // Step 1 Build new bottom project
    // 步骤1：构建新的底部Project节点
    final List<RexNode> newBottomProjects = new ArrayList<>(); // 创建新底部投影的表达式列表
    relBuilder.push(oldBottomProject.getInput()); // 将旧底部Project的输入推入RelBuilder栈
    // First project all group keys, just copy from old one
    // 首先投影所有分组键，直接从旧的复制
    for (int i = 0; i < oldAgg.getGroupCount(); i++) { // 遍历所有分组键
      newBottomProjects.add(oldBottomProject.getProjects().get(i)); // 添加旧的分组键投影
    }
    // If grouping aggregate is needed, project the whole ROW
    // 如果需要分组聚合，投影整个ROW
    if (needGroupingCol) { // 检查是否需要分组列
      final RexNode row = // 创建ROW表达式
          relBuilder.getRexBuilder().makeCall(relBuilder.peek().getRowType(), // 使用当前行类型
              SqlStdOperatorTable.ROW, relBuilder.fields()); // 使用ROW操作符和所有字段
      newBottomProjects.add(row); // 将ROW表达式添加到投影列表
    }
    final int groupCount = oldAgg.getGroupCount() + (needGroupingCol ? 1 : 0); // 计算新的分组键数量

    // Now figure out which columns need to be projected for Pig UDF aggregate calls
    // We need to project these columns for the new aggregate
    // 现在确定Pig UDF聚合调用需要投影哪些列，我们需要为新的聚合投影这些列

    // This is a map from old index to new index
    // 这是旧索引到新索引的映射
    final Map<Integer, Integer> projectedAggColumns = new HashMap<>(); // 创建旧列索引到新列索引的映射
    for (int i = 0; i < newBottomProjects.size(); i++) { // 遍历新的投影列表
      if (newBottomProjects.get(i) instanceof RexInputRef) { // 检查是否是输入引用
        projectedAggColumns.put(((RexInputRef) newBottomProjects.get(i)).getIndex(), i); // 建立映射关系
      }
    }
    // Build a map of each agg call to a list of columns in the new projection for later use
    // 构建每个聚合调用到新投影中列列表的映射，供后续使用
    final Map<RexCall, List<Integer>> aggCallColumns = new HashMap<>(); // 创建聚合调用到列索引列表的映射
    for (RexCall rexCall : pigAggUdfs) { // 遍历所有Pig聚合UDF
      // Get columns in old projection required for the agg call
      // 获取聚合调用所需的旧投影中的列
      final List<Integer> requiredColumns = getAggColumns(rexCall); // 调用getAggColumns方法获取所需列
      // And map it to columns of new projection
      // 并将其映射到新投影的列
      final List<Integer> newColIndexes = new ArrayList<>(); // 创建新列索引列表
      for (int col : requiredColumns) { // 遍历所需列
        Integer newCol = projectedAggColumns.get(col); // 查找该列是否已投影
        if (newCol != null) { // 如果已经投影过
          // The column has been projected before
          // 该列之前已经投影过
          newColIndexes.add(newCol); // 直接添加已存在的索引
        } else {
          // Add it to the projection list if we never project it before
          // 如果之前从未投影过，添加到投影列表
          // First get the ROW operator call
          // 首先获取ROW操作符调用
          final RexCall rowCall = (RexCall) oldBottomProject.getProjects() // 获取ROW调用
              .get(oldAgg.getGroupCount()); // 从分组键位置获取
          // Get the corresponding column index in parent rel through the call operand list
          // 通过调用操作数列表获取父关系中对应的列索引
          final RexInputRef columnRef = (RexInputRef) rowCall.getOperands().get(col); // 获取列引用
          final int newIndex = newBottomProjects.size(); // 新索引为当前列表大小
          newBottomProjects.add(columnRef); // 将列引用添加到投影列表
          projectedAggColumns.put(columnRef.getIndex(), newIndex); // 建立新的映射关系
          newColIndexes.add(newCol); // 添加新索引到列表

        }
      }
      aggCallColumns.put(rexCall, newColIndexes); // 将聚合调用映射到列索引列表
    }
    // Now do the projection
    // 现在执行投影操作
    relBuilder.project(newBottomProjects); // 使用RelBuilder创建新的Project节点

    // Step 2 build new Aggregate
    // 步骤2：构建新的Aggregate节点
    // Copy the group key
    // 复制分组键
    final RelBuilder.GroupKey groupKey = // 创建分组键
        relBuilder.groupKey(oldAgg.getGroupSet(), oldAgg.groupSets); // 使用旧的分组设置
    // The construct the agg call list
    // 构建聚合调用列表
    final List<RelBuilder.AggCall> aggCalls = new ArrayList<>(); // 创建聚合调用列表
    if (needGroupingCol) { // 如果需要分组列
      aggCalls.add( // 添加COLLECT聚合调用
          relBuilder.aggregateCall(SqlStdOperatorTable.COLLECT, // 使用COLLECT操作符
              relBuilder.field(groupCount - 1))); // 对最后一个分组字段进行COLLECT
    }
    for (RexCall rexCall : pigAggUdfs) { // 遍历所有Pig聚合UDF
      final List<RexNode> aggOperands = new ArrayList<>(); // 创建聚合操作数列表
      for (int i : aggCallColumns.get(rexCall)) { // 遍历该聚合调用所需的列索引
        aggOperands.add(relBuilder.field(i)); // 添加字段引用到操作数列表
      }
      if (isMultisetProjection(rexCall)) { // 检查是否是multiset投影
        if (aggOperands.size() == 1) { // 如果只有一个操作数
          // Project single column
          // 投影单列
          aggCalls.add( // 添加COLLECT聚合调用
              relBuilder.aggregateCall(SqlStdOperatorTable.COLLECT, // 使用COLLECT操作符
                  aggOperands)); // 传入操作数列表
        } else {
          // Project more than one column, need to construct a record (ROW)
          // from them
          // 投影多列，需要从它们构造一个记录(ROW)
          final RelDataType rowType = // 创建记录类型
              createRecordType(relBuilder, aggCallColumns.get(rexCall)); // 调用createRecordType方法
          final RexNode row = relBuilder.getRexBuilder() // 创建ROW表达式
              .makeCall(rowType, SqlStdOperatorTable.ROW, aggOperands); // 使用ROW操作符和操作数
          aggCalls.add( // 添加COLLECT聚合调用
              relBuilder.aggregateCall(SqlStdOperatorTable.COLLECT, row)); // 对ROW表达式进行COLLECT
        }
      } else {
        final SqlAggFunction udf = // 获取Pig UDF对应的SQL聚合函数
            PigRelUdfConverter.getSqlAggFuncForPigUdf(rexCall); // 调用转换方法
        aggCalls.add(relBuilder.aggregateCall(udf, aggOperands)); // 添加SQL聚合调用
      }
    }
    relBuilder.aggregate(groupKey, aggCalls); // 使用RelBuilder创建Aggregate节点

    // Step 3 build new top projection
    // 步骤3：构建新的顶部Project节点
    final RelDataType aggType = relBuilder.peek().getRowType(); // 获取聚合节点的行类型
    // First construct a map from old Pig agg UDF call to a projection
    // on new aggregate.
    // 首先构建从旧Pig聚合UDF调用到新聚合上投影的映射
    final Map<RexNode, RexNode> pigCallToNewProjections = new HashMap<>(); // 创建Pig调用到新投影的映射
    for (int i = 0; i < pigAggUdfs.size(); i++) { // 遍历所有Pig聚合UDF
      final RexCall pigAgg = pigAggUdfs.get(i); // 获取当前Pig聚合调用
      final int colIndex = i + groupCount; // 计算在新聚合中的列索引
      final RelDataType fieldType = aggType.getFieldList().get(colIndex).getType(); // 获取新字段的类型
      final RelDataType oldFieldType = pigAgg.getType(); // 获取旧字段的类型
      // If the data type is different, we need to do a type CAST
      // 如果数据类型不同，我们需要进行类型转换
      if (fieldType.equals(oldFieldType)) { // 检查类型是否相同
        pigCallToNewProjections.put(pigAgg, relBuilder.field(colIndex)); // 如果相同，直接映射字段引用
      } else {
        pigCallToNewProjections.put(pigAgg, // 如果不同，创建类型转换表达式
            relBuilder.getRexBuilder().makeCast(oldFieldType, // 转换为旧类型
                relBuilder.field(colIndex))); // 转换新字段
      }
    }
    // Now build all expression for the new top project
    // 现在构建新顶部Project的所有表达式
    final List<RexNode> newTopProjects = new ArrayList<>(); // 创建新顶部投影的表达式列表
    final List<RexNode> oldUpperProjects = oldTopProject.getProjects(); // 获取旧顶部Project的所有投影
    for (RexNode rexNode : oldUpperProjects) { // 遍历所有旧投影表达式
      int groupRefIndex = getGroupRefIndex(rexNode); // 获取分组引用索引
      if (groupRefIndex >= 0) { // 如果是分组字段引用
        // project a field of the group
        // 投影分组的一个字段
        newTopProjects.add(relBuilder.field(groupRefIndex)); // 添加字段引用
      } else if (rexNode instanceof RexInputRef && ((RexInputRef) rexNode).getIndex() == 0) { // 如果是整个分组的引用
        // project the whole group (as a record)
        // 投影整个分组（作为记录）
        newTopProjects.add(oldMiddleProject.getProjects().get(0)); // 添加中间Project的第一个投影
      } else {
        // aggregate functions
        // 聚合函数
        RexCallReplacer replacer = // 创建表达式替换器
            needGroupingCol ? new RexCallReplacer( // 如果需要分组列
                relBuilder.getRexBuilder(), // 传入RexBuilder
                pigCallToNewProjections, // 传入替换映射
                1, // 旧列索引为1
                relBuilder.field(groupCount - 1)) // 新列表达式
                : new RexCallReplacer(relBuilder.getRexBuilder(), pigCallToNewProjections); // 否则创建简化版本
        newTopProjects.add(rexNode.accept(replacer)); // 让表达式接受替换器，添加替换后的结果
      }
    }
    // Finally make the top projection
    // 最后创建顶部投影
    relBuilder.project(newTopProjects, oldTopProject.getRowType().getFieldNames()); // 使用RelBuilder创建Project节点，保留旧字段名

    call.transformTo(relBuilder.build()); // 转换关系表达式为新的计划
  }

  private static RelDataType createRecordType(RelBuilder relBuilder, List<Integer> fields) { // 方法：创建记录类型，用于多列投影
    final List<String> destNames = new ArrayList<>(); // 创建目标字段名列表
    final List<RelDataType> destTypes = new ArrayList<>(); // 创建目标字段类型列表
    final List<RelDataTypeField> fieldList = // 获取当前关系的字段列表
        relBuilder.peek().getRowType().getFieldList(); // 从RelBuilder栈顶获取
    for (Integer index : fields) { // 遍历所有字段索引
      final RelDataTypeField field = fieldList.get(index); // 获取对应索引的字段
      destNames.add(field.getName()); // 添加字段名
      destTypes.add(field.getType()); // 添加字段类型
    }
    return TYPE_FACTORY.createStructType(destTypes, destNames); // 使用类型工厂创建结构化类型
  }

  private static int getGroupRefIndex(RexNode rex) { // 方法：获取分组引用索引，用于检测表达式是否引用分组字段
    if (rex instanceof RexFieldAccess) { // 检查是否是字段访问表达式
      final RexFieldAccess fieldAccess = (RexFieldAccess) rex; // 转换为字段访问
      if (fieldAccess.getReferenceExpr() instanceof RexInputRef) { // 检查引用表达式是否是输入引用
        final RexInputRef inputRef = (RexInputRef) fieldAccess.getReferenceExpr(); // 转换为输入引用
        if (inputRef.getIndex() == 0) { // 检查索引是否为0（分组列）
          // Project from 'group' column
          // 从'group'列投影
          return fieldAccess.getField().getIndex(); // 返回字段索引
        }
      }
    }
    return -1; // 如果不是分组引用，返回-1
  }

  /**
   * Returns a list of columns accessed in a Pig aggregate UDF call.
   *
   * @param pigAggCall Pig aggregate UDF call
   * // 返回Pig聚合UDF调用中访问的列列表
   */
  private static List<Integer> getAggColumns(RexCall pigAggCall) { // 方法：获取Pig聚合调用所需的列
    if (isMultisetProjection(pigAggCall)) { // 检查是否是multiset投影
      return getColsFromMultisetProjection(pigAggCall); // 返回multiset投影的列
    }

    // The only operand should be PIG_BAG
    // 唯一的操作数应该是PIG_BAG
    assert pigAggCall.getOperands().size() == 1 // 断言只有一个操作数
        && pigAggCall.getOperands().get(0) instanceof RexCall; // 且操作数是RexCall
    final RexCall pigBag = (RexCall) pigAggCall.getOperands().get(0); // 获取PIG_BAG调用
    assert pigBag.getOperands().size() == 1; // 断言PIG_BAG只有一个操作数
    final RexNode pigBagInput = pigBag.getOperands().get(0); // 获取PIG_BAG的输入

    if (pigBagInput instanceof RexCall) { // 检查输入是否是RexCall
      // Multiset-projection call
      // Multiset投影调用
      final RexCall multisetProjection = (RexCall) pigBagInput; // 转换为RexCall
      assert isMultisetProjection(multisetProjection); // 断言是multiset投影
      return getColsFromMultisetProjection(multisetProjection); // 返回multiset投影的列
    }
    return new ArrayList<>(); // 返回空列表
  }

  private static List<Integer> getColsFromMultisetProjection(RexCall multisetProjection) { // 方法：从multiset投影中提取列索引
    final List<Integer> columns = new ArrayList<>(); // 创建列索引列表
    assert multisetProjection.getOperands().size() >= 1; // 断言至少有一个操作数
    for (int i = 1; i < multisetProjection.getOperands().size(); i++) { // 从第二个操作数开始遍历（第一个是类型）
      final RexLiteral indexLiteral = // 获取索引字面量
          (RexLiteral) multisetProjection.getOperands().get(i); // 转换为RexLiteral
      columns.add(((BigDecimal) indexLiteral.getValue()).intValue()); // 提取整数值并添加到列表
    }
    return columns; // 返回列索引列表
  }

  private static boolean isMultisetProjection(RexCall rexCall) { // 方法：检查是否是multiset投影
    return rexCall.getOperator().getName().equals(MULTISET_PROJECTION); // 比较操作符名称
  }

  /** Rule configuration. */
  // 规则配置接口
  @Value.Immutable(singleton = false) // Immutables注解：生成不可变实现，非单例模式
  public interface Config extends RelRule.Config { // 配置接口，继承自RelRule.Config
    @Override default PigToSqlAggregateRule toRule() { // 默认方法：将配置转换为规则实例
      return new PigToSqlAggregateRule(this); // 创建并返回PigToSqlAggregateRule实例
    }
  }
} // 类结束
