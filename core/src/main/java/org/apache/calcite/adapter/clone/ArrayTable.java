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
package org.apache.calcite.adapter.clone; // 包声明,表示这个类属于clone适配器包,clone适配器用于创建表的克隆副本

import org.apache.calcite.DataContext; // 导入DataContext接口,提供查询执行时的上下文信息
import org.apache.calcite.adapter.java.AbstractQueryableTable; // 导入抽象可查询表基类,ArrayTable将继承此类
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入抽象可枚举类,用于实现LINQ风格的枚举
import org.apache.calcite.linq4j.Enumerable; // 导入可枚举接口,表示可以枚举的数据集合
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口,用于遍历数据集合
import org.apache.calcite.linq4j.Ord; // 导入Ord工具类,用于给元素添加索引
import org.apache.calcite.linq4j.QueryProvider; // 导入查询提供者接口,用于执行LINQ查询
import org.apache.calcite.linq4j.Queryable; // 导入可查询接口,表示可以执行查询的数据集合
import org.apache.calcite.linq4j.tree.Primitive; // 导入Primitive工具类,用于处理基本类型
import org.apache.calcite.rel.RelCollation; // 导入RelCollation类,表示排序规则
import org.apache.calcite.rel.RelCollations; // 导入RelCollations工具类,用于创建排序规则
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口,表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口,用于创建关系数据类型
import org.apache.calcite.rel.type.RelProtoDataType; // 导入RelProtoDataType接口,表示关系数据类型的原型
import org.apache.calcite.schema.ScannableTable; // 导入ScannableTable接口,表示可以扫描的表
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口,表示Calcite模式
import org.apache.calcite.schema.Statistic; // 导入Statistic接口,表示表的统计信息
import org.apache.calcite.schema.Statistics; // 导入Statistics工具类,用于创建统计信息
import org.apache.calcite.schema.impl.AbstractTableQueryable; // 导入抽象表可查询类,用于实现表的可查询功能
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类,表示不可变的位集合,用于表示列索引
import org.apache.calcite.util.Pair; // 导入Pair工具类,用于存储键值对

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList,表示不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解,表示可能为null的值

import java.lang.reflect.Array; // 导入Array反射工具类,用于操作数组
import java.lang.reflect.Type; // 导入Type接口,表示Java类型
import java.util.AbstractList; // 导入AbstractList抽象类,用于实现自定义列表
import java.util.ArrayList; // 导入ArrayList类,表示动态数组
import java.util.Arrays; // 导入Arrays工具类,用于操作数组
import java.util.Collections; // 导入Collections工具类,用于操作集合
import java.util.List; // 导入List接口,表示列表
import java.util.function.Supplier; // 导入Supplier函数式接口,用于延迟加载

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法,用于检查非空

/**
 * Implementation of table that reads rows from column stores, one per column.
 * 实现从列式存储读取行的表,每列一个存储。列式存储格式根据列中值的类型和分布进行选择;
 * see {@link Representation} and {@link RepresentationType}.
 * 参见Representation和RepresentationType接口/枚举,了解不同的列存储表示方式
 */
class ArrayTable extends AbstractQueryableTable implements ScannableTable { // ArrayTable类定义,继承AbstractQueryableTable并实现ScannableTable接口,表示基于列式存储的表
  private final RelProtoDataType protoRowType; // 行数据类型的原型,用于延迟创建实际的行数据类型
  private final Supplier<Content> supplier; // Content对象的提供者,使用Supplier实现延迟加载,Content包含表的实际数据

  /** Creates an ArrayTable.
   * 创建一个ArrayTable实例
   *
   * @param elementType 元素类型,通常是Object[].class,表示每行是一个对象数组
   * @param protoRowType 行数据类型的原型,用于描述表的行结构
   * @param supplier Content对象的提供者,用于延迟加载表的实际数据
   */
  ArrayTable(Type elementType, RelProtoDataType protoRowType, // 构造方法,接收元素类型、行类型原型和内容提供者
      Supplier<Content> supplier) {
    super(elementType); // 调用父类AbstractQueryableTable的构造方法,传入元素类型
    this.protoRowType = protoRowType; // 保存行类型原型
    this.supplier = supplier; // 保存内容提供者
  }

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法,返回表的行数据类型
    return protoRowType.apply(typeFactory); // 使用类型工厂将原型转换为实际的行数据类型并返回
  }

  @Override public Statistic getStatistic() { // 重写getStatistic方法,返回表的统计信息
    final List<ImmutableBitSet> keys = new ArrayList<>(); // 创建键列表,用于存储所有唯一列的位集合
    final Content content = supplier.get(); // 获取表的内容对象,触发延迟加载
    for (Ord<Column> ord : Ord.zip(content.columns)) { // 遍历所有列,Ord.zip为每列添加索引
      if (ord.e.cardinality == content.size) { // 如果某列的基数(不同值的数量)等于表的总行数
        keys.add(ImmutableBitSet.of(ord.i)); // 说明该列的所有值都唯一,将其作为键添加到键列表中
      }
    }
    return Statistics.of(content.size, keys, content.collations); // 创建并返回统计信息对象,包含行数、键列表和排序规则
  }

  @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法,扫描表数据并返回可枚举的对象数组集合
    return new AbstractEnumerable<@Nullable Object[]>() { // 创建并返回一个抽象可枚举对象
      @Override public Enumerator<@Nullable Object[]> enumerator() { // 重写enumerator方法,返回枚举器
        final Content content = supplier.get(); // 获取表的内容对象
        return content.arrayEnumerator(); // 返回数组枚举器,用于遍历表的每一行
      }
    };
  }

  @Override public <T> Queryable<T> asQueryable(final QueryProvider queryProvider, // 重写asQueryable方法,将表转换为可查询对象
      SchemaPlus schema, String tableName) {
    return new AbstractTableQueryable<T>(queryProvider, schema, this, // 创建并返回抽象表可查询对象
        tableName) {
      @Override public Enumerator<T> enumerator() { // 重写enumerator方法,返回泛型枚举器
        final Content content = supplier.get(); // 获取表的内容对象
        return content.enumerator(); // 返回枚举器,用于遍历表数据
      }
    };
  }

  /** How a column's values are represented.
   * 列值的表示方式枚举,定义了不同的列存储格式,根据数据类型和分布选择最优的存储方式
   */
  enum RepresentationType {
    /** Constant. Contains only one value.
     * 常量表示,只包含一个值,所有行都是相同的值
     *
     * <p>We can't store 0-bit values in
     * an array: we'd have no way of knowing how many there were.
     * 我们无法在数组中存储0位值,因为我们无法知道有多少个这样的值
     *
     * @see Constant 参见Constant类,实现常量列的存储
     */
    CONSTANT, // 常量类型,所有行值相同

    /** Object array. Null values are represented by null. Values may or may
     * not be canonized; if canonized, = and != can be implemented using
     * pointer.
     * 对象数组表示,null值用null表示。值可能被规范化也可能不被规范化;如果被规范化,可以使用指针实现=和!=操作
     *
     * @see ObjectArray 参见ObjectArray类,实现对象数组列的存储
     */
    OBJECT_ARRAY, // 对象数组类型,存储对象引用

    /**
     * Array of primitives. Null values not possible. Only for primitive
     * types (and not optimal for boolean).
     * 基本类型数组表示,不允许null值。只适用于基本类型(对boolean类型不是最优的)
     *
     * @see PrimitiveArray 参见PrimitiveArray类,实现基本类型数组列的存储
     */
    PRIMITIVE_ARRAY, // 基本类型数组类型,存储基本类型值

    /** Bit-sliced primitive array. Values are {@code bitCount} bits each,
     * and interpreted as signed. Stored as an array of long values.
     * 位切片基本类型数组表示,每个值占用bitCount位,解释为有符号数。存储为long数组
     *
     * <p>If gcd(bitCount, 64) != 0, some values will cross boundaries.
     * 如果bitCount和64的最大公约数不为0,有些值会跨越边界
     * bits each. But for all of those values except 4, there is a primitive
     * type (8 byte, 16 short, 32 int) which is more efficient.
     * 每个值的位数。但对于除了4之外的所有值,都有更高效的基本类型(8字节byte, 16位short, 32位int)
     *
     * @see BitSlicedPrimitiveArray 参见BitSlicedPrimitiveArray类,实现位切片列的存储
     */
    BIT_SLICED_PRIMITIVE_ARRAY, // 位切片基本类型数组类型,每个值占用指定位数

    /**
     * Dictionary of primitives. Use one of the previous methods to store
     * unsigned offsets into the dictionary. Dictionary is canonized and
     * sorted, so v1 < v2 if and only if code(v1) < code(v2). The
     * dictionary may or may not contain a null value.
     * 基本类型字典表示,使用前面的方法之一存储字典中的无符号偏移量。字典被规范化和排序,所以v1<v2当且仅当code(v1)<code(v2)。字典可能包含也可能不包含null值
     *
     * <p>The dictionary is not beneficial unless the codes are
     * significantly shorter than the values. A column of {@code long}
     * values with many duplicates is a win; a column of mostly distinct
     * {@code short} values is likely a loss. The other win is if there are
     * null values; otherwise the best option would be an
     * {@link #OBJECT_ARRAY}.
     * 除非编码显著短于值,否则字典没有优势。有很多重复值的long列是优势;大多数不同的short列可能是劣势。另一个优势是有null值;否则最好的选择是OBJECT_ARRAY
     *
     * @see PrimitiveDictionary 参见PrimitiveDictionary类,实现基本类型字典列的存储
     */
    PRIMITIVE_DICTIONARY, // 基本类型字典类型,使用字典编码存储重复值

    /**
     * Dictionary of objects. Use one of the previous methods to store
     * unsigned offsets into the dictionary.
     * 对象字典表示,使用前面的方法之一存储字典中的无符号偏移量
     *
     * @see ObjectDictionary 参见ObjectDictionary类,实现对象字典列的存储
     */
    OBJECT_DICTIONARY, // 对象字典类型,使用字典编码存储对象值

    /**
     * Compressed string table. Block of char data. Strings represented
     * using an unsigned offset into the table (stored using one of the
     * previous methods).
     * 压缩字符串表表示,字符数据块。字符串使用表中的无符号偏移量表示(使用前面的方法之一存储)
     *
     * <p>First 2 bytes are unsigned length; subsequent bytes are string
     * contents. The null value, strings longer than 64k and strings that
     * occur very commonly are held in an 'exceptions' array and are
     * recognized by their high offsets. Other strings are created on demand
     * (this reduces the number of objects that need to be created during
     * deserialization from cache.
     * 前2字节是无符号长度;后续字节是字符串内容。null值、长度超过64k的字符串和非常常见的字符串保存在'exceptions'数组中,通过高位偏移量识别。其他字符串按需创建(这减少了从缓存反序列化期间需要创建的对象数量)
     *
     * @see StringDictionary 参见StringDictionary类,实现字符串字典列的存储
     */
    STRING_DICTIONARY, // 字符串字典类型,压缩存储字符串

    /**
     * Compressed byte array table. Similar to compressed string table.
     * 压缩字节数组表表示,类似于压缩字符串表
     *
     * @see ByteStringDictionary 参见ByteStringDictionary类,实现字节串字典列的存储
     */
    BYTE_STRING_DICTIONARY, // 字节串字典类型,压缩存储字节串
  }

  /** Column definition and value set.
   * 列定义和值集合类,表示表中的一列,包含列的表示方式、数据集和基数
   */
  public static class Column { // Column类定义,表示表中的一列
    final Representation representation; // 列的表示方式,决定了如何存储和访问列数据
    final Object dataSet; // 列的数据集,实际存储的列数据,具体格式取决于representation
    final int cardinality; // 列的基数,即列中不同值的数量

    /**
     * Creates a Column.
     * 创建一个列对象
     *
     * @param representation 列的表示方式
     * @param data 列的数据集
     * @param cardinality 列的基数
     */
    Column(Representation representation, Object data, int cardinality) { // Column构造方法
      this.representation = representation; // 保存列的表示方式
      this.dataSet = data; // 保存列的数据集
      this.cardinality = cardinality; // 保存列的基数
    }

    /**
     * Returns a column that is the same as this but with its data
     * permuted according to a mapping.
     * 返回一个与当前列相同但数据根据映射重新排列的新列
     *
     * @param sources 源索引数组,sources[i]表示新列的第i行应该从原列的哪一行获取
     * @return 重新排列后的新列
     */
    public Column permute(int[] sources) { // permute方法,根据索引映射重新排列列数据
      return new Column( // 创建新的Column对象
          representation, // 保持相同的表示方式
          representation.permute(dataSet, sources), // 使用representation的permute方法重新排列数据
          cardinality); // 保持相同的基数
    }

    @Override public String toString() { // 重写toString方法,返回列的字符串表示
      return "Column(representation=" + representation // 返回包含表示方式和值的字符串
          + ", value=" + representation.toString(dataSet) + ")"; // 使用representation的toString方法获取数据集的字符串表示
    }

    /** Returns a list view onto a data set.
     * 返回数据集的列表视图,将列数据包装为List接口以便访问
     *
     * @param representation 列的表示方式
     * @param dataSet 列的数据集
     * @return 列表视图
     */
    public static List asList(final Representation representation, // asList静态方法,创建数据集的列表视图
        final Object dataSet) {
      // Cache size. It might be expensive to compute.
      // 缓存大小,计算大小可能很昂贵
      final int size = representation.size(dataSet); // 获取数据集的大小
      return new AbstractList() { // 返回一个抽象列表,提供对数据集的随机访问
        @Override public @Nullable Object get(int index) { // 重写get方法,获取指定索引的值
          return representation.getObject(dataSet, index); // 使用representation的getObject方法获取值
        }

        @Override public int size() { // 重写size方法,返回列表大小
          return size; // 返回缓存的大小
        }
      };
    }
  }

  /** Representation of the values of a column.
   * 列值的表示接口,定义了如何存储、访问和操作列数据的标准方法
   */
  public interface Representation { // Representation接口定义
    /** Returns the representation type.
     * 返回表示方式的类型
     *
     * @return 表示类型枚举值
     */
    RepresentationType getType(); // getType方法,返回表示类型

    /** Converts a value set into a compact representation. If
     * {@code sources} is not null, permutes.
     * 将值集合转换为紧凑的表示形式。如果sources不为null,则重新排列
     *
     * @param valueSet 值集合,包含列的所有值
     * @param sources 源索引数组,用于重新排列,如果为null则不重新排列
     * @return 紧凑的数据集
     */
    Object freeze(ColumnLoader.ValueSet valueSet, int @Nullable [] sources); // freeze方法,将值集合冻结为紧凑的数据集

    /**
     * Gets the value at a given ordinal.
     * 获取指定序号的值
     *
     * @param dataSet 数据集
     * @param ordinal 序号(行索引)
     * @return 值,可能为null
     */
    @Nullable Object getObject(Object dataSet, int ordinal); // getObject方法,获取指定序号的对象值

    /**
     * Gets the int value at a given ordinal.
     * 获取指定序号的int值
     *
     * @param dataSet 数据集
     * @param ordinal 序号(行索引)
     * @return int值
     */
    int getInt(Object dataSet, int ordinal); // getInt方法,获取指定序号的int值

    /** Creates a data set that is the same as a given data set
     * but re-ordered.
     * 创建一个与给定数据集相同但重新排序的数据集
     *
     * @param dataSet 原始数据集
     * @param sources 源索引数组,sources[i]表示新数据集的第i行应该从原始数据集的哪一行获取
     * @return 重新排序后的数据集
     */
    Object permute(Object dataSet, int[] sources); // permute方法,重新排列数据集

    /** Returns the number of elements in a data set. (Some representations
     * return the capacity, which may be slightly larger than the actual
     * size.)
     * 返回数据集中的元素数量(某些表示返回容量,可能略大于实际大小)
     *
     * @param dataSet 数据集
     * @return 元素数量
     */
    int size(Object dataSet); // size方法,返回数据集的大小

    /** Converts a data set to a string.
     * 将数据集转换为字符串
     *
     * @param dataSet 数据集
     * @return 字符串表示
     */
    String toString(Object dataSet); // toString方法,返回数据集的字符串表示
  }

  /** Representation that stores the column values in an array.
   * 将列值存储在数组中的表示方式,使用对象数组存储列数据
   */
  public static class ObjectArray implements Representation { // ObjectArray类,实现对象数组表示
    final int ordinal; // 列的序号,表示这是第几列

    /**
     * Creates an ObjectArray.
     * 创建一个对象数组表示
     *
     * @param ordinal 列序号
     */
    ObjectArray(int ordinal) { // ObjectArray构造方法
      this.ordinal = ordinal; // 保存列序号
    }

    @Override public String toString() { // 重写toString方法
      return "ObjectArray(ordinal=" + ordinal + ")"; // 返回包含列序号的字符串
    }

    @Override public RepresentationType getType() { // 重写getType方法
      return RepresentationType.OBJECT_ARRAY; // 返回对象数组类型
    }

    @Override public Object freeze(ColumnLoader.ValueSet valueSet, int @Nullable [] sources) { // 重写freeze方法
      // We assume the values have been canonized.
      // 我们假设值已经被规范化(相同的对象引用指向相同的值)
      final List<Comparable> list = permuteList(valueSet.values, sources); // 重新排列值列表
      return list.toArray(new Comparable[0]); // 将列表转换为Comparable数组并返回
    }

    @Override public Object permute(Object dataSet, int[] sources) { // 重写permute方法
      @Nullable Comparable[] list = (@Nullable Comparable[]) dataSet; // 将数据集转换为Comparable数组
      final int size = list.length; // 获取数组长度
      final @Nullable Comparable[] comparables = new Comparable[size]; // 创建新的Comparable数组
      for (int i = 0; i < size; i++) { // 遍历所有元素
        comparables[i] = list[sources[i]]; // 根据sources数组重新排列元素
      }
      return comparables; // 返回重新排列后的数组
    }

    @Override public @Nullable Object getObject(Object dataSet, int ordinal) { // 重写getObject方法
      return ((@Nullable Comparable[]) dataSet)[ordinal]; // 返回指定序号的Comparable对象
    }

    @Override public int getInt(Object dataSet, int ordinal) { // 重写getInt方法
      Number value = (Number) getObject(dataSet, ordinal); // 获取对象并转换为Number
      return requireNonNull(value, "value").intValue(); // 返回int值,检查value不为null
    }

    @Override public int size(Object dataSet) { // 重写size方法
      return ((Comparable[]) dataSet).length; // 返回数组长度
    }

    @Override public String toString(Object dataSet) { // 重写toString方法
      return Arrays.toString((Comparable[]) dataSet); // 返回数组的字符串表示
    }
  }

  /** Representation that stores the values of a column in an array of
   * primitive values.
   * 将列值存储在基本类型数组中的表示方式,使用基本类型数组(如int[], long[]等)存储列数据
   */
  public static class PrimitiveArray implements Representation { // PrimitiveArray类,实现基本类型数组表示
    final int ordinal; // 列的序号
    private final Primitive primitive; // 基本类型包装器,用于操作基本类型数组
    private final Primitive p; // 另一个基本类型包装器,用于特定操作(可能用于不同目的)

    /**
     * Creates a PrimitiveArray.
     * 创建一个基本类型数组表示
     *
     * @param ordinal 列序号
     * @param primitive 基本类型包装器,用于toArray2操作
     * @param p 基本类型包装器,用于arrayItem和arrayToString操作
     */
    PrimitiveArray(int ordinal, Primitive primitive, Primitive p) { // PrimitiveArray构造方法
      this.ordinal = ordinal; // 保存列序号
      this.primitive = primitive; // 保存第一个基本类型包装器
      this.p = p; // 保存第二个基本类型包装器
    }

    @Override public String toString() { // 重写toString方法
      return "PrimitiveArray(ordinal=" + ordinal // 返回包含列序号和基本类型的字符串
          + ", primitive=" + primitive
          + ", p=" + p
          + ")";
    }

    @Override public RepresentationType getType() { // 重写getType方法
      return RepresentationType.PRIMITIVE_ARRAY; // 返回基本类型数组类型
    }

    @Override public Object freeze(ColumnLoader.ValueSet valueSet, int @Nullable [] sources) { // 重写freeze方法
      //noinspection unchecked
      // 使用primitive的toArray2方法将列表转换为基本类型数组,并重新排列
      return primitive.toArray2( // 调用toArray2方法转换列表
          permuteList((List) valueSet.values, sources)); // 重新排列值列表
    }

    @Override public Object permute(Object dataSet, int[] sources) { // 重写permute方法
      return primitive.permute(dataSet, sources); // 使用primitive的permute方法重新排列数据集
    }

    @Override public @Nullable Object getObject(Object dataSet, int ordinal) { // 重写getObject方法
      return p.arrayItem(dataSet, ordinal); // 使用p的arrayItem方法获取指定序号的元素
    }

    @Override public int getInt(Object dataSet, int ordinal) { // 重写getInt方法
      return Array.getInt(dataSet, ordinal); // 使用反射Array.getInt方法获取int值
    }

    @Override public int size(Object dataSet) { // 重写size方法
      return Array.getLength(dataSet); // 使用反射Array.getLength方法获取数组长度
    }

    @Override public String toString(Object dataSet) { // 重写toString方法
      return p.arrayToString(dataSet); // 使用p的arrayToString方法返回数组的字符串表示
    }
  }

  /** Representation that stores column values in a dictionary of
   * primitive values, then uses a short code for each row.
   * 将列值存储在基本类型字典中,然后为每行使用短代码的表示方式
   */
  public static class PrimitiveDictionary implements Representation { // PrimitiveDictionary类,实现基本类型字典表示
    PrimitiveDictionary() { // PrimitiveDictionary构造方法
    }

    @Override public String toString() { // 重写toString方法
      return "PrimitiveDictionary()"; // 返回类名字符串
    }

    @Override public RepresentationType getType() { // 重写getType方法
      return RepresentationType.PRIMITIVE_DICTIONARY; // 返回基本类型字典类型
    }

    @Override public Object freeze(ColumnLoader.ValueSet valueSet, int @Nullable [] sources) { // 重写freeze方法
      throw new UnsupportedOperationException(); // TODO: // 抛出未实现异常,标记为待实现
    }

    @Override public Object permute(Object dataSet, int[] sources) { // 重写permute方法
      throw new UnsupportedOperationException(); // TODO: // 抛出未实现异常,标记为待实现
    }

    @Override public Object getObject(Object dataSet, int ordinal) { // 重写getObject方法
      throw new UnsupportedOperationException(); // TODO: // 抛出未实现异常,标记为待实现
    }

    @Override public int getInt(Object dataSet, int ordinal) { // 重写getInt方法
      throw new UnsupportedOperationException(); // TODO: // 抛出未实现异常,标记为待实现
    }

    @Override public int size(Object dataSet) { // 重写size方法
      throw new UnsupportedOperationException(); // TODO: // 抛出未实现异常,标记为待实现
    }

    @Override public String toString(Object dataSet) { // 重写toString方法
      throw new UnsupportedOperationException(); // TODO: // 抛出未实现异常,标记为待实现
    }
  }

  /** Representation that stores the values of a column as a
   * dictionary of objects.
   * 将列值存储为对象字典的表示方式,使用字典编码减少重复值的存储
   */
  public static class ObjectDictionary implements Representation { // ObjectDictionary类,实现对象字典表示
    final int ordinal; // 列的序号
    final Representation representation; // 用于存储字典编码的表示方式(通常是位切片或基本类型数组)

    /**
     * Creates an ObjectDictionary.
     * 创建一个对象字典表示
     *
     * @param ordinal 列序号
     * @param representation 用于存储字典编码的表示方式
     */
    ObjectDictionary( // ObjectDictionary构造方法
        int ordinal,
        Representation representation) {
      this.ordinal = ordinal; // 保存列序号
      this.representation = representation; // 保存编码表示方式
    }

    @Override public String toString() { // 重写toString方法
      return "ObjectDictionary(ordinal=" + ordinal // 返回包含列序号和表示方式的字符串
          + ", representation=" + representation
          + ")";
    }

    @Override public RepresentationType getType() { // 重写getType方法
      return RepresentationType.OBJECT_DICTIONARY; // 返回对象字典类型
    }

    @Override public Object freeze(ColumnLoader.ValueSet valueSet, int @Nullable [] sources) { // 重写freeze方法
      final int n = valueSet.map.keySet().size(); // 获取字典中唯一值的数量
      int extra = valueSet.containsNull ? 1 : 0; // 如果包含null值,额外增加1个位置
      @SuppressWarnings("all") // 抑制所有警告
      @Nullable Comparable[] codeValues = // 创建codeValues数组,存储字典中的所有唯一值
          valueSet.map.keySet().toArray(new Comparable[n + extra]); // 从map的keySet获取所有唯一值
      // codeValues[0..n] is non-null since valueSet.map.keySet is non-null
      // codeValues[0..n]非null,因为valueSet.map.keySet非null
      // There might be null at the very end, however, it won't participate in Arrays.sort
      // 最后可能有null,但它不会参与Arrays.sort
      @SuppressWarnings("assignment.type.incompatible") // 抑制类型不兼容警告
      Comparable[] nonNullCodeValues = codeValues; // 转换为非null的Comparable数组
      Arrays.sort(nonNullCodeValues, 0, n); // 对前n个非null值进行排序,以便后续二分查找
      ColumnLoader.ValueSet codeValueSet = // 创建新的值集合,用于存储编码值
          new ColumnLoader.ValueSet(int.class); // 编码值为int类型
      final List<Comparable> list = permuteList(valueSet.values, sources); // 重新排列原始值列表
      for (Comparable value : list) { // 遍历所有值
        int code; // 编码值
        if (value == null) { // 如果值为null
          code = n; // null的编码为n(最后一个位置)
        } else { // 如果值不为null
          code = Arrays.binarySearch(codeValues, value); // 使用二分查找在codeValues中查找值的索引作为编码
          assert code >= 0 : code + ", " + value; // 断言找到值,code应该>=0
        }
        codeValueSet.add(code); // 将编码添加到编码值集合中
      }
      Object codes = representation.freeze(codeValueSet, null); // 使用representation冻结编码值集合
      return Pair.of(codes, codeValues); // 返回编码数组和字典值的键值对
    }

    /**
     * Unfreezes a data set.
     * 解冻数据集,将Pair对象解包为编码和字典值
     *
     * @param value 冻结的数据集(Pair对象)
     * @return 包含编码和字典值的Pair
     */
    private static Pair<Object, @Nullable Comparable[]> unfreeze(Object value) { // unfreeze静态方法
      return (Pair<Object, @Nullable Comparable[]>) value; // 将对象转换为Pair类型并返回
    }

    @Override public Object permute(Object dataSet, int[] sources) { // 重写permute方法
      final Pair<Object, @Nullable Comparable[]> pair = unfreeze(dataSet); // 解冻数据集获取编码和字典值
      Object codes = pair.left; // 获取编码数组
      @Nullable Comparable[] codeValues = pair.right; // 获取字典值数组
      return Pair.of(representation.permute(codes, sources), codeValues); // 重新排列编码,保持字典值不变,返回新的Pair
    }

    @Override public @Nullable Object getObject(Object dataSet, int ordinal) { // 重写getObject方法
      final Pair<Object, @Nullable Comparable[]> pair = unfreeze(dataSet); // 解冻数据集获取编码和字典值
      int code = representation.getInt(pair.left, ordinal); // 从编码数组中获取指定序号的编码
      return pair.right[code]; // 使用编码从字典值数组中查找并返回实际值
    }

    @Override public int getInt(Object dataSet, int ordinal) { // 重写getInt方法
      Number value = (Number) getObject(dataSet, ordinal); // 获取对象并转换为Number
      return requireNonNull(value, "value").intValue(); // 返回int值,检查value不为null
    }

    @Override public int size(Object dataSet) { // 重写size方法
      final Pair<Object, @Nullable Comparable[]> pair = unfreeze(dataSet); // 解冻数据集获取编码和字典值
      return representation.size(pair.left); // 返回编码数组的大小
    }

    @Override public String toString(Object dataSet) { // 重写toString方法
      return Column.asList(this, dataSet).toString(); // 使用Column.asList方法将数据集转为列表,然后返回字符串表示
    }
  }

  /** Representation that stores string column values.
   * 存储字符串列值的表示方式,使用压缩存储优化字符串列
   */
  public static class StringDictionary implements Representation { // StringDictionary类,实现字符串字典表示
    StringDictionary() { // StringDictionary构造方法
    }

    @Override public String toString() { // 重写toString方法
      return "StringDictionary()"; // 返回类名字符串
    }

    @Override public RepresentationType getType() { // 重写getType方法
      return RepresentationType.STRING_DICTIONARY; // 返回字符串字典类型
    }

    @Override public Object freeze(ColumnLoader.ValueSet valueSet, int @Nullable [] sources) { // 重写freeze方法
      throw new UnsupportedOperationException(); // TODO: // 抛出未实现异常,标记为待实现
    }

    @Override public Object permute(Object dataSet, int[] sources) { // 重写permute方法
      throw new UnsupportedOperationException(); // TODO: // 抛出未实现异常,标记为待实现
    }

    @Override public Object getObject(Object dataSet, int ordinal) { // 重写getObject方法
      throw new UnsupportedOperationException(); // TODO: // 抛出未实现异常,标记为待实现
    }

    @Override public int getInt(Object dataSet, int ordinal) { // 重写getInt方法
      throw new UnsupportedOperationException(); // TODO: // 抛出未实现异常,标记为待实现
    }

    @Override public int size(Object dataSet) { // 重写size方法
      throw new UnsupportedOperationException(); // TODO: // 抛出未实现异常,标记为待实现
    }

    @Override public String toString(Object dataSet) { // 重写toString方法
      return Column.asList(this, dataSet).toString(); // 使用Column.asList方法将数据集转为列表,然后返回字符串表示
    }
  }

  /** Representation that stores byte-string column values.
   * 存储字节串列值的表示方式,使用压缩存储优化字节串列
   */
  public static class ByteStringDictionary implements Representation { // ByteStringDictionary类,实现字节串字典表示
    ByteStringDictionary() { // ByteStringDictionary构造方法
    }

    @Override public String toString() { // 重写toString方法
      return "ByteStringDictionary()"; // 返回类名字符串
    }

    @Override public RepresentationType getType() { // 重写getType方法
      return RepresentationType.BYTE_STRING_DICTIONARY; // 返回字节串字典类型
    }

    @Override public Object freeze(ColumnLoader.ValueSet valueSet, int @Nullable [] sources) { // 重写freeze方法
      throw new UnsupportedOperationException(); // TODO: // 抛出未实现异常,标记为待实现
    }

    @Override public Object permute(Object dataSet, int[] sources) { // 重写permute方法
      throw new UnsupportedOperationException(); // TODO: // 抛出未实现异常,标记为待实现
    }

    @Override public Object getObject(Object dataSet, int ordinal) { // 重写getObject方法
      throw new UnsupportedOperationException(); // TODO: // 抛出未实现异常,标记为待实现
    }

    @Override public int getInt(Object dataSet, int ordinal) { // 重写getInt方法
      throw new UnsupportedOperationException(); // TODO: // 抛出未实现异常,标记为待实现
    }

    @Override public int size(Object dataSet) { // 重写size方法
      throw new UnsupportedOperationException(); // TODO: // 抛出未实现异常,标记为待实现
    }

    @Override public String toString(Object dataSet) { // 重写toString方法
      return Column.asList(this, dataSet).toString(); // 使用Column.asList方法将数据集转为列表,然后返回字符串表示
    }
  }

  /** Representation of a column that has the same value for every row.
   * 每行都有相同值的列的表示方式,常量列优化
   */
  public static class Constant implements Representation { // Constant类,实现常量列表示
    final int ordinal; // 列的序号

    /**
     * Creates a Constant.
     * 创建一个常量列表示
     *
     * @param ordinal 列序号
     */
    Constant(int ordinal) { // Constant构造方法
      this.ordinal = ordinal; // 保存列序号
    }

    @Override public String toString() { // 重写toString方法
      return "Constant(ordinal=" + ordinal + ")"; // 返回包含列序号的字符串
    }

    @Override public RepresentationType getType() { // 重写getType方法
      return RepresentationType.CONSTANT; // 返回常量类型
    }

    @Override public Object freeze(ColumnLoader.ValueSet valueSet, int @Nullable [] sources) { // 重写freeze方法
      final int size = valueSet.values.size(); // 获取值的数量(行数)
      return Pair.of(size == 0 ? null : valueSet.values.get(0), size); // 返回包含常量值和行数的Pair,如果行数为0则常量值为null
    }

    /**
     * Unfreezes a data set.
     * 解冻数据集,将Pair对象解包为常量值和行数
     *
     * @param value 冻结的数据集(Pair对象)
     * @return 包含常量值和行数的Pair
     */
    private static Pair<@Nullable Object, Integer> unfreeze(Object value) { // unfreeze静态方法
      return (Pair<@Nullable Object, Integer>) value; // 将对象转换为Pair类型并返回
    }

    @Override public Object permute(Object dataSet, int[] sources) { // 重写permute方法
      return dataSet; // 常量列不需要重新排列,直接返回原数据集
    }

    @Override public @Nullable Object getObject(Object dataSet, int ordinal) { // 重写getObject方法
      Pair<@Nullable Object, Integer> pair = unfreeze(dataSet); // 解冻数据集获取常量值和行数
      return pair.left; // 返回常量值(忽略ordinal参数,因为所有行的值都相同)
    }

    @Override public int getInt(Object dataSet, int ordinal) { // 重写getInt方法
      @Nullable Number value = (Number) getObject(dataSet, ordinal); // 获取对象并转换为Number
      return requireNonNull(value, "value").intValue(); // 返回int值,检查value不为null
    }

    @Override public int size(Object dataSet) { // 重写size方法
      Pair<@Nullable Object, Integer> pair = unfreeze(dataSet); // 解冻数据集获取常量值和行数
      return pair.right; // 返回行数
    }

    @Override public String toString(Object dataSet) { // 重写toString方法
      Pair<@Nullable Object, Integer> pair = unfreeze(dataSet); // 解冻数据集获取常量值和行数
      return Collections.nCopies(pair.right, pair.left).toString(); // 返回包含pair.right个pair.left值的列表的字符串表示
    }
  }

  /** Representation that stores numeric values in a bit-sliced
   * array. Each value does not necessarily occupy 8, 16, 32 or 64
   * bits (the number of bits used by the built-in types). This
   * representation is often used to store the value codes for a
   * dictionary-based representation.
   * 将数值存储在位切片数组中的表示方式。每个值不一定占用8、16、32或64位(内置类型使用的位数)。这种表示通常用于存储基于字典的表示的值编码
   */
  public static class BitSlicedPrimitiveArray implements Representation { // BitSlicedPrimitiveArray类,实现位切片数组表示
    final int ordinal; // 列的序号
    final int bitCount; // 每个值占用的位数
    final Primitive primitive; // 基本类型包装器,用于确定返回值的类型
    final boolean signed; // 是否为有符号数

    /**
     * Creates a BitSlicedPrimitiveArray.
     * 创建一个位切片数组表示
     *
     * @param ordinal 列序号
     * @param bitCount 每个值占用的位数
     * @param primitive 基本类型包装器
     * @param signed 是否为有符号数
     */
    BitSlicedPrimitiveArray( // BitSlicedPrimitiveArray构造方法
        int ordinal, int bitCount, Primitive primitive, boolean signed) {
      assert bitCount > 0; // 断言bitCount大于0
      this.ordinal = ordinal; // 保存列序号
      this.bitCount = bitCount; // 保存每个值占用的位数
      this.primitive = primitive; // 保存基本类型包装器
      this.signed = signed; // 保存是否有符号标志
    }

    @Override public String toString() { // 重写toString方法
      return "BitSlicedPrimitiveArray(ordinal=" + ordinal // 返回包含列序号、位数、基本类型和是否签名标志的字符串
          + ", bitCount=" + bitCount
          + ", primitive=" + primitive
          + ", signed=" + signed + ")";
    }

    @Override public RepresentationType getType() { // 重写getType方法
      return RepresentationType.BIT_SLICED_PRIMITIVE_ARRAY; // 返回位切片基本类型数组类型
    }

    @Override public Object freeze(ColumnLoader.ValueSet valueSet, int @Nullable [] sources) { // 重写freeze方法
      final int chunksPerWord = 64 / bitCount; // 计算每个long(64位)可以存储多少个值
      final List<@Nullable Comparable> valueList = // 重新排列值列表
          permuteList(valueSet.values, sources);
      final int valueCount = valueList.size(); // 获取值的数量
      final int wordCount = // 计算需要多少个long来存储所有值
          (valueCount + (chunksPerWord - 1)) / chunksPerWord; // 向上取整
      final int remainingChunkCount = valueCount % chunksPerWord; // 计算最后一个long中实际存储的值的数量
      final long[] longs = new long[wordCount]; // 创建long数组
      final int n = valueCount / chunksPerWord; // 计算完整填满的long的数量
      int i; // 循环变量,用于long数组的索引
      int k = 0; // 循环变量,用于valueList的索引
      if (valueCount > 0 // 如果有值
          && valueList.get(0) instanceof Boolean) { // 并且第一个值是Boolean类型
        @SuppressWarnings("unchecked") // 抑制未检查的转换警告
        final List<Boolean> booleans = (List) valueList; // 将valueList转换为Boolean列表
        for (i = 0; i < n; i++) { // 遍历完整填满的long
          long v = 0; // 初始化long值为0
          for (int j = 0; j < chunksPerWord; j++) { // 遍历这个long中的每个值的位置
            v |= booleans.get(k++) ? (1 << (bitCount * j)) : 0; // 如果值为true,设置对应的位;否则保持0
          }
          longs[i] = v; // 将填充好的long存入数组
        }
        if (remainingChunkCount > 0) { // 如果还有剩余的值需要存储
          long v = 0; // 初始化long值为0
          for (int j = 0; j < remainingChunkCount; j++) { // 遍历剩余的值
            v |= booleans.get(k++) ? (1 << (bitCount * j)) : 0; // 如果值为true,设置对应的位;否则保持0
          }
          longs[i] = v; // 将最后一个long存入数组
        }
      } else { // 如果值不是Boolean类型(是Number类型)
        @SuppressWarnings("unchecked") // 抑制未检查的转换警告
        final List<Number> numbers = (List) valueList; // 将valueList转换为Number列表
        for (i = 0; i < n; i++) { // 遍历完整填满的long
          long v = 0; // 初始化long值为0
          for (int j = 0; j < chunksPerWord; j++) { // 遍历这个long中的每个值的位置
            v |= numbers.get(k++).longValue() << (bitCount * j); // 将数值左移到对应位置并合并到long中
          }
          longs[i] = v; // 将填充好的long存入数组
        }
        if (remainingChunkCount > 0) { // 如果还有剩余的值需要存储
          long v = 0; // 初始化long值为0
          for (int j = 0; j < remainingChunkCount; j++) { // 遍历剩余的值
            v |= numbers.get(k++).longValue() << (bitCount * j); // 将数值左移到对应位置并合并到long中
          }
          longs[i] = v; // 将最后一个long存入数组
        }
      }
      return longs; // 返回填充好的long数组
    }

    @Override public Object permute(Object dataSet, int[] sources) { // 重写permute方法
      final long[] longs0 = (long[]) dataSet; // 将数据集转换为long数组
      int n = sources.length; // 获取源索引数组的长度
      final long[] longs = new long[longs0.length]; // 创建新的long数组
      for (int i = 0; i < n; i++) { // 遍历所有源索引
        orLong( // 使用orLong方法将值设置到新数组的对应位置
            bitCount, longs, i, // 传入位数、新数组、目标索引
            getLong(bitCount, longs0, sources[i])); // 从原数组获取指定位置的值
      }
      return longs; // 返回重新排列后的long数组
    }

    @Override public Object getObject(Object dataSet, int ordinal) { // 重写getObject方法
      final long[] longs = (long[]) dataSet; // 将数据集转换为long数组
      final int chunksPerWord = 64 / bitCount; // 计算每个long可以存储多少个值
      final int word = ordinal / chunksPerWord; // 计算值所在的long的索引
      final long v = longs[word]; // 获取对应的long
      final int chunk = ordinal % chunksPerWord; // 计算值在long中的位置
      final int mask = (1 << bitCount) - 1; // 创建掩码,用于提取指定位数
      final int signMask = 1 << (bitCount - 1); // 创建符号掩码,用于判断是否为负数
      final int shift = chunk * bitCount; // 计算需要右移的位数
      final long w = v >> shift; // 右移到最低位
      long x = w & mask; // 使用掩码提取指定位数
      if (signed && (x & signMask) != 0) { // 如果是有符号数且最高位为1(负数)
        x = -x; // 取反(简化处理,实际应该用补码转换)
      }
      switch (primitive) { // 根据基本类型返回对应的值
      case BOOLEAN: // 如果是boolean类型
        return x != 0; // 返回是否非零
      case BYTE: // 如果是byte类型
        return (byte) x; // 转换为byte
      case CHAR: // 如果是char类型
        return (char) x; // 转换为char
      case SHORT: // 如果是short类型
        return (short) x; // 转换为short
      case INT: // 如果是int类型
        return (int) x; // 转换为int
      case LONG: // 如果是long类型
        return x; // 直接返回
      default: // 其他类型
        throw new AssertionError(primitive + " unexpected"); // 抛出断言错误
      }
    }

    @Override public int getInt(Object dataSet, int ordinal) { // 重写getInt方法
      final long[] longs = (long[]) dataSet; // 将数据集转换为long数组
      final int chunksPerWord = 64 / bitCount; // 计算每个long可以存储多少个值
      final int word = ordinal / chunksPerWord; // 计算值所在的long的索引
      final long v = longs[word]; // 获取对应的long
      final int chunk = ordinal % chunksPerWord; // 计算值在long中的位置
      final int mask = (1 << bitCount) - 1; // 创建掩码,用于提取指定位数
      final int signMask = 1 << (bitCount - 1); // 创建符号掩码,用于判断是否为负数
      final int shift = chunk * bitCount; // 计算需要右移的位数
      final long w = v >> shift; // 右移到最低位
      long x = w & mask; // 使用掩码提取指定位数
      if (signed && (x & signMask) != 0) { // 如果是有符号数且最高位为1(负数)
        x = -x; // 取反(简化处理,实际应该用补码转换)
      }
      return (int) x; // 转换为int并返回
    }

    /**
     * Gets a long value from a bit-sliced array.
     * 从位切片数组中获取long值
     *
     * @param bitCount 每个值占用的位数
     * @param values 位切片数组
     * @param ordinal 序号
     * @return long值
     */
    public static long getLong(int bitCount, long[] values, int ordinal) { // getLong静态方法,重载版本
      return getLong( // 调用完整版本的getLong方法
          bitCount, 64 / bitCount, (1L << bitCount) - 1L, // 传入位数、每long的值数、掩码
          values, ordinal); // 传入数组、序号
    }

    /**
     * Gets a long value from a bit-sliced array with explicit parameters.
     * 从位切片数组中获取long值,使用显式参数
     *
     * @param bitCount 每个值占用的位数
     * @param chunksPerWord 每个long可以存储的值的数量
     * @param mask 掩码
     * @param values 位切片数组
     * @param ordinal 序号
     * @return long值
     */
    public static long getLong( // getLong静态方法,完整版本
        int bitCount,
        int chunksPerWord,
        long mask,
        long[] values,
        int ordinal) {
      final int word = ordinal / chunksPerWord; // 计算值所在的long的索引
      final int chunk = ordinal % chunksPerWord; // 计算值在long中的位置
      final long value = values[word]; // 获取对应的long
      final int shift = chunk * bitCount; // 计算需要右移的位数
      return (value >> shift) & mask; // 右移并使用掩码提取指定位数
    }

    /**
     * Sets a long value in a bit-sliced array using OR.
     * 使用OR操作在位切片数组中设置long值
     *
     * @param bitCount 每个值占用的位数
     * @param values 位切片数组
     * @param ordinal 序号
     * @param value 要设置的值
     */
    public static void orLong( // orLong静态方法,重载版本
        int bitCount, long[] values, int ordinal, long value) {
      orLong(bitCount, 64 / bitCount, values, ordinal, value); // 调用完整版本的orLong方法
    }

    /**
     * Sets a long value in a bit-sliced array using OR with explicit parameters.
     * 使用OR操作在位切片数组中设置long值,使用显式参数
     *
     * @param bitCount 每个值占用的位数
     * @param chunksPerWord 每个long可以存储的值的数量
     * @param values 位切片数组
     * @param ordinal 序号
     * @param value 要设置的值
     */
    public static void orLong( // orLong静态方法,完整版本
        int bitCount, int chunksPerWord, long[] values, int ordinal,
        long value) {
      final int word = ordinal / chunksPerWord; // 计算值所在的long的索引
      final int chunk = ordinal % chunksPerWord; // 计算值在long中的位置
      final int shift = chunk * bitCount; // 计算需要左移的位数
      values[word] |= value << shift; // 将值左移到对应位置并使用OR操作合并到long中
    }

    @Override public int size(Object dataSet) { // 重写size方法
      final long[] longs = (long[]) dataSet; // 将数据集转换为long数组
      final int chunksPerWord = 64 / bitCount; // 计算每个long可以存储多少个值
      return longs.length * chunksPerWord; // 返回可能的最大值数(可能略大于实际值数)
    }

    @Override public String toString(Object dataSet) { // 重写toString方法
      return Column.asList(this, dataSet).toString(); // 使用Column.asList方法将数据集转为列表,然后返回字符串表示
    }
  }

  /**
   * Permutes a list according to a source mapping.
   * 根据源映射重新排列列表
   *
   * @param list 原始列表
   * @param sources 源索引数组,sources[i]表示新列表的第i个元素应该从原列表的哪个位置获取
   * @param <E> 列表元素类型
   * @return 重新排列后的列表视图
   */
  private static <E> List<E> permuteList( // permuteList静态方法,用于重新排列列表
      final List<E> list, final int @Nullable [] sources) {
    if (sources == null) { // 如果sources为null
      return list; // 直接返回原列表
    }
    return new AbstractList<E>() { // 返回一个抽象列表,提供重新排列的视图
      @Override public E get(int index) { // 重写get方法
        return list.get(sources[index]); // 根据sources数组获取元素
      }

      @Override public int size() { // 重写size方法
        return list.size(); // 返回原列表的大小
      }
    };
  }

  /** Contents of a table.
   * 表的内容类,包含表的所有列数据、行数和排序规则
   */
  public static class Content { // Content类,表示表的内容
    private final List<Column> columns; // 列列表,包含表的所有列
    private final int size; // 表的行数
    private final ImmutableList<RelCollation> collations; // 排序规则列表,描述表的排序方式

    /**
     * Creates a Content.
     * 创建表内容
     *
     * @param columns 列列表
     * @param size 行数
     * @param collations 排序规则
     */
    Content(List<? extends Column> columns, int size, // Content构造方法
        Iterable<? extends RelCollation> collations) {
      this.columns = ImmutableList.copyOf(columns); // 创建列列表的不可变副本
      this.size = size; // 保存行数
      this.collations = ImmutableList.copyOf(collations); // 创建排序规则的不可变副本
    }

    @Deprecated // to be removed before 2.0 // 标记为已废弃,将在2.0版本前移除
    /**
     * Creates a Content with a single sort field.
     * 创建带有单个排序字段的表内容(已废弃)
     *
     * @param columns 列列表
     * @param size 行数
     * @param sortField 排序字段索引,如果<0表示无排序
     */
    Content(List<? extends Column> columns, int size, int sortField) { // Content构造方法,已废弃
      this(columns, size, // 调用新构造方法
          sortField >= 0 // 如果sortField>=0
              ? RelCollations.createSingleton(sortField) // 创建单个字段的排序规则
              : ImmutableList.of()); // 否则创建空的排序规则列表
    }

    @SuppressWarnings("unchecked") // 抑制未检查的转换警告
    /**
     * Returns an enumerator over the table.
     * 返回表的枚举器,用于遍历表数据
     *
     * @param <T> 枚举器返回的元素类型
     * @return 枚举器
     */
    public <T> Enumerator<T> enumerator() { // enumerator方法,返回泛型枚举器
      if (columns.size() == 1) { // 如果只有一列
        return (Enumerator<T>) new ObjectEnumerator(size, columns.get(0)); // 返回对象枚举器,每个元素是一个对象
      } else { // 如果有多列
        return (Enumerator<T>) new ArrayEnumerator(size, columns); // 返回数组枚举器,每个元素是一个对象数组
      }
    }

    /**
     * Returns an enumerator that returns arrays.
     * 返回返回数组的枚举器
     *
     * @return 数组枚举器
     */
    public Enumerator<@Nullable Object[]> arrayEnumerator() { // arrayEnumerator方法
      return new ArrayEnumerator(size, columns); // 返回数组枚举器,每个元素是一个对象数组
    }

    /** Enumerator over a table with a single column; each element
     * returned is an object.
     * 单列表的枚举器,每个返回的元素是一个对象
     */
    private static class ObjectEnumerator implements Enumerator<@Nullable Object> { // ObjectEnumerator内部类,单列表枚举器
      final int rowCount; // 行数
      final Object dataSet; // 列的数据集
      final Representation representation; // 列的表示方式
      int i = -1; // 当前行索引,初始化为-1表示还未开始

      /**
       * Creates an ObjectEnumerator.
       * 创建对象枚举器
       *
       * @param rowCount 行数
       * @param column 列
       */
      ObjectEnumerator(int rowCount, Column column) { // ObjectEnumerator构造方法
        this.rowCount = rowCount; // 保存行数
        this.dataSet = column.dataSet; // 保存列的数据集
        this.representation = column.representation; // 保存列的表示方式
      }

      @Override public @Nullable Object current() { // 重写current方法,返回当前元素
        return representation.getObject(dataSet, i); // 使用representation的getObject方法获取当前行的值
      }

      @Override public boolean moveNext() { // 重写moveNext方法,移动到下一个元素
        return ++i < rowCount; // 增加行索引并检查是否还有更多行
      }

      @Override public void reset() { // 重写reset方法,重置枚举器
        i = -1; // 重置行索引为-1
      }

      @Override public void close() { // 重写close方法,关闭枚举器
      } // 什么都不做,因为没有需要释放的资源
    }

    /** Enumerator over a table with more than one column; each element
     * returned is an array.
     * 多列表的枚举器,每个返回的元素是一个数组
     */
    private static class ArrayEnumerator implements Enumerator<@Nullable Object[]> { // ArrayEnumerator内部类,多列表枚举器
      final int rowCount; // 行数
      final List<Column> columns; // 列列表
      int i = -1; // 当前行索引,初始化为-1表示还未开始

      /**
       * Creates an ArrayEnumerator.
       * 创建数组枚举器
       *
       * @param rowCount 行数
       * @param columns 列列表
       */
      ArrayEnumerator(int rowCount, List<Column> columns) { // ArrayEnumerator构造方法
        this.rowCount = rowCount; // 保存行数
        this.columns = columns; // 保存列列表
      }

      @Override public @Nullable Object[] current() { // 重写current方法,返回当前元素
        @Nullable Object[] objects = new Object[columns.size()]; // 创建对象数组,大小等于列数
        for (int j = 0; j < objects.length; j++) { // 遍历所有列
          final Column pair = columns.get(j); // 获取第j列
          objects[j] = pair.representation.getObject(pair.dataSet, i); // 从该列获取当前行的值
        }
        return objects; // 返回包含当前行所有列值的数组
      }

      @Override public boolean moveNext() { // 重写moveNext方法,移动到下一个元素
        return ++i < rowCount; // 增加行索引并检查是否还有更多行
      }

      @Override public void reset() { // 重写reset方法,重置枚举器
        i = -1; // 重置行索引为-1
      }

      @Override public void close() { // 重写close方法,关闭枚举器
      } // 什么都不做,因为没有需要释放的资源
    }
  }
}