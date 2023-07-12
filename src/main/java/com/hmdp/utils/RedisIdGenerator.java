package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author 时天晔
 * @data 2023/7/8
 * description:
 */
@Service
public class RedisIdGenerator {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final long begin = 1640995200L;

    public long nextID(String prefix) {
        long time = new Date().getTime();
        long timestamp = time - begin;
        SimpleDateFormat yyyyMMdd = new SimpleDateFormat("yyyy:MM:dd");
        String format = yyyyMMdd.format(time);
        long increment = stringRedisTemplate.opsForValue().increment("icr:" + prefix + ":" + format);
        return (time << 32) | increment;

    }

}
