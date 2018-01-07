package flavor.pie.tattle;

import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;

@ConfigSerializable
public class Config {
    public final static TypeToken<Config> type = TypeToken.of(Config.class);

    @Setting public Language lang = new Language();

    @ConfigSerializable
    public static class Language {
        @Setting("new") public NewSection new_ = new NewSection();
        @Setting public ButtonSection button = new ButtonSection();
        @Setting public ListSection list = new ListSection();
        @Setting public AdminSection admin = new AdminSection();
    }

    @ConfigSerializable
    public static class NewSection {
        @Setting("not-player") public String notPlayer = "Only players can create complaints!";
        @Setting("fail-create") public String failCreate = "Could not create complaint!";
        @Setting("success-create") public String successCreate = "Submitted a new complaint.";
    }

    @ConfigSerializable
    public static class ListSection {
        @Setting("not-player") public String notPlayer = "Only players can see their complaints!";
        @Setting("error-load") public String errorLoad = "Error loading complaints!";
        @Setting("empty-list") public String emptyList = "You have no open complaints!";
        @Setting public String header = "Complaints submitted by <player>";
        @Setting public String title = "Complaints";
    }

    @ConfigSerializable
    public static class ButtonSection {
        @Setting public String view = "View";
        @Setting public String delete = "Delete";
        @Setting public String teleport = "Teleport";
        @Setting("click-me") public String clickMe = "Click me!";
        @Setting("btn-view") public ViewButton btnView = new ViewButton();
        @Setting("btn-delete") public DeleteButton btnDelete = new DeleteButton();
        @Setting("btn-teleport") public TeleportButton btnTeleport = new TeleportButton();
    }

    @ConfigSerializable
    public static class ViewButton {
        @Setting public String header = "Complaint submitted by: <player> at <timestamp> at <location> in <world>:";
    }

    @ConfigSerializable
    public static class DeleteButton {
        @Setting("not-exist") public String notExist = "This complaint does not exist!";
        @Setting public String success = "Deleted the complaint.";
    }

    @ConfigSerializable
    public static class TeleportButton {
        @Setting("not-allowed") public String notAllowed = "Sorry, you can't teleport.";
    }

    @ConfigSerializable
    public static class AdminSection {
        @Setting public String header = "Complaints";
        @Setting("error-load") public String errorLoad = "Error loading complaints!";
        @Setting("empty-list") public String emptyList = "There are no open complaints!";
    }
}
