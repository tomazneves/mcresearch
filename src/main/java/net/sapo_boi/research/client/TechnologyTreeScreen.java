package net.sapo_boi.research.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.sapo_boi.research.technology.Technology;

import java.util.Comparator;
import java.util.List;

/**
 * Bare-bones progress list, opened with the "Open Technology Tree" keybind (default: J).
 * Deliberately flat/simple for now - once prerequisite links are added to
 * {@code Technology}, this is the place to turn the list into an actual connected graph.
 */
@OnlyIn(Dist.CLIENT)
public class TechnologyTreeScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 36;

    public TechnologyTreeScreen() {
        super(Component.literal("Technology Progress"));
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
            .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
            .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        List<Technology> technologies = ClientResearchData.getTechnologies().stream()
            .sorted(Comparator.comparing(t -> t.id().toString()))
            .toList();

        if (technologies.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "No technologies configured.", this.width / 2, LIST_TOP, 0xAAAAAA);
        } else {
            int y = LIST_TOP;
            for (Technology tech : technologies) {
                boolean unlocked = ClientResearchData.isUnlocked(tech.id());
                String status = unlocked ? "[Researched]" : "[Locked]";
                int color = unlocked ? 0x55FF55 : 0xFF5555;
                guiGraphics.drawCenteredString(this.font, tech.name() + " " + status, this.width / 2, y, color);
                y += ROW_HEIGHT;
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
