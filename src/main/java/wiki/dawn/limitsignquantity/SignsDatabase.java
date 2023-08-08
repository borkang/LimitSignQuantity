package wiki.dawn.limitsignquantity;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SignsDatabase {

    private final LimitSignQuantity plugin;
    private Connection connection;
    private static final Logger logger = Logger.getLogger("LimitSignQuantity");

    public SignsDatabase(LimitSignQuantity plugin) {
        this.plugin = plugin;
        setupLogger();
    }

    public Logger getServerLogger() {
        return plugin.getLogger();
    }

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

    public void connectToDatabase() {
        try {
            // 创建SQLite数据库文件
            File dbFile = new File(plugin.getDataFolder(), "signs.db");
            if (!dbFile.exists()) {
                dbFile.getParentFile().mkdirs();
                dbFile.createNewFile();
            }

            // 连接到SQLite数据库
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            getServerLogger().info("成功连接到SQLite数据库！");

            // 检查并创建 "signs" 表
            if (!isSignsTableExists()) {
                createSignsTable();
            }
        } catch (SQLException | IOException e) {
            logger.log(Level.SEVERE, "无法连接到SQLite数据库！", e);
            getServerLogger().warning("无法连接到SQLite数据库！具体错误信息已记录到插件目录下的日志文件");
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
            getServerLogger().warning("检查表是否存在时出现错误！具体错误信息已记录到插件目录下的日志文件");
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
            getServerLogger().warning("创建 \"signs\" 表时出现错误！具体错误信息已记录到插件目录下的日志文件");
        }
    }

    public void disconnectFromDatabase() {
        try {
            // 断开数据库连接
            if (connection != null) {
                connection.close();
                getServerLogger().info("已断开与SQLite数据库的连接！");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "无法断开与SQLite数据库的连接！", e);
            getServerLogger().warning("无法断开与SQLite数据库的连接！具体错误信息已记录到插件目录下的日志文件");
        }
    }

    public int getSignCountInBlock(int blockX, int blockZ, String world, String uuid) {
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
            getServerLogger().warning("查询告示牌数量时出现错误！具体错误信息已记录到插件目录下的日志文件");
        }
        return 0;
    }

    public int getPlayerSignCount(String playerUUID) {

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
            getServerLogger().warning("查询玩家所属告示牌数量时出现错误！具体错误信息已记录到插件目录下的日志文件");
        }
        return 0;
    }

    public void saveSignData(Sign sign, String playerUUID, String world) {
        // 检查数据库连接是否有效
        if (connection == null) {
            getServerLogger().warning("无法保存告示牌数据，数据库连接未初始化或已断开！");
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
            getServerLogger().warning("保存告示牌数据时出现错误！具体错误信息已记录到插件目录下的日志文件");
        }
    }

    public void deleteSignData(Location signLocation) {
        // 检查数据库连接是否有效
        if (connection == null) {
            getServerLogger().warning("无法删除告示牌数据，数据库连接未初始化或已断开！");
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
            getServerLogger().warning("删除告示牌数据时出现错误！具体错误信息已记录到插件目录下的日志文件");
        }
    }

    public String getSignOwnerUUID(Sign sign) {
        // 获取告示牌的坐标
        Location signLocation = sign.getLocation();


        // 检查数据库连接是否有效
        if (connection == null) {
            getServerLogger().warning("无法获取告示牌所有者名称，数据库连接未初始化或已断开！");
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
            getServerLogger().warning("获取告示牌所有者名称时出现错误！具体错误信息已记录到插件目录下的日志文件");
        }

        return "";
    }

    public List<Location> getSignLocationsByPlayerUUID(String uuid) {
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
            getServerLogger().warning("获取告示牌位置信息时出现错误！具体错误信息已记录到插件目录下的日志文件");
            // 处理异常
        }

        return signLocations;
    }

    public void check(String uuid) {
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


}
