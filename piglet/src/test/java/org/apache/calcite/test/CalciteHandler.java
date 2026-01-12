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
// Apache许可证声明，说明此代码遵循Apache 2.0许可证
package org.apache.calcite.test; // 定义包名，表示此类属于org.apache.calcite.test包

import org.apache.calcite.piglet.Handler; // 导入Handler基类，CalciteHandler继承自此类
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系代数节点，是Calcite中关系表达式的基本单元
import org.apache.calcite.tools.PigRelBuilder; // 导入PigRelBuilder类，用于构建Pig风格的RelNode
import org.apache.calcite.tools.RelRunners; // 导入RelRunners类，用于执行RelNode并生成PreparedStatement
import org.apache.calcite.util.TestUtil; // 导入TestUtil工具类，提供测试相关的辅助方法

import java.io.PrintWriter; // 导入PrintWriter类，用于向输出流写入文本
import java.io.Writer; // 导入Writer类，表示字符输出流的抽象类
import java.sql.Array; // 导入Array接口，表示SQL数组的映射
import java.sql.PreparedStatement; // 导入PreparedStatement接口，表示预编译的SQL语句对象
import java.sql.ResultSet; // 导入ResultSet接口，表示数据库查询结果集
import java.sql.SQLException; // 导入SQLException类，表示数据库操作异常
import java.sql.Types; // 导入Types类，定义JDBC中通用的SQL类型常量

/**
 * Extension to {@link org.apache.calcite.piglet.Handler} that can execute
 * commands using Calcite.
 */
// 类注释：CalciteHandler是Handler类的扩展，能够使用Calcite执行命令
// 这个类主要用于在测试环境中执行和转储关系代数查询的结果
// 它将RelNode转换为可执行的SQL语句，并输出结果
class CalciteHandler extends Handler { // CalciteHandler继承自Handler基类，扩展了执行和输出功能
  private final PrintWriter writer; // 成员变量：writer用于输出查询结果，final表示初始化后不可修改

  CalciteHandler(PigRelBuilder builder, Writer writer) { // 构造方法：接收PigRelBuilder和Writer参数
    super(builder); // 调用父类Handler的构造方法，传入builder参数
    this.writer = new PrintWriter(writer); // 将Writer包装为PrintWriter并赋值给成员变量writer
  } // 构造方法结束

  @Override protected void dump(RelNode rel) { // 重写父类的dump方法，用于转储RelNode的执行结果
    dump(rel, writer); // 调用静态dump方法，传入rel和writer参数
  } // dump方法结束

  public static void dump(RelNode rel, Writer writer) { // 静态方法：执行RelNode并将结果输出到Writer
    try (PreparedStatement preparedStatement = RelRunners.run(rel)) { // 使用RelRunners将RelNode转换为PreparedStatement，try-with-resources确保资源自动关闭
      final ResultSet resultSet = preparedStatement.executeQuery(); // 执行查询，获得ResultSet结果集
      dump(resultSet, true, new PrintWriter(writer)); // 调用dump方法转储ResultSet，newline参数为true表示每行输出后换行
    } catch (SQLException e) { // 捕获SQL异常
      throw TestUtil.rethrow(e); // 使用TestUtil.rethrow重新抛出异常，保持异常链
    } // try-catch块结束
  } // 静态dump方法结束

  private static void dump(ResultSet resultSet, boolean newline, PrintWriter writer) // 私有静态方法：转储ResultSet内容到Writer
      throws SQLException { // 声明可能抛出SQLException异常
    final int columnCount = resultSet.getMetaData().getColumnCount(); // 获取结果集的列数
    int r = 0; // 初始化行计数器r为0
    while (resultSet.next()) { // 遍历结果集的每一行
      if (!newline && r++ > 0) { // 如果不需要换行且不是第一行，则在行前输出逗号
        writer.print(","); // 输出逗号分隔符
      } // if结束
      if (columnCount == 0) { // 如果结果集没有列
        if (newline) { // 如果需要换行
          writer.println("()"); // 输出空元组()并换行
        } else { // 如果不需要换行
          writer.print("()"); // 输出空元组()不换行
        } // if-else结束
      } else { // 如果结果集有列
        writer.print('('); // 输出左括号开始元组
        dumpColumn(resultSet, 1, writer); // 转储第一列
        for (int i = 2; i <= columnCount; i++) { // 遍历剩余列（从第2列开始）
          writer.print(','); // 输出列分隔符逗号
          dumpColumn(resultSet, i, writer); // 转储第i列
        } // for循环结束
        if (newline) { // 如果需要换行
          writer.println(')'); // 输出右括号并换行
        } else { // 如果不需要换行
          writer.print(")"); // 输出右括号不换行
        } // if-else结束
      } // if-else结束
    } // while循环结束
  } // 私有静态dump方法结束

  /** Dumps a column value.
   *
   * @param i Column ordinal, 1-based
   */
  // 方法注释：转储结果集中指定列的值
  // 参数i表示列的序号，从1开始计数
  private static void dumpColumn(ResultSet resultSet, int i, PrintWriter writer) // 私有静态方法：转储结果集中第i列的值
      throws SQLException { // 声明可能抛出SQLException异常
    final int t = resultSet.getMetaData().getColumnType(i); // 获取第i列的SQL类型
    switch (t) { // 根据列类型进行不同的处理
    case Types.ARRAY: // 如果列类型是数组
      final Array array = resultSet.getArray(i); // 获取数组对象
      writer.print("{"); // 输出数组开始标记{
      if (array != null) { // 如果数组不为null
        dump(array.getResultSet(), false, writer); // 递归转储数组内容，newline为false表示不换行
      } // if结束
      writer.print("}"); // 输出数组结束标记}
      return; // 返回，结束方法
    case Types.REAL: // 如果列类型是REAL（浮点数）
      writer.print(resultSet.getString(i)); // 输出列值的字符串表示
      writer.print("F"); // 输出后缀F表示这是浮点数
      return; // 返回，结束方法
    default: // 对于其他类型
      writer.print(resultSet.getString(i)); // 直接输出列值的字符串表示
    } // switch结束
  } // dumpColumn方法结束
} // CalciteHandler类结束
