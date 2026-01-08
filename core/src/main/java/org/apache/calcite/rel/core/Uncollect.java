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
package org.apache.calcite.rel.core; // 包声明：Uncollect类属于Calcite的rel.core包，表示关系代数核心组件

import org.apache.calcite.plan.Convention; // 导入Convention类：表示关系表达式的调用约定（如物理实现方式）
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类：表示关系优化集群，包含类型工厂等共享资源
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类：表示关系表达式的特征集合（如排序、分区等）
import org.apache.calcite.rel.RelInput; // 导入RelInput类：用于从序列化数据反序列化关系表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口：所有关系表达式的基接口
import org.apache.calcite.rel.RelWriter; // 导入RelWriter类：用于将关系表达式以可读格式输出
import org.apache.calcite.rel.SingleRel; // 导入SingleRel类：表示只有一个子节点的关系表达式基类
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口：表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口：用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField类：表示关系数据类型的字段
import org.apache.calcite.sql.SqlUnnestOperator; // 导入SqlUnnestOperator类：表示SQL中的UNNEST操作符
import org.apache.calcite.sql.type.MapSqlType; // 导入MapSqlType类：表示SQL中的MAP类型
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举：定义SQL类型名称（如INTEGER、VARCHAR等）

import com.google.common.collect.ImmutableList; // 导入ImmutableList类：Google Guava提供的不可变列表实现

import java.util.Collections; // 导入Collections类：Java集合工具类
import java.util.List; // 导入List接口：Java列表接口

import static org.apache.calcite.util.Static.RESOURCE; // 导入静态RESOURCE常量：用于访问资源文件中的错误消息

import static java.util.Objects.requireNonNull; // 导入静态requireNonNull方法：用于检查对象非空

/**
 * Relational expression that unnests its input's columns into a relation. // 类说明：Uncollect是一个关系表达式，用于将输入列解嵌套（展开）为关系表
 * // 该操作符对应SQL中的UNNEST操作，将数组或多集合（multiset）类型的列展开为多行
 *
 * <p>The input may have multiple columns, but each must be a multiset or // 输入可以包含多列，但每列必须是多集合（multiset）或数组类型
 * array. If {@code withOrdinality}, the output contains an extra // 如果withOrdinality为true，输出会包含一个额外的ORDINALITY列，用于记录元素在原数组中的位置
 * {@code ORDINALITY} column.
 *
 * <p>Like its inverse operation {@link Collect}, Uncollect is generally // 与其逆操作Collect类似，Uncollect通常在嵌套循环中被调用
 * invoked in a nested loop, driven by // 由LogicalCorrelate或类似的关联操作驱动
 * {@link org.apache.calcite.rel.logical.LogicalCorrelate} or similar.
 */
public class Uncollect extends SingleRel { // Uncollect类定义：继承自SingleRel，表示只有一个子节点的关系表达式
  public final boolean withOrdinality; // 成员变量：标志位，表示输出是否包含ORDINALITY列（序号列），用于记录元素在原数组中的位置

  // To alias the items in Uncollect list, // 注释说明：为Uncollect列表中的项设置别名
  // i.e., "UNNEST(a, b, c) as T(d, e, f)" // 例如：SQL语句"UNNEST(a, b, c) as T(d, e, f)"
  // outputs as row type Record(d, e, f) where the field "d" has element type of "a", // 输出为行类型Record(d, e, f)，其中字段"d"的类型是"a"的元素类型
  // field "e" has element type of "b"(Presto dialect). // 字段"e"的类型是"b"的元素类型（Presto方言）

  // Without the aliases, the expression "UNNEST(a)" outputs row type // 如果没有别名，表达式"UNNEST(a)"输出的行类型
  // same with element type of "a". // 与"a"的元素类型相同
  private final List<String> itemAliases; // 成员变量：项别名列表，用于为UNNEST展开的每一项指定列名

  //~ Constructors -----------------------------------------------------------

  @Deprecated // to be removed before 2.0 // 已弃用的构造方法：将在2.0版本前移除
  public Uncollect(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法：创建Uncollect实例（已弃用版本）
      RelNode child) { // 参数：cluster-关系优化集群；traitSet-特征集合；child-子关系表达式
    this(cluster, traitSet, child, false, Collections.emptyList()); // 调用完整构造方法，withOrdinality默认为false，itemAliases为空列表
  }

  /** Creates an Uncollect. // 方法说明：创建一个Uncollect实例
   *
   * <p>Use {@link #create} unless you know what you're doing. */ // 建议使用create静态工厂方法，除非你明确知道自己在做什么
  @SuppressWarnings("method.invocation.invalid") // 抑制警告：忽略方法调用无效的警告（因为调用父类构造方法）
  public Uncollect(RelOptCluster cluster, RelTraitSet traitSet, RelNode input, // 构造方法：创建Uncollect实例（完整版本）
      boolean withOrdinality, List<String> itemAliases) { // 参数：cluster-关系优化集群；traitSet-特征集合；input-输入关系表达式；withOrdinality-是否包含序号列；itemAliases-项别名列表
    super(cluster, traitSet, input); // 调用父类SingleRel的构造方法，初始化集群、特征集合和输入
    this.withOrdinality = withOrdinality; // 初始化withOrdinality标志位
    this.itemAliases = ImmutableList.copyOf(itemAliases); // 将itemAliases转换为不可变列表，确保线程安全
    requireNonNull(deriveRowType(), "invalid child rowType"); // 验证派生的行类型不为空，否则抛出异常
  }

  /**
   * Creates an Uncollect by parsing serialized output. // 方法说明：通过解析序列化输出来创建Uncollect实例
   */
  public Uncollect(RelInput input) { // 构造方法：从序列化的RelInput创建Uncollect实例
    this(input.getCluster(), input.getTraitSet(), input.getInput(), // 调用完整构造方法，从RelInput中提取参数
        input.getBoolean("withOrdinality", false), Collections.emptyList()); // withOrdinality从RelInput获取，默认false；itemAliases为空列表
  }

  /**
   * Creates an Uncollect. // 方法说明：创建Uncollect实例（推荐的静态工厂方法）
   *
   * <p>Each field of the input relational expression must be an array or // 输入关系表达式的每个字段必须是数组或多集合类型
   * multiset.
   *
   * @param traitSet       Trait set // 参数说明：traitSet-关系表达式的特征集合
   * @param input          Input relational expression // 参数说明：input-输入关系表达式
   * @param withOrdinality Whether output should contain an ORDINALITY column // 参数说明：withOrdinality-输出是否包含ORDINALITY列
   * @param itemAliases    Aliases for the operand items // 参数说明：itemAliases-操作项的别名列表
   */
  public static Uncollect create( // 静态工厂方法：创建Uncollect实例
      RelTraitSet traitSet, // 参数：traitSet-关系表达式的特征集合
      RelNode input, // 参数：input-输入关系表达式
      boolean withOrdinality, // 参数：withOrdinality-输出是否包含ORDINALITY列
      List<String> itemAliases) { // 参数：itemAliases-操作项的别名列表
    final RelOptCluster cluster = input.getCluster(); // 从输入关系表达式中获取关系优化集群
    return new Uncollect(cluster, traitSet, input, withOrdinality, itemAliases); // 调用构造方法创建并返回Uncollect实例
  }

  //~ Methods ----------------------------------------------------------------

  @Override public RelWriter explainTerms(RelWriter pw) { // 方法说明：重写explainTerms方法，用于输出Uncollect的说明信息
    return super.explainTerms(pw) // 调用父类的explainTerms方法，获取基础说明信息
        .itemIf("withOrdinality", withOrdinality, withOrdinality); // 如果withOrdinality为true，则添加withOrdinality项到说明中
  }

  @Override public final RelNode copy(RelTraitSet traitSet, // 方法说明：重写copy方法，用于复制Uncollect节点
      List<RelNode> inputs) { // 参数：traitSet-新的特征集合；inputs-新的输入列表
    return copy(traitSet, sole(inputs)); // 调用单输入版本的copy方法，sole(inputs)从输入列表中提取唯一的输入
  }

  public RelNode copy(RelTraitSet traitSet, RelNode input) { // 方法说明：复制Uncollect节点（单输入版本）
    assert traitSet.containsIfApplicable(Convention.NONE); // 断言：特征集合如果适用，必须包含Convention.NONE（表示逻辑约定）
    return new Uncollect(getCluster(), traitSet, input, withOrdinality, itemAliases); // 创建并返回新的Uncollect实例，保持withOrdinality和itemAliases不变
  }

  @Override protected RelDataType deriveRowType() { // 方法说明：重写deriveRowType方法，派生Uncollect的行类型（输出行的数据类型）
    return deriveUncollectRowType(input, withOrdinality, itemAliases); // 调用静态方法deriveUncollectRowType计算并返回行类型
  }

  /**
   * Returns the row type returned by applying the 'UNNEST' operation to a // 方法说明：返回对关系表达式应用UNNEST操作后的行类型
   * relational expression.
   *
   * <p>Each column in the relational expression must be a multiset of // 关系表达式中的每一列必须是结构体的多集合或数组
   * structs or an array. The return type是组合每列展开的元素类型，如果withOrdinality为true，还会添加ORDINALITY列
   * element types from each column, plus an ORDINALITY column if {@code
   * withOrdinality}. If {@code itemAliases} is not empty, the element types // 如果itemAliases不为空，元素类型不会展开，每列元素作为一个整体输出（返回类型与输入类型的列类型相同）
   * would not expand, each column element outputs as a whole (the return
   * type has same column types as input type).
   */
  public static RelDataType deriveUncollectRowType(RelNode rel, // 静态方法：派生Uncollect的行类型
      boolean withOrdinality, List<String> itemAliases) { // 参数：rel-输入关系表达式；withOrdinality-是否包含ORDINALITY列；itemAliases-项别名列表
    RelDataType inputType = rel.getRowType(); // 获取输入关系表达式的行类型
    assert inputType.isStruct() : inputType + " is not a struct"; // 断言：输入类型必须是结构体类型

    boolean requireAlias = !itemAliases.isEmpty(); // 判断是否需要别名（itemAliases不为空时为true）
    assert !requireAlias || itemAliases.size() == inputType.getFieldCount(); // 断言：如果需要别名，别名数量必须与输入字段数量相等

    final List<RelDataTypeField> fields = inputType.getFieldList(); // 获取输入类型的所有字段列表
    final RelDataTypeFactory typeFactory = rel.getCluster().getTypeFactory(); // 获取类型工厂，用于创建新类型
    final RelDataTypeFactory.Builder builder = typeFactory.builder(); // 创建类型构建器，用于构建输出行类型

    if (fields.size() == 1 // 如果只有一个字段
        && fields.get(0).getType().getSqlTypeName() == SqlTypeName.ANY) { // 且该字段的类型是ANY（未知类型）
      // Component type is unknown to Uncollect, build a row type with input column name // 组件类型对Uncollect未知，构建一个使用输入列名和ANY类型的行类型
      // and Any type.
      return builder // 返回构建的类型
          .add(requireAlias ? itemAliases.get(0) : fields.get(0).getName(), SqlTypeName.ANY) // 添加字段：如果需要别名使用别名，否则使用原字段名，类型为ANY
          .nullable(true) // 设置字段可为空
          .build(); // 构建并返回类型
    }

    for (int i = 0; i < fields.size(); i++) { // 遍历所有输入字段
      RelDataTypeField field = fields.get(i); // 获取当前字段
      if (field.getType() instanceof MapSqlType) { // 如果字段类型是MAP类型
        // This code is similar to SqlUnnestOperator::inferReturnType. // 这段代码类似于SqlUnnestOperator::inferReturnType的实现
        MapSqlType mapType = (MapSqlType) field.getType(); // 将类型转换为MapSqlType
        builder.add(SqlUnnestOperator.MAP_KEY_COLUMN_NAME, mapType.getKeyType()); // 添加KEY列，使用MAP的键类型
        builder.add(SqlUnnestOperator.MAP_VALUE_COLUMN_NAME, mapType.getValueType()); // 添加VALUE列，使用MAP的值类型
      } else { // 如果不是MAP类型
        RelDataType componentType = field.getType().getComponentType(); // 获取字段的组件类型（数组或多集合的元素类型）
        if (null == componentType) { // 如果组件类型为空（不是数组或多集合）
          throw RESOURCE.unnestArgument().ex(); // 抛出异常：UNNEST参数必须是数组或多集合
        }
        boolean isNullable = componentType.isNullable(); // 获取组件类型是否可为空
        if (requireAlias) { // 如果需要别名
          builder.add(itemAliases.get(i), componentType); // 使用别名作为字段名，添加组件类型
        } else if (componentType.isStruct()) { // 如果组件类型是结构体类型（有多个字段）
          for (RelDataTypeField fieldInfo : componentType.getFieldList()) { // 遍历结构体的所有字段
            RelDataType fieldType = fieldInfo.getType(); // 获取字段类型
            if (isNullable) { // 如果组件类型可为空
              fieldType = typeFactory.enforceTypeWithNullability(fieldType, true); // 强制设置字段类型可为空
            }
            builder.add(fieldInfo.getName(), fieldType); // 添加字段，使用原字段名和类型
          }
        } else { // 如果组件类型不是结构体（是基本类型）
          // Element type is not a record, use the field name of the element directly // 元素类型不是记录，直接使用元素的字段名
          builder.add(field.getName(), componentType); // 添加字段，使用原字段名和组件类型
        }
      }
    }

    if (withOrdinality) { // 如果需要ORDINALITY列
      builder.add(SqlUnnestOperator.ORDINALITY_COLUMN_NAME, // 添加ORDINALITY列
          SqlTypeName.INTEGER); // 类型为INTEGER
    }
    return builder.build(); // 构建并返回最终的行类型
  }
}
