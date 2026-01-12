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
package com.example; // 定义包名，表示这个类属于 com.example 包

import org.apache.calcite.linq4j.Linq4j; // 导入 Linq4j 核心工具类，用于将 Java 集合转换为可查询的 Enumerable 对象
import org.apache.calcite.linq4j.function.Function0; // 导入 Function0 接口，表示无参数的函数，用于延迟初始化或提供默认值
import org.apache.calcite.linq4j.function.Function1; // 导入 Function1 接口，表示接受一个参数的函数，用于数据转换和映射
import org.apache.calcite.linq4j.function.Functions; // 导入 Functions 工具类，提供常用的函数式操作辅助方法

/**
 * Linq4j 示例类，演示如何使用 linq4j 库对内存中的集合进行查询操作
 * 
 * Linq4j 是 Apache Calcite 项目中的一个组件，它将 .NET 的 LINQ（Language Integrated Query）概念引入到 Java 中
 * 允许开发者使用类似 SQL 的声明式语法来查询内存中的集合数据，提供了一种函数式编程的方式来处理集合
 * 
 * 这个示例展示了以下核心概念：
 * 1. 如何将 Java 数组转换为可查询的 Enumerable 对象
 * 2. 如何使用 groupBy 进行分组操作
 * 3. 如何使用 orderBy 进行排序操作
 * 4. 如何使用函数式接口（Function0、Function1）来定义转换逻辑
 * 
 * 主要应用场景：在不需要数据库的情况下，对内存中的数据集合进行复杂的查询、分组、聚合等操作
 */
public class Linq4jExample { // 定义 Linq4jExample 类，这是一个演示类，展示 linq4j 的基本用法
  private Linq4jExample() {} // 私有构造方法，防止实例化，因为这是一个工具类，所有方法都是静态的

  /** Employee 内部类，表示员工实体，包含员工的基本信息 */ // Employee 类的 JavaDoc 注释，说明这是一个员工类
  public static class Employee { // 定义 Employee 内部静态类，表示员工数据模型
    public final int empno; // 员工编号，使用 final 修饰表示一旦赋值就不能修改，保证员工编号的不可变性
    public final String name; // 员工姓名，使用 final 修饰保证姓名的不可变性
    public final int deptno; // 部门编号，使用 final 修饰保证部门编号的不可变性，用于后续分组操作

    public Employee(int empno, String name, int deptno) { // Employee 的构造方法，用于创建员工对象
      this.empno = empno; // 将参数 empno 赋值给成员变量 empno，初始化员工编号
      this.name = name; // 将参数 name 赋值给成员变量 name，初始化员工姓名
      this.deptno = deptno; // 将参数 deptno 赋值给成员变量 deptno，初始化部门编号
    }

    public String toString() { // 重写 toString 方法，用于返回 Employee 对象的字符串表示
      return "Employee(name: " + name + ", deptno:" + deptno + ")"; // 返回包含员工姓名和部门编号的字符串格式
    }
  }

  public static final Employee[] EMPS = { // 定义员工数组 EMPS，这是一个静态常量数组，存储所有员工数据
      new Employee(100, "Fred", 10), // 创建第一个员工对象，员工编号 100，姓名 Fred，部门编号 10
      new Employee(110, "Bill", 30), // 创建第二个员工对象，员工编号 110，姓名 Bill，部门编号 30
      new Employee(120, "Eric", 10), // 创建第三个员工对象，员工编号 120，姓名 Eric，部门编号 10
      new Employee(130, "Janet", 10), // 创建第四个员工对象，员工编号 130，姓名 Janet，部门编号 10
  }; // 数组初始化结束，共 4 个员工，其中 3 个在部门 10，1 个在部门 30

  public static final Function1<Employee, Integer> EMP_DEPTNO_SELECTOR = // 定义一个函数式接口实例，用于从 Employee 对象中提取部门编号
      employee -> employee.deptno; // Lambda 表达式实现，接受一个 Employee 对象，返回其 deptno 属性（部门编号）

  public static void main(String[] args) { // 主方法，程序的入口点，演示 linq4j 的分组和排序功能
    String s = Linq4j.asEnumerable(EMPS) // 将 EMPS 数组转换为 Enumerable 对象，使其支持 LINQ 风格的查询操作
        .groupBy( // 对员工数据进行分组操作，按照部门编号进行分组
            EMP_DEPTNO_SELECTOR, // 分组键选择器，使用前面定义的函数提取每个员工的部门编号作为分组依据
            (Function0<String>) () -> null, // 初始值提供者（Function0），为每个分组创建初始值，这里返回 null 表示累加器初始为 null
            (v1, e0) -> v1 == null ? e0.name : (v1 + "+" + e0.name), // 累加器函数，将员工姓名连接起来：如果累加器 v1 为 null，则使用当前员工姓名；否则用 "+" 连接
            (v1, v2) -> v1 + ": " + v2) // 结果选择器，将分组键和累加结果组合成最终格式，格式为 "部门编号: 姓名列表"
        .orderBy(Functions.identitySelector()) // 对分组结果进行排序，使用 identitySelector 表示按元素本身（即分组字符串）排序
        .toList() // 将排序后的 Enumerable 转换为 List 集合
        .toString(); // 将 List 转换为字符串表示，用于后续断言验证
    assert s.equals("[10: Fred+Eric+Janet, 30: Bill]"); // 断言验证结果是否正确：部门 10 的员工姓名连接为 Fred+Eric+Janet，部门 30 的员工为 Bill
  } // main 方法结束
} // Linq4jExample 类结束
