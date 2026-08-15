package gg.aurora.client.module.modules.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import gg.aurora.client.Aurora;
import gg.aurora.client.event.events.Render3DEvent;
import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.schematic.Schematic;
import gg.aurora.client.schematic.SchematicLoader;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.ColorSetting;
import gg.aurora.client.setting.DoubleSetting;
import gg.aurora.client.setting.IntSetting;
import gg.aurora.client.util.InventoryUtil;
import gg.aurora.client.util.RenderUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Places a loaded schematic into the world, block by block.
 *
 * <p>The queue is built once on load, ordered bottom-up so nothing is placed before what it rests
 * on. Each tick the module takes the first few entries that are in reach, already satisfied, or
 * placeable, and acts on at most {@code Blocks per tick} of them.
 *
 * <p>What it will not do is place a block it has no item for, or one that would land where a block
 * already sits — it skips both and moves on, so a partial inventory produces a partial build
 * rather than a stalled one.
 *
 * <p>Schematics are read from {@code .minecraft/config/aurora/schematics/}. Load one by putting
 * its file name in {@code File} and toggling the module.
 */
public final class SchematicBuilder extends Module {

    private final IntSetting perTick =
            this.register(new IntSetting("Blocks per tick", "How many placements to attempt each tick.",
                    1, 1, 16));
    private final DoubleSetting reach =
            this.register(new DoubleSetting("Reach", "Maximum placement distance, in blocks.", 4.0D, 1.0D, 6.0D, 1));
    private final BoolSetting autoSwap =
            this.register(new BoolSetting("Auto swap", "Swap to the needed block in your hotbar.", true));
    private final BoolSetting preview =
            this.register(new BoolSetting("Preview", "Draw the pending blocks.", true));
    private final IntSetting previewLimit =
            this.register(new IntSetting("Preview limit", "How many pending blocks to draw.", 512, 32, 4096))
                    .visibleWhen(() -> this.preview.value());
    private final ColorSetting pendingColor =
            this.register(new ColorSetting("Pending colour", "Colour for blocks still to place.", 0x6038BDF8))
                    .visibleWhen(() -> this.preview.value());
    private final ColorSetting missingColor =
            this.register(new ColorSetting("Missing colour", "Colour for blocks you have no item for.", 0x60EF4444))
                    .visibleWhen(() -> this.preview.value());

    private Schematic schematic;
    private BlockPos anchor;
    private List<Map.Entry<BlockPos, BlockState>> queue = List.of();
    private int cursor;
    private int placed;
    private String status = "no schematic";

    public SchematicBuilder() {
        super("SchematicBuilder", "Builds a loaded schematic block by block.", Category.WORLD);
        this.listen(TickEvent.class, this::onTick);
        this.listen(Render3DEvent.class, this::onRender);
    }

    @Override
    protected void onEnable() {
        if (!this.inGame()) {
            this.setEnabled(false);
            return;
        }

        if (this.schematic == null) {
            this.status = "no schematic — use .schem load <file>";
            return;
        }

        // Anchor where the player is standing when the build starts.
        this.anchor = this.player().getBlockPos();
        this.queue = this.schematic.placementOrder();
        this.cursor = 0;
        this.placed = 0;
        this.status = "building";
    }

    @Override
    protected void onDisable() {
        this.status = this.schematic == null ? "no schematic" : "stopped";
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /** Directory schematics are read from. */
    public static Path directory() {
        return FabricLoader.getInstance().getConfigDir().resolve("aurora").resolve("schematics");
    }

    /** File names available to load. */
    public static List<String> available() {
        return SchematicLoader.list(directory());
    }

    /**
     * Loads a schematic by file name, replacing whatever was loaded.
     *
     * @return a message describing what happened, for the caller to show the player
     */
    public String load(String fileName) {
        Path file = directory().resolve(fileName);

        if (!Files.isRegularFile(file)) {
            this.status = "not found";
            return "No such schematic: " + fileName;
        }

        try {
            this.schematic = SchematicLoader.load(file);
            this.queue = List.of();
            this.cursor = 0;
            this.placed = 0;
            this.status = "loaded";

            return String.format("Loaded %s — %d blocks, %dx%dx%d",
                    this.schematic.name(), this.schematic.blockCount(),
                    this.schematic.size().getX(), this.schematic.size().getY(), this.schematic.size().getZ());
        } catch (IOException exception) {
            Aurora.LOGGER.error("Could not load schematic {}", fileName, exception);
            this.status = "load failed";
            return "Could not load " + fileName + ": " + exception.getMessage();
        }
    }

    public Schematic schematic() {
        return this.schematic;
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    private void onTick(TickEvent event) {
        if (!this.inGame() || mc.currentScreen != null || this.anchor == null || this.queue.isEmpty()) {
            return;
        }

        if (this.cursor >= this.queue.size()) {
            this.status = "done";
            this.setEnabled(false);
            return;
        }

        int attempts = 0;

        // Walk forward through the queue rather than restarting each tick: entries already
        // satisfied or permanently unplaceable stay behind the cursor instead of being retried
        // on every tick for the rest of the build.
        while (this.cursor < this.queue.size() && attempts < this.perTick.value()) {
            Map.Entry<BlockPos, BlockState> entry = this.queue.get(this.cursor);
            BlockPos worldPos = this.anchor.add(entry.getKey());

            if (!this.world().getBlockState(worldPos).isAir()) {
                // Something is already there — either we placed it or the terrain provides it.
                this.cursor++;
                continue;
            }

            if (this.player().getEyePos().distanceTo(Vec3d.ofCenter(worldPos)) > this.reach.value()) {
                // Out of reach for now; stop here so the queue keeps its bottom-up order and the
                // player can walk closer.
                this.status = "out of reach";
                return;
            }

            if (this.tryPlace(worldPos, entry.getValue())) {
                this.placed++;
                attempts++;
            }

            this.cursor++;
        }

        this.status = "building";
    }

    /** Places one block, returning whether an attempt was actually made. */
    private boolean tryPlace(BlockPos pos, BlockState state) {
        ItemStack needed = new ItemStack(state.getBlock().asItem());
        if (needed.isEmpty()) {
            return false;
        }

        int slot = InventoryUtil.findHotbar(this.player(),
                stack -> stack.getItem() instanceof BlockItem item && item.getBlock() == state.getBlock());

        if (slot < 0) {
            return false;
        }

        Direction against = this.supportingSide(pos);
        if (against == null) {
            return false;
        }

        int previous = this.player().getInventory().getSelectedSlot();
        if (this.autoSwap.value()) {
            InventoryUtil.select(mc, slot);
        }

        BlockPos neighbour = pos.offset(against);
        mc.interactionManager.interactBlock(this.player(), Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(neighbour), against.getOpposite(), neighbour, false));
        this.player().swingHand(Hand.MAIN_HAND);

        if (this.autoSwap.value()) {
            InventoryUtil.select(mc, previous);
        }

        return true;
    }

    /**
     * A side of {@code pos} that has a solid neighbour to click against, or {@code null} when the
     * position is floating and cannot be placed into yet.
     */
    private Direction supportingSide(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!this.world().getBlockState(pos.offset(direction)).isAir()) {
                return direction;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Preview
    // ------------------------------------------------------------------

    private void onRender(Render3DEvent event) {
        if (!this.preview.value() || this.anchor == null || this.queue.isEmpty() || !this.inGame()) {
            return;
        }

        int drawn = 0;

        for (int index = this.cursor; index < this.queue.size() && drawn < this.previewLimit.value(); index++) {
            Map.Entry<BlockPos, BlockState> entry = this.queue.get(index);
            BlockPos worldPos = this.anchor.add(entry.getKey());

            if (!this.world().getBlockState(worldPos).isAir()) {
                continue;
            }

            boolean haveItem = InventoryUtil.findHotbar(this.player(),
                    stack -> stack.getItem() instanceof BlockItem item
                            && item.getBlock() == entry.getValue().getBlock()) >= 0;

            RenderUtil.drawBoxFilled(event, new Box(worldPos),
                    haveItem ? this.pendingColor.argb() : this.missingColor.argb());
            drawn++;
        }
    }

    @Override
    public String hudSuffix() {
        if (this.schematic == null) {
            return this.status;
        }

        if (this.queue.isEmpty()) {
            return this.schematic.name();
        }

        return String.format("%d/%d", this.placed, this.queue.size());
    }

    /** Blocks placed so far in the current run. */
    public int placed() {
        return this.placed;
    }

    public String status() {
        return this.status;
    }

    /** Materials still needed, as block name to count, for a materials list. */
    public List<String> remainingMaterials() {
        List<String> lines = new ArrayList<>();

        if (this.queue.isEmpty()) {
            return lines;
        }

        Map<String, Integer> counts = new java.util.LinkedHashMap<>();

        for (int index = this.cursor; index < this.queue.size(); index++) {
            Map.Entry<BlockPos, BlockState> entry = this.queue.get(index);
            String name = entry.getValue().getBlock().getName().getString();
            counts.merge(name, 1, Integer::sum);
        }

        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> lines.add(entry.getValue() + "x " + entry.getKey()));

        return lines;
    }
}
