package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aopalliance.intercept.Interceptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionAwareMethodMatcher;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.lang.Nullable;

@SuppressWarnings("serial")
public class DefaultAdvisorChainFactory implements AdvisorChainFactory, Serializable {

	/**
	 * 获取方法调用对应的拦截器链（包含增强逻辑）
	 */
	@Override
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice(
			Advised config, Method method, @Nullable Class<?> targetClass) {

		// 获取全局的增强适配器注册器
		AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();
		// 获取配置中所有的 Advisor（增强器） 这里的advisors已经是排序好的 拦截器链需要知道先后顺序
		Advisor[] advisors = config.getAdvisors();
		// 创建用于存放匹配的拦截器列表
		List<Object> interceptorList = new ArrayList<>(advisors.length);
		// 获取实际使用的目标类
		Class<?> actualClass = (targetClass != null ? targetClass : method.getDeclaringClass());
		Boolean hasIntroductions = null;

		// 遍历所有增强器
		for (Advisor advisor : advisors) {

			// 处理切面增强器（带切点）
			if (advisor instanceof PointcutAdvisor) {
				PointcutAdvisor pointcutAdvisor = (PointcutAdvisor) advisor;

				// 若已预过滤或类过滤器匹配当前类
				if (config.isPreFiltered() || pointcutAdvisor.getPointcut().getClassFilter().matches(actualClass)) {
					MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();
					boolean match;

					// 方法匹配器支持引介增强感知
					if (mm instanceof IntroductionAwareMethodMatcher) {
						if (hasIntroductions == null) {
							// 检查是否存在匹配的引介增强
							hasIntroductions = hasMatchingIntroductions(advisors, actualClass);
						}
						match = ((IntroductionAwareMethodMatcher) mm).matches(method, actualClass, hasIntroductions);
					} else {
						match = mm.matches(method, actualClass);
					}

					// 若方法匹配成功，获取对应的拦截器
					if (match) {
						MethodInterceptor[] interceptors = registry.getInterceptors(advisor);

						// 是否为运行时匹配（需传入方法参数）
						if (mm.isRuntime()) {
							for (MethodInterceptor interceptor : interceptors) {
								interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
							}
						} else {
							// 直接添加静态匹配的拦截器
							interceptorList.addAll(Arrays.asList(interceptors));
						}
					}
				}
			}

			// 处理引介增强器（用于添加接口默认实现）
			else if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
					Interceptor[] interceptors = registry.getInterceptors(advisor);
					interceptorList.addAll(Arrays.asList(interceptors));
				}
			}

			// 处理其他通用增强器（无切点）
			else {
				Interceptor[] interceptors = registry.getInterceptors(advisor);
				interceptorList.addAll(Arrays.asList(interceptors));
			}
		}

		return interceptorList;
	}

	/**
	 * 判断是否存在匹配的引介增强（用于 IntroductionAwareMethodMatcher）
	 */
	private static boolean hasMatchingIntroductions(Advisor[] advisors, Class<?> actualClass) {
		for (Advisor advisor : advisors) {
			if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (ia.getClassFilter().matches(actualClass)) {
					return true;
				}
			}
		}
		return false;
	}
}
