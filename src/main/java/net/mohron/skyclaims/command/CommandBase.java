/*
 * SkyClaims - A Skyblock plugin made for Sponge
 * Copyright (C) 2017 Mohron
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SkyClaims is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SkyClaims.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.mohron.skyclaims.command;

import com.google.common.collect.Lists;
import net.mohron.skyclaims.SkyClaims;
import net.mohron.skyclaims.permissions.Permissions;
import net.mohron.skyclaims.schematic.IslandSchematic;
import net.mohron.skyclaims.schematic.SchematicUI;
import net.mohron.skyclaims.team.PrivilegeType;
import net.mohron.skyclaims.world.Island;
import net.mohron.skyclaims.world.IslandManager;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandPermissionException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.service.pagination.PaginationList;
import org.spongepowered.api.service.pagination.PaginationList.Builder;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.format.TextStyles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class CommandBase implements CommandExecutor {

  protected static final SkyClaims PLUGIN = SkyClaims.getInstance();

  public abstract static class IslandCommand extends CommandBase implements CommandRequirement.RequiresPlayerIsland {

    protected static final Text ISLAND = Text.of("island");

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
      if (!(src instanceof Player)) {
        throw new CommandException(Text.of(TextColors.RED, "This command can only be used by a player!"));
      }
      if (!args.hasAny(ISLAND)) {
        return execute(
            (Player) src,
            IslandManager.getByLocation(((Player) src).getLocation()).orElseThrow(() -> new CommandException(Text.of(
                TextColors.RED, "You must be on an island to use this command!")
            )),
            args);
      } else {
        List<Island> islands = Lists.newArrayList();
        args.<UUID>getAll(ISLAND).forEach(i -> IslandManager.get(i).ifPresent(islands::add));
        if (islands.size() == 1) {
          return execute((Player) src, islands.get(0), args);
        } else {
          throw new CommandException(Text.of(TextColors.RED, "Multiple island support not yet implemented!"));
        }
      }
    }
  }

  public abstract static class PlayerCommand extends CommandBase implements CommandRequirement.RequiresPlayer {

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
      if (src instanceof Player) {
        return execute((Player) src, args);
      }
      throw new CommandException(Text.of(TextColors.RED, "This command can only be used by a player!"));
    }
  }

  public abstract static class LockCommand extends CommandBase implements CommandRequirement.RequiresIsland {

    private boolean lock;

    public LockCommand(boolean lock) {
      this.lock = lock;
    }

    protected static final Text ALL = Text.of("all");
    protected static final Text ISLAND = Text.of("island");

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
      if (args.hasAny(ALL)) {
        return lockAll(src);
      }
      if (!args.hasAny(ISLAND)) {
        if (src instanceof Player) {
          Island island = IslandManager.getByLocation(((Player) src).getLocation()).orElseThrow(() -> new CommandException(
              Text.of(TextColors.RED, "You must provide an island argument or be on an island to use this command!")
          ));
          checkPerms(src, island);
          return execute(src, island, args);
        } else {
          throw new CommandException(Text.of(
              TextColors.RED, "An island argument is required when executed by a non-player!"
          ));
        }
      } else {
        List<Island> islands = Lists.newArrayList();
        args.<UUID>getAll(ISLAND).forEach(i -> IslandManager.get(i).ifPresent(islands::add));
        if (islands.size() == 1) {
          checkPerms(src, islands.get(0));
          return execute(src, islands.get(0), args);
        } else {
          return lockIslands(src, args.getAll(ISLAND));
        }
      }
    }

    private void checkPerms(CommandSource src, Island island) throws CommandPermissionException {
      if (!(src instanceof Player && island.isManager((Player) src) || src
          .hasPermission(Permissions.COMMAND_LOCK_OTHERS))) {
        throw new CommandPermissionException(Text.of(
            TextColors.RED, "You do not have permission to ",
            lock ? "lock " : "unlock ",
            island.getName(), TextColors.RED, "!"
        ));
      }
    }

    private CommandResult lockIslands(CommandSource src, Collection<UUID> islandsIds) {
      ArrayList<Island> islands = Lists.newArrayList();
      islandsIds.forEach(i -> IslandManager.get(i).ifPresent(islands::add));
      islands.forEach(island -> {
        island.setLocked(lock);
        src.sendMessage(Text.of(
            island.getName(),
            TextColors.GREEN, " has been ",
            lock ? "locked" : "unlocked", "!"
        ));
      });
      return CommandResult.success();
    }

    private CommandResult lockAll(CommandSource src) {
      IslandManager.ISLANDS.values().forEach(i -> i.setLocked(lock));
      src.sendMessage(Text.of(
          TextColors.DARK_PURPLE, IslandManager.ISLANDS.size(),
          TextColors.GREEN, " islands have been ",
          lock ? "locked" : "unlocked", "!"
      ));
      return CommandResult.success();
    }
  }

  public abstract static class ListIslandCommand extends PlayerCommand implements CommandRequirement.RequiresPlayer {

    protected static final Text ISLAND = Text.of("island");

    protected CommandResult listIslands(Player player, Function<Island, Consumer<CommandSource>> mapper)
        throws CommandException {
      return listIslands(player, PrivilegeType.MEMBER, mapper);
    }

    protected CommandResult listIslands(Player player, PrivilegeType privilege, Function<Island, Consumer<CommandSource>> mapper)
        throws CommandException {
      List<Island> islands = IslandManager.getUserIslandsByPrivilege(player, privilege);

      if (islands.isEmpty()) {
        throw new CommandException(Text.of(TextColors.RED, "You have no island available!"));
      }
      if (islands.size() == 1) {
        mapper.apply(islands.get(0)).accept(player);
        return CommandResult.empty();
      }

      getInteractiveIslandPagination(mapper, islands).sendTo(player);

      return CommandResult.empty();
    }
  }

  protected static Builder getInteractiveIslandPagination(Function<Island, Consumer<CommandSource>> mapper,
      List<Island> islands) {
    List<Text> islandText = islands.stream()
        .map(s -> s.getName().toBuilder().onClick(TextActions.executeCallback(mapper.apply(s))).build())
        .collect(Collectors.toList());

    return PaginationList.builder()
        .title(Text.of(TextColors.AQUA, "Islands"))
        .padding(Text.of(TextColors.AQUA, TextStyles.STRIKETHROUGH, "-"))
        .contents(islandText);
  }

  public abstract static class ListSchematicCommand extends PlayerCommand implements CommandRequirement.RequiresPlayer {

    protected static final Text SCHEMATIC = Text.of("schematic");
  }

  protected void listSchematics(Player player, Function<IslandSchematic, Consumer<CommandSource>> mapper)
      throws CommandException {
    boolean checkPerms = PLUGIN.getConfig().getPermissionConfig().isSeparateSchematicPerms();

    List<IslandSchematic> schematics = PLUGIN.getSchematicManager().getSchematics().stream()
        .filter(s -> !checkPerms || player.hasPermission(Permissions.COMMAND_ARGUMENTS_SCHEMATICS + "." + s.getName()))
        .collect(Collectors.toList());

    if (schematics.isEmpty()) {
      throw new CommandException(Text.of(TextColors.RED, "You have no schematics available!"));
    }
    if (schematics.size() == 1) {
      mapper.apply(schematics.get(0)).accept(player);
    }

    if (PLUGIN.getConfig().getMiscConfig().isTextSchematicList()) {
      List<Text> schematicText = schematics.stream()
          .map(s -> s.getText().toBuilder()
              .onHover(TextActions.showItem(s.getItemStackRepresentation().createSnapshot()))
              .onClick(TextActions.executeCallback(mapper.apply(s)))
              .build())
          .collect(Collectors.toList());

      PaginationList.builder()
          .title(Text.of(TextColors.AQUA, "Schematics"))
          .padding(Text.of(TextColors.AQUA, TextStyles.STRIKETHROUGH, "-"))
          .contents(schematicText)
          .sendTo(player);
    } else {
      player.openInventory(SchematicUI.of(schematics, mapper));
    }
  }

  protected void clearIslandMemberInventories(Island island, String keepPlayerInv, String keepEnderChestInv) {
    UserStorageService uss = PLUGIN.getGame().getServiceManager().provideUnchecked(UserStorageService.class);

    // Clear the owner's inventory, if enabled
    uss.get(island.getOwnerUniqueId()).ifPresent(o -> clearMemberInventory(o, keepPlayerInv, keepEnderChestInv));
    // Clear each member's inventory, if enabled
    for (User user : island.getMembers()) {
      clearMemberInventory(user, keepPlayerInv, keepEnderChestInv);
    }
  }

  protected void clearMemberInventory(User member, String keepPlayerInv, String keepEnderChestInv) {
    // Check if the player is exempt from having their inventory cleared
    if (!member.hasPermission(keepPlayerInv)) {
      PLUGIN.getLogger().debug("Clearing {}'s player inventory.", member.getName());
      member.getInventory().clear();
    }
    if (!member.hasPermission(keepEnderChestInv)) {
      PLUGIN.getLogger().debug("Clearing {}'s ender chest inventory.", member.getName());
      member.getEnderChestInventory().clear();
    }
  }
}
