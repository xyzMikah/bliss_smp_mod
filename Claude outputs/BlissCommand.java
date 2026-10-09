package net.mk.blisssmp.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.mk.blisssmp.item.custom.CooldownManager;

public class BlissCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("bliss")
                    .requires(src -> src.hasPermissionLevel(2)) // Op level 2
                    .then(
                        CommandManager.literal("cd")
                            .then(
                                CommandManager.argument("mode", StringArgumentType.word())
                                    .suggests((ctx, builder) -> {
                                        builder.suggest("0");
                                        builder.suggest("normal");
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> {
                                        String mode = StringArgumentType.getString(ctx, "mode");
                                        ServerCommandSource source = ctx.getSource();
                                        return setCooldownMode(source, mode);
                                    })
                            )
                            // No argument: show current mode
                            .executes(ctx -> {
                                ServerCommandSource source = ctx.getSource();
                                boolean testing = CooldownManager.isTestingMode();
                                source.sendFeedback(() -> Text.literal(
                                        "§6[Bliss] §eCooldown mode: §f" + (testing ? "§atest (0 ticks)" : "§bnormal")
                                ), false);
                                return 1;
                            })
                    )
            );
        });
    }

    private static int setCooldownMode(ServerCommandSource source, String mode) {
        switch (mode.toLowerCase()) {
            case "0", "test", "testing" -> {
                CooldownManager.setTestingMode(true);
                source.sendFeedback(() -> Text.literal(
                        "§6[Bliss] §aCooldowns set to §f0 §a(testing mode). All abilities instant!"
                ), true);
                return 1;
            }
            case "normal", "default", "on" -> {
                CooldownManager.setTestingMode(false);
                source.sendFeedback(() -> Text.literal(
                        "§6[Bliss] §bCooldowns set to §fnormal §b(production values)."
                ), true);
                return 1;
            }
            default -> {
                source.sendError(Text.literal(
                        "§c[Bliss] Unknown mode '§f" + mode + "§c'. Use §f0§c or §fnormal§c."
                ));
                return 0;
            }
        }
    }
}
