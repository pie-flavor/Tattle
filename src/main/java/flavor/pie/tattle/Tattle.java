package flavor.pie.tattle;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.service.pagination.PaginationList;
import org.spongepowered.api.service.permission.PermissionDescription;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.storage.WorldProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Plugin(id="tattle",name="Tattle",authors="pie_flavor",description="A reports plugin.",version="1.0.0")
public class Tattle {
    @Inject
    Game game;
    @Inject
    PluginContainer container;
    @Inject @ConfigDir(sharedRoot = false)
    Path dir;
    @Inject
    Logger logger;

    Path configPath;
    Path storagePath;
    HoconConfigurationLoader configLoader;
    HoconConfigurationLoader storageLoader;
    CommentedConfigurationNode config;
    ConfigurationNode storage;
    @Listener
    public void preInit(GamePreInitializationEvent e) throws IOException {
        try {
            if (Files.exists(dir))
                Files.createDirectory(dir);
            configPath = dir.resolve("config.conf");
            storagePath = dir.resolve("storage.conf");
            if (!storagePath.toFile().exists()) {
                game.getAssetManager().getAsset(this, "storage.conf").get().copyToFile(storagePath);
            }
            if (!configPath.toFile().exists()) {
                game.getAssetManager().getAsset(this, "default.conf").get().copyToFile(configPath);
            }
            configLoader = HoconConfigurationLoader.builder().setPath(configPath).build();
            storageLoader = HoconConfigurationLoader.builder().setPath(storagePath).build();
            config = configLoader.load();
            storage = storageLoader.load();
            if (config.getNode("version").getInt() == 0) {
                game.getAssetManager().getAsset(this, "default.conf").get().copyToFile(configPath);
                config = configLoader.load();
            }
        } catch (IOException ex) {
            disable();
            logger.error("Could not load config! Disabling.");
            throw ex;
        }
    }
    private void disable() {
        game.getEventManager().unregisterPluginListeners(this);
        game.getCommandManager().getOwnedBy(this).forEach(game.getCommandManager()::removeMapping);
    }
    @Listener
    public void init(GameInitializationEvent e) {
        CommandSpec create = CommandSpec.builder()
                .description(Text.of("Creates a new complaint."))
                .executor(this::create)
                .arguments(
                        GenericArguments.remainingJoinedStrings(Text.of("explanation"))
                )
                .build();
        CommandSpec list = CommandSpec.builder()
                .description(Text.of("Lists complaints that you have submitted."))
                .executor(this::list)
                .build();
        CommandSpec admin = CommandSpec.builder()
                .description(Text.of("Lists all complaints."))
                .executor(this::admin)
                .permission("tattle.admin.use")
                .build();
        CommandSpec tattle = CommandSpec.builder()
                .child(create, "new", "create", "n", "c", "+")
                .child(list, "list", "l", "?")
                .child(admin, "admin", "a", "!")
                .build();
        game.getCommandManager().register(this, tattle, "tattle", "tt");
        setupPermissions();
    }
    @Listener
    public void reload(GameReloadEvent e) throws IOException {
        config = configLoader.load();
        storage = storageLoader.load();
    }
    void setupPermissions() {
        PermissionService service = game.getServiceManager().provideUnchecked(PermissionService.class);
        Optional<PermissionDescription.Builder> desc_ = service.newDescriptionBuilder(this);
        if (desc_.isPresent()) {
            desc_.get().id("tattle.admin.use").assign(PermissionService.SUBJECTS_SYSTEM, true).assign(PermissionService.SUBJECTS_COMMAND_BLOCK, true).assign(PermissionDescription.ROLE_ADMIN, true).assign(PermissionDescription.ROLE_STAFF, true).description(Text.of("Permission to access the admin menu")).register();
        }
    }
    CommandResult create(CommandSource src, CommandContext args) throws CommandException {
        if (!(src instanceof Player)) {
            throw new CommandException(Text.of(getLang("new.not-player")));
        }
        Player p = (Player) src;
        String explanation = args.<String>getOne("explanation").get();
        Location<World> loc = p.getLocation();
        LocalDateTime time = LocalDateTime.now();
        Complaint.BlockLocation bloc = new Complaint.BlockLocation(loc);
        Complaint complaint = new Complaint(bloc, explanation, time, p.getUniqueId());
        try {
            storage.getNode("complaints").getAppendedNode().setValue(TypeToken.of(Complaint.class), complaint);
            storageLoader.save(storage);
        } catch (ObjectMappingException | IOException e) {
            throw new CommandException(Text.of(getLang("new.fail-create")), e);
        }
        p.sendMessage(Text.of(getLang("new.success-create")));
        return CommandResult.success();
    }
    CommandResult list(CommandSource src, CommandContext args) throws CommandException {
        if (!(src instanceof Player)) {
            throw new CommandException(Text.of(getLang("list.not-player")));
        }
        Player p = (Player) src;
        List<Text> texts = Lists.newArrayList();
        List<Complaint> list;
        try {
            list = storage.getNode("complaints").getList(TypeToken.of(Complaint.class)).stream().filter(c -> c.getOwner().equals(p.getUniqueId())).collect(Collectors.toCollection(Lists::newArrayList));
        } catch (ObjectMappingException e) {
            throw new CommandException(Text.of(getLang("list.error-load")), e);
        }
        if (list.isEmpty()) {
            p.sendMessage(Text.of(getLang("list.empty-list")));
            return CommandResult.empty();
        }
        for (Complaint complaint : list) {
            Complaint.BlockLocation loc = complaint.getLocation();
            WorldProperties world = game.getServer().getWorldProperties(loc.getWorldID()).get();
            Text text = Text.of(
                    "["+complaint.getFormattedTimestamp()+"]", " ",
                    Text.builder(btn("view")).color(TextColors.GREEN).onHover(TextActions.showText(Text.of(clickMe()))).onClick(TextActions.executeCallback(s -> showComplaint(complaint, s))), " ",
                    Text.builder(btn("delete")).color(TextColors.RED).onHover(TextActions.showText(Text.of(clickMe()))).onClick(TextActions.executeCallback(s -> deleteComplaint(complaint, s)))
            );
            texts.add(text);
        }
        PaginationList.builder()
                .contents(texts)
                .header(Text.of(getWithArgs("list.header", ImmutableMap.of("player", p.getName()))))
                .title(Text.of(getLang("list.title")))
                .build()
                .sendTo(p);
        return CommandResult.builder().successCount(list.size()).build();
    }
    void showComplaint(Complaint complaint, CommandSource src) {
        src.sendMessages(
                Text.of(getWithArgs("buttons.btnView.header", ImmutableMap.of(
                        "player", game.getServiceManager().provideUnchecked(UserStorageService.class).get(complaint.getOwner()).get().getName(),
                        "timestamp", complaint.getFormattedTimestamp(),
                        "location", complaint.getLocation().toString(),
                        "world", game.getServer().getWorldProperties(complaint.getLocation().getWorldID()).get().getWorldName()
                        ))),
                Text.of(complaint.getDescription())
        );
    }
    void deleteComplaint(Complaint complaint, CommandSource src) {
        Player p = (Player) src;
        try {
            List<Complaint> list = storage.getNode("complaints").getList(TypeToken.of(Complaint.class)).stream().filter(c -> c.getOwner().equals(p.getUniqueId())).collect(Collectors.toCollection(Lists::newArrayList));
            if (!list.contains(complaint)) {
                src.sendMessage(Text.of(getLang("buttons.btnDelete.not-exist")));
                return;
            } else {
                list.remove(complaint);
                storage.getNode("complaints").setValue(new TypeToken<List<Complaint>>(){}, list);
                storageLoader.save(storage);
                p.sendMessage(Text.of(getLang("buttons.btnDelete.success")));
            }
        } catch (ObjectMappingException | IOException e) {
            throw new RuntimeException(e);
        }
    }
    void deleteComplaintAdmin(Complaint complaint, CommandSource src) {
        try {
            List<Complaint> list = Lists.newArrayList(storage.getNode("complaints").getList(TypeToken.of(Complaint.class)));
            if (!list.contains(complaint)) {
                src.sendMessage(Text.of(getLang("buttons.btnDelete.success")));
                return;
            } else {
                list.remove(complaint);
                storage.getNode("complaints").setValue(new TypeToken<List<Complaint>>(){}, list);
                storageLoader.save(storage);
                src.sendMessage(Text.of(getLang("buttons.btnDelete.success")));
            }
        } catch (ObjectMappingException | IOException e) {
            throw new RuntimeException(e);
        }
    }
    void teleport(Complaint complaint, CommandSource src) {
        if (!(src instanceof Player)) {
            src.sendMessage(Text.of(getLang("buttons.btnTeleport.not-allowed")));
            return;
        } else {
            Player p = (Player) src;
            p.setLocation(complaint.getLocation().getLocation());
        }
    }
    CommandResult admin(CommandSource src, CommandContext args) throws CommandException {
        List<Text> texts = Lists.newArrayList();
        List<Complaint> list;
        try {
            list = Lists.newArrayList(storage.getNode("complaints").getList(TypeToken.of(Complaint.class)));
        } catch (ObjectMappingException e) {
            throw new CommandException(Text.of(getLang("admin.error-load")), e);
        }
        if (list.isEmpty()) {
            src.sendMessage(Text.of(getLang("admin.empty-list")));
            return CommandResult.empty();
        }
        for (Complaint complaint : list) {
            Complaint.BlockLocation loc = complaint.getLocation();
            WorldProperties world = game.getServer().getWorldProperties(loc.getWorldID()).get();
            Text text = Text.of(
                    "["+complaint.getFormattedTimestamp()+"]", " ",
                    Text.builder(btn("view")).color(TextColors.GREEN).onHover(TextActions.showText(Text.of(clickMe()))).onClick(TextActions.executeCallback(s -> showComplaint(complaint, s))), " ",
                    Text.builder(btn("teleport")).color(TextColors.BLUE).onHover(TextActions.showText(Text.of(clickMe()))).onClick(TextActions.executeCallback(s -> teleport(complaint, s))), " ",
                    Text.builder(btn("delete")).color(TextColors.RED).onHover(TextActions.showText(Text.of(clickMe()))).onClick(TextActions.executeCallback(s -> deleteComplaintAdmin(complaint, s)))
            );
            texts.add(text);
        }
        PaginationList.builder()
                .contents(texts)
                .title(Text.of(getLang("admin.header")))
                .build()
                .sendTo(src);
        return CommandResult.builder().successCount(list.size()).build();
    }
    String getLang(String path) {
        return config.getNode("lang").getNode((Object[]) path.split("\\.")).getString();
    }
    String btn(String button) {
        return "["+config.getNode("lang", "buttons", button).getString()+"]";
    }
    String clickMe() {
        return config.getNode("lang", "buttons", "click-me").getString();
    }
    String getWithArgs(String path, Map<String, String> repl) {
        String lang = getLang(path);
        for (Map.Entry<String, String> entry : repl.entrySet()) {
            lang = lang.replace("<"+entry.getKey()+">", entry.getValue());
        }
        return lang;
    }
}
