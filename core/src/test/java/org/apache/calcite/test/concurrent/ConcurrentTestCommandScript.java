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
import org.apache.calcite.util.TestUnsafe;
import org.apache.calcite.util.Unsafe;
import org.apache.calcite.util.Util;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.util.Objects.requireNonNull;

/**
 * ConcurrentTestCommandScript创建{@link ConcurrentTestCommand}实例，这些实例在测试线程（{@link ConcurrentTestCommandExecutor}）的上下文中按特定顺序执行特定操作。
 *
 * <p>操作从脚本加载（有关脚本格式，请参阅包javadoc）。
 *
 * <p>单个ConcurrentTestCommandScript为多个线程创建命令。每个线程由整数"线程ID"表示，并可选择性地由字符串线程名称表示。
 * 线程ID可以是任何正整数值，并且可以是稀疏集（例如1、2、5）。线程名称可以是任何字符串。
 *
 * <p>创建每个命令时，它与一个线程关联并赋予执行顺序。执行顺序值为正整数，在线程内必须唯一，并且可以是稀疏集。
 * 有关其他注意事项，请参阅{@link ConcurrentTestCommandGenerator#synchronizeCommandSets}。
 *
 * <p>该类主要用于并发测试场景，能够：
 * 1. 解析测试脚本文件，提取各种命令和配置
 * 2. 为多个测试线程生成命令序列
 * 3. 管理线程间的同步和协调
 * 4. 执行SQL语句和其他测试操作
 * 5. 收集和输出测试结果
 * 6. 支持变量替换和脚本包含功能
 * 7. 提供插件扩展机制
 */
public class ConcurrentTestCommandScript
    extends ConcurrentTestCommandGenerator {

  private static final String PRE_SETUP_STATE = "pre-setup";  // 预设置状态：在设置阶段之前，用于定义变量、插件等全局配置
  private static final String SETUP_STATE = "setup";  // 设置状态：执行初始化SQL语句，创建测试所需的表、数据等
  private static final String POST_SETUP_STATE = "post-setup";  // 设置后状态：设置阶段完成后的状态
  private static final String CLEANUP_STATE = "cleanup";  // 清理状态：执行清理SQL语句，删除测试数据、表等
  private static final String POST_CLEANUP_STATE = "post-cleanup";  // 清理后状态：清理阶段完成后的状态
  private static final String THREAD_STATE = "thread";  // 线程状态：定义测试线程及其命令
  private static final String REPEAT_STATE = "repeat";  // 重复状态：重复执行一组命令
  private static final String SQL_STATE = "sql";  // SQL状态：执行SQL语句
  private static final String POST_THREAD_STATE = "post-thread";  // 线程后状态：线程定义完成后的状态
  private static final String EOF_STATE = "eof";  // 文件结束状态：脚本解析完成

  private static final String VAR = "@var";  // 变量定义命令：定义脚本变量，格式为 @var VAR=VAL 或 @var VAR
  private static final String LOCKSTEP = "@lockstep";  // 锁步模式命令：启用锁步执行模式，所有线程同步执行
  private static final String NOLOCKSTEP = "@nolockstep";  // 非锁步模式命令：禁用锁步执行模式
  private static final String ENABLED = "@enabled";  // 启用命令：标记测试为启用状态
  private static final String DISABLED = "@disabled";  // 禁用命令：标记测试为禁用状态，跳过执行
  private static final String SETUP = "@setup";  // 设置命令：开始设置块，用于定义初始化SQL
  private static final String CLEANUP = "@cleanup";  // 清理命令：开始清理块，用于定义清理SQL
  private static final String END = "@end";  // 结束命令：结束当前块（setup、cleanup、repeat等）
  private static final String THREAD = "@thread";  // 线程命令：定义测试线程，格式为 @thread threadName1,threadName2,...
  private static final String REPEAT = "@repeat";  // 重复命令：开始重复块，指定重复次数
  private static final String SYNC = "@sync";  // 同步命令：在线程间添加同步点
  private static final String TIMEOUT = "@timeout";  // 超时命令：为SQL命令设置超时时间
  private static final String ROWLIMIT = "@rowlimit";  // 行限制命令：限制SELECT查询返回的行数
  private static final String PREPARE = "@prepare";  // 准备命令：创建PreparedStatement，供后续FETCH使用
  private static final String PRINT = "@print";  // 打印命令：设置结果输出格式（all、none、every N、count、time、total）
  private static final String String FETCH = "@fetch";  // 获取命令：执行准备好的语句并获取结果
  private static final String CLOSE = "@close";  // 关闭命令：关闭当前PreparedStatement
  private static final String SLEEP = "@sleep";  // 休眠命令：让线程休眠指定毫秒数
  private static final String ERR = "@err";  // 错误命令：标记后续SQL预期会失败
  private static final String ECHO = "@echo";  // 回显命令：输出消息到测试结果
  private static final String INCLUDE = "@include";  // 包含命令：包含并执行另一个脚本文件
  private static final String SHELL = "@shell";  // Shell命令：执行shell命令
  private static final String PLUGIN = "@plugin";  // 插件命令：加载测试插件

  private static final String SQL = "";  // SQL命令标识：空字符串表示SQL语句（非@开头的命令）
  private static final @Nullable String EOF = null;  // 文件结束标识：null表示到达文件末尾

  private static final StateAction[] STATE_TABLE = {
      new StateAction(
          PRE_SETUP_STATE,
          new StateDatum[]{
              new StateDatum(VAR, PRE_SETUP_STATE),
              new StateDatum(LOCKSTEP, PRE_SETUP_STATE),
              new StateDatum(NOLOCKSTEP, PRE_SETUP_STATE),
              new StateDatum(ENABLED, PRE_SETUP_STATE),
              new StateDatum(DISABLED, PRE_SETUP_STATE),
              new StateDatum(PLUGIN, PRE_SETUP_STATE),
              new StateDatum(SETUP, SETUP_STATE),
              new StateDatum(CLEANUP, CLEANUP_STATE),
              new StateDatum(THREAD, THREAD_STATE)
          }),

      new StateAction(
          SETUP_STATE,
          new StateDatum[]{
              new StateDatum(END, POST_SETUP_STATE),
              new StateDatum(SQL, SETUP_STATE),
              new StateDatum(INCLUDE, SETUP_STATE),
          }),

      new StateAction(
          POST_SETUP_STATE,
          new StateDatum[]{
              new StateDatum(CLEANUP, CLEANUP_STATE),
              new StateDatum(THREAD, THREAD_STATE)
          }),

      new StateAction(
          CLEANUP_STATE,
          new StateDatum[]{
              new StateDatum(END, POST_CLEANUP_STATE),
              new StateDatum(SQL, CLEANUP_STATE),
              new StateDatum(INCLUDE, CLEANUP_STATE),
          }),

      new StateAction(
          POST_CLEANUP_STATE,
          new StateDatum[]{
              new StateDatum(THREAD, THREAD_STATE)
          }),

      new StateAction(
          THREAD_STATE,
          new StateDatum[]{
              new StateDatum(REPEAT, REPEAT_STATE),
              new StateDatum(SYNC, THREAD_STATE),
              new StateDatum(TIMEOUT, THREAD_STATE),
              new StateDatum(ROWLIMIT, THREAD_STATE),
              new StateDatum(PREPARE, THREAD_STATE),
              new StateDatum(PRINT, THREAD_STATE),
              new StateDatum(FETCH, THREAD_STATE),
              new StateDatum(CLOSE, THREAD_STATE),
              new StateDatum(SLEEP, THREAD_STATE),
              new StateDatum(SQL, THREAD_STATE),
              new StateDatum(ECHO, THREAD_STATE),
              new StateDatum(ERR, THREAD_STATE),
              new StateDatum(SHELL, THREAD_STATE),
              new StateDatum(END, POST_THREAD_STATE)
          }),

      new StateAction(
          REPEAT_STATE,
          new StateDatum[]{
              new StateDatum(SYNC, REPEAT_STATE),
              new StateDatum(TIMEOUT, REPEAT_STATE),
              new StateDatum(ROWLIMIT, REPEAT_STATE),
              new StateDatum(PREPARE, REPEAT_STATE),
              new StateDatum(PRINT, REPEAT_STATE),
              new StateDatum(FETCH, REPEAT_STATE),
              new StateDatum(CLOSE, REPEAT_STATE),
              new StateDatum(SLEEP, REPEAT_STATE),
              new StateDatum(SQL, REPEAT_STATE),
              new StateDatum(ECHO, REPEAT_STATE),
              new StateDatum(ERR, REPEAT_STATE),
              new StateDatum(SHELL, REPEAT_STATE),
              new StateDatum(END, THREAD_STATE)
          }),

      new StateAction(
          POST_THREAD_STATE,
          new StateDatum[]{
              new StateDatum(THREAD, THREAD_STATE),
              new StateDatum(EOF, EOF_STATE)
          })
  };

  private static final int FETCH_LEN = FETCH.length();  // @fetch命令的长度（用于快速提取参数）
  private static final int PREPARE_LEN = PREPARE.length();  // @prepare命令的长度
  private static final int PRINT_LEN = PRINT.length();  // @print命令的长度
  private static final int REPEAT_LEN = REPEAT.length();  // @repeat命令的长度
  private static final int SLEEP_LEN = SLEEP.length();  // @sleep命令的长度
  private static final int THREAD_LEN = THREAD.length();  // @thread命令的长度
  private static final int TIMEOUT_LEN = TIMEOUT.length();  // @timeout命令的长度
  private static final int ROWLIMIT_LEN = ROWLIMIT.length();  // @rowlimit命令的长度
  private static final int ERR_LEN = ERR.length();  // @err命令的长度
  private static final int ECHO_LEN = ECHO.length();  // @echo命令的长度
  private static final int SHELL_LEN = SHELL.length();  // @shell命令的长度
  private static final int PLUGIN_LEN = PLUGIN.length();  // @plugin命令的长度
  private static final int INCLUDE_LEN = INCLUDE.length();  // @include命令的长度
  private static final int VAR_LEN = VAR.length();  // @var命令的长度

  private static final int BUF_SIZE = 1024;  // 缓冲区大小：用于格式化输出的字符数组大小
  private static final int REPEAT_READ_AHEAD_LIMIT = 65536;  // 重复块读取限制：@repeat块的最大字节数，用于支持reset操作

  private static final char[] SPACES = fill(new char[BUF_SIZE], ' ');  // 空格字符数组：用于格式化输出时的填充
  private static final char[] DASHES = fill(new char[BUF_SIZE], '-');  // 破折号字符数组：用于打印表格分隔线

  // 特殊的"线程ID"用于设置和清理部分；实际上设置和清理SQL由主线程执行，它们都不在线程映射中。
  private static final Integer SETUP_THREAD_ID = -1;  // 设置线程ID：-1表示setup阶段，由主线程执行
  private static final Integer CLEANUP_THREAD_ID = -2;  // 清理线程ID：-2表示cleanup阶段，由主线程执行

  //~ Instance fields (representing a single script):

  private boolean quiet = false;  // 安静模式：为true时不输出详细信息
  private boolean verbose = false;  // 详细模式：为true时输出详细执行信息
  private Boolean lockstep;  // 锁步模式：null表示未设置，true表示启用锁步，false表示禁用锁步
  private Boolean disabled;  // 禁用状态：null表示未设置，true表示测试被禁用，false表示测试启用
  private VariableTable vars = new VariableTable();  // 变量表：存储脚本中定义的变量及其值
  private File scriptDirectory;  // 脚本目录：当前脚本文件所在的目录，用于解析相对路径
  private long scriptStartTime = 0;  // 脚本开始时间：脚本执行开始的时间戳，用于计算执行时长

  private final List<ConcurrentTestPlugin> plugins = new ArrayList<>();  // 插件列表：已加载的所有测试插件
  private final Map<String, ConcurrentTestPlugin> pluginForCommand =  // 插件命令映射：将自定义命令名映射到对应的插件
      new HashMap<>();
  private final Map<String, ConcurrentTestPlugin> preSetupPluginForCommand =  // 预设置插件命令映射：在setup阶段之前可执行的插件命令
      new HashMap<>();
  private final List<String> setupCommands = new ArrayList<>();  // 设置命令列表：setup阶段要执行的SQL语句
  private final List<String> cleanupCommands = new ArrayList<>();  // 清理命令列表：cleanup阶段要执行的SQL语句

  private final Map<Integer, BufferedWriter> threadBufferedWriters =  // 线程缓冲写入器映射：每个线程的BufferedWriter，用于写入输出
      new HashMap<>();
  private final Map<Integer, StringWriter> threadStringWriters =  // 线程字符串写入器映射：每个线程的StringWriter，用于捕获输出
      new HashMap<>();
  private final Map<Integer, ResultsReader> threadResultsReaders =  // 线程结果读取器映射：每个线程的ResultsReader，用于格式化结果输出
      new HashMap<>();

  public ConcurrentTestCommandScript() {
    super();  // 调用父类ConcurrentTestCommandGenerator的构造函数
  }

  /**
   * 构造并准备一个新的ConcurrentTestCommandScript。
   * @param filename 要加载的脚本文件名
   * @throws IOException 如果读取脚本文件时发生I/O错误
   */
  public ConcurrentTestCommandScript(String filename) throws IOException {
    this();  // 调用无参构造函数
    prepare(filename, null);  // 准备脚本，不使用外部变量绑定
  }

  //~ Methods ----------------------------------------------------------------

  private static char[] fill(char[] chars, char c) {
    Arrays.fill(chars, c);  // 使用指定字符填充整个数组
    return chars;  // 返回填充后的数组
  }

  /**
   * 准备执行：加载脚本文件FILENAME并应用外部变量绑定BINDINGS。
   * @param filename 要加载的脚本文件名
   * @param bindings 外部变量绑定列表，格式为VAR=VAL
   * @throws IOException 如果读取脚本文件时发生I/O错误
   */
  private void prepare(String filename, @Nullable List<String> bindings)
      throws IOException {
    vars = new VariableTable();  // 创建新的变量表
    CommandParser parser = new CommandParser();  // 创建命令解析器
    parser.rememberVariableRebindings(bindings);  // 记住外部变量绑定
    parser.load(filename);  // 加载并解析脚本文件

    for (Integer threadId : getThreadIds()) {  // 遍历所有线程ID
      addThreadWriters(threadId);  // 为每个线程添加输出写入器
    }

    // 向后兼容：打印的结果总是有setup部分，但cleanup部分是可选的：
    setThreadName(SETUP_THREAD_ID, "setup");  // 设置setup线程的名称
    addThreadWriters(SETUP_THREAD_ID);  // 为setup线程添加输出写入器
    if (!cleanupCommands.isEmpty()) {  // 如果有清理命令
      setThreadName(CLEANUP_THREAD_ID, "cleanup");  // 设置cleanup线程的名称
      addThreadWriters(CLEANUP_THREAD_ID);  // 为cleanup线程添加输出写入器
    }
  }

  /**
   * 执行脚本。
   * @throws Exception 如果执行过程中发生任何错误
   */
  public void execute() throws Exception {
    scriptStartTime = System.currentTimeMillis();  // 记录脚本开始时间
    executeSetup();  // 执行setup阶段的命令
    ConcurrentTestCommandExecutor[] threads = innerExecute();  // 执行线程命令并获取执行器数组
    executeCleanup();  // 执行cleanup阶段的命令
    postExecute(threads);  // 执行后的处理
  }

  /**
   * 为指定线程添加输出写入器和结果读取器。
   * @param threadId 线程ID
   */
  private void addThreadWriters(Integer threadId) {
    StringWriter w = new StringWriter();  // 创建字符串写入器用于捕获输出
    BufferedWriter bw = new BufferedWriter(w);  // 创建缓冲写入器包装字符串写入器
    threadStringWriters.put(threadId, w);  // 存储字符串写入器
    threadBufferedWriters.put(threadId, bw);  // 存储缓冲写入器
    threadResultsReaders.put(threadId, new ResultsReader(bw));  // 创建并存储结果读取器
  }

  public void setQuiet(boolean val) {
    quiet = val;  // 设置安静模式标志
  }

  public void setVerbose(boolean val) {
    verbose = val;  // 设置详细模式标志
  }


  /**
   * 判断是否使用锁步执行模式。
   * @return true如果启用锁步模式，false否则
   */
  public boolean useLockstep() {
    return lockstep != null && lockstep;  // 返回锁步模式设置
  }

  /**
   * 判断测试是否被禁用。
   * @return true如果测试被禁用，false否则
   */
  public boolean isDisabled() {
    for (ConcurrentTestPlugin plugin : plugins) {  // 遍历所有插件
      if (plugin.isTestDisabled()) {  // 如果有插件禁用了测试
        return true;  // 返回true表示测试被禁用
      }
    }

    return disabled != null && disabled;  // 返回禁用标志
  }

  /**
   * 执行setup阶段的命令。
   * @throws Exception 如果执行过程中发生错误
   */
  public void executeSetup() throws Exception {
    executeCommands(SETUP_THREAD_ID, setupCommands);  // 执行setup命令列表
  }

  /**
   * 执行cleanup阶段的命令。
   * @throws Exception 如果执行过程中发生错误
   */
  public void executeCleanup() throws Exception {
    executeCommands(CLEANUP_THREAD_ID, cleanupCommands);  // 执行cleanup命令列表
  }

  protected void executeCommands(int threadId, List<String> commands)
      throws Exception {
    if (commands.isEmpty()) {  // 如果命令列表为空
      return;  // 直接返回
    }

    Connection connection = DriverManager.getConnection(jdbcURL, jdbcProps);  // 建立数据库连接
    if (connection.getMetaData().supportsTransactions()) {  // 如果数据库支持事务
      connection.setAutoCommit(false);  // 关闭自动提交，启用事务
    }

    boolean forced = false;         // 强制标志：为true时错误后继续执行
    try {
      for (String command : commands) {  // 遍历所有命令
        String sql = command.trim();  // 去除首尾空格
        storeSql(threadId, sql);  // 存储SQL语句到输出

        if (isComment(sql)) {  // 如果是注释行
          continue;  // 跳过
        }

        // 处理sqlline类型的指令：
        if (sql.startsWith("!set")) {  // 如果是!set指令
          String[] tokens = sql.split(" +");  // 按空格分割
          // 只处理SET FORCE
          if ((tokens.length > 2)
              && tokens[1].equalsIgnoreCase("force")) {  // 如果是set force
            forced = asBoolValue(tokens[2]);  // 设置强制标志
          }
          continue;           // 否则忽略
        } else if (sql.startsWith("!")) {  // 如果是其他!开头的指令
          continue;           // 忽略
        }

        if (sql.endsWith(";")) {  // 如果SQL以分号结尾
          sql = sql.substring(0, sql.length() - 1);  // 去掉分号
        }

        if (isSelect(sql)) {  // 如果是SELECT语句
          try (Statement stmt = connection.createStatement()) {  // 创建语句对象
            ResultSet rset = stmt.executeQuery(sql);  // 执行查询
            storeResults(threadId, rset, -1);  // 存储结果（无超时）
          }
        } else if (sql.equalsIgnoreCase("commit")) {  // 如果是commit命令
          connection.commit();  // 提交事务
        } else if (sql.equalsIgnoreCase("rollback")) {  // 如果是rollback命令
          connection.rollback();  // 回滚事务
        } else {  // 其他SQL语句（INSERT、UPDATE、DELETE等）
          try (Statement stmt = connection.createStatement()) {  // 创建语句对象
            int rows = stmt.executeUpdate(sql);  // 执行更新
            if (rows != 1) {  // 如果影响的行数不是1
              storeMessage(threadId, rows + " rows affected.");  // 存储影响行数信息
            } else {
              storeMessage(threadId, "1 row affected.");  // 存储影响1行的信息
            }
          } catch (SQLException ex) {  // 捕获SQL异常
            if (forced) {  // 如果启用强制模式
              storeMessage(threadId, ex.getMessage()); // 吞掉异常，只记录消息
            } else {  // 否则
              throw ex;  // 重新抛出异常
            }
          }
        }
      }
    } finally {
      if (connection.getMetaData().supportsTransactions()) {  // 如果支持事务
        connection.rollback();  // 回滚事务（不提交任何更改）
      }
      connection.close();  // 关闭连接
    }
  }

  // timeout < 0 表示无超时
  private void storeResults(Integer threadId, ResultSet rset, long timeout)
      throws SQLException {
    ResultsReader r = threadResultsReaders.get(threadId);  // 获取线程的结果读取器
    r.read(rset, timeout);  // 读取结果集并格式化输出
  }

  /**
   * 识别注释行的开始；规则与sqlline相同。
   * @param line 要检查的行
   * @return true如果是注释行，false否则
   */
  private boolean isComment(String line) {
    return line.startsWith("--") || line.startsWith("#");  // 以--或#开头的是注释
  }

  /**
   * 转换!set force等指令的参数为布尔值。
   * @param s 要转换的字符串
   * @return true如果字符串表示true，false否则
   */
  private boolean asBoolValue(String s) {
    return s.equalsIgnoreCase("true")  // true
        || s.equalsIgnoreCase("yes")   // yes
        || s.equalsIgnoreCase("on");   // on
  }

  /**
   * 判断SQL块是否为SELECT语句。
   * @param sql 要检查的SQL语句
   * @return true如果是SELECT语句，false否则
   */
  private boolean isSelect(String sql) {
    BufferedReader rdr = new BufferedReader(new StringReader(sql));  // 创建缓冲读取器

    try {
      String line;
      while ((line = rdr.readLine()) != null) {  // 逐行读取
        line = line.trim().toLowerCase(Locale.ROOT);  // 去除空格并转为小写
        if (isComment(line)) {  // 如果是注释行
          continue;  // 跳过
        }
        return line.startsWith("select")  // 以select开头
            || line.startsWith("values")  // 以values开头
            || line.startsWith("explain");  // 以explain开头
      }
    } catch (IOException e) {
      assert false : "IOException via StringReader";  // StringReader不应该抛出IO异常
    } finally {
      try {
        rdr.close();  // 关闭读取器
      } catch (IOException e) {
        assert false : "IOException via StringReader";  // StringReader不应该抛出IO异常
      }
    }

    return false;  // 不是SELECT语句
  }

  /**
   * 构建线程ID到线程结果数据的映射。每个结果数据是一个<code>String[2]</code>，包含线程名称和线程的输出。
   *
   * @return 线程ID到结果的映射
   */
  private Map<Integer, String[]> collectResults() {
    final TreeMap<Integer, String[]> results = new TreeMap<>();  // 使用TreeMap按线程ID排序

    // 获取所有普通线程
    final TreeSet<Integer> threadIds = new TreeSet<>(getThreadIds());  // 使用TreeSet排序
    // 添加"特殊线程"
    threadIds.add(SETUP_THREAD_ID);  // 添加setup线程
    threadIds.add(CLEANUP_THREAD_ID);  // 添加cleanup线程

    for (Integer threadId : threadIds) {  // 遍历所有线程ID
      try {
        BufferedWriter bout = threadBufferedWriters.get(threadId);  // 获取缓冲写入器
        if (bout != null) {  // 如果写入器存在
          bout.flush();  // 刷新缓冲区
        }
      } catch (IOException e) {
        assert false : "IOException via StringWriter";  // StringWriter不应该抛出IO异常
      }
      String threadName = getFormattedThreadName(threadId);  // 获取格式化的线程名称
      StringWriter out = threadStringWriters.get(threadId);  // 获取字符串写入器
      if (out == null) {  // 如果写入器不存在
        continue;  // 跳过
      }
      results.put(  // 存储结果
          threadId,
          new String[]{threadName, out.toString()});  // 线程名称和输出
    }
    return results;  // 返回结果映射
  }

  // 仅用于向后兼容的输出格式
  private String getFormattedThreadName(Integer id) {
    if (id < 0) {                   // 特殊线程（setup或cleanup）
      return getThreadName(id);  // 直接返回线程名称
    } else {                        // 普通线程
      return "thread " + getThreadName(id);  // 添加"thread "前缀
    }
  }

  /**
   * 打印所有线程的结果。
   * @param out 输出写入器
   * @throws IOException 如果写入时发生I/O错误
   */
  public void printResults(PrintWriter out) throws IOException {
    final Map<Integer, String[]> results = collectResults();  // 收集所有结果
    if (verbose) {  // 如果启用详细模式
      out.write(  // 输出脚本开始时间
          String.format(Locale.ROOT,
              "script execution started at %tc (%d)%n",
              new Timestamp(scriptStartTime), scriptStartTime));
    }
    printThreadResults(out, results.get(SETUP_THREAD_ID));  // 打印setup结果
    for (Integer id : results.keySet()) {  // 遍历所有线程
      if (id < 0) {  // 如果是特殊线程
        continue;               // 跳过（setup和cleanup已经处理）
      }
      printThreadResults(out, results.get(id)); // 打印普通线程结果
    }
    printThreadResults(out, results.get(CLEANUP_THREAD_ID));  // 打印cleanup结果
  }

  private void printThreadResults(PrintWriter out,
      String @Nullable[] threadResult) {
    if (threadResult == null) {  // 如果结果为空
      return;  // 直接返回
    }
    String threadName = threadResult[0];  // 获取线程名称
    out.write("-- " + threadName);  // 输出线程开始标记
    out.println();  // 换行
    out.write(threadResult[1]);  // 输出线程结果
    out.write("-- end of " + threadName);  // 输出线程结束标记
    out.println();  // 换行
    out.println();  // 空行
    out.flush();  // 刷新缓冲区
  }

  /**
   * 指示需要自定义错误处理。参见
   * {@link #customErrorHandler(ConcurrentTestCommandExecutor)}。
   * @return true表示需要自定义错误处理
   */
  boolean requiresCustomErrorHandling() {
    return true;  // 总是返回true，启用自定义错误处理
  }

  /**
   * 自定义错误处理器，处理线程执行过程中的错误。
   * @param executor 发生错误的执行器
   */
  void customErrorHandler(
      ConcurrentTestCommandExecutor executor) {
    StringBuilder message = new StringBuilder();  // 创建消息构建器
    Throwable cause = executor.getFailureCause();  // 获取失败原因
    ConcurrentTestCommand command = executor.getFailureCommand();  // 获取失败的命令

    if ((command == null) || !command.isFailureExpected()) {  // 如果命令不存在或失败不是预期的
      message.append(cause.getMessage());  // 添加异常消息
      StackTraceElement[] trace = cause.getStackTrace();  // 获取堆栈跟踪
      for (StackTraceElement aTrace : trace) {  // 遍历堆栈跟踪
        message.append("\n\t").append(aTrace.toString());  // 添加每个堆栈元素
      }
    } else {  // 如果失败是预期的
      message.append(cause.getClass().getName())  // 添加异常类名
          .append(": ")
          .append(cause.getMessage());  // 添加异常消息
    }

    storeMessage(  // 存储错误消息
        executor.getThreadId(),
        message.toString());
  }

  /**
   * 获取指定线程ID的输出流。
   *
   * @return 线程的BufferedWriter（包装在StringWriter上）
   */
  private BufferedWriter getThreadWriter(Integer threadId) {
    assert threadBufferedWriters.containsKey(threadId);  // 断言线程写入器存在
    return threadBufferedWriters.get(threadId);  // 返回线程的缓冲写入器
  }


  /**
   * 保存SQL命令以便与线程的输出一起打印。
   * @param threadId 线程ID
   * @param sql SQL语句
   */
  private void storeSql(Integer threadId, String sql) {
    StringBuilder message = new StringBuilder();  // 创建消息构建器

    BufferedReader rdr = new BufferedReader(new StringReader(sql));  // 创建缓冲读取器

    try {
      String line;
      while ((line = rdr.readLine()) != null) {  // 逐行读取SQL
        line = line.trim();  // 去除空格

        if (message.length() > 0) {  // 如果消息不为空
          message.append('\n');  // 添加换行
        }

        message.append("> ").append(line);  // 添加"> "前缀和SQL行
      }
    } catch (IOException e) {
      assert false : "IOException via StringReader";  // StringReader不应该抛出IO异常
    } finally {
      try {
        rdr.close();  // 关闭读取器
      } catch (IOException e) {
        assert false : "IOException via StringReader";  // StringReader不应该抛出IO异常
      }
    }

    storeMessage(  // 存储消息
        threadId,
        message.toString());
  }

  /**
   * 保存消息以便与线程的输出一起打印。
   * @param threadId 线程ID
   * @param message 要保存的消息
   */
  private void storeMessage(Integer threadId, String message) {
    BufferedWriter out = getThreadWriter(threadId);  // 获取线程写入器
    try {
      if (verbose) {  // 如果启用详细模式
        long t = System.currentTimeMillis() - scriptStartTime;  // 计算相对时间
        out.write("at " + t + ": ");  // 输出时间戳
      }
      out.write(message);  // 输出消息
      out.newLine();  // 换行
    } catch (IOException e) {
      assert false : "IOException on StringWriter";  // StringWriter不应该抛出IO异常
    }
  }

  //~ Inner Classes ----------------------------------------------------------

  /** State action. */
  /** 状态动作：定义在特定状态下允许的命令及其转换的目标状态。 */
  private static class StateAction {
    final String state;  // 当前状态名称
    final StateDatum[] stateData;  // 该状态下允许的命令数据数组

    StateAction(String state, StateDatum[] stateData) {
      this.state = state;  // 设置状态名称
      this.stateData = stateData;  // 设置状态数据数组
    }
  }

  /** 状态数据：定义一个命令及其转换的目标状态。 */
  private static class StateDatum {
    final @Nullable String x;  // 命令名称（如@sync、@thread等），null表示EOF
    final String y;  // 转换的目标状态名称

    StateDatum(@Nullable String x, String y) {
      this.x = x;  // 设置命令名称
      this.y = y;  // 设置目标状态
    }
  }


  /** 脚本变量的符号表：管理脚本中定义的变量及其值，支持变量替换。 */
  private static class VariableTable {
    private final Map<String, String> map;  // 变量名到变量值的映射

    // 匹配 $$, $var, ${var} 模式的正则表达式
    private final Pattern symbolPattern =
        Pattern.compile("\\$((\\$)|([A-Za-z]\\w*)|\\{([A-Za-z]\\w*)})");

    VariableTable() {
      map = new HashMap<>();  // 初始化空的变量映射
    }

    /** 变量表异常：用于报告变量定义错误。 */
    public class Excn extends IllegalArgumentException {
      Excn(String msg) {
        super(msg);  // 调用父类构造函数
      }
    }

    /**
     * 判断变量表是否为空。
     * @return true如果没有定义任何变量
     */
    public boolean isEmpty() {
      return map.isEmpty();  // 检查映射是否为空
    }

    /**
     * 判断变量是否已定义。
     * @param sym 变量名
     * @return true如果变量已定义
     */
    public boolean isDefined(String sym) {
      return map.containsKey(sym);  // 检查映射中是否包含该键
    }

    // 变量必须在使用或读取之前显式定义
    public void define(String sym, String val) {
      if (isDefined(sym)) {  // 如果变量已经定义
        throw new Excn("second declaration of variable " + sym);  // 抛出异常
      }
      // 将null值转换为空字符串
      map.put(sym, val == null ? "" : val);  // 存储变量
    }

    // 如果SYM未定义则返回null
    public @Nullable String get(String sym) {
      if (isDefined(sym)) {  // 如果变量已定义
        return map.get(sym);  // 返回变量值
      } else {
        return null;  // 返回null
      }
    }

    /**
     * 设置变量的值。
     * @param sym 变量名
     * @param val 变量值
     * @throws Excn 如果变量未定义
     */
    public void set(String sym, String val) {
      if (isDefined(sym)) {  // 如果变量已定义
        map.put(sym, val);  // 更新变量值
        return;
      }
      throw new Excn("undeclared variable " + sym);  // 抛出异常
    }

    /**
     * 展开字符串中的变量引用，将$var、${var}等替换为实际的变量值。
     * @param in 输入字符串
     * @return 展开后的字符串
     */
    public String expand(String in) {
      if (in.contains("$")) {  // 如果字符串包含$
        StringBuilder out = new StringBuilder();  // 创建输出构建器
        Matcher matcher = symbolPattern.matcher(in);  // 创建匹配器
        int lastEnd = 0;  // 上次匹配结束位置
        while (matcher.find()) {  // 查找所有匹配
          int start = matcher.start();  // 匹配开始位置
          int end = matcher.end();  // 匹配结束位置
          String val;
          if (null != matcher.group(2)) {  // 如果匹配$$
            val = "$";          // 替换为$
          } else {
            String var = matcher.group(3); // 匹配$var
            if (var == null) {
              var = matcher.group(4); // 匹配${var}
            }
            if (map.containsKey(var)) {  // 如果变量存在
              val = map.get(var);  // 获取变量值
              val = expand(val);  // 递归展开（支持嵌套变量）
            } else {
              // 不是我们的变量，无法展开
              val = matcher.group(0);  // 保持原样
            }
          }
          out.append(in.substring(lastEnd, start));  // 添加匹配前的文本
          out.append(val);  // 添加替换后的值
          lastEnd = end;  // 更新结束位置
        }
        out.append(in.substring(lastEnd));  // 添加剩余文本
        return out.toString();  // 返回展开后的字符串
      } else {
        return in;  // 没有变量引用，直接返回
      }
    }
  }


  /** 命令解析器：解析测试脚本文件，提取命令并生成测试命令序列。 */
  private class CommandParser {
    final Pattern splitWords = Pattern.compile("\\s+");  // 分割单词的正则表达式（一个或多个空白字符）
    final Pattern splitBinding = Pattern.compile("=");  // 分割绑定的正则表达式（等号）
    final Pattern matchesVarDefn =  // 匹配变量定义的正则表达式
        Pattern.compile("([A-Za-z]\\w*) *=(.*)$");
    // \1是VAR，\2是VAL

    // 解析器状态
    private String state;  // 当前解析状态（如PRE_SETUP_STATE、SETUP_STATE等）
    private int threadId;  // 当前线程ID
    private int nextThreadId;  // 下一个要分配的线程ID
    private int order;  // 当前命令的执行顺序号
    private int repeatCount;  // 重复块的剩余重复次数
    private boolean scriptHasVars;  // 脚本是否包含变量
    private final Deque<File> currentDirectory = new ArrayDeque<>();  // 当前目录栈（支持@include嵌套）

    /** 变量绑定：将值绑定到变量。 */
    private class Binding {
      public final String var;  // 变量名
      public final String val;  // 变量值

      Binding(String var, String val) {
        this.var = var;  // 设置变量名
        this.val = val;  // 设置变量值
      }

      // @param phrase 格式为VAR=VAL
      Binding(String phrase) {
        String[] parts = splitBinding.split(phrase);  // 按等号分割
        assert parts.length == 2;  // 断言分割成两部分
        this.var = parts[0];  // 变量名
        this.val = parts[1];  // 变量值
      }
    }

    // 必须在解析最后一个@var后立即应用的绑定列表
    private final List<Binding> deferredBindings = new ArrayList<>();

    CommandParser() {
      state = PRE_SETUP_STATE;  // 初始状态为预设置状态
      threadId = nextThreadId = 1;  // 线程ID从1开始
      order = 1;  // 执行顺序从1开始
      repeatCount = 0;  // 重复次数为0
      scriptHasVars = false;  // 脚本没有变量
      currentDirectory.push(null);  // 初始目录为null
    }

    // 从命令行解析一组VAR=VAL对，并保存以供后续应用
    public void rememberVariableRebindings(@Nullable List<String> pairs) {
      if (pairs == null) {  // 如果绑定列表为空
        return;  // 直接返回
      }
      for (String pair : pairs) {  // 遍历所有绑定
        deferredBindings.add(new Binding(pair));  // 创建并保存绑定
      }
    }

    // 在所有@var命令之后但在任何SQL之前调用
    private void applyVariableRebindings() {
      for (Binding binding : deferredBindings) {  // 遍历所有延迟绑定
        vars.set(binding.var, binding.val);  // 设置变量值
      }
    }

    // 跟踪脚本加载过程（用于调试）
    private void trace(@Nullable String prefix, Object message) {
      if (verbose && !quiet) {  // 如果启用详细模式且不是安静模式
        if (prefix != null) {  // 如果有前缀
          System.out.print(prefix + ": ");  // 输出前缀
        }
        System.out.println(message);  // 输出消息
      }
    }

    private void trace(String message) {
      trace(null, message);  // 无前缀跟踪
    }

    /**
     * 解析多线程脚本并将其转换为测试命令。
     * @param scriptFileName 脚本文件名
     * @throws IOException 如果读取文件时发生I/O错误
     */
    private void load(String scriptFileName) throws IOException {
      File scriptFile = new File(currentDirectory.peek(), scriptFileName);  // 创建文件对象
      currentDirectory.push(scriptDirectory = scriptFile.getParentFile());  // 将当前目录压入栈
      try (BufferedReader in = Util.reader(scriptFile)) {  // 创建缓冲读取器
        String line;
        while ((line = in.readLine()) != null) {  // 逐行读取
          line = line.trim();  // 去除首尾空格
          Map<String, String> commandStateMap = lookupState(state);  // 查找当前状态允许的命令
          final String command;
          boolean isSql = false;
          if (line.isEmpty() || line.startsWith("--")) {  // 如果是空行或注释
            continue;  // 跳过
          } else if (line.startsWith("@")) {  // 如果是命令
            command = firstWord(line);  // 提取命令名
          } else {  // 否则是SQL
            isSql = true;
            command = SQL;  // 使用SQL标识
          }
          if (!commandStateMap.containsKey(command)) {  // 如果命令在当前状态下不允许
            throw new IllegalStateException(  // 抛出异常
                command + " not allowed in state " + state);
          }

          boolean changeState;
          if (isSql) {  // 如果是SQL
            String sql = readSql(line, in);  // 读取SQL块
            loadSql(sql);  // 加载SQL命令
            changeState = true;  // 改变状态
          } else {  // 如果是命令
            changeState = loadCommand(command, line, in);  // 加载命令
          }
          if (changeState) {  // 如果需要改变状态
            String nextState = commandStateMap.get(command);  // 获取下一个状态
            requireNonNull(nextState, "nextState");  // 确保nextState不为null
            if (!nextState.equals(state)) {  // 如果状态改变
              doEndOfState(state);  // 执行状态结束处理
            }
            state = nextState;  // 更新状态
          }
        }

        // 到达文件末尾
        currentDirectory.pop();  // 弹出当前目录
        if (currentDirectory.size() == 1) {  // 如果回到顶层
          // 在顶层EOF
          if (!lookupState(state).containsKey(EOF)) {  // 如果当前状态不允许EOF
            throw new IllegalStateException(  // 抛出异常
                "Premature end of file in '" + state + "' state");
          }
        }
      }
    }

    private void loadSql(String sql) {
      switch (state) {  // 根据当前状态处理SQL
      case SETUP_STATE:  // 在setup状态
        trace("@setup", sql);  // 跟踪
        setupCommands.add(sql);  // 添加到setup命令列表
        break;
      case CLEANUP_STATE:  // 在cleanup状态
        trace("@cleanup", sql);  // 跟踪
        cleanupCommands.add(sql);  // 添加到cleanup命令列表
        break;
      case THREAD_STATE:  // 在thread状态
      case REPEAT_STATE:  // 在repeat状态
        boolean isSelect = isSelect(sql);  // 判断是否是SELECT
        trace(sql);  // 跟踪
        for (int i = threadId; i < nextThreadId; i++) {  // 为当前线程组添加命令
          CommandWithTimeout cmd =  // 创建命令
              isSelect ? new SelectCommand(sql) : new SqlCommand(sql);
          addCommand(i, order, cmd);  // 添加命令
        }
        order++;  // 增加执行顺序
        break;
      default:
        throw new AssertionError();  // 不应该到达这里
      }
    }

    // 返回TRUE时加载状态应该前进，FALSE时不能前进

        private boolean loadCommand(

            String command, String line, BufferedReader in) throws IOException {

          if (VAR.equals(command)) {  // 如果是@var命令

            String args = line.substring(VAR_LEN).trim();  // 提取参数

            scriptHasVars = true;  // 标记脚本包含变量

            trace("@var", args);  // 跟踪

            defineVariables(args);  // 定义变量

    

          } else if (LOCKSTEP.equals(command)) {  // 如果是@lockstep命令

            assert lockstep == null  // 断言lockstep未设置

                : LOCKSTEP + " and " + NOLOCKSTEP + " may only appear once";

            lockstep = Boolean.TRUE;  // 启用锁步模式

            trace("lockstep");  // 跟踪

    

          } else if (NOLOCKSTEP.equals(command)) {  // 如果是@nolockstep命令

            assert lockstep == null  // 断言lockstep未设置

                : LOCKSTEP + " and " + NOLOCKSTEP + " may only appear once";

            lockstep = Boolean.FALSE;  // 禁用锁步模式

            trace("no lockstep");  // 跟踪

    

          } else if (DISABLED.equals(command)) {  // 如果是@disabled命令

            assert disabled == null  // 断言disabled未设置

                : DISABLED + " and " + ENABLED + " may only appear once";

            disabled = Boolean.TRUE;  // 禁用测试

    

            trace("disabled");  // 跟踪

    

          } else if (ENABLED.equals(command)) {  // 如果是@enabled命令

            assert disabled == null  // 断言disabled未设置

                : DISABLED + " and " + ENABLED + " may only appear once";

            disabled = Boolean.FALSE;  // 启用测试

            trace("enabled");  // 跟踪

    

          } else if (SETUP.equals(command)) {  // 如果是@setup命令

            trace("@setup");  // 跟踪

    

          } else if (CLEANUP.equals(command)) {  // 如果是@cleanup命令

            trace("@cleanup");  // 跟踪

    

          } else if (INCLUDE.equals(command)) {  // 如果是@include命令

            String includedFile =  // 提取文件名并展开变量

                vars.expand(line.substring(INCLUDE_LEN).trim());

            trace("@include", includedFile);  // 跟踪

            load(includedFile);  // 加载包含的文件

            trace("end @include", includedFile);  // 跟踪

    

          } else if (THREAD.equals(command)) {  // 如果是@thread命令

            String threadNamesStr = line.substring(THREAD_LEN).trim();  // 提取线程名称

            trace("@thread", threadNamesStr);  // 跟踪

            StringTokenizer threadNamesTok =  // 创建字符串分词器

                new StringTokenizer(threadNamesStr, ",");

            while (threadNamesTok.hasMoreTokens()) {  // 遍历所有线程名称

              setThreadName(  // 设置线程名称

                  nextThreadId++,

                  threadNamesTok.nextToken());

            }

    

          } else if (REPEAT.equals(command)) {  // 如果是@repeat命令

            String arg = line.substring(REPEAT_LEN).trim();  // 提取参数

            repeatCount = parseInt(vars.expand(arg));  // 展开变量并解析为整数

            trace("start @repeat block", repeatCount);  // 跟踪

            assert repeatCount > 0 : "Repeat count must be > 0";  // 断言重复次数>0

            in.mark(REPEAT_READ_AHEAD_LIMIT);  // 标记读取位置以便重复

    

          } else if (END.equals(command)) {  // 如果是@end命令

            switch (state) {  // 根据当前状态处理

            case SETUP_STATE:  // 在setup状态

              trace("end @setup");  // 跟踪

              break;

            case CLEANUP_STATE:  // 在cleanup状态

              trace("end @cleanup");  // 跟踪

              break;

            case THREAD_STATE:  // 在thread状态

              threadId = nextThreadId;  // 更新线程ID

              break;

            case REPEAT_STATE:  // 在repeat状态

              trace("repeating");  // 跟踪

              repeatCount--;  // 减少重复次数

              if (repeatCount > 0) {  // 如果还需要重复

                try {

                  in.reset();  // 重置到标记位置

                } catch (IOException e) {

                  throw new IllegalStateException(  // 抛出异常

                      "Unable to reset reader -- repeat "

                          + "contents must be less than "

                          + REPEAT_READ_AHEAD_LIMIT + " bytes");

                }

    

                trace("end @repeat block");  // 跟踪

                return false;   // 不改变状态

              }

              break;

            default:

              throw new AssertionError();  // 不应该到达这里

            }

    

          } else if (SYNC.equals(command)) {  // 如果是@sync命令

            trace("@sync");  // 跟踪

            for (int i = threadId; i < nextThreadId; i++) {  // 为当前线程组添加同步命令

              addSynchronizationCommand(i, order);

            }

            order++;  // 增加执行顺序

    

          } else if (TIMEOUT.equals(command)) {  // 如果是@timeout命令

            String args = line.substring(TIMEOUT_LEN).trim();  // 提取参数

            String millisStr = vars.expand(firstWord(args));  // 提取超时值并展开变量

            long millis = parseLong(millisStr);  // 解析为长整数

            assert millis >= 0L : "Timeout must be >= 0";  // 断言超时>=0

    

            String sql = readSql(skipFirstWord(args).trim(), in);  // 读取SQL

            trace("@timeout", sql);  // 跟踪

            boolean isSelect = isSelect(sql);  // 判断是否是SELECT

            for (int i = threadId; i < nextThreadId; i++) {  // 为当前线程组添加命令

              CommandWithTimeout cmd =  // 创建带超时的命令

                  isSelect ? new SelectCommand(sql, millis)

                      : new SqlCommand(sql, millis);

              addCommand(i, order, cmd);  // 添加命令

            }

            order++;  // 增加执行顺序

    

          } else if (ROWLIMIT.equals(command)) {  // 如果是@rowlimit命令

            String args = line.substring(ROWLIMIT_LEN).trim();  // 提取参数

            String limitStr = vars.expand(firstWord(args));  // 提取限制值并展开变量

            int limit = parseInt(limitStr);  // 解析为整数

            assert limit >= 0 : "Rowlimit must be >= 0";  // 断言限制>=0

    

            String sql = readSql(skipFirstWord(args).trim(), in);  // 读取SQL

            trace("@rowlimit ", sql);  // 跟踪

            boolean isSelect = isSelect(sql);  // 判断是否是SELECT

            if (!isSelect) {  // 如果不是SELECT

              throw new IllegalStateException(  // 抛出异常

                  "Only select can be used with rowlimit");

            }

            for (int i = threadId; i < nextThreadId; i++) {  // 为当前线程组添加命令

              addCommand(i, order, new SelectCommand(sql, 0, limit));  // 添加带行限制的SELECT命令

            }

            order++;  // 增加执行顺序

    

          } else if (PRINT.equals(command)) {  // 如果是@print命令

            String spec = vars.expand(line.substring(PRINT_LEN).trim());  // 提取规格并展开变量

            trace("@print", spec);  // 跟踪

            for (int i = threadId; i < nextThreadId; i++) {  // 为当前线程组添加命令

              addCommand(i, order, new PrintCommand(spec));  // 添加打印命令

            }

            order++;  // 增加执行顺序

    

          } else if (PREPARE.equals(command)) {  // 如果是@prepare命令

            String startOfSql =  // 提取SQL开始部分

                line.substring(PREPARE_LEN).trim();

            String sql = readSql(startOfSql, in);  // 读取SQL

            trace("@prepare", sql);  // 跟踪

            for (int i = threadId; i < nextThreadId; i++) {  // 为当前线程组添加命令

              addCommand(i, order, new PrepareCommand(sql));  // 添加准备命令

            }

            order++;  // 增加执行顺序

    

          } else if (PLUGIN.equals(command)) {  // 如果是@plugin命令

            String cmd = line.substring(PLUGIN_LEN).trim();  // 提取参数

            String pluginName = readLine(cmd, in).trim();  // 读取插件名称

            trace("@plugin", pluginName);  // 跟踪

            plugin(pluginName);  // 加载插件

    

          } else if (pluginForCommand.containsKey(command)) {  // 如果是插件命令

            String cmd = line.substring(command.length())  // 提取参数

                .trim();

            cmd = readLine(cmd, in);  // 读取命令

            trace("@" + command, cmd);  // 跟踪

            for (int i = threadId; i < nextThreadId; i++) {  // 为当前线程组添加命令

              addCommand(  // 添加插件命令

                  i,

                  order,

                  new PluginCommand(

                      command, cmd));

            }

            order++;  // 增加执行顺序

    

          } else if (preSetupPluginForCommand.containsKey(command)) {  // 如果是预设置插件命令

            String cmd = line.substring(command.length()).trim();  // 提取参数

            cmd = readLine(cmd, in);  // 读取命令

            trace("@" + command, cmd);  // 跟踪

            ConcurrentTestPlugin plugin =  // 获取插件

                preSetupPluginForCommand.get(command);

            plugin.preSetupFor(command, cmd);  // 执行预设置操作

    

    

          } else if (SHELL.equals(command)) {  // 如果是@shell命令

            String cmd = line.substring(SHELL_LEN).trim();  // 提取参数

            cmd = readLine(cmd, in);  // 读取命令

            trace("@shell", cmd);  // 跟踪

            for (int i = threadId; i < nextThreadId; i++) {  // 为当前线程组添加命令

              addCommand(i, order, new ShellCommand(cmd));  // 添加Shell命令

            }

            order++;  // 增加执行顺序

    

          } else if (ECHO.equals(command)) {  // 如果是@echo命令

            String msg = line.substring(ECHO_LEN).trim();  // 提取参数

            msg = readLine(msg, in);  // 读取消息

            trace("@echo", msg);  // 跟踪

            for (int i = threadId; i < nextThreadId; i++) {  // 为当前线程组添加命令

              addCommand(i, order, new EchoCommand(msg));  // 添加回显命令

            }

            order++;  // 增加执行顺序

    

          } else if (ERR.equals(command)) {  // 如果是@err命令

            String startOfSql =  // 提取SQL开始部分

                line.substring(ERR_LEN).trim();

            String sql = readSql(startOfSql, in);  // 读取SQL

            trace("@err ", sql);  // 跟踪

            boolean isSelect = isSelect(sql);  // 判断是否是SELECT

            for (int i = threadId; i < nextThreadId; i++) {  // 为当前线程组添加命令

              CommandWithTimeout cmd =  // 创建预期失败的命令

                  isSelect ? new SelectCommand(sql, true)

                      : new SqlCommand(sql, true);

              addCommand(i, order, cmd);  // 添加命令

            }

            order++;  // 增加执行顺序

    

          } else if (FETCH.equals(command)) {  // 如果是@fetch命令

            String arg = vars.expand(line.substring(FETCH_LEN).trim());  // 提取参数并展开变量

            trace("@fetch", arg);  // 跟踪

            long millis = 0L;

            if (!arg.isEmpty()) {  // 如果有参数

              millis = parseLong(arg);  // 解析为长整数

              assert millis >= 0L : "Fetch timeout must be >= 0";  // 断言超时>=0

            }

    

            for (int i = threadId; i < nextThreadId; i++) {  // 为当前线程组添加命令

              addCommand(  // 添加获取命令

                  i,

                  order,

                  new FetchAndPrintCommand(millis));

            }

            order++;  // 增加执行顺序

    

          } else if (CLOSE.equals(command)) {  // 如果是@close命令

            trace("@close");  // 跟踪

            for (int i = threadId; i < nextThreadId; i++) {  // 为当前线程组添加命令

              addCloseCommand(i, order);  // 添加关闭命令

            }

            order++;  // 增加执行顺序

    

          } else if (SLEEP.equals(command)) {  // 如果是@sleep命令

            String arg = vars.expand(line.substring(SLEEP_LEN).trim());  // 提取参数并展开变量

            trace("@sleep", arg);  // 跟踪

            long millis = parseLong(arg);  // 解析为长整数

            assert millis >= 0L : "Sleep timeout must be >= 0";  // 断言超时>=0

    

            for (int i = threadId; i < nextThreadId; i++) {  // 为当前线程组添加命令

              addSleepCommand(i, order, millis);  // 添加休眠命令

            }

            order++;  // 增加执行顺序

    

          } else {

            assert false : "Unknown command " + command;  // 未知命令

          }

    

          return true;                // 通常情况下，前进状态

        }

    private void doEndOfState(String state) {
      if (state.equals(PRE_SETUP_STATE)) {  // 如果是预设置状态
        applyVariableRebindings();  // 应用变量重新绑定
      }
    }

    /**
     * 定义变量。
     * @param line 变量定义行，格式为"VAR VAR*"或"VAR=VAL$"
     */
    private void defineVariables(String line) {
      // 两种形式："VAR VAR*"和"VAR=VAL$"
      Matcher varDefn = matchesVarDefn.matcher(line);  // 创建匹配器
      if (varDefn.lookingAt()) {  // 如果匹配VAR=VAL形式
        String var = varDefn.group(1);  // 提取变量名
        String val = varDefn.group(2);  // 提取变量值
        vars.define(var, val);  // 定义变量
      } else {  // 否则是VAR VAR*形式（从环境变量读取）
        String[] words = splitWords.split(line);  // 分割单词
        for (String var : words) {  // 遍历所有变量名
          String value = System.getenv(var);  // 从环境变量获取值
          vars.define(var, value);  // 定义变量
        }
      }
    }

    /**
     * 加载插件。
     * @param pluginName 插件类名
     * @throws IOException 如果加载插件失败
     */
    private void plugin(String pluginName) throws IOException {
      try {
        @SuppressWarnings("unchecked")
        Class<ConcurrentTestPlugin> pluginClass =  // 加载插件类
            (Class<ConcurrentTestPlugin>) Class.forName(pluginName);
        final Constructor<ConcurrentTestPlugin> constructor =  // 获取构造函数
            pluginClass.getConstructor();
        final ConcurrentTestPlugin plugin = constructor.newInstance();  // 创建插件实例
        plugins.add(plugin);  // 添加到插件列表
        addExtraCommands(plugin.getSupportedThreadCommands(), THREAD_STATE);  // 添加线程命令
        addExtraCommands(plugin.getSupportedThreadCommands(), REPEAT_STATE);  // 添加重复命令
        for (String commandName : plugin.getSupportedThreadCommands()) {  // 遍历支持的命令
          pluginForCommand.put(commandName, plugin);  // 映射命令到插件
        }
        addExtraCommands(  // 添加预设置命令
            plugin.getSupportedPreSetupCommands(), PRE_SETUP_STATE);
        for (String commandName : plugin.getSupportedPreSetupCommands()) {  // 遍历预设置命令
          preSetupPluginForCommand.put(commandName, plugin);  // 映射命令到插件
        }
      } catch (Exception e) {
        throw new IOException(e.toString());  // 转换为IOException
      }
    }

    private void addExtraCommands(Iterable<String> commands, String state) {
      requireNonNull(state, "state");  // 确保state不为null

      for (int i = 0, n = STATE_TABLE.length; i < n; i++) {  // 遍历状态表
        if (state.equals(STATE_TABLE[i].state)) {  // 如果找到匹配的状态
          StateDatum[] stateData = STATE_TABLE[i].stateData;  // 获取状态数据
          final List<StateDatum> stateDataList =  // 转换为列表
              new ArrayList<>(Arrays.asList(stateData));
          for (String cmd : commands) {  // 遍历所有命令
            stateDataList.add(new StateDatum(cmd, state));  // 添加新的状态数据
          }
          STATE_TABLE[i] =  // 更新状态表
              new StateAction(
                  state, stateDataList.toArray(stateData));
        }
      }
    }

    /**
     * 管理状态转换。
     * 将状态名称转换为映射。映射键是可用命令的名称（例如@sync），
     * 映射值是看到命令时要切换到的状态。
     * @param state 状态名称
     * @return 命令到目标状态的映射
     */
    private Map<String, String> lookupState(String state) {
      requireNonNull(state, "state");  // 确保state不为null

      for (StateAction a : STATE_TABLE) {  // 遍历状态表
        if (state.equals(a.state)) {  // 如果找到匹配的状态
          StateDatum[] stateData = a.stateData;  // 获取状态数据

          Map<String, String> result = new HashMap<>();  // 创建结果映射
          for (StateDatum datum : stateData) {  // 遍历状态数据
            result.put(datum.x, datum.y);  // 添加命令到目标状态的映射
          }
          return result;  // 返回映射
        }
      }

      throw new IllegalArgumentException();  // 抛出异常
    }

    /**
     * 返回给定行的第一个单词，假设行已被修剪。
     * 返回行中第一个非空白字符之前的字符。
     * @param trimmedLine 已修剪的行
     * @return 第一个单词
     */
    private String firstWord(String trimmedLine) {
      return trimmedLine.replaceFirst("\\s.*", "");  // 删除第一个空白及其后的所有内容
    }

    /**
     * 返回给定行除第一个单词外的所有内容，假设行已被修剪。
     * 返回行中第一系列连续空白字符之后的字符。
     * @param trimmedLine 已修剪的行
     * @return 除第一个单词外的内容
     */
    private String skipFirstWord(String trimmedLine) {
      return trimmedLine.replaceFirst("^\\S+\\s+", "");  // 删除第一个单词和随后的空白
    }

    /**
     * 返回输入行，可能由续行字符（\）扩展。
     * 扫描脚本直到找到未转义的换行符。
     * @param line 当前行
     * @param in 缓冲读取器
     * @return 完整的行（可能包含多行）
     * @throws IOException 如果读取时发生I/O错误
     */
    private String readLine(String line, BufferedReader in) throws IOException {
      line = line.trim();  // 去除空格
      boolean more = line.endsWith("\\");  // 检查是否以续行符结尾
      if (more) {  // 如果有续行符
        line = line.substring(0, line.lastIndexOf('\\')); // 去掉续行符
        StringBuilder buf = new StringBuilder(line);        // 保存
        while (more) {  // 继续读取
          line = in.readLine();  // 读取下一行
          if (line == null) {  // 如果到达文件末尾
            break;
          }
          line = line.trim();  // 去除空格
          more = line.endsWith("\\");  // 检查是否还有续行符
          if (more) {  // 如果还有
            line = line.substring(0, line.lastIndexOf('\\'));  // 去掉续行符
          }
          buf.append(' ').append(line);  // 追加到缓冲区
        }
        line = buf.toString().trim();  // 转换为字符串并去除空格
      }

      if (scriptHasVars && line.contains("$")) {  // 如果脚本有变量且行包含$
        line = vars.expand(line);  // 展开变量
      }

      return line;  // 返回完整的行
    }

    /**
     * 返回SQL块，从给定字符串开始。
     * 返回<code>startOfSql</code>与<code>in</code>中每一行的连接，
     * 直到找到以分号结尾的行。
     * @param startOfSql SQL的开始部分
     * @param in 缓冲读取器
     * @return 完整的SQL语句
     * @throws IOException 如果读取时发生I/O错误
     */
    private String readSql(String startOfSql, BufferedReader in)
        throws IOException {
      StringBuilder sql = new StringBuilder(startOfSql);  // 创建SQL构建器
      sql.append('\n');  // 添加换行

      String line;
      if (!startOfSql.trim().endsWith(";")) {  // 如果不以分号结尾
        while ((line = in.readLine()) != null) {  // 读取行
          sql.append(line).append('\n');  // 追加行和换行
          if (line.trim().endsWith(";")) {  // 如果以分号结尾
            break;  // 停止读取
          }
        }
      }

      line = sql.toString().trim();  // 转换为字符串并去除空格
      if (scriptHasVars && line.contains("$")) {  // 如果有变量
        line = vars.expand(line);  // 展开变量
      }
      return line;  // 返回SQL
    }
  }


  /** 打印命令。
   *
   * <p>执行时，@print命令定义任何后续@fetch或@select命令如何处理其结果行。
   * MTSQL可以打印所有行、不打印任何行或每第n行打印一行。
   * 打印的行可以以序列号和/或接收时间为前缀（这与rowtime不同，rowtime通常表示插入时间）。
   */
  private class PrintCommand extends AbstractCommand {
    // 每第n行打印一次：1表示所有行，0表示不打印任何行
    private final int nth;
    private final boolean count;    // 打印序列号
    private final boolean time;     // 打印行被获取的时间
    // 打印总行数和经过的获取时间：
    private final boolean total;
    // TODO: 更多格式控制

    /**
     * 构造打印命令。
     * @param spec 打印规格，可以是：none、all、total、count、time、every N
     */
    PrintCommand(String spec) {
      int nth = 0;
      boolean count = false;
      boolean time = false;
      boolean total = false;
      StringTokenizer tokenizer = new StringTokenizer(spec);  // 创建分词器

      if (tokenizer.countTokens() == 0) {  // 如果没有参数
        // 纯"@print"表示"@print all"
        nth = 1;
      } else {
        while (tokenizer.hasMoreTokens()) {  // 遍历所有token
          String token = tokenizer.nextToken();
          if (token.equalsIgnoreCase("none")) {  // 不打印任何行
            nth = 0;
          } else if (token.equalsIgnoreCase("all")) {  // 打印所有行
            nth = 1;
          } else if (token.equalsIgnoreCase("total")) {  // 打印总计信息
            total = true;
          } else if (token.equalsIgnoreCase("count")) {  // 打印行号
            count = true;
          } else if (token.equalsIgnoreCase("time")) {  // 打印时间戳
            time = true;
          } else if (token.equalsIgnoreCase("every")) {  // 每N行打印一次
            nth = 1;
            if (tokenizer.hasMoreTokens()) {  // 如果有下一个token
              token = tokenizer.nextToken();
              nth = parseInt(token);  // 解析N
            }
          }
        }
      }
      this.nth = nth;  // 设置nth
      this.count = count;  // 设置count
      this.time = time;  // 设置time
      this.total = total;  // 设置total
    }

    /**
     * 执行打印命令。
     * @param executor 命令执行器
     */
    protected void doExecute(ConcurrentTestCommandExecutor executor) {
      Integer threadId = executor.getThreadId();  // 获取线程ID
      BufferedWriter out = threadBufferedWriters.get(threadId);  // 获取输出写入器
      threadResultsReaders.put(  // 创建并设置新的结果读取器
          threadId, new ResultsReader(out, nth, count, time, total));
    }
  }

  /** 回显命令：输出消息到测试结果。 */
  private class EchoCommand extends AbstractCommand {
    private final String msg;  // 要输出的消息

    private EchoCommand(String msg) {
      this.msg = msg;  // 设置消息
    }

    /**
     * 执行回显命令。
     * @param executor 命令执行器
     */
    protected void doExecute(ConcurrentTestCommandExecutor executor) {
      storeMessage(executor.getThreadId(), msg);  // 存储消息
    }
  }

  /** 插件命令：执行插件定义的自定义命令。 */
  private class PluginCommand extends AbstractCommand {

    private final ConcurrentTestPluginCommand pluginCommand;  // 插件命令实例

    /**
     * 构造插件命令。
     * @param command 命令名称
     * @param params 命令参数
     */
    private PluginCommand(String command, String params) {
      ConcurrentTestPlugin plugin = pluginForCommand.get(command);  // 获取插件
      pluginCommand = plugin.getCommandFor(command, params);  // 获取命令实例
    }

    /**
     * 执行插件命令。
     * @param exec 命令执行器
     * @throws Exception 如果执行时发生错误
     */
    protected void doExecute(final ConcurrentTestCommandExecutor exec)
        throws Exception {
      ConcurrentTestPluginCommand.TestContext context =  // 创建测试上下文
          new ConcurrentTestPluginCommand.TestContext() {
            public void storeMessage(String message) {  // 存储消息
              ConcurrentTestCommandScript.this.storeMessage(
                  exec.getThreadId(), message);
            }

            public Connection getConnection() {  // 获取连接
              return exec.getConnection();
            }

            public @Nullable Statement getCurrentStatement() {  // 获取当前语句
              return exec.getStatement();
            }
          };
      pluginCommand.execute(context);  // 执行插件命令
    }
  }

  // 匹配shell通配符和其他特殊字符：当命令包含这些字符时，需要shell来运行它
  private final Pattern shellWildcardPattern = Pattern.compile("[*?$|<>&]");

  // REVIEW mb 2/24/09 (Mardi Gras) 这应该有超时吗？
  /** Shell命令：执行shell命令。 */
  private class ShellCommand extends AbstractCommand {
    private final String command;  // 原始命令字符串
    private final List<String> argv;      // 命令，已解析和处理

    /**
     * 构造Shell命令。
     * @param command 要执行的命令
     */
    private ShellCommand(String command) {
      this.command = command;  // 保存原始命令
      boolean needShell = hasWildcard(command);  // 检查是否需要shell
      if (needShell) {  // 如果需要shell
        argv = new ArrayList<>();  // 创建参数列表
        argv.add("/bin/sh");  // 添加shell
        argv.add("-c");  // 添加-c选项
        argv.add(command);  // 添加命令
      } else {  // 否则直接执行
        argv = tokenize(command);  // 分词
      }
    }

    /**
     * 检查命令是否包含通配符。
     * @param command 命令字符串
     * @return true如果包含通配符
     */
    private boolean hasWildcard(String command) {
      return shellWildcardPattern.matcher(command).find();  // 查找通配符
    }

    /**
     * 将字符串分词为参数列表。
     * @param s 要分词的字符串
     * @return 参数列表
     */
    private List<String> tokenize(String s) {
      List<String> result = new ArrayList<>();  // 创建结果列表
      StringTokenizer tokenizer = new StringTokenizer(s);  // 创建分词器
      while (tokenizer.hasMoreTokens()) {  // 遍历token
        result.add(tokenizer.nextToken());  // 添加token
      }
      return result;  // 返回列表
    }

    /**
     * 执行Shell命令。
     * @param executor 命令执行器
     */
    protected void doExecute(ConcurrentTestCommandExecutor executor) {
      Integer threadId = executor.getThreadId();  // 获取线程ID
      storeMessage(threadId, command);  // 存储命令

      try {
        // argv[0]在$PATH中查找。
        // 工作目录是脚本的主目录。
        // 将stdout和stderr重定向到threadWriter
        int status =  // 运行进程
            TestUnsafe.runAppProcess(argv, scriptDirectory, null, null,
                getThreadWriter(threadId));
        if (status != 0) {  // 如果状态非零
          storeMessage(threadId,  // 存储错误消息
              "command " + command + ": exited with status " + status);
        }
      } catch (Exception e) {  // 捕获异常
        storeMessage(threadId,  // 存储异常消息
            "command " + command + ": failed with exception " + e.getMessage());
      }
    }
  }

  /** 带超时的命令。 */
  // TODO: 用super.CommmandWithTimeout替换
  private abstract static class CommandWithTimeout extends AbstractCommand {
    private final long timeout;  // 超时时间（毫秒）

    /**
     * 构造带超时的命令。
     * @param timeout 超时时间（毫秒）
     */
    private CommandWithTimeout(long timeout) {
      this.timeout = timeout;  // 设置超时
    }

    // 返回设置的超时（-1表示无超时）
    protected long setTimeout(Statement stmt) throws SQLException {
      assert timeout >= 0;  // 断言超时>=0
      if (timeout > 0) {  // 如果超时>0
        // FIX: 当可用时调用setQueryTimeoutMillis()。
        assert timeout >= 1000 : "timeout too short";  // 断言超时足够长
        int t = (int) (timeout / 1000);  // 转换为秒
        stmt.setQueryTimeout(t);  // 设置查询超时
        return t;  // 返回超时
      }
      return -1;  // 返回-1表示无超时
    }
  }

/** 带超时和行限制的命令。 */
  private abstract static class CommandWithTimeoutAndRowLimit
      extends CommandWithTimeout {
    private final int rowLimit;  // 行限制

    /**
     * 构造带超时和行限制的命令。
     * @param timeout 超时时间（毫秒）
     * @param rowLimit 行限制
     */
    private CommandWithTimeoutAndRowLimit(long timeout, int rowLimit) {
      super(timeout);  // 调用父类构造函数
      this.rowLimit = rowLimit;  // 设置行限制
    }

    /**
     * 设置行限制。
     * @param stmt 语句对象
     * @throws SQLException 如果设置失败
     */
    protected void setRowLimit(Statement stmt) throws SQLException {
      assert rowLimit >= 0;  // 断言行限制>=0
      if (rowLimit > 0) {  // 如果行限制>0
        stmt.setMaxRows(rowLimit);  // 设置最大行数
      }
    }
  }

  /**
   * SelectCommand创建并执行SQL SELECT语句，可选地带有超时和行限制。
   */
  private class SelectCommand extends CommandWithTimeoutAndRowLimit {
    private final String sql;  // SQL语句

    /**
     * 构造SELECT命令。
     * @param sql SQL语句
     */
    private SelectCommand(String sql) {
      this(sql, 0, 0);  // 调用完整构造函数，无超时和行限制
    }

    /**
     * 构造预期失败的SELECT命令。
     * @param sql SQL语句
     * @param errorExpected 是否预期失败
     */
    private SelectCommand(String sql, boolean errorExpected) {
      this(sql, 0, 0);  // 调用基本构造函数
      if (errorExpected) {  // 如果预期失败
        this.markToFail();  // 标记为失败
      }
    }

    /**
     * 构造带超时的SELECT命令。
     * @param sql SQL语句
     * @param timeout 超时时间（毫秒）
     */
    private SelectCommand(String sql, long timeout) {
      this(sql, timeout, 0);  // 调用完整构造函数，无行限制
    }

    /**
     * 构造带超时和行限制的SELECT命令。
     * @param sql SQL语句
     * @param timeout 超时时间（毫秒）
     * @param rowLimit 行限制
     */
    private SelectCommand(String sql, long timeout, int rowLimit) {
      super(timeout, rowLimit);  // 调用父类构造函数
      this.sql = sql;  // 保存SQL
    }

    /**
     * 执行SELECT命令。
     * @param executor 命令执行器
     * @throws SQLException 如果执行失败
     */
    protected void doExecute(ConcurrentTestCommandExecutor executor)
        throws SQLException {
      // TODO: 在构造函数中trim和chop；将sql存储在基类中；
      // execute()调用storeSql。
      String properSql = sql.trim();  // 去除空格

      storeSql(  // 存储SQL
          executor.getThreadId(),
          properSql);

      if (properSql.endsWith(";")) {  // 如果以分号结尾
        properSql = properSql.substring(0, properSql.length() - 1);  // 去掉分号
      }

      PreparedStatement stmt =  // 创建预编译语句
          executor.getConnection().prepareStatement(properSql);
      long timeout = setTimeout(stmt);  // 设置超时
      setRowLimit(stmt);  // 设置行限制

      try {
        storeResults(  // 存储结果
            executor.getThreadId(),
            stmt.executeQuery(),
            timeout);
      } finally {
        stmt.close();  // 关闭语句
      }
    }
  }

  /**
   * SqlCommand创建并执行SQL语句（非SELECT），可选地带有超时。
   */
  private class SqlCommand extends CommandWithTimeout {
    private final String sql;  // SQL语句

    /**
     * 构造SQL命令。
     * @param sql SQL语句
     */
    private SqlCommand(String sql) {
      super(0);  // 无超时
      this.sql = sql;  // 保存SQL
    }

    /**
     * 构造预期失败的SQL命令。
     * @param sql SQL语句
     * @param errorExpected 是否预期失败
     */
    private SqlCommand(String sql, boolean errorExpected) {
      super(0);  // 无超时
      this.sql = sql;  // 保存SQL
      if (errorExpected) {  // 如果预期失败
        this.markToFail();  // 标记为失败
      }
    }

    /**
     * 构造带超时的SQL命令。
     * @param sql SQL语句
     * @param timeout 超时时间（毫秒）
     */
    private SqlCommand(String sql, long timeout) {
      super(timeout);  // 设置超时
      this.sql = sql;  // 保存SQL
    }

    /**
     * 执行SQL命令。
     * @param executor 命令执行器
     * @throws SQLException 如果执行失败
     */
    protected void doExecute(ConcurrentTestCommandExecutor executor)
        throws SQLException {
      String properSql = sql.trim();  // 去除空格

      storeSql(  // 存储SQL
          executor.getThreadId(),
          properSql);

      if (properSql.endsWith(";")) {  // 如果以分号结尾
        properSql = properSql.substring(0, properSql.length() - 1);  // 去掉分号
      }

      if (properSql.equalsIgnoreCase("commit")) {  // 如果是commit
        executor.getConnection().commit();  // 提交事务
        return;
      } else if (properSql.equalsIgnoreCase("rollback")) {  // 如果是rollback
        executor.getConnection().rollback();  // 回滚事务
        return;
      }

      PreparedStatement stmt =  // 创建预编译语句
          executor.getConnection().prepareStatement(properSql);
      long timeout = setTimeout(stmt);  // 设置超时
      boolean timeoutSet = timeout >= 0;  // 是否设置了超时

      try {
        boolean haveResults = stmt.execute();  // 执行语句
        if (haveResults) {  // 如果有结果
          // Farrago将"call"语句重写为select。
          storeMessage(  // 存储消息
              executor.getThreadId(),
              "0 rows affected.");
          // ResultSet中有什么有趣的内容吗？
        } else {  // 如果没有结果
          int rows = stmt.getUpdateCount();  // 获取更新行数
          if (rows != 1) {  // 如果不是1行
            storeMessage(executor.getThreadId(), rows + " rows affected.");  // 存储行数
          } else {  // 如果是1行
            storeMessage(  // 存储消息
                executor.getThreadId(),
                "1 row affected.");
          }
        }
      } catch (SqlTimeoutException e) {  // 捕获超时异常
        if (!timeoutSet) {  // 如果没有设置超时
          throw e;  // 重新抛出
        }

        Util.swallow(e, null);  // 吞掉异常
        storeMessage(  // 存储超时消息
            executor.getThreadId(),
            "Timeout");
      } finally {
        stmt.close();  // 关闭语句
      }
    }
  }

  /**
   * PrepareCommand创建一个{@link PreparedStatement}，它被保存为其测试线程的当前语句。
   * 对于预编译查询（SELECT或带结果的CALL），后续的FetchAndPrintCommand执行该语句
   * 并获取其结果，直到数据结束或超时。PrintCommand附加一个监听器，为每行调用，
   * 该监听器选择要保存和打印的行，并设置格式。默认情况下，如果在FetchAndPrintCommand
   * 之前没有出现PrintCommand，则打印所有行。CloseCommand关闭并丢弃预编译语句。
   */
  private class PrepareCommand extends AbstractCommand {
    private final String sql;  // SQL语句

    /**
     * 构造准备命令。
     * @param sql SQL语句
     */
    private PrepareCommand(String sql) {
      this.sql = sql;  // 保存SQL
    }

    /**
     * 执行准备命令。
     * @param executor 命令执行器
     * @throws SQLException 如果准备失败
     */
    protected void doExecute(ConcurrentTestCommandExecutor executor)
        throws SQLException {
      String properSql = sql.trim();  // 去除空格

      storeSql(  // 存储SQL
          executor.getThreadId(),
          properSql);

      if (properSql.endsWith(";")) {  // 如果以分号结尾
        properSql = properSql.substring(0, properSql.length() - 1);  // 去掉分号
      }

      PreparedStatement stmt =  // 创建预编译语句
          executor.getConnection().prepareStatement(properSql);

      executor.setStatement(stmt);  // 设置为当前语句
    }
  }

  /**
   * FetchAndPrintCommand执行存储在ConcurrentTestCommandExecutor中的先前准备的语句，
   * 然后输出返回的行。
   */
  private class FetchAndPrintCommand extends CommandWithTimeout {
    /**
     * 构造获取和打印命令。
     * @param timeout 超时时间（毫秒）
     */
    private FetchAndPrintCommand(long timeout) {
      super(timeout);  // 设置超时
    }

    /**
     * 执行获取和打印命令。
     * @param executor 命令执行器
     * @throws SQLException 如果执行失败
     */
    protected void doExecute(ConcurrentTestCommandExecutor executor)
        throws SQLException {
      PreparedStatement stmt =  // 获取当前语句
          (PreparedStatement) executor.getStatement();
      long timeout = setTimeout(stmt);  // 设置超时

      storeResults(  // 存储结果
          executor.getThreadId(),
          stmt.executeQuery(),
          timeout);
    }
  }

  /** 结果读取器：读取ResultSet并以表格格式输出。 */

    private class ResultsReader {

      private final PrintWriter out;  // 输出写入器

      // 每第N行打印一次。1表示所有行，0表示不打印任何行

      private final int nth;

      // 为打印的行添加序列号前缀

      private final boolean counted;

      // 为打印的行添加获取时间前缀

      private final boolean timestamped;

      // 打印最终摘要，包括行数和经过时间

      private final boolean totaled;

  

      private final long baseTime;  // 基准时间（脚本开始时间）

      private int rowCount = 0;  // 总行数

      private int ncols = 0;  // 列数

      private int[] widths;  // 每列的宽度

      private String[] labels;  // 列标签

  

      /**

       * 构造结果读取器（默认打印所有行）。

       * @param out 缓冲写入器

       */

      ResultsReader(BufferedWriter out) {

        this(out, 1, false, false, false);  // 调用完整构造函数

      }

  

      /**

       * 构造结果读取器。

       * @param out 缓冲写入器

       * @param nth 每第n行打印一次

       * @param counted 是否打印行号

       * @param timestamped 是否打印时间戳

       * @param totaled 是否打印总计

       */

      ResultsReader(

          BufferedWriter out,

          int nth, boolean counted, boolean timestamped, boolean totaled) {

        this.out = new PrintWriter(out);  // 创建PrintWriter

        this.nth = nth;  // 设置nth

        this.counted = counted;  // 设置counted

        this.timestamped = timestamped;  // 设置timestamped

        this.totaled = totaled;  // 设置totaled

        this.baseTime = scriptStartTime;  // 设置基准时间

      }

  

      /**

       * 准备格式：分析ResultSet元数据以确定列宽和标签。

       * @param rset 结果集

       * @throws SQLException 如果获取元数据失败

       */

      void prepareFormat(ResultSet rset) throws SQLException {

        ResultSetMetaData meta = rset.getMetaData();  // 获取元数据

        ncols = meta.getColumnCount();  // 获取列数

        widths = new int[ncols];  // 创建宽度数组

        labels = new String[ncols];  // 创建标签数组

        for (int i = 0; i < ncols; i++) {  // 遍历所有列

          labels[i] = meta.getColumnLabel(i + 1);  // 获取列标签

          int displaySize = meta.getColumnDisplaySize(i + 1);  // 获取显示大小

  

          // NOTE jvs 13-June-2006: 我添加这个来限制EXPLAIN PLAN，

          // 它现在返回一个非常大的最坏情况显示大小。

          if (displaySize > 4096) {  // 如果显示大小过大

            displaySize = 0;  // 设置为0（使用标签长度）

          }

          widths[i] = Math.max(labels[i].length(), displaySize);  // 计算列宽

        }

      }

  

      /**

       * 打印表头。

       */

      private void printHeaders() {

        printSeparator();  // 打印分隔线

        indent();  // 缩进

        printRow(labels);  // 打印标签行

        printSeparator();  // 打印分隔线

      }

  

      /**

       * 读取结果集并格式化输出。

       * @param rset 结果集

       * @param timeout 超时时间（毫秒），-1表示无超时

       * @throws SQLException 如果读取失败

       */

      void read(ResultSet rset, long timeout) throws SQLException {

        boolean withTimeout = timeout >= 0;  // 是否有超时

        boolean timedOut = false;  // 是否超时

        long startTime = 0;  // 开始时间

        long endTime = 0;  // 结束时间

        try {

          prepareFormat(rset);  // 准备格式

          String[] values = new String[ncols];  // 创建值数组

          int printedRowCount = 0;  // 已打印行数

          if (nth > 0) {  // 如果需要打印

            printHeaders();  // 打印表头

          }

          startTime = System.currentTimeMillis();  // 记录开始时间

          for (rowCount = 0; rset.next(); rowCount++) {  // 遍历所有行

            if (nth == 0) {  // 如果不打印任何行

              continue;  // 跳过

            }

            if (nth == 1 || rowCount % nth == 0) {  // 如果需要打印这一行

              long time = System.currentTimeMillis();  // 获取当前时间

              if (printedRowCount > 0  // 如果已打印过行

                  && (printedRowCount % 100 == 0)) {  // 且每100行

                printHeaders();  // 重新打印表头

              }

              for (int i = 0; i < ncols; i++) {  // 遍历所有列

                values[i] = rset.getString(i + 1);  // 获取列值

              }

              if (counted) {  // 如果需要打印行号

                printRowCount(rowCount);  // 打印行号

              }

              if (timestamped) {  // 如果需要打印时间戳

                printTimestamp(time);  // 打印时间戳

              }

              printRow(values);  // 打印行

              printedRowCount++;  // 增加已打印行数

            }

          }

        } catch (SqlTimeoutException e) {  // 捕获SQL超时异常

          endTime = System.currentTimeMillis();  // 记录结束时间

          timedOut = true;  // 标记为超时

          if (!withTimeout) {  // 如果没有设置超时

            throw e;  // 重新抛出

          }

  

          Util.swallow(e, null);  // 吞掉异常

        } catch (SQLException e) {  // 捕获其他SQL异常

          endTime = System.currentTimeMillis();  // 记录结束时间

          timedOut = true;  // 标记为超时

  

          // 2007-10-23 hersker: hack to ignore timeout exceptions

          // from other Farrago projects without being able to

          // import/reference the actual exceptions

          final String eClassName = e.getClass().getName();  // 获取异常类名

          if (eClassName.endsWith("TimeoutException")) {  // 如果是超时异常

            if (!withTimeout) {  // 如果没有设置超时

              throw e;  // 重新抛出

            }

            Util.swallow(e, null);  // 吞掉异常

          } else {  // 其他异常

            Util.swallow(e, null);  // 吞掉异常

            out.println(e.getMessage());  // 打印错误消息

          }

        } catch (RuntimeException e) {  // 捕获运行时异常

          e.printStackTrace();  // 打印堆栈跟踪

          throw e;  // 重新抛出

        } finally {

          if (endTime == 0) {  // 如果没有记录结束时间

            endTime = System.currentTimeMillis();  // 记录结束时间

          }

          rset.close();  // 关闭结果集

          if (nth > 0) {  // 如果打印了行

            printSeparator();  // 打印分隔线

            out.println();  // 空行

          }

          if (verbose) {  // 如果启用详细模式

            out.printf(Locale.ROOT, "fetch started at %tc %d, %s at %tc %d%n",  // 打印时间信息

                startTime, startTime,

                timedOut ? "timeout" : "eos",

                endTime, endTime);

          }

          if (totaled) {  // 如果需要打印总计

            long dt = endTime - startTime;  // 计算经过时间

            if (withTimeout) {  // 如果有超时

              dt -= timeout;  // 减去超时时间

            }

            assert dt >= 0;  // 断言时间>=0

            out.printf(Locale.ROOT, "fetched %d rows in %d msecs %s%n",  // 打印总计

                rowCount, dt, timedOut ? "(timeout)" : "(end)");

          }

        }

      }

    private void printRowCount(int count) {

          out.printf(Locale.ROOT, "(%06d) ", count);  // 打印6位行号

        }

    

        /**

         * 打印时间戳。

         * @param time 时间戳（毫秒）

         */

        private void printTimestamp(long time) {

          time -= baseTime;  // 计算相对时间

    

          out.printf(Locale.ROOT, "(% 4d.%03d) ", time / 1000, time % 1000);  // 打印秒.毫秒

        }

    

        // 缩进标题或分隔线以匹配行值线

        private void indent() {

          if (counted) {  // 如果打印行号

            out.print("         ");  // 行号占位符

          }

          if (timestamped) {  // 如果打印时间戳

            out.print("           ");  // 时间戳占位符

          }

        }

    

        /**

         * 打印输出表格分隔线。类似于<code>"+----+--------+"</code>。

         */

        private void printSeparator() {

          indent();  // 缩进

          for (int i = 0; i < widths.length; i++) {  // 遍历所有列

            if (i > 0) {  // 如果不是第一列

              out.write("-+-");  // 列分隔符

            } else {  // 第一列

              out.write("+-");  // 起始分隔符

            }

    

            int numDashes = widths[i];  // 破折号数量

            while (numDashes > 0) {  // 循环输出破折号

              out.write(  // 写入破折号

                  DASHES,

                  0,

                  Math.min(numDashes, BUF_SIZE));

              numDashes -= Math.min(numDashes, BUF_SIZE);  // 减少剩余数量

            }

          }

          out.println("-+");  // 结束分隔符并换行

        }

    

        /**

         * 打印输出表格行。类似于<code>"| COL1 | COL2 |"</code>。

         * @param values 列值数组

         */

        private void printRow(String[] values) {

          for (int i = 0; i < values.length; i++) {  // 遍历所有列

            String value = values[i];  // 获取列值

            if (value == null) {  // 如果值为null

              value = "";  // 使用空字符串

            }

            if (i > 0) {  // 如果不是第一列

              out.write(" | ");  // 列分隔符

            } else {  // 第一列

              out.write("| ");  // 起始分隔符

            }

            out.write(value);  // 写入值

            int excess = widths[i] - value.length();  // 计算需要填充的空格数

            while (excess > 0) {  // 循环填充空格

              out.write(  // 写入空格

                  SPACES,

                  0,

                  Math.min(excess, BUF_SIZE));

              excess -= Math.min(excess, BUF_SIZE);  // 减少剩余数量

            }

          }

          out.println(" |");  // 结束行并换行

        }

      }


  /** 独立客户端测试工具：命令行工具，用于执行并发测试脚本。 */
  private static class Tool {
    boolean quiet = false;          // -q 安静模式
    boolean verbose = false;        // -v 详细模式
    boolean debug = false;          // -g 调试模式
    String server;                  // -u 服务器URL
    String driver;                  // -d JDBC驱动类
    @Nullable String user;                    // -n 用户名
    @Nullable String password;                // -p 密码
    final List<String> bindings = new ArrayList<>(); // VAR=VAL 变量绑定
    final List<String> files = new ArrayList<>(); // FILE 脚本文件列表

    Tool() {
    }

    // 成功返回0，错误返回1，错误调用返回2
    public int run(String[] args) {
      try (PrintWriter w = Util.printWriter(System.out)) {  // 创建输出写入器
        if (!parseCommand(args)) {  // 解析命令行参数
          usage();  // 打印用法
          return 2;  // 返回错误调用代码
        }

        Class z = Class.forName(driver); // 加载驱动
        Properties jdbcProps = new Properties();  // 创建JDBC属性
        if (user != null) {  // 如果有用户名
          jdbcProps.setProperty("user", user);  // 设置用户
        }
        if (password != null) {  // 如果有密码
          jdbcProps.setProperty("password", password);  // 设置密码
        }

        for (String file : files) {  // 遍历所有脚本文件
          ConcurrentTestCommandScript script =  // 创建脚本实例
              new ConcurrentTestCommandScript();
          try {
            script.setQuiet(quiet);  // 设置安静模式
            script.setVerbose(verbose);  // 设置详细模式
            script.setDebug(debug);  // 设置调试模式
            script.prepare(file, bindings);  // 准备脚本
            script.setDataSource(server, jdbcProps);  // 设置数据源
            script.execute();  // 执行脚本
          } finally {
            if (!quiet) {  // 如果不是安静模式
              script.printResults(w);  // 打印结果
            }
          }
        }
      } catch (Exception e) {  // 捕获异常
        System.err.println(e.getMessage());  // 打印错误消息
        return 1;  // 返回错误代码
      }
      return 0;  // 返回成功代码
    }

    /**
     * 打印用法信息。
     */
    static void usage() {
      System.err.println(  // 打印用法
          "Usage: mtsql [-vg] -u SERVER -d DRIVER "
          + "[-n USER][-p PASSWORD] SCRIPT [SCRIPT]...");
    }

    /**
     * 解析命令行参数。
     * @param args 命令行参数数组
     * @return true如果解析成功，false否则
     */
    boolean parseCommand(String[] args) {
      try {
        // 对顺序非常宽容
        for (int i = 0; i < args.length;) {  // 遍历所有参数
          String arg = args[i++];  // 获取参数
          if (arg.charAt(0) == '-') {  // 如果是选项
            switch (arg.charAt(1)) {  // 检查选项字符
            case 'v':  // -v 详细模式
              verbose = true;
              break;
            case 'q':  // -q 安静模式
              quiet = true;
              break;
            case 'g':  // -g 调试模式
              debug = true;
              break;
            case 'u':  // -u 服务器
              this.server = args[i++];
              break;
            case 'd':  // -d 驱动
              this.driver = args[i++];
              break;
            case 'n':  // -n 用户名
              this.user = args[i++];
              break;
            case 'p':  // -p 密码
              this.password = args[i++];
              break;
            default:
              return false;  // 未知选项
            }
          } else if (arg.contains("=")) {  // 如果包含等号（变量绑定）
            if (Character.isJavaIdentifierStart(arg.charAt(0))) {  // 检查是否是有效的Java标识符
              bindings.add(arg);  // 添加绑定
            } else {
              return false;  // 无效的变量名
            }
          } else {  // 否则是文件名
            files.add(arg);  // 添加文件
          }
        }
        if (server == null || driver == null) {  // 检查必需参数
          return false;  // 缺少必需参数
        }
      } catch (Throwable th) {  // 捕获任何异常
        return false;  // 解析失败
      }
      return true;  // 解析成功
    }
  }

  /**
   * 客户端工具，通过JDBC连接并在该连接上运行一个或多个mtsql脚本。
   *
   * <p>用法: mtsql [-vgq] -u SERVER -d DRIVER [-n USER][-p PASSWORD]
   * [VAR=VAL]...  SCRIPT [SCRIPT]...
   *
   * <p>参数说明：
   * <ul>
   * <li>-v: 详细模式，输出详细执行信息</li>
   * <li>-g: 调试模式</li>
   * <li>-q: 安静模式，不输出结果</li>
   * <li>-u SERVER: JDBC连接URL</li>
   * <li>-d DRIVER: JDBC驱动类名</li>
   * <li>-n USER: 数据库用户名</li>
   * <li>-p PASSWORD: 数据库密码</li>
   * <li>VAR=VAL: 变量绑定，用于替换脚本中的变量</li>
   * <li>SCRIPT: 要执行的脚本文件</li>
   * </ul>
   *
   * @param args 命令行参数
   */
  public static void main(String[] args) {
    int status = new Tool().run(args);  // 运行工具并获取状态
    Unsafe.systemExit(status);  // 退出系统
  }
}
