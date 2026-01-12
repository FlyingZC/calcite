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
package org.apache.calcite.test; // 定义包名,该类位于org.apache.calcite.test测试包中

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂接口,用于创建Java类型
import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // 导入Java类型工厂实现类
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口,表示Calcite中的数据类型
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入关系数据类型系统接口,定义类型系统的行为
import org.apache.calcite.rex.RexBuilder; // 导入Rex表达式构建器,用于构建行表达式(RexNode)
import org.apache.calcite.rex.RexCallBinding; // 导入Rex调用绑定类,用于绑定Rex函数调用
import org.apache.calcite.rex.RexNode; // 导入Rex节点接口,表示行表达式节点
import org.apache.calcite.rex.RexUtil; // 导入Rex工具类,提供Rex节点的实用方法
import org.apache.calcite.sql.SqlCallBinding; // 导入SQL调用绑定类,用于绑定SQL函数调用
import org.apache.calcite.sql.SqlCharStringLiteral; // 导入SQL字符字符串字面量类
import org.apache.calcite.sql.SqlDataTypeSpec; // 导入SQL数据类型规范类,表示SQL数据类型规范
import org.apache.calcite.sql.SqlLiteral; // 导入SQL字面量类,表示SQL字面量值
import org.apache.calcite.sql.SqlNode; // 导入SQL节点接口,表示SQL抽象语法树节点
import org.apache.calcite.sql.SqlOperatorBinding; // 导入SQL操作符绑定类,用于获取操作符的绑定信息
import org.apache.calcite.sql.SqlUtil; // 导入SQL工具类,提供SQL节点的实用方法
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SQL标准操作符表,包含所有标准SQL操作符
import org.apache.calcite.sql.parser.SqlParserPos; // 导入SQL解析位置类,表示SQL语法树节点的位置
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举,定义所有SQL标准类型名称
import org.apache.calcite.sql.type.SqlTypeUtil; // 导入SQL类型工具类,提供类型相关的实用方法

import com.google.common.collect.Lists; // 导入Google Guava集合工具类,提供列表操作方法

import org.junit.jupiter.api.BeforeEach; // 导入JUnit5的BeforeEach注解,标记在每个测试方法前执行的方法
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解,标记测试方法

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest断言工具,用于断言值相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具,用于执行断言

/**
 * Unit tests for {@link SqlOperatorBinding} and its sub-classes // 单元测试类,用于测试SqlOperatorBinding及其子类
 * {@link SqlCallBinding} and {@link RexCallBinding}. // SqlCallBinding和RexCallBinding的功能
 */
class SqlOperatorBindingTest { // 测试类定义,测试SqlOperatorBinding及其子类的字面量判断功能
  private RexBuilder rexBuilder; // Rex表达式构建器,用于在测试中创建Rex节点(行表达式),是构建Rex表达式树的核心工具
  private RelDataType integerDataType; // 整数数据类型对象,表示SQL的INTEGER类型,用于测试中创建整数类型的Rex节点和SQL节点
  private SqlDataTypeSpec integerType; // 整数数据类型规范对象,表示INTEGER类型的SQL规范,用于CAST等类型转换操作

  @BeforeEach // JUnit5注解,表示该方法在每个测试方法执行前运行,用于初始化测试环境
  void setUp() { // 测试设置方法,初始化测试所需的成员变量
    JavaTypeFactory typeFactory = new JavaTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建Java类型工厂实例,使用默认的关系数据类型系统
    integerDataType = typeFactory.createSqlType(SqlTypeName.INTEGER); // 通过类型工厂创建INTEGER类型的数据类型对象,用于后续测试
    integerType = SqlTypeUtil.convertTypeToSpec(integerDataType); // 将RelDataType转换为SqlDataTypeSpec,用于SQL语法树中的类型规范
    rexBuilder = new RexBuilder(typeFactory); // 创建Rex构建器实例,传入类型工厂,用于构建Rex表达式节点
  }

  /** Tests {@link org.apache.calcite.sql.SqlUtil#isLiteral(SqlNode, boolean)}, // 测试SqlUtil.isLiteral方法,该方法用于判断SQL节点是否为字面量
   * which was added to enhance Calcite's public API // 该方法是为了增强Calcite的公共API而添加的
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1219">[CALCITE-1219] // 关联的JIRA问题链接,用于追踪此功能的开发
   * Add a method to SqlOperatorBinding to determine whether operand is a // 添加一个方法到SqlOperatorBinding来判断操作数是否为
   * literal</a>. // 字面量
   */
  @Test void testSqlNodeLiteral() { // 测试方法,测试SqlNode节点的字面量判断功能
    final SqlParserPos pos = SqlParserPos.ZERO; // 创建SQL解析位置对象,位置为(0,0),用于标记SQL语法树节点的位置
    final SqlNode zeroLiteral = SqlLiteral.createExactNumeric("0", pos); // 创建数值0的SQL字面量节点,表示精确数字0
    final SqlNode oneLiteral = SqlLiteral.createExactNumeric("1", pos); // 创建数值1的SQL字面量节点,表示精确数字1
    final SqlNode nullLiteral = SqlLiteral.createNull(pos); // 创建NULL值的SQL字面量节点,表示SQL中的NULL值
    final SqlCharStringLiteral aLiteral = SqlLiteral.createCharString("a", pos); // 创建字符字符串"a"的SQL字面量节点

    final SqlNode castLiteral = // 创建CAST表达式节点,将0字面量转换为INTEGER类型
        SqlStdOperatorTable.CAST.createCall(pos, zeroLiteral, integerType); // 使用CAST操作符创建调用,传入位置、操作数和目标类型
    final SqlNode castCastLiteral = // 创建嵌套CAST表达式节点,将CAST结果再次转换为INTEGER类型
        SqlStdOperatorTable.CAST.createCall(pos, castLiteral, integerType); // 对castLiteral再进行一次CAST操作
    final SqlNode mapLiteral = // 创建MAP字面量节点,构造MAP['a', 1]
        SqlStdOperatorTable.MAP_VALUE_CONSTRUCTOR.createCall(pos, // 使用MAP值构造操作符创建调用
            aLiteral, oneLiteral); // 传入键值对:键为字符'a',值为数字1
    final SqlNode map2Literal = // 创建MAP字面量节点,构造MAP['a', CAST(0 AS INTEGER)]
        SqlStdOperatorTable.MAP_VALUE_CONSTRUCTOR.createCall(pos, // 使用MAP值构造操作符创建调用
            aLiteral, castLiteral); // 传入键值对:键为字符'a',值为CAST后的0
    final SqlNode arrayLiteral = // 创建ARRAY字面量节点,构造ARRAY[0, 1]
        SqlStdOperatorTable.ARRAY_VALUE_CONSTRUCTOR.createCall(pos, // 使用ARRAY值构造操作符创建调用
            zeroLiteral, oneLiteral); // 传入数组元素:0和1
    final SqlNode defaultCall = SqlStdOperatorTable.DEFAULT.createCall(pos); // 创建DEFAULT调用节点,表示列的默认值

    // SqlLiteral is considered a literal // 注释:SqlLiteral类型的节点被认为是字面量
    assertThat(SqlUtil.isLiteral(zeroLiteral, false), is(true)); // 断言:当allowCast=false时,0字面量被认为是字面量(返回true)
    assertThat(SqlUtil.isLiteral(zeroLiteral, true), is(true)); // 断言:当allowCast=true时,0字面量被认为是字面量(返回true)
    // NULL literal is considered a literal // 注释:NULL字面量被认为是字面量
    assertThat(SqlUtil.isLiteral(nullLiteral, false), is(true)); // 断言:当allowCast=false时,NULL字面量被认为是字面量(返回true)
    assertThat(SqlUtil.isLiteral(nullLiteral, true), is(true)); // 断言:当allowCast=true时,NULL字面量被认为是字面量(返回true)
    // CAST(SqlLiteral as type) is considered a literal, iff allowCast // 注释:CAST(字面量 as 类型)只有在allowCast=true时才被认为是字面量
    assertThat(SqlUtil.isLiteral(castLiteral, false), is(false)); // 断言:当allowCast=false时,CAST(0 as INTEGER)不被认为是字面量(返回false)
    assertThat(SqlUtil.isLiteral(castLiteral, true), is(true)); // 断言:当allowCast=true时,CAST(0 as INTEGER)被认为是字面量(返回true)
    // CAST(CAST(SqlLiteral as type) as type) is considered a literal, // 注释:嵌套的CAST表达式只有在allowCast=true时才被认为是字面量
    // iff allowCast // 条件是allowCast参数为true
    assertThat(SqlUtil.isLiteral(castCastLiteral, false), is(false)); // 断言:当allowCast=false时,嵌套CAST不被认为是字面量(返回false)
    assertThat(SqlUtil.isLiteral(castCastLiteral, true), is(true)); // 断言:当allowCast=true时,嵌套CAST被认为是字面量(返回true)
    // MAP['a', 1] and MAP['a', CAST(0 AS INTEGER)] are considered literals, // 注释:MAP构造器只有在allowCast=true时才被认为是字面量
    // iff allowCast // 条件是allowCast参数为true
    assertThat(SqlUtil.isLiteral(mapLiteral, false), is(false)); // 断言:当allowCast=false时,MAP['a',1]不被认为是字面量(返回false)
    assertThat(SqlUtil.isLiteral(mapLiteral, true), is(true)); // 断言:当allowCast=true时,MAP['a',1]被认为是字面量(返回true)
    assertThat(SqlUtil.isLiteral(map2Literal, false), is(false)); // 断言:当allowCast=false时,MAP['a',CAST(0)]不被认为是字面量(返回false)
    assertThat(SqlUtil.isLiteral(map2Literal, true), is(true)); // 断言:当allowCast=true时,MAP['a',CAST(0)]被认为是字面量(返回true)
    // ARRAY[0, 1] is considered a literal, iff allowCast // 注释:ARRAY构造器只有在allowCast=true时才被认为是字面量
    assertThat(SqlUtil.isLiteral(arrayLiteral, false), is(false)); // 断言:当allowCast=false时,ARRAY[0,1]不被认为是字面量(返回false)
    assertThat(SqlUtil.isLiteral(arrayLiteral, true), is(true)); // 断言:当allowCast=true时,ARRAY[0,1]被认为是字面量(返回true)
    // DEFAULT is considered a literal, iff allowCast // 注释:DEFAULT调用只有在allowCast=true时才被认为是字面量
    assertThat(SqlUtil.isLiteral(defaultCall, false), is(false)); // 断言:当allowCast=false时,DEFAULT不被认为是字面量(返回false)
    assertThat(SqlUtil.isLiteral(defaultCall, true), is(true)); // 断言:当allowCast=true时,DEFAULT被认为是字面量(返回true)
  }

  /** Tests {@link org.apache.calcite.rex.RexUtil#isLiteral(RexNode, boolean)}, // 测试RexUtil.isLiteral方法,该方法用于判断Rex节点是否为字面量
   * which was added to enhance Calcite's public API // 该方法是为了增强Calcite的公共API而添加的
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1219">[CALCITE-1219] // 关联的JIRA问题链接,用于追踪此功能的开发
   * Add a method to SqlOperatorBinding to determine whether operand is a // 添加一个方法到SqlOperatorBinding来判断操作数是否为
   * literal</a>. // 字面量
   */
  @Test void testRexNodeLiteral() { // 测试方法,测试Rex节点的字面量判断功能
    final RexNode literal = // 创建Rex字面量节点,表示值为0的整数字面量
        rexBuilder.makeZeroLiteral(integerDataType); // 使用RexBuilder创建0字面量,指定数据类型为INTEGER

    final RexNode castLiteral = // 创建CAST表达式Rex节点,将0字面量转换为INTEGER类型
        rexBuilder.makeCall(integerDataType, // 使用RexBuilder创建函数调用,指定返回类型为INTEGER
            SqlStdOperatorTable.CAST, // 使用CAST操作符进行类型转换
            Lists.newArrayList(literal)); // 将0字面量作为操作数列表传入

    final RexNode castCastLiteral = // 创建嵌套CAST表达式Rex节点,将CAST结果再次转换为INTEGER类型
        rexBuilder.makeCall(integerDataType, // 使用RexBuilder创建函数调用,指定返回类型为INTEGER
            SqlStdOperatorTable.CAST, // 使用CAST操作符进行类型转换
            Lists.newArrayList(castLiteral)); // 将castLiteral作为操作数列表传入

    // RexLiteral is considered a literal // 注释:RexLiteral类型的节点被认为是字面量
    assertThat(RexUtil.isLiteral(literal, true), is(true)); // 断言:当allowCast=true时,RexLiteral被认为是字面量(返回true)
    // CAST(RexLiteral as type) is considered a literal // 注释:CAST(RexLiteral as 类型)被认为是字面量
    assertThat(RexUtil.isLiteral(castLiteral, true), is(true)); // 断言:当allowCast=true时,CAST(RexLiteral)被认为是字面量(返回true)
    // CAST(CAST(RexLiteral as type) as type) is NOT considered a literal // 注释:嵌套的CAST(RexLiteral)不被认为是字面量
    assertThat(RexUtil.isLiteral(castCastLiteral, true), is(false)); // 断言:当allowCast=true时,嵌套CAST不被认为是字面量(返回false)
  }
}
