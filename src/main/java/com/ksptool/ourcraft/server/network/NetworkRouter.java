package com.ksptool.ourcraft.server.network;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 网络事件总线
 * 负责将网络包分发给对应的监听器
 * 运行在每个玩家的 VT3 (处理线程) 中
 */
@Slf4j
public class NetworkRouter {

    //定义 RPC 处理器接口
    @FunctionalInterface
    public interface RpcPacketHandler<T> {
        void handle(NetworkSession session, T packet, long rpcId);
    }

    // 存储 数据包类型 -> 处理器列表 的映射
    // BiConsumer<Session, Packet> 让处理器能同时访问会话和数据包
    private final Map<Class<?>, List<BiConsumer<NetworkSession, ?>>> listeners = new HashMap<>();

    // RPC 处理器
    // 使用 BiFunction，需要返回值
    private final Map<Class<?>, RpcPacketHandler<?>> rpcHandlers = new HashMap<>();

    /**
     * 注册监听器
     * @param packetClass 数据包的类对象
     * @param listener 处理器
     * @param <T> 数据包类型
     */
    public <T> void subscribe(Class<T> packetClass, BiConsumer<NetworkSession, T> listener) {
        listeners.computeIfAbsent(packetClass, k -> new ArrayList<>()).add(listener);
    }

    /**
     * 注册 RPC 处理器 (完全接管模式)
     * 处理器必须在内部手动调用 session.sendRpcResponse
     */
    public <T> void subscribeRpc(Class<T> packetClass, RpcPacketHandler<T> handler) {
        if (rpcHandlers.containsKey(packetClass)) {
            log.warn("RPC处理器覆盖警告: {} 的处理器被替换", packetClass.getSimpleName());
        }
        rpcHandlers.put(packetClass, handler);
    }

    /**
     * 发布网络事件（分发数据包）
     * @param session 来源会话
     * @param packet 数据包对象
     */
    @SuppressWarnings("unchecked")
    public void post(NetworkSession session, Object packet) {
        List<BiConsumer<NetworkSession, ?>> handlers = listeners.get(packet.getClass());

        if (handlers != null) {
            for (BiConsumer<NetworkSession, ?> handler : handlers) {
                try {
                    // 强制类型转换是安全的，因为 subscribe 时保证了类型匹配
                    ((BiConsumer<NetworkSession, Object>) handler).accept(session, packet);
                } catch (Exception e) {
                    log.error("处理网络包 {} 时发生异常", packet.getClass().getSimpleName(), e);
                }
            }
            return;
        }
        // log.warn("收到未处理的数据包: {}", packet.getClass().getSimpleName());
    }

    /**
     * 分发 RPC 请求
     * 注意：不再返回结果对象，处理器负责发送响应
     */
    @SuppressWarnings("unchecked")
    public void postRpc(NetworkSession session, Object packet, long rpcId) {
        // 查找 RPC 处理器
        RpcPacketHandler<Object> handler = (RpcPacketHandler<Object>) rpcHandlers.get(packet.getClass());

        if (handler != null) {
            try {
                // 执行逻辑，传入 rpcId
                handler.handle(session, packet, rpcId);
            } catch (Exception e) {
                log.error("执行RPC处理逻辑 {} 时发生异常", packet.getClass().getSimpleName(), e);
                // 发生异常时，建议发送一个错误响应，防止客户端永久挂起
                // session.sendRpcError(rpcId, e.getMessage());
            }
        } else {
            log.warn("未找到 {} 的RPC处理器，请求被忽略", packet.getClass().getSimpleName());
        }
    }
}