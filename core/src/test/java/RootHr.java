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
// Apache 许可证声明：该文件遵循 Apache 2.0 许可证，允许在保留版权声明的情况下使用和修改代码

/** Equivalent to
 * {@link org.apache.calcite.examples.foodmart.java.JdbcExample.Hr}, but
 * belongs to the unnamed (root) package. */ // 等价于 org.apache.calcite.examples.foodmart.java.JdbcExample.Hr 类，但属于未命名（根）包，用于测试 Calcite 在处理根包类时的行为，这个类主要用于演示和测试 Calcite 如何处理 Java 对象作为数据源
public class RootHr { // 定义一个名为 RootHr 的公共类，用于表示人力资源（HR）数据模型，属于根包（未命名包），该类包含员工信息数组
  public final RootEmployee[] emps = { // 定义一个公共的、不可变的员工数组成员变量 emps，使用 public final 修饰表示该字段是公共的且不可变，一旦初始化后不能被修改，数组中存储 RootEmployee 对象
      new RootEmployee(100, "Bill"), // 创建第一个员工对象，员工ID为100，姓名为"Bill"，使用 new 关键字调用 RootEmployee 的构造方法
      new RootEmployee(200, "Eric"), // 创建第二个员工对象，员工ID为200，姓名为"Eric"，使用 new 关键字调用 RootEmployee 的构造方法
      new RootEmployee(150, "Sebastian"), // 创建第三个员工对象，员工ID为150，姓名为"Sebastian"，使用 new 关键字调用 RootEmployee 的构造方法
  }; // 数组初始化结束，注意员工ID不是按顺序排列的（100, 200, 150），这可能用于测试排序功能
} // 类定义结束，RootHr 类没有定义构造方法，使用默认的无参构造方法，也没有定义其他方法，仅包含一个成员变量
