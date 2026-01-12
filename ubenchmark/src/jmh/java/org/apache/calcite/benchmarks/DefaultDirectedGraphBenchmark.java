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
package org.apache.calcite.benchmarks;

import org.apache.calcite.util.graph.DefaultDirectedGraph;
import org.apache.calcite.util.graph.DefaultEdge;
import org.apache.calcite.util.graph.DirectedGraph;

import com.google.common.collect.Lists;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for {@link org.apache.calcite.util.graph.DefaultDirectedGraph}.
 * DefaultDirectedGraph的性能基准测试类，用于测试有向图的各种操作性能
 * 使用JMH(Java Microbenchmark Harness)框架进行微基准测试，评估DefaultDirectedGraph类中关键方法的性能
 * 主要测试包括：获取入边、获取出边、添加顶点、添加边、获取边、删除边、批量删除顶点等操作
 * 测试使用二叉树结构作为测试数据，通过不同规模的数据集来评估性能表现
 */
public class DefaultDirectedGraphBenchmark {

  /** Node in the graph. */
  private static class Node { // 图中的节点类，用于表示有向图中的顶点，每个节点有一个唯一的id标识符
    final int id; // 节点的唯一标识符，使用final修饰确保不可变，便于equals和hashCode方法的实现

    private Node(int id) { // Node类的私有构造方法，用于创建具有指定id的节点实例
      this.id = id; // 将传入的id参数赋值给实例变量id，初始化节点的唯一标识符
    }

    @Override public boolean equals(Object o) { // 重写Object类的equals方法，用于比较两个Node对象是否相等
      return o == this // 首先检查是否是同一个对象引用，如果是则直接返回true
          || o instanceof Node // 如果不是同一引用，检查o是否是Node类的实例
          && ((Node) o).id == id; // 如果是Node实例，比较两个节点的id是否相等，相等则返回true
    }

    @Override public int hashCode() { // 重写Object类的hashCode方法，用于支持基于哈希的集合操作
      return Objects.hash(id); // 使用Objects工具类的hash方法基于id生成哈希码，确保与equals方法的一致性
    }
  }

  /**
   * State object for the benchmarks.
   * 基准测试的状态对象类，用于管理测试过程中需要共享的数据和状态
   * 使用@State注解标记为JMH状态类，Scope.Benchmark表示该状态在基准测试级别共享
   * 每次基准测试调用时都会重新初始化状态，确保测试的独立性
   */
  @State(Scope.Benchmark)
  public static class GraphState { // 静态内部类，作为基准测试的状态容器，存储测试所需的图结构和节点集合

    static final int NUM_LAYERS = 8; // 静态常量，定义二叉树的层数，8层将产生255个节点(2^8-1)

    DirectedGraph<Node, DefaultEdge> graph; // 有向图实例，使用DefaultDirectedGraph实现，节点类型为Node，边类型为DefaultEdge

    List<Node> nodes; // 节点列表，存储图中所有节点的引用，按创建顺序排列

    List<Node> tenNodes; // 包含前10%节点的列表，用于测试批量删除操作的性能
    List<Node> twentyNodes; // 包含前20%节点的列表，用于测试批量删除操作的性能
    List<Node> thirtyNodes; // 包含前30%节点的列表，用于测试批量删除操作的性能
    List<Node> fortyNodes; // 包含前40%节点的列表，用于测试批量删除操作的性能
    List<Node> fiftyNodes; // 包含前50%节点的列表，用于测试批量删除操作的性能
    List<Node> sixtyNodes; // 包含前60%节点的列表，用于测试批量删除操作的性能
    List<Node> seventyNodes; // 包含前70%节点的列表，用于测试批量删除操作的性能
    List<Node> eightyNodes; // 包含前80%节点的列表，用于测试批量删除操作的性能
    List<Node> ninetyNodes; // 包含前90%节点的列表，用于测试批量删除操作的性能

    @Setup(Level.Invocation) // JMH注解，表示在每次基准测试方法调用之前执行此方法
    public void setUp() { // 设置方法，用于初始化测试状态，构建二叉树结构和准备各种规模的节点集合
      nodes = new ArrayList<>(); // 创建空的节点列表，用于存储所有创建的节点

      // create a binary tree
      graph = DefaultDirectedGraph.create(); // 创建DefaultDirectedGraph实例，用于构建有向图结构

      // create nodes
      int curId = 1; // 当前节点ID计数器，从1开始，用于为每个节点分配唯一标识符
      Node root = new Node(curId++); // 创建根节点，id为1，然后递增计数器
      nodes.add(root); // 将根节点添加到节点列表中
      graph.addVertex(root); // 将根节点添加到图中作为顶点
      List<Node> prevLayerNodes = Lists.newArrayList(root); // 创建前一层节点列表，初始只包含根节点，用于迭代构建二叉树

      for (int i = 1; i < NUM_LAYERS; i++) { // 循环创建二叉树的每一层，从第1层开始(根节点是第0层)
        List<Node> curLayerNodes = new ArrayList<>(); // 创建当前层节点列表，用于存储本层创建的所有节点
        for (Node node : prevLayerNodes) { // 遍历前一层的每个节点，为每个节点创建左右子节点
          Node leftChild = new Node(curId++); // 创建左子节点，使用当前id并递增计数器
          Node rightChild = new Node(curId++); // 创建右子节点，使用当前id并递增计数器

          curLayerNodes.add(leftChild); // 将左子节点添加到当前层节点列表
          curLayerNodes.add(rightChild); // 将右子节点添加到当前层节点列表

          nodes.add(leftChild); // 将左子节点添加到全局节点列表
          nodes.add(rightChild); // 将右子节点添加到全局节点列表

          graph.addVertex(leftChild); // 将左子节点作为顶点添加到图中
          graph.addVertex(rightChild); // 将右子节点作为顶点添加到图中

          graph.addEdge(node, leftChild); // 添加从父节点到左子节点的有向边
          graph.addEdge(node, rightChild); // 添加从父节点到右子节点的有向边
        }
        prevLayerNodes = curLayerNodes; // 将当前层节点列表赋值给前一层节点列表，为下一层迭代做准备
      }

      int tenNodeCount = (int) (nodes.size() * 0.1); // 计算10%的节点数量，用于创建包含前10%节点的子列表
      int twentyNodeCount = (int) (nodes.size() * 0.2); // 计算20%的节点数量，用于创建包含前20%节点的子列表
      int thirtyNodeCount = (int) (nodes.size() * 0.3); // 计算30%的节点数量，用于创建包含前30%节点的子列表
      int fortyNodeCount = (int) (nodes.size() * 0.4); // 计算40%的节点数量，用于创建包含前40%节点的子列表
      int fiftyNodeCount = (int) (nodes.size() * 0.5); // 计算50%的节点数量，用于创建包含前50%节点的子列表
      int sixtyNodeCount = (int) (nodes.size() * 0.6); // 计算60%的节点数量，用于创建包含前60%节点的子列表
      int seventyNodeCount = (int) (nodes.size() * 0.7); // 计算70%的节点数量，用于创建包含前70%节点的子列表
      int eightyNodeCount = (int) (nodes.size() * 0.8); // 计算80%的节点数量，用于创建包含前80%节点的子列表
      int ninetyNodeCount = (int) (nodes.size() * 0.9); // 计算90%的节点数量，用于创建包含前90%节点的子列表
      tenNodes = nodes.subList(0, tenNodeCount); // 从节点列表中截取前10%的节点，创建子列表视图
      twentyNodes = nodes.subList(0, twentyNodeCount); // 从节点列表中截取前20%的节点，创建子列表视图
      thirtyNodes = nodes.subList(0, thirtyNodeCount); // 从节点列表中截取前30%的节点，创建子列表视图
      fortyNodes = nodes.subList(0, fortyNodeCount); // 从节点列表中截取前40%的节点，创建子列表视图
      fiftyNodes = nodes.subList(0, fiftyNodeCount); // 从节点列表中截取前50%的节点，创建子列表视图
      sixtyNodes = nodes.subList(0, sixtyNodeCount); // 从节点列表中截取前60%的节点，创建子列表视图
      seventyNodes = nodes.subList(0, seventyNodeCount); // 从节点列表中截取前70%的节点，创建子列表视图
      eightyNodes = nodes.subList(0, eightyNodeCount); // 从节点列表中截取前80%的节点，创建子列表视图
      ninetyNodes = nodes.subList(0, ninetyNodeCount); // 从节点列表中截取前90%的节点，创建子列表视图
    }
  }

  @Benchmark // JMH注解，标记此方法为基准测试方法，JMH会多次调用此方法来测量性能
  @BenchmarkMode(Mode.AverageTime) // JMH注解，指定基准测试模式为平均时间，测量方法调用的平均执行时间
  @OutputTimeUnit(TimeUnit.MICROSECONDS) // JMH注解，指定输出时间单位为微秒，使结果更易读
  public int getInwardEdgesBenchmark(GraphState state) { // 基准测试方法，测试获取入边操作的性能，返回入边总数
    int sum = 0; // 初始化入边总数为0，用于累加所有节点的入边数量
    int curId = 1; // 初始化当前节点ID为1，从根节点开始
    for (int i = 0; i < GraphState.NUM_LAYERS; i++) { // 循环遍历二叉树的每一层，共8层
      // get the first node in each layer
      Node curNode = state.nodes.get(curId - 1); // 获取当前层的第一个节点，使用curId-1作为索引(因为列表从0开始)
      sum += state.graph.getInwardEdges(curNode).size(); // 获取当前节点的所有入边，并将入边数量累加到sum中
      curId *= 2; // 将curId乘以2，移动到下一层的第一个节点(二叉树中每层第一个节点的ID遵循2^n的规律)
    }
    return sum; // 返回所有遍历节点的入边总数
  }

  @Benchmark // JMH注解，标记此方法为基准测试方法，JMH会多次调用此方法来测量性能
  @BenchmarkMode(Mode.AverageTime) // JMH注解，指定基准测试模式为平均时间，测量方法调用的平均执行时间
  @OutputTimeUnit(TimeUnit.MICROSECONDS) // JMH注解，指定输出时间单位为微秒，使结果更易读
  public int getOutwardEdgesBenchmark(GraphState state) { // 基准测试方法，测试获取出边操作的性能，返回出边总数
    int sum = 0; // 初始化出边总数为0，用于累加所有节点的出边数量
    int curId = 1; // 初始化当前节点ID为1，从根节点开始
    for (int i = 0; i < GraphState.NUM_LAYERS; i++) { // 循环遍历二叉树的每一层，共8层
      // get the first node in each layer
      Node curNode = state.nodes.get(curId - 1); // 获取当前层的第一个节点，使用curId-1作为索引(因为列表从0开始)
      sum += state.graph.getOutwardEdges(curNode).size(); // 获取当前节点的所有出边，并将出边数量累加到sum中
      curId *= 2; // 将curId乘以2，移动到下一层的第一个节点(二叉树中每层第一个节点的ID遵循2^n的规律)
    }
    return sum; // 返回所有遍历节点的出边总数
  }

  @Benchmark // JMH注解，标记此方法为基准测试方法，JMH会多次调用此方法来测量性能
  @BenchmarkMode(Mode.AverageTime) // JMH注解，指定基准测试模式为平均时间，测量方法调用的平均执行时间
  @OutputTimeUnit(TimeUnit.MICROSECONDS) // JMH注解，指定输出时间单位为微秒，使结果更易读
  public boolean addVertexBenchmark(GraphState state) { // 基准测试方法，测试添加顶点操作的性能，返回添加是否成功
    return state.graph.addVertex(new Node(100)); // 向图中添加一个id为100的新节点，并返回添加操作的结果(成功返回true，已存在返回false)
  }

  @Benchmark // JMH注解，标记此方法为基准测试方法，JMH会多次调用此方法来测量性能
  @BenchmarkMode(Mode.AverageTime) // JMH注解，指定基准测试模式为平均时间，测量方法调用的平均执行时间
  @OutputTimeUnit(TimeUnit.MICROSECONDS) // JMH注解，指定输出时间单位为微秒，使结果更易读
  public DefaultEdge addEdgeBenchmark(GraphState state) { // 基准测试方法，测试添加边操作的性能，返回添加的边对象
    return state.graph.addEdge(state.nodes.get(0), state.nodes.get(5)); // 在图中添加从第0个节点到第5个节点的有向边，并返回边对象
  }

  @Benchmark // JMH注解，标记此方法为基准测试方法，JMH会多次调用此方法来测量性能
  @BenchmarkMode(Mode.AverageTime) // JMH注解，指定基准测试模式为平均时间，测量方法调用的平均执行时间
  @OutputTimeUnit(TimeUnit.MICROSECONDS) // JMH注解，指定输出时间单位为微秒，使结果更易读
  public DefaultEdge getEdgeBenchmark(GraphState state) { // 基准测试方法，测试获取边操作的性能，返回边对象(如果存在)
    return state.graph.getEdge(state.nodes.get(0), state.nodes.get(1)); // 获取从第0个节点到第1个节点的边，如果边不存在则返回null
  }

  @Benchmark // JMH注解，标记此方法为基准测试方法，JMH会多次调用此方法来测量性能
  @BenchmarkMode(Mode.AverageTime) // JMH注解，指定基准测试模式为平均时间，测量方法调用的平均执行时间
  @OutputTimeUnit(TimeUnit.MICROSECONDS) // JMH注解，指定输出时间单位为微秒，使结果更易读
  public boolean removeEdgeBenchmark(GraphState state) { // 基准测试方法，测试删除边操作的性能，返回删除是否成功
    return state.graph.removeEdge(state.nodes.get(0), state.nodes.get(1)); // 删除从第0个节点到第1个节点的边，并返回删除操作的结果(成功返回true，边不存在返回false)
  }

  @Benchmark // JMH注解，标记此方法为基准测试方法，JMH会多次调用此方法来测量性能
  @BenchmarkMode(Mode.AverageTime) // JMH注解，指定基准测试模式为平均时间，测量方法调用的平均执行时间
  @OutputTimeUnit(TimeUnit.MICROSECONDS) // JMH注解，指定输出时间单位为微秒，使结果更易读
  public void removeAllVertices10Benchmark(GraphState state) { // 基准测试方法，测试批量删除10%顶点操作的性能
    state.graph.removeAllVertices(state.tenNodes); // 从图中删除前10%的节点及其相关边
  }

  @Benchmark // JMH注解，标记此方法为基准测试方法，JMH会多次调用此方法来测量性能
  @BenchmarkMode(Mode.AverageTime) // JMH注解，指定基准测试模式为平均时间，测量方法调用的平均执行时间
  @OutputTimeUnit(TimeUnit.MICROSECONDS) // JMH注解，指定输出时间单位为微秒，使结果更易读
  public void removeAllVertices20Benchmark(GraphState state) { // 基准测试方法，测试批量删除20%顶点操作的性能
    state.graph.removeAllVertices(state.twentyNodes); // 从图中删除前20%的节点及其相关边
  }

  @Benchmark // JMH注解，标记此方法为基准测试方法，JMH会多次调用此方法来测量性能
  @BenchmarkMode(Mode.AverageTime) // JMH注解，指定基准测试模式为平均时间，测量方法调用的平均执行时间
  @OutputTimeUnit(TimeUnit.MICROSECONDS) // JMH注解，指定输出时间单位为微秒，使结果更易读
  public void removeAllVertices30Benchmark(GraphState state) { // 基准测试方法，测试批量删除30%顶点操作的性能
    state.graph.removeAllVertices(state.thirtyNodes); // 从图中删除前30%的节点及其相关边
  }

  @Benchmark // JMH注解，标记此方法为基准测试方法，JMH会多次调用此方法来测量性能
  @BenchmarkMode(Mode.AverageTime) // JMH注解，指定基准测试模式为平均时间，测量方法调用的平均执行时间
  @OutputTimeUnit(TimeUnit.MICROSECONDS) // JMH注解，指定输出时间单位为微秒，使结果更易读
  public void removeAllVertices40Benchmark(GraphState state) { // 基准测试方法，测试批量删除40%顶点操作的性能
    state.graph.removeAllVertices(state.fortyNodes); // 从图中删除前40%的节点及其相关边
  }

  @Benchmark // JMH注解，标记此方法为基准测试方法，JMH会多次调用此方法来测量性能
  @BenchmarkMode(Mode.AverageTime) // JMH注解，指定基准测试模式为平均时间，测量方法调用的平均执行时间
  @OutputTimeUnit(TimeUnit.MICROSECONDS) // JMH注解，指定输出时间单位为微秒，使结果更易读
  public void removeAllVertices50Benchmark(GraphState state) { // 基准测试方法，测试批量删除50%顶点操作的性能
    state.graph.removeAllVertices(state.fiftyNodes); // 从图中删除前50%的节点及其相关边
  }

  @Benchmark // JMH注解，标记此方法为基准测试方法，JMH会多次调用此方法来测量性能
  @BenchmarkMode(Mode.AverageTime) // JMH注解，指定基准测试模式为平均时间，测量方法调用的平均执行时间
  @OutputTimeUnit(TimeUnit.MICROSECONDS) // JMH注解，指定输出时间单位为微秒，使结果更易读
  public void removeAllVertices60Benchmark(GraphState state) { // 基准测试方法，测试批量删除60%顶点操作的性能
    state.graph.removeAllVertices(state.sixtyNodes); // 从图中删除前60%的节点及其相关边
  }

  @Benchmark // JMH注解，标记此方法为基准测试方法，JMH会多次调用此方法来测量性能
  @BenchmarkMode(Mode.AverageTime) // JMH注解，指定基准测试模式为平均时间，测量方法调用的平均执行时间
  @OutputTimeUnit(TimeUnit.MICROSECONDS) // JMH注解，指定输出时间单位为微秒，使结果更易读
  public void removeAllVertices70Benchmark(GraphState state) { // 基准测试方法，测试批量删除70%顶点操作的性能
    state.graph.removeAllVertices(state.seventyNodes); // 从图中删除前70%的节点及其相关边
  }

  @Benchmark // JMH注解，标记此方法为基准测试方法，JMH会多次调用此方法来测量性能
  @BenchmarkMode(Mode.AverageTime) // JMH注解，指定基准测试模式为平均时间，测量方法调用的平均执行时间
  @OutputTimeUnit(TimeUnit.MICROSECONDS) // JMH注解，指定输出时间单位为微秒，使结果更易读
  public void removeAllVertices80Benchmark(GraphState state) { // 基准测试方法，测试批量删除80%顶点操作的性能
    state.graph.removeAllVertices(state.eightyNodes); // 从图中删除前80%的节点及其相关边
  }

  @Benchmark // JMH注解，标记此方法为基准测试方法，JMH会多次调用此方法来测量性能
  @BenchmarkMode(Mode.AverageTime) // JMH注解，指定基准测试模式为平均时间，测量方法调用的平均执行时间
  @OutputTimeUnit(TimeUnit.MICROSECONDS) // JMH注解，指定输出时间单位为微秒，使结果更易读
  public void removeAllVertices90Benchmark(GraphState state) { // 基准测试方法，测试批量删除90%顶点操作的性能
    state.graph.removeAllVertices(state.ninetyNodes); // 从图中删除前90%的节点及其相关边
  }

  @Benchmark // JMH注解，标记此方法为基准测试方法，JMH会多次调用此方法来测量性能
  @BenchmarkMode(Mode.AverageTime) // JMH注解，指定基准测试模式为平均时间，测量方法调用的平均执行时间
  @OutputTimeUnit(TimeUnit.MICROSECONDS) // JMH注解，指定输出时间单位为微秒，使结果更易读
  public void removeAllVertices100Benchmark(GraphState state) { // 基准测试方法，测试批量删除100%顶点操作的性能
    state.graph.removeAllVertices(state.nodes); // 从图中删除所有节点及其相关边，清空整个图
  }

  public static void main(String[] args) throws RunnerException { // 主方法，用于独立运行基准测试，可以指定命令行参数
    Options opt = new OptionsBuilder() // 创建JMH选项构建器，用于配置基准测试的运行参数
        .include(DefaultDirectedGraphBenchmark.class.getName()) // 指定要运行的基准测试类，只运行当前类中的所有@Benchmark方法
        .forks(1) // 设置fork次数为1，表示在单独的JVM进程中运行一次基准测试
        .build(); // 构建选项对象，完成配置

    new Runner(opt).run(); // 创建Runner实例并运行基准测试，执行所有配置好的基准测试方法并输出结果
  }
}
