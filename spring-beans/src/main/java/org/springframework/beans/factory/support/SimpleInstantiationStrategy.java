package org.springframework.beans.factory.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * 默认的 Bean 实例化策略实现类。
 * 不支持方法注入（Method Injection），仅支持：
 * - 默认构造函数实例化
 * - 指定构造函数实例化
 * - 工厂方法实例化
 */
public class SimpleInstantiationStrategy implements InstantiationStrategy {

	/**
	 * 当前正在调用的工厂方法（用于处理嵌套调用时的上下文传递）
	 */
	private static final ThreadLocal<Method> currentlyInvokedFactoryMethod = new ThreadLocal<>();

	/**
	 * 获取当前正在调用的工厂方法（主要用于内部处理 AOP 时需要判断当前方法是否为工厂方法）
	 */
	@Nullable
	public static Method getCurrentlyInvokedFactoryMethod() {
		return currentlyInvokedFactoryMethod.get();
	}

	/**
	 * 使用默认无参构造方法实例化 Bean
	 */
	@Override
	public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
		// 如果 Bean 定义中没有方法覆盖,则就不需要 CGLib 父类类的方法 jdk动态代理针对接口, 没有重写方法使用cglib进行实例化
		if (!bd.hasMethodOverrides()) {
			Constructor<?> constructorToUse;
			synchronized (bd.constructorArgumentLock) {
				// 从缓存中尝试获取构造器
				constructorToUse = (Constructor<?>) bd.resolvedConstructorOrFactoryMethod;
				// 如果没有构造方法且没有工厂方法
				if (constructorToUse == null) {
					// 使用 JDK 的反射机制,判断要实例化的Bean 是否是接口
					final Class<?> clazz = bd.getBeanClass();
					if (clazz.isInterface()) {
						throw new BeanInstantiationException(clazz, "Specified class is an interface");
					}
					try {
						if (System.getSecurityManager() != null) {
							// 这里是一个匿名内置类,使用反射机制获取 Bean 的构造方法
							constructorToUse = AccessController.doPrivileged(
									(PrivilegedExceptionAction<Constructor<?>>) clazz::getDeclaredConstructor);
						} else {
							constructorToUse = clazz.getDeclaredConstructor(); // 获取默认构造方法
						}
						// 缓存构造器
						bd.resolvedConstructorOrFactoryMethod = constructorToUse;
					} catch (Throwable ex) {
						throw new BeanInstantiationException(clazz, "No default constructor found", ex);
					}
				}
			}
			// 使用 CGLib来实例化对象
			return BeanUtils.instantiateClass(constructorToUse);
		} else {
			// 存在方法注入，则交由子类覆盖的方法处理（本类不支持）
			return instantiateWithMethodInjection(bd, beanName, owner);
		}
	}

	/**
	 * 子类可覆盖：支持方法注入的实例化逻辑。
	 * 此处抛出异常，说明 SimpleInstantiationStrategy 不支持方法注入。
	 */
	protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
		throw new UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy");
	}

	/**
	 * 使用指定构造函数和参数实例化 Bean
	 */
	@Override
	public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
							  final Constructor<?> ctor, Object... args) {

		if (!bd.hasMethodOverrides()) {
			// 构造器权限提升（适配 private 等非 public 构造器）
			if (System.getSecurityManager() != null) {
				AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
					ReflectionUtils.makeAccessible(ctor);
					return null;
				});
			}
			return BeanUtils.instantiateClass(ctor, args);
		} else {
			// 存在方法注入，交给子类实现
			return instantiateWithMethodInjection(bd, beanName, owner, ctor, args);
		}
	}

	/**
	 * 子类可覆盖：支持方法注入的构造函数实例化逻辑。
	 * 此处抛出异常，说明 SimpleInstantiationStrategy 不支持方法注入。
	 */
	protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName,
													BeanFactory owner, @Nullable Constructor<?> ctor, Object... args) {

		throw new UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy");
	}

	/**
	 * 使用工厂方法实例化 Bean（支持静态方法或实例方法）
	 */
	@Override
	public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
							  @Nullable Object factoryBean, final Method factoryMethod, Object... args) {

		try {
			// 提升方法可访问性（如果是私有方法）
			if (System.getSecurityManager() != null) {
				AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
					ReflectionUtils.makeAccessible(factoryMethod);
					return null;
				});
			} else {
				ReflectionUtils.makeAccessible(factoryMethod);
			}

			// 当前线程缓存设置工厂方法（用于后续分析）
			Method priorInvokedFactoryMethod = currentlyInvokedFactoryMethod.get();
			try {
				currentlyInvokedFactoryMethod.set(factoryMethod);

				// 调用工厂方法创建对象
				Object result = factoryMethod.invoke(factoryBean, args);
				// 如果返回为空，则封装为 NullBean（Spring 特有处理）
				if (result == null) {
					result = new NullBean();
				}
				return result;
			} finally {
				// 恢复之前的工厂方法引用（避免影响其他 Bean 创建）
				if (priorInvokedFactoryMethod != null) {
					currentlyInvokedFactoryMethod.set(priorInvokedFactoryMethod);
				} else {
					currentlyInvokedFactoryMethod.remove();
				}
			}
		}
		// 以下是调用异常处理
		catch (IllegalArgumentException ex) {
			throw new BeanInstantiationException(factoryMethod,
					"Illegal arguments to factory method '" + factoryMethod.getName() + "'; " +
							"args: " + StringUtils.arrayToCommaDelimitedString(args), ex);
		} catch (IllegalAccessException ex) {
			throw new BeanInstantiationException(factoryMethod,
					"Cannot access factory method '" + factoryMethod.getName() + "'; is it public?", ex);
		} catch (InvocationTargetException ex) {
			String msg = "Factory method '" + factoryMethod.getName() + "' threw exception";
			// 判断是否出现循环依赖，并给予提示
			if (bd.getFactoryBeanName() != null && owner instanceof ConfigurableBeanFactory &&
					((ConfigurableBeanFactory) owner).isCurrentlyInCreation(bd.getFactoryBeanName())) {
				msg = "Circular reference involving containing bean '" + bd.getFactoryBeanName() + "' - consider " +
						"declaring the factory method as static for independence from its containing instance. " + msg;
			}
			throw new BeanInstantiationException(factoryMethod, msg, ex.getTargetException());
		}
	}
}

