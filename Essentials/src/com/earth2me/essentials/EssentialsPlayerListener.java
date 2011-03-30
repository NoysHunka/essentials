package com.earth2me.essentials;

import java.util.List;
import java.util.logging.Logger;
import net.minecraft.server.InventoryPlayer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.craftbukkit.block.CraftSign;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftInventoryPlayer;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.inventory.ItemStack;


public class EssentialsPlayerListener extends PlayerListener
{
	private static final Logger logger = Logger.getLogger("Minecraft");
	private final Server server;
	private final Essentials parent;

	public EssentialsPlayerListener(Essentials parent)
	{
		this.parent = parent;
		this.server = parent.getServer();
	}

	private void onPlayerInteractEco(PlayerInteractEvent event)
	{
		if (Essentials.getSettings().areSignsDisabled()) return;
		User user = User.get(event.getPlayer());
		if (event.getClickedBlock().getType() != Material.WALL_SIGN && event.getClickedBlock().getType() != Material.SIGN_POST)
			return;
		Sign sign = new CraftSign(event.getClickedBlock());

		if (sign.getLine(0).equals("§1[Buy]") && user.isAuthorized("essentials.signs.buy.use"))
		{
			try
			{
				int amount = Integer.parseInt(sign.getLine(1));
				ItemStack item = ItemDb.get(sign.getLine(2), amount);
				int cost = Integer.parseInt(sign.getLine(3).substring(1));
				if (user.getMoney() < cost) throw new Exception("You do not have sufficient funds.");
				user.takeMoney(cost);
				user.getInventory().addItem(item);
				user.updateInventory();
			}
			catch (Throwable ex)
			{
				user.sendMessage("§cError: " + ex.getMessage());
			}
			return;
		}

		if (sign.getLine(0).equals("§1[Sell]") && user.isAuthorized("essentials.signs.sell.use"))
		{
			try
			{
				int amount = Integer.parseInt(sign.getLine(1));
				ItemStack item = ItemDb.get(sign.getLine(2), amount);
				int cost = Integer.parseInt(sign.getLine(3).substring(1));
				if (!InventoryWorkaround.containsItem((CraftInventory)user.getInventory(), true, item)) throw new Exception("You do not have enough items to sell.");
				user.giveMoney(cost);
				InventoryWorkaround.removeItem((CraftInventory)user.getInventory(), true, item);
				user.updateInventory();
			}
			catch (Throwable ex)
			{
				user.sendMessage("§cError: " + ex.getMessage());
			}
			return;
		}

		if (sign.getLine(0).equals("§1[Trade]") && user.isAuthorized("essentials.signs.trade.use"))
		{
			try
			{
				String[] l1 = sign.getLines()[1].split("[ :-]+");
				String[] l2 = sign.getLines()[2].split("[ :-]+");
				boolean m1 = l1[0].matches("\\$[0-9]+");
				boolean m2 = l2[0].matches("\\$[0-9]+");
				int q1 = Integer.parseInt(m1 ? l1[0].substring(1) : l1[0]);
				int q2 = Integer.parseInt(m2 ? l2[0].substring(1) : l2[0]);
				int r1 = Integer.parseInt(l1[m1 ? 1 : 2]);
				int r2 = Integer.parseInt(l2[m2 ? 1 : 2]);
				r1 = r1 - r1 % q1;
				r2 = r2 - r2 % q2;
				if (q1 < 1 || q2 < 1) throw new Exception("Quantities must be greater than 0.");

				ItemStack i1 = m1 || r1 <= 0 ? null : ItemDb.get(l1[1], r1);
				ItemStack qi1 = m1 ? null : ItemDb.get(l1[1], q1);
				ItemStack qi2 = m2 ? null : ItemDb.get(l2[1], q2);

				if (user.getName().equals(sign.getLines()[3].substring(2)))
				{
					if (m1)
					{
						user.giveMoney(r1);
					}
					else if (i1 != null)
					{
						user.getInventory().addItem(i1);
						user.updateInventory();
					}
					r1 = 0;
					sign.setLine(1, (m1 ? "$" + q1 : q1 + " " + l1[1]) + ":" + r1);
				}
				else
				{
					if (m1)
					{
						if (user.getMoney() < q1)
							throw new Exception("You do not have sufficient funds.");
					}
					else
					{
						if (!InventoryWorkaround.containsItem((CraftInventory)user.getInventory(), true, qi1))
							throw new Exception("You do not have " + q1 + "x " + l1[1] + ".");
					}

					if (r2 < q2) throw new Exception("The trade sign does not have enough supply left.");

					if (m1)
						user.takeMoney(q1);
					else
						InventoryWorkaround.removeItem((CraftInventory)user.getInventory(), true, qi1);

					if (m2)
						user.giveMoney(q2);
					else
						user.getInventory().addItem(qi2);

					user.updateInventory();

					r1 += q1;
					r2 -= q2;

					sign.setLine(0, "§1[Trade]");
					sign.setLine(1, (m1 ? "$" + q1 : q1 + " " + l1[1]) + ":" + r1);
					sign.setLine(2, (m2 ? "$" + q2 : q2 + " " + l2[1]) + ":" + r2);

					user.sendMessage("§7Trade completed.");
				}
			}
			catch (Throwable ex)
			{
				user.sendMessage("§cError: " + ex.getMessage());
			}
			return;
		}
	}

	@Override
	public void onPlayerInteract(PlayerInteractEvent event)
	{
		if (event.isCancelled()) return;
		User user = User.get(event.getPlayer());
		if (user.isJailed())
		{
			event.setCancelled(true);
			return;
		}
		
		onPlayerInteractEco(event);
		onPlayerInteractSigns(event);

		if (!Essentials.getSettings().areSignsDisabled() && EssentialsBlockListener.protectedBlocks.contains(event.getClickedBlock().getType()))
		{
			if (!user.isAuthorized("essentials.signs.protection.override"))
			{
				if (EssentialsBlockListener.isBlockProtected(event.getClickedBlock(), user))
				{
					event.setCancelled(true);
					user.sendMessage("§cYou do not have permission to access that chest.");
					return;
				}
			}
		}

		if (Essentials.getSettings().getBedSetsHome() && event.getClickedBlock().getType() == Material.BED_BLOCK)
		{
			try
			{
				user.setHome();
				user.sendMessage("§7Your home is now set to this bed.");
			}
			catch (Throwable ex)
			{
			}
		}
	}

	private void onPlayerInteractSigns(PlayerInteractEvent event)
	{
		User user = User.get(event.getPlayer());
		if (user.isJailed()) return;
		if (Essentials.getSettings().areSignsDisabled()) return;
		if (event.getClickedBlock().getType() != Material.WALL_SIGN && event.getClickedBlock().getType() != Material.SIGN_POST)
			return;
		Sign sign = new CraftSign(event.getClickedBlock());

		try
		{
			if (sign.getLine(0).equals("§1[Free]") && user.isAuthorized("essentials.signs.free.use"))
			{
				ItemStack item = ItemDb.get(sign.getLine(1));
				CraftInventoryPlayer inv = new CraftInventoryPlayer(new InventoryPlayer(user.getHandle()));
				inv.clear();
				item.setAmount(9 * 4 * 64);
				inv.addItem(item);
				user.showInventory(inv);
				return;
			}
			if (sign.getLine(0).equals("§1[Disposal]") && user.isAuthorized("essentials.signs.disposal.use"))
			{
				CraftInventoryPlayer inv = new CraftInventoryPlayer(new InventoryPlayer(user.getHandle()));
				inv.clear();
				user.showInventory(inv);
				return;
			}
			if (sign.getLine(0).equals("§1[Heal]") && user.isAuthorized("essentials.signs.heal.use"))
			{
				user.setHealth(20);
				user.sendMessage("§7You have been healed.");
				return;
			}
			if (sign.getLine(0).equals("§1[Mail]") && user.isAuthorized("essentials.signs.mail.use") && user.isAuthorized("essentials.mail"))
			{
				List<String> mail = Essentials.readMail(user);
				if (mail.isEmpty())
				{
					user.sendMessage("§cYou do not have any mail!");
					return;
				}
				for (String s : mail) user.sendMessage(s);
				user.sendMessage("§cTo mark your mail as read, type §c/mail clear");
				return;
			}
		}
		catch (Throwable ex)
		{
			user.sendMessage("§cError: " + ex.getMessage());
		}
	}

	@Override
	public void onPlayerRespawn(PlayerRespawnEvent event)
	{
		User user = User.get(event.getPlayer());
		user.setDisplayName(user.getNick());
		updateCompass(user);
	}

	@Override
	public void onPlayerChat(PlayerChatEvent event)
	{
		User user = User.get(event.getPlayer());
		if (user.isMuted())
		{
			event.setCancelled(true);
			logger.info(user.getName() + " tried to speak, but is muted.");
		}
	}

	@Override
	public void onPlayerMove(PlayerMoveEvent event)
	{
		if (event.isCancelled()) return;
		final User user = User.get(event.getPlayer());

		if (!Essentials.getSettings().getNetherPortalsEnabled()) return;

		final Block block = event.getPlayer().getWorld().getBlockAt(event.getTo().getBlockX(), event.getTo().getBlockY(), event.getTo().getBlockZ());
		List<World> worlds = server.getWorlds();

		if (block.getType() == Material.PORTAL && worlds.size() > 1 && user.isAuthorized("essentials.portal"))
		{
			if (user.getJustPortaled()) return;

			Location loc = event.getTo();
			final World world = worlds.get(user.getWorld() == worlds.get(0) ? 1 : 0);

			double factor;
			if (user.getWorld().getEnvironment() == World.Environment.NETHER && world.getEnvironment() == World.Environment.NORMAL)
				factor = 16.0;
			else if (user.getWorld().getEnvironment() != world.getEnvironment())
				factor = 1.0 / 16.0;
			else
				factor = 1.0;

			int x = loc.getBlockX();
			int y = loc.getBlockY();
			int z = loc.getBlockZ();

			if (user.getWorld().getBlockAt(x, y, z - 1).getType() == Material.PORTAL)
				z--;
			if (user.getWorld().getBlockAt(x - 1, y, z).getType() == Material.PORTAL)
				x--;

			x = (int)(x * factor);
			z = (int)(z * factor);
			loc = new Location(world, x + .5, y, z + .5);

			Block dest = world.getBlockAt(x, y, z);
			NetherPortal portal = NetherPortal.findPortal(dest);
			if (portal == null)
			{
				if (world.getEnvironment() == World.Environment.NETHER || Essentials.getSettings().getGenerateExitPortals())
				{
					portal = NetherPortal.createPortal(dest);
					logger.info(event.getPlayer().getName() + " used a portal and generated an exit portal.");
					user.sendMessage("§7Generating an exit portal.");
					loc = portal.getSpawn();
				}
			}
			else
			{
				logger.info(event.getPlayer().getName() + " used a portal and used an existing exit portal.");
				user.sendMessage("§7Teleporting via portal to an existing portal.");
				loc = portal.getSpawn();
			}

			event.setFrom(loc);
			event.setTo(loc);
			try
			{
				user.teleportToNow(loc);
			}
			catch (Exception ex)
			{
				user.sendMessage(ex.getMessage());
			}
			user.setJustPortaled(true);
			user.sendMessage("§7Teleporting via portal.");

			event.setCancelled(true);
			return;
		}

		user.setJustPortaled(false);
	}

	@Override
	public void onPlayerQuit(PlayerQuitEvent event)
	{
		if (!Essentials.getSettings().getReclaimSetting())
			return;

		User.get(event.getPlayer()).dispose();
		Thread thread = new Thread(new Runnable()
		{
			@SuppressWarnings("LoggerStringConcat")
			public void run()
			{
				try
				{
					Thread.sleep(1000);
					Runtime rt = Runtime.getRuntime();
					double mem = rt.freeMemory();
					rt.runFinalization();
					rt.gc();
					mem = rt.freeMemory() - mem;
					mem /= 1024 * 1024;
					logger.info("Freed " + mem + " MB.");
				}
				catch (InterruptedException ex)
				{
					return;
				}
			}
		});
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	@Override
	public void onPlayerJoin(PlayerJoinEvent event)
	{
		Essentials.getStatic().backup.onPlayerJoin();
		User user = User.get(event.getPlayer());

		//we do not know the ip address on playerlogin so we need to do this here.
		if (user.isIpBanned())
		{
			user.kickPlayer("The Ban Hammer has spoken!");
			return;
		}

		user.setDisplayName(user.getNick());

		if (!Essentials.getSettings().isCommandDisabled("motd") && user.isAuthorized("essentials.motd"))
		{
			for (String m : parent.getMotd(user, null))
			{
				if (m == null) continue;
				user.sendMessage(m);
			}
		}

		if (!Essentials.getSettings().isCommandDisabled("mail"))
		{
			List<String> mail = Essentials.readMail(user);
			if (mail.isEmpty()) user.sendMessage("§7You have no new mail.");
			else user.sendMessage("§cYou have " + mail.size() + " messages!§f Type §7/mail read§f to view your mail.");
		}
	}

	@Override
	public void onPlayerLogin(PlayerLoginEvent event)
	{
		User user = User.get(event.getPlayer());
		if (event.getResult() != Result.ALLOWED)
			return;

		if (user.isBanned())
		{
			event.disallow(Result.KICK_BANNED, "The Ban Hammer has spoken!");
			return;
		}

		if (server.getOnlinePlayers().length >= server.getMaxPlayers() && !user.isOp())
		{
			event.disallow(Result.KICK_FULL, "Server is full");
			return;
		}

		updateCompass(user);
	}

	private void updateCompass(User user)
	{
		try
		{
			if (server.getPluginManager().isPluginEnabled("EssentialsHome"))
				user.setCompassTarget(user.getHome());
		}
		catch (Throwable ex)
		{
		}
	}

	@Override
	public void onPlayerTeleport(PlayerTeleportEvent event)
	{
		User user = User.get(event.getPlayer());
		if (user.currentJail == null || user.currentJail.isEmpty())
			return;
		event.setCancelled(true);
		user.sendMessage(ChatColor.RED + "You do the crime, you do the time.");
	}
}