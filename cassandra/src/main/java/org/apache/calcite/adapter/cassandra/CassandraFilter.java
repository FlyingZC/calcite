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
// 声明包名,表示这个类属于Calcite的Cassandra适配器模块
package org.apache.calcite.adapter.cassandra;

// 导入Calcite核心包中的类,用于关系代数优化和执行
import org.apache.calcite.plan.RelOptCluster; // 关系表达式簇,包含共享的优化信息
import org.apache.calcite.plan.RelOptCost; // 关系表达式的代价估计
import org.apache.calcite.plan.RelOptPlanner; // 关系代数优化器,用于选择最优执行计划
import org.apache.calcite.plan.RelOptUtil; // 关系代数工具类,提供各种辅助方法
import org.apache.calcite.plan.RelTraitSet; // 关系表达式的特征集合,如物理实现方式
import org.apache.calcite.rel.RelCollation; // 排序规则,描述字段的排序方式
import org.apache.calcite.rel.RelCollations; // 排序规则的工厂类和工具方法
import org.apache.calcite.rel.RelFieldCollation; // 单个字段的排序规则
import org.apache.calcite.rel.RelNode; // 关系表达式接口,代表关系代数操作
import org.apache.calcite.rel.core.Filter; // 过滤操作的核心实现类,这是CassandraFilter的父类
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 关系表达式元数据查询接口
import org.apache.calcite.rel.type.RelDataType; // 关系数据类型,描述行的结构
import org.apache.calcite.rel.type.RelDataTypeField; // 关系数据类型中的字段
import org.apache.calcite.rex.RexCall; // Rex表达式调用,表示函数调用或操作符应用
import org.apache.calcite.rex.RexInputRef; // Rex输入引用,引用输入行中的某个字段
import org.apache.calcite.rex.RexLiteral; // Rex字面量,表示常量值
import org.apache.calcite.rex.RexNode; // Rex表达式接口,表示行表达式的抽象语法树
import org.apache.calcite.sql.type.SqlTypeName; // SQL类型名称枚举
import org.apache.calcite.util.DateString; // Calcite的日期字符串表示
import org.apache.calcite.util.TimestampString; // Calcite的时间戳字符串表示
import org.apache.calcite.util.TimestampWithTimeZoneString; // Calcite的带时区时间戳字符串表示
import org.apache.calcite.util.Util; // Calcite通用工具类

// 导入空值检查框架的注解
import org.checkerframework.checker.nullness.qual.Nullable; // 标记可能为null的返回值

// 导入Java标准库
import java.text.SimpleDateFormat; // 日期时间格式化工具
import java.util.ArrayList; // 动态数组列表
import java.util.Collections; // 集合工具类,提供不可变集合等
import java.util.HashSet; // 哈希集合,用于快速查找和去重
import java.util.List; // 列表接口
import java.util.Set; // 集合接口

// 导入日期时间格式化常量和方法
import static org.apache.calcite.util.DateTimeStringUtils.ISO_DATETIME_FRACTIONAL_SECOND_FORMAT; // ISO日期时间格式
import static org.apache.calcite.util.DateTimeStringUtils.getDateFormatter; // 获取日期格式化器的方法

// 导入对象工具方法
import static java.util.Objects.requireNonNull; // 要求对象非null的工具方法

/**
 * 实现Cassandra数据库中的过滤(Filter)关系表达式
 * 
 * 【类的作用和职责】:
 * 1. 继承自Calcite的Filter类,实现CassandraRel接口,是Cassandra适配器中的核心过滤节点
 * 2. 负责将Calcite的逻辑过滤条件转换为Cassandra的CQL(Cassandra Query Language)WHERE子句
 * 3. 管理Cassandra的分区键(Partition Key)和聚簇键(Clustering Key)信息
 * 4. 优化过滤条件,判断是否可以限制到单个分区,从而提高查询性能
 * 5. 处理过滤后的隐式排序,因为Cassandra的聚簇键天然具有排序特性
 * 
 * 【Cassandra数据模型背景】:
 * - 分区键(Partition Key): 决定数据在集群中的分布,相同分区键的数据存储在同一节点
 * - 聚簇键(Clustering Key): 在同一分区内对数据进行排序,支持范围查询
 * - 查询性能优化: 如果过滤条件包含完整的分区键,Cassandra可以直接定位到单个分区,避免全表扫描
 */
public class CassandraFilter extends Filter implements CassandraRel {
  // 分区键列表,存储Cassandra表的分区键字段名
  // 在构造方法中被初始化,并在Translator中用于判断是否限制到单个分区
  // 当所有分区键都被等值条件过滤时,查询就限制到单个分区
  private final List<String> partitionKeys;
  
  // 标识当前过滤是否限制到单个分区
  // Boolean而不是boolean是为了允许null值,表示尚未计算
  // true: 过滤条件包含所有分区键的等值条件,查询只访问一个分区
  // false: 过滤条件不完整,可能访问多个分区
  private Boolean singlePartition;
  
  // 聚簇键列表,存储Cassandra表的聚簇键字段名
  // 聚簇键决定了同一分区内数据的物理存储顺序
  // 用于判断过滤后的隐式排序和范围查询能力
  private final List<String> clusteringKeys;
  
  // 隐式字段排序规则列表
  // 每个RelFieldCollation描述一个字段的排序方向(ASC/DESC)
  // 对应Cassandra表定义中聚簇键的CLUSTERING ORDER
  // 例如: [(user_id, ASC), (created_at, DESC)]
  private final List<RelFieldCollation> implicitFieldCollations;
  
  // 过滤后的隐式排序规则
  // 当限制到单个分区时,未被等值约束的聚簇键会形成自然的排序顺序
  // 例如: 分区键为user_id,聚簇键为created_at DESC
  //       过滤条件user_id=123后,结果按created_at DESC排序
  // 这个属性可以被优化器利用,避免额外的排序操作
  private final RelCollation implicitCollation;
  
  // 翻译后的CQL WHERE子句字符串
  // 由Translator将RexNode(Calcite的表达式)转换为CQL语法
  // 例如: "user_id = 123 AND created_at > '2024-01-01'"
  // 这个字符串最终会被拼接到CQL查询语句中
  private final String match;

  /**
   * 构造方法:创建CassandraFilter实例
   * 
   * 【参数说明】:
   * @param cluster 关系表达式簇,包含优化器共享的信息(如类型系统、元数据等)
   * @param traitSet 关系表达式的特征集合,包含物理实现约定(这里是CassandraRel.CONVENTION)
   * @param child 子关系节点,通常是CassandraTableScan,表示要过滤的数据源
   * @param condition 过滤条件,以RexNode形式表示的逻辑表达式(如: user_id = 123 AND age > 18)
   * @param partitionKeys 分区键字段名列表,从表结构中提取,用于判断分区限制
   * @param clusteringKeys 聚簇键字段名列表,从表结构中提取,用于判断排序和范围查询
   * @param implicitFieldCollations 隐式字段排序规则,对应Cassandra表的CLUSTERING ORDER
   * 
   * 【构造过程】:
   * 1. 调用父类Filter的构造方法,初始化基本属性
   * 2. 保存分区键、聚簇键和排序规则
   * 3. 创建Translator对象,负责将RexNode表达式转换为CQL字符串
   * 4. 使用Translator翻译过滤条件,得到CQL WHERE子句
   * 5. 判断是否限制到单个分区,计算隐式排序
   * 6. 断言约定匹配,确保物理实现的正确性
   */
  public CassandraFilter(
      RelOptCluster cluster, // 关系表达式簇
      RelTraitSet traitSet, // 特征集合
      RelNode child, // 子节点(数据源)
      RexNode condition, // 过滤条件
      List<String> partitionKeys, // 分区键列表
      List<String> clusteringKeys, // 聚簇键列表
      List<RelFieldCollation> implicitFieldCollations) { // 隐式排序规则
    // 调用父类Filter的构造方法,初始化cluster、traitSet、child、condition等基本属性
    super(cluster, traitSet, child, condition);

    // 保存分区键列表,final修饰表示不可变
    this.partitionKeys = partitionKeys;
    
    // 初始化为false,后续会根据翻译结果更新
    this.singlePartition = false;
    
    // 创建聚簇键列表的副本,避免外部修改影响内部状态
    this.clusteringKeys = new ArrayList<>(clusteringKeys);
    
    // 保存隐式字段排序规则
    this.implicitFieldCollations = implicitFieldCollations;

    // 创建Translator对象,负责翻译表达式
    // 参数说明:
    // - getRowType(): 获取当前节点的行类型(包含字段名称和类型信息)
    // - partitionKeys: 分区键列表,用于判断是否限制到单个分区
    // - clusteringKeys: 聚簇键列表,用于判断排序和范围查询
    // - implicitFieldCollations: 字段排序方向,用于构建隐式排序
    Translator translator =
        new Translator(getRowType(), partitionKeys, clusteringKeys,
            implicitFieldCollations);
    
    // 使用Translator将RexNode条件转换为CQL WHERE子句字符串
    // 例如: RexNode(user_id = 123 AND created_at > '2024-01-01')
    //      -> CQL("user_id = 123 AND created_at > '2024-01-01'")
    this.match = translator.translateMatch(condition);
    
    // 判断过滤条件是否限制到单个分区
    // 如果所有分区键都被等值条件约束,则返回true
    this.singlePartition = translator.isSinglePartition();
    
    // 获取过滤后的隐式排序规则
    // 当限制到单个分区时,未被等值约束的聚簇键形成自然排序
    this.implicitCollation = translator.getImplicitCollation();

    // 断言:当前节点的约定必须是CassandraRel.CONVENTION
    // 确保这个节点确实实现了Cassandra的物理约定
    assert getConvention() == CassandraRel.CONVENTION;
    
    // 断言:子节点的约定也必须是CassandraRel.CONVENTION
    // 确保整个执行计划都使用Cassandra的物理实现
    assert getConvention() == child.getConvention();
  }

  /**
   * 计算当前节点的执行代价
   * 
   * 【方法作用】:
   * 1. 优化器调用此方法评估执行计划的代价
   * 2. Filter节点的代价通常很低,因为它只是过滤数据,不产生新数据
   * 3. 这里将父类计算的代价乘以0.1,表示过滤操作相对便宜
   * 
   * 【参数说明】:
   * @param planner 关系优化器,用于查询元数据
   * @param mq 元数据查询接口,可以获取行数、数据大小等信息
   * 
   * 【返回值】:
   * @return 优化后的代价估计,通常比扫描操作低一个数量级
   */
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) {
    // 调用父类Filter的代价计算方法
    // 父类会基于元数据(如行数、CPU代价、IO代价)计算基础代价
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq));
    
    // 将代价乘以0.1,表示过滤操作相对便宜
    // 这样优化器更倾向于尽早应用过滤,减少后续处理的数据量
    return cost.multiplyBy(0.1);
  }

  /**
   * 复制当前节点,创建一个新的CassandraFilter实例
   * 
   * 【方法作用】:
   * 1. 优化器在重写执行计划时会调用此方法创建节点的副本
   * 2. 允许修改某些属性(如traitSet、input、condition)而保持其他属性不变
   * 3. 实现了Calcite的不可变对象模式,每次修改都创建新对象
   * 
   * 【参数说明】:
   * @param traitSet 新的特征集合,可能包含不同的物理实现约定或排序要求
   * @param input 新的输入节点,可能是经过优化的子树
   * @param condition 新的过滤条件,可能是经过简化的表达式
   * 
   * 【返回值】:
   * @return 新的CassandraFilter实例,保持原有的分区键、聚簇键等信息
   */
  @Override public CassandraFilter copy(RelTraitSet traitSet, RelNode input,
      RexNode condition) {
    // 创建新的CassandraFilter实例
    // 参数说明:
    // - getCluster(): 保持原有的簇信息
    // - traitSet: 使用新的特征集合
    // - input: 使用新的输入节点
    // - condition: 使用新的过滤条件
    // - partitionKeys: 保持原有的分区键列表
    // - clusteringKeys: 保持原有的聚簇键列表
    // - implicitFieldCollations: 保持原有的排序规则
    return new CassandraFilter(getCluster(), traitSet, input, condition,
        partitionKeys, clusteringKeys, implicitFieldCollations);
  }

  /**
   * 实现CQL查询生成
   * 
   * 【方法作用】:
   * 1. 将CassandraFilter节点转换为可执行的CQL查询语句
   * 2. 这是Cassandra适配器的核心方法,将逻辑计划转换为物理执行语句
   * 3. 实现了CassandraRel接口的implement方法,被Implementor调用
   * 
   * 【参数说明】:
   * @param implementor 实现器,负责生成CQL语句并维护查询上下文
   * 
   * 【执行流程】:
   * 1. 访问子节点(通常是CassandraTableScan),生成FROM子句
   * 2. 添加WHERE子句,使用翻译后的match字符串
   * 3. 最终生成的CQL类似: SELECT * FROM table WHERE match
   */
  @Override public void implement(Implementor implementor) {
    // 访问子节点(索引0),通常是CassandraTableScan
    // implementor会调用子节点的implement方法,生成FROM子句
    // 例如: "SELECT * FROM users"
    implementor.visitChild(0, getInput());
    
    // 添加WHERE子句
    // 参数说明:
    // - 第一个参数null: 表示没有额外的投影,使用所有字段
    // - 第二个参数: WHERE子句的列表,这里只有一个match字符串
    // 例如: "SELECT * FROM users WHERE user_id = 123 AND age > 18"
    implementor.add(null, Collections.singletonList(match));
  }

  /**
   * 检查过滤条件是否限制到单个分区
   * 
   * 【方法作用】:
   * 1. 判断当前过滤是否只访问Cassandra的一个分区
   * 2. 如果是,查询性能会大幅提升,因为避免了跨节点查询
   * 3. 优化器可以利用这个信息进行进一步优化
   * 
   * 【判断逻辑】:
   * - 当所有分区键都被等值条件(=)约束时,返回true
   * - 例如: 分区键为user_id,条件为user_id = 123,则返回true
   * - 如果分区键为(user_id, date),条件为user_id = 123,则返回false(缺少date)
   * 
   * @return true表示只访问单个分区,false表示可能访问多个分区
   */
  public boolean isSinglePartition() {
    // 返回singlePartition标志
    // 这个值在构造方法中由Translator计算
    return singlePartition;
  }

  /**
   * 获取过滤后的隐式排序规则
   * 
   * 【方法作用】:
   * 1. 返回Cassandra聚簇键形成的自然排序顺序
   * 2. 当限制到单个分区时,未被等值约束的聚簇键会形成排序
   * 3. 优化器可以利用这个信息,避免额外的排序操作
   * 
   * 【示例】:
   * - 表定义: PRIMARY KEY((user_id), created_at DESC)
   * - 过滤条件: user_id = 123
   * - 结果: 数据按created_at DESC排序,implicitCollation包含这个排序规则
   * 
   * @return 隐式排序规则,如果不是单个分区则返回空排序
   */
  public RelCollation getImplicitCollation() {
    // 返回在构造方法中计算的隐式排序规则
    return implicitCollation;
  }

  /**
   * 内部类:表达式翻译器
   * 
   * 【类的作用和职责】:
   * 1. 将Calcite的RexNode表达式转换为CQL的WHERE子句字符串
   * 2. 处理各种操作符: =, <, <=, >, >=
   * 3. 管理分区键和聚簇键的约束状态
   * 4. 计算是否限制到单个分区和隐式排序
   * 
   * 【设计模式】:
   * - 访问者模式: 递归遍历RexNode表达式树
   * - 翻译器模式: 将一种表达式语言转换为另一种
   * 
   * 【CQL限制】:
   * - 不支持OR操作(只能使用AND连接的条件)
   * - 分区键只能使用等值条件(=)
   * - 聚簇键可以使用范围查询(<, <=, >, >=),但有顺序限制
   * - 如果前面的聚簇键没有等值约束,后面的聚簇键不能使用范围查询
   */
  static class Translator {
    // 行类型,包含字段名称和类型信息
    // 用于获取字段的SQL类型,以便正确格式化字面量
    private final RelDataType rowType;
    
    // 字段名称列表,从rowType中提取
    // 用于将RexInputRef(字段索引)映射到实际的字段名
    // 例如: [user_id, name, age, created_at]
    private final List<String> fieldNames;
    
    // 分区键集合,使用Set便于快速查找和删除
    // 初始时包含所有分区键,当遇到等值条件时移除对应的键
    // 当集合为空时,表示所有分区键都被约束,限制到单个分区
    private final Set<String> partitionKeys;
    
    // 聚簇键列表,保持顺序很重要
    // 用于判断哪些聚簇键被等值约束,哪些可以用于范围查询
    // 例如: [created_at, updated_at]
    private final List<String> clusteringKeys;
    
    // 被等值约束的聚簇键数量
    // 用于计算隐式排序: 从restrictedClusteringKeys开始的聚簇键形成排序
    // 例如: 聚簇键为[a, b, c],a和b被等值约束,则c形成排序
    private int restrictedClusteringKeys;
    
    // 隐式字段排序规则,对应每个聚簇键的排序方向
    // 例如: [(created_at, DESC), (updated_at, ASC)]
    private final List<RelFieldCollation> implicitFieldCollations;

    /**
     * 构造方法:创建Translator实例
     * 
     * 【参数说明】:
     * @param rowType 行类型,包含字段名称和类型信息
     * @param partitionKeys 分区键字段名列表
     * @param clusteringKeys 聚簇键字段名列表
     * @param implicitFieldCollations 隐式字段排序规则
     * 
     * 【初始化过程】:
     * 1. 保存行类型,用于后续获取字段类型
     * 2. 从行类型中提取字段名称列表
     * 3. 将分区键列表转换为Set,便于快速查找和删除
     * 4. 保存聚簇键列表
     * 5. 初始化被约束的聚簇键数量为0
     * 6. 保存隐式排序规则
     */
    Translator(RelDataType rowType, List<String> partitionKeys, List<String> clusteringKeys,
        List<RelFieldCollation> implicitFieldCollations) {
      // 保存行类型
      this.rowType = rowType;
      
      // 从行类型中提取Cassandra字段名称
      // CassandraRules.cassandraFieldNames会处理大小写和特殊字符
      this.fieldNames = CassandraRules.cassandraFieldNames(rowType);
      
      // 将分区键列表转换为HashSet,便于快速查找和删除
      // 使用Set而不是List是因为需要频繁的remove操作
      this.partitionKeys = new HashSet<>(partitionKeys);
      
      // 保存聚簇键列表,保持顺序
      this.clusteringKeys = clusteringKeys;
      
      // 初始化被约束的聚簇键数量为0
      // 每次遇到聚簇键的等值条件时递增
      this.restrictedClusteringKeys = 0;
      
      // 保存隐式字段排序规则
      this.implicitFieldCollations = implicitFieldCollations;
    }

    /**
     * 检查查询是否限制到单个分区
     * 
     * 【判断逻辑】:
     * - partitionKeys初始时包含所有分区键
     * - 每次遇到分区键的等值条件时,从集合中移除该键
     * - 当集合为空时,表示所有分区键都被等值约束
     * 
     * 【示例】:
     * - 分区键: [user_id, date]
     * - 条件: user_id = 123 AND date = '2024-01-01'
     * - 结果: partitionKeys为空,返回true
     * 
     * @return true表示限制到单个分区,false表示访问多个分区
     */
    public boolean isSinglePartition() {
      // 如果partitionKeys为空,说明所有分区键都被等值约束
      return partitionKeys.isEmpty();
    }

    /**
     * 推断未被约束的聚簇键形成的隐式排序
     * 
     * 【方法作用】:
     * 1. 当限制到单个分区时,Cassandra的聚簇键会形成自然的排序顺序
     * 2. 只有未被等值约束的聚簇键才会形成排序
     * 3. 排序方向由表定义的CLUSTERING ORDER决定
     * 
     * 【示例】:
     * - 聚簇键: [created_at DESC, updated_at ASC]
     * - 约束: created_at = '2024-01-01'
     * - 结果: implicitCollation = [(updated_at, ASC)]
     * 
     * @return 隐式排序规则,如果不是单个分区则返回空排序
     */
    public RelCollation getImplicitCollation() {
      // 如果不是单个分区,则没有隐式排序
      // 因为跨分区查询时,数据在多个节点上,没有全局排序
      if (!isSinglePartition()) {
        return RelCollations.EMPTY;
      }

      // 提取未被约束的聚簇键及其排序方向
      List<RelFieldCollation> fieldCollations = new ArrayList<>();
      
      // 从restrictedClusteringKeys开始遍历,跳过已被等值约束的聚簇键
      for (int i = restrictedClusteringKeys; i < clusteringKeys.size(); i++) {
        // 获取聚簇键在字段列表中的索引
        int fieldIndex = fieldNames.indexOf(clusteringKeys.get(i));
        
        // 获取该聚簇键的排序方向(ASC或DESC)
        RelFieldCollation.Direction direction = implicitFieldCollations.get(i).getDirection();
        
        // 创建字段排序规则并添加到列表
        fieldCollations.add(new RelFieldCollation(fieldIndex, direction));
      }

      // 将字段排序规则列表转换为RelCollation对象
      return RelCollations.of(fieldCollations);
    }

    /**
     * 将RexNode条件转换为CQL WHERE子句字符串
     * 
     * 【方法作用】:
     * 1. 入口方法,接收RexNode表达式,返回CQL字符串
     * 2. 检查是否支持OR操作(CQL不支持,只能用AND)
     * 3. 递归翻译表达式树
     * 
     * 【CQL限制】:
     * - 不支持OR操作,只能使用AND连接多个条件
     * - 如果遇到OR,抛出断言错误
     * 
     * 【参数说明】:
     * @param condition RexNode形式的过滤条件
     * 
     * @return CQL WHERE子句字符串
     */
    private String translateMatch(RexNode condition) {
      // 将条件分解为OR连接的多个子条件
      // RelOptUtil.disjunctions会提取所有OR操作
      List<RexNode> disjunctions = RelOptUtil.disjunctions(condition);
      
      // 如果只有一个子条件(没有OR操作)
      if (disjunctions.size() == 1) {
        // 翻译AND连接的条件
        return translateAnd(disjunctions.get(0));
      } else {
        // 如果有多个子条件(存在OR操作),抛出错误
        // 因为CQL不支持OR操作
        throw new AssertionError("cannot translate " + condition);
      }
    }

    /**
     * 提取字面量的实际值
     * 
     * 【方法作用】:
     * 1. 将RexLiteral对象转换为实际的Java值
     * 2. 处理各种数据类型: 时间戳、日期、字符串、数字等
     * 3. 格式化特殊类型,如时间戳需要转换为ISO格式
     * 
     * 【参数说明】:
     * @param literal RexLiteral对象,表示常量值
     * 
     * @return 实际的值对象,可能是String、Integer、Long等
     */
    private static Object literalValue(RexLiteral literal) {
      // 获取字面量的Comparable值
      Comparable<?> value = RexLiteral.value(literal);
      
      // 根据类型名称处理不同的数据类型
      switch (literal.getTypeName()) {
      // 带时区的时间戳
      case TIMESTAMP_TZ:
        // 断言值是TimestampWithTimeZoneString类型
        assert value instanceof TimestampWithTimeZoneString;
        // 直接转换为字符串,格式如: '2024-01-01 12:00:00+08:00'
        return value.toString();
        
      // 时间戳或本地时间戳
      case TIMESTAMP:
      case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
        // 断言值是TimestampString类型
        assert value instanceof TimestampString;
        // 获取ISO格式的日期时间格式化器
        final SimpleDateFormat dateFormatter =
            getDateFormatter(ISO_DATETIME_FRACTIONAL_SECOND_FORMAT);
        // 格式化时间戳,格式如: '2024-01-01 12:00:00.123'
        return dateFormatter.format(literal.getValue2());
        
      // 日期类型
      case DATE:
        // 断言值是DateString类型
        assert value instanceof DateString;
        // 直接转换为字符串,格式如: '2024-01-01'
        return value.toString();
        
      // 其他类型(整数、浮点数、字符串等)
      default:
        // 获取值对象
        Object val = literal.getValue3();
        // 如果为null,返回"null"字符串,否则返回值本身
        return val == null ? "null" : val;
      }
    }

    /**
     * 将AND连接的多个条件转换为CQL字符串
     * 
     * 【方法作用】:
     * 1. 将条件分解为AND连接的多个子条件
     * 2. 递归翻译每个子条件
     * 3. 用" AND "连接所有子条件
     * 
     * 【示例】:
     * - 输入: user_id = 123 AND age > 18 AND name = 'Alice'
     * - 输出: "user_id = 123 AND age > 18 AND name = 'Alice'"
     * 
     * @param condition AND连接的条件
     * @return CQL字符串
     */
    private String translateAnd(RexNode condition) {
      // 创建 predicates 列表存储翻译后的条件字符串
      List<String> predicates = new ArrayList<>();
      
      // 将条件分解为AND连接的多个子条件
      // RelOptUtil.conjunctions会提取所有AND操作
      for (RexNode node : RelOptUtil.conjunctions(condition)) {
        // 递归翻译每个子条件
        predicates.add(translateMatch2(node));
      }

      // 用" AND "连接所有条件字符串
      // Util.toString的参数: 列表,前缀,分隔符,后缀
      return Util.toString(predicates, "", " AND ", "");
    }

    /**
     * 翻译二元关系表达式(比较操作)
     * 
     * 【方法作用】:
     * 1. 处理各种比较操作符: =, <, <=, >, >=
     * 2. 调用translateBinary方法进行实际的翻译
     * 3. 对于不等操作符,需要考虑操作数的顺序
     * 
     * 【CQL限制】:
     * - 目前主要支持等值操作,不等操作只在聚簇键上支持
     * - 不等操作有顺序限制: 前面的聚簇键必须被等值约束
     * 
     * @param node RexNode形式的比较表达式
     * @return CQL字符串
     */
    private String translateMatch2(RexNode node) {
      // 目前主要使用等值操作,但未来可能支持聚簇键的不等操作
      // 根据表达式类型选择对应的翻译方法
      switch (node.getKind()) {
      // 等于操作
      case EQUALS:
        // 翻译为 "=" 操作,正向和反向操作符都是 "="
        return translateBinary("=", "=", (RexCall) node);
        
      // 小于操作
      case LESS_THAN:
        // 翻译为 "<" 操作,反向操作符是 ">"
        // 例如: age < 18 可以翻译为 age < 18 或 18 > age
        return translateBinary("<", ">", (RexCall) node);
        
      // 小于等于操作
      case LESS_THAN_OR_EQUAL:
        // 翻译为 "<=" 操作,反向操作符是 ">="
        return translateBinary("<=", ">=", (RexCall) node);
        
      // 大于操作
      case GREATER_THAN:
        // 翻译为 ">" 操作,反向操作符是 "<"
        return translateBinary(">", "<", (RexCall) node);
        
      // 大于等于操作
      case GREATER_THAN_OR_EQUAL:
        // 翻译为 ">=" 操作,反向操作符是 "<="
        return translateBinary(">=", "<=", (RexCall) node);
        
      // 不支持的操作符
      default:
        throw new AssertionError("cannot translate " + node);
      }
    }

    /**
     * 翻译二元操作符调用,必要时反转操作数
     * 
     * 【方法作用】:
     * 1. 尝试正向翻译: 左操作数 op 右操作数
     * 2. 如果失败,尝试反向翻译: 右操作数 rop 左操作数
     * 3. 支持操作数的任意顺序,提高灵活性
     * 
     * 【示例】:
     * - 输入: 123 = user_id
     * - 正向尝试: 123 = user_id (失败,因为左操作数不是字段引用)
     * - 反向尝试: user_id = 123 (成功)
     * 
     * @param op 正向操作符
     * @param rop 反向操作符
     * @param call RexCall对象,包含操作符和操作数
     * @return CQL字符串
     */
    private String translateBinary(String op, String rop, RexCall call) {
      // 获取左操作数(第一个操作数)
      final RexNode left = call.operands.get(0);
      
      // 获取右操作数(第二个操作数)
      final RexNode right = call.operands.get(1);
      
      // 尝试正向翻译: left op right
      String expression = translateBinary2(op, left, right);
      if (expression != null) {
        return expression;
      }
      
      // 正向翻译失败,尝试反向翻译: right rop left
      expression = translateBinary2(rop, right, left);
      if (expression != null) {
        return expression;
      }
      
      // 正向和反向都失败,抛出错误
      throw new AssertionError("cannot translate op " + op + " call " + call);
    }

    /**
     * 翻译二元操作符的具体实现
     * 
     * 【方法作用】:
     * 1. 检查右操作数是否为字面量
     * 2. 检查左操作数是否为字段引用或类型转换
     * 3. 生成CQL谓词字符串
     * 
     * 【支持的模式】:
     * - 字段引用 = 字面量: user_id = 123
     * - 类型转换(字段引用) = 字面量: CAST(user_id AS INTEGER) = 123
     * 
     * @param op 操作符
     * @param left 左操作数
     * @param right 右操作数
     * @return CQL字符串,如果不支持则返回null
     */
    private @Nullable String translateBinary2(String op, RexNode left, RexNode right) {
      // 检查右操作数是否为字面量
      switch (right.getKind()) {
      case LITERAL:
        break; // 是字面量,继续处理
      default:
        return null; // 不是字面量,不支持
      }
      
      // 将右操作数转换为RexLiteral
      final RexLiteral rightLiteral = (RexLiteral) right;
      
      // 检查左操作数的类型
      switch (left.getKind()) {
      // 字段引用: user_id
      case INPUT_REF:
        // 将左操作数转换为RexInputRef
        final RexInputRef left1 = (RexInputRef) left;
        
        // 获取字段名称(通过索引映射)
        String name = fieldNames.get(left1.getIndex());
        
        // 翻译为CQL谓词: name op literal
        return translateOp2(op, name, rightLiteral);
        
      // 类型转换: CAST(user_id AS INTEGER)
      case CAST:
        // FIXME: 这不会在所有情况下工作(例如,我们忽略字符串编码)
        // 递归翻译类型转换的操作数
        return translateBinary2(op, ((RexCall) left).operands.get(0), right);
        
      // 不支持的左操作数类型
      default:
        return null;
      }
    }

    /**
     * 组合字段名、操作符和字面量,生成CQL谓词字符串
     * 
     * 【方法作用】:
     * 1. 如果是等值操作,更新分区键和聚簇键的约束状态
     * 2. 格式化字面量值(字符串需要加引号)
     * 3. 生成最终的CQL谓词字符串
     * 
     * 【示例】:
     * - 输入: op="=", name="user_id", right=123
     * - 输出: "user_id = 123"
     * 
     * - 输入: op="=", name="name", right="Alice"
     * - 输出: "name = 'Alice'"
     * 
     * @param op 操作符
     * @param name 字段名称
     * @param right 字面量
     * @return CQL谓词字符串
     */
    private String translateOp2(String op, String name, RexLiteral right) {
      // 如果是等值操作,记录键的约束状态
      if (op.equals("=")) {
        // 从分区键集合中移除该字段
        // 如果字段是分区键,移除后partitionKeys会变小
        partitionKeys.remove(name);
        
        // 如果字段是聚簇键,增加被约束的聚簇键计数
        if (clusteringKeys.contains(name)) {
          restrictedClusteringKeys++;
        }
      }

      // 提取字面量的实际值
      Object value = literalValue(right);
      
      // 转换为字符串
      String valueString = value.toString();
      
      // 如果值是字符串类型,需要添加引号
      if (value instanceof String) {
        // 获取字段的类型信息
        RelDataTypeField field =
            requireNonNull(rowType.getField(name, true, false));
        
        // 获取SQL类型名称
        SqlTypeName typeName = field.getType().getSqlTypeName();
        
        // 如果不是CHAR类型,添加单引号
        // CHAR类型在CQL中不需要引号
        if (typeName != SqlTypeName.CHAR) {
          valueString = "'" + valueString + "'";
        }
      }
      
      // 生成最终的CQL谓词: name op value
      // 例如: "user_id = 123" 或 "name = 'Alice'"
      return name + " " + op + " " + valueString;
    }
  }
}
