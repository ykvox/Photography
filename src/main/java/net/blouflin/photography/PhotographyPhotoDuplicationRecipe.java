package net.blouflin.photography;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class PhotographyPhotoDuplicationRecipe extends CustomRecipe {
    public static final MapCodec<PhotographyPhotoDuplicationRecipe> MAP_CODEC =
            MapCodec.unit(PhotographyPhotoDuplicationRecipe::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, PhotographyPhotoDuplicationRecipe> STREAM_CODEC =
            StreamCodec.unit(new PhotographyPhotoDuplicationRecipe());

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findRecipe(input).matches();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        RecipeContents contents = findRecipe(input);
        if (!contents.matches()) {
            return ItemStack.EMPTY;
        }
        ItemStack duplicate = contents.source().copy();
        PhotographyPhoto.compactImageInPlace(duplicate);
        duplicate.setCount(Math.min(contents.paperSlots(), duplicate.getMaxStackSize()));
        if (Boolean.getBoolean("photography.debugCaptureImages")) {
            Photography.LOGGER.info("[photo-duplicate] sourceHash={} paperCount={} duplicatesCreated={} originalReturned=true",
                    PhotographyPhoto.getImage(contents.source()).hash(),
                    contents.paperSlots(),
                    duplicate.getCount());
        }
        return duplicate;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (PhotographyPhoto.isPhotographyPhoto(stack)) {
                remaining.set(i, stack.copyWithCount(1));
                break;
            }
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<PhotographyPhotoDuplicationRecipe> getSerializer() {
        return Photography.PHOTO_DUPLICATION_RECIPE_SERIALIZER;
    }

    private static RecipeContents findRecipe(CraftingInput input) {
        ItemStack source = ItemStack.EMPTY;
        int paperSlots = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (PhotographyPhoto.isPhotographyPhoto(stack)) {
                if (!source.isEmpty()) {
                    return RecipeContents.invalid();
                }
                source = stack;
            } else if (PhotographyPaper.isPhotographicPaper(stack)) {
                paperSlots++;
            } else {
                return RecipeContents.invalid();
            }
        }
        return new RecipeContents(source, paperSlots);
    }

    private record RecipeContents(ItemStack source, int paperSlots) {
        private boolean matches() {
            return !source.isEmpty() && paperSlots > 0 && paperSlots <= source.getMaxStackSize();
        }

        private static RecipeContents invalid() {
            return new RecipeContents(ItemStack.EMPTY, 0);
        }
    }
}
