package net.sapo_boi.research.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Simple, dependency-free toast (no texture atlas needed) - easy to restyle later
 * once real art/icons exist.
 */
@OnlyIn(Dist.CLIENT)
public class TechnologyToast implements Toast {
    private static final long DISPLAY_TIME_MS = 5000L;

    private final String technologyName;

    public TechnologyToast(String technologyName) {
        this.technologyName = technologyName;
    }

    @Override
    public Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        guiGraphics.fill(0, 0, width(), height(), 0xFF1E1E1E);
        guiGraphics.renderOutline(0, 0, width(), height(), 0xFFFFAA00);

        guiGraphics.drawString(toastComponent.getMinecraft().font, "Technology Researched!", 8, 7, 0xFFAA00, false);
        guiGraphics.drawString(toastComponent.getMinecraft().font, technologyName, 8, 18, 0xFFFFFF, false);

        return timeSinceLastVisible >= DISPLAY_TIME_MS ? Visibility.HIDE : Visibility.SHOW;
    }
}
