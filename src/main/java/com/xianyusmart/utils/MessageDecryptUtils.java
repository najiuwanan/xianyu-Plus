package com.xianyusmart.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 消息解密工具类
 * 参考Python的xianyu_utils.py中的decrypt方法
 */
@Slf4j
public class MessageDecryptUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解密WebSocket消息
     * 参考Python的decrypt方法
     * 
     * @param encryptedData Base64编码的加密数据
     * @return 解密后的JSON字符串
     */
    public static String decrypt(String encryptedData) {
        try {
            if (encryptedData == null || encryptedData.isEmpty()) {
                log.warn("加密数据为空");
                return null;
            }

            log.debug("原始加密数据: {}", encryptedData);
            log.debug("加密数据长度: {}", encryptedData.length());

            // 清理数据，移除可能的非ASCII字符
            String cleanedData = encryptedData;
            try {
                cleanedData.getBytes(StandardCharsets.US_ASCII);
            } catch (Exception e) {
                // 如果包含非ASCII字符，进行清理
                cleanedData = new String(encryptedData.getBytes(StandardCharsets.UTF_8), StandardCharsets.US_ASCII);
                log.debug("清理后的数据: {}", cleanedData);
            }

            // Base64解码
            byte[] decodedBytes;
            try {
                decodedBytes = Base64.getDecoder().decode(cleanedData);
                log.debug("Base64解码成功，字节长度: {}", decodedBytes.length);
            } catch (IllegalArgumentException e) {
                log.debug("Base64解码失败，尝试添加填充: {}", e.getMessage());
                // 如果解码失败，尝试添加填充
                int missingPadding = cleanedData.length() % 4;
                if (missingPadding > 0) {
                    cleanedData += "=".repeat(4 - missingPadding);
                    log.debug("添加填充后: {}", cleanedData);
                }
                decodedBytes = Base64.getDecoder().decode(cleanedData);
                log.debug("添加填充后Base64解码成功，字节长度: {}", decodedBytes.length);
            }

            // 使用MessagePack解码
            try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(decodedBytes)) {
                Value value = unpacker.unpackValue();
                log.debug("MessagePack解码成功");
                
                // 将MessagePack值转换为JSON字符串
                String jsonString = value.toJson();
                log.debug("转换为JSON: {}", jsonString);
                
                // 格式化JSON（可选）
                Object jsonObject = objectMapper.readValue(jsonString, Object.class);
                String result = objectMapper.writeValueAsString(jsonObject);
                log.debug("格式化后的JSON: {}", result);
                return result;
                
            } catch (Exception e) {
                log.error("MessagePack解码失败", e);
                // 如果MessagePack解码失败，尝试直接返回字符串
                String fallback = new String(decodedBytes, StandardCharsets.UTF_8);
                log.debug("使用fallback返回: {}", fallback);
                return fallback;
            }

        } catch (Exception e) {
            log.error("消息解密失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 尝试解密消息，如果失败则返回原始消息
     * 
     * @param message 原始消息
     * @return 解密后的消息或原始消息
     */
    public static String tryDecrypt(String message) {
        try {
            String decrypted = decrypt(message);
            return decrypted != null ? decrypted : message;
        } catch (Exception e) {
            log.error("消息解密失败，返回原始消息: {}", e.getMessage(), e);
            return message;
        }
    }
}
