package net.sapo_boi.research.lab;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import net.sapo_boi.research.ResearchMod;
import net.sapo_boi.research.technology.ResearchController;
import net.sapo_boi.research.technology.ResearchSavedData;
import net.sapo_boi.research.technology.Technology;
import net.sapo_boi.research.technology.TechnologyManager;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class LabBlockEntity extends BlockEntity implements MenuProvider, ContainerData {


    private final ItemStackHandler inventory = new ItemStackHandler(10) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == 0) {
                return ForgeHooks.getBurnTime(stack, null) > 0;
            }
            return true;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.of(() -> inventory);

    private int fuelBurnTime;
    private int fuelBurnTimeTotal;
    private int processTimer;
    private int currentCycleDuration; // in ticks

    public LabBlockEntity(BlockPos pos, BlockState state) {
        super(ResearchMod.LAB_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LabBlockEntity lab) {
        lab.tickServer();
    }

    private void tickServer() {
        if (level == null || level.isClientSide) {
            return;
        }

        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }

        ResearchSavedData data = ResearchSavedData.get(server);
        ResourceLocation currentId = data.getCurrentTechnology();
        Technology tech = currentId == null ? null : TechnologyManager.get(currentId);

        boolean canProcess = tech != null && hasIngredients(tech);
        if (tech == null) processTimer = 0;

        if (fuelBurnTime > 0) {
            fuelBurnTime--;

            if (canProcess) {
                processTimer++;
                if (processTimer >= currentCycleDuration) {
                    processTimer = 0;
                    consumeIngredients(tech);
                    ResearchController.advanceCurrentResearch(server, 1);
                }
            }

            setChanged();
        } else {
            if (canProcess) {
                ItemStack fuelStack = inventory.getStackInSlot(0);
                if (!fuelStack.isEmpty()) {
                    int burnTime = ForgeHooks.getBurnTime(fuelStack, null);
                    if (burnTime > 0) {
                        fuelBurnTime = burnTime;
                        fuelBurnTimeTotal = burnTime;
                        fuelStack.shrink(1);
                        currentCycleDuration = Math.max(1, tech.time() * 20);
                        setChanged();
                    }
                }
            }
        }
    }

    private boolean hasIngredients(Technology tech) {
        Map<Item, Integer> needed = new HashMap<>();
        for (ResourceLocation ingredientId : tech.ingredients()) {
            Item item = ForgeRegistries.ITEMS.getValue(ingredientId);
            if (item == null) {
                return false;
            }
            needed.merge(item, 1, Integer::sum);
        }

        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            int found = 0;
            for (int slot = 1; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.is(entry.getKey())) {
                    found += stack.getCount();
                }
            }
            if (found < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void consumeIngredients(Technology tech) {
        Map<Item, Integer> needed = new HashMap<>();
        for (ResourceLocation ingredientId : tech.ingredients()) {
            Item item = ForgeRegistries.ITEMS.getValue(ingredientId);
            if (item != null) {
                needed.merge(item, 1, Integer::sum);
            }
        }

        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            int remaining = entry.getValue();
            for (int slot = 1; slot < inventory.getSlots() && remaining > 0; slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.is(entry.getKey())) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.shrink(take);
                    remaining -= take;
                }
            }
        }
    }

    public IItemHandler getInventory() {
        return inventory;
    }

    public void dropContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(
                        level,
                        worldPosition.getX(),
                        worldPosition.getY(),
                        worldPosition.getZ(),
                        stack
                );
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.research.lab");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new LabMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("FuelBurnTime", fuelBurnTime);
        tag.putInt("FuelBurnTimeTotal", fuelBurnTimeTotal);
        tag.putInt("ProcessTimer", processTimer);
        tag.putInt("CurrentCycleDuration", currentCycleDuration);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        fuelBurnTime = tag.getInt("FuelBurnTime");
        fuelBurnTimeTotal = tag.getInt("FuelBurnTimeTotal");
        processTimer = tag.getInt("ProcessTimer");
        currentCycleDuration = tag.getInt("CurrentCycleDuration");
    }

    // ---- ContainerData implementation ----

    @Override
    public int get(int index) {
        return switch (index) {
            case 0 -> fuelBurnTime;
            case 1 -> fuelBurnTimeTotal;
            case 2 -> processTimer;
            case 3 -> currentCycleDuration;
            default -> 0;
        };
    }

    @Override
    public void set(int index, int value) {
        // Not needed because the server only writes, client only reads via sync
    }

    @Override
    public int getCount() {
        return 4;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return lazyItemHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyItemHandler.invalidate();
    }
}