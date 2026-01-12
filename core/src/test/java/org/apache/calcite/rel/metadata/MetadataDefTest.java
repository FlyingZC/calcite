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
package org.apache.calcite.rel.metadata; // 声明包名，表示这个类属于 org.apache.calcite.rel.metadata 包，是 Calcite 关系表达式元数据处理相关的包

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow; // 导入静态方法 assertDoesNotThrow，用于验证代码执行不会抛出异常

/**
 * Test cases for {@link MetadataDef}. // MetadataDef 类的测试用例
 * 
 * 【类的作用】：
 * MetadataDefTest 是一个单元测试类，用于测试 MetadataDef 类的行为和正确性。
 * 
 * MetadataDef 是 Calcite 元数据系统的核心类之一，它定义了元数据的类型、处理器类以及元数据接口中定义的方法。
 * 在创建 MetadataDef 实例时，系统会通过反射机制扫描处理器类中的方法，并验证这些方法是否符合规范。
 * 
 * 这个测试类主要验证以下两个关键功能：
 * 1. 处理器中的静态方法应该被正确忽略，不影响 MetadataDef 的创建
 * 2. 处理器中的合成方法（synthetic methods）应该被正确忽略，不影响 MetadataDef 的创建
 * 
 * 这两个测试用例确保了 MetadataDef 在扫描处理器方法时，能够正确过滤掉不应该被当作元数据处理方法的方法，
 * 从而保证元数据系统的稳定性和正确性。
 * 
 * 【测试背景】：
 * 在 Calcite 的元数据系统中，MetadataHandler 接口定义了元数据处理器的规范。
 * 当创建 MetadataDef 时，系统会调用 MetadataHandler.handlerMethods() 静态方法来扫描处理器类中的所有方法。
 * 这个方法会过滤掉以下类型的方法：
 * - getDef() 方法：这是接口定义的方法，不是处理方法
 * - 合成方法（synthetic methods）：编译器自动生成的方法
 * - 静态方法（static methods）：静态方法不依赖于实例，不能用于处理特定关系节点的元数据
 * 
 * 如果过滤逻辑不正确，可能会导致以下问题：
 * 1. 静态方法被当作处理方法，导致参数数量不匹配的验证失败
 * 2. 合成方法被当作处理方法，导致方法签名不匹配
 * 3. 方法数量验证失败，导致 MetadataDef 创建失败
 * 
 * 因此，这两个测试用例非常重要，它们确保了 MetadataDef 能够正确处理各种特殊情况。
 * 
 * 【测试方法】：
 * 1. staticMethodInHandlerIsIgnored()：测试包含静态方法的处理器是否能够正确创建 MetadataDef
 * 2. synthenticMethodInHandlerIsIgnored()：测试包含合成方法的处理器是否能够正确创建 MetadataDef
 * 
 * 两个测试方法都使用 assertDoesNotThrow 来验证 MetadataDef.of() 方法不会抛出异常。
 * 
 * 【相关类】：
 * - MetadataDef：被测试的类，定义元数据的类型和处理器
 * - MetadataHandler：元数据处理器的标记接口
 * - MetadataHandlerWithStaticMethod：包含静态方法的测试处理器
 * - TestMetadataHandlers：用于创建包含合成方法的测试处理器的工具类
 * - TestMetadata：测试用的元数据接口
 */
class MetadataDefTest { // 定义测试类 MetadataDefTest，使用默认访问修饰符（包级私有）
  @Test void staticMethodInHandlerIsIgnored() { // 定义测试方法，验证处理器中的静态方法应该被忽略
    // 【方法作用】：
    // 测试当元数据处理器包含静态方法时，MetadataDef.of() 方法是否能够正确创建 MetadataDef 实例。
    // 
    // 【测试原理】：
    // 1. MetadataDef.of() 方法会调用 MetadataHandler.handlerMethods() 来扫描处理器类中的方法
    // 2. handlerMethods() 方法会过滤掉静态方法（通过 !isStatic(m) 过滤器）
    // 3. 如果静态方法被正确过滤，那么剩余的方法数量应该与元数据接口中的方法数量匹配
    // 4. 如果静态方法没有被正确过滤，会导致方法数量不匹配，从而抛出 IllegalArgumentException
    // 
    // 【测试步骤】：
    // 1. 使用 assertDoesNotThrow 包装 MetadataDef.of() 调用
    // 2. 传入 TestMetadata.class 作为元数据类型
    // 3. 传入 MetadataHandlerWithStaticMethod.class 作为处理器类型
    // 4. 如果 MetadataDef.of() 成功创建实例，说明静态方法被正确忽略
    // 5. 如果抛出异常，说明静态方法没有被正确过滤，测试失败
    // 
    // 【预期结果】：
    // MetadataDef.of() 方法成功执行，不抛出任何异常
    // 
    // 【相关代码】：
    // MetadataHandlerWithStaticMethod 接口定义了一个静态方法 staticMethod()
    // 这个方法应该被 handlerMethods() 过滤掉
    assertDoesNotThrow(() -> // 使用 assertDoesNotThrow 验证 lambda 表达式中的代码不会抛出异常
        MetadataDef.of(TestMetadata.class, // 调用 MetadataDef.of() 静态方法，传入元数据接口类 TestMetadata.class
            MetadataHandlerWithStaticMethod.class)); // 传入包含静态方法的处理器类 MetadataHandlerWithStaticMethod.class
  } // 测试方法结束

  @Test void synthenticMethodInHandlerIsIgnored() { // 定义测试方法，验证处理器中的合成方法应该被忽略（注意：方法名中的 "synthentic" 是拼写错误，应该是 "synthetic"）
    // 【方法作用】：
    // 测试当元数据处理器包含合成方法（synthetic methods）时，MetadataDef.of() 方法是否能够正确创建 MetadataDef 实例。
    // 
    // 【什么是合成方法】：
    // 合成方法（synthetic methods）是 Java 编译器自动生成的方法，用于支持某些语言特性。
    // 常见的合成方法包括：
    // 1. 桥接方法（bridge methods）：用于支持泛型类型擦除后的方法重写
    // 2. 内部类的访问方法：用于让外部类访问内部类的私有成员
    // 3. Lambda 表达式生成的私有方法
    // 
    // 合成方法的标志是 ACC_SYNTHETIC 访问标志，可以通过 Method.isSynthetic() 方法检测。
    // 
    // 【测试原理】：
    // 1. MetadataDef.of() 方法会调用 MetadataHandler.handlerMethods() 来扫描处理器类中的方法
    // 2. handlerMethods() 方法会过滤掉合成方法（通过 !m.isSynthetic() 过滤器）
    // 3. 如果合成方法被正确过滤，那么剩余的方法数量应该与元数据接口中的方法数量匹配
    // 4. 如果合成方法没有被正确过滤，会导致方法数量不匹配，从而抛出 IllegalArgumentException
    // 
    // 【测试步骤】：
    // 1. 使用 assertDoesNotThrow 包装 MetadataDef.of() 调用
    // 2. 传入 TestMetadata.class 作为元数据类型
    // 3. 传入 TestMetadataHandlers.handlerClassWithSyntheticMethod() 创建的处理器类
    // 4. 这个处理器类使用 ByteBuddy 动态生成，包含一个合成方法
    // 5. 如果 MetadataDef.of() 成功创建实例，说明合成方法被正确忽略
    // 6. 如果抛出异常，说明合成方法没有被正确过滤，测试失败
    // 
    // 【预期结果】：
    // MetadataDef.of() 方法成功执行，不抛出任何异常
    // 
    // 【技术细节】：
    // TestMetadataHandlers.handlerClassWithSyntheticMethod() 使用 ByteBuddy 库动态创建一个类：
    // - 基于 BlankMetadataHandler 接口
    // - 添加一个名为 "syntheticMethod" 的方法
    // - 使用 SyntheticState.SYNTHETIC 标记为合成方法
    // - 使用 ClassLoadingStrategy.Default.CHILD_FIRST 加载策略
    // 
    // 【相关代码】：
    // TestMetadataHandlers.handlerClassWithSyntheticMethod() 方法使用 ByteBuddy 创建包含合成方法的处理器类
    assertDoesNotThrow(() -> // 使用 assertDoesNotThrow 验证 lambda 表达式中的代码不会抛出异常
        MetadataDef.of(TestMetadata.class, // 调用 MetadataDef.of() 静态方法，传入元数据接口类 TestMetadata.class
            TestMetadataHandlers.handlerClassWithSyntheticMethod())); // 传入包含合成方法的处理器类，通过 TestMetadataHandlers 工具类动态创建
  } // 测试方法结束
} // MetadataDefTest 类定义结束
