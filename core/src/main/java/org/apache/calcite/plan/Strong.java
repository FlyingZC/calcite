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
package org.apache.calcite.plan; // 声明包名，该类属于org.apache.calcite.plan包，用于Calcite查询优化器的计划相关功能

import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示行表达式(Rex Expression)中的函数调用
import org.apache.calcite.rex.RexFieldAccess; // 导入RexFieldAccess类，表示行表达式中的字段访问
import org.apache.calcite.rex.RexInputRef; // 导入RexInputRef类，表示行表达式中的输入引用，指向输入行的某个字段
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类，表示行表达式中的字面量常量
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式节点的基类，所有行表达式都继承自此类
import org.apache.calcite.rex.RexUnknownAs; // 导入RexUnknownAs枚举，定义了如何处理UNKNOWN值(即SQL中的NULL在布尔上下文中的表现)
import org.apache.calcite.rex.RexUtil; // 导入RexUtil工具类，提供行表达式的实用方法
import org.apache.calcite.rex.RexVisitorImpl; // 导入RexVisitorImpl类，提供行表达式访问者模式的默认实现
import org.apache.calcite.sql.SqlKind; // 导入SqlKind枚举，定义了SQL操作符的种类(如SELECT、AND、OR等)
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator接口，表示SQL操作符
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，表示不可变的位集合，用于高效地标记和查询索引
import org.apache.calcite.util.Sarg; // 导入Sarg类，表示搜索参数(Search Argument)，用于IN、BETWEEN等操作符的优化

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，表示不可变的列表
import com.google.common.collect.ImmutableSet; // 导入Google Guava的ImmutableSet类，表示不可变的集合
import com.google.common.collect.Iterables; // 导入Google Guava的Iterables工具类，提供迭代操作的实用方法

import java.util.ArrayList; // 导入Java标准库的ArrayList类，表示动态数组
import java.util.EnumMap; // 导入Java标准库的EnumMap类，专门用于枚举键的Map实现
import java.util.List; // 导入Java标准库的List接口，表示有序集合
import java.util.Map; // 导入Java标准库的Map接口，表示键值对映射

import static java.util.Objects.requireNonNull; // 静态导入Objects.requireNonNull方法，用于对象非空检查

/** Utilities for strong predicates. // 强谓词(Strong Predicates)的工具类
 *
 * <p>A predicate is strong (or null-rejecting) with regard to selected subset of inputs // 谓词对于输入的选定子集是强(或拒绝null)的
 * if it is UNKNOWN if all inputs in selected subset are UNKNOWN. // 如果选定子集中的所有输入都是UNKNOWN，则谓词返回UNKNOWN
 *
 * <p>By the way, UNKNOWN is just the boolean form of NULL. // 顺便说一句，UNKNOWN只是NULL的布尔形式
 *
 * <p>Examples: // 示例说明：
 * <ul>
 *   <li>{@code UNKNOWN} is strong in [] (definitely null) // UNKNOWN对于空集合是强的(肯定为null)
 *   <li>{@code c = 1} is strong in [c] (definitely null if and only if c is // c=1对于[c]是强的(当且仅当c为null时肯定为null)
 *   null)
 *   <li>{@code c IS NULL} is not strong (always returns TRUE or FALSE, never // c IS NULL不是强的(总是返回TRUE或FALSE，从不返回null)
 *   null)
 *   <li>{@code p1 AND p2} is strong in [p1, p2] (definitely null if either p1 // p1 AND p2对于[p1,p2]是强的(如果p1或p2任一为null，则肯定为null)
 *   is null or p2 is null)
 *   <li>{@code p1 OR p2} is strong if p1 and p2 are strong // 如果p1和p2都是强的，则p1 OR p2是强的
 * </ul>
 */
public class Strong { // 定义Strong类，提供判断表达式是否为强谓词的工具方法
  private static final Map<SqlKind, Policy> MAP = createPolicyMap(); // 静态常量MAP：存储SqlKind到Policy的映射，用于判断不同SQL操作符的null传播策略

  public Strong() { // 构造方法：创建Strong实例
    super(); // 调用父类Object的构造方法
  }

  /** Returns a checker that consults a bit set to find out whether particular // 返回一个检查器，通过查询位集合来确定特定的输入是否可能为null
   * inputs may be null. */
  public static Strong of(final ImmutableBitSet nullColumns) { // 静态工厂方法：根据给定的null列位集合创建Strong实例
    return new Strong() { // 返回一个匿名子类实例，重写isNull方法
      @Override public boolean isNull(RexInputRef ref) { // 重写isNull方法：检查输入引用是否为null
        return nullColumns.get(ref.getIndex()); // 返回该输入引用的索引在nullColumns位集合中的值
      }
    };
  }

  /** Returns a checker that consults a set to find out whether particular // 返回一个检查器，通过查询集合来确定特定的字段是否可能为null
   * field may be null. */
  public static Strong of(final ImmutableSet<RexFieldAccess> nullFields) { // 静态工厂方法：根据给定的null字段集合创建Strong实例
    return new Strong() { // 返回一个匿名子类实例，重写isNull方法
      @Override public boolean isNull(RexFieldAccess ref) { // 重写isNull方法：检查字段访问是否为null
        return nullFields.contains(ref); // 返回该字段访问是否存在于nullFields集合中
      }
    };
  }

  /** Returns whether the analyzed expression will definitely return null if // 返回分析的表达式在给定输入列集合都为null时是否肯定返回null
   * all of a given set of input columns are null. */
  public static boolean isNull(RexNode node, ImmutableBitSet nullColumns) { // 静态方法：判断表达式在给定的null列下是否肯定为null
    return of(nullColumns).isNull(node); // 使用of方法创建Strong实例，然后调用其实例方法isNull进行判断
  }

  /** Returns whether the analyzed expression will definitely not return true // 返回分析的表达式在给定输入列集合都为null时是否肯定不返回true
   * (equivalently, will definitely return null or false) if // (等价于，肯定返回null或false)
   * all of a given set of input columns are null. */
  public static boolean isNotTrue(RexNode node, ImmutableBitSet nullColumns) { // 静态方法：判断表达式在给定的null列下是否肯定不返回true
    return of(nullColumns).isNotTrue(node); // 使用of方法创建Strong实例，然后调用其实例方法isNotTrue进行判断
  }

  /**
   * Returns how to deduce whether a particular kind of expression is null, // 返回如何推断特定类型的表达式是否为null
   * given whether its arguments are null. // 给定其参数是否为null的情况
   *
   * @deprecated Use {@link Strong#policy(RexNode)} or {@link Strong#policy(SqlOperator)} // 已废弃：请使用policy(RexNode)或policy(SqlOperator)方法
   */
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public static Policy policy(SqlKind kind) { // 静态方法：根据SqlKind获取对应的Policy策略
    return MAP.getOrDefault(kind, Policy.AS_IS); // 从MAP中获取Policy，如果不存在则返回默认值AS_IS
  }

  /**
   * Returns how to deduce whether a particular {@link RexNode} expression is null, // 返回如何推断特定的RexNode表达式是否为null
   * given whether its arguments are null. // 给定其参数是否为null的情况
   */
  public static Policy policy(RexNode rexNode) { // 静态方法：根据RexNode获取对应的Policy策略
    if (rexNode instanceof RexCall) { // 如果RexNode是RexCall类型(函数调用)
      return policy(((RexCall) rexNode).getOperator()); // 调用policy(SqlOperator)方法获取策略
    }
    return MAP.getOrDefault(rexNode.getKind(), Policy.AS_IS); // 否则根据SqlKind从MAP中获取策略，默认为AS_IS
  }

  /**
   * Returns how to deduce whether a particular {@link SqlOperator} expression is null, // 返回如何推断特定的SqlOperator表达式是否为null
   * given whether its arguments are null. // 给定其参数是否为null的情况
   */
  public static Policy policy(SqlOperator operator) { // 静态方法：根据SqlOperator获取对应的Policy策略
    if (operator.getStrongPolicyInference() != null) { // 如果操作符定义了自定义的强策略推断
      return operator.getStrongPolicyInference().get(); // 返回自定义的策略
    }
    return MAP.getOrDefault(operator.getKind(), Policy.AS_IS); // 否则根据SqlKind从MAP中获取策略，默认为AS_IS
  }

  /**
   * Returns whether a given expression is strong. // 返回给定的表达式是否是强谓词
   *
   * <p>Examples: // 示例说明：
   * <ul>
   *   <li>Returns true for {@code c = 1} since it returns null if and only if // 对于c=1返回true，因为当且仅当c为null时返回null
   *   c is null
   *   <li>Returns false for {@code c IS NULL} since it always returns TRUE // 对于c IS NULL返回false，因为它总是返回TRUE或FALSE
   *   or FALSE
   *</ul>
   *
   * @param e Expression // 参数e：要检查的表达式
   * @return true if the expression is strong, false otherwise // 返回值：如果表达式是强的返回true，否则返回false
   */
  public static boolean isStrong(RexNode e) { // 静态方法：判断表达式是否是强谓词
    final ImmutableBitSet.Builder nullColumns = ImmutableBitSet.builder(); // 创建位集合构建器，用于收集表达式中的所有输入引用
    e.accept( // 使用访问者模式遍历表达式树
        new RexVisitorImpl<Void>(true) { // 创建RexVisitorImpl实例，参数true表示深度遍历
          @Override public Void visitInputRef(RexInputRef inputRef) { // 重写visitInputRef方法：处理输入引用节点
            nullColumns.set(inputRef.getIndex()); // 将输入引用的索引添加到位集合中
            return super.visitInputRef(inputRef); // 调用父类方法继续遍历
          }
        });
    return isNull(e, nullColumns.build()); // 调用isNull方法，检查表达式在其所有输入引用都为null时是否肯定为null
  }

  /** Returns whether all expressions in a list are strong. */ // 返回列表中的所有表达式是否都是强谓词
  public static boolean allStrong(List<RexNode> operands) { // 静态方法：判断表达式列表中的所有表达式是否都是强谓词
    return operands.stream().allMatch(Strong::isStrong); // 使用流式处理，检查所有操作数是否都满足isStrong条件
  }

  /** Returns whether the analyzed expression will definitely not return true */ // 返回分析的表达式是否肯定不返回true
  /** (equivalently, will definitely return null or false). */ // (等价于，肯定返回null或false)
  public boolean isNotTrue(RexNode node) { // 实例方法：判断表达式是否肯定不返回true
    switch (node.getKind()) { // 根据表达式的类型进行分支处理
    // TODO Enrich with more possible cases? // TODO：是否可以添加更多可能的情况？
    case IS_NOT_NULL: // 如果是IS NOT NULL操作
      return isNull(((RexCall) node).getOperands().get(0)); // 返回操作数是否为null
    case OR: // 如果是OR操作
      return allNotTrue(((RexCall) node).getOperands()); // 返回所有操作数是否都不为true
    case AND: // 如果是AND操作
      return anyNotTrue(((RexCall) node).getOperands()); // 返回是否有任一操作数不为true
    default: // 默认情况
      return isNull(node); // 返回表达式是否为null
    }
  }

  /** Returns whether all expressions in a list are definitely not true. */ // 返回列表中的所有表达式是否都肯定不为true
  private boolean allNotTrue(List<RexNode> operands) { // 私有方法：判断所有操作数是否都不为true
    for (RexNode operand : operands) { // 遍历所有操作数
      if (!isNotTrue(operand)) { // 如果有任一操作数可能为true
        return false; // 返回false
      }
    }
    return true; // 所有操作数都不为true，返回true
  }

  /** Returns whether any expressions in a list are definitely not true. */ // 返回列表中是否有任一表达式肯定不为true
  private boolean anyNotTrue(List<RexNode> operands) { // 私有方法：判断是否有任一操作数不为true
    for (RexNode operand : operands) { // 遍历所有操作数
      if (isNotTrue(operand)) { // 如果找到任一操作数不为true
        return true; // 返回true
      }
    }
    return false; // 所有操作数都可能为true，返回false
  }

  /** Returns whether an expression is definitely null. */ // 返回表达式是否肯定为null
  /** // 答案基于对其组成表达式调用isNull方法的结果，你可以重写方法来测试假设，例如
   * <p>The answer is based on calls to {@link #isNull} for its constituent // "如果x为null，x+y是否为null？"
   * expressions, and you may override methods to test hypotheses such as
   * "if {@code x} is null, is {@code x + y} null? */
  public boolean isNull(RexNode node) { // 实例方法：判断表达式是否肯定为null
    final Policy policy = policy(node); // 获取表达式对应的Policy策略
    switch (policy) { // 根据策略进行分支处理
    case NOT_NULL: // 如果策略是NOT_NULL(表达式从不为null)
      return false; // 返回false
    case ANY: // 如果策略是ANY(任一参数为null则表达式为null)
      return anyNull(((RexCall) node).getOperands()); // 返回是否有任一操作数为null
    default: // 默认情况
      break; // 跳出switch，继续后续处理
    }

    switch (node.getKind()) { // 根据表达式的类型进行分支处理
    case LITERAL: // 如果是字面量
      return ((RexLiteral) node).isNull(); // 返回该字面量是否为null
    // We can only guarantee AND to return NULL if both inputs are NULL  (similar for OR) // 我们只能保证AND在两个输入都为NULL时返回NULL(OR类似)
    // AND(NULL, FALSE) = FALSE // AND(NULL, FALSE)的结果是FALSE
    case AND: // 如果是AND操作
    case OR: // 或者是OR操作
    case COALESCE: // 或者是COALESCE操作
      return allNull(((RexCall) node).getOperands()); // 返回所有操作数是否都为null
    case NULLIF: // 如果是NULLIF操作
      // NULLIF(null, X) where X can be NULL, returns NULL // NULLIF(null, X)其中X可以为NULL，返回NULL
      // NULLIF(X, Y) where X is not NULL, then this may return NULL if X = Y, otherwise X. // NULLIF(X, Y)其中X不为NULL，如果X=Y则返回NULL，否则返回X
      return allNull(ImmutableList.of(((RexCall) node).getOperands().get(0))); // 返回第一个操作数是否为null
    case INPUT_REF: // 如果是输入引用
      return isNull((RexInputRef) node); // 调用重载的isNull方法检查输入引用
    case FIELD_ACCESS: // 如果是字段访问
      return isNull((RexFieldAccess) node); // 调用重载的isNull方法检查字段访问
    case CASE: // 如果是CASE表达式
      final RexCall caseCall = (RexCall) node; // 强制转换为RexCall
      final List<RexNode> caseValues = new ArrayList<>(); // 创建列表存储CASE表达式的结果值
      for (int i = 0; i < caseCall.getOperands().size(); i++) { // 遍历CASE表达式的所有操作数
        if (!RexUtil.isCasePredicate(caseCall, i)) { // 如果当前操作数不是CASE谓词(即不是WHEN条件)
          caseValues.add(caseCall.getOperands().get(i)); // 将该操作数(THEN结果或ELSE结果)添加到结果值列表中
        }
      }
      return allNull(caseValues); // 返回所有CASE结果值是否都为null
    case SEARCH: // 如果是SEARCH操作(如IN、BETWEEN等)
      final RexCall searchCall = (RexCall) node; // 强制转换为RexCall
      boolean isNull = isNull(searchCall.getOperands().get(0)); // 检查SEARCH的第一个操作数(被搜索的值)是否为null
      if (isNull) { // 如果被搜索的值为null
        final Sarg<?> sarg = // 获取Sarg(搜索参数)对象
            requireNonNull(((RexLiteral) searchCall.getOperands().get(1)).getValueAs(Sarg.class)); // 从字面量中提取Sarg对象
        return sarg.nullAs == RexUnknownAs.UNKNOWN; // 返回Sarg的nullAs属性是否为UNKNOWN
      }
      return false; // 被搜索的值不为null，返回false
    default: // 默认情况
      return false; // 返回false(表达式不一定为null)
    }
  }

  /** Returns whether a given input is definitely null. */ // 返回给定的输入是否肯定为null
  public boolean isNull(RexInputRef ref) { // 实例方法：判断输入引用是否为null
    return false; // 默认返回false，子类可以重写此方法提供具体实现
  }

  /** Returns whether a given field is definitely null. */ // 返回给定的字段是否肯定为null
  public boolean isNull(RexFieldAccess ref) { // 实例方法：判断字段访问是否为null
    return false; // 默认返回false，子类可以重写此方法提供具体实现
  }

  /** Returns whether all expressions in a list are definitely null. */ // 返回列表中的所有表达式是否都肯定为null
  private boolean allNull(List<RexNode> operands) { // 私有方法：判断所有操作数是否都为null
    for (RexNode operand : operands) { // 遍历所有操作数
      if (!isNull(operand)) { // 如果有任一操作数不为null
        return false; // 返回false
      }
    }
    return true; // 所有操作数都为null，返回true
  }

  /** Returns whether any expressions in a list are definitely null. */ // 返回列表中是否有任一表达式肯定为null
  private boolean anyNull(List<RexNode> operands) { // 私有方法：判断是否有任一操作数为null
    for (RexNode operand : operands) { // 遍历所有操作数
      if (isNull(operand)) { // 如果找到任一操作数为null
        return true; // 返回true
      }
    }
    return false; // 所有操作数都不为null，返回false
  }

  private static Map<SqlKind, Policy> createPolicyMap() { // 私有静态方法：创建SqlKind到Policy的映射表
    EnumMap<SqlKind, Policy> map = new EnumMap<>(SqlKind.class); // 创建EnumMap，专门用于枚举键的映射

    map.put(SqlKind.INPUT_REF, Policy.AS_IS); // INPUT_REF(输入引用)：AS_IS策略(保持原样，无法简化)
    map.put(SqlKind.LOCAL_REF, Policy.AS_IS); // LOCAL_REF(局部引用)：AS_IS策略
    map.put(SqlKind.DYNAMIC_PARAM, Policy.AS_IS); // DYNAMIC_PARAM(动态参数)：AS_IS策略
    map.put(SqlKind.OTHER_FUNCTION, Policy.AS_IS); // OTHER_FUNCTION(其他函数)：AS_IS策略

    // The following types of expressions could potentially be custom. // 以下类型的表达式可能有自定义处理
    map.put(SqlKind.CASE, Policy.AS_IS); // CASE表达式：AS_IS策略(需要特殊处理)
    map.put(SqlKind.DECODE, Policy.AS_IS); // DECODE表达式：AS_IS策略
    // NULLIF(1, NULL) yields 1, but NULLIF(1, 1) yields NULL // NULLIF(1, NULL)返回1，但NULLIF(1, 1)返回NULL
    map.put(SqlKind.NULLIF, Policy.AS_IS); // NULLIF：AS_IS策略(需要特殊处理)
    // COALESCE(NULL, 2) yields 2 // COALESCE(NULL, 2)返回2
    map.put(SqlKind.COALESCE, Policy.AS_IS); // COALESCE：AS_IS策略(需要特殊处理)
    map.put(SqlKind.NVL, Policy.AS_IS); // NVL：AS_IS策略
    // FALSE AND NULL yields FALSE // FALSE AND NULL返回FALSE
    // TRUE AND NULL yields NULL // TRUE AND NULL返回NULL
    map.put(SqlKind.AND, Policy.AS_IS); // AND：AS_IS策略(需要特殊处理)
    // TRUE OR NULL yields TRUE // TRUE OR NULL返回TRUE
    // FALSE OR NULL yields NULL // FALSE OR NULL返回NULL
    map.put(SqlKind.OR, Policy.AS_IS); // OR：AS_IS策略(需要特殊处理)

    // Expression types with custom handlers. // 具有自定义处理器的表达式类型
    map.put(SqlKind.LITERAL, Policy.CUSTOM); // LITERAL(字面量)：CUSTOM策略(自定义处理)

    map.put(SqlKind.EXISTS, Policy.NOT_NULL); // EXISTS：NOT_NULL策略(从不为null)
    map.put(SqlKind.IS_DISTINCT_FROM, Policy.NOT_NULL); // IS_DISTINCT_FROM：NOT_NULL策略
    map.put(SqlKind.IS_NOT_DISTINCT_FROM, Policy.NOT_NULL); // IS_NOT_DISTINCT_FROM：NOT_NULL策略
    map.put(SqlKind.IS_NULL, Policy.NOT_NULL); // IS_NULL：NOT_NULL策略(总是返回TRUE或FALSE)
    map.put(SqlKind.IS_NOT_NULL, Policy.NOT_NULL); // IS_NOT_NULL：NOT_NULL策略
    map.put(SqlKind.IS_TRUE, Policy.NOT_NULL); // IS_TRUE：NOT_NULL策略
    map.put(SqlKind.IS_NOT_TRUE, Policy.NOT_NULL); // IS_NOT_TRUE：NOT_NULL策略
    map.put(SqlKind.IS_FALSE, Policy.NOT_NULL); // IS_FALSE：NOT_NULL策略
    map.put(SqlKind.IS_NOT_FALSE, Policy.NOT_NULL); // IS_NOT_FALSE：NOT_NULL策略

    map.put(SqlKind.NOT, Policy.ANY); // NOT：ANY策略(任一参数为null则结果为null)
    map.put(SqlKind.EQUALS, Policy.ANY); // EQUALS(=)：ANY策略
    map.put(SqlKind.NOT_EQUALS, Policy.ANY); // NOT_EQUALS(<>)：ANY策略
    map.put(SqlKind.LESS_THAN, Policy.ANY); // LESS_THAN(<)：ANY策略
    map.put(SqlKind.LESS_THAN_OR_EQUAL, Policy.ANY); // LESS_THAN_OR_EQUAL(<=)：ANY策略
    map.put(SqlKind.GREATER_THAN, Policy.ANY); // GREATER_THAN(>)：ANY策略
    map.put(SqlKind.GREATER_THAN_OR_EQUAL, Policy.ANY); // GREATER_THAN_OR_EQUAL(>=)：ANY策略
    map.put(SqlKind.LIKE, Policy.ANY); // LIKE：ANY策略
    map.put(SqlKind.SIMILAR, Policy.ANY); // SIMILAR：ANY策略
    map.put(SqlKind.PLUS, Policy.ANY); // PLUS(+)：ANY策略
    map.put(SqlKind.PLUS_PREFIX, Policy.ANY); // PLUS_PREFIX(+x)：ANY策略
    map.put(SqlKind.MINUS, Policy.ANY); // MINUS(-)：ANY策略
    map.put(SqlKind.MINUS_PREFIX, Policy.ANY); // MINUS_PREFIX(-x)：ANY策略
    map.put(SqlKind.TIMES, Policy.ANY); // TIMES(*)：ANY策略
    map.put(SqlKind.CHECKED_PLUS, Policy.ANY); // CHECKED_PLUS(带溢出检查的+)：ANY策略
    map.put(SqlKind.CHECKED_MINUS, Policy.ANY); // CHECKED_MINUS(带溢出检查的-)：ANY策略
    map.put(SqlKind.CHECKED_MINUS_PREFIX, Policy.ANY); // CHECKED_MINUS_PREFIX(带溢出检查的-x)：ANY策略
    map.put(SqlKind.CHECKED_TIMES, Policy.ANY); // CHECKED_TIMES(带溢出检查的*)：ANY策略
    map.put(SqlKind.CHECKED_DIVIDE, Policy.ANY); // CHECKED_DIVIDE(带溢出检查的/)：ANY策略

    map.put(SqlKind.DIVIDE, Policy.ANY); // DIVIDE(/)：ANY策略
    map.put(SqlKind.CAST, Policy.ANY); // CAST(类型转换)：ANY策略
    map.put(SqlKind.REINTERPRET, Policy.ANY); // REINTERPRET(重新解释)：ANY策略
    map.put(SqlKind.TRIM, Policy.ANY); // TRIM(去空格)：ANY策略
    map.put(SqlKind.LTRIM, Policy.ANY); // LTRIM(去左空格)：ANY策略
    map.put(SqlKind.RTRIM, Policy.ANY); // RTRIM(去右空格)：ANY策略
    map.put(SqlKind.CEIL, Policy.ANY); // CEIL(向上取整)：ANY策略
    map.put(SqlKind.FLOOR, Policy.ANY); // FLOOR(向下取整)：ANY策略
    map.put(SqlKind.EXTRACT, Policy.ANY); // EXTRACT(提取日期部分)：ANY策略
    map.put(SqlKind.GREATEST, Policy.ANY); // GREATEST(最大值)：ANY策略
    map.put(SqlKind.LEAST, Policy.ANY); // LEAST(最小值)：ANY策略
    map.put(SqlKind.TIMESTAMP_ADD, Policy.ANY); // TIMESTAMP_ADD(时间戳加法)：ANY策略
    map.put(SqlKind.TIMESTAMP_DIFF, Policy.ANY); // TIMESTAMP_DIFF(时间戳差值)：ANY策略
    map.put(SqlKind.ITEM, Policy.ANY); // ITEM(数组/映射访问)：ANY策略

    // Assume that any other expressions cannot be simplified. // 假设任何其他表达式都无法被简化
    for (SqlKind k // 遍历所有SqlKind
        : Iterables.concat(SqlKind.EXPRESSION, SqlKind.AGGREGATE)) { // 连接EXPRESSION和AGGREGATE集合
      if (!map.containsKey(k)) { // 如果该SqlKind还没有在映射表中
        map.put(k, Policy.AS_IS); // 添加到映射表，策略为AS_IS
      }
    }
    return map; // 返回构建完成的映射表
  }

  /** How whether an operator's operands are null affects whether a call to // 操作符的操作数是否为null如何影响该操作符调用结果是否为null
   * that operator evaluates to null. */
  public enum Policy { // 定义Policy枚举，描述操作符的null传播策略
    /** This kind of expression is never null. No need to look at its arguments, // 这种表达式从不为null。不需要查看其参数(如果有)
     * if it has any. */
    NOT_NULL, // NOT_NULL策略：表达式从不为null

    /** This kind of expression has its own particular rules about whether it // 这种表达式有自己的特定规则来判断是否为null
     * is null. */
    CUSTOM, // CUSTOM策略：表达式有自定义的null判断规则

    /** This kind of expression is null if and only if at least one of its // 这种表达式当且仅当至少有一个参数为null时才为null
     * arguments is null. */
    ANY, // ANY策略：任一参数为null则表达式为null

    /** This kind of expression may be null. There is no way to rewrite. */ // 这种表达式可能为null。无法重写/简化
    AS_IS, // AS_IS策略：表达式保持原样，无法推断null传播
  }
}
