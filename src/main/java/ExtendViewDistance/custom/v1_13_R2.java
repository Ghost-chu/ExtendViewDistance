package ExtendViewDistance.custom;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.AttributeKey;
import net.minecraft.server.v1_13_R2.*;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_13_R2.CraftChunk;
import org.bukkit.craftbukkit.v1_13_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_13_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class v1_13_R2 implements Extend {


    /** 取得玩家類別 */
    private EntityPlayer getNMSPlayer(Player player) {
        return ((CraftPlayer) player).getHandle();
    }
    /** 取得世界類別 */
    private WorldServer getNMSWorld(World world) {
        return ((CraftWorld) world).getHandle();
    }
    /** 取得世界類別 */
    private net.minecraft.server.v1_13_R2.Chunk getNMSChunk(Chunk chunk) {
        return ((CraftChunk) chunk).getHandle();
    }


    /** 發送封包 */
    private void playerSendPacket(Player player, Packet packet) {
        synchronized (getNMSPlayer(player).playerConnection.networkManager) {
            NetworkManager              networkManager  = getNMSPlayer(player).playerConnection.networkManager;   // 玩家連線
            Channel                     channel         = networkManager.channel;                                 // 取得連線通道
            AttributeKey<EnumProtocol>  enumProtocols   = AttributeKey.valueOf("protocol");                       // 取得所有協議協定類型
            EnumProtocol                enumprotocol    = EnumProtocol.a(packet);
            EnumProtocol                enumprotocol1   = channel.attr(enumProtocols).get();

            if (channel.eventLoop().inEventLoop()) {
                if (enumprotocol != enumprotocol1) {
                    networkManager.setProtocol(enumprotocol);
                }

                ChannelFuture channelfuture = channel.write(packet);

                channelfuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            } else {
                channel.eventLoop().execute(() -> {
                    if (enumprotocol != enumprotocol1) {
                        networkManager.setProtocol(enumprotocol);
                    }

                    ChannelFuture channelfuture1 = channel.write(packet);

                    channelfuture1.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                });
            }
        }
    }


    /** 取得區塊 */
    public Chunk getChunk(World world, int x, int z) {

        synchronized (getNMSWorld(world)) {
            net.minecraft.server.v1_13_R2.Chunk chunk = getNMSWorld(world).getChunkProvider().getChunkAt(x, z, true, true);
            return chunk != null ? chunk.bukkitChunk : null;
        }
    }


    /** 發送視野距離 */
    @Deprecated
    public void playerSendViewDistance(Player player, int distance) {

        //playerSendPacket(player, new PacketPlayOut(distance)); // 發送視野距離
    }


    /** 發送區塊 */
    public void playerSendChunk(Player player, Chunk chunk) {

        // 65535 + 1 = 65536 = 16 * 256 * 16
        playerSendPacket(player, new PacketPlayOutMapChunk(getNMSChunk(chunk), 65535)); // 發送區塊
    }


    /** 發送區塊卸除 */
    public void playerSendUnloadChunk(Player player, int x, int z) {

        playerSendPacket(player, new PacketPlayOutUnloadChunk(x, z)); // 發送區塊卸除
    }


    /** 發送光照更新 */
    @Deprecated
    public void sendChunkLightUpdate(Player player, Chunk chunk) {

    }


}
