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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.jdbc; // 定义包名，该类位于org.apache.calcite.jdbc包下，属于JDBC相关功能模块

import org.apache.calcite.avatica.MetaImpl.MetaColumn; // 导入Avatica框架中的MetaColumn类，用于表示元数据列信息
import org.apache.calcite.schema.Table; // 导入Calcite中的Table接口，表示数据库表结构

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的参数

/** Default implementation of CalciteMetaColumnFactoryImpl. */ // 类的JavaDoc注释：CalciteMetaColumnFactoryImpl的默认实现类
public class CalciteMetaColumnFactoryImpl // 定义类名：CalciteMetaColumnFactoryImpl，用于创建元数据列对象的工厂实现类
    implements CalciteMetaColumnFactory { // 实现CalciteMetaColumnFactory接口，提供创建元数据列的标准方法

  /** Singleton instance. */ // 成员变量注释：单例实例，全局唯一的工厂对象
  public static final CalciteMetaColumnFactoryImpl INSTANCE = // 定义公共静态常量INSTANCE，类型为CalciteMetaColumnFactoryImpl
      new CalciteMetaColumnFactoryImpl(); // 创建并初始化单例对象，通过无参构造函数实例化

  /** Internal constructor; protected to allow subclassing. */ // 构造方法注释：内部构造函数，使用protected修饰以允许子类继承
  protected CalciteMetaColumnFactoryImpl() {} // 定义protected无参构造函数，供子类调用或内部实例化使用

  @Override public MetaColumn createColumn( // 重写接口方法：createColumn，用于创建元数据列对象
      Table table, // 参数1：table，Table对象，表示所属的表（虽然方法内部未使用，但保留以备扩展）
      String tableCat, // 参数2：tableCat，表所属的目录（catalog）名称，用于标识数据库的顶层命名空间
      String tableSchem, // 参数3：tableSchem，表所属的模式（schema）名称，用于标识数据库的逻辑分组
      String tableName, // 参数4：tableName，表的名称，用于唯一标识一个表
      String columnName, // 参数5：columnName，列的名称，用于标识表中的具体字段
      int dataType, // 参数6：dataType，列的数据类型，使用SQL类型代码（如12表示VARCHAR，4表示INTEGER等）
      String typeName, // 参数7：typeName，列的数据类型名称，如"VARCHAR"、"INTEGER"等可读的类型名称
      Integer columnSize, // 参数8：columnSize，列的大小（长度），对于字符串类型表示字符长度，对于数值类型表示精度
      @Nullable Integer decimalDigits, // 参数9：decimalDigits，小数位数，对于DECIMAL/NUMERIC类型表示小数点后的位数，可为null
      int numPrecRadix, // 参数10：numPrecRadix，数值精度基数，通常为10（表示十进制）或2（表示二进制）
      int nullable, // 参数11：nullable，是否可为空，使用DatabaseMetaData定义的常量（columnNoNulls=0，columnNullable=1等）
      Integer charOctetLength, // 参数12：charOctetLength，字符字节长度，对于二进制或字符类型表示最大字节数，可为null
      int ordinalPosition, // 参数13：ordinalPosition，列的序号位置，从1开始计数，表示列在表中的顺序
      String isNullable) { // 参数14：isNullable，是否可为空的字符串表示，如"YES"、"NO"、""
    return new MetaColumn( // 创建并返回一个新的MetaColumn对象，封装所有列的元数据信息
        tableCat, // 参数1：表目录名称
        tableSchem, // 参数2：表模式名称
        tableName, // 参数3：表名称
        columnName, // 参数4：列名称
        dataType, // 参数5：数据类型代码
        typeName, // 参数6：数据类型名称
        columnSize, // 参数7：列大小
        decimalDigits, // 参数8：小数位数
        numPrecRadix, // 参数9：精度基数
        nullable, // 参数10：可为空标志
        charOctetLength, // 参数11：字符字节长度
        ordinalPosition, // 参数12：列序号位置
        isNullable); // 参数13：可为空字符串
  }

  @Override public Class<? extends MetaColumn> getMetaColumnClass() { // 重写接口方法：getMetaColumnClass，返回此工厂创建的MetaColumn类的Class对象
    return MetaColumn.class; // 返回MetaColumn的Class对象，用于反射或其他需要类类型的场景
  }
} // 类定义结束
