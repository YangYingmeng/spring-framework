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

package org.springframework.aop.aspectj.annotation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.autoproxy.AspectJAwareAdvisorAutoProxyCreator;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/*
 * AnnotationAwareAspectJAutoProxyCreator
 * Spring AOP 核心的自动代理创建器之一，用于识别 @AspectJ 注解并为目标 Bean 生成代理。
 * 继承自 AspectJAwareAdvisorAutoProxyCreator，增加了对 @Aspect 注解的支持。
 */

@SuppressWarnings("serial")
public class AnnotationAwareAspectJAutoProxyCreator extends AspectJAwareAdvisorAutoProxyCreator {

	// 匹配可代理 Bean 名称的正则列表（可选）
	@Nullable
	private List<Pattern> includePatterns;

	// AspectJ 通知工厂，用于解析切面类和通知方法
	@Nullable
	private AspectJAdvisorFactory aspectJAdvisorFactory;

	// 根据 BeanFactory 中的 Bean 构建 AspectJ Advisors 的工具
	@Nullable
	private BeanFactoryAspectJAdvisorsBuilder aspectJAdvisorsBuilder;


	/**
	 * 设置可代理 Bean 名称的匹配规则
	 */
	public void setIncludePatterns(List<String> patterns) {
		this.includePatterns = new ArrayList<>(patterns.size());
		for (String patternText : patterns) {
			this.includePatterns.add(Pattern.compile(patternText));
		}
	}

	/**
	 * 注入自定义的 AspectJAdvisorFactory
	 */
	public void setAspectJAdvisorFactory(AspectJAdvisorFactory aspectJAdvisorFactory) {
		Assert.notNull(aspectJAdvisorFactory, "AspectJAdvisorFactory must not be null");
		this.aspectJAdvisorFactory = aspectJAdvisorFactory;
	}

	/**
	 * 初始化 BeanFactory，同时初始化 AspectJAdvisorFactory 和 AspectJAdvisorsBuilder
	 */
	@Override
	protected void initBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		super.initBeanFactory(beanFactory);
		if (this.aspectJAdvisorFactory == null) {
			this.aspectJAdvisorFactory = new ReflectiveAspectJAdvisorFactory(beanFactory);
		}
		this.aspectJAdvisorsBuilder =
				new BeanFactoryAspectJAdvisorsBuilderAdapter(beanFactory, this.aspectJAdvisorFactory);
	}

	/**
	 * 查找候选 Advisor：
	 * 1. 调用父类逻辑获取普通 Spring Advisor
	 * 2. 扫描 BeanFactory 中的 @Aspect 注解类，解析为 AspectJ Advisor
	 */
	@Override
	protected List<Advisor> findCandidateAdvisors() {
		// 调用父类逻辑获取普通 Spring Advisor
		List<Advisor> advisors = super.findCandidateAdvisors();
		// 扫描 BeanFactory 中的 @Aspect 注解类，解析为 AspectJ Advisor
		if (this.aspectJAdvisorsBuilder != null) {
			advisors.addAll(this.aspectJAdvisorsBuilder.buildAspectJAdvisors());
		}
		return advisors;
	}

	/**
	 * 判断某个类是否为基础设施类（不需要被代理）
	 * 基础设施类包括 Spring 自身的 AOP 基础设施和 @Aspect 切面类
	 */
	@Override
	protected boolean isInfrastructureClass(Class<?> beanClass) {
		return (super.isInfrastructureClass(beanClass) ||
				(this.aspectJAdvisorFactory != null && this.aspectJAdvisorFactory.isAspect(beanClass)));
	}

	/**
	 * 判断一个 Bean 是否符合切面解析条件
	 */
	protected boolean isEligibleAspectBean(String beanName) {
		if (this.includePatterns == null) {
			return true;
		}
		else {
			for (Pattern pattern : this.includePatterns) {
				if (pattern.matcher(beanName).matches()) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * 内部适配器类，用于在构建切面 Advisor 时应用 includePatterns 过滤逻辑
	 */
	private class BeanFactoryAspectJAdvisorsBuilderAdapter extends BeanFactoryAspectJAdvisorsBuilder {

		public BeanFactoryAspectJAdvisorsBuilderAdapter(
				ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {

			super(beanFactory, advisorFactory);
		}

		@Override
		protected boolean isEligibleBean(String beanName) {
			return AnnotationAwareAspectJAutoProxyCreator.this.isEligibleAspectBean(beanName);
		}
	}
}

