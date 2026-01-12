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
package org.apache.calcite.test.schemata.hr; // 指定包路径，该类位于org.apache.calcite.test.schemata.hr包下，是Calcite测试框架中HR（人力资源）模式的一部分

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Checker Framework的注解，用于标记可能为null的类型，帮助进行静态空值检查

import java.sql.Timestamp; // 导入Java SQL的Timestamp类，用于表示日期和时间信息，精确到纳秒级别
import java.util.List; // 导入Java集合框架中的List接口，用于存储有序的元素集合

/**
 * Department with inception date model. // 带有成立日期的部门模型类，继承自Department基类，在原有部门信息的基础上增加了成立日期（inceptionDate）字段
 * 该类是Calcite测试框架中HR模式的扩展实体，用于测试Calcite对时间类型数据的处理能力
 * 通过继承Department类，复用了部门编号、名称、员工列表和位置信息等基本属性
 * 新增的inceptionDate字段可以用于测试时间相关的SQL操作，如日期比较、时间范围查询、时间函数等
 * 该类同样被设计为不可变对象（所有字段都是final），通过Java适配器（Java Adapter）机制将Java对象映射为Calcite可查询的表
 * 在Calcite的测试场景中，这个类常被用作测试数据源，用于验证涉及时间字段的SQL查询、关系代数操作和优化规则等功能
 * 例如：可以用于测试时间范围过滤、日期函数（如YEAR、MONTH、DAY）、时间戳比较、时间排序等操作
 */
public class DepartmentPlus extends Department { // 定义DepartmentPlus类，继承自Department类，表示带有成立日期的部门实体
  public final Timestamp inceptionDate; // 部门成立日期，存储部门的成立时间戳，使用java.sql.Timestamp类型表示精确到纳秒的日期时间信息，final修饰表示该字段不可变，在对象创建后无法修改，该字段可用于测试Calcite对时间类型数据的各种操作

  public DepartmentPlus(int deptno, String name, List<Employee> employees, // 构造方法，用于创建DepartmentPlus对象，初始化部门的所有属性，包括从父类继承的属性和新增的成立日期
      @Nullable Location location, Timestamp inceptionDate) { // 构造方法参数，包括部门编号、名称、员工列表、位置信息和成立日期，location参数标记为可空，inceptionDate为新增的时间戳参数
    super(deptno, name, employees, location); // 调用父类Department的构造方法，初始化父类继承的属性：deptno（部门编号）、name（部门名称）、employees（员工列表）和location（位置信息）
    this.inceptionDate = inceptionDate; // 将传入的inceptionDate参数赋值给实例变量inceptionDate，初始化部门成立日期
  }
}
