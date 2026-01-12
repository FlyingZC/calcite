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
package org.apache.calcite.sql.test; // 指定该类所在的包路径，属于org.apache.calcite.sql.test包，这是Calcite框架SQL测试相关的包

import org.apache.calcite.sql.type.ExtraSqlTypes; // 导入ExtraSqlTypes类，该类定义了JDBC标准之外的扩展SQL类型常量（如ROWID、NCHAR、NVARCHAR等）
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举类，该类定义了Calcite支持的所有SQL类型名称（如INTEGER、VARCHAR、DATE等）

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法，JUnit会自动识别并执行带有此注解的方法

import java.sql.Types; // 导入JDBC的Types类，该类定义了JDBC标准的所有SQL类型常量（如Types.INTEGER、Types.VARCHAR等）

import static org.apache.calcite.sql.type.SqlTypeName.ARRAY; // 静态导入SqlTypeName.ARRAY，允许在代码中直接使用ARRAY而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.BIGINT; // 静态导入SqlTypeName.BIGINT，允许在代码中直接使用BIGINT而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.BINARY; // 静态导入SqlTypeName.BINARY，允许在代码中直接使用BINARY而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.BOOLEAN; // 静态导入SqlTypeName.BOOLEAN，允许在代码中直接使用BOOLEAN而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.CHAR; // 静态导入SqlTypeName.CHAR，允许在代码中直接使用CHAR而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.DATE; // 静态导入SqlTypeName.DATE，允许在代码中直接使用DATE而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.DECIMAL; // 静态导入SqlTypeName.DECIMAL，允许在代码中直接使用DECIMAL而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.DISTINCT; // 静态导入SqlTypeName.DISTINCT，允许在代码中直接使用DISTINCT而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.DOUBLE; // 静态导入SqlTypeName.DOUBLE，允许在代码中直接使用DOUBLE而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.FLOAT; // 静态导入SqlTypeName.FLOAT，允许在代码中直接使用FLOAT而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.INTEGER; // 静态导入SqlTypeName.INTEGER，允许在代码中直接使用INTEGER而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.REAL; // 静态导入SqlTypeName.REAL，允许在代码中直接使用REAL而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.SMALLINT; // 静态导入SqlTypeName.SMALLINT，允许在代码中直接使用SMALLINT而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.STRUCTURED; // 静态导入SqlTypeName.STRUCTURED，允许在代码中直接使用STRUCTURED而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.TIME; // 静态导入SqlTypeName.TIME，允许在代码中直接使用TIME而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.TIMESTAMP; // 静态导入SqlTypeName.TIMESTAMP，允许在代码中直接使用TIMESTAMP而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.TINYINT; // 静态导入SqlTypeName.TINYINT，允许在代码中直接使用TINYINT而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.VARBINARY; // 静态导入SqlTypeName.VARBINARY，允许在代码中直接使用VARBINARY而不需要前缀SqlTypeName
import static org.apache.calcite.sql.type.SqlTypeName.VARCHAR; // 静态导入SqlTypeName.VARCHAR，允许在代码中直接使用VARCHAR而不需要前缀SqlTypeName

import static org.hamcrest.CoreMatchers.is; // 静态导入Hamcrest的is匹配器，用于断言两个值是否相等
import static org.hamcrest.CoreMatchers.nullValue; // 静态导入Hamcrest的nullValue匹配器，用于断言值是否为null
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入Hamcrest的assertThat方法，用于编写可读性强的断言语句

/**
 * Tests types supported by {@link SqlTypeName}.
 * SqlTypeNameTest类用于测试Calcite框架中SqlTypeName枚举类型与JDBC类型之间的映射关系
 * 该类包含多个测试方法，每个方法验证一个特定的JDBC类型到SqlTypeName的映射是否正确
 * 主要测试场景包括：
 * 1. 基本数据类型映射（如INTEGER、VARCHAR、DATE等）
 * 2. 特殊类型映射（如ARRAY、STRUCTURED、DISTINCT等）
 * 3. 不支持的类型映射（应返回null）
 * 4. 扩展SQL类型映射（如NCHAR、NVARCHAR等）
 * 
 * 测试方法命名规范：test{JdbcTypeName}，例如testInteger测试Types.INTEGER的映射
 * 每个测试方法都调用SqlTypeName.getNameForJdbcType()方法获取对应的SqlTypeName
 * 然后使用断言验证映射结果是否符合预期
 * 
 * 这个测试类确保了Calcite能够正确识别和处理各种JDBC类型
 * 这对于不同数据库之间的类型兼容性和SQL查询的正确性至关重要
 */
class SqlTypeNameTest {
  @Test void testBit() { // 测试JDBC的BIT类型映射到Calcite的SqlTypeName.BOLEAN类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.BIT); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的BIT类型常量，获取对应的Calcite类型
    assertThat("BIT did not map to BOOLEAN", tn, is(BOOLEAN)); // 使用Hamcrest断言验证tn是否等于BOOLEAN，如果不等则输出错误信息"BIT did not map to BOOLEAN"
  }

  @Test void testTinyint() { // 测试JDBC的TINYINT类型映射到Calcite的SqlTypeName.TINYINT类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.TINYINT); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的TINYINT类型常量，获取对应的Calcite类型
    assertThat("TINYINT did not map to TINYINT", tn, is(TINYINT)); // 使用Hamcrest断言验证tn是否等于TINYINT，如果不等则输出错误信息"TINYINT did not map to TINYINT"
  }

  @Test void testSmallint() { // 测试JDBC的SMALLINT类型映射到Calcite的SqlTypeName.SMALLINT类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.SMALLINT); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的SMALLINT类型常量，获取对应的Calcite类型
    assertThat("SMALLINT did not map to SMALLINT", tn, is(SMALLINT)); // 使用Hamcrest断言验证tn是否等于SMALLINT，如果不等则输出错误信息"SMALLINT did not map to SMALLINT"
  }

  @Test void testInteger() { // 测试JDBC的INTEGER类型映射到Calcite的SqlTypeName.INTEGER类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.INTEGER); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的INTEGER类型常量，获取对应的Calcite类型
    assertThat("INTEGER did not map to INTEGER", tn, is(INTEGER)); // 使用Hamcrest断言验证tn是否等于INTEGER，如果不等则输出错误信息"INTEGER did not map to INTEGER"
  }

  @Test void testBigint() { // 测试JDBC的BIGINT类型映射到Calcite的SqlTypeName.BIGINT类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.BIGINT); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的BIGINT类型常量，获取对应的Calcite类型
    assertThat("BIGINT did not map to BIGINT", tn, is(BIGINT)); // 使用Hamcrest断言验证tn是否等于BIGINT，如果不等则输出错误信息"BIGINT did not map to BIGINT"
  }

  @Test void testFloat() { // 测试JDBC的FLOAT类型映射到Calcite的SqlTypeName.FLOAT类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.FLOAT); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的FLOAT类型常量，获取对应的Calcite类型
    assertThat("FLOAT did not map to FLOAT", tn, is(FLOAT)); // 使用Hamcrest断言验证tn是否等于FLOAT，如果不等则输出错误信息"FLOAT did not map to FLOAT"
  }

  @Test void testReal() { // 测试JDBC的REAL类型映射到Calcite的SqlTypeName.REAL类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.REAL); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的REAL类型常量，获取对应的Calcite类型
    assertThat("REAL did not map to REAL", tn, is(REAL)); // 使用Hamcrest断言验证tn是否等于REAL，如果不等则输出错误信息"REAL did not map to REAL"
  }

  @Test void testDouble() { // 测试JDBC的DOUBLE类型映射到Calcite的SqlTypeName.DOUBLE类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.DOUBLE); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的DOUBLE类型常量，获取对应的Calcite类型
    assertThat("DOUBLE did not map to DOUBLE", tn, is(DOUBLE)); // 使用Hamcrest断言验证tn是否等于DOUBLE，如果不等则输出错误信息"DOUBLE did not map to DOUBLE"
  }

  @Test void testNumeric() { // 测试JDBC的NUMERIC类型映射到Calcite的SqlTypeName.DECIMAL类型（注意：NUMERIC映射到DECIMAL，而不是NUMERIC）
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.NUMERIC); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的NUMERIC类型常量，获取对应的Calcite类型
    assertThat("NUMERIC did not map to DECIMAL", tn, is(DECIMAL)); // 使用Hamcrest断言验证tn是否等于DECIMAL，如果不等则输出错误信息"NUMERIC did not map to DECIMAL"
  }

  @Test void testDecimal() { // 测试JDBC的DECIMAL类型映射到Calcite的SqlTypeName.DECIMAL类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.DECIMAL); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的DECIMAL类型常量，获取对应的Calcite类型
    assertThat("DECIMAL did not map to DECIMAL", tn, is(DECIMAL)); // 使用Hamcrest断言验证tn是否等于DECIMAL，如果不等则输出错误信息"DECIMAL did not map to DECIMAL"
  }

  @Test void testChar() { // 测试JDBC的CHAR类型映射到Calcite的SqlTypeName.CHAR类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.CHAR); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的CHAR类型常量，获取对应的Calcite类型
    assertThat("CHAR did not map to CHAR", tn, is(CHAR)); // 使用Hamcrest断言验证tn是否等于CHAR，如果不等则输出错误信息"CHAR did not map to CHAR"
  }

  @Test void testVarchar() { // 测试JDBC的VARCHAR类型映射到Calcite的SqlTypeName.VARCHAR类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.VARCHAR); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的VARCHAR类型常量，获取对应的Calcite类型
    assertThat("VARCHAR did not map to VARCHAR", tn, is(VARCHAR)); // 使用Hamcrest断言验证tn是否等于VARCHAR，如果不等则输出错误信息"VARCHAR did not map to VARCHAR"
  }

  @Test void testLongvarchar() { // 测试JDBC的LONGVARCHAR类型映射到Calcite的SqlTypeName，预期返回null（因为Calcite不支持LONGVARCHAR类型）
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.LONGVARCHAR); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的LONGVARCHAR类型常量，获取对应的Calcite类型
    assertThat("LONGVARCHAR did not map to null", tn, nullValue()); // 使用Hamcrest断言验证tn是否为null，如果不为null则输出错误信息"LONGVARCHAR did not map to null"
  }

  @Test void testDate() { // 测试JDBC的DATE类型映射到Calcite的SqlTypeName.DATE类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.DATE); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的DATE类型常量，获取对应的Calcite类型
    assertThat("DATE did not map to DATE", tn, is(DATE)); // 使用Hamcrest断言验证tn是否等于DATE，如果不等则输出错误信息"DATE did not map to DATE"
  }

  @Test void testTime() { // 测试JDBC的TIME类型映射到Calcite的SqlTypeName.TIME类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.TIME); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的TIME类型常量，获取对应的Calcite类型
    assertThat("TIME did not map to TIME", tn, is(TIME)); // 使用Hamcrest断言验证tn是否等于TIME，如果不等则输出错误信息"TIME did not map to TIME"
  }

  @Test void testTimestamp() { // 测试JDBC的TIMESTAMP类型映射到Calcite的SqlTypeName.TIMESTAMP类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.TIMESTAMP); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的TIMESTAMP类型常量，获取对应的Calcite类型
    assertThat("TIMESTAMP did not map to TIMESTAMP", tn, is(TIMESTAMP)); // 使用Hamcrest断言验证tn是否等于TIMESTAMP，如果不等则输出错误信息"TIMESTAMP did not map to TIMESTAMP"
  }

  @Test void testBinary() { // 测试JDBC的BINARY类型映射到Calcite的SqlTypeName.BINARY类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.BINARY); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的BINARY类型常量，获取对应的Calcite类型
    assertThat("BINARY did not map to BINARY", tn, is(BINARY)); // 使用Hamcrest断言验证tn是否等于BINARY，如果不等则输出错误信息"BINARY did not map to BINARY"
  }

  @Test void testVarbinary() { // 测试JDBC的VARBINARY类型映射到Calcite的SqlTypeName.VARBINARY类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.VARBINARY); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的VARBINARY类型常量，获取对应的Calcite类型
    assertThat("VARBINARY did not map to VARBINARY", tn, is(VARBINARY)); // 使用Hamcrest断言验证tn是否等于VARBINARY，如果不等则输出错误信息"VARBINARY did not map to VARBINARY"
  }

  @Test void testLongvarbinary() { // 测试JDBC的LONGVARBINARY类型映射到Calcite的SqlTypeName，预期返回null（因为Calcite不支持LONGVARBINARY类型）
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.LONGVARBINARY); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的LONGVARBINARY类型常量，获取对应的Calcite类型
    assertThat("LONGVARBINARY did not map to null", tn, nullValue()); // 使用Hamcrest断言验证tn是否为null，如果不为null则输出错误信息"LONGVARBINARY did not map to null"
  }

  @Test void testNull() { // 测试JDBC的NULL类型映射到Calcite的SqlTypeName，预期返回null（因为Calcite不支持NULL类型）
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.NULL); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的NULL类型常量，获取对应的Calcite类型
    assertThat("NULL did not map to null", tn, nullValue()); // 使用Hamcrest断言验证tn是否为null，如果不为null则输出错误信息"NULL did not map to null"
  }

  @Test void testOther() { // 测试JDBC的OTHER类型映射到Calcite的SqlTypeName，预期返回null（因为Calcite不支持OTHER类型）
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.OTHER); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的OTHER类型常量，获取对应的Calcite类型
    assertThat("OTHER did not map to null", tn, nullValue()); // 使用Hamcrest断言验证tn是否为null，如果不为null则输出错误信息"OTHER did not map to null"
  }

  @Test void testJavaobject() { // 测试JDBC的JAVA_OBJECT类型映射到Calcite的SqlTypeName，预期返回null（因为Calcite不支持JAVA_OBJECT类型）
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.JAVA_OBJECT); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的JAVA_OBJECT类型常量，获取对应的Calcite类型
    assertThat("JAVA_OBJECT did not map to null", tn, nullValue()); // 使用Hamcrest断言验证tn是否为null，如果不为null则输出错误信息"JAVA_OBJECT did not map to null"
  }

  @Test void testDistinct() { // 测试JDBC的DISTINCT类型映射到Calcite的SqlTypeName.DISTINCT类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.DISTINCT); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的DISTINCT类型常量，获取对应的Calcite类型
    assertThat("DISTINCT did not map to DISTINCT", tn, is(DISTINCT)); // 使用Hamcrest断言验证tn是否等于DISTINCT，如果不等则输出错误信息"DISTINCT did not map to DISTINCT"
  }

  @Test void testStruct() { // 测试JDBC的STRUCT类型映射到Calcite的SqlTypeName.STRUCTURED类型（注意：STRUCT映射到STRUCTURED，而不是STRUCT）
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.STRUCT); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的STRUCT类型常量，获取对应的Calcite类型
    assertThat("STRUCT did not map to null", tn, is(STRUCTURED)); // 使用Hamcrest断言验证tn是否等于STRUCTURED，如果不等则输出错误信息"STRUCT did not map to null"
  }

  @Test void testArray() { // 测试JDBC的ARRAY类型映射到Calcite的SqlTypeName.ARRAY类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.ARRAY); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的ARRAY类型常量，获取对应的Calcite类型
    assertThat("ARRAY did not map to ARRAY", tn, is(ARRAY)); // 使用Hamcrest断言验证tn是否等于ARRAY，如果不等则输出错误信息"ARRAY did not map to ARRAY"
  }

  @Test void testBlob() { // 测试JDBC的BLOB类型映射到Calcite的SqlTypeName，预期返回null（因为Calcite不支持BLOB类型）
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.BLOB); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的BLOB类型常量，获取对应的Calcite类型
    assertThat("BLOB did not map to null", tn, nullValue()); // 使用Hamcrest断言验证tn是否为null，如果不为null则输出错误信息"BLOB did not map to null"
  }

  @Test void testClob() { // 测试JDBC的CLOB类型映射到Calcite的SqlTypeName，预期返回null（因为Calcite不支持CLOB类型）
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.CLOB); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的CLOB类型常量，获取对应的Calcite类型
    assertThat("CLOB did not map to null", tn, nullValue()); // 使用Hamcrest断言验证tn是否为null，如果不为null则输出错误信息"CLOB did not map to null"
  }

  @Test void testRef() { // 测试JDBC的REF类型映射到Calcite的SqlTypeName，预期返回null（因为Calcite不支持REF类型）
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.REF); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的REF类型常量，获取对应的Calcite类型
    assertThat("REF did not map to null", tn, nullValue()); // 使用Hamcrest断言验证tn是否为null，如果不为null则输出错误信息"REF did not map to null"
  }

  @Test void testDatalink() { // 测试JDBC的DATALINK类型映射到Calcite的SqlTypeName，预期返回null（因为Calcite不支持DATALINK类型）
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.DATALINK); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的DATALINK类型常量，获取对应的Calcite类型
    assertThat("DATALINK did not map to null", tn, nullValue()); // 使用Hamcrest断言验证tn是否为null，如果不为null则输出错误信息"DATALINK did not map to null"
  }

  @Test void testBoolean() { // 测试JDBC的BOOLEAN类型映射到Calcite的SqlTypeName.BOOLEAN类型
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(Types.BOOLEAN); // 调用SqlTypeName.getNameForJdbcType方法，传入JDBC的BOOLEAN类型常量，获取对应的Calcite类型
    assertThat("BOOLEAN did not map to BOOLEAN", tn, is(BOOLEAN)); // 使用Hamcrest断言验证tn是否等于BOOLEAN，如果不等则输出错误信息"BOOLEAN did not map to BOOLEAN"
  }

  @Test void testRowid() { // 测试ExtraSqlTypes的ROWID类型映射到Calcite的SqlTypeName，预期返回null（因为Calcite暂不支持ROWID类型）
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(ExtraSqlTypes.ROWID); // 调用SqlTypeName.getNameForJdbcType方法，传入ExtraSqlTypes的ROWID类型常量，获取对应的Calcite类型

    // ROWID not supported yet // 注释说明：ROWID类型尚未被Calcite支持
    assertThat("ROWID maps to non-null type", tn, nullValue()); // 使用Hamcrest断言验证tn是否为null，如果不为null则输出错误信息"ROWID maps to non-null type"
  }

  @Test void testNchar() { // 测试ExtraSqlTypes的NCHAR类型映射到Calcite的SqlTypeName，预期映射到CHAR类型（因为Calcite暂不支持NCHAR类型，当前映射到CHAR）
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(ExtraSqlTypes.NCHAR); // 调用SqlTypeName.getNameForJdbcType方法，传入ExtraSqlTypes的NCHAR类型常量，获取对应的Calcite类型

    // NCHAR not supported yet, currently maps to CHAR // 注释说明：NCHAR类型尚未被Calcite支持，当前映射到CHAR类型
    assertThat("NCHAR did not map to CHAR", tn, is(CHAR)); // 使用Hamcrest断言验证tn是否等于CHAR，如果不等则输出错误信息"NCHAR did not map to CHAR"
  }

  @Test void testNvarchar() { // 测试ExtraSqlTypes的NVARCHAR类型映射到Calcite的SqlTypeName，预期映射到VARCHAR类型（因为Calcite暂不支持NVARCHAR类型，当前映射到VARCHAR）
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(ExtraSqlTypes.NVARCHAR); // 调用SqlTypeName.getNameForJdbcType方法，传入ExtraSqlTypes的NVARCHAR类型常量，获取对应的Calcite类型

    // NVARCHAR not supported yet, currently maps to VARCHAR // 注释说明：NVARCHAR类型尚未被Calcite支持，当前映射到VARCHAR类型
    assertThat("NVARCHAR did not map to VARCHAR", tn, is(VARCHAR)); // 使用Hamcrest断言验证tn是否等于VARCHAR，如果不等则输出错误信息"NVARCHAR did not map to VARCHAR"
  }

  @Test void testLongnvarchar() { // 测试ExtraSqlTypes的LONGNVARCHAR类型映射到Calcite的SqlTypeName，预期返回null（因为Calcite暂不支持LONGNVARCHAR类型）
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(ExtraSqlTypes.LONGNVARCHAR); // 调用SqlTypeName.getNameForJdbcType方法，传入ExtraSqlTypes的LONGNVARCHAR类型常量，获取对应的Calcite类型

    // LONGNVARCHAR not supported yet // 注释说明：LONGNVARCHAR类型尚未被Calcite支持
    assertThat("LONGNVARCHAR maps to non-null type", tn, nullValue()); // 使用Hamcrest断言验证tn是否为null，如果不为null则输出错误信息"LONGNVARCHAR maps to non-null type"
  }

  @Test void testNclob() { // 测试ExtraSqlTypes的NCLOB类型映射到Calcite的SqlTypeName，预期返回null（因为Calcite暂不支持NCLOB类型）
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(ExtraSqlTypes.NCLOB); // 调用SqlTypeName.getNameForJdbcType方法，传入ExtraSqlTypes的NCLOB类型常量，获取对应的Calcite类型

    // NCLOB not supported yet // 注释说明：NCLOB类型尚未被Calcite支持
    assertThat("NCLOB maps to non-null type", tn, nullValue()); // 使用Hamcrest断言验证tn是否为null，如果不为null则输出错误信息"NCLOB maps to non-null type"
  }

  @Test void testSqlxml() { // 测试ExtraSqlTypes的SQLXML类型映射到Calcite的SqlTypeName，预期返回null（因为Calcite暂不支持SQLXML类型）
    SqlTypeName tn = // 声明SqlTypeName变量tn，用于存储从JDBC类型映射得到的SqlTypeName
        SqlTypeName.getNameForJdbcType(ExtraSqlTypes.SQLXML); // 调用SqlTypeName.getNameForJdbcType方法，传入ExtraSqlTypes的SQLXML类型常量，获取对应的Calcite类型

    // SQLXML not supported yet // 注释说明：SQLXML类型尚未被Calcite支持
    assertThat("SQLXML maps to non-null type", tn, nullValue()); // 使用Hamcrest断言验证tn是否为null，如果不为null则输出错误信息"SQLXML maps to non-null type"
  }
}
