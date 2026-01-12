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
package org.apache.calcite.test; // 包声明：该测试类位于 org.apache.calcite.test 包下，用于测试 Calcite 框架中的 Puffin 工具类

import org.apache.calcite.runtime.Unit; // 导入 Unit 类型，表示无返回值的单元类型
import org.apache.calcite.util.Puffin; // 导入 Puffin 工具类，用于处理文本流的程序化处理
import org.apache.calcite.util.Source; // 导入 Source 接口，表示数据源的抽象
import org.apache.calcite.util.Sources; // 导入 Sources 工具类，用于创建 Source 实例

import org.hamcrest.Matcher; // 导入 Hamcrest 匹配器接口，用于断言验证
import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，标记测试方法

import java.io.PrintWriter; // 导入 PrintWriter 类，用于向输出流写入文本
import java.io.StringWriter; // 导入 StringWriter 类，用于在内存中构建字符串
import java.util.ArrayList; // 导入 ArrayList 类，用于动态数组列表
import java.util.List; // 导入 List 接口，表示有序集合
import java.util.concurrent.atomic.AtomicInteger; // 导入 AtomicInteger 类，用于原子性整数操作，线程安全
import java.util.function.Consumer; // 导入 Consumer 函数式接口，表示接受单个参数的操作
import java.util.stream.Stream; // 导入 Stream 接口，用于流式数据处理

import static org.apache.calcite.test.Matchers.isLinux; // 导入 isLinux 匹配器，用于验证 Linux 风格的换行符

import static org.hamcrest.CoreMatchers.hasItem; // 导入 hasItem 匹配器，验证集合包含特定元素
import static org.hamcrest.CoreMatchers.is; // 导入 is 匹配器，用于相等性验证
import static org.hamcrest.CoreMatchers.notNullValue; // 导入 notNullValue 匹配器，验证非空值
import static org.hamcrest.MatcherAssert.assertThat; // 导入 assertThat 静态断言方法，用于验证测试条件
import static org.hamcrest.Matchers.hasSize; // 导入 hasSize 匹配器，验证集合大小
import static org.hamcrest.Matchers.hasToString; // 导入 hasToString 匹配器，验证字符串表示

/** Tests {@link Puffin}. */ // 类级别文档注释：该类用于测试 Puffin 工具类的功能，Puffin 是 Calcite 框架中用于文本流处理的核心工具
public class PuffinTest { // PuffinTest 类定义：测试 Puffin 工具类的各种功能，包括行过滤、状态管理、多源处理等
  private static final Fixture<Unit> EMPTY_FIXTURE = // 定义空的测试夹具常量，用于创建默认的测试环境
      new Fixture<>(Sources.of(""), Puffin.builder().build()); // 创建一个包含空数据源和空程序的 Fixture 实例

  @Test void testPuffin() { // 测试方法：测试 Puffin 的基本功能，包括行过滤和全局状态管理
    Puffin.Program<AtomicInteger> program = // 创建一个 Puffin 程序，使用 AtomicInteger 作为全局状态类型
        Puffin.builder(AtomicInteger::new, counter -> Unit.INSTANCE) // 构建 Puffin 程序，初始化全局状态为 AtomicInteger，返回类型为 Unit
            .add(line -> !line.startsWith("#") // 添加行过滤器：过滤掉以 # 开头的注释行
                    && !line.matches(".*/\\*.*\\*/.*"), // 同时过滤掉包含 /* ... */ 注释格式的行
                line -> line.globalState().incrementAndGet()) // 对符合条件的行，递增全局计数器
            .after(context -> // 在程序执行完成后执行的操作
                context.println("counter: " + context.globalState().get())) // 输出计数器的最终值到上下文
            .build(); // 构建完整的 Puffin 程序
    fixture().withDefaultInput() // 使用默认输入数据（包含注释和非注释行）
        .withProgram(program) // 设置要测试的程序
        .generatesOutput(isLinux("counter: 2\n")); // 验证输出结果，期望计数器值为 2（因为有两行非注释行）
  }

  /** Tests Puffin with several sources, registers actions by calling // 方法文档注释：测试 Puffin 处理多个数据源的功能
   * {@link Puffin.Builder#beforeSource(Consumer)}, // 通过调用 beforeSource 方法注册数据源处理前的回调
   * {@link Puffin.Builder#afterSource(Consumer)}, // 通过调用 afterSource 方法注册数据源处理后的回调
   * {@link Puffin.Builder#before(Consumer)}, and // 通过调用 before 方法注册程序执行前的回调
   * {@link Puffin.Builder#after(Consumer)}, and counts how many times each is // 通过调用 after 方法注册程序执行后的回调，统计每个回调被调用的次数
   * called. */
  @Test void testSeveralSources() { // 测试方法：测试 Puffin 处理多个数据源时的生命周期回调
    Puffin.Program<GlobalState> program = // 创建一个 Puffin 程序，使用 GlobalState 作为全局状态类型
        Puffin.builder(GlobalState::new, u -> new AtomicInteger()) // 构建程序，初始化全局状态为 GlobalState，每个数据源的状态为 AtomicInteger
            .add(line -> true, // 添加行处理器：处理所有行（条件始终为 true）
                line -> line.state().incrementAndGet()) // 对每一行，递增当前数据源的计数器
            .add(Puffin.Line::isLast, // 添加行处理器：只处理每个数据源的最后一行
                line -> // 处理最后一行，将最后一行的信息添加到全局消息列表中
                    line.globalState().messages.add("last line of " // 添加消息：记录数据源的最后一行内容
                        + line.filename() + " is [" + line.line() + "]")) // 包含文件名和最后一行的内容
            .beforeSource(context -> { // 注册数据源处理前的回调
              final GlobalState g = context.globalState(); // 获取全局状态对象
              g.beforeSourceCount.incrementAndGet(); // 递增数据源处理前的计数器
            })
            .afterSource(context -> { // 注册数据源处理后的回调
              final GlobalState g = context.globalState(); // 获取全局状态对象
              final AtomicInteger f = context.state(); // 获取当前数据源的状态（行计数器）
              g.messages.add(f.intValue() + " lines"); // 添加消息：记录当前数据源的行数
              g.afterSourceCount.incrementAndGet(); // 递增数据源处理后的计数器
            })
            .before(context -> { // 注册程序执行前的回调
              final GlobalState g = context.globalState(); // 获取全局状态对象
              g.beforeCount.incrementAndGet(); // 递增程序执行前的计数器
            })
            .after(context -> { // 注册程序执行后的回调
              final GlobalState g = context.globalState(); // 获取全局状态对象
              g.afterCount.incrementAndGet(); // 递增程序执行后的计数器
              g.messages.add(g.afterSourceCount + " after sources"); // 添加消息：记录数据源处理后的调用次数
              g.messages.add(g.beforeSourceCount + " before sources"); // 添加消息：记录数据源处理前的调用次数
              g.messages.add(g.beforeCount + " before"); // 添加消息：记录程序执行前的调用次数
              g.messages.add(g.afterCount + " after"); // 添加消息：记录程序执行后的调用次数
            })
            .build(); // 构建完整的 Puffin 程序
    final StringWriter sw = new StringWriter(); // 创建 StringWriter 用于捕获输出
    GlobalState g = // 执行程序，传入三个数据源（分别包含 2 行、1 行、4 行数据）
        program.execute(
            Stream.of(Sources.of("a\nb\n"), // 第一个数据源：包含两行 "a" 和 "b"
                Sources.of("a\n"), // 第二个数据源：包含一行 "a"
                Sources.of("a\nb\nc\n\n")), // 第三个数据源：包含四行 "a"、"b"、"c" 和空行
            new PrintWriter(sw)); // 使用 PrintWriter 将输出写入 StringWriter
    assertThat(g.messages, hasSize(10)); // 验证消息列表包含 10 条消息
    assertThat(g.messages, hasItem("4 lines")); // 验证消息列表包含 "4 lines"（第三个数据源的行数，包括空行）
    assertThat(g.messages, hasItem("2 lines")); // 验证消息列表包含 "2 lines"（第一个数据源的行数）
    assertThat(g.messages, hasItem("1 lines")); // 验证消息列表包含 "1 lines"（第二个数据源的行数）
    assertThat(g.messages, hasItem("3 after sources")); // 验证消息列表包含 "3 after sources"（处理了 3 个数据源）
    assertThat(g.messages, hasItem("3 before sources")); // 验证消息列表包含 "3 before sources"（处理了 3 个数据源）
    assertThat(g.messages, hasItem("1 before")); // 验证消息列表包含 "1 before"（程序执行前调用 1 次）
    assertThat(g.messages, hasItem("1 after")); // 验证消息列表包含 "1 after"（程序执行后调用 1 次）
    assertThat(g.messages, hasItem("last line of GuavaCharSource{memory} is [b]")); // 验证消息列表包含第一个数据源的最后一行
    assertThat(g.messages, hasItem("last line of GuavaCharSource{memory} is [a]")); // 验证消息列表包含第二个数据源的最后一行
    assertThat(g.messages, hasItem("last line of GuavaCharSource{memory} is []")); // 验证消息列表包含第三个数据源的最后一行（空行）
    assertThat(sw, hasToString("")); // 验证输出为空字符串（因为程序没有直接输出内容）
  }

  @Test void testEmptyProgram() { // 测试方法：测试空程序的行为
    final Puffin.Program<Unit> program = Puffin.builder().build(); // 创建一个空的 Puffin 程序（没有任何处理器）
    fixture().withDefaultInput() // 使用默认输入数据
        .withProgram(program) // 设置空程序
        .generatesOutput(is("")); // 验证输出为空字符串（空程序不产生任何输出）
  }

  static Fixture<Unit> fixture() { // 静态工厂方法：返回空的测试夹具实例
    return EMPTY_FIXTURE; // 返回预先定义的空夹具常量
  }

  /** Fixture that contains all the state necessary to test // 内部类文档注释：Fixture 类包含测试 Puffin 所需的所有状态
   * {@link Puffin}. // 用于封装测试 Puffin 所需的数据源和程序
   *
   * @param <G> Type of state that is created when we start processing */ // 泛型参数 G：表示开始处理时创建的状态类型
  private static class Fixture<G> { // Fixture 内部类定义：测试夹具，封装数据源和程序
    private final Source source; // 成员变量：数据源，表示要处理的输入数据
    private final Puffin.Program<G> program; // 成员变量：Puffin 程序，表示要执行的文本处理程序

    Fixture(Source source, Puffin.Program<G> program) { // 构造方法：创建 Fixture 实例，初始化数据源和程序
      this.source = source; // 初始化数据源
      this.program = program; // 初始化程序
    }

    public Fixture<G> withDefaultInput() { // 方法：使用默认输入数据创建新的 Fixture 实例
      final String inputText = "first line\n" // 定义默认输入文本，包含多行数据
          + "# second line\n" // 第二行是注释行（以 # 开头）
          + "third line /* with a comment */\n" // 第三行包含 /* ... */ 注释
          + "fourth line"; // 第四行是普通文本行
      return withSource(Sources.of(inputText)); // 使用 Sources 工具类创建数据源，并调用 withSource 方法
    }

    private Fixture<G> withSource(Source source) { // 方法：使用指定的数据源创建新的 Fixture 实例
      return new Fixture<>(source, program); // 创建新的 Fixture 实例，保留原程序，替换数据源
    }

    public <G2> Fixture<G2> withProgram(Puffin.Program<G2> program) { // 方法：使用指定的程序创建新的 Fixture 实例（泛型方法）
      return new Fixture<>(source, program); // 创建新的 Fixture 实例，保留原数据源，替换程序
    }

    public Fixture<G> generatesOutput(Matcher<String> matcher) { // 方法：执行程序并验证输出结果
      StringWriter sw = new StringWriter(); // 创建 StringWriter 用于捕获输出
      try (PrintWriter pw = new PrintWriter(sw)) { // 使用 try-with-resources 创建 PrintWriter，确保资源自动关闭
        G g = program.execute(Stream.of(source), pw); // 执行程序，传入数据源流和 PrintWriter
        assertThat(g, notNullValue()); // 验证返回的全局状态不为 null
      } // PrintWriter 自动关闭
      assertThat(sw, hasToString(matcher)); // 验证 StringWriter 的内容匹配指定的匹配器
      return this; // 返回当前 Fixture 实例，支持链式调用
    }
  }

  /** Global state. */ // 内部类文档注释：GlobalState 类表示全局状态，用于跟踪测试过程中的各种计数器和消息
  private static class GlobalState { // GlobalState 内部类定义：用于存储测试过程中的全局状态信息
    final List<String> messages = new ArrayList<>(); // 成员变量：消息列表，用于存储程序执行过程中产生的各种消息
    final AtomicInteger beforeSourceCount = new AtomicInteger(); // 成员变量：数据源处理前的调用计数器
    final AtomicInteger afterSourceCount = new AtomicInteger(); // 成员变量：数据源处理后的调用计数器
    final AtomicInteger beforeCount = new AtomicInteger(); // 成员变量：程序执行前的调用计数器
    final AtomicInteger afterCount = new AtomicInteger(); // 成员变量：程序执行后的调用计数器
  }
}
