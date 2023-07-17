package com.hmdp.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author 时天晔
 * @data 2023/7/12
 * description:
 */
@Service
@AllArgsConstructor
@NoArgsConstructor
@Data
public class SimpleRedisLock implements ILock {

    private String name;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String threadId = UUID.randomUUID().toString() + "-";

    // 定义要操作的脚本
    private static final DefaultRedisScript<Long> redisScript;

    static {
        redisScript = new DefaultRedisScript<Long>();
        // 设置脚本位置
        redisScript.setLocation(new ClassPathResource("unlock.lua"));
        redisScript.setResultType(Long.class);
    }

    public SimpleRedisLock(String name) {
        this.name = name;
    }


    @Override
    public Boolean tryLock(long time) {
        //线程标识 uuid-线程id
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent("lock:" + name, threadId + Thread.currentThread().getId(), time, TimeUnit.SECONDS);
        return flag;
    }

    @Override
    public void unlock() {
        // 调用lua脚本，传递参数
        stringRedisTemplate.execute(
                redisScript,
                Collections.singletonList("lock:" + name),
                threadId + Thread.currentThread().getId());

    }

//    @Override
//    public void unlock() {
//        // 判断是否是当前线程
//        String value = stringRedisTemplate.opsForValue().get("lock:" + name);
//        if ((threadId + Thread.currentThread().getId()).equals(value)) {
//            stringRedisTemplate.delete("lock:" + name);
//        }
//    }
}
