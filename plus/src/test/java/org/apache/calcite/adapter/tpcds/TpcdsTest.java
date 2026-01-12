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
package org.apache.calcite.adapter.tpcds;

import org.apache.calcite.plan.RelTraitDef;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.runtime.Hook;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.test.CalciteAssert;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.Program;
import org.apache.calcite.tools.Programs;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.Bug;
import org.apache.calcite.util.Holder;

import net.hydromatic.tpcds.query.Query;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static org.apache.calcite.test.Matchers.hasTree;

import static org.hamcrest.MatcherAssert.assertThat;

/** Unit test for {@link org.apache.calcite.adapter.tpcds.TpcdsSchema}. // TpcdsSchema的单元测试类，用于测试TPC-DS决策支持基准测试的适配器功能
 *
 * <p>Only runs as part of slow test suite. // 该测试类仅在慢速测试套件中运行，因为TPC-DS查询通常比较复杂且耗时
 */
@Tag("slow") // 使用JUnit 5的@Tag注解标记为慢速测试，可以通过标签过滤执行
class TpcdsTest { // TpcdsTest类：用于测试TPC-DS适配器的各种功能，包括表查询、复杂查询计划验证等
  private static Consumer<Holder<Program>> handler( // handler方法：创建一个程序处理器消费者，用于配置查询优化器的连接顺序策略
      final boolean bushy, // bushy参数：是否使用bushy树（非左深树）连接顺序，bushy树可以生成更多样化的连接计划
      final int minJoinCount) { // minJoinCount参数：最小连接数量阈值，只有当连接数达到此值时才应用启发式连接顺序优化
    return holder -> holder.set( // 返回一个lambda表达式，该表达式会设置Holder中存储的Program对象
        Programs.sequence( // Programs.sequence：创建一个程序序列，按顺序执行多个优化程序
            Programs.heuristicJoinOrder(Programs.RULE_SET, bushy, // Programs.heuristicJoinOrder：创建启发式连接顺序优化程序，使用基于规则的优化器
                minJoinCount), // 传入规则集、是否使用bushy树和最小连接数阈值
            Programs.CALC_PROGRAM)); // Programs.CALC_PROGRAM：Calc程序，用于执行计算相关的优化规则
  }

  private static String schema(String name, String scaleFactor) { // schema方法：生成TPC-DS schema的JSON配置字符串，用于定义数据源
    return "     {\n" // 返回JSON格式的schema配置字符串开始
        + "       type: 'custom',\n" // type字段：指定schema类型为custom（自定义），表示使用自定义的schema工厂
        + "       name: '" + name + "',\n" // name字段：schema的名称，例如"TPCDS"、"TPCDS_01"等
        + "       factory: 'org.apache.calcite.adapter.tpcds.TpcdsSchemaFactory',\n" // factory字段：指定schema工厂类的全限定名，用于创建TpcdsSchema实例
        + "       operand: {\n" // operand字段：传递给schema工厂的参数对象
        + "         columnPrefix: true,\n" // columnPrefix参数：是否为列名添加前缀，true表示启用列名前缀以避免列名冲突
        + "         scale: " + scaleFactor + "\n" // scale参数：数据规模因子，如"1.0"表示1GB数据，"0.01"表示10MB数据，"5.0"表示5GB数据
        + "       }\n" // operand对象结束
        + "     }"; // JSON字符串结束
  }

  public static final String TPCDS_MODEL = "{\n" // TPCDS_MODEL常量：Calcite模型配置的JSON字符串，定义了TPC-DS数据源的连接信息
      + "  version: '1.0',\n" // version字段：模型文件的版本号，使用JSON格式
      + "  defaultSchema: 'TPCDS',\n" // defaultSchema字段：默认使用的schema名称，当SQL中未指定schema时使用此schema
      + "   schemas: [\n" // schemas字段：schema数组，包含所有可用的schema配置
      + schema("TPCDS", "1.0") + ",\n" // 添加名为"TPCDS"的schema，数据规模因子为1.0（约1GB）
      + schema("TPCDS_01", "0.01") + ",\n" // 添加名为"TPCDS_01"的schema，数据规模因子为0.01（约10MB），用于快速测试
      + schema("TPCDS_5", "5.0") + "\n" // 添加名为"TPCDS_5"的schema，数据规模因子为5.0（约5GB），用于大规模测试
      + "   ]\n" // schemas数组结束
      + "}"; // 模型配置JSON字符串结束

  private CalciteAssert.AssertThat with() { // with方法：创建一个CalciteAssert断言构建器，用于配置和执行测试查询
    return CalciteAssert.model(TPCDS_MODEL); // 使用TPCDS_MODEL配置创建断言构建器，该构建器可以用于执行SQL查询并验证结果
  }

  @Test void testCallCenter() { // testCallCenter方法：测试call_center（呼叫中心）表的查询功能，验证数据读取的正确性
    final String[] strings = { // strings数组：存储预期的查询结果，每个元素代表一行数据，格式为分号分隔的键值对
        "CC_CALL_CENTER_SK=1; CC_CALL_CENTER_ID=AAAAAAAABAAAAAAA; CC_REC_START_DATE=1998-01-01;" // 第一条记录：呼叫中心代理键、ID、记录开始日期
            + " CC_REC_END_DATE=null; CC_CLOSED_DATE_SK=null; CC_OPEN_DATE_SK=2450952;" // 记录结束日期、关闭日期代理键、开放日期代理键
            + " CC_NAME=NY Metro; CC_CLASS=large; CC_EMPLOYEES=2; CC_SQ_FT=1138;" // 呼叫中心名称、类别（large/medium/small）、员工数、面积（平方英尺）
            + " CC_HOURS=8AM-4PM             ; CC_MANAGER=Bob Belcher; CC_MKT_ID=6;" // 营业时间、经理姓名、市场ID
            + " CC_MKT_CLASS=More than other authori                           ;" // 市场类别描述
            + " CC_MKT_DESC=Shared others could not count fully dollars. New members ca;" // 市场描述文本
            + " CC_MARKET_MANAGER=Julius Tran; CC_DIVISION=3; CC_DIVISION_NAME=pri;" // 市场经理、部门ID、部门名称
            + " CC_COMPANY=6; CC_COMPANY_NAME=cally                                             ;" // 公司ID、公司名称
            + " CC_STREET_NUMBER=730       ; CC_STREET_NAME=Ash Hill;" // 街道号码、街道名称
            + " CC_STREET_TYPE=Boulevard      ; CC_SUITE_NUMBER=Suite 0   ; CC_CITY=Midway;" // 街道类型、套间号、城市
            + " CC_COUNTY=Williamson County; CC_STATE=TN; CC_ZIP=31904     ;" // 县、州、邮编
            + " CC_COUNTRY=United States; CC_GMT_OFFSET=-5; CC_TAX_PERCENTAGE=0.11", // 国家、GMT时区偏移、税率
        "CC_CALL_CENTER_SK=2; CC_CALL_CENTER_ID=AAAAAAAACAAAAAAA; CC_REC_START_DATE=1998-01-01;" // 第二条记录开始
            + " CC_REC_END_DATE=2000-12-31; CC_CLOSED_DATE_SK=null; CC_OPEN_DATE_SK=2450806;" // 记录结束日期为2000-12-31
            + " CC_NAME=Mid Atlantic; CC_CLASS=medium; CC_EMPLOYEES=6; CC_SQ_FT=2268;" // 中型呼叫中心，6名员工
            + " CC_HOURS=8AM-8AM             ; CC_MANAGER=Felipe Perkins; CC_MKT_ID=2;" // 24小时营业
            + " CC_MKT_CLASS=A bit narrow forms matter animals. Consist        ;" // 市场类别
            + " CC_MKT_DESC=Largely blank years put substantially deaf, new others. Question;" // 市场描述
            + " CC_MARKET_MANAGER=Julius Durham; CC_DIVISION=5; CC_DIVISION_NAME=anti;" // 市场经理信息
            + " CC_COMPANY=1; CC_COMPANY_NAME=ought                                             ;" // 公司信息
            + " CC_STREET_NUMBER=984       ; CC_STREET_NAME=Center Hill;" // 地址信息
            + " CC_STREET_TYPE=Way            ; CC_SUITE_NUMBER=Suite 70  ; CC_CITY=Midway;" // 地址续
            + " CC_COUNTY=Williamson County; CC_STATE=TN; CC_ZIP=31904     ;" // 地址续
            + " CC_COUNTRY=United States; CC_GMT_OFFSET=-5; CC_TAX_PERCENTAGE=0.12", // 税率为0.12
        "CC_CALL_CENTER_SK=3; CC_CALL_CENTER_ID=AAAAAAAACAAAAAAA; CC_REC_START_DATE=2001-01-01;" // 第三条记录：同一ID的更新记录
            + " CC_REC_END_DATE=null; CC_CLOSED_DATE_SK=null; CC_OPEN_DATE_SK=2450806;" // 当前有效记录（结束日期为null）
            + " CC_NAME=Mid Atlantic; CC_CLASS=medium; CC_EMPLOYEES=6; CC_SQ_FT=4134;" // 面积增加到4134平方英尺
            + " CC_HOURS=8AM-4PM             ; CC_MANAGER=Mark Hightower; CC_MKT_ID=2;" // 经理变更为Mark Hightower
            + " CC_MKT_CLASS=Wrong troops shall work sometimes in a opti       ;" // 市场类别更新
            + " CC_MKT_DESC=Largely blank years put substantially deaf, new others. Question;" // 市场描述
            + " CC_MARKET_MANAGER=Julius Durham; CC_DIVISION=1; CC_DIVISION_NAME=ought;" // 部门变更
            + " CC_COMPANY=2; CC_COMPANY_NAME=able                                              ;" // 公司变更
            + " CC_STREET_NUMBER=984       ; CC_STREET_NAME=Center Hill;" // 地址保持不变
            + " CC_STREET_TYPE=Way            ; CC_SUITE_NUMBER=Suite 70  ; CC_CITY=Midway;" // 地址续
            + " CC_COUNTY=Williamson County; CC_STATE=TN; CC_ZIP=31904     ;" // 地址续
            + " CC_COUNTRY=United States; CC_GMT_OFFSET=-5; CC_TAX_PERCENTAGE=0.01", // 税率降至0.01
        "CC_CALL_CENTER_SK=4; CC_CALL_CENTER_ID=AAAAAAAAEAAAAAAA; CC_REC_START_DATE=1998-01-01;" // 第四条记录
            + " CC_REC_END_DATE=2000-01-01; CC_CLOSED_DATE_SK=null; CC_OPEN_DATE_SK=2451063;" // 2000年1月1日结束
            + " CC_NAME=North Midwest; CC_CLASS=medium; CC_EMPLOYEES=1; CC_SQ_FT=649;" // 中型但只有1名员工
            + " CC_HOURS=8AM-4PM             ; CC_MANAGER=Larry Mccray; CC_MKT_ID=2;" // 经理Larry Mccray
            + " CC_MKT_CLASS=Dealers make most historical, direct students     ;" // 市场类别
            + " CC_MKT_DESC=Rich groups catch longer other fears; future,;" // 市场描述
            + " CC_MARKET_MANAGER=Matthew Clifton; CC_DIVISION=4; CC_DIVISION_NAME=ese;" // 市场经理
            + " CC_COMPANY=3; CC_COMPANY_NAME=pri                                               ;" // 公司信息
            + " CC_STREET_NUMBER=463       ; CC_STREET_NAME=Pine Ridge;" // 地址信息
            + " CC_STREET_TYPE=RD             ; CC_SUITE_NUMBER=Suite U   ; CC_CITY=Midway;" // 地址续
            + " CC_COUNTY=Williamson County; CC_STATE=TN; CC_ZIP=31904     ;" // 地址续
            + " CC_COUNTRY=United States; CC_GMT_OFFSET=-5; CC_TAX_PERCENTAGE=0.05", // 税率0.05
        "CC_CALL_CENTER_SK=5; CC_CALL_CENTER_ID=AAAAAAAAEAAAAAAA; CC_REC_START_DATE=2000-01-02;" // 第五条记录：同一ID的更新记录
            + " CC_REC_END_DATE=2001-12-31; CC_CLOSED_DATE_SK=null; CC_OPEN_DATE_SK=2451063;" // 2001年12月31日结束
            + " CC_NAME=North Midwest; CC_CLASS=small; CC_EMPLOYEES=3; CC_SQ_FT=795;" // 变更为小型，3名员工
            + " CC_HOURS=8AM-8AM             ; CC_MANAGER=Larry Mccray; CC_MKT_ID=2;" // 24小时营业
            + " CC_MKT_CLASS=Dealers make most historical, direct students     ;" // 市场类别
            + " CC_MKT_DESC=Blue, due beds come. Politicians would not make far thoughts. " // 市场描述
            + "Specifically new horses partic;" // 市场描述续
            + " CC_MARKET_MANAGER=Gary Colburn; CC_DIVISION=4; CC_DIVISION_NAME=ese;" // 市场经理变更
            + " CC_COMPANY=3; CC_COMPANY_NAME=pri                                               ;" // 公司保持不变
            + " CC_STREET_NUMBER=463       ; CC_STREET_NAME=Pine Ridge;" // 地址保持不变
            + " CC_STREET_TYPE=RD             ; CC_SUITE_NUMBER=Suite U   ; CC_CITY=Midway;" // 地址续
            + " CC_COUNTY=Williamson County; CC_STATE=TN; CC_ZIP=31904     ;" // 地址续
            + " CC_COUNTRY=United States; CC_GMT_OFFSET=-5; CC_TAX_PERCENTAGE=0.12", // 税率升至0.12
        "CC_CALL_CENTER_SK=6; CC_CALL_CENTER_ID=AAAAAAAAEAAAAAAA; CC_REC_START_DATE=2002-01-01;" // 第六条记录：同一ID的最新记录
            + " CC_REC_END_DATE=null; CC_CLOSED_DATE_SK=null; CC_OPEN_DATE_SK=2451063;" // 当前有效记录
            + " CC_NAME=North Midwest; CC_CLASS=medium; CC_EMPLOYEES=7; CC_SQ_FT=3514;" // 变更为中型，7名员工，面积3514
            + " CC_HOURS=8AM-4PM             ; CC_MANAGER=Larry Mccray; CC_MKT_ID=5;" // 经理不变，市场ID变更为5
            + " CC_MKT_CLASS=Silly particles could pro                         ;" // 市场类别更新
            + " CC_MKT_DESC=Blue, due beds come. Politicians would not make far thoughts. " // 市场描述
            + "Specifically new horses partic;" // 市场描述续
            + " CC_MARKET_MANAGER=Gary Colburn; CC_DIVISION=5; CC_DIVISION_NAME=anti;" // 部门变更为5，名称为anti
            + " CC_COMPANY=3; CC_COMPANY_NAME=pri                                               ;" // 公司保持不变
            + " CC_STREET_NUMBER=463       ; CC_STREET_NAME=Pine Ridge;" // 地址保持不变
            + " CC_STREET_TYPE=RD             ; CC_SUITE_NUMBER=Suite U   ; CC_CITY=Midway;" // 地址续
            + " CC_COUNTY=Williamson County; CC_STATE=TN; CC_ZIP=31904     ;" // 地址续
            + " CC_COUNTRY=United States; CC_GMT_OFFSET=-5; CC_TAX_PERCENTAGE=0.11"}; // 税率0.11
    with().query("select * from tpcds.call_center").returnsUnordered(strings); // 执行查询并验证结果：查询tpcds schema的call_center表所有数据，验证返回结果与预期字符串数组匹配（不关心顺序）
  }

  @Disabled("it's wasting time to count each table") // @Disabled注解：禁用此测试，因为统计每个表的行数非常耗时
  @Test void testTableCount() { // testTableCount方法：测试TPC-DS数据库中所有表的行数是否正确
    final CalciteAssert.AssertThat with = with(); // 创建CalciteAssert断言构建器，用于执行测试查询
    foo(with, "CALL_CENTER", 6); // 验证CALL_CENTER表有6行数据（呼叫中心表）
    foo(with, "CATALOG_PAGE", 11_718); // 验证CATALOG_PAGE表有11,718行数据（目录页表）
    foo(with, "CATALOG_RETURNS", 144_067); // 验证CATALOG_RETURNS表有144,067行数据（目录退货表）
    foo(with, "CATALOG_SALES", 1_441_548); // 验证CATALOG_SALES表有1,441,548行数据（目录销售表）
    foo(with, "CUSTOMER", 100_000); // 验证CUSTOMER表有100,000行数据（客户表，TPC-DS标准规模）
    foo(with, "CUSTOMER_ADDRESS", 50_000); // 验证CUSTOMER_ADDRESS表有50,000行数据（客户地址表）
    foo(with, "CUSTOMER_DEMOGRAPHICS", 1_920_800); // 验证CUSTOMER_DEMOGRAPHICS表有1,920,800行数据（客户人口统计表）
    foo(with, "DATE_DIM", 73_049); // 验证DATE_DIM表有73,049行数据（日期维度表，覆盖约200年）
    foo(with, "HOUSEHOLD_DEMOGRAPHICS", 7_200); // 验证HOUSEHOLD_DEMOGRAPHICS表有7,200行数据（家庭人口统计表）
    foo(with, "INCOME_BAND", 20); // 验证INCOME_BAND表有20行数据（收入带表，定义收入区间）
    foo(with, "INVENTORY", 11_745_000); // 验证INVENTORY表有11,745,000行数据（库存表）
    foo(with, "ITEM", 18_000); // 验证ITEM表有18,000行数据（商品表）
    foo(with, "PROMOTION", 300); // 验证PROMOTION表有300行数据（促销表）
    foo(with, "REASON", 35); // 验证REASON表有35行数据（退货原因表）
    foo(with, "SHIP_MODE", 20); // 验证SHIP_MODE表有20行数据（运输模式表）
    foo(with, "STORE", 12); // 验证STORE表有12行数据（门店表）
    foo(with, "STORE_RETURNS", 287_514); // 验证STORE_RETURNS表有287,514行数据（门店退货表）
    foo(with, "STORE_SALES", 2_880_404); // 验证STORE_SALES表有2,880,404行数据（门店销售表）
    foo(with, "TIME_DIM", 86_400); // 验证TIME_DIM表有86,400行数据（时间维度表，覆盖24小时）
    foo(with, "WAREHOUSE", 5); // 验证WAREHOUSE表有5行数据（仓库表）
    foo(with, "WEB_PAGE", 60); // 验证WEB_PAGE表有60行数据（网页表）
    foo(with, "WEB_RETURNS", 71_763); // 验证WEB_RETURNS表有71,763行数据（网页退货表）
    foo(with, "WEB_SALES", 719_384); // 验证WEB_SALES表有719,384行数据（网页销售表）
    foo(with, "WEB_SITE", 30); // 验证WEB_SITE表有30行数据（网站表）
    foo(with, "DBGEN_VERSION", 1); // 验证DBGEN_VERSION表有1行数据（数据库生成版本表）
  }

  protected void foo(CalciteAssert.AssertThat with, String tableName, // foo方法：辅助方法，用于验证指定表的行数是否符合预期
      int expectedCount) { // expectedCount参数：预期的表行数
    final String sql = "select * from tpcds." + tableName; // 构建SQL查询语句，查询tpcds schema中指定表的所有数据
    with.query(sql).returnsCount(expectedCount); // 执行查询并验证返回的行数是否等于预期值
  }

  /** Tests the customer table with scale factor 5. */ // 注释：测试规模因子为5的customer表
  @Disabled("add tests like this that count each table") // @Disabled注解：禁用此测试，建议添加类似的统计测试
  @Test void testCustomer5() { // testCustomer5方法：测试TPCDS_5 schema（规模因子5.0）中customer表的行数
    with() // 获取断言构建器
        .query("select * from tpcds_5.customer") // 执行查询：从tpcds_5 schema的customer表查询所有数据
        .returnsCount(750000); // 验证返回的行数为750,000（规模因子5.0对应的标准行数）
  }

  @Disabled("throws 'RuntimeException: Cannot convert null to long'") // @Disabled注解：禁用此测试，因为会抛出运行时异常（无法将null转换为long类型）
  @Test void testQuery01() { // testQuery01方法：测试TPC-DS查询1（Query 1）
    checkQuery(1).runs(); // 调用checkQuery方法检查查询1，并验证查询能够成功运行
  }

  @Test void testQuery17Plan() { // testQuery17Plan方法：测试TPC-DS查询17的执行计划，验证优化器生成的计划是否符合预期
    //noinspection unchecked // 抑制未检查类型转换警告，因为traitDefs可能涉及泛型类型转换
    checkQuery(17) // 调用checkQuery方法检查查询17
        .withHook(Hook.PROGRAM, handler(true, 2)) // 设置PROGRAM钩子，使用自定义的处理器：启用bushy树连接，最小连接数为2
        .explainMatches("including all attributes ", // 验证执行计划包含"including all attributes"字符串
            CalciteAssert.checkMaskedResultContains("" // 检查执行计划是否包含以下内容（使用掩码结果检查）
                + "EnumerableCalc(expr#0..9=[{inputs}], expr#10=[/($t4, $t3)], expr#11=[CAST($t10):INTEGER NOT NULL], expr#12=[*($t4, $t4)], expr#13=[/($t12, $t3)], expr#14=[-($t5, $t13)], expr#15=[1], expr#16=[=($t3, $t15)], expr#17=[null:BIGINT], expr#18=[-($t3, $t15)], expr#19=[CASE($t16, $t17, $t18)], expr#20=[/($t14, $t19)], expr#21=[0.5:DECIMAL(2, 1)], expr#22=[POWER($t20, $t21)], expr#23=[CAST($t22):INTEGER NOT NULL], expr#24=[/($t23, $t11)], expr#25=[/($t6, $t3)], expr#26=[CAST($t25):INTEGER NOT NULL], expr#27=[*($t6, $t6)], expr#28=[/($t27, $t3)], expr#29=[-($t7, $t28)], expr#30=[/($t29, $t19)], expr#31=[POWER($t30, $t21)], expr#32=[CAST($t31):INTEGER NOT NULL], expr#33=[/($t32, $t26)], expr#34=[/($t8, $t3)], expr#35=[CAST($t34):INTEGER NOT NULL], expr#36=[*($t8, $t8)], expr#37=[/($t36, $t3)], expr#38=[-($t9, $t37)], expr#39=[/($t38, $t19)], expr#40=[POWER($t39, $t21)], expr#41=[CAST($t40):INTEGER NOT NULL], expr#42=[/($t41, $t35)], proj#0..3=[{exprs}], STORE_SALES_QUANTITYAVE=[$t11], STORE_SALES_QUANTITYSTDEV=[$t23], STORE_SALES_QUANTITYCOV=[$t24], AS_STORE_RETURNS_QUANTITYCOUNT=[$t3], AS_STORE_RETURNS_QUANTITYAVE=[$t26], AS_STORE_RETURNS_QUANTITYSTDEV=[$t32], STORE_RETURNS_QUANTITYCOV=[$t33], CATALOG_SALES_QUANTITYCOUNT=[$t3], CATALOG_SALES_QUANTITYAVE=[$t35], CATALOG_SALES_QUANTITYSTDEV=[$t42], CATALOG_SALES_QUANTITYCOV=[$t42]): rowcount = 100.0, cumulative cost = {1.2435775409784036E28 rows, 2.95671738161514E30 cpu, 0.0 io}\n" // EnumerableCalc节点：可枚举计算节点，执行复杂的统计计算，包括平均值、标准差、变异系数等，行数为100，累积成本极高
                + "  EnumerableLimit(fetch=[100]): rowcount = 100.0, cumulative cost = {1.2435775409784036E28 rows, 2.95671738161514E30 cpu, 0.0 io}\n" // EnumerableLimit节点：限制结果集为100行，用于分页或限制返回结果数量
                + "    EnumerableSort(sort0=[$0], sort1=[$1], sort2=[$2], dir0=[ASC], dir1=[ASC], dir2=[ASC]): rowcount = 5.434029018852197E26, cumulative cost = {1.2435775409784036E28 rows, 2.95671738161514E30 cpu, 0.0 io}\n" // EnumerableSort节点：对结果进行排序，按前三个字段升序排序，行数巨大
                + "      EnumerableAggregate(group=[{0, 1, 2}], STORE_SALES_QUANTITYCOUNT=[COUNT()], agg#1=[$SUM0($3)], agg#2=[$SUM0($6)], agg#3=[$SUM0($4)], agg#4=[$SUM0($7)], agg#5=[$SUM0($5)], agg#6=[$SUM0($8)]): rowcount = 5.434029018852197E26, cumulative cost = {1.1892372507898816E28 rows, 1.2172225002228922E30 cpu, 0.0 io}\n" // EnumerableAggregate节点：聚合操作，按前三个字段分组，计算COUNT和多个SUM0聚合函数
                + "        EnumerableCalc(expr#0..211=[{inputs}], expr#212=[*($t89, $t89)], expr#213=[*($t140, $t140)], expr#214=[*($t196, $t196)], I_ITEM_ID=[$t58], I_ITEM_DESC=[$t61], S_STATE=[$t24], SS_QUANTITY=[$t89], SR_RETURN_QUANTITY=[$t140], CS_QUANTITY=[$t196], $f6=[$t212], $f7=[$t213], $f8=[$t214]): rowcount = 5.434029018852197E27, cumulative cost = {1.0873492066864028E28 rows, 1.2172225002228922E30 cpu, 0.0 io}\n" // EnumerableCalc节点：计算表达式，提取所需字段并计算平方值，用于后续统计计算
                + "          EnumerableHashJoin(condition=[AND(=($82, $133), =($81, $132), =($88, $139))], joinType=[inner]): rowcount = 5.434029018852197E27, cumulative cost = {5.439463048011832E27 rows, 1.7776306E7 cpu, 0.0 io}\n" // EnumerableHashJoin节点：哈希连接，使用三个等值条件连接，内连接，行数巨大
                + "            EnumerableHashJoin(condition=[=($0, $86)], joinType=[inner]): rowcount = 2.3008402586892598E13, cumulative cost = {4.8588854672854766E13 rows, 7281360.0 cpu, 0.0 io}\n" // EnumerableHashJoin节点：哈希连接，使用单个等值条件，连接STORE表和其他表
                + "              EnumerableTableScan(table=[[TPCDS, STORE]]): rowcount = 12.0, cumulative cost = {12.0 rows, 13.0 cpu, 0.0 io}\n" // EnumerableTableScan节点：表扫描，扫描STORE表，该表只有12行数据
                + "              EnumerableHashJoin(condition=[=($0, $50)], joinType=[inner]): rowcount = 1.2782445881607E13, cumulative cost = {1.279800620431234E13 rows, 7281347.0 cpu, 0.0 io}\n" // EnumerableHashJoin节点：哈希连接，连接DATE_DIM和ITEM表
                + "                EnumerableCalc(expr#0..27=[{inputs}], expr#28=['1998Q1'], expr#29=[=($t15, $t28)], proj#0..27=[{exprs}], $condition=[$t29]): rowcount = 10957.35, cumulative cost = {84006.35 rows, 4382941.0 cpu, 0.0 io}\n" // EnumerableCalc节点：过滤DATE_DIM表，只保留1998年第一季度的数据
                + "                  EnumerableTableScan(table=[[TPCDS, DATE_DIM]]): rowcount = 73049.0, cumulative cost = {73049.0 rows, 73050.0 cpu, 0.0 io}\n" // EnumerableTableScan节点：表扫描，扫描DATE_DIM表，该表有73,049行数据
                + "                EnumerableHashJoin(condition=[=($0, $24)], joinType=[inner]): rowcount = 7.7770908E9, cumulative cost = {7.783045975286664E9 rows, 2898406.0 cpu, 0.0 io}\n" // EnumerableHashJoin节点：哈希连接，连接ITEM和STORE_SALES表
                + "                  EnumerableTableScan(table=[[TPCDS, ITEM]]): rowcount = 18000.0, cumulative cost = {18000.0 rows, 18001.0 cpu, 0.0 io}\n" // EnumerableTableScan节点：表扫描，扫描ITEM表，该表有18,000行数据
                + "                  EnumerableTableScan(table=[[TPCDS, STORE_SALES]]): rowcount = 2880404.0, cumulative cost = {2880404.0 rows, 2880405.0 cpu, 0.0 io}\n" // EnumerableTableScan节点：表扫描，扫描STORE_SALES表，该表有2,880,404行数据
                + "            EnumerableHashJoin(condition=[AND(=($31, $79), =($30, $91))], joinType=[inner]): rowcount = 6.9978029381741304E16, cumulative cost = {7.0048032234040472E16 rows, 1.0494946E7 cpu, 0.0 io}\n" // EnumerableHashJoin节点：哈希连接，使用两个等值条件，连接STORE_RETURNS和CATALOG_SALES表
                + "              EnumerableHashJoin(condition=[=($0, $28)], joinType=[inner]): rowcount = 7.87597881975E8, cumulative cost = {7.884434212216867E8 rows, 4670456.0 cpu, 0.0 io}\n" // EnumerableHashJoin节点：哈希连接，连接DATE_DIM和STORE_RETURNS表
                + "                EnumerableCalc(expr#0..27=[{inputs}], expr#28=[Sarg['1998Q1', '1998Q2', '1998Q3']:CHAR(6)], expr#29=[SEARCH($t15, $t28)], proj#0..27=[{exprs}], $condition=[$t29]): rowcount = 18262.25, cumulative cost = {91311.25 rows, 4382941.0 cpu, 0.0 io}\n" // EnumerableCalc节点：过滤DATE_DIM表，只保留1998年前三季度的数据（使用Sarg搜索参数）
                + "                  EnumerableTableScan(table=[[TPCDS, DATE_DIM]]): rowcount = 73049.0, cumulative cost = {73049.0 rows, 73050.0 cpu, 0.0 io}\n" // EnumerableTableScan节点：表扫描，再次扫描DATE_DIM表
                + "                EnumerableTableScan(table=[[TPCDS, STORE_RETURNS]]): rowcount = 287514.0, cumulative cost = {287514.0 rows, 287515.0 cpu, 0.0 io}\n" // EnumerableTableScan节点：表扫描，扫描STORE_RETURNS表，该表有287,514行数据
                + "              EnumerableHashJoin(condition=[=($0, $28)], joinType=[inner]): rowcount = 3.94888649445E9, cumulative cost = {3.9520401026966867E9 rows, 5824490.0 cpu, 0.0 io}\n" // EnumerableHashJoin节点：哈希连接，连接DATE_DIM和CATALOG_SALES表
                + "                EnumerableCalc(expr#0..27=[{inputs}], expr#28=[Sarg['1998Q1', '1998Q2', '1998Q3']:CHAR(6)], expr#29=[SEARCH($t15, $t28)], proj#0..27=[{exprs}], $condition=[$t29]): rowcount = 18262.25, cumulative cost = {91311.25 rows, 4382941.0 cpu, 0.0 io}\n" // EnumerableCalc节点：过滤DATE_DIM表，只保留1998年前三季度的数据
                + "                  EnumerableTableScan(table=[[TPCDS, DATE_DIM]]): rowcount = 73049.0, cumulative cost = {73049.0 rows, 73050.0 cpu, 0.0 io}\n" // EnumerableTableScan节点：表扫描，再次扫描DATE_DIM表
                + "                EnumerableTableScan(table=[[TPCDS, CATALOG_SALES]]): rowcount = 1441548.0, cumulative cost = {1441548.0 rows, 1441549.0 cpu, 0.0 io}\n")); // EnumerableTableScan节点：表扫描，扫描CATALOG_SALES表，该表有1,441,548行数据
  }

  @Disabled("throws 'RuntimeException: Cannot convert null to long'") // @Disabled注解：禁用此测试，因为会抛出运行时异常（无法将null转换为long类型）
  @Test void testQuery27() { // testQuery27方法：测试TPC-DS查询27（Query 27）
    checkQuery(27).runs(); // 调用checkQuery方法检查查询27，并验证查询能够成功运行
  }

  @Disabled("throws 'RuntimeException: Cannot convert null to long'") // @Disabled注解：禁用此测试，因为会抛出运行时异常（无法将null转换为long类型）
  @Test void testQuery58() { // testQuery58方法：测试TPC-DS查询58（Query 58）
    checkQuery(58).explainContains("PLAN").runs(); // 调用checkQuery方法检查查询58，验证执行计划包含"PLAN"字符串，并验证查询能够成功运行
  }

  @Disabled("takes too long to optimize") // @Disabled注解：禁用此测试，因为优化过程耗时太长
  @Test void testQuery72() { // testQuery72方法：测试TPC-DS查询72（Query 72）
    checkQuery(72).runs(); // 调用checkQuery方法检查查询72，并验证查询能够成功运行
  }

  @Disabled("work in progress") // @Disabled注解：禁用此测试，因为正在开发中
  @Test void testQuery72Plan() { // testQuery72Plan方法：测试TPC-DS查询72的执行计划
    checkQuery(72) // 调用checkQuery方法检查查询72
        .withHook(Hook.PROGRAM, handler(true, 2)) // 设置PROGRAM钩子，使用自定义的处理器：启用bushy树连接，最小连接数为2
        .planContains("xx"); // 验证执行计划包含"xx"字符串（这是一个占位符，实际测试中应该替换为具体的计划特征）
  }

  @Disabled("throws 'java.lang.AssertionError: type mismatch'") // @Disabled注解：禁用此测试，因为会抛出断言错误（类型不匹配）
  @Test void testQuery95() { // testQuery95方法：测试TPC-DS查询95（Query 95）
    checkQuery(95) // 调用checkQuery方法检查查询95
        .withHook(Hook.PROGRAM, handler(false, 6)) // 设置PROGRAM钩子，使用自定义的处理器：禁用bushy树连接（false），最小连接数为6
        .runs(); // 验证查询能够成功运行
  }

  private CalciteAssert.AssertQuery checkQuery(int i) { // checkQuery方法：检查指定的TPC-DS查询，返回一个断言查询对象用于验证
    final Query query = Query.of(i); // 使用Query.of方法创建指定编号的TPC-DS查询对象
    String sql = query.sql(new Random(0)); // 使用固定的随机种子（0）生成SQL查询字符串，确保每次生成的SQL一致
    switch (i) { // 根据查询编号进行特殊处理
    case 58: // 查询58的特殊处理
      if (Bug.upgrade("new TPC-DS generator")) { // 如果升级到新的TPC-DS生成器
        // Work around bug: Support '<DATE>  = <character literal>'. // 变通方法：支持日期等于字符字面量的语法
        sql = sql.replace(" = '", " = DATE '"); // 将" = '"替换为" = DATE '"，使用DATE字面量语法
      } else { // 如果使用旧的TPC-DS生成器
        // Until TPC-DS generator can handle date(...). // 直到TPC-DS生成器能够处理date(...)函数
        sql =
            sql.replace("'date([YEAR]+\"-01-01\",[YEAR]+\"-07-24\",sales)'", // 替换特定的日期函数调用
                "DATE '1998-08-18'"); // 替换为固定的日期字面量
      }
      break;
    case 72: // 查询72的特殊处理
      // Work around CALCITE-304: Support '<DATE> + <INTEGER>'. // 变通方法：支持日期加整数的语法（CALCITE-304问题）
      sql = sql.replace("+ 5", "+ interval '5' day"); // 将"+ 5"替换为"+ interval '5' day"，使用interval语法
      break;
    case 95: // 查询95的特殊处理
      sql = sql.replace("60 days", "interval '60' day"); // 将"60 days"替换为"interval '60' day"，使用interval语法
      sql = sql.replace("d_date between '", "d_date between date '"); // 将日期比较语法改为DATE字面量
      break;
    }
    return with() // 获取断言构建器
        .query(sql.replace("tpcds.", "tpcds_01.")); // 执行查询：将SQL中的"tpcds."替换为"tpcds_01."，使用小规模数据集进行测试
  }

  public Frameworks.ConfigBuilder config() throws Exception { // config方法：创建Calcite框架配置构建器，用于配置RelBuilder和相关参数
    final Holder<@Nullable SchemaPlus> root = Holder.empty(); // 创建一个Holder对象，用于存储SchemaPlus对象（可为null）
    CalciteAssert.model(TPCDS_MODEL) // 使用TPCDS_MODEL创建CalciteAssert
        .doWithConnection(connection -> { // 获取数据库连接并执行操作
          root.set(connection.getRootSchema().subSchemas().get("TPCDS")); // 从连接的根schema中获取"TPCDS"子schema并存储到Holder中
        }); // 连接操作结束
    return Frameworks.newConfigBuilder() // 创建新的框架配置构建器
        .parserConfig(SqlParser.Config.DEFAULT) // 设置SQL解析器配置为默认配置
        .defaultSchema(root.get()) // 设置默认schema为从Holder中获取的TPCDS schema
        .traitDefs((List<RelTraitDef>) null) // 设置trait定义列表为null，使用默认的trait定义
        .programs(Programs.heuristicJoinOrder(Programs.RULE_SET, true, 2)); // 设置优化程序为启发式连接顺序优化程序，使用规则集，启用bushy树，最小连接数为2
  }

  /**
   * Builder query 27 using {@link RelBuilder}. // 使用RelBuilder构建查询27，演示如何使用RelBuilder API构建复杂的TPC-DS查询
   *
   * <blockquote><pre> // SQL查询的原始格式，用于说明RelBuilder构建的查询对应的SQL语句
   * select  i_item_id, // 查询字段：商品ID
   *         s_state, grouping(s_state) g_state, // 查询字段：州和分组标识（grouping函数标识是否为汇总行）
   *         avg(ss_quantity) agg1, // 聚合字段：平均销售数量
   *         avg(ss_list_price) agg2, // 聚合字段：平均标价
   *         avg(ss_coupon_amt) agg3, // 聚合字段：平均优惠券金额
   *         avg(ss_sales_price) agg4 // 聚合字段：平均销售价格
   * from store_sales, customer_demographics, date_dim, store, item // 涉及的表：门店销售、客户人口统计、日期维度、门店、商品
   * where ss_sold_date_sk = d_date_sk and // 连接条件：销售日期代理键等于日期代理键
   *        ss_item_sk = i_item_sk and // 连接条件：销售商品代理键等于商品代理键
   *        ss_store_sk = s_store_sk and // 连接条件：销售门店代理键等于门店代理键
   *        ss_cdemo_sk = cd_demo_sk and // 连接条件：销售客户人口统计代理键等于客户人口统计代理键
   *        cd_gender = 'dist(gender, 1, 1)' and // 过滤条件：客户性别（测试中使用'M'）
   *        cd_marital_status = 'dist(marital_status, 1, 1)' and // 过滤条件：客户婚姻状况（测试中使用'S'）
   *        cd_education_status = 'dist(education, 1, 1)' and // 过滤条件：客户教育状况（测试中使用'HIGH SCHOOL'）
   *        d_year = 1998 and // 过滤条件：年份为1998
   *        s_state in ('distmember(fips_county,[STATENUMBER.1], 3)', // 过滤条件：州在指定的6个州中（测试中使用CA, OR, WA, TX, OK, MD）
   *              'distmember(fips_county,[STATENUMBER.2], 3)',
   *              'distmember(fips_county,[STATENUMBER.3], 3)',
   *              'distmember(fips_county,[STATENUMBER.4], 3)',
   *              'distmember(fips_county,[STATENUMBER.5], 3)',
   *              'distmember(fips_county,[STATENUMBER.6], 3)')
   * group by rollup (i_item_id, s_state) // 分组：使用ROLLUP按商品ID和州分组，生成汇总行
   * order by i_item_id // 排序：按商品ID升序
   *          ,s_state // 排序：按州升序
   * LIMIT 100 // 限制：只返回前100行结果
   * </pre></blockquote>
   */
  @Test void testQuery27Builder() throws Exception { // testQuery27Builder方法：使用RelBuilder API构建TPC-DS查询27，并验证生成的逻辑计划
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例，使用config()方法返回的配置
    final RelNode root = // 创建关系节点（RelNode），表示完整的查询逻辑计划
        builder.scan("STORE_SALES") // 扫描STORE_SALES表，将其添加到RelBuilder的输入集合中
            .scan("CUSTOMER_DEMOGRAPHICS") // 扫描CUSTOMER_DEMOGRAPHICS表，添加到输入集合
            .scan("DATE_DIM") // 扫描DATE_DIM表，添加到输入集合
            .scan("STORE") // 扫描STORE表，添加到输入集合
            .scan("ITEM") // 扫描ITEM表，添加到输入集合（现在有5个表在输入集合中）
            .join(JoinRelType.INNER) // 执行第一个内连接：连接ITEM和STORE表（笛卡尔积，因为未指定连接条件）
            .join(JoinRelType.INNER) // 执行第二个内连接：连接上一步结果和DATE_DIM表（笛卡尔积）
            .join(JoinRelType.INNER) // 执行第三个内连接：连接上一步结果和CUSTOMER_DEMOGRAPHICS表（笛卡尔积）
            .join(JoinRelType.INNER) // 执行第四个内连接：连接上一步结果和STORE_SALES表（笛卡尔积）
            .filter( // 添加过滤条件，用于实际的连接和筛选
                builder.equals(builder.field("SS_SOLD_DATE_SK"), builder.field("D_DATE_SK")), // 连接条件：销售日期代理键等于日期代理键
                builder.equals(builder.field("SS_ITEM_SK"), builder.field("I_ITEM_SK")), // 连接条件：销售商品代理键等于商品代理键
                builder.equals(builder.field("SS_STORE_SK"), builder.field("S_STORE_SK")), // 连接条件：销售门店代理键等于门店代理键
                builder.equals(builder.field("SS_CDEMO_SK"), builder.field("CD_DEMO_SK")), // 连接条件：销售客户人口统计代理键等于客户人口统计代理键
                builder.equals(builder.field("CD_GENDER"), builder.literal("M")), // 过滤条件：客户性别等于'M'
                builder.equals(builder.field("CD_MARITAL_STATUS"), builder.literal("S")), // 过滤条件：客户婚姻状况等于'S'
                builder.equals(builder.field("CD_EDUCATION_STATUS"), // 过滤条件：客户教育状况等于'HIGH SCHOOL'
                    builder.literal("HIGH SCHOOL")),
                builder.equals(builder.field("D_YEAR"), builder.literal(1998)), // 过滤条件：年份等于1998
                builder.in(builder.field("S_STATE"), // 过滤条件：州在指定的6个州中
                    builder.literal("CA"), // 加利福尼亚州
                    builder.literal("OR"), // 俄勒冈州
                    builder.literal("WA"), // 华盛顿州
                    builder.literal("TX"), // 德克萨斯州
                    builder.literal("OK"), // 俄克拉荷马州
                    builder.literal("MD"))) // 马里兰州
            .aggregate(builder.groupKey("I_ITEM_ID", "S_STATE"), // 聚合操作：按商品ID和州分组（相当于GROUP BY）
                builder.avg(false, "AGG1", builder.field("SS_QUANTITY")), // 计算平均销售数量，别名为AGG1（false表示不使用distinct）
                builder.avg(false, "AGG2", builder.field("SS_LIST_PRICE")), // 计算平均标价，别名为AGG2
                builder.avg(false, "AGG3", builder.field("SS_COUPON_AMT")), // 计算平均优惠券金额，别名为AGG3
                builder.avg(false, "AGG4", builder.field("SS_SALES_PRICE"))) // 计算平均销售价格，别名为AGG4
            .sortLimit(0, 100, builder.field("I_ITEM_ID"), builder.field("S_STATE")) // 排序和限制：按商品ID和州升序排序，限制返回100行（offset=0, fetch=100）
            .build(); // 构建关系节点，返回最终的逻辑计划
    String expectResult = "" // expectResult字符串：预期的逻辑计划结构，用于验证RelBuilder生成的计划是否正确
        + "LogicalSort(sort0=[$1], sort1=[$0], dir0=[ASC], dir1=[ASC], fetch=[100])\n" // LogicalSort节点：逻辑排序节点，按字段1和字段0升序排序，限制返回100行
        + "  LogicalAggregate(group=[{84, 90}], AGG1=[AVG($10)], AGG2=[AVG($12)], AGG3=[AVG($19)], AGG4=[AVG($13)])\n" // LogicalAggregate节点：逻辑聚合节点，按字段84和90分组，计算4个AVG聚合函数
        + "    LogicalFilter(condition=[AND(=($0, $32), =($2, $89), " // LogicalFilter节点：逻辑过滤节点，包含多个AND连接的条件
        + "=($7, $60), =($4, $23), =($24, 'M'), " // 连接条件和过滤条件：性别='M'
        + "=($25, 'S'), =($26, 'HIGH SCHOOL'), =($38, 1998), " // 过滤条件：婚姻状况='S'，教育状况='HIGH SCHOOL'，年份=1998
        + "SEARCH($84, Sarg['CA', 'MD', 'OK', 'OR', 'TX', 'WA']:CHAR(2)))])\n" // 过滤条件：州在指定的6个州中（使用Sarg搜索参数）
        + "      LogicalJoin(condition=[true], joinType=[inner])\n" // LogicalJoin节点：逻辑连接节点，条件为true（实际条件在Filter中），内连接
        + "        LogicalTableScan(table=[[TPCDS, STORE_SALES]])\n" // LogicalTableScan节点：逻辑表扫描，扫描STORE_SALES表
        + "        LogicalJoin(condition=[true], joinType=[inner])\n" // LogicalJoin节点：逻辑连接节点，内连接
        + "          LogicalTableScan(table=[[TPCDS, CUSTOMER_DEMOGRAPHICS]])\n" // LogicalTableScan节点：逻辑表扫描，扫描CUSTOMER_DEMOGRAPHICS表
        + "          LogicalJoin(condition=[true], joinType=[inner])\n" // LogicalJoin节点：逻辑连接节点，内连接
        + "            LogicalTableScan(table=[[TPCDS, DATE_DIM]])\n" // LogicalTableScan节点：逻辑表扫描，扫描DATE_DIM表
        + "            LogicalJoin(condition=[true], joinType=[inner])\n" // LogicalJoin节点：逻辑连接节点，内连接
        + "              LogicalTableScan(table=[[TPCDS, STORE]])\n" // LogicalTableScan节点：逻辑表扫描，扫描STORE表
        + "              LogicalTableScan(table=[[TPCDS, ITEM]])\n"; // LogicalTableScan节点：逻辑表扫描，扫描ITEM表
    assertThat(root, hasTree(expectResult)); // 使用Hamcrest断言验证生成的逻辑计划树是否与预期结果匹配
  } // testQuery27Builder方法结束
} // TpcdsTest类结束
