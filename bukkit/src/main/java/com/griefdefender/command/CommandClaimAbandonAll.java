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
package com.griefdefender.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.griefdefender.GDPlayerData;
import com.griefdefender.GriefDefenderPlugin;
import com.griefdefender.api.GriefDefender;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.api.permission.option.Options;
import com.griefdefender.cache.MessageCache;
import com.griefdefender.claim.GDClaim;
import com.griefdefender.configuration.MessageStorage;
import com.griefdefender.event.GDCauseStackManager;
import com.griefdefender.event.GDRemoveClaimEvent;
import com.griefdefender.permission.GDPermissionManager;
import com.griefdefender.permission.GDPermissions;
import com.griefdefender.text.action.GDCallbackHolder;
import com.griefdefender.util.PermissionUtil;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.adapter.bukkit.TextAdapter;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.format.TextColor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import java.util.function.Consumer;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("%griefdefender")
@CommandPermission(GDPermissions.COMMAND_ABANDON_ALL_CLAIMS)
public class CommandClaimAbandonAll extends BaseCommand {

    @CommandAlias("abandonall|abandonallclaims")
    @Description("Abandons ALL your claims")
    @Subcommand("abandon all")
    public void execute(Player player) {
        final GDPlayerData playerData = GriefDefenderPlugin.getInstance().dataStore.getOrCreatePlayerData(player.getWorld(), player.getUniqueId());
        int originalClaimCount = playerData.getInternalClaims().size();

        if (originalClaimCount == 0) {
            try {
                throw new CommandException(MessageCache.getInstance().CLAIM_NO_CLAIMS);
            } catch (CommandException e) {
                TextAdapter.sendComponent(player, e.getText());
                return;
            }
        }

        final boolean autoSchematicRestore = GriefDefenderPlugin.getActiveConfig(player.getWorld().getUID()).getConfig().claim.claimAutoSchematicRestore;
        final Component confirmationText = TextComponent.builder()
                .append(autoSchematicRestore ? MessageCache.getInstance().SCHEMATIC_ABANDON_ALL_RESTORE_WARNING : MessageCache.getInstance().ABANDON_ALL_WARNING)
                .append(TextComponent.builder()
                    .append("\n[")
                    .append("Confirm", TextColor.GREEN)
                    .append("]\n")
                    .clickEvent(ClickEvent.runCommand(GDCallbackHolder.getInstance().createCallbackRunCommand(createConfirmationConsumer(player, playerData))))
                    .hoverEvent(HoverEvent.showText(MessageCache.getInstance().UI_CLICK_CONFIRM)).build())
                .build();
        TextAdapter.sendComponent(player, confirmationText);
    }

    private static Consumer<CommandSender> createConfirmationConsumer(Player player, GDPlayerData playerData) {
        return confirm -> {
            GDCauseStackManager.getInstance().pushCause(player);
            GDRemoveClaimEvent.Abandon event = new GDRemoveClaimEvent.Abandon(ImmutableList.copyOf(playerData.getInternalClaims()));
            GriefDefender.getEventManager().post(event);
            GDCauseStackManager.getInstance().popCause();
            if (event.cancelled()) {
                TextAdapter.sendComponent(player, event.getMessage().orElse(TextComponent.of("Could not abandon claim. A plugin has denied it.").color(TextColor.RED)));
                return;
            }

            double refund = 0;
            // adjust claim blocks
            for (Claim claim : playerData.getInternalClaims()) {
                // remove all context permissions
                PermissionUtil.getInstance().clearPermissions((GDClaim) claim);
                if (claim.isSubdivision() || claim.isAdminClaim() || claim.isWilderness()) {
                    continue;
                }
                final double abandonReturnRatio = GDPermissionManager.getInstance().getInternalOptionValue(TypeToken.of(Double.class), player, Options.ABANDON_RETURN_RATIO, claim);
                if (GriefDefenderPlugin.getInstance().isEconomyModeEnabled()) {
                    refund += claim.getClaimBlocks() * abandonReturnRatio;
                } else {
                    playerData.setAccruedClaimBlocks(playerData.getAccruedClaimBlocks() - ((int) Math.ceil(claim.getClaimBlocks() * (1 - abandonReturnRatio))));
                }
            }

            playerData.restoreAbandonClaim = event.isRestoring();
            GriefDefenderPlugin.getInstance().dataStore.deleteClaimsForPlayer(player.getUniqueId());
            playerData.restoreAbandonClaim = false;

            if (GriefDefenderPlugin.getInstance().isEconomyModeEnabled()) {
                final Economy economy = GriefDefenderPlugin.getInstance().getVaultProvider().getApi();
                if (!economy.hasAccount(player)) {
                    return;
                }

                final EconomyResponse result = economy.depositPlayer(player, refund);
                if (result.transactionSuccess()) {
                    final Component message = GriefDefenderPlugin.getInstance().messageData.getMessage(MessageStorage.ECONOMY_CLAIM_ABANDON_SUCCESS, ImmutableMap.of(
                            "amount", TextComponent.of(String.valueOf(refund))));
                    GriefDefenderPlugin.sendMessage(player, message);
                }
            } else {
                int remainingBlocks = playerData.getRemainingClaimBlocks();
                final Component message = GriefDefenderPlugin.getInstance().messageData.getMessage(MessageStorage.ABANDON_SUCCESS, ImmutableMap.of(
                        "amount", remainingBlocks));
                GriefDefenderPlugin.sendMessage(player, message);
            }

            playerData.revertActiveVisual(player);
        };
    }
}
