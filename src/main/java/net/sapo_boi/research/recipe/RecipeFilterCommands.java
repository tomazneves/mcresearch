package net.sapo_boi.research.recipe;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "research", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RecipeFilterCommands {
    private RecipeFilterCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandBuildContext buildContext = event.getBuildContext();

        event.getDispatcher().register(
                Commands.literal("research")
                        .then(Commands.literal("filter")
                                .then(Commands.argument("item", ItemArgument.item(buildContext))
                                        .executes(ctx -> {
                                            Item item = getItemFromArgument(ctx, "item");
                                            RecipeFilterService.removeRecipesFor(item);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Filtered recipes for " + item),
                                                    true
                                            );
                                            return 1;
                                        })))
                        .then(Commands.literal("restore")
                                .then(Commands.argument("item", ItemArgument.item(buildContext))
                                        .executes(ctx -> {
                                            Item item = getItemFromArgument(ctx, "item");
                                            RecipeFilterService.restoreRecipesFor(item);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Restored recipes for " + item),
                                                    true
                                            );
                                            return 1;
                                        })))
        );
    }

    private static Item getItemFromArgument(CommandContext<?> ctx, String argumentName) {
        // ItemArgument.getItem returns an ItemInput in 1.20.1
        ItemInput input = ItemArgument.getItem(ctx, argumentName);
        return input.getItem();
    }
}