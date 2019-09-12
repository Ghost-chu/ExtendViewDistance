package ExtendViewDistance;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 玩家顯示更遠的區塊迴圈
 * 與 CreativeCat.server.run.RunSendDistantChunk 相同
 */
public class Loop {


    public  static int                    tick                         = 0;
    private static int                    serverViewDistance           = Bukkit.getViewDistance();          // 取得伺服器視野距離
    private static int                    extendViewDistance           = Value.extendViewDistance;          // 最高可擴展的視野距離
    private static int                    tickSendChunkAmount          = Value.tickSendChunkAmount;         // 每個tick可以讀取多少區塊
    private static int                    playerTickSendChunkAmount    = Value.playerTickSendChunkAmount;   // 每個tick能發送給每個玩家多少個封包
    private static boolean                playerOutChunkSendUnload     = Value.playerOutChunkSendUnload;    // 玩家離區塊太遠,是唪發送區塊卸除請求給玩家(主要適用於客戶端性能)
    private static int                    tickChunkExamine             = Value.tickChunkExamine;            // 多少次tick進行1次區塊權重重新檢查
    private static int                    tickChunkSend                = Value.tickChunkSend;               // 多少次tick進行1次區塊發送
    private static boolean                isRun                        = false;                             // 正在運行中
    public  static Map<Player, Order>     priorityOrder                = new HashMap<>();                   // 擁有優先權重的緩




    /**
     * 表示一個等待加載中的區塊請求
     */
    private static class WaitingChunk {

        public int         x;
        public int         z;
        public boolean     isOk = false;  // 預設等待區塊載入

        WaitingChunk(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }


    /**
     * 優先續控制庫
     */
    private static class Order {

        Player                                  player;
        World world;
        Integer                                 playerX         = null; // 當前玩家區塊座標X
        Integer                                 playerZ         = null; // 當前玩家區塊座標Z
        Map<Long, WaitingChunk>                 chunkKey        = new HashMap<>();
        Map<Integer, ArrayList<WaitingChunk>>   chunkPriority   = new HashMap<>();


        Order(Player player) {
            if (player == null) throw new NullPointerException();
            this.player = player;
            move(player); // 初始化
        }











        public void move(Player player) {
            move(player.getWorld(), player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4); // 初始化
        }
        /**
         * 玩家移動座標
         * @param moveX 移動到的新區塊位置X
         * @param moveZ 移動到的新區塊位置Z
         */
        public void move(World nowWorld, int moveX, int moveZ) {

            if (playerX != null && playerZ != null) {
                if (playerX == moveX && playerZ == moveZ) {
                    return; // 沒有改變
                }
            }
            if (world != null) {
                if (!Bukkit.getWorlds().contains(world)) {
                    // 世界已經被卸除
                    chunkKey.clear();
                    chunkPriority.clear();
                    this.world = nowWorld;
                } else if (world != nowWorld) {
                    // 不同世界
                    chunkKey.clear();
                    chunkPriority.clear();
                    this.world = nowWorld;
                }
            } else {
                this.world = nowWorld;
            }



            this.playerX = moveX; // 更新座標X
            this.playerZ = moveZ; // 更新座標Z


            int viewDistance = player.getClientViewDistance(); // 取得客戶端視野距離
            if (viewDistance > extendViewDistance) viewDistance = extendViewDistance;



            // 是否有超過範圍的區塊
            Object[] keys = chunkKey.keySet().toArray();
            for (int i = 0 ; chunkKey.size() - 1 - i >= 0 ; ++i) {
                long key = (long) keys[i];
                int  x   = (int) key;
                int  z   = (int) (key >> 32);


                if (x >= playerX - serverViewDistance - 1 && x <= playerX + serverViewDistance + 1 && z >= playerZ - serverViewDistance - 1 && z <= playerZ + serverViewDistance + 1) {
                    // 與原本的視野距離相撞
                    chunkKey.remove( key );

                } else if (x < playerX - viewDistance || x > playerX + viewDistance || z < playerZ - viewDistance || z > playerZ + viewDistance) {
                    // 超出擴展的視野範圍
                    chunkKey.remove( key );
                    if (playerOutChunkSendUnload) {
                        try {
                            Value.extend.playerSendUnloadChunk(player, x, z); // 請求卸除區塊
                        } catch (Exception ex) {
                            // 不報錯誤
                        }
                    }

                }
            }


            // 重新計算方塊區塊距離權重
            chunkPriority.clear();
            for (int x = playerX - viewDistance; x <= playerX + viewDistance; ++x) {
                for (int z = playerZ - viewDistance; z <= playerZ + viewDistance; ++z) {

                    long key = Chunk.getChunkKey(x, z);  // 計算方塊key


                    if (x >= playerX - serverViewDistance - 1 && x <= playerX + serverViewDistance + 1 && z >= playerZ - serverViewDistance - 1 && z <= playerZ + serverViewDistance + 1) {
                        // 與原本的視野距離相撞
                        chunkKey.remove( key );
                        continue;
                    }


                    WaitingChunk waitingChunk = chunkKey.get( key );        // 先從緩存請求


                    if (waitingChunk == null) {
                        // 還沒有緩存
                        waitingChunk = new WaitingChunk(x, z);
                        chunkKey.put(key, waitingChunk);

                    } else {
                        // 存在緩存
                        if (waitingChunk.isOk) {
                            continue; // 已經完成
                        }
                    }


                    // 計算距離權重
                    int distance = Math.abs(playerX - x) + Math.abs(playerZ - z);
                    // 初始化
                    if (!chunkPriority.containsKey(distance)) {
                        chunkPriority.put(distance, new ArrayList<>());
                    }


                    chunkPriority.get(distance).add( waitingChunk );
                }
            }
        }


        /**
         * 取得最優先的糗求
         * @return 糗求
         */
        public WaitingChunk get() {

            // 從最優先開始取得
            if (chunkPriority != null) {
                for (Integer distance : new TreeMap<>(chunkPriority).navigableKeySet()) {

                    ArrayList<WaitingChunk> prioritys = chunkPriority.get(distance);
                    for (int i = 0 ; prioritys.size() - 1 - i >= 0 ; ++i) {
                        WaitingChunk waitingChunk = prioritys.get(i);

                        if (waitingChunk.isOk) {
                            prioritys.remove(waitingChunk);
                            continue; // 已經完成
                        }

                        prioritys.remove( waitingChunk );
                        return waitingChunk;
                    }
                }
            }
            return null;
        }


        public void clear() {
            chunkKey.clear();
            chunkPriority.clear();
        }
    }




    public static void run() {
    }


    public static void runAsync() {

        //if (Value.tickThreadTiem > Value.lagLight) return; // 已經輕微卡頓



        if (isRun) return;
        isRun = true;
        tick++;


        try {
            //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // 加載區塊請求
            // 每 5 tick 運行一次


            if (tick % tickChunkExamine == 0) {

                Map<Player, Integer> playerTickSend = new HashMap<>();                          // 避免在同個tick發送太多區塊給玩家
                int                  tickSend       = tickSendChunkAmount * tickChunkExamine;   // 每個tick允許發送多少區塊
                Object[]             players        = priorityOrder.keySet().toArray();
                int                  repeat         = players.length * tickChunkExamine;


                for (int r = 0 ; r < repeat ; ++r) {
                    if (tickSend < 0) break; // 等待下一個tick在運行這裡

                    Player       player = (Player)  players[ (int) (Math.random() * players.length) ];
                    Integer      tickSendAmount = playerTickSend.get(player);
                    if (tickSendAmount == null ) tickSendAmount = 0;
                    if (tickSendAmount >= playerTickSendChunkAmount * tickChunkExamine) continue; // 同個tick發送太多區塊給玩家了

                    Order        order = priorityOrder.get(player);
                    if (order == null) continue;

                    WaitingChunk waiting = order.get();
                    if (waiting == null) continue;

                    World   world           = player.getWorld();
                    int     chunkX          = waiting.x;
                    int     chunkZ          = waiting.z;
                    int     viewDistance    = player.getClientViewDistance();
                    if (viewDistance > extendViewDistance) viewDistance = extendViewDistance;


                    // 當前狀態
                    if (!waiting.isOk) {
                        Chunk chunk =Value.extend.getChunk(world, chunkX, chunkZ); // 取得方塊
                        boolean noException = true; // 如果沒有錯誤

                        if (chunk != null) {
                            try {
                                Value.extend.playerSendViewDistance(player, viewDistance);  // 更新玩家應該要顯示的視野距離
                                Value.extend.playerSendChunk(player, chunk);                // 發送區塊給玩家
                            } catch (Exception ex) {
                                // 不報錯誤
                                noException = false;
                            }
                        }

                        if (noException) {
                            waiting.isOk = true;
                            tickSend--;
                            playerTickSend.put(player, tickSendAmount + 1);
                        }
                    }
                }
            }


            //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // 處理玩家應該要加載的區塊


            if (tick % tickChunkSend == 0) {
                // 循環遍布所有線上玩家
                // 並計算附近需要等待發送給玩家的區塊
                for (Player player : Bukkit.getOnlinePlayers()) {
                    /// 加入到緩存中
                    if (!priorityOrder.containsKey(player)) {
                        priorityOrder.put(player, new Order(player));
                    } else {
                        priorityOrder.get(player).move(player);
                    }
                }
            }


            //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


        } catch (Exception ex) {
            ex.printStackTrace();
        }


        isRun = false;
    }


}
