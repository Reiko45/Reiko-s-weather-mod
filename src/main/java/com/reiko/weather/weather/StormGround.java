package com.reiko.weather.weather;

import com.reiko.weather.block.FallenBranchBlock;
import com.reiko.weather.config.WeatherConfig;
import com.reiko.weather.registry.ModBlocks;
import com.reiko.weather.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * What storms do to the ground and the trees: hail lying in drifts, heavy downpours washing bare soil down steep
 * slopes into rills and gullies, wet snow piling on trees until the branches snap, and hurricane and derecho winds
 * tearing whole trees out of the ground, roots and all.
 */
public final class StormGround {
    private static final int AREA = 40;

    static void tickPlayer(ServerLevel level, WeatherState state, ServerPlayer player, LocalWeather local, Climate climate) {
        RandomSource random = level.random;
        WeatherProfile p = local.profile();
        float intensity = local.intensity();
        boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        if (WeatherConfig.HAIL_DRIFTS.get() && (p.hail || p.rateOf(Precip.HAIL) > 0.2F) && intensity > 0.5F) {
            hail(level, column(level, player, random), state);
        }
        if (WeatherConfig.SOIL_EROSION.get() && griefing && p.rain >= 0.9F && intensity > 0.7F && local.type().vanillaRain
                && climate != Climate.COLD && random.nextInt(3) == 0) {
            erode(level, column(level, player, random));
        }
        if (WeatherConfig.SNOW_LOAD.get() && griefing && p.snowLayers >= 2 && intensity > 0.6F && random.nextInt(300) == 0) {
            snowLoad(level, player, random);
        }
        boolean gale = local.type() == WeatherType.HURRICANE && intensity > 0.75F || local.type() == WeatherType.DERECHO && intensity > 0.6F;
        if (WeatherConfig.BLOWDOWNS.get() && griefing && gale && random.nextInt(500) == 0) {
            blowdown(level, state, player, random);
        }
    }

    @Nullable
    private static BlockPos column(ServerLevel level, ServerPlayer player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(AREA * 2 + 1) - AREA, z = player.getBlockZ() + random.nextInt(AREA * 2 + 1) - AREA;
        BlockPos c = new BlockPos(x, 0, z);
        return level.isLoaded(c) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c) : null;
    }

    // ------------------------------------------------------------------ hail

    /**
     * Hail settles out in the open and drifts up against anything in the way, deepest on the lee side of walls
     * and trees and in hollows. It melts away on its own (see the hailstones block).
     */
    private static void hail(ServerLevel level, @Nullable BlockPos top, WeatherState state) {
        if (top == null || !level.canSeeSky(top)) return;
        BlockState at = level.getBlockState(top);
        BlockPos below = top.below();
        if (at.is(ModBlocks.HAILSTONES.get())) {
            int layers = at.getValue(SnowLayerBlock.LAYERS);
            // only grows deep where it's sheltered (a wall or a step up downwind of it) or in a dip
            Direction lee = Direction.fromYRot(Math.toDegrees(state.windAngle) - 90.0);
            boolean sheltered = level.getBlockState(top.relative(lee)).isFaceSturdy(level, top.relative(lee), lee.getOpposite())
                    || level.getBlockState(top.relative(lee.getOpposite())).isFaceSturdy(level, top.relative(lee.getOpposite()), lee);
            int max = sheltered ? 5 : 2;
            if (layers < max) level.setBlockAndUpdate(top, at.setValue(SnowLayerBlock.LAYERS, layers + 1));
            return;
        }
        if (!at.isAir() && !(at.canBeReplaced() && at.getFluidState().isEmpty() && !at.is(Blocks.SNOW))) return;
        BlockState floor = level.getBlockState(below);
        if (!floor.isFaceSturdy(level, below, Direction.UP) || floor.is(BlockTags.LEAVES) || floor.is(Blocks.ICE) || floor.is(Blocks.PACKED_ICE)) return;
        if (!at.isAir()) level.destroyBlock(top, false); // shredding the grass it lands on
        level.setBlockAndUpdate(top, ModBlocks.HAILSTONES.get().defaultBlockState());
    }

    // ------------------------------------------------------------------ soil erosion

    /**
     * Sheet wash and rills: a downpour on bare soil carries it off down a steep slope, cutting the slope back and
     * spreading the mud out at the foot of it. Grass, roots and terraces hold the soil: level fields of farmland
     * don't wash away, only farmland ploughed straight down a hillside.
     */
    private static void erode(ServerLevel level, @Nullable BlockPos top) {
        if (top == null || !level.canSeeSky(top)) return;
        BlockPos soilPos = level.getBlockState(top).isAir() ? top.below() : top;
        BlockState soil = level.getBlockState(soilPos);
        boolean bare = soil.is(Blocks.DIRT) || soil.is(Blocks.COARSE_DIRT) || soil.is(Blocks.FARMLAND) || soil.is(Blocks.ROOTED_DIRT) || soil.is(Blocks.MUD);
        if (!bare || !level.getBlockState(soilPos.above()).canBeReplaced()) return;
        // find the steepest way down
        Direction down = null;
        int drop = 1;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = soilPos.relative(d);
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, n.getX(), n.getZ()) - 1;
            if (soilPos.getY() - h > drop) {
                drop = soilPos.getY() - h;
                down = d;
            }
        }
        if (down == null || drop < 2 || nearBuildings(level, soilPos)) return;
        BlockState above = level.getBlockState(soilPos.above());
        if (!above.isAir()) level.destroyBlock(soilPos.above(), true); // crops and all
        // a rill cuts into the slope; what's left is stony and raw
        level.setBlockAndUpdate(soilPos, soil.is(Blocks.FARMLAND) || soil.is(Blocks.DIRT) ? Blocks.COARSE_DIRT.defaultBlockState()
                : level.getBlockState(soilPos.below()).isFaceSturdy(level, soilPos.below(), Direction.UP) ? Blocks.AIR.defaultBlockState()
                : Blocks.COARSE_DIRT.defaultBlockState());
        // and the mud spreads out at the bottom
        BlockPos foot = soilPos.relative(down);
        foot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, foot);
        for (int i = 0; i < 4; i++) {
            BlockPos next = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, foot.relative(down));
            if (next.getY() >= foot.getY()) break;
            foot = next;
        }
        BlockState there = level.getBlockState(foot);
        if ((there.isAir() || there.canBeReplaced() && there.getFluidState().isEmpty()) && level.getBlockState(foot.below()).isFaceSturdy(level, foot.below(), Direction.UP)
                && !nearBuildings(level, foot)) {
            if (!there.isAir()) level.destroyBlock(foot, true);
            level.setBlockAndUpdate(foot, Blocks.MUD.defaultBlockState());
        }
    }

    private static boolean nearBuildings(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, -3, -4), pos.offset(4, 3, 4))) {
            BlockState s = level.getBlockState(p);
            if (s.hasBlockEntity() || s.is(BlockTags.PLANKS) || s.is(Blocks.BRICKS) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.GLASS)
                    || s.is(BlockTags.DOORS) || s.is(BlockTags.FENCES) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.WATER) && p.getY() == pos.getY()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ snow load

    /**
     * Wet, heavy snow piling up on the trees: now and then a laden branch gives way with a crack, and comes down
     * with its load of snow on whatever's underneath.
     */
    private static void snowLoad(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos ground = StormDebris.groundUnderTrees(level, player, random, AREA);
        if (ground == null) return;
        int canopy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, ground.getX(), ground.getZ());
        BlockPos crown = new BlockPos(ground.getX(), canopy - 1, ground.getZ());
        if (!level.getBiome(crown).value().coldEnoughToSnow(crown)) return;
        // break out a few leaves, and drop the branch and its snow
        int broken = 0;
        for (BlockPos p : BlockPos.betweenClosed(crown.offset(-1, -1, -1), crown.offset(1, 0, 1))) {
            if (broken < 3 && level.getBlockState(p).is(BlockTags.LEAVES) && random.nextBoolean()) {
                level.destroyBlock(p, true);
                broken++;
            }
        }
        if (broken == 0) return;
        BlockPos snowPos = crown.above();
        if (level.getBlockState(snowPos).is(Blocks.SNOW)) {
            FallingBlockEntity.fall(level, snowPos, level.getBlockState(snowPos)).setHurtsEntities(0.5F, 3);
        }
        BlockState here = level.getBlockState(ground);
        BlockState branch = ModBlocks.FALLEN_BRANCH.get().defaultBlockState()
                .setValue(FallenBranchBlock.AXIS, random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z);
        if ((here.isAir() || here.is(Blocks.SNOW) || here.canBeReplaced() && here.getFluidState().isEmpty()) && branch.canSurvive(level, ground)) {
            level.setBlockAndUpdate(ground, branch);
        }
        level.playSound(null, crown, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 2.5F, 0.55F + random.nextFloat() * 0.2F);
        if (player.distanceToSqr(Vec3.atCenterOf(ground)) < 24 * 24) WeatherAdvancements.award(player, "snow_load");
    }

    // ------------------------------------------------------------------ blowdowns

    /**
     * A blowdown: a gust in a hurricane or derecho levers a whole tree out of the ground, and it crashes down
     * downwind, leaving its roots and a clod of earth standing up on end beside the hole it came out of.
     */
    private static void blowdown(ServerLevel level, WeatherState state, ServerPlayer player, RandomSource random) {
        BlockPos ground = StormDebris.groundUnderTrees(level, player, random, AREA);
        if (ground == null) return;
        // find a trunk near this spot
        BlockPos base = null;
        for (BlockPos p : BlockPos.betweenClosed(ground.offset(-3, -1, -3), ground.offset(3, 1, 3))) {
            if (level.getBlockState(p).is(BlockTags.LOGS) && !level.getBlockState(p.below()).is(BlockTags.LOGS)
                    && level.getBlockState(p.below()).is(BlockTags.DIRT)) {
                base = p.immutable();
                break;
            }
        }
        if (base == null || GroundEvents.built(level, base, 6)) return;
        int height = 0;
        while (height < 12 && level.getBlockState(base.above(height)).is(BlockTags.LOGS)) height++;
        if (height < 3 || height > 10) return; // the big ones stand, and nobody uproots a sapling
        // the whole tree: its trunk and the leaves round the top of it
        List<BlockPos> logs = new ArrayList<>(), leaves = new ArrayList<>();
        BlockPos top = base.above(height - 1);
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-3, 0, -3), top.offset(3, 3, 3))) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.LOGS) && (p.getX() == base.getX() && p.getZ() == base.getZ() || p.getY() > base.getY() + height / 2)) logs.add(p.immutable());
            else if (s.is(BlockTags.LEAVES) && p.getY() >= base.getY() + height / 2) leaves.add(p.immutable());
        }
        if (logs.size() > 24) return; // a big tree or a cluster: leave it
        Direction fall = Direction.fromYRot(Math.toDegrees(state.windAngle) - 90.0);
        BlockState trunkState = level.getBlockState(base);
        BlockState leafState = leaves.isEmpty() ? null : level.getBlockState(leaves.getFirst());
        // where it lands: lying downwind along the ground from the foot
        List<BlockPos> lying = new ArrayList<>();
        for (int i = 1; i <= height; i++) {
            BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.relative(fall, i));
            if (Math.abs(at.getY() - base.getY()) > 3 || !level.isLoaded(at)) return;
            BlockState s = level.getBlockState(at);
            if (!(s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty())) return;
            lying.add(at);
        }
        for (BlockPos p : leaves) level.removeBlock(p, false);
        for (BlockPos p : logs) level.removeBlock(p, false);
        Direction.Axis axis = fall.getAxis();
        BlockState fallen = trunkState.hasProperty(RotatedPillarBlock.AXIS) ? trunkState.setValue(RotatedPillarBlock.AXIS, axis) : trunkState;
        for (BlockPos at : lying) {
            if (!level.getBlockState(at).isAir()) level.destroyBlock(at, true);
            level.setBlockAndUpdate(at, fallen);
        }
        if (leafState != null) {
            BlockState crown = leafState.hasProperty(LeavesBlock.PERSISTENT) ? leafState.setValue(LeavesBlock.PERSISTENT, true) : leafState;
            BlockPos end = lying.getLast();
            for (BlockPos p : BlockPos.betweenClosed(end.offset(-1, 0, -1), end.offset(1, 1, 1))) {
                if (level.getBlockState(p).isAir() && random.nextFloat() < 0.7F) level.setBlockAndUpdate(p, crown);
            }
        }
        // the root plate: a clod of soil and roots torn up on end, and the hole it left
        level.setBlockAndUpdate(base, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(base.below(), Blocks.COARSE_DIRT.defaultBlockState());
        BlockPos plate = base.relative(fall.getOpposite());
        if (level.getBlockState(plate).canBeReplaced()) {
            level.setBlockAndUpdate(plate, Blocks.ROOTED_DIRT.defaultBlockState());
            if (level.getBlockState(plate.above()).canBeReplaced()) level.setBlockAndUpdate(plate.above(), Blocks.ROOTED_DIRT.defaultBlockState());
        }
        level.playSound(null, base, ModSounds.TREE_FALL.get(), SoundSource.BLOCKS, 3.0F, 0.9F + random.nextFloat() * 0.2F);
        if (player.distanceToSqr(Vec3.atCenterOf(base)) < 40 * 40) WeatherAdvancements.award(player, "blowdown");
    }

    private StormGround() {}
}
