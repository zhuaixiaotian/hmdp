package com.hmdp.utils;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * @author 时天晔
 * @data 2023/7/12
 * description:
 */
@Service
@AllArgsConstructor
public class SimpleRedisLock implements ILock{

    private String name;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Boolean tryLock(long time) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent("lock:" + name, Thread.currentThread().getId() + "", time, TimeUnit.SECONDS);
        return flag;
    }

    @Override
    public void unlock() {
        stringRedisTemplate.delete("lock:" + name);
    }
}
