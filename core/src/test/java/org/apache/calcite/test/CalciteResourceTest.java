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
// Apache许可证声明，说明代码版权和使用许可
package org.apache.calcite.test; // 定义包名，表示这个测试类属于org.apache.calcite.test包

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.util.Map; // 导入Map接口，用于存储键值对数据

import static org.apache.calcite.util.Static.RESOURCE; // 静态导入Calcite资源工具类，用于访问国际化资源

import static org.hamcrest.CoreMatchers.is; // 静态导入Hamcrest的is匹配器，用于断言值相等
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入Hamcrest的断言方法，用于验证测试结果

/**
 * Tests the generated implementation of
 * {@link org.apache.calcite.runtime.CalciteResource} (mostly a sanity check for
 * the resource-generation infrastructure).
 */
// 测试类文档注释：测试CalciteResource生成实现的功能（主要是对资源生成基础设施的健康检查）
// CalciteResource是Calcite框架中用于国际化错误消息和资源的核心类
// 这个测试类验证资源生成机制是否正常工作，包括SQLSTATE等错误代码的可用性
class CalciteResourceTest { // 定义测试类CalciteResourceTest，用于测试Calcite资源相关功能
  /**
   * Verifies that resource properties such as SQLSTATE are available at
   * runtime.
   */
  // 方法文档注释：验证资源属性（如SQLSTATE）在运行时是否可用
  // SQLSTATE是SQL标准定义的错误代码，用于标识特定类型的SQL错误
  // 这个测试确保资源生成机制能够正确生成和访问这些属性
  @Test void testSqlstateProperty() { // 使用@Test注解标记测试方法，验证SQLSTATE属性功能
    Map<String, String> props = // 声明一个Map变量props，用于存储资源属性键值对
        RESOURCE.illegalIntervalLiteral("", "").getProperties(); // 获取非法间隔字面量资源的属性，illegalIntervalLiteral是CalciteResource中定义的错误消息方法，传入两个空字符串作为参数，然后调用getProperties()方法获取属性Map
    assertThat(props.get("SQLSTATE"), is("42000")); // 断言验证：从props中获取"SQLSTATE"键对应的值，验证其是否为"42000"，42000是SQL标准中的语法错误或访问违规错误代码
  }
} // 类定义结束
