package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    ShopServiceImpl shopService;

    @Test
    public void testAdd(){
        shopService.springShRedisData(1l,20l);
    }

}
