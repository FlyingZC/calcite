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
package org.apache.calcite.test.catalog; // 包声明：该类位于org.apache.calcite.test.catalog包下，属于Calcite测试工具包的一部分

import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示Calcite中的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.schema.TableMacro; // 导入表宏接口，表示可以生成表的宏
import org.apache.calcite.schema.TranslatableTable; // 导入可转换表接口，可以将表转换为关系表达式
import org.apache.calcite.schema.impl.ViewTable; // 导入视图表实现类，表示数据库视图
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义标准SQL数据类型

import org.checkerframework.checker.nullness.qual.NonNull; // 导入非空注解，用于标记不能为null的值

import java.util.Arrays; // 导入Java的数组工具类
import java.util.Collections; // 导入Java的集合工具类
import java.util.List; // 导入Java的列表接口
import java.util.function.Supplier; // 导入Java的函数式接口，用于延迟计算

/**
 * Registers dynamic tables.
 * 注册动态表的目录读取器。
 * 这个类专门用于测试环境中，模拟包含动态类型表的目录读取器。
 * 动态表是指在运行时才能确定其表结构（列定义）的表，与静态表不同。
 * 
 * <p>Not thread-safe.
 * 不是线程安全的，多线程环境下使用需要外部同步。
 * 
 * <p>该类继承自MockCatalogReader，重写了init方法来注册特定的测试表：
 * - NATION: 动态表，运行时确定结构
 * - CUSTOMER: 动态表，运行时确定结构
 * - REGION: 静态表，具有预定义的列结构
 * - CUSTOMER_MODIFIABLEVIEW: 可修改视图，基于CUSTOMER表
 */
public class MockCatalogReaderDynamic extends MockCatalogReader { // 定义模拟动态目录读取器类，继承自MockCatalogReader
  /**
   * Creates a MockCatalogReader.
   * 创建一个MockCatalogReaderDynamic实例。
   *
   * <p>Caller must then call {@link #init} to populate with data;
   * 调用者必须随后调用{@link #init}方法来填充数据；
   * constructor is protected to encourage you to call {@link #create}.
   * 构造方法受保护，以鼓励你调用{@link #create}静态工厂方法。
   *
   * @param typeFactory   Type factory
   * @param typeFactory 类型工厂，用于创建关系数据类型（如INTEGER、VARCHAR等）
   * @param caseSensitive case sensitivity
   * @param caseSensitive 是否区分大小写，true表示区分，false表示不区分
   */
  protected MockCatalogReaderDynamic(RelDataTypeFactory typeFactory, // 构造方法，接收类型工厂和大小写敏感标志
      boolean caseSensitive) { // 参数：类型工厂和是否区分大小写
    super(typeFactory, caseSensitive); // 调用父类MockCatalogReader的构造方法，初始化基础目录读取器
  }

  /** Creates and initializes a MockCatalogReaderDynamic.
   * 创建并初始化一个MockCatalogReaderDynamic实例。
   * 这是一个静态工厂方法，提供更方便的创建方式，直接返回初始化完成的对象。
   * 
   * @param typeFactory Type factory
   * @param typeFactory 类型工厂，用于创建关系数据类型
   * @param caseSensitive case sensitivity
   * @param caseSensitive 是否区分大小写
   * @return initialized MockCatalogReaderDynamic
   * @return 初始化完成的MockCatalogReaderDynamic实例
   */
  public static @NonNull MockCatalogReaderDynamic create( // 静态工厂方法，创建并初始化实例
      RelDataTypeFactory typeFactory, boolean caseSensitive) { // 参数：类型工厂和是否区分大小写
    return new MockCatalogReaderDynamic(typeFactory, caseSensitive).init(); // 创建实例并调用init方法初始化，返回自身以支持链式调用
  }

  @Override public MockCatalogReaderDynamic init() { // 重写init方法，初始化目录读取器，注册所有测试表
    // Register "DYNAMIC" schema.
    // 注册"SALES"模式（schema），这是所有表的容器
    MockSchema schema = new MockSchema("SALES"); // 创建名为SALES的模拟模式
    registerSchema(schema); // 将模式注册到目录中

    MockDynamicTable nationTable = // 创建NATION动态表
        new MockDynamicTable(schema.getCatalogName(), // 使用模式中的目录名（通常是"CATALOG"）
            schema.getName(), "NATION"); // 使用模式名和表名"NATION"
    registerTable(nationTable); // 将NATION动态表注册到目录中，该表的结构在运行时动态确定

    Supplier<MockDynamicTable> customerTableSupplier = () -> // 创建CUSTOMER动态表的供应者（Supplier），用于延迟创建
        new MockDynamicTable(schema.getCatalogName(), schema.getName(), "CUSTOMER"); // 返回新的MockDynamicTable实例

    MockDynamicTable customerTable = customerTableSupplier.get(); // 从供应者获取CUSTOMER表实例
    registerTable(customerTable); // 将CUSTOMER动态表注册到目录中

    // CREATE TABLE "REGION" - static table with known schema.
    // 创建REGION表 - 这是一个静态表，具有预定义的固定结构
    final RelDataType intType = // 创建INTEGER数据类型
        typeFactory.createSqlType(SqlTypeName.INTEGER); // 使用类型工厂创建整数类型
    final RelDataType varcharType = // 创建VARCHAR数据类型
        typeFactory.createSqlType(SqlTypeName.VARCHAR); // 使用类型工厂创建变长字符串类型

    MockTable regionTable = // 创建REGION静态表
        MockTable.create(this, schema, "REGION", false, 100); // 调用create方法，传入目录读取器、模式、表名、非流表、100行估计
    regionTable.addColumn("R_REGIONKEY", intType); // 添加R_REGIONKEY列，类型为整数（主键）
    regionTable.addColumn("R_NAME", varcharType); // 添加R_NAME列，类型为字符串（地区名称）
    regionTable.addColumn("R_COMMENT", varcharType); // 添加R_COMMENT列，类型为字符串（备注信息）
    registerTable(regionTable); // 将REGION静态表注册到目录中

    List<String> custModifiableViewNames = // 构建可修改视图的完全限定名列表
        Arrays.asList(schema.getCatalogName(), schema.getName(), // 包含目录名、模式名
            "CUSTOMER_MODIFIABLEVIEW"); // 和视图名"CUSTOMER_MODIFIABLEVIEW"
    TableMacro custModifiableViewMacro = // 创建视图宏，用于生成可修改视图表
        MockModifiableViewRelOptTable.viewMacro(rootSchema, // 调用viewMacro静态方法，传入根模式
            "select n_name from SALES.CUSTOMER", // 视图的SQL定义，从CUSTOMER表选择n_name列
            custModifiableViewNames.subList(0, 2), // 模式路径列表：[CATALOG, SALES]
            Collections.singletonList(custModifiableViewNames.get(2)), // 视图路径列表：[CUSTOMER_MODIFIABLEVIEW]
            true); // 设置为可修改视图
    TranslatableTable empModifiableView = // 应用视图宏，生成可转换的表对象
        custModifiableViewMacro.apply(Collections.emptyList()); // 传入空参数列表，返回视图表实例
    MockTable mockCustViewTable = // 创建模拟的视图表对象
        MockRelViewTable.create((ViewTable) empModifiableView, this, // 转换为ViewTable，传入目录读取器
            custModifiableViewNames.get(0), custModifiableViewNames.get(1), // 传入目录名和模式名
            custModifiableViewNames.get(2), false, 20, null); // 传入视图名、非流表、20行估计、无列解析器
    registerTable(mockCustViewTable); // 将可修改视图表注册到目录中

    // re-registers customer table to clear its row type after view registration
    // 重新注册CUSTOMER表，以清除视图注册后可能缓存的行类型
    // 这样做是为了确保动态表的行类型在每次查询时都能正确推断
    reregisterTable(customerTableSupplier.get()); // 调用reregisterTable方法，重新注册新的CUSTOMER表实例

    return this; // 返回自身，支持链式调用
  }
}
