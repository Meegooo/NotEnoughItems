
package codechicken.nei.bookmarks.crafts.graph;

import codechicken.nei.bookmarks.crafts.ItemStackWithMetadata;

public class ItemGraphNode implements CraftingGraphNode {

    private final ItemStackWithMetadata pinnedItem;

    private int requestedItems = 0;

    public ItemGraphNode(ItemStackWithMetadata pinnedItem) {
        this.pinnedItem = pinnedItem;
    }

    public ItemStackWithMetadata getPinnedItem() {
        return pinnedItem;
    }

    public int getRequestedItems() {
        return requestedItems;
    }

    public void addToRemainders(String itemKey, int remainder) {
        requestedItems -= remainder;
    }

    public int addCrafts(int crafts) {
        this.requestedItems += crafts;
        return this.requestedItems;
    }
}
