package org.springframework.core.io;

import org.springframework.lang.Nullable;
import org.springframework.util.ResourceUtils;

/**
 * 资源加载器接口，是 Spring 中统一的资源加载抽象。
 * <p>所有的 ApplicationContext 实现默认都实现了该接口，
 * 提供从类路径、文件系统、URL 等位置加载资源的能力。
 */
public interface ResourceLoader {

	/**
	 * 类路径前缀：用于指示资源位于 classpath 中的标识前缀，如 "classpath:"
	 */
	String CLASSPATH_URL_PREFIX = ResourceUtils.CLASSPATH_URL_PREFIX;

	/**
	 * 根据给定的资源路径 location 返回对应的 Resource 句柄。
	 * <p>支持多种路径格式：
	 * <ul>
	 *   <li>类路径（以 "classpath:" 开头）</li>
	 *   <li>文件路径（绝对或相对）</li>
	 *   <li>URL（如 "file:", "http:", "https:"）</li>
	 * </ul>
	 *
	 * @param location 资源路径
	 * @return 表示资源的 {@link Resource} 对象（不会返回 null）
	 */
	Resource getResource(String location);

	/**
	 * 返回用于加载资源的类加载器（通常用于加载 classpath 中的资源）。
	 *
	 * @return 可用于资源加载的 {@link ClassLoader} 实例，允许为 null（表示使用默认类加载器）
	 */
	@Nullable
	ClassLoader getClassLoader();
}

