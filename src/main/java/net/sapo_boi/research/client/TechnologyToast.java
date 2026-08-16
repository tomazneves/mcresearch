package net.sapo_boi.research.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Simple, dependency-free toast (no texture atlas needed) - easy to restyle later
 * once real art/icons exist.
 */
@OnlyIn(Dist.CLIENT)
public class TechnologyToast implements Toast {
    private static final long DISPLAY_TIME_MS = 5000L;

    private final String technologyName;
    private final ItemStack technologyIcon;
    private final String message;

    public TechnologyToast(String technologyName, ResourceLocation technologyIcon, String message) {
        this.message = message;
        this.technologyName = technologyName;
        Item item = ForgeRegistries.ITEMS.getValue(technologyIcon);
        if (item != null) this.technologyIcon = new ItemStack(item);
        else this.technologyIcon = ItemStack.EMPTY;
    }

    @Override
    public Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        guiGraphics.fill(0, 0, width(), height(), 0xFF1E1E1E);
        guiGraphics.renderOutline(0, 0, width(), height(), 0xFFFFAA00);

        guiGraphics.renderItem(technologyIcon, 8, 8);
        guiGraphics.drawString(toastComponent.getMinecraft().font, message, 32, 7, 0xFFAA00, false);
        guiGraphics.drawString(toastComponent.getMinecraft().font, technologyName, 32, 18, 0xFFFFFF, false);

        return timeSinceLastVisible >= DISPLAY_TIME_MS ? Visibility.HIDE : Visibility.SHOW;
    }
}
