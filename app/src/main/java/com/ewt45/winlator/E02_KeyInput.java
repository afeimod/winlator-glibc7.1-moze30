package com.ewt45.winlator;
import android.util.Log;
import android.view.*;
import com.winlator.xserver.*;
import java.util.concurrent.atomic.*;

public class E02_KeyInput {
    private static final String TAG = "E2_KeyInput";
    public static final XKeycode[] stubKeyCode = {
        XKeycode.KEY_CUSTOM_1, XKeycode.KEY_CUSTOM_2, XKeycode.KEY_CUSTOM_3, 
        XKeycode.KEY_CUSTOM_4, XKeycode.KEY_CUSTOM_5, XKeycode.KEY_CUSTOM_6,
        XKeycode.KEY_CUSTOM_7, XKeycode.KEY_CUSTOM_8, XKeycode.KEY_CUSTOM_9,
        XKeycode.KEY_CUSTOM_10, XKeycode.KEY_CUSTOM_11, XKeycode.KEY_CUSTOM_12,
        XKeycode.KEY_CUSTOM_13, XKeycode.KEY_CUSTOM_14, XKeycode.KEY_CUSTOM_15,
        XKeycode.KEY_CUSTOM_16, XKeycode.KEY_CUSTOM_17
    };

    // 使用 AtomicInteger 替代普通的 int
    private static final AtomicInteger currIndex = new AtomicInteger(0);

    public static boolean handleAndroidKeyEvent(XServer xServer, KeyEvent event) {
        boolean handled = false;
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            String characters = event.getCharacters();
            
            // 添加空值检查
            if (characters == null) {
                return false;
            }
            
            for (int i = 0; i < characters.codePointCount(0, characters.length()); i++) {
                // 原子性地获取并增加索引
                int index = currIndex.getAndUpdate(curr -> (curr + 1) % stubKeyCode.length);
                int keycode = stubKeyCode[index].id;
                int keySym = characters.codePointAt(characters.offsetByCodePoints(0, i));

                if (keySym > 0xff) keySym = keySym | 0x1000000;

                xServer.injectKeyPress(stubKeyCode[index], keySym);
                sleep();
                xServer.injectKeyRelease(stubKeyCode[index]);                
                sleep();
                handled = true;
            }
        }
        return handled;
    }

    /**
     * 处理 TX11 的文本输入事件
     * 解决使用 TX11 替代 X Server 时无法输入中文的问题
     * 
     * 问题分析：
     * 1. Android输入法在选词时可能发送ACTION_MULTIPLE事件
     * 2. 但有时也可能通过普通的ACTION_DOWN事件+getUnicodeChar()发送字符
     * 3. Windows程序期待的是WM_CHAR消息，需要正确的UTF-8编码文本
     * 
     * 修复：将 ACTION_MULTIPLE 的完整文本一次性发送，避免逐个字符发送导致的部分字符丢失
     * 
     * @param event Android KeyEvent 事件
     * @param lorieView LorieView 实例用于发送文本事件
     * @return 是否已处理该事件
     */
    public static boolean handleTX11TextInput(KeyEvent event, com.termux.x11.LorieView lorieView) {
        String characters = event.getCharacters();
        
        // 处理 ACTION_MULTIPLE 事件（输入法发送多字符文本）
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            if (characters == null || characters.isEmpty()) {
                Log.d(TAG, "ACTION_MULTIPLE: characters is null or empty, ignoring");
                return true; // 忽略空事件，避免传递给 sendKeyEvent
            }

            Log.d(TAG, "ACTION_MULTIPLE: received \"" + characters + "\", length=" + 
                  characters.length() + ", codepoints=" + characters.codePointCount(0, characters.length()));

            // 一次性发送整个字符串，而不是逐个字符发送
            byte[] utf8Bytes = characters.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            lorieView.sendTextEvent(utf8Bytes);
            return true;
        }
        
        // 处理普通的 ACTION_DOWN 事件中的 Unicode 字符
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // 使用带 meta 状态的 getUnicodeChar 获取更准确的字符
            int unicodeChar = event.getUnicodeChar(event.getMetaState());
            
            // 只处理非 ASCII 字符（中文、日文等）
            if (unicodeChar != 0 && unicodeChar > 0xFF) {
                char[] chars = Character.toChars(unicodeChar);
                String charStr = new String(chars);
                
                Log.d(TAG, "ACTION_DOWN: unicodeChar=0x" + Integer.toHexString(unicodeChar) + 
                      " (\"" + charStr + "\")");
                
                lorieView.sendTextEvent(charStr.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return true;
            }
        }
        
        return false;
    }

    private static void sleep() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}