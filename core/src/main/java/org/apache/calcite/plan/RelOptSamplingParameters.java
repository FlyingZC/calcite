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
package org.apache.calcite.plan; // 声明包名,该类属于org.apache.calcite.plan包,是Calcite优化器包的一部分

import java.math.BigDecimal; // 导入BigDecimal类,用于精确表示采样率,避免浮点数精度问题

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法,用于参数非空校验

/**
 * RelOptSamplingParameters represents the parameters necessary to produce a
 * sample of a relation.
 * RelOptSamplingParameters类表示对关系(表)进行采样所需的所有参数
 *
 * <p>Its parameters are derived from the SQL 2003 TABLESAMPLE clause.
 * 该类的参数来源于SQL 2003标准的TABLESAMPLE子句
 * TABLESAMPLE是SQL标准中用于从表中抽取样本数据的语法,例如:SELECT * FROM table TABLESAMPLE BERNOULLI(10)
 */
public class RelOptSamplingParameters { // 定义公共类RelOptSamplingParameters,用于封装关系采样的参数
  //~ Instance fields --------------------------------------------------------
  // 实例字段区域,以下是该类的成员变量

  private final boolean bernoulli; // 采样方法标志:true表示伯努利采样(Bernoulli采样),false表示系统采样(System采样)。伯努利采样保证每行被选中的概率独立且相等,系统采样由具体实现决定
  public final BigDecimal sampleRate; // 采样率,使用BigDecimal类型以保证精度,表示采样的比例(如0.1表示10%)。范围通常在0.0到1.0之间,但不能为0或1
  private final boolean repeatable; // 可重复性标志:true表示采样结果应该是可重复的,false表示每次采样可能不同。可重复性要求关系的内容和结构没有变化
  private final int repeatableSeed; // 可重复采样种子值,当repeatable为true时使用。相同的采样模式、采样率和种子值应该产生相同的采样结果

  //~ Constructors -----------------------------------------------------------
  // 构造方法区域,以下是该类的构造函数

  public RelOptSamplingParameters(boolean bernoulli, BigDecimal sampleRate, // 构造方法1:主构造方法,使用BigDecimal类型的采样率
      boolean repeatable, int repeatableSeed) { // 参数说明:bernoulli-采样方法标志,sampleRate-采样率,repeatable-可重复性标志,repeatableSeed-种子值
    this.bernoulli = bernoulli; // 将传入的采样方法标志赋值给成员变量
    this.sampleRate = requireNonNull(sampleRate, "sampleRate"); // 将传入的采样率赋值给成员变量,并使用requireNonNull进行非空校验,如果为null则抛出NullPointerException
    this.repeatable = repeatable; // 将传入的可重复性标志赋值给成员变量
    this.repeatableSeed = repeatableSeed; // 将传入的种子值赋值给成员变量
  } // 构造方法结束

  @Deprecated // to be removed before 2.0 // 标记为已废弃,将在2.0版本前移除
  public RelOptSamplingParameters( // 构造方法2:已废弃的构造方法,使用float类型的采样率
      boolean bernoulli, // 参数1:采样方法标志
      float sampleRate, // 参数2:采样率,使用float类型(精度较低,已废弃)
      boolean isRepeatable, // 参数3:可重复性标志(注意参数名与主构造方法不同,使用isRepeatable)
      int repeatableSeed) { // 参数4:种子值
    this(bernoulli, BigDecimal.valueOf(sampleRate), isRepeatable, // 调用主构造方法,将float转换为BigDecimal类型,保证精度
        repeatableSeed); // 传递种子值参数
  } // 已废弃的构造方法结束

  //~ Methods ----------------------------------------------------------------
  // 方法区域,以下是该类的成员方法

  /**
   * Indicates whether Bernoulli or system sampling should be performed.
   * 指示应该执行伯努利采样还是系统采样
   * Bernoulli sampling requires the decision whether to include each row in
   * the sample to be independent across rows. System sampling allows
   * implementation-dependent behavior.
   * 伯努利采样要求决定是否将每一行包含在样本中的决策在行之间是独立的。系统采样允许依赖于具体实现的行为。
   *
   * @return true if Bernoulli sampling is configured, false for system
   * sampling
   * 返回值:true如果配置为伯努利采样,false如果配置为系统采样
   */
  public boolean isBernoulli() { // 判断是否使用伯努利采样的方法
    return bernoulli; // 返回成员变量bernoulli的值
  } // 方法结束

  /**
   * Returns the sampling percentage. For Bernoulli sampling, the sampling
   * percentage is the likelihood that any given row will be included in the
   * sample. For system sampling, the sampling percentage indicates (roughly)
   * what percentage of the rows will appear in the sample.
   * 返回采样百分比。对于伯努利采样,采样百分比是任何给定行被包含在样本中的可能性。对于系统采样,采样百分比表示(大约)样本中将出现的行的百分比。
   *
   * @return the sampling percentage between 0.0 and 1.0, exclusive
   * 返回值:采样百分比,范围在0.0到1.0之间,不包括0.0和1.0
   */
  @Deprecated // to be removed before 2.0 // 标记为已废弃,将在2.0版本前移除
  public float getSamplingPercentage() { // 获取采样百分比的方法,返回float类型(已废弃,建议直接使用sampleRate字段)
    return sampleRate.floatValue(); // 将BigDecimal类型的sampleRate转换为float类型返回,可能会有精度损失
  } // 方法结束

  /**
   * Indicates whether the sample results should be repeatable. Sample results
   * are only required to repeat if no changes have been made to the
   * relation's content or structure. If the sample is configured to be
   * repeatable, then a user-specified seed value can be obtained via
   * {@link #getRepeatableSeed()}.
   * 指示采样结果是否应该是可重复的。只有当关系的内容或结构没有发生变化时,才要求采样结果重复。如果采样被配置为可重复的,那么可以通过getRepeatableSeed()方法获取用户指定的种子值。
   *
   * @return true if the sample results should be repeatable
   * 返回值:true如果采样结果应该是可重复的
   */
  public boolean isRepeatable() { // 判断采样结果是否可重复的方法
    return repeatable; // 返回成员变量repeatable的值
  } // 方法结束

  /**
   * If {@link #isRepeatable()} returns <code>true</code>, this method returns a
   * user-specified seed value. Samples of the same, unmodified relation
   * should be identical if the sampling mode, sampling percentage and
   * repeatable seed are the same.
   * 如果isRepeatable()返回true,此方法返回用户指定的种子值。如果采样模式、采样百分比和可重复种子相同,则相同、未修改的关系的样本应该是相同的。
   *
   * @return seed value for repeatable samples
   * 返回值:用于可重复样本的种子值
   */
  public int getRepeatableSeed() { // 获取可重复采样种子值的方法
    return repeatableSeed; // 返回成员变量repeatableSeed的值
  } // 方法结束
} // 类定义结束
