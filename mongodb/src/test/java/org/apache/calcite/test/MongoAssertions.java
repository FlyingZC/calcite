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
 */ // Apache许可证声明，定义该文件的使用权限和限制
package org.apache.calcite.test; // 定义包名，该类位于org.apache.calcite.test包下，是Calcite测试包的一部分

import org.apache.calcite.config.CalciteSystemProperty; // 导入Calcite系统属性类，用于获取配置信息
import org.apache.calcite.util.TestUtil; // 导入测试工具类，提供测试相关的辅助方法

import com.google.common.collect.Ordering; // 导入Google Guava的Ordering类，用于集合排序

import java.sql.ResultSet; // 导入JDBC ResultSet接口，表示数据库查询结果集
import java.sql.SQLException; // 导入SQLException异常类，处理SQL操作异常
import java.util.ArrayList; // 导入ArrayList动态数组类
import java.util.Arrays; // 导入Arrays工具类，提供数组操作方法
import java.util.Collections; // 导入Collections工具类，提供集合操作方法
import java.util.List; // 导入List接口，表示有序集合
import java.util.function.Consumer; // 导入Consumer函数式接口，用于消费数据
import java.util.regex.Pattern; // 导入正则表达式Pattern类，用于字符串模式匹配

import static org.hamcrest.CoreMatchers.equalTo; // 导入Hamcrest断言库的equalTo匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言方法
import static org.junit.jupiter.api.Assumptions.assumeTrue; // 导入JUnit5的假设断言方法

/**
 * Util class which needs to be in the same package as {@link CalciteAssert}
 * due to package-private visibility.
 */ // 这是一个工具类，由于CalciteAssert类具有包级私有可见性，所以这个类必须与CalciteAssert在同一个包中
public class MongoAssertions { // 定义MongoAssertions类，提供MongoDB测试相关的断言和工具方法，主要用于MongoDB适配器的集成测试

  private static final Pattern PATTERN = Pattern.compile("\\.0$"); // 定义正则表达式模式，用于匹配以".0"结尾的字符串，主要用于过滤MongoDB查询结果中的浮点数小数部分

  private MongoAssertions() {} // 私有构造方法，防止实例化，这是一个纯工具类，所有方法都是静态的

  /**
   * Similar to {@link CalciteAssert#checkResultUnordered}, but filters strings
   * before comparing them.
   *
   * @param lines Expected expressions
   * @return validation function
   */ // 方法注释：类似于CalciteAssert.checkResultUnordered，但在比较之前会过滤字符串中的特定模式（如".0"）
  public static Consumer<ResultSet> checkResultUnordered( // 定义静态方法，返回一个Consumer<ResultSet>函数式接口，用于验证ResultSet结果
      final String... lines) { // 可变参数，期望的结果行字符串数组
    return resultSet -> { // 返回一个lambda表达式，接收ResultSet参数并执行验证逻辑
      try { // 开始try块，捕获可能抛出的SQL异常
        final List<String> expectedList = // 创建期望结果的列表，用于后续比较
            Ordering.natural().immutableSortedCopy(Arrays.asList(lines)); // 将期望的行数组转换为不可变的有序列表，使用自然排序

        final List<String> actualList = new ArrayList<>(); // 创建实际结果的列表，用于存储从ResultSet中提取的数据
        CalciteAssert.toStringList(resultSet, actualList); // 调用CalciteAssert工具方法，将ResultSet转换为字符串列表并填充到actualList中
        for (int i = 0; i < actualList.size(); i++) { // 遍历实际结果列表，对每一行字符串进行处理
          String s = actualList.get(i); // 获取当前索引位置的字符串
          s = s.replace(".0;", ";"); // 替换字符串中的".0;"为";"，这用于去除浮点数的小数部分（如将"123.0;"转换为"123;"）
          s = PATTERN.matcher(s).replaceAll(""); // 使用正则表达式替换字符串末尾的".0"，进一步清理浮点数格式
          actualList.set(i, s); // 将处理后的字符串重新设置到列表中
        } // for循环结束，所有字符串都已处理完毕
        Collections.sort(actualList); // 对实际结果列表进行排序，使其与期望结果的顺序一致

        assertThat(Ordering.natural().immutableSortedCopy(actualList), // 使用Hamcrest断言验证实际结果是否等于期望结果，先将实际结果转换为不可变的有序列表
            equalTo(expectedList)); // 使用equalTo匹配器比较实际结果列表和期望结果列表是否相等
      } catch (SQLException e) { // 捕获SQL异常
        throw TestUtil.rethrow(e); // 使用TestUtil工具类重新抛出异常，保留原始异常的堆栈跟踪信息
      } // catch块结束
    }; // lambda表达式结束
  } // checkResultUnordered方法结束

  /**
   * Whether to run Mongo integration tests. Enabled by default, however test is only
   * included if "it" profile is activated ({@code -Pit}). To disable,
   * specify {@code -Dcalcite.test.mongodb=false} on the Java command line.
   *
   * @return Whether current tests should use an external mongo instance
   */ // 方法注释：判断是否运行MongoDB集成测试，默认启用，但需要激活"it"配置文件（-Pit参数），可以通过-Dcalcite.test.mongodb=false禁用
  public static boolean useMongo() { // 定义静态方法，返回布尔值，判断是否使用真实的MongoDB实例
    return CalciteSystemProperty.INTEGRATION_TEST.value() // 检查是否启用了集成测试配置
            && CalciteSystemProperty.TEST_MONGODB.value(); // 同时检查是否启用了MongoDB测试配置，两个条件都为true才使用真实MongoDB
  } // useMongo方法结束

  /**
   * Checks wherever tests should use Embedded Fake Mongo instead of connecting to real
   * mongodb instance. Opposite of {@link #useMongo()}.
   *
   * @return Whether current tests should use embedded
   * <a href="https://github.com/bwaldvogel/mongo-java-server">Mongo Java Server</a> instance
   */ // 方法注释：判断是否使用嵌入式Fake Mongo（Mongo Java Server）而不是连接真实的MongoDB实例，这是useMongo()方法的相反逻辑
  public static boolean useFake() { // 定义静态方法，返回布尔值，判断是否使用嵌入式MongoDB实例
    return !useMongo(); // 返回useMongo()的相反结果，如果不使用真实MongoDB，则使用嵌入式Fake Mongo
  } // useFake方法结束

  /**
   * Used to skip tests if current instance is not mongo. Some functionalities
   * are not available in fongo.
   *
   * @see <a href="https://github.com/fakemongo/fongo/issues/152">Aggregation with $cond (172)</a>
   */ // 方法注释：假设使用真实的MongoDB实例，如果当前不是MongoDB实例则跳过测试，因为某些功能在fongo中不可用（如$cond聚合操作）
  public static void assumeRealMongoInstance() { // 定义静态方法，使用JUnit5的assumeTrue断言来假设必须使用真实MongoDB实例
    assumeTrue(useMongo(), "Expect mongo instance"); // 如果useMongo()返回false，则跳过测试并输出提示信息"Expect mongo instance"
  } // assumeRealMongoInstance方法结束
} // MongoAssertions类结束
