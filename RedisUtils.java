package com.wechat.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.log4j.Log4j2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Log4j2
@Component
public class RedisUtils {

//  @Autowired
//  private StringRedisTemplate stringRedisTemplate;

//  @Autowired
//  private RedisCacheManager redisObj;

  @Autowired
  private RedisTemplate<Object, Object> redisTemplate;

  @Value("${spring.redis.expire}")
  private long expire;

  @Value("${spring.redis.prefix}")
  private String prefix;

  private static DefaultRedisScript<Long> lockScript = null;

  static {
    lockScript = new DefaultRedisScript<>();
    lockScript.setScriptText(
        (new StringBuffer())
            .append(" local key1     = KEYS[1]                                    ")
            .append(" local key2 = KEYS[2]                                    ")
            .append(" local content = ARGV[1]                                    ")
            .append(" local ttl1     = ARGV[2]                                    ")
            .append(" local lockSet = redis.call('setnx', key1, content)          ")
            .append(" if lockSet == 1 then                                       ")
            .append("   redis.call('pexpire', key2, ttl1)                          ")
            .append("   return 1                          ")
            .append(" else                                                       ")
            .append("   return 0                     ")
            .append(" end                                                        ")
            .toString()
    );
    lockScript.setResultType(Long.class);
  }

  /**
   * 时间转换函数
   */
  private Long TimeConvertToSeconds(long expire, TimeUnit unit) {

    if (unit.equals(TimeUnit.SECONDS)) {
      return expire;
    }
    if (unit.equals(TimeUnit.MINUTES)) {
      return TimeUnit.MINUTES.toSeconds(expire);
    }
    if (unit.equals(TimeUnit.HOURS)) {
      return TimeUnit.HOURS.toSeconds(expire);
    }
    if (unit.equals(TimeUnit.DAYS)) {
      return TimeUnit.DAYS.toSeconds(expire);
    }

    //最小单位到秒，默认为1秒
    return 1L;
  }

  /**
   * set共通方法
   */
  public Boolean setString(String key, final String jsonString, long seconds) {

    log.info(jsonString);

    String serializeKey = prefix == null ? key : prefix + key;
    final Boolean result = redisTemplate.execute((final RedisConnection connection) -> {

      final RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
      final byte[] name = serializer.serialize(serializeKey);
      final byte[] value = serializer.serialize(jsonString);
      connection.set(name, value);
      return connection.expire(name, seconds);

    });

    return result;

  }

  /**
   * 修改有效期共通
   */
  public Boolean setExpire(String key, long seconds) {

    String serializeKey = prefix == null ? key : prefix + key;
    final Boolean result = redisTemplate.execute((final RedisConnection connection) -> {

      final RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
      final byte[] name = serializer.serialize(serializeKey);
      return connection.expire(name, seconds);

    });

    return result;

  }

  public Boolean setKeyNx(final String key, final String value, Long expire, TimeUnit unit) {
    long timeout = expire == null ? this.expire : expire;
    return setKeyNx(key, JsonUtil.dumps(value), TimeConvertToSeconds(timeout, unit));
  }

  /**
   * setNx方式设置key信息与有效期 --修改为通过脚本方式执行，避免由于redis问题设置不了有效期
   */
  public Boolean setKeyNx(String key, final String value, long seconds) {

//    String serializeKey = prefix == null ? key : prefix + key;
//    final Boolean result = redisTemplate.execute((final RedisConnection connection) -> {
//
//      final RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
//      final byte[] name = serializer.serialize(serializeKey);
//      final byte[] byteValue = serializer.serialize(value);
//      connection.setNX(name, byteValue);
//      return connection.expire(name, seconds);
//    });

    String serializeKey = prefix == null ? key : prefix + key;

    List<Object> keyList = new ArrayList<>();
    keyList.add(serializeKey);
    keyList.add(serializeKey);
    redisTemplate.setKeySerializer(new StringRedisSerializer());
    redisTemplate.setValueSerializer(new StringRedisSerializer());
    Long executeResult = redisTemplate.execute(lockScript,
        keyList, value,String.valueOf(TimeUnit.SECONDS.toMillis(seconds)));

    return executeResult==1?true:false;
  }

  /**
   * setNx方式设置key信息 --修改为增加默认的有效期
   */
  public Boolean setKeyNx(String key, final String value) {

//    String serializeKey = prefix == null ? key : prefix + key;
//    final Boolean result = redisTemplate.execute((final RedisConnection connection) -> {
//
//      final RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
//      final byte[] name = serializer.serialize(serializeKey);
//      final byte[] byteValue = serializer.serialize(value);
//      return connection.setNX(name, byteValue);
//    });
//
//    return result;
    return setKeyNx(key, value, this.expire);
  }

  /**
   * 删除Key
   */
  public Long deleteKey(String key) {

    String serializeKey = prefix == null ? key : prefix + key;
    final Long result = redisTemplate.execute((final RedisConnection connection) -> {

      final RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
      final byte[] name = serializer.serialize(serializeKey);
      return connection.del(name);

    });

    return result;
  }

  /**
   * 添加缓存，默认有效期
   */
//  public void set(String key, String value) {
//
//    stringRedisTemplate.boundValueOps(key).set(value);
//  }
  public Boolean set(final String key, final String value) {
    return setString(key, value, expire);
  }

  /**
   * 添加缓存
   */
//  public void set(String key, String value, long timeout, TimeUnit unit) {
//    stringRedisTemplate.boundValueOps(key).set(value, timeout, unit);
//  }
  public Boolean set(String key, String value, long expire, TimeUnit unit) {
    return setString(key, value, TimeConvertToSeconds(expire, unit));
  }

  /**
   * 添加缓存
   */
//  public void set(String key, Object value, Long expire, TimeUnit unit) {
//
//    long timeout = expire == null ? this.expire : expire;
//    stringRedisTemplate.boundValueOps(key).set(JsonUtil.dumps(value), timeout, unit);
//  }
  public Boolean set(String key, Object value, Long expire, TimeUnit unit) {
    long timeout = expire == null ? this.expire : expire;
    return setString(key, JsonUtil.dumps(value), TimeConvertToSeconds(timeout, unit));
  }

  public Boolean setWithType(String key, Object value, Long expire, TimeUnit unit, TypeReference<?> rootType) {
    long timeout = expire == null ? this.expire : expire;
    return setString(key, JsonUtil.dumpsWithType(value, rootType), TimeConvertToSeconds(timeout, unit));
  }

  /**
   * 修改缓存有效期
   */
  public Boolean expire(String key, long timeout, TimeUnit unit) {
//    return stringRedisTemplate.boundValueOps(key).expire(timeout, unit);
    return setExpire(key, TimeConvertToSeconds(timeout, unit));
  }

  public Long getExpire(String key) {
    String serializeKey = prefix == null ? key : prefix + key;
//    return stringRedisTemplate.boundValueOps(key).getExpire();
    final Long result = redisTemplate.execute((final RedisConnection connection) -> {

      final RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
      final byte[] name = serializer.serialize(serializeKey);
      return connection.ttl(name);

    });

    return result;
  }

  /**
   * 删除缓存
   */
  public void delete(String key) {
//    stringRedisTemplate.delete(key);
    deleteKey(key);
  }

  /**
   * 查询缓存是否存在
   */
  public Boolean exists(String key) {
    String serializeKey = prefix == null ? key : prefix + key;
//    return stringRedisTemplate.hasKey(key);
    final Boolean result = redisTemplate.execute((final RedisConnection connection) -> {

      final RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
      final byte[] name = serializer.serialize(serializeKey);
      return connection.exists(name);

    });

    return result;
  }

  /**
   * 查询缓存
   */
  public String get(String key) {
    String serializeKey = prefix == null ? key : prefix + key;
//    return stringRedisTemplate.boundValueOps(key).get();
    final byte[] result = redisTemplate.execute((final RedisConnection connection) -> {

      final RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
      final byte[] name = serializer.serialize(serializeKey);
      return connection.get(name);

    });

    String strResult = null;
    try {
      strResult = result == null ? "" : new String(result, "utf-8");
    } catch (Exception e) {
      log.error("数据转换异常", e);
    }
    return strResult;
  }

  /**
   * 查询缓存取得对象数据信息
   */
  public <T> T get(String key, final Class<T> cls) {
//    return JsonUtil.loads(stringRedisTemplate.boundValueOps(key).get(), cls);
    return JsonUtil.loads(get(key), cls);
  }

  public <T> T getByType(String key, final TypeReference<T> valueTypeRef) {
//    return JsonUtil.loads(stringRedisTemplate.boundValueOps(key).get(), cls);
    return JsonUtil.readValue(get(key), valueTypeRef);
  }

  /**
   * 查询缓存，并删除
   */
  public String getAndDelte(String key) {
//    if (!stringRedisTemplate.hasKey(key)) {
//      return null;
//    }
//    String value = stringRedisTemplate.boundValueOps(key).get();
//    stringRedisTemplate.delete(key);
    if (!exists(key)) {
      return null;
    }
    String value = get(key);
    delete(key);

    return value;
  }

  /**
   * 自增
   */
  public Long incrBy(String key, long increment) {
    String serializeKey = prefix == null ? key : prefix + key;
//    return redisTemplate.opsForValue().increment(key, increment);
    final Long result = redisTemplate.execute((final RedisConnection connection) -> {

      final RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
      final byte[] name = serializer.serialize(serializeKey);
      return connection.incrBy(name, increment);

    });

    return result;
  }

  /**
   * 添加缓存，默认有效期
   */
//  public void set(String name, String key, Object value) {
//    redisObj.getCache(name).put(key, value);
//  }

  /**
   * 删除缓存
   */
//  public void delete(String name, String key) {
//    redisObj.getCache(name).evict(key);
//  }

  /**
   * 查询缓存
   */
//  public <T> T get(String name, String key, Class<T> clazz) {
//    return redisObj.getCache(name).get(key, clazz);
//  }

  /**
   * 查询缓存是否存在
   */
//  public boolean exists(String name, String key) {
//    return null != redisObj.getCache(name).get(key);
//  }

}
