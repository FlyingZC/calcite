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
package org.apache.calcite.test; // 包声明：该类属于org.apache.calcite.test测试包

import org.apache.calcite.materialize.DelegatingLatticeStatisticProvider; // 导入委托型Lattice统计信息提供者基类
import org.apache.calcite.materialize.Lattice; // 导入Lattice（立方体）类，表示多维数据结构
import org.apache.calcite.materialize.LatticeStatisticProvider; // 导入Lattice统计信息提供者接口
import org.apache.calcite.materialize.Lattices; // 导入Lattices工具类

import com.google.common.collect.ImmutableMap; // 导入Google Guava的不可变Map类

import java.util.ArrayList; // 导入Java集合框架的ArrayList类
import java.util.List; // 导入Java集合框架的List接口
import java.util.Map; // 导入Java集合框架的Map接口

/**
 * Implementation of {@link LatticeStatisticProvider} // 类功能说明：这是LatticeStatisticProvider接口的实现类
 * that has hard-coded values for various attributes in the FoodMart lattice. // 为FoodMart立方体中各种属性提供硬编码的统计值
 *
 * <p>This makes testing faster. // 这样做可以加快测试速度，避免每次测试都查询数据库获取统计信息
 */
public class FoodMartLatticeStatisticProvider // 定义FoodMartLatticeStatisticProvider类
    extends DelegatingLatticeStatisticProvider { // 继承自DelegatingLatticeStatisticProvider委托基类
  public static final FoodMartLatticeStatisticProvider.Factory FACTORY = // 定义静态工厂常量，类型为FoodMartLatticeStatisticProvider.Factory（函数式接口）
      lattice -> new FoodMartLatticeStatisticProvider(lattice, // 使用Lambda表达式创建工厂实例，接收Lattice参数并返回新的FoodMartLatticeStatisticProvider对象
          Lattices.CACHED_SQL.apply(lattice)); // 使用Lattices.CACHED_SQL应用器处理lattice，生成底层的LatticeStatisticProvider

  private static final Map<String, Integer> CARDINALITY_MAP = // 定义静态不可变映射，用于存储FoodMart立方体中各列的基数（基数指列中不同值的数量）
      ImmutableMap.<String, Integer>builder() // 创建ImmutableMap构建器，用于构建不可变的Map对象
          .put("brand_name", 111) // 品牌名称列的基数为111（表示有111个不同的品牌名称）
          .put("cases_per_pallet", 10) // 每托盘箱数列的基数为10
          .put("customer_id", 5581) // 客户ID列的基数为5581（表示有5581个不同的客户）
          .put("day_of_month", 30) // 月中天数列的基数为30（表示一个月最多30天）
          .put("fiscal_period", 0) // 财政周期列的基数为0（特殊值，表示无数据或未使用）
          .put("gross_weight", 376) // 毛重列的基数为376（表示有376种不同的毛重值）
          .put("low_fat", 2) // 低脂标志列的基数为2（表示只有2个值：是/否）
          .put("month_of_year", 12) // 年中月份列的基数为12（表示一年有12个月）
          .put("net_weight", 332) // 净重列的基数为332（表示有332种不同的净重值）
          .put("product_category", 45) // 产品类别列的基数为45（表示有45个不同的产品类别）
          .put("product_class_id", 102) // 产品类别ID列的基数为102（表示有102个不同的产品类别ID）
          .put("product_department", 22) // 产品部门列的基数为22（表示有22个不同的产品部门）
          .put("product_family", 3) // 产品系列列的基数为3（表示有3个不同的产品系列）
          .put("product_id", 1559) // 产品ID列的基数为1559（表示有1559个不同的产品）
          .put("product_name", 1559) // 产品名称列的基数为1559（表示有1559个不同的产品名称）
          .put("product_subcategory", 102) // 产品子类别列的基数为102（表示有102个不同的产品子类别）
          .put("promotion_id", 149) // 促销ID列的基数为149（表示有149个不同的促销活动）
          .put("quarter", 4) // 季度列的基数为4（表示一年有4个季度）
          .put("recyclable_package", 2) // 可回收包装标志列的基数为2（表示只有2个值：是/否）
          .put("shelf_depth", 488) // 货架深度列的基数为488（表示有488种不同的货架深度值）
          .put("shelf_height", 524) // 货架高度列的基数为524（表示有524种不同的货架高度值）
          .put("shelf_width", 534) // 货架宽度列的基数为534（表示有534种不同的货架宽度值）
          .put("SKU", 1559) // SKU（库存单位）列的基数为1559（表示有1559个不同的SKU）
          .put("SRP", 315) // SRP（建议零售价）列的基数为315（表示有315种不同的建议零售价）
          .put("store_cost", 10777) // 商店成本列的基数为10777（表示有10777种不同的商店成本值）
          .put("store_id", 13) // 商店ID列的基数为13（表示有13个不同的商店）
          .put("store_sales", 1049) // 商店销售额列的基数为1049（表示有1049种不同的商店销售额值）
          .put("the_date", 323) // 日期列的基数为323（表示有323个不同的日期）
          .put("the_day", 7) // 星期列的基数为7（表示一周有7天）
          .put("the_month", 12) // 月份列的基数为12（表示有12个月）
          .put("the_year", 1) // 年份列的基数为1（表示只有1年数据）
          .put("time_id", 323) // 时间ID列的基数为323（表示有323个不同的时间ID）
          .put("units_per_case", 36) // 每箱单位数列的基数为36（表示有36种不同的每箱单位数）
          .put("unit_sales", 6) // 单位销售列的基数为6（表示有6种不同的单位销售值）
          .put("week_of_year", 52) // 年中周数列的基数为52（表示一年有52周）
          .build(); // 构建不可变Map对象

  private final Lattice lattice; // 定义成员变量：Lattice对象，表示当前关联的立方体（多维数据结构）

  private FoodMartLatticeStatisticProvider(Lattice lattice, // 私有构造方法：接收Lattice对象作为参数
      LatticeStatisticProvider provider) { // 接收LatticeStatisticProvider对象作为参数（委托的底层统计信息提供者）
    super(provider); // 调用父类DelegatingLatticeStatisticProvider的构造方法，传入provider参数实现委托模式
    this.lattice = lattice; // 将传入的lattice对象赋值给成员变量，保存对立方体的引用
  }

  private int cardinality(Lattice.Column column) { // 私有方法：获取单个列的基数（不同值的数量），参数为Lattice.Column对象
    final Integer integer = CARDINALITY_MAP.get(column.alias); // 从CARDINALITY_MAP中根据列别名获取预定义的基数值
    if (integer != null && integer > 0) { // 如果获取到的基数值不为空且大于0
      return integer; // 返回预定义的基数值
    }
    return column.alias.length(); // 如果没有预定义的基数值，则返回列别名的长度作为默认基数（这是一种回退策略）
  }

  @Override public double cardinality(List<Lattice.Column> columns) { // 重写父类方法：计算多列组合的基数，参数为Lattice.Column对象的列表
    final List<Double> cardinalityList = new ArrayList<>(); // 创建Double类型的列表，用于存储每列的基数
    for (Lattice.Column column : columns) { // 遍历传入的列列表
      cardinalityList.add((double) cardinality(column)); // 调用cardinality方法获取每列的基数，转换为double类型后添加到列表中
    }
    return Lattice.getRowCount(lattice.getFactRowCount(), cardinalityList); // 调用Lattice.getRowCount静态方法，根据事实表的行数和各列基数列表，计算组合列的基数并返回
  }
}
