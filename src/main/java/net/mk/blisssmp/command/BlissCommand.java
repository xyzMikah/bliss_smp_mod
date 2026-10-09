package net.mk.blisssmp.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.PermissionProviderCheck;
import net.minecraft.server.permissions.Permissions;
import net.mk.blisssmp.item.custom.CooldownManager;

public class BlissCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerCommands(dispatcher));
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bliss")
                .requires(new PermissionProviderCheck<>(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)))
                .then(Commands.literal("cd")
                        .then(Commands.literal("0")
                                .executes(ctx -> {
                                    CooldownManager.setTestingMode(true);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("§a[Bliss] §eTesting mode ON — all cooldowns = 1 tick"), true);
                                    return 1;
                                }))
                        .then(Commands.literal("normal")
                                .executes(ctx -> {
                                    CooldownManager.setTestingMode(false);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("§a[Bliss] §eNormal mode — production cooldowns restored"), true);
                                    return 1;
                                }))));
    }
}
