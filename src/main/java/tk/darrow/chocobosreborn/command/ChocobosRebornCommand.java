package tk.darrow.chocobosreborn.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import tk.darrow.chocobosreborn.entity.ChocoboEntity;
import tk.darrow.chocobosreborn.race.RaceManager;
import tk.darrow.chocobosreborn.race.RaceTrack;
import tk.darrow.chocobosreborn.race.Square;
import tk.darrow.chocobosreborn.race.SquareBuilder;

/**
 * /chocobosreborn square enter|leave|build <track>|race <track|0-5> [fun]
 * Operator helpers for Chocobo Square; the in-game way is Esther and the gates.
 */
public final class ChocobosRebornCommand {
	private ChocobosRebornCommand() {
	}

	public static void register(RegisterCommandsEvent event) {
		CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
		d.register(Commands.literal("chocobosreborn").requires(s -> s.hasPermission(2))
				.then(Commands.literal("square")
						.then(Commands.literal("enter").executes(c -> enter(c.getSource())))
						.then(Commands.literal("leave").executes(c -> {
							RaceManager.leaveSquare(c.getSource().getPlayerOrException());
							return 1;
						}))
						.then(Commands.literal("build")
								.then(Commands.argument("track", StringArgumentType.word()).executes(c -> {
									ServerLevel square = Square.level(c.getSource().getServer());
									if (square == null) {
										c.getSource().sendFailure(Component.translatable("chocobosreborn.square.missing"));
										return 0;
									}
									String id = StringArgumentType.getString(c, "track");
									RaceTrack track;
									try {
										track = RaceTrack.valueOf(id.toUpperCase(java.util.Locale.ROOT));
									} catch (IllegalArgumentException e) {
										c.getSource().sendFailure(Component.literal("Unknown track. One of: "
												+ java.util.Arrays.toString(RaceTrack.values()).toLowerCase(java.util.Locale.ROOT)));
										return 0;
									}
									SquareBuilder.buildPaddock(square);
									SquareBuilder.spawnKeepers(square);
									SquareBuilder.requestKeeperSync();
									SquareBuilder.buildTrack(square, track);
									c.getSource().sendSuccess(() -> Component.literal("Built " + track.id()), true);
									return 1;
								})))
						.then(Commands.literal("race")
								.then(Commands.argument("course", StringArgumentType.word()).executes(c -> race(c.getSource(),
										StringArgumentType.getString(c, "course"), true))
										.then(Commands.literal("fun").executes(c -> race(c.getSource(),
												StringArgumentType.getString(c, "course"), false)))))));
	}

	private static int enter(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (player.getVehicle() instanceof ChocoboEntity bird) {
			return RaceManager.enterSquare(player, bird) ? 1 : 0;
		}
		return RaceManager.enterSquareOnFoot(player) ? 1 : 0;
	}

	private static int race(CommandSourceStack source, String course, boolean ranked)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		RaceTrack track;
		try {
			track = RaceTrack.valueOf(course.toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			int n;
			try {
				n = Integer.parseInt(course);
			} catch (NumberFormatException nfe) {
				source.sendFailure(Component.literal("Unknown course. A track id (c_meadow) or 0–5 of the mounted class."));
				return 0;
			}
			if (n < 0 || n > 5) {
				source.sendFailure(Component.literal("Course index must be 0–5 of the mounted class."));
				return 0;
			}
			return RaceManager.startRace(player, n, ranked) ? 1 : 0;
		}
		return RaceManager.startRace(player, track, ranked) ? 1 : 0;
	}
}
