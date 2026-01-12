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
package org.apache.calcite.rel.metadata; // org.apache.calcite.rel.metadata 包，包含 Calcite 关系表达式元数据处理的类和接口

/**
 * A {@link MetadataHandler} having a static method. // 一个包含静态方法的 MetadataHandler 接口，用于测试元数据处理器是否支持静态方法
 * 
 * 该接口用于测试 Calcite 的元数据处理器（MetadataHandler）是否能够正确处理包含静态方法的情况。
 * 在 Calcite 中，MetadataHandler 是处理关系表达式元数据的接口，用于计算和缓存各种元数据信息，
 * 例如行数、选择性、唯一键等。这个测试接口继承自 MetadataHandler<TestMetadata>，
 * 并添加了一个静态方法，用于验证元数据处理器框架对静态方法的兼容性。
 * 
 * 接口作用：
 * 1. 测试 MetadataHandler 是否能够正确继承包含静态方法的接口
 * 2. 验证元数据处理器框架对静态方法的处理机制
 * 3. 确保 MetadataHandler 的反射机制不会因为静态方法的存在而出现异常
 * 4. 为元数据处理器的设计和实现提供测试用例
 * 
 * 接口继承关系：
 * - 继承自 MetadataHandler<TestMetadata>，表示这是一个处理 TestMetadata 类型元数据的处理器
 * - TestMetadata 是一个测试用的元数据接口
 */
interface MetadataHandlerWithStaticMethod extends MetadataHandler<TestMetadata> { // 接口定义：包含静态方法的元数据处理器，继承自处理 TestMetadata 元数据的 MetadataHandler

  @SuppressWarnings("unused") // 抑制"未使用"警告，因为这是一个测试用的静态方法，在测试代码中可能不会被直接调用
  static void staticMethod() { // 静态方法定义：用于测试元数据处理器对静态方法的支持，方法体为空
    // do nothing // 空方法体，不做任何操作，仅用于测试静态方法的存在不会影响元数据处理器的功能
  }
} // 接口结束
