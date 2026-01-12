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
// Apache许可证头，声明代码版权和使用许可
package org.apache.calcite.plan;  // 声明包名，属于org.apache.calcite.plan包，该包包含Calcite查询优化器的核心规划相关类

import org.apache.calcite.adapter.enumerable.EnumerableConvention;  // 导入EnumerableConvention类，表示可枚举的调用约定，用于生成可执行的Java代码
import org.apache.calcite.rel.RelCollation;  // 导入RelCollation类，表示关系代数表达式的排序规则（collation trait）
import org.apache.calcite.rel.RelCollationTraitDef;  // 导入RelCollationTraitDef类，定义排序（collation）特征的元数据和工厂方法
import org.apache.calcite.rel.RelCollations;  // 导入RelCollations类，提供创建RelCollation实例的静态工厂方法

import com.google.common.collect.ImmutableList;  // 导入Google Guava库的ImmutableList类，提供不可变列表实现

import org.junit.jupiter.api.Test;  // 导入JUnit 5的Test注解，用于标记测试方法

import java.util.List;  // 导入Java标准库的List接口
import java.util.function.Supplier;  // 导入Java标准库的Supplier函数式接口，用于延迟计算或提供值

import static org.hamcrest.CoreMatchers.is;  // 导入Hamcrest断言库的is匹配器，用于值相等判断
import static org.hamcrest.MatcherAssert.assertThat;  // 导入Hamcrest断言库的assertThat方法，用于灵活的断言
import static org.hamcrest.Matchers.hasSize;  // 导入Hamcrest断言库的hasSize匹配器，用于检查集合大小
import static org.junit.jupiter.api.Assertions.assertFalse;  // 导入JUnit 5的assertFalse断言方法，用于验证条件为false
import static org.junit.jupiter.api.Assertions.assertNotEquals;  // 导入JUnit 5的assertNotEquals断言方法，用于验证两个值不相等
import static org.junit.jupiter.api.Assertions.assertTrue;  // 导入JUnit 5的assertTrue断言方法，用于验证条件为true

import static java.lang.Integer.toHexString;  // 导入Integer的toHexString静态方法，用于将整数转换为十六进制字符串
import static java.lang.System.identityHashCode;  // 导入System的identityHashCode静态方法，用于获取对象的默认哈希码

/**
 * 测试类，用于验证 {@link RelCompositeTrait} 和 {@link RelTraitSet} 的功能
 * RelCompositeTrait: 复合特征类，用于表示由多个子特征组成的复合特征（如多个排序规则的组合）
 * RelTraitSet: 特征集类，用于存储关系代数节点的一组特征（如调用约定、排序规则、分布特征等）
 * 该测试类主要验证以下核心功能：
 * 1. RelCompositeTrait的规范化（canonical）行为，即相同的输入应该返回相同的实例（享元模式）
 * 2. RelTraitSet的默认状态判断、替换操作、相等性判断等核心方法
 */
class RelTraitTest {  // RelTraitTest测试类定义，使用JUnit 5框架进行单元测试
  // 静态常量：Collation（排序）特征定义的实例，用于创建和操作排序相关的特征
  // RelCollationTraitDef是排序特征的元数据定义，INSTANCE是单例模式的全局唯一实例
  // 在查询优化中，排序特征决定了结果的排序顺序，对JOIN操作的性能优化至关重要
  private static final RelCollationTraitDef COLLATION = RelCollationTraitDef.INSTANCE;

  // 私有辅助方法：断言RelCompositeTrait的规范化行为（canonical behavior）
  // 该方法验证对于相同的输入，RelCompositeTrait.of()应该返回相同的实例（享元模式）
  // 参数说明：
  //   - message: 断言消息，用于标识测试场景
  //   - collation: Supplier函数式接口，延迟提供RelCollation列表，避免不必要的计算
  // 该方法的核心作用是确保RelCompositeTrait实现了对象的复用，避免内存浪费
  private void assertCanonical(String message, Supplier<List<RelCollation>> collation) {
    // 第一次调用RelCompositeTrait.of()创建复合特征，传入排序特征定义和排序列表
    // RelCompositeTrait.of()会根据输入的collation列表创建或返回已存在的复合特征实例
    RelTrait trait1 = RelCompositeTrait.of(COLLATION, collation.get());
    // 第二次调用RelCompositeTrait.of()，使用完全相同的参数创建复合特征
    // 如果RelCompositeTrait实现了享元模式，应该返回与trait1相同的对象实例
    RelTrait trait2 = RelCompositeTrait.of(COLLATION, collation.get());

    // 使用Hamcrest断言库验证trait1和trait2应该是同一个对象实例
    // 断言消息说明：RelCompositeTrait.of对于相同的输入应该返回相同的实例
    // 比较内容包括：对象的字符串表示和对象的身份哈希码（内存地址）
    // 如果断言失败，说明RelCompositeTrait没有正确实现享元模式
    assertThat("RelCompositeTrait.of should return the same instance for "  // 断言消息第一部分
            + message,  // 断言消息第二部分，拼接测试场景描述
        trait1 + " @" + toHexString(identityHashCode(trait1)),  // 期望值：trait1的字符串表示和十六进制哈希码
        is(trait2 + " @" + toHexString(identityHashCode(trait2))));  // 实际值：trait2的字符串表示和十六进制哈希码
  }

  // 测试方法：验证空的复合特征（empty composite）的规范化行为
  // 使用@Test注解标记为JUnit测试方法
  // 该测试验证当创建一个不包含任何排序规则的复合特征时，RelCompositeTrait.of()返回的实例应该是规范化的（即相同的实例）
  @Test void compositeEmpty() {
    // 调用assertCanonical辅助方法，传入测试消息和空列表的Supplier
    // ImmutableList::of是方法引用，创建一个空的不可变列表
    // 预期结果：两次调用RelCompositeTrait.of()应该返回同一个对象实例
    assertCanonical("empty composite", ImmutableList::of);  // 验证空复合特征的规范化
  }

  // 测试方法：验证包含一个元素的复合特征（composite with one element）的规范化行为
  // 该测试验证当创建一个包含单个排序规则的复合特征时，RelCompositeTrait.of()返回的实例应该是规范化的
  // 单个元素的排序规则是一个空的排序（没有指定排序字段）
  @Test void compositeOne() {
    // 调用assertCanonical辅助方法，传入测试消息和包含单个元素的列表Supplier
    // Lambda表达式创建一个包含一个RelCollation的不可变列表
    // RelCollations.of(ImmutableList.of())创建一个空排序（没有排序字段）
    // 预期结果：两次调用RelCompositeTrait.of()应该返回同一个对象实例
    assertCanonical("composite with one element",  // 测试消息：包含一个元素的复合特征
        () -> ImmutableList.of(RelCollations.of(ImmutableList.of())));  // 创建包含一个空排序的列表
  }

  // 测试方法：验证包含两个元素的复合特征（composite with two elements）的规范化行为
  // 该测试验证当创建一个包含两个排序规则的复合特征时，RelCompositeTrait.of()返回的实例应该是规范化的
  // 两个排序规则分别是按索引0排序和按索引1排序
  @Test void compositeTwo() {
    // 调用assertCanonical辅助方法，传入测试消息和包含两个元素的列表Supplier
    // Lambda表达式创建一个包含两个RelCollation的不可变列表
    // RelCollations.of(0)创建按索引0排序的规则（即第一个字段升序）
    // RelCollations.of(1)创建按索引1排序的规则（即第二个字段升序）
    // 预期结果：两次调用RelCompositeTrait.of()应该返回同一个对象实例
    assertCanonical("composite with two elements",  // 测试消息：包含两个元素的复合特征
        () -> ImmutableList.of(RelCollations.of(0), RelCollations.of(1)));  // 创建包含两个排序规则的列表
  }

  // 测试方法：测试RelTraitSet的默认状态（default）相关功能
  // 该测试全面验证RelTraitSet的默认状态判断、特征替换、获取默认特征集等核心功能
  // 测试覆盖了以下场景：
  // 1. 创建空特征集并添加默认特征
  // 2. 判断特征集是否为默认状态
  // 3. 替换特征后验证状态变化
  // 4. 获取默认特征集和忽略约定的默认特征集
  @Test void testTraitSetDefault() {
    // 创建一个空的RelTraitSet实例，初始时不包含任何特征
    RelTraitSet traits = RelTraitSet.createEmpty();  // 创建空特征集
    // 使用plus方法向特征集添加两个特征：
    // 1. Convention.NONE: 表示无调用约定（NONE约定）
    // 2. RelCollations.EMPTY: 表示空排序（无排序规则）
    // plus方法返回一个新的特征集实例（不可变对象模式）
    traits = traits.plus(Convention.NONE).plus(RelCollations.EMPTY);  // 添加NONE约定和空排序
    // 验证特征集的大小是否为2（即包含两个特征）
    assertThat(traits, hasSize(2));  // 断言特征集包含2个特征
    // 验证特征集是否处于默认状态（isDefault方法检查所有特征是否都是默认值）
    // 默认状态的特征集包含Convention.NONE和RelCollations.EMPTY
    assertTrue(traits.isDefault());  // 断言特征集是默认状态
    // 使用replace方法将Convention.NONE替换为EnumerableConvention.INSTANCE
    // EnumerableConvention表示可枚举的调用约定，用于生成可执行的Java代码
    traits = traits.replace(EnumerableConvention.INSTANCE);  // 替换为ENUMERABLE约定
    // 验证特征集是否不再是默认状态（因为约定已从NONE改为ENUMERABLE）
    assertFalse(traits.isDefault());  // 断言特征集不是默认状态
    // 验证特征集在忽略约定的情况下是否为默认状态（isDefaultSansConvention方法）
    // 即除了约定之外，其他特征（如排序）都是默认值
    assertTrue(traits.isDefaultSansConvention());  // 断言忽略约定时是默认状态
    // 使用replace方法将空排序替换为按索引0排序的规则
    traits = traits.replace(RelCollations.of(0));  // 替换为按索引0排序
    // 验证特征集不是默认状态（因为排序已从EMPTY改为按索引0排序）
    assertFalse(traits.isDefault());  // 断言特征集不是默认状态
    // 验证将约定替换回NONE后，特征集在忽略约定的情况下是否为默认状态
    // 预期为false，因为排序规则不是默认的EMPTY
    assertFalse(traits.replace(Convention.NONE).isDefaultSansConvention());  // 断言替换回NONE约定后，忽略约定时不是默认状态
    // 验证调用getDefault()方法返回的特征集是否为默认状态
    // getDefault()返回一个完全默认的特征集（包含所有默认特征）
    assertTrue(traits.getDefault().isDefault());  // 断言获取的默认特征集是默认状态
    // 调用getDefaultSansConvention()方法，获取忽略约定的默认特征集
    // 该方法返回一个包含当前约定但其他特征都是默认值的特征集
    traits = traits.getDefaultSansConvention();  // 获取忽略约定的默认特征集
    // 验证特征集不是完全默认状态（因为约定是ENUMERABLE而不是NONE）
    assertFalse(traits.isDefault());  // 断言特征集不是完全默认状态
    // 验证特征集的约定是否为ENUMERABLE（getConvention方法返回特征集中的约定特征）
    assertThat(EnumerableConvention.INSTANCE, is(traits.getConvention()));  // 断言约定是ENUMERABLE
    // 验证特征集在忽略约定的情况下是否为默认状态（因为排序是EMPTY）
    assertTrue(traits.isDefaultSansConvention());  // 断言忽略约定时是默认状态
    // 验证特征集的字符串表示是否为"ENUMERABLE.[]"
    // 该格式表示：约定是ENUMERABLE，排序是空（[]表示无排序字段）
    assertThat("ENUMERABLE.[]", is(traits.toString()));  // 断言字符串表示为"ENUMERABLE.[]"
  }

  // 测试方法：测试RelTraitSet的相等性（equal）相关功能
  // 该测试验证RelTraitSet的相等性判断和忽略约定的相等性判断
  // 测试覆盖了以下场景：
  // 1. 创建不同的特征集并验证它们不相等
  // 2. 验证忽略约定的相等性判断
  // 3. 验证不同排序规则的特征集在忽略约定时也不相等
  @Test void testTraitSetEqual() {
    // 创建一个空的RelTraitSet实例
    RelTraitSet traits = RelTraitSet.createEmpty();  // 创建空特征集
    // 创建特征集traits1，包含Convention.NONE和按索引0排序的规则
    // 该特征集包含两个特征：约定和排序
    RelTraitSet traits1 = traits.plus(Convention.NONE).plus(RelCollations.of(0));  // 创建包含NONE约定和按索引0排序的特征集
    // 验证特征集traits1的大小是否为2
    assertThat(traits1, hasSize(2));  // 断言特征集traits1包含2个特征
    // 创建特征集traits2，通过替换traits1的约定为EnumerableConvention.INSTANCE
    // traits2包含EnumerableConvention.INSTANCE和按索引0排序的规则
    // 注意：排序规则保持不变（仍然是按索引0排序）
    RelTraitSet traits2 = traits1.replace(EnumerableConvention.INSTANCE);  // 创建包含ENUMERABLE约定和按索引0排序的特征集
    // 验证特征集traits2的大小是否为2
    assertThat(traits2, hasSize(2));  // 断言特征集traits2包含2个特征
    // 验证traits1和traits2不相等（因为约定不同：NONE vs ENUMERABLE）
    assertNotEquals(traits1, traits2);  // 断言两个特征集不相等
    // 验证traits1和traits2在忽略约定的情况下是否相等
    // equalsSansConvention方法只比较除约定之外的特征（如排序）
    // 预期为true，因为两者的排序规则都是按索引0排序
    assertTrue(traits1.equalsSansConvention(traits2));  // 断言忽略约定时两个特征集相等
    // 创建特征集traits3，通过替换traits2的排序规则为按索引1排序
    // traits3包含EnumerableConvention.INSTANCE和按索引1排序的规则
    // 注意：约定保持不变（仍然是ENUMERABLE）
    RelTraitSet traits3 = traits2.replace(RelCollations.of(1));  // 创建包含ENUMERABLE约定和按索引1排序的特征集
    // 验证traits3和traits2在忽略约定的情况下是否不相等
    // 预期为false，因为两者的排序规则不同（按索引0排序 vs 按索引1排序）
    assertFalse(traits3.equalsSansConvention(traits2));  // 断言忽略约定时两个特征集不相等
  }
}
