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
     * 处理 TX11 的文本输入事件
     * 对于 ACTION_MULTIPLE，一次性发送整个字符串（不逐个字符发送）
     * 对于 ACTION_DOWN 中的非 ASCII 字符，也一次性发送
     */
    public static boolean handleTX11TextInput(KeyEvent event, LorieView lorieView) {
        if (lorieView == null) return false;

        // 处理 ACTION_MULTIPLE：一次性发送整个字符串
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            String characters = event.getCharacters();
            if (characters == null || characters.isEmpty()) {
                Log.d(TAG, "ACTION_MULTIPLE: empty, ignoring");
                return true; // 消费空事件
            }
            Log.d(TAG, "ACTION_MULTIPLE: sending \"" + characters + "\"");
            byte[] utf8Bytes = characters.getBytes(StandardCharsets.UTF_8);
            lorieView.sendTextEvent(utf8Bytes);
            return true; // 消费事件
        }

        // 处理 ACTION_DOWN 中的单个 Unicode 字符（非 ASCII）
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int unicodeChar = event.getUnicodeChar(event.getMetaState());
            if (unicodeChar != 0 && unicodeChar > 0xFF) {
                char[] chars = Character.toChars(unicodeChar);
                String charStr = new String(chars);
                Log.d(TAG, "ACTION_DOWN: sending single char \"" + charStr + "\"");
                lorieView.sendTextEvent(charStr.getBytes(StandardCharsets.UTF_8));
                return true;
            }
        }

        // 其他事件（ASCII 字符、功能键等）交给 KeyEventSender
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