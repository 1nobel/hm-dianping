package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisDate;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.freemarker.SpringTemplateLoader;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = cachePenetration(id);

        //缓存击穿
        //互斥锁
        //Shop shop = cacheBreakdown(id);

        //逻辑过期
        Shop shop = cacheBreakdownWithLogic(id);

        if(shop == null){
            return Result.fail("商铺信息不存在！！！");
        }
        return Result.ok(shop);
    }

    // TODO 解决缓存穿透代码
    public Shop cachePenetration(Long id){
        //1. 判断缓存是否命中
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2. 存在，直接返回
        if (StrUtil.isNotBlank(shopJson)) {

            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //如果查询到的是“ ”，返回商铺信息不存在
        if(shopJson != null){
            return null;
        }

        //4. 不存在，根据id查询数据库
        Shop shop1 = getById(id);

        //5. 不存在，返回错误
        if (shop1 == null) {
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6.存在写入redis
        else {
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop1), CACHE_SHOP_TTL+ RandomUtil.randomLong(1,5), TimeUnit.MINUTES);
            return shop1;
        }
    }



    // TODO 解决缓存击穿代码(互斥锁)
    public Shop cacheBreakdown(Long id){
        Shop shop1 = null;
        try {
            //1. 判断缓存是否命中
            String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            //2. 存在，直接返回
            if (StrUtil.isNotBlank(shopJson)) {

                return JSONUtil.toBean(shopJson, Shop.class);
            }
            //如果查询到的是“ ”，返回商铺信息不存在
            if(shopJson != null){
                return null;
            }

            //3.实现缓存击穿
            //3.1获取互斥锁
            boolean isLock = tryLock(LOCK_SHOP_KEY);
            //3.2获取锁失败，休眠一段时间
            while(!isLock){
                Thread.sleep(50);
                isLock = tryLock(LOCK_SHOP_KEY);
            }
            //3.3获取锁成功仍需要查询缓存，因为可能在休眠的时间某个线程已经查询到了数据并且存入缓存
            //判断缓存是否命中
            shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            //存在，直接返回
            if (StrUtil.isNotBlank(shopJson)) {

                return JSONUtil.toBean(shopJson, Shop.class);
            }
            //如果查询到的是“ ”，返回商铺信息不存在
            if(shopJson != null){
                return null;
            }

            //4. 不存在，根据id查询数据库
            shop1 = getById(id);
            Thread.sleep(200);

            //5. 不存在，返回错误
            if (shop1 == null) {
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //6.存在写入redis

            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop1), CACHE_SHOP_TTL+ RandomUtil.randomLong(1,5), TimeUnit.MINUTES);


        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //7.释放互斥锁
            delLock(LOCK_SHOP_KEY);
        }
        return shop1;
    }

    // TODO 创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // TODO 解决缓存击穿问题（逻辑过期）
    public Shop cacheBreakdownWithLogic(Long id){

        //1. 判断缓存是否命中
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2. 未命中，直接返回
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        // 3。反序列化数据
        RedisDate redisDate = JSONUtil.toBean(shopJson, RedisDate.class);

        JSONObject jsonShop = (JSONObject) redisDate.getShop();
        Shop shop = JSONUtil.toBean(jsonShop, Shop.class);

        LocalDateTime spiritDateTime = redisDate.getLocalDateTime();

        // 4.命中，判断缓存是否过期
        // 4.1未过期，返回商铺信息
        if(spiritDateTime.isAfter(LocalDateTime.now())){
            return shop;
        }
        // 4.2过期，尝试获取互斥锁
        boolean flag = tryLock(LOCK_SHOP_KEY);
        // 4.3判断是否获取锁
        if(flag){
            // 4.4二次校验redis，如果有数据直接返回
            //1. 判断缓存是否命中
            shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            if(StrUtil.isNotBlank(shopJson)){
                redisDate = JSONUtil.toBean(shopJson, RedisDate.class);

                jsonShop = (JSONObject) redisDate.getShop();
                shop = JSONUtil.toBean(jsonShop, Shop.class);

                spiritDateTime = redisDate.getLocalDateTime();

                // 4.1未过期，返回商铺信息
                if(spiritDateTime.isAfter(LocalDateTime.now())){
                    return shop;
                }
            }
            else{
                return null;
            }
            try {
                // 4.3是，开启独立线程
                CACHE_REBUILD_EXECUTOR.submit(()->{
                    this.springShRedisData(id,20l);
                });
            }catch (Exception e){
                new RuntimeException();
            }
            finally {
                delLock(LOCK_SHOP_KEY);
            }

        }
        return shop;
    }

    // TODO 添加、删除互斥锁
    public boolean tryLock(String lockKey){
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    public void delLock(String lockKey){
        stringRedisTemplate.delete(lockKey);
    }

    // 初始化含有逻辑过期时间的缓存
    public void springShRedisData(Long id, Long expireSeconds){
        //1.根据id获取数据
        Shop shop = getById(id);
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //将数据和过期时间添加到RedisDate
        RedisDate redisDate = new RedisDate();
        redisDate.setShop(shop);
        redisDate.setLocalDateTime(LocalDateTime.now().plusSeconds(expireSeconds));

        //将数据存入redis,无需设置过期时间
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisDate));


    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("店铺id不能为null");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
