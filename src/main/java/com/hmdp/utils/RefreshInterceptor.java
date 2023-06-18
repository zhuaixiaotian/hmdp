package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.ObjectUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

/**
 * @author 时天晔
 * @data 2023/6/19
 * description:
 */
public class RefreshInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 通过token从redis中获取hash数据
        String token = request.getHeader("authorization");
        if (ObjectUtils.isEmpty(token)) {
            // 未登录 放行
            return true;
        }
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        if (ObjectUtils.isEmpty(entries)) {
            return true;
        }
        // 将数据转换为userDto
        UserDTO userDTO = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        // 刷新token，user有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return true;

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
