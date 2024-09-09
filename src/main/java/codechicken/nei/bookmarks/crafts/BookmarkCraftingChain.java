package codechicken.nei.bookmarks.crafts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.item.ItemStack;

import codechicken.nei.BookmarkPanel;
import codechicken.nei.bookmarks.crafts.graph.CraftingGraph;
import codechicken.nei.bookmarks.crafts.graph.CraftingGraphNode;
import codechicken.nei.bookmarks.crafts.graph.ItemGraphNode;
import codechicken.nei.bookmarks.crafts.graph.RecipeGraphNode;
import codechicken.nei.recipe.BookmarkRecipeId;
import codechicken.nei.recipe.StackInfo;

public class BookmarkCraftingChain {

    private CraftingGraph graph;

    public void refresh(ArrayList<ItemStackWithMetadata> groupItems, boolean skipCalculation) {
        if (groupItems.isEmpty()) {
            return;
        }
        Map<String, CraftingGraphNode> nodes = new HashMap<>();

        Map<BookmarkRecipeId, List<ItemStackWithMetadata>> groupedByRecipe = new HashMap<>();
        Map<BookmarkRecipeId, BookmarkPanel.BookmarkRecipe> recipes = new HashMap<>();
        List<ItemStackWithMetadata> itemsWithoutRecipe = new ArrayList<>();

        for (ItemStackWithMetadata groupItem : groupItems) {
            BookmarkRecipeId recipeId = groupItem.getMeta().recipeId;
            if (recipeId != null) {
                groupedByRecipe.computeIfAbsent(recipeId, k -> new ArrayList<>()).add(groupItem);
                BookmarkPanel.BookmarkRecipe fullRecipe = groupItem.getMeta().getFullRecipe(groupItem.getStack());
                if (fullRecipe != null) {
                    recipes.putIfAbsent(recipeId, fullRecipe);
                }
            } else {
                itemsWithoutRecipe.add(groupItem);
            }
        }

        for (Map.Entry<BookmarkRecipeId, List<ItemStackWithMetadata>> entry : groupedByRecipe.entrySet()) {
            if (entry.getKey() != null) {
                List<ItemStackWithMetadata> inputs = new ArrayList<>();
                List<ItemStackWithMetadata> outputs = new ArrayList<>();
                for (ItemStackWithMetadata it : entry.getValue()) {
                    if (it.getMeta().ingredient) {
                        inputs.add(it);
                    } else {
                        outputs.add(it);
                    }
                }

                if (!outputs.isEmpty()) {
                    if (inputs.isEmpty()) {
                        itemsWithoutRecipe.addAll(outputs);
                    } else {
                        BookmarkPanel.BookmarkRecipe recipe = recipes.get(entry.getKey());
                        if (recipe != null) {
                            RecipeGraphNode node = new RecipeGraphNode(recipe, inputs, outputs);
                            for (ItemStackWithMetadata output : outputs) {
                                String key = StackInfo.getItemStackGUID(output.getStack());
                                if (!nodes.containsKey(key) || !(nodes.get(key) instanceof RecipeGraphNode)) {
                                    nodes.put(key, node);
                                }
                            }
                        }
                    }
                }
            }
        }

        for (ItemStackWithMetadata itemWithoutRecipe : itemsWithoutRecipe) {
            String key = StackInfo.getItemStackGUID(itemWithoutRecipe.getStack());
            ItemGraphNode node = new ItemGraphNode(itemWithoutRecipe);
            if (!nodes.containsKey(key) || !(nodes.get(key) instanceof RecipeGraphNode)) {
                nodes.put(key, node);
            }
        }
        this.graph = new CraftingGraph(nodes);
        List<ItemStackWithMetadata> requestedItems = groupItems.stream().filter(it -> !it.getMeta().ingredient)
                .collect(Collectors.toList());
        if (skipCalculation) {
            this.graph.postProcess(Collections.emptyMap());
        } else {
            this.graph.dfs(requestedItems);
        }
    }

    public Map<Integer, ItemStack> getCalculatedItems() {
        if (this.graph == null) return Collections.emptyMap();
        return this.graph.getCalculatedItems();
    }

    public Map<Integer, Integer> getCalculatedCraftCounts() {
        if (this.graph == null) return Collections.emptyMap();
        return this.graph.getCalculatedCraftCounts();
    }

    public Set<Integer> getInputSlots() {
        if (this.graph == null) return Collections.emptySet();
        return this.graph.getInputSlots();
    }

    public Set<Integer> getOutputSlots() {
        if (this.graph == null) return Collections.emptySet();
        return this.graph.getCraftedOutputSlots();
    }

    public Set<ItemStack> getInputStacks() {
        if (this.graph == null) return Collections.emptySet();
        return this.graph.getInputStacks();
    }

    public Set<ItemStack> getOutputStacks() {
        if (this.graph == null) return Collections.emptySet();
        return this.graph.getOutputStacks();
    }

    public Set<ItemStack> getRemainingStacks() {
        if (this.graph == null) return Collections.emptySet();
        return this.graph.getRemainingStacks();
    }
}
