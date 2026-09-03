package dev.sweety.tree.embedding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Universal High-Performance Tri-Hybrid Embedding and BM25F Scoring Engine (Shared across Finder & Client).
 */
public final class TriHybridEmbeddingEngine {

    public static final TriHybridEmbeddingEngine INSTANCE = new TriHybridEmbeddingEngine();

    private static final Pattern CAMEL_CASE = Pattern.compile("(?<=[a-z])(?=[A-Z])");
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z0-9_]{2,}");

    private static final double BM25_K1 = 1.5;
    private static final double BM25_B = 0.75;

    private TriHybridEmbeddingEngine() {}

    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        String spaced = CAMEL_CASE.matcher(text).replaceAll(" ");
        var matcher = WORD_PATTERN.matcher(spaced.toLowerCase());
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return List.copyOf(tokens);
    }

    public Map<String, Float> vectorize(String text, Set<String> additionalTags) {
        Map<String, Float> vector = new HashMap<>();
        if (text == null || text.trim().isEmpty()) return vector;

        List<String> rawTokens = tokenize(text);
        Set<String> expandedWords = new HashSet<>(rawTokens);

        if (additionalTags != null) {
            for (String tag : additionalTags) {
                if (tag != null && tag.length() >= 2) {
                    expandedWords.addAll(tokenize(tag));
                }
            }
        }

        for (String token : expandedWords) {
            token = token.trim().toLowerCase();
            if (token.isEmpty()) continue;

            vector.merge(token, 3.5f, Float::sum);

            if (token.length() >= 3) {
                for (int len = 3; len <= Math.min(5, token.length()); len++) {
                    for (int i = 0; i <= token.length() - len; i++) {
                        String ngram = token.substring(i, i + len);
                        vector.merge(ngram, 0.75f, Float::sum);
                    }
                }
            }
        }

        normalize(vector);
        return vector;
    }

    public float cosineSimilarity(Map<String, Float> vecA, Map<String, Float> vecB) {
        if (vecA == null || vecB == null || vecA.isEmpty() || vecB.isEmpty()) return 0f;

        Map<String, Float> smaller = vecA.size() < vecB.size() ? vecA : vecB;
        Map<String, Float> larger = vecA.size() < vecB.size() ? vecB : vecA;

        float dotProduct = 0f;
        for (Map.Entry<String, Float> entry : smaller.entrySet()) {
            Float weightInLarger = larger.get(entry.getKey());
            if (weightInLarger != null) {
                dotProduct += entry.getValue() * weightInLarger;
            }
        }

        return Math.min(1.0f, Math.max(0.0f, dotProduct));
    }

    public float bm25Score(List<String> queryTokens, List<String> docTokens, double avgDocLen) {
        if (queryTokens.isEmpty() || docTokens.isEmpty()) return 0f;

        int docLen = docTokens.size();
        Map<String, Integer> freqMap = new HashMap<>();
        for (String w : docTokens) {
            freqMap.put(w, freqMap.getOrDefault(w, 0) + 1);
        }

        double score = 0.0;
        for (String q : queryTokens) {
            Integer f = freqMap.get(q);
            if (f != null && f > 0) {
                double idf = Math.log(1.0 + (100.0 / (f + 1.0)));
                double denom = f + BM25_K1 * (1.0 - BM25_B + BM25_B * ((double) docLen / (avgDocLen > 0 ? avgDocLen : 1.0)));
                score += idf * (f * (BM25_K1 + 1.0)) / denom;
            }
        }
        return (float) score;
    }

    public void normalize(Map<String, Float> vec) {
        float normSq = 0f;
        for (float val : vec.values()) {
            normSq += val * val;
        }
        if (normSq > 0.00001f) {
            float norm = (float) Math.sqrt(normSq);
            for (Map.Entry<String, Float> entry : vec.entrySet()) {
                entry.setValue(entry.getValue() / norm);
            }
        }
    }
}
