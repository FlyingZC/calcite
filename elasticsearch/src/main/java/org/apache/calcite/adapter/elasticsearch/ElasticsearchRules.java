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
package org.apache.calcite.adapter.elasticsearch; // Elasticsearch适配器包，包含Elasticsearch相关的规则和转换逻辑

import org.apache.calcite.adapter.enumerable.RexImpTable; // 导入RexImpTable类，用于处理Rex表达式到Linq表达式的转换
import org.apache.calcite.adapter.enumerable.RexToLixTranslator; // 导入RexToLixTranslator类，用于将Rex节点转换为Linq表达式
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入JavaTypeFactory类，用于创建Java类型
import org.apache.calcite.plan.Convention; // 导入Convention类，定义关系代数的调用约定
import org.apache.calcite.plan.RelOptRule; // 导入RelOptRule类，定义优化规则的基类
import org.apache.calcite.plan.RelOptRuleCall; // 导入RelOptRuleCall类，表示优化规则的调用上下文
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系节点的特征集合
import org.apache.calcite.rel.InvalidRelException; // 导入InvalidRelException类，表示无效的关系表达式异常
import org.apache.calcite.rel.RelCollations; // 导入RelCollations类，提供排序和分组相关的工具方法
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系代数节点
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类，定义转换规则的基类
import org.apache.calcite.rel.core.Sort; // 导入Sort类，表示排序操作
import org.apache.calcite.rel.logical.LogicalAggregate; // 导入LogicalAggregate类，表示逻辑聚合操作
import org.apache.calcite.rel.logical.LogicalFilter; // 导入LogicalFilter类，表示逻辑过滤操作
import org.apache.calcite.rel.logical.LogicalProject; // 导入LogicalProject类，表示逻辑投影操作
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示函数调用表达式
import org.apache.calcite.rex.RexInputRef; // 导入RexInputRef类，表示输入字段引用
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类，表示字面量表达式
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式的基类
import org.apache.calcite.rex.RexVisitorImpl; // 导入RexVisitorImpl类，提供Rex节点的访问者模式实现
import org.apache.calcite.sql.SqlKind; // 导入SqlKind类，定义SQL操作的类型
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类，提供标准SQL操作符
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName类，定义SQL类型名称
import org.apache.calcite.sql.validate.SqlValidatorUtil; // 导入SqlValidatorUtil类，提供SQL验证工具方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，表示可能为null的值

import java.util.AbstractList; // 导入AbstractList类，提供列表的抽象实现
import java.util.List; // 导入List接口，表示有序集合

/**
 * Rules and relational operators for
 * {@link ElasticsearchRel#CONVENTION ELASTICSEARCH}
 * calling convention.
 */
// 这个类定义了Elasticsearch适配器的优化规则和关系运算符，用于将Calcite的逻辑操作转换为Elasticsearch特定的操作
// 主要包含：排序规则、过滤规则、投影规则和聚合规则
// 还包含将Rex表达式转换为Elasticsearch查询语言的转换器
class ElasticsearchRules { // Elasticsearch规则类，包含所有Elasticsearch相关的转换规则和工具方法
  static final RelOptRule[] RULES = { // 定义Elasticsearch适配器的优化规则数组
      ElasticsearchSortRule.INSTANCE, // 排序规则实例，用于将逻辑排序转换为Elasticsearch排序
      ElasticsearchFilterRule.INSTANCE, // 过滤规则实例，用于将逻辑过滤转换为Elasticsearch过滤
      ElasticsearchProjectRule.INSTANCE, // 投影规则实例，用于将逻辑投影转换为Elasticsearch投影
      ElasticsearchAggregateRule.INSTANCE // 聚合规则实例，用于将逻辑聚合转换为Elasticsearch聚合
  };

  private ElasticsearchRules() {} // 私有构造方法，防止实例化，这是一个工具类

  /**
   * Returns 'string' if it is a call to item['string'], null otherwise.
   *
   * @param call current relational expression
   * @return literal value
   */
  // 判断给定的RexCall是否是一个item访问调用（如item['fieldName']），如果是则返回字段名，否则返回null
  // 这个方法用于识别Map类型的字段访问，例如访问JSON对象中的某个属性
  private static @Nullable String isItemCall(RexCall call) { // 私有静态方法，检查是否为item访问调用
    if (call.getOperator() != SqlStdOperatorTable.ITEM) { // 检查操作符是否为ITEM操作符（用于访问Map或数组元素）
      return null; // 如果不是ITEM操作符，返回null
    }
    final RexNode op0 = call.getOperands().get(0); // 获取第一个操作数（通常是Map或数组对象）
    final RexNode op1 = call.getOperands().get(1); // 获取第二个操作数（通常是字段名或索引）

    if (op0 instanceof RexInputRef // 检查第一个操作数是否为输入字段引用
        && ((RexInputRef) op0).getIndex() == 0 // 且该输入字段的索引为0（表示是第一个输入字段，通常是_MAP字段）
        && op1 instanceof RexLiteral // 且第二个操作数是字面量
        && ((RexLiteral) op1).getValue2() instanceof String) { // 且该字面量的值为字符串类型
      return (String) ((RexLiteral) op1).getValue2(); // 返回字段名字符串
    }
    return null; // 不满足条件，返回null
  }

  /**
   * Checks if current node represents item access as in {@code _MAP['foo']} or
   * {@code cast(_MAP['foo'] as integer)}.
   *
   * @return whether expression is item
   */
  // 检查给定的RexNode是否表示item访问（如_MAP['foo']或cast(_MAP['foo'] as integer)）
  // 这个方法用于识别对Map类型字段的访问，包括带类型转换的访问
  static boolean isItem(RexNode node) { // 静态方法，判断表达式是否为item访问
    final Boolean result = node.accept(new RexVisitorImpl<Boolean>(false) { // 使用访问者模式遍历Rex节点
      @Override public Boolean visitCall(final RexCall call) { // 重写访问调用节点的方法
        return isItemCall(uncast(call)) != null; // 去除类型转换后检查是否为item访问，返回判断结果
      }
    });
    return Boolean.TRUE.equals(result); // 将结果转换为布尔值返回
  }

  /**
   * Unwraps cast expressions from current call. {@code cast(cast(expr))} becomes {@code expr}.
   */
  // 递归地去除表达式外层的类型转换（CAST）操作
  // 例如：cast(cast(expr)) 会简化为 expr
  // 这个方法用于在分析表达式时忽略类型转换层，专注于实际的表达式结构
  private static RexCall uncast(RexCall maybeCast) { // 私有静态方法，递归去除CAST类型转换
    if (maybeCast.getKind() == SqlKind.CAST && maybeCast.getOperands().get(0) instanceof RexCall) { // 如果当前是CAST操作且操作数是RexCall
      return uncast((RexCall) maybeCast.getOperands().get(0)); // 递归地去除内层的CAST
    }

    // not a cast
    return maybeCast; // 如果不是CAST操作，返回原始调用
  }

  static List<String> elasticsearchFieldNames(final RelDataType rowType) { // 静态方法，获取Elasticsearch字段名列表
    return SqlValidatorUtil.uniquify( // 使用SqlValidatorUtil工具确保字段名唯一
        new AbstractList<String>() { // 创建一个抽象列表，动态生成字段名
          @Override public String get(int index) { // 重写get方法，根据索引获取字段名
            return rowType.getFieldList().get(index).getName(); // 从行类型中获取指定索引的字段名
          }

          @Override public int size() { // 重写size方法，返回字段总数
            return rowType.getFieldCount(); // 获取行类型中的字段数量
          }
        },
        SqlValidatorUtil.EXPR_SUGGESTER, true); // 使用表达式建议器确保唯一性
  }

  static String quote(String s) { // 静态方法，给字符串添加双引号
    return "\"" + s + "\""; // 在字符串前后添加双引号，用于生成Elasticsearch查询中的字段引用
  }

  static String stripQuotes(String s) { // 静态方法，去除字符串两端的引号
    return s.length() > 1 && s.startsWith("\"") && s.endsWith("\"") // 检查字符串是否以双引号开头和结尾
        ? s.substring(1, s.length() - 1) : s; // 如果有引号则去除，否则返回原字符串
  }

  private static String escapeSpecialSymbols(String s) { // 私有静态方法，转义字符串中的特殊符号
    return s.replace("\\", "\\\\").replace("\"", "\\\""); // 转义反斜杠和双引号，防止在Elasticsearch查询中出错
  }

  /**
   * Translator from {@link RexNode} to strings in Elasticsearch's expression
   * language.
   */
  // 将Calcite的Rex表达式节点转换为Elasticsearch查询语言字符串的转换器
  // 这个类负责将各种Rex表达式（如字面量、输入引用、函数调用等）转换为Elasticsearch可理解的格式
  static class RexToElasticsearchTranslator extends RexVisitorImpl<String> { // 内部静态类，Rex到Elasticsearch的转换器
    private final JavaTypeFactory typeFactory; // Java类型工厂，用于处理类型相关的转换
    private final List<String> inFields; // 输入字段名列表，用于将输入引用转换为字段名

    RexToElasticsearchTranslator(JavaTypeFactory typeFactory, List<String> inFields) { // 构造方法
      super(true); // 调用父类构造方法，参数true表示深度遍历
      this.typeFactory = typeFactory; // 保存类型工厂引用
      this.inFields = inFields; // 保存输入字段列表
    }

    @Override public String visitLiteral(RexLiteral literal) { // 访问字面量节点
      if (literal.getValue() == null) { // 如果字面量值为null
        return "null"; // 返回null字符串
      }
      return "\"literal\":" // 返回字面量标签
          + quote( // 对转换后的字符串加引号
          escapeSpecialSymbols( // 转义特殊符号
              RexToLixTranslator.translateLiteral(literal, literal.getType(), // 将Rex字面量转换为Linq字面量
                  typeFactory, RexImpTable.NullAs.NOT_POSSIBLE).toString())); // 转换为字符串表示
    }

    @Override public String visitInputRef(RexInputRef inputRef) { // 访问输入引用节点
      return quote(inFields.get(inputRef.getIndex())); // 根据索引获取字段名并添加引号
    }

    @Override public String visitCall(RexCall call) { // 访问函数调用节点
      final String name = isItemCall(call); // 检查是否为item访问调用
      if (name != null) { // 如果是item访问
        return name; // 直接返回字段名
      }

      final List<String> strings = visitList(call.operands); // 递归访问所有操作数

      if (call.getKind() == SqlKind.CAST) { // 如果是类型转换操作
        return call.getOperands().get(0).accept(this); // 忽略类型转换，只转换内部表达式
      }

      if (call.getOperator() == SqlStdOperatorTable.ITEM) { // 如果是ITEM操作符
        final RexNode op1 = call.getOperands().get(1); // 获取第二个操作数（索引）
        if (op1 instanceof RexLiteral && op1.getType().getSqlTypeName() == SqlTypeName.INTEGER) { // 如果是整数字面量
          return stripQuotes(strings.get(0)) + "[" + ((RexLiteral) op1).getValue2() + "]"; // 生成数组访问语法，如fieldName[0]
        }
      }
      throw new IllegalArgumentException("Translation of " + call // 如果遇到不支持的操作，抛出异常
          + " is not supported by ElasticsearchProject"); // 提示该转换不受支持
    }
  }

  /**
   * Base class for planner rules that convert a relational expression to
   * Elasticsearch calling convention.
   */
  // Elasticsearch转换规则的基类，用于将关系表达式转换为Elasticsearch调用约定
  // 所有具体的转换规则（排序、过滤、投影、聚合）都继承自这个基类
  abstract static class ElasticsearchConverterRule extends ConverterRule { // 抽象静态类，Elasticsearch转换规则的基类
    protected ElasticsearchConverterRule(Config config) { // 受保护的构造方法
      super(config); // 调用父类ConverterRule的构造方法，传入配置对象
    }
  }

  /**
   * Rule to convert a {@link org.apache.calcite.rel.core.Sort} to an
   * {@link ElasticsearchSort}.
   */
  // 将Calcite的逻辑排序（Sort）转换为Elasticsearch排序（ElasticsearchSort）的规则
  // 这个规则负责将ORDER BY子句转换为Elasticsearch的sort查询参数
  private static class ElasticsearchSortRule extends ElasticsearchConverterRule { // 私有静态类，排序转换规则
    private static final ElasticsearchSortRule INSTANCE = Config.INSTANCE // 创建规则的单例实例
        .withConversion(Sort.class, Convention.NONE, // 配置转换：从Sort类，默认约定
            ElasticsearchRel.CONVENTION, "ElasticsearchSortRule") // 转换为Elasticsearch约定
        .withRuleFactory(ElasticsearchSortRule::new) // 设置规则工厂方法
        .toRule(ElasticsearchSortRule.class); // 生成规则对象

    protected ElasticsearchSortRule(Config config) { // 受保护的构造方法
      super(config); // 调用父类构造方法
    }

    @Override public RelNode convert(RelNode relNode) { // 重写转换方法
      final Sort sort = (Sort) relNode; // 将输入节点转换为Sort类型
      final RelTraitSet traitSet = sort.getTraitSet().replace(out).replace(sort.getCollation()); // 创建新的特征集，替换为输出约定和排序规则
      return new ElasticsearchSort(relNode.getCluster(), traitSet, // 创建ElasticsearchSort节点
        convert(sort.getInput(), traitSet.replace(RelCollations.EMPTY)), sort.getCollation(), // 转换输入节点，使用空的排序规则
        sort.offset, sort.fetch); // 保留偏移量和获取数量（用于分页）
    }
  }

  /**
   * Rule to convert a {@link org.apache.calcite.rel.logical.LogicalFilter} to an
   * {@link ElasticsearchFilter}.
   */
  // 将Calcite的逻辑过滤（LogicalFilter）转换为Elasticsearch过滤（ElasticsearchFilter）的规则
  // 这个规则负责将WHERE子句转换为Elasticsearch的bool查询
  private static class ElasticsearchFilterRule extends ElasticsearchConverterRule { // 私有静态类，过滤转换规则
    private static final ElasticsearchFilterRule INSTANCE = Config.INSTANCE // 创建规则的单例实例
        .withConversion(LogicalFilter.class, Convention.NONE, // 配置转换：从LogicalFilter类，默认约定
            ElasticsearchRel.CONVENTION, "ElasticsearchFilterRule") // 转换为Elasticsearch约定
        .withRuleFactory(ElasticsearchFilterRule::new) // 设置规则工厂方法
        .toRule(ElasticsearchFilterRule.class); // 生成规则对象

    protected ElasticsearchFilterRule(Config config) { // 受保护的构造方法
      super(config); // 调用父类构造方法
    }

    @Override public RelNode convert(RelNode relNode) { // 重写转换方法
      final LogicalFilter filter = (LogicalFilter) relNode; // 将输入节点转换为LogicalFilter类型
      final RelTraitSet traitSet = filter.getTraitSet().replace(out); // 创建新的特征集，替换为输出约定
      return new ElasticsearchFilter(relNode.getCluster(), traitSet, // 创建ElasticsearchFilter节点
        convert(filter.getInput(), out), // 转换输入节点
        filter.getCondition()); // 保留过滤条件
    }
  }

  /**
   * Rule to convert an {@link org.apache.calcite.rel.logical.LogicalAggregate}
   * to an {@link ElasticsearchAggregate}.
   */
  // 将Calcite的逻辑聚合（LogicalAggregate）转换为Elasticsearch聚合（ElasticsearchAggregate）的规则
  // 这个规则负责将GROUP BY和聚合函数转换为Elasticsearch的aggs查询
  private static class ElasticsearchAggregateRule extends ElasticsearchConverterRule { // 私有静态类，聚合转换规则
    private static final RelOptRule INSTANCE = Config.INSTANCE // 创建规则的单例实例
        .withConversion(LogicalAggregate.class, Convention.NONE, // 配置转换：从LogicalAggregate类，默认约定
            ElasticsearchRel.CONVENTION, "ElasticsearchAggregateRule") // 转换为Elasticsearch约定
        .withRuleFactory(ElasticsearchAggregateRule::new) // 设置规则工厂方法
        .toRule(ElasticsearchAggregateRule.class); // 生成规则对象

    protected ElasticsearchAggregateRule(Config config) { // 受保护的构造方法
      super(config); // 调用父类构造方法
    }

    @Override public @Nullable RelNode convert(RelNode rel) { // 重写转换方法，可能返回null
      final LogicalAggregate agg = (LogicalAggregate) rel; // 将输入节点转换为LogicalAggregate类型
      final RelTraitSet traitSet = agg.getTraitSet().replace(out); // 创建新的特征集，替换为输出约定
      try { // 尝试创建ElasticsearchAggregate节点
        return new ElasticsearchAggregate( // 创建ElasticsearchAggregate节点
            rel.getCluster(), // 传入集群信息
            traitSet, // 传入特征集
            convert(agg.getInput(), traitSet.simplify()), // 转换输入节点，简化特征集
            agg.getGroupSet(), // 传入分组字段集合
            agg.getGroupSets(), // 传入分组字段集合列表（用于GROUPING SETS）
            agg.getAggCallList()); // 传入聚合函数调用列表
      } catch (InvalidRelException e) { // 如果转换失败（例如Elasticsearch不支持某些聚合）
        return null; // 返回null，表示无法转换
      }
    }
  }


  /**
   * Rule to convert a {@link org.apache.calcite.rel.logical.LogicalProject}
   * to an {@link ElasticsearchProject}.
   */
  // 将Calcite的逻辑投影（LogicalProject）转换为Elasticsearch投影（ElasticsearchProject）的规则
  // 这个规则负责将SELECT子句中的表达式转换为Elasticsearch的_source字段选择
  private static class ElasticsearchProjectRule extends ElasticsearchConverterRule { // 私有静态类，投影转换规则
    private static final ElasticsearchProjectRule INSTANCE = Config.INSTANCE // 创建规则的单例实例
        .withConversion(LogicalProject.class, Convention.NONE, // 配置转换：从LogicalProject类，默认约定
            ElasticsearchRel.CONVENTION, "ElasticsearchProjectRule") // 转换为Elasticsearch约定
        .withRuleFactory(ElasticsearchProjectRule::new) // 设置规则工厂方法
        .toRule(ElasticsearchProjectRule.class); // 生成规则对象

    protected ElasticsearchProjectRule(Config config) { // 受保护的构造方法
      super(config); // 调用父类构造方法
    }

    @Override public boolean matches(RelOptRuleCall call) { // 重写匹配方法，判断规则是否适用
      final LogicalProject project = call.rel(0); // 获取第一个关系节点（LogicalProject）
      return project.getVariablesSet().isEmpty(); // 检查是否没有变量引用（Elasticsearch不支持某些复杂的投影）
    }

    @Override public RelNode convert(RelNode relNode) { // 重写转换方法
      final LogicalProject project = (LogicalProject) relNode; // 将输入节点转换为LogicalProject类型
      final RelTraitSet traitSet = project.getTraitSet().replace(out); // 创建新的特征集，替换为输出约定
      return new ElasticsearchProject(project.getCluster(), traitSet, // 创建ElasticsearchProject节点
        convert(project.getInput(), out), project.getProjects(), project.getRowType()); // 转换输入节点，保留投影表达式和行类型
    }
  }
} // 类结束
