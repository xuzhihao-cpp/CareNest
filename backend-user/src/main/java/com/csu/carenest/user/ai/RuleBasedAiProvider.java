package com.csu.carenest.user.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "carenest.ai", name = "provider", havingValue = "rule", matchIfMissing = true)
public class RuleBasedAiProvider extends AiProvider {
    private final AiSafetyClassifier classifier;
    public RuleBasedAiProvider(AiSafetyClassifier classifier) { this.classifier = classifier; }
    @Override public Result answer(String content) { return classifier.classify(content); }
}
