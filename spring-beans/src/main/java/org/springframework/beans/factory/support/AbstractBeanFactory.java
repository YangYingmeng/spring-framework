package org.springframework.beans.factory.support;

import java.beans.PropertyEditor;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.PropertyEditorRegistrySupport;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanIsAbstractException;
import org.springframework.beans.factory.BeanIsNotAFactoryException;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.Scope;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.DecoratingClassLoader;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.log.LogMessage;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * 抽象的 Bean 工厂基类，继承了 FactoryBeanRegistrySupport，并实现了 ConfigurableBeanFactory。
 *
 * 【核心作用】
 * 这个类是 Spring IoC 容器的核心部分，负责 Bean 的创建、缓存、类型推断、FactoryBean 的处理等。
 * 是 DefaultListableBeanFactory、XmlBeanFactory 等具体 BeanFactory 的共同父类。
 *
 * 【主要功能】
 * 1. 提供了 getBean() 的公共实现流程（延迟到子类实现具体的创建策略）；
 * 2. 维护单例 Bean 的缓存（singletonObjects）；
 * 3. 支持 FactoryBean 的特殊处理（继承自 FactoryBeanRegistrySupport）；
 * 4. 实现了 BeanDefinition 的合并（合并父子定义）；
 * 5. 提供 Bean 的作用域、别名、类型匹配等支持；
 * 6. 管理 Bean 的生命周期回调（如 BeanPostProcessor）；
 * 7. 支持嵌套容器（父 BeanFactory）和分层查找；
 *
 * 【设计模式】
 * - 模板方法模式：定义了获取 Bean 的标准流程，但具体创建 Bean 的逻辑延迟给子类（如 DefaultListableBeanFactory）；
 * - 单例模式：管理 Bean 的缓存实例；
 * - 策略模式：支持不同作用域（singleton、prototype、自定义）策略扩展；
 *
 * 【继承结构】
 * Object
 *  └── DefaultSingletonBeanRegistry（管理 singleton 缓存）
 *      └── FactoryBeanRegistrySupport（管理 FactoryBean 相关逻辑）
 *          └── AbstractBeanFactory（提供核心 Bean 获取与生命周期管理）
 *              └── DefaultListableBeanFactory（最常用的标准容器实现类）
 */
public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {

	@Nullable
	private BeanFactory parentBeanFactory;

	@Nullable
	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	@Nullable
	private ClassLoader tempClassLoader;

	private boolean cacheBeanMetadata = true;

	@Nullable
	private BeanExpressionResolver beanExpressionResolver;

	@Nullable
	private ConversionService conversionService;

	private final Set<PropertyEditorRegistrar> propertyEditorRegistrars = new LinkedHashSet<>(4);

	private final Map<Class<?>, Class<? extends PropertyEditor>> customEditors = new HashMap<>(4);

	@Nullable
	private TypeConverter typeConverter;

	private final List<StringValueResolver> embeddedValueResolvers = new CopyOnWriteArrayList<>();

	private final List<BeanPostProcessor> beanPostProcessors = new BeanPostProcessorCacheAwareList();

	@Nullable
	private BeanPostProcessorCache beanPostProcessorCache;

	private final Map<String, Scope> scopes = new LinkedHashMap<>(8);

	private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;

	@Nullable
	private SecurityContextProvider securityContextProvider;

	private final Map<String, RootBeanDefinition> mergedBeanDefinitions = new ConcurrentHashMap<>(256);

	private final Set<String> alreadyCreated = Collections.newSetFromMap(new ConcurrentHashMap<>(256));

	private final ThreadLocal<Object> prototypesCurrentlyInCreation =
			new NamedThreadLocal<>("Prototype beans currently in creation");


	public AbstractBeanFactory() {
	}

	public AbstractBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		this.parentBeanFactory = parentBeanFactory;
	}


	@Override
	public Object getBean(String name) throws BeansException {
		return doGetBean(name, null, null, false);
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		return doGetBean(name, requiredType, null, false);
	}

	@Override
	public Object getBean(String name, Object... args) throws BeansException {
		return doGetBean(name, null, args, false);
	}

	public <T> T getBean(String name, @Nullable Class<T> requiredType, @Nullable Object... args)
			throws BeansException {

		return doGetBean(name, requiredType, args, false);
	}

	@SuppressWarnings("unchecked")
	protected <T> T doGetBean(
			String name, @Nullable Class<T> requiredType, @Nullable Object[] args, boolean typeCheckOnly)
			throws BeansException {

		// ----------------- 1. 名称转换 -----------------
		// 转换 bean 名称（可能是别名、工厂方法名，转为真实 beanName）
		String beanName = transformedBeanName(name);
		Object beanInstance;

		// ----------------- 2. 尝试从单例缓存获取 -----------------
		Object sharedInstance = getSingleton(beanName);
		if (sharedInstance != null && args == null) {
			// 日志记录是否是由于循环依赖提早暴露的单例对象
			if (logger.isTraceEnabled()) {
				if (isSingletonCurrentlyInCreation(beanName)) {
					logger.trace("Returning eagerly cached instance of singleton bean '" + beanName +
							"' that is not fully initialized yet - a consequence of a circular reference");
				} else {
					logger.trace("Returning cached instance of singleton bean '" + beanName + "'");
				}
			}
			// 返回对象（如果是 FactoryBean 则处理）
			beanInstance = getObjectForBeanInstance(sharedInstance, name, beanName, null);
		} else {

			// ----------------- 3. 检查 prototype 循环依赖 -----------------
			if (isPrototypeCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(beanName);
			}

			// ----------------- 4. 父工厂委托 -----------------
			BeanFactory parentBeanFactory = getParentBeanFactory();
			if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
				String nameToLookup = originalBeanName(name);
				if (parentBeanFactory instanceof AbstractBeanFactory) {
					return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
							nameToLookup, requiredType, args, typeCheckOnly);
				} else if (args != null) {
					return (T) parentBeanFactory.getBean(nameToLookup, args);
				} else if (requiredType != null) {
					return parentBeanFactory.getBean(nameToLookup, requiredType);
				} else {
					return (T) parentBeanFactory.getBean(nameToLookup);
				}
			}


			// ----------------- 5. 标记 Bean 开始创建 -----------------
			if (!typeCheckOnly) {
				markBeanAsCreated(beanName);
			}

			// ----------------- 6. 获取合并的 RootBeanDefinition -----------------
			StartupStep beanCreation = this.applicationStartup.start("spring.beans.instantiate")
					.tag("beanName", name);
			try {
				if (requiredType != null) {
					beanCreation.tag("beanType", requiredType::toString);
				}

				// 获取合并的 RootBeanDefinition
				RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
				checkMergedBeanDefinition(mbd, beanName, args);


				// ----------------- 7. 处理 depends-on 依赖 -----------------
				String[] dependsOn = mbd.getDependsOn();
				if (dependsOn != null) {
					for (String dep : dependsOn) {
						// 检查循环依赖
						if (isDependent(beanName, dep)) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
						}
						// 注册依赖关系并创建依赖的 bean
						registerDependentBean(dep, beanName);
						try {
							getBean(dep);
						} catch (NoSuchBeanDefinitionException ex) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"'" + beanName + "' depends on missing bean '" + dep + "'", ex);
						}
					}
				}

				// ----------------- 8. 根据 Bean 作用域创建实例 -----------------
				// 针对不同 scope 分别处理：singleton、prototype、其他（如 request、session）
				if (mbd.isSingleton()) {

					sharedInstance = getSingleton(beanName, () -> {
						try {
							// 初始化Bean
							return createBean(beanName, mbd, args);
						} catch (BeansException ex) {
							destroySingleton(beanName); // 创建失败，清理缓存
							throw ex;
						}
					});
					// 判断是否是FactoryBean 根据名称前缀是否是&判断返回工厂对象本身还是工厂的内部对象
					beanInstance = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);

				} else if (mbd.isPrototype()) {
					// 创建 prototype bean，每次新建
					Object prototypeInstance = null;
					try {
						beforePrototypeCreation(beanName); // 设置当前线程创建状态
						prototypeInstance = createBean(beanName, mbd, args);
					} finally {
						afterPrototypeCreation(beanName); // 清除线程状态
					}
					beanInstance = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
				} else {
					// 自定义 scope（如 request、session）
					String scopeName = mbd.getScope();
					if (!StringUtils.hasLength(scopeName)) {
						throw new IllegalStateException("No scope name defined for bean '" + beanName + "'");
					}
					Scope scope = this.scopes.get(scopeName);
					if (scope == null) {
						throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
					}
					try {
						Object scopedInstance = scope.get(beanName, () -> {
							beforePrototypeCreation(beanName);
							try {
								return createBean(beanName, mbd, args);
							} finally {
								afterPrototypeCreation(beanName);
							}
						});
						beanInstance = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
					} catch (IllegalStateException ex) {
						throw new ScopeNotActiveException(beanName, scopeName, ex);
					}
				}
			} catch (BeansException ex) {
				// 创建失败日志记录和清理
				beanCreation.tag("exception", ex.getClass().toString());
				beanCreation.tag("message", String.valueOf(ex.getMessage()));
				cleanupAfterBeanCreationFailure(beanName);
				throw ex;
			} finally {
				beanCreation.end(); // 记录 bean 创建步骤结束
			}
		}

		// ----------------- 10. 返回最终 Bean 实例 -----------------
		// 如果 requiredType 不为空，会进行类型检查或转换
		return adaptBeanInstance(name, beanInstance, requiredType);
	}


	@SuppressWarnings("unchecked")
	<T> T adaptBeanInstance(String name, Object bean, @Nullable Class<?> requiredType) {
		// 如果传入的 requiredType 不为空，并且 bean 不是该类型的实例，则尝试类型转换
		if (requiredType != null && !requiredType.isInstance(bean)) {
			try {
				// 使用类型转换器进行类型转换
				Object convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
				if (convertedBean == null) {
					// 转换失败，抛出异常
					throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
				}
				return (T) convertedBean;
			}
			catch (TypeMismatchException ex) {
				// 打印日志并抛出异常
				if (logger.isTraceEnabled()) {
					logger.trace("Failed to convert bean '" + name + "' to required type '" +
							ClassUtils.getQualifiedName(requiredType) + "'", ex);
				}
				throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
			}
		}
		// 类型匹配或没有指定 requiredType，直接返回原始 bean
		return (T) bean;
	}

	@Override
	public boolean containsBean(String name) {
		String beanName = transformedBeanName(name); // 去除 FactoryBean 的前缀
		// 如果本地存在单例或 bean 定义
		if (containsSingleton(beanName) || containsBeanDefinition(beanName)) {
			// 如果不是获取工厂本身 或 是 FactoryBean，则返回 true
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(name));
		}
		// 否则检查父容器
		BeanFactory parentBeanFactory = getParentBeanFactory();
		return (parentBeanFactory != null && parentBeanFactory.containsBean(originalBeanName(name)));
	}

	@Override
	public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);
		Object beanInstance = getSingleton(beanName, false);

		if (beanInstance != null) {
			if (beanInstance instanceof FactoryBean) {
				// 如果是 FactoryBean，根据是否是 dereference 判断
				return (BeanFactoryUtils.isFactoryDereference(name) || ((FactoryBean<?>) beanInstance).isSingleton());
			} else {
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		}

		// 如果父容器存在并且本地无定义，递归父容器
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			return parentBeanFactory.isSingleton(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

		if (mbd.isSingleton()) {
			if (isFactoryBean(beanName, mbd)) {
				if (BeanFactoryUtils.isFactoryDereference(name)) {
					return true;
				}
				FactoryBean<?> factoryBean = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
				return factoryBean.isSingleton();
			} else {
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		} else {
			return false;
		}
	}


	@Override
	public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);

		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			return parentBeanFactory.isPrototype(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isPrototype()) {
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName, mbd));
		}

		if (BeanFactoryUtils.isFactoryDereference(name)) {
			return false;
		}

		// 如果是 FactoryBean，根据是否为 SmartFactoryBean 和是否为单例判断
		if (isFactoryBean(beanName, mbd)) {
			FactoryBean<?> fb = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged(
						(PrivilegedAction<Boolean>) () ->
								((fb instanceof SmartFactoryBean && ((SmartFactoryBean<?>) fb).isPrototype()) ||
										!fb.isSingleton()),
						getAccessControlContext());
			} else {
				return ((fb instanceof SmartFactoryBean && ((SmartFactoryBean<?>) fb).isPrototype()) ||
						!fb.isSingleton());
			}
		} else {
			return false;
		}
	}


	@Override
	public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, typeToMatch, true);
	}

	/**
	 * 判断指定名称的 bean 是否匹配给定的类型（包括泛型信息）。
	 *
	 * @param name bean 的名称，可能包含 & 前缀表示是否是对 FactoryBean 本身的引用。
	 * @param typeToMatch 需要匹配的类型（包含泛型信息）。
	 * @param allowFactoryBeanInit 是否允许初始化 FactoryBean（懒加载场景下控制是否触发初始化）。
	 * @return 是否匹配给定类型。
	 * @throws NoSuchBeanDefinitionException 如果找不到 bean 定义。
	 */
	protected boolean isTypeMatch(String name, ResolvableType typeToMatch, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException {

		// 去除 & 前缀，获取真实的 bean 名称
		String beanName = transformedBeanName(name);
		// 是否对 FactoryBean 本身进行引用（即 name 以 & 开头）
		boolean isFactoryDereference = BeanFactoryUtils.isFactoryDereference(name);

		// 尝试从单例缓存中获取 bean 实例（不触发创建）
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
			// 如果是 FactoryBean 且不以 & 开头，表示调用 getObject() 返回的类型
			if (beanInstance instanceof FactoryBean) {
				if (!isFactoryDereference) {
					// 获取 FactoryBean 中 getObject() 的类型
					Class<?> type = getTypeForFactoryBean((FactoryBean<?>) beanInstance);
					return (type != null && typeToMatch.isAssignableFrom(type));
				} else {
					// 以 & 开头，直接判断 FactoryBean 本身的类型
					return typeToMatch.isInstance(beanInstance);
				}
			}
			// 普通 bean 且非 &，直接匹配实例
			else if (!isFactoryDereference) {
				if (typeToMatch.isInstance(beanInstance)) {
					return true;
				}
				// 如果包含泛型信息，并且存在 bean 定义，进一步检查类型
				else if (typeToMatch.hasGenerics() && containsBeanDefinition(beanName)) {
					RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
					Class<?> targetType = mbd.getTargetType();
					if (targetType != null && targetType != ClassUtils.getUserClass(beanInstance)) {
						Class<?> classToMatch = typeToMatch.resolve();
						if (classToMatch != null && !classToMatch.isInstance(beanInstance)) {
							return false;
						}
						if (typeToMatch.isAssignableFrom(targetType)) {
							return true;
						}
					}
					// 回退使用定义的 ResolvableType 进行匹配
					ResolvableType resolvableType = mbd.targetType;
					if (resolvableType == null) {
						resolvableType = mbd.factoryMethodReturnType;
					}
					return (resolvableType != null && typeToMatch.isAssignableFrom(resolvableType));
				}
			}
			return false;
		}
		// 已有单例但无 bean 定义，不能判断类型
		else if (containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			return false;
		}

		// 委托父 BeanFactory 判断类型
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			return parentBeanFactory.isTypeMatch(originalBeanName(name), typeToMatch);
		}

		// 合并 bean 定义
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		// 如果有包装装饰（例如 @Primary 等修饰 bean），获取其定义
		BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();

		// 解析目标类型的原始类型（不包含泛型）
		Class<?> classToMatch = typeToMatch.resolve();
		if (classToMatch == null) {
			classToMatch = FactoryBean.class;
		}

		// 如果不是精确类型匹配 FactoryBean，则加上 FactoryBean.class
		Class<?>[] typesToMatch = (FactoryBean.class == classToMatch ?
				new Class<?>[]{classToMatch} : new Class<?>[]{FactoryBean.class, classToMatch});

		Class<?> predictedType = null;

		// 如果是 FactoryBean，但当前引用不是 & 且不懒加载或允许初始化，预测返回类型
		if (!isFactoryDereference && dbd != null && isFactoryBean(beanName, mbd)) {
			if (!mbd.isLazyInit() || allowFactoryBeanInit) {
				RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
				Class<?> targetType = predictBeanType(dbd.getBeanName(), tbd, typesToMatch);
				if (targetType != null && !FactoryBean.class.isAssignableFrom(targetType)) {
					predictedType = targetType;
				}
			}
		}

		// 如果未预测出类型，则再次尝试
		if (predictedType == null) {
			predictedType = predictBeanType(beanName, mbd, typesToMatch);
			if (predictedType == null) {
				return false;
			}
		}

		ResolvableType beanType = null;

		// 如果是 FactoryBean 且未初始化，尝试获取其返回类型
		if (FactoryBean.class.isAssignableFrom(predictedType)) {
			if (beanInstance == null && !isFactoryDereference) {
				beanType = getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit);
				predictedType = beanType.resolve();
				if (predictedType == null) {
					return false;
				}
			}
		}
		// 如果是以 & 方式引用 FactoryBean 本身，必须确保类型为 FactoryBean 子类
		else if (isFactoryDereference) {
			predictedType = predictBeanType(beanName, mbd, FactoryBean.class);
			if (predictedType == null || !FactoryBean.class.isAssignableFrom(predictedType)) {
				return false;
			}
		}

		// 尝试使用 BeanDefinition 中定义的 targetType 或 factory 方法返回类型
		if (beanType == null) {
			ResolvableType definedType = mbd.targetType;
			if (definedType == null) {
				definedType = mbd.factoryMethodReturnType;
			}
			if (definedType != null && definedType.resolve() == predictedType) {
				beanType = definedType;
			}
		}

		// 使用 beanType 判断类型匹配（含泛型信息）
		if (beanType != null) {
			return typeToMatch.isAssignableFrom(beanType);
		}

		// 回退使用原始类型匹配
		return typeToMatch.isAssignableFrom(predictedType);
	}

	@Override
	public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, ResolvableType.forRawClass(typeToMatch));
	}

	@Override
	@Nullable
	public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		return getType(name, true);
	}

	/**
	 * 获取指定 Bean 的类型。
	 *
	 * @param name Bean 名称（可能包含 & 前缀以表示工厂 Bean 本身）
	 * @param allowFactoryBeanInit 是否允许初始化 FactoryBean 以确定返回类型
	 * @return Bean 的类型，找不到则抛出异常
	 */
	@Override
	@Nullable
	public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
		// 获取转换后的 Bean 名称（去掉 & 前缀）
		String beanName = transformedBeanName(name);

		// 检查是否已存在单例实例
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
			// 如果是 FactoryBean 且调用者不想要 FactoryBean 本身
			if (beanInstance instanceof FactoryBean && !BeanFactoryUtils.isFactoryDereference(name)) {
				// 获取其创建对象的类型
				return getTypeForFactoryBean((FactoryBean<?>) beanInstance);
			}
			else {
				// 否则返回其实际类
				return beanInstance.getClass();
			}
		}

		// 如果当前容器中没有定义，尝试从父容器中获取
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			return parentBeanFactory.getType(originalBeanName(name));
		}

		// 获取合并的 BeanDefinition
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

		// 若是装饰 Bean（如使用 @Primary 指定了另一个定义），尝试预测目标类型
		BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
		if (dbd != null && !BeanFactoryUtils.isFactoryDereference(name)) {
			RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
			Class<?> targetClass = predictBeanType(dbd.getBeanName(), tbd);
			if (targetClass != null && !FactoryBean.class.isAssignableFrom(targetClass)) {
				return targetClass;
			}
		}

		// 预测 Bean 的类型
		Class<?> beanClass = predictBeanType(beanName, mbd);

		// 如果是 FactoryBean 类型
		if (beanClass != null && FactoryBean.class.isAssignableFrom(beanClass)) {
			// 调用者不是为了获取 FactoryBean 本身
			if (!BeanFactoryUtils.isFactoryDereference(name)) {
				// 获取其创建的对象类型
				return getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit).resolve();
			}
			else {
				return beanClass; // 获取 FactoryBean 本身的类型
			}
		}
		else {
			// 如果不是 FactoryBean 或者用户不想获取 FactoryBean 本身
			return (!BeanFactoryUtils.isFactoryDereference(name) ? beanClass : null);
		}
	}

	/**
	 * 获取指定 Bean 的所有别名。
	 *
	 * @param name Bean 名称（可能包含 & 前缀以表示工厂 Bean 本身）
	 * @return 所有别名
	 */
	@Override
	public String[] getAliases(String name) {
		// 去除工厂前缀后的标准名称
		String beanName = transformedBeanName(name);
		List<String> aliases = new ArrayList<>();

		// 是否是工厂 Bean 的引用（以 & 开头）
		boolean factoryPrefix = name.startsWith(FACTORY_BEAN_PREFIX);
		String fullBeanName = beanName;
		if (factoryPrefix) {
			fullBeanName = FACTORY_BEAN_PREFIX + beanName;
		}

		// 如果传入的是别名，添加其实际 Bean 名称的别名
		if (!fullBeanName.equals(name)) {
			aliases.add(fullBeanName);
		}

		// 获取当前 Bean 的所有别名
		String[] retrievedAliases = super.getAliases(beanName);
		String prefix = (factoryPrefix ? FACTORY_BEAN_PREFIX : "");

		for (String retrievedAlias : retrievedAliases) {
			String alias = prefix + retrievedAlias;
			if (!alias.equals(name)) {
				aliases.add(alias);
			}
		}

		// 如果当前容器中没有定义这个 Bean，但可能在父容器中有定义
		if (!containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			BeanFactory parentBeanFactory = getParentBeanFactory();
			if (parentBeanFactory != null) {
				aliases.addAll(Arrays.asList(parentBeanFactory.getAliases(fullBeanName)));
			}
		}

		return StringUtils.toStringArray(aliases);
	}



	@Override
	@Nullable
	public BeanFactory getParentBeanFactory() {
		return this.parentBeanFactory;
	}

	@Override
	public boolean containsLocalBean(String name) {
		String beanName = transformedBeanName(name);
		return ((containsSingleton(beanName) || containsBeanDefinition(beanName)) &&
				(!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName)));
	}


	@Override
	public void setParentBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		if (this.parentBeanFactory != null && this.parentBeanFactory != parentBeanFactory) {
			throw new IllegalStateException("Already associated with parent BeanFactory: " + this.parentBeanFactory);
		}
		if (this == parentBeanFactory) {
			throw new IllegalStateException("Cannot set parent bean factory to self");
		}
		this.parentBeanFactory = parentBeanFactory;
	}

	@Override
	public void setBeanClassLoader(@Nullable ClassLoader beanClassLoader) {
		this.beanClassLoader = (beanClassLoader != null ? beanClassLoader : ClassUtils.getDefaultClassLoader());
	}

	@Override
	@Nullable
	public ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	@Override
	public void setTempClassLoader(@Nullable ClassLoader tempClassLoader) {
		this.tempClassLoader = tempClassLoader;
	}

	@Override
	@Nullable
	public ClassLoader getTempClassLoader() {
		return this.tempClassLoader;
	}

	@Override
	public void setCacheBeanMetadata(boolean cacheBeanMetadata) {
		this.cacheBeanMetadata = cacheBeanMetadata;
	}

	@Override
	public boolean isCacheBeanMetadata() {
		return this.cacheBeanMetadata;
	}

	@Override
	public void setBeanExpressionResolver(@Nullable BeanExpressionResolver resolver) {
		this.beanExpressionResolver = resolver;
	}

	@Override
	@Nullable
	public BeanExpressionResolver getBeanExpressionResolver() {
		return this.beanExpressionResolver;
	}

	@Override
	public void setConversionService(@Nullable ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	@Override
	@Nullable
	public ConversionService getConversionService() {
		return this.conversionService;
	}

	@Override
	public void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar) {
		Assert.notNull(registrar, "PropertyEditorRegistrar must not be null");
		this.propertyEditorRegistrars.add(registrar);
	}

	public Set<PropertyEditorRegistrar> getPropertyEditorRegistrars() {
		return this.propertyEditorRegistrars;
	}

	@Override
	public void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass) {
		Assert.notNull(requiredType, "Required type must not be null");
		Assert.notNull(propertyEditorClass, "PropertyEditor class must not be null");
		this.customEditors.put(requiredType, propertyEditorClass);
	}

	@Override
	public void copyRegisteredEditorsTo(PropertyEditorRegistry registry) {
		registerCustomEditors(registry);
	}

	public Map<Class<?>, Class<? extends PropertyEditor>> getCustomEditors() {
		return this.customEditors;
	}

	@Override
	public void setTypeConverter(TypeConverter typeConverter) {
		this.typeConverter = typeConverter;
	}

	@Nullable
	protected TypeConverter getCustomTypeConverter() {
		return this.typeConverter;
	}

	@Override
	public TypeConverter getTypeConverter() {
		TypeConverter customConverter = getCustomTypeConverter();
		if (customConverter != null) {
			return customConverter;
		}
		else {
			SimpleTypeConverter typeConverter = new SimpleTypeConverter();
			typeConverter.setConversionService(getConversionService());
			registerCustomEditors(typeConverter);
			return typeConverter;
		}
	}

	@Override
	public void addEmbeddedValueResolver(StringValueResolver valueResolver) {
		Assert.notNull(valueResolver, "StringValueResolver must not be null");
		this.embeddedValueResolvers.add(valueResolver);
	}

	@Override
	public boolean hasEmbeddedValueResolver() {
		return !this.embeddedValueResolvers.isEmpty();
	}

	@Override
	@Nullable
	public String resolveEmbeddedValue(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String result = value;
		for (StringValueResolver resolver : this.embeddedValueResolvers) {
			result = resolver.resolveStringValue(result);
			if (result == null) {
				return null;
			}
		}
		return result;
	}

	@Override
	public void addBeanPostProcessor(BeanPostProcessor beanPostProcessor) {
		Assert.notNull(beanPostProcessor, "BeanPostProcessor must not be null");
		synchronized (this.beanPostProcessors) {
			this.beanPostProcessors.remove(beanPostProcessor);
			this.beanPostProcessors.add(beanPostProcessor);
		}
	}

	public void addBeanPostProcessors(Collection<? extends BeanPostProcessor> beanPostProcessors) {
		synchronized (this.beanPostProcessors) {
			this.beanPostProcessors.removeAll(beanPostProcessors);
			this.beanPostProcessors.addAll(beanPostProcessors);
		}
	}

	@Override
	public int getBeanPostProcessorCount() {
		return this.beanPostProcessors.size();
	}

	public List<BeanPostProcessor> getBeanPostProcessors() {
		return this.beanPostProcessors;
	}

	BeanPostProcessorCache getBeanPostProcessorCache() {
		synchronized (this.beanPostProcessors) {
			BeanPostProcessorCache bppCache = this.beanPostProcessorCache;
			if (bppCache == null) {
				bppCache = new BeanPostProcessorCache();
				for (BeanPostProcessor bpp : this.beanPostProcessors) {
					if (bpp instanceof InstantiationAwareBeanPostProcessor) {
						bppCache.instantiationAware.add((InstantiationAwareBeanPostProcessor) bpp);
						if (bpp instanceof SmartInstantiationAwareBeanPostProcessor) {
							bppCache.smartInstantiationAware.add((SmartInstantiationAwareBeanPostProcessor) bpp);
						}
					}
					if (bpp instanceof DestructionAwareBeanPostProcessor) {
						bppCache.destructionAware.add((DestructionAwareBeanPostProcessor) bpp);
					}
					if (bpp instanceof MergedBeanDefinitionPostProcessor) {
						bppCache.mergedDefinition.add((MergedBeanDefinitionPostProcessor) bpp);
					}
				}
				this.beanPostProcessorCache = bppCache;
			}
			return bppCache;
		}
	}

	private void resetBeanPostProcessorCache() {
		synchronized (this.beanPostProcessors) {
			this.beanPostProcessorCache = null;
		}
	}

	protected boolean hasInstantiationAwareBeanPostProcessors() {
		return !getBeanPostProcessorCache().instantiationAware.isEmpty();
	}

	protected boolean hasDestructionAwareBeanPostProcessors() {
		return !getBeanPostProcessorCache().destructionAware.isEmpty();
	}

	@Override
	public void registerScope(String scopeName, Scope scope) {
		Assert.notNull(scopeName, "Scope identifier must not be null");
		Assert.notNull(scope, "Scope must not be null");
		if (SCOPE_SINGLETON.equals(scopeName) || SCOPE_PROTOTYPE.equals(scopeName)) {
			throw new IllegalArgumentException("Cannot replace existing scopes 'singleton' and 'prototype'");
		}
		Scope previous = this.scopes.put(scopeName, scope);
		if (previous != null && previous != scope) {
			if (logger.isDebugEnabled()) {
				logger.debug("Replacing scope '" + scopeName + "' from [" + previous + "] to [" + scope + "]");
			}
		}
		else {
			if (logger.isTraceEnabled()) {
				logger.trace("Registering scope '" + scopeName + "' with implementation [" + scope + "]");
			}
		}
	}

	@Override
	public String[] getRegisteredScopeNames() {
		return StringUtils.toStringArray(this.scopes.keySet());
	}

	@Override
	@Nullable
	public Scope getRegisteredScope(String scopeName) {
		Assert.notNull(scopeName, "Scope identifier must not be null");
		return this.scopes.get(scopeName);
	}

	public void setSecurityContextProvider(SecurityContextProvider securityProvider) {
		this.securityContextProvider = securityProvider;
	}

	@Override
	public void setApplicationStartup(ApplicationStartup applicationStartup) {
		Assert.notNull(applicationStartup, "ApplicationStartup must not be null");
		this.applicationStartup = applicationStartup;
	}

	@Override
	public ApplicationStartup getApplicationStartup() {
		return this.applicationStartup;
	}

	@Override
	public AccessControlContext getAccessControlContext() {
		return (this.securityContextProvider != null ?
				this.securityContextProvider.getAccessControlContext() :
				AccessController.getContext());
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		Assert.notNull(otherFactory, "BeanFactory must not be null");
		setBeanClassLoader(otherFactory.getBeanClassLoader());
		setCacheBeanMetadata(otherFactory.isCacheBeanMetadata());
		setBeanExpressionResolver(otherFactory.getBeanExpressionResolver());
		setConversionService(otherFactory.getConversionService());
		if (otherFactory instanceof AbstractBeanFactory) {
			AbstractBeanFactory otherAbstractFactory = (AbstractBeanFactory) otherFactory;
			this.propertyEditorRegistrars.addAll(otherAbstractFactory.propertyEditorRegistrars);
			this.customEditors.putAll(otherAbstractFactory.customEditors);
			this.typeConverter = otherAbstractFactory.typeConverter;
			this.beanPostProcessors.addAll(otherAbstractFactory.beanPostProcessors);
			this.scopes.putAll(otherAbstractFactory.scopes);
			this.securityContextProvider = otherAbstractFactory.securityContextProvider;
		}
		else {
			setTypeConverter(otherFactory.getTypeConverter());
			String[] otherScopeNames = otherFactory.getRegisteredScopeNames();
			for (String scopeName : otherScopeNames) {
				this.scopes.put(scopeName, otherFactory.getRegisteredScope(scopeName));
			}
		}
	}

	@Override
	public BeanDefinition getMergedBeanDefinition(String name) throws BeansException {
		String beanName = transformedBeanName(name);
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory) {
			return ((ConfigurableBeanFactory) getParentBeanFactory()).getMergedBeanDefinition(beanName);
		}
		return getMergedLocalBeanDefinition(beanName);
	}

	@Override
	public boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null) {
			return (beanInstance instanceof FactoryBean);
		}
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory) {
			return ((ConfigurableBeanFactory) getParentBeanFactory()).isFactoryBean(name);
		}
		return isFactoryBean(beanName, getMergedLocalBeanDefinition(beanName));
	}

	@Override
	public boolean isActuallyInCreation(String beanName) {
		return (isSingletonCurrentlyInCreation(beanName) || isPrototypeCurrentlyInCreation(beanName));
	}

	protected boolean isPrototypeCurrentlyInCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		return (curVal != null &&
				(curVal.equals(beanName) || (curVal instanceof Set && ((Set<?>) curVal).contains(beanName))));
	}

	@SuppressWarnings("unchecked")
	protected void beforePrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal == null) {
			this.prototypesCurrentlyInCreation.set(beanName);
		}
		else if (curVal instanceof String) {
			Set<String> beanNameSet = new HashSet<>(2);
			beanNameSet.add((String) curVal);
			beanNameSet.add(beanName);
			this.prototypesCurrentlyInCreation.set(beanNameSet);
		}
		else {
			Set<String> beanNameSet = (Set<String>) curVal;
			beanNameSet.add(beanName);
		}
	}

	@SuppressWarnings("unchecked")
	protected void afterPrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal instanceof String) {
			this.prototypesCurrentlyInCreation.remove();
		}
		else if (curVal instanceof Set) {
			Set<String> beanNameSet = (Set<String>) curVal;
			beanNameSet.remove(beanName);
			if (beanNameSet.isEmpty()) {
				this.prototypesCurrentlyInCreation.remove();
			}
		}
	}

	@Override
	public void destroyBean(String beanName, Object beanInstance) {
		destroyBean(beanName, beanInstance, getMergedLocalBeanDefinition(beanName));
	}

	protected void destroyBean(String beanName, Object bean, RootBeanDefinition mbd) {
		new DisposableBeanAdapter(
				bean, beanName, mbd, getBeanPostProcessorCache().destructionAware, getAccessControlContext()).destroy();
	}

	@Override
	public void destroyScopedBean(String beanName) {
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isSingleton() || mbd.isPrototype()) {
			throw new IllegalArgumentException(
					"Bean name '" + beanName + "' does not correspond to an object in a mutable scope");
		}
		String scopeName = mbd.getScope();
		Scope scope = this.scopes.get(scopeName);
		if (scope == null) {
			throw new IllegalStateException("No Scope SPI registered for scope name '" + scopeName + "'");
		}
		Object bean = scope.remove(beanName);
		if (bean != null) {
			destroyBean(beanName, bean, mbd);
		}
	}


	protected String transformedBeanName(String name) {
		return canonicalName(BeanFactoryUtils.transformedBeanName(name));
	}

	protected String originalBeanName(String name) {
		String beanName = transformedBeanName(name);
		if (name.startsWith(FACTORY_BEAN_PREFIX)) {
			beanName = FACTORY_BEAN_PREFIX + beanName;
		}
		return beanName;
	}

	protected void initBeanWrapper(BeanWrapper bw) {
		bw.setConversionService(getConversionService());
		registerCustomEditors(bw);
	}

	protected void registerCustomEditors(PropertyEditorRegistry registry) {
		if (registry instanceof PropertyEditorRegistrySupport) {
			((PropertyEditorRegistrySupport) registry).useConfigValueEditors();
		}
		if (!this.propertyEditorRegistrars.isEmpty()) {
			for (PropertyEditorRegistrar registrar : this.propertyEditorRegistrars) {
				try {
					registrar.registerCustomEditors(registry);
				}
				catch (BeanCreationException ex) {
					Throwable rootCause = ex.getMostSpecificCause();
					if (rootCause instanceof BeanCurrentlyInCreationException) {
						BeanCreationException bce = (BeanCreationException) rootCause;
						String bceBeanName = bce.getBeanName();
						if (bceBeanName != null && isCurrentlyInCreation(bceBeanName)) {
							if (logger.isDebugEnabled()) {
								logger.debug("PropertyEditorRegistrar [" + registrar.getClass().getName() +
										"] failed because it tried to obtain currently created bean '" +
										ex.getBeanName() + "': " + ex.getMessage());
							}
							onSuppressedException(ex);
							continue;
						}
					}
					throw ex;
				}
			}
		}
		if (!this.customEditors.isEmpty()) {
			this.customEditors.forEach((requiredType, editorClass) ->
					registry.registerCustomEditor(requiredType, BeanUtils.instantiateClass(editorClass)));
		}
	}


	protected RootBeanDefinition getMergedLocalBeanDefinition(String beanName) throws BeansException {
		RootBeanDefinition mbd = this.mergedBeanDefinitions.get(beanName);
		if (mbd != null && !mbd.stale) {
			return mbd;
		}
		return getMergedBeanDefinition(beanName, getBeanDefinition(beanName));
	}

	protected RootBeanDefinition getMergedBeanDefinition(String beanName, BeanDefinition bd)
			throws BeanDefinitionStoreException {

		return getMergedBeanDefinition(beanName, bd, null);
	}

	/**
	 * 获取给定 Bean 的合并后 RootBeanDefinition。
	 *
	 * 合并的含义是：如果该 BeanDefinition 有 parentName，则需要递归向父级合并属性，得到一个完整的定义。
	 * 如果是嵌套 Bean（即包含在另一个 Bean 中），也需要根据外层 Bean 的作用域做适配处理。
	 *
	 * @param beanName 当前要处理的 Bean 的名称
	 * @param bd 原始 BeanDefinition（可能是 GenericBeanDefinition）
	 * @param containingBd 如果当前 Bean 是嵌套的子元素，则为其外层 Bean 的定义；否则为 null
	 * @return 合并后的 RootBeanDefinition
	 */
	protected RootBeanDefinition getMergedBeanDefinition(
			String beanName, BeanDefinition bd, @Nullable BeanDefinition containingBd)
			throws BeanDefinitionStoreException {

		// 对 mergedBeanDefinitions 缓存加锁，防止并发修改
		synchronized (this.mergedBeanDefinitions) {
			RootBeanDefinition mbd = null;      // 最终返回的合并 BeanDefinition
			RootBeanDefinition previous = null; // 如果需要覆盖缓存，则保存旧的 mbd 用于复制缓存

			// 如果不是嵌套 Bean，则尝试从缓存中获取已合并的定义
			if (containingBd == null) {
				mbd = this.mergedBeanDefinitions.get(beanName);
			}

			// 如果缓存中没有，或者之前缓存的已失效（比如注册新定义后）
			if (mbd == null || mbd.stale) {
				previous = mbd;

				// ① 没有父 Bean（普通 BeanDefinition）
				if (bd.getParentName() == null) {
					if (bd instanceof RootBeanDefinition) {
						// 如果本身就是 RootBeanDefinition，则直接克隆
						mbd = ((RootBeanDefinition) bd).cloneBeanDefinition();
					} else {
						// 否则新建 RootBeanDefinition，并复制属性
						mbd = new RootBeanDefinition(bd);
					}
				} else {
					// ② 有父 Bean（继承其他 BeanDefinition）

					BeanDefinition pbd;
					try {
						String parentBeanName = transformedBeanName(bd.getParentName());

						// 如果父 Bean 名称和当前相同，说明可能是父容器的 Bean
						if (!beanName.equals(parentBeanName)) {
							// 递归获取父 Bean 的合并定义
							pbd = getMergedBeanDefinition(parentBeanName);
						} else {
							// 向父容器请求（注意必须是 ConfigurableBeanFactory 才能支持 getMergedBeanDefinition）
							BeanFactory parent = getParentBeanFactory();
							if (parent instanceof ConfigurableBeanFactory) {
								pbd = ((ConfigurableBeanFactory) parent).getMergedBeanDefinition(parentBeanName);
							} else {
								// 父容器不支持，抛出异常
								throw new NoSuchBeanDefinitionException(parentBeanName,
										"Parent name '" + parentBeanName + "' is equal to bean name '" + beanName +
												"': cannot be resolved without a ConfigurableBeanFactory parent");
							}
						}
					} catch (NoSuchBeanDefinitionException ex) {
						// 如果找不到父定义，抛出异常
						throw new BeanDefinitionStoreException(bd.getResourceDescription(), beanName,
								"Could not resolve parent bean definition '" + bd.getParentName() + "'", ex);
					}

					// 以父定义为基础创建一个 RootBeanDefinition
					mbd = new RootBeanDefinition(pbd);
					// 使用当前定义覆盖父定义中对应的字段
					mbd.overrideFrom(bd);
				}

				// 如果没有指定 scope，默认为 singleton
				if (!StringUtils.hasLength(mbd.getScope())) {
					mbd.setScope(SCOPE_SINGLETON);
				}

				// 如果是嵌套 Bean（如内部类 Bean），需要继承外层作用域（比如 outer 是 prototype，inner 也必须是 prototype）
				if (containingBd != null && !containingBd.isSingleton() && mbd.isSingleton()) {
					mbd.setScope(containingBd.getScope());
				}

				// 如果当前是根 Bean 且允许缓存合并结果，则存入缓存
				if (containingBd == null && isCacheBeanMetadata()) {
					this.mergedBeanDefinitions.put(beanName, mbd);
				}
			}

			// 如果之前已有缓存，拷贝一些不属于 BeanDefinition 本身的缓存信息（比如解析好的注解元数据）
			if (previous != null) {
				copyRelevantMergedBeanDefinitionCaches(previous, mbd);
			}

			// 返回最终的合并定义
			return mbd;
		}
	}


	private void copyRelevantMergedBeanDefinitionCaches(RootBeanDefinition previous, RootBeanDefinition mbd) {
		if (ObjectUtils.nullSafeEquals(mbd.getBeanClassName(), previous.getBeanClassName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryBeanName(), previous.getFactoryBeanName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryMethodName(), previous.getFactoryMethodName())) {
			ResolvableType targetType = mbd.targetType;
			ResolvableType previousTargetType = previous.targetType;
			if (targetType == null || targetType.equals(previousTargetType)) {
				mbd.targetType = previousTargetType;
				mbd.isFactoryBean = previous.isFactoryBean;
				mbd.resolvedTargetType = previous.resolvedTargetType;
				mbd.factoryMethodReturnType = previous.factoryMethodReturnType;
				mbd.factoryMethodToIntrospect = previous.factoryMethodToIntrospect;
			}
		}
	}

	protected void checkMergedBeanDefinition(RootBeanDefinition mbd, String beanName, @Nullable Object[] args)
			throws BeanDefinitionStoreException {

		if (mbd.isAbstract()) {
			throw new BeanIsAbstractException(beanName);
		}
	}

	protected void clearMergedBeanDefinition(String beanName) {
		RootBeanDefinition bd = this.mergedBeanDefinitions.get(beanName);
		if (bd != null) {
			bd.stale = true;
		}
	}

	public void clearMetadataCache() {
		this.mergedBeanDefinitions.forEach((beanName, bd) -> {
			if (!isBeanEligibleForMetadataCaching(beanName)) {
				bd.stale = true;
			}
		});
	}

	@Nullable
	protected Class<?> resolveBeanClass(RootBeanDefinition mbd, String beanName, Class<?>... typesToMatch)
			throws CannotLoadBeanClassException {

		try {
			if (mbd.hasBeanClass()) {
				return mbd.getBeanClass();
			}
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged((PrivilegedExceptionAction<Class<?>>)
						() -> doResolveBeanClass(mbd, typesToMatch), getAccessControlContext());
			}
			else {
				return doResolveBeanClass(mbd, typesToMatch);
			}
		}
		catch (PrivilegedActionException pae) {
			ClassNotFoundException ex = (ClassNotFoundException) pae.getException();
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
		}
		catch (ClassNotFoundException ex) {
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
		}
		catch (LinkageError err) {
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), err);
		}
	}

	/**
	 * 解析给定的 RootBeanDefinition 中的 className，返回对应的 Class 对象。
	 * 支持类型匹配时的临时类加载器（例如 AOP、泛型匹配等场景），并支持 SPEL 表达式动态 className。
	 *
	 * @param mbd BeanDefinition（一般是 RootBeanDefinition）
	 * @param typesToMatch 类型匹配的目标 Class，用于临时类加载器排除（可选）
	 * @return 返回解析后的 Class（可为 null）
	 * @throws ClassNotFoundException 如果类找不到
	 */
	@Nullable
	private Class<?> doResolveBeanClass(RootBeanDefinition mbd, Class<?>... typesToMatch)
			throws ClassNotFoundException {

		// 获取 Spring 配置的默认类加载器
		ClassLoader beanClassLoader = getBeanClassLoader();
		ClassLoader dynamicLoader = beanClassLoader; // 实际用来加载类的类加载器（可能是临时的）
		boolean freshResolve = false; // 是否需要强制重新解析 className

		// 如果传入了类型匹配需求（比如正在进行泛型匹配或条件匹配）
		if (!ObjectUtils.isEmpty(typesToMatch)) {
			// 尝试获取临时类加载器（Spring 可配置的 temp loader）
			ClassLoader tempClassLoader = getTempClassLoader();
			if (tempClassLoader != null) {
				dynamicLoader = tempClassLoader;
				freshResolve = true; // 强制使用新的 loader 重新加载类

				// 如果是装饰类加载器（可排除指定类），就排除匹配类，避免冲突
				if (tempClassLoader instanceof DecoratingClassLoader) {
					DecoratingClassLoader dcl = (DecoratingClassLoader) tempClassLoader;
					for (Class<?> typeToMatch : typesToMatch) {
						dcl.excludeClass(typeToMatch.getName()); // 排除匹配的类，防止冲突加载
					}
				}
			}
		}

		// 从 BeanDefinition 中获取类名（可能是字符串）
		String className = mbd.getBeanClassName();

		if (className != null) {
			// 允许 className 是 SPEL 表达式，因此需要解析该表达式
			Object evaluated = evaluateBeanDefinitionString(className, mbd);

			// 如果表达式结果发生了变化（即 className 是一个动态计算值）
			if (!className.equals(evaluated)) {
				if (evaluated instanceof Class) {
					// 如果结果已经是 Class 对象，直接返回
					return (Class<?>) evaluated;
				}
				else if (evaluated instanceof String) {
					// 如果是字符串，更新为新的 className，并标记为需要重新加载
					className = (String) evaluated;
					freshResolve = true;
				}
				else {
					// 其他类型不合法
					throw new IllegalStateException("Invalid class name expression result: " + evaluated);
				}
			}

			// 如果是强制重新解析 className，则使用 dynamicLoader 加载该类
			if (freshResolve) {
				if (dynamicLoader != null) {
					try {
						// 尝试使用类加载器加载 className
						return dynamicLoader.loadClass(className);
					}
					catch (ClassNotFoundException ex) {
						// 如果无法加载，记录日志，但继续尝试备用方式
						if (logger.isTraceEnabled()) {
							logger.trace("Could not load class [" + className + "] from " + dynamicLoader + ": " + ex);
						}
					}
				}
				// 最终使用 Spring 封装的 ClassUtils 加载
				return ClassUtils.forName(className, dynamicLoader);
			}
		}

		// 如果没有动态加载需求，使用 BeanDefinition 提供的方法缓存加载
		return mbd.resolveBeanClass(beanClassLoader);
	}


	@Nullable
	protected Object evaluateBeanDefinitionString(@Nullable String value, @Nullable BeanDefinition beanDefinition) {
		if (this.beanExpressionResolver == null) {
			return value;
		}

		Scope scope = null;
		if (beanDefinition != null) {
			String scopeName = beanDefinition.getScope();
			if (scopeName != null) {
				scope = getRegisteredScope(scopeName);
			}
		}
		return this.beanExpressionResolver.evaluate(value, new BeanExpressionContext(this, scope));
	}


	@Nullable
	protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = mbd.getTargetType();
		if (targetType != null) {
			return targetType;
		}
		if (mbd.getFactoryMethodName() != null) {
			return null;
		}
		return resolveBeanClass(mbd, beanName, typesToMatch);
	}

	protected boolean isFactoryBean(String beanName, RootBeanDefinition mbd) {
		Boolean result = mbd.isFactoryBean;
		if (result == null) {
			Class<?> beanType = predictBeanType(beanName, mbd, FactoryBean.class);
			result = (beanType != null && FactoryBean.class.isAssignableFrom(beanType));
			mbd.isFactoryBean = result;
		}
		return result;
	}

	protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
		ResolvableType result = getTypeForFactoryBeanFromAttributes(mbd);
		if (result != ResolvableType.NONE) {
			return result;
		}

		if (allowInit && mbd.isSingleton()) {
			try {
				FactoryBean<?> factoryBean = doGetBean(FACTORY_BEAN_PREFIX + beanName, FactoryBean.class, null, true);
				Class<?> objectType = getTypeForFactoryBean(factoryBean);
				return (objectType != null ? ResolvableType.forClass(objectType) : ResolvableType.NONE);
			}
			catch (BeanCreationException ex) {
				if (ex.contains(BeanCurrentlyInCreationException.class)) {
					logger.trace(LogMessage.format("Bean currently in creation on FactoryBean type check: %s", ex));
				}
				else if (mbd.isLazyInit()) {
					logger.trace(LogMessage.format("Bean creation exception on lazy FactoryBean type check: %s", ex));
				}
				else {
					logger.debug(LogMessage.format("Bean creation exception on eager FactoryBean type check: %s", ex));
				}
				onSuppressedException(ex);
			}
		}
		return ResolvableType.NONE;
	}

	ResolvableType getTypeForFactoryBeanFromAttributes(AttributeAccessor attributes) {
		Object attribute = attributes.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
		if (attribute instanceof ResolvableType) {
			return (ResolvableType) attribute;
		}
		if (attribute instanceof Class) {
			return ResolvableType.forClass((Class<?>) attribute);
		}
		return ResolvableType.NONE;
	}

	@Nullable
	@Deprecated
	protected Class<?> getTypeForFactoryBean(String beanName, RootBeanDefinition mbd) {
		return getTypeForFactoryBean(beanName, mbd, true).resolve();
	}

	protected void markBeanAsCreated(String beanName) {
		if (!this.alreadyCreated.contains(beanName)) {
			synchronized (this.mergedBeanDefinitions) {
				if (!this.alreadyCreated.contains(beanName)) {
					clearMergedBeanDefinition(beanName);
					this.alreadyCreated.add(beanName);
				}
			}
		}
	}

	protected void cleanupAfterBeanCreationFailure(String beanName) {
		synchronized (this.mergedBeanDefinitions) {
			this.alreadyCreated.remove(beanName);
		}
	}

	protected boolean isBeanEligibleForMetadataCaching(String beanName) {
		return this.alreadyCreated.contains(beanName);
	}

	protected boolean removeSingletonIfCreatedForTypeCheckOnly(String beanName) {
		if (!this.alreadyCreated.contains(beanName)) {
			removeSingleton(beanName);
			return true;
		}
		else {
			return false;
		}
	}

	protected boolean hasBeanCreationStarted() {
		return !this.alreadyCreated.isEmpty();
	}

	protected Object getObjectForBeanInstance(
			Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

		if (BeanFactoryUtils.isFactoryDereference(name)) {
			if (beanInstance instanceof NullBean) {
				return beanInstance;
			}
			if (!(beanInstance instanceof FactoryBean)) {
				throw new BeanIsNotAFactoryException(beanName, beanInstance.getClass());
			}
			if (mbd != null) {
				mbd.isFactoryBean = true;
			}
			return beanInstance;
		}

		if (!(beanInstance instanceof FactoryBean)) {
			return beanInstance;
		}

		Object object = null;
		if (mbd != null) {
			mbd.isFactoryBean = true;
		}
		else {
			object = getCachedObjectForFactoryBean(beanName);
		}
		if (object == null) {
			FactoryBean<?> factory = (FactoryBean<?>) beanInstance;
			if (mbd == null && containsBeanDefinition(beanName)) {
				mbd = getMergedLocalBeanDefinition(beanName);
			}
			boolean synthetic = (mbd != null && mbd.isSynthetic());
			object = getObjectFromFactoryBean(factory, beanName, !synthetic);
		}
		return object;
	}

	public boolean isBeanNameInUse(String beanName) {
		return isAlias(beanName) || containsLocalBean(beanName) || hasDependentBean(beanName);
	}

	protected boolean requiresDestruction(Object bean, RootBeanDefinition mbd) {
		return (bean.getClass() != NullBean.class && (DisposableBeanAdapter.hasDestroyMethod(bean, mbd) ||
				(hasDestructionAwareBeanPostProcessors() && DisposableBeanAdapter.hasApplicableProcessors(
						bean, getBeanPostProcessorCache().destructionAware))));
	}

	protected void registerDisposableBeanIfNecessary(String beanName, Object bean, RootBeanDefinition mbd) {
		AccessControlContext acc = (System.getSecurityManager() != null ? getAccessControlContext() : null);
		if (!mbd.isPrototype() && requiresDestruction(bean, mbd)) {
			if (mbd.isSingleton()) {
				registerDisposableBean(beanName, new DisposableBeanAdapter(
						bean, beanName, mbd, getBeanPostProcessorCache().destructionAware, acc));
			}
			else {
				Scope scope = this.scopes.get(mbd.getScope());
				if (scope == null) {
					throw new IllegalStateException("No Scope registered for scope name '" + mbd.getScope() + "'");
				}
				scope.registerDestructionCallback(beanName, new DisposableBeanAdapter(
						bean, beanName, mbd, getBeanPostProcessorCache().destructionAware, acc));
			}
		}
	}


	protected abstract boolean containsBeanDefinition(String beanName);

	protected abstract BeanDefinition getBeanDefinition(String beanName) throws BeansException;

	protected abstract Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException;


	private class BeanPostProcessorCacheAwareList extends CopyOnWriteArrayList<BeanPostProcessor> {

		@Override
		public BeanPostProcessor set(int index, BeanPostProcessor element) {
			BeanPostProcessor result = super.set(index, element);
			resetBeanPostProcessorCache();
			return result;
		}

		@Override
		public boolean add(BeanPostProcessor o) {
			boolean success = super.add(o);
			resetBeanPostProcessorCache();
			return success;
		}

		@Override
		public void add(int index, BeanPostProcessor element) {
			super.add(index, element);
			resetBeanPostProcessorCache();
		}

		@Override
		public BeanPostProcessor remove(int index) {
			BeanPostProcessor result = super.remove(index);
			resetBeanPostProcessorCache();
			return result;
		}

		@Override
		public boolean remove(Object o) {
			boolean success = super.remove(o);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			boolean success = super.removeAll(c);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			boolean success = super.retainAll(c);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public boolean addAll(Collection<? extends BeanPostProcessor> c) {
			boolean success = super.addAll(c);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public boolean addAll(int index, Collection<? extends BeanPostProcessor> c) {
			boolean success = super.addAll(index, c);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public boolean removeIf(Predicate<? super BeanPostProcessor> filter) {
			boolean success = super.removeIf(filter);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public void replaceAll(UnaryOperator<BeanPostProcessor> operator) {
			super.replaceAll(operator);
			resetBeanPostProcessorCache();
		}
	}


	static class BeanPostProcessorCache {

		final List<InstantiationAwareBeanPostProcessor> instantiationAware = new ArrayList<>();

		final List<SmartInstantiationAwareBeanPostProcessor> smartInstantiationAware = new ArrayList<>();

		final List<DestructionAwareBeanPostProcessor> destructionAware = new ArrayList<>();

		final List<MergedBeanDefinitionPostProcessor> mergedDefinition = new ArrayList<>();
	}

}
