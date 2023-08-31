package com.hmdp.service.impl;


import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdGenerator;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;

    @Autowired
    private RedisIdGenerator redisIdGenerator;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private BlockingQueue<VoucherOrder> blockingDeque = new ArrayBlockingQueue(1024 * 1024);

    // 定义要操作的脚本
    private static final DefaultRedisScript<Long> redisScript;

    static {
        redisScript = new DefaultRedisScript<Long>();
        // 设置脚本位置
        redisScript.setLocation(new ClassPathResource("seckill.lua"));
        redisScript.setResultType(Long.class);
    }

//
//    @Override
    // 一人一单实现
//    public Result create(Long voucherId) {
//        // 秒杀券和券共享id
//        SeckillVoucher seckil = seckillVoucherService.getById(voucherId);
//        LocalDateTime beginTime = seckil.getBeginTime();
//        LocalDateTime endTime = seckil.getEndTime();
//        if (beginTime.isAfter(LocalDateTime.now()) || endTime.isBefore(LocalDateTime.now())) {
//            return Result.fail("不在秒杀时间内");
//        }
//        Integer stock = seckil.getStock();
//        if (stock <= 0) {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
    // FIXME: 2023/8/31 setnx实现分布式
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock();
////        simpleRedisLock.setName("order:" + userId);
////        Boolean flag = simpleRedisLock.tryLock(10);
////        if (!flag) {
////            return Result.fail("不允许重复下单");
////        }
////        try {
////            // 获取代理对象，同一个类中调用加事务的方法事务不会生效
////            IVoucherOrderService o = (IVoucherOrderService) AopContext.currentProxy();
////            return o.createVoucherOrder(voucherId);
////        } finally {
////            simpleRedisLock.unlock();
////        }
    //  FIXME: 2023/8/31 redisson实现分布式锁

//        RLock lock = redissonClient.getLock("order:" + userId);
//        boolean flag = false;
//        try {
//            flag = lock.tryLock(1, 10, TimeUnit.SECONDS);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        if (flag) {
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            // 获取代理对象，同一个类中调用加事务的方法事务不会生效
//            IVoucherOrderService o = (IVoucherOrderService) AopContext.currentProxy();
//            return o.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//
//    }

    @Transactional

    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 5.1.查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 5.2.判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            log.error("不允许重复下单！");
            return Result.fail("不允许重复下单");
        }
        // 6.扣减库存
        // CAS 更新库存前判断当前库中的库存和该线程查到的库存一样 以确保中间没被人改动过 在更新，否则不更新。
        // 更新为库存大于0 即可通过校验 业务上不需要强制库存相等，只需不超卖即可
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!success) {
            // 扣减失败
            log.error("库存不足！");
            return Result.fail("库存不足");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        long id = redisIdGenerator.nextID("order");
        voucherOrder.setId(id);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(id);
    }

    public Result create(Long voucherId) {
        // 执行lua脚本，不为0无购买资格
        Long result = stringRedisTemplate.execute(redisScript, Collections.emptyList(), voucherId.toString(),
                UserHolder.getUser().getId().toString());
        int i = result.intValue();
        if (i != 0) {
            return Result.fail(result == 1 ? "库存不足" : "不能重读下单");
        }
        // 有资格买,下单信息放入阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        long id = redisIdGenerator.nextID("order");
        voucherOrder.setId(id);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        blockingDeque.add(voucherOrder);
        return Result.ok(id);
    }
}
