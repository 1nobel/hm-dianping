package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //查询商户类型
    @Override
    public Result queryList() {
        //1.查询redis缓存，存在直接返回
        String shopType = stringRedisTemplate.opsForValue().get("shopType");
        if(StrUtil.isNotBlank(shopType)){
            List<ShopType> shopTypes = JSONUtil.toList(shopType, ShopType.class);
            return Result.ok(shopTypes);
        }

        //2.不存在，从数据库查询
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        if(shopTypes == null){
            return Result.fail("分类不存在");
        }

        stringRedisTemplate.opsForValue().set("shopType",JSONUtil.toJsonStr(shopTypes));
        //返回
        return Result.ok(shopTypes);
    }
}
