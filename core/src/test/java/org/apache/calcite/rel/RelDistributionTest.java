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
package org.apache.calcite.rel; // 声明包名，该类属于org.apache.calcite.rel包，是Calcite关系表达式核心包

import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系表达式的特征集合，如排序、分布等物理属性
import org.apache.calcite.util.ImmutableIntList; // 导入ImmutableIntList类，用于创建不可变的整数列表，常用于存储分区键索引
import org.apache.calcite.util.mapping.Mapping; // 导入Mapping接口，表示源和目标之间的映射关系，用于字段映射和转换
import org.apache.calcite.util.mapping.Mappings; // 导入Mappings工具类，提供创建各种映射的静态工厂方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList，用于创建不可变的列表

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.util.Arrays; // 导入Java标准库的Arrays工具类，用于数组操作

import static org.apache.calcite.rel.RelDistributions.ANY; // 静态导入RelDistributions.ANY，表示任意分布类型，即数据可以分布在任意节点上

import static org.hamcrest.CoreMatchers.is; // 静态导入Hamcrest的is匹配器，用于断言验证
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入Hamcrest的assertThat方法，用于编写可读性强的断言

/**
 * Tests for {@link RelDistribution}. // 类注释：这是RelDistribution类的单元测试类
 * RelDistribution表示关系表达式的数据分布方式，包括哈希分布、随机分布、广播分布、单点分布等
 * 测试内容包括：分布特征的满足关系检查、分布键的映射转换、不同分布类型的创建和比较
 * 该测试类验证了Calcite中数据分布机制的正确性，特别是在分布式查询优化中的应用
 */
class RelDistributionTest { // 定义RelDistributionTest测试类，使用JUnit 5的默认构造函数
  @Test void testRelDistributionSatisfy() { // 测试方法：验证RelDistribution的满足关系和比较功能，测试复合分布特征是否能满足简单分布特征
    RelDistribution distribution1 = RelDistributions.hash(ImmutableList.of(0)); // 创建第一个哈希分布对象，使用第0列作为分布键，表示数据按第0列的哈希值分布
    RelDistribution distribution2 = RelDistributions.hash(ImmutableList.of(1)); // 创建第二个哈希分布对象，使用第1列作为分布键，表示数据按第1列的哈希值分布

    RelTraitSet traitSet = RelTraitSet.createEmpty(); // 创建一个空的RelTraitSet对象，用于存储关系表达式的特征集合
    RelTraitSet simpleTrait1 = traitSet.plus(distribution1); // 将第一个分布特征添加到特征集合中，创建包含distribution1的简单特征集
    RelTraitSet simpleTrait2 = traitSet.plus(distribution2); // 将第二个分布特征添加到特征集合中，创建包含distribution2的简单特征集
    RelTraitSet compositeTrait = // 创建复合特征集，将RelDistribution特征定义替换为包含两个分布特征的列表
        traitSet.replace(RelDistributionTraitDef.INSTANCE, // 使用RelDistributionTraitDef实例作为特征定义
            ImmutableList.of(distribution1, distribution2)); // 创建包含distribution1和distribution2的不可变列表

    assertThat(compositeTrait.satisfies(simpleTrait1), is(true)); // 断言：复合特征集应该满足第一个简单特征集，因为复合特征包含distribution1
    assertThat(compositeTrait.satisfies(simpleTrait2), is(true)); // 断言：复合特征集应该满足第二个简单特征集，因为复合特征包含distribution2

    assertThat(distribution1.compareTo(distribution2), is(-1)); // 断言：比较两个分布对象，distribution1应该小于distribution2，因为分布键0小于1
    assertThat(distribution2.compareTo(distribution1), is(1)); // 断言：比较两个分布对象，distribution2应该大于distribution1，因为分布键1大于0
    //noinspection EqualsWithItself // 抑制警告：与自身比较是合法的测试场景
    assertThat(distribution2.compareTo(distribution2), is(0)); // 断言：distribution2与自身比较应该返回0，表示相等
  }

  @Test void testRelDistributionMapping() { // 测试方法：验证RelDistribution在字段映射下的转换行为，测试分布键如何根据映射关系进行转换
    final int n = 10; // Mapping source count. // 定义源字段的数量为10，表示映射的源空间包含10个字段

    // hash[0] // 注释：测试使用第0列作为哈希分布键的情况
    RelDistribution hash0 = hash(0); // 创建哈希分布对象，使用第0列作为分布键
    assertThat(hash0.apply(mapping(n, 0)), is(hash0)); // 断言：应用恒等映射（0->0），分布应该保持不变，仍然是hash[0]
    assertThat(hash0.apply(mapping(n, 1)), is(ANY)); // 断言：应用映射（0->1），但源字段0不在映射的源列表中，因此返回ANY（任意分布）
    assertThat(hash0.apply(mapping(n, 2, 1, 0)), is(hash(2))); // 断言：应用映射（0->2, 1->1, 2->0），源字段0映射到目标字段2，因此分布变为hash[2]

    // hash[0,1] // 注释：测试使用第0列和第1列作为哈希分布键的情况
    RelDistribution hash01 = hash(0, 1); // 创建哈希分布对象，使用第0列和第1列作为分布键
    assertThat(hash01.apply(mapping(n, 0)), is(ANY)); // 断言：应用映射（0->0），但源字段1不在映射中，因此返回ANY
    assertThat(hash01.apply(mapping(n, 1)), is(ANY)); // 断言：应用映射（1->1），但源字段0不在映射中，因此返回ANY
    assertThat(hash01.apply(mapping(n, 0, 1)), is(hash01)); // 断言：应用恒等映射（0->0, 1->1），分布应该保持不变，仍然是hash[0,1]
    assertThat(hash01.apply(mapping(n, 1, 2)), is(ANY)); // 断言：应用映射（1->1, 2->2），但源字段0不在映射中，因此返回ANY
    assertThat(hash01.apply(mapping(n, 1, 0)), is(hash01)); // 断言：应用交换映射（0->1, 1->0），分布键顺序交换后仍然是hash[0,1]（因为哈希分布键的顺序不影响分布）
    assertThat(hash01.apply(mapping(n, 2, 1, 0)), is(hash(2, 1))); // 断言：应用映射（0->2, 1->1, 2->0），源字段0映射到2，源字段1映射到1，因此分布变为hash[2,1]

    // hash[2] // 注释：测试使用第2列作为哈希分布键的情况
    RelDistribution hash2 = hash(2); // 创建哈希分布对象，使用第2列作为分布键
    assertThat(hash2.apply(mapping(n, 0)), is(ANY)); // 断言：应用映射（0->0），但源字段2不在映射中，因此返回ANY
    assertThat(hash2.apply(mapping(n, 1)), is(ANY)); // 断言：应用映射（1->1），但源字段2不在映射中，因此返回ANY
    assertThat(hash2.apply(mapping(n, 2)), is(hash(0))); // 断言：应用映射（2->0），源字段2映射到目标字段0，因此分布变为hash[0]
    assertThat(hash2.apply(mapping(n, 1, 2)), is(hash(1))); // 断言：应用映射（1->1, 2->2），源字段2映射到目标字段2，但映射的目标列表是[1,2]，因此分布变为hash[1]

    // hash[9] , 9 < mapping.sourceCount() // 注释：测试使用第9列作为哈希分布键的情况，9小于源字段数量10
    RelDistribution hash9 = hash(n - 1); // 创建哈希分布对象，使用第9列（n-1）作为分布键
    assertThat(hash9.apply(mapping(n, 0)), is(ANY)); // 断言：应用映射（0->0），但源字段9不在映射中，因此返回ANY
    assertThat(hash9.apply(mapping(n, 1)), is(ANY)); // 断言：应用映射（1->1），但源字段9不在映射中，因此返回ANY
    assertThat(hash9.apply(mapping(n, 2)), is(ANY)); // 断言：应用映射（2->2），但源字段9不在映射中，因此返回ANY
    assertThat(hash9.apply(mapping(n, n - 1)), is(hash(0))); // 断言：应用映射（9->0），源字段9映射到目标字段0，因此分布变为hash[0]
  }

  private static Mapping mapping(int sourceCount, int... sources) { // 私有静态辅助方法：创建一个目标映射对象，用于字段映射转换
    return Mappings.target(ImmutableIntList.of(sources), sourceCount); // 使用Mappings工具类创建目标映射，sources是目标字段索引列表，sourceCount是源字段总数
  }

  private static RelDistribution hash(int... keys) { // 私有静态辅助方法：创建哈希分布对象，用于简化测试代码
    return RelDistributions.hash(ImmutableIntList.of(keys)); // 使用RelDistributions工厂方法创建哈希分布，keys是分布键索引数组
  }

  @Test void testRangeRelDistributionKeys() { // 测试方法：验证范围分布的创建，范围分布按指定键的范围进行数据分布
    RelDistributions.range(Arrays.asList(0, 1)); // 创建范围分布对象，使用第0列和第1列作为范围分布键
  }

  @Test void testHashRelDistributionKeys() { // 测试方法：验证哈希分布的创建，哈希分布按指定键的哈希值进行数据分布
    RelDistributions.hash(Arrays.asList(0, 1)); // 创建哈希分布对象，使用第0列和第1列作为哈希分布键
  }
}