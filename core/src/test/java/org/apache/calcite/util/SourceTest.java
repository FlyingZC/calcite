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
 */ // Apache License 2.0 许可证声明，说明本代码遵循Apache开源协议
package org.apache.calcite.util; // 声明包名为org.apache.calcite.util，表示该类属于Calcite工具包
import com.google.common.io.CharSource; // 导入Google Guava库的CharSource类，用于字符源操作

import org.junit.jupiter.api.Disabled; // 导入JUnit 5的Disabled注解，用于禁用测试方法
import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法
import org.junit.jupiter.params.ParameterizedTest; // 导入JUnit 5的参数化测试注解
import org.junit.jupiter.params.provider.Arguments; // 导入JUnit 5的参数提供器Arguments
import org.junit.jupiter.params.provider.MethodSource; // 导入JUnit 5的方法源参数提供器

import java.io.BufferedReader; // 导入Java IO的BufferedReader类，用于缓冲读取字符流
import java.io.File; // 导入Java IO的File类，用于文件和目录路径的抽象表示
import java.io.IOException; // 导入Java IO的IOException类，用于处理IO异常
import java.io.InputStreamReader; // 导入Java IO的InputStreamReader类，用于字节流到字符流的桥接
import java.io.Reader; // 导入Java IO的Reader类，用于读取字符流的抽象类
import java.net.URISyntaxException; // 导入Java net的URISyntaxException类，用于处理URI语法异常
import java.net.URL; // 导入Java net的URL类，用于统一资源定位符
import java.nio.charset.StandardCharsets; // 导入Java NIO的StandardCharsets类，用于标准字符集
import java.util.Arrays; // 导入Java util的Arrays类，用于数组操作
import java.util.stream.Stream; // 导入Java util的Stream类，用于流式处理

import static org.apache.calcite.util.Sources.file; // 静态导入Sources工具类的file方法，用于创建文件Source
import static org.apache.calcite.util.Sources.of; // 静态导入Sources工具类的of方法，用于创建Source对象
import static org.apache.calcite.util.Sources.url; // 静态导入Sources工具类的url方法，用于创建URL Source

import static org.hamcrest.CoreMatchers.is; // 静态导入Hamcrest的is匹配器，用于断言
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入Hamcrest的断言方法
import static org.hamcrest.Matchers.hasToString; // 静态导入Hamcrest的hasToString匹配器
import static org.junit.jupiter.api.Assertions.assertNotNull; // 静态导入JUnit 5的assertNotNull断言方法
import static org.junit.jupiter.api.Assertions.assertNull; // 静态导入JUnit 5的assertNull断言方法
import static org.junit.jupiter.params.provider.Arguments.arguments; // 静态导入JUnit 5的arguments参数构造方法

/**
 * Tests for {@link Source}.
 */ // 本类用于测试Source接口的实现，Source是Calcite中表示数据源（文件、URL等）的抽象接口
class SourceTest { // 声明SourceTest测试类，用于测试Source接口的功能
  private static final String ROOT_PREFIX = getRootPrefix(); // 静态常量，存储操作系统的根路径前缀（Linux为"/"，Windows为"c:/"）

  private static String getRootPrefix() { // 私有静态方法，用于检测并返回当前操作系统的根路径前缀
    for (String s : new String[]{"/", "c:/"}) { // 遍历可能的根路径前缀数组："/"用于Unix/Linux/macOS，"c:/"用于Windows
      if (new File(s).isAbsolute()) { // 创建File对象并检查是否为绝对路径
        return s; // 如果是绝对路径，则返回该前缀作为操作系统的根路径
      }
    }
    throw new IllegalStateException( // 如果都不匹配，抛出异常表示不支持当前操作系统
        "Unsupported operation system detected. Both / and c:/ produce relative paths");
  }

  /**
   * Read lines from {@link CharSource}.
   */ // 测试从CharSource创建Source并读取内容
    @Test void charSource() throws IOException { // 测试方法，验证从CharSource创建Source对象并读取字符流的功能
      Source source = Sources.fromCharSource(CharSource.wrap("a\nb")); // 使用Sources工具类从CharSource创建Source对象，CharSource.wrap()将字符串包装为字符源    for (Reader r : Arrays.asList(source.reader(), // 遍历两种Reader：1)直接从Source获取Reader；2)从Source的输入流创建Reader
        new InputStreamReader(source.openStream(), StandardCharsets.UTF_8.name()))) { // 使用UTF-8编码将输入流转换为字符流
      try (BufferedReader reader = new BufferedReader(r)) { // 使用try-with-resources创建BufferedReader，自动关闭资源
        assertThat(reader.readLine(), is("a")); // 断言读取的第一行内容是"a"
        assertThat(reader.readLine(), is("b")); // 断言读取的第二行内容是"b"
        assertNull(reader.readLine()); // 断言读取第三行返回null，表示已到达文件末尾
      }
    }
  }

  static Stream<Arguments> relativePaths() { // 静态方法，提供参数化测试的参数数据，返回Arguments流
    return Stream.of( // 创建Stream对象，包含多个Arguments参数组合
        arguments("abc def.txt", "file:abc%20def.txt"), // 参数组1：文件名包含空格，对应的URL编码（空格编码为%20）
        arguments("abc+def.txt", "file:abc+def.txt"), // 参数组2：文件名包含加号，加号在URL中不需要编码
        arguments("path 1/ subfolder 2/abc.t x t", "file:path%201/%20subfolder%202/abc.t%20x%20t"), // 参数组3：多层路径包含多个空格，每个空格都编码为%20
        arguments("маленькой ёлочке холодно зимой.txt", // 参数组4：俄语文件名包含空格
            "file:маленькой%20ёлочке%20холодно%20зимой.txt")); // 对应的URL编码，保留俄语字符，空格编码为%20
  }

  private static String slashify(String path) { // 私有静态方法，将路径中的文件分隔符转换为斜杠
    return path.replace(File.separatorChar, '/'); // 将操作系统特定的文件分隔符（Windows为\，Unix为/）替换为斜杠/
  }

  @ParameterizedTest // 参数化测试注解，表示该方法会使用多个参数组合运行
  @MethodSource("relativePaths") // 指定参数源方法为relativePaths()
  void testRelativeFileToUrl(String path, String expectedUrl) { // 测试方法，验证相对路径文件与URL之间的转换
    URL url = of(new File(path)).url(); // 使用Sources.of()将File对象转换为Source，然后调用url()方法获取URL

    assertNotNull(url, () -> "No URL generated for Sources.of(file " + path + ")"); // 断言URL不为null，如果为null则显示错误信息
    assertThat("Sources.of(file " + path + ").url()", url, // 断言URL的字符串表示与期望的URL匹配
        hasToString(expectedUrl)); // 使用hasToString匹配器比较URL的toString()结果
    assertThat("Sources.of(Sources.of(file " + path // 断言URL转换回File后的路径与原始路径匹配
            + ").url()).file().getPath()",
        slashify(Sources.of(url).file().getPath()), is(path)); // 将URL转换为Source再转为File，获取路径并转换为斜杠格式，与原始路径比较
  }

  @ParameterizedTest // 参数化测试注解
  @MethodSource("relativePaths") // 指定参数源方法
  @Disabled // Open when we really fix that // 禁用此测试，因为绝对路径转换存在未解决的问题
  void testAbsoluteFileToUrl(String path, String expectedUrl) throws URISyntaxException { // 测试方法，验证绝对路径文件与URL之间的转换
    File absoluteFile = new File(path).getAbsoluteFile(); // 将相对路径转换为绝对路径的File对象
    URL url = of(absoluteFile).url(); // 将绝对路径File转换为Source，然后获取URL

    assertNotNull(url, () -> "No URL generated for Sources.of(file(" + path + ").absoluteFile)"); // 断言URL不为null
    // Sources.of(url).file().getPath() does not always work // 注释说明：从URL转换回File路径并不总是可靠
    // e.g. it might throw java.nio.file.InvalidPathException: Malformed input or input contains // 例如可能抛出异常：无效路径或包含不可映射字符
    // unmappable characters: /home/.../ws/core/????????? ?????? ??????? ?????.txt // 具体示例：包含非ASCII字符的路径
    //        at java.base/sun.nio.fs.UnixPath.encode(UnixPath.java:145) // 异常堆栈位置
    assertThat("Sources.of(Sources.of(file(" + path // 断言：使用URI的scheme-specific-part来验证路径
        + ").absolutePath).url()).file().getPath()",
        url.toURI().getSchemeSpecificPart(), // 获取URI的scheme-specific-part（文件路径部分）
        is(absoluteFile.getAbsolutePath())); // 与绝对路径的完整路径进行比较
  }

  @Test void testAppendWithSpaces() { // 测试方法，验证Source的append功能，处理包含空格的路径
    String fooRelative = "fo o+"; // 相对路径字符串，包含空格和加号
    String fooAbsolute = ROOT_PREFIX + "fo o+"; // 绝对路径字符串，使用根路径前缀
    String barRelative = "b ar+"; // 另一个相对路径字符串
    String barAbsolute = ROOT_PREFIX + "b ar+"; // 另一个绝对路径字符串
    assertAppend(file(null, fooRelative), file(null, barRelative), "fo o+/b ar+"); // 断言：相对路径+相对路径 = 相对路径
    assertAppend(file(null, fooRelative), file(null, barAbsolute), barAbsolute); // 断言：相对路径+绝对路径 = 绝对路径（忽略前面的相对路径）
    assertAppend(file(null, fooAbsolute), file(null, barRelative), ROOT_PREFIX + "fo o+/b ar+"); // 断言：绝对路径+相对路径 = 组合路径
    assertAppend(file(null, fooAbsolute), file(null, barAbsolute), barAbsolute); // 断言：绝对路径+绝对路径 = 绝对路径（忽略前面的绝对路径）

    String urlFooRelative = "file:fo%20o+"; // URL格式的相对路径，空格编码为%20
    String urlFooAbsolute = "file:" + ROOT_PREFIX + "fo%20o+"; // URL格式的绝对路径
    String urlBarRelative = "file:b%20ar+"; // URL格式的另一个相对路径
    String urlBarAbsolute = "file:" + ROOT_PREFIX + "b%20ar+"; // URL格式的另一个绝对路径
    assertAppend(url(urlFooRelative), url(urlBarRelative), "fo o+/b ar+"); // 断言：URL相对路径+URL相对路径 = 相对路径
    assertAppend(url(urlFooRelative), url(urlBarAbsolute), barAbsolute); // 断言：URL相对路径+URL绝对路径 = 绝对路径
    assertAppend(url(urlFooAbsolute), url(urlBarRelative), ROOT_PREFIX + "fo o+/b ar+"); // 断言：URL绝对路径+URL相对路径 = 组合路径
    assertAppend(url(urlFooAbsolute), url(urlBarAbsolute), barAbsolute); // 断言：URL绝对路径+URL绝对路径 = 绝对路径

    assertAppend(file(null, fooRelative), url(urlBarRelative), "fo o+/b ar+"); // 断言：File相对路径+URL相对路径 = 相对路径
    assertAppend(file(null, fooRelative), url(urlBarAbsolute), barAbsolute); // 断言：File相对路径+URL绝对路径 = 绝对路径
    assertAppend(file(null, fooAbsolute), url(urlBarRelative), ROOT_PREFIX + "fo o+/b ar+"); // 断言：File绝对路径+URL相对路径 = 组合路径
    assertAppend(file(null, fooAbsolute), url(urlBarAbsolute), barAbsolute); // 断言：File绝对路径+URL绝对路径 = 绝对路径

    assertAppend(url(urlFooRelative), file(null, barRelative), "fo o+/b ar+"); // 断言：URL相对路径+File相对路径 = 相对路径
    assertAppend(url(urlFooRelative), file(null, barAbsolute), barAbsolute); // 断言：URL相对路径+File绝对路径 = 绝对路径
    assertAppend(url(urlFooAbsolute), file(null, barRelative), ROOT_PREFIX + "fo o+/b ar+"); // 断言：URL绝对路径+File相对路径 = 组合路径
    assertAppend(url(urlFooAbsolute), file(null, barAbsolute), barAbsolute); // 断言：URL绝对路径+File绝对路径 = 绝对路径
  }

  @Test void testAppendHttp() { // 测试方法，验证HTTP URL的append功能
    // I've truly no idea what append of two URLs should be, yet it does something // 注释：不确定两个URL的append应该是什么行为，但它确实做了某些操作
    assertAppendUrl(url("http://fo%20o+/ba%20r+"), file(null, "no idea what I am doing+"), // 断言：HTTP URL + File路径 = 组合的HTTP URL
        "http://fo%20o+/ba%20r+/no%20idea%20what%20I%20am%20doing+"); // 期望的URL字符串，空格编码为%20
    assertAppendUrl(url("http://fo%20o+"), file(null, "no idea what I am doing+"), // 断言：HTTP URL + File路径
        "http://fo%20o+/no%20idea%20what%20I%20am%20doing+"); // 期望的组合URL
    assertAppendUrl(url("http://fo%20o+/ba%20r+"), url("file:no%20idea%20what%20I%20am%20doing+"), // 断言：HTTP URL + File URL
        "http://fo%20o+/ba%20r+/no%20idea%20what%20I%20am%20doing+"); // 期望的组合URL
    assertAppendUrl(url("http://fo%20o+"), url("file:no%20idea%20what%20I%20am%20doing+"), // 断言：HTTP URL + File URL
        "http://fo%20o+/no%20idea%20what%20I%20am%20doing+"); // 期望的组合URL
  }

  private void assertAppend(Source parent, Source child, String expected) { // 私有辅助方法，验证Source的append操作结果
    assertThat(parent + ".append(" + child + ")", // 断言信息：显示parent.append(child)操作
        parent.append(child).file(), // 执行append操作并获取结果File对象
        // This should transparently support various OS // 注释：应该透明支持各种操作系统
        hasToString(new File(expected).toString())); // 断言结果File的toString()与期望的File的toString()匹配
  }

  private void assertAppendUrl(Source parent, Source child, String expected) { // 私有辅助方法，验证Source的append操作返回的URL
    assertThat(parent + ".append(" + child + ")", // 断言信息：显示parent.append(child)操作
        parent.append(child).url(), // 执行append操作并获取结果URL对象
        hasToString(expected)); // 断言结果URL的toString()与期望的字符串匹配
  }

  @Test void testSpaceInUrl() { // 测试方法，验证URL中包含编码空格的处理
    String url = "file:" + ROOT_PREFIX + "dir%20name/test%20file.json"; // 创建包含URL编码空格的URL字符串
    final Source foo = url(url); // 使用Sources.url()方法创建Source对象
    assertThat(url + " .file().getAbsolutePath()", // 断言信息：显示URL和操作
        foo.file().getAbsolutePath(), // 获取Source对应的File对象的绝对路径
        is(new File(ROOT_PREFIX + "dir name/test file.json") // 创建期望的File对象，路径包含实际空格
            .getAbsolutePath())); // 断言绝对路径与期望的绝对路径匹配
  }

  @Test void testSpaceInRelativeUrl() { // 测试方法，验证相对URL中包含编码空格的处理
    String url = "file:dir%20name/test%20file.json"; // 创建相对URL字符串，包含URL编码空格
    final Source foo = url(url); // 使用Sources.url()方法创建Source对象
    assertThat(url + " .file().getAbsolutePath()", // 断言信息：显示URL和操作
        foo.file().getPath().replace('\\', '/'), // 获取文件路径并将反斜杠替换为斜杠（统一路径格式）
        is("dir name/test file.json")); // 断言路径与期望的包含实际空格的路径匹配
  }

  @Test void testRelative() { // 测试方法，验证Source的relative方法功能（计算相对路径）
    final Source fooBar = file(null, ROOT_PREFIX + "foo/bar"); // 创建指向foo/bar路径的Source对象
    final Source foo = file(null, ROOT_PREFIX + "foo"); // 创建指向foo路径的Source对象（fooBar的父目录）
    final Source baz = file(null, ROOT_PREFIX + "baz"); // 创建指向baz路径的Source对象（不相关的路径）
    final Source bar = fooBar.relative(foo); // 调用relative方法，计算fooBar相对于foo的相对路径
    assertThat(bar.file(), hasToString("bar")); // 断言：fooBar相对于foo的相对路径是"bar"
    assertThat(fooBar.relative(baz), is(fooBar)); // 断言：fooBar相对于不相关的baz路径，返回fooBar自身（无相对关系）
  }
}
