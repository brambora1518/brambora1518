package gg.aurora.client.event.events;

import gg.aurora.client.event.Event;

/**
 * Fired for raw keyboard activity, before the game maps it to a vanilla key binding.
 *
 * <p>Posted only while no screen has focus, so typing in chat or in the ClickGUI never triggers a
 * module bind.
 */
public final class KeyEvent implements Event {

    /** GLFW key code, e.g. {@code GLFW.GLFW_KEY_R}. */
    private final int key;
    private final int scancode;
    private final int modifiers;
    private final Action action;

    public KeyEvent(int key, int scancode, int modifiers, Action action) {
        this.key = key;
        this.scancode = scancode;
        this.modifiers = modifiers;
        this.action = action;
    }

    public int key() {
        return this.key;
    }

    public int scancode() {
        return this.scancode;
    }

    public int modifiers() {
        return this.modifiers;
    }

    public Action action() {
        return this.action;
    }

    public enum Action {
        PRESS,
        RELEASE,
        REPEAT
    }
}
