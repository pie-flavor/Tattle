package flavor.pie.tattle;

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
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.service.pagination.PaginationList;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.storage.WorldProperties;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Plugin(id="tattle",name="Tattle",authors="pie_flavor",description="A reports plugin.")
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
            if (!dir.toFile().exists())
                if (!dir.toFile().mkdir())
                    throw new IOException();
            configPath = dir.resolve("config.conf");
            storagePath = dir.resolve("storage.conf");
            if (!storagePath.toFile().exists()) {
                if (!storagePath.toFile().createNewFile())
                    throw new IOException();
            }
            if (!configPath.toFile().exists()) {
                game.getAssetManager().getAsset(this, "assets/tattle/default.conf").get().copyToFile(configPath);
            }
            configLoader = HoconConfigurationLoader.builder().setPath(configPath).build();
            storageLoader = HoconConfigurationLoader.builder().setPath(storagePath).build();
            config = configLoader.load();
            storage = storageLoader.load();
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
    }

    CommandResult create(CommandSource src, CommandContext args) throws CommandException {
        if (!(src instanceof Player)) {
            throw new CommandException(Text.of("Only players can create complaints!"));
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
            throw new CommandException(Text.of("Could not create complaint!"), e);
        }
        p.sendMessage(Text.of("Submitted a new complaint."));
        return CommandResult.success();
    }
    CommandResult list(CommandSource src, CommandContext args) throws CommandException {
        if (!(src instanceof Player)) {
            throw new CommandException(Text.of("Only players can see their complaints!"));
        }
        Player p = (Player) src;
        List<Text> texts = Lists.newArrayList();
        List<Complaint> list;
        try {
            list = storage.getNode("complaints").getList(TypeToken.of(Complaint.class)).stream().filter(c -> c.getOwner().equals(p.getUniqueId())).collect(Collectors.toCollection(Lists::newArrayList));
        } catch (ObjectMappingException e) {
            throw new CommandException(Text.of("Error loading complaints!"), e);
        }

        for (Complaint complaint : list) {
            Complaint.BlockLocation loc = complaint.getLocation();
            WorldProperties world = game.getServer().getWorldProperties(loc.getWorldID()).get();
            Text text = Text.of(
                    "["+complaint.getFormattedTimestamp()+"]",
                    " at ", +loc.getX()+", "+loc.getY()+", "+loc.getZ()+" ~ "+world.getWorldName()+"]", " ",
                    Text.builder("[View]").color(TextColors.GREEN).onClick(TextActions.executeCallback(s -> showComplaint(complaint, s))), " ",
                    Text.builder("[Delete]").color(TextColors.RED).onClick(TextActions.executeCallback(s -> deleteComplaint(complaint, s)))
            );
            texts.add(text);
        }
        PaginationList.builder()
                .contents(texts)
                .header(Text.of("Complaints submitted by ", p.getName()))
                .title(Text.of("Complaints"))
                .build()
                .sendTo(p);
        return CommandResult.builder().successCount(list.size()).build();
    }
    void showComplaint(Complaint complaint, CommandSource src) {
        src.sendMessages(
                Text.of("Complaint submitted by "+
                        game.getServiceManager().provideUnchecked(UserStorageService.class).get(complaint.getOwner()).get().getName()
                        +" at "+complaint.getFormattedTimestamp()
                        +" at "+complaint.getLocation()
                        +" in "+game.getServer().getWorldProperties(complaint.getLocation().getWorldID()).get().getWorldName()
                        +": "),
                Text.of(complaint.getDescription())
        );
    }
    void deleteComplaint(Complaint complaint, CommandSource src) {
        Player p = (Player) src;
        try {
            List<Complaint> list = storage.getNode("complaints").getList(TypeToken.of(Complaint.class)).stream().filter(c -> c.getOwner().equals(p.getUniqueId())).collect(Collectors.toCollection(Lists::newArrayList));
            if (!list.contains(complaint)) {
                src.sendMessage(Text.of("This complaint does not exist!"));
                return;
            } else {
                list.remove(complaint);
                storage.getNode("complaints").setValue(new TypeToken<List<Complaint>>(){}, list);
                storageLoader.save(storage);
                p.sendMessage(Text.of("Deleted the complaint."));
            }
        } catch (ObjectMappingException | IOException e) {
            throw new RuntimeException(e);
        }
    }
    void deleteComplaintAdmin(Complaint complaint, CommandSource src) {
        try {
            List<Complaint> list = Lists.newArrayList(storage.getNode("complaints").getList(TypeToken.of(Complaint.class)));
            if (!list.contains(complaint)) {
                src.sendMessage(Text.of("This complaint does not exist!"));
                return;
            } else {
                list.remove(complaint);
                storage.getNode("complaints").setValue(new TypeToken<List<Complaint>>(){}, list);
                storageLoader.save(storage);
                src.sendMessage(Text.of("Deleted the complaint."));
            }
        } catch (ObjectMappingException | IOException e) {
            throw new RuntimeException(e);
        }
    }
    void teleport(Complaint complaint, CommandSource src) {
        if (!(src instanceof Player)) {
            src.sendMessage(Text.of("Sorry, you can't teleport."));
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
            throw new CommandException(Text.of("Error loading complaints!"), e);
        }

        for (Complaint complaint : list) {
            Complaint.BlockLocation loc = complaint.getLocation();
            WorldProperties world = game.getServer().getWorldProperties(loc.getWorldID()).get();
            Text text = Text.of(
                    "["+complaint.getFormattedTimestamp()+"]",
                    " at ", +loc.getX()+", "+loc.getY()+", "+loc.getZ()+" ~ "+world.getWorldName()+"]", " ",
                    Text.builder("[View]").color(TextColors.GREEN).onClick(TextActions.executeCallback(s -> showComplaint(complaint, s))), " ",
                    Text.builder("[Teleport]").color(TextColors.BLUE).onClick(TextActions.executeCallback(s -> teleport(complaint, s))), " ",
                    Text.builder("[Delete]").color(TextColors.RED).onClick(TextActions.executeCallback(s -> deleteComplaintAdmin(complaint, s)))
            );
            texts.add(text);
        }
        PaginationList.builder()
                .contents(texts)
                .title(Text.of("Complaints"))
                .build()
                .sendTo(src);
        return CommandResult.builder().successCount(list.size()).build();
    }
}
