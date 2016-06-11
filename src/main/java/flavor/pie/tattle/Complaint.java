package flavor.pie.tattle;

import com.flowpowered.math.vector.Vector3i;
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;


@ConfigSerializable
public class Complaint {
    public Complaint() {}
    public Complaint(BlockLocation location, String description, LocalDateTime timestamp, UUID owner) {
        setLocation(location);
        setDescription(description);
        setTimestamp(timestamp);
        setOwner(owner);
        setComplaintID(UUID.randomUUID());
    }
    @Setting
    private BlockLocation location;
    @Setting
    private String description;
    @Setting
    private String timestamp;
    @Setting
    private UUID owner;
    @Setting("complaint-id")
    private UUID complaintID;

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public BlockLocation getLocation() {
        return location;
    }

    public void setLocation(BlockLocation location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getTimestamp() {
        return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public String getFormattedTimestamp() {
        return timestamp;
    }

    public UUID getComplaintID() {
        return complaintID;
    }

    public void setComplaintID(UUID complaintID) {
        this.complaintID = complaintID;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Complaint)) return false;
        Complaint c = (Complaint) other;
        return complaintID.equals(c.getComplaintID());
    }
    @ConfigSerializable
    public static class BlockLocation {
        public BlockLocation() {}
        public BlockLocation(int x, int y, int z, UUID worldID) {
            setX(x);
            setY(y);
            setZ(z);
            setWorldID(worldID);
        }
        public BlockLocation(Location<World> location) {
            setX(location.getBlockX());
            setY(location.getBlockY());
            setZ(location.getBlockZ());
            setWorldID(location.getExtent().getUniqueId());
        }
        @Setting
        private int x;
        @Setting
        private int y;
        @Setting
        private int z;
        @Setting("world-id")
        private UUID worldID;

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getZ() {
            return z;
        }

        public void setZ(int z) {
            this.z = z;
        }

        public UUID getWorldID() {
            return worldID;
        }

        public void setWorldID(UUID worldID) {
            this.worldID = worldID;
        }

        public Vector3i getPosition() {
            return new Vector3i(x, y, z);
        }

        public Location<World> getLocation() {
            return Sponge.getGame().getServer().getWorld(worldID).get().getLocation(getPosition());
        }
        @Override
        public String toString() {
            return x+", "+y+", "+z;
        }
    }
}