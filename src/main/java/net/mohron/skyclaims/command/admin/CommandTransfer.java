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

package net.mohron.skyclaims.command.admin;

import net.mohron.skyclaims.command.CommandBase;
import net.mohron.skyclaims.command.CommandIsland;
import net.mohron.skyclaims.command.argument.Arguments;
import net.mohron.skyclaims.permissions.Permissions;
import net.mohron.skyclaims.team.PrivilegeType;
import net.mohron.skyclaims.world.Island;
import net.mohron.skyclaims.world.IslandManager;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.util.List;

public class CommandTransfer extends CommandBase {

  public static final String HELP_TEXT = "used to transfer island ownership to another player.";
  private static final Text OWNER = Text.of("owner");
  private static final Text USER = Text.of("user");

  public static CommandSpec commandSpec = CommandSpec.builder()
      .permission(Permissions.COMMAND_TRANSFER)
      .description(Text.of(HELP_TEXT))
      .arguments(Arguments.twoUser(OWNER, USER))
      .executor(new CommandTransfer())
      .build();

  public static void register() {
    try {
      CommandIsland.addSubCommand(commandSpec, "transfer");
      PLUGIN.getGame().getCommandManager().register(PLUGIN, commandSpec);
      PLUGIN.getLogger().debug("Registered command: CommandTransfer");
    } catch (UnsupportedOperationException e) {
      PLUGIN.getLogger().error("Failed to register command: CommandTransfer", e);
    }
  }

  @Override
  public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
    User owner = args.<User>getOne(OWNER).orElse(null);
    User user = args.<User>getOne(USER)
        .orElseThrow(() -> new CommandException(Text.of(TextColors.RED, "Invalid user!")));

    if (owner != null) {
      List<Island> ownedIslands = IslandManager.getUserIslandsByPrivilege(owner, PrivilegeType.OWNER);
      return processTransferByOwner(src, ownedIslands, user);
    }

    if (!(src instanceof Player)) {
        throw new CommandException(Text.of(TextColors.RED, "You must supply a owner & user to use this command."));
    }

    Island islandToTransfer = IslandManager.getByLocation(((Player) src).getLocation())
        .orElseThrow(() -> new CommandException(Text.of(
            TextColors.RED, "This command must be run on the island you wish to transfer!")));
    return transferIsland(src, islandToTransfer, user);
  }

  private CommandResult processTransferByOwner(CommandSource src, List<Island> ownedIslands, User user) throws CommandException {
    if (ownedIslands.isEmpty()) {
      throw new CommandException(Text.of(TextColors.RED, "The owner supplied must have an island!"));
    }
    if (ownedIslands.size() > 1) {
      throw new CommandException(Text.of(
          TextColors.RED, "The owner supplied has multiple islands. Please go to the island you want to transfer."
      ));
    }

    Island islandToTransfer = ownedIslands.get(0);
    return transferIsland(src, islandToTransfer, user);
  }

  private CommandResult transferIsland(CommandSource src, Island island, User user) {
    src.sendMessage(Text.of(
        TextColors.GREEN, "Completed transfer of ",
        TextColors.GOLD, island.getOwnerName(),
        TextColors.GREEN, "'s island to ",
        TextColors.GOLD, user.getName(),
        TextColors.GREEN, "."
    ));
    island.transfer(user);

    return CommandResult.success();
  }
}
