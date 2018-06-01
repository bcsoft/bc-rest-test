package cn.bc.rest.test;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;

import javax.ws.rs.core.Application;

/**
 * 整合 spring 的 JerseyTest 单元测试基类
 * <p>此类已不推荐使用，请使用 cn.bc.rest.test.WebTargetFactoryBean 代替（直接注入 WebTarget 实例到单元测试类中）</p>
 * <p>默认全局注册包 cn.bc 下的所有资源注解类，可以通过复写 initResourceConfig 方法进行改写；
 * 默认的 spring 配置文件名为 spring-test.xml，可以通过复写 getSpringContextConfigLocation 方法进行改写。
 * </p>
 * <p>参考：https://jersey.java.net/documentation/latest/user-guide.html#test-framework</p>
 */
@Deprecated
public abstract class ClassicalJerseySpringTest extends JerseyTest {
  @Override
  protected Application configure() {
    ResourceConfig rc = new ResourceConfig();
    set(TestProperties.CONTAINER_PORT, "9998");    // 设置服务器端口

    enable(TestProperties.LOG_TRAFFIC);            // 这个可以让请求、相应的东西一览无余
    enable(TestProperties.DUMP_ENTITY);

    // 1. spring 配置（需依赖 jersey-spring3 包）
    // 1.1 设置 spring 配置文件的位置
    rc.property("contextConfigLocation", getSpringContextConfigLocation());

    // 1.2 注册 spring 上下文监听器（引入 jersey-spring3 后自动注册）
    //rc.register(SpringLifecycleListener.class);
    //rc.register(RequestContextFilter.class);

    // 2. 注册要测试的资源类|包
    initResourceConfig(rc);

    return rc;
  }

  protected String getSpringContextConfigLocation() {
    return "spring-test.xml";
  }

  protected void initResourceConfig(ResourceConfig rc) {
    rc.setApplicationName("rest");
    rc.packages("cn.bc");               // 默认注册 cn.bc 包
    //rc.register(DemoResource.class);  // 注册资源类
  }
}