package org.springframework.beans.factory.support;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessorUtils;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.AutowiredPropertyMarker;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.NativeDetector;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodCallback;
import org.springframework.util.StringUtils;

/**
 * AbstractAutowireCapableBeanFactory 是 Spring 中最核心、最复杂的 Bean 工厂实现类之一。
 * <p>
 * 【类作用】
 * 提供了完整的“创建 Bean 实例”的能力，支持自动装配（Autowire）、依赖注入（DI）、
 * Bean 初始化、AOP 代理、生命周期回调、属性填充等整个 Bean 生命周期的处理流程。
 * <p>
 * 它继承自 {@link AbstractBeanFactory}，实现了 Spring Bean 的创建核心逻辑，是默认 Bean 创建的核心实现。
 * 其子类 {@link DefaultListableBeanFactory} 是实际应用中最常用的 Bean 工厂。
 * <p>
 * 【主要职责】
 * 1. 通过反射或构造函数实例化 Bean（resolveConstructorArguments、instantiateBean 等）；
 * 2. 执行属性注入（populateBean）；
 * 3. 执行初始化方法（invokeInitMethods、applyBeanPostProcessors...）；
 * 4. 支持 Autowire byType/byName；
 * 5. 应用 BeanPostProcessor（AOP 的入口点）；
 * 6. 管理依赖关系、循环依赖（三级缓存）；
 * <p>
 * 【调用主线】
 * 1. `getBean()` ➝ `doGetBean()` ➝ `createBean()` ➝ `doCreateBean()` ：创建完整的 Bean。
 * 2. `doCreateBean()` 包括三步：实例化 ➝ 属性填充 ➝ 初始化。
 * <p>
 * 【生命周期方法调用顺序】
 * - 实例化构造方法（构造器注入）
 * - populateBean：字段注入、set方法注入（@Autowired、@Value）
 * - applyBeanPostProcessorsBeforeInitialization
 * - invokeAwareMethods（注入 BeanNameAware、BeanFactoryAware、ApplicationContextAware）
 * - invokeInitMethods（调用 afterPropertiesSet 或自定义 init-method）
 * - applyBeanPostProcessorsAfterInitialization
 * <p>
 * 【设计模式】
 * - 模板方法模式：子类可扩展创建流程的各个阶段（如实例化策略）；
 * - 策略模式：通过 InstantiationStrategy 定义实例化方式（构造函数 or 反射）；
 * - 装饰器模式：通过 BeanPostProcessor 扩展 Bean 的初始化逻辑；
 * <p>
 * 【核心属性】
 * - InstantiationStrategy：实例化策略（默认 CglibSubclassingInstantiationStrategy）；
 * - BeanPostProcessors：用于处理 AOP、@Autowired 等扩展；
 * - ignoreDependencyType、ignoreDependencyInterface：控制哪些类型不注入；
 * <p>
 * 【典型子类】
 * - DefaultListableBeanFactory：Spring 默认的可注册/获取 Bean 的标准容器；
 */
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
		implements AutowireCapableBeanFactory {

	private InstantiationStrategy instantiationStrategy;

	@Nullable
	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	private boolean allowCircularReferences = true;

	private boolean allowRawInjectionDespiteWrapping = false;

	private final Set<Class<?>> ignoredDependencyTypes = new HashSet<>();

	private final Set<Class<?>> ignoredDependencyInterfaces = new HashSet<>();

	private final NamedThreadLocal<String> currentlyCreatedBean = new NamedThreadLocal<>("Currently created bean");

	private final ConcurrentMap<String, BeanWrapper> factoryBeanInstanceCache = new ConcurrentHashMap<>();

	private final ConcurrentMap<Class<?>, Method[]> factoryMethodCandidateCache = new ConcurrentHashMap<>();

	private final ConcurrentMap<Class<?>, PropertyDescriptor[]> filteredPropertyDescriptorsCache =
			new ConcurrentHashMap<>();


	public AbstractAutowireCapableBeanFactory() {
		super();
		ignoreDependencyInterface(BeanNameAware.class);
		ignoreDependencyInterface(BeanFactoryAware.class);
		ignoreDependencyInterface(BeanClassLoaderAware.class);
		if (NativeDetector.inNativeImage()) {
			this.instantiationStrategy = new SimpleInstantiationStrategy();
		} else {
			this.instantiationStrategy = new CglibSubclassingInstantiationStrategy();
		}
	}

	public AbstractAutowireCapableBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		this();
		setParentBeanFactory(parentBeanFactory);
	}


	public void setInstantiationStrategy(InstantiationStrategy instantiationStrategy) {
		this.instantiationStrategy = instantiationStrategy;
	}

	protected InstantiationStrategy getInstantiationStrategy() {
		return this.instantiationStrategy;
	}

	public void setParameterNameDiscoverer(@Nullable ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	@Nullable
	protected ParameterNameDiscoverer getParameterNameDiscoverer() {
		return this.parameterNameDiscoverer;
	}

	public void setAllowCircularReferences(boolean allowCircularReferences) {
		this.allowCircularReferences = allowCircularReferences;
	}

	public boolean isAllowCircularReferences() {
		return this.allowCircularReferences;
	}

	public void setAllowRawInjectionDespiteWrapping(boolean allowRawInjectionDespiteWrapping) {
		this.allowRawInjectionDespiteWrapping = allowRawInjectionDespiteWrapping;
	}

	public boolean isAllowRawInjectionDespiteWrapping() {
		return this.allowRawInjectionDespiteWrapping;
	}

	public void ignoreDependencyType(Class<?> type) {
		this.ignoredDependencyTypes.add(type);
	}

	public void ignoreDependencyInterface(Class<?> ifc) {
		this.ignoredDependencyInterfaces.add(ifc);
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		super.copyConfigurationFrom(otherFactory);
		if (otherFactory instanceof AbstractAutowireCapableBeanFactory) {
			AbstractAutowireCapableBeanFactory otherAutowireFactory =
					(AbstractAutowireCapableBeanFactory) otherFactory;
			this.instantiationStrategy = otherAutowireFactory.instantiationStrategy;
			this.allowCircularReferences = otherAutowireFactory.allowCircularReferences;
			this.ignoredDependencyTypes.addAll(otherAutowireFactory.ignoredDependencyTypes);
			this.ignoredDependencyInterfaces.addAll(otherAutowireFactory.ignoredDependencyInterfaces);
		}
	}


	@Override
	@SuppressWarnings("unchecked")
	public <T> T createBean(Class<T> beanClass) throws BeansException {
		RootBeanDefinition bd = new RootBeanDefinition(beanClass);
		bd.setScope(SCOPE_PROTOTYPE);
		bd.allowCaching = ClassUtils.isCacheSafe(beanClass, getBeanClassLoader());
		return (T) createBean(beanClass.getName(), bd, null);
	}

	@Override
	public void autowireBean(Object existingBean) {
		RootBeanDefinition bd = new RootBeanDefinition(ClassUtils.getUserClass(existingBean));
		bd.setScope(SCOPE_PROTOTYPE);
		bd.allowCaching = ClassUtils.isCacheSafe(bd.getBeanClass(), getBeanClassLoader());
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(bd.getBeanClass().getName(), bd, bw);
	}

	@Override
	public Object configureBean(Object existingBean, String beanName) throws BeansException {
		markBeanAsCreated(beanName);
		BeanDefinition mbd = getMergedBeanDefinition(beanName);
		RootBeanDefinition bd = null;
		if (mbd instanceof RootBeanDefinition) {
			RootBeanDefinition rbd = (RootBeanDefinition) mbd;
			bd = (rbd.isPrototype() ? rbd : rbd.cloneBeanDefinition());
		}
		if (bd == null) {
			bd = new RootBeanDefinition(mbd);
		}
		if (!bd.isPrototype()) {
			bd.setScope(SCOPE_PROTOTYPE);
			bd.allowCaching = ClassUtils.isCacheSafe(ClassUtils.getUserClass(existingBean), getBeanClassLoader());
		}
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(beanName, bd, bw);
		return initializeBean(beanName, existingBean, bd);
	}


	@Override
	public Object createBean(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
		RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		return createBean(beanClass.getName(), bd, null);
	}

	@Override
	public Object autowire(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
		RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		if (bd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR) {
			return autowireConstructor(beanClass.getName(), bd, null, null).getWrappedInstance();
		} else {
			Object bean;
			if (System.getSecurityManager() != null) {
				bean = AccessController.doPrivileged(
						(PrivilegedAction<Object>) () -> getInstantiationStrategy().instantiate(bd, null, this),
						getAccessControlContext());
			} else {
				bean = getInstantiationStrategy().instantiate(bd, null, this);
			}
			populateBean(beanClass.getName(), bd, new BeanWrapperImpl(bean));
			return bean;
		}
	}

	@Override
	public void autowireBeanProperties(Object existingBean, int autowireMode, boolean dependencyCheck)
			throws BeansException {

		if (autowireMode == AUTOWIRE_CONSTRUCTOR) {
			throw new IllegalArgumentException("AUTOWIRE_CONSTRUCTOR not supported for existing bean instance");
		}
		RootBeanDefinition bd =
				new RootBeanDefinition(ClassUtils.getUserClass(existingBean), autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(bd.getBeanClass().getName(), bd, bw);
	}

	@Override
	public void applyBeanPropertyValues(Object existingBean, String beanName) throws BeansException {
		markBeanAsCreated(beanName);
		BeanDefinition bd = getMergedBeanDefinition(beanName);
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		applyPropertyValues(beanName, bd, bw, bd.getPropertyValues());
	}

	@Override
	public Object initializeBean(Object existingBean, String beanName) {
		return initializeBean(beanName, existingBean, null);
	}

	/**
	 * 应用所有注册的 BeanPostProcessor 的 postProcessBeforeInitialization 方法，
	 * 对指定的 bean 在其初始化方法（如 afterPropertiesSet 或 init-method）调用之前进行处理。
	 *
	 * @param existingBean 已经创建并填充好属性的原始 bean 实例
	 * @param beanName     当前 bean 在容器中的名称
	 * @return 经过所有 BeanPostProcessor 处理后的 bean 实例
	 * @throws BeansException 如果后处理器在处理过程中抛出异常
	 */
	@Override
	public Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName)
			throws BeansException {

		// 初始结果为原始 bean
		Object result = existingBean;

		// 遍历容器为所创建的Bean 添加的所有 BeanPostProcessor 后置处理器
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			// 调用 Bean 实例所有的后置处理中的初始化后处理方法,为Bean 实例对象在初始化之后做一些自定义的处理操作
			Object current = processor.postProcessBeforeInitialization(result, beanName);

			// 如果当前处理器返回 null，表示跳过后续处理器，直接返回当前处理结果
			// 注意：这里不会直接返回 null，而是返回上一个非 null 的 result
			if (current == null) {
				return result;
			}

			result = current;
		}

		return result;
	}


	@Override
	public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
			throws BeansException {

		Object result = existingBean;
		// 遍历容器为所创建的 Bean 添加的所有 BeanPostProcessor 后置处理器
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			// 调用 Bean 实例所有的后置处理中的初始化后处理方法,为Bean 实例对象在初始化之后做一些自定义的处理操作
			Object current = processor.postProcessAfterInitialization(result, beanName);
			if (current == null) {
				return result;
			}
			result = current;
		}
		return result;
	}

	@Override
	public void destroyBean(Object existingBean) {
		new DisposableBeanAdapter(
				existingBean, getBeanPostProcessorCache().destructionAware, getAccessControlContext()).destroy();
	}


	@Override
	public Object resolveBeanByName(String name, DependencyDescriptor descriptor) {
		InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
		try {
			return getBean(name, descriptor.getDependencyType());
		} finally {
			ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
		}
	}

	@Override
	@Nullable
	public Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName) throws BeansException {
		return resolveDependency(descriptor, requestingBeanName, null, null);
	}


	/**
	 * 创建指定名称的 Bean 实例，这是 Bean 创建的总入口方法之一。
	 * <p>
	 * 【方法职责】
	 * - 根据 BeanDefinition 创建 Bean 的完整生命周期，包括实例化、属性填充、初始化等；
	 * - 支持 BeanPostProcessor 的 pre-instantiation 处理（例如 AOP 代理）；
	 *
	 * @param beanName Bean 名称
	 * @param mbd      Bean 定义（RootBeanDefinition 类型）
	 * @param args     构造函数参数（用于构造函数注入）
	 * @return 创建好的 Bean 实例
	 * @throws BeanCreationException 如果创建失败则抛出异常
	 */
	@Override
	protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {

		if (logger.isTraceEnabled()) {
			logger.trace("Creating instance of bean '" + beanName + "'");
		}

		// ----------------- 1. 准备 BeanDefinition -----------------
		RootBeanDefinition mbdToUse = mbd;

		// 判断需要创建的Bean 是否可以实例化,即是否可以通过当前的类加载器加载
		Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
		if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
			// 克隆一份 BeanDefinition 并设置 resolved 的真实类（防止污染原始定义）
			mbdToUse = new RootBeanDefinition(mbd);
			mbdToUse.setBeanClass(resolvedClass);
		}

		// ----------------- 2. 处理 method overrides -----------------
		try {
			// 校验和准备 method overrides（用于 method-lookup、replace-method）
			mbdToUse.prepareMethodOverrides();
		} catch (BeanDefinitionValidationException ex) {
			throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
					beanName, "Validation of method overrides failed", ex);
		}

		// ----------------- 3. InstantiationAwareBeanPostProcessor 扩展点 并非 BeanPostProcessor -----------------
		try {
			// 尝试让 InstantiationAwareBeanPostProcessor 提前返回一个代理对象（如 @Configuration CGLIB）
			// 如果 Bean 配置了初始化前和初始化后的处理器,则试图返回一个需要创建 Bean的代理对象
			Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
			if (bean != null) {
				return bean;  // 如果处理器已创建了代理对象，则直接返回（跳过常规流程）
			}
		} catch (Throwable ex) {
			throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
					"BeanPostProcessor before instantiation of bean failed", ex);
		}

		// ----------------- 4. 核心创建流程 -----------------
		try {
			// 【核心方法】：执行真正的创建流程，包括实例化、填充、初始化等
			Object beanInstance = doCreateBean(beanName, mbdToUse, args);
			if (logger.isTraceEnabled()) {
				logger.trace("Finished creating instance of bean '" + beanName + "'");
			}

			// ----------------- 5. 返回最终 Bean 实例 -----------------
			return beanInstance;
		} catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
			throw ex;
		} catch (Throwable ex) {
			throw new BeanCreationException(
					mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
		}
	}


	/**
	 * 具体创建 Bean 实例的过程，执行实例化、属性填充、初始化等完整生命周期操作。
	 * <p>
	 * 核心流程：
	 * 1. 尝试从缓存中获取已有的 BeanWrapper，否则创建新的 Bean 实例。
	 * 2. 缓存并记录 Bean 的类型信息。
	 * 3. 执行合并后 Bean 定义的后处理器（MergedBeanDefinitionPostProcessor）。
	 * 4. 判断是否需要提前暴露单例 Bean 引用（解决循环依赖）。
	 * 5. 填充 Bean 属性（依赖注入）。
	 * 6. 初始化 Bean（调用初始化方法、应用初始化后处理器）。
	 * 7. 处理循环依赖中的早期引用替换。
	 * 8. 注册销毁方法（如果有）。
	 * 9. 返回最终的 Bean 实例（可能是代理后的对象）。
	 *
	 * @param beanName Bean 名称
	 * @param mbd      Bean 定义
	 * @param args     构造器参数
	 * @return 创建好的 Bean 实例（可能是代理对象）
	 */
	protected Object doCreateBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {

		BeanWrapper instanceWrapper = null;

		// ----------------- 1. 尝试从缓存获取 BeanWrapper -----------------
		if (mbd.isSingleton()) {
			instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
		}

		// ----------------- 2. 如果缓存不存在，创建 Bean 实例（调用构造方法实例化） -----------------
		if (instanceWrapper == null) {
			instanceWrapper = createBeanInstance(beanName, mbd, args);
		}
		// 获取实际的 Bean 对象和类型
		Object bean = instanceWrapper.getWrappedInstance();
		Class<?> beanType = instanceWrapper.getWrappedClass();
		// 记录 Bean 的类型，方便后续使用
		if (beanType != NullBean.class) {
			mbd.resolvedTargetType = beanType;
		}
		// 对 BeanDefinition 进行后置处理器（MergedBeanDefinitionPostProcessor），只处理一次
		synchronized (mbd.postProcessingLock) {
			if (!mbd.postProcessed) {
				try {
					applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
				} catch (Throwable ex) {
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Post-processing of merged bean definition failed", ex);
				}
				mbd.postProcessed = true;
			}
		}
		// 向容器中缓存单例模式的Bean对象, 防止循环引用
		boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
				isSingletonCurrentlyInCreation(beanName));
		if (earlySingletonExposure) {
			if (logger.isTraceEnabled()) {
				logger.trace("Eagerly caching bean '" + beanName +
						"' to allow for resolving potential circular references");
			}
			// 为了防止循环引用, 尽早持有对象的引用
			addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
		}

		// Bean 对象的初始化,依赖注入在此触发, 这个exposedObject 在初始化完成之后返回作为依赖注入完成后的Bean
		Object exposedObject = bean;
		try {
			// 将 Bean 实例对象封装,并且 Bean 定义中配置的属性值赋值给实例对象
			populateBean(beanName, mbd, instanceWrapper);
			// 初始化 Bean（调用 init-method，Aware 接口方法，BeanPostProcessor 的初始化后置处理器）
			exposedObject = initializeBean(beanName, exposedObject, mbd);
		} catch (Throwable ex) {
			if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
				throw (BeanCreationException) ex;
			} else {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
			}
		}

		// 处理早期暴露的单例引用（解决循环依赖时的代理对象替换）
		if (earlySingletonExposure) {
			// 获取指定名称的已注册的单例模式 Bean 对象
			Object earlySingletonReference = getSingleton(beanName, false);
			if (earlySingletonReference != null) {
				// 根据名称获取的已注册的Bean 和正在实例化的Bean 是同一个
				if (exposedObject == bean) {
					//当前实化的Bean 初始化完成
					exposedObject = earlySingletonReference;

				}
				// 否则检查是否存在循环依赖问题（略）
				else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
					String[] dependentBeans = getDependentBeans(beanName);
					Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
					// 获取当前 Bean 所依赖的其他 Bean
					for (String dependentBean : dependentBeans) {
						// 对依赖 Bean 进行类型检查
						if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
							actualDependentBeans.add(dependentBean);
						}
					}
					if (!actualDependentBeans.isEmpty()) {
						throw new BeanCurrentlyInCreationException(beanName,
								"Bean with name '" + beanName + "' has been injected into other beans [" +
										StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
										"] in its raw version as part of a circular reference, but has eventually been " +
										"wrapped. This means that said other beans do not use the final version of the " +
										"bean. This is often the result of over-eager type matching - consider using " +
										"'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example.");
					}
				}
			}
		}

		// 注册完成依赖注入的 Bean, 注册 DisposableBean，支持 Bean 的销毁回调
		try {
			registerDisposableBeanIfNecessary(beanName, bean, mbd);
		} catch (BeanDefinitionValidationException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
		}

		return exposedObject;
	}

	@Override
	@Nullable
	protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = determineTargetType(beanName, mbd, typesToMatch);
		if (targetType != null && !mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			boolean matchingOnlyFactoryBean = (typesToMatch.length == 1 && typesToMatch[0] == FactoryBean.class);
			for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
				Class<?> predicted = bp.predictBeanType(targetType, beanName);
				if (predicted != null &&
						(!matchingOnlyFactoryBean || FactoryBean.class.isAssignableFrom(predicted))) {
					return predicted;
				}
			}
		}
		return targetType;
	}

	@Nullable
	protected Class<?> determineTargetType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = mbd.getTargetType();
		if (targetType == null) {
			targetType = (mbd.getFactoryMethodName() != null ?
					getTypeForFactoryMethod(beanName, mbd, typesToMatch) :
					resolveBeanClass(mbd, beanName, typesToMatch));
			if (ObjectUtils.isEmpty(typesToMatch) || getTempClassLoader() == null) {
				mbd.resolvedTargetType = targetType;
			}
		}
		return targetType;
	}

	@Nullable
	protected Class<?> getTypeForFactoryMethod(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		ResolvableType cachedReturnType = mbd.factoryMethodReturnType;
		if (cachedReturnType != null) {
			return cachedReturnType.resolve();
		}

		Class<?> commonType = null;
		Method uniqueCandidate = mbd.factoryMethodToIntrospect;

		if (uniqueCandidate == null) {
			Class<?> factoryClass;
			boolean isStatic = true;

			String factoryBeanName = mbd.getFactoryBeanName();
			if (factoryBeanName != null) {
				if (factoryBeanName.equals(beanName)) {
					throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
							"factory-bean reference points back to the same bean definition");
				}
				factoryClass = getType(factoryBeanName);
				isStatic = false;
			} else {
				factoryClass = resolveBeanClass(mbd, beanName, typesToMatch);
			}

			if (factoryClass == null) {
				return null;
			}
			factoryClass = ClassUtils.getUserClass(factoryClass);

			int minNrOfArgs =
					(mbd.hasConstructorArgumentValues() ? mbd.getConstructorArgumentValues().getArgumentCount() : 0);
			Method[] candidates = this.factoryMethodCandidateCache.computeIfAbsent(factoryClass,
					clazz -> ReflectionUtils.getUniqueDeclaredMethods(clazz, ReflectionUtils.USER_DECLARED_METHODS));

			for (Method candidate : candidates) {
				if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate) &&
						candidate.getParameterCount() >= minNrOfArgs) {
					if (candidate.getTypeParameters().length > 0) {
						try {
							Class<?>[] paramTypes = candidate.getParameterTypes();
							String[] paramNames = null;
							ParameterNameDiscoverer pnd = getParameterNameDiscoverer();
							if (pnd != null) {
								paramNames = pnd.getParameterNames(candidate);
							}
							ConstructorArgumentValues cav = mbd.getConstructorArgumentValues();
							Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = new HashSet<>(paramTypes.length);
							Object[] args = new Object[paramTypes.length];
							for (int i = 0; i < args.length; i++) {
								ConstructorArgumentValues.ValueHolder valueHolder = cav.getArgumentValue(
										i, paramTypes[i], (paramNames != null ? paramNames[i] : null), usedValueHolders);
								if (valueHolder == null) {
									valueHolder = cav.getGenericArgumentValue(null, null, usedValueHolders);
								}
								if (valueHolder != null) {
									args[i] = valueHolder.getValue();
									usedValueHolders.add(valueHolder);
								}
							}
							Class<?> returnType = AutowireUtils.resolveReturnTypeForFactoryMethod(
									candidate, args, getBeanClassLoader());
							uniqueCandidate = (commonType == null && returnType == candidate.getReturnType() ?
									candidate : null);
							commonType = ClassUtils.determineCommonAncestor(returnType, commonType);
							if (commonType == null) {
								return null;
							}
						} catch (Throwable ex) {
							if (logger.isDebugEnabled()) {
								logger.debug("Failed to resolve generic return type for factory method: " + ex);
							}
						}
					} else {
						uniqueCandidate = (commonType == null ? candidate : null);
						commonType = ClassUtils.determineCommonAncestor(candidate.getReturnType(), commonType);
						if (commonType == null) {
							return null;
						}
					}
				}
			}

			mbd.factoryMethodToIntrospect = uniqueCandidate;
			if (commonType == null) {
				return null;
			}
		}

		cachedReturnType = (uniqueCandidate != null ?
				ResolvableType.forMethodReturnType(uniqueCandidate) : ResolvableType.forClass(commonType));
		mbd.factoryMethodReturnType = cachedReturnType;
		return cachedReturnType.resolve();
	}

	@Override
	protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
		ResolvableType result = getTypeForFactoryBeanFromAttributes(mbd);
		if (result != ResolvableType.NONE) {
			return result;
		}

		ResolvableType beanType =
				(mbd.hasBeanClass() ? ResolvableType.forClass(mbd.getBeanClass()) : ResolvableType.NONE);

		if (mbd.getInstanceSupplier() != null) {
			result = getFactoryBeanGeneric(mbd.targetType);
			if (result.resolve() != null) {
				return result;
			}
			result = getFactoryBeanGeneric(beanType);
			if (result.resolve() != null) {
				return result;
			}
		}

		String factoryBeanName = mbd.getFactoryBeanName();
		String factoryMethodName = mbd.getFactoryMethodName();

		if (factoryBeanName != null) {
			if (factoryMethodName != null) {
				BeanDefinition factoryBeanDefinition = getBeanDefinition(factoryBeanName);
				Class<?> factoryBeanClass;
				if (factoryBeanDefinition instanceof AbstractBeanDefinition &&
						((AbstractBeanDefinition) factoryBeanDefinition).hasBeanClass()) {
					factoryBeanClass = ((AbstractBeanDefinition) factoryBeanDefinition).getBeanClass();
				} else {
					RootBeanDefinition fbmbd = getMergedBeanDefinition(factoryBeanName, factoryBeanDefinition);
					factoryBeanClass = determineTargetType(factoryBeanName, fbmbd);
				}
				if (factoryBeanClass != null) {
					result = getTypeForFactoryBeanFromMethod(factoryBeanClass, factoryMethodName);
					if (result.resolve() != null) {
						return result;
					}
				}
			}
			if (!isBeanEligibleForMetadataCaching(factoryBeanName)) {
				return ResolvableType.NONE;
			}
		}

		if (allowInit) {
			FactoryBean<?> factoryBean = (mbd.isSingleton() ?
					getSingletonFactoryBeanForTypeCheck(beanName, mbd) :
					getNonSingletonFactoryBeanForTypeCheck(beanName, mbd));
			if (factoryBean != null) {
				Class<?> type = getTypeForFactoryBean(factoryBean);
				if (type != null) {
					return ResolvableType.forClass(type);
				}
				return super.getTypeForFactoryBean(beanName, mbd, true);
			}
		}

		if (factoryBeanName == null && mbd.hasBeanClass() && factoryMethodName != null) {
			return getTypeForFactoryBeanFromMethod(mbd.getBeanClass(), factoryMethodName);
		}
		result = getFactoryBeanGeneric(beanType);
		if (result.resolve() != null) {
			return result;
		}
		return ResolvableType.NONE;
	}

	private ResolvableType getFactoryBeanGeneric(@Nullable ResolvableType type) {
		if (type == null) {
			return ResolvableType.NONE;
		}
		return type.as(FactoryBean.class).getGeneric();
	}

	private ResolvableType getTypeForFactoryBeanFromMethod(Class<?> beanClass, String factoryMethodName) {
		Class<?> factoryBeanClass = ClassUtils.getUserClass(beanClass);
		FactoryBeanMethodTypeFinder finder = new FactoryBeanMethodTypeFinder(factoryMethodName);
		ReflectionUtils.doWithMethods(factoryBeanClass, finder, ReflectionUtils.USER_DECLARED_METHODS);
		return finder.getResult();
	}

	@Override
	@Deprecated
	@Nullable
	protected Class<?> getTypeForFactoryBean(String beanName, RootBeanDefinition mbd) {
		return getTypeForFactoryBean(beanName, mbd, true).resolve();
	}

	protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
		Object exposedObject = bean;
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
				exposedObject = bp.getEarlyBeanReference(exposedObject, beanName);
			}
		}
		return exposedObject;
	}


	@Nullable
	private FactoryBean<?> getSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
		synchronized (getSingletonMutex()) {
			BeanWrapper bw = this.factoryBeanInstanceCache.get(beanName);
			if (bw != null) {
				return (FactoryBean<?>) bw.getWrappedInstance();
			}
			Object beanInstance = getSingleton(beanName, false);
			if (beanInstance instanceof FactoryBean) {
				return (FactoryBean<?>) beanInstance;
			}
			if (isSingletonCurrentlyInCreation(beanName) ||
					(mbd.getFactoryBeanName() != null && isSingletonCurrentlyInCreation(mbd.getFactoryBeanName()))) {
				return null;
			}

			Object instance;
			try {
				beforeSingletonCreation(beanName);
				instance = resolveBeforeInstantiation(beanName, mbd);
				if (instance == null) {
					bw = createBeanInstance(beanName, mbd, null);
					instance = bw.getWrappedInstance();
				}
			} catch (UnsatisfiedDependencyException ex) {
				throw ex;
			} catch (BeanCreationException ex) {
				if (ex.contains(LinkageError.class)) {
					throw ex;
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Bean creation exception on singleton FactoryBean type check: " + ex);
				}
				onSuppressedException(ex);
				return null;
			} finally {
				afterSingletonCreation(beanName);
			}

			FactoryBean<?> fb = getFactoryBean(beanName, instance);
			if (bw != null) {
				this.factoryBeanInstanceCache.put(beanName, bw);
			}
			return fb;
		}
	}

	@Nullable
	private FactoryBean<?> getNonSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
		if (isPrototypeCurrentlyInCreation(beanName)) {
			return null;
		}

		Object instance;
		try {
			beforePrototypeCreation(beanName);
			instance = resolveBeforeInstantiation(beanName, mbd);
			if (instance == null) {
				BeanWrapper bw = createBeanInstance(beanName, mbd, null);
				instance = bw.getWrappedInstance();
			}
		} catch (UnsatisfiedDependencyException ex) {
			throw ex;
		} catch (BeanCreationException ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Bean creation exception on non-singleton FactoryBean type check: " + ex);
			}
			onSuppressedException(ex);
			return null;
		} finally {
			afterPrototypeCreation(beanName);
		}

		return getFactoryBean(beanName, instance);
	}

	protected void applyMergedBeanDefinitionPostProcessors(RootBeanDefinition mbd, Class<?> beanType, String beanName) {
		for (MergedBeanDefinitionPostProcessor processor : getBeanPostProcessorCache().mergedDefinition) {
			processor.postProcessMergedBeanDefinition(mbd, beanType, beanName);
		}
	}

	@Nullable
	protected Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
		Object bean = null;
		if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {
			if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
				Class<?> targetType = determineTargetType(beanName, mbd);
				if (targetType != null) {
					bean = applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
					if (bean != null) {
						bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
					}
				}
			}
			mbd.beforeInstantiationResolved = (bean != null);
		}
		return bean;
	}

	@Nullable
	protected Object applyBeanPostProcessorsBeforeInstantiation(Class<?> beanClass, String beanName) {
		for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
			Object result = bp.postProcessBeforeInstantiation(beanClass, beanName);
			if (result != null) {
				return result;
			}
		}
		return null;
	}


	/**
	 * 创建 Bean 实例的具体实现，根据不同情况选择合适的实例化策略。
	 * <p>
	 * 主要流程：
	 * 1. 解析 Bean 的 Class 类型，检查访问权限（必须是 public 或允许非 public 访问）。
	 * 2. 优先通过自定义的实例化 Supplier（工厂函数）获取 Bean 实例。
	 * 3. 如果指定了工厂方法，则通过工厂方法实例化。
	 * 4. 判断是否已经解析过构造方法，并且是否需要自动装配构造器，进行对应处理。
	 * 5. 从 BeanPostProcessors 中推断构造函数（如有），或根据自动装配模式/参数情况，选择自动装配构造器。
	 * 6. 如果存在首选构造器，使用首选构造器自动装配。
	 * 7. 最后，默认调用无参构造器实例化 Bean。
	 *
	 * @param beanName Bean 名称
	 * @param mbd      Bean 定义
	 * @param args     构造函数参数（可空）
	 * @return 包装了实例化好的 Bean 对象的 BeanWrapper
	 * @throws BeanCreationException 实例化失败时抛出
	 */
	protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
		// 解析 Bean 的 Class 类型
		Class<?> beanClass = resolveBeanClass(mbd, beanName);

		// 使用工厂方法对 Bean 进行实例化
		if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
		}

		// 如果配置了实例化 Supplier（自定义实例化策略），通过 Supplier 获取实例
		Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
		if (instanceSupplier != null) {
			return obtainFromSupplier(instanceSupplier, beanName);
		}

		// 如果配置了工厂方法，调用工厂方法实例化
		if (mbd.getFactoryMethodName() != null) {
			return instantiateUsingFactoryMethod(beanName, mbd, args);
		}

		// 使用容器的自动装配方法进行实例化
		boolean resolved = false;
		boolean autowireNecessary = false;

		// 判断是否已经解析过构造方法（只在没有传入参数的情况下判断）
		if (args == null) {
			synchronized (mbd.constructorArgumentLock) {
				if (mbd.resolvedConstructorOrFactoryMethod != null) {
					resolved = true;
					autowireNecessary = mbd.constructorArgumentsResolved;
				}
			}
		}

		// 如果已经解析过构造方法，根据是否需要自动装配构造器选择实例化方式
		if (resolved) {
			if (autowireNecessary) {
				// 配置了自动装配属性,使用容器的自动装配实例化
				// 容器的自动装配是根据参数类型匹配 Bean 的构造方法
				return autowireConstructor(beanName, mbd, null, null);
			} else {
				// 使用默认的无参构造方法实例化
				return instantiateBean(beanName, mbd);
			}
		}

		// 使用 Bean 的构造方法进行实例化
		Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);

		// 根据推断结果、自动装配模式、构造参数等，选择自动装配构造方法实例化
		if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR ||
				mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
			// 使用容器的自动装配特性,调用匹配的构造方法实例化
			return autowireConstructor(beanName, mbd, ctors, args);
		}

		// 如果有首选构造器，使用首选构造器自动装配实例化
		ctors = mbd.getPreferredConstructors();
		if (ctors != null) {
			return autowireConstructor(beanName, mbd, ctors, null);
		}

		// 最后，使用无参构造器实例化 Bean
		return instantiateBean(beanName, mbd);
	}


	protected BeanWrapper obtainFromSupplier(Supplier<?> instanceSupplier, String beanName) {
		Object instance;

		String outerBean = this.currentlyCreatedBean.get();
		this.currentlyCreatedBean.set(beanName);
		try {
			instance = instanceSupplier.get();
		} finally {
			if (outerBean != null) {
				this.currentlyCreatedBean.set(outerBean);
			} else {
				this.currentlyCreatedBean.remove();
			}
		}

		if (instance == null) {
			instance = new NullBean();
		}
		BeanWrapper bw = new BeanWrapperImpl(instance);
		initBeanWrapper(bw);
		return bw;
	}

	@Override
	protected Object getObjectForBeanInstance(
			Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

		String currentlyCreatedBean = this.currentlyCreatedBean.get();
		if (currentlyCreatedBean != null) {
			registerDependentBean(beanName, currentlyCreatedBean);
		}

		return super.getObjectForBeanInstance(beanInstance, name, beanName, mbd);
	}

	@Nullable
	protected Constructor<?>[] determineConstructorsFromBeanPostProcessors(@Nullable Class<?> beanClass, String beanName)
			throws BeansException {

		if (beanClass != null && hasInstantiationAwareBeanPostProcessors()) {
			for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
				Constructor<?>[] ctors = bp.determineCandidateConstructors(beanClass, beanName);
				if (ctors != null) {
					return ctors;
				}
			}
		}
		return null;
	}

	/**
	 * 使用默认的无参构造方法实例化 Bean 对象
	 */
	protected BeanWrapper instantiateBean(String beanName, RootBeanDefinition mbd) {
		try {
			Object beanInstance;
			// 获取系统的安全管理接口,JDK 标准的安全管理 API
			if (System.getSecurityManager() != null) {
				// 根据实例化策略创建实例对象
				beanInstance = AccessController.doPrivileged(
						(PrivilegedAction<Object>) () -> getInstantiationStrategy().instantiate(mbd, beanName, this),
						getAccessControlContext());
			} else {
				// 将实例化的对象封装起来
				beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, this);
			}
			BeanWrapper bw = new BeanWrapperImpl(beanInstance);
			initBeanWrapper(bw);
			return bw;
		} catch (Throwable ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Instantiation of bean failed", ex);
		}
	}

	protected BeanWrapper instantiateUsingFactoryMethod(
			String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {

		return new ConstructorResolver(this).instantiateUsingFactoryMethod(beanName, mbd, explicitArgs);
	}

	protected BeanWrapper autowireConstructor(
			String beanName, RootBeanDefinition mbd, @Nullable Constructor<?>[] ctors, @Nullable Object[] explicitArgs) {

		return new ConstructorResolver(this).autowireConstructor(beanName, mbd, ctors, explicitArgs);
	}

	/**
	 * 将 Bean 属性设置到生成的实例对象上
	 */
	@SuppressWarnings("deprecation")
	protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
		if (bw == null) {
			if (mbd.hasPropertyValues()) {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
			} else {
				return;
			}
		}

		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
				if (!bp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
					return;
				}
			}
		}

		// 获取容器在解析 Bean 定义资源时为 BeanDefiniton 中设置的属性值
		PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

		int resolvedAutowireMode = mbd.getResolvedAutowireMode();
		if (resolvedAutowireMode == AUTOWIRE_BY_NAME || resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
			MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
			if (resolvedAutowireMode == AUTOWIRE_BY_NAME) {
				autowireByName(beanName, mbd, bw, newPvs);
			}
			if (resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
				autowireByType(beanName, mbd, bw, newPvs);
			}
			pvs = newPvs;
		}

		boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
		boolean needsDepCheck = (mbd.getDependencyCheck() != AbstractBeanDefinition.DEPENDENCY_CHECK_NONE);

		PropertyDescriptor[] filteredPds = null;
		if (hasInstAwareBpps) {
			if (pvs == null) {
				pvs = mbd.getPropertyValues();
			}
			for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
				PropertyValues pvsToUse = bp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);
				if (pvsToUse == null) {
					if (filteredPds == null) {
						filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
					}
					pvsToUse = bp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
					if (pvsToUse == null) {
						return;
					}
				}
				pvs = pvsToUse;
			}
		}
		if (needsDepCheck) {
			if (filteredPds == null) {
				filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
			}
			checkDependencies(beanName, mbd, filteredPds, pvs);
		}

		if (pvs != null) {
			// 对属性进行注入
			applyPropertyValues(beanName, mbd, bw, pvs);
		}
	}

	protected void autowireByName(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

		String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
		for (String propertyName : propertyNames) {
			if (containsBean(propertyName)) {
				Object bean = getBean(propertyName);
				pvs.add(propertyName, bean);
				registerDependentBean(propertyName, beanName);
				if (logger.isTraceEnabled()) {
					logger.trace("Added autowiring by name from bean name '" + beanName +
							"' via property '" + propertyName + "' to bean named '" + propertyName + "'");
				}
			} else {
				if (logger.isTraceEnabled()) {
					logger.trace("Not autowiring property '" + propertyName + "' of bean '" + beanName +
							"' by name: no matching bean found");
				}
			}
		}
	}

	protected void autowireByType(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			converter = bw;
		}

		String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
		Set<String> autowiredBeanNames = new LinkedHashSet<>(propertyNames.length * 2);
		for (String propertyName : propertyNames) {
			try {
				PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
				if (Object.class != pd.getPropertyType()) {
					MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
					boolean eager = !(bw.getWrappedInstance() instanceof PriorityOrdered);
					DependencyDescriptor desc = new AutowireByTypeDependencyDescriptor(methodParam, eager);
					Object autowiredArgument = resolveDependency(desc, beanName, autowiredBeanNames, converter);
					if (autowiredArgument != null) {
						pvs.add(propertyName, autowiredArgument);
					}
					for (String autowiredBeanName : autowiredBeanNames) {
						registerDependentBean(autowiredBeanName, beanName);
						if (logger.isTraceEnabled()) {
							logger.trace("Autowiring by type from bean name '" + beanName + "' via property '" +
									propertyName + "' to bean named '" + autowiredBeanName + "'");
						}
					}
					autowiredBeanNames.clear();
				}
			} catch (BeansException ex) {
				throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, propertyName, ex);
			}
		}
	}


	protected String[] unsatisfiedNonSimpleProperties(AbstractBeanDefinition mbd, BeanWrapper bw) {
		Set<String> result = new TreeSet<>();
		PropertyValues pvs = mbd.getPropertyValues();
		PropertyDescriptor[] pds = bw.getPropertyDescriptors();
		for (PropertyDescriptor pd : pds) {
			if (pd.getWriteMethod() != null && !isExcludedFromDependencyCheck(pd) && !pvs.contains(pd.getName()) &&
					!BeanUtils.isSimpleProperty(pd.getPropertyType())) {
				result.add(pd.getName());
			}
		}
		return StringUtils.toStringArray(result);
	}

	protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw, boolean cache) {
		PropertyDescriptor[] filtered = this.filteredPropertyDescriptorsCache.get(bw.getWrappedClass());
		if (filtered == null) {
			filtered = filterPropertyDescriptorsForDependencyCheck(bw);
			if (cache) {
				PropertyDescriptor[] existing =
						this.filteredPropertyDescriptorsCache.putIfAbsent(bw.getWrappedClass(), filtered);
				if (existing != null) {
					filtered = existing;
				}
			}
		}
		return filtered;
	}

	protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw) {
		List<PropertyDescriptor> pds = new ArrayList<>(Arrays.asList(bw.getPropertyDescriptors()));
		pds.removeIf(this::isExcludedFromDependencyCheck);
		return pds.toArray(new PropertyDescriptor[0]);
	}

	protected boolean isExcludedFromDependencyCheck(PropertyDescriptor pd) {
		return (AutowireUtils.isExcludedFromDependencyCheck(pd) ||
				this.ignoredDependencyTypes.contains(pd.getPropertyType()) ||
				AutowireUtils.isSetterDefinedInInterface(pd, this.ignoredDependencyInterfaces));
	}

	protected void checkDependencies(
			String beanName, AbstractBeanDefinition mbd, PropertyDescriptor[] pds, @Nullable PropertyValues pvs)
			throws UnsatisfiedDependencyException {

		int dependencyCheck = mbd.getDependencyCheck();
		for (PropertyDescriptor pd : pds) {
			if (pd.getWriteMethod() != null && (pvs == null || !pvs.contains(pd.getName()))) {
				boolean isSimple = BeanUtils.isSimpleProperty(pd.getPropertyType());
				boolean unsatisfied = (dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_ALL) ||
						(isSimple && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_SIMPLE) ||
						(!isSimple && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_OBJECTS);
				if (unsatisfied) {
					throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, pd.getName(),
							"Set this property value or disable dependency checking for this bean.");
				}
			}
		}
	}

	/**
	 * 解析并注入依赖属性的过程
	 */
	protected void applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs) {
		if (pvs.isEmpty()) {
			return;
		}

		if (System.getSecurityManager() != null && bw instanceof BeanWrapperImpl) {
			((BeanWrapperImpl) bw).setSecurityContext(getAccessControlContext());
		}
		// 封装属性值
		MutablePropertyValues mpvs = null;
		List<PropertyValue> original;

		if (pvs instanceof MutablePropertyValues) {
			mpvs = (MutablePropertyValues) pvs;
			// 属性值已经转换
			if (mpvs.isConverted()) {
				try {
					// 为实例化对象设置属性值
					bw.setPropertyValues(mpvs);
					return;
				} catch (BeansException ex) {
					throw new BeanCreationException(
							mbd.getResourceDescription(), beanName, "Error setting property values", ex);
				}
			}
			// 获取属性值对象的原始类型值
			original = mpvs.getPropertyValueList();
		} else {
			original = Arrays.asList(pvs.getPropertyValues());
		}

		// 获取用户自定义的类型转换
		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			converter = bw;
		}
		// 创建一个 Bean 定义属性值解析器,将 Bean 定义中的属性值解析为 Bean 实例对象的实际值
		BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this, beanName, mbd, converter);

		// 为属性的解析值创建一个拷贝,将拷贝的数据注入到实例对象中
		List<PropertyValue> deepCopy = new ArrayList<>(original.size());
		boolean resolveNecessary = false;
		for (PropertyValue pv : original) {
			// 属性值不需要转换
			if (pv.isConverted()) {
				deepCopy.add(pv);
			}
			// 属性值需要转换
			else {
				String propertyName = pv.getName();
				// 原始的属性值,即转换之前的属性值
				Object originalValue = pv.getValue();
				if (originalValue == AutowiredPropertyMarker.INSTANCE) {
					Method writeMethod = bw.getPropertyDescriptor(propertyName).getWriteMethod();
					if (writeMethod == null) {
						throw new IllegalArgumentException("Autowire marker for property without write method: " + pv);
					}
					originalValue = new DependencyDescriptor(new MethodParameter(writeMethod, 0), true);
				}
				// 转换属性值,例如将引用转换为 IOC 容器中实例化对象引用
				Object resolvedValue = valueResolver.resolveValueIfNecessary(pv, originalValue);
				// 转换之后的属性值
				Object convertedValue = resolvedValue;
				// 属性值是否可以转换
				boolean convertible = bw.isWritableProperty(propertyName) &&
						!PropertyAccessorUtils.isNestedOrIndexedProperty(propertyName);
				if (convertible) {
					// 使用用户自定义的类型转换器转换属性值
					convertedValue = convertForProperty(resolvedValue, propertyName, bw, converter);
				}
				if (resolvedValue == originalValue) {
					if (convertible) {
						pv.setConvertedValue(convertedValue);
					}
					deepCopy.add(pv);
				}
				// 属性是可转换的,且属性原始值是字符串类型,且属性的原始类型值不是
				// 动态生成的字符串,且属性的原始值不是集合或者数组类型
				else if (convertible && originalValue instanceof TypedStringValue &&
						!((TypedStringValue) originalValue).isDynamic() &&
						!(convertedValue instanceof Collection || ObjectUtils.isArray(convertedValue))) {
					pv.setConvertedValue(convertedValue);
					deepCopy.add(pv);
				} else {
					resolveNecessary = true;
					deepCopy.add(new PropertyValue(pv, convertedValue));
				}
			}
		}
		if (mpvs != null && !resolveNecessary) {
			// 标记属性值已经转换过
			mpvs.setConverted();
		}
		// 进行属性依赖注入
		try {
			// 具体注入方法
			bw.setPropertyValues(new MutablePropertyValues(deepCopy));
		} catch (BeansException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Error setting property values", ex);
		}
	}

	@Nullable
	private Object convertForProperty(
			@Nullable Object value, String propertyName, BeanWrapper bw, TypeConverter converter) {

		if (converter instanceof BeanWrapperImpl) {
			return ((BeanWrapperImpl) converter).convertForProperty(value, propertyName);
		} else {
			PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
			MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
			return converter.convertIfNecessary(value, pd.getPropertyType(), methodParam);
		}
	}


	/**
	 * 初始化指定的 bean，包括执行 Aware 接口方法、初始化方法以及 BeanPostProcessor 的前后处理方法。
	 *
	 * @param beanName bean 的名称
	 * @param bean     已实例化并完成依赖注入的 bean 对象
	 * @param mbd      bean 对应的 RootBeanDefinition，可能为 null
	 * @return 初始化后的 bean（可能已被代理包装）
	 */
	protected Object initializeBean(String beanName, Object bean, @Nullable RootBeanDefinition mbd) {

		// 如果启用了 Java 安全管理器，则在特权访问控制下调用 Aware 方法
		if (System.getSecurityManager() != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				// 调用 Aware 接口（如 BeanNameAware、BeanClassLoaderAware、BeanFactoryAware）相关方法
				invokeAwareMethods(beanName, bean);
				return null;
			}, getAccessControlContext());
		} else {
			// 为 Bean实例对象包装相关属性,如名称,类加载器,所属容器等信息
			invokeAwareMethods(beanName, bean);
		}

		// 包装后的 bean（可能被代理）
		Object wrappedBean = bean;

		// 如果该 bean 不是“合成的”（synthetic，表示由 Spring 内部生成，而不是用户定义的），
		// 则调用所有 BeanPostProcessor 的 postProcessBeforeInitialization 方法
		if (mbd == null || !mbd.isSynthetic()) {
			wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
		}

		try {
			// 调用初始化方法（包括 InitializingBean.afterPropertiesSet() 和 自定义 init-method）
			invokeInitMethods(beanName, wrappedBean, mbd);
		} catch (Throwable ex) {
			// 初始化方法调用失败，抛出 Bean 创建异常
			throw new BeanCreationException(
					(mbd != null ? mbd.getResourceDescription() : null),
					beanName, "Invocation of init method failed", ex);
		}

		// 如果该 bean 不是“合成的”，则调用所有 BeanPostProcessor 的 postProcessAfterInitialization 方法
		if (mbd == null || !mbd.isSynthetic()) {
			wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
		}

		// 返回最终初始化完成的 bean，可能是原始对象，也可能是代理对象(增强逻辑)
		return wrappedBean;
	}


	private void invokeAwareMethods(String beanName, Object bean) {
		if (bean instanceof Aware) {
			if (bean instanceof BeanNameAware) {
				((BeanNameAware) bean).setBeanName(beanName);
			}
			if (bean instanceof BeanClassLoaderAware) {
				ClassLoader bcl = getBeanClassLoader();
				if (bcl != null) {
					((BeanClassLoaderAware) bean).setBeanClassLoader(bcl);
				}
			}
			if (bean instanceof BeanFactoryAware) {
				((BeanFactoryAware) bean).setBeanFactory(AbstractAutowireCapableBeanFactory.this);
			}
		}
	}

	protected void invokeInitMethods(String beanName, Object bean, @Nullable RootBeanDefinition mbd)
			throws Throwable {

		boolean isInitializingBean = (bean instanceof InitializingBean);
		if (isInitializingBean && (mbd == null || !mbd.hasAnyExternallyManagedInitMethod("afterPropertiesSet"))) {
			if (logger.isTraceEnabled()) {
				logger.trace("Invoking afterPropertiesSet() on bean with name '" + beanName + "'");
			}
			if (System.getSecurityManager() != null) {
				try {
					AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
						((InitializingBean) bean).afterPropertiesSet();
						return null;
					}, getAccessControlContext());
				} catch (PrivilegedActionException pae) {
					throw pae.getException();
				}
			} else {
				((InitializingBean) bean).afterPropertiesSet();
			}
		}

		if (mbd != null && bean.getClass() != NullBean.class) {
			String initMethodName = mbd.getInitMethodName();
			if (StringUtils.hasLength(initMethodName) &&
					!(isInitializingBean && "afterPropertiesSet".equals(initMethodName)) &&
					!mbd.hasAnyExternallyManagedInitMethod(initMethodName)) {
				invokeCustomInitMethod(beanName, bean, mbd);
			}
		}
	}

	protected void invokeCustomInitMethod(String beanName, Object bean, RootBeanDefinition mbd)
			throws Throwable {

		String initMethodName = mbd.getInitMethodName();
		Assert.state(initMethodName != null, "No init method set");
		Method initMethod = (mbd.isNonPublicAccessAllowed() ?
				BeanUtils.findMethod(bean.getClass(), initMethodName) :
				ClassUtils.getMethodIfAvailable(bean.getClass(), initMethodName));

		if (initMethod == null) {
			if (mbd.isEnforceInitMethod()) {
				throw new BeanDefinitionValidationException("Could not find an init method named '" +
						initMethodName + "' on bean with name '" + beanName + "'");
			} else {
				if (logger.isTraceEnabled()) {
					logger.trace("No default init method named '" + initMethodName +
							"' found on bean with name '" + beanName + "'");
				}
				return;
			}
		}

		if (logger.isTraceEnabled()) {
			logger.trace("Invoking init method  '" + initMethodName + "' on bean with name '" + beanName + "'");
		}
		Method methodToInvoke = ClassUtils.getInterfaceMethodIfPossible(initMethod, bean.getClass());

		if (System.getSecurityManager() != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				ReflectionUtils.makeAccessible(methodToInvoke);
				return null;
			});
			try {
				AccessController.doPrivileged((PrivilegedExceptionAction<Object>)
						() -> methodToInvoke.invoke(bean), getAccessControlContext());
			} catch (PrivilegedActionException pae) {
				InvocationTargetException ex = (InvocationTargetException) pae.getException();
				throw ex.getTargetException();
			}
		} else {
			try {
				ReflectionUtils.makeAccessible(methodToInvoke);
				methodToInvoke.invoke(bean);
			} catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
	}


	@Override
	protected Object postProcessObjectFromFactoryBean(Object object, String beanName) {
		return applyBeanPostProcessorsAfterInitialization(object, beanName);
	}

	@Override
	protected void removeSingleton(String beanName) {
		synchronized (getSingletonMutex()) {
			super.removeSingleton(beanName);
			this.factoryBeanInstanceCache.remove(beanName);
		}
	}

	@Override
	protected void clearSingletonCache() {
		synchronized (getSingletonMutex()) {
			super.clearSingletonCache();
			this.factoryBeanInstanceCache.clear();
		}
	}

	Log getLogger() {
		return logger;
	}


	@SuppressWarnings("serial")
	private static class AutowireByTypeDependencyDescriptor extends DependencyDescriptor {

		public AutowireByTypeDependencyDescriptor(MethodParameter methodParameter, boolean eager) {
			super(methodParameter, false, eager);
		}

		@Override
		public String getDependencyName() {
			return null;
		}
	}


	private static class FactoryBeanMethodTypeFinder implements MethodCallback {

		private final String factoryMethodName;

		private ResolvableType result = ResolvableType.NONE;

		FactoryBeanMethodTypeFinder(String factoryMethodName) {
			this.factoryMethodName = factoryMethodName;
		}

		@Override
		public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
			if (isFactoryBeanMethod(method)) {
				ResolvableType returnType = ResolvableType.forMethodReturnType(method);
				ResolvableType candidate = returnType.as(FactoryBean.class).getGeneric();
				if (this.result == ResolvableType.NONE) {
					this.result = candidate;
				} else {
					Class<?> resolvedResult = this.result.resolve();
					Class<?> commonAncestor = ClassUtils.determineCommonAncestor(candidate.resolve(), resolvedResult);
					if (!ObjectUtils.nullSafeEquals(resolvedResult, commonAncestor)) {
						this.result = ResolvableType.forClass(commonAncestor);
					}
				}
			}
		}

		private boolean isFactoryBeanMethod(Method method) {
			return (method.getName().equals(this.factoryMethodName) &&
					FactoryBean.class.isAssignableFrom(method.getReturnType()));
		}

		ResolvableType getResult() {
			Class<?> resolved = this.result.resolve();
			boolean foundResult = resolved != null && resolved != Object.class;
			return (foundResult ? this.result : ResolvableType.NONE);
		}
	}

}
