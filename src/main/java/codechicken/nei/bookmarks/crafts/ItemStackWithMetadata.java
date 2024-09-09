package codechicken.nei.bookmarks.crafts;

import net.minecraft.item.ItemStack;

import codechicken.nei.BookmarkPanel;

public class ItemStackWithMetadata {

    private Integer gridIdx;
    private final ItemStack stack;
    private final BookmarkPanel.ItemStackMetadata meta;

    public ItemStackWithMetadata(Integer idx, ItemStack stack, BookmarkPanel.ItemStackMetadata meta) {
        this.gridIdx = idx;
        this.stack = stack;
        this.meta = meta;
    }

    public Integer getGridIdx() {
        return gridIdx;
    }

    public ItemStack getStack() {
        return stack;
    }

    public BookmarkPanel.ItemStackMetadata getMeta() {
        return meta;
    }

    public void setGridIdx(Integer gridIdx) {
        this.gridIdx = gridIdx;
    }

}
