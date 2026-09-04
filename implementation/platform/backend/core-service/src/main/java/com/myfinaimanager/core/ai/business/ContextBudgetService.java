package com.myfinaimanager.core.ai.business;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * Builds a compact, provider-neutral context within the configured character budget (FR-029),
 * with an optional redaction pass (FR-039). EN006 has no business feature supplying a rich domain
 * object graph yet, so the "explicit context builder" here operates on the caller-supplied raw
 * context string — a future feature's own context builder would call this after flattening its
 * domain objects, inheriting the same budget/redaction guarantees.
 */
@Service
public class ContextBudgetService {

    /**
     * Conservative, explicit patterns for obviously secret-shaped substrings — never a general DLP
     * classifier. Case-insensitive; each match is replaced with {@code [REDACTED]}.
     */
    private static final Pattern OBVIOUS_SECRET =
            Pattern.compile("(?i)(api[_-]?key\\s*[:=]\\s*\\S+|bearer\\s+\\S+|password\\s*[:=]\\s*\\S+)");

    /**
     * @param rawContext         the caller-supplied context text
     * @param maxInputCharacters the configured character budget (&gt;0)
     * @return the redacted context, truncated at the nearest whole-word boundary at or before
     *     {@code maxInputCharacters} if it would otherwise exceed the budget — never mid-word, so
     *     truncation does not silently change the meaning of the last retained word (§23)
     */
    public String build(String rawContext, int maxInputCharacters) {
        String redacted = OBVIOUS_SECRET.matcher(rawContext).replaceAll("[REDACTED]");
        if (redacted.length() <= maxInputCharacters) {
            return redacted;
        }
        int cut = redacted.lastIndexOf(' ', maxInputCharacters);
        return cut > 0 ? redacted.substring(0, cut) : redacted.substring(0, maxInputCharacters);
    }
}
