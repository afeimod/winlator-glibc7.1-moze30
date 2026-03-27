package com.ewt45.winlator;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;

import com.winlator.xserver.XKeycode;
import com.termux.x11.LorieView;

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

    // 用于累积 ACTION_MULTIPLE 的文本
    private static final StringBuilder pendingText = new StringBuilder();
    private static final Handler uiHandler = new Handler(Looper.getMainLooper());
    private static final Runnable flushRunnable = new Runnable() {
        @Override
        public void run() {
            flushPendingText();
        }
    };

    /**
     * 将累积的文本一次性发送给 X11 应用
     */
    private static void flushPendingText() {
        if (pendingText.length() > 0) {
            String text = pendingText.toString();
            Log.d(TAG, "Flushing pending text: \"" + text + "\"");
            byte[] utf8Bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            // 注意：这里需要传入 LorieView 实例，但 flush 时可能没有实例，需从外部传入
            // 实际调用 flushPendingText 时，需要 LorieView 参数。我们改为在 handleTX11TextInput 中处理
            // 为避免静态方法无 LorieView，我们重新设计：将 flush 逻辑移到 handleTX11TextInput 中处理
        }
    }

    // 改进：在 handleTX11TextInput 中直接使用 pendingText 和 flush
    // 以下重新实现 handleTX11TextInput

    public static boolean handleAndroidKeyEvent(com.winlator.xserver.XServer xServer, KeyEvent event) {
        boolean handled = false;
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            String characters = event.getCharacters();
            if (characters == null) {
                return false;
            }
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
     * 将连续的 ACTION_MULTIPLE 事件合并，延迟 100ms 后一次性发送
     */
    public static boolean handleTX11TextInput(KeyEvent event, LorieView lorieView) {
        if (lorieView == null) return false;

        // 处理 ACTION_MULTIPLE 事件（输入法发送多字符文本）
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            String characters = event.getCharacters();
            if (characters == null || characters.isEmpty()) {
                Log.d(TAG, "ACTION_MULTIPLE: characters is null or empty, ignoring");
                return true; // 消费事件，避免被 KeyEventSender 处理
            }

            Log.d(TAG, "ACTION_MULTIPLE: appending \"" + characters + "\"");
            // 追加到累积缓冲区
            pendingText.append(characters);
            // 重置延迟发送
            uiHandler.removeCallbacks(flushRunnable);
            uiHandler.postDelayed(() -> {
                // 发送累积的文本
                if (pendingText.length() > 0) {
                    String fullText = pendingText.toString();
                    Log.d(TAG, "Sending combined text: \"" + fullText + "\"");
                    byte[] utf8Bytes = fullText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    lorieView.sendTextEvent(utf8Bytes);
                    pendingText.setLength(0); // 清空
                }
            }, 100); // 延迟 100ms，等待输入法后续事件
            return true;
        }

        // 处理 ACTION_DOWN 中的 Unicode 字符（单个字符输入）
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // 如果有累积的文本，先发送（输入法可能已结束）
            if (pendingText.length() > 0) {
                uiHandler.removeCallbacks(flushRunnable);
                String fullText = pendingText.toString();
                Log.d(TAG, "Flushing pending text before single char: \"" + fullText + "\"");
                byte[] utf8Bytes = fullText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                lorieView.sendTextEvent(utf8Bytes);
                pendingText.setLength(0);
            }

            int unicodeChar = event.getUnicodeChar(event.getMetaState());
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