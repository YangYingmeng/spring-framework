package org.springframework.aop.framework;

import java.lang.reflect.Method;
import java.util.List;

import org.springframework.lang.Nullable;

/**
 * Advisor 拦截器链工厂接口，用于根据指定方法和目标类生成对应的拦截器链（Advice 列表）。
 * 该接口通常由 Spring AOP 框架的内部类实现。
 */
public interface AdvisorChainFactory {

	/**
	 * 根据配置、目标方法和目标类，获取拦截器链（包含适配后的 Advice）。
	 */
	List<Object> getInterceptorsAndDynamicInterceptionAdvice(Advised config, Method method, @Nullable Class<?> targetClass);

}

