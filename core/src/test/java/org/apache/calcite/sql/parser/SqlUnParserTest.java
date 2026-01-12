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
// Apache许可证声明，遵循Apache 2.0开源协议
// 声明版权归属Apache软件基金会
// 定义使用许可条款：允许在遵守协议的前提下使用、修改和分发
// 指定许可版本：Apache License, Version 2.0
// 提供许可证获取链接
// 说明除非法律要求或书面同意，否则按"原样"提供，无任何明示或暗示的保证
package org.apache.calcite.sql.parser; // 声明包名：org.apache.calcite.sql.parser，表示这个类属于Calcite SQL解析器包

/**
 * Extension to {@link SqlParserTest} which ensures that every expression can
 * un-parse successfully.
 */
// 类文档注释：这是SqlParserTest的扩展类，用于确保每个表达式都能成功进行反解析
// SqlUnParserTest类：专门用于测试SQL反解析功能的测试类
// 继承自SqlParserTest，复用其测试框架和测试用例
// 主要目的是验证SQL解析后的抽象语法树(AST)能够正确地反解析回SQL字符串
// 反解析是SQL处理流程中的重要环节，确保解析和生成的双向一致性
class SqlUnParserTest extends SqlParserTest { // SqlUnParserTest类定义，继承SqlParserTest基类，使用默认访问权限（包级私有）
  // 继承关系说明：SqlUnParserTest是SqlParserTest的子类，专门用于反解析测试
  // 通过继承，可以复用SqlParserTest中定义的所有测试用例
  // 只需要重写fixture()方法来改变测试行为，使其包含反解析验证

  @Override public SqlParserFixture fixture() { // 重写fixture()方法，用@Override注解标识这是父类方法的重写
    // fixture()方法作用：创建并返回SqlParserFixture对象，该对象配置了测试环境和测试执行器
    // SqlParserFixture是测试框架的配置对象，定义了如何执行SQL解析测试
    // 返回类型：SqlParserFixture，表示测试配置对象
    // 访问修饰符：public，表示这个方法可以被外部访问
    return super.fixture() // 调用父类SqlParserTest的fixture()方法获取基础配置对象
        .withTester(new UnparsingTesterImpl()); // 使用withTester()方法设置自定义的测试器为UnparsingTesterImpl实例
    // withTester()方法作用：设置测试过程中使用的测试器实现
    // UnparsingTesterImpl是自定义的测试器实现，专门用于验证反解析功能
    // UnparsingTesterImpl会在测试过程中对每个解析后的SQL表达式进行反解析验证
    // 验证逻辑：解析SQL -> 生成AST -> 反解析AST -> 比较反解析结果与原始SQL
    // 通过链式调用，先获取父类配置，再添加反解析测试器，最后返回完整配置
  } // fixture()方法结束
} // SqlUnParserTest类定义结束
