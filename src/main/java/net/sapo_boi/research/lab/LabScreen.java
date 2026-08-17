package net.sapo_boi.research.lab;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.sapo_boi.research.ResearchMod;

public class LabScreen extends AbstractContainerScreen<LabMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ResearchMod.MODID, "textures/gui/lab.png");

    // GUI size
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;

    // Flame overlay UV (must match texture)
    private static final int FLAME_OVERLAY_U = 176;
    private static final int FLAME_OVERLAY_V = 0;
    private static final int FLAME_SIZE_X = 14;
    private static final int FLAME_SIZE_Y = 14;

    // Progress arrow overlay UV
    private static final int ARROW_OVERLAY_U = 0;
    private static final int ARROW_OVERLAY_V = 234;
    private static final int ARROW_SIZE_X = 126;
    private static final int ARROW_SIZE_Y = 22;

    // Positions of dynamic elements (relative to GUI top-left)
    private static final int FLAME_X = 8;      // same as fuel slot
    private static final int FLAME_Y = 35;       // below the fuel slot
    private static final int ARROW_X = 42;
    private static final int ARROW_Y = 49;

    public LabScreen(LabMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Draw main background
        graphics.blit(TEXTURE, x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT);

        ContainerData data = this.menu.getData();

        // Fuel remaining (flame)
        int fuelBurnTime = data.get(0);
        int fuelBurnTimeTotal = data.get(1);
        if (fuelBurnTimeTotal > 0 && fuelBurnTime > 0) {
            int flameHeight = (int) (FLAME_SIZE_Y * ((float) fuelBurnTime / fuelBurnTimeTotal));
            // Draw from bottom upwards
            graphics.blit(TEXTURE,
                    x + FLAME_X, y + FLAME_Y + (FLAME_SIZE_Y - flameHeight),
                    FLAME_OVERLAY_U, FLAME_OVERLAY_V + (FLAME_SIZE_Y - flameHeight),
                    FLAME_SIZE_X, flameHeight);
        }

        // Research cycle progress (arrow)
        int processTimer = data.get(2);
        int currentCycleDuration = data.get(3);
        if (currentCycleDuration > 0 && processTimer > 0) {
            int arrowWidth = (int) (ARROW_SIZE_X * ((float) processTimer / currentCycleDuration));
            graphics.blit(TEXTURE,
                    x + ARROW_X, y + ARROW_Y,
                    ARROW_OVERLAY_U, ARROW_OVERLAY_V,
                    arrowWidth, ARROW_SIZE_Y);
        }
    }
}