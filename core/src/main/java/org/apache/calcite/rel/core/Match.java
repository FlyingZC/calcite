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
package org.apache.calcite.rel.core; // 包声明：定义Match类所在的包路径，这是Calcite核心包的一部分

import org.apache.calcite.plan.RelOptCluster; // 导入：关系优化集群类，用于管理关系表达式和元数据
import org.apache.calcite.plan.RelTraitSet; // 导入：关系特征集合，描述关系表达式的物理属性
import org.apache.calcite.rel.RelCollation; // 导入：关系排序规范，定义数据的排序方式
import org.apache.calcite.rel.RelNode; // 导入：关系表达式接口，是所有关系表达式的基类
import org.apache.calcite.rel.RelWriter; // 导入：关系表达式写入器，用于生成可读的查询计划描述
import org.apache.calcite.rel.SingleRel; // 导入：单一输入关系表达式基类，Match继承此类
import org.apache.calcite.rel.type.RelDataType; // 导入：关系数据类型，描述行的结构
import org.apache.calcite.rex.RexCall; // 导入：Rex表达式调用，表示函数调用或操作符应用
import org.apache.calcite.rex.RexNode; // 导入：Rex表达式节点接口，所有行表达式的基类
import org.apache.calcite.rex.RexPatternFieldRef; // 导入：Rex模式字段引用，用于引用模式中的变量
import org.apache.calcite.rex.RexVisitorImpl; // 导入：Rex访问者实现基类，用于遍历Rex表达式树
import org.apache.calcite.sql.SqlAggFunction; // 导入：SQL聚合函数接口
import org.apache.calcite.sql.fun.SqlBitOpAggFunction; // 导入：位操作聚合函数（BIT_AND, BIT_OR, BIT_XOR）
import org.apache.calcite.sql.fun.SqlMinMaxAggFunction; // 导入：MIN/MAX聚合函数
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入：标准操作符表，包含标准SQL函数和操作符
import org.apache.calcite.sql.fun.SqlSumAggFunction; // 导入：SUM聚合函数
import org.apache.calcite.sql.fun.SqlSumEmptyIsZeroAggFunction; // 导入：SUM0聚合函数，空集返回0而非null
import org.apache.calcite.util.ImmutableBitSet; // 导入：不可变位集合，用于高效表示列索引集合

import com.google.common.collect.ImmutableMap; // 导入：Guava不可变Map实现
import com.google.common.collect.ImmutableSortedMap; // 导入：Guava不可变排序Map实现
import com.google.common.collect.ImmutableSortedSet; // 导入：Guava不可变排序Set实现

import org.checkerframework.checker.nullness.qual.Nullable; // 导入：空值检查注解，标记可能为null的值

import java.util.HashSet; // 导入：HashSet集合实现
import java.util.List; // 导入：List接口，有序集合
import java.util.Map; // 导入：Map接口，键值对集合
import java.util.NavigableSet; // 导入：NavigableSet接口，可导航的有序集合
import java.util.Set; // 导入：Set接口，无序不重复集合
import java.util.SortedSet; // 导入：SortedSet接口，有序集合
import java.util.TreeMap; // 导入：TreeMap实现，基于红黑树的排序Map
import java.util.TreeSet; // 导入：TreeSet实现，基于红黑树的排序Set

import static com.google.common.base.Preconditions.checkArgument; // 静态导入：参数前置条件检查方法

import static java.util.Objects.requireNonNull; // 静态导入：对象非空检查方法

/**
 * Relational expression that represent a MATCH_RECOGNIZE node. // 表示MATCH_RECOGNIZE关系表达式节点，用于实现SQL的模式匹配功能
 *
 * <p>Each output row has the columns defined in the measure statements. // 每个输出行包含MEASURE语句中定义的列
 * 
 * Match类是Calcite中实现SQL标准MATCH_RECOGNIZE子句的核心类，该子句用于在数据流中进行复杂模式匹配
 * MATCH_RECOGNIZE是SQL:2011标准引入的行模式识别功能，常用于时序数据分析、欺诈检测、日志分析等场景
 * 该类继承自SingleRel，表示只有一个输入关系表达式
 * Match是抽象类，具体的实现由EnumerableMatch等子类提供
 * 
 * MATCH_RECOGNIZE语法包含以下关键组件：
 * 1. PARTITION BY：将数据分区，每个分区独立进行模式匹配
 * 2. ORDER BY：在每个分区内对行进行排序，确保时间顺序
 * 3. MEASURES：定义输出列的计算方式
 * 4. PATTERN：使用正则表达式定义要匹配的模式
 * 5. DEFINE：为模式变量定义布尔条件
 * 6. SUBSET：定义模式变量的子集
 * 7. AFTER：定义匹配后如何处理数据（SKIP TO）
 * 8. WITHIN：定义匹配的时间窗口限制（可选）
 * 9. ALL ROWS PER MATCH vs ONE ROW PER MATCH：定义输出格式
 * 
 * 示例SQL：
 * SELECT *
 * FROM Ticker
 * MATCH_RECOGNIZE (
 *   PARTITION BY symbol
 *   ORDER BY tstamp
 *   MEASURES STRT.tstamp AS start_tstamp,
 *            LAST(DOWN.price) AS bottom_price,
 *            UP.price AS end_price
 *   ONE ROW PER MATCH
 *   AFTER MATCH SKIP PAST LAST ROW
 *   PATTERN (STRT DOWN+ UP+)
 *   DEFINE
 *     DOWN AS DOWN.price < PREV(DOWN.price),
 *     UP AS UP.price > PREV(UP.price)
 * ) MR
 */
public abstract class Match extends SingleRel { // Match类：抽象的MATCH_RECOGNIZE关系表达式，继承SingleRel表示单输入关系操作
  //~ Instance fields --------------------------------------------- // 实例字段区域标记
  private static final String STAR = "*"; // STAR常量：表示通配符，用于匹配所有模式变量
  protected final ImmutableMap<String, RexNode> measures; // measures：MEASURE子句定义的输出列，键为列名，值为Rex表达式
  protected final RexNode pattern; // pattern：PATTERN子句定义的模式正则表达式，描述要匹配的模式结构
  protected final boolean strictStart; // strictStart：是否严格开始模式，表示模式必须从分区第一行开始匹配
  protected final boolean strictEnd; // strictEnd：是否严格结束模式，表示模式必须匹配到分区最后一行
  protected final boolean allRows; // allRows：是否输出所有匹配行（ALL ROWS PER MATCH），false表示只输出一行（ONE ROW PER MATCH）
  protected final RexNode after; // after：AFTER MATCH SKIP子句，定义匹配后如何定位下一个匹配的起始位置
  protected final ImmutableMap<String, RexNode> patternDefinitions; // patternDefinitions：DEFINE子句定义的模式变量条件，键为变量名，值为布尔Rex表达式
  protected final Set<RexMRAggCall> aggregateCalls; // aggregateCalls：在patternDefinitions和measures中找到的所有聚合函数调用集合
  protected final Map<String, SortedSet<RexMRAggCall>> aggregateCallsPreVar; // aggregateCallsPreVar：按模式变量分组的聚合函数调用，键为变量名，值为该变量相关的聚合函数集合
  protected final ImmutableMap<String, SortedSet<String>> subsets; // subsets：SUBSET子句定义的变量子集，键为子集名称，值为该子集包含的变量名集合
  protected final ImmutableBitSet partitionKeys; // partitionKeys：PARTITION BY子句指定的分区列索引集合，用于将数据分组
  protected final RelCollation orderKeys; // orderKeys：ORDER BY子句指定的排序列信息，定义每个分区内的行顺序
  protected final @Nullable RexNode interval; // interval：WITHIN子句定义的时间窗口间隔，可为null表示无时间限制

  //~ Constructors ----------------------------------------------- // 构造方法区域标记

  /**
   * Creates a Match. // 创建Match关系表达式实例
   *
   * @param cluster Cluster // cluster：关系优化集群，提供元数据管理等功能
   * @param traitSet Trait set // traitSet：关系特征集合，描述物理属性如排序、分布等
   * @param input Input relational expression // input：输入关系表达式，通常是已排序的表
   * @param rowType Row type // rowType：输出行的数据类型，由MEASURE子句定义
   * @param pattern Regular expression that defines pattern variables // pattern：定义模式变量的正则表达式，如"STRT DOWN+ UP+"
   * @param strictStart Whether it is a strict start pattern // strictStart：是否为严格开始模式，true表示必须从分区首行开始
   * @param strictEnd Whether it is a strict end pattern // strictEnd：是否为严格结束模式，true表示必须匹配到分区末尾
   * @param patternDefinitions Pattern definitions // patternDefinitions：模式变量定义，指定每个模式变量的匹配条件
   * @param measures Measure definitions // measures：度量定义，指定输出列的计算表达式
   * @param after After match definitions // after：匹配后跳过策略，如SKIP PAST LAST ROW
   * @param subsets Subsets of pattern variables // subsets：模式变量子集定义，用于简化模式表达式
   * @param allRows Whether all rows per match (false means one row per match) // allRows：true表示输出所有匹配行，false表示每个匹配只输出一行
   * @param partitionKeys Partition by columns // partitionKeys：分区列索引，用于将数据分组独立匹配
   * @param orderKeys Order by columns // orderKeys：排序规范，定义每个分区内的行顺序
   * @param interval Interval definition, null if WITHIN clause is not defined // interval：时间窗口定义，null表示无时间限制
   */
  protected Match(RelOptCluster cluster, RelTraitSet traitSet, RelNode input, // 构造方法：初始化Match关系表达式
      RelDataType rowType, RexNode pattern, // 参数：输出行类型和模式表达式
      boolean strictStart, boolean strictEnd, // 参数：严格开始和严格结束标志
      Map<String, RexNode> patternDefinitions, Map<String, RexNode> measures, // 参数：模式定义和度量定义
      RexNode after, Map<String, ? extends SortedSet<String>> subsets, // 参数：匹配后策略和变量子集
      boolean allRows, ImmutableBitSet partitionKeys, RelCollation orderKeys, // 参数：输出格式、分区键和排序键
      @Nullable RexNode interval) { // 参数：时间窗口间隔（可为null）
    super(cluster, traitSet, input); // 调用父类SingleRel构造方法，设置集群、特征集和输入
    this.rowType = requireNonNull(rowType, "rowType"); // 设置输出行类型，非空检查
    this.pattern = requireNonNull(pattern, "pattern"); // 设置模式表达式，非空检查
    checkArgument(!patternDefinitions.isEmpty()); // 验证模式定义不能为空，至少需要一个模式变量
    this.strictStart = strictStart; // 设置严格开始标志
    this.strictEnd = strictEnd; // 设置严格结束标志
    this.patternDefinitions = ImmutableMap.copyOf(patternDefinitions); // 复制模式定义为不可变Map
    this.measures = ImmutableMap.copyOf(measures); // 复制度量定义为不可变Map
    this.after = requireNonNull(after, "after"); // 设置匹配后策略，非空检查
    this.subsets = copyMap(subsets); // 复制子集定义为不可变排序Map
    this.allRows = allRows; // 设置输出格式标志
    this.partitionKeys = requireNonNull(partitionKeys, "partitionKeys"); // 设置分区键，非空检查
    this.orderKeys = requireNonNull(orderKeys, "orderKeys"); // 设置排序规范，非空检查
    this.interval = interval; // 设置时间窗口间隔，可为null

    final AggregateFinder aggregateFinder = new AggregateFinder(); // 创建聚合函数查找器，用于扫描表达式中的聚合函数
    for (RexNode rex : this.patternDefinitions.values()) { // 遍历所有模式定义表达式
      if (rex instanceof RexCall) { // 如果是函数调用表达式
        aggregateFinder.go((RexCall) rex); // 使用查找器扫描其中的聚合函数
      } // 结束if检查
    } // 结束循环

    for (RexNode rex : this.measures.values()) { // 遍历所有度量定义表达式
      if (rex instanceof RexCall) { // 如果是函数调用表达式
        aggregateFinder.go((RexCall) rex); // 使用查找器扫描其中的聚合函数
      } // 结束if检查
    } // 结束循环

    aggregateCalls = ImmutableSortedSet.copyOf(aggregateFinder.aggregateCalls); // 从查找器中获取所有聚合函数调用并保存为不可变排序Set
    aggregateCallsPreVar = // 初始化按变量分组的聚合函数映射
        copyMap(aggregateFinder.aggregateCallsPerVar); // 保存每个模式变量相关的聚合函数调用
  } // 构造方法结束

  /** Creates an immutable map of a map of sorted sets. */ // 创建一个包含排序集的不可变映射
  private static <K extends Comparable<K>, V> // 泛型方法：K必须是可比较的，用于排序
      ImmutableSortedMap<K, SortedSet<V>> // 返回不可变排序Map，值为排序Set
      copyMap(Map<K, ? extends SortedSet<V>> map) { // 参数：原始Map，值为排序Set
    final ImmutableSortedMap.Builder<K, SortedSet<V>> b = // 创建不可变排序Map构建器
        ImmutableSortedMap.naturalOrder(); // 使用自然顺序（升序）排序
    for (Map.Entry<K, ? extends SortedSet<V>> e : map.entrySet()) { // 遍历原始Map的所有条目
      b.put(e.getKey(), ImmutableSortedSet.copyOf(e.getValue())); // 将键和不可变排序Set添加到构建器
    } // 结束循环
    return b.build(); // 构建并返回不可变排序Map
  } // copyMap方法结束

  //~ Methods -------------------------------------------------- // 方法区域标记

  public ImmutableMap<String, RexNode> getMeasures() { // Getter方法：获取度量定义
    return measures; // 返回不可变的度量定义Map
  } // getMeasures方法结束

  public RexNode getAfter() { // Getter方法：获取匹配后策略
    return after; // 返回AFTER MATCH SKIP表达式
  } // getAfter方法结束

  public RexNode getPattern() { // Getter方法：获取模式表达式
    return pattern; // 返回PATTERN子句定义的正则表达式
  } // getPattern方法结束

  public boolean isStrictStart() { // Getter方法：检查是否为严格开始模式
    return strictStart; // 返回严格开始标志
  } // isStrictStart方法结束

  public boolean isStrictEnd() { // Getter方法：检查是否为严格结束模式
    return strictEnd; // 返回严格结束标志
  } // isStrictEnd方法结束

  public boolean isAllRows() { // Getter方法：检查输出格式
    return allRows; // 返回true表示ALL ROWS PER MATCH，false表示ONE ROW PER MATCH
  } // isAllRows方法结束

  public ImmutableMap<String, RexNode> getPatternDefinitions() { // Getter方法：获取模式定义
    return patternDefinitions; // 返回不可变的模式定义Map
  } // getPatternDefinitions方法结束

  public ImmutableMap<String, SortedSet<String>> getSubsets() { // Getter方法：获取变量子集
    return subsets; // 返回不可变的子集定义Map
  } // getSubsets方法结束

  public ImmutableBitSet getPartitionKeys() { // Getter方法：获取分区键
    return partitionKeys; // 返回分区列索引的不可变位集合
  } // getPartitionKeys方法结束

  public RelCollation getOrderKeys() { // Getter方法：获取排序规范
    return orderKeys; // 返回排序列的排序规范
  } // getOrderKeys方法结束

  public @Nullable RexNode getInterval() { // Getter方法：获取时间窗口间隔
    return interval; // 返回WITHIN子句定义的时间间隔，可能为null
  } // getInterval方法结束

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写explainTerms方法：生成可读的查询计划描述
    return super.explainTerms(pw) // 调用父类方法获取基础描述
        .item("partition", getPartitionKeys().asList()) // 添加分区键信息到描述
        .item("order", getOrderKeys()) // 添加排序信息到描述
        .item("outputFields", getRowType().getFieldNames()) // 添加输出字段名列表
        .item("allRows", isAllRows()) // 添加输出格式标志
        .item("after", getAfter()) // 添加匹配后策略
        .item("pattern", getPattern()) // 添加模式表达式
        .item("isStrictStarts", isStrictStart()) // 添加严格开始标志
        .item("isStrictEnds", isStrictEnd()) // 添加严格结束标志
        .itemIf("interval", getInterval(), getInterval() != null) // 条件添加时间窗口（如果存在）
        .item("subsets", getSubsets().values().asList()) // 添加子集定义列表
        .item("patternDefinitions", getPatternDefinitions().values().asList()) // 添加模式定义列表
        .item("inputFields", getInput().getRowType().getFieldNames()); // 添加输入字段名列表
  } // explainTerms方法结束

  /**
   * Find aggregate functions in operands. // 在操作数中查找聚合函数的内部类
   * 
   * AggregateFinder是一个Rex表达式访问者，用于递归遍历表达式树并收集所有聚合函数调用
   * 它能够识别多种聚合函数：SUM, SUM0, MAX, MIN, COUNT, ANY_VALUE, BIT_AND, BIT_OR, BIT_XOR
   * 对于每个找到的聚合函数，它会：
   * 1. 创建对应的RexMRAggCall对象并添加到全局聚合函数集合
   * 2. 分析聚合函数参数中引用的模式变量
   * 3. 将聚合函数按模式变量分组存储
   * 
   * 这个类在Match构造方法中被使用，用于扫描patternDefinitions和measures中的聚合函数
   * 收集的聚合函数信息用于后续的查询优化和执行计划生成
   */
    private static class AggregateFinder extends RexVisitorImpl<Void> { // AggregateFinder：聚合函数查找器，继承Rex访问者
      final NavigableSet<RexMRAggCall> aggregateCalls = new TreeSet<>(); // aggregateCalls：所有找到的聚合函数调用集合，使用TreeSet自动排序
      final Map<String, NavigableSet<RexMRAggCall>> aggregateCallsPerVar = // aggregateCallsPerVar：按模式变量分组的聚合函数映射
          new TreeMap<>(); // 使用TreeMap按变量名排序
  
      AggregateFinder() { // 构造方法：初始化聚合函数查找器
        super(true); // 调用父类构造方法，参数true表示深度优先遍历
      } // AggregateFinder构造方法结束
  
      @Override public Void visitCall(RexCall call) { // 重写visitCall方法：访问函数调用节点
        SqlAggFunction aggFunction = null; // 初始化聚合函数为null
        switch (call.getKind()) { // 根据函数类型判断是否为聚合函数
        case SUM: // 如果是SUM函数
          aggFunction = new SqlSumAggFunction(call.getType()); // 创建SUM聚合函数对象
          break; // 跳出switch
        case SUM0: // 如果是SUM0函数（空集返回0）
          aggFunction = new SqlSumEmptyIsZeroAggFunction(); // 创建SUM0聚合函数对象
          break; // 跳出switch
        case MAX: // 如果是MAX函数
        case MIN: // 如果是MIN函数
          aggFunction = new SqlMinMaxAggFunction(call.getKind()); // 创建MIN/MAX聚合函数对象
          break; // 跳出switch
        case COUNT: // 如果是COUNT函数
          aggFunction = SqlStdOperatorTable.COUNT; // 获取COUNT聚合函数
          break; // 跳出switch
        case ANY_VALUE: // 如果是ANY_VALUE函数
          aggFunction = SqlStdOperatorTable.ANY_VALUE; // 获取ANY_VALUE聚合函数
          break; // 跳出switch
        case BIT_AND: // 如果是BIT_AND函数
        case BIT_OR: // 如果是BIT_OR函数
        case BIT_XOR: // 如果是BIT_XOR函数
          aggFunction = new SqlBitOpAggFunction(call.getKind()); // 创建位操作聚合函数对象
          break; // 跳出switch
        default: // 如果不是聚合函数
          visitEach(call.operands); // 递归访问操作数，继续查找
        } // switch结束
        if (aggFunction != null) { // 如果识别到聚合函数
          RexMRAggCall aggCall = // 创建RexMRAggCall对象表示这个聚合函数调用
              new RexMRAggCall(aggFunction, call.getType(), call.getOperands(), // 参数：函数、类型、操作数
                  aggregateCalls.size()); // 参数：序号，用于唯一标识
          aggregateCalls.add(aggCall); // 将聚合函数调用添加到全局集合
          Set<String> pv = new PatternVarFinder().go(call.getOperands()); // 使用PatternVarFinder查找聚合函数参数中引用的模式变量
          if (pv.isEmpty()) { // 如果没有引用任何模式变量
            pv.add(STAR); // 添加通配符*，表示适用于所有变量
          } // 结束if检查
          for (String alpha : pv) { // 遍历所有相关的模式变量
            final NavigableSet<RexMRAggCall> set; // 声明该变量的聚合函数集合
            if (aggregateCallsPerVar.containsKey(alpha)) { // 如果该变量已有聚合函数集合
              set = aggregateCallsPerVar.get(alpha); // 获取现有集合
            } else { // 如果该变量还没有聚合函数集合
              set = new TreeSet<>(); // 创建新的TreeSet
              aggregateCallsPerVar.put(alpha, set); // 将新集合添加到映射中
            } // 结束if-else
            boolean update = true; // 初始化更新标志为true
            for (RexMRAggCall rex : set) { // 遍历集合中已有的聚合函数
              if (rex.equals(aggCall)) { // 如果已存在相同的聚合函数
                update = false; // 设置更新标志为false
                break; // 跳出循环
              } // 结束if检查
            } // 结束循环
            if (update) { // 如果需要更新
              set.add(aggCall); // 将聚合函数添加到集合中
            } // 结束if检查
          } // 结束循环
        } // 结束if检查
        return null; // 返回null，因为这是Void类型的访问者
      } // visitCall方法结束
  
      public void go(RexCall call) { // go方法：启动查找过程
        call.accept(this); // 让RexCall接受此访问者，开始遍历
      } // go方法结束
    } // AggregateFinder内部类结束
  /**
   * Visits the operands of an aggregate call to retrieve relevant pattern
   * variables. // 访问聚合函数的操作数以检索相关模式变量的内部类
   * 
   * PatternVarFinder是一个Rex表达式访问者，专门用于查找表达式中引用的模式变量
   * 模式变量是在MATCH_RECOGNIZE的PATTERN子句中定义的，如STRT、DOWN、UP等
   * 在MEASURE和DEFINE子句中，可以通过RexPatternFieldRef引用这些模式变量
   * 例如：LAST(UP.price)引用了模式变量UP，AVG(DOWN.price)引用了模式变量DOWN
   * 
   * 这个类的主要功能：
   * 1. 递归遍历表达式树
   * 2. 收集所有RexPatternFieldRef节点中引用的模式变量名（alpha）
   * 3. 返回找到的所有模式变量集合
   * 
   * PatternVarFinder在AggregateFinder中被使用，用于确定聚合函数与哪些模式变量相关
   */
    private static class PatternVarFinder extends RexVisitorImpl<Void> { // PatternVarFinder：模式变量查找器，继承Rex访问者
      final Set<String> patternVars = new HashSet<>(); // patternVars：找到的模式变量名集合，使用HashSet避免重复
  
      PatternVarFinder() { // 构造方法：初始化模式变量查找器
        super(true); // 调用父类构造方法，参数true表示深度优先遍历
      } // PatternVarFinder构造方法结束
  
      @Override public Void visitPatternFieldRef(RexPatternFieldRef fieldRef) { // 重写visitPatternFieldRef方法：访问模式字段引用节点
        patternVars.add(fieldRef.getAlpha()); // 将模式变量名（alpha）添加到集合中
        return null; // 返回null，因为这是Void类型的访问者
      } // visitPatternFieldRef方法结束
  
      @Override public Void visitCall(RexCall call) { // 重写visitCall方法：访问函数调用节点
        visitEach(call.operands); // 递归访问操作数，继续查找模式变量引用
        return null; // 返回null，因为这是Void类型的访问者
      } // visitCall方法结束
  
      public Set<String> go(RexNode rex) { // go方法：从单个RexNode开始查找
        rex.accept(this); // 让RexNode接受此访问者，开始遍历
        return patternVars; // 返回找到的模式变量集合
      } // go方法结束
  
      public Set<String> go(List<RexNode> rexNodeList) { // go方法：从RexNode列表开始查找
        visitEach(rexNodeList); // 访问列表中的每个节点
        return patternVars; // 返回找到的模式变量集合
      } // go方法结束
    } // PatternVarFinder内部类结束
  /**
   * Aggregate calls in match recognize. // MATCH_RECOGNIZE中的聚合函数调用
   * 
   * RexMRAggCall（Match Recognize Aggregate Call）是专门用于MATCH_RECOGNIZE的聚合函数调用表示
   * 它继承自RexCall，并实现了Comparable接口以便排序
   * 
   * 与普通RexCall的区别：
   * 1. 添加了ordinal字段，表示聚合函数在全局聚合函数集合中的序号
   * 2. 实现了Comparable接口，基于toString()结果进行比较
   * 3. 重写了equals和hashCode方法，确保相同的聚合函数调用被视为相等
   * 4. 在构造时就计算digest（字符串表示），因为类是final的
   * 
   * 这个类在查询优化和执行过程中用于：
   * 1. 唯一标识每个聚合函数调用
   * 2. 在排序和去重时保持一致性
   * 3. 生成可读的查询计划描述
   * 
   * 示例：
   * SUM(UP.price) 会创建一个RexMRAggCall对象
   * AVG(DOWN.price) 会创建另一个RexMRAggCall对象
   * 它们会根据toString()结果进行排序和比较
   */
    public static final class RexMRAggCall extends RexCall // RexMRAggCall：MATCH_RECOGNIZE聚合函数调用，继承RexCall
        implements Comparable<RexMRAggCall> { // 实现Comparable接口以支持排序
      public final int ordinal; // ordinal：聚合函数在全局集合中的序号，用于唯一标识
  
      RexMRAggCall(SqlAggFunction aggFun, // 构造方法：创建RexMRAggCall实例
          RelDataType type, // 参数：聚合函数的返回类型
          List<RexNode> operands, // 参数：聚合函数的操作数列表
          int ordinal) { // 参数：聚合函数的序号
        super(type, aggFun, operands); // 调用父类RexCall构造方法
        this.ordinal = ordinal; // 保存序号
        digest = toString(); // 计算并保存字符串表示（digest），因为类是final的可以在这里计算
      } // RexMRAggCall构造方法结束
  
      @Override public int compareTo(RexMRAggCall o) { // 重写compareTo方法：实现比较逻辑
        return toString().compareTo(o.toString()); // 基于字符串表示进行比较
      } // compareTo方法结束
  
      @Override public boolean equals(@Nullable Object obj) { // 重写equals方法：实现相等比较
        return obj == this // 如果是同一个对象，返回true
            || obj instanceof RexMRAggCall // 或者是RexMRAggCall类型
            && toString().equals(obj.toString()); // 且字符串表示相同，返回true
      } // equals方法结束
  
      @Override public int hashCode() { // 重写hashCode方法：实现哈希码计算
        return toString().hashCode(); // 基于字符串表示计算哈希码
      } // hashCode方法结束
    } // RexMRAggCall内部类结束
  } // Match类结束
