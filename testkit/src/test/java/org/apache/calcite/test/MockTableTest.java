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
package org.apache.calcite.test;

import org.apache.calcite.rel.type.RelDataTypeSystem;
import org.apache.calcite.sql.type.SqlTypeFactoryImpl;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql2rel.NullInitializerExpressionFactory;
import org.apache.calcite.test.catalog.MockCatalogReader;
import org.apache.calcite.util.ImmutableBitSet;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link org.apache.calcite.test.catalog.MockCatalogReader.MockTable}.
 * MockTableTest类用于测试MockCatalogReader.MockTable的功能，MockTable是一个用于测试的模拟表实现
 * 该测试类主要验证MockTable的列添加、键（主键/候选键）添加等核心功能是否正常工作
 */
public class MockTableTest {
  // 类型工厂实例，用于创建SQL数据类型，使用默认的关系数据类型系统，在整个测试过程中共享使用
  private static final SqlTypeFactoryImpl TYPE_FACTORY =
      new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT);

  // 测试方法：验证当添加列时指定列为主键，是否为每列创建单独的键
  // 测试场景：添加两列k1和k2，并将它们都标记为主键，期望生成两个独立的单列键
  @Test void testAddColumnCreatesIndividualKeys() {
    // 创建一个新的MockTable实例用于测试
    MockCatalogReader.MockTable t = newTable();
    // 添加名为"k1"的列，类型为INTEGER，第三个参数true表示该列为主键，自动创建键索引0
    t.addColumn("k1", TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER), true);
    // 添加名为"k2"的列，类型为INTEGER，第三个参数true表示该列为主键，自动创建键索引1
    t.addColumn("k2", TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER), true);
    // 验证表的键集合包含两个独立的键：{0}对应k1列，{1}对应k2列
    assertThat(t.getKeys(), hasToString("[{0}, {1}]"));
  }

  // 测试方法：验证通过列名添加单列键时，是否正确创建简单键
  // 测试场景：添加两列后，通过addKey方法为k1列创建键，期望生成包含k1列索引的键
  @Test void testAddKeyWithOneEntryCreatesSimpleKey() {
    // 创建一个新的MockTable实例用于测试
    MockCatalogReader.MockTable t = newTable();
    // 添加名为"k1"的列，类型为INTEGER，不指定为主键（第三个参数默认为false）
    t.addColumn("k1", TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER));
    // 添加名为"k2"的列，类型为INTEGER，不指定为主键
    t.addColumn("k2", TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER));
    // 通过列名"k1"为该列创建键，系统会查找"k1"列的索引（索引为0）并创建键{0}
    t.addKey("k1");
    // 验证表的键集合包含一个键：{0}对应k1列
    assertThat(t.getKeys(), hasToString("[{0}]"));
  }

  // 测试方法：验证通过多个列名添加复合键时，是否正确创建复合键
  // 测试场景：添加两列后，通过addKey方法为k1和k2两列创建一个复合键，期望生成包含两列索引的复合键
  @Test void testAddKeyWithMultipleEntriesCreatesCompositeKey() {
    // 创建一个新的MockTable实例用于测试
    MockCatalogReader.MockTable t = newTable();
    // 添加名为"k1"的列，类型为INTEGER，不指定为主键
    t.addColumn("k1", TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER));
    // 添加名为"k2"的列，类型为INTEGER，不指定为主键
    t.addColumn("k2", TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER));
    // 通过列名"k1"和"k2"创建复合键，系统会查找两列的索引（0和1）并创建复合键{0, 1}
    t.addKey("k1", "k2");
    // 验证表的键集合包含一个复合键：{0, 1}对应k1和k2两列的组合
    assertThat(t.getKeys(), hasToString("[{0, 1}]"));
  }

  // 测试方法：验证当添加键时使用了不存在的列名，是否抛出IllegalArgumentException异常
  // 测试场景：添加两列k1和k2后，尝试为k1和k3（不存在）创建键，期望抛出异常
  @Test void testAddKeyWithMissingColumnNameThrowsException() {
    // 创建一个新的MockTable实例用于测试
    MockCatalogReader.MockTable t = newTable();
    // 添加名为"k1"的列，类型为INTEGER
    t.addColumn("k1", TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER));
    // 添加名为"k2"的列，类型为INTEGER
    t.addColumn("k2", TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER));
    // 使用assertThrows验证当调用addKey("k1", "k3")时抛出IllegalArgumentException，因为k3列不存在
    assertThrows(IllegalArgumentException.class, () -> t.addKey("k1", "k3"));
  }

  // 测试方法：验证通过列索引（使用ImmutableBitSet）添加键时，是否正确创建键
  // 测试场景：添加两列后，通过列索引0和1创建复合键，期望生成包含这两个索引的复合键
  @Test void testAddKeyUsingColumnIndex() {
    // 创建一个新的MockTable实例用于测试
    MockCatalogReader.MockTable t = newTable();
    // 添加名为"k1"的列，类型为INTEGER，该列索引为0
    t.addColumn("k1", TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER));
    // 添加名为"k2"的列，类型为INTEGER，该列索引为1
    t.addColumn("k2", TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER));
    // 使用ImmutableBitSet.of(0, 1)创建包含索引0和1的位集，通过位集为这两列创建复合键
    t.addKey(ImmutableBitSet.of(0, 1));
    // 验证表的键集合包含一个复合键：{0, 1}
    assertThat(t.getKeys(), hasToString("[{0, 1}]"));
  }

  // 测试方法：验证当添加键时使用了不存在的列索引，是否抛出IllegalArgumentException异常
  // 测试场景：添加两列后（索引为0和1），尝试使用索引0和2创建键，期望抛出异常因为索引2不存在
  @Test void testAddKeyUsingWrongIndexThrowsException() {
    // 创建一个新的MockTable实例用于测试
    MockCatalogReader.MockTable t = newTable();
    // 添加名为"k1"的列，类型为INTEGER，该列索引为0
    t.addColumn("k1", TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER));
    // 添加名为"k2"的列，类型为INTEGER，该列索引为1
    t.addColumn("k2", TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER));
    // 使用assertThrows验证当调用addKey(ImmutableBitSet.of(0, 2))时抛出IllegalArgumentException，因为索引2超出了列索引范围
    assertThrows(IllegalArgumentException.class, () -> t.addKey(ImmutableBitSet.of(0, 2)));
  }

  // 测试方法：验证多次添加键时，是否正确创建多个键
  // 测试场景：添加三列后，为k1创建单列键，为k2和k3创建复合键，期望生成两个键
  @Test void testAddKeyMultipleTimes() {
    // 创建一个新的MockTable实例用于测试
    MockCatalogReader.MockTable t = newTable();
    // 添加名为"k1"的列，类型为INTEGER，该列索引为0
    t.addColumn("k1", TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER));
    // 添加名为"k2"的列，类型为INTEGER，该列索引为1
    t.addColumn("k2", TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER));
    // 添加名为"k3"的列，类型为INTEGER，该列索引为2
    t.addColumn("k3", TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER));
    // 为k1列创建单列键，生成键{0}
    t.addKey("k1");
    // 为k2和k3列创建复合键，生成键{1, 2}
    t.addKey("k2", "k3");
    // 验证表的键集合包含两个键：{0}和{1, 2}
    assertThat(t.getKeys(), hasToString("[{0}, {1, 2}]"));
  }

  // 私有静态辅助方法：创建一个新的MockTable实例用于测试
  // 该方法封装了创建MockTable所需的复杂初始化逻辑，避免在每个测试方法中重复代码
  // 返回值：一个配置好的MockCatalogReader.MockTable实例
  private static MockCatalogReader.MockTable newTable() {

    // 创建一个匿名子类继承MockCatalogReader，使用共享的TYPE_FACTORY和false参数（表示不区分大小写）
    // MockCatalogReader是用于测试的模拟目录读取器，提供表、列等元数据信息
    MockCatalogReader catalogReader = new MockCatalogReader(TYPE_FACTORY, false) {
      // 重写init方法，直接返回this，跳过默认的初始化逻辑
      // init方法通常用于初始化目录内容，但在测试中我们不需要完整的初始化
      @Override public MockCatalogReader init() {
        return this;
      }
    };
    // 创建并返回一个新的MockTable实例，参数说明：
    // catalogReader: 目录读取器，提供元数据服务
    // "catalog": 目录名称，设置为测试用的"catalog"
    // "schema": 模式名称，设置为测试用的"schema"
    // "table": 表名称，设置为测试用的"table"
    // false: 是否为流式表，设置为false表示不是流式表
    // false: 是否为临时表，设置为false表示不是临时表
    // 0.0: 行数估计，设置为0表示没有行数估计
    // null: 原始表类型，设置为null表示没有原始类型
    // NullInitializerExpressionFactory.INSTANCE: 空初始化表达式工厂，用于生成列的默认值表达式
    return new MockCatalogReader.MockTable(catalogReader, "catalog", "schema", "table", false,
        false, 0.0, null, NullInitializerExpressionFactory.INSTANCE);
  }
}
