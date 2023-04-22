package com.abin.mallchat.common.user.service.impl;

import com.abin.mallchat.common.common.annotation.RedissonLock;
import com.abin.mallchat.common.common.domain.enums.IdempotentEnum;
import com.abin.mallchat.common.common.domain.enums.YesOrNoEnum;
import com.abin.mallchat.common.user.dao.ItemConfigDao;
import com.abin.mallchat.common.user.dao.UserBackpackDao;
import com.abin.mallchat.common.user.domain.entity.ItemConfig;
import com.abin.mallchat.common.user.domain.entity.UserBackpack;
import com.abin.mallchat.common.user.domain.enums.ItemTypeEnum;
import com.abin.mallchat.common.user.service.IUserBackpackService;
import com.abin.mallchat.common.user.service.cache.ItemCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * <p>
 * 用户背包表 服务类
 * </p>
 *
 * @author <a href="https://github.com/zongzibinbin">abin</a>
 * @since 2023-03-19
 */
@Service
public class UserBackpackServiceImpl implements IUserBackpackService {
    @Autowired
    private UserBackpackDao userBackpackDao;
    @Autowired
    private ItemConfigDao itemConfigDao;
    @Autowired
    private ItemCache itemCache;

    @Override
    @RedissonLock(key = "#uid")
    public void acquireItem(Long uid, Long itemId, IdempotentEnum idempotentEnum, String businessId) {//todo 分布式锁
        String idempotent = getIdempotent(itemId, idempotentEnum, businessId);
        UserBackpack userBackpack = userBackpackDao.getByIdp(idempotent);
        //幂等检查
        if (Objects.nonNull(userBackpack)) {
            return;
        }
        //业务检查
        ItemConfig itemConfig = itemCache.getById(itemId);
        if (ItemTypeEnum.BADGE.getType().equals(itemConfig.getType())) {//徽章类型做唯一性检查
            Integer countByValidItemId = userBackpackDao.getCountByValidItemId(uid, itemId);
            if (countByValidItemId > 0) {//已经有徽章了不发
                return;
            }
        }
        //发物品
        UserBackpack insert = UserBackpack.builder()
                .itemId(itemId)
                .status(YesOrNoEnum.NO.getStatus())
                .idempotent(idempotent)
                .build();
        userBackpackDao.save(insert);
    }

    private String getIdempotent(Long itemId, IdempotentEnum idempotentEnum, String businessId) {
        return String.format("%d_%d_%s", itemId, idempotentEnum.getType(), businessId);
    }
}
