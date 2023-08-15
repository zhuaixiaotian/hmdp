package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private FollowMapper followMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Override
    public Result follow(Long followedId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;
        if (isFollow) {
            Follow follow = new Follow();
            follow.setFollowUserId(followedId);
            follow.setUserId(userId);
            follow.setCreateTime(LocalDateTime.now());
            save(follow);
            stringRedisTemplate.opsForSet().add(key, followedId.toString());
        } else {
            remove(new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followedId));
            stringRedisTemplate.opsForSet().remove(key, followedId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followedId) {
        Long userId = UserHolder.getUser().getId();
        LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followedId);
        int count = count(queryWrapper);
        return Result.ok(count > 0);
    }

    @Override
    public Result common(Long followedId) {
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;
        String key2 = "follow:" + followedId;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (ObjectUtils.isEmpty(intersect)) {
            return Result.ok(Collections.emptyList());
        }
        Set<Long> collect = intersect.stream().map(Long::valueOf).collect(Collectors.toSet());
        List<UserDTO> users = userService.listByIds(collect)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
