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
 */ // Apache许可证声明，说明此代码遵循Apache 2.0开源协议，允许自由使用和修改
package org.apache.calcite.test; // 声明当前类所在的包路径为org.apache.calcite.test，属于Calcite框架的测试工具包

import org.apache.calcite.schema.ModifiableView; // 导入ModifiableView接口，这是Calcite中定义可修改视图的核心接口
import org.apache.calcite.schema.impl.AbstractTable; // 导入AbstractTable抽象基类，提供了表的基本实现框架

/**
 * Abstract base class for implementations of {@link ModifiableView}.
 */ // 类的JavaDoc文档说明：这是ModifiableView接口实现的抽象基类，为可修改视图提供基础实现
public abstract class AbstractModifiableView // 定义抽象类AbstractModifiableView，作为可修改视图的抽象基类
    extends AbstractTable implements ModifiableView { // 继承AbstractTable抽象类并实现ModifiableView接口，既具备表的基本特性又支持视图的可修改性
  protected AbstractModifiableView() { // 受保护的默认构造方法，用于创建AbstractModifiableView实例
  } // 构造方法体为空，不需要任何初始化操作，由子类负责具体的初始化逻辑
}
