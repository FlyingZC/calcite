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
package org.apache.calcite.test.schemata.hr; // 包声明：该类属于 org.apache.calcite.test.schemata.hr 包，这是 Calcite 测试框架中用于测试 HR（人力资源）模式的包

import java.util.Objects; // 导入 Objects 工具类，用于实现 equals 和 hashCode 方法

/**
 * Employee dependents model. // 员工家属模型类：用于表示员工的家属信息，这是一个简单的 Java Bean 类，用于在 Calcite 测试中模拟数据库表中的家属数据
 */
public class Dependent { // Dependent 类：表示员工的家属信息，包含员工ID和家属姓名两个属性
  public final int empid; // 员工ID（employee id）：标识该家属所属的员工，final 表示该字段在初始化后不可修改，public 表示可以直接访问
  public final String name; // 家属姓名（dependent name）：家属的姓名，final 表示该字段在初始化后不可修改，public 表示可以直接访问

  public Dependent(int empid, String name) { // 构造方法：创建一个 Dependent 对象，初始化员工ID和家属姓名
    this.empid = empid; // 将传入的 empid 参数赋值给实例变量 empid，使用 this 关键字区分实例变量和参数
    this.name = name; // 将传入的 name 参数赋值给实例变量 name，使用 this 关键字区分实例变量和参数
  }

  @Override public String toString() { // 重写 Object 类的 toString 方法：返回该 Dependent 对象的字符串表示形式，用于调试和日志输出
    return "Dependent [empid: " + empid + ", name: " + name + "]"; // 返回格式化的字符串，包含类名和所有字段的值
  }

  @Override public boolean equals(Object obj) { // 重写 Object 类的 equals 方法：比较两个 Dependent 对象是否相等，根据 empid 和 name 字段判断
    return obj == this // 首先检查是否是同一个对象引用（地址相同），如果是则直接返回 true
        || obj instanceof Dependent // 如果不是同一个对象，检查 obj 是否是 Dependent 类的实例
        && empid == ((Dependent) obj).empid // 比较两个对象的 empid 字段是否相等，使用 == 比较基本类型 int
        && Objects.equals(name, ((Dependent) obj).name); // 使用 Objects.equals 比较两个对象的 name 字段，这样可以安全处理 null 值
  }

  @Override public int hashCode() { // 重写 Object 类的 hashCode 方法：根据 empid 和 name 字段生成哈希码，与 equals 方法保持一致
    return Objects.hash(empid, name); // 使用 Objects.hash 方法基于 empid 和 name 字段生成哈希码，确保相等的对象具有相同的哈希码
  }
}
