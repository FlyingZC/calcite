/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache 软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议
 * this work for additional information regarding copyright ownership.  // 有关版权所有权的附加信息
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF 根据第 2.0 版本的 Apache 许可证授权给你
 * (the "License"); you may not use this file except in compliance with // ("许可证");除非符合许可证要求,否则你不能使用此文件
 * the License.  You may obtain a copy of the License at // 你可以在以下位置获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意,否则
 * distributed under the License is distributed on an "AS IS" BASIS, // 根据许可证分发的软件是按"原样"基础分发的
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不提供任何形式的保证或条件,无论是明示的还是暗示的
 * See the License for the specific language governing permissions and // 请参阅许可证以了解特定语言的许可权限和
 * limitations under the License. // 限制
 */
package org.apache.calcite.plan; // 包声明:org.apache.calcite.plan,表示这个类属于 Calcite 的计划(Plan)模块

import org.apache.calcite.rel.RelNode; // 导入 RelNode 接口,代表关系代数树的节点,是 Calcite 中所有关系表达式的基础接口
import org.apache.calcite.rel.RelVisitor; // 导入 RelVisitor 类,用于遍历关系代数树的访问者模式实现
import org.apache.calcite.rel.core.TableModify; // 导入 TableModify 类,代表对表进行修改操作的关系节点(INSERT/UPDATE/DELETE)

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Nullable 注解,用于标记可能为 null 的参数

import java.util.Collections; // 导入 Collections 工具类,用于创建不可修改的空集合
import java.util.HashMap; // 导入 HashMap 类,用于实现键值对映射,存储表名到访问模式的映射
import java.util.List; // 导入 List 接口,用于表示有序的表名列表(完全限定名)
import java.util.Map; // 导入 Map 接口,用于定义存储表访问信息的映射结构
import java.util.Set; // 导入 Set 接口,用于表示表名的集合

// TODO jvs 9-Mar-2006:  move this class to another package; it // TODO 注释:jvs 在 2006年3月9日提出:将此类移动到另一个包;它
// doesn't really belong here.  Also, use a proper class for table // 真的不属于这里。另外,为表名使用一个适当的类
// names instead of List<String>. // 而不是使用 List<String>。

/**
 * <code>TableAccessMap</code> represents the tables accessed by a query plan, // TableAccessMap 类表示查询计划访问的表,
 * with READ/WRITE information. // 包含读/写访问信息
 *
 * @deprecated As of 1.30.0, if you need to know how tables in a plan are accessed you are // @deprecated 注解:从 1.30.0 版本开始已弃用,如果你需要知道计划中的表是如何访问的,
 * encouraged to implement your own logic (using a RelNode visitor or other). The class is not used // 建议实现自己的逻辑(使用 RelNode 访问者或其他)。此类在项目中
 * anywhere in the project and remains untested thus it is deprecated. // 没有被使用且未经测试,因此已被弃用
 */
@Deprecated // to be removed before 2.0 // @Deprecated 注解:将在 2.0 版本之前移除
public class TableAccessMap { // 类定义:TableAccessMap,用于跟踪查询计划中表的访问模式
  //~ Enums ------------------------------------------------------------------ // 枚举类型定义部分

  /** Access mode. */ // 枚举类型注释:访问模式,定义表的不同访问类型
  public enum Mode { // 枚举定义:Mode,表示表的访问模式
    /** // 枚举常量注释
     * Table is not accessed at all. // 表完全没有被访问
     */
    NO_ACCESS, // 枚举值:NO_ACCESS,表示表未被访问

    /** // 枚举常量注释
     * Table is accessed for read only. // 表仅被读取访问
     */
    READ_ACCESS, // 枚举值:READ_ACCESS,表示表仅被读取

    /** // 枚举常量注释
     * Table is accessed for write only. // 表仅被写入访问
     */
    WRITE_ACCESS, // 枚举值:WRITE_ACCESS,表示表仅被写入(INSERT/UPDATE/DELETE)

    /** // 枚举常量注释
     * Table is accessed for both read and write. // 表同时被读取和写入访问
     */
    READWRITE_ACCESS // 枚举值:READWRITE_ACCESS,表示表同时被读取和写入
  } // 枚举定义结束

  //~ Instance fields -------------------------------------------------------- // 实例字段定义部分

  private final Map<List<String>, Mode> accessMap; // 成员变量:accessMap,存储表名(完全限定名列表)到访问模式的映射关系,final 表示不可变引用

  //~ Constructors ----------------------------------------------------------- // 构造方法定义部分

  /** // 构造方法注释
   * Constructs a permanently empty TableAccessMap. // 构造一个永久为空的 TableAccessMap(不包含任何表访问信息)
   */
  public TableAccessMap() { // 无参构造方法:创建一个空的 TableAccessMap 对象
    accessMap = Collections.EMPTY_MAP; // 初始化 accessMap 为不可修改的空映射(Collections.EMPTY_MAP 是一个静态常量)
  } // 构造方法结束

  /** // 构造方法注释
   * Constructs a TableAccessMap for all tables accessed by a RelNode and its // 构造一个 TableAccessMap,包含 RelNode 及其所有后代节点访问的所有表
   * descendants. // (递归遍历整个关系代数树)
   *
   * @param rel the RelNode for which to build the map // 参数说明:rel,要为其构建映射的 RelNode(关系节点)
   */
  public TableAccessMap(RelNode rel) { // 构造方法:接收一个 RelNode 参数,分析该节点及其子树的表访问情况

    // NOTE jvs 9-Mar-2006: This method must NOT retain a reference to the // 注意注释:jvs 在 2006年3月9日指出:此方法绝不能保留对输入 rel 的引用,
    // input rel, because we use it for cached statements, and we don't // 因为我们将它用于缓存语句,而我们不希望在准备完成后
    // want to retain any rel references after preparation completes. // 保留任何 rel 引用(避免内存泄漏)

    accessMap = new HashMap<>(); // 初始化 accessMap 为一个新的 HashMap 实例,用于存储表访问信息
    RelOptUtil.go( // 调用 RelOptUtil.go 方法遍历关系节点树
        new TableRelVisitor(), // 参数1:创建一个新的 TableRelVisitor 访问者(内部类),用于收集表访问信息
        rel); // 参数2:要遍历的根关系节点
  } // 构造方法结束

  /** // 构造方法注释
   * Constructs a TableAccessMap for a single table. // 构造一个仅包含单个表的 TableAccessMap
   *
   * @param table fully qualified name of the table, represented as a list // 参数说明:table,表的完全限定名,表示为字符串列表(如 ["catalog", "schema", "table"])
   * @param mode  access mode for the table // 参数说明:mode,表的访问模式(READ/WRITE/READWRITE)
   */
  public TableAccessMap(List<String> table, Mode mode) { // 构造方法:接收表名和访问模式,创建单表访问映射
    accessMap = new HashMap<>(); // 初始化 accessMap 为一个新的 HashMap 实例
    accessMap.put(table, mode); // 将表名和访问模式存入映射中
  } // 构造方法结束

  //~ Methods ---------------------------------------------------------------- // 方法定义部分

  /** // 方法注释
   * Returns a set of qualified names for all tables accessed. // 返回所有被访问表的完全限定名集合
   */
  @SuppressWarnings("return.type.incompatible") // 注解:抑制返回类型不兼容的警告
  public Set<List<String>> getTablesAccessed() { // 方法:getTablesAccessed,获取所有被访问表的名称集合
    return accessMap.keySet(); // 返回映射的所有键(即所有表的完全限定名列表)
  } // 方法结束

  /** // 方法注释
   * Determines whether a table is accessed at all. // 判断表是否被访问(无论读或写)
   *
   * @param tableName qualified name of the table of interest // 参数说明:tableName,要检查的表的完全限定名
   * @return true if table is accessed // 返回值:如果表被访问则返回 true,否则返回 false
   */
  public boolean isTableAccessed(List<String> tableName) { // 方法:isTableAccessed,检查表是否被访问
    return accessMap.containsKey(tableName); // 返回映射中是否包含该表名(即该表在映射中存在)
  } // 方法结束

  /** // 方法注释
   * Determines whether a table is accessed for read. // 判断表是否被读取访问
   *
   * @param tableName qualified name of the table of interest // 参数说明:tableName,要检查的表的完全限定名
   * @return true if table is accessed for read // 返回值:如果表被读取访问则返回 true,否则返回 false
   */
  public boolean isTableAccessedForRead(List<String> tableName) { // 方法:isTableAccessedForRead,检查表是否被读取
    Mode mode = getTableAccessMode(tableName); // 调用 getTableAccessMode 方法获取表的访问模式
    return (mode == Mode.READ_ACCESS) || (mode == Mode.READWRITE_ACCESS); // 返回:访问模式是 READ_ACCESS 或 READWRITE_ACCESS 时返回 true
  } // 方法结束

  /** // 方法注释
   * Determines whether a table is accessed for write. // 判断表是否被写入访问
   *
   * @param tableName qualified name of the table of interest // 参数说明:tableName,要检查的表的完全限定名
   * @return true if table is accessed for write // 返回值:如果表被写入访问则返回 true,否则返回 false
   */
  public boolean isTableAccessedForWrite(List<String> tableName) { // 方法:isTableAccessedForWrite,检查表是否被写入
    Mode mode = getTableAccessMode(tableName); // 调用 getTableAccessMode 方法获取表的访问模式
    return (mode == Mode.WRITE_ACCESS) || (mode == Mode.READWRITE_ACCESS); // 返回:访问模式是 WRITE_ACCESS 或 READWRITE_ACCESS 时返回 true
  } // 方法结束

  /** // 方法注释
   * Determines the access mode of a table. // 确定表的访问模式
   *
   * @param tableName qualified name of the table of interest // 参数说明:tableName,要检查的表的完全限定名
   * @return access mode // 返回值:表的访问模式(NO_ACCESS/READ_ACCESS/WRITE_ACCESS/READWRITE_ACCESS)
   */
  public Mode getTableAccessMode(List<String> tableName) { // 方法:getTableAccessMode,获取表的访问模式
    Mode mode = accessMap.get(tableName); // 从映射中获取该表的访问模式
    if (mode == null) { // 如果映射中没有该表(即 mode 为 null)
      return Mode.NO_ACCESS; // 返回 NO_ACCESS,表示表未被访问
    } // if 结束
    return mode; // 返回获取到的访问模式
  } // 方法结束

  /** // 方法注释
   * Constructs a qualified name for an optimizer table reference. // 为优化器表引用构造完全限定名
   *
   * @param table table of interest // 参数说明:table,感兴趣的表(RelOptTable 对象)
   * @return qualified name // 返回值:表的完全限定名(字符串列表)
   */
  public List<String> getQualifiedName(RelOptTable table) { // 方法:getQualifiedName,获取表的完全限定名
    return table.getQualifiedName(); // 调用 RelOptTable 接口的 getQualifiedName 方法返回表的完全限定名
  } // 方法结束

  //~ Inner Classes ---------------------------------------------------------- // 内部类定义部分

  /** Visitor that finds all tables in a tree. */ // 内部类注释:访问者类,用于在关系节点树中查找所有表
  private class TableRelVisitor extends RelVisitor { // 内部类定义:TableRelVisitor,继承自 RelVisitor,用于遍历关系节点树并收集表访问信息
    @Override public void visit( // 重写 visit 方法:访问关系节点
        RelNode p, // 参数1:p,当前访问的关系节点
        int ordinal, // 参数2:ordinal,该节点在其父节点子节点列表中的序号
        @Nullable RelNode parent) { // 参数3:parent,父节点(可能为 null,用 @Nullable 注解标记)
      super.visit(p, ordinal, parent); // 调用父类 RelVisitor 的 visit 方法,继续遍历子节点
      RelOptTable table = p.getTable(); // 获取当前关系节点关联的表(RelOptTable 对象)
      if (table == null) { // 如果该节点没有关联的表(例如某些中间节点如 Join/Filter 不直接关联表)
        return; // 直接返回,不处理
      } // if 结束
      Mode newAccess; // 声明变量:newAccess,用于存储当前节点的访问模式

      // FIXME jvs 1-Feb-2006:  Don't rely on object type here; // FIXME 注释:jvs 在 2006年2月1日指出:不要在这里依赖对象类型;
      // eventually someone is going to write a rule which transforms // 最终会有人编写一个规则,将节点转换为
      // to something which doesn't inherit TableModify, // 不继承 TableModify 的其他类型,
      // and this will break.  Need to make this explicit in // 这会导致代码失效。需要在 RelNode 接口中
      // the RelNode interface. // 明确地表达这一点(建议在 RelNode 接口中添加方法来判断访问模式)
      if (p instanceof TableModify) { // 如果当前节点是 TableModify 类型(表示 INSERT/UPDATE/DELETE 操作)
        newAccess = Mode.WRITE_ACCESS; // 设置访问模式为 WRITE_ACCESS(写入访问)
      } else { // 如果不是 TableModify 类型(如 TableScan,表示 SELECT 操作)
        newAccess = Mode.READ_ACCESS; // 设置访问模式为 READ_ACCESS(读取访问)
      } // if-else 结束
      List<String> key = getQualifiedName(table); // 调用 getQualifiedName 方法获取表的完全限定名作为映射的键
      Mode oldAccess = accessMap.get(key); // 从映射中获取该表已有的访问模式
      if ((oldAccess != null) && (oldAccess != newAccess)) { // 如果表之前已被访问过,且之前的访问模式与当前不同
        newAccess = Mode.READWRITE_ACCESS; // 将访问模式更新为 READWRITE_ACCESS(同时读写)
      } // if 结束
      accessMap.put(key, newAccess); // 将表名和(更新后的)访问模式存入映射中
    } // visit 方法结束
  } // TableRelVisitor 内部类结束
} // TableAccessMap 类结束
