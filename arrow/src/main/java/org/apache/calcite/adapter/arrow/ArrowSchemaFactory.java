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
// 声明包名，表示这个类属于org.apache.calcite.adapter.arrow包，这是Calcite中Arrow适配器的包
package org.apache.calcite.adapter.arrow;

// 导入ModelHandler类，用于处理模型配置中的额外操作数，特别是获取基础目录路径
import org.apache.calcite.model.ModelHandler;
// 导入Schema接口，表示Calcite中的数据模式，定义了表、函数等结构
import org.apache.calcite.schema.Schema;
// 导入SchemaFactory接口，这是工厂接口，用于创建Schema实例
import org.apache.calcite.schema.SchemaFactory;
// 导入SchemaPlus接口，这是Schema的扩展接口，提供了添加子schema等功能
import org.apache.calcite.schema.SchemaPlus;

// 导入File类，用于文件和目录路径的操作
import java.io.File;
// 导入Map接口，用于存储键值对，这里用于接收配置参数
import java.util.Map;

/**
 * Factory that creates an {@link ArrowSchema}.
 * 工厂类，用于创建ArrowSchema实例
 * ArrowSchemaFactory是Calcite适配器模式中的工厂类，负责根据配置参数创建Arrow数据源的Schema
 * 实现了SchemaFactory接口，遵循工厂设计模式，将Schema的创建逻辑封装起来
 * Arrow是Apache Arrow项目，是一个跨语言的内存列式数据格式，用于高效的数据处理和分析
 * 这个工厂类的主要作用是：解析配置参数，确定Arrow数据文件的目录位置，然后创建对应的ArrowSchema对象
 */
public class ArrowSchemaFactory implements SchemaFactory {

  // 实现SchemaFactory接口的create方法，这是创建Schema的核心方法
  // @param parentSchema 父schema对象，用于构建schema层次结构，当前实现中未使用但保留参数以符合接口规范
  // @param name schema的名称，用于在schema树中标识这个schema，当前实现中未使用但保留参数以符合接口规范
  // @param operand 配置参数的Map集合，包含创建schema所需的所有配置信息，如目录路径等
  // @return 返回创建好的Schema实例，这里具体返回ArrowSchema对象
  // @throws RuntimeException 当无法确定有效的目录路径时抛出运行时异常
  @Override public Schema create(SchemaPlus parentSchema, String name,
      Map<String, Object> operand) {
    // 从配置参数中获取基础目录(baseDirectory)，使用ModelHandler常量来获取键名
    // ModelHandler.ExtraOperand.BASE_DIRECTORY.camelName返回的是"baseDirectory"字符串
    // 这个基础目录通常是在模型配置文件中指定的，作为相对路径的参考目录
    final File baseDirectory =
        (File) operand.get(ModelHandler.ExtraOperand.BASE_DIRECTORY.camelName);
    // 从配置参数中获取目录(directory)参数，这个参数指定Arrow数据文件的具体目录路径
    // directory参数可以是绝对路径，也可以是相对于baseDirectory的相对路径
    final String directory = (String) operand.get("directory");
    // 声明directoryFile变量，用于存储最终确定的目录文件对象
    // 初始化为null，表示还没有确定最终目录
    File directoryFile = null;
    // 如果directory参数不为空，说明配置中指定了目录路径
    if (directory != null) {
      // 将directory字符串转换为File对象
      // 这个File对象可能代表绝对路径或相对路径，后续会进一步处理
      directoryFile = new File(directory);
    }
    // 如果baseDirectory不为空，说明配置中指定了基础目录
    // 这个基础目录用于解析相对路径，或者作为默认目录
    if (baseDirectory != null) {
      // 如果directoryFile为null，说明没有配置directory参数
      // 这种情况下，直接使用baseDirectory作为最终的目录
      if (directoryFile == null) {
        // 将baseDirectory赋值给directoryFile，使用基础目录作为最终目录
        directoryFile = baseDirectory;
      // 如果directoryFile不为null，但不是绝对路径
      // 说明directory参数指定的是相对路径，需要与baseDirectory组合
      } else if (!directoryFile.isAbsolute()) {
        // 将baseDirectory作为父目录，directoryFile.getPath()作为子路径
        // 创建一个新的File对象，表示组合后的绝对路径
        // 例如：baseDirectory=/data/arrow，directory=test，则结果为/data/arrow/test
        directoryFile = new File(baseDirectory, directoryFile.getPath());
      }
    }
    // 如果最终directoryFile仍然为null，说明既没有配置directory，也没有配置baseDirectory
    // 这种情况下无法确定Arrow数据文件的位置，无法创建schema
    if (directoryFile == null) {
      // 抛出运行时异常，提示缺少目录配置
      // 这是一个严重的配置错误，需要用户修正配置文件
      throw new RuntimeException("no directory");
    }
    // 使用确定好的directoryFile创建ArrowSchema实例并返回
    // ArrowSchema是实际包含Arrow数据表结构的schema对象
    // 它会扫描指定目录下的Arrow文件，并将其转换为Calcite可识别的表结构
    return new ArrowSchema(directoryFile);
  }
}
