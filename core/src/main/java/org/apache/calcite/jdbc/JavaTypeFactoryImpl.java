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
package org.apache.calcite.jdbc; // 声明包名，该类位于org.apache.calcite.jdbc包中，是Calcite JDBC相关的类型工厂实现

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入JavaTypeFactory接口，定义了Java类型工厂的规范
import org.apache.calcite.avatica.util.ByteString; // 导入ByteString类，用于处理二进制数据
import org.apache.calcite.linq4j.Ord; // 导入Ord类，用于给列表元素添加索引
import org.apache.calcite.linq4j.tree.Primitive; // 导入Primitive类，用于处理Java基本类型
import org.apache.calcite.linq4j.tree.Types; // 导入Types类，提供类型相关的工具方法
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，定义了关系数据类型工厂的规范
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField接口，表示关系数据类型字段
import org.apache.calcite.rel.type.RelDataTypeFieldImpl; // 导入RelDataTypeFieldImpl类，关系数据类型字段的实现
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入RelDataTypeSystem接口，定义了关系数据类型系统
import org.apache.calcite.rel.type.RelRecordType; // 导入RelRecordType类，表示记录类型
import org.apache.calcite.runtime.Unit; // 导入Unit类，表示无值类型，用于0字段的记录类型
import org.apache.calcite.sql.type.BasicSqlType; // 导入BasicSqlType类，表示基本SQL类型
import org.apache.calcite.sql.type.IntervalSqlType; // 导入IntervalSqlType类，表示间隔SQL类型
import org.apache.calcite.sql.type.JavaToSqlTypeConversionRules; // 导入Java到SQL类型转换规则
import org.apache.calcite.sql.type.SqlTypeFactoryImpl; // 导入SqlTypeFactoryImpl类，SQL类型工厂的基类实现
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义了所有SQL类型名称
import org.apache.calcite.sql.type.SqlTypeUtil; // 导入SqlTypeUtil类，提供SQL类型相关的工具方法
import org.apache.calcite.util.Pair; // 导入Pair类，表示键值对
import org.apache.calcite.util.Util; // 导入Util类，提供通用的工具方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，表示可能为null的值
import org.locationtech.jts.geom.Geometry; // 导入Geometry类，表示几何类型

import java.lang.reflect.Field; // 导入Field类，用于反射获取类的字段信息
import java.lang.reflect.Modifier; // 导入Modifier类，用于获取字段的修饰符
import java.lang.reflect.Type; // 导入Type接口，表示Java类型
import java.math.BigDecimal; // 导入BigDecimal类，用于精确的小数计算
import java.util.AbstractList; // 导入AbstractList类，用于创建自定义列表
import java.util.ArrayList; // 导入ArrayList类，动态数组实现
import java.util.HashMap; // 导入HashMap类，哈希表实现
import java.util.List; // 导入List接口，表示有序集合
import java.util.Map; // 导入Map接口，表示键值对映射
import java.util.stream.Collectors; // 导入Collectors类，提供流收集器

import static org.apache.calcite.util.ReflectUtil.isStatic; // 导入isStatic静态方法，用于判断字段是否为静态

import static java.util.Objects.requireNonNull; // 导入requireNonNull静态方法，用于检查对象非空

/**
 * Implementation of {@link JavaTypeFactory}. // JavaTypeFactory接口的实现类
 * // 该类提供了Java类型与SQL类型之间的双向转换功能
 * // 核心职责：1. 将Java类型转换为Calcite的关系数据类型(RelDataType)
 * //          2. 将Calcite的关系数据类型转换为Java类型
 * //          3. 创建合成记录类型(SyntheticRecordType)用于表示没有对应Java类的记录
 * //          4. 处理数组、Map等复杂类型的转换
 *
 * <p><strong>NOTE: This class is experimental and subject to
 * change/removal without notice</strong>. // 注意：此类是实验性的，可能会随时更改或移除
 */
public class JavaTypeFactoryImpl // 类声明：Java类型工厂实现类
    extends SqlTypeFactoryImpl // 继承SqlTypeFactoryImpl，复用SQL类型工厂的基础功能
    implements JavaTypeFactory { // 实现JavaTypeFactory接口，提供Java类型特有的功能
  // 成员变量：syntheticTypes - 用于缓存合成记录类型的映射表
  // 键：字段类型和可空性的列表，每个Pair表示一个字段的类型和是否可空
  // 值：对应的合成记录类型(SyntheticRecordType)
  // 作用：避免重复创建相同的合成记录类型，提高性能并保证类型唯一性
  private final Map<List<Pair<Type, Boolean>>, SyntheticRecordType> // 合成类型缓存映射表
      syntheticTypes = new HashMap<>(); // 初始化为空的HashMap

  // 构造方法1：无参构造函数
  // 作用：创建使用默认类型系统的Java类型工厂
  // 默认类型系统：RelDataTypeSystem.DEFAULT，提供标准的SQL类型定义和约束
  public JavaTypeFactoryImpl() { // 无参构造函数
    this(RelDataTypeSystem.DEFAULT); // 调用带参构造函数，传入默认类型系统
  }

  // 构造方法2：带参构造函数
  // 参数：typeSystem - 关系数据类型系统，定义了类型系统的行为和约束
  // 作用：创建使用指定类型系统的Java类型工厂
  // 使用场景：当需要自定义类型系统行为时使用此构造函数
  public JavaTypeFactoryImpl(RelDataTypeSystem typeSystem) { // 带参构造函数
    super(typeSystem); // 调用父类SqlTypeFactoryImpl的构造函数，传入类型系统
  }

  // 方法：createStructType - 从Java类创建结构化类型(记录类型)
  // 参数：type - Java类的Class对象
  // 返回：RelDataType - 创建的关系数据类型
  // 作用：将Java类转换为Calcite的记录类型，提取类的所有非静态字段作为记录的字段
  // 处理流程：1. 遍历类的所有字段
  //          2. 过滤掉静态字段
  //          3. 对每个字段获取其类型(考虑Array和Map注解)
  //          4. 创建RelDataTypeField并添加到列表
  //          5. 创建JavaRecordType并规范化返回
  @Override public RelDataType createStructType(Class type) { // 创建结构化类型
    final List<RelDataTypeField> list = new ArrayList<>(); // 创建字段列表，用于存储记录的所有字段
    for (Field field : type.getFields()) { // 遍历类的所有字段
      if (!isStatic(field)) { // 检查字段是否为非静态字段
        // FIXME: watch out for recursion // TODO：注意可能的递归问题
        final Type fieldType = fieldType(field); // 获取字段类型，考虑Array和Map注解
        list.add( // 将字段添加到列表中
            new RelDataTypeFieldImpl(field.getName(), list.size(), // 创建关系数据字段：字段名、索引、类型
                createType(fieldType))); // 将Java类型转换为关系数据类型
      }
    }
    return canonize(new JavaRecordType(list, type)); // 创建Java记录类型并规范化返回
  }

  /** Returns the type of a field. // 方法：fieldType - 返回字段的类型
   * // 该方法处理字段的类型，特别考虑了Array和Map注解
   * // 作用：获取字段的实际类型，支持通过注解指定数组和Map的详细信息
   *
   * <p>Takes into account {@link org.apache.calcite.adapter.java.Array} // 考虑Array注解
   * annotations if present. // 如果存在Array注解，则返回ArrayType
   */
  private static Type fieldType(Field field) { // 获取字段类型
    final Class<?> klass = field.getType(); // 获取字段的基本类型
    final org.apache.calcite.adapter.java.Array array = // 获取Array注解
        field.getAnnotation(org.apache.calcite.adapter.java.Array.class); // 从字段上获取Array注解
    if (array != null) { // 如果存在Array注解
      return new Types.ArrayType(array.component(), array.componentIsNullable(), // 返回ArrayType：元素类型、元素可空性、最大基数
          array.maximumCardinality()); // 数组的最大容量
    }
    final org.apache.calcite.adapter.java.Map map = // 获取Map注解
        field.getAnnotation(org.apache.calcite.adapter.java.Map.class); // 从字段上获取Map注解
    if (map != null) { // 如果存在Map注解
      return new Types.MapType(map.key(), map.keyIsNullable(), map.value(), // 返回MapType：键类型、键可空性、值类型、值可空性
          map.valueIsNullable()); // 值的可空性
    }
    return klass; // 如果没有特殊注解，返回字段的原始类型
  }

  // 方法：createType - 将Java类型转换为关系数据类型
  // 参数：type - Java类型(Type对象，可以是Class、ArrayType、MapType等)
  // 返回：RelDataType - 转换后的关系数据类型
  // 作用：核心类型转换方法，处理各种Java类型到SQL类型的映射
  // 支持的类型：1. 基本类型(int, long等)和包装类型(Integer, Long等)
  //          2. 数组类型(ArrayType)和Map类型(MapType)
  //          3. 普通Java类(struct类型)
  //          4. List和Map集合类型
  //          5. 合成记录类型(SyntheticRecordType)
  @Override public RelDataType createType(Type type) { // 创建关系数据类型
    if (type instanceof RelDataType) { // 如果类型已经是关系数据类型
      return (RelDataType) type; // 直接返回该类型
    }
    if (type instanceof SyntheticRecordType) { // 如果是合成记录类型
      final SyntheticRecordType syntheticRecordType = // 强制转换类型
          (SyntheticRecordType) type;
      return requireNonNull( // 检查relType不为空
          syntheticRecordType.relType, // 获取合成记录类型对应的关系类型
          () -> "relType for " + syntheticRecordType); // 如果为空，提供错误信息
    }
    if (type instanceof Types.ArrayType) { // 如果是数组类型
      final Types.ArrayType arrayType = (Types.ArrayType) type; // 强制转换类型
      final RelDataType componentRelType = // 递归创建元素类型
          createType(arrayType.getComponentType()); // 获取数组元素的类型
      RelDataType result = // 创建数组类型
          createArrayType( // 调用父类方法创建数组类型
              createTypeWithNullability(componentRelType, // 设置元素类型的可空性
              arrayType.componentIsNullable()), arrayType.maximumCardinality()); // 设置数组的最大基数
      return createTypeWithNullability(result, true); // 设置数组本身可空
    }
    if (type instanceof Types.MapType) { // 如果是Map类型
      final Types.MapType mapType = (Types.MapType) type; // 强制转换类型
      final RelDataType keyRelType = createType(mapType.getKeyType()); // 递归创建键类型
      final RelDataType valueRelType = createType(mapType.getValueType()); // 递归创建值类型
      RelDataType result = // 创建Map类型
          createMapType(createTypeWithNullability(keyRelType, mapType.keyIsNullable()), // 创建键类型并设置可空性
              createTypeWithNullability(valueRelType, mapType.valueIsNullable())); // 创建值类型并设置可空性
      return createTypeWithNullability(result, true); // 设置Map本身可空
    }
    if (!(type instanceof Class)) { // 如果类型不是Class对象
      throw new UnsupportedOperationException("TODO: implement " + type); // 抛出异常，暂不支持该类型
    }
    final Class clazz = (Class) type; // 强制转换为Class对象
    switch (Primitive.flavor(clazz)) { // 判断是否为基本类型或包装类型
    case PRIMITIVE: // 如果是基本类型(int, long等)
      return createJavaType(clazz); // 创建Java类型
    case BOX: // 如果是包装类型(Integer, Long等)
      return createJavaType(Primitive.box(clazz)); // 获取对应的基本类型并创建
    default: // 其他情况
      break; // 跳出switch，继续后续处理
    }
    if (JavaToSqlTypeConversionRules.instance().lookup(clazz) != null) { // 如果该类有对应的SQL类型转换规则
      return createJavaType(clazz); // 创建Java类型
    } else if (clazz.isArray()) { // 如果是Java数组
      return createMultisetType( // 创建多重集类型
          createType(clazz.getComponentType()), -1); // 递归创建元素类型，-1表示无最大限制
    } else if (List.class.isAssignableFrom(clazz)) { // 如果是List或其子类
      return createArrayType( // 创建数组类型
          createTypeWithNullability(createSqlType(SqlTypeName.ANY), true), -1); // 元素类型为ANY，可空，无最大限制
    } else if (Map.class.isAssignableFrom(clazz)) { // 如果是Map或其子类
      return createMapType( // 创建Map类型
          createTypeWithNullability(createSqlType(SqlTypeName.ANY), true), // 键类型为ANY，可空
          createTypeWithNullability(createSqlType(SqlTypeName.ANY), true)); // 值类型为ANY，可空
    } else { // 其他情况，认为是普通Java类
      return createStructType(clazz); // 创建结构化类型(记录类型)
    }
  }

  // 方法：getJavaClass - 将关系数据类型转换为Java类型
  // 参数：type - 关系数据类型(RelDataType)
  // 返回：Type - 对应的Java类型
  // 作用：反向转换，将SQL类型映射回Java类型
  // 映射规则：1. 基本SQL类型(VARCHAR, INTEGER等)映射到对应的Java类型
  //          2. 考虑可空性：可空类型映射到包装类，非空映射到基本类型
  //          3. ROW类型映射到Java类或合成类型
  //          4. MAP类型映射到Map.class
  //          5. ARRAY/MULTISET类型映射到List.class
  @Override public Type getJavaClass(RelDataType type) { // 获取Java类类型
    if (type instanceof JavaType) { // 如果是Java类型
      JavaType javaType = (JavaType) type; // 强制转换
      return javaType.getJavaClass(); // 直接返回对应的Java类
    }
    if (type instanceof BasicSqlType || type instanceof IntervalSqlType) { // 如果是基本SQL类型或间隔类型
      switch (type.getSqlTypeName()) { // 根据SQL类型名称进行映射
      case VARCHAR: // VARCHAR类型
      case CHAR: // CHAR类型
        return String.class; // 映射到String类
      case DATE: // DATE类型
      case TIME: // TIME类型
      case TIME_WITH_LOCAL_TIME_ZONE: // 带本地时区的时间
      case TIME_TZ: // 带时区的时间
      case INTEGER: // INTEGER类型
      case INTERVAL_YEAR: // 年间隔
      case INTERVAL_YEAR_MONTH: // 年月间隔
      case INTERVAL_MONTH: // 月间隔
        return type.isNullable() ? Integer.class : int.class; // 可空返回Integer，非空返回int
      case TIMESTAMP: // TIMESTAMP类型
      case TIMESTAMP_WITH_LOCAL_TIME_ZONE: // 带本地时区的时间戳
      case TIMESTAMP_TZ: // 带时区的时间戳
      case BIGINT: // BIGINT类型
      case INTERVAL_DAY: // 天间隔
      case INTERVAL_DAY_HOUR: // 天时间间隔
      case INTERVAL_DAY_MINUTE: // 天分间隔
      case INTERVAL_DAY_SECOND: // 天秒间隔
      case INTERVAL_HOUR: // 时间隔
      case INTERVAL_HOUR_MINUTE: // 时分间隔
      case INTERVAL_HOUR_SECOND: // 时秒间隔
      case INTERVAL_MINUTE: // 分间隔
      case INTERVAL_MINUTE_SECOND: // 分秒间隔
      case INTERVAL_SECOND: // 秒间隔
        return type.isNullable() ? Long.class : long.class; // 可空返回Long，非空返回long
      case SMALLINT: // SMALLINT类型
        return type.isNullable() ? Short.class : short.class; // 可空返回Short，非空返回short
      case TINYINT: // TINYINT类型
        return type.isNullable() ? Byte.class : byte.class; // 可空返回Byte，非空返回byte
      case DECIMAL: // DECIMAL类型
        return BigDecimal.class; // 映射到BigDecimal类
      case BOOLEAN: // BOOLEAN类型
        return type.isNullable() ? Boolean.class : boolean.class; // 可空返回Boolean，非空返回boolean
      case DOUBLE: // DOUBLE类型
      case FLOAT: // FLOAT类型(注意：SQL的FLOAT映射到Java的double)
        return type.isNullable() ? Double.class : double.class; // 可空返回Double，非空返回double
      case REAL: // REAL类型
        return type.isNullable() ? Float.class : float.class; // 可空返回Float，非空返回float
      case BINARY: // BINARY类型
      case VARBINARY: // VARBINARY类型
        return ByteString.class; // 映射到ByteString类
      case GEOMETRY: // GEOMETRY类型
        return Geometry.class; // 映射到Geometry类
      case SYMBOL: // SYMBOL类型
        return Enum.class; // 映射到Enum类
      case ANY: // ANY类型
        return Object.class; // 映射到Object类
      case NULL: // NULL类型
        return Void.class; // 映射到Void类
      default: // 其他类型
        break; // 跳出switch
      }
    }
    switch (type.getSqlTypeName()) { // 再次根据SQL类型名称处理复合类型
    case ROW: // ROW类型(记录类型)
      assert type instanceof RelRecordType; // 断言类型为RelRecordType
      if (type instanceof JavaRecordType) { // 如果是Java记录类型
        return ((JavaRecordType) type).clazz; // 返回对应的Java类
      } else { // 否则创建合成类型
        return createSyntheticType((RelRecordType) type); // 创建合成记录类型
      }
    case MAP: // MAP类型
      return Map.class; // 返回Map.class
    case ARRAY: // ARRAY类型
    case MULTISET: // MULTISET类型
      return List.class; // 返回List.class
    default: // 其他类型
      break; // 跳出switch
    }
    return Object.class; // 默认返回Object.class
  }

  // 方法：toSql - 将Java格式的关系类型转换为SQL格式的关系类型
  // 参数：type - 关系数据类型
  // 返回：RelDataType - SQL格式的关系数据类型
  // 作用：将包含Java类型信息的RelDataType转换为纯SQL类型的RelDataType
  // 使用场景：当需要将Java类型信息转换为纯SQL类型时使用，例如在SQL生成阶段
  @Override public RelDataType toSql(RelDataType type) { // 转换为SQL类型
    return toSql(this, type); // 调用静态方法进行转换
  }

  /** Converts a type in Java format to a SQL-oriented type. // 方法：toSql - 静态方法，将Java格式类型转换为SQL格式类型
   * // 该方法递归处理复合类型(记录、数组、Map等)，将所有Java类型转换为SQL类型
   * 参数：typeFactory - 关系数据类型工厂，用于创建新的类型
   * 参数：type - 要转换的关系数据类型
   * 返回：RelDataType - 转换后的SQL格式关系数据类型
   */
  public static RelDataType toSql(final RelDataTypeFactory typeFactory, // 类型工厂
      RelDataType type) { // 要转换的类型
    if (type instanceof RelRecordType) { // 如果是记录类型
      return typeFactory.createTypeWithNullability( // 创建带可空性的类型
          typeFactory.createStructType( // 创建结构化类型
              type.getFieldList() // 获取字段列表
                  .stream() // 转换为流
                  .map(field -> toSql(typeFactory, field.getType())) // 递归转换每个字段的类型
                  .collect(Collectors.toList()), // 收集为列表
              type.getFieldNames()), // 字段名称列表
          type.isNullable()); // 设置可空性
    } else if (type instanceof JavaType) { // 如果是Java类型
      SqlTypeName sqlTypeName = type.getSqlTypeName(); // 获取SQL类型名称
      final RelDataType relDataType; // 声明结果变量
      if (SqlTypeUtil.isArray(type)) { // 如果是数组类型
        // Transform to sql type, take care for two cases: // 转换为SQL类型，处理两种情况：
        // 1. type.getJavaClass() is collection with erased generic type // 1. Java类是擦除泛型的集合
        // 2. ElementType returned by JavaType is also of JavaType, // 2. 元素类型也是JavaType，需要使用typeFactory转换
        // and needs conversion using typeFactory // 需要使用typeFactory进行转换
        final RelDataType elementType = // 获取元素类型
            toSqlTypeWithNullToAny(typeFactory, type.getComponentType()); // 转换元素类型，null转为ANY
        relDataType = typeFactory.createArrayType(elementType, -1); // 创建数组类型，-1表示无最大限制
      } else if (SqlTypeUtil.isMap(type)) { // 如果是Map类型
        final RelDataType keyType = // 获取键类型
            toSqlTypeWithNullToAny(typeFactory, type.getKeyType()); // 转换键类型，null转为ANY
        final RelDataType valueType = // 获取值类型
            toSqlTypeWithNullToAny(typeFactory, type.getValueType()); // 转换值类型，null转为ANY
        relDataType = typeFactory.createMapType(keyType, valueType); // 创建Map类型
      } else { // 其他情况
        relDataType = typeFactory.createSqlType(sqlTypeName); // 创建基本SQL类型
      }
      return typeFactory.createTypeWithNullability(relDataType, type.isNullable()); // 设置可空性
    }
    return type; // 其他情况直接返回原类型
  }

  // 方法：toSqlTypeWithNullToAny - 将类型转换为SQL类型，null转为ANY
  // 参数：typeFactory - 关系数据类型工厂
  // 参数：type - 要转换的类型(可能为null)
  // 返回：RelDataType - 转换后的类型，如果输入为null则返回ANY类型
  // 作用：处理null类型的情况，避免null导致的异常
  private static RelDataType toSqlTypeWithNullToAny( // 转换类型，null转为ANY
      final RelDataTypeFactory typeFactory, @Nullable RelDataType type) { // 类型工厂和可能为null的类型
    if (type == null) { // 如果类型为null
      return typeFactory.createSqlType(SqlTypeName.ANY); // 返回ANY类型
    }
    return toSql(typeFactory, type); // 否则正常转换
  }

  // 方法：createSyntheticType - 从类型列表创建合成类型
  // 参数：types - Java类型列表
  // 返回：Type - 合成记录类型
  // 作用：创建一个没有对应Java类的记录类型，用于表示查询结果等场景
  // 特殊处理：如果类型列表为空，返回Unit.class(预定义的0字段类型)
  // 命名规则：Record{字段数}_{序号}，例如Record3_0表示有3个字段的第一个合成类型
  @Override public Type createSyntheticType(List<Type> types) { // 创建合成类型
    if (types.isEmpty()) { // 如果类型列表为空
      // Unit is a pre-defined synthetic type to be used when there are 0 // Unit是预定义的合成类型，用于0字段的情况
      // fields. Because all instances are the same, we use a singleton. // 因为所有实例都相同，所以使用单例
      return Unit.class; // 返回Unit类
    }
    final String name = // 生成类型名称
        "Record" + types.size() + "_" + syntheticTypes.size(); // 格式：Record{字段数}_{序号}
    final SyntheticRecordType syntheticType = // 创建合成记录类型
        new SyntheticRecordType(null, name); // relType为null，表示这是从Java类型创建的
    for (final Ord<Type> ord : Ord.zip(types)) { // 遍历类型列表，Ord提供索引
      syntheticType.fields.add( // 添加字段到合成类型
          new RecordFieldImpl( // 创建字段实现
              syntheticType, // 所属的合成类型
              "f" + ord.i, // 字段名：f0, f1, f2...
              ord.e, // 字段类型
              !Primitive.is(ord.e), // 是否可空(基本类型不可空，其他类型可空)
              Modifier.PUBLIC)); // 修饰符为public
    }
    return register(syntheticType); // 注册并返回合成类型
  }

  // 方法：register - 注册合成类型到缓存
  // 参数：syntheticType - 要注册的合成记录类型
  // 返回：SyntheticRecordType - 缓存中的合成类型(可能是新创建的，也可能是已存在的)
  // 作用：将合成类型注册到缓存中，确保相同结构(字段类型和可空性相同)的类型只创建一次
  // 缓存键：字段类型和可空性的列表，确保类型结构的唯一性
  private SyntheticRecordType register( // 注册合成类型
      final SyntheticRecordType syntheticType) { // 要注册的合成类型
    final List<Pair<Type, Boolean>> key = // 创建缓存键：字段类型和可空性的列表
        new AbstractList<Pair<Type, Boolean>>() { // 使用匿名内部类创建自定义列表
          @Override public Pair<Type, Boolean> get(int index) { // 获取指定索引的键值对
            final Types.RecordField field = // 获取字段
                syntheticType.getRecordFields().get(index); // 从合成类型中获取字段
            return Pair.of(field.getType(), field.nullable()); // 返回类型和可空性的键值对
          }

          @Override public int size() { // 获取列表大小
            return syntheticType.getRecordFields().size(); // 返回字段数量
          }
        };
    SyntheticRecordType syntheticType2 = syntheticTypes.get(key); // 从缓存中查找是否已存在
    if (syntheticType2 == null) { // 如果不存在
      syntheticTypes.put(key, syntheticType); // 放入缓存
      return syntheticType; // 返回新创建的类型
    } else { // 如果已存在
      return syntheticType2; // 返回缓存中的类型
    }
  }

  /** Creates a synthetic Java class whose fields have the same names and // 方法：createSyntheticType - 从关系记录类型创建合成Java类型
   * relational types. // 创建一个合成Java类，其字段具有相同的名称和关系类型
   * 参数：type - 关系记录类型(RelRecordType)
   * 返回：Type - 合成记录类型
   * 作用：将关系记录类型转换为合成Java类型，用于在Java代码中表示查询结果
   * 特点：1. 字段名称与关系类型中的字段名称相同
   *          2. 字段类型通过getJavaClass方法转换为Java类型
   *          3. 考虑字段的可空性
   */
  private Type createSyntheticType(RelRecordType type) { // 从关系记录类型创建合成类型
    final String name = // 生成类型名称
        "Record" + type.getFieldCount() + "_" + syntheticTypes.size(); // 格式：Record{字段数}_{序号}
    final SyntheticRecordType syntheticType = // 创建合成记录类型
        new SyntheticRecordType(type, name); // relType为传入的关系类型
    for (final RelDataTypeField recordField : type.getFieldList()) { // 遍历关系类型的所有字段
      final Type javaClass = getJavaClass(recordField.getType()); // 将关系类型转换为Java类型
      syntheticType.fields.add( // 添加字段到合成类型
          new RecordFieldImpl( // 创建字段实现
              syntheticType, // 所属的合成类型
              recordField.getName(), // 字段名(与关系类型中的字段名相同)
              javaClass, // 字段类型(Java类型)
              recordField.getType().isNullable() // 字段是否可空(结合关系类型的可空性和Java类型的特性)
                  && !Primitive.is(javaClass), // 基本类型永远不可空
              Modifier.PUBLIC)); // 修饰符为public
    }
    return register(syntheticType); // 注册并返回合成类型
  }

  /** Synthetic record type. // 类：SyntheticRecordType - 合成记录类型
   * // 该类表示一个没有对应Java类的记录类型
   * // 用于在运行时动态创建记录类型，例如表示查询结果
   * // 特点：1. 不对应任何实际的Java类
   *          2. 字段信息在运行时动态定义
   *          3. 可以与关系类型关联，也可以独立存在
   */
  public static class SyntheticRecordType implements Types.RecordType { // 合成记录类型实现RecordType接口
    final List<Types.RecordField> fields = new ArrayList<>(); // 字段列表，存储该记录类型的所有字段
    final @Nullable RelDataType relType; // 关联的关系类型，可能为null(从Java类型创建时为null)
    private final String name; // 类型名称，格式为Record{字段数}_{序号}

    private SyntheticRecordType(@Nullable RelDataType relType, String name) { // 构造函数
      this.relType = relType; // 设置关联的关系类型
      this.name = name; // 设置类型名称
      assert relType == null // 断言：要么relType为null
             || Util.isDistinct(relType.getFieldNames()) // 要么字段名称唯一
          : "field names not distinct: " + relType; // 否则抛出错误
    }

    @Override public String getName() { // 获取类型名称
      return name; // 返回类型名称
    }

    @Override public List<Types.RecordField> getRecordFields() { // 获取所有字段
      return fields; // 返回字段列表
    }

    @Override public String toString() { // 转换为字符串
      return name; // 返回类型名称
    }
  }

  /** Implementation of a field. // 类：RecordFieldImpl - 字段实现
   * // 该类实现了Types.RecordField接口，表示合成记录类型中的一个字段
   * // 包含字段的名称、类型、可空性、修饰符等信息
   */
  private static class RecordFieldImpl implements Types.RecordField { // 字段实现类
    private final SyntheticRecordType syntheticType; // 所属的合成记录类型
    private final String name; // 字段名称
    private final Type type; // 字段类型
    private final boolean nullable; // 字段是否可空
    private final int modifiers; // 字段修饰符(如Modifier.PUBLIC)

    RecordFieldImpl( // 构造函数
        SyntheticRecordType syntheticType, // 所属的合成记录类型
        String name, // 字段名称
        Type type, // 字段类型
        boolean nullable, // 是否可空
        int modifiers) { // 修饰符
      this.syntheticType = requireNonNull(syntheticType, "syntheticType"); // 设置所属类型，检查非空
      this.name = requireNonNull(name, "name"); // 设置字段名，检查非空
      this.type = requireNonNull(type, "type"); // 设置字段类型，检查非空
      this.nullable = nullable; // 设置可空性
      this.modifiers = modifiers; // 设置修饰符
      assert !(nullable && Primitive.is(type)) // 断言：基本类型不能标记为可空
          : "type [" + type + "] can never be null"; // 如果违反断言，抛出错误
    }

    @Override public Type getType() { // 获取字段类型
      return type; // 返回字段类型
    }

    @Override public String getName() { // 获取字段名称
      return name; // 返回字段名称
    }

    @Override public int getModifiers() { // 获取字段修饰符
      return modifiers; // 返回修饰符
    }

    @Override public boolean nullable() { // 字段是否可空
      return nullable; // 返回可空性标志
    }

    @Override public @Nullable Object get(@Nullable Object o) { // 从对象中获取字段值
      throw new UnsupportedOperationException(); // 抛出异常，该方法不支持
    }

    @Override public Type getDeclaringClass() { // 获取声明该字段的类
      return syntheticType; // 返回所属的合成记录类型
    }
  }
}
