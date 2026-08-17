package net.sapo_boi.research.client.hud;

import net.minecraftforge.registries.ForgeRegistries;
import net.sapo_boi.research.client.ClientResearchData;
import net.sapo_boi.research.client.ModKeyMappings;
import net.sapo_boi.research.technology.Technology;
import net.sapo_boi.research.technology.TechnologyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

import java.util.Optional;

public class TechnologyHudOverlay {

    private static final int HUD_WIDTH = 160;
    private static final int HUD_HEIGHT = 32;
    private static final int RIGHT_MARGIN = 0;
    private static final int TOP_MARGIN = 0; // aligns with toast y position

    private static final int BACKGROUND_COLOR = 0xFF1E1E1E;
    private static final int BORDER_COLOR = 0xFFFFAA00;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int BAR_BACKGROUND = 0xFF555555;
    private static final int BAR_FILL = 0xFF00FF00;

    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerBelow(
                VanillaGuiOverlay.HOTBAR.id(),
                "technology_hud",
                TechnologyHudOverlay::render
        );
    }

    private static void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        int x = screenWidth - HUD_WIDTH - RIGHT_MARGIN;
        int y = TOP_MARGIN;

        // Draw background
        guiGraphics.fill(x, y, x + HUD_WIDTH, y + HUD_HEIGHT, BACKGROUND_COLOR);
        guiGraphics.renderOutline(x, y, HUD_WIDTH, HUD_HEIGHT, BORDER_COLOR);

        // Determine current technology
        ResourceLocation currentId = ClientResearchData.getCurrentId();
        if (currentId == null) {
            renderNoTechnology(guiGraphics, x, y);
            return;
        }

        Technology currentTech = TechnologyManager.getAll().get(currentId);
        if (currentTech == null) {
            renderNoTechnology(guiGraphics, x, y);
            return;
        }

        // Icon on the left
        ItemStack icon = createItemStack(currentTech.icon());
        guiGraphics.renderItem(icon, x + 8, y + 8);

        // Technology name
        Minecraft mc = Minecraft.getInstance();
        String name = currentTech.name();
        int maxNameWidth = HUD_WIDTH - 28;
        if (mc.font.width(name) > maxNameWidth) {
            name = mc.font.plainSubstrByWidth(name, maxNameWidth - mc.font.width("...")) + "...";
        }
        guiGraphics.drawString(mc.font, name, x + 28, y + 7, TEXT_COLOR, false);



        // Progress bar (no text)
        // Replace with your actual progress retrieval method, e.g. ResearchSavedData.get().getProgress(currentId)
        // This example assumes TechnologyManager.getProgress() returns a float between 0.0 and 1.0.
        double progress = ClientResearchData.getCurrentProgress();
        progress = Math.max(0.0f, Math.min(1.0f, progress));

        int barX = x + 28;
        int barY = y + 20;
        int barWidth = HUD_WIDTH - 8 - 28;
        int barHeight = 4;

        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, BAR_BACKGROUND);
        int fillWidth = (int) (progress * barWidth);
        if (fillWidth > 0) {
            guiGraphics.fill(barX, barY, barX + fillWidth, barY + barHeight, BAR_FILL);
        }
    }

    private static void renderNoTechnology(GuiGraphics guiGraphics, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        String message = "No technology selected";
        String keyHint = "Press " + ModKeyMappings.OPEN_TECH_TREE.getTranslatedKeyMessage().getString() + " to open the tree";

        // Truncate if needed
        int maxWidth = HUD_WIDTH - 8;
        if (mc.font.width(message) > maxWidth) {
            message = mc.font.plainSubstrByWidth(message, maxWidth - mc.font.width("...")) + "...";
        }
        if (mc.font.width(keyHint) > maxWidth) {
            keyHint = mc.font.plainSubstrByWidth(keyHint, maxWidth - mc.font.width("...")) + "...";
        }

        guiGraphics.drawString(mc.font, message, x + 4, y + 5, TEXT_COLOR, false);
        guiGraphics.drawString(mc.font, keyHint, x + 4, y + 20, TEXT_COLOR, false);
    }

    private static ItemStack createItemStack(ResourceLocation itemId) {
        return new ItemStack(Optional.ofNullable(ForgeRegistries.ITEMS.getValue(itemId)).orElse(Items.BARRIER));
    }
}