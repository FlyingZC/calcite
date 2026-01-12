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
package org.apache.calcite.test; // 包声明，该类属于org.apache.calcite.test测试包

import org.apache.calcite.rel.RelNode; // 导入Calcite的关系表达式节点类，代表关系代数操作

import org.junit.jupiter.api.AfterAll; // 导入JUnit5的AfterAll注解，用于在所有测试方法执行后执行一次清理操作
import org.junit.jupiter.api.BeforeAll; // 导入JUnit5的BeforeAll注解，用于在所有测试方法执行前执行一次初始化操作
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.io.IOException; // 导入Java IO异常类，用于处理文件读写异常
import java.nio.charset.StandardCharsets; // 导入标准字符集类，用于指定文件编码为UTF-8
import java.nio.file.Files; // 导入文件操作工具类，用于文件的读写删除等操作
import java.nio.file.Paths; // 导入路径工具类，用于创建文件路径对象
import java.util.Arrays; // 导入数组工具类，用于将数组转换为List集合
import java.util.HashMap; // 导入HashMap类，用于存储键值对参数
import java.util.List; // 导入List接口，用于存储字符串列表
import java.util.Map; // 导入Map接口，用于存储参数映射关系

import static org.apache.calcite.test.Matchers.hasTree; // 导入静态方法hasTree，用于验证关系树的匹配

import static org.hamcrest.MatcherAssert.assertThat; // 导入静态方法assertThat，用于断言验证

/**
 * Test for converting a Pig script file. // Pig脚本文件转换测试类，用于测试将Pig脚本转换为Calcite关系代数表达式
 * 这个类的主要功能是：
 * 1. 测试Pig脚本到RelNode（关系表达式节点）的转换功能
 * 2. 验证转换后的关系代数树是否符合预期结构
 * 3. 测试Pig脚本解析器、语义分析和优化器的集成
 * 4. 验证Pig脚本中的数据加载、聚合、排序等操作能否正确转换为Calcite的算子
 * 
 * Pig是Apache的一个大数据分析平台，使用类似SQL的脚本语言进行数据处理
 * Calcite是一个动态数据管理框架，提供SQL解析、优化、查询执行等功能
 * 这个测试类验证了Calcite能够理解和执行Pig脚本，体现了Calcite的多语言支持能力
 */
class PigScriptTest extends PigRelTestBase { // PigScriptTest类继承自PigRelTestBase基类，获得了Pig关系表达式测试的基础功能
  private static String projectRootDir; // 静态成员变量：存储项目根目录的绝对路径，用于定位测试资源和数据文件
  private static String dataFile; // 静态成员变量：存储测试数据文件的完整路径，该文件包含用于测试的输入数据

  @BeforeAll // JUnit5注解，标记该方法在所有测试方法执行前只执行一次，用于初始化测试环境
  public static void setUpOnce() throws IOException { // 静态方法：一次性设置测试环境，创建测试数据文件
    projectRootDir = System.getProperty("user.dir"); // 获取当前工作目录（项目根目录）的绝对路径
    dataFile = projectRootDir + "/src/test/resources/input.data"; // 构建测试数据文件的完整路径，数据文件位于测试资源目录下
    List<String> lines = // 创建字符串列表，包含测试数据文件的每一行内容
        Arrays.asList("yahoo 10", "twitter 3", "facebook 10", // 数据行1：yahoo查询10次
            "yahoo 15", "facebook 5", "twitter 2"); // 数据行2：yahoo查询15次，facebook查询5次，twitter查询2次
    Files.write(Paths.get(dataFile), lines, StandardCharsets.UTF_8); // 将数据行列表写入测试数据文件，使用UTF-8编码，文件格式为每行包含查询名称和查询次数
  }

  @AfterAll // JUnit5注解，标记该方法在所有测试方法执行后只执行一次，用于清理测试环境
  public static void testClean() throws IOException { // 静态方法：清理测试环境，删除临时创建的测试数据文件
    Files.delete(Paths.get(dataFile)); // 删除测试数据文件，释放磁盘空间，避免影响后续测试
  }

  @Test // JUnit5注解，标记这是一个测试方法
  void testReadScript() throws IOException { // 测试方法：测试读取和转换Pig脚本文件的功能
    Map<String, String> params = new HashMap<>(); // 创建参数映射对象，用于存储传递给Pig脚本的参数
    params.put("input", dataFile); // 将input参数设置为测试数据文件路径，Pig脚本中的$input变量会被替换为这个路径
    params.put("output", "outputFile"); // 将output参数设置为输出文件名，Pig脚本中的$output变量会被替换为这个值

    final String pigFile = projectRootDir + "/src/test/resources/testPig.pig"; // 构建Pig脚本文件的完整路径，脚本文件位于测试资源目录下
    final RelNode rel = converter.pigScript2Rel(pigFile, params, true).get(0); // 调用converter对象（继承自PigRelTestBase）的pigScript2Rel方法，将Pig脚本转换为关系表达式节点列表，true参数表示启用优化，get(0)获取第一个（也是唯一一个）关系表达式节点

    final String dataFile = projectRootDir + "/src/test/resources/input.data"; // 重新构建数据文件路径，用于在预期计划中引用
    String expectedPlan = "" // 定义预期的关系代数树结构字符串，用于验证实际转换结果
        + "LogicalSort(sort0=[$1], dir0=[DESC], fetch=[5])\n" // 逻辑排序节点：按第1列（count字段）降序排序，只取前5条记录
        + "  LogicalProject(query=[$0], count=[CAST($1):BIGINT])\n" // 逻辑投影节点：输出query字段（第0列）和count字段（第1列，并转换为BIGINT类型）
        + "    LogicalAggregate(group=[{0}], agg#0=[SUM($1)])\n" // 逻辑聚合节点：按第0列（query字段）分组，对第1列（count字段）求和
        + "      LogicalTableScan(table=[[" + dataFile + "]])\n"; // 逻辑表扫描节点：扫描测试数据文件，读取输入数据

    assertThat(rel, hasTree(expectedPlan)); // 使用Hamcrest断言验证实际的关系表达式树是否与预期的树结构匹配，hasTree是自定义的匹配器，用于比较关系树的字符串表示
  }
}
