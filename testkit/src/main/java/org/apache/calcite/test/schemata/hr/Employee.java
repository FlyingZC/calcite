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
package org.apache.calcite.test.schemata.hr;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Objects;

/**
 * Employee model. // 员工模型类，用于表示HR（人力资源）模式中的员工实体
 */
public class Employee {
  public final int empid; // 员工ID，唯一标识一个员工，用于主键和员工记录的唯一性标识
  public final int deptno; // 部门编号，表示员工所属的部门，用于关联部门表
  public final String name; // 员工姓名，存储员工的完整名称
  public final float salary; // 员工薪水，表示员工的基本工资金额
  public final @Nullable Integer commission; // 员工佣金，可为null，表示员工的奖金或提成金额

  public Employee(int empid, int deptno, String name, float salary, // 构造方法：创建一个Employee对象，初始化所有员工属性
      @Nullable Integer commission) { // 参数：empid-员工ID，deptno-部门编号，name-员工姓名，salary-薪水，commission-佣金（可为null）
    this.empid = empid; // 将传入的员工ID赋值给实例变量empid
    this.deptno = deptno; // 将传入的部门编号赋值给实例变量deptno
    this.name = name; // 将传入的员工姓名赋值给实例变量name
    this.salary = salary; // 将传入的薪水赋值给实例变量salary
    this.commission = commission; // 将传入的佣金赋值给实例变量commission
  }

  @Override public String toString() { // 重写toString方法，返回Employee对象的字符串表示
    return "Employee [empid: " + empid + ", deptno: " + deptno // 返回包含员工ID、部门编号和姓名的格式化字符串
        + ", name: " + name + "]"; // 闭合字符串，完整输出员工关键信息
  }

  @Override public boolean equals(Object obj) { // 重写equals方法，用于比较两个Employee对象是否相等
    return obj == this // 如果是同一个对象引用，直接返回true
        || obj instanceof Employee // 如果obj是Employee类的实例
        && empid == ((Employee) obj).empid; // 则比较员工ID是否相等，ID相等即视为同一员工
  }

  @Override public int hashCode() { // 重写hashCode方法，用于支持基于哈希的集合（如HashSet、HashMap）
    return Objects.hash(empid); // 使用员工ID生成哈希码，确保equals相等的对象hashCode也相等
  }
}
