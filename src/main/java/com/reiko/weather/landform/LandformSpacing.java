package com.reiko.weather.landform;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Keeps landforms from being built on top of each other. Each landform's structure set spaces out its own kind,
 * and nothing else spaced one kind from another, so a lake could be cut into a caldera, or a loess plateau raised
 * against a volcanic neck. Now, where two would overlap, the bigger one is built and the smaller one isn't.
 *
 * <p>World generation lays out each chunk's structures on its own and in no fixed order, so whether a landform is
 * built can't depend on what happens to have generated already. Instead each one works out from the seed alone
 * which of its neighbours would really be built. It replays their plans just as their own structure sets will
 * (the same per-chunk random, the same biome check, the same keep-out round villages), and a neighbour only counts
 * if it wouldn't give way to something bigger still. That chain always ends, since each step only looks at bigger
 * landforms. Plans are remembered, so each candidate is planned only once however many neighbours ask.</p>
 */
public final class LandformSpacing {
    /** No landform reaches further than this from the middle of its chunk (the biggest come to about 110). */
    static final int REACH = 128;
    /** Every landform structure set keeps this many chunks clear of villages (see the structure_set files). */
    private static final int VILLAGE_KEEPOUT = 5;
    /** How far a village's houses can spread from the middle of the chunk it starts in. */
    private static final int VILLAGE_SPREAD = 80;
    private static final ResourceKey<StructureSet> VILLAGES = ResourceKey.create(Registries.STRUCTURE_SET, ResourceLocation.withDefaultNamespace("villages"));
    private static final int MAX_CACHED = 16384;

    /** A landform that has been planned somewhere: what it is, the chunk it starts in, and the ground it covers. */
    public record Claim(LandformKind kind, ChunkPos chunk, BoundingBox bounds) {
        long area() {
            return (long) bounds.getXSpan() * bounds.getZSpan();
        }

        /** Whether this one is built rather than {@code other} where the two would overlap: the bigger one wins. */
        public boolean beats(Claim other) {
            if (area() != other.area()) return area() > other.area();
            if (kind != other.kind) return kind.ordinal() < other.kind.ordinal();
            return chunk.toLong() < other.chunk.toLong();
        }

        /**
         * Whether the two would share any ground (seen from above; height doesn't matter). Their bounds already take in
         * the edge where each one eases into the land round it, and are boxier than the landforms themselves, so two
         * that merely come close are left be.
         */
        public boolean clashes(Claim other) {
            return bounds.minX() <= other.bounds.maxX() && bounds.maxX() >= other.bounds.minX()
                    && bounds.minZ() <= other.bounds.maxZ() && bounds.maxZ() >= other.bounds.minZ();
        }
    }

    /**
     * Whether {@code claim} gives way: whether a rival that overlaps it and beats it would really be built (that is,
     * it doesn't itself give way to something bigger). {@code rivals} lists the other landforms planned round about.
     */
    public static boolean yields(Claim claim, Function<Claim, List<Claim>> rivals) {
        for (Claim rival : rivals.apply(claim)) {
            if (rival.beats(claim) && rival.clashes(claim) && !yields(rival, rivals)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ in the world

    /** A planned landform and the height its structure starts at (where its biome is checked). */
    record Candidate(Landform landform, int y) {}

    private record Key(int generator, long seed, LandformKind kind, long chunk) {}

    private static final Map<Key, Optional<Candidate>> PLANS = new ConcurrentHashMap<>();
    private static final Map<Key, Boolean> YIELDS = new ConcurrentHashMap<>();
    private static final Map<Key, Boolean> BIOMES = new ConcurrentHashMap<>();
    /** Start heights by chunk (kept under a null kind): working one out runs the terrain noise for a whole column. */
    private static final Map<Key, Integer> STARTS = new ConcurrentHashMap<>();
    private static volatile List<LandformSet> sets;
    private static volatile Object setsFor;

    private record LandformSet(LandformKind kind, LandformStructure structure, RandomSpreadStructurePlacement placement) {}

    private static Key key(Structure.GenerationContext context, LandformKind kind, ChunkPos chunk) {
        return new Key(System.identityHashCode(context.chunkGenerator()), context.seed(), kind, chunk.toLong());
    }

    /**
     * What {@code kind} would plan at {@code chunk}, exactly as its structure set will when that chunk's structures are
     * laid out (null if nothing would go there). It doesn't look at the biome or at other landforms.
     */
    @Nullable
    static Candidate candidate(Structure.GenerationContext context, LandformKind kind, ChunkPos chunk) {
        Key key = key(context, kind, chunk);
        Optional<Candidate> known = PLANS.get(key);
        if (known == null) {
            known = Optional.ofNullable(plan(context, kind, chunk));
            if (PLANS.size() > MAX_CACHED) PLANS.clear();
            PLANS.put(key, known);
        }
        return known.orElse(null);
    }

    @Nullable
    private static Candidate plan(Structure.GenerationContext context, LandformKind kind, ChunkPos chunk) {
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        // the same random the structure gets when its own chunk is laid out
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(context.seed(), chunk.x, chunk.z);
        // working out the ground's height means running the terrain noise for a whole column, so never do a column twice
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap heights = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        PlanContext plan = new PlanContext(random, x, z,
                (px, pz) -> heights.computeIfAbsent(ChunkPos.asLong(px, pz), k -> context.chunkGenerator()
                        .getFirstOccupiedHeight(px, pz, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState())),
                context.chunkGenerator().getSeaLevel(), context.heightAccessor());
        Landform landform = kind.plan(plan);
        if (landform == null) return null;
        // never on (or half-buried by) a volcano
        BoundingBox b = landform.bounds();
        if (LandformStructure.nearVolcano(context, x, z, 125 + Math.max(b.getXSpan(), b.getZSpan()) / 2)) return null;
        // nor where a village's houses could spread onto it
        if (villageReaches(context, b)) return null;
        return new Candidate(landform, Math.max(plan.groundAt(x, z), plan.seaLevel() - 1));
    }

    /** Whether the landform {@code claim} should give way to a bigger neighbour that would overlap it. */
    static boolean yields(Structure.GenerationContext context, Claim claim) {
        Key key = key(context, claim.kind(), claim.chunk());
        Boolean known = YIELDS.get(key);
        if (known == null) {
            // (as the rule above, but asking about each rival through here, so what it comes to is remembered too)
            known = false;
            for (Claim rival : rivals(context, claim)) {
                if (rival.beats(claim) && !yields(context, rival)) {
                    known = true;
                    break;
                }
            }
            if (YIELDS.size() > MAX_CACHED) YIELDS.clear();
            YIELDS.put(key, known);
        }
        return known;
    }

    /** Every other landform that would be laid out round about and comes close to {@code claim}. */
    private static List<Claim> rivals(Structure.GenerationContext context, Claim claim) {
        List<Claim> found = new ArrayList<>();
        BoundingBox b = claim.bounds();
        int reach = REACH;
        int minX = (b.minX() - reach) >> 4, maxX = (b.maxX() + reach) >> 4, minZ = (b.minZ() - reach) >> 4, maxZ = (b.maxZ() + reach) >> 4;
        for (LandformSet set : sets(context)) {
            if (!set.kind().enabled()) continue;
            int spacing = set.placement().spacing();
            for (int rx = Math.floorDiv(minX, spacing); rx <= Math.floorDiv(maxX, spacing); rx++) {
                for (int rz = Math.floorDiv(minZ, spacing); rz <= Math.floorDiv(maxZ, spacing); rz++) {
                    ChunkPos at = set.placement().getPotentialStructureChunk(context.seed(), rx * spacing, rz * spacing);
                    if (at.x < minX || at.x > maxX || at.z < minZ || at.z > maxZ) continue;
                    if (set.kind() == claim.kind() && at.equals(claim.chunk())) continue;
                    if (nearVillage(context, at) || !inBiome(context, set, at)) continue;
                    Candidate c = candidate(context, set.kind(), at);
                    if (c == null) continue;
                    Claim rival = new Claim(set.kind(), at, c.landform().bounds());
                    if (rival.clashes(claim)) found.add(rival);
                }
            }
        }
        return found;
    }

    /**
     * Whether a landform starting in this chunk passes its biome check. Vanilla checks the biome where the structure
     * starts (the middle of the chunk, at ground level or the sea's) only after the structure has planned itself;
     * checking first gives the same answer without planning landforms that could never be built there.
     */
    private static boolean inBiome(Structure.GenerationContext context, LandformSet set, ChunkPos chunk) {
        Key key = key(context, set.kind(), chunk);
        Boolean known = BIOMES.get(key);
        if (known == null) {
            known = inBiome(context, set.structure().biomes()::contains, chunk);
            if (BIOMES.size() > MAX_CACHED * 4) BIOMES.clear();
            BIOMES.put(key, known);
        }
        return known;
    }

    static boolean inBiome(Structure.GenerationContext context, java.util.function.Predicate<Holder<Biome>> valid, ChunkPos chunk) {
        Holder<Biome> biome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(chunk.getMiddleBlockX()), QuartPos.fromBlock(startY(context, chunk)),
                QuartPos.fromBlock(chunk.getMiddleBlockZ()), context.randomState().sampler());
        return valid.test(biome);
    }

    /** The height a landform starting in this chunk starts at: the ground in the middle of it, or just under the sea. */
    static int startY(Structure.GenerationContext context, ChunkPos chunk) {
        Key key = key(context, null, chunk);
        Integer known = STARTS.get(key);
        if (known == null) {
            int ground = context.chunkGenerator().getFirstOccupiedHeight(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(), Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(), context.randomState());
            known = Math.max(ground, context.chunkGenerator().getSeaLevel() - 1);
            if (STARTS.size() > MAX_CACHED * 4) STARTS.clear();
            STARTS.put(key, known);
        }
        return known;
    }

    /**
     * Whether a village would start close enough to a landform's bounds for its houses to spread onto it: the landform
     * sets only keep their starts a few chunks from a village's, but a big village reaches out further than that.
     */
    private static boolean villageReaches(Structure.GenerationContext context, BoundingBox b) {
        StructureSet villages = context.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(VILLAGES);
        if (villages == null || !(villages.placement() instanceof RandomSpreadStructurePlacement placement)) return false;
        int spacing = placement.spacing();
        int minX = (b.minX() - VILLAGE_SPREAD) >> 4, maxX = (b.maxX() + VILLAGE_SPREAD) >> 4, minZ = (b.minZ() - VILLAGE_SPREAD) >> 4, maxZ = (b.maxZ() + VILLAGE_SPREAD) >> 4;
        for (int rx = Math.floorDiv(minX, spacing); rx <= Math.floorDiv(maxX, spacing); rx++) {
            for (int rz = Math.floorDiv(minZ, spacing); rz <= Math.floorDiv(maxZ, spacing); rz++) {
                ChunkPos v = placement.getPotentialStructureChunk(context.seed(), rx * spacing, rz * spacing);
                if (v.x < minX || v.x > maxX || v.z < minZ || v.z > maxZ) continue;
                // (only where the biome would let one start there)
                if (inBiome(context, biome -> villages.structures().stream().anyMatch(s -> s.structure().value().biomes().contains(biome)), v)) return true;
            }
        }
        return false;
    }

    /** Whether a village could start within the landform sets' keep-out of this chunk (so nothing is laid out there). */
    private static boolean nearVillage(Structure.GenerationContext context, ChunkPos chunk) {
        StructureSet villages = context.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(VILLAGES);
        if (villages == null || !(villages.placement() instanceof RandomSpreadStructurePlacement placement)) return false;
        int spacing = placement.spacing();
        for (int rx = Math.floorDiv(chunk.x - VILLAGE_KEEPOUT, spacing); rx <= Math.floorDiv(chunk.x + VILLAGE_KEEPOUT, spacing); rx++) {
            for (int rz = Math.floorDiv(chunk.z - VILLAGE_KEEPOUT, spacing); rz <= Math.floorDiv(chunk.z + VILLAGE_KEEPOUT, spacing); rz++) {
                ChunkPos v = placement.getPotentialStructureChunk(context.seed(), rx * spacing, rz * spacing);
                if (Math.abs(v.x - chunk.x) <= VILLAGE_KEEPOUT && Math.abs(v.z - chunk.z) <= VILLAGE_KEEPOUT) return true;
            }
        }
        return false;
    }

    /** All the landform structure sets (each places one kind), worked out once per set of registries. */
    private static List<LandformSet> sets(Structure.GenerationContext context) {
        Object registries = context.registryAccess();
        List<LandformSet> known = sets;
        if (known != null && setsFor == registries) return known;
        Registry<StructureSet> registry = context.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        List<LandformSet> list = new ArrayList<>();
        for (StructureSet set : registry) {
            if (!(set.placement() instanceof RandomSpreadStructurePlacement placement) || set.structures().size() != 1) continue;
            if (set.structures().get(0).structure().value() instanceof LandformStructure landform) {
                list.add(new LandformSet(landform.kind(), landform, placement));
            }
        }
        sets = List.copyOf(list);
        setsFor = registries;
        return sets;
    }

    /** Whether a structure is being placed by command (which takes any biome), rather than by world generation. */
    static boolean byCommand(Structure.GenerationContext context) {
        var voidBiome = context.registryAccess().registryOrThrow(Registries.BIOME).getHolder(Biomes.THE_VOID);
        return voidBiome.isPresent() && context.validBiome().test(voidBiome.get());
    }

    /** Forgets everything worked out (when the server stops, since another world may have other settings). */
    public static void reset() {
        PLANS.clear();
        YIELDS.clear();
        BIOMES.clear();
        STARTS.clear();
        sets = null;
        setsFor = null;
    }

    private LandformSpacing() {}
}
