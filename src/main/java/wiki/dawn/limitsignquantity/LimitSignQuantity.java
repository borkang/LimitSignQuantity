package wiki.dawn.limitsignquantity;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class LimitSignQuantity extends JavaPlugin implements Listener {
    private File configFile;
    private FileConfiguration config;
    private int allow_number, allow_block_number;
    private String message1, message2, message3, message4;
    private final SignsDatabase database = new SignsDatabase(this);
    private static int getPermissionValue(Player player, String prefix) {
        if (!prefix.endsWith(".")) {
            prefix = prefix + ".";
        }
        int value = -1;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String permission = info.getPermission();
            if (!permission.startsWith(prefix)) {
                continue;
            }

            String valueString = permission.substring(prefix.length());
            if ("*".equals(valueString)) {
                return Integer.MAX_VALUE;
            }

            try {
                int t = Integer.parseInt(valueString);
                if (t > value) {
                    value = t;
                }
            } catch (NumberFormatException ignored) {

            }
        }
        return value;
    }

    private void start() {

        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        allow_number = config.getInt("allowed_placement_amount", 10);
        allow_block_number = config.getInt("block_allowed_placement_amount", 5);
        message1 = config.getString("message1", "§4[提示] §e放置的告示牌数量超过限制！最多只能放置§4 [number3] §e个告示牌！");
        message2 = config.getString("message2", "§4[提示] §e这是你放置的第§4[number1]§e个告示牌，你还能放置§4[number2]§e个告示牌。");
        message3 = config.getString("message3", "§4[提示] §e这是你放置的第§4[number1]§e个告示牌，你还能放置§4[number2]§e个告示牌。");
        message4 = config.getString("message4", "§4[提示] §e放置告示牌数量超过区块限制！为保护服务器，当前区块不能放置更多告示牌！");
        database.connectToDatabase();
    }

    @Override
    public void onEnable() {
        // 插件启动 初始化
        getServer().getPluginManager().registerEvents(this, this);
        start();
    }

    private void end() {
        database.disconnectFromDatabase();
    }

    @Override
    public void onDisable() {
        // 关闭
        end();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {

        // 获取放置的方块类型
        Block block = event.getBlock();
        Material placedBlockType = block.getType();

        // 执行你的处理逻辑
        if (block.getState() instanceof Sign) {
            // 获取放置方块的玩家
            Player player = event.getPlayer();
            String uuid = player.getUniqueId().toString();
            database.check(uuid);

            if (player.isOp()) {
                database.saveSignData((Sign) block.getState(), uuid, player.getWorld().getName());
                return;
            }
            if (allow_block_number != -1 || !player.hasPermission("limitsignquantity.block_amount")) {
                int block_num = database.getSignCountInBlock(player.getLocation().getBlockX() >> 4,
                        player.getLocation().getBlockZ() >> 4,
                        player.getWorld().getName(),
                        uuid);
                int number = getPermissionValue(player, "limitsignquantity.block_amount.");
                int allowed_block_number;
                if (number == -1) allowed_block_number = allow_block_number;
                else allowed_block_number = number;
                if (block_num >= allowed_block_number) {
                    event.setCancelled(true);
                    if (Objects.equals(message4, "")) return;
                    String msg4 = message4;
                    msg4 = msg4.replace("[name]", player.getName());
                    player.sendMessage(msg4);
                    return;
                }
            }
            if (allow_number != -1 || !player.hasPermission("limitsignquantity.amount")) {
                int num = database.getPlayerSignCount(uuid);
                int allowed_number;
                int number = getPermissionValue(player, "limitsignquantity.amount.");
                if(number == -1) allowed_number = allow_number;
                else allowed_number = number;
                if (num >= allowed_number) {
                    event.setCancelled(true);
                    if (Objects.equals(message1, "")) return;
                    String msg1 = message1;
                    msg1 = msg1.replace("[name]", player.getName());
                    msg1 = msg1.replace("[number3]", Integer.toString(allowed_number));
                    player.sendMessage(msg1);
                    return;
                } else {
                    if (Objects.equals(message2, "")) {database.saveSignData((Sign) block.getState(), player.getUniqueId().toString(), player.getWorld().getName());return;}
                    String msg2 = message2;
                    msg2 = msg2.replace("[name]", player.getName());
                    msg2 = msg2.replace("[number1]", Integer.toString(num + 1));
                    msg2 = msg2.replace("[number2]", Integer.toString(allowed_number - num - 1));
                    msg2 = msg2.replace("[number3]", Integer.toString(allowed_number));
                    msg2 = msg2.replace("[number3]", Integer.toString(allowed_number));
                    player.sendMessage(msg2);
                    //saveSignData((Sign) block.getState(), player.getUniqueId().toString(), player.getWorld().getName());
                }
            }
            database.saveSignData((Sign) block.getState(), player.getUniqueId().toString(), player.getWorld().getName());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {

        // 获取被破坏的方块
        Block block = event.getBlock();
        // 检查方块是否是告示牌
        if (block.getState() instanceof Sign) {
            Player player = event.getPlayer();
            Sign sign = (Sign) block.getState();
            sendMessage(sign);// 向非op或无免判断权限的玩家发送消息
            database.deleteSignData(sign.getLocation());
        }
    }


    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {//燃烧破坏告示牌
        Block block = event.getBlock();
        if (block.getState() instanceof Sign) {
            Sign sign = (Sign) block.getState();
            sendMessage(sign);
            database.deleteSignData(sign.getLocation());
        }
    }


    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {//TNT 苦力怕爆炸破坏告示牌
        // 获取爆炸引起的所有破坏方块
        List<Block> destroyedBlocks = event.blockList();

        // 检查是否有告示牌被破坏
        for (Block block : destroyedBlocks) {
            if (block.getState() instanceof Sign) {
                // 删除数据库中的记录
                Sign sign = (Sign) block.getState();
                sendMessage(sign);
                database.deleteSignData(sign.getLocation());
            }
        }
    }


    private void sendMessage(Sign sign) { //当告示牌被破坏时，向所属者发送消息
        UUID uuid = UUID.fromString(database.getSignOwnerUUID(sign));
        Player player = Bukkit.getPlayer(uuid);
        if (player.isOp() || player.hasPermission("limitsignquantity.amount") || allow_number == -1) {
            return;
        }
        if (!player.isOnline()) return;
        if (Objects.equals(message3, "")) return;
        int number = getPermissionValue(player,"limitsignquantity.amount.");
        int allowed_number;
        if(number == -1) allowed_number = allow_number;
        else allowed_number = number;
        String msg3 = message3;
        msg3 = msg3.replace("[name]", player.getName());
        int number2 = database.getPlayerSignCount(uuid.toString());
        msg3 = msg3.replace("[number2]", Integer.toString(Math.max(allowed_number - number2 + 1, 0)));
        msg3 = msg3.replace("[number3]", Integer.toString(allowed_number));
        player.sendMessage(msg3);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (label.equalsIgnoreCase("lsq") || label.equalsIgnoreCase("limitsignquantity")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!player.isOp() && !player.hasPermission("limitsignquantity.reload")) {
                    player.sendMessage(ChatColor.RED + "你没有权限执行该命令！");
                    return true;
                }
            }
            if (args.length == 0 || args.length >= 2) {
                sendHelp(sender);
                return true;
            }
            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("help")) {
                    sendHelp(sender);
                    return true;
                }
                if (args[0].equalsIgnoreCase("reload")) {
                    end();
                    start();
                    sender.sendMessage(ChatColor.AQUA + "插件重载成功");
                    return true;
                }
            }
        }
        return false;
    }

    public void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "------------------------------");
        sender.sendMessage(ChatColor.AQUA + "/lsq help: 帮助");
        sender.sendMessage(ChatColor.AQUA + "/lsq reload: 重载插件");
        sender.sendMessage(ChatColor.AQUA + "------------------------------");
    }
}
