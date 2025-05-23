/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.web.servlet;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceEditor;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.support.ServletContextResourceLoader;
import org.springframework.web.context.support.StandardServletEnvironment;

/*
 * HttpServletBean 是 Spring Web 框架的基础类之一，用于将 Servlet 初始化参数自动绑定到 Servlet 对象的 JavaBean 属性上。
 * 它是 DispatcherServlet 的父类，简化了 servlet 的配置与参数注入过程。
 */
@SuppressWarnings("serial")
public abstract class HttpServletBean extends HttpServlet implements EnvironmentCapable, EnvironmentAware {

	// 日志记录器
	protected final Log logger = LogFactory.getLog(getClass());

	// Spring 的环境变量对象，支持如 ${...} 配置属性解析
	@Nullable
	private ConfigurableEnvironment environment;

	// 存储 init-param 中要求必须配置的参数名
	private final Set<String> requiredProperties = new HashSet<>(4);

	/**
	 * 添加必须存在的初始化参数名
	 */
	protected final void addRequiredProperty(String property) {
		this.requiredProperties.add(property);
	}

	/**
	 * 注入 Spring 的环境变量（用于属性占位符解析）
	 */
	@Override
	public void setEnvironment(Environment environment) {
		Assert.isInstanceOf(ConfigurableEnvironment.class, environment, "ConfigurableEnvironment required");
		this.environment = (ConfigurableEnvironment) environment;
	}

	/**
	 * 获取当前环境变量（若为空则创建默认环境）
	 */
	@Override
	public ConfigurableEnvironment getEnvironment() {
		if (this.environment == null) {
			this.environment = createEnvironment();
		}
		return this.environment;
	}

	/**
	 * 创建默认的环境变量对象，默认使用 StandardServletEnvironment
	 */
	protected ConfigurableEnvironment createEnvironment() {
		return new StandardServletEnvironment();
	}

	/**
	 * Servlet 初始化方法，Spring 会调用此方法初始化 Servlet 对象
	 */
	@Override
	public final void init() throws ServletException {

		// 将 ServletConfig 中的参数封装成 PropertyValues
		PropertyValues pvs = new ServletConfigPropertyValues(getServletConfig(), this.requiredProperties);

		if (!pvs.isEmpty()) {
			try {
				// 创建 BeanWrapper（包装当前 Servlet 实例）
				BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(this);

				// 设置自定义属性编辑器，用于解析 Resource 类型的属性值
				ResourceLoader resourceLoader = new ServletContextResourceLoader(getServletContext());
				bw.registerCustomEditor(Resource.class, new ResourceEditor(resourceLoader, getEnvironment()));

				// 子类可扩展此方法，定制 BeanWrapper 行为
				initBeanWrapper(bw);

				// 设置属性值（将 init-param 值注入到 Servlet 的对应属性上）
				bw.setPropertyValues(pvs, true);
			} catch (BeansException ex) {
				// 日志记录错误并抛出异常
				if (logger.isErrorEnabled()) {
					logger.error("Failed to set bean properties on servlet '" + getServletName() + "'", ex);
				}
				throw ex;
			}
		}

		// 调用子类定义的初始化逻辑（如 DispatcherServlet.initServletBean）
		initServletBean();
	}

	/**
	 * 初始化 BeanWrapper，子类可重写添加更多编辑器或处理逻辑
	 */
	protected void initBeanWrapper(BeanWrapper bw) throws BeansException {
	}

	/**
	 * 子类扩展初始化逻辑的入口方法（如 DispatcherServlet 会重写此方法）
	 */
	protected void initServletBean() throws ServletException {
	}

	/**
	 * 获取 Servlet 名称
	 */
	@Override
	@Nullable
	public String getServletName() {
		return (getServletConfig() != null ? getServletConfig().getServletName() : null);
	}

	/**
	 * 内部静态类：用于从 ServletConfig 中提取参数封装为 PropertyValues
	 */
	private static class ServletConfigPropertyValues extends MutablePropertyValues {

		/**
		 * 构造函数：读取 config 中的 init-param，封装为 PropertyValue 列表。
		 * 如果 requiredProperties 中有缺失项，则抛出异常。
		 */
		public ServletConfigPropertyValues(ServletConfig config, Set<String> requiredProperties)
				throws ServletException {

			// 记录缺失的参数名（如果有必须参数）
			Set<String> missingProps = (!CollectionUtils.isEmpty(requiredProperties) ?
					new HashSet<>(requiredProperties) : null);

			// 遍历所有 init-param
			Enumeration<String> paramNames = config.getInitParameterNames();
			while (paramNames.hasMoreElements()) {
				String property = paramNames.nextElement();
				Object value = config.getInitParameter(property);
				addPropertyValue(new PropertyValue(property, value));
				if (missingProps != null) {
					missingProps.remove(property);
				}
			}

			// 如果还有必填项未设置，则抛出 ServletException
			if (!CollectionUtils.isEmpty(missingProps)) {
				throw new ServletException(
						"Initialization from ServletConfig for servlet '" + config.getServletName() +
								"' failed; the following required properties were missing: " +
								StringUtils.collectionToDelimitedString(missingProps, ", "));
			}
		}
	}
}
