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
package org.apache.calcite.test; // 声明当前类所在的包路径：org.apache.calcite.test，这是Calcite测试类的标准包

/**
 * As {@link RelMetadataTest} but uses a proxying metadata provider.
 * 本类是RelMetadataTest的子类，但使用代理(Proxying)元数据提供者来运行测试
 * 
 * 类作用说明：
 * ProxyingRelMetadataTest是专门用于测试Calcite关系代数元数据系统的测试类
 * 它继承自RelMetadataTest基类，但通过使用代理模式的元数据提供者来进行测试
 * 
 * 在Calcite中，元数据提供者(Metadata Provider)负责为关系表达式(RelNode)提供各种元数据信息
 * 例如：行数(Row Count)、唯一键(Unique Keys)、外键(Foreign Keys)、分布(Distribution)等
 * 
 * 代理元数据提供者(Proxying Metadata Provider)是一种特殊的实现方式
 * 它通过代理模式来调用实际的元数据提供者，可以用于：
 * 1. 性能监控：记录元数据查询的调用次数和耗时
 * 2. 缓存管理：在代理层实现元数据的缓存
 * 3. 调试追踪：追踪元数据的查询路径
 * 4. 灵活切换：在运行时动态切换不同的元数据提供者实现
 * 
 * 本测试类的主要目的：
 * - 验证代理元数据提供者能够正确地委托调用到实际的元数据提供者
 * - 确保代理模式不会影响元数据查询的正确性和性能
 * - 测试在各种复杂的查询场景下，代理机制能够正常工作
 * - 作为元数据系统的一种替代实现方案，提供给用户选择
 *
 * @see RelMetadataFixture.MetadataConfig#PROXYING 参见元数据配置中的PROXYING选项
 */
public class ProxyingRelMetadataTest extends RelMetadataTest { // 定义ProxyingRelMetadataTest类，继承自RelMetadataTest基类，复用基类的所有测试方法
  @Override protected RelMetadataFixture fixture() { // 重写fixture()方法，返回配置了代理元数据提供者的测试装置对象，@Override注解表示这是对父类方法的重写
    return super.fixture() // 首先调用父类的fixture()方法获取基础的测试装置对象，super关键字用于调用父类的方法
        .withMetadataConfig(RelMetadataFixture.MetadataConfig.PROXYING); // 然后使用withMetadataConfig()方法配置元数据提供者为PROXYING模式，这样所有测试都会使用代理元数据提供者来运行
  } // fixture()方法结束，返回配置好的RelMetadataFixture对象
} // ProxyingRelMetadataTest类定义结束
