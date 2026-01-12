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
// Apache许可证头文件，声明代码遵循Apache 2.0许可证
package org.apache.calcite.adapter.innodb;  // 定义包名，该类属于InnoDB适配器包

import org.apache.calcite.adapter.enumerable.EnumerableRel;  // 导入可枚举关系表达式接口，用于标记可转换为LINQ可枚举对象的RelNode
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor;  // 导入可枚举关系表达式实现器，用于生成Java代码
import org.apache.calcite.adapter.enumerable.JavaRowFormat;  // 导入Java行格式枚举，定义行数据的表示方式（如数组、对象等）
import org.apache.calcite.adapter.enumerable.PhysType;  // 导入物理类型接口，描述运行时Java类型
import org.apache.calcite.adapter.enumerable.PhysTypeImpl;  // 导入物理类型实现类
import org.apache.calcite.config.CalciteSystemProperty;  // 导入Calcite系统属性配置类
import org.apache.calcite.linq4j.tree.BlockBuilder;  // 导入代码块构建器，用于构建Java代码块
import org.apache.calcite.linq4j.tree.Expression;  // 导入表达式类，表示Java表达式
import org.apache.calcite.linq4j.tree.Expressions;  // 导入表达式工具类，用于创建各种表达式
import org.apache.calcite.plan.ConventionTraitDef;  // 导入约定特征定义，定义关系表达式的调用约定
import org.apache.calcite.plan.RelOptCluster;  // 导入关系优化集群，包含同一查询中的所有RelNode
import org.apache.calcite.plan.RelOptCost;  // 导入关系优化代价接口，用于计算执行代价
import org.apache.calcite.plan.RelOptPlanner;  // 导入关系优化器接口
import org.apache.calcite.plan.RelTraitSet;  // 导入关系特征集合，包含一组关系特征
import org.apache.calcite.rel.RelNode;  // 导入关系节点接口，是Calcite中所有关系表达式的基础接口
import org.apache.calcite.rel.convert.ConverterImpl;  // 导入转换器实现基类，用于实现关系表达式之间的转换
import org.apache.calcite.rel.metadata.RelMetadataQuery;  // 导入关系元数据查询接口，用于获取关系表达式的元数据
import org.apache.calcite.rel.type.RelDataType;  // 导入关系数据类型接口，描述关系表达式的类型信息
import org.apache.calcite.runtime.PairList;  // 导入键值对列表类，用于存储键值对集合
import org.apache.calcite.sql.validate.SqlValidatorUtil;  // 导入SQL验证器工具类，提供SQL验证相关的实用方法
import org.apache.calcite.util.BuiltInMethod;  // 导入内置方法枚举，定义Calcite内置的常用方法
import org.apache.calcite.util.Pair;  // 导入键值对类，用于存储两个相关联的值
import org.apache.calcite.util.Util;  // 导入通用工具类，提供各种实用方法

import com.alibaba.innodb.java.reader.comparator.ComparisonOperator;  // 导入InnoDB比较操作符枚举，定义各种比较操作符

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入可空注解，用于标记可能为null的返回值

import java.util.AbstractList;  // 导入抽象列表类，用于创建自定义列表
import java.util.Collections;  // 导入集合工具类，提供不可变集合和集合操作方法
import java.util.List;  // 导入列表接口
import java.util.Map;  // 导入映射接口
import java.util.stream.Collectors;  // 导入流收集器工具类，用于流的收集操作
import java.util.stream.Stream;  // 导入流接口，用于函数式编程

import static java.util.Objects.requireNonNull;  // 静态导入requireNonNull方法，用于检查对象是否为null

/**
 * Relational expression representing a scan of a table
 * in InnoDB data source.
 * 表示在InnoDB数据源中扫描表的关系表达式
 * 
 * 【类作用详细说明】
 * InnodbToEnumerableConverter是Calcite InnoDB适配器中的核心转换类，负责将InnoDB特定的关系表达式
 * 转换为可枚举的关系表达式（EnumerableRel）。这个类在Calcite的查询优化和执行流程中扮演着关键角色：
 * 
 * 1. 转换器角色：作为ConverterImpl的子类，实现了从InnoDB约定（InnodbRel）到可枚举约定（EnumerableRel）的转换
 * 2. 代码生成：实现了EnumerableRel接口，能够生成Java代码来执行InnoDB表扫描
 * 3. 查询执行：通过implement方法生成可执行的表达式，最终调用InnoDB的查询接口获取数据
 * 
 * 【核心功能】
 * - 将逻辑查询计划转换为物理执行计划
 * - 生成LINQ风格的Java代码来执行InnoDB查询
 * - 处理索引条件、查询类型、范围查询等InnoDB特定的查询特性
 * - 支持点查询和范围查询的代码生成
 * 
 * 【使用场景】
 * 当查询优化器决定使用InnoDB适配器执行查询时，会通过这个转换器将逻辑计划转换为可执行的代码
 */
public class InnodbToEnumerableConverter extends ConverterImpl  // 定义类，继承ConverterImpl转换器基类
    implements EnumerableRel {  // 实现EnumerableRel接口，表示可以转换为可枚举对象
  // 【构造方法】
  // 作用：创建InnodbToEnumerableConverter实例
  // 参数说明：
  //   - cluster: 关系优化集群，包含查询的上下文信息（如RexBuilder、类型工厂等）
  //   - traits: 关系特征集合，定义该关系表达式的特征（如调用约定、排序等）
  //   - input: 输入关系节点，即要转换的InnoDB关系表达式
  protected InnodbToEnumerableConverter(  // 构造方法，protected访问权限
      RelOptCluster cluster,  // 参数：关系优化集群，提供查询优化所需的共享资源
      RelTraitSet traits,  // 参数：关系特征集合，定义该节点的特征
      RelNode input) {  // 参数：输入关系节点，即要转换的源节点
    super(cluster, ConventionTraitDef.INSTANCE, traits, input);  // 调用父类构造方法，传入集群、约定特征定义、特征集合和输入节点
  }

  // 【方法】copy
  // 作用：复制当前关系节点，可以修改特征集合
  // 参数说明：
  //   - traitSet: 新的特征集合
  //   - inputs: 新的输入节点列表
  // 返回值：新的InnodbToEnumerableConverter实例
  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {  // 覆盖copy方法，用于复制节点
    return new InnodbToEnumerableConverter(  // 返回新的转换器实例
        getCluster(), traitSet, sole(inputs));  // 使用当前集群、新特征集合和唯一输入节点创建新实例
  }

  // 【方法】computeSelfCost
  // 作用：计算当前关系节点的执行代价，供优化器使用
  // 参数说明：
  //   - planner: 关系优化器
  //   - mq: 关系元数据查询，用于获取元数据
  // 返回值：执行代价对象，可能为null
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,  // 覆盖computeSelfCost方法，计算执行代价
      RelMetadataQuery mq) {  // 参数：元数据查询对象
    return requireNonNull(super.computeSelfCost(planner, mq));  // 调用父类方法计算代价并确保不为null
  }

  // 【静态方法】innodbFieldNames
  // 作用：为InnoDB表生成唯一的字段名称列表
  // 参数说明：
  //   - rowType: 关系数据类型，包含表的字段信息
  // 返回值：唯一的字段名称列表
  // 说明：如果字段名有重复，会自动添加后缀使其唯一，避免SQL解析冲突
  static List<String> innodbFieldNames(final RelDataType rowType) {  // 静态方法，生成InnoDB字段名
    return SqlValidatorUtil.uniquify(rowType.getFieldNames(),  // 调用工具方法使字段名唯一化
        SqlValidatorUtil.EXPR_SUGGESTER, true);  // 使用表达式建议器生成唯一名称，第三个参数true表示忽略大小写
  }

  // 【方法】implement
  // 作用：实现可枚举关系表达式，生成可执行的Java代码
  // 这是整个类最核心的方法，负责生成调用InnoDB查询接口的代码
  // 参数说明：
  //   - implementor: 可枚举关系表达式实现器，提供代码生成所需的上下文
  //   - pref: 偏好的行格式（如数组、对象等）
  // 返回值：包含生成的代码块和物理类型的结果对象
  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) {  // 实现可枚举接口，生成执行代码
    final BlockBuilder list = new BlockBuilder();  // 创建代码块构建器，用于构建Java代码块
    final InnodbRel.Implementor innodbImplementor = new InnodbRel.Implementor();  // 创建InnoDB实现器，用于访问InnoDB特定的实现信息
    innodbImplementor.visitChild(0, getInput());  // 访问子节点（输入节点），收集表信息、索引条件、选择字段等
    final RelDataType rowType = getRowType();  // 获取当前节点的关系数据类型（输出行的类型）
    final PhysType physType =  // 创建物理类型对象，描述Java运行时的类型信息
        PhysTypeImpl.of(  // 使用PhysTypeImpl工厂方法创建物理类型
            implementor.getTypeFactory(), rowType,  // 参数：类型工厂和关系类型
            pref.prefer(JavaRowFormat.ARRAY));  // 偏好使用数组格式来表示行数据
    final Expression fields =  // 创建字段表达式，包含字段名称和对应的Java类型
        list.append("fields",  // 将表达式添加到代码块中，命名为"fields"
            constantArrayList(  // 调用方法创建常量数组列表表达式
                Pair.zip(InnodbToEnumerableConverter.innodbFieldNames(rowType),  // 将字段名与Java类型配对
                    new AbstractList<Class>() {  // 创建匿名抽象列表，提供字段的Java类型
                      @Override public Class get(int index) {  // 获取指定索引位置的字段Java类型
                        return physType.fieldClass(index);  // 返回物理类型中对应索引的字段类
                      }

                      @Override public int size() {  // 获取字段总数
                        return rowType.getFieldCount();  // 返回关系类型中的字段数量
                      }
                    }),
                Pair.class));  // 指定生成的列表元素类型为Pair（键值对）
    PairList<String, String> selectList =  // 创建选择字段列表，包含要查询的字段名称
        PairList.of(innodbImplementor.selectFields);  // 从InnoDB实现器中获取选择字段并转换为PairList
    final Expression selectFields =  // 创建选择字段表达式
        list.append("selectFields", constantArrayList(selectList, Pair.class));  // 将选择字段列表作为常量表达式添加到代码块
    final Expression table =  // 创建表表达式，表示要查询的InnoDB表
        list.append("table",  // 将表表达式添加到代码块
            requireNonNull(  // 确保表达式不为null
                innodbImplementor.table.getExpression(  // 获取表的LINQ表达式
                    InnodbTable.InnodbQueryable.class)));  // 指定表达式类型为InnodbQueryable
    IndexCondition condition = innodbImplementor.indexCondition;  // 从InnoDB实现器中获取索引条件对象
    final Expression indexName =  // 创建索引名称表达式
        list.append("indexName",  // 将索引名称表达式添加到代码块
            Expressions.constant(condition.getIndexName(), String.class));  // 创建字符串常量表达式，值为索引名称
    final Expression queryType =  // 创建查询类型表达式
        list.append("queryType",  // 将查询类型表达式添加到代码块
            Expressions.constant(condition.getQueryType(), QueryType.class));  // 创建QueryType枚举常量表达式，值为查询类型
    final Expression pointQueryKey =  // 创建点查询键表达式，用于精确匹配查询
        list.append("pointQueryKey",  // 将点查询键表达式添加到代码块
            constantArrayList(condition.getPointQueryKey(), Object.class));  // 将点查询键列表转换为常量表达式
    final Expression rangeQueryLowerOp =  // 创建范围查询下界操作符表达式
        list.append("rangeQueryLowerOp",  // 将下界操作符表达式添加到代码块
            Expressions.constant(condition.getRangeQueryLowerOp(), ComparisonOperator.class));  // 创建比较操作符常量表达式
    final Expression rangeQueryLowerKey =  // 创建范围查询下界键表达式
        list.append("rangeQueryLowerKey",  // 将下界键表达式添加到代码块
            constantArrayList(condition.getRangeQueryLowerKey(), Object.class));  // 将下界键列表转换为常量表达式
    final Expression rangeQueryUpperOp =  // 创建范围查询上界操作符表达式
        list.append("rangeQueryUpperOp",  // 将上界操作符表达式添加到代码块
            Expressions.constant(condition.getRangeQueryUpperOp(), ComparisonOperator.class));  // 创建比较操作符常量表达式
    final Expression rangeQueryUpperKey =  // 创建范围查询上界键表达式
        list.append("rangeQueryUpperKey",  // 将上界键表达式添加到代码块
            constantArrayList(condition.getRangeQueryUpperKey(), Object.class));  // 将上界键列表转换为常量表达式
    final Expression cond =  // 创建索引条件表达式，封装所有查询条件
        list.append("condition",  // 将条件表达式添加到代码块
            Expressions.call(IndexCondition.class,  // 调用IndexCondition类的静态create方法
                "create", indexName, queryType, pointQueryKey,  // 方法名和参数：索引名、查询类型、点查询键
                rangeQueryLowerOp, rangeQueryUpperOp, rangeQueryLowerKey,  // 范围查询下界操作符、上界操作符、下界键
                rangeQueryUpperKey));  // 范围查询上界键
    final Expression ascOrder =  // 创建排序顺序表达式
        Expressions.constant(innodbImplementor.ascOrder);  // 创建布尔常量表达式，表示是否升序排列
    Expression enumerable =  // 创建可枚举表达式，这是最终要执行的查询表达式
        list.append("enumerable",  // 将可枚举表达式添加到代码块
            Expressions.call(table,  // 调用表对象的查询方法
                InnodbMethod.INNODB_QUERYABLE_QUERY.method, fields,  // 方法引用：InnoDB查询方法，传入字段信息
                selectFields, cond, ascOrder));  // 传入选择字段、索引条件和排序顺序
    if (CalciteSystemProperty.DEBUG.value()) {  // 如果开启了调试模式
      System.out.println("Innodb: " + Expressions.toString(enumerable));  // 打印生成的表达式字符串，用于调试
    }
    list.add(Expressions.return_(null, enumerable));  // 将return语句添加到代码块，返回可枚举表达式
    return implementor.result(physType, list.toBlock());  // 返回实现结果，包含物理类型和生成的代码块
  }

  /**
   * E.g. {@code constantArrayList("x", "y")} returns
   * "Arrays.asList('x', 'y')".
   * 例如：constantArrayList("x", "y") 返回 "Arrays.asList('x', 'y')"
   * 
   * 【方法作用详细说明】
   * 将Java列表转换为常量数组列表表达式，用于代码生成
   * 这个方法在生成LINQ查询代码时非常重要，它将运行时的列表值转换为编译时的常量表达式
   * 
   * 【特殊处理】
   * 对于PairList类型，由于Map.Entry没有默认构造函数，不能直接使用Arrays.asList
   * 因此会生成PairList.of("k0", "v0", "k1", "v1")这样的代码
   * 
   * 【参数说明】
   *   - values: 要转换的值列表
   *   - clazz: 列表元素的类型
   * 【返回值】
   *   表示常量数组列表的表达式对象
   */
  @SuppressWarnings({"rawtypes", "unchecked"})  // 抑制原始类型和未检查转换的警告
  private static <T> Expression constantArrayList(List<T> values, Class clazz) {  // 泛型方法，将列表转换为常量数组表达式
    if (values instanceof PairList  // 如果值列表是PairList类型
        && !values.isEmpty()  // 且列表不为空
        && Map.Entry.class.isAssignableFrom(clazz)) {  // 且元素类型是Map.Entry或其子类
      // For PairList, we cannot generate Arrays.asList because Map.Entry does
      // not an obvious implementation with default constructor. Instead,
      // generate
      //   PairList.of("k0", "v0", "k1", "v1");
      // 对于PairList，我们不能生成Arrays.asList，因为Map.Entry没有明显的默认构造函数实现
      // 相反，我们生成 PairList.of("k0", "v0", "k1", "v1") 这样的代码
      final List<Object> keyValues =  // 创建键值对列表，用于存储所有键和值
          ((PairList<Object, Object>) values).stream()  // 将PairList转换为流
              .flatMap(p -> Stream.of(p.getKey(), p.getValue()))  // 将每个键值对展开为键和值两个元素
              .collect(Collectors.toList());  // 收集为列表
      return Expressions.call(null, BuiltInMethod.PAIR_LIST_COPY_OF.method,  // 调用PairList.copyOf静态方法
          constantList(keyValues));  // 传入键值列表的常量表达式
    }
    return Expressions.call(BuiltInMethod.ARRAYS_AS_LIST.method,  // 调用Arrays.asList方法
        Expressions.newArrayInit(clazz, constantList(values)));  // 创建数组初始化表达式，传入元素类型和值的常量表达式列表
  }

  /**
   * E.g. {@code constantList("x", "y")} returns
   * {@code {ConstantExpression("x"), ConstantExpression("y")}}.
   * 例如：constantList("x", "y") 返回 {ConstantExpression("x"), ConstantExpression("y")}
   * 
   * 【方法作用详细说明】
   * 将Java列表转换为常量表达式列表，每个元素都转换为常量表达式
   * 这是代码生成的基础工具方法，用于将运行时值转换为编译时常量
   * 
   * 【参数说明】
   *   - values: 要转换的值列表
   * 【返回值】
   *   常量表达式列表，每个元素都是Expressions.constant()创建的常量表达式
   */
  private static <T> List<Expression> constantList(List<T> values) {  // 泛型方法，将列表转换为常量表达式列表
    if (values.isEmpty()) {  // 如果列表为空
      return Collections.emptyList();  // 返回不可变的空列表
    }
    return Util.transform(values, Expressions::constant);  // 使用工具方法转换每个元素为常量表达式
  }
}
