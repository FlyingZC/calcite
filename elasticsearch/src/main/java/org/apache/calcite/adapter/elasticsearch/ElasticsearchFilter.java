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
package org.apache.calcite.adapter.elasticsearch; // 定义包名，该类属于Elasticsearch适配器包

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系代数表达式集群，包含类型工厂和查询计划器
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost类，表示关系代数表达式的成本估算
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner类，表示查询优化计划器
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系代数表达式的特征集合（如物理实现方式）
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系代数表达式的基类
import org.apache.calcite.rel.core.Filter; // 导入Filter类，表示过滤操作的关系代数表达式
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系代数表达式的元数据
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示行表达式中的函数调用
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式的基类
import org.apache.calcite.sql.SqlKind; // 导入SqlKind类，表示SQL操作符的种类（如AND、OR、EQUALS等）

import com.fasterxml.jackson.core.JsonGenerator; // 导入JsonGenerator类，用于生成JSON格式的输出
import com.fasterxml.jackson.databind.ObjectMapper; // 导入ObjectMapper类，用于JSON序列化和反序列化

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值

import java.io.IOException; // 导入IOException类，表示输入输出异常
import java.io.StringWriter; // 导入StringWriter类，用于将数据写入字符串缓冲区
import java.io.UncheckedIOException; // 导入UncheckedIOException类，表示未检查的IO异常
import java.util.Iterator; // 导入Iterator类，用于遍历集合

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于检查对象是否为null

/**
 * Implementation of a {@link org.apache.calcite.rel.core.Filter}
 * relational expression in Elasticsearch.
 * Elasticsearch中Filter关系代数表达式的实现类，负责将Calcite的过滤条件转换为Elasticsearch的查询语句
 */
public class ElasticsearchFilter extends Filter implements ElasticsearchRel { // 定义ElasticsearchFilter类，继承自Filter并实现ElasticsearchRel接口
  ElasticsearchFilter(RelOptCluster cluster, RelTraitSet traitSet, RelNode child, // 构造方法：创建ElasticsearchFilter实例
      RexNode condition) { // 参数：condition表示过滤条件，是一个RexNode表达式树
    super(cluster, traitSet, child, condition); // 调用父类Filter的构造方法，初始化集群、特征集、子节点和条件
    assert getConvention() == ElasticsearchRel.CONVENTION; // 断言当前节点的约定是Elasticsearch约定，确保物理实现正确
    assert getConvention() == child.getConvention(); // 断言当前节点的约定与子节点的约定一致，确保特征集匹配
  } // 构造方法结束

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法，计算当前节点的成本
      RelMetadataQuery mq) { // 参数：mq用于查询元数据信息
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq)); // 调用父类方法计算基础成本，并确保不为null
    return cost.multiplyBy(0.1); // 将成本乘以0.1，表示Elasticsearch的过滤操作成本较低，鼓励优化器优先使用
  } // computeSelfCost方法结束

  @Override public Filter copy(RelTraitSet relTraitSet, RelNode input, RexNode condition) { // 重写copy方法，创建当前节点的副本
    return new ElasticsearchFilter(getCluster(), relTraitSet, input, condition); // 返回一个新的ElasticsearchFilter实例，使用新的特征集、输入节点和条件
  } // copy方法结束

  @Override public void implement(Implementor implementor) { // 重写implement方法，实现当前节点到Elasticsearch查询的转换
    implementor.visitChild(0, getInput()); // 访问子节点（索引0），先处理输入节点，生成子查询
    ObjectMapper mapper = implementor.elasticsearchTable.mapper; // 从实现器中获取Elasticsearch表的ObjectMapper，用于JSON序列化
    PredicateAnalyzerTranslator translator = new PredicateAnalyzerTranslator(mapper); // 创建谓词分析翻译器，用于将RexNode条件转换为Elasticsearch查询
    try { // 开始异常处理块
      implementor.add(translator.translateMatch(condition)); // 将过滤条件翻译为Elasticsearch查询字符串，并添加到实现器中
    } catch (IOException e) { // 捕获IO异常
      throw new UncheckedIOException(e); // 将检查型IO异常转换为未检查异常抛出
    } catch (PredicateAnalyzer.ExpressionNotAnalyzableException e) { // 捕获表达式无法分析异常
      throw new RuntimeException(e); // 将异常包装为运行时异常抛出
    } // 异常处理块结束
  } // implement方法结束

  /**
   * New version of translator which uses visitor pattern
   * and allow to process more complex (boolean) predicates.
   * 新版本的翻译器，使用访问者模式，允许处理更复杂的布尔谓词表达式
   */
  static class PredicateAnalyzerTranslator { // 定义静态内部类PredicateAnalyzerTranslator，负责将RexNode条件翻译为Elasticsearch查询
    private final ObjectMapper mapper; // 成员变量：mapper用于JSON序列化和反序列化，是final类型确保不可变

    PredicateAnalyzerTranslator(final ObjectMapper mapper) { // 构造方法：创建PredicateAnalyzerTranslator实例
      this.mapper = requireNonNull(mapper, "mapper"); // 初始化mapper成员变量，并检查mapper不为null，否则抛出NullPointerException
    } // 构造方法结束

    String translateMatch(RexNode condition) throws IOException, // 定义translateMatch方法，将RexNode条件翻译为Elasticsearch查询字符串
        PredicateAnalyzer.ExpressionNotAnalyzableException { // 方法可能抛出IO异常或表达式无法分析异常

      StringWriter writer = new StringWriter(); // 创建StringWriter对象，用于在内存中构建JSON字符串
      JsonGenerator generator = mapper.getFactory().createGenerator(writer); // 从mapper获取JsonGenerator工厂，创建JSON生成器
      boolean disMax = condition.isA(SqlKind.OR); // 检查条件是否为OR操作，如果是则使用dis_max查询
      Iterator<RexNode> operands = ((RexCall) condition).getOperands().iterator(); // 获取条件的操作数迭代器，用于遍历OR的子条件
      while (operands.hasNext() && !disMax) { // 遍历操作数，直到发现OR操作或遍历完毕
        if (operands.next().isA(SqlKind.OR)) { // 检查当前操作数是否为OR操作
          disMax = true; // 如果发现嵌套的OR操作，设置disMax标志为true
          break; // 跳出循环
        } // if语句结束
      } // while循环结束
      if (disMax) { // 如果需要使用dis_max查询
        QueryBuilders.disMaxQueryBuilder(PredicateAnalyzer.analyze(condition)).writeJson(generator); // 使用disMaxQueryBuilder构建查询，分析条件并写入JSON生成器
      } else { // 否则使用常量分数查询
        QueryBuilders.constantScoreQuery(PredicateAnalyzer.analyze(condition)).writeJson(generator); // 使用constantScoreQuery构建查询，分析条件并写入JSON生成器
      } // if-else语句结束
      generator.flush(); // 刷新JSON生成器，确保所有数据写入writer
      generator.close(); // 关闭JSON生成器，释放资源
      return "{\"query\" : " + writer.toString() + "}"; // 返回完整的Elasticsearch查询JSON字符串，包含query字段
    } // translateMatch方法结束
  } // PredicateAnalyzerTranslator类结束

} // ElasticsearchFilter类结束
