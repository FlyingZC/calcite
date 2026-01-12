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
package org.apache.calcite.piglet; // Piglet包，用于Apache Calcite与Apache Pig的集成

import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // 导入Java类型工厂实现类，用于创建基于Java的类型
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示Calcite中的数据类型
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入关系数据类型系统接口，定义类型系统的行为
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义标准的SQL数据类型

import org.apache.pig.data.DataBag; // 导入Pig的DataBag类，表示Pig中的包（Bag）数据结构
import org.apache.pig.data.DataType; // 导入Pig的数据类型类，定义Pig支持的数据类型
import org.apache.pig.newplan.logical.relational.LogicalSchema; // 导入Pig的逻辑模式类，表示数据的结构

import java.util.ArrayList; // 导入ArrayList类，用于动态数组
import java.util.List; // 导入List接口，用于列表集合

/**
 * Utility methods for converting Pig data types to SQL types. // 工具类，提供将Pig数据类型转换为SQL类型的方法
 * 这个类是Piglet适配器的核心类型转换工具，负责在Pig的数据类型系统和Calcite的关系数据类型系统之间进行映射
 * Pig是Apache的数据流处理平台，有自己的类型系统；Calcite是SQL查询优化框架，使用关系代数类型系统
 * 这个类实现了两种类型系统的双向转换，使得Pig脚本可以通过Calcite进行查询优化和执行
 */
class PigTypes { // PigTypes工具类，用于Pig到SQL的类型转换
  private PigTypes() { // 私有构造方法，防止实例化，这是一个纯工具类
  }

  private static final String PIG_TUPLE_WRAPPER = "PIG_WRAPPER"; // Pig元组包装器常量，用于标识特殊的包装元组类型

  // Specialized type factory to handle type conversion // 专门用于处理类型转换的类型工厂
  static final PigRelDataTypeFactory TYPE_FACTORY = // 类型工厂常量，用于创建支持nullability的RelDataType实例
      new PigRelDataTypeFactory(RelDataTypeSystem.DEFAULT); // 使用默认的关系数据类型系统创建PigRelDataTypeFactory实例

  /**
   * Type factory that produces types with the nullability when converting // 类型工厂，在从Pig类型转换时生成支持nullability的类型
   * from Pig types. It also translates a Pig DataBag type into a multiset of // 它还将Pig DataBag类型转换为对象的多集（multiset）类型
   * objects type.
   * 这个内部类扩展了JavaTypeFactoryImpl，专门为Pig到SQL的类型转换提供支持
   * 主要功能是：1. 在创建类型时支持nullability参数；2. 将Pig的DataBag映射为SQL的多集类型
   * Pig的DataBag类似于SQL中的ARRAY或MULTISET，但具有特殊的语义
   */
  static class PigRelDataTypeFactory extends JavaTypeFactoryImpl { // Pig关系数据类型工厂，继承自JavaTypeFactoryImpl
    private PigRelDataTypeFactory(RelDataTypeSystem typeSystem) { // 私有构造方法，接收关系数据类型系统参数
      super(typeSystem); // 调用父类构造方法，初始化JavaTypeFactoryImpl
    }

    public RelDataType createSqlType(SqlTypeName typeName, boolean nullable) { // 创建SQL类型，支持指定是否可为空
      return createTypeWithNullability(super.createSqlType(typeName), nullable); // 调用父类创建类型，然后设置nullability属性
    }

    public RelDataType createStructType(List<RelDataType> typeList, // 创建结构类型（行类型），支持指定是否可为空
        List<String> fieldNameList, boolean nullable) { // 参数：类型列表、字段名列表、是否可为空标志
      return createTypeWithNullability( // 调用父类创建结构类型，然后设置nullability属性
          super.createStructType(typeList, fieldNameList), nullable); // 使用父类方法创建基础结构类型
    }

    public RelDataType createMultisetType(RelDataType type, // 创建多集类型（MULTISET），支持指定是否可为空
        long maxCardinality, boolean nullable) { // 参数：元素类型、最大基数、是否可为空标志
      return createTypeWithNullability( // 调用父类创建多集类型，然后设置nullability属性
          super.createMultisetType(type, maxCardinality), nullable); // 使用父类方法创建基础多集类型
    }

    public RelDataType createMapType(RelDataType keyType, // 创建映射类型（MAP），支持指定是否可为空
        RelDataType valueType, boolean nullable) { // 参数：键类型、值类型、是否可为空标志
      return createTypeWithNullability(super.createMapType(keyType, valueType), nullable); // 调用父类创建映射类型，然后设置nullability属性
    }

    @Override public RelDataType toSql(RelDataType type) { // 重写toSql方法，将Java类型转换为SQL类型
      if (type instanceof JavaType // 如果类型是JavaType实例
          && ((JavaType) type).getJavaClass() == DataBag.class) { // 并且Java类是DataBag（Pig的包类型）
        // We don't know the structure of each tuple inside the bag until the runtime. // 我们在运行时之前不知道包内每个元组的结构
        // Thus just consider a bag as a multiset of unknown objects. // 因此将包视为未知对象的多集
        return createMultisetType(createSqlType(SqlTypeName.ANY, true), -1, true); // 创建一个元素类型为ANY的多集类型，-1表示无上限
      }
      return super.toSql(type); // 其他情况调用父类的toSql方法进行转换
    }
  }

  /**
   * Converts a Pig schema field to relational type. // 将Pig模式字段转换为关系类型（重载方法，默认nullable为true）
   *
   * @param pigField Pig schema field // 参数：Pig模式字段（LogicalFieldSchema类型）
   * @return Relational type // 返回值：转换后的关系数据类型（RelDataType）
   */
  static RelDataType convertSchemaField(LogicalSchema.LogicalFieldSchema pigField) { // 将Pig字段模式转换为关系类型，默认可为空
    return convertSchemaField(pigField, true); // 调用重载方法，设置nullable为true
  }

  /**
   * Converts a Pig schema field to relational type. // 将Pig模式字段转换为关系类型（完整方法，支持指定nullable）
   * 这是类型转换的核心方法，实现了Pig所有数据类型到SQL类型的映射
   * Pig支持的数据类型包括：基本类型（BOOLEAN, INTEGER, LONG, FLOAT, DOUBLE等）、复杂类型（TUPLE, MAP, BAG）
   * 每种Pig类型都有对应的SQL类型映射，确保数据在两个系统之间正确传递
   *
   * @param pigField Pig schema field // 参数：Pig模式字段（LogicalFieldSchema类型），包含字段名、类型和子模式信息
   * @param nullable true if the type is nullable // 参数：布尔值，指示该类型是否可为空
   * @return Relational type // 返回值：转换后的关系数据类型（RelDataType），可在Calcite中使用
   */
  static RelDataType convertSchemaField(LogicalSchema.LogicalFieldSchema pigField, // 将Pig字段模式转换为关系类型
      boolean nullable) { // 参数：Pig字段和是否可为空标志
    switch (pigField.type) { // 根据Pig字段的类型进行switch判断
    case DataType.UNKNOWN: // 如果是未知类型
      return TYPE_FACTORY.createSqlType(SqlTypeName.ANY, nullable); // 转换为SQL的ANY类型
    case DataType.NULL: // 如果是NULL类型
      return TYPE_FACTORY.createSqlType(SqlTypeName.NULL, nullable); // 转换为SQL的NULL类型
    case DataType.BOOLEAN: // 如果是布尔类型
      return TYPE_FACTORY.createSqlType(SqlTypeName.BOOLEAN, nullable); // 转换为SQL的BOOLEAN类型
    case DataType.BYTE: // 如果是字节类型（8位整数）
      return TYPE_FACTORY.createSqlType(SqlTypeName.TINYINT, nullable); // 转换为SQL的TINYINT类型
    case DataType.INTEGER: // 如果是整数类型（32位）
      return TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER, nullable); // 转换为SQL的INTEGER类型
    case DataType.LONG: // 如果是长整型（64位）
      return TYPE_FACTORY.createSqlType(SqlTypeName.BIGINT, nullable); // 转换为SQL的BIGINT类型
    case DataType.FLOAT: // 如果是单精度浮点数
      return TYPE_FACTORY.createSqlType(SqlTypeName.REAL, nullable); // 转换为SQL的REAL类型
    case DataType.DOUBLE: // 如果是双精度浮点数
      return TYPE_FACTORY.createSqlType(SqlTypeName.DOUBLE, nullable); // 转换为SQL的DOUBLE类型
    case DataType.DATETIME: // 如果是日期时间类型
      return TYPE_FACTORY.createSqlType(SqlTypeName.DATE, nullable); // 转换为SQL的DATE类型（注意：只映射到DATE，不是TIMESTAMP）
    case DataType.BYTEARRAY: // 如果是字节数组类型
      return TYPE_FACTORY.createSqlType(SqlTypeName.BINARY, nullable); // 转换为SQL的BINARY类型
    case DataType.CHARARRAY: // 如果是字符数组类型（字符串）
      return TYPE_FACTORY.createSqlType(SqlTypeName.VARCHAR, nullable); // 转换为SQL的VARCHAR类型
    case DataType.BIGINTEGER: // 如果是大整数类型
    case DataType.BIGDECIMAL: // 如果是大十进制类型
      return TYPE_FACTORY.createSqlType(SqlTypeName.DECIMAL, nullable); // 都转换为SQL的DECIMAL类型
    case DataType.TUPLE: { // 如果是元组类型（复合类型，类似结构体）
      if (pigField.alias != null && pigField.alias.equals(PIG_TUPLE_WRAPPER)) { // 检查是否是特殊的包装元组
        if (pigField.schema == null || pigField.schema.size() != 1) { // 如果模式为空或字段数不为1
          throw new IllegalArgumentException("Expect one subfield from " + pigField.schema); // 抛出异常，期望只有一个子字段
        }
        return convertSchemaField(pigField.schema.getField(0), nullable); // 返回包装元组内部唯一的子字段类型
      }
      return convertSchema(pigField.schema, nullable); // 普通元组：递归转换整个元组模式为结构类型
    }
    case DataType.MAP: { // 如果是映射类型（键值对集合）
      final RelDataType relKey = TYPE_FACTORY.createSqlType(SqlTypeName.VARCHAR); // 创建键类型，Pig的Map键总是字符串
      if (pigField.schema == null) { // 如果没有指定值的模式
        // The default type of Pig Map value is bytearray // Pig Map值的默认类型是字节数组
        return TYPE_FACTORY.createMapType(relKey, // 创建映射类型，键为VARCHAR，值为BINARY
            TYPE_FACTORY.createSqlType(SqlTypeName.BINARY), nullable); // 使用BINARY作为默认值类型
      } else { // 如果指定了值的模式
        assert pigField.schema.size() == 1; // 断言模式中只有一个字段（Map的值类型）
        return TYPE_FACTORY.createMapType(relKey, // 创建映射类型，键为VARCHAR，值为转换后的字段类型
            convertSchemaField(pigField.schema.getField(0), nullable), nullable); // 递归转换值类型
      }
    }
    case DataType.BAG: { // 如果是包类型（元组的多重集，类似数组）
      if (pigField.schema == null) { // 如果没有指定包内元组的模式
        return TYPE_FACTORY.createMultisetType(TYPE_FACTORY.createSqlType(SqlTypeName.ANY, true), // 创建多集类型，元素为ANY类型
            -1, true); // -1表示无上限，true表示元素可为空
      }
      assert pigField.schema.size() == 1; // 断言模式中只有一个字段（包内元组的类型）
      return TYPE_FACTORY.createMultisetType( // 创建多集类型
          convertSchemaField(pigField.schema.getField(0), nullable), -1, nullable); // 递归转换包内元组类型作为元素类型
    }
    default: // 如果遇到不支持的类型
      throw new IllegalArgumentException( // 抛出非法参数异常
          "Unsupported conversion for Pig Data type: " // 错误信息：不支持的Pig数据类型转换
              + DataType.findTypeName(pigField.type)); // 显示不支持的类型名称
    }
  }

  /**
   * Converts a Pig tuple schema to a SQL row type. // 将Pig元组模式转换为SQL行类型（重载方法，默认nullable为true）
   * Pig的元组（Tuple）类似于SQL中的一行记录，包含多个字段
   * 这个方法将整个元组模式转换为Calcite的结构类型（StructType）
   *
   * @param pigSchema Pig tuple schema // 参数：Pig元组模式（LogicalSchema类型），包含多个字段定义
   * @return a SQL row type // 返回值：SQL行类型（RelDataType），表示一个结构类型
   */
  static RelDataType convertSchema(LogicalSchema pigSchema) { // 将Pig元组模式转换为SQL行类型，默认可为空
    return convertSchema(pigSchema, true); // 调用重载方法，设置nullable为true
  }

  /**
   * Converts a Pig tuple schema to a SQL row type. // 将Pig元组模式转换为SQL行类型（完整方法，支持指定nullable）
   * 这个方法处理Pig元组到SQL结构类型的转换，是convertSchemaField方法的上层封装
   * 它遍历元组中的所有字段，为每个字段生成名称和类型，然后创建结构类型
   * 如果字段没有别名，则自动生成"$0", "$1", "$2"...这样的名称
   * 如果模式为空或没有字段，则返回动态元组记录类型（DynamicTupleRecordType）
   *
   * @param pigSchema Pig tuple schema // 参数：Pig元组模式（LogicalSchema类型），包含多个字段定义
   * @param nullable true if the type is nullable // 参数：布尔值，指示整个元组类型是否可为空
   * @return a SQL row type // 返回值：SQL行类型（RelDataType），表示一个结构类型或动态类型
   */
  static RelDataType convertSchema(LogicalSchema pigSchema, boolean nullable) { // 将Pig元组模式转换为SQL行类型
    if (pigSchema != null && pigSchema.size() > 0) { // 如果模式不为空且包含字段
      List<String> fieldNameList = new ArrayList<>(); // 创建字段名列表，用于存储所有字段的名称
      List<RelDataType> typeList = new ArrayList<>(); // 创建类型列表，用于存储所有字段的类型
      for (int i = 0; i < pigSchema.size(); i++) { // 遍历模式中的所有字段
        final LogicalSchema.LogicalFieldSchema subPigField = pigSchema.getField(i); // 获取第i个字段
        fieldNameList.add(subPigField.alias != null ? subPigField.alias : "$" + i); // 如果有别名使用别名，否则生成"$i"作为字段名
        typeList.add(convertSchemaField(subPigField, nullable)); // 递归转换字段类型，添加到类型列表
      }
      return TYPE_FACTORY.createStructType(typeList, fieldNameList, nullable); // 使用类型工厂创建结构类型
    }
    return new DynamicTupleRecordType(TYPE_FACTORY); // 如果模式为空，返回动态元组记录类型
  }
}
