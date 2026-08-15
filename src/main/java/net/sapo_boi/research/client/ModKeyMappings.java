package net.sapo_boi.research.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.sapo_boi.research.ResearchMod;
import org.lwjgl.glfw.GLFW;

/** Registered automatically by Forge - no changes needed in the main mod class. */
@Mod.EventBusSubscriber(modid = ResearchMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModKeyMappings {
    public static final KeyMapping OPEN_TECH_TREE = new KeyMapping(
        "key." + ResearchMod.MODID + ".open_tech_tree",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_J,
        "key.categories." + ResearchMod.MODID
    );

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_TECH_TREE);
    }
}
