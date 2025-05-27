/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.context.support;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * 从 classpath 路径加载 XML 配置文件的 ApplicationContext 实现类，
 * 继承自 AbstractXmlApplicationContext
 */
public class ClassPathXmlApplicationContext extends AbstractXmlApplicationContext {

	// 保存 XML 配置文件对应的 Resource 数组（资源对象）
	@Nullable
	private Resource[] configResources;

	// ==========================
	// 构造函数区域（用于多种方式初始化容器）
	// ==========================

	// 无参构造器，常用于手动设置 configLocation 后再调用 refresh()
	public ClassPathXmlApplicationContext() {
	}

	// 传入父容器（可用于上下文嵌套）
	public ClassPathXmlApplicationContext(ApplicationContext parent) {
		super(parent);
	}

	// 通过单个配置路径构造并自动 refresh 容器
	public ClassPathXmlApplicationContext(String configLocation) throws BeansException {
		this(new String[] {configLocation}, true, null);
	}

	// 通过多个配置路径构造并自动 refresh 容器
	public ClassPathXmlApplicationContext(String... configLocations) throws BeansException {
		this(configLocations, true, null);
	}

	// 传入配置路径 + 父容器构造，默认自动 refresh
	public ClassPathXmlApplicationContext(String[] configLocations, @Nullable ApplicationContext parent)
			throws BeansException {
		this(configLocations, true, parent);
	}

	// 配置路径 + 是否 refresh 构造
	public ClassPathXmlApplicationContext(String[] configLocations, boolean refresh) throws BeansException {
		this(configLocations, refresh, null);
	}

	// 构造核心方法，支持配置路径、是否自动刷新、父容器注入
	public ClassPathXmlApplicationContext(
			String[] configLocations, boolean refresh, @Nullable ApplicationContext parent)
			throws BeansException {

		super(parent);  // 设置父容器
		setConfigLocations(configLocations); // 设置配置文件路径
		if (refresh) {
			refresh();  // 加载 Bean 定义并实例化 Bean
		}
	}

	// ==========================
	// 带 Class 对象的构造（适用于非根路径下的 classpath 加载）
	// ==========================

	// 指定路径 + 参照类加载资源
	public ClassPathXmlApplicationContext(String path, Class<?> clazz) throws BeansException {
		this(new String[] {path}, clazz);
	}

	// 多个路径 + 参照类加载资源
	public ClassPathXmlApplicationContext(String[] paths, Class<?> clazz) throws BeansException {
		this(paths, clazz, null);
	}

	// 多路径 + 指定类 + 父容器方式构造
	public ClassPathXmlApplicationContext(String[] paths, Class<?> clazz, @Nullable ApplicationContext parent)
			throws BeansException {

		super(parent);  // 设置父容器
		Assert.notNull(paths, "Path array must not be null");   // 路径不能为空
		Assert.notNull(clazz, "Class argument must not be null"); // 类不能为空

		this.configResources = new Resource[paths.length];  // 初始化资源数组

		// 将路径和 class 结合，构建 ClassPathResource 对象
		for (int i = 0; i < paths.length; i++) {
			this.configResources[i] = new ClassPathResource(paths[i], clazz);
		}

		refresh();  // 加载并初始化上下文
	}

	// ==========================
	// 获取配置资源的实现（供父类使用）
	// ==========================

	@Override
	@Nullable
	protected Resource[] getConfigResources() {
		return this.configResources;
	}

}
