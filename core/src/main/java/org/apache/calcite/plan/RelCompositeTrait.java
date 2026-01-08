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
package org.apache.calcite.plan; // 声明包路径，该类属于org.apache.calcite.plan包，是Calcite查询优化框架的核心包之一

import com.google.common.collect.ImmutableList; // 导入Google Guava库的不可变列表类，用于创建不可修改的列表
import com.google.common.collect.Ordering; // 导入Google Guava库的排序工具类，用于比较和排序操作

import org.checkerframework.checker.nullness.qual.Nullable; // 导入注解，标记可能为null的参数或返回值，用于静态类型检查

import java.util.Arrays; // 导入Java工具类，提供数组操作方法（如排序、比较、哈希等）
import java.util.List; // 导入Java集合接口，表示有序的元素集合

import static java.util.Objects.requireNonNull; // 静态导入Objects类的requireNonNull方法，用于参数校验

/**
 * A trait that consists of a list of traits, all of the same type.
 * 这是一个复合trait（特征），由多个相同类型的trait组成列表
 *
 * <p>It exists so that multiple traits of the same type
 * ({@link org.apache.calcite.plan.RelTraitDef}) can be stored in the same
 * {@link org.apache.calcite.plan.RelTraitSet}.
 * 它的存在是为了让多个相同类型的trait（RelTraitDef定义的trait）能够存储在同一个RelTraitSet（关系表达式trait集合）中
 * 在Calcite中，trait是关系表达式（RelNode）的属性，比如排序、分布、约定等
 * 通常一个RelNode在某个维度上只能有一个trait（如只能有一种排序方式）
 * 但某些情况下，一个RelNode可能同时满足多个trait（如同时满足多种排序方式）
 * 这时就需要RelCompositeTrait来组合这些多个相同类型的trait
 *
 * @param <T> Member trait - 泛型参数T，表示成员trait的类型，必须是RelMultipleTrait的子类
 * RelMultipleTrait表示可以多个同时存在的trait类型
 */
class RelCompositeTrait<T extends RelMultipleTrait> implements RelTrait { // 定义RelCompositeTrait类，实现RelTrait接口，泛型T必须是RelMultipleTrait的子类
  private final RelTraitDef traitDef; // 成员变量：traitDef，存储trait的定义（trait的元数据），如排序trait的定义、分布trait的定义等，final表示不可变
  private final T[] traits; // 成员变量：traits，存储实际的trait数组，数组中的每个元素都是T类型（RelMultipleTrait的子类），final表示不可变

  /** Creates a RelCompositeTrait. */
  // 创建一个RelCompositeTrait实例的构造方法，私有构造方法，外部不能直接调用，必须通过静态工厂方法of()创建
  // Must remain private. Does not copy the array.
  // 必须保持私有，不复制传入的数组（直接使用传入的数组引用，调用者需要保证数组不被修改）
  private RelCompositeTrait(RelTraitDef traitDef, T[] traits) { // 私有构造方法，接收trait定义和trait数组两个参数
    this.traitDef = traitDef; // 将传入的trait定义赋值给成员变量traitDef
    this.traits = requireNonNull(traits, "traits"); // 使用requireNonNull方法检查traits参数是否为null，如果为null则抛出NullPointerException，错误信息为"traits"
    //noinspection unchecked
    // 下面的断言检查traits数组中的元素是否严格有序（按自然顺序排列），确保traits数组是有序的
    assert Ordering.natural() // 使用Ordering.natural()获取自然排序器
        .isStrictlyOrdered(Arrays.asList((Comparable[]) traits)) // 将traits数组转换为List，并检查是否严格有序（每个元素都大于前一个元素）
        : Arrays.toString(traits); // 如果不是严格有序，则抛出断言错误，并输出traits数组的字符串表示
    for (T trait : traits) { // 遍历traits数组中的每个trait元素
      assert trait.getTraitDef() == this.traitDef; // 断言每个trait的trait定义必须与当前复合trait的trait定义相同，确保所有trait都是同一类型
    }
  }

  /** Creates a RelCompositeTrait. The constituent traits are canonized. */
  // 创建一个RelCompositeTrait实例的静态工厂方法，组成trait会被规范化（canonize）
  // 规范化是指将trait转换为其规范形式，确保相同语义的trait使用相同的对象实例
  @SuppressWarnings("unchecked") // 抑制未检查的类型转换警告，因为需要进行泛型数组转换
  static <T extends RelMultipleTrait> RelTrait of(RelTraitDef def, // 静态工厂方法，泛型T必须是RelMultipleTrait的子类，返回RelTrait类型（可能是RelCompositeTrait也可能是单个trait）
      List<T> traitList) { // 参数def是trait定义，traitList是trait列表
    final RelCompositeTrait<T> compositeTrait; // 声明一个变量用于存储最终的复合trait
    if (traitList.isEmpty()) { // 如果trait列表为空
      return def.getDefault(); // 返回该trait定义的默认trait（例如默认的排序方式、默认的分布方式等）
    } else if (traitList.size() == 1) { // 如果trait列表只有一个元素
      return def.canonize(traitList.get(0)); // 返回该单个trait的规范化形式，不需要创建复合trait
    } else { // 如果trait列表有多个元素（需要创建复合trait）
      final RelMultipleTrait[] traits = // 创建一个RelMultipleTrait数组
          traitList.toArray(new RelMultipleTrait[0]); // 将traitList转换为RelMultipleTrait数组
      for (int i = 0; i < traits.length; i++) { // 遍历数组中的每个trait
        traits[i] = (T) def.canonize(traits[i]); // 对每个trait进行规范化（转换为规范形式），并强制转换回类型T
      }
      compositeTrait = new RelCompositeTrait<>(def, (T[]) traits); // 使用规范化的trait数组创建RelCompositeTrait实例
    }
    return def.canonize(compositeTrait); // 返回复合trait的规范化形式，确保相同语义的复合trait使用相同的对象实例
  }

  @Override public RelTraitDef getTraitDef() { // 重写RelTrait接口的getTraitDef方法，获取这个trait的定义
    return traitDef; // 返回成员变量traitDef，即这个trait的定义
  }

  @Override public int hashCode() { // 重写Object类的hashCode方法，用于计算对象的哈希值，用于哈希表（如HashMap）中
    return Arrays.hashCode(traits); // 使用Arrays工具类计算traits数组的哈希值，确保相同的trait数组产生相同的哈希值
  }

  @Override public boolean equals(@Nullable Object obj) { // 重写Object类的equals方法，用于比较两个对象是否相等，@Nullable表示obj参数可能为null
    return this == obj // 如果是同一个对象引用（内存地址相同），直接返回true
        || obj instanceof RelCompositeTrait // 或者obj是RelCompositeTrait类的实例
        && Arrays.equals(traits, ((RelCompositeTrait) obj).traits); // 并且traits数组相等（使用Arrays.equals比较数组内容）
  }

  @Override public String toString() { // 重写Object类的toString方法，返回对象的字符串表示，用于调试和日志输出
    return Arrays.toString(traits); // 返回traits数组的字符串表示（如"[trait1, trait2, trait3]"）
  }

  @Override public boolean satisfies(RelTrait trait) { // 重写RelTrait接口的satisfies方法，检查当前复合trait是否满足给定的trait要求
    for (T t : traits) { // 遍历traits数组中的每个trait元素
      if (t.satisfies(trait)) { // 如果当前trait满足给定的trait要求（调用单个trait的satisfies方法）
        return true; // 返回true，表示复合trait满足要求（只要有一个成员trait满足即可）
      }
    }
    return false; // 如果所有成员trait都不满足给定要求，返回false
  }

  @Override public void register(RelOptPlanner planner) { // 重写RelTrait接口的register方法，向优化器注册这个trait
    // 空方法体，表示不需要向优化器注册任何信息
    // 有些trait需要向优化器注册以便优化器了解如何处理它们，但复合trait不需要特殊注册
  }

  /** Returns an immutable list of the traits in this composite trait. */
  // 返回这个复合trait中所有trait的不可变列表
  public List<T> traitList() { // 公共方法，返回trait列表
    return ImmutableList.copyOf(traits); // 使用Guava的ImmutableList.copyOf创建traits数组的不可变副本并返回，防止外部修改
  }

  /** Returns the {@code i}th trait. */
  // 返回第i个trait（0-based索引）
  public T trait(int i) { // 公共方法，接收索引参数i
    return traits[i]; // 返回traits数组中索引为i的trait元素
  }

  /** Returns the number of traits. */
  // 返回trait的数量
  public int size() { // 公共方法，无参数
    return traits.length; // 返回traits数组的长度，即trait的数量
  }
}
