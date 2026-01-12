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
package org.apache.calcite.linq4j.test; // 包声明：LINQ4J 测试类的包路径

// 导入 LINQ4J 核心类
import org.apache.calcite.linq4j.AbstractEnumerable; // 抽象可枚举类，用于实现自定义的可枚举集合
import org.apache.calcite.linq4j.Enumerable; // 可枚举接口，定义了 LINQ 风格的查询操作
import org.apache.calcite.linq4j.EnumerableDefaults; // Enumerable 接口的默认实现类
import org.apache.calcite.linq4j.Enumerator; // 枚举器接口，用于遍历可枚举集合
import org.apache.calcite.linq4j.ExtendedEnumerable; // 扩展的可枚举接口，提供额外的查询操作
import org.apache.calcite.linq4j.Grouping; // 分组接口，表示分组后的结果
import org.apache.calcite.linq4j.Linq4j; // LINQ4J 的主工具类，提供静态工厂方法
import org.apache.calcite.linq4j.Lookup; // 查找表接口，用于快速查找元素
import org.apache.calcite.linq4j.Queryable; // 可查询接口，支持表达式树的查询
import org.apache.calcite.linq4j.QueryableDefaults; // Queryable 接口的默认实现类

// 导入函数式接口
import org.apache.calcite.linq4j.function.EqualityComparer; // 相等比较器接口，用于自定义相等比较逻辑
import org.apache.calcite.linq4j.function.Function0; // 无参数函数接口
import org.apache.calcite.linq4j.function.Function1; // 单参数函数接口
import org.apache.calcite.linq4j.function.Function2; // 双参数函数接口
import org.apache.calcite.linq4j.function.Functions; // 函数工具类，提供常用的函数实现
import org.apache.calcite.linq4j.function.IntegerFunction1; // 返回整数的单参数函数接口
import org.apache.calcite.linq4j.function.Predicate1; // 单参数谓词接口
import org.apache.calcite.linq4j.function.Predicate2; // 双参数谓词接口

// 导入表达式树相关类
import org.apache.calcite.linq4j.tree.ConstantExpression; // 常量表达式类
import org.apache.calcite.linq4j.tree.Expressions; // 表达式工具类，用于创建表达式树
import org.apache.calcite.linq4j.tree.ParameterExpression; // 参数表达式类

// 导入第三方库
import com.example.Linq4jExample; // LINQ4J 示例类
import com.google.common.collect.ImmutableList; // Google Guava 的不可变列表类
import com.google.common.collect.Lists; // Google Guava 的列表工具类

// 导入测试框架
import org.junit.jupiter.api.Test; // JUnit 5 测试注解

// 导入 Java 标准库
import java.math.BigDecimal; // 大数类，用于精确的浮点数表示
import java.util.ArrayList; // 动态数组列表类
import java.util.Arrays; // 数组工具类
import java.util.Collections; // 集合工具类
import java.util.Comparator; // 比较器接口
import java.util.HashMap; // 哈希映射类
import java.util.Iterator; // 迭代器接口
import java.util.List; // 列表接口
import java.util.Locale; // 本地化类
import java.util.Map; // 映射接口
import java.util.NoSuchElementException; // 无此元素异常类
import java.util.Objects; // 对象工具类
import java.util.TreeSet; // 树集合类

// 导入断言相关的静态方法
import static org.hamcrest.CoreMatchers.endsWith; // 断言以指定字符串结尾
import static org.hamcrest.CoreMatchers.equalTo; // 断言相等
import static org.hamcrest.CoreMatchers.is; // 断言是
import static org.hamcrest.CoreMatchers.not; // 断言不是
import static org.hamcrest.CoreMatchers.nullValue; // 断言为 null
import static org.hamcrest.CoreMatchers.sameInstance; // 断言是同一实例
import static org.hamcrest.MatcherAssert.assertThat; // 断言方法
import static org.hamcrest.Matchers.aMapWithSize; // 断言映射具有指定大小
import static org.hamcrest.Matchers.empty; // 断言为空
import static org.hamcrest.Matchers.hasSize; // 断言具有指定大小
import static org.hamcrest.Matchers.hasToString; // 断言具有指定的字符串表示

// 导入 JUnit 断言相关的静态方法
import static org.junit.jupiter.api.Assertions.assertFalse; // 断言为假
import static org.junit.jupiter.api.Assertions.assertNotEquals; // 断言不相等
import static org.junit.jupiter.api.Assertions.assertNotNull; // 断言不为 null
import static org.junit.jupiter.api.Assertions.assertNull; // 断言为 null
import static org.junit.jupiter.api.Assertions.assertTrue; // 断言为真
import static org.junit.jupiter.api.Assertions.fail; // 测试失败方法

/**
 * LINQ4J（Language Integrated Query for Java）框架的综合测试类
 * 
 * 这个类包含了大量的测试方法，用于测试 LINQ4J 框架提供的各种功能。
 * LINQ4J 是 Apache Calcite 项目中实现的语言集成查询（LINQ）风格的 Java 库，
 * 它提供了一套类似于 .NET LINQ 的查询操作符和方法，用于对集合进行声明式查询和操作。
 * 
 * 主要测试的功能包括：
 * 1. 基本查询操作：select、where、selectMany 等
 * 2. 聚合操作：count、sum、average、min、max、aggregate 等
 * 3. 元素操作：first、firstOrDefault、last、lastOrDefault、single、singleOrDefault 等
 * 4. 集合操作：union、intersect、except、concat、distinct 等
 * 5. 分组操作：groupBy、groupJoin 等
 * 6. 连接操作：join、hashJoin、leftJoin、rightJoin、fullJoin 等
 * 7. 排序操作：orderBy、orderByDescending、reverse 等
 * 8. 分页操作：take、skip、takeWhile、skipWhile 等
 * 9. 转换操作：toMap、toLookup、cast、ofType、zip 等
 * 10. 查询表达式：asQueryable、whereN 等
 * 11. 特殊操作：cartesianProduct、asofJoin 等
 * 
 * 该测试类使用了 Employee 和 Department 两个内部类作为测试数据模型，
 * 模拟了员工和部门的关系，用于测试各种查询场景。
 * 
 * @see org.apache.calcite.linq4j.Linq4j
 * @see org.apache.calcite.linq4j.Enumerable
 */
@SuppressWarnings({"resource", "ArraysAsListWithZeroOrOneArgument"})
public class Linq4jTest {
  // 员工名称选择器：从 Employee 对象中提取员工姓名
  public static final Function1<Employee, String> EMP_NAME_SELECTOR = employee -> employee.name; // Lambda 表达式：接收 Employee 对象，返回其 name 字段

  // 员工部门号选择器：从 Employee 对象中提取部门编号
  public static final Function1<Employee, Integer> EMP_DEPTNO_SELECTOR =
      employee -> employee.deptno; // Lambda 表达式：接收 Employee 对象，返回其 deptno 字段

  // 员工工号选择器：从 Employee 对象中提取员工编号
  public static final Function1<Employee, Integer> EMP_EMPNO_SELECTOR = employee -> employee.empno; // Lambda 表达式：接收 Employee 对象，返回其 empno 字段

  // 部门员工选择器：从 Department 对象中提取员工列表，并转换为可枚举集合
  public static final Function1<Department, Enumerable<Employee>> DEPT_EMPLOYEES_SELECTOR =
      a0 -> Linq4j.asEnumerable(a0.employees); // Lambda 表达式：接收 Department 对象，将其 employees 列表转换为 Enumerable<Employee>

  // 部门名称选择器：从 Department 对象中提取部门名称
  public static final Function1<Department, String> DEPT_NAME_SELECTOR =
      department -> department.name; // Lambda 表达式：接收 Department 对象，返回其 name 字段

  // 部门编号选择器：从 Department 对象中提取部门编号
  public static final Function1<Department, Integer> DEPT_DEPTNO_SELECTOR =
      department -> department.deptno; // Lambda 表达式：接收 Department 对象，返回其 deptno 字段

  // 部门编号选择器2：从 Department 对象中提取部门编号（使用 IntegerFunction1 接口）
  public static final IntegerFunction1<Department> DEPT_DEPTNO_SELECTOR2 =
      department -> department.deptno; // Lambda 表达式：接收 Department 对象，返回其 deptno 字段，类型为 IntegerFunction1

  // 常量选择器：总是返回 1，用于笛卡尔积等操作
  public static final Function1<Object, Integer> ONE_SELECTOR = employee -> 1; // Lambda 表达式：忽略输入参数，总是返回整数 1

  // 配对选择器：用于笛卡尔积，总是返回 1
  private static final Function2<Object, Object, Integer> PAIR_SELECTOR = (employee, v2) -> 1; // Lambda 表达式：接收两个参数，忽略它们，总是返回整数 1

  @Test void testSelect() { // 测试 select 操作：从集合中选择特定字段
    List<String> names = // 创建一个字符串列表，用于存储员工姓名
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .select(EMP_NAME_SELECTOR) // 使用选择器提取每个员工的姓名
            .toList(); // 将结果转换为列表
    assertThat(names, hasToString("[Fred, Bill, Eric, Janet]")); // 断言结果列表包含所有员工姓名
  }

  @Test void testWhere() { // 测试 where 操作：根据条件过滤集合
    List<String> names = // 创建一个字符串列表，用于存储符合条件的员工姓名
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .where(employee -> employee.deptno < 15) // 过滤出部门编号小于 15 的员工
            .select(EMP_NAME_SELECTOR) // 提取过滤后员工的姓名
            .toList(); // 将结果转换为列表
    assertThat(names, hasToString("[Fred, Eric, Janet]")); // 断言结果只包含部门 10 的员工
  }

  @Test void testWhereIndexed() { // 测试带索引的 where 操作：根据元素索引过滤集合
    // Returns every other employee. // 返回每隔一个的员工（索引为偶数的元素）
    List<String> names = // 创建一个字符串列表，用于存储符合条件的员工姓名
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .where((employee, n) -> n % 2 == 0) // 过滤出索引为偶数的员工（0, 2, 4...）
            .select(EMP_NAME_SELECTOR) // 提取过滤后员工的姓名
            .toList(); // 将结果转换为列表
    assertThat(names, hasToString("[Fred, Eric]")); // 断言结果只包含索引为 0 和 2 的员工
  }

  @Test void testSelectMany() { // 测试 selectMany 操作：将嵌套集合展平
    final List<String> nameSeqs = // 创建一个字符串列表，用于存储带序号的员工姓名
        Linq4j.asEnumerable(depts) // 将部门数组转换为可枚举集合
            .selectMany(DEPT_EMPLOYEES_SELECTOR) // 对每个部门，提取其员工列表并展平
            .select((v1, v2) -> "#" + v2 + ": " + v1.name) // 为每个员工添加序号（使用索引）
            .toList(); // 将结果转换为列表
    assertThat(nameSeqs, // 断言结果列表包含所有员工及其序号
        hasToString("[#0: Fred, #1: Eric, #2: Janet, #3: Bill]"));
  }

  @Test void testCount() { // 测试 count 操作：计算集合中的元素数量
    final int count = // 创建一个整数变量，用于存储部门数量
        Linq4j.asEnumerable(depts).count(); // 计算部门数组中的元素数量
    assertThat(count, is(3)); // 断言部门数量为 3
  }

  @Test void testCountPredicate() { // 测试带谓词的 count 操作：计算满足条件的元素数量
    final int count = // 创建一个整数变量，用于存储符合条件的部门数量
        Linq4j.asEnumerable(depts).count(v1 -> !v1.employees.isEmpty()); // 计算有员工的部门数量
    assertThat(count, is(2)); // 断言有员工的部门数量为 2（Sales 和 Marketing）
  }

  @Test void testLongCount() { // 测试 longCount 操作：计算集合中的元素数量（返回 long 类型）
    final long count = // 创建一个长整型变量，用于存储部门数量
        Linq4j.asEnumerable(depts).longCount(); // 计算部门数组中的元素数量（返回 long）
    assertThat(count, is(3L)); // 断言部门数量为 3（long 类型）
  }

  @Test void testLongCountPredicate() { // 测试带谓词的 longCount 操作：计算满足条件的元素数量（返回 long 类型）
    final long count = // 创建一个长整型变量，用于存储符合条件的部门数量
        Linq4j.asEnumerable(depts).longCount(v1 -> !v1.employees.isEmpty()); // 计算有员工的部门数量（返回 long）
    assertThat(count, is(2L)); // 断言有员工的部门数量为 2（long 类型）
  }

  @Test void testAllPredicate() { // 测试 all 操作：检查是否所有元素都满足条件
    Predicate1<Employee> allEmpnoGE100 = emp -> emp.empno >= 100; // 创建谓词：员工编号大于等于 100

    Predicate1<Employee> allEmpnoGT100 = emp -> emp.empno > 100; // 创建谓词：员工编号大于 100

    assertTrue(Linq4j.asEnumerable(emps).all(allEmpnoGE100)); // 断言所有员工编号都大于等于 100
    assertFalse(Linq4j.asEnumerable(emps).all(allEmpnoGT100)); // 断言并非所有员工编号都大于 100（有员工编号等于 100）
  }

  @Test void testAny() { // 测试 any 操作：检查集合是否包含任何元素
    List<Employee> emptyList = Collections.emptyList(); // 创建一个空列表
    assertFalse(Linq4j.asEnumerable(emptyList).any()); // 断言空列表不包含任何元素
    assertTrue(Linq4j.asEnumerable(emps).any()); // 断言员工数组包含元素
  }

  @Test void testAnyPredicate() { // 测试带谓词的 any 操作：检查是否有任何元素满足条件
    Predicate1<Department> deptoNameIT = v1 -> v1.name != null && v1.name.equals("IT"); // 创建谓词：部门名称为 "IT"

    Predicate1<Department> deptoNameSales = v1 -> v1.name != null && v1.name.equals("Sales"); // 创建谓词：部门名称为 "Sales"

    assertFalse(Linq4j.asEnumerable(depts).any(deptoNameIT)); // 断言没有部门名称为 "IT"
    assertTrue(Linq4j.asEnumerable(depts).any(deptoNameSales)); // 断言有部门名称为 "Sales"
  }

  @Test void testAverageSelector() { // 测试 average 操作：计算元素的平均值
    assertThat(Linq4j.asEnumerable(depts).average(DEPT_DEPTNO_SELECTOR2), is(20)); // 断言部门编号的平均值为 20（(10+20+30)/3）
  }

  @Test void testMin() { // 测试 min 操作：计算集合中的最小值
    assertThat((int) Linq4j.asEnumerable(depts).select(DEPT_DEPTNO_SELECTOR) // 先选择部门编号
            .min(), is(10)); // 然后计算最小值，断言为 10
  }

  @Test void testMinSelector() { // 测试带选择器的 min 操作：使用选择器计算最小值
    assertThat((int) Linq4j.asEnumerable(depts).min(DEPT_DEPTNO_SELECTOR), is(10)); // 使用选择器提取部门编号并计算最小值
  }

  @Test void testMinSelector2() { // 测试带选择器2的 min 操作：使用 IntegerFunction1 选择器计算最小值
    assertThat(Linq4j.asEnumerable(depts).min(DEPT_DEPTNO_SELECTOR2), is(10)); // 使用选择器提取部门编号并计算最小值
  }

  @Test void testMax() { // 测试 max 操作：计算集合中的最大值
    assertThat((int) Linq4j.asEnumerable(depts).select(DEPT_DEPTNO_SELECTOR) // 先选择部门编号
            .max(), is(30)); // 然后计算最大值，断言为 30
  }

  @Test void testMaxSelector() { // 测试带选择器的 max 操作：使用选择器计算最大值
    assertThat((int) Linq4j.asEnumerable(depts).max(DEPT_DEPTNO_SELECTOR), is(30)); // 使用选择器提取部门编号并计算最大值
  }

  @Test void testMaxSelector2() { // 测试带选择器2的 max 操作：使用 IntegerFunction1 选择器计算最大值
    assertThat(Linq4j.asEnumerable(depts).max(DEPT_DEPTNO_SELECTOR2), is(30)); // 使用选择器提取部门编号并计算最大值
  }

  @Test void testAggregate() { // 测试 aggregate 操作：对集合进行聚合计算
    assertThat(Linq4j.asEnumerable(depts) // 将部门数组转换为可枚举集合
            .select(DEPT_NAME_SELECTOR) // 选择部门名称
            .aggregate(null, // 初始值为 null
                (v1, v2) -> v1 == null ? v2 : v1 + "," + v2), // 聚合函数：将部门名称用逗号连接
        is("Sales,HR,Marketing")); // 断言聚合结果为部门名称列表
  }

  @Test void testToMap() { // 测试 toMap 操作：将集合转换为 Map
    final Map<Integer, Employee> map = // 创建一个 Map，键为员工编号，值为员工对象
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .toMap(EMP_EMPNO_SELECTOR); // 使用员工编号作为键
    assertThat(map, aMapWithSize(4)); // 断言 Map 包含 4 个条目
    assertThat(map.get(110).name, is("Bill")); // 断言员工编号 110 对应的员工名称为 "Bill"
  }

  @Test void testToMapWithComparer() { // 测试带比较器的 toMap 操作：使用自定义比较器创建 Map
    final Map<String, String> map = // 创建一个 Map，键为字符串，值为字符串
        Linq4j.asEnumerable(Arrays.asList("foo", "bar", "far")) // 创建字符串列表
            .toMap(Functions.identitySelector(), // 使用字符串本身作为键
                new EqualityComparer<String>() { // 创建自定义比较器（不区分大小写）
                  public boolean equal(String v1, String v2) { // 相等比较方法
                    return String.CASE_INSENSITIVE_ORDER.compare(v1, v2) == 0; // 不区分大小写比较
                  }
                  public int hashCode(String s) { // 哈希码计算方法
                    return s.toLowerCase(Locale.ROOT).hashCode(); // 使用小写形式计算哈希码
                  }
                });
    assertThat(map, aMapWithSize(3)); // 断言 Map 包含 3 个条目
    assertThat(map.get("foo"), is("foo")); // 断言 "foo" 对应的值为 "foo"
    assertThat(map.get("Foo"), is("foo")); // 断言 "Foo" 也对应 "foo"（不区分大小写）
    assertThat(map.get("FOO"), is("foo")); // 断言 "FOO" 也对应 "foo"（不区分大小写）
  }

  @Test void testToMap2() { // 测试 toMap2 操作：将集合转换为 Map（带值选择器）
    final Map<Integer, Integer> map = // 创建一个 Map，键为员工编号，值为部门编号
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .toMap(EMP_EMPNO_SELECTOR, EMP_DEPTNO_SELECTOR); // 使用员工编号作为键，部门编号作为值
    assertThat(map, aMapWithSize(4)); // 断言 Map 包含 4 个条目
    assertThat(map.get(110), is(30)); // 断言员工编号 110 对应的部门编号为 30
  }

  @Test void testToMap2WithComparer() { // 测试带比较器的 toMap2 操作：使用自定义比较器创建 Map（带值选择器）
    final Map<String, String> map = // 创建一个 Map，键为字符串，值为字符串（大写）
        Linq4j.asEnumerable(Arrays.asList("foo", "bar", "far")) // 创建字符串列表
            .toMap(Functions.identitySelector(), // 使用字符串本身作为键
                x -> x == null ? null : x.toUpperCase(Locale.ROOT), // 将值转换为大写
                new EqualityComparer<String>() { // 创建自定义比较器（不区分大小写）
                  public boolean equal(String v1, String v2) { // 相等比较方法
                    return String.CASE_INSENSITIVE_ORDER.compare(v1, v2) == 0; // 不区分大小写比较
                  }
                  public int hashCode(String s) { // 哈希码计算方法
                    return s.toLowerCase(Locale.ROOT).hashCode(); // 使用小写形式计算哈希码
                  }
                });
    assertThat(map, aMapWithSize(3)); // 断言 Map 包含 3 个条目
    assertThat(map.get("foo"), is("FOO")); // 断言 "foo" 对应的值为 "FOO"
    assertThat(map.get("Foo"), is("FOO")); // 断言 "Foo" 也对应 "FOO"（不区分大小写）
    assertThat(map.get("FOO"), is("FOO")); // 断言 "FOO" 也对应 "FOO"
  }

  @Test void testToLookup() { // 测试 toLookup 操作：将集合转换为 Lookup（查找表）
    final Lookup<Integer, Employee> lookup = // 创建一个查找表，键为部门编号，值为员工列表
        Linq4j.asEnumerable(emps).toLookup( // 将员工数组转换为可枚举集合
            EMP_DEPTNO_SELECTOR); // 使用部门编号作为键
    int n = 0; // 初始化计数器
    for (Grouping<Integer, Employee> grouping : lookup) { // 遍历查找表中的每个分组
      ++n; // 递增计数器
      switch (grouping.getKey()) { // 根据分组键（部门编号）进行处理
      case 10: // 部门 10
        assertThat(grouping.count(), is(3)); // 断言该分组包含 3 个员工
        break;
      case 30: // 部门 30
        assertThat(grouping.count(), is(1)); // 断言该分组包含 1 个员工
        break;
      default: // 其他部门
        fail("unknown department number " + grouping); // 测试失败
      }
    }
    assertThat(n, is(2)); // 断言有 2 个分组（部门 10 和 30）
  }

  @Test void testToLookupSelector() { // 测试带选择器的 toLookup 操作：将集合转换为 Lookup（带元素选择器）
    final Lookup<Integer, String> lookup = // 创建一个查找表，键为部门编号，值为员工姓名列表
        Linq4j.asEnumerable(emps).toLookup( // 将员工数组转换为可枚举集合
            EMP_DEPTNO_SELECTOR, // 使用部门编号作为键
            EMP_NAME_SELECTOR); // 使用员工姓名作为值
    int n = 0; // 初始化计数器
    for (Grouping<Integer, String> grouping : lookup) { // 遍历查找表中的每个分组
      ++n; // 递增计数器
      switch (grouping.getKey()) { // 根据分组键（部门编号）进行处理
      case 10: // 部门 10
        assertThat(grouping.count(), is(3)); // 断言该分组包含 3 个员工姓名
        assertTrue(grouping.contains("Fred")); // 断言分组包含 "Fred"
        assertTrue(grouping.contains("Eric")); // 断言分组包含 "Eric"
        assertTrue(grouping.contains("Janet")); // 断言分组包含 "Janet"
        assertFalse(grouping.contains("Bill")); // 断言分组不包含 "Bill"
        break;
      case 30: // 部门 30
        assertThat(grouping.count(), is(1)); // 断言该分组包含 1 个员工姓名
        assertTrue(grouping.contains("Bill")); // 断言分组包含 "Bill"
        assertFalse(grouping.contains("Fred")); // 断言分组不包含 "Fred"
        break;
      default: // 其他部门
        fail("unknown department number " + grouping); // 测试失败
      }
    }
    assertThat(n, is(2)); // 断言有 2 个分组（部门 10 和 30）

    assertThat(lookup.applyResultSelector((v1, v2) -> v1 + ":" + v2.count()) // 应用结果选择器，格式化为 "部门编号:员工数量"
            .orderBy(Functions.identitySelector()) // 按部门编号排序
            .toList(), // 转换为列表
        hasToString("[10:3, 30:1]")); // 断言结果为部门编号和员工数量的映射
  }

  @Test void testContains() { // 测试 contains 操作：检查集合是否包含指定元素
    Employee e = emps[1]; // 获取第二个员工
    Employee employeeClone = new Employee(e.empno, e.name, e.deptno); // 创建一个员工克隆对象（相同属性）
    Employee employeeOther = badEmps[0]; // 获取一个不存在的员工

    assertThat(employeeClone, is(e)); // 断言克隆对象与原对象相等
    assertTrue(Linq4j.asEnumerable(emps).contains(e)); // 断言集合包含原员工对象
    assertTrue(Linq4j.asEnumerable(emps).contains(employeeClone)); // 断言集合包含克隆员工对象（因为 equals 方法比较所有字段）
    assertFalse(Linq4j.asEnumerable(emps).contains(employeeOther)); // 断言集合不包含不存在的员工

  }

  @Test void testContainsWithEqualityComparer() { // 测试带比较器的 contains 操作：使用自定义比较器检查集合是否包含指定元素
    EqualityComparer<Employee> compareByEmpno = // 创建一个基于员工编号的比较器
        new EqualityComparer<Employee>() {
          public boolean equal(Employee e1, Employee e2) { // 相等比较方法
            return e1.empno == e2.empno; // 只比较员工编号
          }

          public int hashCode(Employee t) { // 哈希码计算方法
            return t.hashCode(); // 使用默认哈希码
          }
        };

    Employee e = emps[1]; // 获取第二个员工
    Employee employeeClone = new Employee(e.empno, e.name, e.deptno); // 创建一个员工克隆对象
    Employee employeeOther = badEmps[0]; // 获取一个不存在的员工

    assertThat(employeeClone, is(e)); // 断言克隆对象与原对象相等
    assertTrue(Linq4j.asEnumerable(emps) // 断言集合包含原员工对象（使用自定义比较器）
        .contains(e, compareByEmpno));
    assertTrue(Linq4j.asEnumerable(emps) // 断言集合包含克隆员工对象（因为员工编号相同）
        .contains(employeeClone, compareByEmpno));
    assertFalse(Linq4j.asEnumerable(emps) // 断言集合不包含不存在的员工（员工编号不同）
        .contains(employeeOther, compareByEmpno));

  }

  @Test void testFirst() { // 测试 first 操作：获取集合的第一个元素
    Employee e = emps[0]; // 获取第一个员工
    assertThat(emps[0], is(e)); // 断言数组第一个元素是 e
    assertThat(Linq4j.asEnumerable(emps).first(), is(e)); // 断言集合的第一个元素是 e

    Department d = depts[0]; // 获取第一个部门
    assertThat(depts[0], is(d)); // 断言数组第一个元素是 d
    assertThat(Linq4j.asEnumerable(depts).first(), is(d)); // 断言集合的第一个元素是 d

    try { // 测试空集合的情况
      String s = Linq4j.<String>emptyEnumerable().first(); // 尝试获取空集合的第一个元素
      fail("expected exception, got " + s); // 断言失败，应该抛出异常
    } catch (NoSuchElementException ex) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }

    // close occurs if first throws // 测试枚举器在抛出异常时是否正确关闭
    final int[] closeCount = {0}; // 创建一个计数器数组，用于跟踪关闭次数
    try { // 测试空枚举器的情况
      String s = myEnumerable(closeCount, 0).first(); // 尝试获取空枚举器的第一个元素
      fail("expected exception, got " + s); // 断言失败，应该抛出异常
    } catch (NoSuchElementException ex) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }
    assertThat(closeCount[0], equalTo(1)); // 断言枚举器被关闭了一次

    // close occurs if first does not throw // 测试枚举器在成功获取元素时是否正确关闭
    closeCount[0] = 0; // 重置计数器
    final String s = myEnumerable(closeCount, 1).first(); // 获取非空枚举器的第一个元素
    assertThat(s, equalTo("x")); // 断言获取的元素是 "x"
    assertThat(closeCount[0], equalTo(1)); // 断言枚举器被关闭了一次
  }

  private Enumerable<String> myEnumerable(final int[] closes, final int size) { // 创建一个自定义的可枚举集合，用于测试枚举器的关闭行为
    return new AbstractEnumerable<String>() { // 返回一个抽象可枚举集合的匿名子类
      public Enumerator<String> enumerator() { // 创建枚举器
        return new Enumerator<String>() { // 返回一个枚举器的匿名子类
          int i = 0; // 初始化索引

          public String current() { // 获取当前元素
            return "x"; // 总是返回 "x"
          }

          public boolean moveNext() { // 移动到下一个元素
            return i++ < size; // 如果索引小于大小，返回 true，否则返回 false
          }

          public void reset() { // 重置枚举器
          } // 空实现

          public void close() { // 关闭枚举器
            ++closes[0]; // 递增关闭计数器
          }
        };
      }
    };
  }

  @Test void testFirstPredicate1() { // 测试带谓词的 first 操作：获取集合中第一个满足条件的元素
    Predicate1<String> startWithS = s -> s != null && Character.toString(s.charAt(0)).equals("S"); // 创建谓词：字符串以 'S' 开头

    Predicate1<Integer> numberGT15 = i -> i > 15; // 创建谓词：数字大于 15

    String[] people = {"Brill", "Smith", "Simpsom"}; // 创建人员数组
    String[] peopleWithoutCharS = {"Brill", "Andrew", "Alice"}; // 创建不包含以 'S' 开头的人员数组
    Integer[] numbers = {5, 10, 15, 20, 25}; // 创建数字数组

    assertThat(Linq4j.asEnumerable(people).first(startWithS), is(people[1])); // 断言第一个以 'S' 开头的人员是 "Smith"
    assertThat(Linq4j.asEnumerable(numbers).first(numberGT15), is(numbers[3])); // 断言第一个大于 15 的数字是 20

    try { // 测试没有元素满足条件的情况
      String s = Linq4j.asEnumerable(peopleWithoutCharS).first(startWithS); // 尝试获取第一个以 'S' 开头的人员
      fail("expected exception, but got" + s); // 断言失败，应该抛出异常
    } catch (NoSuchElementException e) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }
  }

  @Test void testFirstOrDefault() { // 测试 firstOrDefault 操作：获取集合的第一个元素，如果集合为空则返回默认值

    String[] people = {"Brill", "Smith", "Simpsom"}; // 创建人员数组
    String[] empty = {}; // 创建空数组
    Integer[] numbers = {5, 10, 15, 20, 25}; // 创建数字数组

    assertThat(Linq4j.asEnumerable(people).firstOrDefault(), is(people[0])); // 断言第一个元素是 "Brill"
    assertThat(Linq4j.asEnumerable(numbers).firstOrDefault(), is(numbers[0])); // 断言第一个元素是 5

    assertNull(Linq4j.asEnumerable(empty).firstOrDefault()); // 断言空数组返回 null
  }

  @Test void testFirstOrDefaultPredicate1() { // 测试带谓词的 firstOrDefault 操作：获取集合中第一个满足条件的元素，如果没有则返回默认值
    Predicate1<String> startWithS = s -> s != null && Character.toString(s.charAt(0)).equals("S"); // 创建谓词：字符串以 'S' 开头

    Predicate1<Integer> numberGT15 = i -> i > 15; // 创建谓词：数字大于 15

    String[] people = {"Brill", "Smith", "Simpsom"}; // 创建人员数组
    String[] peopleWithoutCharS = {"Brill", "Andrew", "Alice"}; // 创建不包含以 'S' 开头的人员数组
    Integer[] numbers = {5, 10, 15, 20, 25}; // 创建数字数组

    assertThat(Linq4j.asEnumerable(people) // 断言第一个以 'S' 开头的人员是 "Smith"
          .firstOrDefault(startWithS), is(people[1]));
    assertThat(Linq4j.asEnumerable(numbers) // 断言第一个大于 15 的数字是 20
        .firstOrDefault(numberGT15), is(numbers[3]));

    assertNull(Linq4j.asEnumerable(peopleWithoutCharS) // 断言没有以 'S' 开头的人员时返回 null
        .firstOrDefault(startWithS));
  }

  @Test void testSingle() { // 测试 single 操作：获取集合中唯一的元素，如果集合不只有一个元素则抛出异常

    String[] person = {"Smith"}; // 创建只有一个元素的数组
    String[] people = {"Brill", "Smith", "Simpson"}; // 创建有多个元素的数组
    Integer[] number = {20}; // 创建只有一个元素的数组
    Integer[] numbers = {5, 10, 15, 20}; // 创建有多个元素的数组

    assertThat(Linq4j.asEnumerable(person).single(), is(person[0])); // 断言唯一元素是 "Smith"
    assertThat(Linq4j.asEnumerable(number).single(), is(number[0])); // 断言唯一元素是 20

    try { // 测试集合有多个元素的情况
      String s = Linq4j.asEnumerable(people).single(); // 尝试获取唯一元素
      fail("expected exception, but got" + s); // 断言失败，应该抛出异常
    } catch (IllegalStateException e) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }

    try { // 测试集合有多个元素的情况
      int i = Linq4j.asEnumerable(numbers).single(); // 尝试获取唯一元素
      fail("expected exception, but got" + i); // 断言失败，应该抛出异常
    } catch (IllegalStateException e) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }
  }

  @Test void testSingleOrDefault() { // 测试 singleOrDefault 操作：获取集合中唯一的元素，如果集合为空或有多个元素则返回默认值

    String[] person = {"Smith"}; // 创建只有一个元素的数组
    String[] people = {"Brill", "Smith", "Simpson"}; // 创建有多个元素的数组
    Integer[] number = {20}; // 创建只有一个元素的数组
    Integer[] numbers = {5, 10, 15, 20}; // 创建有多个元素的数组

    assertThat(Linq4j.asEnumerable(person).singleOrDefault(), is(person[0])); // 断言唯一元素是 "Smith"
    assertThat(Linq4j.asEnumerable(number).singleOrDefault(), is(number[0])); // 断言唯一元素是 20

    assertNull(Linq4j.asEnumerable(people).singleOrDefault()); // 断言有多个元素时返回 null
    assertNull(Linq4j.asEnumerable(numbers).singleOrDefault()); // 断言有多个元素时返回 null
  }

  @Test void testSinglePredicate1() { // 测试带谓词的 single 操作：获取集合中唯一满足条件的元素
    Predicate1<String> startWithS = s -> s != null && Character.toString(s.charAt(0)).equals("S"); // 创建谓词：字符串以 'S' 开头

    Predicate1<Integer> numberGT15 = i -> i > 15; // 创建谓词：数字大于 15

    String[] people = {"Brill", "Smith"}; // 创建人员数组（只有一个以 'S' 开头）
    String[] twoPeopleWithCharS = {"Brill", "Smith", "Simpson"}; // 创建人员数组（有两个以 'S' 开头）
    String[] peopleWithoutCharS = {"Brill", "Andrew", "Alice"}; // 创建人员数组（没有以 'S' 开头）
    Integer[] numbers = {5, 10, 15, 20}; // 创建数字数组（只有一个大于 15）
    Integer[] numbersWithoutGT15 = {5, 10, 15}; // 创建数字数组（没有大于 15）
    Integer[] numbersWithTwoGT15 = {5, 10, 15, 20, 25}; // 创建数字数组（有两个大于 15）

    assertThat(Linq4j.asEnumerable(people).single(startWithS), is(people[1])); // 断言唯一以 'S' 开头的人员是 "Smith"
    assertThat(Linq4j.asEnumerable(numbers).single(numberGT15), is(numbers[3])); // 断言唯一大于 15 的数字是 20


    try { // 测试有多个元素满足条件的情况
      String s = Linq4j.asEnumerable(twoPeopleWithCharS).single(startWithS); // 尝试获取唯一以 'S' 开头的人员
      fail("expected exception, but got" + s); // 断言失败，应该抛出异常
    } catch (IllegalStateException e) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }

    try { // 测试有多个元素满足条件的情况
      int i = Linq4j.asEnumerable(numbersWithTwoGT15).single(numberGT15); // 尝试获取唯一大于 15 的数字
      fail("expected exception, but got" + i); // 断言失败，应该抛出异常
    } catch (IllegalStateException e) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }

    try { // 测试没有元素满足条件的情况
      String s = Linq4j.asEnumerable(peopleWithoutCharS).single(startWithS); // 尝试获取唯一以 'S' 开头的人员
      fail("expected exception, but got" + s); // 断言失败，应该抛出异常
    } catch (IllegalStateException e) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }

    try { // 测试没有元素满足条件的情况
      int i = Linq4j.asEnumerable(numbersWithoutGT15).single(numberGT15); // 尝试获取唯一大于 15 的数字
      fail("expected exception, but got" + i); // 断言失败，应该抛出异常
    } catch (IllegalStateException e) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }
  }

  @Test void testSingleOrDefaultPredicate1() { // 测试带谓词的 singleOrDefault 操作：获取集合中唯一满足条件的元素，如果没有或有多个则返回默认值
    Predicate1<String> startWithS = s -> s != null && Character.toString(s.charAt(0)).equals("S"); // 创建谓词：字符串以 'S' 开头

    Predicate1<Integer> numberGT15 = i -> i > 15; // 创建谓词：数字大于 15

    String[] people = {"Brill", "Smith"}; // 创建人员数组（只有一个以 'S' 开头）
    String[] twoPeopleWithCharS = {"Brill", "Smith", "Simpson"}; // 创建人员数组（有两个以 'S' 开头）
    String[] peopleWithoutCharS = {"Brill", "Andrew", "Alice"}; // 创建人员数组（没有以 'S' 开头）
    Integer[] numbers = {5, 10, 15, 20}; // 创建数字数组（只有一个大于 15）
    Integer[] numbersWithTwoGT15 = {5, 10, 15, 20, 25}; // 创建数字数组（有两个大于 15）
    Integer[] numbersWithoutGT15 = {5, 10, 15}; // 创建数字数组（没有大于 15）

    assertThat(Linq4j.asEnumerable(people) // 断言唯一以 'S' 开头的人员是 "Smith"
          .singleOrDefault(startWithS), is(people[1]));

    assertThat(Linq4j.asEnumerable(numbers) // 断言唯一大于 15 的数字是 20
          .singleOrDefault(numberGT15), is(numbers[3]));

    assertNull(Linq4j.asEnumerable(twoPeopleWithCharS) // 断言有两个以 'S' 开头时返回 null
        .singleOrDefault(startWithS));

    assertNull(Linq4j.asEnumerable(numbersWithTwoGT15) // 断言有两个大于 15 时返回 null
        .singleOrDefault(numberGT15));

    assertNull(Linq4j.asEnumerable(peopleWithoutCharS) // 断言没有以 'S' 开头时返回 null
        .singleOrDefault(startWithS));

    assertNull(Linq4j.asEnumerable(numbersWithoutGT15) // 断言没有大于 15 时返回 null
        .singleOrDefault(numberGT15));
  }

  @SuppressWarnings("UnnecessaryBoxing")
  @Test void testIdentityEqualityComparer() { // 测试身份相等比较器：使用对象的 equals 方法进行比较
    final Integer one = 1000; // 创建一个 Integer 对象
    final Integer one2 = Integer.valueOf(one.toString()); // 创建另一个 Integer 对象，值相同但不是同一个实例
    assertThat(one, not(sameInstance(one2))); // 断言两个对象不是同一个实例
    final Integer two = 2; // 创建另一个 Integer 对象，值不同
    final EqualityComparer<Integer> idComparer = Functions.identityComparer(); // 获取身份相等比较器
    assertTrue(idComparer.equal(one, one)); // 断言对象与自身相等
    assertTrue(idComparer.equal(one, one2)); // 断言值相同的对象相等（使用 equals 方法）
    assertFalse(idComparer.equal(one, two)); // 断言值不同的对象不相等
  }

  @Test void testSelectorEqualityComparer() { // 测试选择器相等比较器：使用选择器提取键值进行比较
    final EqualityComparer<Employee> comparer = // 创建一个基于部门编号的比较器
        Functions.selectorComparer((Function1<Employee, Object>) a0 -> a0.deptno); // 使用部门编号作为比较键
    assertTrue(comparer.equal(emps[0], emps[0])); // 断言员工与自身相等
    assertThat(comparer.hashCode(emps[0]), is(comparer.hashCode(emps[0]))); // 断言哈希码一致

    assertTrue(comparer.equal(emps[0], emps[2])); // 断言部门编号相同的员工相等
    assertThat(comparer.hashCode(emps[2]), is(comparer.hashCode(emps[0]))); // 断言哈希码一致

    assertFalse(comparer.equal(emps[0], emps[1])); // 断言部门编号不同的员工不相等
    // not 100% guaranteed, but works for this data // 哈希码可能不同（虽然不是 100% 保证）
    assertNotEquals(comparer.hashCode(emps[0]), comparer.hashCode(emps[1])); // 断言哈希码不同

    assertFalse(comparer.equal(emps[0], null)); // 断言员工与 null 不相等
    assertNotEquals(comparer.hashCode(emps[0]), comparer.hashCode(null)); // 断言哈希码不同

    assertFalse(comparer.equal(null, emps[1])); // 断言 null 与员工不相等
    assertTrue(comparer.equal(null, null)); // 断言 null 与 null 相等
    assertThat(comparer.hashCode(null), is(comparer.hashCode(null))); // 断言哈希码一致
  }

  @Test void testToLookupSelectorComparer() { // 测试带选择器和比较器的 toLookup 操作：创建查找表
    final Lookup<String, Employee> lookup = // 创建一个查找表，键为员工姓名，值为员工列表
        Linq4j.asEnumerable(emps).toLookup( // 将员工数组转换为可枚举集合
            EMP_NAME_SELECTOR, // 使用员工姓名作为键
            new EqualityComparer<String>() { // 创建自定义比较器（基于姓名长度）
              public boolean equal(String v1, String v2) { // 相等比较方法
                return v1.length() == v2.length(); // 比较姓名长度
              }

              public int hashCode(String s) { // 哈希码计算方法
                return s.length(); // 使用姓名长度作为哈希码
              }
            });
    assertThat(lookup, aMapWithSize(2)); // 断言查找表有 2 个分组（姓名长度为 4 和 5）
    assertThat(new TreeSet<>(lookup.keySet()), hasToString("[Fred, Janet]")); // 断言键为 "Fred" 和 "Janet"

    StringBuilder buf = new StringBuilder(); // 创建字符串构建器
    for (Grouping<String, Employee> grouping // 遍历查找表中的每个分组
        : lookup.orderBy(Linq4jTest.groupingKeyExtractor())) { // 按键排序
      buf.append(grouping).append("\n"); // 将分组信息添加到字符串构建器
    }
    assertThat(buf, // 断言分组信息正确
        hasToString("Fred: [Employee(name: Fred, deptno:10)," // 姓名长度为 4 的分组
            + " Employee(name: Bill, deptno:30)," // 包含 Fred、Bill、Eric
            + " Employee(name: Eric, deptno:10)]\n"
            + "Janet: [Employee(name: Janet, deptno:10)]\n")); // 姓名长度为 5 的分组，包含 Janet
  }

  private static <K extends Comparable, V> Function1<Grouping<K, V>, K> groupingKeyExtractor() { // 创建分组键提取器
    return Grouping::getKey; // 返回分组键的方法引用
  }

  /**
   * Tests the version of {@link ExtendedEnumerable#groupBy}
   * that uses an accumulator; does not build intermediate lists.
   */
  @Test void testGroupBy() { // 测试 groupBy 操作：使用累加器进行分组，不构建中间列表
    String s = // 创建一个字符串变量，用于存储分组结果
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .groupBy( // 按部门编号分组
                EMP_DEPTNO_SELECTOR, // 使用部门编号作为分组键
                (Function0<String>) () -> null, // 初始值为 null
                (v1, e0) -> v1 == null ? e0.name : (v1 + "+" + e0.name), // 累加器：将员工姓名用 "+" 连接
                (v1, v2) -> v1 + ": " + v2) // 结果选择器：格式化为 "部门编号:员工姓名列表"
            .orderBy(Functions.identitySelector()) // 按部门编号排序
            .toList() // 转换为列表
            .toString(); // 转换为字符串
    assertThat(s, is("[10: Fred+Eric+Janet, 30: Bill]")); // 断言分组结果正确
  }

  /**
   * Tests the version of
   * {@link ExtendedEnumerable#aggregate}
   * that has a result selector. Note how similar it is to
   * {@link #testGroupBy()}.
   */
  @Test void testAggregate2() { // 测试带结果选择器的 aggregate 操作：对集合进行聚合计算
    String s = // 创建一个字符串变量，用于存储聚合结果
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .aggregate( // 聚合计算
                ((Function0<String>) () -> null).apply(), //CHECKSTYLE: IGNORE 0 // 初始值为 null
                (v1, e0) -> v1 == null ? e0.name : (v1 + "+" + e0.name), v2 -> "<no key>: " + v2); // 累加器和结果选择器
    assertThat(s, is("<no key>: Fred+Bill+Eric+Janet")); // 断言聚合结果正确（没有分组键）
  }

  @Test void testEmptyEnumerable() { // 测试 emptyEnumerable 操作：创建一个空的可枚举集合
    final Enumerable<Object> enumerable = Linq4j.emptyEnumerable(); // 创建一个空的可枚举集合
    assertThat(enumerable.any(), is(false)); // 断言集合不包含任何元素
    assertThat(enumerable.longCount(), equalTo(0L)); // 断言集合的元素数量为 0
    final Enumerator<Object> enumerator = enumerable.enumerator(); // 获取枚举器
    assertThat(enumerator.moveNext(), is(false)); // 断言无法移动到下一个元素
  }

  @Test void testSingletonEnumerable() { // 测试 singletonEnumerable 操作：创建一个只包含一个元素的可枚举集合
    final Enumerable<String> enumerable = Linq4j.singletonEnumerable("foo"); // 创建一个只包含 "foo" 的可枚举集合
    assertThat(enumerable.any(), is(true)); // 断言集合包含元素
    assertThat(enumerable.longCount(), equalTo(1L)); // 断言集合的元素数量为 1
    final Enumerator<String> enumerator = enumerable.enumerator(); // 获取枚举器
    assertThat(enumerator.moveNext(), is(true)); // 断言可以移动到第一个元素
    assertThat(enumerator.current(), equalTo("foo")); // 断言当前元素是 "foo"
    assertThat(enumerator.moveNext(), is(false)); // 断言无法移动到下一个元素
  }

  @Test void testSingletonEnumerator() { // 测试 singletonEnumerator 操作：创建一个只包含一个元素的枚举器
    final Enumerator<String> enumerator = Linq4j.singletonEnumerator("foo"); // 创建一个只包含 "foo" 的枚举器
    assertThat(enumerator.moveNext(), is(true)); // 断言可以移动到第一个元素
    assertThat(enumerator.current(), equalTo("foo")); // 断言当前元素是 "foo"
    assertThat(enumerator.moveNext(), is(false)); // 断言无法移动到下一个元素
  }

  @Test void testSingletonNullEnumerator() { // 测试 singletonNullEnumerator 操作：创建一个只包含 null 的枚举器
    final Enumerator<String> enumerator = Linq4j.singletonNullEnumerator(); // 创建一个只包含 null 的枚举器
    assertThat(enumerator.moveNext(), is(true)); // 断言可以移动到第一个元素
    assertThat(enumerator.current(), nullValue()); // 断言当前元素是 null
    assertThat(enumerator.moveNext(), is(false)); // 断言无法移动到下一个元素
  }

  @Test void testTransformEnumerator() { // 测试 transform 操作：转换枚举器的元素类型
    final List<String> strings = Arrays.asList("one", "two", "three"); // 创建字符串列表
    final Function1<String, Integer> func = String::length; // 创建函数：获取字符串长度
    final Enumerator<Integer> enumerator = // 创建转换后的枚举器
        Linq4j.transform(Linq4j.enumerator(strings), func); // 将字符串枚举器转换为整数枚举器（字符串长度）
    assertThat(enumerator.moveNext(), is(true)); // 断言可以移动到第一个元素
    assertThat(enumerator.current(), is(3)); // 断言 "one" 的长度是 3
    assertThat(enumerator.moveNext(), is(true)); // 断言可以移动到第二个元素
    assertThat(enumerator.current(), is(3)); // 断言 "two" 的长度是 3
    assertThat(enumerator.moveNext(), is(true)); // 断言可以移动到第三个元素
    assertThat(enumerator.current(), is(5)); // 断言 "three" 的长度是 5
    assertThat(enumerator.moveNext(), is(false)); // 断言无法移动到下一个元素

    final Enumerator<Integer> enumerator2 = // 创建转换后的空枚举器
        Linq4j.transform(Linq4j.emptyEnumerator(), func); // 将空枚举器转换为整数枚举器
    assertThat(enumerator2.moveNext(), is(false)); // 断言无法移动到下一个元素
  }

  @Test void testCast() { // 测试 cast 操作：将集合中的元素转换为指定类型（包括 null）
    final List<Number> numbers = Arrays.asList((Number) 2, null, 3.14, 5); // 创建数字列表（包含整数、null、浮点数、整数）
    final Enumerator<Integer> enumerator = // 创建转换后的枚举器
        Linq4j.asEnumerable(numbers) // 将列表转换为可枚举集合
            .cast(Integer.class) // 将元素转换为 Integer 类型（包括 null）
            .enumerator(); // 获取枚举器
    checkCast(enumerator); // 检查转换结果
  }

  @Test void testIterableCast() { // 测试 Iterable 的 cast 操作：将可迭代集合中的元素转换为指定类型
    final List<Number> numbers = Arrays.asList((Number) 2, null, 3.14, 5); // 创建数字列表
    final Enumerator<Integer> enumerator = // 创建转换后的枚举器
        Linq4j.cast(numbers, Integer.class) // 将列表元素转换为 Integer 类型（包括 null）
            .enumerator(); // 获取枚举器
    checkCast(enumerator); // 检查转换结果
  }

  private void checkCast(Enumerator<Integer> enumerator) { // 检查 cast 操作的结果
    assertTrue(enumerator.moveNext()); // 移动到第一个元素
    assertThat(enumerator.current(), is(Integer.valueOf(2))); // 断言第一个元素是 2
    assertTrue(enumerator.moveNext()); // 移动到第二个元素
    assertNull(enumerator.current()); // 断言第二个元素是 null
    assertTrue(enumerator.moveNext()); // 移动到第三个元素
    try { // 尝试获取第三个元素（应该是 3.14，不是 Integer）
      Object x = enumerator.current(); // 获取当前元素
      fail("expected error, got " + x); // 断言失败，应该抛出异常
    } catch (ClassCastException e) { // 捕获预期的异常
      // good // 异常正常，测试通过
    }
    assertTrue(enumerator.moveNext()); // 移动到第四个元素
    assertThat(enumerator.current(), is(Integer.valueOf(5))); // 断言第四个元素是 5
    assertFalse(enumerator.moveNext()); // 断言无法移动到下一个元素
    enumerator.reset(); // 重置枚举器
    assertTrue(enumerator.moveNext()); // 移动到第一个元素
    assertThat(enumerator.current(), is(Integer.valueOf(2))); // 断言第一个元素是 2
  }

  @Test void testOfType() { // 测试 ofType 操作：过滤集合中指定类型的元素（包括 null）
    final List<Number> numbers = Arrays.asList((Number) 2, null, 3.14, 5); // 创建数字列表（包含整数、null、浮点数、整数）
    final Enumerator<Integer> enumerator = // 创建过滤后的枚举器
        Linq4j.asEnumerable(numbers) // 将列表转换为可枚举集合
            .ofType(Integer.class) // 过滤出 Integer 类型的元素（包括 null）
            .enumerator(); // 获取枚举器
    checkIterable(enumerator); // 检查过滤结果
  }

  @Test void testIterableOfType() { // 测试 Iterable 的 ofType 操作：过滤可迭代集合中指定类型的元素
    final List<Number> numbers = Arrays.asList((Number) 2, null, 3.14, 5); // 创建数字列表
    final Enumerator<Integer> enumerator = // 创建过滤后的枚举器
        Linq4j.ofType(numbers, Integer.class) // 过滤出 Integer 类型的元素（包括 null）
            .enumerator(); // 获取枚举器
    checkIterable(enumerator); // 检查过滤结果
  }

  private void checkIterable(Enumerator<Integer> enumerator) { // 检查 ofType 操作的结果
    assertTrue(enumerator.moveNext()); // 移动到第一个元素
    assertThat(enumerator.current(), is(Integer.valueOf(2))); // 断言第一个元素是 2
    assertTrue(enumerator.moveNext()); // 移动到第二个元素
    assertNull(enumerator.current()); // 断言第二个元素是 null
    assertTrue(enumerator.moveNext()); // 移动到第三个元素
    assertThat(enumerator.current(), is(Integer.valueOf(5))); // 断言第三个元素是 5（跳过了 3.14）
    assertFalse(enumerator.moveNext()); // 断言无法移动到下一个元素
    enumerator.reset(); // 重置枚举器
    assertTrue(enumerator.moveNext()); // 移动到第一个元素
    assertThat(enumerator.current(), is(Integer.valueOf(2))); // 断言第一个元素是 2
  }

  @Test void testConcat() { // 测试 concat 操作：连接两个集合

      assertThat( // 断言连接后的集合包含 5 个元素

          Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合

              .concat(Linq4j.asEnumerable(badEmps)) // 连接包含不存在员工的集合

              .count(), // 计算元素数量

          is(5)); // 断言元素数量为 5

    }

  

    @Test void testUnion() { // 测试 union 操作：求两个集合的并集（去重）

      assertThat( // 断言并集包含 5 个元素

          Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合

              .union(Linq4j.asEnumerable(badEmps)) // 与不存在员工的集合求并集

              .union(Linq4j.asEnumerable(emps)) // 再次与员工数组求并集

              .count(), // 计算元素数量

          is(5)); // 断言元素数量为 5（去重后）

    }

  

    @Test void testIntersect() { // 测试 intersect 操作：求两个集合的交集（去重）

      final Employee[] emps2 = { // 创建第二个员工数组

          new Employee(150, "Theodore", 10), // 新员工

          emps[3], // 原员工数组中的第 4 个员工

      };

      assertThat( // 断言交集包含 1 个元素

          Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合

              .intersect(Linq4j.asEnumerable(emps2), false) // 与第二个员工数组求交集（不保留重复）

              .count(), // 计算元素数量

          is(1)); // 断言元素数量为 1（只有 emps[3]）

    }

  

    @Test void testIntersectAll() { // 测试 intersectAll 操作：求两个集合的交集（保留重复）

      final Employee[] emps2 = { // 创建第二个员工数组

          new Employee(150, "Theodore", 10), // 新员工

          emps[3], // 原员工数组中的第 4 个员工（出现 3 次）

          emps[3],

          emps[3]

      };

      assertThat( // 断言交集包含 1 个元素

          Linq4j.asEnumerable(emps2) // 将第二个员工数组转换为可枚举集合

              .intersect(Linq4j.asEnumerable(emps), true) // 与原员工数组求交集（保留重复）

              .count(), // 计算元素数量

          is(1)); // 断言元素数量为 1（只有 emps[3]，原数组只出现 1 次）

    }

  

    @Test void testExcept() { // 测试 except 操作：求两个集合的差集（去重）

      final Employee[] emps2 = { // 创建第二个员工数组

          new Employee(150, "Theodore", 10), // 新员工

          emps[3], // 原员工数组中的第 4 个员工

      };

      assertThat( // 断言差集包含 3 个元素

          Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合

              .except(Linq4j.asEnumerable(emps2), false) // 减去第二个员工数组中的元素（不保留重复）

              .count(), // 计算元素数量

          is(3)); // 断言元素数量为 3（emps[0]、emps[1]、emps[2]）

    }

  

    @Test void testExceptAll() { // 测试 exceptAll 操作：求两个集合的差集（保留重复）

      final Employee[] emps2 = { // 创建第二个员工数组

          new Employee(150, "Theodore", 10), // 新员工（出现 2 次）

          new Employee(150, "Theodore", 10),

          emps[0], // 原员工数组中的第 1 个员工

          emps[1] // 原员工数组中的第 2 个员工

      };

      assertThat( // 断言差集包含 2 个元素

          Linq4j.asEnumerable(emps2) // 将第二个员工数组转换为可枚举集合

              .except(Linq4j.asEnumerable(emps), true) // 减去原员工数组中的元素（保留重复）

              .count(), // 计算元素数量

          is(2)); // 断言元素数量为 2（两个 Theodore，因为原数组中没有）

    }

  

    @Test void testDistinct() { // 测试 distinct 操作：去除集合中的重复元素

      final Employee[] emps2 = { // 创建包含重复元素的员工数组

          new Employee(150, "Theodore", 10), // 新员工

          emps[3], // 原员工数组中的第 4 个员工

          emps[0], // 原员工数组中的第 1 个员工

          emps[3], // 重复的员工

      };

      assertThat( // 断言去重后的集合包含 3 个元素

          Linq4j.asEnumerable(emps2) // 将员工数组转换为可枚举集合

              .distinct() // 去除重复元素

              .count(), // 计算元素数量

          is(3)); // 断言元素数量为 3（Theodore、emps[3]、emps[0]）

    }

  

    @Test void testDistinctWithEqualityComparer() { // 测试带比较器的 distinct 操作：使用自定义比较器去除重复元素

      final Employee[] emps2 = { // 创建包含重复元素的员工数组

          new Employee(150, "Theodore", 10), // 新员工（部门 10）

          emps[3], // 原员工数组中的第 4 个员工（部门 10）

          emps[1], // 原员工数组中的第 2 个员工（部门 30）

          emps[3], // 重复的员工（部门 10）

      };

      assertThat(Linq4j.asEnumerable(emps2) // 将员工数组转换为可枚举集合

              .distinct( // 去除重复元素（使用自定义比较器）

                  new EqualityComparer<Employee>() { // 创建自定义比较器（基于部门编号）

                    public boolean equal(Employee v1, Employee v2) { // 相等比较方法

                      return v1.deptno == v2.deptno; // 只比较部门编号

                    }

  

                    public int hashCode(Employee employee) { // 哈希码计算方法

                      return employee.deptno; // 使用部门编号作为哈希码

                    }

                  })

              .count(), is(2)); // 断言元素数量为 2（部门 10 和部门 30）

    }

  @Test void testGroupJoin() { // 测试 groupJoin 操作：分组连接（左连接）
    // Note #1: Group join is a "left join": "bad employees" are filtered
    //   out, but empty departments are not. // 注意 #1：分组连接是"左连接"：过滤掉不存在部门的员工，但保留空部门
    // Note #2: Order of departments is preserved. // 注意 #2：保留部门的顺序
    String s = // 创建一个字符串变量，用于存储连接结果
        Linq4j.asEnumerable(depts) // 将部门数组转换为可枚举集合
            .groupJoin( // 分组连接
                Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
                    .concat(Linq4j.asEnumerable(badEmps)), // 连接不存在部门的员工
                DEPT_DEPTNO_SELECTOR, // 使用部门编号作为部门的键
                EMP_DEPTNO_SELECTOR, // 使用部门编号作为员工的键
                (v1, v2) -> { // 结果选择器：格式化为 "员工列表 works in 部门名称"
                  final StringBuilder buf = new StringBuilder("["); // 创建字符串构建器
                  int n = 0; // 初始化计数器
                  for (Employee employee : v2) { // 遍历员工列表
                    if (n++ > 0) { // 如果不是第一个员工
                      buf.append(", "); // 添加逗号分隔符
                    }
                    buf.append(employee.name); // 添加员工姓名
                  }
                  return buf.append("] work(s) in ").append(v1.name) // 格式化字符串
                      .toString(); // 转换为字符串
                })
            .toList() // 转换为列表
            .toString(); // 转换为字符串
    assertThat( // 断言连接结果正确
        s, is("[[Fred, Eric, Janet] work(s) in Sales, " // Sales 部门有 3 个员工
            + "[] work(s) in HR, " // HR 部门没有员工
            + "[Bill] work(s) in Marketing]")); // Marketing 部门有 1 个员工
  }

  @Test void testGroupJoinWithComparer() { // 测试带比较器的 groupJoin 操作：使用自定义比较器进行分组连接
    // Note #1: Group join is a "left join": "bad employees" are filtered
    //   out, but empty departments are not. // 注意 #1：分组连接是"左连接"
    // Note #2: Order of departments is preserved. // 注意 #2：保留部门的顺序
    String s = // 创建一个字符串变量，用于存储连接结果
        Linq4j.asEnumerable(depts) // 将部门数组转换为可枚举集合
            .groupJoin( // 分组连接
                Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
                    .concat(Linq4j.asEnumerable(badEmps)), // 连接不存在部门的员工
                DEPT_DEPTNO_SELECTOR, // 使用部门编号作为部门的键
                EMP_DEPTNO_SELECTOR, // 使用部门编号作为员工的键
                (v1, v2) -> { // 结果选择器
                  final StringBuilder buf = new StringBuilder("["); // 创建字符串构建器
                  int n = 0; // 初始化计数器
                  for (Employee employee : v2) { // 遍历员工列表
                    if (n++ > 0) { // 如果不是第一个员工
                      buf.append(", "); // 添加逗号分隔符
                    }
                    buf.append(employee.name); // 添加员工姓名
                  }
                  return buf.append("] work(s) in ").append(v1.name) // 格式化字符串
                      .toString(); // 转换为字符串
                },
                new EqualityComparer<Integer>() { // 创建自定义比较器（所有元素都相等）
                  public boolean equal(Integer v1, Integer v2) { // 相等比较方法
                    return true; // 所有整数都相等
                  }
                  public int hashCode(Integer integer) { // 哈希码计算方法
                    return 0; // 所有整数的哈希码都为 0
                  }
                })
            .toList() // 转换为列表
            .toString(); // 转换为字符串
    assertThat(s, is("[[Fred, Bill, Eric, Janet, Cedric] work(s) in Marketing]")); // 断言所有员工都连接到 Marketing 部门
  }

  @Test void testJoin() { // 测试 join 操作：内连接
    // Note #1: Inner on both sides. Employees with bad departments,
    //   and departments with no employees are eliminated. // 注意 #1：两边都是内连接。过滤掉不存在部门的员工和没有员工的部门
    // Note #2: Order of employees is preserved. // 注意 #2：保留员工的顺序
    String s = // 创建一个字符串变量，用于存储连接结果
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .concat(Linq4j.asEnumerable(badEmps)) // 连接不存在部门的员工
            .hashJoin( // 哈希连接（内连接）
                Linq4j.asEnumerable(depts), // 将部门数组转换为可枚举集合
                EMP_DEPTNO_SELECTOR, // 使用部门编号作为员工的键
                DEPT_DEPTNO_SELECTOR, // 使用部门编号作为部门的键
                (v1, v2) -> v1.name + " works in " + v2.name) // 结果选择器：格式化为 "员工姓名 works in 部门名称"
            .orderBy(Functions.identitySelector()) // 按员工姓名排序
            .toList() // 转换为列表
            .toString(); // 转换为字符串
    assertThat( // 断言连接结果正确
        s, is("[Bill works in Marketing, " // Bill 在 Marketing 部门
            + "Eric works in Sales, " // Eric 在 Sales 部门
            + "Fred works in Sales, " // Fred 在 Sales 部门
            + "Janet works in Sales]")); // Janet 在 Sales 部门
  }

  @Test void testLeftJoin() { // 测试左连接操作
    // Note #1: Left join means emit nulls on RHS but not LHS.
    //   Employees with bad departments are not eliminated;
    //   departments with no employees are eliminated. // 注意 #1：左连接意味着在右侧发出 null，但不在左侧。不过滤掉不存在部门的员工，但过滤掉没有员工的部门
    // Note #2: Order of employees is preserved. // 注意 #2：保留员工的顺序
    String s = // 创建一个字符串变量，用于存储连接结果
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .concat(Linq4j.asEnumerable(badEmps)) // 连接不存在部门的员工
            .hashJoin( // 哈希连接（左连接）
                Linq4j.asEnumerable(depts), // 将部门数组转换为可枚举集合
                EMP_DEPTNO_SELECTOR, // 使用部门编号作为员工的键
                DEPT_DEPTNO_SELECTOR, // 使用部门编号作为部门的键
                (v1, v2) -> v1.name + " works in " // 结果选择器：格式化为 "员工姓名 works in 部门名称"
                    + (v2 == null ? null : v2.name), // 如果部门为 null，则显示 null
                null, // null 值（用于比较）
                false, // 不保留右侧的 null
                true) // 保留左侧的 null
            .orderBy(Functions.identitySelector()) // 按员工姓名排序
            .toList() // 转换为列表
            .toString(); // 转换为字符串
    assertThat( // 断言连接结果正确
        s, is("[Bill works in Marketing, " // Bill 在 Marketing 部门
            + "Cedric works in null, " // Cedric 不存在部门
            + "Eric works in Sales, " // Eric 在 Sales 部门
            + "Fred works in Sales, " // Fred 在 Sales 部门
            + "Janet works in Sales]")); // Janet 在 Sales 部门
  }

  @Test void testRightJoin() { // 测试右连接操作
    // Note #1: Left join means emit nulls on LHS but not RHS.
    //   Employees with bad departments are eliminated;
    //   departments with no employees are not eliminated. // 注意 #1：右连接意味着在左侧发出 null，但不在右侧。过滤掉不存在部门的员工，但不过滤掉没有员工的部门
    // Note #2: Order of employees is preserved. // 注意 #2：保留员工的顺序
    String s = // 创建一个字符串变量，用于存储连接结果
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .concat(Linq4j.asEnumerable(badEmps)) // 连接不存在部门的员工
            .hashJoin( // 哈希连接（右连接）
                Linq4j.asEnumerable(depts), // 将部门数组转换为可枚举集合
                EMP_DEPTNO_SELECTOR, // 使用部门编号作为员工的键
                DEPT_DEPTNO_SELECTOR, // 使用部门编号作为部门的键
                (v1, v2) -> (v1 == null ? null : v1.name) // 结果选择器：格式化为 "员工姓名 works in 部门名称"
                    + " works in " + (v2 == null ? null : v2.name), // 如果员工或部门为 null，则显示 null
                null, // null 值（用于比较）
                true, // 保留左侧的 null
                false) // 不保留右侧的 null
            .orderBy(Functions.identitySelector()) // 按员工姓名排序
            .toList() // 转换为列表
            .toString(); // 转换为字符串
    assertThat( // 断言连接结果正确
        s, is("[Bill works in Marketing, " // Bill 在 Marketing 部门
            + "Eric works in Sales, " // Eric 在 Sales 部门
            + "Fred works in Sales, " // Fred 在 Sales 部门
            + "Janet works in Sales, " // Janet 在 Sales 部门
            + "null works in HR]")); // HR 部门没有员工
  }

  @Test void testFullJoin() { // 测试全连接操作
    // Note #1: Full join means emit nulls both LHS and RHS.
    //   Employees with bad departments are not eliminated;
    //   departments with no employees are not eliminated. // 注意 #1：全连接意味着在左侧和右侧都发出 null。不过滤掉不存在部门的员工，也不过滤掉没有员工的部门
    // Note #2: Order of employees is preserved. // 注意 #2：保留员工的顺序
    String s = // 创建一个字符串变量，用于存储连接结果
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .concat(Linq4j.asEnumerable(badEmps)) // 连接不存在部门的员工
            .hashJoin( // 哈希连接（全连接）
                Linq4j.asEnumerable(depts), // 将部门数组转换为可枚举集合
                EMP_DEPTNO_SELECTOR, // 使用部门编号作为员工的键
                DEPT_DEPTNO_SELECTOR, // 使用部门编号作为部门的键
                (v1, v2) -> (v1 == null ? null : v1.name) // 结果选择器：格式化为 "员工姓名 works in 部门名称"
                    + " works in " + (v2 == null ? null : v2.name), // 如果员工或部门为 null，则显示 null
                null, // null 值（用于比较）
                true, // 保留左侧的 null
                true) // 保留右侧的 null
            .orderBy(Functions.identitySelector()) // 按员工姓名排序
            .toList() // 转换为列表
            .toString(); // 转换为字符串
    assertThat( // 断言连接结果正确
        s, is("[Bill works in Marketing, " // Bill 在 Marketing 部门
            + "Cedric works in null, " // Cedric 不存在部门
            + "Eric works in Sales, " // Eric 在 Sales 部门
            + "Fred works in Sales, " // Fred 在 Sales 部门
            + "Janet works in Sales, " // Janet 在 Sales 部门
            + "null works in HR]")); // HR 部门没有员工
  }

  @Test void cartesianProductWithReset() { // 测试笛卡尔积操作（带重置功能）
    Enumerator<List<Integer>> product = // 创建一个枚举器，用于遍历笛卡尔积
        Linq4j.product( // 计算笛卡尔积
            Arrays.asList( // 创建枚举器列表
                Linq4j.enumerator(Arrays.asList(1, 2)), // 第一个枚举器：[1, 2]
                Linq4j.enumerator(Arrays.asList(3, 4)))); // 第二个枚举器：[3, 4]

    assertThat("cartesian product", // 断言笛卡尔积结果正确
        contentsOf(product), // 获取笛卡尔积的内容
        hasToString("[[1, 3], [1, 4], [2, 3], [2, 4]]")); // 断言结果为 [[1, 3], [1, 4], [2, 3], [2, 4]]
    product.reset(); // 重置枚举器
    assertThat("cartesian product after .reset()", // 断言重置后的笛卡尔积结果正确
        contentsOf(product), // 获取笛卡尔积的内容
        hasToString("[[1, 3], [1, 4], [2, 3], [2, 4]]")); // 断言结果为 [[1, 3], [1, 4], [2, 3], [2, 4]]
    product.moveNext(); // 移动到第一个元素
    product.reset(); // 重置枚举器
    assertThat("cartesian product after .moveNext(); .reset()", // 断言移动后重置的笛卡尔积结果正确
        contentsOf(product), // 获取笛卡尔积的内容
        hasToString("[[1, 3], [1, 4], [2, 3], [2, 4]]")); // 断言结果为 [[1, 3], [1, 4], [2, 3], [2, 4]]
  }

  private <T> List<T> contentsOf(Enumerator<T> enumerator) { // 获取枚举器的所有内容
    List<T> result = new ArrayList<>(); // 创建一个列表，用于存储枚举器的所有元素
    while (enumerator.moveNext()) { // 遍历枚举器
      result.add(enumerator.current()); // 将当前元素添加到列表
    }
    return result; // 返回列表
  }

  @Test void testJoinCartesianProduct() { // 测试连接操作生成笛卡尔积
    int n = // 创建一个整数变量，用于存储笛卡尔积的元素数量
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .<Department, Integer, Integer>hashJoin( // 哈希连接（生成笛卡尔积）
                Linq4j.asEnumerable(depts), // 将部门数组转换为可枚举集合
                (Function1) ONE_SELECTOR, // 使用常量选择器（总是返回 1）
                (Function1) ONE_SELECTOR, // 使用常量选择器（总是返回 1）
                (Function2) PAIR_SELECTOR) // 使用配对选择器（总是返回 1）
            .count(); // 计算元素数量
    assertThat(n, is(12)); // 断言元素数量为 12（4 个员工 × 3 个部门）
  }

  @SuppressWarnings("unchecked")
  @Test void testCartesianProductEnumerator() { // 测试笛卡尔积枚举器
    final Enumerable<String> abc = // 创建一个可枚举集合
        Linq4j.asEnumerable(Arrays.asList("a", "b", "c")); // 包含字符串 "a", "b", "c"
    final Enumerable<String> xy = // 创建另一个可枚举集合
        Linq4j.asEnumerable(Arrays.asList("x", "y")); // 包含字符串 "x", "y"

    final Enumerator<List<String>> productEmpty = // 创建一个空的笛卡尔积枚举器
        Linq4j.product(Arrays.<Enumerator<String>>asList()); // 没有枚举器
    assertTrue(productEmpty.moveNext()); // 断言可以移动到第一个元素（空列表）
    assertThat(productEmpty.current(), empty()); // 断言当前元素是空列表
    assertFalse(productEmpty.moveNext()); // 断言无法移动到下一个元素

    final Enumerator<List<String>> product0 = // 创建一个包含空枚举器的笛卡尔积
        Linq4j.product( // 计算笛卡尔积
            Arrays.asList(Linq4j.emptyEnumerator())); // 包含一个空枚举器
    assertFalse(product0.moveNext()); // 断言无法移动到下一个元素（因为有一个空枚举器）

    final Enumerator<List<String>> productFullEmpty = // 创建一个包含完整枚举器和空枚举器的笛卡尔积
        Linq4j.product( // 计算笛卡尔积
            Arrays.asList( // 创建枚举器列表
                abc.enumerator(), // 包含 "a", "b", "c" 的枚举器
                Linq4j.emptyEnumerator())); // 空枚举器
    assertFalse(productFullEmpty.moveNext()); // 断言无法移动到下一个元素（因为有一个空枚举器）

    final Enumerator<List<String>> productEmptyFull = // 创建一个包含空枚举器和完整枚举器的笛卡尔积
        Linq4j.product( // 计算笛卡尔积
            Arrays.asList( // 创建枚举器列表
                abc.enumerator(), // 包含 "a", "b", "c" 的枚举器
                Linq4j.emptyEnumerator())); // 空枚举器
    assertFalse(productEmptyFull.moveNext()); // 断言无法移动到下一个元素（因为有一个空枚举器）

    final Enumerator<List<String>> productAbcXy = // 创建一个包含两个完整枚举器的笛卡尔积
        Linq4j.product( // 计算笛卡尔积
            Arrays.asList( // 创建枚举器列表
                abc.enumerator(), // 包含 "a", "b", "c" 的枚举器
                xy.enumerator())); // 包含 "x", "y" 的枚举器
    assertTrue(productAbcXy.moveNext()); // 断言可以移动到第一个元素
    assertThat(productAbcXy.current(), is(Arrays.asList("a", "x"))); // 断言第一个元素是 ["a", "x"]
    assertTrue(productAbcXy.moveNext()); // 断言可以移动到第二个元素
    assertThat(productAbcXy.current(), is(Arrays.asList("a", "y"))); // 断言第二个元素是 ["a", "y"]
    assertTrue(productAbcXy.moveNext()); // 断言可以移动到第三个元素
    assertThat(productAbcXy.current(), is(Arrays.asList("b", "x"))); // 断言第三个元素是 ["b", "x"]
    assertTrue(productAbcXy.moveNext()); // 断言可以移动到第四个元素
    assertTrue(productAbcXy.moveNext()); // 断言可以移动到第五个元素
    assertTrue(productAbcXy.moveNext()); // 断言可以移动到第六个元素
    assertFalse(productAbcXy.moveNext()); // 断言无法移动到下一个元素（已遍历完所有元素）
  }

  @Test void testAsQueryable() { // 测试 asQueryable 操作：将可枚举集合转换为可查询集合
    // "count" is an Enumerable method. // "count" 是 Enumerable 方法
    final int n = // 创建一个整数变量，用于存储元素数量
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .asQueryable() // 转换为可查询集合
            .count(); // 计算元素数量
    assertThat(n, is(4)); // 断言元素数量为 4

    // "where" is a Queryable method // "where" 是 Queryable 方法
    // first, use a lambda // 首先，使用 lambda 表达式
    ParameterExpression parameter = // 创建参数表达式
        Expressions.parameter(Employee.class); // 参数类型为 Employee
    final Queryable<Employee> nh = // 创建一个可查询集合
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .asQueryable() // 转换为可查询集合
            .where( // 过滤元素
                Expressions.lambda( // 创建 lambda 表达式
                    Predicate1.class, // 谓词类型
                    Expressions.equal( // 创建相等表达式
                        Expressions.field( // 创建字段表达式
                            parameter, // 参数
                            Employee.class, // 类类型
                            "deptno"), // 字段名称
                        Expressions.constant(10)), // 常量 10
                    parameter)); // 参数
    assertThat(nh.count(), is(3)); // 断言过滤后的元素数量为 3（部门 10 的员工）

    // second, use an expression // 其次，使用表达式
    final Queryable<Employee> nh2 = // 创建一个可查询集合
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .asQueryable() // 转换为可查询集合
            .where( // 过滤元素
                Expressions.lambda(v1 -> v1.deptno == 10)); // 创建 lambda 表达式：部门编号等于 10
    assertThat(nh2.count(), is(3)); // 断言过滤后的元素数量为 3（部门 10 的员工）

    // use lambda, this time call whereN // 使用 lambda 表达式，这次调用 whereN
    ParameterExpression parameterE = // 创建参数表达式（员工）
        Expressions.parameter(Employee.class); // 参数类型为 Employee
    ParameterExpression parameterN = // 创建参数表达式（索引）
        Expressions.parameter(Integer.TYPE); // 参数类型为 int
    final Queryable<Employee> nh3 = // 创建一个可查询集合
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .asQueryable() // 转换为可查询集合
            .whereN( // 过滤元素（带索引）
                Expressions.lambda( // 创建 lambda 表达式
                    (Class<Predicate2<Employee, Integer>>) (Class) Predicate2.class, // 谓词类型（双参数）
                    Expressions.andAlso( // 创建逻辑与表达式
                        Expressions.equal( // 创建相等表达式
                            Expressions.field( // 创建字段表达式
                                parameterE, // 参数（员工）
                                Employee.class, // 类类型
                                "deptno"), // 字段名称
                            Expressions.constant(10)), // 常量 10
                        Expressions.lessThan( // 创建小于表达式
                            parameterN, // 参数（索引）
                            Expressions.constant(3))), // 常量 3
                    parameterE, // 参数（员工）
                    parameterN)); // 参数（索引）
    assertThat(nh3.count(), is(2)); // 断言过滤后的元素数量为 2（部门 10 且索引小于 3 的员工）
  }

  @Test void testTake() { // 测试 take 操作：从集合开头获取指定数量的元素
    final Enumerable<Department> enumerableDepts = // 创建一个可枚举集合
        Linq4j.asEnumerable(depts); // 将部门数组转换为可枚举集合
    final List<Department> enumerableDeptsResult = // 创建一个列表，用于存储结果
        enumerableDepts.take(2).toList(); // 获取前 2 个元素
    assertThat(enumerableDeptsResult, hasSize(2)); // 断言结果包含 2 个元素
    assertThat(enumerableDeptsResult.get(0), is(depts[0])); // 断言第一个元素是 depts[0]
    assertThat(enumerableDeptsResult.get(1), is(depts[1])); // 断言第二个元素是 depts[1]

    final List<Department> enumerableDeptsResult5 = // 创建一个列表，用于存储结果
        enumerableDepts.take(5).toList(); // 获取前 5 个元素（但只有 3 个）
    assertThat(enumerableDeptsResult5, hasSize(3)); // 断言结果包含 3 个元素
  }

  @Test void testTakeEnumerable() { // 测试 EnumerableDefaults.take 方法
    final Enumerable<Department> enumerableDepts = // 创建一个可枚举集合
        Linq4j.asEnumerable(depts); // 将部门数组转换为可枚举集合
    final List<Department> enumerableDeptsResult = // 创建一个列表，用于存储结果
        EnumerableDefaults.take(enumerableDepts, 2).toList(); // 获取前 2 个元素
    assertThat(enumerableDeptsResult, hasSize(2)); // 断言结果包含 2 个元素
    assertThat(enumerableDeptsResult.get(0), is(depts[0])); // 断言第一个元素是 depts[0]
    assertThat(enumerableDeptsResult.get(1), is(depts[1])); // 断言第二个元素是 depts[1]

    final List<Department> enumerableDeptsResult5 = // 创建一个列表，用于存储结果
        EnumerableDefaults.take(enumerableDepts, 5).toList(); // 获取前 5 个元素（但只有 3 个）
    assertThat(enumerableDeptsResult5, hasSize(3)); // 断言结果包含 3 个元素
  }

  @Test void testTakeQueryable() { // 测试 QueryableDefaults.take 方法
    final Queryable<Department> querableDepts = // 创建一个可查询集合
        Linq4j.asEnumerable(depts).asQueryable(); // 将部门数组转换为可查询集合
    final List<Department> queryableResult = // 创建一个列表，用于存储结果
        QueryableDefaults.take(querableDepts, 2).toList(); // 获取前 2 个元素

    assertThat(queryableResult, hasSize(2)); // 断言结果包含 2 个元素
    assertThat(queryableResult.get(0), is(depts[0])); // 断言第一个元素是 depts[0]
    assertThat(queryableResult.get(1), is(depts[1])); // 断言第二个元素是 depts[1]
  }

  @Test void testTakeEnumerableZeroOrNegativeSize() { // 测试 take 操作处理零或负数的情况
    assertThat(EnumerableDefaults.take(Linq4j.asEnumerable(depts), 0) // 获取 0 个元素
            .toList(), hasSize(0)); // 断言结果为空列表
    assertThat(EnumerableDefaults.take(Linq4j.asEnumerable(depts), -2) // 获取 -2 个元素
            .toList(), hasSize(0)); // 断言结果为空列表
  }

  @Test void testTakeQueryableZeroOrNegativeSize() { // 测试 QueryableDefaults.take 方法处理零或负数的情况
    assertThat(QueryableDefaults.take(Linq4j.asEnumerable(depts).asQueryable(), 0) // 获取 0 个元素
            .toList(), hasSize(0)); // 断言结果为空列表
    assertThat(QueryableDefaults.take(Linq4j.asEnumerable(depts).asQueryable(), -2) // 获取 -2 个元素
            .toList(), hasSize(0)); // 断言结果为空列表
  }

  @Test void testTakeEnumerableGreaterThanLength() { // 测试 take 操作处理数量大于集合长度的情况
    final Enumerable<Department> enumerableDepts = // 创建一个可枚举集合
        Linq4j.asEnumerable(depts); // 将部门数组转换为可枚举集合
    final List<Department> depList = // 创建一个列表，用于存储结果
        EnumerableDefaults.take(enumerableDepts, 5).toList(); // 获取前 5 个元素（但只有 3 个）
    assertThat(depList, hasSize(3)); // 断言结果包含 3 个元素
    assertThat(depList.get(0), is(depts[0])); // 断言第一个元素是 depts[0]
    assertThat(depList.get(1), is(depts[1])); // 断言第二个元素是 depts[1]
    assertThat(depList.get(2), is(depts[2])); // 断言第三个元素是 depts[2]
  }

  @Test void testTakeQueryableGreaterThanLength() { // 测试 QueryableDefaults.take 方法处理数量大于集合长度的情况
    final Enumerable<Department> enumerableDepts = // 创建一个可枚举集合
        Linq4j.asEnumerable(depts); // 将部门数组转换为可枚举集合
    final List<Department> depList = // 创建一个列表，用于存储结果
        EnumerableDefaults.take(enumerableDepts, 5).toList(); // 获取前 5 个元素（但只有 3 个）
    assertThat(depList, hasSize(3)); // 断言结果包含 3 个元素
    assertThat(depList.get(0), is(depts[0])); // 断言第一个元素是 depts[0]
    assertThat(depList.get(1), is(depts[1])); // 断言第二个元素是 depts[1]
    assertThat(depList.get(2), is(depts[2])); // 断言第三个元素是 depts[2]
  }

  @Test void testTakeWhileEnumerablePredicate() { // 测试 takeWhile 操作：从集合开头获取满足条件的元素，直到遇到不满足条件的元素
    final Enumerable<Department> enumerableDepts = // 创建一个可枚举集合
        Linq4j.asEnumerable(depts); // 将部门数组转换为可枚举集合
    final List<Department> deptList = // 创建一个列表，用于存储结果
        EnumerableDefaults.takeWhile( // 获取满足条件的元素
            enumerableDepts, v1 -> v1.name.contains("e")).toList(); // 谓词：部门名称包含 "e"

    // Only one department: // 只有一个部门：
    // 0: Sales --> true // Sales 包含 "e"，满足条件
    // 1: HR --> false // HR 不包含 "e"，不满足条件
    // 2: Marketing --> never get to it (we stop after false) // Marketing 永远不会被处理（在遇到 false 后停止）
    assertThat(deptList, hasSize(1)); // 断言结果包含 1 个元素
    assertThat(deptList.get(0), is(depts[0])); // 断言第一个元素是 depts[0]
  }

  @Test void testTakeWhileEnumerableFunction() { // 测试带索引的 takeWhile 操作
    final Enumerable<Department> enumerableDepts = // 创建一个可枚举集合
        Linq4j.asEnumerable(depts); // 将部门数组转换为可枚举集合
    final List<Department> deptList = // 创建一个列表，用于存储结果
        EnumerableDefaults.takeWhile( // 获取满足条件的元素（带索引）
            enumerableDepts,
            new Predicate2<Department, Integer>() { // 创建双参数谓词
              int index = 0; // 初始化索引

              public boolean apply(Department v1, Integer v2) { // 应用谓词
                // Make sure we're passed the correct indices // 确保传递了正确的索引
                assertThat("Invalid index passed to function", v2, is(index++)); // 断言索引正确
                return 20 != v1.deptno; // 返回部门编号不等于 20
              }
            }).toList(); // 转换为列表

    assertThat(deptList, hasSize(1)); // 断言结果包含 1 个元素
    assertThat(deptList.get(0), is(depts[0])); // 断言第一个元素是 depts[0]
  }

  @Test void testTakeWhileQueryableFunctionExpressionPredicate() { // 测试 QueryableDefaults.takeWhile 方法（带表达式）
    final Queryable<Department> queryableDepts = // 创建一个可查询集合
        Linq4j.asEnumerable(depts).asQueryable(); // 将部门数组转换为可查询集合
    Predicate1<Department> predicate = v1 -> "HR".equals(v1.name); // 创建谓词：部门名称为 "HR"
    List<Department> deptList = // 创建一个列表，用于存储结果
        QueryableDefaults.takeWhile( // 获取满足条件的元素
            queryableDepts, Expressions.lambda(predicate)) // 使用表达式
            .toList(); // 转换为列表

    assertThat(deptList, hasSize(0)); // 断言结果为空列表（第一个部门不是 "HR"）

    predicate = v1 -> "Sales".equals(v1.name); // 创建谓词：部门名称为 "Sales"
    deptList = // 创建一个列表，用于存储结果
        QueryableDefaults.takeWhile( // 获取满足条件的元素
            queryableDepts, Expressions.lambda(predicate)) // 使用表达式
            .toList(); // 转换为列表

    assertThat(deptList, hasSize(1)); // 断言结果包含 1 个元素
    assertThat(deptList.get(0), is(depts[0])); // 断言第一个元素是 depts[0]
  }

  @Test void testTakeWhileN() { // 测试带索引的 takeWhileN 操作
    final Queryable<Department> queryableDepts = // 创建一个可查询集合
        Linq4j.asEnumerable(depts).asQueryable(); // 将部门数组转换为可查询集合
    Predicate2<Department, Integer> function2 = // 创建双参数谓词
        new Predicate2<Department, Integer>() {
          int index = 0; // 初始化索引
          public boolean apply(Department v1, Integer v2) { // 应用谓词
            // Make sure we're passed the correct indices // 确保传递了正确的索引
            assertThat("Invalid index passed to function", v2, is(index++)); // 断言索引正确
            return v2 < 2; // 返回索引小于 2
          }
        };

    final List<Department> deptList = // 创建一个列表，用于存储结果
        QueryableDefaults.takeWhileN( // 获取满足条件的元素（带索引）
            queryableDepts, Expressions.lambda(function2)) // 使用表达式
            .toList(); // 转换为列表

    assertThat(deptList, hasSize(2)); // 断言结果包含 2 个元素
    assertThat(deptList.get(0), is(depts[0])); // 断言第一个元素是 depts[0]
    assertThat(deptList.get(1), is(depts[1])); // 断言第二个元素是 depts[1]
  }

  @Test void testAsofJoin() { // 测试 asofJoin 操作：基于时间戳的连接（as-of join）
    // TODO: improve this test // TODO：改进这个测试
    Enumerable<Employee> employees = Linq4j.asEnumerable(emps); // 创建员工可枚举集合
    Enumerable<Department> departments = Linq4j.asEnumerable(depts); // 创建部门可枚举集合
    employees.iterator().forEachRemaining(System.out::println); // 打印所有员工
    departments.iterator().forEachRemaining(System.out::println); // 打印所有部门
    Enumerable<String> result = // 创建结果可枚举集合
        employees.asofJoin(departments, // inner：内部集合
            e -> e.deptno, // outerKeySelector：外部键选择器（员工部门编号）
            d -> d.deptno, // innerKeySelector：内部键选择器（部门编号）
            (e, d) -> e.name + ":" + (d != null ? d.name : "null"),   // resultSelector：结果选择器
            (e, d) -> e.name.charAt(1) <= d.name.charAt(1), // matchComparator：匹配比较器
            Comparator.comparing(d0 -> d0.name),            // timestampComparator：时间戳比较器
            true); // 允许不匹配
    result.iterator().forEachRemaining(System.out::println); // 打印结果
  }

  @Test void testTakeWhileNNoMatch() { // 测试带索引的 takeWhileN 操作（没有匹配）
    final Queryable<Department> queryableDepts = // 创建一个可查询集合
        Linq4j.asEnumerable(depts).asQueryable(); // 将部门数组转换为可查询集合
    Predicate2<Department, Integer> function2 = Functions.falsePredicate2(); // 创建总是返回 false 的谓词
    final List<Department> deptList = // 创建一个列表，用于存储结果
        QueryableDefaults.takeWhileN( // 获取满足条件的元素（带索引）
            queryableDepts, // 可查询集合
            Expressions.lambda(function2)) // 使用表达式
            .toList(); // 转换为列表

    assertThat(deptList, hasSize(0)); // 断言结果为空列表（没有元素满足条件）
  }

  @Test void testSkip() { // 测试 skip 操作：跳过指定数量的元素
    assertThat(Linq4j.asEnumerable(depts).skip(1).count(), is(2)); // 断言跳过 1 个元素后剩余 2 个元素
    assertThat(Linq4j.asEnumerable(depts).skipWhile(v1 -> v1.name.equals("Sales")).count(), is(2)); // 断言跳过名称为 "Sales" 的元素后剩余 2 个元素
    assertThat(Linq4j.asEnumerable(depts).skipWhile(v1 -> !v1.name.equals("Sales")).count(), is(3)); // 断言跳过名称不为 "Sales" 的元素后剩余 3 个元素
    assertThat( // 断言带索引的 skipWhile 操作
        Linq4j.asEnumerable(depts).skipWhile((v1, v2) -> v1.name.equals("Sales") // 谓词：名称为 "Sales" 或索引为 1
            || v2 == 1).count(), is(1)); // 断言剩余 1 个元素

    assertThat(Linq4j.asEnumerable(depts).skip(1).count(), is(2)); // 断言跳过 1 个元素后剩余 2 个元素
    assertThat(Linq4j.asEnumerable(depts).skip(5).count(), is(0)); // 断言跳过 5 个元素后剩余 0 个元素
    assertThat( // 断言带索引的 skipWhile 操作
        Linq4j.asEnumerable(depts).skipWhile((v1, v2) -> v1.name.equals("Sales") // 谓词：名称为 "Sales" 或索引为 1
            || v2 == 1).count(), is(1)); // 断言剩余 1 个元素

    assertThat(Linq4j.asEnumerable(depts).asQueryable().skip(1).count(), is(2)); // 断言可查询集合跳过 1 个元素后剩余 2 个元素
    assertThat(Linq4j.asEnumerable(depts).asQueryable().skip(5).count(), is(0)); // 断言可查询集合跳过 5 个元素后剩余 0 个元素
    assertThat( // 断言可查询集合带索引的 skipWhile 操作
        Linq4j.asEnumerable(depts).asQueryable().skipWhileN( // 带索引的 skipWhile
            Expressions.lambda((v1, v2) -> v1.name.equals("Sales") // 谓词：名称为 "Sales" 或索引为 1
                || v2 == 1)).count(), is(1)); // 断言剩余 1 个元素
  }

  @Test void testOrderBy() { // 测试 orderBy 操作：按指定键排序
    // Note: sort is stable. Records occur Fred, Eric, Janet in input. // 注意：排序是稳定的。输入顺序为 Fred、Eric、Janet
    assertThat(Linq4j.asEnumerable(emps).orderBy(EMP_DEPTNO_SELECTOR) // 按部门编号排序
            .toList(), // 转换为列表
        hasToString("[Employee(name: Fred, deptno:10)," // 断言排序结果正确（部门 10 的员工保持原始顺序）
            + " Employee(name: Eric, deptno:10),"
            + " Employee(name: Janet, deptno:10),"
            + " Employee(name: Bill, deptno:30)]"));
  }

  @Test void testOrderByComparator() { // 测试带比较器的 orderBy 操作
    assertThat(Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .orderBy(EMP_NAME_SELECTOR) // 按员工姓名排序
            .orderBy( // 再按部门编号排序
                EMP_DEPTNO_SELECTOR, Collections.reverseOrder()) // 使用降序比较器
            .toList(), // 转换为列表
        hasToString("[Employee(name: Bill, deptno:30)," // 断言排序结果正确（先按姓名排序，再按部门编号降序）
            + " Employee(name: Eric, deptno:10),"
            + " Employee(name: Fred, deptno:10),"
            + " Employee(name: Janet, deptno:10)]"));
  }

  @Test void testOrderByInSeries() { // 测试连续的 orderBy 操作
    // OrderBy in series works because sort is stable. // 连续的 orderBy 操作有效，因为排序是稳定的
    assertThat(Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .orderBy(EMP_NAME_SELECTOR) // 先按员工姓名排序
            .orderBy(EMP_DEPTNO_SELECTOR) // 再按部门编号排序
            .toList(), // 转换为列表
        hasToString("[Employee(name: Eric, deptno:10)," // 断言排序结果正确（先按姓名排序，再按部门编号排序，保持相对顺序）
            + " Employee(name: Fred, deptno:10),"
            + " Employee(name: Janet, deptno:10),"
            + " Employee(name: Bill, deptno:30)]"));
  }

  @Test void testOrderByDescending() { // 测试 orderByDescending 操作：按指定键降序排序
    assertThat(Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .orderByDescending(EMP_NAME_SELECTOR) // 按员工姓名降序排序
            .toList(), // 转换为列表
        hasToString("[Employee(name: Janet, deptno:10)," // 断言排序结果正确（按姓名降序）
            + " Employee(name: Fred, deptno:10),"
            + " Employee(name: Eric, deptno:10),"
            + " Employee(name: Bill, deptno:30)]"));
  }

  @Test void testReverse() { // 测试 reverse 操作：反转集合中的元素顺序
    assertThat(Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .reverse() // 反转元素顺序
            .toList(), // 转换为列表
        hasToString("[Employee(name: Janet, deptno:10)," // 断言反转结果正确
            + " Employee(name: Eric, deptno:10),"
            + " Employee(name: Bill, deptno:30),"
            + " Employee(name: Fred, deptno:10)]"));
  }

  @Test void testList0() { // 测试 into 操作：将查询结果添加到指定列表
    final List<Employee> employees = // 创建员工列表
        Arrays.asList(new Employee(100, "Fred", 10), // 员工 1
            new Employee(110, "Bill", 30), // 员工 2
            new Employee(120, "Eric", 10), // 员工 3
            new Employee(130, "Janet", 10)); // 员工 4
    final List<Employee> result = new ArrayList<>(); // 创建结果列表
    Linq4j.asEnumerable(employees) // 将员工列表转换为可枚举集合
        .where(e -> e.name.contains("e")) // 过滤出姓名包含 "e" 的员工
        .into(result); // 将结果添加到 result 列表
    assertThat(result, // 断言结果正确
        hasToString("[Employee(name: Fred, deptno:10), " // Fred 和 Janet 的姓名包含 "e"
            + "Employee(name: Janet, deptno:10)]"));
  }

  @Test void testList() { // 测试 groupBy 和 into 操作
    final List<Employee> employees = // 创建员工列表
        Arrays.asList(new Employee(100, "Fred", 10), // 员工 1
            new Employee(110, "Bill", 30), // 员工 2
            new Employee(120, "Eric", 10), // 员工 3
            new Employee(130, "Janet", 10)); // 员工 4
    final Map<Employee, Department> empDepts = new HashMap<>(); // 创建员工到部门的映射
    for (Employee employee : employees) { // 遍历员工列表
      empDepts.put(employee, depts[(employee.deptno - 10) / 10]); // 将员工映射到对应的部门
    }
    final List<Grouping<Object, Map.Entry<Employee, Department>>> result = // 创建结果列表
        new ArrayList<>(); // 用于存储分组结果
    Linq4j.asEnumerable(empDepts.entrySet()) // 将映射条目转换为可枚举集合
        .groupBy((Function1<Map.Entry<Employee, Department>, Object>) Map.Entry::getValue) // 按部门分组
        .into(result); // 将分组结果添加到 result 列表
    assertNotNull(result.toString()); // 断言结果不为 null
  }

  @Test void testList2() { // 测试不同类型的枚举器
    final List<String> experience = Arrays.asList("jimi", "mitch", "noel"); // 创建字符串列表
    final Enumerator<String> enumerator = Linq4j.enumerator(experience); // 创建列表枚举器
    assertThat(enumerator.getClass().getName(), endsWith("ListEnumerator")); // 断言枚举器类型为 ListEnumerator
    assertThat(count(enumerator), equalTo(3)); // 断言枚举器包含 3 个元素

    final Enumerable<String> listEnumerable = Linq4j.asEnumerable(experience); // 将列表转换为可枚举集合
    final Enumerator<String> listEnumerator = listEnumerable.enumerator(); // 获取枚举器
    assertThat(listEnumerator.getClass().getName(), // 断言枚举器类型为 ListEnumerator
        endsWith("ListEnumerator"));
    assertThat(count(listEnumerator), equalTo(3)); // 断言枚举器包含 3 个元素

    final Enumerable<String> linkedListEnumerable = // 将链表转换为可枚举集合
        Linq4j.asEnumerable(Lists.newLinkedList(experience)); // 使用 Google Guava 创建链表
    final Enumerator<String> iterableEnumerator = // 获取枚举器
        linkedListEnumerable.enumerator(); // 获取枚举器
    assertThat(iterableEnumerator.getClass().getName(), // 断言枚举器类型为 IterableEnumerator
        endsWith("IterableEnumerator"));
    assertThat(count(iterableEnumerator), equalTo(3)); // 断言枚举器包含 3 个元素
  }

  @Test void testDefaultIfEmpty() { // 测试 defaultIfEmpty 操作：如果集合为空，则返回包含默认值的集合
    final List<String> experience = Arrays.asList("jimi", "mitch", "noel"); // 创建字符串列表
    final Enumerable<String> notEmptyEnumerable = Linq4j.asEnumerable(experience).defaultIfEmpty(); // 如果集合为空，则返回包含 null 的集合
    final Enumerator<String> notEmptyEnumerator = notEmptyEnumerable.enumerator(); // 获取枚举器
    notEmptyEnumerator.moveNext(); // 移动到第一个元素
    assertThat(notEmptyEnumerator.current(), is("jimi")); // 断言第一个元素是 "jimi"
    notEmptyEnumerator.moveNext(); // 移动到第二个元素
    assertThat(notEmptyEnumerator.current(), is("mitch")); // 断言第二个元素是 "mitch"
    notEmptyEnumerator.moveNext(); // 移动到第三个元素
    assertThat(notEmptyEnumerator.current(), is("noel")); // 断言第三个元素是 "noel"

    final Enumerable<String> emptyEnumerable = // 创建空集合
        Linq4j.asEnumerable(Linq4j.<String>emptyEnumerable()).defaultIfEmpty(); // 如果集合为空，则返回包含 null 的集合
    final Enumerator<String> emptyEnumerator = emptyEnumerable.enumerator(); // 获取枚举器
    assertTrue(emptyEnumerator.moveNext()); // 断言可以移动到第一个元素（null）
    assertNull(emptyEnumerator.current()); // 断言当前元素是 null
    assertFalse(emptyEnumerator.moveNext()); // 断言无法移动到下一个元素
  }

  @Test void testDefaultIfEmpty2() { // 测试 defaultIfEmpty 操作（指定默认值）
    final List<String> experience = Arrays.asList("jimi", "mitch", "noel"); // 创建字符串列表
    final Enumerable<String> notEmptyEnumerable = // 如果集合为空，则返回包含 "dummy" 的集合
        Linq4j.asEnumerable(experience).defaultIfEmpty("dummy"); // 指定默认值为 "dummy"
    final Enumerator<String> notEmptyEnumerator = notEmptyEnumerable.enumerator(); // 获取枚举器
    notEmptyEnumerator.moveNext(); // 移动到第一个元素
    assertThat(notEmptyEnumerator.current(), is("jimi")); // 断言第一个元素是 "jimi"
    notEmptyEnumerator.moveNext(); // 移动到第二个元素
    assertThat(notEmptyEnumerator.current(), is("mitch")); // 断言第二个元素是 "mitch"
    notEmptyEnumerator.moveNext(); // 移动到第三个元素
    assertThat(notEmptyEnumerator.current(), is("noel")); // 断言第三个元素是 "noel"

    final Enumerable<String> emptyEnumerable = // 创建空集合
        Linq4j.asEnumerable(Linq4j.<String>emptyEnumerable()).defaultIfEmpty("N/A"); // 如果集合为空，则返回包含 "N/A" 的集合
    final Enumerator<String> emptyEnumerator = emptyEnumerable.enumerator(); // 获取枚举器
    assertTrue(emptyEnumerator.moveNext()); // 断言可以移动到第一个元素（"N/A"）
    assertThat(emptyEnumerator.current(), is("N/A")); // 断言当前元素是 "N/A"
    assertFalse(emptyEnumerator.moveNext()); // 断言无法移动到下一个元素
  }

  @Test void testElementAt() { // 测试 elementAt 操作：获取指定索引处的元素
    final Enumerable<String> enumerable = Linq4j.asEnumerable(Arrays.asList("jimi", "mitch")); // 创建可枚举集合
    assertThat(enumerable.elementAt(0), is("jimi")); // 断言索引 0 处的元素是 "jimi"
    try { // 测试索引超出范围的情况
      enumerable.elementAt(2); // 尝试获取索引 2 处的元素
      fail(); // 断言失败，应该抛出异常
    } catch (Exception ignored) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }
    try { // 测试索引为负数的情况
      enumerable.elementAt(-1); // 尝试获取索引 -1 处的元素
      fail(); // 断言失败，应该抛出异常
    } catch (Exception ignored) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }
  }

  @Test void testElementAtWithoutList() { // 测试 elementAt 操作（非列表集合）
    final Enumerable<String> enumerable = // 创建可枚举集合（不可修改的集合）
        Linq4j.asEnumerable(Collections.unmodifiableCollection(Arrays.asList("jimi", "mitch"))); // 创建不可修改的集合
    assertThat(enumerable.elementAt(0), is("jimi")); // 断言索引 0 处的元素是 "jimi"
    try { // 测试索引超出范围的情况
      enumerable.elementAt(2); // 尝试获取索引 2 处的元素
      fail(); // 断言失败，应该抛出异常
    } catch (Exception ignored) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }
    try { // 测试索引为负数的情况
      enumerable.elementAt(-1); // 尝试获取索引 -1 处的元素
      fail(); // 断言失败，应该抛出异常
    } catch (Exception ignored) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }
  }

  @Test void testElementAtOrDefault() { // 测试 elementAtOrDefault 操作：获取指定索引处的元素，如果索引超出范围则返回默认值
    final Enumerable<String> enumerable = Linq4j.asEnumerable(Arrays.asList("jimi", "mitch")); // 创建可枚举集合
    assertThat(enumerable.elementAtOrDefault(0), is("jimi")); // 断言索引 0 处的元素是 "jimi"
    assertNull(enumerable.elementAtOrDefault(2)); // 断言索引 2 处的元素是 null（超出范围）
    assertNull(enumerable.elementAtOrDefault(-1)); // 断言索引 -1 处的元素是 null（负数）
  }

  @Test void testElementAtOrDefaultWithoutList() { // 测试 elementAtOrDefault 操作（非列表集合）
    final Enumerable<String> enumerable = // 创建可枚举集合（不可修改的集合）
        Linq4j.asEnumerable(Collections.unmodifiableCollection(Arrays.asList("jimi", "mitch"))); // 创建不可修改的集合
    assertThat(enumerable.elementAt(0), is("jimi")); // 断言索引 0 处的元素是 "jimi"
    try { // 测试索引超出范围的情况（elementAtOrDefault 不应该抛出异常）
      enumerable.elementAt(2); // 尝试获取索引 2 处的元素
      fail(); // 断言失败，应该抛出异常
    } catch (Exception ignored) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }
    try { // 测试索引为负数的情况（elementAtOrDefault 不应该抛出异常）
      enumerable.elementAt(-1); // 尝试获取索引 -1 处的元素
      fail(); // 断言失败，应该抛出异常
    } catch (Exception ignored) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }
  }

  @Test void testLast() { // 测试 last 操作：获取集合的最后一个元素
    final Enumerable<String> enumerable = Linq4j.asEnumerable(Arrays.asList("jimi", "mitch")); // 创建可枚举集合
    assertThat(enumerable.last(), is("mitch")); // 断言最后一个元素是 "mitch"

    final Enumerable<?> emptyEnumerable = Linq4j.asEnumerable(Collections.EMPTY_LIST); // 创建空集合
    try { // 测试空集合的情况
      emptyEnumerable.last(); // 尝试获取最后一个元素
      fail(); // 断言失败，应该抛出异常
    } catch (Exception ignored) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }
  }

  @Test void testLastWithoutList() { // 测试 last 操作（非列表集合）
    final Enumerable<String> enumerable = // 创建可枚举集合（不可修改的集合）
        Linq4j.asEnumerable( // 将不可修改的集合转换为可枚举集合
            Collections.unmodifiableCollection(Arrays.asList("jimi", "noel", "mitch"))); // 创建不可修改的集合
    assertThat(enumerable.last(), is("mitch")); // 断言最后一个元素是 "mitch"
  }

  @Test void testLastOrDefault() { // 测试 lastOrDefault 操作：获取集合的最后一个元素，如果集合为空则返回默认值
    final Enumerable<String> enumerable = Linq4j.asEnumerable(Arrays.asList("jimi", "mitch")); // 创建可枚举集合
    assertThat(enumerable.lastOrDefault(), is("mitch")); // 断言最后一个元素是 "mitch"

    final Enumerable<?> emptyEnumerable = Linq4j.asEnumerable(Collections.EMPTY_LIST); // 创建空集合
    assertNull(emptyEnumerable.lastOrDefault()); // 断言空集合返回 null
  }

  @Test void testLastWithPredicate() { // 测试带谓词的 last 操作：获取集合中最后一个满足条件的元素
    final Enumerable<String> enumerable = // 创建可枚举集合
        Linq4j.asEnumerable(Arrays.asList("jimi", "mitch", "ming")); // 创建字符串列表
    assertThat(enumerable.last(x -> x.startsWith("mit")), is("mitch")); // 断言最后一个以 "mit" 开头的字符串是 "mitch"
    try { // 测试没有元素满足条件的情况
      enumerable.last(x -> false); // 尝试获取满足条件的最后一个元素（没有元素满足）
      fail(); // 断言失败，应该抛出异常
    } catch (Exception ignored) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }

    @SuppressWarnings("unchecked") // 抑制未检查的警告
    final Enumerable<String> emptyEnumerable = Linq4j.asEnumerable(Collections.EMPTY_LIST); // 创建空集合
    try { // 测试空集合的情况
      emptyEnumerable.last(x -> { // 尝试获取满足条件的最后一个元素
        fail(); // 断言失败，不应该执行到这里
        return false; // 返回 false
      });
      fail(); // 断言失败，应该抛出异常
    } catch (Exception ignored) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }
  }

  @Test void testLastOrDefaultWithPredicate() { // 测试带谓词的 lastOrDefault 操作：获取集合中最后一个满足条件的元素，如果没有则返回默认值
    final Enumerable<String> enumerable = // 创建可枚举集合
        Linq4j.asEnumerable(Arrays.asList("jimi", "mitch", "ming")); // 创建字符串列表
    assertThat(enumerable.lastOrDefault(x -> x.startsWith("mit")), is("mitch")); // 断言最后一个以 "mit" 开头的字符串是 "mitch"
    assertNull(enumerable.lastOrDefault(x -> false)); // 断言没有元素满足条件时返回 null

    @SuppressWarnings("unchecked") // 抑制未检查的警告
    final Enumerable<String> emptyEnumerable = Linq4j.asEnumerable(Collections.EMPTY_LIST); // 创建空集合
    assertNull( // 断言空集合返回 null
        emptyEnumerable.lastOrDefault(x -> { // 尝试获取满足条件的最后一个元素
          fail(); // 断言失败，不应该执行到这里
          return false; // 返回 false
        }));
  }

  @Test void testSelectManyWithIndexableSelector() { // 测试带索引的 selectMany 操作

      final int[] indexRef = {0}; // 创建索引引用数组，用于验证索引是否正确传递

      final List<String> nameSeqs = // 创建一个字符串列表，用于存储带序号的员工姓名

          Linq4j.asEnumerable(depts) // 将部门数组转换为可枚举集合

              .selectMany((element, index) -> { // 对每个部门，提取其员工列表并展平（带索引）

                assertThat(index.longValue(), is((long) indexRef[0])); // 断言索引正确

                indexRef[0] = index + 1; // 更新索引引用

                return Linq4j.asEnumerable(element.employees); // 返回员工列表

              })

              .select((v1, v2) -> "#" + v2 + ": " + v1.name) // 为每个员工添加序号（使用索引）

              .toList(); // 转换为列表

      assertThat(nameSeqs, // 断言结果列表包含所有员工及其序号

          hasToString("[#0: Fred, #1: Eric, #2: Janet, #3: Bill]"));

    }

  

    @Test void testSelectManyWithResultSelector() { // 测试带结果选择器的 selectMany 操作

      final List<String> nameSeqs = // 创建一个字符串列表，用于存储带部门信息的员工姓名

          Linq4j.asEnumerable(depts) // 将部门数组转换为可枚举集合

              .selectMany(DEPT_EMPLOYEES_SELECTOR, // 对每个部门，提取其员工列表并展平

                  (element, subElement) -> subElement.name + "@" + element.name) // 结果选择器：格式化为 "员工姓名@部门名称"

              .select((v0, v1) -> "#" + v1 + ": " + v0) // 为每个员工添加序号

              .toList(); // 转换为列表

      assertThat(nameSeqs, // 断言结果列表包含所有员工及其部门信息

          hasToString("[#0: Fred@Sales," // Fred 在 Sales 部门

              + " #1: Eric@Sales," // Eric 在 Sales 部门

              + " #2: Janet@Sales," // Janet 在 Sales 部门

              + " #3: Bill@Marketing]")); // Bill 在 Marketing 部门

    }

  

    @Test void testSelectManyWithIndexableSelectorAndResultSelector() { // 测试带索引和结果选择器的 selectMany 操作

      final int[] indexRef = {0}; // 创建索引引用数组，用于验证索引是否正确传递

      final List<String> nameSeqs = // 创建一个字符串列表，用于存储带部门信息的员工姓名

          Linq4j.asEnumerable(depts) // 将部门数组转换为可枚举集合

              .selectMany((element, index) -> { // 对每个部门，提取其员工列表并展平（带索引）

                assertThat(index.longValue(), is((long) indexRef[0])); // 断言索引正确

                indexRef[0] = index + 1; // 更新索引引用

                return Linq4j.asEnumerable(element.employees); // 返回员工列表

              }, (element, subElement) -> subElement.name + "@" + element.name) // 结果选择器：格式化为 "员工姓名@部门名称"

              .select((v0, v1) -> "#" + v1 + ": " + v0) // 为每个员工添加序号

              .toList(); // 转换为列表

      assertThat(nameSeqs, // 断言结果列表包含所有员工及其部门信息

          hasToString("[#0: Fred@Sales," // Fred 在 Sales 部门

              + " #1: Eric@Sales," // Eric 在 Sales 部门

              + " #2: Janet@Sales," // Janet 在 Sales 部门

              + " #3: Bill@Marketing]")); // Bill 在 Marketing 部门

    }

  @Test void testSequenceEqual() { // 测试 sequenceEqual 操作：检查两个集合的元素是否相等（按顺序）
    final Enumerable<String> enumerable1 = // 创建第一个可枚举集合
        Linq4j.asEnumerable( // 将不可修改的集合转换为可枚举集合
            Collections.unmodifiableCollection( // 创建不可修改的集合
                Arrays.asList("ming", "foo", "bar"))); // 创建字符串列表
    final Enumerable<String> enumerable2 = // 创建第二个可枚举集合
        Linq4j.asEnumerable( // 将不可修改的集合转换为可枚举集合
            Collections.unmodifiableCollection( // 创建不可修改的集合
                Arrays.asList("ming", "foo", "bar"))); // 创建字符串列表
    assertTrue(enumerable1.sequenceEqual(enumerable2)); // 断言两个集合相等
    assertFalse( // 断言两个集合不相等
        enumerable1.sequenceEqual(
            Linq4j.asEnumerable(new String[]{"ming", "foo", "far"}))); // 创建不同的字符串列表

    try { // 测试第一个集合为 null 的情况
      EnumerableDefaults.sequenceEqual(null, enumerable2); // 尝试比较 null 和 enumerable2
      fail(); // 断言失败，应该抛出异常
    } catch (NullPointerException ignored) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }
    try { // 测试第二个集合为 null 的情况
      EnumerableDefaults.sequenceEqual(enumerable1, null); // 尝试比较 enumerable1 和 null
      fail(); // 断言失败，应该抛出异常
    } catch (NullPointerException ignored) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }

    assertFalse(Linq4j.asEnumerable(enumerable1.skip(1).toList()) // Keep as collection // 断言跳过第一个元素后不相等
        .sequenceEqual(enumerable2));
    assertFalse(enumerable1 // 断言跳过第一个元素后不相等
        .sequenceEqual(Linq4j.asEnumerable(enumerable2.skip(1).toList()))); // Keep as collection
  }

  @Test void testSequenceEqualWithoutCollection() { // 测试 sequenceEqual 操作（非集合）
    final Enumerable<String> enumerable1 = // 创建第一个可枚举集合（使用迭代器）
        Linq4j.asEnumerable(() -> Arrays.asList("ming", "foo", "bar").iterator()); // 创建迭代器
    final Enumerable<String> enumerable2 = // 创建第二个可枚举集合（使用迭代器）
        Linq4j.asEnumerable(() -> Arrays.asList("ming", "foo", "bar").iterator()); // 创建迭代器
    assertTrue(enumerable1.sequenceEqual(enumerable2)); // 断言两个集合相等
    assertFalse( // 断言两个集合不相等
        enumerable1.sequenceEqual(
            Linq4j.asEnumerable(() -> Arrays.asList("ming", "foo", "far").iterator()))); // 创建不同的迭代器

    try { // 测试第一个集合为 null 的情况
      EnumerableDefaults.sequenceEqual(null, enumerable2); // 尝试比较 null 和 enumerable2
      fail(); // 断言失败，应该抛出异常
    } catch (NullPointerException ignored) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }
    try { // 测试第二个集合为 null 的情况
      EnumerableDefaults.sequenceEqual(enumerable1, null); // 尝试比较 enumerable1 和 null
      fail(); // 断言失败，应该抛出异常
    } catch (NullPointerException ignored) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }

    assertFalse(enumerable1.skip(1).sequenceEqual(enumerable2)); // 断言跳过第一个元素后不相等
    assertFalse(enumerable1.sequenceEqual(enumerable2.skip(1))); // 断言跳过第一个元素后不相等
  }

  @Test void testSequenceEqualWithComparer() { // 测试带比较器的 sequenceEqual 操作
    final Enumerable<String> enumerable1 = // 创建第一个可枚举集合
        Linq4j.asEnumerable( // 将不可修改的集合转换为可枚举集合
            Collections.unmodifiableCollection( // 创建不可修改的集合
                Arrays.asList("ming", "foo", "bar"))); // 创建字符串列表
    final Enumerable<String> enumerable2 = // 创建第二个可枚举集合
        Linq4j.asEnumerable( // 将不可修改的集合转换为可枚举集合
            Collections.unmodifiableCollection( // 创建不可修改的集合
                Arrays.asList("ming", "foo", "bar"))); // 创建字符串列表
    final EqualityComparer<String> equalityComparer = new EqualityComparer<String>() { // 创建自定义比较器（反转相等性）
      public boolean equal(String v1, String v2) { // 相等比较方法
        return !Objects.equals(v1, v2); // reverse the equality. // 反转相等性（不相等才返回 true）
      }

      public int hashCode(String s) { // 哈希码计算方法
        return Objects.hashCode(s); // 使用默认哈希码
      }
    };
    assertFalse(enumerable1.sequenceEqual(enumerable2, equalityComparer)); // 断言两个集合不相等（因为反转了相等性）
    assertTrue(enumerable1 // 断言两个集合相等（因为反转了相等性）
        .sequenceEqual(Linq4j.asEnumerable(Arrays.asList("fun", "lol", "far")), equalityComparer)); // 创建不同的字符串列表

    try { // 测试第一个集合为 null 的情况
      EnumerableDefaults.sequenceEqual(null, enumerable2); // 尝试比较 null 和 enumerable2
      fail(); // 断言失败，应该抛出异常
    } catch (NullPointerException ignored) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }
    try { // 测试第二个集合为 null 的情况
      EnumerableDefaults.sequenceEqual(enumerable1, null); // 尝试比较 enumerable1 和 null
      fail(); // 断言失败，应该抛出异常
    } catch (NullPointerException ignored) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }

    assertFalse(Linq4j.asEnumerable(enumerable1.skip(1).toList()) // Keep as collection // 断言跳过第一个元素后不相等
        .sequenceEqual(enumerable2));
    assertFalse(enumerable1 // 断言跳过第一个元素后不相等
        .sequenceEqual(Linq4j.asEnumerable(enumerable2.skip(1).toList()))); // Keep as collection
  }

  @Test void testSequenceEqualWithComparerWithoutCollection() { // 测试带比较器的 sequenceEqual 操作（非集合）
    final Enumerable<String> enumerable1 = // 创建第一个可枚举集合（使用迭代器）
        Linq4j.asEnumerable(() -> Arrays.asList("ming", "foo", "bar").iterator()); // 创建迭代器
    final Enumerable<String> enumerable2 = // 创建第二个可枚举集合（使用迭代器）
        Linq4j.asEnumerable(() -> Arrays.asList("ming", "foo", "bar").iterator()); // 创建迭代器
    final EqualityComparer<String> equalityComparer = new EqualityComparer<String>() { // 创建自定义比较器（反转相等性）
      public boolean equal(String v1, String v2) { // 相等比较方法
        return !Objects.equals(v1, v2); // reverse the equality. // 反转相等性（不相等才返回 true）
      }
      public int hashCode(String s) { // 哈希码计算方法
        return Objects.hashCode(s); // 使用默认哈希码
      }
    };
    assertFalse(enumerable1.sequenceEqual(enumerable2, equalityComparer)); // 断言两个集合不相等（因为反转了相等性）
    final Enumerable<String> enumerable3 = // 创建第三个可枚举集合（使用迭代器）
        Linq4j.asEnumerable(() -> Arrays.asList("fun", "lol", "far").iterator()); // 创建不同的迭代器
    assertTrue( // 断言两个集合相等（因为反转了相等性）
        enumerable1.sequenceEqual(enumerable3, equalityComparer));

    try { // 测试第一个集合为 null 的情况
      EnumerableDefaults.sequenceEqual(null, enumerable2); // 尝试比较 null 和 enumerable2
      fail(); // 断言失败，应该抛出异常
    } catch (NullPointerException ignored) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }
    try { // 测试第二个集合为 null 的情况
      EnumerableDefaults.sequenceEqual(enumerable1, null); // 尝试比较 enumerable1 和 null
      fail(); // 断言失败，应该抛出异常
    } catch (NullPointerException ignored) { // 捕获预期的异常
      // ok // 异常正常，测试通过
    }

    assertFalse(enumerable1.skip(1).sequenceEqual(enumerable2)); // 断言跳过第一个元素后不相等
    assertFalse(enumerable1.sequenceEqual(enumerable2.skip(1))); // 断言跳过第一个元素后不相等
  }

  @Test void testGroupByWithKeySelector() { // 测试 groupBy 操作：只使用键选择器分组
    String s = // 创建一个字符串变量，用于存储分组结果
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .groupBy(EMP_DEPTNO_SELECTOR) // 按部门编号分组
            .select(group -> // 选择分组结果
                String.format(Locale.ROOT, "%s: %s", group.getKey(), // 格式化为 "部门编号:员工姓名列表"
                    stringJoin("+", group.select(element -> element.name)))) // 员工姓名用 "+" 连接
            .toList() // 转换为列表
            .toString(); // 转换为字符串
    assertThat(s, is("[10: Fred+Eric+Janet, 30: Bill]")); // 断言分组结果正确
  }

  @Test void testGroupByWithKeySelectorAndComparer() { // 测试 groupBy 操作：使用键选择器和比较器分组
    String s = // 创建一个字符串变量，用于存储分组结果
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .groupBy(EMP_DEPTNO_SELECTOR, new EqualityComparer<Integer>() { // 按部门编号分组（使用自定义比较器）
              public boolean equal(Integer v1, Integer v2) { // 相等比较方法
                return true; // 所有整数都相等
              }
              public int hashCode(Integer integer) { // 哈希码计算方法
                return 0; // 所有整数的哈希码都为 0
              }
            })
            .select(group -> // 选择分组结果
                String.format(Locale.ROOT, "%s: %s", group.getKey(), // 格式化为 "部门编号:员工姓名列表"
                    stringJoin("+", group.select(element -> element.name)))) // 员工姓名用 "+" 连接
            .toList() // 转换为列表
            .toString(); // 转换为字符串
    assertThat(s, is("[10: Fred+Bill+Eric+Janet]")); // 断言所有员工都分到同一组
  }

  @Test void testGroupByWithKeySelectorAndElementSelector() { // 测试 groupBy 操作：使用键选择器和元素选择器分组
    String s = // 创建一个字符串变量，用于存储分组结果
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .groupBy(EMP_DEPTNO_SELECTOR, EMP_NAME_SELECTOR) // 按部门编号分组，元素选择器选择员工姓名
            .select(group -> // 选择分组结果
                String.format(Locale.ROOT, "%s: %s", group.getKey(), // 格式化为 "部门编号:员工姓名列表"
                    stringJoin("+", group))) // 员工姓名用 "+" 连接
            .toList() // 转换为列表
            .toString(); // 转换为字符串
    assertThat(s, is("[10: Fred+Eric+Janet, 30: Bill]")); // 断言分组结果正确
  }

  /** Equivalent to {@link String}.join, but that method is only in JDK 1.8 and
   * higher. */
  private static String stringJoin(String delimiter, Iterable<String> group) { // 字符串连接方法（类似于 String.join）
    final StringBuilder sb = new StringBuilder(); // 创建字符串构建器
    final Iterator<String> iterator = group.iterator(); // 获取迭代器
    if (iterator.hasNext()) { // 如果有元素
      sb.append(iterator.next()); // 添加第一个元素
      while (iterator.hasNext()) { // 遍历剩余元素
        sb.append(delimiter).append(iterator.next()); // 添加分隔符和元素
      }
    }
    return sb.toString(); // 返回连接后的字符串
  }

  @Test void testGroupByWithKeySelectorAndElementSelectorAndComparer() { // 测试 groupBy 操作：使用键选择器、元素选择器和比较器分组
    String s = // 创建一个字符串变量，用于存储分组结果
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .groupBy(EMP_DEPTNO_SELECTOR, EMP_NAME_SELECTOR, // 按部门编号分组，元素选择器选择员工姓名
                new EqualityComparer<Integer>() { // 使用自定义比较器
                  public boolean equal(Integer v1, Integer v2) { // 相等比较方法
                    return true; // 所有整数都相等
                  }
                  public int hashCode(Integer integer) { // 哈希码计算方法
                    return 0; // 所有整数的哈希码都为 0
                  }
                })
            .select(group -> // 选择分组结果
                String.format(Locale.ROOT, "%s: %s", group.getKey(), // 格式化为 "部门编号:员工姓名列表"
                    stringJoin("+", group))) // 员工姓名用 "+" 连接
            .toList() // 转换为列表
            .toString(); // 转换为字符串
    assertThat(s, is("[10: Fred+Bill+Eric+Janet]")); // 断言所有员工都分到同一组
  }

  @Test void testGroupByWithKeySelectorAndResultSelector() { // 测试 groupBy 操作：使用键选择器和结果选择器分组
    String s = // 创建一个字符串变量，用于存储分组结果
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .groupBy( // 分组
                EMP_DEPTNO_SELECTOR, // 按部门编号分组
                (key, group) -> String.format(Locale.ROOT, "%s: %s", key, // 结果选择器：格式化为 "部门编号:员工姓名列表"
                    stringJoin("+", group.select(element -> element.name)))) // 员工姓名用 "+" 连接
            .toList() // 转换为列表
            .toString(); // 转换为字符串
    assertThat(s, is("[10: Fred+Eric+Janet, 30: Bill]")); // 断言分组结果正确
  }

  @Test void testGroupByWithKeySelectorAndResultSelectorAndComparer() { // 测试 groupBy 操作：使用键选择器、结果选择器和比较器分组
    String s = // 创建一个字符串变量，用于存储分组结果
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .groupBy(EMP_DEPTNO_SELECTOR, // 按部门编号分组
                (key, group) -> String.format(Locale.ROOT, "%s: %s", key, // 结果选择器：格式化为 "部门编号:员工姓名列表"
                    stringJoin("+", group.select(element -> element.name))), // 员工姓名用 "+" 连接
                new EqualityComparer<Integer>() { // 使用自定义比较器
                  public boolean equal(Integer v1, Integer v2) { // 相等比较方法
                    return true; // 所有整数都相等
                  }
                  public int hashCode(Integer integer) { // 哈希码计算方法
                    return 0; // 所有整数的哈希码都为 0
                  }
                })
            .toList() // 转换为列表
            .toString(); // 转换为字符串
    assertThat(s, is("[10: Fred+Bill+Eric+Janet]")); // 断言所有员工都分到同一组
  }

  @Test void testGroupByWithKeySelectorAndElementSelectorAndResultSelector() { // 测试 groupBy 操作：使用键选择器、元素选择器和结果选择器分组
    String s = // 创建一个字符串变量，用于存储分组结果
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .groupBy(EMP_DEPTNO_SELECTOR, EMP_NAME_SELECTOR, // 按部门编号分组，元素选择器选择员工姓名
                (key, group) -> String.format(Locale.ROOT, "%s: %s", key, // 结果选择器：格式化为 "部门编号:员工姓名列表"
                    stringJoin("+", group))) // 员工姓名用 "+" 连接
            .toList() // 转换为列表
            .toString(); // 转换为字符串
    assertThat(s, is("[10: Fred+Eric+Janet, 30: Bill]")); // 断言分组结果正确
  }

  @Test void testGroupByWithKeySelectorAndElementSelectorAndResultSelectorAndComparer() { // 测试 groupBy 操作：使用键选择器、元素选择器、结果选择器和比较器分组
    String s = // 创建一个字符串变量，用于存储分组结果
        Linq4j.asEnumerable(emps) // 将员工数组转换为可枚举集合
            .groupBy(EMP_DEPTNO_SELECTOR, EMP_NAME_SELECTOR, // 按部门编号分组，元素选择器选择员工姓名
                (key, group) -> String.format(Locale.ROOT, "%s: %s", key, // 结果选择器：格式化为 "部门编号:员工姓名列表"
                    stringJoin("+", group)), // 员工姓名用 "+" 连接
                new EqualityComparer<Integer>() { // 使用自定义比较器
                  public boolean equal(Integer v1, Integer v2) { // 相等比较方法
                    return true; // 所有整数都相等
                  }

                  public int hashCode(Integer integer) { // 哈希码计算方法
                    return 0; // 所有整数的哈希码都为 0
                  }
                })
            .toList() // 转换为列表
            .toString(); // 转换为字符串
    assertThat(s, is("[10: Fred+Bill+Eric+Janet]")); // 断言所有员工都分到同一组
  }

  @Test void testZip() { // 测试 zip 操作：将两个集合的元素按顺序配对
    final Enumerable<String> e1 = Linq4j.asEnumerable(Arrays.asList("a", "b", "c")); // 创建第一个可枚举集合
    final Enumerable<String> e2 = Linq4j.asEnumerable(Arrays.asList("1", "2", "3")); // 创建第二个可枚举集合

    final Enumerable<String> zipped = e1.zip(e2, (v0, v1) -> v0 + v1); // 将两个集合的元素配对并连接
    assertThat(zipped.count(), is(3)); // 断言结果包含 3 个元素
    zipped.enumerator().reset(); // 重置枚举器
    for (int i = 0; i < 3; i++) { // 遍历结果
      assertThat(zipped.elementAt(i), is("" + (char) ('a' + i) + (char) ('1' + i))); // 断言每个元素正确
    }
  }

  @Test void testZipLengthNotMatch() { // 测试 zip 操作处理长度不匹配的情况
    final Enumerable<String> e1 = Linq4j.asEnumerable(Arrays.asList("a", "b")); // 创建第一个可枚举集合（2 个元素）
    final Enumerable<String> e2 = Linq4j.asEnumerable(Arrays.asList("1", "2", "3")); // 创建第二个可枚举集合（3 个元素）

    final Function2<String, String, String> resultSelector = (v0, v1) -> v0 + v1; // 结果选择器：连接两个字符串

    final Enumerable<String> zipped1 = e1.zip(e2, resultSelector); // 将两个集合的元素配对（以较短的集合为准）
    assertThat(zipped1.count(), is(2)); // 断言结果包含 2 个元素
    assertThat(count(zipped1.enumerator()), is(2)); // 断言枚举器包含 2 个元素
    zipped1.enumerator().reset(); // 重置枚举器
    for (int i = 0; i < 2; i++) { // 遍历结果
      assertThat(zipped1.elementAt(i), is("" + (char) ('a' + i) + (char) ('1' + i))); // 断言每个元素正确
    }

    final Enumerable<String> zipped2 = e2.zip(e1, resultSelector); // 将两个集合的元素配对（以较短的集合为准）
    assertThat(zipped2.count(), is(2)); // 断言结果包含 2 个元素
    assertThat(count(zipped2.enumerator()), is(2)); // 断言枚举器包含 2 个元素
    zipped2.enumerator().reset(); // 重置枚举器
    for (int i = 0; i < 2; i++) { // 遍历结果
      assertThat(zipped2.elementAt(i), is("" + (char) ('1' + i) + (char) ('a' + i))); // 断言每个元素正确
    }
  }

  private static int count(Enumerator<String> enumerator) { // 计算枚举器中非 null 元素的数量
    int n = 0; // 初始化计数器
    while (enumerator.moveNext()) { // 遍历枚举器
      if (enumerator.current() != null) { // 如果当前元素不为 null
        ++n; // 递增计数器
      }
    }
    return n; // 返回计数
  }

  @Test void testExample() { // 测试示例程序
    Linq4jExample.main(new String[0]); // 运行 LINQ4J 示例程序
  }

  /** We use BigDecimal to represent literals of float and double using
   * BigDecimal, because we want an exact representation. */
  @Test void testApproxConstant() { // 测试常量表达式的创建和转换
    ConstantExpression c; // 创建常量表达式变量
    c = Expressions.constant(new BigDecimal("3.1"), float.class); // 创建 float 类型的常量表达式
    assertThat(Expressions.toString(c), equalTo("3.1F")); // 断言字符串表示为 "3.1F"
    c = Expressions.constant(new BigDecimal("-5.156"), float.class); // 创建 float 类型的常量表达式
    assertThat(Expressions.toString(c), equalTo("-5.156F")); // 断言字符串表示为 "-5.156F"
    c = Expressions.constant(new BigDecimal("-51.6"), Float.class); // 创建 Float 类型的常量表达式
    assertThat(Expressions.toString(c), equalTo("Float.valueOf(-51.6F)")); // 断言字符串表示为 "Float.valueOf(-51.6F)"
    c = Expressions.constant(new BigDecimal(Float.MAX_VALUE), Float.class); // 创建 Float 类型的常量表达式（最大值）
    assertThat(Expressions.toString(c), // 断言字符串表示正确
        equalTo("Float.valueOf(Float.intBitsToFloat(2139095039))"));
    c = Expressions.constant(new BigDecimal(Float.MIN_VALUE), Float.class); // 创建 Float 类型的常量表达式（最小值）
    assertThat(Expressions.toString(c), // 断言字符串表示正确
        equalTo("Float.valueOf(Float.intBitsToFloat(1))"));

    c = Expressions.constant(new BigDecimal("3.1"), double.class); // 创建 double 类型的常量表达式
    assertThat(Expressions.toString(c), equalTo("3.1D")); // 断言字符串表示为 "3.1D"
    c = Expressions.constant(new BigDecimal("-5.156"), double.class); // 创建 double 类型的常量表达式
    assertThat(Expressions.toString(c), equalTo("-5.156D")); // 断言字符串表示为 "-5.156D"
    c = Expressions.constant(new BigDecimal("-51.6"), Double.class); // 创建 Double 类型的常量表达式
    assertThat(Expressions.toString(c), equalTo("Double.valueOf(-51.6D)")); // 断言字符串表示为 "Double.valueOf(-51.6D)"
    c = Expressions.constant(new BigDecimal(Double.MAX_VALUE), Double.class); // 创建 Double 类型的常量表达式（最大值）
    assertThat(Expressions.toString(c), // 断言字符串表示正确
        equalTo("Double.valueOf(Double.longBitsToDouble(9218868437227405311L))"));
    c = Expressions.constant(new BigDecimal(Double.MIN_VALUE), Double.class); // 创建 Double 类型的常量表达式（最小值）
    assertThat(Expressions.toString(c), // 断言字符串表示正确
        equalTo("Double.valueOf(Double.longBitsToDouble(1L))"));
  }

  /** Employee. */ // 员工类：表示一个员工，包含员工编号、姓名和部门编号
  public static class Employee { // 员工类（静态内部类）
    public final int empno; // 员工编号（final 字段，不可修改）
    public final String name; // 员工姓名（final 字段，不可修改）
    public final int deptno; // 部门编号（final 字段，不可修改）

    public Employee(int empno, String name, int deptno) { // 员工类构造方法
      this.empno = empno; // 初始化员工编号
      this.name = name; // 初始化员工姓名
      this.deptno = deptno; // 初始化部门编号
    }

    public String toString() { // 重写 toString 方法，返回员工的字符串表示
      return "Employee(name: " + name + ", deptno:" + deptno + ")"; // 返回格式为 "Employee(name: 员工姓名, deptno:部门编号)" 的字符串
    }

    @Override public int hashCode() { // 重写 hashCode 方法，根据员工编号、姓名和部门编号计算哈希码
      final int prime = 31; // 质数，用于哈希码计算
      int result = 1; // 初始化结果
      result = prime * result + deptno; // 加上部门编号
      result = prime * result + empno; // 加上员工编号
      result = prime * result + ((name == null) ? 0 : name.hashCode()); // 加上姓名的哈希码（如果姓名不为 null）
      return result; // 返回哈希码
    }

    @Override public boolean equals(Object obj) { // 重写 equals 方法，比较两个员工是否相等
      if (this == obj) { // 如果是同一个对象
        return true; // 返回 true
      }
      if (obj == null) { // 如果对象为 null
        return false; // 返回 false
      }
      if (getClass() != obj.getClass()) { // 如果类型不同
        return false; // 返回 false
      }
      Employee other = (Employee) obj; // 将对象转换为 Employee 类型
      if (deptno != other.deptno) { // 如果部门编号不同
        return false; // 返回 false
      }
      if (empno != other.empno) { // 如果员工编号不同
        return false; // 返回 false
      }
      if (name == null) { // 如果姓名为 null
        if (other.name != null) { // 如果另一个对象的姓名不为 null
          return false; // 返回 false
        }
      } else if (!name.equals(other.name)) { // 如果姓名不同
        return false; // 返回 false
      }
      return true; // 所有字段都相同，返回 true
    }
  }

  /** Department. */ // 部门类：表示一个部门，包含部门名称、部门编号和员工列表
  public static class Department { // 部门类（静态内部类）
    public final String name; // 部门名称（final 字段，不可修改）
    public final int deptno; // 部门编号（final 字段，不可修改）
    public final List<Employee> employees; // 员工列表（final 字段，不可修改）

    public Department(String name, int deptno, List<Employee> employees) { // 部门类构造方法
      this.name = name; // 初始化部门名称
      this.deptno = deptno; // 初始化部门编号
      this.employees = employees; // 初始化员工列表
    }

    public String toString() { // 重写 toString 方法，返回部门的字符串表示
      return "Department(name: " + name // 返回格式为 "Department(name: 部门名称, deptno:部门编号, employees: 员工列表)" 的字符串
          + ", deptno:" + deptno
          + ", employees: " + employees
          + ")";
    }
  }

  // Cedric works in a non-existent department. // Cedric 在一个不存在的部门工作
  //CHECKSTYLE: IGNORE 1 // 忽略 Checkstyle 警告
  public static final Employee[] badEmps = { // 不存在部门的员工数组
      new Employee(140, "Cedric", 40), // 员工 Cedric，部门编号 40（不存在）
  };

  //CHECKSTYLE: IGNORE 1 // 忽略 Checkstyle 警告
  public static final Employee[] emps = { // 员工数组（测试数据）
      new Employee(100, "Fred", 10), // 员工 Fred，部门编号 10（Sales 部门）
      new Employee(110, "Bill", 30), // 员工 Bill，部门编号 30（Marketing 部门）
      new Employee(120, "Eric", 10), // 员工 Eric，部门编号 10（Sales 部门）
      new Employee(130, "Janet", 10), // 员工 Janet，部门编号 10（Sales 部门）
  };

  //CHECKSTYLE: IGNORE 1 // 忽略 Checkstyle 警告
  public static final Department[] depts = { // 部门数组（测试数据）
      new Department("Sales", 10, Arrays.asList(emps[0], emps[2], emps[3])), // Sales 部门，部门编号 10，包含 Fred、Eric、Janet
      new Department("HR", 20, ImmutableList.of()), // HR 部门，部门编号 20，没有员工
      new Department("Marketing", 30, ImmutableList.of(emps[1])), // Marketing 部门，部门编号 30，包含 Bill
  };
}
