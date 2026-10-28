package fr.mange.airwavesutilities;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sends a short-lived, server-side marker only to the player who ran the scan. */
public final class AirwavesDetectorMarkers {
    private static final int DURATION_TICKS = 60;
    private static final int BEAM_HEIGHT = 64;
    private static final Map<UUID, Marker> MARKERS = new HashMap<>();

    private AirwavesDetectorMarkers() { }

    public static void show(ServerPlayer player, net.minecraft.core.BlockPos pos, int radius) {
        MARKERS.put(player.getUUID(), new Marker(player, pos.immutable(), radius,
                player.serverLevel().getGameTime() + DURATION_TICKS));
    }

    public static void clear(ServerPlayer player) {
        MARKERS.remove(player.getUUID());
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        MARKERS.entrySet().removeIf(entry -> {
            Marker marker = entry.getValue();
            if (marker.expiresAt() <= now || marker.player().isRemoved()) return true;
            if (marker.player().serverLevel() != level) return false;
            draw(level, marker.player(), marker.pos(), marker.radius());
            return false;
        });
    }

    private static void draw(ServerLevel level, ServerPlayer player, net.minecraft.core.BlockPos pos, int radius) {
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        double y = pos.getY() + 0.15;
        int points = Math.max(64, radius * 2);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true,
                    cx + Math.cos(angle) * radius, y, cz + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);
        }
        // A tall particle column gives a beacon-like cue without placing a real beacon block.
        int beamHeight = Math.min(BEAM_HEIGHT, level.getMaxBuildHeight() - pos.getY() - 1);
        for (int i = 1; i <= beamHeight; i++) {
            level.sendParticles(player, ParticleTypes.END_ROD, true,
                    cx, y + i, cz, 1, 0.025, 0.025, 0.025, 0.002);
        }
        level.sendParticles(player, ParticleTypes.END_ROD, true,
                cx, y + 0.1, cz, 8, 0.25, 0.15, 0.25, 0.025);
    }

    private record Marker(ServerPlayer player, net.minecraft.core.BlockPos pos, int radius, long expiresAt) { }
}
