/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // 许可证声明：本文件由Apache软件基金会许可
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议：详见NOTICE文件，了解版权信息
 * this work for additional information regarding copyright ownership. // 本工作包含版权所有权信息
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache 2.0许可证授权您使用本文件
 * (the "License"); you may not use this file except in compliance with // 您只能在遵守许可证的情况下使用本文件
 * the License.  You may obtain a copy of the License at // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // Apache 2.0许可证的在线地址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则
 * distributed under the License is distributed on an "AS IS" BASIS, // 本软件按"原样"分发，不提供任何明示或暗示的保证
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不包含任何类型的保证或条件，无论是明示还是暗示的
 * See the License for the specific language governing permissions and // 详见许可证，了解语言特定的权限和
 * limitations under the License. // 使用限制
 */
package org.apache.calcite.adapter.splunk; // 声明包名，此类属于Apache Calcite的Splunk适配器包

import org.apache.calcite.adapter.splunk.util.StringUtils; // 导入StringUtils工具类，用于日志记录
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示优化器的集群，包含类型工厂等
import org.apache.calcite.plan.RelOptRuleCall; // 导入RelOptRuleCall类，表示规则调用时的上下文
import org.apache.calcite.plan.RelOptRuleOperand; // 导入RelOptRuleOperand类，定义规则的操作数
import org.apache.calcite.plan.RelRule; // 导入RelRule类，Calcite优化规则的基类
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式
import org.apache.calcite.rel.logical.LogicalFilter; // 导入LogicalFilter类，表示逻辑过滤操作
import org.apache.calcite.rel.logical.LogicalProject; // 导入LogicalProject类，表示逻辑投影操作
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型（行类型）
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField类，表示关系数据类型的字段
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示Rex表达式中的函数调用
import org.apache.calcite.rex.RexInputRef; // 导入RexInputRef类，表示对输入字段的引用
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类，表示字面量（常量值）
import org.apache.calcite.rex.RexNode; // 导入RexNode接口，表示行表达式
import org.apache.calcite.rex.RexSlot; // 导入RexSlot接口，表示对槽位的引用（字段或变量）
import org.apache.calcite.runtime.PairList; // 导入PairList类，用于存储键值对列表
import org.apache.calcite.sql.SqlBinaryOperator; // 导入SqlBinaryOperator类，表示二元操作符
import org.apache.calcite.sql.SqlKind; // 导入SqlKind枚举，定义SQL操作的类型
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator接口，表示SQL操作符
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类，标准SQL操作符表
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义SQL类型名称
import org.apache.calcite.tools.RelBuilderFactory; // 导入RelBuilderFactory接口，用于创建RelNode的工厂
import org.apache.calcite.util.NlsString; // 导入NlsString类，表示支持国际化的字符串

import com.google.common.collect.ImmutableSet; // 导入Google Guava的ImmutableSet，创建不可变集合

import org.immutables.value.Value; // 导入Immutables库的Value注解，用于生成不可变值对象

import org.slf4j.Logger; // 导入SLF4J的Logger接口，用于日志记录

import java.util.ArrayList; // 导入ArrayList类，动态数组实现
import java.util.List; // 导入List接口，表示有序集合
import java.util.Set; // 导入Set接口，表示不重复元素的集合

/**
 * Planner rule to push filters and projections to Splunk.
 * 负责将过滤条件和投影操作下推到Splunk的优化规则，减少数据传输量，提高查询性能
 * 通过将Calcite的逻辑操作符转换为Splunk的搜索语言（SPL），实现查询下推
 */
@Value.Enclosing // 标记这是一个Value.Enclosing类，用于Immutables库生成不可变配置类
public class SplunkPushDownRule
    extends RelRule<SplunkPushDownRule.Config> { // 继承自RelRule，是Calcite优化规则的基础类
  // 日志记录器，用于记录规则执行过程中的调试信息
  private static final Logger LOGGER =
      StringUtils.getClassTracer(SplunkPushDownRule.class);

  // 支持下推到Splunk的SQL操作符集合，只有这些操作可以被转换为Splunk搜索语法
  private static final Set<SqlKind> SUPPORTED_OPS =
      ImmutableSet.of( // 使用Google Guava的不可变集合，确保线程安全和不可修改性
          SqlKind.CAST, // 类型转换操作，支持数据类型转换
          SqlKind.EQUALS, // 等于操作符 (=)
          SqlKind.LESS_THAN, // 小于操作符 (<)
          SqlKind.LESS_THAN_OR_EQUAL, // 小于等于操作符 (<=)
          SqlKind.GREATER_THAN, // 大于操作符 (>)
          SqlKind.GREATER_THAN_OR_EQUAL, // 大于等于操作符 (>=)
          SqlKind.NOT_EQUALS, // 不等于操作符 (!= 或 <>)
          SqlKind.LIKE, // 模糊匹配操作符 (LIKE)
          SqlKind.AND, // 逻辑与操作符 (AND)
          SqlKind.OR, // 逻辑或操作符 (OR)
          SqlKind.NOT); // 逻辑非操作符 (NOT)

  // 规则1：处理 Project -> Filter -> Project -> SplunkTableScan 的模式
  // 用于处理最复杂的场景：顶部投影 + 过滤 + 底部投影 + Splunk表扫描
  // 这种情况下需要将过滤条件和两个投影都下推到Splunk
  public static final SplunkPushDownRule PROJECT_ON_FILTER =
      ImmutableSplunkPushDownRule.Config.builder() // 使用Immutables构建器创建规则配置
          .withOperandSupplier(b0 -> // 定义操作数提供器，描述规则匹配的RelNode树结构
              b0.operand(LogicalProject.class).oneInput(b1 -> // 匹配顶部的LogicalProject节点
                  b1.operand(LogicalFilter.class).oneInput(b2 -> // 匹配LogicalFilter节点
                      b2.operand(LogicalProject.class).oneInput(b3 -> // 匹配底部的LogicalProject节点
                          b3.operand(SplunkTableScan.class).noInputs())))) // 匹配SplunkTableScan叶子节点
          .build() // 构建配置对象
          .withId("proj on filter on proj") // 设置规则唯一标识符
          .toRule(); // 将配置转换为规则实例

  // 规则2：处理 Filter -> Project -> SplunkTableScan 的模式
  // 用于处理过滤条件在顶部投影之上的场景
  public static final SplunkPushDownRule FILTER_ON_PROJECT =
      ImmutableSplunkPushDownRule.Config.builder()
          .withOperandSupplier(b0 ->
              b0.operand(LogicalFilter.class).oneInput(b1 -> // 匹配LogicalFilter节点
                  b1.operand(LogicalProject.class).oneInput(b2 -> // 匹配LogicalProject节点
                      b2.operand(SplunkTableScan.class).noInputs()))) // 匹配SplunkTableScan叶子节点
          .build()
          .withId("filter on proj")
          .toRule();

  // 规则3：处理 Filter -> SplunkTableScan 的简单模式
  // 用于处理只有过滤条件，没有投影的场景
  public static final SplunkPushDownRule FILTER =
      ImmutableSplunkPushDownRule.Config.builder()
          .withOperandSupplier(b0 ->
              b0.operand(LogicalFilter.class).oneInput(b1 -> // 匹配LogicalFilter节点
                  b1.operand(SplunkTableScan.class).noInputs())) // 匹配SplunkTableScan叶子节点
          .build()
          .withId("filter")
          .toRule();

  // 规则4：处理 Project -> SplunkTableScan 的简单模式
  // 用于处理只有投影操作，没有过滤条件的场景
  public static final SplunkPushDownRule PROJECT =
      ImmutableSplunkPushDownRule.Config.builder()
          .withOperandSupplier(b0 ->
              b0.operand(LogicalProject.class).oneInput(b1 -> // 匹配LogicalProject节点
                  b1.operand(SplunkTableScan.class).noInputs())) // 匹配SplunkTableScan叶子节点
          .build()
          .withId("proj")
          .toRule();

  /** Creates a SplunkPushDownRule. */
  // 构造方法：根据配置对象创建SplunkPushDownRule实例
  // 参数 config: 规则配置对象，包含操作数定义、RelBuilderFactory等信息
  protected SplunkPushDownRule(Config config) {
    super(config); // 调用父类RelRule的构造方法，初始化规则配置
  }

  @Deprecated // to be removed before 2.0 // 标记为已弃用，将在2.0版本前移除
  // 旧版构造方法：使用操作数和ID创建规则（已弃用，请使用Config构造方法）
  // 参数 operand: 规则的操作数定义，描述匹配的RelNode模式
  // 参数 id: 规则的唯一标识符
  protected SplunkPushDownRule(RelOptRuleOperand operand, String id) {
    this(ImmutableSplunkPushDownRule.Config.builder() // 使用构建器创建配置
        .withOperandSupplier(b -> b.exactly(operand)) // 设置操作数提供器
        .build() // 构建配置对象
        .withId(id)); // 设置规则ID
  }

  @Deprecated // to be removed before 2.0 // 标记为已弃用，将在2.0版本前移除
  // 旧版构造方法：使用操作数、RelBuilderFactory和ID创建规则（已弃用）
  // 参数 operand: 规则的操作数定义，描述匹配的RelNode模式
  // 参数 relBuilderFactory: RelNode构建工厂，用于创建新的RelNode
  // 参数 id: 规则的唯一标识符
  protected SplunkPushDownRule(RelOptRuleOperand operand,
      RelBuilderFactory relBuilderFactory, String id) {
    this(ImmutableSplunkPushDownRule.Config.builder() // 使用构建器创建配置
        .withOperandSupplier(b -> b.exactly(operand)) // 设置操作数提供器
        .withRelBuilderFactory(relBuilderFactory) // 设置RelNode构建工厂
        .build() // 构建配置对象
        .withId(id)); // 设置规则ID
  }

  // ~ Methods --------------------------------------------------------------

  // 当规则匹配成功时调用此方法，执行优化转换逻辑
  // 参数 call: 规则调用对象，包含匹配到的RelNode树和相关信息
  @Override public void onMatch(RelOptRuleCall call) {
    LOGGER.debug(description); // 记录规则描述信息到日志

    int relLength = call.rels.length; // 获取匹配到的RelNode数量
    SplunkTableScan splunkRel = // 从RelNode数组的最后一个元素获取SplunkTableScan（总是位于树的最底层）
        (SplunkTableScan) call.rels[relLength - 1];

    LogicalFilter filter; // 声明逻辑过滤器变量，用于存储匹配到的过滤节点
    LogicalProject topProj    = null; // 顶部投影节点（可能在过滤器之上），初始化为null
    LogicalProject bottomProj = null; // 底部投影节点（可能在过滤器之下），初始化为null


    RelDataType topRow = splunkRel.getRowType(); // 获取Splunk表的行类型（字段定义）

    int filterIdx = 2; // 初始化过滤器的索引位置，默认为2（从底部向上数）
    if (call.rels[relLength - 2] instanceof LogicalProject) { // 检查倒数第二个RelNode是否为LogicalProject
      bottomProj = (LogicalProject) call.rels[relLength - 2]; // 如果是，则将其赋值给底部投影
      filterIdx  = 3; // 更新过滤器索引为3，因为存在底部投影

      // bottom projection will change the field count/order // 底部投影注释：底部投影会改变字段的数量和顺序
      topRow =  bottomProj.getRowType(); // 使用底部投影的行类型更新topRow
    }

    String filterString; // 声明过滤字符串变量，用于存储转换后的Splunk搜索条件

    if (filterIdx <= relLength // 检查过滤器索引是否在有效范围内
        && call.rels[relLength - filterIdx] instanceof LogicalFilter) { // 检查对应位置是否为LogicalFilter
      filter = (LogicalFilter) call.rels[relLength - filterIdx]; // 获取过滤器节点

      int topProjIdx = filterIdx + 1; // 计算顶部投影的索引位置
      if (topProjIdx <= relLength // 检查顶部投影索引是否在有效范围内
          && call.rels[relLength - topProjIdx] instanceof LogicalProject) { // 检查对应位置是否为LogicalProject
        topProj = (LogicalProject) call.rels[relLength - topProjIdx]; // 如果是，则将其赋值给顶部投影
      }

      RexCall filterCall = (RexCall) filter.getCondition(); // 获取过滤条件的RexCall表达式
      SqlOperator op = filterCall.getOperator(); // 获取过滤条件的操作符（如=, >, <等）
      List<RexNode> operands = filterCall.getOperands(); // 获取过滤条件的操作数列表

      LOGGER.debug("fieldNames: {}", getFieldsString(topRow)); // 记录字段名到日志，便于调试

      final StringBuilder buf = new StringBuilder(); // 创建StringBuilder用于构建Splunk过滤字符串
      if (getFilter(op, operands, buf, topRow.getFieldNames())) { // 调用getFilter方法尝试将过滤条件转换为Splunk语法
        filterString = buf.toString(); // 如果转换成功，获取生成的过滤字符串
      } else {
        return; // can't handle // 如果转换失败，直接返回，不执行下推优化
      }
    } else {
      filterString = ""; // 如果不存在过滤器，将过滤字符串设为空
    }

    // top projection will change the field count/order // 顶部投影注释：顶部投影会改变字段的数量和顺序
    if (topProj != null) { // 检查是否存在顶部投影
      topRow =  topProj.getRowType(); // 如果存在，使用顶部投影的行类型更新topRow
    }
    LOGGER.debug("pre transformTo fieldNames: {}", getFieldsString(topRow)); // 记录转换前的字段名到日志

    call.transformTo( // 执行转换，用优化后的RelNode替换原始RelNode树
        appendSearchString( // 调用appendSearchString方法生成新的SplunkTableScan
            filterString, splunkRel, topProj, bottomProj, // 传入过滤字符串和各个RelNode
            topRow, null)); // 传入行类型，bottomRow传null
  }

  /**
   * Appends a search string.
   * 追加搜索字符串到Splunk查询中，并处理投影和字段重命名
   *
   * @param toAppend Search string to append // 要追加的搜索字符串（通常是过滤条件）
   * @param splunkRel Relational expression // Splunk表扫描RelNode
   * @param topProj Top projection // 顶部投影节点（可能包含字段重命名）
   * @param bottomProj Bottom projection // 底部投影节点（可能包含字段选择）
   */
  protected RelNode appendSearchString(
      String toAppend, // 要追加到Splunk搜索语句的过滤条件
      SplunkTableScan splunkRel, // 原始的Splunk表扫描节点
      LogicalProject topProj, // 顶部投影节点（用于处理字段重命名和重排序）
      LogicalProject bottomProj, // 底部投影节点（用于处理字段选择）
      RelDataType topRow, // 顶部投影后的行类型
      RelDataType bottomRow) { // 底部投影后的行类型（当前未使用）
    final RelOptCluster cluster = splunkRel.getCluster(); // 获取RelNode所在的集群，包含类型工厂等信息
    StringBuilder updateSearchStr = new StringBuilder(splunkRel.search); // 创建StringBuilder，初始化为原始搜索字符串

    if (!toAppend.isEmpty()) { // 检查是否有过滤条件需要追加
      updateSearchStr.append(" ").append(toAppend); // 如果有，追加空格和过滤条件到搜索字符串
    }
    List<RelDataTypeField> bottomFields = // 获取底部字段列表
        bottomRow == null ? null : bottomRow.getFieldList(); // 如果bottomRow为null，则bottomFields也为null
    List<RelDataTypeField> topFields    = // 获取顶部字段列表
        topRow    == null ? null : topRow.getFieldList(); // 如果topRow为null，则topFields也为null

    if (bottomFields == null) { // 如果底部字段列表为null
      bottomFields = splunkRel.getRowType().getFieldList(); // 使用Splunk表的原始字段列表
    }

    // handle bottom projection (ie choose a subset of the table fields) // 处理底部投影：选择表的字段子集
    if (bottomProj != null) { // 检查是否存在底部投影
      List<RelDataTypeField> tmp  = new ArrayList<>(); // 创建临时列表存储处理后的字段
      List<RelDataTypeField> dRow = bottomProj.getRowType().getFieldList(); // 获取底部投影的行类型字段列表
      for (RexNode rn : bottomProj.getProjects()) { // 遍历底部投影的每个投影表达式
        RelDataTypeField rdtf; // 声明字段变量
        if (rn instanceof RexSlot) { // 如果表达式是RexSlot（字段引用）
          RexSlot rs = (RexSlot) rn; // 强制转换为RexSlot
          rdtf = bottomFields.get(rs.getIndex()); // 根据索引从bottomFields获取对应的字段
        } else { // 如果表达式不是RexSlot（可能是常量或复杂表达式）
          rdtf = dRow.get(tmp.size()); // 从投影的行类型中按顺序获取字段
        }
        tmp.add(rdtf); // 将字段添加到临时列表
      }
      bottomFields = tmp; // 用处理后的字段列表更新bottomFields
    }

    // field renaming: to -> from // 字段重命名注释：从原名到新名的映射
    final PairList<String, String> renames = PairList.of(); // 创建键值对列表，存储字段重命名映射

    // handle top projection (ie reordering and renaming) // 处理顶部投影：字段重排序和重命名
    List<RelDataTypeField> newFields = bottomFields; // 初始化新字段列表为底部字段
    if (topProj != null) { // 检查是否存在顶部投影
      LOGGER.debug("topProj: {}", topProj.getPermutation()); // 记录顶部投影的排列信息到日志
      newFields = new ArrayList<>(); // 创建新的字段列表
      int i = 0; // 初始化索引计数器
      for (RexNode rn : topProj.getProjects()) { // 遍历顶部投影的每个投影表达式
        RexInputRef rif = (RexInputRef) rn; // 强制转换为RexInputRef（输入字段引用）
        RelDataTypeField field = bottomFields.get(rif.getIndex()); // 根据索引从bottomFields获取对应的字段
        if (!bottomFields.get(rif.getIndex()).getName() // 检查字段名是否发生了变化
            .equals(topFields.get(i).getName())) { // 比较底部字段名和顶部字段名
          renames.add(bottomFields.get(rif.getIndex()).getName(), // 如果不同，添加重命名映射
              topFields.get(i).getName()); // 映射：原名 -> 新名
          field = topFields.get(i); // 使用顶部字段定义（可能包含新的类型信息）
        }
        newFields.add(field); // 将字段添加到新字段列表
        i++; // 递增索引计数器
      }
    }

    if (!renames.isEmpty()) { // 检查是否有字段需要重命名
      updateSearchStr.append("| rename "); // 追加Splunk的rename命令
      renames.forEach((left, right) -> // 遍历所有重命名映射
          updateSearchStr.append(left).append(" AS ") // 添加原名和AS关键字
              .append(right).append(" ")); // 添加新名和空格
    }

    RelDataType resultType = // 创建结果行类型
        cluster.getTypeFactory().createStructType(newFields); // 使用类型工厂创建结构类型，包含新字段列表
    String searchWithFilter = updateSearchStr.toString(); // 将StringBuilder转换为最终的搜索字符串

    RelNode rel = // 创建新的SplunkTableScan节点
        new SplunkTableScan(
            cluster, // 传入集群
            splunkRel.getTable(), // 传入表定义
            splunkRel.splunkTable, // 传入Splunk表对象
            searchWithFilter, // 传入更新后的搜索字符串（包含过滤条件和重命名）
            splunkRel.earliest, // 传入时间范围：最早时间
            splunkRel.latest, // 传入时间范围：最晚时间
            resultType.getFieldNames()); // 传入结果字段名列表

    LOGGER.debug("end of appendSearchString fieldNames: {}", // 记录最终字段名到日志
        rel.getRowType().getFieldNames());
    return rel; // 返回新的SplunkTableScan节点
  }

  // ~ Private Methods ------------------------------------------------------

  @SuppressWarnings("unused") // 抑制未使用警告
  // 私有方法：添加投影规则到RelNode
  // 如果投影存在，则创建新的LogicalProject节点；否则直接返回原始RelNode
  // 参数 proj: 要应用的投影节点
  // 参数 rel: 输入RelNode
  // 返回值: 如果proj不为null则返回新的投影节点，否则返回原始rel
  private static RelNode addProjectionRule(LogicalProject proj, RelNode rel) {
    if (proj == null) { // 检查投影是否为null
      return rel; // 如果为null，直接返回原始RelNode
    }
    return LogicalProject.create(rel, proj.getHints(), // 创建新的LogicalProject节点，保留原始投影的所有属性
        proj.getProjects(), proj.getRowType(), proj.getVariablesSet());
  }

  // TODO: use StringBuilder instead of String // TODO注释：应该使用StringBuilder而不是String
  // TODO: refactor this to use more tree like parsing, need to also // TODO注释：需要重构为树形解析
  //      make sure we use parens properly - currently precedence // TODO注释：需要正确使用括号 - 当前优先级规则只是简单的从左到右
  //      rules are simply left to right
  // 私有方法：将RexNode过滤条件转换为Splunk搜索字符串
  // 参数 op: SQL操作符（如=, >, <, AND, OR等）
  // 参数 operands: 操作数列表（RexNode表达式）
  // 参数 s: StringBuilder用于构建Splunk搜索字符串
  // 参数 fieldNames: 字段名列表，用于将RexInputRef转换为字段名
  // 返回值: 如果转换成功返回true，否则返回false
  private static boolean getFilter(SqlOperator op, List<RexNode> operands,
      StringBuilder s, List<String> fieldNames) {
    if (!valid(op.getKind())) { // 检查操作符类型是否支持下推
      return false; // 如果不支持，返回false
    }

    boolean like = false; // 标记是否为LIKE操作
    switch (op.getKind()) { // 根据操作符类型进行不同处理
    case NOT: // 如果是NOT操作符
      // NOT op pre-pended // NOT操作符前置
      s.append(" NOT "); // 在字符串中追加" NOT "
      break; // 跳出switch
    case CAST: // 如果是类型转换操作符
      return asd(false, operands, s, fieldNames, 0); // 直接调用asd方法处理第一个操作数
    case LIKE: // 如果是LIKE操作符
      like = true; // 设置like标记为true
      break; // 跳出switch
    default: // 其他操作符
      break; // 不做特殊处理
    }

    for (int i = 0; i < operands.size(); i++) { // 遍历所有操作数
      if (!asd(like, operands, s, fieldNames, i)) { // 调用asd方法处理每个操作数
        return false; // 如果处理失败，返回false
      }
      if (op instanceof SqlBinaryOperator && i == 0) { // 如果是二元操作符且处理完第一个操作数
        s.append(" ").append(op).append(" "); // 追加操作符到字符串（如" = ", " > "等）
      }
    }
    return true; // 所有操作数处理成功，返回true
  }

  // 私有方法：处理单个操作数，将其转换为Splunk搜索语法
  // 参数 like: 是否为LIKE操作（影响字符串转义）
  // 参数 operands: 操作数列表
  // 参数 s: StringBuilder用于构建Splunk搜索字符串
  // 参数 fieldNames: 字段名列表
  // 参数 i: 当前操作数的索引
  // 返回值: 如果处理成功返回true，否则返回false
  private static boolean asd(boolean like, List<RexNode> operands, StringBuilder s,
      List<String> fieldNames, int i) {
    RexNode operand = operands.get(i); // 获取指定索引的操作数
    if (operand instanceof RexCall) { // 如果操作数是一个函数调用（如AND, OR等）
      s.append("("); // 添加左括号
      final RexCall call = (RexCall) operand; // 强制转换为RexCall
      boolean b = // 递归调用getFilter处理嵌套的函数调用
          getFilter(
              call.getOperator(), // 传入嵌调用的操作符
              call.getOperands(), // 传入嵌调用的操作数
              s, // 传入StringBuilder
              fieldNames); // 传入字段名列表
      if (!b) { // 如果嵌套调用失败
        return false; // 返回false
      }
      s.append(")"); // 添加右括号
    } else { // 如果操作数不是函数调用
      if (operand instanceof RexInputRef) { // 如果操作数是输入字段引用
        if (i != 0) { // 检查索引是否为0（字段引用必须在二元操作符的左侧）
          return false; // 如果不是左侧，返回false（不支持右侧字段引用）
        }
        int fieldIndex = ((RexInputRef) operand).getIndex(); // 获取字段的索引
        String name = fieldNames.get(fieldIndex); // 根据索引获取字段名
        s.append(name); // 将字段名追加到字符串
      } else { // RexLiteral // 如果操作数是字面量（常量值）
        String tmp = toString(like, (RexLiteral) operand); // 调用toString方法将字面量转换为字符串
        if (tmp == null) { // 检查转换是否成功
          return false; // 如果失败，返回false
        }
        s.append(tmp); // 将转换后的字符串追加到StringBuilder
      }
    }
    return true; // 操作数处理成功，返回true
  }

  // 私有方法：验证操作符类型是否支持下推到Splunk
  // 参数 kind: SQL操作符类型（如SqlKind.EQUALS, SqlKind.AND等）
  // 返回值: 如果操作符在支持集合中返回true，否则返回false
  private static boolean valid(SqlKind kind) {
    return SUPPORTED_OPS.contains(kind); // 检查操作符是否在SUPPORTED_OPS集合中
  }

  @SuppressWarnings("unused") // 抑制未使用警告
  // 私有方法：将SQL操作符转换为字符串表示
  // 参数 op: SQL操作符
  // 返回值: 操作符的字符串表示
  private static String toString(SqlOperator op) {
    if (op.equals(SqlStdOperatorTable.LIKE)) { // 如果是LIKE操作符
      return SqlStdOperatorTable.EQUALS.toString(); // 返回等号（Splunk中LIKE用=表示）
    } else if (op.equals(SqlStdOperatorTable.NOT_EQUALS)) { // 如果是不等于操作符
      return "!="; // 返回!=字符串
    }
    return op.toString(); // 返回操作符的默认字符串表示
  }

  // 公共静态方法：对Splunk搜索字符串进行转义处理
  // 参数 str: 需要转义的字符串
  // 返回值: 转义后的字符串，如果包含特殊字符则用双引号包裹
  public static String searchEscape(String str) {
    if (str.isEmpty()) { // 检查字符串是否为空
      return "\"\""; // 如果为空，返回空字符串的双引号形式
    }
    StringBuilder sb = new StringBuilder(str.length()); // 创建StringBuilder，初始容量为字符串长度
    boolean quote = false; // 标记是否需要用引号包裹

    for (int i = 0; i < str.length(); i++) { // 遍历字符串的每个字符
      char c = str.charAt(i); // 获取当前字符
      if (c == '"' || c == '\\') { // 如果字符是双引号或反斜杠
        sb.append('\\'); // 添加转义字符反斜杠
      }
      sb.append(c); // 添加当前字符

      quote |= !(Character.isLetterOrDigit(c) || c == '_'); // 如果字符不是字母、数字或下划线，标记需要引号
    }

    if (quote || sb.length() != str.length()) { // 如果需要引号或字符串长度发生变化（有转义字符）
      sb.insert(0, '"'); // 在开头插入双引号
      sb.append('"'); // 在末尾追加双引号
      return sb.toString(); // 返回带引号的字符串
    }
    return str; // 返回原始字符串
  }

  // 私有方法：将RexLiteral字面量转换为Splunk搜索字符串
  // 参数 like: 是否为LIKE操作（影响通配符转换）
  // 参数 literal: RexLiteral字面量对象
  // 返回值: 转换后的字符串，如果不支持的类型返回null
  private static String toString(boolean like, RexLiteral literal) {
    String value = null; // 初始化值为null
    SqlTypeName litSqlType = literal.getTypeName(); // 获取字面量的SQL类型名
    if (SqlTypeName.NUMERIC_TYPES.contains(litSqlType)) { // 如果是数值类型
      value = literal.getValue().toString(); // 直接转换为字符串
    } else if (litSqlType == SqlTypeName.CHAR) { // 如果是字符类型
      value = ((NlsString) literal.getValue()).getValue(); // 获取NlsString的值（处理国际化字符串）
      if (like) { // 如果是LIKE操作
        value = value.replace("%", "*"); // 将SQL的%通配符替换为Splunk的*通配符
      }
      value = searchEscape(value); // 对字符串进行转义处理
    }
    return value; // 返回转换后的字符串
  }

  // transform the call from SplunkUdxRel to FarragoJavaUdxRel // 注释：将SplunkUdxRel转换为FarragoJavaUdxRel
  // usually used to stop the optimizer from calling us // 注释：通常用于阻止优化器继续调用此规则
  // 保护方法：将SplunkTableScan转换为FarragoJavaUdxRel（当前未实现）
  // 参数 call: 规则调用对象
  // 参数 splunkRel: Splunk表扫描节点
  // 参数 filter: 过滤器节点
  // 参数 topProj: 顶部投影节点
  // 参数 bottomProj: 底部投影节点
  protected void transformToFarragoUdxRel(
      RelOptRuleCall call,
      SplunkTableScan splunkRel,
      LogicalFilter filter,
      LogicalProject topProj,
      LogicalProject bottomProj) {
    assert false; // 断言失败，此方法当前不应被调用
/*
    RelNode rel = // 注释代码：创建新的EnumerableTableScan节点
        new EnumerableTableScan(
            udxRel.getCluster(),
            udxRel.getTable(),
            udxRel.getRowType(),
            udxRel.getServerMofId());

    rel = RelOptUtil.createCastRel(rel, udxRel.getRowType(), true); // 注释代码：创建类型转换RelNode

    rel = addProjectionRule(bottomProj, rel); // 注释代码：应用底部投影

    if (filter != null) { // 注释代码：如果存在过滤器
      rel = // 注释代码：创建新的LogicalFilter节点
          new LogicalFilter(filter.getCluster(), rel, filter.getCondition());
    }

    rel = addProjectionRule(topProj, rel); // 注释代码：应用顶部投影

    call.transformTo(rel); // 注释代码：执行转换
*/
  }

  // 公共静态方法：获取RelDataType的字段名字符串表示
  // 参数 row: 行类型对象，包含字段信息
  // 返回值: 字段名列表的字符串表示（如[field1, field2, field3]）
  public static String getFieldsString(RelDataType row) {
    return row.getFieldNames().toString(); // 获取字段名列表并转换为字符串
  }

  /** Rule configuration. */ // 注释：规则配置接口
  // 配置接口：定义SplunkPushDownRule的配置，使用Immutables注解生成不可变配置类
  @Value.Immutable(singleton = false) // 标记为不可变值对象，不使用单例模式
  public interface Config extends RelRule.Config { // 继承自RelRule.Config接口
    @Override default SplunkPushDownRule toRule() { // 默认方法：将配置转换为规则实例
      return new SplunkPushDownRule(this); // 创建并返回新的SplunkPushDownRule实例
    }

    /** Defines an operand tree for the given classes. */ // 注释：为给定的类定义操作数树
    // 默认方法：为指定的RelNode类定义操作数
    // 参数 relClass: RelNode的子类
    // 返回值: 配置对象，设置了操作数提供器
    default Config withOperandFor(Class<? extends RelNode> relClass) {
      return withOperandSupplier(b -> b.operand(relClass).anyInputs()) // 设置操作数提供器，匹配任意输入
          .as(Config.class); // 转换为Config类型
    }

    // 默认方法：设置规则的ID和描述
    // 参数 id: 规则的唯一标识符
    // 返回值: 配置对象，设置了描述信息
    default Config withId(String id) {
      return withDescription("SplunkPushDownRule: " + id).as(Config.class); // 设置描述为规则名+ID
    }
  }
} // 类定义结束
