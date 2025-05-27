package org.springframework.core.io.support;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.VfsResource;
import org.springframework.lang.Nullable;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.PathMatcher;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * 路径匹配资源模式解析器，实现了 ResourcePatternResolver 接口，
 * 用于支持 Ant 风格的路径模式加载 classpath 或文件系统资源。
 */
public class PathMatchingResourcePatternResolver implements ResourcePatternResolver {

	// 日志记录器
	private static final Log logger = LogFactory.getLog(PathMatchingResourcePatternResolver.class);

	// Equinox OSGi 框架的 FileLocator.resolve 方法引用（可选）
	@Nullable
	private static Method equinoxResolveMethod;

	// 静态初始化块：尝试获取 OSGi 框架中的 FileLocator.resolve 方法
	static {
		try {
			Class<?> fileLocatorClass = ClassUtils.forName("org.eclipse.core.runtime.FileLocator",
					PathMatchingResourcePatternResolver.class.getClassLoader());
			equinoxResolveMethod = fileLocatorClass.getMethod("resolve", URL.class);
			logger.trace("Found Equinox FileLocator for OSGi bundle URL resolution");
		} catch (Throwable ex) {
			// 如果加载失败则设为 null，表示当前不是在 OSGi 环境中
			equinoxResolveMethod = null;
		}
	}

	// 资源加载器（默认使用 DefaultResourceLoader）
	private final ResourceLoader resourceLoader;

	// 路径匹配器（默认使用 AntPathMatcher）
	private PathMatcher pathMatcher = new AntPathMatcher();

	// 默认构造函数，使用默认资源加载器
	public PathMatchingResourcePatternResolver() {
		this.resourceLoader = new DefaultResourceLoader();
	}

	// 使用指定的资源加载器构造解析器
	public PathMatchingResourcePatternResolver(ResourceLoader resourceLoader) {
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		this.resourceLoader = resourceLoader;
	}

	// 通过 ClassLoader 构造对应的 DefaultResourceLoader
	public PathMatchingResourcePatternResolver(@Nullable ClassLoader classLoader) {
		this.resourceLoader = new DefaultResourceLoader(classLoader);
	}

	// 获取资源加载器
	public ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	// 获取类加载器（可为空）
	@Override
	@Nullable
	public ClassLoader getClassLoader() {
		return getResourceLoader().getClassLoader();
	}

	// 设置路径匹配器
	public void setPathMatcher(PathMatcher pathMatcher) {
		Assert.notNull(pathMatcher, "PathMatcher must not be null");
		this.pathMatcher = pathMatcher;
	}

	// 获取路径匹配器
	public PathMatcher getPathMatcher() {
		return this.pathMatcher;
	}

	// 加载单个资源（支持 classpath:, file:, http: 等协议）
	@Override
	public Resource getResource(String location) {
		return getResourceLoader().getResource(location);
	}

	/**
	 * 加载多个资源，支持通配符匹配，如 `classpath*:com/example/**.xml
	 * 资源路径模式
	 */
	@Override
	public Resource[] getResources(String locationPattern) throws IOException {
		Assert.notNull(locationPattern, "Location pattern must not be null");

		// 处理 classpath*: 开头的路径（加载多个 classpath 下资源）
		if (locationPattern.startsWith(CLASSPATH_ALL_URL_PREFIX)) {
			// 是否包含通配符（如 **/*.xml）
			if (getPathMatcher().isPattern(locationPattern.substring(CLASSPATH_ALL_URL_PREFIX.length()))) {
				return findPathMatchingResources(locationPattern);
			} else {
				// 否则只是普通路径，加载所有同名资源
				return findAllClassPathResources(locationPattern.substring(CLASSPATH_ALL_URL_PREFIX.length()));
			}
		} else {
			// 处理其他前缀（file:, http:, jar:, war: 等）
			int prefixEnd = (locationPattern.startsWith("war:") ?
					locationPattern.indexOf("*/") + 1 : locationPattern.indexOf(':') + 1);

			// 是否包含通配符
			if (getPathMatcher().isPattern(locationPattern.substring(prefixEnd))) {
				return findPathMatchingResources(locationPattern);
			} else {
				// 普通文件路径，返回单个资源
				return new Resource[] {getResourceLoader().getResource(locationPattern)};
			}
		}
	}

	/**
	 * 查找 classpath 下所有给定路径的资源（无通配符）
	 */
	protected Resource[] findAllClassPathResources(String location) throws IOException {
		String path = location;
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		Set<Resource> result = doFindAllClassPathResources(path);
		if (logger.isTraceEnabled()) {
			logger.trace("Resolved classpath location [" + location + "] to resources " + result);
		}
		return result.toArray(new Resource[0]);
	}

	/**
	 * 实际查找 classpath 下的所有资源（无通配符）
	 */
	protected Set<Resource> doFindAllClassPathResources(String path) throws IOException {
		Set<Resource> result = new LinkedHashSet<>(16);
		ClassLoader cl = getClassLoader();
		Enumeration<URL> resourceUrls = (cl != null ? cl.getResources(path) : ClassLoader.getSystemResources(path));
		while (resourceUrls.hasMoreElements()) {
			URL url = resourceUrls.nextElement();
			result.add(convertClassLoaderURL(url));
		}

		// 特殊情况：查找根路径时，还需添加 jar 包根目录
		if (!StringUtils.hasLength(path)) {
			addAllClassLoaderJarRoots(cl, result);
		}
		return result;
	}

	/**
	 * 将 ClassLoader 返回的 URL 封装为 Resource
	 */
	protected Resource convertClassLoaderURL(URL url) {
		return new UrlResource(url);
	}


	protected void addAllClassLoaderJarRoots(@Nullable ClassLoader classLoader, Set<Resource> result) {
		// 如果类加载器是 URLClassLoader（通常是应用类加载器或扩展类加载器）
		if (classLoader instanceof URLClassLoader) {
			try {
				// 获取该类加载器加载的所有URL资源
				for (URL url : ((URLClassLoader) classLoader).getURLs()) {
					try {
						// 根据协议构建 UrlResource，兼容普通URL和jar协议URL
						UrlResource jarResource = (ResourceUtils.URL_PROTOCOL_JAR.equals(url.getProtocol()) ?
								new UrlResource(url) :
								new UrlResource(ResourceUtils.JAR_URL_PREFIX + url + ResourceUtils.JAR_URL_SEPARATOR));
						// 若资源存在，则加入结果集
						if (jarResource.exists()) {
							result.add(jarResource);
						}
					} catch (MalformedURLException ex) {
						// URL无法转为有效的jar协议URL时，记录调试日志
						if (logger.isDebugEnabled()) {
							logger.debug("Cannot search for matching files underneath [" + url +
									"] because it cannot be converted to a valid 'jar:' URL: " + ex.getMessage());
						}
					}
				}
			} catch (Exception ex) {
				// 类加载器不支持 getURLs() 方法时，记录调试日志
				if (logger.isDebugEnabled()) {
					logger.debug("Cannot introspect jar files since ClassLoader [" + classLoader +
							"] does not support 'getURLs()': " + ex);
				}
			}
		}

		// 如果当前类加载器是系统类加载器，尝试从 java.class.path 中读取资源
		if (classLoader == ClassLoader.getSystemClassLoader()) {
			addClassPathManifestEntries(result);
		}

		// 递归处理父类加载器
		if (classLoader != null) {
			try {
				addAllClassLoaderJarRoots(classLoader.getParent(), result);
			} catch (Exception ex) {
				// 父类加载器不支持 getParent() 方法时，记录调试日志
				if (logger.isDebugEnabled()) {
					logger.debug("Cannot introspect jar files in parent ClassLoader since [" + classLoader +
							"] does not support 'getParent()': " + ex);
				}
			}
		}
	}

	protected void addClassPathManifestEntries(Set<Resource> result) {
		try {
			// 获取系统属性 java.class.path，包含所有类路径
			String javaClassPathProperty = System.getProperty("java.class.path");
			// 根据路径分隔符拆分多个路径
			for (String path : StringUtils.delimitedListToStringArray(
					javaClassPathProperty, System.getProperty("path.separator"))) {
				try {
					// 获取路径对应的绝对路径
					String filePath = new File(path).getAbsolutePath();
					// 判断路径中是否有冒号，Windows下驱动器路径形式如 C:\xxx
					int prefixIndex = filePath.indexOf(':');
					// 如果冒号位置是1，说明是 Windows 驱动器路径
					if (prefixIndex == 1) {
						// 给路径加上前缀斜杠，变成 /C:\xxx 格式（Linux兼容）
						filePath = "/" + StringUtils.capitalize(filePath);
					}
					// 将路径中的 # 替换为 %23，避免 URL 解析问题
					filePath = StringUtils.replace(filePath, "#", "%23");
					// 组合成 jar URL 格式：jar:file:/xxx.jar!/
					UrlResource jarResource = new UrlResource(ResourceUtils.JAR_URL_PREFIX +
							ResourceUtils.FILE_URL_PREFIX + filePath + ResourceUtils.JAR_URL_SEPARATOR);
					// 如果结果集中没有该资源，且路径不重复且资源存在，则加入结果集
					if (!result.contains(jarResource) && !hasDuplicate(filePath, result) && jarResource.exists()) {
						result.add(jarResource);
					}
				} catch (MalformedURLException ex) {
					// URL格式错误时输出调试日志
					if (logger.isDebugEnabled()) {
						logger.debug("Cannot search for matching files underneath [" + path +
								"] because it cannot be converted to a valid 'jar:' URL: " + ex.getMessage());
					}
				}
			}
		} catch (Exception ex) {
			// 出现异常时输出调试日志
			if (logger.isDebugEnabled()) {
				logger.debug("Failed to evaluate 'java.class.path' manifest entries: " + ex);
			}
		}
	}

	// 判断给定路径是否已经在结果集中存在重复资源
	private boolean hasDuplicate(String filePath, Set<Resource> result) {
		if (result.isEmpty()) {
			return false;
		}
		// 路径去除或添加前缀斜杠，用于构造另一种路径形式，避免重复判断时遗漏
		String duplicatePath = (filePath.startsWith("/") ? filePath.substring(1) : "/" + filePath);
		try {
			// 创建对应的 UrlResource 并判断结果集中是否已存在
			return result.contains(new UrlResource(ResourceUtils.JAR_URL_PREFIX + ResourceUtils.FILE_URL_PREFIX +
					duplicatePath + ResourceUtils.JAR_URL_SEPARATOR));
		} catch (MalformedURLException ex) {
			return false;
		}
	}

	// 根据路径模式查找匹配的资源，支持 jar、文件系统、vfs 等协议
	protected Resource[] findPathMatchingResources(String locationPattern) throws IOException {
		// 获取路径的根目录部分
		String rootDirPath = determineRootDir(locationPattern);
		// 获取根目录之后的子路径匹配模式
		String subPattern = locationPattern.substring(rootDirPath.length());
		// 获取根目录资源，可能是多个
		Resource[] rootDirResources = getResources(rootDirPath);
		// 结果集使用 LinkedHashSet 保持顺序且无重复
		Set<Resource> result = new LinkedHashSet<>(64);
		for (Resource rootDirResource : rootDirResources) {
			// 解析根目录资源，默认返回自身
			rootDirResource = resolveRootDirResource(rootDirResource);
			URL rootDirUrl = rootDirResource.getURL();
			// 如果是 Equinox OSGi 环境的 bundle URL，则调用特殊方法转换为标准 URL
			if (equinoxResolveMethod != null && rootDirUrl.getProtocol().startsWith("bundle")) {
				URL resolvedUrl = (URL) ReflectionUtils.invokeMethod(equinoxResolveMethod, null, rootDirUrl);
				if (resolvedUrl != null) {
					rootDirUrl = resolvedUrl;
				}
				rootDirResource = new UrlResource(rootDirUrl);
			}
			// 如果是 VFS 协议，委托 VFS 资源匹配工具类查找
			if (rootDirUrl.getProtocol().startsWith(ResourceUtils.URL_PROTOCOL_VFS)) {
				result.addAll(VfsResourceMatchingDelegate.findMatchingResources(rootDirUrl, subPattern, getPathMatcher()));
			}
			// 如果是 jar URL 或者资源本身是 jar 包资源，调用 jar 包资源查找方法
			else if (ResourceUtils.isJarURL(rootDirUrl) || isJarResource(rootDirResource)) {
				result.addAll(doFindPathMatchingJarResources(rootDirResource, rootDirUrl, subPattern));
			}
			// 否则按照文件系统资源查找
			else {
				result.addAll(doFindPathMatchingFileResources(rootDirResource, subPattern));
			}
		}
		// 记录匹配结果的调试日志
		if (logger.isTraceEnabled()) {
			logger.trace("Resolved location pattern [" + locationPattern + "] to resources " + result);
		}
		return result.toArray(new Resource[0]);
	}

	// 确定给定路径模式的根目录部分（不含通配符部分）
	protected String determineRootDir(String location) {
		int prefixEnd = location.indexOf(':') + 1;  // 例如 file: 的结束位置
		int rootDirEnd = location.length();
		// 从后往前寻找路径中的第一个非通配符位置
		while (rootDirEnd > prefixEnd && getPathMatcher().isPattern(location.substring(prefixEnd, rootDirEnd))) {
			rootDirEnd = location.lastIndexOf('/', rootDirEnd - 2) + 1;
		}
		if (rootDirEnd == 0) {
			rootDirEnd = prefixEnd;
		}
		return location.substring(0, rootDirEnd);
	}

	// 解析根目录资源，默认直接返回传入资源
	protected Resource resolveRootDirResource(Resource original) throws IOException {
		return original;
	}

	// 判断资源是否是 jar 包资源，默认返回 false
	protected boolean isJarResource(Resource resource) throws IOException {
		return false;
	}

	// 在 jar 包中查找匹配子路径的资源
	protected Set<Resource> doFindPathMatchingJarResources(Resource rootDirResource, URL rootDirURL, String subPattern)
			throws IOException {

		URLConnection con = rootDirURL.openConnection();
		JarFile jarFile;
		String jarFileUrl;
		String rootEntryPath;
		boolean closeJarFile;

		if (con instanceof JarURLConnection) {
			JarURLConnection jarCon = (JarURLConnection) con;
			ResourceUtils.useCachesIfNecessary(jarCon);
			jarFile = jarCon.getJarFile();
			jarFileUrl = jarCon.getJarFileURL().toExternalForm();
			JarEntry jarEntry = jarCon.getJarEntry();
			rootEntryPath = (jarEntry != null ? jarEntry.getName() : "");
			closeJarFile = !jarCon.getUseCaches();
		} else {
			String urlFile = rootDirURL.getFile();
			try {
				int separatorIndex = urlFile.indexOf(ResourceUtils.WAR_URL_SEPARATOR);
				if (separatorIndex == -1) {
					separatorIndex = urlFile.indexOf(ResourceUtils.JAR_URL_SEPARATOR);
				}
				if (separatorIndex != -1) {
					jarFileUrl = urlFile.substring(0, separatorIndex);
					rootEntryPath = urlFile.substring(separatorIndex + 2);
					jarFile = getJarFile(jarFileUrl);
				} else {
					jarFile = new JarFile(urlFile);
					jarFileUrl = urlFile;
					rootEntryPath = "";
				}
				closeJarFile = true;
			} catch (ZipException ex) {
				// 跳过无效的 jar 包
				if (logger.isDebugEnabled()) {
					logger.debug("Skipping invalid jar classpath entry [" + urlFile + "]");
				}
				return Collections.emptySet();
			}
		}

		try {
			// 记录查找 jar 包资源的详细日志
			if (logger.isTraceEnabled()) {
				logger.trace("Looking for matching resources in jar file [" + jarFileUrl + "]");
			}
			// 确保根路径以斜杠结尾
			if (StringUtils.hasLength(rootEntryPath) && !rootEntryPath.endsWith("/")) {
				rootEntryPath = rootEntryPath + "/";
			}
			Set<Resource> result = new LinkedHashSet<>(64);
			// 遍历 jar 包中所有条目
			for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements(); ) {
				JarEntry entry = entries.nextElement();
				String entryPath = entry.getName();
				// 如果条目路径匹配根目录路径
				if (entryPath.startsWith(rootEntryPath)) {
					// 计算相对路径
					String relativePath = entryPath.substring(rootEntryPath.length());
					// 匹配子路径模式，符合则加入结果
					if (getPathMatcher().match(subPattern, relativePath)) {
						result.add(rootDirResource.createRelative(relativePath));
					}
				}
			}
			return result;
		} finally {
			if (closeJarFile) {
				jarFile.close();
			}
		}
	}

	protected JarFile getJarFile(String jarFileUrl) throws IOException {
		// 如果路径以 file: 开头，说明是文件资源，转换成URI处理
		if (jarFileUrl.startsWith(ResourceUtils.FILE_URL_PREFIX)) {
			try {
				// 尝试将路径转换成URI，获取其具体路径部分并创建JarFile
				return new JarFile(ResourceUtils.toURI(jarFileUrl).getSchemeSpecificPart());
			} catch (URISyntaxException ex) {
				// URI转换失败，直接去掉 file: 前缀后创建JarFile
				return new JarFile(jarFileUrl.substring(ResourceUtils.FILE_URL_PREFIX.length()));
			}
		} else {
			// 非文件协议，直接用路径创建JarFile
			return new JarFile(jarFileUrl);
		}
	}

	protected Set<Resource> doFindPathMatchingFileResources(Resource rootDirResource, String subPattern)
			throws IOException {

		File rootDir;
		try {
			// 尝试从资源中获取对应的文件对象
			rootDir = rootDirResource.getFile().getAbsoluteFile();
		} catch (FileNotFoundException ex) {
			// 文件没找到，打印调试日志并返回空集合
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot search for matching files underneath " + rootDirResource +
						" in the file system: " + ex.getMessage());
			}
			return Collections.emptySet();
		} catch (Exception ex) {
			// 其他异常，打印信息日志并返回空集合
			if (logger.isInfoEnabled()) {
				logger.info("Failed to resolve " + rootDirResource + " in the file system: " + ex);
			}
			return Collections.emptySet();
		}
		// 递归查找匹配的文件资源
		return doFindMatchingFileSystemResources(rootDir, subPattern);
	}

	protected Set<Resource> doFindMatchingFileSystemResources(File rootDir, String subPattern) throws IOException {
		if (logger.isTraceEnabled()) {
			// 跟踪日志，打印正在查找的目录路径
			logger.trace("Looking for matching resources in directory tree [" + rootDir.getPath() + "]");
		}
		// 调用递归方法查找所有匹配的文件
		Set<File> matchingFiles = retrieveMatchingFiles(rootDir, subPattern);
		Set<Resource> result = new LinkedHashSet<>(matchingFiles.size());
		// 将匹配到的文件封装成文件系统资源集合返回
		for (File file : matchingFiles) {
			result.add(new FileSystemResource(file));
		}
		return result;
	}

	protected Set<File> retrieveMatchingFiles(File rootDir, String pattern) throws IOException {
		if (!rootDir.exists()) {
			// 如果根目录不存在，记录调试日志，返回空集合
			if (logger.isDebugEnabled()) {
				logger.debug("Skipping [" + rootDir.getAbsolutePath() + "] because it does not exist");
			}
			return Collections.emptySet();
		}
		if (!rootDir.isDirectory()) {
			// 如果不是目录，记录信息日志，返回空集合
			if (logger.isInfoEnabled()) {
				logger.info("Skipping [" + rootDir.getAbsolutePath() + "] because it does not denote a directory");
			}
			return Collections.emptySet();
		}
		if (!rootDir.canRead()) {
			// 如果目录不可读，记录信息日志，返回空集合
			if (logger.isInfoEnabled()) {
				logger.info("Skipping search for matching files underneath directory [" + rootDir.getAbsolutePath() +
						"] because the application is not allowed to read the directory");
			}
			return Collections.emptySet();
		}
		// 构造完整匹配模式，将文件路径分隔符统一成 /
		String fullPattern = StringUtils.replace(rootDir.getAbsolutePath(), File.separator, "/");
		if (!pattern.startsWith("/")) {
			fullPattern += "/";
		}
		fullPattern = fullPattern + StringUtils.replace(pattern, File.separator, "/");
		Set<File> result = new LinkedHashSet<>(8);
		// 递归查找符合匹配模式的文件
		doRetrieveMatchingFiles(fullPattern, rootDir, result);
		return result;
	}

	protected void doRetrieveMatchingFiles(String fullPattern, File dir, Set<File> result) throws IOException {
		if (logger.isTraceEnabled()) {
			// 跟踪日志，打印当前搜索目录和匹配模式
			logger.trace("Searching directory [" + dir.getAbsolutePath() +
					"] for files matching pattern [" + fullPattern + "]");
		}
		for (File content : listDirectory(dir)) {
			// 将当前文件绝对路径分隔符替换为 /
			String currPath = StringUtils.replace(content.getAbsolutePath(), File.separator, "/");
			// 如果是目录且路径部分匹配模式起始，则递归搜索该目录
			if (content.isDirectory() && getPathMatcher().matchStart(fullPattern, currPath + "/")) {
				if (!content.canRead()) {
					// 目录不可读时记录调试日志，跳过
					if (logger.isDebugEnabled()) {
						logger.debug("Skipping subdirectory [" + dir.getAbsolutePath() +
								"] because the application is not allowed to read the directory");
					}
				} else {
					// 递归调用继续查找
					doRetrieveMatchingFiles(fullPattern, content, result);
				}
			}
			// 文件路径匹配模式，则加入结果集合
			if (getPathMatcher().match(fullPattern, currPath)) {
				result.add(content);
			}
		}
	}

	protected File[] listDirectory(File dir) {
		// 获取目录下所有文件和子目录
		File[] files = dir.listFiles();
		if (files == null) {
			// 目录内容为空或不可访问，记录信息日志
			if (logger.isInfoEnabled()) {
				logger.info("Could not retrieve contents of directory [" + dir.getAbsolutePath() + "]");
			}
			return new File[0];
		}
		// 对文件名排序，保证顺序一致性
		Arrays.sort(files, Comparator.comparing(File::getName));
		return files;
	}


	private static class VfsResourceMatchingDelegate {

		public static Set<Resource> findMatchingResources(
				URL rootDirURL, String locationPattern, PathMatcher pathMatcher) throws IOException {

			// 通过VFS工具获取根资源对象
			Object root = VfsPatternUtils.findRoot(rootDirURL);
			// 创建资源访问者，传入根路径、匹配模式和路径匹配器
			PatternVirtualFileVisitor visitor =
					new PatternVirtualFileVisitor(VfsPatternUtils.getPath(root), locationPattern, pathMatcher);
			// 遍历VFS虚拟文件系统，访问符合条件的资源
			VfsPatternUtils.visit(root, visitor);
			// 返回匹配到的资源集合
			return visitor.getResources();
		}
	}


	@SuppressWarnings("unused")
	private static class PatternVirtualFileVisitor implements InvocationHandler {

		// 子匹配模式
		private final String subPattern;

		// 路径匹配器
		private final PathMatcher pathMatcher;

		// 根路径，确保以 / 结尾
		private final String rootPath;

		// 匹配到的资源集合，初始容量64
		private final Set<Resource> resources = new LinkedHashSet<>(64);

		public PatternVirtualFileVisitor(String rootPath, String subPattern, PathMatcher pathMatcher) {
			this.subPattern = subPattern;
			this.pathMatcher = pathMatcher;
			this.rootPath = (rootPath.isEmpty() || rootPath.endsWith("/") ? rootPath : rootPath + "/");
		}

		@Override
		@Nullable
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			String methodName = method.getName();
			// 处理Object类自身的方法
			if (Object.class == method.getDeclaringClass()) {
				if (methodName.equals("equals")) {
					return (proxy == args[0]);
				} else if (methodName.equals("hashCode")) {
					return System.identityHashCode(proxy);
				}
			} else if ("getAttributes".equals(methodName)) {
				// 获取访问者属性
				return getAttributes();
			} else if ("visit".equals(methodName)) {
				// 访问资源节点
				visit(args[0]);
				return null;
			} else if ("toString".equals(methodName)) {
				// 转字符串描述
				return toString();
			}
			// 不支持的方法抛异常
			throw new IllegalStateException("Unexpected method invocation: " + method);
		}

		// 访问VFS资源，判断路径是否匹配子模式，匹配则加入资源集合
		public void visit(Object vfsResource) {
			if (this.pathMatcher.match(this.subPattern,
					VfsPatternUtils.getPath(vfsResource).substring(this.rootPath.length()))) {
				this.resources.add(new VfsResource(vfsResource));
			}
		}

		@Nullable
		public Object getAttributes() {
			// 获取访问者属性（VFS相关）
			return VfsPatternUtils.getVisitorAttributes();
		}

		public Set<Resource> getResources() {
			// 返回匹配资源集合
			return this.resources;
		}

		public int size() {
			// 返回匹配资源数量
			return this.resources.size();
		}

		@Override
		public String toString() {
			return "sub-pattern: " + this.subPattern + ", resources: " + this.resources;
		}
	}

}
