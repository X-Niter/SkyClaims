package net.mohron.skyclaims.world;

import com.flowpowered.math.vector.Vector3d;
import com.google.common.collect.Sets;
import me.ryanhamshire.griefprevention.api.claim.Claim;
import me.ryanhamshire.griefprevention.api.claim.ClaimManager;
import me.ryanhamshire.griefprevention.api.claim.TrustType;
import net.mohron.skyclaims.SkyClaims;
import net.mohron.skyclaims.exception.CreateIslandException;
import net.mohron.skyclaims.exception.InvalidRegionException;
import net.mohron.skyclaims.util.ClaimUtil;
import net.mohron.skyclaims.util.ConfigUtil;
import net.mohron.skyclaims.world.region.IRegionPattern;
import net.mohron.skyclaims.world.region.Region;
import net.mohron.skyclaims.world.region.SpiralRegionPattern;
import org.spongepowered.api.entity.Transform;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class Island {
	private static final SkyClaims PLUGIN = SkyClaims.getInstance();
	private static final ClaimManager CLAIM_MANAGER = PLUGIN.getGriefPrevention().getClaimManager(ConfigUtil.getWorld());
	private static final IRegionPattern PATTERN = new SpiralRegionPattern();

	private UUID id;
	private UUID owner;
	private UUID claim;
	private Transform<World> spawn;
	private boolean locked;

	public Island(User owner, String schematic) throws CreateIslandException {
		this.id = UUID.randomUUID();

		this.owner = owner.getUniqueId();
		Region region;
		try {
			region = PATTERN.nextRegion();
		} catch (InvalidRegionException e) {
			throw new CreateIslandException(e.getText());
		}
		this.spawn = new Transform<>(region.getCenter());
		this.locked = false;

		// Create the island claim
		this.claim = ClaimUtil.createIslandClaim(owner.getUniqueId(), region).getUniqueId();

		// Run commands defined in config on creation
		ConfigUtil.getCreateCommands().ifPresent(commands -> {
			for (String command : commands) {
				PLUGIN.getGame().getCommandManager().process(PLUGIN.getGame().getServer().getConsole(), command.replace("@p", owner.getName()));
			}
		});

		// Generate the island using the specified schematic
		GenerateIslandTask generateIsland = new GenerateIslandTask(owner.getUniqueId(), this, schematic);
		PLUGIN.getGame().getScheduler().createTaskBuilder().execute(generateIsland).submit(PLUGIN);

		save();
	}

	public Island(UUID id, UUID owner, UUID claimId, Vector3d spawnLocation, boolean locked) {
		this.id = id;
		this.owner = owner;
		this.spawn = new Transform<>(ConfigUtil.getWorld(), spawnLocation);
		this.locked = locked;
		this.claim = claimId;

		Claim claim = CLAIM_MANAGER.getClaimByUUID(claimId).orElse(null);
		if (claim != null) {
			this.claim = claimId;
			claim.getData().setResizable(false);
			claim.getData().setClaimExpiration(false);
			claim.getData().setRequiresClaimBlocks(false);
		} else {
			try {
				this.claim = ClaimUtil.createIslandClaim(owner, getRegion()).getUniqueId();
				PLUGIN.queueForSaving(this);
			} catch (CreateIslandException e) {
				PLUGIN.getLogger().error("Failed to create a new claim for island " + id);
			}
		}
	}

	public static Optional<Island> get(UUID islandUniqueId) {
		return (SkyClaims.islands.containsKey(islandUniqueId)) ? Optional.of(SkyClaims.islands.get(islandUniqueId)) : Optional.empty();
	}

	public static Optional<Island> get(Location<World> location) {
		for (Island island : SkyClaims.islands.values()) {
			if (island.contains(location)) return Optional.of(island);
		}
		return Optional.empty();
	}

	public static Optional<Island> get(Claim claim) {
		for (Island island : SkyClaims.islands.values()) {
			if (island.getClaim().isPresent() && island.getClaim().get().equals(claim)) return Optional.of(island);
		}
		return Optional.empty();
	}

	@Deprecated
	public static Optional<Island> getByOwner(UUID owner) {
		for (Island island : SkyClaims.islands.values()) {
			if (island.getOwnerUniqueId().equals(owner)) return Optional.of(island);
		}
		return Optional.empty();
	}

	public static boolean hasIsland(UUID owner) {
		if (SkyClaims.islands.isEmpty()) return false;
		for (Island island : SkyClaims.islands.values()) {
			if (island.getOwnerUniqueId().equals(owner)) return true;
		}
		return false;
	}

	public static int getIslandsOwned(UUID owner) {
		int i = 0;
		for (Island island : SkyClaims.islands.values()) {
			if (island.getOwnerUniqueId().equals(owner)) i++;
		}
		return i;
	}

	public UUID getUniqueId() {
		return id;
	}

	public UUID getOwnerUniqueId() {
		return owner;
	}

	public String getOwnerName() {
		return getName(owner);
	}

	@SuppressWarnings("OptionalGetWithoutIsPresent")
	private String getName(UUID uuid) {
		Optional<User> player = PLUGIN.getGame().getServiceManager().provide(UserStorageService.class).get().get(uuid);
		if (player.isPresent()) {
			return player.get().getName();
		} else {
			try {
				return PLUGIN.getGame().getServer().getGameProfileManager().get(uuid).get().getName().get();
			} catch (Exception e) {
				return "somebody";
			}
		}
	}

	public UUID getClaimUniqueId() {
		return claim;
	}

	public Optional<Claim> getClaim() {
		return CLAIM_MANAGER.getClaimByUUID(this.claim);
	}

	public Date getDateCreated() {
		return (getClaim().isPresent()) ? Date.from(getClaim().get().getData().getDateCreated()) : null;
	}

	public Text getName() {
		return (getClaim().isPresent()) ? (getClaim().get().getName().isPresent()) ? getClaim().get().getName().get() : Text.of(getOwnerName(), "'s Island") : Text.of(getOwnerName(), "'s Island");
	}

	public boolean isLocked() {
		return locked;
	}

	public void setLocked(boolean locked) {
		this.locked = locked;
		// TODO: Set protection flags if possible
		save();
	}

	public World getWorld() {
		return ConfigUtil.getWorld();
	}

	public Transform<World> getSpawn() {
		return spawn;
	}

	public void setSpawn(Transform<World> transform) {
		if (contains(transform.getLocation())) {
			Transform<World> spawn = new Transform<>(ConfigUtil.getWorld(), transform.getPosition(), transform.getRotation());
			if (transform.getLocation().getY() < 0 || transform.getLocation().getY() > 255) {
				spawn.setPosition(new Vector3d(spawn.getLocation().getX(), ConfigUtil.getIslandHeight(), spawn.getLocation().getZ()));
			}
			this.spawn = spawn;
			getClaim().ifPresent(claim -> {
				claim.getData().setSpawnPos(spawn.getPosition().toInt());
			});
			save();
		}
	}

	private boolean contains(Location<World> location) {
		return Region.get(location).equals(getRegion());
	}

	public int getRadius() {
		return (getClaim().isPresent()) ? (getClaim().get().getGreaterBoundaryCorner().getBlockX() - getClaim().get().getLesserBoundaryCorner().getBlockX()) / 2 : 256;
	}

	public Set<String> getMembers() {
		Set<String> members = Sets.newHashSet();
		if (!getClaim().isPresent()) return members;
		for (UUID builder : getClaim().get().getTrusts(TrustType.BUILDER)) {
			members.add(getName(builder));
		}
		for (UUID manager : getClaim().get().getTrusts(TrustType.MANAGER)) {
			members.add(getName(manager));
		}
		return members;
	}

	public boolean hasPermissions(User user) {
		return user.getUniqueId().equals(owner)
				|| getClaim().isPresent()
				&& (getClaim().get().getTrusts(TrustType.ACCESSOR).contains(user.getUniqueId())
				|| getClaim().get().getTrusts(TrustType.BUILDER).contains(user.getUniqueId())
				|| getClaim().get().getTrusts(TrustType.CONTAINER).contains(user.getUniqueId())
				|| getClaim().get().getTrusts(TrustType.MANAGER).contains(user.getUniqueId()));
	}

	public Set<Player> getPlayers() {
		Set<Player> players = Sets.newHashSet();
		for (Player player : PLUGIN.getGame().getServer().getOnlinePlayers()) {
			if (player.getLocation().getChunkPosition().getX() >> 5 == getRegion().getX()
					&& player.getLocation().getChunkPosition().getZ() >> 5 == getRegion().getZ())
				players.add(player);
		}
		return players;
	}

	public Region getRegion() {
		return Region.get(getSpawn().getLocation());
	}

	public void transfer(User user) {
		getClaim().ifPresent(claim -> {
			claim.transferOwner(user.getUniqueId());
		});
		this.owner = user.getUniqueId();
		save();
	}

	private void save() {
		SkyClaims.islands.put(id, this);
		PLUGIN.getDatabase().saveIsland(this);
	}

	public void regen() {
		RegenerateRegionTask regenerateRegionTask = new RegenerateRegionTask(getRegion());
		PLUGIN.getGame().getScheduler().createTaskBuilder().execute(regenerateRegionTask).submit(PLUGIN);
	}

	public void delete() {
		getClaim().ifPresent(claim -> {
			CLAIM_MANAGER.deleteClaim(claim, Cause.source(PLUGIN).build());
			SkyClaims.islandClaims.remove(claim);
		});
		SkyClaims.islands.remove(id);
		PLUGIN.getDatabase().removeIsland(this);
	}
}