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

package org.springframework.aop.aspectj.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.reflect.PerClauseKind;

import org.springframework.aop.Advisor;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Helper for retrieving @AspectJ beans from a BeanFactory and building
 * Spring Advisors based on them, for use with auto-proxying.
 *
 * @author Juergen Hoeller
 * @since 2.0.2
 * @see AnnotationAwareAspectJAutoProxyCreator
 */
public class BeanFactoryAspectJAdvisorsBuilder {

	private static final Log logger = LogFactory.getLog(BeanFactoryAspectJAdvisorsBuilder.class);

	private final ListableBeanFactory beanFactory;

	private final AspectJAdvisorFactory advisorFactory;

	@Nullable
	private volatile List<String> aspectBeanNames;

	private final Map<String, List<Advisor>> advisorsCache = new ConcurrentHashMap<>();

	private final Map<String, MetadataAwareAspectInstanceFactory> aspectFactoryCache = new ConcurrentHashMap<>();


	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory) {
		this(beanFactory, new ReflectiveAspectJAdvisorFactory(beanFactory));
	}

	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 * @param advisorFactory the AspectJAdvisorFactory to build each Advisor with
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		Assert.notNull(advisorFactory, "AspectJAdvisorFactory must not be null");
		this.beanFactory = beanFactory;
		this.advisorFactory = advisorFactory;
	}


	/**
	 * Look for AspectJ-annotated aspect beans in the current bean factory,
	 * and return to a list of Spring AOP Advisors representing them.
	 * <p>Creates a Spring Advisor for each AspectJ advice method.
	 *
	 * @return the list of {@link org.springframework.aop.Advisor} beans
	 * @see #isEligibleBean
	 */
	public List<Advisor> buildAspectJAdvisors() {
		// 获取已经缓存的切面 Bean 名称
		List<String> aspectNames = this.aspectBeanNames;

		if (aspectNames == null) {
			synchronized (this) {
				aspectNames = this.aspectBeanNames;
				if (aspectNames == null) {
					List<Advisor> advisors = new ArrayList<>();
					aspectNames = new ArrayList<>();

					// 1. 扫描容器中所有 Bean
					String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
							this.beanFactory, Object.class, true, false);

					for (String beanName : beanNames) {
						// 2. 判断 Bean 是否符合切面资格
						if (!isEligibleBean(beanName)) continue;

						// 3. 获取 Bean 类型
						Class<?> beanType = this.beanFactory.getType(beanName, false);
						if (beanType == null) continue;

						// 4. 判断 Bean 是否标注 @Aspect
						if (this.advisorFactory.isAspect(beanType)) {
							try {
								AspectMetadata amd = new AspectMetadata(beanType, beanName);

								// 5. 根据切面实例化模型创建 Advisor
								if (amd.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {
									MetadataAwareAspectInstanceFactory factory =
											new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);
									List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);
									// 6. 缓存 Advisor
									if (this.beanFactory.isSingleton(beanName)) {
										this.advisorsCache.put(beanName, classAdvisors);
									} else {
										this.aspectFactoryCache.put(beanName, factory);
									}
									advisors.addAll(classAdvisors);
								} else {
									// 针对 perthis / pertarget 模型
									if (this.beanFactory.isSingleton(beanName)) {
										throw new IllegalArgumentException("单例 Bean 不能使用 perthis / pertarget 切面");
									}
									MetadataAwareAspectInstanceFactory factory =
											new PrototypeAspectInstanceFactory(this.beanFactory, beanName);
									this.aspectFactoryCache.put(beanName, factory);
									advisors.addAll(this.advisorFactory.getAdvisors(factory));
								}
								// 7. 缓存切面 Bean 名称
								aspectNames.add(beanName);
							} catch (IllegalArgumentException | IllegalStateException | AopConfigException ex) {
								// 忽略不兼容的切面
								if (logger.isDebugEnabled()) {
									logger.debug("忽略不兼容切面 [" + beanType.getName() + "]: " + ex);
								}
							}
						}
					}

					// 8. 保存切面 Bean 名称缓存
					this.aspectBeanNames = aspectNames;
					return advisors;
				}
			}
		}

		// 9. 如果已缓存切面 Bean，则直接读取缓存的 Advisors
		if (aspectNames.isEmpty()) {
			return Collections.emptyList();
		}

		List<Advisor> advisors = new ArrayList<>();
		for (String aspectName : aspectNames) {
			List<Advisor> cachedAdvisors = this.advisorsCache.get(aspectName);
			if (cachedAdvisors != null) {
				advisors.addAll(cachedAdvisors);
			} else {
				MetadataAwareAspectInstanceFactory factory = this.aspectFactoryCache.get(aspectName);
				advisors.addAll(this.advisorFactory.getAdvisors(factory));
			}
		}
		return advisors;
	}


	/**
	 * Return whether the aspect bean with the given name is eligible.
	 * @param beanName the name of the aspect bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleBean(String beanName) {
		return true;
	}

}
