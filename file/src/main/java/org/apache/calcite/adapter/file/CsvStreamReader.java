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
package org.apache.calcite.adapter.file; // 声明该类属于org.apache.calcite.adapter.file包，这是Calcite文件适配器模块

import org.apache.calcite.util.Source; // 导入Calcite的Source类，用于表示文件源

import org.apache.commons.io.input.Tailer; // 导入Apache Commons IO的Tailer类，用于监控文件新增内容
import org.apache.commons.io.input.TailerListener; // 导入TailerListener接口，用于接收文件变更通知
import org.apache.commons.io.input.TailerListenerAdapter; // 导入TailerListenerAdapter适配器类，简化监听器实现

import au.com.bytecode.opencsv.CSVParser; // 导入OpenCSV的CSVParser类，用于解析CSV格式数据
import au.com.bytecode.opencsv.CSVReader; // 导入OpenCSV的CSVReader类，用于读取CSV文件

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值

import java.io.Closeable; // 导入Closeable接口，用于资源关闭
import java.io.IOException; // 导入IOException类，用于处理IO异常
import java.io.StringReader; // 导入StringReader类，用于读取字符串
import java.time.Duration; // 导入Duration类，用于表示时间间隔
import java.util.ArrayDeque; // 导入ArrayDeque类，用于实现双端队列
import java.util.Queue; // 导入Queue接口，用于队列操作

/**
 * Extension to {@link CSVReader} that can read newly appended file content.
 * CSVReader的扩展类，能够读取文件中新增的内容，支持实时监控CSV文件的变化
 * 该类主要用于处理动态增长的CSV文件，可以持续读取文件末尾新增的数据行
 * 通过使用Tailer机制监控文件变化，将新增内容放入队列中供读取
 */
class CsvStreamReader extends CSVReader implements Closeable { // 定义CsvStreamReader类，继承CSVReader并实现Closeable接口
  protected final CSVParser parser; // CSV解析器，用于解析CSV格式的数据行，支持自定义分隔符、引号字符等
  protected final int skipLines; // 开始读取前需要跳过的行数，用于跳过CSV文件头部的标题行
  protected final Tailer tailer; // 文件尾部监控器，用于持续监控文件并捕获新增内容
  protected final Queue<String> contentQueue; // 内容队列，用于存储从文件中读取的原始文本行，采用先进先出(FIFO)策略

  /**
   * The default line to start reading.
   * 默认开始读取的行号，0表示从文件第一行开始读取
   */
  public static final int DEFAULT_SKIP_LINES = 0; // 定义默认跳过行数为0，即不跳过任何行

  /**
   * The default file monitor delay.
   * 默认文件监控延迟时间（毫秒），Tailer每隔这个时间间隔检查一次文件是否有新内容
   */
  public static final long DEFAULT_MONITOR_DELAY = 2000; // 定义默认监控延迟为2000毫秒（2秒）

  CsvStreamReader(Source source) { // 构造方法：使用默认参数创建CsvStreamReader实例
    this(source, // 调用另一个构造方法，传入文件源
        CSVParser.DEFAULT_SEPARATOR, // 使用默认分隔符（逗号）
        CSVParser.DEFAULT_QUOTE_CHARACTER, // 使用默认引号字符（双引号）
        CSVParser.DEFAULT_ESCAPE_CHARACTER, // 使用默认转义字符
        DEFAULT_SKIP_LINES, // 使用默认跳过行数（0）
        CSVParser.DEFAULT_STRICT_QUOTES, // 使用默认严格引号模式
        CSVParser.DEFAULT_IGNORE_LEADING_WHITESPACE); // 使用默认忽略前导空格设置
  }

  /**
   * Creates a CsvStreamReader with supplied separator and quote char.
   * 创建一个具有自定义分隔符和引号字符的CsvStreamReader实例
   *
   * @param source The file to an underlying CSV source // 底层CSV源文件
   * @param separator The delimiter to use for separating entries // 用于分隔条目的分隔符（如逗号、分号等）
   * @param quoteChar The character to use for quoted elements // 用于引用元素的字符（如双引号）
   * @param escape The character to use for escaping a separator or quote // 用于转义分隔符或引号的字符
   * @param line The line number to skip for start reading // 开始读取前要跳过的行号
   * @param strictQuotes Sets if characters outside the quotes are ignored // 设置是否忽略引号外的字符
   * @param ignoreLeadingWhiteSpace If true, parser should ignore // 如果为true，解析器应忽略字段中引号前的空白字符
   *                                white space before a quote in a field
   */
  private CsvStreamReader(Source source, char separator, char quoteChar, // 私有构造方法：使用自定义参数创建CsvStreamReader实例
      char escape, int line, boolean strictQuotes, // 接收转义字符、跳过行数、严格引号模式等参数
      boolean ignoreLeadingWhiteSpace) { // 接收是否忽略前导空格的参数
    super(new StringReader("")); // dummy call to base constructor // 调用父类CSVReader的构造方法，传入空的StringReader作为占位符
    contentQueue = new ArrayDeque<>(); // 初始化内容队列，使用ArrayDeque实现双端队列，用于存储从文件读取的行
    TailerListener listener = new CsvContentListener(contentQueue); // 创建文件变更监听器，将读取的内容添加到队列中
    tailer = // 初始化文件尾部监控器，用于持续监控文件变化
        Tailer.builder() // 使用Builder模式创建Tailer实例
            .setFile(source.file()) // 设置要监控的文件
            .setTailerListener(listener) // 设置文件变更监听器
            .setDelayDuration(Duration.ofMillis(DEFAULT_MONITOR_DELAY)) // 设置监控延迟时间为默认值（2000毫秒）
            .setTailFromEnd(false) // 设置不从文件末尾开始读取，而是从头开始读取
            .setReOpen(true) // 设置文件被外部修改后重新打开文件
            .setBufferSize(4096) // 设置缓冲区大小为4096字节
            .get(); // 获取Tailer实例并启动监控线程

    this.parser = // 初始化CSV解析器
        new CSVParser(separator, quoteChar, escape, strictQuotes, // 使用传入的分隔符、引号字符、转义字符等参数创建解析器
            ignoreLeadingWhiteSpace); // 传入是否忽略前导空格的参数
    this.skipLines = line; // 保存需要跳过的行数
    try { // 尝试等待监控器捕获初始数据
      // wait for tailer to capture data // 等待Tailer捕获文件中的初始数据，确保队列中有数据可读
      Thread.sleep(DEFAULT_MONITOR_DELAY); // 线程休眠默认监控延迟时间（2000毫秒）
    } catch (InterruptedException e) { // 捕获线程中断异常
      throw new RuntimeException(e); // 将中断异常包装为运行时异常并抛出
    }
  }

  /**
   * Reads the next line from the buffer and converts to a string array.
   * 从缓冲区读取下一行并转换为字符串数组，支持处理跨多行的CSV数据
   *
   * @return a string array with each comma-separated element as a separate entry. // 返回字符串数组，每个逗号分隔的元素作为一个独立条目，如果没有更多数据则返回null
   *
   * @throws IOException if bad things happen during the read // 如果读取过程中发生错误则抛出IOException
   */
  @Override public String @Nullable[] readNext() throws IOException { // 重写父类的readNext方法，从队列中读取并解析下一行CSV数据
    String[] result = null; // 初始化结果数组为null，用于存储解析后的字段数据
    do { // 循环读取并解析数据，直到所有跨行的数据都处理完毕
      String nextLine = getNextLine(); // 从队列中获取下一行原始文本数据
      if (nextLine == null) { // 如果获取的行为null，表示没有更多数据
        return null; // 返回null表示已到达文件末尾
      }
      String[] r = parser.parseLineMulti(nextLine); // 使用CSV解析器解析这一行，支持处理跨多行的字段（如字段中包含换行符）
      if (r.length > 0) { // 如果解析结果非空，表示该行包含有效数据
        if (result == null) { // 如果result为null，表示这是第一部分数据
          result = r; // 直接将解析结果赋值给result
        } else { // 如果result不为null，表示需要合并之前的数据和当前数据
          String[] t = new String[result.length + r.length]; // 创建一个新数组，长度为两部分数据长度之和
          System.arraycopy(result, 0, t, 0, result.length); // 将之前的数据复制到新数组的前半部分
          System.arraycopy(r, 0, t, result.length, r.length); // 将当前数据复制到新数组的后半部分
          result = t; // 将合并后的数组赋值给result
        }
      }
    } while (parser.isPending()); // 继续循环，只要解析器还有未完成的多行数据需要处理
    return result; // 返回解析完成的字符串数组
  }

  /**
   * Reads the next line from the file.
   * 从文件中读取下一行，从内容队列的头部获取数据
   *
   * @return the next line from the file without trailing newline // 返回文件中的下一行，不包含尾部的换行符，如果没有更多数据则返回null
   *
   */
  private @Nullable String getNextLine() { // 私有方法：从队列中获取下一行数据
    return contentQueue.poll(); // 从队列头部移除并返回一个元素，如果队列为空则返回null
  }

  /**
   * Closes the underlying reader.
   * 关闭底层的读取器，释放相关资源
   */
  @Override public void close() { // 实现Closeable接口的close方法，用于关闭资源
  } // 当前实现为空，不执行任何操作，因为Tailer由其自身管理生命周期

  /** Watches for content being appended to a CSV file. */
  private static class CsvContentListener extends TailerListenerAdapter { // 静态内部类：监听CSV文件内容被追加的事件
    final Queue<String> contentQueue; // 内容队列的引用，用于存储从文件中读取的每一行数据

    CsvContentListener(Queue<String> contentQueue) { // 构造方法：接收一个队列作为参数
      this.contentQueue = contentQueue; // 保存队列引用，用于后续将读取的行添加到队列中
    }

    @Override public void handle(String line) { // 重写TailerListenerAdapter的handle方法，处理文件中新增的每一行
      this.contentQueue.add(line); // 将新读取的行添加到队列末尾，供CsvStreamReader读取使用
    }
  }
} // CsvStreamReader类定义结束
