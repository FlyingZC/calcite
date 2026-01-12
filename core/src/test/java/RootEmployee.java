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

/** Equivalent to
 * {@link org.apache.calcite.examples.foodmart.java.JdbcExample.Employee}, but
 * belongs to the unnamed (root) package. */ // 等价于 org.apache.calcite.examples.foodmart.java.JdbcExample.Employee 类，但属于未命名（根）包，用于测试 Calcite 在处理根包类时的行为
public class RootEmployee { // 定义一个名为 RootEmployee 的公共类，用于表示员工信息，属于根包（未命名包）
  public final int empid; // 员工ID，使用 public final 修饰表示该字段是公共的且不可变，一旦初始化后不能被修改
  public final String name; // 员工姓名，使用 public final 修饰表示该字段是公共的且不可变，一旦初始化后不能被修改

  /** Creates a RootEmployee. */ // 创建一个 RootEmployee 对象的构造方法
  public RootEmployee(int empid, String name) { // 构造方法，接收员工ID和姓名两个参数
    this.empid = empid; // 将传入的 empid 参数值赋给当前对象的 empid 成员变量，使用 this 关键字区分成员变量和参数
    this.name = name; // 将传入的 name 参数值赋给当前对象的 name 成员变量，使用 this 关键字区分成员变量和参数
  }
}
