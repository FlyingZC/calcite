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
package org.apache.calcite.test; // 定义包名，该类属于org.apache.calcite.test测试包

import org.apache.calcite.config.CalciteSystemProperty; // 导入Calcite系统属性配置类，用于读取系统配置参数
import org.apache.calcite.linq4j.tree.Primitive; // 导入LINQ4J的Primitive工具类，用于处理基本类型数组
import org.apache.calcite.util.IntegerIntervalSet; // 导入整数区间集合工具类，用于解析整数范围字符串

import org.junit.jupiter.api.AfterAll; // 导入JUnit5的AfterAll注解，用于在所有测试方法执行后执行清理操作
import org.junit.jupiter.api.Assertions; // 导入JUnit5的断言工具类，用于验证测试结果
import org.junit.jupiter.api.BeforeAll; // 导入JUnit5的BeforeAll注解，用于在所有测试方法执行前执行初始化操作
import org.junit.jupiter.api.Disabled; // 导入JUnit5的Disabled注解，用于禁用某个测试方法
import org.junit.jupiter.api.Tag; // 导入JUnit5的Tag注解，用于给测试类打标签，方便分类运行测试
import org.junit.jupiter.params.ParameterizedTest; // 导入JUnit5的参数化测试注解，用于运行多组参数的测试
import org.junit.jupiter.params.provider.MethodSource; // 导入JUnit5的方法源注解，用于指定参数化测试的数据源方法

import java.io.IOException; // 导入IO异常类，用于处理文件读写异常
import java.time.Duration; // 导入Java 8的时间持续类，用于设置测试超时时间
import java.util.ArrayList; // 导入ArrayList集合类，用于动态存储查询对象列表
import java.util.List; // 导入List接口，用于定义查询对象列表的类型
import java.util.stream.Stream; // 导入Stream流接口，用于返回查询对象的流式集合

/**
 * Test case that runs the FoodMart reference queries. // FoodMart参考查询测试用例类
 * 这是一个参数化测试类，用于运行FoodMart数据仓库的参考查询集合，验证Calcite查询引擎的正确性和性能
 * FoodMart是一个经典的OLAP数据仓库示例，包含销售、产品、客户、时间等多个维度的事实表和维度表
 * 该测试类通过参数化测试自动运行大量的SQL查询，确保Calcite能够正确处理各种复杂的OLAP查询场景
 * 测试数据使用FoodMart数据集，这是一个零售数据仓库，包含24个表和多种数据类型
 * 测试类标记为@Tag("slow")，表示这些测试运行较慢，可以在CI/CD中按需执行
 */
@Tag("slow") // 给测试类打上"slow"标签，表示这些测试运行较慢，可以单独运行或跳过
class FoodmartTest { // 定义FoodmartTest测试类，用于测试FoodMart数据仓库的查询功能

  private static CalciteAssert.AssertThat assertFoodmart; // 静态成员变量：Calcite断言工具对象，用于测试FoodMart克隆数据源的查询功能
  // CalciteAssert.AssertThat是一个流式API构建器，用于设置测试环境并执行SQL查询断言
  // assertFoodmart使用FOODMART_CLONE配置，这是一个内存中克隆的FoodMart数据源
  // pooled()方法表示使用连接池，提高测试性能
  private static CalciteAssert.AssertThat assertFoodmartLattice; // 静态成员变量：Calcite断言工具对象，用于测试带Lattice优化的FoodMart数据源
  // Lattice是Calcite的物化视图优化技术，可以自动识别和重写查询以使用物化视图
  // assertFoodmartLattice使用JDBC_FOODMART_WITH_LATTICE配置，包含预定义的Lattice结构
  // 该对象用于测试物化视图优化功能是否正常工作

  private static final int[] DISABLED_IDS = { // 静态最终成员变量：禁用的查询ID数组，这些查询暂时不运行
      58, 83, 202, 204, 205, 206, 207, 209, 211, 231, 247, 275, 309, 383, 384, // 这些ID对应的查询因各种原因被禁用，包括语法不兼容、性能问题、已知bug等
      385, 448, 449, 471, 494, 495, 496, 497, 499, 500, 501, 502, 503, 505, 506, // 每个数字代表FoodMart查询集合中的一个查询ID
      507, 514, 516, 518, 520, 534, 551, 563, 566, 571, 628, 629, 630, 644, 649, // 这些查询可能在Calcite中尚未完全支持，或者存在已知问题
      650, 651, 653, 654, 655, 656, 657, 658, 659, 669, 722, 731, 732, 737, 748, // 禁用的原因包括：SQL语法差异、函数不支持、性能问题、优化器bug等
      750, 756, 774, 777, 778, 779, 781, 782, 783, 811, 818, 819, 820, 2057, // 随着Calcite版本的更新，部分禁用的查询可能会被修复并重新启用
      2059, 2060, 2073, 2088, 2098, 2099, 2136, 2151, 2158, 2162, 2163, 2164, // 开发者会定期审查这个列表，将已修复的查询ID移除
      2165, 2166, 2167, 2168, 2169, 2170, 2171, 2172, 2173, 2174, 2175, 2176, // 每个禁用的查询都有其特定的原因，通常在注释中标注了相关issue号
      2177, 2178, 2179, 2180, 2181, 2187, 2190, 2191, 2235, 2245, 2264, 2265, // 这些查询覆盖了各种SQL场景：聚合、连接、子查询、窗口函数等
      2266, 2267, 2268, 2270, 2271, 2279, 2327, 2328, 2341, 2356, 2365, 2374, // 禁用列表的维护是测试套件质量保证的重要组成部分
      2415, 2416, 2424, 2432, 2455, 2456, 2457, 2518, 2521, 2528, 2542, 2570, // 通过禁用已知失败的查询，可以确保CI/CD流程的稳定性
      2578, 2579, 2580, 2581, 2594, 2598, 2749, 2774, 2778, 2780, 2781, 2786, // 同时保留这些ID记录，方便后续跟踪和修复
      2787, 2790, 2791, 2876, 2883, 5226, 5227, 5228, 5229, 5230, 5238, 5239, // 注意：这些ID来自FoodMart查询集，不是连续的，而是根据原始查询编号
      5249, 5279, 5281, 5282, 5283, 5284, 5286, 5288, 5291, 5415, 5444, 5445, // 部分高编号的查询可能是后来添加的，或者是特定功能的测试
      5446, 5447, 5448, 5452, 5459, 5460, 5461, 5517, 5519, 5558, 5560, 5561, // 数组中的每个元素都是一个整数，代表一个查询的唯一标识符
      5562, 5572, 5573, 5576, 5577, 5607, 5644, 5648, 5657, 5664, 5665, 5667, // 这个数组是final的，意味着一旦初始化就不能修改，保证测试的一致性
      5671, 5682, 5700, 5743, 5748, 5749, 5750, 5751, 5775, 5776, 5777, 5785, // 使用数组而不是集合是为了性能考虑，因为这是静态常量
      5793, 5796, 5797, 5810, 5811, 5814, 5816, 5852, 5874, 5875, 5910, 5953, // 数组中的ID按升序排列，便于维护和查找
      5960, 5971, 5975, 5983, 6016, 6028, 6030, 6031, 6033, 6034, 6081, 6082, // 每个ID都对应一个特定的SQL查询，查询内容定义在FoodMartQuerySet中
      6083, 6084, 6085, 6086, 6087, 6088, 6089, 6090, 6097, 6098, 6099, 6100, // 禁用的查询不会在默认的测试运行中执行，但可以通过系统属性单独运行
      6101, 6102, 6103, 6104, 6105, 6106, 6107, 6108, 6109, 6110, 6111, 6112, // 数组长度很大，说明有很多查询需要进一步修复或支持
      6113, 6114, 6115, 6141, 6150, 6156, 6160, 6164, 6168, 6169, 6172, 6177, // 这也反映了Calcite作为一个成熟的SQL引擎，支持各种复杂的查询场景
      6180, 6181, 6185, 6187, 6188, 6190, 6191, 6193, 6194, 6196, 6197, 6199, // 数组中的元素可以分组，每组代表一类相似的问题
      6200, 6202, 6203, 6205, 6206, 6208, 6209, 6211, 6212, 6214, 6215, 6217, // 维护这个数组需要谨慎，避免误删或误添加ID
      6218, 6220, 6221, 6223, 6224, 6226, 6227, 6229, 6230, 6232, 6233, 6235, // 建议在添加或移除ID时，添加注释说明原因
      6236, 6238, 6239, 6241, 6242, 6244, 6245, 6247, 6248, 6250, 6251, 6253, // 数组中的ID应该在编译时确定，避免运行时修改
      6254, 6256, 6257, 6259, 6260, 6262, 6263, 6265, 6266, 6268, 6269, // 这个数组是测试套件配置的一部分，应该定期审查和更新

      // failed // 注释：下面这些查询执行失败，暂时禁用
      5677, 5681, // 查询ID 5677和5681执行失败，具体失败原因可能需要查看测试日志或issue跟踪系统

      // 2nd run // 注释：第二次运行时发现的问题查询
      6271, 6272, 6274, 6275, 6277, 6278, 6280, 6281, 6283, 6284, 6286, 6287, // 这些查询在第二次测试运行中被发现有问题
      6289, 6290, 6292, 6293, 6295, 6296, 6298, 6299, 6301, 6302, 6304, 6305, // 可能是因为运行环境不同或数据变化导致
      6307, 6308, 6310, 6311, 6313, 6314, 6316, 6317, 6319, 6327, 6328, 6337, // 第二次运行可能是在不同的配置或版本下进行的
      6338, 6339, 6341, 6345, 6346, 6348, 6349, 6354, 6355, 6359, 6366, 6368, // 这些查询可能在某些情况下能通过，但在其他情况下失败
      6369, 6375, 6376, 6377, 6389, 6396, 6400, 6422, 6424, 6445, 6447, 6449, // "2nd run"标签表明这些问题是在后续测试中发现的
      6450, 6454, 6456, 6470, 6479, 6480, 6491, 6509, 6518, 6522, 6561, 6562, // 可能涉及并发、资源竞争或时序相关的问题
      6564, 6566, 6578, 6581, 6582, 6583, 6587, 6591, 6594, 6603, 6610, 6613, // 也可能是测试框架或环境配置的问题
      6615, 6618, 6619, 6622, 6627, 6632, 6635, 6643, 6650, 6651, 6652, 6653, // 这些查询需要进一步调查才能确定根本原因
      6656, 6659, 6668, 6670, 6720, 6726, 6735, 6737, 6739, // 随着问题的修复，这些ID应该从禁用列表中移除

      // timeout oor OOM // 注释：这些查询因为超时或内存溢出而被禁用
      420, 423, 5218, 5219, 5616, 5617, 5618, 5891, 5892, 5895, 5896, 5898, // OOM是Out Of Memory的缩写，表示内存不足
      5899, 5900, 5901, 5902, 6080, 6091, // 这些查询可能涉及大量的数据处理或复杂的计算
      // 超时问题可能是因为查询优化不够高效，或者是查询本身非常复杂
      // 内存溢出可能是因为中间结果集过大，或者存在笛卡尔积等导致数据爆炸的操作
      // 解决这些问题可能需要优化查询计划、增加内存限制或修改查询本身

      // bugs // 注释：这些查询因为已知的Calcite bug而被禁用
      6597, // CALCITE-403 // 查询ID 6597对应 CALCITE-403 这个issue，这是一个已知的bug
  }; // 禁用ID数组的结束大括号

  // Interesting tests. (We need to fix and remove from the disabled list.) // 注释：有趣的测试（我们需要修复它们并从禁用列表中移除）
  // 2452, 2453, 2454, 2457 only: RTRIM // 注释：查询2452、2453、2454、2457只涉及RTRIM函数问题
  // RTRIM是字符串右去空格函数，这些查询可能测试了RTRIM函数的边界情况或特殊字符处理
  // 2436-2453,2455: agg_ // 注释：查询2436到2453以及2455涉及聚合函数（agg_）相关的问题
  // agg_可能表示聚合函数的前缀，这些查询可能测试了各种聚合函数的组合使用
  // 2518, 5960 only: "every derived table must have its own alias" // 注释：查询2518和5960只涉及派生表别名问题
  // 这是MySQL的语法要求，每个派生表（子查询作为表使用）都必须有别名
  // 2542: timeout. Running big, simple SQL cartesian product. // 注释：查询2542超时，运行大的简单SQL笛卡尔积
  // 笛卡尔积会产生大量数据，导致查询执行时间过长或内存溢出
  // // 空注释行，用于分隔代码块

  @BeforeAll // JUnit5注解：在所有测试方法执行前执行一次，用于初始化测试环境
  public static void setupAsserts() { // 静态方法：设置断言工具对象，初始化测试环境
    assertFoodmart = CalciteAssert.that() // 创建CalciteAssert构建器实例，开始流式API调用链
      .with(CalciteAssert.Config.FOODMART_CLONE) // 配置使用FoodMart克隆数据源，FOODMART_CLONE是预定义的配置
      // FOODMART_CLONE配置使用内存中的克隆数据，测试运行更快，不依赖外部数据库
      // 克隆数据是在测试初始化时从原始数据源复制到内存中的
      // 这种方式可以避免测试之间的数据污染，提高测试的可靠性和独立性
      .pooled(); // 启用连接池，提高数据库连接的复用率，减少连接创建和销毁的开销
    // 连接池可以显著提高测试性能，特别是当测试需要执行大量查询时
    // 连接池会管理多个数据库连接，测试完成后连接不会被关闭，而是返回到池中供下次使用
    assertFoodmartLattice = CalciteAssert.that() // 创建第二个CalciteAssert构建器实例，用于测试Lattice优化
      .with(CalciteAssert.Config.JDBC_FOODMART_WITH_LATTICE) // 配置使用带Lattice的JDBC FoodMart数据源
      // JDBC_FOODMART_WITH_LATTICE配置包含预定义的Lattice结构，用于测试物化视图优化
      // Lattice是一种物化视图技术，可以自动识别和重写查询以使用预计算的结果
      // 这种配置使用真实的JDBC连接，而不是内存克隆，更接近生产环境
      .pooled(); // 同样启用连接池，提高测试性能
  } // setupAsserts方法结束，此时两个断言工具对象已经初始化完成

  @AfterAll // JUnit5注解：在所有测试方法执行后执行一次，用于清理测试环境
  public static void tearDownAsserts() { // 静态方法：清理断言工具对象，释放资源
    assertFoodmart = null; // 将FoodMart断言工具对象设置为null，帮助垃圾回收器回收内存
    // 虽然Java有垃圾回收机制，但显式设置为null是一个好的实践，特别是在长时间运行的测试中
    // 这可以避免对象被意外引用，导致内存泄漏
    assertFoodmartLattice = null; // 将Lattice断言工具对象设置为null，释放相关资源
    // 清理工作完成后，测试环境恢复到初始状态，为下一次测试运行做准备
  } // tearDownAsserts方法结束，测试环境清理完成

  // 202 and others: strip away "CAST(the_year AS UNSIGNED) = 1997" // 注释：查询202等涉及去除CAST类型转换表达式的问题
  // CAST(the_year AS UNSIGNED)是MySQL特有的类型转换语法，转换为无符号整数
  // Calcite可能不支持这种特定的类型转换语法，需要在查询解析或转换时处理
  // 去除CAST表达式可能涉及查询重写或类型推断的优化
  public static Stream<FoodMartQuerySet.FoodmartQuery> queries() throws IOException { // 静态方法：提供测试查询的数据源方法，返回查询对象的流
    // 该方法使用@MethodSource注解，为参数化测试提供测试数据
    // 返回Stream<FoodMartQuerySet.FoodmartQuery>表示返回一个FoodmartQuery对象的流
    // FoodmartQuery是FoodMart查询集合中的一个查询封装对象，包含查询ID和SQL语句
    // throws IOException表示该方法可能抛出IO异常，通常发生在读取查询配置文件时
    String idList = CalciteSystemProperty.TEST_FOODMART_QUERY_IDS.value(); // 从系统属性中获取要运行的查询ID列表
    // CalciteSystemProperty是Calcite的系统属性配置类，TEST_FOODMART_QUERY_IDS是特定的属性键
    // value()方法获取该属性的值，值为null表示未设置，将运行所有启用的查询
    // 如果设置了该属性（如"1-10,20,30"），则只运行指定ID的查询
    // 这允许开发者针对特定查询进行调试或验证，而不需要运行整个测试套件
    final FoodMartQuerySet set = FoodMartQuerySet.instance(); // 获取FoodMart查询集的单例实例
    // FoodMartQuerySet是一个单例类，instance()方法返回唯一的实例
    // 查询集包含了所有FoodMart参考查询的定义，从配置文件或资源中加载
    // 单例模式确保查询集只加载一次，提高性能并保持一致性
    // set.queries是一个Map，键是查询ID，值是FoodmartQuery对象
    final List<FoodMartQuerySet.FoodmartQuery> list = new ArrayList<>(); // 创建一个动态列表，用于存储要运行的查询对象
    // ArrayList是Java的动态数组实现，可以自动扩容，适合存储不确定数量的元素
    // 使用List而不是直接返回Stream，是因为需要先过滤和查询，然后再转换为Stream
    // list将在后续逻辑中填充符合条件的查询对象
    if (idList != null) { // 判断是否设置了系统属性来指定要运行的查询ID
      // 如果idList不为null，说明用户通过系统属性指定了要运行的查询
      // 这允许开发者只运行特定的查询，而不是所有查询
      if (idList.endsWith(",-disabled")) { // 检查ID列表字符串是否以",-disabled"结尾
        // ",-disabled"是一个特殊的标记，表示要运行所有查询，但排除禁用的查询
        // 这是一个便捷的语法，避免手动列出所有启用的查询ID
        StringBuilder buf = new StringBuilder(idList); // 创建StringBuilder对象，用于修改ID列表字符串
        // StringBuilder是可变的字符串构建器，比直接操作字符串更高效
        // 将idList复制到buf中，准备进行字符串修改操作
        buf.setLength(buf.length() - ",-disabled".length()); // 移除字符串末尾的",-disabled"部分
        // setLength方法截断字符串，减去",-disabled"的长度（11个字符）
        // 这样就得到了基础ID列表，例如"1-100"从"1-100,-disabled"中提取出来
        for (int disabledId : DISABLED_IDS) { // 遍历所有禁用的查询ID数组
          // 使用增强for循环遍历DISABLED_IDS数组中的每个元素
          // disabledId是当前遍历到的禁用查询ID
          buf.append(",-").append(disabledId); // 将禁用ID以",-ID"的格式追加到字符串中
          // append方法链式调用，先追加",-"，再追加disabledId的值
          // 例如，如果disabledId是58，则追加",-58"到字符串中
          // 这样构建出的字符串格式为"1-100,-58,-83,-202,..."
          // 这种格式会被IntegerIntervalSet解析为包含多个排除区间的整数集合
        } // 遍历完所有禁用ID后，字符串构建完成
        idList = buf.toString(); // 将StringBuilder转换为字符串，赋值给idList变量
        // toString()方法将StringBuilder的内容转换为不可变的String对象
        // 现在idList包含了基础ID范围和所有禁用ID的排除规则
      } // 处理完",-disabled"标记后，继续处理ID列表
      for (Integer id : IntegerIntervalSet.of(idList)) { // 使用IntegerIntervalSet解析ID列表字符串，并遍历解析出的ID
        // IntegerIntervalSet.of(idList)将字符串解析为整数区间的集合
        // 支持的格式包括："1-10"（范围）、"1,3,5"（列表）、"1-10,-5,-7"（排除）
        // 返回一个Iterable<Integer>，可以遍历所有符合条件的ID
        // 例如："1-10,-5"会返回[1,2,3,4,6,7,8,9,10]，排除了5
        final FoodMartQuerySet.FoodmartQuery query1 = set.queries.get(id); // 从查询集中获取指定ID的查询对象
        // set.queries是一个Map<Integer, FoodmartQuery>，get(id)根据ID查找对应的查询对象
        // 如果ID不存在，返回null
        // query1变量名使用1后缀是为了避免与参数名query冲突（虽然这个方法没有参数）
        if (query1 != null) { // 检查查询对象是否不为null
          // 如果查询对象存在（ID有效），则将其添加到测试列表中
          // 这个检查是必要的，因为IntegerIntervalSet可能解析出不存在于查询集中的ID
          list.add(query1); // 将查询对象添加到列表中
          // add方法将query1追加到list的末尾
          // list中的查询对象将作为参数化测试的输入数据
        } // 如果查询对象为null，跳过该ID，继续处理下一个ID
      } // 遍历完所有解析出的ID后，list包含了所有符合条件的查询对象
    } else { // 如果idList为null，表示没有设置系统属性，运行所有非禁用的查询
      // 这是默认行为，运行FoodMart查询集中所有未被禁用的查询
      for (FoodMartQuerySet.FoodmartQuery query1 : set.queries.values()) { // 遍历查询集中的所有查询对象
        // set.queries.values()返回Map中所有值的Collection视图
        // 使用增强for循环遍历每个FoodmartQuery对象
        // query1是当前遍历到的查询对象
        if (Primitive.asList(DISABLED_IDS).contains(query1.id)) { // 检查当前查询的ID是否在禁用列表中
          // Primitive.asList(DISABLED_IDS)将int数组转换为List<Integer>
          // contains(query1.id)检查禁用列表中是否包含当前查询的ID
          // 这是一个O(n)操作，因为列表需要线性搜索
          // 如果查询被禁用，跳过该查询，不添加到测试列表中
          continue; // 跳过当前迭代，继续下一个查询
          // continue语句立即结束本次循环，进入下一次迭代
          // 这样禁用的查询就不会被添加到list中
        } // 如果查询未被禁用，继续执行下面的代码
        list.add(query1); // 将查询对象添加到列表中
        // 只有未被禁用的查询才会被添加到list中
        // list最终包含所有可以运行的查询对象
      } // 遍历完所有查询后，list包含了所有非禁用的查询对象
    } // if-else块结束，list已经填充完成
    return list.stream(); // 将列表转换为流并返回
    // stream()方法将List转换为Stream<FoodmartQuerySet.FoodmartQuery>
    // Stream是Java 8引入的函数式编程API，支持惰性计算和链式操作
    // 返回的Stream将被@MethodSource注解的参数化测试方法使用
    // 每个查询对象都会作为参数传递给test方法，生成一个独立的测试用例
  } // queries方法结束，返回测试查询的流

  @ParameterizedTest(name = "{0}") // JUnit5参数化测试注解，name="{0}"表示使用查询对象的toString()作为测试名称
  // {0}是参数占位符，会被第一个参数（FoodmartQuery对象）的字符串表示替换
  // 例如，如果query.toString()返回"Query 1: SELECT ..."，测试名称就是"Query 1: SELECT ..."
  // 这使得测试报告更易读，可以清楚地看到每个测试用例对应哪个查询
  @MethodSource("queries") // 指定测试数据源方法为queries()，该方法返回Stream<FoodmartQuerySet.FoodmartQuery>
  // JUnit5会调用queries()方法获取测试数据，并为每个查询对象调用一次test方法
  // 这种方式可以自动运行大量测试用例，而不需要手动编写每个测试方法
  public void test(FoodMartQuerySet.FoodmartQuery query) { // 测试方法：测试单个FoodMart查询的正确性
    // query参数是由@MethodSource提供的查询对象，包含查询ID和SQL语句
    // 每个查询对象都会生成一个独立的测试用例，测试失败不会影响其他查询
    // 方法名test是JUnit5的标准命名，表示这是一个测试方法
    Assertions.assertTimeoutPreemptively(Duration.ofMinutes(2), () -> { // 断言测试在2分钟内完成，超时则中断执行
      // assertTimeoutPreemptively是JUnit5的超时断言方法，会在超时后强制中断测试线程
      // Duration.ofMinutes(2)创建一个2分钟的时间段
      // 超时设置为2分钟是因为某些复杂查询可能需要较长时间执行
      // preemptive表示超时后立即中断，而不是等待查询自然完成
      // 这可以防止测试无限期挂起，提高测试套件的可靠性
      assertFoodmart.query(query.sql).runs(); // 执行查询并断言查询成功运行
      // assertFoodmart是之前初始化的CalciteAssert对象，配置了FoodMart克隆数据源
      // query(query.sql)方法设置要执行的SQL语句
      // runs()方法执行查询并断言查询成功返回结果
      // 如果查询失败（语法错误、运行时错误等），测试会失败并抛出异常
      // runs()是一个流式API的终端操作，触发实际的查询执行
    }); // assertTimeoutPreemptively的lambda表达式结束
    // 如果查询在2分钟内成功执行，测试通过
    // 如果查询超时或失败，测试失败，JUnit5会报告错误
  } // test方法结束，单个查询测试完成

  @ParameterizedTest(name = "{0}") // JUnit5参数化测试注解，使用查询对象作为测试名称
  @Disabled // JUnit5注解：禁用此测试方法，不会在默认测试运行中执行
  // 该测试被禁用可能是因为Lattice功能还在开发中，或者存在已知问题
  // 开发者可以手动启用此测试来验证Lattice优化功能
  // 禁用测试是一种常见的做法，用于标记不稳定的或正在开发的功能
  @MethodSource("queries") // 指定测试数据源方法为queries()，与test方法使用相同的数据源
  public void testWithLattice(FoodMartQuerySet.FoodmartQuery query) { // 测试方法：测试带Lattice优化的FoodMart查询
    // 该方法与test方法类似，但使用assertFoodmartLattice而不是assertFoodmart
    // assertFoodmartLattice配置了Lattice结构，可以测试物化视图优化
    // Lattice可以自动识别和重写查询以使用预计算的物化视图，提高查询性能
    // 该测试验证Lattice优化是否正确工作，不会改变查询结果的正确性
    Assertions.assertTimeoutPreemptively(Duration.ofMinutes(2), () -> { // 断言测试在2分钟内完成
      // 同样设置2分钟超时，防止Lattice优化的查询执行时间过长
      assertFoodmartLattice // 使用Lattice配置的断言工具对象
        .withDefaultSchema("foodmart") // 设置默认schema为"foodmart"
        // withDefaultSchema方法指定查询的默认命名空间
        // "foodmart"是FoodMart数据仓库的schema名称
        // 这确保查询中的表名会正确解析到foodmart schema下的表
        .query(query.sql) // 设置要执行的SQL查询语句
        // query.sql是从FoodmartQuery对象中获取的SQL字符串
        // 这个SQL语句会被Lattice优化器分析和重写
        .enableMaterializations(true) // 启用物化视图优化
        // enableMaterializations(true)告诉优化器可以使用预计算的物化视图
        // Lattice优化器会检查查询是否可以重写为使用物化视图
        // 如果可以，优化器会生成使用物化视图的执行计划，提高查询性能
        .runs(); // 执行查询并断言成功
      // runs()方法执行优化后的查询，验证结果正确性
      // 如果Lattice优化有问题，可能会导致错误的结果或执行失败
      // 测试失败表明Lattice优化需要修复或调整
    }); // assertTimeoutPreemptively的lambda表达式结束
    // 如果启用了Lattice优化的查询在2分钟内成功执行，测试通过
    // 这证明Lattice优化不会破坏查询的正确性
  } // testWithLattice方法结束

} // FoodmartTest类结束
