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
package org.apache.calcite.adapter.elasticsearch;

import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.util.Pair;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Elasticsearch适配器中Project关系表达式的实现类
 * Project操作在SQL中对应SELECT子句，用于从输入关系中投影出指定的列或计算表达式
 * 该类负责将Calcite的Project操作转换为Elasticsearch查询中的_source字段和script_fields
 * _source用于指定返回的原始字段，script_fields用于计算动态字段
 * 继承自Calcite的Project基类，并实现ElasticsearchRel接口以支持Elasticsearch特定的实现
 */
public class ElasticsearchProject extends Project implements ElasticsearchRel { // ElasticsearchProject类：继承自Project（Calcite的投影操作基类）并实现ElasticsearchRel接口（Elasticsearch关系表达式接口），是Elasticsearch适配器中处理投影操作的核心类
  ElasticsearchProject(RelOptCluster cluster, RelTraitSet traitSet, RelNode input,
      List<? extends RexNode> projects, RelDataType rowType) { // 构造方法：创建ElasticsearchProject实例，参数：cluster-优化集群，traitSet-关系表达式特征集（包含物理实现约定），input-输入关系节点，projects-投影表达式列表（对应SELECT子句），rowType-输出的行类型（包含字段名和类型）
    super(cluster, traitSet, ImmutableList.of(), input, projects, rowType, ImmutableSet.of()); // 调用父类Project构造方法：ImmutableList.of()作为flags标志位（表示不需要特殊标志），ImmutableSet.of()作为变量名集合（表示没有变量名），确保构造的Project节点使用Elasticsearch的CONVENTION约定
    assert getConvention() == ElasticsearchRel.CONVENTION; // 断言：验证当前节点的Convention特征必须等于ElasticsearchRel.CONVENTION，确保这是一个Elasticsearch物理实现节点
    assert getConvention() == input.getConvention(); // 断言：验证输入节点的Convention特征也必须等于ElasticsearchRel.CONVENTION，确保输入节点和当前节点的物理实现约定一致
  }

  @Override public Project copy(RelTraitSet relTraitSet, RelNode input, List<RexNode> projects,
      RelDataType relDataType) { // copy方法：创建当前ElasticsearchProject节点的副本，参数：relTraitSet-新的关系表达式特征集（用于优化时修改特征），input-新的输入节点，projects-新的投影表达式列表，relDataType-新的输出行类型
    return new ElasticsearchProject(getCluster(), traitSet, input, projects, relDataType); // 创建并返回新的ElasticsearchProject实例：getCluster()获取当前集群，traitSet使用新的特征集，input使用新的输入节点，projects使用新的投影表达式，relDataType使用新的行类型
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) { // computeSelfCost方法：计算当前ElasticsearchProject节点的执行成本，参数：planner-优化器实例，mq-元数据查询对象（用于获取表的行数等统计信息），返回值：RelOptCost对象表示执行成本（可能为null），用于优化器选择最优执行计划
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq)); // 调用父类Project的computeSelfCost方法计算基础成本，requireNonNull确保成本不为null，cost包含CPU、I/O和内存等成本指标
    return cost.multiplyBy(0.1); // 将计算出的基础成本乘以0.1倍，因为Elasticsearch的投影操作（_source字段选择）成本相对较低，大部分工作在数据传输时完成，这样可以鼓励优化器优先使用Elasticsearch的投影操作
  }

  @Override public void implement(Implementor implementor) { // implement方法：实现ElasticsearchRel接口，将Project关系表达式转换为Elasticsearch查询语句，参数：implementor-实现器对象，用于构建和存储Elasticsearch查询的各个部分
    implementor.visitChild(0, getInput()); // 访问输入节点（索引0）：调用输入节点的implement方法，确保输入节点的Elasticsearch查询先被实现并添加到implementor中，这样可以构建完整的查询树

    final List<String> inFields =
            ElasticsearchRules.elasticsearchFieldNames(getInput().getRowType()); // 获取输入节点的字段名列表：getInput().getRowType()获取输入的行类型，ElasticsearchRules.elasticsearchFieldNames()将行类型转换为Elasticsearch字段名列表，用于后续翻译投影表达式
    final ElasticsearchRules.RexToElasticsearchTranslator translator =
            new ElasticsearchRules.RexToElasticsearchTranslator(
                    (JavaTypeFactory) getCluster().getTypeFactory(), inFields); // 创建Rex到Elasticsearch的转换器：getCluster().getTypeFactory()获取类型工厂并转换为JavaTypeFactory，inFields是输入字段名列表，这个转换器负责将Calcite的RexNode表达式转换为Elasticsearch脚本表达式

    final List<String> fields = new ArrayList<>(); // 创建普通字段列表：用于存储需要从Elasticsearch的_source字段直接返回的字段，这些字段不需要计算，直接从文档中提取
    final List<String> scriptFields = new ArrayList<>(); // 创建脚本字段列表：用于存储需要通过Elasticsearch的Painless脚本计算的字段，这些字段包含复杂的表达式计算
    // registers wherever "select *" is present
    boolean hasSelectStar = false; // 标记是否存在SELECT *操作：如果SQL查询中包含SELECT *，则此标志为true，此时不需要在查询中指定_source字段，直接返回所有字段
    for (Pair<RexNode, String> pair : getNamedProjects()) { // 遍历所有命名的投影表达式：getNamedProjects()返回Pair列表，每个Pair包含RexNode表达式和对应的字段名（如"SELECT emp.name AS emp_name"中的name和emp_name）
      final String name = pair.right; // 获取投影表达式的输出字段名：如"SELECT emp.name AS emp_name"中的"emp_name"，这是结果集中的列名
      final String expr = pair.left.accept(translator); // 将RexNode表达式转换为Elasticsearch脚本表达式：pair.left是RexNode表达式（如字段引用、函数调用等），调用translator的accept方法进行转换，返回Elasticsearch可识别的脚本字符串

      // "select *" present?
      hasSelectStar |= ElasticsearchConstants.isSelectAll(name); // 检查当前字段是否是SELECT *：ElasticsearchConstants.isSelectAll(name)判断字段名是否表示选择所有字段，如果是则将hasSelectStar标志设为true

      if (ElasticsearchRules.isItem(pair.left)) { // 判断表达式是否是简单的字段引用：ElasticsearchRules.isItem()检查RexNode是否是字段引用（如"emp.name"），如果是则直接从_source获取
        implementor.addExpressionItemMapping(name, expr); // 在实现器中添加表达式项映射：name是输出字段名，expr是Elasticsearch字段表达式，这个映射用于后续处理（如排序、过滤等）
        fields.add(expr); // 将字段表达式添加到普通字段列表：expr是Elasticsearch字段名（如"emp.name"），这些字段将包含在_source字段中返回
      } else if (expr.equals(name)) { // 判断表达式和字段名是否相同：如果转换后的表达式和字段名完全相同（如都是"emp.name"），则直接使用字段名，不需要额外处理
        fields.add(name); // 直接添加字段名到普通字段列表：name是输出字段名，因为表达式和字段名相同，所以直接使用字段名
      } else if (expr.matches("\"literal\":.+")) { // 判断表达式是否是字面量：使用正则表达式匹配"literal:"开头的字符串，表示这是一个常量字面量（如数字、字符串等）
        scriptFields.add(ElasticsearchRules.quote(name)
                + ":{\"script\": "
                + expr.split(":")[1] + "}"); // 将字面量添加到脚本字段列表：quote(name)给字段名加引号，expr.split(":")[1]提取字面量值，格式为"字段名":{"script": 字面量值}
      } else {
        scriptFields.add(ElasticsearchRules.quote(name)
                + ":{\"script\":"
                // _source (ES2) vs params._source (ES5)
                + "\"" + implementor.elasticsearchTable.scriptedFieldPrefix() + "."
                + expr.replace("\"", "") + "\"}"); // 将复杂表达式添加到脚本字段列表：quote(name)给字段名加引号，scriptedFieldPrefix()获取脚本前缀（ES2是"_source"，ES5是"params._source"），expr.replace("\"", "")移除表达式中的引号，格式为"字段名":{"script": "前缀.表达式"}
      } // 结束if-else判断：完成了三种情况的处理（简单字段、相同字段名、字面量、复杂表达式）
    } // 结束for循环：完成了所有投影表达式的处理，fields列表包含普通字段，scriptFields列表包含脚本字段

    if (hasSelectStar) { // 判断是否存在SELECT *：如果查询中包含SELECT *，则不需要在Elasticsearch查询中指定_source字段，直接返回所有字段
      // means select * from elastic
      // this does not yet cover select *, _MAP['foo'], _MAP['bar'][0] from elastic
      return; // 直接返回：SELECT *情况下不需要添加_source或script_fields，Elasticsearch会自动返回所有字段，注意：暂不支持SELECT *和MAP字段混合的情况
    } // 结束if判断：完成了SELECT *的特殊处理

    final StringBuilder query = new StringBuilder(); // 创建查询字符串构建器：用于构建Elasticsearch查询中的_source或script_fields部分
    if (scriptFields.isEmpty()) { // 判断是否没有脚本字段：如果scriptFields列表为空，说明所有字段都是普通字段，可以使用_source字段直接返回
      List<String> newList = fields.stream()
          // _id field is available implicitly
          .filter(f -> !ElasticsearchConstants.ID.equals(f))
          .map(ElasticsearchRules::quote)
          .collect(Collectors.toList()); // 处理普通字段列表：使用Stream API，filter过滤掉_id字段（因为_id字段在Elasticsearch中隐式可用），map给每个字段名加引号，collect转换为List

      final String findString = String.join(", ", newList); // 将字段列表拼接为逗号分隔的字符串：如["field1", "field2"]会拼接为"field1, field2"
      query.append("\"_source\" : [").append(findString).append("]"); // 构建_source查询部分：格式为"_source" : [字段1, 字段2, ...]，指定Elasticsearch只返回这些字段
    } else { // 存在脚本字段的情况：如果scriptFields列表不为空，说明有需要计算的字段，Elasticsearch会忽略_source属性，必须使用script_fields
      // if scripted fields are present, ES ignores _source attribute
      for (String field : fields) { // 遍历普通字段列表：因为存在脚本字段时Elasticsearch忽略_source属性，所以需要将普通字段也转换为脚本字段
        scriptFields.add(ElasticsearchRules.quote(field) + ":{\"script\": "
                // _source (ES2) vs params._source (ES5)
                + "\"" + implementor.elasticsearchTable.scriptedFieldPrefix() + "."
                + field + "\"}"); // 将普通字段转换为脚本字段：quote(field)给字段名加引号，scriptedFieldPrefix()获取前缀，格式为"字段名":{"script": "前缀.字段名"}
      }
      query.append("\"script_fields\": {")
          .append(String.join(", ", scriptFields))
          .append("}"); // 构建script_fields查询部分：格式为"script_fields": {"字段1":{"script": "脚本1"}, "字段2":{"script": "脚本2"}, ...}，使用String.join将所有脚本字段拼接为逗号分隔的字符串
    } // 结束if-else判断：完成了_source或script_fields查询部分的构建

    implementor.list.removeIf(l -> l.startsWith("\"_source\"")); // 从实现器中移除已有的_source配置：removeIf移除所有以"_source"开头的字符串，避免重复配置（因为新的Project会覆盖之前的_source配置）
    implementor.add("{" + query + "}"); // 将构建的查询部分添加到实现器：query包含_source或script_fields配置，格式为{"_source": [...]}或{"script_fields": {...}}，这段JSON会被添加到最终的Elasticsearch查询中
  } // 结束implement方法：完成了Project关系表达式到Elasticsearch查询的转换
} // 结束ElasticsearchProject类定义
