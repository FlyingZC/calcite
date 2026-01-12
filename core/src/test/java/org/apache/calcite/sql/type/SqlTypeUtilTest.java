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
// Apache Calcite SQL类型工具测试类，用于测试SqlTypeUtil工具类的各种类型操作方法
// 该测试类覆盖了类型家族判断、类型转换、类型比较、类型规范转换等核心功能
package org.apache.calcite.sql.type;

import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示Calcite中的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建各种数据类型
import org.apache.calcite.sql.SqlBasicTypeNameSpec; // 导入SQL基本类型名称规范类，表示基本类型的名称规范
import org.apache.calcite.sql.SqlCollectionTypeNameSpec; // 导入SQL集合类型名称规范类，表示集合类型的名称规范
import org.apache.calcite.sql.SqlIdentifier; // 导入SQL标识符类，表示SQL中的标识符
import org.apache.calcite.sql.SqlRowTypeNameSpec; // 导入SQL行类型名称规范类，表示行类型的名称规范
import org.apache.calcite.util.TryThreadLocal; // 导入TryThreadLocal工具类，用于线程本地变量的异常安全处理

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类，用于创建不可变的列表

import org.hamcrest.Matcher; // 导入Hamcrest匹配器接口，用于断言匹配
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，标记测试方法

import java.util.List; // 导入Java List接口，表示列表集合
import java.util.Locale; // 导入Java Locale类，用于本地化相关的操作
import java.util.stream.Collectors; // 导入Java流收集器工具类，用于流操作的收集

import static org.apache.calcite.sql.type.SqlTypeUtil.areSameFamily; // 静态导入SqlTypeUtil的areSameFamily方法，判断类型是否属于同一家族
import static org.apache.calcite.sql.type.SqlTypeUtil.convertTypeToSpec; // 静态导入SqlTypeUtil的convertTypeToSpec方法，将类型转换为类型规范
import static org.apache.calcite.sql.type.SqlTypeUtil.equalAsCollectionSansNullability; // 静态导入SqlTypeUtil的equalAsCollectionSansNullability方法，忽略可空性比较集合类型
import static org.apache.calcite.sql.type.SqlTypeUtil.equalAsMapSansNullability; // 静态导入SqlTypeUtil的equalAsMapSansNullability方法，忽略可空性比较Map类型
import static org.apache.calcite.test.Matchers.isListOf; // 静态导入Matchers的isListOf方法，用于断言列表内容

import static org.hamcrest.CoreMatchers.is; // 静态导入Hamcrest的is匹配器，用于相等性断言
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入Hamcrest的assertThat方法，用于执行断言

/**
 * Test of {@link org.apache.calcite.sql.type.SqlTypeUtil}.
 * SqlTypeUtil工具类的测试类，用于验证SQL类型工具类的各种功能是否正常工作
 * 测试覆盖的类型操作包括：类型家族判断、类型转换规则、类型相等性比较、类型规范转换等
 */
class SqlTypeUtilTest { // 测试类定义，使用JUnit5进行单元测试

  private final SqlTypeFixture f = new SqlTypeFixture(); // 成员变量：SQL类型测试夹具对象，用于提供各种测试用的数据类型

  @Test void testTypesIsSameFamilyWithNumberTypes() { // 测试方法：测试数值类型是否属于同一家族，验证数值类型家族判断的正确性
    assertThat(areSameFamily(ImmutableList.of(f.sqlBigInt, f.sqlBigInt)), is(true)); // 断言：两个BIGINT类型属于同一家族，结果为true
    assertThat(areSameFamily(ImmutableList.of(f.sqlInt, f.sqlBigInt)), is(true)); // 断言：INTEGER和BIGINT类型属于同一家族（都是数值类型），结果为true
    assertThat(areSameFamily(ImmutableList.of(f.sqlFloat, f.sqlBigInt)), is(true)); // 断言：FLOAT和BIGINT类型属于同一家族（都是数值类型），结果为true
    assertThat(areSameFamily(ImmutableList.of(f.sqlInt, f.sqlBigIntNullable)), // 断言：INTEGER和可空BIGINT类型属于同一家族，可空性不影响家族判断
        is(true)); // 结果为true，证明类型家族判断忽略可空性
  }

  @Test void testTypesIsSameFamilyWithCharTypes() { // 测试方法：测试字符类型是否属于同一家族，验证字符类型家族判断的正确性
    assertThat(areSameFamily(ImmutableList.of(f.sqlVarchar, f.sqlVarchar)), is(true)); // 断言：两个VARCHAR类型属于同一家族，结果为true
    assertThat(areSameFamily(ImmutableList.of(f.sqlVarchar, f.sqlChar)), is(true)); // 断言：VARCHAR和CHAR类型属于同一家族（都是字符类型），结果为true
    assertThat(areSameFamily(ImmutableList.of(f.sqlVarchar, f.sqlVarcharNullable)), // 断言：VARCHAR和可空VARCHAR类型属于同一家族，可空性不影响家族判断
        is(true)); // 结果为true
  }

  @Test void testTypesIsSameFamilyWithInconvertibleTypes() { // 测试方法：测试不可转换的类型是否属于不同家族，验证不同类型家族判断的准确性
    assertThat(areSameFamily(ImmutableList.of(f.sqlBoolean, f.sqlBigInt)), is(false)); // 断言：BOOLEAN和BIGINT类型不属于同一家族（布尔类型和数值类型），结果为false
    assertThat(areSameFamily(ImmutableList.of(f.sqlFloat, f.sqlBoolean)), is(false)); // 断言：FLOAT和BOOLEAN类型不属于同一家族（数值类型和布尔类型），结果为false
    assertThat(areSameFamily(ImmutableList.of(f.sqlInt, f.sqlDate)), is(false)); // 断言：INTEGER和DATE类型不属于同一家族（数值类型和日期类型），结果为false
  }

  @Test void testTypesIsSameFamilyWithNumberStructTypes() { // 测试方法：测试包含数值类型的结构体是否属于同一家族，验证结构体类型家族判断的正确性
    final RelDataType bigIntAndFloat = struct(f.sqlBigInt, f.sqlFloat); // 创建包含BIGINT和FLOAT的结构体类型
    final RelDataType floatAndBigInt = struct(f.sqlFloat, f.sqlBigInt); // 创建包含FLOAT和BIGINT的结构体类型（字段顺序不同）

    assertThat(areSameFamily(ImmutableList.of(bigIntAndFloat, floatAndBigInt)), // 断言：两个结构体虽然字段顺序不同，但都是数值类型，属于同一家族
        is(true)); // 结果为true，证明结构体类型家族判断考虑字段类型而非顺序
    assertThat(areSameFamily(ImmutableList.of(bigIntAndFloat, bigIntAndFloat)), // 断言：相同的结构体类型属于同一家族
        is(true)); // 结果为true
    assertThat(areSameFamily(ImmutableList.of(floatAndBigInt, bigIntAndFloat)), // 断言：字段顺序不同的结构体属于同一家族
        is(true)); // 结果为true
    assertThat(areSameFamily(ImmutableList.of(floatAndBigInt, floatAndBigInt)), // 断言：相同的结构体类型属于同一家族
        is(true)); // 结果为true
  }

  @Test void testTypesIsSameFamilyWithCharStructTypes() { // 测试方法：测试包含字符类型的结构体是否属于同一家族，验证字符结构体类型家族判断的正确性
    final RelDataType varCharStruct = struct(f.sqlVarchar); // 创建包含VARCHAR的结构体类型
    final RelDataType charStruct = struct(f.sqlChar); // 创建包含CHAR的结构体类型

    assertThat(areSameFamily(ImmutableList.of(varCharStruct, charStruct)), is(true)); // 断言：VARCHAR结构体和CHAR结构体属于同一家族（都是字符类型），结果为true
    assertThat(areSameFamily(ImmutableList.of(charStruct, varCharStruct)), is(true)); // 断言：CHAR结构体和VARCHAR结构体属于同一家族，结果为true
    assertThat(areSameFamily(ImmutableList.of(varCharStruct, varCharStruct)), is(true)); // 断言：相同的VARCHAR结构体属于同一家族，结果为true
    assertThat(areSameFamily(ImmutableList.of(charStruct, charStruct)), is(true)); // 断言：相同的CHAR结构体属于同一家族，结果为true
  }

  @Test void testTypesIsSameFamilyWithInconvertibleStructTypes() { // 测试方法：测试不可转换的结构体类型是否属于不同家族，验证不同类型结构体家族判断的准确性
    final RelDataType dateStruct = struct(f.sqlDate); // 创建包含DATE的结构体类型
    final RelDataType boolStruct = struct(f.sqlBoolean); // 创建包含BOOLEAN的结构体类型
    assertThat(areSameFamily(ImmutableList.of(dateStruct, boolStruct)), is(false)); // 断言：DATE结构体和BOOLEAN结构体不属于同一家族，结果为false

    final RelDataType charIntStruct = struct(f.sqlChar, f.sqlInt); // 创建包含CHAR和INT的结构体类型
    final RelDataType charDateStruct = struct(f.sqlChar, f.sqlDate); // 创建包含CHAR和DATE的结构体类型
    assertThat(areSameFamily(ImmutableList.of(charIntStruct, charDateStruct)), // 断言：CHAR+INT结构体和CHAR+DATE结构体不属于同一家族（第二个字段类型不同）
        is(false)); // 结果为false，证明结构体类型家族判断需要所有字段类型都匹配

    final RelDataType boolDateStruct = struct(f.sqlBoolean, f.sqlDate); // 创建包含BOOLEAN和DATE的结构体类型
    final RelDataType floatIntStruct = struct(f.sqlInt, f.sqlFloat); // 创建包含INT和FLOAT的结构体类型
    assertThat(areSameFamily(ImmutableList.of(boolDateStruct, floatIntStruct)), // 断言：BOOLEAN+DATE结构体和INT+FLOAT结构体不属于同一家族
        is(false)); // 结果为false
  }

  @Test void testModifyTypeCoercionMappings() { // 测试方法：测试修改类型强制转换映射规则，验证如何自定义类型转换规则
    SqlTypeMappingRules.Builder builder = SqlTypeMappingRules.builder(); // 创建类型映射规则构建器，用于构建自定义的类型转换规则
    final SqlTypeCoercionRule defaultRules = SqlTypeCoercionRule.instance(); // 获取默认的类型强制转换规则实例
    builder.addAll(defaultRules.getTypeMapping()); // 将默认规则的所有映射添加到构建器中，保留原有规则
    // Do the tweak, for example, if we want to add a rule to allow
    // coercion of BOOLEAN to TIMESTAMP.
    // 进行调整，例如，如果我们想添加一个规则来允许从BOOLEAN强制转换到TIMESTAMP
    builder.add(SqlTypeName.TIMESTAMP, // 为TIMESTAMP类型添加转换规则
        builder.copyValues(SqlTypeName.TIMESTAMP) // 复制TIMESTAMP类型现有的转换目标
            .add(SqlTypeName.BOOLEAN).build()); // 添加BOOLEAN作为TIMESTAMP的转换源类型

    // Try converting with both default rules and the new rule set.
    // 尝试使用默认规则和新规则集进行转换
    checkConvert(defaultRules, is(false)); // 验证使用默认规则时，BOOLEAN不能转换为TIMESTAMP，结果为false
    final SqlTypeCoercionRule typeCoercionRules = // 创建新的类型强制转换规则实例，使用自定义的映射
        SqlTypeCoercionRule.instance(builder.map); // 从构建器的映射创建规则实例
    try (TryThreadLocal.Memo ignored = // 使用TryThreadLocal进行线程本地变量的异常安全管理
             SqlTypeCoercionRule.THREAD_PROVIDERS.push(typeCoercionRules)) { // 在当前线程上下文中设置自定义的类型转换规则
      checkConvert(typeCoercionRules, is(true)); // 验证使用自定义规则时，BOOLEAN可以转换为TIMESTAMP，结果为true
    } // try-with-resources自动关闭TryThreadLocal.Memo，恢复之前的规则设置
  }

  private void checkConvert(SqlTypeCoercionRule rules, // 私有辅助方法：检查类型转换是否符合预期，用于验证类型转换规则的正确性
      Matcher<Boolean> matcher) { // 参数：matcher - 布尔值匹配器，用于断言转换结果是否符合预期
    assertThat( // 断言：验证BOOLEAN到TIMESTAMP的转换结果
        SqlTypeUtil.canCastFrom(f.sqlTimestampPrec3, f.sqlBoolean, true), // 使用默认的强制转换规则检查是否可以转换
        matcher); // 使用匹配器验证结果
    assertThat( // 断言：验证BOOLEAN到TIMESTAMP的转换结果
        SqlTypeUtil.canCastFrom(f.sqlTimestampPrec3, f.sqlBoolean, rules), // 使用指定的类型转换规则检查是否可以转换
        matcher); // 使用匹配器验证结果
  }

  @Test void testEqualAsCollectionSansNullability() { // 测试方法：测试忽略可空性时集合类型的相等性比较，验证集合类型比较的正确性
    // case array
    // 情况1：数组类型
    assertThat( // 断言：验证忽略可空性时，BIGINT数组和可空BIGINT数组是否相等
        equalAsCollectionSansNullability(f.typeFactory, f.arrayBigInt, f.arrayBigIntNullable), // 比较两个数组类型，忽略可空性
        is(true)); // 结果为true，证明忽略可空性时，两个数组类型相等

    // case multiset
    // 情况2：多重集类型
    assertThat( // 断言：验证忽略可空性时，BIGINT多重集和可空BIGINT多重集是否相等
        equalAsCollectionSansNullability(f.typeFactory, f.multisetBigInt, f.multisetBigIntNullable), // 比较两个多重集类型，忽略可空性
        is(true)); // 结果为true，证明忽略可空性时，两个多重集类型相等

    // multiset and array are not equal.
    // 多重集和数组不相等
    assertThat( // 断言：验证BIGINT数组和BIGINT多重集是否相等
        equalAsCollectionSansNullability(f.typeFactory, f.arrayBigInt, f.multisetBigInt), // 比较数组和多重集类型
        is(false)); // 结果为false，证明数组和多重集是不同的集合类型
  }

  @Test void testEqualAsMapSansNullability() { // 测试方法：测试忽略可空性时Map类型的相等性比较，验证Map类型比较的正确性
    assertThat( // 断言：验证忽略可空性时，INT类型的Map和可空INT类型的Map是否相等
        equalAsMapSansNullability(f.typeFactory, f.mapOfInt, f.mapOfIntNullable), // 比较两个Map类型，忽略可空性
        is(true)); // 结果为true，证明忽略可空性时，两个Map类型相等
  }

  @Test void testConvertTypeToSpec() { // 测试方法：测试将RelDataType转换为SqlTypeNameSpec，验证类型到类型规范的转换功能
    SqlBasicTypeNameSpec nullSpec = // 转换NULL类型为类型规范
        (SqlBasicTypeNameSpec) convertTypeToSpec(f.sqlNull).getTypeNameSpec(); // 将NULL类型转换为类型规范，并获取类型名称规范
    assertThat(nullSpec.getTypeName().getSimple(), is("NULL")); // 断言：验证NULL类型的名称为"NULL"

    SqlBasicTypeNameSpec unknownSpec = // 转换UNKNOWN类型为类型规范
        (SqlBasicTypeNameSpec) convertTypeToSpec(f.sqlUnknown).getTypeNameSpec(); // 将UNKNOWN类型转换为类型规范，并获取类型名称规范
    assertThat(unknownSpec.getTypeName().getSimple(), is("UNKNOWN")); // 断言：验证UNKNOWN类型的名称为"UNKNOWN"

    SqlBasicTypeNameSpec basicSpec = // 转换BIGINT基本类型为类型规范
        (SqlBasicTypeNameSpec) convertTypeToSpec(f.sqlBigInt).getTypeNameSpec(); // 将BIGINT类型转换为类型规范，并获取类型名称规范
    assertThat(basicSpec.getTypeName().getSimple(), is("BIGINT")); // 断言：验证BIGINT类型的名称为"BIGINT"

    SqlCollectionTypeNameSpec arraySpec = // 转换BIGINT数组类型为类型规范
        (SqlCollectionTypeNameSpec) convertTypeToSpec(f.arrayBigInt).getTypeNameSpec(); // 将数组类型转换为类型规范，并获取集合类型名称规范
    assertThat(arraySpec.getTypeName().getSimple(), is("ARRAY")); // 断言：验证数组类型的名称为"ARRAY"
    assertThat(arraySpec.getElementTypeName().getTypeName().getSimple(), is("BIGINT")); // 断言：验证数组元素类型的名称为"BIGINT"

    SqlCollectionTypeNameSpec multisetSpec = // 转换BIGINT多重集类型为类型规范
        (SqlCollectionTypeNameSpec) convertTypeToSpec(f.multisetBigInt).getTypeNameSpec(); // 将多重集类型转换为类型规范，并获取集合类型名称规范
    assertThat(multisetSpec.getTypeName().getSimple(), is("MULTISET")); // 断言：验证多重集类型的名称为"MULTISET"
    assertThat(multisetSpec.getElementTypeName().getTypeName().getSimple(), is("BIGINT")); // 断言：验证多重集元素类型的名称为"BIGINT"

    SqlRowTypeNameSpec rowSpec = // 转换结构体类型为类型规范
        (SqlRowTypeNameSpec) convertTypeToSpec(f.structOfInt).getTypeNameSpec(); // 将结构体类型转换为类型规范，并获取行类型名称规范
    List<String> fieldNames = // 提取字段名称列表
        SqlIdentifier.simpleNames(rowSpec.getFieldNames()); // 从行类型规范中获取字段名称并转换为简单名称列表
    List<String> fieldTypeNames = rowSpec.getFieldTypes() // 提取字段类型名称列表
        .stream() // 创建流
        .map(f -> f.getTypeName().getSimple()) // 将每个字段类型规范转换为简单类型名称
        .collect(Collectors.toList()); // 收集为列表
    assertThat(rowSpec.getTypeName().getSimple(), is("ROW")); // 断言：验证行类型的名称为"ROW"
    assertThat(fieldNames, isListOf("i", "j")); // 断言：验证字段名称为["i", "j"]
    assertThat(fieldTypeNames, isListOf("INTEGER", "INTEGER")); // 断言：验证字段类型名称为["INTEGER", "INTEGER"]
  }

  @Test void testGetMaxPrecisionScaleDecimal() { // 测试方法：测试获取最大精度和小数位数的DECIMAL类型，验证DECIMAL类型最大精度和小数位数的获取功能
    RelDataType decimal = SqlTypeUtil.getMaxPrecisionScaleDecimal(f.typeFactory); // 获取具有最大精度和小数位数的DECIMAL类型
    assertThat(decimal, is(f.typeFactory.createSqlType(SqlTypeName.DECIMAL, 19, 9))); // 断言：验证返回的DECIMAL类型精度为19，小数位数为9
  }


  private RelDataType struct(RelDataType...relDataTypes) { // 私有辅助方法：创建结构体类型，用于测试结构体类型的家族判断
    final RelDataTypeFactory.Builder builder = f.typeFactory.builder(); // 创建类型工厂构建器，用于构建结构体类型
    for (int i = 0; i < relDataTypes.length; i++) { // 遍历所有传入的类型
      builder.add("field" + i, relDataTypes[i]); // 将每个类型添加为结构体的一个字段，字段名为"field" + 索引
    }
    return builder.build(); // 构建并返回结构体类型
  }

  private void compareTypesIgnoringNullability( // 私有辅助方法：比较两个类型忽略可空性时的相等性，用于验证类型相等性比较的正确性
      String comment, RelDataType type1, RelDataType type2, boolean expectedResult) { // 参数：comment-注释信息，type1-第一个类型，type2-第二个类型，expectedResult-预期的比较结果
    String typeString1 = type1.getFullTypeString(); // 获取第一个类型的完整类型字符串表示
    String typeString2 = type2.getFullTypeString(); // 获取第二个类型的完整类型字符串表示

    assertThat( // 断言：验证使用类型工厂的equalSansNullability方法的结果
        "The result of SqlTypeUtil.equalSansNullability" // 错误信息：equalSansNullability方法的结果不正确
            + "(typeFactory, " + typeString1 + ", " + typeString2 + ") is incorrect: " + comment, // 包含类型名称和注释
        SqlTypeUtil.equalSansNullability(f.typeFactory, type1, type2), is(expectedResult)); // 使用类型工厂比较两个类型，忽略可空性
    assertThat("The result of SqlTypeUtil.equalSansNullability" // 断言：验证不使用类型工厂的equalSansNullability方法的结果
            + "(" + typeString1 + ", " + typeString2 + ") is incorrect: " + comment, // 错误信息：equalSansNullability方法的结果不正确
        SqlTypeUtil.equalSansNullability(type1, type2), is(expectedResult)); // 直接比较两个类型，忽略可空性
  }

  @Test void testEqualSansNullability() { // 测试方法：测试忽略可空性时类型的相等性比较，验证类型相等性比较的正确性
    RelDataType bigIntType = f.sqlBigInt; // 获取BIGINT类型（不可空）
    RelDataType nullableBigIntType = f.sqlBigIntNullable; // 获取可空的BIGINT类型
    RelDataType varCharType = f.sqlVarchar; // 获取VARCHAR类型
    RelDataType bigIntType1 = // 创建不可空的BIGINT类型
        f.typeFactory.createTypeWithNullability(nullableBigIntType, false); // 将可空BIGINT类型转换为不可空

    compareTypesIgnoringNullability("different types should return false. ", // 断言：不同类型应该返回false
        bigIntType, varCharType, false); // BIGINT和VARCHAR是不同的类型，应该返回false

    compareTypesIgnoringNullability("types differing only in nullability should return true.", // 断言：仅可空性不同的类型应该返回true
        bigIntType, nullableBigIntType, true); // BIGINT和可空BIGINT，忽略可空性后应该相等

    compareTypesIgnoringNullability("identical types should return true.", // 断言：相同的类型应该返回true
        bigIntType, bigIntType1, true); // 两个都是不可空的BIGINT类型，应该相等
  }

  @Test void testCanAlwaysCastToUnknownFromBasic() { // 测试方法：测试从基本类型到UNKNOWN类型的转换，验证所有基本类型都可以转换为UNKNOWN类型
    RelDataType unknownType = f.typeFactory.createUnknownType(); // 创建UNKNOWN类型（不可空）
    RelDataType nullableUnknownType = f.typeFactory.createTypeWithNullability(unknownType, true); // 创建可空的UNKNOWN类型

    for (SqlTypeName fromTypeName : SqlTypeName.values()) { // 遍历所有SQL类型名称
      BasicSqlType fromType; // 声明基本SQL类型变量
      try {
        // This only works for basic types. Ignore the rest.
        // 这只适用于基本类型，忽略其他类型
        fromType = (BasicSqlType) f.typeFactory.createSqlType(fromTypeName); // 尝试创建指定类型的基本SQL类型
      } catch (AssertionError e) { // 捕获断言错误（当类型不是基本类型时）
        continue; // 跳过非基本类型
      }
      BasicSqlType nullableFromType = fromType.createWithNullability(!fromType.isNullable); // 创建可空版本的基本类型

      assertCanCast(unknownType, fromType); // 断言：可以从基本类型转换为不可空的UNKNOWN类型
      assertCanCast(unknownType, nullableFromType); // 断言：可以从可空基本类型转换为不可空的UNKNOWN类型
      assertCanCast(nullableUnknownType, fromType); // 断言：可以从基本类型转换为可空的UNKNOWN类型
      assertCanCast(nullableUnknownType, nullableFromType); // 断言：可以从可空基本类型转换为可空的UNKNOWN类型
    }
  }

  /** Tests that casting BOOLEAN to INTEGER is not allowed for the default
   * {@link SqlTypeCoercionRule}, but is allowed in lenient mode. */
  @Test void testCastBooleanToInteger() { // 测试方法：测试BOOLEAN到INTEGER的类型转换，验证默认规则和宽松规则下的转换行为差异
    RelDataType booleanType = f.sqlBoolean; // 获取BOOLEAN类型
    RelDataType intType = f.sqlInt; // 获取INTEGER类型
    final SqlTypeCoercionRule rule = SqlTypeCoercionRule.instance(); // 获取默认的类型强制转换规则
    final SqlTypeCoercionRule lenientRule = // 获取宽松的类型强制转换规则
        SqlTypeCoercionRule.lenientInstance(); // 宽松规则允许更多的类型转换
    assertThat(SqlTypeUtil.canCastFrom(intType, booleanType, rule), // 断言：使用默认规则时，BOOLEAN不能转换为INTEGER
        is(false)); // 结果为false，默认规则不允许BOOLEAN到INTEGER的转换
    assertThat(SqlTypeUtil.canCastFrom(intType, booleanType, lenientRule), // 断言：使用宽松规则时，BOOLEAN可以转换为INTEGER
        is(true)); // 结果为true，宽松规则允许BOOLEAN到INTEGER的转换
  }

  private static void assertCanCast(RelDataType toType, RelDataType fromType) { // 私有静态辅助方法：断言可以从源类型转换为目标类型，验证类型转换的可行性
    final SqlTypeCoercionRule defaultRules = SqlTypeCoercionRule.instance(); // 获取默认的类型强制转换规则
    assertThat( // 断言：验证不使用强制转换时可以进行转换
        String.format(Locale.ROOT, // 使用ROOT语言环境格式化错误信息
            "Expected to be able to cast from %s to %s without coercion.", fromType, toType), // 错误信息：期望能够不使用强制转换进行类型转换
        SqlTypeUtil.canCastFrom(toType, fromType, /* coerce= */ false), is(true)); // 检查不使用强制转换时是否可以转换
    assertThat( // 断言：验证使用强制转换时可以进行转换
        String.format(Locale.ROOT, // 使用ROOT语言环境格式化错误信息
            "Expected to be able to cast from %s to %s with coercion.", fromType, toType), // 错误信息：期望能够使用强制转换进行类型转换
        SqlTypeUtil.canCastFrom(toType, fromType, /* coerce= */ true), is(true)); // 检查使用强制转换时是否可以转换
    assertThat( // 断言：验证使用默认规则时可以进行转换
        String.format(Locale.ROOT, // 使用ROOT语言环境格式化错误信息
            "Expected to be able to cast from %s to %s without coercion.", fromType, toType), // 错误信息：期望能够使用默认规则进行类型转换
        SqlTypeUtil.canCastFrom(toType, fromType, /* coerce= */ defaultRules), is(true)); // 检查使用默认规则时是否可以转换
  }
}
