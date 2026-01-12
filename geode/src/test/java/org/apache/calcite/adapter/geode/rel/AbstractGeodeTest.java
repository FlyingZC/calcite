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
 */ // Apache软件基金会许可证声明，定义了代码的使用权限和限制
package org.apache.calcite.adapter.geode.rel; // 声明该类所属的包，位于org.apache.calcite.adapter.geode.rel包下，这是Calcite适配器中Geode相关的关系型类包

import org.junit.jupiter.api.extension.RegisterExtension; // 导入JUnit 5的RegisterExtension注解，用于注册测试扩展点
import org.junit.jupiter.api.parallel.Execution; // 导入JUnit 5的Execution注解，用于配置测试的执行模式
import org.junit.jupiter.api.parallel.ExecutionMode; // 导入ExecutionMode枚举，定义了测试的并发执行模式

/**
 * Base class that allows sharing same geode instance across all tests.
 * 基础测试类，允许在所有测试之间共享同一个Geode实例
 *
 * <p>Also, due to legacy reasons, there can't be more than one Geode
 * instance (running in parallel) for a single JVM.
 * 此外，由于历史原因，单个JVM中不能同时运行多个Geode实例（并行运行）
 */ // 类级别的Javadoc注释，说明了该测试基类的作用和限制条件
@Execution(ExecutionMode.CONCURRENT) // 使用JUnit 5的并发执行模式，允许测试方法并发执行
public abstract class AbstractGeodeTest { // 定义一个抽象类AbstractGeodeTest，作为所有Geode适配器测试的基类，抽象类不能直接实例化，必须被子类继承

  @RegisterExtension // 使用JUnit 5的扩展注册机制，将POLICY字段注册为测试扩展，使其在测试生命周期中自动管理
  public static final GeodeEmbeddedPolicy POLICY = GeodeEmbeddedPolicy.create().share(); // 声明一个公共静态常量POLICY，类型为GeodeEmbeddedPolicy，通过create()工厂方法创建实例，并调用share()方法使其在所有测试间共享，这样可以确保所有测试使用同一个Geode实例，避免资源冲突

} // 类定义结束，这是一个非常简洁的测试基类，主要作用是提供共享的Geode实例管理策略
