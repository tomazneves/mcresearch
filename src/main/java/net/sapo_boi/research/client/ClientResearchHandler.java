package net.sapo_boi.research.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientResearchHandler {
    public static void showToast(String technologyName) {
        Minecraft.getInstance().getToasts().addToast(new TechnologyToast(technologyName));
    }
}
