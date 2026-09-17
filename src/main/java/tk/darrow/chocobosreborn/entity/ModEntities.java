package tk.darrow.chocobosreborn.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import tk.darrow.chocobosreborn.ChocobosReborn;

public final class ModEntities {
	public static final DeferredRegister<EntityType<?>> ENTITIES =
			DeferredRegister.create(Registries.ENTITY_TYPE, ChocobosReborn.MOD_ID);

	public static final DeferredHolder<EntityType<?>, EntityType<ChocoboEntity>> CHOCOBO =
			ENTITIES.register("chocobo", () -> EntityType.Builder.of(ChocoboEntity::new, MobCategory.CREATURE)
					.sized(1.75F, ChocoboEntity.ADULT_H)
					.clientTrackingRange(10)
					.build("chocobosreborn:chocobo"));

	public static final DeferredHolder<EntityType<?>, EntityType<KinStewardEntity>> KIN_STEWARD =
			ENTITIES.register("kin_steward", () -> EntityType.Builder.of(KinStewardEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F)
					.clientTrackingRange(10)
					.build("chocobosreborn:kin_steward"));

	public static void attributes(EntityAttributeCreationEvent event) {
		event.put(CHOCOBO.get(), ChocoboEntity.createAttributes().build());
		event.put(KIN_STEWARD.get(), KinStewardEntity.createAttributes().build());
	}

	public static void placements(RegisterSpawnPlacementsEvent event) {
		event.register(CHOCOBO.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				ChocoboEntity::checkSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
	}

	private ModEntities() {
	}
}
