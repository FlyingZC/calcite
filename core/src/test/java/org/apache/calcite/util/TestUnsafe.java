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
package org.apache.calcite.util; // 声明TestUnsafe类所在的包为org.apache.calcite.util工具包

import com.google.common.collect.ImmutableList; // 导入Google Guava库的不可变列表类，用于创建不可修改的列表对象

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework框架的注解，用于标记可空类型，帮助进行空值检查

import org.slf4j.Logger; // 导入SLF4J日志框架的Logger接口，用于记录日志信息

import java.io.BufferedInputStream; // 导入缓冲输入流类，用于提高读取数据的效率
import java.io.BufferedOutputStream; // 导入缓冲输出流类，用于提高写入数据的效率
import java.io.BufferedReader; // 导入缓冲字符输入流类，用于按行读取文本数据
import java.io.File; // 导入文件类，用于表示文件和目录路径
import java.io.IOException; // 导入IO异常类，用于处理输入输出操作中的异常
import java.io.InputStream; // 导入输入流抽象类，用于从各种数据源读取字节
import java.io.OutputStream; // 导入输出流抽象类，用于向各种目标写入字节
import java.io.Reader; // 导入字符读取器抽象类，用于读取字符数据
import java.io.StringReader; // 导入字符串读取器类，用于从字符串读取字符数据
import java.io.StringWriter; // 导入字符串写入器类，用于将字符数据写入字符串缓冲区
import java.io.Writer; // 导入字符写入器抽象类，用于写入字符数据
import java.util.List; // 导入列表接口，表示有序的元素集合
import java.util.function.BiConsumer; // 导入双参数消费者函数式接口，用于接受两个参数并执行操作
import java.util.stream.Stream; // 导入流接口，用于支持函数式风格的集合操作

/**
 * Unsafe methods to be used by tests.
 * 用于测试的不安全方法集合
 *
 * <p>Contains methods that call JDK methods that the
 * <a href="https://github.com/policeman-tools/forbidden-apis">forbidden
 * APIs checker</a> does not approve of.
 * 包含调用JDK方法的方法，这些方法被<a href="https://github.com/policeman-tools/forbidden-apis">禁止API检查器</a>不认可
 *
 * <p>This class is excluded from the check, so methods called via this class
 * will not fail the build.
 * 该类被排除在检查之外，因此通过该类调用的方法不会导致构建失败
 *
 * <p>Why is this in {@code core/src/test} and not in {@code testkit/src/main}?
 * Because some of the methods (e.g. {@link #runAppProcess}) are so unsafe that
 * they must not be on the class-path of production code.
 * 为什么这个类在{@code core/src/test}而不是{@code testkit/src/main}中？
 * 因为某些方法（例如{@link #runAppProcess}）非常不安全，绝不能出现在生产代码的类路径中
 */
public abstract class TestUnsafe { // 声明TestUnsafe为抽象类，不能直接实例化，只能通过类名调用静态方法
  /**
   * Runs an external application process.
   * 运行外部应用程序进程
   *
   * @param argumentList  command name and its arguments 命令名称及其参数列表
   * @param directory  working directory 工作目录，进程将在该目录下运行
   * @param logger    if not null, command and exit status will be logged here 如果不为null，命令和退出状态将记录到此日志器
   * @param appInput  if not null, data will be copied to application's stdin 如果不为null，数据将被复制到应用程序的标准输入
   * @param appOutput if not null, data will be captured from application's
   *                  stdout and stderr 如果不为null，数据将从应用程序的标准输出和标准错误输出中捕获
   * @return application process exit value 应用程序进程的退出值，0表示成功，非0表示失败
   */
  public static int runAppProcess(List<String> argumentList, File directory, // 声明公共静态方法，运行外部应用程序进程，接收命令列表、工作目录、日志器、输入读取器和输出写入器作为参数
      @Nullable Logger logger, @Nullable Reader appInput, // 接收可空的日志器和输入读取器参数，用于日志记录和提供输入数据
      @Nullable Writer appOutput) throws IOException, InterruptedException { // 接收可空的输出写入器参数，用于捕获输出数据，声明可能抛出IO异常和中断异常

    // WARNING: ProcessBuilder is security-sensitive. Its use is currently
    // safe because this code is under "core/test". Developers must not move
    // this code into "core/main".
    // 警告：ProcessBuilder具有安全敏感性。当前使用是安全的，因为此代码位于"core/test"下。开发者绝不能将此代码移动到"core/main"中
    final ProcessBuilder pb = new ProcessBuilder(argumentList); // 创建ProcessBuilder对象，用于构建操作系统进程，传入命令参数列表
    pb.directory(directory); // 设置进程的工作目录为指定的目录
    pb.redirectErrorStream(true); // 将错误流重定向到输出流，使得标准错误和标准输出合并到同一个流中
    if (logger != null) { // 检查日志器是否不为null
      logger.info("start process: " + pb.command()); // 如果日志器存在，记录启动进程的信息，包括完整的命令
    }
    Process p = pb.start(); // 启动进程，返回Process对象代表正在运行的子进程

    // Setup the input/output streams to the subprocess.
    // The buffering here is arbitrary. Javadocs strongly encourage
    // buffering, but the size needed is very dependent on the
    // specific application being run, the size of the input
    // provided by the caller, and the amount of output expected.
    // Since this method is currently used only by unit tests,
    // large-ish fixed buffer sizes have been chosen. If this
    // method becomes used for something in production, it might
    // be better to have the caller provide them as arguments.
    // 设置子进程的输入/输出流
    // 这里的缓冲区大小是任意的。Javadoc强烈建议使用缓冲，
    // 但所需的大小很大程度上取决于正在运行的特定应用程序、
    // 调用者提供的输入大小以及预期的输出量。
    // 由于此方法目前仅由单元测试使用，因此选择了较大的固定缓冲区大小。
    // 如果此方法在生产环境中使用，最好让调用者将它们作为参数提供。
    if (appInput != null) { // 检查输入读取器是否不为null
      OutputStream out = // 创建输出流对象，用于向子进程写入数据
          new BufferedOutputStream( // 创建缓冲输出流，提高写入效率
              p.getOutputStream(), // 获取子进程的输出流（即子进程的输入流）
              100 * 1024); // 设置缓冲区大小为100KB
      int c; // 声明整型变量，用于存储读取的字符
      while ((c = appInput.read()) != -1) { // 循环读取输入读取器中的数据，直到到达文件末尾（返回-1）
        out.write(c); // 将读取的字符写入子进程的输入流
      }
      out.flush(); // 刷新输出流，确保所有缓冲的数据都被写入子进程
    }
    if (appOutput != null) { // 检查输出写入器是否不为null
      InputStream in = // 创建输入流对象，用于从子进程读取数据
          new BufferedInputStream( // 创建缓冲输入流，提高读取效率
              p.getInputStream(), // 获取子进程的输入流（即子进程的输出流）
              100 * 1024); // 设置缓冲区大小为100KB
      int c; // 声明整型变量，用于存储读取的字节
      while ((c = in.read()) != -1) { // 循环读取子进程的输出数据，直到到达流末尾（返回-1）
        appOutput.write(c); // 将读取的字节写入输出写入器
      }
      appOutput.flush(); // 刷新输出写入器，确保所有缓冲的数据都被写入
      in.close(); // 关闭输入流，释放系统资源
    }
    p.waitFor(); // 等待子进程执行完成，阻塞当前线程直到子进程结束

    int status = p.exitValue(); // 获取子进程的退出状态码，0表示正常退出，非0表示异常退出
    if (logger != null) { // 检查日志器是否不为null
      logger.info("exit status=" + status + " from " + pb.command()); // 如果日志器存在，记录进程退出状态和命令信息
    }
    return status; // 返回子进程的退出状态码
  }

  /** Returns whether we seem are in a valid environment.
   * 返回我们是否处于有效的Git环境中
   */
  public static boolean haveGit() { // 声明公共静态方法，检查当前环境是否支持Git操作，返回布尔值
    // Is there a '.git' directory? If not, we may be in a source tree
    // unzipped from a tarball.
    // 是否存在'.git'目录？如果不存在，我们可能位于从tarball解压的源代码树中
    final File base = TestUtil.getBaseDir(TestUnsafe.class); // 获取TestUnsafe类所在的基础目录，即项目根目录
    final File gitDir = new File(base, ".git"); // 创建.git目录的File对象，Git版本控制目录
    if (!gitDir.exists() // 检查.git目录是否存在
        || !gitDir.isDirectory() // 检查.git目录是否为目录（而不是文件）
        || !gitDir.canRead()) { // 检查.git目录是否可读
      return false; // 如果任何一个条件不满足，返回false，表示Git环境无效
    }

    // Execute a simple git command. If it fails, we're probably not in a
    // valid git environment.
    // 执行一个简单的git命令。如果失败，我们可能不在有效的git环境中
    final List<String> argumentList = // 创建命令参数列表
        ImmutableList.of("git", "--version"); // 使用不可变列表存储"git --version"命令，用于检查Git是否可用
    try { // 开始try-catch块，捕获可能发生的异常
      final StringWriter sw = new StringWriter(); // 创建字符串写入器，用于捕获命令输出
      int status = // 执行git命令，获取退出状态码
          runAppProcess(argumentList, base, null, null, sw); // 调用runAppProcess方法执行git命令，不提供输入和日志，输出写入字符串写入器
      final String s = sw.toString(); // 将字符串写入器的内容转换为字符串
      if (status != 0) { // 检查命令退出状态码是否不为0（非0表示命令执行失败）
        return false; // 如果命令执行失败，返回false，表示Git环境无效
      }
    } catch (Exception e) { // 捕获执行过程中可能抛出的任何异常
      return false; // 如果发生异常，返回false，表示Git环境无效
    }
    return true; // 如果所有检查都通过，返回true，表示Git环境有效
  }

  /** Returns a list of Java files in git.
   * 返回Git仓库中的Java文件列表
   */
  public static List<File> getJavaFiles() { // 声明公共静态方法，获取Git仓库中所有Java源代码文件
    return getGitFiles("*.java"); // 调用getGitFiles方法，传入"*.java"模式，返回匹配该模式的所有文件
  }

  /** Returns a list of text files in git.
   * 返回Git仓库中的文本文件列表
   */
  public static List<File> getTextFiles() { // 声明公共静态方法，获取Git仓库中所有文本类型文件
    return getGitFiles("*.bat", "*.cmd", "*.csv", "*.fmpp", "*.ftl", // 调用getGitFiles方法，传入多种文本文件扩展名模式
        "*.iq", "*.java", "*.json", "*.jj", // 包括批处理文件、命令文件、CSV、FreeMarker模板、FreeMarker标记
        "*.kt", "*.kts", ".mailmap", "*.md", // 包括IQ文件、Java文件、JSON文件、JavaCC文件
        "*.properties", "*.sh", "*.sql", "*.txt", "*.xml", "*.yaml", // 包括Kotlin文件、Kotlin脚本、邮件映射、Markdown文件
        "*.yml"); // 包括属性文件、Shell脚本、SQL文件、文本文件、XML文件、YAML文件、YML文件
  }

  /** Returns a list of files in git matching a given pattern or patterns.
   * 返回Git仓库中匹配给定模式或模式的文件列表
   *
   * <p>Assumes running Linux or macOS, and that git is available.
   * 假定运行在Linux或macOS系统上，并且Git可用 */
  public static List<File> getGitFiles(String... patterns) { // 声明公共静态方法，接收可变参数的文件模式字符串，返回匹配的文件列表
    String s; // 声明字符串变量，用于存储git命令的输出结果
    try { // 开始try-catch块，捕获可能发生的异常
      final List<String> argumentList = // 创建命令参数列表
          ImmutableList.<String>builder().add("git").add("ls-files") // 使用不可变列表构建器，添加"git"和"ls-files"命令，列出Git仓库中的文件
              .add(patterns).build(); // 添加文件模式参数并构建不可变列表
      final File base = TestUtil.getBaseDir(TestUnsafe.class); // 获取TestUnsafe类所在的基础目录，即项目根目录
      try { // 开始内层try-catch块，捕获执行命令时的异常
        final StringWriter sw = new StringWriter(); // 创建字符串写入器，用于捕获git命令的输出
        int status = // 执行git ls-files命令，获取退出状态码
            runAppProcess(argumentList, base, null, null, sw); // 调用runAppProcess方法执行git命令，不提供输入和日志，输出写入字符串写入器
        if (status != 0) { // 检查命令退出状态码是否不为0（非0表示命令执行失败）
          throw new RuntimeException("command " + argumentList // 抛出运行时异常，包含命令和退出状态信息
              + ": exited with status " + status);
        }
        s = sw.toString(); // 将字符串写入器的内容转换为字符串，存储到变量s中
      } catch (Exception e) { // 捕获执行过程中可能抛出的任何异常
        throw new RuntimeException("command " + argumentList // 抛出运行时异常，包含命令和异常信息
            + ": failed with exception", e);
      }

      final ImmutableList.Builder<File> files = ImmutableList.builder(); // 创建不可变列表构建器，用于构建文件列表
      try (StringReader r = new StringReader(s); // 使用try-with-resources语句，创建字符串读取器，自动管理资源
           BufferedReader br = new BufferedReader(r)) { // 创建缓冲字符读取器，包装字符串读取器，提高读取效率
        for (;;) { // 无限循环，用于逐行读取文件列表
          String line = br.readLine(); // 从缓冲读取器中读取一行文本
          if (line == null) { // 检查读取的行是否为null（表示到达文件末尾）
            break; // 如果到达文件末尾，退出循环
          }
          files.add(new File(base, line)); // 将读取的文件路径与基础目录结合，创建File对象并添加到列表构建器中
        }
      }
      return files.build(); // 构建并返回不可变的文件列表
    } catch (IOException e) { // 捕获IO异常
      throw new RuntimeException(e); // 将IO异常包装为运行时异常并抛出
    }
  }

  /** Returns the subject / body pairs of the {@code n} most recent commits.
   * 返回最近{@code n}次提交的主题/正文对
   */
  public static void getCommitMessages(int n, // 声明公共静态方法，获取最近n次Git提交的消息，接收提交次数和消费者作为参数
      BiConsumer<String, String> consumer) { // 接收双参数消费者函数式接口，用于处理每次提交的主题和正文
    // Generate log like this:
    //
    //   ===
    //   subject
    //
    //   body
    //   ===
    //   subject 2
    //
    //   body2
    //
    // then split on "===\n"
    // 生成如下格式的日志：
    //
    //   ===
    //   主题
    //
    //   正文
    //   ===
    //   主题2
    //
    //   正文2
    //
    // 然后按"===\n"分割
    final File base = TestUtil.getBaseDir(TestUnsafe.class); // 获取TestUnsafe类所在的基础目录，即项目根目录
    final List<String> argumentList = // 创建命令参数列表
        ImmutableList.of("git", "log", "-n" + n, "--pretty=format:===%n%B"); // 使用不可变列表存储git log命令，指定提交次数和格式，使用"==="作为分隔符，%B表示完整的提交消息（主题+正文）
    try { // 开始try-catch块，捕获可能发生的异常
      final StringWriter sw = new StringWriter(); // 创建字符串写入器，用于捕获git log命令的输出
      int status = // 执行git log命令，获取退出状态码
          runAppProcess(argumentList, base, null, null, sw); // 调用runAppProcess方法执行git log命令，不提供输入和日志，输出写入字符串写入器
      String s = sw.toString(); // 将字符串写入器的内容转换为字符串，存储到变量s中
      if (status != 0) { // 检查命令退出状态码是否不为0（非0表示命令执行失败）
        throw new RuntimeException("command " + argumentList // 抛出运行时异常，包含命令、退出状态和输出信息
            + ": exited with status " + status
            + (s.isEmpty() ? "" : "; output [" + s + "]")); // 如果输出不为空，则包含输出信息
      }
      Stream.of(s.split("===\n")).forEach(s2 -> { // 将输出字符串按"===\n"分割成流，对每个提交消息块进行处理
        if (s2.isEmpty()) { // 检查提交消息块是否为空
          return; // 如果为空，忽略该块（忽略空的主题和正文）
        }
        int i = s2.indexOf("\n"); // 查找第一个换行符的位置，用于分隔主题和正文
        if (i < 0) { // 检查是否找到换行符
          i = s2.length(); // 如果没有换行符，将整个块作为主题
        }
        String subject = s2.substring(0, i); // 提取主题部分，从开始到第一个换行符
        while (i < s2.length() && s2.charAt(i) == '\n') { // 循环跳过主题和正文之间的多个换行符
          ++i; // 跳过主题和正文之间的多个换行符
        }
        String body = s2.substring(i); // 提取正文部分，从跳过换行符后的位置到结束
        consumer.accept(subject, body); // 调用消费者函数，传递主题和正文
      });
    } catch (Exception e) { // 捕获执行过程中可能抛出的任何异常
      throw new RuntimeException("command " + argumentList // 抛出运行时异常，包含命令和异常信息
          + ": failed with exception", e);
    }
  }
}
