package flavor.pie.tattle;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.SimpleConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.bstats.sponge.MetricsLite;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.persistence.DataFormats;
import org.spongepowered.api.data.persistence.DataTranslators;
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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Plugin(id="tattle", name="Tattle", authors="pie_flavor", description="A reports plugin.", version="1.1.1-SNAPSHOT")
public class Tattle {
    @Inject
    Game game;
    @Inject
    PluginContainer container;
    @Inject @ConfigDir(sharedRoot = false)
    Path dir;
    @Inject @DefaultConfig(sharedRoot = false)
    ConfigurationLoader<CommentedConfigurationNode> loader;
    @Inject @DefaultConfig(sharedRoot = false)
    Path path;
    @Inject
    Logger logger;
    @Inject @SuppressWarnings("unused")
    MetricsLite metrics;

    Path storagePath;
    Config config;
    List<Complaint> complaints = new ArrayList<>();

    @Listener
    public void preInit(GamePreInitializationEvent e) throws IOException, ObjectMappingException {
        Files.createDirectories(dir);
        storagePath = dir.resolve("storage.dat");
        loadConfig();
        Path legacyPath = dir.resolve("storage.conf");
        if (Files.exists(legacyPath)) {
            ConfigurationNode node = HoconConfigurationLoader.builder().setPath(legacyPath).build().load();
            DataContainer container = DataTranslators.CONFIGURATION_NODE.translate(node);
            try (OutputStream os = Files.newOutputStream(storagePath)) {
                DataFormats.NBT.writeTo(os, container);
            }
            Files.delete(legacyPath);
        }
        Path legacyConfigPath = dir.resolve("config.conf");
        if (Files.exists(legacyConfigPath)) {
            Files.move(legacyConfigPath, path, StandardCopyOption.REPLACE_EXISTING);
        }
        load();
    }

    private void loadConfig() throws IOException, ObjectMappingException {
        if (!Files.exists(path)) {
            game.getAssetManager().getAsset(this, "default.conf").get().copyToFile(path);
        }
        ConfigurationNode node = loader.load();
        if (node.getNode("version").getInt() < 2) {
            ConfigUpdater.t2(node);
            node.getNode("version").setValue(2);
            loader.save(node);
        }
        config = node.getValue(Config.type);
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
    public void reload(GameReloadEvent e) throws IOException, ObjectMappingException {
        loadConfig();
    }

    void setupPermissions() {
        game.getServiceManager().provideUnchecked(PermissionService.class).newDescriptionBuilder(this)
                .id("tattle.admin.use")
                .assign(PermissionService.SUBJECTS_SYSTEM, true)
                .assign(PermissionService.SUBJECTS_COMMAND_BLOCK, true)
                .assign(PermissionDescription.ROLE_ADMIN, true)
                .assign(PermissionDescription.ROLE_STAFF, true)
                .description(Text.of("Permission to access the admin menu"))
                .register();
    }

    CommandResult create(CommandSource src, CommandContext args) throws CommandException {
        if (!(src instanceof Player)) {
            throw new CommandException(Text.of(config.lang.new_.notPlayer));
        }
        Player p = (Player) src;
        String explanation = args.<String>getOne("explanation").get();
        Location<World> loc = p.getLocation();
        LocalDateTime time = LocalDateTime.now();
        Complaint complaint = new Complaint(loc, explanation, time, p.getUniqueId());
        complaints.add(complaint);
        try {
            save();
        } catch (IOException | ObjectMappingException e) {
            throw new CommandException(Text.of(config.lang.new_.failCreate), e);
        }
        p.sendMessage(Text.of(config.lang.new_.successCreate));
        return CommandResult.success();
    }

    CommandResult list(CommandSource src, CommandContext args) throws CommandException {
        if (!(src instanceof Player)) {
            throw new CommandException(Text.of(config.lang.list.notPlayer));
        }
        Player p = (Player) src;
        List<Text> texts = Lists.newArrayList();
        List<Complaint> list = complaints.stream().filter(c -> c.getOwner().equals(p.getUniqueId()))
                .collect(Collectors.toCollection(Lists::newArrayList));
        if (list.isEmpty()) {
            p.sendMessage(Text.of(config.lang.list.emptyList));
            return CommandResult.empty();
        }
        for (Complaint complaint : list) {
            Complaint.BlockLocation loc = complaint.getLocation();
            WorldProperties world = game.getServer().getWorldProperties(loc.getWorldID()).get();
            Text text = Text.of(
                    "["+complaint.getFormattedTimestamp()+"]", " ",
                    Text.builder("[" + config.lang.button.view + "]")
                            .color(TextColors.GREEN)
                            .onHover(TextActions.showText(Text.of(config.lang.button.clickMe)))
                            .onClick(TextActions.executeCallback(s -> showComplaint(complaint, s))), " ",
                    Text.builder("[" + config.lang.button.delete + "]")
                            .color(TextColors.RED)
                            .onHover(TextActions.showText(Text.of(config.lang.button.clickMe)))
                            .onClick(TextActions.executeCallback(s -> deleteComplaint(complaint, s)))
            );
            texts.add(text);
        }
        PaginationList.builder()
                .contents(texts)
                .header(Text.of(getWithArgs(config.lang.list.header, ImmutableMap.of("player", p.getName()))))
                .title(Text.of(config.lang.list.title))
                .build()
                .sendTo(p);
        return CommandResult.builder().successCount(list.size()).build();
    }

    void showComplaint(Complaint complaint, CommandSource src) {
        src.sendMessages(
                Text.of(getWithArgs(config.lang.button.btnView.header, ImmutableMap.of(
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
        List<Complaint> list = complaints.stream().filter(c -> c.getOwner().equals(p.getUniqueId()))
                .collect(Collectors.toCollection(Lists::newArrayList));
        try {
            if (!list.contains(complaint)) {
                src.sendMessage(Text.of(config.lang.button.btnDelete.notExist));
            } else {
                complaints.remove(complaint);
                save();
                p.sendMessage(Text.of(config.lang.button.btnDelete.success));
            }
        } catch (IOException | ObjectMappingException e) {
            rethrow(e);
        }
    }

    void deleteComplaintAdmin(Complaint complaint, CommandSource src) {
        try {
            if (!complaints.contains(complaint)) {
                src.sendMessage(Text.of(config.lang.button.btnDelete.notExist));
            } else {
                complaints.remove(complaint);
                save();
                src.sendMessage(Text.of(config.lang.button.btnDelete.success));
            }
        } catch (IOException | ObjectMappingException e) {
            rethrow(e);
        }
    }

    void teleport(Complaint complaint, CommandSource src) {
        if (!(src instanceof Player)) {
            src.sendMessage(Text.of(config.lang.button.btnTeleport.notAllowed));
        } else {
            Player p = (Player) src;
            p.setLocation(complaint.getLocation().getLocation());
        }
    }

    CommandResult admin(CommandSource src, CommandContext args) throws CommandException {
        List<Text> texts = Lists.newArrayList();
        if (complaints.isEmpty()) {
            src.sendMessage(Text.of(config.lang.admin.emptyList));
            return CommandResult.empty();
        }
        for (Complaint complaint : complaints) {
            Complaint.BlockLocation loc = complaint.getLocation();
            WorldProperties world = game.getServer().getWorldProperties(loc.getWorldID()).get();
            Text text = Text.of(
                    "["+complaint.getFormattedTimestamp()+"]", " ",
                    Text.builder("[" + config.lang.button.view + "]")
                            .color(TextColors.GREEN)
                            .onHover(TextActions.showText(Text.of(config.lang.button.clickMe)))
                            .onClick(TextActions.executeCallback(s -> showComplaint(complaint, s))), " ",
                    Text.builder("[" + config.lang.button.teleport + "]")
                            .color(TextColors.BLUE)
                            .onHover(TextActions.showText(Text.of(config.lang.button.clickMe)))
                            .onClick(TextActions.executeCallback(s -> teleport(complaint, s))), " ",
                    Text.builder("[" + config.lang.button.delete + "]")
                            .color(TextColors.RED)
                            .onHover(TextActions.showText(Text.of(config.lang.button.clickMe)))
                            .onClick(TextActions.executeCallback(s -> deleteComplaintAdmin(complaint, s)))
            );
            texts.add(text);
        }
        PaginationList.builder()
                .contents(texts)
                .title(Text.of(config.lang.admin.header))
                .build()
                .sendTo(src);
        return CommandResult.builder().successCount(complaints.size()).build();
    }

    String getWithArgs(String lang, Map<String, String> repl) {
        for (Map.Entry<String, String> entry : repl.entrySet()) {
            lang = lang.replace("<"+entry.getKey()+">", entry.getValue());
        }
        return lang;
    }

    private void save() throws IOException, ObjectMappingException {
        List<DataContainer> list = new ArrayList<>();
        DataContainer container = DataContainer.createNew();
        for (Complaint complaint : complaints) {
            ConfigurationNode node = SimpleConfigurationNode.root();
            node.setValue(Complaint.type, complaint);
            list.add(DataTranslators.CONFIGURATION_NODE.translate(node));
        }
        container.set(DataQuery.of("complaints"), list);
        try (OutputStream os = Files.newOutputStream(storagePath)) {
            DataFormats.NBT.writeTo(os, container);
        }
    }

    private void load() throws IOException, ObjectMappingException {
        DataContainer container;
        try (InputStream is = Files.newInputStream(storagePath)) {
            container = DataFormats.NBT.readFrom(is);
        }
        complaints = new ArrayList<>();
        for (DataView view : container.getViewList(DataQuery.of("complaints")).get()) {
            complaints.add(DataTranslators.CONFIGURATION_NODE.translate(view).getValue(Complaint.type));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void rethrow(Throwable t) throws T {
        throw (T) t;
    }
}
