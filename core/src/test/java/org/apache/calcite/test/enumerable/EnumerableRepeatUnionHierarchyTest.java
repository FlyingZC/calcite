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
// 声明包名，表示这个类属于org.apache.calcite.test.enumerable包，用于测试可枚举相关的功能
package org.apache.calcite.test.enumerable;

// 导入EnumerableRepeatUnion类，这是Calcite中用于实现可重复联合的物理操作符，支持递归查询
import org.apache.calcite.adapter.enumerable.EnumerableRepeatUnion;
// 导入ReflectiveSchema类，用于通过反射将Java对象转换为Calcite的Schema
import org.apache.calcite.adapter.java.ReflectiveSchema;
// 导入RelNode接口，表示关系代数树的节点，是Calcite中所有关系操作符的基类
import org.apache.calcite.rel.RelNode;
// 导入JoinRelType枚举，定义了连接的类型，包括INNER、LEFT、RIGHT、FULL等
import org.apache.calcite.rel.core.JoinRelType;
// 导入RexNode类，表示行表达式（Row Expression），用于在关系代数中描述表达式
import org.apache.calcite.rex.RexNode;
// 导入Schema接口，表示Calcite中的模式（Schema），包含表、函数等元数据
import org.apache.calcite.schema.Schema;
// 导入CalciteAssert类，用于编写Calcite测试的断言工具类
import org.apache.calcite.test.CalciteAssert;
// 导入HierarchySchema类，用于测试层级结构的数据模式
import org.apache.calcite.test.schemata.hr.HierarchySchema;
// 导入RelBuilder类，用于构建关系代数树的构建器，提供了流式API来构建复杂的查询计划
import org.apache.calcite.tools.RelBuilder;

// 导入ParameterizedTest注解，用于标记参数化测试方法
import org.junit.jupiter.params.ParameterizedTest;
// 导入MethodSource注解，用于指定参数化测试的数据源方法
import org.junit.jupiter.params.provider.MethodSource;

// 导入ArrayList类，用于动态数组的实现
import java.util.ArrayList;
// 导入Arrays类，用于数组操作的实用工具类
import java.util.Arrays;
// 导入List接口，表示有序的集合
import java.util.List;
// 导入Function接口，表示函数式编程中的函数
import java.util.function.Function;

/**
 * Unit tests for
 * {@link EnumerableRepeatUnion}
 * <a href="https://issues.apache.org/jira/browse/CALCITE-2812">[CALCITE-2812]
 * Add algebraic operators to allow expressing recursive queries</a>.
 */
// 这是一个针对EnumerableRepeatUnion操作符的单元测试类
// EnumerableRepeatUnion是Calcite中用于实现递归查询的可枚举重复联合操作符
// 该类测试了在层级结构（如组织架构）中使用递归查询的各种场景
// 相关JIRA问题：CALCITE-2812，目标是添加代数操作符以支持递归查询的表达
class EnumerableRepeatUnionHierarchyTest {

  // Tests for the following hierarchy:
  //      Emp1
  //      /  \
  //    Emp2  Emp4
  //    /  \
  // Emp3   Emp5
  // 注释说明：测试使用的层级结构如下：
  // Emp1是根节点，有两个子节点Emp2和Emp4
  // Emp2有两个子节点Emp3和Emp5
  // Emp4和Emp3、Emp5是叶子节点
  // 这个层级结构用于测试向上（祖先）和向下（后代）遍历的功能

  // 定义员工1的字符串表示，包含员工ID和姓名，用于测试用例中
  private static final String EMP1 = "empid=1; name=Emp1";
  // 定义员工2的字符串表示，包含员工ID和姓名，用于测试用例中
  private static final String EMP2 = "empid=2; name=Emp2";
  // 定义员工3的字符串表示，包含员工ID和姓名，用于测试用例中
  private static final String EMP3 = "empid=3; name=Emp3";
  // 定义员工4的字符串表示，包含员工ID和姓名，用于测试用例中
  private static final String EMP4 = "empid=4; name=Emp4";
  // 定义员工5的字符串表示，包含员工ID和姓名，用于测试用例中
  private static final String EMP5 = "empid=5; name=Emp5";

  // 定义包含员工ID为1的整数数组，用于作为查询的起始ID
  private static final int[] ID1 = new int[]{1};
  // 将ID1数组转换为字符串表示，用于测试名称显示
  private static final String ID1_STR = Arrays.toString(ID1);
  // 定义包含员工ID为2的整数数组，用于作为查询的起始ID
  private static final int[] ID2 = new int[]{2};
  // 将ID2数组转换为字符串表示，用于测试名称显示
  private static final String ID2_STR = Arrays.toString(ID2);
  // 定义包含员工ID为3的整数数组，用于作为查询的起始ID
  private static final int[] ID3 = new int[]{3};
  // 将ID3数组转换为字符串表示，用于测试名称显示
  private static final String ID3_STR = Arrays.toString(ID3);
  // 定义包含员工ID为4的整数数组，用于作为查询的起始ID
  private static final int[] ID4 = new int[]{4};
  // 将ID4数组转换为字符串表示，用于测试名称显示
  private static final String ID4_STR = Arrays.toString(ID4);
  // 定义包含员工ID为5的整数数组，用于作为查询的起始ID
  private static final int[] ID5 = new int[]{5};
  // 将ID5数组转换为字符串表示，用于测试名称显示
  private static final String ID5_STR = Arrays.toString(ID5);
  // 定义包含员工ID为3和5的整数数组，用于测试多起始点的情况
  private static final int[] ID3_5 = new int[]{3, 5};
  // 将ID3_5数组转换为字符串表示，用于测试名称显示
  private static final String ID3_5_STR = Arrays.toString(ID3_5);
  // 定义包含员工ID为1和3的整数数组，用于测试多起始点的情况
  private static final int[] ID1_3 = new int[]{1, 3};
  // 将ID1_3数组转换为字符串表示，用于测试名称显示
  private static final String ID1_3_STR = Arrays.toString(ID1_3);

  // 定义一个数据提供方法，用于参数化测试
  // 返回一个可迭代的Object数组，每个数组元素代表一组测试参数
  public static Iterable<Object[]> data() {

    // 返回一个包含多组测试数据的列表
    // 每组数据包含：all标志、起始ID数组、起始ID字符串、是否向上遍历标志、最大深度、期望结果数组
    return Arrays.asList(new Object[][] {
        // 测试用例1：all=true，从ID1开始，向上遍历，最大深度-1（无限制），期望结果只有EMP1
        { true, ID1, ID1_STR, true, -1, new String[]{EMP1} },
        // 测试用例2：all=true，从ID2开始，向上遍历，期望结果为EMP2及其经理EMP1
        { true, ID2, ID2_STR, true, -2, new String[]{EMP2, EMP1} },
        // 测试用例3：all=true，从ID3开始，向上遍历，期望结果为EMP3、经理EMP2、EMP2的经理EMP1
        { true, ID3, ID3_STR, true, -1, new String[]{EMP3, EMP2, EMP1} },
        // 测试用例4：all=true，从ID4开始，向上遍历，期望结果为EMP4及其经理EMP1
        { true, ID4, ID4_STR, true, -5, new String[]{EMP4, EMP1} },
        // 测试用例5：all=true，从ID5开始，向上遍历，期望结果为EMP5、经理EMP2、EMP2的经理EMP1
        { true, ID5, ID5_STR, true, -1, new String[]{EMP5, EMP2, EMP1} },
        // 测试用例6：all=true，从ID3开始，向上遍历，最大深度0（只包含起始节点），期望结果只有EMP3
        { true, ID3, ID3_STR, true,  0, new String[]{EMP3} },
        // 测试用例7：all=true，从ID3开始，向上遍历，最大深度1，期望结果为EMP3及其经理EMP2
        { true, ID3, ID3_STR, true,  1, new String[]{EMP3, EMP2} },
        // 测试用例8：all=true，从ID3开始，向上遍历，最大深度2，期望结果为EMP3、EMP2、EMP1
        { true, ID3, ID3_STR, true,  2, new String[]{EMP3, EMP2, EMP1} },
        // 测试用例9：all=true，从ID3开始，向上遍历，最大深度10（超过实际深度），期望结果为完整路径
        { true, ID3, ID3_STR, true, 10, new String[]{EMP3, EMP2, EMP1} },

        // 测试用例10：all=true，从ID1开始，向下遍历，期望结果为所有后代员工
        { true, ID1, ID1_STR, false, -1, new String[]{EMP1, EMP2, EMP4, EMP3, EMP5} },
        // 测试用例11：all=true，从ID2开始，向下遍历，期望结果为EMP2及其所有后代
        { true, ID2, ID2_STR, false, -10, new String[]{EMP2, EMP3, EMP5} },
        // 测试用例12：all=true，从ID3开始，向下遍历，期望结果只有EMP3（没有下属）
        { true, ID3, ID3_STR, false, -100, new String[]{EMP3} },
        // 测试用例13：all=true，从ID4开始，向下遍历，期望结果只有EMP4（没有下属）
        { true, ID4, ID4_STR, false, -1, new String[]{EMP4} },
        // 测试用例14：all=true，从ID1开始，向下遍历，最大深度0，期望结果只有EMP1
        { true, ID1, ID1_STR, false,  0, new String[]{EMP1} },
        // 测试用例15：all=true，从ID1开始，向下遍历，最大深度1，期望结果为EMP1及其直接下属
        { true, ID1, ID1_STR, false,  1, new String[]{EMP1, EMP2, EMP4} },
        // 测试用例16：all=true，从ID1开始，向下遍历，最大深度2，期望结果为EMP1及其两代下属
        { true, ID1, ID1_STR, false,  2, new String[]{EMP1, EMP2, EMP4, EMP3, EMP5} },
        // 测试用例17：all=true，从ID1开始，向下遍历，最大深度20，期望结果为完整层级
        { true, ID1, ID1_STR, false, 20, new String[]{EMP1, EMP2, EMP4, EMP3, EMP5} },

        // tests to verify all=true vs all=false
        // 测试用例18：all=true，从ID3和ID5开始，向上遍历，期望结果包含重复项（EMP2和EMP1各出现两次）
        { true, ID3_5, ID3_5_STR, true, -1, new String[]{EMP3, EMP5, EMP2, EMP2, EMP1, EMP1} },
        // 测试用例19：all=false，从ID3和ID5开始，向上遍历，期望结果去重（EMP2和EMP1只出现一次）
        { false, ID3_5, ID3_5_STR, true, -1, new String[]{EMP3, EMP5, EMP2, EMP1} },
        // 测试用例20：all=true，从ID3和ID5开始，向上遍历，最大深度0，期望结果只有起始节点
        { true, ID3_5, ID3_5_STR, true, 0, new String[]{EMP3, EMP5} },
        // 测试用例21：all=false，从ID3和ID5开始，向上遍历，最大深度0，期望结果只有起始节点
        { false, ID3_5, ID3_5_STR, true, 0, new String[]{EMP3, EMP5} },
        // 测试用例22：all=true，从ID3和ID5开始，向上遍历，最大深度1，期望结果包含重复的EMP2
        { true, ID3_5, ID3_5_STR, true, 1, new String[]{EMP3, EMP5, EMP2, EMP2} },
        // 测试用例23：all=false，从ID3和ID5开始，向上遍历，最大深度1，期望结果去重的EMP2
        { false, ID3_5, ID3_5_STR, true, 1, new String[]{EMP3, EMP5, EMP2} },
        // 测试用例24：all=true，从ID1和ID3开始，向下遍历，期望结果包含重复的EMP3
        { true, ID1_3, ID1_3_STR, false, -1, new String[]{EMP1, EMP3, EMP2, EMP4, EMP3, EMP5} },
        // 测试用例25：all=false，从ID1和ID3开始，向下遍历，期望结果去重
        { false, ID1_3, ID1_3_STR, false, -1, new String[]{EMP1, EMP3, EMP2, EMP4, EMP5} },
    });
  }

  // 使用ParameterizedTest注解标记这是一个参数化测试方法
  // name参数定义了测试用例的显示名称，包含索引和各个参数值
  @ParameterizedTest(name = "{index} : hierarchy(startIds:{2}, ascendant:{3}, "
      + "maxDepth:{4}, all:{0})")
  // 使用MethodSource注解指定测试数据来源为data方法
  @MethodSource("data")
  // 测试层级结构查询的公共方法，接收多个参数进行测试
  // all: 是否保留重复项（true为UNION ALL，false为UNION去重）
  // startIds: 起始员工ID数组
  // startIdsStr: 起始ID的字符串表示（用于显示）
  // ascendant: 是否向上遍历（true查找祖先，false查找后代）
  // maxDepth: 最大遍历深度（-1表示无限制）
  // expected: 期望的结果数组
  public void testHierarchy(
      boolean all,
      int[] startIds,
      String startIdsStr,
      boolean ascendant,
      int maxDepth,
      String[] expected) {
    // 定义fromField变量，用于存储连接关系的源字段名
    final String fromField;
    // 定义toField变量，用于存储连接关系的目标字段名
    final String toField;
    // 根据ascendant标志判断是向上还是向下遍历
    if (ascendant) {
      // 向上遍历：从下属ID连接到经理ID
      fromField = "subordinateid"; // 源字段为下属ID
      toField = "managerid"; // 目标字段为经理ID
    } else {
      // 向下遍历：从经理ID连接到下属ID
      fromField = "managerid"; // 源字段为经理ID
      toField = "subordinateid"; // 目标字段为下属ID
    }

    // 创建一个ReflectiveSchema实例，将HierarchySchema包装为Calcite的Schema
    final Schema schema = new ReflectiveSchema(new HierarchySchema());
    // 使用CalciteAssert构建测试断言
    // withSchema: 注册名为"s"的Schema
    // withRel: 构建关系代数树，使用buildHierarchy方法构建层级查询
    // returnsOrdered: 验证查询结果与期望结果一致（按顺序比较）
    CalciteAssert.that()
        .withSchema("s", schema)
        .withRel(buildHierarchy(all, startIds, fromField, toField, maxDepth))
        .returnsOrdered(expected);
  }

  // 私有方法：构建层级查询的关系代数树
  // 返回一个函数，该函数接收RelBuilder并返回RelNode（关系代数树的根节点）
  // all: 是否保留重复项
  // startIds: 起始员工ID数组
  // fromField: 连接关系的源字段名
  // toField: 连接关系的目标字段名
  // maxDepth: 最大遍历深度
  private Function<RelBuilder, RelNode> buildHierarchy(
      boolean all,
      int[] startIds,
      String fromField,
      String toField,
      int maxDepth) {

    //   WITH RECURSIVE delta(empid, name) as (
    //       SELECT empid, name FROM emps WHERE empid IN (<startIds>)
    //     UNION [ALL]
    //       SELECT e.empid, e.name FROM delta d
    //                              JOIN hierarchies h ON d.empid = h.<fromField>
    //                              JOIN emps e        ON h.<toField> = e.empid
    //   )
    //   SELECT empid, name FROM delta
    // 注释说明：这个方法构建的SQL等价于上面的WITH RECURSIVE递归查询
    // delta是递归表，包含两个部分：
    // 1. 锚点查询：从emps表中选择起始ID对应的员工
    // 2. 递归查询：将delta与hierarchies和emps表连接，找到下一层级的员工
    // 最终返回delta表的所有结果
    return builder -> {
      // 使用RelBuilder扫描名为"s"的Schema中的"emps"表
      builder
          .scan("s", "emps");

      // 创建一个过滤器列表，用于存储多个过滤条件
      final List<RexNode> filters = new ArrayList<>();
      // 遍历起始ID数组，为每个ID创建一个相等条件
      for (int startId : startIds) {
        // 添加一个相等条件：empid字段等于当前起始ID
        filters.add(
            builder.equals(
                builder.field("empid"), // 获取empid字段
                builder.literal(startId))); // 创建字面量（常量）表达式
      }

      // 应用过滤器和投影操作
      builder
        // 应用过滤器，使用OR逻辑组合所有条件（empid等于任意一个起始ID）
        .filter(
            builder.or(filters))
        // 投影操作，选择emps表的empid和name字段
        .project(
            builder.field("emps", "empid"), // 选择empid字段
            builder.field("emps", "name")) // 选择name字段

        // 创建一个临时扫描节点，名称为"#DELTA#"，这是递归查询的锚点部分
        .transientScan("#DELTA#")
        // 扫描hierarchies表，该表存储员工之间的层级关系
        .scan("s", "hierarchies")
        // 执行内连接，将delta表与hierarchies表连接
        // 连接条件：delta的empid等于hierarchies的fromField字段
        .join(
            JoinRelType.INNER, // 连接类型为内连接
            builder.equals(
                builder.field(2, "#DELTA#", "empid"), // 获取delta表的empid字段（输入2表示从第2个输入）
                builder.field(2, "hierarchies", fromField))) // 获取hierarchies表的fromField字段
        // 扫描emps表，用于获取员工的详细信息
        .scan("s", "emps")
        // 执行内连接，将hierarchies表与emps表连接
        // 连接条件：hierarchies的toField等于emps的empid
        .join(
            JoinRelType.INNER, // 连接类型为内连接
            builder.equals(
                builder.field(2, "hierarchies", toField), // 获取hierarchies表的toField字段
                builder.field(2, "emps", "empid"))) // 获取emps表的empid字段
        // 投影操作，选择emps表的empid和name字段
        .project(
            builder.field("emps", "empid"), // 选择empid字段
            builder.field("emps", "name")) // 选择name字段
        // 创建repeatUnion操作符，实现递归查询
        // 第一个参数：临时表的名称"#DELTA#"
        // 第二个参数：all标志，true表示保留重复项（UNION ALL），false表示去重（UNION）
        // 第三个参数：最大深度，控制递归的层级数
        .repeatUnion("#DELTA#", all, maxDepth);

      // 构建并返回关系代数树的根节点
      return builder.build();
    };
  }

}