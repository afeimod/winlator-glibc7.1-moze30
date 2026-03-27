package com.ewt45.winlator;

import android.util.Log;
import android.view.KeyEvent;

import com.winlator.xserver.XKeycode;
import com.termux.x11.LorieView;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static final AtomicInteger currIndex = new AtomicInteger(0);

    public static boolean handleAndroidKeyEvent(com.winlator.xserver.XServer xServer, KeyEvent event) {
        boolean handled = false;
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            String characters = event.getCharacters();
            if (characters == null) return false;
            for (int i = 0; i < characters.codePointCount(0, characters.length()); i++) {
                int index = currIndex.getAndUpdate(curr -> (curr + 1) % stubKeyCode.length);
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
     * 处理 TX11 的文本输入事件（修复中文输入分批发送问题）
     * @param event Android KeyEvent
     * @param lorieView LorieView 实例
     * @param pendingText 用于累积 ACTION_MULTIPLE 文本的 StringBuilder
     * @return true 表示已处理该事件，false 表示未处理（需交给 KeyEventSender）
     */
    public static boolean handleTX11TextInput(KeyEvent event, LorieView lorieView, StringBuilder pendingText) {
        if (lorieView == null) return false;

        int action = event.getAction();

        // 处理 ACTION_MULTIPLE：累积文本，不立即发送
        if (action == KeyEvent.ACTION_MULTIPLE) {
            String characters = event.getCharacters();
            if (characters == null || characters.isEmpty()) {
                Log.d(TAG, "ACTION_MULTIPLE: empty, ignoring");
                return true; // 消费空事件
            }
            pendingText.append(characters);
            Log.d(TAG, "ACTION_MULTIPLE: appended \"" + characters + "\", total length=" + pendingText.length());
            return true; // 消费事件，防止被 KeyEventSender 处理
        }

        // 非 ACTION_MULTIPLE 事件：先发送累积的文本
        if (pendingText.length() > 0) {
            String fullText = pendingText.toString();
            Log.d(TAG, "Flushing pending text: \"" + fullText + "\"");
            byte[] utf8Bytes = fullText.getBytes(StandardCharsets.UTF_8);
            lorieView.sendTextEvent(utf8Bytes);
            pendingText.setLength(0); // 清空
        }

        // 处理 ACTION_DOWN 中的单个 Unicode 字符（非 ASCII）
        if (action == KeyEvent.ACTION_DOWN) {
            int unicodeChar = event.getUnicodeChar(event.getMetaState());
            if (unicodeChar != 0 && unicodeChar > 0xFF) {
                char[] chars = Character.toChars(unicodeChar);
                String charStr = new String(chars);
                Log.d(TAG, "ACTION_DOWN: sending single char \"" + charStr + "\"");
                lorieView.sendTextEvent(charStr.getBytes(StandardCharsets.UTF_8));
                return true; // 已处理，阻止 KeyEventSender 再次处理
            }
        }

        // 其他事件（如功能键、ACTION_UP 等）返回 false，交给 KeyEventSender 处理
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