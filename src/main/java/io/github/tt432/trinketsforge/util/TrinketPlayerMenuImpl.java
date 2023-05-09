package io.github.tt432.trinketsforge.util;

import dev.emi.trinkets.Point;
import dev.emi.trinkets.SurvivalTrinketSlot;
import dev.emi.trinkets.TrinketPlayerScreenHandler;
import dev.emi.trinkets.TrinketsClient;
import dev.emi.trinkets.api.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * @author DustW
 */
public interface TrinketPlayerMenuImpl extends TrinketPlayerScreenHandler {
    Data t$data();

    InventoryMenu self();

    class Data {
        private final Map<SlotGroup, Integer> groupNums = new HashMap<>();
        private final Map<SlotGroup, Point> groupPos = new HashMap<>();
        private final Map<SlotGroup, List<Point>> slotHeights = new HashMap<>();
        private final Map<SlotGroup, List<SlotType>> slotTypes = new HashMap<>();
        private final Map<SlotGroup, Integer> slotWidths = new HashMap<>();
        private int trinketSlotStart = 0;
        private int trinketSlotEnd = 0;
        private int groupCount = 0;
    }

    @Override
    default int trinkets$getGroupNum(SlotGroup group) {
        return t$data().groupNums.getOrDefault(group, 0);
    }

    @Nullable
    @Override
    default Point trinkets$getGroupPos(SlotGroup group) {
        return t$data().groupPos.get(group);
    }

    @Nonnull
    @Override
    default List<Point> trinkets$getSlotHeights(SlotGroup group) {
        return t$data().slotHeights.getOrDefault(group, List.of());
    }

    @Nullable
    @Override
    default Point trinkets$getSlotHeight(SlotGroup group, int i) {
        List<Point> points = this.trinkets$getSlotHeights(group);
        return i < points.size() ? points.get(i) : null;
    }

    @Nonnull
    @Override
    default List<SlotType> trinkets$getSlotTypes(SlotGroup group) {
        return t$data().slotTypes.getOrDefault(group, List.of());
    }

    @Override
    default int trinkets$getSlotWidth(SlotGroup group) {
        return t$data().slotWidths.getOrDefault(group, 0);
    }

    @Override
    default int trinkets$getGroupCount() {
        return t$data().groupCount;
    }

    @Override
    default int trinkets$getTrinketSlotStart() {
        return t$data().trinketSlotStart;
    }

    @Override
    default int trinkets$getTrinketSlotEnd() {
        return t$data().trinketSlotEnd;
    }

    @Override
    default void trinkets$updateTrinketSlots(boolean slotsChanged) {
        TrinketsApi.getTrinketComponent(self().owner).ifPresent(trinkets -> {
            if (slotsChanged) trinkets.update();
            Map<String, SlotGroup> groups = trinkets.getGroups();
            t$data().groupPos.clear();
            while (t$data().trinketSlotStart < t$data().trinketSlotEnd) {
                self().slots.remove(t$data().trinketSlotStart);
                ((InventoryMenu) (Object) this).lastSlots.remove(t$data().trinketSlotStart);
                ((InventoryMenu) (Object) this).remoteSlots.remove(t$data().trinketSlotStart);
                t$data().trinketSlotEnd--;
            }

            int groupNum = 1; // Start at 1 because offhand exists

            for (SlotGroup group : groups.values().stream().sorted(Comparator.comparing(SlotGroup::getOrder)).toList()) {
                if (!hasSlots(trinkets, group)) {
                    continue;
                }
                int id = group.getSlotId();
                if (id != -1) {
                    if (self().slots.size() > id) {
                        Slot slot = self().slots.get(id);
                        if (!(slot instanceof SurvivalTrinketSlot)) {
                            t$data().groupPos.put(group, new Point(slot.x, slot.y));
                            t$data().groupNums.put(group, -id);
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
                    t$data().groupPos.put(group, new Point(x, y));
                    t$data().groupNums.put(group, groupNum);
                    groupNum++;
                }
            }

            t$data().groupCount = Math.max(0, groupNum - 4);
            t$data().trinketSlotStart = self().slots.size();
            t$data().slotWidths.clear();
            t$data().slotHeights.clear();
            t$data().slotTypes.clear();

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
                for (Map.Entry<String, TrinketInventory> slot : entry.getValue().entrySet().stream()
                        .sorted(Comparator.comparingInt(a -> a.getValue().getSlotType().getOrder())).toList()) {
                    TrinketInventory stacks = slot.getValue();
                    if (stacks.getContainerSize() == 0) {
                        continue;
                    }
                    int slotOffset = 1;
                    int x = (int) ((groupOffset / 2) * 18 * Math.pow(-1, groupOffset));
                    t$data().slotHeights.computeIfAbsent(group, (k) -> new ArrayList<>()).add(new Point(x, stacks.getContainerSize()));
                    t$data().slotTypes.computeIfAbsent(group, (k) -> new ArrayList<>()).add(stacks.getSlotType());
                    for (int i = 0; i < stacks.getContainerSize(); i++) {
                        int y = (int) (pos.y() + (slotOffset / 2) * 18 * Math.pow(-1, slotOffset));
                        self().addSlot(new SurvivalTrinketSlot(stacks, i, x + pos.x(), y, group, stacks.getSlotType(), i, groupOffset == 1 && i == 0));
                        slotOffset++;
                    }
                    groupOffset++;
                    width++;
                }
                t$data().slotWidths.put(group, width);
            }

            t$data().trinketSlotEnd = self().slots.size();
        });
    }

    private boolean hasSlots(TrinketComponent comp, SlotGroup group) {
        for (TrinketInventory inv : comp.getInventory().get(group.getName()).values()) {
            if (inv.getContainerSize() > 0) {
                return true;
            }
        }
        return false;
    }

    default void qm(Player player, int index, CallbackInfoReturnable<ItemStack> info) {
        Slot slot = self().slots.get(index);

        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            if (index >= t$data().trinketSlotStart && index < t$data().trinketSlotEnd) {
                if (!self().moveItemStackTo(stack, 9, 45, false)) {
                    info.setReturnValue(ItemStack.EMPTY);
                } else {
                    info.setReturnValue(stack);
                }
            } else if (index >= 9 && index < 45) {
                TrinketsApi.getTrinketComponent(player).ifPresent(trinkets -> {
                            for (int i = t$data().trinketSlotStart; i < t$data().trinketSlotEnd; i++) {
                                Slot s = self().slots.get(i);
                                if (!(s instanceof SurvivalTrinketSlot) || !s.mayPlace(stack)) {
                                    continue;
                                }

                                SurvivalTrinketSlot ts = (SurvivalTrinketSlot) s;
                                SlotType type = ts.getType();
                                SlotReference ref = new SlotReference((TrinketInventory) ts.container, ts.getContainerSlot());

                                boolean res = TrinketsApi.evaluatePredicateSet(type.getQuickMovePredicates(), stack, ref, player);

                                if (res) {
                                    if (self().moveItemStackTo(stack, i, i + 1, false)) {
                                        if (player.level.isClientSide) {
                                            TrinketsClient.quickMoveTimer = 20;
                                            TrinketsClient.quickMoveGroup = TrinketsApi.getPlayerSlots(self().owner).get(type.getGroup());
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
