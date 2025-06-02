package org.springframework.aop.framework;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.lang.Nullable;

public class ReflectiveMethodInvocation implements ProxyMethodInvocation, Cloneable {

	protected final Object proxy;

	@Nullable
	protected final Object target;

	protected final Method method;

	protected Object[] arguments;

	@Nullable
	private final Class<?> targetClass;

	@Nullable
	private Map<String, Object> userAttributes;

	protected final List<?> interceptorsAndDynamicMethodMatchers;

	private int currentInterceptorIndex = -1;


	protected ReflectiveMethodInvocation(
			Object proxy, @Nullable Object target, Method method, @Nullable Object[] arguments,
			@Nullable Class<?> targetClass, List<Object> interceptorsAndDynamicMethodMatchers) {

		this.proxy = proxy;
		this.target = target;
		this.targetClass = targetClass;
		this.method = BridgeMethodResolver.findBridgedMethod(method);
		this.arguments = AopProxyUtils.adaptArgumentsIfNecessary(method, arguments);
		this.interceptorsAndDynamicMethodMatchers = interceptorsAndDynamicMethodMatchers;
	}


	@Override
	public final Object getProxy() {
		return this.proxy;
	}

	@Override
	@Nullable
	public final Object getThis() {
		return this.target;
	}

	@Override
	public final AccessibleObject getStaticPart() {
		return this.method;
	}

	@Override
	public final Method getMethod() {
		return this.method;
	}

	@Override
	public final Object[] getArguments() {
		return this.arguments;
	}

	@Override
	public void setArguments(Object... arguments) {
		this.arguments = arguments;
	}


	@Override
	@Nullable
	public Object proceed() throws Throwable {
		// 如果已经执行到拦截器链的最后一个，则直接调用目标方法（Joinpoint）
		if (this.currentInterceptorIndex == this.interceptorsAndDynamicMethodMatchers.size() - 1) {
			return invokeJoinpoint();
		}

		// 获取下一个拦截器或增强器
		Object interceptorOrInterceptionAdvice =
				this.interceptorsAndDynamicMethodMatchers.get(++this.currentInterceptorIndex);

		// 如果是带有动态方法匹配的拦截器（即需要在运行时判断是否匹配的方法）
		if (interceptorOrInterceptionAdvice instanceof InterceptorAndDynamicMethodMatcher) {
			InterceptorAndDynamicMethodMatcher dm =
					(InterceptorAndDynamicMethodMatcher) interceptorOrInterceptionAdvice;
			Class<?> targetClass = (this.targetClass != null ? this.targetClass : this.method.getDeclaringClass());

			// 动态匹配：判断当前方法是否满足增强器的条件
			if (dm.methodMatcher.matches(this.method, targetClass, this.arguments)) {
				// 满足匹配条件，调用当前拦截器
				return dm.interceptor.invoke(this);
			} else {
				// 不匹配，则跳过当前拦截器，继续执行下一个
				return proceed();
			}
		} else {
			// 普通拦截器（无需动态匹配），直接调用
			return ((MethodInterceptor) interceptorOrInterceptionAdvice).invoke(this);
		}
	}


	@Nullable
	protected Object invokeJoinpoint() throws Throwable {
		return AopUtils.invokeJoinpointUsingReflection(this.target, this.method, this.arguments);
	}


	@Override
	public MethodInvocation invocableClone() {
		Object[] cloneArguments = this.arguments;
		if (this.arguments.length > 0) {
			cloneArguments = this.arguments.clone();
		}
		return invocableClone(cloneArguments);
	}

	@Override
	public MethodInvocation invocableClone(Object... arguments) {
		if (this.userAttributes == null) {
			this.userAttributes = new HashMap<>();
		}

		try {
			ReflectiveMethodInvocation clone = (ReflectiveMethodInvocation) clone();
			clone.arguments = arguments;
			return clone;
		}
		catch (CloneNotSupportedException ex) {
			throw new IllegalStateException(
					"Should be able to clone object of type [" + getClass() + "]: " + ex);
		}
	}


	@Override
	public void setUserAttribute(String key, @Nullable Object value) {
		if (value != null) {
			if (this.userAttributes == null) {
				this.userAttributes = new HashMap<>();
			}
			this.userAttributes.put(key, value);
		}
		else {
			if (this.userAttributes != null) {
				this.userAttributes.remove(key);
			}
		}
	}

	@Override
	@Nullable
	public Object getUserAttribute(String key) {
		return (this.userAttributes != null ? this.userAttributes.get(key) : null);
	}

	public Map<String, Object> getUserAttributes() {
		if (this.userAttributes == null) {
			this.userAttributes = new HashMap<>();
		}
		return this.userAttributes;
	}


	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("ReflectiveMethodInvocation: ");
		sb.append(this.method).append("; ");
		if (this.target == null) {
			sb.append("target is null");
		}
		else {
			sb.append("target is of class [").append(this.target.getClass().getName()).append(']');
		}
		return sb.toString();
	}

}
