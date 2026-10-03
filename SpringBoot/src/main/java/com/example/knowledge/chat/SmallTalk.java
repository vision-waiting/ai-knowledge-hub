package com.example.knowledge.chat;

import java.util.Locale;

final class SmallTalk {
    private SmallTalk() {}

    // Match the entire message so a greeting before a real question still uses retrieval.
    static String reply(String question) {
        if (question == null) return null;
        String normalized = question.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{Z}\\s]+", "");
        return switch (normalized) {
            case "你好", "您好", "你好呀", "你好啊", "您好呀", "嗨", "哈喽", "哈罗", "hello", "hi", "hey" ->
                    "你好！很高兴和你交流，有什么想了解的？";
            case "早", "早安", "早上好", "上午好", "中午好", "下午好", "晚上好", "goodmorning", "goodafternoon", "goodevening" ->
                    "你好！希望你今天一切顺利，有什么需要我帮忙的吗？";
            case "在吗", "在么", "你在吗" -> "在的！有什么需要我帮忙的吗？";
            case "谢谢", "谢谢你", "多谢", "感谢", "感谢你", "谢啦", "thanks", "thankyou" ->
                    "不客气！有其他问题，随时来问我。";
            case "再见", "拜拜", "回头见", "bye", "goodbye" -> "再见！有需要随时来找我。";
            case "晚安" -> "晚安，祝你休息愉快！";
            default -> null;
        };
    }
}
