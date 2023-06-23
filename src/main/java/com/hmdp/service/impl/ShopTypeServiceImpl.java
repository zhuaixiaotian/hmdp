package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> queryByCache() {
        String key = "cache:shop-type:list";
        // 1、从redis查询缓存
        String str = stringRedisTemplate.opsForValue().get(key);
        // 2、存在直接返回
        if (!ObjectUtils.isEmpty(str) && !StrUtil.isBlank(str)) {
            return JSONUtil.toList(str,ShopType.class);
        }
        // 3、不存在查询数据库
        List<ShopType> list = query().orderByAsc("sort").list();
        // 4、数据不存在返回报错
        if (ObjectUtils.isEmpty(list)) {
            throw new RuntimeException("种类不存在");
        }
        // 5、数据库存在，写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(list));
        // 6、对象返回前端
        return list;

    }
}
