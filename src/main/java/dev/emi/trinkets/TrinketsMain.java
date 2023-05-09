package dev.emi.trinkets;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;

import dev.emi.trinkets.api.LivingEntityTrinketComponent;
import dev.emi.trinkets.api.SlotGroup;
import dev.emi.trinkets.api.SlotType;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketsApi;
import dev.emi.trinkets.data.EntitySlotLoader;
import dev.emi.trinkets.data.SlotLoader;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.LivingEntity;

public class TrinketsMain implements ModInitializer, EntityComponentInitializer {

	public static final String MOD_ID = "trinkets";
	public static final Logger LOGGER = LogManager.getLogger();

	@Override
	public void onInitialize() {
		ResourceManagerHelper resourceManagerHelper = ResourceManagerHelper.get(PackType.SERVER_DATA);
		resourceManagerHelper.registerReloadListener(SlotLoader.INSTANCE);
		resourceManagerHelper.registerReloadListener(EntitySlotLoader.SERVER);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, serverResourceManager, success)
				-> EntitySlotLoader.SERVER.sync(server.getPlayerList().getPlayers()));
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> 
			dispatcher.register(literal("trinkets")
				.requires(source -> source.hasPermission(2))
				.then(
					literal("set")
					.then(
						argument("group", string())
						.then(
							argument("slot", string())
							.then(
								argument("offset", integer(0))
								.then(
									argument("stack", ItemArgument.item(registry))
									.executes(context -> {
										try {
										return trinketsCommand(context, 1);

										} catch (Exception e) {
											e.printStackTrace();
											return -1;
										}
									})
									.then(
										argument("count", integer(1))
										.executes(context -> {
											int amount = context.getArgument("amount", Integer.class);
											return trinketsCommand(context, amount);
										})
									)
								)
							)
						)
					)
				)
			));
	}

	private static int trinketsCommand(CommandContext<CommandSourceStack> context, int amount) {
		try {
			String group = context.getArgument("group", String.class);
			String slot = context.getArgument("slot", String.class);
			int offset = context.getArgument("offset", Integer.class);
			ItemInput stack = context.getArgument("stack", ItemInput.class);
			ServerPlayer player = context.getSource().getPlayer();
			if (player != null) {
				TrinketComponent comp = TrinketsApi.getTrinketComponent(player).get();
				SlotGroup slotGroup = comp.getGroups().getOrDefault(group, null);
				if (slotGroup != null) {
					SlotType slotType = slotGroup.getSlots().getOrDefault(slot, null);
					if (slotType != null) {
						if (offset >= 0 && offset < slotType.getAmount()) {
							comp.getInventory().get(group).get(slot).setItem(offset, stack.createItemStack(amount, true));
							return Command.SINGLE_SUCCESS;
						} else {
							context.getSource().sendFailure(Component.literal(offset + " offset does not exist for slot"));
						}
					} else {
						context.getSource().sendFailure(Component.literal(slot + " does not exist"));
					}
				} else {
					context.getSource().sendFailure(Component.literal(group + " does not exist"));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
	}

	@Override
	public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
		registry.registerFor(LivingEntity.class, TrinketsApi.TRINKET_COMPONENT, LivingEntityTrinketComponent::new);
		registry.registerForPlayers(TrinketsApi.TRINKET_COMPONENT, LivingEntityTrinketComponent::new, RespawnCopyStrategy.ALWAYS_COPY);
	}
}