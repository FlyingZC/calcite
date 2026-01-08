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
// Apache许可证声明，说明代码归属和使用权限
package org.apache.calcite.test; // 定义包名，该测试类位于org.apache.calcite.test包下

import com.datastax.oss.driver.api.core.CqlSession; // 导入Cassandra驱动核心会话类，用于与Cassandra数据库建立连接和执行CQL查询
import com.google.common.collect.ImmutableMap; // 导入Google Guava的不可变Map类，用于存储配置信息，确保线程安全和不可变性

import org.cassandraunit.CQLDataLoader; // 导入Cassandra单元测试工具的数据加载器，用于加载CQL脚本到测试数据库
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet; // 导入CQL数据集类，用于从类路径加载CQL脚本文件
import org.junit.jupiter.api.BeforeAll; // 导入JUnit5注解，标记在所有测试方法执行前运行一次的静态初始化方法
import org.junit.jupiter.api.Test; // 导入JUnit5注解，标记测试方法，JUnit会自动执行带有此注解的方法
import org.junit.jupiter.api.extension.ExtendWith; // 导入JUnit5注解，用于注册自定义扩展，扩展测试框架的功能
import org.junit.jupiter.api.parallel.Execution; // 导入JUnit5注解，控制测试方法的并行执行模式
import org.junit.jupiter.api.parallel.ExecutionMode; // 导入JUnit5并行执行模式枚举，定义测试的并行执行策略

/**
 * Tests for the {@code org.apache.calcite.adapter.cassandra} package.
 * // 类文档注释：这是对org.apache.calcite.adapter.cassandra包的测试类
 *
 * <p>Instantiates a CQL session without keyspace, but passes it to
 * {@code org.apache.calcite.adapter.cassandra.CassandraTable}.
 * // 创建一个没有指定keyspace的CQL会话，但将其传递给CassandraTable适配器
 * All generated CQL queries should still succeed and explicitly
 * reference the keyspace.
 * // 所有生成的CQL查询应该仍然成功，并且显式引用keyspace
 */
// JUnit5注解：指定测试在同一个线程中执行，避免并发问题，因为Cassandra连接可能不是线程安全的
@Execution(ExecutionMode.SAME_THREAD)
// JUnit5注解：注册CassandraExtension扩展，该扩展负责启动和管理嵌入式Cassandra测试环境
@ExtendWith(CassandraExtension.class)
// 测试类定义：CassandraAdapterWithoutKeyspaceTest，测试在没有keyspace的情况下Cassandra适配器的功能
class CassandraAdapterWithoutKeyspaceTest {
  // 静态常量：不可变Map，存储Cassandra适配器的配置信息，使用model-without-keyspace.json模型文件
  // 这个配置不包含keyspace信息，用于测试适配器在没有keyspace上下文时的行为
  private static final ImmutableMap<String, String> TWISSANDRA_WITHOUT_KEYSPACE =
          CassandraExtension.getDataset("/model-without-keyspace.json"); // 通过CassandraExtension获取数据集配置，该文件定义了表结构但不指定keyspace

  // JUnit5注解：标记该方法在所有测试方法执行前运行一次，用于初始化测试环境
  @BeforeAll
  // 静态方法：加载测试数据到Cassandra数据库，参数session是Cassandra连接会话，由CassandraExtension提供
  static void load(CqlSession session) { // 接收CqlSession对象，用于执行CQL语句
    new CQLDataLoader(session) // 创建CQL数据加载器，传入Cassandra会话对象
        .load(new ClassPathCQLDataSet("twissandra-small.cql")); // 加载CQL脚本文件，该文件包含创建表和插入测试数据的CQL语句
  } // 方法结束，测试数据已加载到Cassandra数据库中

  // JUnit5注解：标记这是一个测试方法，JUnit会自动执行此方法
  @Test
  // 测试方法：测试基本的SELECT查询功能，验证在没有keyspace的情况下，适配器能否正确生成和执行CQL查询
  void testSelect() { // 无参数测试方法，测试从users表查询所有数据
    CalciteAssert.that() // 创建Calcite断言工具，用于验证SQL查询结果是否符合预期
        .with(TWISSANDRA_WITHOUT_KEYSPACE) // 配置测试环境，传入不包含keyspace的模型配置
        .query("select * from \"users\"") // 执行SQL查询，从users表选择所有列，注意表名用双引号包围以保留大小写
        .returnsCount(10); // 验证查询结果应该返回10行数据，断言结果集的行数
  } // 方法结束，测试完成
} // 类定义结束
