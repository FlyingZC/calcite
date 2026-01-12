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
package org.apache.calcite.adapter.os; // 指定当前类所属的包，位于org.apache.calcite.adapter.os包下，该包包含操作系统相关的适配器功能

import org.apache.calcite.DataContext; // 导入DataContext类，表示查询执行时的数据上下文，提供访问会话变量和系统资源的接口
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入AbstractEnumerable抽象类，用于实现LINQ风格的枚举功能，提供可枚举的数据集合基础实现
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，表示可枚举的数据集合，支持LINQ风格的查询操作
import org.apache.calcite.linq4j.Enumerator; // 导入Enumerator接口，用于遍历数据集合的枚举器，提供逐个访问元素的能力
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型，用于描述表或表达式的类型信息
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型对象的工厂
import org.apache.calcite.schema.ScannableTable; // 导入ScannableTable接口，表示可扫描的表，提供扫描表数据的能力
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举类，定义了SQL标准中的所有数据类型名称

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值，帮助进行空值检查

import java.io.BufferedReader; // 导入BufferedReader类，用于缓冲读取字符输入流，提高读取效率
import java.io.IOException; // 导入IOException类，表示输入输出操作时可能抛出的异常
import java.io.InputStream; // 导入InputStream类，表示字节输入流的抽象基类
import java.io.InputStreamReader; // 导入InputStreamReader类，用于将字节流转换为字符流
import java.nio.charset.StandardCharsets; // 导入StandardCharsets类，提供标准字符集常量，如UTF-8
import java.util.NoSuchElementException; // 导入NoSuchElementException类，表示枚举器没有更多元素时抛出的异常

/**
 * Table function that reads stdin and returns one row per line.
 * 表函数，用于从标准输入(stdin)读取数据，并为每一行返回一行记录。这个类允许Calcite查询直接读取进程的标准输入流，
 * 将输入的每一行转换为表中的一条记录，使得外部数据可以通过管道方式传递给Calcite查询处理。
 */
public class StdinTableFunction { // 定义StdinTableFunction类，这是一个表函数类，用于将标准输入转换为可查询的表结构

  private StdinTableFunction() { // 私有构造方法，防止外部实例化该类，因为该类只提供静态方法，不需要创建实例对象
  } // 构造方法结束，确保该类只能通过静态方法调用

  public static ScannableTable eval(boolean b) { // 公共静态方法eval，接收一个布尔参数b（虽然参数未使用，但可能用于未来扩展或兼容性），返回ScannableTable对象，该方法作为表函数的入口点
    return new AbstractBaseScannableTable() { // 返回一个匿名内部类实例，继承自AbstractBaseScannableTable，该内部类实现了ScannableTable接口，提供扫描表数据的能力
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法，接收DataContext参数root表示查询上下文，返回可枚举的Object数组集合，每个Object数组代表表的一行数据
        final InputStream is = DataContext.Variable.STDIN.get(root); // 从DataContext中获取标准输入流(STDIN)，使用DataContext.Variable.STDIN.get方法从上下文中提取输入流
        return new AbstractEnumerable<Object[]>() { // 返回一个匿名内部类实例，继承自AbstractEnumerable，实现可枚举的数据集合，该集合从输入流中读取数据
          final InputStreamReader in = // 创建InputStreamReader对象in，将字节输入流转换为字符输入流，使用UTF-8字符集进行编码
              new InputStreamReader(is, StandardCharsets.UTF_8); // 使用InputStreamReader包装InputStream，指定字符集为StandardCharsets.UTF_8，确保正确读取文本数据
          final BufferedReader br = new BufferedReader(in); // 创建BufferedReader对象br，包装InputStreamReader，提供缓冲读取功能，提高读取效率，支持逐行读取

          @Override public Enumerator<Object[]> enumerator() { // 重写enumerator方法，返回一个Enumerator对象，用于遍历数据集合中的每一行数据
            return new Enumerator<Object[]>() { // 返回一个匿名内部类实例，实现Enumerator接口，该枚举器负责逐行读取标准输入并提供数据访问
              @Nullable String line; // 声明可空的字符串成员变量line，用于存储当前读取的行内容，可能为null表示已读取完毕
              int i; // 声明整型成员变量i，用于记录当前行的序号(行号)，从0开始计数

              @Override public Object[] current() { // 重写current方法，返回当前枚举位置的元素，即当前行的数据数组
                if (line == null) { // 检查当前行内容是否为null
                  throw new NoSuchElementException(); // 如果line为null，抛出NoSuchElementException异常，表示没有更多元素可访问
                } // 条件判断结束
                return new Object[] {i, line}; // 返回包含行号和行内容的Object数组，数组第一个元素是行号i，第二个元素是行内容line
              } // current方法结束

              @Override public boolean moveNext() { // 重写moveNext方法，移动到下一个元素位置，返回boolean表示是否成功移动到下一个元素
                try { // 开始try-catch块，捕获可能的IO异常
                  line = br.readLine(); // 使用BufferedReader的readLine方法读取一行文本，将结果赋值给line变量，如果到达流末尾则返回null
                  ++i; // 将行号计数器i递增1，表示移动到下一行
                  return line != null; // 返回布尔值，如果line不为null表示成功读取到行内容，返回true；否则返回false表示已到达末尾
                } catch (IOException e) { // 捕获IOException异常，处理读取过程中可能出现的输入输出错误
                  throw new RuntimeException(e); // 将IOException包装为RuntimeException抛出，简化异常处理
                } // catch块结束
              } // moveNext方法结束

              @Override public void reset() { // 重写reset方法，用于重置枚举器到初始位置
                throw new UnsupportedOperationException(); // 抛出UnsupportedOperationException异常，表示不支持重置操作，因为标准输入流不支持回退
              } // reset方法结束

              @Override public void close() { // 重写close方法，用于关闭枚举器并释放相关资源
                try { // 开始try-catch块，捕获可能的IO异常
                  br.close(); // 关闭BufferedReader，释放系统资源，同时也会关闭底层的InputStreamReader和InputStream
                } catch (IOException e) { // 捕获IOException异常，处理关闭过程中可能出现的错误
                  throw new RuntimeException(e); // 将IOException包装为RuntimeException抛出，简化异常处理
                } // catch块结束
              } // close方法结束
            }; // 匿名内部类Enumerator实例创建结束
          } // enumerator方法结束
        }; // 匿名内部类AbstractEnumerable实例创建结束
      } // scan方法结束

      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，接收RelDataTypeFactory参数typeFactory用于创建类型对象，返回表的行类型描述
        return typeFactory.builder() // 使用RelDataTypeFactory创建一个类型构建器，用于构建表的行类型结构
            .add("ordinal", SqlTypeName.INTEGER) // 添加名为"ordinal"的列，类型为INTEGER，表示行号的整数类型
            .add("line", SqlTypeName.VARCHAR) // 添加名为"line"的列，类型为VARCHAR，表示行内容的字符串类型
            .build(); // 构建并返回RelDataType对象，完成行类型的定义
      } // getRowType方法结束
    }; // 匿名内部类AbstractBaseScannableTable实例创建结束
  } // eval方法结束
} // StdinTableFunction类定义结束
