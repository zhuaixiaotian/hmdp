package com.hmdp.utils;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author 时天晔
 * @data 2023/7/8
 * description:
 */
@Service
@Slf4j
public class CacheClient {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);

    }

    public void setLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
}
