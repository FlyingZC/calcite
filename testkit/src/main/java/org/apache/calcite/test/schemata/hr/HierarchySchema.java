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
package org.apache.calcite.test.schemata.hr; // 包声明：该类属于org.apache.calcite.test.schemata.hr包，用于测试模式中的层次结构数据

import java.util.Arrays; // 导入Arrays工具类，用于数组操作
import java.util.Objects; // 导入Objects工具类，用于对象操作和哈希计算

/**
 * A Schema representing a hierarchy of employees. // 类的作用：表示员工层次结构的模式类，用于测试Calcite的层次查询功能
 *
 * <p>The Schema is meant to be used with // 说明：该模式设计用于配合ReflectiveSchema使用，因此所有字段和方法必须是public的
 * {@link org.apache.calcite.adapter.java.ReflectiveSchema} thus all
 * fields, and methods, should be public.
 */
public class HierarchySchema { // 类定义：HierarchySchema类，表示员工层次结构的测试模式
  @Override public String toString() { // 重写toString方法：返回对象的字符串表示形式
    return "HierarchySchema"; // 返回类名字符串，用于调试和日志输出
  }

  public final Employee[] emps = { // 成员变量：员工数组，存储所有员工对象的数据，用于测试层次查询
      new Employee(1, 10, "Emp1", 10000, 1000), // 创建员工对象：员工ID=1，部门ID=10，姓名=Emp1，工资=10000，佣金=1000
      new Employee(2, 10, "Emp2", 8000, 500), // 创建员工对象：员工ID=2，部门ID=10，姓名=Emp2，工资=8000，佣金=500
      new Employee(3, 10, "Emp3", 7000, null), // 创建员工对象：员工ID=3，部门ID=10，姓名=Emp3，工资=7000，佣金=null（无佣金）
      new Employee(4, 10, "Emp4", 8000, 500), // 创建员工对象：员工ID=4，部门ID=10，姓名=Emp4，工资=8000，佣金=500
      new Employee(5, 10, "Emp5", 7000, null), // 创建员工对象：员工ID=5，部门ID=10，姓名=Emp5，工资=7000，佣金=null（无佣金）
  };

  public final Department[] depts = { // 成员变量：部门数组，存储部门信息，包含部门ID、名称、员工列表和位置
      new Department( // 创建部门对象：初始化部门信息
          10, // 部门ID=10
          "Dept", // 部门名称="Dept"
          Arrays.asList(emps[0], emps[1], emps[2], emps[3], emps[4]), // 部门员工列表：包含所有5个员工（emps[0]到emps[4]）
          new Location(-122, 38)), // 部门位置：经度=-122，纬度=38
  };

  //      Emp1 // 层次结构树形图：Emp1是根节点（最高级管理者）
  //      /  \ // Emp1有两个直接下属：Emp2和Emp4
  //    Emp2  Emp4 // Emp2和Emp4是Emp1的直接下属
  //    /  \ // Emp2有两个直接下属：Emp3和Emp5
  // Emp3   Emp5 // Emp3和Emp5是Emp2的直接下属，也是叶子节点
  public final Hierarchy[] hierarchies = { // 成员变量：层次关系数组，定义员工之间的上下级关系（管理者-下属关系）
      new Hierarchy(1, 2), // 层次关系：员工1（Emp1）是员工2（Emp2）的管理者
      new Hierarchy(2, 3), // 层次关系：员工2（Emp2）是员工3（Emp3）的管理者
      new Hierarchy(2, 5), // 层次关系：员工2（Emp2）是员工5（Emp5）的管理者
      new Hierarchy(1, 4), // 层次关系：员工1（Emp1）是员工4（Emp4）的管理者
  };
  /** Hierarchy representing manager - subordinate. */ // 内部类注释：Hierarchy类表示员工之间的管理者-下属关系
  public static class Hierarchy { // 内部类定义：Hierarchy静态内部类，用于表示层次关系
    public final int managerid; // 成员变量：管理者ID，表示上级员工的ID，final表示不可变
    public final int subordinateid; // 成员变量：下属ID，表示下级员工的ID，final表示不可变

    public Hierarchy(int managerid, int subordinateid) { // 构造方法：创建Hierarchy对象，初始化管理者ID和下属ID
      this.managerid = managerid; // 将参数managerid赋值给成员变量managerid
      this.subordinateid = subordinateid; // 将参数subordinateid赋值给成员变量subordinateid
    } // 构造方法结束

    @Override public String toString() { // 重写toString方法：返回层次关系的字符串表示，用于调试和日志输出
      return "Hierarchy [managerid: " + managerid + ", subordinateid: " + subordinateid + "]"; // 返回格式化的字符串，包含管理者和下属ID
    } // toString方法结束

    @Override public boolean equals(Object obj) { // 重写equals方法：比较两个Hierarchy对象是否相等
      return obj == this // 如果obj是当前对象本身，直接返回true
          || obj instanceof Hierarchy // 或者obj是Hierarchy类的实例
          && managerid == ((Hierarchy) obj).managerid // 并且managerid相等
          && subordinateid == ((Hierarchy) obj).subordinateid; // 并且subordinateid相等，则返回true
    } // equals方法结束

    @Override public int hashCode() { // 重写hashCode方法：计算Hierarchy对象的哈希值，用于HashMap等集合
      return Objects.hash(managerid, subordinateid); // 使用Objects.hash方法基于managerid和subordinateid计算哈希值
    } // hashCode方法结束
  } // Hierarchy内部类结束
}
