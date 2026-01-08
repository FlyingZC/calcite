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
package org.apache.calcite.rel.core;

import org.apache.calcite.linq4j.Ord;  // 导入Ord类,用于为元素添加序号索引的工具类
import org.apache.calcite.rel.RelCollation;  // 导入RelCollation类,表示关系代数中的排序规则
import org.apache.calcite.rel.RelCollations;  // 导入RelCollations工具类,提供排序相关的静态方法
import org.apache.calcite.rel.RelNode;  // 导入RelNode类,表示关系代数节点(所有关系操作符的基类)
import org.apache.calcite.rel.type.RelDataType;  // 导入RelDataType类,表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory;  // 导入RelDataTypeFactory类,用于创建关系数据类型的工厂
import org.apache.calcite.rex.RexNode;  // 导入RexNode类,表示行表达式(Row Expression)节点
import org.apache.calcite.rex.RexUtil;  // 导入RexUtil工具类,提供行表达式相关的工具方法
import org.apache.calcite.sql.SqlAggFunction;  // 导入SqlAggFunction类,表示SQL聚合函数(如SUM, COUNT等)
import org.apache.calcite.sql.SqlKind;  // 导入SqlKind枚举,表示SQL操作的类型
import org.apache.calcite.sql.parser.SqlParserPos;  // 导入SqlParserPos类,表示SQL解析位置信息
import org.apache.calcite.sql.type.SqlTypeUtil;  // 导入SqlTypeUtil工具类,提供SQL类型相关的工具方法
import org.apache.calcite.util.ImmutableBitSet;  // 导入ImmutableBitSet类,表示不可变的位集合
import org.apache.calcite.util.Optionality;  // 导入Optionality枚举,表示某个选项的可选性(必需、可选、忽略)
import org.apache.calcite.util.mapping.Mapping;  // 导入Mapping接口,表示映射关系
import org.apache.calcite.util.mapping.Mappings;  // 导入Mappings工具类,提供映射相关的静态方法

import com.google.common.collect.ImmutableList;  // 导入Google Guava的ImmutableList类,表示不可变列表

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入Nullable注解,用于标记可空类型

import java.util.List;  // 导入Java标准库的List接口
import java.util.Objects;  // 导入Java标准库的Objects类,提供对象操作的工具方法

import static com.google.common.base.Preconditions.checkArgument;  // 静态导入Preconditions的checkArgument方法,用于参数校验

import static java.util.Objects.requireNonNull;  // 静态导入Objects的requireNonNull方法,用于非空检查

/**
 * Call to an aggregate function within an {@link org.apache.calcite.rel.core.Aggregate}.
 * 聚合调用(AggregateCall)类 - 表示在聚合操作(Aggregate)中的一个聚合函数调用
 * 
 * 类作用:
 * 这个类封装了SQL中聚合函数调用(如SUM(salary), COUNT(*), AVG(price))的所有信息。
 * 它是Calcite关系代数中聚合操作的核心组件,每个聚合函数调用都会对应一个AggregateCall对象。
 * 
 * 主要功能:
 * 1. 存储聚合函数的类型(SUM, COUNT, AVG, MAX, MIN等)
 * 2. 存储聚合函数的参数(参数索引列表)
 * 3. 存储聚合函数的修饰符(DISTINCT, APPROXIMATE, IGNORE NULLS等)
 * 4. 存储聚合函数的过滤条件(FILTER子句)
 * 5. 存储聚合函数的排序要求(WITHIN GROUP子句)
 * 6. 存储聚合函数的返回类型和名称
 * 
 * 使用场景:
 * - 在SQL解析阶段创建,表示解析出的聚合函数调用
 * - 在查询优化阶段被修改和转换(如参数重映射、类型推导)
 * - 在查询执行阶段被转换为具体的物理实现
 * 
 * 示例:
 * SQL: SELECT dept_id, SUM(DISTINCT salary) FILTER (WHERE salary > 5000) FROM emp GROUP BY dept_id
 * 对应的AggregateCall包含:
 * - aggFunction: SUM
 * - distinct: true
 * - argList: [salary字段索引]
 * - filterArg: 过滤条件表达式索引
 */
public class AggregateCall {
  //~ Instance fields --------------------------------------------------------

  /**
   * Some aggregate calls may produce runtime errors.  For these
   * we need to keep around the original source position information
   * so that the runtime can produce error messages pointing to
   * the offending source operation.  For "safe" aggregations
   * this field may be ZERO.
   * SQL解析位置信息 - 记录聚合函数在原始SQL语句中的位置
   * 
   * 字段作用:
   * - 存储聚合函数在SQL源代码中的位置信息(行号、列号等)
   * - 当聚合函数在运行时产生错误时,可以定位到具体的源代码位置
   * - 对于不会产生运行时错误的聚合函数(如COUNT(*)),该值可能为ZERO
   * - 主要用于错误报告和调试,帮助用户定位问题
   * 
   * 使用场景:
   * - 在SQL解析时由解析器设置
   * - 在运行时发生错误时,通过该位置信息生成详细的错误消息
   * - 某些聚合函数(如除法、类型转换)可能产生运行时异常,需要保留位置信息
   */
  private final SqlParserPos pos;  // SQL解析位置,用于错误定位
  private final SqlAggFunction aggFunction;  // 聚合函数对象,表示具体的聚合函数类型(SUM, COUNT, AVG等)

  private final boolean distinct;  // 是否使用DISTINCT修饰符,如COUNT(DISTINCT empno)
  private final boolean approximate;  // 是否使用近似计算,如APPROX_COUNT_DISTINCT
  private final boolean ignoreNulls;  // 是否忽略NULL值,如IGNORE NULLS
  public final RelDataType type;  // 聚合函数的返回类型,如SUM返回的数值类型
  public final @Nullable String name;  // 聚合结果的名称(别名),可能为null
  public final List<RexNode> rexList;  // 预参数列表,存储聚合函数调用前需要计算的RexNode表达式

  // We considered using ImmutableIntList but we would not save much memory:
  // since all values are small, ImmutableList uses cached Integer values.
  // 参数索引列表 - 存储聚合函数参数在输入关系中的索引位置
  // 说明: 我们考虑过使用ImmutableIntList,但不会节省太多内存,因为所有值都很小,ImmutableList使用缓存的Integer值
  private final ImmutableList<Integer> argList;  // 不可变的参数索引列表,每个整数表示输入关系中的一个字段索引
  public final int filterArg;  // 过滤参数索引,表示FILTER (WHERE ...)子句对应的参数索引,-1表示没有过滤条件
  public final @Nullable ImmutableBitSet distinctKeys;  // 去重键集合,表示在聚合前需要根据哪些字段进行去重,null表示不需要
  public final RelCollation collation;  // 排序规则,表示WITHIN GROUP子句指定的排序要求

  //~ Constructors -----------------------------------------------------------

  /**
   * Creates an AggregateCall.
   *
   * @param aggFunction Aggregate function
   * @param distinct    Whether distinct
   * @param argList     List of ordinals of arguments
   * @param type        Result type
   * @param name        Name (may be null)
   */
  @Deprecated // to be removed before 2.0  // 已废弃,将在2.0版本前移除
  public AggregateCall(  // 构造方法1:创建一个简单的AggregateCall对象(已废弃)
      SqlAggFunction aggFunction,  // 参数:聚合函数对象
      boolean distinct,  // 参数:是否使用DISTINCT
      List<Integer> argList,  // 参数:参数索引列表
      RelDataType type,  // 参数:返回类型
      String name) {  // 参数:名称(可能为null)
    this(SqlParserPos.ZERO, aggFunction, distinct, false, false,  // 调用完整构造函数,位置设为ZERO,approximate和ignoreNulls设为false
        ImmutableList.of(), argList, -1, null,  // rexList为空,filterArg为-1(无过滤),distinctKeys为null
        RelCollations.EMPTY, type, name);  // collation为空,使用传入的type和name
  }

  /**
   * Creates an AggregateCall.
   *
   * @param pos         Source position for this aggregate.
   *                    Ideally it should only be ZERO when the aggregate
   *                    can never fail at runtime.
   * @param aggFunction Aggregate function
   * @param distinct    Whether distinct
   * @param approximate Whether approximate
   * @param rexList     List of pre-arguments
   * @param argList     List of ordinals of arguments
   * @param filterArg   Ordinal of filter argument (the
   *                    {@code FILTER (WHERE ...)} clause in SQL), or -1
   * @param distinctKeys Ordinals of fields to make values distinct on before
   *                    aggregating, or null
   * @param collation   How to sort values before aggregation (the
   *                    {@code WITHIN GROUP} clause in SQL)
   * @param type        Result type
   * @param name        Name (may be null)
   */
  private AggregateCall(SqlParserPos pos, SqlAggFunction aggFunction, boolean distinct,  // 私有构造方法:创建完整的AggregateCall对象
      boolean approximate, boolean ignoreNulls,  // 参数:是否近似计算,是否忽略NULL
      List<RexNode> rexList, List<Integer> argList,  // 参数:预参数列表,参数索引列表
      int filterArg, @Nullable ImmutableBitSet distinctKeys,  // 参数:过滤参数索引,去重键集合
      RelCollation collation, RelDataType type, @Nullable String name) {  // 参数:排序规则,返回类型,名称
    this.pos = pos;  // 设置SQL解析位置
    this.type = requireNonNull(type, "type");  // 设置返回类型,要求非空
    this.name = name;  // 设置名称
    this.aggFunction = requireNonNull(aggFunction, "aggFunction");  // 设置聚合函数,要求非空
    this.argList = ImmutableList.copyOf(argList);  // 创建参数索引列表的不可变副本
    this.rexList = ImmutableList.copyOf(rexList);  // 创建预参数列表的不可变副本
    this.distinctKeys = distinctKeys;  // 设置去重键集合
    this.filterArg = filterArg;  // 设置过滤参数索引
    this.collation = requireNonNull(collation, "collation");  // 设置排序规则,要求非空
    this.distinct = distinct;  // 设置是否使用DISTINCT
    this.approximate = approximate;  // 设置是否使用近似计算
    this.ignoreNulls = ignoreNulls;  // 设置是否忽略NULL
    checkArgument(aggFunction.getDistinctOptionality() != Optionality.IGNORED  // 校验:如果聚合函数不支持DISTINCT,则distinct必须为false
            || !distinct,  // 条件:如果聚合函数的DISTINCT选项是IGNORED,则distinct不能为true
        "DISTINCT has no effect for this aggregate function, so must be false");  // 错误消息:DISTINCT对该聚合函数无效,必须为false
    checkArgument(filterArg < 0 || aggFunction.allowsFilter());  // 校验:如果有过滤参数,则聚合函数必须支持FILTER
  }

  //~ Methods ----------------------------------------------------------------

  @Deprecated // to be removed before 2.0  // 已废弃,将在2.0版本前移除
  public static AggregateCall create(SqlAggFunction aggFunction,  // 静态工厂方法:创建AggregateCall(已废弃)
      boolean distinct, List<Integer> argList, int groupCount, RelNode input,  // 参数:聚合函数,DISTINCT标志,参数列表,分组数量,输入关系
      @Nullable RelDataType type, @Nullable String name) {  // 参数:返回类型,名称
    return create(aggFunction, distinct, false, false,  // 调用完整create方法,approximate和ignoreNulls设为false
        ImmutableList.of(), argList, -1,  // rexList为空,filterArg为-1
        null, RelCollations.EMPTY, groupCount, input, type, name);  // distinctKeys为null,collation为空
  }

  @Deprecated // to be removed before 2.0  // 已废弃,将在2.0版本前移除
  public static AggregateCall create(SqlAggFunction aggFunction,  // 静态工厂方法2:创建带过滤参数的AggregateCall(已废弃)
      boolean distinct, List<Integer> argList, int filterArg, int groupCount,  // 参数:聚合函数,DISTINCT标志,参数列表,过滤参数索引,分组数量
      RelNode input, @Nullable RelDataType type, @Nullable String name) {  // 参数:输入关系,返回类型,名称
    return create(aggFunction, distinct, false, false,  // 调用完整create方法,approximate和ignoreNulls设为false
        ImmutableList.of(), argList, filterArg,  // rexList为空,使用传入的filterArg
        null, RelCollations.EMPTY, groupCount, input, type, name);  // distinctKeys为null,collation为空
  }

  @Deprecated // to be removed before 2.0  // 已废弃,将在2.0版本前移除
  public static AggregateCall create(SqlAggFunction aggFunction,  // 静态工厂方法3:创建带近似计算的AggregateCall(已废弃)
      boolean distinct, boolean approximate, List<Integer> argList,  // 参数:聚合函数,DISTINCT标志,近似计算标志,参数列表
      int filterArg, int groupCount,  // 参数:过滤参数索引,分组数量
      RelNode input, @Nullable RelDataType type, @Nullable String name) {  // 参数:输入关系,返回类型,名称
    return create(aggFunction, distinct, approximate, false,  // 调用完整create方法,ignoreNulls设为false
        ImmutableList.of(), argList,  // rexList为空
        filterArg, null, RelCollations.EMPTY, groupCount, input, type, name);  // 使用传入的filterArg,distinctKeys为null,collation为空
  }

  @Deprecated // to be removed before 2.0  // 已废弃,将在2.0版本前移除
  public static AggregateCall create(SqlAggFunction aggFunction,  // 静态工厂方法4:创建带排序规则的AggregateCall(已废弃)
      boolean distinct, boolean approximate, List<Integer> argList,  // 参数:聚合函数,DISTINCT标志,近似计算标志,参数列表
      int filterArg, RelCollation collation, int groupCount,  // 参数:过滤参数索引,排序规则,分组数量
      RelNode input, @Nullable RelDataType type, @Nullable String name) {  // 参数:输入关系,返回类型,名称
    return create(aggFunction, distinct, approximate, false,  // 调用完整create方法,ignoreNulls设为false
        ImmutableList.of(), argList, filterArg,  // rexList为空,使用传入的filterArg
        null, collation, groupCount, input, type, name);  // distinctKeys为null,使用传入的collation
  }

  @Deprecated // to be removed before 2.0  // 已废弃,将在2.0版本前移除
  public static AggregateCall create(SqlAggFunction aggFunction,  // 静态工厂方法5:创建完整参数的AggregateCall(已废弃)
      boolean distinct, boolean approximate, boolean ignoreNulls,  // 参数:聚合函数,DISTINCT标志,近似计算标志,忽略NULL标志
      List<Integer> argList, int filterArg,  // 参数:参数列表,过滤参数索引
      @Nullable ImmutableBitSet distinctKeys, RelCollation collation,  // 参数:去重键集合,排序规则
      int groupCount,  // 参数:分组数量
      RelNode input, @Nullable RelDataType type, @Nullable String name) {  // 参数:输入关系,返回类型,名称
    return create(aggFunction, distinct, approximate, ignoreNulls,  // 调用完整create方法
        ImmutableList.of(), argList, filterArg,  // rexList为空,使用传入的参数
        distinctKeys, collation, groupCount, input, type, name);  // 使用传入的所有参数
  }

    /** Creates an AggregateCall, inferring its type if {@code type} is null. */
  public static AggregateCall create(SqlAggFunction aggFunction,  // 静态工厂方法6:创建AggregateCall,如果type为null则自动推导类型
      boolean distinct, boolean approximate, boolean ignoreNulls,  // 参数:聚合函数,DISTINCT标志,近似计算标志,忽略NULL标志
      List<RexNode> rexList, List<Integer> argList, int filterArg,  // 参数:预参数列表,参数列表,过滤参数索引
      @Nullable ImmutableBitSet distinctKeys, RelCollation collation,  // 参数:去重键集合,排序规则
      int groupCount,  // 参数:分组数量
      RelNode input, @Nullable RelDataType type, @Nullable String name) {  // 参数:输入关系,返回类型(可为null),名称
    return create(SqlParserPos.ZERO, aggFunction, distinct, approximate,  // 调用带位置参数的create方法,位置设为ZERO
        ignoreNulls, rexList, argList, filterArg, distinctKeys, collation, groupCount,  // 传递所有参数
        input, type, name);  // 传递输入关系、类型和名称
  }

  public static AggregateCall create(SqlParserPos pos, SqlAggFunction aggFunction,  // 静态工厂方法7:创建带位置参数的AggregateCall
      boolean distinct, boolean approximate, boolean ignoreNulls,  // 参数:SQL解析位置,聚合函数,DISTINCT标志,近似计算标志,忽略NULL标志
      List<RexNode> rexList, List<Integer> argList, int filterArg,  // 参数:预参数列表,参数列表,过滤参数索引
      @Nullable ImmutableBitSet distinctKeys, RelCollation collation,  // 参数:去重键集合,排序规则
      int groupCount,  // 参数:分组数量
      RelNode input, @Nullable RelDataType type, @Nullable String name) {  // 参数:输入关系,返回类型(可为null),名称
    if (type == null) {  // 如果返回类型为null,则需要推导类型
      final RelDataTypeFactory typeFactory =  // 获取类型工厂
          input.getCluster().getTypeFactory();  // 从输入关系的集群中获取类型工厂
      final List<RelDataType> preTypes = RexUtil.types(rexList);  // 获取预参数的类型列表
      final List<RelDataType> types =  // 获取参数的类型列表
          SqlTypeUtil.projectTypes(input.getRowType(), argList);  // 根据参数索引从输入行类型中投影出参数类型
      final Aggregate.AggCallBinding callBinding =  // 创建聚合函数调用绑定对象
          new Aggregate.AggCallBinding(typeFactory, aggFunction, preTypes,  // 传入类型工厂、聚合函数、预参数类型
              types, groupCount, filterArg >= 0);  // 传入参数类型、分组数量、是否有过滤
      type = aggFunction.inferReturnType(callBinding);  // 调用聚合函数的类型推导方法,推导出返回类型
    }  // 类型推导完成
    return create(pos, aggFunction, distinct, approximate, ignoreNulls,  // 调用最终的create方法创建AggregateCall
        rexList, argList, filterArg, distinctKeys, collation, type, name);  // 传递所有参数,包括推导出的类型
  }

  @Deprecated // to be removed before 2.0  // 已废弃,将在2.0版本前移除
  public static AggregateCall create(SqlAggFunction aggFunction,  // 静态工厂方法8:创建简单AggregateCall(已废弃)
      boolean distinct, List<Integer> argList, int filterArg, RelDataType type,  // 参数:聚合函数,DISTINCT标志,参数列表,过滤参数索引,返回类型
      @Nullable String name) {  // 参数:名称
    return create(aggFunction, distinct, false, false,  // 调用完整create方法,approximate和ignoreNulls设为false
        ImmutableList.of(), argList, filterArg, null,  // rexList为空,使用传入的filterArg,distinctKeys为null
        RelCollations.EMPTY, type, name);  // collation为空,使用传入的type和name
  }

  @Deprecated // to be removed before 2.0  // 已废弃,将在2.0版本前移除
  public static AggregateCall create(SqlAggFunction aggFunction,  // 静态工厂方法9:创建带近似计算的AggregateCall(已废弃)
      boolean distinct, boolean approximate, List<Integer> argList,  // 参数:聚合函数,DISTINCT标志,近似计算标志,参数列表
      int filterArg, RelDataType type, @Nullable String name) {  // 参数:过滤参数索引,返回类型,名称
    return create(aggFunction, distinct, approximate, false,  // 调用完整create方法,ignoreNulls设为false
        ImmutableList.of(), argList, filterArg,  // rexList为空,使用传入的filterArg
        null, RelCollations.EMPTY, type, name);  // distinctKeys为null,collation为空
  }

  @Deprecated // to be removed before 2.0  // 已废弃,将在2.0版本前移除
  public static AggregateCall create(SqlAggFunction aggFunction,  // 静态工厂方法10:创建带排序规则的AggregateCall(已废弃)
      boolean distinct, boolean approximate, List<Integer> argList,  // 参数:聚合函数,DISTINCT标志,近似计算标志,参数列表
      int filterArg, RelCollation collation, RelDataType type, @Nullable String name) {  // 参数:过滤参数索引,排序规则,返回类型,名称
    return create(aggFunction, distinct, approximate, false,  // 调用完整create方法,ignoreNulls设为false
        ImmutableList.of(), argList, filterArg,  // rexList为空,使用传入的filterArg
        null, collation, type, name);  // distinctKeys为null,使用传入的collation
  }

  @Deprecated // to be removed before 2.0  // 已废弃,将在2.0版本前移除
  public static AggregateCall create(SqlAggFunction aggFunction,  // 静态工厂方法11:创建完整参数的AggregateCall(已废弃)
      boolean distinct, boolean approximate, boolean ignoreNulls,  // 参数:聚合函数,DISTINCT标志,近似计算标志,忽略NULL标志
      List<Integer> argList, int filterArg, RelCollation collation,  // 参数:参数列表,过滤参数索引,排序规则
      RelDataType type, @Nullable String name) {  // 参数:返回类型,名称
    return create(aggFunction, distinct, approximate, ignoreNulls,  // 调用完整create方法
        ImmutableList.of(), argList,  // rexList为空
        filterArg, null, collation, type, name);  // 使用传入的参数,distinctKeys为null
  }

  /** Creates an AggregateCall. */
  public static AggregateCall create(SqlAggFunction aggFunction,  // 静态工厂方法12:创建AggregateCall(推荐使用)
      boolean distinct, boolean approximate, boolean ignoreNulls,  // 参数:聚合函数,DISTINCT标志,近似计算标志,忽略NULL标志
      List<RexNode> rexList, List<Integer> argList, int filterArg,  // 参数:预参数列表,参数列表,过滤参数索引
      @Nullable ImmutableBitSet distinctKeys, RelCollation collation,  // 参数:去重键集合,排序规则
      RelDataType type, @Nullable String name) {  // 参数:返回类型,名称
    return create(SqlParserPos.ZERO, aggFunction, distinct, approximate,  // 调用带位置参数的create方法,位置设为ZERO
        ignoreNulls, rexList, argList, filterArg, distinctKeys, collation, type, name);  // 传递所有参数
  }

  public static AggregateCall create(SqlParserPos pos, SqlAggFunction aggFunction,  // 静态工厂方法13:创建带位置参数的AggregateCall(最终实现)
      boolean distinct, boolean approximate, boolean ignoreNulls,  // 参数:SQL解析位置,聚合函数,DISTINCT标志,近似计算标志,忽略NULL标志
      List<RexNode> rexList, List<Integer> argList, int filterArg,  // 参数:预参数列表,参数列表,过滤参数索引
      @Nullable ImmutableBitSet distinctKeys, RelCollation collation,  // 参数:去重键集合,排序规则
      RelDataType type, @Nullable String name) {  // 参数:返回类型,名称
    final boolean distinct2 = distinct  // 计算实际的distinct值
        && (aggFunction.getDistinctOptionality() != Optionality.IGNORED);  // 只有当聚合函数支持DISTINCT且传入的distinct为true时,实际的distinct才为true
    return new AggregateCall(pos, aggFunction, distinct2, approximate, ignoreNulls,  // 创建新的AggregateCall对象,使用计算后的distinct2
        rexList, argList, filterArg, distinctKeys, collation, type, name);  // 传递所有参数
  }

  /**
   * Returns whether this AggregateCall is distinct, as in <code>
   * COUNT(DISTINCT empno)</code>.
   *
   * @return whether distinct
   */
  public final boolean isDistinct() {  // 方法:判断是否使用DISTINCT修饰符
    return distinct;  // 返回distinct字段的值
  }

  /** Withs {@link #isDistinct()}. */
  public AggregateCall withDistinct(boolean distinct) {  // 方法:创建一个新的AggregateCall,修改DISTINCT标志
    return distinct == this.distinct ? this  // 如果新的distinct值与当前值相同,直接返回当前对象
        : new AggregateCall(pos, aggFunction, distinct, approximate, ignoreNulls,  // 否则创建新的AggregateCall对象,使用新的distinct值
            rexList, argList, filterArg, distinctKeys, collation, type, name);  // 其他参数保持不变
  }

  /**
   * Returns whether this AggregateCall is approximate, as in <code>
   * APPROX_COUNT_DISTINCT(empno)</code>.
   *
   * @return whether approximate
   */
  public final boolean isApproximate() {  // 方法:判断是否使用近似计算
    return approximate;  // 返回approximate字段的值
  }

  /** Withs {@link #isApproximate()}. */
  public AggregateCall withApproximate(boolean approximate) {  // 方法:创建一个新的AggregateCall,修改近似计算标志
    return approximate == this.approximate ? this  // 如果新的approximate值与当前值相同,直接返回当前对象
        : new AggregateCall(pos, aggFunction, distinct, approximate, ignoreNulls,  // 否则创建新的AggregateCall对象,使用新的approximate值
            rexList, argList, filterArg, distinctKeys, collation, type, name);  // 其他参数保持不变
  }

  /**
   * Returns whether this AggregateCall ignores nulls.
   *
   * @return whether ignore nulls
   */
  public final boolean ignoreNulls() {  // 方法:判断是否忽略NULL值
    return ignoreNulls;  // 返回ignoreNulls字段的值
  }

  /** Withs {@link #ignoreNulls()}. */
  public AggregateCall withIgnoreNulls(boolean ignoreNulls) {  // 方法:创建一个新的AggregateCall,修改忽略NULL标志
    return ignoreNulls == this.ignoreNulls ? this  // 如果新的ignoreNulls值与当前值相同,直接返回当前对象
        : new AggregateCall(pos, aggFunction, distinct, approximate, ignoreNulls,  // 否则创建新的AggregateCall对象,使用新的ignoreNulls值
            rexList, argList, filterArg, distinctKeys, collation, type, name);  // 其他参数保持不变
  }

  /**
   * Returns the aggregate function.
   *
   * @return aggregate function
   */
  public final SqlAggFunction getAggregation() {  // 方法:获取聚合函数对象
    return aggFunction;  // 返回aggFunction字段
  }

  /**
   * Returns the aggregate ordering definition (the {@code WITHIN GROUP} clause
   * in SQL), or the empty list if not specified.
   *
   * @return ordering definition
   */
  public RelCollation getCollation() {  // 方法:获取排序规则(WITHIN GROUP子句)
    return collation;  // 返回collation字段
  }

  /** Withs {@link #getCollation()}. */
  public AggregateCall withCollation(RelCollation collation) {  // 方法:创建一个新的AggregateCall,修改排序规则
    return collation.equals(this.collation) ? this  // 如果新的collation与当前值相同,直接返回当前对象
        : new AggregateCall(pos, aggFunction, distinct, approximate, ignoreNulls,  // 否则创建新的AggregateCall对象,使用新的collation值
            rexList, argList, filterArg, distinctKeys, collation, type, name);  // 其他参数保持不变
  }

  /**
   * Returns the ordinals of the arguments to this call.
   *
   * <p>The list is immutable.
   *
   * @return list of argument ordinals
   */
  public final List<Integer> getArgList() {  // 方法:获取参数索引列表
    return argList;  // 返回argList字段(不可变列表)
  }

  /** Withs {@link #getArgList()}. */
  public AggregateCall withArgList(List<Integer> argList) {  // 方法:创建一个新的AggregateCall,修改参数列表
    return argList.equals(this.argList) ? this  // 如果新的argList与当前值相同,直接返回当前对象
        : new AggregateCall(pos, aggFunction, distinct, approximate, ignoreNulls,  // 否则创建新的AggregateCall对象,使用新的argList值
            rexList, argList, filterArg, distinctKeys, collation, type, name);  // 其他参数保持不变
  }

  /** Withs {@link #distinctKeys}. */
  public AggregateCall withDistinctKeys(  // 方法:创建一个新的AggregateCall,修改去重键集合
      @Nullable ImmutableBitSet distinctKeys) {  // 参数:新的去重键集合
    return Objects.equals(distinctKeys, this.distinctKeys) ? this  // 如果新的distinctKeys与当前值相同,直接返回当前对象
        : new AggregateCall(pos, aggFunction, distinct, approximate, ignoreNulls,  // 否则创建新的AggregateCall对象,使用新的distinctKeys值
            rexList, argList, filterArg, distinctKeys, collation, type, name);  // 其他参数保持不变
  }

  /**
   * Returns the result type.
   *
   * @return result type
   */
  public final RelDataType getType() {  // 方法:获取返回类型
    return type;  // 返回type字段
  }

  /**
   * Returns the name.
   *
   * @return name
   */
  public @Nullable String getName() {  // 方法:获取名称
    return name;  // 返回name字段(可能为null)
  }

  /** Withs {@link #name}. */
  public AggregateCall withName(@Nullable String name) {  // 方法:创建一个新的AggregateCall,修改名称
    return Objects.equals(name, this.name) ? this  // 如果新的name与当前值相同,直接返回当前对象
        : new AggregateCall(pos, aggFunction, distinct, approximate, ignoreNulls,  // 否则创建新的AggregateCall对象,使用新的name值
            rexList, argList, filterArg, distinctKeys, collation, type, name);  // 其他参数保持不变
  }

  @Deprecated // to be removed before 2.0  // 已废弃,将在2.0版本前移除
  public AggregateCall rename(@Nullable String name) {  // 方法:重命名AggregateCall(已废弃,请使用withName)
    return withName(name);  // 调用withName方法
  }

  @Override public String toString() {  // 方法:将AggregateCall转换为字符串表示
    StringBuilder buf = new StringBuilder(aggFunction.toString());  // 创建StringBuilder,初始值为聚合函数名称
    buf.append("(");  // 添加左括号
    if (approximate) {  // 如果是近似计算
      buf.append("APPROXIMATE ");  // 添加"APPROXIMATE "前缀
    }  // 近似计算前缀添加完成
    if (distinct) {  // 如果使用DISTINCT
      buf.append(argList.isEmpty() ? "DISTINCT" : "DISTINCT ");  // 如果参数列表为空,添加"DISTINCT",否则添加"DISTINCT "
    }  // DISTINCT前缀添加完成
    int i = -1;  // 初始化索引计数器
    for (RexNode rexNode : rexList) {  // 遍历预参数列表
      if (++i > 0) {  // 如果不是第一个参数
        buf.append(", ");  // 添加逗号和空格分隔符
      }  // 分隔符添加完成
      buf.append(rexNode);  // 添加预参数的字符串表示
    }  // 预参数遍历完成
    for (Integer arg : argList) {  // 遍历参数索引列表
      if (++i > 0) {  // 如果不是第一个参数
        buf.append(", ");  // 添加逗号和空格分隔符
      }  // 分隔符添加完成
      buf.append("$");  // 添加"$"前缀表示参数引用
      buf.append(arg);  // 添加参数索引
    }  // 参数索引遍历完成
    buf.append(")");  // 添加右括号
    if (distinctKeys != null) {  // 如果有去重键集合
      buf.append(" WITHIN DISTINCT (");  // 添加" WITHIN DISTINCT ("前缀
      for (Ord<Integer> key : Ord.zip(distinctKeys)) {  // 遍历去重键集合(带序号)
        buf.append(key.i > 0 ? ", $" : "$");  // 如果不是第一个键,添加", $",否则添加"$"
        buf.append(key.e);  // 添加键的索引
      }  // 去重键遍历完成
      buf.append(")");  // 添加右括号
    }  // 去重键处理完成
    if (hasCollation()) {  // 如果有排序规则
      buf.append(" WITHIN GROUP (");  // 添加" WITHIN GROUP ("前缀
      buf.append(collation);  // 添加排序规则的字符串表示
      buf.append(")");  // 添加右括号
    }  // 排序规则处理完成
    if (hasFilter()) {  // 如果有过滤条件
      buf.append(" FILTER $");  // 添加" FILTER $"前缀
      buf.append(filterArg);  // 添加过滤参数索引
    }  // 过滤条件处理完成
    return buf.toString();  // 返回构建的字符串
  }

  /** Returns whether this AggregateCall has a filter argument. */
  public boolean hasFilter() {  // 方法:判断是否有过滤参数
    return filterArg >= 0;  // 如果filterArg大于等于0,表示有过滤参数
  }

  /** Returns true if this AggregateCall has a non-empty collation. Returns false otherwise. */
  public boolean hasCollation() {  // 方法:判断是否有非空的排序规则
    return !collation.equals(RelCollations.EMPTY);  // 如果collation不等于空排序规则,返回true
  }

  /** Withs {@link #filterArg}. */
  public AggregateCall withFilter(int filterArg) {  // 方法:创建一个新的AggregateCall,修改过滤参数索引
    return filterArg == this.filterArg ? this  // 如果新的filterArg与当前值相同,直接返回当前对象
        : new AggregateCall(pos, aggFunction, distinct, approximate, ignoreNulls,  // 否则创建新的AggregateCall对象,使用新的filterArg值
            rexList, argList, filterArg, distinctKeys, collation, type, name);  // 其他参数保持不变
  }

  public SqlParserPos getParserPosition() {  // 方法:获取SQL解析位置
    return this.pos;  // 返回pos字段
  }

  @Override public boolean equals(@Nullable Object o) {  // 方法:判断对象是否相等
    // Intentionally ignore the position  // 故意忽略位置信息,因为位置不影响聚合调用的语义
    return o == this  // 如果是同一个对象,返回true
        || o instanceof AggregateCall  // 或者o是AggregateCall的实例
        && aggFunction.equals(((AggregateCall) o).aggFunction)  // 并且聚合函数相等
        && distinct == ((AggregateCall) o).distinct  // 并且DISTINCT标志相等
        && approximate == ((AggregateCall) o).approximate  // 并且近似计算标志相等
        && ignoreNulls == ((AggregateCall) o).ignoreNulls  // 并且忽略NULL标志相等
        && argList.equals(((AggregateCall) o).argList)  // 并且参数列表相等
        && filterArg == ((AggregateCall) o).filterArg  // 并且过滤参数索引相等
        && Objects.equals(distinctKeys, ((AggregateCall) o).distinctKeys)  // 并且去重键集合相等
        && collation.equals(((AggregateCall) o).collation);  // 并且排序规则相等
  }

  @Override public int hashCode() {  // 方法:计算对象的哈希码
    // Ignore the position!  // 忽略位置信息
    return Objects.hash(aggFunction, distinct, approximate, ignoreNulls,  // 使用Objects.hash方法计算哈希码
        rexList, argList, filterArg, distinctKeys, collation);  // 包含所有关键字段
  }

  /**
   * Creates a binding of this call in the context of an
   * {@link org.apache.calcite.rel.logical.LogicalAggregate},
   * which can then be used to infer the return type.
   */
  public Aggregate.AggCallBinding createBinding(  // 方法:创建聚合函数调用绑定对象
      Aggregate aggregateRelBase) {  // 参数:聚合关系节点
    final RelDataType rowType = aggregateRelBase.getInput().getRowType();  // 获取输入关系的行类型
    final RelDataTypeFactory typeFactory =  // 获取类型工厂
        aggregateRelBase.getCluster().getTypeFactory();  // 从聚合关系的集群中获取类型工厂

    if (aggFunction.getKind() == SqlKind.PERCENTILE_DISC  // 如果是百分位离散函数(PERCENTILE_DISC)
        || aggFunction.getKind() == SqlKind.PERCENTILE_CONT) {  // 或者是百分位连续函数(PERCENTILE_CONT)
      assert collation.getKeys().size() == 1;  // 断言排序键的数量为1
      return new Aggregate.PercentileDiscAggCallBinding(typeFactory,  // 返回百分位离散聚合调用绑定对象
          aggFunction, SqlTypeUtil.projectTypes(rowType, argList),  // 传入类型工厂,聚合函数,参数类型
          SqlTypeUtil.projectTypes(rowType, collation.getKeys()).get(0),  // 传入排序键类型(取第一个)
          aggregateRelBase.getGroupCount(), hasFilter());  // 传入分组数量,是否有过滤
    }  // 百分位函数处理完成
    return new Aggregate.AggCallBinding(typeFactory, aggFunction,  // 返回普通聚合调用绑定对象
        RexUtil.types(rexList), SqlTypeUtil.projectTypes(rowType, argList),  // 传入类型工厂,聚合函数,预参数类型,参数类型
        aggregateRelBase.getGroupCount(), hasFilter());  // 传入分组数量,是否有过滤
  }

  /**
   * Creates an equivalent AggregateCall with new argument ordinals.
   *
   * @see #transform(Mappings.TargetMapping)
   *
   * @param argList Arguments
   * @return AggregateCall that suits new inputs and GROUP BY columns
   */
  @Deprecated // to be removed before 2.0  // 已废弃,将在2.0版本前移除
  public AggregateCall copy(List<Integer> argList, int filterArg,  // 方法:创建AggregateCall的副本,修改参数列表和过滤参数(已废弃)
      @Nullable ImmutableBitSet distinctKeys, RelCollation collation) {  // 参数:新的参数列表,过滤参数索引,去重键集合,排序规则
    return new AggregateCall(pos, aggFunction, distinct, approximate, ignoreNulls,  // 创建新的AggregateCall对象
        rexList, argList, filterArg, distinctKeys, collation, type, name);  // 使用传入的新参数,其他参数保持不变
  }

  @Deprecated // to be removed before 2.0  // 已废弃,将在2.0版本前移除
  public AggregateCall copy(List<Integer> argList, int filterArg,  // 方法:创建AggregateCall的副本,修改参数列表和过滤参数(已废弃)
      RelCollation collation) {  // 参数:新的参数列表,过滤参数索引,排序规则
    // ignoring distinctKeys is error-prone  // 忽略distinctKeys容易出错
    return new AggregateCall(pos, aggFunction, distinct, approximate, ignoreNulls,  // 创建新的AggregateCall对象
        rexList, argList, filterArg, distinctKeys, collation, type, name);  // 使用传入的新参数,保留原有的distinctKeys
  }

  @Deprecated // to be removed before 2.0  // 已废弃,将在2.0版本前移除
  public AggregateCall copy(List<Integer> argList, int filterArg) {  // 方法:创建AggregateCall的副本,修改参数列表和过滤参数(已废弃)
    // ignoring distinctKeys, collation is error-prone  // 忽略distinctKeys和collation容易出错
    return new AggregateCall(pos, aggFunction, distinct, approximate, ignoreNulls,  // 创建新的AggregateCall对象
        rexList, argList, filterArg, distinctKeys, collation, type, name);  // 使用传入的新参数,保留原有的distinctKeys和collation
  }

  @Deprecated // to be removed before 2.0  // 已废弃,将在2.0版本前移除
  public AggregateCall copy(List<Integer> argList) {  // 方法:创建AggregateCall的副本,仅修改参数列表(已废弃)
    // ignoring filterArg, distinctKeys, collation is error-prone  // 忽略filterArg、distinctKeys和collation容易出错
    return new AggregateCall(pos, aggFunction, distinct, approximate, ignoreNulls,  // 创建新的AggregateCall对象
        rexList, argList, filterArg, distinctKeys, collation, type, name);  // 使用传入的新参数,保留原有的filterArg、distinctKeys和collation
  }

  /**
   * Creates an equivalent AggregateCall that is adapted to a new input types
   * and/or number of columns in GROUP BY.
   *
   * @param input            Relation that will be input of Aggregate
   * @param argList          Argument indices of the new call in the input
   * @param filterArg        Index of the filter, or -1
   * @param oldGroupKeyCount number of columns in GROUP BY of old aggregate
   * @param newGroupKeyCount number of columns in GROUP BY of new aggregate
   * @return AggregateCall that suits new inputs and GROUP BY columns
   */
  public AggregateCall adaptTo(RelNode input, List<Integer> argList,  // 方法:创建适应新输入和分组列的AggregateCall
      int filterArg, int oldGroupKeyCount, int newGroupKeyCount) {  // 参数:输入关系,参数列表,过滤参数索引,旧分组数量,新分组数量
    // The return type of aggregate call need to be recomputed.
    // Since it might depend on the number of columns in GROUP BY.
    // 聚合调用的返回类型需要重新计算,因为它可能依赖于GROUP BY中的列数
    final RelDataType newType =  // 计算新的返回类型
        oldGroupKeyCount == newGroupKeyCount  // 如果分组数量没有变化
            && argList.equals(this.argList)  // 并且参数列表没有变化
            && filterArg == this.filterArg  // 并且过滤参数索引没有变化
            ? type  // 则使用原有的返回类型
            : null;  // 否则设为null,需要重新推导
    return create(pos, aggFunction, distinct, approximate, ignoreNulls,  // 调用create方法创建新的AggregateCall
        rexList, argList, filterArg, distinctKeys, collation,  // 传递所有参数
        newGroupKeyCount, input, newType, getName());  // 传递新的分组数量,输入关系,新的返回类型(可能为null),名称
  }

  /** Creates a copy of this aggregate call, applying a mapping to its
   * arguments. */
  public AggregateCall transform(Mappings.TargetMapping mapping) {  // 方法:创建AggregateCall的副本,应用映射转换参数
    return new AggregateCall(pos, aggFunction, distinct, approximate, ignoreNulls,  // 创建新的AggregateCall对象
        rexList, Mappings.apply2((Mapping) mapping, argList),  // 应用映射到参数列表
        hasFilter() ? Mappings.apply(mapping, filterArg) : -1,  // 如果有过滤参数,应用映射到过滤参数索引,否则设为-1
        distinctKeys == null ? null : distinctKeys.permute(mapping),  // 如果有去重键集合,应用映射变换,否则为null
        RelCollations.permute(collation, mapping), type, name);  // 应用映射到排序规则,其他参数保持不变
  }
}