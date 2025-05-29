package org.springframework.beans.factory.support;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.NamedBeanHolder;
import org.springframework.beans.factory.config.RuntimeBeanNameReference;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

class BeanDefinitionValueResolver {

	private final AbstractAutowireCapableBeanFactory beanFactory;

	private final String beanName;

	private final BeanDefinition beanDefinition;

	private final TypeConverter typeConverter;


	public BeanDefinitionValueResolver(AbstractAutowireCapableBeanFactory beanFactory, String beanName,
									   BeanDefinition beanDefinition, TypeConverter typeConverter) {

		this.beanFactory = beanFactory;
		this.beanName = beanName;
		this.beanDefinition = beanDefinition;
		this.typeConverter = typeConverter;
	}


	/**
	 * 根据需要解析指定的值，常用于 BeanDefinition 中的属性注入解析过程。
	 *
	 * @param argName 参数名称，用于错误信息提示。
	 * @param value   待解析的值，可能是引用、集合、内嵌 Bean 等。
	 * @return 解析后的实际对象。
	 */
	@Nullable
	public Object resolveValueIfNecessary(Object argName, @Nullable Object value) {
		if (value instanceof RuntimeBeanReference) {
			// 如果是一个运行时 Bean 引用（ref），递归解析引用的 Bean。
			RuntimeBeanReference ref = (RuntimeBeanReference) value;
			// 解析引用类型的属性值
			return resolveReference(argName, ref);
		} else if (value instanceof RuntimeBeanNameReference) {
			// 如果是运行时 Bean 名称引用，获取 Bean 名称字符串
			String refName = ((RuntimeBeanNameReference) value).getBeanName();
			refName = String.valueOf(doEvaluate(refName));
			if (!this.beanFactory.containsBean(refName)) {
				throw new BeanDefinitionStoreException(
						"Invalid bean name '" + refName + "' in bean reference for " + argName);
			}
			return refName;
		} else if (value instanceof BeanDefinitionHolder) {
			// 如果是一个包含名称的内嵌 Bean 定义，递归解析这个内嵌 Bean
			BeanDefinitionHolder bdHolder = (BeanDefinitionHolder) value;
			return resolveInnerBean(argName, bdHolder.getBeanName(), bdHolder.getBeanDefinition());
		} else if (value instanceof BeanDefinition) {
			// 如果只是一个匿名的 BeanDefinition，构造内部名称并解析该 Bean
			BeanDefinition bd = (BeanDefinition) value;
			String innerBeanName = "(inner bean)" + BeanFactoryUtils.GENERATED_BEAN_NAME_SEPARATOR +
					ObjectUtils.getIdentityHexString(bd);
			return resolveInnerBean(argName, innerBeanName, bd);
		} else if (value instanceof DependencyDescriptor) {
			// 自动装配依赖，处理 @Autowired 场景
			Set<String> autowiredBeanNames = new LinkedHashSet<>(2);
			Object result = this.beanFactory.resolveDependency(
					(DependencyDescriptor) value, this.beanName, autowiredBeanNames, this.typeConverter);
			for (String autowiredBeanName : autowiredBeanNames) {
				if (this.beanFactory.containsBean(autowiredBeanName)) {
					// 注册依赖关系
					this.beanFactory.registerDependentBean(autowiredBeanName, this.beanName);
				}
			}
			return result;
		} else if (value instanceof ManagedArray) {
			// 处理 Spring 自定义的数组类型（ManagedArray）
			ManagedArray array = (ManagedArray) value;
			Class<?> elementType = array.resolvedElementType;
			if (elementType == null) {
				String elementTypeName = array.getElementTypeName();
				if (StringUtils.hasText(elementTypeName)) {
					try {
						// 根据名称解析数组元素类型
						elementType = ClassUtils.forName(elementTypeName, this.beanFactory.getBeanClassLoader());
						array.resolvedElementType = elementType;
					} catch (Throwable ex) {
						throw new BeanCreationException(
								this.beanDefinition.getResourceDescription(), this.beanName,
								"Error resolving array type for " + argName, ex);
					}
				} else {
					elementType = Object.class;
				}
			}
			return resolveManagedArray(argName, (List<?>) value, elementType);
		} else if (value instanceof ManagedList) {
			// 处理 Spring 自定义的 List 类型（ManagedList）
			return resolveManagedList(argName, (List<?>) value);
		} else if (value instanceof ManagedSet) {
			// 处理 Spring 自定义的 Set 类型（ManagedSet）
			return resolveManagedSet(argName, (Set<?>) value);
		} else if (value instanceof ManagedMap) {
			// 处理 Spring 自定义的 Map 类型（ManagedMap）
			return resolveManagedMap(argName, (Map<?, ?>) value);
		} else if (value instanceof ManagedProperties) {
			// 处理 Spring 自定义的 Properties 类型（ManagedProperties）
			Properties original = (Properties) value;
			Properties copy = new Properties();
			original.forEach((propKey, propValue) -> {
				if (propKey instanceof TypedStringValue) {
					propKey = evaluate((TypedStringValue) propKey);
				}
				if (propValue instanceof TypedStringValue) {
					propValue = evaluate((TypedStringValue) propValue);
				}
				if (propKey == null || propValue == null) {
					throw new BeanCreationException(
							this.beanDefinition.getResourceDescription(), this.beanName,
							"Error converting Properties key/value pair for " + argName + ": resolved to null");
				}
				copy.put(propKey, propValue);
			});
			return copy;
		} else if (value instanceof TypedStringValue) {
			// 类型为 TypedStringValue，带有目标类型的字符串值，需先评估再转换类型
			TypedStringValue typedStringValue = (TypedStringValue) value;
			Object valueObject = evaluate(typedStringValue);
			try {
				Class<?> resolvedTargetType = resolveTargetType(typedStringValue);
				if (resolvedTargetType != null) {
					return this.typeConverter.convertIfNecessary(valueObject, resolvedTargetType);
				} else {
					return valueObject;
				}
			} catch (Throwable ex) {
				throw new BeanCreationException(
						this.beanDefinition.getResourceDescription(), this.beanName,
						"Error converting typed String value for " + argName, ex);
			}
		} else if (value instanceof NullBean) {
			// Spring 特殊标识：显式指定为 null 的 Bean 引用
			return null;
		} else {
			// 默认情况，尝试对值进行 SpEL 表达式或占位符评估
			return evaluate(value);
		}
	}


	@Nullable
	protected Object evaluate(TypedStringValue value) {
		Object result = doEvaluate(value.getValue());
		if (!ObjectUtils.nullSafeEquals(result, value.getValue())) {
			value.setDynamic();
		}
		return result;
	}

	@Nullable
	protected Object evaluate(@Nullable Object value) {
		if (value instanceof String) {
			return doEvaluate((String) value);
		} else if (value instanceof String[]) {
			String[] values = (String[]) value;
			boolean actuallyResolved = false;
			Object[] resolvedValues = new Object[values.length];
			for (int i = 0; i < values.length; i++) {
				String originalValue = values[i];
				Object resolvedValue = doEvaluate(originalValue);
				if (resolvedValue != originalValue) {
					actuallyResolved = true;
				}
				resolvedValues[i] = resolvedValue;
			}
			return (actuallyResolved ? resolvedValues : values);
		} else {
			return value;
		}
	}

	@Nullable
	private Object doEvaluate(@Nullable String value) {
		return this.beanFactory.evaluateBeanDefinitionString(value, this.beanDefinition);
	}

	@Nullable
	protected Class<?> resolveTargetType(TypedStringValue value) throws ClassNotFoundException {
		if (value.hasTargetType()) {
			return value.getTargetType();
		}
		return value.resolveTargetType(this.beanFactory.getBeanClassLoader());
	}

	/**
	 * 解析 RuntimeBeanReference 类型的引用值。
	 * 用于根据 bean 名称或类型，从当前容器或父容器中获取对应的 bean 实例。
	 *
	 * @param argName 当前参数的名称（用于错误信息提示）
	 * @param ref     RuntimeBeanReference 对象，表示一个对其他 Bean 的引用
	 * @return 解析得到的 Bean 实例，可能为 null（如果引用的是 NullBean）
	 */
	@Nullable
	private Object resolveReference(Object argName, RuntimeBeanReference ref) {
		try {
			Object bean;
			Class<?> beanType = ref.getBeanType(); // 获取引用的 Bean 类型

			// 如果引用的是父容器中的 Bean
			if (ref.isToParent()) {
				BeanFactory parent = this.beanFactory.getParentBeanFactory(); // 获取父 BeanFactory
				if (parent == null) {
					// 如果没有父容器，抛出异常
					throw new BeanCreationException(
							this.beanDefinition.getResourceDescription(), this.beanName,
							"无法在父容器中解析 bean 引用 " + ref + "：未配置父容器");
				}
				// 如果指定了类型，用类型去父容器获取 Bean；否则用名称获取
				if (beanType != null) {
					bean = parent.getBean(beanType);
				} else {
					bean = parent.getBean(String.valueOf(doEvaluate(ref.getBeanName())));
				}
			}
			// 否则引用的是当前容器中的 Bean
			else {
				String resolvedName;
				if (beanType != null) {
					// 如果指定了类型，用类型解析带有名称的 Bean
					NamedBeanHolder<?> namedBean = this.beanFactory.resolveNamedBean(beanType);
					bean = namedBean.getBeanInstance();
					resolvedName = namedBean.getBeanName();
				} else {
					// 否则用 bean 名称解析
					resolvedName = String.valueOf(doEvaluate(ref.getBeanName()));
					bean = this.beanFactory.getBean(resolvedName);
				}
				// 注册依赖关系，说明当前 Bean 依赖于被引用的 Bean
				this.beanFactory.registerDependentBean(resolvedName, this.beanName);
			}

			// 如果引用的是 NullBean，则返回 null
			if (bean instanceof NullBean) {
				bean = null;
			}
			return bean;
		} catch (BeansException ex) {
			// 捕获任何异常，封装为 BeanCreationException 抛出
			throw new BeanCreationException(
					this.beanDefinition.getResourceDescription(), this.beanName,
					"设置 " + argName + " 时无法解析对 bean '" + ref.getBeanName() + "' 的引用", ex);
		}
	}


	@Nullable
	private Object resolveInnerBean(Object argName, String innerBeanName, BeanDefinition innerBd) {
		RootBeanDefinition mbd = null;
		try {
			mbd = this.beanFactory.getMergedBeanDefinition(innerBeanName, innerBd, this.beanDefinition);
			String actualInnerBeanName = innerBeanName;
			if (mbd.isSingleton()) {
				actualInnerBeanName = adaptInnerBeanName(innerBeanName);
			}
			this.beanFactory.registerContainedBean(actualInnerBeanName, this.beanName);
			String[] dependsOn = mbd.getDependsOn();
			if (dependsOn != null) {
				for (String dependsOnBean : dependsOn) {
					this.beanFactory.registerDependentBean(dependsOnBean, actualInnerBeanName);
					this.beanFactory.getBean(dependsOnBean);
				}
			}
			Object innerBean = this.beanFactory.createBean(actualInnerBeanName, mbd, null);
			if (innerBean instanceof FactoryBean) {
				boolean synthetic = mbd.isSynthetic();
				innerBean = this.beanFactory.getObjectFromFactoryBean(
						(FactoryBean<?>) innerBean, actualInnerBeanName, !synthetic);
			}
			if (innerBean instanceof NullBean) {
				innerBean = null;
			}
			return innerBean;
		} catch (BeansException ex) {
			throw new BeanCreationException(
					this.beanDefinition.getResourceDescription(), this.beanName,
					"Cannot create inner bean '" + innerBeanName + "' " +
							(mbd != null && mbd.getBeanClassName() != null ? "of type [" + mbd.getBeanClassName() + "] " : "") +
							"while setting " + argName, ex);
		}
	}

	private String adaptInnerBeanName(String innerBeanName) {
		String actualInnerBeanName = innerBeanName;
		int counter = 0;
		String prefix = innerBeanName + BeanFactoryUtils.GENERATED_BEAN_NAME_SEPARATOR;
		while (this.beanFactory.isBeanNameInUse(actualInnerBeanName)) {
			counter++;
			actualInnerBeanName = prefix + counter;
		}
		return actualInnerBeanName;
	}

	private Object resolveManagedArray(Object argName, List<?> ml, Class<?> elementType) {
		Object resolved = Array.newInstance(elementType, ml.size());
		for (int i = 0; i < ml.size(); i++) {
			Array.set(resolved, i, resolveValueIfNecessary(new KeyedArgName(argName, i), ml.get(i)));
		}
		return resolved;
	}

	private List<?> resolveManagedList(Object argName, List<?> ml) {
		List<Object> resolved = new ArrayList<>(ml.size());
		for (int i = 0; i < ml.size(); i++) {
			resolved.add(resolveValueIfNecessary(new KeyedArgName(argName, i), ml.get(i)));
		}
		return resolved;
	}

	private Set<?> resolveManagedSet(Object argName, Set<?> ms) {
		Set<Object> resolved = new LinkedHashSet<>(ms.size());
		int i = 0;
		for (Object m : ms) {
			resolved.add(resolveValueIfNecessary(new KeyedArgName(argName, i), m));
			i++;
		}
		return resolved;
	}

	private Map<?, ?> resolveManagedMap(Object argName, Map<?, ?> mm) {
		Map<Object, Object> resolved = CollectionUtils.newLinkedHashMap(mm.size());
		mm.forEach((key, value) -> {
			Object resolvedKey = resolveValueIfNecessary(argName, key);
			Object resolvedValue = resolveValueIfNecessary(new KeyedArgName(argName, key), value);
			resolved.put(resolvedKey, resolvedValue);
		});
		return resolved;
	}


	private static class KeyedArgName {

		private final Object argName;

		private final Object key;

		public KeyedArgName(Object argName, Object key) {
			this.argName = argName;
			this.key = key;
		}

		@Override
		public String toString() {
			return this.argName + " with key " + BeanWrapper.PROPERTY_KEY_PREFIX +
					this.key + BeanWrapper.PROPERTY_KEY_SUFFIX;
		}
	}

}
