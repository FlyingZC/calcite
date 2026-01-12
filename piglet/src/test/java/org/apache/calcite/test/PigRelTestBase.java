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
// Apache许可证头，声明代码版权和使用许可
package org.apache.calcite.test; // 声明包名，表示该类属于org.apache.calcite.test测试包

import org.apache.calcite.piglet.PigConverter; // 导入PigConverter类，用于将Pig脚本转换为Calcite关系表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示Calcite的关系代数节点
import org.apache.calcite.tools.FrameworkConfig; // 导入FrameworkConfig类，用于配置Calcite框架

import org.junit.jupiter.api.BeforeEach; // 导入BeforeEach注解，用于标记在每个测试方法执行前运行的方法

import static org.apache.calcite.piglet.PigConverter.create; // 静态导入PigConverter的create方法，用于创建PigConverter实例
import static org.apache.calcite.test.PigRelBuilderTest.config; // 静态导入PigRelBuilderTest的config方法，用于获取框架配置

import static org.junit.jupiter.api.Assumptions.assumeFalse; // 静态导入assumeFalse方法，用于设置测试前提条件

import static java.lang.System.getProperty; // 静态导入getProperty方法，用于获取系统属性

/**
 * Abstract class for Pig to {@link RelNode} tests.
 * Pig到RelNode转换测试的抽象基类
 * 
 * 该类为所有Pig脚本到Calcite关系表达式(RelNode)的转换测试提供基础功能
 * 它封装了测试环境的初始化工作，包括PigConverter的创建和配置
 * 
 * <p>Under JDK 23 and higher, this test requires
 * "{@code -Djava.security.manager=allow}" command-line arguments due to
 * Hadoop's use of deprecated methods in {@link javax.security.auth.Subject}.
 * 在JDK 23及更高版本中，此测试需要"-Djava.security.manager=allow"命令行参数
 * 这是因为Hadoop使用了javax.security.auth.Subject类中的已弃用方法
 * 
 * These arguments are set automatically if you run via Gradle.
 * 如果通过Gradle运行，这些参数会自动设置
 */
public abstract class PigRelTestBase { // 定义抽象测试基类，所有Pig到RelNode的测试类都应继承此类
  PigConverter converter; // 成员变量：PigConverter实例，负责将Pig脚本转换为Calcite的关系表达式树

  @BeforeEach // JUnit5注解，标记该方法在每个测试方法执行前自动运行
  public void testSetup() throws Exception { // 测试设置方法，初始化测试环境，可能抛出异常
    assumeFalse(getProperty("os.name").startsWith("Windows"), // 检查操作系统是否为Windows，如果是则跳过测试
        "Skip: Pig/Hadoop tests do not work on Windows"); // 跳过原因：Pig/Hadoop测试在Windows系统上无法运行

    final FrameworkConfig config = config().build(); // 创建Calcite框架配置对象，通过PigRelBuilderTest的config方法获取配置构建器并构建
    converter = create(config); // 使用配置创建PigConverter实例，该实例将用于后续的Pig脚本转换
  }
}
