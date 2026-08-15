# Aurora Client

Modular utility client for Minecraft 1.21.8, built as a Fabric mod. Ships an in-game module
browser, a configurable HUD, and a profile system, on top of an event bus that the modules plug
into.

## Building

```bash
./gradlew build
```

The mod jar lands in `build/libs/aurora-client-<version>.jar`. Drop it in `.minecraft/mods`
alongside [Fabric API](https://modrinth.com/mod/fabric-api).

To launch a development client with the mod already loaded:

```bash
./gradlew runClient
```

Requires JDK 21. The Gradle wrapper pulls everything else.

## Layout

```
gg.aurora.client
├── Aurora                  entry point, service locator
├── event/                  event bus, event types, Fabric bridge
├── module/                 Module base, ModuleManager, Category
│   └── modules/            the modules themselves, one package per category
├── setting/                Setting types: bool, int, double, enum, colour, keybind
├── config/                 profile load/save
├── command/                chat commands
├── schematic/              format parsers and the in-memory structure
├── ui/                     ClickGUI, theme
├── util/                   rotation, ballistics, inventory, render helpers
└── mixin/                  the few hooks Fabric does not expose
```

### How a module works

A module declares its options as fields and subscribes to the events it needs, both from its
constructor:

```java
public final class Example extends Module {

    private final DoubleSetting range =
            this.register(new DoubleSetting("Range", "How far to look.", 8.0D, 1.0D, 32.0D));

    public Example() {
        super("Example", "Does a thing.", Category.WORLD);
        this.listen(TickEvent.class, this::onTick);
    }

    private void onTick(TickEvent event) {
        // Only runs while the module is enabled.
    }
}
```

Registering it in `Aurora.onInitializeClient` is the only other step — the ClickGUI, the config
writer, the HUD list and the keybind handler all pick it up from there.

Handlers only fire while the module is enabled; the bus checks that per dispatch, so enabling and
disabling costs nothing and is safe to do from inside a handler.

## Modules

| Category | Module | What it does |
|---|---|---|
| Combat | AimAssist | Steers aim by a capped turn per tick inside an FOV cone |
| Combat | AutoClicker | Repeats the held mouse button at a randomised CPS |
| Combat | AutoCrystal | Places and detonates end crystals, scored by blast damage |
| Combat | AutoSpear | Charges and throws a trident, correcting for drop |
| Combat | AutoTotem | Keeps a totem in the off-hand |
| Combat | BowAimbot | Solves pitch for the current draw, leads moving targets |
| Combat | MaceHit | Times a mace strike for the end of a fall |
| Combat | Reach | Raises the entity/block interaction range attributes |
| Combat | SafeAnchor | Places, charges and triggers a respawn anchor |
| Combat | ShieldBreaker | Swaps to an axe to disable a raised shield |
| Combat | TriggerBot | Attacks the entity already under the crosshair |
| Combat | WindCharge | Fires wind charges for height or knockback |
| Movement | NoSlow | Replaces the item-use movement penalty |
| Movement | Sprint | Sprints without holding the key |
| Movement | Step | Raises the step-height attribute |
| Render | Freecam | Detaches the camera; the body stays put |
| Render | Fullbright | Drives gamma past the options-screen limit |
| Render | Hitboxes | Outlines entity collision boxes |
| Render | NoFog | Scales the view distance the fog is derived from |
| Render | PearlPrediction | Ray-traced arc and landing point for throwables |
| Render | PlayerESP | Boxes other players, team-coloured |
| Render | Zoom | Eased field-of-view zoom |
| Player | AutoRefill | Tops up a low hotbar stack from the inventory |
| Player | FastPlace | Shortens the repeat delay on right-click |
| World | AutoBuilder | Fills a floor/wall/perimeter/nest with the held block |
| World | AutoMiner | Clears a sphere, cube or layer, nearest first |
| World | ChunkFinder | Marks chunks as the server streams them in |
| World | SchematicBuilder | Places a `.litematic`, `.schem` or `.nbt` structure |
| Client | ClickGUI | Opens the module browser (default: Right Shift) |
| Client | HUD | Module list, watermark, coordinates, FPS |

`Reach` and `Step` drive vanilla synced attributes. The server checks both on its own side, so
against a server that has not granted the same values the change is simply refused — they do what
they say on a world or server you control, and nothing on one you do not.

Nothing here is written to avoid server-side detection, and none of it is tuned against an
anti-cheat. Modules act on the tick they decide to act; timings are the ones the feature needs,
not ones shaped to look like anything.

## Commands

Chat messages starting with `.` are intercepted before they leave the client, so a mistyped
command never reaches the server.

```
.help                       list commands
.toggle <module>            turn a module on or off
.bind <module> <key|none>   rebind a module
.list [category]            list modules, enabled ones highlighted
.schem list                 list files in config/aurora/schematics/
.schem load <file>          load a schematic
.schem materials            what the current build still needs
.profile save|load <name>   manage config profiles
```

## Schematics

Put `.litematic`, `.schem` or `.nbt` files in `.minecraft/config/aurora/schematics/`, load one
with `.schem load <file>`, stand where you want it, and enable SchematicBuilder — the structure is
anchored where you were standing when you turned it on.

All three formats are gzipped NBT and are handled directly:

- **`.nbt`** (vanilla structure block) — palette plus an explicit position list.
- **`.schem`** (Sponge v2 and v3) — palette plus varint-packed indices in Y-Z-X order. v3's
  nesting under `Schematic`/`Blocks` and v2's flat layout are both accepted.
- **`.litematic`** (Litematica) — one or more regions, each a palette plus a packed long array.
  Litematica's bit array lets entries straddle two longs, unlike the non-spanning layout Minecraft
  itself moved to in 1.16, so it has its own unpacker; reading it with the vanilla one gives
  subtly wrong blocks wherever an entry crosses a boundary.

Placement is ordered bottom layer first, then outward from the structure's centre — a block with
nothing under it has no face to click against and the server rejects the placement. Blocks you
have no item for are skipped rather than stalling the queue, so a partial inventory gives a
partial build. `.schem materials` reports what is still outstanding.

Structures over 8 million blocks are refused at load rather than filling the heap.

## Config

Profiles are JSON under `.minecraft/config/aurora/`, one file per profile, `default.json` unless
you switch. Saved when the ClickGUI closes and on exit.

Writes go through a temp file that is then moved into place, so an interrupted save cannot corrupt
the profile that was already there. Loads are forgiving: an unknown, missing or malformed entry
leaves that setting at its default rather than failing the whole file, so hand-editing is safe.

## Extending

Adding a category means adding a constant to `Category` — the ClickGUI builds its panels from the
enum. Adding a setting type means extending `Setting<T>` with a JSON shape, and adding a branch in
`ClickGuiScreen.renderSettings` and `clickSetting`.

The bridge in `event/EventBridge` is where tick and render hooks come from Fabric's own callbacks.
Prefer adding hooks there over writing a mixin: Fabric maintains those across Minecraft releases,
and hand-written injection points are the first thing to break on an update.

## Status

Everything above compiles and the mixin targets resolve at build time. The client has not been
exercised in a running game — module behaviour and mixin injection want a real session before any
of it is relied on.
