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
package org.apache.calcite.test;

import org.apache.calcite.avatica.util.Spaces;
import org.apache.calcite.linq4j.Nullness;
import org.apache.calcite.util.Pair;
import org.apache.calcite.util.Sources;
import org.apache.calcite.util.Util;
import org.apache.calcite.util.XmlOutput;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Assertions;
import org.opentest4j.AssertionFailedError;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static java.util.Objects.requireNonNull;

/**
 * A collection of resources used by tests. // 用于测试的资源集合类
 *
 * <p>Loads files containing test input and output into memory. If there are
 * differences, writes out a log file containing the actual output. // 将包含测试输入和输出的文件加载到内存中。如果存在差异，则写入包含实际输出的日志文件
 *
 * <p>Typical usage is as follows. A test case class defines a method // 典型用法如下。测试用例类定义一个方法
 *
 * <blockquote><pre><code>
 * package com.acme.test;
 * &nbsp;
 * public class MyTest extends TestCase {
 *   public DiffRepository getDiffRepos() {
 *     return DiffRepository.lookup(MyTest.class);
 *   }
 * &nbsp;
 *   &#64;Test void testToUpper() {
 *     getDiffRepos().assertEquals("${result}", "${string}");
 *   }
 * &nbsp;
 *   &#64;Test void testToLower() {
 *     getDiffRepos().assertEquals("Multi-line\nstring", "${string}");
 *   }
 * }
 * </code></pre></blockquote>
 *
 * <p>There is an accompanying reference file named after the class,
 * <code>src/test/resources/com/acme/test/MyTest.xml</code>: // 有一个与类同名的参考文件
 *
 * <blockquote><pre><code>
 * &lt;Root&gt;
 *     &lt;TestCase name="testToUpper"&gt;
 *         &lt;Resource name="string"&gt;
 *             &lt;![CDATA[String to be converted to upper case]]&gt;
 *         &lt;/Resource&gt;
 *         &lt;Resource name="result"&gt;
 *             &lt;![CDATA[STRING TO BE CONVERTED TO UPPER CASE]]&gt;
 *         &lt;/Resource&gt;
 *     &lt;/TestCase&gt;
 *     &lt;TestCase name="testToLower"&gt;
 *         &lt;Resource name="result"&gt;
 *             &lt;![CDATA[multi-line
 * string]]&gt;
 *         &lt;/Resource&gt;
 *     &lt;/TestCase&gt;
 * &lt;/Root&gt;
 *
 * </code></pre></blockquote>
 *
 * <p>If any of the test cases fails, a log file is generated, called
 * {@code build/diffrepo/test/com/acme/test/MyTest_actual.xml},
 * containing the actual output. // 如果任何测试用例失败，会生成一个日志文件，包含实际输出
 *
 * <p>The log
 * file is otherwise identical to the reference log, so once the log file has
 * been verified, it can simply be copied over to become the new reference
 * log: // 日志文件与参考日志完全相同，因此一旦验证了日志文件，就可以简单地复制它成为新的参考日志
 *
 * <blockquote>{@code
 * cp build/diffrepo/test/com/acme/test/MyTest_actual.xml
 * src/test/resources/com/acme/test/MyTest.xml
 * }</blockquote>
 *
 * <p>If a resource or test case does not exist, <code>DiffRepository</code>
 * creates them in the log file. Because DiffRepository is so forgiving, it is
 * very easy to create new tests and test cases. // 如果资源或测试用例不存在，DiffRepository会在日志文件中创建它们。由于DiffRepository非常宽容，所以很容易创建新的测试和测试用例
 *
 * <p>The {@link #lookup} method ensures that all test cases share the same
 * instance of the repository. This is important more than one test case fails.
 * The shared instance ensures that the generated
 * {@code build/diffrepo/test/com/acme/test/MyTest_actual.xml}
 * file contains the actual for <em>both</em> test cases. // lookup方法确保所有测试用例共享同一个仓库实例。这一点很重要，因为多个测试用例可能会失败。共享实例确保生成的日志文件包含所有失败测试用例的实际输出
 */
public class DiffRepository {
  //~ Static fields/initializers --------------------------------------------- // 静态字段和初始化块

/*
      Example XML document: // XML文档示例

      <Root>
        <TestCase name="testFoo">
          <Resource name="sql">
            <![CDATA[select from emps]]>
           </Resource>
           <Resource name="plan">
             <![CDATA[MockTableImplRel.FENNEL_EXEC(table=[SALES, EMP])]]>
           </Resource>
         </TestCase>
         <TestCase name="testBar">
           <Resource name="sql">
             <![CDATA[select * from depts where deptno = 10]]>
           </Resource>
           <Resource name="output">
             <![CDATA[10, 'Sales']]>
           </Resource>
         </TestCase>
       </Root>
*/
  private static final String ROOT_TAG = "Root"; // XML根元素的标签名
  private static final String TEST_CASE_TAG = "TestCase"; // 测试用例元素的标签名
  private static final String TEST_CASE_NAME_ATTR = "name"; // 测试用例名称属性名
  private static final String TEST_CASE_OVERRIDES_ATTR = "overrides"; // 测试用例覆盖属性名
  private static final String RESOURCE_TAG = "Resource"; // 资源元素的标签名
  private static final String RESOURCE_NAME_ATTR = "name"; // 资源名称属性名

  /**
   * Holds one diff-repository per class. It is necessary for all test cases in
   * the same class to share the same diff-repository: if the repository gets
   * loaded once per test case, then only one diff is recorded. // 为每个类持有一个diff-repository实例。同一个类的所有测试用例必须共享同一个diff-repository：如果每个测试用例都加载一次仓库，那么只会记录一个差异
   */
  private static final LoadingCache<Key, DiffRepository> REPOSITORY_CACHE = // 仓库缓存，使用LoadingCache按需创建和缓存DiffRepository实例
      CacheBuilder.newBuilder().build(CacheLoader.from(Key::toRepo)); // 使用CacheLoader从Key创建DiffRepository

  private static final ThreadLocal<DocumentBuilderFactory> DOCUMENT_BUILDER_FACTORY = // 线程安全的DocumentBuilderFactory，用于解析XML文档
      ThreadLocal.withInitial(() -> { // 使用ThreadLocal确保每个线程有自己的DocumentBuilderFactory实例
        final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance(); // 创建DocumentBuilderFactory实例
        documentBuilderFactory.setXIncludeAware(false); // 禁用XInclude支持，防止XXE攻击
        documentBuilderFactory.setExpandEntityReferences(false); // 不展开实体引用，防止XXE攻击
        documentBuilderFactory.setNamespaceAware(true); // 启用命名空间支持
        try {
          documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true); // 启用安全处理特性
          documentBuilderFactory
              .setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // 禁止DOCTYPE声明，防止XXE攻击
        } catch (final ParserConfigurationException e) { // 捕获解析器配置异常
          throw new IllegalStateException("Document Builder configuration failed", e); // 抛出运行时异常
        }
        return documentBuilderFactory; // 返回配置好的DocumentBuilderFactory
      });

  //~ Instance fields -------------------------------------------------------- // 实例字段

  private final @Nullable DiffRepository baseRepository; // 基础仓库，用于继承测试资源，可为null
  private final int indent; // XML文件的缩进空格数，用于格式化输出
  private final ImmutableSortedSet<String> outOfOrderTests; // 排序错误的测试用例名称集合
  private final Boolean existsMethodOnlyInXml; // 是否存在仅在XML中定义的测试方法
  private final SortedMap<String, Node> xmlTestCases; // XML中定义的测试用例映射，按名称排序
  private Document doc; // XML文档对象，包含所有测试用例和资源
  private final Element root; // XML文档的根元素
  private final URL refFile; // 参考文件的URL，存储期望的测试输出
  private final File logFile; // 日志文件，存储实际的测试输出
  private final @Nullable Filter filter; // 过滤器，用于过滤返回的字符串，可为null
  private int modCount; // 文档修改计数器，记录文档被修改的次数
  private int modCountAtLastWrite; // 上次写入时的修改计数器，用于判断是否需要写入

  /**
   * Creates a DiffRepository. // 创建DiffRepository实例
   *
   * @param refFile   Reference file // 参考文件URL，包含期望的测试输出
   * @param logFile   Log file // 日志文件，用于存储实际的测试输出
   * @param baseRepository Parent repository or null // 父仓库，用于继承测试资源
   * @param filter    Filter or null // 过滤器，用于过滤返回的字符串
   * @param indent    Indentation of XML file // XML文件的缩进空格数
   */
  private DiffRepository(URL refFile, File logFile,
      @Nullable DiffRepository baseRepository, @Nullable Filter filter,
      int indent, Set<String> javaTestMethods) {
    this.baseRepository = baseRepository; // 初始化基础仓库
    this.filter = filter; // 初始化过滤器
    this.indent = indent; // 初始化缩进空格数
    this.refFile = requireNonNull(refFile, "refFile"); // 初始化参考文件URL，确保不为null
    this.logFile = logFile; // 初始化日志文件
    this.modCountAtLastWrite = 0; // 初始化上次写入时的修改计数器为0
    this.modCount = 0; // 初始化修改计数器为0

    // Load the document. // 加载XML文档
    try {
      DocumentBuilder docBuilder = // 创建文档构建器
          DOCUMENT_BUILDER_FACTORY.get().newDocumentBuilder(); // 从线程安全工厂获取并创建DocumentBuilder
      try (InputStream inputStream = refFile.openStream()) { // 打开参考文件的输入流
        // Parse the reference file. // 解析参考文件
        this.doc = docBuilder.parse(inputStream); // 解析XML文档
        // Don't write a log file yet -- as far as we know, it's still identical. // 暂不写入日志文件 - 据我们所知，它仍然相同
      } catch (IOException e) { // 捕获IO异常
        // There's no reference file. Create and write a log file. // 没有参考文件，创建并写入日志文件
        this.doc = docBuilder.newDocument(); // 创建新的空文档
        this.doc.appendChild(doc.createElement(ROOT_TAG)); // 添加根元素
        flushDoc(); // 立即写入日志文件
      }
      this.root = doc.getDocumentElement(); // 获取文档根元素
      this.xmlTestCases = analyze(this.root); // 分析根元素，提取所有测试用例
      existsMethodOnlyInXml = checkExists(this.root, javaTestMethods, this.xmlTestCases); // 检查是否存在仅在XML中定义的测试方法
      outOfOrderTests = validateOrder(this.root, this.xmlTestCases); // 验证测试用例的顺序是否正确
    } catch (ParserConfigurationException | SAXException e) { // 捕获解析器配置异常或SAX异常
      throw new RuntimeException("error while creating xml parser", e); // 抛出运行时异常
    }
  }

  //~ Methods ---------------------------------------------------------------- // 方法部分

  public void checkActualAndReferenceFiles() { // 检查实际文件和参考文件是否一致
    if (existsMethodOnlyInXml) { // 如果存在仅在XML中定义的测试方法
      modCount++; // 增加修改计数
      flushDoc(); // 刷新文档到日志文件
    }

    if (!logFile.exists()) { // 如果日志文件不存在
      return; // 直接返回
    }

    final String resourceFile = // 构建资源文件路径
        Sources.of(refFile).file().getPath().replace( // 替换路径中的build/resources/test为src/test/resources
            String.join(File.separator, "build", "resources", "test"), // 原路径部分
            String.join(File.separator, "src", "test", "resources")); // 新路径部分

    final String diff = DiffTestCase.diff(new File(resourceFile), logFile); // 比较资源文件和日志文件的差异

    if (!diff.isEmpty()) { // 如果存在差异
      throw new IllegalArgumentException("Actual and reference files differ. " // 抛出异常，提示文件不同
          + "If you are adding new tests, replace the reference file with the " // 如果添加新测试，替换参考文件
          + "current actual file, after checking its content." // 在检查内容后
          + "\ndiff " + logFile.getAbsolutePath() + " " + resourceFile + "\n" // 显示diff命令
          + diff); // 显示差异内容
    }
  }

  String logFilePath() { // 获取日志文件的绝对路径
    return logFile.getAbsolutePath(); // 返回日志文件的绝对路径
  }

  private static URL findFile(Class<?> clazz, final String suffix) { // 查找类对应的参考文件URL
    // The reference file for class "com.foo.Bar" is "com/foo/Bar.xml" // 类com.foo.Bar的参考文件是com/foo/Bar.xml
    String rest = "/" + clazz.getName().replace('.', File.separatorChar) // 将类名转换为路径格式
        + suffix; // 添加文件后缀
    return requireNonNull(clazz.getResource(rest)); // 获取资源URL并确保不为null
  }

  /** Returns the diff repository, checking that it is not null. // 返回diff repository，检查其不为null
   *
   * <p>If it is null, throws {@link IllegalArgumentException} with a message
   * informing people that they need to change their test configuration. */ // 如果为null，抛出IllegalArgumentException并提示用户需要更改测试配置
  public static DiffRepository castNonNull( // 将可能为null的DiffRepository转为非null
      @Nullable DiffRepository diffRepos) {
    if (diffRepos != null) { // 如果diffRepos不为null
      return Nullness.castNonNull(diffRepos); // 使用Nullness工具类强制转换为非null
    }
    throw new IllegalArgumentException("diffRepos is null; if you require a " // 抛出异常，提示diffRepos为null
        + "DiffRepository, set it in your test's fixture() method"); // 提示在测试的fixture()方法中设置DiffRepository
  }

  /**
   * Expands a string containing one or more variables. (Currently only works
   * if there is one variable.) // 展开包含一个或多个变量的字符串（目前只支持一个变量）
   */
  public String expand(String tag, String text) { // 展开字符串中的变量
    requireNonNull(tag, "tag"); // 确保tag不为null
    requireNonNull(text, "text"); // 确保text不为null
    if (text.startsWith("${") // 如果text以${开头
        && text.endsWith("}")) { // 且以}结尾
      final String testCaseName = getCurrentTestCaseName(); // 获取当前测试用例名称
      final String token = text.substring(2, text.length() - 1); // 提取变量名（去掉${和}）
      assert token.startsWith(tag) : "token '" + token // 断言token以tag开头，否则抛出异常
          + "' does not match tag '" + tag + "'";
      String expanded = get(testCaseName, token); // 从XML中获取token对应的值
      if (expanded == null) { // 如果未找到该token
        // Token is not specified. Return the original text: this will
        // cause a diff, and the actual value will be written to the
        // log file. // Token未指定，返回原始文本：这将导致差异，实际值将被写入日志文件
        return text; // 返回原始文本
      }
      if (filter != null) { // 如果过滤器不为null
        expanded = filter.filter(this, testCaseName, tag, text, expanded); // 对展开后的文本进行过滤
      }
      return expanded; // 返回展开后的文本
    } else { // 如果text不是变量格式
      // Make sure what appears in the resource file is consistent with
      // what is in the Java. It helps to have a redundant copy in the
      // resource file. // 确保资源文件中的内容与Java代码中的内容一致。在资源文件中保留冗余副本很有帮助
      final String testCaseName = getCurrentTestCaseName(); // 获取当前测试用例名称
      if (baseRepository == null // 如果没有基础仓库
          || baseRepository.get(testCaseName, tag) == null) { // 或基础仓库中没有该资源
        set(tag, text); // 设置该资源值
      }
      return text; // 返回原始文本
    }
  }

  /**
   * Sets the value of a given resource of the current test case. // 设置当前测试用例中指定资源的值
   *
   * @param resourceName Name of the resource, e.g. "sql" // 资源名称，如"sql"
   * @param value        Value of the resource // 资源的值
   */
  public synchronized void set(String resourceName, String value) { // 设置资源值（同步方法）
    requireNonNull(resourceName, "resourceName"); // 确保resourceName不为null
    final String testCaseName = getCurrentTestCaseName(); // 获取当前测试用例名称
    update(testCaseName, resourceName, value); // 更新测试用例中的资源
  }

  public void amend(String expected, String actual) { // 修正测试用例中的期望值
    if (expected.startsWith("${") // 如果expected以${开头
        && expected.endsWith("}")) { // 且以}结尾
      String token = expected.substring(2, expected.length() - 1); // 提取变量名
      set(token, actual); // 将实际值设置为该变量的值
    }
  }

  /**
   * Returns a given resource from a given test case. // 从指定测试用例中返回指定资源
   *
   * @param testCaseName Name of test case, e.g. "testFoo" // 测试用例名称，如"testFoo"
   * @param resourceName Name of resource, e.g. "sql", "plan" // 资源名称，如"sql"、"plan"
   * @return The value of the resource, or null if not found // 资源的值，如果未找到则返回null
   */
  private synchronized @Nullable String get( // 获取资源值（同步方法）
      final String testCaseName, // 测试用例名称
      String resourceName) { // 资源名称
    Element testCaseElement = getTestCaseElement(testCaseName, true, null); // 获取测试用例元素
    if (testCaseElement == null) { // 如果测试用例元素不存在
      if (baseRepository != null) { // 如果有基础仓库
        return baseRepository.get(testCaseName, resourceName); // 从基础仓库获取资源
      } else { // 如果没有基础仓库
        return null; // 返回null
      }
    }
    final @Nullable Element resourceElement = // 获取资源元素
        getResourceElement(testCaseElement, resourceName); // 从测试用例元素中获取指定资源
    if (resourceElement != null) { // 如果资源元素存在
      return getText(resourceElement); // 返回资源元素的文本内容
    }
    return null; // 返回null
  }

  /**
   * Returns the text under an element. // 返回元素下的文本内容
   */
  private static String getText(Element element) { // 获取元素的文本内容
    // If there is a <![CDATA[ ... ]]> child, return its text and ignore
    // all other child elements. // 如果有CDATA子节点，返回其文本并忽略所有其他子元素
    final NodeList childNodes = element.getChildNodes(); // 获取所有子节点
    for (int i = 0; i < childNodes.getLength(); i++) { // 遍历子节点
      Node node = childNodes.item(i); // 获取当前节点
      if (node instanceof CDATASection) { // 如果是CDATA节点
        return node.getNodeValue(); // 返回CDATA节点的值
      }
    }

    // Otherwise return all the text under this element (including
    // whitespace). // 否则返回该元素下的所有文本（包括空白字符）
    StringBuilder buf = new StringBuilder(); // 创建字符串缓冲区
    for (int i = 0; i < childNodes.getLength(); i++) { // 遍历子节点
      Node node = childNodes.item(i); // 获取当前节点
      if (node instanceof Text) { // 如果是文本节点
        buf.append(((Text) node).getWholeText()); // 追加文本节点的完整文本
      }
    }
    return buf.toString(); // 返回构建的字符串
  }

/**
   * Returns the &lt;TestCase&gt; element corresponding to the current test
   * case. // 返回对应于当前测试用例的&lt;TestCase&gt;元素
   *
   * @param testCaseName  Name of test case // 测试用例名称
   * @param checkOverride Make sure that if an element overrides an element in
   *                      a base repository, it has overrides="true" // 确保如果元素覆盖基础仓库中的元素，它具有overrides="true"属性
   * @return TestCase element, or null if not found // TestCase元素，如果未找到则返回null
   */
  private synchronized @Nullable Element getTestCaseElement( // 获取测试用例元素（同步方法）
      final String testCaseName, // 测试用例名称
      boolean checkOverride, // 是否检查覆盖属性
      @Nullable List<Pair<String, Element>> elements) { // 用于收集所有测试用例元素的列表
    final NodeList childNodes = root.getChildNodes(); // 获取根元素的所有子节点
    for (int i = 0; i < childNodes.getLength(); i++) { // 遍历子节点
      Node child = childNodes.item(i); // 获取当前子节点
      if (child.getNodeName().equals(TEST_CASE_TAG)) { // 如果是TestCase元素
        Element testCase = (Element) child; // 转换为Element
        final String name = testCase.getAttribute(TEST_CASE_NAME_ATTR); // 获取测试用例名称
        if (testCaseName.equals(name)) { // 如果名称匹配
          if (checkOverride // 如果需要检查覆盖属性
              && (baseRepository != null) // 且有基础仓库
              && (baseRepository.getTestCaseElement(testCaseName, false, null) != null) // 且基础仓库中有同名测试用例
              && !"true".equals( // 且当前测试用例没有overrides="true"属性
                  testCase.getAttribute(TEST_CASE_OVERRIDES_ATTR))) {
            throw new RuntimeException( // 抛出运行时异常
                "TestCase  '" + testCaseName + "' overrides a " // 提示测试用例覆盖了基础仓库中的测试用例
                + "test case in the base repository, but does " // 但没有指定overrides=true
                + "not specify 'overrides=true'");
          }
          if (outOfOrderTests.contains(testCaseName)) { // 如果测试用例顺序错误
            ++modCount; // 增加修改计数
            flushDoc(); // 刷新文档
            throw new IllegalArgumentException("TestCase '" + testCaseName // 抛出异常
                + "' is out of order in the reference file: " // 提示测试用例顺序错误
                + Sources.of(refFile).file() + "\n" // 显示参考文件路径
                + "To fix, copy the生成的日志文件: " + logFile + "\n"); // 提示修复方法
          }
          return testCase; // 返回测试用例元素
        }
        if (elements != null) { // 如果需要收集所有测试用例元素
          elements.add(Pair.of(name, testCase)); // 添加到列表中
        }
      }
    }
    return null; // 未找到返回null
  }

  /**
   * Returns the name of the current test case by looking up the call stack for
   * a method whose name starts with "test", for example "testFoo". // 通过查找调用栈中名称以"test"开头的方法来返回当前测试用例的名称
   *
   * @param fail Whether to fail if no method is found // 如果未找到方法是否抛出异常
   * @return Name of current test case, or null if not found // 当前测试用例名称，如果未找到则返回null
   */
  private static @Nullable String getCurrentTestCaseName(boolean fail) { // 获取当前测试用例名称
    // REVIEW jvs 12-Mar-2006: Too clever by half.  Someone might not know
    // about this and use a private helper method whose name also starts
    // with test. Perhaps just require them to pass in getName() from the
    // calling TestCase's setUp method and store it in a thread-local,
    // failing here if they forgot? // 审查：这种方法可能过于聪明。有人可能不知道这一点，并使用名称也以test开头的私有辅助方法。也许应该要求他们从调用TestCase的setUp方法传入getName()并将其存储在线程本地变量中，如果他们忘记了则在这里失败

    // Clever, this. Dump the stack and look up it for a method which
    // looks like a test case name, e.g. "testFoo". // 这很巧妙。转储堆栈并查找看起来像测试用例名称的方法，例如"testFoo"
    final StackTraceElement[] stackTrace; // 堆栈跟踪元素数组
    Throwable runtimeException = new Throwable(); // 创建Throwable对象
    runtimeException.fillInStackTrace(); // 填充堆栈跟踪
    stackTrace = runtimeException.getStackTrace(); // 获取堆栈跟踪
    for (StackTraceElement stackTraceElement : stackTrace) { // 遍历堆栈跟踪
      final String methodName = stackTraceElement.getMethodName(); // 获取方法名
      if (methodName.startsWith("test")) { // 如果方法名以"test"开头
        return methodName; // 返回方法名
      }
    }
    if (fail) { // 如果需要失败
      throw new RuntimeException("no test case on current call stack"); // 抛出异常
    } else { // 如果不需要失败
      return null; // 返回null
    }
  }

  /** Returns the current test case name; // 返回当前测试用例名称
   * equivalent to {@link #getCurrentTestCaseName}(true), // 等同于getCurrentTestCaseName(true)
   * this method throws if not found, and never returns null. */ // 如果未找到则抛出异常，从不返回null
  private static String getCurrentTestCaseName() { // 获取当前测试用例名称（必须找到）
    return requireNonNull(getCurrentTestCaseName(true)); // 调用getCurrentTestCaseName(true)并确保不为null
  }

  public void assertEquals(String tag, String expected, String actual) { // 断言期望值和实际值相等
    final String testCaseName = getCurrentTestCaseName(true); // 获取当前测试用例名称
    String expected2 = expand(tag, expected); // 展开期望值中的变量
    if (expected2 == null) { // 如果展开后的期望值为null
      update(testCaseName, expected, actual); // 更新测试用例资源
      throw new AssertionError("reference file does not contain resource '" // 抛出断言错误
          + expected + "' for test case '" + testCaseName + "'"); // 提示参考文件中不包含该资源
    } else { // 如果展开后的期望值不为null
      try { // 尝试断言
        // TODO jvs 25-Apr-2006:  reuse bulk of
        // DiffTestCase.diffTestLog here; besides newline
        // insensitivity, it can report on the line
        // at which the first diff occurs, which is useful
        // for largish snippets // TODO: 在这里重用DiffTestCase.diffTestLog的大部分内容；除了换行符不敏感之外，它还可以报告第一个差异发生的行，这对于较大的代码片段很有用
        String expected2Canonical = // 将期望值转换为规范格式（统一换行符）
            expected2.replace(Util.LINE_SEPARATOR, "\n"); // 替换换行符为\n
        String actualCanonical = // 将实际值转换为规范格式（统一换行符）
            actual.replace(Util.LINE_SEPARATOR, "\n"); // 替换换行符为\n
        Assertions.assertEquals(expected2Canonical, actualCanonical, tag); // 断言期望值和实际值相等
      } catch (AssertionFailedError e) { // 捕获断言失败异常
        amend(expected, actual); // 修正期望值
        throw e; // 重新抛出异常
      }
    }
  }

  /**
   * Creates a new document with a given resource. // 使用给定资源创建新文档
   *
   * <p>This method is synchronized, in case two threads are running test
   * cases of this test at the same time. // 此方法是同步的，以防两个线程同时运行此测试的测试用例
   *
   * @param testCaseName Test case name // 测试用例名称
   * @param resourceName Resource name // 资源名称
   * @param value        New value of resource // 资源的新值
   */
  private synchronized void update( // 更新测试用例资源（同步方法）
      String testCaseName, // 测试用例名称
      String resourceName, // 资源名称
      String value) { // 资源值
    final List<Pair<String, Element>> map = new ArrayList<>(); // 创建测试用例元素列表
    Element testCaseElement = getTestCaseElement(testCaseName, true, map); // 获取测试用例元素
    if (testCaseElement == null) { // 如果测试用例元素不存在
      testCaseElement = doc.createElement(TEST_CASE_TAG); // 创建新的TestCase元素
      testCaseElement.setAttribute(TEST_CASE_NAME_ATTR, testCaseName); // 设置测试用例名称属性
      Node refElement = ref(testCaseName, map); // 获取参考元素（用于插入位置）
      root.insertBefore(testCaseElement, refElement); // 在参考元素前插入新元素
      ++modCount; // 增加修改计数
    }
    Element resourceElement = // 获取资源元素
        getResourceElement(testCaseElement, resourceName, true); // 从测试用例元素中获取指定资源
    if (resourceElement == null) { // 如果资源元素不存在
      resourceElement = doc.createElement(RESOURCE_TAG); // 创建新的Resource元素
      resourceElement.setAttribute(RESOURCE_NAME_ATTR, resourceName); // 设置资源名称属性
      testCaseElement.appendChild(resourceElement); // 将资源元素添加到测试用例元素
      ++modCount; // 增加修改计数
      if (!value.isEmpty()) { // 如果资源值不为空
        resourceElement.appendChild(doc.createCDATASection(value)); // 添加CDATA节点
      }
    } else { // 如果资源元素已存在
      final List<Node> newChildList; // 新的子节点列表
      if (value.isEmpty()) { // 如果资源值为空
        newChildList = ImmutableList.of(); // 创建空列表
      } else { // 如果资源值不为空
        newChildList = ImmutableList.of(doc.createCDATASection(value)); // 创建包含CDATA节点的列表
      }
      if (replaceChildren(resourceElement, newChildList)) { // 如果替换了子节点
        ++modCount; // 增加修改计数
      }
    }

    // Write out the document. // 写出文档
    flushDoc(); // 刷新文档到文件
  }

  private static @Nullable Node ref(String testCaseName, // 获取参考元素，用于确定新元素的插入位置
      List<Pair<String, Element>> map) { // 测试用例元素列表
    if (map.isEmpty()) { // 如果列表为空
      return null; // 返回null
    }
    // Compute the position that the new element should be if the map were
    // sorted. // 计算如果列表已排序，新元素应该在的位置
    int i = 0; // 位置计数器
    final List<String> names = Pair.left(map); // 提取所有测试用例名称
    for (String s : names) { // 遍历名称
      if (s.compareToIgnoreCase(testCaseName) <= 0) { // 如果当前名称小于等于新测试用例名称
        ++i; // 增加计数器
      }
    }
    // Starting at a proportional position in the list,
    // move forwards through lesser names, then
    // move backwards through greater names. // 从列表中的比例位置开始，向前移动较小的名称，然后向后移动较大的名称
    //
    // The intended effect is that if the list is already sorted, the new item
    // will end up in exactly the right position, and if the list is not sorted,
    // the new item will end up in approximately the right position. // 预期效果是：如果列表已经排序，新项目将最终处于完全正确的位置；如果列表未排序，新项目将最终处于大致正确的位置
    while (i < map.size() // 当位置小于列表大小
        && names.get(i).compareToIgnoreCase(testCaseName) < 0) { // 且当前名称小于新测试用例名称
      ++i; // 向前移动
    }
    if (i >= map.size() - 1) { // 如果位置已经到达或超过列表末尾
      return null; // 返回null（插入到末尾）
    }
    while (i >= 0 && names.get(i).compareToIgnoreCase(testCaseName) > 0) { // 当位置有效且当前名称大于新测试用例名称
      --i; // 向后移动
    }
    return map.get(i + 1).right; // 返回下一个位置的元素
  }

  /**
   * Flushes the reference document to the file system. // 将参考文档刷新到文件系统
   */
  private synchronized void flushDoc() { // 刷新文档（同步方法）
    if (modCount == modCountAtLastWrite) { // 如果文档自上次写入后未被修改
      // Document has not been modified since last write. // 文档自上次写入后未被修改
      return; // 直接返回
    }
    try { // 尝试写入文件
      boolean b = logFile.getParentFile().mkdirs(); // 创建父目录
      Util.discard(b); // 忽略返回值
      try (Writer w = Util.printWriter(logFile)) { // 创建写入器
        write(doc, w, indent); // 写入文档
      }
    } catch (IOException e) { // 捕获IO异常
      throw Util.throwAsRuntime("error while writing test reference log '" // 抛出运行时异常
          + logFile + "'", e); // 提示写入测试参考日志时出错
    }
    modCountAtLastWrite = modCount; // 更新上次写入时的修改计数器
  }

  /** Analyzes the root element. // 分析根元素
   *
   * <p>Returns the set of test names that in the reference file. */ // 返回参考文件中的测试名称集合
  private static SortedMap<String, Node> analyze(Element root) { // 分析根元素，提取所有测试用例
    if (!root.getNodeName().equals(ROOT_TAG)) { // 如果根元素名称不是ROOT_TAG
      throw new RuntimeException("expected root element of type '" + ROOT_TAG // 抛出异常
          + "', but found '" + root.getNodeName() + "'"); // 提示根元素类型不正确
    }

    // Make sure that there are no duplicate test cases, and count how many
    // tests are out of order. // 确保没有重复的测试用例，并统计有多少测试顺序错误
    final SortedMap<String, Node> testCases = new TreeMap<>(); // 创建排序的测试用例映射
    final NodeList childNodes = root.getChildNodes(); // 获取所有子节点
    for (int i = 0; i < childNodes.getLength(); i++) { // 遍历子节点
      Node child = childNodes.item(i); // 获取当前子节点
      if (child.getNodeName().equals(TEST_CASE_TAG)) { // 如果是TestCase元素
        Element testCase = (Element) child; // 转换为Element
        final String name = testCase.getAttribute(TEST_CASE_NAME_ATTR); // 获取测试用例名称
        if (testCases.put(name, testCase) != null) { // 如果已存在同名测试用例
          throw new RuntimeException("TestCase '" + name + "' is duplicate"); // 抛出异常
        }
      }
    }
    return testCases; // 返回测试用例映射
  }

  /** Checks if there are methods that only exist in XML. // 检查是否存在仅在XML中定义的测试方法
   *
   * <p>Returns true if there are methods that only exist in XML. */ // 如果存在仅在XML中定义的测试方法则返回true
  private static Boolean checkExists(Element root, Set<String> javaTestMethods, // 检查是否存在仅在XML中定义的测试方法
      Map<String, Node> testCases) { // 测试用例映射
    final List<String> existsOnlyInXml = new ArrayList<>(); // 创建仅在XML中存在的测试方法列表
    for (Map.Entry<String, Node> entry : testCases.entrySet()) { // 遍历测试用例
      String name = entry.getKey(); // 获取测试用例名称
      if (!javaTestMethods.contains(name)) { // 如果Java代码中没有对应的测试方法
        existsOnlyInXml.add(name); // 添加到列表中
      }
    }
    if (!existsOnlyInXml.isEmpty()) { // 如果列表不为空
      for (String value : existsOnlyInXml) { // 遍历列表
        root.removeChild(testCases.get(value)); // 从XML中删除这些测试用例
      }
    }

    return !existsOnlyInXml.isEmpty(); // 返回是否存在仅在XML中定义的测试方法
  }

  /** Validates the root element order. // 验证根元素顺序
   *
   * <p>Returns the set of test names that are out of order in the reference
   * file (empty if the reference file is fully sorted). */ // 返回参考文件中顺序错误的测试名称集合（如果参考文件完全排序则为空）
  private static ImmutableSortedSet<String> validateOrder(Element root, // 验证测试用例顺序
      SortedMap<String, Node> testCases) { // 测试用例映射
    String previousName = null; // 上一个测试用例名称
    final List<String> outOfOrderNames = new ArrayList<>(); // 创建顺序错误的测试名称列表
    for (Map.Entry<String, Node> entry : testCases.entrySet()) { // 遍历测试用例
      String name = entry.getKey(); // 获取测试用例名称
      if (previousName != null && previousName.compareTo(name) > 0) { // 如果上一个名称大于当前名称（顺序错误）
        outOfOrderNames.add(name); // 添加到列表中
      }
      previousName = name; // 更新上一个名称
    }

    // If any nodes were out of order, rebuild the document in sorted order. // 如果任何节点顺序错误，则按排序顺序重建文档
    if (!outOfOrderNames.isEmpty()) { // 如果列表不为空
      for (Node testCase : testCases.values()) { // 遍历所有测试用例节点
        root.removeChild(testCase); // 从根元素中移除
      }
      for (Node testCase : testCases.values()) { // 再次遍历（此时按排序顺序）
        root.appendChild(testCase); // 按排序顺序添加到根元素
      }
    }
    return ImmutableSortedSet.copyOf(outOfOrderNames); // 返回不可变的排序集合
  }

  /**
   * Returns a given resource from a given test case. // 从指定测试用例中返回指定资源
   *
   * @param testCaseElement The enclosing TestCase element, e.g. <code>
   *                        &lt;TestCase name="testFoo"&gt;</code>. // 包含的TestCase元素
   * @param resourceName    Name of resource, e.g. "sql", "plan" // 资源名称，如"sql"、"plan"
   * @return The value of the resource, or null if not found // 资源的值，如果未找到则返回null
   */
  private static @Nullable Element getResourceElement( // 获取资源元素
      Element testCaseElement, // 测试用例元素
      String resourceName) { // 资源名称
    return getResourceElement(testCaseElement, resourceName, false); // 调用重载方法，不删除重复资源
  }

  /**
   * Returns a given resource from a given test case. // 从指定测试用例中返回指定资源
   *
   * @param testCaseElement The enclosing TestCase element, e.g. <code>
   *                        &lt;TestCase name="testFoo"&gt;</code>. // 包含的TestCase元素
   * @param resourceName    Name of resource, e.g. "sql", "plan" // 资源名称，如"sql"、"plan"
   * @param killYoungerSiblings Whether to remove resources with the same
   *                        name and the same parent that are eclipsed // 是否删除同名且被遮挡的资源
   * @return The value of the resource, or null if not found // 资源的值，如果未找到则返回null
   */
  private static @Nullable Element getResourceElement(Element testCaseElement, // 获取资源元素
      String resourceName, boolean killYoungerSiblings) { // 资源名称，是否删除重复资源
    final NodeList childNodes = testCaseElement.getChildNodes(); // 获取所有子节点
    Element found = null; // 找到的资源元素
    final List<Node> kills = new ArrayList<>(); // 需要删除的节点列表
    for (int i = 0; i < childNodes.getLength(); i++) { // 遍历子节点
      Node child = childNodes.item(i); // 获取当前子节点
      if (child.getNodeName().equals(RESOURCE_TAG) // 如果是Resource元素
          && resourceName.equals( // 且资源名称匹配
              ((Element) child).getAttribute(RESOURCE_NAME_ATTR))) {
        if (found == null) { // 如果还未找到资源
          found = (Element) child; // 保存找到的资源
        } else if (killYoungerSiblings) { // 如果找到了重复资源且需要删除
          kills.add(child); // 添加到删除列表
        }
      }
    }
    for (Node kill : kills) { // 遍历删除列表
      testCaseElement.removeChild(kill); // 从测试用例元素中删除重复资源
    }
    return found; // 返回找到的资源元素
  }

  private static void removeAllChildren(Element element) { // 删除元素的所有子节点
    final NodeList childNodes = element.getChildNodes(); // 获取所有子节点
    while (childNodes.getLength() > 0) { // 当还有子节点时
      element.removeChild(childNodes.item(0)); // 删除第一个子节点
    }
  }

  private static boolean replaceChildren(Element element, List<Node> children) { // 替换元素的子节点
    // Current children // 当前子节点
    final NodeList childNodes = element.getChildNodes(); // 获取所有子节点
    final List<Node> list = new ArrayList<>(); // 创建非文本子节点列表
    for (Node item : iterate(childNodes)) { // 遍历子节点
      if (item.getNodeType() != Node.TEXT_NODE) { // 如果不是文本节点
        list.add(item); // 添加到列表中
      }
    }

    // Are new children equal to old? // 新子节点是否与旧子节点相等？
    if (equalList(children, list)) { // 如果相等
      return false; // 返回false（不需要替换）
    }

    // Replace old children with new children // 用新子节点替换旧子节点
    removeAllChildren(element); // 删除所有旧子节点
    children.forEach(element::appendChild); // 添加所有新子节点
    return true; // 返回true（已替换）
  }

  /** Returns whether two lists of nodes are equal. */ // 返回两个节点列表是否相等
  private static boolean equalList(List<Node> list0, List<Node> list1) { // 比较两个节点列表是否相等
    return list1.size() == list0.size() // 如果大小相等
        && Pair.zip(list1, list0).stream() // 且所有对应的节点相等
        .allMatch(p -> p.left.isEqualNode(p.right)); // 使用isEqualNode方法比较节点
  }

  /**
   * Serializes an XML document as text. // 将XML文档序列化为文本
   *
   * <p>FIXME: I'm sure there's a library call to do this, but I'm danged if I
   * can find it. -- jhyde, 2006/2/9. // FIXME: 我确定有一个库调用来做这个，但我找不到它
   */
  private static void write(Document doc, Writer w, int indent) { // 将文档写入写入器
    final XmlOutput out = new XmlOutput(w); // 创建XML输出对象
    out.setGlob(true); // 启用全局处理
    out.setIndentString(Spaces.of(indent)); // 设置缩进字符串
    writeNode(doc, out); // 写入文档节点
  }

  private static void writeNode(Node node, XmlOutput out) { // 递归写入节点
    final NodeList childNodes; // 子节点列表
    switch (node.getNodeType()) { // 根据节点类型处理
    case Node.DOCUMENT_NODE: // 如果是文档节点
      out.print("<?xml version=\"1.0\" ?>\n"); // 输出XML声明
      childNodes = node.getChildNodes(); // 获取子节点
      for (int i = 0; i < childNodes.getLength(); i++) { // 遍历子节点
        Node child = childNodes.item(i); // 获取当前子节点
        writeNode(child, out); // 递归写入子节点
      }
      break; // 跳出switch

    case Node.ELEMENT_NODE: // 如果是元素节点
      Element element = (Element) node; // 转换为Element
      final String tagName = element.getTagName(); // 获取标签名
      out.beginBeginTag(tagName); // 开始开始标签

      // Attributes. // 属性
      final NamedNodeMap attributeMap = element.getAttributes(); // 获取属性映射
      for (int i = 0; i < attributeMap.getLength(); i++) { // 遍历属性
        final Node att = attributeMap.item(i); // 获取当前属性
        out.attribute( // 输出属性
            att.getNodeName(), // 属性名
            att.getNodeValue()); // 属性值
      }
      out.endBeginTag(tagName); // 结束开始标签

      // Write child nodes, ignoring attributes but including text. // 写入子节点，忽略属性但包括文本
      childNodes = node.getChildNodes(); // 获取子节点
      for (int i = 0; i < childNodes.getLength(); i++) { // 遍历子节点
        Node child = childNodes.item(i); // 获取当前子节点
        if (child.getNodeType() == Node.ATTRIBUTE_NODE) { // 如果是属性节点
          continue; // 跳过
        }
        writeNode(child, out); // 递归写入子节点
      }
      out.endTag(tagName); // 输出结束标签
      break; // 跳出switch

    case Node.ATTRIBUTE_NODE: // 如果是属性节点
      out.attribute( // 输出属性
          node.getNodeName(), // 属性名
          node.getNodeValue()); // 属性值
      break; // 跳出switch

    case Node.CDATA_SECTION_NODE: // 如果是CDATA节点
      CDATASection cdata = (CDATASection) node; // 转换为CDATASection
      out.cdata( // 输出CDATA
          cdata.getNodeValue(), // CDATA内容
          true); // 作为CDATA输出
      break; // 跳出switch

    case Node.TEXT_NODE: // 如果是文本节点
      Text text = (Text) node; // 转换为Text
      final String wholeText = text.getNodeValue(); // 获取完整文本
      if (!isWhitespace(wholeText)) { // 如果不是纯空白
        out.cdata(wholeText, false); // 输出为CDATA（不标记为CDATA）
      }
      break; // 跳出switch

    case Node.COMMENT_NODE: // 如果是注释节点
      Comment comment = (Comment) node; // 转换为Comment
      out.print("<!--" + comment.getNodeValue() + "-->\n"); // 输出注释
      break; // 跳出switch

    default: // 默认情况
      throw new RuntimeException("unexpected node type: " + node.getNodeType() // 抛出异常
          + " (" + node + ")"); // 提示意外的节点类型
    }
  }

  private static boolean isWhitespace(String text) { // 检查字符串是否为纯空白
    for (int i = 0, count = text.length(); i < count; ++i) { // 遍历字符
      final char c = text.charAt(i); // 获取当前字符
      switch (c) { // 检查字符类型
      case ' ': // 空格
      case '\t': // 制表符
      case '\n': // 换行符
        break; // 继续
      default: // 其他字符
        return false; // 不是纯空白
      }
    }
    return true; // 是纯空白
  }

  /**
   * Finds the repository instance for a given class, with no base
   * repository or filter. // 查找给定类的仓库实例，没有基础仓库或过滤器
   *
   * @param clazz Test case class // 测试用例类
   * @return The diff repository shared between test cases in this class. // 在此类中测试用例之间共享的diff repository
   */
  public static DiffRepository lookup(Class<?> clazz) { // 查找仓库实例（简化版本）
    return lookup(clazz, null, null, 2); // 调用完整版本，使用默认缩进2
  }

  /**
   * Finds the repository instance for a given class. // 查找给定类的仓库实例
   *
   * <p>It is important that all test cases in a class share the same
   * repository instance. This ensures that, if two or more test cases fail,
   * the log file will contains the actual results of both test cases. // 同一类中的所有测试用例共享同一个仓库实例很重要。这确保了如果两个或多个测试用例失败，日志文件将包含两个测试用例的实际结果
   *
   * <p>The <code>baseRepository</code> parameter is useful if the test is an
   * extension to a previous test. If the test class has a base class which
   * also has a repository, specify the repository here. DiffRepository will
   * look for resources in the base class if it cannot find them in this
   * repository. If test resources from test cases in the base class are
   * missing or incorrect, it will not write them to the log file -- you
   * probably need to fix the base test. // baseRepository参数在测试是先前测试的扩展时很有用。如果测试类有一个也具有仓库的基类，请在此处指定仓库。如果在此仓库中找不到资源，DiffRepository将在基类中查找。如果基类中测试用例的测试资源缺失或不正确，它将不会将它们写入日志文件 - 您可能需要修复基类测试
   *
   * <p>Use the <code>filter</code> parameter if you expect the test to
   * return results slightly different than in the repository. This happens
   * if the behavior of a derived test is slightly different than a base
   * test. If you do not specify a filter, no filtering will happen. // 如果您期望测试返回的结果与仓库中的结果略有不同，请使用filter参数。如果派生测试的行为与基类测试略有不同，就会发生这种情况。如果不指定过滤器，将不会进行过滤
   *
   * @param clazz     Test case class // 测试用例类
   * @param baseRepository Base repository // 基础仓库
   * @param filter    Filters each string returned by the repository // 过滤仓库返回的每个字符串
   * @param indent    Indent of the XML file (usually 2) // XML文件的缩进（通常为2）
   *
   * @return The diff repository shared between test cases in this class // 在此类中测试用例之间共享的diff repository
   */
  public static DiffRepository lookup(Class<?> clazz, // 查找仓库实例（完整版本）
      @Nullable DiffRepository baseRepository, @Nullable Filter filter, // 基础仓库和过滤器
      int indent) { // 缩进空格数
    final Key key = new Key(clazz, baseRepository, filter, indent); // 创建缓存键
    return REPOSITORY_CACHE.getUnchecked(key); // 从缓存获取仓库实例
  }

  /**
   * Callback to filter strings before returning them. // 在返回字符串之前过滤字符串的回调
   */
  public interface Filter { // 过滤器接口
    /**
     * Filters a string. // 过滤字符串
     *
     * @param diffRepository Repository // 仓库实例
     * @param testCaseName   Test case name // 测试用例名称
     * @param tag            Tag being expanded // 正在展开的标签
     * @param text           Text being expanded // 正在展开的文本
     * @param expanded       Expanded text // 展开后的文本
     * @return Expanded text after filtering // 过滤后的展开文本
     */
    String filter( // 过滤方法
        DiffRepository diffRepository, // 仓库实例
        String testCaseName, // 测试用例名称
        String tag, // 标签
        String text, // 原始文本
        String expanded); // 展开后的文本
  }

  /** Cache key. */ // 缓存键类
  private static class Key { // 缓存键
    private final Class<?> clazz; // 测试类
    private final @Nullable DiffRepository baseRepository; // 基础仓库
    private final @Nullable Filter filter; // 过滤器
    private final int indent; // 缩进空格数

    Key(Class<?> clazz, @Nullable DiffRepository baseRepository, // 构造方法
        @Nullable Filter filter, int indent) { // 参数：测试类、基础仓库、过滤器、缩进
      this.clazz = requireNonNull(clazz, "clazz"); // 初始化测试类，确保不为null
      this.baseRepository = baseRepository; // 初始化基础仓库
      this.filter = filter; // 初始化过滤器
      this.indent = indent; // 初始化缩进
    }

    @Override public int hashCode() { // 重写hashCode方法
      return Objects.hash(clazz, baseRepository, filter); // 使用Objects.hash计算哈希值
    }

    @Override public boolean equals(Object obj) { // 重写equals方法
      return this == obj // 如果是同一个对象
          || obj instanceof Key // 或者是Key类型的对象
          && clazz.equals(((Key) obj).clazz) // 且测试类相等
          && Objects.equals(baseRepository, ((Key) obj).baseRepository) // 且基础仓库相等
          && Objects.equals(filter, ((Key) obj).filter); // 且过滤器相等
    }

    DiffRepository toRepo() { // 将Key转换为DiffRepository实例
      final URL refFile = findFile(clazz, ".xml"); // 查找参考文件URL
      final String refFilePath = Sources.of(refFile).file().getAbsolutePath(); // 获取参考文件绝对路径
      final String logFilePath = refFilePath // 构建日志文件路径
          .replace("resources", "diffrepo") // 替换resources为diffrepo
          .replace(".xml", "_actual.xml"); // 替换.xml为_actual.xml
      final File logFile = new File(logFilePath); // 创建日志文件对象
      assert !refFilePath.equals(logFile.getAbsolutePath()); // 断言参考文件路径不等于日志文件路径
      Set<String> javaTestMethods = // 获取Java中所有以test开头的方法名
          Arrays.stream(clazz.getDeclaredMethods()).map(Method::getName) // 获取所有声明的方法名
              .filter(name -> name.startsWith("test")).collect(Collectors.toSet()); // 过滤出以test开头的方法名
      return new DiffRepository(refFile, logFile, baseRepository, filter, // 创建并返回DiffRepository实例
          indent, javaTestMethods); // 传入缩进和Java测试方法集合
    }
  }

  private static Iterable<Node> iterate(NodeList nodeList) { // 将NodeList转换为Iterable
    return new AbstractList<Node>() { // 创建AbstractList的匿名子类
      @Override public Node get(int index) { // 实现get方法
        return nodeList.item(index); // 返回指定索引的节点
      }

      @Override public int size() { // 实现size方法
        return nodeList.getLength(); // 返回节点列表的长度
      }
    };
  }
} // DiffRepository类结束
