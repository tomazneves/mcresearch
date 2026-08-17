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
    private static final int PERIOD_MS = 500;

    private final String technologyName;
    private final ItemStack technologyIcon;
    private final String message;
    private long startTime = -1;
    private int background_color = BACKGROUND_COLOR;

    public static final int BASE_COLOR = 0xFF1E1E1E;
    public static final int BACKGROUND_COLOR = 0xFF28AD28;
    private static final int BORDER_COLOR = 0xFF05F705;

    private int interpolateColor(int colorA, int colorB, float ratio) {
        if (colorA == colorB) return colorA;

        int a = (int) ((colorA >> 24 & 0xFF) * (1 - ratio) + (colorB >> 24 & 0xFF) * ratio);
        int r = (int) ((colorA >> 16 & 0xFF) * (1 - ratio) + (colorB >> 16 & 0xFF) * ratio);
        int g = (int) ((colorA >> 8 & 0xFF) * (1 - ratio) + (colorB >> 8 & 0xFF) * ratio);
        int b = (int) ((colorA & 0xFF) * (1 - ratio) + (colorB & 0xFF) * ratio);
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    private int getOscillatingColor(int color1, int color2, int period, long time) {
        if (color1 == color2) return color1;

        // all in milliseconds
        double ratio = (Math.sin(time * 2.0 * Math.PI / period) + 1.0) / 2.0;
        return interpolateColor(color1, color2, (float) ratio);
    }

    public TechnologyToast(String technologyName, ResourceLocation technologyIcon, String message, int color) {
        this.message = message;
        this.technologyName = technologyName;
        this.background_color = color;

        Item item = ForgeRegistries.ITEMS.getValue(technologyIcon);
        if (item != null) this.technologyIcon = new ItemStack(item);
        else this.technologyIcon = ItemStack.EMPTY;
    }

    @Override
    public Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        if (this.startTime == -1) {
            this.startTime = timeSinceLastVisible;
        }

        int bg_color = getOscillatingColor(BASE_COLOR, background_color, PERIOD_MS, startTime + timeSinceLastVisible);
        guiGraphics.fill(0, 0, width(), height(), bg_color);
        guiGraphics.renderOutline(0, 0, width(), height(), BORDER_COLOR);

        guiGraphics.renderItem(technologyIcon, 8, 8);
        guiGraphics.drawString(toastComponent.getMinecraft().font, message, 28, 18, 0xFFAA00, false);
        guiGraphics.drawString(toastComponent.getMinecraft().font, technologyName, 28, 7, 0xFFFFFF, false);

        return timeSinceLastVisible >= DISPLAY_TIME_MS ? Visibility.HIDE : Visibility.SHOW;
    }
}
