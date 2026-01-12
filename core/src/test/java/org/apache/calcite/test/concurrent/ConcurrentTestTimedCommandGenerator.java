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
package org.apache.calcite.test.concurrent; // 包声明，该类位于org.apache.calcite.test.concurrent包中，用于并发测试相关功能

import com.google.common.collect.ImmutableList; // 导入Google Guava库的ImmutableList类，用于创建不可变的列表集合

import java.io.PrintStream; // 导入Java IO的PrintStream类，用于输出打印流
import java.util.Collection; // 导入Java集合框架的Collection接口，表示一组对象的集合
import java.util.Iterator; // 导入Java集合框架的Iterator接口，用于遍历集合元素
import java.util.List; // 导入Java集合框架的List接口，表示有序的元素列表
import java.util.NoSuchElementException; // 导入Java集合框架的NoSuchElementException异常类，当元素不存在时抛出

/**
 * ConcurrentTestTimedCommandGenerator extends
 * {@link ConcurrentTestCommandGenerator} and repeats the configured command
 * sequence until a certain amount of time has elapsed.
 * ConcurrentTestTimedCommandGenerator类继承自ConcurrentTestCommandGenerator，它会重复执行配置的命令序列，直到达到指定的时间长度
 *
 * <p>The command sequence is always completed in full, even if the time limit
 * has been exceeded. Therefore, the time limit can only be considered the
 * minimum length of time that the test will run and not a guarantee of how long
 * the test will take.
 * 命令序列总是会完整执行完毕，即使时间限制已经超过，因此时间限制只能被视为测试运行的最小时间长度，而不能保证测试实际运行的确切时长
 */
public class ConcurrentTestTimedCommandGenerator
    extends ConcurrentTestCommandGenerator { // 定义ConcurrentTestTimedCommandGenerator类，继承自ConcurrentTestCommandGenerator父类，实现基于时间的命令生成器功能

  private final int runTimeSeconds; // 定义测试运行的最小时间长度（秒），使用final修饰表示该值在构造后不可改变
  private long endTimeMillis; // 定义测试结束的毫秒时间戳，用于判断测试是否应该继续运行

  /**
   * Constructs a new ConcurrentTestTimedCommandGenerator that will run
   * for at least the given amount of time. See
   * {@link ConcurrentTestTimedCommandGenerator} for more information on the
   * semantics of run-time length.
   * 构造一个新的ConcurrentTestTimedCommandGenerator实例，该实例将至少运行指定的时间长度，关于运行时间长度的语义请参见ConcurrentTestTimedCommandGenerator类的文档
   *
   * @param runTimeSeconds minimum run-time length, in seconds
   * 参数runTimeSeconds表示测试运行的最小时间长度，单位为秒
   */
  public ConcurrentTestTimedCommandGenerator(int runTimeSeconds) { // 定义构造方法，接受一个整数参数runTimeSeconds表示运行时间（秒）
    this.runTimeSeconds = runTimeSeconds; // 将传入的运行时间参数赋值给成员变量runTimeSeconds，保存测试运行时长配置
  }

  /**
   * Retrieves an Iterator based on the configured commands. This Iterator,
   * when it reaches the end of the command list will compare the current time
   * with the test's end time. If there is time left, the Iterator will repeat
   * the command sequence.
   * 获取基于配置命令的迭代器，当迭代器到达命令列表末尾时，会将当前时间与测试结束时间进行比较，如果还有剩余时间，迭代器将重复命令序列
   *
   * <p>The test's end time is computed by taking the value of <code>
   * System.currentTimeMillis()</code> the first time this method is called
   * (across all thread IDs) and adding the configured run time.
   * 测试结束时间是通过在第一次调用此方法时（跨所有线程ID）获取System.currentTimeMillis()的值并加上配置的运行时间来计算的
   *
   * @param threadId the thread ID to get an Iterator on
   * 参数threadId表示要获取迭代器的线程ID
   */
  Iterable<ConcurrentTestCommand> getCommandIterable(final int threadId) { // 定义getCommandIterable方法，返回一个可迭代的ConcurrentTestCommand集合，参数threadId为线程ID
    synchronized (this) { // 使用synchronized关键字对当前对象加锁，确保多线程环境下的线程安全性
      if (endTimeMillis == 0L) { // 检查endTimeMillis是否为0，表示这是第一次调用该方法
        endTimeMillis = // 如果是第一次调用，则计算并设置测试结束时间
            System.currentTimeMillis() + (runTimeSeconds * 1000); // 获取当前系统时间（毫秒）并加上运行时间（秒转换为毫秒），得到测试结束时间戳
      }
    } // 同步块结束，释放锁

    return () -> new TimedIterator<ConcurrentTestCommand>( // 返回一个Lambda表达式，该表达式创建一个新的TimedIterator实例
        getCommands(threadId), // 调用getCommands方法获取指定线程ID的命令列表
        endTimeMillis); // 将计算得到的结束时间传递给TimedIterator构造函数
  }

  /**
   * Outputs command sequence and notes how long the sequence will be
   * repeated.
   * 输出命令序列并注明该序列将重复多长时间
   */
  void printCommands( // 定义printCommands方法，用于打印命令序列信息
      PrintStream out, // 参数out表示输出打印流，用于输出命令序列信息
      Integer threadId) { // 参数threadId表示线程ID
    super.printCommands(out, threadId); // 调用父类ConcurrentTestCommandGenerator的printCommands方法，输出基本的命令序列信息
    out.println("Repeat sequence for " + runTimeSeconds + " seconds"); // 在输出流中打印命令序列将重复的秒数信息
  }

  //~ Inner Classes ----------------------------------------------------------

  /**
   * TimedIterator is an Iterator that repeats a given collection's elements
   * until <code>System.currentTimeMillis() &ge; endTimeMillis</code>.
   * TimedIterator是一个迭代器，它会重复给定集合的元素，直到当前系统时间达到或超过结束时间endTimeMillis
   *
   * @param <E> element type
   * 泛型参数E表示元素的类型
   */
  private static class TimedIterator<E> implements Iterator<E> { // 定义TimedIterator内部静态类，实现Iterator<E>接口，提供基于时间的迭代功能
    private final List<E> commands; // 定义命令列表，使用泛型E表示元素类型，final修饰表示该列表在构造后不可改变
    private final long endTimeMillis; // 定义迭代器结束的毫秒时间戳，final修饰表示该值在构造后不可改变
    private int commandIndex; // 定义当前命令索引，用于跟踪当前遍历到命令列表中的哪个位置

    private TimedIterator( // 定义TimedIterator的私有构造方法
        Collection<E> commands, // 参数commands表示要迭代的命令集合
        long endTimeMillis) { // 参数endTimeMillis表示迭代器结束的毫秒时间戳
      this.commands = ImmutableList.copyOf(commands); // 使用Guava的ImmutableList.copyOf方法将传入的集合转换为不可变列表，确保命令列表不会被修改
      this.endTimeMillis = endTimeMillis; // 将传入的结束时间戳赋值给成员变量endTimeMillis
      this.commandIndex = 0; // 将命令索引初始化为0，表示从列表的第一个元素开始遍历
    }

    public boolean hasNext() { // 实现Iterator接口的hasNext方法，判断是否还有下一个元素可以遍历
      if (commandIndex < commands.size()) { // 检查当前命令索引是否小于命令列表的大小，表示还有未遍历的命令
        return true; // 如果还有未遍历的命令，返回true表示还有下一个元素
      } // 如果当前命令索引已经到达或超过命令列表的大小，表示当前序列已经遍历完毕

      if (System.currentTimeMillis() < endTimeMillis) { // 检查当前系统时间是否小于结束时间，表示测试时间还未结束
        commandIndex = 0; // 如果时间未结束，将命令索引重置为0，准备重新遍历命令序列
        return !commands.isEmpty(); // 返回命令列表是否不为空，处理空数组的情况，如果命令列表为空则返回false
      } // 如果测试时间已经结束，则不再重复命令序列

      return false; // 如果既没有剩余命令，测试时间也已结束，返回false表示没有下一个元素
    }

    public E next() { // 实现Iterator接口的next方法，返回下一个元素
      if (!hasNext()) { // 调用hasNext方法检查是否还有下一个元素
        throw new NoSuchElementException(); // 如果没有下一个元素，抛出NoSuchElementException异常
      } // 如果还有下一个元素，则继续执行

      return commands.get(commandIndex++); // 从命令列表中获取当前索引位置的元素，然后将索引加1，为下一次遍历做准备
    }

    public void remove() { // 实现Iterator接口的remove方法，用于从底层集合中移除最后返回的元素
      throw new UnsupportedOperationException(); // 抛出UnsupportedOperationException异常，表示不支持移除操作，因为使用的是不可变的命令列表
    } // 方法结束
  } // TimedIterator内部类结束
} // ConcurrentTestTimedCommandGenerator类结束
