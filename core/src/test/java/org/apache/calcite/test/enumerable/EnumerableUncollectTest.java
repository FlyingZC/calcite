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
package org.apache.calcite.test.enumerable; // 声明包名，该测试类位于org.apache.calcite.test.enumerable包下

import org.apache.calcite.config.CalciteConnectionProperty; // 导入Calcite连接属性配置类，用于配置Calcite连接的各种属性
import org.apache.calcite.config.Lex; // 导入词法分析配置类，用于配置SQL词法分析策略
import org.apache.calcite.test.CalciteAssert; // 导入Calcite断言工具类，用于编写和执行SQL测试

import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

/** Test for {@link org.apache.calcite.adapter.enumerable.EnumerableUncollect}. */ // 类文档注释：这是对EnumerableUncollect类的测试类，用于测试UNNEST/Uncollect操作符的功能
class EnumerableUncollectTest { // 定义测试类EnumerableUncollectTest，用于测试EnumerableUncollect操作符的各种场景

  @Test void simpleUnnestArray() { // 测试方法：测试最简单的数组展开操作，UNNEST可以将数组中的元素展开为多行
    final String sql = "select * from UNNEST(array[3, 4]) as T2(y)"; // 定义SQL语句，使用UNNEST展开包含整数3和4的数组，并给结果列别名为y
    tester() // 调用tester()方法获取CalciteAssert测试器实例
        .query(sql) // 执行SQL查询
        .returnsUnordered( // 验证查询结果，不关心行的顺序
            "y=3", // 期望第一行结果：y列的值为3
            "y=4"); // 期望第二行结果：y列的值为4
  }

  @Test void simpleUnnestNullArray() { // 测试方法：测试展开null数组的情况
    final String sql = "SELECT * FROM UNNEST(CAST(null AS INTEGER ARRAY))"; // 定义SQL语句，将null转换为整数数组类型，然后展开
    tester() // 调用tester()方法获取测试器实例
        .query(sql) // 执行SQL查询
        .returnsCount(0); // 验证查询结果行数为0，因为展开null数组应该返回空结果
  }

  @Test void simpleUnnestArrayOfArrays() { // 测试方法：测试展开数组的数组，即二维数组的情况
    final String sql = "select * from UNNEST(array[array[3], array[4]]) as T2(y)"; // 定义SQL语句，展开包含两个数组的数组，每个子数组包含一个元素
    tester() // 调用tester()方法获取测试器实例
        .query(sql) // 执行SQL查询
        .returnsUnordered( // 验证查询结果
            "y=[3]", // 期望第一行结果：y列的值为包含3的数组
            "y=[4]"); // 期望第二行结果：y列的值为包含4的数组
  }

  @Test void simpleUnnestArrayOfArrays2() { // 测试方法：测试展开包含多个元素的二维数组
    final String sql = "select * from UNNEST(array[array[3, 4], array[4, 5]]) as T2(y)"; // 定义SQL语句，展开包含两个数组的数组，每个子数组包含两个元素
    tester() // 调用tester()方法获取测试器实例
        .query(sql) // 执行SQL查询
        .returnsUnordered( // 验证查询结果
            "y=[3, 4]", // 期望第一行结果：y列的值为包含3和4的数组
            "y=[4, 5]"); // 期望第二行结果：y列的值为包含4和5的数组
  }

  @Test void simpleUnnestArrayOfArrays3() { // 测试方法：测试展开三维数组的情况
    final String sql = "select * from UNNEST(" // 定义SQL语句的开始部分，使用UNNEST展开
        + "array[array[array[3,4], array[4,5]], array[array[7,8], array[9,10]]]) as T2(y)"; // 继续SQL语句，定义三维数组结构并给结果列别名为y
    tester() // 调用tester()方法获取测试器实例
        .query(sql) // 执行SQL查询
        .returnsUnordered( // 验证查询结果
            "y=[[3, 4], [4, 5]]", // 期望第一行结果：y列的值为二维数组[[3, 4], [4, 5]]
            "y=[[7, 8], [9, 10]]"); // 期望第二行结果：y列的值为二维数组[[7, 8], [9, 10]]
  }

  @Test void simpleUnnestArrayOfRows() { // 测试方法：测试展开包含单列行的数组
    final String sql = "select * from UNNEST(array[ROW(3), ROW(4)]) as T2(y)"; // 定义SQL语句，展开包含两个ROW的数组，每个ROW只有一个值
    tester() // 调用tester()方法获取测试器实例
        .query(sql) // 执行SQL查询
        .returnsUnordered( // 验证查询结果
            "y=3", // 期望第一行结果：y列的值为3（ROW自动展开）
            "y=4"); // 期望第二行结果：y列的值为4（ROW自动展开）
  }

  @Test void simpleUnnestArrayOfRows2() { // 测试方法：测试展开包含多列行的数组
    final String sql = "select * from UNNEST(array[ROW(3, 5), ROW(4, 6)]) as T2(y, z)"; // 定义SQL语句，展开包含两个ROW的数组，每个ROW有两个值，分别映射到y和z列
    tester() // 调用tester()方法获取测试器实例
        .query(sql) // 执行SQL查询
        .returnsUnordered( // 验证查询结果
            "y=3; z=5", // 期望第一行结果：y列值为3，z列值为5
            "y=4; z=6"); // 期望第二行结果：y列值为4，z列值为6
  }

  @Test void simpleUnnestArrayOfRows3() { // 测试方法：测试使用WITH ORDINALITY子句展开数组，该子句会为每个元素添加序号
    final String sql = "select * from UNNEST(array[ROW(3), ROW(4)]) WITH ORDINALITY as T2(y, o)"; // 定义SQL语句，使用WITH ORDINALITY展开数组，o列会自动包含元素的序号（从1开始）
    tester() // 调用tester()方法获取测试器实例
        .query(sql) // 执行SQL查询
        .returnsUnordered( // 验证查询结果
            "y=3; o=1", // 期望第一行结果：y列值为3，o列值为1（第一个元素）
            "y=4; o=2"); // 期望第二行结果：y列值为4，o列值为2（第二个元素）
  }

  @Test void simpleUnnestArrayOfRows4() { // 测试方法：测试展开包含嵌套ROW的数组
    final String sql = "select * from UNNEST(array[ROW(1, ROW(5, 10)), ROW(2, ROW(6, 12))]) " // 定义SQL语句的开始部分，展开包含ROW的数组，ROW中嵌套了另一个ROW
        + "as T2(y, z)"; // 继续SQL语句，将外层ROW的两个值分别映射到y和z列
    tester() // 调用tester()方法获取测试器实例
        .query(sql) // 执行SQL查询
        .returnsUnordered( // 验证查询结果
            "y=1; z={5, 10}", // 期望第一行结果：y列值为1，z列值为嵌套ROW的值{5, 10}
            "y=2; z={6, 12}"); // 期望第二行结果：y列值为2，z列值为嵌套ROW的值{6, 12}
  }

  @Test void simpleUnnestArrayOfRows5() { // 测试方法：测试展开包含单元素嵌套ROW的数组
    final String sql = "select * from UNNEST(array[ROW(ROW(3)), ROW(ROW(4))]) as T2(y)"; // 定义SQL语句，展开包含两个ROW的数组，每个ROW中又包含一个单元素的ROW
    tester() // 调用tester()方法获取测试器实例
        .query(sql) // 执行SQL查询
        .returnsUnordered( // 验证查询结果
            "y={3}", // 期望第一行结果：y列值为嵌套ROW的值{3}
            "y={4}"); // 期望第二行结果：y列值为嵌套ROW的值{4}
  }

  @Test void chainedUnnestArray() { // 测试方法：测试链式UNNEST操作，即在一个查询中使用多个UNNEST，实现笛卡尔积
    final String sql = "select * from (values (1), (2)) T1(x)," // 定义SQL语句的开始部分，创建一个包含两行的临时表T1，x列的值为1和2
        + "UNNEST(array[3, 4]) as T2(y)"; // 继续SQL语句，与UNNEST结果进行笛卡尔积，T2的y列值为3和4
    tester() // 调用tester()方法获取测试器实例
        .query(sql) // 执行SQL查询
        .returnsUnordered( // 验证查询结果，应该有2*2=4行
            "x=1; y=3", // 期望结果：x=1与y=3的组合
            "x=1; y=4", // 期望结果：x=1与y=4的组合
            "x=2; y=3", // 期望结果：x=2与y=3的组合
            "x=2; y=4"); // 期望结果：x=2与y=4的组合
  }

  @Test void chainedUnnestArrayOfArrays() { // 测试方法：测试链式UNNEST操作，其中UNNEST展开的是数组的数组
    final String sql = "select * from (values (1), (2)) T1(x)," // 定义SQL语句的开始部分，创建临时表T1，x列值为1和2
        + "UNNEST(array[array[3], array[4]]) as T2(y)"; // 继续SQL语句，展开包含两个数组的数组，与T1进行笛卡尔积
    tester() // 调用tester()方法获取测试器实例
        .query(sql) // 执行SQL查询
        .returnsUnordered( // 验证查询结果
            "x=1; y=[3]", // 期望结果：x=1与y=[3]的组合
            "x=1; y=[4]", // 期望结果：x=1与y=[4]的组合
            "x=2; y=[3]", // 期望结果：x=2与y=[3]的组合
            "x=2; y=[4]"); // 期望结果：x=2与y=[4]的组合
  }

  @Test void chainedUnnestArrayOfArrays2() { // 测试方法：测试链式UNNEST操作，其中UNNEST展开的是包含多个元素的二维数组
    final String sql = "select * from (values (1), (2)) T1(x)," // 定义SQL语句的开始部分，创建临时表T1
        + "UNNEST(array[array[3, 4], array[4, 5]]) as T2(y)"; // 继续SQL语句，展开包含两个数组的数组，每个数组有两个元素
    tester() // 调用tester()方法获取测试器实例
        .query(sql) // 执行SQL查询
        .returnsUnordered( // 验证查询结果
            "x=1; y=[3, 4]", // 期望结果：x=1与y=[3, 4]的组合
            "x=1; y=[4, 5]", // 期望结果：x=1与y=[4, 5]的组合
            "x=2; y=[3, 4]", // 期望结果：x=2与y=[3, 4]的组合
            "x=2; y=[4, 5]"); // 期望结果：x=2与y=[4, 5]的组合
  }

  @Test void chainedUnnestArrayOfArrays3() { // 测试方法：测试链式UNNEST操作，其中UNNEST展开的是三维数组
    final String sql = "select * from (values (1), (2)) T1(x)," // 定义SQL语句的开始部分，创建临时表T1
        + "UNNEST(array[array[array[3,4], array[4,5]], array[array[7,8], array[9,10]]]) as T2(y)"; // 继续SQL语句，展开三维数组结构
    tester() // 调用tester()方法获取测试器实例
        .query(sql) // 执行SQL查询
        .returnsUnordered( // 验证查询结果
            "x=1; y=[[3, 4], [4, 5]]", // 期望结果：x=1与y=[[3, 4], [4, 5]]的组合
            "x=1; y=[[7, 8], [9, 10]]", // 期望结果：x=1与y=[[7, 8], [9, 10]]的组合
            "x=2; y=[[3, 4], [4, 5]]", // 期望结果：x=2与y=[[3, 4], [4, 5]]的组合
            "x=2; y=[[7, 8], [9, 10]]"); // 期望结果：x=2与y=[[7, 8], [9, 10]]的组合
  }

  @Test void chainedUnnestArrayOfRows() { // 测试方法：测试链式UNNEST操作，其中UNNEST展开的是包含单列ROW的数组
    final String sql = "select * from (values (1), (2)) T1(x)," // 定义SQL语句的开始部分，创建临时表T1
        + "UNNEST(array[ROW(3), ROW(4)]) as T2(y)"; // 继续SQL语句，展开包含两个ROW的数组，与T1进行笛卡尔积
    tester() // 调用tester()方法获取测试器实例
        .query(sql) // 执行SQL查询
        .returnsUnordered( // 验证查询结果
            "x=1; y=3", // 期望结果：x=1与y=3的组合
            "x=1; y=4", // 期望结果：x=1与y=4的组合
            "x=2; y=3", // 期望结果：x=2与y=3的组合
            "x=2; y=4"); // 期望结果：x=2与y=4的组合
  }

  @Test void chainedUnnestArrayOfRows2() { // 测试方法：测试链式UNNEST操作，其中UNNEST展开的是包含多列ROW的数组
    final String sql = "select * from (values (1), (2)) T1(x)," // 定义SQL语句的开始部分，创建临时表T1
        + "UNNEST(array[ROW(3, 5), ROW(4, 6)]) as T2(y, z)"; // 继续SQL语句，展开包含两个ROW的数组，每个ROW有两个值，分别映射到y和z列
    tester() // 调用tester()方法获取测试器实例
        .query(sql) // 执行SQL查询
        .returnsUnordered( // 验证查询结果
            "x=1; y=3; z=5", // 期望结果：x=1, y=3, z=5的组合
            "x=1; y=4; z=6", // 期望结果：x=1, y=4, z=6的组合
            "x=2; y=3; z=5", // 期望结果：x=2, y=3, z=5的组合
            "x=2; y=4; z=6"); // 期望结果：x=2, y=4, z=6的组合
  }

  @Test void chainedUnnestArrayOfRows3() { // 测试方法：测试链式UNNEST操作，使用WITH ORDINALITY子句
    final String sql = "select * from (values (1), (2)) T1(x)," // 定义SQL语句的开始部分，创建临时表T1
        + "UNNEST(array[ROW(3), ROW(4)]) WITH ORDINALITY as T2(y, o)"; // 继续SQL语句，使用WITH ORDINALITY展开数组，o列包含序号
    tester() // 调用tester()方法获取测试器实例
        .query(sql) // 执行SQL查询
        .returnsUnordered( // 验证查询结果
            "x=1; y=3; o=1", // 期望结果：x=1, y=3, o=1（第一个元素）
            "x=1; y=4; o=2", // 期望结果：x=1, y=4, o=2（第二个元素）
            "x=2; y=3; o=1", // 期望结果：x=2, y=3, o=1（第一个元素）
            "x=2; y=4; o=2"); // 期望结果：x=2, y=4, o=2（第二个元素）
  }

  private CalciteAssert.AssertThat tester() { // 私有辅助方法：创建并配置CalciteAssert测试器实例，用于所有测试方法
    return CalciteAssert.that() // 创建CalciteAssert测试器的基础实例
        .with(CalciteConnectionProperty.LEX, Lex.JAVA) // 配置词法分析器为JAVA模式，支持Java风格的数组语法（如array[1, 2, 3]）
        .with(CalciteConnectionProperty.FORCE_DECORRELATE, false); // 配置不强制去相关化，保持原始查询结构，避免不必要的子查询展开
  } // 方法结束，返回配置好的测试器实例
} // 类定义结束，EnumerableUncollectTest类用于全面测试EnumerableUncollect操作符的各种使用场景
