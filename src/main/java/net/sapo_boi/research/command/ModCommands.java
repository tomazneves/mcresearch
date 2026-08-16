package net.sapo_boi.research.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.sapo_boi.research.ResearchMod;
import net.sapo_boi.research.network.ResearchNetworking;
//import net.sapo_boi.research.technology.RecipeFilter;
import net.sapo_boi.research.technology.ResearchController;
import net.sapo_boi.research.technology.ResearchSavedData;
import net.sapo_boi.research.technology.Technology;
import net.sapo_boi.research.technology.TechnologyManager;

import java.util.Comparator;

/**
 * Registers:
 * /research unlock <technology>  - unlocks a technology (requires permission level 2 / OP)
 * /research list                 - lists every known technology and its research status
 */
@Mod.EventBusSubscriber(modid = ResearchMod.MODID)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("research")
                        .then(Commands.literal("unlock")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("technology", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(TechnologyManager.getAll().keySet(), builder))
                                        .executes(ModCommands::unlockTechnology)))
                        .then(Commands.literal("lock")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("technology", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(TechnologyManager.getAll().keySet(), builder))
                                        .executes(ModCommands::lockTechnology)))
                        .then(Commands.literal("list")
                                .executes(ModCommands::listTechnologies))
                        .then(Commands.literal("reload")
                                .executes(ModCommands::reloadTechnologies))
                        .then(Commands.literal("current")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("technology", ResourceLocationArgument.id())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(TechnologyManager.getAll().keySet(), builder))
                                                .executes(ModCommands::setCurrentTechnology)))
                                .then(Commands.literal("unset")
                                        .executes(ModCommands::unsetCurrentTechnology)))
                        .then(Commands.literal("progress")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("set")
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                            .executes(ModCommands::setCurrentProgress)))
                                .then(Commands.literal("step")
                                        .executes(ModCommands::stepCurrentProgress)))
        );
    }

    private static int unlockTechnology(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "technology");
        Technology tech = TechnologyManager.get(id);
        if (tech == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown technology: " + id));
            return 0;
        }

        MinecraftServer server = ctx.getSource().getServer();
        ResearchSavedData data = ResearchSavedData.get(server);



        if (!data.unlock(id)) {
            ctx.getSource().sendFailure(Component.literal("Technology already researched: " + id));
            return 0;
        }

        // gemini
        //RecipeFilter.updateGlobalRecipes(server, TechnologyManager.getAllTechnologies(), data);

        ctx.getSource().sendSuccess(() -> Component.literal("Unlocked technology: " + tech.name()), true);
        ResearchNetworking.broadcastTechUnlocked(server, tech);
        return 1;
    }


    private static int lockTechnology(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "technology");
        Technology tech = TechnologyManager.get(id);
        if (tech == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown technology: " + id));
            return 0;
        }

        MinecraftServer server = ctx.getSource().getServer();
        ResearchSavedData data = ResearchSavedData.get(server);


        if (!data.lock(id)) {
            ctx.getSource().sendFailure(Component.literal("Technology already locked: " + id));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("Lost technology: " + tech.name()), true);
        ResearchNetworking.broadcastTechLocked(server, tech);
        return 1;
    }

    private static int listTechnologies(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        ResearchSavedData data = ResearchSavedData.get(server);

        ctx.getSource().sendSuccess(() -> {
            MutableComponent msg = Component.literal("Technologies:");
            TechnologyManager.getAll().values().stream()
                    .sorted(Comparator.comparing(t -> t.id().toString()))
                    .forEach(t -> {
                        boolean unlocked = data.isUnlocked(t.id());
                        msg.append(Component.literal("\n - "))
                                .append(Component.literal(t.name() + " [" + t.id() + "]")
                                        .withStyle(unlocked ? ChatFormatting.GREEN : ChatFormatting.RED));
                    });
            return msg;
        }, false);
        return 1;
    }

    private static int reloadTechnologies(CommandContext<CommandSourceStack> ctx) {
        TechnologyManager.reloadTechnologies();
        return 1;

    }

    private static int setCurrentTechnology(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "technology");
        MinecraftServer server = ctx.getSource().getServer();
        ResearchController.Result result = ResearchController.trySetCurrent(server, id);

        switch (result) {
            case SUCCESS -> {
                Technology tech = TechnologyManager.get(id);
                ctx.getSource().sendSuccess(() -> Component.literal("Now researching: " + tech.name()), true);
                return 1;
            }
            case UNKNOWN_TECHNOLOGY -> ctx.getSource().sendFailure(Component.literal("Unknown technology: " + id));
            case ALREADY_RESEARCHED -> ctx.getSource().sendFailure(Component.literal("Technology already researched: " + id));
            case MISSING_PREREQUISITES -> ctx.getSource().sendFailure(Component.literal("Prerequisites are not researched yet: " + id));
        }
        return 0;
    }

    private static int unsetCurrentTechnology(CommandContext<CommandSourceStack> ctx) {
        ResearchController.clearCurrent(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("Cleared the current research."), true);
        return 1;
    }

    private static int setCurrentProgress(CommandContext<CommandSourceStack> ctx) {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        ResearchController.setCurrentResearchProgress(ctx.getSource().getServer(), Math.max(0, ResearchSavedData.get().getCurrentTechnologyCost() - amount));
        return 1;
    }

    private static int stepCurrentProgress(CommandContext<CommandSourceStack> ctx) {
        ResearchController.advanceCurrentResearch(ctx.getSource().getServer(), 1);
        return 1;
    }
}
