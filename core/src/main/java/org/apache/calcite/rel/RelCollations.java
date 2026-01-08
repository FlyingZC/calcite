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
package org.apache.calcite.rel; // 定义包名，该类属于org.apache.calcite.rel包，处理关系代数相关的排序和排序规则

import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型类，用于描述关系表达式的行类型
import org.apache.calcite.util.ImmutableBitSet; // 导入不可变位集合类，用于高效存储和操作整数集合
import org.apache.calcite.util.ImmutableIntList; // 导入不可变整数列表类，用于存储索引列表
import org.apache.calcite.util.Util; // 导入工具类，提供各种通用工具方法
import org.apache.calcite.util.mapping.Mappings; // 导入映射工具类，用于字段索引映射

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类，用于创建不可变的集合

import java.util.HashSet; // 导入哈希集合类，用于去重操作
import java.util.Iterator; // 导入迭代器接口，用于遍历集合
import java.util.List; // 导入列表接口，用于存储有序集合
import java.util.Map; // 导入映射接口，用于存储键值对
import java.util.Set; // 导入集合接口，用于存储不重复的元素
import java.util.stream.Collectors; // 导入流收集器，用于流操作的结果收集

import static java.util.Objects.requireNonNull; // 导入静态方法，用于空值检查

/**
 * Utilities concerning {@link org.apache.calcite.rel.RelCollation}
 * and {@link org.apache.calcite.rel.RelFieldCollation}.
 * 这是一个工具类，提供关于RelCollation（排序规则）和RelFieldCollation（字段排序）的静态工具方法
 * RelCollation表示关系表达式的排序属性，描述数据如何排序
 * RelFieldCollation表示单个字段的排序方式（升序、降序等）
 * 该类不包含实例成员，所有方法都是静态方法，提供排序规则的创建、验证、比较、转换等功能
 */
public class RelCollations { // 定义工具类RelCollations，所有方法都是静态的，不提供实例化
  /**
   * A collation indicating that a relation is not sorted. Ordering by no
   * columns.
   * EMPTY是一个静态常量，表示空的排序规则，即关系表达式没有任何排序
   * 这是一个特殊的排序规则，表示数据是无序的
   * 通过RelCollationTraitDef.INSTANCE.canonize进行规范化处理，确保排序规则的唯一性
   * RelCollationImpl是RelCollation的实现类，构造函数接收一个字段排序列表
   * ImmutableList.of()创建一个空的不可变列表，表示没有任何字段参与排序
   */
  public static final RelCollation EMPTY = // 定义公共静态常量EMPTY，表示空的排序规则（无序）
      RelCollationTraitDef.INSTANCE.canonize( // 调用排序规则特征定义的规范化方法，确保排序规则的唯一性和规范化
          new RelCollationImpl(ImmutableList.of())); // 创建RelCollationImpl实例，传入空的字段排序列表

  /**
   * A collation that cannot be replicated by applying a sort. The only
   * implementation choice is to apply operations that preserve order.
   * PRESERVE是一个已废弃的静态常量，表示保持顺序的排序规则
   * 这个排序规则不能通过应用排序操作来复制，只能通过保持顺序的操作来实现
   * 使用-1作为字段索引，这是一个特殊的标记值，表示保持原有顺序
   * 该常量已标记为@Deprecated，将在2.0版本之前移除
   * 通过重写toString方法，在打印时显示为"PRESERVE"而不是默认的字符串表示
   */
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本之前移除
  public static final RelCollation PRESERVE = // 定义公共静态常量PRESERVE（已废弃），表示保持顺序的排序规则
      RelCollationTraitDef.INSTANCE.canonize( // 调用规范化方法处理排序规则
          new RelCollationImpl( // 创建RelCollationImpl实例
              ImmutableList.of(new RelFieldCollation(-1))) { // 创建包含一个字段排序的列表，字段索引为-1（特殊标记）
            @Override public String toString() { // 重写toString方法，提供自定义的字符串表示
              return "PRESERVE"; // 返回字符串"PRESERVE"表示保持顺序
            }
          });

  private RelCollations() {} // 私有构造方法，防止实例化，因为这是一个工具类，所有方法都是静态的

  public static RelCollation of(RelFieldCollation... fieldCollations) { // 定义静态工厂方法of，接收可变参数的字段排序对象
    return of(ImmutableList.copyOf(fieldCollations)); // 调用另一个重载的of方法，将可变参数转换为不可变列表后传入
  }

  public static RelCollation of(List<RelFieldCollation> fieldCollations) { // 定义静态工厂方法of，接收字段排序列表作为参数
    RelCollation collation; // 声明RelCollation变量，用于存储创建的排序规则
    if (Util.isDistinct(ordinals(fieldCollations))) { // 检查字段索引是否都是唯一的（没有重复的字段索引）
      collation = new RelCollationImpl(ImmutableList.copyOf(fieldCollations)); // 如果字段索引唯一，直接创建RelCollationImpl实例
    } else { // 如果存在重复的字段索引
      // Remove field collations whose field has already been seen // 移除重复出现的字段排序，只保留第一次出现的
      final ImmutableList.Builder<RelFieldCollation> builder = // 创建不可变列表构建器，用于构建去重后的字段排序列表
          ImmutableList.builder(); // 调用builder()方法获取构建器实例
      final Set<Integer> set = new HashSet<>(); // 创建哈希集合，用于记录已经出现过的字段索引
      for (RelFieldCollation fieldCollation : fieldCollations) { // 遍历字段排序列表
        if (set.add(fieldCollation.getFieldIndex())) { // 尝试将字段索引添加到集合，如果返回true表示该索引之前未出现过
          builder.add(fieldCollation); // 将该字段排序添加到构建器中
        }
      }
      collation = new RelCollationImpl(builder.build()); // 使用构建器创建去重后的RelCollationImpl实例
    }
    return RelCollationTraitDef.INSTANCE.canonize(collation); // 对创建的排序规则进行规范化处理并返回
  }

  /**
   * Creates a collation containing one field.
   * 创建一个只包含单个字段的排序规则
   * @param fieldIndex 字段索引，指定要排序的字段
   * @return 包含单个字段排序的RelCollation对象
   */
  public static RelCollation of(int fieldIndex) { // 定义静态工厂方法of，接收单个字段索引作为参数
    return of(new RelFieldCollation(fieldIndex)); // 创建RelFieldCollation对象并调用另一个of方法
  }

  /**
   * Creates a collation containing multiple fields.
   * 创建一个包含多个字段的排序规则
   * @param keys 字段索引列表，指定要排序的字段，默认为升序
   * @return 包含多个字段排序的RelCollation对象
   */
  public static RelCollation of(ImmutableIntList keys) { // 定义静态工厂方法of，接收不可变整数列表作为参数
    List<RelFieldCollation> cols = keys.stream() // 将整数列表转换为流
        .map(k -> new RelFieldCollation(k)) // 将每个整数索引映射为RelFieldCollation对象
        .collect(Collectors.toList()); // 收集流结果为列表
    return of(cols); // 调用of方法创建排序规则并返回
  }

  /**
   * Creates a list containing one collation containing one field.
   * 创建一个包含单个排序规则的列表，该排序规则只包含一个字段
   * @param fieldIndex 字段索引，指定要排序的字段
   * @return 包含单个RelCollation对象的不可变列表
   */
  public static List<RelCollation> createSingleton(int fieldIndex) { // 定义静态方法createSingleton，创建单元素列表
    return ImmutableList.of(of(fieldIndex)); // 创建包含单个排序规则的不可变列表并返回
  }

  /**
   * Checks that a collection of collations is valid.
   * 检查排序规则集合是否有效
   * 有效的排序规则要求所有字段索引都在行类型的有效范围内
   *
   * @param rowType       Row type of the relational expression // 关系表达式的行类型，包含字段信息
   * @param collationList List of collations // 要验证的排序规则列表
   * @param fail          Whether to fail if invalid // 如果无效是否抛出异常（通过assert实现）
   * @return Whether valid // 如果所有字段索引都有效则返回true，否则返回false
   */
  public static boolean isValid( // 定义静态方法isValid，验证排序规则的有效性
      RelDataType rowType, // 参数：关系表达式的行类型
      List<RelCollation> collationList, // 参数：要验证的排序规则列表
      boolean fail) { // 参数：是否在无效时通过assert失败
    final int fieldCount = rowType.getFieldCount(); // 获取行类型的字段总数
    for (RelCollation collation : collationList) { // 遍历每个排序规则
      for (RelFieldCollation fieldCollation : collation.getFieldCollations()) { // 遍历排序规则中的每个字段排序
        final int index = fieldCollation.getFieldIndex(); // 获取字段索引
        if (index < 0 || index >= fieldCount) { // 检查索引是否超出有效范围
          assert !fail; // 如果fail为true且索引无效，assert会失败
          return false; // 返回false表示排序规则无效
        }
      }
    }
    return true; // 所有字段索引都有效，返回true
  }

  public static boolean equal( // 定义静态方法equal，比较两个排序规则列表是否相等
      List<RelCollation> collationList1, // 参数：第一个排序规则列表
      List<RelCollation> collationList2) { // 参数：第二个排序规则列表
    return collationList1.equals(collationList2); // 使用List的equals方法比较两个列表是否相等
  }

  /** Returns the indexes of the field collations in a given collation. */
  public static List<Integer> ordinals(RelCollation collation) { // 定义静态方法ordinals，提取排序规则中的字段索引列表
    return ordinals(collation.getFieldCollations()); // 调用重载的ordinals方法，传入字段排序列表
  }

  /** Returns the indexes of the fields in a list of field collations. */
  public static List<Integer> ordinals( // 定义静态方法ordinals，从字段排序列表中提取字段索引列表
      List<RelFieldCollation> fieldCollations) { // 参数：字段排序列表
    return Util.transform(fieldCollations, RelFieldCollation::getFieldIndex); // 使用工具方法转换，提取每个字段排序的索引
  }

  /** Returns whether a collation indicates that the collection is sorted on
   * a given list of keys.
   * 判断排序规则是否表示集合按照给定的键列表排序
   * 该方法要求键必须按顺序出现在排序规则的前缀中
   *
   * @param collation Collation // 要检查的排序规则
   * @param keys List of keys // 要检查的键列表
   * @return Whether the collection is sorted on the given keys // 如果排序规则的前缀包含所有键且顺序一致则返回true
   */
  public static boolean contains(RelCollation collation, // 定义静态方法contains，检查排序规则是否包含指定的键
      Iterable<Integer> keys) { // 参数：可迭代的键列表
    return contains(collation, Util.distinctList(keys)); // 调用私有重载方法，先对键去重
  }

  private static boolean contains(RelCollation collation, // 定义私有静态方法contains，检查排序规则是否包含指定的键列表
      List<Integer> keys) { // 参数：已去重的键列表
    final int n = collation.getFieldCollations().size(); // 获取排序规则中字段排序的数量
    final Iterator<Integer> iterator = keys.iterator(); // 创建键列表的迭代器
    for (int i = 0; i < n; i++) { // 遍历排序规则中的每个字段排序
      final RelFieldCollation fieldCollation = // 获取第i个字段排序
          collation.getFieldCollations().get(i); // 通过索引获取字段排序对象
      if (!iterator.hasNext()) { // 检查是否还有键需要匹配
        return true; // 如果所有键都已匹配，返回true
      }
      if (fieldCollation.getFieldIndex() != iterator.next()) { // 比较字段索引和键
        return false; // 如果不匹配，返回false
      }
    }
    return !iterator.hasNext(); // 检查是否所有键都已匹配
  }

  /** Returns whether one of a list of collations indicates that the collection
   * is sorted on the given list of keys. */
  public static boolean contains(List<RelCollation> collations, // 定义静态方法contains，检查排序规则列表中是否有包含指定键的排序规则
      ImmutableIntList keys) { // 参数：不可变整数键列表
    final List<Integer> distinctKeys = Util.distinctList(keys); // 对键列表去重
    for (RelCollation collation : collations) { // 遍历排序规则列表
      if (contains(collation, distinctKeys)) { // 检查当前排序规则是否包含所有键
        return true; // 如果找到匹配的排序规则，返回true
      }
    }
    return false; // 没有找到匹配的排序规则，返回false
  }

  /** Returns whether a collation contains a given list of keys regardless
   * the order.
   * 判断排序规则是否包含给定的键列表，不考虑顺序
   * 该方法检查排序规则的前N个字段是否恰好包含所有指定的键（顺序不重要）
   *
   * @param collation Collation // 要检查的排序规则
   * @param keys List of keys // 要检查的键列表
   * @return Whether the collection contains the given keys // 如果排序规则的前N个字段包含所有键则返回true
   */
  public static boolean containsOrderless(RelCollation collation, // 定义静态方法containsOrderless，不考虑顺序地检查排序规则是否包含键
      List<Integer> keys) { // 参数：键列表
    final List<Integer> distinctKeys = Util.distinctList(keys); // 对键列表去重
    final ImmutableBitSet keysBitSet = ImmutableBitSet.of(distinctKeys); // 将键列表转换为不可变位集合，用于高效比较
    List<Integer> colKeys = Util.distinctList(collation.getKeys()); // 获取排序规则的键列表并去重

    if (colKeys.size() < distinctKeys.size()) { // 检查排序规则的键数量是否足够
      return false; // 如果排序规则的键数量少于要检查的键数量，返回false
    } else { // 键数量足够
      ImmutableBitSet bitset = // 创建位集合
          ImmutableBitSet.of(colKeys.subList(0, distinctKeys.size())); // 取排序规则的前N个键转换为位集合
      return bitset.equals(keysBitSet); // 比较两个位集合是否相等
    }
  }

  /** Returns whether a collation is contained by a given list of keys regardless ordering.
   * 判断排序规则是否被给定的键列表包含，不考虑顺序
   * 该方法检查排序规则的所有键是否都在给定的键列表中
   *
   * @param collation Collation // 要检查的排序规则
   * @param keys List of keys // 要检查的键列表
   * @return Whether the collection contains the given keys // 如果排序规则的所有键都在键列表中则返回true
   */
  public static boolean containsOrderless( // 定义静态方法containsOrderless，检查键列表是否包含排序规则的所有键
      List<Integer> keys, RelCollation collation) { // 参数：键列表和排序规则（注意参数顺序与上一个方法相反）
    final List<Integer> distinctKeys = Util.distinctList(keys); // 对键列表去重
    List<Integer> colKeys = Util.distinctList(collation.getKeys()); // 获取排序规则的键列表并去重

    if (colKeys.size() > distinctKeys.size()) { // 检查排序规则的键数量是否超过键列表的数量
      return false; // 如果排序规则的键数量超过键列表的数量，返回false
    } else { // 键数量不超过
      return colKeys.stream().allMatch(i -> distinctKeys.contains(i)); // 检查排序规则的每个键是否都在键列表中
    }
  }

  /**
   * Returns whether one of a list of collations contains the given list of keys
   * regardless the order.
   * 判断排序规则列表中是否有任何一个排序规则包含给定的键列表，不考虑顺序
   * @param collations 排序规则列表
   * @param keys 要检查的键列表
   * @return 如果有任何一个排序规则包含所有键则返回true
   */
  public static boolean collationsContainKeysOrderless( // 定义静态方法collationsContainKeysOrderless，检查排序规则列表是否包含键
      List<RelCollation> collations, List<Integer> keys) { // 参数：排序规则列表和键列表
    for (RelCollation collation : collations) { // 遍历排序规则列表
      if (containsOrderless(collation, keys)) { // 检查当前排序规则是否包含所有键
        return true; // 如果找到匹配的排序规则，返回true
      }
    }
    return false; // 没有找到匹配的排序规则，返回false
  }

  /**
   * Returns whether one of a list of collations is contained by the given list of keys
   * regardless the order.
   * 判断排序规则列表中是否有任何一个排序规则的所有键都在给定的键列表中，不考虑顺序
   * @param keys 键列表
   * @param collations 排序规则列表
   * @return 如果有任何一个排序规则的所有键都在键列表中则返回true
   */
  public static boolean keysContainCollationsOrderless( // 定义静态方法keysContainCollationsOrderless，检查键列表是否包含排序规则
      List<Integer> keys,  List<RelCollation> collations) { // 参数：键列表和排序规则列表
    for (RelCollation collation : collations) { // 遍历排序规则列表
      if (containsOrderless(keys, collation)) { // 检查键列表是否包含当前排序规则的所有键
        return true; // 如果找到匹配的排序规则，返回true
      }
    }
    return false; // 没有找到匹配的排序规则，返回false
  }

  public static RelCollation shift(RelCollation collation, int offset) { // 定义静态方法shift，对排序规则的字段索引进行偏移
    if (offset == 0) { // 检查偏移量是否为0
      return collation; // 如果偏移量为0，直接返回原排序规则，避免不必要的操作
    }
    final ImmutableList.Builder<RelFieldCollation> fieldCollations = // 创建不可变列表构建器
        ImmutableList.builder(); // 获取构建器实例
    for (RelFieldCollation fc : collation.getFieldCollations()) { // 遍历排序规则中的每个字段排序
      fieldCollations.add(fc.shift(offset)); // 对字段排序应用偏移量并添加到构建器
    }
    return RelCollationTraitDef.INSTANCE.canonize(new RelCollationImpl(fieldCollations.build())); // 创建新的排序规则并规范化后返回
  }

  /** Creates a copy of this collation that changes the ordinals of input
   * fields.
   * 创建排序规则的副本，根据给定的映射关系改变输入字段的序号
   * 这个方法用于在字段索引发生变化时（例如投影操作后）调整排序规则
   *
   * @param collation 原始排序规则
   * @param mapping 字段索引映射关系，从旧索引映射到新索引
   * @return 应用映射后的新排序规则
   */
  public static RelCollation permute(RelCollation collation, // 定义静态方法permute，根据Map映射转换排序规则
      Map<Integer, Integer> mapping) { // 参数：字段索引映射Map
    return of( // 调用of方法创建新的排序规则
        Util.transform(collation.getFieldCollations(), // 转换字段排序列表
            fc -> fc.withFieldIndex( // 为每个字段排序设置新的字段索引
                requireNonNull(mapping.get(fc.getFieldIndex()), // 从映射中获取新索引，如果不存在则抛出异常
                    () -> "no entry for " + fc.getFieldIndex() + " in " + mapping)))); // 异常消息，显示缺失的映射项
  }

  /** Creates a copy of this collation that changes the ordinals of input
   * fields.
   * 创建排序规则的副本，根据给定的目标映射关系改变输入字段的序号
   * 这个方法与上一个方法功能相同，但使用Mappings.TargetMapping作为映射类型
   * TargetMapping是Calcite专门用于字段映射的接口，提供更强大的映射功能
   *
   * @param collation 原始排序规则
   * @param mapping 目标映射对象，用于将源索引映射到目标索引
   * @return 应用映射后的新排序规则
   */
  public static RelCollation permute(RelCollation collation, // 定义静态方法permute，根据TargetMapping转换排序规则
      Mappings.TargetMapping mapping) { // 参数：目标映射对象
    return of( // 调用of方法创建新的排序规则
        Util.transform(collation.getFieldCollations(), // 转换字段排序列表
            fc -> fc.withFieldIndex(mapping.getTarget(fc.getFieldIndex())))); // 为每个字段排序设置新的字段索引
  }
} // 类定义结束
