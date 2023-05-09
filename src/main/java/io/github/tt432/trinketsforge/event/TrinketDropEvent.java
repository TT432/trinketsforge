package io.github.tt432.trinketsforge.event;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketEnums;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;

/**
 * @author DustW
 */
public class TrinketDropEvent extends Event {
    private TrinketEnums.DropRule rule;
    private final ItemStack stack;
    private final SlotReference ref;
    private final LivingEntity entity;

    public TrinketDropEvent(TrinketEnums.DropRule rule, ItemStack stack, SlotReference ref, LivingEntity entity) {
        this.rule = rule;
        this.stack = stack;
        this.ref = ref;
        this.entity = entity;
    }

    public TrinketEnums.DropRule getRule() {
        return rule;
    }

    public void setRule(TrinketEnums.DropRule rule) {
        this.rule = rule;
    }

    public ItemStack getStack() {
        return stack;
    }

    public SlotReference getRef() {
        return ref;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public static TrinketEnums.DropRule onTrinketDrop(TrinketEnums.DropRule rule, ItemStack stack, SlotReference ref, LivingEntity entity) {
        var event = new TrinketDropEvent(rule, stack, ref, entity);
        MinecraftForge.EVENT_BUS.post(event);
        return event.rule;
    }
}
