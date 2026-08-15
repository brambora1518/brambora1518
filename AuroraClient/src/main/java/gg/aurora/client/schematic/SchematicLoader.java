package gg.aurora.client.schematic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.util.math.Vec3i;

/**
 * Reads the three schematic formats worth supporting into a {@link Schematic}.
 *
 * <p>All three are gzipped NBT and are dispatched on file extension:
 * <ul>
 *   <li>{@code .nbt} — vanilla structure blocks: a palette plus an explicit list of positions.</li>
 *   <li>{@code .schem} — Sponge schematic v2 and v3: a palette plus varint-packed indices in
 *       Y-Z-X order.</li>
 *   <li>{@code .litematic} — Litematica: one or more regions, each a palette plus a packed
 *       bit array.</li>
 * </ul>
 */
public final class SchematicLoader {

    /** Structures larger than this are refused rather than filling the heap. */
    public static final long MAX_VOLUME = 8_000_000L;

    private SchematicLoader() {
    }

    /** Names of the files this loader recognises in {@code directory}. */
    public static List<String> list(Path directory) {
        List<String> names = new ArrayList<>();

        if (!Files.isDirectory(directory)) {
            return names;
        }

        try (var stream = Files.list(directory)) {
            stream.map(path -> path.getFileName().toString())
                    .filter(SchematicLoader::isSupported)
                    .forEach(names::add);
        } catch (IOException ignored) {
            // An unreadable directory is the same as an empty one for the caller's purposes.
        }

        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static boolean isSupported(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".nbt") || lower.endsWith(".schem") || lower.endsWith(".litematic");
    }

    /**
     * Loads a schematic.
     *
     * @throws IOException if the file cannot be read, is not one of the supported formats, or
     *                     describes a structure larger than {@link #MAX_VOLUME}
     */
    public static Schematic load(Path file) throws IOException {
        String name = file.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);

        NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());

        if (lower.endsWith(".litematic")) {
            return readLitematic(root, name);
        }
        if (lower.endsWith(".schem")) {
            return readSponge(root, name);
        }
        if (lower.endsWith(".nbt")) {
            return readStructure(root, name);
        }

        throw new IOException("Unsupported schematic format: " + name);
    }

    // ------------------------------------------------------------------
    // Vanilla structure (.nbt)
    // ------------------------------------------------------------------

    private static Schematic readStructure(NbtCompound root, String name) throws IOException {
        NbtList sizeList = root.getList("size").orElseThrow(() -> new IOException("Structure has no size"));
        Vec3i size = new Vec3i(sizeList.getInt(0, 0), sizeList.getInt(1, 0), sizeList.getInt(2, 0));
        checkVolume(size);

        NbtList paletteList = root.getListOrEmpty("palette");
        BlockState[] palette = new BlockState[paletteList.size()];
        for (int index = 0; index < palette.length; index++) {
            palette[index] = BlockStateParser.fromCompound(paletteList.getCompoundOrEmpty(index));
        }

        Schematic.Builder builder = new Schematic.Builder();
        NbtList blocks = root.getListOrEmpty("blocks");

        for (int index = 0; index < blocks.size(); index++) {
            NbtCompound entry = blocks.getCompoundOrEmpty(index);

            int stateIndex = entry.getInt("state", -1);
            if (stateIndex < 0 || stateIndex >= palette.length) {
                continue;
            }

            NbtList pos = entry.getListOrEmpty("pos");
            if (pos.size() < 3) {
                continue;
            }

            builder.set(pos.getInt(0, 0), pos.getInt(1, 0), pos.getInt(2, 0), palette[stateIndex]);
        }

        return builder.build(name, size);
    }

    // ------------------------------------------------------------------
    // Sponge schematic (.schem), v2 and v3
    // ------------------------------------------------------------------

    private static Schematic readSponge(NbtCompound root, String name) throws IOException {
        // v3 moved everything under a "Schematic" compound; v2 has it at the top level.
        NbtCompound body = root.getCompound("Schematic").orElse(root);

        int width = body.getInt("Width", 0);
        int height = body.getInt("Height", 0);
        int length = body.getInt("Length", 0);

        Vec3i size = new Vec3i(width, height, length);
        checkVolume(size);

        // v3 nests the palette and data inside "Blocks"; v2 keeps them alongside the dimensions.
        NbtCompound container = body.getCompound("Blocks").orElse(body);

        NbtCompound palette = container.getCompound("Palette")
                .or(() -> body.getCompound("Palette"))
                .orElseThrow(() -> new IOException("Schematic has no palette"));

        byte[] data = container.getByteArray("Data")
                .or(() -> body.getByteArray("BlockData"))
                .orElseThrow(() -> new IOException("Schematic has no block data"));

        // The palette maps descriptor to index, so invert it into an index-addressed array.
        BlockState[] states = new BlockState[palette.getKeys().size()];
        for (String descriptor : palette.getKeys()) {
            int index = palette.getInt(descriptor, -1);
            if (index >= 0 && index < states.length) {
                states[index] = BlockStateParser.fromString(descriptor);
            }
        }

        Schematic.Builder builder = new Schematic.Builder();

        int cursor = 0;
        int written = 0;
        int volume = width * height * length;

        while (cursor < data.length && written < volume) {
            int value = 0;
            int shift = 0;

            // Varint: seven bits per byte, high bit marks continuation.
            while (true) {
                if (cursor >= data.length) {
                    throw new IOException("Truncated block data");
                }

                byte current = data[cursor++];
                value |= (current & 0x7F) << shift;

                if ((current & 0x80) == 0) {
                    break;
                }

                shift += 7;
                if (shift > 35) {
                    throw new IOException("Malformed varint in block data");
                }
            }

            // Indices run Y, then Z, then X.
            int x = written % width;
            int z = (written / width) % length;
            int y = written / (width * length);
            written++;

            if (value >= 0 && value < states.length) {
                builder.set(x, y, z, states[value]);
            }
        }

        return builder.build(name, size);
    }

    // ------------------------------------------------------------------
    // Litematica (.litematic)
    // ------------------------------------------------------------------

    private static Schematic readLitematic(NbtCompound root, String name) throws IOException {
        NbtCompound regions = root.getCompound("Regions")
                .orElseThrow(() -> new IOException("Litematic has no regions"));

        Schematic.Builder builder = new Schematic.Builder();

        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;

        for (String regionName : regions.getKeys()) {
            NbtCompound region = regions.getCompoundOrEmpty(regionName);

            Vec3i regionSize = readVec3i(region.getCompound("Size"));
            Vec3i regionPos = readVec3i(region.getCompound("Position"));

            // A negative size means the region extends the other way from its corner; normalise so
            // the loop below can always count upwards.
            int sizeX = Math.abs(regionSize.getX());
            int sizeY = Math.abs(regionSize.getY());
            int sizeZ = Math.abs(regionSize.getZ());

            int originX = Math.min(regionPos.getX(), regionPos.getX() + regionSize.getX() + (regionSize.getX() < 0 ? 1 : -1));
            int originY = Math.min(regionPos.getY(), regionPos.getY() + regionSize.getY() + (regionSize.getY() < 0 ? 1 : -1));
            int originZ = Math.min(regionPos.getZ(), regionPos.getZ() + regionSize.getZ() + (regionSize.getZ() < 0 ? 1 : -1));

            checkVolume(new Vec3i(sizeX, sizeY, sizeZ));

            NbtList paletteList = region.getListOrEmpty("BlockStatePalette");
            BlockState[] palette = new BlockState[paletteList.size()];
            for (int index = 0; index < palette.length; index++) {
                palette[index] = BlockStateParser.fromCompound(paletteList.getCompoundOrEmpty(index));
            }

            long[] packed = region.getLongArray("BlockStates").orElse(new long[0]);
            if (packed.length == 0 || palette.length == 0) {
                continue;
            }

            // Litematica sizes entries to the palette, with a floor of two bits.
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
            LitematicaBitArray array = new LitematicaBitArray(bits, packed);

            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    for (int x = 0; x < sizeX; x++) {
                        int index = (y * sizeZ + z) * sizeX + x;
                        int value = array.get(index);

                        if (value >= 0 && value < palette.length) {
                            builder.set(originX + x, originY + y, originZ + z, palette[value]);
                        }
                    }
                }
            }

            maxX = Math.max(maxX, originX + sizeX);
            maxY = Math.max(maxY, originY + sizeY);
            maxZ = Math.max(maxZ, originZ + sizeZ);
        }

        return builder.build(name, new Vec3i(maxX, maxY, maxZ));
    }

    private static Vec3i readVec3i(Optional<NbtCompound> compound) {
        return compound
                .map(nbt -> new Vec3i(nbt.getInt("x", 0), nbt.getInt("y", 0), nbt.getInt("z", 0)))
                .orElse(Vec3i.ZERO);
    }

    private static void checkVolume(Vec3i size) throws IOException {
        long volume = (long) Math.abs(size.getX()) * Math.abs(size.getY()) * Math.abs(size.getZ());

        if (volume > MAX_VOLUME) {
            throw new IOException("Schematic is too large: " + volume + " blocks (limit " + MAX_VOLUME + ")");
        }
    }

    /**
     * Litematica's packed bit array.
     *
     * <p>Entries are packed back to back and may straddle two longs, unlike the non-spanning
     * layout Minecraft itself switched to in 1.16 — reading it with the vanilla unpacker gives
     * subtly wrong blocks wherever an entry crosses a boundary, which is why this is written out
     * here rather than borrowed.
     */
    private static final class LitematicaBitArray {

        private final long[] words;
        private final int bitsPerEntry;
        private final long mask;

        private LitematicaBitArray(int bitsPerEntry, long[] words) {
            this.bitsPerEntry = bitsPerEntry;
            this.words = words;
            this.mask = (1L << bitsPerEntry) - 1L;
        }

        private int get(int index) {
            long bitOffset = (long) index * this.bitsPerEntry;
            int startWord = (int) (bitOffset >> 6);
            int startBit = (int) (bitOffset & 63L);

            if (startWord < 0 || startWord >= this.words.length) {
                return 0;
            }

            long value = this.words[startWord] >>> startBit;

            // The entry runs past the end of this long, so pull the rest from the next one.
            int available = 64 - startBit;
            if (available < this.bitsPerEntry && startWord + 1 < this.words.length) {
                value |= this.words[startWord + 1] << available;
            }

            return (int) (value & this.mask);
        }
    }

}
