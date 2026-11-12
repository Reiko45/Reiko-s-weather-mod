package com.reiko.weather.volcano;

import com.reiko.weather.ReikosWeather;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.entity.VolcanicBombEntity;
import com.reiko.weather.network.VolcanoPayload;
import com.reiko.weather.registry.ModEntities;
import com.reiko.weather.registry.ModStructures;
import com.reiko.weather.volcano.Volcano.Phase;
import com.reiko.weather.weather.StormCell;
import com.reiko.weather.weather.WeatherAdvancements;
import com.reiko.weather.weather.WeatherManager;
import com.reiko.weather.weather.WeatherState;
import com.reiko.weather.weather.WeatherType;
import com.reiko.weather.weather.Wind;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Runs the volcanoes of the overworld. Volcanoes are found as players come near the ones world generation
 * placed (or are raised with {@code /rweather volcano create}), then go through a cycle: dormant for days,
 * a few minutes of rumbling, an eruption, and a cooling-off while the spilled lava crusts over.
 */
public final class VolcanoManager {
    /** Dormant volcanoes only count down to their next eruption while a player is this close. */
    private static final double WATCH_RANGE = 1024.0;
    /** Clients hear about volcanoes this close (the ash plume can be seen from far away). */
    private static final double SYNC_RANGE = 1536.0;
    private static final int MAX_SPILLS = 800;
    private static final int MAX_FLOWS_PER_ERUPTION = 4;
    private static final int MAX_ASH_CELLS = 6;

    public static VolcanoData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(VolcanoData.FACTORY, VolcanoData.FILE_ID);
    }

    public static boolean handles(Level level) {
        return level.dimension() == Level.OVERWORLD;
    }

    // ------------------------------------------------------------------ events

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !handles(level) || !WeatherConfig.SPEC.isLoaded()) {
            return;
        }
        VolcanoData data = data(level);
        long time = level.getGameTime();
        if (time % 200L == 17L) {
            discover(level, data);
        }
        boolean second = time % 20L == 0L;
        for (Volcano v : data.all()) {
            if (second) {
                v.watched = nearestPlayer(level, v) < WATCH_RANGE;
                checkPlayers(level, v);
                if (v.watched || v.phase != Phase.DORMANT) {
                    data.setDirty();
                }
            }
            tick(level, data, v);
        }
        MassFlows.tick(level, data);
        if (time % 40L == 0L) {
            sync(level, data);
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && handles(player.level())) {
            syncTo(player, data(player.serverLevel()));
        }
    }

    // ------------------------------------------------------------------ discovery

    @Nullable
    private static Structure structure(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ModStructures.VOLCANO_KEY);
    }

    /** Looks for volcanoes generated around players that we don't know about yet. */
    private static void discover(ServerLevel level, VolcanoData data) {
        Structure structure = structure(level);
        if (structure == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            ChunkPos center = player.chunkPosition();
            // a volcano's pieces cover up to 15 chunks across, so a coarse grid of chunks finds every one nearby
            for (int dx = -12; dx <= 12; dx += 4) {
                for (int dz = -12; dz <= 12; dz += 4) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
                    if (chunk == null) {
                        continue;
                    }
                    LongSet refs = chunk.getReferencesForStructure(structure);
                    for (long ref : refs) {
                        if (!data.contains(ref)) {
                            register(level, data, structure, new ChunkPos(ref));
                        }
                    }
                }
            }
        }
    }

    private static void register(ServerLevel level, VolcanoData data, Structure structure, ChunkPos startChunk) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(startChunk.x, startChunk.z);
        if (chunk == null) {
            return; // try again once it loads
        }
        StructureStart start = chunk.getStartForStructure(structure);
        if (start == null || !start.isValid()) {
            return;
        }
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof VolcanoPiece volcanoPiece) {
                Volcano v = new Volcano(volcanoPiece.shape());
                v.enter(Phase.DORMANT, (int) (dormantLength(level.random) * (0.3F + level.random.nextFloat())));
                data.add(v);
                ReikosWeather.LOGGER.debug("Found a volcano at {}, {}", v.shape.cx(), v.shape.cz());
                return;
            }
        }
    }

    /** Raises a new volcano centred on a column (the command). */
    public static Volcano create(ServerLevel level, int x, int z, RandomSource random) {
        VolcanoShape shape = VolcanoStructure.plan(random, x, z, (px, pz) -> level.getHeight(Heightmap.Types.OCEAN_FLOOR, px, pz),
                level.getSeaLevel(), level);
        if (shape == null) {
            return null;
        }
        net.minecraft.world.level.levelgen.structure.BoundingBox box = VolcanoPiece.bounds(shape);
        VolcanoBuilder.build(level, shape, box);
        Volcano v = new Volcano(shape);
        v.enter(Phase.DORMANT, dormantLength(random));
        VolcanoData data = data(level);
        data.add(v);
        sync(level, data);
        return v;
    }

    // ------------------------------------------------------------------ the cycle

    private static int dormantLength(RandomSource random) {
        double days = WeatherConfig.SPEC.isLoaded() ? WeatherConfig.DAYS_BETWEEN_ERUPTIONS.get() : 4.0;
        return Math.max(200, (int) (days * 24000.0 * (0.5 + random.nextDouble())));
    }

    private static void tick(ServerLevel level, VolcanoData data, Volcano v) {
        boolean eruptions = WeatherConfig.ERUPTIONS.get();
        switch (v.phase) {
            case DORMANT -> {
                if (eruptions && v.watched && --v.timer <= 0) {
                    setPhase(level, data, v, Phase.RUMBLING);
                }
            }
            case RUMBLING -> {
                if (!eruptions) {
                    setPhase(level, data, v, Phase.COOLING);
                } else if (--v.timer <= 0) {
                    setPhase(level, data, v, Phase.ERUPTING);
                }
            }
            case ERUPTING -> {
                if (!eruptions) {
                    setPhase(level, data, v, Phase.COOLING);
                    break;
                }
                erupting(level, v);
                if (--v.timer <= 0) {
                    setPhase(level, data, v, Phase.COOLING);
                }
            }
            case COOLING -> {
                crustOver(level, v, 40);
                if (--v.timer <= 0) {
                    setPhase(level, data, v, Phase.DORMANT);
                }
            }
        }
        if (v.phase != Phase.ERUPTING && !v.spills.isEmpty() && level.getGameTime() % 2L == 0L) {
            harden(level, v, v.phase == Phase.DORMANT ? 8 : 1 + v.spills.size() / 300);
        }
    }

    /** Moves a volcano on to another phase (also used by the commands). */
    public static void setPhase(ServerLevel level, VolcanoData data, Volcano v, Phase phase) {
        RandomSource random = level.random;
        int length = switch (phase) {
            case DORMANT -> dormantLength(random);
            case RUMBLING -> 3600 + random.nextInt(2400);
            case ERUPTING -> 6000 + random.nextInt(3600);
            case COOLING -> 3000 + random.nextInt(1200);
        };
        v.enter(phase, length);
        if (phase == Phase.ERUPTING) {
            v.flowsStarted = 0;
            data.lastEruption = level.getDayTime(); // (by the day clock, so sleeping through nights counts too)
            data.setDirty();
            sync(level, data);
            feedAshCloud(level, v, random);
        } else if (phase == Phase.COOLING) {
            v.flows.clear();
            smog(level, v, random);
        } else {
            v.flows.clear();
        }
        if (phase == Phase.DORMANT) {
            harden(level, v, v.spills.size());
        }
        data.setDirty();
        sync(level, data);
        ReikosWeather.LOGGER.debug("Volcano at {}, {} is now {}", v.shape.cx(), v.shape.cz(), phase.id());
    }

    // ------------------------------------------------------------------ eruptions

    private static void erupting(ServerLevel level, Volcano v) {
        RandomSource random = level.random;
        if (level.getGameTime() % 100L == 0L) {
            feedAshCloud(level, v, random);
        }
        BlockPos crater = v.shape.crater();
        if (!level.isLoaded(crater)) {
            return; // nobody near enough to see it: the rest is only show
        }
        float a = v.activity();
        if (WeatherConfig.LAVA_BOMBS.get() && level.isPositionEntityTicking(crater) && random.nextFloat() < 0.12F * a) {
            launchBomb(level, v, random);
        }
        if (random.nextFloat() < 0.012F * a) {
            volcanicLightning(level, v, random);
        }
        MassFlows.maybePyroclastic(level, v, random);
        boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        if (WeatherConfig.LAVA_FLOWS.get() && griefing) {
            if (a > 0.5F && v.flowsStarted < MAX_FLOWS_PER_ERUPTION && v.flows.size() < 2 && random.nextFloat() < 1.0F / 600.0F) {
                startFlow(level, v, random);
            }
            if (level.getGameTime() % 10L == 0L) {
                advanceFlows(level, v, random);
            }
        }
    }

    private static void launchBomb(ServerLevel level, Volcano v, RandomSource random) {
        VolcanoShape s = v.shape;
        double angle = random.nextDouble() * Math.PI * 2.0;
        float a = v.activity();
        double horizontal = 0.25 + random.nextDouble() * 0.9 * (0.5 + 0.5 * a);
        double up = 1.1 + random.nextDouble() * 0.9 * (0.6 + 0.4 * a);
        double spread = s.craterRadius() * 0.4;
        VolcanicBombEntity bomb = new VolcanicBombEntity(ModEntities.VOLCANIC_BOMB.get(), level);
        bomb.setPos(s.cx() + 0.5 + (random.nextDouble() - 0.5) * spread, s.lavaY() + 1.0, s.cz() + 0.5 + (random.nextDouble() - 0.5) * spread);
        bomb.setDeltaMovement(Math.cos(angle) * horizontal, up, Math.sin(angle) * horizontal);
        level.addFreshEntity(bomb);
    }

    /** Lightning crackling in the ash column, striking the upper slopes. */
    private static void volcanicLightning(ServerLevel level, Volcano v, RandomSource random) {
        VolcanoShape s = v.shape;
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = s.craterRadius() + 3 + random.nextDouble() * 40.0;
        BlockPos column = BlockPos.containing(s.cx() + Math.cos(angle) * distance, 0, s.cz() + Math.sin(angle) * distance);
        if (!level.isLoaded(column)) {
            return;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column)));
            level.addFreshEntity(bolt);
        }
    }

    /** Keeps the ash cloud going: a new ash cell over the crater whenever the last one has drifted off downwind. */
    private static void feedAshCloud(ServerLevel level, Volcano v, RandomSource random) {
        if (!WeatherConfig.ASH_FALL.get()) {
            return;
        }
        WeatherState state = WeatherManager.activeState(level);
        if (state == null) {
            return;
        }
        v.ashCells.removeIf(id -> cell(state, id) == null);
        if (v.ashCells.size() >= MAX_ASH_CELLS) {
            return;
        }
        VolcanoShape s = v.shape;
        StormCell newest = v.ashCells.isEmpty() ? null : cell(state, v.ashCells.getInt(v.ashCells.size() - 1));
        if (newest != null && Math.hypot(newest.x - s.cx(), newest.z - s.cz()) < newest.radius * 0.6) {
            return;
        }
        Vec3 wind = Wind.direction(state.windAngle);
        float radius = 140.0F + random.nextFloat() * 80.0F;
        // drifts slowly, so the ash keeps falling around the volcano for the whole eruption
        double speed = 0.05 + random.nextDouble() * 0.03;
        StormCell cell = WeatherManager.spawnCell(level, state, WeatherType.VOLCANIC_ASH, s.cx() + wind.x * radius * 0.4,
                s.cz() + wind.z * radius * 0.4, radius, speed, 7000 + random.nextInt(3000));
        v.ashCells.add(cell.id);
    }

    /** As an eruption dies down it leaves a lingering, sulfurous smog (with an acid drizzle) drifting downwind. */
    private static void smog(ServerLevel level, Volcano v, RandomSource random) {
        WeatherState state = WeatherManager.activeState(level);
        if (state == null || !WeatherConfig.ACID_RAIN.get()) {
            return;
        }
        Vec3 wind = Wind.direction(state.windAngle);
        float radius = 220.0F + random.nextFloat() * 80.0F;
        double speed = 0.03 + random.nextDouble() * 0.02;
        WeatherManager.spawnCell(level, state, WeatherType.VOLCANIC_SMOG, v.shape.cx() + wind.x * radius * 0.5, v.shape.cz() + wind.z * radius * 0.5,
                radius, wind.x * speed, wind.z * speed, 9000 + random.nextInt(4000), 1500, true);
    }

    @Nullable
    private static StormCell cell(WeatherState state, int id) {
        for (StormCell c : state.cells) {
            if (c.id == id) return c;
        }
        return null;
    }

    // ------------------------------------------------------------------ lava flows

    /** The top of the ground at a column (under any trees), or null if it isn't loaded. */
    @Nullable
    private static BlockPos surface(ServerLevel level, int x, int z) {
        BlockPos column = new BlockPos(x, 0, z);
        if (!level.isLoaded(column)) {
            return null;
        }
        BlockPos.MutableBlockPos pos = level.getHeightmapPos(Heightmap.Types.OCEAN_FLOOR, column).mutable();
        for (int i = 0; i < 32; i++) {
            BlockState below = level.getBlockState(pos.below());
            if (!below.is(BlockTags.LOGS) && !below.is(BlockTags.LEAVES)) break;
            pos.move(0, -1, 0);
        }
        return pos.immutable();
    }

    private static void startFlow(ServerLevel level, Volcano v, RandomSource random) {
        VolcanoShape s = v.shape;
        float angle = random.nextFloat() * Mth.TWO_PI;
        float r = s.rimRadius(angle) + 1.0F;
        BlockPos top = surface(level, Mth.floor(s.cx() + Mth.cos(angle) * r), Mth.floor(s.cz() + Mth.sin(angle) * r));
        if (top != null && placeLava(level, v, top)) {
            v.flows.add(new Volcano.Flow(top.asLong(), 0));
            v.flowsStarted++;
        }
    }

    /** Each flow's front creeps one block down the steepest way off the mountain. */
    private static void advanceFlows(ServerLevel level, Volcano v, RandomSource random) {
        VolcanoShape s = v.shape;
        for (Iterator<Volcano.Flow> it = v.flows.iterator(); it.hasNext(); ) {
            Volcano.Flow flow = it.next();
            BlockPos head = BlockPos.of(flow.head);
            if (!level.isLoaded(head)) {
                continue;
            }
            double out = s.horizontalDistance(head.getX(), head.getZ());
            BlockPos next = null;
            double best = Double.MAX_VALUE;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos p = surface(level, head.getX() + dx, head.getZ() + dz);
                    if (p == null || p.getY() > head.getY()) continue;
                    // downhill first, then away from the crater, with a little wander
                    double score = p.getY() - (s.horizontalDistance(p.getX(), p.getZ()) - out) * 0.6 + random.nextDouble() * 0.5;
                    if (score < best) {
                        best = score;
                        next = p;
                    }
                }
            }
            if (next == null || ++flow.steps > 80 || s.horizontalDistance(next.getX(), next.getZ()) > s.radius() * 1.15
                    || level.getFluidState(next).is(net.minecraft.tags.FluidTags.WATER)) {
                it.remove(); // pooled, ran out, or reached the water (where it'll quench itself)
                continue;
            }
            // lava runs on down a slope by itself, so a source every few blocks keeps the stream going without flooding the hillside
            if (flow.steps % 4 != 0 || placeLava(level, v, next)) {
                flow.head = next.asLong();
            } else {
                it.remove();
            }
        }
    }

    private static boolean placeLava(ServerLevel level, Volcano v, BlockPos pos) {
        BlockState at = level.getBlockState(pos);
        if (at.is(Blocks.LAVA)) {
            return true;
        }
        if (!at.isAir() && !at.canBeReplaced() && !at.is(BlockTags.LOGS) && !at.is(BlockTags.LEAVES)) {
            return false;
        }
        level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
        if (v.spills.size() < MAX_SPILLS) {
            v.spills.add(pos.asLong());
        }
        return true;
    }

    /** Spilled lava crusts over into basalt (with the odd patch of magma, blackstone or obsidian). */
    private static void harden(ServerLevel level, Volcano v, int count) {
        RandomSource random = level.random;
        for (int i = 0; i < count && !v.spills.isEmpty(); i++) {
            long packed = v.spills.removeLong(v.spills.size() - 1);
            BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos)) {
                v.spills.add(0, packed); // come back to it when it's loaded
                continue;
            }
            // the source and the stream around it set solid; lava that ran further drains away once its sources are gone
            boolean fizz = false;
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -3, -2), pos.offset(2, 1, 2))) {
                if (level.getBlockState(p).is(Blocks.LAVA) && !v.shape.inCrater(p.getX(), p.getZ())) {
                    level.setBlock(p, cooledRock(random), Block.UPDATE_ALL);
                    fizz = true;
                }
            }
            if (fizz) {
                level.levelEvent(LevelEvent.LAVA_FIZZ, pos, 0);
            }
        }
    }

    /** Lava that spread out over the slopes crusts over into rock, leaving a lava field where it ran. */
    private static void crustOver(ServerLevel level, Volcano v, int samples) {
        VolcanoShape s = v.shape;
        if (!level.isLoaded(s.crater())) {
            return;
        }
        RandomSource random = level.random;
        double reach = s.radius() * 1.15;
        for (int i = 0; i < samples; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0, r = Math.sqrt(random.nextDouble()) * reach;
            int x = Mth.floor(s.cx() + Math.cos(angle) * r), z = Mth.floor(s.cz() + Math.sin(angle) * r);
            BlockPos column = new BlockPos(x, 0, z);
            if (s.inCrater(x, z) || !level.isLoaded(column)) {
                continue;
            }
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below();
            if (level.getBlockState(top).is(Blocks.LAVA)) {
                level.setBlock(top, cooledRock(random), Block.UPDATE_ALL);
                if (random.nextInt(4) == 0) {
                    level.levelEvent(LevelEvent.LAVA_FIZZ, top, 0);
                }
            }
        }
    }

    private static BlockState cooledRock(RandomSource random) {
        float roll = random.nextFloat();
        return roll < 0.62F ? Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.Y)
                : roll < 0.8F ? Blocks.BLACKSTONE.defaultBlockState()
                : roll < 0.94F ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
    }

    // ------------------------------------------------------------------ players

    private static double nearestPlayer(ServerLevel level, Volcano v) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                best = Math.min(best, v.shape.horizontalDistance(player.getX(), player.getZ()));
            }
        }
        return best;
    }

    private static void checkPlayers(ServerLevel level, Volcano v) {
        VolcanoShape s = v.shape;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            double d = s.horizontalDistance(player.getX(), player.getZ());
            if (d > s.radius() + 200) continue;
            if (d > s.craterRadius() - 2 && d < s.craterRadius() + 8 && player.getY() > s.peakY() - 8 && player.getY() < s.peakY() + 6) {
                WeatherAdvancements.award(player, "hot_spot");
            }
            if (v.phase == Phase.ERUPTING && d < 200) {
                WeatherAdvancements.award(player, "ring_of_fire");
            }
        }
    }

    /** All known volcanoes within {@code range} blocks of a position, nearest first. */
    public static List<Volcano> near(ServerLevel level, double x, double z, double range) {
        List<Volcano> list = new ArrayList<>();
        if (!handles(level)) {
            return list;
        }
        for (Volcano v : data(level).all()) {
            if (v.shape.horizontalDistance(x, z) < range) {
                list.add(v);
            }
        }
        list.sort((a, b) -> Double.compare(a.shape.horizontalDistance(x, z), b.shape.horizontalDistance(x, z)));
        return list;
    }

    @Nullable
    public static Volcano nearest(ServerLevel level, double x, double z, double range) {
        List<Volcano> list = near(level, x, z, range);
        return list.isEmpty() ? null : list.get(0);
    }

    // ------------------------------------------------------------------ redstone

    /** The seismograph's reading at a position: the strongest tremor from any volcano. */
    public static int seismicSignal(ServerLevel level, BlockPos pos) {
        int signal = 0;
        for (Volcano v : near(level, pos.getX(), pos.getZ(), 1024.0)) {
            signal = Math.max(signal, signalFor(v.phase, v.progress(), v.shape.horizontalDistance(pos.getX(), pos.getZ())));
        }
        return signal;
    }

    /**
     * Rumbling volcanoes give a weak signal that grows as the eruption nears (and the closer you are);
     * an eruption reads 15 within ~380 blocks; aftershocks while it cools. Nothing beyond 1024 blocks.
     */
    public static int signalFor(Phase phase, float progress, double distance) {
        if (distance >= 1024.0) {
            return 0;
        }
        float near = 1.0F - (float) (distance / 1024.0);
        return switch (phase) {
            case DORMANT -> 0;
            case RUMBLING -> Mth.clamp(Math.round((2.0F + 10.0F * progress) * near), 1, 12);
            case ERUPTING -> Mth.clamp(Math.round(15.0F * Math.min(1.0F, near * 1.6F)), 1, 15);
            case COOLING -> Mth.clamp(Math.round(4.0F * (1.0F - progress) * near), 0, 4);
        };
    }

    // ------------------------------------------------------------------ sync

    private static void sync(ServerLevel level, VolcanoData data) {
        for (ServerPlayer player : level.players()) {
            syncTo(player, data);
        }
    }

    private static void syncTo(ServerPlayer player, VolcanoData data) {
        List<VolcanoPayload.Entry> entries = new ArrayList<>();
        for (Volcano v : data.all()) {
            if (v.shape.horizontalDistance(player.getX(), player.getZ()) < SYNC_RANGE) {
                VolcanoShape s = v.shape;
                entries.add(new VolcanoPayload.Entry(s.cx(), s.cz(), s.lavaY(), s.peakY(), s.radius(), s.craterRadius(),
                        v.phase.ordinal(), v.progress(), v.length));
            }
        }
        PacketDistributor.sendToPlayer(player, new VolcanoPayload(entries, data.lastEruption));
    }

    private VolcanoManager() {}
}
