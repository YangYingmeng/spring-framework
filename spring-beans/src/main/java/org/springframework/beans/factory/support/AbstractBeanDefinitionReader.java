package org.springframework.beans.factory.support;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * 抽象的 BeanDefinitionReader 实现类，封装了公共的属性和通用方法。
 * 提供 Bean 注册器、资源加载器、环境变量、类加载器等功能。
 */
public abstract class AbstractBeanDefinitionReader implements BeanDefinitionReader, EnvironmentCapable {

	// 日志记录器
	protected final Log logger = LogFactory.getLog(getClass());

	// Bean 定义注册器（如 DefaultListableBeanFactory）
	private final BeanDefinitionRegistry registry;

	// 资源加载器（用于加载 XML、注解、配置类等资源）
	@Nullable
	private ResourceLoader resourceLoader;

	// Bean 类加载器（用于加载配置类、注解类等）
	@Nullable
	private ClassLoader beanClassLoader;

	// Spring 环境对象（如包含 profile、property 等）
	private Environment environment;

	// Bean 名称生成器（用于注册时生成默认 Bean 名）
	private BeanNameGenerator beanNameGenerator = DefaultBeanNameGenerator.INSTANCE;

	/**
	 * 构造方法，传入 BeanDefinitionRegistry 并初始化 ResourceLoader 和 Environment。
	 */
	protected AbstractBeanDefinitionReader(BeanDefinitionRegistry registry) {
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		this.registry = registry;

		// 如果 registry 本身实现了 ResourceLoader，则使用它
		if (this.registry instanceof ResourceLoader) {
			this.resourceLoader = (ResourceLoader) this.registry;
		} else {
			// 否则使用默认的路径匹配资源加载器
			this.resourceLoader = new PathMatchingResourcePatternResolver();
		}

		// 如果 registry 支持 Environment（如 ApplicationContext），则使用其环境
		if (this.registry instanceof EnvironmentCapable) {
			this.environment = ((EnvironmentCapable) this.registry).getEnvironment();
		} else {
			// 否则使用标准环境
			this.environment = new StandardEnvironment();
		}
	}

	/**
	 * 已废弃，使用 getRegistry() 替代。
	 */
	@Deprecated
	public final BeanDefinitionRegistry getBeanFactory() {
		return this.registry;
	}

	/**
	 * 获取 Bean 注册器。
	 */
	@Override
	public final BeanDefinitionRegistry getRegistry() {
		return this.registry;
	}

	/**
	 * 设置资源加载器。
	 */
	public void setResourceLoader(@Nullable ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	/**
	 * 获取资源加载器。
	 */
	@Override
	@Nullable
	public ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	/**
	 * 设置类加载器。
	 */
	public void setBeanClassLoader(@Nullable ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
	}

	/**
	 * 获取类加载器。
	 */
	@Override
	@Nullable
	public ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	/**
	 * 设置环境对象。
	 */
	public void setEnvironment(Environment environment) {
		Assert.notNull(environment, "Environment must not be null");
		this.environment = environment;
	}

	/**
	 * 获取环境对象。
	 */
	@Override
	public Environment getEnvironment() {
		return this.environment;
	}

	/**
	 * 设置 Bean 名称生成器。
	 */
	public void setBeanNameGenerator(@Nullable BeanNameGenerator beanNameGenerator) {
		this.beanNameGenerator = (beanNameGenerator != null ? beanNameGenerator : DefaultBeanNameGenerator.INSTANCE);
	}

	/**
	 * 获取 Bean 名称生成器。
	 */
	@Override
	public BeanNameGenerator getBeanNameGenerator() {
		return this.beanNameGenerator;
	}

	/**
	 * 加载多个资源中的 BeanDefinition。
	 */
	@Override
	public int loadBeanDefinitions(Resource... resources) throws BeanDefinitionStoreException {
		Assert.notNull(resources, "Resource array must not be null");
		int count = 0;
		for (Resource resource : resources) {
			count += loadBeanDefinitions(resource); // 抽象方法，由子类实现
		}
		return count;
	}

	/**
	 * 根据位置字符串加载 BeanDefinition（支持 classpath: 等前缀）
	 */
	@Override
	public int loadBeanDefinitions(String location) throws BeanDefinitionStoreException {
		return loadBeanDefinitions(location, null);
	}

	/**
	 * 根据位置字符串加载 BeanDefinition，并可记录实际加载的资源（actualResources）。
	 */
	public int loadBeanDefinitions(String location, @Nullable Set<Resource> actualResources)
			throws BeanDefinitionStoreException {

		ResourceLoader resourceLoader = getResourceLoader();
		if (resourceLoader == null) {
			throw new BeanDefinitionStoreException(
					"Cannot load bean definitions from location [" + location + "]: no ResourceLoader available");
		}

		// 如果资源加载器支持模式匹配（如 classpath*:myBeans.xml）
		if (resourceLoader instanceof ResourcePatternResolver) {
			try {
				Resource[] resources = ((ResourcePatternResolver) resourceLoader).getResources(location);
				int count = loadBeanDefinitions(resources); // 批量加载
				if (actualResources != null) {
					Collections.addAll(actualResources, resources); // 记录加载过的资源
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Loaded " + count + " bean definitions from location pattern [" + location + "]");
				}
				return count;
			} catch (IOException ex) {
				throw new BeanDefinitionStoreException(
						"Could not resolve bean definition resource pattern [" + location + "]", ex);
			}
		} else {
			// 不支持模式匹配时只加载一个资源
			Resource resource = resourceLoader.getResource(location);
			int count = loadBeanDefinitions(resource);
			if (actualResources != null) {
				actualResources.add(resource);
			}
			if (logger.isTraceEnabled()) {
				logger.trace("Loaded " + count + " bean definitions from location [" + location + "]");
			}
			return count;
		}
	}

	/**
	 * 加载多个位置中的 BeanDefinition。
	 */
	@Override
	public int loadBeanDefinitions(String... locations) throws BeanDefinitionStoreException {
		Assert.notNull(locations, "Location array must not be null");
		int count = 0;
		for (String location : locations) {
			count += loadBeanDefinitions(location);
		}
		return count;
	}

	// 抽象方法 loadBeanDefinitions(Resource resource) 由子类实现，如 XmlBeanDefinitionReader、PropertiesBeanDefinitionReader 等
}
