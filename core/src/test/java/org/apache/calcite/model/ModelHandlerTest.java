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
// Apache 许可证头部声明，说明该文件的版权和使用许可
package org.apache.calcite.model; // 声明该类所在的包名为 org.apache.calcite.model，这是 Calcite 框架中处理模型定义的包

import org.apache.calcite.jdbc.CalciteSchema; // 导入 CalciteSchema 类，用于表示 Calcite 的 Schema 结构
import org.apache.calcite.schema.SchemaPlus; // 导入 SchemaPlus 类，提供了对 Schema 的增强操作接口
import org.apache.calcite.schema.lookup.LikePattern; // 导入 LikePattern 类，用于模式匹配查询，类似 SQL 中的 LIKE 操作
import org.apache.calcite.util.Sources; // 导入 Sources 工具类，用于处理数据源和资源文件路径

import com.google.common.collect.ImmutableSet; // 导入 Google Guava 库的 ImmutableSet 类，用于创建不可变的集合

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法

import java.io.IOException; // 导入 IOException 异常类，用于处理输入输出异常
import java.util.Set; // 导入 Set 接口，用于存储不重复的元素集合

import static org.hamcrest.CoreMatchers.is; // 导入 Hamcrest 断言库的 is 匹配器，用于断言值相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入 Hamcrest 断言工具类，用于执行断言操作

import static java.util.Objects.requireNonNull; // 导入 Objects 工具类的 requireNonNull 方法，用于检查对象非空

/**
 * Unit test for {@link ModelHandler}.
 * ModelHandler 的单元测试类
 * 该测试类用于验证 ModelHandler 的功能，特别是测试从模型文件（JSON/YAML）解析和创建 Schema 的能力
 * ModelHandler 是 Calcite 中负责处理模型定义的核心类，它能够从配置文件中读取 Schema 定义并构建相应的 Schema 对象
 */
public class ModelHandlerTest { // 定义一个名为 ModelHandlerTest 的公共测试类

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-7022">[CALCITE-7022]
   * Decouple ModelHandler from CalciteConnection</a>.
   * The test ensures/demonstrates that a Schema can be easily parsed/created from a model
   * file (JSON/YAML) without necessitating the creation of complex/heavy objects
   * (e.g., CalciteConnection). */
  // 测试方法注释：该测试用例针对 CALCITE-7022 问题，验证 ModelHandler 与 CalciteConnection 的解耦
  // 测试确保/演示了可以从模型文件（JSON/YAML）轻松解析/创建 Schema，而无需创建复杂/繁重的对象（如 CalciteConnection）
  @Test void testPopulateRootSchemaFromURL() throws IOException { // 定义一个测试方法，方法名为 testPopulateRootSchemaFromURL，表示测试从 URL 填充根 Schema 的功能，可能抛出 IOException 异常
    SchemaPlus root = CalciteSchema.createRootSchema(false, false).plus(); // 创建一个根 Schema 对象，createRootSchema 方法创建基础 Schema，plus() 方法返回增强的 SchemaPlus 接口，参数 false 表示不缓存，不启用类型系统
    String mURI = // 声明一个字符串变量 mURI，用于存储模型文件的 URI 路径
        Sources.of(requireNonNull(ModelHandlerTest.class.getResource("/hsqldb-scott.json"))) // 获取测试资源文件 hsqldb-scott.json 的 URL，requireNonNull 确保资源存在不为空，Sources.of 将 URL 转换为 Source 对象
            .path(); // 调用 path() 方法获取 Source 对象的文件路径字符串，赋值给 mURI 变量
    ModelHandler h = new ModelHandler(root, mURI); // 创建 ModelHandler 实例，传入根 Schema 和模型文件 URI，ModelHandler 会解析模型文件并将 Schema 定义填充到根 Schema 中
    SchemaPlus scott = root.subSchemas().get("SCOTT"); // 从根 Schema 的子 Schema 集合中获取名为 "SCOTT" 的 Schema，这是从模型文件中解析出的 Schema 名称
    Set<String> tables = scott.tables().getNames(new LikePattern("%")); // 获取 SCOTT Schema 中所有表的名称集合，LikePattern("%") 表示匹配所有表名（类似 SQL 中的 SELECT *）
    assertThat(tables, is(ImmutableSet.of("EMP", "DEPT", "BONUS", "SALGRADE"))); // 断言获取的表名集合等于预期的四个表名，验证模型文件解析正确，包含了 EMP、DEPT、BONUS、SALGRADE 四个表
    assertThat(h.defaultSchemaName(), is("SCOTT")); // 断言 ModelHandler 的默认 Schema 名称为 "SCOTT"，验证默认 Schema 设置正确
  } // 测试方法结束

} // 类定义结束
