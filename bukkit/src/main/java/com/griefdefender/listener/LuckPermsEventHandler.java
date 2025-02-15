/*
 * This file is part of GriefDefender, licensed under the MIT License (MIT).
 *
 * Copyright (c) bloodmc
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.griefdefender.listener;

import com.griefdefender.GriefDefenderPlugin;
import com.griefdefender.cache.PermissionHolderCache;
import com.griefdefender.permission.GDPermissionHolder;
import me.lucko.luckperms.api.LuckPermsApi;
import me.lucko.luckperms.api.event.group.GroupDataRecalculateEvent;
import me.lucko.luckperms.api.event.user.UserDataRecalculateEvent;

public class LuckPermsEventHandler {

    private final LuckPermsApi luckPermsApi;

    public LuckPermsEventHandler(LuckPermsApi luckPermsApi) {
        this.luckPermsApi = luckPermsApi;
        this.luckPermsApi.getEventBus().subscribe(GroupDataRecalculateEvent.class, this::onGroupDataRecalculate);
        this.luckPermsApi.getEventBus().subscribe(UserDataRecalculateEvent.class, this::onUserDataRecalculate);
    }

    public void onGroupDataRecalculate(GroupDataRecalculateEvent event) {
        final GDPermissionHolder holder = PermissionHolderCache.getInstance().getOrCreateGroup(event.getGroup().getName());
        PermissionHolderCache.getInstance().getOrCreatePermissionCache(holder).invalidateAll();
    }

    public void onUserDataRecalculate(UserDataRecalculateEvent event) {
        final GDPermissionHolder holder = PermissionHolderCache.getInstance().getOrCreateUser(event.getUser().getUuid());
        PermissionHolderCache.getInstance().getOrCreatePermissionCache(holder).invalidateAll();
        PermissionHolderCache.getInstance().getOrCreatePermissionCache(GriefDefenderPlugin.DEFAULT_HOLDER).invalidateAll();
    }
}
