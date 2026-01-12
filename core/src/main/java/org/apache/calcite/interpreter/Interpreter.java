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
package org.apache.calcite.interpreter; // 解释器包，包含Calcite的解释执行相关类

import org.apache.calcite.DataContext; // 数据上下文接口，提供执行SQL所需的运行时环境
import org.apache.calcite.DataContexts; // 数据上下文工具类，用于创建和管理数据上下文
import org.apache.calcite.adapter.java.JavaTypeFactory; // Java类型工厂，用于创建和转换Java类型
import org.apache.calcite.config.CalciteSystemProperty; // Calcite系统属性配置类
import org.apache.calcite.linq4j.AbstractEnumerable; // LINQ4J抽象可枚举类，提供可枚举数据源的基础实现
import org.apache.calcite.linq4j.Enumerable; // LINQ4J可枚举接口，表示可枚举的数据源
import org.apache.calcite.linq4j.Enumerator; // LINQ4J枚举器接口，用于遍历数据集合
import org.apache.calcite.linq4j.Linq4j; // LINQ4J工具类，提供LINQ操作的各种工具方法
import org.apache.calcite.linq4j.Ord; // 有序元素包装类，用于跟踪元素的索引位置
import org.apache.calcite.linq4j.TransformedEnumerator; // 转换枚举器类，用于将一种类型的枚举器转换为另一种类型
import org.apache.calcite.plan.RelOptCluster; // 关系表达式优化集群，包含优化器的共享状态
import org.apache.calcite.plan.hep.HepPlanner; // Hep启发式规划器，基于规则的关系表达式优化器
import org.apache.calcite.plan.hep.HepProgram; // Hep优化程序，定义优化规则的应用顺序
import org.apache.calcite.plan.hep.HepProgramBuilder; // Hep优化程序构建器，用于构建优化程序
import org.apache.calcite.rel.RelNode; // 关系表达式节点接口，表示关系代数运算
import org.apache.calcite.rel.RelVisitor; // 关系表达式访问者，用于遍历关系表达式树
import org.apache.calcite.rel.rules.CoreRules; // Calcite核心优化规则集合
import org.apache.calcite.rel.type.RelDataType; // 关系数据类型接口，表示关系型数据的类型信息
import org.apache.calcite.rel.type.RelDataTypeFactory; // 关系数据类型工厂接口，用于创建关系数据类型
import org.apache.calcite.rex.RexNode; // 行表达式节点接口，表示行级表达式
import org.apache.calcite.util.Pair; // 通用键值对类，用于存储两个关联的对象
import org.apache.calcite.util.ReflectUtil; // 反射工具类，提供反射操作的便捷方法
import org.apache.calcite.util.ReflectiveVisitDispatcher; // 反射访问分发器，通过反射调用访问方法
import org.apache.calcite.util.ReflectiveVisitor; // 反射访问者接口，标记支持反射访问的类
import org.apache.calcite.util.Util; // Calcite通用工具类

import com.google.common.collect.ImmutableList; // Google Guava不可变列表类
import com.google.common.collect.ImmutableMap; // Google Guava不可变映射类
import com.google.common.collect.Iterables; // Google Guava可迭代对象工具类
import com.google.common.collect.LinkedHashMultimap; // Google Guava链式哈希多重映射类
import com.google.common.collect.Lists; // Google Guava列表工具类
import com.google.common.collect.Multimap; // Google Guava多重映射接口，一个键可以对应多个值

import org.checkerframework.checker.initialization.qual.NotOnlyInitialized; // CheckerFramework初始化注解，表示字段可能未完全初始化
import org.checkerframework.checker.initialization.qual.UnknownInitialization; // CheckerFramework初始化注解，表示初始化状态未知
import org.checkerframework.checker.nullness.qual.Nullable; // CheckerFramework可空注解，标记可能为null的值

import java.util.ArrayDeque; // Java数组双端队列类，实现队列和栈操作
import java.util.ArrayList; // Java动态数组列表类
import java.util.Collection; // Java集合接口
import java.util.HashMap; // Java哈希映射类
import java.util.Iterator; // Java迭代器接口
import java.util.LinkedHashMap; // Java链式哈希映射类，保持插入顺序
import java.util.List; // Java列表接口
import java.util.Map; // Java映射接口
import java.util.NoSuchElementException; // Java无元素异常类

import static java.util.Objects.requireNonNull; // Java Objects工具类的静态导入，用于非空检查

/**
 * Interpreter（解释器）类
 *
 * <p>包含解释关系表达式的上下文环境。特别是在数据流图组装过程中，
 * 它保存了工作状态。这个类是Calcite解释执行模式的核心，负责将关系代数树
 * 转换为可执行的数据流图，并执行查询。
 *
 * <p>解释器模式是Calcite的三种执行模式之一（另外两种是Enumerable和Bindable），
 * 它通过解释执行关系表达式来处理数据，适合于调试和测试场景。
 * 解释器会遍历关系表达式树，为每个节点创建对应的可执行节点，
 * 然后通过数据流将这些节点连接起来，最终产生查询结果。
 */
public class Interpreter extends AbstractEnumerable<@Nullable Object[]> // 继承抽象可枚举类，提供可枚举的数据源，元素类型为可空的对象数组
    implements AutoCloseable { // 实现AutoCloseable接口，支持自动资源管理
  // 存储关系表达式节点到节点信息的映射，每个RelNode对应一个NodeInfo
  // 这个映射在整个解释执行过程中维护数据流图的所有节点
  private final Map<RelNode, NodeInfo> nodes; // 节点映射表，键为关系表达式节点，值为节点信息
  // 数据上下文，提供执行SQL所需的运行时环境，包括类型工厂、数据源等
  // 这个上下文在整个查询执行过程中保持不变，为所有节点共享
  private final DataContext dataContext; // 数据上下文对象，包含执行环境信息
  // 根关系表达式节点，表示整个查询树的根节点
  // 这个节点经过优化后，是解释执行的起点
  private final RelNode rootRel; // 根关系表达式节点，查询树的根

  /** Creates an Interpreter（创建解释器）构造方法 */
  public Interpreter(DataContext dataContext, RelNode rootRel) { // 构造函数，接收数据上下文和根关系表达式
    this.dataContext = requireNonNull(dataContext, "dataContext"); // 验证数据上下文非空并赋值
    final RelNode rel = optimize(rootRel); // 对根关系表达式进行优化，应用一系列优化规则
    // 创建核心编译器实现，用于将关系表达式树编译为可执行的节点
    // 编译器负责遍历关系表达式树并创建对应的执行节点
    final CompilerImpl compiler =
        new Nodes.CoreCompiler(this, rootRel.getCluster()); // 创建编译器实例，传入解释器和优化集群
    @SuppressWarnings("method.invocation.invalid") // 抑制编译器对方法调用的警告
    // 访问根关系表达式，编译整个关系表达式树
    // 返回一个Pair，左值是优化后的根节点，右值是节点信息映射
    Pair<RelNode, Map<RelNode, NodeInfo>> pair = compiler.visitRoot(rel); // 编译关系表达式树，返回根节点和节点映射
    this.rootRel = pair.left; // 保存优化后的根关系表达式
    this.nodes = ImmutableMap.copyOf(pair.right); // 创建节点映射的不可变副本，确保线程安全
  }

  /** 优化关系表达式的方法，应用一系列优化规则来改进查询计划 */
  private static RelNode optimize(RelNode rootRel) { // 静态优化方法，接收原始关系表达式
    // 创建Hep优化程序，添加多个优化规则
    // 这些规则用于简化关系表达式，提高执行效率
    final HepProgram hepProgram = new HepProgramBuilder() // 创建优化程序构建器
        .addRuleInstance(CoreRules.CALC_SPLIT) // 添加Calc拆分规则，将复杂的Calc节点拆分为Filter和Project
        .addRuleInstance(CoreRules.FILTER_SCAN) // 添加过滤器扫描规则，将过滤器下推到扫描节点
        .addRuleInstance(CoreRules.FILTER_INTERPRETER_SCAN) // 添加解释器过滤器扫描规则，优化解释器模式下的过滤器
        .addRuleInstance(CoreRules.PROJECT_TABLE_SCAN) // 添加投影表扫描规则，将投影下推到表扫描
        .addRuleInstance(CoreRules.PROJECT_INTERPRETER_TABLE_SCAN) // 添加解释器投影表扫描规则，优化解释器模式下的投影
        .addRuleInstance(CoreRules.AGGREGATE_REDUCE_FUNCTIONS) // 添加聚合函数简化规则，简化聚合表达式
        .build(); // 构建优化程序
    // 创建Hep启发式规划器，使用上述优化程序
    // HepPlanner是一个基于规则的优化器，按照固定顺序应用规则
    final HepPlanner planner = new HepPlanner(hepProgram); // 创建规划器实例
    planner.setRoot(rootRel); // 设置规划器的根关系表达式
    rootRel = planner.findBestExp(); // 执行优化，找到最优的表达式
    return rootRel; // 返回优化后的关系表达式
  }

  /** 创建并返回查询结果的枚举器，用于遍历查询结果集 */
  @Override public Enumerator<@Nullable Object[]> enumerator() { // 实现枚举器方法，返回结果集的枚举器
    start(); // 启动解释执行，开始运行所有节点
    // 获取根节点的节点信息
    // 根节点是整个数据流的终点，从这里获取最终结果
    final NodeInfo nodeInfo =
        requireNonNull(nodes.get(rootRel), () -> "nodeInfo for " + rootRel); // 获取根节点信息，非空检查
    final Enumerator<Row> rows; // 行枚举器变量，用于遍历数据行
    // 如果节点信息中包含可枚举的行数据源，直接使用该数据源的枚举器
    // 这种情况通常对应于表扫描等叶子节点
    if (nodeInfo.rowEnumerable != null) { // 检查是否有可枚举的行数据源
      rows = nodeInfo.rowEnumerable.enumerator(); // 创建行数据源的枚举器
    } else {
      // 否则从节点的sink队列中获取数据
      // 这种情况对应于中间节点，数据从上游节点流入sink队列
      final ArrayDeque<Row> queue =
          Iterables.getOnlyElement(nodeInfo.sinks.values()).list; // 获取唯一的sink队列
      rows = Linq4j.iterableEnumerator(queue); // 从队列创建枚举器
    }

    // 返回转换后的枚举器，将Row对象转换为对象数组
    // TransformedEnumerator将内部Row格式转换为外部使用Object[]格式
    return new TransformedEnumerator<Row, @Nullable Object[]>(rows) { // 创建转换枚举器
      @Override protected @Nullable Object[] transform(Row row) { // 转换方法，将Row转换为Object[]
        return row.getValues(); // 返回Row中的值数组
      }
    };
  }

  /** 启动解释执行，运行所有节点开始数据流处理 */
  @SuppressWarnings("CatchAndPrintStackTrace") // 抑制捕获异常并打印堆栈的警告
  private void start() { // 私有启动方法
    // 我们依赖节点按叶子节点优先的顺序排列
    // 这样可以确保在处理父节点之前，子节点已经准备好数据
    for (Map.Entry<RelNode, NodeInfo> entry : nodes.entrySet()) { // 遍历所有节点
      final NodeInfo nodeInfo = entry.getValue(); // 获取节点信息
      try { // 异常处理
        // 如果节点信息中没有可执行节点，抛出断言错误
        // 每个节点都必须有对应的可执行实现
        if (nodeInfo.node == null) { // 检查节点是否为空
          throw new AssertionError("node must not be null for nodeInfo, rel="
              + nodeInfo.rel); // 抛出断言错误
        }
        nodeInfo.node.run(); // 运行节点，开始数据处理
      } catch (InterruptedException e) { // 捕获中断异常
        e.printStackTrace(); // 打印异常堆栈
      }
    }
  }

  /** 关闭解释器，释放所有资源 */
  @Override public void close() { // 实现AutoCloseable的close方法
    nodes.values().forEach(NodeInfo::close); // 关闭所有节点，释放资源
  }

  /**
   * NodeInfo（节点信息）内部类
   *
   * 存储注册在数据流图中的节点的信息。每个关系表达式节点
   * 都对应一个NodeInfo对象，包含该节点的输入输出连接信息、
   * 可执行节点实例等。这个类是数据流图构建和执行的核心数据结构。
   */
  private static class NodeInfo { // 节点信息内部类
    // 对应的关系表达式节点，这个节点是数据流图的逻辑表示
    final RelNode rel; // 关系表达式节点
    // Sink映射表，键为边（Edge），值为ListSink
    // 每个边代表一个输出连接，ListSink是接收数据的队列
    // 一个节点可能有多个输出边（例如广播操作）
    final Map<Edge, ListSink> sinks = new LinkedHashMap<>(); // Sink映射表
    // 可枚举的行数据源，如果节点直接提供数据源（如表扫描），则此字段非空
    // 否则为null，数据通过sinks队列流入
    final @Nullable Enumerable<Row> rowEnumerable; // 可枚举的行数据源
    // 可执行节点实例，实际执行数据处理逻辑的对象
    // 这个对象在编译阶段创建，在start阶段运行
    @Nullable Node node; // 可执行节点

    /** NodeInfo构造方法，创建节点信息对象 */
    NodeInfo(RelNode rel, @Nullable Enumerable<Row> rowEnumerable) { // 构造函数
      this.rel = rel; // 保存关系表达式节点
      this.rowEnumerable = rowEnumerable; // 保存可枚举数据源
    }

    /** 关闭节点信息，释放资源 */
    void close() { // 关闭方法
      if (node != null) { // 如果节点存在
        final Node n = node; // 保存节点引用
        node = null; // 清空节点引用，防止重复关闭
        n.close(); // 关闭节点，释放资源
      }
    }
  }

  /**
   * EnumeratorSource（枚举器源）内部类
   *
   * 一个基于Enumerator的Source实现。Enumerator在完成或调用close()时被关闭。
   * 这个类将LINQ4J的枚举器包装为解释器数据流中的Source接口，
   * 使得外部数据源可以接入解释器的数据流图。
   */
  private static class EnumeratorSource implements Source { // 枚举器源类，实现Source接口
    // 内部维护的行枚举器，用于遍历数据行
    // 这个枚举器可能是表扫描的结果，也可能是其他数据源
    private final Enumerator<Row> enumerator; // 行枚举器

    /** EnumeratorSource构造方法，创建枚举器源 */
    EnumeratorSource(final Enumerator<Row> enumerator) { // 构造函数
      this.enumerator = requireNonNull(enumerator, "enumerator"); // 验证枚举器非空并赋值
    }

    /** 接收数据行，从枚举器中获取下一行数据 */
    @Override public @Nullable Row receive() { // 实现Source接口的receive方法
      if (enumerator.moveNext()) { // 移动到下一行，检查是否有数据
        return enumerator.current(); // 返回当前行
      }
      // 遍历完所有数据后关闭枚举器，释放资源
      enumerator.close(); // 关闭枚举器
      return null; // 返回null表示没有更多数据
    }

    /** 关闭枚举器源，释放资源 */
    @Override public void close() { // 实现Source接口的close方法
      enumerator.close(); // 关闭枚举器
    }
  }

  /**
   * ListSink（列表接收器）内部类
   *
   * 使用ArrayDeque实现的Sink接口。Sink是数据流的接收端，
   * 负责接收上游节点发送的数据行。ListSink使用队列作为缓冲区，
   * 支持多个生产者向同一个队列写入数据。
   */
  private static class ListSink implements Sink { // 列表接收器类，实现Sink接口
    // 数据行队列，使用双端队列作为缓冲区
    // 队列支持高效的插入和删除操作，适合流式数据处理
    final ArrayDeque<Row> list; // 行数据队列

    /** ListSink构造方法，创建列表接收器 */
    private ListSink(ArrayDeque<Row> list) { // 构造函数
      this.list = list; // 保存队列引用
    }

    /** 发送数据行，将行添加到队列中 */
    @Override public void send(Row row) { // 实现Sink接口的send方法
      list.add(row); // 将行添加到队列末尾
    }

    /** 结束数据发送，标记数据流结束 */
    @Override public void end() { // 实现Sink接口的end方法
      // 空实现，ListSink不需要特殊的结束处理
    }

    /** 设置源可枚举对象，从源复制数据到本地列表（已弃用） */
    @SuppressWarnings("deprecation") // 抑制弃用警告
    @Override public void setSourceEnumerable(Enumerable<Row> enumerable) // 实现Sink接口的setSourceEnumerable方法
        throws InterruptedException { // 可能抛出中断异常
      // 将源数据复制到本地列表
      // 这个方法用于批量加载数据，而不是流式处理
      final Enumerator<Row> enumerator = enumerable.enumerator(); // 创建枚举器
      while (enumerator.moveNext()) { // 遍历所有行
        this.send(enumerator.current()); // 将每行发送到队列
      }
      enumerator.close(); // 关闭枚举器
    }
  }

  /**
   * ListSource（列表源）内部类
   *
   * 使用ArrayDeque实现的Source接口。Source是数据流的发送端，
   * 负责向下游节点发送数据行。ListSource从队列中读取数据，
   * 支持多个消费者从同一个队列读取数据。
   */
  private static class ListSource implements Source { // 列表源类，实现Source接口
    // 数据行队列，数据从这个队列中读取
    // 这个队列通常与ListSink共享，形成生产者-消费者模式
    private final ArrayDeque<Row> list; // 行数据队列
    // 行迭代器，用于遍历队列中的数据
    // 迭代器在第一次调用receive时初始化
    private @Nullable Iterator<Row> iterator; // 行迭代器

    /** ListSource构造方法，创建列表源 */
    ListSource(ArrayDeque<Row> list) { // 构造函数
      this.list = list; // 保存队列引用
    }

    /** 接收数据行，从队列中获取下一行数据 */
    @Override public @Nullable Row receive() { // 实现Source接口的receive方法
      try { // 异常处理
        if (iterator == null) { // 第一次调用时初始化迭代器
          iterator = list.iterator(); // 创建队列的迭代器
        }
        return iterator.next(); // 返回下一行
      } catch (NoSuchElementException e) { // 捕获无元素异常
        iterator = null; // 清空迭代器
        return null; // 返回null表示没有更多数据
      }
    }

    /** 关闭列表源，释放资源 */
    @Override public void close() { // 实现Source接口的close方法
      // 空操作，ListSource不需要资源清理
    }
  }

  /**
   * DuplicatingSink（复制接收器）内部类
   *
   * 使用ArrayDeque实现的Sink接口，支持将同一份数据复制到多个队列。
   * 这个类用于实现广播操作，将数据同时发送给多个下游节点。
   * 每个队列对应一个下游节点的输入。
   */
  private static class DuplicatingSink implements Sink { // 复制接收器类，实现Sink接口
    // 队列列表，每个队列对应一个下游节点
    // 数据会被复制到所有队列中，实现广播效果
    private final List<ArrayDeque<Row>> queues; // 队列列表

    /** DuplicatingSink构造方法，创建复制接收器 */
    private DuplicatingSink(List<ArrayDeque<Row>> queues) { // 构造函数
      this.queues = ImmutableList.copyOf(queues); // 创建队列列表的不可变副本
    }

    /** 发送数据行，将行复制到所有队列 */
    @Override public void send(Row row) { // 实现Sink接口的send方法
      for (ArrayDeque<Row> queue : queues) { // 遍历所有队列
        queue.add(row); // 将行添加到每个队列
      }
    }

    /** 结束数据发送，标记数据流结束 */
    @Override public void end() { // 实现Sink接口的end方法
      // 空实现，DuplicatingSink不需要特殊的结束处理
    }

    /** 设置源可枚举对象，从源复制数据到所有队列（已弃用） */
    @SuppressWarnings("deprecation") // 抑制弃用警告
    @Override public void setSourceEnumerable(Enumerable<Row> enumerable) { // 实现Sink接口的setSourceEnumerable方法
      // 将源数据复制到所有本地列表
      // 这个方法用于批量加载数据，而不是流式处理
      final Enumerator<Row> enumerator = enumerable.enumerator(); // 创建枚举器
      while (enumerator.moveNext()) { // 遍历所有行
        this.send(enumerator.current()); // 将每行发送到所有队列
      }
      enumerator.close(); // 关闭枚举器
    }
  }

  /**
   * CompilerImpl（编译器实现）内部类
   *
   * 遍历关系表达式树（RelNode树），为每个节点创建可以在解释器中执行的Node对象。
   *
   * <p>编译器通过反射查找形式为"visit(XxxRel)"的方法。
   * "visit"方法必须创建适当的Node并将其放入{@link #node}字段中。
   *
   * <p>如果希望处理更多类型的关系表达式，可以在此类或子类中添加额外的"visit"方法，
   * 它们将通过反射被找到并调用。这种设计使得编译器具有良好的扩展性，
   * 可以通过添加新的visit方法来支持新的关系表达式类型。
   */
  static class CompilerImpl extends RelVisitor // 继承关系表达式访问者，用于遍历关系表达式树
      implements Compiler, ReflectiveVisitor { // 实现Compiler和ReflectiveVisitor接口
    // 标量编译器，用于将RexNode表达式编译为可执行的标量表达式
    // 标量表达式用于计算行级表达式的值，如过滤条件、投影表达式等
    final ScalarCompiler scalarCompiler; // 标量编译器
    // 反射访问分发器，通过反射调用visit和rewrite方法
    // 这个分发器根据RelNode的实际类型，动态调用对应的visit方法
    private final ReflectiveVisitDispatcher<CompilerImpl, RelNode> dispatcher =
        ReflectUtil.createDispatcher(CompilerImpl.class, RelNode.class); // 创建反射分发器
    // 解释器引用，标记字段可能未完全初始化
    @NotOnlyInitialized
    protected final Interpreter interpreter; // 解释器引用
    // 根关系表达式节点，可能为null
    protected @Nullable RelNode rootRel; // 根关系表达式
    // 当前处理的关系表达式节点，可能为null
    protected @Nullable RelNode rel; // 当前关系表达式
    // 当前创建的可执行节点，可能为null
    protected @Nullable Node node; // 当前可执行节点
    // 节点信息映射表，存储所有关系表达式节点对应的节点信息
    // 这个映射表在编译过程中构建，用于后续的数据流图执行
    final Map<RelNode, NodeInfo> nodes = new LinkedHashMap<>(); // 节点信息映射
    // 关系表达式输入映射表，存储每个节点的输入节点列表
    // 这个映射表用于跟踪节点之间的连接关系
    final Map<RelNode, List<RelNode>> relInputs = new HashMap<>(); // 输入节点映射
    // 输出边多重映射，存储每个节点的输出边
    // 一个节点可能有多个输出边（例如广播操作）
    final Multimap<RelNode, Edge> outEdges = LinkedHashMultimap.create(); // 输出边映射

    // 重写方法名称常量，用于反射调用
    private static final String REWRITE_METHOD_NAME = "rewrite"; // 重写方法名
    // 访问方法名称常量，用于反射调用
    private static final String VISIT_METHOD_NAME = "visit"; // 访问方法名

    /** CompilerImpl构造方法，创建编译器实现 */
    CompilerImpl(@UnknownInitialization Interpreter interpreter, RelOptCluster cluster) { // 构造函数
      this.interpreter = interpreter; // 保存解释器引用
      // 创建JaninoRex编译器，用于编译RexNode表达式为可执行的Java代码
      // Janino是一个轻量级的Java编译器，可以在运行时编译Java代码
      this.scalarCompiler = new JaninoRexCompiler(cluster.getRexBuilder()); // 创建标量编译器
    }

    /** 访问关系表达式树，从根节点p开始 */
    Pair<RelNode, Map<RelNode, NodeInfo>> visitRoot(RelNode p) { // 访问根节点方法
      rootRel = p; // 保存根节点
      visit(p, 0, null); // 递归访问整个关系表达式树
      // 返回根节点和节点信息映射的Pair
      return Pair.of(requireNonNull(rootRel, "rootRel"), nodes); // 返回编译结果
    }

    /** 访问关系表达式节点，递归处理整个树 */
    @Override public void visit(RelNode p, int ordinal, @Nullable RelNode parent) { // 访问节点方法
      for (;;) { // 无限循环，用于处理多次重写
        rel = null; // 清空当前关系表达式
        // 尝试调用rewrite方法，通过反射查找对应的方法
        // rewrite方法用于重写关系表达式，例如优化或转换
        boolean found = dispatcher.invokeVisitor(this, p, REWRITE_METHOD_NAME); // 调用重写方法
        if (!found) { // 如果没有找到重写方法
          throw new AssertionError( // 抛出断言错误
              "interpreter: no implementation for rewrite"); // 错误信息
        }
        if (rel == null) { // 如果没有重写
          break; // 退出循环
        }
        // 如果在调试模式下，打印重写信息
        if (CalciteSystemProperty.DEBUG.value()) { // 检查调试标志
          System.out.println("Interpreter: rewrite " + p + " to " + rel); // 打印重写信息
        }
        p = requireNonNull(rel, "rel"); // 更新当前节点为重写后的节点
        if (parent != null) { // 如果有父节点
          // 更新父节点的输入列表
          List<RelNode> inputs = relInputs.get(parent); // 获取父节点的输入列表
          if (inputs == null) { // 如果输入列表不存在
            inputs = Lists.newArrayList(parent.getInputs()); // 创建输入列表的副本
            relInputs.put(parent, inputs); // 保存输入列表
          }
          inputs.set(ordinal, p); // 更新指定位置的输入节点
        } else { // 如果没有父节点（根节点）
          rootRel = p; // 更新根节点
        }
      }

      // 先重写子节点（从左到右）
      // 这确保了在处理父节点之前，所有子节点都已经完成重写
      final List<RelNode> inputs = relInputs.get(p); // 获取当前节点的输入列表
      RelNode finalP = p; // 保存当前节点引用
      // 为每个输入节点创建输出边，记录节点之间的连接关系
      Ord.forEach(Util.first(inputs, p.getInputs()), // 遍历所有输入节点
          (r, i) -> outEdges.put(r, new Edge(finalP, i))); // 创建输出边
      if (inputs != null) { // 如果有输入列表
        for (int i = 0; i < inputs.size(); i++) { // 遍历所有输入节点
          RelNode input = inputs.get(i); // 获取输入节点
          visit(input, i, p); // 递归访问输入节点
        }
      } else { // 如果没有输入列表
        p.childrenAccept(this); // 使用默认方式访问子节点
      }

      // 清空当前节点
      node = null; // 清空节点
      // 尝试调用visit方法，通过反射查找对应的方法
      // visit方法用于创建可执行节点
      boolean found = dispatcher.invokeVisitor(this, p, VISIT_METHOD_NAME); // 调用访问方法
      if (!found) { // 如果没有找到visit方法
        if (p instanceof InterpretableRel) { // 如果节点实现了InterpretableRel接口
          InterpretableRel interpretableRel = (InterpretableRel) p; // 类型转换
          // 使用节点自身的实现方法创建可执行节点
          node =
              interpretableRel.implement( // 调用节点的实现方法
                  new InterpretableRel.InterpreterImplementor(this, null, // 创建实现器
                      DataContexts.EMPTY)); // 使用空数据上下文
        } else { // 如果节点不支持解释执行
          // 可能需要在CoreCompiler中添加visit(XxxRel)方法
          throw new AssertionError("interpreter: no implementation for " // 抛出断言错误
              + p.getClass()); // 错误信息
        }
      }
      // 获取节点信息并设置可执行节点
      final NodeInfo nodeInfo = requireNonNull(nodes.get(p)); // 获取节点信息
      nodeInfo.node = node; // 设置可执行节点
      if (inputs != null) { // 如果有输入列表
        for (int i = 0; i < inputs.size(); i++) { // 遍历所有输入节点
          final RelNode input = inputs.get(i); // 获取输入节点
          visit(input, i, p); // 递归访问输入节点
        }
      }
    }

    /**
     * 默认的rewrite方法（回退方法）
     *
     * <p>重写方法（每个方法都有不同的RelNode子类作为参数类型）
     * 如果打算重写，则设置{@link #rel}字段。
     * 子类可以重写这个方法来实现特定的重写逻辑。
     */
    public void rewrite(RelNode r) { // 默认重写方法
      // 空实现，默认不进行重写
    }

    /** 编译表达式列表为标量，用于计算表达式值 */
    @Override public Scalar compile(List<RexNode> nodes, @Nullable RelDataType inputRowType) { // 实现Compiler接口的compile方法
      if (inputRowType == null) { // 如果输入行类型为null
        inputRowType = getTypeFactory().builder() // 创建类型构建器
            .build(); // 构建空类型
      }
      // 编译表达式列表，返回标量生产者
      // 标量生产者可以在运行时计算表达式的值
      return scalarCompiler.compile(nodes, inputRowType) // 编译表达式
          .apply(interpreter.dataContext); // 应用数据上下文
    }

    /** 获取类型工厂，用于创建关系数据类型 */
    private JavaTypeFactory getTypeFactory() { // 获取类型工厂方法
      return interpreter.dataContext.getTypeFactory(); // 从数据上下文获取类型工厂
    }

    /** 组合多个输入节点的行类型，返回合并后的行类型 */
    @Override public RelDataType combinedRowType(List<RelNode> inputs) { // 实现Compiler接口的combinedRowType方法
      final RelDataTypeFactory.Builder builder = // 创建类型构建器
          getTypeFactory().builder(); // 获取类型工厂并创建构建器
      for (RelNode input : inputs) { // 遍历所有输入节点
        builder.addAll(input.getRowType().getFieldList()); // 添加所有字段
      }
      return builder.build(); // 构建合并后的行类型
    }

    /** 创建数据源，从指定关系表达式的指定输入位置获取数据 */
    @Override public Source source(RelNode rel, int ordinal) { // 实现Compiler接口的source方法
      final RelNode input = getInput(rel, ordinal); // 获取输入节点
      final Edge edge = new Edge(rel, ordinal); // 创建边对象
      final Collection<Edge> edges = outEdges.get(input); // 获取输入节点的所有输出边
      final NodeInfo nodeInfo = nodes.get(input); // 获取输入节点的节点信息
      if (nodeInfo == null) { // 如果节点信息不存在
        throw new AssertionError("should be registered: " + rel); // 抛出断言错误
      }
      // 如果节点有可枚举的行数据源，使用枚举器源
      if (nodeInfo.rowEnumerable != null) { // 检查是否有可枚举数据源
        return new EnumeratorSource(nodeInfo.rowEnumerable.enumerator()); // 创建枚举器源
      }
      // 否则使用列表源，从sink队列读取数据
      assert nodeInfo.sinks.size() == edges.size(); // 断言sink数量与边数量一致
      final ListSink sink = nodeInfo.sinks.get(edge); // 获取对应的sink
      if (sink != null) { // 如果sink存在
        return new ListSource(sink.list); // 创建列表源
      }
      // 如果找不到匹配的源类型，抛出异常
      throw new IllegalStateException( // 抛出非法状态异常
          "Got a sink " + sink + " to which there is no match source type!"); // 错误信息
    }

    /** 获取关系表达式的指定输入节点 */
    private RelNode getInput(RelNode rel, int ordinal) { // 获取输入节点方法
      final List<RelNode> inputs = relInputs.get(rel); // 获取输入列表
      if (inputs != null) { // 如果输入列表存在
        return inputs.get(ordinal); // 返回指定位置的输入节点
      }
      return rel.getInput(ordinal); // 否则使用默认方法获取输入节点
    }

    /** 创建数据接收器，用于接收当前节点产生的数据 */
    @Override public Sink sink(RelNode rel) { // 实现Compiler接口的sink方法
      final Collection<Edge> edges = outEdges.get(rel); // 获取当前节点的所有输出边
      // 如果没有输出边，创建一个虚拟边；否则使用实际的边
      final Collection<Edge> edges2 = edges.isEmpty() // 检查是否为空
          ? ImmutableList.of(new Edge(null, 0)) // 创建虚拟边
          : edges; // 使用实际边
      NodeInfo nodeInfo = nodes.get(rel); // 获取节点信息
      if (nodeInfo == null) { // 如果节点信息不存在
        nodeInfo = new NodeInfo(rel, null); // 创建新的节点信息
        nodes.put(rel, nodeInfo); // 保存节点信息
        for (Edge edge : edges2) { // 遍历所有边
          nodeInfo.sinks.put(edge, new ListSink(new ArrayDeque<>())); // 创建sink
        }
      } else { // 如果节点信息已存在
        for (Edge edge : edges2) { // 遍历所有边
          if (nodeInfo.sinks.containsKey(edge)) { // 如果sink已存在
            continue; // 跳过
          }
          nodeInfo.sinks.put(edge, new ListSink(new ArrayDeque<>())); // 创建新的sink
        }
      }
      // 如果只有一个输出边，返回单个sink
      if (edges.size() == 1) { // 检查边数量
        return Iterables.getOnlyElement(nodeInfo.sinks.values()); // 返回唯一的sink
      } else { // 如果有多个输出边
        final List<ArrayDeque<Row>> queues = new ArrayList<>(); // 创建队列列表
        for (ListSink sink : nodeInfo.sinks.values()) { // 遍历所有sink
          queues.add(sink.list); // 添加队列
        }
        return new DuplicatingSink(queues); // 返回复制接收器
      }
    }

    /** 设置可枚举数据源，用于叶子节点直接提供数据 */
    @Override public void enumerable(RelNode rel, Enumerable<Row> rowEnumerable) { // 实现Compiler接口的enumerable方法
      NodeInfo nodeInfo = new NodeInfo(rel, rowEnumerable); // 创建节点信息
      nodes.put(rel, nodeInfo); // 保存节点信息
    }

    /** 创建执行上下文，用于节点执行时访问运行时环境 */
    @Override public Context createContext() { // 实现Compiler接口的createContext方法
      return new Context(getDataContext()); // 创建上下文对象
    }

    /** 获取数据上下文 */
    @Override public DataContext getDataContext() { // 实现Compiler接口的getDataContext方法
      return interpreter.dataContext; // 返回解释器的数据上下文
    }
  }

  /**
   * Edge（边）内部类
   *
   * 表示RelNode与其输入之一之间的边。边是数据流图中的基本连接单元，
   * 记录了数据从子节点流向父节点的路径。每个边包含父节点引用和输入序号。
   */
  static class Edge extends Pair<@Nullable RelNode, Integer> { // 边类，继承Pair
    /** Edge构造方法，创建边对象 */
    Edge(@Nullable RelNode parent, int ordinal) { // 构造函数
      super(parent, ordinal); // 调用父类构造函数
    }
  }

  /**
   * ScalarCompiler（标量编译器）接口
   *
   * 将表达式列表转换为可以计算其值的标量。
   * 标量编译器负责将RexNode表达式编译为可执行的代码，
   * 用于在运行时计算表达式的值，如过滤条件、投影表达式等。
   */
  interface ScalarCompiler { // 标量编译器接口
    /** 编译表达式列表，返回标量生产者 */
    Scalar.Producer compile(List<RexNode> nodes, RelDataType inputRowType); // 编译方法
  }
}
