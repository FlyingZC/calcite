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
package org.apache.calcite.test.fuzzer; // 定义包路径，该类位于org.apache.calcite.test.fuzzer包下，用于测试Rex表达式模糊化功能

import org.apache.calcite.plan.Strong; // 导入Strong类，用于强类型检查和约束
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类，表示字面量表达式
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式的基类
import org.apache.calcite.rex.RexProgramBuilderBase; // 导入RexProgramBuilderBase类，作为测试类的基类提供Rex表达式构建功能
import org.apache.calcite.rex.RexUnknownAs; // 导入RexUnknownAs枚举，定义如何处理UNKNOWN值
import org.apache.calcite.rex.RexUtil; // 导入RexUtil类，提供Rex表达式的工具方法
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator接口，表示SQL操作符
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类，包含标准SQL操作符
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义SQL类型名称
import org.apache.calcite.sql.type.SqlTypeUtil; // 导入SqlTypeUtil类，提供SQL类型相关的工具方法
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，表示不可变的位集合

import org.junit.jupiter.api.Disabled; // 导入Disabled注解，用于禁用测试方法
import org.junit.jupiter.api.Test; // 导入Test注解，标记测试方法
import org.slf4j.Logger; // 导入Logger接口，用于日志记录
import org.slf4j.LoggerFactory; // 导入LoggerFactory类，用于创建Logger实例

import java.time.Duration; // 导入Duration类，表示时间间隔
import java.time.temporal.ChronoUnit; // 导入ChronoUnit枚举，定义时间单位
import java.util.ArrayList; // 导入ArrayList类，动态数组实现
import java.util.Arrays; // 导入Arrays类，提供数组操作的工具方法
import java.util.Comparator; // 导入Comparator接口，用于对象比较
import java.util.HashSet; // 导入HashSet类，哈希集合实现
import java.util.Iterator; // 导入Iterator接口，用于遍历集合
import java.util.List; // 导入List接口，列表集合
import java.util.PriorityQueue; // 导入PriorityQueue类，优先队列实现
import java.util.Random; // 导入Random类，用于生成随机数
import java.util.Set; // 导入Set接口，集合接口

import static org.hamcrest.CoreMatchers.is; // 导入is匹配器，用于断言
import static org.hamcrest.MatcherAssert.assertThat; // 导入断言工具类
import static org.junit.jupiter.api.Assertions.fail; // 导入fail方法，用于测试失败

/**
 * Validates that {@link org.apache.calcite.rex.RexSimplify} is able to deal
 * with a randomized {@link RexNode}.
 * // 验证RexSimplify能够处理随机生成的RexNode表达式
 *
 * <p>The default fuzzing time is 5 seconds to keep overall test duration
 * reasonable. The test starts from a random point every time, so the longer it
 * runs the more errors it detects.
 * // 默认的模糊化测试时间为5秒，以保持整体测试时间合理。测试每次从随机点开始，运行时间越长，检测到的错误越多
 */
class RexProgramFuzzyTest extends RexProgramBuilderBase { // RexProgramFuzzyTest类：Rex表达式模糊化测试类，继承自RexProgramBuilderBase基类，用于测试RexSimplify简化器的正确性和鲁棒性
  protected static final Logger LOGGER = // 日志记录器，用于记录测试过程中的信息和错误
      LoggerFactory.getLogger(RexProgramFuzzyTest.class); // 通过LoggerFactory创建RexProgramFuzzyTest类的日志记录器

  private static final Duration TEST_DURATION = // 测试持续时间，控制模糊化测试运行的时间长度
      Duration.of(Integer.getInteger("rex.fuzzing.duration", 5), ChronoUnit.SECONDS); // 从系统属性获取持续时间，默认为5秒
  private static final long TEST_ITERATIONS = Long.getLong("rex.fuzzing.iterations", 2000); // 测试迭代次数，控制模糊化测试运行的次数，默认为2000次
  // Stop fuzzing after detecting MAX_FAILURES errors
  private static final int MAX_FAILURES = // 最大失败次数，达到此次数后停止测试
      Integer.getInteger("rex.fuzzing.max.failures", 1); // 从系统属性获取最大失败次数，默认为1次
  // Number of slowest to simplify expressions to show
  private static final int TOPN_SLOWEST = // 显示最慢简化表达式的数量
      Integer.getInteger("rex.fuzzing.max.slowest", 0); // 从系统属性获取数量，默认为0（不显示）
  // 0 means use random seed
  // 42 is used to make sure tests pass in CI
  private static final long SEED = // 随机种子，用于控制随机数生成的确定性
      Long.getLong("rex.fuzzing.seed", 44); // 从系统属性获取种子，默认为44

  private static final long DEFAULT_FUZZ_TEST_SEED = // 默认模糊化测试的随机种子
      Long.getLong("rex.fuzzing.default.seed", 0); // 从系统属性获取，默认为0（使用随机种子）
  private static final Duration DEFAULT_FUZZ_TEST_DURATION = // 默认模糊化测试的持续时间
      Duration.of(Integer.getInteger("rex.fuzzing.default.duration", 5), ChronoUnit.SECONDS); // 从系统属性获取，默认为5秒
  private static final long DEFAULT_FUZZ_TEST_ITERATIONS = // 默认模糊化测试的迭代次数
      Long.getLong("rex.fuzzing.default.iterations", 0); // 从系统属性获取，默认为0（不限制次数）
  private static final boolean DEFAULT_FUZZ_TEST_FAIL = // 默认模糊化测试是否在失败时抛出异常
      Boolean.getBoolean("rex.fuzzing.default.fail"); // 从系统属性获取，默认为false

  private PriorityQueue<SimplifyTask> slowestTasks; // 优先队列，用于存储简化最慢的任务，用于性能分析

  private long currentSeed = 0; // 当前测试使用的随机种子，用于调试和重现问题

  private static final Strong STRONG = Strong.of(ImmutableBitSet.of()); // Strong实例，用于强类型检查，表示没有强约束

  /**
   * A bounded variation of {@link PriorityQueue}.
   * // PriorityQueue的有界变体，只保留最大的N个元素
   *
   * @param <E> the type of elements held in this collection
   * // 泛型参数E，表示集合中元素的类型，必须实现Comparable接口
   */
  private static class TopN<E extends Comparable<E>> extends PriorityQueue<E> { // TopN内部类：有界优先队列，继承自PriorityQueue，只保留最大的N个元素
    private final int n; // 队列的最大容量，表示最多保留的元素数量

    private TopN(int n) { // 构造方法：创建指定容量的TopN队列
      this.n = n; // 初始化队列容量
    }

    @Override public boolean offer(E o) { // 重写offer方法：向队列中添加元素，如果队列已满则移除最小的元素
      if (size() == n) { // 检查队列是否已满
        E peek = peek(); // 获取队列中的最小元素（队首元素）
        if (peek != null && peek.compareTo(o) > 0) { // 如果最小元素大于新元素
          // If the smallest element in the queue exceeds the added one
          // then just ignore the offer
          // 如果队列中最小的元素大于要添加的元素，则忽略此次添加
          return false; // 返回false，表示元素未被添加
        }
        // otherwise extract the smallest element, and offer a new one
        // 否则移除最小的元素，添加新元素
        poll(); // 移除队列中的最小元素
      }
      return super.offer(o); // 调用父类的offer方法添加元素
    }

    @Override public Iterator<E> iterator() { // 重写iterator方法：禁止使用迭代器遍历
      throw new UnsupportedOperationException("Order of elements is not defined, please use .peek"); // 抛出异常，提示使用peek方法
    }
  }

  /**
   * Verifies {@code IS TRUE(IS NULL(null))} kind of expressions up to 4 level deep.
   * // 验证嵌套调用表达式，如IS TRUE(IS NULL(null))，最多嵌套4层深度
   */
  @Test void testNestedCalls() { // 测试方法：验证嵌套的布尔表达式调用
    nestedCalls(trueLiteral); // 使用true字面量测试嵌套调用
    nestedCalls(falseLiteral); // 使用false字面量测试嵌套调用
    nestedCalls(nullBool); // 使用null布尔值测试嵌套调用
    nestedCalls(vBool()); // 使用可空布尔变量测试嵌套调用
    nestedCalls(vBoolNotNull()); // 使用非空布尔变量测试嵌套调用
  }

  private void nestedCalls(RexNode arg) { // 私有方法：生成并验证嵌套的布尔表达式调用
    SqlOperator[] operators = { // 定义布尔操作符数组，用于生成嵌套表达式
        SqlStdOperatorTable.NOT, // NOT操作符：逻辑非
        SqlStdOperatorTable.IS_FALSE, // IS_FALSE操作符：判断是否为false
        SqlStdOperatorTable.IS_NOT_FALSE, // IS_NOT_FALSE操作符：判断是否不为false
        SqlStdOperatorTable.IS_TRUE, // IS_TRUE操作符：判断是否为true
        SqlStdOperatorTable.IS_NOT_TRUE, // IS_NOT_TRUE操作符：判断是否不为true
        SqlStdOperatorTable.IS_NULL, // IS_NULL操作符：判断是否为null
        SqlStdOperatorTable.IS_NOT_NULL, // IS_NOT_NULL操作符：判断是否不为null
        SqlStdOperatorTable.IS_UNKNOWN, // IS_UNKNOWN操作符：判断是否为unknown
        SqlStdOperatorTable.IS_NOT_UNKNOWN // IS_NOT_UNKNOWN操作符：判断是否不为unknown
    };
    for (SqlOperator op1 : operators) { // 第一层嵌套：遍历所有操作符
      RexNode n1 = rexBuilder.makeCall(op1, arg); // 使用操作符op1创建第一层表达式
      checkUnknownAs(n1); // 检查第一层表达式的简化结果
      for (SqlOperator op2 : operators) { // 第二层嵌套：遍历所有操作符
        RexNode n2 = rexBuilder.makeCall(op2, n1); // 使用操作符op2创建第二层表达式
        checkUnknownAs(n2); // 检查第二层表达式的简化结果
        for (SqlOperator op3 : operators) { // 第三层嵌套：遍历所有操作符
          RexNode n3 = rexBuilder.makeCall(op3, n2); // 使用操作符op3创建第三层表达式
          checkUnknownAs(n3); // 检查第三层表达式的简化结果
          for (SqlOperator op4 : operators) { // 第四层嵌套：遍历所有操作符
            RexNode n4 = rexBuilder.makeCall(op4, n3); // 使用操作符op4创建第四层表达式
            checkUnknownAs(n4); // 检查第四层表达式的简化结果
          }
        }
      }
    }
  }

  private void checkUnknownAs(RexNode node) { // 私有方法：检查节点在不同unknownAs模式下的简化结果
    checkUnknownAsAndShrink(node, RexUnknownAs.FALSE); // 检查unknownAs为FALSE时的简化结果
    checkUnknownAsAndShrink(node, RexUnknownAs.UNKNOWN); // 检查unknownAs为UNKNOWN时的简化结果
    checkUnknownAsAndShrink(node, RexUnknownAs.TRUE); // 检查unknownAs为TRUE时的简化结果
  }

  private void checkUnknownAsAndShrink(RexNode node, RexUnknownAs unknownAs) { // 私有方法：检查节点并在失败时尝试缩小表达式以便更好地理解错误
    try {
      checkUnknownAs(node, unknownAs); // 尝试检查节点的简化结果
    } catch (Exception e) { // 如果检查失败
      // Try shrink the example so human can understand it better
      // 尝试缩小示例表达式，使人类更容易理解
      Random rnd = new Random(); // 创建随机数生成器
      rnd.setSeed(currentSeed); // 使用当前种子设置随机数生成器，确保可重现性
      long deadline = System.currentTimeMillis() + 20000; // 设置截止时间为20秒后
      RexNode original = node; // 保存原始节点
      int len = Integer.MAX_VALUE; // 初始化最小长度为最大值
      for (int i = 0; i < 100000 && System.currentTimeMillis() < deadline; i++) { // 最多尝试100000次或直到超时
        RexShrinker shrinker = new RexShrinker(rnd, rexBuilder); // 创建RexShrinker实例用于缩小表达式
        RexNode newNode = node.accept(shrinker); // 应用缩小器生成新的节点
        try {
          checkUnknownAs(newNode, unknownAs); // 检查缩小后的节点
          // bad shrink
          // 缩小失败，新节点没有触发相同的错误
        } catch (Exception ex) { // 如果缩小后的节点也触发错误
          // Good shrink
          // 缩小成功，找到了更小的失败用例
          node = newNode; // 更新节点为缩小后的节点
          String str = nodeToString(node); // 将节点转换为字符串
          int newLen = str.length(); // 获取字符串长度
          if (newLen < len) { // 如果新长度小于之前的最小长度
            long remaining = deadline - System.currentTimeMillis(); // 计算剩余时间
            System.out.println("Shrinked to " + newLen + " chars, time remaining " + remaining); // 输出缩小进度
            len = newLen; // 更新最小长度
          }
        }
      }
      if (original.toString().equals(node.toString())) { // 如果节点没有被缩小
        // Bad luck, throw original exception
        // 不走运，抛出原始异常
        throw e; // 抛出原始异常
      }
      checkUnknownAs(node, unknownAs); // 重新检查缩小后的节点
    }
  }

  private void checkUnknownAs(RexNode node, RexUnknownAs unknownAs) { // 私有方法：检查节点在指定unknownAs模式下的简化结果是否正确
    RexNode opt; // 声明简化后的表达式节点
    final String uaf = unknownAsString(unknownAs); // 获取unknownAs模式的字符串表示
    try {
      long start = System.nanoTime(); // 记录开始时间，用于性能测量
      opt = simplify.simplifyUnknownAs(node, unknownAs); // 调用simplify方法简化表达式
      long end = System.nanoTime(); // 记录结束时间
      if (end - start > 1000 && slowestTasks != null) { // 如果简化时间超过1000纳秒且slowestTasks不为空
        slowestTasks.add(new SimplifyTask(node, currentSeed, opt, end - start)); // 将慢速任务添加到优先队列
      }
    } catch (AssertionError a) { // 捕获断言错误
      String message = a.getMessage(); // 获取错误消息
      if (message != null && message.startsWith("result mismatch")) { // 如果是结果不匹配错误
        throw a; // 重新抛出断言错误
      }
      throw new IllegalStateException("Unable to simplify " + uaf + nodeToString(node), a); // 抛出非法状态异常
    } catch (Throwable t) { // 捕获其他异常
      throw new IllegalStateException("Unable to simplify " + uaf + nodeToString(node), t); // 抛出非法状态异常
    }
    if (trueLiteral.equals(opt) && node.isAlwaysFalse()) { // 如果简化结果为TRUE但原节点总是FALSE
      String msg = nodeToString(node); // 将节点转换为字符串
      fail(msg + " optimizes to TRUE, isAlwaysFalse MUST not be true " + uaf); // 测试失败：简化结果与节点属性矛盾
//      This is a missing optimization, not a bug
//      这是一个缺失的优化，不是bug
//      assertFalse(msg + " optimizes to TRUE, isAlwaysTrue MUST be true",
//          !node.isAlwaysTrue());
    }
    if (falseLiteral.equals(opt) && node.isAlwaysTrue()) { // 如果简化结果为FALSE但原节点总是TRUE
      String msg = nodeToString(node); // 将节点转换为字符串
      fail(msg + " optimizes to FALSE, isAlwaysTrue MUST not be true " + uaf); // 测试失败：简化结果与节点属性矛盾
//      This is a missing optimization, not a bug
//      这是一个缺失的优化，不是bug
//      assertFalse(msg + " optimizes to FALSE, isAlwaysFalse MUST be true",
//          !node.isAlwaysFalse());
    }
    if (STRONG.isNull(opt)) { // 如果简化结果为NULL
      if (node.isAlwaysTrue()) { // 如果原节点总是TRUE
        fail(nodeToString(node) + " optimizes to NULL: " + nodeToString(opt) // 测试失败：简化结果与节点属性矛盾
            + ", isAlwaysTrue MUST be FALSE " + uaf);
      }
      if (node.isAlwaysFalse()) { // 如果原节点总是FALSE
        fail(nodeToString(node) + " optimizes to NULL: " + nodeToString(opt) // 测试失败：简化结果与节点属性矛盾
            + ", isAlwaysFalse MUST be FALSE " + uaf);
      }
    }
    if (node.isAlwaysTrue()) { // 如果原节点总是TRUE
      if (!trueLiteral.equals(opt)) { // 如果简化结果不是TRUE
        assertThat(nodeToString(node) // 断言：简化结果应该是TRUE
                + " isAlwaysTrue, so it should simplify to TRUE " + uaf,
            opt, is(trueLiteral));
      }
    }
    if (node.isAlwaysFalse()) { // 如果原节点总是FALSE
      if (!falseLiteral.equals(opt)) { // 如果简化结果不是FALSE
        assertThat(nodeToString(node) // 断言：简化结果应该是FALSE
            + " isAlwaysFalse, so it should simplify to FALSE " + uaf,
            opt, is(falseLiteral));
      }
    }
    if (STRONG.isNull(node)) { // 如果原节点总是NULL
      switch (unknownAs) { // 根据unknownAs模式进行不同处理
      case FALSE: // 如果unknownAs为FALSE
        if (node.getType().getSqlTypeName() == SqlTypeName.BOOLEAN) { // 如果节点类型为BOOLEAN
          if (!falseLiteral.equals(opt)) { // 如果简化结果不是FALSE
            assertThat(nodeToString(node) // 断言：简化结果应该是FALSE
                    + " is always null boolean, so it should simplify to FALSE "
                    + uaf,
                opt, is(falseLiteral));
          }
        } else { // 如果节点类型不是BOOLEAN
          if (!RexLiteral.isNullLiteral(opt)) { // 如果简化结果不是NULL字面量
            assertThat(nodeToString(node) // 断言：简化结果应该是NULL
                    + " is always null (non boolean), so it should simplify to NULL "
                    + uaf,
                opt, is(rexBuilder.makeNullLiteral(node.getType())));
          }
        }
        break;
      case TRUE: // 如果unknownAs为TRUE
        if (node.getType().getSqlTypeName() == SqlTypeName.BOOLEAN) { // 如果节点类型为BOOLEAN
          if (!trueLiteral.equals(opt)) { // 如果简化结果不是TRUE
            assertThat(nodeToString(node) // 断言：简化结果应该是TRUE
                    + " is always null boolean, so it should simplify to TRUE "
                    + uaf,
                opt, is(trueLiteral));
          }
        } else { // 如果节点类型不是BOOLEAN
          if (!RexLiteral.isNullLiteral(opt)) { // 如果简化结果不是NULL字面量
            assertThat(nodeToString(node) // 断言：简化结果应该是NULL
                    + " is always null (non boolean), so it should simplify to NULL "
                    + uaf,
                opt, is(rexBuilder.makeNullLiteral(node.getType())));
          }
        }
        break;
      case UNKNOWN: // 如果unknownAs为UNKNOWN
        if (!RexUtil.isNull(opt)) { // 如果简化结果不是NULL
          assertThat(nodeToString(node) // 断言：简化结果应该是NULL
                  + " is always null, so it should simplify to NULL " + uaf,
              opt, is(nullBool));
        }
      }
    }
    if (unknownAs == RexUnknownAs.UNKNOWN // 如果unknownAs为UNKNOWN
        && opt.getType().isNullable() // 且简化结果类型可为空
        && !node.getType().isNullable()) { // 但原节点类型不可为空
      fail(nodeToString(node) + " had non-nullable type " + opt.getType() // 测试失败：类型可空性改变
          + ", and it was optimized to " + nodeToString(opt)
          + " that has nullable type " + opt.getType());
    }
    if (!SqlTypeUtil.equalSansNullability(typeFactory, node.getType(), opt.getType())) { // 如果简化前后类型不一致
      assertThat(nodeToString(node) // 断言：简化前后类型应该一致
              + " has different type after simplification to "
              + nodeToString(opt),
          opt.getType(), is(node.getType()));
    }
  }

  private String unknownAsString(RexUnknownAs unknownAs) { // 私有方法：将RexUnknownAs枚举转换为字符串表示
    switch (unknownAs) { // 根据unknownAs的值返回不同的字符串
    case UNKNOWN: // 如果为UNKNOWN
    default: // 默认情况
      return ""; // 返回空字符串
    case FALSE: // 如果为FALSE
      return "unknownAsFalse"; // 返回"unknownAsFalse"
    case TRUE: // 如果为TRUE
      return "unknownAsTrue"; // 返回"unknownAsTrue"
    }
  }

  private static String nodeToString(RexNode node) { // 私有静态方法：将RexNode转换为字符串表示
    return node + "\n" // 返回节点的字符串表示
        + node.accept(new RexToTestCodeShuttle()); // 使用RexToTestCodeShuttle访问器获取更详细的字符串表示
  }

  private static void trimStackTrace(Throwable t, int maxStackLines) { // 私有静态方法：裁剪异常的堆栈跟踪，只保留指定行数
    StackTraceElement[] stackTrace = t.getStackTrace(); // 获取异常的堆栈跟踪
    if (stackTrace == null || stackTrace.length <= maxStackLines) { // 如果堆栈跟踪为空或行数不超过最大行数
      return; // 直接返回，不进行裁剪
    }
    stackTrace = Arrays.copyOf(stackTrace, maxStackLines); // 复制堆栈跟踪的前maxStackLines行
    t.setStackTrace(stackTrace); // 设置裁剪后的堆栈跟踪
  }

  @Test void defaultFuzzTest() { // 测试方法：默认的模糊化测试，使用默认参数运行
    try {
      runRexFuzzer(DEFAULT_FUZZ_TEST_SEED, DEFAULT_FUZZ_TEST_DURATION, 1, // 运行Rex模糊化测试，最大失败次数为1
          DEFAULT_FUZZ_TEST_ITERATIONS, 0); // 不显示最慢的任务
    } catch (Throwable e) { // 捕获异常
      for (Throwable t = e; t != null; t = t.getCause()) { // 遍历异常链
        trimStackTrace(t, DEFAULT_FUZZ_TEST_FAIL ? 8 : 4); // 裁剪堆栈跟踪，根据配置保留8或4行
      }
      if (DEFAULT_FUZZ_TEST_FAIL) { // 如果配置为失败时抛出异常
        throw e; // 重新抛出异常
      }
      LOGGER.info("Randomized test identified a potential defect. Feel free to fix that issue", e); // 记录信息日志
    }
  }

  @Disabled("Ignore for now: CALCITE-3457") // 禁用此测试，因为CALCITE-3457问题
  @Test void testFuzzy() { // 测试方法：模糊化测试，使用配置的参数运行
    runRexFuzzer(SEED, TEST_DURATION, MAX_FAILURES, TEST_ITERATIONS, TOPN_SLOWEST); // 运行Rex模糊化测试
  }

  private void runRexFuzzer(long startSeed, Duration testDuration, int maxFailures, // 私有方法：运行Rex表达式模糊化测试
      long testIterations, int topnSlowest) { // 参数：起始种子、测试持续时间、最大失败次数、测试迭代次数、显示最慢任务的数量
    if (testDuration.toMillis() == 0) { // 如果测试持续时间为0
      return; // 直接返回，不执行测试
    }
    slowestTasks = new TopN<>(topnSlowest > 0 ? topnSlowest : 1); // 创建TopN优先队列，用于存储最慢的简化任务
    Random r = new Random(); // 创建随机数生成器
    if (startSeed != 0) { // 如果起始种子不为0
      LOGGER.info("Using seed {} for rex fuzzing", startSeed); // 记录日志，显示使用的种子
      r.setSeed(startSeed); // 设置随机数生成器的种子
    }
    long start = System.currentTimeMillis(); // 记录测试开始时间
    long deadline = start + testDuration.toMillis(); // 计算测试截止时间
    List<Throwable> exceptions = new ArrayList<>(); // 创建异常列表，用于存储测试失败的异常
    Set<String> duplicates = new HashSet<>(); // 创建重复异常消息集合，用于去重
    long total = 0; // 总测试次数
    int dup = 0; // 重复失败次数
    int fail = 0; // 失败次数
    RexFuzzer fuzzer = new RexFuzzer(rexBuilder, typeFactory); // 创建RexFuzzer实例，用于生成随机表达式
    while (System.currentTimeMillis() < deadline && exceptions.size() < maxFailures // 循环执行测试，直到超时或达到最大失败次数
        && (testIterations == 0 || total < testIterations)) { // 或达到最大迭代次数
      long seed = r.nextLong(); // 生成随机种子
      this.currentSeed = seed; // 保存当前种子，用于调试
      r.setSeed(seed); // 设置随机数生成器的种子
      try {
        total++; // 增加总测试次数
        generateRexAndCheckTrueFalse(fuzzer, r); // 生成Rex表达式并检查真值
      } catch (Throwable e) { // 捕获异常
        if (!duplicates.add(e.getMessage())) { // 如果异常消息已存在（重复异常）
          dup++; // 增加重复失败次数
          // known exception, nothing to see here
          // 已知异常，无需处理
          continue; // 跳过此次循环
        }
        fail++; // 增加失败次数
        StackTraceElement[] stackTrace = e.getStackTrace(); // 获取堆栈跟踪
        for (int j = 0; j < stackTrace.length; j++) { // 遍历堆栈跟踪
          if (stackTrace[j].getClassName().endsWith("RexProgramTest")) { // 如果找到RexProgramTest类
            e.setStackTrace(Arrays.copyOf(stackTrace, j + 1)); // 裁剪堆栈跟踪
            break; // 跳出循环
          }
        }
        e.addSuppressed(new Throwable("seed " + seed) { // 添加种子信息到异常中
          @Override public synchronized Throwable fillInStackTrace() { // 重写fillInStackTrace方法
            return this; // 返回this，不填充堆栈跟踪
          }
        });
        exceptions.add(e); // 将异常添加到异常列表
      }
    }
    long rate = total * 1000 / (System.currentTimeMillis() - start); // 计算测试速率（每秒测试次数）
    LOGGER.info( // 记录日志，输出测试结果
        "Rex fuzzing results: number of cases tested={}, failed cases={}, duplicate failures={}, fuzz rate={} per second",
        total, fail, dup, rate); // 输出测试总数、失败数、重复失败数和测试速率

    if (topnSlowest > 0) { // 如果需要显示最慢的任务
      LOGGER.info("The 5 slowest to simplify nodes were"); // 记录日志
      SimplifyTask task; // 声明SimplifyTask变量
      RexToTestCodeShuttle v = new RexToTestCodeShuttle(); // 创建RexToTestCodeShuttle实例
      while ((task = slowestTasks.poll()) != null) { // 遍历最慢的任务
        LOGGER.info(task.duration / 1000 + " us (" + task.seed + ")"); // 输出任务耗时和种子
        LOGGER.info("      " + task.node.toString()); // 输出原始节点
        LOGGER.info("      " + task.node.accept(v)); // 输出节点的详细表示
        LOGGER.info("    ->" + task.result.toString()); // 输出简化结果
      }
    }

    if (exceptions.isEmpty()) { // 如果没有异常
      return; // 直接返回
    }

    // Print the shortest fails first
    // 先打印最短的失败信息
    exceptions.sort( // 对异常列表排序
        Comparator. // 使用比较器
            <Throwable>comparingInt(t -> t.getMessage() == null ? -1 : t.getMessage().length()) // 按消息长度排序
            .thenComparing(Throwable::getMessage)); // 然后按消息内容排序

    // The first exception will be thrown, so the others go to printStackTrace
    // 第一个异常将被抛出，其他异常将打印堆栈跟踪
    for (int i = 1; i < exceptions.size() && i < 100; i++) { // 遍历异常列表（最多100个）
      Throwable exception = exceptions.get(i); // 获取异常
      exception.printStackTrace(); // 打印堆栈跟踪
    }

    Throwable ex = exceptions.get(0); // 获取第一个异常
    if (ex instanceof Error) { // 如果是Error类型
      throw (Error) ex; // 抛出Error
    }
    if (ex instanceof RuntimeException) { // 如果是RuntimeException类型
      throw (RuntimeException) ex; // 抛出RuntimeException
    }
    throw new RuntimeException("Exception in runRexFuzzer", ex); // 抛出RuntimeException包装其他异常
  }

  private void generateRexAndCheckTrueFalse(RexFuzzer fuzzer, Random r) { // 私有方法：生成Rex表达式并检查其真值
    RexNode expression = fuzzer.getExpression(r, r.nextInt(10)); // 使用fuzzer生成随机表达式，深度为0-9
    checkUnknownAs(expression); // 检查表达式的简化结果
  }

  @Disabled("This is just a scaffold for quick investigation of a single fuzz test") // 禁用此测试，仅用于快速调查单个模糊化测试
  @Test void singleFuzzyTest() { // 测试方法：单个模糊化测试，用于调试和调查特定问题
    Random r = new Random(); // 创建随机数生成器
    r.setSeed(4887662474363391810L); // 设置固定种子，确保可重现性
    RexFuzzer fuzzer = new RexFuzzer(rexBuilder, typeFactory); // 创建RexFuzzer实例
    generateRexAndCheckTrueFalse(fuzzer, r); // 生成Rex表达式并检查真值
  }
} // RexProgramFuzzyTest类结束
