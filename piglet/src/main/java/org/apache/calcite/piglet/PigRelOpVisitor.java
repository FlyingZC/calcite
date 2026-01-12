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
package org.apache.calcite.piglet;

import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalCorrelate;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexWindowBounds;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.Pair;

import org.apache.pig.builtin.CubeDimensions;
import org.apache.pig.builtin.RollupDimensions;
import org.apache.pig.impl.logicalLayer.FrontendException;
import org.apache.pig.impl.util.LinkedMultiMap;
import org.apache.pig.impl.util.MultiMap;
import org.apache.pig.newplan.Operator;
import org.apache.pig.newplan.OperatorPlan;
import org.apache.pig.newplan.PlanWalker;
import org.apache.pig.newplan.logical.expression.LogicalExpressionPlan;
import org.apache.pig.newplan.logical.expression.UserFuncExpression;
import org.apache.pig.newplan.logical.relational.LOCogroup;
import org.apache.pig.newplan.logical.relational.LOCross;
import org.apache.pig.newplan.logical.relational.LOCube;
import org.apache.pig.newplan.logical.relational.LODistinct;
import org.apache.pig.newplan.logical.relational.LOFilter;
import org.apache.pig.newplan.logical.relational.LOForEach;
import org.apache.pig.newplan.logical.relational.LOGenerate;
import org.apache.pig.newplan.logical.relational.LOInnerLoad;
import org.apache.pig.newplan.logical.relational.LOJoin;
import org.apache.pig.newplan.logical.relational.LOLimit;
import org.apache.pig.newplan.logical.relational.LOLoad;
import org.apache.pig.newplan.logical.relational.LONative;
import org.apache.pig.newplan.logical.relational.LORank;
import org.apache.pig.newplan.logical.relational.LOSort;
import org.apache.pig.newplan.logical.relational.LOSplit;
import org.apache.pig.newplan.logical.relational.LOSplitOutput;
import org.apache.pig.newplan.logical.relational.LOStore;
import org.apache.pig.newplan.logical.relational.LOStream;
import org.apache.pig.newplan.logical.relational.LOUnion;
import org.apache.pig.newplan.logical.relational.LogicalPlan;
import org.apache.pig.newplan.logical.relational.LogicalRelationalOperator;
import org.apache.pig.newplan.logical.relational.LogicalSchema;

import com.google.common.collect.ImmutableList;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Visits Pig logical operators and converts them into corresponding relational
 * algebra plans.
 * Pig逻辑操作符访问器，用于将Pig的逻辑操作符转换为对应的关系代数计划
 * 这个类是Pig到Calcite关系代数转换的核心组件，它遍历Pig逻辑计划中的每个操作符，
 * 并将其转换为Calcite的RelNode关系节点，从而实现Pig脚本到SQL查询计划的转换
 * 支持的Pig操作符包括：LOAD、FILTER、FOREACH、GROUP/COGROUP、CUBE/ROLLUP、
 * LIMIT、SORT、JOIN、CROSS、UNION、DISTINCT、SPLIT、RANK等
 */
class PigRelOpVisitor extends PigRelOpWalker.PlanPreVisitor {
  // The relational algebra builder customized for Pig
  protected final PigRelBuilder builder;  // Pig专用的关系代数构建器，用于构建Calcite的RelNode关系节点树，提供scan、filter、project、join等操作符构建方法
  private Operator currentRoot;  // 当前正在处理的Pig操作符根节点，用于跟踪Pig逻辑计划的遍历位置，特别是在处理多个sink操作符时

  /** Type of Pig groups. */
  private enum GroupType {
    CUBE,  // CUBE分组类型，生成所有可能的分组组合，用于多维数据分析
    ROLLUP,  // ROLLUP分组类型，生成层级分组组合，从细粒度到粗粒度的汇总
    REGULAR  // 普通分组类型，标准的GROUP BY操作
  }

  /**
   * Creates a PigRelOpVisitor.
   * 构造函数，创建PigRelOpVisitor实例
   *
   * @param plan    Pig logical plan - Pig的逻辑计划，包含所有待转换的操作符
   * @param walker  The walker over Pig logical plan - 遍历Pig逻辑计划的遍历器，控制访问顺序
   * @param builder Relational algebra builder - 关系代数构建器，用于构建Calcite的RelNode树
   * @throws FrontendException Exception during processing Pig operators - 处理Pig操作符时的异常
   */
  PigRelOpVisitor(OperatorPlan plan, PlanWalker walker, PigRelBuilder builder)
      throws FrontendException {
    super(plan, walker);  // 调用父类构造函数，传入Pig逻辑计划和遍历器
    if (!(walker instanceof PigRelOpWalker)) {  // 验证遍历器类型必须是PigRelOpWalker
      throw new FrontendException("Expected PigRelOpWalker", 2223);  // 类型不匹配则抛出异常
    }
    this.builder = builder;  // 保存关系代数构建器引用
    this.currentRoot = null;  // 初始化当前根节点为null
  }

  Operator getCurrentRoot() {
    return currentRoot;  // 返回当前正在处理的Pig操作符根节点
  }

  /**
   * Translates the given pig logical plan into a list of relational algebra plans.
   * 将给定的Pig逻辑计划转换为关系代数计划列表
   * 这是转换的入口方法，遍历Pig计划中的所有sink操作符（通常是LOStore），
   * 并从每个sink反向遍历整个计划树，构建对应的关系代数计划
   *
   * @return The list of roots of translated plans, each corresponding to a sink
   * operator in the Pig plan - 转换后的关系代数计划根节点列表，每个节点对应Pig计划中的一个sink操作符
   * @throws FrontendException Exception during processing Pig operators - 处理Pig操作符时的异常
   */
  List<RelNode> translate() throws FrontendException {
    List<RelNode> relNodes = new ArrayList<>();  // 创建结果列表，用于存储转换后的RelNode
    for (Operator pigOp : plan.getSinks()) {  // 遍历Pig计划中的所有sink操作符（输出节点）
      currentRoot = pigOp;  // 设置当前根节点为当前sink操作符
      currentWalker.walk(this);  // 使用遍历器从当前sink开始反向遍历整个计划树，触发visit方法
      if (!(pigOp instanceof LOStore)) {  // 如果不是LOStore操作符（LOStore不需要返回RelNode）
        relNodes.add(builder.build());  // 构建关系代数节点并添加到结果列表中
      }
    }
    return relNodes;  // 返回所有转换后的关系代计划根节点
  }

  @Override public void visit(LOLoad load) throws FrontendException {
    // Two types of tables to load:
    // 1. LOAD '[schemaName.]tableName': load table from database catalog - 从数据库目录加载表
    // 2. LOAD '/path/to/tableName': load from a file - 从文件系统加载数据
    String fullName = load.getSchemaFile();  // 获取LOAD语句指定的表名或文件路径
    if (fullName.contains("file://")) {  // 如果包含file://前缀，说明是从数据库目录加载
      // load from database catalog. Pig will see it as a file in the working directory
      fullName = Paths.get(load.getSchemaFile()).getFileName().toString();  // 提取文件名作为表名
    }
    String[] tableNames;  // 用于存储表名数组的变量
    if (fullName.startsWith("/")) {  // 如果以/开头，说明是文件路径
      // load from file
      tableNames = new String[1];  // 创建单元素数组
      tableNames[0] = fullName;  // 存储文件路径
    } else {  // 否则是数据库表名
      // load from catalog
      tableNames = fullName.split("\\.");  // 按点分割schema和表名
    }
    final LogicalSchema pigSchema = load.getSchema();  // 获取Pig LOAD语句中定义的schema
    final RelOptTable pigRelOptTable;  // 声明关系优化表变量
    if (pigSchema == null) {  // 如果Pig schema为null
      pigRelOptTable = null;  // 关系优化表也设为null
    } else {  // 如果Pig schema不为null
      // If Pig schema is provided in the load command, convert it into
      // relational row type
      final RelDataType rowType = PigTypes.convertSchema(pigSchema);  // 将Pig schema转换为Calcite行类型
      pigRelOptTable =  // 创建关系优化表对象
          PigTable.createRelOptTable(builder.getRelOptSchema(), rowType,
              Arrays.asList(tableNames));  // 使用schema、行类型和表名创建
    }
    builder.scan(pigRelOptTable, tableNames);  // 调用构建器的scan方法，创建表扫描节点
    builder.register(load);  // 注册LOAD操作符到构建器，建立Pig操作符到RelNode的映射
  }

  @Override public void visit(LOFilter filter) throws FrontendException {
    final RexNode relExFilter = PigRelExVisitor.translatePigEx(builder, filter.getFilterPlan());  // 将Pig过滤表达式转换为Calcite的RexNode表达式节点
    builder.filter(relExFilter);  // 调用构建器的filter方法，创建过滤节点，添加到关系代数计划中
    builder.register(filter);  // 注册FILTER操作符到构建器，建立Pig操作符到RelNode的映射
  }

  @Override public void visit(LOForEach foreach) throws FrontendException {
    // Use an inner visitor to translate Pig inner plan into a relational plan
    // 使用内部访问器将Pig内部计划转换为关系代数计划
    // See @PigRelOpInnerVisitor for details. - 详细实现见PigRelOpInnerVisitor类
    PigRelOpWalker innerWalker = new PigRelOpWalker(foreach.getInnerPlan());  // 为FOREACH的内部计划创建遍历器
    PigRelOpInnerVisitor innerVisitor =  // 创建内部访问器，用于处理FOREACH内部的嵌套计划
        new PigRelOpInnerVisitor(foreach.getInnerPlan(), innerWalker, builder);
    RelNode root = innerVisitor.translate().get(0);  // 转换内部计划并获取生成的根节点
    builder.push(root);  // 将生成的根节点压入构建器栈中
    builder.register(foreach);  // 注册FOREACH操作符到构建器，建立Pig操作符到RelNode的映射
  }

  @Override public void visit(LOCogroup loCogroup) throws FrontendException {
    // Pig parser already converted CUBE operator into a set of operators, including LOCogroup.
    // Pig解析器已经将CUBE操作符转换为一组操作符，包括LOCogroup
    // Thus this method handles all GROUP/COGROUP/CUBE commands - 因此这个方法处理所有GROUP/COGROUP/CUBE命令
    final GroupType groupType = getGroupType(loCogroup);  // 获取分组类型（REGULAR/CUBE/ROLLUP）
    if (groupType == GroupType.REGULAR) {  // 如果是普通分组
      processRegularGroup(loCogroup);  // 处理普通GROUP/COGROUP操作
    } else { // for CUBE and ROLLUP - 如果是CUBE或ROLLUP
      processCube(groupType, loCogroup);  // 处理CUBE或ROLLUP操作
    }

    // Finally project the group and aggregate fields. Note that if group consists of multiple
    // group keys, we do grouping using multiple keys and then convert these group keys into
    // a single composite group key (with tuple/struct type) in this step.
    // 最后投影分组键和聚合字段。注意如果分组包含多个分组键，我们使用多个键进行分组，
    // 然后在这一步将这些分组键转换为单个复合分组键（使用tuple/struct类型）
    // The other option is to create the composite group key first and do grouping on this
    // composite key. But this option is less friendly the relational algebra, which flat
    // types are more common.
    // 另一个选项是先创建复合分组键，然后在这个复合键上进行分组。但这个选项对关系代数不太友好，
    // 因为关系代数中扁平类型更常见
    projectGroup(loCogroup.getExpressionPlans().get(0).size());  // 投影分组键和聚合字段
    builder.register(loCogroup);  // 注册COGROUP操作符到构建器
  }

  /**
   * Projects group key with 'group' alias so that upstream operator can refer to, along
   * with other aggregate columns. If group consists of multiple group keys, construct
   * a composite tuple/struct type to make it compatible with PIG group semantic.
   * 投影带有'group'别名的分组键，以便上游操作符可以引用，以及其他聚合列。
   * 如果分组包含多个分组键，构造一个复合tuple/struct类型以兼容PIG的分组语义。
   *
   * @param groupCount Number of group keys. - 分组键的数量
   */
  private void projectGroup(int groupCount) {
    final List<RelDataTypeField> inputFields = builder.peek().getRowType().getFieldList();  // 获取当前节点的所有输入字段
    RexNode groupRex;  // 声明分组键的RexNode表达式
    // First construct the group field - 首先构造分组字段
    if (groupCount == 1) {  // 如果只有一个分组键
      // Single group key, just project it out directly - 单个分组键，直接投影
      groupRex = builder.field(0);  // 获取第一个字段作为分组键
    } else {  // 如果有多个分组键
      // Otherwise, build a struct for all group keys use SQL ROW operator - 否则，使用SQL ROW操作符为所有分组键构建一个struct
      List<String> fieldNames = new ArrayList<>();  // 字段名列表
      List<RelDataType> fieldTypes = new ArrayList<>();  // 字段类型列表
      List<RexNode> fieldRexes = new ArrayList<>();  // 字段表达式列表
      for (int j = 0; j < groupCount; j++) {  // 遍历所有分组键
        fieldTypes.add(inputFields.get(j).getType());  // 添加字段类型
        fieldNames.add(inputFields.get(j).getName());  // 添加字段名
        fieldRexes.add(builder.field(j));  // 添加字段引用
      }
      RelDataType groupDataType =  // 创建结构体类型
          PigTypes.TYPE_FACTORY.createStructType(fieldTypes, fieldNames);
      groupRex =  // 使用ROW操作符创建复合分组键表达式
          builder.getRexBuilder().makeCall(groupDataType,
              SqlStdOperatorTable.ROW, fieldRexes);
    }
    List<RexNode> outputFields = new ArrayList<>();  // 输出字段表达式列表
    List<String> outputNames = new ArrayList<>();  // 输出字段名列表
    // Project group field first - 首先投影分组字段
    outputFields.add(groupRex);  // 添加分组键表达式
    outputNames.add("group");  // 添加分组键别名
    // Then all other aggregate fields - 然后投影所有其他聚合字段
    for (int i = groupCount; i < inputFields.size(); i++) {  // 遍历聚合字段
      outputFields.add(builder.field(i));  // 添加字段引用
      outputNames.add(inputFields.get(i).getName());  // 添加字段名
    }
    builder.project(outputFields, outputNames, true);  // 创建投影节点，添加到关系代数计划中
  }

  /**
   * Processes regular a group/group.
   * 处理普通的GROUP/COGROUP操作
   *
   * @param loCogroup Pig logical group operator - Pig逻辑分组操作符
   * @throws FrontendException Exception during processing Pig operators - 处理Pig操作符时的异常
   */
  private void processRegularGroup(LOCogroup loCogroup) throws FrontendException {
    final List<RelBuilder.GroupKey> groupKeys = new ArrayList<>();  // 分组键列表
    final int numRels = loCogroup.getExpressionPlans().size();  // 需要分组的关系数量
    // Project out the group keys and the whole row, which will be aggregated with
    // COLLECT operator later.
    // 投影出分组键和整行，这些行稍后将使用COLLECT操作符进行聚合
    preprocessCogroup(loCogroup, false);  // 预处理COGROUP，投影分组键和整行

    // Build the group key - 构建分组键
    for (Integer key : loCogroup.getExpressionPlans().keySet()) {  // 遍历每个关系的分组键
      final int groupCount = loCogroup.getExpressionPlans().get(key).size();  // 当前关系的分组键数量
      final List<RexNode> relKeys = new ArrayList<>();  // 关系键列表
      for (int i = 0; i < groupCount; i++) {  // 遍历当前关系的每个分组键
        relKeys.add(builder.field(numRels - key, 0, i));  // 获取字段引用，构建分组键表达式
      }
      groupKeys.add(builder.groupKey(relKeys));  // 创建分组键并添加到列表
    }

    // The do COLLECT aggregate. - 然后执行COLLECT聚合
    builder.cogroup(groupKeys);  // 创建COGROUP节点，使用COLLECT聚合函数
  }

  /**
   * Processes a CUBE/ROLLUP group type.
   * 处理CUBE/ROLLUP分组类型
   *
   * @param groupType type of the group, either ROLLUP or CUBE - 分组类型，ROLLUP或CUBE
   * @param loCogroup Pig logical group operator - Pig逻辑分组操作符
   * @throws FrontendException Exception during processing Pig operator - 处理Pig操作符时的异常
   */
  private void processCube(GroupType groupType, LOCogroup loCogroup)
      throws FrontendException {
    assert loCogroup.getExpressionPlans().size() == 1;  // 断言只有一个关系（CUBE/ROLLUP只作用于单个关系）
    // First adjust the top rel in the builder, which will be served as input rel for
    // the CUBE COGROUP operator because Pig already convert LOCube into
    // a ForEach (to project out the group set using @CubeDimensions or @RollupDimension UDFs)
    // and a @LOCogroup. We dont need to use these UDFs to generate the groupset.
    // 首先调整构建器中的顶层关系，它将作为CUBE COGROUP操作符的输入关系，因为Pig已经将LOCube转换为
    // 一个ForEach（使用@CubeDimensions或@RollupDimension UDF投影出分组集）和一个@LOCogroup。
    // 我们不需要使用这些UDF来生成分组集。
    // So we need to undo the effect of translate this ForEach int relational
    // algebra nodes before.
    // 所以我们需要撤销之前将这个ForEach转换为关系代数节点的效果
    adjustCubeInput();  // 调整CUBE输入，撤销ForEach的转换效果

    // Project out the group keys and the whole row, which will be aggregated with
    // COLLECT operator later.
    // 投影出分组键和整行，这些行稍后将使用COLLECT操作符进行聚合
    preprocessCogroup(loCogroup, true);  // 预处理COGROUP，投影分组键和整行（标记为CUBE/ROLLUP）

    // Generate the group set for the corresponding group type.
    // 为对应的分组类型生成分组集
    ImmutableList.Builder<ImmutableBitSet> groupsetBuilder =  // 分组集构建器
        new ImmutableList.Builder<>();
    List<Integer> keyIndexs = new ArrayList<>();  // 键索引列表
    groupsetBuilder.add(ImmutableBitSet.of(keyIndexs));  // 添加空分组集（ROLLUP的初始状态）
    int groupCount = loCogroup.getExpressionPlans().get(0).size();  // 分组键数量
    for (int i = groupCount - 1; i >= 0; i--) {  // 从后向前遍历分组键
      keyIndexs.add(i);  // 添加键索引
      groupsetBuilder.add(ImmutableBitSet.of(keyIndexs));  // 添加当前分组集
    }
    final ImmutableBitSet groupSet = ImmutableBitSet.of(keyIndexs);  // 最终分组集（包含所有键）
    final ImmutableList<ImmutableBitSet> groupSets =  // 所有分组集
        (groupType == GroupType.CUBE)  // 如果是CUBE
            ? ImmutableList.copyOf(groupSet.powerSet()) : groupsetBuilder.build();  // CUBE使用幂集，ROLLUP使用层级集
    RelBuilder.GroupKey groupKey = builder.groupKey(groupSet, groupSets);  // 创建分组键

    // Finally, do COLLECT aggregate. - 最后，执行COLLECT聚合
    builder.cogroup(ImmutableList.of(groupKey));  // 创建COGROUP节点，使用COLLECT聚合函数
  }

  /**
   * Adjusts the rel input for Pig Cube operator.
   * 调整Pig CUBE操作符的关系输入
   * Pig解析器会将CUBE转换为ForEach + LOCogroup的组合，但我们在转换时不需要使用ForEach生成的UDF调用，
   * 所以需要撤销ForEach的转换效果，直接使用原始输入
   */
  private void adjustCubeInput() {
    RelNode project1 = builder.peek();  // 获取栈顶节点（应该是ForEach生成的LogicalProject）
    assert project1 instanceof LogicalProject;  // 断言是LogicalProject节点
    RelNode correl = ((LogicalProject) project1).getInput();  // 获取Project的输入（应该是LogicalCorrelate）
    assert correl instanceof LogicalCorrelate;  // 断言是LogicalCorrelate节点
    RelNode project2 = ((LogicalCorrelate) correl).getLeft();  // 获取Correlate的左子节点（另一个LogicalProject）
    assert project2 instanceof LogicalProject;  // 断言是LogicalProject节点
    builder.replaceTop(((LogicalProject) project2).getInput());  // 将栈顶节点替换为Project2的输入（原始数据）
  }

  /**
   * Projects out group key and the row for each relation.
   * 为每个关系投影出分组键和整行
   *
   * @param loCogroup Pig logical group operator - Pig逻辑分组操作符
   * @param isCubeRollup 是否是CUBE或ROLLUP分组
   * @throws FrontendException Exception during processing Pig operator - 处理Pig操作符时的异常
   */
  private void preprocessCogroup(LOCogroup loCogroup, boolean isCubeRollup)
      throws FrontendException {
    final int numRels = loCogroup.getExpressionPlans().size();  // 需要分组的关系数量

    // Pull out all cogrouped relations from the builder - 从构建器中取出所有需要分组的关系
    List<RelNode> inputRels = new ArrayList<>();  // 输入关系列表
    for (int i = 0; i < numRels; i++) {  // 遍历所有关系
      inputRels.add(0, builder.build());  // 从构建器栈中弹出关系并添加到列表头部
    }

    // Then adding back with the corresponding projection - 然后使用相应的投影添加回构建器
    for (int i = 0; i < numRels; i++) {  // 遍历所有关系
      final RelNode originalRel = inputRels.get(i);  // 获取原始关系
      builder.push(originalRel);  // 将原始关系压入构建器栈
      final Collection<LogicalExpressionPlan> pigGroupKeys =  // 获取当前关系的分组键表达式
          loCogroup.getExpressionPlans().get(i);
      List<RexNode> fieldRels = new ArrayList<>();  // 字段表达式列表
      for (LogicalExpressionPlan pigKey : pigGroupKeys) {  // 遍历所有分组键
        fieldRels.add(PigRelExVisitor.translatePigEx(builder, pigKey));  // 将Pig表达式转换为RexNode
      }
      final RexNode row =  // 创建整行的ROW表达式
          builder.getRexBuilder().makeCall(
              getGroupRowType(fieldRels, isCubeRollup), SqlStdOperatorTable.ROW,
              getGroupRowOperands(fieldRels, isCubeRollup));
      fieldRels.add(row);  // 将整行表达式添加到字段列表
      builder.project(fieldRels);  // 创建投影节点
      builder.updateAlias(builder.getPig(originalRel), builder.getAlias(originalRel), false);  // 更新别名
    }
  }

  // Gets row type for the group column - 获取分组列的行类型
  private RelDataType getGroupRowType(List<RexNode> groupFields, boolean isCubeRollup) {
    if (isCubeRollup) {  // 如果是CUBE或ROLLUP
      final List<RelDataTypeField> rowFields = builder.peek().getRowType().getFieldList();  // 获取当前行的所有字段
      final List<String> fieldNames = new ArrayList<>();  // 字段名列表
      final List<RelDataType> fieldTypes = new ArrayList<>();  // 字段类型列表
      final List<Integer> groupColIndexes = new ArrayList<>();  // 分组列索引列表

      // First copy fields of grouping columns - 首先复制分组列的字段
      for (RexNode rex : groupFields) {  // 遍历分组字段
        assert rex instanceof RexInputRef;  // 断言是输入引用
        int colIndex = ((RexInputRef) rex).getIndex();  // 获取列索引
        groupColIndexes.add(colIndex);  // 添加到分组列索引列表
        fieldNames.add(rowFields.get(colIndex).getName());  // 添加字段名
        fieldTypes.add(rowFields.get(colIndex).getType());  // 添加字段类型
      }

      // Then copy the remaining fields from the parent rel - 然后从父关系复制剩余字段
      for (int i = 0; i < rowFields.size(); i++) {  // 遍历所有字段
        if (!groupColIndexes.contains(i)) {  // 如果不是分组列
          fieldNames.add(rowFields.get(i).getName());  // 添加字段名
          fieldTypes.add(rowFields.get(i).getType());  // 添加字段类型
        }
      }
      return PigTypes.TYPE_FACTORY.createStructType(fieldTypes, fieldNames);  // 创建结构体类型
    }
    return builder.peek().getRowType();  // 如果不是CUBE/ROLLUP，直接返回当前行类型
  }

  /** Gets the operands for the ROW operator to construct the group column. - 获取ROW操作符的操作数以构造分组列 */
  private List<RexNode> getGroupRowOperands(List<RexNode> fieldRels,
      boolean isCubeRollup) {
    final List<RexNode> rowFields = builder.fields();  // 获取所有字段
    if (isCubeRollup) {  // 如果是CUBE或ROLLUP
      // Add group by columns first - 首先添加分组列
      List<RexNode> cubeRowFields = new ArrayList<>(fieldRels);  // 创建列表，包含分组字段

      // Then and remaining columns - 然后添加剩余列
      for (RexNode field : rowFields) {  // 遍历所有字段
        if (!cubeRowFields.contains(field)) {  // 如果字段不在列表中
          cubeRowFields.add(field);  // 添加到列表
        }
      }
      return ImmutableList.copyOf(cubeRowFields);  // 返回不可变列表
    }
    return rowFields;  // 如果不是CUBE/ROLLUP，直接返回所有字段
  }

  /**
   * Checks the group type of a group.
   * 检查分组的类型
   * 通过分析LOCogroup操作符的前驱操作符来判断是CUBE、ROLLUP还是普通GROUP
   * CUBE和ROLLUP会被Pig解析器转换为ForEach + LOCogroup的组合，ForEach内部使用CubeDimensions或RollupDimensions UDF
   *
   * @param pigGroup Pig logical group operator - Pig逻辑分组操作符
   * @return The group type, either CUBE, ROLLUP, or REGULAR - 分组类型，CUBE、ROLLUP或REGULAR
   */
  private static GroupType getGroupType(LOCogroup pigGroup) {
    if (pigGroup.getInputs((LogicalPlan) pigGroup.getPlan()).size() == 1) {  // 如果只有一个输入关系
      final Operator input = pigGroup.getInputs((LogicalPlan) pigGroup.getPlan()).get(0);  // 获取输入操作符
      if (input instanceof LOForEach) {  // 如果输入是LOForEach
        final LOForEach foreach = (LOForEach) input;  // 转换为LOForEach
        if (foreach.getInnerPlan().getSinks().size() == 1) {  // 如果内部计划只有一个sink
          final LOGenerate generate = (LOGenerate) foreach.getInnerPlan().getSinks().get(0);  // 获取LOGenerate
          final List<LogicalExpressionPlan> projectList = generate.getOutputPlans();  // 获取输出计划列表
          if (projectList.size() > 1) {  // 如果有多个输出计划
            final LogicalExpressionPlan exPlan = projectList.get(0);  // 获取第一个表达式计划
            if (exPlan.getSources().size() == 1  // 如果只有一个源
                    && exPlan.getSources().get(0) instanceof UserFuncExpression) {  // 并且是用户函数表达式
              final UserFuncExpression func = (UserFuncExpression) exPlan.getSources().get(0);  // 获取函数表达式
              if (func.getFuncSpec().getClassName().equals(CubeDimensions.class.getName())) {  // 如果是CubeDimensions函数
                return GroupType.CUBE;  // 返回CUBE类型
              }
              if (func.getFuncSpec().getClassName().equals(RollupDimensions.class.getName())) {  // 如果是RollupDimensions函数
                return GroupType.ROLLUP;  // 返回ROLLUP类型
              }
            }
          }
        }
      }
    }
    return GroupType.REGULAR;  // 否则返回REGULAR类型
  }

  @Override public void visit(LOLimit loLimit) {
    builder.limit(0, (int) loLimit.getLimit());  // 调用构建器的limit方法，创建限制节点，从第0行开始限制行数
    builder.register(loLimit);  // 注册LIMIT操作符到构建器
  }

  @Override public void visit(LOSort loSort) throws FrontendException {
    // TODO Hanlde custom sortFunc from Pig??? - TODO: 处理Pig的自定义排序函数
    final int limit = (int) loSort.getLimit();  // 获取排序限制行数
    List<RexNode> relSortCols = new ArrayList<>();  // 排序列表达式列表
    if (loSort.isStar()) {  // 如果是星号排序
      // Sort using all columns - 使用所有列排序
      RelNode top = builder.peek();  // 获取栈顶节点
      for (RelDataTypeField field : top.getRowType().getFieldList()) {  // 遍历所有字段
        relSortCols.add(builder.field(field.getIndex()));  // 添加字段引用到排序列表
      }
    } else {  // 否则使用指定列排序
      // Sort using specific columns - 使用特定列排序
      assert loSort.getSortColPlans().size() == loSort.getAscendingCols().size();  // 断言排序列数和升序标志数相等
      for (int i = 0; i < loSort.getSortColPlans().size(); i++) {  // 遍历每个排序列
        RexNode sortColsNoDirection =  // 将Pig排序表达式转换为RexNode（不包含方向）
            PigRelExVisitor.translatePigEx(builder, loSort.getSortColPlans().get(i));
        // Add sort directions - 添加排序方向
        if (!loSort.getAscendingCols().get(i)) {  // 如果是降序
          relSortCols.add(builder.desc(sortColsNoDirection));  // 添加降序表达式
        } else {  // 如果是升序
          relSortCols.add(sortColsNoDirection);  // 添加升序表达式
        }
      }
    }
    builder.sortLimit(-1, limit, relSortCols);  // 创建排序限制节点，-1表示无偏移量
    builder.register(loSort);  // 注册SORT操作符到构建器
  }

  @Override public void visit(LOJoin join) throws FrontendException {
    joinInternal(join.getExpressionPlans(), join.getInnerFlags());  // 调用内部join方法，处理连接逻辑
    LogicalJoin joinRel = (LogicalJoin) builder.peek();  // 获取生成的LogicalJoin节点
    Set<String> duplicateNames = new HashSet<>(joinRel.getLeft().getRowType().getFieldNames());  // 获取左表的字段名集合
    duplicateNames.retainAll(joinRel.getRight().getRowType().getFieldNames());  // 保留右表中也存在的字段名（重复字段名）
    if (!duplicateNames.isEmpty()) {  // 如果有重复字段名
      final List<String> fieldNames = new ArrayList<>();  // 字段名列表
      final List<RexNode> fields = new ArrayList<>();  // 字段表达式列表
      for (RelDataTypeField leftField : joinRel.getLeft().getRowType().getFieldList()) {  // 遍历左表字段
        fieldNames.add(builder.getAlias(joinRel.getLeft()) + "::" + leftField.getName());  // 添加别名::字段名格式的字段名
        fields.add(builder.field(leftField.getIndex()));  // 添加字段引用
      }
      int leftCount = joinRel.getLeft().getRowType().getFieldList().size();  // 左表字段数
      for (RelDataTypeField rightField : joinRel.getRight().getRowType().getFieldList()) {  // 遍历右表字段
        fieldNames.add(builder.getAlias(joinRel.getRight()) + "::" + rightField.getName());  // 添加别名::字段名格式的字段名
        fields.add(builder.field(rightField.getIndex() + leftCount));  // 添加字段引用（索引偏移左表字段数）
      }
      builder.project(fields, fieldNames);  // 创建投影节点，消除字段名冲突
    }
    builder.register(join);  // 注册JOIN操作符到构建器
  }

  @Override public void visit(LOCross loCross) throws FrontendException {
    final int numInputs = loCross.getInputs().size();  // 获取输入关系数量
    MultiMap<Integer, LogicalExpressionPlan> joinPlans = new LinkedMultiMap<>();  // 创建连接键映射
    boolean[] innerFlags = new boolean[numInputs];  // 创建内连接标志数组
    for (int i = 0; i < numInputs; i++) {  // 遍历所有输入
      // Adding empty join keys - 添加空的连接键（CROSS不需要连接键）
      joinPlans.put(i, Collections.emptyList());  // 为每个输入设置空的连接键列表
      innerFlags[i] = true;  // 设置为内连接标志
    }
    joinInternal(joinPlans, innerFlags);  // 调用内部join方法，处理CROSS连接
    builder.register(loCross);  // 注册CROSS操作符到构建器
  }

  /**
   * Joins a list of relations (previously pushed into the builder).
   * 连接一组关系（之前已压入构建器）
   * 使用左深度树的方式连接多个关系，从左到右依次连接
   *
   * @param joinPlans  Join keys - 连接键映射，每个关系对应的连接键表达式
   * @param innerFlags Join type - 连接类型标志数组，true表示内连接，false表示外连接
   * @throws FrontendException Exception during processing Pig operator - 处理Pig操作符时的异常
   */
  private void joinInternal(MultiMap<Integer, LogicalExpressionPlan> joinPlans,
                            boolean[] innerFlags) throws FrontendException {
    final int numRels = joinPlans.size();  // 需要连接的关系数量

    // Pull out all joined relations from the builder - 从构建器中取出所有需要连接的关系
    List<RelNode> joinRels = new ArrayList<>();  // 连接关系列表
    for (int i = 0; i < numRels; i++) {  // 遍历所有关系
      joinRels.add(0, builder.build());  // 从构建器栈中弹出关系并添加到列表头部
    }

    // Then join each pair from left to right - 然后从左到右依次连接每对关系
    for (int i = 0; i < numRels; i++) {  // 遍历所有关系
      builder.push(joinRels.get(i));  // 将关系压入构建器栈
      if (i == 0) {  // 如果是第一个关系
        continue;  // 跳过，不需要连接
      }
      List<RexNode> predicates = new ArrayList<>();  // 连接谓词列表
      List<LogicalExpressionPlan> leftJoinExprs = joinPlans.get(i - 1);  // 左连接键表达式列表
      List<LogicalExpressionPlan> rightJoinExprs = joinPlans.get(i);  // 右连接键表达式列表
      assert leftJoinExprs.size() == rightJoinExprs.size();  // 断言左右连接键数量相等
      for (int j = 0; j < leftJoinExprs.size(); j++) {  // 遍历所有连接键对
        RexNode leftRelExpr =  // 转换左连接键表达式
            PigRelExVisitor.translatePigEx(builder, leftJoinExprs.get(j), 2, 0);
        RexNode rightRelExpr =  // 转换右连接键表达式
            PigRelExVisitor.translatePigEx(builder, rightJoinExprs.get(j), 2, 1);
        predicates.add(builder.equals(leftRelExpr, rightRelExpr));  // 添加等值连接谓词
      }
      builder.join(getJoinType(innerFlags[i - 1], innerFlags[i]), builder.and(predicates));  // 创建连接节点
    }
  }

  /**
   * Decides the join type from the inner types of both relation.
   * 根据两个关系的内连接类型决定最终的连接类型
   * Pig的JOIN语法使用内连接标志来控制连接类型，例如：A JOIN B BY key, C OUTER JOIN D BY key
   * innerFlags数组表示每个关系是否要求内连接
   *
   * @param leftInner  true if the left requires inner - 如果左关系要求内连接则为true
   * @param rightInner true if the right requires inner - 如果右关系要求内连接则为true
   * @return The join type, either INNER, LEFT, RIGHT, or FULL - 连接类型，INNER、LEFT、RIGHT或FULL
   */
  private static JoinRelType getJoinType(boolean leftInner, boolean rightInner) {
    if (leftInner && rightInner) {  // 如果左右都要求内连接
      return JoinRelType.INNER;  // 返回内连接
    } else if (leftInner) {  // 如果只有左要求内连接
      return JoinRelType.LEFT;  // 返回左外连接
    } else if (rightInner) {  // 如果只有右要求内连接
      return JoinRelType.RIGHT;  // 返回右外连接
    } else {  // 如果都不要求内连接
      return JoinRelType.FULL;  // 返回全外连接
    }
  }

  @Override public void visit(LOUnion loUnion) throws FrontendException {
    // The tricky thing to translate union are the input schemas. Relational algebra does not
    // support UNION of input with different schemas, so we need to make sure to have inputs
    // with same schema first.
    // 转换UNION的棘手之处是输入schema。关系代数不支持不同schema的输入的UNION，
    // 所以我们需要确保输入具有相同的schema
    LogicalSchema unionSchema = loUnion.getSchema();  // 获取UNION的schema
    if (unionSchema == null) {  // 如果schema为null
      throw new IllegalArgumentException("UNION on incompatible types is not supported. "  // 抛出异常
          + "Please consider using ONSCHEMA option");  // 提示使用ONSCHEMA选项
    }
    // First get the shared schema - 首先获取共享的schema
    int numInputs = loUnion.getInputs().size();  // 获取输入数量
    RelDataType unionRelType = PigTypes.convertSchema(unionSchema);  // 将Pig schema转换为Calcite类型

    // Then using projections to adjust input relations with the shared schema - 然后使用投影将输入关系调整为共享schema
    List<RelNode> adjustedInputs = new ArrayList<>();  // 调整后的输入列表
    for (int i = 0; i < numInputs; i++) {  // 遍历所有输入
      adjustedInputs.add(builder.project(builder.build(), unionRelType));  // 投影到共享schema
    }

    // Push the adjusted input back to the builder to do union - 将调整后的输入压回构建器以执行UNION
    for (int i = numInputs - 1; i >= 0; i--) {  // 从后向前遍历
      builder.push(adjustedInputs.get(i));  // 压入调整后的输入
    }

    // Finally do union - 最后执行UNION
    builder.union(true, numInputs);  // 创建UNION节点，true表示去重
    builder.register(loUnion);  // 注册UNION操作符到构建器
  }

  @Override public void visit(LODistinct loDistinct) {
    // Straightforward, just build distinct on the top relation - 直接在顶层关系上构建去重节点
    builder.distinct();  // 调用构建器的distinct方法，创建去重节点
    builder.register(loDistinct);  // 注册DISTINCT操作符到构建器
  }

  @Override public void visit(LOCube cube) throws FrontendException {
    // Invalid to get here - 不应该到达这里
    throw new FrontendException("Cube should be translated into group by Pig parser", 10000);  // 抛出异常，CUBE应该被Pig解析器转换为GROUP BY
  }

  @Override public void visit(LOInnerLoad load) throws FrontendException {
    // InnerLoad should be handled by @PigRelOpInnerVisitor - InnerLoad应该由PigRelOpInnerVisitor处理
    throw new FrontendException("Not implemented", 10000);  // 抛出异常，未实现
  }

  @Override public void visit(LOSplit loSplit) {
    builder.register(loSplit);  // 注册SPLIT操作符到构建器（SPLIT只是标记，实际处理在LOSplitOutput中）
  }

  @Override public void visit(LOSplitOutput loSplitOutput) throws FrontendException {
    final RexNode relExFilter =  // 将Pig过滤表达式转换为Calcite的RexNode
        PigRelExVisitor.translatePigEx(builder, loSplitOutput.getFilterPlan());
    builder.filter(relExFilter);  // 调用构建器的filter方法，创建过滤节点
    builder.register(loSplitOutput);  // 注册SPLITOUTPUT操作符到构建器
  }

  @Override public void visit(LOStore store) {
    builder.store(store.getAlias());  // 调用构建器的store方法，创建存储节点，使用别名作为表名
  }

  @Override public void visit(LOGenerate gen) throws FrontendException {
    // LOGenerate should be handled by @PigRelOpInnerVisitor - LOGenerate应该由PigRelOpInnerVisitor处理
    throw new FrontendException("Not implemented", 10000);  // 抛出异常，未实现
  }

  @Override public void visit(LORank loRank) throws FrontendException {
    // First build the rank field using window function with information from loRank - 首先使用窗口函数构建rank字段
    final RexNode rankField = buildRankField(loRank);  // 调用buildRankField方法构建rank字段

    // Then project out the rank field along with all other fields - 然后投影rank字段和所有其他字段
    final RelDataType inputRowType = builder.peek().getRowType();  // 获取输入行类型
    List<RexNode> projectedFields = new ArrayList<>();  // 投影字段列表
    List<String> fieldNames = new ArrayList<>();  // 字段名列表

    projectedFields.add(rankField);  // 添加rank字段
    fieldNames.add(loRank.getSchema().getField(0).alias);  // 添加rank字段的别名
    for (int i = 0; i < inputRowType.getFieldCount(); i++) {  // 遍历所有输入字段
      projectedFields.add(builder.field(i));  // 添加字段引用
      fieldNames.add(inputRowType.getFieldNames().get(i));  // 添加字段名
    }

    // Finally do project - 最后执行投影
    builder.project(projectedFields, fieldNames);  // 创建投影节点
    builder.register(loRank);  // 注册RANK操作符到构建器
  }

  /**
   * Builds a window function for {@link LORank}.
   * 为LORank操作符构建窗口函数
   * 使用Calcite的窗口函数功能来实现Pig的RANK操作
   *
   * @param loRank Pig logical rank operator - Pig逻辑rank操作符
   * @return The window function - 窗口函数表达式
   * @throws FrontendException Exception during processing Pig operator - 处理Pig操作符时的异常
   */
  private RexNode buildRankField(LORank loRank) throws FrontendException {
    // Aggregate function is either RANK or DENSE_RANK - 聚合函数是RANK或DENSE_RANK
    SqlAggFunction rank =  // 聚合函数
        loRank.isDenseRank() ? SqlStdOperatorTable.DENSE_RANK : SqlStdOperatorTable.RANK;  // 根据isDenseRank标志选择函数

    // Build the order keys - 构建排序列
    List<RexNode> orderNodes = new ArrayList<>();  // 排序列表达式列表
    for (Pair<LogicalExpressionPlan, Boolean> p  // 遍历排序列和升序标志对
        : Pair.zip(loRank.getRankColPlans(), loRank.getAscendingCol())) {
      RexNode orderNode =  // 转换Pig排序列表达式
          PigRelExVisitor.translatePigEx(builder, p.left);
      final boolean ascending = p.right;  // 获取升序标志
      if (!ascending) {  // 如果是降序
        orderNode = builder.desc(orderNode);  // 添加降序修饰符
      }
      orderNodes.add(orderNode);  // 添加到排序列表
    }

    return builder.aggregateCall(rank)  // 创建聚合调用
        .over()  // 开始窗口函数定义
        .rangeFrom(RexWindowBounds.UNBOUNDED_PRECEDING)  // 设置窗口范围为从无界前驱开始
        .orderBy(orderNodes)  // 设置排序列
        .toRex();  // 转换为RexNode表达式
  }

  @Override public void visit(LOStream loStream) throws FrontendException {
    throw new FrontendException("Not implemented", 10000);  // 抛出异常，LOStream未实现
  }

  @Override public void visit(LONative nativeMR) throws FrontendException {
    throw new FrontendException("Not implemented", 10000);  // 抛出异常，LONative未实现
  }

  @Override public boolean preVisit(LogicalRelationalOperator root) {
    return builder.checkMap(root);  // 检查并映射Pig操作符到关系代数节点，返回是否应该继续访问
  }
}
