/*
 * Copyright 2002-2024 the original author or authors.
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
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.lang.Nullable;

/**
 * 可刷新的抽象应用上下文类（支持重新创建 BeanFactory）
 * 继承自 AbstractApplicationContext，增强了对 DefaultListableBeanFactory 的支持
 */
public abstract class AbstractRefreshableApplicationContext extends AbstractApplicationContext {

	// 是否允许同名 Bean 定义覆盖（默认 null，由子类控制）
	@Nullable
	private Boolean allowBeanDefinitionOverriding;

	// 是否允许循环依赖（默认 null，由子类控制）
	@Nullable
	private Boolean allowCircularReferences;

	// Bean 工厂（默认使用 DefaultListableBeanFactory）
	@Nullable
	private volatile DefaultListableBeanFactory beanFactory;

	// ========== 构造方法 ==========

	public AbstractRefreshableApplicationContext() {
	}

	// 支持设置父容器
	public AbstractRefreshableApplicationContext(@Nullable ApplicationContext parent) {
		super(parent);
	}

	// ========== 配置项设置 ==========

	// 设置是否允许 Bean 定义覆盖（例如重复定义相同名字的 Bean）
	public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
		this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
	}

	// 设置是否允许循环依赖
	public void setAllowCircularReferences(boolean allowCircularReferences) {
		this.allowCircularReferences = allowCircularReferences;
	}

	// ========== BeanFactory 刷新机制核心逻辑 ==========

	/**
	 * 刷新 BeanFactory 的核心方法。
	 * 每次刷新 ApplicationContext 时，都会重新创建一个新的 BeanFactory。
	 */
	@Override
	protected final void refreshBeanFactory() throws BeansException {
		// 如果已有 BeanFactory，则先销毁其中的 Bean，再关闭 BeanFactory
		if (hasBeanFactory()) {
			destroyBeans();        // 销毁已注册的 Bean
			closeBeanFactory();    // 关闭并清理 BeanFactory 实例
		}
		try {
			// 创建新的 BeanFactory（默认 DefaultListableBeanFactory）
			DefaultListableBeanFactory beanFactory = createBeanFactory();

			// 设置序列化 ID（用于 JMX 等机制）
			beanFactory.setSerializationId(getId());

			// 设置启动分析器（用于 ApplicationStartup）
			beanFactory.setApplicationStartup(getApplicationStartup());

			// 根据配置项自定义 BeanFactory（如覆盖、循环依赖等设置）
			customizeBeanFactory(beanFactory);

			// 加载 Bean 定义（如读取 XML、注解、配置类等）
			loadBeanDefinitions(beanFactory);

			// 设置为当前上下文的 BeanFactory
			this.beanFactory = beanFactory;
		} catch (IOException ex) {
			// 加载失败时抛出上下文异常
			throw new ApplicationContextException(
					"I/O error parsing bean definition source for " + getDisplayName(), ex);
		}
	}

	/**
	 * 当刷新失败时调用：清除序列化 ID，调用父类逻辑
	 */
	@Override
	protected void cancelRefresh(BeansException ex) {
		DefaultListableBeanFactory beanFactory = this.beanFactory;
		if (beanFactory != null) {
			beanFactory.setSerializationId(null);
		}
		super.cancelRefresh(ex);
	}

	/**
	 * 关闭 BeanFactory，即将其置空，并取消序列化 ID
	 */
	@Override
	protected final void closeBeanFactory() {
		DefaultListableBeanFactory beanFactory = this.beanFactory;
		if (beanFactory != null) {
			beanFactory.setSerializationId(null);
			this.beanFactory = null;
		}
	}

	/**
	 * 判断当前是否已经存在 BeanFactory（用于判断是否需要销毁旧的）
	 */
	protected final boolean hasBeanFactory() {
		return (this.beanFactory != null);
	}

	/**
	 * 获取当前使用的 BeanFactory（必须先 refresh 之后才可用）
	 */
	@Override
	public final ConfigurableListableBeanFactory getBeanFactory() {
		DefaultListableBeanFactory beanFactory = this.beanFactory;
		if (beanFactory == null) {
			throw new IllegalStateException(
					"BeanFactory not initialized or already closed - " +
							"call 'refresh' before accessing beans via the ApplicationContext");
		}
		return beanFactory;
	}

	/**
	 * 用于断言 BeanFactory 是否处于激活状态（此处为空实现，子类可重写）
	 */
	@Override
	protected void assertBeanFactoryActive() {
		// 默认不检查，可重写
	}

	// ========== 核心扩展点：创建和定制 BeanFactory ==========

	/**
	 * 创建新的 BeanFactory（可被子类重写）
	 * 默认返回 DefaultListableBeanFactory，支持 Bean 定义注册、依赖注入等功能
	 */
	protected DefaultListableBeanFactory createBeanFactory() {
		return new DefaultListableBeanFactory(getInternalParentBeanFactory());
	}

	/**
	 * 自定义 BeanFactory 的行为，例如是否允许覆盖、是否允许循环依赖
	 */
	protected void customizeBeanFactory(DefaultListableBeanFactory beanFactory) {
		if (this.allowBeanDefinitionOverriding != null) {
			beanFactory.setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
		}
		if (this.allowCircularReferences != null) {
			beanFactory.setAllowCircularReferences(this.allowCircularReferences);
		}
	}

	/**
	 * 加载 Bean 定义的抽象方法，必须由子类实现
	 * 比如：XmlWebApplicationContext 会从 XML 中读取 Bean 定义
	 */
	protected abstract void loadBeanDefinitions(DefaultListableBeanFactory beanFactory)
			throws BeansException, IOException;

}
