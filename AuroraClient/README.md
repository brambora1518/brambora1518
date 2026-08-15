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
├── ui/                     ClickGUI, theme
├── util/                   world-space render helpers
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
| Combat | AutoClicker | Repeats the held mouse button at a randomised CPS |
| Combat | AutoTotem | Keeps a totem in the off-hand |
| Combat | Reach | Raises the entity/block interaction range attributes |
| Combat | TriggerBot | Attacks the entity already under the crosshair |
| Movement | Sprint | Sprints without holding the key |
| Movement | Step | Raises the step-height attribute |
| Render | Fullbright | Drives gamma past the options-screen limit |
| Render | Hitboxes | Outlines entity collision boxes |
| Render | PlayerESP | Boxes other players, team-coloured |
| Render | Zoom | Eased field-of-view zoom |
| Player | AutoRefill | Tops up a low hotbar stack from the inventory |
| Player | FastPlace | Shortens the repeat delay on right-click |
| World | ChunkFinder | Marks chunks as the server streams them in |
| Client | ClickGUI | Opens the module browser (default: Right Shift) |
| Client | HUD | Module list, watermark, coordinates, FPS |

`Reach` and `Step` drive vanilla synced attributes. The server checks both on its own side, so
against a server that has not granted the same values the change is simply refused — they do what
they say on a world or server you control, and nothing on one you do not.

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
