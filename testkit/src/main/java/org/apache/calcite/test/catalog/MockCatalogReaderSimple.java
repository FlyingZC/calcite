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
 */ // Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.test.catalog; // 声明该类属于org.apache.calcite.test.catalog包

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory类，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField类，表示关系数据类型的字段
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder类，用于构建行表达式(RexNode)
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式节点
import org.apache.calcite.rex.RexUtil; // 导入RexUtil类，提供行表达式工具方法
import org.apache.calcite.sql.SqlIdentifier; // 导入SqlIdentifier类，表示SQL标识符
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类，提供标准SQL操作符表
import org.apache.calcite.sql.type.ObjectSqlType; // 导入ObjectSqlType类，表示对象SQL类型
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName类，表示SQL类型名称枚举
import org.apache.calcite.sql2rel.InitializerExpressionFactory; // 导入InitializerExpressionFactory接口，用于创建初始化表达式工厂
import org.apache.calcite.sql2rel.NullInitializerExpressionFactory; // 导入NullInitializerExpressionFactory类，提供空初始化表达式工厂
import org.apache.calcite.util.ImmutableIntList; // 导入ImmutableIntList类，表示不可变的整数列表
import org.apache.calcite.util.Litmus; // 导入Litmus枚举，用于表示检查的严格程度
import org.apache.calcite.util.Util; // 导入Util类，提供通用工具方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，表示不可变列表

import org.checkerframework.checker.nullness.qual.NonNull; // 导入NonNull注解，用于标记非空参数

import java.math.BigDecimal; // 导入BigDecimal类，用于精确的十进制数值计算
import java.util.Arrays; // 导入Arrays类，提供数组操作工具方法
import java.util.List; // 导入List接口，表示列表集合

// =================================================================================================================================================================================================
// MockCatalogReaderSimple类：Calcite测试框架的核心组件之一，提供模拟的SQL目录（Catalog），包含模式（Schema）、表（Table）、视图（View）和类型（Type）
// =================================================================================================================================================================================================

/**
 * Simple catalog reader for testing.
 */ // MockCatalogReaderSimple：简单的目录读取器，用于测试目的。该类继承自MockCatalogReader，提供了Calcite测试所需的各种模拟表、视图和模式。它包含了经典的EMP-DEPT示例数据，以及用于测试各种SQL特性的表（如流表、时态表、嵌套类型表、结构化类型表等）。该类是Calcite测试框架的核心组件之一，用于在单元测试和集成测试中提供一致的测试数据环境。
public class MockCatalogReaderSimple extends MockCatalogReader { // 定义MockCatalogReaderSimple类，继承自MockCatalogReader基类，用于提供测试用的简单目录读取功能
  private final ObjectSqlType addressType; // 声明一个final类型的ObjectSqlType成员变量addressType，用于存储地址类型信息，该类型表示一个对象SQL类型，包含多个字段的复合类型

  /**
   * Creates a MockCatalogReader.
   *
   * <p>Caller must then call {@link #init} to populate with data;
   * constructor is protected to encourage you to call {@link #create}.
   *
   * @param typeFactory   Type factory
   * @param caseSensitive case sensitivity
   */ // 创建一个MockCatalogReader的构造方法，调用者必须随后调用init方法来填充数据，构造方法受保护以鼓励调用create方法
  protected MockCatalogReaderSimple(RelDataTypeFactory typeFactory, // 构造方法参数：typeFactory，类型工厂，用于创建关系数据类型
      boolean caseSensitive) { // 构造方法参数：caseSensitive，布尔值，表示是否区分大小写
    super(typeFactory, caseSensitive); // 调用父类MockCatalogReader的构造方法，传入类型工厂和大小写敏感标志

    addressType = new Fixture(typeFactory).addressType; // 创建一个Fixture对象并初始化addressType成员变量，Fixture是测试辅助类，包含预定义的测试数据类型
  } // 构造方法结束

  /** Creates and initializes a MockCatalogReaderSimple. */ // 静态工厂方法注释：创建并初始化一个MockCatalogReaderSimple实例
  public static @NonNull MockCatalogReaderSimple create( // 静态工厂方法声明，返回非空的MockCatalogReaderSimple实例
      RelDataTypeFactory typeFactory, // 参数：typeFactory，类型工厂
      boolean caseSensitive) { // 参数：caseSensitive，是否区分大小写
    return new MockCatalogReaderSimple(typeFactory, caseSensitive).init(); // 创建MockCatalogReaderSimple实例并调用init方法进行初始化，然后返回初始化后的实例
  } // 静态工厂方法结束

  @Override public RelDataType getNamedType(SqlIdentifier typeName) { // 重写父类的getNamedType方法，根据类型名称获取命名类型
    if (typeName.equalsDeep(addressType.getSqlIdentifier(), Litmus.IGNORE)) { // 如果传入的类型名称与addressType的SQL标识符深度相等（忽略检查严格程度）
      return addressType; // 返回addressType，即地址类型
    } else { // 如果类型名称不匹配addressType
      return super.getNamedType(typeName); // 调用父类的getNamedType方法处理其他类型名称
    } // if-else语句结束
  } // getNamedType方法结束

  private void registerTableEmp(MockTable empTable, Fixture fixture) { // 私有方法：注册EMP表，添加员工相关列
    empTable.addColumn("EMPNO", fixture.intType, true); // 添加EMPNO列，类型为整数，设为主键（第三个参数true表示主键）
    empTable.addColumn("ENAME", fixture.varchar20Type); // 添加ENAME列，类型为最大长度20的变长字符串
    empTable.addColumn("JOB", fixture.varchar10Type); // 添加JOB列，类型为最大长度10的变长字符串
    empTable.addColumn("MGR", fixture.intTypeNull); // 添加MGR列，类型为可空的整数
    empTable.addColumn("HIREDATE", fixture.timestampType); // 添加HIREDATE列，类型为时间戳
    empTable.addColumn("SAL", fixture.intType); // 添加SAL列，类型为整数
    empTable.addColumn("COMM", fixture.intType); // 添加COMM列，类型为整数
    empTable.addColumn("DEPTNO", fixture.intType); // 添加DEPTNO列，类型为整数
    empTable.addColumn("SLACKER", fixture.booleanType); // 添加SLACKER列，类型为布尔值
    registerTable(empTable); // 调用registerTable方法将empTable注册到目录中
  } // registerTableEmp方法结束

  private void registerTableEmpNullables(MockTable empNullablesTable, Fixture fixture) { // 私有方法：注册EMPNULLABLES表，所有列都可为空
    empNullablesTable.addColumn("EMPNO", fixture.intType, true); // 添加EMPNO列，类型为整数，设为主键
    empNullablesTable.addColumn("ENAME", fixture.varchar20TypeNull); // 添加ENAME列，类型为可空的最大长度20的变长字符串
    empNullablesTable.addColumn("JOB", fixture.varchar10TypeNull); // 添加JOB列，类型为可空的最大长度10的变长字符串
    empNullablesTable.addColumn("MGR", fixture.intTypeNull); // 添加MGR列，类型为可空的整数
    empNullablesTable.addColumn("HIREDATE", fixture.timestampTypeNull); // 添加HIREDATE列，类型为可空的时间戳
    empNullablesTable.addColumn("SAL", fixture.intTypeNull); // 添加SAL列，类型为可空的整数
    empNullablesTable.addColumn("COMM", fixture.intTypeNull); // 添加COMM列，类型为可空的整数
    empNullablesTable.addColumn("DEPTNO", fixture.intTypeNull); // 添加DEPTNO列，类型为可空的整数
    empNullablesTable.addColumn("SLACKER", fixture.booleanTypeNull); // 添加SLACKER列，类型为可空的布尔值
    registerTable(empNullablesTable); // 调用registerTable方法将empNullablesTable注册到目录中
  } // registerTableEmpNullables方法结束

  private void registerTableEmpDefaults(MockSchema salesSchema, Fixture fixture) { // 私有方法：注册EMPDEFAULTS表，该表包含默认值
    final MockTable empDefaultsTable = // 声明最终变量empDefaultsTable，类型为MockTable
        MockTable.create(this, salesSchema, "EMPDEFAULTS", false, 14, null, // 创建MockTable实例，表名为EMPDEFAULTS，非流式表，预估行数14
            new EmpInitializerExpressionFactory(), false); // 使用EmpInitializerExpressionFactory作为初始化表达式工厂，非临时表
    empDefaultsTable.addColumn("EMPNO", fixture.intType, true); // 添加EMPNO列，类型为整数，设为主键
    empDefaultsTable.addColumn("ENAME", fixture.varchar20Type); // 添加ENAME列，类型为最大长度20的变长字符串
    empDefaultsTable.addColumn("JOB", fixture.varchar10TypeNull); // 添加JOB列，类型为可空的最大长度10的变长字符串
    empDefaultsTable.addColumn("MGR", fixture.intTypeNull); // 添加MGR列，类型为可空的整数
    empDefaultsTable.addColumn("HIREDATE", fixture.timestampTypeNull); // 添加HIREDATE列，类型为可空的时间戳
    empDefaultsTable.addColumn("SAL", fixture.intTypeNull); // 添加SAL列，类型为可空的整数
    empDefaultsTable.addColumn("COMM", fixture.intTypeNull); // 添加COMM列，类型为可空的整数
    empDefaultsTable.addColumn("DEPTNO", fixture.intTypeNull); // 添加DEPTNO列，类型为可空的整数
    empDefaultsTable.addColumn("SLACKER", fixture.booleanTypeNull); // 添加SLACKER列，类型为可空的布尔值
    registerTable(empDefaultsTable); // 调用registerTable方法将empDefaultsTable注册到目录中
  } // registerTableEmpDefaults方法结束

  private void registerTableEmpB(MockSchema salesSchema, Fixture fixture) { // 私有方法：注册EMP_B表，在EMP表基础上增加了BIRTHDATE列
    final MockTable empBTable = // 声明最终变量empBTable，类型为MockTable
        MockTable.create(this, salesSchema, "EMP_B", false, 14); // 创建MockTable实例，表名为EMP_B，非流式表，预估行数14
    empBTable.addColumn("EMPNO", fixture.intType, true); // 添加EMPNO列，类型为整数，设为主键
    empBTable.addColumn("ENAME", fixture.varchar20Type); // 添加ENAME列，类型为最大长度20的变长字符串
    empBTable.addColumn("JOB", fixture.varchar10Type); // 添加JOB列，类型为最大长度10的变长字符串
    empBTable.addColumn("MGR", fixture.intTypeNull); // 添加MGR列，类型为可空的整数
    empBTable.addColumn("HIREDATE", fixture.timestampType); // 添加HIREDATE列，类型为时间戳
    empBTable.addColumn("SAL", fixture.intType); // 添加SAL列，类型为整数
    empBTable.addColumn("COMM", fixture.intType); // 添加COMM列，类型为整数
    empBTable.addColumn("DEPTNO", fixture.intType); // 添加DEPTNO列，类型为整数
    empBTable.addColumn("SLACKER", fixture.booleanType); // 添加SLACKER列，类型为布尔值
    empBTable.addColumn("BIRTHDATE", fixture.dateType); // 添加BIRTHDATE列，类型为日期，这是与EMP表的区别
    registerTable(empBTable); // 调用registerTable方法将empBTable注册到目录中
  } // registerTableEmpB方法结束

  private void registerTableDept(MockSchema salesSchema, Fixture fixture) { // 私有方法：注册DEPT表，表示部门信息
    MockTable deptTable = MockTable.create(this, salesSchema, "DEPT", false, 4); // 创建MockTable实例，表名为DEPT，非流式表，预估行数4
    deptTable.addColumn("DEPTNO", fixture.intType, true); // 添加DEPTNO列，类型为整数，设为主键
    deptTable.addColumn("NAME", fixture.varchar10Type); // 添加NAME列，类型为最大长度10的变长字符串
    registerTable(deptTable); // 调用registerTable方法将deptTable注册到目录中
  } // registerTableDept方法结束

  private void registerTableDeptNullables(MockSchema salesSchema, Fixture fixture) { // 私有方法：注册DEPTNULLABLES表，所有列都可为空
    MockTable deptNullablesTable = MockTable.create(this, salesSchema, "DEPTNULLABLES", false, 4); // 创建MockTable实例，表名为DEPTNULLABLES，非流式表，预估行数4
    deptNullablesTable.addColumn("DEPTNO", fixture.intTypeNull, true); // 添加DEPTNO列，类型为可空的整数，设为主键
    deptNullablesTable.addColumn("NAME", fixture.varchar10TypeNull); // 添加NAME列，类型为可空的最大长度10的变长字符串
    registerTable(deptNullablesTable); // 调用registerTable方法将deptNullablesTable注册到目录中
  } // registerTableDeptNullables方法结束

  private void registerTableDeptSingle(MockSchema salesSchema, Fixture fixture) { // 私有方法：注册DEPT_SINGLE表，包含单个记录类型的列
    MockTable deptSingleTable = // 声明变量deptSingleTable，类型为MockTable
        MockTable.create(this, salesSchema, "DEPT_SINGLE", false, 4); // 创建MockTable实例，表名为DEPT_SINGLE，非流式表，预估行数4
    deptSingleTable.addColumn("SKILL", fixture.singleRecordType); // 添加SKILL列，类型为单个记录类型（复合类型）
    registerTable(deptSingleTable); // 调用registerTable方法将deptSingleTable注册到目录中
  } // registerTableDeptSingle方法结束

  private void registerTableDeptNested(MockSchema salesSchema, Fixture fixture) { // 私有方法：注册DEPT_NESTED表，包含嵌套类型的列
    MockTable deptNestedTable = // 声明变量deptNestedTable，类型为MockTable
        MockTable.create(this, salesSchema, "DEPT_NESTED", false, 4); // 创建MockTable实例，表名为DEPT_NESTED，非流式表，预估行数4
    deptNestedTable.addColumn("DEPTNO", fixture.intType, true); // 添加DEPTNO列，类型为整数，设为主键
    deptNestedTable.addColumn("NAME", fixture.varchar10Type); // 添加NAME列，类型为最大长度10的变长字符串
    deptNestedTable.addColumn("SKILL", fixture.skillRecordType); // 添加SKILL列，类型为技能记录类型（复合类型）
    deptNestedTable.addColumn("EMPLOYEES", fixture.empListType); // 添加EMPLOYEES列，类型为员工列表类型（集合类型）
    registerTable(deptNestedTable); // 调用registerTable方法将deptNestedTable注册到目录中
  } // registerTableDeptNested方法结束

  private void registerTableDeptNestedExpanded(MockSchema salesSchema, Fixture fixture) { // 私有方法：注册DEPT_NESTED_EXPANDED表，包含扩展的嵌套类型列
    MockTable deptNestedExpandedTable = // 声明变量deptNestedExpandedTable，类型为MockTable
        MockTable.create(this, salesSchema, "DEPT_NESTED_EXPANDED", false, 4); // 创建MockTable实例，表名为DEPT_NESTED_EXPANDED，非流式表，预估行数4
    deptNestedExpandedTable.addColumn("DEPTNO", fixture.intType, true); // 添加DEPTNO列，类型为整数，设为主键
    deptNestedExpandedTable.addColumn("NAME", fixture.varchar10Type); // 添加NAME列，类型为最大长度10的变长字符串
    deptNestedExpandedTable.addColumn("EMPLOYEES", fixture.empListType); // 添加EMPLOYEES列，类型为员工列表类型（集合类型）
    deptNestedExpandedTable.addColumn("ADMINS", fixture.varchar5ArrayType); // 添加ADMINS列，类型为最大长度5的变长字符串数组类型
    deptNestedExpandedTable.addColumn("OFFICES", fixture.rectilinearPeekCoordMultisetType); // 添加OFFICES列，类型为直角坐标多重集类型
    registerTable(deptNestedExpandedTable); // 调用registerTable方法将deptNestedExpandedTable注册到目录中
  } // registerTableDeptNestedExpanded方法结束

  private void registerTableBonus(MockSchema salesSchema, Fixture fixture) { // 私有方法：注册BONUS表，表示奖金信息
    MockTable bonusTable = // 声明变量bonusTable，类型为MockTable
        MockTable.create(this, salesSchema, "BONUS", false, 0); // 创建MockTable实例，表名为BONUS，非流式表，预估行数0（空表）
    bonusTable.addColumn("ENAME", fixture.varchar20Type); // 添加ENAME列，类型为最大长度20的变长字符串
    bonusTable.addColumn("JOB", fixture.varchar10Type); // 添加JOB列，类型为最大长度10的变长字符串
    bonusTable.addColumn("SAL", fixture.intType); // 添加SAL列，类型为整数
    bonusTable.addColumn("COMM", fixture.intType); // 添加COMM列，类型为整数
    registerTable(bonusTable); // 调用registerTable方法将bonusTable注册到目录中
  } // registerTableBonus方法结束

  private void registerTableSalgrade(MockSchema salesSchema, Fixture fixture) { // 私有方法：注册SALGRADE表，表示薪资等级信息
    MockTable salgradeTable = // 声明变量salgradeTable，类型为MockTable
        MockTable.create(this, salesSchema, "SALGRADE", false, 5); // 创建MockTable实例，表名为SALGRADE，非流式表，预估行数5
    salgradeTable.addColumn("GRADE", fixture.intType, true); // 添加GRADE列，类型为整数，设为主键
    salgradeTable.addColumn("LOSAL", fixture.intType); // 添加LOSAL列，类型为整数，表示最低薪资
    salgradeTable.addColumn("HISAL", fixture.intType); // 添加HISAL列，类型为整数，表示最高薪资
    registerTable(salgradeTable); // 调用registerTable方法将salgradeTable注册到目录中
  } // registerTableSalgrade方法结束

  private void registerTableEmpAddress(MockSchema salesSchema, Fixture fixture) { // 私有方法：注册EMP_ADDRESS表，包含地址类型的列
    MockTable contactAddressTable = // 声明变量contactAddressTable，类型为MockTable
        MockTable.create(this, salesSchema, "EMP_ADDRESS", false, 26); // 创建MockTable实例，表名为EMP_ADDRESS，非流式表，预估行数26
    contactAddressTable.addColumn("EMPNO", fixture.intType, true); // 添加EMPNO列，类型为整数，设为主键
    contactAddressTable.addColumn("HOME_ADDRESS", addressType); // 添加HOME_ADDRESS列，类型为addressType（地址对象类型）
    contactAddressTable.addColumn("MAILING_ADDRESS", addressType); // 添加MAILING_ADDRESS列，类型为addressType（地址对象类型）
    registerTable(contactAddressTable); // 调用registerTable方法将contactAddressTable注册到目录中

  } // registerTableEmpAddress方法结束

  private void registerTableContact(MockSchema customerSchema, Fixture fixture) { // 私有方法：注册CONTACT表，表示联系人信息
    MockTable contactTable = // 声明变量contactTable，类型为MockTable
        MockTable.create(this, customerSchema, "CONTACT", false, 1000); // 创建MockTable实例，表名为CONTACT，非流式表，预估行数1000
    contactTable.addColumn("CONTACTNO", fixture.intType); // 添加CONTACTNO列，类型为整数
    contactTable.addColumn("FNAME", fixture.varchar10Type); // 添加FNAME列，类型为最大长度10的变长字符串（名）
    contactTable.addColumn("LNAME", fixture.varchar10Type); // 添加LNAME列，类型为最大长度10的变长字符串（姓）
    contactTable.addColumn("EMAIL", fixture.varchar20Type); // 添加EMAIL列，类型为最大长度20的变长字符串
    contactTable.addColumn("COORD", fixture.rectilinearCoordType); // 添加COORD列，类型为直角坐标类型
    registerTable(contactTable); // 调用registerTable方法将contactTable注册到目录中
  } // registerTableContact方法结束

  private void registerTableContactPeek(MockSchema customerSchema, Fixture fixture) { // 私有方法：注册CONTACT_PEEK表，包含特殊坐标类型的列
    MockTable contactPeekTable = // 声明变量contactPeekTable，类型为MockTable
        MockTable.create(this, customerSchema, "CONTACT_PEEK", false, 1000); // 创建MockTable实例，表名为CONTACT_PEEK，非流式表，预估行数1000
    contactPeekTable.addColumn("CONTACTNO", fixture.intType); // 添加CONTACTNO列，类型为整数
    contactPeekTable.addColumn("FNAME", fixture.varchar10Type); // 添加FNAME列，类型为最大长度10的变长字符串（名）
    contactPeekTable.addColumn("LNAME", fixture.varchar10Type); // 添加LNAME列，类型为最大长度10的变长字符串（姓）
    contactPeekTable.addColumn("EMAIL", fixture.varchar20Type); // 添加EMAIL列，类型为最大长度20的变长字符串
    contactPeekTable.addColumn("COORD", fixture.rectilinearPeekCoordType); // 添加COORD列，类型为直角坐标Peek类型（用于空间数据查询）
    contactPeekTable.addColumn("COORD_NE", fixture.rectilinearPeekNoExpandCoordType); // 添加COORD_NE列，类型为直角坐标Peek无扩展类型
    registerTable(contactPeekTable); // 调用registerTable方法将contactPeekTable注册到目录中
  } // registerTableContactPeek方法结束

  private void registerTableAccount(MockSchema customerSchema, Fixture fixture) { // 私有方法：注册ACCOUNT表，表示账户信息
    MockTable accountTable = // 声明变量accountTable，类型为MockTable
        MockTable.create(this, customerSchema, "ACCOUNT", false, 457); // 创建MockTable实例，表名为ACCOUNT，非流式表，预估行数457
    accountTable.addColumn("ACCTNO", fixture.intType); // 添加ACCTNO列，类型为整数（账户编号）
    accountTable.addColumn("TYPE", fixture.varchar20Type); // 添加TYPE列，类型为最大长度20的变长字符串（账户类型）
    accountTable.addColumn("BALANCE", fixture.intType); // 添加BALANCE列，类型为整数（账户余额）
    registerTable(accountTable); // 调用registerTable方法将accountTable注册到目录中
  } // registerTableAccount方法结束

  private void registerTableOrders(MockSchema salesSchema, Fixture fixture) { // 私有方法：注册ORDERS流表，表示订单流数据
    MockTable ordersStream = // 声明变量ordersStream，类型为MockTable
        MockTable.create(this, salesSchema, "ORDERS", true, // 创建MockTable实例，表名为ORDERS，第三个参数true表示是流式表
            Double.POSITIVE_INFINITY); // 预估行数为正无穷大（流式表数据持续产生）
    ordersStream.addColumn("ROWTIME", fixture.timestampType); // 添加ROWTIME列，类型为时间戳（行时间，流式表特有的列）
    ordersStream.addMonotonic("ROWTIME"); // 将ROWTIME列标记为单调递增列，流式表的时间戳通常单调递增
    ordersStream.addColumn("PRODUCTID", fixture.intType); // 添加PRODUCTID列，类型为整数（产品ID）
    ordersStream.addColumn("ORDERID", fixture.intType); // 添加ORDERID列，类型为整数（订单ID）
    registerTable(ordersStream); // 调用registerTable方法将ordersStream注册到目录中
  } // registerTableOrders方法结束

  private void registerTableShipments(MockSchema salesSchema, Fixture fixture) { // 私有方法：注册SHIPMENTS流表，表示发货流数据
    // "ROWTIME" is not column 0, just to mix things up. // 注释说明：ROWTIME不是第0列，这是为了测试不同场景
    MockTable shipmentsStream = // 声明变量shipmentsStream，类型为MockTable
        MockTable.create(this, salesSchema, "SHIPMENTS", true, // 创建MockTable实例，表名为SHIPMENTS，第三个参数true表示是流式表
            Double.POSITIVE_INFINITY); // 预估行数为正无穷大（流式表数据持续产生）
    shipmentsStream.addColumn("ORDERID", fixture.intType); // 添加ORDERID列，类型为整数（订单ID），这是第0列
    shipmentsStream.addColumn("ROWTIME", fixture.timestampType); // 添加ROWTIME列，类型为时间戳（行时间），这是第1列，不是第0列
    shipmentsStream.addMonotonic("ROWTIME"); // 将ROWTIME列标记为单调递增列
    registerTable(shipmentsStream); // 调用registerTable方法将shipmentsStream注册到目录中
  } // registerTableShipments方法结束

  private void registerTableProducts(MockSchema salesSchema, Fixture fixture) { // 私有方法：注册PRODUCTS表，表示产品信息
    MockTable productsTable = // 声明变量productsTable，类型为MockTable
        MockTable.create(this, salesSchema, "PRODUCTS", false, 200D); // 创建MockTable实例，表名为PRODUCTS，非流式表，预估行数200
    productsTable.addColumn("PRODUCTID", fixture.intType); // 添加PRODUCTID列，类型为整数（产品ID）
    productsTable.addColumn("NAME", fixture.varchar20Type); // 添加NAME列，类型为最大长度20的变长字符串（产品名称）
    productsTable.addColumn("SUPPLIERID", fixture.intType); // 添加SUPPLIERID列，类型为整数（供应商ID）
    registerTable(productsTable); // 调用registerTable方法将productsTable注册到目录中
  } // registerTableProducts方法结束

  private void registerTableEmptyProducts(MockSchema salesSchema, Fixture fixture) { // 私有方法：注册EMPTY_PRODUCTS表，表示空产品表
    MockTable emptyProductsTable = // 声明变量emptyProductsTable，类型为MockTable
        MockTable.create(this, salesSchema, "EMPTY_PRODUCTS", false, 0D, 0D); // 创建MockTable实例，表名为EMPTY_PRODUCTS，非流式表，预估行数0（空表），两个0D参数分别表示原始行数和不同行数
    emptyProductsTable.addColumn("PRODUCTID", fixture.intType); // 添加PRODUCTID列，类型为整数（产品ID）
    emptyProductsTable.addColumn("NAME", fixture.varchar20Type); // 添加NAME列，类型为最大长度20的变长字符串（产品名称）
    emptyProductsTable.addColumn("SUPPLIERID", fixture.intType); // 添加SUPPLIERID列，类型为整数（供应商ID）
    registerTable(emptyProductsTable); // 调用registerTable方法将emptyProductsTable注册到目录中
  } // registerTableEmptyProducts方法结束

  private void registerTableProductsTemporal(MockSchema salesSchema, Fixture fixture) { // 私有方法：注册PRODUCTS_TEMPORAL表，表示时态产品表，包含时间戳列
    MockTable productsTemporalTable = // 声明变量productsTemporalTable，类型为MockTable
        MockTable.create(this, salesSchema, "PRODUCTS_TEMPORAL", false, 200D, // 创建MockTable实例，表名为PRODUCTS_TEMPORAL，非流式表，预估行数200
            null, NullInitializerExpressionFactory.INSTANCE, true); // null表示无约束，使用Null初始化表达式工厂，true表示是时态表
    productsTemporalTable.addColumn("PRODUCTID", fixture.intType); // 添加PRODUCTID列，类型为整数（产品ID）
    productsTemporalTable.addColumn("NAME", fixture.varchar20Type); // 添加NAME列，类型为最大长度20的变长字符串（产品名称）
    productsTemporalTable.addColumn("SUPPLIERID", fixture.intType); // 添加SUPPLIERID列，类型为整数（供应商ID）
    productsTemporalTable.addColumn("SYS_START", fixture.timestampType); // 添加SYS_START列，类型为时间戳（系统开始时间，时态表特有）
    productsTemporalTable.addColumn("SYS_END", fixture.timestampType); // 添加SYS_END列，类型为时间戳（系统结束时间，时态表特有）
    productsTemporalTable.addColumn( // 添加SYS_START_LOCAL_TIMESTAMP列
        "SYS_START_LOCAL_TIMESTAMP", fixture.timestampTypeWithLocalTimeZone); // 类型为带本地时区的时间戳
    productsTemporalTable.addColumn( // 添加SYS_END_LOCAL_TIMESTAMP列
        "SYS_END_LOCAL_TIMESTAMP", fixture.timestampTypeWithLocalTimeZone); // 类型为带本地时区的时间戳
    registerTable(productsTemporalTable); // 调用registerTable方法将productsTemporalTable注册到目录中
  } // registerTableProductsTemporal方法结束

  private void registerTableSuppliers(MockSchema salesSchema, Fixture fixture) { // 私有方法：注册SUPPLIERS表，表示供应商信息
    MockTable suppliersTable = // 声明变量suppliersTable，类型为MockTable
        MockTable.create(this, salesSchema, "SUPPLIERS", false, 10D); // 创建MockTable实例，表名为SUPPLIERS，非流式表，预估行数10
    suppliersTable.addColumn("SUPPLIERID", fixture.intType); // 添加SUPPLIERID列，类型为整数（供应商ID）
    suppliersTable.addColumn("NAME", fixture.varchar20Type); // 添加NAME列，类型为最大长度20的变长字符串（供应商名称）
    suppliersTable.addColumn("CITY", fixture.intType); // 添加CITY列，类型为整数（城市ID，这里用整数表示）
    registerTable(suppliersTable); // 调用registerTable方法将suppliersTable注册到目录中
  } // registerTableSuppliers方法结束

  private void registerViewEmp20(MockSchema salesSchema, MockTable empTable, Fixture fixture) { // 私有方法：注册EMP_20视图，基于EMP表，过滤DEPTNO=20且SAL>1000的记录
    final ImmutableIntList m0 = ImmutableIntList.of(0, 1, 2, 3, 4, 5, 6, 8); // 创建不可变整数列表，表示从empTable中选择的列索引（不包括DEPTNO列索引7）
    MockTable emp20View = // 声明变量emp20View，类型为MockTable（实际是MockViewTable）
        new MockViewTable(this, salesSchema.getCatalogName(), salesSchema.getName(), // 创建MockViewTable实例，传入目录读取器、目录名、模式名
            "EMP_20", false, 600, empTable, m0, null, // 视图名为EMP_20，非流式表，预估行数600，基于empTable，选择m0列，无约束表达式
            NullInitializerExpressionFactory.INSTANCE) { // 使用Null初始化表达式工厂
          @Override public RexNode getConstraint(RexBuilder rexBuilder, // 重写getConstraint方法，返回视图的约束条件（即WHERE子句）
              RelDataType tableRowType) { // 参数：tableRowType，表的行类型
            final RelDataTypeField deptnoField = // 获取DEPTNO字段（索引7）
                tableRowType.getFieldList().get(7); // 从字段列表中获取第7个字段（DEPTNO）
            final RelDataTypeField salField = // 获取SAL字段（索引5）
                tableRowType.getFieldList().get(5); // 从字段列表中获取第5个字段（SAL）
            final List<RexNode> nodes = // 创建RexNode列表，存储约束条件
                Arrays.asList( // 使用Arrays.asList创建列表
                    rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, // 创建等于操作符的调用：DEPTNO = 20
                        rexBuilder.makeInputRef(deptnoField.getType(), // 创建输入引用，引用DEPTNO字段
                            deptnoField.getIndex()), // 使用DEPTNO字段的索引
                        rexBuilder.makeExactLiteral(BigDecimal.valueOf(20L), // 创建精确字面量20
                            deptnoField.getType())), // 使用DEPTNO字段的类型
                    rexBuilder.makeCall(SqlStdOperatorTable.GREATER_THAN, // 创建大于操作符的调用：SAL > 1000
                        rexBuilder.makeInputRef(salField.getType(), // 创建输入引用，引用SAL字段
                            salField.getIndex()), // 使用SAL字段的索引
                        rexBuilder.makeExactLiteral(BigDecimal.valueOf(1000L), // 创建精确字面量1000
                            salField.getType()))); // 使用SAL字段的类型
            return RexUtil.composeConjunction(rexBuilder, nodes); // 使用RexUtil将多个条件组合成AND连接的约束条件
          } // getConstraint方法结束
        }; // 匿名内部类结束
    salesSchema.addTable(Util.last(emp20View.getQualifiedName())); // 将emp20View添加到salesSchema中
    emp20View.addColumn("EMPNO", fixture.intType); // 添加EMPNO列，类型为整数
    emp20View.addColumn("ENAME", fixture.varchar20Type); // 添加ENAME列，类型为最大长度20的变长字符串
    emp20View.addColumn("JOB", fixture.varchar10Type); // 添加JOB列，类型为最大长度10的变长字符串
    emp20View.addColumn("MGR", fixture.intTypeNull); // 添加MGR列，类型为可空的整数
    emp20View.addColumn("HIREDATE", fixture.timestampType); // 添加HIREDATE列，类型为时间戳
    emp20View.addColumn("SAL", fixture.intType); // 添加SAL列，类型为整数
    emp20View.addColumn("COMM", fixture.intType); // 添加COMM列，类型为整数
    emp20View.addColumn("SLACKER", fixture.booleanType); // 添加SLACKER列，类型为布尔值
    registerTable(emp20View); // 调用registerTable方法将emp20View注册到目录中
  } // registerViewEmp20方法结束

  private void registerViewEmpNullables20( // 私有方法：注册EMPNULLABLES_20视图，基于EMPNULLABLES表，过滤DEPTNO=20且SAL>1000的记录
      MockSchema salesSchema, MockTable empNullablesTable, Fixture fixture) { // 参数：salesSchema（销售模式），empNullablesTable（可空员工表），fixture（测试数据类型）
    final ImmutableIntList m0 = ImmutableIntList.of(0, 1, 2, 3, 4, 5, 6, 8); // 创建不可变整数列表，表示从empNullablesTable中选择的列索引（不包括DEPTNO列索引7）
    MockTable empNullables20View = // 声明变量empNullables20View，类型为MockTable（实际是MockViewTable）
        new MockViewTable(this, salesSchema.getCatalogName(), salesSchema.getName(), // 创建MockViewTable实例，传入目录读取器、目录名、模式名
            "EMPNULLABLES_20", false, 600, empNullablesTable, m0, null, // 视图名为EMPNULLABLES_20，非流式表，预估行数600，基于empNullablesTable，选择m0列，无约束表达式
            NullInitializerExpressionFactory.INSTANCE) { // 使用Null初始化表达式工厂
          @Override public RexNode getConstraint(RexBuilder rexBuilder, // 重写getConstraint方法，返回视图的约束条件（即WHERE子句）
              RelDataType tableRowType) { // 参数：tableRowType，表的行类型
            final RelDataTypeField deptnoField = // 获取DEPTNO字段（索引7）
                tableRowType.getFieldList().get(7); // 从字段列表中获取第7个字段（DEPTNO）
            final RelDataTypeField salField = // 获取SAL字段（索引5）
                tableRowType.getFieldList().get(5); // 从字段列表中获取第5个字段（SAL）
            final List<RexNode> nodes = // 创建RexNode列表，存储约束条件
                Arrays.asList( // 使用Arrays.asList创建列表
                    rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, // 创建等于操作符的调用：DEPTNO = 20
                        rexBuilder.makeInputRef(deptnoField.getType(), // 创建输入引用，引用DEPTNO字段
                            deptnoField.getIndex()), // 使用DEPTNO字段的索引
                        rexBuilder.makeExactLiteral(BigDecimal.valueOf(20L), // 创建精确字面量20
                            deptnoField.getType())), // 使用DEPTNO字段的类型
                    rexBuilder.makeCall(SqlStdOperatorTable.GREATER_THAN, // 创建大于操作符的调用：SAL > 1000
                        rexBuilder.makeInputRef(salField.getType(), // 创建输入引用，引用SAL字段
                            salField.getIndex()), // 使用SAL字段的索引
                        rexBuilder.makeExactLiteral(BigDecimal.valueOf(1000L), // 创建精确字面量1000
                            salField.getType()))); // 使用SAL字段的类型
            return RexUtil.composeConjunction(rexBuilder, nodes); // 使用RexUtil将多个条件组合成AND连接的约束条件
          } // getConstraint方法结束
        }; // 匿名内部类结束
    salesSchema.addTable(Util.last(empNullables20View.getQualifiedName())); // 将empNullables20View添加到salesSchema中
    empNullables20View.addColumn("EMPNO", fixture.intType); // 添加EMPNO列，类型为整数
    empNullables20View.addColumn("ENAME", fixture.varchar20TypeNull); // 添加ENAME列，类型为可空的最大长度20的变长字符串
    empNullables20View.addColumn("JOB", fixture.varchar10TypeNull); // 添加JOB列，类型为可空的最大长度10的变长字符串
    empNullables20View.addColumn("MGR", fixture.intTypeNull); // 添加MGR列，类型为可空的整数
    empNullables20View.addColumn("HIREDATE", fixture.timestampTypeNull); // 添加HIREDATE列，类型为可空的时间戳
    empNullables20View.addColumn("SAL", fixture.intTypeNull); // 添加SAL列，类型为可空的整数
    empNullables20View.addColumn("COMM", fixture.intTypeNull); // 添加COMM列，类型为可空的整数
    empNullables20View.addColumn("SLACKER", fixture.booleanTypeNull); // 添加SLACKER列，类型为可空的布尔值
    registerTable(empNullables20View); // 调用registerTable方法将empNullables20View注册到目录中
  } // registerViewEmpNullables20方法结束

  private void registerStructTypeTables(Fixture fixture) { // 私有方法：注册STRUCT模式的表，包含结构化类型（复合名称列）
    MockSchema structTypeSchema = new MockSchema("STRUCT"); // 创建MockSchema实例，模式名为STRUCT
    registerSchema(structTypeSchema); // 调用registerSchema方法将structTypeSchema注册到目录中
    final List<CompoundNameColumn> columns = // 声明最终变量columns，类型为CompoundNameColumn列表，用于存储复合名称列
        Arrays.asList(new CompoundNameColumn("", "K0", fixture.varchar20Type), // 创建复合名称列：前缀为空，名称为K0，类型为最大长度20的变长字符串
            new CompoundNameColumn("", "C1", fixture.varchar20Type), // 创建复合名称列：前缀为空，名称为C1，类型为最大长度20的变长字符串
            new CompoundNameColumn("F1", "A0", fixture.intType), // 创建复合名称列：前缀为F1，名称为A0，类型为整数
            new CompoundNameColumn("F2", "A0", fixture.booleanType), // 创建复合名称列：前缀为F2，名称为A0，类型为布尔值
            new CompoundNameColumn("F0", "C0", fixture.intType), // 创建复合名称列：前缀为F0，名称为C0，类型为整数
            new CompoundNameColumn("F1", "C0", fixture.intTypeNull), // 创建复合名称列：前缀为F1，名称为C0，类型为可空的整数
            new CompoundNameColumn("F0", "C1", fixture.intType), // 创建复合名称列：前缀为F0，名称为C1，类型为整数
            new CompoundNameColumn("F1", "C2", fixture.intType), // 创建复合名称列：前缀为F1，名称为C2，类型为整数
            new CompoundNameColumn("F2", "C3", fixture.intType)); // 创建复合名称列：前缀为F2，名称为C3，类型为整数
    final CompoundNameColumnResolver structTypeTableResolver = // 声明最终变量structTypeTableResolver，类型为CompoundNameColumnResolver
        new CompoundNameColumnResolver(columns, "F0"); // 创建复合名称列解析器，传入列列表和前缀F0
    final MockTable structTypeTable = // 声明最终变量structTypeTable，类型为MockTable
        MockTable.create(this, structTypeSchema, "T", false, 100, // 创建MockTable实例，表名为T，非流式表，预估行数100
            structTypeTableResolver); // 使用structTypeTableResolver作为列解析器
    for (CompoundNameColumn column : columns) { // 遍历columns列表
      structTypeTable.addColumn(column.getName(), column.type); // 为structTypeTable添加列，使用列的名称和类型
    } // for循环结束
    registerTable(structTypeTable); // 调用registerTable方法将structTypeTable注册到目录中

    final List<CompoundNameColumn> columnsNullable = // 声明最终变量columnsNullable，类型为CompoundNameColumn列表，用于存储可空的复合名称列
        Arrays.asList(new CompoundNameColumn("", "K0", fixture.varchar20TypeNull), // 创建复合名称列：前缀为空，名称为K0，类型为可空的最大长度20的变长字符串
            new CompoundNameColumn("", "C1", fixture.varchar20TypeNull), // 创建复合名称列：前缀为空，名称为C1，类型为可空的最大长度20的变长字符串
            new CompoundNameColumn("F1", "A0", fixture.intTypeNull), // 创建复合名称列：前缀为F1，名称为A0，类型为可空的整数
            new CompoundNameColumn("F2", "A0", fixture.booleanTypeNull), // 创建复合名称列：前缀为F2，名称为A0，类型为可空的布尔值
            new CompoundNameColumn("F0", "C0", fixture.intTypeNull), // 创建复合名称列：前缀为F0，名称为C0，类型为可空的整数
            new CompoundNameColumn("F1", "C0", fixture.intTypeNull), // 创建复合名称列：前缀为F1，名称为C0，类型为可空的整数
            new CompoundNameColumn("F0", "C1", fixture.intTypeNull), // 创建复合名称列：前缀为F0，名称为C1，类型为可空的整数
            new CompoundNameColumn("F1", "C2", fixture.intType), // 创建复合名称列：前缀为F1，名称为C2，类型为整数
            new CompoundNameColumn("F2", "C3", fixture.intTypeNull)); // 创建复合名称列：前缀为F2，名称为C3，类型为可空的整数
    final MockTable structNullableTypeTable = // 声明最终变量structNullableTypeTable，类型为MockTable
        MockTable.create(this, structTypeSchema, "T_NULLABLES", false, 100, // 创建MockTable实例，表名为T_NULLABLES，非流式表，预估行数100
            structTypeTableResolver); // 使用structTypeTableResolver作为列解析器
    for (CompoundNameColumn column : columnsNullable) { // 遍历columnsNullable列表
      structNullableTypeTable.addColumn(column.getName(), column.type); // 为structNullableTypeTable添加列，使用列的名称和类型
    } // for循环结束
    registerTable(structNullableTypeTable); // 调用registerTable方法将structNullableTypeTable注册到目录中

    // Register "STRUCT.T_10" view.
    // Same columns as "STRUCT.T", but "F0.C0" is set to 10 by default, which is the equivalent of:
    //   SELECT * FROM T WHERE F0.C0 = 10
    // This table uses MockViewTable which does not populate the constrained columns with default
    // values on INSERT.
    // 注释说明：注册STRUCT.T_10视图，与STRUCT.T表列相同，但F0.C0默认设置为10，相当于SELECT * FROM T WHERE F0.C0 = 10，在INSERT时不会用默认值填充约束列
    final ImmutableIntList m1 = ImmutableIntList.of(0, 1, 2, 3, 4, 5, 6, 7, 8); // 创建不可变整数列表，表示从structTypeTable中选择的列索引（所有9列）
    MockTable struct10View = // 声明变量struct10View，类型为MockTable（实际是MockViewTable）
        new MockViewTable(this, structTypeSchema.getCatalogName(), // 创建MockViewTable实例，传入目录读取器、目录名
            structTypeSchema.getName(), "T_10", false, 20, structTypeTable, // 模式名、视图名T_10，非流式表，预估行数20，基于structTypeTable
            m1, structTypeTableResolver, // 选择m1列，使用structTypeTableResolver
            NullInitializerExpressionFactory.INSTANCE) { // 使用Null初始化表达式工厂
          @Override public RexNode getConstraint(RexBuilder rexBuilder, // 重写getConstraint方法，返回视图的约束条件
              RelDataType tableRowType) { // 参数：tableRowType，表的行类型
            final RelDataTypeField c0Field = // 获取C0字段（索引4，即F0.C0）
                tableRowType.getFieldList().get(4); // 从字段列表中获取第4个字段（F0.C0）
            return rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, // 创建等于操作符的调用：F0.C0 = 10
                rexBuilder.makeInputRef(c0Field.getType(), // 创建输入引用，引用c0Field字段
                    c0Field.getIndex()), // 使用c0Field字段的索引
                rexBuilder.makeExactLiteral(BigDecimal.valueOf(10L), // 创建精确字面量10
                    c0Field.getType())); // 使用c0Field字段的类型
          } // getConstraint方法结束
        }; // 匿名内部类结束
    structTypeSchema.addTable(Util.last(struct10View.getQualifiedName())); // 将struct10View添加到structTypeSchema中
    for (CompoundNameColumn column : columns) { // 遍历columns列表
      struct10View.addColumn(column.getName(), column.type); // 为struct10View添加列，使用列的名称和类型
    } // for循环结束
    registerTable(struct10View); // 调用registerTable方法将struct10View注册到目录中
  } // registerStructTypeTables方法结束

  @Override public MockCatalogReaderSimple init() { // 重写init方法，初始化目录读取器，注册所有模式、表和视图
    final Fixture fixture = new Fixture(typeFactory); // 创建Fixture实例，传入类型工厂，Fixture包含预定义的测试数据类型

    // Register "SALES" schema.
    // 注释说明：注册SALES模式
    MockSchema salesSchema = new MockSchema("SALES"); // 创建MockSchema实例，模式名为SALES
    registerSchema(salesSchema); // 调用registerSchema方法将salesSchema注册到目录中

    // Register "EMP" table with customer InitializerExpressionFactory
    // to check whether newDefaultValue method called or not.
    // 注释说明：注册EMP表，使用自定义的InitializerExpressionFactory来检查newDefaultValue方法是否被调用
    final InitializerExpressionFactory countingInitializerExpressionFactory = // 声明最终变量countingInitializerExpressionFactory
        new CountingFactory(ImmutableList.of("DEPTNO")); // 创建CountingFactory实例，传入包含"DEPTNO"的不可变列表，用于统计方法调用次数

    registerType( // 注册自定义类型
        ImmutableList.of(salesSchema.getCatalogName(), salesSchema.getName(), // 创建不可变列表，包含目录名和模式名
            "customBigInt"), // 添加类型名称customBigInt
        typeFactory -> typeFactory.createSqlType(SqlTypeName.BIGINT)); // 使用lambda表达式创建BIGINT类型

    // Register "EMP" table.
    // 注释说明：注册EMP表
    final MockTable empTable = // 声明最终变量empTable，类型为MockTable
        MockTable.create(this, salesSchema, "EMP", false, 14, null, // 创建MockTable实例，表名为EMP，非流式表，预估行数14，无约束
            countingInitializerExpressionFactory, false); // 使用countingInitializerExpressionFactory作为初始化表达式工厂，非临时表
    registerTableEmp(empTable, fixture); // 调用registerTableEmp方法为empTable添加列并注册到目录中

    // Register "EMPNULLABLES" table with nullable columns.
    // 注释说明：注册EMPNULLABLES表，所有列都可为空
    final MockTable empNullablesTable = // 声明最终变量empNullablesTable，类型为MockTable
        MockTable.create(this, salesSchema, "EMPNULLABLES", false, 14); // 创建MockTable实例，表名为EMPNULLABLES，非流式表，预估行数14
    registerTableEmpNullables(empNullablesTable, fixture); // 调用registerTableEmpNullables方法为empNullablesTable添加列并注册到目录中

    // Register "EMPDEFAULTS" table with default values for some columns.
    // 注释说明：注册EMPDEFAULTS表，某些列包含默认值
    registerTableEmpDefaults(salesSchema, fixture); // 调用registerTableEmpDefaults方法创建并注册EMPDEFAULTS表

    // Register "EMP_B" table. As "EMP", birth with a "BIRTHDATE" column.
    // 注释说明：注册EMP_B表，与EMP表类似，但增加了BIRTHDATE列
    registerTableEmpB(salesSchema, fixture); // 调用registerTableEmpB方法创建并注册EMP_B表

    // Register "DEPT" table.
    // 注释说明：注册DEPT表
    registerTableDept(salesSchema, fixture); // 调用registerTableDept方法创建并注册DEPT表

    // Register "DEPTNULLABLES" table.
    // 注释说明：注册DEPTNULLABLES表
    registerTableDeptNullables(salesSchema, fixture); // 调用registerTableDeptNullables方法创建并注册DEPTNULLABLES表

    // Register "DEPT_SINGLE" table.
    // 注释说明：注册DEPT_SINGLE表
    registerTableDeptSingle(salesSchema, fixture); // 调用registerTableDeptSingle方法创建并注册DEPT_SINGLE表

    // Register "DEPT_NESTED" table.
    // 注释说明：注册DEPT_NESTED表
    registerTableDeptNested(salesSchema, fixture); // 调用registerTableDeptNested方法创建并注册DEPT_NESTED表

    // Register "DEPT_NESTED_EXPANDED" table.
    // 注释说明：注册DEPT_NESTED_EXPANDED表
    registerTableDeptNestedExpanded(salesSchema, fixture); // 调用registerTableDeptNestedExpanded方法创建并注册DEPT_NESTED_EXPANDED表

    // Register "BONUS" table.
    // 注释说明：注册BONUS表
    registerTableBonus(salesSchema, fixture); // 调用registerTableBonus方法创建并注册BONUS表

    // Register "SALGRADE" table.
    // 注释说明：注册SALGRADE表
    registerTableSalgrade(salesSchema, fixture); // 调用registerTableSalgrade方法创建并注册SALGRADE表

    // Register "EMP_ADDRESS" table
    // 注释说明：注册EMP_ADDRESS表
    registerTableEmpAddress(salesSchema, fixture); // 调用registerTableEmpAddress方法创建并注册EMP_ADDRESS表

    // Register "CUSTOMER" schema.
    // 注释说明：注册CUSTOMER模式
    MockSchema customerSchema = new MockSchema("CUSTOMER"); // 创建MockSchema实例，模式名为CUSTOMER
    registerSchema(customerSchema); // 调用registerSchema方法将customerSchema注册到目录中

    // Register "CONTACT" table.
    // 注释说明：注册CONTACT表
    registerTableContact(customerSchema, fixture); // 调用registerTableContact方法创建并注册CONTACT表

    // Register "CONTACT_PEEK" table. The
    // 注释说明：注册CONTACT_PEEK表，该表包含特殊坐标类型
    registerTableContactPeek(customerSchema, fixture); // 调用registerTableContactPeek方法创建并注册CONTACT_PEEK表

    // Register "ACCOUNT" table.
    // 注释说明：注册ACCOUNT表
    registerTableAccount(customerSchema, fixture); // 调用registerTableAccount方法创建并注册ACCOUNT表

    // Register "ORDERS" stream.
    // 注释说明：注册ORDERS流表
    registerTableOrders(salesSchema, fixture); // 调用registerTableOrders方法创建并注册ORDERS流表

    // Register "SHIPMENTS" stream.
    // 注释说明：注册SHIPMENTS流表
    registerTableShipments(salesSchema, fixture); // 调用registerTableShipments方法创建并注册SHIPMENTS流表

    // Register "PRODUCTS" table.
    // 注释说明：注册PRODUCTS表
    registerTableProducts(salesSchema, fixture); // 调用registerTableProducts方法创建并注册PRODUCTS表

    // Register "EMPTY_PRODUCTS" table.
    // 注释说明：注册EMPTY_PRODUCTS表
    registerTableEmptyProducts(salesSchema, fixture); // 调用registerTableEmptyProducts方法创建并注册EMPTY_PRODUCTS表

    // Register "PRODUCTS_TEMPORAL" table.
    // 注释说明：注册PRODUCTS_TEMPORAL表
    registerTableProductsTemporal(salesSchema, fixture); // 调用registerTableProductsTemporal方法创建并注册PRODUCTS_TEMPORAL表

    // Register "SUPPLIERS" table.
    // 注释说明：注册SUPPLIERS表
    registerTableSuppliers(salesSchema, fixture); // 调用registerTableSuppliers方法创建并注册SUPPLIERS表

    // Register "EMP_20" and "EMPNULLABLES_20 views.
    // Same columns as "EMP" amd "EMPNULLABLES", but "DEPTNO" not visible and set to 20 by default
    // and "SAL" is visible but must be greater than 1000, which is the equivalent of:
    //   SELECT EMPNO, ENAME, JOB, MGR, HIREDATE, SAL, COMM, SLACKER
    //   FROM EMP WHERE DEPTNO = 20 AND SAL > 1000
    // 注释说明：注册EMP_20和EMPNULLABLES_20视图，与EMP和EMPNULLABLES表列相同，但DEPTNO不可见且默认设置为20，SAL可见但必须大于1000，相当于SELECT EMPNO, ENAME, JOB, MGR, HIREDATE, SAL, COMM, SLACKER FROM EMP WHERE DEPTNO = 20 AND SAL > 1000
    registerViewEmp20(salesSchema, empTable, fixture); // 调用registerViewEmp20方法创建并注册EMP_20视图

    registerViewEmpNullables20(salesSchema, empNullablesTable, fixture); // 调用registerViewEmpNullables20方法创建并注册EMPNULLABLES_20视图

    registerStructTypeTables(fixture); // 调用registerStructTypeTables方法创建并注册STRUCT模式的表

    registerTablesWithRollUp(salesSchema, fixture); // 调用registerTablesWithRollUp方法创建并注册带ROLLUP的表（该方法在父类中定义）
    return this; // 返回this，支持链式调用
  } // init方法结束
} // MockCatalogReaderSimple类结束
