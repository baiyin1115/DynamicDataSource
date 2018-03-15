# 负责项目最近踩过的坑的总结

### 网上copy demo的兄弟，一定要理解以后再整啊，随便一个坑，都得读源码，搞了好几天。

#### 1 动态数据源的例子可参考这哥们儿的文档，我们是直接copy过来用的，结果换了个数据源，压测出问题了。例子说明可参考：http://blog.csdn.net/catoop/article/details/50575038 

##### 需要在pom文件里面添加如下：

        <dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-jdbc</artifactId>
			<exclusions>
				<exclusion>
					<groupId>org.apache.tomcat</groupId>
					<artifactId>tomcat-jdbc</artifactId>
				</exclusion>
			</exclusions>
		</dependency>
		<dependency>
			<groupId>com.zaxxer</groupId>
			<artifactId>HikariCP</artifactId>
			<version>${HikariCP.version}</version>
		</dependency>


##### Application代码里面添加：
@Import({DynamicDataSourceRegister.class}) // 注册动态多数据源
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}

##### yml文件里面添加：

##### 注意这里，如果你用springboot方式配置hikari，并不会生效，因为数据库参数这个例子中是用 RelaxedDataBinder绑定的
  datasource:
    url: jdbc:log4jdbc:oracle:thin:@//XXXXX:1521/XXXXX
    username: XXXX
    password: XXXX
    driver-class: net.sf.log4jdbc.sql.jdbcapi.DriverSpy
    type: com.zaxxer.hikari.HikariDataSource
// 由于系统是通过DynamicDataSourceRegister注入的，以下参数这么写并不会生效
//    hikari:
//      connection-timeout: 60000
//      maximum-pool-size: 5
    poolName: HikariCP_main
    minimumIdle: 5
    maximumPoolSize: 20
    connectionTimeout: 60000
    cachePrepStmts: true
    prepStmtCacheSize: 250
    prepStmtCacheSqlLimit: 2048


##### HikariCP 可以参考github：
https://github.com/brettwooldridge/HikariCP

##### 注意代码里面两个坑爹的地方：
参数命名是这个
   private volatile int maxPoolSize;
   private volatile int minIdle;

get/set方法是这个
   /** {@inheritDoc} */
   @Override
   public int getMaximumPoolSize()
   {
      return maxPoolSize;
   }

   /** {@inheritDoc} */
   @Override
   public void setMaximumPoolSize(int maxPoolSize)
   {
      if (maxPoolSize < 1) {
         throw new IllegalArgumentException("maxPoolSize cannot be less than 1");
      }
      this.maxPoolSize = maxPoolSize;
   }

   /** {@inheritDoc} */
   @Override
   public int getMinimumIdle()
   {
      return minIdle;
   }

   /** {@inheritDoc} */
   @Override
   public void setMinimumIdle(int minIdle)
   {
      if (minIdle < 0) {
         throw new IllegalArgumentException("minimumIdle cannot be negative");
      }
      this.minIdle = minIdle;
   }

#### 2 注意log4j2的配置信息，也是有个坑

我们用url: jdbc:log4jdbc:oracle:thin:@//XXXXX:1521/XXXXX的形式  将sql耗时等信息打印出来，但是用的是@Log4j2这个注解，这个注解是通过Log4j2SpyLogDelegator解析的  根本没有log4jdbc的属性信息，默认全给打出来了。 需要换成Slf4jSpyLogDelegator或者将注解修改下。

解决方案是：增加个资源文件log4jdbc.log4j2.properties,里面就一句话：
log4jdbc.spylogdelegator.name=net.sf.log4jdbc.log.slf4j.Slf4jSpyLogDelegator

看源码吧，找不到哪里看见的了 坑了好几天。

#### 3 redis用脚本setnx的，原因是考虑redis的特性，不能用两个命令设置锁、锁过期，这两个命令如果第一条setnx没问题，第二条没发送成功  等等异常情况  存在锁永远存在的问题

修改为执行lua脚本。看参考redisUtil.java
