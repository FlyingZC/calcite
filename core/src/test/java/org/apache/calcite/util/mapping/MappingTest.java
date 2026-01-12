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
// Apache许可证头文件,声明代码版权和授权信息
package org.apache.calcite.util.mapping; // 声明包名,该类位于org.apache.calcite.util.mapping包下,用于测试映射工具类

import com.google.common.collect.ImmutableMap; // 导入Google Guava的不可变Map类,用于创建不可变的映射集合

import org.hamcrest.FeatureMatcher; // 导入Hamcrest测试框架的特征匹配器,用于匹配对象的特定特征
import org.hamcrest.Matcher; // 导入Hamcrest测试框架的匹配器接口
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解,用于标记测试方法

import java.util.Arrays; // 导入Java数组工具类,用于数组操作
import java.util.Collections; // 导入Java集合工具类,用于创建不可变集合
import java.util.List; // 导入Java列表接口,用于有序集合操作

import static org.hamcrest.CoreMatchers.equalTo; // 导入Hamcrest的equalTo匹配器,用于验证两个值相等
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest的is匹配器,用于验证条件是否为真
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的断言方法,用于验证测试结果
import static org.hamcrest.Matchers.hasToString; // 导入Hamcrest的hasToString匹配器,用于验证对象的toString输出
import static org.junit.jupiter.api.Assertions.assertFalse; // 导入JUnit5的assertFalse断言方法,用于验证条件为假
import static org.junit.jupiter.api.Assertions.assertThrows; // 导入JUnit5的assertThrows断言方法,用于验证抛出指定异常
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit5的assertTrue断言方法,用于验证条件为真

/**
 * Unit test for mappings.
 * 映射工具类的单元测试类
 * 该类用于测试Calcite框架中映射(Mapping)功能的各种操作和行为
 * 映射在查询优化过程中用于表示源位置到目标位置的转换关系,例如字段映射、项目映射等
 * 测试涵盖恒等映射、偏移映射、双射映射、逆映射等多种映射类型的创建和操作
 *
 * @see Mapping // 参见Mapping接口,定义了映射的基本行为
 * @see Mappings // 参见Mappings工具类,提供了创建和操作映射的静态方法
 */
class MappingTest { // MappingTest类,用于测试映射功能的测试类
  @Test void testMappings() { // 测试方法:测试映射的基本功能,包括恒等映射的创建、属性查询和边界条件处理
    assertTrue(Mappings.isIdentity(Mappings.createIdentity(0))); // 验证:创建大小为0的恒等映射并检查它是否为恒等映射,应该返回true
    assertTrue(Mappings.isIdentity(Mappings.createIdentity(5))); // 验证:创建大小为5的恒等映射并检查它是否为恒等映射,应该返回true
    assertFalse( // 验证:创建一个部分满射映射(3个源,4个目标)并检查它是否为恒等映射,应该返回false
        Mappings.isIdentity( // 调用isIdentity方法检查映射是否为恒等映射
            Mappings.create(MappingType.PARTIAL_SURJECTION, 3, 4))); // 创建部分满射映射,源数量为3,目标数量为4
    assertFalse( // 验证:创建一个部分满射映射(3个源,3个目标)并检查它是否为恒等映射,应该返回false
        Mappings.isIdentity( // 调用isIdentity方法检查映射是否为恒等映射
            Mappings.create(MappingType.PARTIAL_SURJECTION, 3, 3))); // 创建部分满射映射,源数量为3,目标数量为3
    assertFalse( // 验证:创建一个部分满射映射(4个源,4个目标)并检查它是否为恒等映射,应该返回false
        Mappings.isIdentity( // 调用isIdentity方法检查映射是否为恒等映射
            Mappings.create(MappingType.PARTIAL_SURJECTION, 4, 4))); // 创建部分满射映射,源数量为4,目标数量为4

    Mapping identity = Mappings.createIdentity(5); // 创建一个大小为5的恒等映射,每个源位置映射到相同的目标位置
    assertThat(identity.getTargetCount(), equalTo(5)); // 验证:恒等映射的目标数量应该等于5
    assertThat(identity.getSourceCount(), equalTo(5)); // 验证:恒等映射的源数量应该等于5
    assertThat(identity.getTarget(0), equalTo(0)); // 验证:源位置0应该映射到目标位置0(恒等映射特性)
    assertThat(identity.getTarget(1), equalTo(1)); // 验证:源位置1应该映射到目标位置1(恒等映射特性)
    assertThat(identity.getTarget(4), equalTo(4)); // 验证:源位置4应该映射到目标位置4(恒等映射特性)
    assertThat(identity.getSource(0), equalTo(0)); // 验证:目标位置0应该映射回源位置0(恒等映射的逆映射特性)
    assertThat(identity.getSource(1), equalTo(1)); // 验证:目标位置1应该映射回源位置1(恒等映射的逆映射特性)
    assertThat(identity.getSource(4), equalTo(4)); // 验证:目标位置4应该映射回源位置4(恒等映射的逆映射特性)
    assertThat(identity.getTargetOpt(4), equalTo(4)); // 验证:使用可选方法获取目标位置,源4应该映射到目标4
    assertThat(identity.getSourceOpt(4), equalTo(4)); // 验证:使用可选方法获取源位置,目标4应该映射回源4

    assertThrows(IndexOutOfBoundsException.class, () -> identity.getSourceOpt(5)); // 验证:访问超出范围的目标位置5应该抛出索引越界异常
    assertThrows(IndexOutOfBoundsException.class, () -> identity.getSource(5)); // 验证:访问超出范围的目标位置5应该抛出索引越界异常
    assertThrows(IndexOutOfBoundsException.class, () -> identity.getTargetOpt(5)); // 验证:访问超出范围的源位置5应该抛出索引越界异常
    assertThrows(IndexOutOfBoundsException.class, () -> identity.getTarget(5)); // 验证:访问超出范围的源位置5应该抛出索引越界异常
    assertThrows(IndexOutOfBoundsException.class, () -> identity.getSourceOpt(-1)); // 验证:访问负索引-1应该抛出索引越界异常
    assertThrows(IndexOutOfBoundsException.class, () -> identity.getSource(-1)); // 验证:访问负索引-1应该抛出索引越界异常
    assertThrows(IndexOutOfBoundsException.class, () -> identity.getTargetOpt(-1)); // 验证:访问负索引-1应该抛出索引越界异常
    assertThrows(IndexOutOfBoundsException.class, () -> identity.getTarget(-1)); // 验证:访问负索引-1应该抛出索引越界异常

    Mapping infiniteIdentity = Mappings.createIdentity(-1); // 创建一个无限大小的恒等映射(源数量为-1表示无限)
    assertThrows(IndexOutOfBoundsException.class, () -> infiniteIdentity.getTarget(-1)); // 验证:即使是无限映射,访问负索引-1也应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, () -> infiniteIdentity.getSource(-2)); // 验证:即使是无限映射,访问负索引-2也应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, () -> infiniteIdentity.getTargetOpt(-1)); // 验证:即使是无限映射,使用可选方法访问负索引-1也应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, () -> infiniteIdentity.getSourceOpt(-2)); // 验证:即使是无限映射,使用可选方法访问负索引-2也应该抛出异常
    assertThat(infiniteIdentity.getTarget(100), equalTo(100)); // 验证:无限恒等映射可以处理任意大的索引,源100应该映射到目标100
    assertThat(infiniteIdentity.getSource(100), equalTo(100)); // 验证:无限恒等映射可以处理任意大的索引,目标100应该映射回源100
  } // testMappings方法结束

  /**
   * Unit test for {@link Mappings#createShiftMapping}.
   * 测试Mappings.createShiftMapping方法的单元测试
   * 该方法用于创建偏移映射,通过指定源范围和目标偏移量来建立映射关系
   * 常用于在查询重写或优化过程中调整字段或项目的位置
   */
  @Test void testMappingsCreateShiftMapping() { // 测试方法:测试创建偏移映射的功能
    assertThat(Mappings.createShiftMapping(20, 3, 6, 2, 10, 15, 3), // 创建偏移映射:源数量为20,映射对为(3,6,2)和(10,15,3),表示源位置3-4映射到目标6-7,源位置10-12映射到目标15-17
        hasToString("[size=5, sourceCount=20, targetCount=13, " // 验证映射的字符串表示形式
            + "elements=[6:3, 7:4, 15:10, 16:11, 17:12]]")); // 映射元素列表:目标6来自源3,目标7来自源4,目标15来自源10,目标16来自源11,目标17来自源12

    // no triples makes for a mapping with 0 targets, 20 sources, but still
    // valid
    // 没有映射三元组会创建一个有0个目标、20个源的映射,但仍然是有效的
    Mappings.TargetMapping mapping = Mappings.createShiftMapping(20); // 创建一个源数量为20但没有映射关系的偏移映射
    assertThat(mapping, // 验证该映射的属性
        hasToString("[size=0, sourceCount=20, targetCount=0, elements=[]]")); // 映射大小为0,源数量为20,目标数量为0,没有映射元素
    assertThat(mapping.getSourceCount(), is(20)); // 验证映射的源数量为20
    assertThat(mapping.getTargetCount(), is(0)); // 验证映射的目标数量为0
  } // testMappingsCreateShiftMapping方法结束

  /**
   * Unit test for {@link Mappings#append}.
   * 测试Mappings.append方法的单元测试
   * 该方法用于将两个映射连接起来,形成一个新的映射
   * 第一个映射的源范围保持不变,第二个映射的源范围偏移第一个映射的源数量
   * 常用于合并多个步骤的映射结果
   */
  @Test void testMappingsAppend() { // 测试方法:测试映射追加连接的功能
    assertTrue( // 验证:将两个恒等映射连接后仍然是恒等映射
        Mappings.isIdentity( // 检查连接后的映射是否为恒等映射
            Mappings.append( // 连接两个映射
                Mappings.createIdentity(3), // 第一个映射:大小为3的恒等映射
                Mappings.createIdentity(2)))); // 第二个映射:大小为2的恒等映射,连接后源位置3-4映射到目标3-4
    Mapping mapping0 = Mappings.create(MappingType.PARTIAL_SURJECTION, 5, 3); // 创建一个部分满射映射:5个源,3个目标
    mapping0.set(0, 2); // 设置映射:源位置0映射到目标位置2
    mapping0.set(3, 1); // 设置映射:源位置3映射到目标位置1
    mapping0.set(4, 0); // 设置映射:源位置4映射到目标位置0
    assertThat(Mappings.append(mapping0, Mappings.createIdentity(2)), // 将mapping0与大小为2的恒等映射连接
        hasToString("[size=5, sourceCount=7, targetCount=5, elements=[0:2, 3:1, 4:0, 5:3, 6:4]]")); // 验证连接后的映射:源0->目标2,源3->目标1,源4->目标0,源5->目标3,源6->目标4
  } // testMappingsAppend方法结束

  /**
   * Unit test for {@link Mappings#offsetSource}.
   * 测试Mappings.offsetSource方法的单元测试
   * 该方法用于偏移映射的源范围,保持目标范围不变
   * 常用于在查询优化过程中调整源位置,例如在子查询展开或连接重排时
   */
  @Test void testMappingsOffsetSource() { // 测试方法:测试偏移映射源范围的功能
    final Mappings.TargetMapping mapping = // 创建一个目标映射
        Mappings.target(ImmutableMap.of(0, 5, 1, 7), 2, 8); // 使用不可变Map创建映射:源0->目标5,源1->目标7,源数量为2,目标数量为8
    assertThat(mapping, // 验证映射的字符串表示
        hasToString("[size=2, sourceCount=2, targetCount=8, elements=[0:5, 1:7]]")); // 映射大小为2,源数量为2,目标数量为8,包含两个映射元素
    assertThat(mapping.getSourceCount(), is(2)); // 验证映射的源数量为2
    assertThat(mapping.getTargetCount(), is(8)); // 验证映射的目标数量为8

    final Mappings.TargetMapping mapping1 = // 创建偏移后的映射
        Mappings.offsetSource(mapping, 3, 5); // 将原映射的源范围偏移3,新的源数量为5
    assertThat(mapping1, // 验证偏移后映射的字符串表示
        hasToString("[size=2, sourceCount=5, targetCount=8, " // 映射大小为2,源数量为5,目标数量为8
            + "elements=[3:5, 4:7]]")); // 映射元素:源3->目标5,源4->目标7(原源0和1分别偏移了3)
    assertThat(mapping1.getSourceCount(), is(5)); // 验证偏移后映射的源数量为5
    assertThat(mapping1.getTargetCount(), is(8)); // 验证偏移后映射的目标数量仍为8

    // mapping that extends RHS
    // 创建一个扩展右侧(源数量)的映射
    final Mappings.TargetMapping mapping2 = // 创建另一个偏移映射
        Mappings.offsetSource(mapping, 3, 15); // 将原映射的源范围偏移3,新的源数量为15(比原映射大很多)
    assertThat(mapping2, // 验证扩展后映射的字符串表示
        hasToString("[size=2, sourceCount=15, targetCount=8, " // 映射大小为2,源数量为15,目标数量为8
            + "elements=[3:5, 4:7]]")); // 映射元素:源3->目标5,源4->目标7
    assertThat(mapping2.getSourceCount(), is(15)); // 验证扩展后映射的源数量为15
    assertThat(mapping2.getTargetCount(), is(8)); // 验证扩展后映射的目标数量仍为8

    assertThrows(IllegalArgumentException.class, () -> Mappings.offsetSource(mapping, 3, 4)); // 验证:如果新的源数量(4)小于偏移量+原源数量(3+2=5),应该抛出非法参数异常
  } // testMappingsOffsetSource方法结束

  /** Unit test for {@link Mappings#source(List, int)}
   * and its converse, {@link Mappings#asList(Mappings.TargetMapping)}.
   * 测试Mappings.source方法和Mappings.asList方法的单元测试
   * source方法:根据目标位置列表创建映射,表示源位置按顺序映射到指定的目标位置
   * asList方法:将映射转换回目标位置列表,是source方法的逆操作
   * 这两个方法用于在列表表示和映射表示之间转换
   */
  @Test void testSource() { // 测试方法:测试从目标列表创建映射以及将映射转换为列表的功能
    List<Integer> targets = Arrays.asList(3, 1, 4, 5, 8); // 创建目标位置列表:源0->目标3,源1->目标1,源2->目标4,源3->目标5,源4->目标8
    final Mapping mapping = Mappings.source(targets, 10); // 根据目标列表创建映射,源数量为5(列表大小),目标数量为10
    assertThat(mapping.getTarget(0), equalTo(3)); // 验证:源位置0应该映射到目标位置3
    assertThat(mapping.getTarget(1), equalTo(1)); // 验证:源位置1应该映射到目标位置1
    assertThat(mapping.getTarget(2), equalTo(4)); // 验证:源位置2应该映射到目标位置4
    assertThat(mapping.getTargetCount(), equalTo(10)); // 验证:映射的目标数量为10
    assertThat(mapping.getSourceCount(), equalTo(5)); // 验证:映射的源数量为5

    assertThrows(IndexOutOfBoundsException.class, () -> mapping.getTargetOpt(5)); // 验证:访问超出源范围的索引5应该抛出异常(源数量为5,有效索引为0-4)
    assertThrows(IndexOutOfBoundsException.class, () -> mapping.getTargetOpt(10)); // 验证:访问超出源范围的索引10应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, () -> mapping.getTarget(10)); // 验证:访问超出源范围的索引10应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, () -> mapping.getTargetOpt(-1)); // 验证:访问负索引-1应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, () -> mapping.getTarget(-1)); // 验证:访问负索引-1应该抛出异常

    final List<Integer> integers = Mappings.asList(mapping); // 将映射转换为目标位置列表
    assertThat("Mappings.asList" + mapping + ")", integers, equalTo(targets)); // 验证:转换后的列表应该与原始目标列表相同
    assertThat( // 验证:使用非空列表转换方法
        "Mappings.asListNonNull(" + mapping + ")", // 提示信息
        Mappings.asListNonNull(mapping), equalTo(targets)); // 转换后的非空列表应该与原始目标列表相同

    final Mapping inverse = mapping.inverse(); // 创建映射的逆映射
    assertThat(inverse, // 验证逆映射的字符串表示
        hasToString( // 应该包含以下映射元素
            "[size=5, sourceCount=10, targetCount=5, " // 映射大小为5,源数量为10,目标数量为5
                + "elements=[1:1, 3:0, 4:2, 5:3, 8:4]]")); // 逆映射元素:目标1->源1,目标3->源0,目标4->源2,目标5->源3,目标8->源4
  } // testSource方法结束

  /** Unit test for {@link Mappings#target(List, int)}.
   * 测试Mappings.target方法的单元测试
   * 该方法根据源位置列表创建映射,表示指定的源位置按顺序映射到目标位置
   * 与source方法相反,source方法是从源到目标的映射,target方法是从目标到源的映射
   * 常用于表示某些目标位置来自特定的源位置
   */
  @Test void testTarget() { // 测试方法:测试从源列表创建目标映射的功能
    List<Integer> sources = Arrays.asList(3, 1, 4, 5, 8); // 创建源位置列表:目标0来自源3,目标1来自源1,目标2来自源4,目标3来自源5,目标4来自源8
    final Mapping mapping = Mappings.target(sources, 10); // 根据源列表创建映射,目标数量为5(列表大小),源数量为10
    assertThat(mapping.getTarget(3), equalTo(0)); // 验证:源位置3应该映射到目标位置0
    assertThat(mapping.getTarget(1), equalTo(1)); // 验证:源位置1应该映射到目标位置1
    assertThat(mapping.getTarget(4), equalTo(2)); // 验证:源位置4应该映射到目标位置2

    assertThrows(Mappings.NoElementException.class, () -> mapping.getTarget(0)); // 验证:源位置0没有映射到任何目标,应该抛出无元素异常

    assertThrows(IndexOutOfBoundsException.class, () -> mapping.getTargetOpt(10)); // 验证:访问超出范围的索引10应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, () -> mapping.getTarget(10)); // 验证:访问超出范围的索引10应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, () -> mapping.getTargetOpt(-1)); // 验证:访问负索引-1应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, () -> mapping.getTarget(-1)); // 验证:访问负索引-1应该抛出异常

    assertThat(mapping.getTargetCount(), equalTo(5)); // 验证:映射的目标数量为5
    assertThat(mapping.getSourceCount(), equalTo(10)); // 验证:映射的源数量为10

    final List<Integer> integers = Mappings.asList(mapping); // 将映射转换为列表表示
    assertThat(integers, // 验证列表内容
        equalTo(Arrays.asList(null, 1, null, 0, 2, 3, null, null, 4, null))); // 列表表示:索引0->null(无映射),索引1->1,索引2->null,索引3->0,索引4->2,索引5->3,索引6->null,索引7->null,索引8->4,索引9->null

    // Note: exception is thrown on list.get, so it is needed to trigger the exception
    // 注意:异常是在list.get时抛出的,所以需要访问元素来触发异常
    IllegalArgumentException exception = // 捕获非法参数异常
        assertThrows(IllegalArgumentException.class, () -> // 验证会抛出非法参数异常
            Mappings.asListNonNull(mapping).get(0)); // 调用非空列表转换方法并访问索引0,该位置没有映射
    assertThat(exception.getMessage(), // 验证异常消息
        equalTo("Element 0 is not found in mapping [size=5, sourceCount=10, targetCount=5" // 异常消息应该指出元素0在映射中未找到
            + ", elements=[1:1, 3:0, 4:2, 5:3, 8:4]]")); // 完整的异常消息包含映射的详细信息
  } // testTarget方法结束

  /** Returns a Matcher that checks {@link Mapping#size()}.
   * 返回一个匹配器,用于检查映射的大小
   * 这是一个辅助方法,用于创建可以验证映射大小的Hamcrest匹配器
   * 使用FeatureMatcher来提取映射的size特征并进行匹配
   */
  private static Matcher<Mapping> hasSize(Matcher<Integer> matcher) { // 私有静态方法:返回一个映射大小的匹配器
    return new FeatureMatcher<Mapping, Integer>(matcher, "Mapping", "size") { // 创建特征匹配器,用于匹配Mapping对象的size特征
      @Override protected Integer featureValueOf(Mapping actual) { // 重写方法:从实际的Mapping对象中提取size特征值
        return actual.size(); // 返回映射的大小(映射元素的数量)
      } // featureValueOf方法结束
    }; // 匿名内部类结束
  } // hasSize方法结束

  /** Unit test for {@link Mappings#bijection(List)}.
   * 测试Mappings.bijection方法的单元测试
   * 该方法创建一个双射(一一对应)映射,即每个源位置唯一映射到一个目标位置,且每个目标位置也唯一对应一个源位置
   * 双射映射是可逆的,常用于需要双向映射的场景,例如字段重命名、列映射等
   * 双射要求目标列表中的值必须是一个有效的排列(0到n-1的每个值恰好出现一次)
   */
  @Test void testBijection() { // 测试方法:测试创建双射映射的功能
    List<Integer> targets = Arrays.asList(3, 0, 1, 2); // 创建目标位置列表,构成一个排列[3,0,1,2]
    final Mapping mapping = Mappings.bijection(targets); // 根据目标列表创建双射映射
    assertThat(mapping, hasSize(is(4))); // 验证:映射的大小应该为4
    assertThat(mapping.getTarget(0), equalTo(3)); // 验证:源位置0应该映射到目标位置3
    assertThat(mapping.getTarget(1), equalTo(0)); // 验证:源位置1应该映射到目标位置0
    assertThat(mapping.getTarget(2), equalTo(1)); // 验证:源位置2应该映射到目标位置1
    assertThat(mapping.getTarget(3), equalTo(2)); // 验证:源位置3应该映射到目标位置2
    assertThat(mapping.getTargetOpt(3), equalTo(2)); // 验证:使用可选方法获取目标,源3应该映射到目标2
    assertThat(mapping.getSource(3), equalTo(0)); // 验证:目标位置3应该映射回源位置0(双射的可逆性)
    assertThat(mapping.getSourceOpt(3), equalTo(0)); // 验证:使用可选方法获取源,目标3应该映射回源0

    assertThrows(IndexOutOfBoundsException.class, () -> mapping.getSourceOpt(4)); // 验证:访问超出范围的索引4应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, () -> mapping.getSource(4)); // 验证:访问超出范围的索引4应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, () -> mapping.getTargetOpt(4)); // 验证:访问超出范围的索引4应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, () -> mapping.getTarget(4)); // 验证:访问超出范围的索引4应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, () -> mapping.getSourceOpt(-1)); // 验证:访问负索引-1应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, () -> mapping.getSource(-1)); // 验证:访问负索引-1应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, () -> mapping.getTargetOpt(-1)); // 验证:访问负索引-1应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, () -> mapping.getTarget(-1)); // 验证:访问负索引-1应该抛出异常

    assertThat(mapping.getTargetCount(), equalTo(4)); // 验证:映射的目标数量为4
    assertThat(mapping.getSourceCount(), equalTo(4)); // 验证:映射的源数量为4
    assertThat(mapping, hasToString("[3, 0, 1, 2]")); // 验证:映射的字符串表示应该为[3, 0, 1, 2]
    assertThat(mapping.inverse(), hasToString("[1, 2, 3, 0]")); // 验证:逆映射的字符串表示应该为[1, 2, 3, 0](原映射的逆排列)

    // empty is OK
    // 空列表是有效的,可以创建空的双射映射
    final Mapping empty = Mappings.bijection(Collections.emptyList()); // 创建空的双射映射
    assertThat(empty, hasSize(is(0))); // 验证:空映射的大小应该为0
    assertThat(empty.iterator().hasNext(), equalTo(false)); // 验证:空映射的迭代器应该没有下一个元素
    assertThat(empty, hasToString("[]")); // 验证:空映射的字符串表示应该为[]

    assertThrows(Exception.class, () -> Mappings.bijection(Arrays.asList(0, 5, 1)), // 验证:目标值5超出范围(应该是0-2),应该抛出异常
        "target out of range"); // 异常提示:目标超出范围
    assertThrows(Exception.class, () -> Mappings.bijection(Arrays.asList(1, 0, 1)), // 验证:值1出现两次,不是有效的排列,应该抛出异常
        "more than one permutation element maps to position 1"); // 异常提示:多个排列元素映射到位置1
  } // testBijection方法结束
} // MappingTest类结束