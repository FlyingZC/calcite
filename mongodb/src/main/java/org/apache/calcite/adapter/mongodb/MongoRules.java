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
package org.apache.calcite.adapter.mongodb;

import org.apache.calcite.adapter.enumerable.RexImpTable;
import org.apache.calcite.adapter.enumerable.RexToLixTranslator;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.InvalidRelException;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.convert.ConverterRule;
import org.apache.calcite.rel.core.Sort;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexVisitorImpl;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlValidatorUtil;
import org.apache.calcite.util.Bug;
import org.apache.calcite.util.Util;
import org.apache.calcite.util.trace.CalciteTrace;

import org.slf4j.Logger;

import java.util.AbstractList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rules and relational operators for
 * {@link MongoRel#CONVENTION MONGO}
 * calling convention.
 */
// MongoRules类：MongoDB适配器规则集合类，负责定义将Calcite逻辑算子转换为MongoDB物理算子的规则
// 这个类包含了MongoDB适配器的核心转换规则，用于将Calcite的逻辑关系表达式转换为MongoDB的调用约定
// 主要包含：排序规则、过滤规则、投影规则、聚合规则等转换器
// 这些规则是Calcite优化器在将SQL查询转换为MongoDB查询时使用的关键组件
public class MongoRules {
  private MongoRules() {} // 私有构造函数，防止实例化，这是一个工具类，只包含静态方法和规则定义

  protected static final Logger LOGGER = CalciteTrace.getPlannerTracer(); // 日志记录器，用于记录规划器相关的日志信息，使用Calcite的追踪器

  @SuppressWarnings("MutablePublicArray")
  public static final RelOptRule[] RULES = { // 规则数组，包含所有MongoDB转换规则，这些规则将被注册到优化器中用于转换逻辑算子
      MongoSortRule.INSTANCE, // 排序转换规则实例，将逻辑Sort转换为MongoSort
      MongoFilterRule.INSTANCE, // 过滤转换规则实例，将逻辑Filter转换为MongoFilter
      MongoProjectRule.INSTANCE, // 投影转换规则实例，将逻辑Project转换为MongoProject
      MongoAggregateRule.INSTANCE, // 聚合转换规则实例，将逻辑Aggregate转换为MongoAggregate
  };

  /** Returns 'string' if it is a call to item['string'], null otherwise. */
  static String isItem(RexCall call) { // 静态方法：判断RexCall是否为item['string']形式的访问，如果是则返回字段名，否则返回null
    if (call.getOperator() != SqlStdOperatorTable.ITEM) { // 检查操作符是否为ITEM操作符（用于访问数组或对象的元素）
      return null; // 不是ITEM操作符，返回null
    }
    final RexNode op0 = call.operands.get(0); // 获取ITEM操作符的第一个操作数（通常是输入引用）
    final RexNode op1 = call.operands.get(1); // 获取ITEM操作符的第二个操作数（通常是字段名或索引）
    if (op0 instanceof RexInputRef // 检查第一个操作数是否为输入引用
        && ((RexInputRef) op0).getIndex() == 0 // 检查输入引用的索引是否为0（表示根对象）
        && op1 instanceof RexLiteral // 检查第二个操作数是否为字面量
        && ((RexLiteral) op1).getValue2() instanceof String) { // 检查字面量的值是否为字符串类型
      return (String) ((RexLiteral) op1).getValue2(); // 返回字符串字段名，用于MongoDB字段访问
    }
    return null; // 不符合item['string']模式，返回null
  }

  static List<String> mongoFieldNames(final RelDataType rowType) { // 静态方法：根据关系数据类型生成MongoDB字段名列表，处理字段名冲突和特殊字符
    return SqlValidatorUtil.uniquify( // 使用SqlValidatorUtil工具类确保字段名唯一
        new AbstractList<String>() { // 创建一个匿名抽象列表，用于懒加载字段名
          @Override public String get(int index) { // 重写get方法，根据索引获取字段名
            final String name = rowType.getFieldList().get(index).getName(); // 从行类型中获取指定索引的字段名
            return name.startsWith("$") ? "_" + name.substring(2) : name; // 如果字段名以$开头（MongoDB保留字符），则替换为_开头，否则保持原样
          }

          @Override public int size() { // 重写size方法，返回字段总数
            return rowType.getFieldCount(); // 返回行类型的字段数量
          }
        },
        SqlValidatorUtil.EXPR_SUGGESTER, true); // EXPR_SUGGESTER用于生成唯一的字段名，true表示使用数字后缀
  }

  static String maybeQuote(String s) { // 静态方法：根据字符串内容决定是否需要添加引号，用于MongoDB字段名或值的引用
    if (!needsQuote(s)) { // 检查字符串是否需要引号
      return s; // 不需要引号，直接返回原字符串
    }
    return quote(s); // 需要引号，调用quote方法添加引号
  }

  static String quote(String s) { // 静态方法：为字符串添加单引号，用于MongoDB表达式中引用字符串
    return "'" + s + "'"; // TODO: handle embedded quotes // 返回用单引号包裹的字符串，TODO注释表示需要处理字符串中嵌入的引号
  }

  private static boolean needsQuote(String s) { // 私有静态方法：判断字符串是否需要引号
    for (int i = 0, n = s.length(); i < n; i++) { // 遍历字符串的每个字符
      char c = s.charAt(i); // 获取当前位置的字符
      if (!Character.isJavaIdentifierPart(c) // 检查字符是否不是有效的Java标识符部分（如空格、特殊符号等）
          || c == '$') { // 检查字符是否为$（MongoDB的保留字符）
        return true; // 需要引号，返回true
      }
    }
    return false; // 所有字符都是有效的标识符部分，不需要引号，返回false
  }

  /** Translator from {@link RexNode} to strings in MongoDB's expression
   * language. */
  static class RexToMongoTranslator extends RexVisitorImpl<String> { // 内部静态类：RexNode到MongoDB表达式语言的转换器，继承自RexVisitorImpl
    private final JavaTypeFactory typeFactory; // Java类型工厂，用于处理Java类型到MongoDB类型的转换
    private final List<String> inFields; // 输入字段名列表，用于将输入引用映射到MongoDB字段名

    private static final Map<SqlOperator, String> MONGO_OPERATORS = // 静态映射表：存储Calcite SQL操作符到MongoDB操作符的映射关系
        new HashMap<>();

    static { // 静态初始化块：初始化操作符映射表，将Calcite的标准操作符映射到对应的MongoDB操作符
      // Arithmetic // 算术运算符映射
      MONGO_OPERATORS.put(SqlStdOperatorTable.DIVIDE, "$divide"); // 除法：Calcite的DIVIDE映射到MongoDB的$divide
      MONGO_OPERATORS.put(SqlStdOperatorTable.MULTIPLY, "$multiply"); // 乘法：Calcite的MULTIPLY映射到MongoDB的$multiply
      MONGO_OPERATORS.put(SqlStdOperatorTable.MOD, "$mod"); // 取模：Calcite的MOD映射到MongoDB的$mod
      MONGO_OPERATORS.put(SqlStdOperatorTable.PLUS, "$add"); // 加法：Calcite的PLUS映射到MongoDB的$add
      MONGO_OPERATORS.put(SqlStdOperatorTable.MINUS, "$subtract"); // 减法：Calcite的MINUS映射到MongoDB的$subtract
      // Boolean // 布尔运算符映射
      MONGO_OPERATORS.put(SqlStdOperatorTable.AND, "$and"); // 逻辑与：Calcite的AND映射到MongoDB的$and
      MONGO_OPERATORS.put(SqlStdOperatorTable.OR, "$or"); // 逻辑或：Calcite的OR映射到MongoDB的$or
      MONGO_OPERATORS.put(SqlStdOperatorTable.NOT, "$not"); // 逻辑非：Calcite的NOT映射到MongoDB的$not
      // Comparison // 比较运算符映射
      MONGO_OPERATORS.put(SqlStdOperatorTable.EQUALS, "$eq"); // 等于：Calcite的EQUALS映射到MongoDB的$eq
      MONGO_OPERATORS.put(SqlStdOperatorTable.NOT_EQUALS, "$ne"); // 不等于：Calcite的NOT_EQUALS映射到MongoDB的$ne
      MONGO_OPERATORS.put(SqlStdOperatorTable.GREATER_THAN, "$gt"); // 大于：Calcite的GREATER_THAN映射到MongoDB的$gt
      MONGO_OPERATORS.put(SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, "$gte"); // 大于等于：Calcite的GREATER_THAN_OR_EQUAL映射到MongoDB的$gte
      MONGO_OPERATORS.put(SqlStdOperatorTable.LESS_THAN, "$lt"); // 小于：Calcite的LESS_THAN映射到MongoDB的$lt
      MONGO_OPERATORS.put(SqlStdOperatorTable.LESS_THAN_OR_EQUAL, "$lte"); // 小于等于：Calcite的LESS_THAN_OR_EQUAL映射到MongoDB的$lte
    }

    protected RexToMongoTranslator(JavaTypeFactory typeFactory, // 构造函数：初始化RexNode到MongoDB表达式的转换器
        List<String> inFields) { // 参数：typeFactory是Java类型工厂，inFields是输入字段名列表
      super(true); // 调用父类构造函数，true表示深度优先遍历
      this.typeFactory = typeFactory; // 保存类型工厂引用，用于类型转换
      this.inFields = inFields; // 保存输入字段名列表，用于将输入引用映射到MongoDB字段
    }

    @Override public String visitLiteral(RexLiteral literal) { // 重写visitLiteral方法：访问字面量节点，将其转换为MongoDB字面量表达式
      if (literal.getValue() == null) { // 检查字面量值是否为null
        return "null"; // 返回MongoDB的null值
      }
      return "{$literal: " // 返回MongoDB的字面量表达式，使用$literal操作符确保值被当作字面量处理
          + RexToLixTranslator.translateLiteral(literal, literal.getType(), // 使用RexToLixTranslator将字面量转换为Java表达式
              typeFactory, RexImpTable.NullAs.NOT_POSSIBLE) // 传入类型工厂和null处理策略（NOT_POSSIBLE表示不可能为null）
          + "}"; // 闭合MongoDB表达式
    }

    @Override public String visitInputRef(RexInputRef inputRef) { // 重写visitInputRef方法：访问输入引用节点，将其转换为MongoDB字段引用
      return maybeQuote( // 根据需要决定是否添加引号
          "$" + inFields.get(inputRef.getIndex())); // 获取输入引用索引对应的字段名，并添加$前缀（MongoDB字段引用语法）
    }

    @Override public String visitCall(RexCall call) { // 重写visitCall方法：访问函数调用节点，将其转换为MongoDB表达式
      String name = isItem(call); // 检查是否为item['string']形式的字段访问
      if (name != null) { // 如果是字段访问
        return "'$" + name + "'"; // 返回MongoDB字段引用格式，如'$fieldName'
      }
      final List<String> strings = visitList(call.operands); // 递归访问所有操作数，转换为MongoDB表达式字符串列表
      if (call.getKind() == SqlKind.CAST) { // 检查是否为类型转换操作
        return strings.get(0); // MongoDB是动态类型系统，类型转换可以忽略，直接返回转换后的表达式
      }
      String stdOperator = MONGO_OPERATORS.get(call.getOperator()); // 从映射表中获取对应的MongoDB操作符
      if (stdOperator != null) { // 如果找到对应的MongoDB操作符
        return "{" + stdOperator + ": [" + Util.commaList(strings) + "]}"; // 返回MongoDB操作符表达式，如{$add: [1, 2]}
      }
      if (call.getOperator() == SqlStdOperatorTable.ITEM) { // 检查是否为ITEM操作符（数组或对象访问）
        final RexNode op1 = call.operands.get(1); // 获取第二个操作数（索引或字段名）
        if (op1 instanceof RexLiteral // 检查第二个操作数是否为字面量
            && op1.getType().getSqlTypeName() == SqlTypeName.INTEGER) { // 检查字面量类型是否为整数（数组索引）
          if (!Bug.CALCITE_194_FIXED) { // 检查CALCITE-194 bug是否已修复（关于数组索引处理的bug）
            return "'" + stripQuotes(strings.get(0)) + "[" // 返回带引号的数组访问表达式，如'$array[0]'
                + ((RexLiteral) op1).getValue2() + "]'";
          }
          return strings.get(0) + "[" + strings.get(1) + "]"; // 返回数组访问表达式，如$array[0]
        }
      }
      if (call.getOperator() == SqlStdOperatorTable.CASE) { // 检查是否为CASE操作符（条件表达式）
        StringBuilder sb = new StringBuilder(); // 创建字符串构建器用于构建MongoDB表达式
        StringBuilder finish = new StringBuilder(); // 创建字符串构建器用于存储闭合括号
        // case(a, b, c)  -> $cond:[a, b, c] // CASE表达式转换为MongoDB的$cond操作符的示例
        // case(a, b, c, d) -> $cond:[a, b, $cond:[c, d, null]] // 嵌套CASE表达式转换示例
        // case(a, b, c, d, e) -> $cond:[a, b, $cond:[c, d, e]] // 多个WHEN-THEN对转换示例
        for (int i = 0; i < strings.size(); i += 2) { // 遍历操作数，每次步进2（WHEN-THEN对）
          sb.append("{$cond:["); // 添加MongoDB条件操作符的开始标记
          finish.append("]}"); // 添加闭合标记到finish构建器

          sb.append(strings.get(i)); // 添加条件表达式（WHEN部分）
          sb.append(','); // 添加逗号分隔符
          sb.append(strings.get(i + 1)); // 添加结果表达式（THEN部分）
          sb.append(','); // 添加逗号分隔符
          if (i == strings.size() - 3) { // 检查是否为最后一个WHEN-THEN-ELSE情况
            sb.append(strings.get(i + 2)); // 添加ELSE部分
            break; // 跳出循环
          }
          if (i == strings.size() - 2) { // 检查是否为最后一个WHEN-THEN情况（无ELSE）
            sb.append("null"); // 添加null作为默认值
            break; // 跳出循环
          }
        }
        sb.append(finish); // 添加所有闭合括号
        return sb.toString(); // 返回构建好的MongoDB CASE表达式
      }
      throw new IllegalArgumentException("Translation of " + call.toString() // 抛出异常：不支持的操作符转换
          + " is not supported by MongoProject"); // 异常消息说明该操作符在MongoProject中不被支持
    }

    private static String stripQuotes(String s) { // 私有静态方法：移除字符串两端的引号
      return s.startsWith("'") && s.endsWith("'") // 检查字符串是否以单引号开头和结尾
          ? s.substring(1, s.length() - 1) // 如果是，则移除首尾的引号，返回中间部分
          : s; // 如果不是，则直接返回原字符串
    }
  }

  /** Base class for planner rules that convert a relational expression to
   * MongoDB calling convention. */
  abstract static class MongoConverterRule extends ConverterRule { // 抽象静态类：MongoDB转换规则的基类，继承自ConverterRule
    protected MongoConverterRule(Config config) { // 构造函数：初始化MongoDB转换规则
      super(config); // 调用父类ConverterRule的构造函数，传入配置对象
    }
  }

  /**
   * Rule to convert a {@link org.apache.calcite.rel.core.Sort} to a
   * {@link MongoSort}.
   */
  private static class MongoSortRule extends MongoConverterRule { // 私有静态类：排序转换规则，将逻辑Sort转换为MongoSort
    static final MongoSortRule INSTANCE = Config.INSTANCE // 单例实例：使用Builder模式创建规则实例
        .withConversion(Sort.class, Convention.NONE, MongoRel.CONVENTION, // 配置转换：从Sort类、NONE约定转换为MongoDB约定
            "MongoSortRule") // 规则名称
        .withRuleFactory(MongoSortRule::new) // 规则工厂：使用构造函数引用创建规则实例
        .toRule(MongoSortRule.class); // 生成最终的规则对象

    MongoSortRule(Config config) { // 构造函数：初始化排序转换规则
      super(config); // 调用父类MongoConverterRule的构造函数
    }

    @Override public RelNode convert(RelNode rel) { // 重写convert方法：执行从逻辑Sort到MongoSort的转换
      final Sort sort = (Sort) rel; // 将输入关系节点转换为Sort类型
      final RelTraitSet traitSet = // 构建新的特征集
          sort.getTraitSet().replace(out) // 将约定替换为MongoDB约定
              .replace(sort.getCollation()); // 替换排序规则
      return new MongoSort(rel.getCluster(), traitSet, // 创建MongoSort节点
          convert(sort.getInput(), traitSet.replace(RelCollations.EMPTY)), // 递归转换输入节点，移除排序规则（避免重复排序）
          sort.getCollation(), sort.offset, sort.fetch); // 传递排序规则、偏移量和获取限制
    }
  }

  /**
   * Rule to convert a {@link org.apache.calcite.rel.logical.LogicalFilter} to a
   * {@link MongoFilter}.
   */
  private static class MongoFilterRule extends MongoConverterRule { // 私有静态类：过滤转换规则，将逻辑Filter转换为MongoFilter
    static final MongoFilterRule INSTANCE = Config.INSTANCE // 单例实例：使用Builder模式创建规则实例
        .withConversion(LogicalFilter.class, Convention.NONE, // 配置转换：从LogicalFilter类、NONE约定
            MongoRel.CONVENTION, "MongoFilterRule") // 转换为MongoDB约定，规则名称为MongoFilterRule
        .withRuleFactory(MongoFilterRule::new) // 规则工厂：使用构造函数引用创建规则实例
        .toRule(MongoFilterRule.class); // 生成最终的规则对象

    MongoFilterRule(Config config) { // 构造函数：初始化过滤转换规则
      super(config); // 调用父类MongoConverterRule的构造函数
    }

    @Override public RelNode convert(RelNode rel) { // 重写convert方法：执行从逻辑Filter到MongoFilter的转换
      final LogicalFilter filter = (LogicalFilter) rel; // 将输入关系节点转换为LogicalFilter类型
      final RelTraitSet traitSet = filter.getTraitSet().replace(out); // 构建新的特征集，将约定替换为MongoDB约定
      return new MongoFilter( // 创建MongoFilter节点
          rel.getCluster(), // 传入集群信息
          traitSet, // 传入特征集
          convert(filter.getInput(), out), // 递归转换输入节点为MongoDB约定
          filter.getCondition()); // 传递过滤条件
    }
  }

  /**
   * Rule to convert a {@link org.apache.calcite.rel.logical.LogicalProject}
   * to a {@link MongoProject}.
   */
  private static class MongoProjectRule extends MongoConverterRule { // 私有静态类：投影转换规则，将逻辑Project转换为MongoProject
    static final MongoProjectRule INSTANCE = Config.INSTANCE // 单例实例：使用Builder模式创建规则实例
        .withConversion(LogicalProject.class, Convention.NONE, // 配置转换：从LogicalProject类、NONE约定
            MongoRel.CONVENTION, "MongoProjectRule") // 转换为MongoDB约定，规则名称为MongoProjectRule
        .withRuleFactory(MongoProjectRule::new) // 规则工厂：使用构造函数引用创建规则实例
        .toRule(MongoProjectRule.class); // 生成最终的规则对象

    MongoProjectRule(Config config) { // 构造函数：初始化投影转换规则
      super(config); // 调用父类MongoConverterRule的构造函数
    }

    @Override public boolean matches(RelOptRuleCall call) { // 重写matches方法：检查规则是否可以应用
      final LogicalProject project = call.rel(0); // 获取调用关系中的第一个关系节点（LogicalProject）
      return project.getVariablesSet().isEmpty(); // 检查Project是否包含变量引用（如窗口函数），如果为空则可以转换
    }

    @Override public RelNode convert(RelNode rel) { // 重写convert方法：执行从逻辑Project到MongoProject的转换
      final LogicalProject project = (LogicalProject) rel; // 将输入关系节点转换为LogicalProject类型
      final RelTraitSet traitSet = project.getTraitSet().replace(out); // 构建新的特征集，将约定替换为MongoDB约定
      return new MongoProject(project.getCluster(), traitSet, // 创建MongoProject节点
          convert(project.getInput(), out), // 递归转换输入节点为MongoDB约定
          project.getProjects(), // 传递投影表达式列表
          project.getRowType()); // 传递输出行类型
    }
  }

/*

  /**
   * Rule to convert a {@link LogicalCalc} to an
   * {@link MongoCalcRel}.
   o/
  private static class MongoCalcRule
      extends MongoConverterRule {
    private MongoCalcRule(MongoConvention out) {
      super(
          LogicalCalc.class,
          Convention.NONE,
          out,
          "MongoCalcRule");
    }

    public RelNode convert(RelNode rel) {
      final LogicalCalc calc = (LogicalCalc) rel;

      // If there's a multiset, let FarragoMultisetSplitter work on it
      // first.
      if (RexMultisetUtil.containsMultiset(calc.getProgram())) {
        return null;
      }

      return new MongoCalcRel(
          rel.getCluster(),
          rel.getTraitSet().replace(out),
          convert(
              calc.getChild(),
              calc.getTraitSet().replace(out)),
          calc.getProgram(),
          Project.Flags.Boxed);
    }
  }

  public static class MongoCalcRel extends SingleRel implements MongoRel {
    private final RexProgram program;

    /**
     * Values defined in {@link org.apache.calcite.rel.core.Project.Flags}.
     o/
    protected int flags;

    public MongoCalcRel(
        RelOptCluster cluster,
        RelTraitSet traitSet,
        RelNode child,
        RexProgram program,
        int flags) {
      super(cluster, traitSet, child);
      assert getConvention() instanceof MongoConvention;
      this.flags = flags;
      this.program = program;
      this.rowType = program.getOutputRowType();
    }

    public RelOptPlanWriter explainTerms(RelOptPlanWriter pw) {
      return program.explainCalc(super.explainTerms(pw));
    }

    public double getRows() {
      return LogicalFilter.estimateFilteredRows(
          getChild(), program);
    }

    public RelOptCost computeSelfCost(RelOptPlanner planner) {
      double dRows = RelMetadataQuery.getRowCount(this);
      double dCpu =
          RelMetadataQuery.getRowCount(getChild())
              * program.getExprCount();
      double dIo = 0;
      return planner.makeCost(dRows, dCpu, dIo);
    }

    public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
      return new MongoCalcRel(
          getCluster(),
          traitSet,
          sole(inputs),
          program.copy(),
          getFlags());
    }

    public int getFlags() {
      return flags;
    }

    public RexProgram getProgram() {
      return program;
    }

    public SqlString implement(MongoImplementor implementor) {
      final SqlBuilder buf = new SqlBuilder(implementor.dialect);
      buf.append("SELECT ");
      if (isStar(program)) {
        buf.append("*");
      } else {
        for (Ord<RexLocalRef> ref : Ord.zip(program.getProjectList())) {
          buf.append(ref.i == 0 ? "" : ", ");
          expr(buf, program, ref.e);
          alias(buf, null, getRowType().getFieldNames().get(ref.i));
        }
      }
      implementor.newline(buf)
          .append("FROM ");
      implementor.subQuery(buf, 0, getChild(), "t");
      if (program.getCondition() != null) {
        implementor.newline(buf);
        buf.append("WHERE ");
        expr(buf, program, program.getCondition());
      }
      return buf.toSqlString();
    }

    private static boolean isStar(RexProgram program) {
      int i = 0;
      for (RexLocalRef ref : program.getProjectList()) {
        if (ref.getIndex() != i++) {
          return false;
        }
      }
      return i == program.getInputRowType().getFieldCount();
    }

    private static void expr(
        SqlBuilder buf, RexProgram program, RexNode rex) {
      if (rex instanceof RexLocalRef) {
        final int index = ((RexLocalRef) rex).getIndex();
        expr(buf, program, program.getExprList().get(index));
      } else if (rex instanceof RexInputRef) {
        buf.identifier(
            program.getInputRowType().getFieldNames().get(
                ((RexInputRef) rex).getIndex()));
      } else if (rex instanceof RexLiteral) {
        toSql(buf, (RexLiteral) rex);
      } else if (rex instanceof RexCall) {
        final RexCall call = (RexCall) rex;
        switch (call.getOperator().getSyntax()) {
        case Binary:
          expr(buf, program, call.getOperands().get(0));
          buf.append(' ')
              .append(call.getOperator().toString())
              .append(' ');
          expr(buf, program, call.getOperands().get(1));
          break;
        default:
          throw new AssertionError(call.getOperator());
        }
      } else {
        throw new AssertionError(rex);
      }
    }
  }

  private static SqlBuilder toSql(SqlBuilder buf, RexLiteral rex) {
    switch (rex.getTypeName()) {
    case CHAR:
    case VARCHAR:
      return buf.append(
          new NlsString(rex.getValue2().toString(), null, null)
              .asSql(false, false));
    default:
      return buf.append(rex.getValue2().toString());
    }
  }

*/

  /**
   * Rule to convert an {@link org.apache.calcite.rel.logical.LogicalAggregate}
   * to an {@link MongoAggregate}.
   */
  private static class MongoAggregateRule extends MongoConverterRule { // 私有静态类：聚合转换规则，将逻辑Aggregate转换为MongoAggregate
    static final MongoAggregateRule INSTANCE = Config.INSTANCE // 单例实例：使用Builder模式创建规则实例
        .withConversion(LogicalAggregate.class, Convention.NONE, // 配置转换：从LogicalAggregate类、NONE约定
            MongoRel.CONVENTION, "MongoAggregateRule") // 转换为MongoDB约定，规则名称为MongoAggregateRule
        .withRuleFactory(MongoAggregateRule::new) // 规则工厂：使用构造函数引用创建规则实例
        .toRule(MongoAggregateRule.class); // 生成最终的规则对象

    MongoAggregateRule(Config config) { // 构造函数：初始化聚合转换规则
      super(config); // 调用父类MongoConverterRule的构造函数
    }

    @Override public RelNode convert(RelNode rel) { // 重写convert方法：执行从逻辑Aggregate到MongoAggregate的转换
      final LogicalAggregate agg = (LogicalAggregate) rel; // 将输入关系节点转换为LogicalAggregate类型
      final RelTraitSet traitSet = // 构建新的特征集
          agg.getTraitSet().replace(out); // 将约定替换为MongoDB约定
      try {
        return new MongoAggregate( // 创建MongoAggregate节点
            rel.getCluster(), // 传入集群信息
            traitSet, // 传入特征集
            convert(agg.getInput(), traitSet.simplify()), // 递归转换输入节点，并简化特征集
            agg.getGroupSet(), // 传递分组字段集合
            agg.getGroupSets(), // 传递分组集合列表（用于多级分组）
            agg.getAggCallList()); // 传递聚合函数调用列表
      } catch (InvalidRelException e) { // 捕获无效关系异常（当聚合不支持转换为MongoDB时抛出）
        LOGGER.warn(e.toString()); // 记录警告日志
        return null; // 返回null表示转换失败
      }
    }
  }

/*
  /**
   * Rule to convert an {@link org.apache.calcite.rel.logical.Union} to a
   * {@link MongoUnionRel}.
   o/
  private static class MongoUnionRule
      extends MongoConverterRule {
    private MongoUnionRule(MongoConvention out) {
      super(
          Union.class,
          Convention.NONE,
          out,
          "MongoUnionRule");
    }

    public RelNode convert(RelNode rel) {
      final Union union = (Union) rel;
      final RelTraitSet traitSet =
          union.getTraitSet().replace(out);
      return new MongoUnionRel(
          rel.getCluster(),
          traitSet,
          convertList(union.getInputs(), traitSet),
          union.all);
    }
  }

  public static class MongoUnionRel
      extends Union
      implements MongoRel {
    public MongoUnionRel(
        RelOptCluster cluster,
        RelTraitSet traitSet,
        List<RelNode> inputs,
        boolean all) {
      super(cluster, traitSet, inputs, all);
    }

    public MongoUnionRel copy(
        RelTraitSet traitSet, List<RelNode> inputs, boolean all) {
      return new MongoUnionRel(getCluster(), traitSet, inputs, all);
    }

    @Override public RelOptCost computeSelfCost(RelOptPlanner planner) {
      return super.computeSelfCost(planner).multiplyBy(.1);
    }

    public SqlString implement(MongoImplementor implementor) {
      return setOpSql(this, implementor, "UNION");
    }
  }

  private static SqlString setOpSql(
      SetOp setOpRel, MongoImplementor implementor, String op) {
    final SqlBuilder buf = new SqlBuilder(implementor.dialect);
    for (Ord<RelNode> input : Ord.zip(setOpRel.getInputs())) {
      if (input.i > 0) {
        implementor.newline(buf)
            .append(op + (setOpRel.all ? " ALL " : ""));
        implementor.newline(buf);
      }
      buf.append(implementor.visitChild(input.i, input.e));
    }
    return buf.toSqlString();
  }

  /**
   * Rule to convert an {@link org.apache.calcite.rel.logical.LogicalIntersect}
   * to an {@link MongoIntersectRel}.
   o/
  private static class MongoIntersectRule
      extends MongoConverterRule {
    private MongoIntersectRule(MongoConvention out) {
      super(
          LogicalIntersect.class,
          Convention.NONE,
          out,
          "MongoIntersectRule");
    }

    public RelNode convert(RelNode rel) {
      final LogicalIntersect intersect = (LogicalIntersect) rel;
      if (intersect.all) {
        return null; // INTERSECT ALL not implemented
      }
      final RelTraitSet traitSet =
          intersect.getTraitSet().replace(out);
      return new MongoIntersectRel(
          rel.getCluster(),
          traitSet,
          convertList(intersect.getInputs(), traitSet),
          intersect.all);
    }
  }

  public static class MongoIntersectRel
      extends Intersect
      implements MongoRel {
    public MongoIntersectRel(
        RelOptCluster cluster,
        RelTraitSet traitSet,
        List<RelNode> inputs,
        boolean all) {
      super(cluster, traitSet, inputs, all);
      assert !all;
    }

    public MongoIntersectRel copy(
        RelTraitSet traitSet, List<RelNode> inputs, boolean all) {
      return new MongoIntersectRel(getCluster(), traitSet, inputs, all);
    }

    public SqlString implement(MongoImplementor implementor) {
      return setOpSql(this, implementor, " intersect ");
    }
  }

  /**
   * Rule to convert an {@link org.apache.calcite.rel.logical.LogicalMinus}
   * to an {@link MongoMinusRel}.
   o/
  private static class MongoMinusRule
      extends MongoConverterRule {
    private MongoMinusRule(MongoConvention out) {
      super(
          LogicalMinus.class,
          Convention.NONE,
          out,
          "MongoMinusRule");
    }

    public RelNode convert(RelNode rel) {
      final LogicalMinus minus = (LogicalMinus) rel;
      if (minus.all) {
        return null; // EXCEPT ALL not implemented
      }
      final RelTraitSet traitSet =
          rel.getTraitSet().replace(out);
      return new MongoMinusRel(
          rel.getCluster(),
          traitSet,
          convertList(minus.getInputs(), traitSet),
          minus.all);
    }
  }

  public static class MongoMinusRel
      extends Minus
      implements MongoRel {
    public MongoMinusRel(
        RelOptCluster cluster,
        RelTraitSet traitSet,
        List<RelNode> inputs,
        boolean all) {
      super(cluster, traitSet, inputs, all);
      assert !all;
    }

    public MongoMinusRel copy(
        RelTraitSet traitSet, List<RelNode> inputs, boolean all) {
      return new MongoMinusRel(getCluster(), traitSet, inputs, all);
    }

    public SqlString implement(MongoImplementor implementor) {
      return setOpSql(this, implementor, " minus ");
    }
  }

  public static class MongoValuesRule extends MongoConverterRule {
    private MongoValuesRule(MongoConvention out) {
      super(
          LogicalValues.class,
          Convention.NONE,
          out,
          "MongoValuesRule");
    }

    @Override public RelNode convert(RelNode rel) {
      LogicalValues valuesRel = (LogicalValues) rel;
      return new MongoValuesRel(
          valuesRel.getCluster(),
          valuesRel.getRowType(),
          valuesRel.getTuples(),
          valuesRel.getTraitSet().plus(out));
    }
  }

  public static class MongoValuesRel
      extends Values
      implements MongoRel {
    MongoValuesRel(
        RelOptCluster cluster,
        RelDataType rowType,
        List<List<RexLiteral>> tuples,
        RelTraitSet traitSet) {
      super(cluster, rowType, tuples, traitSet);
    }

    @Override public RelNode copy(
        RelTraitSet traitSet, List<RelNode> inputs) {
      assert inputs.isEmpty();
      return new MongoValuesRel(
          getCluster(), rowType, tuples, traitSet);
    }

    public SqlString implement(MongoImplementor implementor) {
      throw new AssertionError(); // TODO:
    }
  }
*/
}
