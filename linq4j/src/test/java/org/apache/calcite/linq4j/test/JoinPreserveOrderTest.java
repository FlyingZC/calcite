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
// Apache许可证声明，允许在特定条件下使用和修改代码
package org.apache.calcite.linq4j.test; // 定义包名，该测试类位于org.apache.calcite.linq4j.test包下

import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，表示可枚举的序列
import org.apache.calcite.linq4j.EnumerableDefaults; // 导入EnumerableDefaults类，提供Enumerable的默认扩展方法
import org.apache.calcite.linq4j.JoinType; // 导入JoinType枚举，定义连接类型（INNER、LEFT、RIGHT、FULL等）
import org.apache.calcite.linq4j.Linq4j; // 导入Linq4j工具类，提供LINQ操作的核心方法
import org.apache.calcite.linq4j.function.Function1; // 导入Function1函数式接口，表示接受一个参数的函数
import org.apache.calcite.linq4j.function.Function2; // 导入Function2函数式接口，表示接受两个参数的函数

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可空的类型
import org.junit.jupiter.params.ParameterizedTest; // 导入ParameterizedTest注解，用于参数化测试
import org.junit.jupiter.params.provider.Arguments; // 导入Arguments类，用于提供测试参数
import org.junit.jupiter.params.provider.MethodSource; // 导入MethodSource注解，用于指定参数化测试的数据源方法

import java.util.ArrayList; // 导入ArrayList类，动态数组实现
import java.util.Arrays; // 导入Arrays工具类，提供数组操作方法
import java.util.List; // 导入List接口，表示有序集合
import java.util.Objects; // 导入Objects工具类，提供对象操作方法
import java.util.stream.Stream; // 导入Stream接口，支持函数式编程的流式操作

import static org.apache.calcite.linq4j.function.Functions.nullsComparator; // 静态导入nullsComparator方法，用于处理null值的比较

import static org.junit.jupiter.api.Assertions.assertFalse; // 静态导入assertFalse断言方法
import static org.junit.jupiter.api.Assertions.assertTrue; // 静态导入assertTrue断言方法

/**
 * Test validating the order preserving properties of join algorithms in
 * {@link org.apache.calcite.linq4j.ExtendedEnumerable}. The correctness of the
 * join algorithm is not examined by this set of tests.
 * 测试验证{@link org.apache.calcite.linq4j.ExtendedEnumerable}中连接算法的顺序保持特性。
 * 本测试集不检查连接算法的正确性。
 *
 * <p>To verify that the order of left/right/both input(s) is preserved they
 * must be all ordered by at least one column. The inputs are either sorted on
 * the join or some other column. For the tests to be meaningful the result of the
 * join must not be empty.
 * 为了验证左/右/两侧输入的顺序是否被保留，它们必须至少按一列排序。输入可以在连接列或其他列上排序。
 * 为了使测试有意义，连接的结果不能为空。
 *
 * <p>Interesting variants that may affect the join output and thus destroy the
 * order of one or both inputs is when the join column or the sorted column
 * (when join column != sort column) contain nulls or duplicate values.
 * 可能影响连接输出从而破坏一个或两个输入顺序的有趣变体是：当连接列或排序列（当连接列!=排序列时）
 * 包含null值或重复值时。
 *
 * <p>In addition, the way that nulls are sorted before the join can also play
 * an important role regarding the order preserving semantics of the join.
 * 此外，连接前null值的排序方式也会对连接的顺序保持语义产生重要影响。
 *
 * <p>Last but not least, the type of the join (left/right/full/inner/semi/anti)
 * has a major impact on the preservation of order for the various joins.
 * 最后但同样重要的是，连接的类型（左/右/全/内/半/反）对各种连接的顺序保持有重大影响。
 */
public final class JoinPreserveOrderTest { // 定义最终类JoinPreserveOrderTest，用于测试连接算法的顺序保持特性，final表示不能被继承

  /**
   * A description holding which column must be sorted and how.
   * 一个描述，保存哪个列必须被排序以及如何排序。
   *
   * @param <T> the type of the input relation // 泛型参数T，表示输入关系的类型
   */
  private static class Field<T> { // 定义私有静态内部类Field<T>，用于描述列的排序属性
    private final String colName; // 列名，表示要排序的列的名称
    private final Function1<T, Comparable> colSelector; // 列选择器函数，用于从对象T中提取可比较的值
    private final boolean isAscending; // 是否升序排序，true表示升序，false表示降序
    private final boolean isNullsFirst; // 是否null值排在前面，true表示null值排在最前面

    Field(String colName, // Field构造方法，初始化列的排序属性
          Function1<T, Comparable> colSelector, // 参数：列选择器函数
          boolean isAscending, // 参数：是否升序
          boolean isNullsFirst) { // 参数：是否null值优先
      this.colName = colName; // 保存列名
      this.colSelector = colSelector; // 保存列选择器
      this.isAscending = isAscending; // 保存升序标志
      this.isNullsFirst = isNullsFirst; // 保存null值优先标志
    }

    @Override public String toString() { // 重写toString方法，返回Field对象的字符串表示
      return "on='" + colName + "', asc=" + isAscending + ", nullsFirst=" + isNullsFirst + '}'; // 返回格式化的字符串，包含列名、升序标志和null值优先标志
    }
  }

  /**
   * An abstraction for a join algorithm which performs an operation on two inputs and produces a
   * result.
   * 连接算法的抽象，对两个输入执行操作并产生结果。
   *
   * @param <L> the type of the left input // 泛型参数L，表示左输入的类型
   * @param <R> the type of the right input // 泛型参数R，表示右输入的类型
   * @param <Result> the type of the result // 泛型参数Result，表示结果的类型
   */
  private interface JoinAlgorithm<L, R, Result> { // 定义私有接口JoinAlgorithm<L,R,Result>，表示连接算法的抽象
    Enumerable<Result> join(Enumerable<L> left, Enumerable<R> right); // 连接方法，接受左右两个输入，返回连接后的结果
  }

  private Field<Employee> leftColumn; // 左侧列的排序描述，用于描述Employee表的排序列
  private Field<Department> rightColumn; // 右侧列的排序描述，用于描述Department表的排序列
  private static final Function2<Employee, Department, List<Integer>> RESULT_SELECTOR = // 结果选择器函数，用于从Employee和Department对象中提取ID列表
      (emp, dept) -> Arrays.asList( // Lambda表达式，将Employee和Department转换为包含两个ID的列表
          (emp != null) ? emp.eid : null, // 如果Employee不为null，则使用其eid，否则使用null
          (dept != null) ? dept.did : null); // 如果Department不为null，则使用其did，否则使用null

  public static Stream<Arguments> data() { // 静态方法，生成参数化测试的数据源，返回Arguments流
    List<Arguments> data = new ArrayList<>(); // 创建Arguments列表，用于存储所有测试参数组合
    List<String> empOrderColNames = Arrays.asList("name", "deptno", "eid"); // Employee表的可排序列名列表
    List<Function1<Employee, Comparable>> empOrderColSelectors = // Employee表的列选择器函数列表
        Arrays.asList(Employee::getName, // Employee::getName，选择name列
            Employee::getDeptno, // Employee::getDeptno，选择deptno列
            Employee::getEid); // Employee::getEid，选择eid列
    List<String> deptOrderColNames = Arrays.asList("name", "deptno", "did"); // Department表的可排序列名列表
    List<Function1<Department, Comparable>> deptOrderColSelectors = // Department表的列选择器函数列表
        Arrays.asList(Department::getName, // Department::getName，选择name列
            Department::getDeptno, // Department::getDeptno，选择deptno列
            Department::getDid); // Department::getDid，选择did列
    List<Boolean> trueFalse = Arrays.asList(true, false); // 布尔值列表，用于遍历升序和降序、null值优先和不优先的情况
    for (int i = 0; i < empOrderColNames.size(); i++) { // 遍历Employee表的所有排序列
      for (Boolean ascendingL : trueFalse) { // 遍历左输入的升序/降序选项
        for (Boolean nullsFirstL : trueFalse) { // 遍历左输入的null值优先/不优先选项
          for (int j = 0; j < deptOrderColNames.size(); j++) { // 遍历Department表的所有排序列
            for (Boolean nullsFirstR : trueFalse) { // 遍历右输入的null值优先/不优先选项
              for (Boolean ascendingR : trueFalse) { // 遍历右输入的升序/降序选项
                Object[] params = new Object[2]; // 创建参数数组，包含两个Field对象
                params[0] = // 设置第一个参数为左侧列的排序描述
                    new Field<>(empOrderColNames.get(i), // 使用Employee表的第i个列名
                        empOrderColSelectors.get(i), // 使用对应的列选择器
                        ascendingL, // 使用左输入的升序标志
                        nullsFirstL); // 使用左输入的null值优先标志
                params[1] = // 设置第二个参数为右侧列的排序描述
                    new Field<>(deptOrderColNames.get(j), // 使用Department表的第j个列名
                        deptOrderColSelectors.get(j), // 使用对应的列选择器
                        ascendingR, // 使用右输入的升序标志
                        nullsFirstR); // 使用右输入的null值优先标志
                data.add(Arguments.of(params[0], params[1])); // 将参数组合添加到数据列表中
              }
            }
          }
        }
      }
    }
    return data.stream(); // 返回数据列表的流，作为参数化测试的数据源
  }

  public static Stream<Arguments> noNullsFirstOnLeft() { // 静态方法，生成左输入不将null值排在第一的测试数据
    //noinspection unchecked // 忽略未检查的类型转换警告
    return data().filter(x -> !((Field<Employee>) x.get()[0]).isNullsFirst); // 过滤data()方法的结果，只保留左侧列的isNullsFirst为false的参数组合
  }

  private void initColumns(Field<Employee> left, Field<Department> right) { // 初始化列的排序描述方法
    this.leftColumn = left; // 保存左侧列的排序描述
    this.rightColumn = right; // 保存右侧列的排序描述
  }

  @ParameterizedTest // 参数化测试注解
  @MethodSource("data") // 指定数据源方法为data()
  void leftJoinPreservesOrderOfLeftInput(Field<Employee> left, Field<Department> right) { // 测试左连接是否保持左输入的顺序
    initColumns(left, right); // 初始化列的排序描述
    testJoin(hashJoin(false, true), AssertOrder.PRESERVED, AssertOrder.IGNORED); // 执行测试，使用hashJoin算法（左连接），验证左输入顺序保持，忽略右输入顺序
  }

  @ParameterizedTest // 参数化测试注解
  @MethodSource("noNullsFirstOnLeft") // 指定数据源方法为noNullsFirstOnLeft()，左输入null值不排在第一
  void rightJoinPreservesOrderOfLeftInput(Field<Employee> left, Field<Department> right) { // 测试右连接是否保持左输入的顺序
    initColumns(left, right); // 初始化列的排序描述
    testJoin(hashJoin(true, false), AssertOrder.PRESERVED, AssertOrder.IGNORED); // 执行测试，使用hashJoin算法（右连接），验证左输入顺序保持，忽略右输入顺序
  }

  @ParameterizedTest // 参数化测试注解
  @MethodSource("noNullsFirstOnLeft") // 指定数据源方法为noNullsFirstOnLeft()，左输入null值不排在第一
  void fullJoinPreservesOrderOfLeftInput(Field<Employee> left, Field<Department> right) { // 测试全连接是否保持左输入的顺序
    initColumns(left, right); // 初始化列的排序描述
    testJoin(hashJoin(true, true), AssertOrder.PRESERVED, AssertOrder.IGNORED); // 执行测试，使用hashJoin算法（全连接），验证左输入顺序保持，忽略右输入顺序
  }

  @ParameterizedTest // 参数化测试注解
  @MethodSource("data") // 指定数据源方法为data()
  void innerJoinPreservesOrderOfLeftInput(Field<Employee> left, Field<Department> right) { // 测试内连接是否保持左输入的顺序
    initColumns(left, right); // 初始化列的排序描述
    testJoin(hashJoin(false, false), AssertOrder.PRESERVED, AssertOrder.IGNORED); // 执行测试，使用hashJoin算法（内连接），验证左输入顺序保持，忽略右输入顺序
  }

  @ParameterizedTest // 参数化测试注解
  @MethodSource("data") // 指定数据源方法为data()
  void leftNestedLoopJoinPreservesOrderOfLeftInput(Field<Employee> left, Field<Department> right) { // 测试嵌套循环左连接是否保持左输入的顺序
    initColumns(left, right); // 初始化列的排序描述
    testJoin(nestedLoopJoin(JoinType.LEFT), AssertOrder.PRESERVED, AssertOrder.IGNORED); // 执行测试，使用nestedLoopJoin算法（左连接），验证左输入顺序保持，忽略右输入顺序
  }

  @ParameterizedTest // 参数化测试注解
  @MethodSource("noNullsFirstOnLeft") // 指定数据源方法为noNullsFirstOnLeft()，左输入null值不排在第一
  void rightNestedLoopJoinPreservesOrderOfLeftInput(Field<Employee> left, Field<Department> right) { // 测试嵌套循环右连接是否保持左输入的顺序
    initColumns(left, right); // 初始化列的排序描述
    testJoin(nestedLoopJoin(JoinType.RIGHT), AssertOrder.PRESERVED, AssertOrder.IGNORED); // 执行测试，使用nestedLoopJoin算法（右连接），验证左输入顺序保持，忽略右输入顺序
  }

  @ParameterizedTest // 参数化测试注解
  @MethodSource("noNullsFirstOnLeft") // 指定数据源方法为noNullsFirstOnLeft()，左输入null值不排在第一
  void fullNestedLoopJoinPreservesOrderOfLeftInput(Field<Employee> left, Field<Department> right) { // 测试嵌套循环全连接是否保持左输入的顺序
    initColumns(left, right); // 初始化列的排序描述
    testJoin(nestedLoopJoin(JoinType.FULL), AssertOrder.PRESERVED, AssertOrder.IGNORED); // 执行测试，使用nestedLoopJoin算法（全连接），验证左输入顺序保持，忽略右输入顺序
  }

  @ParameterizedTest // 参数化测试注解
  @MethodSource("data") // 指定数据源方法为data()
  void innerNestedLoopJoinPreservesOrderOfLeftInput(Field<Employee> left, Field<Department> right) { // 测试嵌套循环内连接是否保持左输入的顺序
    initColumns(left, right); // 初始化列的排序描述
    testJoin(nestedLoopJoin(JoinType.INNER), AssertOrder.PRESERVED, AssertOrder.IGNORED); // 执行测试，使用nestedLoopJoin算法（内连接），验证左输入顺序保持，忽略右输入顺序
  }

  @ParameterizedTest // 参数化测试注解
  @MethodSource("data") // 指定数据源方法为data()
  void leftCorrelateJoinPreservesOrderOfLeftInput(Field<Employee> left, Field<Department> right) { // 测试相关左连接是否保持左输入的顺序
    initColumns(left, right); // 初始化列的排序描述
    testJoin(correlateJoin(JoinType.LEFT), AssertOrder.PRESERVED, AssertOrder.IGNORED); // 执行测试，使用correlateJoin算法（左连接），验证左输入顺序保持，忽略右输入顺序
  }

  @ParameterizedTest // 参数化测试注解
  @MethodSource("data") // 指定数据源方法为data()
  void innerCorrelateJoinPreservesOrderOfLeftInput(Field<Employee> left, Field<Department> right) { // 测试相关内连接是否保持左输入的顺序
    initColumns(left, right); // 初始化列的排序描述
    testJoin(correlateJoin(JoinType.INNER), AssertOrder.PRESERVED, AssertOrder.IGNORED); // 执行测试，使用correlateJoin算法（内连接），验证左输入顺序保持，忽略右输入顺序
  }

  @ParameterizedTest // 参数化测试注解
  @MethodSource("data") // 指定数据源方法为data()
  void antiCorrelateJoinPreservesOrderOfLeftInput(Field<Employee> left, Field<Department> right) { // 测试相关反连接是否保持左输入的顺序
    initColumns(left, right); // 初始化列的排序描述
    testJoin(correlateJoin(JoinType.ANTI), AssertOrder.PRESERVED, AssertOrder.IGNORED); // 执行测试，使用correlateJoin算法（反连接），验证左输入顺序保持，忽略右输入顺序
  }

  @ParameterizedTest // 参数化测试注解
  @MethodSource("data") // 指定数据源方法为data()
  void semiCorrelateJoinPreservesOrderOfLeftInput(Field<Employee> left, Field<Department> right) { // 测试相关半连接是否保持左输入的顺序
    initColumns(left, right); // 初始化列的排序描述
    testJoin(correlateJoin(JoinType.SEMI), AssertOrder.PRESERVED, AssertOrder.IGNORED); // 执行测试，使用correlateJoin算法（半连接），验证左输入顺序保持，忽略右输入顺序
  }

  @ParameterizedTest // 参数化测试注解
  @MethodSource("data") // 指定数据源方法为data()
  void semiDefaultJoinPreservesOrderOfLeftInput(Field<Employee> left, Field<Department> right) { // 测试默认半连接是否保持左输入的顺序
    initColumns(left, right); // 初始化列的排序描述
    testJoin(semiJoin(), AssertOrder.PRESERVED, AssertOrder.IGNORED); // 执行测试，使用semiJoin算法，验证左输入顺序保持，忽略右输入顺序
  }

  @ParameterizedTest // 参数化测试注解
  @MethodSource("data") // 指定数据源方法为data()
  void correlateBatchJoin(Field<Employee> left, Field<Department> right) { // 测试批量相关连接
    initColumns(left, right); // 初始化列的排序描述
    testJoin( // 执行测试
        correlateBatchJoin(JoinType.INNER), // 使用correlateBatchJoin算法（内连接）
        AssertOrder.PRESERVED, // 验证左输入顺序保持
        AssertOrder.IGNORED); // 忽略右输入顺序
  }

  @ParameterizedTest // 参数化测试注解
  @MethodSource("data") // 指定数据源方法为data()
  void antiDefaultJoinPreservesOrderOfLeftInput(Field<Employee> left, Field<Department> right) { // 测试默认反连接是否保持左输入的顺序
    initColumns(left, right); // 初始化列的排序描述
    testJoin(antiJoin(), AssertOrder.PRESERVED, AssertOrder.IGNORED); // 执行测试，使用antiJoin算法，验证左输入顺序保持，忽略右输入顺序
  }

  private void testJoin( // 测试连接方法的通用实现
      JoinAlgorithm<Employee, Department, List<Integer>> joinAlgorithm, // 参数：连接算法
      AssertOrder assertLeftInput, // 参数：左输入的顺序断言类型
      AssertOrder assertRightInput) { // 参数：右输入的顺序断言类型
    Enumerable<Employee> left = // 创建左侧输入的可枚举集合
        Linq4j.asEnumerable(EMPS) // 将Employee数组转换为可枚举集合
            .orderBy(leftColumn.colSelector, // 按左侧列的值排序
                nullsComparator(leftColumn.isNullsFirst, !leftColumn.isAscending)); // 使用null值比较器，考虑null值优先和升序/降序
    Enumerable<Department> right = // 创建右侧输入的可枚举集合
        Linq4j.asEnumerable(DEPTS) // 将Department数组转换为可枚举集合
            .orderBy(rightColumn.colSelector, // 按右侧列的值排序
                nullsComparator(rightColumn.isNullsFirst, !rightColumn.isAscending)); // 使用null值比较器，考虑null值优先和升序/降序
    Enumerable<List<Integer>> joinResult = joinAlgorithm.join(left, right); // 执行连接操作，得到结果

    List<Integer> actualIdOrderLeft = joinResult.select(joinTuple -> joinTuple.get(0)).toList(); // 提取连接结果中的左输入ID列表
    List<Integer> expectedIdOrderLeft = left.select(e -> e.eid).toList(); // 提取左输入的原始ID列表作为期望值
    assertLeftInput.check(expectedIdOrderLeft, actualIdOrderLeft, leftColumn.isNullsFirst); // 验证左输入的顺序是否保持
    List<Integer> actualIdOrderRight = joinResult.select(joinTuple -> joinTuple.get(1)).toList(); // 提取连接结果中的右输入ID列表
    List<Integer> expectedIdOrderRight = right.select(d -> d.did).toList(); // 提取右输入的原始ID列表作为期望值
    assertRightInput.check(expectedIdOrderRight, actualIdOrderRight, rightColumn.isNullsFirst); // 验证右输入的顺序是否保持
  }

  private JoinAlgorithm<Employee, Department, List<Integer>> correlateJoin( // 创建相关连接算法
      JoinType joinType) { // 参数：连接类型
    return (left, right) -> // 返回一个Lambda表达式，接受左右输入
        left.correlateJoin( // 调用左侧的correlateJoin方法
            joinType, // 连接类型
            emp -> right.where(dept -> // 对每个Employee，筛选满足条件的Department
                emp.deptno != null // 检查Employee的deptno不为null
                    && dept.deptno != null // 检查Department的deptno不为null
                    && emp.deptno.equals(dept.deptno)), // 检查Employee和Department的deptno相等
            RESULT_SELECTOR); // 结果选择器，用于生成输出
  }

  private JoinAlgorithm<Employee, Department, List<Integer>> hashJoin( // 创建哈希连接算法
      boolean generateNullsOnLeft, // 参数：是否在左侧生成null值（用于外连接）
      boolean generateNullsOnRight) { // 参数：是否在右侧生成null值（用于外连接）
    return (left, right) -> // 返回一个Lambda表达式，接受左右输入
        left.hashJoin(right, // 调用左侧的hashJoin方法
            e -> e.deptno, // 左侧键选择器，提取Employee的deptno
            d -> d.deptno, // 右侧键选择器，提取Department的deptno
            RESULT_SELECTOR, // 结果选择器，用于生成输出
            null, // 比较器，使用默认的equals比较
            generateNullsOnLeft, // 是否在左侧生成null值
            generateNullsOnRight); // 是否在右侧生成null值
  }

  private JoinAlgorithm<Employee, Department, List<Integer>> nestedLoopJoin(JoinType joinType) { // 创建嵌套循环连接算法
    return (left, right) -> // 返回一个Lambda表达式，接受左右输入
        EnumerableDefaults.nestedLoopJoin( // 调用静态方法nestedLoopJoin
            left, // 左输入
            right, // 右输入
            (emp, dept) -> // 连接谓词，判断是否满足连接条件
                emp.deptno != null && dept.deptno != null && emp.deptno.equals(dept.deptno), // 两个deptno都不为null且相等
            RESULT_SELECTOR, // 结果选择器，用于生成输出
            joinType); // 连接类型
  }

  private JoinAlgorithm<Employee, Department, List<Integer>> semiJoin() { // 创建半连接算法
    return (left, right) -> // 返回一个Lambda表达式，接受左右输入
        EnumerableDefaults.semiJoin( // 调用静态方法semiJoin
            left, // 左输入
            right, // 右输入
            emp -> emp.deptno, // 左侧键选择器，提取Employee的deptno
            dept -> dept.deptno).select(emp -> Arrays.asList(emp.eid, null)); // 右侧键选择器，提取Department的deptno，然后选择结果，只保留Employee的eid，右侧为null
  }

  private JoinAlgorithm<Employee, Department, List<Integer>> antiJoin() { // 创建反连接算法
    return (left, right) -> // 返回一个Lambda表达式，接受左右输入
        EnumerableDefaults.antiJoin( // 调用静态方法antiJoin
            left, // 左输入
            right, // 右输入
            emp -> emp.deptno, // 左侧键选择器，提取Employee的deptno
            dept -> dept.deptno).select(emp -> Arrays.asList(emp.eid, null)); // 右侧键选择器，提取Department的deptno，然后选择结果，只保留Employee的eid，右侧为null
  }

  private JoinAlgorithm<Employee, Department, List<Integer>> correlateBatchJoin( // 创建批量相关连接算法
      JoinType joinType) { // 参数：连接类型
    return (left, right) -> // 返回一个Lambda表达式，接受左右输入
        EnumerableDefaults.correlateBatchJoin( // 调用静态方法correlateBatchJoin
            joinType, // 连接类型
            left, // 左输入
            emp -> right.where(dept -> // 对每个Employee，筛选满足条件的Department
                    dept.deptno != null // 检查Department的deptno不为null
                        && (dept.deptno.equals(emp.get(0).deptno) // 检查Department的deptno等于第一个Employee的deptno
                        || dept.deptno.equals(emp.get(1).deptno) // 或者等于第二个Employee的deptno
                        || dept.deptno.equals(emp.get(2).deptno))), // 或者等于第三个Employee的deptno
            RESULT_SELECTOR, // 结果选择器，用于生成输出
            (emp, dept) -> Objects.equals(dept.deptno, emp.deptno), // 谓词，判断Employee和Department的deptno是否相等
             3); // 批量大小，每次处理3个Employee
  }

  /**
   * Different assertions for the result of the join.
   * 连接结果的不同断言。
   */
  private enum AssertOrder { // 定义私有枚举AssertOrder，表示对连接结果顺序的断言类型
    PRESERVED { // PRESERVED枚举值，表示顺序应该被保持
      @Override <E> void check(final List<E> expected, final List<E> actual, // 重写check方法
          final boolean nullsFirst) { // 参数：期望列表、实际列表、null值是否优先
        assertTrue(isOrderPreserved(expected, actual, nullsFirst), // 断言顺序被保持
            () -> "Order is not preserved. Expected:<" + expected + "> but was:<" + actual + ">"); // 如果断言失败，输出错误信息
      }
    },
    DESTROYED { // DESTROYED枚举值，表示顺序应该被破坏
      @Override <E> void check(final List<E> expected, final List<E> actual, // 重写check方法
          final boolean nullsFirst) { // 参数：期望列表、实际列表、null值是否优先
        assertFalse(isOrderPreserved(expected, actual, nullsFirst), // 断言顺序被破坏
            () -> "Order is not destroyed. Expected:<" + expected + "> but was:<" + actual + ">"); // 如果断言失败，输出错误信息
      }
    },
    IGNORED { // IGNORED枚举值，表示忽略顺序检查
      @Override <E> void check(final List<E> expected, final List<E> actual, // 重写check方法
          final boolean nullsFirst) { // 参数：期望列表、实际列表、null值是否优先
        // Do nothing // 不做任何操作
      }
    };

    abstract <E> void check(List<E> expected, List<E> actual, boolean nullsFirst); // 抽象方法，检查顺序，由枚举值实现

    /**
     * Checks that the elements in the list are in the expected order.
     * 检查列表中的元素是否按期望的顺序排列。
     */
    <E> boolean isOrderPreserved(List<E> expected, List<E> actual, boolean nullsFirst) { // 判断顺序是否保持的方法
      boolean isPreserved = true; // 初始化顺序保持标志为true
      for (int i = 1; i < actual.size(); i++) { // 遍历实际列表，从第二个元素开始
        E prev = actual.get(i - 1); // 获取前一个元素
        E next = actual.get(i); // 获取当前元素
        int posPrev = prev == null ? (nullsFirst ? -1 : actual.size()) : expected.indexOf(prev); // 计算前一个元素在期望列表中的位置，如果是null则根据nullsFirst决定位置
        int posNext = next == null ? (nullsFirst ? -1 : actual.size()) : expected.indexOf(next); // 计算当前元素在期望列表中的位置，如果是null则根据nullsFirst决定位置
        isPreserved &= posPrev <= posNext; // 检查前一个元素的位置是否小于等于当前元素的位置，更新顺序保持标志
      }
      return isPreserved; // 返回顺序保持标志
    }
  }

  /** Department. */ // 部门类
  private static class Department { // 定义私有静态内部类Department，表示部门
    private final int did; // 部门ID，唯一标识一个部门
    private final @Nullable Integer deptno; // 部门编号，可为null，用于连接
    private final @Nullable String name; // 部门名称，可为null

    Department(final int did, final @Nullable Integer deptno, // Department构造方法
        final @Nullable String name) { // 参数：部门ID、部门编号、部门名称
      this.did = did; // 保存部门ID
      this.deptno = deptno; // 保存部门编号
      this.name = name; // 保存部门名称
    }

    int getDid() { // 获取部门ID的方法
      return did; // 返回部门ID
    }

    @Nullable Integer getDeptno() { // 获取部门编号的方法
      return deptno; // 返回部门编号
    }

    @Nullable String getName() { // 获取部门名称的方法
      return name; // 返回部门名称
    }
  }

  /** Employee. */ // 员工类
  private static class Employee { // 定义私有静态内部类Employee，表示员工
    private final int eid; // 员工ID，唯一标识一个员工
    private final @Nullable String name; // 员工姓名，可为null
    private final @Nullable Integer deptno; // 员工所属部门编号，可为null，用于连接

    Employee(final int eid, final @Nullable String name, // Employee构造方法
        final @Nullable Integer deptno) { // 参数：员工ID、员工姓名、部门编号
      this.eid = eid; // 保存员工ID
      this.name = name; // 保存员工姓名
      this.deptno = deptno; // 保存部门编号
    }

    int getEid() { // 获取员工ID的方法
      return eid; // 返回员工ID
    }

    @Nullable String getName() { // 获取员工姓名的方法
      return name; // 返回员工姓名
    }

    @Nullable Integer getDeptno() { // 获取员工部门编号的方法
      return deptno; // 返回部门编号
    }

    @Override public String toString() { // 重写toString方法，返回Employee对象的字符串表示
      return "Employee{eid=" + eid + ", name='" + name + '\'' + ", deptno=" + deptno + '}'; // 返回格式化的字符串，包含员工ID、姓名和部门编号
    }
  }

  private static final Employee[] EMPS = { // 静态常量Employee数组，包含测试用的员工数据
      new Employee(100, "Stam", 10), // 员工100：姓名Stam，部门编号10
      new Employee(110, "Greg", 20), // 员工110：姓名Greg，部门编号20
      new Employee(120, "Ilias", 30), // 员工120：姓名Ilias，部门编号30
      new Employee(130, "Ruben", 40), // 员工130：姓名Ruben，部门编号40
      new Employee(140, "Tanguy", 50), // 员工140：姓名Tanguy，部门编号50
      new Employee(145, "Khawla", 40), // 员工145：姓名Khawla，部门编号40（与员工130相同，测试重复值）
      new Employee(150, "Andrew", -10), // 员工150：姓名Andrew，部门编号-10（负数）
      // Nulls on name // 测试姓名为null的情况
      new Employee(160, null, 60), // 员工160：姓名为null，部门编号60
      new Employee(170, null, -60), // 员工170：姓名为null，部门编号-60
      // Nulls on deptno // 测试部门编号为null的情况
      new Employee(180, "Achille", null), // 员工180：姓名Achille，部门编号为null
      // Duplicate values on name // 测试姓名重复的情况
      new Employee(190, "Greg", 70), // 员工190：姓名Greg（与员工110相同），部门编号70
      new Employee(200, "Ilias", -70), // 员工200：姓名Ilias（与员工120相同），部门编号-70
      // Duplicates values on deptno // 测试部门编号重复的情况
      new Employee(210, "Sophia", 40), // 员工210：姓名Sophia，部门编号40（与员工130、145相同）
      new Employee(220, "Alexia", -40), // 员工220：姓名Alexia，部门编号-40
      new Employee(230, "Loukia", -40) // 员工230：姓名Loukia，部门编号-40（与员工220相同）
  };

  private static final Department[] DEPTS = { // 静态常量Department数组，包含测试用的部门数据
      new Department(1, 10, "Sales"), // 部门1：部门编号10，名称Sales
      new Department(2, 20, "Pre-sales"), // 部门2：部门编号20，名称Pre-sales
      new Department(4, 40, "Support"), // 部门4：部门编号40，名称Support
      new Department(5, 50, "Marketing"), // 部门5：部门编号50，名称Marketing
      new Department(6, 60, "Engineering"), // 部门6：部门编号60，名称Engineering
      new Department(7, 70, "Management"), // 部门7：部门编号70，名称Management
      new Department(8, 80, "HR"), // 部门8：部门编号80，名称HR
      new Department(9, 90, "Product design"), // 部门9：部门编号90，名称Product design
      // Nulls on name // 测试名称为null的情况
      new Department(3, 30, null), // 部门3：部门编号30，名称为null
      new Department(10, 100, null), // 部门10：部门编号100，名称为null
      // Nulls on deptno // 测试部门编号为null的情况
      new Department(11, null, "Post-sales"), // 部门11：部门编号为null，名称Post-sales
      // Duplicate values on name // 测试名称重复的情况
      new Department(12, 50, "Support"), // 部门12：部门编号50，名称Support（与部门4相同）
      new Department(13, 140, "Support"), // 部门13：部门编号140，名称Support（与部门4、12相同）
      // Duplicate values on deptno // 测试部门编号重复的情况
      new Department(14, 20, "Board"), // 部门14：部门编号20（与部门2相同），名称Board
      new Department(15, 40, "Promotions"), // 部门15：部门编号40（与部门4相同），名称Promotions
  };

}