package fr.mange.airwavesutilities;

import com.airwaves.api.AirwavesApi;
import com.airwaves.api.RadioStation;
import com.airwaves.api.Reception;
import com.airwaves.ModRegistry;
import com.airwaves.Tuning;
import com.airwaves.logic.RadioNetwork;
import com.airwaves.logic.WeatherUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import org.openjdk.nashorn.internal.objects.Global;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class AirwavesDetectorItem extends Item {
    private static final int SEARCH_STEP = 12;
    private static final int MIN_TRANSMITTER_DISTANCE = 8;
    private static final int MAX_SITES = 3000;
    private static final int CLIMB_MAX_MOVES = 3; // Déplacements max par échelle
    private static final int[] CLIMB_STEPS = {8, 4, 2, 1};
    private static final double JAMMER_SCORE_PENALTY = 0.12;
    private static final double PROMINENCE_WEIGHT = 0.12;
    private static final double TARGET_WEIGHT = 0.30;

    public AirwavesDetectorItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        Level level = context.getLevel();
        if (!level.isClientSide()) {
            BlockPos clicked =  context.getClickedPos();
            context.getItemInHand().set(AirwavesUtilities.TARGET.get(), GlobalPos.of(level.dimension(), clicked));
            player.displayClientMessage(Component.literal(String.format("Cible enregistrée : %d %d %d",
                    clicked.getX(), clicked.getY(), clicked.getZ())).withStyle(ChatFormatting.AQUA), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack detector = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(detector, true);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer))
            return InteractionResultHolder.fail(detector);

        if (serverPlayer.isShiftKeyDown()) {
            if (detector.remove(AirwavesUtilities.TARGET.get()) != null) {
                serverPlayer.displayClientMessage(Component.literal("Cible effacée !")
                        .withStyle(ChatFormatting.GRAY), false);
            }
            return InteractionResultHolder.success(detector);
        }

        BlockPos targetPos = null;
        GlobalPos target = detector.get(AirwavesUtilities.TARGET.get());
        if (target != null) {
            if (target.dimension().equals(serverLevel.dimension())) {
                targetPos = target.pos();
            }
            else {
                serverPlayer.displayClientMessage(Component.literal("La cible est dans une autre dimension : elle est ignorée")
                .withStyle(ChatFormatting.YELLOW), false);
            }
        }
        scan(serverLevel, serverPlayer, targetPos);
        return InteractionResultHolder.success(detector);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos target = stack.get(AirwavesUtilities.TARGET.get());
        tooltip.add(Component.literal(target == null ? "Aucune cible (Maj + clic droit sur un bloc)"
                : String.format("Cible : %d %d %d", target.pos().getX(), target.pos().getY(), target.pos().getZ()))
                .withStyle(ChatFormatting.GRAY));
    }

    private static void scan(ServerLevel level, ServerPlayer player,  BlockPos target) {
        AirwavesDetectorMarkers.clear(player);
        List<RadioStation> allStations = AirwavesApi.stations(level).stream()
                .sorted((a, b) -> Double.compare(a.pos().distSqr(player.blockPosition()),
                        b.pos().distSqr(player.blockPosition()))).toList();
        if (allStations.isEmpty()) {
            player.displayClientMessage(Component.literal("Aucune station Airwaves détectée.")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }

        ItemStack frequencyCard = player.getOffhandItem();
        Tuning selected = frequencyCard.getItem() == BuiltInRegistries.ITEM.get(
                ResourceLocation.parse("airwaves:frequency_card"))
                ? frequencyCard.get(ModRegistry.TUNING.get()) : null;
        Map<Tuning, RadioStation> frequencies = new LinkedHashMap<>();
        for (RadioStation station : allStations) {
            if (selected == null || selected.equals(station.tuning()))
                frequencies.putIfAbsent(station.tuning(), station);
        }
        if (frequencies.isEmpty()) {
            player.displayClientMessage(Component.literal("Aucune station active sur la fréquence de la carte tenue en main secondaire.")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }

        List<BlockPos> jammers = List.of();
        try {
            jammers = RadioNetwork.jammers(level);
        } catch (LinkageError ignored) { }

        // 1) Sites candidats : un anneau autour de chaque station suivie, sur une grille alignée sur le monde
        Set<Long> seen = new HashSet<>();
        Set<Long> peakSeen = new HashSet<>();
        List<BlockPos> sites = new ArrayList<>();
        for (RadioStation center : frequencies.values()) {
            double range = Math.max(64.0, center.range());
            double minDistance = Math.max(MIN_TRANSMITTER_DISTANCE + 8.0, range * 0.42);
            double maxDistance = range * 0.90;
            if (maxDistance <= minDistance) maxDistance = minDistance + 24.0;
            int extent = (int) Math.ceil(maxDistance);
            int startX = Math.floorDiv(center.pos().getX() - extent, SEARCH_STEP) * SEARCH_STEP;
            int startZ = Math.floorDiv(center.pos().getZ() - extent, SEARCH_STEP) * SEARCH_STEP;
            for (int px = startX; px <= center.pos().getX() + extent; px += SEARCH_STEP) {
                for (int pz = startZ; pz <= center.pos().getZ() + extent; pz += SEARCH_STEP) {
                    if (sites.size() >= MAX_SITES) continue;
                    double dx = px - center.pos().getX();
                    double dz = pz - center.pos().getZ();
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    if (distance < minDistance || distance > maxDistance) continue;
                    if (!seen.add(BlockPos.asLong(px, 0, pz))) continue;
                    if (!level.isLoaded(new BlockPos(px, player.blockPosition().getY(), pz))) continue;
                    int py = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
                    BlockPos site = climbToPeak(level, new BlockPos(px, py, pz));
                    if (!peakSeen.add(site.asLong())) continue;
                    if (!validSite(level, site) || isTooCloseToTransmitter(site, allStations)) continue;
                    sites.add(site);
                }
            }
        }

        double nearestToTarget = 0.0;
        double farthestFromTarget = 0.0;
        if (target != null && !sites.isEmpty()) {
            nearestToTarget = Double.MAX_VALUE;
            for (BlockPos candidateSite : sites) {
                double d = Math.sqrt(candidateSite.distSqr(target));
                nearestToTarget = Math.min(nearestToTarget, d);
                farthestFromTarget = Math.max(farthestFromTarget, d);
            }
        }

        // 2) Évaluation de chaque site
        Candidate best = null;
        for (BlockPos pos : sites) {
            // Station de référence : la plus proche du site, par rapport à sa portée
            RadioStation reference = null;
            double distanceFraction = Double.MAX_VALUE;
            for (RadioStation station : frequencies.values()) {
                double sx = pos.getX() - station.pos().getX();
                double sz = pos.getZ() - station.pos().getZ();
                double fraction = Math.sqrt(sx * sx + sz * sz) / Math.max(64.0, station.range());
                if (fraction < distanceFraction) {
                    distanceFraction = fraction;
                    reference = station;
                }
            }
            if (reference == null) continue;

            double qualitySum = 0.0;
            double uncertaintySum = 0.0;
            int evaluated = 0;
            int received = 0;
            for (RadioStation station : frequencies.values()) {
                Reception reception = AirwavesApi.listen(level, pos, pos.getY() + 4.0,
                        station.tuning(), 1.0);
                RadioStation receivedStation = allStations.stream()
                        .filter(candidate -> candidate.id() == reception.stationId())
                        .findFirst().orElse(null);
                if (receivedStation == null || !receivedStation.tuning().equals(station.tuning())) {
                    uncertaintySum += Math.min(0.45, 0.30
                        + (level.isRainingAt(pos) ? 0.08 : 0.0)
                        + (level.isThundering() ? 0.16 : 0.0));
                    evaluated++;
                    continue;
                }
                int obstacles = countObstacles(level, pos, receivedStation.pos());
                double uncertainty = Math.min(0.45, 0.035 + obstacles * 0.035
                        + (level.isRainingAt(pos) ? 0.08 : 0.0)
                        + (level.isThundering() ? 0.16 : 0.0)
                        + Math.max(0.0, 1.0 - reception.quality()) * 0.18);
                qualitySum += reception.quality();
                uncertaintySum += uncertainty;
                evaluated++;
                received++;
            }
            if (evaluated == 0 || received == 0) continue;
            double quality = qualitySum / evaluated;
            double uncertainty = uncertaintySum / evaluated;
            double preferedDistanceFraction = clamp(0.52 + quality * 0.28 - uncertainty * 0.12,
                    0.48, 0.82);
            double distanceScore = clamp(1.0 - Math.abs(distanceFraction - preferedDistanceFraction) / 0.34,
                    0.0, 1.0);
            double elevationScore = clamp((pos.getY() - reference.antennaTop()) / 64.0, 0.0, 1.0);

            // Alignement avec le joueur, vu depuis la station de référence
            double toPlayerX = player.getX() - (reference.pos().getX() + 0.5);
            double toPlayerZ = player.getZ() - (reference.pos().getZ() + 0.5);
            double playerLength = Math.sqrt(toPlayerX * toPlayerX + toPlayerZ * toPlayerZ);
            if (playerLength <= 1.0) {
                toPlayerX = player.getLookAngle().x;
                toPlayerZ = player.getLookAngle().z;
                playerLength = Math.sqrt(toPlayerX * toPlayerX + toPlayerZ * toPlayerZ);
            }
            double toSiteX = pos.getX() - reference.pos().getX();
            double toSiteZ = pos.getZ() - reference.pos().getZ();
            double siteLength = Math.sqrt(toSiteX * toSiteX + toSiteZ * toSiteZ);
            double playerAlignment = 0.5;
            if (playerLength > 0.001 && siteLength > 1.0) {
                double alignment = (toSiteX * toPlayerX + toSiteZ * toPlayerZ) / (siteLength * playerLength);
                playerAlignment = (alignment + 1.0) * 0.5;
            }

            // Proximité d'un brouilleur actif vis-à-vis du trajet du signal
            double nearestJammer = Double.MAX_VALUE;
            for (BlockPos jammer : jammers)
                nearestJammer = Math.min(nearestJammer, distanceToSegment(jammer, pos, reference.pos()));
            double jamRisk = nearestJammer == Double.MAX_VALUE ? 0.0
                    : clamp(1.0 - (nearestJammer - JAMMER_RANGE) / JAMMER_RANGE, 0.0, 1.0);

            double prominence = prominence(level, pos);

            double targetProgress = 0.0;
            if (target != null &&  farthestFromTarget - nearestToTarget > 1.0) {
                double siteToTarget = Math.sqrt(pos.distSqr(target));
                targetProgress = 1.0 - (siteToTarget - nearestToTarget) / (farthestFromTarget - nearestToTarget);
            }

            double score = quality * 0.48 + distanceScore * 0.27 + elevationScore * 0.13
                    + playerAlignment * (target != null ? 0.0 : 0.07) + targetProgress * TARGET_WEIGHT
                    + prominence * PROMINENCE_WEIGHT - uncertainty * 0.20 - jamRisk * JAMMER_SCORE_PENALTY;
            if (best == null || score > best.score())
                best = new Candidate(pos, (float) quality, uncertainty, score);
        }

        if (best == null) {
            player.displayClientMessage(Component.literal("Aucun site exploitable trouvé dans la portée chargée autour des émetteurs. " +
                    "Avance vers la zone ou augmente la distance de rendu, puis relance le scan")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }

        Zone zone = computeZone(level, best.pos(), best.quality(), frequencies, allStations);
        AirwavesDetectorMarkers.show(player, best.pos(), zone.radius());

        String frequencyDescription = selected != null ? selected.display()
                : frequencies.size() == 1 ? frequencies.keySet().iterator().next().display()
                : "moyenne de " + frequencies.size() + " fréquences";
        player.displayClientMessage(Component.literal(String.format(
                "Site conseillé : %d, %d, %d | %s | réception moyenne %.0f%% ± %.0f%% | zone ~%d blocs",
                best.pos().getX(), best.pos().getY(), best.pos().getZ(), frequencyDescription,
                best.quality() * 100.0, best.uncertainty() * 100.0, zone.radius()))
                .withStyle(ChatFormatting.GREEN), false);
        player.displayClientMessage(Component.literal("Détail de la zone : " + zone.describe())
                .withStyle(ChatFormatting.GRAY), false);
        if (target != null) {
            player.displayClientMessage(Component.literal(String.format(
                    "Cible : %d %d %d | à %.0f blocs du site conseillé",
                    target.getX(), target.getY(), target.getZ(), Math.sqrt(best.pos().distSqr(target))))
                    .withStyle(ChatFormatting.AQUA), false);
        }
    }

    private static final int OBSTACLE_CAP = 10;

    private static final double JAMMER_RANGE = 48.0; //Portée annoncée par l'infobulle du brouilleur

    private record Zone(int radius, double weakSignal, double weather, double terrain,
                        double jammers, double obstacles) {
        String describe() {
            StringBuilder sb = new StringBuilder("base ").append(UtilitiesConfig.ZONE_BASE_RADIUS.get());
            append(sb, "signal faible", weakSignal);
            append(sb, "météo", weather);
            append(sb, "relief", terrain);
            append(sb, "brouilleurs", jammers);
            append(sb, "obstacles", obstacles);
            return sb.toString();
        }

        private static void append(StringBuilder sb, String label, double blocks) {
            long rounded = Math.round(blocks);
            if (rounded > 0) sb.append(" | +").append(rounded).append(' ').append(label);
        }
    }

    private static Zone computeZone(ServerLevel level, BlockPos pos, double quality,
                                    Map<Tuning, RadioStation> frequences, List<RadioStation> allStations) {
        List<BlockPos> jammers = List.of();
        try {
            jammers = RadioNetwork.jammers(level);
        } catch (LinkageError ignored) { }

        double nearestJammer = Double.MAX_VALUE;
        for (RadioStation station : frequences.values()) {
            for (BlockPos jammer : jammers) {
                nearestJammer = Math.min(nearestJammer, distanceToSegment(jammer, pos, station.pos()));
            }
        }
        double jamProximity = nearestJammer == Double.MAX_VALUE ? 0.0
                : clamp(1.0 - (nearestJammer - JAMMER_RANGE) / JAMMER_RANGE, 0.0, 1.0);

        double weatherSum = 0.0, terrainSum = 0.0, jamSum = 0.0, obstacleSum = 0.0;
        int counted = 0;
        for (RadioStation station : frequences.values()) {
            Reception reception = AirwavesApi.listen(level, pos, pos.getY() + 4.0, station.tuning(), 1.0);
            RadioStation heard = allStations.stream()
                    .filter(candidate -> candidate.id() == reception.stationId())
                    .findFirst().orElse(null);
            if (heard == null || !heard.tuning().equals(station.tuning())) continue;
            counted++;
            obstacleSum += Math.min(countObstacles(level, pos, heard.pos()), OBSTACLE_CAP)
                    / (double) OBSTACLE_CAP;
            try {
                boolean fm = heard.tuning().fm();
                jamSum += Math.max(0.0, 1.0 - RadioNetwork.jamming(pos, heard.pos(), jammers));
                terrainSum += Math.max(0.0, 1.0 - RadioNetwork.terrain(level, heard.pos(),
                        heard.antennaTop(), pos, pos.getY() + 4.0, fm));
                weatherSum += weatherMargin(level, pos, fm);
            } catch (LinkageError ignored) { }
        }
        double n = Math.max(1, counted);
        double weak = (1.0 - quality) * UtilitiesConfig.ZONE_WEAK_SIGNAL.get();
        double weather = weatherSum / n * UtilitiesConfig.ZONE_WEATHER.get();
        double terrain = terrainSum / n * UtilitiesConfig.ZONE_TERRAIN.get();
        double jam = Math.max(jamSum / n, jamProximity) * UtilitiesConfig.ZONE_JAMMERS.get();
        double obstacles = obstacleSum / n * UtilitiesConfig.ZONE_OBSTACLES.get();

        int base = UtilitiesConfig.ZONE_BASE_RADIUS.get();
        int max = UtilitiesConfig.ZONE_MAX_RADIUS.get();
        int radius = (int) Math.round(clamp(base + weak + weather + terrain + jam + obstacles, base, max));
        return new Zone(radius, weak, weather, terrain, jam, obstacles);
    }

    /** Part de réception qu'un orage ferait perdre ici, d'après les formules d'Airwaves */
    private static double weatherMargin(ServerLevel level, BlockPos pos, boolean fm) {
        float current = WeatherUtil.factor(level, pos, fm);
        if (current <= 0.0f) return 0.0;
        float sky = level.canSeeSky(pos.above()) ? 1.0f : 0.5f;
        float storm = Math.max(0.2f, 1.0f - WeatherUtil.penalty(1.0f, 1.0f, fm) * sky);
        return Math.max(0.0, 1.0 - storm / current);
    }

    /** Distance entre un point et le segment [a, b], comme Airwaves la mesure pour le brouillage */
    private static double distanceToSegment(BlockPos point, BlockPos a, BlockPos b) {
        double abx = b.getX() - a.getX(), aby = b.getY() - a.getY(), abz = b.getZ() - a.getZ();
        double apx = point.getX() - a.getX(), apy = point.getY() - a.getY(), apz = point.getZ() - a.getZ();
        double lengthSqr = abx * abx + aby * aby + abz * abz;
        double t = lengthSqr == 0.0 ? 0.0
                : clamp((apx * abx + apy * aby + apz * abz) / lengthSqr, 0.0, 1.0);
        double dx = apx - abx * t, dy = apy - aby * t, dz = apz - abz * t;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isTooCloseToTransmitter(BlockPos pos, List<RadioStation> stations) {
        long minDistanceSqr = (long) MIN_TRANSMITTER_DISTANCE * MIN_TRANSMITTER_DISTANCE;
        for (RadioStation station : stations) {
            long dx = pos.getX() - station.pos().getX();
            long dz = pos.getZ() - station.pos().getZ();
            if (dx * dx + dz * dz < minDistanceSqr) return true;
        }
        return false;
    }

    private static boolean validSite(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || pos.getY() >= level.getMaxBuildHeight() - 24) return false;
        BlockState support = level.getBlockState(pos.below());
        return support.isFaceSturdy(level, pos.below(), Direction.UP)
                && level.getBlockState(pos).canBeReplaced();
    }

    /** Hauteur de la surface (sans feuilles), ou Integer.MIN_VALUE si le chunk n'est pas chargé */
    private static int surfaceY(ServerLevel level, int x, int z, int referenceY) {
        if (level.isLoaded(new BlockPos(x, referenceY, z))) return Integer.MIN_VALUE;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** Monte vers le sommet local : on avance vers le voisin le plus haut, à des échelles décroissantes */
    private static BlockPos climbToPeak(ServerLevel level, BlockPos start) {
        int x = start.getX();
        int y = start.getY();
        int z = start.getZ();
        for (int step : CLIMB_STEPS) {
            for (int move = 0; move < CLIMB_MAX_MOVES; move++) {
                int bestX = x,  bestY = y, bestZ = z;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        int nx = x + dx * step;
                        int nz = z + dz * step;
                        int ny = surfaceY(level, nx, nz, y);
                        if (ny > bestY) {
                            bestX = nx;
                            bestY = ny;
                            bestZ = nz;
                        }
                    }
                }
                if (bestX == x && bestZ == z) break;
                x = bestX;
                z = bestZ;
                y = bestY;
            }
        }
        return new BlockPos(x, y, z);
    }

    /** De 0 (terrain plat ou en creux) à 1 (net sommet) : hauteur du site par rapport au terrain alentour */
    private static double prominence(ServerLevel level, BlockPos site) {
        double sum = 0.0;
        int count = 0;
        for (int radius : new int[] {12, 28}) {
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * 2.0 * i / 8.0;
                int x = site.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = site.getZ() + (int) Math.round(Math.sin(angle) * radius);
                int y = surfaceY(level, x, z, site.getY());
                if (y == Integer.MIN_VALUE) continue;
                sum += y;
                count++;
            }
        }
        if (count < 6) return 0.0;
        return clamp((site.getY() - sum / count) / 24.0, 0.0, 1.0);
    }

    private static int countObstacles(ServerLevel level, BlockPos from, BlockPos to) {
        Vec3 a = Vec3.atCenterOf(from).add(0, 3, 0);
        Vec3 b = Vec3.atCenterOf(to).add(0, 4, 0);
        int steps = Math.max(1, (int) (a.distanceTo(b) / 2.0));
        int blocked = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            cursor.set(Math.floor(a.x + (b.x - a.x) * t), Math.floor(a.y + (b.y - a.y) * t),
                    Math.floor(a.z + (b.z - a.z) * t));
            BlockState state = level.getBlockState(cursor);
            if (!state.is(BlockTags.LEAVES) && !state.getCollisionShape(level, cursor).isEmpty()) blocked++;
        }
        return blocked;
    }

    private record Candidate(BlockPos pos, float quality, double uncertainty, double score) { }
}
