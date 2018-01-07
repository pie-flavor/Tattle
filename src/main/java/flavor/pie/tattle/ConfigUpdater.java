package flavor.pie.tattle;

import ninja.leaping.configurate.ConfigurationNode;

public class ConfigUpdater {
    private ConfigUpdater() { }

    static void t2(ConfigurationNode node) {
        node.getNode("lang", "buttons", "btn-view").setValue(node.getNode("lang", "buttons", "btnView"));
        node.getNode("lang", "buttons").removeChild("btnView");
        node.getNode("lang", "buttons", "btn-delete").setValue(node.getNode("lang", "buttons", "btnDelete"));
        node.getNode("lang", "buttons").removeChild("btnDelete");
        node.getNode("lang", "buttons", "btn-teleport").setValue(node.getNode("lang", "buttons", "btnTeleport"));
        node.getNode("lang", "buttons").removeChild("btnTeleport");
    }
}
