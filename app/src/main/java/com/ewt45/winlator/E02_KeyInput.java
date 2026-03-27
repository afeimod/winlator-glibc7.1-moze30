package com.ewt45.winlator;

import android.os.Handler;
import android.os.Looper;
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

    // TX11 文本累积相关
    private static final StringBuilder pendingText = new StringBuilder();
    private static final Handler uiHandler = new Handler(Looper.getMainLooper());
    private static final Runnable flushRunnable = () -> flushPendingText(null);
    private static LorieView currentLorieView = null;

    /**
     * 处理默认 XServer 的 ACTION_MULTIPLE 事件（模拟按键方式输入）
     * 此方法供 com.winlator.xserver.Keyboard 调用
     */
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
     * 发送累积的文本到 LorieView
     */
    private static void flushPendingText(LorieView lorieView) {
        if (pendingText.length() == 0) return;
        LorieView target = lorieView != null ? lorieView : currentLorieView;
        if (target == null) {
            Log.w(TAG, "flushPendingText: no LorieView available");
            return;
        }
        String fullText = pendingText.toString();
        Log.d(TAG, "Flushing pending text: \"" + fullText + "\" (length=" + fullText.length() + ")");
        byte[] utf8Bytes = fullText.getBytes(StandardCharsets.UTF_8);
        target.sendTextEvent(utf8Bytes);
        pendingText.setLength(0);
    }

    /**
     * 处理 TX11 的文本输入事件
     * 将连续的 ACTION_MULTIPLE 合并，延迟 30ms 后一次性发送，避免分片丢失
     *
     * @param event     Android KeyEvent
     * @param lorieView LorieView 实例
     * @return true 表示事件已处理（消耗），false 表示未处理（需交由 KeyEventSender）
     */
    public static boolean handleTX11TextInput(KeyEvent event, LorieView lorieView) {
        if (lorieView == null) return false;
        currentLorieView = lorieView; // 保存引用供延迟发送使用

        int action = event.getAction();

        // 处理 ACTION_MULTIPLE：累积文本，延迟发送
        if (action == KeyEvent.ACTION_MULTIPLE) {
            String characters = event.getCharacters();
            if (characters == null || characters.isEmpty()) {
                Log.d(TAG, "ACTION_MULTIPLE: empty, ignoring");
                return true; // 消费空事件
            }
            pendingText.append(characters);
            Log.d(TAG, "ACTION_MULTIPLE: appended \"" + characters + "\", pending length=" + pendingText.length());

            // 取消之前的延迟任务，重新开始计时
            uiHandler.removeCallbacks(flushRunnable);
            uiHandler.postDelayed(flushRunnable, 30); // 30ms 延迟，平衡响应与合并
            return true;
        }

        // 非 ACTION_MULTIPLE 事件（如 ACTION_DOWN 中的单个字符）：先发送累积文本
        if (pendingText.length() > 0) {
            uiHandler.removeCallbacks(flushRunnable);
            flushPendingText(lorieView);
        }

        // 处理 ACTION_DOWN 中的 Unicode 字符（非 ASCII）
        if (action == KeyEvent.ACTION_DOWN) {
            int unicodeChar = event.getUnicodeChar(event.getMetaState());
            if (unicodeChar != 0 && unicodeChar > 0xFF) {
                char[] chars = Character.toChars(unicodeChar);
                String charStr = new String(chars);
                Log.d(TAG, "ACTION_DOWN: sending single char \"" + charStr + "\"");
                lorieView.sendTextEvent(charStr.getBytes(StandardCharsets.UTF_8));
                return true;
            }
        }

        return false; // 其他事件（ASCII、功能键等）交给 KeyEventSender
    }

    /**
     * 主动预热 LorieView 的文本输入通道（在连接成功后调用）
     * 发送一个零宽空格，确保底层初始化
     */
    public static void warmup(LorieView lorieView) {
        if (lorieView == null) return;
        Log.d(TAG, "Warmup: sending initial text to stabilize channel");
        // 发送零宽空格（不可见字符），触发内部初始化
        lorieView.sendTextEvent("\u200B".getBytes(StandardCharsets.UTF_8));
    }

    private static void sleep() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}