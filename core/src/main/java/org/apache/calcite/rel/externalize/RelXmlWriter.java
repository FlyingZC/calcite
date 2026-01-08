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
package org.apache.calcite.rel.externalize; // 包声明：定义RelXmlWriter类所在的包路径，该包负责关系表达式(RelNode)的外部化操作

import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式的基础接口
import org.apache.calcite.sql.SqlExplainLevel; // 导入SqlExplainLevel枚举，定义SQL解释的详细级别
import org.apache.calcite.util.Pair; // 导入Pair工具类，用于存储键值对
import org.apache.calcite.util.XmlOutput; // 导入XmlOutput工具类，用于生成XML格式的输出

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，标记可能为null的值

import java.io.PrintWriter; // 导入PrintWriter类，用于字符输出流
import java.util.ArrayList; // 导入ArrayList类，动态数组实现
import java.util.List; // 导入List接口，表示有序集合
import java.util.Objects; // 导入Objects工具类，提供对象操作方法

/**
 * Callback for a relational expression to dump in XML format. // 类注释：RelXmlWriter是一个回调类，用于将关系表达式(RelNode)以XML格式转储输出
 * 该类继承自RelWriterImpl，实现了将关系代数树转换为XML文档的功能
 * 支持两种XML格式：通用XML(element-oriented)和特定XML(attribute-oriented)
 */
public class RelXmlWriter extends RelWriterImpl { // 类定义：RelXmlWriter继承RelWriterImpl，实现XML格式的关系表达式写入器
  //~ Instance fields -------------------------------------------------------- // 成员变量区域的分隔符标记

  private final XmlOutput xmlOutput; // 成员变量：xmlOutput是XmlOutput对象，用于生成和输出XML格式的内容，final修饰表示不可变
  final boolean generic = true; // 成员变量：generic是布尔值，标记是否使用通用XML格式，true表示使用通用格式(element-oriented)，false表示使用特定格式(attribute-oriented)

  //~ Constructors ----------------------------------------------------------- // 构造方法区域的分隔符标记

  // TODO jvs 23-Dec-2005:  honor detail level.  The current inheritance // TODO注释：需要遵守detailLevel参数，但当前的继承结构使得在不重复代码的情况下难以实现
  // structure makes this difficult without duplication; need to factor // 需要在渲染之前提取属性过滤逻辑
  // out the filtering of attributes before rendering. // 这个TODO提示未来需要重构以支持不同详细级别的输出

  public RelXmlWriter(PrintWriter pw, SqlExplainLevel detailLevel) { // 构造方法：创建RelXmlWriter实例，参数pw是输出流，detailLevel是解释详细级别
    super(pw, detailLevel, true); // 调用父类RelWriterImpl的构造方法，传入输出流、详细级别和true(表示使用缩进格式)
    xmlOutput = new XmlOutput(pw); // 初始化XmlOutput对象，使用提供的PrintWriter作为输出目标
    xmlOutput.setGlob(true); // 设置XmlOutput的全局模式为true，启用全局配置
    xmlOutput.setCompact(false); // 设置XmlOutput的紧凑模式为false，输出格式化的XML(带缩进和换行)
  } // 构造方法结束

  //~ Methods ---------------------------------------------------------------- // 方法区域的分隔符标记

  @Override protected void explain_( // 方法：explain_是受保护的方法，覆盖父类的方法，用于解释单个关系节点
      RelNode rel, // 参数rel：要解释的关系表达式节点
      List<Pair<String, @Nullable Object>> values) { // 参数values：属性名和属性值的键值对列表
    if (generic) { // 判断：如果generic标志为true
      explainGeneric(rel, values); // 调用explainGeneric方法，生成通用格式的XML(element-oriented)
    } else { // 否则
      explainSpecific(rel, values); // 调用explainSpecific方法，生成特定格式的XML(attribute-oriented)
    } // if-else结束
  } // explain_方法结束

  /**
   * Generates generic XML (sometimes called 'element-oriented XML'). Like // 方法注释：explainGeneric生成通用XML格式(也称为面向元素的XML)，示例格式如下：
   * this: // 该格式使用<Property>子元素来表示属性
   *
   * <blockquote> // 代码块开始
   * <code> // 示例XML代码
   * &lt;RelNode id="1" type="Join"&gt;<br> // RelNode元素，包含id和type属性
   * &nbsp;&nbsp;&lt;Property name="condition"&gt;EMP.DEPTNO = // Property子元素，name属性指定属性名，CDATA内容是属性值
   * DEPT.DEPTNO&lt;/Property&gt;<br> // 每个属性都是一个独立的Property元素
   * &nbsp;&nbsp;&lt;Inputs&gt;<br> // Inputs元素包含所有输入节点
   * &nbsp;&nbsp;&nbsp;&nbsp;&lt;RelNode id="2" type="Project"&gt;<br> // 嵌套的RelNode元素表示输入节点
   * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;Property name="expr1"&gt;x + // 输入节点也有自己的Property元素
   * y&lt;/Property&gt;<br> // 多个Property元素
   * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;Property
   * name="expr2"&gt;45&lt;/Property&gt;<br> // 属性值可以是表达式或常量
   * &nbsp;&nbsp;&nbsp;&nbsp;&lt;/RelNode&gt;<br> // 输入节点结束
   * &nbsp;&nbsp;&nbsp;&nbsp;&lt;RelNode id="3" type="TableAccess"&gt;<br> // 另一个输入节点
   * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;Property
   * name="table"&gt;SALES.EMP&lt;/Property&gt;<br> // 表访问节点的属性
   * &nbsp;&nbsp;&nbsp;&nbsp;&lt;/RelNode&gt;<br> // 输入节点结束
   * &nbsp;&nbsp;&lt;/Inputs&gt;<br> // Inputs元素结束
   * &lt;/RelNode&gt;</code> // RelNode元素结束
   * </blockquote> // 代码块结束
   *
   * @param rel    Relational expression // 参数说明：rel是关系表达式节点
   * @param values List of term-value pairs // 参数说明：values是属性名和属性值的键值对列表
   */
  private void explainGeneric( // 方法：explainGeneric私有方法，生成通用XML格式(element-oriented)
      RelNode rel, // 参数rel：要解释的关系表达式节点
      List<Pair<String, @Nullable Object>> values) { // 参数values：属性名和属性值的键值对列表
    String relType = rel.getRelTypeName(); // 获取关系节点的类型名称(如"Join"、"Project"等)
    xmlOutput.beginBeginTag("RelNode"); // 开始输出<RelNode开始标签，准备添加属性
    xmlOutput.attribute("type", relType); // 添加type属性，值为关系节点的类型名称

    xmlOutput.endBeginTag("RelNode"); // 完成<RelNode>开始标签的输出，关闭标签开始部分

    final List<RelNode> inputs = new ArrayList<>(); // 创建输入节点列表，用于存储该关系节点的所有输入
    for (Pair<String, @Nullable Object> pair : values) { // 遍历所有属性键值对
      if (pair.right instanceof RelNode) { // 判断：如果值是RelNode类型(表示是输入节点)
        inputs.add((RelNode) pair.right); // 将该RelNode添加到输入列表中
        continue; // 跳过后续处理，继续下一个键值对
      } // if结束
      if (pair.right == null) { // 判断：如果值为null
        continue; // 跳过该属性，继续下一个键值对
      } // if结束
      xmlOutput.beginBeginTag("Property"); // 开始输出<Property开始标签
      xmlOutput.attribute("name", pair.left); // 添加name属性，值为属性名
      xmlOutput.endBeginTag("Property"); // 完成<Property>开始标签的输出
      xmlOutput.cdata(pair.right.toString()); // 输出属性值作为CDATA内容(可以包含特殊字符)
      xmlOutput.endTag("Property"); // 输出</Property>结束标签
    } // for循环结束
    xmlOutput.beginTag("Inputs", null); // 输出<Inputs>开始标签，null表示无属性
    spacer.add(2); // 增加缩进级别(2个单位)，使嵌套内容更易读
    for (RelNode input : inputs) { // 遍历所有输入节点
      input.explain(this); // 递归调用输入节点的explain方法，使用当前writer继续解释
    } // for循环结束
    spacer.subtract(2); // 减少缩进级别，恢复到之前的缩进
    xmlOutput.endTag("Inputs"); // 输出</Inputs>结束标签
    xmlOutput.endTag("RelNode"); // 输出</RelNode>结束标签
  } // explainGeneric方法结束

  /**
   * Generates specific XML (sometimes called 'attribute-oriented XML'). Like // 方法注释：explainSpecific生成特定XML格式(也称为面向属性的XML)，示例格式如下：
   * this: // 该格式使用XML属性来表示所有属性，更加紧凑
   *
   * <blockquote><pre> // 代码块开始
   * &lt;Join condition="EMP.DEPTNO = DEPT.DEPTNO"&gt; // Join元素，condition属性直接在标签上
   *   &lt;Project expr1="x + y" expr2="42"&gt; // Project元素，所有属性都在标签上
   *   &lt;TableAccess table="SALES.EMPS"&gt; // TableAccess元素，table属性在标签上
   * &lt;/Join&gt; // Join元素结束
   * </pre></blockquote> // 代码块结束
   *
   * @param rel    Relational expression // 参数说明：rel是关系表达式节点
   * @param values List of term-value pairs // 参数说明：values是属性名和属性值的键值对列表
   */
  private void explainSpecific( // 方法：explainSpecific私有方法，生成特定XML格式(attribute-oriented)
      RelNode rel, // 参数rel：要解释的关系表达式节点
      List<Pair<String, @Nullable Object>> values) { // 参数values：属性名和属性值的键值对列表
    String tagName = rel.getRelTypeName(); // 获取关系节点的类型名称作为XML标签名(如"Join"、"Project")
    xmlOutput.beginBeginTag(tagName); // 开始输出<tagName开始标签，准备添加属性
    xmlOutput.attribute("id", rel.getId() + ""); // 添加id属性，值为关系节点的ID(转换为字符串)

    for (Pair<String, @Nullable Object> value : values) { // 遍历所有属性键值对
      if (value.right instanceof RelNode) { // 判断：如果值是RelNode类型(表示是输入节点)
        continue; // 跳过该属性，因为输入节点会作为子元素处理
      } // if结束
      xmlOutput.attribute( // 添加属性到当前XML标签
          value.left, // 属性名
          Objects.toString(value.right)); // 属性值，使用Objects.toString安全转换
    } // for循环结束
    xmlOutput.endBeginTag(tagName); // 完成<tagName>开始标签的输出
    spacer.add(2); // 增加缩进级别(2个单位)，使嵌套内容更易读
    for (RelNode input : rel.getInputs()) { // 遍历关系节点的所有输入节点
      input.explain(this); // 递归调用输入节点的explain方法，使用当前writer继续解释
    } // for循环结束
    spacer.subtract(2); // 减少缩进级别，恢复到之前的缩进
  } // explainSpecific方法结束
} // RelXmlWriter类结束
