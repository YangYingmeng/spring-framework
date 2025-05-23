/*
 * Copyright 2002-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory;

import org.springframework.beans.BeansException;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

/**
 * Spring 容器的顶级接口，是所有 Bean 容器的基础。
 *
 * <p>BeanFactory 提供了最基本的 IoC 容器能力，用于获取 Bean 实例。</p>
 *
 * <p>它支持：
 * <ul>
 *     <li>按名称获取 Bean</li>
 *     <li>按类型获取 Bean</li>
 *     <li>延迟加载（懒加载）Bean</li>
 *     <li>判断 Bean 的作用域（单例 / 原型）</li>
 *     <li>处理 FactoryBean 的特殊语义</li>
 * </ul>
 * </p>
 *
 * <p>它是 {@link ApplicationContext} 的父接口，ApplicationContext 提供了更多高级功能，
 * 如国际化、事件发布、Bean 自动装配、环境信息等。</p>
 *
 * <p>通常开发者更常与 ApplicationContext 打交道，但理解 BeanFactory 是理解 Spring IoC 的核心。</p>
 */
public interface BeanFactory {

	/**
	 * FactoryBean 的前缀标识：
	 * 用于获取 FactoryBean 本身，而不是它所创建的 bean 实例。
	 * 举例：如果容器中有一个名为 "myFactoryBean" 的 FactoryBean，
	 * 直接通过 "myFactoryBean" 获取的是其生产的对象，
	 * 若想获取 FactoryBean 自身，应使用 "&myFactoryBean"。
	 */
	String FACTORY_BEAN_PREFIX = "&";

	/**
	 * 根据 bean 名称获取一个实例对象。
	 *
	 * @param name bean 名称
	 * @return 对应的 bean 实例
	 * @throws BeansException 若找不到 bean 或创建失败
	 */
	Object getBean(String name) throws BeansException;

	/**
	 * 根据 bean 名称和类型获取一个实例对象。
	 *
	 * @param name         bean 名称
	 * @param requiredType 期望的 bean 类型
	 * @return 匹配的 bean 实例
	 * @throws BeansException 若找不到或类型不匹配
	 */
	<T> T getBean(String name, Class<T> requiredType) throws BeansException;

	/**
	 * 根据 bean 名称和构造参数获取一个实例对象。
	 * 适用于调用带参数构造器的情况。
	 *
	 * @param name bean 名称
	 * @param args 用于构造 bean 的参数
	 * @return 对应的 bean 实例
	 * @throws BeansException 创建失败或找不到
	 */
	Object getBean(String name, Object... args) throws BeansException;

	/**
	 * 根据类型获取一个唯一匹配的 bean 实例。
	 * 若有多个该类型的 bean，会抛出异常。
	 *
	 * @param requiredType 期望的 bean 类型
	 * @return 匹配的 bean 实例
	 * @throws BeansException 找不到或匹配多个时抛出
	 */
	<T> T getBean(Class<T> requiredType) throws BeansException;

	/**
	 * 根据类型及构造参数获取一个唯一匹配的 bean 实例。
	 *
	 * @param requiredType 期望的 bean 类型
	 * @param args         用于构造 bean 的参数
	 * @return 匹配的 bean 实例
	 * @throws BeansException 创建失败或找不到
	 */
	<T> T getBean(Class<T> requiredType, Object... args) throws BeansException;

	/**
	 * 延迟查找指定类型的 bean 的提供者。
	 * 可用于延迟获取或者按需实例化 bean。
	 *
	 * @param requiredType 目标类型
	 * @return ObjectProvider 提供懒加载能力
	 */
	<T> ObjectProvider<T> getBeanProvider(Class<T> requiredType);

	/**
	 * 延迟查找指定 ResolvableType 类型的 bean 的提供者。
	 * 支持泛型类型查找。
	 *
	 * @param requiredType 可解析的类型
	 * @return ObjectProvider 提供懒加载能力
	 */
	<T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType);

	/**
	 * 判断容器中是否包含指定名称的 bean。
	 * 不区分普通 bean 和 FactoryBean。
	 *
	 * @param name bean 名称
	 * @return 是否存在
	 */
	boolean containsBean(String name);

	/**
	 * 判断指定名称的 bean 是否为单例模式。
	 *
	 * @param name bean 名称
	 * @return 是否为单例
	 * @throws NoSuchBeanDefinitionException bean 不存在
	 */
	boolean isSingleton(String name) throws NoSuchBeanDefinitionException;

	/**
	 * 判断指定名称的 bean 是否为原型模式（每次获取创建新实例）。
	 *
	 * @param name bean 名称
	 * @return 是否为原型
	 * @throws NoSuchBeanDefinitionException bean 不存在
	 */
	boolean isPrototype(String name) throws NoSuchBeanDefinitionException;

	/**
	 * 判断指定名称的 bean 是否匹配给定的类型（使用 ResolvableType 支持泛型）。
	 *
	 * @param name        bean 名称
	 * @param typeToMatch 要匹配的类型
	 * @return 是否匹配
	 * @throws NoSuchBeanDefinitionException bean 不存在
	 */
	boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException;

	/**
	 * 判断指定名称的 bean 是否匹配给定的类型。
	 *
	 * @param name        bean 名称
	 * @param typeToMatch 要匹配的类型
	 * @return 是否匹配
	 * @throws NoSuchBeanDefinitionException bean 不存在
	 */
	boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException;

	/**
	 * 获取指定名称的 bean 的类型（Class 对象）。
	 *
	 * @param name bean 名称
	 * @return bean 的类型，若不存在返回 null
	 * @throws NoSuchBeanDefinitionException bean 不存在
	 */
	@Nullable
	Class<?> getType(String name) throws NoSuchBeanDefinitionException;

	/**
	 * 获取指定名称的 bean 的类型，可控制是否触发 FactoryBean 的初始化。
	 *
	 * @param name                 bean 名称
	 * @param allowFactoryBeanInit 是否允许初始化 FactoryBean
	 * @return bean 的类型，若不存在返回 null
	 * @throws NoSuchBeanDefinitionException bean 不存在
	 */
	@Nullable
	Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException;

	/**
	 * 获取指定名称的 bean 的所有别名。
	 * 如果该 bean 通过多个名称注册，则返回所有别名。
	 *
	 * @param name bean 名称
	 * @return 该名称对应的所有别名数组
	 */
	String[] getAliases(String name);
}
