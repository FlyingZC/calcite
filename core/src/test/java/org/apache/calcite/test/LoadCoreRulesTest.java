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
package org.apache.calcite.test; // 声明包名，表示这个类属于 org.apache.calcite.test 包

import org.apache.calcite.plan.RelOptRule; // 导入 Calcite 的关系表达式优化规则接口，所有优化规则都实现此接口
import org.apache.calcite.rel.rules.CoreRules; // 导入 Calcite 的核心规则集合类，包含了所有核心优化规则的静态实例

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的测试注解，用于标记测试方法

import java.lang.reflect.Field; // 导入 Java 反射 API 中的 Field 类，用于通过反射访问类的字段

import static org.junit.jupiter.api.Assertions.assertEquals; // 导入 JUnit 5 的断言方法，用于验证两个值是否相等
import static org.junit.jupiter.api.Assertions.assertNotEquals; // 导入 JUnit 5 的断言方法，用于验证两个值是否不相等
import static org.junit.jupiter.api.Assertions.assertThrows; // 导入 JUnit 5 的断言方法，用于验证执行某个代码块是否抛出指定异常

/**
 * Unit test for {@link QuidemTest} loading {@link CoreRules}.
 * // 单元测试类，用于测试 QuidemTest 类加载 CoreRules（核心规则）的功能
 * // 这个测试类的主要目的是验证 QuidemTest.getCoreRule() 方法能够正确地通过规则名称加载 CoreRules 类中定义的所有优化规则
 * // 测试包括：加载所有规则、加载不存在的规则、加载特定规则、加载包含子类的规则等场景
 */
class LoadCoreRulesTest { // 定义测试类 LoadCoreRulesTest，用于测试核心规则的加载功能
  //~ Methods ---------------------------------------------------------------- // 方法区域的分隔标记，用于提高代码可读性

  @Test void testLoadAllRules() { // 测试方法：测试加载 CoreRules 类中定义的所有规则
    Field[] fields = CoreRules.class.getDeclaredFields(); // 通过反射获取 CoreRules 类中声明的所有字段（包括私有字段），每个字段对应一个优化规则
    for (Field field : fields) { // 遍历所有字段
      String fieldName = field.getName(); // 获取当前字段的名称，即规则的名称（如 "EXPAND_FILTER_DISJUNCTION_LOCAL"）
      // Skip JaCoCo-injected fields (e.g. $jacocoData)
      if (fieldName.contains("jacocoData")) { // 检查字段名是否包含 "jacocoData"，这是 JaCoCo 代码覆盖率工具自动注入的字段
        continue; // 如果是 JaCoCo 注入的字段，则跳过，不进行规则加载
      }
      QuidemTest.getCoreRule(fieldName); // 调用 QuidemTest.getCoreRule() 方法，根据规则名称加载对应的优化规则对象
    }
  }

  @Test void testLoadNonExistRule() { // 测试方法：测试加载不存在的规则时是否抛出异常
    assertThrows(RuntimeException.class, // 断言执行 lambda 表达式时会抛出 RuntimeException 异常
        () -> QuidemTest.getCoreRule("xxx")); // 尝试加载一个不存在的规则 "xxx"，预期会抛出异常
  }

  @Test void testLoadSpecifyRule() { // 测试方法：测试加载指定的特定规则
    RelOptRule rule1 = // 声明 RelOptRule 类型的变量 rule1，用于存储加载的规则对象
        QuidemTest.getCoreRule("EXPAND_FILTER_DISJUNCTION_LOCAL"); // 通过规则名称 "EXPAND_FILTER_DISJUNCTION_LOCAL" 加载对应的优化规则，这是一个用于展开 Filter 节点上局部析取表达式的规则
    RelOptRule expected1 = CoreRules.EXPAND_FILTER_DISJUNCTION_LOCAL; // 获取 CoreRules 类中定义的预期规则对象，作为比较的基准
    assertEquals(rule1, expected1); // 断言加载的规则对象 rule1 与预期的规则对象 expected1 相等，验证加载的正确性

    RelOptRule rule2 = // 声明 RelOptRule 类型的变量 rule2，用于存储另一个加载的规则对象
        QuidemTest.getCoreRule("EXPAND_JOIN_DISJUNCTION_LOCAL"); // 通过规则名称 "EXPAND_JOIN_DISJUNCTION_LOCAL" 加载对应的优化规则，这是一个用于展开 Join 节点上局部析取表达式的规则
    RelOptRule expected2 = CoreRules.EXPAND_JOIN_DISJUNCTION_LOCAL; // 获取 CoreRules 类中定义的预期规则对象，作为比较的基准
    assertEquals(rule2, expected2); // 断言加载的规则对象 rule2 与预期的规则对象 expected2 相等，验证加载的正确性

    // Same rule type with different Configs should not be equal.
    // 相同的规则类型但具有不同的配置（Config）应该不相等
    assertNotEquals(rule1, rule2); // 断言 rule1 和 rule2 不相等，因为虽然它们都是展开析取表达式的规则，但是作用在不同的节点类型（Filter 和 Join）上，配置不同
  }

  @Test void testLoadIncludeSubclassesRule() { // 测试方法：测试加载包含子类的规则
    RelOptRule rule1 = // 声明 RelOptRule 类型的变量 rule1，用于存储加载的规则对象
        QuidemTest.getCoreRule("FILTER_REDUCE_EXPRESSIONS"); // 通过规则名称 "FILTER_REDUCE_EXPRESSIONS" 加载对应的优化规则，这是一个用于简化 Filter 节点中表达式的规则
    RelOptRule expected1 = CoreRules.FILTER_REDUCE_EXPRESSIONS; // 获取 CoreRules 类中定义的预期规则对象，作为比较的基准
    assertEquals(rule1, expected1); // 断言加载的规则对象 rule1 与预期的规则对象 expected1 相等，验证加载的正确性

    RelOptRule rule2 = // 声明 RelOptRule 类型的变量 rule2，用于存储另一个加载的规则对象
        QuidemTest.getCoreRule("PROJECT_REDUCE_EXPRESSIONS"); // 通过规则名称 "PROJECT_REDUCE_EXPRESSIONS" 加载对应的优化规则，这是一个用于简化 Project 节点中表达式的规则
    RelOptRule expected2 = CoreRules.PROJECT_REDUCE_EXPRESSIONS; // 获取 CoreRules 类中定义的预期规则对象，作为比较的基准
    assertEquals(rule2, expected2); // 断言加载的规则对象 rule2 与预期的规则对象 expected2 相等，验证加载的正确性
  }
} // 类定义结束
