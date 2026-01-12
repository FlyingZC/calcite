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
 */ // Apache许可证声明，说明代码的版权归属和使用许可条件
package org.apache.calcite.test; // 声明当前类所在的包路径，属于org.apache.calcite.test测试包

import org.apache.calcite.util.Filterator; // 导入Filterator类，这是一个过滤迭代器工具类，用于从迭代器中过滤出特定类型的元素
import org.apache.calcite.util.Util; // 导入Util工具类，提供各种实用方法，包括filter方法用于过滤集合元素

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.util.ArrayList; // 导入ArrayList类，实现了可变大小的数组列表
import java.util.Arrays; // 导入Arrays工具类，提供操作数组的各种静态方法
import java.util.Collection; // 导入Collection接口，是集合框架的根接口
import java.util.HashSet; // 导入HashSet类，实现了基于哈希表的Set集合
import java.util.LinkedList; // 导入LinkedList类，实现了基于链表的List集合
import java.util.List; // 导入List接口，表示有序集合

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest匹配器的is方法，用于断言值相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的断言方法，用于验证测试条件
import static org.hamcrest.Matchers.hasToString; // 导入Hamcrest的hasToString匹配器，用于验证对象的toString输出
import static org.junit.jupiter.api.Assertions.assertFalse; // 导入JUnit的assertFalse断言方法，用于验证条件为false
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit的assertTrue断言方法，用于验证条件为true

/**
 * Unit test for {@link Filterator}. // Filterator类的单元测试类
 */ // 该类用于测试Filterator过滤迭代器的各种功能和行为
class FilteratorTest { // 定义FilteratorTest测试类，用于验证Filterator工具类的正确性
  //~ Methods ---------------------------------------------------------------- // 方法区域分隔符，标记以下为方法的定义

  @Test void testOne() { // 测试方法：测试Filterator的基本功能，包括hasNext和next方法的调用
    final List<String> tomDickHarry = Arrays.asList("tom", "dick", "harry"); // 创建包含三个字符串的列表，用于作为测试数据源
    final Filterator<String> filterator = // 创建Filterator实例，传入字符串列表的迭代器和String.class类型参数
        new Filterator<String>(tomDickHarry.iterator(), String.class); // Filterator会过滤出所有String类型的元素

    // call hasNext twice // 注释说明：连续调用两次hasNext方法，验证多次调用不会影响迭代器状态
    assertTrue(filterator.hasNext()); // 断言迭代器还有下一个元素，应该返回true
    assertTrue(filterator.hasNext()); // 再次断言迭代器还有下一个元素，验证多次调用hasNext不会消耗元素
    assertThat(filterator.next(), is("tom")); // 调用next方法获取第一个元素，断言返回值为"tom"

    // call next without calling hasNext // 注释说明：不调用hasNext直接调用next方法，验证迭代器的健壮性
    assertThat(filterator.next(), is("dick")); // 直接调用next获取第二个元素，断言返回值为"dick"
    assertTrue(filterator.hasNext()); // 断言迭代器还有下一个元素，应该返回true
    assertThat(filterator.next(), is("harry")); // 调用next获取第三个元素，断言返回值为"harry"
    assertFalse(filterator.hasNext()); // 断言迭代器没有更多元素，应该返回false
    assertFalse(filterator.hasNext()); // 再次断言迭代器没有更多元素，验证在末尾多次调用hasNext的行为
  } // testOne方法结束

  @Test void testNulls() { // 测试方法：测试Filterator对null值的处理能力
    // Nulls don't cause an error - but are not emitted, because they // 注释说明：null值不会导致错误，但不会被输出
    // fail the instanceof test. // 原因是null值无法通过instanceof类型检查
    final List<String> tomDickHarry = Arrays.asList("paul", null, "ringo"); // 创建包含null值的字符串列表
    final Filterator<String> filterator = // 创建Filterator实例，传入包含null的列表迭代器
        new Filterator<String>(tomDickHarry.iterator(), String.class); // Filterator会自动跳过null值
    assertThat(filterator.next(), is("paul")); // 获取第一个元素，跳过null，断言返回值为"paul"
    assertThat(filterator.next(), is("ringo")); // 获取第二个元素，跳过null，断言返回值为"ringo"
    assertFalse(filterator.hasNext()); // 断言迭代器没有更多元素，null值已被跳过
  } // testNulls方法结束

  @Test void testSubtypes() { // 测试方法：测试Filterator对子类型元素的过滤能力
    final ArrayList arrayList = new ArrayList(); // 创建ArrayList实例，它是List接口的实现类
    final HashSet hashSet = new HashSet(); // 创建HashSet实例，它是Collection接口的实现类但不是List
    final LinkedList linkedList = new LinkedList(); // 创建LinkedList实例，它是List接口的实现类
    Collection[] collections = { // 创建Collection数组，包含不同类型的集合对象和null值
        null, // 第一个元素为null
        arrayList, // 第二个元素是ArrayList，是List的子类
        hashSet, // 第三个元素是HashSet，不是List的子类，应该被过滤
        linkedList, // 第四个元素是LinkedList，是List的子类
        null, // 第五个元素为null
    }; // 数组初始化完成
    final Filterator<List> filterator = // 创建Filterator实例，过滤出List类型的元素
        new Filterator<List>( // 传入Collection数组的迭代器和List.class作为目标类型
            Arrays.asList(collections).iterator(), // 将数组转换为列表并获取迭代器
            List.class); // Filterator只会返回ArrayList和LinkedList，跳过null和HashSet
    assertTrue(filterator.hasNext()); // 断言迭代器还有下一个元素，应该返回true

    // skips null // 注释说明：跳过第一个null元素
    assertThat(arrayList, is(filterator.next())); // 获取第一个List类型元素，断言返回值为arrayList

    // skips the HashSet // 注释说明：跳过HashSet，因为它不是List类型
    assertThat(linkedList, is(filterator.next())); // 获取第二个List类型元素，断言返回值为linkedList
    assertFalse(filterator.hasNext()); // 断言迭代器没有更多元素，null和HashSet已被跳过
  } // testSubtypes方法结束

  @Test void testBox() { // 测试方法：测试Filterator在装箱类型过滤场景下的应用
    final Number[] numbers = {1, 2, 3.14, 4, null, 6E23}; // 创建Number数组，包含整数、浮点数、null和科学计数法数字
    List<Integer> result = new ArrayList<Integer>(); // 创建ArrayList用于存放过滤后的Integer元素
    for (int i : Util.filter(Arrays.asList(numbers), Integer.class)) { // 使用Util.filter方法过滤Number列表中的Integer类型元素，并遍历
      result.add(i); // 将每个Integer元素添加到结果列表中
    } // for循环结束，此时result列表应该包含[1, 2, 4]
    assertThat(result, hasToString("[1, 2, 4]")); // 断言结果列表的字符串表示为"[1, 2, 4]"，验证只过滤出了整数
  } // testBox方法结束
} // FilteratorTest类结束
