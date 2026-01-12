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
package org.apache.calcite.test.catalog; // 声明包名，该类属于org.apache.calcite.test.catalog包，主要用于测试目录相关功能

import org.apache.calcite.linq4j.Ord; // 导入Ord类，用于为集合元素添加索引，支持带索引的迭代
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型，是Calcite中类型系统的核心接口
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型的工厂类
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField接口，表示关系数据类型中的字段，包含字段名和类型信息
import org.apache.calcite.rel.type.RelDataTypeFieldImpl; // 导入RelDataTypeFieldImpl类，RelDataTypeField接口的默认实现类
import org.apache.calcite.rel.type.StructKind; // 导入StructKind枚举，表示结构类型的种类，如PEEK_FIELDS、FULLY_QUALIFIED等
import org.apache.calcite.runtime.PairList; // 导入PairList类，用于存储键值对列表，支持排序和转换操作
import org.apache.calcite.util.Pair; // 导入Pair类，用于存储两个值的有序对，泛型参数表示两个值的类型

import java.util.AbstractList; // 导入AbstractList抽象类，用于创建自定义列表实现
import java.util.ArrayList; // 导入ArrayList类，动态数组实现，支持快速随机访问
import java.util.Arrays; // 导入Arrays工具类，提供数组操作的各种静态方法
import java.util.Comparator; // 导入Comparator接口，用于定义对象比较规则
import java.util.HashMap; // 导入HashMap类，基于哈希表的Map接口实现，提供快速的键值对存储和查找
import java.util.List; // 导入List接口，表示有序集合，支持重复元素
import java.util.Map; // 导入Map接口，表示键值对映射集合

/** ColumnResolver implementation that resolves CompoundNameColumn by simulating
 * Phoenix behaviors. */ // 类级JavaDoc注释：这是一个ColumnResolver接口的实现类，用于解析复合名称列，通过模拟Phoenix数据库的行为来处理列名解析
final class CompoundNameColumnResolver implements MockCatalogReader.ColumnResolver { // 定义名为CompoundNameColumnResolver的final类，实现MockCatalogReader.ColumnResolver接口，final表示该类不能被继承
  private final Map<String, Integer> nameMap = new HashMap<>(); // 成员变量：nameMap是一个映射表，键是列的完整名称，值是该列在字段列表中的索引位置，用于快速通过列名查找字段索引
  private final Map<String, Map<String, Integer>> groupMap = new HashMap<>(); // 成员变量：groupMap是一个嵌套映射表，外层键是列组名（如表名或列族名），内层Map的键是组内列名，值是字段索引，用于支持分组列名解析（如schema.table.column格式）
  private final String defaultColumnGroup; // 成员变量：defaultColumnGroup是默认的列组名，当列名没有明确指定组时，使用此默认组进行查找

  CompoundNameColumnResolver( // 构造方法：创建CompoundNameColumnResolver实例，初始化列名解析器
      List<CompoundNameColumn> columns, String defaultColumnGroup) { // 参数：columns是复合名称列列表，defaultColumnGroup是默认列组名
    this.defaultColumnGroup = defaultColumnGroup; // 将传入的默认列组名保存到成员变量中
    for (Ord<CompoundNameColumn> column : Ord.zip(columns)) { // 遍历列列表，使用Ord.zip为每个列元素添加索引，column包含列对象和索引值
      nameMap.put(column.e.getName(), column.i); // 将列的完整名称作为键，列索引作为值存入nameMap，建立列名到索引的快速查找映射
      Map<String, Integer> subMap = // 获取或创建该列所属组的子映射表，computeIfAbsent方法在组不存在时自动创建新的HashMap
          groupMap.computeIfAbsent(column.e.first, k -> new HashMap<>()); // column.e.first是列的第一部分（组名），如表名或列族名
      subMap.put(column.e.second, column.i); // 将列的第二部分（列名）作为键，列索引作为值存入子映射表，建立组内列名到索引的映射
    } // 循环结束，所有列的索引信息都已建立到nameMap和groupMap中
  } // 构造方法结束

  @Override public List<Pair<RelDataTypeField, List<String>>> resolveColumn( // 重写接口方法：解析列名，返回匹配的字段列表和剩余名称部分，支持多种解析策略
      RelDataType rowType, RelDataTypeFactory typeFactory, List<String> names) { // 参数：rowType是行类型（包含所有字段信息），typeFactory是类型工厂用于创建新类型，names是要解析的名称列表（如["table", "column", "field"]）
    List<Pair<RelDataTypeField, List<String>>> ret = new ArrayList<>(); // 创建返回结果列表，每个元素是一个Pair，包含匹配的字段和剩余的名称部分
    if (names.size() >= 2) { // 第一种解析策略：如果名称列表长度大于等于2，尝试按组名和列名进行解析（如"table.column"格式）
      Map<String, Integer> subMap = groupMap.get(names.get(0)); // 获取第一个名称作为组名，从groupMap中查找对应的子映射表
      if (subMap != null) { // 如果找到了对应的组
        Integer index = subMap.get(names.get(1)); // 在子映射表中用第二个名称作为列名查找对应的字段索引
        if (index != null) { // 如果找到了匹配的列
          ret.add( // 将匹配结果添加到返回列表中
              new Pair<RelDataTypeField, List<String>>( // 创建一个新的Pair对象
                  rowType.getFieldList().get(index), // Pair的第一个元素：从行类型的字段列表中获取指定索引的字段对象
                  names.subList(2, names.size()))); // Pair的第二个元素：从第2个元素开始到末尾的子列表，表示剩余未解析的名称部分
        } // 如果找到匹配则添加结果
      } // 如果组存在则查找列
    } // 第一种策略结束

    final String columnName = names.get(0); // 第二种解析策略：提取第一个名称作为列名，尝试直接通过完整列名查找
    final List<String> remainder = names.subList(1, names.size()); // 提取剩余的名称部分（从第1个元素到末尾），用于后续嵌套字段解析
    Integer index = nameMap.get(columnName); // 在nameMap中通过完整列名查找字段索引
    if (index != null) { // 如果找到了匹配的列
      ret.add( // 将匹配结果添加到返回列表中
          new Pair<RelDataTypeField, List<String>>( // 创建新的Pair对象
              rowType.getFieldList().get(index), // Pair的第一个元素：获取指定索引的字段对象
              remainder)); // Pair的第二个元素：剩余的名称部分
      return ret; // 直接返回结果，因为精确匹配已经找到
    } // 第二种策略结束

    final List<String> priorityGroups = Arrays.asList("", defaultColumnGroup); // 第三种解析策略：定义优先级组列表，空字符串表示无组名，defaultColumnGroup是默认组，按优先级顺序查找
    for (String group : priorityGroups) { // 遍历优先级组列表
      Map<String, Integer> subMap = groupMap.get(group); // 获取当前组的子映射表
      if (subMap != null) { // 如果该组存在
        index = subMap.get(columnName); // 在子映射表中查找列名对应的索引
        if (index != null) { // 如果找到了匹配的列
          ret.add( // 将匹配结果添加到返回列表中
              new Pair<RelDataTypeField, List<String>>( // 创建新的Pair对象
                  rowType.getFieldList().get(index), // Pair的第一个元素：获取指定索引的字段对象
                  remainder)); // Pair的第二个元素：剩余的名称部分
          return ret; // 直接返回结果，因为已在优先级组中找到匹配
        } // 如果找到匹配则返回
      } // 如果组存在则查找
    } // 第三种策略结束
    for (Map.Entry<String, Map<String, Integer>> entry : groupMap.entrySet()) { // 第四种解析策略：遍历所有其他组（非优先级组），查找匹配的列名
      if (priorityGroups.contains(entry.getKey())) { // 如果当前组在优先级组列表中
        continue; // 跳过该组，因为已经在第三种策略中处理过
      } // 跳过优先级组
      index = entry.getValue().get(columnName); // 在当前组的子映射表中查找列名对应的索引
      if (index != null) { // 如果找到了匹配的列
        ret.add(new Pair<>(rowType.getFieldList().get(index), remainder)); // 将匹配结果添加到返回列表中，但不立即返回，继续查找其他可能的匹配
      } // 如果找到匹配则添加到结果列表
    } // 第四种策略结束

    if (ret.isEmpty() && names.size() == 1) { // 第五种解析策略：如果之前没有找到任何匹配，且名称列表只有一个元素，尝试将该名称作为组名，返回该组下的所有列作为结构类型
      Map<String, Integer> subMap = groupMap.get(columnName); // 将列名作为组名，从groupMap中查找对应的子映射表
      if (subMap != null) { // 如果找到了对应的组（即该名称是一个组名）
        PairList<String, Integer> entries = PairList.of(subMap); // 将子映射表转换为PairList，便于排序操作
        entries.sort(Comparator.comparingInt(Map.Entry::getValue)); // 按字段索引值对条目进行排序，确保返回的字段顺序与原始字段列表一致
        ret.add( // 将匹配结果添加到返回列表中
            new Pair<>( // 创建新的Pair对象
                new RelDataTypeFieldImpl(columnName, -1, // Pair的第一个元素：创建一个新的RelDataTypeFieldImpl，字段名为组名，索引为-1（表示虚拟字段）
                    createStructType(rowType, typeFactory, entries)), // 字段类型通过createStructType方法创建，是一个包含该组所有列的结构类型
                remainder)); // Pair的第二个元素：剩余的名称部分（此时为空列表）
      } // 如果找到了组则创建结构类型
    } // 第五种策略结束

    return ret; // 返回所有匹配的结果列表，可能为空、包含单个元素或包含多个元素
  } // resolveColumn方法结束

  private static RelDataType createStructType( // 私有静态方法：创建结构类型，将多个字段组合成一个结构类型
      final RelDataType rowType, // 参数：rowType是原始行类型，包含所有字段的类型信息
      RelDataTypeFactory typeFactory, // 参数：typeFactory是类型工厂，用于创建新的结构类型
      final List<Map.Entry<String, Integer>> entries) { // 参数：entries是字段条目列表，每个条目包含字段名和字段索引
    return typeFactory.createStructType( // 使用类型工厂创建结构类型
        StructKind.PEEK_FIELDS, // 第一个参数：结构类型种类，PEEK_FIELDS表示可以通过字段名直接访问字段
        new AbstractList<RelDataType>() { // 第二个参数：字段类型列表，使用匿名内部类实现AbstractList，延迟加载字段类型
          @Override public RelDataType get(int index) { // 重写get方法：获取指定索引位置的字段类型
            final int i = entries.get(index).getValue(); // 从条目列表中获取第index个条目的值（即字段索引）
            return rowType.getFieldList().get(i).getType(); // 从行类型的字段列表中获取指定索引的字段，然后返回该字段的类型
          } // get方法结束
          @Override public int size() { // 重写size方法：返回字段类型列表的大小
            return entries.size(); // 返回条目列表的大小，即字段数量
          } // size方法结束
        }, // 字段类型列表匿名类结束
        new AbstractList<String>() { // 第三个参数：字段名称列表，使用匿名内部类实现AbstractList，延迟加载字段名称
          @Override public String get(int index) { // 重写get方法：获取指定索引位置的字段名称
            return entries.get(index).getKey(); // 从条目列表中获取第index个条目的键（即字段名）
          } // get方法结束
          @Override public int size() { // 重写size方法：返回字段名称列表的大小
            return entries.size(); // 返回条目列表的大小，即字段数量
          } // size方法结束
        }); // 字段名称列表匿名类结束
  } // createStructType方法结束
} // 类定义结束
