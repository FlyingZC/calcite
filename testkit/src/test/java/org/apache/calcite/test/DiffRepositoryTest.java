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
package org.apache.calcite.test; // 包声明，该类属于org.apache.calcite.test包

import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.io.IOException; // 导入IO异常类，用于处理输入输出异常
import java.nio.file.Files; // 导入Files工具类，用于文件操作
import java.nio.file.Paths; // 导入Paths工具类，用于路径操作

import static org.hamcrest.CoreMatchers.containsString; // 导入Hamcrest匹配器，用于检查字符串包含关系
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest匹配器，用于检查相等性
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具，用于执行断言

/**
 * Tests for {@link DiffRepository} class. // DiffRepository类的测试类，用于测试DiffRepository的功能
 * 
 * 该测试类主要验证DiffRepository的以下核心功能：
 * 1. 当断言失败时，日志文件是否正确更新
 * 2. 当测试方法仅在XML文件中定义时，是否能正确检测到
 * 
 * DiffRepository是Calcite测试框架中用于管理测试资源的核心类，它：
 * - 从XML参考文件中加载测试期望值
 * - 在测试失败时将实际值写入日志文件
 * - 支持变量展开（如${content}）
 * - 管理测试用例的生命周期
 * 
 * 测试方法：
 * - testAssertEqualsUpdatesLogFileUponFailure: 验证断言失败时日志文件的更新
 * - testMethodOnlyExistsInXml: 验证仅在XML中定义的测试方法的检测
 */
public class DiffRepositoryTest { // 测试类定义，继承自Object（默认）

  @Test void testAssertEqualsUpdatesLogFileUponFailure() throws IOException { // 测试方法：验证断言失败时日志文件更新
    DiffRepository r = DiffRepository.lookup(DiffRepositoryTest.class); // 获取DiffRepository实例，通过类名查找对应的仓库
    final String actual = "Random sentence not present in resources file"; // 定义实际值字符串，该字符串不在参考文件中，用于触发断言失败
    boolean assertPassed = false; // 初始化断言标志为false，用于记录断言是否通过
    try { // 开始try块，执行可能抛出异常的代码
      r.assertEquals("content", "${content}", actual); // 调用assertEquals方法，期望从XML中获取content变量的值，与actual比较，由于actual不在参考文件中，会失败
      assertPassed = true; // 如果没有抛出异常，设置标志为true
    } catch (AssertionError e) { // 捕获断言错误异常
      String logContent = String.join("", Files.readAllLines(Paths.get(r.logFilePath()))); // 读取日志文件内容，将所有行合并为一个字符串
      assertThat(logContent, containsString(actual)); // 验证日志内容包含actual字符串，确认实际值已被写入日志文件
    }
    assertThat("First assertion must always fail", assertPassed, is(false)); // 验证断言标志为false，确保第一次断言确实失败了
  }

  @Test void testMethodOnlyExistsInXml() { // 测试方法：验证仅在XML中定义的测试方法的检测
    boolean assertPassed = false; // 初始化断言标志为false，用于记录断言是否通过
    DiffRepository r = DiffRepository.lookup(DiffRepositoryTest.class); // 获取DiffRepository实例，通过类名查找对应的仓库
    final String actual = "testMethodOnlyExistsInXml1"; // 定义测试方法名，该方法仅在XML文件中定义，不在Java代码中
    try { // 开始try块，执行可能抛出异常的代码
      r.checkActualAndReferenceFiles(); // 调用checkActualAndReferenceFiles方法，检查实际文件和参考文件是否一致
      assertPassed = true; // 如果没有抛出异常，设置标志为true
    } catch (IllegalArgumentException e) { // 捕获非法参数异常
      assertThat(e.getMessage(), containsString(actual)); // 验证异常消息包含actual字符串，确认检测到了仅在XML中定义的测试方法
    }
    assertThat("First assertion must always fail", assertPassed, is(false)); // 验证断言标志为false，确保第一次断言确实失败了
  }
}
