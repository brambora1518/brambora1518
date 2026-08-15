package gg.aurora.client.module;

/**
 * Top-level grouping for modules. Each becomes one draggable panel in the ClickGUI, in the order
 * declared here.
 */
public enum Category {

    COMBAT("Combat"),
    MOVEMENT("Movement"),
    RENDER("Render"),
    PLAYER("Player"),
    WORLD("World"),
    CLIENT("Client");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }
}
