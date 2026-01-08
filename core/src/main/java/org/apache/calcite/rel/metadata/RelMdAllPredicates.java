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
package org.apache.calcite.rel.metadata; // 声明包名，该类位于org.apache.calcite.rel.metadata包下，属于Calcite元数据管理模块

import org.apache.calcite.plan.RelOptPredicateList; // 导入RelOptPredicateList类，用于表示优化器的谓词列表
import org.apache.calcite.plan.RelOptUtil; // 导入RelOptUtil工具类，提供关系表达式优化工具方法
import org.apache.calcite.plan.hep.HepRelVertex; // 导入HepRelVertex类，表示基于启发式优化器的顶点节点
import org.apache.calcite.plan.volcano.RelSubset; // 导入RelSubset类，表示火山优化器中的关系表达式子集
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系表达式树的基类
import org.apache.calcite.rel.core.Aggregate; // 导入Aggregate类，表示聚合操作节点
import org.apache.calcite.rel.core.Calc; // 导入Calc类，表示计算操作节点（包含投影和过滤）
import org.apache.calcite.rel.core.Exchange; // 导入Exchange类，表示数据交换节点（用于分布式处理）
import org.apache.calcite.rel.core.Filter; // 导入Filter类，表示过滤操作节点
import org.apache.calcite.rel.core.Join; // 导入Join类，表示连接操作节点
import org.apache.calcite.rel.core.Project; // 导入Project类，表示投影操作节点
import org.apache.calcite.rel.core.Sample; // 导入Sample类，表示采样操作节点
import org.apache.calcite.rel.core.SetOp; // 导入SetOp类，表示集合操作节点（UNION/INTERSECT等）
import org.apache.calcite.rel.core.Sort; // 导入Sort类，表示排序操作节点
import org.apache.calcite.rel.core.TableModify; // 导入TableModify类，表示表修改操作节点（INSERT/UPDATE/DELETE）
import org.apache.calcite.rel.core.TableScan; // 导入TableScan类，表示表扫描操作节点
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField类，表示关系数据类型字段
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder类，用于构建行表达式（RexNode）
import org.apache.calcite.rex.RexInputRef; // 导入RexInputRef类，表示行表达式的输入引用
import org.apache.calcite.rex.RexNode; // 导入RexNode接口，表示行表达式的基类
import org.apache.calcite.rex.RexProgram; // 导入RexProgram类，表示行表达式程序（包含表达式和条件）
import org.apache.calcite.rex.RexTableInputRef; // 导入RexTableInputRef类，表示表的输入引用
import org.apache.calcite.rex.RexTableInputRef.RelTableRef; // 导入RelTableRef类，表示表的引用
import org.apache.calcite.rex.RexUtil; // 导入RexUtil工具类，提供行表达式的工具方法
import org.apache.calcite.sql.validate.SqlValidatorUtil; // 导入SqlValidatorUtil工具类，提供SQL验证工具方法
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，表示不可变的位集合
import org.apache.calcite.util.Util; // 导入Util工具类，提供通用工具方法

import com.google.common.collect.HashMultimap; // 导入Guava的HashMultimap类，提供基于哈希的多重映射
import com.google.common.collect.ImmutableList; // 导入Guava的ImmutableList类，提供不可变列表
import com.google.common.collect.Multimap; // 导入Guava的Multimap接口，表示多重映射

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的值

import java.util.Collection; // 导入Collection接口，表示集合
import java.util.HashMap; // 导入HashMap类，提供哈希映射实现
import java.util.LinkedHashMap; // 导入LinkedHashMap类，提供保持插入顺序的映射
import java.util.LinkedHashSet; // 导入LinkedHashSet类，提供保持插入顺序的集合
import java.util.List; // 导入List接口，表示列表
import java.util.Map; // 导入Map接口，表示映射
import java.util.Set; // 导入Set接口，表示集合

/**
 * Utility to extract Predicates that are present in the (sub)plan
 * starting at this node.
 * 工具类，用于提取从该节点开始的（子）计划中存在的谓词
 *
 * <p>This should be used to infer whether same filters are applied on
 * a given plan by materialized view rewriting rules.
 * 应该用于推断物化视图重写规则是否在给定计划上应用了相同的过滤器
 *
 * <p>The output predicates might contain references to columns produced
 * by TableScan operators ({@link RexTableInputRef}). In turn, each TableScan
 * operator is identified uniquely by its qualified name and an identifier.
 * 输出的谓词可能包含对TableScan操作符产生的列的引用（RexTableInputRef）。
 * 每个TableScan操作符通过其限定名和标识符唯一标识
 *
 * <p>If the provider cannot infer the lineage for any of the expressions
 * contain in any of the predicates, it will return null. Observe that
 * this is different from the empty list of predicates, which means that
 * there are not predicates in the (sub)plan.
 * 如果提供者无法推断任何谓词中包含的表达式的谱系，它将返回null。
 * 注意，这与空的谓词列表不同，后者意味着（子）计划中没有谓词
 *
 */
public class RelMdAllPredicates // 定义RelMdAllPredicates类，用于计算关系节点的所有谓词元数据
    implements MetadataHandler<BuiltInMetadata.AllPredicates> { // 实现MetadataHandler接口，处理AllPredicates元数据
  public static final RelMetadataProvider SOURCE = // 定义静态常量SOURCE，作为元数据提供者
      ReflectiveRelMetadataProvider.reflectiveSource( // 使用反射创建元数据提供者
          new RelMdAllPredicates(), BuiltInMetadata.AllPredicates.Handler.class); // 创建RelMdAllPredicates实例并指定处理器类型

  @Override public MetadataDef<BuiltInMetadata.AllPredicates> getDef() { // 重写getDef方法，返回元数据定义
    return BuiltInMetadata.AllPredicates.DEF; // 返回AllPredicates的元数据定义
  }

  /** Catch-all implementation for
   * {@link BuiltInMetadata.AllPredicates#getAllPredicates()},
   * invoked using reflection.
   * getAllPredicates方法的通用实现，通过反射调用
   *
   * @see org.apache.calcite.rel.metadata.RelMetadataQuery#getAllPredicates(RelNode)
   * 参见RelMetadataQuery的getAllPredicates方法
   */
  public @Nullable RelOptPredicateList getAllPredicates(RelNode rel, RelMetadataQuery mq) { // 定义getAllPredicates方法，参数为关系节点和元数据查询
    return null; // 返回null，表示无法推断谓词
  }

  public @Nullable RelOptPredicateList getAllPredicates(HepRelVertex rel, RelMetadataQuery mq) { // 定义针对HepRelVertex的getAllPredicates方法
    return mq.getAllPredicates(rel.stripped()); // 返回剥离包装后的节点的谓词
  }

  public @Nullable RelOptPredicateList getAllPredicates(RelSubset rel, // 定义针对RelSubset的getAllPredicates方法
      RelMetadataQuery mq) {
    return mq.getAllPredicates(rel.stripped()); // 返回剥离包装后的节点的谓词
  }

  /**
   * Extracts predicates for a table scan.
   * 提取表扫描的谓词
   */
  public @Nullable RelOptPredicateList getAllPredicates(TableScan scan, RelMetadataQuery mq) { // 定义针对TableScan的getAllPredicates方法
    final BuiltInMetadata.AllPredicates.Handler handler = // 获取表的谓词处理器
        scan.getTable().unwrap(BuiltInMetadata.AllPredicates.Handler.class); // 尝试从表中获取AllPredicates处理器
    if (handler != null) { // 如果处理器存在
      return handler.getAllPredicates(scan, mq); // 调用处理器的getAllPredicates方法
    }
    return RelOptPredicateList.EMPTY; // 返回空的谓词列表
  }

  /**
   * Extracts predicates for a project.
   * 提取投影操作的谓词
   */
  public @Nullable RelOptPredicateList getAllPredicates(Project project, RelMetadataQuery mq) { // 定义针对Project的getAllPredicates方法
    return mq.getAllPredicates(project.getInput()); // 直接返回输入节点的谓词，投影不产生新的谓词
  }

  /**
   * Extracts predicates for a Filter.
   * 提取过滤操作的谓词
   */
  public @Nullable RelOptPredicateList getAllPredicates(Filter filter, RelMetadataQuery mq) { // 定义针对Filter的getAllPredicates方法
    return getAllFilterPredicates(filter.getInput(), mq, filter.getCondition()); // 调用getAllFilterPredicates方法，传入输入、元数据查询和过滤条件
  }

  /**
   * Extracts predicates for a Calc.
   * 提取计算操作的谓词
   */
  public @Nullable RelOptPredicateList getAllPredicates(Calc calc, RelMetadataQuery mq) { // 定义针对Calc的getAllPredicates方法
    final RexProgram rexProgram = calc.getProgram(); // 获取Calc的RexProgram（行表达式程序）
    if (rexProgram.getCondition() != null) { // 如果RexProgram中有条件
      final RexNode condition = rexProgram.expandLocalRef(rexProgram.getCondition()); // 扩展条件中的局部引用
      return getAllFilterPredicates(calc.getInput(), mq, condition); // 调用getAllFilterPredicates方法
    } else { // 如果没有条件
      return mq.getAllPredicates(calc.getInput()); // 直接返回输入节点的谓词
    }
  }

  /**
   * Add the Filter condition to the list obtained from the input.
   * The pred comes from the parent of rel.
   * 将过滤条件添加到从输入获得的列表中，谓词来自rel的父节点
   */
  private static @Nullable RelOptPredicateList getAllFilterPredicates(RelNode rel, // 定义getAllFilterPredicates私有静态方法
      RelMetadataQuery mq, RexNode pred) {
    final RexBuilder rexBuilder = rel.getCluster().getRexBuilder(); // 获取RexBuilder用于构建行表达式
    final RelOptPredicateList predsBelow = mq.getAllPredicates(rel); // 获取该节点下方的所有谓词
    if (predsBelow == null) { // 如果下方的谓词列表为null
      // Safety check
      return null; // 进行安全检查，返回null
    }

    // Extract input fields referenced by Filter condition
    // 提取过滤条件引用的输入字段
    final Set<RelDataTypeField> inputExtraFields = new LinkedHashSet<>(); // 创建集合存储额外输入字段
    final RelOptUtil.InputFinder inputFinder = new RelOptUtil.InputFinder(inputExtraFields); // 创建输入查找器
    pred.accept(inputFinder); // 让过滤条件接受输入查找器，找出使用的输入字段
    final ImmutableBitSet inputFieldsUsed = inputFinder.build(); // 构建使用的输入字段的位集合

    // Infer column origin expressions for given references
    // 推断给定引用的列来源表达式
    final Map<RexInputRef, Set<RexNode>> mapping = new LinkedHashMap<>(); // 创建映射关系
    for (int idx : inputFieldsUsed) { // 遍历使用的输入字段索引
      final RexInputRef ref = RexInputRef.of(idx, rel.getRowType().getFieldList()); // 创建输入引用
      final Set<RexNode> originalExprs = mq.getExpressionLineage(rel, ref); // 获取表达式的谱系
      if (originalExprs == null) { // 如果谱系为null
        // Bail out
        return null; // 退出并返回null
      }
      mapping.put(ref, originalExprs); // 将引用和原始表达式放入映射
    }

    // Replace with new expressions and return union of predicates
    // 用新表达式替换并返回谓词的并集
    final Set<RexNode> allExprs = // 创建所有表达式的集合
        RelMdExpressionLineage.createAllPossibleExpressions(rexBuilder, pred, mapping); // 创建所有可能的表达式
    if (allExprs == null) { // 如果表达式集合为null
      return null; // 返回null
    }
    return predsBelow.union(rexBuilder, RelOptPredicateList.of(rexBuilder, allExprs)); // 返回下方谓词和新谓词的并集
  }

  /**
   * Add the Join condition to the list obtained from the input.
   * 将连接条件添加到从输入获得的列表中
   */
  public @Nullable RelOptPredicateList getAllPredicates(Join join, RelMetadataQuery mq) { // 定义针对Join的getAllPredicates方法
    if (join.getJoinType().isOuterJoin()) { // 如果是外连接
      // We cannot map origin of this expression.
      return null; // 无法映射表达式的来源，返回null
    }

    final RexBuilder rexBuilder = join.getCluster().getRexBuilder(); // 获取RexBuilder
    final RexNode pred = join.getCondition(); // 获取连接条件

    final Multimap<List<String>, RelTableRef> qualifiedNamesToRefs = HashMultimap.create(); // 创建多重映射，存储限定名到表引用的映射
    RelOptPredicateList newPreds = RelOptPredicateList.EMPTY; // 初始化新的谓词列表为空
    for (RelNode input : join.getInputs()) { // 遍历连接的输入
      final RelOptPredicateList inputPreds = mq.getAllPredicates(input); // 获取输入的谓词
      if (inputPreds == null) { // 如果输入谓词为null
        // Bail out
        return null; // 退出并返回null
      }
      // Gather table references
      // 收集表引用
      final Set<RelTableRef> tableRefs = mq.getTableReferences(input); // 获取输入的表引用
      if (tableRefs == null) { // 如果表引用为null
        return null; // 返回null
      }
      if (input == join.getLeft()) { // 如果是左输入
        // Left input references remain unchanged
        // 左输入引用保持不变
        for (RelTableRef leftRef : tableRefs) { // 遍历左输入的表引用
          qualifiedNamesToRefs.put(leftRef.getQualifiedName(), leftRef); // 将限定名和表引用放入映射
        }
        newPreds = newPreds.union(rexBuilder, inputPreds); // 将输入谓词合并到新谓词列表
      } else { // 如果是右输入
        // Right input references might need to be updated if there are table name
        // clashes with left input
        // 如果与左输入存在表名冲突，右输入引用可能需要更新
        final Map<RelTableRef, RelTableRef> currentTablesMapping = new HashMap<>(); // 创建当前表的映射
        for (RelTableRef rightRef : tableRefs) { // 遍历右输入的表引用
          int shift = 0; // 初始化偏移量为0
          Collection<RelTableRef> lRefs = // 获取左输入中相同限定名的表引用
              qualifiedNamesToRefs.get(rightRef.getQualifiedName());
          if (lRefs != null) { // 如果存在冲突
            shift = lRefs.size(); // 设置偏移量为左输入引用的数量
          }
          currentTablesMapping.put(rightRef, // 将右表引用映射到新的表引用
              RelTableRef.of(rightRef.getTable(), shift + rightRef.getEntityNumber())); // 实体编号加上偏移量
        }
        final List<RexNode> updatedPreds = // 更新谓词列表
            Util.transform(inputPreds.pulledUpPredicates, // 转换上拉谓词
                e -> RexUtil.swapTableReferences(rexBuilder, e, // 交换表引用
                    currentTablesMapping));
        newPreds = // 合并更新后的谓词
            newPreds.union(rexBuilder,
                RelOptPredicateList.of(rexBuilder, updatedPreds));
      }
    }

    // Extract input fields referenced by Join condition
    // 提取连接条件引用的输入字段
    final Set<RelDataTypeField> inputExtraFields = new LinkedHashSet<>(); // 创建额外输入字段集合
    final RelOptUtil.InputFinder inputFinder = new RelOptUtil.InputFinder(inputExtraFields); // 创建输入查找器
    pred.accept(inputFinder); // 让连接条件接受输入查找器
    final ImmutableBitSet inputFieldsUsed = inputFinder.build(); // 构建使用的输入字段位集合

    // Infer column origin expressions for given references
    // 推断给定引用的列来源表达式
    final Map<RexInputRef, Set<RexNode>> mapping = new LinkedHashMap<>(); // 创建映射
    final RelDataType fullRowType = // 创建完整的行类型
        SqlValidatorUtil.createJoinType(rexBuilder.getTypeFactory(), // 使用类型工厂创建连接类型
            join.getLeft().getRowType(), // 左输入的行类型
            join.getRight().getRowType(), // 右输入的行类型
            null, // 无字段名
            ImmutableList.of()); // 无系统字段
    for (int idx : inputFieldsUsed) { // 遍历使用的输入字段索引
      final RexInputRef inputRef = RexInputRef.of(idx, fullRowType.getFieldList()); // 创建输入引用
      final Set<RexNode> originalExprs = mq.getExpressionLineage(join, inputRef); // 获取表达式的谱系
      if (originalExprs == null) { // 如果谱系为null
        // Bail out
        return null; // 退出并返回null
      }
      final RexInputRef ref = RexInputRef.of(idx, fullRowType.getFieldList()); // 再次创建输入引用
      mapping.put(ref, originalExprs); // 将引用和原始表达式放入映射
    }

    // Replace with new expressions and return union of predicates
    // 用新表达式替换并返回谓词的并集
    final Set<RexNode> allExprs = // 创建所有表达式集合
        RelMdExpressionLineage.createAllPossibleExpressions(rexBuilder, pred, mapping); // 创建所有可能的表达式
    if (allExprs == null) { // 如果表达式集合为null
      return null; // 返回null
    }
    return newPreds.union(rexBuilder, RelOptPredicateList.of(rexBuilder, allExprs)); // 返回新谓词和所有表达式的并集
  }

  /**
   * Extracts predicates for an Aggregate.
   * 提取聚合操作的谓词
   */
  public @Nullable RelOptPredicateList getAllPredicates(Aggregate agg, RelMetadataQuery mq) { // 定义针对Aggregate的getAllPredicates方法
    return mq.getAllPredicates(agg.getInput()); // 直接返回输入节点的谓词，聚合不产生新的谓词
  }

  /**
   * Extracts predicates for an TableModify.
   * 提取表修改操作的谓词
   */
  public @Nullable RelOptPredicateList getAllPredicates(TableModify tableModify, // 定义针对TableModify的getAllPredicates方法
      RelMetadataQuery mq) {
    return mq.getAllPredicates(tableModify.getInput()); // 直接返回输入节点的谓词
  }

  /**
   * Extracts predicates for a SetOp.
   * 提取集合操作的谓词
   */
  public @Nullable RelOptPredicateList getAllPredicates(SetOp setOp, RelMetadataQuery mq) { // 定义针对SetOp的getAllPredicates方法
    final RexBuilder rexBuilder = setOp.getCluster().getRexBuilder(); // 获取RexBuilder

    final Multimap<List<String>, RelTableRef> qualifiedNamesToRefs = HashMultimap.create(); // 创建多重映射
    RelOptPredicateList newPreds = RelOptPredicateList.EMPTY; // 初始化新谓词列表为空
    for (int i = 0; i < setOp.getInputs().size(); i++) { // 遍历集合操作的所有输入
      final RelNode input = setOp.getInput(i); // 获取第i个输入
      final RelOptPredicateList inputPreds = mq.getAllPredicates(input); // 获取输入的谓词
      if (inputPreds == null) { // 如果输入谓词为null
        // Bail out
        return null; // 退出并返回null
      }
      // Gather table references
      // 收集表引用
      final Set<RelTableRef> tableRefs = mq.getTableReferences(input); // 获取输入的表引用
      if (tableRefs == null) { // 如果表引用为null
        return null; // 返回null
      }
      if (i == 0) { // 如果是第一个输入
        // Left input references remain unchanged
        // 左输入引用保持不变
        for (RelTableRef leftRef : tableRefs) { // 遍历表引用
          qualifiedNamesToRefs.put(leftRef.getQualifiedName(), leftRef); // 将限定名和表引用放入映射
        }
        newPreds = newPreds.union(rexBuilder, inputPreds); // 合并输入谓词
      } else { // 如果不是第一个输入
        // Right input references might need to be updated if there are table name
        // clashes with left input
        // 如果与左输入存在表名冲突，右输入引用可能需要更新
        final Map<RelTableRef, RelTableRef> currentTablesMapping = new HashMap<>(); // 创建当前表的映射
        for (RelTableRef rightRef : tableRefs) { // 遍历表引用
          int shift = 0; // 初始化偏移量为0
          Collection<RelTableRef> lRefs = // 获取左输入中相同限定名的表引用
              qualifiedNamesToRefs.get(rightRef.getQualifiedName());
          if (lRefs != null) { // 如果存在冲突
            shift = lRefs.size(); // 设置偏移量
          }
          currentTablesMapping.put(rightRef, // 将右表引用映射到新的表引用
              RelTableRef.of(rightRef.getTable(),
                  shift + rightRef.getEntityNumber()));
        }
        // Add to existing qualified names
        // 添加到现有的限定名
        for (RelTableRef newRef : currentTablesMapping.values()) { // 遍历新的表引用
          qualifiedNamesToRefs.put(newRef.getQualifiedName(), newRef); // 将限定名和表引用放入映射
        }
        // Update preds
        // 更新谓词
        final List<RexNode> updatedPreds = // 更新谓词列表
            Util.transform(inputPreds.pulledUpPredicates, // 转换上拉谓词
                e -> RexUtil.swapTableReferences(rexBuilder, e, // 交换表引用
                    currentTablesMapping));
        newPreds = // 合并更新后的谓词
            newPreds.union(rexBuilder,
                RelOptPredicateList.of(rexBuilder, updatedPreds));
      }
    }
    return newPreds; // 返回新的谓词列表
  }

  /**
   * Extracts predicates for a Sample.
   * 提取采样操作的谓词
   */
  public @Nullable RelOptPredicateList getAllPredicates(Sample sample, // 定义针对Sample的getAllPredicates方法
      RelMetadataQuery mq) {
    return mq.getAllPredicates(sample.getInput()); // 直接返回输入节点的谓词
  }

  /**
   * Extracts predicates for a Sort.
   * 提取排序操作的谓词
   */
  public @Nullable RelOptPredicateList getAllPredicates(Sort sort, // 定义针对Sort的getAllPredicates方法
      RelMetadataQuery mq) {
    return mq.getAllPredicates(sort.getInput()); // 直接返回输入节点的谓词
  }

  /**
   * Extracts predicates for an Exchange.
   * 提取交换操作的谓词
   */
  public @Nullable RelOptPredicateList getAllPredicates(Exchange exchange, // 定义针对Exchange的getAllPredicates方法
      RelMetadataQuery mq) {
    return mq.getAllPredicates(exchange.getInput()); // 直接返回输入节点的谓词
  }

}
