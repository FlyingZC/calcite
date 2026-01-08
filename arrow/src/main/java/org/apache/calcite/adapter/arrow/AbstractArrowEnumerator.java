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
package org.apache.calcite.adapter.arrow; // 定义包名,这是Calcite中Arrow适配器所在的包

import org.apache.calcite.linq4j.Enumerator; // 导入Calcite的LINQ枚举器接口,用于遍历数据集合
import org.apache.calcite.util.ImmutableIntList; // 导入不可变的整数列表工具类,用于存储字段索引
import org.apache.calcite.util.Util; // 导入Calcite工具类,提供异常转换等通用功能

import org.apache.arrow.vector.ValueVector; // 导入Arrow值向量接口,表示列式存储的数据结构
import org.apache.arrow.vector.VectorSchemaRoot; // 导入Arrow向量模式根,包含一组值向量和行数信息
import org.apache.arrow.vector.VectorUnloader; // 导入Arrow向量卸载器,用于从VectorSchemaRoot中提取记录批次
import org.apache.arrow.vector.ipc.ArrowFileReader; // 导入Arrow文件读取器,用于读取Arrow格式文件
import org.apache.arrow.vector.ipc.message.ArrowRecordBatch; // 导入Arrow记录批次,表示一批数据记录

import java.io.IOException; // 导入IO异常类,用于处理文件读写异常
import java.util.ArrayList; // 导入动态数组列表,用于存储值向量集合
import java.util.List; // 导入列表接口,定义集合操作规范

/**
 * Enumerator that reads from a collection of Arrow value-vectors.
 * 这是一个从Arrow值向量集合中读取数据的枚举器抽象类
 * Arrow是Apache的列式内存格式,Calcite通过这个适配器可以查询Arrow格式的数据
 * 该类作为枚举器Enumerator的实现,用于逐行遍历Arrow文件中的数据
 * 它是抽象类,具体的过滤、投影等操作由子类实现
 */
abstract class AbstractArrowEnumerator implements Enumerator<Object> { // 定义抽象枚举器类,实现Enumerator<Object>接口,泛型Object表示每行数据可以是对象或对象数组
  protected final ArrowFileReader arrowFileReader; // Arrow文件读取器,用于从Arrow文件中读取数据,final表示初始化后不可变,protected表示子类可访问
  protected final List<Integer> fields; // 字段索引列表,存储需要读取的列在Arrow文件中的索引位置,final表示不可变,用于实现列投影功能
  protected final List<ValueVector> valueVectors; // 值向量列表,存储当前批次中每个字段的值向量,每个ValueVector对应一列数据,protected表示子类可访问
  protected int currRowIndex; // 当前行索引,表示当前读取到第几行数据,初始值为-1表示还未开始读取,protected表示子类可访问
  protected int rowCount; // 当前批次的总行数,表示当前Arrow记录批次中包含多少行数据,protected表示子类可访问

  // 构造方法,初始化Arrow枚举器
  // 参数1: arrowFileReader - Arrow文件读取器,用于读取Arrow格式文件中的数据
  // 参数2: fields - 不可变整数列表,包含需要读取的字段索引,用于实现列投影,只读取指定的列
  AbstractArrowEnumerator(ArrowFileReader arrowFileReader, ImmutableIntList fields) { // 构造方法定义,接收Arrow文件读取器和字段索引列表
    this.arrowFileReader = arrowFileReader; // 将传入的Arrow文件读取器赋值给成员变量,用于后续读取数据
    this.fields = fields; // 将传入的字段索引列表赋值给成员变量,用于后续按索引获取对应的列
    this.valueVectors = new ArrayList<>(fields.size()); // 初始化值向量列表,初始容量为字段数量,避免频繁扩容,提高性能
    this.currRowIndex = -1; // 将当前行索引初始化为-1,表示还未开始读取任何行数据,第一次调用current()后会递增为0
  }

  // 抽象方法,由子类实现对Arrow记录批次的操作
  // 子类可以在此方法中实现过滤、投影、聚合等操作
  // 参数: arrowRecordBatch - Arrow记录批次,包含从VectorSchemaRoot中卸载的一批数据
  abstract void evaluateOperator(ArrowRecordBatch arrowRecordBatch); // 抽象方法定义,子类必须实现该方法来处理数据批次

  // 加载下一个Arrow记录批次的方法
  // 从Arrow文件中读取一批数据,并将值向量添加到valueVectors列表中
  // 同时会调用evaluateOperator()方法让子类处理这批数据
  protected void loadNextArrowBatch() { // 定义加载下一个批次的方法,protected表示只有本类和子类可以调用
    try { // 使用try-catch捕获可能发生的IO异常
      final VectorSchemaRoot vsr = arrowFileReader.getVectorSchemaRoot(); // 从Arrow文件读取器中获取向量模式根,VectorSchemaRoot包含所有列的值向量和行数
      for (int i : fields) { // 遍历需要读取的字段索引列表
        this.valueVectors.add(vsr.getVector(i)); // 根据字段索引从VectorSchemaRoot中获取对应的值向量,并添加到valueVectors列表中
      }
      this.rowCount = vsr.getRowCount(); // 从VectorSchemaRoot中获取当前批次的行数,赋值给rowCount成员变量
      VectorUnloader vectorUnloader = new VectorUnloader(vsr); // 创建向量卸载器,用于从VectorSchemaRoot中提取记录批次,VectorUnloader可以将VectorSchemaRoot转换为ArrowRecordBatch
      ArrowRecordBatch arrowRecordBatch = vectorUnloader.getRecordBatch(); // 从向量卸载器中获取Arrow记录批次,ArrowRecordBatch包含一批数据的二进制表示
      evaluateOperator(arrowRecordBatch); // 调用抽象方法,让子类处理这个记录批次,子类可以在此实现过滤、投影等操作
    } catch (IOException e) { // 捕获IO异常,处理文件读取过程中可能出现的错误
      throw Util.toUnchecked(e); // 将检查型异常转换为非检查型异常,使用Calcite工具类Util的方法,避免在方法签名中声明throws
    }
  }

  // 获取当前行数据的方法,实现Enumerator接口的current()方法
  // 返回当前行索引对应的数据,如果只有一个字段则返回单个对象,否则返回对象数组
  @Override public Object current() { // 重写Enumerator接口的current()方法,返回当前行的数据
    if (fields.size() == 1) { // 如果只需要读取一个字段(单列查询)
      return this.valueVectors.get(0).getObject(currRowIndex); // 直接从第一个值向量中获取当前行的数据,返回单个对象
    }
    Object[] current = new Object[valueVectors.size()]; // 创建对象数组,数组长度为值向量数量,用于存储多列数据
    for (int i = 0; i < valueVectors.size(); i++) { // 遍历所有值向量
      ValueVector vector = this.valueVectors.get(i); // 获取第i个值向量,对应第i列的数据
      current[i] = vector.getObject(currRowIndex); // 从值向量中获取当前行的数据,存入数组,getObject()方法会根据数据类型返回对应的Java对象
    }
    return current; // 返回包含多列数据的对象数组
  }

  // 重置枚举器的方法,实现Enumerator接口的reset()方法
  // 该方法不支持重置,抛出不支持操作异常
  @Override public void reset() { // 重写Enumerator接口的reset()方法,用于将枚举器重置到初始状态
    throw new UnsupportedOperationException(); // 抛出不支持操作异常,表示该方法不支持,Arrow枚举器不支持重新遍历
  }
}
