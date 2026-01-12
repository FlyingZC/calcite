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
package org.apache.calcite.test.concurrent; // 定义包名，此类位于concurrent测试包中，用于并发测试

import org.apache.calcite.util.Unsafe; // 导入Unsafe工具类，用于低级别的线程同步操作

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值

import java.io.PrintStream; // 导入PrintStream类，用于调试输出
import java.sql.Connection; // 导入JDBC Connection接口，用于数据库连接
import java.sql.DriverManager; // 导入DriverManager类，用于获取数据库连接
import java.sql.Statement; // 导入JDBC Statement接口，用于执行SQL语句
import java.util.Properties; // 导入Properties类，用于存储连接属性

/**
 * ConcurrentTestCommandExecutor is a thread that executes a sequence of
 * {@link ConcurrentTestCommand commands} on a JDBC connection.
 * ConcurrentTestCommandExecutor是一个线程，用于在JDBC连接上执行一系列ConcurrentTestCommand命令
 * 这个类是Calcite并发测试框架的核心组件，负责在独立的线程中执行测试命令序列
 * 它支持多线程并发执行SQL命令，并通过同步机制协调线程间的执行顺序
 * 主要用于测试Calcite在并发场景下的正确性和稳定性
 */
class ConcurrentTestCommandExecutor extends Thread { // 定义ConcurrentTestCommandExecutor类，继承自Thread，表示这是一个可运行的线程
  /**
   * The id for this thread.
   * 线程的唯一标识符，用于区分不同的执行线程
   * 在测试中可以通过threadId来跟踪和识别每个线程的执行情况
   */
  private final Integer threadId; // 线程ID字段，使用final修饰表示初始化后不可更改

  /**
   * JDBC URL to connect with.
   * JDBC连接字符串，用于指定要连接的数据库地址和参数
   * 例如："jdbc:calcite:model=target/test-classes/model.json"
   */
  private final String jdbcURL; // JDBC URL字段，存储数据库连接地址

  /**
   * JDBC Connection properties.
   * JDBC连接属性，包含用户名、密码等连接参数
   * 这些属性会传递给DriverManager.getConnection()方法建立连接
   */
  private final Properties jdbcProps; // JDBC连接属性字段

  /**
   * Command sequence for this thread.
   * 该线程要执行的命令序列，每个命令都是ConcurrentTestCommand的实例
   * 命令序列中可能包含null元素，表示无操作(no-op)
   * 通过Iterable接口支持遍历命令序列
   */
  private final Iterable<ConcurrentTestCommand> commands; // 命令序列字段

  /**
   * Used to synchronize command execution.
   * 用于同步命令执行的对象，确保多个线程在特定点同步执行
   * 通过Sync对象，可以实现线程间的同步点，使各个线程在执行到同步点时等待其他线程
   */
  private final Sync synchronizer; // 同步器字段

  /**
   * JDBC connection for commands.
   * JDBC连接对象，用于执行SQL命令
   * 在线程启动时建立连接，在所有命令执行完毕后关闭
   */
  private Connection connection; // JDBC连接字段

  /**
   * Current JDBC Statement. May be null.
   * 当前的JDBC Statement对象，用于执行SQL语句
   * 可能为null，表示当前没有活动的Statement
   * Statement对象由命令创建和设置，执行完毕后清除
   */
  private @Nullable Statement statement; // Statement字段，可能为null

  /**
   * First exception thrown by the thread.
   * 线程执行过程中抛出的第一个异常
   * 如果为null，表示所有命令都成功执行
   * 一旦发生异常，后续命令将不再执行
   */
  private Throwable error; // 异常字段，存储执行过程中遇到的错误

  /**
   * Location of {@link #error}.
   * 异常发生的位置描述，例如"during step 5"或"during connect"
   * 用于帮助定位问题发生的位置
   */
  private String when; // 异常位置描述字段

  /**
   * Debugging print stream. May be null.
   * 调试输出流，用于输出调试信息
   * 如果为null，则不输出调试信息
   * 可以设置为System.out或文件输出流等
   */
  private final @Nullable PrintStream debugPrintStream; // 调试输出流字段

  /**
   * Command throwing error.
   * 抛出异常的命令对象
   * 可能为null，表示没有命令抛出异常
   * 通过这个字段可以定位具体哪个命令导致了错误
   */
  private @Nullable ConcurrentTestCommand errorCommand; // 出错的命令字段

  /**
   * Constructs a ConcurrentTestCommandExecutor with the given thread
   * ID, JDBC URL, commands and synchronization object.
   * 构造函数：创建一个ConcurrentTestCommandExecutor实例
   * 初始化所有必要的字段，包括线程ID、JDBC连接参数、命令序列和同步器
   *
   * @param threadId         the thread ID
   *                         线程ID，用于标识这个执行线程
   *                         (see {@link ConcurrentTestCommandGenerator})
   *                         参见ConcurrentTestCommandGenerator类了解线程ID的使用方式
   * @param threadName       the thread's name
   *                         线程名称，用于调试和日志输出
   * @param jdbcURL          the JDBC URL to connect to
   *                         JDBC连接URL，指定要连接的数据库
   * @param jdbcProps        JDBC Connection properties (user, password, etc.)
   *                         JDBC连接属性，包含用户名、密码等连接参数
   * @param commands         the sequence of commands to execute -- null
   *                         要执行的命令序列，null元素表示无操作
   *                         elements indicate no-ops
   * @param synchronizer     synchronization object (may not be null);
   *                         同步对象，用于线程间同步，不能为null
   * @param debugPrintStream if non-null a PrintStream to use for debugging
   *                         调试输出流，如果不为null则用于输出调试信息
   *                         output (may help debugging thread synchronization
   *                         有助于调试线程同步问题
   */
  ConcurrentTestCommandExecutor( // 构造函数定义
      int threadId, // 参数：线程ID
      String threadName, // 参数：线程名称
      String jdbcURL, // 参数：JDBC连接URL
      Properties jdbcProps, // 参数：JDBC连接属性
      Iterable<ConcurrentTestCommand> commands, // 参数：命令序列
      Sync synchronizer, // 参数：同步器对象
      @Nullable PrintStream debugPrintStream) { // 参数：调试输出流
    this.threadId = threadId; // 初始化线程ID字段
    this.jdbcURL = jdbcURL; // 初始化JDBC URL字段
    this.jdbcProps = jdbcProps; // 初始化JDBC属性字段
    this.commands = commands; // 初始化命令序列字段
    this.synchronizer = synchronizer; // 初始化同步器字段
    this.debugPrintStream = debugPrintStream; // 初始化调试输出流字段

    this.setName("Command Executor " + threadName); // 设置线程名称，便于调试和日志识别
  }

  //~ Methods ---------------------------------------------------------------- // 方法区域分隔符

  /**
   * Executes the configured commands.
   * 线程的run方法，执行配置的命令序列
   * 这是线程的核心执行逻辑，会依次执行所有命令
   * 执行流程：1.建立JDBC连接 2.执行所有命令 3.关闭连接
   */
  public void run() { // 定义run方法，覆盖Thread类的run方法
    try { // 开始try块，捕获连接过程中的异常
      connection = DriverManager.getConnection(jdbcURL, jdbcProps); // 使用DriverManager获取JDBC连接
      if (connection.getMetaData().supportsTransactions()) { // 检查数据库是否支持事务
        connection.setAutoCommit(false); // 如果支持事务，则关闭自动提交，启用事务模式
      }
    } catch (Throwable t) { // 捕获连接过程中抛出的任何异常
      handleError(t, "during connect", null); // 调用错误处理方法，记录连接阶段发生的错误
    }

    // stepNumber is used to reconstitute the original step
    // numbers passed by the test case author.
    // stepNumber用于重建测试用例作者传入的原始步骤编号
    // 这样可以在错误报告中显示原始的步骤号，便于定位问题
    int stepNumber = 0; // 初始化步骤计数器

    for (ConcurrentTestCommand command : commands) { // 遍历命令序列中的每个命令
      if (!(command // 检查当前命令是否不是自动同步命令
          instanceof
          ConcurrentTestCommandGenerator.AutoSynchronizationCommand)) { // 如果不是自动同步命令
        stepNumber++; // 则增加步骤计数器
      }

      //  if (debugPrintStream != null) { // 如果调试输出流不为null（这段代码被注释掉）
      //      debugPrintStream.println(Thread.currentThread().getName() // 输出当前线程名称
      //                               + ": Step " // 输出步骤标识
      //                               + stepNumber // 输出步骤编号
      //                               + ": " // 输出分隔符
      //                               + System.currentTimeMillis()); // 输出当前时间戳
      //  } // 结束调试输出

      // synchronization commands are always executed, lest we deadlock
      // 同步命令总是被执行，否则可能会导致死锁
      // 这是因为同步命令用于协调线程间的执行，如果跳过会导致其他线程永远等待
      boolean isSync = command // 判断当前命令是否是同步命令
          instanceof ConcurrentTestCommandGenerator.SynchronizationCommand; // 使用instanceof检查命令类型

      if (isSync // 如果是同步命令，或者满足以下条件：连接不为null且命令不为null且没有发生错误
          || ((connection != null) // 检查连接是否有效
          && (command != null) // 检查命令是否不为null
          && (error == null))) { // 检查是否没有发生错误
        try { // 开始try块，捕获命令执行过程中的异常
          command.execute(this); // 执行当前命令，传入this作为执行器
        } catch (Throwable t) { // 捕获命令执行过程中抛出的任何异常
          handleError(t, "during step " + stepNumber, command); // 调用错误处理方法，记录命令执行阶段的错误
        }
      }
    }

    try { // 开始try块，捕获连接关闭过程中的异常
      if (connection != null) { // 检查连接是否不为null
        if (connection.getMetaData().supportsTransactions()) { // 检查数据库是否支持事务
          connection.rollback(); // 如果支持事务，则回滚所有未提交的事务
        }
        connection.close(); // 关闭JDBC连接
      }
    } catch (Throwable t) { // 捕获连接关闭过程中抛出的任何异常
      handleError(t, "during connection close", null); // 调用错误处理方法，记录连接关闭阶段的错误
    }
  }

  /**
   * Handles details of an exception during execution.
   * 处理执行过程中异常的详细信息
   * 将异常信息、发生位置和相关命令存储到实例变量中，供后续查询
   *
   * @param error    the exception that occurred
   *                 发生的异常对象
   * @param when     description of where the error occurred
   *                 错误发生位置的描述字符串
   * @param command  the command being executed when the error occurred
   *                 发生错误时正在执行的命令，可能为null
   */
  private void handleError( // 定义错误处理方法
      Throwable error, // 参数：异常对象
      String when, // 参数：错误位置描述
      @Nullable ConcurrentTestCommand command) { // 参数：出错的命令
    this.error = error; // 将异常对象存储到实例变量中
    this.when = when; // 将错误位置描述存储到实例变量中
    this.errorCommand = command; // 将出错命令存储到实例变量中

    if (debugPrintStream != null) { // 如果调试输出流不为null
      debugPrintStream.println( // 输出错误信息到调试流
          Thread.currentThread().getName() + ": " // 输出当前线程名称
              + when); // 输出错误位置描述
      error.printStackTrace(debugPrintStream); // 将异常堆栈跟踪输出到调试流
    }
  }

  /**
   * Obtains the thread's JDBC connection.
   * 获取线程的JDBC连接对象
   * 返回的连接对象可能为null（如果连接失败）
   *
   * @return the JDBC connection, or null if not yet established
   *         返回JDBC连接对象，如果尚未建立则返回null
   */
  public Connection getConnection() { // 定义获取连接的方法
    return connection; // 返回连接对象
  }

  /**
   * Obtains the thread's current JDBC statement. May return null.
   * 获取线程当前的JDBC Statement对象
   * Statement对象用于执行SQL语句，可能为null
   *
   * @return the current statement, or null if none
   *         返回当前Statement对象，如果没有则返回null
   */
  public @Nullable Statement getStatement() { // 定义获取Statement的方法
    return statement; // 返回Statement对象
  }

  /**
   * Sets the thread's current JDBC statement. To clear the JDBC statement use
   * {@link #clearStatement()}.
   * 设置线程当前的JDBC Statement对象
   * 在设置之前会断言当前没有Statement对象，防止资源泄漏
   * 要清除Statement对象，请使用clearStatement()方法
   *
   * @param stmt the statement to set
   *             要设置的Statement对象
   */
  public void setStatement(Statement stmt) { // 定义设置Statement的方法
    // assert that we don't already have a statement
    // 断言我们还没有Statement对象，防止重复设置导致资源泄漏
    assert statement == null; // 断言当前statement为null

    statement = stmt; // 将参数赋值给statement字段
  }

  /**
   * Clears the thread's current JDBC statement. To set the JDBC statement use
   * {@link #setStatement(Statement)}.
   * 清除线程当前的JDBC Statement对象
   * 通常在Statement执行完毕后调用，释放资源
   * 要设置Statement对象，请使用setStatement(Statement)方法
   */
  public void clearStatement() { // 定义清除Statement的方法
    statement = null; // 将statement字段设置为null
  }

  /**
   * Retrieves the object used to synchronize threads at a point in the list
   * of commands.
   * 获取用于在命令列表中的特定点同步线程的对象
   * 通过Sync对象，多个线程可以在执行到同步点时相互等待
   *
   * @return the synchronization object
   *         返回同步器对象
   */
  public Sync getSynchronizer() { // 定义获取同步器的方法
    return synchronizer; // 返回同步器对象
  }

  /**
   * Checks whether an exception occurred during execution. If this method
   * returns null, the thread's commands all succeeded. If this method returns
   * non-null, see {@link #getFailureLocation()} for details on which command
   * caused the failure.
   * 检查执行过程中是否发生异常
   * 如果此方法返回null，表示线程的所有命令都成功执行
   * 如果返回非null值，请参见getFailureLocation()方法了解哪个命令导致了失败
   *
   * @return the exception that occurred, or null if none
   *         返回发生的异常对象，如果没有异常则返回null
   */
  public Throwable getFailureCause() { // 定义获取失败原因的方法
    return error; // 返回异常对象
  }

  /**
   * Returns location (e.g., command number) for exception returned by
   * {@link #getFailureCause()}.
   * 返回getFailureCause()方法返回的异常的位置信息
   * 例如："during step 5"或"during connect"
   *
   * @return description of where the error occurred
   *         返回错误发生位置的描述
   */
  public String getFailureLocation() { // 定义获取失败位置的方法
    return when; // 返回位置描述字符串
  }

  public ConcurrentTestCommand getFailureCommand() { // 定义获取失败命令的方法
    return errorCommand; // 返回导致错误的命令对象
  }

  public Integer getThreadId() { // 定义获取线程ID的方法
    return threadId; // 返回线程ID
  }

  //~ Inner Classes ---------------------------------------------------------- // 内部类区域分隔符

  /**
   * Synchronization object that allows multiple
   * ConcurrentTestCommandExecutors to execute commands in lock-step.
   * 同步对象，允许多个ConcurrentTestCommandExecutors以锁步方式执行命令
   * 锁步执行意味着所有线程在执行到同步点时会等待其他线程，然后一起继续执行
   * Requires that all ConcurrentTestCommandExecutors have the same
   * 要求所有ConcurrentTestCommandExecutors具有相同数量的命令
   * number of commands.
   * 这样才能保证所有线程都能到达每个同步点
   */
  public static class Sync { // 定义Sync内部类，用于线程同步
    private int numThreads; // 需要同步的线程总数
    private int numWaiting; // 当前正在等待的线程数

    Sync(int numThreads) { // Sync构造函数
      assert numThreads > 0; // 断言线程数大于0
      this.numThreads = numThreads; // 初始化线程总数
      this.numWaiting = 0; // 初始化等待线程数为0
    }

    synchronized void waitForOthers() throws InterruptedException { // 定义等待其他线程的方法
      if (++numWaiting == numThreads) { // 增加等待计数器，如果所有线程都已到达
        numWaiting = 0; // 重置等待计数器
        Unsafe.notifyAll(this); // 唤醒所有等待的线程
      } else { // 如果还有线程未到达同步点
        // REVIEW: SZ 6/17/2004: Need a timeout here --
        // otherwise a test case will hang forever if there's
        // a deadlock.  The question is, how long should the
        // timeout be to avoid falsely detecting deadlocks?
        // 复查：SZ 2004年6月17日：这里需要一个超时机制
        // 否则如果发生死锁，测试用例将永远挂起
        // 问题是，超时时间应该设置为多长，以避免错误地检测到死锁？
        Unsafe.wait(this); // 当前线程进入等待状态，直到被唤醒
      }
    }
  }
} // 类定义结束
