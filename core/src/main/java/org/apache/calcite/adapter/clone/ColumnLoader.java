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
package org.apache.calcite.adapter.clone; // 包声明：克隆适配器包，包含用于内存表克隆和缓存的功能

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂，用于创建和管理Java类型
import org.apache.calcite.avatica.ColumnMetaData; // 导入列元数据类，描述列的物理表示方式
import org.apache.calcite.avatica.util.DateTimeUtils; // 导入日期时间工具类，用于处理日期时间转换
import org.apache.calcite.linq4j.Enumerable; // 导入可枚举接口，表示可遍历的数据集合
import org.apache.calcite.linq4j.Ord; // 导入有序包装类，为元素添加索引
import org.apache.calcite.linq4j.tree.Primitive; // 导入原始类型枚举，表示Java基本类型
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型，表示Calcite中的类型系统
import org.apache.calcite.rel.type.RelDataTypeField; // 导入关系数据类型字段，表示表中的列
import org.apache.calcite.rel.type.RelProtoDataType; // 导入关系原型数据类型，延迟创建数据类型
import org.apache.calcite.util.Util; // 导入工具类，提供通用工具方法

import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf; // 导入注解，表示方法返回值与参数非空性的关系
import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，标记可能为null的值

import java.lang.reflect.Type; // 导入Type接口，表示Java类型
import java.sql.Date; // 导入SQL日期类
import java.sql.Time; // 导入SQL时间类
import java.sql.Timestamp; // 导入SQL时间戳类
import java.util.AbstractList; // 导入抽象列表类，用于创建自定义列表实现
import java.util.ArrayList; // 导入数组列表类，动态数组实现
import java.util.Arrays; // 导入数组工具类，提供数组操作方法
import java.util.Collections; // 导入集合工具类，提供不可变集合等
import java.util.HashMap; // 导入哈希映射类，键值对存储
import java.util.List; // 导入列表接口
import java.util.Map; // 导入映射接口

import static java.util.Objects.requireNonNull; // 静态导入，用于检查对象非空

/**
 * Column loader.
 * 列加载器：负责将源表数据加载到内存中，并优化列的存储表示方式
 * 这是Calcite克隆适配器的核心类，用于将外部数据源转换为内存中的高效表示
 * 
 * 主要功能：
 * 1. 从源表加载数据到内存列表
 * 2. 为每列选择最优的存储表示（如位切片、字典编码、原始数组等）
 * 3. 检测表的主键列，用于后续的快速查找
 * 4. 对数据进行排序以优化查询性能
 *
 * @param <T> Element type of source table - 源表元素的类型，通常是Object[]表示一行数据
 */
class ColumnLoader<T> { // 列加载器类，泛型T表示源表行类型
  // INT_B：int类型位运算的掩码数组，用于快速计算log2，每个元素对应不同的位范围
  static final int[] INT_B = {0x2, 0xC, 0xF0, 0xFF00, 0xFFFF0000}; // 位掩码：二进制10, 1100, 11110000, 1111111100000000, 11111111111111110000000000000000
  // INT_S：int类型位移数组，与INT_B对应，表示需要右移的位数
  static final int[] INT_S = {1, 2, 4, 8, 16}; // 位移量：1位, 2位, 4位, 8位, 16位
  // LONG_B：long类型位运算的掩码数组，支持64位长整数的log2计算
  static final long[] LONG_B = { // long类型的位掩码数组
      0x2, 0xC, 0xF0, 0xFF00, 0xFFFF0000, 0xFFFFFFFF00000000L}; // 6个掩码，覆盖long的所有位
  // LONG_S：long类型位移数组，与LONG_B对应
  static final int[] LONG_S = {1, 2, 4, 8, 16, 32}; // 6个位移量，覆盖1到32位

  // list：存储源表的所有行数据，每行是一个T类型的对象（通常是Object[]数组）
  public final List<T> list = new ArrayList<>(); // 公共成员，存储加载后的原始数据行
  // representationValues：存储每列的优化表示，每个Column对象包含列的压缩/优化存储方式
  public final List<ArrayTable.Column> representationValues = new ArrayList<>(); // 公共成员，存储每列的优化表示对象
  // typeFactory：Java类型工厂，用于在Calcite类型系统和Java类型之间转换
  private final JavaTypeFactory typeFactory; // 私有成员，类型工厂实例
  // sortField：排序字段的索引，如果找到唯一键列则设置为其索引，否则为-1
  public final int sortField; // 公共成员，记录用于排序的字段索引

  /**
   * Creates a column loader, and performs the load.
   * 创建列加载器并执行加载操作
   * 
   * @param typeFactory Type factory - Java类型工厂，用于类型转换
   * @param sourceTable Source data - 源表数据，可枚举的数据集合
   * @param protoRowType Logical row type - 逻辑行类型，描述表的结构
   * @param repList Physical row types, or null if not known - 物理行类型列表，描述每列的物理表示方式，如果为null则默认使用OBJECT表示
   */
  @SuppressWarnings("method.invocation.invalid") // 抑制编译器警告，表示方法调用是有效的
  ColumnLoader(JavaTypeFactory typeFactory, // 构造方法：创建列加载器实例
      Enumerable<T> sourceTable, // 参数：源表数据，可枚举的数据源
      RelProtoDataType protoRowType, // 参数：原型数据类型，延迟创建的行类型
      @Nullable List<ColumnMetaData.Rep> repList) { // 参数：列表示列表，可为null
    this.typeFactory = typeFactory; // 保存类型工厂引用
    final RelDataType rowType = protoRowType.apply(typeFactory); // 应用类型工厂，获取实际的行类型
    if (repList == null) { // 如果没有提供物理表示列表
      repList = // 创建默认的表示列表
          Collections.nCopies(rowType.getFieldCount(), // 为每个字段创建一个OBJECT表示
              ColumnMetaData.Rep.OBJECT); // 使用OBJECT作为默认表示方式
    }
    sourceTable.into(list); // 将源表数据加载到内存列表中
    final int[] sorts = {-1}; // 初始化排序字段数组，初始值为-1表示未找到
    load(rowType, repList, sorts); // 调用load方法加载并处理数据
    this.sortField = sorts[0]; // 保存排序字段的索引
  }

  /**
   * 计算大于等于给定值的最小2的幂次方
   * 这是一个位运算技巧，通过将所有低位设置为1然后加1来实现
   * 例如：nextPowerOf2(5) = 8, nextPowerOf2(16) = 16, nextPowerOf2(17) = 32
   *
   * @param v 输入值
   * @return 大于等于v的最小2的幂次方
   */
  static int nextPowerOf2(int v) { // 计算int类型的下一个2的幂
    v--; // 先减1，处理v本身就是2的幂的情况
    v |= v >>> 1; // 将最高位后的所有位设置为1，右移1位并或运算
    v |= v >>> 2; // 右移2位并或运算
    v |= v >>> 4; // 右移4位并或运算
    v |= v >>> 8; // 右移8位并或运算
    v |= v >>> 16; // 右移16位并或运算，覆盖int的所有位
    v++; // 加1得到下一个2的幂
    return v; // 返回结果
  }

  /**
   * 计算long类型大于等于给定值的最小2的幂次方
   * 与int版本类似，但处理64位long类型
   *
   * @param v 输入值
   * @return 大于等于v的最小2的幂次方
   */
  static long nextPowerOf2(long v) { // 计算long类型的下一个2的幂
    v--; // 先减1
    v |= v >>> 1; // 右移1位
    v |= v >>> 2; // 右移2位
    v |= v >>> 4; // 右移4位
    v |= v >>> 8; // 右移8位
    v |= v >>> 16; // 右移16位
    v |= v >>> 32; // 右移32位，覆盖long的所有64位
    v++; // 加1得到结果
    return v; // 返回结果
  }

  /**
   * 计算以2为底的对数（log2）
   * 使用位运算和预定义的掩码数组实现快速计算
   * 例如：log2(8) = 3, log2(16) = 4
   *
   * @param v 输入值（必须是2的幂）
   * @return log2(v)的结果
   */
  static int log2(int v) { // 计算int类型的log2
    int r = 0; // 结果初始化为0
    for (int i = 4; i >= 0; i--) { // 从高位到低位遍历掩码数组
      if ((v & INT_B[i]) != 0) { // 如果当前位范围有值
        v >>= INT_S[i]; // 右移相应的位数
        r |= INT_S[i]; // 将对应的位设置到结果中
      }
    }
    return r; // 返回log2结果
  }

  /**
   * 计算long类型的log2
   * 与int版本类似，但处理64位long类型
   *
   * @param v 输入值（必须是2的幂）
   * @return log2(v)的结果
   */
  static int log2(long v) { // 计算long类型的log2
    int r = 0; // 结果初始化为0
    for (int i = 5; i >= 0; i--) { // 从高位到低位遍历long掩码数组
      if ((v & LONG_B[i]) != 0) { // 如果当前位范围有值
        v >>= LONG_S[i]; // 右移相应的位数
        r |= LONG_S[i]; // 将对应的位设置到结果中
      }
    }
    return r; // 返回log2结果
  }

  /**
   * 反转映射数组
   * 将目标索引数组转换回源索引数组
   * 例如：invert([2,0,1]) = [1,2,0]，表示索引0的元素来自索引1，索引1的元素来自索引2，索引2的元素来自索引0
   *
   * @param targets 目标索引数组，targets[i]表示索引i的元素应该放在targets[i]位置
   * @return 源索引数组，sources[i]表示索引i的元素来自sources[i]
   */
  static int[] invert(int[] targets) { // 反转映射数组
    final int[] sources = new int[targets.length]; // 创建源数组
    for (int i = 0; i < targets.length; i++) { // 遍历目标数组
      sources[targets[i]] = i; // 设置反向映射
    }
    return sources; // 返回源数组
  }

  /**
   * 检查数组是否是恒等映射
   * 即检查sources[i] == i对所有i是否成立
   * 如果是恒等映射，表示数据已经按顺序排列，不需要重新排序
   *
   * @param sources 源索引数组
   * @return 如果是恒等映射返回true，否则返回false
   */
  static boolean isIdentity(int[] sources) { // 检查是否是恒等映射
    for (int i = 0; i < sources.length; i++) { // 遍历数组
      if (sources[i] != i) { // 如果发现不匹配的索引
        return false; // 返回false
      }
    }
    return true; // 所有索引都匹配，返回true
  }

  /**
   * 返回加载的数据行数
   *
   * @return 列表的大小，即数据行数
   */
  public int size() { // 获取数据行数
    return list.size(); // 返回列表大小
  }

  /**
   * 加载数据并为每列选择最优的存储表示
   * 这是ColumnLoader的核心方法，负责：
   * 1. 遍历每一列
   * 2. 提取列数据
   * 3. 根据列的值分布选择最优的存储表示
   * 4. 检测唯一键列并排序数据
   *
   * @param elementType 行类型，包含所有字段信息
   * @param repList 每列的物理表示方式列表
   * @param sort 排序字段索引数组（输出参数），如果找到唯一键则设置
   */
  private void load(final RelDataType elementType, // 加载数据并处理每列
      List<ColumnMetaData.Rep> repList, int[] sort) { // 参数：行类型、表示列表、排序字段
    // 创建一个抽象列表，用于快速获取每列的Java类型
    final List<Type> types = // 类型列表
        new AbstractList<Type>() { // 匿名内部类，实现抽象列表
          final List<RelDataTypeField> fields = // 获取所有字段
              elementType.getFieldList(); // 从行类型中获取字段列表
          @Override public Type get(int index) { // 获取指定索引的Java类型
            return typeFactory.getJavaClass( // 使用类型工厂获取Java类
                fields.get(index).getType()); // 获取字段的类型
          }

          @Override public int size() { // 返回字段数量
            return fields.size(); // 字段列表的大小
          }
        };
    int[] sources = null; // 源索引数组，用于排序，初始为null表示未排序
    for (final Ord<Type> pair : Ord.zip(types)) { // 遍历每一列，Ord.zip为元素添加索引
      @SuppressWarnings("unchecked") // 抑制类型转换警告
      // 提取当前列的数据：如果只有一列直接使用list，否则创建一个抽象列表提取指定列
      final List<?> sliceList = // 列数据切片
          types.size() == 1 // 如果只有一列
              ? list // 直接使用原始列表
              : new AbstractList<Object>() { // 否则创建抽象列表提取指定列
                final int slice = pair.i; // 当前列的索引

                @Override public Object get(int index) { // 获取指定行的列值
                  T row = requireNonNull(list.get(index), () -> "null value at index " + index); // 获取行并检查非空
                  return ((Object[]) row)[slice]; // 返回指定列的值（假设行是Object[]）
                }

                @Override public int size() { // 返回行数
                  return list.size(); // 列表大小
                }
              };
      // 根据列的物理表示方式包装列数据，处理特殊类型转换（如Timestamp转Long）
      final List<?> list2 = // 包装后的列数据
          wrap( // 调用wrap方法
              repList.get(pair.i), // 获取当前列的表示方式
              sliceList, // 原始列数据
              elementType.getFieldList().get(pair.i).getType()); // 获取列的类型
      // 获取列的Java类，如果是Class类型则使用，否则使用Object.class
      final Class clazz = pair.e instanceof Class // 检查类型是否是Class
          ? (Class) pair.e // 如果是Class则强制转换
          : Object.class; // 否则使用Object.class
      ValueSet valueSet = new ValueSet(clazz); // 创建值集对象，用于收集列的统计信息
      for (Object o : list2) { // 遍历列的所有值
        valueSet.add((Comparable) o); // 将值添加到值集中进行统计
      }
      // 如果还没有找到排序字段，且当前列的所有值都是唯一的
      if (sort != null // 如果排序参数不为null
          && sort[0] < 0 // 且还没有找到排序字段
          && valueSet.map.keySet().size() == list.size()) { // 且当前列的值都是唯一的（无重复）
        // We have discovered a the first unique key in the table.
        // 我们发现了表中的第一个唯一键列
        sort[0] = pair.i; // 设置排序字段为当前列索引
        // map.keySet().size() == list.size() above implies list contains only non-null elements
        // 上面的条件意味着列表只包含非空元素
        @SuppressWarnings("assignment.type.incompatible") // 抑制类型转换警告
        final Comparable[] values = // 提取所有唯一值
            valueSet.values.toArray(new Comparable[0]); // 转换为数组
        final Kev[] kevs = new Kev[list.size()]; // 创建键值对数组，用于排序
        for (int i = 0; i < kevs.length; i++) { // 遍历所有行
          kevs[i] = new Kev(i, values[i]); // 创建键值对：源索引和值
        }
        Arrays.sort(kevs); // 对键值对按值排序
        sources = new int[list.size()]; // 创建源索引数组
        for (int i = 0; i < sources.length; i++) { // 遍历排序后的数组
          sources[i] = kevs[i].source; // 记录每个位置对应的原始索引
        }

        if (isIdentity(sources)) { // 检查是否是恒等映射（已经排序）
          // Table was already sorted. Clear the permutation.
          // 表已经排序，清除排列
          // We've already set sort[0], so we won't check for another
          // sorted column.
          // 我们已经设置了sort[0]，所以不会检查另一个排序列
          sources = null; // 设置为null表示不需要重新排序
        } else { // 如果需要重新排序
          // Re-sort all previous columns.
          // 重新排序所有之前的列
          for (int i = 0; i < pair.i; i++) { // 遍历之前处理的所有列
            representationValues.set( // 更新列的表示
                i, representationValues.get(i).permute(sources)); // 应用排列变换
          }
        }
      }
      // 将当前列的值集冻结为列表示，并添加到表示值列表中
      representationValues.add(valueSet.freeze(pair.i, sources)); // 冻结并添加列表示
    }
  }

  /** Adapt for some types that we represent differently internally than their
   * JDBC types. {@link java.sql.Timestamp} values that are not null are
   * converted to {@code long}, but nullable timestamps are acquired using
   * {@link java.sql.ResultSet#getObject(int)} and therefore the Timestamp
   * value needs to be converted to a {@link Long}. Similarly
   * {@link java.sql.Date} and {@link java.sql.Time} values to
   * {@link Integer}.
   * 
   * 适配一些类型，这些类型在内部表示与JDBC类型不同。
   * 非null的Timestamp值转换为long，但可空的timestamp通过ResultSet.getObject获取，
   * 因此需要将Timestamp转换为Long。类似地，Date和Time值转换为Integer。
   * 
   * 注意：当前实现中if (true)直接返回原列表，这个方法实际上被禁用了
   * 原因可能是性能问题或者后续优化改变了实现方式
   *
   * @param rep 列的物理表示方式
   * @param list 原始列数据列表
   * @param type 列的SQL类型
   * @return 转换后的列数据列表
   */
  private static List<? extends @Nullable Object> wrap(ColumnMetaData.Rep rep, List<?> list, // 包装方法，处理特殊类型转换
      RelDataType type) { // 参数：表示方式、列数据、SQL类型
    if (true) { // 如果为true（当前总是true）
      return list; // 直接返回原列表，不进行转换
    }
    switch (type.getSqlTypeName()) { // 根据SQL类型进行转换
    case TIMESTAMP: // 处理时间戳类型
      switch (rep) { // 根据表示方式
      case OBJECT: // 如果是OBJECT表示
      case JAVA_SQL_TIMESTAMP: // 或JAVA_SQL_TIMESTAMP表示
        final List<@Nullable Long> longs = // 创建Long列表
            Util.transform((List<@Nullable Timestamp>) list, // 转换Timestamp列表
                (Timestamp t) -> t == null ? null : t.getTime()); // 将Timestamp转换为Long（毫秒数）
        return longs; // 返回转换后的列表
      default: // 其他表示方式
        break; // 跳过
      }
      break;
    case TIME: // 处理时间类型
      switch (rep) { // 根据表示方式
      case OBJECT: // 如果是OBJECT表示
      case JAVA_SQL_TIME: // 或JAVA_SQL_TIME表示
        return Util.<@Nullable Time, @Nullable Integer>transform( // 转换Time列表
            (List<@Nullable Time>) list, // 输入Time列表
            (Time t) -> t == null ? null // 如果为null返回null
                : (int) (t.getTime() % DateTimeUtils.MILLIS_PER_DAY)); // 否则转换为当天的毫秒数
      default: // 其他表示方式
        break; // 跳过
      }
      break;
    case DATE: // 处理日期类型
      switch (rep) { // 根据表示方式
      case OBJECT: // 如果是OBJECT表示
      case JAVA_SQL_DATE: // 或JAVA_SQL_DATE表示
        return Util.<@Nullable Date, @Nullable Integer>transform( // 转换Date列表
            (List<@Nullable Date>) list, // 输入Date列表
            (Date d) -> d == null // 如果为null
                ? null // 返回null
                : (int) (d.getTime() / DateTimeUtils.MILLIS_PER_DAY)); // 否则转换为天数（从1970-01-01开始）
      default: // 其他表示方式
        break; // 跳过
      }
      break;
    default: // 其他SQL类型
      break; // 不做处理
    }
    return list; // 返回原列表
  }

  /**
   * Set of values of a column, created during the load process, and converted
   * to a serializable (and more compact) form before load completes.
   * 
   * 列的值集合，在加载过程中创建，在加载完成前转换为可序列化（更紧凑）的形式。
   * 
   * ValueSet是ColumnLoader的内部类，负责收集列的统计信息并选择最优的存储表示。
   * 它会记录列中的所有不同值、最小值、最大值、是否包含null等信息，
   * 然后根据这些信息选择最适合的存储方式（如位切片、字典编码、原始数组等）。
   * 
   * 主要功能：
   * 1. 收集列的所有值并去重
   * 2. 记录列的统计信息（最小值、最大值、基数、是否包含null）
   * 3. 根据统计信息选择最优的存储表示
   * 4. 将值集冻结为可序列化的列表示
   */
  static class ValueSet { // 值集合内部类
    final Class clazz; // 列的Java类类型
    final Map<Comparable, Comparable> map = new HashMap<>(); // 去重后的值映射，键和值相同，用于快速去重
    final List<@Nullable Comparable> values = new ArrayList<>(); // 所有值的列表（包含重复值和null），保持原始顺序
    @Nullable Comparable min; // 列的最小值（非null）
    @Nullable Comparable max; // 列的最大值（非null）
    boolean containsNull; // 标记是否包含null值

    /**
     * 构造方法
     * @param clazz 列的Java类类型
     */
    ValueSet(Class clazz) { // 构造方法
      this.clazz = clazz; // 保存类类型
    }

    /**
     * 向值集添加一个值
     * 这个方法会：
     * 1. 如果值不为null，添加到map中去重，并更新最小值和最大值
     * 2. 如果值为null，设置containsNull标志
     * 3. 将值添加到values列表中（保持原始顺序）
     *
     * @param e 要添加的值
     */
    void add(@Nullable Comparable e) { // 添加值到值集
      if (e != null) { // 如果值不为null
        final Comparable old = e; // 保存原始值
        e = map.get(e); // 尝试从map中获取（检查是否已存在）
        if (e == null) { // 如果不存在
          e = old; // 使用原始值
          map.put(e, e); // 添加到map中
          //noinspection unchecked // 抑制未检查的转换警告
          if (min == null || min.compareTo(e) > 0) { // 如果这是第一个值或比当前最小值小
            min = e; // 更新最小值
          }
          //noinspection unchecked // 抑制未检查的转换警告
          if (max == null || max.compareTo(e) < 0) { // 如果这是第一个值或比当前最大值大
            max = e; // 更新最大值
          }
        }
      } else { // 如果值为null
        containsNull = true; // 设置包含null标志
      }
      values.add(e); // 将值添加到列表中（null或非null）
    }

    /**
     * Freezes the contents of this value set into a column, optionally
     * re-ordering if {@code sources} is specified.
     * 
     * 将值集的内容冻结为列，如果指定了sources则重新排序。
     * 
     * 这个方法是ValueSet的核心，它：
     * 1. 选择最优的存储表示（调用chooseRep）
     * 2. 计算列的基数（不同值的数量）
     * 3. 调用表示的freeze方法将数据转换为紧凑的存储格式
     * 4. 创建Column对象并返回
     *
     * @param ordinal 列在表中的序号
     * @param sources 排序的源索引数组，如果为null则不重新排序
     * @return 冻结后的列对象
     */
    ArrayTable.Column freeze(int ordinal, int @Nullable [] sources) { // 冻结值集为列
      ArrayTable.Representation representation = chooseRep(ordinal); // 选择最优的存储表示
      final int cardinality = map.size() + (containsNull ? 1 : 0); // 计算基数：不同值的数量加上null（如果有）
      final Object data = representation.freeze(this, sources); // 调用表示的freeze方法转换数据
      return new ArrayTable.Column(representation, data, cardinality); // 创建并返回列对象
    }

    /**
     * 选择最优的存储表示
     * 这是存储优化的核心，根据列的特征选择最适合的存储方式：
     * 
     * 1. 如果列不包含null且是基本类型，考虑使用位切片或原始数组
     * 2. 如果列的值范围小且基数小，使用位切片存储（节省空间）
     * 3. 如果列的基数适中且数据量大，使用字典编码（节省重复值的存储）
     * 4. 否则使用对象数组存储（最通用但占用空间最大）
     *
     * @param ordinal 列在表中的序号
     * @return 最优的存储表示
     */
    ArrayTable.Representation chooseRep(int ordinal) { // 选择存储表示
      Primitive primitive = Primitive.of(clazz); // 获取原始类型（如果是基本类型）
      Primitive boxPrimitive = Primitive.ofBox(clazz); // 获取包装类型的原始类型
      Primitive p = primitive != null ? primitive : boxPrimitive; // 优先使用原始类型，否则使用包装类型
      if (!containsNull && p != null) { // 如果不包含null且是基本类型
        switch (p) { // 根据基本类型选择
        case FLOAT: // 浮点类型
        case DOUBLE: // 双精度类型
          return new ArrayTable.PrimitiveArray(ordinal, p, p); // 使用原始数组存储
        case OTHER: // 其他类型
        case VOID: // void类型
          throw new AssertionError("wtf?!"); // 不应该出现，抛出断言错误
        default: // 其他基本类型（byte, char, short, int, long, boolean）
          break; // 继续处理
        }
        Comparable min = this.min; // 获取最小值
        Comparable max = this.max; // 获取最大值
        if (canBeLong(min) && canBeLong(max)) { // 如果最小值和最大值都可以转换为long
          return chooseFixedRep( // 调用chooseFixedRep选择固定精度表示
              ordinal, p, toLong(min), toLong(max)); // 传递序号、类型、最小值、最大值
        }
      }

      // We don't want to use a dictionary if:
      // 我们不想使用字典编码，如果：
      // (a) there are so many values that an object pointer (with one
      //     indirection) has about as many bits as a code (with two
      //     indirections); or
      // (a) 有太多值，使得对象指针（一次间接引用）的位数与代码（两次间接引用）的位数差不多；或者
      // (b) if there are very few copies of each value.
      // (b) 每个值的副本很少。
      // The condition kind of captures this, but needs to be tuned.
      // 这个条件大致捕捉了这一点，但需要调整。
      final int codeCount = map.size() + (containsNull ? 1 : 0); // 计算代码数量（基数）
      final int codeBitCount = log2(nextPowerOf2(codeCount)); // 计算需要的位数
      if (codeBitCount < 10 && values.size() > 2000) { // 如果代码位数小于10且数据量大于2000
        final ArrayTable.Representation representation = // 创建固定精度表示
            chooseFixedRep(-1, Primitive.INT, 0, codeCount - 1); // 使用int类型存储代码
        return new ArrayTable.ObjectDictionary(ordinal, representation); // 返回字典编码表示
      }
      return new ArrayTable.ObjectArray(ordinal); // 默认使用对象数组存储
    }

    /**
     * 将对象转换为long值
     * 处理Boolean和Character（它们不是Number的子类但可以转换为数字）
     *
     * @param o 要转换的对象
     * @return long值
     */
    private static long toLong(Object o) { // 转换为long
      // We treat Boolean and Character as if they were subclasses of
      // Number but actually they are not.
      // 我们将Boolean和Character视为Number的子类，但实际上它们不是。
      if (o instanceof Boolean) { // 如果是Boolean类型
        return (Boolean) o ? 1 : 0; // true转为1，false转为0
      } else if (o instanceof Character) { // 如果是Character类型
        return (long) (Character) o; // 转换为字符的Unicode值
      } else { // 其他类型（应该是Number）
        return ((Number) o).longValue(); // 转换为long值
      }
    }

    /**
     * 检查对象是否可以转换为long
     * Boolean、Character和Number都可以转换为long
     *
     * @param o 要检查的对象
     * @return 如果可以转换为long返回true
     */
    @EnsuresNonNullIf(result = true, expression = "#1") // 注解：如果返回true则参数不为null
    private static boolean canBeLong(@Nullable Object o) { // 检查是否可以转换为long
      return o instanceof Boolean // Boolean可以转换
          || o instanceof Character // Character可以转换
          || o instanceof Number; // Number可以转换
    }

    /**
     * Chooses a representation for a fixed-precision primitive type
     * (boolean, byte, char, short, int, long).
     * 
     * 为固定精度的原始类型（boolean, byte, char, short, int, long）选择表示。
     * 
     * 这个方法根据值的范围选择最优的存储方式：
     * 1. 如果所有值都相同，使用常量表示（Constant）
     * 2. 如果值范围小，使用位切片存储（BitSlicedPrimitiveArray）
     * 3. 如果值范围适合8位，使用byte数组
     * 4. 如果值范围适合16位，使用short数组
     * 5. 如果值范围适合32位，使用int数组
     * 6. 如果值范围适合64位，使用long数组
     *
     * @param ordinal Ordinal of this column in table - 列在表中的序号
     * @param p Type that values are to be returned as (not necessarily the
     *     same as they will be stored) - 值要返回的类型（不一定与存储类型相同）
     * @param min Minimum value to be encoded - 要编码的最小值
     * @param max Maximum value to be encoded (inclusive) - 要编码的最大值（包含）
     */
    private static ArrayTable.Representation chooseFixedRep( // 选择固定精度表示
        int ordinal, Primitive p, long min, long max) { // 参数：序号、类型、最小值、最大值
      if (min == max) { // 如果最小值等于最大值（所有值相同）
        return new ArrayTable.Constant(ordinal); // 使用常量表示
      }
      final int bitCountMax = log2(nextPowerOf2(abs2(max) + 1)); // 计算最大值需要的位数
      int bitCount; // 最终需要的位数
      boolean signed; // 是否需要符号位

      if (min >= 0) { // 如果最小值非负
        signed = false; // 不需要符号位
        bitCount = bitCountMax; // 使用最大值需要的位数
      } else { // 如果最小值为负
        signed = true; // 需要符号位
        int bitCountMin = log2(nextPowerOf2(abs2(min) + 1)); // 计算最小值绝对值需要的位数
        bitCount = Math.max(bitCountMin, bitCountMax) + 1; // 取最大值并加1（符号位）
      }

      // Must be a fixed point primitive.
      // 必须是定点基本类型。
      if (bitCount > 21 && bitCount < 32) { // 如果位数在21到32之间
        // Can't get more than 2 into a word.
        // 不能在一个字中放入超过2个。
        signed = true; // 使用有符号
        bitCount = 32; // 使用32位
      }
      if (bitCount >= 33 && bitCount < 64) { // 如果位数在33到64之间
        // Can't get more than one into a word.
        // 不能在一个字中放入超过1个。
        signed = true; // 使用有符号
        bitCount = 64; // 使用64位
      }
      if (signed) { // 如果需要符号位
        switch (bitCount) { // 根据位数选择
        case 8: // 8位
          return new ArrayTable.PrimitiveArray( // 使用byte数组
              ordinal, Primitive.BYTE, p); // 存储为byte，返回为p类型
        case 16: // 16位
          return new ArrayTable.PrimitiveArray( // 使用short数组
              ordinal, Primitive.SHORT, p); // 存储为short，返回为p类型
        case 32: // 32位
          return new ArrayTable.PrimitiveArray( // 使用int数组
              ordinal, Primitive.INT, p); // 存储为int，返回为p类型
        case 64: // 64位
          return new ArrayTable.PrimitiveArray( // 使用long数组
              ordinal, Primitive.LONG, p); // 存储为long，返回为p类型
        default: // 其他位数
          break; // 跳过
        }
      }
      // 使用位切片数组存储（位数不是8/16/32/64的情况）
      return new ArrayTable.BitSlicedPrimitiveArray( // 位切片原始数组
          ordinal, bitCount, p, signed); // 参数：序号、位数、返回类型、是否有符号
    }

    /**
     * Two's complement absolute on int value.
     * 计算int值的二进制补码绝对值
     * 
     * 例如：abs2(-128) = 127（因为-128的二进制补码的绝对值是127）
     * 这是处理二进制补码的特殊情况，因为int的范围是-2^31到2^31-1，
     * 所以-2^31的绝对值2^31无法用正int表示，需要特殊处理
     *
     * @param v 输入值
     * @return 二进制补码绝对值
     */
    @SuppressWarnings("unused") // 抑制未使用警告（虽然当前未使用，但保留供将来使用）
    private static int abs2(int v) { // int类型的二进制补码绝对值
      // -128 becomes +127
      // -128变成+127
      return v < 0 ? ~v : v; // 如果为负，返回按位取反（相当于绝对值-1）
    }

    /**
     * Two's complement absolute on long value.
     * 计算long值的二进制补码绝对值
     * 
     * 与int版本类似，但处理64位long类型
     *
     * @param v 输入值
     * @return 二进制补码绝对值
     */
    private static long abs2(long v) { // long类型的二进制补码绝对值
      // -128 becomes +127
      // -128变成+127
      return v < 0 ? ~v : v; // 如果为负，返回按位取反
    }
  }

  /**
   * Key-value pair.
   * 键值对，用于排序
   * 
   * Kev是ColumnLoader的私有内部类，用于在检测唯一键时进行排序。
   * 它存储了原始索引和对应的值，实现Comparable接口以便排序。
   * 
   * 使用场景：
   * 当检测到某个列的所有值都是唯一时，需要对该列进行排序，
   * Kev用于记录每个值在原始数据中的位置，以便后续对其他列进行相应的排列。
   */
  private static class Kev implements Comparable<Kev> { // 键值对内部类，实现Comparable接口
    private final int source; // 原始索引，表示值在原始数据中的位置
    private final Comparable key; // 键值，用于排序比较

    /**
     * 构造方法
     *
     * @param source 原始索引
     * @param key 键值
     */
    Kev(int source, Comparable key) { // 构造方法
      this.source = source; // 保存原始索引
      this.key = key; // 保存键值
    }

    /**
     * 比较方法，用于排序
     * 按键值进行比较
     *
     * @param o 另一个Kev对象
     * @return 比较结果
     */
    @Override public int compareTo(Kev o) { // 实现compareTo方法
      //noinspection unchecked // 抑制未检查的转换警告
      return key.compareTo(o.key); // 比较键值
    }
  }
}