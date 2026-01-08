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
// Apache许可证头,说明代码的版权和使用许可
package org.apache.calcite.plan; // 定义包路径,该类位于org.apache.calcite.plan包中

import org.apache.calcite.rel.RelCollation; // 导入RelCollation类,表示关系表达式的排序特性
import org.apache.calcite.rel.RelCollationTraitDef; // 导入RelCollationTraitDef类,定义排序特性的元数据
import org.apache.calcite.rel.RelDistribution; // 导入RelDistribution类,表示关系表达式的数据分布特性
import org.apache.calcite.rel.RelDistributionTraitDef; // 导入RelDistributionTraitDef类,定义数据分布特性的元数据
import org.apache.calcite.util.mapping.Mappings; // 导入Mappings工具类,用于处理字段映射关系

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入空值检查注解,用于标记可能为null的返回值

import java.util.AbstractList; // 导入Java抽象列表类,RelTraitSet继承自它以支持列表操作
import java.util.Arrays; // 导入Java数组工具类,用于数组操作
import java.util.HashMap; // 导入Java HashMap类,用于实现缓存
import java.util.List; // 导入Java List接口,用于处理列表数据
import java.util.Map; // 导入Java Map接口,用于键值对存储
import java.util.function.Supplier; // 导入Java Supplier函数式接口,用于延迟计算

import static java.util.Objects.requireNonNull; // 导入Objects静态方法,用于非空检查

/**
 * RelTraitSet represents an ordered set of {@link RelTrait}s.
 */
// RelTraitSet类表示一个有序的RelTrait(关系特性)集合
// RelTrait是关系表达式的物理属性,如约定(Convention)、排序(Collation)、分布(Distribution)等
// 这个类是不可变的,所有修改操作都会返回新的RelTraitSet实例
public final class RelTraitSet extends AbstractList<RelTrait> { // 定义一个最终的公共类,继承自AbstractList<RelTrait>,使其可以像列表一样使用
  private static final RelTrait[] EMPTY_TRAITS = new RelTrait[0]; // 定义一个静态的空RelTrait数组常量,用于初始化空的特性集

  //~ Instance fields --------------------------------------------------------
  // 实例字段区域开始标记

  private final Cache cache; // 缓存对象,用于缓存RelTraitSet实例以避免重复创建相同的特性集,实现享元模式
  private final RelTrait[] traits; // 存储RelTrait对象的数组,按照特定顺序排列,每个位置对应一种特性类型
  private @Nullable String string; // 缓存该RelTraitSet的字符串表示,延迟计算以提高性能,可能为null
  /** Caches the hash code for the traits. */
  private int hash; // 缓存该RelTraitSet的哈希码,默认值为0,用于快速比较和哈希表操作

  //~ Constructors -----------------------------------------------------------
  // 构造方法区域开始标记

  /**
   * Constructs a RelTraitSet with the given set of RelTraits.
   *
   * @param cache  Trait set cache (and indirectly cluster) that this set
   *               belongs to
   * @param traits Traits
   */
  // 构造一个RelTraitSet对象,使用给定的缓存和特性数组
  // 参数cache: 特性集缓存(间接关联到集群),该特性集属于这个缓存
  // 参数traits: RelTrait数组,包含所有特性
  private RelTraitSet(Cache cache, RelTrait[] traits) { // 私有构造方法,防止外部直接创建,确保通过工厂方法创建
    // NOTE: We do not copy the array. It is important that the array is not
    //   shared. However, since this constructor is private, we assume that
    //   the caller has made a copy.
    // 注意:我们不复制数组。重要的是数组不被共享。但是,由于此构造方法是私有的,我们假设调用者已经创建了副本
    this.cache = cache; // 将传入的cache参数赋值给实例变量cache
    this.traits = traits; // 将传入的traits数组直接赋值给实例变量traits,不进行复制以提高性能
  }

  //~ Methods ----------------------------------------------------------------
  // 方法区域开始标记

  /**
   * Creates an empty trait set.
   *
   * <p>It has a new cache, which will be shared by any trait set created from
   * it. Thus each empty trait set is the start of a new ancestral line.
   */
  // 创建一个空的特性集
  // 它有一个新的缓存,该缓存将被从此特性集创建的任何特性集共享
  // 因此每个空特性集都是一个新的祖先线的起点
  public static RelTraitSet createEmpty() { // 公共静态方法,创建并返回一个空的RelTraitSet实例
    return new RelTraitSet(new Cache(), EMPTY_TRAITS); // 创建一个新的Cache对象和空特性数组,构造新的RelTraitSet并返回
  }

  /**
   * Retrieves a RelTrait from the set.
   *
   * @param index 0-based index into ordered RelTraitSet
   * @return the RelTrait
   * @throws ArrayIndexOutOfBoundsException if index greater than or equal to
   *                                        {@link #size()} or less than 0.
   */
  // 从特性集中检索一个RelTrait对象
  // 参数index: 基于0的索引,指向有序RelTraitSet中的位置
  // 返回值: 对应索引位置的RelTrait对象
  // 异常: 如果索引大于等于size()或小于0,抛出ArrayIndexOutOfBoundsException
  public RelTrait getTrait(int index) { // 公共方法,根据索引获取特性
    return traits[index]; // 直接返回traits数组中指定索引位置的RelTrait对象
  }

  /**
   * Retrieves a list of traits from the set.
   *
   * @param index 0-based index into ordered RelTraitSet
   * @return the RelTrait
   * @throws ArrayIndexOutOfBoundsException if index greater than or equal to
   *                                        {@link #size()} or less than 0.
   */
  // 从特性集中检索一个特性列表
  // 参数index: 基于0的索引,指向有序RelTraitSet中的位置
  // 返回值: RelTrait列表,可能是单个元素的列表或复合特性的列表
  // 异常: 如果索引大于等于size()或小于0,抛出ArrayIndexOutOfBoundsException
  public <E extends RelMultipleTrait> List<E> getTraits(int index) { // 泛型方法,E必须是RelMultipleTrait的子类
    final RelTrait trait = traits[index]; // 获取指定索引位置的RelTrait对象
    if (trait instanceof RelCompositeTrait) { // 如果该特性是复合特性(RelCompositeTrait)
      //noinspection unchecked
      return ((RelCompositeTrait<E>) trait).traitList(); // 强制转换为RelCompositeTrait并返回其内部特性列表
    } else { // 如果不是复合特性
      //noinspection unchecked
      return ImmutableList.of((E) trait); // 将单个特性包装成不可变列表返回
    }
  }

  @Override public RelTrait get(int index) { // 重写AbstractList的get方法,使RelTraitSet可以像列表一样通过索引访问
    return getTrait(index); // 委托给getTrait方法实现
  }

  /**
   * Returns whether a given kind of trait is enabled.
   */
  // 返回给定类型的特性是否已启用(即存在于特性集中)
  // 参数traitDef: 要检查的特性定义(RelTraitDef类型)
  // 返回值: 如果该特性在特性集中存在(不为null)则返回true,否则返回false
  public <T extends RelTrait> boolean isEnabled(RelTraitDef<T> traitDef) { // 泛型方法,T必须是RelTrait的子类
    return getTrait(traitDef) != null; // 调用getTrait方法,如果返回值不为null说明特性已启用
  }

  /**
   * Retrieves a RelTrait of the given type from the set.
   *
   * @param traitDef the type of RelTrait to retrieve
   * @return the RelTrait, or null if not found
   */
  // 从特性集中检索指定类型的RelTrait对象
  // 参数traitDef: 要检索的RelTrait类型(通过RelTraitDef指定)
  // 返回值: 找到的RelTrait对象,如果未找到则返回null
  public <T extends RelTrait> @Nullable T getTrait(RelTraitDef<T> traitDef) { // 泛型方法,T必须是RelTrait的子类,返回值可能为null
    int index = findIndex(traitDef); // 调用findIndex方法查找该特性定义在数组中的索引
    if (index >= 0) { // 如果索引大于等于0,说明找到了对应的特性
      //noinspection unchecked
      return (T) getTrait(index); // 强制转换并返回该索引位置的特性
    }

    return null; // 如果未找到,返回null
  }

  /**
   * Retrieves a list of traits of the given type from the set.
   *
   * <p>Only valid for traits that support multiple entries. (E.g. collation.)
   *
   * @param traitDef the type of RelTrait to retrieve
   * @return the RelTrait, or null if not found
   */
  // 从特性集中检索指定类型的特性列表
  // 仅对支持多个条目的特性有效(例如排序collation)
  // 参数traitDef: 要检索的RelTrait类型(通过RelTraitDef指定)
  // 返回值: 找到的RelTrait列表,如果未找到则返回null
  public <T extends RelMultipleTrait> @Nullable List<T> getTraits( // 泛型方法,T必须是RelMultipleTrait的子类,返回值可能为null
      RelTraitDef<T> traitDef) {
    int index = findIndex(traitDef); // 调用findIndex方法查找该特性定义在数组中的索引
    if (index >= 0) { // 如果索引大于等于0,说明找到了对应的特性
      //noinspection unchecked
      return (List<T>) getTraits(index); // 强制转换并返回该索引位置的特性列表
    }

    return null; // 如果未找到,返回null
  }

  /**
   * Replaces an existing RelTrait in the set.
   * Returns a different trait set; does not modify this trait set.
   *
   * @param index 0-based index into ordered RelTraitSet
   * @param trait the new RelTrait
   * @return the old RelTrait at the index
   */
  // 替换特性集中已存在的RelTrait对象
  // 返回一个新的特性集,不修改当前特性集(不可变对象模式)
  // 参数index: 基于0的索引,指向要替换的特性位置
  // 参数trait: 新的RelTrait对象
  // 返回值: 包含替换后特性的新RelTraitSet对象
  public RelTraitSet replace(int index, RelTrait trait) { // 公共方法,在指定索引位置替换特性
    assert traits[index].getTraitDef() == trait.getTraitDef() // 断言:确保原特性和新特性具有相同的特性定义
        : "RelTrait has different RelTraitDef than replacement"; // 如果不同,抛出错误信息

    RelTrait canonizedTrait = canonize(trait); // 调用canonize方法将新特性规范化为标准形式
    if (traits[index] == canonizedTrait) { // 如果规范化后的特性与原特性相同(使用==比较)
      return this; // 返回当前对象本身,不需要创建新对象
    }
    RelTrait[] newTraits = traits.clone(); // 克隆当前的traits数组
    newTraits[index] = canonizedTrait; // 将新数组中指定索引位置的元素替换为规范化后的特性
    return cache.getOrAdd(new RelTraitSet(cache, newTraits)); // 通过缓存获取或添加新的RelTraitSet对象
  }

  /**
   * Returns a trait set consisting of the current set plus a new trait.
   *
   * <p>If the set does not contain a trait of the same {@link RelTraitDef},
   * the trait is ignored, and this trait set is returned.
   *
   * @param trait the new trait
   * @return New set
   * @see #plus(RelTrait)
   */
  // 返回由当前特性集加上新特性组成的特性集
  // 如果特性集中不包含相同RelTraitDef的特性,则忽略该特性,返回当前特性集
  // 参数trait: 要添加的新特性
  // 返回值: 新的特性集
  // 参见: plus(RelTrait)方法
  public RelTraitSet replace( // 公共方法,替换特性
      RelTrait trait) { // 参数:要替换的特性
    // Quick check for common case
    // 快速检查常见情况
    if (containsShallow(traits, trait)) { // 如果traits数组中已经包含该特性(使用==比较)
      return this; // 返回当前对象本身
    }
    final RelTraitDef traitDef = trait.getTraitDef(); // 获取新特性的特性定义
    int index = findIndex(traitDef); // 查找该特性定义在当前数组中的索引
    if (index < 0) { // 如果索引小于0,说明该特性定义不在当前特性集中
      // Trait is not present. Ignore it.
      // 特性不存在,忽略它
      return this; // 返回当前对象本身
    }

    return replace(index, trait); // 调用replace方法,在指定索引位置替换特性
  }

  /** Returns whether an element occurs within an array.
   *
   * <p>Uses {@code ==}, not {@link #equals}. Nulls are allowed. */
  // 返回元素是否出现在数组中
  // 使用==进行比较,而不是equals方法。允许null值
  // 参数ts: 要搜索的数组
  // 参数seek: 要查找的RelTrait对象
  // 返回值: 如果数组中包含该对象(使用==比较)则返回true,否则返回false
  private static <T> boolean containsShallow(T[] ts, RelTrait seek) { // 私有静态泛型方法,进行浅比较
    for (T t : ts) { // 遍历数组中的每个元素
      if (t == seek) { // 如果找到相同的对象引用(使用==比较)
        return true; // 返回true
      }
    }
    return false; // 遍历完未找到,返回false
  }

  /** Replaces the trait(s) of a given type with a list of traits of the same
   * type.
   *
   * <p>The list must not be empty, and all traits must be of the same type.
   */
  // 将给定类型的特性替换为相同类型的特性列表
  // 列表不能为空,且所有特性必须是同一类型
  // 参数traits: 要替换的特性列表,不能为空,所有元素必须属于同一特性类型
  // 返回值: 包含替换后特性的新RelTraitSet对象
  public <T extends RelMultipleTrait> RelTraitSet replace(List<T> traits) { // 泛型方法,T必须是RelMultipleTrait的子类
    assert !traits.isEmpty(); // 断言:特性列表不能为空
    final RelTraitDef def = traits.get(0).getTraitDef(); // 获取列表中第一个特性的特性定义
    return replace(RelCompositeTrait.of(def, traits)); // 调用replace方法,创建复合特性并替换
  }

  /** Replaces the trait(s) of a given type with a list of traits of the same
   * type.
   *
   * <p>The list must not be empty, and all traits must be of the same type.
   */
  // 将给定类型的特性替换为相同类型的特性列表
  // 列表不能为空,且所有特性必须是同一类型
  // 参数def: 特性定义,指定要替换的特性类型
  // 参数traits: 新的特性列表,不能为空,所有元素必须符合指定的特性定义
  // 返回值: 包含替换后特性的新RelTraitSet对象
  public <T extends RelMultipleTrait> RelTraitSet replace(RelTraitDef<T> def, // 泛型方法,T必须是RelMultipleTrait的子类
      List<T> traits) { // 参数:特性定义和特性列表
    return replace(RelCompositeTrait.of(def, traits)); // 调用replace方法,创建复合特性并替换
  }

  /** If a given multiple trait is enabled, replaces it by calling the given
   * function. */
  // 如果给定的多重特性已启用,则通过调用给定的函数来替换它
  // 参数def: 特性定义,指定要替换的特性类型
  // 参数traitSupplier: 特性列表的提供者(Supplier),用于延迟计算新的特性列表
  // 返回值: 包含替换后特性的新RelTraitSet对象,如果特性未启用则返回当前对象
  public <T extends RelMultipleTrait> RelTraitSet replaceIfs(RelTraitDef<T> def, // 泛型方法,T必须是RelMultipleTrait的子类
      Supplier<? extends @Nullable List<T>> traitSupplier) { // 参数:特性定义和特性列表提供者
    int index = findIndex(def); // 查找该特性定义在数组中的索引
    if (index < 0) { // 如果索引小于0,说明特性未启用
      return this; // trait is not enabled; ignore it // 特性未启用,忽略它,返回当前对象
    }
    final List<T> traitList = traitSupplier.get(); // 调用提供者获取新的特性列表
    if (traitList == null) { // 如果特性列表为null
      return replace(index, def.getDefault()); // 替换为该特性类型的默认值
    }
    return replace(index, RelCompositeTrait.of(def, traitList)); // 创建复合特性并替换
  }

  /** If a given trait is enabled, replaces it by calling the given function. */
  // 如果给定的特性已启用,则通过调用给定的函数来替换它
  // 参数def: 特性定义,指定要替换的特性类型
  // 参数traitSupplier: 特性的提供者(Supplier),用于延迟计算新的特性
  // 返回值: 包含替换后特性的新RelTraitSet对象,如果特性未启用则返回当前对象
  public <T extends RelTrait> RelTraitSet replaceIf(RelTraitDef<T> def, // 泛型方法,T必须是RelTrait的子类
      Supplier<? extends @Nullable T> traitSupplier) { // 参数:特性定义和特性提供者
    int index = findIndex(def); // 查找该特性定义在数组中的索引
    if (index < 0) { // 如果索引小于0,说明特性未启用
      return this; // trait is not enabled; ignore it // 特性未启用,忽略它,返回当前对象
    }
    T traitList = traitSupplier.get(); // 调用提供者获取新的特性
    if (traitList == null) { // 如果特性为null
      traitList = def.getDefault(); // 使用该特性类型的默认值
    }
    return replace(index, traitList); // 替换指定索引位置的特性
  }

  /**
   * Applies a mapping to this traitSet.
   *
   * @param mapping   Mapping
   * @return traitSet with mapping applied
   */
  // 将映射应用到当前特性集
  // 参数mapping: 字段映射对象,描述如何将字段从一个位置映射到另一个位置
  // 返回值: 应用映射后的新RelTraitSet对象
  public RelTraitSet apply(Mappings.TargetMapping mapping) { // 公共方法,应用字段映射
    RelTrait[] newTraits = new RelTrait[traits.length]; // 创建新的RelTrait数组,长度与原数组相同
    for (int i = 0; i < traits.length; i++) { // 遍历原数组中的每个特性
      newTraits[i] = traits[i].apply(mapping); // 调用每个特性的apply方法,将映射应用到该特性
    }
    return cache.getOrAdd(new RelTraitSet(cache, newTraits)); // 通过缓存获取或添加新的RelTraitSet对象
  }

  /**
   * Returns whether all the traits are default trait value.
   */
  // 返回所有特性是否都是默认特性值
  // 返回值: 如果所有特性都等于其对应特性定义的默认值,则返回true,否则返回false
  public boolean isDefault() { // 公共方法,检查是否所有特性都是默认值
    for (final RelTrait trait : traits) { // 遍历特性数组中的每个特性
      if (trait != trait.getTraitDef().getDefault()) { // 如果当前特性不等于其特性定义的默认值
        return false; // 返回false
      }
    }
    return true; // 所有特性都是默认值,返回true
  }

  /**
   * Returns whether all the traits except {@link Convention}
   * are default trait value.
   */
  // 返回除Convention(约定)之外的所有特性是否都是默认特性值
  // Convention特性代表关系表达式的物理实现约定(如可枚举、物理等)
  // 返回值: 如果除Convention外的所有特性都等于其默认值,则返回true,否则返回false
  public boolean isDefaultSansConvention() { // 公共方法,检查除约定外的特性是否都是默认值
    for (final RelTrait trait : traits) { // 遍历特性数组中的每个特性
      if (trait.getTraitDef() == ConventionTraitDef.INSTANCE) { // 如果当前特性是约定特性
        continue; // 跳过,不检查
      }
      if (trait != trait.getTraitDef().getDefault()) { // 如果当前特性不等于其默认值
        return false; // 返回false
      }
    }
    return true; // 除约定外的所有特性都是默认值,返回true
  }

  /**
   * Returns whether all the traits except {@link Convention}
   * equals with traits in {@code other} traitSet.
   */
  // 返回除Convention(约定)之外的所有特性是否与另一个特性集中的特性相等
  // 参数other: 要比较的另一个RelTraitSet对象
  // 返回值: 如果除约定外的所有特性都相等,则返回true,否则返回false
  public boolean equalsSansConvention(RelTraitSet other) { // 公共方法,比较除约定外的特性是否相等
    if (this == other) { // 如果两个对象是同一个引用
      return true; // 返回true
    }
    if (this.size() != other.size()) { // 如果两个特性集的大小不同
      return false; // 返回false
    }
    for (int i = 0; i < traits.length; i++) { // 遍历特性数组
      if (traits[i].getTraitDef() == ConventionTraitDef.INSTANCE) { // 如果当前特性是约定特性
        continue; // 跳过,不比较
      }
      // each trait should be canonized already
      // 每个特性应该已经被规范化
      if (traits[i] != other.traits[i]) { // 如果两个特性不相等(使用==比较,因为已规范化)
        return false; // 返回false
      }
    }
    return true; // 除约定外的所有特性都相等,返回true
  }

  /**
   * Returns a new traitSet with same traitDefs with
   * current traitSet, but each trait is the default
   * trait value.
   */
  // 返回一个新的特性集,具有与当前特性集相同的特性定义,但每个特性都是默认特性值
  // 返回值: 包含所有默认特性的新RelTraitSet对象
  public RelTraitSet getDefault() { // 公共方法,获取包含所有默认值的特性集
    RelTrait[] newTraits = new RelTrait[traits.length]; // 创建新的RelTrait数组,长度与原数组相同
    for (int i = 0; i < traits.length; i++) { // 遍历原数组
      newTraits[i] = traits[i].getTraitDef().getDefault(); // 获取每个位置的特性定义的默认值
    }
    return cache.getOrAdd(new RelTraitSet(cache, newTraits)); // 通过缓存获取或添加新的RelTraitSet对象
  }

  /**
   * Returns a new traitSet with same traitDefs with
   * current traitSet, but each trait except {@link Convention}
   * is the default trait value. {@link Convention} trait
   * remains the same with current traitSet.
   */
  // 返回一个新的特性集,具有与当前特性集相同的特性定义,但除Convention外的每个特性都是默认特性值
  // Convention特性保持不变
  // 返回值: 包含默认特性(约定除外)的新RelTraitSet对象
  public RelTraitSet getDefaultSansConvention() { // 公共方法,获取包含默认值(约定除外)的特性集
    RelTrait[] newTraits = new RelTrait[traits.length]; // 创建新的RelTrait数组,长度与原数组相同
    for (int i = 0; i < traits.length; i++) { // 遍历原数组
      if (traits[i].getTraitDef() == ConventionTraitDef.INSTANCE) { // 如果当前特性是约定特性
        newTraits[i] = traits[i]; // 保持原值不变
      } else { // 如果不是约定特性
        newTraits[i] = traits[i].getTraitDef().getDefault(); // 使用默认值
      }
    }
    return cache.getOrAdd(new RelTraitSet(cache, newTraits)); // 通过缓存获取或添加新的RelTraitSet对象
  }

  /**
   * Returns {@link Convention} trait defined by
   * {@link ConventionTraitDef#INSTANCE}, or null if the
   * {@link ConventionTraitDef#INSTANCE} is not registered
   * in this traitSet.
   */
  // 返回由ConventionTraitDef.INSTANCE定义的Convention(约定)特性
  // 如果ConventionTraitDef.INSTANCE未在此特性集中注册,则返回null
  // 返回值: Convention对象,如果未找到则返回null
  public @Nullable Convention getConvention() { // 公共方法,获取约定特性
    return getTrait(ConventionTraitDef.INSTANCE); // 调用getTrait方法获取约定特性
  }

  /**
   * Returns {@link RelDistribution} trait defined by
   * {@link RelDistributionTraitDef#INSTANCE}, or null if the
   * {@link RelDistributionTraitDef#INSTANCE} is not registered
   * in this traitSet.
   */
  // 返回由RelDistributionTraitDef.INSTANCE定义的RelDistribution(数据分布)特性
  // 如果RelDistributionTraitDef.INSTANCE未在此特性集中注册,则返回null
  // 返回值: RelDistribution对象,如果未找到则返回null
  @SuppressWarnings("unchecked") // 抑制未检查的类型转换警告
  public <T extends RelDistribution> @Nullable T getDistribution() { // 泛型方法,返回数据分布特性
    return (@Nullable T) getTrait(RelDistributionTraitDef.INSTANCE); // 调用getTrait方法并强制转换
  }

  /**
   * Returns {@link RelCollation} trait defined by
   * {@link RelCollationTraitDef#INSTANCE}, or null if the
   * {@link RelCollationTraitDef#INSTANCE} is not registered
   * in this traitSet.
   */
  // 返回由RelCollationTraitDef.INSTANCE定义的RelCollation(排序)特性
  // 如果RelCollationTraitDef.INSTANCE未在此特性集中注册,则返回null
  // 返回值: RelCollation对象,如果未找到则返回null
  @SuppressWarnings("unchecked") // 抑制未检查的类型转换警告
  public <T extends RelCollation> @Nullable T getCollation() { // 泛型方法,返回排序特性
    return (@Nullable T) getTrait(RelCollationTraitDef.INSTANCE); // 调用getTrait方法并强制转换
  }

  /**
   * Returns the size of the RelTraitSet.
   *
   * @return the size of the RelTraitSet.
   */
  // 返回RelTraitSet的大小(即包含的特性数量)
  // 返回值: 特性数组中元素的数量
  @Override public int size() { // 重写AbstractList的size方法
    return traits.length; // 返回traits数组的长度
  }

  /**
   * Converts a trait to canonical form.
   *
   * <p>After canonization, t1.equals(t2) if and only if t1 == t2.
   *
   * @param trait Trait
   * @return Trait in canonical form
   */
  // 将特性转换为规范(标准)形式
  // 规范化后,如果t1.equals(t2)当且仅当t1 == t2(即使用对象引用相等性)
  // 这是为了实现享元模式,确保相同语义的特性使用同一个对象
  // 参数trait: 要规范化的特性
  // 返回值: 规范化后的特性
  public <T extends RelTrait> T canonize(T trait) { // 泛型方法,规范化特性
    if (trait == null) { // 如果特性为null
      // Return "trait" makes the input type to be the same as the output type,
      // so checkerframework is happy
      // 返回"trait"使输入类型与输出类型相同,让checkerframework满意
      return trait; // 直接返回null
    }

    if (trait instanceof RelCompositeTrait) { // 如果特性是复合特性
      // Composite traits are canonized on creation
      // 复合特性在创建时就已经规范化
      //noinspection unchecked
      return trait; // 直接返回
    }

    //noinspection unchecked
    return (T) trait.getTraitDef().canonize(trait); // 调用特性定义的canonize方法进行规范化
  }

  /**
   * Compares two RelTraitSet objects for equality.
   *
   * @param obj another RelTraitSet
   * @return true if traits are equal and in the same order, false otherwise
   */
  // 比较两个RelTraitSet对象是否相等
  // 参数obj: 另一个RelTraitSet对象
  // 返回值: 如果特性相等且顺序相同则返回true,否则返回false
  @Override public boolean equals(@Nullable Object obj) { // 重写Object的equals方法
    if (this == obj) { // 如果是同一个对象引用
      return true; // 返回true
    }
    if (!(obj instanceof RelTraitSet)) { // 如果obj不是RelTraitSet的实例
      return false; // 返回false
    }
    RelTraitSet that = (RelTraitSet) obj; // 将obj强制转换为RelTraitSet
    if (this.hash != 0 // 如果当前对象的哈希码不为0
        && that.hash != 0 // 且另一个对象的哈希码也不为0
        && this.hash != that.hash) { // 且两个哈希码不相等
      return false; // 返回false(哈希码不同,对象肯定不同)
    }
    if (traits.length != that.traits.length) { // 如果两个特性数组的长度不同
      return false; // 返回false
    }
    for (int i = 0; i < traits.length; i++) { // 遍历特性数组
      if (traits[i] != that.traits[i]) { // 如果对应位置的特性不相等(使用==比较,因为已规范化)
        return false; // 返回false
      }
    }
    return true; // 所有特性都相等,返回true
  }

  @Override public int hashCode() { // 重写Object的hashCode方法
    if (hash == 0) { // 如果哈希码还未计算(默认值为0)
      hash = Arrays.hashCode(traits); // 调用Arrays.hashCode计算traits数组的哈希码
    }
    return hash; // 返回缓存的哈希码
  }

  /**
   * Returns whether this trait set satisfies another trait set.
   *
   * <p>For that to happen, each trait satisfies the corresponding trait in the
   * other set. In particular, each trait set satisfies itself, because each
   * trait subsumes itself.
   *
   * <p>Intuitively, if a relational expression is needed that has trait set
   * S (A, B), and trait set S1 (A1, B1) subsumes S, then any relational
   * expression R in S1 meets that need.
   *
   * <p>For example, if we need a relational expression that has
   * trait set S = {enumerable convention, sorted on [C1 asc]}, and R
   * has {enumerable convention, sorted on [C3], [C1, C2]}. R has two
   * sort keys, but one them [C1, C2] satisfies S [C1], and that is enough.
   */
  // 返回此特性集是否满足另一个特性集
  // 要满足这个条件,每个特性都必须满足另一个集合中对应的特性
  // 特别地,每个特性集都满足自身,因为每个特性都包含自身
  // 直观地说,如果需要一个具有特性集S(A, B)的关系表达式,而特性集S1(A1, B1)包含S,那么S1中的任何关系表达式R都满足该需求
  // 例如,如果需要一个具有特性集S={可枚举约定,按[C1升序]排序}的关系表达式,而R具有{可枚举约定,按[C3]、[C1,C2]排序}
  // R有两个排序键,但其中[C1,C2]满足S的[C1],这就足够了
  // 参数that: 另一个RelTraitSet对象
  // 返回值: 如果此特性集满足另一个特性集则返回true,否则返回false
  public boolean satisfies(RelTraitSet that) { // 公共方法,检查是否满足另一个特性集
    if (this == that) { // 如果是同一个对象
      return true; // 返回true
    }
    final int n = // 计算需要比较的元素数量,取两个特性集大小的较小值
        Math.min( // 使用Math.min取较小值
            this.size(), // 当前特性集的大小
            that.size()); // 另一个特性集的大小
    for (int i = 0; i < n; i++) { // 遍历需要比较的元素
      RelTrait thisTrait = this.traits[i]; // 获取当前特性集的第i个特性
      RelTrait thatTrait = that.traits[i]; // 获取另一个特性集的第i个特性
      if (!thisTrait.satisfies(thatTrait)) { // 如果当前特性不满足另一个特性
        return false; // 返回false
      }
    }
    return true; // 所有特性都满足,返回true
  }

  /**
   * Compares two RelTraitSet objects to see if they match for the purposes of
   * firing a rule. A null RelTrait within a RelTraitSet indicates a wildcard:
   * any RelTrait in the other RelTraitSet will match. If one RelTraitSet is
   * smaller than the other, comparison stops when the last RelTrait from the
   * smaller set has been examined and the remaining RelTraits in the larger
   * set are assumed to match.
   */
  // 比较两个RelTraitSet对象,看它们是否匹配以用于触发规则
  // RelTraitSet中的null RelTrait表示通配符:另一个RelTraitSet中的任何RelTrait都会匹配
  // 如果一个RelTraitSet比另一个小,则在检查完较小集合的最后一个RelTrait后停止比较,并假设较大集合中剩余的RelTraits匹配
  // 参数that: 另一个RelTraitSet对象
  // 返回值: 如果RelTraitSets匹配则返回true,否则返回false
  public boolean matches(RelTraitSet that) { // 公共方法,检查是否匹配(用于规则触发)
    final int n = // 计算需要比较的元素数量,取两个特性集大小的较小值
        Math.min( // 使用Math.min取较小值
            this.size(), // 当前特性集的大小
            that.size()); // 另一个特性集的大小

    for (int i = 0; i < n; i++) { // 遍历需要比较的元素
      RelTrait thisTrait = this.traits[i]; // 获取当前特性集的第i个特性
      RelTrait thatTrait = that.traits[i]; // 获取另一个特性集的第i个特性

      if ((thisTrait == null) || (thatTrait == null)) { // 如果任一特性为null(通配符)
        continue; // 跳过,认为匹配
      }

      if (thisTrait != thatTrait) { // 如果两个特性不相等(使用==比较)
        return false; // 返回false
      }
    }

    return true; // 所有比较的元素都匹配,返回true
  }

  /**
   * Returns whether this trait set contains a given trait.
   *
   * @param trait Sought trait
   * @return Whether set contains given trait
   */
  // 返回此特性集是否包含给定的特性
  // 参数trait: 要查找的特性
  // 返回值: 如果特性集包含该特性则返回true,否则返回false
  public boolean contains(RelTrait trait) { // 公共方法,检查是否包含指定特性
    for (RelTrait relTrait : traits) { // 遍历特性数组
      if (trait == relTrait) { // 如果找到相同的对象引用(使用==比较)
        return true; // 返回true
      }
    }
    return false; // 遍历完未找到,返回false
  }

  /**
   * Returns whether this trait set contains the given trait, or whether the
   * trait is not present because its {@link RelTraitDef} is not enabled.
   * Returns false if another trait of the same {@code RelTraitDef} is
   * present.
   */
  // 返回此特性集是否包含给定的特性,或者因为其RelTraitDef未启用而不存在该特性
  // 如果存在相同RelTraitDef的另一个特性,则返回false
  // 参数trait: 要检查的特性
  // 返回值: 如果特性存在或因为未启用而不存在则返回true,如果存在相同类型的其他特性则返回false
  public boolean containsIfApplicable(RelTrait trait) { // 公共方法,检查是否包含或未启用
    // Note that '==' is sufficient, because trait should be canonized.
    // 注意'=='就足够了,因为特性应该已经被规范化
    final RelTrait trait1 = getTrait(trait.getTraitDef()); // 获取该特性定义对应的特性
    return trait1 == null || trait1 == trait; // 如果为null(未启用)或等于给定特性则返回true
  }

  /**
   * Returns whether this trait set comprises precisely the list of given
   * traits.
   *
   * @param relTraits Traits
   * @return Whether this trait set's traits are the same as the argument
   */
  // 返回此特性集是否完全由给定的特性列表组成
  // 参数relTraits: 要比较的特性数组
  // 返回值: 如果此特性集的特性与参数完全相同则返回true,否则返回false
  public boolean comprises(RelTrait... relTraits) { // 公共方法,检查是否完全由给定特性组成
    return Arrays.equals(traits, relTraits); // 使用Arrays.equals比较两个数组是否相等
  }

  @Override public String toString() { // 重写Object的toString方法
    if (string == null) { // 如果字符串表示还未计算
      string = computeString(); // 调用computeString方法计算字符串表示
    }
    return string; // 返回缓存的字符串表示
  }

  /**
   * Outputs the traits of this set as a String. Traits are output in order,
   * separated by periods.
   */
  // 将此集合的特性输出为字符串。特性按顺序输出,用句点分隔
  // 返回值: 特性集的字符串表示
  String computeString() { // 包级私有方法,计算字符串表示
    StringBuilder s = new StringBuilder(); // 创建StringBuilder对象
    for (int i = 0; i < traits.length; i++) { // 遍历特性数组
      final RelTrait trait = traits[i]; // 获取第i个特性
      if (i > 0) { // 如果不是第一个特性
        s.append('.'); // 添加句点分隔符
      }
      if ((trait == null) // 如果特性为null
          && (traits.length == 1)) { // 且只有一个特性
        // Special format for a list containing a single null trait;
        // otherwise its string appears as "null", which is the same
        // as if the whole trait set were null, and so confusing.
        // 包含单个null特性的列表的特殊格式;否则其字符串显示为"null",
        // 这与整个特性集为null时相同,容易混淆
        s.append("{null}"); // 添加"{null}"作为特殊格式
      } else { // 如果不是特殊情况
        s.append(trait); // 添加特性的字符串表示
      }
    }
    return s.toString(); // 返回构建的字符串
  }

  /**
   * Finds the index of a trait of a given type in this set.
   *
   * @param traitDef Sought trait definition
   * @return index of trait, or -1 if not found
   */
  // 在此集合中查找给定类型特性的索引
  // 参数traitDef: 要查找的特性定义
  // 返回值: 特性的索引,如果未找到则返回-1
  private int findIndex(RelTraitDef traitDef) { // 私有方法,查找特性定义的索引
    for (int i = 0; i < traits.length; i++) { // 遍历特性数组
      RelTrait trait = traits[i]; // 获取第i个特性
      if ((trait != null) && (trait.getTraitDef() == traitDef)) { // 如果特性不为null且特性定义匹配
        return i; // 返回索引
      }
    }

    return -1; // 未找到,返回-1
  }

  /**
   * Returns this trait set with a given trait added or overridden. Does not
   * modify this trait set.
   *
   * @param trait Trait
   * @return Trait set with given trait
   */
  // 返回添加或覆盖给定特性后的特性集。不修改当前特性集
  // 参数trait: 要添加或覆盖的特性
  // 返回值: 包含给定特性的新特性集
  public RelTraitSet plus(RelTrait trait) { // 公共方法,添加或覆盖特性
    if (contains(trait)) { // 如果特性集已包含该特性
      return this; // 返回当前对象本身
    }
    int i = findIndex(trait.getTraitDef()); // 查找该特性定义的索引
    if (i >= 0) { // 如果找到(索引大于等于0)
      return replace(i, trait); // 替换该位置的特性
    }
    final RelTrait canonizedTrait = canonize(trait); // 规范化新特性
    requireNonNull(canonizedTrait, "canonizedTrait"); // 确保规范化后的特性不为null
    RelTrait[] newTraits = new RelTrait[traits.length + 1]; // 创建新数组,长度加1
    System.arraycopy(traits, 0, newTraits, 0, traits.length); // 将原数组内容复制到新数组
    newTraits[traits.length] = canonizedTrait; // 在末尾添加新特性
    return cache.getOrAdd(new RelTraitSet(cache, newTraits)); // 通过缓存获取或添加新的RelTraitSet对象
  }

  public RelTraitSet plusAll(RelTrait[] traits) { // 公共方法,添加多个特性
    RelTraitSet t = this; // 从当前特性集开始
    for (RelTrait trait : traits) { // 遍历要添加的特性数组
      t = t.plus(trait); // 逐个添加特性
    }
    return t; // 返回最终特性集
  }

  public RelTraitSet merge(RelTraitSet additionalTraits) { // 公共方法,合并另一个特性集
    return plusAll(additionalTraits.traits); // 调用plusAll方法,添加另一个特性集的所有特性
  }

  /** Returns a list of traits that are in {@code traitSet} but not in this
   * RelTraitSet. */
  // 返回在traitSet中但不在当前RelTraitSet中的特性列表
  // 参数traitSet: 要比较的特性集
  // 返回值: 差异特性列表
  public ImmutableList<RelTrait> difference(RelTraitSet traitSet) { // 公共方法,计算差异
    final ImmutableList.Builder<RelTrait> builder = ImmutableList.builder(); // 创建不可变列表构建器
    final int n = // 计算需要比较的元素数量,取两个特性集大小的较小值
        Math.min( // 使用Math.min取较小值
            this.size(), // 当前特性集的大小
            traitSet.size()); // 另一个特性集的大小

    for (int i = 0; i < n; i++) { // 遍历需要比较的元素
      RelTrait thisTrait = this.traits[i]; // 获取当前特性集的第i个特性
      RelTrait thatTrait = traitSet.traits[i]; // 获取另一个特性集的第i个特性
      if (thisTrait != thatTrait) { // 如果两个特性不相等
        builder.add(thatTrait); // 将另一个特性集的特性添加到结果列表
      }
    }
    return builder.build(); // 构建并返回不可变列表
  }

  /** Returns whether there are any composite traits in this set. */
  // 返回此集合中是否有任何复合特性
  // 返回值: 如果所有特性都是简单特性则返回true,否则返回false
  public boolean allSimple() { // 公共方法,检查是否都是简单特性
    for (RelTrait trait : traits) { // 遍历特性数组
      if (trait instanceof RelCompositeTrait) { // 如果发现复合特性
        return false; // 返回false
      }
    }
    return true; // 所有特性都是简单特性,返回true
  }

  /** Returns a trait set similar to this one but with all composite traits
   * flattened. */
  // 返回与此特性集类似但所有复合特性都已展平的特性集
  // 返回值: 展平后的特性集
  public RelTraitSet simplify() { // 公共方法,简化特性集(展平复合特性)
    RelTraitSet x = this; // 从当前特性集开始
    for (int i = 0; i < traits.length; i++) { // 遍历特性数组
      final RelTrait trait = traits[i]; // 获取第i个特性
      if (trait instanceof RelCompositeTrait) { // 如果是复合特性
        x = // 更新特性集
            x.replace(i, ((RelCompositeTrait) trait).size() == 1 // 如果复合特性只有一个元素
                ? ((RelCompositeTrait) trait).trait(0) // 则使用该元素替换
                : trait.getTraitDef().getDefault()); // 否则使用默认值替换
      }
    }
    return x; // 返回简化后的特性集
  }

  /** Cache of trait sets. */
  // 特性集的缓存
  private static class Cache { // 私有静态内部类,实现RelTraitSet对象的缓存
    final Map<RelTraitSet, RelTraitSet> map = new HashMap<>(); // 使用HashMap存储RelTraitSet的映射

    Cache() { // 默认构造方法
    }

    RelTraitSet getOrAdd(RelTraitSet t) { // 获取或添加RelTraitSet对象
      RelTraitSet exist = map.putIfAbsent(t, t); // 尝试将t放入map,如果已存在则返回已存在的对象
      return exist == null ? t : exist; // 如果不存在则返回t,否则返回已存在的对象(实现享元模式)
    }
  }
}