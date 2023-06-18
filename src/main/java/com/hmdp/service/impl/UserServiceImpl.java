package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return true
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号,错误返回提示
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 2.生成验证码，保存并发送
        String code = RandomUtil.randomNumbers(6);

        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 3.发送验证码
        log.debug("发送短信验证码成功,验证码:{}",code);
        return Result.ok();
    }

    /**
     * 登录校验
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1、校验手机号和验证码
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 从redis中获取code
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (ObjectUtils.isEmpty(code) || !code.equals(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }
        // 2、根据手机号查出用户
        QueryChainWrapper<User> query = query().eq("phone", loginForm.getPhone());
        User user = query.one();
        // 3、根据用户是否存在进行登录或注册
        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(5));
            user.setUpdateTime(LocalDateTime.now());
            user.setCreateTime(LocalDateTime.now());
            // 注册入库
            save(user);
        }
        // 4、生成token，作为key，将userDto转为hash存入redis
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor(
                        (fieldName,fieldValue)->fieldValue.toString()
                ));
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token,userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        // 5、将token返回前端保存，前段以后会的请求会带着token，通过token从redis中取出对应的user
        return Result.ok(token);
    }

}
