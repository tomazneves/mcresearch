package net.sapo_boi.research.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.sapo_boi.research.ResearchMod;
import net.sapo_boi.research.lab.LabScreen;

@Mod.EventBusSubscriber(modid = ResearchMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        while (ModKeyMappings.OPEN_TECH_TREE.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new net.sapo_boi.research.client.TechnologyTreeScreen());
            }
        }
    }
}
