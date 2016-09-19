package de.domisum.lib.compitum.navmesh.edit;

import de.domisum.lib.compitum.CompitumLib;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;

import java.util.ArrayList;

class EditNavMeshCommand extends BukkitCommand
{

	// -------
	// CONSTRUCTOR
	// -------
	EditNavMeshCommand()
	{
		super("editNavMesh");

		this.description = "Used to edit the NavMeshes";
		this.usageMessage = "/editNavMesh";
		// this.setPermission("");

		this.setAliases(new ArrayList<>());
	}


	// -------
	// EXECUTION
	// -------
	@Override
	public boolean execute(CommandSender sender, String alias, String[] args)
	{
		if(!(sender instanceof Player))
		{
			CompitumLib.getLogger().severe("This command can only be used by players!");
			return true;
		}

		Player player = (Player) sender;

		NavMeshEditManager editManager = CompitumLib.getNavMeshManager().getEditManager();
		editManager.executeCommand(player, args);

		return true;
	}

}