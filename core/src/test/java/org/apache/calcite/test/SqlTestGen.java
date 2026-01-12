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
package org.apache.calcite.test; // 声明包名，该类属于 org.apache.calcite.test 测试包

import org.apache.calcite.sql.parser.StringAndPos; // 导入 StringAndPos 类，用于存储字符串及其位置信息（用于错误定位）
import org.apache.calcite.sql.test.SqlTestFactory; // 导入 SqlTestFactory 类，用于创建 SQL 测试的工厂对象
import org.apache.calcite.sql.test.SqlValidatorTester; // 导入 SqlValidatorTester 类，用于测试 SQL 验证器
import org.apache.calcite.sql.validate.SqlValidator; // 导入 SqlValidator 接口，用于验证 SQL 语句的语义正确性
import org.apache.calcite.util.BarfingInvocationHandler; // 导入 BarfingInvocationHandler 类，用于创建动态代理调用处理器，当调用未实现方法时抛出异常
import org.apache.calcite.util.TestUtil; // 导入 TestUtil 工具类，提供测试相关的实用方法
import org.apache.calcite.util.Util; // 导入 Util 工具类，提供通用的实用方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Nullable 注解，用于标记可能为 null 的参数或返回值

import java.io.File; // 导入 File 类，用于文件操作
import java.io.PrintWriter; // 导入 PrintWriter 类，用于向文件写入文本数据
import java.lang.reflect.Method; // 导入 Method 类，用于通过反射获取和调用类的方法
import java.lang.reflect.Proxy; // 导入 Proxy 类，用于创建动态代理对象
import java.util.ArrayList; // 导入 ArrayList 类，用于动态数组列表
import java.util.List; // 导入 List 接口，表示有序的集合

import static org.apache.calcite.util.ReflectUtil.isPublic; // 静态导入 isPublic 方法，用于检查方法是否为 public
import static org.apache.calcite.util.ReflectUtil.isStatic; // 静态导入 isStatic 方法，用于检查方法是否为 static

/**
 * Utility to generate a SQL script from validator test.
 * 这是一个工具类，用于从验证器测试（validator test）生成 SQL 脚本。
 * 它的主要功能是扫描 SqlValidatorTest 类中的所有测试方法，
 * 执行这些测试，并将成功的 SQL 语句输出到一个 SQL 脚本文件中。
 * 这样可以生成一个包含所有有效 SQL 语句的脚本文件，便于后续使用。
 */
class SqlTestGen { // 定义 SqlTestGen 类，用于生成 SQL 测试脚本的工具类
  private SqlTestGen() {} // 私有构造方法，防止实例化，因为这是一个工具类，所有方法都是静态的

  private static final SqlTestFactory SPOOLER_TEST_FACTORY = // 定义一个静态常量，用于创建特殊的 SqlTestFactory 实例
      SqlTestFactory.INSTANCE.withValidator( // 获取 SqlTestFactory 的单例实例，并配置其验证器
          (opTab, catalogReader, typeFactory, config) -> // 使用 lambda 表达式创建自定义的 SqlValidator
              (SqlValidator) Proxy.newProxyInstance( // 使用动态代理创建 SqlValidator 的代理对象
                  SqlValidatorSpooler.class.getClassLoader(), // 指定类加载器，使用 SqlValidatorSpooler 的类加载器
                  new Class[]{SqlValidator.class}, // 指定代理类要实现的接口数组，这里只实现 SqlValidator 接口
                  new SqlValidatorSpooler.MyInvocationHandler())); // 指定调用处理器，使用自定义的 MyInvocationHandler 来处理方法调用

  //~ Methods ---------------------------------------------------------------- // 方法区域的分隔符注释

  public static void main(String[] args) { // 程序的入口方法，接收命令行参数（虽然未使用）
    new SqlTestGen().genValidatorTest(); // 创建 SqlTestGen 实例并调用 genValidatorTest 方法生成验证器测试
  }

  private void genValidatorTest() { // 生成验证器测试的方法，负责执行所有测试并将成功的 SQL 输出到文件
    final File file = new File("validatorTest.sql"); // 创建 File 对象，指定输出文件名为 validatorTest.sql
    try (PrintWriter pw = Util.printWriter(file)) { // 使用 try-with-resources 语句创建 PrintWriter，自动管理资源关闭
      List<Method> methods = getJunitMethods(SqlValidatorSpooler.class); // 获取 SqlValidatorSpooler 类中所有符合 JUnit 测试方法签名的方法
      for (Method method : methods) { // 遍历所有获取到的测试方法
        final SqlValidatorSpooler test = new SqlValidatorSpooler(pw); // 为每个方法创建一个新的 SqlValidatorSpooler 实例，传入 PrintWriter 用于输出
        final Object result = method.invoke(test); // 使用反射调用测试方法，传入 SqlValidatorSpooler 实例作为对象
        assert result == null; // 断言方法返回值为 null，因为测试方法通常返回 void
      }
    } catch (Exception e) { // 捕获可能发生的异常
      throw TestUtil.rethrow(e); // 使用 TestUtil.rethrow 方法重新抛出异常，保留原始异常类型
    }
  }

  /**
   * Returns a list of all Junit methods in a given class.
   * 返回给定类中所有符合 JUnit 测试方法签名的方法列表。
   * JUnit 测试方法的特征是：
   * 1. 方法名以 "test" 开头
   * 2. 是 public 方法
   * 3. 不是 static 方法
   * 4. 没有参数
   * 5. 返回类型为 void
   */
  private static List<Method> getJunitMethods(Class<SqlValidatorSpooler> clazz) { // 获取指定类中所有 JUnit 测试方法的静态方法
    List<Method> list = new ArrayList<>(); // 创建一个 ArrayList 用于存储找到的测试方法
    for (Method method : clazz.getMethods()) { // 遍历类中的所有 public 方法（包括继承的方法）
      if (method.getName().startsWith("test") // 检查方法名是否以 "test" 开头
          && isPublic(method) // 检查方法是否为 public
          && !isStatic(method) // 检查方法是否不是 static
          && (method.getParameterCount() == 0) // 检查方法是否没有参数
          && (method.getReturnType() == Void.TYPE)) { // 检查方法返回类型是否为 void
        list.add(method); // 如果所有条件都满足，将该方法添加到列表中
      }
    }
    return list; // 返回找到的所有测试方法列表
  }

  //~ Inner Classes ---------------------------------------------------------- // 内部类区域的分隔符注释

  /**
   * Subversive subclass, which spools results to a writer rather than running
   * tests.
   * 这是一个颠覆性的子类，它将结果输出到写入器（writer）而不是运行测试。
   * 它继承自 SqlValidatorTest，但重写了测试行为，使其不再执行断言检查，
   * 而是将成功的 SQL 语句写入到输出文件中。
   * 这样可以从现有的测试用例中提取出所有有效的 SQL 语句。
   */
  private static class SqlValidatorSpooler extends SqlValidatorTest { // 定义内部静态类 SqlValidatorSpooler，继承自 SqlValidatorTest
    private final PrintWriter pw; // 成员变量，用于存储 PrintWriter 对象，用于输出 SQL 语句到文件

    private SqlValidatorSpooler(PrintWriter pw) { // 构造方法，接收 PrintWriter 参数
      this.pw = pw; // 将传入的 PrintWriter 赋值给成员变量 pw
    }

    @Override public SqlValidatorFixture fixture() { // 重写 fixture 方法，返回自定义的 SqlValidatorFixture
      return super.fixture() // 调用父类的 fixture 方法获取基础的 SqlValidatorFixture
          .withTester(t -> new SpoolerTester(pw)) // 配置 tester，使用 lambda 创建 SpoolerTester 实例，传入 PrintWriter
          .withFactory(t -> SPOOLER_TEST_FACTORY); // 配置 factory，使用之前定义的 SPOOLER_TEST_FACTORY
    }

    /**
     * Handles the methods in
     * {@link org.apache.calcite.sql.validate.SqlValidator} that are called
     * from validator tests.
     * 这是一个内部静态类，用于处理从验证器测试中调用的 SqlValidator 方法。
     * 它继承自 BarfingInvocationHandler，这意味着对于任何未显式实现的方法，
     * 调用时会抛出异常（barf 表示呕吐，即拒绝处理）。
     * 这里只实现了几个特定的方法，这些方法在测试过程中会被调用。
     */
    public static class MyInvocationHandler extends BarfingInvocationHandler { // 定义内部静态类 MyInvocationHandler，继承自 BarfingInvocationHandler
      public void setIdentifierExpansion(boolean b) { // 重写 setIdentifierExpansion 方法，用于设置是否展开标识符
      } // 方法体为空，不执行任何操作，因为我们只是生成 SQL 脚本，不需要实际设置

      public void setColumnReferenceExpansion(boolean b) { // 重写 setColumnReferenceExpansion 方法，用于设置是否展开列引用
      } // 方法体为空，不执行任何操作

      public void setCallRewrite(boolean b) { // 重写 setCallRewrite 方法，用于设置是否重写函数调用
      } // 方法体为空，不执行任何操作

      public boolean shouldExpandIdentifiers() { // 重写 shouldExpandIdentifiers 方法，询问是否应该展开标识符
        return true; // 返回 true，表示应该展开标识符
      }
    }

    /** Extension of {@link org.apache.calcite.sql.test.SqlTester} that writes
     * out SQL.
     * 这是 SqlValidatorTester 的扩展，用于输出 SQL 语句。
     * 它重写了测试相关的断言方法，使其不再执行实际的验证和断言，
     * 而是将成功的 SQL 语句写入到输出文件中。
     */
    private static class SpoolerTester extends SqlValidatorTester { // 定义内部静态类 SpoolerTester，继承自 SqlValidatorTester
      private final PrintWriter pw; // 成员变量，用于存储 PrintWriter 对象

      SpoolerTester(PrintWriter pw) { // 构造方法，接收 PrintWriter 参数
        this.pw = pw; // 将传入的 PrintWriter 赋值给成员变量 pw
      }

      @Override public void assertExceptionIsThrown(SqlTestFactory factory, // 重写 assertExceptionIsThrown 方法，用于断言是否抛出异常
          StringAndPos sap, @Nullable String expectedMsgPattern) { // 参数：工厂对象、SQL 字符串及位置、期望的异常消息模式
        if (expectedMsgPattern == null) { // 如果期望的异常消息模式为 null，说明这个 SQL 语句应该成功执行
          // This SQL statement is supposed to succeed.
          // Generate it to the file, so we can see what
          // output it produces. // 注释说明：这个 SQL 语句应该成功，将其生成到文件中，以便查看输出
          pw.println("-- " /* + getName() */); // 向文件写入注释行，可以添加测试名称（当前被注释掉）
          pw.println(sap); // 向文件写入 SQL 语句
          pw.println(";"); // 向文件写入分号，表示 SQL 语句结束
        } else { // 如果期望的异常消息模式不为 null，说明这个 SQL 语句应该失败
          // Do nothing. We know that this fails the validator
          // test, so we don't learn anything by having it fail
          // from SQL. // 注释说明：什么都不做，我们知道这会失败验证器测试，所以让它在 SQL 中失败也学不到什么
        }
      }

      @Override public void validateAndThen(SqlTestFactory factory, // 重写 validateAndThen 方法，用于验证 SQL 语句并执行后续操作
          StringAndPos sap, ValidatedNodeConsumer consumer) { // 参数：工厂对象、SQL 字符串及位置、验证后的节点消费者
      } // 方法体为空，不执行任何操作，因为我们只是生成 SQL 脚本，不需要实际验证和消费节点
    }
  }
}
