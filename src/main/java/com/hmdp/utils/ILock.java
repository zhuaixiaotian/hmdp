package com.hmdp.utils;

/**
 * @author 时天晔
 * @data 2023/7/12
 * description:
 */
public interface ILock {

    /**
     * 获取锁
     * @param time
     * @return
     */
    Boolean tryLock(long time);

    /**
     * 释放锁
     */
    void unlock();

}
