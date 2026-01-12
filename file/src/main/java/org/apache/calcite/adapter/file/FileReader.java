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
package org.apache.calcite.adapter.file; // 声明包名，该类属于Calcite的文件适配器包

import org.apache.calcite.util.Source; // 导入Calcite的Source工具类，用于表示数据源

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的字段
import org.jsoup.Jsoup; // 导入Jsoup库，用于解析HTML文档
import org.jsoup.nodes.Document; // 导入Jsoup的Document类，表示HTML文档对象
import org.jsoup.nodes.Element; // 导入Jsoup的Element类，表示HTML元素
import org.jsoup.select.Elements; // 导入Jsoup的Elements类，表示HTML元素集合

import java.io.IOException; // 导入IO异常类，用于处理输入输出异常
import java.nio.charset.Charset; // 导入字符集类，用于指定字符编码
import java.nio.charset.StandardCharsets; // 导入标准字符集类，提供常用字符编码
import java.util.Arrays; // 导入数组工具类，提供数组操作方法
import java.util.Iterator; // 导入迭代器接口，用于遍历集合
import java.util.concurrent.TimeUnit; // 导入时间单位枚举，用于时间转换

import static java.util.Objects.requireNonNull; // 静态导入Objects的requireNonNull方法，用于非空检查

/**
 * Scrapes HTML tables from URLs using Jsoup. // 类说明：使用Jsoup库从URL中抓取HTML表格
 * 该类是Calcite文件适配器的核心组件，负责从HTML文件或URL中提取表格数据
 * 支持通过CSS选择器选择特定的表格，或者自动选择最大的表格
 * 实现了Iterable接口，可以像集合一样遍历表格的每一行数据
 * 
 * 主要功能：
 * 1. 从各种数据源（文件、HTTP/HTTPS URL、FTP等）读取HTML内容
 * 2. 解析HTML文档并定位表格元素
 * 3. 提取表格的表头信息
 * 4. 提供迭代器接口遍历表格的每一行
 * 5. 支持CSS选择器精确选择表格
 * 6. 支持表格索引选择（当有多个匹配时）
 * 
 * 使用场景：
 * - 将HTML网页中的表格数据作为Calcite查询的数据源
 * - 支持数据虚拟化，无需将数据导入数据库即可查询
 * - 适用于快速原型开发和数据探索
 */
public class FileReader implements Iterable<Elements> { // 定义FileReader类，实现Iterable接口，可以迭代返回Elements对象（每行的单元格集合）

  private final Source source; // 成员变量：数据源对象，表示要读取的HTML文件或URL，final修饰表示初始化后不可变
  private final @Nullable String selector; // 成员变量：CSS选择器字符串，用于精确选择HTML中的特定表格元素，可为null表示不使用选择器
  private final @Nullable Integer index; // 成员变量：表格索引，当选择器匹配多个表格时，指定使用第几个表格（从0开始），可为null
  private final Charset charset = StandardCharsets.UTF_8; // 成员变量：字符编码，默认使用UTF-8编码解析HTML文档
  private @Nullable Element tableElement; // 成员变量：解析后的HTML表格元素对象，缓存认为最优的表格，延迟初始化
  private @Nullable Elements headings; // 成员变量：表格的表头元素集合，包含<th>标签的元素，延迟初始化

  /**
   * 构造方法1：完整参数构造器
   * 
   * @param source 数据源对象，不能为null，指定要读取的HTML文件或URL
   * @param selector CSS选择器，可选参数，用于选择特定的HTML表格元素
   * @param index 表格索引，可选参数，当选择器匹配多个表格时，指定使用第几个
   * 
   * 说明：
   * - 这是最完整的构造方法，允许精确控制表格的选择
   * - source参数必须非空，否则会抛出NullPointerException
   * - selector和index可以为null，表示使用默认的表格选择策略
   * - 实际的表格解析延迟到第一次调用iterator()方法时进行
   */
  public FileReader(Source source, @Nullable String selector, // 构造方法参数：source数据源不能为null，selector选择器可为null
      @Nullable Integer index) { // 构造方法参数：index表格索引可为null
    this.source = requireNonNull(source, "source must not be null"); // 初始化source成员变量，使用requireNonNull确保source不为null
    this.selector = selector; // 初始化selector成员变量，保存CSS选择器
    this.index = index; // 初始化index成员变量，保存表格索引
  }

  /**
   * 构造方法2：带选择器的构造器
   * 
   * @param source 数据源对象，指定要读取的HTML文件或URL
   * @param selector CSS选择器，用于选择特定的HTML表格元素
   * 
   * 说明：
   * - 这是构造方法1的简化版本，默认index为null
   * - 当选择器匹配多个表格时，默认使用第一个匹配的表格
   */
  public FileReader(Source source, String selector) { // 构造方法参数：source数据源，selector选择器
    this(source, selector, null); // 调用完整参数构造方法，index设为null
  }

  /**
   * 构造方法3：最简构造器
   * 
   * @param source 数据源对象，指定要读取的HTML文件或URL
   * 
   * 说明：
   * - 最简单的构造方法，仅指定数据源
   * - 不使用CSS选择器，自动选择文档中最大的表格
   * - 适用于HTML文档中只有一个表格，或者希望使用最大表格的场景
   */
  public FileReader(Source source) { // 构造方法参数：仅source数据源
    this(source, null, null); // 调用完整参数构造方法，selector和index都设为null
  }

  /**
   * 私有方法：从数据源中获取表格元素
   * 
   * @throws FileReaderException 当读取或解析HTML失败时抛出
   * 
   * 功能说明：
   * 1. 根据数据源的协议类型（file、http、https、ftp等）选择不同的读取方式
   * 2. 使用Jsoup库解析HTML文档
   * 3. 根据是否提供了selector选择器，调用不同的表格选择策略
   * 4. 将解析结果缓存到tableElement成员变量中
   * 
   * 协议处理：
   * - file协议：直接从本地文件系统读取
   * - http/https/ftp协议：通过URL读取，设置20秒超时
   * - 其他协议：使用通用的流读取方式
   */
  private void getTable() throws FileReaderException { // 方法声明：私有方法，抛出FileReaderException异常
    final Document doc; // 声明HTML文档对象变量，用于存储解析后的文档
    try { // 开始try块，捕获可能的IO异常
      String proto = source.protocol(); // 获取数据源的协议类型（如file、http、https等）
      if ("file".equals(proto)) { // 判断如果是file协议（本地文件）
        doc = Jsoup.parse(source.file(), this.charset.name()); // 使用Jsoup解析本地文件，指定UTF-8编码
      } else if (Arrays.asList("http", "https", "ftp").contains(proto)) { // 判断如果是网络协议（http、https、ftp）
        // known protocols handled by URL // 注释说明：这些是已知的可以通过URL处理的协议
        doc = Jsoup.parse(source.url(), (int) TimeUnit.SECONDS.toMillis(20)); // 使用Jsoup解析URL内容，设置20秒超时（转换为毫秒）
      } else { // 其他未知协议
        // generically read this source // 注释说明：通用方式读取数据源
        doc = Jsoup.parse(source.openStream(), charset.name(), ""); // 使用Jsoup解析输入流，指定编码和基础URL
      }
    } catch (IOException e) { // 捕获IO异常
      throw new FileReaderException("Cannot read " + source, e); // 抛出FileReaderException，包装原始异常信息
    } // try块结束

    this.tableElement = (this.selector != null && !this.selector.isEmpty()) // 判断是否提供了非空的选择器
        ? getSelectedTable(doc, this.selector) : getBestTable(doc); // 如果有选择器则使用选择器获取表格，否则自动选择最佳表格
  }

  /**
   * 私有方法：根据CSS选择器获取指定的表格元素
   * 
   * @param doc 已解析的HTML文档对象
   * @param selector CSS选择器字符串
   * @return 选中的表格元素
   * @throws FileReaderException 当选择器匹配元素数量不正确或选中的不是表格时抛出
   * 
   * 功能说明：
   * 1. 使用CSS选择器从文档中选中所有匹配的元素
   * 2. 根据index参数决定选择哪个元素：
   *    - 如果index为null，要求必须恰好匹配1个元素
   *    - 如果index不为null，选择指定索引的元素
   * 3. 验证选中的元素确实是table标签
   * 
   * CSS选择器示例：
   * - "table"：选择所有表格
   * - "#mytable"：选择id为mytable的元素
   * - ".data-table"：选择class为data-table的元素
   * - "div.content > table"：选择div.content下的直接子表格
   */
  private Element getSelectedTable(Document doc, String selector) // 方法声明：私有方法，接收文档和选择器，返回表格元素
      throws FileReaderException { // 方法声明：抛出FileReaderException异常
    // get selected elements // 注释说明：获取选中的元素集合
    Elements list = doc.select(selector); // 使用CSS选择器从文档中选择所有匹配的元素

    // get the element // 注释说明：获取具体的元素
    Element el; // 声明元素变量，用于存储最终选择的元素

    if (this.index == null) { // 判断如果没有指定索引
      if (list.size() != 1) { // 检查匹配的元素数量是否不等于1
        throw new FileReaderException(list.size() // 抛出异常，提示匹配的元素数量不正确
            + " HTML element(s) selected"); // 异常消息：显示匹配的元素数量
      } // if块结束

      el = list.first(); // 获取第一个（也是唯一的）匹配元素
    } else { // 如果指定了索引
      el = list.get(this.index); // 获取指定索引的元素
    } // if-else块结束

    // verify element is a table // 注释说明：验证元素是否为表格
    if (el.tag().getName().equals("table")) { // 检查元素的标签名是否为"table"
      return el; // 如果是表格，直接返回该元素
    } else { // 如果不是表格
      throw new FileReaderException("selected (" + selector + ") element is a " // 抛出异常，提示选中的元素不是表格
          + el.tag().getName() + ", not a table"); // 异常消息：显示选择器和实际的标签名
    } // if-else块结束
  }

  /**
   * 私有静态方法：从文档中自动选择最佳的表格
   * 
   * @param doc 已解析的HTML文档对象
   * @return 评分最高的表格元素
   * @throws FileReaderException 当文档中没有找到任何表格时抛出
   * 
   * 功能说明：
   * 1. 遍历文档中的所有表格元素
   * 2. 对每个表格计算评分：评分 = 行数 × 列数
   * 3. 选择评分最高的表格作为"最佳"表格
   * 
   * 算法原理：
   * - 假设包含最多数据的表格是用户最感兴趣的表格
   * - 行数和列数的乘积可以反映表格的数据量
   * - 这种启发式方法适用于大多数简单的HTML表格场景
   * 
   * 注意事项：
   * - 如果文档中有多个相同大小的表格，会选择第一个
   * - 可能不适合复杂的嵌套表格场景
   */
  private static Element getBestTable(Document doc) throws FileReaderException { // 方法声明：私有静态方法，接收文档，返回表格元素
    Element bestTable = null; // 声明最佳表格变量，初始化为null
    int bestScore = -1; // 声明最佳评分变量，初始化为-1

    for (Element t : doc.select("table")) { // 遍历文档中的所有表格元素
      int rows = t.select("tr").size(); // 获取当前表格的行数（tr标签数量）
      Element firstRow = t.select("tr").get(0); // 获取表格的第一行元素
      int cols = firstRow.select("th,td").size(); // 获取第一行的列数（th或td标签数量）
      int thisScore = rows * cols; // 计算当前表格的评分（行数×列数）
      if (thisScore > bestScore) { // 如果当前评分高于最佳评分
        bestTable = t; // 更新最佳表格为当前表格
        bestScore = thisScore; // 更新最佳评分为当前评分
      } // if块结束
    } // for循环结束

    if (bestTable == null) { // 检查是否没有找到任何表格
      throw new FileReaderException("no tables found"); // 抛出异常，提示文档中没有表格
    } // if块结束

    return bestTable; // 返回找到的最佳表格
  }

  /**
   * 包级私有方法：刷新表格数据
   * 
   * @throws FileReaderException 当重新读取或解析失败时抛出
   * 
   * 功能说明：
   * 1. 清除缓存的表头信息
   * 2. 重新从数据源读取并解析表格
   * 
   * 使用场景：
   * - 当数据源内容发生变化时，需要重新读取
   * - 用于测试和调试，确保获取最新数据
   * 
   * 注意事项：
   * - 该方法不会清除缓存的tableElement，但会重新调用getTable()
   * - 调用后需要重新创建迭代器才能获取新数据
   */
  void refresh() throws FileReaderException { // 方法声明：包级私有方法，抛出FileReaderException异常
    this.headings = null; // 清除缓存的表头信息，设置为null
    getTable(); // 重新调用getTable方法，从数据源读取并解析表格
  }

  /**
   * 包级私有方法：获取表格的表头元素
   * 
   * @return 表头元素集合（Elements对象）
   * 
   * 功能说明：
   * 1. 如果表头尚未缓存，通过调用iterator()方法触发表头提取
   * 2. 返回缓存的表头元素集合
   * 
   * 表头提取逻辑：
   * - 优先使用第一行的<th>标签作为表头
   * - 如果第一行没有<th>标签，则使用第一行的<td>标签生成默认表头
   * - 默认表头命名为"col0"、"col1"、"col2"等
   * 
   * 注意事项：
   * - 该方法会触发表格的延迟加载（如果尚未加载）
   * - 表头提取逻辑在iterator()方法中实现
   */
  Elements getHeadings() { // 方法声明：包级私有方法，返回表头元素集合
    if (this.headings == null) { // 检查表头是否尚未缓存
      this.iterator(); // 调用iterator方法，该方法会触发表头的提取和缓存
    } // if块结束

    return this.headings; // 返回缓存的表头元素集合
  }

  /**
   * 公共方法：创建并返回表格行的迭代器
   * 
   * @return FileReaderIterator对象，用于迭代表格的每一行
   * 
   * 功能说明：
   * 1. 如果表格尚未加载，调用getTable()方法加载表格
   * 2. 创建FileReaderIterator迭代器，用于遍历表格的每一行
   * 3. 提取并缓存表头信息
   * 
   * 表头提取逻辑：
   * - 尝试从第一行提取<th>标签作为表头
   * - 如果第一行没有<th>，则使用第一行的<td>生成默认列名（col0, col1, ...）
   * - 表头提取后会回退迭代器，确保查询时能看到第一行数据
   * 
   * 异常处理：
   * - 将检查异常（Exception）转换为运行时异常（RuntimeException）
   * - 保留RuntimeException和Error的原样抛出
   * 
   * 注意事项：
   * - 每次调用都会创建新的迭代器对象
   * - TODO注释表明表头缓存逻辑需要重构
   * - 当前实现中，headings检查被硬编码为true，每次都会重新提取表头
   */
  @Override public FileReaderIterator iterator() { // 方法声明：公共方法，重写Iterable接口的iterator方法
    if (this.tableElement == null) { // 检查表格元素是否尚未加载
      try { // 开始try块，捕获可能的异常
        getTable(); // 调用getTable方法加载表格
      } catch (RuntimeException | Error e) { // 捕获运行时异常和错误
        throw e; // 原样抛出运行时异常和错误
      } catch (Exception e) { // 捕获其他检查异常
        throw new RuntimeException(e); // 将检查异常包装为运行时异常后抛出
      } // try-catch块结束
    } // if块结束

    FileReaderIterator iterator = // 创建FileReaderIterator迭代器对象
        new FileReaderIterator(this.tableElement.select("tr")); // 传入表格的所有行元素（tr标签集合）

    // if we haven't cached the headings, get them // 注释说明：如果表头尚未缓存，则获取表头
    // TODO: this needs to be reworked to properly cache the headings // TODO注释：需要重构以正确缓存表头
    if (/* this.headings == null */ true) { // 条件判断：当前硬编码为true，每次都会执行表头提取
      // first row must contain headings // 注释说明：第一行必须包含表头
      Elements headings = iterator.next("th"); // 从迭代器获取下一行的<th>元素作为表头
      // if not, generate some default column names // 注释说明：如果没有表头，则生成默认列名
      if (headings.isEmpty()) { // 检查是否没有找到<th>元素
        // rewind and peek at the first row of data // 注释说明：回退并查看第一行数据
        iterator = new FileReaderIterator(this.tableElement.select("tr")); // 重新创建迭代器，重置到第一行
        Elements firstRow = iterator.next("td"); // 获取第一行的<td>元素
        int i = 0; // 声明列索引计数器，从0开始
        headings = new Elements(); // 创建新的Elements对象用于存储生成的表头
        for (Element td : firstRow) { // 遍历第一行的每个单元格
          Element th = td.clone(); // 克隆单元格元素
          th.tagName("th"); // 将克隆元素的标签名改为th
          th.html("col" + i++); // 设置表头内容为"col"加上列索引
          headings.add(th); // 将生成的表头元素添加到表头集合中
        } // for循环结束
        // rewind, so queries see the first row // 注释说明：回退，以便查询能看到第一行
        iterator = new FileReaderIterator(this.tableElement.select("tr")); // 再次重新创建迭代器，重置到第一行
      } // if块结束
      this.headings = headings; // 将提取或生成的表头缓存到成员变量中
    } // if块结束

    return iterator; // 返回创建的迭代器对象
  }

  /**
   * 公共方法：关闭文件读取器
   * 
   * 功能说明：
   * - 当前实现为空方法，不执行任何操作
   * - 保留该接口是为了兼容性和未来扩展
   * 
   * 使用场景：
   * - 在try-with-resources语句中使用时需要实现AutoCloseable接口
   * - 未来可以添加资源清理逻辑，如关闭文件流等
   * 
   * 注意事项：
   * - 当前FileReader使用Jsoup库，资源管理由Jsoup自动处理
   * - 如果未来添加需要手动管理的资源，可以在此方法中实现清理逻辑
   */
  public void close() { // 方法声明：公共方法，无参数无返回值
  } // 空方法体，当前不执行任何操作

  /**
   * 私有静态内部类：FileReaderIterator
   * 
   * 功能说明：
   * - 实现Iterator<Elements>接口，用于迭代HTML表格的每一行
   * - 每次迭代返回一个Elements对象，包含该行的所有单元格（th或td）
   * - 支持自定义选择器来选择特定类型的单元格
   * 
   * 设计模式：
   * - 迭代器模式：提供统一的方式遍历表格数据
   * - 适配器模式：将HTML表格结构适配为迭代器接口
   * 
   * 使用方式：
   * - 默认情况下，next()返回每行的th和td元素
   * - 可以使用next(selector)方法指定自定义选择器
   * 
   * 注意事项：
   * - 不支持remove操作，调用会抛出UnsupportedOperationException
   * - 迭代器是基于原始表格元素的视图，修改会影响原始数据
   */
  /** Iterates over HTML tables, returning an Elements per row. */ // 类注释：迭代HTML表格，每行返回一个Elements对象
  private static class FileReaderIterator implements Iterator<Elements> { // 定义私有静态内部类，实现Iterator接口，泛型为Elements
    final Iterator<Element> rowIterator; // 成员变量：行元素的迭代器，用于遍历表格的每一行（tr元素），final修饰表示不可变

    /**
     * 构造方法：初始化行迭代器
     * 
     * @param rows 表格的所有行元素集合（Elements对象）
     * 
     * 功能说明：
     * - 将传入的Elements集合转换为Iterator<Element>
     * - 保存行迭代器以便后续遍历
     * 
     * 参数说明：
     * - rows通常是通过tableElement.select("tr")获取的所有表格行
     */
    FileReaderIterator(Elements rows) { // 构造方法参数：接收表格行的Elements集合
      this.rowIterator = rows.iterator(); // 将Elements集合转换为迭代器并保存
    }

    /**
     * 方法：检查是否还有下一行
     * 
     * @return 如果还有下一行返回true，否则返回false
     * 
     * 功能说明：
     * - 委托给底层行迭代器的hasNext()方法
     * - 用于在循环中判断是否继续迭代
     */
    @Override public boolean hasNext() { // 方法声明：重写Iterator接口的hasNext方法
      return this.rowIterator.hasNext(); // 返回行迭代器的hasNext结果
    }

    /**
     * 方法：获取下一行的指定选择器匹配的元素
     * 
     * @param selector CSS选择器字符串，用于选择行中的特定元素
     * @return 匹配选择器的元素集合（Elements对象）
     * 
     * 功能说明：
     * - 从行迭代器获取下一行元素
     * - 使用CSS选择器从该行中选择匹配的子元素
     * 
     * 使用示例：
     * - next("th")：获取该行的所有表头单元格
     * - next("td")：获取该行的所有数据单元格
     * - next("td.col1")：获取该行中class为col1的数据单元格
     * 
     * 注意事项：
     * - 每次调用都会推进到下一行
     * - 如果没有更多行，会抛出NoSuchElementException
     */
    Elements next(String selector) { // 方法声明：包级私有方法，接收选择器参数，返回Elements对象
      Element row = this.rowIterator.next(); // 从行迭代器获取下一行元素

      return row.select(selector); // 使用CSS选择器从该行中选择匹配的元素并返回
    }

    // return th and td elements by default // 注释说明：默认返回th和td元素
    /**
     * 方法：获取下一行的所有单元格元素
     * 
     * @return 该行的所有th和td元素集合（Elements对象）
     * 
     * 功能说明：
     * - 这是Iterator接口的标准next()方法实现
     * - 默认选择器为"th,td"，即选择表头单元格和数据单元格
     * - 内部调用next("th,td")方法实现
     * 
     * 使用场景：
     * - 在标准for-each循环中使用
     * - 获取表格行的所有单元格内容
     * 
     * 注意事项：
     * - 每次调用都会推进到下一行
     * - 如果没有更多行，会抛出NoSuchElementException
     */
    @Override public Elements next() { // 方法声明：重写Iterator接口的next方法
      return next("th,td"); // 调用带选择器的next方法，选择th和td元素
    }

    /**
     * 方法：移除当前行（不支持）
     * 
     * @throws UnsupportedOperationException 总是抛出此异常
     * 
     * 功能说明：
     * - Iterator接口要求实现此方法，但FileReader不支持删除操作
     * - 抛出UnsupportedOperationException表示不支持
     * 
     * 设计考虑：
     * - HTML表格是只读数据源，不应支持修改
     * - 保持迭代器的不可变性，避免副作用
     * 
     * 异常消息：
     * - "NFW - can't remove!"：NFW可能表示"No Fucking Way"，强调不支持删除
     */
    @Override public void remove() { // 方法声明：重写Iterator接口的remove方法
      throw new UnsupportedOperationException("NFW - can't remove!"); // 抛出不支持操作异常
    } // 方法结束
  } // 内部类结束
} // FileReader类结束
