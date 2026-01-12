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
package org.apache.calcite.piglet; // 定义包名，属于calcite的piglet子包，用于处理Pig脚本到关系代数的转换

import org.apache.calcite.plan.RelOptUtil; // 导入Calcite的关系代数工具类，用于处理关系节点的优化和操作
import org.apache.calcite.rel.RelNode; // 导入关系代数节点接口，表示关系代数中的一个操作节点
import org.apache.calcite.rel.core.CorrelationId; // 导入相关ID类，用于标识相关子查询中的相关变量
import org.apache.calcite.rel.core.JoinRelType; // 导入连接关系类型枚举，如INNER、LEFT、RIGHT等
import org.apache.calcite.rel.logical.LogicalValues; // 导入逻辑常量节点，用于生成常量行
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，描述关系代数中的数据类型
import org.apache.calcite.rex.RexNode; // 导入行表达式节点接口，表示关系代数中的表达式
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SQL标准操作符表，提供各种SQL操作符
import org.apache.calcite.sql.type.MultisetSqlType; // 导入多重集SQL类型，用于表示集合类型
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，如INTEGER、VARCHAR等
import org.apache.calcite.sql.type.SqlTypeUtil; // 导入SQL类型工具类，提供类型比较和转换等操作
import org.apache.calcite.util.Litmus; // 导入Litmus枚举，用于控制验证的严格程度

import org.apache.pig.impl.logicalLayer.FrontendException; // 导入Pig前端异常类，表示Pig逻辑层处理时的异常
import org.apache.pig.newplan.Operator; // 导入Pig操作符接口，表示Pig逻辑计划中的一个操作符
import org.apache.pig.newplan.OperatorPlan; // 导入Pig操作符计划接口，表示Pig逻辑计划
import org.apache.pig.newplan.PlanWalker; // 导入Pig计划遍历器接口，用于遍历Pig逻辑计划
import org.apache.pig.newplan.logical.expression.LogicalExpressionPlan; // 导入Pig逻辑表达式计划，表示表达式子计划
import org.apache.pig.newplan.logical.relational.LOGenerate; // 导入Pig的LOGenerate操作符，用于生成输出
import org.apache.pig.newplan.logical.relational.LOInnerLoad; // 导入Pig的LOInnerLoad操作符，用于内部加载
import org.apache.pig.newplan.logical.relational.LogicalRelationalOperator; // 导入Pig逻辑关系操作符接口
import org.apache.pig.newplan.logical.relational.LogicalSchema; // 导入Pig逻辑模式，描述数据的结构

import com.google.common.collect.ImmutableSet; // 导入Google Guava的不可变集合类

import java.util.ArrayDeque; // 导入Java的双端队列数组实现，用于高效的栈操作
import java.util.ArrayList; // 导入Java的动态数组列表，用于存储可变长度的元素集合
import java.util.Deque; // 导入Java的双端队列接口，支持两端插入和删除
import java.util.List; // 导入Java的列表接口，表示有序的元素集合

/**
 * Visits Pig logical operators of Pig inner logical plans
 * (in {@link org.apache.pig.newplan.logical.relational.LOForEach})
 * and converts them into corresponding relational algebra plans.
 * 访问Pig内部逻辑计划中的Pig逻辑操作符（在LOForEach中），并将它们转换为对应的关系代数计划
 * 这个类专门处理Pig的嵌套foreach块中的操作符转换，是Pig到Calcite关系代数转换的核心组件
 */
class PigRelOpInnerVisitor extends PigRelOpVisitor { // 定义PigRelOpInnerVisitor类，继承自PigRelOpVisitor基类
  // The relational algebra operator corresponding to the input of LOForeach operator.
  // 对应于LOForeach操作符输入的关系代数操作符，这是内部计划的输入关系节点
  private final RelNode inputRel; // 声明私有的常量关系节点，存储LOForeach的输入关系

  // Stack contains correlation id required for processing inner plan.
  // 栈结构，包含处理内部计划所需的相关ID，用于跟踪和管理嵌套块中的相关变量
  private final Deque<CorrelationId> corStack = new ArrayDeque<>(); // 声明私有的双端队列作为栈，存储相关ID

  /**
   * Creates a PigRelOpInnerVisitor.
   * 创建一个PigRelOpInnerVisitor实例，用于访问和转换Pig内部逻辑计划
   *
   * @param plan Pig inner logical plan - Pig内部逻辑计划，包含需要转换的操作符
   * @param walker The walker over Pig logical plan - 遍历Pig逻辑计划的遍历器
   * @param builder Relational algebra builder - 关系代数构建器，用于构建关系代数节点
   * @throws FrontendException Exception during processing Pig operators - 处理Pig操作符时可能抛出的异常
   */
  PigRelOpInnerVisitor(OperatorPlan plan, PlanWalker walker, PigRelBuilder builder) // 构造函数，初始化访问器
      throws FrontendException { // 声明可能抛出前端异常
    super(plan, walker, builder); // 调用父类PigRelOpVisitor的构造函数，初始化基本属性
    this.inputRel = builder.peek(); // 从构建器中获取当前的关系节点，作为LOForeach的输入关系
  }

  @Override public void visit(LOGenerate gen) throws FrontendException { // 重写visit方法，处理LOGenerate操作符
    // @LOGenerate is the root of the inner plan, meaning if we reach here, all operators
    // except this node have been converted into relational algebra nodes stored in the builder.
    // LOGenerate是内部计划的根节点，意味着当我们到达这里时，除这个节点外的所有操作符
    // 都已经被转换为关系代数节点并存储在构建器中
    // Here we do the final step of generating the relational algebra output node for the
    // @LOForEach operator.
    // 在这里我们执行生成LOForEach操作符的关系代数输出节点的最后步骤

    // First rejoin all results of columns processed in nested block, if any, using correlation ids
    // we remembered before (in visit(LOForeach)).
    // 首先重新连接在嵌套块中处理的所有列的结果（如果有的话），使用我们之前记住的相关ID（在visit(LOForeach)中）
    makeCorrelates(); // 调用makeCorrelates方法，执行相关连接操作

    // The project all expressions in the generate command, but ignore flattened columns now
    // 投影generate命令中的所有表达式，但现在忽略展平的列
    final List<Integer> multisetFlattens = new ArrayList<>(); // 创建列表，存储需要展平的多重集列的索引
    final List<String> flattenOutputAliases = new ArrayList<>(); // 创建列表，存储展平列的输出别名
    doGenerateWithoutMultisetFlatten(gen, multisetFlattens, flattenOutputAliases); // 调用方法执行投影操作（不处理多重集展平）
    if (!multisetFlattens.isEmpty()) { // 如果存在需要展平的多重集列
      builder.multiSetFlatten(multisetFlattens, flattenOutputAliases); // 调用构建器执行多重集展平操作
    }
  }

  /**
   * Rejoins all multiset (bag) columns that have been processed in the nested
   * foreach block.
   * 重新连接在嵌套foreach块中处理的所有多重集（bag）列
   * 这个方法通过相关连接将嵌套块中处理的结果重新整合到主计划中
   *
   */
  private void makeCorrelates() { // 定义私有方法makeCorrelates，执行相关连接操作
    List<CorrelationId> corIds = new ArrayList<>(); // 创建列表，存储所有相关ID
    List<RelNode> rightRels =  new ArrayList<>(); // 创建列表，存储右侧关系节点

    // First pull out all correlation ids we remembered from the InnerLoads
    // 首先从栈中提取我们从InnerLoads记住的所有相关ID
    while (!corStack.isEmpty()) { // 当相关ID栈不为空时循环
      final CorrelationId corId = corStack.pop(); // 从栈顶弹出一个相关ID
      corIds.add(0, corId); // 将相关ID添加到列表的开头，保持顺序

      final List<RelNode> corRels = new ArrayList<>(); // 创建列表，存储来自同一内部加载的所有输出关系节点
      while (!RelOptUtil.notContainsCorrelation(builder.peek(), corId, Litmus.IGNORE)) { // 当构建器栈顶节点包含相关ID时循环
        corRels.add(0, builder.build()); // 从构建器构建关系节点并添加到列表开头
      }

      assert !corRels.isEmpty(); // 断言corRels不为空，确保至少有一个关系节点
      builder.push(corRels.get(0)); // 将第一个关系节点压入构建器栈
      builder.collect(); // 调用collect操作，将结果收集为多重集
      // Now collapse these rels to a single multiset row and join them together
      // 现在将这些关系节点折叠为单个多重集行并将它们连接在一起
      for (int i = 1; i < corRels.size(); i++) { // 遍历剩余的关系节点
        builder.push(corRels.get(i)); // 将关系节点压入构建器栈
        builder.collect(); // 调用collect操作收集为多重集
        builder.join(JoinRelType.INNER, builder.literal(true)); // 执行内连接，条件为true
      }

      rightRels.add(0, builder.build()); // 构建最终的关系节点并添加到右侧关系列表开头
    }

    // The do correlate join
    // 执行相关连接操作
    for (int i = 0; i < corIds.size(); i++) { // 遍历所有相关ID
      builder.push(rightRels.get(i)); // 将右侧关系节点压入构建器栈
      builder.join(JoinRelType.INNER, builder.literal(true), ImmutableSet.of(corIds.get(i))); // 执行内连接，使用相关ID集合
    }
  }

  /**
   * Projects all expressions in LOGenerate output expressions, but not consider flatten
   * multiset columns yet.
   * 投影LOGenerate输出表达式中的所有表达式，但不考虑展平多重集列
   * 这个方法处理generate语句中的投影操作，将多重集展平延迟到最后处理
   *
   * @param gen Pig logical generate operator - Pig逻辑generate操作符
   * @throws FrontendException Exception during processing Pig operators - 处理Pig操作符时可能抛出的异常
   */
  private void doGenerateWithoutMultisetFlatten(LOGenerate gen, List<Integer> multisetFlattens, // 定义私有方法，执行generate操作（不处理多重集展平）
      List<String> flattenOutputAliases) throws FrontendException { // 声明可能抛出前端异常
    final List<LogicalExpressionPlan> pigProjections = gen.getOutputPlans(); // 获取generate操作符的输出表达式计划列表
    final List<RexNode> innerCols = new ArrayList<>(); // 创建列表，用于存储投影表达式（关系代数表达式）
    final List<String> fieldAlias = new ArrayList<>(); // 创建列表，用于存储投影名称/别名

    if (gen.getOutputPlanSchemas() == null) { // 如果输出模式为空
      throw new IllegalArgumentException( // 抛出非法参数异常
          "Generate statement at line " + gen.getLocation().line() + " produces empty schema"); // 异常信息：generate语句产生的模式为空
    }

    for (int i = 0; i < pigProjections.size(); i++) { // 遍历所有Pig投影表达式
      final LogicalSchema outputFieldSchema = gen.getOutputPlanSchemas().get(i); // 获取当前字段的输出模式
      RexNode rexNode = PigRelExVisitor.translatePigEx(builder, pigProjections.get(i)); // 将Pig表达式转换为关系代数表达式
      RelDataType dataType = rexNode.getType(); // 获取表达式的数据类型
      // If project field in null constant, dataType will by NULL type, need to check the original
      // type of Pig Schema
      // 如果投影字段是null常量，dataType将是NULL类型，需要检查Pig模式的原始类型
      if (dataType.getSqlTypeName() == SqlTypeName.NULL) { // 如果数据类型是NULL类型
        dataType = PigTypes.convertSchema(outputFieldSchema, true); // 从Pig模式转换数据类型
      }

      if (outputFieldSchema.size() == 1 && !gen.getFlattenFlags()[i]) { // 如果输出模式只有一个字段且不需要展平
        final RelDataType scriptType = // 获取脚本类型
            PigTypes.convertSchemaField(outputFieldSchema.getField(0)); // 从Pig模式字段转换类型
        if (dataType.getSqlTypeName() == SqlTypeName.ANY // 如果数据类型是ANY类型
                || !SqlTypeUtil.isComparable(dataType, scriptType)) { // 或者类型不可比较
          // Script schema is different from project expression schema, need to do type cast
          // 脚本模式与投影表达式模式不同，需要进行类型转换
          rexNode = builder.getRexBuilder().makeCast(scriptType, rexNode); // 执行类型转换
        }
      }

      if (gen.getFlattenFlags()[i] && dataType.isStruct() // 如果需要展平且数据类型是结构体
              && (dataType.getFieldCount() > 0 || dataType instanceof DynamicTupleRecordType)) { // 且有字段或是动态元组类型
        if (dataType instanceof DynamicTupleRecordType) { // 如果是动态元组记录类型
          ((DynamicTupleRecordType) dataType).resize(outputFieldSchema.size()); // 调整动态元组大小
          for (int j = 0; j < outputFieldSchema.size(); j++) { // 遍历输出模式的所有字段
            final RelDataType scriptType = // 获取脚本类型
                PigTypes.convertSchemaField(outputFieldSchema.getField(j)); // 从Pig模式字段转换类型
            RexNode exp = // 创建表达式
                builder.call(SqlStdOperatorTable.ITEM, rexNode, // 调用ITEM操作符访问元组元素
                    builder.literal(j + 1)); // 使用1-based索引
            innerCols.add(builder.getRexBuilder().makeCast(scriptType, exp)); // 添加类型转换后的表达式到投影列表
            fieldAlias.add(outputFieldSchema.getField(j).alias); // 添加字段别名
          }
        } else { // 如果是普通结构体类型
          for (int j = 0; j < dataType.getFieldCount(); j++) { // 遍历结构体的所有字段
            innerCols.add(builder.dot(rexNode, j)); // 添加字段访问表达式到投影列表
            fieldAlias.add(outputFieldSchema.getField(j).alias); // 添加字段别名
          }
        }
      } else { // 如果不需要展平或不是结构体类型
        innerCols.add(rexNode); // 直接添加表达式到投影列表
        String alias = null; // 初始化别名为null
        if (outputFieldSchema.size() == 1) { // 如果输出模式只有一个字段
          // If simple type, take user alias if available
          // 如果是简单类型，使用用户提供的别名（如果有）
          alias = outputFieldSchema.getField(0).alias; // 获取字段别名
        }
        fieldAlias.add(alias); // 添加别名到别名列表
        if (gen.getFlattenFlags()[i] && dataType.getFamily() instanceof MultisetSqlType) { // 如果需要展平且是多重集类型
          multisetFlattens.add(innerCols.size() - 1); // 记录需要展平的列索引
          for (LogicalSchema.LogicalFieldSchema field : outputFieldSchema.getFields()) { // 遍历输出模式的所有字段
            String colAlias = field.alias; // 获取列别名
            if (colAlias.contains("::")) { // 如果别名包含"::"分隔符
              String[] tokens  = colAlias.split("::"); // 分割别名
              colAlias = tokens[tokens.length - 1]; // 取最后一部分作为别名
            }
            flattenOutputAliases.add(colAlias); // 添加展平输出别名
          }
        }
      }
    }
    builder.project(innerCols, fieldAlias, true); // 调用构建器执行投影操作，第三个参数true表示强制投影
  }

  @Override public void visit(LOInnerLoad load) { // 重写visit方法，处理LOInnerLoad操作符
    // Inner loads are the first operator the post order walker (@PigRelOpWalker) visits first
    // 内部加载是后序遍历器(@PigRelOpWalker)首先访问的第一个操作符
    // We first look at the plan structure to see if the inner load is for a simple projection,
    // which will not be processed in the nested block
    // 我们首先查看计划结构，看看内部加载是否用于简单投影，这种情况下不会在嵌套块中处理
    List<Operator> succesors = load.getPlan().getSuccessors(load); // 获取当前操作符的后继操作符列表

    // An inner load is for a simple projection if it is a direct input of the @LOGenerate.
    // 如果内部加载是@LOGenerate的直接输入，则它是用于简单投影的
    // Nothing need to be done further here.
    // 这里不需要做任何进一步的操作
    if (succesors.size() == 1 && succesors.get(0) instanceof LOGenerate) { // 如果只有一个后继且是LOGenerate
      return; // 直接返回，不做处理
    }

    // Now get the index of projected column using its alias
    // 现在使用别名获取投影列的索引
    RelDataType inputType = inputRel.getRowType(); // 获取输入关系的数据类型
    final String colAlias = load.getProjection().getColAlias(); // 获取投影列的别名
    int index = colAlias != null // 如果别名不为null
                    ? inputType.getFieldNames().indexOf(colAlias) // 通过别名查找索引
                    : load.getProjection().getColNum(); // 否则使用列号
    assert index >= 0; // 断言索引非负，确保找到列

    // The column should have multiset type to serve as input for the inner plan
    // 该列应该具有多重集类型，以作为内部计划的输入
    assert inputType.getFieldList().get(index).getType().getFamily() instanceof MultisetSqlType; // 断言列类型是多重集类型

    // Build a correlated expression from the input row
    // 从输入行构建相关表达式
    final CorrelationId correlId = builder.nextCorrelId(); // 生成一个新的相关ID
    final RexNode cor = builder.correl(inputType.getFieldList(), correlId); // 创建相关表达式，表示对输入行中变量的引用

    // The project out the column from the correlated expression
    // 从相关表达式中投影出列
    RexNode fieldAccess = builder.getRexBuilder().makeFieldAccess(cor, index); // 创建字段访问表达式
    builder.push(LogicalValues.createOneRow(builder.getCluster())); // 压入单行常量节点
    builder.project(fieldAccess); // 投影字段访问表达式

    // Flatten the column value so that it can be served as the input relation for the inner plan
    // 展平列值，使其可以作为内部计划的输入关系
    builder.multiSetFlatten(); // 执行多重集展平操作，将集合展开为多行

    // Remember the correlation id, then the walker will walk up successor Pig operators. These
    // operators will be processed in @PigRelOpVisitor until it hits the @LOGenerate operator,
    // which will be processed in this class in visit(LOGenerate)
    // 记住相关ID，然后遍历器将向上遍历后继Pig操作符。这些操作符将在@PigRelOpVisitor中处理，
    // 直到遇到@LOGenerate操作符，该操作符将在本类的visit(LOGenerate)方法中处理
    corStack.push(correlId); // 将相关ID压入栈中保存
  }

  @Override public boolean preVisit(LogicalRelationalOperator root) { // 重写preVisit方法，在访问操作符前调用
    // Do not remember the visited PigOp in the inner plan, otherwise, we have trouble in doing
    // correlate with shared PigOp
    // 不要记住内部计划中访问过的Pig操作符，否则我们在与共享的Pig操作符进行相关连接时会有问题
    return false; // 返回false，表示不记住访问的操作符
  }
}
