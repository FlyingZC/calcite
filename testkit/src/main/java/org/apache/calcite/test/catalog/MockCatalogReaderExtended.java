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
package org.apache.calcite.test.catalog; // 包声明：org.apache.calcite.test.catalog，Calcite测试目录包

import org.apache.calcite.plan.RelOptPredicateList; // 关系优化谓词列表，用于存储关系表达式的谓词信息
import org.apache.calcite.rel.RelNode; // 关系节点接口，表示关系代数中的操作
import org.apache.calcite.rel.metadata.BuiltInMetadata; // 内置元数据接口定义
import org.apache.calcite.rel.metadata.MetadataDef; // 元数据定义类，用于描述元数据接口
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 关系元数据查询类，用于查询关系节点的元数据
import org.apache.calcite.rel.type.RelDataType; // 关系数据类型接口，表示Calcite中的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.rex.RexBuilder; // Rex表达式构建器，用于构建行表达式
import org.apache.calcite.rex.RexInputRef; // Rex输入引用，表示对输入字段的引用
import org.apache.calcite.schema.TableMacro; // 表宏接口，用于定义表宏函数
import org.apache.calcite.schema.TranslatableTable; // 可翻译表接口，表可以转换为关系表达式
import org.apache.calcite.sql.SqlKind; // SQL操作种类枚举，定义各种SQL操作的类型
import org.apache.calcite.sql.SqlOperator; // SQL操作符接口，表示SQL中的操作符
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // SQL标准操作符表，包含标准SQL操作符
import org.apache.calcite.sql.type.SqlTypeName; // SQL类型名称枚举，定义各种SQL数据类型
import org.apache.calcite.sql.util.SqlOperatorTables; // SQL操作符表工具类

import com.google.common.collect.ImmutableList; // Google Guava不可变列表类，提供线程安全的不可变列表

import org.checkerframework.checker.nullness.qual.NonNull; // CheckerFramework非空注解，标记参数或返回值不能为null

import java.util.ArrayList; // Java ArrayList类，动态数组实现
import java.util.Arrays; // Java Arrays工具类，提供数组操作方法
import java.util.List; // Java List接口，表示有序集合

/** Adds some extra tables to the mock catalog. These increase the time and
 * complexity of initializing the catalog (because they contain views whose
 * SQL needs to be parsed) and so are not used for all tests. */ // 扩展的模拟目录读取器类，继承自MockCatalogReaderSimple，用于在测试中提供额外的表和视图。该类包含：
 //* - 三个可修改视图表（EMP_MODIFIABLEVIEW、EMP_MODIFIABLEVIEW2、EMP_MODIFIABLEVIEW3），用于测试可修改视图功能
 //* - EMPM表，包含度量列，用于测试度量列功能
 //* - STRUCT模式的T_EXTEND表，包含复合名称列，用于测试嵌套字段访问
 //* - VIRTUALCOLUMNS模式的虚拟列表（VC_T1、VC_T2），用于测试存储虚拟列和计算虚拟列
 //* - COMPLEXTYPES模式的复杂类型表，包含记录、数组、多重集、映射等多种复杂数据类型
 //* - NULLABLEROWS模式的可空行表，用于测试可空记录类型
 //* - GEO模式的餐厅表，包含地理空间数据和自定义元数据处理器，用于测试空间函数和谓词推导 */
public class MockCatalogReaderExtended extends MockCatalogReaderSimple {
  /**
   * Creates a MockCatalogReader.
   *
   * <p>Caller must then call {@link #init} to populate with data;
   * constructor is protected to encourage you to call {@link #create}.
   *
   * @param typeFactory   Type factory
   * @param caseSensitive case sensitivity
   */
  protected MockCatalogReaderExtended(RelDataTypeFactory typeFactory, // 类型工厂，用于创建SQL数据类型
      boolean caseSensitive) { // 是否区分大小写
    super(typeFactory, caseSensitive); // 调用父类构造函数，初始化基础目录读取器
  }

  /** Creates and initializes a MockCatalogReaderExtended. */ // 创建并初始化一个扩展的模拟目录读取器实例
  public static @NonNull MockCatalogReaderExtended create( // 静态工厂方法，返回非空的MockCatalogReaderExtended实例
      RelDataTypeFactory typeFactory, // 类型工厂，用于创建SQL数据类型
      boolean caseSensitive) { // 是否区分大小写
    return new MockCatalogReaderExtended(typeFactory, caseSensitive).init(); // 创建实例并调用init方法初始化目录数据
  }

  @Override public MockCatalogReaderExtended init() { // 重写init方法，初始化扩展目录，包含额外的表和视图
    super.init(); // 调用父类init方法，初始化基础表结构

    final Fixture f = new Fixture(typeFactory); // 创建测试辅助对象，包含各种数据类型
    MockSchema salesSchema = new MockSchema("SALES"); // 创建SALES销售模式
    // Same as "EMP_20" except it uses ModifiableViewTable which populates
    // constrained columns with default values on INSERT and has a single constraint on DEPTNO. // 创建可修改视图表，类似EMP_20但使用ModifiableViewTable，在INSERT时为约束列填充默认值，并在DEPTNO上有单个约束
    List<String> empModifiableViewNames = // 定义可修改视图的完整名称列表 [catalog, schema, table]
        ImmutableList.of(salesSchema.getCatalogName(), salesSchema.getName(),
            "EMP_MODIFIABLEVIEW");
    TableMacro empModifiableViewMacro = // 创建表宏，用于生成可修改视图的SQL定义
        MockModifiableViewRelOptTable.viewMacro(rootSchema,
            "select EMPNO, ENAME, JOB, MGR, HIREDATE, SAL, COMM, SLACKER\n" // 视图的SQL查询语句，从EMPDEFAULTS表选择DEPTNO=20的员工数据
                + "from EMPDEFAULTS\n"
                + "where DEPTNO = 20",
            empModifiableViewNames.subList(0, 2), // 基础路径（catalog和schema）
            ImmutableList.of(empModifiableViewNames.get(2)), true); // 视图名称列表
    TranslatableTable empModifiableView = // 应用宏参数生成可翻译表对象
        empModifiableViewMacro.apply(ImmutableList.of());
    MockModifiableViewRelOptTable mockEmpViewTable = // 创建模拟可修改视图关系优化表
        MockModifiableViewRelOptTable.create(
            (MockModifiableViewRelOptTable.MockModifiableViewTable) // 强制类型转换为可修改视图表
                empModifiableView, this,
            empModifiableViewNames.get(0), empModifiableViewNames.get(1), // catalog和schema名称
            empModifiableViewNames.get(2), false, 20, null); // 表名、是否临时、约束列索引20（DEPTNO）
    registerTable(mockEmpViewTable); // 将可修改视图表注册到目录中

    // Same as "EMP_MODIFIABLEVIEW" except that all columns are in the view, columns are reordered,
    // and there is an `extra` extended column. // 创建第二个可修改视图，包含所有列、列重新排序，并添加EXTRA扩展列
    List<String> empModifiableViewNames2 = // 定义第二个可修改视图的完整名称列表
        ImmutableList.of(salesSchema.getCatalogName(), salesSchema.getName(),
            "EMP_MODIFIABLEVIEW2");
    TableMacro empModifiableViewMacro2 = // 创建第二个表宏，包含扩展列EXTRA
        MockModifiableViewRelOptTable.viewMacro(rootSchema,
            "select ENAME, EMPNO, JOB, DEPTNO, SLACKER, SAL, EXTRA, HIREDATE," // SQL查询，列重新排序，包含EXTRA扩展列
                + " MGR, COMM\n"
                + "from EMPDEFAULTS extend (EXTRA boolean)\n" // 使用extend关键字添加EXTRA扩展列（boolean类型）
                + "where DEPTNO = 20",
            empModifiableViewNames2.subList(0, 2), // 基础路径
            ImmutableList.of(empModifiableViewNames.get(2)), // 视图名称
            true);
    TranslatableTable empModifiableView2 = // 应用宏生成可翻译表
        empModifiableViewMacro2.apply(ImmutableList.of());
    MockModifiableViewRelOptTable mockEmpViewTable2 = // 创建第二个模拟可修改视图表
        MockModifiableViewRelOptTable.create(
            (MockModifiableViewRelOptTable.MockModifiableViewTable) // 强制类型转换
                empModifiableView2, this,
            empModifiableViewNames2.get(0), empModifiableViewNames2.get(1), // catalog和schema名称
            empModifiableViewNames2.get(2), false, 20, null); // 表名、是否临时、约束列索引
    registerTable(mockEmpViewTable2); // 注册第二个可修改视图表

    // Same as "EMP_MODIFIABLEVIEW" except that comm is not in the view. // 创建第三个可修改视图，与EMP_MODIFIABLEVIEW类似但不包含COMM列
    List<String> empModifiableViewNames3 = // 定义第三个可修改视图的完整名称列表
        ImmutableList.of(salesSchema.getCatalogName(), salesSchema.getName(),
            "EMP_MODIFIABLEVIEW3");
    TableMacro empModifiableViewMacro3 = // 创建第三个表宏，不包含COMM列
        MockModifiableViewRelOptTable.viewMacro(rootSchema,
            "select EMPNO, ENAME, JOB, MGR, HIREDATE, SAL, SLACKER\n" // SQL查询，不包含COMM列
                + "from EMPDEFAULTS\n"
                + "where DEPTNO = 20",
            empModifiableViewNames3.subList(0, 2), // 基础路径
            ImmutableList.of(empModifiableViewNames3.get(2)), // 视图名称
            true);
    TranslatableTable empModifiableView3 = // 应用宏生成可翻译表
        empModifiableViewMacro3.apply(ImmutableList.of());
    MockModifiableViewRelOptTable mockEmpViewTable3 = // 创建第三个模拟可修改视图表
        MockModifiableViewRelOptTable.create(
            (MockModifiableViewRelOptTable.MockModifiableViewTable) // 强制类型转换
                empModifiableView3, this,
            empModifiableViewNames3.get(0), empModifiableViewNames3.get(1), // catalog和schema名称
            empModifiableViewNames3.get(2), false, 20, null); // 表名、是否临时、约束列索引
    registerTable(mockEmpViewTable3); // 注册第三个可修改视图表

    // Register "EMPM" table.
    // Same as "EMP" but with "COUNT_PLUS_100" and "COUNT_TIMES_100" measure columns. // 注册EMPM表，与EMP表类似但添加了两个度量列COUNT_PLUS_100和COUNT_TIMES_100
    final MockTable empmTable = // 创建EMPM模拟表
        MockTable.create(this, salesSchema, "EMPM", false, 14); // 参数：目录读取器、模式、表名、是否临时、行数
    empmTable.addColumn("EMPNO", f.intType, true); // 添加EMPNO列（整型，主键）
    empmTable.addColumn("ENAME", f.varchar20Type); // 添加ENAME列（20字符字符串）
    empmTable.addColumn("JOB", f.varchar10Type); // 添加JOB列（10字符字符串）
    empmTable.addColumn("MGR", f.intTypeNull); // 添加MGR列（可空整型）
    empmTable.addColumn("HIREDATE", f.timestampType); // 添加HIREDATE列（时间戳类型）
    empmTable.addColumn("SAL", f.intType); // 添加SAL列（整型）
    empmTable.addColumn("COMM", f.intType); // 添加COMM列（整型）
    empmTable.addColumn("DEPTNO", f.intType); // 添加DEPTNO列（整型）
    empmTable.addColumn("SLACKER", f.booleanType); // 添加SLACKER列（布尔类型）
    empmTable.addColumn("COUNT_PLUS_100", // 添加COUNT_PLUS_100度量列（整型度量）
        f.typeFactory.createMeasureType(f.intType)); // 使用类型工厂创建度量类型
    empmTable.addColumn("COUNT_TIMES_100", // 添加COUNT_TIMES_100度量列（小数度量）
        f.typeFactory.createMeasureType(f.decimalType)); // 使用类型工厂创建小数度量类型
    registerTable(empmTable); // 注册EMPM表到目录

    MockSchema structTypeSchema = new MockSchema("STRUCT"); // 创建STRUCT结构类型模式
    registerSchema(structTypeSchema); // 注册STRUCT模式到目录
    final List<CompoundNameColumn> columnsExtended = // 定义扩展列列表，包含复合名称列
        Arrays.asList(new CompoundNameColumn("", "K0", f.varchar20TypeNull), // 顶层K0列（可空字符串）
            new CompoundNameColumn("", "C1", f.varchar20TypeNull), // 顶层C1列（可空字符串）
            new CompoundNameColumn("F0", "C0", f.intType), // F0.C0复合列（整型）
            new CompoundNameColumn("F1", "C1", f.intTypeNull)); // F1.C1复合列（可空整型）
    final List<CompoundNameColumn> extendedColumns = // 创建扩展列的副本列表
        new ArrayList<>(columnsExtended);
    extendedColumns.add(new CompoundNameColumn("F2", "C2", f.varchar20Type)); // 添加F2.C2扩展列
    final CompoundNameColumnResolver structExtendedTableResolver = // 创建复合名称列解析器，用于解析嵌套字段
        new CompoundNameColumnResolver(extendedColumns, "F0"); // 以F0作为基础字段
    final MockTable structExtendedTypeTable = // 创建结构扩展类型表
        MockTable.create(this, structTypeSchema, "T_EXTEND", false, 100, // 参数：目录读取器、模式、表名、是否临时、行数
            structExtendedTableResolver); // 列解析器
    for (CompoundNameColumn column : columnsExtended) { // 遍历基础列列表
      structExtendedTypeTable.addColumn(column.getName(), column.type); // 添加列到表
    }
    registerTable(structExtendedTypeTable); // 注册结构扩展类型表到目录

    // Defines a table with
    // schema(A int, B bigint, C varchar(10), D as a + 1 stored, E as b * 3 virtual). // 定义包含虚拟列的表，A/B/C是物理列，D是存储虚拟列（a+1），E是计算虚拟列（b*3）
    MockSchema virtualColumnsSchema = new MockSchema("VIRTUALCOLUMNS"); // 创建VIRTUALCOLUMNS虚拟列模式
    registerSchema(virtualColumnsSchema); // 注册虚拟列模式到目录
    final MockTable virtualColumnsTable1 = // 创建第一个虚拟列表VC_T1
        MockTable.create(this, virtualColumnsSchema, "VC_T1", false, 100, // 参数：目录读取器、模式、表名、是否临时、行数
            null, new VirtualColumnsExpressionFactory(), true); // 列解析器、虚拟列表达式工厂、是否包含存储虚拟列
    virtualColumnsTable1.addColumn("A", f.intTypeNull); // 添加A列（可空整型）
    virtualColumnsTable1.addColumn("B", f.bigintType); // 添加B列（长整型）
    virtualColumnsTable1.addColumn("C", f.varchar10Type); // 添加C列（10字符字符串）
    virtualColumnsTable1.addColumn("D", f.intTypeNull); // 添加D列（存储虚拟列，表达式为a+1）
    // Column E has the same type as column A because it's a virtual column
    // with expression that references column A. // E列与A列类型相同，因为它是引用A列的虚拟列
    virtualColumnsTable1.addColumn("E", f.intTypeNull); // 添加E列（虚拟列，类型与A相同）
    // Same schema with VC_T1 but with different table name. // 创建第二个虚拟列表VC_T2，与VC_T1结构相同但表名不同
    final MockTable virtualColumnsTable2 = // 创建第二个虚拟列表VC_T2
        MockTable.create(this, virtualColumnsSchema, "VC_T2", false, 100, // 参数：目录读取器、模式、表名、是否临时、行数
            null, new VirtualColumnsExpressionFactory(), false); // 列解析器、虚拟列表达式工厂、是否包含存储虚拟列（false）
    virtualColumnsTable2.addColumn("A", f.intTypeNull); // 添加A列（可空整型）
    virtualColumnsTable2.addColumn("B", f.bigintType); // 添加B列（长整型）
    virtualColumnsTable2.addColumn("C", f.varchar10Type); // 添加C列（10字符字符串）
    virtualColumnsTable2.addColumn("D", f.intTypeNull); // 添加D列（可空整型）
    virtualColumnsTable2.addColumn("E", f.bigintType); // 添加E列（长整型，与VC_T1中E列类型不同）
    registerTable(virtualColumnsTable1); // 注册第一个虚拟列表
    registerTable(virtualColumnsTable2); // 注册第二个虚拟列表

    // Register table with complex data type rows. // 注册包含复杂数据类型行的表
    MockSchema complexTypeColumnsSchema = new MockSchema("COMPLEXTYPES"); // 创建COMPLEXTYPES复杂类型模式
    registerSchema(complexTypeColumnsSchema); // 注册复杂类型模式到目录
    final MockTable complexTypeColumnsTable = // 创建复杂类型列表CTC_T1
        MockTable.create(this, complexTypeColumnsSchema, "CTC_T1", // 参数：目录读取器、模式、表名
            false, 100); // 是否临时、行数
    complexTypeColumnsTable.addColumn("A", f.recordType1); // 添加A列（记录类型1）
    complexTypeColumnsTable.addColumn("B", f.recordType2); // 添加B列（记录类型2）
    complexTypeColumnsTable.addColumn("C", f.recordType3); // 添加C列（记录类型3）
    complexTypeColumnsTable.addColumn("D", f.recordType4); // 添加D列（记录类型4）
    complexTypeColumnsTable.addColumn("E", f.recordType5); // 添加E列（记录类型5）
    complexTypeColumnsTable.addColumn("intArrayType", f.intArrayType); // 添加intArrayType列（整型数组）
    complexTypeColumnsTable.addColumn("varchar5ArrayType", f.varchar5ArrayType); // 添加varchar5ArrayType列（5字符字符串数组）
    complexTypeColumnsTable.addColumn("intArrayArrayType", f.intArrayArrayType); // 添加intArrayArrayType列（整型数组的数组）
    complexTypeColumnsTable.addColumn("varchar5ArrayArrayType", f.varchar5ArrayArrayType); // 添加varchar5ArrayArrayType列（5字符字符串数组的数组）
    complexTypeColumnsTable.addColumn("intMultisetType", f.intMultisetType); // 添加intMultisetType列（整型多重集）
    complexTypeColumnsTable.addColumn("varchar5MultisetType", f.varchar5MultisetType); // 添加varchar5MultisetType列（5字符字符串多重集）
    complexTypeColumnsTable.addColumn("intMultisetArrayType", f.intMultisetArrayType); // 添加intMultisetArrayType列（整型多重集数组）
    complexTypeColumnsTable.addColumn("varchar5MultisetArrayType", // 添加varchar5MultisetArrayType列（5字符字符串多重集数组）
        f.varchar5MultisetArrayType);
    complexTypeColumnsTable.addColumn("intArrayMultisetType", f.intArrayMultisetType); // 添加intArrayMultisetType列（整型数组多重集）
    complexTypeColumnsTable.addColumn("rowArrayMultisetType", f.rowArrayMultisetType); // 添加rowArrayMultisetType列（行数组多重集）
    complexTypeColumnsTable.addColumn("int2IntMapType", f.int2IntMapType); // 添加int2IntMapType列（整型到整型映射）
    complexTypeColumnsTable.addColumn("int2varcharArrayMapType", // 添加int2varcharArrayMapType列（整型到5字符字符串数组映射）
        f.int2varcharArrayMapType);
    complexTypeColumnsTable.addColumn("varcharMultiset2IntIntMapType", // 添加varcharMultiset2IntIntMapType列（字符串多重集到整型整型对映射）
        f.varcharMultiset2IntIntMapType);
    registerTable(complexTypeColumnsTable); // 注册复杂类型列表到目录

    MockSchema nullableRowsSchema = new MockSchema("NULLABLEROWS"); // 创建NULLABLEROWS可空行模式
    registerSchema(nullableRowsSchema); // 注册可空行模式到目录
    final MockTable nullableRowsTable = // 创建可空行列表NR_T1
        MockTable.create(this, nullableRowsSchema, "NR_T1", false, 100); // 参数：目录读取器、模式、表名、是否临时、行数
    RelDataType bigIntNotNull = typeFactory.createSqlType(SqlTypeName.BIGINT); // 创建不可空长整型数据类型
    RelDataType nullableRecordType = // 创建可空记录类型
        typeFactory.builder() // 获取类型构建器
            .nullableRecord(true) // 设置记录本身可空
            .add("NOT_NULL_FIELD", bigIntNotNull) // 添加NOT_NULL_FIELD字段（不可空长整型）
            .add("NULLABLE_FIELD", bigIntNotNull).nullable(true) // 添加NULLABLE_FIELD字段（可空长整型）
            .build(); // 构建记录类型

    nullableRowsTable.addColumn("ROW_COLUMN", nullableRecordType, false); // 添加ROW_COLUMN列（可空记录类型）
    nullableRowsTable.addColumn( // 添加ROW_COLUMN_ARRAY列
        "ROW_COLUMN_ARRAY", // 列名
        typeFactory.createArrayType(nullableRecordType, -1), // 创建数组类型（元素类型为可空记录，无固定长度）
        true); // 是否允许为null
    registerTable(nullableRowsTable); // 注册可空行列表到目录

    MockSchema geoSchema = new MockSchema("GEO"); // 创建GEO地理空间模式
    registerSchema(geoSchema); // 注册地理空间模式到目录
    final MockTable restaurantTable = // 创建餐厅表RESTAURANTS
        MockTable.create(this, geoSchema, "RESTAURANTS", false, 100); // 参数：目录读取器、模式、表名、是否临时、行数
    restaurantTable.addColumn("NAME", f.varchar20Type, true); // 添加NAME列（20字符字符串，主键）
    restaurantTable.addColumn("LATITUDE", f.intType); // 添加LATITUDE列（整型，纬度）
    restaurantTable.addColumn("LONGITUDE", f.intType); // 添加LONGITUDE列（整型，经度）
    restaurantTable.addColumn("CUISINE", f.varchar10Type); // 添加CUISINE列（10字符字符串，菜系）
    restaurantTable.addColumn("HILBERT", f.bigintType); // 添加HILBERT列（长整型，希尔伯特曲线索引）
    restaurantTable.addMonotonic("HILBERT"); // 标记HILBERT列为单调列（用于优化）
    restaurantTable.addWrap( // 添加元数据包装器，提供自定义谓词信息
        new BuiltInMetadata.AllPredicates.Handler() { // 创建所有谓词元数据处理器
          @Override public RelOptPredicateList getAllPredicates(RelNode r, // 获取所有谓词列表
              RelMetadataQuery mq) {
            // Return the predicate:
            //  r.hilbert = hilbert(r.longitude, r.latitude)
            //
            // (Yes, x = longitude, y = latitude. Same as ST_MakePoint.) // 返回谓词：hilbert = hilbert(longitude, latitude)，注意x是经度，y是纬度
            final RexBuilder rexBuilder = r.getCluster().getRexBuilder(); // 获取Rex表达式构建器
            final RexInputRef refLatitude = rexBuilder.makeInputRef(r, 1); // 创建对LATITUDE列的输入引用（索引1）
            final RexInputRef refLongitude = rexBuilder.makeInputRef(r, 2); // 创建对LONGITUDE列的输入引用（索引2）
            final RexInputRef refHilbert = rexBuilder.makeInputRef(r, 4); // 创建对HILBERT列的输入引用（索引4）
            return RelOptPredicateList.of(rexBuilder, // 构建谓词列表
                ImmutableList.of( // 包含一个等值谓词：hilbert = hilbert(longitude, latitude)
                    rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, // 创建等于操作符调用
                        refHilbert, // 左操作数：hilbert列
                        rexBuilder.makeCall(hilbertOp(), // 右操作数：调用hilbert函数
                            refLongitude, refLatitude)))); // 函数参数：经度和纬度
          }

          SqlOperator hilbertOp() { // 查找希尔伯特空间操作符
            for (SqlOperator op // 遍历空间操作符列表
                : SqlOperatorTables.spatialInstance().getOperatorList()) {
              if (op.getKind() == SqlKind.HILBERT // 检查是否为HILBERT类型操作符
                  && op.getOperandCountRange().isValidCount(2)) { // 检查是否接受2个参数
                return op; // 返回找到的希尔伯特操作符
              }
            }
            throw new AssertionError(); // 未找到则抛出断言错误
          }

          @Override public MetadataDef<BuiltInMetadata.AllPredicates> getDef() { // 获取元数据定义
            return BuiltInMetadata.AllPredicates.DEF; // 返回所有谓词元数据的定义
          }
        });
    registerTable(restaurantTable); // 注册餐厅表到目录

    return this; // 返回当前实例，支持链式调用
  }
}
