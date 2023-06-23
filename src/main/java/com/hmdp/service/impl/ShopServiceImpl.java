package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import jdk.nashorn.internal.ir.CallNode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1、从redis查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2、存在直接返回
        if (!ObjectUtils.isEmpty(shopJson) && StrUtil.isNotBlank(shopJson)) {
            return Result.ok(JSONUtil.toBean(shopJson,Shop.class));
        }
        // 3、不存在查询数据库
        Shop shop = getById(id);
        // 4、数据不存在返回报错
        if (ObjectUtils.isEmpty(shop)) {
            return Result.fail("商铺不存在");
        }
        // 5、数据库存在，写入redis,添加过期时间
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 6、对象返回前端
        return Result.ok(shop);
    }

    @Transactional
    @Override
    public Result updateDataAndCacheById(Shop shop) {
        Long id = shop.getId();
        if (ObjectUtils.isEmpty(id)) {
            return Result.fail("店铺id不存在，数据库异常");
        }
        // 先更新数据库
        updateById(shop);
        // 再删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
