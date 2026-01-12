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
// Apache Calcite是一个动态数据管理框架，提供SQL解析、优化、执行等功能
// 本文件是Apache Calcite项目的一部分，遵循Apache 2.0许可证
package org.apache.calcite.materialize; // 物化视图相关的包，包含Lattice（立方体）建议器等核心功能

import org.apache.calcite.adapter.tpcds.TpcdsSchema; // TPC-DS数据集的Schema适配器，TPC-DS是决策支持基准测试数据集
import org.apache.calcite.config.CalciteConnectionConfig; // Calcite连接配置，用于配置Calcite连接的各种参数
import org.apache.calcite.config.CalciteConnectionProperty; // Calcite连接属性枚举，定义可配置的连接属性
import org.apache.calcite.config.CalciteSystemProperty; // Calcite系统属性，用于访问系统级别的配置
import org.apache.calcite.plan.Contexts; // 上下文工具类，用于创建和管理Calcite的上下文环境
import org.apache.calcite.prepare.PlannerImpl; // Planner接口的实现类，负责SQL查询的解析、验证和转换
import org.apache.calcite.rel.RelRoot; // 关系代数表达式的根节点，包含查询的优化关系表达式树
import org.apache.calcite.schema.SchemaPlus; // Schema的扩展接口，提供了添加子Schema等功能
import org.apache.calcite.sql.SqlNode; // SQL抽象语法树（AST）的节点接口，表示SQL语句的各种元素
import org.apache.calcite.sql.parser.SqlParseException; // SQL解析异常，当SQL语法错误时抛出
import org.apache.calcite.sql.parser.SqlParser; // SQL解析器，负责将SQL文本解析为SqlNode对象
import org.apache.calcite.sql.validate.SqlConformanceEnum; // SQL兼容性枚举，定义不同级别的SQL标准兼容性
import org.apache.calcite.test.CalciteAssert; // Calcite测试工具类，提供断言和测试辅助功能
import org.apache.calcite.tools.FrameworkConfig; // Calcite框架配置接口，定义框架的各种配置选项
import org.apache.calcite.tools.Frameworks; // 框架工具类，用于创建和配置Calcite框架
import org.apache.calcite.tools.Planner; // Planner接口，定义查询规划器的核心方法
import org.apache.calcite.tools.RelConversionException; // 关系转换异常，当SQL转换为关系代数失败时抛出
import org.apache.calcite.tools.ValidationException; // 验证异常，当SQL语义验证失败时抛出

import net.hydromatic.tpcds.query.Query; // TPC-DS查询类，代表TPC-DS基准测试中的各种查询

import org.junit.jupiter.api.Disabled; // JUnit 5的注解，用于禁用测试方法
import org.junit.jupiter.api.Test; // JUnit 5的注解，标记测试方法

import java.util.List; // Java集合框架的List接口
import java.util.Random; // Java随机数生成器
import java.util.regex.Pattern; // Java正则表达式模式类

import static org.hamcrest.MatcherAssert.assertThat; // Hamcrest断言工具，用于编写更灵活的断言
import static org.hamcrest.Matchers.aMapWithSize; // Hamcrest匹配器，检查Map的大小
import static org.hamcrest.Matchers.hasSize; // Hamcrest匹配器，检查集合的大小
import static org.hamcrest.Matchers.hasToString; // Hamcrest匹配器，检查对象的toString方法输出

/**
 * Unit tests for {@link LatticeSuggester}.
 * LatticeSuggester的单元测试类
 * 
 * Lattice（立方体）是Calcite中物化视图和聚合查询的核心概念：
 * - Lattice是一个多维数据结构，包含事实表和维度表
 * - LatticeSuggester负责分析查询模式，自动建议合适的Lattice结构
 * - 通过Lattice可以加速OLAP查询，提高查询性能
 * 
 * 本测试类使用TPC-DS（决策支持基准测试）数据集来测试LatticeSuggester：
 * - TPC-DS包含99个复杂的SQL查询，模拟真实的商业智能场景
 * - 测试LatticeSuggester能否从这些查询中提取出合适的Lattice结构
 * - 验证Lattice的图结构、节点映射、路径映射等是否正确
 * 
 * 主要测试内容：
 * 1. 测试LatticeSuggester对TPC-DS查询的分析能力
 * 2. 验证生成的Lattice图结构是否正确
 * 3. 测试Lattice的演化（evolve）功能
 * 4. 验证Lattice节点、路径的映射关系
 */
class TpcdsLatticeSuggesterTest { // 测试类，用于测试LatticeSuggester在TPC-DS数据集上的表现

  // 私有辅助方法：为SQL查询字符串添加行号
  // 参数：s - SQL查询字符串，可能包含多行
  // 返回：添加了行号的SQL字符串，每行前都有行号
  // 用途：在调试时方便查看SQL的行号，快速定位问题
  private String number(String s) { // 接收SQL字符串，返回带行号的字符串
    final StringBuilder b = new StringBuilder(); // 创建StringBuilder用于构建结果字符串
    int i = 0; // 初始化行号计数器
    for (String line : s.split("\n")) { // 按换行符分割SQL字符串，逐行处理
      b.append(++i).append(' ').append(line).append("\n"); // 追加行号、空格、原行内容和换行符
    }
    return b.toString(); // 返回带行号的完整SQL字符串
  }

  // 核心测试方法：检查所有TPC-DS查询的Lattice建议结果
  // 参数：evolve - 是否启用Lattice演化功能，演化功能可以动态调整Lattice结构
  // 返回：无
  // 异常：可能抛出SQL解析、验证或转换异常
  // 
  // 方法功能详解：
  // 1. 创建Tester实例，配置TPC-DS Schema和演化选项
  // 2. 遍历TPC-DS的前11个查询（跳过有问题的查询6和9）
  // 3. 对每个查询进行SQL语法转换以适配Calcite
  // 4. 将转换后的查询添加到LatticeSuggester中进行分析
  // 5. 验证生成的Lattice图结构是否符合预期
  // 6. 根据是否启用演化功能，验证不同的数据结构大小
  //
  // Lattice图结构说明：
  // - vertices（顶点）：代表数据库表，包括事实表和维度表
  // - edges（边）：代表表之间的连接关系，通过外键连接
  // - 每条边记录了源表、目标表和连接键
  private void checkFoodMartAll(boolean evolve) throws Exception { // 主测试方法，evolve参数控制是否启用Lattice演化
    final Tester t = new Tester().tpcds().withEvolve(evolve); // 创建测试器，配置TPC-DS数据源和演化选项
    final Pattern pattern = // 创建正则表达式模式，用于匹配substr函数
        Pattern.compile("substr\\(([^,]*),([^,]*),([^)]*)\\)"); // 匹配格式：substr(参数1,参数2,参数3)
    for (Query query : Query.values()) { // 遍历所有TPC-DS查询（共99个查询）
      final String sql0 = query.sql(new Random(0)) // 使用固定随机种子生成SQL，确保结果可重现
          .replace("as returns", "as \"returns\"") // 替换：returns是关键字，需要加引号
          .replace("sum(returns)", "sum(\"returns\")") // 替换：sum中的returns也需要加引号
          .replace(", returns", ", \"returns\"") // 替换：逗号后的returns也需要加引号
          .replace("14 days", "interval '14' day"); // 替换：日期间隔语法适配Calcite
      final String sql = // 应用正则表达式替换，将substr函数转换为substring函数
          pattern.matcher(sql0).replaceAll("substring($1 from $2 for $3)"); // substr(a,b,c) -> substring(a from b for c)
      if (CalciteSystemProperty.DEBUG.value()) { // 如果开启了DEBUG系统属性
        System.out.println("Query #" + query.id + "\n" // 输出查询编号
            + number(sql)); // 输出带行号的SQL内容
      }
      switch (query.id) { // 根据查询ID进行分支处理
      case 6: // 查询6
      case 9: // 查询9
        continue; // 跳过这两个查询，因为它们会抛出空指针异常（NPE）
      }
      if (query.id > 11) { // 如果查询ID大于11
        break; // 停止处理，只测试前11个查询
      }
      t.addQuery(sql); // 将SQL查询添加到测试器中，触发Lattice分析
    }

    // The graph of all tables and hops
    // 预期的Lattice图结构字符串，包含顶点（表）和边（连接关系）
    // 顶点包括：CATALOG_SALES（目录销售）、CUSTOMER（客户）、CUSTOMER_ADDRESS（客户地址）等11个表
    // 边描述了表之间的连接关系，例如：CATALOG_SALES通过CS_SHIP_CUSTOMER_SK连接到CUSTOMER
    final String expected = "graph(vertices: [" // 开始定义顶点列表
        + "[tpcds, CATALOG_SALES], " // 目录销售事实表
        + "[tpcds, CUSTOMER], " // 客户维度表
        + "[tpcds, CUSTOMER_ADDRESS], " // 客户地址维度表
        + "[tpcds, CUSTOMER_DEMOGRAPHICS], " // 客户人口统计维度表
        + "[tpcds, DATE_DIM], " // 日期维度表
        + "[tpcds, ITEM], " // 商品维度表
        + "[tpcds, PROMOTION], " // 促销维度表
        + "[tpcds, STORE], " // 商店维度表
        + "[tpcds, STORE_RETURNS], " // 商店退货事实表
        + "[tpcds, STORE_SALES], " // 商店销售事实表
        + "[tpcds, WEB_SALES]], " // 网页销售事实表
        + "edges: " // 开始定义边（连接关系）
        + "[Step([tpcds, CATALOG_SALES], [tpcds, CUSTOMER], CS_SHIP_CUSTOMER_SK:C_CUSTOMER_SK)," // 目录销售 -> 客户
        + " Step([tpcds, CATALOG_SALES], [tpcds, DATE_DIM], CS_SOLD_DATE_SK:D_DATE_SK)," // 目录销售 -> 日期
        + " Step([tpcds, STORE_RETURNS], [tpcds, CUSTOMER], SR_CUSTOMER_SK:C_CUSTOMER_SK)," // 商店退货 -> 客户
        + " Step([tpcds, STORE_RETURNS], [tpcds, DATE_DIM], SR_RETURNED_DATE_SK:D_DATE_SK)," // 商店退货 -> 日期
        + " Step([tpcds, STORE_RETURNS], [tpcds, STORE], SR_STORE_SK:S_STORE_SK)," // 商店退货 -> 商店
        + " Step([tpcds, STORE_RETURNS], [tpcds, STORE_RETURNS], SR_STORE_SK:SR_STORE_SK)," // 商店退货自连接
        + " Step([tpcds, STORE_SALES], [tpcds, CUSTOMER], SS_CUSTOMER_SK:C_CUSTOMER_SK)," // 商店销售 -> 客户
        + " Step([tpcds, STORE_SALES], [tpcds, CUSTOMER_DEMOGRAPHICS], SS_CDEMO_SK:CD_DEMO_SK)," // 商店销售 -> 客户人口统计
        + " Step([tpcds, STORE_SALES], [tpcds, DATE_DIM], SS_SOLD_DATE_SK:D_DATE_SK)," // 商店销售 -> 日期
        + " Step([tpcds, STORE_SALES], [tpcds, ITEM], SS_ITEM_SK:I_ITEM_SK)," // 商店销售 -> 商品
        + " Step([tpcds, STORE_SALES], [tpcds, PROMOTION], SS_PROMO_SK:P_PROMO_SK)," // 商店销售 -> 促销
        + " Step([tpcds, WEB_SALES], [tpcds, CUSTOMER], WS_BILL_CUSTOMER_SK:C_CUSTOMER_SK)," // 网页销售 -> 客户
        + " Step([tpcds, WEB_SALES], [tpcds, DATE_DIM], WS_SOLD_DATE_SK:D_DATE_SK)])"; // 网页销售 -> 日期
    assertThat(t.suggester.space.g, hasToString(expected)); // 断言：验证生成的图结构与预期一致
    if (evolve) { // 如果启用了演化功能
      assertThat(t.suggester.space.nodeMap, aMapWithSize(5)); // 断言：节点映射包含5个节点
      assertThat(t.suggester.latticeMap, aMapWithSize(3)); // 断言：Lattice映射包含3个Lattice
      assertThat(t.suggester.space.pathMap, aMapWithSize(10)); // 断言：路径映射包含10条路径
    } else { // 如果未启用演化功能
      assertThat(t.suggester.space.nodeMap, aMapWithSize(5)); // 断言：节点映射包含5个节点
      assertThat(t.suggester.latticeMap, aMapWithSize(4)); // 断言：Lattice映射包含4个Lattice（比演化模式多1个）
      assertThat(t.suggester.space.pathMap, aMapWithSize(10)); // 断言：路径映射包含10条路径
    }
  }

  // 测试方法：测试所有TPC-DS查询的Lattice建议（不启用演化）
  // 注解@Disabled：禁用此测试，因为在Maven和Gradle环境下都会抛出NPE
  // 原因：某些TPC-DS查询（如查询6和9）在解析或分析时会触发空指针异常
  // 解决方案：需要修复LatticeSuggester中的空指针问题
  @Disabled("Throws NPE with both Maven and Gradle") // 禁用标记，说明在Maven和Gradle构建中会抛出NPE
  @Test void testTpcdsAll() throws Exception { // 测试方法：测试不启用演化时的Lattice建议
    checkFoodMartAll(false); // 调用核心测试方法，传入false表示不启用演化
  } // 测试方法结束

  // 测试方法：测试所有TPC-DS查询的Lattice建议（启用演化）
  // 注解@Disabled：禁用此测试，因为在Maven和Gradle环境下都会抛出NPE
  // 演化功能说明：
  // - Lattice演化允许动态调整Lattice结构，优化查询性能
  // - 启用演化后，LatticeSuggester会根据查询模式动态合并或拆分Lattice
  // - 演化模式下的Lattice数量可能比非演化模式少（3个 vs 4个）
  @Disabled("Throws NPE with both Maven and Gradle") // 禁用标记，说明在Maven和Gradle构建中会抛出NPE
  @Test void testTpcdsAllEvolve() throws Exception { // 测试方法：测试启用演化时的Lattice建议
    checkFoodMartAll(true); // 调用核心测试方法，传入true表示启用演化
  } // 测试方法结束

  /** Test helper. */
  // 测试辅助类：封装测试所需的配置和操作
  // 
  // 类作用：
  // - 提供统一的测试环境配置
  // - 封装LatticeSuggester的创建和使用
  // - 提供便捷的方法添加查询和获取结果
  // - 支持不同的Schema配置（TPC-DS、空白Schema等）
  // - 支持Lattice演化功能的开关
  //
  // 设计模式：Builder模式，通过链式调用配置测试环境
  private static class Tester { // 静态内部类，用于辅助测试
    final LatticeSuggester suggester; // Lattice建议器实例，核心对象，负责分析查询并建议Lattice结构
    private final FrameworkConfig config; // Calcite框架配置，包含解析器、Schema、上下文等配置信息

    // 默认构造方法：创建使用空白Schema的测试器
    // 调用链：构造方法 -> config() -> build() -> 私有构造方法
    // 用途：用于创建基础测试环境，不包含任何表
    Tester() { // 默认构造方法
      this(config(CalciteAssert.SchemaSpec.BLANK).build()); // 调用私有构造方法，传入空白Schema的配置
    } // 构造方法结束

    // 私有构造方法：使用指定配置创建测试器
    // 参数：config - Calcite框架配置对象
    // 功能：初始化LatticeSuggester，准备接收查询进行分析
    private Tester(FrameworkConfig config) { // 私有构造方法，接收框架配置
      this.config = config; // 保存框架配置
      suggester = new LatticeSuggester(config); // 创建LatticeSuggester实例，传入配置
    } // 构造方法结束

    // 方法：配置TPC-DS Schema
    // 返回：新的Tester实例，配置了TPC-DS数据源
    // 
    // TPC-DS说明：
    // - TPC-DS是决策支持基准测试数据集
    // - 包含24个表，模拟真实的零售业务场景
    // - scaleFactor = 0.01表示使用1%的数据规模（约1GB）
    // - LENIENT兼容性模式：宽松的SQL语法检查
    //
    // 配置步骤：
    // 1. 创建根Schema
    // 2. 添加TPC-DS Schema到根Schema
    // 3. 配置解析器和兼容性
    // 4. 设置默认Schema为TPC-DS
    Tester tpcds() { // 配置TPC-DS数据源的方法
      final SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根Schema，true表示启用缓存
      final double scaleFactor = 0.01d; // 设置数据规模因子，0.01表示1%的数据量
      final SchemaPlus schema = // 创建TPC-DS Schema
          rootSchema.add("tpcds", new TpcdsSchema(scaleFactor)); // 将TPC-DS Schema添加到根Schema，命名为"tpcds"
      final FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置构建器
          .parserConfig(SqlParser.Config.DEFAULT) // 设置默认的SQL解析器配置
          .context( // 设置上下文
              Contexts.of( // 创建上下文对象
                  CalciteConnectionConfig.DEFAULT // 使用默认连接配置
                      .set(CalciteConnectionProperty.CONFORMANCE, // 设置SQL兼容性属性
                          SqlConformanceEnum.LENIENT.name()))) // 使用LENIENT（宽松）兼容性模式
          .defaultSchema(schema) // 设置默认Schema为TPC-DS Schema
          .build(); // 构建配置对象
      return withConfig(config); // 返回使用新配置的Tester实例
    } // 方法结束

    // 方法：使用指定配置创建新的Tester实例
    // 参数：config - Calcite框架配置
    // 返回：新的Tester实例
    // 用途：支持链式调用，灵活配置测试环境
    Tester withConfig(FrameworkConfig config) { // 使用指定配置创建新Tester的方法
      return new Tester(config); // 返回使用指定配置的新Tester实例
    } // 方法结束

    // 方法：添加查询到LatticeSuggester进行分析
    // 参数：q - SQL查询字符串
    // 返回：分析得到的Lattice列表
    // 异常：可能抛出SQL解析异常、验证异常或关系转换异常
    //
    // 处理流程：
    // 1. 创建Planner实例用于处理SQL
    // 2. 解析SQL为SqlNode（抽象语法树）
    // 3. 验证SqlNode的语义正确性
    // 4. 将SqlNode转换为RelRoot（关系代数根节点）
    // 5. 提取投影部分，添加到LatticeSuggester进行分析
    //
    // LatticeSuggester分析过程：
    // - 识别查询中的表和连接关系
    // - 构建Lattice图结构
    // - 识别度量（聚合函数）和维度
    // - 建议合适的Lattice结构
    List<Lattice> addQuery(String q) throws SqlParseException, // 添加查询方法，可能抛出SQL解析异常
        ValidationException, RelConversionException { // 可能抛出验证异常或关系转换异常
      final Planner planner = new PlannerImpl(config); // 创建Planner实现实例，传入配置
      final SqlNode node = planner.parse(q); // 解析SQL字符串为SqlNode（抽象语法树）
      final SqlNode node2 = planner.validate(node); // 验证SqlNode的语义，确保SQL语法正确
      final RelRoot root = planner.rel(node2); // 将验证后的SqlNode转换为RelRoot（关系代数表达式）
      return suggester.addQuery(root.project()); // 提取投影部分，添加到LatticeSuggester，返回生成的Lattice列表
    } // 方法结束

    /** Parses a query returns its graph. */
    // 方法：解析查询并返回其Lattice根节点
    // 参数：q - SQL查询字符串
    // 返回：Lattice根节点
    // 异常：可能抛出SQL解析异常、验证异常或关系转换异常
    //
    // 功能说明：
    // - 解析单个查询，期望生成一个Lattice
    // - 断言确保只生成了一个Lattice
    // - 返回该Lattice的根节点
    //
    // LatticeRootNode说明：
    // - LatticeRootNode是Lattice图的根节点
    // - 包含Lattice的完整结构信息
    // - 可以用于遍历整个Lattice图
    LatticeRootNode node(String q) throws SqlParseException, // 解析查询并返回Lattice根节点的方法
        ValidationException, RelConversionException { // 可能抛出验证异常或关系转换异常
      final List<Lattice> list = addQuery(q); // 调用addQuery方法，获取生成的Lattice列表
      assertThat(list, hasSize(1)); // 断言：确保列表中只有一个Lattice
      return list.get(0).rootNode; // 返回第一个Lattice的根节点
    } // 方法结束

    // 静态方法：创建框架配置构建器
    // 参数：spec - Schema规范，定义要使用的Schema类型
    // 返回：框架配置构建器
    //
    // SchemaSpec说明：
    // - BLANK：空白Schema，不包含任何表
    // - 其他规范：可以包含预定义的测试表
    //
    // 配置项：
    // - parserConfig：SQL解析器配置
    // - defaultSchema：默认Schema
    static Frameworks.ConfigBuilder config(CalciteAssert.SchemaSpec spec) { // 创建框架配置的静态方法
      final SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根Schema，启用缓存
      final SchemaPlus schema = CalciteAssert.addSchema(rootSchema, spec); // 根据规范添加Schema到根Schema
      return Frameworks.newConfigBuilder() // 创建框架配置构建器
          .parserConfig(SqlParser.Config.DEFAULT) // 设置默认解析器配置
          .defaultSchema(schema); // 设置默认Schema
    } // 方法结束

    // 方法：设置Lattice演化选项
    // 参数：evolve - 是否启用演化功能
    // 返回：新的Tester实例，配置了指定的演化选项
    //
    // Lattice演化说明：
    // - 启用演化：LatticeSuggester会动态调整Lattice结构，优化查询性能
    // - 禁用演化：LatticeSuggester保持静态的Lattice结构
    // - 演化功能可以合并相似的Lattice，减少物化视图的数量
    //
    // 优化逻辑：
    // - 如果当前配置已经符合要求，直接返回this（避免不必要的对象创建）
    // - 否则创建新的配置并返回新的Tester实例
    Tester withEvolve(boolean evolve) { // 设置Lattice演化选项的方法
      if (evolve == config.isEvolveLattice()) { // 如果当前配置的演化设置与请求的一致
        return this; // 直接返回当前实例，无需重新创建
      } // 条件判断结束
      final Frameworks.ConfigBuilder configBuilder = // 创建配置构建器
          Frameworks.newConfigBuilder(config); // 基于当前配置创建构建器
      return new Tester(configBuilder.evolveLattice(true).build()); // 启用演化并构建新配置，返回新Tester实例
    } // 方法结束
  } // Tester类结束
} // TpcdsLatticeSuggesterTest类结束
