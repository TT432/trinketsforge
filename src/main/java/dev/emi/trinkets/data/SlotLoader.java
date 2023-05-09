package dev.emi.trinkets.data;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.*;
import dev.emi.trinkets.TrinketsMain;
import dev.emi.trinkets.api.SlotType;
import dev.emi.trinkets.api.TrinketEnums.DropRule;
import dev.emi.trinkets.data.SlotLoader.GroupData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class SlotLoader extends SimplePreparableReloadListener<Map<String, GroupData>> {
	public static final SlotLoader INSTANCE = new SlotLoader();

	private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();
	private static final int FILE_SUFFIX_LENGTH = ".json".length();

	private Map<String, GroupData> slots = new HashMap<>();

	@Override
	protected Map<String, GroupData> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
		Map<String, GroupData> map = new HashMap<>();
		String dataType = "slots";
		for (Map.Entry<ResourceLocation, List<Resource>> entry : resourceManager.listResourceStacks(dataType, id -> id.getPath().endsWith(".json")).entrySet()) {
			ResourceLocation identifier = entry.getKey();

			if (identifier.getNamespace().equals(TrinketsMain.MOD_ID)) {

				try {
					for (Resource resource : entry.getValue()) {
						InputStreamReader reader = new InputStreamReader(resource.open());
						JsonObject jsonObject = GsonHelper.fromJson(GSON, reader, JsonObject.class);

						if (jsonObject != null) {
							String path = identifier.getPath();
							String[] parsed = path.substring(dataType.length() + 1, path.length() - FILE_SUFFIX_LENGTH).split("/");
							String groupName = parsed[0];
							String fileName = parsed[parsed.length - 1];
							GroupData group = map.computeIfAbsent(groupName, (k) -> new GroupData());

							try {
								if (fileName.equals("group")) {
									group.read(jsonObject);
								} else {
									SlotData slot = group.slots.computeIfAbsent(fileName, (k) -> new SlotData());
									slot.read(jsonObject);
								}
							} catch (JsonSyntaxException e) {
								TrinketsMain.LOGGER.error("[trinkets] Syntax error while reading data for " + path);
								e.printStackTrace();
							}
						}
					}
				} catch (IOException e) {
					TrinketsMain.LOGGER.error("[trinkets] Unknown IO error while reading slot data!");
					e.printStackTrace();
				}
			}
		}
		return map;
	}

	@Override
	protected void apply(Map<String, GroupData> loader, ResourceManager manager, ProfilerFiller profiler) {
		this.slots = loader;
	}

	public Map<String, GroupData> getSlots() {
		return ImmutableMap.copyOf(this.slots);
	}

	static class GroupData {

		private int slotId = -1;
		private int order = 0;
		private final Map<String, SlotData> slots = new HashMap<>();

		void read(JsonObject jsonObject) {
			slotId = GsonHelper.getAsInt(jsonObject, "slot_id", slotId);
			order = GsonHelper.getAsInt(jsonObject, "order", order);
		}

		int getSlotId() {
			return slotId;
		}

		int getOrder() {
			return order;
		}

		SlotData getSlot(String name) {
			return slots.get(name);
		}
	}

	static class SlotData {
		private static final Set<ResourceLocation> DEFAULT_QUICK_MOVE_PREDICATES = ImmutableSet.of(new ResourceLocation("trinkets", "all"));
		private static final Set<ResourceLocation> DEFAULT_VALIDATOR_PREDICATES = ImmutableSet.of(new ResourceLocation("trinkets", "tag"));
		private static final Set<ResourceLocation> DEFAULT_TOOLTIP_PREDICATES = ImmutableSet.of(new ResourceLocation("trinkets", "all"));

		private int order = 0;
		private int amount = -1;
		private String icon = "";
		private final Set<String> quickMovePredicates = new HashSet<>();
		private final Set<String> validatorPredicates = new HashSet<>();
		private final Set<String> tooltipPredicates = new HashSet<>();
		private String dropRule = DropRule.DEFAULT.toString();

		SlotType create(String group, String name) {
			ResourceLocation finalIcon = new ResourceLocation(icon);
			finalIcon = new ResourceLocation(finalIcon.getNamespace(), "textures/" + finalIcon.getPath() + ".png");
			Set<ResourceLocation> finalValidatorPredicates = validatorPredicates.stream().map(ResourceLocation::new).collect(Collectors.toSet());
			Set<ResourceLocation> finalQuickMovePredicates = quickMovePredicates.stream().map(ResourceLocation::new).collect(Collectors.toSet());
			Set<ResourceLocation> finalTooltipPredicates = tooltipPredicates.stream().map(ResourceLocation::new).collect(Collectors.toSet());
			if (finalValidatorPredicates.isEmpty()) {
				finalValidatorPredicates = DEFAULT_VALIDATOR_PREDICATES;
			}
			if (finalQuickMovePredicates.isEmpty()) {
				finalQuickMovePredicates = DEFAULT_QUICK_MOVE_PREDICATES;
			}
			if (finalTooltipPredicates.isEmpty()) {
				finalTooltipPredicates = DEFAULT_TOOLTIP_PREDICATES;
			}
			if (amount == -1) {
				amount = 1;
			}
			return new SlotType(group, name, order, amount, finalIcon, finalQuickMovePredicates, finalValidatorPredicates,
				finalTooltipPredicates, DropRule.valueOf(dropRule));
		}

		void read(JsonObject jsonObject) {
			boolean replace = GsonHelper.getAsBoolean(jsonObject, "replace", false);

			order = GsonHelper.getAsInt(jsonObject, "order", order);

			int jsonAmount = GsonHelper.getAsInt(jsonObject, "amount", amount);
			amount = replace ? jsonAmount : Math.max(jsonAmount, amount);

			icon = GsonHelper.getAsString(jsonObject, "icon", icon);

			JsonArray jsonQuickMovePredicates = GsonHelper.getAsJsonArray(jsonObject, "quick_move_predicates", new JsonArray());

			if (jsonQuickMovePredicates != null) {

				if (replace && jsonQuickMovePredicates.size() > 0) {
					quickMovePredicates.clear();
				}

				for (JsonElement jsonQuickMovePredicate : jsonQuickMovePredicates) {
					quickMovePredicates.add(jsonQuickMovePredicate.getAsString());
				}
			}

			String jsonDropRule = GsonHelper.getAsString(jsonObject, "drop_rule", dropRule).toUpperCase();

			if (DropRule.has(jsonDropRule)) {
				dropRule = jsonDropRule;
			}
			JsonArray jsonValidatorPredicates = GsonHelper.getAsJsonArray(jsonObject, "validator_predicates", new JsonArray());

			if (jsonValidatorPredicates != null) {

				if (replace && jsonValidatorPredicates.size() > 0) {
					validatorPredicates.clear();
				}

				for (JsonElement jsonValidatorPredicate : jsonValidatorPredicates) {
					validatorPredicates.add(jsonValidatorPredicate.getAsString());
				}
			}

			JsonArray jsonTooltipPredicates = GsonHelper.getAsJsonArray(jsonObject, "tooltip_predicates", new JsonArray());

			if (jsonTooltipPredicates != null) {

				if (replace && jsonTooltipPredicates.size() > 0) {
					tooltipPredicates.clear();
				}

				for (JsonElement jsonTooltipPredicate : jsonTooltipPredicates) {
					tooltipPredicates.add(jsonTooltipPredicate.getAsString());
				}
			}
		}
	}
}
