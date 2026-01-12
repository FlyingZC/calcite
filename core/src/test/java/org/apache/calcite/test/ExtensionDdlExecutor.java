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
package org.apache.calcite.test; // 定义包名，表示该类位于org.apache.calcite.test包下，这是Calcite测试包，用于存放测试相关的类

import org.apache.calcite.server.DdlExecutor; // 导入DdlExecutor接口，这是Calcite中DDL（数据定义语言）执行器的接口，定义了执行DDL语句的标准方法
import org.apache.calcite.sql.SqlDataTypeSpec; // 导入SqlDataTypeSpec类，表示SQL数据类型规范，用于描述SQL中的数据类型（如VARCHAR, INTEGER等）
import org.apache.calcite.sql.SqlIdentifier; // 导入SqlIdentifier类，表示SQL标识符，用于表示SQL中的名称（如表名、列名、函数名等）
import org.apache.calcite.sql.ddl.SqlCreateTable; // 导入SqlCreateTable类，表示SQL CREATE TABLE语句的抽象语法树节点，用于创建表的操作
import org.apache.calcite.sql.parser.SqlAbstractParserImpl; // 导入SqlAbstractParserImpl抽象类，这是Calcite SQL解析器的抽象实现，提供了SQL解析的基础功能
import org.apache.calcite.sql.parser.SqlParserImplFactory; // 导入SqlParserImplFactory接口，这是SQL解析器实现的工厂接口，用于创建解析器实例
import org.apache.calcite.sql.parser.parserextensiontesting.ExtensionSqlCreateTable; // 导入ExtensionSqlCreateTable类，这是扩展的CREATE TABLE语句实现，用于测试Calcite的SQL扩展功能
import org.apache.calcite.sql.parser.parserextensiontesting.ExtensionSqlParserImpl; // 导入ExtensionSqlParserImpl类，这是扩展的SQL解析器实现，支持自定义的SQL语法扩展

import java.io.Reader; // 导入Reader类，Java IO包中的字符流读取器，用于读取SQL语句的字符流
import java.util.function.BiConsumer; // 导入BiConsumer函数式接口，表示接受两个参数且无返回值的操作，用于遍历表名和类型时的消费操作

/** Executes the few DDL commands supported by
 * {@link ExtensionSqlParserImpl}. */ // 类注释：该类用于执行ExtensionSqlParserImpl支持的少量DDL命令，是扩展SQL解析器的DDL执行器实现
public class ExtensionDdlExecutor extends MockDdlExecutor { // 定义ExtensionDdlExecutor类，继承自MockDdlExecutor，这是一个模拟的DDL执行器，用于测试Calcite的DDL功能扩展
  static final ExtensionDdlExecutor INSTANCE = new ExtensionDdlExecutor(); // 定义静态常量INSTANCE，这是ExtensionDdlExecutor的单例实例，用于全局共享同一个执行器对象

  /** Parser factory. */ // 成员变量注释：PARSER_FACTORY是一个解析器工厂，用于创建扩展的SQL解析器和DDL执行器
  @SuppressWarnings("unused") // used via reflection // 抑制未使用警告，说明该变量通过反射机制被使用，避免编译器警告
  public static final SqlParserImplFactory PARSER_FACTORY = // 定义静态常量PARSER_FACTORY，类型为SqlParserImplFactory，这是一个解析器工厂的静态实例
      new SqlParserImplFactory() { // 创建SqlParserImplFactory接口的匿名实现类，用于提供自定义的解析器和执行器创建逻辑
        @Override public SqlAbstractParserImpl getParser(Reader stream) { // 重写getParser方法，用于创建SQL解析器实例，接受Reader参数（SQL字符流）
          return ExtensionSqlParserImpl.FACTORY.getParser(stream); // 调用ExtensionSqlParserImpl的FACTORY工厂方法，传入字符流，返回扩展的SQL解析器实例
        }

        @Override public DdlExecutor getDdlExecutor() { // 重写getDdlExecutor方法，用于获取DDL执行器实例
          return ExtensionDdlExecutor.INSTANCE; // 返回ExtensionDdlExecutor的单例实例INSTANCE，确保整个应用使用同一个执行器
        }
      }; // 匿名内部类的结束，完成了SqlParserImplFactory的实现

  @Override protected void forEachNameType(SqlCreateTable createTable, BiConsumer<SqlIdentifier, // 重写forEachNameType方法，用于遍历CREATE TABLE语句中的列名和类型，接受SqlCreateTable和BiConsumer参数
      SqlDataTypeSpec> consumer) { // BiConsumer参数类型定义，接受SqlIdentifier（列名）和SqlDataTypeSpec（数据类型）两个参数
    ((ExtensionSqlCreateTable) createTable).forEachNameType(consumer); // 将createTable强制转换为ExtensionSqlCreateTable类型，然后调用其forEachNameType方法，传入consumer来遍历列名和类型
  } // forEachNameType方法结束，完成了对CREATE TABLE语句列定义的遍历处理
} // ExtensionDdlExecutor类定义结束
