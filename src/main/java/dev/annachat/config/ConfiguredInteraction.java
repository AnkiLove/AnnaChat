package dev.annachat.config;

import dev.annachat.api.InteractionProvider;
import dev.annachat.api.InteractiveMatch;
import dev.annachat.api.context.ChatContext;
import dev.annachat.service.TextService;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfiguredInteraction implements InteractionProvider {
    private final int priority;
    private final Pattern pattern;
    private final String text;
    private final List<String> hover;
    private final String clickAction;
    private final String clickValue;
    private final String insertion;
    private final TextService textService;

    public ConfiguredInteraction(int priority, Pattern pattern, String text, List<String> hover,
                                 String clickAction, String clickValue, String insertion,
                                 TextService textService) {
        this.priority = priority;
        this.pattern = pattern;
        this.text = text;
        this.hover = List.copyOf(hover);
        this.clickAction = clickAction;
        this.clickValue = clickValue;
        this.insertion = insertion;
        this.textService = textService;
    }

    @Override
    public Optional<InteractiveMatch> find(ChatContext context, String message, int fromIndex) {
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find(fromIndex)) return Optional.empty();
        String renderedText = expand(text, matcher);
        List<String> renderedHover = hover.stream().map(line -> expand(line, matcher)).toList();
        Component component = textService.configuredComponent(
                context,
                renderedText,
                renderedHover,
                clickAction,
                expand(clickValue, matcher),
                expand(insertion, matcher)
        );
        return Optional.of(new InteractiveMatch(matcher.start(), matcher.end(), component));
    }

    private static String expand(String input, Matcher matcher) {
        if (input == null || input.isEmpty()) return "";
        String output = input.replace("{match}", matcher.group());
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String value = matcher.group(i);
            output = output.replace("{group:" + i + "}", value == null ? "" : value);
        }
        return output;
    }

    @Override
    public int priority() {
        return priority;
    }
}
