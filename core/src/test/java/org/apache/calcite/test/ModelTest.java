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
package org.apache.calcite.test; // 包声明，此类位于org.apache.calcite.test包下

import org.apache.calcite.model.JsonColumn; // 导入JsonColumn类，用于表示JSON格式的列定义
import org.apache.calcite.model.JsonCustomSchema; // 导入JsonCustomSchema类，用于表示JSON格式的自定义schema
import org.apache.calcite.model.JsonCustomTable; // 导入JsonCustomTable类，用于表示JSON格式的自定义表
import org.apache.calcite.model.JsonJdbcSchema; // 导入JsonJdbcSchema类，用于表示JSON格式的JDBC schema
import org.apache.calcite.model.JsonLattice; // 导入JsonLattice类，用于表示JSON格式的格子（lattice，用于物化视图）
import org.apache.calcite.model.JsonMapSchema; // 导入JsonMapSchema类，用于表示JSON格式的map schema
import org.apache.calcite.model.JsonRoot; // 导入JsonRoot类，用于表示JSON模型的根节点
import org.apache.calcite.model.JsonTable; // 导入JsonTable类，用于表示JSON格式的表定义
import org.apache.calcite.model.JsonTypeAttribute; // 导入JsonTypeAttribute类，用于表示JSON格式的类型属性
import org.apache.calcite.model.JsonView; // 导入JsonView类，用于表示JSON格式的视图定义

import com.fasterxml.jackson.core.JsonParser; // 导入Jackson的JsonParser类，用于解析JSON
import com.fasterxml.jackson.databind.ObjectMapper; // 导入Jackson的ObjectMapper类，用于JSON和Java对象的相互转换

import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.io.IOException; // 导入IOException类，用于处理IO异常
import java.net.URL; // 导入URL类，用于表示统一资源定位符
import java.util.List; // 导入List接口，用于表示列表集合

import static org.hamcrest.CoreMatchers.equalTo; // 导入Hamcrest的equalTo匹配器，用于断言相等
import static org.hamcrest.CoreMatchers.instanceOf; // 导入Hamcrest的instanceOf匹配器，用于断言类型
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest的is匹配器，用于增强可读性的断言
import static org.hamcrest.CoreMatchers.notNullValue; // 导入Hamcrest的notNullValue匹配器，用于断言非空
import static org.hamcrest.CoreMatchers.nullValue; // 导入Hamcrest的nullValue匹配器，用于断言为空
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的assertThat方法，用于断言
import static org.hamcrest.Matchers.anEmptyMap; // 导入Hamcrest的anEmptyMap匹配器，用于断言空map
import static org.hamcrest.Matchers.hasSize; // 导入Hamcrest的hasSize匹配器，用于断言集合大小
import static org.junit.jupiter.api.Assertions.assertNull; // 导入JUnit5的assertNull方法，用于断言为空
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit5的assertTrue方法，用于断言为真
import static org.junit.jupiter.api.Assertions.fail; // 导入JUnit5的fail方法，用于标记测试失败

import static java.util.Objects.requireNonNull; // 导入Objects类的requireNonNull方法，用于检查对象非空

/**
 * Unit test for data models. // 数据模型的单元测试类
 * 本类用于测试Calcite的数据模型（Model）功能，包括：
 * 1. JSON/YAML格式的模型文件解析
 * 2. Schema（模式）的读取和验证
 * 3. Table（表）的读取和验证
 * 4. View（视图）的读取和验证
 * 5. Lattice（格子/物化视图）的读取和验证
 * 6. 自定义Schema和自定义Table的读取和验证
 * 7. JDBC Schema的读取和验证
 * 8. 类型系统的读取和验证
 * 9. 模型文件格式的自动检测（JSON或YAML）
 * 10. 必需属性缺失时的错误处理
 */
class ModelTest { // ModelTest类定义，用于测试Calcite的数据模型功能
  private ObjectMapper mapper() { // 私有辅助方法，用于创建并配置ObjectMapper实例，用于JSON解析
    final ObjectMapper mapper = new ObjectMapper(); // 创建一个新的ObjectMapper实例，用于JSON和Java对象的转换
    mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true); // 配置JSON解析器允许字段名不加引号（宽松JSON格式）
    mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true); // 配置JSON解析器允许使用单引号（宽松JSON格式）
    return mapper; // 返回配置好的ObjectMapper实例
  }

  /** Reads a simple schema from a string into objects. */ // 测试方法：从JSON字符串读取简单的schema并转换为Java对象
  @Test void testRead() throws IOException { // 测试方法标记，声明可能抛出IOException异常
    final ObjectMapper mapper = mapper(); // 调用mapper()方法获取配置好的ObjectMapper实例
    final String json = "{\n" // 定义JSON字符串，包含一个简单的数据模型定义
        + "  version: '1.0',\n" // 模型版本号，使用宽松JSON格式（字段名不加引号）
        + "   schemas: [\n" // schemas数组，包含一个或多个schema定义
        + "     {\n" // 第一个schema定义开始
        + "       name: 'FoodMart',\n" // schema名称为FoodMart
        + "       types: [\n" // types数组，定义自定义类型
        + "         {\n" // 第一个类型定义开始
        + "           name: 'mytype1',\n" // 类型名称为mytype1
        + "           attributes: [\n" // attributes数组，定义类型的属性
        + "             {\n" // 第一个属性定义开始
        + "               name: 'f1',\n" // 属性名称为f1
        + "               type: 'BIGINT'\n" // 属性类型为BIGINT
        + "             }\n" // 第一个属性定义结束
        + "           ]\n" // attributes数组结束
        + "         }\n" // 第一个类型定义结束
        + "       ],\n" // types数组结束
        + "       tables: [\n" // tables数组，定义表
        + "         {\n" // 第一个表定义开始
        + "           name: 'time_by_day',\n" // 表名称为time_by_day
        + "           factory: 'com.test',\n" // 表工厂类全限定名为com.test，用于创建表实例
        + "           columns: [\n" // columns数组，定义表的列
        + "             {\n" // 第一个列定义开始
        + "               name: 'time_id'\n" // 列名称为time_id
        + "             }\n" // 第一个列定义结束
        + "           ]\n" // columns数组结束
        + "         },\n" // 第一个表定义结束
        + "         {\n" // 第二个表定义开始
        + "           name: 'sales_fact_1997',\n" // 表名称为sales_fact_1997
        + "           factory: 'com.test',\n" // 表工厂类全限定名为com.test
        + "           columns: [\n" // columns数组
        + "             {\n" // 第一个列定义开始
        + "               name: 'time_id'\n" // 列名称为time_id
        + "             }\n" // 第一个列定义结束
        + "           ]\n" // columns数组结束
        + "         }\n" // 第二个表定义结束
        + "       ]\n" // tables数组结束
        + "     }\n" // 第一个schema定义结束
        + "   ]\n" // schemas数组结束
        + "}"; // JSON字符串结束
    JsonRoot root = mapper.readValue(json, JsonRoot.class); // 使用ObjectMapper将JSON字符串解析为JsonRoot对象
    assertThat(root.version, is("1.0")); // 断言：验证模型版本号为1.0
    assertThat(root.schemas, hasSize(1)); // 断言：验证schemas数组包含1个元素
    final JsonMapSchema schema = (JsonMapSchema) root.schemas.get(0); // 获取第一个schema并转换为JsonMapSchema类型
    assertThat(schema.name, is("FoodMart")); // 断言：验证schema名称为FoodMart
    assertThat(schema.types, hasSize(1)); // 断言：验证types数组包含1个元素
    final List<JsonTypeAttribute> attributes = schema.types.get(0).attributes; // 获取第一个类型的所有属性
    assertThat(attributes.get(0).name, is("f1")); // 断言：验证第一个属性名称为f1
    assertThat(attributes.get(0).type, is("BIGINT")); // 断言：验证第一个属性类型为BIGINT
    assertThat(schema.tables, hasSize(2)); // 断言：验证tables数组包含2个元素
    final JsonTable table0 = schema.tables.get(0); // 获取第一个表
    assertThat(table0.name, is("time_by_day")); // 断言：验证第一个表名称为time_by_day
    final JsonTable table1 = schema.tables.get(1); // 获取第二个表
    assertThat(table1.name, is("sales_fact_1997")); // 断言：验证第二个表名称为sales_fact_1997
    assertThat(table0.columns, hasSize(1)); // 断言：验证第一个表包含1列
    final JsonColumn column = table0.columns.get(0); // 获取第一个表的第一个列
    assertThat(column.name, is("time_id")); // 断言：验证列名称为time_id
  }

  /** Reads a simple schema containing JdbcSchema, a sub-type of Schema. */ // 测试方法：读取包含JdbcSchema子类型的简单schema
  @Test void testSubtype() throws IOException { // 测试方法标记，声明可能抛出IOException异常
    final ObjectMapper mapper = mapper(); // 获取配置好的ObjectMapper实例
    final String json = "{\n" // 定义JSON字符串，包含JDBC类型的schema定义
        + "  version: '1.0',\n" // 模型版本号
        + "   schemas: [\n" // schemas数组
        + "     {\n" // 第一个schema定义开始
        + "       type: 'jdbc',\n" // schema类型为jdbc，表示这是一个JDBC schema
        + "       name: 'FoodMart',\n" // schema名称为FoodMart
        + "       jdbcUser: 'u_baz',\n" // JDBC连接用户名
        + "       jdbcPassword: 'p_baz',\n" // JDBC连接密码
        + "       jdbcUrl: 'jdbc:baz',\n" // JDBC连接URL
        + "       jdbcCatalog: 'cat_baz',\n" // JDBC目录名称
        + "       jdbcSchema: ''\n" // JDBC schema名称，此处为空字符串
        + "     }\n" // 第一个schema定义结束
        + "   ]\n" // schemas数组结束
        + "}"; // JSON字符串结束
    JsonRoot root = mapper.readValue(json, JsonRoot.class); // 将JSON字符串解析为JsonRoot对象
    assertThat(root.version, is("1.0")); // 断言：验证模型版本号为1.0
    assertThat(root.schemas, hasSize(1)); // 断言：验证schemas数组包含1个元素
    final JsonJdbcSchema schema = (JsonJdbcSchema) root.schemas.get(0); // 获取第一个schema并转换为JsonJdbcSchema类型
    assertThat(schema.name, is("FoodMart")); // 断言：验证schema名称为FoodMart
  }

  /** Reads a custom schema. */ // 测试方法：读取自定义schema
  @Test void testCustomSchema() throws IOException { // 测试方法标记，声明可能抛出IOException异常
    final ObjectMapper mapper = mapper(); // 获取配置好的ObjectMapper实例
    JsonRoot root = mapper.readValue("{\n" // 将JSON字符串解析为JsonRoot对象
            + "  version: '1.0',\n" // 模型版本号
            + "   schemas: [\n" // schemas数组
            + "     {\n" // 第一个schema定义开始
            + "       type: 'custom',\n" // schema类型为custom，表示这是一个自定义schema
            + "       name: 'My Custom Schema',\n" // schema名称为My Custom Schema
            + "       factory: 'com.acme.MySchemaFactory',\n" // schema工厂类全限定名，用于创建schema实例
            + "       operand: {a: 'foo', b: [1, 3.5] },\n" // operand参数，传递给schema工厂的配置参数，包含字符串和数组
            + "       tables: [\n" // tables数组，定义schema中的表
            + "         { type: 'custom', name: 'T1', factory: 'com.test' },\n" // 第一个自定义表，名称为T1，工厂为com.test，无operand
            + "         { type: 'custom', name: 'T2', factory: 'com.test', operand: {} },\n" // 第二个自定义表，名称为T2，工厂为com.test，operand为空map
            + "         { type: 'custom', name: 'T3', factory: 'com.test', operand: {a: 'foo'} }\n" // 第三个自定义表，名称为T3，工厂为com.test，operand包含键值对
            + "       ]\n" // tables数组结束
            + "     },\n" // 第一个schema定义结束
            + "     {\n" // 第二个schema定义开始
            + "       type: 'custom',\n" // schema类型为custom
            + "       factory: 'com.acme.MySchemaFactory',\n" // schema工厂类全限定名
            + "       name: 'has-no-operand'\n" // schema名称为has-no-operand，表示没有operand参数
            + "     }\n" // 第二个schema定义结束
            + "   ]\n" // schemas数组结束
            + "}", // JSON字符串结束
        JsonRoot.class); // 指定目标类型为JsonRoot.class
    assertThat(root.version, is("1.0")); // 断言：验证模型版本号为1.0
    assertThat(root.schemas, hasSize(2)); // 断言：验证schemas数组包含2个元素
    final JsonCustomSchema schema = (JsonCustomSchema) root.schemas.get(0); // 获取第一个schema并转换为JsonCustomSchema类型
    assertThat(schema.name, is("My Custom Schema")); // 断言：验证schema名称为My Custom Schema
    assertThat(schema.factory, is("com.acme.MySchemaFactory")); // 断言：验证工厂类全限定名为com.acme.MySchemaFactory
    assertThat(schema.operand, notNullValue()); // 断言：验证operand不为null
    assertThat(schema.operand.get("a"), is("foo")); // 断言：验证operand中键a的值为foo
    assertNull(schema.operand.get("c")); // 断言：验证operand中不存在键c，值为null
    assertThat(schema.operand.get("b"), instanceOf(List.class)); // 断言：验证operand中键b的值是List类型
    final List<Object> list = (List<Object>) schema.operand.get("b"); // 获取operand中键b的值并转换为List
    assertThat(list, hasSize(2)); // 断言：验证list包含2个元素
    assertThat(list.get(0), is(1)); // 断言：验证list第一个元素为1
    assertThat(list.get(1), is(3.5)); // 断言：验证list第二个元素为3.5

    assertThat(schema.tables, hasSize(3)); // 断言：验证schema.tables包含3个元素
    final JsonCustomTable table0 = (JsonCustomTable) schema.tables.get(0); // 获取第一个表并转换为JsonCustomTable类型
    assertThat(table0.operand, nullValue()); // 断言：验证第一个表的operand为null
    final JsonCustomTable table1 = (JsonCustomTable) schema.tables.get(1); // 获取第二个表并转换为JsonCustomTable类型
    assertThat(table1.operand, notNullValue()); // 断言：验证第二个表的operand不为null
    assertThat(table1.operand, anEmptyMap()); // 断言：验证第二个表的operand为空map
  }

  /** Tests that an immutable schema in a model cannot contain a
   * materialization. */ // 测试方法：验证不可变schema不能包含物化视图
  @Test void testModelImmutableSchemaCannotContainMaterialization() { // 测试方法标记
    CalciteAssert.model("{\n" // 使用CalciteAssert工具类创建模型测试
        + "  version: '1.0',\n" // 模型版本号
        + "  defaultSchema: 'adhoc',\n" // 默认schema名称为adhoc
        + "  schemas: [\n" // schemas数组
        + "    {\n" // 第一个schema定义开始
        + "      name: 'empty'\n" // schema名称为empty
        + "    },\n" // 第一个schema定义结束
        + "    {\n" // 第二个schema定义开始
        + "      name: 'adhoc',\n" // schema名称为adhoc
        + "      type: 'custom',\n" // schema类型为custom
        + "      factory: '" // factory属性开始
        + JdbcTest.MySchemaFactory.class.getName() // 获取JdbcTest.MySchemaFactory类的全限定名
        + "',\n" // factory属性结束
        + "      operand: {\n" // operand参数开始
        + "           'tableName': 'ELVIS',\n" // 表名参数为ELVIS
        + "           'mutable': false\n" // mutable参数为false，表示schema是不可变的
        + "      },\n" // operand参数结束
        + "      materializations: [\n" // materializations数组，定义物化视图
        + "        {\n" // 第一个物化视图定义开始
        + "          table: 'v',\n" // 物化视图表名为v
        + "          sql: 'values (1)'\n" // 物化视图的SQL语句
        + "        }\n" // 第一个物化视图定义结束
        + "      ]\n" // materializations数组结束
        + "    }\n" // 第二个schema定义结束
        + "  ]\n" // schemas数组结束
        + "}") // 模型定义结束
        .connectThrows("Cannot define materialization; parent schema 'adhoc' " // 断言连接时抛出异常
            + "is not a SemiMutableSchema"); // 异常消息：不能定义物化视图，父schema adhoc不是SemiMutableSchema
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1899">[CALCITE-1899]
   * When reading model, give error if mandatory JSON attributes are
   * missing</a>.
   *
   * <p>Schema without name should give useful error, not
   * NullPointerException. */ // 测试方法：验证缺少必需JSON属性时的错误处理
  @Test void testSchemaWithoutName() { // 测试方法标记
    final String model = "{\n" // 定义模型字符串，包含一个没有name属性的schema
        + "  version: '1.0',\n" // 模型版本号
        + "  defaultSchema: 'adhoc',\n" // 默认schema名称为adhoc
        + "  schemas: [ {\n" // schemas数组，包含一个空的schema定义（缺少name属性）
        + "  } ]\n" // 空schema定义结束
        + "}"; // 模型字符串结束
    CalciteAssert.model(model) // 使用CalciteAssert工具类创建模型测试
        .connectThrows("Missing required creator property 'name'"); // 断言连接时抛出异常，提示缺少必需的name属性
  }

  @Test void testCustomSchemaWithoutFactory() { // 测试方法：验证自定义schema缺少factory属性时的错误处理
    final String model = "{\n" // 定义模型字符串，包含一个没有factory属性的自定义schema
        + "  version: '1.0',\n" // 模型版本号
        + "  defaultSchema: 'adhoc',\n" // 默认schema名称为adhoc
        + "  schemas: [ {\n" // schemas数组
        + "    type: 'custom',\n" // schema类型为custom
        + "    name: 'my_custom_schema'\n" // schema名称为my_custom_schema，但缺少factory属性
        + "  } ]\n" // schemas数组结束
        + "}"; // 模型字符串结束
    CalciteAssert.model(model) // 使用CalciteAssert工具类创建模型测试
        .connectThrows("Missing required creator property 'factory'"); // 断言连接时抛出异常，提示缺少必需的factory属性
  }

  /** Tests a model containing a lattice and some views. */ // 测试方法：验证包含lattice（格子/物化视图）和视图的模型
  @Test void testReadLattice() throws IOException { // 测试方法标记，声明可能抛出IOException异常
    final ObjectMapper mapper = mapper(); // 获取配置好的ObjectMapper实例
    JsonRoot root = mapper.readValue("{\n" // 将JSON字符串解析为JsonRoot对象
            + "  version: '1.0',\n" // 模型版本号
            + "   schemas: [\n" // schemas数组
            + "     {\n" // 第一个schema定义开始
            + "       name: 'FoodMart',\n" // schema名称为FoodMart
            + "       tables: [\n" // tables数组，定义表和视图
            + "         {\n" // 第一个表定义开始
            + "           name: 'time_by_day',\n" // 表名称为time_by_day
            + "           factory: 'com.test',\n" // 表工厂类全限定名为com.test
            + "           columns: [\n" // columns数组
            + "             {\n" // 第一个列定义开始
            + "               name: 'time_id'\n" // 列名称为time_id
            + "             }\n" // 第一个列定义结束
            + "           ]\n" // columns数组结束
            + "         },\n" // 第一个表定义结束
            + "         {\n" // 第二个表定义开始
            + "           name: 'sales_fact_1997',\n" // 表名称为sales_fact_1997
            + "           factory: 'com.test',\n" // 表工厂类全限定名为com.test
            + "           columns: [\n" // columns数组
            + "             {\n" // 第一个列定义开始
            + "               name: 'time_id'\n" // 列名称为time_id
            + "             }\n" // 第一个列定义结束
            + "           ]\n" // columns数组结束
            + "         },\n" // 第二个表定义结束
            + "         {\n" // 第三个表定义开始（视图）
            + "           name: 'V',\n" // 视图名称为V
            + "           type: 'view',\n" // 类型为view，表示这是一个视图
            + "           sql: 'values (1)'\n" // 视图的SQL语句
            + "         },\n" // 第三个表定义结束（视图）
            + "         {\n" // 第四个表定义开始（多行SQL视图）
            + "           name: 'V2',\n" // 视图名称为V2
            + "           type: 'view',\n" // 类型为view
            + "           sql: [ 'values (1)', '(2)' ]\n" // 视图的SQL语句为数组格式，将被拼接成多行SQL
            + "         }\n" // 第四个表定义结束（多行SQL视图）
            + "       ],\n" // tables数组结束
            + "       lattices: [\n" // lattices数组，定义格子（lattice，用于物化视图）
            + "         {\n" // 第一个lattice定义开始
            + "           name: 'SalesStar',\n" // lattice名称为SalesStar
            + "           sql: 'select * from sales_fact_1997'\n" // lattice的SQL语句，单行格式
            + "         },\n" // 第一个lattice定义结束
            + "         {\n" // 第二个lattice定义开始
            + "           name: 'SalesStar2',\n" // lattice名称为SalesStar2
            + "           sql: [ 'select *', 'from sales_fact_1997' ]\n" // lattice的SQL语句为数组格式，将被拼接成多行SQL
            + "         }\n" // 第二个lattice定义结束
            + "       ]\n" // lattices数组结束
            + "     }\n" // 第一个schema定义结束
            + "   ]\n" // schemas数组结束
            + "}", // JSON字符串结束
        JsonRoot.class); // 指定目标类型为JsonRoot.class
    assertThat(root.version, is("1.0")); // 断言：验证模型版本号为1.0
    assertThat(root.schemas, hasSize(1)); // 断言：验证schemas数组包含1个元素
    final JsonMapSchema schema = (JsonMapSchema) root.schemas.get(0); // 获取第一个schema并转换为JsonMapSchema类型
    assertThat(schema.name, is("FoodMart")); // 断言：验证schema名称为FoodMart
    assertThat(schema.lattices, hasSize(2)); // 断言：验证lattices数组包含2个元素
    final JsonLattice lattice0 = schema.lattices.get(0); // 获取第一个lattice
    assertThat(lattice0.name, is("SalesStar")); // 断言：验证第一个lattice名称为SalesStar
    assertThat(lattice0.getSql(), is("select * from sales_fact_1997")); // 断言：验证第一个lattice的SQL语句
    final JsonLattice lattice1 = schema.lattices.get(1); // 获取第二个lattice
    assertThat(lattice1.name, is("SalesStar2")); // 断言：验证第二个lattice名称为SalesStar2
    assertThat(lattice1.getSql(), is("select *\nfrom sales_fact_1997\n")); // 断言：验证第二个lattice的SQL语句（多行格式）
    assertThat(schema.tables, hasSize(4)); // 断言：验证tables数组包含4个元素
    final JsonTable table1 = schema.tables.get(1); // 获取第二个表
    assertTrue(!(table1 instanceof JsonView)); // 断言：验证第二个表不是JsonView类型
    final JsonTable table2 = schema.tables.get(2); // 获取第三个表
    assertThat(table2, instanceOf(JsonView.class)); // 断言：验证第三个表是JsonView类型
    assertThat(((JsonView) table2).getSql(), equalTo("values (1)")); // 断言：验证第三个表（视图）的SQL语句
    final JsonTable table3 = schema.tables.get(3); // 获取第四个表
    assertThat(table3, instanceOf(JsonView.class)); // 断言：验证第四个表是JsonView类型
    assertThat(((JsonView) table3).getSql(), equalTo("values (1)\n(2)\n")); // 断言：验证第四个表（视图）的SQL语句（多行格式）
  }

  /** Tests a model with bad multi-line SQL. */ // 测试方法：验证包含错误多行SQL的模型
  @Test void testReadBadMultiLineSql() throws IOException { // 测试方法标记，声明可能抛出IOException异常
    final ObjectMapper mapper = mapper(); // 获取配置好的ObjectMapper实例
    JsonRoot root = mapper.readValue("{\n" // 将JSON字符串解析为JsonRoot对象
            + "  version: '1.0',\n" // 模型版本号
            + "   schemas: [\n" // schemas数组
            + "     {\n" // 第一个schema定义开始
            + "       name: 'FoodMart',\n" // schema名称为FoodMart
            + "       tables: [\n" // tables数组
            + "         {\n" // 第一个表定义开始（视图）
            + "           name: 'V',\n" // 视图名称为V
            + "           type: 'view',\n" // 类型为view
            + "           sql: [ 'values (1)', 2 ]\n" // 视图的SQL语句为数组格式，但第二个元素是数字2而不是字符串，这是错误的
            + "         }\n" // 第一个表定义结束（视图）
            + "       ]\n" // tables数组结束
            + "     }\n" // 第一个schema定义结束
            + "   ]\n" // schemas数组结束
            + "}", // JSON字符串结束
        JsonRoot.class); // 指定目标类型为JsonRoot.class
    assertThat(root.schemas, hasSize(1)); // 断言：验证schemas数组包含1个元素
    final JsonMapSchema schema = (JsonMapSchema) root.schemas.get(0); // 获取第一个schema并转换为JsonMapSchema类型
    assertThat(schema.tables, hasSize(1)); // 断言：验证tables数组包含1个元素
    final JsonView table1 = (JsonView) schema.tables.get(0); // 获取第一个表并转换为JsonView类型
    try { // 尝试执行可能抛出异常的代码
      String s = table1.getSql(); // 调用getSql()方法获取SQL语句
      fail("expected error, got " + s); // 如果没有抛出异常，测试失败
    } catch (RuntimeException e) { // 捕获RuntimeException异常
      assertThat(e.getMessage(), // 断言：验证异常消息
          equalTo("each element of a string list must be a string; found: 2")); // 期望的异常消息：字符串列表的每个元素必须是字符串；发现：2
    }
  }

  @Test void testYamlInlineDetection() throws Exception { // 测试方法：验证YAML内联模型的自动检测
    // yaml model with different line endings // YAML模型，使用不同的行结束符
    final String yamlModel = "version: 1.0\r\n" // 定义YAML模型字符串，版本号为1.0，使用CRLF行结束符
        + "schemas:\n" // schemas键，使用LF行结束符
        + "- type: custom\r\n" // 第一个schema，类型为custom，使用CRLF行结束符
        + "  name: 'MyCustomSchema'\n" // schema名称为MyCustomSchema，使用LF行结束符
        + "  factory: " + JdbcTest.MySchemaFactory.class.getName() + "\r\n"; // schema工厂类全限定名，使用CRLF行结束符
    CalciteAssert.model(yamlModel).doWithConnection(calciteConnection -> null); // 使用CalciteAssert测试YAML模型，执行连接操作但不做任何操作
    // with a comment // 带注释的YAML模型
    CalciteAssert.model("\n  \r\n# comment\n " + yamlModel) // 在YAML模型前添加空行、空格和注释
        .doWithConnection(calciteConnection -> null); // 测试带有注释的YAML模型
    // if starts with { -> treated as json // 如果以{开头，则被视为JSON
    CalciteAssert.model("  { " + yamlModel + " }") // 在YAML模型前后添加花括号，使其看起来像JSON
        .connectThrows("Unexpected character ('s' (code 115)): " // 断言连接时抛出异常，提示JSON解析错误
            + "was expecting comma to separate Object entries"); // 异常消息：期望用逗号分隔对象条目
    // if starts with /* -> treated as json // 如果以/*开头，则被视为JSON注释
    CalciteAssert.model("  /* " + yamlModel) // 在YAML模型前添加/*，使其看起来像JSON注释
        .connectThrows("Unexpected end-of-input in a comment"); // 断言连接时抛出异常，提示注释未正确结束
  }

  @Test void testYamlFileDetection() throws Exception { // 测试方法：验证YAML文件的自动检测
    final URL inUrl = // 定义URL变量，用于引用YAML模型文件
        requireNonNull(ModelTest.class.getResource("/empty-model.yaml"), "url"); // 获取类路径下的empty-model.yaml文件资源，如果为null则抛出NullPointerException
    CalciteAssert.that() // 创建CalciteAssert实例
        .withModel(inUrl) // 设置模型文件URL
        .doWithConnection(calciteConnection -> null); // 执行连接操作但不做任何操作
  }
} // ModelTest类定义结束
