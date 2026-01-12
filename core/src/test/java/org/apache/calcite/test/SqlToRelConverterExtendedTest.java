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
// 本类是SqlToRelConverterTest的扩展测试类，用于测试SQL到关系表达式转换过程中的JSON序列化和反序列化功能
// 主要功能：在每次SQL转换为关系表达式后，将关系表达式树序列化为JSON格式，然后再将JSON反序列化回关系表达式树
// 这个测试类通过Hook机制在转换过程中插入自定义逻辑，验证关系表达式的JSON表示是否正确
// 同时也测试了Calcite的RelJsonWriter和RelJsonReader的功能，确保关系表达式可以正确地在JSON格式和对象之间转换
// 使用@ResourceLock注解确保测试线程安全，防止并发测试时出现资源竞争
package org.apache.calcite.test;

import org.apache.calcite.plan.RelOptSchema; // 导入RelOptSchema类，用于表示关系优化模式，提供表和关系表达式的元数据信息
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系表达式树中的节点，是所有关系操作符的基类
import org.apache.calcite.rel.RelShuttleImpl; // 导入RelShuttleImpl类，用于遍历关系表达式树的访问器实现
import org.apache.calcite.rel.core.TableScan; // 导入TableScan类，表示表扫描操作符，用于从表中读取数据
import org.apache.calcite.rel.externalize.RelJsonReader; // 导入RelJsonReader类，用于将JSON格式的关系表达式反序列化为RelNode对象
import org.apache.calcite.rel.externalize.RelJsonWriter; // 导入RelJsonWriter类，用于将RelNode对象序列化为JSON格式
import org.apache.calcite.runtime.Hook; // 导入Hook类，用于在Calcite执行过程中插入自定义回调逻辑
import org.apache.calcite.tools.Frameworks; // 导入Frameworks类，提供创建和配置Calcite框架的工具方法
import org.apache.calcite.util.TestUtil; // 导入TestUtil类，提供测试工具方法，如异常重新抛出等

import org.junit.jupiter.api.AfterEach; // 导入AfterEach注解，用于在每个测试方法执行后执行清理操作
import org.junit.jupiter.api.BeforeEach; // 导入BeforeEach注解，用于在每个测试方法执行前执行初始化操作
import org.junit.jupiter.api.parallel.ResourceLock; // 导入ResourceLock注解，用于控制测试资源的并发访问

import java.io.IOException; // 导入IOException类，处理输入输出异常

/**
 * Runs {@link org.apache.calcite.test.SqlToRelConverterTest} with extensions.
 * 运行带有扩展功能的SqlToRelConverterTest测试类
 * 本类继承自SqlToRelConverterTest，在父类测试的基础上增加了JSON序列化和反序列化的验证
 * 通过Hook机制，在SQL转换为关系表达式后，自动将关系表达式转换为JSON，然后再转换回来
 * 这样可以验证关系表达式的JSON表示是否完整和正确
 */
@ResourceLock(value = "SqlToRelConverterTest.xml") // 使用资源锁注解，指定锁名称为"SqlToRelConverterTest.xml"，确保测试在并发环境下安全执行
class SqlToRelConverterExtendedTest extends SqlToRelConverterTest { // 定义测试类，继承自SqlToRelConverterTest，继承所有父类的测试方法
  Hook.Closeable closeable; // 成员变量：Hook.Closeable类型的closeable对象，用于存储Hook回调的句柄，可以在测试结束后关闭Hook以释放资源

  @BeforeEach public void before() { // 使用BeforeEach注解，在每个测试方法执行前调用此方法进行初始化
    this.closeable = // 将Hook.CONVERTED（SQL转换完成后的Hook点）添加一个线程级别的回调函数
        Hook.CONVERTED.addThread(SqlToRelConverterExtendedTest::foo); // 添加foo方法作为回调函数，当SQL转换为关系表达式后会调用foo方法，closeable对象用于存储这个Hook的句柄以便后续关闭
  }

  @AfterEach public void after() { // 使用AfterEach注解，在每个测试方法执行后调用此方法进行清理
    if (this.closeable != null) { // 检查closeable对象是否不为null，确保有Hook需要关闭
      this.closeable.close(); // 调用close方法关闭Hook，移除之前添加的回调函数，释放资源
      this.closeable = null; // 将closeable对象设置为null，避免重复关闭和内存泄漏
    }
  }

  public static void foo(RelNode rel) { // 公共静态方法foo，作为Hook的回调函数，接收转换后的关系表达式树作为参数
    // Convert rel tree to JSON.
    // 将关系表达式树转换为JSON格式，用于验证序列化功能
    final RelJsonWriter writer = new RelJsonWriter(); // 创建RelJsonWriter对象，用于将关系表达式序列化为JSON格式
    rel.explain(writer); // 调用rel的explain方法，将关系表达式树转换为JSON表示并写入writer
    final String json = writer.asString(); // 调用writer的asString方法，获取JSON格式的字符串表示

    // Find the schema. If there are no tables in the plan, we won't need one.
    // 查找关系模式，如果计划中没有表，则不需要模式信息
    final RelOptSchema[] schemas = {null}; // 创建一个长度为1的RelOptSchema数组，初始值为null，用于存储找到的关系模式
    rel.accept(new RelShuttleImpl() { // 创建RelShuttleImpl匿名内部类实例，用于遍历关系表达式树
      @Override public RelNode visit(TableScan scan) { // 重写visit方法，当访问到TableScan节点时调用
        schemas[0] = scan.getTable().getRelOptSchema(); // 从TableScan节点获取表对象，再从表对象获取RelOptSchema（关系优化模式），并存储到schemas数组的第一个元素中
        return super.visit(scan); // 调用父类的visit方法继续遍历子节点
      }
    }); // 调用rel的accept方法，使用RelShuttleImpl访问器遍历整个关系表达式树，找到第一个TableScan节点并获取其模式

    // Convert JSON back to rel tree.
    // 将JSON格式转换回关系表达式树，用于验证反序列化功能
    Frameworks.withPlanner((cluster, relOptSchema, rootSchema) -> { // 使用Frameworks的withPlanner方法创建一个临时的Calcite规划器环境，并提供lambda表达式处理规划器相关操作
      final RelJsonReader reader = // 创建RelJsonReader对象，用于将JSON字符串反序列化为关系表达式树
          new RelJsonReader(cluster, schemas[0], rootSchema); // 构造RelJsonReader，传入cluster（集群对象，包含类型工厂等）、schemas[0]（之前找到的关系模式）、rootSchema（根模式）
      try { // 使用try-catch块处理可能的IO异常
        RelNode x = reader.read(json); // 调用reader的read方法，将JSON字符串反序列化为RelNode对象，得到新的关系表达式树x
      } catch (IOException e) { // 捕获IO异常，可能在读取JSON时发生
        throw TestUtil.rethrow(e); // 使用TestUtil的rethrow方法重新抛出异常，保留原始异常堆栈
      }
      return null; // 返回null，因为lambda表达式要求返回值，但这里只需要执行反序列化操作，不需要返回结果
    }); // withPlanner方法会自动管理规划器的创建和销毁
  }
}
