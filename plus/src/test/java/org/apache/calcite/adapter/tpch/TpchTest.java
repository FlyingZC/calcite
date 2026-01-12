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
 */ // Apache License 2.0 开源许可证声明
package org.apache.calcite.adapter.tpch; // 声明包名：org.apache.calcite.adapter.tpch，表示该类属于 Calcite 的 TPC-H 适配器包

import org.apache.calcite.plan.RelOptUtil; // 导入 RelOptUtil 工具类，用于关系表达式（RelNode）的字符串转换和操作
import org.apache.calcite.test.CalciteAssert; // 导入 CalciteAssert 测试工具类，用于编写 Calcite 的断言测试
import org.apache.calcite.util.TestUtil; // 导入 TestUtil 工具类，提供测试相关的实用方法

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的 ImmutableList 类，用于创建不可变列表

import org.junit.jupiter.api.Disabled; // 导入 JUnit 5 的 Disabled 注解，用于禁用测试方法
import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法
import org.junit.jupiter.api.Timeout; // 导入 JUnit 5 的 Timeout 注解，用于设置测试超时时间

import java.util.List; // 导入 Java 标准库的 List 接口
import java.util.concurrent.TimeUnit; // 导入 Java 标准库的 TimeUnit 枚举，用于时间单位转换

import static org.hamcrest.CoreMatchers.containsString; // 导入 Hamcrest 的 containsString 匹配器，用于验证字符串包含
import static org.hamcrest.CoreMatchers.not; // 导入 Hamcrest 的 not 匹配器，用于否定匹配
import static org.hamcrest.MatcherAssert.assertThat; // 导入 Hamcrest 的 assertThat 断言方法

/** Unit test for {@link org.apache.calcite.adapter.tpch.TpchSchema}. // TpchSchema 的单元测试类
 *
 * <p>Because the TPC-H data generator takes time and memory to instantiate, // 因为 TPC-H 数据生成器的实例化需要时间和内存
 * tests only run as part of slow tests. // 测试仅作为慢速测试的一部分运行
 */
class TpchTest { // 定义 TpchTest 测试类，用于测试 TPC-H 适配器的功能
  public static final boolean ENABLE = TestUtil.getJavaMajorVersion() >= 7; // 静态常量：测试启用标志，只有在 Java 7 或更高版本时才启用测试（因为 TPC-H 库需要 Java 7+）

  private static String schema(String name, String scaleFactor) { // 私有静态方法：生成 TPC-H schema 的 JSON 配置字符串
    return "     {\n" // 返回 JSON 格式的 schema 配置字符串
        + "       type: 'custom',\n" // schema 类型为自定义（custom）
        + "       name: '" + name + "',\n" // schema 名称，如 "TPCH"、"TPCH_01" 等
        + "       factory: 'org.apache.calcite.adapter.tpch.TpchSchemaFactory',\n" // 指定 TPC-H schema 工厂类
        + "       operand: {\n" // 工厂的操作数参数
        + "         columnPrefix: false,\n" // 不使用列前缀
        + "         scale: " + scaleFactor + "\n" // 数据规模因子，如 1.0、0.01、5.0 等
        + "       }\n"
        + "     }";
  }

  public static final String TPCH_MODEL = "{\n" // 公共静态常量：TPC-H 模型的 JSON 配置，包含多个不同规模的 schema
      + "  version: '1.0',\n" // 模型版本号
      + "  defaultSchema: 'TPCH',\n" // 默认 schema 为 TPCH
      + "   schemas: [\n" // schemas 数组，包含多个 schema 定义
      + schema("TPCH", "1.0") + ",\n" // TPCH schema，规模因子为 1.0（标准规模）
      + schema("TPCH_01", "0.01") + ",\n" // TPCH_01 schema，规模因子为 0.01（小规模）
      + schema("TPCH_5", "5.0") + "\n" // TPCH_5 schema，规模因子为 5.0（大规模）
      + "   ]\n"
      + "}";

  private static final String[] QUERY_ARRAY = { // 私有静态常量：TPC-H 查询数组，包含 22 个标准的 TPC-H 基准测试查询
      "select\n" // 查询 1：定价统计报告查询，计算 lineitem 表中按退货标志和订单状态分组的各种汇总指标
          + "  l_returnflag,\n" // 退货标志
          + "  l_linestatus,\n" // 订单状态
          + "  sum(l_quantity) as sum_qty,\n" // 数量总和
          + "  sum(l_extendedprice) as sum_base_price,\n" // 扩展价格总和（基础价格）
          + "  sum(l_extendedprice * (1 - l_discount)) as sum_disc_price,\n" // 折扣后价格总和
          + "  sum(l_extendedprice * (1 - l_discount) * (1 + l_tax)) as sum_charge,\n" // 税后价格总和
          + "  avg(l_quantity) as avg_qty,\n" // 平均数量
          + "  avg(l_extendedprice) as avg_price,\n" // 平均价格
          + "  avg(l_discount) as avg_disc,\n" // 平均折扣
          + "  count(*) as count_order\n" // 订单数量
          + "from\n"
          + "  tpch.lineitem\n" // 从 lineitem 表查询
          + "-- where\n" // where 子句被注释掉，用于测试时减少数据量
          + "--  l_shipdate <= date '1998-12-01' - interval '120' day (3)\n" // 原始的日期过滤条件
          + "group by\n"
          + "  l_returnflag,\n" // 按退货标志分组
          + "  l_linestatus\n" // 按订单状态分组
          + "\n"
          + "order by\n"
          + "  l_returnflag,\n" // 按退货标志排序
          + "  l_linestatus", // 按订单状态排序

      // 02 // 查询 2：最低成本供应商查询，查找在特定地区提供最低成本的零件供应商
      "select\n"
          + "  s.s_acctbal,\n" // 供应商账户余额
          + "  s.s_name,\n" // 供应商名称
          + "  n.n_name,\n" // 国家名称
          + "  p.p_partkey,\n" // 零件键
          + "  p.p_mfgr,\n" // 制造商
          + "  s.s_address,\n" // 供应商地址
          + "  s.s_phone,\n" // 供应商电话
          + "  s.s_comment\n" // 供应商备注
          + "from\n"
          + "  tpch.part p,\n" // 从零件表
          + "  tpch.supplier s,\n" // 供应商表
          + "  tpch.partsupp ps,\n" // 零件供应表
          + "  tpch.nation n,\n" // 国家表
          + "  tpch.region r\n" // 地区表
          + "where\n"
          + "  p.p_partkey = ps.ps_partkey\n" // 零件键关联
          + "  and s.s_suppkey = ps.ps_suppkey\n" // 供应商键关联
          + "  and p.p_size = 41\n" // 零件大小为 41
          + "  and p.p_type like '%NICKEL'\n" // 零件类型包含 NICKEL
          + "  and s.s_nationkey = n.n_nationkey\n" // 供应商国家键关联
          + "  and n.n_regionkey = r.r_regionkey\n" // 国家地区键关联
          + "  and r.r_name = 'EUROPE'\n" // 地区为欧洲
          + "  and ps.ps_supplycost = (\n" // 供应成本等于子查询的最小供应成本
          + "\n"
          + "    select\n"
          + "      min(ps.ps_supplycost)\n" // 获取最小供应成本
          + "\n"
          + "    from\n"
          + "      tpch.partsupp ps,\n"
          + "      tpch.supplier s,\n"
          + "      tpch.nation n,\n"
          + "      tpch.region r\n"
          + "    where\n"
          + "      p.p_partkey = ps.ps_partkey\n" // 相同的关联条件
          + "      and s.s_suppkey = ps.ps_suppkey\n"
          + "      and s.s_nationkey = n.n_nationkey\n"
          + "      and n.n_regionkey = r.r_regionkey\n"
          + "      and r.r_name = 'EUROPE'\n" // 相同的地区限制
          + "  )\n"
          + "\n"
          + "order by\n"
          + "  s.s_acctbal desc,\n" // 按账户余额降序
          + "  n.n_name,\n" // 按国家名称
          + "  s.s_name,\n" // 按供应商名称
          + "  p.p_partkey\n" // 按零件键
          + "limit 100", // 限制返回 100 条记录

      // 03 // 查询 3：运输优先级查询，查找特定市场细分客户的未发货订单
      "select\n"
          + "  l.l_orderkey,\n" // 订单键
          + "  sum(l.l_extendedprice * (1 - l.l_discount)) as revenue,\n" // 收入（折扣后价格总和）
          + "  o.o_orderdate,\n" // 订单日期
          + "  o.o_shippriority\n" // 运输优先级
          + "\n"
          + "from\n"
          + "  tpch.customer c,\n" // 客户表
          + "  tpch.orders o,\n" // 订单表
          + "  tpch.lineitem l\n" // 订单明细表
          + "\n"
          + "where\n"
          + "  c.c_mktsegment = 'HOUSEHOLD'\n" // 市场细分为家庭
          + "  and c.c_custkey = o.o_custkey\n" // 客户键关联订单
          + "  and l.l_orderkey = o.o_orderkey\n" // 订单键关联订单明细
          + "--  and o.o_orderdate < date '1995-03-25'\n" // 日期条件被注释掉
          + "--  and l.l_shipdate > date '1995-03-25'\n" // 发货日期条件被注释掉
          + "\n"
          + "group by\n"
          + "  l.l_orderkey,\n" // 按订单键分组
          + "  o.o_orderdate,\n" // 按订单日期分组
          + "  o.o_shippriority\n" // 按运输优先级分组
          + "order by\n"
          + "  revenue desc,\n" // 按收入降序
          + "  o.o_orderdate\n" // 按订单日期
          + "limit 10", // 限制返回 10 条记录

      // 04 // 查询 4：订单优先级查询，统计具有延迟订单明细的订单数量
      "select\n"
          + "  o_orderpriority,\n" // 订单优先级
          + "  count(*) as order_count\n" // 订单数量
          + "from\n"
          + "  tpch.orders\n" // 从订单表
          + "\n"
          + "where\n"
          + "--  o_orderdate >= date '1996-10-01'\n" // 日期条件被注释掉
          + "--  and o_orderdate < date '1996-10-01' + interval '3' month\n" // 日期范围条件被注释掉
          + "--  and\n"
          + "  exists (\n" // 存在子查询：检查是否有延迟的订单明细
          + "    select\n"
          + "      *\n"
          + "    from\n"
          + "      tpch.lineitem\n" // 从订单明细表
          + "    where\n"
          + "      l_orderkey = o_orderkey\n" // 订单键关联
          + "      and l_commitdate < l_receiptdate\n" // 提交日期早于接收日期（表示延迟）
          + "  )\n"
          + "group by\n"
          + "  o_orderpriority\n" // 按订单优先级分组
          + "order by\n"
          + "  o_orderpriority", // 按订单优先级排序

      // 05 // 查询 5：按国家统计收入查询，计算特定地区各国家的订单收入
      "select\n"
          + "  n.n_name,\n" // 国家名称
          + "  sum(l.l_extendedprice * (1 - l.l_discount)) as revenue\n" // 收入（折扣后价格总和）
          + "\n"
          + "from\n"
          + "  tpch.customer c,\n" // 客户表
          + "  tpch.orders o,\n" // 订单表
          + "  tpch.lineitem l,\n" // 订单明细表
          + "  tpch.supplier s,\n" // 供应商表
          + "  tpch.nation n,\n" // 国家表
          + "  tpch.region r\n" // 地区表
          + "\n"
          + "where\n"
          + "  c.c_custkey = o.o_custkey\n" // 客户键关联订单
          + "  and l.l_orderkey = o.o_orderkey\n" // 订单明细键关联订单
          + "  and l.l_suppkey = s.s_suppkey\n" // 订单明细供应商键关联供应商
          + "  and c.c_nationkey = s.s_nationkey\n" // 客户国家等于供应商国家（本地业务）
          + "  and s.s_nationkey = n.n_nationkey\n" // 供应商国家键关联国家
          + "  and n.n_regionkey = r.r_regionkey\n" // 国家地区键关联地区
          + "  and r.r_name = 'EUROPE'\n" // 地区为欧洲
          + "--  and o.o_orderdate >= date '1997-01-01'\n" // 日期条件被注释掉
          + "--  and o.o_orderdate < date '1997-01-01' + interval '1' year\n" // 日期范围条件被注释掉
          + "group by\n"
          + "  n.n_name\n" // 按国家名称分组
          + "\n"
          + "order by\n"
          + "  revenue desc", // 按收入降序排序

      // 06 // 查询 6：收入预测查询，计算特定折扣范围和数量范围的订单收入
      "select\n"
          + "  sum(l_extendedprice * l_discount) as revenue\n" // 收入（扩展价格乘以折扣）
          + "from\n"
          + "  tpch.lineitem\n" // 从订单明细表
          + "where\n"
          + "--  l_shipdate >= date '1997-01-01'\n" // 发货日期条件被注释掉
          + "--  and l_shipdate < date '1997-01-01' + interval '1' year\n" // 日期范围条件被注释掉
          + "--  and\n"
          + "  l_discount between 0.03 - 0.01 and 0.03 + 0.01\n" // 折扣在 0.02 到 0.04 之间
          + "  and l_quantity < 24", // 数量小于 24

      // 07 // 查询 7：货物运输量查询，统计特定国家对之间的货物运输收入
      "select\n"
          + "  supp_nation,\n" // 供应商国家
          + "  cust_nation,\n" // 客户国家
          + "  l_year,\n" // 运输年份
          + "  sum(volume) as revenue\n" // 收入总和
          + "from\n"
          + "  (\n" // 子查询：计算每笔运输的详细信息
          + "    select\n"
          + "      n1.n_name as supp_nation,\n" // 供应商国家名称
          + "      n2.n_name as cust_nation,\n" // 客户国家名称
          + "      extract(year from l.l_shipdate) as l_year,\n" // 提取发货日期的年份
          + "      l.l_extendedprice * (1 - l.l_discount) as volume\n" // 运输量（折扣后价格）
          + "    from\n"
          + "      tpch.supplier s,\n" // 供应商表
          + "      tpch.lineitem l,\n" // 订单明细表
          + "      tpch.orders o,\n" // 订单表
          + "      tpch.customer c,\n" // 客户表
          + "      tpch.nation n1,\n" // 供应商国家表
          + "      tpch.nation n2\n" // 客户国家表
          + "    where\n"
          + "      s.s_suppkey = l.l_suppkey\n" // 供应商键关联
          + "      and o.o_orderkey = l.l_orderkey\n" // 订单键关联
          + "      and c.c_custkey = o.o_custkey\n" // 客户键关联
          + "      and s.s_nationkey = n1.n_nationkey\n" // 供应商国家键关联
          + "      and c.c_nationkey = n2.n_nationkey\n" // 客户国家键关联
          + "      and (\n" // 国家对条件：埃及和美国之间的贸易
          + "        (n1.n_name = 'EGYPT' and n2.n_name = 'UNITED STATES')\n" // 供应商为埃及，客户为美国
          + "        or (n1.n_name = 'UNITED STATES' and n2.n_name = 'EGYPT')\n" // 或供应商为美国，客户为埃及
          + "      )\n"
          + "--      and l.l_shipdate between date '1995-01-01' and date '1996-12-31'\n" // 日期范围条件被注释掉
          + "  ) as shipping\n" // 子查询别名为 shipping
          + "group by\n"
          + "  supp_nation,\n" // 按供应商国家分组
          + "  cust_nation,\n" // 按客户国家分组
          + "  l_year\n" // 按年份分组
          + "order by\n"
          + "  supp_nation,\n" // 按供应商国家排序
          + "  cust_nation,\n" // 按客户国家排序
          + "  l_year", // 按年份排序

      // 08 // 查询 8：市场份额查询，计算特定国家在特定地区的市场份额
      "select\n"
          + "  o_year,\n" // 订单年份
          + "  sum(case\n" // 计算市场份额的 case 表达式
          + "    when nation = 'EGYPT' then volume\n" // 如果国家为埃及，则计入 volume
          + "    else 0\n" // 否则为 0
          + "  end) / sum(volume) as mkt_share\n" // 市场份额 = 埃及的 volume / 总 volume
          + "from\n"
          + "  (\n" // 子查询：计算每年的订单详细信息
          + "    select\n"
          + "      extract(year from o.o_orderdate) as o_year,\n" // 提取订单日期的年份
          + "      l.l_extendedprice * (1 - l.l_discount) as volume,\n" // 订单量（折扣后价格）
          + "      n2.n_name as nation\n" // 供应商国家名称
          + "    from\n"
          + "      tpch.part p,\n" // 零件表
          + "      tpch.supplier s,\n" // 供应商表
          + "      tpch.lineitem l,\n" // 订单明细表
          + "      tpch.orders o,\n" // 订单表
          + "      tpch.customer c,\n" // 客户表
          + "      tpch.nation n1,\n" // 客户国家表
          + "      tpch.nation n2,\n" // 供应商国家表
          + "      tpch.region r\n" // 地区表
          + "    where\n"
          + "      p.p_partkey = l.l_partkey\n" // 零件键关联
          + "      and s.s_suppkey = l.l_suppkey\n" // 供应商键关联
          + "      and l.l_orderkey = o.o_orderkey\n" // 订单键关联
          + "      and o.o_custkey = c.c_custkey\n" // 客户键关联
          + "      and c.c_nationkey = n1.n_nationkey\n" // 客户国家键关联
          + "      and n1.n_regionkey = r.r_regionkey\n" // 客户国家地区键关联
          + "      and r.r_name = 'MIDDLE EAST'\n" // 客户地区为中东
          + "      and s.s_nationkey = n2.n_nationkey\n" // 供应商国家键关联
          + "      and o.o_orderdate between date '1995-01-01' and date '1996-12-31'\n" // 订单日期在 1995-1996 年之间
          + "      and p.p_type = 'PROMO BRUSHED COPPER'\n" // 零件类型为 PROMO BRUSHED COPPER
          + "  ) as all_nations\n" // 子查询别名为 all_nations
          + "group by\n"
          + "  o_year\n" // 按年份分组
          + "order by\n"
          + "  o_year", // 按年份排序

      // 09 // 查询 9：产品类型利润查询，计算特定产品类型在各国家的利润
      "select\n"
          + "  nation,\n" // 国家名称
          + "  o_year,\n" // 订单年份
          + "  sum(amount) as sum_profit\n" // 利润总和
          + "from\n"
          + "  (\n" // 子查询：计算每笔订单的利润
          + "    select\n"
          + "      n_name as nation,\n" // 国家名称
          + "      extract(year from o_orderdate) as o_year,\n" // 提取订单日期的年份
          + "      l.l_extendedprice * (1 - l.l_discount) - ps.ps_supplycost * l.l_quantity as amount\n" // 利润 = 收入 - 成本
          + "    from\n"
          + "      tpch.part p,\n" // 零件表
          + "      tpch.supplier s,\n" // 供应商表
          + "      tpch.lineitem l,\n" // 订单明细表
          + "      tpch.partsupp ps,\n" // 零件供应表
          + "      tpch.orders o,\n" // 订单表
          + "      tpch.nation n\n" // 国家表
          + "    where\n"
          + "      s.s_suppkey = l.l_suppkey\n" // 供应商键关联
          + "      and ps.ps_suppkey = l.l_suppkey\n" // 零件供应供应商键关联
          + "      and ps.ps_partkey = l.l_partkey\n" // 零件供应零件键关联
          + "      and p.p_partkey = l.l_partkey\n" // 零件键关联
          + "      and o.o_orderkey = l.l_orderkey\n" // 订单键关联
          + "      and s.s_nationkey = n.n_nationkey\n" // 供应商国家键关联
          + "      and p.p_name like '%yellow%'\n" // 零件名称包含 yellow
          + "  ) as profit\n" // 子查询别名为 profit
          + "group by\n"
          + "  nation,\n" // 按国家分组
          + "  o_year\n" // 按年份分组
          + "order by\n"
          + "  nation,\n" // 按国家排序
          + "  o_year desc", // 按年份降序排序

      // 10 // 查询 10：退货客户查询，查找在特定时间段内有退货的客户
      "select\n"
          + "  c.c_custkey,\n" // 客户键
          + "  c.c_name,\n" // 客户名称
          + "  sum(l.l_extendedprice * (1 - l.l_discount)) as revenue,\n" // 收入（折扣后价格总和）
          + "  c.c_acctbal,\n" // 客户账户余额
          + "  n.n_name,\n" // 国家名称
          + "  c.c_address,\n" // 客户地址
          + "  c.c_phone,\n" // 客户电话
          + "  c.c_comment\n" // 客户备注
          + "from\n"
          + "  tpch.customer c,\n" // 客户表
          + "  tpch.orders o,\n" // 订单表
          + "  tpch.lineitem l,\n" // 订单明细表
          + "  tpch.nation n\n" // 国家表
          + "where\n"
          + "  c.c_custkey = o.o_custkey\n" // 客户键关联订单
          + "  and l.l_orderkey = o.o_orderkey\n" // 订单明细键关联订单
          + "  and o.o_orderdate >= date '1994-03-01'\n" // 订单日期在 1994-03-01 之后
          + "  and o.o_orderdate < date '1994-03-01' + interval '3' month\n" // 订单日期在 1994-03-01 之后 3 个月内
          + "  and l.l_returnflag = 'R'\n" // 退货标志为 R（已退货）
          + "  and c.c_nationkey = n.n_nationkey\n" // 客户国家键关联
          + "group by\n"
          + "  c.c_custkey,\n" // 按客户键分组
          + "  c.c_name,\n" // 按客户名称分组
          + "  c.c_acctbal,\n" // 按账户余额分组
          + "  c.c_phone,\n" // 按电话分组
          + "  n.n_name,\n" // 按国家名称分组
          + "  c.c_address,\n" // 按地址分组
          + "  c.c_comment\n" // 按备注分组
          + "order by\n"
          + "  revenue desc\n" // 按收入降序排序
          + "limit 20", // 限制返回 20 条记录

      // 11 // 查询 11：重要库存识别查询，查找特定国家中价值超过阈值的零件
      "select\n"
          + "  ps.ps_partkey,\n" // 零件键
          + "  sum(ps.ps_supplycost * ps.ps_availqty) as \"value\"\n" // 价值 = 供应成本 × 可用数量
          + "from\n"
          + "  tpch.partsupp ps,\n" // 零件供应表
          + "  tpch.supplier s,\n" // 供应商表
          + "  tpch.nation n\n" // 国家表
          + "where\n"
          + "  ps.ps_suppkey = s.s_suppkey\n" // 零件供应供应商键关联
          + "  and s.s_nationkey = n.n_nationkey\n" // 供应商国家键关联
          + "  and n.n_name = 'JAPAN'\n" // 国家为日本
          + "group by\n"
          + "  ps.ps_partkey having\n" // 按零件键分组，并使用 having 子句过滤
          + "    sum(ps.ps_supplycost * ps.ps_availqty) > (\n" // 价值大于子查询的阈值
          + "      select\n"
          + "        sum(ps.ps_supplycost * ps.ps_availqty) * 0.0001000000\n" // 阈值 = 日本总价值的 0.01%
          + "      from\n"
          + "        tpch.partsupp ps,\n"
          + "        tpch.supplier s,\n"
          + "        tpch.nation n\n"
          + "      where\n"
          + "        ps.ps_suppkey = s.s_suppkey\n"
          + "        and s.s_nationkey = n.n_nationkey\n"
          + "        and n.n_name = 'JAPAN'\n" // 计算日本所有零件的总价值
          + "    )\n"
          + "order by\n"
          + "  \"value\" desc", // 按价值降序排序

      // 12 // 查询 12：运输模式查询，统计高优先级和低优先级订单的运输模式分布
      "select\n"
          + "  l.l_shipmode,\n" // 运输模式
          + "  sum(case\n" // 计算高优先级订单数量的 case 表达式
          + "    when o.o_orderpriority = '1-URGENT'\n" // 如果订单优先级为 1-URGENT
          + "      or o.o_orderpriority = '2-HIGH'\n" // 或 2-HIGH
          + "      then 1\n" // 则计为 1
          + "    else 0\n" // 否则为 0
          + "  end) as high_line_count,\n" // 高优先级订单数量
          + "  sum(case\n" // 计算低优先级订单数量的 case 表达式
          + "    when o.o_orderpriority <> '1-URGENT'\n" // 如果订单优先级不是 1-URGENT
          + "      and o.o_orderpriority <> '2-HIGH'\n" // 且不是 2-HIGH
          + "      then 1\n" // 则计为 1
          + "    else 0\n" // 否则为 0
          + "  end) as low_line_count\n" // 低优先级订单数量
          + "from\n"
          + "  tpch.orders o,\n" // 订单表
          + "  tpch.lineitem l\n" // 订单明细表
          + "where\n"
          + "  o.o_orderkey = l.l_orderkey\n" // 订单键关联
          + "  and l.l_shipmode in ('TRUCK', 'REG AIR')\n" // 运输模式为卡车或定期空运
          + "  and l.l_commitdate < l.l_receiptdate\n" // 提交日期早于接收日期（延迟）
          + "  and l.l_shipdate < l.l_commitdate\n" // 发货日期早于提交日期（未按承诺时间发货）
          + "--  and l.l_receiptdate >= date '1994-01-01'\n" // 接收日期条件被注释掉
          + "--  and l.l_receiptdate < date '1994-01-01' + interval '1' year\n" // 日期范围条件被注释掉
          + "group by\n"
          + "  l.l_shipmode\n" // 按运输模式分组
          + "order by\n"
          + "  l.l_shipmode", // 按运输模式排序

      // 13 // 查询 13：客户分布查询，统计客户订单数量的分布情况
      "select\n"
          + "  c_count,\n" // 客户订单数量
          + "  count(*) as custdist\n" // 客户数
          + "from\n"
          + "  (\n" // 子查询：计算每个客户的订单数量
          + "    select\n"
          + "      c.c_custkey,\n" // 客户键
          + "      count(o.o_orderkey)\n" // 订单数量
          + "    from\n"
          + "      tpch.customer c\n" // 客户表
          + "      left outer join tpch.orders o\n" // 左外连接订单表
          + "        on c.c_custkey = o.o_custkey\n" // 客户键关联
          + "        and o.o_comment not like '%special%requests%'\n" // 排除备注包含 special requests 的订单
          + "    group by\n"
          + "      c.c_custkey\n" // 按客户键分组
          + "  ) as orders (c_custkey, c_count)\n" // 子查询别名为 orders，指定列别名
          + "group by\n"
          + "  c_count\n" // 按订单数量分组
          + "order by\n"
          + "  custdist desc,\n" // 按客户数降序
          + "  c_count desc", // 按订单数量降序

      // 14 // 查询 14：促销效果查询，计算促销商品的收入占比
      "select\n"
          + "  100.00 * sum(case\n" // 计算促销收入占比的 case 表达式
          + "    when p.p_type like 'PROMO%'\n" // 如果零件类型以 PROMO 开头
          + "      then l.l_extendedprice * (1 - l.l_discount)\n" // 则计入折扣后价格
          + "    else 0\n" // 否则为 0
          + "  end) / sum(l.l_extendedprice * (1 - l.l_discount)) as promo_revenue\n" // 促销收入占比 = 促销收入 / 总收入 × 100
          + "from\n"
          + "  tpch.lineitem l,\n" // 订单明细表
          + "  tpch.part p\n" // 零件表
          + "where\n"
          + "  l.l_partkey = p.p_partkey\n" // 零件键关联
          + "  and l.l_shipdate >= date '1994-08-01'\n" // 发货日期在 1994-08-01 之后
          + "  and l.l_shipdate < date '1994-08-01' + interval '1' month", // 发货日期在 1994-08-01 之后 1 个月内

      // 15 // 查询 15：顶级供应商查询，查找收入最高的供应商
      "with revenue0 (supplier_no, total_revenue) as (\n" // CTE（公共表表达式）：计算每个供应商的总收入
          + "  select\n"
          + "    l_suppkey,\n" // 供应商键
          + "    sum(l_extendedprice * (1 - l_discount))\n" // 总收入（折扣后价格总和）
          + "  from\n"
          + "    tpch.lineitem\n" // 订单明细表
          + "  where\n"
          + "    l_shipdate >= date '1993-05-01'\n" // 发货日期在 1993-05-01 之后
          + "    and l_shipdate < date '1993-05-01' + interval '3' month\n" // 发货日期在 1993-05-01 之后 3 个月内
          + "  group by\n"
          + "    l_suppkey)\n" // 按供应商键分组
          + "select\n"
          + "  s.s_suppkey,\n" // 供应商键
          + "  s.s_name,\n" // 供应商名称
          + "  s.s_address,\n" // 供应商地址
          + "  s.s_phone,\n" // 供应商电话
          + "  r.total_revenue\n" // 总收入
          + "from\n"
          + "  tpch.supplier s,\n" // 供应商表
          + "  revenue0 r\n" // CTE 表
          + "where\n"
          + "  s.s_suppkey = r.supplier_no\n" // 供应商键关联
          + "  and r.total_revenue = (\n" // 收入等于子查询的最大收入
          + "    select\n"
          + "      max(total_revenue)\n" // 获取最大收入
          + "    from\n"
          + "      revenue0\n" // 从 CTE 表
          + "  )\n"
          + "order by\n"
          + "  s.s_suppkey", // 按供应商键排序

      // 16 // 查询 16：零件/供应商数量查询，统计特定零件的供应商数量
      "select\n"
          + "  p.p_brand,\n" // 零件品牌
          + "  p.p_type,\n" // 零件类型
          + "  p.p_size,\n" // 零件大小
          + "  count(distinct ps.ps_suppkey) as supplier_cnt\n" // 供应商数量（去重）
          + "from\n"
          + "  tpch.partsupp ps,\n" // 零件供应表
          + "  tpch.part p\n" // 零件表
          + "where\n"
          + "  p.p_partkey = ps.ps_partkey\n" // 零件键关联
          + "  and p.p_brand <> 'Brand#21'\n" // 零件品牌不是 Brand#21
          + "  and p.p_type not like 'MEDIUM PLATED%'\n" // 零件类型不以 MEDIUM PLATED 开头
          + "  and p.p_size in (38, 2, 8, 31, 44, 5, 14, 24)\n" // 零件大小在指定列表中
          + "  and ps.ps_suppkey not in (\n" // 供应商键不在子查询中
          + "    select\n"
          + "      s_suppkey\n"
          + "    from\n"
          + "      tpch.supplier\n" // 供应商表
          + "    where\n"
          + "      s_comment like '%Customer%Complaints%'\n" // 排除备注包含 Customer Complaints 的供应商
          + "  )\n"
          + "group by\n"
          + "  p.p_brand,\n" // 按品牌分组
          + "  p.p_type,\n" // 按类型分组
          + "  p.p_size\n" // 按大小分组
          + "order by\n"
          + "  supplier_cnt desc,\n" // 按供应商数量降序
          + "  p.p_brand,\n" // 按品牌
          + "  p.p_type,\n" // 按类型
          + "  p.p_size", // 按大小

      // 17 // 查询 17：小批量订单收入查询，计算特定零件小批量订单的平均年收入
      "select\n"
          + "  sum(l.l_extendedprice) / 7.0 as avg_yearly\n" // 平均年收入 = 总收入 / 7（假设为 7 年）
          + "from\n"
          + "  tpch.lineitem l,\n" // 订单明细表
          + "  tpch.part p\n" // 零件表
          + "where\n"
          + "  p.p_partkey = l.l_partkey\n" // 零件键关联
          + "  and p.p_brand = 'Brand#13'\n" // 零件品牌为 Brand#13
          + "  and p.p_container = 'JUMBO CAN'\n" // 零件容器为 JUMBO CAN
          + "  and l.l_quantity < (\n" // 数量小于子查询的平均数量的 20%
          + "    select\n"
          + "      0.2 * avg(l2.l_quantity)\n" // 平均数量的 20%
          + "    from\n"
          + "      tpch.lineitem l2\n" // 订单明细表（别名 l2）
          + "    where\n"
          + "      l2.l_partkey = p.p_partkey\n" // 零件键关联（同一种零件）
          + "  )",

      // 18 // 查询 18：大批量订单查询，查找订单数量超过阈值的订单
      "select\n"
          + "  c.c_name,\n" // 客户名称
          + "  c.c_custkey,\n" // 客户键
          + "  o.o_orderkey,\n" // 订单键
          + "  o.o_orderdate,\n" // 订单日期
          + "  o.o_totalprice,\n" // 订单总价
          + "  sum(l.l_quantity)\n" // 订单数量总和
          + "from\n"
          + "  tpch.customer c,\n" // 客户表
          + "  tpch.orders o,\n" // 订单表
          + "  tpch.lineitem l\n" // 订单明细表
          + "where\n"
          + "  o.o_orderkey in (\n" // 订单键在子查询中
          + "    select\n"
          + "      l_orderkey\n" // 订单键
          + "    from\n"
          + "      tpch.lineitem\n" // 订单明细表
          + "    group by\n"
          + "      l_orderkey having\n" // 按订单键分组，并使用 having 子句过滤
          + "        sum(l_quantity) > 313\n" // 订单数量总和大于 313
          + "  )\n"
          + "  and c.c_custkey = o.o_custkey\n" // 客户键关联订单
          + "  and o.o_orderkey = l.l_orderkey\n" // 订单键关联订单明细
          + "group by\n"
          + "  c.c_name,\n" // 按客户名称分组
          + "  c.c_custkey,\n" // 按客户键分组
          + "  o.o_orderkey,\n" // 按订单键分组
          + "  o.o_orderdate,\n" // 按订单日期分组
          + "  o.o_totalprice\n" // 按订单总价分组
          + "order by\n"
          + "  o.o_totalprice desc,\n" // 按订单总价降序
          + "  o.o_orderdate\n" // 按订单日期
          + "limit 100", // 限制返回 100 条记录

      // 19 // 查询 19：折扣收入查询，计算特定条件下三种不同规格零件的收入
      "select\n"
          + "  sum(l.l_extendedprice* (1 - l.l_discount)) as revenue\n" // 收入总和（折扣后价格）
          + "from\n"
          + "  tpch.lineitem l,\n" // 订单明细表
          + "  tpch.part p\n" // 零件表
          + "where\n"
          + "  (\n" // 第一个条件组合：小型零件
          + "    p.p_partkey = l.l_partkey\n" // 零件键关联
          + "    and p.p_brand = 'Brand#41'\n" // 品牌为 Brand#41
          + "    and p.p_container in ('SM CASE', 'SM BOX', 'SM PACK', 'SM PKG')\n" // 容器为小型
          + "    and l.l_quantity >= 2 and l.l_quantity <= 2 + 10\n" // 数量在 2-12 之间
          + "    and p.p_size between 1 and 5\n" // 大小在 1-5 之间
          + "    and l.l_shipmode in ('AIR', 'AIR REG')\n" // 运输模式为空运
          + "    and l.l_shipinstruct = 'DELIVER IN PERSON'\n" // 运输指令为亲自交付
          + "  )\n"
          + "  or\n" // 或
          + "  (\n" // 第二个条件组合：中型零件
          + "    p.p_partkey = l.l_partkey\n" // 零件键关联
          + "    and p.p_brand = 'Brand#13'\n" // 品牌为 Brand#13
          + "    and p.p_container in ('MED BAG', 'MED BOX', 'MED PKG', 'MED PACK')\n" // 容器为中型
          + "    and l.l_quantity >= 14 and l.l_quantity <= 14 + 10\n" // 数量在 14-24 之间
          + "    and p.p_size between 1 and 10\n" // 大小在 1-10 之间
          + "    and l.l_shipmode in ('AIR', 'AIR REG')\n" // 运输模式为空运
          + "    and l.l_shipinstruct = 'DELIVER IN PERSON'\n" // 运输指令为亲自交付
          + "  )\n"
          + "  or\n" // 或
          + "  (\n" // 第三个条件组合：大型零件
          + "    p.p_partkey = l.l_partkey\n" // 零件键关联
          + "    and p.p_brand = 'Brand#55'\n" // 品牌为 Brand#55
          + "    and p.p_container in ('LG CASE', 'LG BOX', 'LG PACK', 'LG PKG')\n" // 容器为大型
          + "    and l.l_quantity >= 23 and l.l_quantity <= 23 + 10\n" // 数量在 23-33 之间
          + "    and p.p_size between 1 and 15\n" // 大小在 1-15 之间
          + "    and l.l_shipmode in ('AIR', 'AIR REG')\n" // 运输模式为空运
          + "    and l.l_shipinstruct = 'DELIVER IN PERSON'\n" // 运输指令为亲自交付
          + "  )",

      // 20 // 查询 20：潜在零件促销查询，查找特定国家中可以供应潜在促销零件的供应商
      "select\n"
          + "  s.s_name,\n" // 供应商名称
          + "  s.s_address\n" // 供应商地址
          + "from\n"
          + "  tpch.supplier s,\n" // 供应商表
          + "  tpch.nation n\n" // 国家表
          + "where\n"
          + "  s.s_suppkey in (\n" // 供应商键在子查询中
          + "    select\n"
          + "      ps.ps_suppkey\n" // 供应商键
          + "    from\n"
          + "      tpch.partsupp ps\n" // 零件供应表
          + "    where\n"
          + "      ps. ps_partkey in (\n" // 零件键在子查询中
          + "        select\n"
          + "          p.p_partkey\n" // 零件键
          + "        from\n"
          + "          tpch.part p\n" // 零件表
          + "        where\n"
          + "          p.p_name like 'antique%'\n" // 零件名称以 antique 开头
          + "      )\n"
          + "      and ps.ps_availqty > (\n" // 可用数量大于子查询的阈值
          + "        select\n"
          + "          0.5 * sum(l.l_quantity)\n" // 阈值 = 过去一年发货数量总和的 50%
          + "        from\n"
          + "          tpch.lineitem l\n" // 订单明细表
          + "        where\n"
          + "          l.l_partkey = ps.ps_partkey\n" // 零件键关联
          + "          and l.l_suppkey = ps.ps_suppkey\n" // 供应商键关联
          + "          and l.l_shipdate >= date '1993-01-01'\n" // 发货日期在 1993-01-01 之后
          + "          and l.l_shipdate < date '1993-01-01' + interval '1' year\n" // 发货日期在 1993-01-01 之后 1 年内
          + "      )\n"
          + "  )\n"
          + "  and s.s_nationkey = n.n_nationkey\n" // 供应商国家键关联
          + "  and n.n_name = 'KENYA'\n" // 国家为肯尼亚
          + "order by\n"
          + "  s.s_name", // 按供应商名称排序

      // 21 // 查询 21：无法按时交付的供应商查询，查找特定国家中只能按时交付部分订单的供应商
      "select\n"
          + "  s.s_name,\n" // 供应商名称
          + "  count(*) as numwait\n" // 等待数量
          + "from\n"
          + "  tpch.supplier s,\n" // 供应商表
          + "  tpch.lineitem l1,\n" // 订单明细表（别名 l1）
          + "  tpch.orders o,\n" // 订单表
          + "  tpch.nation n\n" // 国家表
          + "where\n"
          + "  s.s_suppkey = l1.l_suppkey\n" // 供应商键关联
          + "  and o.o_orderkey = l1.l_orderkey\n" // 订单键关联
          + "  and o.o_orderstatus = 'F'\n" // 订单状态为 F（已完成）
          + "  and l1.l_receiptdate > l1.l_commitdate\n" // 接收日期晚于提交日期（延迟）
          + "  and exists (\n" // 存在子查询：检查是否有其他供应商
          + "    select\n"
          + "      *\n"
          + "    from\n"
          + "      tpch.lineitem l2\n" // 订单明细表（别名 l2）
          + "    where\n"
          + "      l2.l_orderkey = l1.l_orderkey\n" // 同一订单
          + "      and l2.l_suppkey <> l1.l_suppkey\n" // 不同供应商
          + "  )\n"
          + "  and not exists (\n" // 不存在子查询：检查其他供应商是否按时交付
          + "    select\n"
          + "      *\n"
          + "    from\n"
          + "      tpch.lineitem l3\n" // 订单明细表（别名 l3）
          + "    where\n"
          + "      l3.l_orderkey = l1.l_orderkey\n" // 同一订单
          + "      and l3.l_suppkey <> l1.l_suppkey\n" // 不同供应商
          + "      and l3.l_receiptdate > l3.l_commitdate\n" // 其他供应商也延迟
          + "  )\n"
          + "  and s.s_nationkey = n.n_nationkey\n" // 供应商国家键关联
          + "  and n.n_name = 'BRAZIL'\n" // 国家为巴西
          + "group by\n"
          + "  s.s_name\n" // 按供应商名称分组
          + "order by\n"
          + "  numwait desc,\n" // 按等待数量降序
          + "  s.s_name\n" // 按供应商名称
          + "limit 100", // 限制返回 100 条记录

      // 22 // 查询 22：全球销售机会查询，统计特定国家代码中无订单的高价值客户
      "select\n"
          + "  cntrycode,\n" // 国家代码（电话号码前两位）
          + "  count(*) as numcust,\n" // 客户数量
          + "  sum(c_acctbal) as totacctbal\n" // 账户余额总和
          + "from\n"
          + "  (\n" // 子查询：筛选符合条件的客户
          + "    select\n"
          + "      substring(c_phone from 1 for 2) as cntrycode,\n" // 提取电话号码前两位作为国家代码
          + "      c_acctbal\n" // 账户余额
          + "    from\n"
          + "      tpch.customer c\n" // 客户表
          + "    where\n"
          + "      substring(c_phone from 1 for 2) in\n" // 国家代码在指定列表中
          + "        ('24', '31', '11', '16', '21', '20', '34')\n"
          + "      and c_acctbal > (\n" // 账户余额大于子查询的平均值
          + "        select\n"
          + "          avg(c_acctbal)\n" // 平均账户余额
          + "        from\n"
          + "          tpch.customer\n" // 客户表
          + "        where\n"
          + "          c_acctbal > 0.00\n" // 账户余额大于 0
          + "          and substring(c_phone from 1 for 2) in\n" // 国家代码在指定列表中
          + "            ('24', '31', '11', '16', '21', '20', '34')\n"
          + "      )\n"
          + "      and not exists (\n" // 不存在子查询：检查是否有订单
          + "        select\n"
          + "          *\n"
          + "        from\n"
          + "          tpch.orders o\n" // 订单表
          + "        where\n"
          + "          o.o_custkey = c.c_custkey\n" // 客户键关联（无订单）
          + "      )\n"
          + "  ) as custsale\n" // 子查询别名为 custsale
          + "group by\n"
          + "  cntrycode\n" // 按国家代码分组
          + "order by\n"
          + "  cntrycode"}; // 按国家代码排序

  static final List<String> QUERIES = // 静态常量：查询列表，使用 ImmutableList 创建不可变列表
      ImmutableList.copyOf(QUERY_ARRAY); // 从 QUERY_ARRAY 数组创建不可变列表

  @Disabled("it's wasting time") // 禁用此测试，因为浪费时间
  @Test void testRegion() { // 测试方法：测试 region 表的查询
    with() // 获取 CalciteAssert.AssertThat 对象
        .query("select * from tpch.region") // 执行查询：从 tpch.region 表选择所有数据
        .returnsUnordered( // 验证返回结果（不关心顺序）
            "R_REGIONKEY=0; R_NAME=AFRICA; R_COMMENT=lar deposits. blithely final packages cajole. regular waters are final requests. regular accounts are according to ", // 验证地区 0：非洲
            "R_REGIONKEY=1; R_NAME=AMERICA; R_COMMENT=hs use ironic, even requests. s", // 验证地区 1：美洲
            "R_REGIONKEY=2; R_NAME=ASIA; R_COMMENT=ges. thinly even pinto beans ca", // 验证地区 2：亚洲
            "R_REGIONKEY=3; R_NAME=EUROPE; R_COMMENT=ly final courts cajole furiously final excuse", // 验证地区 3：欧洲
            "R_REGIONKEY=4; R_NAME=MIDDLE EAST; R_COMMENT=uickly special accounts cajole carefully blithely close requests. carefully final asymptotes haggle furiousl"); // 验证地区 4：中东
  }

  @Disabled("it's wasting time") // 禁用此测试，因为浪费时间
  @Test void testLineItem() { // 测试方法：测试 lineitem 表的查询
    with() // 获取 CalciteAssert.AssertThat 对象
        .query("select * from tpch.lineitem") // 执行查询：从 tpch.lineitem 表选择所有数据
        .returnsCount(6001215); // 验证返回的行数为 6001215（标准规模下的 lineitem 表行数）
  }

  @Disabled("it's wasting time") // 禁用此测试，因为浪费时间
  @Test void testOrders() { // 测试方法：测试 orders 表的查询
    with() // 获取 CalciteAssert.AssertThat 对象
        .query("select * from tpch.orders") // 执行查询：从 tpch.orders 表选择所有数据
        .returnsCount(1500000); // 验证返回的行数为 1500000（标准规模下的 orders 表行数）
  }

  /** Test case for // 测试用例：针对 JIRA 问题 CALCITE-1543
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1543">[CALCITE-1543]
   * Correlated scalar sub-query with multiple aggregates gives // 相关标量子查询与多个聚合导致 AssertionError
   * AssertionError</a>. */
  @Disabled("planning succeeds, but gives OutOfMemoryError during execution") // 禁用此测试，因为执行时会内存溢出
  @Test void testDecorrelateScalarAggregate() { // 测试方法：测试相关标量子查询的去相关优化
    final String sql = "select sum(l_extendedprice)\n" // SQL 查询：计算满足条件的扩展价格总和
        + "from lineitem, part\n" // 从 lineitem 和 part 表
        + "where\n"
        + "     p_partkey = l_partkey\n" // 零件键关联
        + "     and l_quantity > (\n" // 数量大于子查询的平均数量
        + "       select avg(l_quantity)\n" // 子查询：计算平均数量
        + "       from lineitem\n" // 从 lineitem 表
        + "       where l_partkey = p_partkey\n" // 相同的零件键（相关子查询）
        + "    )\n";
    with().query(sql).runs(); // 执行查询并验证运行成功
  }

  @Disabled("it's wasting time") // 禁用此测试，因为浪费时间
  @Test void testCustomer() { // 测试方法：测试 customer 表的查询
    with() // 获取 CalciteAssert.AssertThat 对象
        .query("select * from tpch.customer") // 执行查询：从 tpch.customer 表选择所有数据
        .returnsCount(150000); // 验证返回的行数为 150000（标准规模下的 customer 表行数）
  }

  private CalciteAssert.AssertThat with() { // 私有方法：获取 CalciteAssert.AssertThat 测试对象
    // Only run on JDK 1.7 or higher. The io.airlift.tpch library requires it. // 仅在 JDK 1.7 或更高版本运行，因为 io.airlift.tpch 库需要它
    return CalciteAssert.model(TPCH_MODEL).enable(ENABLE); // 返回使用 TPCH_MODEL 模型并根据 ENABLE 标志启用/禁用的 AssertThat 对象
  }

  /** Tests the customer table with scale factor 5. */ // 测试规模因子为 5 的 customer 表
  @Disabled("it's wasting time") // 禁用此测试，因为浪费时间
  @Test void testCustomer5() { // 测试方法：测试 tpch_5 schema 的 customer 表（规模因子为 5）
    with() // 获取 CalciteAssert.AssertThat 对象
        .query("select * from tpch_5.customer") // 执行查询：从 tpch_5.customer 表选择所有数据
        .returnsCount(750000); // 验证返回的行数为 750000（规模因子为 5 时的 customer 表行数）
  }

  @Test void testQuery01() { // 测试方法：测试 TPC-H 查询 1
    checkQuery(1); // 调用 checkQuery 方法验证查询 1
  }

  @Test void testQuery02() { // 测试方法：测试 TPC-H 查询 2
    checkQuery(2); // 调用 checkQuery 方法验证查询 2
  }

  @Test void testQuery02Conversion() { // 测试方法：测试查询 2 的转换（验证去相关优化）
    query(2) // 获取查询 2 的 AssertQuery 对象
        .convertMatches(relNode -> { // 验证关系表达式转换结果
          String s = RelOptUtil.toString(relNode); // 将关系表达式转换为字符串
          assertThat(s, not(containsString("Correlator"))); // 断言字符串中不包含 "Correlator"（确保已去相关）
        });
  }

  @Test void testQuery03() { // 测试方法：测试 TPC-H 查询 3
    checkQuery(3); // 调用 checkQuery 方法验证查询 3
  }

  @Test void testQuery04() { // 测试方法：测试 TPC-H 查询 4
    checkQuery(4); // 调用 checkQuery 方法验证查询 4
  }

  @Test void testQuery05() { // 测试方法：测试 TPC-H 查询 5
    checkQuery(5); // 调用 checkQuery 方法验证查询 5
  }

  @Test void testQuery06() { // 测试方法：测试 TPC-H 查询 6
    checkQuery(6); // 调用 checkQuery 方法验证查询 6
  }

  @Test void testQuery07() { // 测试方法：测试 TPC-H 查询 7
    checkQuery(7); // 调用 checkQuery 方法验证查询 7
  }

  @Test void testQuery08() { // 测试方法：测试 TPC-H 查询 8
    checkQuery(8); // 调用 checkQuery 方法验证查询 8
  }

  @Test void testQuery09() { // 测试方法：测试 TPC-H 查询 9
    checkQuery(9); // 调用 checkQuery 方法验证查询 9
  }

  @Test void testQuery10() { // 测试方法：测试 TPC-H 查询 10
    checkQuery(10); // 调用 checkQuery 方法验证查询 10
  }

  @Test void testQuery11() { // 测试方法：测试 TPC-H 查询 11
    checkQuery(11); // 调用 checkQuery 方法验证查询 11
  }

  @Test void testQuery12() { // 测试方法：测试 TPC-H 查询 12
    checkQuery(12); // 调用 checkQuery 方法验证查询 12
  }

  @Test void testQuery13() { // 测试方法：测试 TPC-H 查询 13
    checkQuery(13); // 调用 checkQuery 方法验证查询 13
  }

  @Test void testQuery14() { // 测试方法：测试 TPC-H 查询 14
    checkQuery(14); // 调用 checkQuery 方法验证查询 14
  }

  @Test void testQuery15() { // 测试方法：测试 TPC-H 查询 15
    checkQuery(15); // 调用 checkQuery 方法验证查询 15
  }

  @Test void testQuery16() { // 测试方法：测试 TPC-H 查询 16
    checkQuery(16); // 调用 checkQuery 方法验证查询 16
  }

  @Test void testQuery17() { // 测试方法：测试 TPC-H 查询 17
    checkQuery(17); // 调用 checkQuery 方法验证查询 17
  }

  @Test void testQuery18() { // 测试方法：测试 TPC-H 查询 18
    checkQuery(18); // 调用 checkQuery 方法验证查询 18
  }

  // a bit slow
  @Timeout(value = 10, unit = TimeUnit.MINUTES) // 设置超时时间为 10 分钟
  @Disabled("Too slow, more than 5 min") // 禁用此测试，因为太慢（超过 5 分钟）
  @Test void testQuery19() { // 测试方法：测试 TPC-H 查询 19
    checkQuery(19); // 调用 checkQuery 方法验证查询 19
  }

  @Test void testQuery20() { // 测试方法：测试 TPC-H 查询 20
    checkQuery(20); // 调用 checkQuery 方法验证查询 20
  }

  @Test void testQuery21() { // 测试方法：测试 TPC-H 查询 21
    checkQuery(21); // 调用 checkQuery 方法验证查询 21
  }

  @Test void testQuery22() { // 测试方法：测试 TPC-H 查询 22
    checkQuery(22); // 调用 checkQuery 方法验证查询 22
  }

  private void checkQuery(int i) { // 私有方法：检查指定的查询是否能成功运行
    query(i).runs(); // 调用 query 方法获取 AssertQuery 对象，然后调用 runs() 验证查询运行成功
  }

  /** Runs with query #i. // 运行第 i 个查询
   *
   * @param i Ordinal of query, per the benchmark, 1-based */ // 参数 i：查询的序号，基于基准测试，从 1 开始
  private CalciteAssert.AssertQuery query(int i) { // 私有方法：获取指定查询的 AssertQuery 对象
    return with() // 获取 CalciteAssert.AssertThat 对象
        .query(QUERIES.get(i - 1).replace("tpch.", "tpch_01.")); // 获取查询 SQL（索引为 i-1，因为数组从 0 开始），并将 tpch. 替换为 tpch_01.（使用小规模数据）
  }
} // TpchTest 类结束
