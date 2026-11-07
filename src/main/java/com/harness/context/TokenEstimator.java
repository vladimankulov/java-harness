package com.harness.context;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.StringUtils;

import java.util.List;

public final class TokenEstimator {

    private static final int CHARS_PER_TOKEN = 4;

    private TokenEstimator() {
    }

    public static int estimate(@Nullable String text) {
        return !StringUtils.hasText(text) ? 0 : Math.round((float) text.length() / CHARS_PER_TOKEN);
    }

    /**
     * Оценка всего, что уедет в модель одним запросом.
     */
    public static int estimate(List<Message> messages) {
        int total = 0;
        for (Message message : messages) {
            total += estimate(message.getText());
        }
        return total;
    }
}
