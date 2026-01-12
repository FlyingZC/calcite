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
package org.apache.calcite.test.schemata.hr; // 定义包名，该类位于org.apache.calcite.test.schemata.hr包下，是Calcite测试框架中的人力资源（HR）测试schema包

import com.google.common.collect.ImmutableList; // 导入Google Guava库的ImmutableList类，用于创建不可变的列表集合

import java.util.Arrays; // 导入Java标准库的Arrays工具类，用于数组操作和转换为集合
import java.util.Collections; // 导入Java标准库的Collections工具类，用于创建不可修改的集合

/**
 * HR schema with more data than in {@link HrSchema}.
 * 人力资源（HR）schema类，包含比HrSchema更多的测试数据
 * 
 * 类作用说明：
 * 该类是Calcite测试框架中使用的一个测试Schema（模式/数据源），用于模拟人力资源数据库的结构和数据
 * 它定义了两个主要的表（通过数组表示）：emps（员工表）和depts（部门表）
 * 该类与HrSchema类似，但包含更多的测试数据，用于测试Calcite在处理较大数据集时的性能和正确性
 * 在Calcite中，Schema是一个逻辑概念，代表一个数据源或数据库的命名空间，包含表、视图等对象
 * 该类通过public final的成员变量暴露表数据，Calcite可以通过反射机制访问这些数据作为查询的数据源
 * 
 * 使用场景：
 * 1. 用于Calcite的单元测试和集成测试
 * 2. 验证SQL查询优化器的正确性
 * 3. 测试不同数据量下的查询性能
 * 4. 演示Calcite如何处理Java对象作为数据源
 */
public class HrSchemaBig { // 定义HrSchemaBig类，表示包含更多数据的人力资源Schema
  @Override // 注解：表示该方法覆盖父类Object的toString方法
  public String toString() { // 重写toString方法，返回对象的字符串表示
    return "HrSchema"; // 返回字符串"HrSchema"，用于标识该Schema的名称
  }

  // 成员变量：员工数组
  // 作用：存储所有员工数据的数组，每个Employee对象代表一个员工记录
  // 说明：该数组是public final的，表示它是公开访问且不可重新赋值的（但数组内容本身是可变的）
  // Employee对象构造参数：(id, deptId, name, salary, commission)
  //   - id: 员工唯一标识符
  //   - deptId: 所属部门ID，关联到depts数组中的部门
  //   - name: 员工姓名
  //   - salary: 员工薪资
  //   - commission: 佣金，可能为null表示无佣金
  public final Employee[] emps = { // 定义员工数组，包含48个员工记录
      new Employee(1, 10, "Bill", 10000, 1000), // 员工1：Bill，部门10，薪资10000，佣金1000
      new Employee(2, 20, "Eric", 8000, 500), // 员工2：Eric，部门20，薪资8000，佣金500
      new Employee(3, 10, "Sebastian", 7000, null), // 员工3：Sebastian，部门10，薪资7000，无佣金（null）
      new Employee(4, 10, "Theodore", 11500, 250), // 员工4：Theodore，部门10，薪资11500，佣金250
      new Employee(5, 10, "Marjorie", 10000, 1000), // 员工5：Marjorie，部门10，薪资10000，佣金1000
      new Employee(6, 20, "Guy", 8000, 500), // 员工6：Guy，部门20，薪资8000，佣金500
      new Employee(7, 10, "Dieudonne", 7000, null), // 员工7：Dieudonne，部门10，薪资7000，无佣金
      new Employee(8, 10, "Haroun", 11500, 250), // 员工8：Haroun，部门10，薪资11500，佣金250
      new Employee(9, 10, "Sarah", 10000, 1000), // 员工9：Sarah，部门10，薪资10000，佣金1000
      new Employee(10, 20, "Gabriel", 8000, 500), // 员工10：Gabriel，部门20，薪资8000，佣金500
      new Employee(11, 10, "Pierre", 7000, null), // 员工11：Pierre，部门10，薪资7000，无佣金
      new Employee(12, 10, "Paul", 11500, 250), // 员工12：Paul，部门10，薪资11500，佣金250
      new Employee(13, 10, "Jacques", 100, 1000), // 员工13：Jacques，部门10，薪资100（异常低，用于测试边界情况），佣金1000
      new Employee(14, 20, "Khawla", 8000, 500), // 员工14：Khawla，部门20，薪资8000，佣金500
      new Employee(15, 10, "Brielle", 7000, null), // 员工15：Brielle，部门10，薪资7000，无佣金
      new Employee(16, 10, "Hyuna", 11500, 250), // 员工16：Hyuna，部门10，薪资11500，佣金250
      new Employee(17, 10, "Ahmed", 10000, 1000), // 员工17：Ahmed，部门10，薪资10000，佣金1000
      new Employee(18, 20, "Lara", 8000, 500), // 员工18：Lara，部门20，薪资8000，佣金500
      new Employee(19, 10, "Capucine", 7000, null), // 员工19：Capucine，部门10，薪资7000，无佣金
      new Employee(20, 10, "Michelle", 11500, 250), // 员工20：Michelle，部门10，薪资11500，佣金250
      new Employee(21, 10, "Cerise", 10000, 1000), // 员工21：Cerise，部门10，薪资10000，佣金1000
      new Employee(22, 80, "Travis", 8000, 500), // 员工22：Travis，部门80（Finance），薪资8000，佣金500
      new Employee(23, 10, "Taylor", 7000, null), // 员工23：Taylor，部门10，薪资7000，无佣金
      new Employee(24, 10, "Seohyun", 11500, 250), // 员工24：Seohyun，部门10，薪资11500，佣金250
      new Employee(25, 70, "Helen", 10000, 1000), // 员工25：Helen，部门70（Production），薪资10000，佣金1000
      new Employee(26, 50, "Patric", 8000, 500), // 员工26：Patric，部门50（Design），薪资8000，佣金500
      new Employee(27, 10, "Clara", 7000, null), // 员工27：Clara，部门10，薪资7000，无佣金
      new Employee(28, 10, "Catherine", 11500, 250), // 员工28：Catherine，部门10，薪资11500，佣金250
      new Employee(29, 10, "Anibal", 10000, 1000), // 员工29：Anibal，部门10，薪资10000，佣金1000
      new Employee(30, 30, "Ursula", 8000, 500), // 员工30：Ursula，部门30（HR），薪资8000，佣金500
      new Employee(31, 10, "Arturito", 7000, null), // 员工31：Arturito，部门10，薪资7000，无佣金
      new Employee(32, 70, "Diane", 11500, 250), // 员工32：Diane，部门70（Production），薪资11500，佣金250
      new Employee(33, 10, "Phoebe", 10000, 1000), // 员工33：Phoebe，部门10，薪资10000，佣金1000
      new Employee(34, 20, "Maria", 8000, 500), // 员工34：Maria，部门20，薪资8000，佣金500
      new Employee(35, 10, "Edouard", 7000, null), // 员工35：Edouard，部门10，薪资7000，无佣金
      new Employee(36, 110, "Isabelle", 11500, 250), // 员工36：Isabelle，部门110（Maintenance），薪资11500，佣金250
      new Employee(37, 120, "Olivier", 10000, 1000), // 员工37：Olivier，部门120（Client Support），薪资10000，佣金1000
      new Employee(38, 20, "Yann", 8000, 500), // 员工38：Yann，部门20，薪资8000，佣金500
      new Employee(39, 60, "Ralf", 7000, null), // 员工39：Ralf，部门60（IT），薪资7000，无佣金
      new Employee(40, 60, "Emmanuel", 11500, 250), // 员工40：Emmanuel，部门60（IT），薪资11500，佣金250
      new Employee(41, 10, "Berenice", 10000, 1000), // 员工41：Berenice，部门10，薪资10000，佣金1000
      new Employee(42, 20, "Kylie", 8000, 500), // 员工42：Kylie，部门20，薪资8000，佣金500
      new Employee(43, 80, "Natacha", 7000, null), // 员工43：Natacha，部门80（Finance），薪资7000，无佣金
      new Employee(44, 100, "Henri", 11500, 250), // 员工44：Henri，部门100（Research），薪资11500，佣金250
      new Employee(45, 90, "Pascal", 10000, 1000), // 员工45：Pascal，部门90（Accounting），薪资10000，佣金1000
      new Employee(46, 90, "Sabrina", 8000, 500), // 员工46：Sabrina，部门90（Accounting），薪资8000，佣金500
      new Employee(47, 8, "Riyad", 7000, null), // 员工47：Riyad，部门8（不存在于depts中，用于测试外键约束），薪资7000，无佣金
      new Employee(48, 5, "Andy", 11500, 250), // 员工48：Andy，部门5（不存在于depts中，用于测试外键约束），薪资11500，佣金250
  };
  // 成员变量：部门数组
  // 作用：存储所有部门数据的数组，每个Department对象代表一个部门记录
  // 说明：该数组是public final的，表示它是公开访问且不可重新赋值的
  // Department对象构造参数：(id, name, employees, location)
  //   - id: 部门唯一标识符
  //   - name: 部门名称
  //   - employees: 部门员工列表，可以是Arrays.asList、ImmutableList.of()或Collections.singletonList创建
  //   - location: 部门位置（Location对象），可能为null表示未设置位置
  // 注意：某些部门的employees列表引用了emps数组中的元素，建立了部门与员工的关联关系
  public final Department[] depts = { // 定义部门数组，包含12个部门记录
      new Department(10, "Sales", Arrays.asList(emps[0], emps[2]), // 部门10：销售部，包含员工Bill和Sebastian
          new Location(-122, 38)), // 位置：经度-122，纬度38（可能表示美国西海岸某地）
      new Department(20, "Marketing", ImmutableList.of(), new Location(0, 52)), // 部门20：市场部，无员工（空列表），位置：经度0，纬度52（可能表示英国附近）
      new Department(30, "HR", Collections.singletonList(emps[1]), null), // 部门30：人力资源部，包含员工Eric，位置未设置（null）
      new Department(40, "Administration", Arrays.asList(emps[0], emps[2]), // 部门40：行政部，包含员工Bill和Sebastian（注意：员工可能属于多个部门，这是测试数据的特殊设计）
          new Location(-122, 38)), // 位置：经度-122，纬度38
      new Department(50, "Design", ImmutableList.of(), new Location(0, 52)), // 部门50：设计部，无员工，位置：经度0，纬度52
      new Department(60, "IT", Collections.singletonList(emps[1]), null), // 部门60：IT部，包含员工Eric，位置未设置
      new Department(70, "Production", Arrays.asList(emps[0], emps[2]), // 部门70：生产部，包含员工Bill和Sebastian
          new Location(-122, 38)), // 位置：经度-122，纬度38
      new Department(80, "Finance", ImmutableList.of(), new Location(0, 52)), // 部门80：财务部，无员工，位置：经度0，纬度52
      new Department(90, "Accounting", Collections.singletonList(emps[1]), null), // 部门90：会计部，包含员工Eric，位置未设置
      new Department(100, "Research", Arrays.asList(emps[0], emps[2]), // 部门100：研发部，包含员工Bill和Sebastian
          new Location(-122, 38)), // 位置：经度-122，纬度38
      new Department(110, "Maintenance", ImmutableList.of(), new Location(0, 52)), // 部门110：维护部，无员工，位置：经度0，纬度52
      new Department(120, "Client Support", Collections.singletonList(emps[1]), null), // 部门120：客户支持部，包含员工Eric，位置未设置
  };
}
