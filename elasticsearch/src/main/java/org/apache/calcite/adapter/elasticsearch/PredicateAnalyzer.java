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
 */ // Apache许可证头，声明代码的版权和使用许可
package org.apache.calcite.adapter.elasticsearch; // 声明包名，表示这个类属于Calcite的Elasticsearch适配器模块

import org.apache.calcite.adapter.elasticsearch.QueryBuilders.BoolQueryBuilder; // 导入布尔查询构建器
import org.apache.calcite.adapter.elasticsearch.QueryBuilders.QueryBuilder; // 导入查询构建器基类
import org.apache.calcite.adapter.elasticsearch.QueryBuilders.RangeQueryBuilder; // 导入范围查询构建器
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型
import org.apache.calcite.rex.RexCall; // 导入Rex调用表达式，表示函数调用
import org.apache.calcite.rex.RexInputRef; // 导入Rex输入引用，表示对输入字段的引用
import org.apache.calcite.rex.RexLiteral; // 导入Rex字面量，表示常量值
import org.apache.calcite.rex.RexNode; // 导入Rex节点基类，表示关系表达式树的节点
import org.apache.calcite.rex.RexVisitorImpl; // 导入Rex访问者接口实现，用于遍历表达式树
import org.apache.calcite.sql.SqlKind; // 导入SQL操作符类型枚举
import org.apache.calcite.sql.SqlSyntax; // 导入SQL语法类型枚举
import org.apache.calcite.sql.type.SqlTypeFamily; // 导入SQL类型族
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称
import org.apache.calcite.util.NlsString; // 导入国际化字符串
import org.apache.calcite.util.Sarg; // 导入Sarg（Search Argument），用于表示搜索参数

import com.google.common.base.Throwables; // 导入Google Guava的异常处理工具
import com.google.common.collect.Range; // 导入Google Guava的范围类

import java.util.ArrayList; // 导入动态数组列表
import java.util.GregorianCalendar; // 导入日历类，用于处理日期时间
import java.util.LinkedHashMap; // 导入有序哈希映射
import java.util.List; // 导入列表接口
import java.util.Locale; // 导入本地化类
import java.util.Map; // 导入映射接口
import java.util.Set; // 导入集合接口

import static com.google.common.base.Preconditions.checkArgument; // 导入参数检查工具
import static com.google.common.base.Preconditions.checkState; // 导入状态检查工具

import static org.apache.calcite.adapter.elasticsearch.QueryBuilders.boolQuery; // 导入布尔查询构建器工厂方法
import static org.apache.calcite.adapter.elasticsearch.QueryBuilders.existsQuery; // 导入存在性查询构建器工厂方法
import static org.apache.calcite.adapter.elasticsearch.QueryBuilders.rangeQuery; // 导入范围查询构建器工厂方法
import static org.apache.calcite.adapter.elasticsearch.QueryBuilders.regexpQuery; // 导入正则表达式查询构建器工厂方法
import static org.apache.calcite.adapter.elasticsearch.QueryBuilders.termQuery; // 导入词项查询构建器工厂方法
import static org.apache.calcite.adapter.elasticsearch.QueryBuilders.termsQuery; // 导入多词项查询构建器工厂方法

import static java.lang.String.format; // 导入字符串格式化方法
import static java.util.Objects.requireNonNull; // 导入对象非空检查方法

/**
 * Query predicate analyzer. Uses visitor pattern to traverse existing expression
 * and convert it to {@link QueryBuilder}.
 *
 * <p>Major part of this class have been copied from
 * <a href="https://www.dremio.com/">dremio</a> ES adapter
 * (thanks to their team for improving calcite-ES integration).
 */ // 类注释：查询谓词分析器，使用访问者模式遍历表达式树并将其转换为Elasticsearch的QueryBuilder查询构建器，大部分代码参考自Dremio的ES适配器
class PredicateAnalyzer { // 谓词分析器类，负责将Calcite的Rex表达式树转换为Elasticsearch查询

  /**
   * Internal exception.
   */ // 内部异常类注释：用于谓词分析器处理过程中抛出的内部异常
  @SuppressWarnings("serial") // 抑制序列化版本号警告
  private static final class PredicateAnalyzerException extends RuntimeException { // 内部异常类，继承自运行时异常

    PredicateAnalyzerException(String message) { // 构造函数：接收错误消息
      super(message); // 调用父类构造函数，传入消息
    }

    PredicateAnalyzerException(Throwable cause) { // 构造函数：接收异常原因
      super(cause); // 调用父类构造函数，传入异常原因
    }
  } // 内部异常类结束

  /**
   * Exception that is thrown when a {@link org.apache.calcite.rel.RelNode}
   * expression cannot be processed (or converted into an Elasticsearch query).
   */ // 异常类注释：当RelNode表达式无法处理或无法转换为Elasticsearch查询时抛出的异常
  static class ExpressionNotAnalyzableException extends Exception { // 表达式不可分析异常类，继承自Exception
    ExpressionNotAnalyzableException(String message, Throwable cause) { // 构造函数：接收错误消息和异常原因
      super(message, cause); // 调用父类构造函数，传入消息和原因
    }
  } // 表达式不可分析异常类结束

  private PredicateAnalyzer() {} // 私有构造函数，防止实例化，因为这是一个工具类

  /**
   * Walks the expression tree, attempting to convert the entire tree into
   * an equivalent Elasticsearch query filter. If an error occurs, or if it
   * is determined that the expression cannot be converted, an exception is
   * thrown and an error message logged.
   *
   * <p>Callers should catch ExpressionNotAnalyzableException
   * and fall back to not using push-down filters.
   *
   * @param expression expression to analyze
   * @return search query which can be used to query ES cluster
   * @throws ExpressionNotAnalyzableException when expression can't processed by this analyzer
   */ // 方法注释：遍历表达式树，尝试将整个树转换为等价的Elasticsearch查询过滤器，如果发生错误或表达式无法转换，则抛出异常
  static QueryBuilder analyze(RexNode expression) throws ExpressionNotAnalyzableException { // 静态分析方法：分析Rex表达式并返回Elasticsearch查询构建器
    requireNonNull(expression, "expression"); // 检查表达式参数不为空
    try { // 开始try块
      // visits expression tree // 注释：访问表达式树
      QueryExpression e = (QueryExpression) expression.accept(new Visitor()); // 创建访问者并遍历表达式树，转换为QueryExpression

      if (e != null && e.isPartial()) { // 检查表达式是否为部分表达式
        throw new UnsupportedOperationException("Can't handle partial QueryExpression: " + e); // 如果是部分表达式则抛出不支持操作异常
      } // 结束if
      return e != null ? e.builder() : null; // 如果表达式不为空则返回其QueryBuilder，否则返回null
    } catch (Throwable e) { // 捕获所有异常
      Throwables.throwIfInstanceOf(e, UnsupportedOperationException.class); // 如果是不支持操作异常则重新抛出
      throw new ExpressionNotAnalyzableException("Can't convert " + expression, e); // 否则包装为表达式不可分析异常抛出
    } // 结束try-catch块
  } // 方法结束

  /**
   * Traverses {@link RexNode} tree and builds ES query.
   */ // 内部类注释：访问者类，用于遍历RexNode树并构建Elasticsearch查询
  private static class Visitor extends RexVisitorImpl<Expression> { // 访问者类，继承自RexVisitorImpl，用于遍历表达式树

    private Visitor() { // 私有构造函数
      super(true); // 调用父类构造函数，参数true表示深度优先遍历
    }

    @Override public Expression visitInputRef(RexInputRef inputRef) { // 重写访问输入引用的方法
      return new NamedFieldExpression(inputRef); // 将输入引用转换为命名字段表达式
    }

    @Override public Expression visitLiteral(RexLiteral literal) { // 重写访问字面量的方法
      return new LiteralExpression(literal); // 将字面量转换为字面量表达式
    } // 方法结束

    private static boolean supportedRexCall(RexCall call) { // 私有静态方法：判断Rex调用是否支持
      final SqlSyntax syntax = call.getOperator().getSyntax(); // 获取操作符的语法类型
      switch (syntax) { // 根据语法类型进行判断
      case BINARY: // 二元操作符
        switch (call.getKind()) { // 根据操作符类型判断
        case CONTAINS: // 包含操作符
        case AND: // 逻辑与操作符
        case OR: // 逻辑或操作符
        case LIKE: // LIKE操作符
        case EQUALS: // 等于操作符
        case NOT_EQUALS: // 不等于操作符
        case GREATER_THAN: // 大于操作符
        case GREATER_THAN_OR_EQUAL: // 大于等于操作符
        case LESS_THAN: // 小于操作符
        case LESS_THAN_OR_EQUAL: // 小于等于操作符
          return true; // 支持这些操作符
        default: // 其他操作符
          return false; // 不支持
        } // 结束switch
      case SPECIAL: // 特殊操作符
        switch (call.getKind()) { // 根据操作符类型判断
        case CAST: // 类型转换操作符
        case LIKE: // LIKE操作符
        case ITEM: // 数组项访问操作符
        case OTHER_FUNCTION: // 其他函数
          return true; // 支持这些操作符
        case CASE: // CASE表达式
        case SIMILAR: // SIMILAR操作符
        default: // 其他操作符
          return false; // 不支持
        } // 结束switch
      case FUNCTION: // 函数操作符
        return true; // 支持所有函数
      case POSTFIX: // 后缀操作符
        switch (call.getKind()) { // 根据操作符类型判断
        case IS_NOT_NULL: // IS NOT NULL操作符
        case IS_NULL: // IS NULL操作符
          return true; // 支持这些操作符
        default: // 其他操作符
          return false; // 不支持
        } // 结束switch
      case PREFIX: // NOT() 前缀操作符
        switch (call.getKind()) { // 根据操作符类型判断
        case NOT: // NOT操作符
          return true; // 支持NOT操作符
        default: // 其他操作符
          return false; // 不支持
        } // 结束switch
      case INTERNAL: // 内部操作符
        switch (call.getKind()) { // 根据操作符类型判断
        case SEARCH: // SEARCH操作符
          return canBeTranslatedToTermsQuery(call); // 判断是否可以转换为terms查询
        default: // 其他操作符
          return false; // 不支持
        } // 结束switch
      case FUNCTION_ID: // 函数ID
      case FUNCTION_STAR: // 函数星号
      default: // 其他情况
        return false; // 不支持
      } // 结束switch
    } // 方法结束

    /**
     * There are three types of the Sarg included in SEARCH RexCall:
     * 1) Sarg is points (In ('a', 'b', 'c' ...)).
     *    In this case the search call can be translated to terms Query
     * 2) Sarg is complementedPoints (Not in ('a', 'b')).
     *    In this case the search call can be translated to MustNot terms Query
     * 3) Sarg is real Range( > 1 and <= 10).
     *    In this case the search call should be translated to rang Query
     * Currently only the 1) and 2) cases are supported.
     *
     * @param search SEARCH RexCall
     * @return true if it isSearchWithPoints or isSearchWithComplementedPoints, other false
     */ // 方法注释：SEARCH RexCall中包含三种类型的Sarg：1)点集合（IN操作）2)补点集合（NOT IN操作）3)范围查询，目前只支持前两种
    static boolean canBeTranslatedToTermsQuery(RexCall search) { // 静态方法：判断SEARCH调用是否可以转换为terms查询
      return isSearchWithPoints(search) || isSearchWithComplementedPoints(search); // 返回是否为点集合或补点集合
    }

    static boolean isSearchWithPoints(RexCall search) { // 静态方法：判断SEARCH调用是否包含点集合
      RexLiteral literal = (RexLiteral) search.getOperands().get(1); // 获取第二个操作数（Sarg字面量）
      final Sarg<?> sarg = requireNonNull(literal.getValueAs(Sarg.class), "Sarg"); // 从字面量中获取Sarg对象
      return sarg.isPoints(); // 返回Sarg是否为点集合
    }

    static boolean isSearchWithComplementedPoints(RexCall search) { // 静态方法：判断SEARCH调用是否包含补点集合
      RexLiteral literal = (RexLiteral) search.getOperands().get(1); // 获取第二个操作数（Sarg字面量）
      final Sarg<?> sarg = requireNonNull(literal.getValueAs(Sarg.class), "Sarg"); // 从字面量中获取Sarg对象
      return sarg.isComplementedPoints(); // 返回Sarg是否为补点集合
    } // 方法结束

    @Override public Expression visitCall(RexCall call) { // 重写访问Rex调用的方法

      SqlSyntax syntax = call.getOperator().getSyntax(); // 获取操作符的语法类型
      if (!supportedRexCall(call)) { // 检查调用是否支持
        String message = String.format(Locale.ROOT, "Unsupported call: [%s]", call); // 构造错误消息
        throw new PredicateAnalyzerException(message); // 抛出谓词分析器异常
      } // 结束if

      switch (syntax) { // 根据语法类型分发处理
      case BINARY: // 二元操作符
        return binary(call); // 调用二元操作处理方法
      case POSTFIX: // 后缀操作符
        return postfix(call); // 调用后缀操作处理方法
      case PREFIX: // 前缀操作符
        return prefix(call); // 调用前缀操作处理方法
      case INTERNAL: // 内部操作符
        return binary(call); // 调用二元操作处理方法
      case SPECIAL: // 特殊操作符
        switch (call.getKind()) { // 根据操作符类型进一步判断
        case CAST: // 类型转换
          return toCastExpression(call); // 转换为类型转换表达式
        case LIKE: // LIKE操作符
          return binary(call); // 调用二元操作处理方法
        case CONTAINS: // 包含操作符
          return binary(call); // 调用二元操作处理方法
        default: // 其他操作符
          // manually process ITEM($0, 'foo') which in our case will be named attribute // 注释：手动处理ITEM操作符
          if (call.getOperator().getName().equalsIgnoreCase("ITEM")) { // 如果是ITEM操作符
            return toNamedField((RexLiteral) call.getOperands().get(1)); // 转换为命名字段表达式
          } // 结束if
          String message = String.format(Locale.ROOT, "Unsupported call: [%s]", call); // 构造错误消息
          throw new PredicateAnalyzerException(message); // 抛出谓词分析器异常
        } // 结束switch
      case FUNCTION: // 函数操作符
        if (call.getOperator().getName().equalsIgnoreCase("CONTAINS")) { // 如果是CONTAINS函数
          List<Expression> operands = visitList(call.getOperands()); // 访问所有操作数
          String query = // 转换查询字符串
              convertQueryString(operands.subList(0, operands.size() - 1),
                  operands.get(operands.size() - 1));
          return QueryExpression.create(new NamedFieldExpression()).queryString(query); // 创建查询表达式并设置查询字符串
        } // 结束if
        // fall through // 注释：继续执行到default分支
      default: // 默认情况
        String message = // 构造错误消息
            format(Locale.ROOT,
                "Unsupported syntax [%s] for call: [%s]", syntax, call);
        throw new PredicateAnalyzerException(message); // 抛出谓词分析器异常
      } // 结束switch
    } // 方法结束

    private static String convertQueryString(List<Expression> fields, Expression query) { // 私有静态方法：转换查询字符串
      int index = 0; // 初始化索引计数器
      checkArgument(query instanceof LiteralExpression, // 检查查询表达式是否为字面量表达式
          "Query string must be a string literal");
      String queryString = ((LiteralExpression) query).stringValue(); // 获取查询字符串值
      @SuppressWarnings("ModifiedButNotUsed") // 抑制未使用变量警告
      Map<String, String> fieldMap = new LinkedHashMap<>(); // 创建字段映射（当前未使用）
      for (Expression expr : fields) { // 遍历所有字段表达式
        if (expr instanceof NamedFieldExpression) { // 如果是命名字段表达式
          NamedFieldExpression field = (NamedFieldExpression) expr; // 转换为命名字段表达式
          String fieldIndexString = String.format(Locale.ROOT, "$%d", index++); // 格式化字段索引字符串
          fieldMap.put(fieldIndexString, field.getReference()); // 将索引和字段引用放入映射
        } // 结束if
      } // 结束for循环
      try { // 开始try块
        return queryString; // 返回查询字符串
      } catch (Exception e) { // 捕获异常
        throw new PredicateAnalyzerException(e); // 抛出谓词分析器异常
      } // 结束try-catch块
    } // 方法结束

    private QueryExpression prefix(RexCall call) { // 私有方法：处理前缀操作符（NOT）
      checkArgument(call.getKind() == SqlKind.NOT, // 检查操作符类型是否为NOT
          "Expected %s got %s", SqlKind.NOT, call.getKind());

      if (call.getOperands().size() != 1) { // 检查操作数数量是否为1
        String message = String.format(Locale.ROOT, "Unsupported NOT operator: [%s]", call); // 构造错误消息
        throw new PredicateAnalyzerException(message); // 抛出谓词分析器异常
      } // 结束if

      QueryExpression expr = (QueryExpression) call.getOperands().get(0).accept(this); // 访问第一个操作数
      return expr.not(); // 返回取反后的表达式
    } // 方法结束

    private QueryExpression postfix(RexCall call) { // 私有方法：处理后缀操作符（IS NULL/IS NOT NULL）
      checkArgument(call.getKind() == SqlKind.IS_NULL // 检查操作符类型是否为IS NULL或IS NOT NULL
          || call.getKind() == SqlKind.IS_NOT_NULL);
      if (call.getOperands().size() != 1) { // 检查操作数数量是否为1
        String message = String.format(Locale.ROOT, "Unsupported operator: [%s]", call); // 构造错误消息
        throw new PredicateAnalyzerException(message); // 抛出谓词分析器异常
      } // 结束if
      Expression a = call.getOperands().get(0).accept(this); // 访问第一个操作数
      // Elasticsearch does not want is null/is not null (exists query) // 注释：Elasticsearch不支持_id和_index字段的is null/is not null查询
      // for _id and _index, although it supports for all other metadata column
      isColumn(a, call, ElasticsearchConstants.ID, true); // 检查是否为_id列
      isColumn(a, call, ElasticsearchConstants.INDEX, true); // 检查是否为_index列
      QueryExpression operand = QueryExpression.create((TerminalExpression) a); // 创建查询表达式
      return call.getKind() == SqlKind.IS_NOT_NULL ? operand.exists() : operand.notExists(); // 根据操作符类型返回存在性查询或非存在性查询
    } // 方法结束

    /**
     * Process a call which is a binary operation, transforming into an equivalent
     * query expression. Note that the incoming call may be either a simple binary
     * expression, such as {@code foo > 5}, or it may be several simple expressions connected
     * by {@code AND} or {@code OR} operators, such as {@code foo > 5 AND bar = 'abc' AND 'rot' < 1}
     *
     * @param call existing call
     * @return evaluated expression
     */ // 方法注释：处理二元操作调用，转换为等价的查询表达式，可能是简单二元表达式或由AND/OR连接的多个表达式
    private QueryExpression binary(RexCall call) { // 私有方法：处理二元操作

      // if AND/OR, do special handling // 注释：如果是AND/OR操作，进行特殊处理
      if (call.getKind() == SqlKind.AND || call.getKind() == SqlKind.OR) { // 检查是否为AND或OR操作
        return andOr(call); // 调用AND/OR处理方法
      } // 结束if

      checkForIncompatibleDateTimeOperands(call); // 检查是否有不兼容的日期时间操作数

      checkState(call.getOperands().size() == 2); // 检查操作数数量是否为2
      final Expression a = call.getOperands().get(0).accept(this); // 访问第一个操作数
      final Expression b = call.getOperands().get(1).accept(this); // 访问第二个操作数

      final SwapResult pair = swap(a, b); // 交换操作数顺序，确保字面量在右侧
      final boolean swapped = pair.isSwapped(); // 获取是否交换的标志

      // For _id and _index columns, only equals/not_equals work! // 注释：对于_id和_index列，只支持等于和不等于操作
      if (isColumn(pair.getKey(), call, ElasticsearchConstants.ID, false) // 检查是否为_id列
          || isColumn(pair.getKey(), call, ElasticsearchConstants.INDEX, false) // 检查是否为_index列
          || isColumn(pair.getKey(), call, ElasticsearchConstants.UID, false)) { // 检查是否为_uid列
        switch (call.getKind()) { // 根据操作符类型判断
        case EQUALS: // 等于
        case NOT_EQUALS: // 不等于
          break; // 允许这些操作
        default: // 其他操作
          throw new PredicateAnalyzerException( // 抛出异常
              "Cannot handle " + call.getKind() + " expression for _id field, " + call);
        } // 结束switch
      } // 结束if

      switch (call.getKind()) { // 根据操作符类型分发处理
      case CONTAINS: // 包含操作
        return QueryExpression.create(pair.getKey()).contains(pair.getValue()); // 返回包含查询表达式
      case LIKE: // LIKE操作
        throw new UnsupportedOperationException("LIKE not yet supported"); // 抛出不支持操作异常
      case EQUALS: // 等于操作
        return QueryExpression.create(pair.getKey()).equals(pair.getValue()); // 返回等于查询表达式
      case NOT_EQUALS: // 不等于操作
        return QueryExpression.create(pair.getKey()).notEquals(pair.getValue()); // 返回不等于查询表达式
      case GREATER_THAN: // 大于操作
        if (swapped) { // 如果操作数已交换
          return QueryExpression.create(pair.getKey()).lt(pair.getValue()); // 返回小于查询表达式
        } // 结束if
        return QueryExpression.create(pair.getKey()).gt(pair.getValue()); // 返回大于查询表达式
      case GREATER_THAN_OR_EQUAL: // 大于等于操作
        if (swapped) { // 如果操作数已交换
          return QueryExpression.create(pair.getKey()).lte(pair.getValue()); // 返回小于等于查询表达式
        } // 结束if
        return QueryExpression.create(pair.getKey()).gte(pair.getValue()); // 返回大于等于查询表达式
      case LESS_THAN: // 小于操作
        if (swapped) { // 如果操作数已交换
          return QueryExpression.create(pair.getKey()).gt(pair.getValue()); // 返回大于查询表达式
        } // 结束if
        return QueryExpression.create(pair.getKey()).lt(pair.getValue()); // 返回小于查询表达式
      case LESS_THAN_OR_EQUAL: // 小于等于操作
        if (swapped) { // 如果操作数已交换
          return QueryExpression.create(pair.getKey()).gte(pair.getValue()); // 返回大于等于查询表达式
        } // 结束if
        return QueryExpression.create(pair.getKey()).lte(pair.getValue()); // 返回小于等于查询表达式
      case SEARCH: // SEARCH操作
        if (isSearchWithComplementedPoints(call)) { // 如果是补点集合
          return QueryExpression.create(pair.getKey()).notIn(pair.getValue()); // 返回NOT IN查询表达式
        } else { // 否则
          return QueryExpression.create(pair.getKey()).in(pair.getValue()); // 返回IN查询表达式
        } // 结束if-else
      default: // 默认情况
        break; // 跳出switch
      } // 结束switch
      String message = String.format(Locale.ROOT, "Unable to handle call: [%s]", call); // 构造错误消息
      throw new PredicateAnalyzerException(message); // 抛出谓词分析器异常
    } // 方法结束

    private QueryExpression andOr(RexCall call) { // 私有方法：处理AND/OR逻辑操作
      QueryExpression[] expressions = new QueryExpression[call.getOperands().size()]; // 创建查询表达式数组
      PredicateAnalyzerException firstError = null; // 初始化第一个错误
      boolean partial = false; // 初始化部分转换标志
      for (int i = 0; i < call.getOperands().size(); i++) { // 遍历所有操作数
        try { // 开始try块
          Expression expr = call.getOperands().get(i).accept(this); // 访问当前操作数
          if (expr instanceof NamedFieldExpression) { // 如果是命名字段表达式
            // nop currently // 注释：当前不处理
          } else { // 否则
            expressions[i] = (QueryExpression) call.getOperands().get(i).accept(this); // 存储查询表达式
          } // 结束if-else
          partial |= expressions[i].isPartial(); // 更新部分转换标志
        } catch (PredicateAnalyzerException e) { // 捕获谓词分析器异常
          if (firstError == null) { // 如果是第一个错误
            firstError = e; // 保存第一个错误
          } // 结束if
          partial = true; // 设置部分转换标志
        } // 结束try-catch块
      } // 结束for循环

      switch (call.getKind()) { // 根据操作符类型分发处理
      case OR: // OR操作
        if (partial) { // 如果是部分转换
          if (firstError != null) { // 如果有错误
            throw firstError; // 抛出第一个错误
          } else { // 否则
            final String message = String.format(Locale.ROOT, "Unable to handle call: [%s]", call); // 构造错误消息
            throw new PredicateAnalyzerException(message); // 抛出谓词分析器异常
          } // 结束if-else
        } // 结束if
        return CompoundQueryExpression.or(expressions); // 返回OR组合查询表达式
      case AND: // AND操作
        return CompoundQueryExpression.and(partial, expressions); // 返回AND组合查询表达式
      default: // 默认情况
        String message = String.format(Locale.ROOT, "Unable to handle call: [%s]", call); // 构造错误消息
        throw new PredicateAnalyzerException(message); // 抛出谓词分析器异常
      } // 结束switch
    } // 方法结束

    /**
     * Holder class for a pair of expressions. Used to convert {@code 1 = foo} into {@code foo = 1}
     */ // 内部类注释：表达式对的持有类，用于将字面量在左边的表达式（如1 = foo）转换为标准形式（foo = 1）
    private static class SwapResult { // 交换结果类，用于存储交换后的表达式对
      final boolean swapped; // 是否交换的标志
      final TerminalExpression terminal; // 终端表达式（通常是字段引用）
      final LiteralExpression literal; // 字面量表达式

      SwapResult(boolean swapped, TerminalExpression terminal, LiteralExpression literal) { // 构造函数
        super(); // 调用父类构造函数
        this.swapped = swapped; // 设置交换标志
        this.terminal = terminal; // 设置终端表达式
        this.literal = literal; // 设置字面量表达式
      }

      TerminalExpression getKey() { // 获取键（终端表达式）
        return terminal; // 返回终端表达式
      }

      LiteralExpression getValue() { // 获取值（字面量表达式）
        return literal; // 返回字面量表达式
      }

      boolean isSwapped() { // 判断是否交换过
        return swapped; // 返回交换标志
      }
    } // 内部类结束

    /**
     * Swap order of operands such that the literal expression is always on the right.
     *
     * <p>NOTE: Some combinations of operands are implicitly not supported and will
     * cause an exception to be thrown. For example, we currently do not support
     * comparing a literal to another literal as convention {@code 5 = 5}. Nor do we support
     * comparing named fields to other named fields as convention {@code $0 = $1}.
     *
     * @param left left expression
     * @param right right expression
     */ // 方法注释：交换操作数顺序，确保字面量表达式总是在右侧，不支持字面量与字面量比较或字段与字段比较
    private static SwapResult swap(Expression left, Expression right) { // 私有静态方法：交换操作数顺序

      TerminalExpression terminal; // 声明终端表达式变量
      LiteralExpression literal = expressAsLiteral(left); // 尝试将左表达式转换为字面量
      boolean swapped = false; // 初始化交换标志
      if (literal != null) { // 如果左表达式是字面量
        swapped = true; // 设置交换标志
        terminal = (TerminalExpression) right; // 将右表达式作为终端表达式
      } else { // 否则
        literal = expressAsLiteral(right); // 尝试将右表达式转换为字面量
        terminal = (TerminalExpression) left; // 将左表达式作为终端表达式
      } // 结束if-else

      if (literal == null || terminal == null) { // 检查是否成功分离字面量和终端表达式
        String message = // 构造错误消息
            String.format(Locale.ROOT,
                "Unexpected combination of expressions [left: %s] [right: %s]",
                left, right);
        throw new PredicateAnalyzerException(message); // 抛出谓词分析器异常
      } // 结束if

      if (CastExpression.isCastExpression(terminal)) { // 如果终端表达式是类型转换表达式
        terminal = CastExpression.unpack(terminal); // 解包类型转换表达式
      } // 结束if

      return new SwapResult(swapped, terminal, literal); // 返回交换结果
    } // 方法结束

    private CastExpression toCastExpression(RexCall call) { // 私有方法：转换为类型转换表达式
      TerminalExpression argument = (TerminalExpression) call.getOperands().get(0).accept(this); // 访问第一个操作数
      return new CastExpression(call.getType(), argument); // 创建类型转换表达式
    }

    private static NamedFieldExpression toNamedField(RexLiteral literal) { // 私有静态方法：转换为命名字段表达式
      return new NamedFieldExpression(literal); // 创建命名字段表达式
    }

    /**
     * Try to convert a generic expression into a literal expression.
     */ // 方法注释：尝试将通用表达式转换为字面量表达式
    private static LiteralExpression expressAsLiteral(Expression exp) { // 私有静态方法：转换为字面量表达式

      if (exp instanceof LiteralExpression) { // 如果表达式是字面量表达式
        return (LiteralExpression) exp; // 直接返回
      } // 结束if

      return null; // 返回null表示无法转换
    } // 方法结束

    private static boolean isColumn(Expression exp, RexNode node, // 私有静态方法：检查表达式是否为指定列
        String columnName, boolean throwException) { // 参数：表达式、节点、列名、是否抛出异常
      if (!(exp instanceof NamedFieldExpression)) { // 如果表达式不是命名字段表达式
        return false; // 返回false
      } // 结束if

      final NamedFieldExpression termExp = (NamedFieldExpression) exp; // 转换为命名字段表达式
      if (columnName.equals(termExp.getRootName())) { // 如果列名匹配
        if (throwException) { // 如果需要抛出异常
          throw new PredicateAnalyzerException("Cannot handle _id field in " + node); // 抛出谓词分析器异常
        } // 结束if
        return true; // 返回true表示是指定列
      } // 结束if
      return false; // 返回false
    } // 方法结束
  } // Visitor类结束

  /**
   * Empty interface; exists only to define the type hierarchy.
   */ // 接口注释：空接口，仅用于定义类型层次结构，所有表达式都实现此接口
  interface Expression { // 表达式接口
  } // 接口结束

  /**
   * Main expression operators (like {@code equals}, {@code gt}, {@code exists} etc.)
   */ // 抽象类注释：查询表达式抽象类，定义主要的表达式操作符（如equals、gt、exists等）
  abstract static class QueryExpression implements Expression { // 查询表达式抽象类

    public abstract QueryBuilder builder(); // 抽象方法：获取Elasticsearch查询构建器

    public boolean isPartial() { // 方法：判断是否为部分表达式
      return false; // 默认返回false
    }

    public abstract QueryExpression contains(LiteralExpression literal); // 抽象方法：包含操作

    /**
     * Negate {@code this} QueryExpression (not the next one).
     */ // 方法注释：取反当前查询表达式
    public abstract QueryExpression not(); // 抽象方法：取反操作

    public abstract QueryExpression exists(); // 抽象方法：存在性查询

    public abstract QueryExpression notExists(); // 抽象方法：非存在性查询

    public abstract QueryExpression like(LiteralExpression literal); // 抽象方法：LIKE操作

    public abstract QueryExpression notLike(LiteralExpression literal); // 抽象方法：NOT LIKE操作

    public abstract QueryExpression equals(LiteralExpression literal); // 抽象方法：等于操作

    public abstract QueryExpression in(LiteralExpression literal); // 抽象方法：IN操作

    public abstract QueryExpression notIn(LiteralExpression literal); // 抽象方法：NOT IN操作

    public abstract QueryExpression notEquals(LiteralExpression literal); // 抽象方法：不等于操作

    public abstract QueryExpression gt(LiteralExpression literal); // 抽象方法：大于操作

    public abstract QueryExpression gte(LiteralExpression literal); // 抽象方法：大于等于操作

    public abstract QueryExpression lt(LiteralExpression literal); // 抽象方法：小于操作

    public abstract QueryExpression lte(LiteralExpression literal); // 抽象方法：小于等于操作

    public abstract QueryExpression queryString(String query); // 抽象方法：查询字符串操作

    public abstract QueryExpression isTrue(); // 抽象方法：判断为真

    public static QueryExpression create(TerminalExpression expression) { // 静态工厂方法：创建查询表达式
      if (expression instanceof CastExpression) { // 如果是类型转换表达式
        expression = CastExpression.unpack(expression); // 解包类型转换表达式
      } // 结束if

      if (expression instanceof NamedFieldExpression) { // 如果是命名字段表达式
        return new SimpleQueryExpression((NamedFieldExpression) expression); // 创建简单查询表达式
      } else { // 否则
        String message = String.format(Locale.ROOT, "Unsupported expression: [%s]", expression); // 构造错误消息
        throw new PredicateAnalyzerException(message); // 抛出谓词分析器异常
      } // 结束if-else
    } // 方法结束

  } // 抽象类结束

  /**
   * Builds conjunctions / disjunctions based on existing expressions.
   */ // 类注释：组合查询表达式类，用于构建基于现有表达式的合取（AND）或析取（OR）查询
  static class CompoundQueryExpression extends QueryExpression { // 组合查询表达式类

    private final boolean partial; // 是否为部分表达式
    private final BoolQueryBuilder builder; // 布尔查询构建器

    public static CompoundQueryExpression or(QueryExpression... expressions) { // 静态方法：创建OR组合查询表达式
      CompoundQueryExpression bqe = new CompoundQueryExpression(false); // 创建组合查询表达式
      for (QueryExpression expression : expressions) { // 遍历所有表达式
        bqe.builder.should(expression.builder()); // 使用should子句添加表达式
      } // 结束for循环
      return bqe; // 返回组合查询表达式
    }

    /**
     * If partial expression, we will need to complete it with a full filter.
     *
     * @param partial whether we partially converted a and for push down purposes
     * @param expressions list of expressions to join with {@code and} boolean
     * @return new instance of expression
     */ // 方法注释：创建AND组合查询表达式，如果是部分表达式，需要用完整过滤器来补充
    public static CompoundQueryExpression and(boolean partial, QueryExpression... expressions) { // 静态方法：创建AND组合查询表达式
      CompoundQueryExpression bqe = new CompoundQueryExpression(partial); // 创建组合查询表达式
      for (QueryExpression expression : expressions) { // 遍历所有表达式
        if (expression != null) { // 如果表达式不为null
          // partial expressions have nulls for missing nodes // 注释：部分表达式的缺失节点为null
          bqe.builder.must(expression.builder()); // 使用must子句添加表达式
        } // 结束if
      } // 结束for循环
      return bqe; // 返回组合查询表达式
    }

    private CompoundQueryExpression(boolean partial) { // 私有构造函数
      this(partial, boolQuery()); // 调用另一个构造函数
    }

    private CompoundQueryExpression(boolean partial, BoolQueryBuilder builder) { // 私有构造函数
      this.partial = partial; // 设置部分表达式标志
      this.builder = requireNonNull(builder, "builder"); // 设置布尔查询构建器
    }

    @Override public boolean isPartial() { // 重写方法：判断是否为部分表达式
      return partial; // 返回部分表达式标志
    }


    @Override public QueryBuilder builder() { // 重写方法：获取查询构建器
      return builder; // 返回布尔查询构建器
    }

    @Override public QueryExpression not() { // 重写方法：取反操作
      return new CompoundQueryExpression(partial, QueryBuilders.boolQuery().mustNot(builder())); // 创建取反的组合查询表达式
    }

    @Override public QueryExpression exists() { // 重写方法：存在性查询
      throw new PredicateAnalyzerException("SqlOperatorImpl ['exists'] " // 抛出异常
          + "cannot be applied to a compound expression");
    }

    @Override public QueryExpression contains(LiteralExpression literal) { // 重写方法：包含操作
      throw new PredicateAnalyzerException("SqlOperatorImpl ['contains'] " // 抛出异常
              + "cannot be applied to a compound expression");
    }

    @Override public QueryExpression notExists() { // 重写方法：非存在性查询
      throw new PredicateAnalyzerException("SqlOperatorImpl ['notExists'] " // 抛出异常
          + "cannot be applied to a compound expression");
    }

    @Override public QueryExpression like(LiteralExpression literal) { // 重写方法：LIKE操作
      throw new PredicateAnalyzerException("SqlOperatorImpl ['like'] " // 抛出异常
          + "cannot be applied to a compound expression");
    }

    @Override public QueryExpression notLike(LiteralExpression literal) { // 重写方法：NOT LIKE操作
      throw new PredicateAnalyzerException("SqlOperatorImpl ['notLike'] " // 抛出异常
          + "cannot be applied to a compound expression");
    }

    @Override public QueryExpression equals(LiteralExpression literal) { // 重写方法：等于操作
      throw new PredicateAnalyzerException("SqlOperatorImpl ['='] " // 抛出异常
          + "cannot be applied to a compound expression");
    }

    @Override public QueryExpression notEquals(LiteralExpression literal) { // 重写方法：不等于操作
      throw new PredicateAnalyzerException("SqlOperatorImpl ['not'] " // 抛出异常
          + "cannot be applied to a compound expression");
    }

    @Override public QueryExpression gt(LiteralExpression literal) { // 重写方法：大于操作
      throw new PredicateAnalyzerException("SqlOperatorImpl ['>'] " // 抛出异常
          + "cannot be applied to a compound expression");
    }

    @Override public QueryExpression gte(LiteralExpression literal) { // 重写方法：大于等于操作
      throw new PredicateAnalyzerException("SqlOperatorImpl ['>='] " // 抛出异常
          + "cannot be applied to a compound expression");
    }

    @Override public QueryExpression lt(LiteralExpression literal) { // 重写方法：小于操作
      throw new PredicateAnalyzerException("SqlOperatorImpl ['<'] " // 抛出异常
          + "cannot be applied to a compound expression");
    }

    @Override public QueryExpression lte(LiteralExpression literal) { // 重写方法：小于等于操作
      throw new PredicateAnalyzerException("SqlOperatorImpl ['<='] " // 抛出异常
          + "cannot be applied to a compound expression");
    }

    @Override public QueryExpression queryString(String query) { // 重写方法：查询字符串操作
      throw new PredicateAnalyzerException("QueryString " // 抛出异常
          + "cannot be applied to a compound expression");
    }

    @Override public QueryExpression isTrue() { // 重写方法：判断为真
      throw new PredicateAnalyzerException("isTrue cannot be applied to a compound expression"); // 抛出异常
    }

    @Override public QueryExpression in(LiteralExpression literal) { // 重写方法：IN操作
      throw new PredicateAnalyzerException("in cannot be applied to a compound expression"); // 抛出异常
    }

    @Override public QueryExpression notIn(LiteralExpression literal) { // 重写方法：NOT IN操作
      throw new PredicateAnalyzerException("notIn cannot be applied to a compound expression"); // 抛出异常
    }
  } // 类结束

  /**

     * Usually basic expression of type {@code a = 'val'} or {@code b > 42}.

     */ // 类注释：简单查询表达式类，表示基本表达式类型（如a = 'val'或b > 42）

    static class SimpleQueryExpression extends QueryExpression { // 简单查询表达式类

  

      private final NamedFieldExpression rel; // 命名字段表达式

      private QueryBuilder builder; // 查询构建器

  

      private String getFieldReference() { // 私有方法：获取字段引用

        return rel.getReference(); // 返回字段引用

      }

  

      private SimpleQueryExpression(NamedFieldExpression rel) { // 私有构造函数

        this.rel = rel; // 设置命名字段表达式

      }

  

      @Override public QueryBuilder builder() { // 重写方法：获取查询构建器

        if (builder == null) { // 如果构建器未初始化

          throw new IllegalStateException("Builder was not initialized"); // 抛出非法状态异常

        } // 结束if

        return builder; // 返回查询构建器

      }

  

      @Override public QueryExpression not() { // 重写方法：取反操作

        builder = boolQuery().mustNot(builder()); // 创建mustNot布尔查询

        return this; // 返回当前对象

      }

  

      @Override public QueryExpression exists() { // 重写方法：存在性查询

        builder = existsQuery(getFieldReference()); // 创建exists查询

        return this; // 返回当前对象

      }

  

      @Override public QueryExpression notExists() { // 重写方法：非存在性查询

        // Even though Lucene doesn't allow a stand alone mustNot boolean query, // 注释：虽然Lucene不允许独立的mustNot布尔查询

        // Elasticsearch handles this problem transparently on its end // 但Elasticsearch会透明地处理这个问题

        builder = boolQuery().mustNot(existsQuery(getFieldReference())); // 创建mustNot exists查询

        return this; // 返回当前对象

      }

  

      @Override public QueryExpression like(LiteralExpression literal) { // 重写方法：LIKE操作

        builder = regexpQuery(getFieldReference(), literal.stringValue()); // 创建正则表达式查询

        return this; // 返回当前对象

      }

  

      @Override public QueryExpression contains(LiteralExpression literal) { // 重写方法：包含操作

        builder = QueryBuilders.matchQuery(getFieldReference(), literal.value()); // 创建match查询

        return this; // 返回当前对象

      }

  

      @Override public QueryExpression notLike(LiteralExpression literal) { // 重写方法：NOT LIKE操作

        builder = boolQuery() // 创建布尔查询

                // NOT LIKE should return false when field is NULL // 注释：当字段为NULL时，NOT LIKE应返回false

                .must(existsQuery(getFieldReference())) // 添加exists子句

                .mustNot(regexpQuery(getFieldReference(), literal.stringValue())); // 添加mustNot正则表达式查询

        return this; // 返回当前对象

      }

  

      @Override public QueryExpression equals(LiteralExpression literal) { // 重写方法：等于操作

        Object value = literal.value(); // 获取字面量值

        if (value instanceof GregorianCalendar) { // 如果是日历对象（日期时间）

          builder = boolQuery() // 创建布尔查询

                  .must(addFormatIfNecessary(literal, rangeQuery(getFieldReference()).gte(value))) // 添加大于等于范围查询

                  .must(addFormatIfNecessary(literal, rangeQuery(getFieldReference()).lte(value))); // 添加小于等于范围查询

        } else { // 否则

          builder = termQuery(getFieldReference(), value); // 创建词项查询

        } // 结束if-else

        return this; // 返回当前对象

      }

  

      @Override public QueryExpression notEquals(LiteralExpression literal) { // 重写方法：不等于操作

        Object value = literal.value(); // 获取字面量值

        if (value instanceof GregorianCalendar) { // 如果是日历对象（日期时间）

          builder = boolQuery() // 创建布尔查询

                  .should(addFormatIfNecessary(literal, rangeQuery(getFieldReference()).gt(value))) // 添加大于范围查询

                  .should(addFormatIfNecessary(literal, rangeQuery(getFieldReference()).lt(value))); // 添加小于范围查询

        } else { // 否则

          builder = boolQuery() // 创建布尔查询

                  // NOT LIKE should return false when field is NULL // 注释：当字段为NULL时，NOT LIKE应返回false

                  .must(existsQuery(getFieldReference())) // 添加exists子句

                  .mustNot(termQuery(getFieldReference(), value)); // 添加mustNot词项查询

        } // 结束if-else

        return this; // 返回当前对象

      }

  

      @Override public QueryExpression gt(LiteralExpression literal) { // 重写方法：大于操作

        Object value = literal.value(); // 获取字面量值

        builder = // 创建范围查询

            addFormatIfNecessary(literal,

                rangeQuery(getFieldReference()).gt(value));

        return this; // 返回当前对象

      }

  

      @Override public QueryExpression gte(LiteralExpression literal) { // 重写方法：大于等于操作

  

        Object value = literal.value(); // 获取字面量值

        builder = addFormatIfNecessary(literal, rangeQuery(getFieldReference()).gte(value)); // 创建大于等于范围查询

        return this; // 返回当前对象

      } // 方法结束

    @Override public QueryExpression lt(LiteralExpression literal) { // 重写方法：小于操作
      Object value = literal.value(); // 获取字面量值
      builder = addFormatIfNecessary(literal, rangeQuery(getFieldReference()).lt(value)); // 创建小于范围查询
      return this; // 返回当前对象
    }

    @Override public QueryExpression lte(LiteralExpression literal) { // 重写方法：小于等于操作
      Object value = literal.value(); // 获取字面量值
      builder = addFormatIfNecessary(literal, rangeQuery(getFieldReference()).lte(value)); // 创建小于等于范围查询
      return this; // 返回当前对象
    }

    @Override public QueryExpression queryString(String query) { // 重写方法：查询字符串操作
      throw new UnsupportedOperationException("QueryExpression not yet supported: " + query); // 抛出不支持操作异常
    }

    @Override public QueryExpression isTrue() { // 重写方法：判断为真
      builder = termQuery(getFieldReference(), true); // 创建词项查询，值为true
      return this; // 返回当前对象
    }

    @Override public QueryExpression in(LiteralExpression literal) { // 重写方法：IN操作
      Iterable<?> iterable = (Iterable<?>) literal.value(); // 获取可迭代对象
      builder = termsQuery(getFieldReference(), iterable); // 创建多词项查询
      return this; // 返回当前对象
    }

    @Override public QueryExpression notIn(LiteralExpression literal) { // 重写方法：NOT IN操作
      Iterable<?> iterable = (Iterable<?>) literal.value(); // 获取可迭代对象
      builder = boolQuery().mustNot(termsQuery(getFieldReference(), iterable)); // 创建mustNot多词项查询
      return this; // 返回当前对象
    }
  } // 类结束


  /**
   * By default, range queries on date/time need use the format of the source to parse the literal.
   * So we need to specify that the literal has "date_time" format
   *
   * @param literal literal value
   * @param rangeQueryBuilder query builder to optionally add {@code format} expression
   * @return existing builder with possible {@code format} attribute
   */ // 方法注释：默认情况下，日期时间的范围查询需要使用源的格式来解析字面量，因此需要指定字面量具有"date_time"格式
  private static RangeQueryBuilder addFormatIfNecessary(LiteralExpression literal, // 私有静态方法：根据需要添加格式
      RangeQueryBuilder rangeQueryBuilder) { // 参数：字面量表达式、范围查询构建器
    if (literal.value() instanceof GregorianCalendar) { // 如果字面量值是日历对象（日期时间）
      rangeQueryBuilder.format("date_time"); // 设置格式为date_time
    } // 结束if
    return rangeQueryBuilder; // 返回范围查询构建器
  } // 方法结束

  /**
   * Empty interface; exists only to define the type hierarchy.
   */ // 接口注释：空接口，仅用于定义类型层次结构，表示终端表达式
  interface TerminalExpression extends Expression { // 终端表达式接口
  } // 接口结束

  /**
   * SQL cast. For example, {@code cast(col as INTEGER)}.
   */ // 类注释：类型转换表达式类，表示SQL的CAST操作（如cast(col as INTEGER)）
  static final class CastExpression implements TerminalExpression { // 类型转换表达式类
    @SuppressWarnings("unused") // 抑制未使用变量警告
    private final RelDataType type; // 目标数据类型
    private final TerminalExpression argument; // 参数表达式

    private CastExpression(RelDataType type, TerminalExpression argument) { // 私有构造函数
      this.type = type; // 设置目标类型
      this.argument = argument; // 设置参数表达式
    }

    public boolean isCastFromLiteral() { // 方法：判断是否从字面量转换
      return argument instanceof LiteralExpression; // 返回参数是否为字面量表达式
    }

    static TerminalExpression unpack(TerminalExpression exp) { // 静态方法：解包类型转换表达式
      if (!(exp instanceof CastExpression)) { // 如果不是类型转换表达式
        return exp; // 直接返回
      } // 结束if
      return ((CastExpression) exp).argument; // 返回参数表达式
    }

    static boolean isCastExpression(Expression exp) { // 静态方法：判断是否为类型转换表达式
      return exp instanceof CastExpression; // 返回是否为类型转换表达式
    }

  } // 类结束

  /**
   * Used for bind variables.
   */ // 类注释：命名字段表达式类，用于表示绑定变量
  static final class NamedFieldExpression implements TerminalExpression { // 命名字段表达式类

    private final String name; // 字段名称

    private NamedFieldExpression() { // 私有构造函数：无参构造
      this.name = null; // 设置名称为null
    }

    private NamedFieldExpression(RexInputRef schemaField) { // 私有构造函数：从输入引用创建
      this.name = schemaField == null ? null : schemaField.getName(); // 获取字段名称
    }

    private NamedFieldExpression(RexLiteral literal) { // 私有构造函数：从字面量创建
      this.name = literal == null ? null : RexLiteral.stringValue(literal); // 获取字符串值作为名称
    }

    String getRootName() { // 方法：获取根名称
      return name; // 返回字段名称
    }

    boolean isMetaField() { // 方法：判断是否为元数据字段
      return ElasticsearchConstants.META_COLUMNS.contains(getRootName()); // 检查是否在元数据列集合中
    }

    String getReference() { // 方法：获取字段引用
      return getRootName(); // 返回根名称
    }
  } // 类结束

  /**
   * Literal like {@code 'foo' or 42 or true} etc.
   */ // 类注释：字面量表达式类，表示字面量值（如'foo'、42、true等）
  static final class LiteralExpression implements TerminalExpression { // 字面量表达式类

    final RexLiteral literal; // Rex字面量对象

    LiteralExpression(RexLiteral literal) { // 构造函数
      this.literal = literal; // 设置Rex字面量
    }

    Object value() { // 方法：获取字面量值

      if (isSarg()) { // 如果是Sarg类型
        return sargValue(); // 返回Sarg值
      } else if (isIntegral()) { // 如果是整数类型
        return longValue(); // 返回长整型值
      } else if (isFloatingPoint()) { // 如果是浮点类型
        return doubleValue(); // 返回双精度浮点值
      } else if (isBoolean()) { // 如果是布尔类型
        return booleanValue(); // 返回布尔值
      } else if (isString()) { // 如果是字符串类型
        return RexLiteral.stringValue(literal); // 返回字符串值
      } else { // 否则
        return rawValue(); // 返回原始值
      } // 结束if-else
    } // 方法结束

    boolean isIntegral() { // 方法：判断是否为整数类型
      return SqlTypeName.INT_TYPES.contains(literal.getType().getSqlTypeName()); // 检查是否在整数类型集合中
    }

    boolean isFloatingPoint() { // 方法：判断是否为浮点类型
      return SqlTypeName.APPROX_TYPES.contains(literal.getType().getSqlTypeName()); // 检查是否在近似类型集合中
    }

    boolean isBoolean() { // 方法：判断是否为布尔类型
      return SqlTypeName.BOOLEAN_TYPES.contains(literal.getType().getSqlTypeName()); // 检查是否在布尔类型集合中
    }

    public boolean isString() { // 方法：判断是否为字符串类型
      return SqlTypeName.CHAR_TYPES.contains(literal.getType().getSqlTypeName()); // 检查是否在字符类型集合中
    }

    public boolean isSarg() { // 方法：判断是否为Sarg类型
      return SqlTypeName.SARG.getName().equalsIgnoreCase(literal.getTypeName().getName()); // 检查类型名称是否为SARG
    }

    long longValue() { // 方法：获取长整型值
      return ((Number) literal.getValue()).longValue(); // 转换为长整型
    }

    double doubleValue() { // 方法：获取双精度浮点值
      return ((Number) literal.getValue()).doubleValue(); // 转换为双精度浮点数
    }

    boolean booleanValue() { // 方法：获取布尔值
      return RexLiteral.booleanValue(literal); // 获取布尔值
    }

    String stringValue() { // 方法：获取字符串值
      return RexLiteral.stringValue(literal); // 获取字符串值
    }

    List<Object> sargValue() { // 方法：获取Sarg值列表
      final Sarg sarg = requireNonNull(literal.getValueAs(Sarg.class), "Sarg"); // 获取Sarg对象
      final RelDataType type = literal.getType(); // 获取数据类型
      List<Object> values = new ArrayList<>(); // 创建值列表
      final SqlTypeName sqlTypeName = type.getSqlTypeName(); // 获取SQL类型名称
      if (sarg.isPoints()) { // 如果是点集合
        Set<Range> ranges = sarg.rangeSet.asRanges(); // 获取范围集合
        ranges.forEach(range -> // 遍历所有范围
            values.add(sargPointValue(range.lowerEndpoint(), sqlTypeName))); // 添加点值
      } else if (sarg.isComplementedPoints()) { // 如果是补点集合
        Set<Range> ranges = sarg.negate().rangeSet.asRanges(); // 获取否定后的范围集合
        ranges.forEach(range -> // 遍历所有范围
            values.add(sargPointValue(range.lowerEndpoint(), sqlTypeName))); // 添加点值
      } // 结束if-else
      return values; // 返回值列表
    } // 方法结束

    Object sargPointValue(Object point, SqlTypeName sqlTypeName) { // 方法：获取Sarg点值
      switch (sqlTypeName) { // 根据SQL类型名称判断
      case CHAR: // 字符类型
      case VARCHAR: // 变长字符类型
        return ((NlsString) point).getValue(); // 返回字符串值
      default: // 默认情况
        return point; // 直接返回点值
      } // 结束switch
    } // 方法结束

    Object rawValue() { // 方法：获取原始值
      return literal.getValue(); // 返回字面量的原始值
    } // 方法结束
  } // 类结束

  /**
   * If one operand in a binary operator is a DateTime type, but the other isn't,
   * we should not push down the predicate.
   *
   * @param call Current node being evaluated
   */ // 方法注释：检查二元操作符的操作数是否为不兼容的日期时间类型，如果一个操作数是DateTime类型而另一个不是，则不应该下推谓词
  private static void checkForIncompatibleDateTimeOperands(RexCall call) { // 私有静态方法：检查不兼容的日期时间操作数
    RelDataType op1 = call.getOperands().get(0).getType(); // 获取第一个操作数的数据类型
    RelDataType op2 = call.getOperands().get(1).getType(); // 获取第二个操作数的数据类型
    if ((SqlTypeFamily.DATETIME.contains(op1) && !SqlTypeFamily.DATETIME.contains(op2)) // 检查日期时间类型不兼容
           || (SqlTypeFamily.DATETIME.contains(op2) && !SqlTypeFamily.DATETIME.contains(op1)) // 检查日期时间类型不兼容
           || (SqlTypeFamily.DATE.contains(op1) && !SqlTypeFamily.DATE.contains(op2)) // 检查日期类型不兼容
           || (SqlTypeFamily.DATE.contains(op2) && !SqlTypeFamily.DATE.contains(op1)) // 检查日期类型不兼容
           || (SqlTypeFamily.TIMESTAMP.contains(op1) && !SqlTypeFamily.TIMESTAMP.contains(op2)) // 检查时间戳类型不兼容
           || (SqlTypeFamily.TIMESTAMP.contains(op2) && !SqlTypeFamily.TIMESTAMP.contains(op1)) // 检查时间戳类型不兼容
           || (SqlTypeFamily.TIME.contains(op1) && !SqlTypeFamily.TIME.contains(op2)) // 检查时间类型不兼容
           || (SqlTypeFamily.TIME.contains(op2) && !SqlTypeFamily.TIME.contains(op1))) { // 检查时间类型不兼容
      throw new PredicateAnalyzerException("Cannot handle " + call.getKind() // 抛出谓词分析器异常
          + " expression for _id field, " + call);
    } // 结束if
  } // 方法结束
} // 类结束
