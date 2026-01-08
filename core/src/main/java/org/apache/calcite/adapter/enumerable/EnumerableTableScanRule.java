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
// 包声明：该类属于org.apache.calcite.adapter.enumerable包，这是Calcite框架中用于可枚举适配器的包
package org.apache.calcite.adapter.enumerable;

// 导入Convention类：表示关系代数表达式的调用约定，不同的Convention代表不同的物理实现方式
import org.apache.calcite.plan.Convention;
// 导入RelOptTable类：表示优化器层面的表抽象，包含表的元数据信息
import org.apache.calcite.plan.RelOptTable;
// 导入RelNode类：关系代数节点的基类，所有关系表达式都继承自此类
import org.apache.calcite.rel.RelNode;
// 导入ConverterRule类：转换规则的基类，用于定义如何将一种RelNode转换为另一种RelNode
import org.apache.calcite.rel.convert.ConverterRule;
// 导入TableScan类：表扫描节点的基类，表示从表中读取数据的操作
import org.apache.calcite.rel.core.TableScan;
// 导入LogicalTableScan类：逻辑层面的表扫描节点，代表逻辑计划中的表扫描操作
import org.apache.calcite.rel.logical.LogicalTableScan;
// 导入QueryableTable接口：可查询表的接口，该表可以直接转换为LINQ表达式进行查询
import org.apache.calcite.schema.QueryableTable;
// 导入Table接口：Calcite中表的抽象接口，定义了表的基本操作
import org.apache.calcite.schema.Table;

// 导入Nullable注解：用于标记可能为null的返回值或参数
import org.checkerframework.checker.nullness.qual.Nullable;

/** Planner rule that converts a {@link LogicalTableScan} to an {@link EnumerableTableScan}.
 * 这是一个优化器规则类，用于将逻辑表扫描节点（LogicalTableScan）转换为可枚举表扫描节点（EnumerableTableScan）
 * 该类继承自ConverterRule，是Calcite优化器规则体系的一部分，负责将逻辑计划转换为物理计划
 * 
 * 核心作用：
 * 1. 在查询优化过程中，识别逻辑表扫描节点（LogicalTableScan）
 * 2. 判断该表是否可以被枚举（即是否可以转换为Enumerable约定）
 * 3. 如果可以，则将其转换为物理的EnumerableTableScan节点，该节点可以生成可执行的Java代码
 * 
 * EnumerableTableScan的特点：
 * - 它是物理实现节点，属于EnumerableConvention调用约定
 * - 可以生成Java代码来遍历表数据，实现数据源的枚举
 * - 适用于内存表、CSV文件、JSON文件等可枚举的数据源
 * 
 * You may provide a custom config to convert other nodes that extend {@link TableScan}.
 * 你可以提供自定义配置来转换其他继承自TableScan的节点，这使得该规则具有很好的扩展性
 *
 * @see EnumerableRules#ENUMERABLE_TABLE_SCAN_RULE */
public class EnumerableTableScanRule extends ConverterRule {
  /** Default configuration. 
   * 默认配置对象，该配置定义了规则的转换条件和行为
   * 这是一个静态常量，被所有EnumerableTableScanRule实例共享
   * 
   * 配置说明：
   * 1. 转换源：LogicalTableScan.class（逻辑表扫描节点）
   * 2. 转换条件：使用lambda表达式 r -> EnumerableTableScan.canHandle(r.getTable()) 判断表是否可处理
   * 3. 源调用约定：Convention.NONE（无约定，逻辑节点通常使用NONE）
   * 4. 目标调用约定：EnumerableConvention.INSTANCE（可枚举约定）
   * 5. 规则名称："EnumerableTableScanRule"
   * 6. 规则工厂：使用方法引用 EnumerableTableScanRule::new 创建规则实例
   */
  public static final Config DEFAULT_CONFIG = Config.INSTANCE
      // withConversion方法：配置转换规则，指定要转换的节点类型、转换条件、源约定、目标约定和规则名称
      // 参数1：LogicalTableScan.class - 要转换的源节点类型为逻辑表扫描
      // 参数2：r -> EnumerableTableScan.canHandle(r.getTable()) - 使用lambda表达式判断表是否可以被EnumerableTableScan处理
      //        - r是LogicalTableScan节点，r.getTable()获取该节点对应的表
      //        - EnumerableTableScan.canHandle()方法检查表是否支持枚举操作（如是否是QueryableTable或是否有表达式）
      // 参数3：Convention.NONE - 源节点的调用约定为NONE（逻辑节点的标准约定）
      // 参数4：EnumerableConvention.INSTANCE - 目标节点的调用约定为可枚举约定
      // 参数5："EnumerableTableScanRule" - 规则的描述性名称，用于调试和日志
      .withConversion(LogicalTableScan.class,
          r -> EnumerableTableScan.canHandle(r.getTable()),
          Convention.NONE, EnumerableConvention.INSTANCE,
          "EnumerableTableScanRule")
      // withRuleFactory方法：配置规则工厂，指定如何创建规则实例
      // 参数：EnumerableTableScanRule::new - 方法引用，使用配置对象调用构造函数创建规则实例
      //      这使得优化器可以根据配置动态创建规则实例
      .withRuleFactory(EnumerableTableScanRule::new);

  /** 
   * 构造方法：创建一个新的EnumerableTableScanRule实例
   * 
   * @param config 规则配置对象，包含该规则的所有配置信息，如转换条件、调用约定等
   *               通常使用DEFAULT_CONFIG常量，也可以传入自定义配置来扩展规则功能
   * 
   * 构造方法作用：
   * 1. 调用父类ConverterRule的构造方法，初始化规则的基本属性
   * 2. 将配置信息保存到父类中，供后续匹配和转换使用
   * 3. 设置规则的匹配条件、转换逻辑等
   * 
   * protected修饰符说明：
   * - 该构造方法受保护，只能被子类或同包内的类访问
   * - 通常通过规则工厂（withRuleFactory）来创建实例，而不是直接调用构造方法
   */
  protected EnumerableTableScanRule(Config config) {
    // 调用父类ConverterRule的构造方法，传入配置对象
    // 父类会根据配置初始化规则的各种属性，如：
    // - 转换的源节点类型（LogicalTableScan）
    // - 转换的目标约定（EnumerableConvention）
    // - 转换条件的谓词（canHandle检查）
    // - 规则的描述信息等
    super(config);
  }

  /** 
   * convert方法：核心转换方法，将逻辑表扫描节点转换为可枚举表扫描节点
   * 
   * 该方法是ConverterRule接口的实现，当优化器匹配到符合条件的LogicalTableScan节点时会调用此方法
   * 
   * @param rel 待转换的关系节点，这里应该是LogicalTableScan类型的节点
   * @return 转换后的RelNode，通常是EnumerableTableScan实例；如果无法转换则返回null
   * 
   * 方法执行流程：
   * 1. 将输入节点强制类型转换为TableScan
   * 2. 获取表扫描节点关联的RelOptTable对象（优化器层面的表）
   * 3. 从RelOptTable中解包获取实际的Table对象
   * 4. 判断表是否支持枚举操作：
   *    - 如果表是QueryableTable实例，说明可以直接转换为LINQ表达式
   *    - 或者表有可获取的表达式（getExpression(Object.class) != null）
   * 5. 如果满足条件，创建并返回EnumerableTableScan节点
   * 6. 如果不满足条件，返回null，表示该节点不能被此规则转换
   * 
   * @Nullable注解说明：返回值可能为null，当表不支持枚举操作时返回null
   */
  @Override public @Nullable RelNode convert(RelNode rel) {
    // 将输入的RelNode强制转换为TableScan类型
    // 因为该规则只处理TableScan及其子类（如LogicalTableScan），所以这里类型转换是安全的
    // scan变量代表逻辑层面的表扫描操作，包含表引用等信息
    TableScan scan = (TableScan) rel;
    // 从表扫描节点中获取RelOptTable对象
    // RelOptTable是优化器层面的表抽象，包含表的元数据、统计信息、表达式等
    // 它是对实际Table对象的包装，提供了优化器需要的额外信息
    final RelOptTable relOptTable = scan.getTable();
    // 从RelOptTable中解包获取实际的Table对象
    // unwrap方法会尝试从包装对象中获取指定类型的实例
    // 这里获取Table接口的实现类，可能是QueryableTable、ScannableTable等
    final Table table = relOptTable.unwrap(Table.class);
    // The QueryableTable can only be implemented as ENUMERABLE convention,
    // QueryableTable只能以ENUMERABLE约定实现，因为它需要生成可执行的LINQ表达式
    // but some test QueryableTables do not really implement the expressions,
    // 但一些测试用的QueryableTable并没有真正实现表达式生成逻辑
    // just skips the QueryableTable#getExpression invocation and returns early.
    // 只是跳过了QueryableTable#getExpression的调用并提前返回
    // 
    // 条件判断：检查表是否支持枚举操作
    // 条件1：table instanceof QueryableTable - 表是否实现了QueryableTable接口
    //        QueryableTable接口表示表可以直接转换为LINQ表达式进行查询
    //        如果是QueryableTable，则必须使用Enumerable约定来执行
    // 条件2：relOptTable.getExpression(Object.class) != null - 表是否有可获取的表达式
    //        getExpression方法尝试获取表的Java表达式，如果返回非null，说明表可以被枚举
    //        这个表达式可能是LINQ表达式、Java代码或其他可执行的表达式
    // 
    // 如果满足任一条件，说明该表可以被转换为EnumerableTableScan
    if (table instanceof QueryableTable || relOptTable.getExpression(Object.class) != null) {
      // 创建并返回EnumerableTableScan节点
      // 参数1：scan.getCluster() - 获取关系表达式集群，包含优化器上下文、类型工厂等共享信息
      // 参数2：relOptTable - 优化器层面的表对象，包含表的元数据和表达式信息
      // EnumerableTableScan.create方法会创建一个新的物理表扫描节点，该节点可以生成可执行的Java代码
      // 这个节点属于EnumerableConvention约定，可以参与后续的物理优化和代码生成
      return EnumerableTableScan.create(scan.getCluster(), relOptTable);
    }

    // 如果表不支持枚举操作（既不是QueryableTable，也没有可获取的表达式）
    // 则返回null，表示该规则无法转换此节点
    // 返回null后，优化器会尝试使用其他规则来转换该节点，或者保持原样
    return null;
  }
}
