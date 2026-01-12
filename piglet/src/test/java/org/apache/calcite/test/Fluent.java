/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明，授权给ASF，允许一个或多个贡献者协议
 * contributor license agreements.  See the NOTICE file distributed with // 查看NOTICE文件获取版权所有权信息
 * this work for additional information regarding copyright ownership. // ASF根据Apache许可证2.0版本授权给您
 * The ASF licenses this file to you under the Apache License, Version 2.0 // 除非遵守许可证，否则您不能使用此文件
 * (the "License"); you may not use this file except in compliance with // 您可以在以下网址获取许可证副本
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // 除非适用法律要求或书面同意，否则根据许可证分发的软件
 *
 * Unless required by applicable law or agreed to in writing, software // 按"原样"分发，不提供任何明示或暗示的担保或条件
 * distributed under the License is distributed on an "AS IS" BASIS, // 详见许可证中关于权限和限制的特定语言
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.test; // 声明该类属于Apache Calcite项目的test包，该包包含Calcite框架的测试类

import org.apache.calcite.piglet.Ast; // 导入Apache Calcite Piglet模块的抽象语法树（AST）相关类，用于表示Piglet脚本的语法结构
import org.apache.calcite.piglet.Handler; // 导入Apache Calcite Piglet模块的处理器类，用于将AST转换为关系代数操作
import org.apache.calcite.piglet.parser.ParseException; // 导入Apache Calcite Piglet解析器的异常类，表示解析Piglet脚本时发生的错误
import org.apache.calcite.piglet.parser.PigletParser; // 导入Apache Calcite Piglet解析器类，用于将Piglet脚本字符串解析为AST
import org.apache.calcite.plan.RelOptUtil; // 导入Apache Calcite的关系优化工具类，提供关系代数树的字符串表示、比较等实用方法
import org.apache.calcite.tools.PigRelBuilder; // 导入Apache Calcite的Pig关系代数构建器，用于构建和操作关系代数表达式树
import org.apache.calcite.util.Util; // 导入Apache Calcite的工具类，提供各种通用工具方法，如字符串格式化、集合操作等

import com.google.common.collect.Ordering; // 导入Google Guava库的排序工具类，提供灵活的排序和比较功能

import java.io.StringReader; // 导入Java IO库的字符串读取器，用于从字符串中读取字符流
import java.io.StringWriter; // 导入Java IO库的字符串写入器，用于将字符流写入字符串缓冲区
import java.util.ArrayList; // 导入Java集合框架的动态数组列表类，提供可变大小的数组实现
import java.util.Arrays; // 导入Java数组的工具类，提供数组的排序、搜索、转换等操作
import java.util.List; // 导入Java集合框架的列表接口，表示有序的元素集合
import java.util.function.Function; // 导入Java函数式编程的函数接口，表示接收一个参数并产生结果的函数

import static org.hamcrest.CoreMatchers.is; // 静态导入Hamcrest匹配器的is方法，用于创建相等性匹配器，简化断言语法
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入Hamcrest的assertThat断言方法，用于验证实际值是否满足匹配器条件

/** Fluent API to perform Piglet test actions. */ // 流式API（Fluent API）用于执行Piglet测试操作，提供链式调用方式来简化测试代码的编写，支持解析、执行和验证Piglet脚本
class Fluent {
  private final String pig; // 存储待测试的Piglet脚本字符串，该字符串将被解析为AST（抽象语法树）并用于后续的测试验证

  Fluent(String pig) { // 构造方法，接收Piglet脚本字符串并初始化Fluent对象，为后续的链式测试操作做准备
    this.pig = pig; // 将传入的Piglet脚本字符串保存到成员变量pig中，供后续方法使用
  }

  private Ast.Program parseProgram(String s) throws ParseException { // 私有辅助方法，用于将Piglet脚本字符串解析为抽象语法树（AST），参数s为待解析的Piglet脚本字符串，返回解析后的AST.Program对象，抛出ParseException表示解析失败
    return new PigletParser(new StringReader(s)).stmtListEof(); // 创建PigletParser对象并传入StringReader，调用stmtListEof()方法解析完整的语句列表，直到文件结束符EOF
  }

  public Fluent explainContains(String expected) throws ParseException { // 公共方法，用于验证Piglet脚本执行后的关系代数计划（Relational Algebra Plan）是否包含预期的字符串，参数expected为预期的计划字符串，返回Fluent对象以支持链式调用，抛出ParseException表示解析失败
    final Ast.Program program = parseProgram(pig); // 调用parseProgram方法将pig成员变量中的Piglet脚本解析为抽象语法树（AST）
    final PigRelBuilder builder = // 创建PigRelBuilder对象，用于构建关系代数表达式树
        PigRelBuilder.create(PigRelBuilderTest.config().build()); // 使用PigRelBuilderTest的配置创建builder，config()提供测试配置，build()构建配置对象
    new Handler(builder).handle(program); // 创建Handler对象并传入builder，调用handle方法处理AST程序，将Piglet操作转换为关系代数操作
    assertThat(Util.toLinux(RelOptUtil.toString(builder.peek())), is(expected)); // 验证关系代数树的字符串表示是否与预期字符串匹配，builder.peek()获取顶层关系节点，RelOptUtil.toString转换为字符串，Util.toLinux统一换行符为Linux格式
    return this; // 返回当前Fluent对象，支持链式调用
  }

  public Fluent returns(final String out) // 公共方法，用于验证Piglet脚本执行后的输出结果是否与预期字符串完全匹配，参数out为预期的输出字符串，返回Fluent对象以支持链式调用，抛出ParseException表示解析失败
      throws ParseException {
    return returns(s -> { // 调用重载的returns方法，传入一个lambda函数作为checker参数，该函数接收实际输出字符串s
      assertThat(s, is(out)); // 使用Hamcrest断言验证实际输出s是否与预期输出out完全相等
      return null; // 返回null，因为checker函数的返回类型为Void，这里返回null表示验证通过
    });
  }

  public Fluent returnsUnordered(String... lines) throws ParseException { // 公共方法，用于验证Piglet脚本执行后的输出结果是否包含预期的行，不考虑行的顺序（无序比较），参数lines为可变参数，表示预期的输出行数组，返回Fluent对象以支持链式调用，抛出ParseException表示解析失败
    final List<String> expectedLines = // 创建预期行列表，按自然顺序排序
        Ordering.natural().immutableSortedCopy(Arrays.asList(lines)); // 使用Guava的Ordering工具将lines数组转换为List，然后进行不可变的排序副本，natural()使用自然排序（字典序）
    return returns(s -> { // 调用重载的returns方法，传入一个lambda函数作为checker参数，该函数接收实际输出字符串s
      final List<String> actualLines = new ArrayList<>(); // 创建ArrayList用于存储实际输出的行
      for (;;) { // 无限循环，逐行解析实际输出字符串s
        int i = s.indexOf('\n'); // 查找换行符的位置
        if (i < 0) { // 如果找不到换行符，说明已经到达字符串末尾
          if (!s.isEmpty()) { // 如果s不为空，说明还有最后一行没有换行符
            actualLines.add(s); // 将最后一行添加到actualLines列表中
          }
          break; // 退出循环
        } else { // 如果找到换行符
          actualLines.add(s.substring(0, i)); // 截取从开头到换行符之前的子字符串（一行）并添加到actualLines
          s = s.substring(i + 1); // 将s更新为换行符之后的部分，继续处理下一行
        }
      }
      assertThat(Ordering.natural().sortedCopy(actualLines), // 对实际行列表进行排序
          is(expectedLines)); // 验证排序后的实际行列表是否与预期的排序行列表完全相等
      return null; // 返回null，因为checker函数的返回类型为Void，这里返回null表示验证通过
    });
  }

  public Fluent returns(Function<String, Void> checker) throws ParseException { // 公共方法，用于执行Piglet脚本并使用自定义的checker函数验证输出结果，参数checker为函数式接口，接收实际输出字符串并返回Void（用于验证逻辑），返回Fluent对象以支持链式调用，抛出ParseException表示解析失败
    final Ast.Program program = parseProgram(pig); // 调用parseProgram方法将pig成员变量中的Piglet脚本解析为抽象语法树（AST）
    final PigRelBuilder builder = // 创建PigRelBuilder对象，用于构建关系代数表达式树
        PigRelBuilder.create(PigRelBuilderTest.config().build()); // 使用PigRelBuilderTest的配置创建builder，config()提供测试配置，build()构建配置对象
    final StringWriter sw = new StringWriter(); // 创建StringWriter对象，用于捕获执行过程中的输出结果
    new CalciteHandler(builder, sw).handle(program); // 创建CalciteHandler对象并传入builder和StringWriter，调用handle方法处理AST程序，将Piglet操作转换为关系代数操作并将输出写入StringWriter
    checker.apply(Util.toLinux(sw.toString())); // 调用checker函数验证输出结果，sw.toString()获取StringWriter中的输出字符串，Util.toLinux统一换行符为Linux格式
    return this; // 返回当前Fluent对象，支持链式调用
  }

  public Fluent parseContains(String expected) throws ParseException { // 公共方法，用于验证Piglet脚本解析后的抽象语法树（AST）字符串表示是否与预期字符串匹配，参数expected为预期的AST字符串表示，返回Fluent对象以支持链式调用，抛出ParseException表示解析失败
    final Ast.Program program = parseProgram(pig); // 调用parseProgram方法将pig成员变量中的Piglet脚本解析为抽象语法树（AST）
    assertThat(Util.toLinux(Ast.toString(program)), is(expected)); // 验证AST的字符串表示是否与预期字符串匹配，Ast.toString将AST对象转换为字符串，Util.toLinux统一换行符为Linux格式
    return this; // 返回当前Fluent对象，支持链式调用
  }
}
