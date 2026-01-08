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
package org.apache.calcite.adapter.enumerable; // 声明包名,该类位于org.apache.calcite.adapter.enumerable包中,这是Calcite可枚举适配器包

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入JavaTypeFactory,用于Java类型工厂,负责将Calcite类型映射到Java类型
import org.apache.calcite.interpreter.Row; // 导入Row类,这是Calcite解释器中的行表示
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression,这是LINQ4J表达式树中的表达式基类,用于表示代码表达式
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions,这是表达式工厂类,用于创建各种表达式
import org.apache.calcite.linq4j.tree.IndexExpression; // 导入IndexExpression,用于表示数组索引表达式
import org.apache.calcite.linq4j.tree.MemberExpression; // 导入MemberExpression,用于表示成员访问表达式
import org.apache.calcite.linq4j.tree.MethodCallExpression; // 导入MethodCallExpression,用于表示方法调用表达式
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入ParameterExpression,用于表示参数表达式
import org.apache.calcite.linq4j.tree.Statement; // 导入Statement,表示语句
import org.apache.calcite.linq4j.tree.Types; // 导入Types,提供类型相关的工具方法
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType,表示Calcite关系数据类型
import org.apache.calcite.runtime.FlatLists; // 导入FlatLists,提供不可变扁平列表的实现
import org.apache.calcite.runtime.Unit; // 导入Unit,表示空值类型,类似于void但可以作为对象使用
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName,表示SQL类型名称枚举
import org.apache.calcite.util.BuiltInMethod; // 导入BuiltInMethod,定义了内置方法的引用

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解,用于标记可能为null的值

import java.lang.reflect.Type; // 导入Type,表示Java类型
import java.util.ArrayList; // 导入ArrayList,动态数组实现
import java.util.List; // 导入List,列表接口

import static org.apache.calcite.util.BuiltInMethod.ARRAY_COPY; // 静态导入ARRAY_COPY方法,表示System.arraycopy方法
import static org.apache.calcite.util.BuiltInMethod.LIST_TO_ARRAY; // 静态导入LIST_TO_ARRAY方法,表示List.toArray方法
import static org.apache.calcite.util.BuiltInMethod.ROW_COPY_VALUES; // 静态导入ROW_COPY_VALUES方法,表示Row.copyValues方法

/**
 * How a row is represented as a Java value.
 * 描述如何在Java中表示一行数据,这是一个枚举类型,定义了多种行格式
 * 每种格式代表不同的Java数据结构来存储查询结果行,例如:自定义对象、标量、列表、Row对象、数组等
 * Calcite在生成可枚举代码时,会根据性能需求和功能特性选择合适的行格式
 */
public enum JavaRowFormat { // 定义JavaRowFormat枚举,包含5种行格式:CUSTOM、SCALAR、LIST、ROW、ARRAY

  // ===== CUSTOM格式:使用自定义POJO(Plain Old Java Object)类来表示行 =====
  // 适用于字段数大于1的情况,每个字段对应POJO的一个属性
  // 优点:类型安全,可读性好,IDE支持完善
  // 缺点:需要生成额外的类文件,启动开销较大
  CUSTOM { // CUSTOM枚举值,使用自定义Java类来表示行

    @Override Type javaRowClass( // 覆盖抽象方法,返回表示行的Java类类型
        JavaTypeFactory typeFactory, // 参数:Java类型工厂,用于将Calcite类型映射到Java类型
        RelDataType type) { // 参数:行类型,包含字段的元数据信息
      assert type.getFieldCount() > 1; // 断言字段数必须大于1,CUSTOM格式用于多字段场景
      return typeFactory.getJavaClass(type); // 使用类型工厂获取该行类型对应的Java类,通常是生成的POJO类
    }

    @Override Type javaFieldClass(JavaTypeFactory typeFactory, RelDataType type, // 覆盖抽象方法,返回指定字段的Java类类型
        int index) { // 参数:index,字段索引,从0开始
      return typeFactory.getJavaClass(type.getFieldList().get(index).getType()); // 获取指定索引字段对应的Java类型
    }

    @Override public Expression record( // 覆盖方法,创建记录(行)的表达式
        Type javaRowClass, // 参数:Java行类类型,即POJO类的类型
        List<Expression> expressions) { // 参数:表达式列表,每个表达式对应一个字段值
      switch (expressions.size()) { // 根据字段数量进行不同处理
      case 0: // 如果字段数为0
        assert javaRowClass == Unit.class; // 断言行类必须是Unit类型(Unit表示空值)
        return Expressions.field(null, javaRowClass, "INSTANCE"); // 返回Unit.INSTANCE单例对象,表示空行
      default: // 默认情况,字段数大于等于1
        return Expressions.new_(javaRowClass, expressions); // 创建新的POJO对象,使用构造函数传入所有字段值
      }
    }

    @Override public MemberExpression field( // 覆盖方法,访问行的指定字段,返回字段访问表达式
        Expression expression, // 参数:行对象的表达式
        int field, // 参数:字段索引
        @Nullable Type fromType, // 参数:源类型,可能为null,表示字段值的原始类型
        Type fieldType) { // 参数:字段类型,期望的字段类型
      final Type type = expression.getType(); // 获取行对象的类型
      if (type instanceof Types.RecordType) { // 如果类型是RecordType(记录类型)
        Types.RecordType recordType = (Types.RecordType) type; // 强制转换为RecordType
        Types.RecordField recordField = // 获取指定索引的记录字段
            recordType.getRecordFields().get(field); // 从记录字段列表中获取
        return Expressions.field(expression, recordField.getDeclaringClass(), // 创建字段访问表达式,访问POJO的指定字段
            recordField.getName()); // 使用字段名和声明类
      } else { // 如果不是RecordType
        return Expressions.field(expression, Types.nthField(field, type)); // 使用反射方式访问第n个字段
      }
    }

    @Override public List<Statement> copy( // 覆盖方法,生成将行字段复制到数组的语句列表
        ParameterExpression parameter, // 参数:参数表达式,表示源行对象
        ParameterExpression outputArray, // 参数:输出数组参数表达式,目标数组
        int outputStartIndex, // 参数:输出数组起始索引
        int length) { // 参数:要复制的字段数量
      // Parameter holds an expression representing a POJO Object
      // 参数表示一个POJO对象表达式
      // Results in:
      // 生成的代码如下:
      // outputArray[outputStartIndex] = parameter.field{1};
      // outputArray[outputStartIndex] = parameter.field1; // 将第1个字段复制到数组
      // ...
      // outputArray[outputStartIndex + length - 1] = parameter.field{length - 1};
      // outputArray[outputStartIndex + length - 1] = parameter.field{length-1}; // 将最后一个字段复制到数组
      final List<Statement> statements = new ArrayList<>(length); // 创建语句列表,初始容量为字段数
      for (int i = 0; i < length; i++) { // 遍历每个字段
        statements.add( // 添加一条赋值语句
            Expressions.statement( // 创建语句表达式
                Expressions.assign( // 创建赋值表达式
            Expressions.arrayIndex(outputArray, Expressions.constant(outputStartIndex + i)), // 左边:数组索引表达式 outputArray[outputStartIndex + i]
            field(parameter, i, null, Object.class)))); // 右边:访问行对象的第i个字段
      }
      return statements; // 返回所有赋值语句列表
    }
  },

  // ===== SCALAR格式:使用单个标量值来表示行 =====
  // 适用于只有1个字段的情况,直接使用该字段的值作为行表示
  // 优点:无需包装,性能最优
  // 缺点:仅适用于单字段场景
  SCALAR { // SCALAR枚举值,使用单个标量值表示行

    @Override Type javaRowClass( // 覆盖抽象方法,返回表示行的Java类类型
        JavaTypeFactory typeFactory, // 参数:Java类型工厂
        RelDataType type) { // 参数:行类型
      assert type.getFieldCount() == 1; // 断言字段数必须为1,SCALAR格式仅用于单字段
      RelDataType field0Type = type.getFieldList().get(0).getType(); // 获取第0个字段的类型
      // nested ROW type is always represented as array.
      // 嵌套的ROW类型总是用数组表示
      if (field0Type.getSqlTypeName() == SqlTypeName.ROW) { // 如果字段类型是ROW(嵌套行类型)
        return Object[].class; // 返回Object[].class,使用数组表示嵌套行
      }
      return typeFactory.getJavaClass( // 否则,返回字段对应的Java类
          type.getFieldList().get(0).getType()); // 获取第0个字段的Java类型
    }

    @Override Type javaFieldClass(JavaTypeFactory typeFactory, RelDataType type, // 覆盖抽象方法,返回字段的Java类类型
        int index) { // 参数:字段索引
      return javaRowClass(typeFactory, type); // SCALAR格式中,行类型和字段类型相同
    }

    @Override public Expression record(Type javaRowClass, List<Expression> expressions) { // 覆盖方法,创建记录表达式
      assert expressions.size() == 1; // 断言表达式列表大小为1,只能有一个字段
      return expressions.get(0); // 直接返回该表达式,无需包装
    }

    @Override public Expression field(Expression expression, int field, @Nullable Type fromType, // 覆盖方法,访问字段
        Type fieldType) { // 参数:字段类型
      assert field == 0; // 断言字段索引必须为0,只有第0个字段
      return expression; // 直接返回表达式本身,因为行就是该字段值
    }

    @Override public List<Statement> copy( // 覆盖方法,生成将行复制到数组的语句
        ParameterExpression parameter, // 参数:源行对象(标量值)
        ParameterExpression outputArray, // 参数:目标数组
        int outputStartIndex, // 参数:输出数组起始索引
        int length) { // 参数:要复制的长度
      // Parameter holds an expression representing a scalar Object
      // 参数表示一个标量对象表达式
      // Results in:
      // 生成的代码如下:
      // outputArray[outputStartIndex] = parameter;
      // outputArray[outputStartIndex] = parameter; // 直接将标量值赋给数组元素
      assert length == 1; // 断言长度必须为1
      return FlatLists.of( // 返回包含单个语句的列表
          Expressions.statement( // 创建语句
              Expressions.assign( // 创建赋值表达式
              Expressions.arrayIndex(outputArray, // 左边:数组索引
                  Expressions.constant(outputStartIndex)), parameter))); // 右边:标量值
    }
  },

  /** A list that is comparable and immutable. Useful for records with 0 fields
   * (empty list is a good singleton) but sometimes also for records with 2 or
   * more fields that you need to be comparable, say as a key in a lookup.
   * 使用可比较且不可变的列表来表示行。适用于0个字段的记录(空列表是很好的单例)，
   * 有时也适用于需要可比较性的2个或更多字段的记录,例如作为查找的键。
   * 优点:支持比较,可以用于分组、排序等操作;不可变保证线程安全
   * 缺点:性能不如数组
   */
  LIST { // LIST枚举值,使用FlatLists.ComparableList表示行

    @Override Type javaRowClass( // 覆盖抽象方法,返回表示行的Java类类型
        JavaTypeFactory typeFactory, // 参数:Java类型工厂
        RelDataType type) { // 参数:行类型
      return FlatLists.ComparableList.class; // 返回FlatLists.ComparableList.class,可比较的不可变列表
    }

    @Override Type javaFieldClass(JavaTypeFactory typeFactory, RelDataType type, // 覆盖抽象方法,返回字段的Java类类型
        int index) { // 参数:字段索引
      return Object.class; // 列表中的元素都是Object类型
    }

    @Override public Expression record( // 覆盖方法,创建记录表达式
        Type javaRowClass, // 参数:Java行类类型
        List<Expression> expressions) { // 参数:字段值表达式列表
      switch (expressions.size()) { // 根据字段数量进行不同处理
      case 0: // 如果字段数为0
        return Expressions.field( // 返回空列表单例
          null, // 目标对象为null,访问静态字段
          FlatLists.class, // 类名
          "COMPARABLE_EMPTY_LIST"); // 字段名:COMPARABLE_EMPTY_LIST
      case 2: // 如果字段数为2
        return Expressions.convert_( // 创建类型转换表达式
            Expressions.call( // 创建方法调用表达式
                List.class, // 目标类型
                null, // 目标对象为null,静态方法
                BuiltInMethod.LIST2.method, // LIST2方法,创建包含2个元素的列表
                expressions), // 参数列表
            List.class); // 转换为List类型
      case 3: // 如果字段数为3
        return Expressions.convert_( // 创建类型转换表达式
            Expressions.call( // 创建方法调用
                List.class, // 目标类型
                null, // 静态方法
                BuiltInMethod.LIST3.method, // LIST3方法,创建包含3个元素的列表
                expressions), // 参数列表
            List.class); // 转换为List类型
      case 4: // 如果字段数为4
        return Expressions.convert_( // 创建类型转换表达式
            Expressions.call( // 创建方法调用
                List.class, // 目标类型
                null, // 静态方法
                BuiltInMethod.LIST4.method, // LIST4方法,创建包含4个元素的列表
                expressions), // 参数列表
            List.class); // 转换为List类型
      case 5: // 如果字段数为5
        return Expressions.convert_( // 创建类型转换表达式
            Expressions.call( // 创建方法调用
                List.class, // 目标类型
                null, // 静态方法
                BuiltInMethod.LIST5.method, // LIST5方法,创建包含5个元素的列表
                expressions), // 参数列表
            List.class); // 转换为List类型
      case 6: // 如果字段数为6
        return Expressions.convert_( // 创建类型转换表达式
            Expressions.call( // 创建方法调用
                List.class, // 目标类型
                null, // 静态方法
                BuiltInMethod.LIST6.method, // LIST6方法,创建包含6个元素的列表
                expressions), // 参数列表
            List.class); // 转换为List类型
      default: // 默认情况,字段数大于6或等于1
        return Expressions.convert_( // 创建类型转换表达式
            Expressions.call( // 创建方法调用
                List.class, // 目标类型
                null, // 静态方法
                BuiltInMethod.LIST_N.method, // LIST_N方法,创建包含N个元素的列表
                Expressions.newArrayInit( // 创建数组初始化表达式
                    Comparable.class, // 数组元素类型
                    expressions)), // 初始值列表
            List.class); // 转换为List类型
      }
    }

    @Override public Expression field( // 覆盖方法,访问列表的指定字段
        Expression expression, // 参数:列表对象表达式
        int field, // 参数:字段索引
        @Nullable Type fromType, // 参数:源类型,可能为null
        Type fieldType) { // 参数:期望的字段类型
      final MethodCallExpression e = // 创建方法调用表达式,调用List.get(index)方法
          Expressions.call(expression, BuiltInMethod.LIST_GET.method, // 调用LIST_GET方法
              Expressions.constant(field)); // 参数:字段索引
      if (fromType == null) { // 如果源类型为null
        fromType = e.getType(); // 使用表达式的类型作为源类型
      }
      return EnumUtils.convert(e, fromType, fieldType); // 将获取的值转换为期望的字段类型
    }

    @Override public List<Statement> copy( // 覆盖方法,生成将列表复制到数组的语句
        ParameterExpression parameter, // 参数:列表对象表达式
        ParameterExpression outputArray, // 参数:目标数组
        int outputStartIndex, // 参数:输出数组起始索引
        int length) { // 参数:要复制的长度
      // Parameter holds an expression representing a List
      // 参数表示一个List对象表达式
      // Results in:
      // 生成的代码如下:
      // System.arraycopy(parameter.toArray(), 0, outputArray, outputStartIndex, length);
      // System.arraycopy(parameter.toArray(), 0, outputArray, outputStartIndex, length); // 使用System.arraycopy批量复制
      return FlatLists.of( // 返回包含单个语句的列表
          Expressions.statement( // 创建语句
              Expressions.call(ARRAY_COPY.method, // 调用System.arraycopy方法
              Expressions.call(parameter, LIST_TO_ARRAY.method), // 第1个参数:parameter.toArray()
              Expressions.constant(0), // 第2个参数:源起始位置0
              outputArray, // 第3个参数:目标数组
              Expressions.constant(outputStartIndex), // 第4个参数:目标起始位置
              Expressions.constant(length)))); // 第5个参数:复制长度
    }
  },

  /**
   * See {@link org.apache.calcite.interpreter.Row}.
   * 参见org.apache.calcite.interpreter.Row类,使用Row对象表示行
   * Row是Calcite解释器中专门设计的行表示类
   * 优点:提供了丰富的行操作方法,如copyValues、get等
   * 缺点:性能不如数组和标量
   */
  ROW { // ROW枚举值,使用org.apache.calcite.interpreter.Row表示行

    @Override Type javaRowClass(JavaTypeFactory typeFactory, RelDataType type) { // 覆盖抽象方法,返回表示行的Java类类型
      return Row.class; // 返回Row.class
    }

    @Override Type javaFieldClass(JavaTypeFactory typeFactory, RelDataType type, // 覆盖抽象方法,返回字段的Java类类型
        int index) { // 参数:字段索引
      return Object.class; // Row中的字段都是Object类型
    }

    @Override public Expression record(Type javaRowClass, // 覆盖方法,创建记录表达式
        List<Expression> expressions) { // 参数:字段值表达式列表
      return Expressions.call(BuiltInMethod.ROW_AS_COPY.method, expressions); // 调用ROW_AS_COPY方法创建Row对象
    }

    @Override public Expression field( // 覆盖方法,访问Row的指定字段
        Expression expression, // 参数:Row对象表达式
        int field, // 参数:字段索引
        @Nullable Type fromType, // 参数:源类型,可能为null
        Type fieldType) { // 参数:期望的字段类型
      final Expression e = // 创建方法调用表达式,调用Row.value(index)方法
          Expressions.call(expression, // 目标对象:Row对象
              BuiltInMethod.ROW_VALUE.method, // 方法:ROW_VALUE
              Expressions.constant(field)); // 参数:字段索引
      if (fromType == null) { // 如果源类型为null
        fromType = e.getType(); // 使用表达式的类型作为源类型
      }
      return EnumUtils.convert(e, fromType, fieldType); // 将获取的值转换为期望的字段类型
    }

    @Override public List<Statement> copy( // 覆盖方法,生成将Row复制到数组的语句
        ParameterExpression parameter, // 参数:Row对象表达式
      ParameterExpression outputArray, // 参数:目标数组
      int outputStartIndex, // 参数:输出数组起始索引
      int length) { // 参数:要复制的长度
      // Parameter holds an expression representing a org.apache.calcite.interpreter.Row
      // 参数表示一个org.apache.calcite.interpreter.Row对象表达式
      // Results in:
      // 生成的代码如下:
      // System.arraycopy(parameter.copyValues(), 0, outputArray, outputStartIndex, length);
      // System.arraycopy(parameter.copyValues(), 0, outputArray, outputStartIndex, length); // 使用copyValues方法获取字段数组
      return FlatLists.of( // 返回包含单个语句的列表
          Expressions.statement( // 创建语句
          Expressions.call(ARRAY_COPY.method, // 调用System.arraycopy方法
              Expressions.call(Object[].class, parameter, ROW_COPY_VALUES.method), // 第1个参数:parameter.copyValues()
              Expressions.constant(0), // 第2个参数:源起始位置0
              outputArray, // 第3个参数:目标数组
              Expressions.constant(outputStartIndex), // 第4个参数:目标起始位置
              Expressions.constant(length)))); // 第5个参数:复制长度
    }
  },

  // ===== ARRAY格式:使用Object[]数组来表示行 =====
  // 通用格式,适用于任意数量的字段
  // 优点:性能好,实现简单,内存紧凑
  // 缺点:类型不安全,不支持直接比较
  ARRAY { // ARRAY枚举值,使用Object[]数组表示行

    @Override Type javaRowClass( // 覆盖抽象方法,返回表示行的Java类类型
        JavaTypeFactory typeFactory, // 参数:Java类型工厂
        RelDataType type) { // 参数:行类型
      return Object[].class; // 返回Object[].class
    }

    @Override Type javaFieldClass(JavaTypeFactory typeFactory, RelDataType type, // 覆盖抽象方法,返回字段的Java类类型
        int index) { // 参数:字段索引
      return Object.class; // 数组元素都是Object类型
    }

    @Override public Expression record(Type javaRowClass, List<Expression> expressions) { // 覆盖方法,创建记录表达式
      return Expressions.newArrayInit(Object.class, expressions); // 创建数组初始化表达式,使用所有字段值初始化数组
    }

    @Override public Expression comparer() { // 覆盖方法,返回数组比较器表达式
      return Expressions.call(BuiltInMethod.ARRAY_COMPARER.method); // 调用ARRAY_COMPARER方法,获取数组比较器
    }

    @Override public Expression field( // 覆盖方法,访问数组的指定元素
        Expression expression, // 参数:数组对象表达式
        int field, // 参数:字段索引
        @Nullable Type fromType, // 参数:源类型,可能为null
        Type fieldType) { // 参数:期望的字段类型
      final IndexExpression e = // 创建数组索引表达式: array[field]
          Expressions.arrayIndex(expression, Expressions.constant(field)); // 使用常量索引访问数组
      if (fromType == null) { // 如果源类型为null
        fromType = e.getType(); // 使用表达式的类型作为源类型
      }
      return EnumUtils.convert(e, fromType, fieldType); // 将获取的值转换为期望的字段类型
    }

    @Override public List<Statement> copy( // 覆盖方法,生成将数组复制到另一个数组的语句
        ParameterExpression parameter, // 参数:源数组
        ParameterExpression outputArray, // 参数:目标数组
        int outputStartIndex, // 参数:输出数组起始索引
        int length) { // 参数:要复制的长度
      // Parameter holds an expression representing an Object[]
      // 参数表示一个Object[]数组表达式
      // Results in:
      // 生成的代码如下:
      // System.arraycopy(parameter, 0, outputArray, outputStartIndex, length);
      // System.arraycopy(parameter, 0, outputArray, outputStartIndex, length); // 使用System.arraycopy批量复制
      return FlatLists.of( // 返回包含单个语句的列表
          Expressions.statement( // 创建语句
          Expressions.call(ARRAY_COPY.method, // 调用System.arraycopy方法
              parameter, // 第1个参数:源数组
              Expressions.constant(0), // 第2个参数:源起始位置0
              outputArray, // 第3个参数:目标数组
              Expressions.constant(outputStartIndex), // 第4个参数:目标起始位置
              Expressions.constant(length)))); // 第5个参数:复制长度
    }
  };

  // ===== 公共方法 =====

  // 优化行格式,根据行类型选择最优的行格式
  // 这是一个优化方法,根据字段数量选择更高效的行格式
  public JavaRowFormat optimize(RelDataType rowType) { // 根据行类型优化行格式
    switch (rowType.getFieldCount()) { // 根据字段数量进行优化
    case 0: // 如果字段数为0
      return LIST; // 使用LIST格式,空列表是很好的单例
    case 1: // 如果字段数为1
      return SCALAR; // 使用SCALAR格式,性能最优
    default: // 默认情况,字段数大于1
      if (this == SCALAR) { // 如果当前是SCALAR格式(不应该发生)
        return LIST; // 降级为LIST格式
      }
      return this; // 保持当前格式
    }
  }

  // 抽象方法,返回表示行的Java类类型
  // 子类必须实现此方法,返回该格式使用的Java类
  abstract Type javaRowClass(JavaTypeFactory typeFactory, RelDataType type); // 获取行对应的Java类类型

  /**
   * Returns the java class that is used to physically store the given field.
   * For instance, a non-null int field can still be stored in a field of type
   * {@code Object.class} in {@link JavaRowFormat#ARRAY} case.
   * 返回用于物理存储给定字段的Java类。
   * 例如,非空的int字段在ARRAY格式中仍然可以存储在Object.class类型的字段中。
   * 注意:物理存储类型可能与逻辑类型不同,如ARRAY格式中所有字段都是Object类型
   *
   * @param typeFactory type factory to resolve java types
   * @param typeFactory:类型工厂,用于解析Java类型
   * @param type row type
   * @param type:行类型,包含字段的元数据
   * @param index field index
   * @param index:字段索引,从0开始
   * @return java type used to store the field
   * @return:用于存储字段的Java类型
   */
  abstract Type javaFieldClass(JavaTypeFactory typeFactory, RelDataType type, // 获取字段物理存储的Java类型
      int index); // 字段索引

  // 抽象方法,创建记录(行)的表达式
  // 给定Java行类类型和字段值表达式列表,返回创建行对象的表达式
  public abstract Expression record( // 创建行记录的表达式
      Type javaRowClass, // 参数:Java行类类型
      List<Expression> expressions); // 参数:字段值表达式列表

  // 返回比较器表达式,用于比较两个行对象
  // 默认返回null,表示不支持比较
  // ARRAY格式重写了此方法,返回数组比较器
  public @Nullable Expression comparer() { // 获取行比较器表达式
    return null; // 默认不支持比较
  }

  /** Returns a reference to a particular field.
   * 返回对特定字段的引用,即访问字段的表达式
   *
   * <p>{@code fromType} may be null; if null, uses the natural type of the
   * field.
   * fromType可能为null;如果为null,则使用字段的自然类型
   * 自然类型是指字段在行对象中的实际类型
   */
  public abstract Expression field( // 访问行的指定字段,返回字段访问表达式
      Expression expression, // 参数:行对象表达式
      int field, // 参数:字段索引
      @Nullable Type fromType, // 参数:源类型,可能为null
      Type fieldType); // 参数:期望的字段类型

  /**
   * Returns an expression that copies the fields of a row of this type to the array.
   * 返回将此类型的行的字段复制到数组的表达式列表
   * 生成的代码通常使用System.arraycopy进行批量复制,性能较高
   */
  public abstract List<Statement> copy( // 生成将行字段复制到数组的语句列表
      ParameterExpression parameter, // 参数:源行对象表达式
      ParameterExpression outputArray, // 参数:目标数组表达式
      int outputStartIndex, // 参数:输出数组起始索引
      int length); // 参数:要复制的字段数量
}