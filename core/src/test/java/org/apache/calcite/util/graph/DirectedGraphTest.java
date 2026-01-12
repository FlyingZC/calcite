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
package org.apache.calcite.util.graph; // 声明包名，这个类属于org.apache.calcite.util.graph包，用于图相关的数据结构和算法
import org.apache.calcite.plan.hep.HepMatchOrder; // 导入HepMatchOrder类，用于定义匹配顺序，特别是TOP_DOWN和BOTTOM_UP

import com.google.common.collect.ImmutableList; // 导入Guava库的不可变列表类
import com.google.common.collect.ImmutableSet; // 导入Guava库的不可变集合类
import com.google.common.collect.Lists; // 导入Guava库的Lists工具类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的返回值
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.util.ArrayList; // 导入ArrayList类
import java.util.Arrays; // 导入Arrays工具类
import java.util.LinkedHashSet; // 导入LinkedHashSet类，保持插入顺序的Set
import java.util.List; // 导入List接口
import java.util.Set; // 导入Set接口

// 导入Hamcrest断言库的各种匹配器，用于编写更易读的测试断言
import static org.hamcrest.CoreMatchers.equalTo; // 判断相等
import static org.hamcrest.CoreMatchers.is; // 判断是否相等
import static org.hamcrest.CoreMatchers.notNullValue; // 判断不为null
import static org.hamcrest.CoreMatchers.nullValue; // 判断为null
import static org.hamcrest.MatcherAssert.assertThat; // 断言的核心方法
import static org.hamcrest.Matchers.empty; // 判断集合为空
import static org.hamcrest.Matchers.hasSize; // 判断集合大小
import static org.hamcrest.Matchers.hasToString; // 判断toString结果
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize; // 判断可迭代对象的大小
// 导入JUnit5的断言方法
import static org.junit.jupiter.api.Assertions.assertFalse; // 断言为false
import static org.junit.jupiter.api.Assertions.assertNotNull; // 断言不为null
import static org.junit.jupiter.api.Assertions.assertTrue; // 断言为true
import static org.junit.jupiter.api.Assertions.fail; // 标记测试失败

/**
 * Unit test for {@link DirectedGraph}.
 * 有向图的单元测试类，用于测试DirectedGraph及其相关功能
 * 包括最短路径查找、深度优先遍历、广度优先遍历、拓扑排序、环检测等功能
 * 这个测试类全面验证了有向图的各种操作和算法的正确性
 */
class DirectedGraphTest { // 定义测试类，名为DirectedGraphTest
  @Test void testOne() { // 测试方法：测试基本的有向图操作，包括添加顶点和边、查找最短路径
    DirectedGraph<String, DefaultEdge> g = DefaultDirectedGraph.create(); // 创建一个默认的有向图实例，顶点类型为String，边类型为DefaultEdge
    g.addVertex("A"); // 向图中添加顶点A
    g.addVertex("B"); // 向图中添加顶点B
    g.addVertex("C"); // 向图中添加顶点C
    g.addVertex("D"); // 向图中添加顶点D
    g.addVertex("E"); // 向图中添加顶点E
    g.addVertex("F"); // 向图中添加顶点F
    g.addEdge("A", "B"); // 添加从A到B的有向边
    g.addEdge("B", "C"); // 添加从B到C的有向边
    g.addEdge("D", "C"); // 添加从D到C的有向边
    g.addEdge("C", "D"); // 添加从C到D的有向边，与上一条边形成环
    g.addEdge("E", "F"); // 添加从E到F的有向边
    g.addEdge("C", "C"); // 添加从C到C的自环边
    assertThat(shortestPath(g, "A", "D"), hasToString("[A, B, C, D]")); // 验证从A到D的最短路径是[A, B, C, D]
    g.addEdge("B", "D"); // 添加从B到D的直接边，这将创建一条更短的路径
    assertThat(shortestPath(g, "A", "D"), hasToString("[A, B, D]")); // 验证现在从A到D的最短路径变为[A, B, D]，因为有了直接边
    assertThat("There is no path from A to E", // 断言消息：从A到E没有路径
        shortestPath(g, "A", "E"), nullValue()); // 验证从A到E的最短路径返回null，因为不存在路径
    assertThat(shortestPath(g, "D", "D"), hasToString("[D]")); // 验证从D到D的最短路径是[D]，即起点和终点相同时返回只包含该节点的路径
    assertThat("Node X is not in the graph", // 断言消息：节点X不在图中
        shortestPath(g, "X", "A"), nullValue()); // 验证从不存在的节点X到A的最短路径返回null
    assertThat(paths(g, "A", "D"), hasToString("[[A, B, D], [A, B, C, D]]")); // 验证从A到D的所有路径，按长度排序返回[[A, B, D], [A, B, C, D]]
  }

  private <V> @Nullable List<V> shortestPath(DirectedGraph<V, DefaultEdge> g, // 私有辅助方法：获取从source到target的最短路径，泛型V表示顶点类型
      V source, V target) { // 参数：source是起点，target是终点
    List<List<V>> paths = Graphs.makeImmutable(g).getPaths(source, target); // 将图转换为不可变形式，然后获取从source到target的所有路径
    return paths.isEmpty() ? null : paths.get(0); // 如果没有路径返回null，否则返回第一条路径（最短路径）
  }

  private <V> @Nullable List<V> shortestPath( // 私有辅助方法：获取从source到target的最短路径，重载方法接受FrozenGraph参数
      Graphs.FrozenGraph<V, DefaultEdge> g, V source, V target) { // 参数：g是冻结图，source是起点，target是终点
    List<List<V>> paths = g.getPaths(source, target); // 直接从冻结图中获取从source到target的所有路径
    return paths.isEmpty() ? null : paths.get(0); // 如果没有路径返回null，否则返回第一条路径（最短路径）
  }

  private <V> List<List<V>> paths(DirectedGraph<V, DefaultEdge> g, // 私有辅助方法：获取从source到target的所有路径
      V source, V target) { // 参数：source是起点，target是终点
    return Graphs.makeImmutable(g).getPaths(source, target); // 将图转换为不可变形式，然后返回从source到target的所有路径
  }

  @Test void testVertexMustExist() { // 测试方法：测试顶点必须存在才能添加边的约束条件
    DirectedGraph<String, DefaultEdge> g = DefaultDirectedGraph.create(); // 创建一个默认的有向图实例

    final boolean b = g.addVertex("A"); // 添加顶点A，返回true表示添加成功
    assertTrue(b); // 断言添加成功

    final boolean b2 = g.addVertex("A"); // 再次添加顶点A，返回false表示顶点已存在
    assertFalse(b2); // 断言添加失败，因为顶点已存在

    try { // 尝试添加边，但目标顶点B不存在
      DefaultEdge x = g.addEdge("A", "B"); // 尝试添加从A到B的边，但B不存在
      fail("expected exception, got " + x); // 如果没有抛出异常则测试失败
    } catch (IllegalArgumentException e) { // 捕获预期的非法参数异常
      // ok // 异常是预期的，测试通过
    }
    g.addVertex("B"); // 添加顶点B
    DefaultEdge x = g.addEdge("A", "B"); // 现在可以添加从A到B的边了
    assertNotNull(x); // 断言边对象不为null
    DefaultEdge x2 = g.addEdge("A", "B"); // 再次添加相同的边
    assertThat(x2, nullValue()); // 断言返回null，因为边已存在
    try { // 尝试添加边，但源顶点Z不存在
      DefaultEdge x3 = g.addEdge("Z", "A"); // 尝试添加从Z到A的边，但Z不存在
      fail("expected exception, got " + x3); // 如果没有抛出异常则测试失败
    } catch (IllegalArgumentException e) { // 捕获预期的非法参数异常
      // ok // 异常是预期的，测试通过
    }
    g.addVertex("Z"); // 添加顶点Z
    DefaultEdge x3 = g.addEdge("Z", "A"); // 现在可以添加从Z到A的边了
    assertNotNull(x3); // 断言边对象不为null
    DefaultEdge x4 = g.addEdge("Z", "A"); // 再次添加相同的边
    assertThat(x4, nullValue()); // 断言返回null，因为边已存在

    // Attempting to add a vertex already present does not change the graph. // 注释：尝试添加已存在的顶点不会改变图
    final List<DefaultEdge> in1 = g.getInwardEdges("A"); // 获取顶点A的入边列表
    final List<DefaultEdge> out1 = g.getOutwardEdges("A"); // 获取顶点A的出边列表
    final boolean b3 = g.addVertex("A"); // 再次添加顶点A
    assertFalse(b3); // 断言返回false，因为顶点已存在
    final List<DefaultEdge> in2 = g.getInwardEdges("A"); // 再次获取顶点A的入边列表
    final List<DefaultEdge> out2 = g.getOutwardEdges("A"); // 再次获取顶点A的出边列表
    assertThat(in1, is(in2)); // 断言入边列表没有变化
    assertThat(out1, is(out2)); // 断言出边列表没有变化
  }

  /** Unit test for {@link DepthFirstIterator}. */ // 注释：DepthFirstIterator的单元测试
  @Test void testDepthFirst() { // 测试方法：测试深度优先遍历迭代器
    final DefaultDirectedGraph<String, DefaultEdge> graph = createDag(); // 创建一个有向无环图（DAG）
    final List<String> list = new ArrayList<>(); // 创建一个列表用于存储遍历结果
    for (String s : DepthFirstIterator.of(graph, "A")) { // 使用深度优先迭代器从顶点A开始遍历
      list.add(s); // 将访问的顶点添加到列表中
    }
    assertThat(list, hasToString("[A, B, C, D, E, C, D, F]")); // 验证深度优先遍历的结果，注意C和D被访问了两次
    list.clear(); // 清空列表
    DepthFirstIterator.reachable(list, graph, "A"); // 使用静态方法获取从A可达的所有顶点
    assertThat(list, hasToString("[A, B, C, D, E, C, D, F]")); // 验证结果与迭代器方式相同
  }

  /** Unit test for {@link DepthFirstIterator}. */ // 注释：测试前驱列表功能
  @Test void testPredecessorList() { // 测试方法：测试获取顶点的前驱列表
    final DefaultDirectedGraph<String, DefaultEdge> graph = createDag(); // 创建一个有向无环图
    final List<String> list = Graphs.predecessorListOf(graph, "C"); // 获取顶点C的所有前驱节点
    assertThat(list, hasToString("[B, E]")); // 验证C的前驱是B和E
  }

  /** Unit test for
   * {@link DefaultDirectedGraph#removeAllVertices(java.util.Collection)}. */ // 注释：测试批量删除顶点功能
  @Test void testRemoveAllVertices() { // 测试方法：测试删除多个顶点及其关联的边
    final DefaultDirectedGraph<String, DefaultEdge> graph = createDag(); // 创建一个有向无环图
    assertThat(graph.edgeSet(), hasSize(6)); // 验证图中有6条边
    graph.removeAllVertices(Arrays.asList("B", "E")); // 删除顶点B和E
    assertThat(graph.vertexSet(), hasToString("[A, C, D, F]")); // 验证剩余顶点为[A, C, D, F]
    assertThat(graph.edgeSet(), hasSize(1)); // 验证只剩1条边（C->D）
    assertThat(graph.edgeSet(), hasToString("[C -> D]")); // 验证剩余的边是C->D
  }

  /** Unit test for {@link TopologicalOrderIterator}. */ // 注释：TopologicalOrderIterator的单元测试
  @Test void testTopologicalOrderIterator() { // 测试方法：测试拓扑排序迭代器
    final DefaultDirectedGraph<String, DefaultEdge> graph = createDag(); // 创建一个有向无环图
    final List<String> list = new ArrayList<>(); // 创建一个列表用于存储拓扑排序结果
    for (String s : TopologicalOrderIterator.of(graph)) { // 使用拓扑排序迭代器遍历图
      list.add(s); // 将顶点添加到列表中
    }
    assertThat(list, hasToString("[A, B, E, C, F, D]")); // 验证拓扑排序结果，从上到下按照依赖关系排列
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-7030">[CALCITE-7030]
   * Enhance TopologicalOrderIterator to support BOTTOM_UP</a>. */ // 注释：测试用例，增强TopologicalOrderIterator以支持BOTTOM_UP模式
  @Test void testTopologicalOrderWithBottomUpIterator() { // 测试方法：测试从下到上的拓扑排序
    final DefaultDirectedGraph<String, DefaultEdge> graph = createDag(); // 创建一个有向无环图
    final List<String> list = new ArrayList<>(); // 创建一个列表用于存储排序结果
    for (String s : TopologicalOrderIterator.of(graph, HepMatchOrder.BOTTOM_UP)) { // 使用BOTTOM_UP模式进行拓扑排序
      list.add(s); // 将顶点添加到列表中
    }
    assertThat(list, hasToString("[D, F, C, B, E, A]")); // 验证从下到上的拓扑排序结果，与TOP_DOWN相反
  }

  private DefaultDirectedGraph<String, DefaultEdge> createDag() { // 私有辅助方法：创建一个有向无环图（DAG）
    //    D         F // 图结构注释：D和F是终点
    //    ^         ^ // 箭头表示边的方向
    //    |         | // 竖线表示依赖关系
    //    C <------ + // C被D依赖，也被E指向
    //    ^         | // C被B和E依赖
    //    |         | // 竖线表示依赖关系
    //    |         | // 空行
    //    B <- A -> E // A指向B和E，B指向C
    final DefaultDirectedGraph<String, DefaultEdge> graph = // 创建图实例
        DefaultDirectedGraph.create(); // 调用工厂方法创建图
    graph.addVertex("A"); // 添加顶点A
    graph.addVertex("B"); // 添加顶点B
    graph.addVertex("C"); // 添加顶点C
    graph.addVertex("D"); // 添加顶点D
    graph.addVertex("E"); // 添加顶点E
    graph.addVertex("F"); // 添加顶点F
    graph.addEdge("A", "B"); // 添加边A->B
    graph.addEdge("B", "C"); // 添加边B->C
    graph.addEdge("C", "D"); // 添加边C->D
    graph.addEdge("A", "E"); // 添加边A->E
    graph.addEdge("E", "C"); // 添加边E->C
    graph.addEdge("E", "F"); // 添加边E->F
    return graph; // 返回创建的图
  }

  private DefaultDirectedGraph<String, DefaultEdge> createDag1() { // 私有辅助方法：创建另一个有向无环图（DAG1）
    //    +--> E <--+ // 图结构注释：E是终点，被C和D指向
    //    |         | // 竖线表示边的方向
    //    C         | // C指向E
    //    ^         D // C被B依赖，D指向E
    //    |         ^ // 竖线表示依赖关系
    //    |         | // 竖线表示依赖关系
    //    |         | // 竖线表示依赖关系
    //    B <-- A --+ // A指向B和D，B指向C
    final DefaultDirectedGraph<String, DefaultEdge> graph = // 创建图实例
        DefaultDirectedGraph.create(); // 调用工厂方法创建图
    graph.addVertex("A"); // 添加顶点A
    graph.addVertex("B"); // 添加顶点B
    graph.addVertex("C"); // 添加顶点C
    graph.addVertex("D"); // 添加顶点D
    graph.addVertex("E"); // 添加顶点E
    graph.addVertex("F"); // 添加顶点F（虽然未使用）
    graph.addEdge("A", "B"); // 添加边A->B
    graph.addEdge("B", "C"); // 添加边B->C
    graph.addEdge("A", "D"); // 添加边A->D
    graph.addEdge("D", "E"); // 添加边D->E
    graph.addEdge("C", "E"); // 添加边C->E
    return graph; // 返回创建的图
  }

  /** Unit test for
   * {@link org.apache.calcite.util.graph.Graphs.FrozenGraph}. */ // 注释：FrozenGraph的单元测试
  @Test void testPaths() { // 测试方法：测试冻结图的路径查找功能
    final DefaultDirectedGraph<String, DefaultEdge> graph = createDag1(); // 创建DAG1图
    final Graphs.FrozenGraph<String, DefaultEdge> frozenGraph = // 创建冻结图实例
        Graphs.makeImmutable(graph); // 将可变图转换为不可变的冻结图
    assertThat(shortestPath(frozenGraph, "A", "B"), hasToString("[A, B]")); // 验证从A到B的最短路径
    assertThat(frozenGraph.getPaths("A", "B"), hasToString("[[A, B]]")); // 验证从A到B的所有路径
    assertThat(shortestPath(frozenGraph, "A", "E"), hasToString("[A, D, E]")); // 验证从A到E的最短路径是[A, D, E]而非[A, B, C, E]
    assertThat(frozenGraph.getPaths("A", "E"), // 验证从A到E的所有路径
        hasToString("[[A, D, E], [A, B, C, E]]")); // 两条路径按长度排序
    assertThat(shortestPath(frozenGraph, "B", "A"), nullValue()); // 验证从B到A没有路径（反向不可达）

    assertThat(shortestPath(frozenGraph, "D", "C"), nullValue()); // 验证从D到C没有路径
    assertThat(frozenGraph.getPaths("D", "E"), hasToString("[[D, E]]")); // 验证从D到E只有一条路径
    assertThat(shortestPath(frozenGraph, "D", "E"), hasToString("[D, E]")); // 验证从D到E的最短路径
  }

  @Test void testDistances() { // 测试方法：测试图的距离计算功能
    final DefaultDirectedGraph<String, DefaultEdge> graph = createDag1(); // 创建DAG1图
    final Graphs.FrozenGraph<String, DefaultEdge> frozenGraph = // 创建冻结图实例
        Graphs.makeImmutable(graph); // 将可变图转换为不可变的冻结图
    assertThat(frozenGraph.getShortestDistance("A", "B"), is(1)); // 验证从A到B的最短距离为1
    assertThat(frozenGraph.getShortestDistance("A", "E"), is(2)); // 验证从A到E的最短距离为2（A->D->E）
    assertThat(frozenGraph.getShortestDistance("B", "A"), is(-1)); // 验证从B到A不可达，返回-1
    assertThat(frozenGraph.getShortestDistance("D", "C"), is(-1)); // 验证从D到C不可达，返回-1
    assertThat(frozenGraph.getShortestDistance("D", "E"), is(1)); // 验证从D到E的最短距离为1
    assertThat(frozenGraph.getShortestDistance("B", "B"), is(0)); // 验证从B到B的距离为0（自身）
  }

  /** Unit test for {@link org.apache.calcite.util.graph.CycleDetector}. */ // 注释：CycleDetector的单元测试
  @Test void testCycleDetection() { // 测试方法：测试环检测功能
    // A - B - C - D // 初始图结构注释：这是一个有向无环图
    //  \     / // A指向B和E
    //   +- E - F // E指向F
    DefaultDirectedGraph<String, DefaultEdge> graph = createDag(); // 创建DAG图
    assertThat(new CycleDetector<>(graph).findCycles(), empty()); // 验证图中没有环

    // Add cycle C-D-E-C // 注释：添加环C-D-E-C
    //
    // A - B - C - D // 添加边D->E后形成环
    //  \     /     \ // C->D->E->C
    //   +- E - F   | // F也在环中
    //      ^      / // E指向C和F
    //      \_____/ // 形成大环
    graph.addEdge("D", "E"); // 添加边D->E，形成环C-D-E-C-F
    assertThat(new CycleDetector<>(graph).findCycles(), // 验证检测到的环
        is(ImmutableSet.of("C", "D", "E", "F"))); // 环包含C、D、E、F四个节点

    // Add another cycle, D-C-D in addition to C-D-E-C. // 注释：添加另一个环D-C-D
    //           __ // 添加边D->C形成自环
    //          /  \ // C和D之间双向连接
    // A - B - C - D // 现在有多个环
    //  \     /     \ // C-D和C-D-E-C
    //   +- E - F   | // F仍在环中
    //      ^      / // E指向C和F
    //      \_____/ // 复杂的环结构
    graph.addEdge("D", "C"); // 添加边D->C
    assertThat(new CycleDetector<>(graph).findCycles(), // 验证检测到的环
        is(ImmutableSet.of("C", "D", "E", "F"))); // 仍然是C、D、E、F四个节点

    graph.removeEdge("D", "E"); // 移除边D->E
    graph.removeEdge("D", "C"); // 移除边D->C
    graph.addEdge("C", "B"); // 添加边C->B

    // Add cycle of length 2, C-B-C // 注释：添加长度为2的环C-B-C
    //       __ // B和C之间的双向边
    //      /  \ // 形成环B-C-B
    // A - B - C - D // 新图结构
    //  \     / // D不在环中但被检测出来
    //   +- E - F // 注意：这里的实现可能有误
    //
    // Detected cycle contains "D", which is downstream from the cycle but not // 注释：检测到的环包含"D"，这是环的下游节点但不在环中
    // in the cycle. Not sure whether that is correct. // 不确定这是否正确
    assertThat(new CycleDetector<>(graph).findCycles(), // 验证检测到的环
        is(ImmutableSet.of("B", "C", "D"))); // 返回B、C、D，其中D是下游节点

    // Add single-node cycle, C-C // 注释：添加单节点环（自环）C-C
    //
    //        ___ // 自环符号
    //        \ / // C指向自己
    // A - B - C - D // 新图结构
    //  \     / // D仍然被检测出来
    //   +- E - F // 自环C-C
    graph.removeEdge("C", "B"); // 移除边C->B
    graph.addEdge("C", "C"); // 添加自环边C->C
    assertThat(new CycleDetector<>(graph).findCycles(), // 验证检测到的环
        is(ImmutableSet.of("C", "D"))); // 返回C和D，D是下游节点

    // Empty graph is not cyclic. // 注释：空图不是循环图
    graph.removeAllVertices(graph.vertexSet()); // 删除所有顶点
    assertThat(new CycleDetector<>(graph).findCycles(), // 验证空图没有环
        is(ImmutableSet.of())); // 返回空集合
  }

  /** Unit test for
   * {@link org.apache.calcite.util.graph.BreadthFirstIterator}. */ // 注释：BreadthFirstIterator的单元测试
  @Test void testBreadthFirstIterator() { // 测试方法：测试广度优先遍历迭代器
    DefaultDirectedGraph<String, DefaultEdge> graph = createDag(); // 创建DAG图
    final List<String> expected = // 创建期望的遍历顺序列表
        ImmutableList.of("A", "B", "E", "C", "F", "D"); // 广度优先遍历顺序：先访问A，然后是B和E，然后是C和F，最后是D
    assertThat(getA(graph, "A"), equalTo(expected)); // 验证使用迭代器方式的遍历结果
    assertThat(Lists.newArrayList(getB(graph, "A")), equalTo(expected)); // 验证使用静态方法方式的遍历结果
  }

  private List<String> getA(DefaultDirectedGraph<String, DefaultEdge> graph, // 私有辅助方法：使用迭代器方式进行广度优先遍历
      String root) { // 参数：root是遍历的起始顶点
    final List<String> list = new ArrayList<>(); // 创建列表存储遍历结果
    for (String s : BreadthFirstIterator.of(graph, root)) { // 使用广度优先迭代器从root开始遍历
      list.add(s); // 将访问的顶点添加到列表中
    }
    return list; // 返回遍历结果列表
  }

  private Set<String> getB(DefaultDirectedGraph<String, DefaultEdge> graph, // 私有辅助方法：使用静态方法进行广度优先遍历
      String root) { // 参数：root是遍历的起始顶点
    final Set<String> list = new LinkedHashSet<>(); // 创建LinkedHashSet存储遍历结果，保持顺序
    BreadthFirstIterator.reachable(list, graph, root); // 使用静态方法获取从root可达的所有顶点
    return list; // 返回遍历结果集合
  }

  @Test void testAttributed() { // 测试方法：测试带属性的有向图
    AttributedDirectedGraph<String, DefaultEdge> g = // 创建带属性的有向图实例
        AttributedDirectedGraph.create(new DefaultAttributedEdgeFactory()); // 使用自定义边工厂创建图
    g.addVertex("A"); // 添加顶点A
    g.addVertex("B"); // 添加顶点B
    g.addVertex("C"); // 添加顶点C
    g.addVertex("D"); // 添加顶点D
    g.addVertex("E"); // 添加顶点E
    g.addVertex("F"); // 添加顶点F
    g.addEdge("A", "B", 1); // 添加带属性值的边A->B，属性值为1
    g.addEdge("B", "C", 1); // 添加带属性值的边B->C，属性值为1
    g.addEdge("D", "C", 1); // 添加带属性值的边D->C，属性值为1
    g.addEdge("C", "D", 1); // 添加带属性值的边C->D，属性值为1
    g.addEdge("E", "F", 1); // 添加带属性值的边E->F，属性值为1
    g.addEdge("C", "C", 1); // 添加带属性值的自环边C->C，属性值为1
    assertThat(shortestPath(g, "A", "D"), hasToString("[A, B, C, D]")); // 验证从A到D的最短路径
    g.addEdge("B", "D", 1); // 添加带属性值的边B->D，属性值为1
    assertThat(shortestPath(g, "A", "D"), hasToString("[A, B, D]")); // 验证现在从A到D的最短路径变为[A, B, D]
    assertThat("There is no path from A to E", shortestPath(g, "A", "E"), // 验证从A到E没有路径
        nullValue()); // 返回null
    assertThat(shortestPath(g, "D", "D"), hasToString("[D]")); // 验证从D到D的路径是[D]
    assertThat("Node X is not in the graph", shortestPath(g, "X", "A"), // 验证从不存在的节点X到A没有路径
        nullValue()); // 返回null
    assertThat(paths(g, "A", "D"), hasToString("[[A, B, D], [A, B, C, D]]")); // 验证从A到D的所有路径
    assertThat(g.addVertex("B"), is(false)); // 验证添加已存在的顶点B返回false

    assertThat(g.getEdges("A", "B"), iterableWithSize(1)); // 验证A到B只有1条边
    assertThat(g.addEdge("A", "B", 1), nullValue()); // 尝试添加相同的边（属性值相同），返回null
    assertThat(g.getEdges("A", "B"), iterableWithSize(1)); // 验证A到B仍然只有1条边
    assertThat(g.addEdge("A", "B", 2), notNullValue()); // 添加不同属性值的边A->B，属性值为2
    assertThat(g.getEdges("A", "B"), iterableWithSize(2)); // 验证A到B现在有2条边（属性值不同）
  }

  @Test void testToString() { // 测试方法：测试图的toString方法
    DefaultDirectedGraph<String, DefaultEdge> g = createDag(); // 创建DAG图
    assertThat(g, // 验证图的字符串表示
        hasToString("graph(vertices: [A, B, C, D, E, F], " // 顶点列表
            + "edges: [A -> B, A -> E, B -> C, C -> D, E -> C, E -> F])")); // 边列表

    DefaultDirectedGraph<String, DefaultEdge> g1 = createDag1(); // 创建DAG1图
    assertThat(g1, // 验证图的字符串表示
        hasToString("graph(vertices: [A, B, C, D, E, F], " // 顶点列表
            + "edges: [A -> B, A -> D, B -> C, C -> E, D -> E])")); // 边列表
  }

  /** Edge that stores its attributes in a list. */ // 注释：将属性存储在列表中的边类
  private static class DefaultAttributedEdge extends DefaultEdge { // 私有静态内部类：带属性的默认边，继承自DefaultEdge
    private final List list; // 成员变量：存储边的属性列表

    DefaultAttributedEdge(String source, String target, List list) { // 构造方法：创建带属性的边
      super(source, target); // 调用父类构造方法，设置源顶点和目标顶点
      this.list = ImmutableList.copyOf(list); // 将属性列表复制为不可变列表
    }

    @Override public int hashCode() { // 重写hashCode方法
      return super.hashCode() * 31 + list.hashCode(); // 结合父类hashCode和属性列表的hashCode
    }

    @Override public boolean equals(Object obj) { // 重写equals方法
      return this == obj // 先检查是否是同一个对象
          || obj instanceof DefaultAttributedEdge // 再检查是否是DefaultAttributedEdge类型
          && ((DefaultAttributedEdge) obj).source.equals(source) // 检查源顶点是否相同
          && ((DefaultAttributedEdge) obj).target.equals(target) // 检查目标顶点是否相同
          && ((DefaultAttributedEdge) obj).list.equals(list); // 检查属性列表是否相同
    }
  }

    /** Factory for {@link DefaultAttributedEdge}. */ // 注释：DefaultAttributedEdge的工厂类
  private static class DefaultAttributedEdgeFactory // 私有静态内部类：带属性边的工厂
      implements AttributedDirectedGraph.AttributedEdgeFactory<String, // 实现AttributedEdgeFactory接口
          DefaultEdge> { // 泛型参数：顶点类型为String，边类型为DefaultEdge
    public DefaultEdge createEdge(String v0, String v1, Object... attributes) { // 创建带属性的边的方法
      return new DefaultAttributedEdge(v0, v1, // 创建DefaultAttributedEdge实例
          ImmutableList.copyOf(attributes)); // 将属性数组复制为不可变列表
    }

    public DefaultEdge createEdge(String v0, String v1) { // 创建不带属性的边的方法
      throw new UnsupportedOperationException(); // 抛出不支持操作异常，因为必须提供属性
    }
  }
} // 类结束
