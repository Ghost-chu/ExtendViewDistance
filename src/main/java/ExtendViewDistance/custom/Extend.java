package ExtendViewDistance.custom;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * 所有nms功能
 */
public interface Extend {
    /** 取得區塊 */
    Chunk getChunk(World world, int x, int z);
    /** 發送視野距離 */
    void playerSendViewDistance(Player player, int distance);
    /** 發送區塊 */
    void playerSendChunk(Player player, Chunk chunk);
    /** 發送區塊卸除 */
    void playerSendUnloadChunk(Player player, int x, int z);
    /** 發送光照更新 */
    void sendChunkLightUpdate(Player player, Chunk chunk);

    /**
     * 取得當前版本能使用的擴展
     * @return 擴展類別
     */
    static Extend getExtend() {

        String bukkitVersion = Bukkit.getBukkitVersion();

        if (bukkitVersion.matches("1\\.13\\.[0-9]*-R[0-9]*\\..*")) {
            // 1.13
            return new v1_13_R2();
        } else if (bukkitVersion.matches("1\\.14\\.[0-9]*-R[0-9]*\\..*")) {
            // 1.14
            return new v1_14_R1();
        }

        return null; // 不支持的版本
    }
}
