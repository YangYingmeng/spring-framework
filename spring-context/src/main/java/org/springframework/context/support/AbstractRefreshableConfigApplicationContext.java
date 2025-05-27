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

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * 抽象类：可刷新的、可配置的 ApplicationContext 实现类
 * 提供 configLocation(s) 配置路径的支持
 */
public abstract class AbstractRefreshableConfigApplicationContext extends AbstractRefreshableApplicationContext
		implements BeanNameAware, InitializingBean {

	// 保存配置路径（如 classpath:applicationContext.xml 等）
	@Nullable
	private String[] configLocations;

	// 标志：是否已显式调用 setId() 方法
	private boolean setIdCalled = false;

	// ====================
	// 构造方法
	// ====================

	// 无参构造器
	public AbstractRefreshableConfigApplicationContext() {
	}

	// 允许指定父容器的构造器
	public AbstractRefreshableConfigApplicationContext(@Nullable ApplicationContext parent) {
		super(parent);
	}

	// ====================
	// 配置路径处理方法
	// ====================

	/**
	 * 设置单个配置路径（会自动用 , ; \t \n 等分隔）
	 * 示例：setConfigLocation("classpath:context1.xml,classpath:context2.xml");
	 */
	public void setConfigLocation(String location) {
		setConfigLocations(StringUtils.tokenizeToStringArray(location, CONFIG_LOCATION_DELIMITERS));
	}

	/**
	 * 设置多个配置路径
	 * 会进行非 null 校验和占位符解析（如 ${user.dir}）
	 */
	public void setConfigLocations(@Nullable String... locations) {
		if (locations != null) {
			Assert.noNullElements(locations, "Config locations must not be null"); // 校验
			this.configLocations = new String[locations.length];
			for (int i = 0; i < locations.length; i++) {
				// 解析占位符并去除空格
				this.configLocations[i] = resolvePath(locations[i]).trim();
			}
		} else {
			this.configLocations = null;
		}
	}

	/**
	 * 获取配置路径数组（优先使用显式设置的路径，否则调用默认路径）
	 */
	@Nullable
	protected String[] getConfigLocations() {
		return (this.configLocations != null ? this.configLocations : getDefaultConfigLocations());
	}

	/**
	 * 获取默认配置路径（一般由子类实现）
	 * 本类默认返回 null
	 */
	@Nullable
	protected String[] getDefaultConfigLocations() {
		return null;
	}

	/**
	 * 占位符路径解析方法，如解析 ${user.dir}/app.xml
	 */
	protected String resolvePath(String path) {
		return getEnvironment().resolveRequiredPlaceholders(path);
	}

	// ====================
	// BeanNameAware 和 InitializingBean 接口实现
	// ====================

	/**
	 * 设置 ApplicationContext 的唯一 ID
	 */
	@Override
	public void setId(String id) {
		super.setId(id);
		this.setIdCalled = true;
	}

	/**
	 * 实现 BeanNameAware 接口：当容器启动时注入 Bean 的名字
	 * 如果用户没有调用 setId()，则默认使用 Bean 名称作为 id
	 */
	@Override
	public void setBeanName(String name) {
		if (!this.setIdCalled) {
			super.setId(name); // 设置 ID
			setDisplayName("ApplicationContext '" + name + "'"); // 设置显示名
		}
	}

	/**
	 * 实现 InitializingBean 接口：在属性注入完毕后执行
	 * 如果当前容器还未激活（未刷新），则自动刷新一次
	 */
	@Override
	public void afterPropertiesSet() {
		if (!isActive()) {
			refresh(); // 自动触发容器刷新
		}
	}


}
