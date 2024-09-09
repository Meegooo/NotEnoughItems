package codechicken.nei.bookmarks.crafts.graph;

import static codechicken.nei.recipe.StackInfo.getItemStackGUID;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.bookmarks.crafts.ItemStackWithMetadata;
import codechicken.nei.recipe.BookmarkRecipeId;
import codechicken.nei.recipe.StackInfo;

public class CraftingGraph {

    private static class QueueElement {

        private final CraftingGraphNode node;
        private final String requestedKey;
        private final int requestedAmount;
        private final Set<BookmarkRecipeId> history;

        public QueueElement(CraftingGraphNode node, String requestedKey, int requestedAmount) {
            this(node, requestedKey, requestedAmount, new HashSet<>());
        }

        public QueueElement(CraftingGraphNode node, String requestedKey, int requestedAmount,
                Set<BookmarkRecipeId> history) {
            this.node = node;
            this.requestedKey = requestedKey;
            this.requestedAmount = requestedAmount;
            this.history = history;
        }

        public QueueElement withNextNode(CraftingGraphNode newNode, String requestedItemKey, int requestedItemCount) {
            Set<BookmarkRecipeId> newHistory = new HashSet<>(this.history);
            if (this.node instanceof RecipeGraphNode) {
                newHistory.add(((RecipeGraphNode) this.node).getRecipe().getRecipeId());
            }
            return new QueueElement(newNode, requestedItemKey, requestedItemCount, newHistory);
        }
    }

    private Map<String, CraftingGraphNode> nodes;

    private Map<String, ItemStack> itemStackDummies = new HashMap<>();

    private Map<Integer, ItemStack> calculatedItems = new HashMap<>();
    private Map<Integer, Integer> calculatedCraftCounts = new HashMap<>();

    private Set<Integer> inputSlots = new HashSet<>();
    private Set<Integer> craftedOutputSlots = new HashSet<>();

    private Set<ItemStack> inputStacks = new HashSet<>();
    private Set<ItemStack> outputStacks = new HashSet<>();
    private Set<ItemStack> remainingStacks = new HashSet<>();

    public CraftingGraph(Map<String, CraftingGraphNode> nodes) {
        this.nodes = nodes;
        for (CraftingGraphNode node : nodes.values()) {
            if (node instanceof RecipeGraphNode) {
                RecipeGraphNode recipeNode = (RecipeGraphNode) node;
                recipeNode.getRecipe().allIngredients.stream().flatMap(Collection::stream)
                        .forEach(it -> itemStackDummies.put(getItemStackGUID(it), it));
                recipeNode.getRecipe().result.stream().forEach(it -> itemStackDummies.put(getItemStackGUID(it), it));
            }
        }
    }

    public void dfs(List<ItemStackWithMetadata> request) {
        Deque<QueueElement> stack = new ArrayDeque<>();

        Map<String, Integer> inputTotal = new HashMap<>();

        for (ItemStackWithMetadata item : request) {
            String key = getItemStackGUID(item.getStack());
            int requestedAmount = item.getMeta().requestedAmount;
            if (nodes.containsKey(key)) {
                CraftingGraphNode node = nodes.get(key);
                if (requestedAmount > 0) {
                    stack.addFirst(new QueueElement(node, key, requestedAmount));
                    outputStacks.add(withStackSize(item.getStack(), requestedAmount));
                } else if (requestedAmount < 0) {
                    node.addToRemainders(key, -requestedAmount);
                }
            }
        }

        while (!stack.isEmpty()) {
            QueueElement queueElement = stack.pollFirst();
            CraftingGraphNode node = queueElement.node;
            Set<BookmarkRecipeId> history = queueElement.history;
            String requestedKey = queueElement.requestedKey;

            // Handle item node.
            if (node instanceof ItemGraphNode) {
                ItemGraphNode itemGraphNode = (ItemGraphNode) node;
                itemGraphNode.addCrafts(queueElement.requestedAmount);
                inputTotal.compute(requestedKey, (k, v) -> (v == null ? 0 : v) + queueElement.requestedAmount);
                continue;
            }

            RecipeGraphNode recipeNode = (RecipeGraphNode) node;
            BookmarkRecipeId recipeId = recipeNode.getRecipe().getRecipeId();

            // Handle recursive recipes
            if (history.contains(recipeId)) {
                inputTotal.compute(requestedKey, (k, v) -> (v == null ? 0 : v) + queueElement.requestedAmount);
                continue;
            }

            int requestedAmount = recipeNode.takeFromRemainders(requestedKey, queueElement.requestedAmount);

            // Calculate number of requested crafts
            int crafts = 0;
            for (ItemStack outputItemStack : recipeNode.getRecipe().result) {
                if (Objects.equals(getItemStackGUID(outputItemStack), requestedKey) && requestedAmount > 0) {
                    crafts = NEIServerUtils.divideCeil(requestedAmount, getStackSize(outputItemStack));
                    break;
                }
            }

            for (ItemStack outputItemStack : recipeNode.getRecipe().result) {
                String key = getItemStackGUID(outputItemStack);
                if (recipeNode.getPinnedOutputKeys().containsKey(key)) {
                    recipeNode.addToRemainders(key, getStackSize(outputItemStack) * crafts);
                }
            }

            if (crafts == 0) {
                continue;
            }

            recipeNode.addCrafts(crafts);
            final int finalCrafts = crafts;

            // Process inputs
            final Map<String, Integer> ingredientsToRequest = new LinkedHashMap<>();
            final Map<String, Integer> chainInputs = new LinkedHashMap<>();

            for (Map<String, ItemStack> ingredientCandidates : recipeNode.getRecipeIngredients()) {
                // Intersect current recipe pinned inputs with recipe inputs
                Set<String> pinnedCurrentRecipeInputs = new HashSet<>(recipeNode.getPinnedInputKeys().keySet());
                pinnedCurrentRecipeInputs.retainAll(ingredientCandidates.keySet());
                // Intersect all pinned outputs with recipe inputs
                Set<String> pinnedRecipeOutputs = new HashSet<>(nodes.keySet());
                pinnedRecipeOutputs.retainAll(ingredientCandidates.keySet());

                if (!pinnedCurrentRecipeInputs.isEmpty() && !pinnedRecipeOutputs.isEmpty()) {
                    // If item is pinned, proceed to try and request craft.
                    String key = pinnedRecipeOutputs.iterator().next();
                    ItemStack ingredient = ingredientCandidates.get(key);
                    ingredientsToRequest
                            .compute(key, (k, v) -> (v == null ? 0 : v) + getStackSize(ingredient) * finalCrafts);
                } else if (!pinnedCurrentRecipeInputs.isEmpty()) {
                    // Otherwise add pinned item to crafting chain inputs
                    String key = pinnedCurrentRecipeInputs.iterator().next();
                    ItemStack ingredient = ingredientCandidates.get(key);
                    chainInputs.compute(key, (k, v) -> (v == null ? 0 : v) + getStackSize(ingredient) * finalCrafts);
                    this.inputSlots.add(recipeNode.getPinnedInputKeys().get(key));
                }

            }

            for (Map.Entry<String, Integer> entry : ingredientsToRequest.entrySet()) {
                CraftingGraphNode requestedNode = nodes.get(entry.getKey());
                stack.addFirst(queueElement.withNextNode(requestedNode, entry.getKey(), entry.getValue()));
            }
            for (Map.Entry<String, Integer> entry : chainInputs.entrySet()) {
                inputTotal.compute(entry.getKey(), (k, v) -> (v == null ? 0 : v) + entry.getValue());
            }
        }
        postProcess(inputTotal);
    }

    public void postProcess(Map<String, Integer> inputTotal) {
        this.calculatedItems.clear();
        this.calculatedCraftCounts.clear();
        this.craftedOutputSlots.clear();

        this.inputStacks.clear();
        this.remainingStacks.clear();

        for (Map.Entry<String, Integer> entry : inputTotal.entrySet()) {
            ItemStack inputStack = this.itemStackDummies.get(entry.getKey());
            if (inputStack != null) {
                this.inputStacks.add(withStackSize(inputStack, entry.getValue()));
            }
        }

        Map<String, Integer> remainders = new HashMap<>();
        IdentityHashMap<CraftingGraphNode, Void> distinctNodes = new IdentityHashMap<>();
        for (CraftingGraphNode value : nodes.values()) {
            distinctNodes.put(value, null);
        }

        for (CraftingGraphNode node : distinctNodes.keySet()) {
            if (node instanceof RecipeGraphNode) {
                RecipeGraphNode recipeNode = (RecipeGraphNode) node;
                for (ItemStackWithMetadata pinnedOutput : recipeNode.getPinnedOutputs()) {
                    String key = getItemStackGUID(pinnedOutput.getStack());
                    int newCount = recipeNode.getRecipe().result.stream()
                            .filter(it -> Objects.equals(key, getItemStackGUID(it))).findFirst()
                            .map(it -> getStackSize(it) * recipeNode.getCrafts()).orElse(0);

                    ItemStack stack = withStackSize(pinnedOutput.getStack(), newCount);
                    this.calculatedItems.put(pinnedOutput.getGridIdx(), stack);
                    this.calculatedCraftCounts.put(pinnedOutput.getGridIdx(), recipeNode.getCrafts());
                    if (newCount > 0) {
                        this.craftedOutputSlots.add(pinnedOutput.getGridIdx());
                    }
                }
                for (ItemStackWithMetadata pinnedInput : recipeNode.getPinnedInputs()) {
                    String key = getItemStackGUID(pinnedInput.getStack());
                    int newCount = recipeNode.getRecipe().allIngredients.stream().flatMap(Collection::stream)
                            .filter(it -> Objects.requireNonNull(getItemStackGUID(it)).equals(key))
                            .map(it -> getStackSize(it) * recipeNode.getCrafts()).mapToInt(it -> it).sum();

                    ItemStack stack = withStackSize(pinnedInput.getStack(), newCount);
                    this.calculatedItems.put(pinnedInput.getGridIdx(), stack);
                }

                for (Map.Entry<String, Integer> entry : recipeNode.getRemainders().entrySet()) {
                    remainders.compute(entry.getKey(), (k, v) -> (v == null ? 0 : v) + entry.getValue());
                }
            } else {
                ItemGraphNode itemGraphNode = (ItemGraphNode) node;
                ItemStack stack = withStackSize(
                        itemGraphNode.getPinnedItem().getStack(),
                        itemGraphNode.getRequestedItems());
                this.calculatedItems.put(itemGraphNode.getPinnedItem().getGridIdx(), stack);
            }
        }
        for (Map.Entry<String, Integer> entry : remainders.entrySet()) {
            ItemStack stack = itemStackDummies.get(entry.getKey());
            this.remainingStacks.add(withStackSize(stack, entry.getValue()));
        }
    }

    public Map<Integer, ItemStack> getCalculatedItems() {
        return calculatedItems;
    }

    public Map<Integer, Integer> getCalculatedCraftCounts() {
        return calculatedCraftCounts;
    }

    public Set<Integer> getInputSlots() {
        return inputSlots;
    }

    public Set<Integer> getCraftedOutputSlots() {
        return craftedOutputSlots;
    }

    public Set<ItemStack> getInputStacks() {
        return inputStacks;
    }

    public Set<ItemStack> getOutputStacks() {
        return outputStacks;
    }

    public Set<ItemStack> getRemainingStacks() {
        return remainingStacks;
    }

    private int getStackSize(ItemStack itemStack) {
        return StackInfo.itemStackToNBT(itemStack).getInteger("Count");
    }

    private ItemStack withStackSize(ItemStack itemStack, int stackSize) {
        NBTTagCompound tagCompound = StackInfo.itemStackToNBT(itemStack);
        return StackInfo.loadFromNBT(tagCompound, stackSize);
    }
}
