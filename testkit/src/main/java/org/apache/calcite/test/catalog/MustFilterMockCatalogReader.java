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
package org.apache.calcite.test.catalog; // 声明包名，该类属于org.apache.calcite.test.catalog包，用于测试目录相关的功能

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型，用于描述表列的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义SQL标准类型名称如INTEGER、VARCHAR等
import org.apache.calcite.sql.validate.SqlValidatorCatalogReader; // 导入SqlValidatorCatalogReader接口，SQL验证器读取目录信息的接口
import org.apache.calcite.sql2rel.NullInitializerExpressionFactory; // 导入NullInitializerExpressionFactory类，用于创建空初始化表达式

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，用于创建不可变列表
import com.google.common.collect.ImmutableMap; // 导入Google Guava的ImmutableMap类，用于创建不可变映射

/**
 * Mock catalog reader that tags a few columns in the tables as must-filter. // 模拟目录读取器，将表中的某些列标记为必须过滤（must-filter）列
 *
 * <p>Used for testing must-filter validation. // 用于测试必须过滤列的验证功能
 * See {@code org.apache.calcite.test.SqlValidatorTest#testMustFilterColumns()}. // 参见SqlValidatorTest测试类中的testMustFilterColumns测试方法
 */ // 类文档注释结束
public class MustFilterMockCatalogReader extends MockCatalogReader { // 定义MustFilterMockCatalogReader类，继承自MockCatalogReader基类，用于创建带有必须过滤列标记的模拟目录读取器

  // 构造方法：创建MustFilterMockCatalogReader实例 // 构造方法说明
  MustFilterMockCatalogReader(RelDataTypeFactory typeFactory, // 参数typeFactory：关系数据类型工厂，用于创建各种SQL数据类型
      boolean caseSensitive) { // 参数caseSensitive：布尔值，标识是否区分大小写，true表示区分，false表示不区分
    super(typeFactory, caseSensitive); // 调用父类MockCatalogReader的构造方法，初始化类型工厂和大小写敏感性设置
  } // 构造方法结束

  // 静态工厂方法：创建并初始化一个MustFilterMockCatalogReader实例 // 静态工厂方法说明
  public static SqlValidatorCatalogReader create(RelDataTypeFactory typeFactory, // 参数typeFactory：关系数据类型工厂，用于创建各种SQL数据类型
      boolean caseSensitive) { // 参数caseSensitive：布尔值，标识是否区分大小写
    return new MustFilterMockCatalogReader(typeFactory, caseSensitive).init(); // 创建MustFilterMockCatalogReader实例并调用init方法初始化后返回
  } // 静态工厂方法结束

  // 重写初始化方法：设置目录结构，注册表和列，并标记必须过滤列 // 方法说明
  @Override public MockCatalogReader init() { // 重写父类的init方法，返回类型为MockCatalogReader，用于初始化目录结构
    MockSchema salesSchema = new MockSchema("SALES"); // 创建名为"SALES"的模拟模式（Schema），模拟数据库中的模式结构
    registerSchema(salesSchema); // 将SALES模式注册到目录中，使其在目录中可用

    // Register "EMP" table. Must-filter fields are "EMPNO", "JOB". // 注册"EMP"表，必须过滤的字段是"EMPNO"和"JOB"
    // Bypass field of column (1): ENAME. // 绕过字段（bypass field）是索引为1的列，即ENAME列，绕过字段表示该列不需要被过滤
    MustFilterMockTable empTable = // 创建EMP表对象，类型为MustFilterMockTable，支持标记必须过滤列
        MustFilterMockTable.create(this, salesSchema, "EMP", // 调用MustFilterMockTable的静态工厂方法创建表，参数：this（当前catalog reader）、salesSchema（所属模式）、表名"EMP"
            false, 14, null, NullInitializerExpressionFactory.INSTANCE, // 参数：是否流式（false）、单调性（14）、视图宏（null）、空初始化表达式工厂实例
            false, ImmutableMap.of("EMPNO", "10", "JOB", "JOB_1"), // 参数：是否临时（false）、必须过滤列映射（EMPNO列标记为"10"，JOB列标记为"JOB_1"）
            ImmutableList.of(1)); // 参数：绕过列索引列表，这里是ImmutableList.of(1)表示索引为1的列（ENAME）是绕过列

    final RelDataType integerType = // 创建整数类型变量，final表示不可变
        typeFactory.createSqlType(SqlTypeName.INTEGER); // 使用类型工厂创建INTEGER类型的RelDataType对象，用于表示整数列
    final RelDataType timestampType = // 创建时间戳类型变量，final表示不可变
        typeFactory.createSqlType(SqlTypeName.TIMESTAMP); // 使用类型工厂创建TIMESTAMP类型的RelDataType对象，用于表示时间戳列
    final RelDataType varcharType = // 创建变长字符串类型变量，final表示不可变
        typeFactory.createSqlType(SqlTypeName.VARCHAR); // 使用类型工厂创建VARCHAR类型的RelDataType对象，用于表示字符串列
    final RelDataType booleanType = // 创建布尔类型变量，final表示不可变
        typeFactory.createSqlType(SqlTypeName.BOOLEAN); // 使用类型工厂创建BOOLEAN类型的RelDataType对象，用于表示布尔列
    empTable.addColumn("EMPNO", integerType, true); // 为EMP表添加"EMPNO"列，类型为integerType，true表示该列是主键，不能为空
    empTable.addColumn("ENAME", varcharType); // 为EMP表添加"ENAME"列，类型为varcharType，非主键列
    empTable.addColumn("JOB", varcharType); // 为EMP表添加"JOB"列，类型为varcharType，非主键列
    empTable.addColumn("MGR", integerType); // 为EMP表添加"MGR"列，类型为integerType，表示经理编号
    empTable.addColumn("HIREDATE", timestampType); // 为EMP表添加"HIREDATE"列，类型为timestampType，表示雇佣日期
    empTable.addColumn("SAL", integerType); // 为EMP表添加"SAL"列，类型为integerType，表示薪水
    empTable.addColumn("COMM", integerType); // 为EMP表添加"COMM"列，类型为integerType，表示佣金
    empTable.addColumn("DEPTNO", integerType); // 为EMP表添加"DEPTNO"列，类型为integerType，表示部门编号
    empTable.addColumn("SLACKER", booleanType); // 为EMP表添加"SLACKER"列，类型为booleanType，表示是否为懒散员工
    registerTable(empTable); // 将EMP表注册到目录中，使其在SQL查询中可用

    // Register "DEPT" table. "NAME" is a must-filter field. // 注册"DEPT"表，必须过滤的字段是"NAME"
    // Bypass field of column (0): DEPTNO. // 绕过字段是索引为0的列，即DEPTNO列
    MustFilterMockTable deptTable = // 创建DEPT表对象，类型为MustFilterMockTable
        MustFilterMockTable.create(this, salesSchema, "DEPT", // 调用MustFilterMockTable的静态工厂方法创建表，参数：this、salesSchema、表名"DEPT"
            false, 14, null, NullInitializerExpressionFactory.INSTANCE, // 参数：是否流式（false）、单调性（14）、视图宏（null）、空初始化表达式工厂实例
            false, ImmutableMap.of("NAME", "ACCOUNTING_DEPT"), // 参数：是否临时（false）、必须过滤列映射（NAME列标记为"ACCOUNTING_DEPT"）
            ImmutableList.of(0)); // 参数：绕过列索引列表，这里是ImmutableList.of(0)表示索引为0的列（DEPTNO）是绕过列
    deptTable.addColumn("DEPTNO", integerType, true); // 为DEPT表添加"DEPTNO"列，类型为integerType，true表示该列是主键
    deptTable.addColumn("NAME", varcharType); // 为DEPT表添加"NAME"列，类型为varcharType，表示部门名称
    registerTable(deptTable); // 将DEPT表注册到目录中，使其在SQL查询中可用
    return this; // 返回当前MustFilterMockCatalogReader实例，支持链式调用
  } // init方法结束
} // 类定义结束
