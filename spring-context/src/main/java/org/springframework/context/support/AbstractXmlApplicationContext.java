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

package org.springframework.context.support;

import java.io.IOException;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.BeanDefinitionDocumentReader;
import org.springframework.beans.factory.xml.ResourceEntityResolver;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;

/**
 * 基于 XML 配置文件的抽象 ApplicationContext 实现类
 * 子类如 ClassPathXmlApplicationContext 会通过它加载 XML Bean 配置文件
 */
public abstract class AbstractXmlApplicationContext extends AbstractRefreshableConfigApplicationContext {

	// 是否启用 XML 验证（默认为 true）
	private boolean validating = true;

	// ====================
	// 构造方法区域
	// ====================

	// 无参构造器
	public AbstractXmlApplicationContext() {
	}

	// 构造器，允许设置父容器
	public AbstractXmlApplicationContext(@Nullable ApplicationContext parent) {
		super(parent);
	}

	// ====================
	// 验证器开关设置方法
	// ====================

	/**
	 * 设置是否在加载 XML 配置时启用 DTD/XSD 验证，默认启用。
	 * @param validating true 表示开启验证，false 表示关闭验证
	 */
	public void setValidating(boolean validating) {
		this.validating = validating;
	}

	// ====================
	// 加载 Bean 定义的主逻辑
	// ====================

	/**
	 * 重写自 AbstractRefreshableApplicationContext 的模板方法，
	 * 用于从 XML 中加载 Bean 定义信息
	 */
	@Override
	protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) throws BeansException, IOException {
		// 创建用于解析 XML 的读取器，绑定当前 BeanFactory
		XmlBeanDefinitionReader beanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);

		// 配置读取器的环境变量（比如 profile 信息）
		beanDefinitionReader.setEnvironment(getEnvironment());

		// 设置资源加载器为当前上下文（实现了 ResourceLoader 接口）
		beanDefinitionReader.setResourceLoader(this);

		// 设置实体解析器，用于解析 DTD/XSD 引用
		beanDefinitionReader.setEntityResolver(new ResourceEntityResolver(this));

		// 当 Bean 读取器读取 Bean 定义的Xml 资源文件时,启用Xml 的校验机制
		initBeanDefinitionReader(beanDefinitionReader);

		// Bean 读取器真正实现加载的方法
		loadBeanDefinitions(beanDefinitionReader);
	}

	/**
	 * 子类可以重写这个方法，定制 XmlBeanDefinitionReader 的属性
	 */
	protected void initBeanDefinitionReader(XmlBeanDefinitionReader reader) {
		// 设置是否开启 XML 验证功能
		reader.setValidating(this.validating);
	}

	/**
	 * 具体加载 Bean 定义的方法
	 * 支持两种来源：Resource[] 或 String[]
	 */
	protected void loadBeanDefinitions(XmlBeanDefinitionReader reader) throws BeansException, IOException {
		// 从 Resource[] 加载（通常是类路径中加载）
		Resource[] configResources = getConfigResources();
		if (configResources != null) {
			reader.loadBeanDefinitions(configResources);
		}

		// 从路径字符串数组加载（通常是绝对路径或 classpath 路径）
		String[] configLocations = getConfigLocations();
		if (configLocations != null) {
			reader.loadBeanDefinitions(configLocations);
		}
	}

	/**
	 * 获取 Resource[] 类型的配置资源，默认返回 null，交由子类实现（如 ClassPathXmlApplicationContext）
	 */
	@Nullable
	protected Resource[] getConfigResources() {
		return null;
	}

}
