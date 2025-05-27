package org.springframework.beans.factory.support;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * BeanDefinition 读取器工具类，提供 BeanDefinition 创建、生成唯一名称及注册等静态辅助方法。
 */
public abstract class BeanDefinitionReaderUtils {

	// 生成 Bean 名称的分隔符，默认为 "#"
	public static final String GENERATED_BEAN_NAME_SEPARATOR = BeanFactoryUtils.GENERATED_BEAN_NAME_SEPARATOR;

	/**
	 * 创建一个 GenericBeanDefinition 实例。
	 *
	 * @param parentName  父类 Bean 名称（可空）
	 * @param className   Bean 的全限定类名（可空）
	 * @param classLoader 用于加载类的 ClassLoader（可空）
	 * @return 创建好的 AbstractBeanDefinition 实例
	 * @throws ClassNotFoundException 如果指定类名无法加载，会抛出异常
	 */
	public static AbstractBeanDefinition createBeanDefinition(
			@Nullable String parentName, @Nullable String className, @Nullable ClassLoader classLoader)
			throws ClassNotFoundException {

		GenericBeanDefinition bd = new GenericBeanDefinition();
		// 设置父 Bean 名称
		bd.setParentName(parentName);
		if (className != null) {
			if (classLoader != null) {
				// 加载 Class 对象，设置到 bd 中
				bd.setBeanClass(ClassUtils.forName(className, classLoader));
			} else {
				// 只设置类名字符串（延迟加载）
				bd.setBeanClassName(className);
			}
		}
		return bd;
	}

	/**
	 * 生成一个唯一的 Bean 名称。
	 * 默认不认为是内部 Bean。
	 *
	 * @param beanDefinition 要生成名称的 BeanDefinition
	 * @param registry       注册表，用于检测名称冲突
	 * @return 生成的唯一 Bean 名称
	 * @throws BeanDefinitionStoreException 名称无法生成时抛出
	 */
	public static String generateBeanName(BeanDefinition beanDefinition, BeanDefinitionRegistry registry)
			throws BeanDefinitionStoreException {

		return generateBeanName(beanDefinition, registry, false);
	}

	/**
	 * 生成一个唯一的 Bean 名称。
	 *
	 * @param definition  要生成名称的 BeanDefinition
	 * @param registry    注册表，用于检测名称冲突
	 * @param isInnerBean 是否是内部 Bean（如匿名内部 Bean）
	 * @return 生成的唯一 Bean 名称
	 * @throws BeanDefinitionStoreException 名称无法生成时抛出
	 */
	public static String generateBeanName(
			BeanDefinition definition, BeanDefinitionRegistry registry, boolean isInnerBean)
			throws BeanDefinitionStoreException {

		// 先尝试用类名做为基础名称
		String generatedBeanName = definition.getBeanClassName();
		// 如果类名为空，则尝试用父 Bean 名称或工厂 Bean 名称拼接字符串作为基础名称
		if (generatedBeanName == null) {
			if (definition.getParentName() != null) {
				generatedBeanName = definition.getParentName() + "$child";
			} else if (definition.getFactoryBeanName() != null) {
				generatedBeanName = definition.getFactoryBeanName() + "$created";
			}
		}
		// 基础名称仍然为空，无法生成 Bean 名称，抛异常
		if (!StringUtils.hasText(generatedBeanName)) {
			throw new BeanDefinitionStoreException("Unnamed bean definition specifies neither " +
					"'class' nor 'parent' nor 'factory-bean' - can't generate bean name");
		}
		// 如果是内部 Bean，则生成一个带有身份 HashCode 的唯一名称，避免冲突
		if (isInnerBean) {
			return generatedBeanName + GENERATED_BEAN_NAME_SEPARATOR + ObjectUtils.getIdentityHexString(definition);
		}
		// 否则，生成在注册表中唯一的 Bean 名称
		return uniqueBeanName(generatedBeanName, registry);
	}

	/**
	 * 生成一个在注册表中唯一的 Bean 名称。
	 * 如果名称已存在，则在名称后追加 "#数字" 递增直到唯一。
	 *
	 * @param beanName 基础 Bean 名称
	 * @param registry 注册表
	 * @return 唯一的 Bean 名称
	 */
	public static String uniqueBeanName(String beanName, BeanDefinitionRegistry registry) {
		String id = beanName;
		int counter = -1;

		// 基础名称后面加分隔符 "#"
		String prefix = beanName + GENERATED_BEAN_NAME_SEPARATOR;

		// 如果名称冲突，计数器递增，拼接成新名称，直到找到唯一
		while (counter == -1 || registry.containsBeanDefinition(id)) {
			counter++;
			id = prefix + counter;
		}
		return id;
	}

	/**
	 * 注册 BeanDefinition 和其所有别名到 BeanDefinitionRegistry。
	 *
	 * @param definitionHolder BeanDefinition 包装对象，包含名称、别名、定义
	 * @param registry         注册表
	 * @throws BeanDefinitionStoreException 注册失败抛出
	 */
	public static void registerBeanDefinition(
			BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry)
			throws BeanDefinitionStoreException {

		String beanName = definitionHolder.getBeanName();
		// 注册 BeanDefinition
		registry.registerBeanDefinition(beanName, definitionHolder.getBeanDefinition());

		// 注册所有别名
		String[] aliases = definitionHolder.getAliases();
		if (aliases != null) {
			for (String alias : aliases) {
				registry.registerAlias(beanName, alias);
			}
		}
	}

	/**
	 * 根据 BeanDefinition 生成唯一名称，并注册到注册表。
	 *
	 * @param definition 要注册的 BeanDefinition
	 * @param registry   注册表
	 * @return 注册的 Bean 名称
	 * @throws BeanDefinitionStoreException 注册失败抛出
	 */
	public static String registerWithGeneratedName(
			AbstractBeanDefinition definition, BeanDefinitionRegistry registry)
			throws BeanDefinitionStoreException {

		// 生成唯一 Bean 名称
		String generatedName = generateBeanName(definition, registry, false);
		// 注册 BeanDefinition
		registry.registerBeanDefinition(generatedName, definition);
		return generatedName;
	}

}
