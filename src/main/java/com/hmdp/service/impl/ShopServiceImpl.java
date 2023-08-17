package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
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
        return queryWithMutex(id);

    }

    /**
     * 缓存穿透-- 不存在的数据
     *
     * @param id
     * @return
     */
    private Result queryWithThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1、从redis查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2、（判断不为空串返回）
        if (StrUtil.isNotBlank(shopJson)) {
            return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
        }
        // 空字符串返回空
        else if ("".equals(shopJson)) {
            return Result.fail("商铺不存在");
        }
        // 3、不存在查询数据库
        Shop shop = getById(id);
        // 4、数据不存在返回报错
        if (ObjectUtils.isEmpty(shop)) {
            // 存入空值
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("商铺不存在");
        }
        // 5、数据库存在，写入redis,添加过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 6、对象返回前端
        return Result.ok(shop);
    }

    /**
     * 缓存击穿 热点key失效，高并发访问
     *
     * @param id
     * @return
     */
    private Result queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1、从redis查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2、（判断不为空串返回）
        if (StrUtil.isNotBlank(shopJson)) {
            return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
        }
        // 空字符串返回空
        else if ("".equals(shopJson)) {
            return Result.fail("商铺不存在");
        }
        // 缓存重建
        // 获取互斥锁 ，失败休眠并重试
        if (!tryLock("lock")) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException("休眠失败");
            }
            return queryWithMutex(id);
        }
        // 3、不存在查询数据库
        Shop shop = getById(id);
        // 4、数据不存在返回报错
        if (ObjectUtils.isEmpty(shop)) {
            // 存入空值
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("商铺不存在");
        }
        // 5、数据库存在，写入redis,添加过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        unLocak("lock");
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 判断是否需要根据坐标查询
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        // 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 查询redis
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> search = stringRedisTemplate.opsForGeo()
                .search(key, GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance()
                                .limit(end));
        if (ObjectUtils.isEmpty(search)) {
            return Result.ok();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = search.getContent();
        List<Long> list = new ArrayList<>(content.size());
        HashMap<Long, Distance> map = new HashMap<>();
        // 手动截取集合,from -> end
        content.stream().skip(from).forEach(result -> {
            RedisGeoCommands.GeoLocation<String> name = result.getContent();
            String idStr = name.getName();
            // 解析id
            list.add(Long.valueOf(idStr));
            Distance distance = result.getDistance();
            map.put(Long.valueOf(idStr), distance);
        });
        if (ObjectUtils.isEmpty(list)) {
            return Result.ok();
        }
        // 查询数据库返回
        String join = StrUtil.join(",", list);
        List<Shop> shopList = query().in("id", list).last("order by field (id," + join + " )").list();
        for (Shop shop : shopList) {
            shop.setDistance(map.get(shop.getId()).getValue());
        }
        return Result.ok(shopList);

    }

    private Boolean tryLock(String key) {
        return stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
    }

    private void unLocak(String key) {
        stringRedisTemplate.delete(key);
    }
}
