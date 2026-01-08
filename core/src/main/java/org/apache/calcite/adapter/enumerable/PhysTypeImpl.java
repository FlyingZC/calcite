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
package org.apache.calcite.adapter.enumerable; // 定义包名,位于枚举适配器模块中,包含可枚举物理实现相关类

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂,用于在SQL类型和Java类型之间进行转换
import org.apache.calcite.linq4j.Ord; // 导入Ord工具类,表示带索引的元素,用于遍历时同时获取元素和索引
import org.apache.calcite.linq4j.function.Function1; // 导入Function1函数式接口,表示接受一个参数并返回一个结果的函数
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入代码块构建器,用于构建Java代码块表达式
import org.apache.calcite.linq4j.tree.Expression; // 导入表达式基类,表示Java语言的表达式
import org.apache.calcite.linq4j.tree.Expressions; // 导入表达式工具类,提供创建各种表达式的方法
import org.apache.calcite.linq4j.tree.MemberDeclaration; // 导入成员声明接口,表示类成员(字段、方法等)的声明
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入参数表达式,表示方法或lambda的参数
import org.apache.calcite.linq4j.tree.Primitive; // 导入原始类型工具类,处理Java原始类型和包装类型
import org.apache.calcite.linq4j.tree.Types; // 导入类型工具类,提供类型反射和类型信息获取功能
import org.apache.calcite.rel.RelCollation; // 导入排序规则类,定义行的排序方式
import org.apache.calcite.rel.RelFieldCollation; // 导入字段排序类,定义单个字段的排序方向和空值处理
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口,表示SQL类型系统中的类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口,用于创建和操作SQL类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入关系数据类型字段类,表示结构化类型中的一个字段
import org.apache.calcite.runtime.Utilities; // 导入运行时工具类,提供比较、转换等实用方法
import org.apache.calcite.sql.SqlUtil; // 导入SQL工具类,提供SQL相关的实用方法
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举,定义所有标准SQL类型名称
import org.apache.calcite.util.BuiltInMethod; // 导入内置方法枚举,引用Calcite内置的Java方法
import org.apache.calcite.util.Pair; // 导入Pair工具类,表示不可变的键值对
import org.apache.calcite.util.Util; // 导入通用工具类,提供集合处理、字符串操作等实用方法

import com.google.common.collect.ImmutableList; // 导入Guava的不可变列表类,确保列表创建后不可修改

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解,标记可能为null的元素

import java.lang.reflect.Modifier; // 导入修饰符工具类,用于处理类、方法、字段的访问修饰符
import java.lang.reflect.Type; // 导入Type接口,表示Java中的类型
import java.util.AbstractList; // 导入抽象列表类,用于创建自定义的列表实现
import java.util.ArrayList; // 导入ArrayList动态数组类,提供可变大小的数组实现
import java.util.Comparator; // 导入比较器接口,用于定义对象比较规则
import java.util.List; // 导入List接口,表示有序集合

import static org.apache.calcite.adapter.enumerable.EnumUtils.generateCollatorExpression; // 静态导入:生成排序器表达式的方法
import static org.apache.calcite.adapter.enumerable.EnumUtils.overridingMethodDecl; // 静态导入:生成覆盖方法声明的方法

import static java.util.Objects.requireNonNull; // 静态导入:要求对象非空的方法,用于参数校验

/** Implementation of {@link PhysType}. */ // 类注释:PhysType接口的实现类,表示物理类型,用于描述行数据的Java表示形式
public class PhysTypeImpl implements PhysType { // 定义PhysTypeImpl类,实现PhysType接口,提供物理类型的具体实现
  private final JavaTypeFactory typeFactory; // 成员变量:Java类型工厂,用于在SQL类型和Java类型之间进行转换,final表示不可变
  private final RelDataType rowType; // 成员变量:关系数据类型,表示行的SQL类型结构(包含字段列表和类型信息),final表示不可变
  private final Type javaRowClass; // 成员变量:Java行类,表示行数据在Java中的具体类型(如Object[]、List、自定义类等),final表示不可变
  private final List<Class> fieldClasses = new ArrayList<>(); // 成员变量:字段类列表,存储每个字段对应的Java类类型,用于快速访问字段类型
  final JavaRowFormat format; // 成员变量:Java行格式,指定行数据的存储格式(如ARRAY、SCALAR、LIST等),final表示不可变

  /** Creates a PhysTypeImpl. */ // 方法注释:创建PhysTypeImpl实例的构造方法
  PhysTypeImpl( // 构造方法:创建PhysTypeImpl对象,初始化物理类型的各个属性
      JavaTypeFactory typeFactory, // 参数:Java类型工厂,用于类型转换和类型信息获取
      RelDataType rowType, // 参数:关系数据类型,描述行的SQL类型结构
      Type javaRowClass, // 参数:Java行类,表示行数据的Java类型
      JavaRowFormat format) { // 参数:Java行格式,指定行数据的存储格式
    this.typeFactory = typeFactory; // 将传入的类型工厂赋值给成员变量,用于后续类型转换操作
    this.rowType = rowType; // 将传入的关系数据类型赋值给成员变量,用于描述行的结构
    this.javaRowClass = javaRowClass; // 将传入的Java行类赋值给成员变量,用于表示行数据的Java类型
    this.format = format; // 将传入的行格式赋值给成员变量,用于确定如何访问和存储行数据
    for (RelDataTypeField field : rowType.getFieldList()) { // 遍历关系数据类型中的所有字段,获取每个字段的类型信息
      Type fieldType = typeFactory.getJavaClass(field.getType()); // 通过类型工厂将SQL类型转换为对应的Java类型
      fieldClasses.add(fieldType instanceof Class ? (Class) fieldType : Object[].class); // 将Java类型添加到字段类列表中,如果是数组类型则使用Object[].class
    } // 结束字段遍历,此时fieldClasses列表包含了所有字段对应的Java类类型
  } // 构造方法结束,PhysTypeImpl对象初始化完成

  public static PhysType of( // 静态工厂方法:创建PhysType实例,使用默认优化选项
      JavaTypeFactory typeFactory, // 参数:Java类型工厂,用于类型转换
      RelDataType rowType, // 参数:关系数据类型,描述行的结构
      JavaRowFormat format) { // 参数:Java行格式,指定行的存储格式
    return of(typeFactory, rowType, format, true); // 调用重载方法,传入true表示启用格式优化
  } // 静态方法结束,返回优化后的PhysType实例

  public static PhysType of( // 静态工厂方法:创建PhysType实例,可控制是否优化
      JavaTypeFactory typeFactory, // 参数:Java类型工厂,用于类型转换
      RelDataType rowType, // 参数:关系数据类型,描述行的结构
      JavaRowFormat format, // 参数:Java行格式,指定行的存储格式
      boolean optimize) { // 参数:是否优化标志,如果为true则根据行结构优化格式选择
    if (optimize) { // 如果启用优化
      format = format.optimize(rowType); // 调用格式的optimize方法,根据行类型优化选择最合适的格式(如单字段使用SCALAR等)
    } // 优化判断结束
    final Type javaRowClass = format.javaRowClass(typeFactory, rowType); // 根据格式和行类型确定对应的Java行类类型
    return new PhysTypeImpl(typeFactory, rowType, javaRowClass, format); // 创建并返回PhysTypeImpl实例
  } // 静态方法结束,返回创建的PhysType实例

  static PhysType of( // 静态工厂方法:从Java行类创建PhysType实例,主要用于反射场景
      final JavaTypeFactory typeFactory, // 参数:Java类型工厂,用于创建SQL类型
      Type javaRowClass) { // 参数:Java行类,表示行数据的Java类型
    final RelDataTypeFactory.Builder builder = typeFactory.builder(); // 创建关系数据类型构建器,用于构建SQL类型结构
    if (javaRowClass instanceof Types.RecordType) { // 如果Java行类是记录类型(自定义结构体)
      final Types.RecordType recordType = (Types.RecordType) javaRowClass; // 将Java行类转换为记录类型
      for (Types.RecordField field : recordType.getRecordFields()) { // 遍历记录类型中的所有字段
        builder.add(field.getName(), typeFactory.createType(field.getType())); // 将每个字段添加到构建器中,创建对应的SQL类型
      } // 字段遍历结束
    } // 记录类型判断结束
    RelDataType rowType = builder.build(); // 根据构建器构建关系数据类型,完成SQL类型结构的创建
    // Do not optimize if there are 0 or 1 fields. // 注释:如果字段数为0或1则不优化格式
    return new PhysTypeImpl(typeFactory, rowType, javaRowClass, // 创建PhysTypeImpl实例,使用CUSTOM格式表示自定义类型
        JavaRowFormat.CUSTOM); // 使用CUSTOM格式,表示这是自定义的Java类型
  } // 静态方法结束,返回从Java行类创建的PhysType实例

  @Override public JavaRowFormat getFormat() { // 重写接口方法:获取Java行格式
    return format; // 返回成员变量format,表示行数据的存储格式
  } // 方法结束,返回Java行格式

  @Override public JavaTypeFactory getTypeFactory() { // 重写接口方法:获取Java类型工厂
    return typeFactory; // 返回成员变量typeFactory,用于类型转换和类型信息获取
  } // 方法结束,返回Java类型工厂

  @Override public PhysType project(List<Integer> integers, JavaRowFormat format) { // 重写接口方法:投影操作,选择指定字段并转换为指定格式
    return project(integers, false, format); // 调用重载方法,传入false表示不添加指示器字段
  } // 方法结束,返回投影后的新PhysType

  @Override public PhysType project(List<Integer> integers, boolean indicator, // 重写接口方法:投影操作,可选择添加指示器字段
      JavaRowFormat format) { // 参数:目标格式,指定投影后行的存储格式
    final RelDataTypeFactory.Builder builder = typeFactory.builder(); // 创建关系数据类型构建器,用于构建投影后的类型
    for (int index : integers) { // 遍历要选择的字段索引列表
      builder.add(rowType.getFieldList().get(index)); // 将指定索引的字段添加到构建器中,保留字段名称和类型
    } // 字段遍历结束
    if (indicator) { // 如果需要添加指示器字段(用于标记字段是否来自左连接的右表等场景)
      final RelDataType booleanType = // 创建布尔类型,用于指示器字段
          typeFactory.createTypeWithNullability( // 创建带可空性的类型
              typeFactory.createSqlType(SqlTypeName.BOOLEAN), false); // 创建SQL的BOOLEAN类型,设置为不可空
      for (int index : integers) { // 再次遍历字段索引列表
        builder.add("i$" + rowType.getFieldList().get(index).getName(), // 添加指示器字段,名称为"i$"加上原字段名
            booleanType); // 指示器字段的类型为布尔类型
      } // 指示器字段添加循环结束
    } // 指示器判断结束
    RelDataType projectedRowType = builder.build(); // 根据构建器构建投影后的关系数据类型
    return of(typeFactory, projectedRowType, format.optimize(projectedRowType)); // 创建并返回新的PhysType实例,对格式进行优化
  } // 方法结束,返回投影后的PhysType

  @Override public Expression generateSelector( // 重写接口方法:生成选择器表达式,用于从行中选择指定字段
      ParameterExpression parameter, // 参数:参数表达式,表示输入行的参数名
      List<Integer> fields) { // 参数:字段索引列表,指定要选择的字段
    return generateSelector(parameter, fields, format); // 调用重载方法,使用当前格式作为目标格式
  } // 方法结束,返回生成的选择器表达式

  @Override public Expression generateSelector( // 重写接口方法:生成选择器表达式,可指定目标格式
      ParameterExpression parameter, // 参数:参数表达式,表示输入行的参数名
      List<Integer> fields, // 参数:字段索引列表,指定要选择的字段
      JavaRowFormat targetFormat) { // 参数:目标格式,指定输出行的存储格式
    // Optimize target format // 注释:优化目标格式
    switch (fields.size()) { // 根据字段数量进行优化
    case 0: // 如果没有字段
      targetFormat = JavaRowFormat.LIST; // 使用LIST格式,表示空列表
      break; // 结束case
    case 1: // 如果只有一个字段
      targetFormat = JavaRowFormat.SCALAR; // 使用SCALAR格式,表示单个标量值
      break; // 结束case
    default: // 其他情况
      break; // 保持原有格式不变
    } // switch结束
    final PhysType targetPhysType = // 创建目标物理类型
        project(fields, targetFormat); // 调用project方法创建投影后的物理类型
    switch (format) { // 根据当前格式进行不同的处理
    case SCALAR: // 如果当前格式是SCALAR(标量)
      return Expressions.call(BuiltInMethod.IDENTITY_SELECTOR.method); // 返回恒等选择器,直接返回输入值
    default: // 其他格式
      return Expressions.lambda(Function1.class, // 创建lambda表达式,类型为Function1
          targetPhysType.record(fieldReferences(parameter, fields)), parameter); // lambda体:创建目标格式的记录,使用字段引用列表
    } // switch结束
  } // 方法结束,返回生成的选择器lambda表达式

  @Override public Expression generateSelector(final ParameterExpression parameter, // 重写接口方法:生成带指示器字段的选择器表达式
      final List<Integer> fields, List<Integer> usedFields, // 参数:usedFields表示实际使用的字段索引
      JavaRowFormat targetFormat) { // 参数:目标格式
    final PhysType targetPhysType = // 创建目标物理类型,包含指示器字段
        project(fields, true, targetFormat); // 调用project方法,true表示添加指示器字段
    final List<Expression> expressions = new ArrayList<>(); // 创建表达式列表,用于存储lambda体中的表达式
    for (Ord<Integer> ord : Ord.zip(fields)) { // 遍历字段索引,Ord.zip提供索引和值的配对
      final Integer field = ord.e; // 获取当前字段索引
      if (usedFields.contains(field)) { // 如果字段在实际使用的字段列表中
        expressions.add(fieldReference(parameter, field)); // 添加字段引用表达式,访问该字段的值
      } else { // 如果字段不在实际使用的字段列表中
        final Primitive primitive = // 获取字段对应的原始类型
            Primitive.of(targetPhysType.fieldClass(ord.i)); // 通过目标物理类型获取字段的Java类,然后获取对应的原始类型
        expressions.add( // 添加常量表达式
            Expressions.constant( // 创建常量表达式
                primitive != null ? primitive.defaultValue : null)); // 如果是原始类型则使用默认值,否则使用null
      } // if-else结束
    } // 字段遍历结束
    for (Integer field : fields) { // 再次遍历字段索引,用于生成指示器字段
      expressions.add(Expressions.constant(!usedFields.contains(field))); // 添加指示器字段的值:如果字段未被使用则为true,否则为false
    } // 指示器字段循环结束
    return Expressions.lambda(Function1.class, // 创建lambda表达式,类型为Function1
        targetPhysType.record(expressions), parameter); // lambda体:创建目标格式的记录,包含字段值和指示器字段
  } // 方法结束,返回带指示器字段的选择器lambda表达式

  @Override public Pair<Type, List<Expression>> selector( // 重写接口方法:生成选择器,返回类型和表达式列表
      ParameterExpression parameter, // 参数:参数表达式,表示输入行的参数名
      List<Integer> fields, // 参数:字段索引列表,指定要选择的字段
      JavaRowFormat targetFormat) { // 参数:目标格式
    // Optimize target format // 注释:优化目标格式
    switch (fields.size()) { // 根据字段数量进行优化
    case 0: // 如果没有字段
      targetFormat = JavaRowFormat.LIST; // 使用LIST格式
      break; // 结束case
    case 1: // 如果只有一个字段
      targetFormat = JavaRowFormat.SCALAR; // 使用SCALAR格式
      break; // 结束case
    default: // 其他情况
      break; // 保持原有格式不变
    } // switch结束
    final PhysType targetPhysType = // 创建目标物理类型
        project(fields, targetFormat); // 调用project方法创建投影后的物理类型
    switch (format) { // 根据当前格式进行不同的处理
    case SCALAR: // 如果当前格式是SCALAR(标量)
      return Pair.of(parameter.getType(), ImmutableList.of(parameter)); // 返回参数类型和包含参数的列表,表示直接返回参数本身
    default: // 其他格式
      return Pair.of(targetPhysType.getJavaRowType(), // 返回目标Java行类型
          fieldReferences(parameter, fields)); // 返回字段引用表达式列表
    } // switch结束
  } // 方法结束,返回类型和表达式列表的Pair

  @Override public List<Expression> accessors(Expression v1, List<Integer> argList) { // 重写接口方法:生成访问器表达式列表,用于访问指定字段
    final List<Expression> expressions = new ArrayList<>(); // 创建表达式列表,用于存储访问器表达式
    for (int field : argList) { // 遍历字段索引列表
      expressions.add( // 添加访问器表达式
          EnumUtils.convert( // 调用工具方法进行类型转换
              fieldReference(v1, field), // 获取字段引用表达式
              fieldClass(field))); // 转换为字段对应的Java类类型
    } // 字段遍历结束
    return expressions; // 返回访问器表达式列表
  } // 方法结束,返回访问器表达式列表

  @Override public PhysType makeNullable(boolean nullable) { // 重写接口方法:创建可空版本的PhysType
    if (!nullable) { // 如果不需要可空
      return this; // 返回当前对象本身
    } // if结束
    return new PhysTypeImpl(typeFactory, // 创建新的PhysTypeImpl实例
        typeFactory.createTypeWithNullability(rowType, true), // 创建可空的关系数据类型
        Primitive.box(javaRowClass), // 将Java行类转换为对应的包装类型(如int->Integer)
        format); // 保持原有格式不变
  } // 方法结束,返回可空版本的PhysType

  @SuppressWarnings("deprecation") // 抑制废弃警告
  @Override public Expression convertTo(Expression exp, PhysType targetPhysType) { // 重写接口方法:将表达式转换为目标物理类型
    return convertTo(exp, targetPhysType.getFormat()); // 调用重载方法,使用目标物理类型的格式
  } // 方法结束,返回转换后的表达式

  @Override public Expression convertTo(Expression exp, JavaRowFormat targetFormat) { // 重写接口方法:将表达式转换为目标格式
    if (format == targetFormat) { // 如果当前格式与目标格式相同
      return exp; // 直接返回原表达式,无需转换
    } // if结束
    final ParameterExpression o_ = // 创建参数表达式,表示输入对象
        Expressions.parameter(javaRowClass, "o"); // 参数名为"o",类型为当前Java行类
    final int fieldCount = rowType.getFieldCount(); // 获取字段数量
    // The conversion must be strict so optimizations of the targetFormat should not be performed // 注释:转换必须是严格的,不应优化目标格式
    // by the code that follows. If necessary the target format can be optimized before calling // 注释:如果需要优化,应在调用此方法之前完成
    // this method. // 注释结束
    PhysType targetPhysType = // 创建目标物理类型
        PhysTypeImpl.of(typeFactory, rowType, targetFormat, false); // 调用of方法,false表示不优化格式
    final Expression selector = // 创建选择器表达式
        Expressions.lambda(Function1.class, // 创建lambda表达式,类型为Function1
            targetPhysType.record(fieldReferences(o_, Util.range(fieldCount))), // lambda体:创建目标格式的记录,使用所有字段的引用
            o_); // lambda参数为o_
    return Expressions.call(exp, BuiltInMethod.SELECT.method, selector); // 调用SELECT方法,将选择器应用于输入表达式
  } // 方法结束,返回格式转换后的表达式

  @Override public Pair<Expression, Expression> generateCollationKey( // 重写接口方法:生成排序键表达式,用于排序操作
      final List<RelFieldCollation> collations) { // 参数:字段排序规则列表,定义每个字段的排序方向和空值处理
    final Expression selector; // 声明选择器表达式变量
    if (collations.size() == 1) { // 如果只有一个排序字段
      RelFieldCollation collation = collations.get(0); // 获取第一个排序规则
      RelDataType fieldType = rowType.getFieldList() == null || rowType.getFieldList().isEmpty() // 判断字段列表是否为空
          ? rowType // 如果为空则使用整个行类型
          : rowType.getFieldList().get(collation.getFieldIndex()).getType(); // 否则获取指定索引字段的类型
      Expression fieldComparator = generateCollatorExpression(fieldType.getCollation()); // 生成字段排序器表达式,用于自定义排序规则
      ParameterExpression parameter = // 创建参数表达式
          Expressions.parameter(javaRowClass, "v"); // 参数名为"v",类型为当前Java行类
      selector = // 创建选择器lambda表达式
          Expressions.lambda( // 创建lambda表达式
              Function1.class, // lambda类型为Function1
              fieldReference(parameter, collation.getFieldIndex()), // lambda体:返回排序字段的引用
              parameter); // lambda参数为parameter
      return Pair.of(selector, // 返回选择器表达式
          Expressions.call( // 创建比较器表达式
              fieldComparator == null ? BuiltInMethod.NULLS_COMPARATOR.method // 如果没有自定义排序器则使用NULLS_COMPARATOR
                  : BuiltInMethod.NULLS_COMPARATOR2.method, // 否则使用NULLS_COMPARATOR2(支持自定义排序器)
              Expressions.list( // 创建参数列表
                  (Expression) Expressions.constant( // 添加常量参数
                      collation.nullDirection // 判断空值排序方向
                          == RelFieldCollation.NullDirection.FIRST), // 如果空值排在最前面则为true
                  Expressions.constant( // 添加常量参数
                      collation.direction // 判断排序方向
                          == RelFieldCollation.Direction.DESCENDING)) // 如果是降序则为true
                  .appendIfNotNull(fieldComparator))); // 如果有自定义排序器则追加到参数列表
    } // 单字段排序处理结束
    selector = // 多字段排序时的选择器
        Expressions.call(BuiltInMethod.IDENTITY_SELECTOR.method); // 使用恒等选择器,直接返回整个行对象

    // int c; // 注释:声明比较结果变量
    // c = Utilities.compare(v0, v1); // 注释:比较两个字段的值
    // if (c != 0) return c; // or -c if descending // 注释:如果比较结果不为0则返回结果(降序时取负)
    // ... // 注释:继续比较下一个字段
    // return 0; // 注释:所有字段都相等则返回0
    BlockBuilder body = new BlockBuilder(); // 创建代码块构建器,用于构建比较器的方法体
    final ParameterExpression parameterV0 = // 创建参数表达式,表示第一个比较对象
        Expressions.parameter(javaRowClass, "v0"); // 参数名为"v0",类型为当前Java行类
    final ParameterExpression parameterV1 = // 创建参数表达式,表示第二个比较对象
        Expressions.parameter(javaRowClass, "v1"); // 参数名为"v1",类型为当前Java行类
    final ParameterExpression parameterC = // 创建参数表达式,表示比较结果
        Expressions.parameter(int.class, "c"); // 参数名为"c",类型为int
    final int mod = collations.size() == 1 ? Modifier.FINAL : 0; // 确定修饰符:单字段比较时c为final,多字段时不为final
    body.add(Expressions.declare(mod, parameterC, null)); // 添加变量声明语句,声明比较结果变量c
    for (RelFieldCollation collation : collations) { // 遍历所有排序规则
      final int index = collation.getFieldIndex(); // 获取当前排序字段的索引
      final RelDataType fieldType = rowType.getFieldList().get(index).getType(); // 获取当前字段的类型
      final Expression fieldComparator = generateCollatorExpression(fieldType.getCollation()); // 生成字段排序器表达式
      Expression arg0 = fieldReference(parameterV0, index); // 获取第一个对象的字段引用
      Expression arg1 = fieldReference(parameterV1, index); // 获取第二个对象的字段引用
      switch (Primitive.flavor(fieldClass(index))) { // 判断字段类型的种类(原始类型或对象类型)
      case OBJECT: // 如果是对象类型
        arg0 = EnumUtils.convert(arg0, Comparable.class); // 将第一个参数转换为Comparable接口
        arg1 = EnumUtils.convert(arg1, Comparable.class); // 将第二个参数转换为Comparable接口
        break; // 结束case
      default: // 其他类型(原始类型)
        break; // 不需要转换
      } // switch结束
      final boolean nullsFirst = // 判断空值是否排在最前面
          collation.nullDirection // 获取空值排序方向
              == RelFieldCollation.NullDirection.FIRST; // 如果是FIRST则为true
      final boolean descending = // 判断是否降序
          collation.getDirection() // 获取排序方向
              == RelFieldCollation.Direction.DESCENDING; // 如果是DESCENDING则为true
      body.add( // 添加比较语句
          Expressions.statement( // 创建语句表达式
              Expressions.assign( // 创建赋值表达式
                  parameterC, // 左值:比较结果变量c
                  Expressions.call( // 右值:调用比较方法
                      Utilities.class, // 调用Utilities类的比较方法
                      fieldNullable(index) // 判断字段是否可空
                          ? (nullsFirst != descending // 如果字段可空
                          ? "compareNullsFirst" // 如果空值在前且不是降序则使用compareNullsFirst
                          : "compareNullsLast") // 否则使用compareNullsLast
                          : "compare", // 如果字段不可空则使用compare
                      Expressions.list( // 创建参数列表
                          arg0, // 第一个比较参数
                          arg1) // 第二个比较参数
                          .appendIfNotNull(fieldComparator))))); // 如果有自定义排序器则追加到参数列表
      body.add( // 添加条件判断语句
          Expressions.ifThen( // 创建if-then语句
              Expressions.notEqual( // 条件:比较结果不等于0
                  parameterC, Expressions.constant(0)), // parameterC != 0
              Expressions.return_( // 如果条件成立则返回
                  null, // 返回值为null(表示从当前方法返回)
                  descending // 判断是否降序
                      ? Expressions.negate(parameterC) // 如果降序则返回-c
                      : parameterC)))); // 否则返回c
    } // 排序规则遍历结束
    body.add( // 添加返回语句
        Expressions.return_(null, Expressions.constant(0))); // 所有字段都相等则返回0

    final List<MemberDeclaration> memberDeclarations = // 创建成员声明列表
        Expressions.list( // 使用Expressions工具类创建列表
            Expressions.methodDecl( // 添加方法声明
                Modifier.PUBLIC, // 方法修饰符为public
                int.class, // 方法返回类型为int
                "compare", // 方法名为compare
                ImmutableList.of( // 方法参数列表
                    parameterV0, parameterV1), // 参数为v0和v1
                body.toBlock())); // 方法体为之前构建的代码块

    if (EnumerableRules.BRIDGE_METHODS) { // 如果需要生成桥接方法
      final ParameterExpression parameterO0 = // 创建参数表达式,表示第一个桥接参数
          Expressions.parameter(Object.class, "o0"); // 参数名为"o0",类型为Object
      final ParameterExpression parameterO1 = // 创建参数表达式,表示第二个桥接参数
          Expressions.parameter(Object.class, "o1"); // 参数名为"o1",类型为Object
      BlockBuilder bridgeBody = new BlockBuilder(); // 创建桥接方法体构建器
      bridgeBody.add( // 添加返回语句
          Expressions.return_( // 创建返回语句
              null, // 返回值为null
              Expressions.call( // 调用compare方法
                  Expressions.parameter( // 创建this参数表达式
                      Comparable.class, "this"), // 类型为Comparable,名称为this
                  BuiltInMethod.COMPARATOR_COMPARE.method, // 调用Comparator接口的compare方法
                  Expressions.convert_( // 转换第一个参数
                      parameterO0, // 源参数为o0
                      javaRowClass), // 目标类型为当前Java行类
                  Expressions.convert_( // 转换第二个参数
                      parameterO1, // 源参数为o1
                      javaRowClass))))); // 目标类型为当前Java行类
      memberDeclarations.add( // 添加桥接方法声明
          overridingMethodDecl( // 创建覆盖方法声明
              BuiltInMethod.COMPARATOR_COMPARE.method, // 覆盖的方法为Comparator的compare方法
              ImmutableList.of(parameterO0, parameterO1), // 方法参数列表
              bridgeBody.toBlock())); // 方法体为桥接方法体
    } // 桥接方法判断结束
    return Pair.of(selector, // 返回选择器表达式
        Expressions.new_(Comparator.class, // 创建Comparator匿名类实例
            ImmutableList.of(), // 构造函数参数列表为空
            memberDeclarations)); // 成员声明列表包含compare方法和可能的桥接方法
  } // 方法结束,返回选择器和比较器的Pair

  @Override public Expression generateComparator(RelCollation collation) { // 重写接口方法:生成比较器表达式
    return this.generateComparator(collation, fieldCollation -> { // 调用重载方法,传入lambda函数确定比较方法名
      final int index = fieldCollation.getFieldIndex(); // 获取字段索引
      final boolean nullsFirst = // 判断空值是否排在最前面
          fieldCollation.nullDirection // 获取空值排序方向
              == RelFieldCollation.NullDirection.FIRST; // 如果是FIRST则为true
      final boolean descending = // 判断是否降序
          fieldCollation.getDirection() // 获取排序方向
              == RelFieldCollation.Direction.DESCENDING; // 如果是DESCENDING则为true
      return fieldNullable(index) // 判断字段是否可空
          ? (nullsFirst != descending // 如果字段可空
          ? "compareNullsFirst" // 如果空值在前且不是降序则使用compareNullsFirst
          : "compareNullsLast") // 否则使用compareNullsLast
          : "compare"; // 如果字段不可空则使用compare
    }); // lambda函数结束
  } // 方法结束,返回生成的比较器表达式

  private Expression generateComparator(RelCollation collation, // 私有方法:生成比较器表达式,可自定义比较方法选择逻辑
      Function1<RelFieldCollation, String> compareMethodNameFunction) { // 参数:函数接口,用于根据排序规则选择比较方法名
    // int c; // 注释:声明比较结果变量
    // c = Utilities.compare(v0, v1); // 注释:比较两个字段的值
    // if (c != 0) return c; // or -c if descending // 注释:如果比较结果不为0则返回结果(降序时取负)
    // ... // 注释:继续比较下一个字段
    // return 0; // 注释:所有字段都相等则返回0
    BlockBuilder body = new BlockBuilder(); // 创建代码块构建器,用于构建比较器的方法体
    final Type javaRowClass = Primitive.box(this.javaRowClass); // 将Java行类转换为包装类型(如int->Integer)
    final ParameterExpression parameterV0 = // 创建参数表达式,表示第一个比较对象
        Expressions.parameter(javaRowClass, "v0"); // 参数名为"v0",类型为包装后的Java行类
    final ParameterExpression parameterV1 = // 创建参数表达式,表示第二个比较对象
        Expressions.parameter(javaRowClass, "v1"); // 参数名为"v1",类型为包装后的Java行类
    final ParameterExpression parameterC = // 创建参数表达式,表示比较结果
        Expressions.parameter(int.class, "c"); // 参数名为"c",类型为int
    final int mod = // 确定修饰符
        collation.getFieldCollations().size() == 1 ? Modifier.FINAL : 0; // 单字段比较时c为final,多字段时不为final
    body.add(Expressions.declare(mod, parameterC, null)); // 添加变量声明语句,声明比较结果变量c
    for (RelFieldCollation fieldCollation : collation.getFieldCollations()) { // 遍历所有排序规则
      final int index = fieldCollation.getFieldIndex(); // 获取当前排序字段的索引
      final RelDataType fieldType = rowType.getFieldList().get(index).getType(); // 获取当前字段的类型
      final Expression fieldComparator = generateCollatorExpression(fieldType.getCollation()); // 生成字段排序器表达式
      Expression arg0 = fieldReference(parameterV0, index); // 获取第一个对象的字段引用
      Expression arg1 = fieldReference(parameterV1, index); // 获取第二个对象的字段引用
      switch (Primitive.flavor(fieldClass(index))) { // 判断字段类型的种类
      case OBJECT: // 如果是对象类型
        arg0 = EnumUtils.convert(arg0, Comparable.class); // 将第一个参数转换为Comparable接口
        arg1 = EnumUtils.convert(arg1, Comparable.class); // 将第二个参数转换为Comparable接口
        break; // 结束case
      default: // 其他类型
        break; // 不需要转换
      } // switch结束
      final boolean descending = // 判断是否降序
          fieldCollation.getDirection() // 获取排序方向
              == RelFieldCollation.Direction.DESCENDING; // 如果是DESCENDING则为true
      body.add( // 添加比较语句
          Expressions.statement( // 创建语句表达式
              Expressions.assign( // 创建赋值表达式
                  parameterC, // 左值:比较结果变量c
                  Expressions.call( // 右值:调用比较方法
                      Utilities.class, // 调用Utilities类的比较方法
                      compareMethodNameFunction.apply(fieldCollation), // 使用传入的函数选择比较方法名
                      Expressions.list( // 创建参数列表
                          arg0, // 第一个比较参数
                          arg1) // 第二个比较参数
                          .appendIfNotNull(fieldComparator))))); // 如果有自定义排序器则追加到参数列表
      body.add( // 添加条件判断语句
          Expressions.ifThen( // 创建if-then语句
              Expressions.notEqual( // 条件:比较结果不等于0
                  parameterC, Expressions.constant(0)), // parameterC != 0
              Expressions.return_( // 如果条件成立则返回
                  null, // 返回值为null
                  descending // 判断是否降序
                      ? Expressions.negate(parameterC) // 如果降序则返回-c
                      : parameterC)))); // 否则返回c
    } // 排序规则遍历结束
    body.add( // 添加返回语句
        Expressions.return_(null, Expressions.constant(0))); // 所有字段都相等则返回0

    final List<MemberDeclaration> memberDeclarations = // 创建成员声明列表
        Expressions.list( // 使用Expressions工具类创建列表
            Expressions.methodDecl( // 添加方法声明
                Modifier.PUBLIC, // 方法修饰符为public
                int.class, // 方法返回类型为int
                "compare", // 方法名为compare
                ImmutableList.of(parameterV0, parameterV1), // 方法参数列表
                body.toBlock())); // 方法体为之前构建的代码块

    if (EnumerableRules.BRIDGE_METHODS) { // 如果需要生成桥接方法
      final ParameterExpression parameterO0 = // 创建参数表达式,表示第一个桥接参数
          Expressions.parameter(Object.class, "o0"); // 参数名为"o0",类型为Object
      final ParameterExpression parameterO1 = // 创建参数表达式,表示第二个桥接参数
          Expressions.parameter(Object.class, "o1"); // 参数名为"o1",类型为Object
      BlockBuilder bridgeBody = new BlockBuilder(); // 创建桥接方法体构建器
      bridgeBody.add( // 添加返回语句
          Expressions.return_( // 创建返回语句
              null, // 返回值为null
              Expressions.call( // 调用compare方法
                  Expressions.parameter( // 创建this参数表达式
                      Comparable.class, "this"), // 类型为Comparable,名称为this
                  BuiltInMethod.COMPARATOR_COMPARE.method, // 调用Comparator接口的compare方法
                  Expressions.convert_( // 转换第一个参数
                      parameterO0, // 源参数为o0
                      javaRowClass), // 目标类型为当前Java行类
                  Expressions.convert_( // 转换第二个参数
                      parameterO1, // 源参数为o1
                      javaRowClass))))); // 目标类型为当前Java行类
      memberDeclarations.add( // 添加桥接方法声明
          overridingMethodDecl( // 创建覆盖方法声明
              BuiltInMethod.COMPARATOR_COMPARE.method, // 覆盖的方法为Comparator的compare方法
              ImmutableList.of(parameterO0, parameterO1), // 方法参数列表
              bridgeBody.toBlock())); // 方法体为桥接方法体
    } // 桥接方法判断结束
    return Expressions.new_( // 返回Comparator匿名类实例
        Comparator.class, // 类名为Comparator
        ImmutableList.of(), // 构造函数参数列表为空
        memberDeclarations); // 成员声明列表包含compare方法和可能的桥接方法
  } // 方法结束,返回生成的比较器表达式

  @Override public Expression generateMergeJoinComparator(RelCollation collation) { // 重写接口方法:生成合并连接的比较器表达式
    return this.generateComparator(collation, fieldCollation -> { // 调用重载方法,传入lambda函数确定比较方法名
      // merge join keys must be sorted in ascending order, nulls last // 注释:合并连接的键必须按升序排序,空值排在最后
      assert fieldCollation.nullDirection == RelFieldCollation.NullDirection.LAST; // 断言空值方向为LAST
      assert fieldCollation.getDirection() == RelFieldCollation.Direction.ASCENDING; // 断言排序方向为ASCENDING
      return fieldNullable(fieldCollation.getFieldIndex()) // 判断字段是否可空
          ? "compareNullsLastForMergeJoin" // 如果可空则使用compareNullsLastForMergeJoin方法
          : "compare"; // 如果不可空则使用compare方法
    }); // lambda函数结束
  } // 方法结束,返回合并连接专用的比较器表达式

  @Override public RelDataType getRowType() { // 重写接口方法:获取关系数据类型
    return rowType; // 返回成员变量rowType,表示行的SQL类型结构
  } // 方法结束,返回关系数据类型

  @Override public Expression record(List<Expression> expressions) { // 重写接口方法:生成记录表达式,创建行对象
    return format.record(javaRowClass, expressions); // 调用格式的record方法,根据格式和Java行类创建记录
  } // 方法结束,返回记录表达式

  @Override public Type getJavaRowType() { // 重写接口方法:获取Java行类型
    return javaRowClass; // 返回成员变量javaRowClass,表示行数据的Java类型
  } // 方法结束,返回Java行类型

  @Override public Type getJavaFieldType(int index) { // 重写接口方法:获取指定字段的Java类型
    return format.javaFieldClass(typeFactory, rowType, index); // 调用格式的javaFieldClass方法,根据索引获取字段类型
  } // 方法结束,返回字段的Java类型

  @Override public PhysType component(int fieldOrdinal) { // 重写接口方法:获取数组字段的元素类型
    final RelDataTypeField field = rowType.getFieldList().get(fieldOrdinal); // 获取指定字段
    RelDataType componentType = // 获取字段的组件类型(数组元素的类型)
        requireNonNull(field.getType().getComponentType(), // 获取字段的组件类型
            () -> "field.getType().getComponentType() for " + field); // 如果为null则抛出异常,附带错误信息
    return PhysTypeImpl.of(typeFactory, // 创建新的PhysTypeImpl实例
        toStruct(componentType), format, false); // 将组件类型转换为结构类型,传入false表示不优化
  } // 方法结束,返回数组元素类型的PhysType

  @Override public PhysType field(int ordinal) { // 重写接口方法:获取指定字段的PhysType
    final RelDataTypeField field = rowType.getFieldList().get(ordinal); // 获取指定字段
    final RelDataType type = field.getType(); // 获取字段的类型
    return PhysTypeImpl.of(typeFactory, toStruct(type), format, false); // 创建新的PhysTypeImpl实例,将类型转换为结构类型
  } // 方法结束,返回字段的PhysType

  private RelDataType toStruct(RelDataType type) { // 私有方法:将类型转换为结构类型
    if (type.isStruct()) { // 如果类型已经是结构类型
      return type; // 直接返回该类型
    } // if结束
    return typeFactory.builder() // 创建类型构建器
        .add(SqlUtil.deriveAliasFromOrdinal(0), type) // 添加字段,使用序号派生的别名,类型为原类型
        .build(); // 构建结构类型
  } // 方法结束,返回结构类型

  @Override public @Nullable Expression comparer() { // 重写接口方法:获取比较器表达式
    return format.comparer(); // 调用格式的comparer方法,返回格式对应的比较器
  } // 方法结束,返回比较器表达式或null

  private List<Expression> fieldReferences( // 私有方法:生成字段引用表达式列表
      final Expression parameter, final List<Integer> fields) { // 参数:参数表达式和字段索引列表
    return new AbstractList<Expression>() { // 返回匿名AbstractList子类,提供延迟计算的字段引用列表
      @Override public Expression get(int index) { // 重写get方法,获取指定索引的字段引用
        return fieldReference(parameter, fields.get(index)); // 调用fieldReference方法生成字段引用表达式
      } // get方法结束

      @Override public int size() { // 重写size方法,获取列表大小
        return fields.size(); // 返回字段索引列表的大小
      } // size方法结束
    }; // 匿名类结束
  } // 方法结束,返回字段引用表达式列表

  @Override public Class fieldClass(int field) { // 重写接口方法:获取指定字段的Java类类型
    return fieldClasses.get(field); // 从fieldClasses列表中获取指定字段的Java类类型
  } // 方法结束,返回字段的Java类类型

  @Override public boolean fieldNullable(int field) { // 重写接口方法:判断指定字段是否可空
    return rowType.getFieldList().get(field).getType().isNullable(); // 从关系数据类型中获取字段类型并判断是否可空
  } // 方法结束,返回字段是否可空的布尔值

  @Override public Expression generateAccessor( // 重写接口方法:生成访问器表达式,用于访问指定字段
      List<Integer> fields) { // 参数:字段索引列表
    ParameterExpression v1 = // 创建参数表达式
        Expressions.parameter(javaRowClass, "v1"); // 参数名为"v1",类型为当前Java行类
    switch (fields.size()) { // 根据字段数量进行不同的处理
    case 0: // 如果没有字段
      return Expressions.lambda( // 返回lambda表达式
          Function1.class, // lambda类型为Function1
          Expressions.field( // 访问静态字段
              null, // 对象为null表示静态字段
              BuiltInMethod.COMPARABLE_EMPTY_LIST.field), // 访问COMPARABLE_EMPTY_LIST字段
          v1); // lambda参数为v1
    case 1: // 如果只有一个字段
      int field0 = fields.get(0); // 获取第一个字段索引

      // new Function1<Employee, Res> { // 注释:创建匿名Function1实例
      //    public Res apply(Employee v1) { // 注释:实现apply方法
      //        return v1.<fieldN>; // 注释:返回字段的值
      //    } // 注释:方法结束
      // } // 注释:类结束
      Class returnType = fieldClasses.get(field0); // 获取字段的返回类型
      Expression fieldReference = // 创建字段引用表达式
          EnumUtils.convert( // 转换字段引用
              fieldReference(v1, field0), // 获取字段引用
              returnType); // 转换为目标返回类型
      return Expressions.lambda( // 返回lambda表达式
          Function1.class, // lambda类型为Function1
          fieldReference, // lambda体为字段引用表达式
          v1); // lambda参数为v1
    default: // 多个字段的情况
      // new Function1<Employee, List> { // 注释:创建匿名Function1实例
      //    public List apply(Employee v1) { // 注释:实现apply方法
      //        return Arrays.asList( // 注释:返回包含多个字段值的列表
      //            new Object[] {v1.<fieldN>, v1.<fieldM>}); // 注释:字段值数组
      //    } // 注释:方法结束
      // } // 注释:类结束
      Expressions.FluentList<Expression> list = Expressions.list(); // 创建表达式列表
      for (int field : fields) { // 遍历字段索引列表
        list.add(fieldReference(v1, field)); // 添加字段引用表达式到列表
      } // 字段遍历结束
      return Expressions.lambda(Function1.class, getListExpression(list), v1); // 返回lambda表达式,lambda体为列表表达式
    } // switch结束
  } // 方法结束,返回生成的访问器lambda表达式

  private static Expression getListExpression(Expressions.FluentList<Expression> list) { // 私有静态方法:生成列表表达式
    assert list.size() >= 2; // 断言列表大小至少为2

    switch (list.size()) { // 根据列表大小选择不同的创建方法
    case 2: // 如果列表大小为2
      return Expressions.call( // 调用方法创建列表
              List.class, // 目标类为List
              null, // 对象为null表示静态方法
              BuiltInMethod.LIST2.method, // 调用LIST2方法
              list); // 参数为列表
    case 3: // 如果列表大小为3
      return Expressions.call( // 调用方法创建列表
              List.class, // 目标类为List
              null, // 对象为null表示静态方法
              BuiltInMethod.LIST3.method, // 调用LIST3方法
              list); // 参数为列表
    case 4: // 如果列表大小为4
      return Expressions.call( // 调用方法创建列表
              List.class, // 目标类为List
              null, // 对象为null表示静态方法
              BuiltInMethod.LIST4.method, // 调用LIST4方法
              list); // 参数为列表
    case 5: // 如果列表大小为5
      return Expressions.call( // 调用方法创建列表
              List.class, // 目标类为List
              null, // 对象为null表示静态方法
              BuiltInMethod.LIST5.method, // 调用LIST5方法
              list); // 参数为列表
    case 6: // 如果列表大小为6
      return Expressions.call( // 调用方法创建列表
              List.class, // 目标类为List
              null, // 对象为null表示静态方法
              BuiltInMethod.LIST6.method, // 调用LIST6方法
              list); // 参数为列表
    default: // 其他情况(列表大小大于6)
      return Expressions.call( // 调用方法创建列表
              List.class, // 目标类为List
              null, // 对象为null表示静态方法
              BuiltInMethod.LIST_N.method, // 调用LIST_N方法
              Expressions.newArrayInit(Comparable.class, list)); // 参数为数组初始化表达式
    } // switch结束
  } // 方法结束,返回生成的列表表达式

  @Override public Expression generateAccessorWithoutNulls(List<Integer> fields) { // 重写接口方法:生成访问器表达式,如果任何字段为null则返回null
    if (fields.size() < 2) { // 如果字段数量小于2
      return generateAccessor(fields); // 调用普通的generateAccessor方法
    } // if结束

    ParameterExpression v1 = Expressions.parameter(javaRowClass, "v1"); // 创建参数表达式
    Expressions.FluentList<Expression> list = Expressions.list(); // 创建表达式列表
    for (int field : fields) { // 遍历字段索引列表
      list.add(fieldReference(v1, field)); // 添加字段引用表达式到列表
    } // 字段遍历结束

    // (v1.<field0> == null) // 注释:第一个字段是否为null
    //   ? null // 注释:如果为null则返回null
    //   : (v1.<field1> == null) // 注释:否则检查第二个字段是否为null
    //     ? null; // 注释:如果为null则返回null
    //     : ... // 注释:继续检查下一个字段
    //         : FlatLists.of(...); // 注释:所有字段都不为null则返回列表
    Expression exp = getListExpression(list); // 获取列表表达式
    for (int i = list.size() - 1; i >= 0; i--) { // 从后向前遍历字段列表
      exp = // 构建嵌套的条件表达式
          Expressions.condition( // 创建条件表达式
              Expressions.equal(list.get(i), Expressions.constant(null)), // 条件:字段是否为null
              Expressions.constant(null), // 如果为null则返回null
              exp); // 否则继续检查下一个字段或返回列表
    } // 循环结束
    return Expressions.lambda(Function1.class, exp, v1); // 返回lambda表达式,lambda体为嵌套的条件表达式
  } // 方法结束,返回带null检查的访问器lambda表达式

  @Override public Expression fieldReference( // 重写接口方法:生成字段引用表达式
      Expression expression, int field) { // 参数:行表达式和字段索引
    return fieldReference(expression, field, null); // 调用重载方法,存储类型为null
  } // 方法结束,返回字段引用表达式

  @Override public Expression fieldReference( // 重写接口方法:生成字段引用表达式,可指定存储类型
      Expression expression, int field, @Nullable Type storageType) { // 参数:行表达式、字段索引和存储类型
    Type fieldType; // 声明字段类型变量
    if (storageType == null) { // 如果没有指定存储类型
      storageType = fieldClass(field); // 使用字段的Java类类型作为存储类型
      fieldType = null; // 字段类型为null
    } else { // 如果指定了存储类型
      fieldType = fieldClass(field); // 获取字段的Java类类型
      if (fieldType != java.sql.Date.class // 如果字段类型不是java.sql.Date
          && fieldType != java.sql.Time.class // 且不是java.sql.Time
          && fieldType != java.sql.Timestamp.class) { // 且不是java.sql.Timestamp
        fieldType = null; // 则字段类型设置为null
      } // if结束
    } // if-else结束
    return format.field(expression, field, fieldType, storageType); // 调用格式的field方法,根据格式生成字段引用表达式
  } // 方法结束,返回字段引用表达式
} // 类结束,PhysTypeImpl类定义完成