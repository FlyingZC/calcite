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
// Apache许可证声明,说明该代码遵循Apache 2.0开源协议
package org.apache.calcite.example.maze; // 定义包名为org.apache.calcite.example.maze,这是Calcite项目中迷宫生成器示例所在的包

import org.apache.calcite.linq4j.Enumerator; // 导入Calcite的LINQ4J库中的Enumerator接口,用于枚举迷宫的文本行表示

import java.io.PrintWriter; // 导入Java I/O库中的PrintWriter类,用于将迷宫输出到文本流
import java.util.ArrayDeque; // 导入Java集合库中的ArrayDeque类,用作双端队列,用于迷宫求解时的回溯栈
import java.util.ArrayList; // 导入Java集合库中的ArrayList类,用于存储迷宫求解过程中的路径
import java.util.Deque; // 导入Java集合库中的Deque接口,定义双端队列的标准接口
import java.util.LinkedHashSet; // 导入Java集合库中的LinkedHashSet类,用于存储迷宫解路径并保持插入顺序
import java.util.List; // 导入Java集合库中的List接口,定义列表的标准接口
import java.util.Random; // 导入Java工具库中的Random类,用于生成随机数以随机化迷宫布局
import java.util.Set; // 导入Java集合库中的Set接口,定义集合的标准接口

/** Maze generator. */
// 迷宫生成器类:用于生成和求解迷宫,使用随机化Prim算法生成完美迷宫(无环且所有单元格可达)
class Maze { // 定义Maze类,实现了迷宫的生成、打印和求解功能
  private final int width; // 迷宫的宽度(列数),final表示初始化后不可改变,表示迷宫的横向单元格数量
  final int height; // 迷宫的高度(行数),包级别访问权限,表示迷宫的纵向单元格数量
  private final int[] regions; // 区域数组,用于并查集数据结构,存储每个单元格所属的区域编号,初始时每个单元格都是独立区域
  private final boolean[] ups; // 上方墙壁数组,记录每个单元格上方是否有墙壁(true表示无墙/可通过,false表示有墙/不可通过)
  private final boolean[] lefts; // 左侧墙壁数组,记录每个单元格左侧是否有墙壁(true表示无墙/可通过,false表示有墙/不可通过)

  static final boolean DEBUG = false; // 静态调试标志,设置为true时会打印迷宫生成的详细调试信息
  private final boolean horizontal = false; // 水平走廊标志,为true时会优先生成水平方向的走廊,使迷宫有更多水平通道
  private final boolean spiral = false; // 螺旋迷宫标志,为true时会生成螺旋形迷宫,墙壁倾向于向边缘分布

  Maze(int width, int height) { // 构造方法,创建指定宽高的迷宫对象
    this.width = width; // 初始化迷宫宽度
    this.height = height; // 初始化迷宫高度
    this.regions = new int[width * height]; // 创建区域数组,大小为迷宫单元格总数(width * height),用于并查集
    for (int i = 0; i < regions.length; i++) { // 遍历所有单元格
      regions[i] = i; // 初始化时每个单元格都是独立区域,区域编号等于单元格索引(并查集的初始化)
    } // 初始化完成,每个单元格都是自己的区域代表
    this.ups = new boolean[width * height + width]; // 创建上方墙壁数组,大小为单元格数+宽度(多一行用于底部边界)
    this.lefts = new boolean[width * height + 1]; // 创建左侧墙壁数组,大小为单元格数+1(多一列用于右边界)
  } // 构造方法完成,初始化了迷宫的基本数据结构

  private int region(int cell) { // 查找单元格所属的区域,使用路径压缩优化并查集查找性能
    int region = regions[cell]; // 获取单元格当前存储的区域编号
    if (region == cell) { // 如果区域编号等于单元格索引,说明这个单元格就是区域的根节点
      return region; // 返回根节点(区域代表)
    } // 需要继续向上查找
    return regions[cell] = region(region); // 递归查找根节点,并使用路径压缩(将当前单元格直接指向根节点)
  } // 区域查找方法完成,返回单元格所属的最终区域编号

  /** Prints the maze. Results are like this:
   *
   * <blockquote>
   * +--+--+--+--+--+
   * |        |     |
   * +--+  +--+--+  +
   * |     |  |     |
   * +  +--+  +--+  +
   * |              |
   * +--+--+--+--+--+
   * </blockquote>
   *
   * @param pw Print writer
   * @param space Whether to put a space in each cell; if false, prints the
   *              region number of the cell
   */
  // 打印迷宫到输出流,迷宫以ASCII字符形式显示,使用+表示墙角,--和|表示墙壁
  public void print(PrintWriter pw, boolean space) { // 打印方法,参数pw是输出流,space决定单元格内容是空格还是区域号
    pw.println(); // 先输出一个空行,与前面的输出分隔
    final StringBuilder b = new StringBuilder(); // 创建字符串构建器b,用于构建迷宫的奇数行(包含上方墙壁)
    final StringBuilder b2 = new StringBuilder(); // 创建字符串构建器b2,用于构建迷宫的偶数行(包含单元格内容和左侧墙壁)
    final CellContent cellContent; // 定义单元格内容回调接口
    if (space) { // 如果space参数为true
      cellContent = c -> "  "; // 单元格显示两个空格,迷宫只显示墙壁结构
    } else { // 如果space参数为false
      cellContent = c -> { // 使用lambda表达式定义单元格内容为区域编号
        String s = region(c) + ""; // 获取单元格的区域编号并转为字符串
        return s.length() == 1 ? " " + s : s; // 如果编号是一位数,前面补空格;否则直接返回(保持两位对齐)
      }; // lambda表达式结束
    } // 单元格内容定义完成
    for (int y = 0; y < height; y++) { // 遍历迷宫的每一行
      row(cellContent, b, b2, y); // 调用row方法生成第y行的两个字符串表示(b和b2)
      pw.println(b.toString()); // 输出奇数行(上方墙壁行)
      pw.println(b2.toString()); // 输出偶数行(单元格内容和左侧墙壁)
      b.setLength(0); // 清空字符串构建器b,准备下一行
      b2.setLength(0); // 清空字符串构建器b2,准备下一行
    } // 所有行输出完成
    for (int x = 0; x < width; x++) { // 输出迷宫底部的边界墙
      pw.print("+--"); // 每列输出"+--",表示底部墙壁
    } // 底部边界墙输出完成
    pw.println('+'); // 输出最后一个角落的'+'
    pw.flush(); // 刷新输出流,确保所有内容都被写入
  } // 打印方法完成,迷宫已输出到指定的PrintWriter

  /** Generates a list of lines representing the maze in text form. */
  // 生成迷宫文本表示的枚举器,返回一个可以逐行遍历迷宫文本的Enumerator对象
  public Enumerator<String> enumerator(final Set<Integer> solutionSet) { // 返回迷宫文本行的枚举器,solutionSet是解路径的单元格集合(可为null)
    final CellContent cellContent; // 定义单元格内容回调接口
    if (solutionSet == null) { // 如果没有提供解路径
      cellContent = CellContent.SPACE; // 单元格显示两个空格
    } else { // 如果提供了解路径
      cellContent = c -> solutionSet.contains(c) ? "* " : "  "; // 解路径上的单元格显示"* ",其他显示"  "
    } // 单元格内容定义完成
    return new Enumerator<String>() { // 返回一个匿名Enumerator对象
      int i = -1; // 当前行的索引,初始化为-1(表示还未开始)
      final StringBuilder b = new StringBuilder(); // 字符串构建器b,用于存储奇数行内容
      final StringBuilder b2 = new StringBuilder(); // 字符串构建器b2,用于存储偶数行内容

      @Override public String current() { // 返回当前行的字符串
        return i % 2 == 0 ? b.toString() : b2.toString(); // 如果是偶数行返回b,奇数行返回b2
      } // 当前行方法完成

      @Override public boolean moveNext() { // 移动到下一行,返回是否还有更多行
        if (i >= height * 2) { // 如果已经超过迷宫的总行数(每行占两行文本)
          return false; // 返回false,表示没有更多行
        } // 还有更多行
        ++i; // 行索引递增
        if (i % 2 == 0) { // 如果是偶数行(新的一行开始)
          b.setLength(0); // 清空b
          b2.setLength(0); // 清空b2
          row(cellContent, b, b2, i / 2); // 调用row方法生成第i/2行的内容
        } // 新行内容生成完成
        return true; // 返回true,表示还有更多行
      } // 移动方法完成

      @Override public void reset() { // 重置枚举器到初始状态
        i = -1; // 行索引重置为-1
      } // 重置方法完成

      @Override public void close() {} // 关闭枚举器,此实现不需要清理资源
    }; // 匿名Enumerator对象创建完成
  } // 枚举器生成方法完成

  /** Returns a pair of strings representing a row of the maze. */
  // 生成迷宫指定行的文本表示,生成两个字符串:奇数行(上方墙壁)和偶数行(单元格内容和左侧墙壁)
  private void row(CellContent cellContent, StringBuilder b, StringBuilder b2,
      int y) { // cellContent定义单元格内容,b存储上方墙壁行,b2存储单元格内容行,y是行号
    final int c0 = y * width; // 计算该行第一个单元格的索引
    for (int x = 0; x < width; x++) { // 遍历该行的所有列
      b.append('+'); // 添加墙角符号'+'
      b.append(ups[c0 + x] ? "  " : "--"); // 如果上方无墙添加"  ",有墙添加"--"
    } // 上方墙壁行构建完成
    b.append('+'); // 添加最后一个墙角符号'+'
    if (y == height) { // 如果是最后一行(边界)
      return; // 直接返回,不需要构建单元格内容行
    } // 需要构建单元格内容行
    for (int x = 0; x < width; x++) { // 遍历该行的所有列
      b2.append(lefts[c0 + x] ? ' ' : '|') // 如果左侧无墙添加' ',有墙添加'|'
          .append(cellContent.get(c0 + x)); // 添加单元格内容(空格或区域号或解路径标记)
    } // 单元格内容行构建完成
    b2.append('|'); // 添加最右侧的墙壁'|'
  } // 行生成方法完成

  public Maze layout(Random random, PrintWriter pw) { // 生成迷宫布局,使用随机化Prim算法,random是随机数生成器,pw用于调试输出
    int[] candidates = // 创建候选墙壁数组,存储所有可能移除的墙壁
        new int[width * height - width // 水平方向候选墙数量(总单元格数减去最后一行)
            + width * height - height]; // 垂直方向候选墙数量(总单元格数减去最后一列)
    int z = 0; // 候选墙数组的索引
    for (int y = 0, c = 0; y < height; y++) { // 遍历所有行,y是行号,c是单元格索引
      for (int x = 0; x < width; x++) { // 遍历所有列,x是列号
        if (x > 0) { // 如果不是第一列
          candidates[z++] = c; // 添加左侧墙壁候选(偶数索引表示左墙)
        } // 左墙候选添加完成
        ++c; // 单元格索引递增
        if (y > 0) { // 如果不是第一行
          candidates[z++] = c; // 添加上方墙壁候选(奇数索引表示上墙)
        } // 上墙候选添加完成
        ++c; // 单元格索引递增
      } // 该行遍历完成
    } // 所有单元格遍历完成
    assert z == candidates.length; // 断言候选墙数量正确
    shuffle(random, candidates); // 随机打乱候选墙的顺序

    for (int candidate : candidates) { // 遍历所有候选墙
      final boolean up = (candidate & 1) != 0; // 通过最低位判断是上墙(true)还是左墙(false)
      final int c = candidate >> 1; // 右移一位获取单元格索引
      if (up) { // 如果是上方墙壁
        int region = region(c - width); // 获取上方单元格的区域编号

        // make sure we are not joining the same region, that is, making
        // a cycle
        if (region(c) != region) { // 检查当前单元格和上方单元格是否属于不同区域(避免形成环路)
          ups[c] = true; // 移除上方墙壁(设置为true表示可通过)
          regions[regions[c]] = region; // 并查集合并:将当前单元格的区域指向上方单元格的区域
          regions[c] = region; // 更新当前单元格的区域为上方单元格的区域
          if (DEBUG) { // 如果开启调试模式
            pw.println("up " + c); // 打印移除上方墙壁的调试信息
          } // 调试信息输出完成
        } else { // 两个单元格属于同一区域
          if (DEBUG) { // 如果开启调试模式
            pw.println("cannot remove top wall at " + c); // 打印不能移除墙壁的调试信息
          } // 调试信息输出完成
        } // 上方墙壁处理完成
      } else { // 如果是左侧墙壁
        int region = region(c - 1); // 获取左侧单元格的区域编号

        // make sure we are not joining the same region, that is, making
        // a cycle
        if (region(c) != region) { // 检查当前单元格和左侧单元格是否属于不同区域(避免形成环路)
          lefts[c] = true; // 移除左侧墙壁(设置为true表示可通过)
          regions[regions[c]] = region; // 并查集合并:将当前单元格的区域指向左侧单元格的区域
          regions[c] = region; // 更新当前单元格的区域为左侧单元格的区域
          if (DEBUG) { // 如果开启调试模式
            pw.println("left " + c); // 打印移除左侧墙壁的调试信息
          } // 调试信息输出完成
        } else { // 两个单元格属于同一区域
          if (DEBUG) { // 如果开启调试模式
            pw.println("cannot remove left wall at " + c); // 打印不能移除墙壁的调试信息
          } // 调试信息输出完成
        } // 左侧墙壁处理完成
      } // 候选墙处理完成
      if (DEBUG) { // 如果开启调试模式
        print(pw, false); // 打印带区域号的迷宫
        print(pw, true); // 打印纯迷宫结构
      } // 调试输出完成
    } // 所有候选墙处理完成,迷宫生成完毕
    return this; // 返回当前Maze对象,支持链式调用
  } // 迷宫布局生成方法完成

  Set<Integer> solve(int x, int y) { // 求解迷宫,从指定起点(x,y)到终点(右下角),返回解路径的单元格集合
    int c = y * width + x; // 计算起点单元格的索引
    final int target = regions.length - 1; // 目标单元格是最后一个单元格(右下角)
    Direction d = Direction.UP; // 当前探索方向,初始为向上
    final List<Integer> list = new ArrayList<>(); // 路径列表,存储已访问的单元格
    final Deque<Direction> fromStack = new ArrayDeque<>(); // 来源方向栈,用于回溯时记录从哪个方向来的
    final Deque<Direction> directionStack = new ArrayDeque<>(); // 方向栈,用于回溯时记录下一个要探索的方向
    Direction from = Direction.BACKTRACK; // 当前移动来源方向,初始为回溯
    int cNext = 0; // 下一个要移动到的单元格索引
    Direction dNext = Direction.UP; // 下一个要探索的方向
    boolean move = false; // 是否可以移动的标志
    for (;;) { // 无限循环,直到找到解
      switch (d) { // 根据当前方向进行不同的处理
      case UP: // 向上探索
        // try to go up
        move = from != Direction.DOWN && ups[c]; // 检查是否可以向上移动:不是从下方来的且上方无墙
        cNext = c - width; // 计算上方单元格的索引
        dNext = Direction.LEFT; // 设置下一个探索方向为向左
        break; // case UP结束
      case LEFT: // 向左探索
        // try to go left
        move = from != Direction.RIGHT && lefts[c]; // 检查是否可以向左移动:不是从右方来的且左侧无墙
        cNext = c - 1; // 计算左侧单元格的索引
        dNext = Direction.DOWN; // 设置下一个探索方向为向下
        break; // case LEFT结束
      case DOWN: // 向下探索
        // try to go down
        move = from != Direction.UP && c + width < regions.length // 检查是否可以向下移动:不是从上方来的且在边界内
            && ups[c + width]; // 且下方无墙(注意:ups存储的是单元格上方的墙,所以下方单元格的up墙就是当前单元格的下方墙)
        cNext = c + width; // 计算下方单元格的索引
        dNext = Direction.RIGHT; // 设置下一个探索方向为向右
        break; // case DOWN结束
      case RIGHT: // 向右探索
        move = from != Direction.LEFT && c % width < width - 1 // 检查是否可以向右移动:不是从左方来的且在边界内
            && lefts[c + 1]; // 且右侧无墙(注意:lefts存储的是单元格左侧的墙,所以右侧单元格的left墙就是当前单元格的右侧墙)
        cNext = c + 1; // 计算右侧单元格的索引
        dNext = Direction.BACKTRACK; // 设置下一个探索方向为回溯
        break; // case RIGHT结束
      case BACKTRACK: // 回溯
        move = false; // 回溯时不移动
        do { // 循环回溯,直到找到一个可以继续探索的方向
          c = list.remove(list.size() - 1); // 从路径中移除最后一个单元格(回退一步)
          dNext = directionStack.pop(); // 弹出下一个要探索的方向
          from = fromStack.pop(); // 弹出来源方向
        } while (dNext == Direction.BACKTRACK); // 如果下一个方向还是回溯,继续回退
        break; // case BACKTRACK结束
      default: // 默认情况(不应该发生)
        break; // 直接跳出
      } // switch语句结束
      if (move) { // 如果可以移动
        directionStack.push(dNext); // 将下一个探索方向压入方向栈
        fromStack.push(from); // 将来源方向压入来源栈
        list.add(c); // 将当前单元格添加到路径
        if (cNext == target) { // 如果下一个单元格就是目标
          list.add(cNext); // 将目标单元格添加到路径
          return new LinkedHashSet<>(list); // 返回路径的LinkedHashSet(保持插入顺序)
        } // 找到解,返回
        from = d; // 更新来源方向为当前方向
        d = Direction.UP; // 重置探索方向为向上(从新位置开始探索)
        c = cNext; // 更新当前位置为下一个单元格
      } else { // 如果不能移动
        d = dNext; // 切换到下一个方向
      } // 移动/方向切换完成
    } // 无限循环,直到找到解
  } // 迷宫求解方法完成

  /** Direction. */
  // 方向枚举,定义迷宫中可以移动的四个基本方向以及回溯方向
  private enum Direction { // 定义Direction枚举
    UP, LEFT, DOWN, RIGHT, BACKTRACK // 上、左、下、右四个移动方向,以及BACKTRACK回溯方向
  } // 枚举定义完成

  /**
   * Randomly permutes the members of an array. Based on the Fisher-Yates
   * algorithm.
   *
   * @param random Random number generator
   * @param ints Array of integers to shuffle
   */
  // 随机打乱数组,使用Fisher-Yates洗牌算法,还可以根据horizontal和spiral标志调整墙壁分布
  private void shuffle(Random random, int[] ints) { // 随机打乱整数数组
    for (int i = ints.length - 1; i > 0; i--) { // 从数组末尾开始遍历
      int j = random.nextInt(i + 1); // 生成0到i的随机索引
      int t = ints[j]; // 交换ints[i]和ints[j]
      ints[j] = ints[i]; // 交换完成
      ints[i] = t; // 交换完成
    } // Fisher-Yates洗牌完成,数组已随机打乱

    // move even walls (left) towards the start, so we end up with
    // long horizontal corridors
    if (horizontal) { // 如果horizontal标志为true
      for (int i = 2; i < ints.length; i++) { // 从第三个元素开始遍历
        if (ints[i] % 2 == 0) { // 如果是偶数(表示左墙)
          int j = random.nextInt(i); // 生成0到i-1的随机索引
          int t = ints[j]; // 将左墙向前移动(与前面的元素交换)
          ints[j] = ints[i]; // 交换完成
          ints[i] = t; // 交换完成
        } // 左墙向前移动完成
      } // 水平走廊调整完成
    } // horizontal标志处理完成

    // move walls towards the edges towards the start
    if (spiral) { // 如果spiral标志为true
      for (int z = 0; z < 5; z++) { // 重复5次调整
        for (int i = 2; i < ints.length; i++) { // 从第三个元素开始遍历
          int x = ints[i] / 2 % width; // 计算墙壁所在单元格的x坐标
          int y = ints[i] / 2 / width; // 计算墙壁所在单元格的y坐标
          int xMin = Math.min(x, width - x); // 计算到左右边界的最小距离
          int yMin = Math.min(y, height - y); // 计算到上下边界的最小距离
          if (ints[i] % 2 == (xMin < yMin ? 1 : 0)) { // 根据到边界的距离决定保留哪种墙壁
            int j = random.nextInt(i); // 生成0到i-1的随机索引
            int t = ints[j]; // 将墙壁向前移动
            ints[j] = ints[i]; // 交换完成
            ints[i] = t; // 交换完成
          } // 螺旋调整完成
        } // 内层循环完成
      } // 外层循环完成
    } // spiral标志处理完成
  } // 洗牌方法完成

  /** Callback to get what to print in a particular cell. Must be two characters
   * long, usually two spaces. */
  // 单元格内容回调接口,用于定义在迷宫打印时每个单元格显示什么内容
  interface CellContent { // 定义CellContent接口
    CellContent SPACE = c -> "  "; // 默认实现,单元格显示两个空格

    String get(int c); // 根据单元格索引返回要显示的内容(必须是两个字符)
  } // 接口定义完成
} // Maze类定义完成