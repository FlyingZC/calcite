/* // Apache软件基金会许可证声明：本代码遵循Apache 2.0许可证，允许在遵守许可证条款的前提下使用、修改和分发
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
package org.apache.calcite.jdbc; // 声明当前类所在的包：org.apache.calcite.jdbc，这是Calcite JDBC驱动相关类的包路径

import org.apache.calcite.avatica.MetaImpl.MetaColumn; // 导入Avatica框架中的MetaColumn类，用于表示数据库表的列元数据信息
import org.apache.calcite.schema.Table; // 导入Calcite中的Table接口，表示数据库表的抽象

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的注解，用于标记可能为null的参数，帮助进行静态空值检查

import java.util.List; // 导入Java集合框架中的List接口，用于存储有序的元素列表

/** Factory for creating instances of {@link MetaColumn}. // 这是一个工厂接口，用于创建MetaColumn（元数据列）对象的实例
 * // MetaColumn是对数据库表列的元数据描述，包含列名、数据类型、长度等信息
 * // 工厂模式的使用允许不同的实现类创建不同类型的MetaColumn对象，提供了灵活性和扩展性
 *
 * @see java.sql.DatabaseMetaData#getColumns */ // 关联到JDBC标准的DatabaseMetaData.getColumns方法，该方法用于获取数据库表的列信息
public interface CalciteMetaColumnFactory { // 定义一个公共接口CalciteMetaColumnFactory，这是创建MetaColumn对象的工厂接口
  /** Instantiates a MetaColumn. */ // 方法说明：实例化并创建一个MetaColumn对象，用于描述数据库表的一个列
  // 返回值：MetaColumn对象，包含列的完整元数据信息
  // 参数table：Table对象，表示该列所属的表，提供了表的结构和属性信息
  // 参数tableCat：String类型，表示表所属的目录名称（catalog），在某些数据库系统中用于组织表
  // 参数tableSchem：String类型，表示表所属的模式名称（schema），用于在目录中进一步组织表
  // 参数tableName：String类型，表示表的名称，唯一标识一个表
  // 参数columnName：String类型，表示列的名称，在表中唯一标识一个列
  // 参数dataType：int类型，表示列的SQL数据类型，使用java.sql.Types中定义的常量（如Types.INTEGER）
  // 参数typeName：String类型，表示数据类型的名称（如"VARCHAR"、"INTEGER"），是数据库特定的类型名称
  // 参数columnSize：Integer类型，表示列的长度或精度，对于字符类型表示最大字符数，对于数值类型表示精度
  // 参数decimalDigits：Integer类型，可为null，表示小数位数，适用于DECIMAL、NUMERIC等数值类型
  // 参数numPrecRadix：int类型，表示数值精度的基数，通常为10（十进制）或2（二进制）
  // 参数nullable：int类型，表示列是否允许为null，使用DatabaseMetaData.columnNoNulls等常量
  // 参数charOctetLength：Integer类型，表示字符类型的字节长度，对于多字节字符集，可能大于字符长度
  // 参数ordinalPosition：int类型，表示列在表中的位置序号，从1开始计数
  // 参数isNullable：String类型，表示列是否可为null的字符串描述（如"YES"、"NO"）
  MetaColumn createColumn(Table table, String tableCat, String tableSchem, // 创建MetaColumn对象的方法签名开始，接收表和列的基本信息
      String tableName, String columnName, int dataType, String typeName, // 接收列名、数据类型和类型名称等参数
      Integer columnSize, @Nullable Integer decimalDigits, int numPrecRadix, // 接收列长度、小数位数和精度基数等参数，decimalDigits可为null
      int nullable, Integer charOctetLength, int ordinalPosition, // 接收是否可为null、字节长度和位置序号等参数
      String isNullable); // 接收是否可为null的字符串描述参数，方法签名结束

  /** Returns the list of expected column names. // 方法说明：返回期望的列名列表，这些列名用于描述元数据列对象的属性
   *
   * <p>The default implementation returns the columns described in the JDBC // 默认实现返回JDBC规范中定义的列名列表
   * specification. */ // 这些列名对应于DatabaseMetaData.getColumns方法返回的结果集的列名
  default List<String> getColumnNames() { // 默认方法实现，返回列名列表，使用default关键字允许接口提供默认实现
    return CalciteMetaImpl.COLUMN_COLUMNS; // 返回CalciteMetaImpl类中定义的COLUMN_COLUMNS常量，这是JDBC规范中定义的列名列表
  } // 方法结束

  /** Returns the type of object created. Must be a subclass of MetaColumn. */ // 方法说明：返回创建的对象类型，必须是MetaColumn的子类
  // 返回值：Class对象，表示创建的MetaColumn的具体子类类型
  // 这个方法用于确定工厂创建的具体MetaColumn子类类型，便于反射或其他需要类型信息的场景
  Class<? extends MetaColumn> getMetaColumnClass(); // 声明获取MetaColumn类类型的方法，返回类型是MetaColumn或其子类的Class对象

} // 接口定义结束
