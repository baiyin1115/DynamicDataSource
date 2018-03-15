package com.wechat.common.configure.datasource;

import lombok.extern.log4j.Log4j2;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 切换数据源Advice
 */
@Log4j2
@Aspect
@Order(-1) // 保证该AOP在@Transactional之前执行
@Component
public class DynamicDataSourceAspect {

  @Before("@annotation(ds)")
  public void changeDataSource(JoinPoint point, TargetDataSource ds) throws Throwable {
    String dsId = ds.name();
    if (!DynamicDataSourceContextHolder.containsDataSource(dsId)) {
      log.error("数据源[{}]不存在，使用默认数据源 > {}", ds.name(), point.getSignature());
    } else {
      log.debug("Use DataSource : {} > {}", ds.name(), point.getSignature());
      DynamicDataSourceContextHolder.setDataSourceType(ds.name());
    }
  }

  @After("@annotation(ds)")
  public void restoreDataSource(JoinPoint point, TargetDataSource ds) {
    log.debug("Revert DataSource : {} > {}", ds.name(), point.getSignature());
    DynamicDataSourceContextHolder.clearDataSourceType();
  }

}
