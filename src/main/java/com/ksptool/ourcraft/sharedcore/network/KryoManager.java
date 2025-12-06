package com.ksptool.ourcraft.sharedcore.network;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.DefaultSerializers;
import com.ksptool.ourcraft.server.archive.model.GlobalPaletteProperty;
import com.ksptool.ourcraft.sharedcore.network.nvo.HuChunkUnloadNVo;
import com.ksptool.ourcraft.sharedcore.network.packets.*;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * Kryo序列化管理器，负责所有网络数据包的序列化和反序列化
 * 使用ThreadLocal确保线程安全
 */
@Slf4j
public class KryoManager {
    
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(true);
        
        // 为JDK内置类配置安全的序列化器（按字母顺序）
        kryo.addDefaultSerializer(Boolean.class, new DefaultSerializers.BooleanSerializer());
        kryo.addDefaultSerializer(Byte.class, new DefaultSerializers.ByteSerializer());
        kryo.addDefaultSerializer(Character.class, new DefaultSerializers.CharSerializer());
        kryo.addDefaultSerializer(Double.class, new DefaultSerializers.DoubleSerializer());
        kryo.addDefaultSerializer(Float.class, new DefaultSerializers.FloatSerializer());
        kryo.addDefaultSerializer(Integer.class, new DefaultSerializers.IntSerializer());
        kryo.addDefaultSerializer(Long.class, new DefaultSerializers.LongSerializer());
        kryo.addDefaultSerializer(Short.class, new DefaultSerializers.ShortSerializer());
        kryo.addDefaultSerializer(String.class, new DefaultSerializers.StringSerializer());
        kryo.addDefaultSerializer(int.class, new DefaultSerializers.IntSerializer());
        kryo.addDefaultSerializer(long.class, new DefaultSerializers.LongSerializer());
        kryo.addDefaultSerializer(double.class, new DefaultSerializers.DoubleSerializer());
        kryo.addDefaultSerializer(float.class, new DefaultSerializers.FloatSerializer());
        kryo.addDefaultSerializer(boolean.class, new DefaultSerializers.BooleanSerializer());
        kryo.addDefaultSerializer(short.class, new DefaultSerializers.ShortSerializer());
        kryo.addDefaultSerializer(byte.class, new DefaultSerializers.ByteSerializer());
        kryo.addDefaultSerializer(char.class, new DefaultSerializers.CharSerializer());
        
        
        registerAllPackets(kryo);
        return kryo;
    });
    
    /**
     * 注册所有数据包类
     * 注意：注册顺序必须与服务端和客户端完全一致！
     */
    private static void registerAllPackets(Kryo kryo) {
        int id = 0;
        
        // 阶段I: 认证数据包
        kryo.register(GetServerStatusNDto.class, id++);
        kryo.register(RequestJoinServerNDto.class, id++);
        kryo.register(ClientReadyNDto.class, id++);
        kryo.register(GetServerStatusNVo.class, id++);
        kryo.register(RequestJoinServerNVo.class, id++);
        kryo.register(ServerDisconnectNVo.class, id++);
        
        // 阶段II: 连接维护数据包
        kryo.register(ClientKeepAliveNPkg.class, id++);
        kryo.register(ServerKeepAliveNPkg.class, id++);
        
        // 阶段III: 世界同步数据包
        kryo.register(PlayerDcparNDto.class, id++);
        kryo.register(PlayerDshsNdto.class, id++);
        kryo.register(PlayerDActionNDto.class, id++);
        kryo.register(PlayerInputStateNDto.class, id++);
        kryo.register(ServerSyncChunkDataNVo.class, id++);
        kryo.register(HuChunkUnloadNVo.class, id++);
        kryo.register(ServerSyncBlockUpdateNVo.class, id++);
        kryo.register(ServerSyncEntityPositionAndRotationNVo.class, id++);
        kryo.register(ServerSyncPlayerStateNVo.class, id++);
        kryo.register(ServerSyncWorldTimeNVo.class, id++);
        
        // 枚举类型
        kryo.register(ActionType.class, id++);
        
        // Java包装类型（按字母顺序注册，用于record中的包装类型字段）
        kryo.register(Boolean.class, id++);
        kryo.register(Byte.class, id++);
        kryo.register(Character.class, id++);
        kryo.register(Double.class, id++);
        kryo.register(Float.class, id++);
        kryo.register(Integer.class, id++);
        kryo.register(Long.class, id++);
        kryo.register(Short.class, id++);
        kryo.register(String.class, id++);
        
        // Java数组类型（按字母顺序注册）
        kryo.register(boolean[].class, id++);
        kryo.register(byte[].class, id++);
        kryo.register(char[].class, id++);
        kryo.register(double[].class, id++);
        kryo.register(float[].class, id++);
        kryo.register(int[].class, id++);
        kryo.register(long[].class, id++);
        kryo.register(short[].class, id++);

        kryo.register(boolean.class, id++);
        kryo.register(byte.class, id++);
        kryo.register(char.class, id++);
        kryo.register(double.class, id++);
        kryo.register(float.class, id++);
        kryo.register(int.class, id++);
        kryo.register(long.class, id++);
        kryo.register(short.class, id++);

        //注册全局调色板
        kryo.register(GlobalPaletteProperty.class, id++);
        kryo.register(ArrayList.class, id++);

        log.debug("KryoManager: 已注册{}个类，ID范围: 0-{}", id, id - 1);
    }
    
    /**
     * 获取当前线程的Kryo实例
     */
    private static Kryo getKryo() {
        return kryoThreadLocal.get();
    }
    
    /**
     * 序列化对象并写入输出流（带长度前缀）
     * 
     * @param object 要序列化的对象
     * @param outputStream 输出流
     * @throws IOException 如果写入失败
     */
    public static void writeObject(Object object, OutputStream outputStream) throws IOException {
        if (object == null) {
            log.warn("KryoManager: 尝试序列化null对象");
            return;
        }
        
        Kryo kryo = getKryo();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        
        try {
            // 使用writeClassAndObject确保类信息也被写入
            kryo.writeClassAndObject(output, object);
            output.flush();
            
            byte[] data = baos.toByteArray();
            int length = data.length;
            
            // 写入长度前缀（4字节，大端序）
            outputStream.write((length >>> 24) & 0xFF);
            outputStream.write((length >>> 16) & 0xFF);
            outputStream.write((length >>> 8) & 0xFF);
            outputStream.write(length & 0xFF);
            
            // 写入数据
            outputStream.write(data);
            outputStream.flush();
        } finally {
            output.close();
        }
    }
    
    /**
     * 从输入流读取并反序列化对象（带长度前缀）
     * 
     * @param inputStream 输入流
     * @param clazz 期望的对象类型
     * @return 反序列化后的对象
     * @throws IOException 如果读取失败
     */
    public static <T> T readObject(InputStream inputStream, Class<T> clazz) throws IOException {
        // 读取长度前缀（4字节，大端序）
        int byte1 = inputStream.read();
        int byte2 = inputStream.read();
        int byte3 = inputStream.read();
        int byte4 = inputStream.read();
        
        if (byte1 == -1 || byte2 == -1 || byte3 == -1 || byte4 == -1) {
            throw new IOException("流已关闭或到达末尾");
        }
        
        int length = (byte1 << 24) | (byte2 << 16) | (byte3 << 8) | byte4;
        
        if (length < 0 || length > 10 * 1024 * 1024) { // 最大10MB
            throw new IOException("数据包长度异常: " + length);
        }
        
        // 读取数据
        byte[] data = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int bytesRead = inputStream.read(data, totalRead, length - totalRead);
            if (bytesRead == -1) {
                throw new IOException("流在读取数据包时提前结束");
            }
            totalRead += bytesRead;
        }
        
        // 反序列化
        Kryo kryo = getKryo();
        Input input = new Input(new ByteArrayInputStream(data));
        
        try {
            return kryo.readObject(input, clazz);
        } finally {
            input.close();
        }
    }
    
    /**
     * 从输入流读取并反序列化对象（自动类型推断）
     * 注意：此方法需要知道对象类型，通常用于已知类型的场景
     * 
     * @param inputStream 输入流
     * @return 反序列化后的对象
     * @throws IOException 如果读取失败
     */
    public static Object readObject(InputStream inputStream) throws IOException {
        // 读取长度前缀
        int byte1 = inputStream.read();
        int byte2 = inputStream.read();
        int byte3 = inputStream.read();
        int byte4 = inputStream.read();
        
        if (byte1 == -1 || byte2 == -1 || byte3 == -1 || byte4 == -1) {
            throw new IOException("流已关闭或到达末尾");
        }
        
        int length = (byte1 << 24) | (byte2 << 16) | (byte3 << 8) | byte4;
        
        if (length < 0 || length > 10 * 1024 * 1024) {
            log.warn("KryoManager: 数据包长度异常: {}，可能是TCP粘包问题", length);
            throw new IOException("数据包长度异常: " + length);
        }
        
        // 读取数据
        byte[] data = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int bytesRead = inputStream.read(data, totalRead, length - totalRead);
            if (bytesRead == -1) {
                throw new IOException("流在读取数据包时提前结束，已读取: " + totalRead + "/" + length);
            }
            totalRead += bytesRead;
        }
        
        // 反序列化（使用Kryo的readClassAndObject）
        Kryo kryo = getKryo();
        Input input = new Input(new ByteArrayInputStream(data));
        
        try {
            Object result = kryo.readClassAndObject(input);
            if (result == null) {
                log.warn("KryoManager: 反序列化返回null，数据长度: {}，可能是数据包格式错误", length);
                // 打印前几个字节用于调试
                if (data.length > 0) {
                    StringBuilder hex = new StringBuilder();
                    for (int i = 0; i < Math.min(20, data.length); i++) {
                        hex.append(String.format("%02X ", data[i]));
                    }
                    log.warn("KryoManager: 数据包前20字节（十六进制）: {}", hex.toString());
                }
            }
            return result;
        } catch (com.esotericsoftware.kryo.KryoException e) {
            if (e.getMessage() != null && e.getMessage().contains("unregistered class ID")) {
                log.error("KryoManager: 遇到未注册的类ID，数据长度: {}，错误: {}", length, e.getMessage());
                log.error("这可能是因为客户端和服务端的KryoManager注册顺序不一致");
            }
            log.error("KryoManager: 反序列化失败，数据长度: {}", length, e);
            // 打印前几个字节用于调试
            if (data.length > 0) {
                StringBuilder hex = new StringBuilder();
                for (int i = 0; i < Math.min(20, data.length); i++) {
                    hex.append(String.format("%02X ", data[i]));
                }
                log.error("KryoManager: 数据包前20字节（十六进制）: {}", hex.toString());
            }
            throw new IOException("反序列化失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("KryoManager: 反序列化失败，数据长度: {}", length, e);
            throw new IOException("反序列化失败: " + e.getMessage(), e);
        } finally {
            input.close();
        }
    }
}

