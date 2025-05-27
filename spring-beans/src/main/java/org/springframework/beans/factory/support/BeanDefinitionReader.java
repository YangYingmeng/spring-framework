package org.springframework.beans.factory.support;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;

/**
 * Bean 定义读取器接口，用于将 Bean 定义从各种资源（如 XML、注解、配置类）中加载到 BeanDefinitionRegistry 中。
 * <p>主要被 ApplicationContext 和 BeanFactory 在容器初始化时调用，用于构建 IoC 容器中所有的 BeanDefinition。
 */
public interface BeanDefinitionReader {

	/**
	 * 获取当前 BeanDefinitionReader 绑定的 BeanDefinitionRegistry。
	 * <p>所有加载的 BeanDefinition 都将注册到该注册表中。
	 *
	 * @return Bean 定义注册器
	 */
	BeanDefinitionRegistry getRegistry();

	/**
	 * 获取资源加载器 ResourceLoader，用于加载配置资源（如 XML 文件、注解类等）。
	 * <p>可能为 null，表示不支持通过路径字符串加载资源。
	 *
	 * @return 资源加载器（可为空）
	 */
	@Nullable
	ResourceLoader getResourceLoader();

	/**
	 * 获取用于加载 Bean 类的类加载器。
	 * <p>通常用于在解析 Bean 定义时实例化类对象，可能为 null。
	 *
	 * @return 类加载器（可为空）
	 */
	@Nullable
	ClassLoader getBeanClassLoader();

	/**
	 * 获取用于生成 Bean 名称的策略对象。
	 * <p>当 Bean 未显式指定名称时使用，如配置类中的 @Bean 方法。
	 *
	 * @return Bean 名称生成器
	 */
	BeanNameGenerator getBeanNameGenerator();

	/**
	 * 从指定的 Resource 资源中加载并注册 Bean 定义。
	 *
	 * @param resource 资源对象（如 XML 文件、properties 文件等）
	 * @return 加载的 Bean 定义数量
	 * @throws BeanDefinitionStoreException 加载失败时抛出
	 */
	int loadBeanDefinitions(Resource resource) throws BeanDefinitionStoreException;

	/**
	 * 从多个 Resource 资源中批量加载并注册 Bean 定义。
	 *
	 * @param resources 多个资源对象
	 * @return 加载的 Bean 定义总数
	 * @throws BeanDefinitionStoreException 加载失败时抛出
	 */
	int loadBeanDefinitions(Resource... resources) throws BeanDefinitionStoreException;

	/**
	 * 根据给定的位置（如 classpath 路径、文件系统路径或 URL）加载 Bean 定义。
	 *
	 * @param location 资源路径（支持通配符、classpath 前缀等）
	 * @return 加载的 Bean 定义数量
	 * @throws BeanDefinitionStoreException 加载失败时抛出
	 */
	int loadBeanDefinitions(String location) throws BeanDefinitionStoreException;

	/**
	 * 批量加载多个位置下的 Bean 定义。
	 *
	 * @param locations 多个资源路径
	 * @return 加载的 Bean 定义总数
	 * @throws BeanDefinitionStoreException 加载失败时抛出
	 */
	int loadBeanDefinitions(String... locations) throws BeanDefinitionStoreException;
}

