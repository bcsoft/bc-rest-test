package cn.bc.rest.test;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.StringUtils;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import java.util.logging.Handler;


/**
 * 整合 spring 的 JerseyTest 单元测试基类
 * <p>此类已不推荐使用，请使用 cn.bc.rest.test.WebTargetFactoryBean 代替（直接注入 WebTarget 实例到单元测试类中）</p>
 * <p>子类需实现 getComponentClasses 方法来定义要注册的资源、组件类，可以通过复写 getServerPort 方法改写默认的服务器端口。</p>
 * <p>use the jersey-spring3-xxx.jar!/jersey-spring-applicationContext.xml in order to include jersey specific spring configuration</p>
 * <p>http://stackoverflow.com/questions/24509754/force-jersey-to-read-mocks-from-jerseytest</p>
 * <p>https://jersey.java.net/documentation/latest/user-guide.html#test-framework</p>
 */
@Deprecated
@ContextConfiguration(locations = "classpath:jersey-spring-applicationContext.xml")
public abstract class JerseySpringTest {
	private final static Logger logger = LoggerFactory.getLogger(JerseySpringTest.class);
	protected JerseyTest jerseyTest;

	public final WebTarget target(final String path) {
		return jerseyTest.target(path);
	}

	@BeforeClass
	public static void initLogger() {
		// 将 jersey-test 使用的 java.util.logging 桥接到 slf4j
		// https://jersey.java.net/documentation/latest/logging_chapter.html
		// http://stackoverflow.com/questions/4121722/how-to-make-jersey-to-use-slf4j-instead-of-jul
		java.util.logging.Logger rootLogger = java.util.logging.LogManager.getLogManager().getLogger("");
		java.util.logging.Handler[] handlers = rootLogger.getHandlers();
		for (Handler handler : handlers) rootLogger.removeHandler(handler);
		org.slf4j.bridge.SLF4JBridgeHandler.install();
	}

	@Before
	public void setup() throws Exception {
		jerseyTest.setUp();
	}

	@After
	public void tearDown() throws Exception {
		jerseyTest.tearDown();
	}

	@Autowired
	public void setApplicationContext(final ApplicationContext context) {
		// We cannot inherit from JerseyTest because it initializes the Application
		// in the constructor before the test application context is initialized.
		// http://stackoverflow.com/questions/24509754/force-jersey-to-read-mocks-from-jerseytest
		jerseyTest = new JerseyTest() {
			@Override
			protected Application configure() {
				// 1. 设置服务器端口
				if (getServerPort() != null) set(TestProperties.CONTAINER_PORT, getServerPort());

				// 据说这个可以让请求、相应的东西一览无余
				enable(TestProperties.LOG_TRAFFIC);
				enable(TestProperties.DUMP_ENTITY);

				// 2. 初始化 ResourceConfig
				ResourceConfig rc = new ResourceConfig();

				// 2.1 注入现有的 spring 上下文，避免 JerseyTest 重新生成
				// see org.glassfish.jersey.server.spring.SpringComponentProvider.createSpringContext()
				rc.property("contextConfig", context);

				// 2.2 其它初始化
				JerseySpringTest.this.configure(rc);
				return rc;
			}
		};
	}

	/**
	 * 单元测试默认使用的 server 端口，默认 9998
	 */
	protected String getServerPort() {
		return null;
	}

	/**
	 * JerseyTest 的 ResourceConfig 配置
	 */
	protected void configure(ResourceConfig rc) {
		//logger.info("------test java.util.logging.Logger");

		//  注册使用 JAX-RS 注解的类：在子类中实现 configure(ResourceConfig rc) 方法进行注册
		//rc.packages("cn.bc");               // 默认注册 cn.bc 包
		//rc.register(DemoResource.class);  // 注册资源类

		// 注册日志功能，不需要可以去掉: org.glassfish.jersey.logging.LoggingFeature
		//rc.register(LoggingFilter.class);// 仅打印请求响应的 header、URL
		//rc.register(new LoggingFilter(Logger.getLogger(LoggingFilter.class.getName()), true)); // 打印请求响应的body
		//rc.register(LoggingFeature.class); // LoggingFeature 测试不成功 2016-07-29 by dragon
		//rc.property(LoggingFeature.DEFAULT_LOGGER_LEVEL, Level.FINEST.getName());
		//rc.property(LoggingFeature.LOGGING_FEATURE_LOGGER_LEVEL, Level.FINEST.getName());
		//rc.property(LoggingFeature.LOGGING_FEATURE_LOGGER_LEVEL_CLIENT, Level.FINEST.getName());
		//rc.property(LoggingFeature.LOGGING_FEATURE_LOGGER_LEVEL_SERVER, Level.FINEST.getName());
		//rc.property(LoggingFeature.LOGGING_FEATURE_VERBOSITY, LoggingFeature.Verbosity.PAYLOAD_ANY);
		//rc.register(new LoggingFeature(logger, Level.ALL, LoggingFeature.Verbosity.PAYLOAD_ANY, null));

		// 注册文件上传功能，不需要可以去掉
		//rc.register(MultiPartFeature.class);

		// 使用fastJson作为Json序列化工具，不需要可以去掉，使用官方提供的jackson工具
		//rc.register(FastJsonFeature.class);

		// @Inject @Resource DI注入: http://blog.csdn.net/soslinken/article/details/51668509
		//rc.register(new InjectComponent());

		// 异常处理，不需要可以去掉
		//rc.register(ExceptionMappingResource.class);

		// 注册默认 utf-8 编码的过滤器
		//rc.register(CharsetResponseFilter.class);

		// 注册登录认证过滤器
		// rc.register(AuthRequestFilter.class, Priorities.AUTHENTICATION);


		// 注册资源、组件类
		Class<?>[] componentClasses = getComponentClasses();
		if (componentClasses != null) {
			if (logger.isInfoEnabled())
				logger.info("注册资源、组件类：{}", StringUtils.arrayToCommaDelimitedString(componentClasses));
			for (Class<?> resource : componentClasses) rc.register(resource);
		}

		// 注册资源、组件包
		String[] packages = getComponentPackages();
		if (packages != null) {
			if (logger.isInfoEnabled())
				logger.info("注册资源、组件包：{}", StringUtils.arrayToCommaDelimitedString(packages));
			for (String p : packages) rc.packages(p);
		}
	}


	/**
	 * 获取要注册的资源、组件包
	 */
	protected String[] getComponentPackages() {
		return null;
	}

	/**
	 * 获取要注册的资源、组件类
	 */
	protected abstract Class<?>[] getComponentClasses();
}