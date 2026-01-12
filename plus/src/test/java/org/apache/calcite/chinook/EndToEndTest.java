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
package org.apache.calcite.chinook; // 指定当前类所在的包路径，位于org.apache.calcite.chinook包下

import org.apache.calcite.test.QuidemTest; // 导入QuidemTest基类，用于基于Quidem框架的端到端测试

import net.hydromatic.quidem.Quidem; // 导入Quidem核心类，用于执行SQL测试脚本

import java.util.Collection; // 导入Collection接口，用于返回测试文件路径集合

/**
 * Entry point for all end-to-end tests based on Chinook data in HSQLDB wrapped
 * by Calcite schema.
 */ // 类注释：这是基于Chinook数据集的端到端测试入口点，Chinook数据存储在HSQLDB中，并通过Calcite schema进行包装
class EndToEndTest extends QuidemTest { // EndToEndTest类继承自QuidemTest，用于执行基于Chinook数据集的端到端SQL测试
  /** Runs a test from the command line.
   *
   * <p>For example:
   *
   * <blockquote>
   *   <code>java EndToEndTest sql/basic.iq</code>
   * </blockquote> */ // main方法注释：从命令行运行测试，例如执行java EndToEndTest sql/basic.iq命令
  public static void main(String[] args) throws Exception { // 程序入口方法，接收命令行参数数组，可能抛出异常
    for (String arg : args) { // 遍历命令行参数，每个参数代表一个测试文件路径
      new EndToEndTest().test(arg); // 创建EndToEndTest实例并调用test方法执行指定的测试文件
    }
  }

  /** For {@link QuidemTest#test(String)} parameters. */ // getPath方法注释：用于QuidemTest.test(String)方法的参数，返回测试文件路径集合
  @Override public Collection<String> getPath() { // 重写父类QuidemTest的getPath方法，返回测试文件路径集合
    // Start with a test file we know exists, then find the directory and list
    // its files.
    final String first = "sql/basic.iq"; // 定义第一个已知的测试文件路径sql/basic.iq，作为起始点
    return data(first); // 调用data方法，根据第一个文件路径获取该目录下所有测试文件的路径集合
  }

  @Override protected Quidem.ConnectionFactory createConnectionFactory() { // 重写父类方法，创建Quidem连接工厂
    return new ConnectionFactory(); // 返回一个新的ConnectionFactory实例，用于创建数据库连接
  }
} // 类结束花括号
