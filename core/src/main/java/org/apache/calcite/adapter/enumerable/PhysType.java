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
package org.apache.calcite.adapter.enumerable; // 包声明：该接口位于Enumerable适配器包中，用于处理可枚举数据的物理类型映射

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂，用于创建和管理Java类型
import org.apache.calcite.linq4j.tree.Expression; // 导入表达式类，用于构建LINQ表达式树
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入参数表达式类，表示lambda表达式中的参数
import org.apache.calcite.rel.RelCollation; // 导入关系排序类，定义字段的排序规则
import org.apache.calcite.rel.RelFieldCollation; // 导入关系字段排序类，定义单个字段的排序方向和空值处理
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型类，表示Calcite的逻辑类型系统
import org.apache.calcite.util.Pair; // 导入Pair工具类，用于存储键值对

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，标记可能为null的参数或返回值

import java.lang.reflect.Type; // 导入Java反射Type接口，表示Java类型
import java.util.List; // 导入List接口，用于存储字段列表

/**
 * Physical type of a row. // 类作用：表示行的物理类型，封装了逻辑SQL类型到物理Java类型的映射关系
 *
 * <p>Consists of the SQL row type (returned by {@link #getRowType()}), the Java
 * type of the row (returned by {@link #getJavaRowType()}), and methods to
 * generate expressions to access fields, generate records, and so forth.
 * Together, the records encapsulate how the logical type maps onto the physical
 * type.
 * // 详细说明：该接口由三部分组成：(1)SQL行类型，由getRowType()返回，描述逻辑数据模型中的字段类型；
 * // (2)Java行类型，由getJavaRowType()返回，描述物理实现中使用的Java类型（如Object[]、自定义POJO等）；
 * // (3)一系列方法，用于生成访问字段、创建记录、类型转换等表达式。这些方法共同封装了从逻辑类型到物理类型的映射机制，
 * // 使得Calcite能够将关系代数操作转换为可执行的Java代码（通过LINQ表达式树）。这是Calcite Enumerable适配器的核心抽象，
 * // 负责处理类型系统的转换和代码生成。
 */
public interface PhysType { // 接口定义：PhysType接口定义了物理类型的规范，具体实现由PhysTypeImpl等类提供
  /** Returns the Java type (often a Class) that represents a row. For
   * example, in one row format, always returns {@code Object[].class}. */
  // 方法作用：返回表示一行的Java类型（通常是Class对象），如Object[].class、Employee.class等
  // 返回值说明：返回Type类型，可以是Class对象或ParameterizedType等，表示行的物理存储类型
  Type getJavaRowType(); // 方法签名：获取Java行类型，确定数据在物理层如何表示

  /**
   * Returns the Java class that is used to store the field with the given
   * ordinal.
   *
   * <p>For instance, when the java row type is {@code Object[]}, the java
   * field type is {@code Object} even if the field is not nullable.
   */
  // 方法作用：返回用于存储指定序号字段的Java类型，注意这是物理存储类型而非逻辑类型
  // 参数说明：field - 字段的序号（从0开始），标识要查询的字段位置
  // 返回值说明：返回Type类型，表示该字段在物理存储中的Java类型，如Object.class、Integer.class等
  // 重要说明：当Java行类型是Object[]时，即使字段不为空，Java字段类型也是Object，因为数组元素类型统一
  Type getJavaFieldType(int field); // 方法签名：获取指定字段的Java存储类型

  /** Returns the type factory. */
  // 方法作用：返回类型工厂，用于创建和管理Calcite的类型系统
  // 返回值说明：返回JavaTypeFactory实例，提供类型创建、类型转换等核心功能
  // 使用场景：在代码生成过程中，需要类型工厂来获取类型信息、创建新类型、进行类型检查等
  JavaTypeFactory getTypeFactory(); // 方法签名：获取类型工厂对象

  /** Returns the physical type of a field. */
  // 方法作用：返回指定序号字段的物理类型，返回的PhysType可以进一步查询该字段的类型信息
  // 参数说明：ordinal - 字段的序号（从0开始）
  // 返回值说明：返回PhysType实例，封装了该字段的物理类型信息
  // 使用场景：当需要获取字段的详细类型信息或对字段进行进一步类型操作时使用
  PhysType field(int ordinal); // 方法签名：获取字段的物理类型

  /** Returns the physical type of a given field's component type. */
  // 方法作用：返回指定字段组件类型的物理类型，主要用于数组类型或复杂类型的元素类型
  // 参数说明：field - 字段的序号
  // 返回值说明：返回PhysType实例，表示该字段内部元素的物理类型
  // 使用场景：例如字段是INTEGER[]数组类型，此方法返回INTEGER类型的PhysType；用于处理嵌套类型或集合类型
  PhysType component(int field); // 方法签名：获取字段组件类型的物理类型

  /** Returns the SQL row type. */
  // 方法作用：返回SQL行类型，这是Calcite逻辑层的数据类型描述
  // 返回值说明：返回RelDataType实例，包含所有字段的逻辑类型信息（名称、类型、是否可空等）
  // 重要说明：SQL类型与Java类型的区别：SQL类型是逻辑层抽象，Java类型是物理层实现；PhysType负责两者间的转换
  RelDataType getRowType(); // 方法签名：获取SQL行类型

  /** Returns the Java class of the field with the given ordinal. */
  // 方法作用：返回指定序号字段的Java类（Class对象），比getJavaFieldType()更具体
  // 参数说明：field - 字段的序号
  // 返回值说明：返回Class<?>对象，表示字段的Java类，如Integer.class、String.class等
  // 与getJavaFieldType的区别：本方法总是返回Class对象，而getJavaFieldType可能返回ParameterizedType等复杂类型
  Class fieldClass(int field); // 方法签名：获取字段的Java类

  /** Returns whether a given field allows null values. */
  // 方法作用：判断指定字段是否允许null值，用于空值检查和优化
  // 参数说明：index - 字段的序号
  // 返回值说明：返回true表示字段可为空，false表示字段不可为空
  // 使用场景：在代码生成时，根据此信息决定是否需要添加null检查，优化生成的代码
  boolean fieldNullable(int index); // 方法签名：判断字段是否可空

  /** Generates a reference to a given field in an expression.
   *
   * <p>For example given {@code expression=employee} and {@code field=2},
   * generates
   *
   * <blockquote><pre>{@code employee.deptno}</pre></blockquote>
   *
   * @param expression Expression
   * @param field Ordinal of field
   * @return Expression to access the field of the expression
   */
  // 方法作用：生成访问表达式中指定字段的引用表达式，用于字段访问代码生成
  // 参数说明：expression - 表达式对象，表示要访问的对象（如employee变量）
  // 参数说明：field - 字段序号，标识要访问的字段位置
  // 返回值说明：返回Expression对象，表示字段访问表达式（如employee.deptno）
  // 示例说明：如果expression是"employee"变量，field是2，则生成"employee.deptno"表达式
  Expression fieldReference(Expression expression, int field); // 方法签名：生成字段引用表达式

  /** Generates a reference to a given field in an expression.
   *
   * <p>This method optimizes for the target存储类型（即避免不必要的类型转换）
   *
   * <p>For example given {@code expression=employee} and {@code field=2},
   * generates
   *
   * <blockquote><pre>{@code employee.deptno}</pre></blockquote>
   *
   * @param expression Expression
   * @param field Ordinal of field
   * @param storageType optional hint for storage class
   * @return Expression to access the field of the expression
   */
  // 方法作用：生成字段引用表达式，针对目标存储类型进行优化，避免不必要的类型转换
  // 参数说明：expression - 表达式对象，表示要访问的对象
  // 参数说明：field - 字段序号
  // 参数说明：storageType - 可选参数，提示目标存储类型，用于优化表达式生成
  // 返回值说明：返回Expression对象，表示优化后的字段访问表达式
  // 优化原理：如果storageType与字段实际类型匹配，则省略类型转换，提高性能
  Expression fieldReference(Expression expression, int field, // 方法签名：生成优化的字段引用表达式
      @Nullable Type storageType); // 可选的存储类型提示参数

  /** Generates an accessor function for a given list of fields.  The resulting
   * object is a {@link List} (implementing {@link Object#hashCode()} and
   * {@link Object#equals(Object)} per that interface) and also implements
   * {@link Comparable}.
   *
   * <p>For example:
   *
   * <blockquote><pre>
   * new Function1&lt;Employee, Object[]&gt; {
   *    public Object[] apply(Employee v1) {
   *        return FlatLists.of(v1.&lt;fieldN&gt;, v1.&lt;fieldM&gt;);
   *    }
   * }</pre></blockquote>
   */
  // 方法作用：为给定字段列表生成访问器函数表达式，该函数从输入对象中提取指定字段并返回List
  // 参数说明：fields - 字段序号列表，指定要提取的字段位置
  // 返回值说明：返回Expression对象，表示一个函数表达式，该函数接受输入对象，返回包含指定字段值的List
  // 返回对象特性：返回的List实现了hashCode()、equals()和Comparable接口，可用于比较和哈希
  // 示例说明：生成一个Function1<Employee, Object[]>，提取Employee的fieldN和fieldM字段，返回FlatLists.of(v1.fieldN, v1.fieldM)
  Expression generateAccessor(List<Integer> fields); // 方法签名：生成字段访问器函数表达式

  /** Similar to {@link #generateAccessor(List)}, but if one of the fields is <code>null</code>,
   * it will return <code>null</code>.
   *
   * <p>For example:
   *
   * <blockquote><pre>
   * new Function1&lt;Employee, Object[]&gt; {
   *    public Object[] apply(Employee v1) {
   *        return v1.&lt;fieldN&gt; == null
   *            ? null
   *            : v1.&lt;fieldM&gt; == null
   *                ? null
   *                : FlatLists.of(v1.&lt;fieldN&gt;, v1.&lt;fieldM&gt;);
   *    }
   * }</pre></blockquote>
   */
  // 方法作用：生成字段访问器函数，与generateAccessor类似，但如果任一字段为null，则整个结果返回null
  // 参数说明：fields - 字段序号列表
  // 返回值说明：返回Expression对象，表示带有null检查的访问器函数
  // 与generateAccessor的区别：本方法会检查每个字段是否为null，任一字段为null时返回null，而不是包含null的List
  // 使用场景：用于GROUPING函数等需要严格非null的场景
  Expression generateAccessorWithoutNulls(List<Integer> fields); // 方法签名：生成严格非null的字段访问器

  /** Generates a selector for the given fields from an expression, with the
   * default row format. */
  // 方法作用：为给定字段生成选择器表达式，使用默认行格式，选择器用于从输入对象中提取指定字段
  // 参数说明：parameter - 参数表达式，表示lambda函数的输入参数
  // 参数说明：fields - 字段序号列表，指定要提取的字段
  // 返回值说明：返回Expression对象，表示lambda选择器表达式
  // 默认行格式：使用JavaRowFormat的默认格式（通常是ARRAY格式）
  Expression generateSelector( // 方法签名：生成字段选择器（使用默认行格式）
      ParameterExpression parameter, // lambda函数的输入参数
      List<Integer> fields); // 要选择的字段列表

  /** Generates a lambda expression that is a selector for the given fields from
   * an expression. */
  // 方法作用：生成lambda表达式作为选择器，从输入表达式中提取指定字段，使用指定的目标行格式
  // 参数说明：parameter - 参数表达式，表示lambda函数的输入参数
  // 参数说明：fields - 字段序号列表
  // 参数说明：targetFormat - 目标行格式，指定输出行的物理表示方式（如ARRAY、SCALAR等）
  // 返回值说明：返回Expression对象，表示lambda选择器表达式
  // 使用场景：在投影操作中，用于从输入行中选择部分字段并转换为指定格式
  Expression generateSelector( // 方法签名：生成指定格式的字段选择器lambda表达式
      ParameterExpression parameter, // lambda参数
      List<Integer> fields, // 字段列表
      JavaRowFormat targetFormat); // 目标行格式

  /** Generates a lambda expression that is a selector for the given fields from
   * an expression.
   *
   * <p>{@code usedFields} must be a subset of {@code fields}.
   * For each field, there is a corresponding indicator field.
   * If a field is used, its value is assigned and its indicator is left
   * {@code false}.
   * If a field is not used, its value is not assigned and its indicator is
   * set to {@code true};
   * This will become a value of 1 when {@code GROUPING(field)} is called.
   */
  // 方法作用：生成带有指示器字段的选择器lambda表达式，用于GROUPING聚合函数
  // 参数说明：parameter - 参数表达式，lambda函数的输入参数
  // 参数说明：fields - 所有字段的序号列表
  // 参数说明：usedFields - 实际使用的字段列表，必须是fields的子集
  // 参数说明：targetFormat - 目标行格式
  // 返回值说明：返回Expression对象，表示带有指示器的选择器
  // 指示器机制：每个字段对应一个指示器字段，如果字段被使用，指示器为false；如果字段未被使用，指示器为true
  // GROUPING函数：当调用GROUPING(field)时，如果字段未被聚合（指示器为true），返回1；否则返回0
  // 使用场景：CUBE和ROLLUP操作中，标识哪些字段参与了聚合
  Expression generateSelector( // 方法签名：生成带有GROUPING指示器的选择器
      ParameterExpression parameter, // lambda参数
      List<Integer> fields, // 所有字段
      List<Integer> usedFields, // 实际使用的字段
      JavaRowFormat targetFormat); // 目标行格式

  /** Generates a selector for the given fields from an expression.
   * Only used by EnumerableWindow. */
  // 方法作用：生成选择器，返回类型和表达式列表，专门用于EnumerableWindow窗口函数
  // 参数说明：parameter - 参数表达式
  // 参数说明：fields - 字段序号列表
  // 参数说明：targetFormat - 目标行格式
  // 返回值说明：返回Pair<Type, List<Expression>>，第一个元素是返回类型，第二个元素是表达式列表
  // 使用场景：窗口函数操作中，需要同时获取类型信息和访问表达式列表
  Pair<Type, List<Expression>> selector( // 方法签名：生成窗口函数选择器（返回类型和表达式列表）
      ParameterExpression parameter, // lambda参数
      List<Integer> fields, // 字段列表
      JavaRowFormat targetFormat); // 目标行格式

  /** Projects a given collection of fields from this input record, into
   * a particular preferred output format. The output format is optimized
   * if there are 0 or 1 fields. */
  // 方法作用：将输入记录投影到指定字段集合，转换为指定的输出格式，并进行优化
  // 参数说明：integers - 要投影的字段序号列表
  // 参数说明：format - 首选的输出行格式
  // 返回值说明：返回PhysType实例，表示投影后的物理类型
  // 优化说明：当字段数为0或1时，会自动优化输出格式（如使用SCALAR格式而非ARRAY格式）
  // 使用场景：在SELECT操作中，从输入行中选择部分字段并转换为指定格式
  PhysType project( // 方法签名：投影字段到指定格式
      List<Integer> integers, // 要投影的字段列表
      JavaRowFormat format); // 目标格式

  /** Projects a given collection of fields from this input record, optionally
   * with indicator fields, into a particular preferred output format.
   *
   * <p>The output format is optimized if there are 0 or 1 fields
   * and indicators are disabled.
   */
  // 方法作用：投影字段到指定格式，可选择是否包含指示器字段，并进行优化
  // 参数说明：integers - 要投影的字段序号列表
  // 参数说明：indicator - 是否包含指示器字段，true表示包含，false表示不包含
  // 参数说明：format - 首选的输出行格式
  // 返回值说明：返回PhysType实例，表示投影后的物理类型
  // 优化说明：当字段数为0或1且指示器禁用时，会优化输出格式
  // 指示器字段：用于GROUPING等聚合函数，标识字段是否被聚合
  PhysType project( // 方法签名：投影字段（支持指示器）
      List<Integer> integers, // 字段列表
      boolean indicator, // 是否包含指示器
      JavaRowFormat format); // 目标格式

  /** Returns a lambda to create a collation key and a comparator. The
   * comparator is sometimes null. */
  // 方法作用：生成排序键创建函数和比较器，用于ORDER BY操作
  // 参数说明：collations - 字段排序规则列表，定义每个字段的排序方向（ASC/DESC）和空值处理
  // 返回值说明：返回Pair<Expression, Expression>，第一个元素是排序键创建lambda，第二个元素是比较器（可能为null）
  // 排序键：从行中提取排序字段值的函数，用于生成排序键
  // 比较器：用于比较两个排序键的函数，可能为null（某些情况下直接使用Comparable）
  // 使用场景：在排序操作中，生成用于比较和排序的表达式
  Pair<Expression, Expression> generateCollationKey( // 方法签名：生成排序键和比较器
      List<RelFieldCollation> collations); // 排序规则列表

  /** Returns a comparator. Unlike the comparator returned by
   * {@link #generateCollationKey(java.util.List)}, this comparator acts on the
   * whole element. */
  // 方法作用：生成比较器表达式，直接作用于整个元素（行），而不是排序键
  // 参数说明：collation - 关系排序对象，定义完整的排序规则
  // 返回值说明：返回Expression对象，表示比较器表达式
  // 与generateCollationKey的区别：本方法生成的比较器直接比较整个行对象，而generateCollationKey生成的是先提取排序键再比较
  // 使用场景：某些排序实现需要直接比较整个对象而非排序键
  Expression generateComparator( // 方法签名：生成直接作用于整个元素的比较器
      RelCollation collation); // 关系排序规则

  /** Similar to {@link #generateComparator(RelCollation)}, but with some specificities for
   * MergeJoin algorithm: it will not consider two <code>null</code> values as equal.
   *
   * @see org.apache.calcite.linq4j.EnumerableDefaults#compareNullsLastForMergeJoin
   */
  // 方法作用：生成MergeJoin算法专用的比较器，特殊处理null值比较
  // 参数说明：collation - 关系排序对象
  // 返回值说明：返回Expression对象，表示MergeJoin比较器
  // null处理：本方法不会将两个null值视为相等，这是MergeJoin算法的特殊要求
  // 使用场景：MergeJoin操作中，需要特殊的null比较逻辑以确保正确性
  // 参考方法：EnumerableDefaults.compareNullsLastForMergeJoin
  Expression generateMergeJoinComparator(RelCollation collation); // 方法签名：生成MergeJoin专用比较器

  /** Returns a expression that yields a comparer, or null if this type
   * is comparable. */
  // 方法作用：返回比较器表达式，如果类型不可比较则返回null
  // 返回值说明：返回Expression对象表示比较器，如果类型本身实现了Comparable则返回null（使用默认比较）
  // 使用场景：在某些需要显式比较器的场景中使用，如果类型本身可比较则返回null以使用默认比较
  @Nullable Expression comparer(); // 方法签名：获取比较器表达式（可能为null）

  /** Generates an expression that creates a record for a row, initializing
   * its fields with the given expressions. There must be one expression per
   * field.
   *
   * @param expressions Expression to initialize each field
   * @return Expression to create a row
   */
  // 方法作用：生成创建行记录的表达式，用给定的表达式初始化每个字段
  // 参数说明：expressions - 表达式列表，每个表达式对应一个字段的初始化值
  // 返回值说明：返回Expression对象，表示创建行记录的表达式
  // 重要说明：expressions的数量必须与字段数量一一对应
  // 使用场景：在构造输出行时，用计算得到的结果初始化每个字段
  Expression record(List<Expression> expressions); // 方法签名：生成创建行记录的表达式

  /** Returns the format. */
  // 方法作用：返回当前物理类型使用的行格式
  // 返回值说明：返回JavaRowFormat枚举值，表示行的物理表示方式
  // 行格式类型：包括ARRAY（Object[]）、SCALAR（单个值）、CUSTOM（自定义POJO）等
  JavaRowFormat getFormat(); // 方法签名：获取行格式

  List<Expression> accessors(Expression parameter, List<Integer> argList); // 方法签名：生成字段访问器表达式列表

  /** Returns a copy of this type that allows nulls if {@code nullable} is
   * true. */
  // 方法作用：返回当前类型的副本，根据nullable参数决定是否允许null值
  // 参数说明：nullable - true表示允许null，false表示不允许null
  // 返回值说明：返回PhysType实例，表示修改了可空性后的新类型
  // 使用场景：在类型转换或类型推导过程中，调整类型的可空性
  PhysType makeNullable(boolean nullable); // 方法签名：创建可空性调整后的类型副本

  /** Converts an enumerable of this physical type to an enumerable that uses a
   * given physical type for its rows.
   *
   * @deprecated Use {@link #convertTo(Expression, JavaRowFormat)}.
   * The use of PhysType as a second parameter is misleading since only the row
   * format of the expression is affected by the conversion. Moreover it requires
   * to have at hand a PhysType object which is not really necessary for achieving
   * the desired result.
   */
  // 方法作用：将此物理类型的可枚举集合转换为使用指定物理类型的可枚举集合
  // 参数说明：expression - 表达式对象，表示要转换的可枚举集合
  // 参数说明：targetPhysType - 目标物理类型
  // 返回值说明：返回Expression对象，表示转换后的可枚举集合
  // 废弃原因：参数名称容易误导，实际只影响行格式，不需要完整的PhysType对象
  // 替代方法：使用convertTo(Expression, JavaRowFormat)代替
  @Deprecated // to be removed before 2.0 // 废弃标记，将在2.0版本前移除
  Expression convertTo(Expression expression, PhysType targetPhysType); // 方法签名：转换为指定物理类型（已废弃）

  /** Converts an enumerable of this physical type to an enumerable that uses
   * the <code>targetFormat</code> for representing its rows. */
  // 方法作用：将此物理类型的可枚举集合转换为使用指定行格式的可枚举集合
  // 参数说明：expression - 表达式对象，表示要转换的可枚举集合
  // 参数说明：targetFormat - 目标行格式，指定转换后的行表示方式
  // 返回值说明：返回Expression对象，表示转换后的可枚举集合
  // 使用场景：在不同行格式之间转换，如从ARRAY格式转换为CUSTOM格式
  Expression convertTo(Expression expression, JavaRowFormat targetFormat); // 方法签名：转换为指定行格式
}
