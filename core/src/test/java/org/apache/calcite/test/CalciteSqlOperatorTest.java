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
package org.apache.calcite.test; // 声明包名，表示该类属于org.apache.calcite.test包

import org.apache.calcite.sql.test.SqlOperatorFixture; // 导入SqlOperatorFixture类，用于提供SQL操作符测试的测试环境配置

/**
 * Embodiment of {@link SqlOperatorTest} // 类的Javadoc注释：这是SqlOperatorTest的具体实现类
 * that generates SQL statements and executes them using Calcite. // 该类生成SQL语句并使用Calcite执行它们，用于测试SQL操作符
 */ // Javadoc注释结束
class CalciteSqlOperatorTest extends SqlOperatorTest { // 定义CalciteSqlOperatorTest类，继承自SqlOperatorTest基类，专门用于Calcite引擎的SQL操作符测试
  @Override protected SqlOperatorFixture fixture() { // 重写父类的fixture()方法，使用@Override注解表示这是方法重写，返回SqlOperatorFixture对象用于配置测试环境
    return super.fixture() // 调用父类的fixture()方法获取基础的SqlOperatorFixture对象
        .withTester(t -> TESTER); // 使用withTester方法设置测试器，传入lambda表达式t -> TESTER，指定使用TESTER作为测试器实例来执行SQL语句
  } // fixture()方法结束
} // CalciteSqlOperatorTest类定义结束
