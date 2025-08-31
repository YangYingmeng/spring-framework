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

package org.springframework.aop.framework.autoproxy;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Helper for retrieving standard Spring Advisors from a BeanFactory,
 * for use with auto-proxying.
 *
 * @author Juergen Hoeller
 * @since 2.0.2
 * @see AbstractAdvisorAutoProxyCreator
 */
public class BeanFactoryAdvisorRetrievalHelper {

	private static final Log logger = LogFactory.getLog(BeanFactoryAdvisorRetrievalHelper.class);

	private final ConfigurableListableBeanFactory beanFactory;

	@Nullable
	private volatile String[] cachedAdvisorBeanNames;


	/**
	 * Create a new BeanFactoryAdvisorRetrievalHelper for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 */
	public BeanFactoryAdvisorRetrievalHelper(ConfigurableListableBeanFactory beanFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		this.beanFactory = beanFactory;
	}


	/**
	 * Find all eligible Advisor beans in the current bean factory,
	 * ignoring FactoryBeans and excluding beans that are currently in creation.
	 * @return the list of {@link org.springframework.aop.Advisor} beans
	 * @see #isEligibleBean
	 */
	/**
	 * findAdvisorBeans: 获取容器中所有可用的 Advisor Bean
	 *
	 * 流程：
	 * 1️⃣ 检查缓存
	 *      └─ 如果已缓存 Advisor 名称列表，则直接使用
	 *      └─ 否则通过 BeanFactoryUtils 查询容器及祖先容器中所有类型为 Advisor 的 Bean 名称（不初始化 FactoryBean）
	 *
	 * 2️⃣ 遍历所有 Advisor Bean 名称
	 *      ├─ 调用 isEligibleBean(name) 判断该 Advisor 是否符合条件（子类可重写）
	 *      ├─ 检查 Advisor 是否正在创建中（循环依赖保护），若正在创建则跳过
	 *      └─ 通过 beanFactory.getBean(name, Advisor.class) 获取实例
	 *           └─ 如果获取过程中抛出 BeanCurrentlyInCreationException 且依赖于当前正在创建的 Bean，则跳过
	 *
	 * 3️⃣ 将符合条件的 Advisor 加入返回列表
	 *
	 * @return 当前容器中可应用的 Advisor 列表
	 */
	public List<Advisor> findAdvisorBeans() {
		// 获取缓存的 Advisor Bean 名称
		String[] advisorNames = this.cachedAdvisorBeanNames;
		if (advisorNames == null) {
			// 查询容器及祖先容器中所有 Advisor 类型的 Bean 名称
			// 不初始化 FactoryBean，避免提前初始化普通 Bean
			advisorNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
					this.beanFactory, Advisor.class, true, false);
			this.cachedAdvisorBeanNames = advisorNames;
		}

		if (advisorNames.length == 0) {
			return new ArrayList<>();
		}

		List<Advisor> advisors = new ArrayList<>();
		for (String name : advisorNames) {
			if (isEligibleBean(name)) {
				// 跳过正在创建的 Advisor（防止循环依赖）
				if (this.beanFactory.isCurrentlyInCreation(name)) {
					if (logger.isTraceEnabled()) {
						logger.trace("Skipping currently created advisor '" + name + "'");
					}
				}
				else {
					try {
						// 获取 Advisor 实例
						advisors.add(this.beanFactory.getBean(name, Advisor.class));
					}
					catch (BeanCreationException ex) {
						// 处理依赖当前正在创建 Bean 的情况
						Throwable rootCause = ex.getMostSpecificCause();
						if (rootCause instanceof BeanCurrentlyInCreationException) {
							BeanCreationException bce = (BeanCreationException) rootCause;
							String bceBeanName = bce.getBeanName();
							if (bceBeanName != null && this.beanFactory.isCurrentlyInCreation(bceBeanName)) {
								if (logger.isTraceEnabled()) {
									logger.trace("Skipping advisor '" + name +
											"' with dependency on currently created bean: " + ex.getMessage());
								}
								// 忽略：表示该 Advisor 依赖于当前正在创建的 Bean
								continue;
							}
						}
						throw ex;
					}
				}
			}
		}
		return advisors;
	}

	/**
	 * Determine whether the aspect bean with the given name is eligible.
	 * <p>The default implementation always returns {@code true}.
	 * @param beanName the name of the aspect bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleBean(String beanName) {
		return true;
	}

}
