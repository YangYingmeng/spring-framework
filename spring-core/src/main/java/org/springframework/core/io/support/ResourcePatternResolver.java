package org.springframework.core.io.support;

import java.io.IOException;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * 资源模式解析器接口。
 * 该接口继承自 {@link ResourceLoader}，增加了根据位置模式（例如 Ant 风格的路径模式）
 * 解析资源的方法。
 * 实现类应支持基于路径模式匹配资源，比如扫描类路径或者文件系统中的资源。
 */
public interface ResourcePatternResolver extends ResourceLoader {

	/**
	 * 表示搜索所有类路径下资源的前缀。
	 * 以此前缀开头的位置模式表示在所有类路径中查找匹配的资源，
	 * 而不仅仅是查找单个资源。
	 */
	String CLASSPATH_ALL_URL_PREFIX = "classpath*:";

	/**
	 * 根据给定的位置模式解析出匹配的资源数组。
	 * 位置模式可以是一个简单的资源路径，也可以是带有通配符的模式（如 Ant 风格路径）。
	 * @param locationPattern 需要解析的资源位置模式
	 * @return 匹配到的资源数组
	 * @throws IOException 资源解析过程中发生的输入输出异常
	 */
	Resource[] getResources(String locationPattern) throws IOException;

}
