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
package org.apache.calcite; // 声明该类属于 org.apache.calcite 包，这是 Apache Calcite 框架的核心包

import java.util.ArrayList; // 导入 ArrayList 类，用于创建动态数组列表，这是一个可调整大小的数组实现

/**
 * Demo. // 这是一个演示类，用于展示基本的 Java 集合操作和迭代器使用模式
 * // 该类的主要目的是演示如何使用 ArrayList 存储数据，以及如何通过 Iterable 接口遍历集合元素
 * // 在 Calcite 框架中，这种数据结构和迭代模式经常用于处理查询结果集和数据流
 */
public class Demo { // 定义一个名为 Demo 的公共类，该类包含演示代码
  private Demo() { // 私有构造方法，防止外部实例化该类，确保该类只能通过静态方法调用
    // 这是一个工具类设计模式，私有构造方法表示该类不应该被实例化
    // 在 Calcite 中，很多工具类都采用这种设计，因为它们只提供静态方法供外部使用
  }

  public static void main(String[] args) { // 程序的主入口方法，静态方法，接收命令行参数数组
    // args: 命令行参数数组，用于接收从命令行传递给程序的参数
    ArrayList<String> names = new ArrayList<>(); // 创建一个泛型为 String 的 ArrayList 实例，用于存储字符串类型的名字列表
    // ArrayList 是 Java 集合框架中的动态数组，可以自动扩容，适合存储可变数量的元素
    // 在 Calcite 中，ArrayList 常用于存储查询的列名、表名等元数据信息
    names.add("John"); // 向 names 列表中添加字符串 "John"，这是第一个 Beatles 成员的名字
    // add() 方法将元素添加到列表的末尾，时间复杂度为 O(1)（均摊）
    names.add("Paul"); // 向 names 列表中添加字符串 "Paul"，这是第二个 Beatles 成员的名字
    names.add("George"); // 向 names 列表中添加字符串 "George"，这是第三个 Beatles 成员的名字
    names.add("Ringo"); // 向 names 列表中添加字符串 "Ringo"，这是第四个 Beatles 成员的名字

    Iterable<String> nameIterable = names; // 将 ArrayList 赋值给 Iterable 接口类型的变量
    // Iterable 是 Java 集合框架中的顶层接口，定义了迭代集合元素的能力
    // ArrayList 实现了 Iterable 接口，因此可以向上转型为 Iterable 类型
    // 这种多态设计允许代码只依赖 Iterable 接口，而不依赖具体的集合实现类
    // 在 Calcite 中，Iterable 接口广泛用于抽象数据源的遍历操作，使得代码更加灵活和可扩展

    for (String name : nameIterable) { // 使用增强 for 循环（for-each 循环）遍历 Iterable 中的每个元素
      // 这种语法是 Java 5 引入的，简化了集合遍历操作
      // 底层实际上调用 Iterable 的 iterator() 方法获取迭代器，然后通过 hasNext() 和 next() 方法遍历
      // 在 Calcite 中，这种遍历模式常用于处理查询结果集、元数据列表等
      System.out.println(name); // 将当前遍历到的名字字符串输出到标准输出流（控制台）
      // System.out 是标准输出流，println() 方法会在输出后自动换行
      // 在 Calcite 的调试和测试代码中，经常使用这种方式输出查询结果和调试信息
    }
  }
}
