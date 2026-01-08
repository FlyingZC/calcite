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
package org.apache.calcite.adapter.enumerable; // 定义包名,该类位于可枚举适配器包中,用于实现基于LINQ的可枚举调用约定

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂接口,用于创建和管理Java类型
import org.apache.calcite.config.CalciteSystemProperty; // 导入Calcite系统属性配置类,用于读取系统配置
import org.apache.calcite.interpreter.Row; // 导入Row类,用于表示解释器中的行数据
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口,表示可枚举的数据集合
import org.apache.calcite.linq4j.Queryable; // 导入Queryable接口,表示可查询的数据源
import org.apache.calcite.linq4j.function.Function1; // 导入Function1函数式接口,表示单参数函数
import org.apache.calcite.linq4j.tree.Blocks; // 导入Blocks工具类,用于创建代码块表达式
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression基类,表示LINQ表达式树中的表达式节点
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions工具类,用于创建各种表达式
import org.apache.calcite.linq4j.tree.MethodCallExpression; // 导入方法调用表达式类,表示方法调用
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入参数表达式类,表示方法参数
import org.apache.calcite.linq4j.tree.Primitive; // 导入Primitive工具类,用于处理基本类型
import org.apache.calcite.linq4j.tree.Types; // 导入Types工具类,用于类型相关的操作
import org.apache.calcite.plan.DeriveMode; // 导入推导模式枚举,控制特性如何从子节点推导
import org.apache.calcite.plan.RelOptCluster; // 导入关系表达式集群类,包含优化器的共享资源
import org.apache.calcite.plan.RelOptTable; // 导入关系优化表接口,表示优化器中的表
import org.apache.calcite.plan.RelOptUtil; // 导入关系优化工具类,提供各种辅助方法
import org.apache.calcite.plan.RelTraitSet; // 导入关系特性集合类,表示关系节点的特性集合
import org.apache.calcite.rel.RelCollationTraitDef; // 导入排序特性定义类,定义排序特性
import org.apache.calcite.rel.RelNode; // 导入关系节点接口,表示关系代数表达式
import org.apache.calcite.rel.core.TableScan; // 导入TableScan基类,表示表扫描操作
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口,表示关系类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入关系数据类型字段类,表示字段
import org.apache.calcite.schema.FilterableTable; // 导入可过滤表接口,支持过滤操作的表
import org.apache.calcite.schema.ProjectableFilterableTable; // 导入可投影可过滤表接口,支持投影和过滤的表
import org.apache.calcite.schema.QueryableTable; // 导入可查询表接口,支持LINQ查询的表
import org.apache.calcite.schema.ScannableTable; // 导入可扫描表接口,支持扫描操作的表
import org.apache.calcite.schema.StreamableTable; // 导入可流式表接口,支持流式处理的表
import org.apache.calcite.schema.Table; // 导入Table接口,表示数据表
import org.apache.calcite.schema.TransientTable; // 导入临时表接口,表示临时表
import org.apache.calcite.sql.SqlExplainLevel; // 导入SQL解释级别枚举,控制解释输出的详细程度
import org.apache.calcite.util.BuiltInMethod; // 导入内置方法类,包含Calcite内置方法的引用

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解,用于标记可能为null的值

import java.lang.reflect.Type; // 导入Java反射的Type接口,表示类型
import java.util.ArrayList; // 导入ArrayList动态数组类
import java.util.List; // 导入List集合接口

import static org.apache.calcite.linq4j.tree.Types.toClass; // 静态导入toClass方法,用于将Type转换为Class

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法,用于检查非空

/** Implementation of {@link org.apache.calcite.rel.core.TableScan} in
 * {@link org.apache.calcite.adapter.enumerable.EnumerableConvention enumerable calling convention}. */
// 类注释:这是TableScan在可枚举调用约定下的实现类
// TableScan是关系代数中扫描表的操作,EnumerableConvention表示使用LINQ的可枚举方式来执行
// 该类负责将表扫描操作转换为可执行的LINQ表达式树,实现数据表的读取
public class EnumerableTableScan
    extends TableScan // 继承TableScan基类,表示这是一个表扫描操作
    implements EnumerableRel { // 实现EnumerableRel接口,表示支持可枚举调用约定
  private final Class elementType; // 成员变量:元素类型,表示表中每一行数据的Java类型(如Object[].class、自定义类等)

  /** Creates an EnumerableTableScan.
   *
   * <p>Use {@link #create} unless you know what you are doing. */
  // 构造方法注释:创建一个EnumerableTableScan实例
  // 建议使用create静态工厂方法而不是直接使用此构造方法,除非你清楚自己在做什么
  public EnumerableTableScan(RelOptCluster cluster, RelTraitSet traitSet,
      RelOptTable table, Class elementType) { // 参数:cluster-关系集群,traitSet-特性集合,table-要扫描的表,elementType-元素类型
    super(cluster, traitSet, ImmutableList.of(), table); // 调用父类TableScan的构造方法,传入集群、特性集、空输入列表和表
    assert getConvention() instanceof EnumerableConvention; // 断言:确保调用约定是EnumerableConvention类型
    this.elementType = elementType; // 保存元素类型到成员变量
    assert canHandle(table) // 断言:确保该表可以被EnumerableTableScan处理
        : "EnumerableTableScan can't implement " + table + ", see EnumerableTableScan#canHandle"; // 如果不能处理则抛出断言错误
  }

  /**
   * Code snippet to demonstrate how to generate IndexScan on demand
   * by passing required collation through TableScan.
   *
   * @return IndexScan if there is index available on collation keys
   */
  // 方法注释:演示如何通过TableScan传递所需的排序特性来按需生成索引扫描
  // 这是一个预留的扩展点,用于支持基于索引的扫描优化
  // 参数:required-所需的特性集合,包含排序要求
  // 返回值:如果有索引可用则返回IndexScan,当前实现返回null
  @Override public @Nullable RelNode passThrough(final RelTraitSet required) { // 重写passThrough方法,用于特性传递
/*
    keys = required.getCollation().getKeys(); // 获取排序键
    if (table has index on keys) { // 如果表在这些键上有索引
      direction = forward or backward; // 确定扫描方向(正向或反向)
      return new IndexScan(table, indexInfo, direction); // 返回索引扫描节点
    }
*/
    return null; // 当前实现返回null,表示不进行索引扫描
  }

  @Override public DeriveMode getDeriveMode() { // 重写getDeriveMode方法,返回特性推导模式
    return DeriveMode.PROHIBITED; // 返回PROHIBITED模式,表示禁止从子节点推导特性
  } // 对于表扫描操作,不需要从子节点推导特性,因为它没有子节点

  /** Creates an EnumerableTableScan. */
  // 静态工厂方法注释:创建一个EnumerableTableScan实例,这是推荐的创建方式
  // 参数:cluster-关系集群,relOptTable-关系优化表
  // 返回值:创建的EnumerableTableScan实例
  public static EnumerableTableScan create(RelOptCluster cluster,
      RelOptTable relOptTable) { // 参数定义
    final Table table = relOptTable.unwrap(Table.class); // 从RelOptTable中解包获取Table对象
    Class elementType = EnumerableTableScan.deduceElementType(table); // 推断表的元素类型(Object[].class或自定义类)
    final RelTraitSet traitSet = // 创建特性集合
        cluster.traitSetOf(EnumerableConvention.INSTANCE) // 从集群创建特性集,指定使用EnumerableConvention调用约定
            .replaceIfs(RelCollationTraitDef.INSTANCE, () -> { // 如果表有排序统计信息,则替换排序特性
              if (table != null) { // 如果表不为空
                return table.getStatistic().getCollations(); // 返回表的排序统计信息
              }
              return ImmutableList.of(); // 否则返回空列表
            });
    return new EnumerableTableScan(cluster, traitSet, relOptTable, elementType); // 创建并返回EnumerableTableScan实例
  }

  /** Returns whether EnumerableTableScan can generate code to handle a
   * particular variant of the Table SPI.
   *
   * @deprecated remove before Calcite 2.0
   */
  // 方法注释:判断EnumerableTableScan是否能够生成代码来处理特定类型的Table SPI
  // 参数:table-要检查的表对象
  // 返回值:如果可以处理返回true,否则返回false
  // @deprecated注解:标记为过时,计划在Calcite 2.0之前移除
  @Deprecated
  public static boolean canHandle(Table table) { // 静态方法,检查表类型是否受支持
    if (table instanceof TransientTable) { // 如果表是临时表类型
      // CALCITE-3673: TransientTable can't be implemented with Enumerable
      // 注释:临时表无法用Enumerable实现,因为临时表的生命周期管理机制与可枚举调用约定不兼容
      return false; // 返回false,表示不能处理
    }
    // See org.apache.calcite.prepare.RelOptTableImpl.getClassExpressionFunction
    // 注释:参考RelOptTableImpl的getClassExpressionFunction方法,了解如何处理不同类型的表
    return table instanceof QueryableTable // 检查是否是可查询表(支持LINQ查询)
        || table instanceof FilterableTable // 或可过滤表(支持过滤操作)
        || table instanceof ProjectableFilterableTable // 或可投影可过滤表(支持投影和过滤)
        || table instanceof ScannableTable; // 或可扫描表(支持扫描操作)
  }

  /** Returns whether EnumerableTableScan can generate code to handle a
   * particular variant of the Table SPI. */
  // 方法注释:判断EnumerableTableScan是否能够生成代码来处理特定类型的RelOptTable
  // 这个方法比上一个方法更全面,不仅检查表类型,还检查字段类型是否受支持
  // 参数:relOptTable-关系优化表
  // 返回值:如果可以处理返回true,否则返回false
  public static boolean canHandle(RelOptTable relOptTable) { // 重载方法,接受RelOptTable参数
    Table table = relOptTable.unwrap(Table.class); // 从RelOptTable中解包获取Table对象
    if (table != null && !canHandle(table)) { // 如果表不为空且不能处理
      return false; // 返回false
    }
    boolean supportArray = CalciteSystemProperty.ENUMERABLE_ENABLE_TABLESCAN_ARRAY.value(); // 读取系统配置:是否支持数组类型
    boolean supportMap = CalciteSystemProperty.ENUMERABLE_ENABLE_TABLESCAN_MAP.value(); // 读取系统配置:是否支持Map类型
    boolean supportMultiset = CalciteSystemProperty.ENUMERABLE_ENABLE_TABLESCAN_MULTISET.value(); // 读取系统配置:是否支持多重集类型
    if (supportArray && supportMap && supportMultiset) { // 如果所有类型都支持
      return true; // 直接返回true
    }
    // Struct fields are not supported in EnumerableTableScan
    // 注释:EnumerableTableScan不支持结构化字段(嵌套的复杂类型)
    for (RelDataTypeField field : relOptTable.getRowType().getFieldList()) { // 遍历表的所有字段
      boolean unsupportedType = false; // 标记是否是不支持的类型
      switch (field.getType().getSqlTypeName()) { // 根据字段的SQL类型名进行判断
      case ARRAY: // 如果是数组类型
        unsupportedType = supportArray; // 如果不支持数组则标记为不支持
        break;
      case MAP: // 如果是Map类型
        unsupportedType = supportMap; // 如果不支持Map则标记为不支持
        break;
      case MULTISET: // 如果是多重集类型
        unsupportedType = supportMultiset; // 如果不支持多重集则标记为不支持
        break;
      default: // 其他类型
        break; // 不做处理
      }
      if (unsupportedType) { // 如果有不支持的类型
        return false; // 返回false
      }
    }
    return true; // 所有检查都通过,返回true
  }

  public static Class deduceElementType(@Nullable Table table) { // 静态方法:推断表的元素类型
    if (table instanceof QueryableTable) { // 如果表是可查询表
      final QueryableTable queryableTable = (QueryableTable) table; // 强制转换为QueryableTable
      final Type type = queryableTable.getElementType(); // 获取表的元素类型
      if (type instanceof Class) { // 如果类型是Class对象
        return (Class) type; // 返回该Class,可能是自定义类
      } else { // 如果类型不是Class(比如TypeVariable等)
        return Object[].class; // 返回Object[].class作为默认类型
      }
    } else if (table instanceof ScannableTable // 如果是可扫描表
        || table instanceof FilterableTable // 或可过滤表
        || table instanceof ProjectableFilterableTable // 或可投影可过滤表
        || table instanceof StreamableTable) { // 或可流式表
      return Object[].class; // 这些类型都使用Object[].class作为元素类型,表示每行是一个对象数组
    } else { // 其他表类型
      return Object.class; // 返回Object.class作为默认类型
    }
  }

  public static JavaRowFormat deduceFormat(RelOptTable table) { // 静态方法:推断Java行格式
    final Class elementType = deduceElementType(table.unwrapOrThrow(Table.class)); // 获取表的元素类型
    return elementType == Object[].class // 如果元素类型是Object[]
        ? JavaRowFormat.ARRAY // 返回ARRAY格式,表示每行是一个数组
        : JavaRowFormat.CUSTOM; // 否则返回CUSTOM格式,表示每行是自定义对象
  }

  private Expression getExpression(PhysType physType) { // 私有方法:获取表的表达式
    final Expression expression = table.getExpression(Queryable.class); // 从表获取Queryable类型的表达式
    if (expression == null) { // 如果表达式为null
      throw new IllegalStateException( // 抛出非法状态异常
          "Unable to implement " + RelOptUtil.toString(this, SqlExplainLevel.ALL_ATTRIBUTES) // 错误信息:无法实现该节点
          + ": " + table + ".getExpression(Queryable.class) returned null"); // 因为表的getExpression方法返回了null
    }
    final Expression expression2 = toEnumerable(expression); // 将表达式转换为Enumerable类型
    assert Types.isAssignableFrom(Enumerable.class, expression2.getType()); // 断言:确保转换后的类型是Enumerable
    return toRows(physType, expression2); // 将表达式转换为行格式并返回
  }

  private static Expression toEnumerable(Expression expression) { // 私有静态方法:将表达式转换为Enumerable类型
    final Type type = expression.getType(); // 获取表达式的类型
    if (Types.isArray(type)) { // 如果类型是数组
      if (requireNonNull(toClass(type).getComponentType()).isPrimitive()) { // 如果是基本类型数组(如int[])
        expression = // 调用asList方法将基本类型数组转换为List
            Expressions.call(BuiltInMethod.AS_LIST.method, expression);
      }
      return Expressions.call(BuiltInMethod.AS_ENUMERABLE.method, expression); // 调用asEnumerable方法转换为Enumerable
    } else if (Types.isAssignableFrom(Iterable.class, type) // 如果类型是Iterable或其子类
        && !Types.isAssignableFrom(Enumerable.class, type)) { // 但不是Enumerable
      return Expressions.call(BuiltInMethod.AS_ENUMERABLE2.method, // 调用asEnumerable方法将Iterable转换为Enumerable
          expression);
    } else if (Types.isAssignableFrom(Queryable.class, type)) { // 如果类型是Queryable或其子类
      // Queryable extends Enumerable, but it's too "clever", so we call
      // Queryable.asEnumerable so that operations such as take(int) will be
      // evaluated directly.
      // 注释:Queryable继承自Enumerable,但它"太聪明了"(会延迟执行操作),
      // 所以我们调用Queryable.asEnumerable,使take(int)等操作直接求值
      return Expressions.call(expression, // 调用Queryable的asEnumerable方法
          BuiltInMethod.QUERYABLE_AS_ENUMERABLE.method);
    }
    return expression; // 如果已经是Enumerable类型,直接返回
  }

  private Expression toRows(PhysType physType, Expression expression) { // 私有方法:将表达式转换为行格式
    if (physType.getFormat() == JavaRowFormat.SCALAR // 如果物理类型格式是SCALAR(标量)
        && Object[].class.isAssignableFrom(elementType) // 且元素类型是Object[]
        && getRowType().getFieldCount() == 1 // 且只有1个字段
        && (table.unwrap(ScannableTable.class) != null // 且表是可扫描表
            || table.unwrap(FilterableTable.class) != null // 或可过滤表
            || table.unwrap(ProjectableFilterableTable.class) != null)) { // 或可投影可过滤表
      return Expressions.call(BuiltInMethod.SLICE0.method, expression); // 调用slice0方法提取数组的第一个元素
    }
    JavaRowFormat oldFormat = format(); // 获取当前的行格式
    if (physType.getFormat() == oldFormat && !hasCollectionField(getRowType())) { // 如果格式匹配且没有集合字段
      return expression; // 直接返回表达式,不需要转换
    }
    final ParameterExpression row_ = // 创建一个参数表达式,表示行数据
        Expressions.parameter(elementType, "row"); // 参数类型为elementType,名称为"row"
    final int fieldCount = table.getRowType().getFieldCount(); // 获取字段数量
    List<Expression> expressionList = new ArrayList<>(fieldCount); // 创建表达式列表,初始容量为字段数
    for (int i = 0; i < fieldCount; i++) { // 遍历每个字段
      expressionList.add(fieldExpression(row_, i, physType, oldFormat)); // 为每个字段创建表达式并添加到列表
    }
    return Expressions.call(expression, // 调用select方法进行投影转换
        BuiltInMethod.SELECT.method, // 使用内置的select方法
        Expressions.lambda(Function1.class, physType.record(expressionList), // 创建lambda表达式,将字段列表转换为记录
            row_)); // lambda的参数是row_
  }

  private Expression fieldExpression(ParameterExpression row_, int i,

        PhysType physType, JavaRowFormat format) { // 私有方法:创建字段表达式

      final Expression e = // 获取字段表达式

          format.field(row_, i, null, physType.getJavaFieldType(i)); // 使用format.field方法从行中提取第i个字段

      final RelDataType relFieldType = // 获取字段的关系数据类型

          physType.getRowType().getFieldList().get(i).getType(); // 从物理类型的行类型中获取第i个字段的类型

      switch (relFieldType.getSqlTypeName()) { // 根据SQL类型名进行分支处理

      case ARRAY: // 如果是数组类型

      case MULTISET: // 或多重集类型

        final RelDataType fieldType = // 获取元素类型

            requireNonNull(relFieldType.getComponentType(), // 获取组件类型

                () -> "relFieldType.getComponentType() for " + relFieldType); // 如果为null则抛出异常

        if (fieldType.isStruct()) { // 如果元素类型是结构化类型(嵌套结构)

          // We can't represent a multiset or array as a List<Employee>, because

          // the consumer does not know the element type.

          // The standard element type is List.

          // We need to convert to a List<List>.

          // 注释:我们无法将多重集或数组表示为List<Employee>,

          // 因为消费者不知道元素类型。标准元素类型是List。我们需要转换为List<List>。

          final JavaTypeFactory typeFactory = // 获取Java类型工厂

                  (JavaTypeFactory) getCluster().getTypeFactory(); // 从集群获取类型工厂并转换

          final PhysType elementPhysType = // 创建元素的物理类型

              PhysTypeImpl.of(typeFactory, fieldType, JavaRowFormat.CUSTOM); // 使用CUSTOM格式

          final MethodCallExpression e2 = // 调用asEnumerable方法转换为Enumerable

              Expressions.call(BuiltInMethod.AS_ENUMERABLE2.method, e); // 使用内置方法

          final Expression e3 = elementPhysType.convertTo(e2, JavaRowFormat.LIST); // 转换为LIST格式

          return Expressions.call(e3, BuiltInMethod.ENUMERABLE_TO_LIST.method); // 调用toList方法转换为List

        } else { // 如果元素类型不是结构化类型

          return e; // 直接返回字段表达式

        }

      default: // 其他类型

        return e; // 直接返回字段表达式

      }

    }

  private JavaRowFormat format() { // 私有方法:推断Java行格式
    int fieldCount = getRowType().getFieldCount(); // 获取字段数量
    if (fieldCount == 0) { // 如果没有字段
      return JavaRowFormat.LIST; // 返回LIST格式
    }
    if (Object[].class.isAssignableFrom(elementType)) { // 如果元素类型是Object[]
      return fieldCount == 1 ? JavaRowFormat.SCALAR : JavaRowFormat.ARRAY; // 单字段返回SCALAR,多字段返回ARRAY
    }
    if (Row.class.isAssignableFrom(elementType)) { // 如果元素类型是Row
      return JavaRowFormat.ROW; // 返回ROW格式
    }
    if (fieldCount == 1 && (Object.class == elementType // 如果只有一个字段且元素类型是Object
          || Primitive.is(elementType) // 或基本类型
          || Number.class.isAssignableFrom(elementType) // 或Number类型
          || String.class == elementType)) { // 或String类型
      return JavaRowFormat.SCALAR; // 返回SCALAR格式(标量)
    }
    return JavaRowFormat.CUSTOM; // 其他情况返回CUSTOM格式(自定义类型)
  }

  private static boolean hasCollectionField(RelDataType rowType) { // 私有静态方法:检查行类型是否包含集合字段
    for (RelDataTypeField field : rowType.getFieldList()) { // 遍历所有字段
      switch (field.getType().getSqlTypeName()) { // 根据字段的SQL类型名判断
      case ARRAY: // 如果是数组类型
      case MULTISET: // 或多重集类型
        return true; // 返回true,表示包含集合字段
      default: // 其他类型
        break; // 继续检查下一个字段
      }
    }
    return false; // 所有字段检查完毕,没有集合字段,返回false
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法,创建节点的副本
    return new EnumerableTableScan(getCluster(), traitSet, table, elementType); // 创建新的EnumerableTableScan实例
  } // 参数:traitSet-新的特性集合,inputs-输入节点列表(对于TableScan应该为空)

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 重写implement方法,实现可执行代码生成
    // Note that representation is ARRAY. This assumes that the table
    // returns a Object[] for each record. Actually a Table<T> can
    // return any type T. And, if it is a JdbcTable, we'd like to be
    // able to generate alternate accessors that return e.g. synthetic
    // records {T0 f0; T1 f1; ...} and don't box every primitive value.
    // 注释:注意表示格式是ARRAY。这假设表为每条记录返回一个Object[]。
    // 实际上Table<T>可以返回任何类型T。如果是JdbcTable,我们希望能够
    // 生成替代的访问器,返回例如合成记录{T0 f0; T1 f1; ...},而不对每个基本值进行装箱。
    final PhysType physType = // 创建物理类型,用于代码生成
        PhysTypeImpl.of( // 使用PhysTypeImpl工厂方法
            implementor.getTypeFactory(), // 从实现器获取类型工厂
            getRowType(), // 获取行类型
            format()); // 获取行格式
    final Expression expression = getExpression(physType); // 获取表达式树,表示如何读取数据
    return implementor.result(physType, Blocks.toBlock(expression)); // 返回实现结果,包含物理类型和代码块
  }
} // 类结束
