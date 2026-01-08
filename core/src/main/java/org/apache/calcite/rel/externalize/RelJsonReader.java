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
package org.apache.calcite.rel.externalize; // 包声明：该类属于org.apache.calcite.rel.externalize包，用于关系表达式的外部化处理

import org.apache.calcite.plan.Convention; // 导入Convention类：用于表示关系表达式的调用约定
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类：关系表达式优化集群，包含类型工厂等共享资源
import org.apache.calcite.plan.RelOptSchema; // 导入RelOptSchema类：关系表达式优化模式，用于查找表
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable类：优化过程中的表抽象
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类：关系表达式的特征集合
import org.apache.calcite.rel.RelCollation; // 导入RelCollation类：表示排序规则
import org.apache.calcite.rel.RelCollations; // 导入RelCollations类：排序规则的工厂类
import org.apache.calcite.rel.RelDistribution; // 导入RelDistribution类：表示数据分布规则
import org.apache.calcite.rel.RelInput; // 导入RelInput接口：关系表达式输入接口，用于从JSON创建关系表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口：关系表达式的基础接口
import org.apache.calcite.rel.core.AggregateCall; // 导入AggregateCall类：聚合函数调用
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口：关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口：关系数据类型工厂
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类：行表达式的字面量
import org.apache.calcite.rex.RexNode; // 导入RexNode接口：行表达式节点
import org.apache.calcite.runtime.SqlFunctions; // 导入SqlFunctions类：SQL函数工具类
import org.apache.calcite.schema.Schema; // 导入Schema接口：Calcite的模式接口
import org.apache.calcite.sql.SqlAggFunction; // 导入SqlAggFunction接口：SQL聚合函数
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类：不可变的位集合，用于表示索引集合
import org.apache.calcite.util.Pair; // 导入Pair类：键值对
import org.apache.calcite.util.Util; // 导入Util类：通用工具类

import com.fasterxml.jackson.core.type.TypeReference; // 导入TypeReference类：Jackson的类型引用，用于泛型反序列化
import com.fasterxml.jackson.databind.DeserializationFeature; // 导入DeserializationFeature类：Jackson反序列化配置
import com.fasterxml.jackson.databind.ObjectMapper; // 导入ObjectMapper类：Jackson的核心对象映射器
import com.google.common.collect.ImmutableList; // 导入ImmutableList类：Google Guava的不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解：表示可以为null的值

import java.io.IOException; // 导入IOException类：IO异常
import java.lang.reflect.Constructor; // 导入Constructor类：反射构造函数
import java.lang.reflect.InvocationTargetException; // 导入InvocationTargetException类：反射调用异常
import java.math.BigDecimal; // 导入BigDecimal类：高精度十进制数
import java.util.AbstractList; // 导入AbstractList类：抽象列表
import java.util.ArrayList; // 导入ArrayList类：动态数组列表
import java.util.LinkedHashMap; // 导入LinkedHashMap类：保持插入顺序的哈希映射
import java.util.List; // 导入List接口：列表接口
import java.util.Locale; // 导入Locale类：地区设置
import java.util.Map; // 导入Map接口：映射接口
import java.util.function.UnaryOperator; // 导入UnaryOperator接口：一元操作符函数

import static java.util.Objects.requireNonNull; // 导入requireNonNull静态方法：用于检查对象不为null

/**
 * Reads a JSON plan and converts it back to a tree of relational expressions.
 * 读取JSON计划并将其转换回关系表达式树。该类是RelJson的逆操作，负责将JSON格式的
 * 关系表达式反序列化为Calcite的RelNode对象树。通常用于从持久化存储中恢复查询计划，
 * 或者在分布式系统中传输和重建查询计划。
 *
 * @see org.apache.calcite.rel.RelInput // 参见RelInput接口：定义了从输入创建关系表达式的方法
 */
public class RelJsonReader { // 类定义：RelJsonReader类，负责从JSON读取关系表达式
  private static final TypeReference<LinkedHashMap<String, Object>> TYPE_REF = // 静态常量：Jackson类型引用，用于反序列化为LinkedHashMap<String, Object>
      new TypeReference<LinkedHashMap<String, Object>>() { // 匿名内部类：创建LinkedHashMap<String, Object>的类型引用
      }; // 匿名类结束

  private final RelOptCluster cluster; // 成员变量：关系表达式优化集群，包含类型工厂等共享资源，所有关系表达式共享
  private final RelOptSchema relOptSchema; // 成员变量：关系表达式优化模式，用于查找表和模式信息
  private final RelJson relJson; // 成员变量：RelJson对象，负责处理JSON与Java对象之间的转换
  private final Map<String, RelNode> relMap = new LinkedHashMap<>(); // 成员变量：关系表达式映射表，用ID映射到RelNode对象，用于引用已创建的关系表达式
  private @Nullable RelNode lastRel; // 成员变量：最后创建的关系表达式，用于默认输入（当没有明确指定inputs时）

  public RelJsonReader(RelOptCluster cluster, RelOptSchema relOptSchema, // 构造方法：创建RelJsonReader实例，使用默认的RelJson转换器
      Schema schema) { // 参数：Schema对象（当前未使用，保留用于未来扩展）
    this(cluster, relOptSchema, schema, UnaryOperator.identity()); // 调用另一个构造方法，使用恒等函数作为RelJson转换器
  } // 构造方法结束

  public RelJsonReader(RelOptCluster cluster, RelOptSchema relOptSchema, // 构造方法：创建RelJsonReader实例，允许自定义RelJson转换器
      Schema schema, UnaryOperator<RelJson> relJsonTransform) { // 参数：Schema对象和RelJson转换函数，用于自定义JSON转换行为
    this.cluster = cluster; // 初始化：保存关系表达式优化集群
    this.relOptSchema = relOptSchema; // 初始化：保存关系表达式优化模式
    Util.discard(schema); // 操作：丢弃schema参数（当前未使用，保留用于未来扩展）
    relJson = relJsonTransform.apply(RelJson.create()); // 初始化：应用转换函数到RelJson对象，创建自定义的JSON转换器
  } // 构造方法结束

  public RelNode read(String s) throws IOException { // 方法：读取JSON字符串并转换为关系表达式树
    lastRel = null; // 初始化：清空最后的关系表达式引用
    final ObjectMapper mapper = new ObjectMapper(); // 创建：Jackson的ObjectMapper对象，用于JSON解析
    Map<String, Object> o = mapper // 反序列化：将JSON字符串解析为Map对象
        .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true) // 配置：使用BigDecimal处理浮点数以保持精度
        .readValue(s, TYPE_REF); // 读取：从字符串s读取JSON并转换为LinkedHashMap<String, Object>
    @SuppressWarnings("unchecked") // 注解：抑制未检查的类型转换警告
    final List<Map<String, Object>> rels = (List) requireNonNull(o.get("rels"), "rels"); // 提取：从JSON中获取rels列表，包含所有关系表达式的JSON描述
    readRels(rels); // 调用：读取所有关系表达式并构建关系表达式树
    return requireNonNull(lastRel, "lastRel"); // 返回：返回最后创建的关系表达式（通常是根节点）
  } // 方法结束

  /** Converts a JSON string (such as that produced by // 方法注释：将JSON字符串转换为Calcite类型
   * {@link RelJson#toJson(Object)}) into a Calcite type. // 该方法是RelJson.toJson的逆操作，用于反序列化关系数据类型 */
  public static RelDataType readType(RelDataTypeFactory typeFactory, String s) // 静态方法：将JSON字符串转换为RelDataType对象
      throws IOException { // 声明：可能抛出IOException异常
    final ObjectMapper mapper = new ObjectMapper(); // 创建：Jackson的ObjectMapper对象
    Map<String, Object> o = mapper // 反序列化：将JSON字符串解析为Map对象
        .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true) // 配置：使用BigDecimal处理浮点数
        .readValue(s, TYPE_REF); // 读取：从字符串s读取JSON
    return RelJson.create().toType(typeFactory, o); // 返回：通过RelJson将Map转换为RelDataType对象
  } // 方法结束

  /** Converts a JSON string (such as that produced by // 方法注释：将JSON字符串转换为Calcite行表达式
   * {@link RelJson#toJson(RexNode)}) into a Calcite expression. // 该方法是RelJson.toJson的逆操作，用于反序列化RexNode对象 */
  public static RexNode readRex(RelOptCluster typeFactory, String s) // 静态方法：将JSON字符串转换为RexNode对象
      throws IOException { // 声明：可能抛出IOException异常
    final ObjectMapper mapper = new ObjectMapper(); // 创建：Jackson的ObjectMapper对象
    Map<String, Object> o = mapper // 反序列化：将JSON字符串解析为Map对象
        .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true) // 配置：使用BigDecimal处理浮点数
        .readValue(s, TYPE_REF); // 读取：从字符串s读取JSON
    return RelJson.create().toRex(typeFactory, o); // 返回：通过RelJson将Map转换为RexNode对象
  } // 方法结束

  private void readRels(List<Map<String, Object>> jsonRels) { // 私有方法：读取所有关系表达式的JSON列表
    for (Map<String, Object> jsonRel : jsonRels) { // 循环：遍历每个关系表达式的JSON描述
      readRel(jsonRel); // 调用：读取单个关系表达式
    } // 循环结束
  } // 方法结束

  private void readRel(final Map<String, Object> jsonRel) { // 私有方法：读取单个关系表达式的JSON描述
    String id = (String) requireNonNull(jsonRel.get("id"), "jsonRel.id"); // 提取：获取关系表达式的唯一标识符ID
    String type = (String) requireNonNull(jsonRel.get("relOp"), "jsonRel.relOp"); // 提取：获取关系操作符类型（如LogicalFilter、LogicalProject等）
    Constructor constructor = relJson.getConstructor(type); // 获取：根据类型获取对应的构造函数
    RelInput input = new RelInput() { // 创建：RelInput匿名内部类，用于向构造函数提供输入数据
      @Override public RelOptCluster getCluster() { // 重写方法：获取关系表达式优化集群
        return cluster; // 返回：返回当前reader持有的cluster对象
      } // 方法结束

      @Override public RelTraitSet getTraitSet() { // 重写方法：获取关系表达式特征集合
        return cluster.traitSetOf(Convention.NONE); // 返回：返回一个只包含NONE约定（无约定）的特征集合
      } // 方法结束

      @Override public RelOptTable getTable(String table) { // 重写方法：根据表名获取表对象
        final List<String> list = // 声明：表名的完整路径列表
            requireNonNull(getStringList(table), // 调用：从JSON中获取表名列表
                () -> "getStringList for " + table); // 错误消息：如果获取失败，提供包含表名的错误信息
        return requireNonNull(relOptSchema.getTableForMember(list), // 调用：从relOptSchema中查找表
            () -> "table " + table + " is not found in schema " + relOptSchema); // 错误消息：如果表不存在，提供详细的错误信息
      } // 方法结束

      @Override public RelNode getInput() { // 重写方法：获取单个输入关系表达式
        final List<RelNode> inputs = getInputs(); // 调用：获取所有输入列表
        assert inputs.size() == 1; // 断言：确保只有一个输入
        return inputs.get(0); // 返回：返回第一个输入
      } // 方法结束

      @Override public List<RelNode> getInputs() { // 重写方法：获取所有输入关系表达式列表
        final List<String> jsonInputs = getStringList("inputs"); // 调用：从JSON中获取输入ID列表
        if (jsonInputs == null) { // 判断：如果没有明确指定inputs
          return ImmutableList.of(requireNonNull(lastRel, "lastRel")); // 返回：返回包含lastRel的单元素列表
        } // 条件结束
        final ImmutableList.Builder<RelNode> inputs = new ImmutableList.Builder<>(); // 创建：不可变列表构建器
        for (String jsonInput : jsonInputs) { // 循环：遍历每个输入ID
          inputs.add(lookupInput(jsonInput)); // 调用：查找并添加对应的关系表达式
        } // 循环结束
        return inputs.build(); // 返回：构建并返回不可变列表
      } // 方法结束

      @Override public @Nullable RexNode getExpression(String tag) { // 重写方法：根据标签获取行表达式
        return relJson.toRex(this, jsonRel.get(tag)); // 调用：通过relJson将JSON对象转换为RexNode
      } // 方法结束

      @Override public ImmutableBitSet getBitSet(String tag) { // 重写方法：根据标签获取位集合
        return ImmutableBitSet.of(requireNonNull(getIntegerList(tag), tag)); // 调用：从整数列表创建不可变位集合
      } // 方法结束

      @Override public @Nullable List<ImmutableBitSet> getBitSetList(String tag) { // 重写方法：根据标签获取位集合列表
        List<List<Integer>> list = getIntegerListList(tag); // 调用：从JSON中获取整数列表的列表
        if (list == null) { // 判断：如果数据不存在
          return null; // 返回：返回null
        } // 条件结束
        final ImmutableList.Builder<ImmutableBitSet> builder = // 创建：不可变列表构建器
            ImmutableList.builder(); // 实例化：构建器对象
        for (List<Integer> integers : list) { // 循环：遍历每个整数列表
          builder.add(ImmutableBitSet.of(integers)); // 调用：将整数列表转换为位集合并添加到构建器
        } // 循环结束
        return builder.build(); // 返回：构建并返回位集合列表
      } // 方法结束

      @Override public @Nullable List<String> getStringList(String tag) { // 重写方法：根据标签获取字符串列表
        //noinspection unchecked // 注解：抑制未检查的类型转换警告
        return (List<String>) jsonRel.get(tag); // 调用：从JSON中获取字符串列表
      } // 方法结束

      @Override public @Nullable List<Integer> getIntegerList(String tag) { // 重写方法：根据标签获取整数列表
        //noinspection unchecked // 注解：抑制未检查的类型转换警告
        return (List<Integer>) jsonRel.get(tag); // 调用：从JSON中获取整数列表
      } // 方法结束

      @Override public @Nullable List<List<Integer>> getIntegerListList(String tag) { // 重写方法：根据标签获取整数列表的列表
        //noinspection unchecked // 注解：抑制未检查的类型转换警告
        return (List<List<Integer>>) jsonRel.get(tag); // 调用：从JSON中获取整数列表的列表
      } // 方法结束

      @Override public List<AggregateCall> getAggregateCalls(String tag) { // 重写方法：根据标签获取聚合调用列表
        @SuppressWarnings("unchecked") // 注解：抑制未检查的类型转换警告
        final List<Map<String, Object>> jsonAggs = (List) getNonNull(tag); // 调用：从JSON中获取聚合调用的JSON列表
        final List<AggregateCall> inputs = new ArrayList<>(); // 创建：聚合调用列表
        for (Map<String, Object> jsonAggCall : jsonAggs) { // 循环：遍历每个聚合调用的JSON描述
          inputs.add(toAggCall(jsonAggCall)); // 调用：将JSON转换为AggregateCall并添加到列表
        } // 循环结束
        return inputs; // 返回：返回聚合调用列表
      } // 方法结束

      @Override public @Nullable Object get(String tag) { // 重写方法：根据标签获取任意对象
        return jsonRel.get(tag); // 调用：从JSON中获取对象
      } // 方法结束

      private Object getNonNull(String tag) { // 私有方法：根据标签获取非空对象
        return requireNonNull(get(tag), () -> "no entry for tag " + tag); // 调用：获取对象并确保不为null，否则抛出异常
      } // 方法结束

      @Override public @Nullable String getString(String tag) { // 重写方法：根据标签获取字符串
        return (String) get(tag); // 调用：从JSON中获取字符串
      } // 方法结束

      @Override public float getFloat(String tag) { // 重写方法：根据标签获取浮点数
        return ((Number) getNonNull(tag)).floatValue(); // 调用：从JSON中获取数值并转换为float
      } // 方法结束

      @Override public BigDecimal getBigDecimal(String tag) { // 重写方法：根据标签获取BigDecimal
        return SqlFunctions.toBigDecimal(getNonNull(tag)); // 调用：从JSON中获取数值并转换为BigDecimal
      } // 方法结束

      @Override public boolean getBoolean(String tag, boolean default_) { // 重写方法：根据标签获取布尔值，支持默认值
        final Boolean b = (Boolean) get(tag); // 调用：从JSON中获取布尔值
        return b != null ? b : default_; // 返回：如果值为null则返回默认值，否则返回实际值
      } // 方法结束

      @Override public <E extends Enum<E>> @Nullable E getEnum(String tag, Class<E> enumClass) { // 重写方法：根据标签获取枚举值
        return Util.enumVal(enumClass, // 调用：通过Util工具类将字符串转换为枚举值
            ((String) getNonNull(tag)).toUpperCase(Locale.ROOT)); // 转换：将字符串转为大写以匹配枚举名称
      } // 方法结束

      @Override public @Nullable List<RexNode> getExpressionList(String tag) { // 重写方法：根据标签获取行表达式列表
        @SuppressWarnings("unchecked") // 注解：抑制未检查的类型转换警告
        final List<Object> jsonNodes = (List) jsonRel.get(tag); // 调用：从JSON中获取行表达式的JSON列表
        if (jsonNodes == null) { // 判断：如果列表不存在
          return null; // 返回：返回null
        } // 条件结束
        final List<RexNode> nodes = new ArrayList<>(); // 创建：行表达式列表
        for (Object jsonNode : jsonNodes) { // 循环：遍历每个行表达式的JSON描述
          nodes.add(relJson.toRex(this, jsonNode)); // 调用：将JSON转换为RexNode并添加到列表
        } // 循环结束
        return nodes; // 返回：返回行表达式列表
      } // 方法结束

      @Override public RelDataType getRowType(String tag) { // 重写方法：根据标签获取行类型
        final Object o = getNonNull(tag); // 调用：从JSON中获取行类型的JSON描述
        return relJson.toType(cluster.getTypeFactory(), o); // 调用：通过relJson将JSON转换为RelDataType
      } // 方法结束

      @Override public RelDataType getRowType(String expressionsTag, String fieldsTag) { // 重写方法：根据表达式列表和字段名列表创建行类型
        final List<RexNode> expressionList = getExpressionList(expressionsTag); // 调用：从JSON中获取表达式列表
        @SuppressWarnings("unchecked") final List<String> names = // 注解：抑制未检查的类型转换警告
            (List<String>) getNonNull(fieldsTag); // 调用：从JSON中获取字段名列表
        return cluster.getTypeFactory().createStructType( // 调用：创建结构类型
            new AbstractList<Map.Entry<String, RelDataType>>() { // 创建：匿名AbstractList，提供字段名和类型的映射
              @Override public Map.Entry<String, RelDataType> get(int index) { // 重写方法：获取指定索引的字段条目
                return Pair.of(names.get(index), // 返回：创建包含字段名和类型的键值对
                    requireNonNull(expressionList, "expressionList").get(index).getType()); // 获取：表达式的类型
              } // 方法结束

              @Override public int size() { // 重写方法：获取字段数量
                return names.size(); // 返回：返回字段名列表的大小
              } // 方法结束
            }); // 匿名类结束
      } // 方法结束

      @Override public RelCollation getCollation() { // 重写方法：获取排序规则
        //noinspection unchecked // 注解：抑制未检查的类型转换警告
        return relJson.toCollation((List) getNonNull("collation")); // 调用：从JSON中获取排序规则并转换为RelCollation对象
      } // 方法结束

      @Override public RelDistribution getDistribution() { // 重写方法：获取数据分布规则
        //noinspection unchecked // 注解：抑制未检查的类型转换警告
        return relJson.toDistribution((Map<String, Object>) getNonNull("distribution")); // 调用：从JSON中获取分布规则并转换为RelDistribution对象
      } // 方法结束

      @Override public ImmutableList<ImmutableList<RexLiteral>> getTuples(String tag) { // 重写方法：根据标签获取元组列表（Values常量）
        //noinspection unchecked // 注解：抑制未检查的类型转换警告
        final List<List> jsonTuples = (List) getNonNull(tag); // 调用：从JSON中获取元组列表
        final ImmutableList.Builder<ImmutableList<RexLiteral>> builder = // 创建：不可变列表构建器
            ImmutableList.builder(); // 实例化：构建器对象
        for (List jsonTuple : jsonTuples) { // 循环：遍历每个元组
          builder.add(getTuple(jsonTuple)); // 调用：将JSON元组转换为RexLiteral列表并添加到构建器
        } // 循环结束
        return builder.build(); // 返回：构建并返回元组列表
      } // 方法结束

      public ImmutableList<RexLiteral> getTuple(List jsonTuple) { // 公共方法：将JSON元组转换为RexLiteral列表
        final ImmutableList.Builder<RexLiteral> builder = // 创建：不可变列表构建器
            ImmutableList.builder(); // 实例化：构建器对象
        for (Object jsonValue : jsonTuple) { // 循环：遍历元组中的每个值
          builder.add((RexLiteral) relJson.toRex(this, jsonValue)); // 调用：将JSON值转换为RexLiteral并添加到构建器
        } // 循环结束
        return builder.build(); // 返回：构建并返回RexLiteral列表
      } // 方法结束
    }; // RelInput匿名内部类结束
    try { // 开始：异常处理块
      final RelNode rel = (RelNode) constructor.newInstance(input); // 调用：通过反射使用构造函数创建关系表达式实例
      relMap.put(id, rel); // 操作：将创建的关系表达式存入映射表，使用ID作为键
      lastRel = rel; // 操作：更新最后的关系表达式引用
    } catch (InstantiationException | IllegalAccessException e) { // 捕获：实例化或访问异常
      throw new RuntimeException(e); // 抛出：包装为运行时异常
    } catch (InvocationTargetException e) { // 捕获：反射调用目标异常
      final Throwable e2 = e.getCause(); // 获取：获取异常的根本原因
      if (e2 instanceof RuntimeException) { // 判断：如果根本原因是运行时异常
        throw (RuntimeException) e2; // 抛出：直接抛出该异常
      } // 条件结束
      throw new RuntimeException(e2); // 抛出：否则包装为运行时异常
    } // 异常处理结束
  } // 方法结束

  private AggregateCall toAggCall(Map<String, Object> jsonAggCall) { // 私有方法：将JSON聚合调用转换为AggregateCall对象
    @SuppressWarnings("unchecked") // 注解：抑制未检查的类型转换警告
    final Map<String, Object> aggMap = // 声明：聚合函数的JSON映射
        (Map) requireNonNull(jsonAggCall.get("agg"), // 调用：从JSON中获取聚合函数信息
            "agg key is not found"); // 错误消息：如果agg键不存在
    final SqlAggFunction aggregation = // 声明：SQL聚合函数对象
        requireNonNull(relJson.toAggregation(aggMap), // 调用：将JSON转换为SqlAggFunction
            () -> "relJson.toAggregation output for " + aggMap); // 错误消息：如果转换失败
    final boolean distinct = // 声明：是否去重标志
        requireNonNull((Boolean) jsonAggCall.get("distinct"), // 调用：从JSON中获取distinct标志
            "jsonAggCall.distinct"); // 错误消息：如果distinct键不存在
    @SuppressWarnings("unchecked") // 注解：抑制未检查的类型转换警告
    final List<Integer> operands = // 声明：操作数索引列表
        requireNonNull((List<Integer>) jsonAggCall.get("operands"), // 调用：从JSON中获取操作数列表
            "jsonAggCall.operands"); // 错误消息：如果operands键不存在
    final Integer filterOperand = (Integer) jsonAggCall.get("filter"); // 声明：过滤操作数索引（可为null）
    final Object jsonAggType = // 声明：聚合结果类型的JSON描述
        requireNonNull(jsonAggCall.get("type"), "jsonAggCall.type"); // 调用：从JSON中获取类型信息
    final RelDataType type = // 声明：聚合结果的数据类型
        relJson.toType(cluster.getTypeFactory(), jsonAggType); // 调用：将JSON转换为RelDataType
    final String name = (String) jsonAggCall.get("name"); // 声明：聚合调用的名称（可为null）
    return AggregateCall.create(aggregation, distinct, false, false, // 返回：创建AggregateCall对象，参数分别为：聚合函数、是否去重、是否近似、忽略Nulls、排序键、操作数、过滤操作数、去重键、排序规则、返回类型、名称
        ImmutableList.of(), operands, // 参数：空排序键列表和操作数列表
        filterOperand == null ? -1 : filterOperand, // 参数：过滤操作数索引，如果为null则设为-1
        null, RelCollations.EMPTY, type, name); // 参数：null去重键、空排序规则、返回类型、名称
  } // 方法结束

  private RelNode lookupInput(String jsonInput) { // 私有方法：根据ID查找关系表达式
    RelNode node = relMap.get(jsonInput); // 调用：从映射表中查找对应的关系表达式
    if (node == null) { // 判断：如果找不到对应的关系表达式
      throw new RuntimeException("unknown id " + jsonInput // 抛出：运行时异常，提示未知的ID
          + " for relational expression"); // 错误消息：包含ID的完整错误信息
    } // 条件结束
    return node; // 返回：返回找到的关系表达式
  } // 方法结束
} // 类结束
