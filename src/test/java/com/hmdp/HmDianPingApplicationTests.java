package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisIdGenerator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
@RunWith(SpringRunner.class)
public class HmDianPingApplicationTests {

    @Autowired
    private RedisIdGenerator redisIdGenerator;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IShopService shopService;

    @Test
    public void test() {
        System.out.println(new String("aaa").intern() == new StringBuilder("aaa").toString().intern());
    }

    @Test
    public void loadData() {
        // 查询全量店铺
        List<Shop> list = shopService.list();
        // 按照type分组
        Map<Long, List<Shop>> collect = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 存入redis
        for (Map.Entry<Long, List<Shop>> entry : collect.entrySet()) {
            Long type = entry.getKey();
            List<Shop> value = entry.getValue();
            String key = SHOP_GEO_KEY + type;
            List<RedisGeoCommands.GeoLocation<String>> locations = value.stream()
                    .map(shop -> new RedisGeoCommands.GeoLocation<String>(shop.getId().toString(),
                            new Point(shop.getX(), shop.getY()))).collect(Collectors.toList());
            redisTemplate.opsForGeo().add(key, locations);
        }
    }


}
