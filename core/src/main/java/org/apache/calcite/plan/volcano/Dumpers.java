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
// 指定当前类所在的包路径，org.apache.calcite.plan.volcano是火山优化器的核心包
package org.apache.calcite.plan.volcano;

// 导入Spaces工具类，用于生成空格缩进，主要用于格式化输出
import org.apache.calcite.avatica.util.Spaces;
// 导入RelNode接口，代表关系代数表达式，是Calcite中所有关系操作符的基类
import org.apache.calcite.rel.RelNode;
// 导入RelVisitor抽象类，用于遍历关系表达式树
import org.apache.calcite.rel.RelVisitor;
// 导入RelMetadataQuery类，用于查询关系表达式的元数据信息（如行数、代价等）
import org.apache.calcite.rel.metadata.RelMetadataQuery;
// 导入PartiallyOrderedSet类，用于表示部分有序集合，用于管理子集之间的偏序关系
import org.apache.calcite.util.PartiallyOrderedSet;
// 导入Util工具类，提供各种实用方法
import org.apache.calcite.util.Util;

// 导入Google Guava的Ordering类，用于排序操作
import com.google.common.collect.Ordering;

// 导入API注解，用于标记API的版本和状态
import org.apiguardian.api.API;
// 导入Nullable注解，用于标记可空的参数或返回值
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入PrintWriter类，用于格式化输出到字符流
import java.io.PrintWriter;
// 导入StringWriter类，用于将输出收集到字符串缓冲区
import java.io.StringWriter;
// 导入ArrayList类，动态数组实现
import java.util.ArrayList;
// 导入Arrays类，提供数组操作的静态方法
import java.util.Arrays;
// 导入Comparator接口，用于定义比较规则
import java.util.Comparator;
// 导入HashSet类，基于哈希表的Set实现
import java.util.HashSet;
// 导入Iterator接口，用于迭代集合元素
import java.util.Iterator;
// 导入List接口，列表集合
import java.util.List;
// 导入Map接口，键值对映射
import java.util.Map;
// 导入Set接口，不包含重复元素的集合
import java.util.Set;

// 导入requireNonNull静态方法，用于非空检查
import static java.util.Objects.requireNonNull;

/**
 * Utility class to dump state of <code>VolcanoPlanner</code>.
 * 工具类，用于转储VolcanoPlanner（火山优化器）的状态信息
 * 
 * VolcanoPlanner是Calcite中的基于成本和规则的查询优化器，采用动态规划算法
 * 这个类提供了多种方法来导出优化器的内部状态，包括：
 * 1. 关系表达式的来源追溯（provenance）- 显示每个关系表达式是由哪个规则创建的
 * 2. 集合和子集的详细信息（dumpSets）- 显示所有RelSet和RelSubset的内容
 * 3. Graphviz格式的图形化输出（dumpGraphviz）- 生成可用于可视化的DOT格式图形
 * 
 * 这些功能对于理解优化器的工作原理、调试查询计划、分析性能问题非常有帮助
 */
// 标记此API从1.23版本开始存在，状态为INTERNAL（内部API）
@API(since = "1.23", status = API.Status.INTERNAL)
// Dumpers类：VolcanoPlanner状态转储工具类，提供多种方式导出优化器内部状态
class Dumpers {

  // 私有构造方法，防止实例化，因为这是一个纯工具类，所有方法都是静态的
  private Dumpers() {}

  /**
   * Returns a multi-line string describing the provenance of a tree of
   * relational expressions. For each node in the tree, prints the rule that
   * created the node, if any. Recursively describes the provenance of the
   * relational expressions that are the arguments to that rule.
   *
   * <p>Thus, every relational expression and rule invocation that affected
   * the final outcome is described in the provenance. This can be useful
   * when finding the root cause of "mistakes" in a query plan.
   *
   * @param provenanceMap The provenance map
   * @param root Root relational expression in a tree
   * @return Multi-line string describing the rules that created the tree
   */
  // 返回多行字符串，描述关系表达式树的来源（provenance）
  // 对于树中的每个节点，打印创建该节点的规则（如果存在）
  // 递归地描述作为该规则参数的关系表达式的来源
  // 因此，所有影响最终结果的关系表达式和规则调用都会在来源追溯中描述
  // 这对于查找查询计划中"错误"的根本原因非常有用
  // 参数：provenanceMap - 来源映射表，记录每个关系表达式的来源信息
  // 参数：root - 关系表达式树的根节点
  // 返回：描述创建该树的规则的多行字符串
  static String provenance(
      Map<RelNode, VolcanoPlanner.Provenance> provenanceMap, RelNode root) {
    // 创建StringWriter对象，用于将输出收集到字符串缓冲区
    final StringWriter sw = new StringWriter();
    // 创建PrintWriter对象，包装StringWriter，提供格式化输出功能
    final PrintWriter pw = new PrintWriter(sw);
    // 创建ArrayList集合，用于存储遍历到的所有关系表达式节点
    final List<RelNode> nodes = new ArrayList<>();
    // 创建匿名RelVisitor对象，用于遍历关系表达式树
    new RelVisitor() {
      // 重写visit方法，在访问每个节点时执行特定操作
      @Override public void visit(RelNode node, int ordinal, @Nullable RelNode parent) {
        // 将当前访问的关系表达式节点添加到nodes列表中
        nodes.add(node);
        // 调用父类的visit方法，继续遍历子节点
        super.visit(node, ordinal, parent);
      }
      // CHECKSTYLE: IGNORE 1 - 忽略checkstyle对匿名内部类的检查
    }.go(root); // 从根节点开始遍历关系表达式树
    // 创建HashSet集合，用于记录已访问过的关系表达式节点，避免重复处理
    final Set<RelNode> visited = new HashSet<>();
    // 遍历所有收集到的关系表达式节点
    for (RelNode node : nodes) {
      // 对每个节点调用provenanceRecurse方法，递归地生成来源信息，缩进级别为0
      provenanceRecurse(provenanceMap, pw, node, 0, visited);
    }
    // 刷新PrintWriter，确保所有输出都写入StringWriter
    pw.flush();
    // 返回StringWriter中收集的字符串内容
    return sw.toString();
  }

  // 私有静态方法：递归地生成关系表达式的来源信息
  // 参数：provenanceMap - 来源映射表
  // 参数：pw - PrintWriter对象，用于输出
  // 参数：node - 当前处理的关系表达式节点
  // 参数：i - 缩进级别，用于控制输出缩进
  // 参数：visited - 已访问节点的集合，用于避免循环引用导致的无限递归
  private static void provenanceRecurse(
      Map<RelNode, VolcanoPlanner.Provenance> provenanceMap,
      PrintWriter pw, RelNode node, int i, Set<RelNode> visited) {
    // 使用Spaces工具类在输出前添加缩进空格，缩进量为i*2个空格
    Spaces.append(pw, i * 2);
    // 尝试将当前节点添加到已访问集合中，如果添加失败说明节点已被访问过（存在循环引用）
    if (!visited.add(node)) {
      // 输出节点ID并提示"见上方"，避免重复输出
      pw.println("rel#" + node.getId() + " (see above)");
      // 直接返回，不再处理此节点
      return;
    }
    // 输出当前关系表达式节点的字符串表示
    pw.println(node);
    // 从来源映射表中获取当前节点的来源信息（Provenance对象）
    final VolcanoPlanner.Provenance o = provenanceMap.get(node);
    // 添加缩进，比当前级别多2个空格，用于显示来源详情
    Spaces.append(pw, i * 2 + 2);
    // 如果来源信息是EMPTY（空来源），说明该节点没有父节点
    if (o == VolcanoPlanner.Provenance.EMPTY) {
      // 输出"no parent"表示没有父节点
      pw.println("no parent");
    // 如果来源信息是DirectProvenance（直接来源），说明该节点是直接从某个关系表达式转换而来
    } else if (o instanceof VolcanoPlanner.DirectProvenance) {
      // 获取直接来源的关系表达式节点
      RelNode rel = ((VolcanoPlanner.DirectProvenance) o).source;
      // 输出"direct"表示直接来源
      pw.println("direct");
      // 递归处理来源节点，缩进级别增加2
      provenanceRecurse(provenanceMap, pw, rel, i + 2, visited);
    // 如果来源信息是RuleProvenance（规则来源），说明该节点是通过某个规则转换产生的
    } else if (o instanceof VolcanoPlanner.RuleProvenance) {
      // 将来源信息转换为RuleProvenance类型
      VolcanoPlanner.RuleProvenance rule = (VolcanoPlanner.RuleProvenance) o;
      // 输出规则调用ID和规则名称，格式为"call#xxx rule [规则名]"
      pw.println("call#" + rule.callId + " rule [" + rule.rule + "]");
      // 遍历规则的所有输入关系表达式
      for (RelNode rel : rule.rels) {
        // 递归处理每个输入关系表达式，缩进级别增加2
        provenanceRecurse(provenanceMap, pw, rel, i + 2, visited);
      }
    // 如果来源信息为null且节点是RelSubset类型（子集）
    } else if (o == null && node instanceof RelSubset) {
      // 某些操作数识别的是子集而不是单个关系表达式
      // 子集中的第一个关系表达式被认为是创建该子集的表达式
      final RelSubset subset = (RelSubset) node;
      // 输出子集信息
      pw.println("subset " + subset);
      // 递归处理子集中的第一个关系表达式，缩进级别增加2
      provenanceRecurse(provenanceMap, pw,
          subset.getRelList().get(0), i + 2, visited);
    // 其他情况：来源信息类型不合法
    } else {
      // 抛出断言错误，提示类型错误
      throw new AssertionError("bad type " + o);
    }
  }

  // 静态方法：将VolcanoPlanner中的所有集合（RelSet）和子集（RelSubset）的详细信息输出到PrintWriter
  // 参数：planner - VolcanoPlanner实例，包含要转储的信息
  // 参数：pw - PrintWriter对象，用于输出结果
  static void dumpSets(VolcanoPlanner planner, PrintWriter pw) {
    // 创建Ordering对象，按照RelSet的id属性进行排序
    Ordering<RelSet> ordering = Ordering.from(Comparator.comparingInt(o -> o.id));
    // 遍历所有RelSet，按照id排序后处理
    for (RelSet set : ordering.immutableSortedCopy(planner.allSets)) {
      // 输出Set的ID和行类型信息
      pw.println("Set#" + set.id
          + ", type: " + set.subsets.get(0).getRowType());
      // 初始化子集索引为-1
      int j = -1;
      // 遍历当前Set中的所有RelSubset（子集）
      for (RelSubset subset : set.subsets) {
        // 子集索引递增
        ++j;
        // 输出子集信息及其最佳关系表达式，格式为"子集描述, best=rel#xxx"或"子集描述, best=null"
        pw.println(
            "\t" + subset + ", best="
                + ((subset.best == null) ? "null"
                : ("rel#" + subset.best.getId())));
        // 断言：子集所属的Set必须是当前Set
        assert subset.set == set;
        // 遍历当前子集之前的所有子集
        for (int k = 0; k < j; k++) {
          // 断言：同一Set中的不同子集应该有不同的TraitSet（特征集合）
          assert !set.subsets.get(k).getTraitSet().equals(
              subset.getTraitSet());
        }
        // 遍历当前子集中的所有关系表达式
        for (RelNode rel : subset.getRels()) {
          // 输出关系表达式信息，格式为"\t\trel#xxx:关系表达式类型(...)"
          // 例如："\t\trel#34:JavaProject(rel#32:JavaFilter(...), ...)"
          pw.print("\t\t" + rel);
          // 遍历当前关系表达式的所有输入
          for (RelNode input : rel.getInputs()) {
            // 获取输入关系表达式对应的RelSubset
            RelSubset inputSubset =
                planner.getSubset(
                    input,
                    input.getTraitSet());
            // 如果找不到对应的子集
            if (inputSubset == null) {
              // 输出错误信息
              pw.append("no subset found for input ").print(input.getId());
              // 继续处理下一个输入
              continue;
            }
            // 获取输入子集所属的RelSet
            RelSet inputSet = inputSubset.set;
            // 如果输入本身就是一个RelSubset
            if (input instanceof RelSubset) {
              // 获取输入子集中的关系表达式迭代器
              final Iterator<RelNode> rels =
                  inputSubset.getRels().iterator();
              // 如果子集中有关系表达式
              if (rels.hasNext()) {
                // 获取第一个关系表达式
                input = rels.next();
                // 断言：输入的特征集合必须满足子集的特征集合
                assert input.getTraitSet().satisfies(inputSubset.getTraitSet());
                // 断言：输入关系表达式必须在输入Set的rels集合中
                assert inputSet.rels.contains(input);
                // 断言：输入子集必须在输入Set的subsets集合中
                assert inputSet.subsets.contains(inputSubset);
              }
            }
          }
          // 如果当前关系表达式已被剪枝（pruned）
          if (planner.prunedNodes.contains(rel)) {
            // 输出", pruned"标记
            pw.print(", pruned");
          }
          // 获取关系表达式所在Cluster的元数据查询对象
          RelMetadataQuery mq = rel.getCluster().getMetadataQuery();
          // 输出行数信息
          pw.print(", rowcount=" + mq.getRowCount(rel));
          // 输出累积代价信息
          pw.println(", cumulative cost=" + planner.getCost(rel, mq));
        }
      }
    }
  }

  // 静态方法：将VolcanoPlanner的状态以Graphviz DOT格式输出，可用于生成可视化图形
  // 参数：planner - VolcanoPlanner实例，包含要转储的信息
  // 参数：pw - PrintWriter对象，用于输出DOT格式的图形描述
  static void dumpGraphviz(VolcanoPlanner planner, PrintWriter pw) {
    // 创建Ordering对象，按照RelSet的id属性进行排序
    Ordering<RelSet> ordering = Ordering.from(Comparator.comparingInt(o -> o.id));
    // 创建HashSet集合，存储当前活跃的关系表达式（正在被规则处理的关系表达式）
    Set<RelNode> activeRels = new HashSet<>();
    // 遍历规则调用栈中的所有规则调用
    for (VolcanoRuleCall volcanoRuleCall : planner.ruleCallStack) {
      // 将规则调用涉及的所有关系表达式添加到活跃集合中
      activeRels.addAll(Arrays.asList(volcanoRuleCall.rels));
    }
    // 输出DOT格式的有向图开始标记
    pw.println("digraph G {");
    // 输出根节点定义，使用填充样式，标签为"Root"
    pw.println("\troot [style=filled,label=\"Root\"];");
    // 创建PartiallyOrderedSet对象，用于管理RelSubset之间的偏序关系
    // 偏序关系基于TraitSet的满足关系：e1的TraitSet满足e2的TraitSet
    PartiallyOrderedSet<RelSubset> subsetPoset =
        new PartiallyOrderedSet<>(
            (e1, e2) -> e1.getTraitSet().satisfies(e2.getTraitSet()));
    // 创建HashSet集合，存储非空的RelSubset（包含非AbstractConverter关系表达式的子集）
    Set<RelSubset> nonEmptySubsets = new HashSet<>();
    // 遍历所有RelSet，按照id排序后处理
    for (RelSet set : ordering.immutableSortedCopy(planner.allSets)) {
      // 输出子图开始标记，cluster后跟Set的id
      pw.print("\tsubgraph cluster");
      pw.print(set.id);
      pw.println("{");
      // 输出子图标签，包含Set的id和行类型信息
      pw.print("\t\tlabel=");
      Util.printJavaString(pw, "Set " + set.id + " "
          + set.subsets.get(0).getRowType(), false);
      pw.print(";\n");
      // 遍历当前Set中的所有关系表达式
      for (RelNode rel : set.rels) {
        // 输出关系表达式节点定义，节点名称为"rel"后跟关系表达式ID
        pw.print("\t\trel");
        pw.print(rel.getId());
        pw.print(" [label=");
        // 获取关系表达式所在Cluster的元数据查询对象
        RelMetadataQuery mq = rel.getCluster().getMetadataQuery();

        // 注意：关系表达式的TraitSet可能与其子集的TraitSet不同
        // 这可能由于RelTraitSet#simplify操作导致
        // 如果特征不同，我们希望在图形中保留它们
        RelSubset relSubset = planner.getSubset(rel);
        // 如果找不到对应的子集
        if (relSubset == null) {
          // 输出错误信息
          pw.append("no subset found for rel");
          // 继续处理下一个关系表达式
          continue;
        }
        // 获取子集TraitSet的字符串表示，前面加"."号
        String traits = "." + relSubset.getTraitSet().toString();
        // 从关系表达式的字符串表示中移除TraitSet部分
        String title = rel.toString().replace(traits, "");
        // 如果标题以右括号结尾
        if (title.endsWith(")")) {
          // 查找第一个左括号的位置
          int openParen = title.indexOf('(');
          // 如果找到了左括号
          if (openParen != -1) {
            // 标题格式如：rel#12:LogicalJoin(left=RelSubset#4,right=RelSubset#3,
            // condition==($2, $0),joinType=inner)
            // 所以我们移除括号，并将参数包装到第二行
            // 这样可以避免Graphviz框"太宽"，使图形更容易跟踪
            title = title.substring(0, openParen) + '\n'
                + title.substring(openParen + 1, title.length() - 1);
          }
        }
        // 输出节点标签，包含关系表达式标题、行数和代价信息
        Util.printJavaString(pw,
            title
                + "\nrows=" + mq.getRowCount(rel) + ", cost="
                + planner.getCost(rel, mq), false);
        // 如果关系表达式不是AbstractConverter类型
        if (!(rel instanceof AbstractConverter)) {
          // 将其子集添加到非空子集集合中
          nonEmptySubsets.add(relSubset);
        }
        // 如果该关系表达式是子集中的最佳表达式
        if (relSubset.best == rel) {
          // 设置节点颜色为蓝色
          pw.print(",color=blue");
        }
        // 如果该关系表达式当前是活跃的（正在被规则处理）
        if (activeRels.contains(rel)) {
          // 设置节点样式为虚线
          pw.print(",style=dashed");
        }
        // 设置节点形状为矩形
        pw.print(",shape=box");
        // 输出节点定义的结束标记
        pw.println("]");
      }

      // 清空子集偏序集合
      subsetPoset.clear();
      // 遍历当前Set中的所有RelSubset
      for (RelSubset subset : set.subsets) {
        // 将子集添加到偏序集合中
        subsetPoset.add(subset);
        // 输出子集节点定义，节点名称为"subset"后跟子集ID
        pw.print("\t\tsubset");
        pw.print(subset.getId());
        pw.print(" [label=");
        // 输出子集的字符串表示作为标签
        Util.printJavaString(pw, subset.toString(), false);
        // 判断子集是否为空（不在非空子集集合中）
        boolean empty = !nonEmptySubsets.contains(subset);
        // 如果标记为空
        if (empty) {
          // 我们不想在已知集合不为空时遍历关系表达式
          // 遍历子集中的所有关系表达式
          for (RelNode rel : subset.getRels()) {
            // 如果找到非AbstractConverter类型的关系表达式
            if (!(rel instanceof AbstractConverter)) {
              // 标记为非空
              empty = false;
              // 跳出循环
              break;
            }
          }
          // 如果确实为空（只包含AbstractConverter）
          if (empty) {
            // 设置节点颜色为红色
            pw.print(",color=red");
          }
        }
        // 如果该子集当前是活跃的
        if (activeRels.contains(subset)) {
          // 设置节点样式为虚线
          pw.print(",style=dashed");
        }
        // 输出节点定义的结束标记
        pw.print("]\n");
      }

      // 遍历偏序集合中的所有子集
      for (RelSubset subset : subsetPoset) {
        // 获取当前子集的所有子节点（在偏序关系中）
        List<RelSubset> children = subsetPoset.getChildren(subset);
        // 如果没有子节点
        if (children == null) {
          // 继续处理下一个子集
          continue;
        }
        // 遍历所有子节点
        for (RelSubset parent : children) {
          // 输出从子集到父集的边
          pw.print("\t\tsubset");
          pw.print(subset.getId());
          pw.print(" -> subset");
          pw.print(parent.getId());
          pw.print(";");
        }
      }

      // 输出子图结束标记
      pw.print("\t}\n");
    }
    // 注意：所有边的声明必须在节点声明之后
    // 否则Graphviz会隐式创建节点，并将它们放入错误的集群中
    // 输出从根节点到根子集的边
    pw.print("\troot -> subset");
    pw.print(requireNonNull(planner.root, "planner.root").getId());
    pw.println(";");
    // 再次遍历所有RelSet，用于输出关系表达式之间的边
    for (RelSet set : ordering.immutableSortedCopy(planner.allSets)) {
      // 遍历当前Set中的所有关系表达式
      for (RelNode rel : set.rels) {
        // 获取关系表达式对应的子集
        RelSubset relSubset = planner.getSubset(rel);
        // 如果找不到对应的子集
        if (relSubset == null) {
          // 输出错误信息
          pw.append("no subset found for rel ").print(rel.getId());
          // 继续处理下一个关系表达式
          continue;
        }
        // 输出从子集到关系表达式的边
        pw.print("\tsubset");
        pw.print(relSubset.getId());
        pw.print(" -> rel");
        pw.print(rel.getId());
        // 如果该关系表达式是子集中的最佳表达式
        if (relSubset.best == rel) {
          // 设置边的颜色为蓝色
          pw.print("[color=blue]");
        }
        // 输出边的结束标记
        pw.print(";");
        // 获取关系表达式的所有输入
        List<RelNode> inputs = rel.getInputs();
        // 遍历所有输入
        for (int i = 0; i < inputs.size(); i++) {
          // 获取当前输入
          RelNode input = inputs.get(i);
          // 输出从关系表达式到输入的边
          pw.print(" rel");
          pw.print(rel.getId());
          pw.print(" -> ");
          // 根据输入类型输出"subset"或"rel"
          pw.print(input instanceof RelSubset ? "subset" : "rel");
          pw.print(input.getId());
          // 如果关系表达式是最佳表达式或有多个输入
          if (relSubset.best == rel || inputs.size() > 1) {
            // 初始化分隔符为'['
            char sep = '[';
            // 如果关系表达式是最佳表达式
            if (relSubset.best == rel) {
              // 输出分隔符
              pw.print(sep);
              // 设置边的颜色为蓝色
              pw.print("color=blue");
              // 更新分隔符为','
              sep = ',';
            }
            // 如果有多个输入
            if (inputs.size() > 1) {
              // 输出分隔符
              pw.print(sep);
              // 输出标签属性的开始
              pw.print("label=\"");
              // 输出输入索引
              pw.print(i);
              // 输出标签属性的结束
              pw.print("\"");
              // sep = ','; // 注释掉的代码，未使用
            }
            // 输出属性列表的结束标记
            pw.print(']');
          }
          // 输出边的结束标记
          pw.print(";");
        }
        // 输出换行
        pw.println();
      }
    }

    // 绘制当前规则的连线
    for (VolcanoRuleCall ruleCall : planner.ruleCallStack) {
      // 输出规则节点定义，节点名称为"rule"后跟规则调用ID
      pw.print("rule");
      pw.print(ruleCall.id);
      pw.print(" [style=dashed,label=");
      // 输出规则的字符串表示作为标签
      Util.printJavaString(pw, ruleCall.rule.toString(), false);
      pw.print("]");

      // 获取规则调用涉及的所有关系表达式
      RelNode[] rels = ruleCall.rels;
      // 遍历所有关系表达式
      for (int i = 0; i < rels.length; i++) {
        // 获取当前关系表达式
        RelNode rel = rels[i];
        // 输出从规则到关系表达式的边
        pw.print(" rule");
        pw.print(ruleCall.id);
        pw.print(" -> ");
        // 根据关系表达式类型输出"subset"或"rel"
        pw.print(rel instanceof RelSubset ? "subset" : "rel");
        pw.print(rel.getId());
        pw.print(" [style=dashed");
        // 如果有多个关系表达式
        if (rels.length > 1) {
          // 输出标签属性的开始
          pw.print(",label=\"");
          // 输出关系表达式索引
          pw.print(i);
          // 输出标签属性的结束
          pw.print("\"");
        }
        // 输出属性列表的结束标记
        pw.print("]");
        // 输出边的结束标记
        pw.print(";");
      }
      // 输出换行
      pw.println();
    }

    // 输出DOT格式的有向图结束标记
    pw.print("}");
  }
}
