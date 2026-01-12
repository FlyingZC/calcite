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
// Apache License 开源协议声明
package org.apache.calcite.adapter.os; // 包名，属于 Calcite 框架的操作系统适配器模块

import org.apache.calcite.linq4j.Enumerator; // 导入 Calcite 的 LINQ4J 枚举器接口，用于遍历数据
import org.apache.calcite.linq4j.Linq4j; // 导入 Calcite 的 LINQ4J 工具类，提供各种操作集合的静态方法
import org.apache.calcite.util.trace.CalciteTrace; // 导入 Calcite 的跟踪工具类，用于获取日志记录器

import org.slf4j.Logger; // 导入 SLF4J 日志接口，用于记录日志信息

import java.util.ArrayList; // 导入 Java 的 ArrayList 动态数组类，用于存储查询结果
import java.util.List; // 导入 Java 的 List 接口，表示有序集合
import java.util.Locale; // 导入 Java 的 Locale 类，用于处理区域相关的信息，如大小写转换

/**
 * Enumerator that reads from OS's System.
 */
// 类注释：这是一个从操作系统系统信息读取数据的枚举器
// 该类实现了 Enumerator<Object[]> 接口，可以遍历操作系统相关的系统信息
// 它是 Calcite 框架中用于查询操作系统系统信息的核心类
// 通过 OsQueryType 枚举类型支持多种系统信息查询，如 CPU 信息、内存信息、网络接口等
// 该类采用委托模式，将实际的枚举操作委托给内部的 enumerator 成员变量
public class OsQuery implements Enumerator<Object[]> { // 定义 OsQuery 类，实现泛型接口 Enumerator<Object[]>，表示该类可以枚举对象数组类型的数据
  private static final Logger LOGGER = CalciteTrace.getParserTracer(); // 静态日志记录器，使用 CalciteTrace 获取解析器跟踪器，用于记录解析过程中的错误和调试信息

  private final Enumerator<Object[]> enumerator; // 成员变量：内部的枚举器实例，用于实际的数据遍历操作，通过 Linq4j 工具类创建，委托给该枚举器执行所有枚举操作

  public OsQuery(String type) { // 构造方法：接收一个类型参数 type，用于指定要查询的系统信息类型
    this.enumerator = Linq4j.enumerator(eval(type)); // 调用 eval(type) 方法获取系统信息列表，然后使用 Linq4j.enumerator() 方法将列表转换为枚举器并赋值给成员变量
  }

  public Enumerator<Object[]> getEnumerator() { // 公共方法：获取内部的枚举器实例
    return enumerator; // 返回内部的枚举器对象，允许外部直接访问枚举器
  }

  @Override public Object[] current() { // 重写接口方法：获取当前元素，返回当前遍历位置的对象数组
    return enumerator.current(); // 委托给内部枚举器的 current() 方法，返回当前遍历位置的数据行（对象数组形式）
  }

  @Override public boolean moveNext() { // 重写接口方法：移动到下一个元素，如果成功移动到下一个元素返回 true，否则返回 false
    return enumerator.moveNext(); // 委托给内部枚举器的 moveNext() 方法，控制遍历的进度
  }

  @Override public void reset() { // 重写接口方法：重置枚举器到初始状态
    enumerator.reset(); // 委托给内部枚举器的 reset() 方法，将枚举器重置到起始位置，可以重新遍历数据
  }

  @Override public void close() { // 重写接口方法：关闭枚举器，释放相关资源
    enumerator.close(); // 委托给内部枚举器的 close() 方法，释放枚举器占用的系统资源
  }

  public List<Object[]> eval(String type) { // 公共方法：根据指定的类型执行系统信息查询并返回结果列表，参数 type 表示查询的系统信息类型
    OsQueryType queryType = OsQueryType.valueOf(type.toUpperCase(Locale.ROOT)); // 将输入的类型字符串转换为大写（使用 ROOT 区域设置以确保一致性），然后通过 valueOf 方法转换为 OsQueryType 枚举类型
    try { // 开始 try 块，捕获可能发生的异常
      return queryType.getInfo(); // 调用 OsQueryType 枚举实例的 getInfo() 方法，获取对应的系统信息数据并返回
    } catch (Exception e) { // 捕获所有类型的异常
      LOGGER.error("Failed to get result's info", e); // 使用日志记录器记录错误信息，包含异常堆栈
      return new ArrayList<>(); // 发生异常时返回空列表，确保不会因为异常导致程序崩溃
    }
  }
}
