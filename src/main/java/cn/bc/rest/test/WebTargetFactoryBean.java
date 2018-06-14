package cn.bc.rest.test;

import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.StringUtils;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;

/**
 * 启动 rest 测试服务器并生成客户端测试用的 WebTarget 实例
 *
 * @author dragon 2016-08-02
 */
public class WebTargetFactoryBean implements FactoryBean<WebTarget>, InitializingBean, DisposableBean, ApplicationContextAware {
  private final static Logger logger = LoggerFactory.getLogger(WebTargetFactoryBean.class);
  private String serverPort;              // 单元测试默认使用的 server 端口，默认为 9998
  private String[] componentPackages;     // 要注册的资源、组件包
  private Class<?>[] componentClasses;    // 获取要注册的资源、组件类

  private ApplicationContext context;
  private JerseyTest jerseyTest;
  private WebTarget target;

  static {
    // 将 jersey-test 使用的 java.util.logging 桥接到 slf4j
    // https://jersey.java.net/documentation/latest/logging_chapter.html
    // http://stackoverflow.com/questions/4121722/how-to-make-jersey-to-use-slf4j-instead-of-jul
    java.util.logging.Logger rootLogger = java.util.logging.LogManager.getLogManager().getLogger("");
    java.util.logging.Handler[] handlers = rootLogger.getHandlers();
    for (java.util.logging.Handler handler : handlers) rootLogger.removeHandler(handler);
    org.slf4j.bridge.SLF4JBridgeHandler.install();
  }

  @Override
  public WebTarget getObject() throws Exception {
    return target;
  }

  @Override
  public Class<?> getObjectType() {
    return WebTarget.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }

  @Override
  public void setApplicationContext(ApplicationContext context) throws BeansException {
    this.context = context;
  }

  // spring 完成初始化后再初始化 JerseyTest 实例
  @Override
  public void afterPropertiesSet() throws Exception {
    // We cannot inherit from JerseyTest because it initializes the Application
    // in the constructor before the test application context is initialized.
    // http://stackoverflow.com/questions/24509754/force-jersey-to-read-mocks-from-jerseytest
    jerseyTest = new JerseyTest() {
      @Override
      protected Application configure() {
        // 1. 设置 rest 测试服务器使用的端口
        if (WebTargetFactoryBean.this.getServerPort() != null)
          set(TestProperties.CONTAINER_PORT, WebTargetFactoryBean.this.getServerPort());

        // 据说这个可以让请求、相应的东西一览无余
        enable(TestProperties.LOG_TRAFFIC);
        enable(TestProperties.DUMP_ENTITY);

        // 2. 初始化 ResourceConfig
        ResourceConfig rc = new ResourceConfig();

        // 2.1 注入现有的 spring 上下文，避免 JerseyTest 重新生成
        // see org.glassfish.jersey.server.spring.SpringComponentProvider.createSpringContext()
        rc.property("contextConfig", WebTargetFactoryBean.this.context);

        // 2.2 其它初始化
        WebTargetFactoryBean.this.configure(rc);
        return rc;
      }
    };

    logger.info("setUp jerseyTest instance");
    jerseyTest.setUp();
    target = jerseyTest.target();

    if (logger.isInfoEnabled())
      if (getServerPort() != null) logger.info("custom serverPort = {}", getServerPort());
  }

  @Override
  public void destroy() throws Exception {
    logger.info("destroy jerseyTest instance");
    jerseyTest.tearDown();
  }

  protected void configure(ResourceConfig rc) {
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

    // 注册日志功能，不需要可以去掉: org.glassfish.jersey.logging.LoggingFeature
    //rc.register(LoggingFilter.class);// 仅打印请求响应的 header、URL
    //rc.register(new LoggingFilter(java.util.logging.Logger.getLogger(LoggingFilter.class.getName()), true)); // 打印请求响应的body
    if (logger.isDebugEnabled()) {
      // 打印请求响应的 body (for jersey2.26+)
      // see https://github.com/swagger-api/swagger-codegen/issues/6715
      // see https://github.com/swagger-api/swagger-codegen/commit/2d19776caf4c06cf98627b5a2a228af9a82346b7
      rc.register(new LoggingFeature(java.util.logging.Logger.getLogger(LoggingFeature.DEFAULT_LOGGER_NAME),
        java.util.logging.Level.INFO, LoggingFeature.Verbosity.PAYLOAD_ANY, 1024 * 50 /* Log payloads up to 50K */));
    }
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
  }

  public String getServerPort() {
    return serverPort;
  }

  public void setServerPort(String serverPort) {
    this.serverPort = serverPort;
  }

  public String[] getComponentPackages() {
    return componentPackages;
  }

  public void setComponentPackages(String[] componentPackages) {
    this.componentPackages = componentPackages;
  }

  public Class<?>[] getComponentClasses() {
    return componentClasses;
  }

  public void setComponentClasses(Class<?>[] componentClasses) {
    this.componentClasses = componentClasses;
  }
}