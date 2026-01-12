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

import java.util.List; // 导入Java集合框架中的List接口，用于存储有序的元素集合
import java.util.Objects; // 导入Java工具类Objects，用于对象的equals、hashCode、toString等常用操作

/**
 * Department model. // 部门模型类，用于表示组织结构中的部门信息
 * 该类是Calcite测试框架中HR模式的核心实体之一，代表一个部门，包含部门编号、名称、员工列表和位置信息
 * 该类被设计为不可变对象（所有字段都是final），通过Java适配器（Java Adapter）机制将Java对象映射为Calcite可查询的表
 * 在Calcite的测试场景中，这个类常被用作测试数据源，用于验证SQL查询、关系代数操作和优化规则等功能
 */
public class Department { // 定义Department类，表示部门实体
  public final int deptno; // 部门编号，唯一标识一个部门，使用final修饰表示该字段不可变，在对象创建后无法修改
  public final String name; // 部门名称，存储部门的描述性名称，使用final修饰表示该字段不可变

  @org.apache.calcite.adapter.java.Array(component = Employee.class) // Calcite Java适配器注解，标记该字段为数组类型，指定数组元素类型为Employee类，使Calcite能够识别并处理该集合字段
  public final List<Employee> employees; // 部门员工列表，存储该部门下的所有员工对象，使用List集合维护员工集合，final修饰表示该引用不可变
  public final @Nullable Location location; // 部门位置信息，存储部门的地理位置，使用@Nullable注解表示该字段可以为null，final修饰表示该引用不可变

  public Department(int deptno, String name, List<Employee> employees, // 构造方法，用于创建Department对象，初始化部门的所有属性
      @Nullable Location location) { // 构造方法参数，包括部门编号、名称、员工列表和位置信息，location参数标记为可空
    this.deptno = deptno; // 将传入的deptno参数赋值给实例变量deptno，初始化部门编号
    this.name = name; // 将传入的name参数赋值给实例变量name，初始化部门名称
    this.employees = employees; // 将传入的employees参数赋值给实例变量employees，初始化部门员工列表
    this.location = location; // 将传入的location参数赋值给实例变量location，初始化部门位置信息
  }

  @Override public String toString() { // 重写Object类的toString方法，用于返回对象的字符串表示，便于调试和日志输出
    return "Department [deptno: " + deptno + ", name: " + name // 返回格式化的字符串，包含部门编号和名称
        + ", employees: " + employees + ", location: " + location + "]"; // 继续拼接员工列表和位置信息，形成完整的部门描述字符串
  }

  @Override public boolean equals(Object obj) { // 重写Object类的equals方法，用于比较两个Department对象是否相等
    return obj == this // 首先检查obj是否是当前对象本身（引用相等），如果是则直接返回true
        || obj instanceof Department // 如果不是同一对象，检查obj是否是Department类的实例
        && deptno == ((Department) obj).deptno; // 如果是Department实例，比较两者的deptno字段是否相等，因为deptno是唯一标识符，相等则认为两个部门对象相等
  }

  @Override public int hashCode() { // 重写Object类的hashCode方法，用于计算对象的哈希码，与equals方法保持一致
    return Objects.hash(deptno); // 使用Objects工具类的hash方法基于deptno字段计算哈希码，确保相等的对象（deptno相同）具有相同的哈希码
  }
}
