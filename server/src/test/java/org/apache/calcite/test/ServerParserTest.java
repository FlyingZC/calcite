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
// Apache许可证头部声明,声明代码归属和使用条款
package org.apache.calcite.test; // 定义包名,属于Calcite项目的测试包

import org.apache.calcite.sql.parser.SqlParserFixture; // 导入SqlParserFixture类,用于配置SQL解析器的测试夹具
import org.apache.calcite.sql.parser.SqlParserTest; // 导入SqlParserTest基类,提供SQL解析测试的框架
import org.apache.calcite.sql.parser.ddl.SqlDdlParserImpl; // 导入SqlDdlParserImpl类,DDL(Data Definition Language)解析器的实现类

import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解,用于标记测试方法

/**
 * Tests SQL parser extensions for DDL. // 类作用:测试SQL解析器对DDL扩展的支持,验证数据定义语言(DDL)语法解析是否正确
 *
 * <p>Remaining tasks: // 剩余任务:列出尚未完成的功能开发项
 * <ul>
 *
 * <li>"create table x (a int) as values 1, 2" should fail validation; // 任务1:带数据类型的CREATE TABLE AS应该验证失败,因为CREATE TABLE ... AS中不允许指定数据类型
 * data type not allowed in "create table ... as".
 *
 * <li>"create table x (a int, b int as (a + 1)) stored" // 任务2:存储的生成列在INSERT时不应该被指定,应该生成b的检查约束,在INSERT时应该像有默认值一样填充b
 * should not allow b to be specified in insert;
 * should generate check constraint on b;
 * should populate b in insert as if it had a default
 *
 * <li>"create table as select" should store constraints // 任务3:CREATE TABLE AS SELECT应该存储由优化器推导出的约束条件
 * deduced by planner
 *
 * <li>during CREATE VIEW, check for a table and a materialized view // 任务4:在创建视图时,检查是否存在同名的表和物化视图(它们使用相同的命名空间)
 * with the same name (they have the same namespace)
 *
 * </ul>
 */
class ServerParserTest extends SqlParserTest { // 类定义:ServerParserTest继承自SqlParserTest,专门用于测试服务器模块的DDL解析功能

  @Override public SqlParserFixture fixture() { // 重写fixture方法:配置SQL解析器测试夹具,返回自定义的解析器配置
    return super.fixture() // 调用父类的fixture方法获取默认配置
        .withConfig(c -> c.withParserFactory(SqlDdlParserImpl.FACTORY)); // 使用lambda表达式配置解析器工厂,设置为SqlDdlParserImpl.FACTORY以支持DDL语法解析
  }

  @Test void testCreateSchema() { // 测试方法:测试CREATE SCHEMA语句的解析,验证创建模式(schema)语法是否正确解析
    sql("create schema x") // 调用sql方法传入待测试的SQL语句,测试"create schema x"语句
        .ok("CREATE SCHEMA `X`"); // 断言解析结果正确,期望输出为"CREATE SCHEMA `X`",标识符被反引号包围并转为大写
  }

  @Test void testProcessCreateTableWithDefault() { // 测试方法:测试带默认值的CREATE TABLE语句解析,验证表字段默认值语法的正确性
    String sql = "create table tdef (i int not null, j int default 100)"; // 定义待测试的SQL语句,创建表tdef,字段i为非空整数,字段j为带默认值100的整数
    String expected = "CREATE TABLE `TDEF` (`I` INTEGER NOT NULL," // 定义期望的标准化输出,表名和字段名被反引号包围并转为大写
        + " `J` INTEGER DEFAULT 100)"; // 期望输出包含默认值定义DEFAULT 100
    sql(sql).ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  @Test void testCreateOrReplaceSchema() { // 测试方法:测试CREATE OR REPLACE SCHEMA语句解析,验证创建或替换模式的语法
    sql("create or replace schema x") // 调用sql方法传入待测试的SQL语句,测试"create or replace schema x"语句
        .ok("CREATE OR REPLACE SCHEMA `X`"); // 断言解析结果正确,期望输出为"CREATE OR REPLACE SCHEMA `X`"
  }

  @Test void testCreateForeignSchema() { // 测试方法:测试CREATE FOREIGN SCHEMA语句解析,验证创建外部模式(外部数据源模式)的语法,支持多种选项类型
    final String sql = "create or replace foreign schema x\n" // 定义待测试的SQL语句,创建或替换外部模式x
        + "type 'jdbc'\n" // 指定外部模式类型为jdbc,表示使用JDBC连接外部数据源
        + "options (\n" // 开始定义选项列表,配置外部数据源的连接参数
        + "  aBoolean true,\n" // 选项1:布尔类型参数aBoolean,值为true
        + "  anInteger -45,\n" // 选项2:整数类型参数anInteger,值为-45(负数测试)
        + "  aDate DATE '1970-03-21',\n" // 选项3:日期类型参数aDate,值为1970-03-21
        + "  \"quoted.id\" TIMESTAMP '1970-03-21 12:4:56.78',\n" // 选项4:带引号的标识符quoted.id,时间戳类型,值为1970-03-21 12:4:56.78
        + "  aString 'foo''bar')"; // 选项5:字符串类型参数aString,值为'foo''bar'(测试字符串中的单引号转义)
    final String expected = "CREATE OR REPLACE FOREIGN SCHEMA `X` TYPE 'jdbc' " // 定义期望的标准化输出,模式名转为大写并加反引号
        + "OPTIONS (`ABOOLEAN` TRUE," // 期望输出中选项名转为大写并加反引号,布尔值转为大写TRUE
        + " `ANINTEGER` -45," // 整数值保持原样
        + " `ADATE` DATE '1970-03-21'," // 日期值保持原格式
        + " `quoted.id` TIMESTAMP '1970-03-21 12:4:56.78'," // 带引号的标识符保持原样(不转大写)
        + " `ASTRING` 'foo''bar')"; // 字符串值保持原样,转义的单引号保持转义
    sql(sql).ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  @Test void testCreateForeignSchema2() { // 测试方法:测试使用LIBRARY子句的CREATE FOREIGN SCHEMA语句解析,验证通过自定义工厂类创建外部模式
    final String sql = "create or replace foreign schema x\n" // 定义待测试的SQL语句,创建或替换外部模式x
        + "library 'com.example.ExampleSchemaFactory'\n" // 指定库类名,使用com.example.ExampleSchemaFactory作为模式工厂类
        + "options ()"; // 空选项列表,测试不传递任何参数的情况
    final String expected = "CREATE OR REPLACE FOREIGN SCHEMA `X` " // 定义期望的标准化输出
        + "LIBRARY 'com.example.ExampleSchemaFactory' " // 期望输出包含LIBRARY子句和完整的类名
        + "OPTIONS ()"; // 期望输出包含空的OPTIONS子句
    sql(sql).ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  @Test void testCreateTypeWithAttributeList() { // 测试方法:测试带属性列表的CREATE TYPE语句解析,验证创建自定义类型(结构化类型)的语法
    sql("create type x.mytype1 as (i int not null, j varchar(5) null)") // 调用sql方法,测试在模式x中创建类型mytype1,包含两个属性:i和j
        .ok("CREATE TYPE `X`.`MYTYPE1` AS (`I` INTEGER NOT NULL, `J` VARCHAR(5))"); // 断言解析结果正确,类型名转为大写,属性名转为大写,varchar(5)保持原样,null被省略(因为默认可为空)
  }

  @Test void testCreateTypeWithBaseType() { // 测试方法:测试基于基本类型的CREATE TYPE语句解析,验证创建别名类型(基于现有类型的类型别名)的语法
    sql("create type mytype1 as varchar(5)") // 调用sql方法,测试创建类型mytype1,它是varchar(5)类型的别名
        .ok("CREATE TYPE `MYTYPE1` AS VARCHAR(5)"); // 断言解析结果正确,类型名转为大写,基本类型定义保持原样
  }

  @Test void testCreateOrReplaceTypeWith() { // 测试方法:测试CREATE OR REPLACE TYPE语句解析,验证创建或替换类型的语法
    sql("create or replace type mytype1 as varchar(5)") // 调用sql方法,测试创建或替换类型mytype1,它是varchar(5)类型的别名
        .ok("CREATE OR REPLACE TYPE `MYTYPE1` AS VARCHAR(5)"); // 断言解析结果正确,包含OR REPLACE关键字
  }

  @Test void testCreateTable() { // 测试方法:测试基本的CREATE TABLE语句解析,验证创建表的语法,包括字段定义和约束
    sql("create table x (i int not null, j varchar(5) null)") // 调用sql方法,测试创建表x,包含两个字段:i为非空整数,j为可为空的varchar(5)
        .ok("CREATE TABLE `X` (`I` INTEGER NOT NULL, `J` VARCHAR(5))"); // 断言解析结果正确,表名和字段名转为大写并加反引号,null被省略(默认可为空)
  }

  @Test void testCreateTableAsSelect() { // 测试方法:测试CREATE TABLE AS SELECT语句解析,验证通过查询结果创建表的语法(CTAS)
    final String expected = "CREATE TABLE `X` AS\n" // 定义期望的标准化输出,CREATE TABLE AS SELECT语句
        + "SELECT *\n" // SELECT子句保持原样,从emp表选择所有字段
        + "FROM `EMP`"; // FROM子句,表名转为大写并加反引号
    sql("create table x as select * from emp") // 调用sql方法,测试通过查询emp表创建表x
        .ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  @Test void testCreateTableIfNotExistsAsSelect() { // 测试方法:测试CREATE TABLE IF NOT EXISTS AS SELECT语句解析,验证条件性创建表的语法
    final String expected = "CREATE TABLE IF NOT EXISTS `X`.`Y` AS\n" // 定义期望的标准化输出,包含IF NOT EXISTS子句和模式限定表名
        + "SELECT *\n" // SELECT子句
        + "FROM `EMP`"; // FROM子句
    sql("create table if not exists x.y as select * from emp") // 调用sql方法,测试仅在表不存在时创建x.y表
        .ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  @Test void testCreateTableAsValues() { // 测试方法:测试CREATE TABLE AS VALUES语句解析,验证通过VALUES子句创建表的语法
    final String expected = "CREATE TABLE `X` AS\n" // 定义期望的标准化输出
        + "VALUES (ROW(1)),\n" // VALUES子句,每个值被包装为ROW构造函数
        + "(ROW(2))"; // 第二个值也被包装为ROW
    sql("create table x as values 1, 2") // 调用sql方法,测试通过VALUES创建包含两行数据的表x
        .ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  @Test void testCreateTableAsSelectColumnList() { // 测试方法:测试带列名列表的CREATE TABLE AS SELECT语句解析,验证指定列名创建表的语法
    final String expected = "CREATE TABLE `X` (`A`, `B`) AS\n" // 定义期望的标准化输出,包含列名列表(a, b)
        + "SELECT *\n" // SELECT子句,从emp表选择所有字段
        + "FROM `EMP`"; // FROM子句
    sql("create table x (a, b) as select * from emp") // 调用sql方法,测试创建表x并指定列名为a和b
        .ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  @Test void testCreateTableCheck() { // 测试方法:测试带CHECK约束的CREATE TABLE语句解析,验证检查约束语法的正确性
    final String expected = "CREATE TABLE `X` (`I` INTEGER NOT NULL," // 定义期望的标准化输出,包含非空约束
        + " CONSTRAINT `C1` CHECK (`I` < 10), `J` INTEGER)"; // 期望输出包含命名约束C1,检查条件为i < 10,约束名转为大写并加反引号
    sql("create table x (i int not null, constraint c1 check (i < 10), j int)") // 调用sql方法,测试创建表x并添加CHECK约束
        .ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  @Test void testCreateTableVirtualColumn() { // 测试方法:测试带生成列(virtual column)的CREATE TABLE语句解析,验证生成列语法的正确性
    final String sql = "create table if not exists x (\n" // 定义待测试的SQL语句,创建或替换表x
        + " i int not null,\n" // 字段i:普通整数列,非空约束
        + " j int generated always as (i + 1) stored,\n" // 字段j:存储的生成列,基于i+1计算,物理存储
        + " k int as (j + 1) virtual,\n" // 字段k:虚拟生成列,基于j+1计算,不物理存储(默认virtual)
        + " m int as (k + 1))"; // 字段m:虚拟生成列,基于k+1计算,省略virtual关键字(默认)
    final String expected = "CREATE TABLE IF NOT EXISTS `X` " // 定义期望的标准化输出
        + "(`I` INTEGER NOT NULL," // 字段i标准化输出
        + " `J` INTEGER AS (`I` + 1) STORED," // 字段j标准化输出,GENERATED ALWAYS被简化为AS,保留STORED关键字
        + " `K` INTEGER AS (`J` + 1) VIRTUAL," // 字段k标准化输出,显式包含VIRTUAL关键字
        + " `M` INTEGER AS (`K` + 1) VIRTUAL)"; // 字段m标准化输出,默认的VIRTUAL被显式添加
    sql(sql).ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  @Test void testCreateTableWithUDT() { // 测试方法:测试使用用户自定义类型(UDT)的CREATE TABLE语句解析,验证自定义类型引用语法的正确性
    final String sql = "create table if not exists t (\n" // 定义待测试的SQL语句,创建表t
        + "  f0 MyType0 not null,\n" // 字段f0:使用自定义类型MyType0,非空约束,仅指定类型名(在当前模式中查找)
        + "  f1 db_name.MyType1,\n" // 字段f1:使用模式限定的自定义类型db_name.MyType1
        + "  f2 catalog_name.db_name.MyType2)"; // 字段f2:使用完全限定的自定义类型catalog_name.db_name.MyType2
    final String expected = "CREATE TABLE IF NOT EXISTS `T` (" // 定义期望的标准化输出
        +"`F0` `MYTYPE0` NOT NULL," // 字段f0标准化输出,类型名转为大写并加反引号
        + " `F1` `DB_NAME`.`MYTYPE1`," // 字段f1标准化输出,模式名和类型名都转为大写并加反引号
        + " `F2` `CATALOG_NAME`.`DB_NAME`.`MYTYPE2`)"; // 字段f2标准化输出,三级限定名都转为大写并加反引号
    sql(sql).ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  /** Test case for // 注释说明:这是针对特定JIRA问题的测试用例
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6022">[CALCITE-6022] // JIRA问题链接:CALCITE-6022
   * Support "CREATE TABLE ... LIKE" DDL in server module</a>. */ // 问题描述:在server模块中支持"CREATE TABLE ... LIKE" DDL语句
  @Test void testCreateTableLike() { // 测试方法:测试CREATE TABLE ... LIKE语句解析,验证基于现有表结构创建新表的语法
    final String sql = "create table x like y"; // 定义待测试的SQL语句,创建表x,其结构与表y相同
    final String expected = "CREATE TABLE `X` LIKE `Y`"; // 定义期望的标准化输出,表名转为大写并加反引号
    sql(sql).ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  /** Test case for // 注释说明:这是针对特定JIRA问题的测试用例
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6022">[CALCITE-6022] // JIRA问题链接:CALCITE-6022
   * Support "CREATE TABLE ... LIKE" DDL in server module</a>. */ // 问题描述:在server模块中支持"CREATE TABLE ... LIKE" DDL语句
  @Test void testCreateTableLikeWithOptions() { // 测试方法:测试带选项的CREATE TABLE ... LIKE语句解析,验证INCLUDING/EXCLUDING子句的语法和验证逻辑
    sql("create table x like y including all") // 测试1:INCLUDING ALL子句,包含所有属性
        .ok("CREATE TABLE `X` LIKE `Y`\n" // 期望输出1
            + "INCLUDING ALL"); // INCLUDING ALL子句被保留

    sql("create table s.x like s.y excluding defaults including generated") // 测试2:混合使用EXCLUDING和INCLUDING子句
        .ok("CREATE TABLE `S`.`X` LIKE `S`.`Y`\n" // 期望输出2,模式限定表名
            + "INCLUDING GENERATED\n" // INCLUDING GENERATED子句
            + "EXCLUDING DEFAULTS"); // EXCLUDING DEFAULTS子句

    sql("create table x like y excluding defaults including all") // 测试3:ALL不能与其他选项同时使用,应该失败
        .fails("ALL cannot be used with other options"); // 断言解析失败,期望错误消息

    sql("create table x like y including defaults excluding defaults") // 测试4:不能同时包含和排除同一选项,应该失败
        .fails("Cannot include and exclude option DEFAULTS at same time"); // 断言解析失败,期望错误消息

  }

  @Test void testCreateView() { // 测试方法:测试CREATE OR REPLACE VIEW语句解析,验证创建或替换视图的语法
    final String sql = "create or replace view v as\n" // 定义待测试的SQL语句,创建或替换视图v
        + "select * from (values (1, '2'), (3, '45')) as t (x, y)"; // 视图定义:从VALUES子句选择所有数据,别名为t,列名为x和y
    final String expected = "CREATE OR REPLACE VIEW `V` AS\n" // 定义期望的标准化输出
        + "SELECT *\n" // SELECT子句保持原样
        + "FROM (VALUES (ROW(1, '2')),\n" // FROM子句,VALUES子句的每个值被包装为ROW
        + "(ROW(3, '45'))) AS `T` (`X`, `Y`)"; // 表别名和列别名都被转为大写并加反引号
    sql(sql).ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  @Test void testCreateMaterializedView() { // 测试方法:测试CREATE MATERIALIZED VIEW语句解析,验证创建物化视图的语法,物化视图会物理存储查询结果
    final String sql = "create materialized view mv (d, v) as\n" // 定义待测试的SQL语句,创建物化视图mv,指定列名为d和v
        + "select deptno, count(*) from emp\n" // 查询定义:从emp表选择deptno和计数,按deptno分组,按deptno降序排序
        + "group by deptno order by deptno desc";
    final String expected = "CREATE MATERIALIZED VIEW `MV` (`D`, `V`) AS\n" // 定义期望的标准化输出,视图名和列名转为大写并加反引号
        + "SELECT `DEPTNO`, COUNT(*)\n" // SELECT子句,列名转为大写并加反引号
        + "FROM `EMP`\n" // FROM子句,表名转为大写并加反引号
        + "GROUP BY `DEPTNO`\n" // GROUP BY子句,列名转为大写并加反引号
        + "ORDER BY `DEPTNO` DESC"; // ORDER BY子句,列名转为大写并加反引号,降序保持
    sql(sql).ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  @Test void testCreateMaterializedView2() { // 测试方法:测试CREATE MATERIALIZED VIEW IF NOT EXISTS语句解析,验证条件性创建物化视图的语法
    final String sql = "create materialized view if not exists mv as\n" // 定义待测试的SQL语句,仅在物化视图不存在时创建mv
        + "select deptno, count(*) from emp\n" // 查询定义:从emp表选择deptno和计数,按deptno分组,按deptno降序排序
        + "group by deptno order by deptno desc";
    final String expected = "CREATE MATERIALIZED VIEW IF NOT EXISTS `MV` AS\n" // 定义期望的标准化输出,包含IF NOT EXISTS子句
        + "SELECT `DEPTNO`, COUNT(*)\n" // SELECT子句,列名转为大写并加反引号
        + "FROM `EMP`\n" // FROM子句,表名转为大写并加反引号
        + "GROUP BY `DEPTNO`\n" // GROUP BY子句,列名转为大写并加反引号
        + "ORDER BY `DEPTNO` DESC"; // ORDER BY子句,列名转为大写并加反引号,降序保持
    sql(sql).ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  // "OR REPLACE" is allowed by the parser, but the validator will give an // 注释说明:解析器允许OR REPLACE关键字,但验证器会在后续阶段报错
  // error later // 因为物化视图不支持OR REPLACE语义(物化视图不能简单地替换)
  @Test void testCreateOrReplaceMaterializedView() { // 测试方法:测试CREATE OR REPLACE MATERIALIZED VIEW语句解析,验证解析器接受此语法(尽管语义上可能不支持)
    final String sql = "create or replace materialized view mv as\n" // 定义待测试的SQL语句,尝试创建或替换物化视图mv
        + "select * from emp"; // 查询定义:从emp表选择所有数据
    final String expected = "CREATE MATERIALIZED VIEW `MV` AS\n" // 定义期望的标准化输出,注意OR REPLACE被移除(因为物化视图不支持)
        + "SELECT *\n" // SELECT子句保持原样
        + "FROM `EMP`"; // FROM子句,表名转为大写并加反引号
    sql(sql).ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  @Test void testCreateOrReplaceFunction() { // 测试方法:测试CREATE OR REPLACE FUNCTION语句解析,验证创建或替换用户定义函数(UDF)的语法,支持多个JAR和FILE依赖
    final String sql = "create or replace function if not exists x.udf\n" // 定义待测试的SQL语句,创建或替换函数x.udf,仅在不存在时创建
        + " as 'org.apache.calcite.udf.TableFun.demoUdf'\n" // 指定函数实现类的完全限定名
        + "using jar 'file:/path/udf/udf-0.0.1-SNAPSHOT.jar',\n" // USING子句:指定依赖的JAR文件1
        + " jar 'file:/path/udf/udf2-0.0.1-SNAPSHOT.jar',\n" // 依赖的JAR文件2
        + " file 'file:/path/udf/logback.xml'"; // 依赖的配置文件
    final String expected = "CREATE OR REPLACE FUNCTION" // 定义期望的标准化输出
        + " IF NOT EXISTS `X`.`UDF`" // 函数名转为大写并加反引号,保留IF NOT EXISTS
        + " AS 'org.apache.calcite.udf.TableFun.demoUdf'" // 类名保持原样
        + " USING JAR 'file:/path/udf/udf-0.0.1-SNAPSHOT.jar'," // JAR关键字大写,路径保持原样
        + " JAR 'file:/path/udf/udf2-0.0.1-SNAPSHOT.jar'," // 第二个JAR
        + " FILE 'file:/path/udf/logback.xml'"; // FILE关键字大写,路径保持原样
    sql(sql).ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  @Test void testCreateOrReplaceFunction2() { // 测试方法:测试带引号函数名的CREATE FUNCTION语句解析,验证标识符引用语法的正确性
    final String sql = "create function \"my Udf\"\n" // 定义待测试的SQL语句,创建函数,函数名用双引号包围(保留大小写和空格)
        + " as 'org.apache.calcite.udf.TableFun.demoUdf'"; // 指定函数实现类的完全限定名
    final String expected = "CREATE FUNCTION `my Udf`" // 定义期望的标准化输出,双引号转换为反引号,保留原始大小写和空格
        + " AS 'org.apache.calcite.udf.TableFun.demoUdf'"; // 类名保持原样
    sql(sql).ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  @Test void testDropSchema() { // 测试方法:测试DROP SCHEMA语句解析,验证删除模式的语法
    sql("drop schema x") // 调用sql方法,测试删除模式x
        .ok("DROP SCHEMA `X`"); // 断言解析结果正确,模式名转为大写并加反引号
  }

  @Test void testDropSchemaIfExists() { // 测试方法:测试DROP SCHEMA IF EXISTS语句解析,验证条件性删除模式的语法
    sql("drop schema if exists x") // 调用sql方法,测试仅在模式存在时删除x
        .ok("DROP SCHEMA IF EXISTS `X`"); // 断言解析结果正确,保留IF EXISTS子句
  }

  @Test void testDropForeignSchema() { // 测试方法:测试DROP FOREIGN SCHEMA语句解析,验证删除外部模式的语法
    sql("drop foreign schema x") // 调用sql方法,测试删除外部模式x
        .ok("DROP FOREIGN SCHEMA `X`"); // 断言解析结果正确,模式名转为大写并加反引号
  }

  @Test void testDropType() { // 测试方法:测试DROP TYPE语句解析,验证删除自定义类型的语法
    sql("drop type X") // 调用sql方法,测试删除类型X
        .ok("DROP TYPE `X`"); // 断言解析结果正确,类型名转为大写并加反引号
  }

  @Test void testDropTypeIfExists() { // 测试方法:测试DROP TYPE IF EXISTS语句解析,验证条件性删除自定义类型的语法
    sql("drop type if exists X") // 调用sql方法,测试仅在类型存在时删除X
        .ok("DROP TYPE IF EXISTS `X`"); // 断言解析结果正确,保留IF EXISTS子句
  }

  @Test void testDropTypeTrailingIfExistsFails() { // 测试方法:测试DROP TYPE语句中IF EXISTS位置错误的语法,验证解析器能检测到语法错误
    sql("drop type X ^if^ exists") // 调用sql方法,测试错误的语法:IF EXISTS应该在TYPE之后,而不是在类型名之后(^标记错误位置)
        .fails("(?s)Encountered \"if\" at.*"); // 断言解析失败,期望错误消息包含"Encountered \"if\" at",(?s)表示单行模式,.*匹配任意字符
  }

  @Test void testDropTable() { // 测试方法:测试DROP TABLE语句解析,验证删除表的语法
    sql("drop table x") // 调用sql方法,测试删除表x
        .ok("DROP TABLE `X`"); // 断言解析结果正确,表名转为大写并加反引号
  }

  @Test void testDropTableComposite() { // 测试方法:测试DROP TABLE语句解析,验证删除模式限定表的语法
    sql("drop table x.y") // 调用sql方法,测试删除模式x中的表y
        .ok("DROP TABLE `X`.`Y`"); // 断言解析结果正确,模式名和表名都转为大写并加反引号
  }

  @Test void testDropTableIfExists() { // 测试方法:测试DROP TABLE IF EXISTS语句解析,验证条件性删除表的语法
    sql("drop table if exists x") // 调用sql方法,测试仅在表存在时删除x
        .ok("DROP TABLE IF EXISTS `X`"); // 断言解析结果正确,保留IF EXISTS子句
  }

  @Test void testTruncateTable() { // 测试方法:测试TRUNCATE TABLE语句解析,验证清空表数据的语法,包括IDENTITY选项
    sql("truncate table x") // 测试1:基本的TRUNCATE TABLE语句,省略IDENTITY选项
        .ok("TRUNCATE TABLE `X` CONTINUE IDENTITY"); // 期望输出1:默认添加CONTINUE IDENTITY子句(继续自增值)

    sql("truncate table x continue identity") // 测试2:显式指定CONTINUE IDENTITY选项
        .ok("TRUNCATE TABLE `X` CONTINUE IDENTITY"); // 期望输出2:保持CONTINUE IDENTITY子句

    sql("truncate table x restart identity") // 测试3:指定RESTART IDENTITY选项(重置自增值)
        .ok("TRUNCATE TABLE `X` RESTART IDENTITY"); // 期望输出3:保持RESTART IDENTITY子句
  }

  @Test void testDropView() { // 测试方法:测试DROP VIEW语句解析,验证删除视图的语法
    sql("drop view x") // 调用sql方法,测试删除视图x
        .ok("DROP VIEW `X`"); // 断言解析结果正确,视图名转为大写并加反引号
  }

  @Test void testDropMaterializedView() { // 测试方法:测试DROP MATERIALIZED VIEW语句解析,验证删除物化视图的语法
    sql("drop materialized view x") // 调用sql方法,测试删除物化视图x
        .ok("DROP MATERIALIZED VIEW `X`"); // 断言解析结果正确,视图名转为大写并加反引号
  }

  @Test void testDropMaterializedViewIfExists() { // 测试方法:测试DROP MATERIALIZED VIEW IF EXISTS语句解析,验证条件性删除物化视图的语法
    sql("drop materialized view if exists x") // 调用sql方法,测试仅在物化视图存在时删除x
        .ok("DROP MATERIALIZED VIEW IF EXISTS `X`"); // 断言解析结果正确,保留IF EXISTS子句
  }

  @Test void testDropFunction() { // 测试方法:测试DROP FUNCTION语句解析,验证删除用户定义函数的语法
    final String sql = "drop function x.udf"; // 定义待测试的SQL语句,删除模式x中的函数udf
    final String expected = "DROP FUNCTION `X`.`UDF`"; // 定义期望的标准化输出,模式名和函数名都转为大写并加反引号
    sql(sql).ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

  @Test void testDropFunctionIfExists() { // 测试方法:测试DROP FUNCTION IF EXISTS语句解析,验证条件性删除用户定义函数的语法,支持带引号的函数名
    final String sql = "drop function if exists \"my udf\""; // 定义待测试的SQL语句,仅在函数存在时删除"my udf"(带引号保留大小写和空格)
    final String expected = "DROP FUNCTION IF EXISTS `my udf`"; // 定义期望的标准化输出,双引号转换为反引号,保留原始大小写和空格
    sql(sql).ok(expected); // 执行解析测试并验证输出是否与期望一致
  }

} // 类结束:ServerParserTest类定义结束,该类继承自SqlParserTest,专门用于测试Calcite服务器模块的DDL语句解析功能
