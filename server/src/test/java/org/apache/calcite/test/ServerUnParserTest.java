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
// Apache许可证头部声明,声明代码归属和使用条款
package org.apache.calcite.test; // 定义包名,属于Calcite项目的测试包

import org.apache.calcite.sql.parser.SqlParserFixture; // 导入SqlParserFixture类,用于配置SQL解析器的测试夹具

/**
 * Extension to {@link ServerParserTest} that ensures that every expression can
 * un-parse successfully.
 * 类作用说明:ServerUnParserTest是ServerParserTest的扩展类,专门用于测试SQL语句的反解析(un-parsing)功能
 * 反解析是指将解析后的SQL语法树(SqlNode)重新转换为SQL字符串的过程
 * 该类确保每个能够成功解析的SQL表达式也能够成功地进行反解析,保证解析和反解析的往返一致性
 * 通过使用UnparsingTesterImpl测试器,该类会验证所有DDL语句(如CREATE、DROP等)在解析后能够正确地重新生成SQL文本
 * 这是SQL解析器质量保证的重要环节,确保解析器能够完整地保留SQL语句的语义信息
 */
class ServerUnParserTest extends ServerParserTest { // 类定义:ServerUnParserTest继承自ServerParserTest,专注于DDL语句的反解析测试
  //~ Methods ---------------------------------------------------------------- // 方法分隔符注释,标记下面是方法定义区域

  @Override public SqlParserFixture fixture() { // 重写fixture方法:配置SQL解析器测试夹具,返回自定义的解析器配置
    return super.fixture() // 调用父类的fixture方法获取默认配置(已配置为支持DDL的SqlDdlParserImpl)
        .withTester(new UnparsingTesterImpl()); // 使用withTester方法配置测试器为UnparsingTesterImpl实例,该测试器会执行反解析验证
  } // 方法结束:fixture方法返回配置好的SqlParserFixture对象,该对象会在后续测试中使用,确保每个SQL语句都经过解析和反解析的往返测试
} // 类结束:ServerUnParserTest类定义结束,该类通过重写fixture方法,为ServerParserTest的所有DDL测试添加了反解析验证功能
