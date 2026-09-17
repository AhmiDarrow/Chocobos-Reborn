package tk.darrow.chocobosreborn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.race.RaceManager;
import tk.darrow.chocobosreborn.race.Square;

/**
 * Chocobo Square gates. ENTRY (placed anywhere): ride a saddled bird into
 * the Square. SHORT / LONG (in the Square): a fun one-on-one heat on course
 * 0 / 1. RETURN: home.
 */
public class SquareGateBlock extends Block {
	public enum Kind implements StringRepresentable {
		ENTRY, SHORT_COURSE, LONG_COURSE, RETURN_GATE;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);

	public SquareGateBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(KIND, Kind.ENTRY));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(KIND);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.PASS;
		}
		use(state, level, sp);
		return InteractionResult.CONSUME;
	}

	/** A ridden bird walked into the gate (ChocoboEntity.aiStep): same as a click. */
	public void rideThrough(BlockState state, Level level, BlockPos pos, ServerPlayer rider, ChocoboEntity bird) {
		use(state, level, rider);
	}

	private void use(BlockState state, Level level, ServerPlayer sp) {
		Kind kind = state.getValue(KIND);
		boolean inSquare = Square.isSquare(level);
		switch (kind) {
			case ENTRY -> {
				if (inSquare) {
					RaceManager.leaveSquare(sp);
				} else if (sp.getVehicle() instanceof ChocoboEntity bird) {
					RaceManager.enterSquare(sp, bird);
				} else {
					sp.displayClientMessage(Component.translatable("chocobosreborn.square.need_bird"), true);
				}
			}
			case SHORT_COURSE, LONG_COURSE -> {
				if (!inSquare) {
					sp.displayClientMessage(Component.translatable("chocobosreborn.gate.only_square"), true);
				} else {
					RaceManager.startRace(sp, kind == Kind.SHORT_COURSE ? 0 : 1, false);
				}
			}
			case RETURN_GATE -> RaceManager.leaveSquare(sp);
		}
	}
}
