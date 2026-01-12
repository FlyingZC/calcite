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
package org.apache.calcite.test.concurrent;

import org.apache.calcite.jdbc.SqlTimeoutException;
import org.apache.calcite.util.Util;

import com.google.common.collect.ImmutableList;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * ConcurrentTestCommandGenerator creates instances of
 * {@link ConcurrentTestCommand} that perform specific actions in a specific
 * order and within the context of a test thread
 * ({@link ConcurrentTestCommandExecutor}).
 * ConcurrentTestCommandGenerator类用于创建ConcurrentTestCommand实例，这些实例在特定测试线程的上下文中按特定顺序执行特定操作
 *
 * <p>Typical actions include preparing a SQL statement for execution, executing
 * the statement and verifying its result set, and closing the statement.
 * 典型操作包括：准备SQL语句以供执行、执行语句并验证其结果集、关闭语句
 *
 * <p>A single ConcurrentTestCommandGenerator creates commands for
 * multiple threads. Each thread is represented by an integer "thread ID".
 * Thread IDs may take on any positive integer value and may be a sparse set
 * (e.g. 1, 2, 5).
 * 单个ConcurrentTestCommandGenerator为多个线程创建命令。每个线程由一个整数"线程ID"表示。线程ID可以是任何正整数值，并且可以是一个稀疏集合（例如1、2、5）
 *
 * <p>When each command is created, it is associated with a thread and given an
 * execution order. Execution order values are positive integers, must be unique
 * within a thread, and may be a sparse set.
 * 当创建每个命令时，它与一个线程关联并被赋予执行顺序。执行顺序值是正整数，在线程内必须唯一，并且可以是一个稀疏集合
 *
 * <p>There are no restrictions on the order of command creation.
 * 对命令创建的顺序没有限制
 */
public class ConcurrentTestCommandGenerator {
  private static final char APOS = '\''; // 单引号字符常量，用于字符串值的定界符
  private static final char COMMA = ','; // 逗号字符常量，用于分隔值
  private static final char LEFT_BRACKET = '{'; // 左大括号字符常量，用于标记行数据的开始
  private static final char RIGHT_BRACKET = '}'; // 右大括号字符常量，用于标记行数据的结束

  protected boolean debug = false; // 调试标志，控制是否输出调试信息
  protected PrintStream debugStream = System.out; // 调试输出流，默认为标准输出
  protected String jdbcURL; // JDBC连接URL，用于连接数据库
  protected Properties jdbcProps; // JDBC连接属性，包含连接数据库所需的配置信息


  /**
   * Maps Integer thread IDs to a TreeMap. The TreeMap values map an Integer
   * execution order to a {@link ConcurrentTestCommand}.
   * 将整数线程ID映射到TreeMap。TreeMap的值将整数执行顺序映射到ConcurrentTestCommand
   */
  private final Map<Integer, TreeMap<Integer, ConcurrentTestCommand>> threadMap; // 线程映射表，存储每个线程的命令序列

  /**
   * Maps Integer thread IDs to thread names.
   * 将整数线程ID映射到线程名称
   */
  private final Map<Integer, String> threadNameMap; // 线程名称映射表，存储每个线程的可读名称

  /**
   * Describes a thread that failed.
   * 描述一个失败的线程
   */
  static class FailedThread { // 静态内部类，用于封装失败线程的信息
    public final String name; // 失败线程的名称
    public final String location; // 失败发生的位置
    public final Throwable failure; // 导致失败的异常对象

    FailedThread(String name, String location, Throwable failure) { // 构造函数，初始化失败线程的信息
      this.name = name; // 设置线程名称
      this.location = location; // 设置失败位置
      this.failure = failure; // 设置失败异常
    }
  }

  /**
   * Collects threads that failed. Cleared when execution starts, valid whe/n
   * execution has ended. Only failed threads appear in the list, so after a
   * successful test the list is empty.
   * 收集失败的线程。执行开始时清除，执行结束后有效。只有失败的线程出现在列表中，因此在成功的测试后列表为空
   */
  private final List<FailedThread> failedThreads; // 失败线程列表，存储所有执行失败的线程信息

  /**
   * Constructs a new ConcurrentTestCommandGenerator.
   * 构造一个新的ConcurrentTestCommandGenerator实例
   */
  public ConcurrentTestCommandGenerator() { // 构造函数，初始化命令生成器的内部数据结构
    threadMap = // 初始化线程映射表，使用TreeMap保证线程ID的有序性
        new TreeMap<Integer,
            TreeMap<Integer, ConcurrentTestCommand>>(); // 创建嵌套的TreeMap结构
    threadNameMap = new TreeMap<Integer, String>(); // 初始化线程名称映射表
    failedThreads = new ArrayList<FailedThread>(); // 初始化失败线程列表
  }

  //~ Methods ----------------------------------------------------------------

  /**
   * Adds a synchronization commands. When a thread reaches a synchronization
   * command it stops and waits for all other threads to reach their
   * synchronization commands. When all threads have reached their
   * synchronization commands, they are all released simultaneously (or as
   * close as one can get with {@link Object#notifyAll()}). Each thread must
   * have exactly the same number of synchronization commands.
   * 添加同步命令。当线程到达同步命令时，它会停止并等待所有其他线程到达它们的同步命令。当所有线程都到达它们的同步命令时，它们会被同时释放（或尽可能接近Object#notifyAll()的效果）。每个线程必须具有完全相同数量的同步命令
   *
   * @param threadId the thread that should execute this command // 应该执行此命令的线程ID
   * @param order    the execution order // 执行顺序
   * @return the newly-added command // 新添加的命令
   */
  public ConcurrentTestCommand addSynchronizationCommand( // 添加同步命令的方法
      int threadId, // 线程ID参数
      int order) { // 执行顺序参数
    return addCommand( // 调用内部方法添加命令
        threadId, // 传入线程ID
        order, // 传入执行顺序
        new SynchronizationCommand()); // 创建新的同步命令实例
  }

  /**
   * Causes the given thread to sleep for the indicated number of
   * milliseconds.  Thread executes {@link java.lang.Thread#sleep(long)}.
   * 使给定线程休眠指定的毫秒数。线程执行Thread#sleep(long)
   *
   * @param threadId the thread that should execute this command // 应该执行此命令的线程ID
   * @param order    the execution order // 执行顺序
   * @param millis   the length of time to sleep in milliseconds (must not be // 休眠时间长度（毫秒），必须不为负数
   *                 negative)
   * @return the newly-added command // 新添加的命令
   */
  public ConcurrentTestCommand addSleepCommand( // 添加休眠命令的方法
      int threadId, // 线程ID参数
      int order, // 执行顺序参数
      long millis) { // 休眠毫秒数参数
    return addCommand( // 调用内部方法添加命令
        threadId, // 传入线程ID
        order, // 传入执行顺序
        new SleepCommand(millis)); // 创建新的休眠命令实例，传入休眠时间
  }

  /**
   * Adds an "explain plan" command.
   * 添加"解释计划"命令
   *
   * @param threadId the thread that should execute this command // 应该执行此命令的线程ID
   * @param order    the execution order // 执行顺序
   * @param sql      the explain plan SQL (e.g. <code>"explain plan for select * // 解释计划SQL语句（例如"explain plan for select * from t"）
   *                 from t"</code>)
   * @return the newly-added command // 新添加的命令
   */
  public ConcurrentTestCommand addExplainCommand( // 添加解释计划命令的方法
      int threadId, // 线程ID参数
      int order, // 执行顺序参数
      String sql) { // SQL语句参数
    requireNonNull(sql, "sql"); // 验证SQL参数不为null

    ConcurrentTestCommand command = new ExplainCommand(sql); // 创建新的解释计划命令实例

    return addCommand(threadId, order, command); // 调用内部方法添加命令
  }

  /**
   * Creates a {@link PreparedStatement} for the given SQL. This command does
   * not execute the SQL, it merely creates a PreparedStatement and stores it
   * in the ConcurrentTestCommandExecutor.
   * 为给定的SQL创建PreparedStatement。此命令不执行SQL，它只是创建PreparedStatement并将其存储在ConcurrentTestCommandExecutor中
   *
   * @param threadId the thread that should execute this command // 应该执行此命令的线程ID
   * @param order    the execution order // 执行顺序
   * @param sql      the SQL to prepare (e.g. <code>"select * from t"</code>) // 要准备的SQL语句（例如"select * from t"）
   * @return the newly-added command // 新添加的命令
   * @see #addFetchAndCompareCommand(int, int, int, String) // 参见addFetchAndCompareCommand方法
   */
  public ConcurrentTestCommand addPrepareCommand( // 添加准备命令的方法
      int threadId, // 线程ID参数
      int order, // 执行顺序参数
      String sql) { // SQL语句参数
    requireNonNull(sql, "sql"); // 验证SQL参数不为null

    ConcurrentTestCommand command = new PrepareCommand(sql); // 创建新的准备命令实例

    return addCommand(threadId, order, command); // 调用内部方法添加命令
  }

  /**
   * Executes a previously
   * {@link #addPrepareCommand(int, int, String) prepared} SQL statement and
   * compares its {@link ResultSet} to the given data.
   * 执行之前通过addPrepareCommand准备的SQL语句，并将其ResultSet与给定的数据进行比较
   *
   * <p><b>Expected data format:</b> <code>{ 'row1, col1 value', 'row1, col2
   * value', ... }, { 'row2, col1 value', 'row2, col2 value', ... },
   * ...</code>
   * <b>期望数据格式：</b> { '行1, 列1值', '行1, 列2值', ... }, { '行2, 列1值', '行2, 列2值', ... }, ...
   *
   * <ul>
   * <li>For string data: enclose value in apostrophes, use doubled apostrophe
   * to include an apostrophe in the value.</li>
   * 对于字符串数据：用单引号括起值，使用双单引号在值中包含单引号
   * <li>For integer or real data: simply use the stringified value (e.g. 123,
   * 12.3, 0.65). No scientific notation is allowed.</li>
   * 对于整数或实数数据：直接使用字符串化的值（例如123、12.3、0.65）。不允许使用科学计数法
   * <li>For null values, use the word <code>null</code> without quotes.</li>
   * 对于null值，使用单词null不加引号
   * </ul>
   * <b>Example:</b> <code>{ 'foo', 10, 3.14, null }</code>
   * <b>示例：</b> { 'foo', 10, 3.14, null }
   *
   * <p><b>Note on timeout:</b> If the previously prepared statement's
   * {@link Statement#setQueryTimeout(int)} method throws an
   * {@link UnsupportedOperationException} it is ignored and no timeout is set.
   * <b>关于超时的说明：</b> 如果之前准备的语句的Statement#setQueryTimeout(int)方法抛出UnsupportedOperationException，它将被忽略且不设置超时
   *
   * @param threadId the thread that should execute this command // 应该执行此命令的线程ID
   * @param order    the execution order // 执行顺序
   * @param timeout  the query timeout, in seconds (see above) // 查询超时时间（秒）（见上文）
   * @param expected the expected results (see above) // 期望的结果（见上文）
   * @return the newly-added command // 新添加的命令
   */
  public ConcurrentTestCommand addFetchAndCompareCommand( // 添加获取和比较命令的方法
      int threadId, // 线程ID参数
      int order, // 执行顺序参数
      int timeout, // 超时时间参数
      String expected) { // 期望结果参数
    ConcurrentTestCommand command = // 创建获取和比较命令实例
        new FetchAndCompareCommand(timeout, expected); // 传入超时时间和期望结果

    return addCommand(threadId, order, command); // 调用内部方法添加命令
  }

  /**
   * Closes a previously {@link #addPrepareCommand(int, int, String) prepared}
   * SQL statement.
   * 关闭之前通过addPrepareCommand准备的SQL语句
   *
   * @param threadId the thread that should execute this command // 应该执行此命令的线程ID
   * @param order    the execution order // 执行顺序
   * @return the newly-added command // 新添加的命令
   */
  public ConcurrentTestCommand addCloseCommand( // 添加关闭命令的方法
      int threadId, // 线程ID参数
      int order) { // 执行顺序参数
    return addCommand( // 调用内部方法添加命令
        threadId, // 传入线程ID
        order, // 传入执行顺序
        new CloseCommand()); // 创建新的关闭命令实例
  }

  /**
   * Executes the given SQL via {@link Statement#executeUpdate(String)}. May
   * be used for update as well as insert statements.
   * 通过Statement#executeUpdate(String)执行给定的SQL。可用于更新和插入语句
   *
   * <p><b>Note on timeout:</b> If the previously prepared statement's
   * {@link Statement#setQueryTimeout(int)} method throws an
   * {@link UnsupportedOperationException} it is ignored and no timeout is set.
   * <b>关于超时的说明：</b> 如果之前准备的语句的Statement#setQueryTimeout(int)方法抛出UnsupportedOperationException，它将被忽略且不设置超时
   *
   * @param threadId the thread that should execute this command // 应该执行此命令的线程ID
   * @param order    the execution order // 执行顺序
   * @param timeout  the query timeout, in seconds (see above) // 查询超时时间（秒）（见上文）
   * @param sql      the insert/update/delete SQL // 插入/更新/删除SQL语句
   * @return the newly-added command // 新添加的命令
   */
  public ConcurrentTestCommand addInsertCommand( // 添加插入命令的方法
      int threadId, // 线程ID参数
      int order, // 执行顺序参数
      int timeout, // 超时时间参数
      String sql) { // SQL语句参数
    ConcurrentTestCommand command = new InsertCommand(timeout, sql); // 创建新的插入命令实例

    return addCommand(threadId, order, command); // 调用内部方法添加命令
  }

  /**
   * Commits pending transaction on the thread's connection.
   * 提交线程连接上的挂起事务
   *
   * @param threadId the thread that should execute this command // 应该执行此命令的线程ID
   * @param order    the execution order // 执行顺序
   * @return the newly-added command // 新添加的命令
   */
  public ConcurrentTestCommand addCommitCommand( // 添加提交命令的方法
      int threadId, // 线程ID参数
      int order) { // 执行顺序参数
    return addCommand( // 调用内部方法添加命令
        threadId, // 传入线程ID
        order, // 传入执行顺序
        new CommitCommand()); // 创建新的提交命令实例
  }

  /**
   * Rolls back pending transaction on the thread's connection.
   * 回滚线程连接上的挂起事务
   *
   * @param threadId the thread that should execute this command // 应该执行此命令的线程ID
   * @param order    the execution order // 执行顺序
   * @return the newly-added command // 新添加的命令
   */
  public ConcurrentTestCommand addRollbackCommand( // 添加回滚命令的方法
      int threadId, // 线程ID参数
      int order) { // 执行顺序参数
    return addCommand( // 调用内部方法添加命令
        threadId, // 传入线程ID
        order, // 传入执行命令
        new RollbackCommand()); // 创建新的回滚命令实例
  }

  /**
   * Executes a DDL statement immediately. Assumes the statement returns no
   * information.
   * 立即执行DDL语句。假设语句不返回任何信息
   *
   * @return the newly-added command // 新添加的命令
   */
  public ConcurrentTestCommand addDdlCommand( // 添加DDL命令的方法
      int threadId, // 线程ID参数
      int order, // 执行顺序参数
      String ddl) { // DDL语句参数
    return addCommand( // 调用内部方法添加命令
        threadId, // 传入线程ID
        order, // 传入执行顺序
        new DdlCommand(ddl)); // 创建新的DDL命令实例
  }

  /**
   * Handles adding a command to {@link #threadMap}.
   * 处理将命令添加到threadMap
   *
   * @return the newly-added command // 新添加的命令
   */
  protected ConcurrentTestCommand addCommand( // 添加命令到线程映射表的内部方法
      int threadId, // 线程ID参数
      int order, // 执行顺序参数
      ConcurrentTestCommand command) { // 要添加的命令对象
    assert threadId > 0; // 断言线程ID为正数
    assert order > 0; // 断言执行顺序为正数

    TreeMap<Integer, ConcurrentTestCommand> commandMap = // 获取或创建指定线程的命令映射表
        threadMap.computeIfAbsent(threadId, k -> new TreeMap<>()); // 如果线程ID不存在则创建新的TreeMap

    // check for duplicate order numbers // 检查重复的顺序号
    assert !commandMap.containsKey(order); // 断言该顺序号不存在

    commandMap.put(order, command); // 将命令按顺序号存入映射表
    return command; // 返回添加的命令
  }

  /**
   * Configures a human-readable name for a given thread identifier. Does not
   * imply that the thread will be created -- that only happens if there are
   * commands added to the thread.
   * 为给定的线程标识符配置人类可读的名称。这并不意味着线程将被创建——只有当有命令添加到线程时才会创建
   */
  public void setThreadName(int threadId, String name) { // 设置线程名称的方法
    threadNameMap.put(threadId, name); // 将线程ID和名称存入映射表
  }

  protected void setDebug(boolean enabled) { // 设置调试模式的方法（简单版本）
    debug = enabled; // 设置调试标志
  }

  protected void setDebug( // 设置调试模式的方法（完整版本）
      boolean enabled, // 调试标志参数
      PrintStream alternatePrintStream) { // 自定义输出流参数
    debug = enabled; // 设置调试标志
    debugStream = alternatePrintStream; // 设置调试输出流
  }


  /**
   * Sets the jdbc data source for executing the command threads.
   * 设置用于执行命令线程的JDBC数据源
   */
  public void setDataSource(String jdbcURL, Properties jdbcProps) { // 设置数据源的方法
    this.jdbcURL = jdbcURL; // 设置JDBC连接URL
    this.jdbcProps = jdbcProps; // 设置JDBC连接属性
  }


  /**
   * Creates a {@link ConcurrentTestCommandExecutor} object for each define
   * thread, and then runs them all.
   * 为每个定义的线程创建ConcurrentTestCommandExecutor对象，然后运行它们
   *
   * @throws Exception if no connection found or if a thread operation is // 如果没有找到连接或线程操作被中断则抛出异常
   *                   interrupted
   */
  public void execute() throws Exception { // 执行所有测试命令的公开方法
    ConcurrentTestCommandExecutor[] threads = innerExecute(); // 调用内部执行方法获取线程数组
    postExecute(threads); // 执行后处理
  }

  protected ConcurrentTestCommandExecutor[] innerExecute() throws Exception { // 内部执行方法，创建并运行所有线程
    failedThreads.clear(); // 清除之前的失败线程列表
    Set<Integer> threadIds = getThreadIds(); // 获取所有线程ID
    ConcurrentTestCommandExecutor.Sync sync = // 创建同步对象，用于线程间同步
        new ConcurrentTestCommandExecutor.Sync(threadIds.size()); // 传入线程数量

    // initialize command executors // 初始化命令执行器
    ConcurrentTestCommandExecutor[] threads = // 创建线程执行器数组
        new ConcurrentTestCommandExecutor[threadIds.size()]; // 根据线程数量初始化数组

    int threadIndex = 0; // 线程索引计数器
    for (int threadId : threadIds) { // 遍历所有线程ID
      Iterable<ConcurrentTestCommand> commands = getCommandIterable(threadId); // 获取该线程的命令序列

      if (debug) { // 如果启用调试模式
        debugStream.println("Thread ID: " + threadId + " (" // 输出线程ID
            + getThreadName(threadId) // 输出线程名称
            + ")"); // 输出右括号
        printCommands(debugStream, threadId); // 打印该线程的所有命令
      }

      threads[threadIndex++] = // 创建并存储线程执行器
          new ConcurrentTestCommandExecutor( // 创建新的命令执行器实例
              threadId, getThreadName(threadId), // 传入线程ID和名称
              this.jdbcURL, this.jdbcProps, // 传入JDBC连接信息
              commands, // 传入命令序列
              sync, // 传入同步对象
              this.debug ? this.debugStream : null); // 传入调试输出流（如果启用调试）
    }

    // start all the threads // 启动所有线程
    for (ConcurrentTestCommandExecutor thread : threads) { // 遍历所有线程执行器
      thread.start(); // 启动线程
    }

    // wait for all threads to finish // 等待所有线程完成
    for (ConcurrentTestCommandExecutor thread : threads) { // 遍历所有线程执行器
      thread.join(); // 等待线程结束
    }
    return threads; // 返回线程数组
  }

  protected void postExecute(ConcurrentTestCommandExecutor[] threads) { // 执行后处理方法
    // check for failures // 检查失败情况
    if (requiresCustomErrorHandling()) { // 如果需要自定义错误处理
      for (ConcurrentTestCommandExecutor executor : threads) { // 遍历所有执行器
        if (executor.getFailureCause() != null) { // 如果执行器有失败原因
          customErrorHandler(executor); // 调用自定义错误处理器
        }
      }
    } else { // 否则使用默认错误处理
      for (ConcurrentTestCommandExecutor thread : threads) { // 遍历所有线程执行器
        Throwable cause = thread.getFailureCause(); // 获取失败原因
        if (cause != null) { // 如果有失败原因
          failedThreads.add( // 添加到失败线程列表
              new FailedThread( // 创建失败线程对象
                  thread.getName(), // 传入线程名称
                  thread.getFailureLocation(), // 传入失败位置
                  cause)); // 传入失败异常
        }
      }
    }
  }


  /**
   * Returns whether any test thread failed. Valid after {@link #execute} has
   * returned.
   * 返回是否有任何测试线程失败。在execute返回后有效
   */
  public boolean failed() { // 检查是否有线程失败的方法
    return !failedThreads.isEmpty(); // 返回失败线程列表是否非空
  }

  /** Returns the list of failed threads (unmodifiable). */ // 返回失败线程列表（不可修改）
  public List<FailedThread> getFailedThreads() { // 获取失败线程列表的方法
    return ImmutableList.copyOf(failedThreads); // 返回失败线程列表的不可修改副本
  }

  /**
   * Insures that the number of commands is the same for each thread, fills
   * missing order value with null commands, and interleaves a synchronization
   * command before each actual command. These steps are required for
   * synchronized execution in FarragoConcurrencyTestCase.
   * 确保每个线程的命令数量相同，用null命令填充缺失的顺序值，并在每个实际命令之前插入同步命令。这些步骤是FarragoConcurrencyTestCase中同步执行所必需的
   */
  public void synchronizeCommandSets() { // 同步命令集的方法
    int maxCommands = 0; // 最大命令数计数器
    for (TreeMap<Integer, ConcurrentTestCommand> map : threadMap.values()) { // 遍历所有线程的命令映射表
      // Fill in missing slots with null (no-op) commands. // 用null（无操作）命令填充缺失的槽位
      for (int j = 1; j < map.lastKey(); j++) { // 遍历从1到最后一个键的所有位置
        if (!map.containsKey(j)) { // 如果该位置不存在命令
          map.put(j, null); // 插入null命令
        }
      }

      maxCommands = // 更新最大命令数
          Math.max( // 使用Math.max取较大值
              maxCommands, // 当前最大命令数
              map.size()); // 当前线程的命令数
    }

    // Make sure all threads have the same number of commands. // 确保所有线程具有相同数量的命令
    for (TreeMap<Integer, ConcurrentTestCommand> map : threadMap.values()) { // 遍历所有线程的命令映射表
      if (map.size() < maxCommands) { // 如果当前线程的命令数小于最大命令数
        for (int j = map.size() + 1; j <= maxCommands; j++) { // 遍历缺失的位置
          map.put(j, null); // 插入null命令
        }
      }
    }

    // Interleave synchronization commands before each command. // 在每个命令之前插入同步命令
    for (Map.Entry<Integer, TreeMap<Integer, ConcurrentTestCommand>> entry // 遍历线程映射表的条目
        : threadMap.entrySet()) { // 获取所有线程的条目
      TreeMap<Integer, ConcurrentTestCommand> commands = entry.getValue(); // 获取当前线程的命令映射表

      TreeMap<Integer, ConcurrentTestCommand> synchronizedCommands = // 创建新的同步命令映射表
          new TreeMap<Integer, ConcurrentTestCommand>(); // 初始化TreeMap

      for (Map.Entry<Integer, ConcurrentTestCommand> commandEntry // 遍历当前线程的所有命令
          : commands.entrySet()) { // 获取命令条目集合
        int orderKey = commandEntry.getKey(); // 获取命令的顺序键
        ConcurrentTestCommand command = commandEntry.getValue(); // 获取命令对象

        synchronizedCommands.put((orderKey * 2) - 1, // 在每个实际命令之前插入同步命令
            new AutoSynchronizationCommand()); // 创建自动同步命令
        synchronizedCommands.put(orderKey * 2, command); // 插入实际命令
      }

      entry.setValue(synchronizedCommands); // 用同步后的命令集替换原命令集
    }
  }

  /**
   * Validates that all threads have the same number of
   * SynchronizationCommands (otherwise a deadlock is guaranteed).
   * 验证所有线程具有相同数量的SynchronizationCommands（否则保证死锁）
   *
   * @return true when valid, false when invalid. // 有效时返回true，无效时返回false
   */
  public boolean hasValidSynchronization() { // 验证同步是否有效的方法
    int numSyncs = -1; // 同步命令数量计数器，初始为-1表示未设置
    for (Map.Entry<Integer, TreeMap<Integer, ConcurrentTestCommand>> entry // 遍历所有线程的命令映射表
        : threadMap.entrySet()) { // 获取线程映射表的所有条目
      TreeMap<Integer, ConcurrentTestCommand> commands = entry.getValue(); // 获取当前线程的命令映射表

      int numSyncsThisThread = 0; // 当前线程的同步命令计数器
      for (ConcurrentTestCommand concurrentTestCommand : commands.values()) { // 遍历当前线程的所有命令
        if (concurrentTestCommand instanceof SynchronizationCommand) { // 如果是同步命令
          numSyncsThisThread++; // 增加同步命令计数
        }
      }
      if (numSyncs < 0) { // 如果是第一个线程
        numSyncs = numSyncsThisThread; // 设置基准同步命令数
      }
      if (numSyncs != numSyncsThisThread) { // 如果当前线程的同步命令数与基准不同
        return false; // 返回无效
      }
    }
    return true; // 返回有效
  }

  /**
   * Returns a set of thread IDs.
   * 返回线程ID集合
   */
  protected Set<Integer> getThreadIds() { // 获取所有线程ID的方法
    return threadMap.keySet(); // 返回线程映射表的键集合
  }

  /**
   * Retrieves the name of a given thread. If no thread names were configured,
   * returns the concatenation of "#" and the thread's numeric identifier.
   * 检索给定线程的名称。如果未配置线程名称，则返回"#"和线程数字标识符的连接
   *
   * @return human-readable thread name // 人类可读的线程名称
   */
  protected String getThreadName(Integer threadId) { // 获取线程名称的方法
    if (threadNameMap.containsKey(threadId)) { // 如果线程ID在名称映射表中存在
      return threadNameMap.get(threadId); // 返回配置的线程名称
    } else { // 否则
      return "#" + threadId; // 返回默认的线程名称格式
    }
  }

  /**
   * Indicates whether commands generated by this generator require special
   * handling. Default implement returns false.
   * 指示此生成器生成的命令是否需要特殊处理。默认实现返回false
   */
  boolean requiresCustomErrorHandling() { // 判断是否需要自定义错误处理的方法
    return false; // 默认返回false
  }

  /**
   * Custom error handling occurs here if
   * {@link #requiresCustomErrorHandling()} returns true. Default implementation
   * does nothing.
   * 如果requiresCustomErrorHandling()返回true，则在此处进行自定义错误处理。默认实现不执行任何操作
   */
  void customErrorHandler( // 自定义错误处理方法
      ConcurrentTestCommandExecutor executor) { // 执行器参数
  }

  /**
   * Returns a {@link Collection} of {@link ConcurrentTestCommand}
   * objects for the given thread ID.
   * 返回给定线程ID的ConcurrentTestCommand对象集合
   */
  Collection<ConcurrentTestCommand> getCommands(int threadId) { // 获取指定线程的所有命令
    assert threadMap.containsKey(threadId); // 断言线程ID存在

    return threadMap.get(threadId).values(); // 返回该线程的所有命令
  }

  /**
   * Returns an {@link Iterator} of {@link ConcurrentTestCommand}
   * objects for the given thread ID.
   * 返回给定线程ID的ConcurrentTestCommand对象的迭代器
   *
   * @param threadId Thread id // 线程ID
   */
  Iterable<ConcurrentTestCommand> getCommandIterable(int threadId) { // 获取指定线程命令的可迭代对象
    return getCommands(threadId); // 返回命令集合
  }

  /**
   * Prints a description of the commands to be executed for a given thread.
   * 打印给定线程要执行的命令的描述
   */
  void printCommands( // 打印命令的方法
      PrintStream out, // 输出流参数
      Integer threadId) { // 线程ID参数
    int stepNumber = 1; // 步骤号计数器
    for (ConcurrentTestCommand command : getCommandIterable(threadId)) { // 遍历该线程的所有命令
      out.println("\tStep " + stepNumber++ // 输出步骤号
          + ": " + command.getClass().getName()); // 输出命令的类名
    }
  }

  //~ Inner Classes ----------------------------------------------------------

  /** Abstract base to handle {@link SQLException}s. */ // 处理SQLException的抽象基类
  protected abstract static class AbstractCommand // 抽象命令基类
      implements ConcurrentTestCommand { // 实现ConcurrentTestCommand接口
    private boolean shouldFail = false; // 标记是否应该失败的标志
    private @Nullable String failComment = null; // 描述预期错误的注释
    private @Nullable Pattern failPattern = null; // 预期错误消息的正则表达式模式
    private boolean failureExpected = false; // 标记是否预期失败，无模式

    // implement ConcurrentTestCommand // 实现ConcurrentTestCommand接口
    public ConcurrentTestCommand markToFail( // 标记命令预期失败的方法
        String comment, // 失败注释参数
        String pattern) { // 错误模式参数
      shouldFail = true; // 设置应该失败标志
      failComment = comment; // 设置失败注释
      failPattern = Pattern.compile(pattern); // 编译错误模式
      return this; // 返回当前命令对象
    }

    public boolean isFailureExpected() { // 判断是否预期失败的方法
      return failureExpected; // 返回预期失败标志
    }

    public ConcurrentTestCommand markToFail() { // 标记命令预期失败的简单方法
      this.failureExpected = true; // 设置预期失败标志
      return this; // 返回当前命令对象
    }

    // subclasses define this to execute themselves // 子类定义此方法以执行自身
    protected abstract void doExecute( // 抽象执行方法，由子类实现
        ConcurrentTestCommandExecutor exec) throws Exception; // 可能抛出异常

    // implement ConcurrentTestCommand // 实现ConcurrentTestCommand接口
    public void execute(ConcurrentTestCommandExecutor exec) throws Exception { // 执行命令的方法
      try { // 尝试执行命令
        doExecute(exec); // 调用子类实现的执行方法
        if (shouldFail) { // 如果应该失败但没有失败
          throw new ConcurrentTestCommand.ShouldHaveFailedException( // 抛出应该失败异常
              failComment); // 传入失败注释
        }
      } catch (SQLException err) { // 捕获SQL异常
        if (!shouldFail) { // 如果不应该失败
          throw err; // 重新抛出异常
        }
        boolean matches = false; // 匹配标志
        if (failPattern == null) { // 如果没有设置失败模式
          matches = true; // 默认匹配
        } else { // 否则检查模式匹配
          for (SQLException err2 = err; err2 != null; // 遍历异常链
               err2 = err2.getNextException()) { // 获取下一个异常
            String msg = err2.getMessage(); // 获取异常消息
            if (msg != null) { // 如果消息不为null
              matches = failPattern.matcher(msg).find(); // 检查消息是否匹配模式
            }
            if (matches) { // 如果匹配
              break; // 退出循环
            }
          }
        }
        if (!matches) { // 如果不匹配
          // an unexpected error // 意外的错误
          throw err; // 重新抛出异常
        } else { // 否则
          // else swallow it // 吞掉异常
          Util.swallow(err, null); // 使用Util工具类吞掉异常
        }
      }
    }
  }

  /**
   * SynchronizationCommand causes the execution thread to wait for all other
   * threads in the test before continuing.
   * SynchronizationCommand使执行线程在继续之前等待测试中的所有其他线程
   */
  static class SynchronizationCommand extends AbstractCommand { // 同步命令类
    private SynchronizationCommand() { // 私有构造函数
    }

    protected void doExecute(ConcurrentTestCommandExecutor executor) // 执行同步命令的方法
        throws Exception { // 可能抛出异常
      executor.getSynchronizer().waitForOthers(); // 调用同步器等待其他线程
    }
  }

  /**
   * AutoSynchronizationCommand is idential to SynchronizationCommand, except
   * that it is generated automatically by the test harness and is not counted
   * when displaying the step number in which an error occurred.
   * AutoSynchronizationCommand与SynchronizationCommand相同，除了它是由测试工具自动生成的，并且在显示发生错误的步骤号时不被计数
   */
  static class AutoSynchronizationCommand extends SynchronizationCommand { // 自动同步命令类
    private AutoSynchronizationCommand() { // 私有构造函数
    }
  }

  /**
   * SleepCommand causes the execution thread to wait for all other threads in
   * the test before continuing.
   * SleepCommand使执行线程在继续之前等待测试中的所有其他线程
   */
  private static class SleepCommand extends AbstractCommand { // 休眠命令类
    private final long millis; // 休眠时间（毫秒）

    private SleepCommand(long millis) { // 私有构造函数
      this.millis = millis; // 设置休眠时间
    }

    protected void doExecute(ConcurrentTestCommandExecutor executor) // 执行休眠命令的方法
        throws Exception { // 可能抛出异常
      Thread.sleep(millis); // 使当前线程休眠指定毫秒数
    }
  }

  /**
   * ExplainCommand executes explain plan commands. Automatically closes the
   * {@link Statement} before returning from
   * {@link #execute(ConcurrentTestCommandExecutor)}.
   * ExplainCommand执行解释计划命令。在从execute返回之前自动关闭Statement
   */
  private static class ExplainCommand extends AbstractCommand { // 解释计划命令类
    private final String sql; // SQL语句

    private ExplainCommand(String sql) { // 私有构造函数
      this.sql = sql; // 设置SQL语句
    }

    protected void doExecute(ConcurrentTestCommandExecutor executor) // 执行解释计划命令的方法
        throws SQLException { // 可能抛出SQL异常
      Statement stmt = executor.getConnection().createStatement(); // 创建Statement对象

      try { // 尝试执行
        ResultSet rset = stmt.executeQuery(sql); // 执行查询获取结果集

        try { // 尝试处理结果集
          int rowCount = 0; // 行数计数器
          while (rset.next()) { // 遍历结果集
            // REVIEW: SZ 6/17/2004: Should we attempt to // 评审：是否应该验证解释计划的结果？
            // validate the results of the explain plan? // 验证解释计划的结果？
            rowCount++; // 增加行数计数
          }

          assert rowCount > 0; // 断言至少有一行结果
        } finally { // 无论是否异常都执行
          rset.close(); // 关闭结果集
        }
      } finally { // 无论是否异常都执行
        stmt.close(); // 关闭Statement
      }
    }
  }

  /**
   * PrepareCommand creates a {@link PreparedStatement}. Stores the prepared
   * statement in the ConcurrentTestCommandExecutor.
   * PrepareCommand创建PreparedStatement。将准备好的语句存储在ConcurrentTestCommandExecutor中
   */
  private static class PrepareCommand extends AbstractCommand { // 准备命令类
    private final String sql; // SQL语句

    private PrepareCommand(String sql) { // 私有构造函数
      this.sql = sql; // 设置SQL语句
    }

    protected void doExecute(ConcurrentTestCommandExecutor executor) // 执行准备命令的方法
        throws SQLException { // 可能抛出SQL异常
      PreparedStatement stmt = // 创建PreparedStatement对象
          executor.getConnection().prepareStatement(sql); // 使用连接准备SQL语句

      executor.setStatement(stmt); // 将PreparedStatement存储到执行器中
    }
  }

  /**
   * CloseCommand closes a previously prepared statement. If no statement is
   * stored in the ConcurrentTestCommandExecutor, it does nothing.
   * CloseCommand关闭之前准备的语句。如果ConcurrentTestCommandExecutor中没有存储语句，它不执行任何操作
   */
  private static class CloseCommand extends AbstractCommand { // 关闭命令类
    protected void doExecute(ConcurrentTestCommandExecutor executor) // 执行关闭命令的方法
        throws SQLException { // 可能抛出SQL异常
      Statement stmt = executor.getStatement(); // 获取存储的Statement

      if (stmt != null) { // 如果Statement不为null
        stmt.close(); // 关闭Statement
      }

      executor.clearStatement(); // 清除执行器中的Statement引用
    }
  }

  /** Command that executes statements with a given timeout. */ // 执行带有给定超时的语句的命令
  private abstract static class CommandWithTimeout extends AbstractCommand { // 带超时的命令基类
    private final int timeout; // 超时时间（秒）

    private CommandWithTimeout(int timeout) { // 私有构造函数
      this.timeout = timeout; // 设置超时时间
    }

    protected boolean setTimeout(Statement stmt) throws SQLException { // 设置超时的方法
      assert timeout >= 0; // 断言超时时间非负

      if (timeout > 0) { // 如果超时时间大于0
        stmt.setQueryTimeout(timeout); // 设置查询超时
        return true; // 返回成功设置
      }

      return false; // 返回未设置
    }
  }

  /**
   * FetchAndCompareCommand executes a previously prepared statement stored in
   * the ConcurrentTestCommandExecutor and then validates the returned
   * rows against expected data.
   * FetchAndCompareCommand执行存储在ConcurrentTestCommandExecutor中的之前准备的语句，然后验证返回的行是否与期望数据匹配
   */
  private static class FetchAndCompareCommand extends CommandWithTimeout { // 获取和比较命令类
    private List<List<Object>> expected; // 期望的数据行列表
    private List<List<Object>> result; // 实际的结果数据行列表

    private FetchAndCompareCommand( // 私有构造函数
        int timeout, // 超时时间参数
        String expected) { // 期望结果参数
      super(timeout); // 调用父类构造函数

      parseExpected(expected.trim()); // 解析期望结果字符串
    }

    protected void doExecute(ConcurrentTestCommandExecutor executor) // 执行获取和比较命令的方法
        throws SQLException { // 可能抛出SQL异常
      PreparedStatement stmt = // 获取存储的PreparedStatement
          (PreparedStatement) executor.getStatement(); // 强制类型转换

      boolean timeoutSet = setTimeout(stmt); // 设置查询超时

      ResultSet rset = stmt.executeQuery(); // 执行查询获取结果集

      List<List<Object>> rows = new ArrayList<List<Object>>(); // 创建结果行列表
      try { // 尝试处理结果集
        int rsetColumnCount = rset.getMetaData().getColumnCount(); // 获取结果集的列数

        while (rset.next()) { // 遍历结果集的每一行
          List<Object> row = new ArrayList<Object>(); // 创建行数据列表

          for (int i = 1; i <= rsetColumnCount; i++) { // 遍历每一列
            Object value = rset.getObject(i); // 获取列值对象
            if (rset.wasNull()) { // 如果值为null
              value = null; // 设置为null
            }

            row.add(value); // 将值添加到行列表
          }

          rows.add(row); // 将行添加到结果列表
        }
      } catch (SqlTimeoutException e) { // 捕获SQL超时异常
        if (!timeoutSet) { // 如果没有设置超时
          throw e; // 重新抛出异常
        }
        Util.swallow(e, null); // 否则吞掉异常
      } finally { // 无论是否异常都执行
        rset.close(); // 关闭结果集
      }

      result = rows; // 保存结果数据

      testValues(); // 验证结果值
    }

    private static final int STATE_ROW_START = 0; // 状态：行开始
    private static final int STATE_VALUE_START = 1; // 状态：值开始
    private static final int STATE_STRING_VALUE = 2; // 状态：字符串值
    private static final int STATE_OTHER_VALUE = 3; // 状态：其他值（数值、null等）
    private static final int STATE_VALUE_END = 4; // 状态：值结束

    /**
     * Parses expected values. See
     * {@link ConcurrentTestCommandGenerator#addFetchAndCompareCommand(int, int, int, String)}
     * for details on format of <code>expected</code>.
     * 解析期望值。关于expected格式的详细信息，请参见addFetchAndCompareCommand方法
     *
     * @throws IllegalStateException if there are formatting errors in // 如果expected中有格式错误则抛出异常
     *                               <code>expected</code>
     */
    private void parseExpected(String expected) { // 解析期望结果字符串的方法
      List<List<Object>> rows = new ArrayList<List<Object>>(); // 创建行列表
      int state = STATE_ROW_START; // 初始状态为行开始
      List<Object> row = null; // 当前行列表
      StringBuilder value = new StringBuilder(); // 值构建器

      for (int i = 0; i < expected.length(); i++) { // 遍历字符串的每个字符
        char ch = expected.charAt(i); // 获取当前字符
        char nextCh = // 获取下一个字符（如果存在）
            ((i + 1) < expected.length()) ? expected.charAt(i + 1) : 0; // 否则为0
        switch (state) { // 根据当前状态处理
        case STATE_ROW_START: // 状态：行开始
          if (ch == LEFT_BRACKET) { // 如果遇到左大括号
            row = new ArrayList<Object>(); // 创建新的行列表
            state = STATE_VALUE_START; // 切换到值开始状态
          }
          break;
        case STATE_VALUE_START: // 状态：值开始
          if (!Character.isWhitespace(ch)) { // 如果不是空白字符
            value.setLength(0); // 清空值构建器
            if (ch == APOS) { // 如果是单引号
              // a string value // 字符串值
              state = STATE_STRING_VALUE; // 切换到字符串值状态
            } else { // 否则
              // some other kind of value // 其他类型的值
              value.append(ch); // 添加字符到值构建器
              state = STATE_OTHER_VALUE; // 切换到其他值状态
            }
          }
          break;
        case STATE_STRING_VALUE: // 状态：字符串值
          if (ch == APOS) { // 如果遇到单引号
            if (nextCh == APOS) { // 如果下一个字符也是单引号（转义）
              value.append(APOS); // 添加单引号到值构建器
              i++; // 跳过下一个字符
            } else { // 否则字符串结束
              row.add(value.toString()); // 将字符串值添加到行
              state = STATE_VALUE_END; // 切换到值结束状态
            }
          } else { // 否则
            value.append(ch); // 添加字符到值构建器
          }
          break;
        case STATE_OTHER_VALUE: // 状态：其他值（数值、null）
          if ((ch != COMMA) && (ch != RIGHT_BRACKET)) { // 如果不是逗号或右大括号
            value.append(ch); // 添加字符到值构建器
            break;
          }
          String stringValue = value.toString().trim(); // 获取字符串值并去除空白
          if (stringValue.matches("^-?[0-9]+$")) { // 如果匹配整数格式
            row.add(new BigInteger(stringValue)); // 添加BigInteger值
          } else if (stringValue.matches("^-?[0-9]*\\.[0-9]+$")) { // 如果匹配小数格式
            row.add(new BigDecimal(stringValue)); // 添加BigDecimal值
          } else if (stringValue.equals("true")) { // 如果是true
            row.add(Boolean.TRUE); // 添加Boolean.TRUE
          } else if (stringValue.equals("false")) { // 如果是false
            row.add(Boolean.FALSE); // 添加Boolean.FALSE
          } else if (stringValue.equals("null")) { // 如果是null
            row.add(null); // 添加null值
          } else { // 否则
            throw new IllegalStateException( // 抛出非法状态异常
                "unknown value type '" // 未知值类型
                + stringValue + "' for FetchAndCompare command"); // 错误消息
          }

          state = STATE_VALUE_END; // 切换到值结束状态

          // fall through // 贯穿到下一个case
        case STATE_VALUE_END: // 状态：值结束
          if (ch == COMMA) { // 如果遇到逗号
            state = STATE_VALUE_START; // 切换到值开始状态
          } else if (ch == RIGHT_BRACKET) { // 如果遇到右大括号
            // end of row // 行结束
            rows.add(row); // 将行添加到行列表
            state = STATE_ROW_START; // 切换到行开始状态
          } else if (!Character.isWhitespace(ch)) { // 如果不是空白字符
            throw new IllegalStateException( // 抛出非法状态异常

                "unexpected character '" + ch + "' at position " // 意外的字符
                + i + " of expected values"); // 在期望值的位置i
          }
          break;
        }
      }

      if (state != STATE_ROW_START) { // 如果最终状态不是行开始
        throw new IllegalStateException( // 抛出非法状态异常
            "unterminated data in expected values"); // 期望值中的未终止数据
      }

      if (rows.size() > 1) { // 如果有多行
        Iterator rowIter = rows.iterator(); // 获取行迭代器

        int expectedNumColumns = ((ArrayList) rowIter.next()).size(); // 获取第一行的列数

        while (rowIter.hasNext()) { // 遍历剩余行
          int numColumns = ((ArrayList) rowIter.next()).size(); // 获取当前行的列数

          if (numColumns != expectedNumColumns) { // 如果列数不一致
            throw new IllegalStateException( // 抛出非法状态异常
                "all rows in expected values must have the same number of columns"); // 期望值中的所有行必须具有相同的列数
          }
        }
      }

      this.expected = rows; // 保存解析后的期望值
    }

    /**
     * Validates expected data against retrieved data.
     * 根据检索到的数据验证期望数据
     */
    private void testValues() { // 验证值的方法
      if (expected.size() != result.size()) { // 如果期望行数与结果行数不一致
        dumpData( // 输出数据
            "Expected " + expected.size() + " rows, got " // 期望X行，得到Y行
            + result.size()); // 实际行数
      }

      Iterator<List<Object>> expectedIter = expected.iterator(); // 获取期望值迭代器
      Iterator<List<Object>> resultIter = result.iterator(); // 获取结果值迭代器

      int rowNum = 1; // 行号计数器
      while (expectedIter.hasNext() && resultIter.hasNext()) { // 遍历期望值和结果值
        List<Object> expectedRow = expectedIter.next(); // 获取期望行
        List<Object> resultRow = resultIter.next(); // 获取结果行

        testValues(expectedRow, resultRow, rowNum++); // 验证行数据
      }
    }

    /**
     * Validates {@link ResultSet} against expected data.
     * 根据期望数据验证ResultSet
     */
    private void testValues( // 验证行值的方法
        List<Object> expectedRow, // 期望行
        List<Object> resultRow, // 结果行
        int rowNum) { // 行号
      if (expectedRow.size() != resultRow.size()) { // 如果期望列数与结果列数不一致
        dumpData( // 输出数据
            "Row " + rowNum + " Expected " + expected.size() // 行X期望Y列
            + " columns, got " + result.size()); // 实际列数
      }

      Iterator expectedIter = expectedRow.iterator(); // 获取期望值迭代器
      Iterator resultIter = resultRow.iterator(); // 获取结果值迭代器

      int colNum = 1; // 列号计数器
      while (expectedIter.hasNext() && resultIter.hasNext()) { // 遍历期望值和结果值
        @Nullable Object expectedValue = expectedIter.next(); // 获取期望值
        Object resultValue = resultIter.next(); // 获取结果值

        if ((expectedValue == null) // 如果期望值为null
            || (expectedValue instanceof String) // 或期望值是字符串
            || (expectedValue instanceof Boolean)) { // 或期望值是布尔值
          test(expectedValue, resultValue, rowNum, colNum); // 使用通用测试方法
        } else if (expectedValue instanceof BigInteger) { // 如果期望值是BigInteger
          BigInteger expectedInt = (BigInteger) expectedValue; // 强制类型转换

          if (expectedInt.bitLength() <= 31) { // 如果在int范围内
            test( // 测试int值
                expectedInt.intValue(), // 转换为int
                ((Number) resultValue).intValue(), // 转换结果值为int
                rowNum, // 行号
                colNum); // 列号
          } else if (expectedInt.bitLength() <= 63) { // 如果在long范围内
            test( // 测试long值
                expectedInt.longValue(), // 转换为long
                ((Number) resultValue).longValue(), // 转换结果值为long
                rowNum, // 行号
                colNum); // 列号
          } else { // 否则
            // REVIEW: how do we return very // 评审：我们如何返回非常大的
            // large integer values? // 整数值？
            test(expectedInt, resultValue, rowNum, colNum); // 直接测试BigInteger
          }
        } else if (expectedValue instanceof BigDecimal) { // 如果期望值是BigDecimal
          BigDecimal expectedReal = (BigDecimal) expectedValue; // 强制类型转换

          float asFloat = expectedReal.floatValue(); // 转换为float
          double asDouble = expectedReal.doubleValue(); // 转换为double

          if ((asFloat != Float.POSITIVE_INFINITY) // 如果float不是正无穷
              && (asFloat != Float.NEGATIVE_INFINITY)) { // 且float不是负无穷
            test( // 测试float值
                asFloat, // float值
                ((Number) resultValue).floatValue(), // 转换结果值为float
                rowNum, // 行号
                colNum); // 列号
          } else if ( // 否则如果
              (asDouble != Double.POSITIVE_INFINITY) // double不是正无穷
                  && (asDouble != Double.NEGATIVE_INFINITY)) { // 且double不是负无穷
            test( // 测试double值
                asDouble, // double值
                ((Number) resultValue).doubleValue(), // 转换结果值为double
                rowNum, // 行号
                colNum); // 列号
          } else { // 否则
            // REVIEW: how do we return very large decimal // 评审：我们如何返回非常大的
            // values? // 十进制值？
            test(expectedReal, resultValue, rowNum, colNum); // 直接测试BigDecimal
          }
        } else { // 否则
          throw new IllegalStateException( // 抛出非法状态异常
              "unknown type of expected value: " // 未知类型的期望值
              + expectedValue.getClass().getName()); // 期望值的类名
        }

        colNum++; // 增加列号
      }
    }

    private void test( // 测试对象值的方法
        @Nullable Object expected, // 期望值
        @Nullable Object got, // 实际值
        int rowNum, // 行号
        int colNum) { // 列号
      if ((expected == null) && (got == null)) { // 如果期望值和实际值都为null
        return; // 直接返回（匹配）
      }

      if ((expected == null) || !expected.equals(got)) { // 如果期望值为null或不相等
        reportError( // 报告错误
            String.valueOf(expected), // 期望值的字符串表示
            String.valueOf(got), // 实际值的字符串表示
            rowNum, // 行号
            colNum); // 列号
      }
    }

    private void test( // 测试int值的方法
        int expected, // 期望值
        int got, // 实际值
        int rowNum, // 行号
        int colNum) { // 列号
      if (expected != got) { // 如果不相等
        reportError( // 报告错误
            String.valueOf(expected), // 期望值的字符串表示
            String.valueOf(got), // 实际值的字符串表示
            rowNum, // 行号
            colNum); // 列号
      }
    }

    private void test( // 测试long值的方法
        long expected, // 期望值
        long got, // 实际值
        int rowNum, // 行号
        int colNum) { // 列号
      if (expected != got) { // 如果不相等
        reportError( // 报告错误
            String.valueOf(expected), // 期望值的字符串表示
            String.valueOf(got), // 实际值的字符串表示
            rowNum, // 行号
            colNum); // 列号
      }
    }

    private void test( // 测试float值的方法
        float expected, // 期望值
        float got, // 实际值
        int rowNum, // 行号
        int colNum) { // 列号
      if (expected != got) { // 如果不相等
        reportError( // 报告错误
            String.valueOf(expected), // 期望值的字符串表示
            String.valueOf(got), // 实际值的字符串表示
            rowNum, // 行号
            colNum); // 列号
      }
    }

    private void test( // 测试double值的方法
        double expected, // 期望值
        double got, // 实际值
        int rowNum, // 行号
        int colNum) { // 列号
      if (expected != got) { // 如果不相等
        reportError( // 报告错误
            String.valueOf(expected), // 期望值的字符串表示
            String.valueOf(got), // 实际值的字符串表示
            rowNum, // 行号
            colNum); // 列号
      }
    }

    private void reportError( // 报告错误的方法
        String expected, // 期望值字符串
        String got, // 实际值字符串
        int rowNum, // 行号
        int colNum) { // 列号
      dumpData( // 输出数据
          "Row " + rowNum + ", column " + colNum + ": expected <" // 行X, 列Y: 期望值
          + expected + ">, got <" + got + ">"); // 实际值
    }

    /**
     * Outputs expected and result data in tabular format.
     * 以表格格式输出期望值和结果数据
     */
    private void dumpData(String message) { // 输出数据的方法
      Iterator<List<Object>> expectedIter = expected.iterator(); // 获取期望值迭代器
      Iterator<List<Object>> resultIter = result.iterator(); // 获取结果值迭代器

      StringBuilder fullMessage = new StringBuilder(message); // 创建完整消息构建器

      int rowNum = 1; // 行号计数器
      while (expectedIter.hasNext() || resultIter.hasNext()) { // 遍历期望值和结果值
        StringBuilder expectedOut = new StringBuilder(); // 创建期望值输出构建器
        expectedOut.append("Row ").append(rowNum).append(" exp:"); // 添加行标题

        StringBuilder resultOut = new StringBuilder(); // 创建结果值输出构建器
        resultOut.append("Row ").append(rowNum).append(" got:"); // 添加行标题

        Iterator<Object> expectedRowIter = null; // 期望行迭代器
        if (expectedIter.hasNext()) { // 如果还有期望行
          List<Object> expectedRow = expectedIter.next(); // 获取期望行
          expectedRowIter = expectedRow.iterator(); // 获取行迭代器
        }

        Iterator<Object> resultRowIter = null; // 结果行迭代器
        if (resultIter.hasNext()) { // 如果还有结果行
          List<Object> resultRow = resultIter.next(); // 获取结果行
          resultRowIter = resultRow.iterator(); // 获取行迭代器
        }

        while (((expectedRowIter != null) && expectedRowIter.hasNext()) // 遍历期望行和结果行的所有列
            || ((resultRowIter != null) && resultRowIter.hasNext())) { // 只要还有列
          Object expectedObject = // 获取期望值对象
              expectedRowIter != null ? expectedRowIter.next() : ""; // 如果为null则使用空字符串

          Object resultObject = // 获取结果值对象
              resultRowIter != null ? resultRowIter.next() : ""; // 如果为null则使用空字符串

          String expectedValue; // 期望值字符串
          if (expectedObject == null) { // 如果期望对象为null
            expectedValue = "<null>"; // 使用<null>表示
          } else { // 否则
            expectedValue = expectedObject.toString(); // 转换为字符串
          }

          String resultValue; // 结果值字符串
          if (resultObject == null) { // 如果结果对象为null
            resultValue = "<null>"; // 使用<null>表示
          } else { // 否则
            resultValue = resultObject.toString(); // 转换为字符串
          }

          int width = // 计算列宽
              Math.max( // 取最大值
                  expectedValue.length(), // 期望值长度
                  resultValue.length()); // 结果值长度

          expectedOut.append(" | ").append(expectedValue); // 添加期望值
          for (int i = 0; i < (width - expectedValue.length()); i++) { // 填充空格
            expectedOut.append(' '); // 添加空格
          }

          resultOut.append(" | ").append(resultValue); // 添加结果值
          for (int i = 0; i < (width - resultValue.length()); i++) { // 填充空格
            resultOut.append(' '); // 添加空格
          }
        }

        if ((expectedRowIter == null) && (resultRowIter == null)) { // 如果两个迭代器都为null
          expectedOut.append('|'); // 添加结束符
          resultOut.append('|'); // 添加结束符
        }

        expectedOut.append(" |"); // 添加结束符
        resultOut.append(" |"); // 添加结束符

        fullMessage.append('\n').append(expectedOut) // 添加期望值行
            .append('\n').append(resultOut); // 添加结果值行

        rowNum++; // 增加行号
      }

      throw new RuntimeException(fullMessage.toString()); // 抛出运行时异常包含完整消息
    }
  }

  /**
   * InsertCommand exeutes an insert, update or delete SQL statement. Uses
   * {@link Statement#executeUpdate(String)}.
   * InsertCommand执行插入、更新或删除SQL语句。使用Statement#executeUpdate(String)
   */
  private static class InsertCommand extends CommandWithTimeout { // 插入命令类
    private final String sql; // SQL语句

    private InsertCommand( // 私有构造函数
        int timeout, // 超时时间参数
        String sql) { // SQL语句参数
      super(timeout); // 调用父类构造函数

      this.sql = sql; // 设置SQL语句
    }

    protected void doExecute(ConcurrentTestCommandExecutor executor) // 执行插入命令的方法
        throws SQLException { // 可能抛出SQL异常
      Statement stmt = executor.getConnection().createStatement(); // 创建Statement对象

      setTimeout(stmt); // 设置查询超时

      stmt.executeUpdate(sql); // 执行更新操作
    }
  }

  /**
   * CommitCommand commits pending transactions via
   * {@link Connection#commit()}.
   * CommitCommand通过Connection#commit()提交挂起的事务
   */
  private static class CommitCommand extends AbstractCommand { // 提交命令类
    protected void doExecute(ConcurrentTestCommandExecutor executor) // 执行提交命令的方法
        throws SQLException { // 可能抛出SQL异常
      executor.getConnection().commit(); // 提交事务
    }
  }

  /**
   * RollbackCommand rolls back pending transactions via
   * {@link Connection#rollback()}.
   * RollbackCommand通过Connection#rollback()回滚挂起的事务
   */
  private static class RollbackCommand extends AbstractCommand { // 回滚命令类
    protected void doExecute(ConcurrentTestCommandExecutor executor) // 执行回滚命令的方法
        throws SQLException { // 可能抛出SQL异常
      executor.getConnection().rollback(); // 回滚事务
    }
  }

  /**
   * DdlCommand executes DDL commands. Automatically closes the
   * {@link Statement} before returning from
   * {@link #doExecute(ConcurrentTestCommandExecutor)}.
   * DdlCommand执行DDL命令。在从doExecute返回之前自动关闭Statement
   */
  private static class DdlCommand extends AbstractCommand { // DDL命令类
    private final String sql; // SQL语句

    private DdlCommand(String sql) { // 私有构造函数
      this.sql = sql; // 设置SQL语句
    }

    protected void doExecute(ConcurrentTestCommandExecutor executor) // 执行DDL命令的方法
        throws SQLException { // 可能抛出SQL异常
      Statement stmt = executor.getConnection().createStatement(); // 创建Statement对象

      try { // 尝试执行
        stmt.execute(sql); // 执行SQL语句
      } finally { // 无论是否异常都执行
        stmt.close(); // 关闭Statement
      }
    }
  }
}