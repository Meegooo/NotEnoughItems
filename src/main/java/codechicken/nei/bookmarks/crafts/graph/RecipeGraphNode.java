package codechicken.nei.bookmarks.crafts.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import codechicken.nei.BookmarkPanel.BookmarkRecipe;
import codechicken.nei.bookmarks.crafts.ItemStackWithMetadata;
import codechicken.nei.recipe.StackInfo;

public class RecipeGraphNode implements CraftingGraphNode {

    private final List<ItemStackWithMetadata> pinnedInputs;
    private final List<ItemStackWithMetadata> pinnedOutputs;
    private final Map<String, Integer> pinnedInputKeys = new HashMap<>();
    private final Map<String, Integer> pinnedOutputKeys = new HashMap<>();
    private final BookmarkRecipe recipe;

    private final List<Map<String, ItemStack>> recipeIngredients = new ArrayList<>();

    private int crafts = 0;
    private Map<String, Integer> remainders;

    public RecipeGraphNode(BookmarkRecipe recipe, List<ItemStackWithMetadata> pinnedInputs,
            List<ItemStackWithMetadata> pinnedOutputs) {
        this.pinnedInputs = pinnedInputs;
        this.pinnedOutputs = pinnedOutputs;
        this.recipe = recipe;
        for (ItemStackWithMetadata item : pinnedInputs) {
            pinnedInputKeys.put(StackInfo.getItemStackGUID(item.getStack()), item.getGridIdx());
        }
        for (ItemStackWithMetadata item : pinnedOutputs) {
            pinnedOutputKeys.put(StackInfo.getItemStackGUID(item.getStack()), item.getGridIdx());
        }

        for (List<ItemStack> slotIngredients : recipe.allIngredients) {
            Map<String, ItemStack> ingredientMap = new HashMap<>();
            for (ItemStack ingredient : slotIngredients) {
                ingredientMap.put(StackInfo.getItemStackGUID(ingredient), ingredient);
            }
            recipeIngredients.add(ingredientMap);
        }
        this.remainders = new HashMap<>();
    }

    public List<ItemStackWithMetadata> getPinnedInputs() {
        return pinnedInputs;
    }

    public List<ItemStackWithMetadata> getPinnedOutputs() {
        return pinnedOutputs;
    }

    public Map<String, Integer> getPinnedInputKeys() {
        return pinnedInputKeys;
    }

    public Map<String, Integer> getPinnedOutputKeys() {
        return pinnedOutputKeys;
    }

    public List<Map<String, ItemStack>> getRecipeIngredients() {
        return recipeIngredients;
    }

    public BookmarkRecipe getRecipe() {
        return recipe;
    }

    public int getCrafts() {
        return crafts;
    }

    public int addCrafts(int crafts) {
        this.crafts += crafts;
        return this.crafts;
    }

    public int takeFromRemainders(String itemKey, int requestedAmount) {
        if (remainders.containsKey(itemKey)) {
            int remainder = remainders.remove(itemKey);
            int requiredNew = requestedAmount - remainder;
            if (requiredNew > 0) {
                remainders.put(itemKey, -requiredNew);
                return requiredNew;
            } else if (requiredNew < 0) {
                remainders.put(itemKey, -requiredNew);
            }
            return 0;
        } else {
            remainders.put(itemKey, -requestedAmount);
            return requestedAmount;
        }
    }

    public void addToRemainders(String itemKey, int remainder) {
        remainders.compute(itemKey, (k, v) -> {
            int result = (v == null ? 0 : v) + remainder;
            if (result == 0) return null;
            return result;
        });
    }

    public Map<String, Integer> getRemainders() {
        return remainders;
    }
}
