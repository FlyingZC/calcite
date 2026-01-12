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

import org.apache.calcite.adapter.enumerable.EnumerableRules; // 导入EnumerableRules类，包含可枚举规则集合
import org.apache.calcite.avatica.AvaticaUtils; // 导入AvaticaUtils工具类，提供工具方法
import org.apache.calcite.config.CalciteConnectionProperty; // 导入Calcite连接属性枚举
import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite连接接口
import org.apache.calcite.plan.RelOptPlanner; // 导入关系优化器接口
import org.apache.calcite.plan.RelOptRule; // 导入关系优化规则接口
import org.apache.calcite.plan.RelRule.Config; // 导入RelRule配置接口
import org.apache.calcite.prepare.Prepare; // 导入Prepare类，处理SQL准备
import org.apache.calcite.rel.rules.CoreRules; // 导入CoreRules类，包含核心规则集合
import org.apache.calcite.rel.rules.RuleConfig; // 导入RuleConfig注解
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口
import org.apache.calcite.runtime.Hook; // 导入Hook类，用于扩展点
import org.apache.calcite.schema.Schema; // 导入Schema接口
import org.apache.calcite.schema.impl.AbstractSchema; // 导入抽象Schema基类
import org.apache.calcite.schema.impl.AbstractTable; // 导入抽象Table基类
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举
import org.apache.calcite.test.schemata.catchall.CatchallSchema; // 导入CatchallSchema测试Schema
import org.apache.calcite.util.Bug; // 导入Bug类，用于处理已知问题
import org.apache.calcite.util.Closer; // 导入Closer工具类，用于资源管理
import org.apache.calcite.util.Sources; // 导入Sources工具类，处理文件源
import org.apache.calcite.util.Util; // 导入Util工具类，提供通用工具方法

import com.google.common.io.PatternFilenameFilter; // 导入Google Guava的模式文件名过滤器

import net.hydromatic.quidem.CommandHandler; // 导入Quidem命令处理器接口
import net.hydromatic.quidem.Quidem; // 导入Quidem主类，用于执行测试脚本

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解
import org.junit.jupiter.api.TestInstance; // 导入JUnit5测试实例注解
import org.junit.jupiter.params.ParameterizedTest; // 导入JUnit5参数化测试注解
import org.junit.jupiter.params.provider.MethodSource; // 导入JUnit5方法源注解

import java.io.File; // 导入File类，处理文件操作
import java.io.FilenameFilter; // 导入文件名过滤器接口
import java.io.Reader; // 导入Reader接口，读取字符流
import java.io.Writer; // 导入Writer接口，写入字符流
import java.lang.reflect.Field; // 导入Field类，反射字段
import java.lang.reflect.InvocationTargetException; // 导入反射调用异常
import java.lang.reflect.Method; // 导入Method类，反射方法
import java.lang.reflect.Modifier; // 导入Modifier类，反射修饰符
import java.math.BigDecimal; // 导入BigDecimal类，精确小数
import java.net.URL; // 导入URL类，统一资源定位符
import java.sql.Connection; // 导入JDBC连接接口
import java.sql.DriverManager; // 导入JDBC驱动管理器
import java.util.ArrayList; // 导入ArrayList动态数组
import java.util.Collection; // 导入Collection集合接口
import java.util.List; // 导入List列表接口
import java.util.function.Consumer; // 导入Consumer函数式接口
import java.util.function.Function; // 导入Function函数式接口
import java.util.regex.Matcher; // 导入正则匹配器
import java.util.regex.Pattern; // 导入正则表达式类

import static org.junit.jupiter.api.Assertions.fail; // 导入JUnit断言失败方法

import static java.util.Objects.requireNonNull; // 导入对象非空检查方法

/**
 * Test that runs every Quidem file as a test.
 * 测试类，用于将每个Quidem文件作为测试运行
 * Quidem是一种测试框架，使用.iq文件编写SQL测试脚本
 * 该类是所有Quidem测试的基类，提供测试执行的基础设施
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // 指定测试实例生命周期为每个类一个实例
public abstract class QuidemTest { // 抽象测试类，需要子类实现具体测试

  private static final Pattern PATTERN = Pattern.compile("\\.iq$"); // 正则表达式模式，匹配.iq文件扩展名

  // Saved original planner rules
  private static @Nullable List<RelOptRule> originalRules; // 保存原始优化器规则的静态变量，用于规则重置

  private static @Nullable Object getEnv(String varName) { // 获取环境变量或特殊函数，用于Quidem测试脚本中的条件判断
    switch (varName) { // 根据变量名返回不同的值
    case "jdk18": // 检查是否为JDK 1.8
      return System.getProperty("java.version").startsWith("1.8"); // 返回Java版本是否为1.8的布尔值
    case "fixed": // 返回一个函数，用于检查特定Bug是否已修复
      // Quidem requires a Java 8 function
      return (Function<String, Object>) v -> { // 返回一个函数，接受Bug名称并返回是否已修复
        switch (v) { // 根据Bug名称返回对应的状态
        case "calcite1045": // CALCITE-1045 Bug
          return Bug.CALCITE_1045_FIXED; // 返回该Bug是否已修复
        case "calcite1048": // CALCITE-1048 Bug
          return Bug.CALCITE_1048_FIXED; // 返回该Bug是否已修复
        }
        return null; // 未知Bug返回null
      };
    case "not": // 返回一个函数，用于对另一个函数的结果取反
      return (Function<String, Object>) v -> { // 返回一个函数，接受变量名并返回取反后的函数
        final Object o = getEnv(v); // 获取原始环境变量
        if (o instanceof Function) { // 如果原始值是函数
          @SuppressWarnings("unchecked") final Function<String, Object> f = // 抑制类型检查警告
              (Function<String, Object>) o; // 强制转换为Function类型
          return (Function<String, Object>) v2 -> !((Boolean) f.apply(v2)); // 返回一个新函数，对原函数结果取反
        }
        return null; // 如果不是函数则返回null
      };
    default:
      return null; // 未知变量名返回null
    }
  }

  private @Nullable Method findMethod(String path) { // 根据文件路径查找对应的测试方法
    // E.g. path "sql/agg.iq" gives method "testSqlAgg"
    final String path1 = path.replace(File.separatorChar, '_'); // 将文件路径分隔符替换为下划线
    final String path2 = PATTERN.matcher(path1).replaceAll(""); // 移除.iq文件扩展名
    String methodName = AvaticaUtils.toCamelCase("test_" + path2); // 将路径转换为驼峰命名的方法名
    Method m; // 声明方法变量
    try {
      m = getClass().getMethod(methodName, String.class); // 尝试获取指定名称和参数类型的方法
    } catch (NoSuchMethodException e) { // 如果方法不存在
      m = null; // 设置为null
    }
    return m; // 返回找到的方法或null
  }

  protected static Collection<String> data(String first) { // 收集指定目录下所有.iq测试文件的路径
    // inUrl = "file:/home/fred/calcite/core/target/test-classes/sql/agg.iq"
    final URL inUrl = QuidemTest.class.getResource("/" + n2u(first)); // 获取第一个测试文件的URL资源
    final File firstFile = Sources.of(requireNonNull(inUrl, "inUrl")).file(); // 将URL转换为File对象
    final int commonPrefixLength = firstFile.getAbsolutePath().length() - first.length(); // 计算公共前缀长度
    final File dir = firstFile.getParentFile(); // 获取文件所在目录
    final List<String> paths = new ArrayList<>(); // 创建路径列表
    final FilenameFilter filter = new PatternFilenameFilter(".*\\.iq$"); // 创建文件名过滤器，只匹配.iq文件
    for (File f : Util.first(dir.listFiles(filter), new File[0])) { // 遍历目录下所有.iq文件
      paths.add(f.getAbsolutePath().substring(commonPrefixLength)); // 添加相对路径到列表
    }
    return paths; // 返回所有测试文件路径的集合
  }

  protected void checkRun(String path) throws Exception { // 执行Quidem测试文件并验证输出
    final File inFile; // 输入文件变量
    final File outFile; // 输出文件变量
    final File f = new File(path); // 创建文件对象
    if (f.isAbsolute()) { // 如果是绝对路径
      // e.g. path = "/tmp/foo.iq"
      inFile = f; // 直接使用该文件作为输入
      outFile = new File(path + ".out"); // 输出文件为输入文件加上.out后缀
    } else { // 如果是相对路径
      // e.g. path = "sql/agg.iq"
      // inUrl = "file:/home/fred/calcite/core/build/resources/test/sql/agg.iq"
      // inFile = "/home/fred/calcite/core/build/resources/test/sql/agg.iq"
      // outDir = "/home/fred/calcite/core/build/quidem/test/sql"
      // outFile = "/home/fred/calcite/core/build/quidem/test/sql/agg.iq"
      final URL inUrl = QuidemTest.class.getResource("/" + n2u(path)); // 从类路径获取资源URL
      inFile = Sources.of(requireNonNull(inUrl, "inUrl")).file(); // 转换为File对象
      outFile = replaceDir(inFile, "resources", "quidem"); // 将resources目录替换为quidem目录
    }
    Util.discard(outFile.getParentFile().mkdirs()); // 创建输出文件的父目录
    try (Reader reader = Util.reader(inFile); // 创建输入文件读取器
         Writer writer = Util.printWriter(outFile); // 创建输出文件写入器
         Closer closer = new Closer()) { // 创建资源关闭器
      final Quidem.Config config = Quidem.configBuilder() // 开始构建Quidem配置
          .withReader(reader) // 设置输入读取器
          .withWriter(writer) // 设置输出写入器
          .withConnectionFactory(createConnectionFactory()) // 设置连接工厂
          .withCommandHandler(createCommandHandler()) // 设置命令处理器
          .withPropertyHandler((propertyName, value) -> { // 设置属性处理器
            if (propertyName.equals("bindable")) { // 如果属性是bindable
              final boolean b = value instanceof Boolean // 检查值是否为布尔类型
                  && (Boolean) value; // 获取布尔值
              closer.add(Hook.ENABLE_BINDABLE.addThread(Hook.propertyJ(b))); // 设置可绑定Hook
            }
            if (propertyName.equals("expand")) { // 如果属性是expand
              final boolean b = value instanceof Boolean // 检查值是否为布尔类型
                  && (Boolean) value; // 获取布尔值
              closer.add(Prepare.THREAD_EXPAND.push(b)); // 设置展开Hook
            }
            if (propertyName.equals("insubquerythreshold")) { // 如果属性是insubquerythreshold
              int thresholdValue = ((BigDecimal) value).intValue(); // 获取阈值整数值
              closer.add(Prepare.THREAD_INSUBQUERY_THRESHOLD.push(thresholdValue)); // 设置子查询阈值
            }
            // Configures query planner rules via "!set planner-rules" command.
            // The value can be set as follows:
            // - Add rule:       "+EnumerableRules.ENUMERABLE_INTERSECT_RULE"
            // - Remove rule:    "-CoreRules.INTERSECT_TO_DISTINCT"
            // - Short form:     "+INTERSECT_TO_DISTINCT" (CoreRules prefix may be omitted)
            // - Reset defaults: "original"
            if (propertyName.equals("planner-rules")) { // 如果属性是planner-rules
              if (value.equals("original")) { // 如果值为original
                closer.add(Hook.PLANNER.addThread(this::resetPlanner)); // 重置优化器规则
              } else { // 否则更新优化器规则
                closer.add( // 添加Hook
                    Hook.PLANNER.addThread((Consumer<RelOptPlanner>) // 创建优化器消费者
                        planner -> { // 优化器处理逻辑
                          if (originalRules == null) { // 如果原始规则未保存
                            originalRules = planner.getRules(); // 保存当前规则
                          }
                          updatePlanner(planner, (String) value); // 更新优化器规则
                        }));
              }
            }
          })
          .withEnv(QuidemTest::getEnv) // 设置环境变量提供者
          .build(); // 构建配置对象
      new Quidem(config).execute(); // 执行Quidem测试
    }
    // Sanity check: we do not allow an empty input file, it may indicate that it was overwritten
    if (inFile.length() == 0) { // 检查输入文件是否为空
      fail("Input file was empty: " + inFile + "\n"); // 如果为空则测试失败
    }
    final String diff = DiffTestCase.diff(inFile, outFile); // 比较输入和输出文件的差异
    if (!diff.isEmpty()) { // 如果有差异
      fail("Files differ: " + outFile + " " + inFile + "\n" // 测试失败，显示差异
          + diff); // 输出差异内容
    }
  }

  private void updatePlanner(RelOptPlanner planner, String value) { // 更新优化器的规则集合
    List<RelOptRule> rulesAdd = new ArrayList<>(); // 创建要添加的规则列表
    List<RelOptRule> rulesRemove = new ArrayList<>(); // 创建要移除的规则列表
    parseRules(value, rulesAdd, rulesRemove); // 解析规则字符串，填充添加和移除列表
    rulesRemove.forEach(planner::removeRule); // 从优化器中移除所有需要移除的规则
    rulesAdd.forEach(planner::addRule); // 向优化器中添加所有需要添加的规则
  }

  private void resetPlanner(RelOptPlanner planner) { // 重置优化器规则到原始状态
    if (originalRules != null) { // 如果保存了原始规则
      planner.getRules().forEach(planner::removeRule); // 移除优化器中的所有当前规则
      originalRules.forEach(planner::addRule); // 添加回所有原始规则
    }
  }

  private void parseRules(String value, List<RelOptRule> rulesAdd, List<RelOptRule> rulesRemove) { // 解析规则字符串
    Pattern pattern = Pattern.compile("([+-])((CoreRules|EnumerableRules)\\.)?(\\w+)"); // 创建正则表达式匹配规则
    Matcher matcher = pattern.matcher(value); // 创建匹配器

    while (matcher.find()) { // 循环查找所有匹配项
      char operation = matcher.group(1).charAt(0); // 获取操作符（+或-）
      String ruleSource = matcher.group(3); // 获取规则源（CoreRules或EnumerableRules）
      String ruleName = matcher.group(4); // 获取规则名称

      try { // 尝试获取规则对象
        if (ruleSource == null || ruleSource.equals("CoreRules")) { // 如果是CoreRules
          setRules(operation, getCoreRule(ruleName), rulesAdd, rulesRemove); // 处理CoreRules规则
        } else if (ruleSource.equals("EnumerableRules")) { // 如果是EnumerableRules
          Object rule = EnumerableRules.class.getField(ruleName).get(null); // 通过反射获取规则对象
          setRules(operation, (RelOptRule) rule, rulesAdd, rulesRemove); // 处理EnumerableRules规则
        } else { // 未知规则源
          throw new RuntimeException("Unknown rule: " + ruleName); // 抛出运行时异常
        }
      } catch (NoSuchFieldException | IllegalAccessException e) { // 捕获反射异常
        throw new RuntimeException("set rules failed: " + e.getMessage(), e); // 抛出运行时异常
      }
    }
  }

  public static RelOptRule getCoreRule(String ruleName) { // 通过名称获取CoreRules中的规则对象
    RelOptRule rule = null; // 初始化规则对象为null
    try {
      // Get rule class and config annotation
      Field ruleField = CoreRules.class.getField(ruleName); // 通过反射获取规则字段
      Class<?> ruleClass = ruleField.getType(); // 获取规则字段的类型

      // Find Config inner class
      Class<?> configClass = null; // 初始化Config类为null
      for (Class<?> innerClass : ruleClass.getDeclaredClasses()) { // 遍历规则类的所有内部类
        if (innerClass.getSimpleName().endsWith("Config")) { // 如果内部类名以Config结尾
          configClass = innerClass; // 找到Config类
          break; // 跳出循环
        }
      }
      if (configClass == null) { // 如果未找到Config类
        // Should not enter
        throw new RuntimeException("Config not found in " + ruleClass.getName()); // 抛出异常
      }

      // Determine config field name
      RuleConfig ruleConfig = ruleField.getAnnotation(RuleConfig.class); // 获取RuleConfig注解
      String configValue = (ruleConfig == null || ruleConfig.value().isEmpty()) // 确定配置值
          ? "DEFAULT" // 默认使用DEFAULT
          : ruleConfig.value(); // 否则使用注解中指定的值

      // Find and process the target config field
      for (Field field : configClass.getDeclaredFields()) { // 遍历Config类的所有字段
        if (field.getType() == configClass // 如果字段类型是Config类本身
            && Modifier.isStatic(field.getModifiers()) // 且是静态字段
            && field.getName().equals(configValue)) { // 且字段名匹配配置值
          field.setAccessible(true); // 设置字段可访问
          Config config = (Config) field.get(null); // 获取Config对象
          rule = config.toRule(); // 将Config转换为规则对象
          break; // 跳出循环
        }
      }

      if (rule == null) { // 如果规则仍为null
        throw new RuntimeException("No matching config value '" + configValue // 抛出异常
            + "' found in " + configClass.getName()); // 显示未找到的配置值
      }
    } catch (NoSuchFieldException | IllegalAccessException // 捕获反射异常
         | RuntimeException e) { // 捕获运行时异常
      throw new RuntimeException("Failed to get rule '" + ruleName + "': " + e.getMessage()); // 抛出异常
    }
    return rule; // 返回获取的规则对象
  }

  private void setRules(char operation, RelOptRule rule, // 根据操作符将规则添加到相应列表
      List<RelOptRule> rulesAdd, List<RelOptRule> rulesRemove) { // 接收添加和移除列表
    if (operation == '+') { // 如果操作符是加号
      rulesAdd.add(rule); // 将规则添加到添加列表
    } else if (operation == '-') { // 如果操作符是减号
      rulesRemove.add(rule); // 将规则添加到移除列表
    } else { // 未知操作符
      throw new RuntimeException("unknown operation '" + operation + "'"); // 抛出异常
    }
  }

  /** Returns a file, replacing one directory with another.
   * 返回一个文件，替换路径中的一个目录为另一个目录
   *
   * <p>For example, {@code replaceDir("/abc/str/astro.txt", "str", "xyz")}
   * returns "{@code "/abc/xyz/astro.txt}".
   * 例如，replaceDir("/abc/str/astro.txt", "str", "xyz") 返回 "/abc/xyz/astro.txt"
   * Note that the file name "astro.txt" does not become "axyzo.txt".
   * 注意文件名"astro.txt"不会变成"axyzo.txt"
   */
  private static File replaceDir(File file, String target, String replacement) { // 替换文件路径中的目录
    return new File( // 返回新的File对象
        n2u(file.getAbsolutePath()).replace(n2u('/' + target + '/'), // 替换目标目录
            n2u('/' + replacement + '/'))); // 为替换目录
  }

  /** Creates a command handler.
   * 创建命令处理器
   */
  protected CommandHandler createCommandHandler() { // 创建Quidem命令处理器
    return Quidem.EMPTY_COMMAND_HANDLER; // 返回空命令处理器
  }

  /** Creates a connection factory.
   * 创建连接工厂
   */
  protected Quidem.ConnectionFactory createConnectionFactory() { // 创建Quidem连接工厂
    return new QuidemConnectionFactory(); // 返回QuidemConnectionFactory实例
  }

  /** Converts a path from native to Unix. On Windows, converts
   * back-slashes to forward-slashes; on Linux, does nothing.
   * 将路径从本地格式转换为Unix格式。在Windows上，将反斜杠转换为正斜杠；在Linux上，不做任何操作。
   */
  private static String n2u(String s) { // 路径格式转换方法
    return File.separatorChar == '\\' // 如果文件分隔符是反斜杠（Windows）
        ? s.replace('\\', '/') // 将反斜杠替换为正斜杠
        : s; // 否则直接返回原字符串
  }

  @ParameterizedTest // 参数化测试注解
  @MethodSource("getPath") // 指定测试数据源方法
  public void test(String path) throws Exception { // 测试方法，执行指定路径的Quidem测试
    final Method method = findMethod(path); // 查找对应的测试方法
    if (method != null) { // 如果找到了对应的方法
      try {
        method.invoke(this, path); // 通过反射调用该方法
      } catch (InvocationTargetException e) { // 捕获调用异常
        Throwable cause = e.getCause(); // 获取异常原因
        if (cause instanceof Exception) { // 如果是Exception类型
          throw (Exception) cause; // 抛出该异常
        }
        if (cause instanceof Error) { // 如果是Error类型
          throw (Error) cause; // 抛出该错误
        }
        throw e; // 抛出原异常
      }
    } else { // 如果没有找到对应方法
      checkRun(path); // 执行默认的检查运行流程
    }
  }

  /** Factory method for {@link QuidemTest#test(String)} parameters.
 * {@link QuidemTest#test(String)}参数的工厂方法
 */
  protected abstract Collection<String> getPath(); // 抽象方法，由子类实现，返回测试文件路径集合

  /** Quidem connection factory for Calcite's built-in test schemas.
   * 用于Calcite内置测试Schema的Quidem连接工厂
   */
  protected static class QuidemConnectionFactory // 内部静态类，实现Quidem连接工厂接口
      implements Quidem.ConnectionFactory { // 实现Quidem.ConnectionFactory接口
    public Connection connect(String name) throws Exception { // 连接方法，单参数版本
      return connect(name, false); // 调用双参数版本，reference设为false
    }

    @Override public Connection connect(String name, boolean reference) // 重写连接方法，支持引用连接
        throws Exception {
      if (reference) { // 如果是引用连接
        if (name.equals("foodmart")) { // 如果名称是foodmart
          final ConnectionSpec db = // 获取foodmart数据库连接规范
              CalciteAssert.DatabaseInstance.HSQLDB.foodmart;
          final Connection connection = // 创建数据库连接
              DriverManager.getConnection(db.url, db.username, // 使用JDBC驱动管理器连接
                  db.password);
          connection.setSchema("foodmart"); // 设置默认Schema为foodmart
          return connection; // 返回连接
        }
        return null; // 其他引用连接返回null
      }
      switch (name) { // 根据名称创建不同的测试连接
      case "hr": // hr人力资源Schema
        return CalciteAssert.hr() // 创建hr断言
            .connect(); // 返回连接
      case "aux": // aux辅助Schema
        return CalciteAssert.hr() // 创建hr断言
            .with(CalciteAssert.Config.AUX) // 配置aux设置
            .connect(); // 返回连接
      case "foodmart": // foodmart食品超市Schema
        return CalciteAssert.that() // 创建断言
            .with(CalciteAssert.Config.FOODMART_CLONE) // 配置foodmart克隆
            .connect(); // 返回连接
      case "geo": // geo地理Schema
        return CalciteAssert.that() // 创建断言
            .with(CalciteAssert.Config.GEO) // 配置geo设置
            .connect(); // 返回连接
      case "scott": // scott经典测试Schema
        return CalciteAssert.that() // 创建断言
            .with(CalciteAssert.Config.SCOTT) // 配置scott设置
            .connect(); // 返回连接
      case "jdbc_scott": // jdbc版本的scott Schema
        return CalciteAssert.that() // 创建断言
            .with(CalciteAssert.Config.JDBC_SCOTT) // 配置jdbc scott
            .connect(); // 返回连接
      case "steelwheels": // steelwheels钢轮Schema
        return CalciteAssert.that() // 创建断言
            .with(CalciteAssert.SchemaSpec.STEELWHEELS) // 配置steelwheels
            .connect(); // 返回连接
      case "jdbc_steelwheels": // jdbc版本的steelwheels Schema
        return CalciteAssert.that() // 创建断言
            .with(CalciteAssert.SchemaSpec.JDBC_STEELWHEELS) // 配置jdbc steelwheels
            .connect(); // 返回连接
      case "post": // post邮政Schema
        return CalciteAssert.that() // 创建断言
            .with(CalciteAssert.Config.REGULAR) // 配置常规设置
            .with(CalciteAssert.SchemaSpec.POST) // 配置post Schema
            .connect(); // 返回连接
      case "post-postgresql": // post Schema使用PostgreSQL函数
        return CalciteAssert.that() // 创建断言
            .with(CalciteConnectionProperty.FUN, "standard,postgresql") // 配置PostgreSQL函数
            .with(CalciteAssert.Config.REGULAR) // 配置常规设置
            .with(CalciteAssert.SchemaSpec.POST) // 配置post Schema
            .connect(); // 返回连接
      case "post-big-query": // post Schema使用BigQuery函数
        return CalciteAssert.that() // 创建断言
            .with(CalciteConnectionProperty.FUN, "standard,bigquery") // 配置BigQuery函数
            .with(CalciteAssert.Config.REGULAR) // 配置常规设置
            .with(CalciteAssert.SchemaSpec.POST) // 配置post Schema
            .connect(); // 返回连接
      case "mysqlfunc": // post Schema使用MySQL函数
        return CalciteAssert.that() // 创建断言
            .with(CalciteConnectionProperty.FUN, "mysql") // 配置MySQL函数
            .with(CalciteAssert.Config.REGULAR) // 配置常规设置
            .with(CalciteAssert.SchemaSpec.POST) // 配置post Schema
            .connect(); // 返回连接
      case "sparkfunc": // post Schema使用Spark函数
        return CalciteAssert.that() // 创建断言
            .with(CalciteConnectionProperty.FUN, "spark") // 配置Spark函数
            .with(CalciteAssert.Config.REGULAR) // 配置常规设置
            .with(CalciteAssert.SchemaSpec.POST) // 配置post Schema
            .connect(); // 返回连接
      case "oraclefunc": // 常规Schema使用Oracle函数
        return CalciteAssert.that() // 创建断言
            .with(CalciteConnectionProperty.FUN, "oracle") // 配置Oracle函数
            .with(CalciteAssert.Config.REGULAR) // 配置常规设置
            .connect(); // 返回连接
      case "mssqlfunc": // 常规Schema使用MSSQL函数
        return CalciteAssert.that() // 创建断言
            .with(CalciteConnectionProperty.FUN, "mssql") // 配置MSSQL函数
            .with(CalciteAssert.Config.REGULAR) // 配置常规设置
            .connect(); // 返回连接
      case "catchall": // catchall捕获所有Schema
        return CalciteAssert.that() // 创建断言
            .with(CalciteConnectionProperty.TIME_ZONE, "UTC") // 配置UTC时区
            .withSchema("s", // 配置Schema名称
                new ReflectiveSchemaWithoutRowCount( // 创建反射Schema
                    new CatchallSchema())) // 使用CatchallSchema
            .connect(); // 返回连接
      case "orinoco": // orinoco Schema
        return CalciteAssert.that() // 创建断言
            .with(CalciteAssert.SchemaSpec.ORINOCO) // 配置orinoco Schema
            .connect(); // 返回连接
      case "seq": // seq序列Schema
        final Connection connection = CalciteAssert.that() // 创建断言
            .withSchema("s", new AbstractSchema()) // 配置抽象Schema
            .connect(); // 返回连接
        connection.unwrap(CalciteConnection.class).getRootSchema() // 获取根Schema
            .subSchemas().get("s") // 获取子Schema
            .add("my_seq", // 添加序列表
                new AbstractTable() { // 创建抽象表
                  @Override public RelDataType getRowType( // 重写获取行类型方法
                      RelDataTypeFactory typeFactory) { // 接收类型工厂
                    return typeFactory.builder() // 构建类型
                        .add("$seq", SqlTypeName.BIGINT).build(); // 添加序列字段
                  }

                  @Override public Schema.TableType getJdbcTableType() { // 重写获取表类型方法
                    return Schema.TableType.SEQUENCE; // 返回序列类型
                  }
                });
        return connection; // 返回连接
      case "bookstore": // bookstore书店Schema
        return CalciteAssert.that() // 创建断言
            .with(CalciteAssert.SchemaSpec.BOOKSTORE) // 配置bookstore Schema
            .connect(); // 返回连接
      default: // 未知连接名称
        throw new RuntimeException("unknown connection '" + name + "'"); // 抛出异常
      }
    }
  }

}
