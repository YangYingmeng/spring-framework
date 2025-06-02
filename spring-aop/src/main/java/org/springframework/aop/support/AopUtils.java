package org.springframework.aop.support;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.aop.Advisor;
import org.springframework.aop.AopInvocationException;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionAwareMethodMatcher;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.TargetClassAware;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodIntrospector;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Aop 工具类，提供判断是否为代理类、选择可执行方法、判断 advisor 是否可用等静态方法。
 */
public abstract class AopUtils {

	/**
	 * 判断给定对象是否为 AOP 代理对象（JDK 动态代理或 CGLIB 代理）
	 */
	public static boolean isAopProxy(@Nullable Object object) {
		return (object instanceof SpringProxy && (Proxy.isProxyClass(object.getClass()) ||
				object.getClass().getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR)));
	}

	/**
	 * 判断是否为 JDK 动态代理对象
	 */
	public static boolean isJdkDynamicProxy(@Nullable Object object) {
		return (object instanceof SpringProxy && Proxy.isProxyClass(object.getClass()));
	}

	/**
	 * 判断是否为 CGLIB 动态代理对象
	 */
	public static boolean isCglibProxy(@Nullable Object object) {
		return (object instanceof SpringProxy &&
				object.getClass().getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR));
	}

	/**
	 * 获取目标类（TargetClassAware 优先，否则根据代理类型返回 Class 或其父类）
	 */
	public static Class<?> getTargetClass(Object candidate) {
		Assert.notNull(candidate, "Candidate object must not be null");
		Class<?> result = null;
		if (candidate instanceof TargetClassAware) {
			result = ((TargetClassAware) candidate).getTargetClass();
		}
		if (result == null) {
			result = (isCglibProxy(candidate) ? candidate.getClass().getSuperclass() : candidate.getClass());
		}
		return result;
	}

	/**
	 * 选择可调用的 Method（处理桥接方法、代理类中的方法反射等问题）
	 */
	public static Method selectInvocableMethod(Method method, @Nullable Class<?> targetType) {
		if (targetType == null) {
			return method;
		}
		Method methodToUse = MethodIntrospector.selectInvocableMethod(method, targetType);
		// 如果方法是 private 且不是 static，并且是 SpringProxy 的子类，则抛异常
		if (Modifier.isPrivate(methodToUse.getModifiers()) && !Modifier.isStatic(methodToUse.getModifiers()) &&
				SpringProxy.class.isAssignableFrom(targetType)) {
			throw new IllegalStateException(String.format(
					"需要调用目标类 '%s' 上的方法 '%s'，但无法被代理对象调用。请将其可见性改为包访问或 protected。",
					method.getDeclaringClass().getSimpleName(), method.getName()));
		}
		return methodToUse;
	}

	/**
	 * 判断方法是否为 equals 方法
	 */
	public static boolean isEqualsMethod(@Nullable Method method) {
		return ReflectionUtils.isEqualsMethod(method);
	}

	/**
	 * 判断方法是否为 hashCode 方法
	 */
	public static boolean isHashCodeMethod(@Nullable Method method) {
		return ReflectionUtils.isHashCodeMethod(method);
	}

	/**
	 * 判断方法是否为 toString 方法
	 */
	public static boolean isToStringMethod(@Nullable Method method) {
		return ReflectionUtils.isToStringMethod(method);
	}

	/**
	 * 判断方法是否为 finalize 方法
	 */
	public static boolean isFinalizeMethod(@Nullable Method method) {
		return (method != null && method.getName().equals("finalize") &&
				method.getParameterCount() == 0);
	}

	/**
	 * 获取在目标类中最具体（most specific）的实现方法（处理桥接方法）
	 */
	public static Method getMostSpecificMethod(Method method, @Nullable Class<?> targetClass) {
		Class<?> specificTargetClass = (targetClass != null ? ClassUtils.getUserClass(targetClass) : null);
		Method resolvedMethod = ClassUtils.getMostSpecificMethod(method, specificTargetClass);
		return BridgeMethodResolver.findBridgedMethod(resolvedMethod);
	}

	/**
	 * 判断 Pointcut 是否可以应用到目标类
	 */
	public static boolean canApply(Pointcut pc, Class<?> targetClass) {
		return canApply(pc, targetClass, false);
	}

	/**
	 * 判断 Pointcut 是否可以应用到目标类（支持引介增强）
	 */
	public static boolean canApply(Pointcut pc, Class<?> targetClass, boolean hasIntroductions) {
		Assert.notNull(pc, "Pointcut must not be null");

		// 类过滤器不匹配，直接返回 false
		if (!pc.getClassFilter().matches(targetClass)) {
			return false;
		}

		MethodMatcher methodMatcher = pc.getMethodMatcher();
		// 如果方法匹配器为 TRUE（全匹配），则可以应用
		if (methodMatcher == MethodMatcher.TRUE) {
			return true;
		}

		IntroductionAwareMethodMatcher introductionAwareMethodMatcher = null;
		if (methodMatcher instanceof IntroductionAwareMethodMatcher) {
			introductionAwareMethodMatcher = (IntroductionAwareMethodMatcher) methodMatcher;
		}

		Set<Class<?>> classes = new LinkedHashSet<>();
		if (!Proxy.isProxyClass(targetClass)) {
			classes.add(ClassUtils.getUserClass(targetClass)); // 原始类
		}
		classes.addAll(ClassUtils.getAllInterfacesForClassAsSet(targetClass)); // 所有接口

		// 遍历所有类的方法，看是否有匹配方法
		for (Class<?> clazz : classes) {
			Method[] methods = ReflectionUtils.getAllDeclaredMethods(clazz);
			for (Method method : methods) {
				if (introductionAwareMethodMatcher != null ?
						introductionAwareMethodMatcher.matches(method, targetClass, hasIntroductions) :
						methodMatcher.matches(method, targetClass)) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * 判断给定 Advisor 是否可以应用到目标类
	 */
	public static boolean canApply(Advisor advisor, Class<?> targetClass) {
		return canApply(advisor, targetClass, false);
	}

	/**
	 * 判断给定 Advisor 是否可以应用到目标类（支持引介增强）
	 */
	public static boolean canApply(Advisor advisor, Class<?> targetClass, boolean hasIntroductions) {
		if (advisor instanceof IntroductionAdvisor) {
			// 引介增强只看类过滤器
			return ((IntroductionAdvisor) advisor).getClassFilter().matches(targetClass);
		} else if (advisor instanceof PointcutAdvisor) {
			// 切点增强使用 Pointcut 判断
			PointcutAdvisor pca = (PointcutAdvisor) advisor;
			return canApply(pca.getPointcut(), targetClass, hasIntroductions);
		} else {
			// 普通 Advisor 默认可用
			return true;
		}
	}

	/**
	 * 找出所有可以应用到目标类的 Advisors
	 */
	public static List<Advisor> findAdvisorsThatCanApply(List<Advisor> candidateAdvisors, Class<?> clazz) {
		if (candidateAdvisors.isEmpty()) {
			return candidateAdvisors;
		}
		List<Advisor> eligibleAdvisors = new ArrayList<>();

		// 先处理引介增强
		for (Advisor candidate : candidateAdvisors) {
			if (candidate instanceof IntroductionAdvisor && canApply(candidate, clazz)) {
				eligibleAdvisors.add(candidate);
			}
		}
		boolean hasIntroductions = !eligibleAdvisors.isEmpty();

		// 再处理普通增强
		for (Advisor candidate : candidateAdvisors) {
			if (candidate instanceof IntroductionAdvisor) {
				continue;
			}
			if (canApply(candidate, clazz, hasIntroductions)) {
				eligibleAdvisors.add(candidate);
			}
		}
		return eligibleAdvisors;
	}

	/**
	 * 通过反射调用目标方法（用于连接点执行）
	 */
	@Nullable
	public static Object invokeJoinpointUsingReflection(@Nullable Object target, Method method, Object[] args)
			throws Throwable {
		try {
			// 设置方法可访问
			ReflectionUtils.makeAccessible(method);
			return method.invoke(target, args);
		} catch (InvocationTargetException ex) {
			// 目标方法抛出的异常需要重新抛出
			throw ex.getTargetException();
		} catch (IllegalArgumentException ex) {
			throw new AopInvocationException("AOP 配置可能无效：尝试调用方法 [" +
					method + "] 但目标对象为 [" + target + "]", ex);
		} catch (IllegalAccessException ex) {
			throw new AopInvocationException("无法访问方法 [" + method + "]", ex);
		}
	}
}

