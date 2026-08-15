package gg.aurora.client.module.modules.combat;

import gg.aurora.client.event.events.TickEvent;
import gg.aurora.client.module.Category;
import gg.aurora.client.module.Module;
import gg.aurora.client.setting.BoolSetting;
import gg.aurora.client.setting.DoubleSetting;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;

/**
 * Extends how far the player can interact with entities and blocks.
 *
 * <p>Both ranges are ordinary synced attributes, and the server checks them on its own side before
 * accepting a hit. Raising them client-side changes what the client will try, not what the server
 * allows: against a server that has not granted the same value, the extra range is simply refused.
 */
public final class Reach extends Module {

    private static final double VANILLA_ENTITY_RANGE = 3.0D;
    private static final double VANILLA_BLOCK_RANGE = 4.5D;

    private final DoubleSetting entityRange =
            this.register(new DoubleSetting("Entity range", "Attack range, in blocks.", 4.0D, 3.0D, 8.0D, 1));
    private final BoolSetting blocks =
            this.register(new BoolSetting("Blocks", "Extend block reach as well.", false));
    private final DoubleSetting blockRange =
            this.register(new DoubleSetting("Block range", "Block interaction range, in blocks.", 5.5D, 4.5D, 8.0D, 1))
                    .visibleWhen(() -> this.blocks.value());

    public Reach() {
        super("Reach", "Extends entity and block interaction range.", Category.COMBAT);
        this.listen(TickEvent.class, this::onTick);
    }

    @Override
    protected void onDisable() {
        this.apply(EntityAttributes.ENTITY_INTERACTION_RANGE, VANILLA_ENTITY_RANGE);
        this.apply(EntityAttributes.BLOCK_INTERACTION_RANGE, VANILLA_BLOCK_RANGE);
    }

    private void onTick(TickEvent event) {
        if (!this.inGame()) {
            return;
        }

        this.apply(EntityAttributes.ENTITY_INTERACTION_RANGE, this.entityRange.value());
        this.apply(EntityAttributes.BLOCK_INTERACTION_RANGE,
                this.blocks.value() ? this.blockRange.value() : VANILLA_BLOCK_RANGE);
    }

    private void apply(net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,
                       double value) {
        if (mc.player == null) {
            return;
        }

        EntityAttributeInstance instance = mc.player.getAttributeInstance(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Override
    public String hudSuffix() {
        return String.format("%.1f", this.entityRange.value());
    }
}
