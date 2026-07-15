package com.xianyusmart.utils;

import lombok.extern.slf4j.Slf4j;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class CookieUtils {
    
    /**
     * 将Cookie字符串转换为Map
     * @param cookieStr Cookie字符串
     * @return Cookie Map
     */
    public static Map<String, String> parseCookies(String cookieStr) {
        Map<String, String> cookies = new HashMap<>();
        
        if (cookieStr == null || cookieStr.isEmpty()) {
            return cookies;
        }
        
        String[] pairs = cookieStr.split("; ");
        for (String pair : pairs) {
            if (pair.contains("=")) {
                String[] parts = pair.split("=", 2);
                cookies.put(parts[0].trim(), parts[1].trim());
            }
        }
        
        return cookies;
    }
    
    /**
     * 将Cookie Map转换为字符串
     * @param cookies Cookie Map
     * @return Cookie字符串
     */
    public static String formatCookies(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        cookies.forEach((key, value) -> {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(key).append("=").append(value);
        });
        
        return sb.toString();
    }
    
    /**
     * 提取_m_h5_tk中的token部分
     * @param cookieStr Cookie字符串
     * @return token
     */
    public static String extractToken(String cookieStr) {
        Map<String, String> cookies = parseCookies(cookieStr);
        String mh5tk = cookies.get("_m_h5_tk");
        
        if (mh5tk != null && mh5tk.contains("_")) {
            return mh5tk.split("_")[0];
        }
        
        return "";
    }
    
    /**
     * 生成API签名
     * @param timestamp 时间戳
     * @param token token
     * @param data 数据
     * @return 签名
     */
    public static String generateSign(String timestamp, String token, String data) {
        String appKey = "34839810";
        String msg = token + "&" + timestamp + "&" + appKey + "&" + data;
        
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(msg.getBytes());
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("生成签名失败", e);
            return "";
        }
    }
}