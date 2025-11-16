package com.ksptool.mycraft.world.save;

import com.ksptool.mycraft.server.entity.ServerEntity;
import com.ksptool.mycraft.server.entity.ServerLivingEntity;
import com.ksptool.mycraft.server.entity.ServerPlayer;
import com.ksptool.mycraft.sharedcore.item.Item;
import com.ksptool.mycraft.sharedcore.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * 实体序列化器，使用自定义的二进制 KV 格式序列化/反序列化实体
 * KV 格式: [Key长度 (short)][Key字符串 (UTF-8)][Value类型 (byte)][Value数据 (byte[])]
 */
public class EntitySerializer {
    private static final byte TYPE_FLOAT = 0x01;
    private static final byte TYPE_STRING = 0x02;
    private static final byte TYPE_UUID = 0x03;
    private static final byte TYPE_INT = 0x04;
    private static final byte TYPE_BOOLEAN = 0x05;
    
    public static byte[] serialize(List<ServerEntity> entities) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        dos.writeInt(entities.size());
        
        for (ServerEntity entity : entities) {
            Map<String, Object> kvMap = flattenEntity(entity);
            byte[] entityData = serializeKvMap(kvMap);
            dos.writeInt(entityData.length);
            dos.write(entityData);
        }
        
        byte[] uncompressed = baos.toByteArray();
        
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(uncompressed);
        deflater.finish();
        
        ByteArrayOutputStream compressedBaos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            compressedBaos.write(buffer, 0, count);
        }
        deflater.end();
        
        return compressedBaos.toByteArray();
    }
    
    public static List<ServerEntity> deserialize(byte[] compressedData, com.ksptool.mycraft.server.world.ServerWorld world) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressedData);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                baos.write(buffer, 0, count);
            }
        } catch (DataFormatException e) {
            throw new IOException("解压实体数据失败: " + e.getMessage(), e);
        } finally {
            inflater.end();
        }
        
        byte[] uncompressed = baos.toByteArray();
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(uncompressed));
        
        int entityCount = dis.readInt();
        List<ServerEntity> entities = new ArrayList<>();
        
        for (int i = 0; i < entityCount; i++) {
            int entityDataLength = dis.readInt();
            byte[] entityData = new byte[entityDataLength];
            dis.readFully(entityData);
            
            Map<String, Object> kvMap = deserializeKvMap(entityData);
            ServerEntity entity = reconstructEntity(kvMap, world);
            if (entity != null) {
                entities.add(entity);
            }
        }
        
        return entities;
    }
    
    private static Map<String, Object> flattenEntity(ServerEntity entity) {
        Map<String, Object> kvMap = new HashMap<>();
        
        kvMap.put("core:uuid", entity.getUniqueId());
        kvMap.put("core:pos.x", entity.getPosition().x);
        kvMap.put("core:pos.y", entity.getPosition().y);
        kvMap.put("core:pos.z", entity.getPosition().z);
        kvMap.put("core:vel.x", entity.getVelocity().x);
        kvMap.put("core:vel.y", entity.getVelocity().y);
        kvMap.put("core:vel.z", entity.getVelocity().z);
        kvMap.put("core:onGround", entity.isOnGround());
        kvMap.put("core:isDead", entity.isDead());
        
        if (entity instanceof ServerPlayer) {
            kvMap.put("core:type", "mycraft:player");
            ServerPlayer player = (ServerPlayer) entity;
            kvMap.put("player:yaw", player.getYaw());
            kvMap.put("player:pitch", player.getPitch());
            kvMap.put("player:selectedSlot", player.getInventory().getSelectedSlot());
            
            com.ksptool.mycraft.sharedcore.item.ItemStack[] hotbar = player.getInventory().getHotbar();
            for (int i = 0; i < hotbar.length; i++) {
                if (hotbar[i] != null && !hotbar[i].isEmpty()) {
                    kvMap.put("player:hotbar." + i + ".itemId", hotbar[i].getItem().getId());
                    kvMap.put("player:hotbar." + i + ".count", hotbar[i].getCount());
                }
            }
        } else if (entity instanceof ServerLivingEntity) {
            kvMap.put("core:type", "mycraft:living");
            ServerLivingEntity living = (ServerLivingEntity) entity;
            kvMap.put("living:health", living.getHealth());
            kvMap.put("living:eyeHeight", living.getEyeHeight());
        } else {
            kvMap.put("core:type", "mycraft:entity");
        }
        
        return kvMap;
    }
    
    private static ServerEntity reconstructEntity(Map<String, Object> kvMap, com.ksptool.mycraft.server.world.ServerWorld world) {
        Object uuidObj = kvMap.get("core:uuid");
        if (uuidObj == null) {
            return null;
        }
        
        UUID uuid;
        if (uuidObj instanceof UUID) {
            uuid = (UUID) uuidObj;
        } else if (uuidObj instanceof String) {
            uuid = UUID.fromString((String) uuidObj);
        } else {
            return null;
        }
        
        String entityType = (String) kvMap.get("core:type");
        if (entityType == null) {
            return null;
        }
        
        ServerEntity entity;
        if ("mycraft:player".equals(entityType)) {
            entity = new ServerPlayer(world, uuid);
        } else {
            return null;
        }
        
        Float posX = getFloat(kvMap, "core:pos.x");
        Float posY = getFloat(kvMap, "core:pos.y");
        Float posZ = getFloat(kvMap, "core:pos.z");
        if (posX != null && posY != null && posZ != null) {
            entity.getPosition().set(posX, posY, posZ);
        }
        
        Float velX = getFloat(kvMap, "core:vel.x");
        Float velY = getFloat(kvMap, "core:vel.y");
        Float velZ = getFloat(kvMap, "core:vel.z");
        if (velX != null && velY != null && velZ != null) {
            entity.getVelocity().set(velX, velY, velZ);
        }
        
        Boolean onGround = getBoolean(kvMap, "core:onGround");
        if (onGround != null) {
            entity.setOnGround(onGround);
        }
        
        Boolean isDead = getBoolean(kvMap, "core:isDead");
        if (isDead != null) {
            entity.setDead(isDead);
        }
        
        if (entity instanceof ServerLivingEntity) {
            ServerLivingEntity living = (ServerLivingEntity) entity;
            Float health = getFloat(kvMap, "living:health");
            if (health != null) {
                living.setHealth(health);
            }
            Float eyeHeight = getFloat(kvMap, "living:eyeHeight");
            if (eyeHeight != null) {
                living.setEyeHeight(eyeHeight);
            }
        }
        
        if (entity instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) entity;
            Float yaw = getFloat(kvMap, "player:yaw");
            Float pitch = getFloat(kvMap, "player:pitch");
            if (yaw != null) {
                player.setYaw(yaw);
            }
            if (pitch != null) {
                player.setPitch(pitch);
            }
            
            Integer selectedSlot = getInt(kvMap, "player:selectedSlot");
            if (selectedSlot != null) {
                player.getInventory().setSelectedSlot(selectedSlot);
            }
            
            com.ksptool.mycraft.sharedcore.item.ItemStack[] hotbar = player.getInventory().getHotbar();
            for (int i = 0; i < hotbar.length; i++) {
                Integer itemId = getInt(kvMap, "player:hotbar." + i + ".itemId");
                Integer count = getInt(kvMap, "player:hotbar." + i + ".count");
                if (itemId != null && count != null) {
                    Item item = Item.getItem(itemId);
                    if (item != null) {
                        hotbar[i] = new com.ksptool.mycraft.sharedcore.item.ItemStack(item, count);
                    }
                }
            }
        }
        
        return entity;
    }
    
    private static byte[] serializeKvMap(Map<String, Object> kvMap) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        dos.writeInt(kvMap.size());
        
        for (Map.Entry<String, Object> entry : kvMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            dos.writeShort(keyBytes.length);
            dos.write(keyBytes);
            
            if (value instanceof Float) {
                dos.writeByte(TYPE_FLOAT);
                dos.writeFloat((Float) value);
            } else if (value instanceof String) {
                dos.writeByte(TYPE_STRING);
                byte[] strBytes = ((String) value).getBytes(StandardCharsets.UTF_8);
                dos.writeInt(strBytes.length);
                dos.write(strBytes);
            } else if (value instanceof UUID) {
                dos.writeByte(TYPE_UUID);
                dos.writeLong(((UUID) value).getMostSignificantBits());
                dos.writeLong(((UUID) value).getLeastSignificantBits());
            } else if (value instanceof Integer) {
                dos.writeByte(TYPE_INT);
                dos.writeInt((Integer) value);
            } else if (value instanceof Boolean) {
                dos.writeByte(TYPE_BOOLEAN);
                dos.writeBoolean((Boolean) value);
            }
        }
        
        return baos.toByteArray();
    }
    
    private static Map<String, Object> deserializeKvMap(byte[] data) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        Map<String, Object> kvMap = new HashMap<>();
        
        int count = dis.readInt();
        
        for (int i = 0; i < count; i++) {
            int keyLength = dis.readShort() & 0xFFFF;
            byte[] keyBytes = new byte[keyLength];
            dis.readFully(keyBytes);
            String key = new String(keyBytes, StandardCharsets.UTF_8);
            
            byte type = dis.readByte();
            Object value = null;
            
            if (type == TYPE_FLOAT) {
                value = dis.readFloat();
            } else if (type == TYPE_STRING) {
                int strLength = dis.readInt();
                byte[] strBytes = new byte[strLength];
                dis.readFully(strBytes);
                value = new String(strBytes, StandardCharsets.UTF_8);
            } else if (type == TYPE_UUID) {
                long mostSig = dis.readLong();
                long leastSig = dis.readLong();
                value = new UUID(mostSig, leastSig);
            } else if (type == TYPE_INT) {
                value = dis.readInt();
            } else if (type == TYPE_BOOLEAN) {
                value = dis.readBoolean();
            }
            
            if (value != null) {
                kvMap.put(key, value);
            }
        }
        
        return kvMap;
    }
    
    private static Float getFloat(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Float) {
            return (Float) value;
        }
        if (value instanceof Double) {
            return ((Double) value).floatValue();
        }
        return null;
    }
    
    private static Integer getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return null;
    }
    
    private static Boolean getBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return null;
    }
}

