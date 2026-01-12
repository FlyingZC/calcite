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
package org.apache.calcite.test.concurrent; // 定义包名，该类位于org.apache.calcite.test.concurrent包下，用于并发测试相关的插件功能

import java.sql.PreparedStatement; // 导入PreparedStatement类，用于表示预编译的SQL语句对象
import java.sql.ResultSetMetaData; // 导入ResultSetMetaData类，用于获取结果集的元数据信息（如列名、列类型等）
import java.sql.SQLException; // 导入SQLException类，用于处理SQL操作中可能出现的异常
import java.sql.Statement; // 导入Statement类，用于表示执行SQL语句的对象
import java.util.Arrays; // 导入Arrays类，用于创建固定大小的列表

/**
 * Sample mtsql plugin. // 示例多线程SQL插件，用于在并发测试环境中扩展SQL脚本的功能
 *
 * <p>To use add at start of script "@plugin // 使用方法：在脚本开头添加"@plugin org.apache.calcite.test.concurrent.SamplePlugin"来加载此插件
 * org.apache.calcite.test.concurrent.SamplePlugin".  After doing a prepare you // 在执行prepare操作后，可以使用"@describeResultSet"命令来显示查询返回的列信息
 * can then do "@describeResultSet" to show columns returned by query. // 该命令会展示结果集中所有列的名称和类型信息
 */
public class SamplePlugin extends ConcurrentTestPlugin { // SamplePlugin类继承自ConcurrentTestPlugin，实现了并发测试插件的基本框架
  private static final String DESCRIBE_RESULT_SET_CMD = "@describeResultSet"; // 定义私有静态常量，存储描述结果集命令的名称，用于识别和处理该特定命令

  public ConcurrentTestPluginCommand getCommandFor(String name, String params) { // 根据命令名称和参数获取对应的插件命令对象，这是插件框架的核心方法，用于将命令字符串映射到具体的命令执行器
    if (name.equals(DESCRIBE_RESULT_SET_CMD)) { // 判断传入的命令名称是否等于描述结果集命令的名称
      return new DescribeResultSet(); // 如果匹配，则创建并返回一个新的DescribeResultSet命令对象，用于执行结果集描述操作
    }
    assert false; // 如果命令名称不匹配任何已知命令，断言失败，表示不应该执行到这里
    return null; // 返回null，表示没有找到对应的命令处理器
  }

  public Iterable<String> getSupportedThreadCommands() { // 获取该插件支持的所有线程命令列表，用于向测试框架注册插件支持的命令
    return Arrays.asList(new String[]{DESCRIBE_RESULT_SET_CMD}); // 返回包含描述结果集命令的列表，使用Arrays.asList创建不可变的列表
  }

  /** Command that describes a result set. */ // 内部静态类：描述结果集的命令，实现了ConcurrentTestPluginCommand接口，用于执行结果集描述的具体逻辑
  static class DescribeResultSet implements ConcurrentTestPluginCommand { // DescribeResultSet类实现了ConcurrentTestPluginCommand接口，定义了如何执行结果集描述操作
    public void execute(TestContext testContext) { // 执行命令的方法，接收测试上下文对象作为参数，该对象包含了当前测试状态和所有必要的信息
      Statement stmt = testContext.getCurrentStatement(); // 从测试上下文中获取当前活动的Statement对象，该对象包含了最近执行的SQL语句信息
      if (stmt == null) { // 检查当前Statement对象是否为null，表示没有活动的语句
        testContext.storeMessage("No current statement"); // 如果没有当前语句，则在测试上下文中存储一条错误消息，提示用户没有可用的语句
      } else if (stmt instanceof PreparedStatement) { // 检查当前Statement对象是否是PreparedStatement的实例，即是否为预编译语句
        try { // 开始try块，用于捕获可能出现的SQL异常
          ResultSetMetaData metadata = // 声明ResultSetMetaData变量，用于存储结果集的元数据信息
              ((PreparedStatement) stmt).getMetaData(); // 将Statement强制转换为PreparedStatement并调用getMetaData()方法获取结果集的元数据，包含列的详细信息
          for (int i = 1; i <= metadata.getColumnCount(); i++) { // 遍历结果集的所有列，从第1列开始到总列数结束
            testContext.storeMessage( // 在测试上下文中存储消息，用于输出列信息
                metadata.getColumnName(i) + ": " // 获取当前列的名称并添加冒号分隔符
                    + metadata.getColumnTypeName(i)); // 获取当前列的类型名称并拼接到消息中，格式为"列名: 类型名"
          }
        } catch (SQLException e) { // 捕获SQL异常，处理在获取元数据过程中可能出现的数据库错误
          throw new IllegalStateException(e.toString()); // 将SQL异常转换为运行时异常IllegalStateException并抛出，中断当前操作
        }
      }
    }
  }
}
