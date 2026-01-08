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
package org.apache.calcite.adapter.enumerable;

import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.avatica.util.ByteString;
import org.apache.calcite.avatica.util.DateTimeUtils;
import org.apache.calcite.linq4j.Ord;
import org.apache.calcite.linq4j.function.Function1;
import org.apache.calcite.linq4j.tree.BlockBuilder;
import org.apache.calcite.linq4j.tree.BlockStatement;
import org.apache.calcite.linq4j.tree.CatchBlock;
import org.apache.calcite.linq4j.tree.ConstantExpression;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.ParameterExpression;
import org.apache.calcite.linq4j.tree.Primitive;
import org.apache.calcite.linq4j.tree.Statement;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexCallBinding;
import org.apache.calcite.rex.RexCorrelVariable;
import org.apache.calcite.rex.RexDynamicParam;
import org.apache.calcite.rex.RexFieldAccess;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLambda;
import org.apache.calcite.rex.RexLambdaRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexLocalRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexOver;
import org.apache.calcite.rex.RexPatternFieldRef;
import org.apache.calcite.rex.RexProgram;
import org.apache.calcite.rex.RexRangeRef;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.rex.RexTableInputRef;
import org.apache.calcite.rex.RexUtil;
import org.apache.calcite.rex.RexVisitor;
import org.apache.calcite.runtime.SpatialTypeFunctions;
import org.apache.calcite.runtime.rtti.RuntimeTypeInformation;
import org.apache.calcite.runtime.variant.VariantValue;
import org.apache.calcite.schema.FunctionContext;
import org.apache.calcite.sql.SqlIntervalQualifier;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlWindowTableFunction;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeFamily;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.type.SqlTypeUtil;
import org.apache.calcite.sql.validate.SqlConformance;
import org.apache.calcite.util.BuiltInMethod;
import org.apache.calcite.util.ControlFlowException;
import org.apache.calcite.util.Pair;
import org.apache.calcite.util.Util;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.locationtech.jts.geom.Geometry;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.apache.calcite.linq4j.tree.Expressions.constant;
import static org.apache.calcite.sql.fun.SqlLibraryOperators.TRANSLATE3;
import static org.apache.calcite.sql.fun.SqlStdOperatorTable.CASE;
import static org.apache.calcite.sql.fun.SqlStdOperatorTable.CHAR_LENGTH;
import static org.apache.calcite.sql.fun.SqlStdOperatorTable.OCTET_LENGTH;
import static org.apache.calcite.sql.fun.SqlStdOperatorTable.PREV;
import static org.apache.calcite.sql.fun.SqlStdOperatorTable.SEARCH;
import static org.apache.calcite.sql.fun.SqlStdOperatorTable.SUBSTRING;
import static org.apache.calcite.sql.fun.SqlStdOperatorTable.UPPER;

import static java.util.Objects.requireNonNull;

/**
 * 将 {@link org.apache.calcite.rex.RexNode REX 表达式}转换为 {@link Expression linq4j 表达式}
 *
 * <p>这是 Calcite 框架中用于代码生成的核心类之一。它负责将关系代数表达式(REX)转换为可执行的 Java 代码表达式。
 * 这个转换过程是 Calcite 实现可扩展查询执行的关键步骤。</p>
 *
 * <p><b>核心功能:</b></p>
 * <ul>
 *   <li>将 REX 表达式树转换为 Java 表达式树</li>
 *   <li>处理各种 SQL 操作符的代码生成</li>
 *   <li>管理类型转换和空值处理</li>
 *   <li>优化生成的代码以避免重复计算</li>
 *   <li>支持相关变量和动态参数</li>
 * </ul>
 *
 * <p><b>转换过程:</b></p>
 * <ol>
 *   <li>访问 REX 表达式树的每个节点</li>
 *   <li>根据节点类型生成对应的 Java 代码</li>
 *   <li>处理类型转换和空值检查</li>
 *   <li>缓存结果以避免重复代码生成</li>
 * </ol>
 *
 * <p><b>重要概念:</b></p>
 * <ul>
 *   <li>REX 表达式: Calcite 内部的关系代数表达式表示</li>
 *   <li>LINQ4J 表达式: Java 语言集成查询的表达式树表示</li>
 *   <li>Result 类: 包含值变量和空值变量的转换结果</li>
 *   <li>InputGetter: 用于获取输入字段值的接口</li>
 * </ul>
 *
 * <p><b>使用场景:</b></p>
 * <ul>
 *   <li>Enumerable 适配器的代码生成</li>
 *   <li>投影(Project)表达式的转换</li>
 *   <li>条件(Filter)表达式的转换</li>
 *   <li>聚合函数的代码生成</li>
 * </ul>
 */
public class RexToLixTranslator implements RexVisitor<RexToLixTranslator.Result> {
  /**
   * Java 方法到 SQL 操作符的映射表
   * 用于将 Java 方法调用映射回对应的 SQL 操作符,主要用于代码优化和反向转换
   */
  public static final Map<Method, SqlOperator> JAVA_TO_SQL_METHOD_MAP =
      ImmutableMap.<Method, SqlOperator>builder()
          .put(BuiltInMethod.STRING_TO_UPPER.method, UPPER)  // String.toUpperCase() 映射到 UPPER 操作符
          .put(BuiltInMethod.SUBSTRING.method, SUBSTRING)    // String.substring() 映射到 SUBSTRING 操作符
          .put(BuiltInMethod.OCTET_LENGTH.method, OCTET_LENGTH)  // 字节长度映射到 OCTET_LENGTH 操作符
          .put(BuiltInMethod.CHAR_LENGTH.method, CHAR_LENGTH)    // 字符长度映射到 CHAR_LENGTH 操作符
          .put(BuiltInMethod.TRANSLATE3.method, TRANSLATE3)      // 字符串转换映射到 TRANSLATE3 操作符
          .build();

  /**
   * Java 类型工厂,用于创建和转换 Java 类型
   * 负责在 SQL 类型和 Java 类型之间进行映射
   */
  final JavaTypeFactory typeFactory;

  /**
   * REX 表达式构建器,用于创建新的 REX 表达式
   * 在转换过程中可能需要创建辅助的 REX 表达式
   */
  final RexBuilder builder;

  /**
   * REX 程序,包含表达式列表、投影列表和条件
   * 可能为 null,当不需要完整的程序上下文时
   */
  private final @Nullable RexProgram program;

  /**
   * SQL 一致性配置,指定 SQL 方言的兼容性规则
   * 控制某些 SQL 操作符的行为和语法支持
   */
  final SqlConformance conformance;

  /**
   * 根表达式,通常是 DataContext.ROOT
   * 表示数据访问的根节点,用于获取运行时上下文
   */
  private final Expression root;

  /**
   * 输入获取器,用于从输入中获取字段值
   * 可能为 null,当不需要访问输入时
   */
  final RexToLixTranslator.@Nullable InputGetter inputGetter;

  /**
   * 代码块构建器,用于构建生成的代码块
   * 所有的代码语句都添加到这里
   */
  private final BlockBuilder list;

  /**
   * 静态代码块构建器,用于构建静态成员声明
   * 可能为 null,当不需要静态成员时
   */
  private final @Nullable BlockBuilder staticList;

  /**
   * 相关变量解析器,用于获取相关变量的输入获取器
   * 可能为 null,当没有相关变量时
   */
  private final @Nullable Function1<String, InputGetter> correlates;

  /**
   * 字面量映射表:从 RexLiteral 的变量名到其实际字面量值的映射
   * 字面量通常是 {@link org.apache.calcite.linq4j.tree.ConstantExpression}
   *
   * <p>这个映射在某些 {@code RexCall} 的实现器中使用,例如 {@code ExtractImplementor}</p>
   *
   * <p><b>用途:</b></p>
   * <ul>
   *   <li>在代码生成过程中快速查找字面量的实际值</li>
   *   <li>避免重复生成相同的常量表达式</li>
   *   <li>支持运行时优化</li>
   * </ul>
   *
   * @see #getLiteral 获取字面量表达式
   * @see #getLiteralValue 获取字面量的实际值
   */
  private final Map<Expression, Expression> literalMap = new HashMap<>();

  /**
   * 调用操作数结果映射表:为 {@code RexCall} 保存其操作数的 {@code Result} 列表
   * 在创建 {@code CallImplementor} 时非常有用
   *
   * <p><b>用途:</b></p>
   * <ul>
   *   <li>在实现函数调用时访问所有操作数的转换结果</li>
   *   <li>支持操作数的复杂转换逻辑</li>
   *   <li>避免重复转换相同的操作数</li>
   * </ul>
   */
  private final Map<RexCall, List<Result>> callOperandResultMap =
      new HashMap<>();

  /**
   * 带存储类型的 REX 节点结果映射表:从特定存储类型的 RexNode 到其 Result 的映射
   * 用于避免生成重复代码
   *
   * <p>适用于: {@code RexInputRef}、{@code RexDynamicParam} 和 {@code RexFieldAccess}</p>
   *
   * <p><b>Key:</b> (RexNode, StorageType) 的 Pair</p>
   * <p><b>Value:</b> 对应的 Result 对象</p>
   *
   * <p><b>用途:</b></p>
   * <ul>
   *   <li>缓存转换结果,避免重复代码生成</li>
   *   <li>支持不同的存储类型,优化类型转换</li>
   *   <li>提高代码生成效率</li>
   * </ul>
   */
  private final Map<Pair<RexNode, @Nullable Type>, Result> rexWithStorageTypeResultMap =
      new HashMap<>();

  /**
   * REX 节点结果映射表:从 RexNode 到其 Result 的映射
   * 用于避免生成重复代码
   *
   * <p>适用于: {@code RexLiteral} 和 {@code RexCall}</p>
   *
   * <p><b>Key:</b> RexNode 对象</p>
   * <p><b>Value:</b> 对应的 Result 对象</p>
   *
   * <p><b>用途:</b></p>
   * <ul>
   *   <li>缓存转换结果,避免重复代码生成</li>
   *   <li>识别重复的表达式</li>
   *   <li>优化生成的代码</li>
   * </ul>
   */
  private final Map<RexNode, Result> rexResultMap = new HashMap<>();

  /**
   * 当前存储类型,表示当前转换的目标 Java 类型
   * 可能为 null,表示使用默认存储类型
   *
   * <p><b>用途:</b></p>
   * <ul>
   *   <li>指导类型转换优化</li>
   *   <li>避免不必要的装箱/拆箱操作</li>
   *   <li>提高生成代码的效率</li>
   * </ul>
   */
  private @Nullable Type currentStorageType;

  /**
   * 私有构造方法,创建一个新的 RexToLixTranslator 实例
   *
   * <p><b>参数说明:</b></p>
   * <ul>
   *   <li>program: REX 程序,可能为 null</li>
   *   <li>typeFactory: Java 类型工厂,必须非 null</li>
   *   <li>root: 根表达式,通常是 DataContext.ROOT,必须非 null</li>
   *   <li>inputGetter: 输入获取器,可能为 null</li>
   *   <li>list: 代码块构建器,必须非 null</li>
   *   <li>staticList: 静态代码块构建器,可能为 null</li>
   *   <li>builder: REX 表达式构建器,必须非 null</li>
   *   <li>conformance: SQL 一致性配置,必须非 null</li>
   *   <li>correlates: 相关变量解析器,可能为 null</li>
   * </ul>
   *
   * <p><b>构造方法设计:</b></p>
   * <ul>
   *   <li>使用私有构造方法,强制通过静态工厂方法创建实例</li>
   *   <li>对必须参数进行 null 检查</li>
   *   <li>可选参数允许为 null</li>
   * </ul>
   */
  private RexToLixTranslator(@Nullable RexProgram program,
      JavaTypeFactory typeFactory,
      Expression root,
      @Nullable InputGetter inputGetter,
      BlockBuilder list,
      @Nullable BlockBuilder staticList,
      RexBuilder builder,
      SqlConformance conformance,
      @Nullable Function1<String, InputGetter> correlates) {
    this.program = program; // REX 程序,可能为 null
    this.typeFactory = requireNonNull(typeFactory, "typeFactory"); // 类型工厂,必须非 null
    this.conformance = requireNonNull(conformance, "conformance"); // SQL 一致性,必须非 null
    this.root = requireNonNull(root, "root"); // 根表达式,必须非 null
    this.inputGetter = inputGetter; // 输入获取器,可能为 null
    this.list = requireNonNull(list, "list"); // 代码块构建器,必须非 null
    this.staticList = staticList; // 静态代码块构建器,可能为 null
    this.builder = requireNonNull(builder, "builder"); // REX 构建器,必须非 null
    this.correlates = correlates; // 相关变量解析器,可能为 null
  }

  /**
   * 将 {@link RexProgram} 转换为表达式序列和声明
   *
   * <p>这是代码生成的主要入口点之一,用于将投影表达式转换为可执行的 Java 代码。</p>
   *
   * <p><b>转换过程:</b></p>
   * <ol>
   *   <li>确定输出类型的存储类型</li>
   *   <li>创建 RexToLixTranslator 实例</li>
   *   <li>设置相关变量解析器</li>
   *   <li>转换投影列表中的每个表达式</li>
   * </ol>
   *
   * <p><b>参数说明:</b></p>
   * <ul>
   *   <li>program: 要转换的 REX 程序,包含投影表达式列表</li>
   *   <li>typeFactory: 类型工厂,用于类型转换</li>
   *   <li>conformance: SQL 一致性配置</li>
   *   <li>list: 语句列表,生成的声明将添加到这里</li>
   *   <li>staticList: 成员声明列表,静态成员将添加到这里</li>
   *   <li>outputPhysType: 输出物理类型,可为 null</li>
   *   <li>root: 根表达式,通常是 DataContext.ROOT</li>
   *   <li>inputGetter: 为输入生成表达式的获取器</li>
   *   <li>correlates: 相关变量值的引用提供器</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>表达式序列:每个表达式对应一个投影字段</li>
   * </ul>
   *
   * <p><b>使用场景:</b></p>
   * <ul>
   *   <li>Enumerable 操作符的投影转换</li>
   *   <li>Calc 操作符的代码生成</li>
   *   <li>Project 操作符的实现</li>
   * </ul>
   */
  public static List<Expression> translateProjects(RexProgram program,
      JavaTypeFactory typeFactory, SqlConformance conformance,
      BlockBuilder list, @Nullable BlockBuilder staticList,
      @Nullable PhysType outputPhysType, Expression root,
      InputGetter inputGetter, @Nullable Function1<String, InputGetter> correlates) {
    // 存储类型列表,用于优化类型转换
    List<Type> storageTypes = null;
    // 如果提供了输出物理类型,则确定每个字段的存储类型
    if (outputPhysType != null) {
      final RelDataType rowType = outputPhysType.getRowType();  // 获取输出行类型
      storageTypes = new ArrayList<>(rowType.getFieldCount()); // 创建存储类型列表
      // 遍历每个字段,获取其 Java 字段类型
      for (int i = 0; i < rowType.getFieldCount(); i++) {
        storageTypes.add(outputPhysType.getJavaFieldType(i)); // 添加字段的 Java 类型
      }
    }
    // 创建翻译器实例,设置相关变量解析器,然后转换投影列表
    return new RexToLixTranslator(program, typeFactory, root, inputGetter,
        list, staticList, new RexBuilder(typeFactory), conformance,  null)
        .setCorrelates(correlates)  // 设置相关变量解析器
        .translateList(program.getProjectList(), storageTypes); // 转换投影列表
  }

  /**
   * @deprecated 已弃用,将在 2.0 版本前移除
   * 请使用带 staticList 参数的版本
   */
  @Deprecated // to be removed before 2.0
  public static List<Expression> translateProjects(RexProgram program,
      JavaTypeFactory typeFactory, SqlConformance conformance,
      BlockBuilder list, @Nullable PhysType outputPhysType, Expression root,
      InputGetter inputGetter, @Nullable Function1<String, InputGetter> correlates) {
    // 委托给新版本,staticList 传 null
    return translateProjects(program, typeFactory, conformance, list, null,
        outputPhysType, root, inputGetter, correlates);
  }

  /**
   * 转换表函数调用
   *
   * <p>将 REX 表函数调用转换为可执行的 Java 表达式。</p>
   *
   * <p><b>参数说明:</b></p>
   * <ul>
   *   <li>typeFactory: 类型工厂</li>
   *   <li>conformance: SQL 一致性配置</li>
   *   <li>list: 代码块构建器</li>
   *   <li>root: 根表达式</li>
   *   <li>rexCall: REX 函数调用</li>
   *   <li>inputEnumerable: 输入可枚举对象</li>
   *   <li>inputPhysType: 输入物理类型</li>
   *   <li>outputPhysType: 输出物理类型</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换后的表函数表达式</li>
   * </ul>
   */
  public static Expression translateTableFunction(JavaTypeFactory typeFactory,
      SqlConformance conformance, BlockBuilder list,
      Expression root, RexCall rexCall, Expression inputEnumerable,
      PhysType inputPhysType, PhysType outputPhysType) {
    // 创建翻译器实例,不需要程序和输入获取器
    final RexToLixTranslator translator =
        new RexToLixTranslator(null, typeFactory, root, null, list,
            null, new RexBuilder(typeFactory), conformance, null);
    // 调用实例方法转换表函数
    return translator
        .translateTableFunction(rexCall, inputEnumerable, inputPhysType,
            outputPhysType);
  }

  /**
   * 创建用于翻译聚合函数的翻译器
   *
   * <p>这个方法专门为聚合函数的代码生成创建翻译器实例。</p>
   *
   * <p><b>参数说明:</b></p>
   * <ul>
   *   <li>typeFactory: 类型工厂</li>
   *   <li>list: 代码块构建器</li>
   *   <li>inputGetter: 输入获取器,可能为 null</li>
   *   <li>conformance: SQL 一致性配置</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>配置好的翻译器实例</li>
   * </ul>
   *
   * <p><b>特点:</b></p>
   * <ul>
   *   <li>根表达式固定为 DataContext.ROOT</li>
   *   <li>不需要程序上下文</li>
   *   <li>专门用于聚合场景</li>
   * </ul>
   */
  public static RexToLixTranslator forAggregation(JavaTypeFactory typeFactory,
      BlockBuilder list, @Nullable InputGetter inputGetter,
      SqlConformance conformance) {
    final ParameterExpression root = DataContext.ROOT; // 使用标准根表达式
    // 创建翻译器实例,不需要程序和静态列表
    return new RexToLixTranslator(null, typeFactory, root, inputGetter, list,
        null, new RexBuilder(typeFactory), conformance, null);
  }

  /**
   * 转换单个 REX 表达式(使用默认空值处理)
   *
   * <p>这是最简单的转换方法,自动根据表达式的可空性确定空值处理策略。</p>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>expr: 要转换的 REX 表达式</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换后的 Java 表达式</li>
   * </ul>
   */
  Expression translate(RexNode expr) {
    // 根据表达式是否可空确定空值处理策略
    final RexImpTable.NullAs nullAs =
        RexImpTable.NullAs.of(isNullable(expr));
    // 委托给带 nullAs 参数的版本
    return translate(expr, nullAs);
  }

  /**
   * 转换单个 REX 表达式(指定空值处理策略)
   *
   * <p>允许显式指定空值处理策略,提供更精细的控制。</p>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>expr: 要转换的 REX 表达式</li>
   *   <li>nullAs: 空值处理策略</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换后的 Java 表达式</li>
   * </ul>
   */
  Expression translate(RexNode expr, RexImpTable.NullAs nullAs) {
    // 委托给完整版本,storageType 为 null
    return translate(expr, nullAs, null);
  }

  /**
   * 转换单个 REX 表达式(指定存储类型)
   *
   * <p>允许指定目标存储类型,用于优化类型转换。</p>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>expr: 要转换的 REX 表达式</li>
   *   <li>storageType: 目标存储类型,可能为 null</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换后的 Java 表达式</li>
   * </ul>
   */
  Expression translate(RexNode expr, @Nullable Type storageType) {
    // 根据表达式是否可空确定空值处理策略
    final RexImpTable.NullAs nullAs =
        RexImpTable.NullAs.of(isNullable(expr));
    // 委托给完整版本
    return translate(expr, nullAs, storageType);
  }

  /**
   * 转换单个 REX 表达式(完整版本)
   *
   * <p>这是最完整的转换方法,允许指定空值处理策略和存储类型。</p>
   *
   * <p><b>转换过程:</b></p>
   * <ol>
   *   <li>设置当前存储类型</li>
   *   <li>访问 REX 表达式,生成转换结果</li>
   *   <li>转换为内部表示</li>
   *   <li>应用空值处理策略</li>
   * </ol>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>expr: 要转换的 REX 表达式</li>
   *   <li>nullAs: 空值处理策略</li>
   *   <li>storageType: 目标存储类型,可能为 null</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换后的 Java 表达式</li>
   * </ul>
   *
   * <p><b>优化:</b></p>
   * <ul>
   *   <li>当输入不可能为空且存储类型匹配时,避免不必要的拆箱</li>
   * </ul>
   */
  Expression translate(RexNode expr, RexImpTable.NullAs nullAs,
      @Nullable Type storageType) {
    currentStorageType = storageType; // 设置当前存储类型,用于缓存键
    final Result result = expr.accept(this); // 访问表达式,获取转换结果
    // 转换为内部表示,处理类型转换
    final Expression translated =
        requireNonNull(EnumUtils.toInternal(result.valueVariable, storageType));
    // 优化:当输入不可能为空且类型匹配时,避免拆箱
    if (RexImpTable.NullAs.NOT_POSSIBLE == nullAs
        && translated.type.equals(storageType)) {
      return translated; // 直接返回,避免额外处理
    }
    // 应用空值处理策略
    return nullAs.handle(translated);
  }

  /**
   * 用于安全操作符的表达式处理
   *
   * <p>安全操作符在抛出异常时返回 null,而不是传播异常。</p>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>body: 要包装的表达式</li>
   *   <li>safe: 是否使用安全模式</li>
   *   <li>targetType: 目标类型</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>如果是安全模式,返回包装后的表达式</li>
   *   <li>否则返回原始表达式</li>
   * </ul>
   */
  private Expression expressionHandlingSafe(
      Expression body, boolean safe, RelDataType targetType) {
    // 如果是安全模式,使用 try-catch 包装
    return safe ? safeExpression(body, targetType) : body;
  }

  /**
   * 创建安全表达式,捕获异常并返回 null
   *
   * <p>生成的代码类似:</p>
   * <pre>
   * try {
   *   return body;
   * } catch (Exception e) {
   *   return null;
   * }
   * </pre>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>body: 要包装的表达式</li>
   *   <li>targetType: 目标类型</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>包装后的安全表达式</li>
   * </ul>
   *
   * <p><b>注意:</b></p>
   * <ul>
   *   <li>FUNCTION0 总是返回 Object,需要转换为目标类型</li>
   *   <li>目标类型被标记为可空,因为安全转换可能返回 null</li>
   * </ul>
   */
  private Expression safeExpression(Expression body, RelDataType targetType) {
    final ParameterExpression e_ =
        Expressions.parameter(Exception.class, new BlockBuilder().newName("e"));

    // 目标类型接收的类型永远不会是可空的
    // 但安全转换可能返回 null
    RelDataType nullableTargetType = typeFactory.createTypeWithNullability(targetType, true);
    // 创建 lambda 表达式,包含 try-catch 块
    Expression result =
        Expressions.call(
            Expressions.lambda(
                Expressions.block(
                    Expressions.tryCatch(
                        Expressions.return_(null, body),      // 正常返回 body
                        Expressions.catch_(e_,                  // 捕获异常
                            Expressions.return_(null, constant(null)))))), // 返回 null
            BuiltInMethod.FUNCTION0_APPLY.method);
    // FUNCTION0 总是返回 Object,所以需要转换为目标类型
    return EnumUtils.convert(result, typeFactory.getJavaClass(nullableTargetType));
  }

  /**
   * 转换类型转换表达式
   *
   * <p>将 SQL 类型转换转换为 Java 表达式,支持类型检查、填充/截断和值缩放。</p>
   *
   * <p><b>转换步骤:</b></p>
   * <ol>
   *   <li>基本的类型转换</li>
   *   <li>检查并应用填充/截断(针对字符和二进制类型)</li>
   *   <li>缩放值(针对区间到数值的转换)</li>
   *   <li>应用安全处理(如果需要)</li>
   * </ol>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>sourceType: 源类型</li>
   *   <li>targetType: 目标类型</li>
   *   <li>operand: 操作数表达式</li>
   *   <li>safe: 是否使用安全模式</li>
   *   <li>format: 格式字符串,可能为 null</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换后的表达式</li>
   * </ul>
   */
  Expression translateCast(
      RelDataType sourceType,
      RelDataType targetType,
      Expression operand,
      boolean safe,
      ConstantExpression format) {
    // 第一步:基本的类型转换
    Expression convert = getConvertExpression(sourceType, targetType, operand, format);
    // 第二步:检查并应用填充/截断
    Expression convert2 = checkExpressionPadTruncate(convert, sourceType, targetType);
    // 第三步:缩放值(区间到数值)
    Expression convert3 = scaleValue(sourceType, targetType, convert2);
    // 第四步:应用安全处理
    return expressionHandlingSafe(convert3, safe, targetType);
  }

  /**
   * 获取类型转换表达式
   *
   * <p>这是类型转换的核心方法,根据源类型和目标类型生成适当的转换表达式。</p>
   *
   * <p><b>支持的转换类型:</b></p>
   * <ul>
   *   <li>VARIANT 类型转换</li>
   *   <li>ROW(结构)类型转换</li>
   *   <li>ARRAY 类型转换</li>
   *   <li>基本类型转换(字符、数值、日期时间等)</li>
   *   <li>特殊类型转换(GEOMETRY、UUID 等)</li>
   * </ul>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>sourceType: 源类型</li>
   *   <li>targetType: 目标类型</li>
   *   <li>operand: 操作数表达式</li>
   *   <li>format: 格式字符串,可能为 null</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换后的表达式</li>
   * </ul>
   */
  private Expression getConvertExpression(
      RelDataType sourceType,
      RelDataType targetType,
      Expression operand,
      ConstantExpression format) {
    // 默认转换:简单的类型转换
    final Supplier<Expression> defaultExpression = () ->
        EnumUtils.convert(operand, typeFactory.getJavaClass(targetType));

    // 处理 VARIANT 类型转换
    if (sourceType.getSqlTypeName() == SqlTypeName.VARIANT) {
      // VARIANT 到 VARIANT 的转换使用默认转换
      if (targetType.getSqlTypeName() == SqlTypeName.VARIANT) {
        return defaultExpression.get();
      }
      // VARIANT 到其他类型的转换调用 Variant.cast 方法
      // 首先将操作数转换为 VariantValue(它可能是一个 Object)
      Expression operandCast = Expressions.convert_(operand, VariantValue.class);
      // 调用 Variant.cast 方法进行类型转换
      Expression cast =
          Expressions.call(operandCast, BuiltInMethod.VARIANT_CAST.method,
              RuntimeTypeInformation.createExpression(targetType));
      // cast 返回 Object,所以需要转换为预期的 Java 类型
      RelDataType nullableTarget = typeFactory.createTypeWithNullability(targetType, true);
      return Expressions.convert_(cast, typeFactory.getJavaClass(nullableTarget));
    }

    // 处理 ROW(结构)类型转换
    if (targetType.getSqlTypeName() == SqlTypeName.ROW) {
      assert sourceType.getSqlTypeName() == SqlTypeName.ROW;
      List<RelDataTypeField> targetTypes = targetType.getFieldList();
      List<RelDataTypeField> sourceTypes = sourceType.getFieldList();
      assert targetTypes.size() == sourceTypes.size();
      List<Expression> fields = new ArrayList<>();
      // 逐个转换每个字段
      for (int i = 0; i < targetTypes.size(); i++) {
        RelDataTypeField targetField = targetTypes.get(i);
        RelDataTypeField sourceField = sourceTypes.get(i);
        // 获取数组中的字段
        Expression field = Expressions.arrayIndex(operand, Expressions.constant(i));
        // 在生成的 Java 代码中 'field' 是一个 Object,
        // 我们需要将其转换为正确的类型以启用 Java 中的正确方法调度。
        // 我们强制类型为可空;这样,我们得到 (Integer) 而不是 (int)。
        // 将对象转换为 int 是不合法的。
        RelDataType nullableSourceFieldType =
            typeFactory.createTypeWithNullability(sourceField.getType(), true);
        Type javaType = typeFactory.getJavaClass(nullableSourceFieldType);
        if (!javaType.getTypeName().equals("java.lang.Void")
            && !nullableSourceFieldType.isStruct()) {
          // 不能转换为 Void - 这是 NULL 字面量的类型。
          field = Expressions.convert_(field, javaType);
        }
        // 递归转换字段类型
        Expression convert =
            getConvertExpression(sourceField.getType(), targetField.getType(), field, format);
        fields.add(convert);
      }
      // 创建数组表达式
      return Expressions.call(BuiltInMethod.ARRAY.method, fields);
    }

    // 根据目标类型进行转换
    switch (targetType.getSqlTypeName()) {
    case ARRAY:
      // ARRAY 类型转换:使用 LIST_TRANSFORM 转换每个元素
      final RelDataType sourceDataType = sourceType.getComponentType();
      final RelDataType targetDataType = targetType.getComponentType();
      assert sourceDataType != null;
      assert targetDataType != null;
      // 创建 lambda 表达式来转换每个元素
      final ParameterExpression parameter =
          Expressions.parameter(typeFactory.getJavaClass(sourceDataType), "root");
      Expression convert =
          getConvertExpression(sourceDataType, targetDataType, parameter, format);
      return Expressions.call(BuiltInMethod.LIST_TRANSFORM.method, operand,
          Expressions.lambda(Function1.class, convert, parameter));

    case VARIANT:
      // 任何类型到 VARIANT 的转换调用 Variant 构造函数
      Expression rtti = RuntimeTypeInformation.createExpression(sourceType);
      Expression roundingMode = Expressions.constant(typeFactory.getTypeSystem().roundingMode());
      return Expressions.call(BuiltInMethod.VARIANT_CREATE.method, roundingMode, operand, rtti);
    case ANY:
      // ANY 类型不需要转换
      return operand;

    case VARBINARY:
    case BINARY:
      // 二进制类型转换
      switch (sourceType.getSqlTypeName()) {
      case CHAR:
      case VARCHAR:
        // 字符串到二进制
        return Expressions.call(BuiltInMethod.STRING_TO_BINARY.method, operand,
            new ConstantExpression(Charset.class, sourceType.getCharset()));
      case UUID:
        // UUID 到二进制
        return Expressions.call(BuiltInMethod.UUID_TO_BINARY.method, operand);
      default:
        // 默认转换
        return defaultExpression.get();
      }

    case GEOMETRY:
      // 几何类型转换
      switch (sourceType.getSqlTypeName()) {
      case CHAR:
      case VARCHAR:
        // 字符串到几何类型(EWKT 格式)
        return Expressions.call(BuiltInMethod.ST_GEOM_FROM_EWKT.method, operand);

      default:
        // 默认转换
        return defaultExpression.get();
      }

    case DATE:
      // 日期类型转换
      return translateCastToDate(sourceType, operand, format, defaultExpression);

    case TIME:
      // 时间类型转换
      return translateCastToTime(sourceType, operand, format, defaultExpression);

    case TIME_WITH_LOCAL_TIME_ZONE:
      // 带本地时区的时间类型转换
      return translateCastToTimeWithLocalTimeZone(sourceType, operand, defaultExpression);

    case TIMESTAMP:
      // 时间戳类型转换
      return translateCastToTimestamp(sourceType, operand, format, defaultExpression);

    case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
      // 带本地时区的时间戳类型转换
      return translateCastToTimestampWithLocalTimeZone(sourceType, operand, defaultExpression);

    case BOOLEAN:
      // 布尔类型转换
      switch (sourceType.getSqlTypeName()) {
      case CHAR:
      case VARCHAR:
        // 字符串到布尔值
        return Expressions.call(BuiltInMethod.STRING_TO_BOOLEAN.method, operand);

      default:
        // 默认转换
        return defaultExpression.get();
      }
    case UUID:
      // UUID 类型转换
      switch (sourceType.getSqlTypeName()) {
      case UUID:
        // UUID 到 UUID:不需要转换
        return operand;
      case CHAR:
      case VARCHAR:
        // 字符串到 UUID
        return Expressions.call(BuiltInMethod.UUID_FROM_STRING.method, operand);
      case BINARY:
      case VARBINARY:
        // 二进制到 UUID
        return Expressions.call(BuiltInMethod.BINARY_TO_UUID.method, operand);
      default:
        // 默认转换
        return defaultExpression.get();
      }
    case CHAR:
    case VARCHAR:
      // 字符类型转换
      final SqlIntervalQualifier interval =
          sourceType.getIntervalQualifier();
      switch (sourceType.getSqlTypeName()) {
      case UUID:
        // UUID 到字符串
        return Expressions.call(BuiltInMethod.UUID_TO_STRING.method, operand);
      // 如果提供了格式字符串,返回格式化的日期/时间/时间戳
      case DATE:
        // 日期到字符串
        return RexImpTable.optimize2(operand, Expressions.isConstantNull(format)
            ? Expressions.call(BuiltInMethod.UNIX_DATE_TO_STRING.method, operand)
            : Expressions.call(
                Expressions.new_(
                    BuiltInMethod.FORMAT_DATE.method.getDeclaringClass()),
                BuiltInMethod.FORMAT_DATE.method, format, operand));

      case TIME:
        // 时间到字符串
        return RexImpTable.optimize2(operand, Expressions.isConstantNull(format)
            ? Expressions.call(BuiltInMethod.UNIX_TIME_TO_STRING.method, operand)
            : Expressions.call(
                Expressions.new_(
                    BuiltInMethod.FORMAT_TIME.method.getDeclaringClass()),
                BuiltInMethod.FORMAT_TIME.method, format, operand));

      case TIME_WITH_LOCAL_TIME_ZONE:
        // 带本地时区的时间到字符串
        return RexImpTable.optimize2(operand, Expressions.isConstantNull(format)
            ? Expressions.call(BuiltInMethod.TIME_WITH_LOCAL_TIME_ZONE_TO_STRING.method, operand,
            Expressions.call(BuiltInMethod.TIME_ZONE.method, root))
            : Expressions.call(
                Expressions.new_(
                    BuiltInMethod.FORMAT_TIME.method.getDeclaringClass()),
                BuiltInMethod.FORMAT_TIME.method, format, operand));

      case TIMESTAMP:
        // 时间戳到字符串
        return RexImpTable.optimize2(operand, Expressions.isConstantNull(format)
            ? Expressions.call(BuiltInMethod.UNIX_TIMESTAMP_TO_STRING.method, operand)
            : Expressions.call(
                Expressions.new_(
                    BuiltInMethod.FORMAT_TIMESTAMP.method.getDeclaringClass()),
                BuiltInMethod.FORMAT_TIMESTAMP.method, format, operand));

      case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
        // 带本地时区的时间戳到字符串
        return RexImpTable.optimize2(operand, Expressions.isConstantNull(format)
            ? Expressions.call(BuiltInMethod.TIMESTAMP_WITH_LOCAL_TIME_ZONE_TO_STRING.method,
            operand, Expressions.call(BuiltInMethod.TIME_ZONE.method, root))
            : Expressions.call(
                Expressions.new_(
                    BuiltInMethod.FORMAT_TIMESTAMP.method.getDeclaringClass()),
                BuiltInMethod.FORMAT_TIMESTAMP.method, format, operand));

      case INTERVAL_YEAR:
      case INTERVAL_YEAR_MONTH:
      case INTERVAL_MONTH:
        // 年-月区间到字符串
        return RexImpTable.optimize2(operand,
            Expressions.call(BuiltInMethod.INTERVAL_YEAR_MONTH_TO_STRING.method,
                operand,
                Expressions.constant(
                    requireNonNull(interval, "interval").timeUnitRange)));

      case INTERVAL_DAY:
      case INTERVAL_DAY_HOUR:
      case INTERVAL_DAY_MINUTE:
      case INTERVAL_DAY_SECOND:
      case INTERVAL_HOUR:
      case INTERVAL_HOUR_MINUTE:
      case INTERVAL_HOUR_SECOND:
      case INTERVAL_MINUTE:
      case INTERVAL_MINUTE_SECOND:
      case INTERVAL_SECOND:
        // 日-时间区间到字符串
        return RexImpTable.optimize2(operand,
            Expressions.call(BuiltInMethod.INTERVAL_DAY_TIME_TO_STRING.method,
                operand,
                Expressions.constant(
                    requireNonNull(interval, "interval").timeUnitRange),
                Expressions.constant(
                    interval.getFractionalSecondPrecision(
                        typeFactory.getTypeSystem()))));

      case BOOLEAN:
        // 布尔值到字符串
        return RexImpTable.optimize2(operand,
            Expressions.call(BuiltInMethod.BOOLEAN_TO_STRING.method,
                operand));

      default:
        // 默认转换
        return defaultExpression.get();
      }

    case DECIMAL: {
      // DECIMAL 类型转换,需要处理精度和小数位数
      int precision = targetType.getPrecision();  // 目标精度
      int scale = targetType.getScale();          // 目标小数位数
      // 如果精度和小数位数都已指定
      if (precision != RelDataType.PRECISION_NOT_SPECIFIED
          && scale != RelDataType.SCALE_NOT_SPECIFIED) {
        if (sourceType.getFamily() == SqlTypeFamily.CHARACTER) {
          // 字符类型到 DECIMAL
          return Expressions.call(
              BuiltInMethod.CHAR_DECIMAL_CAST_ROUNDING_MODE.method,
              operand,
              Expressions.constant(precision),
              Expressions.constant(scale),
              Expressions.constant(typeFactory.getTypeSystem().roundingMode()));
        } else if (sourceType.getFamily() == SqlTypeFamily.INTERVAL_DAY_TIME) {
          // 日-时间区间到 DECIMAL(短区间)
          return Expressions.call(
              BuiltInMethod.SHORT_INTERVAL_DECIMAL_CAST_ROUNDING_MODE.method,
              operand,
              Expressions.constant(precision),
              Expressions.constant(scale),
              Expressions.constant(sourceType.getSqlTypeName().getEndUnit().multiplier),
              Expressions.constant(typeFactory.getTypeSystem().roundingMode()));
        } else if (sourceType.getFamily() == SqlTypeFamily.INTERVAL_YEAR_MONTH) {
          // 年-月区间到 DECIMAL(长区间)
          return Expressions.call(
              BuiltInMethod.LONG_INTERVAL_DECIMAL_CAST_ROUNDING_MODE.method,
              operand,
              Expressions.constant(precision),
              Expressions.constant(scale),
              Expressions.constant(sourceType.getSqlTypeName().getEndUnit().multiplier),
              Expressions.constant(typeFactory.getTypeSystem().roundingMode()));
        } else if (sourceType.getSqlTypeName() == SqlTypeName.DECIMAL) {
          // DECIMAL 到 DECIMAL,可能需要调整精度和小数位数
          return Expressions.call(
              BuiltInMethod.DECIMAL_DECIMAL_CAST_ROUNDING_MODE.method,
              operand,
              Expressions.constant(precision),
              Expressions.constant(scale),
              Expressions.constant(typeFactory.getTypeSystem().roundingMode()));
        } else if (SqlTypeName.INT_TYPES.contains(sourceType.getSqlTypeName())) {
          // 整数类型到 DECIMAL,检查溢出
          return Expressions.call(
              BuiltInMethod.INTEGER_DECIMAL_CAST_ROUNDING_MODE.method,
              operand,
              Expressions.constant(precision),
              Expressions.constant(scale),
              Expressions.constant(typeFactory.getTypeSystem().roundingMode()));
        }  else if (SqlTypeName.APPROX_TYPES.contains(sourceType.getSqlTypeName())) {
          // 浮点类型(FLOAT/DOUBLE)到 DECIMAL
          return Expressions.call(
              BuiltInMethod.FP_DECIMAL_CAST_ROUNDING_MODE.method,
              operand,
              Expressions.constant(precision),
              Expressions.constant(scale),
              Expressions.constant(typeFactory.getTypeSystem().roundingMode()));
        }
      }
      // 默认转换
      return defaultExpression.get();
    }
    case BIGINT:
    case INTEGER:
    case TINYINT:
    case SMALLINT: {
      // 整数类型转换
      if (SqlTypeName.NUMERIC_TYPES.contains(sourceType.getSqlTypeName())) {
        // 如果源类型是数值类型
        Type javaClass = typeFactory.getJavaClass(targetType);
        Primitive primitive = Primitive.of(javaClass);
        if (primitive == null) {
          // 如果不是基本类型,尝试获取其包装类型
          primitive = Primitive.ofBox(javaClass);
        }
        // 使用舍入模式进行整数转换
        return Expressions.call(
            BuiltInMethod.INTEGER_CAST_ROUNDING_MODE.method,
            Expressions.constant(primitive),
            operand, Expressions.constant(typeFactory.getTypeSystem().roundingMode()));
      }
      // 默认转换
      return defaultExpression.get();
    }

    default:
      // 默认转换
      return defaultExpression.get();
    }
  }

  /**
   * 检查并应用填充或截断操作
   *
   * <p>当从任何类型转换为 CHAR(n) 或 VARCHAR(n) 时,确保值不超过 n。</p>
   *
   * <p><b>处理的场景:</b></p>
   * <ul>
   *   <li>CHAR/BINARY:需要填充和截断</li>
   *   <li>VARCHAR/VARBINARY:需要截断</li>
   *   <li>TIMESTAMP:需要舍入</li>
   *   <li>INTERVAL:需要缩放</li>
   * </ul>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>operand: 操作数表达式</li>
   *   <li>sourceType: 源类型</li>
   *   <li>targetType: 目标类型</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>应用填充或截断后的表达式</li>
   * </ul>
   */
  private static Expression checkExpressionPadTruncate(
      Expression operand,
      RelDataType sourceType,
      RelDataType targetType) {
    // 从任何类型转换为 CHAR(n) 或 VARCHAR(n) 时,确保值不超过 n
    boolean pad = false;      // 是否需要填充
    boolean truncate = true;  // 是否需要截断
    switch (targetType.getSqlTypeName()) {
    case CHAR:
    case BINARY:
      pad = true;  // CHAR 和 BINARY 需要填充
      // fall through
    case VARCHAR:
    case VARBINARY:
      final int targetPrecision = targetType.getPrecision();
      if (targetPrecision < 0) {
        // 如果精度未指定,不需要处理
        return operand;
      }
      switch (sourceType.getSqlTypeName()) {
      case CHAR:
      case VARCHAR:
      case BINARY:
      case VARBINARY:
        // 如果是扩展转换,不需要截断
        final int sourcePrecision = sourceType.getPrecision();
        if (SqlTypeUtil.comparePrecision(sourcePrecision, targetPrecision)
            <= 0) {
          truncate = false;
        }
        // 如果是缩小转换,不需要填充
        // 但是,从 VARCHAR(N) 到 CHAR(N) 的转换仍然需要填充,
        // 因为 VARCHAR(N) 不显式表示空格,而 CHAR(N) 表示。
        if ((SqlTypeUtil.comparePrecision(sourcePrecision, targetPrecision) >= 0)
            && (sourceType.getSqlTypeName() != SqlTypeName.VARCHAR)) {
          pad = false;
        }
        // fall through
      default:
        if (truncate || pad) {
          // 应用填充或截断
          final Method method =
              pad ? BuiltInMethod.TRUNCATE_OR_PAD.method
                  : BuiltInMethod.TRUNCATE.method;
          return Expressions.call(method, operand,
              Expressions.constant(targetPrecision));
        }
        return operand;
      }

      // Checkstyle 认为前面的分支应该有 break,但它错了。
      // CHECKSTYLE: IGNORE 1
    case TIMESTAMP:
      // TIMESTAMP 类型需要舍入
      int targetScale = targetType.getScale();
      if (targetScale == RelDataType.SCALE_NOT_SPECIFIED) {
        targetScale = 0;
      }
      if (targetScale < sourceType.getScale()) {
        // 如果目标精度小于源精度,需要舍入
        return Expressions.call(BuiltInMethod.ROUND_LONG.method, operand,
            Expressions.constant((long) Math.pow(10, 3 - targetScale)));
      }
      return operand;

    case INTERVAL_YEAR:
    case INTERVAL_YEAR_MONTH:
    case INTERVAL_MONTH:
    case INTERVAL_DAY:
    case INTERVAL_DAY_HOUR:
    case INTERVAL_DAY_MINUTE:
    case INTERVAL_DAY_SECOND:
    case INTERVAL_HOUR:
    case INTERVAL_HOUR_MINUTE:
    case INTERVAL_HOUR_SECOND:
    case INTERVAL_MINUTE:
    case INTERVAL_MINUTE_SECOND:
    case INTERVAL_SECOND:
      // INTERVAL 类型需要缩放
      final SqlTypeFamily family =
          requireNonNull(sourceType.getSqlTypeName().getFamily(),
              () -> "null SqlTypeFamily for " + sourceType + ", SqlTypeName "
                  + sourceType.getSqlTypeName());
      switch (family) {
      case NUMERIC:
        // 数值到区间需要缩放
        final BigDecimal multiplier =
            targetType.getSqlTypeName().getEndUnit().multiplier;
        final BigDecimal divider = BigDecimal.ONE;
        return RexImpTable.multiplyDivide(operand, multiplier, divider);

      default:
        // 其他类型不需要处理
        return operand;
      }

    default:
      // 默认不需要处理
      return operand;
    }
  }

  private Expression translateCastToDate(RelDataType sourceType,
      Expression operand, ConstantExpression format,
      Supplier<Expression> defaultExpression) {

    switch (sourceType.getSqlTypeName()) {
    case CHAR:
    case VARCHAR:
      // If format string is supplied, parse formatted string into date
      return Expressions.isConstantNull(format)
          ? Expressions.call(BuiltInMethod.STRING_TO_DATE.method, operand)
          : Expressions.call(Expressions.new_(BuiltInMethod.PARSE_DATE.method.getDeclaringClass()),
              BuiltInMethod.PARSE_DATE.method, format, operand);

    case TIMESTAMP:
      return
          Expressions.convert_(
              Expressions.call(BuiltInMethod.FLOOR_DIV.method,
                  operand, Expressions.constant(DateTimeUtils.MILLIS_PER_DAY)),
              int.class);

    case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
      return
          RexImpTable.optimize2(
              operand, Expressions.call(
                  BuiltInMethod.TIMESTAMP_WITH_LOCAL_TIME_ZONE_TO_DATE.method,
                  operand,
                  Expressions.call(BuiltInMethod.TIME_ZONE.method, root)));

    default:
      return defaultExpression.get();
    }
  }

  private Expression translateCastToTime(RelDataType sourceType,
      Expression operand, ConstantExpression format, Supplier<Expression> defaultExpression) {

    switch (sourceType.getSqlTypeName()) {
    case CHAR:
    case VARCHAR:
      // If format string is supplied, parse formatted string into time
      return Expressions.isConstantNull(format)
          ? Expressions.call(BuiltInMethod.STRING_TO_TIME.method, operand)
          : Expressions.call(Expressions.new_(BuiltInMethod.PARSE_TIME.method.getDeclaringClass()),
              BuiltInMethod.PARSE_TIME.method, format, operand);

    case TIME_WITH_LOCAL_TIME_ZONE:
      return
          RexImpTable.optimize2(
              operand, Expressions.call(
                  BuiltInMethod.TIME_WITH_LOCAL_TIME_ZONE_TO_TIME.method,
                  operand,
                  Expressions.call(BuiltInMethod.TIME_ZONE.method, root)));

    case TIMESTAMP:
      return
          Expressions.convert_(
              Expressions.call(BuiltInMethod.FLOOR_MOD.method,
                  operand,
                  Expressions.constant(DateTimeUtils.MILLIS_PER_DAY)),
              int.class);


    case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
      return
          RexImpTable.optimize2(
              operand, Expressions.call(
                  BuiltInMethod.TIMESTAMP_WITH_LOCAL_TIME_ZONE_TO_TIME.method,
                  operand,
                  Expressions.call(BuiltInMethod.TIME_ZONE.method, root)));

    default:
      return defaultExpression.get();
    }
  }

  private Expression translateCastToTimeWithLocalTimeZone(RelDataType sourceType,
      Expression operand, Supplier<Expression> defaultExpression) {

    switch (sourceType.getSqlTypeName()) {
    case CHAR:
    case VARCHAR:
      return
          Expressions.call(BuiltInMethod.STRING_TO_TIME_WITH_LOCAL_TIME_ZONE.method, operand);

    case TIME:
      return
          Expressions.call(BuiltInMethod.TIME_STRING_TO_TIME_WITH_LOCAL_TIME_ZONE.method,
              RexImpTable.optimize2(operand,
                  Expressions.call(BuiltInMethod.UNIX_TIME_TO_STRING.method,
                      operand)),
              Expressions.call(BuiltInMethod.TIME_ZONE.method, root));

    case TIMESTAMP:
      return
          Expressions.call(BuiltInMethod.TIMESTAMP_STRING_TO_TIMESTAMP_WITH_LOCAL_TIME_ZONE.method,
              RexImpTable.optimize2(operand,
                  Expressions.call(BuiltInMethod.UNIX_TIMESTAMP_TO_STRING.method,
                      operand)),
              Expressions.call(BuiltInMethod.TIME_ZONE.method, root));

    case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
      return
          RexImpTable.optimize2(
              operand, Expressions.call(
                  BuiltInMethod
                      .TIMESTAMP_WITH_LOCAL_TIME_ZONE_TO_TIME_WITH_LOCAL_TIME_ZONE
                      .method,
                  operand));

    default:
      return defaultExpression.get();
    }
  }

  private Expression translateCastToTimestamp(RelDataType sourceType,
      Expression operand, ConstantExpression format, Supplier<Expression> defaultExpression) {

    switch (sourceType.getSqlTypeName()) {
    case CHAR:
    case VARCHAR:
      // If format string is supplied, parse formatted string into timestamp
      return Expressions.isConstantNull(format)
          ? Expressions.call(BuiltInMethod.STRING_TO_TIMESTAMP.method, operand)
          : Expressions.call(
              Expressions.new_(BuiltInMethod.PARSE_TIMESTAMP.method.getDeclaringClass()),
              BuiltInMethod.PARSE_TIMESTAMP.method, format, operand);

    case DATE:
      return
          Expressions.multiply(Expressions.convert_(operand, long.class),
              Expressions.constant(DateTimeUtils.MILLIS_PER_DAY));

    case TIME:
      return
          Expressions.add(
              Expressions.multiply(
                  Expressions.convert_(
                      Expressions.call(BuiltInMethod.CURRENT_DATE.method, root),
                      long.class),
                  Expressions.constant(DateTimeUtils.MILLIS_PER_DAY)),
              Expressions.convert_(operand, long.class));

    case TIME_WITH_LOCAL_TIME_ZONE:
      return
          RexImpTable.optimize2(
              operand, Expressions.call(
                  BuiltInMethod.TIME_WITH_LOCAL_TIME_ZONE_TO_TIMESTAMP.method,
                  Expressions.call(BuiltInMethod.UNIX_DATE_TO_STRING.method,
                      Expressions.call(BuiltInMethod.CURRENT_DATE.method, root)),
                  operand,
                  Expressions.call(BuiltInMethod.TIME_ZONE.method, root)));

    case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
      return
          RexImpTable.optimize2(
              operand, Expressions.call(
                  BuiltInMethod.TIMESTAMP_WITH_LOCAL_TIME_ZONE_TO_TIMESTAMP.method,
                  operand,
                  Expressions.call(BuiltInMethod.TIME_ZONE.method, root)));

    default:
      return defaultExpression.get();
    }
  }

  private Expression translateCastToTimestampWithLocalTimeZone(RelDataType sourceType,
      Expression operand, Supplier<Expression> defaultExpression) {

    switch (sourceType.getSqlTypeName()) {
    case CHAR:
    case VARCHAR:
      return
          Expressions.call(BuiltInMethod.STRING_TO_TIMESTAMP_WITH_LOCAL_TIME_ZONE.method, operand);

    case DATE:
      return
          Expressions.call(BuiltInMethod.TIMESTAMP_STRING_TO_TIMESTAMP_WITH_LOCAL_TIME_ZONE.method,
              RexImpTable.optimize2(operand,
                  Expressions.call(
                      BuiltInMethod.UNIX_TIMESTAMP_TO_STRING.method,
                      Expressions.multiply(
                          Expressions.convert_(operand, long.class),
                          Expressions.constant(DateTimeUtils.MILLIS_PER_DAY)))),
              Expressions.call(BuiltInMethod.TIME_ZONE.method, root));

    case TIME:
      return
          Expressions.call(BuiltInMethod.TIMESTAMP_STRING_TO_TIMESTAMP_WITH_LOCAL_TIME_ZONE.method,
              RexImpTable.optimize2(operand,
                  Expressions.call(BuiltInMethod.UNIX_TIMESTAMP_TO_STRING.method,
                      Expressions.add(
                          Expressions.multiply(
                              Expressions.convert_(
                                  Expressions.call(BuiltInMethod.CURRENT_DATE.method, root),
                                  long.class),
                              Expressions.constant(DateTimeUtils.MILLIS_PER_DAY)),
                          Expressions.convert_(operand, long.class)))),
              Expressions.call(BuiltInMethod.TIME_ZONE.method, root));

    case TIME_WITH_LOCAL_TIME_ZONE:
      return
          RexImpTable.optimize2(
              operand, Expressions.call(
                  BuiltInMethod
                      .TIME_WITH_LOCAL_TIME_ZONE_TO_TIMESTAMP_WITH_LOCAL_TIME_ZONE
                      .method,
                  Expressions.call(BuiltInMethod.UNIX_DATE_TO_STRING.method,
                      Expressions.call(BuiltInMethod.CURRENT_DATE.method, root)),
                  operand));

    case TIMESTAMP:
      return
          Expressions.call(BuiltInMethod.TIMESTAMP_STRING_TO_TIMESTAMP_WITH_LOCAL_TIME_ZONE.method,
              RexImpTable.optimize2(operand,
                  Expressions.call(
                      BuiltInMethod.UNIX_TIMESTAMP_TO_STRING.method,
                      operand)),
              Expressions.call(BuiltInMethod.TIME_ZONE.method, root));

    default:
      return defaultExpression.get();
    }
  }

  /**
   * Handle checked Exceptions declared in Method. In such case,
   * method call should be wrapped in a try...catch block.
   * "
   *      final Type method_call;
   *      try {
   *        method_call = callExpr
   *      } catch (Exception e) {
   *        throw new RuntimeException(e);
   *      }
   * "
   */
  Expression handleMethodCheckedExceptions(Expression callExpr) {
    // Try statement
    ParameterExpression methodCall =
        Expressions.parameter(callExpr.getType(), list.newName("method_call"));
    list.add(Expressions.declare(Modifier.FINAL, methodCall, null));
    Statement st = Expressions.statement(Expressions.assign(methodCall, callExpr));
    // Catch Block, wrap checked exception in unchecked exception
    ParameterExpression e = Expressions.parameter(0, Exception.class, "e");
    Expression uncheckedException = Expressions.new_(RuntimeException.class, e);
    CatchBlock cb = Expressions.catch_(e, Expressions.throw_(uncheckedException));
    list.add(Expressions.tryCatch(st, cb));
    return methodCall;
  }

  /** Dereferences an expression if it is a
   * {@link org.apache.calcite.rex.RexLocalRef}. */
  public RexNode deref(RexNode expr) {
    if (expr instanceof RexLocalRef) {
      RexLocalRef ref = (RexLocalRef) expr;
      final RexNode e2 = requireNonNull(program, "program")
          .getExprList().get(ref.getIndex());
      assert ref.getType().equals(e2.getType());
      return e2;
    } else {
      return expr;
    }
  }

  /**
   * 转换字面量
   *
   * <p>将 REX 字面量转换为 Java 常量表达式。</p>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>literal: REX 字面量</li>
   *   <li>type: 目标类型</li>
   *   <li>typeFactory: 类型工厂</li>
   *   <li>nullAs: 空值处理策略</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换后的常量表达式</li>
   * </ul>
   *
   * <p><b>异常:</b></p>
   * <ul>
   *   <li>如果字面量为 null 且 nullAs 为 NOT_POSSIBLE,抛出 ControlFlowException</li>
   * </ul>
   */
  public static Expression translateLiteral(
      RexLiteral literal,
      RelDataType type,
      JavaTypeFactory typeFactory,
      RexImpTable.NullAs nullAs) {
    if (literal.isNull()) {
      switch (nullAs) {
      case TRUE:
      case IS_NULL:
        return RexImpTable.TRUE_EXPR;
      case FALSE:
      case IS_NOT_NULL:
        return RexImpTable.FALSE_EXPR;
      case NOT_POSSIBLE:
        throw new ControlFlowException();
      case NULL:
      default:
        return RexImpTable.NULL_EXPR;
      }
    } else {
      switch (nullAs) {
      case IS_NOT_NULL:
        return RexImpTable.TRUE_EXPR;
      case IS_NULL:
        return RexImpTable.FALSE_EXPR;
      default:
        break;
      }
    }
    Type javaClass = typeFactory.getJavaClass(type);
    final Object value2;
    switch (literal.getType().getSqlTypeName()) {
    case DECIMAL:
      final BigDecimal bd = literal.getValueAs(BigDecimal.class);
      if (javaClass == float.class) {
        return Expressions.constant(bd, javaClass);
      } else if (javaClass == double.class) {
        return Expressions.constant(bd, javaClass);
      }
      assert javaClass == BigDecimal.class;
      return Expressions.new_(BigDecimal.class,
          Expressions.constant(
              requireNonNull(bd,
                  () -> "value for " + literal).toString()));
    case DATE:
    case TIME:
    case TIME_WITH_LOCAL_TIME_ZONE:
    case INTERVAL_YEAR:
    case INTERVAL_YEAR_MONTH:
    case INTERVAL_MONTH:
      value2 = literal.getValueAs(Integer.class);
      javaClass = int.class;
      break;
    case TIMESTAMP:
    case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
    case INTERVAL_DAY:
    case INTERVAL_DAY_HOUR:
    case INTERVAL_DAY_MINUTE:
    case INTERVAL_DAY_SECOND:
    case INTERVAL_HOUR:
    case INTERVAL_HOUR_MINUTE:
    case INTERVAL_HOUR_SECOND:
    case INTERVAL_MINUTE:
    case INTERVAL_MINUTE_SECOND:
    case INTERVAL_SECOND:
      value2 = literal.getValueAs(Long.class);
      javaClass = long.class;
      break;
    case CHAR:
    case VARCHAR:
      value2 = literal.getValueAs(String.class);
      break;
    case UUID:
      return Expressions.call(null, BuiltInMethod.UUID_FROM_STRING.method,
          Expressions.constant(literal.getValueAs(String.class)));
    case BINARY:
    case VARBINARY:
      return Expressions.new_(
          ByteString.class,
          Expressions.constant(
              literal.getValueAs(byte[].class),
              byte[].class));
    case GEOMETRY:
      final Geometry geom =
          requireNonNull(literal.getValueAs(Geometry.class),
              () -> "getValueAs(Geometries.Geom) for " + literal);
      final String wkt = SpatialTypeFunctions.ST_AsWKT(geom);
      return Expressions.call(null, BuiltInMethod.ST_GEOM_FROM_EWKT.method,
          Expressions.constant(wkt));
    case SYMBOL:
      value2 =
          requireNonNull(literal.getValueAs(Enum.class),
              () -> "getValueAs(Enum.class) for " + literal);
      javaClass = value2.getClass();
      break;
    default:
      final Primitive primitive = Primitive.ofBoxOr(javaClass);
      final Comparable value = literal.getValueAs(Comparable.class);
      if (primitive != null && value instanceof Number) {
        value2 = primitive.number((Number) value);
      } else {
        value2 = value;
      }
    }
    return Expressions.constant(value2, javaClass);
  }

  public List<Expression> translateList(
      List<RexNode> operandList,
      RexImpTable.NullAs nullAs) {
    return translateList(operandList, nullAs,
        EnumUtils.internalTypes(operandList));
  }

  public List<Expression> translateList(
      List<RexNode> operandList,
      RexImpTable.NullAs nullAs,
      List<? extends @Nullable Type> storageTypes) {
    final List<Expression> list = new ArrayList<>();
    for (Pair<RexNode, ? extends @Nullable Type> e : Pair.zip(operandList, storageTypes)) {
      list.add(translate(e.left, nullAs, e.right));
    }
    return list;
  }

  /**
   * Translates the list of {@code RexNode}, using the default output types.
   * This might be suboptimal in terms of additional box-unbox when you use
   * the translation later.
   * If you know the java class that will be used to store the results, use
   * {@link org.apache.calcite.adapter.enumerable.RexToLixTranslator#translateList(java.util.List, java.util.List)}
   * version.
   *
   * @param operandList list of RexNodes to translate
   *
   * @return translated expressions
   */
  public List<Expression> translateList(List<? extends RexNode> operandList) {
    return translateList(operandList, EnumUtils.internalTypes(operandList));
  }

  /**
   * Translates the list of {@code RexNode}, while optimizing for output
   * storage.
   * For instance, if the result of translation is going to be stored in
   * {@code Object[]}, and the input is {@code Object[]} as well,
   * then translator will avoid casting, boxing, etc.
   *
   * @param operandList list of RexNodes to translate
   * @param storageTypes hints of the java classes that will be used
   *                     to store translation results. Use null to use
   *                     default storage type
   *
   * @return translated expressions
   */
  public List<Expression> translateList(List<? extends RexNode> operandList,
      @Nullable List<? extends @Nullable Type> storageTypes) {
    final List<Expression> list = new ArrayList<>(operandList.size());

    for (int i = 0; i < operandList.size(); i++) {
      RexNode rex = operandList.get(i);
      Type desiredType = null;
      if (storageTypes != null) {
        desiredType = storageTypes.get(i);
      }
      final Expression translate = translate(rex, desiredType);
      list.add(translate);
      // desiredType is still a hint, thus we might get any kind of output
      // (boxed or not) when hint was provided.
      // It is favourable to get the type matching desired type
      if (desiredType == null && !isNullable(rex)) {
        assert !Primitive.isBox(translate.getType())
            : "Not-null boxed primitive should come back as primitive: "
            + rex + ", " + translate.getType();
      }
    }
    return list;
  }

  private Expression translateTableFunction(RexCall rexCall, Expression inputEnumerable,
      PhysType inputPhysType, PhysType outputPhysType) {
    assert rexCall.getOperator() instanceof SqlWindowTableFunction;
    TableFunctionCallImplementor implementor =
        RexImpTable.INSTANCE.get((SqlWindowTableFunction) rexCall.getOperator());
    if (implementor == null) {
      throw Util.needToImplement("implementor of " + rexCall.getOperator().getName());
    }
    return implementor.implement(
        this, inputEnumerable, rexCall, inputPhysType, outputPhysType);
  }

  public static Expression translateCondition(RexProgram program,
      JavaTypeFactory typeFactory, BlockBuilder list, InputGetter inputGetter,
      Function1<String, InputGetter> correlates, SqlConformance conformance) {
    RexLocalRef condition = program.getCondition();
    if (condition == null) {
      return RexImpTable.TRUE_EXPR;
    }
    final ParameterExpression root = DataContext.ROOT;
    RexToLixTranslator translator =
        new RexToLixTranslator(program, typeFactory, root, inputGetter, list,
            null, new RexBuilder(typeFactory), conformance, null);
    translator = translator.setCorrelates(correlates);
    return translator.translate(
        condition,
        RexImpTable.NullAs.FALSE);
  }

  /** Returns whether an expression is nullable.
   *
   * @param e Expression
   * @return Whether expression is nullable
   */
  public boolean isNullable(RexNode e) {
    return e.getType().isNullable();
  }

  public RexToLixTranslator setBlock(BlockBuilder list) {
    if (list == this.list) {
      return this;
    }
    return new RexToLixTranslator(program, typeFactory, root, inputGetter, list,
        staticList, builder, conformance, correlates);
  }

  public RexToLixTranslator setCorrelates(
      @Nullable Function1<String, InputGetter> correlates) {
    if (this.correlates == correlates) {
      return this;
    }
    return new RexToLixTranslator(program, typeFactory, root, inputGetter, list,
        staticList, builder, conformance, correlates);
  }

  public Expression getRoot() {
    return root;
  }

  /** If an expression is a {@code NUMERIC} derived from an {@code INTERVAL},
   * scales it appropriately; returns the operand unchanged if the conversion
   * is not from {@code INTERVAL} to {@code NUMERIC}.
   * Does <b>not</b> scale values of type DECIMAL, these are expected
   * to be already scaled. */
  private static Expression scaleValue(
      RelDataType sourceType,
      RelDataType targetType,
      Expression operand) {
    final SqlTypeFamily targetFamily = targetType.getSqlTypeName().getFamily();
    final SqlTypeFamily sourceFamily = sourceType.getSqlTypeName().getFamily();
    if (targetFamily == SqlTypeFamily.NUMERIC
        // multiplyDivide cannot handle DECIMALs, but for DECIMAL
        // target types the result is already scaled.
        && targetType.getSqlTypeName() != SqlTypeName.DECIMAL
        && (sourceFamily == SqlTypeFamily.INTERVAL_YEAR_MONTH
            || sourceFamily == SqlTypeFamily.INTERVAL_DAY_TIME)) {
      // Scale to the given field.
      final BigDecimal multiplier = BigDecimal.ONE;
      final BigDecimal divider =
          sourceType.getSqlTypeName().getEndUnit().multiplier;
      return RexImpTable.multiplyDivide(operand, multiplier, divider);
    }
    if (SqlTypeName.INTERVAL_TYPES.contains(targetType.getSqlTypeName())
        && !SqlTypeName.INTERVAL_TYPES.contains(sourceType.getSqlTypeName())) {
      // Conversion between intervals is only allowed if the intervals have the same type,
      // and then it should be a no-op.
      final BigDecimal multiplier = targetType.getSqlTypeName().getEndUnit().multiplier;
      final BigDecimal divider = BigDecimal.ONE;
      return RexImpTable.multiplyDivide(operand, multiplier, divider);
    }
    return operand;
  }

  /**
   * 访问 {@code RexInputRef}(输入引用)
   *
   * <p>如果之前从未在当前存储类型下访问过此节点,{@code RexToLixTranslator}
   * 通常会生成三行代码。</p>
   *
   * <p><b>生成的代码示例:</b></p>
   * <p>当访问 Employee 表中的列(名为 commission)时,生成的代码片段为:</p>
   * <pre>
   * final Employee current = (Employee) inputEnumerator.current();
   * final Integer input_value = current.commission;
   * final boolean input_isNull = input_value == null;
   * </pre>
   *
   * <p><b>转换过程:</b></p>
   * <ol>
   *   <li>检查缓存,如果已转换过则直接返回</li>
   *   <li>生成代码获取输入值</li>
   *   <li>生成值变量声明</li>
   *   <li>生成空值检查</li>
   *   <li>缓存结果(除非是 PrevInputGetter)</li>
   * </ol>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>inputRef: 输入引用</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换结果,包含值变量和空值变量</li>
   * </ul>
   */
  @Override public Result visitInputRef(RexInputRef inputRef) {
    // 创建缓存键:(RexInputRef, 当前存储类型)
    final Pair<RexNode, @Nullable Type> key = Pair.of(inputRef, currentStorageType);
    // 如果 RexInputRef 已经在当前存储类型下访问过,则不需要再次访问,直接返回结果
    if (rexWithStorageTypeResultMap.containsKey(key)) {
      return rexWithStorageTypeResultMap.get(key);
    }
    // 生成一行代码来获取输入值,例如:
    // "final Employee current =(Employee) inputEnumerator.current();"
    final Expression valueExpression =
        requireNonNull(inputGetter, "inputGetter")
            .field(list, inputRef.getIndex(), currentStorageType);

    // 生成一行代码来声明 RexInputRef 的值变量,例如:
    // "final Integer input_value = current.commission;"
    final ParameterExpression valueVariable =
        Expressions.parameter(
            valueExpression.getType(), list.newName("input_value"));
    list.add(Expressions.declare(Modifier.FINAL, valueVariable, valueExpression));

    // 生成一行代码来检查 RexInputRef 是否为 null,例如:
    // "final boolean input_isNull = input_value == null;"
    final Expression isNullExpression = checkNull(valueVariable);
    final ParameterExpression isNullVariable =
        Expressions.parameter(
            Boolean.TYPE, list.newName("input_isNull"));
    list.add(Expressions.declare(Modifier.FINAL, isNullVariable, isNullExpression));

    final Result result = new Result(isNullVariable, valueVariable);

    // 缓存 <RexInputRef, currentStorageType> 的结果
    // 注意: EnumerableMatch 的 PrevInputGetter 每次都会改变索引,
    // 在这种情况下重用结果是不正确的。
    if (!(inputGetter instanceof EnumerableMatch.PrevInputGetter)) {
      rexWithStorageTypeResultMap.put(key, result);
    }
    return new Result(isNullVariable, valueVariable);
  }

  /**
   * 访问 Lambda 引用
   *
   * <p>Lambda 引用是 lambda 表达式中的参数引用。</p>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>ref: Lambda 引用</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换结果,包含值变量和空值变量</li>
   * </ul>
   */
  @Override public Result visitLambdaRef(RexLambdaRef ref) {
    // 创建值变量,使用 lambda 参数的类型和名称
    final ParameterExpression valueVariable =
        Expressions.parameter(
            typeFactory.getJavaClass(ref.getType()), ref.getName());

    // 生成一行代码来检查 lambdaRef 是否为 null,例如:
    // "final boolean input_isNull = $0 == null;"
    final Expression isNullExpression = checkNull(valueVariable);
    final ParameterExpression isNullVariable =
        Expressions.parameter(
            Boolean.TYPE, list.newName("input_isNull"));
    list.add(Expressions.declare(Modifier.FINAL, isNullVariable, isNullExpression));
    return new Result(isNullVariable, valueVariable);
  }

  /**
   * 访问局部引用
   *
   * <p>局部引用需要先解引用,然后访问被引用的表达式。</p>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>localRef: 局部引用</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换结果</li>
   * </ul>
   */
  @Override public Result visitLocalRef(RexLocalRef localRef) {
    // 解引用后访问
    return deref(localRef).accept(this);
  }

  /**
   * 访问 {@code RexLiteral}(字面量)
   *
   * <p>如果之前从未访问过,{@code RexToLixTranslator} 会生成两行代码。</p>
   *
   * <p><b>生成的代码示例:</b></p>
   * <p>当访问原始 int (10)时,生成的代码片段为:</p>
   * <pre>
   * final int literal_value = 10;
   * final boolean literal_isNull = false;
   * </pre>
   *
   * <p><b>转换过程:</b></p>
   * <ol>
   *   <li>检查缓存,如果已转换过则直接返回</li>
   *   <li>生成字面量表达式</li>
   *   <li>生成值变量声明</li>
   *   <li>生成空值检查</li>
   *   <li>维护字面量映射</li>
   *   <li>缓存结果</li>
   * </ol>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>literal: 字面量</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换结果,包含值变量和空值变量</li>
   * </ul>
   */
  @Override public Result visitLiteral(RexLiteral literal) {
    // 如果 RexLiteral 已经访问过,直接返回结果
    if (rexResultMap.containsKey(literal)) {
      return rexResultMap.get(literal);
    }
    // 生成一行代码来声明 RexLiteral 的值变量,例如:
    // "final int literal_value = 10;"
    final Expression valueExpression = literal.isNull()
        // 注意:即使是 null 字面量,我们也不能丢失其类型信息
        ? getTypedNullLiteral(literal)
        : translateLiteral(literal, literal.getType(),
            typeFactory, RexImpTable.NullAs.NOT_POSSIBLE);
    final ParameterExpression valueVariable;
    // 尝试将常量添加到静态列表
    final Expression literalValue =
        appendConstant("literal_value", valueExpression);
    if (literalValue instanceof ParameterExpression) {
      valueVariable = (ParameterExpression) literalValue;
    } else {
      // 如果不是参数表达式,创建新的值变量
      valueVariable =
          Expressions.parameter(valueExpression.getType(),
              list.newName("literal_value"));
      list.add(
          Expressions.declare(Modifier.FINAL, valueVariable, valueExpression));
    }

    // 生成一行代码来检查 RexLiteral 是否为 null,例如:
    // "final boolean literal_isNull = false;"
    final Expression isNullExpression =
        literal.isNull() ? RexImpTable.TRUE_EXPR : RexImpTable.FALSE_EXPR;
    final ParameterExpression isNullVariable =
        Expressions.parameter(Boolean.TYPE, list.newName("literal_isNull"));
    list.add(Expressions.declare(Modifier.FINAL, isNullVariable, isNullExpression));

    // 维护从 valueVariable (ParameterExpression) 到实际 Expression 的映射
    literalMap.put(valueVariable, valueExpression);
    final Result result = new Result(isNullVariable, valueVariable);
    // 缓存 RexLiteral 的结果
    rexResultMap.put(literal, result);
    return result;
  }

  /**
   * Returns an {@code Expression} for null literal without losing its type
   * information.
   */
  private ConstantExpression getTypedNullLiteral(RexLiteral literal) {
    assert literal.isNull();
    Type javaClass = typeFactory.getJavaClass(literal.getType());
    switch (literal.getType().getSqlTypeName()) {
    case DATE:
    case TIME:
    case TIME_WITH_LOCAL_TIME_ZONE:
    case INTERVAL_YEAR:
    case INTERVAL_YEAR_MONTH:
    case INTERVAL_MONTH:
      javaClass = Integer.class;
      break;
    case TIMESTAMP:
    case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
    case INTERVAL_DAY:
    case INTERVAL_DAY_HOUR:
    case INTERVAL_DAY_MINUTE:
    case INTERVAL_DAY_SECOND:
    case INTERVAL_HOUR:
    case INTERVAL_HOUR_MINUTE:
    case INTERVAL_HOUR_SECOND:
    case INTERVAL_MINUTE:
    case INTERVAL_MINUTE_SECOND:
    case INTERVAL_SECOND:
      javaClass = Long.class;
      break;
    default:
      break;
    }
    return javaClass == null || javaClass == Void.class
        ? RexImpTable.NULL_EXPR
        : Expressions.constant(null, javaClass);
  }

  /**
   * 访问 {@code RexCall}(函数调用)
   *
   * <p>对于大多数 {@code SqlOperator},我们可以从 {@code RexImpTable} 获取实现器。
   * 几个具有特殊语义的操作符(例如 CaseWhen)需要单独实现。</p>
   *
   * <p><b>特殊操作符:</b></p>
   * <ul>
   *   <li>PREV: 前一行引用</li>
   *   <li>CASE: CASE WHEN 表达式</li>
   *   <li>SEARCH: 搜索操作</li>
   * </ul>
   *
   * <p><b>转换过程:</b></p>
   * <ol>
   *   <li>检查缓存</li>
   *   <li>处理特殊操作符</li>
   *   <li>获取操作符实现器</li>
   *   <li>转换所有操作数</li>
   *   <li>调用实现器生成代码</li>
   *   <li>缓存结果</li>
   * </ol>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>call: 函数调用</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换结果</li>
   * </ul>
   */
  @Override public Result visitCall(RexCall call) {
    // 检查缓存
    if (rexResultMap.containsKey(call)) {
      return rexResultMap.get(call);
    }
    final SqlOperator operator = call.getOperator();
    // 处理 PREV 操作符
    if (operator == PREV) {
      return implementPrev(call);
    }
    // 处理 CASE 操作符
    if (operator == CASE) {
      return implementCaseWhen(call);
    }
    // 处理 SEARCH 操作符
    if (operator == SEARCH) {
      return RexUtil.expandSearch(builder, program, call).accept(this);
    }
    // 从 RexImpTable 获取操作符实现器
    final RexImpTable.RexCallImplementor implementor =
        RexImpTable.INSTANCE.get(operator);
    if (implementor == null) {
      throw new RuntimeException("cannot translate call " + call);
    }
    // 获取操作数列表和存储类型
    final List<RexNode> operandList = call.getOperands();
    final List<@Nullable Type> storageTypes = EnumUtils.internalTypes(operandList);
    final List<Result> operandResults = new ArrayList<>();
    // 转换每个操作数
    for (int i = 0; i < operandList.size(); i++) {
      final Result operandResult =
          implementCallOperand(operandList.get(i), storageTypes.get(i), this);
      operandResults.add(operandResult);
    }
    // 缓存操作数结果
    callOperandResultMap.put(call, operandResults);
    // 调用实现器生成代码
    final Result result = implementor.implement(this, call, operandResults);
    // 缓存结果
    rexResultMap.put(call, result);
    return result;
  }

  /**
   * 实现函数调用操作数
   *
   * <p>转换操作数并处理存储类型。</p>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>operand: 操作数</li>
   *   <li>storageType: 存储类型</li>
   *   <li>translator: 翻译器</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换结果</li>
   * </ul>
   */
  private static Result implementCallOperand(final RexNode operand,
      final @Nullable Type storageType, final RexToLixTranslator translator) {
    // 保存原始存储类型
    final Type originalStorageType = translator.currentStorageType;
    // 设置当前存储类型
    translator.currentStorageType = storageType;
    // 转换操作数
    Result operandResult = operand.accept(translator);
    // 如果指定了存储类型,转换为内部存储类型
    if (storageType != null) {
      operandResult = translator.toInnerStorageType(operandResult, storageType);
    }
    // 恢复原始存储类型
    translator.currentStorageType = originalStorageType;
    return operandResult;
  }

  /**
   * 实现函数调用操作数(返回 Expression 版本)
   *
   * <p>与 implementCallOperand 类似,但直接返回 Expression 而不是 Result。</p>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>operand: 操作数</li>
   *   <li>storageType: 存储类型</li>
   *   <li>translator: 翻译器</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换后的表达式</li>
   * </ul>
   */
  private static Expression implementCallOperand2(final RexNode operand,
      final @Nullable Type storageType, final RexToLixTranslator translator) {
    // 保存原始存储类型
    final Type originalStorageType = translator.currentStorageType;
    // 设置当前存储类型
    translator.currentStorageType = storageType;
    // 转换操作数
    final Expression result =  translator.translate(operand);
    // 恢复原始存储类型
    translator.currentStorageType = originalStorageType;
    return result;
  }

  /**
   * 实现 PREV 操作符
   *
   * <p>对于 {@code PREV} 操作符,应该先设置 {@code inputGetter} 的偏移量。</p>
   *
   * <p>PREV 操作符用于访问匹配操作中的前一行数据。</p>
   *
   * <p><b>实现过程:</b></p>
   * <ol>
   *   <li>获取节点和偏移量</li>
   *   <li>计算偏移量(乘以 -1)</li>
   *   <li>设置输入获取器的偏移量</li>
   *   <li>访问节点</li>
   * </ol>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>call: PREV 函数调用</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换结果</li>
   * </ul>
   */
  private Result implementPrev(RexCall call) {
    // 获取节点和偏移量
    final RexNode node = call.getOperands().get(0);
    final RexNode offset = call.getOperands().get(1);
    // 计算偏移量(乘以 -1)
    final Expression offs =
        Expressions.multiply(translate(offset), Expressions.constant(-1));
    // 设置输入获取器的偏移量
    requireNonNull((EnumerableMatch.PrevInputGetter) inputGetter, "inputGetter")
        .setOffset(offs);
    // 访问节点
    return node.accept(this);
  }

  /**
   * The CASE operator is SQL’s way of handling if/then logic.
   * Different with other {@code RexCall}s, it is not safe to
   * implement its operands first.
   * For example: {@code
   *   select case when s=0 then false
   *          else 100/s > 0 end
   *   from (values (1),(0)) ax(s);
   * }
   */
  private Result implementCaseWhen(RexCall call) {
    final Type returnType = typeFactory.getJavaClass(call.getType());
    final ParameterExpression valueVariable =
        Expressions.parameter(returnType,
            list.newName("case_when_value"));
    list.add(Expressions.declare(0, valueVariable, null));
    final List<RexNode> operandList = call.getOperands();
    implementRecursively(this, operandList, valueVariable, 0);
    final Expression isNullExpression = checkNull(valueVariable);
    final ParameterExpression isNullVariable =
        Expressions.parameter(
            Boolean.TYPE, list.newName("case_when_isNull"));
    list.add(Expressions.declare(Modifier.FINAL, isNullVariable, isNullExpression));
    final Result result = new Result(isNullVariable, valueVariable);
    rexResultMap.put(call, result);
    return result;
  }

  /**
   * 递归实现 CASE WHEN 语句
   *
   * <p>CASE 语句的形式为:</p>
   * <pre>
   * CASE WHEN a THEN b [WHEN c THEN d]* [ELSE e] END
   * </pre>
   *
   * <p>当 {@code a = true} 时,返回 {@code b};</p>
   * <p>当 {@code c = true} 时,返回 {@code d};</p>
   * <p>否则返回 {@code e}。</p>
   *
   * <p><b>生成的代码结构:</b></p>
   * <pre>
   * int case_when_value;
   * ......code for a......
   * if (!a_isNull && a_value) {
   *     ......code for b......
   *     case_when_value = res(b_isNull, b_value);
   * } else {
   *     ......code for c......
   *     if (!c_isNull && c_value) {
   *         ......code for d......
   *         case_when_value = res(d_isNull, d_value);
   *     } else {
   *         ......code for e......
   *         case_when_value = res(e_isNull, e_value);
   *     }
   * }
   * </pre>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>currentTranslator: 当前翻译器</li>
   *   <li>operandList: 操作数列表</li>
   *   <li>valueVariable: 值变量</li>
   *   <li>pos: 当前位置</li>
   * </ul>
   */
  private static void implementRecursively(RexToLixTranslator currentTranslator,
      List<RexNode> operandList, ParameterExpression valueVariable, int pos) {
    final BlockBuilder currentBlockBuilder =
        currentTranslator.getBlockBuilder();
    final List<@Nullable Type> storageTypes =
        EnumUtils.internalTypes(operandList);
    // [ELSE] clause
    if (pos == operandList.size() - 1) {
      Expression res =
          implementCallOperand2(operandList.get(pos), storageTypes.get(pos),
              currentTranslator);
      currentBlockBuilder.add(
          Expressions.statement(
              Expressions.assign(valueVariable,
                  EnumUtils.convert(res, valueVariable.getType()))));
      return;
    }
    // Condition code: !a_isNull && a_value
    final RexNode testerNode = operandList.get(pos);
    final Result testerResult =
        implementCallOperand(testerNode, storageTypes.get(pos),
            currentTranslator);
    final Expression tester =
        Expressions.andAlso(Expressions.not(testerResult.isNullVariable),
            testerResult.valueVariable);
    // Code for {if} branch
    final RexNode ifTrueNode = operandList.get(pos + 1);
    final BlockBuilder ifTrueBlockBuilder =
        new BlockBuilder(true, currentBlockBuilder);
    final RexToLixTranslator ifTrueTranslator =
        currentTranslator.setBlock(ifTrueBlockBuilder);
    ifTrueTranslator.rexResultMap.putAll(currentTranslator.rexResultMap);
    final Expression ifTrueRes =
        implementCallOperand2(ifTrueNode, storageTypes.get(pos + 1),
            ifTrueTranslator);
    // Assign the value: case_when_value = ifTrueRes
    ifTrueBlockBuilder.add(
        Expressions.statement(
            Expressions.assign(valueVariable,
                EnumUtils.convert(ifTrueRes, valueVariable.getType()))));
    final BlockStatement ifTrue = ifTrueBlockBuilder.toBlock();
    // There is no [ELSE] clause
    if (pos + 1 == operandList.size() - 1) {
      currentBlockBuilder.add(
          Expressions.ifThen(tester, ifTrue));
      return;
    }
    // Generate code for {else} branch recursively
    final BlockBuilder ifFalseBlockBuilder =
        new BlockBuilder(true, currentBlockBuilder);
    final RexToLixTranslator ifFalseTranslator =
        currentTranslator.setBlock(ifFalseBlockBuilder);
    ifFalseTranslator.rexResultMap.putAll(currentTranslator.rexResultMap);
    implementRecursively(ifFalseTranslator, operandList, valueVariable, pos + 2);
    final BlockStatement ifFalse = ifFalseBlockBuilder.toBlock();
    currentBlockBuilder.add(
        Expressions.ifThenElse(tester, ifTrue, ifFalse));
  }

  private Result toInnerStorageType(Result result, Type storageType) {
    final Expression valueExpression =
        EnumUtils.toInternal(result.valueVariable, storageType);
    if (valueExpression.equals(result.valueVariable)) {
      return result;
    }
    final ParameterExpression valueVariable =
        Expressions.parameter(
            valueExpression.getType(),
            list.newName(result.valueVariable.name + "_inner_type"));
    list.add(Expressions.declare(Modifier.FINAL, valueVariable, valueExpression));
    final ParameterExpression isNullVariable = result.isNullVariable;
    return new Result(isNullVariable, valueVariable);
  }

  /**
   * 访问动态参数
   *
   * <p>动态参数是在运行时提供的参数,通常来自预编译语句。</p>
   *
   * <p><b>转换过程:</b></p>
   * <ol>
   *   <li>检查缓存</li>
   *   <li>确定存储类型</li>
   *   <li>从 DataContext 获取参数值</li>
   *   <li>对于数值类型,先转换为 Number 再转换为目标类型</li>
   *   <li>生成值变量和空值检查</li>
   *   <li>缓存结果</li>
   * </ol>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>dynamicParam: 动态参数</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换结果</li>
   * </ul>
   */
  @Override public Result visitDynamicParam(RexDynamicParam dynamicParam) {
    // 创建缓存键
    final Pair<RexNode, @Nullable Type> key =
        Pair.of(dynamicParam, currentStorageType);
    // 检查缓存
    if (rexWithStorageTypeResultMap.containsKey(key)) {
      return rexWithStorageTypeResultMap.get(key);
    }
    // 确定存储类型
    final Type storageType = currentStorageType != null
        ? currentStorageType : typeFactory.getJavaClass(dynamicParam.getType());

    // 检查是否为数值类型
    final boolean isNumeric = SqlTypeFamily.NUMERIC.contains(dynamicParam.getType());

    // 对于数值类型,使用 java.lang.Number 来防止类型转换异常
    // 当参数类型与目标类型不同时
    final Expression valueExpression = isNumeric
        ? EnumUtils.convert(
            EnumUtils.convert(
                Expressions.call(root, BuiltInMethod.DATA_CONTEXT_GET.method,
                    Expressions.constant("?" + dynamicParam.getIndex())),
                java.lang.Number.class),
            storageType)
        : EnumUtils.convert(
            Expressions.call(root, BuiltInMethod.DATA_CONTEXT_GET.method,
                Expressions.constant("?" + dynamicParam.getIndex())),
            storageType);

    // 生成值变量
    final ParameterExpression valueVariable =
        Expressions.parameter(valueExpression.getType(),
            list.newName("value_dynamic_param"));
    list.add(Expressions.declare(Modifier.FINAL, valueVariable, valueExpression));
    // 生成空值检查变量
    final ParameterExpression isNullVariable =
        Expressions.parameter(Boolean.TYPE, list.newName("isNull_dynamic_param"));
    list.add(
        Expressions.declare(Modifier.FINAL, isNullVariable,
            checkNull(valueVariable)));
    // 创建结果并缓存
    final Result result = new Result(isNullVariable, valueVariable);
    rexWithStorageTypeResultMap.put(key, result);
    return result;
  }

  /**
   * 访问字段访问
   *
   * <p>字段访问用于访问结构化类型(如 ROW)的字段或相关变量的字段。</p>
   *
   * <p><b>支持的场景:</b></p>
   * <ul>
   *   <li>相关变量字段访问(CORREL_VARIABLE)</li>
   *   <li>结构化类型字段访问(STRUCT_ACCESS)</li>
   * </ul>
   *
   * <p><b>转换过程:</b></p>
   * <ol>
   *   <li>检查缓存</li>
   *   <li>解引用目标表达式</li>
   *   <li>根据目标类型进行不同的处理</li>
   *   <li>生成值变量和空值检查</li>
   *   <li>缓存结果</li>
   * </ol>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>fieldAccess: 字段访问</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>转换结果</li>
   * </ul>
   */
  @Override public Result visitFieldAccess(RexFieldAccess fieldAccess) {
    // 创建缓存键
    final Pair<RexNode, @Nullable Type> key =
        Pair.of(fieldAccess, currentStorageType);
    // 检查缓存
    if (rexWithStorageTypeResultMap.containsKey(key)) {
      return rexWithStorageTypeResultMap.get(key);
    }
    // 解引用目标表达式
    final RexNode target = deref(fieldAccess.getReferenceExpr());
    int fieldIndex = fieldAccess.getField().getIndex();
    String fieldName = fieldAccess.getField().getName();
    // 根据目标类型进行不同的处理
    switch (target.getKind()) {
    case CORREL_VARIABLE:
      // 处理相关变量字段访问
      if (correlates == null) {
        throw new RuntimeException("Cannot translate " + fieldAccess
            + " since correlate variables resolver is not defined");
      }
      // 获取相关变量的输入获取器
      final RexToLixTranslator.InputGetter getter =
          correlates.apply(((RexCorrelVariable) target).getName());
      // 获取字段值
      final Expression input =
          getter.field(list, fieldIndex, currentStorageType);
      // 检查输入是否为 null
      final Expression condition = checkNull(input);
      // 创建值变量
      final ParameterExpression valueVariable =
          Expressions.parameter(input.getType(), list.newName("corInp_value"));
      list.add(Expressions.declare(Modifier.FINAL, valueVariable, input));
      // 创建空值检查变量
      final ParameterExpression isNullVariable =
          Expressions.parameter(Boolean.TYPE, list.newName("corInp_isNull"));
      final Expression isNullExpression =
          Expressions.condition(condition,
              RexImpTable.TRUE_EXPR,
              checkNull(valueVariable));
      list.add(
          Expressions.declare(Modifier.FINAL, isNullVariable, isNullExpression));
      final Result result1 = new Result(isNullVariable, valueVariable);
      rexWithStorageTypeResultMap.put(key, result1);
      return result1;
    default:
      // 处理结构化类型字段访问
      RexNode rxIndex =
          builder.makeLiteral(fieldIndex, typeFactory.createType(int.class), true);
      RexNode rxName =
          builder.makeLiteral(fieldName, typeFactory.createType(String.class), true);
      RexCall accessCall =
          (RexCall) builder.makeCall(fieldAccess.getType(),
              SqlStdOperatorTable.STRUCT_ACCESS,
              ImmutableList.of(target, rxIndex, rxName));
      final Result result2 = accessCall.accept(this);
      rexWithStorageTypeResultMap.put(key, result2);
      return result2;
    }
  }

  @Override public Result visitOver(RexOver over) {
    throw new RuntimeException("cannot translate expression " + over);
  }

  @Override public Result visitCorrelVariable(RexCorrelVariable correlVariable) {
    throw new RuntimeException("Cannot translate " + correlVariable
        + ". Correlated variables should always be referenced by field access");
  }

  @Override public Result visitRangeRef(RexRangeRef rangeRef) {
    throw new RuntimeException("cannot translate expression " + rangeRef);
  }

  @Override public Result visitSubQuery(RexSubQuery subQuery) {
    throw new RuntimeException("cannot translate expression " + subQuery);
  }

  @Override public Result visitTableInputRef(RexTableInputRef fieldRef) {
    throw new RuntimeException("cannot translate expression " + fieldRef);
  }

  @Override public Result visitPatternFieldRef(RexPatternFieldRef fieldRef) {
    return visitInputRef(fieldRef);
  }

  @Override public Result visitLambda(RexLambda lambda) {
    final RexNode expression = lambda.getExpression();
    final List<RexLambdaRef> rexLambdaRefs = lambda.getParameters();

    // Prepare parameter expressions for lambda expression
    final ParameterExpression[] parameterExpressions =
        new ParameterExpression[rexLambdaRefs.size()];
    for (int i = 0; i < rexLambdaRefs.size(); i++) {
      final RexLambdaRef rexLambdaRef = rexLambdaRefs.get(i);
      parameterExpressions[i] =
          Expressions.parameter(
              typeFactory.getJavaClass(rexLambdaRef.getType()), rexLambdaRef.getName());
    }

    // Generate code for lambda expression body
    final RexToLixTranslator exprTranslator = this.setBlock(new BlockBuilder());
    final Result exprResult = expression.accept(exprTranslator);
    exprTranslator.list.add(
        Expressions.return_(null, exprResult.valueVariable));

    // Generate code for lambda expression
    final Expression functionExpression =
        Expressions.lambda(exprTranslator.list.toBlock(), parameterExpressions);
    final ParameterExpression valueVariable =
        Expressions.parameter(functionExpression.getType(), list.newName("function_value"));
    list.add(Expressions.declare(Modifier.FINAL, valueVariable, functionExpression));

    // Generate code for checking whether lambda expression is null
    final Expression isNullExpression = checkNull(valueVariable);
    final ParameterExpression isNullVariable =
        Expressions.parameter(Boolean.TYPE, list.newName("function_isNull"));
    list.add(Expressions.declare(Modifier.FINAL, isNullVariable, isNullExpression));

    return new Result(isNullVariable, valueVariable);
  }

  Expression checkNull(Expression expr) {
    if (Primitive.flavor(expr.getType())
        == Primitive.Flavor.PRIMITIVE) {
      return RexImpTable.FALSE_EXPR;
    }
    return Expressions.equal(expr, RexImpTable.NULL_EXPR);
  }

  Expression checkNotNull(Expression expr) {
    if (Primitive.flavor(expr.getType())
        == Primitive.Flavor.PRIMITIVE) {
      return RexImpTable.TRUE_EXPR;
    }
    return Expressions.notEqual(expr, RexImpTable.NULL_EXPR);
  }

  BlockBuilder getBlockBuilder() {
    return list;
  }

  Expression getLiteral(Expression literalVariable) {
    return requireNonNull(literalMap.get(literalVariable),
        () -> "literalMap.get(literalVariable) for " + literalVariable);
  }

  /** Returns the value of a literal. */
  @Nullable Object getLiteralValue(@Nullable Expression expr) {
    if (expr instanceof ParameterExpression) {
      final Expression constantExpr = literalMap.get(expr);
      return getLiteralValue(constantExpr);
    }
    if (expr instanceof ConstantExpression) {
      return ((ConstantExpression) expr).value;
    }
    return null;
  }

  List<Result> getCallOperandResult(RexCall call) {
    return requireNonNull(callOperandResultMap.get(call),
        () -> "callOperandResultMap.get(call) for " + call);
  }

  /** Returns an expression that yields the function object whose method
   * we are about to call.
   *
   * <p>It might be 'new MyFunction()', but it also might be a reference
   * to a static field 'F', defined by
   * 'static final MyFunction F = new MyFunction()'.
   *
   * <p>If there is a constructor that takes a {@link FunctionContext}
   * argument, we call that, passing in the values of arguments that are
   * literals; this allows the function to do some computation at load time.
   *
   * <p>If the call is "f(1, 2 + 3, 'foo')" and "f" is implemented by method
   * "eval(int, int, String)" in "class MyFun", the expression might be
   * "new MyFunction(FunctionContexts.of(new Object[] {1, null, "foo"})".
   *
   * @param method Method that implements the UDF
   * @param call Call to the UDF
   * @return New expression
   */
  Expression functionInstance(RexCall call, Method method) {
    final RexCallBinding callBinding =
        RexCallBinding.create(typeFactory, call, program, ImmutableList.of());
    final Expression target = getInstantiationExpression(method, callBinding);
    return appendConstant("f", target);
  }

  /** Helper for {@link #functionInstance}. */
  private Expression getInstantiationExpression(Method method,
      RexCallBinding callBinding) {
    final Class<?> declaringClass = method.getDeclaringClass();
    // If the UDF class has a constructor that takes a Context argument,
    // use that.
    try {
      final Constructor<?> constructor =
          declaringClass.getConstructor(FunctionContext.class);
      final List<Expression> constantArgs = new ArrayList<>();
      //noinspection unchecked
      Ord.forEach(method.getParameterTypes(),
          (parameterType, i) ->
              constantArgs.add(
                  callBinding.isOperandLiteral(i, true)
                      ? appendConstant("_arg",
                      Expressions.constant(
                          callBinding.getOperandLiteralValue(i,
                              Primitive.box(parameterType))))
                      : Expressions.constant(null)));
      final Expression context =
          Expressions.call(BuiltInMethod.FUNCTION_CONTEXTS_OF.method,
              DataContext.ROOT,
              Expressions.newArrayInit(Object.class, constantArgs));
      return Expressions.new_(constructor, context);
    } catch (NoSuchMethodException e) {
      // ignore
    }
    // The UDF class must have a public zero-args constructor.
    // Assume that the validator checked already.
    return Expressions.new_(declaringClass);
  }

  /** Stores a constant expression in a variable. */
  private Expression appendConstant(String name, Expression e) {
    if (staticList != null) {
      // If name is "camelCase", upperName is "CAMEL_CASE".
      final String upperName =
          CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, name);
      return staticList.append(upperName, e);
    } else {
      return list.append(name, e);
    }
  }

  /**
 * 将输入的字段转换为表达式
 *
 * <p>这是一个函数式接口,用于从输入中获取字段值。</p>
 *
 * <p><b>方法:</b></p>
 * <ul>
 *   <li>field: 获取字段表达式</li>
 * </ul>
 */
public interface InputGetter {
  /**
   * 获取字段表达式
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>list: 代码块构建器</li>
   *   <li>index: 字段索引</li>
   *   <li>storageType: 存储类型</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>字段表达式</li>
   * </ul>
   */
  Expression field(BlockBuilder list, int index, @Nullable Type storageType);
}

/**
 * {@link InputGetter} 的实现,调用 {@link PhysType#fieldReference}
 *
 * <p>这个实现使用物理类型来引用字段。</p>
 *
 * <p><b>成员变量:</b></p>
 * <ul>
 *   <li>inputs: 输入表达式到物理类型的映射</li>
 * </ul>
 */
public static class InputGetterImpl implements InputGetter {
  /**
   * 输入表达式到物理类型的不可变映射
   */
  private final ImmutableMap<Expression, PhysType> inputs;

  /**
   * @deprecated 已弃用,将在 2.0 版本前移除
   */
  @Deprecated // to be removed before 2.0
  public InputGetterImpl(List<Pair<Expression, PhysType>> inputs) {
    this(mapOf(inputs));
  }

  /**
   * 使用单个输入创建 InputGetterImpl
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>e: 输入表达式</li>
   *   <li>physType: 物理类型</li>
   * </ul>
   */
  public InputGetterImpl(Expression e, PhysType physType) {
    this(ImmutableMap.of(e, physType));
  }

  /**
   * 使用输入映射创建 InputGetterImpl
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>inputs: 输入映射</li>
   * </ul>
   */
  public InputGetterImpl(Map<Expression, PhysType> inputs) {
    this.inputs = ImmutableMap.copyOf(inputs);
  }

  /**
   * 将条目列表转换为映射
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>entries: 条目列表</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>映射</li>
   * </ul>
   */
  private static <K, V> Map<K, V> mapOf(
      Iterable<? extends Map.Entry<K, V>> entries) {
    ImmutableMap.Builder<K, V> b = ImmutableMap.builder();
    Pair.forEach(entries, b::put);
    return b.build();
  }

  /**
   * 获取字段表达式
   *
   * <p>通过遍历所有输入找到指定索引的字段。</p>
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>list: 代码块构建器</li>
   *   <li>index: 字段索引</li>
   *   <li>storageType: 存储类型</li>
   * </ul>
   *
   * <p><b>返回值:</b></p>
   * <ul>
   *   <li>字段表达式</li>
   * </ul>
   *
   * <p><b>异常:</b></p>
   * <ul>
   *   <li>如果找不到字段,抛出 IllegalArgumentException</li>
   * </ul>
   */
  @Override public Expression field(BlockBuilder list, int index, @Nullable Type storageType) {
    int offset = 0;
    // 遍历所有输入
    for (Map.Entry<Expression, PhysType> input : inputs.entrySet()) {
      final PhysType physType = input.getValue();
      int fieldCount = physType.getRowType().getFieldCount();
      // 如果索引在当前输入的范围内
      if (index >= offset + fieldCount) {
        offset += fieldCount;
        continue;
      }
      // 获取字段引用
      final Expression left = list.append("current", input.getKey());
      return physType.fieldReference(left, index - offset, storageType);
    }
    throw new IllegalArgumentException("Unable to find field #" + index);
  }
}

/**
 * 转换 {@code RexNode} 的结果
 *
 * <p>这个类封装了 REX 表达式转换的结果,包含值变量和空值变量。</p>
 *
 * <p><b>成员变量:</b></p>
 * <ul>
 *   <li>isNullVariable: 空值检查变量</li>
 *   <li>valueVariable: 值变量</li>
 * </ul>
 *
 * <p><b>用途:</b></p>
 * <ul>
 *   <li>传递转换结果</li>
 *   <li>支持空值检查</li>
 *   <li>优化代码生成</li>
 * </ul>
 */
public static class Result {
  /**
   * 空值检查变量,布尔类型,表示值是否为 null
   */
  final ParameterExpression isNullVariable;
  /**
   * 值变量,存储实际的值
   */
  final ParameterExpression valueVariable;

  /**
   * 创建 Result 对象
   *
   * <p><b>参数:</b></p>
   * <ul>
   *   <li>isNullVariable: 空值检查变量</li>
   *   <li>valueVariable: 值变量</li>
   * </ul>
   */
  public Result(ParameterExpression isNullVariable,
      ParameterExpression valueVariable) {
    this.isNullVariable = isNullVariable;
    this.valueVariable = valueVariable;
  }
}
}
