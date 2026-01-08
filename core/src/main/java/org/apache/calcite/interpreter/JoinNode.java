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
// 定义包名为 org.apache.calcite.interpreter,表示该类属于 Calcite 框架的解释器模块
package org.apache.calcite.interpreter;

// 导入 Join 类,表示关系代数中的连接操作
import org.apache.calcite.rel.core.Join;
// 导入 JoinRelType 枚举,表示连接的类型(内连接、左外连接、右外连接、全外连接等)
import org.apache.calcite.rel.core.JoinRelType;

// 导入 Google Guava 库的 ImmutableList 类,用于创建不可变列表
import com.google.common.collect.ImmutableList;

// 导入 Checker Framework 的注解,用于空值检查
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入 Java 标准库的集合类
import java.util.ArrayList;  // 动态数组列表
import java.util.HashSet;     // 哈希集合,用于存储不重复的元素
import java.util.List;        // 列表接口
import java.util.Set;         // 集合接口

// 导入 Objects.requireNonNull 方法,用于空值检查
import static java.util.Objects.requireNonNull;

/**
 * 解释器节点,实现了 {@link org.apache.calcite.rel.core.Join} 关系操作
 * 
 * 该类是 Calcite 解释器模式的核心组件之一,负责在解释器模式下执行连接操作
 * 
 * 核心功能:
 * 1. 使用嵌套循环算法实现各种类型的连接(内连接、左外连接、右外连接、全外连接、半连接、反连接)
 * 2. 支持基于条件表达式的连接条件判断
 * 3. 处理连接结果的输出和发送
 * 
 * 实现原理:
 * - 采用嵌套循环连接算法(Nested Loop Join),时间复杂度为 O(m*n),其中 m 和 n 分别是左右表的数据量
 * - 外层循环遍历外表(outer table)的每一行
 * - 内层循环遍历内表(inner table)的每一行
 * - 对每一对行执行连接条件判断,满足条件的行会被输出
 * 
 * 支持的连接类型:
 * - INNER JOIN: 只输出满足连接条件的行
 * - LEFT OUTER JOIN: 输出左表所有行,右表不匹配的行用 NULL 填充
 * - RIGHT OUTER JOIN: 输出右表所有行,左表不匹配的行用 NULL 填充
 * - FULL OUTER JOIN: 输出左右表所有行,不匹配的行用 NULL 填充
 * - SEMI JOIN: 只输出左表中与右表匹配的行(不输出右表数据)
 * - ANTI JOIN: 只输出左表中与右表不匹配的行
 */
public class JoinNode implements Node {
  // 左数据源,表示连接操作的左表数据来源,Source 接口提供行数据的接收功能
  private final Source leftSource;
  // 右数据源,表示连接操作的右表数据来源,Source 接口提供行数据的接收功能
  private final Source rightSource;
  // 输出接收器,用于将连接结果发送到下一个处理节点,Sink 接口提供行数据的发送功能
  private final Sink sink;
  // Join 关系节点,包含连接操作的元数据信息(连接类型、连接条件、左右表信息等)
  private final Join rel;
  // 连接条件表达式,编译后的标量表达式,用于判断两行数据是否满足连接条件
  private final Scalar condition;
  // 执行上下文,用于存储连接过程中的中间数据(如合并后的行值、变量等)
  private final Context context;

  /**
   * 构造方法,初始化 JoinNode 实例
   * 
   * @param compiler 编译器对象,负责将关系表达式编译为可执行的解释器节点
   * @param rel Join 关系节点,包含连接操作的完整信息
   * 
   * 初始化步骤:
   * 1. 创建左右数据源,从编译器获取左右表的数据输入
   * 2. 创建输出接收器,用于发送连接结果
   * 3. 编译连接条件表达式,生成可执行的标量表达式
   * 4. 保存 Join 关系节点引用,用于获取连接类型等信息
   * 5. 创建执行上下文,用于存储执行过程中的临时数据
   */
  public JoinNode(Compiler compiler, Join rel) {
    // 通过编译器创建左数据源,参数 0 表示左表索引,数据源提供行数据的迭代接收功能
    this.leftSource = compiler.source(rel, 0);
    // 通过编译器创建右数据源,参数 1 表示右表索引,数据源提供行数据的迭代接收功能
    this.rightSource = compiler.source(rel, 1);
    // 通过编译器创建输出接收器,用于将连接结果发送到下一个处理节点
    this.sink = compiler.sink(rel);
    // 编译连接条件表达式:
    // 1. 将 Join 的条件表达式转换为不可变列表
    // 2. 获取左右表合并后的行类型,用于确定表达式的输入字段
    // 3. 编译生成可执行的标量表达式,用于运行时判断连接条件
    this.condition =
        compiler.compile(ImmutableList.of(rel.getCondition()),
            compiler.combinedRowType(rel.getInputs()));
    // 保存 Join 关系节点引用,后续用于获取连接类型、行类型等元数据
    this.rel = rel;
    // 创建执行上下文,用于存储连接过程中的临时变量和行数据
    this.context = compiler.createContext();

  }

  /**
   * 关闭数据源,释放资源
   * 
   * 该方法在连接操作完成后调用,用于关闭左右数据源
   * 防止资源泄漏,确保数据流正确关闭
   */
  @Override public void close() {
    // 关闭左数据源,释放相关资源
    leftSource.close();
    // 关闭右数据源,释放相关资源
    rightSource.close();
  }

  /**
   * 执行连接操作
   * 
   * 该方法是连接操作的核心执行逻辑,使用嵌套循环算法实现各种类型的连接
   * 
   * 执行流程:
   * 1. 计算连接后结果的字段总数(左表字段数 + 右表字段数)
   * 2. 初始化上下文的值数组,用于存储合并后的行数据
   * 3. 根据连接类型确定外表和内表:
   *    - INNER/LEFT/FULL JOIN: 左表为外表,右表为内表
   *    - RIGHT JOIN: 右表为外表,左表为内表
   * 4. 加载内表所有行到内存(因为需要多次遍历)
   * 5. 对外表每一行,与内表所有行进行连接条件判断:
   *    - 满足条件的行被标记为匹配
   *    - 根据连接类型输出结果
   * 6. 对于 FULL JOIN,额外处理右表中未匹配的行
   * 
   * @throws InterruptedException 如果执行被中断
   */
  @Override public void run() throws InterruptedException {
    // 计算连接结果的字段总数 = 左表字段数 + 右表字段数
    // getLeft() 获取左表关系表达式
    // getRowType() 获取行类型
    // getFieldCount() 获取字段数量
    final int fieldCount = rel.getLeft().getRowType().getFieldCount()
        + rel.getRight().getRowType().getFieldCount();
    // 初始化上下文的值数组,用于存储连接后的一行数据(左右表数据的合并)
    context.values = new Object[fieldCount];

    // 定义外表数据源,默认左表为外表
    // 外表:嵌套循环外层循环遍历的表
    Source outerSource = leftSource;
    // 定义内表数据源,默认右表为内表
    // 内表:嵌套循环内层循环遍历的表,会被完全加载到内存
    Source innerSource = rightSource;
    // 如果是右外连接,需要交换外表和内表
    // 右外连接需要确保右表数据全部输出,因此右表作为外表
    if (rel.getJoinType() == JoinRelType.RIGHT) {
      outerSource = rightSource;  // 右表作为外表
      innerSource = leftSource;   // 左表作为内表
    }

    // 定义外表的当前行,初始化为 null
    Row outerRow = null;
    // 定义内表的所有行集合,初始为 null(延迟加载)
    // 使用 List 存储因为需要多次遍历
    List<Row> innerRows = null;
    // 创建匹配行集合,用于记录内表中已被匹配的行
    // 使用 Set 实现快速查找,用于 FULL JOIN 时判断右表哪些行未被匹配
    Set<Row> matchRowSet = new HashSet<>();
    // 外层循环:遍历外表的每一行数据
    // receive() 方法从数据源接收一行数据,返回 null 表示数据源已耗尽
    while ((outerRow = outerSource.receive()) != null) {
      // 延迟加载内表数据:只在第一次需要时加载
      // 这样可以避免在不需要时浪费内存
      if (innerRows == null) {
        // 初始化内表行列表
        innerRows = new ArrayList<Row>();
        // 定义内表的当前行
        Row innerRow = null;
        // 内层循环:遍历内表的所有行,将所有行加载到内存
        // receive() 会阻塞直到返回一行或 null(数据源耗尽)
        while ((innerRow = innerSource.receive()) != null) {
          // 将内表行添加到列表中
          innerRows.add(innerRow);
        }
      }
      // 执行连接操作:
      // 1. 将外表行与内表所有行进行连接条件判断
      // 2. 返回内表中与外表行匹配的行列表
      // 3. 将匹配的行添加到匹配集合中(用于 FULL JOIN)
      matchRowSet.addAll(doJoin(outerRow, innerRows, rel.getJoinType()));
    }
    // 如果是全外连接,需要额外处理右表中未匹配的行
    if (rel.getJoinType() == JoinRelType.FULL) {
      // 创建空的匹配行列表,表示外表没有匹配的行
      List<Row> empty = new ArrayList<>();
      // 遍历内表的所有行,找出未被匹配的行
      // requireNonNull 确保内表行列表不为 null,否则抛出异常
      // TODO 注释: CALCITE-4308,解释器中的 JoinNode 在 FULL JOIN 时可能因 NPE 失败
      for (Row row : requireNonNull(innerRows, "innerRows")) {
        // 如果该行已被匹配,跳过
        if (matchRowSet.contains(row)) {
          continue;
        }
        // 该行未被匹配,作为右外连接处理,输出该行(左表部分用 NULL 填充)
        doSend(row, empty, JoinRelType.RIGHT);
      }
    }
  }

  /**
   * 执行连接操作,返回与外表行匹配的内表行列表
   * 
   * 该方法实现连接的核心逻辑:
   * 1. 将外表行复制到上下文中
   * 2. 遍历内表所有行
   * 3. 对每个内表行:
   *    - 将内表行复制到上下文中(与外表行合并)
   *    - 执行连接条件表达式判断
   *    - 如果条件满足,将该内表行添加到匹配列表
   * 4. 根据匹配结果和连接类型输出连接结果
   * 
   * @param outerRow 外表的当前行
   * @param innerRows 内表的所有行(已加载到内存)
   * @param joinRelType 连接类型(INNER/LEFT/RIGHT/FULL/SEMI/ANTI)
   * @return 内表中与外表行匹配的行列表
   * @throws InterruptedException 如果执行被中断
   */
  private List<Row> doJoin(Row outerRow, List<Row> innerRows,
      JoinRelType joinRelType) throws InterruptedException {
    // 判断外表行是否在左侧:
    // - 非 RIGHT JOIN 时,外表行在左侧(左表为外表)
    // - RIGHT JOIN 时,外表行在右侧(右表为外表)
    boolean outerRowOnLeft = joinRelType != JoinRelType.RIGHT;
    // 将外表行的值复制到上下文的值数组中
    // 如果外表在左侧,复制到数组的左侧部分;否则复制到右侧部分
    copyToContext(outerRow, outerRowOnLeft);
    // 创建匹配的内表行列表,用于存储满足连接条件的内表行
    List<Row> matchInnerRows = new ArrayList<>();
    // 遍历内表的所有行
    for (Row innerRow : innerRows) {
      // 将内表行的值复制到上下文的值数组中
      // 如果外表在左侧,内表在右侧;否则内表在左侧
      copyToContext(innerRow, !outerRowOnLeft);
      // 执行连接条件表达式,判断当前这对行是否满足连接条件
      // context 包含了合并后的左右表行数据
      // execute() 返回 Boolean 类型,true 表示满足条件,false 表示不满足,null 表示未知
      final Boolean execute = (Boolean) condition.execute(context);
      // 如果条件结果不为 null 且为 true,表示满足连接条件
      if (execute != null && execute) {
        // 将该内表行添加到匹配列表中
        matchInnerRows.add(innerRow);
      }
    }
    // 根据匹配结果和连接类型,发送连接结果到输出接收器
    doSend(outerRow, matchInnerRows, joinRelType);
    // 返回匹配的内表行列表,供调用者使用(如记录匹配集合)
    return matchInnerRows;
  }

  /**
   * 发送连接结果到输出接收器
   * 
   * 根据连接类型和匹配情况,决定如何发送结果:
   * 
   * 有匹配的情况:
   * - INNER/LEFT/RIGHT/FULL JOIN: 输出连接后的完整行(左右表数据合并)
   * - SEMI JOIN: 只输出外表行(不输出内表数据)
   * 
   * 无匹配的情况:
   * - LEFT/RIGHT/FULL JOIN: 输出外表行,内表部分用 NULL 填充
   * - ANTI JOIN: 只输出外表行(不输出内表数据)
   * - INNER JOIN: 不输出任何数据
   * 
   * @param outerRow 外表的当前行
   * @param matchInnerRows 内表中与外表行匹配的行列表
   * @param joinRelType 连接类型
   * @throws InterruptedException 如果执行被中断
   */
  private void doSend(Row outerRow, List<Row> matchInnerRows,
      JoinRelType joinRelType) throws InterruptedException {
    // 情况1:存在匹配的内表行
    if (!matchInnerRows.isEmpty()) {
      // 根据连接类型处理
      switch (joinRelType) {
      // 内连接:输出满足条件的连接行
      case INNER:
      // 左外连接:输出满足条件的连接行
      case LEFT:
      // 右外连接:输出满足条件的连接行
      case RIGHT:
      // 全外连接:输出满足条件的连接行
      case FULL:
        // 判断外表行是否在左侧(逻辑同 doJoin 方法)
        boolean outerRowOnLeft = joinRelType != JoinRelType.RIGHT;
        // 将外表行的值复制到上下文的值数组中
        copyToContext(outerRow, outerRowOnLeft);
        // 确保上下文的值数组不为 null,否则抛出异常
        requireNonNull(context.values, "context.values");
        // 遍历所有匹配的内表行
        for (Row row : matchInnerRows) {
          // 将内表行的值复制到上下文的值数组中,与外表行合并
          copyToContext(row, !outerRowOnLeft);
          // 将合并后的行数据发送到输出接收器
          // Row.asCopy() 创建行的副本,避免后续修改影响
          sink.send(Row.asCopy(context.values));
        }
        // 跳出 switch 语句
        break;
      // 半连接:只输出外表行,不输出内表数据
      case SEMI:
        // 发送外表行的副本
        sink.send(Row.asCopy(outerRow.getValues()));
        // 跳出 switch 语句
        break;
      // 默认情况:不做任何操作
      default:
        // 跳出 switch 语句
        break;
      }
    } else {
      // 情况2:不存在匹配的内表行
      // 根据连接类型处理
      switch (joinRelType) {
      // 左外连接:输出外表行,内表部分用 NULL 填充
      case LEFT:
      // 右外连接:输出外表行,内表部分用 NULL 填充
      case RIGHT:
      // 全外连接:输出外表行,内表部分用 NULL 填充
      case FULL:
        // 确保上下文的值数组不为 null,否则抛出异常
        requireNonNull(context.values, "context.values");
        // 计算需要填充 NULL 的列数
        // 总列数 - 外表列数 = 内表列数
        int nullColumnNum = context.values.length - outerRow.size();
        // 对于全外连接:
        // - 使用左表作为外表,先输出左表中未匹配的行
        // - 右表中未匹配的行会在后续处理(run 方法的最后部分)
        // generatesNullsOnRight() 返回 true 表示在右侧生成 NULL(即左外连接或全外连接)
        copyToContext(outerRow, joinRelType.generatesNullsOnRight());
        // 计算 NULL 列的起始位置
        // 如果在右侧生成 NULL,从外表列数开始填充
        // 否则从 0 开始填充(即左表部分用 NULL 填充)
        int nullColumnStart = joinRelType.generatesNullsOnRight() ? outerRow.size() : 0;
        // 将 NULL 值数组复制到上下文的值数组中
        // new Object[nullColumnNum] 创建全为 null 的数组
        System.arraycopy(new Object[nullColumnNum], 0,
            context.values, nullColumnStart, nullColumnNum);
        // 发送填充 NULL 后的行数据
        sink.send(Row.asCopy(context.values));
        // 跳出 switch 语句
        break;
      // 反连接:只输出外表行(不匹配的行),不输出内表数据
      case ANTI:
        // 发送外表行的副本
        sink.send(Row.asCopy(outerRow.getValues()));
        // 跳出 switch 语句
        break;
      // 默认情况:不做任何操作
      default:
        // 跳出 switch 语句
        break;
      }
    }
  }

  /**
   * 将行的值复制到上下文的值数组中
   * 
   * 该方法用于将单独的左表行或右表行复制到合并后的上下文值数组中
   * 
   * 复制策略:
   * - 如果 toLeftSide 为 true,将行的值复制到数组的左侧部分(从索引 0 开始)
   * - 如果 toLeftSide 为 false,将行的值复制到数组的右侧部分(从末尾开始)
   * 
   * 例如:
   * - 左表有 3 列,右表有 2 列
   * - 复制左表行(toLeftSide=true): values[0..2] = 行值
   * - 复制右表行(toLeftSide=false): values[3..4] = 行值
   * 
   * @param row 要复制的行
   * @param toLeftSide 是否复制到左侧,true 表示左侧,false 表示右侧
   */
  private void copyToContext(Row row, boolean toLeftSide) {
    // 获取行的值数组,可能为 null(使用 @Nullable 注解标记)
    @Nullable Object[] values = row.getValues();
    // 确保上下文的值数组不为 null,否则抛出异常
    requireNonNull(context.values, "context.values");
    // 如果要复制到左侧
    if (toLeftSide) {
      // 将行的值从源数组的 0 位置复制到目标数组的 0 位置
      // 复制长度为 values.length
      System.arraycopy(values, 0, context.values, 0, values.length);
    } else {
      // 如果要复制到右侧
      // 将行的值从源数组的 0 位置复制到目标数组的末尾
      // 目标起始位置 = context.values.length - values.length
      // 这样可以确保右表的数据放在数组的右侧部分
      System.arraycopy(values, 0, context.values,
          context.values.length - values.length, values.length);
    }
  }
}
