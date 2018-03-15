package com.wechat.common.configure.datasource;

import lombok.extern.log4j.Log4j2;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.boot.bind.RelaxedDataBinder;
import org.springframework.boot.bind.RelaxedPropertyResolver;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

/**
 * 动态数据源注册<br/>
 * 启动动态数据源请在启动类中（如Application）
 * 添加 @Import(DynamicDataSourceRegister.class)
 */
@Log4j2
public class DynamicDataSourceRegister implements ImportBeanDefinitionRegistrar, EnvironmentAware {

  // 如配置文件中未指定数据源类型，使用该默认值
  // private static final Object DATASOURCE_TYPE_DEFAULT = "org.apache.tomcat.jdbc.pool.DataSource";
  private static final Object DATASOURCE_TYPE_DEFAULT = "com.zaxxer.hikari.HikariDataSource";
  private ConversionService conversionService = new DefaultConversionService();
  //  private PropertyValues dataSourcePropertyValues;
  // 数据源
  private DataSource defaultDataSource;
  private Map<String, DataSource> customDataSources = new HashMap<>();

  @Override
  public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
    Map<Object, Object> targetDataSources = new HashMap<Object, Object>();
    // 将主数据源添加到更多数据源中
    targetDataSources.put("dataSource", defaultDataSource);
    DynamicDataSourceContextHolder.dataSourceIds.add("dataSource");
    // 添加更多数据源
    targetDataSources.putAll(customDataSources);
    for (String key : customDataSources.keySet()) {
      DynamicDataSourceContextHolder.dataSourceIds.add(key);
    }

    // 创建DynamicDataSource
    GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
    beanDefinition.setBeanClass(DynamicDataSource.class);
    beanDefinition.setSynthetic(true);
    MutablePropertyValues mpv = beanDefinition.getPropertyValues();
    mpv.addPropertyValue("defaultTargetDataSource", defaultDataSource);
    mpv.addPropertyValue("targetDataSources", targetDataSources);
    registry.registerBeanDefinition("dataSource", beanDefinition);

    log.info("Dynamic DataSource Registry");
  }

  /**
   * 创建DataSource
   */
  @SuppressWarnings("unchecked")
  public DataSource buildDataSource(Map<String, Object> dsMap) {
    try {
      Object type = dsMap.get("type");
      if (type == null)
        type = DATASOURCE_TYPE_DEFAULT;// 默认DataSource

      Class<? extends DataSource> dataSourceType;
      dataSourceType = (Class<? extends DataSource>) Class.forName((String) type);
      log.info(dsMap);
//      String driverClassName = dsMap.get("driver-class-name").toString();
      String driverClassName = dsMap.get("driver-class").toString();
      String url = dsMap.get("url").toString();
      String username = dsMap.get("username").toString();
      String password = dsMap.get("password").toString();

      DataSourceBuilder factory = DataSourceBuilder.create().driverClassName(driverClassName).url(url)
          .username(username).password(password).type(dataSourceType);
      return factory.build();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * 加载多数据源配置
   */
  @Override
  public void setEnvironment(Environment env) {
    initDefaultDataSource(env);
    initCustomDataSources(env);
  }

  /**
   * 初始化主数据源
   */
  private void initDefaultDataSource(Environment env) {
    // 读取主数据源
    Map<String, Object> rpr = new RelaxedPropertyResolver(env, "spring.datasource").getSubProperties(".");
    Map<String, Object> dsMap = new HashMap<>();
    dsMap.put("type", rpr.get("type"));
    dsMap.put("driver-class", rpr.get("driver-class"));
    dsMap.put("url", rpr.get("url"));
    dsMap.put("username", rpr.get("username"));
    dsMap.put("password", rpr.get("password"));

    defaultDataSource = buildDataSource(dsMap);

    dataBinder(defaultDataSource, rpr);
  }

  /**
   * 为DataSource绑定更多数据
   */
  private void dataBinder(DataSource dataSource, Map<String, Object> rpr) {
    RelaxedDataBinder dataBinder = new RelaxedDataBinder(dataSource);
    // dataBinder.setValidator(new LocalValidatorFactory().run(this.applicationContext));
    dataBinder.setConversionService(conversionService);
    dataBinder.setIgnoreNestedProperties(false);// false
    dataBinder.setIgnoreInvalidFields(false);// false
    dataBinder.setIgnoreUnknownFields(true);// true

    Map<String, Object> values = new HashMap<>(rpr);
    // 排除已经设置的属性
    values.remove("type");
    values.remove("driver-class");
    values.remove("url");
    values.remove("username");
    values.remove("password");

    //设置缓存信息
    Boolean isCache = (Boolean) values.get("cachePrepStmts");
    if (isCache != null && isCache) {
      Properties dataSourceProperties = new Properties();
      dataSourceProperties.put("cachePrepStmts", values.get("cachePrepStmts"));
      dataSourceProperties.put("prepStmtCacheSize", values.get("prepStmtCacheSize"));
      dataSourceProperties.put("prepStmtCacheSqlLimit", values.get("prepStmtCacheSqlLimit"));

      values.remove("cachePrepStmts");
      values.remove("prepStmtCacheSize");
      values.remove("prepStmtCacheSqlLimit");
      values.put("dataSourceProperties", dataSourceProperties);
    }

    PropertyValues dataSourcePropertyValues = new MutablePropertyValues(values);

    //注意这里，并不是springboot的方式注入属性信息
    dataBinder.bind(dataSourcePropertyValues);
  }

  /**
   * 初始化更多数据源
   */
  private void initCustomDataSources(Environment env) {
    // 读取配置文件获取更多数据源，也可以通过defaultDataSource读取数据库获取更多数据源
    RelaxedPropertyResolver propertyResolver = new RelaxedPropertyResolver(env, "custom.datasource.");
    String dsPrefixs = propertyResolver.getProperty("names");
    for (String dsPrefix : dsPrefixs.split(",")) {// 多个数据源
      Map<String, Object> dsMap = propertyResolver.getSubProperties(dsPrefix + ".");
      DataSource ds = buildDataSource(dsMap);
      dataBinder(ds, dsMap);

      customDataSources.put(dsPrefix, ds);

    }
  }

}
