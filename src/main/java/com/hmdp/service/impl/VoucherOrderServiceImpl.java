package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdGenerator;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result create(Long voucherId) {
        // 秒杀券和券共享id
        SeckillVoucher seckil = seckillVoucherService.getById(voucherId);
        LocalDateTime beginTime = seckil.getBeginTime();
        LocalDateTime endTime = seckil.getEndTime();
        if (beginTime.isAfter(LocalDateTime.now()) || endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("不在秒杀时间内");
        }
        Integer stock = seckil.getStock();
        if (stock <= 0) {
            return Result.fail("库存不足");
        }
        // 扣减库存
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucherId);
        seckillVoucher.setStock(seckil.getStock() - 1);
        LambdaUpdateWrapper<SeckillVoucher> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        // CAS 更辛苦存前判断当前库中的库存和该线程查到的库存一样 以确保中间没被人改动过 在更新，否则不更新。
        // 更新为库存大于0 即可通过校验 业务上不需要强制库存相等，只需不超卖即可
        lambdaUpdateWrapper.eq(SeckillVoucher::getVoucherId, voucherId).gt(SeckillVoucher::getStock, 0);
        int i = seckillVoucherMapper.update(seckillVoucher, lambdaUpdateWrapper);
        if (i != 1) {
            return Result.fail("库存更新失败");
        }
        // 插入订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long id = redisIdGenerator.nextID("order");
        voucherOrder.setId(id);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(id);

    }
}
