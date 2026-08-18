package net.sapo_boi.research.technology;

import net.minecraft.resources.ResourceLocation;

import javax.naming.spi.ResolveResult;
import java.util.*;

public class TechnologyTree {
    Map<ResourceLocation, Node> nodes;
    Map<ResourceLocation, Set<ResourceLocation>> mapParents;
    Map<ResourceLocation, Set<ResourceLocation>> mapChildren;

    public TechnologyTree(Collection<Technology> allTechs) {
        this.nodes = new HashMap<>();
        this.mapParents = new HashMap<>();
        this.mapChildren = new HashMap<>();

        for (Technology technology : allTechs) {
            Node newNode = new Node(technology);
            Set<ResourceLocation> parents = technology.prerequisites() == null ?
                    new HashSet<>() : new HashSet<>(technology.prerequisites());

            mapParents.put(newNode.id, parents);
            mapChildren.put(newNode.id, new HashSet<>());
            nodes.put(newNode.id, newNode);
        }
        mapParents.forEach((childId, parentIds) -> {
            for (ResourceLocation parentId: parentIds) mapChildren.get(parentId).add(childId);
        });

        this.recalculateNeighbors();
    }

    private TechnologyTree(TechnologyTree tree, Set<Node> nodes, boolean dummy) {
        this.nodes = new HashMap<>();
        this.mapParents = new HashMap<>();
        this.mapChildren = new HashMap<>();
        for (Node node: nodes) {
            this.nodes.put(node.id, node);
            this.mapParents.put(node.id, tree.mapParents.get(node.id));
            this.mapChildren.put(node.id, tree.mapChildren.get(node.id));
        }
    }

    private void recalculateNeighbors() {
        nodes.forEach((id, node) -> {
            node.children = new HashSet<>();
            node.parents = new HashSet<>();
            for (ResourceLocation childId : mapChildren.get(id)) node.addChild(nodes.get(childId));
            for (ResourceLocation parentId : mapParents.get(id)) node.addParent(nodes.get(parentId));
        });
    }

    public TechnologyTree(TechnologyTree tree, Set<ResourceLocation> ids) {
        this.nodes = new HashMap<>();
        this.mapParents = new HashMap<>();
        this.mapChildren = new HashMap<>();
        for (ResourceLocation id: ids) {
            this.nodes.put(id, tree.nodes.get(id));
            this.mapChildren.put(id, tree.mapChildren.get(id));
            this.mapParents.put(id, tree.mapParents.get(id));
        }
        this.recalculateNeighbors();
    }

    private Set<Node> getAllNodes() {
        return new HashSet<>(nodes.values());
    }

    public Set<ResourceLocation> getAllIds() {
        return nodes.keySet();
    }

    public boolean contains(ResourceLocation id) {
        return nodes.containsKey(id);
    }

    private boolean contains(Node node) {
        return nodes.containsValue(node);
    }

    public Set<ResourceLocation> getParentIdsOf(ResourceLocation id) {
        if (!nodes.containsKey(id)) return null;
        return nodes.get(id).getParentIds();
    }

    public Set<ResourceLocation> getChildrenIdsOf(ResourceLocation id) {
        if (!nodes.containsKey(id)) return null;
        return nodes.get(id).getChildrenIds();
    }

    private Set<ResourceLocation> getIdsOf(Set<Node> nodes) {
        Set<ResourceLocation> set = new HashSet<>();
        for (Node node : nodes) set.add(node.id);
        return set;
    }

    private Set<Node> getChildrenOf(Set<Node> nodes) {
        Set<Node> children = new HashSet<>();
        for (Node node: nodes) children.addAll(node.children);
        return children;
    }

    private Set<Node> getChildrenOf(Node node) {
        return node.children;
    }

    private Set<Node> getParentsOf(Set<Node> nodes) {
        Set<Node> parents = new HashSet<>();
        for (Node node: nodes) parents.addAll(node.parents);
        return parents;
    }

    private Set<Node> getParentsOf(Node node) {
        return node.parents;
    }

    private Set<Node> getSiblingsOf(Set<Node> nodes) {
        return getChildrenOf(getParentsOf(nodes));
    }

    private Set<Node> getSiblingsOf(Node node) {
        return getChildrenOf(getParentsOf(node));
    }

    public TechnologyTree getVisibleSubtreeOf(ResourceLocation id, int ancestorDepth, int descendantDepth) {
        return new TechnologyTree(this, getVisibleIdsOf(id, ancestorDepth, descendantDepth));
    }

    private Set<Node> getAncestorsOf(Set<Node> nodes) {
        Set<Node> ancestors = new HashSet<>();
        Set<Node> parents = getParentsOf(nodes);
        while (!parents.isEmpty()) {
            ancestors.addAll(parents);
            parents = getParentsOf(parents);
        }
        return ancestors;
    }

    private Set<Node> getAncestorsOf(Node node) {
        Set<Node> ancestors = new HashSet<>();
        Set<Node> parents = getParentsOf(node);
        while (!parents.isEmpty()) {
            ancestors.addAll(parents);
            parents = getParentsOf(parents);
        }
        return ancestors;
    }

    public Set<ResourceLocation> getAncestorIdsOf(ResourceLocation id) {
        if (!nodes.containsKey(id)) return null;
        return getIdsOf(getAncestorsOf(nodes.get(id)));
    }

    public Set<ResourceLocation> getVisibleIdsOf(ResourceLocation id, int ancestorDepth, int descendantDepth) {
        if (!nodes.containsKey(id)) return null;
        Node core = nodes.get(id);
        Set<ResourceLocation> subgraph = new HashSet<>();

        // core
        subgraph.add(core.id);

        // siblings
        subgraph.addAll(getIdsOf(getSiblingsOf(core)));

        // ancestors
        Set<Node> parents = core.parents;
        for (int i = 0; i < ancestorDepth; i++) {
            if (parents.isEmpty()) break;

            subgraph.addAll(getIdsOf(parents));
            parents = getParentsOf(parents);
        }

        // descendants
        Set<Node> children = core.children;
        for (int i = 0; i < descendantDepth; i++) {
            if (children.isEmpty()) break;

            subgraph.addAll(getIdsOf(children));
            children = getChildrenOf(children);
        }

        return subgraph;
    }

    private void _topologicalSortVisit(Node node, List<Node> unmarked, List<ResourceLocation> sorted) {
        if (!unmarked.contains(node)) return;
        for (Node child: node.children) {
            _topologicalSortVisit(child, unmarked, sorted);
        }
        unmarked.remove(node);
        sorted.add(node.id);
    }

    public List<ResourceLocation> topologicalSortIds() {
        List<ResourceLocation> sorted = new ArrayList<>();
        List<Node> unmarked = new ArrayList<>(nodes.values());
        // assume acyclic
        while (!unmarked.isEmpty()) {
            Node node = unmarked.get(0);
            _topologicalSortVisit(node, unmarked, sorted);
        }
        for (ResourceLocation id: sorted) System.out.println(id.toString());
        return sorted;
    }

    private List<Node> topologicalSort() {
        List<Node> sorted = new ArrayList<>();
        for (ResourceLocation id: topologicalSortIds()) sorted.add(nodes.get(id));
        return sorted;
    }

    private List<Node> getLongestPath() {
        List<Node> topologicalSorting = topologicalSort();
        return getLongestPath(topologicalSorting);
    }

    public int getCountInTree(Collection<ResourceLocation> set) {
        int count = 0;
        for (ResourceLocation id: set) {
            if (contains(id)) count += 1;
        }
        return count;
    }

    public int getCountNotInTree(Collection<ResourceLocation> set) {
        int count = 0;
        for (ResourceLocation id: set) {
            if (!contains(id)) count += 1;
        }
        return count;
    }

    private List<Node> getLongestPath(List<Node> topologicalSorting) {

        // For each vertex v of the DAG, in the topological ordering,
        // compute the length of the longest path ending at v by looking
        // at its incoming neighbors and adding one to the maximum
        // length recorded for those neighbors. If v has no incoming
        // neighbors, set the length of the longest path ending at v to
        // zero. In either case, record this number so that later steps
        // of the algorithm can access it.

        Map<Node, Integer> lengthUntilNode = new HashMap<>();
        Node leaf = null;
        int leafIncomingLength = -1;
        for (Node node : topologicalSorting) {
            lengthUntilNode.put(node, 0);
            int longest = 0;
            for (Node parent: node.parents) {
                int candidate = lengthUntilNode.getOrDefault(parent, 0);
                if (candidate > longest) {
                    longest = candidate;
                }
            }
            lengthUntilNode.replace(node, longest + 1);
            if (longest + 1 > leafIncomingLength) {
                leaf = node;
                leafIncomingLength = longest + 1;
            }
        }
        List<Node> longestPath = new ArrayList<>();
        if (leaf == null) return longestPath;

        while (leafIncomingLength > -1) {
            longestPath.add(leaf);
            leafIncomingLength = -1;
            for (Node node: leaf.parents) {
                int candidate = lengthUntilNode.getOrDefault(node, -1);
                if (candidate > leafIncomingLength) {
                    leafIncomingLength = candidate;
                    leaf = node;
                }
            }
        }
        return  longestPath;
    }

    public Map<ResourceLocation, Integer> getTiers() {
        Map<ResourceLocation, Integer> map = new HashMap<>();
        List<Node> topologicalSorting = topologicalSort();

        for (Node node: topologicalSorting) {
            System.out.println("Tiering node \"" + node.toString() + "\":");
            if (node.parents.isEmpty()) map.put(node.id, 0);
            int largestParentTier = 0;
            for (Node parent: node.parents) {
                int parentTier = map.getOrDefault(parent.id, 0);
                if (parentTier > largestParentTier) {
                    largestParentTier = parentTier;
                    System.out.println("> New largest parent tier: " + largestParentTier);
                }
            }
            map.put(node.id, largestParentTier + 1);
            System.out.println(">>> Tier = " + (largestParentTier + 1) + "\n");
        }
        map.forEach((id, i) -> {
            System.out.println(i + ".\t" + id.toString());
        });
        return map;
    }

    private static class Node {
        ResourceLocation id;
        String name;
        int cost;
        int time;
        List<ResourceLocation> blockedItems;
        List<ResourceLocation> ingredients;
        Set<Node> parents;
        Set<Node> children;

        Node(ResourceLocation id, String name, int cost, int time, List<ResourceLocation> blockedItems, List<ResourceLocation> ingredients) {
            this.id = id;
            this.name = name;
            this.cost = cost;
            this.time = time;
            this.blockedItems = blockedItems;
            this.ingredients = ingredients;
            this.parents = new HashSet<>();
            this.children = new HashSet<>();
        }

        Node(Technology technology) {
            this.id = technology.id();
            this.name = technology.name();
            this.cost = technology.cost();
            this.time = technology.time();
            this.blockedItems = technology.blockedItems();
            this.ingredients = technology.ingredients();
            this.parents = new HashSet<>();
            this.children = new HashSet<>();
        }

        private Set<Node> addParent(Node parent) {
            this.parents.add(parent);
            return this.parents;
        }

        private Set<Node> addParent(Collection<Node> parents) {
            this.parents.addAll(parents);
            return this.parents;
        }

        private Set<Node> addChild(Node child) {
            this.children.add(child);
            return this.children;
        }

        private Set<Node> addChild(Collection<Node> children) {
            this.children.addAll(children);
            return this.children;
        }

        public Set<ResourceLocation> getParentIds() {
            Set<ResourceLocation> ids = new HashSet<>();
            for (Node node : parents) ids.add(node.id);
            return ids;
        }

        public Set<ResourceLocation> getChildrenIds() {
            Set<ResourceLocation> ids = new HashSet<>();
            for (Node node : children) ids.add(node.id);
            return ids;
        }

        public String toString() {
            return id.toString() + "(" + parents.size() + "P" + children.size() + "C)";
        }

    }
}
