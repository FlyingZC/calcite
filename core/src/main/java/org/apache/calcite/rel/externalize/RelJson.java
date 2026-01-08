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
package org.apache.calcite.rel.externalize;

import org.apache.calcite.avatica.AvaticaUtils;
import org.apache.calcite.avatica.util.ByteString;
import org.apache.calcite.avatica.util.TimeUnit;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelCollationImpl;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelDistribution;
import org.apache.calcite.rel.RelDistributions;
import org.apache.calcite.rel.RelFieldCollation;
import org.apache.calcite.rel.RelFieldCollation.Direction;
import org.apache.calcite.rel.RelFieldCollation.NullDirection;
import org.apache.calcite.rel.RelInput;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.CorrelationId;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexCorrelVariable;
import org.apache.calcite.rex.RexDynamicParam;
import org.apache.calcite.rex.RexFieldAccess;
import org.apache.calcite.rex.RexFieldCollation;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexOver;
import org.apache.calcite.rex.RexSlot;
import org.apache.calcite.rex.RexWindow;
import org.apache.calcite.rex.RexWindowBound;
import org.apache.calcite.rex.RexWindowBounds;
import org.apache.calcite.rex.RexWindowExclusion;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.SqlFunction;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlIntervalQualifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.SqlSyntax;
import org.apache.calcite.sql.fun.SqlLibrary;
import org.apache.calcite.sql.fun.SqlLibraryOperatorTableFactory;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlNameMatchers;
import org.apache.calcite.util.DateString;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.ImmutableIntList;
import org.apache.calcite.util.JsonBuilder;
import org.apache.calcite.util.NlsString;
import org.apache.calcite.util.RangeSets;
import org.apache.calcite.util.Sarg;
import org.apache.calcite.util.TimeString;
import org.apache.calcite.util.TimestampString;
import org.apache.calcite.util.Util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.apache.calcite.rel.RelDistributions.EMPTY;
import static org.apache.calcite.util.Static.RESOURCE;

import static java.util.Objects.requireNonNull;

/**
 * Utilities for converting {@link org.apache.calcite.rel.RelNode}
 * into JSON format.
 *
 * RelJson类是Apache Calcite中用于将关系表达式(RelNode)与JSON格式进行双向转换的核心工具类
 * 它提供了两个主要功能：
 * 1. 将RelNode及其相关对象(如RexNode、RelDataType等)序列化为JSON格式
 * 2. 从JSON格式反序列化重建RelNode及其相关对象
 *
 * 这个类在Calcite中主要用于：
 * - 关系表达式的持久化存储
 * - 跨进程传输关系表达式
 * - 调试和可视化关系表达式树
 * - 测试中验证关系表达式的正确性
 *
 * 核心设计思想：
 * - 使用反射机制动态创建RelNode实例，通过构造器注入RelInput参数
 * - 支持所有Calcite核心类型的JSON序列化和反序列化
 * - 提供可扩展的输入转换器(InputTranslator)接口
 * - 支持自定义操作符表(SqlOperatorTable)
 *
 * 主要成员：
 * - OBJECT_MAPPER: Jackson的ObjectMapper实例，用于JSON处理
 * - constructorMap: 缓存类型名到构造器的映射，提高性能
 * - jsonBuilder: 用于构建JSON对象的工具
 * - inputTranslator: 将输入引用转换为RexNode的转换器
 * - operatorTable: SQL操作符表，用于查找操作符
 */
public class RelJson {
  // Jackson的ObjectMapper实例，配置为使用BigDecimal处理浮点数，避免精度丢失
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

  // 支持的值类型列表，这些类型可以在JSON中直接序列化和反序列化
  // NlsString: 国际化字符串，包含字符集信息
  // BigDecimal: 高精度十进制数
  // ByteString: 二进制数据
  // Boolean: 布尔值
  // TimestampString: 时间戳字符串
  // DateString: 日期字符串
  // TimeString: 时间字符串
  private static final List<Class> VALUE_CLASSES =
      ImmutableList.of(NlsString.class, BigDecimal.class, ByteString.class,
      Boolean.class, TimestampString.class, DateString.class, TimeString.class);

  // 构造器缓存映射表：键为类型名(如"LogicalProject")，值为对应的构造器对象
  // 这个缓存避免了每次创建RelNode时都要通过反射查找构造器，显著提高性能
  private final Map<String, Constructor> constructorMap = new HashMap<>();
  // JSON构建器，用于创建Map和List等JSON数据结构，可为null
  private final @Nullable JsonBuilder jsonBuilder;
  // 输入转换器：负责将JSON中的输入引用转换为RexNode表达式
  // 这是一个函数式接口，允许自定义输入引用的转换逻辑
  private final InputTranslator inputTranslator;
  // SQL操作符表：用于查找和获取SQL操作符(如+、-、*、/、AND、OR等)
  // 支持标准SQL操作符和自定义操作符
  private final SqlOperatorTable operatorTable;

  // 支持的包名列表，用于将短类型名(如"LogicalProject")转换为完整类名
  // 当反序列化时，如果类型名不包含包名，会依次在这些包中查找对应的类
  // org.apache.calcite.rel.: 基础关系表达式包
  // org.apache.calcite.rel.core.: 核心关系表达式包
  // org.apache.calcite.rel.logical.: 逻辑关系表达式包
  // org.apache.calcite.adapter.jdbc.: JDBC适配器包
  // org.apache.calcite.adapter.jdbc.JdbcRules$: JDBC规则包(包含内部类)
  public static final List<String> PACKAGES =
      ImmutableList.of(
          "org.apache.calcite.rel.",
          "org.apache.calcite.rel.core.",
          "org.apache.calcite.rel.logical.",
          "org.apache.calcite.adapter.jdbc.",
          "org.apache.calcite.adapter.jdbc.JdbcRules$");

  /** Private constructor. 私有构造函数，通过工厂方法创建实例
   * @param jsonBuilder JSON构建器，可为null
   * @param inputTranslator 输入转换器，不能为null，用于将输入引用转换为RexNode
   * @param operatorTable SQL操作符表，不能为null，用于查找操作符
   */
  private RelJson(@Nullable JsonBuilder jsonBuilder,
      InputTranslator inputTranslator, SqlOperatorTable operatorTable) {
    this.jsonBuilder = jsonBuilder;  // 保存JSON构建器引用
    this.inputTranslator = requireNonNull(inputTranslator, "inputTranslator");  // 确保inputTranslator非null
    this.operatorTable = requireNonNull(operatorTable, "operatorTable");  // 确保operatorTable非null
  }

  /** Creates a RelJson. 创建一个默认配置的RelJson实例
   * 使用默认的translateInput方法作为输入转换器
   * 使用标准SQL操作符表(SqlStdOperatorTable)
   *
   * @return 新的RelJson实例
   */
  public static RelJson create() {
    return new RelJson(null, RelJson::translateInput,
        SqlStdOperatorTable.instance());
  }

  /** Creates a RelJson. 创建一个RelJson实例(已废弃)
   *
   * @deprecated 请使用 {@link RelJson#create} 方法创建实例，
   * 如果需要jsonBuilder，再调用 {@link #withJsonBuilder} 方法设置
   * 这个构造函数将在2.0版本之前移除
   *
   * @param jsonBuilder JSON构建器，可为null
   */
  @Deprecated // to be removed before 2.0
  public RelJson(@Nullable JsonBuilder jsonBuilder) {
    this(jsonBuilder, RelJson::translateInput, SqlStdOperatorTable.instance());
  }

  /** Returns a RelJson with a given JsonBuilder. 返回一个设置了指定JsonBuilder的新RelJson实例
   * 这是一个流式API方法，支持链式调用
   * 如果传入的jsonBuilder与当前实例相同，则直接返回当前实例
   *
   * @param jsonBuilder 要设置的JSON构建器，不能为null
   * @return 新的RelJson实例(如果jsonBuilder不同)或当前实例
   */
  public RelJson withJsonBuilder(JsonBuilder jsonBuilder) {
    requireNonNull(jsonBuilder, "jsonBuilder");  // 确保jsonBuilder非null
    if (jsonBuilder == this.jsonBuilder) {  // 如果相同，无需创建新实例
      return this;
    }
    return new RelJson(jsonBuilder, inputTranslator, operatorTable);  // 创建新实例
  }

  /** Returns a RelJson with a given InputTranslator. 返回一个设置了指定InputTranslator的新RelJson实例
   * 这是一个流式API方法，支持链式调用
   * InputTranslator用于自定义输入引用到RexNode的转换逻辑
   *
   * @param inputTranslator 要设置的输入转换器
   * @return 新的RelJson实例(如果inputTranslator不同)或当前实例
   */
  public RelJson withInputTranslator(InputTranslator inputTranslator) {
    if (inputTranslator == this.inputTranslator) {  // 如果相同，无需创建新实例
      return this;
    }
    return new RelJson(jsonBuilder, inputTranslator, operatorTable);  // 创建新实例
  }

  /** Returns a RelJson with a given operator table. 返回一个设置了指定操作符表的新RelJson实例
   * 这是一个流式API方法，支持链式调用
   * 操作符表用于查找和获取SQL操作符
   *
   * @param operatorTable 要设置的SQL操作符表
   * @return 新的RelJson实例(如果operatorTable不同)或当前实例
   */
  public RelJson withOperatorTable(SqlOperatorTable operatorTable) {
    if (operatorTable == this.operatorTable) {  // 如果相同，无需创建新实例
      return this;
    }
    return new RelJson(jsonBuilder, inputTranslator, operatorTable);  // 创建新实例
  }

  /** Returns a RelJson with an operator table that consists of the standard
   * operators plus operators in all libraries. 返回一个包含标准操作符和所有库操作符的RelJson实例
   * 使用SqlLibraryOperatorTableFactory创建包含所有SQL库操作符的操作符表
   * 这会包含标准操作符以及特定SQL库(MYSQL, POSTGRES等)的扩展操作符
   *
   * @return 新的RelJson实例，包含完整的操作符表
   */
  public RelJson withLibraryOperatorTable() {
    return withOperatorTable(
        SqlLibraryOperatorTableFactory.INSTANCE.getOperatorTable(
            SqlLibrary.values()));
  }

  // 获取JSON构建器实例，如果为null则抛出异常
  // 这是一个私有辅助方法，确保在使用jsonBuilder之前它已经被正确初始化
  private JsonBuilder jsonBuilder() {
    return requireNonNull(jsonBuilder, "jsonBuilder");
  }

  @SuppressWarnings("unchecked")
  // 从Map中获取指定键的值，并进行非null检查
  // 这是一个泛型方法，可以返回任何类型的值
  // 如果键不存在或值为null，会抛出NullPointerException
  //
  // @param <T> 返回值的类型
  // @param map 要查询的Map
  // @param key 要查询的键
  // @return 键对应的值
  // @throws NullPointerException 如果键不存在或值为null
  private static <T extends Object> T get(Map<String, ? extends @Nullable Object> map,
      String key) {
    return (T) requireNonNull(map.get(key), () -> "entry for key " + key);
  }

  // 从Map中获取指定键的枚举值
  // 这是一个泛型方法，可以返回任何枚举类型的值
  // 首先获取字符串值，然后将其转换为枚举值
  //
  // @param <T> 枚举类型
  // @param clazz 枚举类的Class对象
  // @param map 要查询的Map
  // @param key 要查询的键
  // @return 键对应的枚举值
  // @throws NullPointerException 如果键不存在、值为null或无法转换为枚举值
  private static <T extends Enum<T>> T enumVal(Class<T> clazz, Map<String, Object> map,
      String key) {
    String textValue = get(map, key);  // 获取字符串值
    return requireNonNull(
        Util.enumVal(clazz, textValue),  // 转换为枚举值
        () -> "unable to find enum value " + textValue + " in class " + clazz);
  }

  // 从JSON Map创建RelNode实例
  // 这是反序列化的核心方法，通过类型名查找对应的构造器，然后创建实例
  //
  // 工作流程：
  // 1. 从Map中获取"type"字段，确定要创建的RelNode类型
  // 2. 根据类型名获取对应的构造器(使用缓存提高性能)
  // 3. 通过反射调用构造器，传入Map作为参数
  // 4. 返回创建的RelNode实例
  //
  // @param map 包含RelNode序列化信息的Map，必须包含"type"字段
  // @return 创建的RelNode实例
  // @throws RuntimeException 如果创建失败(实例化异常、类型转换异常等)
  public RelNode create(Map<String, Object> map) {
    String type = get(map, "type");  // 获取类型名，如"LogicalProject"
    Constructor constructor = getConstructor(type);  // 获取构造器
    try {
      return (RelNode) constructor.newInstance(map);  // 通过反射创建实例
    } catch (InstantiationException | ClassCastException | InvocationTargetException
        | IllegalAccessException e) {
      throw new RuntimeException(
          "while invoking constructor for type '" + type + "'", e);
    }
  }

  // 获取指定类型名的构造器
  // 使用缓存机制提高性能，避免重复通过反射查找构造器
  //
  // 工作流程：
  // 1. 首先从缓存中查找构造器
  // 2. 如果缓存中没有，通过typeNameToClass将类型名转换为Class对象
  // 3. 获取该类的RelInput参数构造器
  // 4. 将构造器放入缓存
  // 5. 返回构造器
  //
  // @param type 类型名，如"LogicalProject"或完整类名
  // @return 对应类的RelInput参数构造器
  // @throws RuntimeException 如果类不存在或没有RelInput参数构造器
  public Constructor getConstructor(String type) {
    Constructor constructor = constructorMap.get(type);  // 从缓存获取
    if (constructor == null) {  // 缓存未命中
      Class clazz = typeNameToClass(type);  // 类型名转Class
      try {
        //noinspection unchecked
        constructor = clazz.getConstructor(RelInput.class);  // 获取RelInput参数构造器
      } catch (NoSuchMethodException e) {
        throw new RuntimeException("class does not have required constructor, "
            + clazz + "(RelInput)");
      }
      constructorMap.put(type, constructor);  // 放入缓存
    }
    return constructor;
  }

  /**
   * Converts a type name to a class. E.g. {@code getClass("LogicalProject")}
   * returns {@link org.apache.calcite.rel.logical.LogicalProject}.class.
   * 将类型名转换为Class对象
   *
   * 支持两种格式：
   * 1. 短类型名(如"LogicalProject")：会在PACKAGES列表中的包中查找
   * 2. 完整类名(如"org.apache.calcite.rel.logical.LogicalProject")：直接加载
   *
   * 查找策略：
   * - 如果类型名不包含点号，遍历PACKAGES列表依次尝试加载
   * - 如果所有包都找不到，尝试作为完整类名加载
   * - 如果都失败，抛出异常
   *
   * @param type 类型名，可以是短名或完整类名
   * @return 对应的Class对象
   * @throws RuntimeException 如果找不到对应的类
   */
  public Class typeNameToClass(String type) {
    if (!type.contains(".")) {  // 短类型名
      for (String package_ : PACKAGES) {  // 遍历所有包
        try {
          return Class.forName(package_ + type);  // 尝试加载
        } catch (ClassNotFoundException e) {
          // ignore 继续尝试下一个包
        }
      }
    }
    try {
      return Class.forName(type);  // 尝试作为完整类名加载
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("unknown type " + type);
    }
  }

  /**
   * Inverse of {@link #typeNameToClass}.
   * 将Class对象转换为类型名(与typeNameToClass相反)
   *
   * 转换策略：
   * - 如果类的完整类名以PACKAGES中的某个包开头，返回短类型名
   * - 否则返回完整类名
   *
   * 只返回不包含内部类(无$)的短类型名
   *
   * @param class_ RelNode的Class对象
   * @return 类型名(短名或完整类名)
   */
  public String classToTypeName(Class<? extends RelNode> class_) {
    final String canonicalName = class_.getName();  // 获取完整类名
    for (String package_ : PACKAGES) {  // 遍历所有包
      if (canonicalName.startsWith(package_)) {  // 类名以包名开头
        String remaining = canonicalName.substring(package_.length());  // 提取剩余部分
        if (remaining.indexOf('.') < 0 && remaining.indexOf('$') < 0) {  // 不包含点号和$
          return remaining;  // 返回短类型名
        }
      }
    }
    return canonicalName;  // 返回完整类名
  }

  /** Default implementation of
   * {@link InputTranslator#translateInput(RelJson, int, Map, RelInput)}.
   * InputTranslator的默认实现，将输入引用转换为RexNode
   *
   * 工作流程：
   * 1. 检查是否为局部引用(包含"type"字段)
   * 2. 如果是局部引用，创建RexLocalRef
   * 3. 否则，遍历所有输入节点，查找对应的字段
   * 4. 创建RexInputRef
   *
   * 字段索引计算：
   * - input参数是全局字段索引
   * - 需要减去前面所有输入节点的字段数，找到当前输入节点中的字段索引
   *
   * @param relJson RelJson实例
   * @param input 输入字段的全局索引
   * @param map 包含输入引用信息的Map
   * @param relInput RelInput对象，包含输入节点信息
   * @return 对应的RexNode(RexInputRef或RexLocalRef)
   * @throws RuntimeException 如果输入字段索引超出范围
   */
  private static RexNode translateInput(RelJson relJson, int input,
      Map<String, @Nullable Object> map, RelInput relInput) {
    final RelOptCluster cluster = relInput.getCluster();  // 获取优化集群
    final RexBuilder rexBuilder = cluster.getRexBuilder();  // 获取Rex构建器

    // Check if it is a local ref. 检查是否为局部引用
    if (map.containsKey("type")) {  // 包含type字段，说明是局部引用
      final RelDataTypeFactory typeFactory = cluster.getTypeFactory();  // 获取类型工厂
      final RelDataType type = relJson.toType(typeFactory, get(map, "type"));  // 解析类型
      return rexBuilder.makeLocalRef(type, input);  // 创建RexLocalRef
    }
    int i = input;  // 当前字段索引
    final List<RelNode> relNodes = relInput.getInputs();  // 获取所有输入节点
    for (RelNode inputNode : relNodes) {  // 遍历输入节点
      final RelDataType rowType = inputNode.getRowType();  // 获取行类型
      if (i < rowType.getFieldCount()) {  // 字段索引在当前输入节点范围内
        final RelDataTypeField field = rowType.getFieldList().get(i);  // 获取字段
        return rexBuilder.makeInputRef(field.getType(), input);  // 创建RexInputRef
      }
      i -= rowType.getFieldCount();  // 减去当前输入节点的字段数，继续查找
    }
    throw new RuntimeException("input field " + input + " is out of range");  // 超出范围
  }

  // 将RelCollationImpl转换为JSON格式
  // RelCollation表示排序规则，包含多个字段的排序信息
  //
  // JSON格式：
  // [
  //   {"field": 0, "direction": "ASCENDING", "nulls": "FIRST"},
  //   {"field": 1, "direction": "DESCENDING", "nulls": "LAST"}
  // ]
  //
  // @param node RelCollationImpl对象
  // @return JSON格式的列表，每个元素是一个字段的排序信息
  public Object toJson(RelCollationImpl node) {
    final List<Object> list = new ArrayList<>();  // 创建结果列表
    for (RelFieldCollation fieldCollation : node.getFieldCollations()) {  // 遍历所有字段排序
      final Map<String, @Nullable Object> map = jsonBuilder().map();  // 创建Map
      map.put("field", fieldCollation.getFieldIndex());  // 字段索引
      map.put("direction", fieldCollation.getDirection().name());  // 排序方向
      map.put("nulls", fieldCollation.nullDirection.name());  // 空值排序方向
      list.add(map);  // 添加到列表
    }
    return list;
  }

  // 从JSON格式创建RelCollation对象
  // 这是toJson(RelCollationImpl)的反向操作
  //
  // @param jsonFieldCollations JSON格式的字段排序信息列表
  // * @return RelCollation对象
  public RelCollation toCollation(
      List<Map<String, Object>> jsonFieldCollations) {
    final List<RelFieldCollation> fieldCollations = new ArrayList<>();  // 创建字段排序列表
    for (Map<String, Object> map : jsonFieldCollations) {  // 遍历JSON列表
      fieldCollations.add(toFieldCollation(map));  // 转换每个字段排序
    }
    return RelCollations.of(fieldCollations);  // 创建RelCollation
  }

  // 从JSON格式创建RelFieldCollation对象
  // RelFieldCollation表示单个字段的排序规则
  //
  // @param map 包含字段排序信息的Map
  // * @return RelFieldCollation对象
  public RelFieldCollation toFieldCollation(Map<String, Object> map) {
    final Integer field = get(map, "field");  // 字段索引
    final RelFieldCollation.Direction direction =
        enumVal(RelFieldCollation.Direction.class,
            map, "direction");  // 排序方向(ASCENDING/DESCENDING)
    final RelFieldCollation.NullDirection nullDirection =
        enumVal(RelFieldCollation.NullDirection.class,
            map, "nulls");  // 空值排序方向(FIRST/LAST)
    return new RelFieldCollation(field, direction, nullDirection);  // 创建RelFieldCollation
  }

  // 从JSON格式创建RelDistribution对象
  // RelDistribution表示数据分布方式(如HASH、RANGE、BROADCAST等)
  //
  // * @param map 包含分布信息的Map
  // * @return RelDistribution对象
  public RelDistribution toDistribution(Map<String, Object> map) {
    final RelDistribution.Type type =
        enumVal(RelDistribution.Type.class,
            map, "type");  // 分布类型

    ImmutableIntList list = EMPTY;  // 默认为空列表
    List<Integer> keys = (List<Integer>) map.get("keys");  // 获取分布键
    if (keys != null) {  // 如果有分布键
      list = ImmutableIntList.copyOf(keys);  // 转换为不可变列表
    }
    return RelDistributions.of(type, list);  // 创建RelDistribution
  }

  // 将RelDistribution转换为JSON格式
  // 这是toDistribution的反向操作
  //
  // JSON格式：
  // {
  //   "type": "HASH",
  //   "keys": [0, 1]
  // * }
  //
  // * @param relDistribution RelDistribution对象
  // * @return JSON格式的Map
  private Object toJson(RelDistribution relDistribution) {
    final Map<String, @Nullable Object> map = jsonBuilder().map();  // 创建Map
    map.put("type", relDistribution.getType().name());  // 分布类型
    if (!relDistribution.getKeys().isEmpty()) {  // 如果有分布键
      map.put("keys", relDistribution.getKeys());  // 添加分布键
    }
    return map;
  }

  // 从JSON格式创建RelDataType对象
  // RelDataType表示SQL数据类型，支持多种格式：
  // * 1. List格式：表示结构类型(Record)，包含多个字段
  // * 2. Map格式：表示具体类型，包含类型信息和可空性
  // * 3. String格式：表示简单类型名(如"INTEGER"、"VARCHAR")
  // *
  // * @param typeFactory 类型工厂，用于创建类型
  // * @param o JSON对象，可以是List、Map或String
  // * @return RelDataType对象
  public RelDataType toType(RelDataTypeFactory typeFactory, Object o) {
    if (o instanceof List) {  // 结构类型(Record)
      @SuppressWarnings("unchecked")
      final List<Map<String, Object>> jsonList = (List<Map<String, Object>>) o;  // 字段列表
      final RelDataTypeFactory.Builder builder = typeFactory.builder();  // 创建构建器
      for (Map<String, Object> jsonMap : jsonList) {  // 遍历字段
        builder.add(get(jsonMap, "name"), toType(typeFactory, jsonMap));  // 添加字段
      }
      return builder.build();  // 构建结构类型
    } else if (o instanceof Map) {  // 具体类型
      @SuppressWarnings("unchecked")
      final Map<String, Object> map = (Map<String, Object>) o;  // 类型信息Map
      final RelDataType type = getRelDataType(typeFactory, map);  // 获取基础类型
      final boolean nullable = get(map, "nullable");  // 获取可空性
      return typeFactory.createTypeWithNullability(type, nullable);  // 设置可空性
    } else {  // 简单类型名
      final SqlTypeName sqlTypeName =
          requireNonNull(Util.enumVal(SqlTypeName.class, (String) o),
              () -> "unable to find enum value " + o
                  + " in class " + SqlTypeName.class);  // 转换为SqlTypeName
      return typeFactory.createSqlType(sqlTypeName);  // 创建SQL类型
    }
  }

  // 从JSON Map获取RelDataType(基础类型，不含可空性)
  // 处理各种复杂的SQL类型，包括：
  // * - INTERVAL类型：时间间隔类型
  // * - ARRAY类型：数组类型
  // * - MAP类型：键值对类型
  // * - MULTISET类型：多重集类型
  // * - 基本类型：带精度和标度的类型
  // *
  // * @param typeFactory 类型工厂
  // * @param map 包含类型信息的Map
  // * @return RelDataType对象
  private RelDataType getRelDataType(RelDataTypeFactory typeFactory, Map<String, Object> map) {
    final Object fields = map.get("fields");  // 检查是否为嵌套结构
    if (fields != null) {
      // Nested struct 嵌套结构类型
      return toType(typeFactory, fields);  // 递归处理
    }
    final SqlTypeName sqlTypeName =
        enumVal(SqlTypeName.class, map, "type");  // 获取SQL类型名
    final Object component;
    final RelDataType componentType;
    switch (sqlTypeName) {
    case INTERVAL_YEAR:  // 时间间隔类型处理
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
      TimeUnit startUnit = sqlTypeName.getStartUnit();  // 起始时间单位
      TimeUnit endUnit = sqlTypeName.getEndUnit();  // 结束时间单位
      return typeFactory.createSqlIntervalType(
          new SqlIntervalQualifier(startUnit, endUnit, SqlParserPos.ZERO));  // 创建间隔类型

    case ARRAY:  // 数组类型
      component = requireNonNull(map.get("component"), "component");  // 元素类型
      componentType = toType(typeFactory, component);  // 递归解析元素类型
      return typeFactory.createArrayType(componentType, -1);  // 创建数组类型

    case MAP:  // MAP类型
      Object key = get(map, "key");  // 键类型
      Object value = get(map, "value");  // 值类型
      RelDataType keyType = toType(typeFactory, key);  // 解析键类型
      RelDataType valueType = toType(typeFactory, value);  // 解析值类型
      return typeFactory.createMapType(keyType, valueType);  // 创建MAP类型

    case MULTISET:  // 多重集类型
      component = requireNonNull(map.get("component"), "component");  // 元素类型
      componentType = toType(typeFactory, component);  // 递归解析元素类型
      return typeFactory.createMultisetType(componentType, -1);  // 创建多重集类型

    default:  // 基本类型
      final Integer precision = (Integer) map.get("precision");  // 精度
      final Integer scale = (Integer) map.get("scale");  // 标度
      if (precision == null) {  // 无精度
        return typeFactory.createSqlType(sqlTypeName);  // 创建基本类型
      } else if (scale == null) {  // 有精度无标度
        return typeFactory.createSqlType(sqlTypeName, precision);  // 创建带精度类型
      } else {  // 有精度和标度
        return typeFactory.createSqlType(sqlTypeName, precision, scale);  // 创建带精度和标度类型
      }
    }
  }

  // 将AggregateCall转换为JSON格式
  // AggregateCall表示聚合函数调用(如SUM、COUNT、AVG等)
  //
  // JSON格式：
  // {
  //   "agg": {"name": "SUM", "kind": "SUM", "syntax": "FUNCTION"},
  //   "type": {"type": "INTEGER", "nullable": false},
  //   "distinct": false,
  //   "operands": [0],
  //   "name": "SUM($0)"
  // * }
  // *
  // * @param node AggregateCall对象
  // * @return JSON格式的Map
  public Object toJson(AggregateCall node) {
    final Map<String, @Nullable Object> map = jsonBuilder().map();  // 创建结果Map
    final Map<String, @Nullable Object> aggMap = toJson(node.getAggregation());  // 转换聚合函数
    if (node.getAggregation().getFunctionType().isUserDefined()) {  // 如果是用户自定义函数
      aggMap.put("class", node.getAggregation().getClass().getName());  // 添加类名
    }
    map.put("agg", aggMap);  // 聚合函数信息
    map.put("type", toJson(node.getType()));  // 返回类型
    map.put("distinct", node.isDistinct());  // 是否去重
    map.put("operands", node.getArgList());  // 参数列表
    map.put("name", node.getName());  // 聚合函数名称
    return map;
  }

  // 将任意对象转换为JSON格式(通用方法)
  // 这是一个重载方法，支持多种类型的转换
  // *
  // * 支持的类型：
  // * - 基本类型：null、Number、String、Boolean(直接返回)
  // * - RexNode相关：RexNode、RexWindow、RexFieldCollation、RexWindowBound
  // * - RelNode相关：AggregateCall、RelCollationImpl、RelDataType、RelDataTypeField、RelDistribution
  // * - 集合类型：List、Set、ImmutableBitSet
  // * - 其他：CorrelationId、Sarg、RangeSet、Range、ByteString、UUID
  // *
  // * @param value 要转换的对象
  // * @return JSON格式的对象
  // * @throws UnsupportedOperationException 如果类型不支持序列化
  public @Nullable Object toJson(@Nullable Object value) {
    if (value == null  // 基本类型直接返回
        || value instanceof Number
        || value instanceof String
        || value instanceof Boolean) {
      return value;
    } else if (value instanceof RexNode) {  // RexNode表达式
      return toJson((RexNode) value);
    } else if (value instanceof RexWindow) {  // 窗口函数
      return toJson((RexWindow) value);
    } else if (value instanceof RexFieldCollation) {  // 字段排序
      return toJson((RexFieldCollation) value);
    } else if (value instanceof RexWindowBound) {  // 窗口边界
      return toJson((RexWindowBound) value);
    } else if (value instanceof CorrelationId) {  // 相关ID
      return toJson((CorrelationId) value);
    } else if (value instanceof List || value instanceof Set) {  // 集合类型
      final List<@Nullable Object> list = jsonBuilder().list();  // 创建列表
      for (Object o : (Collection<?>) value) {  // 递归转换每个元素
        list.add(toJson(o));
      }
      return list;
    } else if (value instanceof ImmutableBitSet) {  // 不可变位集
      final List<@Nullable Object> list = jsonBuilder().list();  // 创建列表
      for (Integer integer : (ImmutableBitSet) value) {  // 转换每个整数
        list.add(toJson(integer));
      }
      return list;
    } else if (value instanceof AggregateCall) {  // 聚合调用
      return toJson((AggregateCall) value);
    } else if (value instanceof RelCollationImpl) {  // 排序规则
      return toJson((RelCollationImpl) value);
    } else if (value instanceof RelDataType) {  // 数据类型
      return toJson((RelDataType) value);
    } else if (value instanceof RelDataTypeField) {  // 数据类型字段
      return toJson((RelDataTypeField) value);
    } else if (value instanceof RelDistribution) {  // 数据分布
      return toJson((RelDistribution) value);
    } else if (value instanceof Sarg) {  // 搜索参数
      //noinspection unchecked,rawtypes
      return toJson((Sarg) value);
    } else if (value instanceof RangeSet) {  // 范围集合
      //noinspection unchecked,rawtypes
      return toJson((RangeSet) value);
    } else if (value instanceof Range) {  // 范围
      //noinspection rawtypes,unchecked
      return toJson((Range) value);
    } else if (value instanceof ByteString) {  // 字节数组
      return toJson(((ByteString) value).toString(16));  // 转为16进制字符串
    } else if (value instanceof UUID) {  // UUID
      return toJson(value.toString());  // 转为字符串
    } else {
      throw new UnsupportedOperationException("type not serializable as JSON: "
          + value + " (type " + value.getClass().getCanonicalName() + ")");
    }
  }

  // 将Sarg(搜索参数)转换为JSON格式
  // Sarg表示一个搜索条件，包含范围集合和空值处理方式
  // *
  // * JSON格式：
  // * {
  // *   "rangeSet": [["[", 0, 5, "]"], ["[", 10, "-", ")"]],
  // *   "nullAs": "UNKNOWN"
  // * }
  // *
  // * @param <C> 可比较的类型
  // * @param node Sarg对象
  // * @return JSON格式的Map
  public <C extends Comparable<C>> Object toJson(Sarg<C> node) {
    final Map<String, @Nullable Object> map = jsonBuilder().map();  // 创建Map
    map.put("rangeSet", toJson(node.rangeSet));  // 范围集合
    map.put("nullAs", RelEnumTypes.fromEnum(node.nullAs));  // 空值处理方式
    return map;
  }

  // 将RangeSet转换为JSON格式
  // * RangeSet表示一组不相交的范围
  // *
  // * JSON格式：
  // * [
  // *   ["closed", 0, 5],
  // *   ["atLeast", 10]
  // * ]
  // *
  // * @param <C> 可比较的类型
  // * @param rangeSet RangeSet对象
  // * @return JSON格式的列表，每个元素是一个范围
  public <C extends Comparable<C>> List<List<String>> toJson(
      RangeSet<C> rangeSet) {
    final List<List<String>> list = new ArrayList<>();  // 创建结果列表
    try {
      RangeSets.forEach(rangeSet,  // 遍历每个范围
          RangeToJsonConverter.<C>instance().andThen(list::add));  // 转换并添加
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize RangeSet: ", e);
    }
    return list;
  }

  /** Serializes a {@link Range} that can be deserialized using
   * {@link RelJson#rangeFromJson(List, RelDataType)}.
   * 将Range(范围)转换为JSON格式
   *
   * 支持的范围类型：
  * - all: 所有值
  * - atLeast: 大于等于
  * - atMost: 小于等于
  * - greaterThan: 大于
  * - lessThan: 小于
  * - singleton: 单个值
  * - closed: 闭区间
  * - closedOpen: 左闭右开
  * - openClosed: 左开右闭
  * - open: 开区间
  *
  * @param <C> 可比较的类型
  * @param range Range对象
  * @return JSON格式的列表
  */
  public <C extends Comparable<C>> List<String> toJson(Range<C> range) {
    return RangeSets.map(range, RangeToJsonConverter.instance());
  }

  // 将RelDataType转换为JSON格式
 // * RelDataType表示SQL数据类型
 // *
 // * 结构类型JSON格式：
 // * {
 // *   "fields": [
 // *     {"name": "id", "type": "INTEGER", "nullable": false},
 // *     {"name": "name", "type": "VARCHAR", "nullable": true}
 // *   ],
 // *   "nullable": false
 // * }
 // *
 // * 简单类型JSON格式：
 // * {
 // *   "type": "INTEGER",
 // *   "nullable": false,
 // *   "precision": 10,
 // *   "scale": 2
 // * }
 // *
 // * @param node RelDataType对象
 // * @return JSON格式的Map
  private Object toJson(RelDataType node) {
    final Map<String, @Nullable Object> map = jsonBuilder().map();  // 创建Map
    if (node.isStruct()) {  // 结构类型(Record)
      final List<@Nullable Object> list = jsonBuilder().list();  // 创建字段列表
      for (RelDataTypeField field : node.getFieldList()) {  // 遍历字段
        list.add(toJson(field));  // 递归转换字段
      }
      map.put("fields", list);  // 添加字段列表
      map.put("nullable", node.isNullable());  // 添加可空性
    } else {  // 简单类型
      map.put("type", node.getSqlTypeName().name());  // 类型名
      map.put("nullable", node.isNullable());  // 可空性
      if (node.getComponentType() != null) {  // 数组/多重集的元素类型
        map.put("component", toJson(node.getComponentType()));
      }
      RelDataType keyType = node.getKeyType();  // MAP的键类型
      if (keyType != null) {
        map.put("key", toJson(keyType));
      }
      RelDataType valueType = node.getValueType();  // MAP的值类型
      if (valueType != null) {
        map.put("value", toJson(valueType));
      }
      if (node.getSqlTypeName().allowsPrec()) {  // 如果支持精度
        map.put("precision", node.getPrecision());
      }
      if (node.getSqlTypeName().allowsScale()) {  // 如果支持标度
        map.put("scale", node.getScale());
      }
    }
    return map;
  }

  // 将RelDataTypeField转换为JSON格式
  // RelDataTypeField表示结构类型中的一个字段
  // *
  // * JSON格式：
  // * {
  // *   "name": "id",
  // *   "type": "INTEGER",
  // *   "nullable": false
  // * }
  // *
  // * @param node RelDataTypeField对象
  // * @return JSON格式的Map
  private Object toJson(RelDataTypeField node) {
    Map<String, Object> map = (Map<String, Object>) toJson(node.getType());  // 转换类型
    map.put("name", node.getName());  // 添加字段名
    return map;
  }

  // 将CorrelationId转换为JSON格式
  // CorrelationId表示相关ID，用于关联子查询
  // *
  // * JSON格式：
  // * "correlationId"
  // *
  // * @param node CorrelationId对象
  // * @return JSON格式的字符串
  private static Object toJson(CorrelationId node) {
    return node.getId();
  }

  // 将RexNode转换为JSON格式
  // RexNode表示行表达式(Row Expression)，是Calcite中表达式的抽象
  // *
  // * 支持的RexNode类型：
  // * - DYNAMIC_PARAM: 动态参数
  // * - FIELD_ACCESS: 字段访问
  // * - LITERAL: 字面量
  // * - INPUT_REF: 输入引用
  // * - LOCAL_REF: 局部引用
  // * - CORREL_VARIABLE: 相关变量
  // * - RexCall: 函数调用(包括窗口函数)
  // *
  // * @param node RexNode对象
  // * @return JSON格式的Map
  // * @throws UnsupportedOperationException 如果RexNode类型不支持
  public Object toJson(RexNode node) {
    final Map<String, @Nullable Object> map;
    switch (node.getKind()) {
    case DYNAMIC_PARAM:  // 动态参数(如?)
      map = jsonBuilder().map();
      final RexDynamicParam rexDynamicParam = (RexDynamicParam) node;
      final RelDataType rdpType = rexDynamicParam.getType();
      map.put("dynamicParam", rexDynamicParam.getIndex());  // 参数索引
      map.put("type", toJson(rdpType));  // 参数类型
      return map;
    case FIELD_ACCESS:  // 字段访问(如row.field)
      map = jsonBuilder().map();
      final RexFieldAccess fieldAccess = (RexFieldAccess) node;
      map.put("field", fieldAccess.getField().getName());  // 字段名
      map.put("expr", toJson(fieldAccess.getReferenceExpr()));  // 引用的表达式
      return map;
    case LITERAL:  // 字面量(如1、'abc'、NULL)
      final RexLiteral literal = (RexLiteral) node;
      final Object value = literal.getValue3();
      map = jsonBuilder().map();
      //noinspection rawtypes
      map.put("literal",  // 字面量值
          value instanceof Enum
              ? RelEnumTypes.fromEnum((Enum) value)  // 枚举类型转换
              : toJson(value));  // 普通值转换
      map.put("type", toJson(node.getType()));  // 字面量类型
      return map;
    case INPUT_REF:  // 输入引用(如$0、$1)
      map = jsonBuilder().map();
      map.put("input", ((RexSlot) node).getIndex());  // 字段索引
      map.put("name", ((RexSlot) node).getName());  // 字段名
      return map;
    case LOCAL_REF:  // 局部引用(用于局部变量)
      map = jsonBuilder().map();
      map.put("input", ((RexSlot) node).getIndex());  // 索引
      map.put("name", ((RexSlot) node).getName());  // 名称
      map.put("type", toJson(node.getType()));  // 类型
      return map;
    case CORREL_VARIABLE:  // 相关变量(用于关联子查询)
      map = jsonBuilder().map();
      map.put("correl", ((RexCorrelVariable) node).getName());  // 相关变量名
      map.put("type", toJson(node.getType()));  // 类型
      return map;
    default:
      if (node instanceof RexCall) {  // 函数调用(包括算术、逻辑、比较、窗口函数等)
        final RexCall call = (RexCall) node;
        map = jsonBuilder().map();
        map.put("op", toJson(call.getOperator()));  // 操作符
        final List<@Nullable Object> list = jsonBuilder().list();  // 创建参数列表
        for (RexNode operand : call.getOperands()) {  // 遍历所有参数
          list.add(toJson(operand));  // 递归转换参数
        }
        map.put("operands", list);  // 添加参数列表
        switch (node.getKind()) {
        case MINUS:  // 减法需要返回类型
        case CAST:  // 类型转换需要返回类型
        case SAFE_CAST:  // 安全类型转换需要返回类型
          map.put("type", toJson(node.getType()));
          break;
        default:
          break;
        }
        if (call.getOperator() instanceof SqlFunction) {  // 如果是SQL函数
          if (((SqlFunction) call.getOperator()).getFunctionType().isUserDefined()) {  // 用户自定义函数
            SqlOperator op = call.getOperator();
            map.put("class", op.getClass().getName());  // 函数类名
            map.put("type", toJson(node.getType()));  // 返回类型
            map.put("deterministic", op.isDeterministic());  // 是否确定性
            map.put("dynamic", op.isDynamicFunction());  // 是否动态函数
          }
        }
        if (call instanceof RexOver) {  // 如果是窗口函数
          RexOver over = (RexOver) call;
          map.put("distinct", over.isDistinct());  // 是否去重
          map.put("type", toJson(node.getType()));  // 返回类型
          map.put("window", toJson(over.getWindow()));  // 窗口定义
        }
        return map;
      }
      throw new UnsupportedOperationException("unknown rex " + node);  // 不支持的类型
    }
  }

  // 将RexWindow转换为JSON格式
  // RexWindow表示窗口函数的窗口定义
  // *
  // * JSON格式示例：
  // * {
  // *   "partition": [{"input": 0, "name": "dept"}],
  // *   "order": [{"expr": {"input": 1, "name": "salary"}, "direction": "DESCENDING", "null-direction": "LAST"}],
  // *   "rows-lower": {"type": "UNBOUNDED_PRECEDING"},
  // *   "rows-upper": {"type": "CURRENT_ROW"}
  // * }
  // *
  // * @param window RexWindow对象
  // * @return JSON格式的Map
  private Object toJson(RexWindow window) {
    final Map<String, @Nullable Object> map = jsonBuilder().map();  // 创建Map
    if (!window.partitionKeys.isEmpty()) {  // 如果有分区键
      map.put("partition", toJson(window.partitionKeys));  // 添加分区键
    }
    if (!window.orderKeys.isEmpty()) {  // 如果有排序键
      map.put("order", toJson(window.orderKeys));  // 添加排序键
    }
    if (window.getLowerBound() == null) {  // 没有窗口边界
      // No ROWS or RANGE clause
    } else if (window.getUpperBound() == null) {  // 只有下边界
      if (window.isRows()) {  // ROWS模式
        map.put("rows-lower", toJson(window.getLowerBound()));  // 添加ROWS下边界
      } else {  // RANGE模式
        map.put("range-lower", toJson(window.getLowerBound()));  // 添加RANGE下边界
      }
    } else {  // 上下边界都有
      if (window.isRows()) {  // ROWS模式
        map.put("rows-lower", toJson(window.getLowerBound()));  // 添加ROWS下边界
        map.put("rows-upper", toJson(window.getUpperBound()));  // 添加ROWS上边界
      } else {  // RANGE模式
        map.put("range-lower", toJson(window.getLowerBound()));  // 添加RANGE下边界
        map.put("range-upper", toJson(window.getUpperBound()));  // 添加RANGE上边界
      }
    }
    return map;
  }

  // 将RexFieldCollation转换为JSON格式
  // RexFieldCollation表示窗口函数中的字段排序
  // *
  // * JSON格式：
  // * {
  // *   "expr": {"input": 1, "name": "salary"},
  // *   "direction": "DESCENDING",
  // *   "null-direction": "LAST"
  // * }
  // *
  // * @param collation RexFieldCollation对象
  // * @return JSON格式的Map
  private Object toJson(RexFieldCollation collation) {
    final Map<String, @Nullable Object> map = jsonBuilder().map();  // 创建Map
    map.put("expr", toJson(collation.left));  // 排序表达式
    map.put("direction", collation.getDirection().name());  // 排序方向
    map.put("null-direction", collation.getNullDirection().name());  // 空值排序方向
    return map;
  }

  // 将RexWindowBound转换为JSON格式
  // RexWindowBound表示窗口边界
  // *
  // * 支持的边界类型：
  // * - CURRENT_ROW: 当前行
  // * - UNBOUNDED_PRECEDING: 无界前行(窗口开始)
  // * - UNBOUNDED_FOLLOWING: 无界后行(窗口结束)
  // * - PRECEDING: 前行N行
  // * - FOLLOWING: 后行N行
  // *
  // * JSON格式示例：
  // * {"type": "CURRENT_ROW"}
  // * {"type": "UNBOUNDED_PRECEDING"}
  // * {"type": "PRECEDING", "offset": {"literal": 1, "type": {"type": "INTEGER"}}}
  // *
  // * @param windowBound RexWindowBound对象
  // * @return JSON格式的Map
  private Object toJson(RexWindowBound windowBound) {
    final Map<String, @Nullable Object> map = jsonBuilder().map();  // 创建Map
    if (windowBound.isCurrentRow()) {  // 当前行
      map.put("type", "CURRENT_ROW");
    } else if (windowBound.isUnbounded()) {  // 无界边界
      map.put("type", windowBound.isPreceding() ? "UNBOUNDED_PRECEDING" : "UNBOUNDED_FOLLOWING");
    } else {  // 有偏移量的边界
      map.put("type", windowBound.isPreceding() ? "PRECEDING" : "FOLLOWING");  // 前行或后行
      RexNode offset =
          requireNonNull(windowBound.getOffset(),  // 获取偏移量
              () -> "getOffset for window bound " + windowBound);
      map.put("offset", toJson(offset));  // 添加偏移量
    }
    return map;
  }

  /**
   * Translates a JSON expression into a RexNode.
   * 将JSON表达式转换为RexNode(公共方法)
   *
   * 这是一个便捷方法，内部创建RelInputForCluster并调用重载的toRex方法
   *
   * @param cluster 优化集群，包含RexBuilder和类型工厂等
   * @param o JSON对象，可以是Map、List或基本类型
   * @return 转换后的RexNode
   */
  public RexNode toRex(RelOptCluster cluster, Object o) {
    RelInput input = new RelInputForCluster(cluster);  // 创建RelInput
    return toRex(input, o);  // 调用重载方法
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  // 将JSON对象转换为RexNode(核心方法)
  // 这是toRex(RelOptCluster, Object)的实际实现
  // *
  // * 支持的JSON格式：
  // * 1. null: 返回null
  // * 2. Map: 表示复杂表达式(函数调用、输入引用、字段访问等)
  // * 3. Boolean/Number/String: 转换为对应的字面量
  // *
  // * Map格式支持的表达式类型：
  // * - "op": 函数调用(包含操作符和参数)
  // * - "input": 输入引用
  // * - "field": 字段访问
  // * - "correl": 相关变量
  // * - "literal": 字面量
  // * - "dynamicParam": 动态参数
  // *
  // * @param relInput RelInput对象，包含集群和输入信息
  // * @param o JSON对象
  // * @return 转换后的RexNode
  @PolyNull RexNode toRex(RelInput relInput, @PolyNull Object o) {
    final RelOptCluster cluster = relInput.getCluster();  // 获取优化集群
    final RexBuilder rexBuilder = cluster.getRexBuilder();  // 获取Rex构建器
    if (o == null) {  // null值
      return null;
    // Support JSON deserializing of non-default Map classes such as gson LinkedHashMap
    // 支持非默认Map类(如gson的LinkedHashMap)
    } else if (Map.class.isAssignableFrom(o.getClass())) {  // Map类型(复杂表达式)
      final Map<String, @Nullable Object> map = (Map) o;  // 转换为Map
      final RelDataTypeFactory typeFactory = cluster.getTypeFactory();  // 获取类型工厂
      if (map.containsKey("op")) {  // 函数调用(包含"op"字段)
        final Map<String, @Nullable Object> opMap = get(map, "op");  // 获取操作符信息
        if (map.containsKey("class")) {  // 如果有类名(用户自定义函数)
          opMap.put("class", get(map, "class"));  // 添加类名
        }
        final List operands = get(map, "operands");  // 获取参数列表
        final List<RexNode> rexOperands = toRexList(relInput, operands);  // 递归转换参数
        final Object jsonType = map.get("type");  // 获取返回类型
        final Map window = (Map) map.get("window");  // 获取窗口信息
        if (window != null) {  // 如果有窗口信息(窗口函数)
          final SqlAggFunction operator = requireNonNull(toAggregation(opMap), "operator");  // 转换聚合函数
          final RelDataType type = toType(typeFactory, requireNonNull(jsonType, "jsonType"));  // 转换返回类型
          List<RexNode> partitionKeys = new ArrayList<>();  // 分区键列表
          Object partition = window.get("partition");  // 获取分区键
          if (partition != null) {  // 如果有分区键
            partitionKeys = toRexList(relInput, (List) partition);  // 递归转换分区键
          }
          List<RexFieldCollation> orderKeys = new ArrayList<>();  // 排序键列表
          if (window.containsKey("order")) {  // 如果有排序键
            addRexFieldCollationList(orderKeys, relInput, (List) window.get("order"));  // 转换排序键
          }
          final RexWindowBound lowerBound;  // 窗口下边界
          final RexWindowBound upperBound;  // 窗口上边界
          final boolean physical;  // 是否为物理窗口(ROWS)
          if (window.get("rows-lower") != null) {  // ROWS模式
            lowerBound = toRexWindowBound(relInput, (Map) window.get("rows-lower"));  // 转换下边界
            upperBound = toRexWindowBound(relInput, (Map) window.get("rows-upper"));  // 转换上边界
            physical = true;  // 物理窗口
          } else if (window.get("range-lower") != null) {  // RANGE模式
            lowerBound = toRexWindowBound(relInput, (Map) window.get("range-lower"));  // 转换下边界
            upperBound = toRexWindowBound(relInput, (Map) window.get("range-upper"));  // 转换上边界
            physical = false;  // 逻辑窗口
          } else {
            // No ROWS or RANGE clause 没有ROWS或RANGE子句
            // Note: lower and upper bounds are non-nullable, so this branch is not reachable
            lowerBound = null;
            upperBound = null;
            physical = false;
          }
          final RexWindowExclusion exclude;  // 窗口排除规则
          if (window.get("exclude") != null) {  // 如果有排除规则
            exclude = toRexWindowExclusion((Map) window.get("exclude"));  // 转换排除规则
          } else {
            exclude = RexWindowExclusion.EXCLUDE_NO_OTHER;  // 默认不排除
          }
          final boolean distinct = get((Map<String, Object>) map, "distinct");  // 是否去重
          return rexBuilder.makeOver(type, operator, rexOperands, partitionKeys,  // 创建窗口函数
              ImmutableList.copyOf(orderKeys),
              requireNonNull(lowerBound, "lowerBound"),
              requireNonNull(upperBound, "upperBound"),
              requireNonNull(exclude, "exclude"),
              physical,
              true, false, distinct, false);
        } else {  // 普通函数调用(非窗口函数)
          final SqlOperator operator = requireNonNull(toOp(opMap), "operator");  // 转换操作符
          final RelDataType type;  // 返回类型
          if (jsonType != null) {  // 如果指定了返回类型
            type = toType(typeFactory, jsonType);  // 转换类型
          } else {  // 否则推导返回类型
            type = rexBuilder.deriveReturnType(operator, rexOperands);
          }
          return rexBuilder.makeCall(type, operator, rexOperands);  // 创建函数调用
        }
      }
      final Integer input = (Integer) map.get("input");  // 获取输入字段索引
      if (input != null) {  // 如果是输入引用
        return inputTranslator.translateInput(this, input, map, relInput);  // 使用转换器转换
      }
      final String field = (String) map.get("field");  // 获取字段名
      if (field != null) {  // 如果是字段访问
        final Object jsonExpr = get(map, "expr");  // 获取引用的表达式
        final RexNode expr = toRex(relInput, jsonExpr);  // 递归转换表达式
        return rexBuilder.makeFieldAccess(expr, field, true);  // 创建字段访问
      }
      final String correl = (String) map.get("correl");  // 获取相关变量名
      if (correl != null) {  // 如果是相关变量
        final Object jsonType = map.get("type");  // 获取类型
        RelDataType type = toType(typeFactory, jsonType);  // 转换类型
        return rexBuilder.makeCorrel(type, new CorrelationId(correl));  // 创建相关变量
      }
      if (map.containsKey("literal")) {  // 如果是字面量
        Object literal = map.get("literal");  // 获取字面量值
        if (literal == null) {  // NULL值
          final RelDataType type = toType(typeFactory, get(map, "type"));  // 获取类型
          return rexBuilder.makeNullLiteral(type);  // 创建NULL字面量
        }
        if (!map.containsKey("type")) {  // 如果没有指定类型(向后兼容)
          // In previous versions, type was not specified for all literals.
          // To keep backwards compatibility, if type is not specified
          // we just interpret the literal
          return toRex(relInput, literal);  // 直接解释字面量
        }
        final RelDataType type = toType(typeFactory, get(map, "type"));  // 获取类型
        if (literal instanceof Map  // 如果是Sarg(搜索参数)
            && ((Map<?, ?>) literal).containsKey("rangeSet")) {
          Sarg sarg = sargFromJson((Map) literal, type);  // 转换Sarg
          return rexBuilder.makeSearchArgumentLiteral(sarg, type);  // 创建Sarg字面量
        }
        SqlTypeName sqlTypeName = type.getSqlTypeName();  // 获取SQL类型名
        if (sqlTypeName == SqlTypeName.SYMBOL) {  // 符号类型(枚举)
          literal = RelEnumTypes.toEnum((String) literal);  // 转换为枚举
        } else if (sqlTypeName == SqlTypeName.TIMESTAMP  // 时间戳类型
            || sqlTypeName == SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE) {
          if (literal instanceof Integer) {  // 整数转长整型
            literal = ((Integer) literal).longValue();
          }
        } else if (sqlTypeName == SqlTypeName.BINARY || sqlTypeName == SqlTypeName.VARBINARY) {  // 二进制类型
          literal = ByteString.of((String) literal, 16);  // 16进制字符串转字节数组
        } else if (sqlTypeName == SqlTypeName.UUID) {  // UUID类型
          literal = UUID.fromString((String) literal);  // 字符串转UUID
        }
        return rexBuilder.makeLiteral(literal, type);  // 创建字面量
      }
      if (map.containsKey("sargLiteral")) {  // 如果是Sarg字面量(另一种格式)
        Object sargObject = map.get("sargLiteral");  // 获取Sarg对象
        if (sargObject == null) {  // NULL值
          final RelDataType type = toType(typeFactory, get(map, "type"));  // 获取类型
          return rexBuilder.makeNullLiteral(type);  // 创建NULL字面量
        }
        final RelDataType type = toType(typeFactory, get(map, "type"));  // 获取类型
        Sarg sarg = sargFromJson((Map) sargObject, type);  // 转换Sarg
        return rexBuilder.makeSearchArgumentLiteral(sarg, type);  // 创建Sarg字面量
      }
      if (map.containsKey("dynamicParam")) {  // 如果是动态参数
        final Object dynamicParamObject = requireNonNull(map.get("dynamicParam"));  // 获取参数索引
        final Integer index = (Integer) dynamicParamObject;  // 转换为整数
        final RelDataType type = toType(typeFactory, get(map, "type"));  // 获取类型
        return rexBuilder.makeDynamicParam(type, index);  // 创建动态参数
      }
      throw new UnsupportedOperationException("cannot convert to rex " + o);  // 不支持的类型
    } else if (o instanceof Boolean) {  // 布尔值
      return rexBuilder.makeLiteral((Boolean) o);  // 创建布尔字面量
    } else if (o instanceof String) {  // 字符串
      return rexBuilder.makeLiteral((String) o);  // 创建字符串字面量
    } else if (o instanceof Number) {  // 数字
      final Number number = (Number) o;  // 转换为Number
      if (number instanceof Double || number instanceof Float) {  // 浮点数
        return rexBuilder.makeApproxLiteral(  // 创建近似字面量
            BigDecimal.valueOf(number.doubleValue()));
      } else {  // 整数
        return rexBuilder.makeExactLiteral(  // 创建精确字面量
            BigDecimal.valueOf(number.longValue()));
      }
    } else {
      throw new UnsupportedOperationException("cannot convert to rex " + o);  // 不支持的类型
    }
  }

  @Deprecated
  /** Converts a JSON object to a {@code Sarg}.
   * 将JSON对象转换为Sarg(搜索参数)(已废弃)
   *
   * <p>For example,
   * {@code {rangeSet: [["[", 0, 5, "]"], ["[", 10, "-", ")"]],
   * nullAs: "UNKNOWN"}} represents the range x &ge; 0 and x &le; 5 or
   * x &gt; 10.
   *
   * 示例：
   * {rangeSet: [["closed", 0, 5], ["atLeast", 10]], nullAs: "UNKNOWN"}
   * 表示范围：0 <= x <= 5 或 x >= 10
   *
   * @param <C> 可比较的类型
   * @param map 包含Sarg信息的Map
   * @return Sarg对象
   */
  public static <C extends Comparable<C>> Sarg<C> sargFromJson(
      Map<String, Object> map) {
    final String nullAs = requireNonNull((String) map.get("nullAs"), "nullAs");  // 空值处理方式
    final List<List<String>> rangeSet =
        requireNonNull((List<List<String>>) map.get("rangeSet"), "rangeSet");  // 范围集合
    return Sarg.of(RelEnumTypes.toEnum(nullAs),  // 转换空值处理方式
        RelJson.<C>rangeSetFromJson(rangeSet));  // 转换范围集合
  }

  // 将JSON对象转换为Sarg(搜索参数，带类型信息)
  // *
  // * @param <C> 可比较的类型
  // * @param map 包含Sarg信息的Map
  // * @param type 值的数据类型
  // * @return Sarg对象
  public static <C extends Comparable<C>> Sarg<C> sargFromJson(
      Map<String, Object> map, RelDataType type) {
    final String nullAs = requireNonNull((String) map.get("nullAs"), "nullAs");  // 空值处理方式
    final List<List<String>> rangeSet =
        requireNonNull((List<List<String>>) map.get("rangeSet"), "rangeSet");  // 范围集合
    return Sarg.of(RelEnumTypes.toEnum(nullAs),  // 转换空值处理方式
        RelJson.<C>rangeSetFromJson(rangeSet, type));  // 转换范围集合(带类型)
  }

  @Deprecated
  /** Converts a JSON list to a {@link RangeSet}. 将JSON列表转换为RangeSet(已废弃) */
  public static <C extends Comparable<C>> RangeSet<C> rangeSetFromJson(
      List<List<String>> rangeSetsJson) {
    final ImmutableRangeSet.Builder<C> builder = ImmutableRangeSet.builder();  // 创建构建器
    try {
      rangeSetsJson.forEach(list -> builder.add(rangeFromJson(list)));  // 转换每个范围
    } catch (Exception e) {
      throw new RuntimeException("Error creating RangeSet from JSON: ", e);  // 转换失败
    }
    return builder.build();  // 构建RangeSet
  }

  /** Converts a JSON list to a {@link RangeSet} with supplied value typing.
   * 将JSON列表转换为RangeSet(带类型信息)
   *
  * @param <C> 可比较的类型
  * @param rangeSetsJson JSON格式的范围列表
  * @param type 值的数据类型
  * @return RangeSet对象
  */
  public static <C extends Comparable<C>> RangeSet<C> rangeSetFromJson(
      List<List<String>> rangeSetsJson, RelDataType type) {
    final ImmutableRangeSet.Builder<C> builder = ImmutableRangeSet.builder();  // 创建构建器
    try {
      rangeSetsJson.forEach(list -> builder.add(rangeFromJson(list, type)));  // 转换每个范围(带类型)
    } catch (Exception e) {
      throw new RuntimeException("Error creating RangeSet from JSON: ", e);  // 转换失败
    }
    return builder.build();  // 构建RangeSet
  }

  @Deprecated
  /** Creates a {@link Range} from a JSON object.
   * 从JSON对象创建Range(范围)(已废弃)
   *
   * <p>The JSON object is as serialized using {@link RelJson#toJson(Range)},
   * e.g. {@code ["[", ")", 10, "-"]}.
   *
   * 支持的范围类型：
   * - "all": 所有值
   * - "atLeast": 大于等于某个值
   * - "atMost": 小于等于某个值
   * - "greaterThan": 大于某个值
   * - "lessThan": 小于某个值
   * - "singleton": 单个值
   * - "closed": 闭区间
   * - "closedOpen": 左闭右开
   * - "openClosed": 左开右闭
   * - "open": 开区间
   *
   * @see RangeToJsonConverter
   *
   * @param <C> 可比较的类型
  * @param list JSON格式的范围列表
  * @return Range对象
  */
  public static <C extends Comparable<C>> Range<C> rangeFromJson(
      List<String> list) {
    switch (list.get(0)) {  // 根据范围类型创建Range
    case "all":
      return Range.all();  // 所有值
    case "atLeast":
      return Range.atLeast(rangeEndPointFromJson(list.get(1)));  // 大于等于
    case "atMost":
      return Range.atMost(rangeEndPointFromJson(list.get(1)));  // 小于等于
    case "greaterThan":
      return Range.greaterThan(rangeEndPointFromJson(list.get(1)));  // 大于
    case "lessThan":
      return Range.lessThan(rangeEndPointFromJson(list.get(1)));  // 小于
    case "singleton":
      return Range.singleton(rangeEndPointFromJson(list.get(1)));  // 单个值
    case "closed":
      return Range.closed(rangeEndPointFromJson(list.get(1)),  // 闭区间
          rangeEndPointFromJson(list.get(2)));
    case "closedOpen":
      return Range.closedOpen(rangeEndPointFromJson(list.get(1)),  // 左闭右开
          rangeEndPointFromJson(list.get(2)));
    case "openClosed":
      return Range.openClosed(rangeEndPointFromJson(list.get(1)),  // 左开右闭
          rangeEndPointFromJson(list.get(2)));
    case "open":
      return Range.open(rangeEndPointFromJson(list.get(1)),  // 开区间
          rangeEndPointFromJson(list.get(2)));
    default:
      throw new AssertionError("unknown range type " + list.get(0));  // 未知类型
    }
  }

  /** Creates a {@link Range} from a JSON object.
   * 从JSON对象创建Range(范围，带类型信息)
   *
   * <p>The JSON object is as serialized using {@link RelJson#toJson(Range)},
   * e.g. {@code ["[", ")", 10, "-"]}.
   *
  * @see RangeToJsonConverter
  *
  * @param <C> 可比较的类型
  * @param list JSON格式的范围列表
  * @param type 值的数据类型
  * @return Range对象
  */
  public static <C extends Comparable<C>> Range<C> rangeFromJson(
      List<String> list, RelDataType type) {
    switch (list.get(0)) {  // 根据范围类型创建Range
    case "all":
      return Range.all();  // 所有值
    case "atLeast":
      return Range.atLeast(rangeEndPointFromJson(list.get(1), type));  // 大于等于(带类型)
    case "atMost":
      return Range.atMost(rangeEndPointFromJson(list.get(1), type));  // 小于等于(带类型)
    case "greaterThan":
      return Range.greaterThan(rangeEndPointFromJson(list.get(1), type));  // 大于(带类型)
    case "lessThan":
      return Range.lessThan(rangeEndPointFromJson(list.get(1), type));  // 小于(带类型)
    case "singleton":
      return Range.singleton(rangeEndPointFromJson(list.get(1), type));  // 单个值(带类型)
    case "closed":
      return Range.closed(rangeEndPointFromJson(list.get(1), type),  // 闭区间(带类型)
          rangeEndPointFromJson(list.get(2), type));
    case "closedOpen":
      return Range.closedOpen(rangeEndPointFromJson(list.get(1)),  // 左闭右开(带类型)
          rangeEndPointFromJson(list.get(2), type));
    case "openClosed":
      return Range.openClosed(rangeEndPointFromJson(list.get(1)),  // 左开右闭(带类型)
          rangeEndPointFromJson(list.get(2), type));
    case "open":
      return Range.open(rangeEndPointFromJson(list.get(1), type),  // 开区间(带类型)
          rangeEndPointFromJson(list.get(2), type));
    default:
      throw new AssertionError("unknown range type " + list.get(0));  // 未知类型
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Deprecated
  // 从JSON字符串反序列化范围端点值(已废弃)
  // 尝试使用VALUE_CLASSES中的所有类型进行反序列化，直到成功
  // *
  // * @param <C> 可比较的类型
  // * @param o JSON字符串
  // * @return 反序列化后的值
  // * @throws RuntimeException 如果所有类型都失败
  private static <C extends Comparable<C>> C rangeEndPointFromJson(Object o) {
    Exception e = null;
    for (Class clsType : VALUE_CLASSES) {  // 遍历所有支持的值类型
      try {
        return (C) OBJECT_MAPPER.readValue((String) o, clsType);  // 尝试反序列化
      } catch (JsonProcessingException ex) {
        e = ex;  // 保存异常，继续尝试下一个类型
      }
    }
    throw new RuntimeException(  // 所有类型都失败
        "Error deserializing range endpoint (did not find compatible type): ",
        e);
  }

  // 从JSON字符串反序列化范围端点值(带类型信息)
  // 根据指定的RelDataType确定值的类型，然后进行反序列化
  // *
  // * @param <C> 可比较的类型
  // * @param o JSON字符串
  // * @param type 值的数据类型
  // * @return 反序列化后的值
  // * @throws RuntimeException 如果反序列化失败
  private static <C extends Comparable<C>> C rangeEndPointFromJson(Object o, RelDataType type) {
    Exception e;
    try {
      Class clsType = determineRangeEndpointValueClass(type);  // 确定值的类型
      return (C) OBJECT_MAPPER.readValue((String) o, clsType);  // 反序列化
    } catch (JsonProcessingException ex) {
      e = ex;
    }
    throw new RuntimeException(  // 反序列化失败
        "Error deserializing range endpoint (did not find compatible type): ",
        e);
  }

  // 根据RelDataType确定范围端点值的Java类型
  // *
  // * 支持的类型映射：
  // * - DECIMAL -> BigDecimal
  // * - DOUBLE -> Double
  // * - CHAR -> NlsString
  // * - BOOLEAN -> Boolean
  // * - TIMESTAMP -> TimestampString
  // * - DATE -> DateString
  // * - TIME -> TimeString
  // *
  // * @param type RelDataType对象
  // * @return 对应的Java Class对象
  // * @throws RuntimeException 如果类型不支持
  private static Class determineRangeEndpointValueClass(RelDataType type) {
    SqlTypeName typeName = RexLiteral.strictTypeName(type);  // 获取严格的SQL类型名
    switch (typeName) {
    case DECIMAL:
      return BigDecimal.class;  // 十进制数
    case DOUBLE:
      return Double.class;  // 双精度浮点数
    case CHAR:
      return NlsString.class;  // 国际化字符串
    case BOOLEAN:
      return Boolean.class;  // 布尔值
    case TIMESTAMP:
      return TimestampString.class;  // 时间戳字符串
    case DATE:
      return DateString.class;  // 日期字符串
    case TIME:
      return TimeString.class;  // 时间字符串
    default:
      throw new RuntimeException(  // 不支持的类型
          "Error deserializing range endpoint (did not find compatible type)");
    }
  }

  // 将JSON格式的字段排序列表转换为RexFieldCollation列表
  // *
  // * @param list 结果列表，用于存放转换后的RexFieldCollation
  // * @param relInput RelInput对象
  // * @param order JSON格式的字段排序列表
  private void addRexFieldCollationList(List<RexFieldCollation> list,
      RelInput relInput, @Nullable List<Map<String, Object>> order) {
    if (order == null) {  // 如果为null，直接返回
      return;
    }

    for (Map<String, Object> o : order) {  // 遍历每个字段排序
      RexNode expr = requireNonNull(toRex(relInput, o.get("expr")), "expr");  // 转换表达式
      Set<SqlKind> directions = new HashSet<>();  // 创建方向集合
      if (Direction.valueOf(get(o, "direction")) == Direction.DESCENDING) {  // 降序
        directions.add(SqlKind.DESCENDING);  // 添加降序标记
      }
      if (NullDirection.valueOf(get(o, "null-direction")) == NullDirection.FIRST) {  // 空值在前
        directions.add(SqlKind.NULLS_FIRST);  // 添加空值在前标记
      } else {  // 空值在后
        directions.add(SqlKind.NULLS_LAST);  // 添加空值在后标记
      }
      list.add(new RexFieldCollation(expr, directions));  // 创建RexFieldCollation并添加
    }
  }

  // 将JSON格式转换为RexWindowExclusion(窗口排除规则)
  // *
  // * 支持的排除规则：
  // * - "CURRENT_ROW": 排除当前行
  // * - "GROUP": 排除当前组
  // * - "TIES": 排除相同值的行
  // * - "NO OTHERS": 不排除其他行
  // *
  // * @param map JSON格式的排除规则
  // * @return RexWindowExclusion对象，如果map为null则返回null
  private static @Nullable RexWindowExclusion toRexWindowExclusion(
      @Nullable Map<String, Object> map) {
    if (map == null) {  // 如果为null，返回null
      return null;
    }
    final String type = get(map, "type");  // 获取排除规则类型
    switch (type) {
    case "CURRENT_ROW":
      return RexWindowExclusion.EXCLUDE_CURRENT_ROW;  // 排除当前行
    case "GROUP":
      return RexWindowExclusion.EXCLUDE_GROUP;  // 排除当前组
    case "TIES":
      return RexWindowExclusion.EXCLUDE_TIES;  // 排除相同值的行
    case "NO OTHERS":
      return RexWindowExclusion.EXCLUDE_NO_OTHER;  // 不排除其他行
    default:
      throw new UnsupportedOperationException(  // 不支持的类型
          "cannot convert " + type + " to rex window exclusion");
    }
  }
  // 将JSON格式转换为RexWindowBound(窗口边界)
  // *
  // * 支持的边界类型：
  // * - "CURRENT_ROW": 当前行
  // * - "UNBOUNDED_PRECEDING": 无界前行
  // * - "UNBOUNDED_FOLLOWING": 无界后行
  // * - "PRECEDING": 前行N行
  // * - "FOLLOWING": 后行N行
  // *
  // * @param input RelInput对象
  // * @param map JSON格式的窗口边界
  // * @return RexWindowBound对象，如果map为null则返回null
  private @Nullable RexWindowBound toRexWindowBound(RelInput input,
      @Nullable Map<String, Object> map) {
    if (map == null) {  // 如果为null，返回null
      return null;
    }

    final String type = get(map, "type");  // 获取边界类型
    switch (type) {
    case "CURRENT_ROW":
      return RexWindowBounds.CURRENT_ROW;  // 当前行
    case "UNBOUNDED_PRECEDING":
      return RexWindowBounds.UNBOUNDED_PRECEDING;  // 无界前行
    case "UNBOUNDED_FOLLOWING":
      return RexWindowBounds.UNBOUNDED_FOLLOWING;  // 无界后行
    case "PRECEDING":
      return RexWindowBounds.preceding(toRex(input, get(map, "offset")));  // 前行N行
    case "FOLLOWING":
      return RexWindowBounds.following(toRex(input, get(map, "offset")));  // 后行N行
    default:
      throw new UnsupportedOperationException("cannot convert " + type + " to rex window bound");  // 不支持的类型
    }
  }

  // 将JSON列表转换为RexNode列表
  // *
  // * @param relInput RelInput对象
  // * @param operands JSON格式的操作数列表
  // * @return 转换后的RexNode列表
  private List<RexNode> toRexList(RelInput relInput, List operands) {
    final List<RexNode> list = new ArrayList<>();  // 创建结果列表
    for (Object operand : operands) {  // 遍历每个操作数
      list.add(toRex(relInput, operand));  // 递归转换
    }
    return list;
  }

  // 将JSON格式转换为SqlOperator
  // *
  // * 查找策略：
  // * 1. 通过名称、类型和语法在操作符表中查找
  // * 2. 如果找不到，尝试通过类名实例化(用户自定义操作符)
  // * 3. 如果都失败，抛出异常
  // *
  // * @param map 包含操作符信息的Map
  // * @return SqlOperator对象，如果找不到则返回null
  @Nullable SqlOperator toOp(Map<String, ? extends @Nullable Object> map) {
    // in case different operator has the same kind, check with both name and kind.
    // 为了区分不同操作符可能有相同的kind，同时检查name和kind
    String name = get(map, "name");  // 操作符名称
    String kind = get(map, "kind");  // 操作符类型
    String syntax = get(map, "syntax");  // 操作符语法
    SqlKind sqlKind = SqlKind.valueOf(kind);  // 转换为SqlKind
    SqlSyntax sqlSyntax = SqlSyntax.valueOf(syntax);  // 转换为SqlSyntax
    List<SqlOperator> operators = new ArrayList<>();  // 创建操作符列表
    operatorTable.lookupOperatorOverloads(  // 查找操作符重载
        new SqlIdentifier(name, SqlParserPos.ZERO),  // 操作符标识符
        null,  // 参数类型列表
        sqlSyntax,  // 操作符语法
        operators,  // 结果列表
        SqlNameMatchers.liberal());  // 名称匹配器
    for (SqlOperator operator : operators) {  // 遍历找到的操作符
      if (operator.kind == sqlKind) {  // 检查类型是否匹配
        return operator;  // 返回匹配的操作符
      }
    }
    String class_ = (String) map.get("class");  // 获取类名
    if (class_ != null) {  // 如果有类名(用户自定义操作符)
      return AvaticaUtils.instantiatePlugin(SqlOperator.class, class_);  // 实例化
    }
    throw RESOURCE.noOperator(name, kind, syntax).ex();  // 找不到操作符
  }

  // 将JSON格式转换为SqlAggFunction(聚合函数)
  // *
  // * @param map 包含聚合函数信息的Map
  // * @return SqlAggFunction对象
  @Nullable SqlAggFunction toAggregation(Map<String, ? extends @Nullable Object> map) {
    return (SqlAggFunction) toOp(map);  // 聚合函数也是操作符的一种
  }

  // 将SqlOperator转换为JSON格式
  // *
  // * JSON格式：
  // * {
  // *   "name": "+",
  // *   "kind": "PLUS",
  // *   "syntax": "BINARY"
  // * }
  // *
  // * @param operator SqlOperator对象
  // * @return JSON格式的Map
  private Map<String, @Nullable Object> toJson(SqlOperator operator) {
    // User-defined operators are not yet handled. 用户自定义操作符暂未处理
    Map<String, @Nullable Object> map = jsonBuilder().map();  // 创建Map
    map.put("name", operator.getName());  // 操作符名称
    map.put("kind", operator.kind.toString());  // 操作符类型
    map.put("syntax", operator.getSyntax().toString());  // 操作符语法
    return map;
  }

  /**
   * Translates a JSON expression into a RexNode,
   * using a given {@link InputTranslator} to transform JSON objects that
   * represent input references into RexNodes.
   * 将JSON表达式转换为RexNode，使用指定的InputTranslator转换输入引用(已废弃)
   *
   * @param cluster 优化集群
   * @param translator 输入转换器
   * @param o JSON对象
   * @return 转换后的RexNode
   *
   * @deprecated 请使用 {@link #toRex(RelOptCluster, Object)} 方法
   */
  @Deprecated // to be removed before 2.0
  public static RexNode readExpression(RelOptCluster cluster,
      InputTranslator translator, Map<String, Object> o) {
    RelInput relInput = new RelInputForCluster(cluster);  // 创建RelInput
    return new RelJson(null, translator, SqlStdOperatorTable.instance()).toRex(relInput, o);  // 转换
  }

  /**
   * Special context from which a relational expression can be initialized,
   * reading from a serialized form of the relational expression.
   * 特殊的RelInput实现，用于从序列化形式初始化关系表达式
   *
   * <p>Contains only a cluster and an empty list of inputs;
   * most methods throw {@link UnsupportedOperationException}.
   * 只包含集群和空的输入列表，大多数方法抛出UnsupportedOperationException
   *
   * 这个类是一个最小化的RelInput实现，只支持getCluster()和getInputs()方法
   * 用于在反序列化RexNode时提供必要的上下文信息
   */
  private static class RelInputForCluster implements RelInput {
    private final RelOptCluster cluster;  // 优化集群

    // 构造函数
    RelInputForCluster(RelOptCluster cluster) {
      this.cluster = cluster;  // 保存集群引用
    }
    // 获取集群(唯一支持的方法)
    @Override public RelOptCluster getCluster() {
      return cluster;
    }

    // 以下方法都不支持，抛出异常
    @Override public RelTraitSet getTraitSet() {
      throw new UnsupportedOperationException();
    }

    @Override public RelOptTable getTable(String table) {
      throw new UnsupportedOperationException();
    }

    @Override public RelNode getInput() {
      throw new UnsupportedOperationException();
    }

    // 返回空的输入列表
    @Override public List<RelNode> getInputs() {
      return ImmutableList.of();
    }

    @Override public @Nullable RexNode getExpression(String tag) {
      throw new UnsupportedOperationException();
    }

    @Override public ImmutableBitSet getBitSet(String tag) {
      throw new UnsupportedOperationException();
    }

    @Override public @Nullable List<ImmutableBitSet> getBitSetList(String tag) {
      throw new UnsupportedOperationException();
    }

    @Override public List<AggregateCall> getAggregateCalls(String tag) {
      throw new UnsupportedOperationException();
    }

    @Override public @Nullable Object get(String tag) {
      throw new UnsupportedOperationException();
    }

    @Override public @Nullable String getString(String tag) {
      throw new UnsupportedOperationException();
    }

    @Override public float getFloat(String tag) {
      throw new UnsupportedOperationException();
    }

    @Override public BigDecimal getBigDecimal(String tag) {
      throw new UnsupportedOperationException();
    }

    @Override public <E extends Enum<E>> @Nullable E getEnum(
        String tag, Class<E> enumClass) {
      throw new UnsupportedOperationException();
    }

    @Override public @Nullable List<RexNode> getExpressionList(String tag) {
      throw new UnsupportedOperationException();
    }

    @Override public @Nullable List<String> getStringList(String tag) {
      throw new UnsupportedOperationException();
    }

    @Override public @Nullable List<Integer> getIntegerList(String tag) {
      throw new UnsupportedOperationException();
    }

    @Override public @Nullable List<List<Integer>> getIntegerListList(String tag) {
      throw new UnsupportedOperationException();
    }

    @Override public RelDataType getRowType(String tag) {
      throw new UnsupportedOperationException();
    }

    @Override public RelDataType getRowType(String expressionsTag, String fieldsTag) {
      throw new UnsupportedOperationException();
    }

    @Override public RelCollation getCollation() {
      throw new UnsupportedOperationException();
    }

    @Override public RelDistribution getDistribution() {
      throw new UnsupportedOperationException();
    }

    @Override public ImmutableList<ImmutableList<RexLiteral>> getTuples(String tag) {
      throw new UnsupportedOperationException();
    }

    @Override public boolean getBoolean(String tag, boolean default_) {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Translates a JSON object that represents an input reference into a RexNode.
   * 输入转换器接口，用于将JSON格式的输入引用转换为RexNode
   *
   * 这是一个函数式接口，允许自定义输入引用的转换逻辑
   * 默认实现是translateInput静态方法
   */
  @FunctionalInterface
  public interface InputTranslator {
    /**
     * Transforms an input reference into a RexNode.
     * 将输入引用转换为RexNode
     *
     * @param relJson RelJson实例
     * @param input 输入字段的序号(全局索引)
     * @param map 表示输入引用的JSON对象
     * @param relInput 输入的描述(包含输入节点信息)
     * @return 表示输入引用的RexNode
     */
    RexNode translateInput(RelJson relJson, int input,
        Map<String, @Nullable Object> map, RelInput relInput);
  }

  /** Implementation of {@link RangeSets.Handler} that converts a {@link Range}
   * event to a list of strings.
   * RangeToJsonConverter是RangeSets.Handler的实现，将Range事件转换为字符串列表
   *
   * 这个类用于将Range对象序列化为JSON格式的字符串列表
   * 使用单例模式，所有方法都是线程安全的
   *
   * @param <V> Range值的类型
   */
  private static class RangeToJsonConverter<V>
      implements RangeSets.Handler<@NonNull V, List<String>> {
    @SuppressWarnings("rawtypes")
    private static final RangeToJsonConverter INSTANCE =
        new RangeToJsonConverter<>();  // 单例实例

    // 获取单例实例
    private static <C extends Comparable<C>> RangeToJsonConverter<C> instance() {
      //noinspection unchecked
      return INSTANCE;
    }

    // 所有值
    @Override public List<String> all() {
      return ImmutableList.of("all");
    }

    // 大于等于
    @Override public List<String> atLeast(@NonNull V lower) {
      return ImmutableList.of("atLeast", toJson(lower));
    }

    // 小于等于
    @Override public List<String> atMost(@NonNull V upper) {
      return ImmutableList.of("atMost", toJson(upper));
    }

    // 大于
    @Override public List<String> greaterThan(@NonNull V lower) {
      return ImmutableList.of("greaterThan", toJson(lower));
    }

    // 小于
    @Override public List<String> lessThan(@NonNull V upper) {
      return ImmutableList.of("lessThan", toJson(upper));
    }

    // 单个值
    @Override public List<String> singleton(@NonNull V value) {
      return ImmutableList.of("singleton", toJson(value));
    }

    // 闭区间
    @Override public List<String> closed(@NonNull V lower, @NonNull V upper) {
      return ImmutableList.of("closed", toJson(lower), toJson(upper));
    }

    // 左闭右开
    @Override public List<String> closedOpen(@NonNull V lower,
        @NonNull V upper) {
      return ImmutableList.of("closedOpen", toJson(lower), toJson(upper));
    }

    // 左开右闭
    @Override public List<String> openClosed(@NonNull V lower,
        @NonNull V upper) {
      return ImmutableList.of("openClosed", toJson(lower), toJson(upper));
    }

    // 开区间
    @Override public List<String> open(@NonNull V lower, @NonNull V upper) {
      return ImmutableList.of("open", toJson(lower), toJson(upper));
    }

    // 将对象转换为JSON字符串
    private static String toJson(Object o) {
      try {
        return OBJECT_MAPPER.writeValueAsString(o);  // 使用Jackson序列化
      } catch (JsonProcessingException e) {
        throw new RuntimeException("Failed to serialize Range endpoint: ", e);  // 序列化失败
      }
    }
  }
}
