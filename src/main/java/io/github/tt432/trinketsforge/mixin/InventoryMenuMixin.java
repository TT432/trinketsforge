package io.github.tt432.trinketsforge.mixin;

import com.google.common.collect.ImmutableList;
import dev.emi.trinkets.Point;
import dev.emi.trinkets.SurvivalTrinketSlot;
import dev.emi.trinkets.TrinketPlayerScreenHandler;
import dev.emi.trinkets.TrinketsClient;
import dev.emi.trinkets.api.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Adds trinket slots to the player's screen handler
 *
 * @author Emi
 */
@Mixin(InventoryMenu.class)
public abstract class PlayerScreenHandlerMixin extends AbstractContainerMenu implements TrinketPlayerScreenHandler {
    @Shadow
    @Final
    private Player owner;

    @Unique
    private final Map<SlotGroup, Integer> groupNums = new HashMap<>();
    @Unique
    private final Map<SlotGroup, Point> groupPos = new HashMap<>();
    @Unique
    private final Map<SlotGroup, List<Point>> slotHeights = new HashMap<>();
    @Unique
    private final Map<SlotGroup, List<SlotType>> slotTypes = new HashMap<>();
    @Unique
    private final Map<SlotGroup, Integer> slotWidths = new HashMap<>();
    @Unique
    private int trinketSlotStart = 0;
    @Unique
    private int trinketSlotEnd = 0;
    @Unique
    private int groupCount = 0;
    @Unique
    private Inventory inventory;

    private PlayerScreenHandlerMixin() {
        super(null, 0);
    }

    @Inject(at = @At("RETURN"), method = "<init>")
    private void init(Inventory playerInv, boolean onServer, Player owner, CallbackInfo info) {
        this.inventory = playerInv;
        trinkets$updateTrinketSlots(true);
    }

    @Override
    public void trinkets$updateTrinketSlots(boolean slotsChanged) {
        TrinketsApi.getTrinketComponent(owner).ifPresent(trinkets -> {
            if (slotsChanged) trinkets.update();
            Map<String, SlotGroup> groups = trinkets.getGroups();
            groupPos.clear();
            while (trinketSlotStart < trinketSlotEnd) {
                slots.remove(trinketSlotStart);
                ((InventoryMenu) (Object) this).lastSlots.remove(trinketSlotStart);
                ((InventoryMenu) (Object) this).remoteSlots.remove(trinketSlotStart);
                trinketSlotEnd--;
            }

            int groupNum = 1; // Start at 1 because offhand exists

            for (SlotGroup group : groups.values().stream().sorted(Comparator.comparing(SlotGroup::getOrder)).toList()) {
                if (!hasSlots(trinkets, group)) {
                    continue;
                }
                int id = group.getSlotId();
                if (id != -1) {
                    if (this.slots.size() > id) {
                        Slot slot = this.slots.get(id);
                        if (!(slot instanceof SurvivalTrinketSlot)) {
                            groupPos.put(group, new Point(slot.x, slot.y));
                            groupNums.put(group, -id);
                        }
                    }
                } else {
                    int x = 77;
                    int y;
                    if (groupNum >= 4) {
                        x = 4 - (groupNum / 4) * 18;
                        y = 8 + (groupNum % 4) * 18;
                    } else {
                        y = 62 - groupNum * 18;
                    }
                    groupPos.put(group, new Point(x, y));
                    groupNums.put(group, groupNum);
                    groupNum++;
                }
            }
            groupCount = Math.max(0, groupNum - 4);
            trinketSlotStart = slots.size();
            slotWidths.clear();
            slotHeights.clear();
            slotTypes.clear();
            for (Map.Entry<String, Map<String, TrinketInventory>> entry : trinkets.getInventory().entrySet()) {
                String groupId = entry.getKey();
                SlotGroup group = groups.get(groupId);
                int groupOffset = 1;

                if (group.getSlotId() != -1) {
                    groupOffset++;
                }
                int width = 0;
                Point pos = trinkets$getGroupPos(group);
                if (pos == null) {
                    continue;
                }
                for (Map.Entry<String, TrinketInventory> slot : entry.getValue().entrySet().stream().sorted((a, b) ->
                        Integer.compare(a.getValue().getSlotType().getOrder(), b.getValue().getSlotType().getOrder())).toList()) {
                    TrinketInventory stacks = slot.getValue();
                    if (stacks.getContainerSize() == 0) {
                        continue;
                    }
                    int slotOffset = 1;
                    int x = (int) ((groupOffset / 2) * 18 * Math.pow(-1, groupOffset));
                    slotHeights.computeIfAbsent(group, (k) -> new ArrayList<>()).add(new Point(x, stacks.getContainerSize()));
                    slotTypes.computeIfAbsent(group, (k) -> new ArrayList<>()).add(stacks.getSlotType());
                    for (int i = 0; i < stacks.getContainerSize(); i++) {
                        int y = (int) (pos.y() + (slotOffset / 2) * 18 * Math.pow(-1, slotOffset));
                        this.addSlot(new SurvivalTrinketSlot(stacks, i, x + pos.x(), y, group, stacks.getSlotType(), i, groupOffset == 1 && i == 0));
                        slotOffset++;
                    }
                    groupOffset++;
                    width++;
                }
                slotWidths.put(group, width);
            }

            trinketSlotEnd = slots.size();
        });
    }

    @Unique
    private boolean hasSlots(TrinketComponent comp, SlotGroup group) {
        for (TrinketInventory inv : comp.getInventory().get(group.getName()).values()) {
            if (inv.getContainerSize() > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int trinkets$getGroupNum(SlotGroup group) {
        return groupNums.getOrDefault(group, 0);
    }

    @Nullable
    @Override
    public Point trinkets$getGroupPos(SlotGroup group) {
        return groupPos.get(group);
    }

    @Nonnull
    @Override
    public List<Point> trinkets$getSlotHeights(SlotGroup group) {
        return slotHeights.getOrDefault(group, ImmutableList.of());
    }

    @Nullable
    @Override
    public Point trinkets$getSlotHeight(SlotGroup group, int i) {
        List<Point> points = this.trinkets$getSlotHeights(group);
        return i < points.size() ? points.get(i) : null;
    }

    @Nonnull
    @Override
    public List<SlotType> trinkets$getSlotTypes(SlotGroup group) {
        return slotTypes.getOrDefault(group, ImmutableList.of());
    }

    @Override
    public int trinkets$getSlotWidth(SlotGroup group) {
        return slotWidths.getOrDefault(group, 0);
    }

    @Override
    public int trinkets$getGroupCount() {
        return groupCount;
    }

    @Override
    public int trinkets$getTrinketSlotStart() {
        return trinketSlotStart;
    }

    @Override
    public int trinkets$getTrinketSlotEnd() {
        return trinketSlotEnd;
    }

    @Inject(at = @At("HEAD"), method = "removed")
    private void onClosed(Player player, CallbackInfo info) {
        if (player.level.isClientSide) {
            TrinketsClient.activeGroup = null;
            TrinketsClient.activeType = null;
            TrinketsClient.quickMoveGroup = null;
        }
    }

    @Inject(at = @At("HEAD"), method = "quickMoveStack", cancellable = true)
    private void quickMove(Player player, int index, CallbackInfoReturnable<ItemStack> info) {
        Slot slot = slots.get(index);

        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            if (index >= trinketSlotStart && index < trinketSlotEnd) {
                if (!this.moveItemStackTo(stack, 9, 45, false)) {
                    info.setReturnValue(ItemStack.EMPTY);
                } else {
                    info.setReturnValue(stack);
                }
            } else if (index >= 9 && index < 45) {
                TrinketsApi.getTrinketComponent(player).ifPresent(trinkets -> {
                            for (int i = trinketSlotStart; i < trinketSlotEnd; i++) {
                                Slot s = slots.get(i);
                                if (!(s instanceof SurvivalTrinketSlot) || !s.mayPlace(stack)) {
                                    continue;
                                }

                                SurvivalTrinketSlot ts = (SurvivalTrinketSlot) s;
                                SlotType type = ts.getType();
                                SlotReference ref = new SlotReference((TrinketInventory) ts.container, ts.getContainerSlot());

                                boolean res = TrinketsApi.evaluatePredicateSet(type.getQuickMovePredicates(), stack, ref, player);

                                if (res) {
                                    if (this.moveItemStackTo(stack, i, i + 1, false)) {
                                        if (player.level.isClientSide) {
                                            TrinketsClient.quickMoveTimer = 20;
                                            TrinketsClient.quickMoveGroup = TrinketsApi.getPlayerSlots(this.owner).get(type.getGroup());
                                            if (ref.index() > 0) {
                                                TrinketsClient.quickMoveType = type;
                                            } else {
                                                TrinketsClient.quickMoveType = null;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                );
            }
        }
    }
}
