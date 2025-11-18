package com.example.bingo.service;

import com.example.bingo.model.KeywordInsight;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Produces short educational snippets for each bingo phrase. A handful of curated rules cover the
 * most common scenarios, and a friendly default keeps every square meaningful.
 */
@Component
public class KeywordInsightService {

    private final List<InsightRule> rules = List.of(
            rule(
                    "Why hallucinations happen",
                    "Generative models predict the most probable next token. Without grounding, they can invent facts that sound convincing—double-check anything critical.",
                    "https://en.wikipedia.org/wiki/Hallucination_(artificial_intelligence)",
                    "hallucination",
                    "confident"),
            rule(
                    "Prompt engineering reality check",
                    "Prompts act like instructions, but models still follow probability. Long or forceful prompts rarely beat clear context and iterative refinement.",
                    "https://learn.microsoft.com/azure/ai-services/openai/how-to/system-message",
                    "prompt"),
            rule(
                    "Legal answers require humans",
                    "Language models are not lawyers and can't provide advice tailored to jurisdictions or current statutes. Treat AI outputs as drafts, not counsel.",
                    "https://www.americanbar.org/groups/law_practice/publications/law_practice_magazine/2019/nd2019/nd2019-ethics",
                    "legal"),
            rule(
                    "Protect customer data",
                    "Production data should only be accessed through governed systems. Copying it into public AI tools risks security incidents and compliance violations.",
                    "https://owasp.org/www-project-top-10-for-large-language-model-applications/",
                    "production",
                    "data"),
            rule(
                    "TTS quirks are normal",
                    "Text-to-speech systems still struggle with names, abbreviations, and accents. Provide phonetic hints or SSML tags when clarity matters.",
                    "https://cloud.google.com/text-to-speech/docs/ssml",
                    "tts",
                    "speech",
                    "voice"),
            rule(
                    "Image models still miss details",
                    "Diffusion models learn visual patterns but can fumble human anatomy and fine structure (like fingers). Iterating on prompts or editing manually is often required.",
                    "https://research.nvidia.com/publication/2023-06_diffusion-models",
                    "image",
                    "picture"),
            rule(
                    "Automate with context",
                    "Automation amplifies both clarity and confusion. Before delegating to AI, decide who owns the outcome and how humans will review it.",
                    null,
                    "automation",
                    "auto"),
            rule(
                    "AI coding assistants",
                    "Code copilots can accelerate routine tasks, but they also generate bugs or outdated APIs. Keep tests handy and review suggestions like you would a junior dev.",
                    "https://arxiv.org/abs/2306.10053",
                    "code",
                    "copilot"),
            rule(
                    "Summaries need nuance",
                    "Automatic summaries condense text but may miss tone, nuance, or action items. Use them as starting drafts and fill gaps collaboratively.",
                    null,
                    "summary",
                    "summaries",
                    "notes"),
            rule(
                    "Ethics > hype",
                    "Ethics conversations are valuable when they focus on governance, bias testing, and user impact—not just futuristic debates.",
                    "https://www.nist.gov/itl/ai-risk-management-framework",
                    "ethic",
                    "sentient"),
            rule(
                    "Naming with AI",
                    "AI-generated names can spark ideas, but check for trademarks, cultural context, and pronounceability before adopting them.",
                    null,
                    "name",
                    "project"),
            rule(
                    "Prompt stacks and iteration",
                    "If you're rewriting AI output with another AI, consider adjusting the source prompt or providing explicit critiques instead of nesting tools.",
                    null,
                    "rewrite",
                    "regenerate"));

    public KeywordInsight describe(String phrase) {
        String normalized = normalize(phrase);
        return rules.stream()
                .filter(rule -> rule.test(normalized))
                .findFirst()
                .map(rule -> rule.toInsight(phrase))
                .orElse(defaultInsight(phrase));
    }

    private KeywordInsight defaultInsight(String phrase) {
        String title = "What this square highlights";
        String description = """
                “%s” is a real pattern teams report when experimenting with AI. Use it as a reminder to pause, discuss the behavior, and decide how people and tools can improve it together.
                """.formatted(phrase);
        return new KeywordInsight(phrase, title, description.trim(), null);
    }

    private static String normalize(String phrase) {
        return phrase.toLowerCase(Locale.ENGLISH);
    }

    private static InsightRule rule(String title, String description, String source, String... keywords) {
        return new InsightRule(title, description, source, Arrays.asList(keywords));
    }

    private static final class InsightRule implements Predicate<String> {
        private final String title;
        private final String description;
        private final String source;
        private final List<String> keywords;

        private InsightRule(String title, String description, String source, List<String> keywords) {
            this.title = title;
            this.description = description;
            this.source = source;
            this.keywords = keywords.stream()
                    .filter(Objects::nonNull)
                    .map(String::toLowerCase)
                    .toList();
        }

        @Override
        public boolean test(String candidate) {
            return keywords.stream().allMatch(candidate::contains);
        }

        private KeywordInsight toInsight(String phrase) {
            return new KeywordInsight(phrase, title, description, source);
        }
    }
}
