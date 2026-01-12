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
// Apache许可证声明，说明本文件遵循Apache 2.0许可证
package org.apache.calcite.adapter.file; // 定义包名，该类属于org.apache.calcite.adapter.file包

import org.apache.calcite.DataContext; // 导入DataContext类，提供查询执行时的上下文信息，包括类型工厂、数据源等
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入JavaTypeFactory接口，用于创建和管理Java类型系统中的数据类型
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入AbstractEnumerable抽象类，实现了Enumerable接口，提供可枚举数据集合的基础实现
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，表示可枚举的数据集合，支持LINQ风格的查询操作
import org.apache.calcite.linq4j.Enumerator; // 导入Enumerator接口，表示数据枚举器，用于遍历数据集合中的元素
import org.apache.calcite.schema.ScannableTable; // 导入ScannableTable接口，表示可以被扫描的表，通过scan方法获取数据
import org.apache.calcite.util.Source; // 导入Source类，封装了数据源信息，如文件路径、输入流等

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值，用于静态类型检查

/**
 * Table based on a JSON file.
 * 基于JSON文件的表实现，用于将JSON文件作为Calcite中的数据表进行查询
 *
 * <p>It implements the {@link ScannableTable} interface, so Calcite gets
 * data by calling the {@link #scan(DataContext)} method.
 * 该类实现了ScannableTable接口，因此Calcite通过调用scan(DataContext)方法来获取表中的数据
 * ScannableTable是Calcite中一种简单的表实现方式，直接通过扫描数据源返回数据，不需要复杂的查询优化
 */
public class JsonScannableTable extends JsonTable // 定义JsonScannableTable类，继承自JsonTable基类
    implements ScannableTable { // 实现ScannableTable接口，表示这是一个可以被扫描的表
  /**
   * Creates a JsonScannableTable.
   * 构造方法：创建一个JsonScannableTable实例
   * 
   * @param source 数据源对象，封装了JSON文件的位置和访问方式
   */
  public JsonScannableTable(Source source) { // 构造方法，接收Source参数作为数据源
    super(source); // 调用父类JsonTable的构造方法，将source传递给父类进行初始化，父类会保存这个数据源引用
  } // 构造方法结束

  @Override public String toString() { // 重写toString方法，用于返回对象的字符串表示
    return "JsonScannableTable"; // 返回类名作为字符串表示，便于调试和日志输出
  } // toString方法结束

  @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 实现ScannableTable接口的scan方法，返回一个可枚举的对象数组集合
    // root参数：DataContext对象，包含查询执行的上下文信息，如类型工厂、schema、数据源配置等
    // 返回值：Enumerable<Object[]>表示一个可枚举的数据集，每个元素是一个Object数组，代表表中的一行数据
    return new AbstractEnumerable<@Nullable Object[]>() { // 创建并返回一个匿名内部类，继承自AbstractEnumerable
      // AbstractEnumerable是LINQ4J框架中的抽象类，提供了可枚举集合的基础实现
      // 这个匿名内部类通过实现enumerator()方法来提供数据枚举器
      
      @Override public Enumerator<@Nullable Object[]> enumerator() { // 实现enumerator方法，返回一个数据枚举器
        // Enumerator接口定义了遍历数据集合的方法，包括current()、moveNext()、reset()等
        // 该方法会在查询执行时被调用，用于创建实际的数据遍历器
        
        JavaTypeFactory typeFactory = root.getTypeFactory(); // 从DataContext中获取类型工厂
        // typeFactory用于创建和管理Calcite中的数据类型，确保数据类型与Java类型系统的正确映射
        // 在解析JSON数据时，需要使用typeFactory来创建正确的类型实例
        
        return new JsonEnumerator(getDataList(typeFactory)); // 创建并返回JsonEnumerator对象
        // getDataList(typeFactory)是父类JsonTable的方法，用于读取并解析JSON文件数据
        // 该方法会使用typeFactory将JSON数据转换为适合Calcite查询的格式
        // JsonEnumerator实现了Enumerator接口，负责遍历解析后的JSON数据，每次返回一行数据（Object数组）
        // 这样Calcite的查询引擎就可以通过JsonEnumerator逐行读取JSON文件中的数据
      } // enumerator方法结束
    }; // 匿名内部类实例化结束
  } // scan方法结束
} // JsonScannableTable类定义结束
