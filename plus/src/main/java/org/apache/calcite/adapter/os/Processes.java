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
package org.apache.calcite.adapter.os; // 定义包名，该类位于org.apache.calcite.adapter.os包中，属于Calcite框架的操作系统适配器模块

import org.apache.calcite.linq4j.AbstractEnumerable; // 导入AbstractEnumerable抽象类，用于实现可枚举集合的基础功能
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，表示可枚举的序列
import org.apache.calcite.linq4j.Enumerator; // 导入Enumerator接口，用于遍历可枚举集合

import java.io.BufferedInputStream; // 导入BufferedInputStream类，用于提供缓冲输入流以提高读取效率
import java.io.BufferedReader; // 导入BufferedReader类，用于提供缓冲字符输入流以按行读取文本
import java.io.IOException; // 导入IOException类，用于处理输入输出异常
import java.io.InputStream; // 导入InputStream抽象类，表示字节输入流
import java.io.InputStreamReader; // 导入InputStreamReader类，用于将字节流转换为字符流
import java.nio.charset.StandardCharsets; // 导入StandardCharsets类，用于指定字符编码为UTF-8
import java.util.Arrays; // 导入Arrays工具类，用于数组操作
import java.util.function.Supplier; // 导入Supplier函数式接口，用于提供Process对象的工厂方法

import static java.util.Objects.requireNonNull; // 导入requireNonNull静态方法，用于参数非空校验

/**
 * Utilities regarding operating system processes. // 关于操作系统进程的工具类
 *
 * <p>WARNING: Spawning processes is not secure. // 警告：启动进程是不安全的
 * Use this class with caution. // 请谨慎使用此类
 * This class is in the "plus" module because "plus" is not used by default. // 此类在"plus"模块中，因为"plus"模块默认不被使用
 * Do not move this class to the "core" module. // 不要将此类移动到"core"模块
 */
public class Processes { // Processes类：操作系统进程工具类，用于执行操作系统命令并将输出作为可枚举集合返回
  private Processes() {} // 私有构造方法，防止实例化，此类为纯工具类只提供静态方法

  /** Executes a command and returns its result as an enumerable of lines. */ // 执行命令并将其结果作为行的可枚举集合返回
  static Enumerable<String> processLines(String... args) { // processLines方法：执行指定命令并按行返回输出结果，使用空格作为分隔符
    return processLines(' ', args); // 调用重载方法，使用空格字符作为分隔符
  }

  /** Executes a command and returns its result as an enumerable of lines. */ // 执行命令并将其结果作为行的可枚举集合返回
  static Enumerable<String> processLines(char sep, String... args) { // processLines方法：执行指定命令并按指定分隔符返回输出结果
    return processLines(sep, processSupplier(args)); // 调用私有方法，传入分隔符和进程提供者
  }

  /** Executes a command and returns its result as an enumerable of lines. // 执行命令并将其结果作为行的可枚举集合返回
   *
   * @param sep Separator character // 分隔符字符参数
   * @param processSupplier Command and its arguments // 命令及其参数的提供者
   */
  private static Enumerable<String> processLines(char sep, // processLines私有方法：根据分隔符类型创建不同的可枚举集合实现
      Supplier<Process> processSupplier) { // processSupplier参数：用于创建Process对象的函数式接口
    if (sep != ' ') { // 如果分隔符不是空格
      return new SeparatedLinesEnumerable(processSupplier, sep); // 返回使用自定义分隔符的可枚举集合
    } else { // 如果分隔符是空格
      return new ProcessLinesEnumerator(processSupplier); // 返回按行分隔的标准可枚举集合
    }
  }

  private static Supplier<Process> processSupplier(final String... args) { // processSupplier私有方法：创建进程提供者，延迟执行命令
    return new ProcessFactory(args); // 返回ProcessFactory实例，该类实现了Supplier接口用于创建Process对象
  }

  /** Enumerator that executes a process and returns each line as an element. */ // 枚举器：执行进程并将每一行作为一个元素返回
  private static class ProcessLinesEnumerator // ProcessLinesEnumerator内部类：按行读取进程输出的枚举器实现
      extends AbstractEnumerable<String> { // 继承AbstractEnumerable抽象类，提供可枚举集合的基础实现
    private final Supplier<Process> processSupplier; // processSupplier成员变量：进程提供者，用于创建Process对象

    ProcessLinesEnumerator(Supplier<Process> processSupplier) { // ProcessLinesEnumerator构造方法：初始化按行枚举器
      this.processSupplier = requireNonNull(processSupplier, "processSupplier"); // 保存进程提供者并进行非空校验
    }

    @Override public Enumerator<String> enumerator() { // enumerator方法：创建并返回一个字符串枚举器用于遍历进程输出
      final Process process = processSupplier.get(); // 获取Process对象，启动操作系统进程
      final InputStream is = process.getInputStream(); // 获取进程的标准输出流
      final BufferedInputStream bis = // 创建缓冲输入流以提高读取效率
          new BufferedInputStream(is); // 包装原始输入流
      final InputStreamReader isr = // 创建输入流读取器，将字节流转换为字符流
          new InputStreamReader(bis, StandardCharsets.UTF_8); // 使用UTF-8编码
      final BufferedReader br = new BufferedReader(isr); // 创建缓冲读取器，支持按行读取
      return new Enumerator<String>() { // 返回匿名内部类实现的Enumerator接口
        private String line; // line成员变量：存储当前读取的行内容

        @Override public String current() { // current方法：返回当前元素的值
          return line; // 返回当前行
        }

        @Override public boolean moveNext() { // moveNext方法：移动到下一个元素
          try { // 尝试读取下一行
            line = br.readLine(); // 从缓冲读取器中读取一行文本
            return line != null; // 如果读取到非空行则返回true，否则返回false表示已到末尾
          } catch (IOException e) { // 捕获IO异常
            throw new RuntimeException(e); // 将异常包装为运行时异常抛出
          }
        }

        @Override public void reset() { // reset方法：重置枚举器位置
          throw new UnsupportedOperationException(); // 抛出不支持操作异常，因为进程输出流不可重置
        }

        @Override public void close() { // close方法：关闭枚举器并释放资源
          try { // 尝试关闭资源
            br.close(); // 关闭缓冲读取器
          } catch (IOException e) { // 捕获IO异常
            throw new RuntimeException("while running " + processSupplier, e); // 包装为运行时异常并包含进程信息
          }
          process.destroy(); // 销毁进程，释放系统资源
        }
      };
    }
  }

  /** Enumerator that executes a process and returns each line as an element. */ // 枚举器：执行进程并将每一行作为一个元素返回
  private static class SeparatedLinesEnumerable // SeparatedLinesEnumerable内部类：按自定义分隔符读取进程输出的枚举器实现
      extends AbstractEnumerable<String> { // 继承AbstractEnumerable抽象类，提供可枚举集合的基础实现
    private final Supplier<Process> processSupplier; // processSupplier成员变量：进程提供者，用于创建Process对象
    private final int sep; // sep成员变量：分隔符的整数值

    SeparatedLinesEnumerable(Supplier<Process> processSupplier, char sep) { // SeparatedLinesEnumerable构造方法：初始化自定义分隔符枚举器
      this.processSupplier = processSupplier; // 保存进程提供者
      this.sep = sep; // 保存分隔符
    }

    @Override public Enumerator<String> enumerator() { // enumerator方法：创建并返回一个字符串枚举器用于遍历进程输出
      final Process process = processSupplier.get(); // 获取Process对象，启动操作系统进程
      final InputStream is = process.getInputStream(); // 获取进程的标准输出流
      final BufferedInputStream bis = // 创建缓冲输入流以提高读取效率
          new BufferedInputStream(is); // 包装原始输入流
      final InputStreamReader isr = // 创建输入流读取器，将字节流转换为字符流
          new InputStreamReader(bis, StandardCharsets.UTF_8); // 使用UTF-8编码
      final BufferedReader br = new BufferedReader(isr); // 创建缓冲读取器，支持按字符读取
      return new Enumerator<String>() { // 返回匿名内部类实现的Enumerator接口
        private final StringBuilder b = new StringBuilder(); // b成员变量：StringBuilder对象，用于构建分隔符之间的文本
        private String line; // line成员变量：存储当前读取的元素内容

        @Override public String current() { // current方法：返回当前元素的值
          return line; // 返回当前元素
        }

        @Override public boolean moveNext() { // moveNext方法：移动到下一个元素
          try { // 尝试读取下一个分隔符分隔的元素
            for (;;) { // 无限循环，逐个字符读取
              int c = br.read(); // 读取一个字符
              if (c < 0) { // 如果读取到流末尾
                return false; // 返回false表示没有更多元素
              }
              if (c == sep) { // 如果读取到分隔符
                line = b.toString(); // 将StringBuilder中的内容转换为字符串作为当前元素
                b.setLength(0); // 清空StringBuilder，准备构建下一个元素
                return true; // 返回true表示成功移动到下一个元素
              }
              b.append((char) c); // 将字符追加到StringBuilder中
            }
          } catch (IOException e) { // 捕获IO异常
            throw new RuntimeException(e); // 将异常包装为运行时异常抛出
          }
        }

        @Override public void reset() { // reset方法：重置枚举器位置
          throw new UnsupportedOperationException(); // 抛出不支持操作异常，因为进程输出流不可重置
        }

        @Override public void close() { // close方法：关闭枚举器并释放资源
          try { // 尝试关闭资源
            br.close(); // 关闭缓冲读取器
          } catch (IOException e) { // 捕获IO异常
            throw new RuntimeException("while running " + processSupplier, e); // 包装为运行时异常并包含进程信息
          }
          process.destroy(); // 销毁进程，释放系统资源
        }
      };
    }
  }

  /** Creates processes. */ // 创建进程的工厂类
  private static class ProcessFactory implements Supplier<Process> { // ProcessFactory内部类：实现Supplier接口作为进程工厂
    private final String[] args; // args成员变量：命令及其参数数组

    ProcessFactory(String... args) { // ProcessFactory构造方法：初始化进程工厂
      this.args = args; // 保存命令参数数组
    }

    @Override public Process get() { // get方法：创建并启动一个新的进程
      try { // 尝试创建进程
        return new ProcessBuilder().command(args).start(); // 使用ProcessBuilder创建并启动进程
      } catch (IOException e) { // 捕获IO异常
        throw new RuntimeException("while creating process: " // 包装为运行时异常并包含命令信息
            + Arrays.toString(args), e); // 抛出异常
      }
    }

    @Override public String toString() { // toString方法：返回进程工厂的字符串表示
      return args[0]; // 返回命令名称（参数数组的第一个元素）
    }
  }
}
