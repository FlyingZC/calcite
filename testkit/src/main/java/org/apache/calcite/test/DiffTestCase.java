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
 */ // Apache许可证头,声明代码版权和使用条款
package org.apache.calcite.test; // 包声明,该类属于org.apache.calcite.test包

import org.apache.calcite.util.ReflectUtil; // 导入反射工具类,用于获取类名等反射操作
import org.apache.calcite.util.TestUtil; // 导入测试工具类,提供异常重抛等功能
import org.apache.calcite.util.Util; // 导入通用工具类,提供文件读取等实用方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入空值检查注解,标记可能为null的字段
import org.incava.diff.Diff; // 导入差异比较工具,用于计算两个列表的差异
import org.incava.diff.Difference; // 导入差异对象,表示具体的一个差异
import org.junit.jupiter.api.AfterEach; // 导入JUnit5注解,标记在每个测试方法之后执行的方法
import org.junit.jupiter.api.BeforeEach; // 导入JUnit5注解,标记在每个测试方法之前执行的方法

import java.io.BufferedReader; // 导入缓冲字符输入流,用于高效读取文本
import java.io.ByteArrayOutputStream; // 导入字节数组输出流,用于在内存中构建字节数据
import java.io.File; // 导入文件类,用于文件和目录路径的抽象表示
import java.io.FileInputStream; // 导入文件输入流,用于从文件读取字节
import java.io.IOException; // 导入IO异常类,处理输入输出错误
import java.io.LineNumberReader; // 导入带行号的字符输入流,可以跟踪读取的行号
import java.io.OutputStream; // 导入输出流抽象类,用于写入字节数据
import java.io.OutputStreamWriter; // 导入输出流字符转换器,将字符流转换为字节流
import java.io.StringWriter; // 导入字符串写入器,用于在内存中构建字符串
import java.io.Writer; // 导入字符写入流抽象类,用于写入字符数据
import java.nio.charset.StandardCharsets; // 导入标准字符集常量,指定UTF-8编码
import java.nio.file.Files; // 导入文件工具类,提供文件操作方法
import java.util.ArrayList; // 导入动态数组列表,存储可变长度的元素集合
import java.util.List; // 导入列表接口,表示有序的元素集合
import java.util.regex.Matcher; // 导入正则匹配器,用于执行正则表达式匹配操作
import java.util.regex.Pattern; // 导入正则表达式模式,用于编译正则表达式

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest匹配器,用于断言相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言方法,执行匹配断言
import static org.junit.jupiter.api.Assertions.fail; // 导入JUnit5断言方法,使测试失败

/**
 * DiffTestCase is an abstract base for JUnit tests which produce multi-line
 * output to be verified by diffing against a pre-existing reference file.
 */ // 类注释:DiffTestCase是JUnit测试的抽象基类,用于产生多行输出并通过与预先存在的参考文件进行差异比较来验证
public abstract class DiffTestCase { // 定义抽象类DiffTestCase,作为基于差异比较的测试用例基类
  //~ Instance fields -------------------------------------------------------- // 实例字段区域分隔符

  private final String testCaseName; // 测试用例名称,用于标识当前测试用例,final表示一旦初始化不可修改

  /**
   * Name of current .log file.
   */ // 字段注释:当前.log文件的名称
  protected @Nullable File logFile; // 日志文件对象,存储测试输出的实际结果,protected允许子类访问,@Nullable表示可能为null

  /**
   * Name of current .ref file.
   */ // 字段注释:当前.ref文件的名称
  protected @Nullable File refFile; // 参考文件对象,存储预期的测试结果,protected允许子类访问,@Nullable表示可能为null

  /**
   * OutputStream for current test log.
   */ // 字段注释:当前测试日志的输出流
  protected @Nullable OutputStream logOutputStream; // 日志输出流,用于向日志文件写入测试数据,protected允许子类访问,@Nullable表示可能为null

  /** Diff masks defined so far. */ // 字段注释:已定义的差异掩码
  private String diffMasks; // 差异掩码字符串,存储所有正则表达式模式,用"|"连接,用于在比较前屏蔽匹配的内容
  @Nullable Pattern compiledDiffPattern; // 编译后的差异掩码正则表达式模式对象,用于高效匹配,@Nullable表示可能为null
  @Nullable Matcher compiledDiffMatcher; // 编译后的差异掩码匹配器对象,用于执行匹配操作,@Nullable表示可能为null
  private String ignorePatterns; // 忽略模式字符串,存储所有需要完全忽略的行匹配正则表达式,用"|"连接
  @Nullable Pattern compiledIgnorePattern; // 编译后的忽略模式正则表达式模式对象,@Nullable表示可能为null
  @Nullable Matcher compiledIgnoreMatcher; // 编译后的忽略模式匹配器对象,@Nullable表示可能为null

  /**
   * Whether to give verbose message if diff fails.
   */ // 字段注释:当差异比较失败时是否提供详细消息
  private boolean verbose; // 详细模式标志,为true时在差异失败时输出完整的差异信息,否则只输出简单错误信息

  /**
   * Initializes a new DiffTestCase.
   *
   * @param testCaseName Test case name
   */ // 方法注释:初始化一个新的DiffTestCase实例
  protected DiffTestCase(String testCaseName) { // 构造方法,接收测试用例名称作为参数,protected允许子类调用
    this.testCaseName = testCaseName; // 将参数赋值给实例字段testCaseName,保存测试用例名称
    // diffMasks = new ArrayList(); // 注释掉的代码,原计划使用ArrayList存储掩码,现在改用字符串
    diffMasks = ""; // 初始化差异掩码字符串为空字符串
    ignorePatterns = ""; // 初始化忽略模式字符串为空字符串
    compiledIgnoreMatcher = null; // 初始化忽略模式匹配器为null
    compiledDiffMatcher = null; // 初始化差异掩码匹配器为null
    String verboseVal = // 从系统属性中获取详细模式配置值,属性名为"org.apache.calcite.test.DiffTestCase.verbose"
        System.getProperty(DiffTestCase.class.getName() + ".verbose");
    if (verboseVal != null) { // 如果系统属性存在且不为null
      verbose = true; // 将详细模式标志设置为true,启用详细输出
    }
  }

  //~ Methods ---------------------------------------------------------------- // 方法区域分隔符

  @BeforeEach // JUnit5注解,标记在每个测试方法执行前调用此方法
  protected void setUp() { // 测试前设置方法,用于初始化测试环境
    // diffMasks.clear(); // 注释掉的代码,原计划清空ArrayList
    diffMasks = ""; // 重置差异掩码字符串为空字符串
    ignorePatterns = ""; // 重置忽略模式字符串为空字符串
    compiledIgnoreMatcher = null; // 重置忽略模式匹配器为null
    compiledDiffMatcher = null; // 重置差异掩码匹配器为null
  }

  @AfterEach // JUnit5注解,标记在每个测试方法执行后调用此方法
  protected void tearDown() throws IOException { // 测试后清理方法,用于释放资源,可能抛出IO异常
    if (logOutputStream != null) { // 如果日志输出流不为null
      logOutputStream.close(); // 关闭日志输出流,释放文件资源
      logOutputStream = null; // 将日志输出流引用设置为null,帮助垃圾回收
    }
  }

  /**
   * Initializes a diff-based test. Any existing .log and .dif files
   * corresponding to this test case are deleted, and a new, empty .log file
   * is created. The default log file location is a subdirectory under the
   * result getTestlogRoot(), where the subdirectory name is based on the
   * unqualified name of the test class. The generated log file name will be
   * testMethodName.log, and the expected reference file will be
   * testMethodName.ref.
   *
   * @return Writer for log file, which caller should use as a destination for
   * test output to be diffed
   */ // 方法注释:初始化基于差异比较的测试,删除现有的.log和.dif文件,创建新的空.log文件
  protected Writer openTestLog() throws Exception { // 打开测试日志方法,返回日志文件的写入器,可能抛出异常
    File testClassDir = // 创建测试类目录对象,路径为getTestlogRoot()/类简单名称
        new File(getTestlogRoot(),
            ReflectUtil.getUnqualifiedClassName(getClass())); // 使用反射获取当前类的简单名称(不含包名)
    //noinspection ResultOfMethodCallIgnored // 忽略mkdirs返回值的检查,因为目录可能已存在
    testClassDir.mkdirs(); // 创建测试类目录,包括所有必要的父目录
    File testLogFile = // 创建测试日志文件对象,路径为testClassDir/testCaseName
        new File(
            testClassDir,
            testCaseName); // 使用测试用例名称作为文件名
    return new OutputStreamWriter( // 返回一个输出流写入器,将字符转换为字节
        openTestLogOutputStream(testLogFile), StandardCharsets.UTF_8); // 使用UTF-8编码打开日志文件输出流
  }

  /** Returns the root directory under which test logs should be written. */ // 方法注释:返回测试日志应该写入的根目录
  protected abstract File getTestlogRoot(); // 抽象方法,由子类实现,返回测试日志的根目录路径

  /**
   * Initializes a diff-based test, overriding the default log file naming
   * scheme altogether.
   *
   * @param testFileSansExt full path to log filename, without .log/.ref
   *                        extension
   */ // 方法注释:初始化基于差异比较的测试,完全覆盖默认的日志文件命名方案
  protected OutputStream openTestLogOutputStream(File testFileSansExt) // 打开测试日志输出流方法,接收不带扩展名的文件路径
      throws IOException { // 可能抛出IO异常
    assert logOutputStream == null; // 断言日志输出流为null,确保没有重复打开

    logFile = new File(testFileSansExt + ".log"); // 创建.log文件对象,路径为testFileSansExt.log
    //noinspection ResultOfMethodCallIgnored // 忽略delete返回值的检查,因为文件可能不存在
    logFile.delete(); // 删除现有的.log文件(如果存在),确保使用新的空文件

    refFile = new File(testFileSansExt + ".ref"); // 创建.ref文件对象,路径为testFileSansExt.ref

    logOutputStream = Files.newOutputStream(logFile.toPath()); // 使用NIO的Files类创建新的文件输出流
    return logOutputStream; // 返回日志输出流,供调用者写入测试数据
  }

  /**
   * Finishes a diff-based test. Output that was written to the Writer
   * returned by openTestLog is diffed against a .ref file, and if any
   * differences are detected, the test case fails. Note that the diff used is
   * just a boolean test, and does not create any .dif ouput.
   *
   * <p>NOTE: if you wrap the Writer returned by openTestLog() (e.g. with a
   * PrintWriter), be sure to flush the wrapping Writer before calling this
   * method.
   *
   * @see #diffFile(File, File)
   */ // 方法注释:完成基于差异比较的测试,将日志输出与参考文件进行差异比较
  protected void diffTestLog() throws IOException { // 差异测试日志方法,可能抛出IO异常
    if (logOutputStream == null) { // 如果日志输出流为null
      throw new IllegalStateException(); // 抛出非法状态异常,表示测试未正确初始化
    }
    logOutputStream.close(); // 关闭日志输出流,确保所有数据写入文件
    logOutputStream = null; // 将日志输出流引用设置为null

    if (refFile == null) { // 如果参考文件为null
      throw new IllegalStateException(); // 抛出非法状态异常,表示测试未正确初始化
    }
    if (!refFile.exists()) { // 如果参考文件不存在
      fail("Reference file " + refFile + " does not exist"); // 测试失败,输出参考文件不存在的错误信息
    }
    if (logFile == null) { // 如果日志文件为null
      throw new IllegalStateException(); // 抛出非法状态异常,表示测试未正确初始化
    }
    diffFile(logFile, refFile); // 调用diffFile方法比较日志文件和参考文件
  }

  /**
   * Compares a log file with its reference log.
   *
   * <p>Usually, the log file and the reference log are in the same directory,
   * one ending with '.log' and the other with '.ref'.
   *
   * <p>If the files are identical, removes logFile.
   *
   * @param logFile Log file
   * @param refFile Reference log
   */ // 方法注释:比较日志文件与其参考日志,如果相同则删除日志文件
  protected void diffFile(File logFile, File refFile) throws IOException { // 差异文件比较方法,可能抛出IO异常
    BufferedReader logReader = null; // 声明日志文件缓冲读取器,初始化为null
    BufferedReader refReader = null; // 声明参考文件缓冲读取器,初始化为null
    try { // try块,用于异常处理
      // NOTE: Use of diff.mask is deprecated, use diff_mask. // 注释:diff.mask已弃用,使用diff_mask
      String diffMask = System.getProperty("diff.mask", null); // 从系统属性获取diff.mask值,已弃用
      if (diffMask != null) { // 如果diff.mask不为null
        addDiffMask(diffMask); // 添加差异掩码,兼容旧版本
      }

      diffMask = System.getProperty("diff_mask", null); // 从系统属性获取diff_mask值,推荐使用
      if (diffMask != null) { // 如果diff_mask不为null
        addDiffMask(diffMask); // 添加差异掩码
      }

      logReader = Util.reader(logFile); // 使用Util工具创建日志文件的缓冲读取器
      refReader = Util.reader(refFile); // 使用Util工具创建参考文件的缓冲读取器
      LineNumberReader logLineReader = new LineNumberReader(logReader); // 创建带行号的日志文件行读取器
      LineNumberReader refLineReader = new LineNumberReader(refReader); // 创建带行号的参考文件行读取器
      for (;;) { // 无限循环,逐行比较两个文件
        String logLine = logLineReader.readLine(); // 从日志文件读取一行
        String refLine = refLineReader.readLine(); // 从参考文件读取一行
        while ((logLine != null) && matchIgnorePatterns(logLine)) { // 当日志行不为null且匹配忽略模式时
          // System.out.println("logMatch Line:" + logLine); // 注释掉的调试输出
          logLine = logLineReader.readLine(); // 跳过日志文件的忽略行,读取下一行
        }
        while ((refLine != null) && matchIgnorePatterns(refLine)) { // 当参考行不为null且匹配忽略模式时
          // System.out.println("refMatch Line:" + logLine); // 注释掉的调试输出
          refLine = refLineReader.readLine(); // 跳过参考文件的忽略行,读取下一行
        }
        if ((logLine == null) || (refLine == null)) { // 如果任一行为null(文件结束)
          if (logLine != null) { // 如果日志文件还有剩余行
            diffFail( // 调用差异失败方法
                logFile, // 传入日志文件
                logLineReader.getLineNumber()); // 传入当前行号
          }
          if (refLine != null) { // 如果参考文件还有剩余行
            diffFail( // 调用差异失败方法
                logFile, // 传入日志文件
                refLineReader.getLineNumber()); // 传入当前行号
          }
          break; // 跳出循环
        }
        logLine = applyDiffMask(logLine); // 对日志行应用差异掩码,替换匹配的内容
        refLine = applyDiffMask(refLine); // 对参考行应用差异掩码,替换匹配的内容
        if (!logLine.equals(refLine)) { // 如果两行不相等
          diffFail( // 调用差异失败方法
              logFile, // 传入日志文件
              logLineReader.getLineNumber()); // 传入当前行号
        }
      }
    } finally { // finally块,确保资源被释放
      if (logReader != null) { // 如果日志读取器不为null
        logReader.close(); // 关闭日志读取器
      }
      if (refReader != null) { // 如果参考读取器不为null
        refReader.close(); // 关闭参考读取器
      }
    }

    // no diffs detected, so delete redundant .log file // 注释:未检测到差异,删除多余的.log文件
    //noinspection ResultOfMethodCallIgnored // 忽略delete返回值的检查
    logFile.delete(); // 删除日志文件,因为与参考文件完全相同
  }

  /**
   * Adds a diff mask. Strings matching the given regular expression will be
   * masked before diffing. This can be used to suppress spurious diffs on a
   * case-by-case basis.
   *
   * @param mask a regular expression, as per String.replaceAll
   */ // 方法注释:添加差异掩码,匹配给定正则表达式的字符串将在比较前被屏蔽
  protected void addDiffMask(String mask) { // 添加差异掩码方法,接收正则表达式字符串
    // diffMasks.add(mask); // 注释掉的代码,原计划使用ArrayList
    if (diffMasks.isEmpty()) { // 如果差异掩码字符串为空
      diffMasks = mask; // 直接赋值为新的掩码
    } else { // 如果差异掩码字符串不为空
      diffMasks = diffMasks + "|" + mask; // 用"|"连接新掩码,形成正则表达式的或关系
    }
    compiledDiffPattern = Pattern.compile(diffMasks); // 编译差异掩码正则表达式为Pattern对象
    compiledDiffMatcher = compiledDiffPattern.matcher(""); // 创建匹配器对象,使用空字符串初始化
  }

  protected void addIgnorePattern(String javaPattern) { // 添加忽略模式方法,接收正则表达式字符串
    if (ignorePatterns.isEmpty()) { // 如果忽略模式字符串为空
      ignorePatterns = javaPattern; // 直接赋值为新的模式
    } else { // 如果忽略模式字符串不为空
      ignorePatterns = ignorePatterns + "|" + javaPattern; // 用"|"连接新模式,形成正则表达式的或关系
    }
    compiledIgnorePattern = Pattern.compile(ignorePatterns); // 编译忽略模式正则表达式为Pattern对象
    compiledIgnoreMatcher = compiledIgnorePattern.matcher(""); // 创建匹配器对象,使用空字符串初始化
  }

  private String applyDiffMask(String s) { // 应用差异掩码方法,接收字符串参数
    if (compiledDiffMatcher != null) { // 如果差异掩码匹配器不为null
      if (compiledDiffPattern == null) { // 如果差异掩码模式为null
        throw new AssertionError(); // 抛出断言错误,表示状态不一致
      }
      compiledDiffMatcher.reset(s); // 重置匹配器,准备匹配新字符串

      // we assume most lines do not match // 注释:假设大多数行不匹配
      // so compiled matches will be faster than replaceAll. // 注释:所以编译后的匹配比replaceAll更快
      if (compiledDiffMatcher.find()) { // 如果字符串匹配差异掩码
        return compiledDiffPattern.matcher(s).replaceAll("XYZZY"); // 将所有匹配部分替换为"XYZZY"占位符
      }
    }
    return s; // 返回原字符串(不匹配或没有设置掩码)
  }

  private boolean matchIgnorePatterns(String s) { // 匹配忽略模式方法,接收字符串参数
    if (compiledIgnoreMatcher != null) { // 如果忽略模式匹配器不为null
      compiledIgnoreMatcher.reset(s); // 重置匹配器,准备匹配新字符串
      return compiledIgnoreMatcher.matches(); // 返回是否完全匹配忽略模式
    }
    return false; // 没有设置忽略模式,返回false
  }

  private void diffFail( // 差异失败方法,处理差异比较失败的情况
      File logFile, // 日志文件参数
      int lineNumber) { // 行号参数
    final String message = // 创建错误消息字符串
        "diff detected at line " + lineNumber + " in " + logFile; // 格式:在文件的某行检测到差异
    if (verbose) { // 如果启用了详细模式
      if (refFile == null) { // 如果参考文件为null
        throw new IllegalStateException(); // 抛出非法状态异常
      }
      if (inIde()) { // 如果在IntelliJ IDE中运行
        // If we're in IntelliJ, it's worth printing the 'expected // 注释:如果在IntelliJ中,值得打印期望和实际值
        // <...> actual <...>' string, because IntelliJ can format // 注释:因为IntelliJ可以智能格式化这种输出
        // this intelligently. Otherwise, use the more concise // 注释:否则使用更简洁的差异格式
        // diff format. // 注释:差异格式
        assertThat(message, fileContents(logFile), // 使用Hamcrest断言比较文件内容
            is(fileContents(refFile))); // 期望内容等于参考文件内容
      } else { // 如果不在IDE中运行
        String s = diff(refFile, logFile); // 调用diff方法生成差异字符串
        fail(message + '\n' + s + '\n'); // 测试失败,输出消息和差异详情
      }
    }
    fail(message); // 测试失败,输出简单的错误消息
  }

  /**
   * Returns whether this test is running inside the IntelliJ IDE.
   *
   * @return whether we're running in IntelliJ.
   */ // 方法注释:返回测试是否在IntelliJ IDE中运行
  private static boolean inIde() { // 判断是否在IDE中运行的静态方法
    Throwable runtimeException = new Throwable(); // 创建一个Throwable对象用于获取堆栈跟踪
    runtimeException.fillInStackTrace(); // 填充堆栈跟踪信息
    final StackTraceElement[] stackTrace = // 获取堆栈跟踪元素数组
        runtimeException.getStackTrace();
    StackTraceElement lastStackTraceElement = // 获取堆栈跟踪的最后一个元素(主方法)
        stackTrace[stackTrace.length - 1];

    // Junit test launched from IntelliJ 6.0 // 注释:从IntelliJ 6.0启动的JUnit测试
    if (lastStackTraceElement.getClassName().equals( // 如果最后一个堆栈元素的类名是JUnitStarter
        "com.intellij.rt.execution.junit.JUnitStarter") // IntelliJ的JUnit启动器类名
        && lastStackTraceElement.getMethodName().equals("main")) { // 并且方法名是main
      return true; // 返回true,表示在IntelliJ中运行
    }

    // Application launched from IntelliJ 6.0 // 注释:从IntelliJ 6.0启动的应用程序
    if (lastStackTraceElement.getClassName().equals( // 如果最后一个堆栈元素的类名是AppMain
        "com.intellij.rt.execution.application.AppMain") // IntelliJ的应用程序启动器类名
        && lastStackTraceElement.getMethodName().equals("main")) { // 并且方法名是main
      return true; // 返回true,表示在IntelliJ中运行
    }
    return false; // 返回false,表示不在IntelliJ中运行
  }

  /**
   * Returns a string containing the difference between the contents of two
   * files. The string has a similar format to the UNIX 'diff' utility.
   */ // 方法注释:返回包含两个文件内容差异的字符串,格式类似UNIX的diff工具
  public static String diff(File file1, File file2) { // 差异比较静态方法,接收两个文件对象
    List<String> lines1 = fileLines(file1); // 获取第一个文件的所有行
    List<String> lines2 = fileLines(file2); // 获取第二个文件的所有行
    return diffLines(lines1, lines2); // 调用diffLines方法比较两行列表的差异
  }

  /**
   * Returns a string containing the difference between the two sets of lines.
   */ // 方法注释:返回包含两组行之间差异的字符串
  public static String diffLines(List<String> lines1, List<String> lines2) { // 差异行比较静态方法,接收两个行列表
    final Diff<String> differencer = new Diff<>(lines1, lines2); // 创建Diff对象,用于计算两个列表的差异
    final List<Difference> differences = differencer.execute(); // 执行差异计算,获取差异列表
    StringWriter sw = new StringWriter(); // 创建字符串写入器,用于构建差异输出
    int offset = 0; // 偏移量,用于跟踪行号变化
    for (Difference d : differences) { // 遍历每个差异
      final int as = d.getAddedStart() + 1; // 获取添加的起始行号(转换为1-based)
      final int ae = d.getAddedEnd() + 1; // 获取添加的结束行号(转换为1-based)
      final int ds = d.getDeletedStart() + 1; // 获取删除的起始行号(转换为1-based)
      final int de = d.getDeletedEnd() + 1; // 获取删除的结束行号(转换为1-based)
      if (ae == 0) { // 如果没有添加行
        if (de == 0) { // 如果也没有删除行
          // no change // 注释:没有变化
        } else { // 如果有删除行
          // a deletion: "<ds>,<de>d<as>" // 注释:删除操作,格式为<起始行>,<结束行>d<位置>
          sw.append(String.valueOf(ds)); // 写入删除起始行号
          if (de > ds) { // 如果删除多行
            sw.append(",").append(String.valueOf(de)); // 写入逗号和结束行号
          }
          sw.append("d").append(String.valueOf(as - 1)).append('\n'); // 写入'd'和位置,然后换行
          for (int i = ds - 1; i < de; ++i) { // 遍历所有被删除的行
            sw.append("< ").append(lines1.get(i)).append('\n'); // 写入'< '和被删除的行内容
          }
        }
      } else { // 如果有添加行
        if (de == 0) { // 如果没有删除行
          // an addition: "<ds>a<as,ae>" // 注释:添加操作,格式为<位置>a<起始行>,<结束行>
          sw.append(String.valueOf(ds - 1)).append("a").append( // 写入位置和'a'
              String.valueOf(as)); // 写入添加起始行号
          if (ae > as) { // 如果添加多行
            sw.append(",").append(String.valueOf(ae)); // 写入逗号和结束行号
          }
          sw.append('\n'); // 换行
          for (int i = as - 1; i < ae; ++i) { // 遍历所有添加的行
            sw.append("> ").append(lines2.get(i)).append('\n'); // 写入'> '和添加的行内容
          }
        } else { // 如果既有删除又有添加(修改操作)
          // a change: "<ds>,<de>c<as>,<ae> // 注释:修改操作,格式为<起始行>,<结束行>c<起始行>,<结束行>
          sw.append(String.valueOf(ds)); // 写入删除起始行号
          if (de > ds) { // 如果删除多行
            sw.append(",").append(String.valueOf(de)); // 写入逗号和结束行号
          }
          sw.append("c").append(String.valueOf(as)); // 写入'c'和添加起始行号
          if (ae > as) { // 如果添加多行
            sw.append(",").append(String.valueOf(ae)); // 写入逗号和结束行号
          }
          sw.append('\n'); // 换行
          for (int i = ds - 1; i < de; ++i) { // 遍历所有被删除的行
            sw.append("< ").append(lines1.get(i)).append('\n'); // 写入'< '和被删除的行内容
          }
          sw.append("---\n"); // 写入分隔线
          for (int i = as - 1; i < ae; ++i) { // 遍历所有添加的行
            sw.append("> ").append(lines2.get(i)).append('\n'); // 写入'> '和添加的行内容
          }
          offset = offset + (ae - as) - (de - ds); // 更新偏移量,计算行号变化
        }
      }
    }
    return sw.toString(); // 返回差异字符串
  }

  /**
   * Returns a list of the lines in a given file.
   *
   * @param file File
   * @return List of lines
   */ // 方法注释:返回给定文件的所有行列表
  private static List<String> fileLines(File file) { // 读取文件行列表的静态方法
    List<String> lines = new ArrayList<>(); // 创建动态数组列表存储行
    try (LineNumberReader r = new LineNumberReader(Util.reader(file))) { // 使用try-with-resources创建行号读取器
      String line; // 声明行字符串变量
      while ((line = r.readLine()) != null) { // 循环读取每一行,直到文件结束
        lines.add(line); // 将行添加到列表中
      }
      return lines; // 返回行列表
    } catch (IOException e) { // 捕获IO异常
      e.printStackTrace(); // 打印异常堆栈跟踪
      throw TestUtil.rethrow(e); // 使用TestUtil重新抛出异常
    }
  }

  /**
   * Returns the contents of a file as a string.
   *
   * @param file File
   * @return Contents of the file
   */ // 方法注释:返回文件内容作为字符串
  protected static String fileContents(File file) { // 读取文件内容的静态方法
    byte[] buf = new byte[2048]; // 创建2048字节的缓冲区
    try (FileInputStream reader = new FileInputStream(file)) { // 使用try-with-resources创建文件输入流
      int readCount; // 声明读取字节数变量
      final ByteArrayOutputStream writer = new ByteArrayOutputStream(); // 创建字节数组输出流
      while ((readCount = reader.read(buf)) >= 0) { // 循环读取数据到缓冲区,直到文件结束
        writer.write(buf, 0, readCount); // 将读取的数据写入输出流
      }
      return writer.toString(StandardCharsets.UTF_8.name()); // 将字节数组转换为UTF-8字符串并返回
    } catch (IOException e) { // 捕获IO异常
      throw TestUtil.rethrow(e); // 使用TestUtil重新抛出异常
    }
  }

  /**
   * Sets whether to give verbose message if diff fails.
   */ // 方法注释:设置差异失败时是否提供详细消息
  protected void setVerbose(boolean verbose) { // 设置详细模式方法
    this.verbose = verbose; // 将参数赋值给verbose字段
  }

  /**
   * Sets the diff masks that are common to .REF files
   */ // 方法注释:设置.ref文件通用的差异掩码
  protected void setRefFileDiffMasks() { // 设置参考文件差异掩码方法
    // mask out source control Id // 注释:屏蔽源控制ID
    addDiffMask("\\$Id.*\\$"); // 添加正则表达式,匹配$Id...$格式的源控制关键字

    // NOTE hersker 2006-06-02: // 注释:作者hersker在2006-06-02的说明
    // The following two patterns can be used to mask out the // 注释:以下两个模式可用于屏蔽
    // sqlline JDBC URI and continuation prompts. This is useful // 注释:sqlline的JDBC URI和继续提示符。这在
    // during transition periods when URIs are changed, or when // 注释:URI变更的过渡期间很有用,或者当
    // new drivers are deployed which have their own URIs but // 注释:部署新驱动程序时,它们有自己的URI但
    // should first pass the existing test suite before their // 注释:应该首先通过现有测试套件,然后它们的
    // own .ref files get checked in. // 注释:自己的.ref文件才能被提交。
    //
    // It is not recommended to use these patterns on an everyday // 注释:不建议在日常使用中使用这些模式。
    // basis. Real differences in the output are difficult to spot // 注释:当比较具有不同sqlline提示符的
    // when diff-ing .ref and .log files which have different // 注释:.ref和.log文件时,输出中的真实差异
    // sqlline prompts at the start of each line. // 注释:很难被发现。

    // mask out sqlline JDBC URI prompt // 注释:屏蔽sqlline JDBC URI提示符
    addDiffMask("0: \\bjdbc(:[^:>]+)+:>"); // 添加正则表达式,匹配jdbc:...>格式的URI提示符

    // mask out different-length sqlline continuation prompts // 注释:屏蔽不同长度的sqlline继续提示符
    addDiffMask("^(\\.\\s?)+>"); // 添加正则表达式,匹配. >或.. >等继续提示符
  }
}
