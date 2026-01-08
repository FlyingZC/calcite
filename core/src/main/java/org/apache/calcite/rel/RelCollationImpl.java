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
// Apache Calcite是一个动态数据管理框架，提供SQL解析、优化、执行等功能
// 本文件定义了RelCollation接口的实现类，用于表示关系代数操作符的排序属性
package org.apache.calcite.rel;  // 声明包名，org.apache.calcite.rel包包含了关系代数相关的核心类

import org.apache.calcite.plan.RelMultipleTrait;  // 导入RelMultipleTrait接口，表示可以有多种值的RelTrait
import org.apache.calcite.plan.RelOptPlanner;  // 导入RelOptPlanner接口，表示查询优化器
import org.apache.calcite.plan.RelTrait;  // 导入RelTrait接口，表示关系操作符的物理属性
import org.apache.calcite.plan.RelTraitDef;  // 导入RelTraitDef接口，表示RelTrait的定义
import org.apache.calcite.rel.type.RelDataType;  // 导入RelDataType接口，表示关系数据类型
import org.apache.calcite.rex.RexUtil;  // 导入RexUtil工具类，提供行表达式相关的实用方法
import org.apache.calcite.runtime.Utilities;  // 导入Utilities工具类，提供通用的实用方法
import org.apache.calcite.util.Util;  // 导入Util工具类，提供各种实用方法
import org.apache.calcite.util.mapping.Mappings;  // 导入Mappings工具类，提供映射相关的功能

import com.google.common.collect.ImmutableList;  // 导入Google Guava的ImmutableList，表示不可变列表
import com.google.common.collect.UnmodifiableIterator;  // 导入Google Guava的UnmodifiableIterator，表示不可修改的迭代器

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入Nullable注解，用于标记可能为null的参数

import java.util.Iterator;  // 导入Java标准库的Iterator接口
import java.util.List;  // 导入Java标准库的List接口

import static com.google.common.base.Preconditions.checkArgument;  // 导入Preconditions的checkArgument静态方法，用于参数校验

/**
 * Simple implementation of {@link RelCollation}.
 * RelCollationImpl是RelCollation接口的简单实现类，用于表示关系操作符的排序属性
 * 排序属性指定了数据按照哪些字段、以什么方向（升序或降序）进行排序
 * 
 * 核心功能：
 * 1. 存储字段排序信息（字段索引、排序方向、NULL值处理方式）
 * 2. 提供排序属性的比较、验证、转换等操作
 * 3. 支持排序属性的映射（在投影等操作中字段索引会变化）
 * 4. 判断一个排序属性是否满足另一个排序属性（前缀匹配）
 * 
 * 使用场景：
 * - 在查询优化过程中，优化器需要知道数据的排序情况以便选择最优的执行计划
 * - 例如：如果数据已经按照某个字段排序，那么可以直接使用归并排序算法而不是重新排序
 * - 在Sort、Aggregate、Join等操作中，排序属性是重要的物理属性
 */
public class RelCollationImpl implements RelCollation {  // RelCollationImpl类实现了RelCollation接口
  //~ Static fields/initializers ---------------------------------------------
  // 静态字段和初始化块区域标记，用于组织代码结构

  @Deprecated // to be removed before 2.0  // 标记为已废弃，将在2.0版本前移除
  public static final RelCollation EMPTY = RelCollations.EMPTY;  // 空排序常量，表示没有任何排序要求，已废弃，建议使用RelCollations.EMPTY

  @Deprecated // to be removed before 2.0  // 标记为已废弃，将在2.0版本前移除
  public static final RelCollation PRESERVE = RelCollations.PRESERVE;  // 保持排序常量，表示保持原有的排序，已废弃，建议使用RelCollations.PRESERVE

  //~ Instance fields --------------------------------------------------------
  // 实例字段区域标记，用于组织代码结构

  private final ImmutableList<RelFieldCollation> fieldCollations;  // 字段排序列表，不可变的RelFieldCollation列表，每个RelFieldCollation表示一个字段的排序方式（包括字段索引、排序方向、NULL值处理方向）

  //~ Constructors -----------------------------------------------------------
  // 构造方法区域标记，用于组织代码结构

  protected RelCollationImpl(ImmutableList<RelFieldCollation> fieldCollations) {  // 受保护的构造方法，接收一个不可变的RelFieldCollation列表作为参数
    this.fieldCollations = fieldCollations;  // 将传入的字段排序列表赋值给实例变量
    checkArgument(Util.isDistinct(RelCollations.ordinals(fieldCollations)),  // 校验参数：确保字段索引是唯一的，不允许重复的字段索引
        "fields must be distinct");  // 如果字段索引不唯一，抛出IllegalArgumentException异常，提示"fields must be distinct"
  }

  @Deprecated // to be removed before 2.0  // 标记为已废弃，将在2.0版本前移除
  public static RelCollation of(RelFieldCollation... fieldCollations) {  // 静态工厂方法，通过可变参数创建RelCollation对象
    return RelCollations.of(fieldCollations);  // 委托给RelCollations.of方法创建RelCollation对象
  }

  @Deprecated // to be removed before 2.0  // 标记为已废弃，将在2.0版本前移除
  public static RelCollation of(List<RelFieldCollation> fieldCollations) {  // 静态工厂方法，通过List创建RelCollation对象
    return RelCollations.of(fieldCollations);  // 委托给RelCollations.of方法创建RelCollation对象
  }

  //~ Methods ----------------------------------------------------------------
  // 方法区域标记，用于组织代码结构

  @Override public RelTraitDef getTraitDef() {  // 重写getTraitDef方法，返回此RelTrait的定义
    return RelCollationTraitDef.INSTANCE;  // 返回RelCollationTraitDef的单例实例，这是RelCollation的定义类
  }

  @Override public List<RelFieldCollation> getFieldCollations() {  // 重写getFieldCollations方法，获取字段排序列表
    return fieldCollations;  // 返回存储的字段排序列表
  }

  @Override public int hashCode() {  // 重写hashCode方法，计算对象的哈希值
    return fieldCollations.hashCode();  // 直接返回字段排序列表的哈希值，确保相同的排序属性具有相同的哈希值
  }

  @Override public boolean equals(@Nullable Object obj) {  // 重写equals方法，判断两个对象是否相等
    if (this == obj) {  // 如果是同一个对象引用
      return true;  // 直接返回true
    }
    if (obj instanceof RelCollationImpl) {  // 如果obj是RelCollationImpl类型的实例
      RelCollationImpl that = (RelCollationImpl) obj;  // 将obj强制转换为RelCollationImpl类型
      return this.fieldCollations.equals(that.fieldCollations);  // 比较两个对象的字段排序列表是否相等
    }
    return false;  // 如果不是同一类型，返回false
  }

  @Override public boolean isTop() {  // 重写isTop方法，判断是否为顶层排序（空排序）
    return fieldCollations.isEmpty();  // 如果字段排序列表为空，返回true，表示没有排序要求
  }

  @Override public int compareTo(RelMultipleTrait o) {  // 重写compareTo方法，比较两个RelCollationImpl对象的大小
    final RelCollationImpl that = (RelCollationImpl) o;  // 将参数o强制转换为RelCollationImpl类型
    final UnmodifiableIterator<RelFieldCollation> iterator =  // 创建that对象的字段排序列表的不可修改迭代器
        that.fieldCollations.iterator();  // 获取迭代器
    for (RelFieldCollation f : fieldCollations) {  // 遍历当前对象的字段排序列表
      if (!iterator.hasNext()) {  // 如果that对象的迭代器已经没有下一个元素
        return 1;  // 返回1，表示当前对象的排序比that对象的排序"大"（当前对象有更多字段）
      }
      final RelFieldCollation f2 = iterator.next();  // 获取that对象的下一个字段排序
      int c = Utilities.compare(f.getFieldIndex(), f2.getFieldIndex());  // 比较两个字段的索引
      if (c != 0) {  // 如果字段索引不相等
        return c;  // 返回比较结果
      }
    }
    return iterator.hasNext() ? -1 : 0;  // 如果that对象还有剩余字段，返回-1；否则返回0，表示两个排序相等
  }

  @Override public void register(RelOptPlanner planner) {}  // 重写register方法，向优化器注册此RelTrait，当前实现为空（不需要特殊注册）

  /**
   * Applies mapping to a given collation.
   * 将映射应用到给定的排序属性上
   *
   * <p>If mapping destroys the collation prefix, this method returns an empty
   * collation.  Examples of applying mappings to collation [0, 1]:
   * 如果映射破坏了排序前缀，此方法返回空排序。将映射应用到排序[0, 1]的示例：
   *
   * <ul>
   *   <li>mapping(0, 1) =&gt; [0, 1]</li>  // 字段0和1都保留，排序不变
   *   <li>mapping(1, 0) =&gt; [1, 0]</li>  // 字段0和1交换位置，排序变为[1, 0]
   *   <li>mapping(0) =&gt; [0]</li>  // 只保留字段0，排序变为[0]
   *   <li>mapping(1) =&gt; []</li>  // 只保留字段1，但排序前缀要求字段0在前，所以返回空排序
   *   <li>mapping(2, 0) =&gt; [1]</li>  // 新字段2映射到位置0，新字段0映射到位置1，排序变为[1]（原字段0）
   *   <li>mapping(2, 1, 0) =&gt; [2, 1]</li>  // 新字段2,1,0分别映射到位置0,1,2，排序变为[2, 1]
   *   <li>mapping(2, 1) =&gt; []</li>  // 只保留字段2和1，但排序前缀要求字段0在前，所以返回空排序
   * </ul>
   *
   * @param mapping   Mapping  // 映射对象，表示字段索引的转换关系
   * @return Collation with applied mapping.  // 返回应用映射后的排序属性
   */
  @Override public RelCollationImpl apply(  // 重写apply方法，应用字段映射
      final Mappings.TargetMapping mapping) {  // 接收一个TargetMapping参数，表示目标映射
    return (RelCollationImpl) RexUtil.apply(mapping, this);  // 委托给RexUtil.apply方法应用映射，并将结果强制转换为RelCollationImpl类型
  }

  @Override public boolean satisfies(RelTrait trait) {  // 重写satisfies方法，判断当前排序属性是否满足给定的trait
    return this == trait  // 如果是同一个对象引用，返回true
        || trait instanceof RelCollationImpl  // 或者trait是RelCollationImpl类型
        && Util.startsWith(fieldCollations,  // 并且当前排序的字段列表以trait的字段列表为前缀
            ((RelCollationImpl) trait).fieldCollations);  // 使用Util.startsWith方法检查前缀关系
  }

  /** Returns a string representation of this collation, suitably terse given
   * that it will appear in plan traces. Examples:
   * 返回此排序的字符串表示，简洁地展示排序信息，便于在计划追踪中显示。示例：
   * "[]", "[2]", "[0 DESC, 1]", "[0 DESC, 1 ASC NULLS LAST]".
   * "[]"表示空排序，"[2]"表示按字段2升序，"[0 DESC, 1]"表示按字段0降序、字段1升序，
   * "[0 DESC, 1 ASC NULLS LAST]"表示按字段0降序、字段1升序且NULL值排在最后
   */
  @Override public String toString() {  // 重写toString方法，返回排序的字符串表示
    Iterator<RelFieldCollation> it = fieldCollations.iterator();  // 获取字段排序列表的迭代器
    if (! it.hasNext()) {  // 如果迭代器没有下一个元素（列表为空）
      return "[]";  // 返回空排序的字符串表示"[]"
    }
    StringBuilder sb = new StringBuilder();  // 创建StringBuilder对象用于构建字符串
    sb.append('[');  // 添加左括号
    for (;;) {  // 无限循环，通过break或return退出
      RelFieldCollation e = it.next();  // 获取下一个字段排序
      sb.append(e.getFieldIndex());  // 添加字段索引
      if (e.direction != RelFieldCollation.Direction.ASCENDING  // 如果排序方向不是升序
          || e.nullDirection != e.direction.defaultNullDirection()) {  // 或者NULL值处理方向不是默认方向
        sb.append(' ').append(e.shortString());  // 添加空格和排序方向的简短字符串表示
      }
      if (!it.hasNext()) {  // 如果迭代器没有下一个元素
        return sb.append(']').toString();  // 添加右括号并返回字符串
      }
      sb.append(',').append(' ');  // 添加逗号和空格作为分隔符
    }
  }

  @Deprecated // to be removed before 2.0  // 标记为已废弃，将在2.0版本前移除
  public static List<RelCollation> createSingleton(int fieldIndex) {  // 静态方法，创建包含单个字段排序的列表
    return RelCollations.createSingleton(fieldIndex);  // 委托给RelCollations.createSingleton方法
  }

  @Deprecated // to be removed before 2.0  // 标记为已废弃，将在2.0版本前移除
  public static boolean isValid(  // 静态方法，验证排序列表是否有效
      RelDataType rowType,  // 行类型，用于验证字段索引是否在有效范围内
      List<RelCollation> collationList,  // 要验证的排序列表
      boolean fail) {  // 如果验证失败是否抛出异常
    return RelCollations.isValid(rowType, collationList, fail);  // 委托给RelCollations.isValid方法
  }

  @Deprecated // to be removed before 2.0  // 标记为已废弃，将在2.0版本前移除
  public static boolean equal(  // 静态方法，比较两个排序列表是否相等
      List<RelCollation> collationList1,  // 第一个排序列表
      List<RelCollation> collationList2) {  // 第二个排序列表
    return RelCollations.equal(collationList1, collationList2);  // 委托给RelCollations.equal方法
  }

  @Deprecated // to be removed before 2.0  // 标记为已废弃，将在2.0版本前移除
  public static List<Integer> ordinals(RelCollation collation) {  // 静态方法，获取排序中的所有字段索引
    return RelCollations.ordinals(collation);  // 委托给RelCollations.ordinals方法
  }
}  // 类定义结束
