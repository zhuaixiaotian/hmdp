package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;

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

    @Override
    public Result follow(Long followedId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        if (isFollow) {
            Follow follow = new Follow();
            follow.setFollowUserId(followedId);
            follow.setUserId(userId);
            follow.setCreateTime(LocalDateTime.now());
            save(follow);
        } else {
            remove(new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followedId));
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
}
