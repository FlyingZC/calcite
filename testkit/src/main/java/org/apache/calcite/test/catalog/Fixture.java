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
package org.apache.calcite.test.catalog; // 声明包名为 org.apache.calcite.test.catalog，表示该类属于测试目录相关的包

import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，代表 Calcite 中的数据类型系统
import org.apache.calcite.rel.type.RelDataTypeComparability; // 导入关系数据类型可比较性枚举，用于定义类型的比较规则
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建各种数据类型
import org.apache.calcite.rel.type.RelDataTypeFieldImpl; // 导入关系数据类型字段实现类，表示结构化类型的字段
import org.apache.calcite.rel.type.StructKind; // 导入结构类型种类枚举，定义结构化类型的行为（如 PEEK_FIELDS、FULLY_QUALIFIED 等）
import org.apache.calcite.sql.SqlIdentifier; // 导入 SQL 标识符类，用于表示 SQL 中的标识符（如表名、列名等）
import org.apache.calcite.sql.parser.SqlParserPos; // 导入 SQL 解析位置类，用于标记 SQL 解析过程中的位置信息
import org.apache.calcite.sql.type.ObjectSqlType; // 导入对象 SQL 类型类，用于表示自定义的结构化类型
import org.apache.calcite.sql.type.SqlTypeName; // 导入 SQL 类型名称枚举，定义所有标准 SQL 数据类型（如 INTEGER、VARCHAR 等）

import java.util.Arrays; // 导入 Java 工具类 Arrays，用于操作数组

/** Types used during initialization. */ // 类注释：定义在初始化过程中使用的各种数据类型
final class Fixture extends AbstractFixture { // 定义 Fixture 类，继承自 AbstractFixture，用于存储测试中常用的数据类型定义
  final RelDataType intType = sqlType(SqlTypeName.INTEGER); // 定义整数类型（INTEGER），不可为空，用于表示 32 位有符号整数
  final RelDataType intTypeNull = nullable(intType); // 定义可空的整数类型，基于 intType 添加可空性约束
  final RelDataType bigintType = sqlType(SqlTypeName.BIGINT); // 定义大整数类型（BIGINT），不可为空，用于表示 64 位有符号整数
  final RelDataType decimalType = sqlType(SqlTypeName.DECIMAL); // 定义十进制类型（DECIMAL），不可为空，用于表示精确的小数
  final RelDataType varcharType = sqlType(SqlTypeName.VARCHAR); // 定义变长字符类型（VARCHAR），不可为空，默认长度
  final RelDataType varcharTypeNull = nullable(varcharType); // 定义可空的变长字符类型，基于 varcharType 添加可空性约束
  final RelDataType varchar5Type = sqlType(SqlTypeName.VARCHAR, 5); // 定义长度为 5 的变长字符类型，不可为空
  final RelDataType varchar10Type = sqlType(SqlTypeName.VARCHAR, 10); // 定义长度为 10 的变长字符类型，不可为空
  final RelDataType varchar10TypeNull = nullable(varchar10Type); // 定义可空的长度为 10 的变长字符类型
  final RelDataType varchar20Type = sqlType(SqlTypeName.VARCHAR, 20); // 定义长度为 20 的变长字符类型，不可为空
  final RelDataType varchar20TypeNull = nullable(varchar20Type); // 定义可空的长度为 20 的变长字符类型
  final RelDataType timestampType = sqlType(SqlTypeName.TIMESTAMP); // 定义时间戳类型（TIMESTAMP），不可为空，用于表示日期和时间
  final RelDataType timestampTypeWithLocalTimeZone = // 定义带本地时区的时间戳类型（TIMESTAMP_WITH_LOCAL_TIME_ZONE），不可为空
      sqlType(SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE); // 用于存储根据会话时区转换后的时间戳值
  final RelDataType timestampTypeNull = nullable(timestampType); // 定义可空的时间戳类型
  final RelDataType timestampTypeWithLocalTimeZoneNull = // 定义可空的带本地时区的时间戳类型
      nullable(timestampTypeWithLocalTimeZone); // 基于带本地时区的时间戳类型添加可空性约束
  final RelDataType dateType = sqlType(SqlTypeName.DATE); // 定义日期类型（DATE），不可为空，用于表示年月日
  final RelDataType booleanType = sqlType(SqlTypeName.BOOLEAN); // 定义布尔类型（BOOLEAN），不可为空，用于表示 true/false
  final RelDataType booleanTypeNull = nullable(booleanType); // 定义可空的布尔类型
  final RelDataType rectilinearCoordType = typeFactory.builder() // 定义直角坐标类型，包含 X 和 Y 两个整型字段，用于表示二维坐标点
      .add("X", intType) // 添加 X 字段，类型为整数
      .add("Y", intType) // 添加 Y 字段，类型为整数
      .build(); // 构建结构化类型
  final RelDataType rectilinearPeekCoordType = typeFactory.builder() // 定义直角坐标的 PEEK 类型，PEEK_FIELDS 表示字段可以独立访问
      .add("X", intType) // 添加 X 字段，类型为整数
      .add("Y", intType) // 添加 Y 字段，类型为整数
      .add("unit", varchar20Type) // 添加 unit 字段，类型为长度 20 的字符串，用于表示单位
      .kind(StructKind.PEEK_FIELDS) // 设置结构类型种类为 PEEK_FIELDS，允许直接访问字段而不展开
      .build(); // 构建结构化类型
  final RelDataType rectilinearPeekCoordMultisetType = // 定义直角坐标 PEEK 类型的多重集类型，多重集表示无序可重复的集合
      typeFactory.createMultisetType(rectilinearPeekCoordType, -1); // -1 表示多重集的最大元素数量无限制
  final RelDataType rectilinearPeekNoExpandCoordType = typeFactory.builder() // 定义 PEEK_NO_EXPAND 类型，表示字段不自动展开
      .add("M", intType) // 添加 M 字段，类型为整数
      .add("SUB", // 添加 SUB 嵌套字段
          typeFactory.builder() // 创建嵌套结构类型的构建器
              .add("A", intType) // 添加 A 字段，类型为整数
              .add("B", intType) // 添加 B 字段，类型为整数
              .kind(StructKind.PEEK_FIELDS_NO_EXPAND) // 设置嵌套结构为 PEEK_FIELDS_NO_EXPAND，表示不展开字段
              .build()) // 构建嵌套结构类型
      .kind(StructKind.PEEK_FIELDS_NO_EXPAND) // 设置外层结构也为 PEEK_FIELDS_NO_EXPAND
      .build(); // 构建完整结构化类型
  final RelDataType singleRecordType = typeFactory.builder() // 定义单字段记录类型，包含一个 TYPE 字段
      .add("TYPE", varchar10Type) // 添加 TYPE 字段，类型为长度 10 的字符串
      .build(); // 构建结构化类型
  final RelDataType abRecordType = typeFactory.builder() // 定义 AB 记录类型，包含 A 和 B 两个字段
      .add("A", varchar10Type) // 添加 A 字段，类型为长度 10 的字符串
      .add("B", varchar10Type) // 添加 B 字段，类型为长度 10 的字符串
      .build(); // 构建结构化类型
  final RelDataType skillRecordType = typeFactory.builder() // 定义技能记录类型，包含 TYPE、DESC 和 OTHERS 字段
      .add("TYPE", varchar10Type) // 添加 TYPE 字段，类型为长度 10 的字符串，表示技能类型
      .add("DESC", varchar20Type) // 添加 DESC 字段，类型为长度 20 的字符串，表示技能描述
      .add("OTHERS", abRecordType) // 添加 OTHERS 字段，类型为 abRecordType，表示其他相关信息
      .build(); // 构建结构化类型
  final RelDataType empRecordType = typeFactory.builder() // 定义员工记录类型，包含员工基本信息和详情
      .add("EMPNO", intType) // 添加 EMPNO 字段，类型为整数，表示员工编号
      .add("ENAME", varchar10Type) // 添加 ENAME 字段，类型为长度 10 的字符串，表示员工姓名
      .add("DETAIL", typeFactory.builder() // 添加 DETAIL 字段，为嵌套结构类型
      .add("SKILLS", array(skillRecordType)).build()) // DETAIL 中包含 SKILLS 字段，类型为技能记录类型的数组
      .kind(StructKind.PEEK_FIELDS) // 设置结构类型种类为 PEEK_FIELDS，允许直接访问字段
      .build(); // 构建完整的员工记录类型
  final RelDataType empListType = array(empRecordType); // 定义员工列表类型，即员工记录类型的数组，用于存储多个员工记录
  final ObjectSqlType addressType = // 定义地址对象类型，使用 ObjectSqlType 创建自定义结构化类型
      new ObjectSqlType(SqlTypeName.STRUCTURED, // 指定类型名称为 STRUCTURED，表示这是一个结构化类型
          new SqlIdentifier("ADDRESS", SqlParserPos.ZERO), // 创建 SQL 标识符 "ADDRESS"，表示类型的名称，位置信息设为零
          false, // 指定该类型不是可空的（false 表示 NOT NULL）
          Arrays.asList(new RelDataTypeFieldImpl("STREET", 0, varchar20Type), // 创建字段列表：STREET 字段，索引 0，类型为长度 20 的字符串
              new RelDataTypeFieldImpl("CITY", 1, varchar20Type), // CITY 字段，索引 1，类型为长度 20 的字符串
              new RelDataTypeFieldImpl("ZIP", 2, intType), // ZIP 字段，索引 2，类型为整数，表示邮政编码
              new RelDataTypeFieldImpl("STATE", 3, varchar20Type)), // STATE 字段，索引 3，类型为长度 20 的字符串，表示州或省份
          RelDataTypeComparability.NONE); // 设置可比较性为 NONE，表示该类型的值不参与排序和比较操作
  // Row(f0 int, f1 varchar) // 注释：定义包含两个字段的行类型，f0 为整数，f1 为变长字符串
  final RelDataType recordType1 = // 定义记录类型 1，包含 f0 和 f1 两个字段
      typeFactory.createStructType(Arrays.asList(intType, varcharType), // 创建结构化类型，字段类型列表为整数和变长字符串
          Arrays.asList("f0", "f1")); // 字段名称列表为 f0 和 f1
  // Row(f0 int not null, f1 varchar null) // 注释：定义包含两个字段的行类型，f0 不可为空的整数，f1 可空的变长字符串
  final RelDataType recordType2 = // 定义记录类型 2，包含 f0 和 f1 两个字段，其中 f1 可为空
      typeFactory.createStructType(Arrays.asList(intType, nullable(varcharType)), // 创建结构化类型，f0 为整数，f1 为可空的变长字符串
          Arrays.asList("f0", "f1")); // 字段名称列表为 f0 和 f1
  // Row(f0 Row(ff0 int not null, ff1 varchar null) null, f1 timestamp not null) // 注释：定义包含嵌套结构的行类型
  final RelDataType recordType3 = // 定义记录类型 3，包含嵌套的行类型和时间戳类型
      typeFactory.createStructType( // 创建结构化类型
          Arrays.asList( // 字段类型列表
              nullable( // 第一个字段 f0 为可空类型
                  typeFactory.createStructType( // 创建嵌套的结构化类型
                      Arrays.asList(intType, varcharTypeNull), // 嵌套结构的字段类型：整数和可空的变长字符串
                      Arrays.asList("ff0", "ff1"))), // 嵌套结构的字段名称：ff0 和 ff1
              timestampType), // 第二个字段 f1 为时间戳类型
          Arrays.asList("f0", "f1")); // 字段名称列表为 f0 和 f1
  // Row(f0 bigint not null, f1 decimal null) array // 注释：定义包含大整数和十进制数的行类型的数组
  final RelDataType recordType4 = // 定义记录类型 4，为行类型的数组
      array( // 创建数组类型
          typeFactory.createStructType( // 创建结构化类型作为数组的元素类型
              Arrays.asList(bigintType, nullable(decimalType)), // 字段类型：大整数和可空的十进制数
              Arrays.asList("f0", "f1"))); // 字段名称：f0 和 f1
  // Row(f0 varchar not null, f1 timestamp null) multiset // 注释：定义包含变长字符串和时间戳的行类型的多重集
  final RelDataType recordType5 = // 定义记录类型 5，为行类型的多重集
      typeFactory.createMultisetType( // 创建多重集类型
          typeFactory.createStructType( // 创建结构化类型作为多重集的元素类型
              Arrays.asList(varcharType, timestampTypeNull), // 字段类型：变长字符串和可空的时间戳
              Arrays.asList("f0", "f1")), // 字段名称：f0 和 f1
          -1); // -1 表示多重集的最大元素数量无限制
  final RelDataType intArrayType = array(intType); // 定义整数数组类型，用于存储多个整数元素
  final RelDataType varchar5ArrayType = array(varchar5Type); // 定义长度为 5 的字符串数组类型
  final RelDataType intArrayArrayType = array(intArrayType); // 定义整数数组的数组类型，即二维整数数组
  final RelDataType varchar5ArrayArrayType = array(varchar5ArrayType); // 定义长度为 5 的字符串数组的数组类型，即二维字符串数组
  final RelDataType intMultisetType = typeFactory.createMultisetType(intType, -1); // 定义整数多重集类型，-1 表示元素数量无限制
  final RelDataType varchar5MultisetType = typeFactory.createMultisetType(varchar5Type, -1); // 定义长度为 5 的字符串多重集类型
  final RelDataType intMultisetArrayType = array(intMultisetType); // 定义整数多重集的数组类型
  final RelDataType varchar5MultisetArrayType = array(varchar5MultisetType); // 定义长度为 5 的字符串多重集的数组类型
  final RelDataType intArrayMultisetType = typeFactory.createMultisetType(intArrayType, -1); // 定义整数数组的多重集类型
  // Row(f0 int array multiset, f1 varchar(5) array) array multiset // 注释：定义包含整数数组多重集和字符串数组的行类型的多重集
  final RelDataType rowArrayMultisetType = // 定义行数组多重集类型，这是一个复杂的多重集类型
      typeFactory.createMultisetType( // 创建多重集类型
          array( // 元素类型为数组类型
              typeFactory.createStructType( // 创建结构化类型作为数组的元素类型
                  Arrays.asList(intArrayMultisetType, varchar5ArrayType), // 字段类型：整数数组多重集和长度为 5 的字符串数组
                  Arrays.asList("f0", "f1"))), // 字段名称：f0 和 f1
          -1); // -1 表示多重集的最大元素数量无限制
  final RelDataType int2IntMapType = // 定义整数到整数的映射类型，用于表示键值对集合
      typeFactory.createMapType(intType, intType); // 创建映射类型，键和值都是整数类型
  final RelDataType int2varcharArrayMapType = // 定义整数到字符串数组的映射类型
      typeFactory.createMapType(intType, array(varcharType)); // 创建映射类型，键为整数，值为字符串数组
  final RelDataType varcharMultiset2IntIntMapType = // 定义字符串多重集到整数映射的映射类型，嵌套映射结构
      typeFactory.createMapType(varchar5MultisetType, int2IntMapType); // 创建映射类型，键为字符串多重集，值为整数到整数的映射

  Fixture(RelDataTypeFactory typeFactory) { // Fixture 类的构造方法，接收一个关系数据类型工厂作为参数
    super(typeFactory); // 调用父类 AbstractFixture 的构造方法，将类型工厂传递给父类
  }

  private RelDataType nullable(RelDataType type) { // 私有辅助方法：将指定的数据类型转换为可空类型
    return typeFactory.createTypeWithNullability(type, true); // 使用类型工厂创建带有可空性的类型，第二个参数 true 表示允许为空
  }

  private RelDataType sqlType(SqlTypeName typeName, int... args) { // 私有辅助方法：创建 SQL 类型，支持可选的参数（如精度、小数位数）
    assert args.length < 3 : "unknown size of additional int args"; // 断言参数数量小于 3，确保只支持最多 2 个额外参数
    return args.length == 2 ? typeFactory.createSqlType(typeName, args[0], args[1]) // 如果有 2 个参数，创建带精度和小数位数的类型（如 DECIMAL(precision, scale)）
        : args.length == 1 ? typeFactory.createSqlType(typeName, args[0]) // 如果有 1 个参数，创建带精度的类型（如 VARCHAR(length)）
        : typeFactory.createSqlType(typeName); // 如果没有参数，创建基本类型（如 INTEGER、BOOLEAN）
  }

  private RelDataType array(RelDataType type) { // 私有辅助方法：创建指定元素类型的数组类型
    return typeFactory.createArrayType(type, -1); // 使用类型工厂创建数组类型，-1 表示数组的最大长度无限制
  }
}

/**
 * Just a little trick to store factory ref before field init in fixture. // 抽象类注释：一个小技巧，用于在 Fixture 的字段初始化之前存储类型工厂的引用
 */
abstract class AbstractFixture { // 定义抽象类 AbstractFixture，作为 Fixture 的父类，用于提前存储类型工厂引用
  final RelDataTypeFactory typeFactory; // 声明最终的关系数据类型工厂字段，用于创建各种数据类型

  AbstractFixture(RelDataTypeFactory typeFactory) { // AbstractFixture 的构造方法，接收类型工厂作为参数
    this.typeFactory = typeFactory; // 将传入的类型工厂赋值给实例变量，以便子类在字段初始化时使用
  }
}