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
// 声明包名，表示这个类属于 org.apache.calcite.rel.metadata 包，是 Calcite 关系表达式元数据处理相关的测试包
package org.apache.calcite.rel.metadata;

// 导入 Google Guava 库中的 ImmutableList 类，用于创建不可变的列表集合
import com.google.common.collect.ImmutableList;

// 导入 JUnit 5 的 Test 注解，用于标记测试方法
import org.junit.jupiter.api.Test;

// 导入 Java 反射相关的 Method 类，用于获取和操作方法信息
import java.lang.reflect.Method;
// 导入 Java 集合框架中的 List 接口，表示有序的元素集合
import java.util.List;
// 导入 Java 集合框架中的 SortedMap 接口，表示按键排序的映射集合
import java.util.SortedMap;

// 导入 Hamcrest 断言库中的 is 匹配器，用于判断值是否相等
import static org.hamcrest.CoreMatchers.is;
// 导入 Hamcrest 断言库中的 assertThat 方法，用于编写可读性强的断言
import static org.hamcrest.MatcherAssert.assertThat;
// 导入 Hamcrest 断言库中的 empty 匹配器，用于判断集合是否为空
import static org.hamcrest.Matchers.empty;
// 导入 Hamcrest 断言库中的 hasSize 匹配器，用于判断集合的大小
import static org.hamcrest.Matchers.hasSize;

/**
 * Tests for {@link MetadataHandler}.
 * 【类的作用】：
 * MetadataHandlerTest 是一个单元测试类，用于测试 MetadataHandler 接口的核心功能。
 * 
 * MetadataHandler 是 Calcite 元数据系统的核心接口，定义了元数据处理器的规范。
 * 元数据处理器负责计算和提供关系表达式（RelNode）的各种统计信息，如行数、唯一键、分布等。
 * 这些元数据对于查询优化器选择最优执行计划至关重要。
 * 
 * 本测试类主要验证以下功能：
 * 1. handlerMethods() 方法能够正确识别和提取元数据处理器中的有效处理方法
 * 2. handlerMethods() 方法能够正确过滤掉不应该被识别的方法，包括：
 *    - getDef() 方法：这是接口定义的方法，不是元数据处理方法
 *    - 静态方法（static methods）：静态方法不依赖于实例，不能处理特定关系节点的元数据
 *    - 合成方法（synthetic methods）：编译器自动生成的方法，如桥接方法
 * 
 * 测试方法的设计思路：
 * - 使用不同的测试接口来模拟各种场景
 * - 通过反射机制获取处理器类的方法信息
 * - 使用断言验证方法的过滤和提取逻辑是否正确
 * 
 * 这个测试类确保了元数据系统的核心功能能够正确工作，是 Calcite 查询优化器正确运行的基础保障。
 */
class MetadataHandlerTest { // 定义测试类，由于是测试类，不需要访问修饰符，默认为包级私有
  /**
   * 【方法作用】：
   * 测试 handlerMethods() 方法能够正确识别并提取元数据处理器中的有效处理方法。
   * 
   * 【测试场景】：
   * 使用 TestMetadataHandler 接口作为测试对象，该接口定义了一个有效的处理方法 getTestMetadata()。
   * 验证 handlerMethods() 方法能够：
   * 1. 正确识别 getTestMetadata() 方法为有效的处理方法
   * 2. 将该方法添加到返回的映射中
   * 3. 返回的映射中只包含这一个方法
   * 
   * 【测试步骤】：
   * 1. 调用 MetadataHandler.handlerMethods() 方法，传入 TestMetadataHandler.class
   * 2. 将返回的 SortedMap 转换为 List 以便进行断言验证
   * 3. 验证方法列表的大小为 1
   * 4. 验证唯一的方法名称为 "getTestMetadata"
   * 
   * 【预期结果】：
   * - 方法列表应该包含且仅包含一个方法
   * - 该方法的名称应该是 "getTestMetadata"
   * 
   * 【重要意义】：
   * 这个测试验证了元数据系统最基本的功能：能够正确识别处理方法。
   * 如果这个测试失败，意味着元数据系统无法正确提取处理方法，整个元数据查询机制将无法工作。
   */
  @Test // 使用 JUnit 5 的 @Test 注解标记这是一个测试方法
  void findsHandlerMethods() { // 定义测试方法，测试 handlerMethods 能够正确找到处理方法
    // 【代码说明】：
    // 调用 MetadataHandler.handlerMethods() 静态方法，传入 TestMetadataHandler 接口的 Class 对象
    // 该方法会通过反射扫描 TestMetadataHandler 接口中定义的所有方法
    // 并根据过滤规则（排除 getDef、静态方法、合成方法）提取出有效的处理方法
    // 返回值是一个 SortedMap，键为方法名，值为 Method 对象
    final SortedMap<String, Method> map = // 声明一个不可变的 SortedMap 变量 map，用于存储方法映射
        MetadataHandler.handlerMethods(TestMetadataHandler.class); // 调用 handlerMethods 方法，传入测试处理器类
    // 【代码说明】：
    // 将 SortedMap 的值（即 Method 对象集合）转换为 ImmutableList
    // ImmutableList 是 Guava 提供的不可变列表，线程安全且防止意外修改
    // copyOf() 方法会创建一个包含所有 Method 对象的新列表
    final List<Method> methods = // 声明一个不可变的 List 变量 methods，用于存储方法列表
        ImmutableList.copyOf(map.values()); // 将 map 的值集合转换为不可变列表

    // 【代码说明】：
    // 使用 Hamcrest 断言验证 methods 列表的大小是否为 1
    // hasSize(1) 是一个匹配器，用于检查集合的大小
    // 如果列表大小不是 1，断言会失败，测试用例会报错
    assertThat(methods, hasSize(1)); // 断言方法列表的大小为 1，表示只找到了一个处理方法
    // 【代码说明】：
    // 使用 Hamcrest 断言验证 methods 列表中第一个方法的名称是否为 "getTestMetadata"
    // methods.get(0) 获取列表中的第一个元素（即第一个 Method 对象）
    // .getName() 获取该方法的名称
    // is("getTestMetadata") 是一个匹配器，用于检查值是否等于 "getTestMetadata"
    assertThat(methods.get(0).getName(), is("getTestMetadata")); // 断言第一个方法的名称是 "getTestMetadata"
  } // 测试方法结束

  /**
   * 【方法作用】：
   * 测试 handlerMethods() 方法能够正确过滤掉 getDef() 方法。
   * 
   * 【测试场景】：
   * 使用 MetadataHandlerWithGetDefMethodOnly 接口作为测试对象，
   * 该接口只定义了 getDef() 方法，没有定义任何处理方法。
   * 验证 handlerMethods() 方法能够：
   * 1. 识别 getDef() 方法是接口定义的方法，不是处理方法
   * 2. 将 getDef() 方法从结果中过滤掉
   * 3. 返回一个空的映射
   * 
   * 【测试步骤】：
   * 1. 调用 MetadataHandler.handlerMethods() 方法，传入 MetadataHandlerWithGetDefMethodOnly.class
   * 2. 将返回的 SortedMap 转换为 List
   * 3. 验证方法列表为空
   * 
   * 【预期结果】：
   * - 方法列表应该是空的，不包含任何方法
   * 
   * 【重要意义】：
   * getDef() 是 MetadataHandler 接口定义的方法，用于返回元数据定义信息。
   * 它不是元数据处理方法，不应该被当作处理方法使用。
   * 这个测试确保了元数据系统能够正确区分接口定义的方法和实际的处理方法。
   */
  @Test // 使用 JUnit 5 的 @Test 注解标记这是一个测试方法
  void getDefMethodInHandlerIsIgnored() { // 定义测试方法，测试 getDef 方法会被正确忽略
    // 【代码说明】：
    // 调用 MetadataHandler.handlerMethods() 静态方法，传入 MetadataHandlerWithGetDefMethodOnly 接口的 Class 对象
    // 该接口只定义了 getDef() 方法，没有任何处理方法
    // handlerMethods() 方法应该能够识别 getDef() 方法并过滤掉它
    final SortedMap<String, Method> map = // 声明一个不可变的 SortedMap 变量 map，用于存储方法映射
        MetadataHandler.handlerMethods( // 调用 handlerMethods 方法
            MetadataHandlerWithGetDefMethodOnly.class); // 传入只包含 getDef 方法的测试处理器类
    // 【代码说明】：
    // 将 SortedMap 的值转换为 ImmutableList
    // 由于 getDef() 方法应该被过滤掉，这里应该得到一个空列表
    final List<Method> methods = // 声明一个不可变的 List 变量 methods，用于存储方法列表
        ImmutableList.copyOf(map.values()); // 将 map 的值集合转换为不可变列表

    // 【代码说明】：
    // 使用 Hamcrest 断言验证 methods 列表是否为空
    // empty() 是一个匹配器，用于检查集合是否为空
    // 如果列表不为空，断言会失败，测试用例会报错
    assertThat(methods, empty()); // 断言方法列表为空，表示 getDef 方法被正确过滤掉了
  } // 测试方法结束

  /**
   * 【方法作用】：
   * 测试 handlerMethods() 方法能够正确过滤掉静态方法（static methods）。
   * 
   * 【测试场景】：
   * 使用 MetadataHandlerWithStaticMethod 接口作为测试对象，
   * 该接口定义了一个静态方法。
   * 验证 handlerMethods() 方法能够：
   * 1. 识别静态方法不是实例方法，不能用于处理元数据
   * 2. 将静态方法从结果中过滤掉
   * 3. 返回一个空的映射
   * 
   * 【测试步骤】：
   * 1. 调用 MetadataHandler.handlerMethods() 方法，传入 MetadataHandlerWithStaticMethod.class
   * 2. 将返回的 SortedMap 转换为 List
   * 3. 验证方法列表为空
   * 
   * 【预期结果】：
   * - 方法列表应该是空的，不包含任何方法
   * 
   * 【重要意义】：
   * 静态方法不依赖于对象实例，无法访问特定关系节点的信息。
   * 元数据处理方法必须能够访问和操作特定的 RelNode 实例，因此必须是实例方法。
   * 这个测试确保了元数据系统能够正确过滤掉静态方法，避免将它们误认为是处理方法。
   */
  @Test // 使用 JUnit 5 的 @Test 注解标记这是一个测试方法
  void staticMethodInHandlerIsIgnored() { // 定义测试方法，测试静态方法会被正确忽略
    // 【代码说明】：
    // 调用 MetadataHandler.handlerMethods() 静态方法，传入 MetadataHandlerWithStaticMethod 接口的 Class 对象
    // 该接口定义了一个静态方法，handlerMethods() 方法应该能够识别并过滤掉它
    final SortedMap<String, Method> map = // 声明一个不可变的 SortedMap 变量 map，用于存储方法映射
        MetadataHandler.handlerMethods(MetadataHandlerWithStaticMethod.class); // 调用 handlerMethods 方法，传入包含静态方法的测试处理器类
    // 【代码说明】：
    // 将 SortedMap 的值转换为 ImmutableList
    // 由于静态方法应该被过滤掉，这里应该得到一个空列表
    final List<Method> methods = // 声明一个不可变的 List 变量 methods，用于存储方法列表
        ImmutableList.copyOf(map.values()); // 将 map 的值集合转换为不可变列表

    // 【代码说明】：
    // 使用 Hamcrest 断言验证 methods 列表是否为空
    // empty() 是一个匹配器，用于检查集合是否为空
    // 如果列表不为空，断言会失败，测试用例会报错
    assertThat(methods, empty()); // 断言方法列表为空，表示静态方法被正确过滤掉了
  } // 测试方法结束

  /**
   * 【方法作用】：
   * 测试 handlerMethods() 方法能够正确过滤掉合成方法（synthetic methods）。
   * 
   * 【测试场景】：
   * 使用动态生成的类作为测试对象，该类包含一个合成方法。
   * 合成方法是编译器自动生成的方法，例如：
   * - 桥接方法（bridge methods）：用于处理泛型类型擦除
   * - 默认方法的桥接方法
   * - 内部类的访问方法
   * 
   * 验证 handlerMethods() 方法能够：
   * 1. 识别合成方法是编译器生成的，不是程序员显式定义的
   * 2. 将合成方法从结果中过滤掉
   * 3. 返回一个空的映射
   * 
   * 【测试步骤】：
   * 1. 调用 TestMetadataHandlers.handlerClassWithSyntheticMethod() 动态生成一个包含合成方法的类
   * 2. 调用 MetadataHandler.handlerMethods() 方法，传入动态生成的类
   * 3. 将返回的 SortedMap 转换为 List
   * 4. 验证方法列表为空
   * 
   * 【预期结果】：
   * - 方法列表应该是空的，不包含任何方法
   * 
   * 【重要意义】：
   * 合成方法是编译器为了实现某些语言特性而自动生成的方法，它们不是程序员显式定义的处理方法。
   * 如果不过滤掉合成方法，可能会导致元数据系统识别到错误的方法，引发运行时错误。
   * 这个测试确保了元数据系统能够正确过滤掉合成方法，保证系统的稳定性和正确性。
   */
  @Test // 使用 JUnit 5 的 @Test 注解标记这是一个测试方法
  void syntheticMethodInHandlerIsIgnored() { // 定义测试方法，测试合成方法会被正确忽略
    // 【代码说明】：
    // 调用 TestMetadataHandlers.handlerClassWithSyntheticMethod() 方法动态生成一个包含合成方法的类
    // 这个方法使用 ByteBuddy 库在运行时动态创建一个类，并添加一个标记为 SYNTHETIC 的方法
    // 合成方法是编译器自动生成的方法，不是程序员显式定义的
    final SortedMap<String, Method> map = // 声明一个不可变的 SortedMap 变量 map，用于存储方法映射
        MetadataHandler.handlerMethods( // 调用 handlerMethods 方法
            TestMetadataHandlers.handlerClassWithSyntheticMethod()); // 传入动态生成的包含合成方法的测试处理器类
    // 【代码说明】：
    // 将 SortedMap 的值转换为 ImmutableList
    // 由于合成方法应该被过滤掉，这里应该得到一个空列表
    final List<Method> methods = // 声明一个不可变的 List 变量 methods，用于存储方法列表
        ImmutableList.copyOf(map.values()); // 将 map 的值集合转换为不可变列表

    // 【代码说明】：
    // 使用 Hamcrest 断言验证 methods 列表是否为空
    // empty() 是一个匹配器，用于检查集合是否为空
    // 如果列表不为空，断言会失败，测试用例会报错
    assertThat(methods, empty()); // 断言方法列表为空，表示合成方法被正确过滤掉了
  } // 测试方法结束

  /**
   * 【接口作用】：
   * TestMetadataHandler 是一个测试用的元数据处理器接口，用于测试 handlerMethods() 方法的基本功能。
   * 
   * 【接口设计】：
   * - 继承自 MetadataHandler<TestMetadata>，表示这是一个处理 TestMetadata 类型元数据的处理器
   * - 定义了一个处理方法 getTestMetadata()，用于获取测试元数据
   * - 使用 @SuppressWarnings("unused") 注解抑制编译器警告，因为该方法在测试中不会被直接调用
   * 
   * 【使用场景】：
   * 这个接口专门用于测试，用于验证 handlerMethods() 方法能够正确识别和提取有效的处理方法。
   * 在 findsHandlerMethods() 测试方法中，这个接口被用作测试对象。
   * 
   * 【重要意义】：
   * 这个接口提供了一个最小化的、符合规范的元数据处理器实现示例。
   * 通过这个简单的接口，可以验证元数据系统的核心功能是否正常工作。
   * 如果能够正确识别这个接口中的 getTestMetadata() 方法，说明 handlerMethods() 方法的基本逻辑是正确的。
   */
  /**
   * {@link MetadataHandler} which has a handler method.
   * 【注释说明】：这是一个包含处理方法的 MetadataHandler 接口，用于测试
   */
  interface TestMetadataHandler extends MetadataHandler<TestMetadata> { // 定义测试接口，继承自 MetadataHandler，处理 TestMetadata 类型元数据
    @SuppressWarnings("unused") // 抑制未使用警告，因为这个方法在测试中不会被直接调用，只是用于反射扫描
    TestMetadata getTestMetadata(); // 定义处理方法，返回 TestMetadata 类型的元数据，这是元数据处理器中定义的实际处理方法
  } // 测试接口定义结束

  /**
   * 【接口作用】：
   * MetadataHandlerWithGetDefMethodOnly 是一个测试用的元数据处理器接口，用于测试 handlerMethods() 方法对 getDef() 方法的过滤功能。
   * 
   * 【接口设计】：
   * - 继承自 MetadataHandler<TestMetadata>，表示这是一个处理 TestMetadata 类型元数据的处理器
   * - 只定义了 getDef() 方法，没有定义任何处理方法
   * - getDef() 方法返回 MetadataDef<TestMetadata>，这是 MetadataHandler 接口要求的方法
   * 
   * 【使用场景】：
   * 这个接口专门用于测试，用于验证 handlerMethods() 方法能够正确过滤掉 getDef() 方法。
   * 在 getDefMethodInHandlerIsIgnored() 测试方法中，这个接口被用作测试对象。
   * 
   * 【重要意义】：
   * getDef() 方法是 MetadataHandler 接口定义的方法，用于返回元数据定义信息。
   * 它不是元数据处理方法，不应该被 handlerMethods() 方法识别为处理方法。
   * 这个接口通过只包含 getDef() 方法，可以验证 handlerMethods() 方法是否能够正确区分接口定义的方法和处理方法。
   * 如果能够正确过滤掉 getDef() 方法，说明 handlerMethods() 方法的过滤逻辑是正确的。
   */
  /**
   * {@link MetadataHandler} which only has getDef() method.
   * 【注释说明】：这是一个只包含 getDef 方法的 MetadataHandler 接口，用于测试 getDef 方法是否会被过滤
   */
  interface MetadataHandlerWithGetDefMethodOnly extends MetadataHandler<TestMetadata> { // 定义测试接口，继承自 MetadataHandler，处理 TestMetadata 类型元数据
    MetadataDef<TestMetadata> getDef(); // 重写 getDef 方法，返回元数据定义对象，这是 MetadataHandler 接口要求的方法，不是处理方法
  } // 测试接口定义结束
} // 测试类定义结束
