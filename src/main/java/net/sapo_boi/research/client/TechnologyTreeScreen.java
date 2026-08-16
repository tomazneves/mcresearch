package net.sapo_boi.research.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.sapo_boi.research.ResearchMod;
import net.sapo_boi.research.network.ServerboundSetCurrentTechnologyPacket;
import net.sapo_boi.research.technology.Technology;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.*;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import static com.ibm.icu.impl.ValidIdentifiers.Datatype.x;

@OnlyIn(Dist.CLIENT)
public class TechnologyTreeScreen extends Screen {

    private static final int NODE_WIDTH = 150;
    private static final int NODE_HEIGHT = 40;
    private static final int HORIZONTAL_SPACING = 60;
    private static final int VERTICAL_SPACING = 56;
    private static final int MARGIN = 30;
    private static final int MAX_ICONS_PER_ROW = 6;

    private Map<ResourceLocation, Technology> allTechs = new HashMap<>();
    private Set<ResourceLocation> unlocked = new HashSet<>();
    private ResourceLocation current;

    private final Map<ResourceLocation, Node> nodesById = new HashMap<>();
    private final List<Node> orderedNodes = new ArrayList<>();

    private int contentWidth;
    private int contentHeight;
    private double scrollX;
    private double scrollY;

    private Node hovered;
    private boolean dragging;

    private Button clearButton;

    public TechnologyTreeScreen() {
        super(Component.literal("Technology Tree"));
    }

    @Override
    protected void init() {
        super.init();

        this.allTechs = ClientResearchData.getTechnologies().stream()
                .collect(Collectors.toMap(Technology::id, t -> t));
        this.unlocked = new HashSet<>(ClientResearchData.getUnlocked());
        this.current = ClientResearchData.getCurrentId();

        buildGraph();
        this.scrollX = 0;
        this.scrollY = 0;

        this.clearButton = Button.builder(Component.literal("Clear Research"), b -> onClearClicked())
                .bounds(this.width - 130, 10, 120, 20)
                .build();
        this.addRenderableWidget(this.clearButton);
    }

    private void buildGraph() {
        nodesById.clear();
        orderedNodes.clear();

        if (allTechs.isEmpty()) {
            contentWidth = 0;
            contentHeight = 0;
            return;
        }

        for (Map.Entry<ResourceLocation, Technology> entry : allTechs.entrySet()) {
            nodesById.put(entry.getKey(), new Node(entry.getValue()));
        }

        Map<ResourceLocation, Integer> depths = computeDepths();
        Map<Integer, List<ResourceLocation>> byDepth = new TreeMap<>();

        for (ResourceLocation id : allTechs.keySet()) {
            int depth = depths.getOrDefault(id, 0);
            byDepth.computeIfAbsent(depth, k -> new ArrayList<>()).add(id);
        }

        for (List<ResourceLocation> list : byDepth.values()) {
            list.sort(Comparator.comparing(ResourceLocation::toString));
        }

        for (Map.Entry<Integer, List<ResourceLocation>> entry : byDepth.entrySet()) {
            int depth = entry.getKey();
            int y = MARGIN;

            for (ResourceLocation id : entry.getValue()) {
                Node node = nodesById.get(id);
                node.depth = depth;
                node.x = MARGIN + depth * (NODE_WIDTH + HORIZONTAL_SPACING);
                node.y = y;
                orderedNodes.add(node);
                y += VERTICAL_SPACING;
            }
        }

        contentWidth = 0;
        contentHeight = 0;

        for (Node node : orderedNodes) {
            contentWidth = Math.max(contentWidth, node.x + NODE_WIDTH + MARGIN);
            contentHeight = Math.max(contentHeight, node.y + NODE_HEIGHT + MARGIN);
        }
    }

    private Map<ResourceLocation, Integer> computeDepths() {
        Map<ResourceLocation, Integer> depths = new HashMap<>();
        Set<ResourceLocation> visiting = new HashSet<>();

        for (ResourceLocation id : allTechs.keySet()) {
            computeDepth(id, depths, visiting);
        }

        return depths;
    }

    private int computeDepth(ResourceLocation id, Map<ResourceLocation, Integer> depths, Set<ResourceLocation> visiting) {
        if (depths.containsKey(id)) {
            return depths.get(id);
        }

        if (visiting.contains(id)) {
            depths.put(id, 0);
            return 0;
        }

        visiting.add(id);
        Technology tech = allTechs.get(id);
        int max = 0;

        if (tech != null && tech.prerequisites() != null) {
            for (ResourceLocation prereq : tech.prerequisites()) {
                if (allTechs.containsKey(prereq)) {
                    max = Math.max(max, computeDepth(prereq, depths, visiting) + 1);
                }
            }
        }

        visiting.remove(id);
        depths.put(id, max);
        return max;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        if (clearButton != null) {
            clearButton.visible = current != null;
        }

        if (orderedNodes.isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.literal("No technologies"), this.width / 2, this.height / 2, 0xFFFFFFFF);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        hovered = findHovered(mouseX, mouseY);

        guiGraphics.enableScissor(0, 0, this.width, this.height);
        drawConnections(guiGraphics);
        for (Node node : orderedNodes) {
            renderNode(guiGraphics, node);
        }
        guiGraphics.disableScissor();

        renderCurrentResearchBanner(guiGraphics);

        // Draws the "Clear Research" button (and any other widgets) on top of the tree.
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (hovered != null) {
            renderTooltip(guiGraphics, hovered, mouseX, mouseY);
        }
    }

    /** Small always-visible banner showing the current technology and its progress. */
    private void renderCurrentResearchBanner(GuiGraphics guiGraphics) {
        if (current == null) {
            return;
        }

        Technology tech = allTechs.get(current);
        String name = tech != null ? tech.name() : current.toString();

        int cost = ClientResearchData.getProgressCost();
        int completed = ClientResearchData.getProgressCompleted();
        float pct = cost > 0 ? Math.max(0f, Math.min(1f, (float) completed / cost)) : 0f;

        int barX = 10;
        int barY = 10;
        int barW = 200;
        int barH = 14;

        guiGraphics.fill(barX - 2, barY - 2, barX + barW + 2, barY + barH + 12, 0xC0000000);
        guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0xFF3A3A3A);

        int filled = (int) (barW * pct);
        if (filled > 0) {
            guiGraphics.fill(barX, barY, barX + filled, barY + barH, 0xFFFFC107);
        }
        guiGraphics.renderOutline(barX, barY, barW, barH, 0xFF000000);

        guiGraphics.drawString(font, "Researching: " + name, barX + 2, barY + 18, 0xFFFFFFFF, true);
        String progressText = completed + " / " + cost;
        int textWidth = font.width(progressText);
        guiGraphics.drawString(font, progressText, barX + barW / 2 - textWidth / 2, barY + 3, 0xFF000000, false);
    }

    private Node findHovered(double mouseX, double mouseY) {
        double vx = mouseX + scrollX;
        double vy = mouseY + scrollY;

        for (Node node : orderedNodes) {
            if (vx >= node.x && vx <= node.x + NODE_WIDTH && vy >= node.y && vy <= node.y + NODE_HEIGHT) {
                return node;
            }
        }

        return null;
    }

    private int hashClamp(String str, int min, int max) {
        return Math.abs(str.hashCode()) % (max - min) + min;
    }

    private int hashClamp(String str, int amp) {
        return hashClamp(str, 0, amp);
    }

    private void drawConnections(GuiGraphics guiGraphics) {
        int color = 0xFF9E9E9E;

        for (Node node : orderedNodes) {
            Technology tech = node.tech;
            if (tech.prerequisites() == null) continue;

            for (ResourceLocation prereqId : tech.prerequisites()) {
                Node prereq = nodesById.get(prereqId);
                if (prereq == null) continue;

                String name = node.tech.name();
                int x1 = prereq.x + NODE_WIDTH - (int) scrollX;
                int y1 = prereq.y + hashClamp(name, NODE_HEIGHT) - (int) scrollY;
                int x2 = node.x - (int) scrollX;
                int y2 = node.y + hashClamp(name, NODE_HEIGHT) - (int) scrollY;
                ClientLevel level = Minecraft.getInstance().level;
                int midX = hashClamp(name, x1 + 1, x2 - 1);

                guiGraphics.hLine(Math.min(x1, midX), Math.max(x1, midX), y1, color);
                guiGraphics.vLine(midX, Math.min(y1, y2), Math.max(y1, y2), color);
                guiGraphics.hLine(Math.min(midX, x2), Math.max(midX, x2), y2, color);
            }
        }
    }

    private void renderNode(GuiGraphics guiGraphics, Node node) {
        int x = node.x - (int) scrollX;
        int y = node.y - (int) scrollY;

        if (x + NODE_WIDTH < 0 || y + NODE_HEIGHT < 0 || x > this.width || y > this.height) {
            return;
        }

        Technology tech = node.tech;
        int color = getNodeColor(tech);

        guiGraphics.fill(x, y, x + NODE_WIDTH, y + NODE_HEIGHT, color);

        if (node == hovered) {
            guiGraphics.fill(x - 1, y - 1, x + NODE_WIDTH + 1, y, 0xFFFFFFFF);
            guiGraphics.fill(x - 1, y + NODE_HEIGHT, x + NODE_WIDTH + 1, y + NODE_HEIGHT + 1, 0xFFFFFFFF);
            guiGraphics.fill(x - 1, y, x, y + NODE_HEIGHT, 0xFFFFFFFF);
            guiGraphics.fill(x + NODE_WIDTH, y, x + NODE_WIDTH + 1, y + NODE_HEIGHT, 0xFFFFFFFF);
        }

        ItemStack icon = createStack(tech.icon());
        int iconX = x + 4;
        int iconY = y + (NODE_HEIGHT - 16) / 2;
        guiGraphics.renderItem(icon, iconX, iconY);

        String name = tech.name();
        int maxTextWidth = NODE_WIDTH - 28;
        int textX = x + 26;
        int textY = y + (NODE_HEIGHT - font.lineHeight) / 2;

        if (font.width(name) > maxTextWidth) {
            name = font.plainSubstrByWidth(name, maxTextWidth - font.width("...")) + "...";
        }

        guiGraphics.drawString(font, name, textX, textY, 0xFFFFFFFF, false);

        if (Objects.equals(tech.id(), current) && !unlocked.contains(tech.id())) {
            int cost = ClientResearchData.getProgressCost();
            int completed = ClientResearchData.getProgressCompleted();
            float pct = cost > 0 ? Math.max(0f, Math.min(1f, (float) completed / cost)) : 0f;

            int barX = x + 3;
            int barY = y + NODE_HEIGHT - 6;
            int barWidth = NODE_WIDTH - 6;
            int barHeight = 3;

            guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF1B1B1B);
            int filled = (int) (barWidth * pct);
            if (filled > 0) {
                guiGraphics.fill(barX, barY, barX + filled, barY + barHeight, 0xFFFFC107);
            }
        }
    }

    private int getNodeColor(Technology tech) {
        if (unlocked.contains(tech.id())) {
            return 0xFF2E7D32; // unlocked green
        }

        if (Objects.equals(tech.id(), current)) {
            return 0xFF9E9D24; // ongoing yellow
        }

        return 0xFF616161; // locked gray
    }

    private ItemStack createStack(ResourceLocation id) {
        if (id == null) {
            return new ItemStack(Items.BARRIER);
        }
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(id).orElse(Items.BARRIER));
    }

    private void renderTooltip(GuiGraphics guiGraphics, Node node, int mouseX, int mouseY) {
        Technology tech = node.tech;
        List<ResourceLocation> ingredients = tech.ingredients() != null ? tech.ingredients() : List.of();
        List<ResourceLocation> blocked = tech.blockedItems() != null ? tech.blockedItems() : List.of();

        List<Component> headerLines = new ArrayList<>();
        headerLines.add(Component.literal(tech.name()));

        if (unlocked.contains(tech.id())) {
            headerLines.add(Component.literal("Unlocked").withStyle(ChatFormatting.GREEN));
        } else if (Objects.equals(tech.id(), current)) {
            String progress = ClientResearchData.getProgressCompleted() + " / " + ClientResearchData.getProgressCost();
            headerLines.add(Component.literal("Researching... (" + progress + ")").withStyle(ChatFormatting.YELLOW));
        } else if (arePrerequisitesMet(tech)) {
            headerLines.add(Component.literal("Click to research").withStyle(ChatFormatting.AQUA));
        } else {
            headerLines.add(Component.literal("Locked").withStyle(ChatFormatting.RED));
        }

        headerLines.add(Component.literal("Cycle time: " + tech.time() + "s per lab"));
        headerLines.add(Component.literal("Research cost: " + tech.cost()));

        boolean hasIngredients = !ingredients.isEmpty();
        boolean hasBlocked = !blocked.isEmpty();

        int lineHeight = font.lineHeight + 2;
        int headerHeight = headerLines.size() * lineHeight;
        int sectionHeaderHeight = (hasIngredients ? lineHeight : 0) + (hasBlocked ? lineHeight : 0);
        int ingredientRows = hasIngredients ? (int) Math.ceil(ingredients.size() / (double) MAX_ICONS_PER_ROW) : 0;
        int blockedRows = hasBlocked ? (int) Math.ceil(blocked.size() / (double) MAX_ICONS_PER_ROW) : 0;
        int iconAreaHeight = (ingredientRows + blockedRows) * 20;
        int tooltipHeight = headerHeight + sectionHeaderHeight + iconAreaHeight + 8;

        int textWidth = 0;
        for (Component line : headerLines) {
            textWidth = Math.max(textWidth, font.width(line));
        }
        if (hasIngredients) textWidth = Math.max(textWidth, font.width(Component.literal("Ingredients:")));
        if (hasBlocked) textWidth = Math.max(textWidth, font.width(Component.literal("Unlocks:")));

        int iconRowWidth = MAX_ICONS_PER_ROW * 18 + 8;
        int tooltipWidth = Math.max(textWidth + 8, iconRowWidth);

        int x = mouseX + 12;
        int y = mouseY - 12;

        if (x + tooltipWidth > this.width) {
            x = mouseX - tooltipWidth - 12;
        }
        if (y + tooltipHeight > this.height) {
            y = this.height - tooltipHeight;
        }

        x = Math.max(0, x);
        y = Math.max(0, y);

        guiGraphics.fill(x, y, x + tooltipWidth, y + tooltipHeight, 0xE0000000);
        guiGraphics.fill(x, y, x + tooltipWidth, y + 1, 0xFFFFFFFF);
        guiGraphics.fill(x, y + tooltipHeight - 1, x + tooltipWidth, y + tooltipHeight, 0xFFFFFFFF);
        guiGraphics.fill(x, y, x + 1, y + tooltipHeight, 0xFFFFFFFF);
        guiGraphics.fill(x + tooltipWidth - 1, y, x + tooltipWidth, y + tooltipHeight, 0xFFFFFFFF);

        int currentY = y + 4;
        for (Component line : headerLines) {
            guiGraphics.drawString(font, line, x + 4, currentY, 0xFFFFFFFF, false);
            currentY += lineHeight;
        }

        if (hasIngredients) {
            guiGraphics.drawString(font, Component.literal("Ingredients:"), x + 4, currentY, 0xFFAAAAAA, false);
            currentY += lineHeight;
            currentY = renderIconRow(guiGraphics, ingredients, x + 4, currentY);
        }

        if (hasBlocked) {
            guiGraphics.drawString(font, Component.literal("Unlocks:"), x + 4, currentY, 0xFFAAAAAA, false);
            currentY += lineHeight;
            renderIconRow(guiGraphics, blocked, x + 4, currentY);
        }
    }

    private int renderIconRow(GuiGraphics guiGraphics, List<ResourceLocation> items, int startX, int startY) {
        int x = startX;
        int y = startY;
        int count = 0;

        for (ResourceLocation id : items) {
            ItemStack stack = createStack(id);
            guiGraphics.renderItem(stack, x, y);
            x += 18;
            count++;

            if (count >= MAX_ICONS_PER_ROW) {
                x = startX;
                y += 20;
                count = 0;
            }
        }

        int rows = (int) Math.ceil(items.size() / (double) MAX_ICONS_PER_ROW);
        return startY + rows * 20;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (hasShiftDown()) {
            scrollX = clamp(scrollX - delta * 20, 0, Math.max(0, contentWidth - this.width));
        } else {
            scrollY = clamp(scrollY - delta * 20, 0, Math.max(0, contentHeight - this.height));
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Widgets (like the Clear Research button) get first refusal so they're clickable.
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0) {
            Node clicked = findHovered(mouseX, mouseY);
            if (clicked != null) {
                onNodeClicked(clicked);
                return true;
            }
            dragging = true;
            return true;
        }

        return false;
    }

    /** Selecting/clearing the current technology always goes through a confirmation dialog. */
    private void onNodeClicked(Node node) {
        Technology tech = node.tech;

        if (unlocked.contains(tech.id())) {
            return; // already researched, nothing to do
        }

        if (Objects.equals(tech.id(), current)) {
            onClearClicked();
            return;
        }

        if (!arePrerequisitesMet(tech)) {
            return; // locked: prerequisites aren't researched yet
        }

        this.minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    this.minecraft.setScreen(this);
                    if (confirmed) {
                        ResearchMod.CHANNEL.sendToServer(new ServerboundSetCurrentTechnologyPacket(tech.id()));
                    }
                },
                Component.literal("Research " + tech.name() + "?"),
                Component.literal("Set \"" + tech.name() + "\" as the technology currently being researched?")
        ));
    }

    private void onClearClicked() {
        if (current == null) {
            return;
        }

        Technology tech = allTechs.get(current);
        String name = tech != null ? tech.name() : current.toString();

        this.minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    this.minecraft.setScreen(this);
                    if (confirmed) {
                        ResearchMod.CHANNEL.sendToServer(new ServerboundSetCurrentTechnologyPacket(null));
                    }
                },
                Component.literal("Abandon research?"),
                Component.literal("Stop researching \"" + name + "\"? All progress on it will be lost.")
        ));
    }

    private boolean arePrerequisitesMet(Technology tech) {
        if (tech.prerequisites() == null) {
            return true;
        }
        for (ResourceLocation prereq : tech.prerequisites()) {
            if (!unlocked.contains(prereq)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            scrollX = clamp(scrollX - dragX, 0, Math.max(0, contentWidth - this.width));
            scrollY = clamp(scrollY - dragY, 0, Math.max(0, contentHeight - this.height));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class Node {
        final Technology tech;
        int x;
        int y;
        int depth;

        Node(Technology tech) {
            this.tech = tech;
        }
    }
}