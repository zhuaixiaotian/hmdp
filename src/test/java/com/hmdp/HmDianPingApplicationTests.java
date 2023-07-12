package com.hmdp;

import com.hmdp.utils.RedisIdGenerator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
public class HmDianPingApplicationTests {

    @Autowired
    private RedisIdGenerator redisIdGenerator;

    @Test
    public void test() {
        System.out.println(redisIdGenerator.nextID("order"));
    }


}
