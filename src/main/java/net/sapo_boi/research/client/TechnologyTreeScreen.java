package net.sapo_boi.research.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.sapo_boi.research.ResearchMod;
import net.sapo_boi.research.network.ServerboundSetCurrentTechnologyPacket;
import net.sapo_boi.research.technology.Technology;

import java.util.*;
import java.util.stream.Collectors;

@OnlyIn(Dist.CLIENT)
public class TechnologyTreeScreen extends Screen {

    private static final int MARGINS = 0;

    // Colors
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF2E7D32;
    private static final int YELLOW = 0xFFF9A825;
    private static final int LIGHT_GRAY = 0xFFAAAAAA;
    private static final int GRAY = 0xFF9E9E9E;
    private static final int RED = 0xFFC62828;
    private static final int DISABLED_GRAY = 0xFF3A3A3A;
    private static final int FILLED_GREEN = 0xFFFFC107;
    private static final int BLACK = 0xFF000000;
    private static final int BACKGROUND_DIM = 0xE0000000;
    private static final int BACKGROUND_TINTED = 0x40000000;
    private static final int TRANSPARENT = 0x00000000;

    // Sidebar / info panel
    private static final int SIDEBAR_X = MARGINS;
    private static final int SIDEBAR_Y = MARGINS;
    private static final int SIDEBAR_WIDTH = 196;
    private static final int INFO_HEIGHT = 120;
    private static final int GRID_TOP = SIDEBAR_Y + INFO_HEIGHT + MARGINS;

    // Grid
    private static final int GRID_CELL_SIZE = 44;
    private static final int GRID_CELL_PADDING = 4;

    // Tree node (3:4 rectangle, vertical larger)
    private static final int TREE_NODE_WIDTH = 60;
    private static final int TREE_NODE_HEIGHT = 80;
    private static final int TREE_H_SPACING = 20;
    private static final int TREE_V_SPACING = 30;
    private static final int TREE_MARGIN = 20;

    private static final int TREE_X = SIDEBAR_X + SIDEBAR_WIDTH + MARGINS;
    private static final int TREE_Y = MARGINS;

    private Map<ResourceLocation, Technology> allTechs = new HashMap<>();
    private Set<ResourceLocation> unlocked = new HashSet<>();
    private ResourceLocation current;
    private ResourceLocation selected;
    private ResourceLocation hovered;

    private final Map<ResourceLocation, List<ResourceLocation>> childrenMap = new HashMap<>();

    // Tree layout
    private final List<TreeRenderNode> treeNodes = new ArrayList<>();
    private final List<TreeConnection> treeConnections = new ArrayList<>();
    private int treeContentWidth;
    private int treeContentHeight;
    private double treeScrollX;
    private double treeScrollY;
    private TreeRenderNode hoveredTree;
    private boolean draggingTree;

    // Grid
    private final List<ResourceLocation> gridOrder = new ArrayList<>();
    private double gridScroll;
    private int gridContentHeight;

    private Button startButton;
    private Button cancelButton;

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
        this.selected = current; // initially focus on the current research, if any
        this.hovered = null;

        buildChildrenMap();
        buildGridOrder();
        buildTreeLayout();

        this.treeScrollX = 0;
        this.treeScrollY = 0;
        this.gridScroll = 0;

        int buttonY = SIDEBAR_Y + INFO_HEIGHT - 28;
        int buttonMargin = 8;
        int buttonWidth = (SIDEBAR_WIDTH - 3 * buttonMargin) / 2;

        this.startButton = Button.builder(Component.literal("Start Research"), b -> onStartClicked())
                .bounds(SIDEBAR_X + buttonMargin, buttonY, buttonWidth, 20)
                .build();
        this.cancelButton = Button.builder(Component.literal("Cancel"), b -> onCancelClicked())
                .bounds(SIDEBAR_X + buttonWidth + 2 * buttonMargin, buttonY, buttonWidth, 20)
                .build();
        this.addRenderableWidget(this.startButton);
        this.addRenderableWidget(this.cancelButton);
    }

    public void refresh() {
        this.init();
    }

    private void buildChildrenMap() {
        childrenMap.clear();
        for (Technology tech : allTechs.values()) {
            if (tech.prerequisites() != null) {
                for (ResourceLocation prereq : tech.prerequisites()) {
                    childrenMap.computeIfAbsent(prereq, k -> new ArrayList<>()).add(tech.id());
                }
            }
        }
    }

    private void buildGridOrder() {
        List<ResourceLocation> ids = new ArrayList<>(allTechs.keySet());
        ids.sort(Comparator
                .comparingInt(this::getGridGroupOrder)
                .thenComparingInt(this::getIngredientCount)
                .thenComparing(id -> {
                    Technology tech = allTechs.get(id);
                    return tech != null ? tech.name().toLowerCase(Locale.ROOT) : "";
                }));
        gridOrder.clear();
        gridOrder.addAll(ids);

        int columns = getGridColumns();
        int rows = (int) Math.ceil((double) gridOrder.size() / columns);
        gridContentHeight = rows * (GRID_CELL_SIZE + GRID_CELL_PADDING) + 4;
    }

    private int getGridGroupOrder(ResourceLocation id) {
        if (unlocked.contains(id)) return 0;
        if (Objects.equals(id, current)) return 1;
        Technology tech = allTechs.get(id);
        if (tech != null && arePrerequisitesMet(tech)) return 2;
        return 3;
    }

    private int getIngredientCount(ResourceLocation id) {
        Technology tech = allTechs.get(id);
        return tech != null && tech.ingredients() != null ? tech.ingredients().size() : 0;
    }

    private int getGridColumns() {
        int available = SIDEBAR_WIDTH - GRID_CELL_PADDING;
        return Math.max(1, available / (GRID_CELL_SIZE + GRID_CELL_PADDING));
    }

    private int getGridHeight() {
        return Math.max(0, this.height - GRID_TOP - MARGINS);
    }

    private int getTreeWidth() {
        return Math.max(0, this.width - TREE_X - MARGINS);
    }

    private int getTreeHeight() {
        return Math.max(0, this.height - MARGINS * 2);
    }

    /**
     * Finds the longest path (as a list of technology IDs) that follows ancestor → descendant
     * relationships among the nodes present in {@code visibleReal}.
     *
     * @param visibleReal the subset of technologies currently visible in the tree
     * @return the longest path, or an empty list if the set is empty
     */
    private List<ResourceLocation> findLongestPath(Set<ResourceLocation> visibleReal) {
        if (visibleReal.isEmpty()) {
            return List.of();
        }

        // Build adjacency map: parent -> list of children (only within visibleReal)
        Map<ResourceLocation, List<ResourceLocation>> children = new HashMap<>();
        for (ResourceLocation id : visibleReal) {
            Technology tech = allTechs.get(id);
            if (tech != null && tech.prerequisites() != null) {
                for (ResourceLocation prereq : tech.prerequisites()) {
                    if (visibleReal.contains(prereq)) {
                        children.computeIfAbsent(prereq, k -> new ArrayList<>()).add(id);
                    }
                }
            }
        }

        // Memoization for DFS: maps a node to the longest path starting from that node
        Map<ResourceLocation, List<ResourceLocation>> memo = new HashMap<>();
        List<ResourceLocation> overallLongest = List.of();

        for (ResourceLocation id : visibleReal) {
            List<ResourceLocation> path = dfsLongest(id, children, memo);
            if (path.size() > overallLongest.size()) {
                overallLongest = path;
            }
        }

        return overallLongest;
    }

    private List<ResourceLocation> dfsLongest(ResourceLocation id,
                                              Map<ResourceLocation, List<ResourceLocation>> children,
                                              Map<ResourceLocation, List<ResourceLocation>> memo) {
        if (memo.containsKey(id)) {
            return memo.get(id);
        }

        List<ResourceLocation> bestPath = new ArrayList<>();
        bestPath.add(id);

        List<ResourceLocation> childList = children.getOrDefault(id, List.of());
        for (ResourceLocation child : childList) {
            List<ResourceLocation> childPath = dfsLongest(child, children, memo);
            // If this child's path plus the current node is longer than the best so far, update
            if (childPath.size() + 1 > bestPath.size()) {
                bestPath = new ArrayList<>();
                bestPath.add(id);
                bestPath.addAll(childPath);
            }
        }

        memo.put(id, bestPath);
        return bestPath;
    }

    private void buildTreeLayout() {
        treeNodes.clear();
        treeConnections.clear();

        if (selected == null || !allTechs.containsKey(selected)) {
            treeContentWidth = 0;
            treeContentHeight = 0;
            return;
        }

        Set<ResourceLocation> visibleReal = new HashSet<>();

        // Ancestors
        addAncestors(selected, visibleReal);
        visibleReal.add(selected);

        // Children and grandchildren
        List<ResourceLocation> children = childrenMap.getOrDefault(selected, List.of());
        for (ResourceLocation child : children) {
            if (allTechs.containsKey(child)) {
                visibleReal.add(child);
                for (ResourceLocation grandchild : childrenMap.getOrDefault(child, List.of())) {
                    if (allTechs.containsKey(grandchild)) {
                        visibleReal.add(grandchild);
                    }
                }
            }
        }

        // Direct siblings: share at least one direct prerequisite with selected
        Technology selectedTech = allTechs.get(selected);
        if (selectedTech != null && selectedTech.prerequisites() != null) {
            for (ResourceLocation prereq : selectedTech.prerequisites()) {
                for (ResourceLocation sibling : childrenMap.getOrDefault(prereq, List.of())) {
                    if (!sibling.equals(selected) && allTechs.containsKey(sibling)) {
                        visibleReal.add(sibling);
                    }
                }
            }
        }

        // First-degree cousins: children of siblings of selected's direct parents
        Set<ResourceLocation> parentSiblings = new HashSet<>();
        if (selectedTech != null && selectedTech.prerequisites() != null) {
            for (ResourceLocation parent : selectedTech.prerequisites()) {
                Technology parentTech = allTechs.get(parent);
                if (parentTech != null && parentTech.prerequisites() != null) {
                    for (ResourceLocation grandparent : parentTech.prerequisites()) {
                        for (ResourceLocation auntUncle : childrenMap.getOrDefault(grandparent, List.of())) {
                            if (!auntUncle.equals(parent) && allTechs.containsKey(auntUncle)) {
                                parentSiblings.add(auntUncle);
                            }
                        }
                    }
                }
            }
        }
        for (ResourceLocation psib : parentSiblings) {
            for (ResourceLocation cousin : childrenMap.getOrDefault(psib, List.of())) {
                if (!cousin.equals(selected) && allTechs.containsKey(cousin)) {
                    visibleReal.add(cousin);
                }
            }
        }

        // Assign levels
        //1. Find amplitude
        List<ResourceLocation> longestPath = findLongestPath(visibleReal);


        /*
        Map<ResourceLocation, Integer> levels = new HashMap<>();
        levels.put(selected, 0);
        assignAncestorLevels(selected, 0, levels);

        for (ResourceLocation child : childrenMap.getOrDefault(selected, List.of())) {
            if (visibleReal.contains(child)) {
                levels.put(child, 1);
                for (ResourceLocation grandchild : childrenMap.getOrDefault(child, List.of())) {
                    if (visibleReal.contains(grandchild)) {
                        levels.put(grandchild, 2);
                    }
                }
            }
        }

        // Siblings/cousins are same generation as selected
        for (ResourceLocation id : visibleReal) {
            levels.putIfAbsent(id, 0);
        }
        */

        // Create real render nodes
        List<TreeRenderNode> realNodes = new ArrayList<>();
        for (ResourceLocation id : visibleReal) {
            realNodes.add(new TreeRenderNode(id, levels.getOrDefault(id, 0), false, 0, null, 0));
        }

        // Create dummy nodes for hidden neighbors
        List<TreeRenderNode> dummyNodes = new ArrayList<>();
        for (ResourceLocation id : visibleReal) {
            Technology tech = allTechs.get(id);
            int level = levels.getOrDefault(id, 0);

            long hiddenParents = tech.prerequisites() != null
                    ? tech.prerequisites().stream().filter(p -> !visibleReal.contains(p)).count()
                    : 0;
            if (hiddenParents > 0) {
                dummyNodes.add(new TreeRenderNode(null, level - 1, true, (int) hiddenParents, id, -1));
            }

            long hiddenChildren = childrenMap.getOrDefault(id, List.of()).stream()
                    .filter(c -> !visibleReal.contains(c))
                    .count();
            if (hiddenChildren > 0) {
                dummyNodes.add(new TreeRenderNode(null, level + 1, true, (int) hiddenChildren, id, 1));
            }
        }

        // Group by level
        Map<Integer, List<TreeRenderNode>> byLevel = new TreeMap<>();
        for (TreeRenderNode node : realNodes) {
            byLevel.computeIfAbsent(node.level, k -> new ArrayList<>()).add(node);
        }
        for (TreeRenderNode node : dummyNodes) {
            byLevel.computeIfAbsent(node.level, k -> new ArrayList<>()).add(node);
        }

        int minLevel = byLevel.keySet().stream().min(Integer::compareTo).orElse(0);
        int maxLevel = byLevel.keySet().stream().max(Integer::compareTo).orElse(0);

        treeNodes.clear();
        for (Map.Entry<Integer, List<TreeRenderNode>> entry : byLevel.entrySet()) {
            int level = entry.getKey();
            List<TreeRenderNode> levelNodes = entry.getValue();
            levelNodes.sort(Comparator
                    .comparing((TreeRenderNode n) -> !n.isReal())
                    .thenComparing(n -> n.techId != null && n.techId.equals(selected) ? 0 : 1)
                    .thenComparing(n -> n.techId != null ? allTechs.get(n.techId).name() : "")
                    .thenComparingInt(n -> n.dummyCount));

            int x = TREE_MARGIN;
            for (TreeRenderNode node : levelNodes) {
                node.x = x;
                node.y = TREE_MARGIN + (level - minLevel) * (TREE_NODE_HEIGHT + TREE_V_SPACING);
                treeNodes.add(node);
                x += TREE_NODE_WIDTH + TREE_H_SPACING;
            }
        }

        // Content bounds
        treeContentWidth = 0;
        treeContentHeight = 0;
        for (TreeRenderNode node : treeNodes) {
            treeContentWidth = Math.max(treeContentWidth, node.x + TREE_NODE_WIDTH + TREE_MARGIN);
            treeContentHeight = Math.max(treeContentHeight, node.y + TREE_NODE_HEIGHT + TREE_MARGIN);
        }

        // Connections
        treeConnections.clear();
        Map<ResourceLocation, TreeRenderNode> byTechId = new HashMap<>();
        for (TreeRenderNode node : treeNodes) {
            if (node.isReal()) byTechId.put(node.techId, node);
        }

        // Real connections
        for (TreeRenderNode node : treeNodes) {
            if (!node.isReal()) continue;
            ResourceLocation id = node.techId;
            Technology tech = allTechs.get(id);

            if (tech.prerequisites() != null) {
                for (ResourceLocation prereq : tech.prerequisites()) {
                    if (visibleReal.contains(prereq)) {
                        TreeRenderNode from = byTechId.get(prereq);
                        if (from != null) treeConnections.add(new TreeConnection(from, node));
                    }
                }
            }

            for (ResourceLocation child : childrenMap.getOrDefault(id, List.of())) {
                if (visibleReal.contains(child)) {
                    TreeRenderNode to = byTechId.get(child);
                    if (to != null) treeConnections.add(new TreeConnection(node, to));
                }
            }
        }

        // Dummy connections
        for (TreeRenderNode node : treeNodes) {
            if (!node.isReal()) continue;
            ResourceLocation id = node.techId;
            Technology tech = allTechs.get(id);

            long hiddenParents = tech.prerequisites() != null
                    ? tech.prerequisites().stream().filter(p -> !visibleReal.contains(p)).count()
                    : 0;
            if (hiddenParents > 0) {
                TreeRenderNode dummy = findDummyFor(node, -1);
                if (dummy != null) treeConnections.add(new TreeConnection(dummy, node));
            }

            long hiddenChildren = childrenMap.getOrDefault(id, List.of()).stream()
                    .filter(c -> !visibleReal.contains(c))
                    .count();
            if (hiddenChildren > 0) {
                TreeRenderNode dummy = findDummyFor(node, 1);
                if (dummy != null) treeConnections.add(new TreeConnection(node, dummy));
            }
        }
    }

    private void addAncestors(ResourceLocation id, Set<ResourceLocation> visible) {
        Technology tech = allTechs.get(id);
        if (tech != null && tech.prerequisites() != null) {
            for (ResourceLocation prereq : tech.prerequisites()) {
                if (allTechs.containsKey(prereq) && visible.add(prereq)) {
                    addAncestors(prereq, visible);
                }
            }
        }
    }

    private void assignAncestorLevels(ResourceLocation id, int level, Map<ResourceLocation, Integer> levels) {
        Technology tech = allTechs.get(id);
        if (tech != null && tech.prerequisites() != null) {
            for (ResourceLocation prereq : tech.prerequisites()) {
                if (allTechs.containsKey(prereq)) {
                    int newLevel = level - 1;
                    Integer existing = levels.get(prereq);
                    if (existing == null || newLevel < existing) {
                        levels.put(prereq, newLevel);
                        assignAncestorLevels(prereq, newLevel, levels);
                    }
                }
            }
        }
    }

    private TreeRenderNode findDummyFor(TreeRenderNode node, int direction) {
        for (TreeRenderNode dummy : treeNodes) {
            if (dummy.isDummy()
                    && Objects.equals(dummy.attachToId, node.techId)
                    && dummy.attachDirection == direction) {
                return dummy;
            }
        }
        return null;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        if (allTechs.isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.literal("No technologies"), this.width / 2, this.height / 2, WHITE);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        // Update button states
        startButton.active = selected != null
                && allTechs.containsKey(selected)
                && !unlocked.contains(selected)
                && !Objects.equals(selected, current)
                && arePrerequisitesMet(allTechs.get(selected));
        cancelButton.active = current != null;

        renderInfoPanel(guiGraphics);
        renderGrid(guiGraphics, mouseX, mouseY);
        renderTree(guiGraphics, mouseX, mouseY);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (hoveredTree != null && hoveredTree.isReal()) {
            hovered = hoveredTree.techId;
            renderTreeTooltip(guiGraphics, hoveredTree, mouseX, mouseY);
        }
        else {
            hovered = null;
        }
    }

    private void renderInfoPanel(GuiGraphics guiGraphics) {
        int x = SIDEBAR_X;
        int y = SIDEBAR_Y;
        int w = SIDEBAR_WIDTH;
        int h = INFO_HEIGHT;

        guiGraphics.fill(x, y, x + w, y + h, BACKGROUND_DIM);
        guiGraphics.fill(x, y, x + w, y + 1, WHITE);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, WHITE);
        guiGraphics.fill(x, y, x + 1, y + h, WHITE);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, WHITE);

        ResourceLocation displayId = null;
        if (hovered != null && hoveredTree.isReal()) displayId = hovered;
        else if (selected != null) displayId = selected;
        else if (current != null) displayId = current;

        if (displayId == null) {
            guiGraphics.drawString(font, "No technology selected", x + 8, y + 8, WHITE, false);
            guiGraphics.drawString(font, "Click a node in the tree", x + 8, y + 20, LIGHT_GRAY, false);
            return;
        }

        Technology tech = allTechs.get(displayId);
        if (tech == null) {
            guiGraphics.drawString(font, displayId.toString(), x + 8, y + 8, WHITE, false);
            return;
        }

        ItemStack icon = createStack(tech.icon());
        float scale = 2.0f;
        renderItemScaled(guiGraphics, icon, x + 8, y + 4, scale);

        String name = tech.name();
        int maxNameWidth = w - 44;
        if (font.width(name) > maxNameWidth) {
            name = font.plainSubstrByWidth(name, maxNameWidth - font.width("...")) + "...";
        }
        guiGraphics.drawString(font, name, x + 16 + (int) (16 * scale), y + 10, WHITE, false);

        String status;
        int statusColor;
        if (unlocked.contains(displayId)) {
            status = "Researched";
            statusColor = GREEN;
        } else if (Objects.equals(displayId, current)) {
            status = "In Progress";
            statusColor = YELLOW;
        } else if (arePrerequisitesMet(tech)) {
            status = "Available";
            statusColor = GRAY;
        } else {
            status = "Locked";
            statusColor = RED;
        }
        guiGraphics.drawString(font, status, x + 16 + (int) (16 * scale), y + 22, statusColor, false);

        guiGraphics.drawString(font, "Time: " + tech.time() + " s / cycle", x + 8, y + 40, LIGHT_GRAY, false);

        List<ResourceLocation> uniqueIngredients = uniqueIngredients(tech);
        if (!uniqueIngredients.isEmpty()) {
            int ix = guiGraphics.drawString(font, "Cost: " + tech.cost() + " x ", x + 8, y + 52, LIGHT_GRAY, false);
            float iScale = 0.5f;
            int iSep = 2;
            int iSize = (int) (iScale * 16);

            //int ix = x + SIDEBAR_WIDTH - (iSize + iSep) * uniqueIngredients.size() - 6;
            int iy = y + 52;
            for (ResourceLocation ingredient : uniqueIngredients) {
                renderItemScaled(guiGraphics, createStack(ingredient), ix, iy, iScale);
                ix += iSep + iSize;
            }
        }

        List<ResourceLocation> uniqueUnlocks = uniqueUnlocks(tech);
        if (!uniqueUnlocks.isEmpty()) {
            int ix = guiGraphics.drawString(font, "Unlocks: ", x + 8, y + 64, LIGHT_GRAY, false);
            float iScale = 0.5f;
            int iSep = 2;
            int iSize = (int) (iScale * 16);

            int iy = y + 64;
            for (ResourceLocation unlock : uniqueUnlocks) {
                renderItemScaled(guiGraphics, createStack(unlock), ix, iy, iScale);
                ix += iSep + iSize;
            }
        }

        if (Objects.equals(displayId, current)) {
            int cost = ClientResearchData.getProgressCost();
            int completed = ClientResearchData.getProgressCompleted();
            float pct = cost > 0 ? Math.max(0f, Math.min(1f, (float) completed / cost)) : 0f;

            int barX = x + 8;
            int barY = y + 78;
            int barW = w - 16;
            int barH = 8;

            guiGraphics.fill(barX, barY, barX + barW, barY + barH, DISABLED_GRAY);
            int filled = (int) (barW * pct);
            if (filled > 0) {
                guiGraphics.fill(barX, barY, barX + filled, barY + barH, FILLED_GREEN);
            }
            guiGraphics.renderOutline(barX, barY, barW, barH, BLACK);

            String progressText = completed + " / " + cost;
            int tw = font.width(progressText);
            guiGraphics.drawString(font, progressText, barX + barW / 2 - tw / 2, barY + 1, WHITE, false);
        }

    }

    private void renderGrid(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = SIDEBAR_X;
        int y = GRID_TOP;
        int w = SIDEBAR_WIDTH;
        int h = getGridHeight();

        guiGraphics.fill(x, y, x + w, y + h, BACKGROUND_TINTED);
        guiGraphics.enableScissor(x, y, x + w, y + h);

        int columns = getGridColumns();
        int cellSize = GRID_CELL_SIZE;
        int pad = GRID_CELL_PADDING;

        for (int i = 0; i < gridOrder.size(); i++) {
            int row = i / columns;
            int col = i % columns;

            int cellX = x + 4 + col * (cellSize + pad);
            int cellY = y + 4 + row * (cellSize + pad) - (int) gridScroll;

            if (cellY + cellSize < y || cellY > y + h) continue;

            ResourceLocation id = gridOrder.get(i);
            int color = getNodeColor(id);
            guiGraphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, color);

            if (Objects.equals(id, selected)) {
                guiGraphics.fill(cellX - 1, cellY - 1, cellX + cellSize + 1, cellY, WHITE);
                guiGraphics.fill(cellX - 1, cellY + cellSize, cellX + cellSize + 1, cellY + cellSize + 1, WHITE);
                guiGraphics.fill(cellX - 1, cellY, cellX, cellY + cellSize, WHITE);
                guiGraphics.fill(cellX + cellSize, cellY, cellX + cellSize + 1, cellY + cellSize, WHITE);
            }

            Technology tech = allTechs.get(id);
            ItemStack icon = createStack(tech.icon());
            renderItemScaled(guiGraphics, icon, cellX + 6, cellY + 6, 2.0f);

            List<ResourceLocation> ingredients = uniqueIngredients(tech);
            int spacing = Math.max(14 - ingredients.size() * 2, 4);

            int miniSize = 8;
            int startX = cellX + cellSize - ((ingredients.size() - 1) * spacing) - 13;
            if (startX < cellX + 4) startX = cellX + 4;
            int miniY = cellY + cellSize - 6 - miniSize;

            for (int j = 0; j < ingredients.size(); j++) {
                renderItemScaled(guiGraphics, createStack(ingredients.get(j)), startX + j * spacing, miniY, 1.0f);
            }
        }

        guiGraphics.disableScissor();
    }

    private void renderTree(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = TREE_X;
        int y = TREE_Y;
        int w = getTreeWidth();
        int h = getTreeHeight();

        guiGraphics.fill(x, y, x + w, y + h, BACKGROUND_TINTED);
        guiGraphics.enableScissor(x, y, x + w, y + h);

        for (TreeConnection conn : treeConnections) {
            drawTreeConnection(guiGraphics, conn);
        }

        hoveredTree = findHoveredTree(mouseX, mouseY);
        for (TreeRenderNode node : treeNodes) {
            renderTreeNode(guiGraphics, node);
        }

        guiGraphics.disableScissor();

        if (treeNodes.isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.literal("Select a technology from the grid"), x + w / 2, y + h / 2, WHITE);
        }
    }

    private int hashClamp(String str, int min, int max) {
        return Math.abs(str.hashCode()) % (max - min) + min;
    }

    private int hashClamp(String str, int amp) {
        return hashClamp(str, 0, amp);
    }

    private void drawTreeConnection(GuiGraphics guiGraphics, TreeConnection conn) {
        int midpointX1 = (conn.from.techId == null) ? TREE_NODE_WIDTH / 2 : hashClamp(conn.from.techId.toString(), TREE_NODE_WIDTH - 4);
        int midpointX2 = (conn.to.techId == null) ? TREE_NODE_WIDTH / 2 : hashClamp(conn.to.techId.toString(), TREE_NODE_WIDTH - 4);

        int x1 = toScreenX(conn.from.x) + midpointX1;
        int x2 = toScreenX(conn.to.x) + midpointX2;

        int y1 = toScreenY(conn.from.y) + TREE_NODE_HEIGHT ;
        int y2 = toScreenY(conn.to.y) ;

        int midY = (conn.from.techId == null) ? (
                (conn.to.techId == null) ?
                        (y1 + y2) / 2 :
                        hashClamp(conn.to.techId.toString(), y1 + 4, y2 - 4)
                ) : hashClamp(conn.from.techId.toString(), y1 + 4, y2 - 4);

        guiGraphics.vLine(x1, Math.min(y1, midY), Math.max(y1, midY), GRAY);
        guiGraphics.hLine(Math.min(x1, x2), Math.max(x1, x2), midY, GRAY);
        guiGraphics.vLine(x2, Math.min(midY, y2), Math.max(midY, y2), GRAY);
    }

    private void renderTreeNode(GuiGraphics guiGraphics, TreeRenderNode node) {
        int sx = toScreenX(node.x);
        int sy = toScreenY(node.y);

        if (sx + TREE_NODE_WIDTH < TREE_X || sx > TREE_X + getTreeWidth()
                || sy + TREE_NODE_HEIGHT < TREE_Y || sy > TREE_Y + getTreeHeight()) {
            return;
        }

        int color = node.isReal() ? getNodeColor(node.techId) : TRANSPARENT;
        guiGraphics.fill(sx, sy, sx + TREE_NODE_WIDTH, sy + TREE_NODE_HEIGHT, color);

        if ((node == hoveredTree && node.isReal()) || (hoveredTree == null && node.techId == selected))
            guiGraphics.renderOutline(sx, sy, TREE_NODE_WIDTH, TREE_NODE_HEIGHT, WHITE);

        if (node.isReal()) {
            Technology tech = allTechs.get(node.techId);
            ItemStack icon = createStack(tech.icon());
            int iconX = sx + TREE_NODE_WIDTH / 2 - 8;
            int iconY = sy + 8;
            renderItemScaled(guiGraphics, icon, iconX-16, iconY, 3.0f);

            List<ResourceLocation> ingredients = uniqueIngredients(tech);
            int spacing = Math.max(14 - ingredients.size() * 2, 4);
            float scale = 1.0f;
            int miniSize = (int) scale * 16;

            int startX = sx + TREE_NODE_WIDTH - ((ingredients.size() - 1) * spacing) - miniSize + 3;
            if (startX < sx + 4) startX = sx + 4;
            int miniY = sy + TREE_NODE_HEIGHT - 6 - miniSize;

            for (int i = 0; i < ingredients.size(); i++) {
                renderItemScaled(guiGraphics, createStack(ingredients.get(i)), startX + i * spacing, miniY, 1.0f);
            }
        } else {
            String text = "+" + String.valueOf(node.dummyCount);
            int pY = node.attachDirection > 0 ? sy + 4 : sy + TREE_NODE_HEIGHT - 4 - font.lineHeight;
            guiGraphics.drawCenteredString(font, text, sx + TREE_NODE_WIDTH / 2, pY, GRAY);
        }
    }

    private TreeRenderNode findHoveredTree(double mouseX, double mouseY) {
        for (TreeRenderNode node : treeNodes) {
            int sx = toScreenX(node.x);
            int sy = toScreenY(node.y);
            if (mouseX >= sx && mouseX <= sx + TREE_NODE_WIDTH && mouseY >= sy && mouseY <= sy + TREE_NODE_HEIGHT) {
                return node;
            }
        }
        return null;
    }

    private void renderTreeTooltip(GuiGraphics guiGraphics, TreeRenderNode node, int mouseX, int mouseY) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 200.0F); // Moves layer forward

        final boolean doShowIngredients = false;

        Technology tech = allTechs.get(node.techId);
        List<ResourceLocation> ingredients = (tech.ingredients() != null && doShowIngredients) ? tech.ingredients() : List.of();
        List<ResourceLocation> blockedItems = tech.blockedItems() != null ? tech.blockedItems() : List.of();

        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        for (ResourceLocation id : ingredients) {
            counts.merge(id, 1, Integer::sum);
        }

        int lineHeight = font.lineHeight + 2;
        int headerHeight = 2 * lineHeight; // name + time
        int ingredientRows = counts.isEmpty() ? 0 : 1;
        int blockedRows = blockedItems.size() == 0 ? 0 : 1;


        float ingredientScale = 1.0f;
        float blockedScale = 1.0f;
        int ingredientRowHeight = ingredientRows * (2 + (int) (ingredientScale * 16));
        int blockedRowHeight = blockedRows * (2 + (int) (blockedScale * 16));

        int tooltipHeight = headerHeight + ingredientRowHeight + blockedRowHeight + 12;

        int textWidth = Math.max(
                font.width(tech.name()),
                font.width("Time: " + tech.time() + " s/cyc")
        );
        int iconWidth = Math.max(
                counts.size() * ingredientRowHeight + 8,
                blockedItems.size() * blockedRowHeight + 8
        );
        int tooltipWidth = Math.max(textWidth + 8, iconWidth);

        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + tooltipWidth > this.width) x = mouseX - tooltipWidth - 12;
        if (y + tooltipHeight > this.height) y = this.height - tooltipHeight;
        x = Math.max(0, x);
        y = Math.max(0, y);

        guiGraphics.fill(x, y, x + tooltipWidth, y + tooltipHeight, 0xE0000000);
        guiGraphics.fill(x, y, x + tooltipWidth, y + 1, WHITE);
        guiGraphics.fill(x, y + tooltipHeight - 1, x + tooltipWidth, y + tooltipHeight, WHITE);
        guiGraphics.fill(x, y, x + 1, y + tooltipHeight, WHITE);
        guiGraphics.fill(x + tooltipWidth - 1, y, x + tooltipWidth, y + tooltipHeight, WHITE);

        guiGraphics.drawString(font, tech.name(), x + 4, y + 4, WHITE, false);
        guiGraphics.drawString(font, "Time: " + tech.time() + " s/cyc", x + 4, y + 4 + lineHeight, WHITE, false);

        if (!counts.isEmpty()) {
            int ix = x + 4;
            int iy = y + 4 + headerHeight;
            for (Map.Entry<ResourceLocation, Integer> entry : counts.entrySet()) {
                ItemStack stack = createStack(entry.getKey());
                stack.setCount(entry.getValue());
                guiGraphics.renderItem(stack, ix, iy);
                ix += ingredientRowHeight;
            }
        }

        if (!blockedItems.isEmpty()) {
            int ix = x + 4;
            int iy = y + 4 + headerHeight + ingredientRowHeight;
            for (ResourceLocation location : blockedItems) {
                ItemStack stack = createStack(location);
                guiGraphics.renderItem(stack, ix, iy);
                ix += blockedRowHeight;
            }
        }

        guiGraphics.pose().popPose();
    }

    private int getNodeColor(ResourceLocation id) {
        if (unlocked.contains(id)) return GREEN;       // researched green
        if (Objects.equals(id, current)) return YELLOW; // current yellow
        Technology tech = allTechs.get(id);
        if (tech != null && arePrerequisitesMet(tech)) return GRAY; // available grey
        return RED;                                   // locked red
    }

    private ItemStack createStack(ResourceLocation id) {
        if (id == null) return new ItemStack(Items.BARRIER);
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(id).orElse(Items.BARRIER));
    }

    private List<ResourceLocation> uniqueIngredients(Technology tech) {
        if (tech == null || tech.ingredients() == null) return List.of();
        return new ArrayList<>(new LinkedHashSet<>(tech.ingredients()));
    }

    private List<ResourceLocation> uniqueUnlocks(Technology tech) {
        if (tech == null || tech.blockedItems() == null) return List.of();
        return new ArrayList<>(new LinkedHashSet<>(tech.blockedItems()));
    }

    private void renderItemScaled(GuiGraphics guiGraphics, ItemStack stack, int x, int y, float scale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(scale, scale, 1);
        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.pose().popPose();
    }

    private void renderItemScaled(GuiGraphics guiGraphics, ItemStack stack, int x, int y, float scale, int stackSize) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(scale, scale, 1);
        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.renderItemDecorations(font, stack, 0, 0, String.valueOf(stackSize));
        guiGraphics.pose().popPose();
    }

    private int toScreenX(int layoutX) {
        return layoutX - (int) treeScrollX + TREE_X;
    }

    private int toScreenY(int layoutY) {
        return layoutY - (int) treeScrollY + TREE_Y;
    }

    private boolean isMouseInGrid(double mouseX, double mouseY) {
        return mouseX >= SIDEBAR_X && mouseX <= SIDEBAR_X + SIDEBAR_WIDTH
                && mouseY >= GRID_TOP && mouseY <= GRID_TOP + getGridHeight();
    }

    private boolean isMouseInTree(double mouseX, double mouseY) {
        return mouseX >= TREE_X && mouseX <= TREE_X + getTreeWidth()
                && mouseY >= TREE_Y && mouseY <= TREE_Y + getTreeHeight();
    }

    private void handleGridClick(double mouseX, double mouseY) {
        int columns = getGridColumns();
        int cellSize = GRID_CELL_SIZE;
        int pad = GRID_CELL_PADDING;

        int relX = (int) mouseX - (SIDEBAR_X + 4);
        int relY = (int) mouseY - (GRID_TOP + 4) + (int) gridScroll;

        int col = relX / (cellSize + pad);
        int row = relY / (cellSize + pad);
        int index = row * columns + col;

        if (index >= 0 && index < gridOrder.size()) {
            selected = gridOrder.get(index);
            buildTreeLayout();
        }
    }

    private void handleTreeClick(double mouseX, double mouseY) {
        TreeRenderNode clicked = findHoveredTree(mouseX, mouseY);
        if (clicked != null && clicked.isReal()) {
            selected = clicked.techId;
            buildTreeLayout();
        } else {
            draggingTree = true;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0) {
            if (isMouseInGrid(mouseX, mouseY)) {
                handleGridClick(mouseX, mouseY);
                return true;
            }
            if (isMouseInTree(mouseX, mouseY)) {
                handleTreeClick(mouseX, mouseY);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isMouseInGrid(mouseX, mouseY)) {
            gridScroll = clamp(gridScroll - delta * 20, 0, Math.max(0, gridContentHeight - getGridHeight()));
            return true;
        }

        if (isMouseInTree(mouseX, mouseY)) {
            if (hasShiftDown()) {
                treeScrollX = clamp(treeScrollX - delta * 20, 0, Math.max(0, treeContentWidth - getTreeWidth()));
            } else {
                treeScrollY = clamp(treeScrollY - delta * 20, 0, Math.max(0, treeContentHeight - getTreeHeight()));
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingTree = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingTree && button == 0) {
            treeScrollX = clamp(treeScrollX - dragX, 0, Math.max(0, treeContentWidth - getTreeWidth()));
            treeScrollY = clamp(treeScrollY - dragY, 0, Math.max(0, treeContentHeight - getTreeHeight()));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void onStartClicked() {
        if (selected == null || !allTechs.containsKey(selected)) return;
        Technology tech = allTechs.get(selected);
        if (tech == null || unlocked.contains(selected) || Objects.equals(selected, current)) return;
        if (!arePrerequisitesMet(tech)) return;

        ResearchMod.CHANNEL.sendToServer(new ServerboundSetCurrentTechnologyPacket(selected));
    }

    private void onCancelClicked() {
        if (current == null) return;
        ResearchMod.CHANNEL.sendToServer(new ServerboundSetCurrentTechnologyPacket(null));
    }

    private boolean arePrerequisitesMet(Technology tech) {
        if (tech == null || tech.prerequisites() == null) return true;
        for (ResourceLocation prereq : tech.prerequisites()) {
            if (!unlocked.contains(prereq)) return false;
        }
        return true;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class TreeRenderNode {
        final ResourceLocation techId;     // null for dummy
        final int level;
        final boolean dummy;
        final int dummyCount;
        final ResourceLocation attachToId; // for dummy
        final int attachDirection;         // -1 for parent side, +1 for child side
        int x;
        int y;

        TreeRenderNode(ResourceLocation techId, int level, boolean dummy, int dummyCount, ResourceLocation attachToId, int attachDirection) {
            this.techId = techId;
            this.level = level;
            this.dummy = dummy;
            this.dummyCount = dummyCount;
            this.attachToId = attachToId;
            this.attachDirection = attachDirection;
        }

        boolean isReal() {
            return !dummy;
        }

        boolean isDummy() {
            return dummy;
        }
    }

    private static class TreeConnection {
        final TreeRenderNode from;
        final TreeRenderNode to;

        TreeConnection(TreeRenderNode from, TreeRenderNode to) {
            this.from = from;
            this.to = to;
        }
    }
}