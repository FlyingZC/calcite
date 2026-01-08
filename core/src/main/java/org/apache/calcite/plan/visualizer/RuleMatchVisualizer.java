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
// 声明包名：org.apache.calcite.plan.visualizer，表示这个类属于Calcite查询优化器的可视化工具包
package org.apache.calcite.plan.visualizer;

// 导入RelOptCost类：表示关系表达式节点的成本信息，包括CPU、IO和行数等指标
import org.apache.calcite.plan.RelOptCost;
// 导入RelOptListener接口：查询优化器的监听器接口，用于监听优化过程中的各种事件
import org.apache.calcite.plan.RelOptListener;
// 导入RelOptPlanner接口：查询优化器的基类接口，定义了优化器的基本行为
import org.apache.calcite.plan.RelOptPlanner;
// 导入RelOptRuleCall类：表示优化规则调用的上下文信息，包含匹配的关系表达式和规则
import org.apache.calcite.plan.RelOptRuleCall;
// 导入HepRelVertex类：HepPlanner（启发式优化器）中使用的关系节点包装类
import org.apache.calcite.plan.hep.HepRelVertex;
// 导入RelSubset类：VolcanoPlanner（火山优化器）中使用的等价关系集合，包含多个等价的物理实现
import org.apache.calcite.plan.volcano.RelSubset;
// 导入RelNode接口：Calcite中关系表达式节点的基类接口，所有关系操作都继承自这个接口
import org.apache.calcite.rel.RelNode;
// 导入RelMetadataQuery类：用于查询关系节点元数据的工具类，如行数、成本等
import org.apache.calcite.rel.metadata.RelMetadataQuery;

// 导入IOUtils工具类：Apache Commons IO库中的工具类，用于处理输入输出流
import org.apache.commons.io.IOUtils;

// 导入JsonProcessingException类：Jackson库中处理JSON时的异常类
import com.fasterxml.jackson.core.JsonProcessingException;
// 导入DefaultPrettyPrinter类：Jackson库中用于格式化JSON输出的打印机
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
// 导入ObjectMapper类：Jackson库中用于JSON序列化和反序列化的核心类
import com.fasterxml.jackson.databind.ObjectMapper;

// 导入@Nullable注解：Checker Framework框架中的可空性注解，用于标记可能为null的值
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入IOException类：Java标准库中的IO异常类
import java.io.IOException;
// 导入InputStream类：Java标准库中的输入流类
import java.io.InputStream;
// 导入UncheckedIOException类：Java标准库中的非受检IO异常类
import java.io.UncheckedIOException;
// 导入Files类：Java NIO中用于文件操作的静态工具类
import java.nio.file.Files;
// 导入Path类：Java NIO中表示文件路径的接口
import java.nio.file.Path;
// 导入Paths类：Java NIO中用于创建Path对象的工具类
import java.nio.file.Paths;
// 导入StandardOpenOption类：Java NIO中定义文件打开选项的枚举类
import java.nio.file.StandardOpenOption;
// 导入DecimalFormat类：Java标准库中用于格式化数字的类
import java.text.DecimalFormat;
// 导入MessageFormat类：Java标准库中用于格式化消息的类
import java.text.MessageFormat;
// 导入ArrayList类：Java集合框架中的动态数组实现类
import java.util.ArrayList;
// 导入Arrays类：Java标准库中用于数组操作的静态工具类
import java.util.Arrays;
// 导入HashSet类：Java集合框架中的哈希集合实现类
import java.util.HashSet;
// 导入LinkedHashMap类：Java集合框架中保持插入顺序的哈希映射实现类
import java.util.LinkedHashMap;
// 导入List接口：Java集合框架中的列表接口
import java.util.List;
// 导入Locale类：Java标准库中表示地区和语言环境的类
import java.util.Locale;
// 导入Map接口：Java集合框架中的映射接口
import java.util.Map;
// 导入Set接口：Java集合框架中的集合接口
import java.util.Set;

// 导入toImmutableList静态方法：Guava库中将流转换为不可变列表的收集器
import static com.google.common.collect.ImmutableList.toImmutableList;

// 导入transform静态方法：Calcite工具类中对集合进行转换的静态方法
import static org.apache.calcite.util.Util.transform;

// 导入UTF_8常量：Java标准库中表示UTF-8字符集的常量
import static java.nio.charset.StandardCharsets.UTF_8;
// 导入requireNonNull静态方法：Java标准库中用于检查对象非空的方法
import static java.util.Objects.requireNonNull;

/**
 * 这是一个用于可视化RelOptPlanner（查询优化器）规则匹配过程的工具类。
 * 
 * 【核心功能说明】：
 * 1. 监听查询优化器的执行过程，捕获规则匹配、关系节点变化等事件
 * 2. 记录优化过程中的每个步骤，包括规则应用、节点创建、成本更新等
 * 3. 将优化过程生成为HTML和JavaScript文件，提供交互式可视化界面
 * 4. 支持VolcanoPlanner（火山优化器）和HepPlanner（启发式优化器）两种优化器
 * 5. 可以展示最终执行计划、中间成本变化、节点等价关系等信息
 *
 * 【使用示例】：
 * <blockquote><pre>{@code
 * // 创建可视化器实例，指定输出目录和文件名后缀
 * RuleMatchVisualizer viz =
 *     new RuleMatchVisualizer("/path/to/output/dir", "file-name-suffix");
 * // 将可视化器附加到优化器上，开始监听优化过程
 * viz.attachTo(planner)
 *
 * // 执行优化过程，可视化器会自动记录所有步骤
 * planner.findBestExpr();
 *
 * // 对于HepPlanner需要手动调用writeToFile()方法
 * // VolcanoPlanner会自动调用这个方法
 * viz.writeToFile();
 * }</pre></blockquote>
 */
// RuleMatchVisualizer类：规则匹配可视化器，实现了RelOptListener接口以监听优化器事件
public class RuleMatchVisualizer implements RelOptListener {

  // 常量INITIAL：表示初始状态的步骤ID，用于标识优化开始时的初始计划
  private static final String INITIAL = "INITIAL";
  // 常量FINAL：表示最终状态的步骤ID，用于标识优化完成时的最终计划
  private static final String FINAL = "FINAL";
  // 常量DEFAULT_SET：默认的等价集合ID，用于标记没有明确等价关系的节点
  public static final String DEFAULT_SET = "default";

  // 注释：默认HTML模板可以在以下路径编辑
  // core/src/main/resources/org/apache/calcite/plan/visualizer/viz-template.html
  // 常量TEMPLATE_DIRECTORY：HTML模板文件所在的资源目录路径
  private static final String TEMPLATE_DIRECTORY =
      "org/apache/calcite/plan/visualizer";

  // 成员变量outputDirectory：可视化输出文件的保存目录，如果为null则不保存到磁盘
  private final @Nullable String outputDirectory;
  // 成员变量outputSuffix：输出文件名的后缀，用于区分不同的可视化结果
  private final @Nullable String outputSuffix;

  // 成员变量latestRuleID：最近一次应用的优化规则的ID，用于跟踪规则应用顺序
  private String latestRuleID = "";
  // 成员变量latestRuleTransformCount：当前规则应用的转换次数计数器，处理同一规则多次调用的情况
  private int latestRuleTransformCount = 1;
  // 成员变量initialized：标志位，表示可视化器是否已经初始化（是否已注册初始计划）
  private boolean initialized = false;

  // 成员变量planner：当前附加的查询优化器引用，用于获取优化器状态和根节点
  private @Nullable RelOptPlanner planner = null;

  // 成员变量includeTransitiveEdges：是否包含传递边的标志，控制是否显示RelSubset之间的满足关系
  private boolean includeTransitiveEdges = false;
  // 成员变量includeIntermediateCosts：是否包含中间成本的标志，控制是否记录所有成本更新
  private boolean includeIntermediateCosts = false;

  // 成员变量steps：步骤信息列表，按时间顺序记录优化过程中的每个步骤
  private final List<StepInfo> steps = new ArrayList<>();
  // 成员变量allNodes：所有节点的映射表，键为节点ID，值为节点更新辅助对象，用于跟踪节点状态变化
  private final Map<String, NodeUpdateHelper> allNodes = new LinkedHashMap<>();

  /**
   * 构造函数：用于在优化阶段结束时将结果保存到磁盘。
   * 
   * 【参数说明】：
   * @param outputDirectory 输出文件的保存目录路径，不能为null
   * @param outputSuffix 输出文件名的后缀，用于区分不同的可视化结果，不能为null
   * 
   * 【使用场景】：
   * 当需要将优化过程的可视化结果保存为HTML和JavaScript文件时使用此构造函数。
   * 
   * 【注意事项】：
   * - 使用HepPlanner时，需要手动调用{@link #writeToFile()}方法
   * - 使用VolcanoPlanner时，会自动调用writeToFile()方法
   */
  public RuleMatchVisualizer(
      String outputDirectory,
      String outputSuffix) {
    // 使用requireNonNull方法检查outputDirectory参数是否为null，如果为null则抛出NullPointerException
    this.outputDirectory = requireNonNull(outputDirectory, "outputDirectory");
    // 使用requireNonNull方法检查outputSuffix参数是否为null，如果为null则抛出NullPointerException
    this.outputSuffix = requireNonNull(outputSuffix, "outputSuffix");
  }

  /**
   * 构造函数：用于不需要将结果写入磁盘的情况。
   * 
   * 【使用场景】：
   * 当只需要在内存中收集优化过程数据，而不需要生成可视化文件时使用此构造函数。
   * 
   * 【注意事项】：
   * - 此构造函数创建的可视化器不会调用writeToFile()方法
   * - 可以通过getJsonStringResult()方法获取JSON格式的结果数据
   */
  public RuleMatchVisualizer() {
    // 将outputDirectory设置为null，表示不保存到磁盘
    this.outputDirectory = null;
    // 将outputSuffix设置为null，表示不需要文件名后缀
    this.outputSuffix = null;
  }

  /**
   * 将可视化器附加到优化器上。
   * 
   * 【方法作用】：
   * - 将当前可视化器注册为优化器的监听器，开始监听优化过程
   * - 保存优化器的引用，用于后续获取优化器状态
   * 
   * 【调用时机】：
   * - 必须在应用优化规则之前调用
   * - 必须且只能调用一次
   * 
   * 【参数说明】：
   * @param planner 要附加的查询优化器实例
   * 
   * 【异常处理】：
   * - 如果已经附加过优化器，会触发断言失败
   */
  public void attachTo(RelOptPlanner planner) {
    // 断言检查：确保当前还没有附加过优化器，planner必须为null
    assert this.planner == null;
    // 将当前可视化器注册为优化器的监听器，开始接收优化事件
    planner.addListener(this);
    // 保存优化器的引用，用于后续获取优化器状态和根节点
    this.planner = planner;
  }

  /**
   * 设置是否包含传递边。
   * 
   * 【方法作用】：
   * - 控制可视化中是否显示从RelSubset到满足它的所有RelSubset中节点的边
   * - 传递边表示RelSubset之间的满足关系，用于展示等价集合之间的依赖
   * 
   * 【参数说明】：
   * @param includeTransitiveEdges 如果为true，则包含传递边；如果为false，则不包含
   * 
   * 【使用场景】：
   * - 当需要展示RelSubset之间的完整依赖关系时设置为true
   * - 当需要简化可视化结果时设置为false
   */
  public void setIncludeTransitiveEdges(final boolean includeTransitiveEdges) {
    // 设置includeTransitiveEdges标志，控制是否在可视化中包含传递边
    this.includeTransitiveEdges = includeTransitiveEdges;
  }

  /**
   * 设置是否包含中间成本。
   * 
   * 【方法作用】：
   * - 控制可视化中是否记录所有成本更新，包括中间状态的成本变化
   * - 如果启用，会记录每个步骤的成本信息；如果禁用，只记录最终成本
   * 
   * 【参数说明】：
   * @param includeIntermediateCosts 如果为true，则包含中间成本；如果为false，则只包含最终成本
   * 
   * 【使用场景】：
   * - 当需要分析成本变化过程时设置为true
   * - 当只需要关注最终成本时设置为false，可以减少数据量
   */
  public void setIncludeIntermediateCosts(final boolean includeIntermediateCosts) {
    // 设置includeIntermediateCosts标志，控制是否记录中间成本更新
    this.includeIntermediateCosts = includeIntermediateCosts;
  }

  /**
   * 监听器回调方法：当优化器尝试应用规则时调用。
   * 
   * 【方法作用】：
   * - 处理HepPlanner的兼容性问题
   * - 在第一次规则尝试时初始化可视化器，注册初始计划
   * 
   * 【调用时机】：
   * - 优化器每次尝试应用优化规则时调用
   * - 对于HepPlanner，这是初始化的触发点
   * 
   * 【参数说明】：
   * @param event 规则尝试事件，包含尝试应用的规则信息
   */
  @Override public void ruleAttempted(RuleAttemptedEvent event) {
    // HepPlanner兼容性处理：如果还没有初始化，则进行初始化
    if (!initialized) {
      // 检查planner是否为null，如果为null则抛出NullPointerException
      requireNonNull(planner, "planner");
      // 获取优化器的根节点，如果为null则抛出NullPointerException
      RelNode root = requireNonNull(planner.getRoot());
      // 设置初始化标志为true，表示已经初始化
      initialized = true;
      // 递归注册初始计划中的所有节点
      updateInitialPlan(root);
    }
  }

  /**
   * 注册初始计划。
   * 
   * 【方法作用】：
   * - 递归遍历初始计划的所有节点
   * - 将每个节点注册到allNodes映射表中
   * - 处理HepPlanner的HepRelVertex包装类
   * 
   * 【调用时机】：
   * - 在ruleAttempted方法中，第一次规则尝试时调用
   * - 这是HepPlanner的兼容性解决方案
   * 
   * 【参数说明】：
   * @param node 要注册的关系节点
   * 
   * 【实现细节】：
   * - 如果节点是HepRelVertex，先解包后再递归处理
   * - 否则直接注册节点，然后递归处理所有输入节点
   */
  private void updateInitialPlan(RelNode node) {
    // 如果节点是HepRelVertex类型（HepPlanner的包装类），需要先解包
    if (node instanceof HepRelVertex) {
      // 递归调用updateInitialPlan处理解包后的节点
      updateInitialPlan(node.stripped());
      // 返回，不继续处理当前包装节点
      return;
    }
    // 将当前节点注册到allNodes映射表中
    this.registerRelNode(node);
    // 递归处理当前节点的所有输入节点
    for (RelNode input : getInputs(node)) {
      // 对每个输入节点递归调用updateInitialPlan
      updateInitialPlan(input);
    }
  }

  /**
   * 获取节点的输入列表，解包HepRelVertex节点。
   * 
   * 【方法作用】：
   * - 获取关系节点的所有输入节点
   * - 如果输入节点是HepRelVertex，则解包为原始节点
   * 
   * 【调用时机】：
   * - 在updateInitialPlan方法中，递归遍历节点时调用
   * - 在updateFinalPlan方法中，标记最终计划时调用
   * - 在updateNodeInfo方法中，更新节点输入信息时调用
   * 
   * 【参数说明】：
   * @param node 要获取输入的关系节点
   * @return 输入节点列表，其中HepRelVertex已被解包
   * 
   * 【实现细节】：
   * - 使用Calcite的transform工具函数对输入列表进行转换
   * - 对每个输入节点，如果是HepRelVertex则调用stripped()解包，否则保持原样
   */
  private static List<RelNode> getInputs(final RelNode node) {
    // 使用transform工具函数转换输入节点列表，解包HepRelVertex节点
    return transform(node.getInputs(), n ->
        // 如果输入节点是HepRelVertex，则解包为原始节点；否则保持原样
        n instanceof HepRelVertex ? n.stripped() : n);
  }

  /**
   * 监听器回调方法：当优化器选择最终执行计划时调用。
   * 
   * 【方法作用】：
   * - 检测优化过程是否完成
   * - 标记最终计划中的所有节点
   * - 添加最终步骤并写入可视化文件
   * 
   * 【调用时机】：
   * - VolcanoPlanner在优化完成时调用
   * - 事件中的rel为null时表示优化完成
   * 
   * 【参数说明】：
   * @param event 关系选择事件，包含被选中的关系节点
   * 
   * 【实现细节】：
   * - 只有当event.getRel()为null时才执行最终处理
   * - 递归标记最终计划中的所有节点
   * - 添加FINAL步骤并调用writeToFile()生成可视化文件
   */
  @Override public void relChosen(RelChosenEvent event) {
    // 如果事件中的rel为null，表示优化过程完成
    if (event.getRel() == null) {
      // 检查planner是否为null，如果为null则抛出NullPointerException
      requireNonNull(planner, "planner");
      // 获取优化器的根节点，如果为null则抛出NullPointerException
      RelNode root = requireNonNull(planner.getRoot());
      // 递归标记最终计划中的所有节点
      updateFinalPlan(root);
      // 添加FINAL步骤，表示优化完成
      this.addStep(FINAL, null);
      // 将可视化结果写入文件
      this.writeToFile();
    }
  }

  /**
   * 标记最终计划中的节点。
   * 
   * 【方法作用】：
   * - 递归遍历最终计划的所有节点
   * - 为每个节点设置inFinalPlan属性为true
   * - 处理RelSubset的特殊情况，选择best节点
   * 
   * 【调用时机】：
   * - 在relChosen方法中，优化完成时调用
   * 
   * 【参数说明】：
   * @param node 要标记的关系节点
   * 
   * 【实现细节】：
   * - 如果已经添加过FINAL步骤，则直接返回，避免重复处理
   * - 如果节点是RelSubset，选择best节点继续递归
   * - 否则递归标记所有输入节点
   */
  private void updateFinalPlan(RelNode node) {
    // 获取当前步骤列表的大小
    int size = this.steps.size();
    // 如果已经有步骤且最后一个步骤是FINAL，则直接返回，避免重复处理
    if (size > 0 && FINAL.equals(this.steps.get(size - 1).getId())) {
      // 返回，不继续处理
      return;
    }

    // 注册当前节点并设置inFinalPlan属性为true，标记为最终计划的一部分
    this.registerRelNode(node).updateAttribute("inFinalPlan", Boolean.TRUE);
    // 如果节点是RelSubset类型
    if (node instanceof RelSubset) {
      // 获取RelSubset的best节点（最优物理实现）
      RelNode best = ((RelSubset) node).getBest();
      // 如果best节点为null，则返回
      if (best == null) {
        // 返回，不继续处理
        return;
      }
      // 递归标记best节点
      updateFinalPlan(best);
    } else {
      // 递归标记所有输入节点
      for (RelNode input : getInputs(node)) {
        // 对每个输入节点递归调用updateFinalPlan
        updateFinalPlan(input);
      }
    }
  }

  /**
   * 监听器回调方法：当规则成功产生新的关系节点时调用。
   * 
   * 【方法作用】：
   * - 处理规则应用前后的两种状态
   * - 在规则应用前添加INITIAL步骤
   * - 在规则应用后记录规则转换结果
   * 
   * 【调用时机】：
   * - 每次优化规则成功产生新的关系节点时调用
   * - 此方法会被调用两次：一次在规则匹配前（isBefore=true），一次在规则应用后（isBefore=false）
   * 
   * 【参数说明】：
   * @param event 规则产生事件，包含规则调用信息
   * 
   * 【实现细节】：
   * - isBefore=true时：如果是第一次规则调用，添加INITIAL步骤
   * - isBefore=false时：添加规则应用后的步骤，处理同一规则多次转换的情况
   */
  @Override public void ruleProductionSucceeded(RuleProductionEvent event) {
    // 此方法在规则匹配前调用一次，在规则应用后调用一次
    if (event.isBefore()) {
      // 如果是规则应用前的调用
      // 如果是第一次规则调用，添加初始状态
      if (latestRuleID.isEmpty()) {
        // 添加INITIAL步骤，表示优化开始时的初始状态
        this.addStep(INITIAL, null);
        // 将latestRuleID设置为INITIAL，表示已添加初始步骤
        this.latestRuleID = INITIAL;
      }
      // 返回，不继续处理
      return;
    }

    // 在规则应用后添加状态
    // 获取规则调用对象
    RelOptRuleCall ruleCall = event.getRuleCall();
    // 将规则ID转换为字符串
    String ruleID = Integer.toString(ruleCall.id);
    // 构造显示的规则名称，格式为"规则ID-规则名称"
    String displayRuleName = ruleCall.id + "-" + ruleCall.getRule();

    // 处理规则可能多次调用transform的情况，通过修改规则名称来区分
    if (ruleID.equals(this.latestRuleID)) {
      // 如果是同一个规则再次调用，增加转换计数
      latestRuleTransformCount++;
      // 在规则名称后添加转换计数，如"规则ID-规则名称-2"
      displayRuleName += "-" + latestRuleTransformCount;
    } else {
      // 如果是不同的规则，重置转换计数为1
      latestRuleTransformCount = 1;
    }
    // 更新latestRuleID为当前规则ID
    this.latestRuleID = ruleID;

    // 添加规则应用后的步骤
    this.addStep(displayRuleName, ruleCall);
  }

  /**
   * 监听器回调方法：当关系节点被丢弃时调用。
   * 
   * 【方法作用】：
   * - 当前实现为空，不处理节点丢弃事件
   * 
   * 【调用时机】：
   * - 优化器丢弃某个关系节点时调用
   * 
   * 【参数说明】：
   * @param event 关系丢弃事件，包含被丢弃的关系节点信息
   */
  @Override public void relDiscarded(RelDiscardedEvent event) {
    // 当前实现为空，不处理节点丢弃事件
  }

  /**
   * 监听器回调方法：当发现等价关系时调用。
   * 
   * 【方法作用】：
   * - 处理关系节点的等价类信息
   * - 注册等价集合（set）
   * - 将节点关联到对应的等价集合
   * 
   * 【调用时机】：
   * - 优化器发现新的等价关系时调用
   * - VolcanoPlanner中使用RelSubset来管理等价关系
   * 
   * 【参数说明】：
   * @param event 等价关系事件，包含关系节点和等价类信息
   * 
   * 【实现细节】：
   * - 如果等价类是字符串类型，解析出set ID
   * - 注册set节点
   * - 将关系节点关联到对应的set
   */
  @Override public void relEquivalenceFound(RelEquivalenceEvent event) {
    // 获取事件中的关系节点，如果为null则抛出NullPointerException
    final RelNode rel = requireNonNull(event.getRel());
    // 获取等价类对象
    Object eqClass = event.getEquivalenceClass();
    // 如果等价类是字符串类型
    if (eqClass instanceof String) {
      // 将等价类对象转换为字符串
      String eqClassStr = (String) eqClass;
      // 移除字符串中的"equivalence class "前缀
      eqClassStr = eqClassStr.replace("equivalence class ", "");
      // 构造set ID，格式为"set-等价类编号"
      String setId = "set-" + eqClassStr;
      // 注册set节点
      registerSet(setId);
      // 注册关系节点并设置其set属性
      registerRelNode(rel).updateAttribute("set", setId);
    }
    // 注册关系节点
    this.registerRelNode(rel);
  }

  /**
   * 添加一个等价集合（set）节点。
   * 
   * 【方法作用】：
   * - 在allNodes映射表中注册一个新的set节点
   * - 设置set节点的label和kind属性
   * 
   * 【调用时机】：
   * - 在relEquivalenceFound方法中，发现新的等价关系时调用
   * 
   * 【参数说明】：
   * @param setID 等价集合的ID
   * 
   * 【实现细节】：
   * - 使用computeIfAbsent方法，如果setID不存在则创建新的NodeUpdateHelper
   * - set节点的label：如果是DEFAULT_SET则为空字符串，否则为setID
   * - set节点的kind：固定为"set"
   */
  private void registerSet(final String setID) {
    // 使用computeIfAbsent方法，如果setID不存在则创建新的NodeUpdateHelper
    this.allNodes.computeIfAbsent(setID, k -> {
      // 创建新的NodeUpdateHelper对象，key为setID，rel为null（set不是关系节点）
      NodeUpdateHelper h = new NodeUpdateHelper(setID, null);
      // 设置label属性：如果是DEFAULT_SET则为空字符串，否则为setID
      h.updateAttribute("label", DEFAULT_SET.equals(setID) ? "" : setID);
      // 设置kind属性为"set"，表示这是一个等价集合节点
      h.updateAttribute("kind", "set");
      // 返回创建的NodeUpdateHelper对象
      return h;
    });
  }

  /**
   * 注册一个关系节点以跟踪其变化。
   * 
   * 【方法作用】：
   * - 在allNodes映射表中注册一个新的关系节点
   * - 设置节点的基本属性：label、explanation、set、kind
   * 
   * 【调用时机】：
   * - 在updateInitialPlan方法中，注册初始计划节点时调用
   * - 在relEquivalenceFound方法中，注册等价关系节点时调用
   * - 在updateFinalPlan方法中，标记最终计划节点时调用
   * - 在updateNodeInfo方法中，更新节点信息时调用
   * 
   * 【参数说明】：
   * @param rel 要注册的关系节点
   * @return NodeUpdateHelper对象，用于后续更新节点属性
   * 
   * 【实现细节】：
   * - 使用computeIfAbsent方法，如果节点不存在则创建新的NodeUpdateHelper
   * - label：节点的显示标签，包含节点ID和类型名称
   * - explanation：节点的解释信息，使用InputExcludedRelWriter生成
   * - set：默认为DEFAULT_SET，后续可能更新为具体的set ID
   * - kind：如果是RelSubset则为"subset"，否则默认为普通节点
   */
  private NodeUpdateHelper registerRelNode(final RelNode rel) {
    // 使用computeIfAbsent方法，如果节点不存在则创建新的NodeUpdateHelper
    return this.allNodes.computeIfAbsent(key(rel), k -> {
      // 创建新的NodeUpdateHelper对象，key为节点ID，rel为关系节点
      NodeUpdateHelper h = new NodeUpdateHelper(key(rel), rel);
      // 设置label属性：节点的显示标签
      h.updateAttribute("label", getNodeLabel(rel));
      // 设置explanation属性：节点的解释信息
      h.updateAttribute("explanation", getNodeExplanation(rel));
      // 设置set属性：默认为DEFAULT_SET
      h.updateAttribute("set", DEFAULT_SET);

      // 如果节点是RelSubset类型
      if (rel instanceof RelSubset) {
        // 设置kind属性为"subset"，表示这是一个等价子集节点
        h.updateAttribute("kind", "subset");
      }
      // 返回创建的NodeUpdateHelper对象
      return h;
    });
  }

  /**
   * 检查并存储关系节点的变化。
   * 
   * 【方法作用】：
   * - 更新节点的成本信息（如果启用中间成本或这是最后一步）
   * - 更新节点的输入列表
   * - 处理RelSubset的特殊情况：包含所有等价节点和满足的子集
   * 
   * 【调用时机】：
   * - 在addStep方法中，为每个节点更新信息时调用
   * 
   * 【参数说明】：
   * @param rel 要更新的关系节点
   * @param isLastStep 是否是最后一步（FINAL步骤）
   * 
   * 【实现细节】：
   * - 如果启用中间成本或是最后一步，则计算并更新成本信息
   * - 如果节点是RelSubset，收集所有等价节点和满足的子集
   * - 如果不包含传递边，则过滤掉传递节点
   * - 如果节点是普通节点，则收集所有输入节点
   */
  private void updateNodeInfo(final RelNode rel, final boolean isLastStep) {
    // 获取或创建节点的NodeUpdateHelper对象
    NodeUpdateHelper helper = registerRelNode(rel);
    // 如果启用中间成本或是最后一步，则更新成本信息
    if (this.includeIntermediateCosts || isLastStep) {
      // 获取优化器引用，如果为null则抛出NullPointerException
      final RelOptPlanner planner = requireNonNull(this.planner);
      // 获取关系节点的元数据查询对象
      RelMetadataQuery mq = rel.getCluster().getMetadataQuery();
      // 计算关系节点的成本
      RelOptCost cost = planner.getCost(rel, mq);
      // 获取关系节点的行数
      Double rowCount = mq.getRowCount(rel);
      // 更新节点的cost属性，格式化为易读的字符串
      helper.updateAttribute("cost", formatCost(rowCount, cost));
    }

    // 创建输入节点ID列表
    List<String> inputs = new ArrayList<>();
    // 如果节点是RelSubset类型
    if (rel instanceof RelSubset) {
      // 将节点转换为RelSubset
      RelSubset relSubset = (RelSubset) rel;
      // 收集RelSubset中所有等价节点的ID
      relSubset.getRels().forEach(input -> inputs.add(key(input)));
      // 创建传递节点集合，用于过滤
      Set<String> transitive = new HashSet<>();
      // 收集满足当前RelSubset的所有其他RelSubset
      relSubset.getSubsetsSatisfyingThis()
          // 过滤掉自身
          .filter(other -> !other.equals(relSubset))
          // 对每个满足的RelSubset
          .forEach(input -> {
            // 添加RelSubset的ID到输入列表
            inputs.add(key(input));
            // 如果不包含传递边
            if (!includeTransitiveEdges) {
              // 将RelSubset中的所有节点添加到传递集合
              input.getRels().forEach(r -> transitive.add(key(r)));
            }
          });
      // 从输入列表中移除传递节点
      inputs.removeAll(transitive);
    } else {
      // 如果节点是普通节点，收集所有输入节点的ID
      getInputs(rel).forEach(input -> inputs.add(key(input)));
    }

    // 更新节点的inputs属性
    helper.updateAttribute("inputs", inputs);
  }

  /**
   * 将自上一步以来的更新添加到steps列表中。
   * 
   * 【方法作用】：
   * - 收集所有节点的更新信息
   * - 更新节点的成本和输入信息
   * - 创建新的StepInfo对象并添加到steps列表
   * 
   * 【调用时机】：
   * - 在ruleProductionSucceeded方法中，规则应用后调用
   * - 在relChosen方法中，优化完成时调用
   * 
   * 【参数说明】：
   * @param stepID 步骤ID，如"INITIAL"、"FINAL"或规则名称
   * @param ruleCall 规则调用对象，可能为null
   * 
   * 【实现细节】：
   * - 检查是否使用DEFAULT_SET，如果是则注册DEFAULT_SET节点
   * - 遍历所有节点，更新节点信息并收集更新
   * - 获取规则匹配的关系节点列表
   * - 创建StepInfo对象并添加到steps列表
   */
  private void addStep(String stepID, @Nullable RelOptRuleCall ruleCall) {
    // 创建节点更新映射表，用于存储本步骤中的所有节点更新
    Map<String, Object> nextNodeUpdates = new LinkedHashMap<>();

    // HepPlanner兼容性处理：检查是否有节点使用DEFAULT_SET
    boolean usesDefaultSet = this.allNodes.values()
        // 将NodeUpdateHelper集合转换为流
        .stream()
        // 检查是否有节点的set属性为DEFAULT_SET
        .anyMatch(h -> DEFAULT_SET.equals(h.getValue("set")));
    // 如果有节点使用DEFAULT_SET
    if (usesDefaultSet) {
      // 注册DEFAULT_SET节点
      this.registerSet(DEFAULT_SET);
    }

    // 遍历所有节点
    for (NodeUpdateHelper h : allNodes.values()) {
      // 获取节点的关系节点对象
      RelNode rel = h.getRel();
      // 如果关系节点不为null
      if (rel != null) {
        // 更新节点信息，如果是FINAL步骤则强制更新成本
        updateNodeInfo(rel, FINAL.equals(stepID));
      }
      // 如果节点没有更新，则跳过
      if (h.isEmptyUpdate()) {
        // 继续下一个节点
        continue;
      }
      // 获取并重置节点的更新信息
      Object update = h.getAndResetUpdate();
      // 如果更新信息不为null
      if (update != null) {
        // 将更新信息添加到nextNodeUpdates映射表中
        nextNodeUpdates.put(h.getKey(), update);
      }
    }

    // 获取规则匹配的关系节点列表
    List<String> matchedRels =
        // 将规则调用的rels数组转换为流
        Arrays.stream(ruleCall == null ? new RelNode[0] : ruleCall.rels)
            // 将每个关系节点转换为其key（ID）
            .map(RuleMatchVisualizer::key)
            // 收集为不可变列表
            .collect(toImmutableList());
    // 创建新的StepInfo对象并添加到steps列表
    this.steps.add(new StepInfo(stepID, nextNodeUpdates, matchedRels));
  }

  /**
   * 获取JSON格式的结果字符串。
   * 
   * 【方法作用】：
   * - 将steps列表序列化为格式化的JSON字符串
   * - 使用Jackson库进行JSON序列化
   * 
   * 【返回值】：
   * @return JSON格式的结果字符串，包含所有步骤信息
   * 
   * 【使用场景】：
   * - 在writeToFile方法中，生成数据文件内容
   * - 用户可以直接调用此方法获取JSON数据
   * 
   * 【实现细节】：
   * - 创建LinkedHashMap存储数据，保持顺序
   * - 使用DefaultPrettyPrinter格式化JSON输出
   * - 移除对象条目中的空格，使JSON更紧凑
   */
  public String getJsonStringResult() {
    try {
      // 创建LinkedHashMap存储数据，保持插入顺序
      LinkedHashMap<String, Object> data = new LinkedHashMap<>();
      // 将steps列表添加到数据中
      data.put("steps", steps);
      // 创建Jackson的ObjectMapper对象
      ObjectMapper objectMapper = new ObjectMapper();
      // 创建默认的JSON格式化打印机
      DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
      // 配置打印机，移除对象条目中的空格
      printer = printer.withoutSpacesInObjectEntries();
      // 将数据序列化为格式化的JSON字符串并返回
      return objectMapper.writer(printer).writeValueAsString(data);
    } catch (JsonProcessingException e) {
      // 如果JSON处理失败，抛出运行时异常
      throw new RuntimeException(e);
    }
  }

  /**
   * 将规则匹配可视化的HTML和JavaScript文件写入磁盘。
   * 
   * 【方法作用】：
   * - 从资源目录加载HTML模板文件
   * - 生成包含可视化数据的JavaScript文件
   * - 修改HTML模板中的数据文件引用
   * - 将HTML和JavaScript文件写入指定目录
   * 
   * 【调用时机】：
   * - VolcanoPlanner在优化完成时自动调用
   * - HepPlanner需要手动调用
   * 
   * 【注意事项】：
   * - 同名文件会被替换
   * - 如果outputDirectory或outputSuffix为null，则不执行任何操作
   * - 如果输出目录不存在，会自动创建
   * 
   * 【实现细节】：
   * - 使用ClassLoader加载资源文件
   * - 使用IOUtils读取模板内容
   * - 使用字符串替换更新数据文件引用
   * - 使用Files.write写入文件，使用CREATE和TRUNCATE_EXISTING选项
   */
  public void writeToFile() {
    // 如果outputDirectory或outputSuffix为null，则不执行任何操作
    if (outputDirectory == null || outputSuffix == null) {
      // 返回，不执行文件写入
      return;
    }

    try {
      // 构造HTML模板文件的完整路径
      final String templatePath =
          Paths.get(TEMPLATE_DIRECTORY).resolve("viz-template.html").toString();
      // 获取类加载器，如果为null则抛出NullPointerException
      final ClassLoader cl = requireNonNull(getClass().getClassLoader());
      // 获取模板文件的输入流，如果为null则抛出NullPointerException
      final InputStream resourceAsStream =
          requireNonNull(cl.getResourceAsStream(templatePath));
      // 使用IOUtils读取模板内容，使用UTF-8编码
      String htmlTemplate = IOUtils.toString(resourceAsStream, UTF_8);

      // 构造HTML文件名：planner-viz + 后缀 + .html
      String htmlFileName = "planner-viz" + outputSuffix + ".html";
      // 构造数据文件名：planner-viz-data + 后缀 + .js
      String dataFileName = "planner-viz-data" + outputSuffix + ".js";

      // 定义要替换的字符串：模板中的数据文件引用
      String replaceString = "src=\"planner-viz-data.js\"";
      // 查找替换字符串在模板中的位置
      int replaceIndex = htmlTemplate.indexOf(replaceString);
      // 构造HTML内容：替换数据文件引用为实际文件名
      String htmlContent = htmlTemplate.substring(0, replaceIndex)
          + "src=\"" + dataFileName + "\""
          + htmlTemplate.substring(replaceIndex + replaceString.length());

      // 构造JavaScript数据文件内容：定义data变量并赋值为JSON字符串
      String dataJsContent = "var data = " + getJsonStringResult() + ";\n";

      // 构造输出目录路径
      Path outputDirPath = Paths.get(outputDirectory);
      // 构造HTML文件的完整输出路径
      Path htmlOutput = outputDirPath.resolve(htmlFileName);
      // 构造数据文件的完整输出路径
      Path dataOutput = outputDirPath.resolve(dataFileName);

      // 如果输出目录不存在，则创建目录
      if (!Files.exists(outputDirPath)) {
        // 创建输出目录及其所有不存在的父目录
        Files.createDirectories(outputDirPath);
      }

      // 写入HTML文件，使用CREATE和TRUNCATE_EXISTING选项（如果文件存在则截断）
      Files.write(htmlOutput, htmlContent.getBytes(UTF_8), StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
      // 写入数据文件，使用CREATE和TRUNCATE_EXISTING选项（如果文件存在则截断）
      Files.write(dataOutput, dataJsContent.getBytes(UTF_8), StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException e) {
      // 如果发生IO异常，包装为UncheckedIOException并抛出
      throw new UncheckedIOException(e);
    }
  }

  //--------------------------------------------------------------------------------
  // methods related to string representation
  // 以下是与字符串表示相关的方法
  //--------------------------------------------------------------------------------

  /**
   * 获取关系节点的key（ID）。
   * 
   * 【方法作用】：
   * - 将关系节点的ID转换为字符串
   * - 用作节点在allNodes映射表中的键
   * 
   * 【参数说明】：
   * @param rel 关系节点
   * @return 节点ID的字符串表示
   * 
   * 【实现细节】：
   * - 直接将节点的ID转换为字符串
   */
  private static String key(final RelNode rel) {
    // 将节点ID转换为字符串并返回
    return "" + rel.getId();
  }

  /**
   * 获取节点的显示标签。
   * 
   * 【方法作用】：
   * - 为节点生成易读的显示标签
   * - 对于RelSubset，显示subset ID、set ID和trait set
   * - 对于普通节点，显示节点ID和类型名称
   * 
   * 【参数说明】：
   * @param relNode 关系节点
   * @return 节点的显示标签
   * 
   * 【实现细节】：
   * - RelSubset标签格式：subset#ID-setSetID-TraitSet
   * - 普通节点标签格式：#ID-RelTypeName
   */
  private static String getNodeLabel(final RelNode relNode) {
    // 如果节点是RelSubset类型
    if (relNode instanceof RelSubset) {
      // 将节点转换为RelSubset
      final RelSubset relSubset = (RelSubset) relNode;
      // 获取RelSubset的set ID
      String setId = getSetId(relSubset);
      // 构造RelSubset的标签：subset#ID-setSetID-TraitSet
      return "subset#" + relSubset.getId() + "-set" + setId + "-\n"
          + relSubset.getTraitSet();
    }

    // 普通节点的标签：#ID-RelTypeName
    return "#" + relNode.getId() + "-" + relNode.getRelTypeName();
  }

  /**
   * 从RelSubset的解释信息中提取set ID。
   * 
   * 【方法作用】：
   * - 解析RelSubset的解释字符串
   * - 提取RelSubset后面的数字作为set ID
   * 
   * 【参数说明】：
   * @param relSubset RelSubset节点
   * @return set ID，如果解析失败则返回空字符串
   * 
   * 【实现细节】：
   * - 解释字符串格式通常为"RelSubset[id].xxx"
   * - 提取"RelSubset"和"."之间的数字
   */
  private static String getSetId(final RelSubset relSubset) {
    // 获取RelSubset的解释信息
    String explanation = getNodeExplanation(relSubset);
    // 查找"RelSubset"的位置，并计算起始位置
    int start = explanation.indexOf("RelSubset") + "RelSubset".length();
    // 如果起始位置无效，返回空字符串
    if (start < 0) {
      // 返回空字符串
      return "";
    }
    // 查找"."的位置
    int end = explanation.indexOf(".", start);
    // 如果结束位置无效，返回空字符串
    if (end < 0) {
      // 返回空字符串
      return "";
    }
    // 返回起始位置和结束位置之间的子字符串（set ID）
    return explanation.substring(start, end);
  }

  /**
   * 获取节点的解释信息。
   * 
   * 【方法作用】：
   * - 使用InputExcludedRelWriter生成节点的解释信息
   * - 不包含输入节点的详细信息，只显示节点本身
   * 
   * 【参数说明】：
   * @param relNode 关系节点
   * @return 节点的解释信息字符串
   * 
   * 【实现细节】：
   * - 创建InputExcludedRelWriter实例
   * - 调用节点的explain方法传入writer
   * - 返回writer的字符串表示
   */
  private static String getNodeExplanation(final RelNode relNode) {
    // 创建InputExcludedRelWriter实例，用于生成不包含输入的解释信息
    InputExcludedRelWriter relWriter = new InputExcludedRelWriter();
    // 调用节点的explain方法，传入relWriter
    relNode.explain(relWriter);
    // 返回writer的字符串表示
    return relWriter.toString();
  }

  /**
   * 格式化成本信息。
   * 
   * 【方法作用】：
   * - 将成本信息格式化为易读的字符串
   * - 包含行数、CPU成本和IO成本
   * - 使用科学计数法表示大数值
   * 
   * 【参数说明】：
   * @param rowCount 行数
   * @param cost 成本对象，可能为null
   * @return 格式化的成本字符串
   * 
   * 【实现细节】：
   * - 如果成本为null，返回"null"
   * - 如果成本包含特殊值（inf、huge、tiny），直接返回
   * - 否则使用MessageFormat格式化成本信息
   * - 格式：rowCount、rows、cpu、io，每项使用科学计数法
   */
  private static String formatCost(Double rowCount, @Nullable RelOptCost cost) {
    // 如果成本为null，返回"null"
    if (cost == null) {
      // 返回"null"
      return "null";
    }
    // 获取成本的字符串表示
    String originalStr = cost.toString();
    // 如果成本包含特殊值（inf、huge、tiny），直接返回
    if (originalStr.contains("inf") || originalStr.contains("huge")
        || originalStr.contains("tiny")) {
      // 返回原始字符串
      return originalStr;
    }
    // 使用MessageFormat格式化成本信息，包含行数、CPU和IO
    return new MessageFormat("\nrowCount: {0}\nrows: {1}\ncpu:  {2}\nio:   {3}",
        Locale.ROOT).format(new String[]{
            // 格式化行数
            formatCostScientific(rowCount),
            // 格式化成本中的行数
            formatCostScientific(cost.getRows()),
            // 格式化CPU成本
            formatCostScientific(cost.getCpu()),
            // 格式化IO成本
            formatCostScientific(cost.getIo())
        });
  }

  /**
   * 使用科学计数法格式化成本数值。
   * 
   * 【方法作用】：
   * - 将数值转换为科学计数法格式
   * - 先四舍五入为整数，再格式化
   * 
   * 【参数说明】：
   * @param costNumber 成本数值
   * @return 科学计数法格式的字符串
   * 
   * 【实现细节】：
   * - 使用Math.round四舍五入为整数
   * - 使用DecimalFormat格式化，模式为"#.#############################################E0"
   * - 支持最多50位小数精度
   */
  private static String formatCostScientific(double costNumber) {
    // 四舍五入为整数
    long costRounded = Math.round(costNumber);
    // 获取DecimalFormat实例，使用ROOT语言环境
    DecimalFormat formatter = (DecimalFormat) DecimalFormat.getInstance(Locale.ROOT);
    // 设置格式化模式：科学计数法，支持最多50位小数
    formatter.applyPattern("#.#############################################E0");
    // 格式化数值并返回
    return formatter.format(costRounded);
  }

}
