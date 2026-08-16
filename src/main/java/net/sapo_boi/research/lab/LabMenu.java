package net.sapo_boi.research.lab;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.sapo_boi.research.ResearchMod;

public class LabMenu extends AbstractContainerMenu {

    private static final int FUEL_SLOT = 0;
    private static final int INGREDIENT_START = 1;
    private static final int INGREDIENT_END = 10;
    private static final int PLAYER_START = 10;
    private static final int PLAYER_END = 46;

    // New slot positions (relative to GUI top-left)
    private static final int INGREDIENT_GRID_X = 30;
    private static final int INGREDIENT_GRID_Y = 17;
    private static final int FUEL_SLOT_X = 140;
    private static final int FUEL_SLOT_Y = 53;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    // Server-side constructor
    public LabMenu(int containerId, Inventory playerInventory, LabBlockEntity blockEntity) {
        super(ResearchMod.LAB_MENU.get(), containerId);
        this.access = ContainerLevelAccess.create(
                playerInventory.player.level(), blockEntity.getBlockPos()
        );
        this.data = blockEntity; // LabBlockEntity implements ContainerData
        addDataSlots(data);
        addSlots(blockEntity.getInventory(), playerInventory);
    }

    // Client-side constructor
    public LabMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(ResearchMod.LAB_MENU.get(), containerId);
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);
        this.data = new SimpleContainerData(4);
        addDataSlots(data);

        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
        IItemHandler handler;
        if (blockEntity instanceof LabBlockEntity labBlockEntity) {
            handler = labBlockEntity.getInventory();
        } else {
            handler = new ItemStackHandler(10);
        }
        addSlots(handler, playerInventory);
    }

    public static LabMenu createClientMenu(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        BlockPos pos = data.readBlockPos();
        return new LabMenu(containerId, playerInventory, pos);
    }

    private void addSlots(IItemHandler handler, Inventory playerInventory) {
        // Fuel slot (index 0)
        addSlot(new FuelSlot(handler, FUEL_SLOT, FUEL_SLOT_X, FUEL_SLOT_Y));

        // 3x3 ingredient grid (indices 1-9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slotIndex = INGREDIENT_START + row * 3 + col;
                addSlot(new SlotItemHandler(handler, slotIndex,
                        INGREDIENT_GRID_X + col * 18,
                        INGREDIENT_GRID_Y + row * 18));
            }
        }

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    public ContainerData getData() {
        return data;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();

            if (index >= INGREDIENT_START && index < INGREDIENT_END) {
                if (!this.moveItemStackTo(stackInSlot, PLAYER_START, PLAYER_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index == FUEL_SLOT) {
                if (!this.moveItemStackTo(stackInSlot, PLAYER_START, PLAYER_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= PLAYER_START && index < PLAYER_END) {
                if (ForgeHooks.getBurnTime(stackInSlot, null) > 0) {
                    if (!this.moveItemStackTo(stackInSlot, FUEL_SLOT, FUEL_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(stackInSlot, INGREDIENT_START, INGREDIENT_END, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stackInSlot.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stackInSlot.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, stackInSlot);
        }

        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.access.evaluate(
                (level, pos) -> level.getBlockState(pos).is(ResearchMod.LAB_BLOCK.get()),
                true
        );
    }

    private static class FuelSlot extends SlotItemHandler {
        public FuelSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return ForgeHooks.getBurnTime(stack, null) > 0;
        }
    }
}