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
// Apache Calcite是一个动态数据管理框架，提供SQL解析、优化、查询执行等功能
// 本包org.apache.calcite.rel.core包含Calcite关系代数表达式的核心实现类
package org.apache.calcite.rel.core;

// 导入Convention类：表示关系表达式的调用约定，定义了数据如何访问和处理的规范
import org.apache.calcite.plan.Convention;
// 导入RelOptCluster类：关系表达式集群，包含了一组共享相同环境的关系表达式
import org.apache.calcite.plan.RelOptCluster;
// 导入RelTraitSet类：关系表达式特征集合，定义了关系表达式的物理和逻辑属性
import org.apache.calcite.plan.RelTraitSet;
// 导入RelInput类：用于从序列化数据中重建关系表达式的接口
import org.apache.calcite.rel.RelInput;
// 导入RelNode接口：所有关系表达式的基础接口，定义了关系代数操作的基本行为
import org.apache.calcite.rel.RelNode;
// 导入RelWriter类：用于将关系表达式以可读格式输出的接口
import org.apache.calcite.rel.RelWriter;
// 导入SingleRel类：单输入关系表达式的抽象基类，Collect继承此类因为它只有一个输入
import org.apache.calcite.rel.SingleRel;
// 导入RelDataType接口：表示关系数据的类型，包含字段信息、类型信息等
import org.apache.calcite.rel.type.RelDataType;
// 导入RelDataTypeFactory接口：关系数据类型工厂，用于创建各种SQL数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory;
// 导入SqlKind枚举：SQL操作的类型枚举，如SELECT、INSERT、ARRAY_QUERY_CONSTRUCTOR等
import org.apache.calcite.sql.SqlKind;
// 导入SqlTypeName枚举：SQL数据类型名称枚举，如INTEGER、VARCHAR、ARRAY、MULTISET等
import org.apache.calcite.sql.type.SqlTypeName;
// 导入SqlTypeUtil类：SQL类型工具类，提供类型转换、类型推导等辅助方法
import org.apache.calcite.sql.type.SqlTypeUtil;

// 导入Google Guava的Iterables类：提供集合操作的实用工具方法
import com.google.common.collect.Iterables;

// 导入Java标准库的List接口：表示有序列表集合
import java.util.List;

// 静态导入Java Objects.requireNonNull方法：用于检查对象引用不为null
import static java.util.Objects.requireNonNull;

/**
 * A relational expression that collapses multiple rows into one.
 * 这是一个关系表达式，用于将多行数据折叠成一行，即将输入的多条记录收集到一个集合中
 * 
 * Collect操作是Calcite中用于实现集合构造功能的关系算子，它可以将查询结果转换为集合类型
 * 支持的集合类型包括：ARRAY（数组）、MULTISET（多重集）、MAP（映射）
 * 
 * <p>Rules: 创建Collect关系的规则说明
 *
 * <ul>
 * <li>{@link org.apache.calcite.rel.rules.SubQueryRemoveRule}
 * creates a Collect from a call to
 * SubQueryRemoveRule规则会从以下SQL函数调用中创建Collect关系表达式：
 * {@link org.apache.calcite.sql.fun.SqlArrayQueryConstructor},  // 数组查询构造器，如ARRAY(SELECT x FROM t)
 * {@link org.apache.calcite.sql.fun.SqlMapQueryConstructor}, or  // 映射查询构造器，如MAP(SELECT key, value FROM t)
 * {@link org.apache.calcite.sql.fun.SqlMultisetQueryConstructor}.</li>  // 多重集查询构造器，如MULTISET(SELECT x FROM t)
 * 这些SQL构造函数在查询优化过程中会被转换为Collect关系表达式
 * </ul>
 */
// Collect类继承自SingleRel，表示它是一个单输入的关系表达式
// SingleRel是所有只有一个输入的关系表达式的基类，如Filter、Project等
public class Collect extends SingleRel {
  //~ Instance fields --------------------------------------------------------
  // 成员变量区域标记（虽然本类没有显式声明的实例字段，但继承自父类的字段包括：
  // cluster: 关系表达式集群，包含类型工厂等共享资源
  // traitSet: 特征集合，定义此关系表达式的属性
  // input: 输入关系表达式，Collect只有一个输入
  // rowType: 输出行类型，定义了Collect操作结果的类型信息，这是一个隐式的成员变量）

  //~ Constructors -----------------------------------------------------------
  // 构造方法区域标记

  /**
   * Creates a Collect.
   * 创建一个Collect关系表达式实例
   *
   * <p>Use {@link #create} unless you know what you're doing.
   * 除非你清楚自己在做什么，否则建议使用静态工厂方法create()来创建Collect实例
   * 静态工厂方法提供了更方便的创建方式和参数验证
   *
   * @param cluster   Cluster - 关系表达式集群，提供类型工厂等共享资源
   * @param traitSet  Trait set - 特征集合，定义此关系表达式的物理和逻辑属性
   * @param input     Input relational expression - 输入关系表达式，Collect会将这个输入的多行数据收集到一个集合中
   * @param rowType   Row type - 输出行类型，定义了Collect操作结果的类型，必须是集合类型（ARRAY、MAP或MULTISET）
   */
  protected Collect(
      RelOptCluster cluster,  // 关系表达式集群参数，必须非null
      RelTraitSet traitSet,   // 特征集合参数，定义关系表达式的属性
      RelNode input,          // 输入关系表达式参数，将被收集到集合中
      RelDataType rowType) {  // 输出行类型参数，定义结果集合的类型
    super(cluster, traitSet, input);  // 调用父类SingleRel的构造方法，初始化集群、特征集合和输入
    this.rowType = requireNonNull(rowType, "rowType");  // 保存行类型，使用requireNonNull确保rowType不为null，否则抛出NullPointerException
    final SqlTypeName collectionType = getCollectionType(rowType);  // 从行类型中提取集合类型（ARRAY、MAP或MULTISET）
    switch (collectionType) {  // 根据集合类型进行验证
    case ARRAY:  // 如果是数组类型，验证通过
    case MAP:    // 如果是映射类型，验证通过
    case MULTISET:  // 如果是多重集类型，验证通过
      break;  // 这些都是合法的集合类型，不做任何操作
    default:  // 其他类型都是非法的
      throw new IllegalArgumentException("not a collection type "  // 抛出非法参数异常，提示不是合法的集合类型
          + collectionType);  // 拼接实际的类型名称
    }
  }

  @Deprecated // to be removed before 2.0  // 标记为过时的构造方法，将在2.0版本之前移除
  /**
   * 已废弃的构造方法，使用字段名而不是行类型来创建Collect
   * @deprecated 推荐使用带有rowType参数的构造方法或create()工厂方法
   */
  public Collect(
      RelOptCluster cluster,  // 关系表达式集群参数
      RelTraitSet traitSet,   // 特征集合参数
      RelNode input,          // 输入关系表达式参数
      String fieldName) {     // 字段名参数，用于创建MULTISET类型的行类型
    this(cluster, traitSet, input,  // 调用主构造方法，使用deriveRowType方法推导出行类型
        deriveRowType(cluster.getTypeFactory(), SqlTypeName.MULTISET, fieldName,  // 创建MULTISET类型的行类型
            input.getRowType()));  // 使用输入的行类型作为元素类型
  }

  /**
   * Creates a Collect by parsing serialized output.
   * 通过解析序列化输出来创建Collect关系表达式
   * 这个构造方法用于从持久化存储中恢复Collect关系表达式
   * RelInput包含了序列化后的关系表达式信息
   */
  public Collect(RelInput input) {  // RelInput参数包含序列化的关系表达式数据
    this(input.getCluster(), input.getTraitSet(), input.getInput(),  // 调用主构造方法，从RelInput中提取集群、特征集合和输入
        deriveRowType(input.getCluster().getTypeFactory(), SqlTypeName.MULTISET,  // 推导MULTISET类型的行类型
            requireNonNull(input.getString("field"), "field"),  // 从序列化数据中获取字段名，要求非null
            input.getInput().getRowType()));  // 获取输入的行类型作为元素类型
  }

  //~ Methods ----------------------------------------------------------------
  // 方法区域标记

  /**
   * Creates a Collect.
   * 创建Collect关系表达式的静态工厂方法，这是推荐的创建方式
   *
   * @param input          Input relational expression - 输入关系表达式
   * @param rowType        Row type - 输出行类型，必须是集合类型
   * @return 新创建的Collect关系表达式实例
   */
  public static Collect create(RelNode input, RelDataType rowType) {  // 静态工厂方法，根据输入和行类型创建Collect
    final RelOptCluster cluster = input.getCluster();  // 从输入关系表达式中获取集群
    final RelTraitSet traitSet =  // 创建特征集合
        cluster.traitSet().replace(Convention.NONE);  // 使用NONE约定，表示这是一个逻辑关系表达式，不指定物理实现
    return new Collect(cluster, traitSet, input, rowType);  // 创建并返回新的Collect实例
  }

  /**
   * Creates a Collect.
   * 已废弃的静态工厂方法，使用字段名和集合类型创建Collect
   *
   * @param input          Input relational expression - 输入关系表达式
   * @param collectionType ARRAY, MAP or MULTISET - 集合类型
   * @param fieldName      Name of the sole output field - 唯一输出字段的名称
   * @deprecated 推荐使用带有rowType参数的create方法
   */
  @Deprecated // to be removed before 2.0  // 标记为过时的方法，将在2.0版本之前移除
  public static Collect create(RelNode input,  // 输入关系表达式
      SqlTypeName collectionType,  // 集合类型（ARRAY、MAP或MULTISET）
      String fieldName) {  // 输出字段名
    return create(input,  // 调用主create方法
        deriveRowType(input.getCluster().getTypeFactory(), collectionType,  // 根据集合类型和字段名推导行类型
            fieldName, input.getRowType()));  // 使用输入的行类型作为元素类型
  }

  /**
   * Creates a Collect.
   * 根据SQL操作类型创建Collect关系表达式的静态工厂方法
   * 这是最灵活的创建方式，可以根据不同的SQL语法自动选择合适的集合类型
   *
   * @param input          Input relational expression - 输入关系表达式
   * @param sqlKind        SqlKind - SQL操作类型，如ARRAY_QUERY_CONSTRUCTOR、MAP_QUERY_CONSTRUCTOR等
   * @param fieldName      Name of the sole output field - 唯一输出字段的名称
   * @return 新创建的Collect关系表达式实例
   */
  public static Collect create(RelNode input,  // 输入关系表达式
      SqlKind sqlKind,  // SQL操作类型
      String fieldName) {  // 输出字段名
    SqlTypeName collectionType = getCollectionType(sqlKind);  // 根据SQL操作类型获取对应的集合类型
    RelDataType rowType;  // 声明行类型变量
    switch (sqlKind) {  // 根据SQL操作类型进行不同处理
    case ARRAY_QUERY_CONSTRUCTOR:  // 数组查询构造器，如ARRAY(SELECT x FROM t)
    case MAP_QUERY_CONSTRUCTOR:    // 映射查询构造器，如MAP(SELECT key, value FROM t)
    case MULTISET_QUERY_CONSTRUCTOR:  // 多重集查询构造器，如MULTISET(SELECT x FROM t)
      rowType =  // 对于查询构造器，需要推导集合查询的组件类型
          deriveRowType(input.getCluster().getTypeFactory(), collectionType,  // 推导行类型
              fieldName,  // 字段名
              SqlTypeUtil.deriveCollectionQueryComponentType(collectionType,  // 从集合类型推导查询组件类型
                  input.getRowType()));  // 使用输入的行类型
      break;  // 跳出switch
    default:  // 其他情况
      rowType =  // 直接使用输入的行类型作为元素类型
          deriveRowType(input.getCluster().getTypeFactory(), collectionType,  // 推导行类型
              fieldName, input.getRowType());  // 字段名和输入行类型
    }
    return create(input, rowType);  // 调用主create方法创建并返回Collect实例
  }

  /** Returns the row type, guaranteed not null.
   * 返回行类型，保证非null
   * 
   * (The row type is never null after initialization, but
   * CheckerFramework can't deduce that references are safe.)
   * 行类型在初始化后永远不会为null，但CheckerFramework无法推断引用是安全的
   * CheckerFramework是一个静态分析工具，用于检测Java代码中的bug
   * 这个方法使用了requireNonNull来满足CheckerFramework的要求
   * 
   * @return 非null的行类型对象
   */
  protected final RelDataType rowType() {  // 受保护的final方法，返回行类型
    return requireNonNull(rowType, "rowType");  // 返回rowType，使用requireNonNull确保非null，如果为null则抛出NullPointerException
  }

  @Override public final RelNode copy(RelTraitSet traitSet,  // 重写copy方法，用于复制关系表达式
      List<RelNode> inputs) {  // 输入关系表达式列表
    return copy(traitSet, sole(inputs));  // 调用重载的copy方法，使用sole方法获取唯一的输入
  }

  /**
   * 复制Collect关系表达式，使用新的特征集合和输入
   * @param traitSet 新的特征集合
   * @param input 新的输入关系表达式
   * @return 新的Collect实例
   */
  public RelNode copy(RelTraitSet traitSet, RelNode input) {  // 重载的copy方法
    assert traitSet.containsIfApplicable(Convention.NONE);  // 断言特征集合包含NONE约定（如果适用）
    return new Collect(getCluster(), traitSet, input, rowType());  // 创建并返回新的Collect实例，保持相同的行类型
  }

  @Override public RelWriter explainTerms(RelWriter pw) {  // 重写explainTerms方法，用于生成关系表达式的可读描述
    return super.explainTerms(pw)  // 调用父类的explainTerms方法，生成基础描述
        .item("field", getFieldName());  // 添加字段名项到描述中
  }

  /**
   * Returns the name of the sole output field.
   * 返回唯一输出字段的名称
   * Collect的输出只有一行，该行只包含一个字段，这个方法返回该字段的名称
   *
   * @return name of the sole output field - 唯一输出字段的名称
   */
  public String getFieldName() {  // 获取输出字段名的方法
    return Iterables.getOnlyElement(rowType().getFieldList()).getName();  // 从行类型的字段列表中获取唯一的字段，并返回其名称
  }

  /** Returns the collection type (ARRAY, MAP, or MULTISET).
   * 返回集合类型（ARRAY、MAP或MULTISET）
   * 
   * @return 集合类型的SqlTypeName枚举值
   */
  public SqlTypeName getCollectionType() {  // 获取集合类型的公共方法
    return getCollectionType(rowType());  // 调用静态方法，从行类型中获取集合类型
  }

  /**
   * 从行类型中提取集合类型的静态辅助方法
   * Collect的输出行类型包含一个字段，该字段的类型就是集合类型
   * 
   * @param rowType 行类型对象
   * @return 集合类型（ARRAY、MAP或MULTISET）
   */
  private static SqlTypeName getCollectionType(RelDataType rowType) {  // 私有静态方法，从行类型获取集合类型
    return Iterables.getOnlyElement(rowType.getFieldList())  // 获取行类型中唯一的字段
        .getType().getSqlTypeName();  // 获取该字段的类型，然后获取其SQL类型名称
  }

  /**
   * 根据SQL操作类型获取对应的集合类型的静态辅助方法
   * 不同的SQL构造函数对应不同的集合类型
   * 
   * @param sqlKind SQL操作类型
   * @return 对应的集合类型
   * @throws IllegalArgumentException 如果sqlKind不是集合类型则抛出异常
   */
  private static SqlTypeName getCollectionType(SqlKind sqlKind) {  // 私有静态方法，从SQL操作类型获取集合类型
    switch (sqlKind) {  // 根据SQL操作类型进行匹配
    case ARRAY_QUERY_CONSTRUCTOR:  // 数组查询构造器
    case ARRAY_VALUE_CONSTRUCTOR:  // 数组值构造器
      return SqlTypeName.ARRAY;  // 返回数组类型
    case MULTISET_QUERY_CONSTRUCTOR:  // 多重集查询构造器
    case MULTISET_VALUE_CONSTRUCTOR:  // 多重集值构造器
      return SqlTypeName.MULTISET;  // 返回多重集类型
    case MAP_QUERY_CONSTRUCTOR:  // 映射查询构造器
    case MAP_VALUE_CONSTRUCTOR:  // 映射值构造器
      return SqlTypeName.MAP;  // 返回映射类型
    default:  // 其他类型
      throw new IllegalArgumentException("not a collection kind "  // 抛出非法参数异常，提示不是集合类型
          + sqlKind);  // 拼接实际的SQL操作类型名称
    }
  }

  @Override protected RelDataType deriveRowType() {  // 重写父类的deriveRowType方法
    // this method should never be called; rowType is always set
    // 这个方法永远不应该被调用，因为rowType总是在构造时就被设置了
    // Collect的行类型是由构造参数提供的，不需要推导
    throw new UnsupportedOperationException();  // 抛出不支持操作异常
  }

  /**
   * Derives the output row type of a Collect relational expression.
   * 推导Collect关系表达式的输出行类型
   * 
   * 这是一个已废弃的方法，建议使用直接带有类型参数的deriveRowType方法
   *
   * @param rel       relational expression - 关系表达式
   * @param fieldName name of sole output field - 唯一输出字段的名称
   * @return output row type of a Collect relational expression - Collect关系表达式的输出行类型
   * @deprecated 推荐使用deriveRowType(RelDataTypeFactory, SqlTypeName, String, RelDataType)方法
   */
  @Deprecated // to be removed before 2.0  // 标记为过时的方法，将在2.0版本之前移除
  public static RelDataType deriveCollectRowType(  // 静态方法，推导Collect的行类型
      SingleRel rel,  // 单输入关系表达式
      String fieldName) {  // 字段名
    RelDataType inputType = rel.getInput().getRowType();  // 获取输入的行类型
    assert inputType.isStruct();  // 断言输入类型是结构化类型（有字段）
    return deriveRowType(rel.getCluster().getTypeFactory(),  // 调用主deriveRowType方法
        SqlTypeName.MULTISET, fieldName, inputType);  // 创建MULTISET类型的行类型
  }

  /**
   * Derives the output row type of a Collect relational expression.
   * 推导Collect关系表达式的输出行类型（主要方法）
   * 
   * 这个方法根据集合类型、字段名和元素类型，创建一个包含单个集合字段的行类型
   * 这是Collect操作的核心功能：将输入的多行数据转换为单个集合值
   *
   * @param typeFactory    Type factory - 类型工厂，用于创建SQL类型
   * @param collectionType MULTISET, ARRAY or MAP - 集合类型（多重集、数组或映射）
   * @param fieldName      Name of sole output field - 唯一输出字段的名称
   * @param elementType    Element type - 集合元素的类型，通常是输入的行类型
   * @return output row type of a Collect relational expression - Collect关系表达式的输出行类型
   */
  public static RelDataType deriveRowType(RelDataTypeFactory typeFactory,  // 类型工厂
      SqlTypeName collectionType, String fieldName, RelDataType elementType) {  // 集合类型、字段名、元素类型
    final RelDataType type1;  // 声明集合类型变量
    switch (collectionType) {  // 根据集合类型创建相应的集合类型
    case ARRAY:  // 如果是数组类型
      type1 = SqlTypeUtil.createArrayType(typeFactory, elementType, false);  // 创建数组类型，不允许null元素
      break;  // 跳出switch
    case MULTISET:  // 如果是多重集类型
      type1 = SqlTypeUtil.createMultisetType(typeFactory, elementType, false);  // 创建多重集类型，不允许null元素
      break;  // 跳出switch
    case MAP:  // 如果是映射类型
      type1 = SqlTypeUtil.createMapTypeFromRecord(typeFactory, elementType);  // 从记录类型创建映射类型，元素类型必须包含键值对
      break;  // 跳出switch
    default:  // 其他类型（理论上不应该到达这里）
      throw new AssertionError(collectionType);  // 抛出断言错误
    }
    return typeFactory.createTypeWithNullability(  // 创建具有可空性设置的类型
        typeFactory.builder().add(fieldName, type1).build(), false);  // 使用类型构建器添加字段，构建结构类型，设置为不可null
  }
}
