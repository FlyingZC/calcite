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
package org.apache.calcite.adapter.splunk.search; // 声明包名，该类属于org.apache.calcite.adapter.splunk.search包

import org.apache.calcite.adapter.splunk.util.StringUtils; // 导入字符串工具类，用于处理字符串编码解码
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历查询结果
import org.apache.calcite.linq4j.Linq4j; // 导入Linq4j工具类，提供空枚举器等
import org.apache.calcite.util.Unsafe; // 导入不安全工具类，提供系统退出等方法
import org.apache.calcite.util.Util; // 导入通用工具类，提供读取器等方法

import au.com.bytecode.opencsv.CSVReader; // 导入CSV读取器，用于解析Splunk返回的CSV格式数据

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的参数
import org.slf4j.Logger; // 导入日志记录器接口
import org.slf4j.LoggerFactory; // 导入日志工厂类，用于创建日志记录器

import java.io.BufferedReader; // 导入缓冲读取器，用于高效读取文本
import java.io.Closeable; // 导入可关闭接口，用于资源管理
import java.io.IOException; // 导入IO异常类
import java.io.InputStream; // 导入输入流接口
import java.io.InputStreamReader; // 导入输入流读取器，将字节流转换为字符流
import java.io.PrintWriter; // 导入打印写入器，用于写入文本
import java.io.StringWriter; // 导入字符串写入器，用于构建字符串
import java.net.MalformedURLException; // 导入URL格式异常类
import java.net.URI; // 导入URI类，用于统一资源标识符
import java.net.URL; // 导入URL类，用于统一资源定位符
import java.nio.charset.StandardCharsets; // 导入标准字符集，指定UTF-8编码
import java.util.Arrays; // 导入数组工具类
import java.util.HashMap; // 导入哈希映射类，用于存储键值对
import java.util.LinkedHashMap; // 导入链式哈希映射类，保持插入顺序
import java.util.List; // 导入列表接口
import java.util.Locale; // 导入区域设置类，用于本地化
import java.util.Map; // 导入映射接口
import java.util.regex.Matcher; // 导入正则匹配器，用于模式匹配
import java.util.regex.Pattern; // 导入正则表达式类

import static org.apache.calcite.runtime.HttpUtils.appendURLEncodedArgs; // 导入URL编码参数追加方法
import static org.apache.calcite.runtime.HttpUtils.post; // 导入HTTP POST方法

import static java.lang.Boolean.parseBoolean; // 导入布尔值解析方法
import static java.util.Objects.requireNonNull; // 导入非空检查方法

/**
 * Implementation of {@link SplunkConnection} based on Splunk's REST API.
 * // 基于Splunk REST API的SplunkConnection接口实现类
 * // 该类负责与Splunk服务器建立连接、执行搜索查询并返回结果
 * // 主要功能：
 * // 1. 通过用户名密码登录Splunk服务器获取会话密钥
 * // 2. 执行搜索查询并获取结果
 * // 3. 解析返回的CSV格式数据
 * // 4. 提供枚举器接口供外部遍历结果
 */
public class SplunkConnectionImpl implements SplunkConnection { // 定义SplunkConnectionImpl类，实现SplunkConnection接口
  private static final Logger LOGGER = // 声明静态日志记录器，用于记录运行时信息
      LoggerFactory.getLogger(SplunkConnectionImpl.class); // 创建当前类的日志记录器实例

  private static final Pattern SESSION_KEY = // 声明静态正则表达式模式，用于从Splunk登录响应中提取会话密钥
      Pattern.compile( // 编译正则表达式
          "<sessionKey>([0-9a-zA-Z^_]+)</sessionKey>"); // 匹配sessionKey标签内的内容，包含数字、字母、下划线和^符号

  final URL url; // Splunk服务器的URL地址，包含协议、主机名和端口
  final String username; // 登录Splunk服务器的用户名
  final String password; // 登录Splunk服务器的密码
  String sessionKey; // 登录成功后获取的会话密钥，用于后续API调用的身份验证
  final Map<String, String> requestHeaders = new HashMap<>(); // HTTP请求头映射，存储Authorization等请求头信息

  public SplunkConnectionImpl(String url, String username, String password) // 构造方法，接收字符串格式的URL、用户名和密码
      throws MalformedURLException { // 声明可能抛出URL格式异常
    this(URI.create(url).toURL(), username, password); // 调用另一个构造方法，将字符串URL转换为URL对象
  } // 构造方法结束

  public SplunkConnectionImpl(URL url, String username, String password) { // 构造方法，接收URL对象、用户名和密码
    this.url      = url; // 保存Splunk服务器URL
    this.username = username; // 保存用户名
    this.password = password; // 保存密码
    connect(); // 调用connect方法建立连接并获取会话密钥
  } // 构造方法结束

  private static void close(Closeable c) { // 静态方法，安全地关闭可关闭资源
    try { // 尝试关闭资源
      c.close(); // 调用close方法关闭资源
    } catch (Exception ignore) { // 捕获所有异常
      // ignore // 忽略异常，不进行处理
    } // 异常处理结束
  } // close方法结束

  @SuppressWarnings("CatchAndPrintStackTrace") // 抑制捕获异常并打印堆栈的警告
  private void connect() { // 私有方法，建立与Splunk服务器的连接并获取会话密钥
    BufferedReader rd = null; // 声明缓冲读取器，用于读取登录响应

    try { // 尝试建立连接
      String loginUrl = // 构建登录URL
          String.format(Locale.ROOT, // 使用根区域设置格式化字符串
              "%s://%s:%d/services/auth/login", // URL格式：协议://主机:端口/services/auth/login
              url.getProtocol(), // 获取协议（http或https）
              url.getHost(), // 获取主机名
              url.getPort()); // 获取端口号

      StringBuilder data = new StringBuilder(); // 创建字符串构建器，用于存储POST请求数据
      appendURLEncodedArgs( // 追加URL编码的参数
          data, "username", username, "password", password); // 添加用户名和密码参数

      rd = Util.reader(post(loginUrl, data, requestHeaders)); // 发送POST请求并获取响应的读取器

      String line; // 声明字符串变量，用于存储读取的每一行
      StringBuilder reply = new StringBuilder(); // 创建字符串构建器，用于存储完整的响应
      while ((line = rd.readLine()) != null) { // 循环读取响应的每一行
        reply.append(line); // 追加行内容
        reply.append("\n"); // 追加换行符
      } // 循环结束

      Matcher m = SESSION_KEY.matcher(reply); // 创建正则匹配器，在响应中查找会话密钥
      if (m.find()) { // 如果找到匹配
        sessionKey = m.group(1); // 提取会话密钥（第一个捕获组）
        requestHeaders.put("Authorization", "Splunk " + sessionKey); // 将会话密钥添加到请求头，格式为"Splunk <sessionKey>"
      } // if结束
    } catch (Exception e) { // 捕获所有异常
      e.printStackTrace(); // 打印异常堆栈跟踪
    } finally { // finally块，确保资源被释放
      close(rd); // 关闭读取器
    } // finally结束
  } // connect方法结束

  @Override public void getSearchResults(String search, Map<String, String> otherArgs, // 重写接口方法，执行搜索并通过监听器回调处理结果
      @Nullable List<String> fieldList, SearchResultListener srl) { // 参数：搜索语句、其他参数、字段列表（可为空）、结果监听器
    requireNonNull(srl, "srl"); // 检查监听器不为空，否则抛出异常
    Enumerator<Object> x = getSearchResults_(search, otherArgs, fieldList, srl); // 调用内部方法执行搜索，传入监听器
    assert x == null; // 断言返回值为null，因为使用监听器模式时不需要返回枚举器
  } // getSearchResults方法结束

  @Override public Enumerator<Object> getSearchResultEnumerator(String search, // 重写接口方法，执行搜索并返回结果枚举器
      Map<String, String> otherArgs, @Nullable List<String> fieldList) { // 参数：搜索语句、其他参数、字段列表（可为空）
    return requireNonNull( // 调用内部方法执行搜索，不传入监听器，确保返回值不为空
        getSearchResults_(search, otherArgs, fieldList, null)); // 返回结果枚举器
  } // getSearchResultEnumerator方法结束

  private @Nullable Enumerator<Object> getSearchResults_( // 私有方法，内部实现搜索逻辑，根据参数返回枚举器或null
      String search, // 参数：搜索语句
      Map<String, String> otherArgs, // 参数：其他搜索参数映射
      @Nullable List<String> wantedFields, // 参数：需要的字段列表（可为空）
      @Nullable SearchResultListener srl) { // 参数：结果监听器（可为空）
    String searchUrl = // 构建搜索导出URL
        String.format(Locale.ROOT, // 使用根区域设置格式化字符串
            "%s://%s:%d/services/search/jobs/export", // URL格式：协议://主机:端口/services/search/jobs/export
            url.getProtocol(), // 获取协议
            url.getHost(), // 获取主机名
            url.getPort()); // 获取端口号

    StringBuilder data = new StringBuilder(); // 创建字符串构建器，用于存储POST请求数据
    Map<String, String> args = new LinkedHashMap<>(otherArgs); // 创建链式哈希映射，复制其他参数并保持插入顺序
    args.put("search", search); // 添加搜索语句参数
    // override these args // 覆盖以下参数
    args.put("output_mode", "csv"); // 设置输出模式为CSV格式
    args.put("preview", "0"); // 设置预览模式为0，不返回预览结果

    // TODO: remove this once the csv parser can handle leading spaces // TODO：待CSV解析器能处理前导空格后移除此参数
    args.put("check_connection", "0"); // 设置检查连接为0，避免CSV解析问题

    appendURLEncodedArgs(data, args); // 将所有参数URL编码后追加到请求数据中
    try { // 尝试执行搜索请求
      // wait at most 30 minutes for first result // 最多等待30分钟获取第一个结果
      InputStream in = // 发送POST请求获取输入流
          post(searchUrl, data, requestHeaders, 10000, 1800000); // 参数：URL、数据、请求头、连接超时10秒、读取超时30分钟
      if (srl == null) { // 如果没有提供监听器
        return new SplunkResultEnumerator(in, wantedFields); // 返回结果枚举器，用于遍历结果
      } else { // 如果提供了监听器
        parseResults( // 解析结果并通过监听器回调
            in, // 输入流
            srl); // 结果监听器
        return null; // 返回null，因为结果已通过监听器处理
      } // if-else结束
    } catch (Exception e) { // 捕获所有异常
      StringWriter sw = new StringWriter(); // 创建字符串写入器
      e.printStackTrace(new PrintWriter(sw)); // 将异常堆栈跟踪写入字符串
      LOGGER.warn("{}\n{}", e.getMessage(), sw); // 记录警告日志，包含异常消息和堆栈
      return srl == null ? Linq4j.emptyEnumerator() : null; // 如果没有监听器返回空枚举器，否则返回null
    } // try-catch结束
  } // getSearchResults_方法结束

  private static void parseResults(InputStream in, SearchResultListener srl) { // 静态方法，解析CSV格式的搜索结果并通过监听器回调
    try (CSVReader r = // 使用try-with-resources创建CSV读取器，自动关闭资源
             new CSVReader( // 创建CSV读取器
                 new BufferedReader( // 包装缓冲读取器提高效率
                     new InputStreamReader(in, StandardCharsets.UTF_8)))) { // 将输入流转换为UTF-8编码的字符流
      String[] header = r.readNext(); // 读取CSV的第一行作为表头
      if (header != null // 检查表头不为空
          && header.length > 0 // 检查表头长度大于0
          && !(header.length == 1 && header[0].isEmpty())) { // 检查表头不是单个空字符串
        srl.setFieldNames(header); // 调用监听器设置字段名

        String[] line; // 声明字符串数组，用于存储每行数据
        while ((line = r.readNext()) != null) { // 循环读取CSV的每一行
          if (line.length == header.length) { // 检查行数据长度与表头长度一致
            srl.processSearchResult(line); // 调用监听器处理搜索结果
          } // if结束
        } // while循环结束
      } // if结束
    } catch (IOException ignore) { // 捕获IO异常
      StringWriter sw = new StringWriter(); // 创建字符串写入器
      ignore.printStackTrace(new PrintWriter(sw)); // 将异常堆栈跟踪写入字符串
      LOGGER.warn("{}\n{}", ignore.getMessage(), sw); // 记录警告日志
    } // try-catch结束
  } // parseResults方法结束

  public static void parseArgs(String[] args, Map<String, String> map) { // 静态方法，解析命令行参数并存储到映射中
    for (int i = 0; i < args.length; i++) { // 遍历命令行参数数组
      String argName = args[i++]; // 获取参数名（以-开头），并递增索引
      String argValue = i < args.length ? args[i] : ""; // 获取参数值，如果没有则为空字符串

      if (!argName.startsWith("-")) { // 检查参数名是否以-开头
        throw new IllegalArgumentException("invalid argument name: " + argName // 抛出非法参数异常
            + ". Argument names must start with -"); // 提示参数名必须以-开头
      } // if结束
      map.put(argName.substring(1), argValue); // 将参数名（去掉-）和参数值存入映射
    } // for循环结束
  } // parseArgs方法结束

  public static void printUsage(String errorMsg) { // 静态方法，打印使用说明并退出程序
    String[] strings = { // 定义使用说明字符串数组
        "Usage: java Connection -<arg-name> <arg-value>", // 使用方法
        "The following <arg-name> are valid", // 有效的参数名
        "search        - required, search string to execute", // search参数说明
        "field_list    - " // field_list参数说明
          + "required, list of fields to request, comma delimited", // 字段列表，逗号分隔
        "uri           - " // uri参数说明
          + "uri to splunk's mgmt port, default: https://localhost:8089", // Splunk管理端口URI
        "username      - " // username参数说明
          + "username to use for authentication, default: admin", // 认证用户名
        "password      - " // password参数说明
          + "password to use for authentication, default: changeme", // 认证密码
        "earliest_time - earliest time for the search, default: -24h", // 搜索最早时间
        "latest_time   - latest time for the search, default: now", // 搜索最晚时间
        "-print        - whether to print results or just the summary" // 是否打印结果
    }; // 字符串数组定义结束
    System.err.println(errorMsg); // 打印错误消息到标准错误输出
    for (String s : strings) { // 遍历使用说明字符串
      System.err.println(s); // 打印每一行到标准错误输出
    } // for循环结束
    Unsafe.systemExit(1); // 调用系统退出方法，退出码为1表示错误
  } // printUsage方法结束

  public static void main(String[] args) throws MalformedURLException { // 主方法，程序入口点，用于测试Splunk连接和搜索功能
    Map<String, String> argsMap = new HashMap<>(); // 创建参数映射，存储默认值
    argsMap.put("uri",           "https://localhost:8089"); // 设置默认Splunk URI
    argsMap.put("username",      "admin"); // 设置默认用户名
    argsMap.put("password",      "changeme"); // 设置默认密码
    argsMap.put("earliest_time", "-24h"); // 设置默认最早时间为24小时前
    argsMap.put("latest_time",   "now"); // 设置默认最晚时间为现在
    argsMap.put("-print",        "true"); // 设置默认打印结果为true

    parseArgs(args, argsMap); // 解析命令行参数，覆盖默认值

    String search = argsMap.get("search"); // 获取搜索语句参数
    String field_list = argsMap.get("field_list"); // 获取字段列表参数

    if (search == null) { // 检查搜索语句是否为空
      printUsage("Missing required argument: search"); // 打印使用说明并退出
      return; // 返回（实际上不会执行，因为printUsage会退出）
    } // if结束
    if (field_list == null) { // 检查字段列表是否为空
      printUsage("Missing required argument: field_list"); // 打印使用说明并退出
      return; // 返回（实际上不会执行，因为printUsage会退出）
    } // if结束

    List<String> fieldList = StringUtils.decodeList(field_list, ','); // 解码字段列表，将逗号分隔的字符串转换为列表

    SplunkConnection c = // 创建Splunk连接对象
        new SplunkConnectionImpl( // 调用构造方法
            argsMap.get("uri"), // 传入URI
            argsMap.get("username"), // 传入用户名
            argsMap.get("password")); // 传入密码

    Map<String, String> searchArgs = new HashMap<>(); // 创建搜索参数映射
    searchArgs.put("earliest_time", argsMap.get("earliest_time")); // 添加最早时间参数
    searchArgs.put("latest_time", argsMap.get("latest_time")); // 添加最晚时间参数
    searchArgs.put("field_list", // 添加字段列表参数
        StringUtils.encodeList(fieldList, ',').toString()); // 将字段列表编码为字符串


    CountingSearchResultListener dummy = // 创建计数搜索结果监听器
        new CountingSearchResultListener( // 调用构造方法
            parseBoolean(argsMap.get("-print"))); // 解析-print参数确定是否打印结果
    long start = System.currentTimeMillis(); // 记录开始时间
    c.getSearchResults(search, searchArgs, null, dummy); // 执行搜索，结果通过监听器处理

    System.out.printf(Locale.ROOT, "received %d results in %dms\n", // 打印结果统计信息
        dummy.getResultCount(), // 获取结果数量
        System.currentTimeMillis() - start); // 计算并打印耗时
  } // main方法结束

  /** Implementation of // 实现类
   * {@link SearchResultListener} // SearchResultListener接口
   * interface that just counts the results. */ // 该接口实现类仅用于计数搜索结果
  public static class CountingSearchResultListener // 定义静态内部类CountingSearchResultListener
      implements SearchResultListener { // 实现SearchResultListener接口
    String @Nullable[] fieldNames; // 字段名数组，可为空，存储结果集的字段名
    int resultCount = 0; // 结果计数器，初始值为0
    final boolean print; // 布尔标志，决定是否打印结果，final表示不可变

    public CountingSearchResultListener(boolean print) { // 构造方法，接收打印标志参数
      this.print = print; // 保存打印标志
    } // 构造方法结束

    @Override public void setFieldNames(String[] fieldNames) { // 重写接口方法，设置字段名
      this.fieldNames = fieldNames; // 保存字段名数组
    } // setFieldNames方法结束

    @Override public boolean processSearchResult(String[] values) { // 重写接口方法，处理每一条搜索结果
      resultCount++; // 结果计数器加1
      if (print) { // 如果需要打印结果
        requireNonNull(fieldNames, "fieldNames"); // 检查字段名不为空
        for (int i = 0; i < fieldNames.length; ++i) { // 遍历所有字段
          System.out.printf(Locale.ROOT, "%s=%s\n", fieldNames[i], values[i]); // 打印字段名和值
        } // for循环结束
        System.out.println(); // 打印空行分隔结果
      } // if结束
      return true; // 返回true表示继续处理下一条结果
    } // processSearchResult方法结束

    public int getResultCount() { // 公共方法，获取结果计数
      return resultCount; // 返回结果数量
    } // getResultCount方法结束
  } // CountingSearchResultListener类结束

  /** Implementation of {@link org.apache.calcite.linq4j.Enumerator} that parses // 实现枚举器接口，用于解析
   * results from a Splunk REST call. // Splunk REST调用返回的结果
   *
   * <p>The element type is either {@code String} or {@code String[]}, depending // 元素类型可以是String或String[]
   * on the value of {@code source}. */ // 取决于source的值
  public static class SplunkResultEnumerator implements Enumerator<Object> { // 定义静态内部类SplunkResultEnumerator，实现枚举器接口
    private final CSVReader csvReader; // CSV读取器，用于解析CSV格式数据，final表示不可变
    private String @Nullable [] fieldNames; // 字段名数组，可为空，存储CSV表头
    private int @Nullable [] sources; // 源索引数组，可为空，用于字段重映射
    private @Nullable Object current; // 当前元素，可为空，存储当前遍历的结果

    /**
     * Where to find the singleton field, or whether to map. Values:
     * // 查找单个字段的位置，或者是否需要映射。可能值：
     *
     * <ul>
     * <li>Non-negative The index of the sole field</li> // 非负数：单个字段的索引
     * <li>-1 Generate a singleton null field for every record</li> // -1：为每条记录生成单个null字段
     * <li>-2 Return line intact</li> // -2：原样返回整行数据
     * <li>-3 Use sources to re-map</li> // -3：使用sources数组进行重映射
     * </ul>
     */
    private int source; // 源类型标识，决定如何处理结果数据

    public SplunkResultEnumerator(InputStream in, // 构造方法，接收输入流和需要的字段列表
        @Nullable List<String> wantedFields) { // 参数：输入流、需要的字段列表（可为空）
      csvReader = // 创建CSV读取器
          new CSVReader( // 实例化CSVReader
              new BufferedReader( // 包装缓冲读取器
                  new InputStreamReader(in, StandardCharsets.UTF_8))); // 将输入流转换为UTF-8字符流
      try { // 尝试读取表头
        fieldNames = csvReader.readNext(); // 读取CSV第一行作为表头
        if (fieldNames == null // 检查表头为空
            || fieldNames.length == 0 // 检查表头长度为0
            || fieldNames.length == 1 && fieldNames[0].isEmpty()) { // 检查表头是单个空字符串
          // do nothing // 不做任何处理
        } else { // 表头有效
          final List<String> headerList = Arrays.asList(fieldNames); // 将表头数组转换为列表
          requireNonNull(wantedFields, "wantedFields"); // 检查需要的字段列表不为空
          if (wantedFields.size() == 1) { // 如果只需要一个字段
            // Yields 0 or higher if wanted field exists. // 如果字段存在，返回0或更大的索引
            // Yields -1 if wanted field does not exist. // 如果字段不存在，返回-1
            source = headerList.indexOf(wantedFields.get(0)); // 查找字段在表头中的索引
            assert source >= -1; // 断言索引大于等于-1
            sources = null; // 不需要sources数组
          } else if (wantedFields.equals(headerList)) { // 如果需要的字段与表头完全相同
            source = -2; // 设置source为-2，表示原样返回整行
          } else { // 需要的字段是表头的子集或需要重排序
            source = -3; // 设置source为-3，表示需要使用sources数组重映射
            sources = new int[wantedFields.size()]; // 创建sources数组，长度为需要的字段数量
            int i = 0; // 初始化索引
            for (String wantedField : wantedFields) { // 遍历需要的字段
              sources[i++] = headerList.indexOf(wantedField); // 查找每个字段在表头中的索引并存储
            } // for循环结束
          } // if-else结束
        } // if-else结束
      } catch (IOException ignore) { // 捕获IO异常
        StringWriter sw = new StringWriter(); // 创建字符串写入器
        ignore.printStackTrace(new PrintWriter(sw)); // 将异常堆栈跟踪写入字符串
        LOGGER.warn("{}\n{}", ignore.getMessage(), sw); // 记录警告日志
      } // try-catch结束
    } // 构造方法结束

    @Override public Object current() { // 重写接口方法，获取当前元素
      return current; // 返回当前元素
    } // current方法结束

    @Override public boolean moveNext() { // 重写接口方法，移动到下一个元素
      try { // 尝试读取下一行
        String[] line; // 声明字符串数组，用于存储每行数据
        while ((line = csvReader.readNext()) != null) { // 循环读取CSV的每一行
          if (line.length == fieldNames.length) { // 检查行数据长度与表头长度一致
            switch (source) { // 根据source值决定如何处理数据
            case -3: // source为-3，需要重映射
              // Re-map using sources // 使用sources数组重映射
              String[] mapped = new String[sources.length]; // 创建映射后的数组
              for (int i = 0; i < sources.length; i++) { // 遍历sources数组
                int source1 = sources[i]; // 获取源索引
                mapped[i] = source1 < 0 ? null : line[source1]; // 如果索引有效则取值，否则为null
              } // for循环结束
              this.current = mapped; // 设置当前元素为映射后的数组
              break; // 跳出switch
            case -2: // source为-2，原样返回
              // Return line as is. No need to re-map. // 原样返回整行，不需要重映射
              current = line; // 设置当前元素为整行数据
              break; // 跳出switch
            case -1: // source为-1，返回null
              // Singleton null // 单个null字段
              this.current = null; // 设置当前元素为null
              break; // 跳出switch
            default: // source为非负数，返回指定字段
              this.current = line[source]; // 设置当前元素为指定索引的字段值
              break; // 跳出switch
            } // switch结束
            return true; // 返回true表示成功移动到下一个元素
          } // if结束
        } // while循环结束
      } catch (IOException ignore) { // 捕获IO异常
        StringWriter sw = new StringWriter(); // 创建字符串写入器
        ignore.printStackTrace(new PrintWriter(sw)); // 将异常堆栈跟踪写入字符串
        LOGGER.warn("{}\n{}", ignore.getMessage(), sw); // 记录警告日志
      } // try-catch结束
      return false; // 返回false表示没有更多元素
    } // moveNext方法结束

    @Override public void reset() { // 重写接口方法，重置枚举器
      throw new UnsupportedOperationException(); // 抛出不支持操作异常，因为CSV读取器不支持重置
    } // reset方法结束

    @Override public void close() { // 重写接口方法，关闭枚举器
      try { // 尝试关闭CSV读取器
        csvReader.close(); // 调用close方法关闭读取器
      } catch (IOException e) { // 捕获IO异常
        throw new RuntimeException(e); // 抛出运行时异常
      } // try-catch结束
    } // close方法结束
  } // SplunkResultEnumerator类结束
} // SplunkConnectionImpl类结束
