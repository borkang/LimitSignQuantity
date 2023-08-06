package wiki.dawn.limitsignquantity;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LimitSignQuantity extends JavaPlugin implements Listener {
    private File configFile;
    private FileConfiguration config;
    private int allow_number, allow_block_number;
    private String message1, message2, message3, message4;
    private Connection connection;
    private static final Logger logger = Logger.getLogger("LimitSignQuantity");

    private void setupLogger() {
        try {
            // 创建一个文件处理器，将日志输出到文件中
            Handler fileHandler = new FileHandler("error_sqlite.log");

            // 设置文件处理器的格式
            fileHandler.setFormatter(new java.util.logging.SimpleFormatter());

            // 添加文件处理器到日志记录器
            logger.addHandler(fileHandler);
        } catch (IOException e) {
            // 若出现错误，仅在控制台输出错误信息，因为日志记录器可能无法正常工作
            logger.log(Level.SEVERE, "sqlite数据库的日志文件相关错误", e);
            logger.warning("sqlite数据库的日志文件相关错误，具体错误信息已记录到插件目录下的日志文件");
        }
    }

    private void connectToDatabase() {
        try {
            // 创建SQLite数据库文件
            File dbFile = new File(getDataFolder(), "signs.db");
            if (!dbFile.exists()) {
                dbFile.getParentFile().mkdirs();
                dbFile.createNewFile();
            }

            // 连接到SQLite数据库
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            getLogger().info("成功连接到SQLite数据库！");

            // 检查并创建 "signs" 表
            if (!isSignsTableExists()) {
                createSignsTable();
            }
        } catch (SQLException | IOException e) {
            logger.log(Level.SEVERE, "无法连接到SQLite数据库！", e);
            getLogger().warning("无法连接到SQLite数据库！具体错误信息已记录到插件目录下的日志文件");
        }
    }

    private boolean isSignsTableExists() {
        try {
            // 创建一个用于查询表是否存在的语句
            String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='signs';";
            PreparedStatement statement = connection.prepareStatement(query);

            // 执行查询，并获取结果集
            ResultSet resultSet = statement.executeQuery();

            // 如果结果集中有数据，说明 "signs" 表存在
            boolean tableExists = resultSet.next();

            // 关闭查询相关的资源
            resultSet.close();
            statement.close();

            return tableExists;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "检查表是否存在时出现错误！", e);
            getLogger().warning("检查表是否存在时出现错误！具体错误信息已记录到插件目录下的日志文件");
            return false;
        }
    }

    private void createSignsTable() {
        try {
            // 创建 "signs" 表
            Statement statement = connection.createStatement();
            // language=sqlite
            String createTableQuery = "CREATE TABLE IF NOT EXISTS signs ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "block_x INTEGER NOT NULL,"
                    + "block_z INTEGER NOT NULL,"
                    + "x INTEGER NOT NULL,"
                    + "y INTEGER NOT NULL,"
                    + "z INTEGER NOT NULL,"
                    + "player_uuid TEXT NOT NULL,"
                    + "world TEXT NOT NULL,"
                    + "CONSTRAINT location_unique UNIQUE (x, y, z)"
                    + ");";
            statement.execute(createTableQuery);
            statement.close();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "创建 \"signs\" 表时出现错误！", e);
            getLogger().warning("创建 \"signs\" 表时出现错误！具体错误信息已记录到插件目录下的日志文件");
        }
    }

    private void disconnectFromDatabase() {
        try {
            // 断开数据库连接
            if (connection != null) {
                connection.close();
                getLogger().info("已断开与SQLite数据库的连接！");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "无法断开与SQLite数据库的连接！", e);
            getLogger().warning("无法断开与SQLite数据库的连接！具体错误信息已记录到插件目录下的日志文件");
        }
    }

    private int getSignCountInBlock(int blockX, int blockZ, String world, String uuid) {
        try {
            // 创建一个预编译的SQL语句，用于查询指定区块内的告示牌数量
            String query = "SELECT COUNT(id) AS count FROM signs WHERE block_x = ? AND block_z = ? AND world = ? AND player_uuid = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, blockX);
            statement.setInt(2, blockZ);
            statement.setString(3, world);
            statement.setString(4, uuid);

            // 执行SQL查询，获取结果集
            ResultSet resultSet = statement.executeQuery();

            // 获取查询结果
            if (resultSet.next()) {
                return resultSet.getInt("count");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "无法断开与SQLite数据库的连接！", e);
            getLogger().warning("查询告示牌数量时出现错误！具体错误信息已记录到插件目录下的日志文件");
        }
        return 0;
    }

    private int getPlayerSignCount(String playerUUID) {

        try {
            // 创建一个预编译的SQL语句，用于查询指定玩家所属的告示牌数量
            String query = "SELECT COUNT(id) AS count FROM signs WHERE player_uuid = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, playerUUID);

            // 执行SQL查询，获取结果集
            ResultSet resultSet = statement.executeQuery();

            // 获取查询结果
            if (resultSet.next()) {
                return resultSet.getInt("count");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询玩家所属告示牌数量时出现错误！", e);
            getLogger().warning("查询玩家所属告示牌数量时出现错误！具体错误信息已记录到插件目录下的日志文件");
        }
        return 0;
    }

    private void saveSignData(Sign sign, String playerUUID, String world) {
        // 检查数据库连接是否有效
        if (connection == null) {
            getLogger().warning("无法保存告示牌数据，数据库连接未初始化或已断开！");
            return;
        }

        try {
            // 获取告示牌的坐标
            Location signLocation = sign.getLocation();

            // 创建一个预编译的SQL语句，用于插入数据
            String query = "INSERT INTO signs (block_x, block_z, x, y, z, player_uuid ,world) VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement statement = connection.prepareStatement(query);

            // 设置SQL语句中的参数
            statement.setInt(1, signLocation.getBlockX() >> 4); // 将坐标转换为区块坐标
            statement.setInt(2, signLocation.getBlockZ() >> 4);
            statement.setInt(3, signLocation.getBlockX());
            statement.setInt(4, signLocation.getBlockY());
            statement.setInt(5, signLocation.getBlockZ());
            statement.setString(6, playerUUID);
            statement.setString(7, world);
            // 执行SQL语句，将数据插入数据库
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "保存告示牌数据时出现错误！", e);
            getLogger().warning("保存告示牌数据时出现错误！具体错误信息已记录到插件目录下的日志文件");
        }
    }

    private void deleteSignData(Location signLocation) {
        // 检查数据库连接是否有效
        if (connection == null) {
            getLogger().warning("无法删除告示牌数据，数据库连接未初始化或已断开！");
            return;
        }

        try {

            // 创建一个预编译的SQL语句，用于删除数据
            String query = "DELETE FROM signs WHERE block_x = ? AND block_z = ? AND x = ? AND y = ? AND z = ?";
            PreparedStatement statement = connection.prepareStatement(query);

            // 设置SQL语句中的参数
            statement.setInt(1, signLocation.getBlockX() >> 4); // 将坐标转换为区块坐标
            statement.setInt(2, signLocation.getBlockZ() >> 4);
            statement.setInt(3, signLocation.getBlockX());
            statement.setInt(4, signLocation.getBlockY());
            statement.setInt(5, signLocation.getBlockZ());
            // 执行SQL语句，从数据库中删除数据
            int deletedRows = statement.executeUpdate();
//            if (deletedRows <= 0) {       //因为可能较高频率触发，注释掉不做提醒
//                getLogger().warning("告示牌数据不存在于数据库中，无法从数据库删除！如果该告示牌为安装本插件前存在的，请忽略本消息");
//            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "删除告示牌数据时出现错误！", e);
            getLogger().warning("删除告示牌数据时出现错误！具体错误信息已记录到插件目录下的日志文件");
        }
    }

    private String getSignOwnerUUID(Sign sign) {
        // 获取告示牌的坐标
        Location signLocation = sign.getLocation();


        // 检查数据库连接是否有效
        if (connection == null) {
            getLogger().warning("无法获取告示牌所有者名称，数据库连接未初始化或已断开！");
            return null;
        }

        try {
            // 创建一个预编译的SQL语句，用于查询告示牌所有者名称
            String query = "SELECT player_uuid FROM signs WHERE block_x = ? AND block_z = ? AND x = ? AND y = ? AND z = ?";
            PreparedStatement statement = connection.prepareStatement(query);

            // 设置SQL语句中的参数
            statement.setInt(1, signLocation.getBlockX() >> 4); // 将坐标转换为区块坐标
            statement.setInt(2, signLocation.getBlockZ() >> 4);
            statement.setInt(3, signLocation.getBlockX());
            statement.setInt(4, signLocation.getBlockY());
            statement.setInt(5, signLocation.getBlockZ());

            // 执行SQL查询，获取结果集
            ResultSet resultSet = statement.executeQuery();

            // 获取查询结果
            if (resultSet.next()) {
                return resultSet.getString("player_uuid");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "获取告示牌所有者名称时出现错误！", e);
            getLogger().warning("获取告示牌所有者名称时出现错误！具体错误信息已记录到插件目录下的日志文件");
        }

        return "";
    }

    private List<Location> getSignLocationsByPlayerUUID(String uuid) {
        List<Location> signLocations = new ArrayList<>();

        try {
            // 创建一个预编译的SQL语句，用于查询指定玩家所属的告示牌坐标
            String query = "SELECT x, y, z, world FROM signs WHERE player_uuid = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, uuid);

            // 执行SQL查询，获取结果集
            ResultSet resultSet = statement.executeQuery();

            // 处理查询结果
            while (resultSet.next()) {
                int x = resultSet.getInt("x");
                int y = resultSet.getInt("y");
                int z = resultSet.getInt("z");
                String world = resultSet.getString("world");
                // 创建Location对象并添加到列表中
                signLocations.add(new Location(Bukkit.getWorld(world), x, y, z));
            }

            // 关闭资源
            resultSet.close();
            statement.close();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "获取告示牌位置信息时出现错误！", e);
            getLogger().warning("获取告示牌位置信息时出现错误！具体错误信息已记录到插件目录下的日志文件");
            // 处理异常
        }

        return signLocations;
    }

    private void check(String uuid) {
        List<Location> signLocations = getSignLocationsByPlayerUUID(uuid);
        for (Location location : signLocations) {
            // 获取坐标对应的方块
            Block block = location.getBlock();

            // 检查方块是否是告示牌
            if (!(block.getState() instanceof Sign)) {
                deleteSignData(location);
            }
        }

    }

    private static int getPermissionValue(Player player, String prefix) {
        if (!prefix.endsWith(".")) {
            prefix = prefix + ".";
        }
        int value = 0;
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
        message1 = config.getString("message1", "§4[提示] §e放置的告示牌数量超过限制！最多只能放置§4[allowed_number]§e个告示牌！");
        message2 = config.getString("message2", "§4[提示] §e这是你放置的第§4[number1]§e个告示牌，你还能放置§4[number2]§e个告示牌。");
        message3 = config.getString("message3", "§4[提示] §e这是你放置的第§4[number1]§e个告示牌，你还能放置§4[number2]§e个告示牌。");
        message4 = config.getString("message4", "§4[提示] §e放置的告示牌数量超过区块限制！为保护服务器，当前区块已不能放置更多告示牌！");
        connectToDatabase();
        setupLogger();
    }

    @Override
    public void onEnable() {
        // 插件启动 初始化
        getServer().getPluginManager().registerEvents(this, this);
        start();
    }

    private void end() {
        disconnectFromDatabase();
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
            check(uuid);

            if (player.isOp()) {
                saveSignData((Sign) block.getState(), uuid, player.getWorld().getName());
                return;
            }
            if (allow_block_number != -1 || !player.hasPermission("limitsignquantity.block_amount")) {
                int block_num = getSignCountInBlock(player.getLocation().getBlockX() >> 4,
                        player.getLocation().getBlockZ() >> 4,
                        player.getWorld().getName(),
                        uuid);
                int allowed_block_number = Math.max(allow_block_number, getPermissionValue(player, "limitsignquantity.block_amount."));
                if (block_num >= allow_block_number) {
                    event.setCancelled(true);
                    if (Objects.equals(message4, "")) return;
                    String msg4 = message4;
                    message4 = message4.replace("[name]", player.getName());
                    player.sendMessage(message4);
                    message4 = msg4;
                    return;
                }
            }
            if (allow_number != -1 || !player.hasPermission("limitsignquantity.amount")) {
                int num = getPlayerSignCount(uuid);
                int allowed_number = Math.max(allow_number, getPermissionValue(player, "limitsignquantity.amount."));
                if (num >= allowed_number) {
                    event.setCancelled(true);
                    if (Objects.equals(message1, "")) return;
                    String msg1 = message1;
                    message1 = message1.replace("[name]", player.getName());
                    player.sendMessage(message1);
                    message1 = msg1;
                    return;
                } else {
                    if (Objects.equals(message2, "")) return;
                    String msg2 = message2;
                    message2 = message2.replace("[name]", player.getName());
                    message2 = message2.replace("[number1]", Integer.toString(num - 1));
                    message2 = message2.replace("[number2]", Integer.toString(allowed_number - num - 1));
                    player.sendMessage(message2);
                    message2 = msg2;
                    //saveSignData((Sign) block.getState(), player.getUniqueId().toString(), player.getWorld().getName());
                }
            }
            saveSignData((Sign) block.getState(), player.getUniqueId().toString(), player.getWorld().getName());
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
            if (!(player.isOp() || (player.hasPermission("limitsignquantity.amount") && player.hasPermission("limitsignquantity.block_amount")))){
                sendMessage(sign);// 向非op或无免判断权限的玩家发送消息
            }
            deleteSignData(sign.getLocation());
        }
    }


    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {//燃烧破坏告示牌
        Block block = event.getBlock();
        if (block.getState() instanceof Sign) {
            Sign sign = (Sign) block.getState();
            sendMessage(sign);
            deleteSignData(sign.getLocation());
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
                deleteSignData(sign.getLocation());
            }
        }
    }


    private void sendMessage(Sign sign) { //当告示牌被破坏时，向所属者发送消息
        UUID uuid = UUID.fromString(getSignOwnerUUID(sign));
        Player player = Bukkit.getPlayer(uuid);
        if (player.isOp() || player.hasPermission("limitsignquantity.amount") || allow_number == -1) {
            return;
        }
        if (!player.isOnline()) return;
        if (Objects.equals(message3, "")) return;
        String msg3 = message3;
        message3 = message3.replace("[name]", player.getName());
        int number = getPlayerSignCount(uuid.toString());
        message3 = message3.replace("[number2]", Objects.toString(Math.max(allow_number - number + 1, 0)));
        player.sendMessage(message3);
        message3 = msg3;
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
