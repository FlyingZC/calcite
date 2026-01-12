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
package org.apache.calcite.test; // 声明包名，表示这个类属于 org.apache.calcite.test 测试包

import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建各种SQL数据类型
import org.apache.calcite.test.catalog.MockCatalogReader; // 导入模拟目录读取器基类，用于创建测试用的目录读取器

import org.checkerframework.checker.nullness.qual.NonNull; // 导入非空注解，用于标记方法返回值不能为null

/** A catalog reader with tables "T1" and "T2" whose schema contains all
 * test data types. */ // 类的JavaDoc注释：这是一个目录读取器，包含表"T1"和"T2"，其模式包含所有测试数据类型
public class TCatalogReader extends MockCatalogReader { // 定义TCatalogReader类，继承自MockCatalogReader基类，用于测试的目录读取器
  private final boolean caseSensitive; // 成员变量：标识符是否区分大小写，final表示一旦初始化就不能修改

  TCatalogReader(RelDataTypeFactory typeFactory, boolean caseSensitive) { // 构造方法：接收类型工厂和大小写敏感标志作为参数
    super(typeFactory, false); // 调用父类MockCatalogReader的构造方法，传入类型工厂和false（表示不区分大小写）
    this.caseSensitive = caseSensitive; // 将传入的大小写敏感标志赋值给成员变量
  } // 构造方法结束

  /** Creates and initializes a TCatalogReader. */ // 静态工厂方法的JavaDoc注释：创建并初始化一个TCatalogReader实例
  public static @NonNull TCatalogReader create(RelDataTypeFactory typeFactory, // 静态工厂方法：创建并初始化TCatalogReader实例，返回值非空
      boolean caseSensitive) { // 参数：大小写敏感标志
    return new TCatalogReader(typeFactory, caseSensitive).init(); // 创建TCatalogReader实例并调用init方法进行初始化，返回初始化后的实例
  } // 静态工厂方法结束

  @Override public TCatalogReader init() { // 重写父类的init方法，用于初始化目录读取器，返回当前实例以支持链式调用
    final TypeCoercionTest.Fixture f = // 创建类型强制转换测试的Fixture对象，该对象包含各种测试数据类型
        TypeCoercionTest.DEFAULT_FIXTURE.withTypeFactory(typeFactory); // 使用默认的Fixture配置，但替换为传入的类型工厂
    MockSchema tSchema = new MockSchema("SALES"); // 创建名为"SALES"的模拟模式（Schema），相当于数据库中的命名空间
    registerSchema(tSchema); // 将创建的模式注册到目录读取器中，使其可以被查询
    // Register "T1" table. // 注释：注册"T1"表
    final MockTable t1 = // 创建模拟表T1的变量
        MockTable.create(this, tSchema, "T1", false, 7.0, null); // 创建名为"T1"的模拟表，指定所属模式、非流式表、行数为7.0、无统计信息
    t1.addColumn("t1_varchar20", f.varchar20Type, true); // 为T1表添加列：列名"t1_varchar20"，类型为varchar(20)，true表示可为空
    t1.addColumn("t1_smallint", f.smallintType); // 为T1表添加列：列名"t1_smallint"，类型为smallint（短整型）
    t1.addColumn("t1_int", f.intType); // 为T1表添加列：列名"t1_int"，类型为int（整型）
    t1.addColumn("t1_bigint", f.bigintType); // 为T1表添加列：列名"t1_bigint"，类型为bigint（长整型）
    t1.addColumn("t1_real", f.realType); // 为T1表添加列：列名"t1_real"，类型为real（单精度浮点数）
    t1.addColumn("t1_double", f.doubleType); // 为T1表添加列：列名"t1_double"，类型为double（双精度浮点数）
    t1.addColumn("t1_decimal", f.decimalType); // 为T1表添加列：列名"t1_decimal"，类型为decimal（定点数）
    t1.addColumn("t1_timestamp", f.timestampType); // 为T1表添加列：列名"t1_timestamp"，类型为timestamp（时间戳）
    t1.addColumn("t1_date", f.dateType); // 为T1表添加列：列名"t1_date"，类型为date（日期）
    t1.addColumn("t1_binary", f.binaryType); // 为T1表添加列：列名"t1_binary"，类型为binary（二进制数据）
    t1.addColumn("t1_boolean", f.booleanType); // 为T1表添加列：列名"t1_boolean"，类型为boolean（布尔值）
    registerTable(t1); // 将T1表注册到目录读取器中，使其可以被查询

    final MockTable t2 = // 创建模拟表T2的变量
        MockTable.create(this, tSchema, "T2", false, 7.0, null); // 创建名为"T2"的模拟表，参数同T1表
    t2.addColumn("t2_varchar20", f.varchar20Type, true); // 为T2表添加列：列名"t2_varchar20"，类型为varchar(20)，可为空
    t2.addColumn("t2_smallint", f.smallintType); // 为T2表添加列：列名"t2_smallint"，类型为smallint
    t2.addColumn("t2_int", f.intType); // 为T2表添加列：列名"t2_int"，类型为int
    t2.addColumn("t2_bigint", f.bigintType); // 为T2表添加列：列名"t2_bigint"，类型为bigint
    t2.addColumn("t2_real", f.realType); // 为T2表添加列：列名"t2_real"，类型为real
    t2.addColumn("t2_double", f.doubleType); // 为T2表添加列：列名"t2_double"，类型为double
    t2.addColumn("t2_decimal", f.decimalType); // 为T2表添加列：列名"t2_decimal"，类型为decimal
    t2.addColumn("t2_timestamp", f.timestampType); // 为T2表添加列：列名"t2_timestamp"，类型为timestamp
    t2.addColumn("t2_date", f.dateType); // 为T2表添加列：列名"t2_date"，类型为date
    t2.addColumn("t2_binary", f.binaryType); // 为T2表添加列：列名"t2_binary"，类型为binary
    t2.addColumn("t2_boolean", f.booleanType); // 为T2表添加列：列名"t2_boolean"，类型为boolean
    registerTable(t2); // 将T2表注册到目录读取器中，使其可以被查询
    return this; // 返回当前实例，支持链式调用
  } // init方法结束

  @Override public boolean isCaseSensitive() { // 重写父类的isCaseSensitive方法，用于判断标识符是否区分大小写
    return caseSensitive; // 返回成员变量caseSensitive的值
  } // isCaseSensitive方法结束
} // TCatalogReader类定义结束
