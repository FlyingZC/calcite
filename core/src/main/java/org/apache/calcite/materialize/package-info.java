/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明，表明该代码遵循Apache 2.0许可证
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，查看随此工作分发的NOTICE文件以获取版权所有权信息
 * this work for additional information regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache许可证2.0版授权给您使用此文件
 * (the "License"); you may not use this file except in compliance with // （"许可证"）；除非符合许可证，否则您不得使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下位置获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // Apache许可证2.0的官方网址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则根据许可证分发的软件
 * distributed under the License is distributed on an "AS IS" BASIS, // 是按"原样"基础分发的，不附带任何明示或暗示的保证或条件
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不包含任何形式的保证或条件，无论是明示的还是暗示的
 * See the License for the specific language governing permissions and // 请参阅许可证以了解许可证下的特定语言管理权限和
 * limitations under the License. // 限制
 */

/**
 * Management of materialized query results. // 物化查询结果的管理
 *
 * <p>An actor ({@link org.apache.calcite.materialize.MaterializationActor}) // 一个执行器（MaterializationActor类）维护系统中所有物化的状态
 * maintains the state of all // 并被包装在一个服务（MaterializationService类）中
 * materializations in the system and is wrapped in a service // 供系统的其他部分访问
 * ({@link org.apache.calcite.materialize.MaterializationService})
 * for access from other parts of the system.
 *
 * <p>Optimizer rules allow Calcite to rewrite queries using materializations, // 优化器规则允许Calcite使用物化来重写查询
 * if they are valid (that is, contain the same result as executing their // 如果它们是有效的（即包含与其定义查询执行相同的结果）
 * defining query) and lower cost. // 并且成本更低
 *
 * <p>In future, the actor may manage the process of updating materializations, // 在未来，执行器可能会管理更新物化的过程
 * instantiating materializations from the intermediate results of queries, and // 从查询的中间结果实例化物化，以及
 * recognize what materializations would be useful based on actual query load. // 根据实际查询负载识别哪些物化是有用的
 */
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.FIELD) // 指定该包中所有字段默认为非空类型，使用Checker框架进行空值检查
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.PARAMETER) // 指定该包中所有参数默认为非空类型，使用Checker框架进行空值检查
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.RETURN) // 指定该包中所有返回值默认为非空类型，使用Checker框架进行空值检查
package org.apache.calcite.materialize; // 定义包名为org.apache.calcite.materialize，这是Calcite框架中负责物化查询结果管理的包

import org.checkerframework.checker.nullness.qual.NonNull; // 导入Checker框架的非空注解，用于标记类型不允许为null
import org.checkerframework.framework.qual.DefaultQualifier; // 导入Checker框架的默认限定符注解，用于设置包级别的默认类型限定
import org.checkerframework.framework.qual.TypeUseLocation; // 导入Checker框架的类型使用位置枚举，用于指定限定符应用的位置（如字段、参数、返回值等）
