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
package org.apache.calcite.test; // 包声明：该类属于 org.apache.calcite.test 包，是测试包的一部分

import org.apache.calcite.server.DdlExecutor; // 导入 DdlExecutor 接口，用于定义 DDL（数据定义语言）执行器的抽象行为
import org.apache.calcite.sql.parser.SqlAbstractParserImpl; // 导入 SQL 解析器抽象实现类，是所有具体 SQL 解析器的基类
import org.apache.calcite.sql.parser.SqlParserImplFactory; // 导入 SQL 解析器工厂接口，用于创建解析器实例和 DDL 执行器
import org.apache.calcite.sql.parser.babel.SqlBabelParserImpl; // 导入 Babel SQL 解析器实现类，这是 Babel 方言的具体解析器

import java.io.Reader; // 导入 Reader 类，用于读取字符流，作为 SQL 解析器的输入源

/** Executes the few DDL commands supported by
 * {@link SqlBabelParserImpl}. */ // 类文档注释：该类用于执行 SqlBabelParserImpl 支持的少量 DDL 命令（数据定义语言，如 CREATE TABLE、CREATE VIEW 等）
public class BabelDdlExecutor extends MockDdlExecutor { // 类声明：BabelDdlExecutor 继承自 MockDdlExecutor，MockDdlExecutor 提供了 DDL 执行的基础实现
  static final BabelDdlExecutor INSTANCE = new BabelDdlExecutor(); // 静态常量：BabelDdlExecutor 的单例实例，用于在整个应用程序中共享同一个 DDL 执行器实例，避免重复创建

  /** Parser factory. */ // 成员变量注释：解析器工厂，用于创建 Babel SQL 解析器和 DDL 执行器
  @SuppressWarnings("unused") // used via reflection // 抑制未使用警告：该变量通过反射机制被使用，不是直接调用，所以忽略警告
  public static final SqlParserImplFactory PARSER_FACTORY = // 公共静态常量：SQL 解析器工厂实例，用于创建 Babel 方言的 SQL 解析器和对应的 DDL 执行器
      new SqlParserImplFactory() { // 匿名内部类：实现 SqlParserImplFactory 接口，提供自定义的解析器和 DDL 执行器创建逻辑
        @Override public SqlAbstractParserImpl getParser(Reader stream) { // 重写 getParser 方法：根据输入的字符流创建并返回一个 Babel SQL 解析器实例
          return SqlBabelParserImpl.FACTORY.getParser(stream); // 调用 Babel 解析器工厂的 getParser 方法，传入字符流，返回具体的 Babel SQL 解析器实例
        } // 方法结束：返回解析器实例

        @Override public DdlExecutor getDdlExecutor() { // 重写 getDdlExecutor 方法：返回该工厂对应的 DDL 执行器实例
          return BabelDdlExecutor.INSTANCE; // 返回 BabelDdlExecutor 的单例实例，该实例负责执行 Babel 方言的 DDL 命令
        } // 方法结束：返回 DDL 执行器实例
      }; // 匿名内部类结束：完成 SqlParserImplFactory 接口的实现
} // 类结束：BabelDdlExecutor 类定义完成
