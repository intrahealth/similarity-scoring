/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.intrahealth.elasticsearch.plugin.similarity;

import info.debatty.java.stringsimilarity.Cosine;
import info.debatty.java.stringsimilarity.Damerau;
import info.debatty.java.stringsimilarity.Jaccard;
import info.debatty.java.stringsimilarity.JaroWinkler;
import info.debatty.java.stringsimilarity.Levenshtein;
import info.debatty.java.stringsimilarity.LongestCommonSubsequence;
import info.debatty.java.stringsimilarity.MetricLCS;
import info.debatty.java.stringsimilarity.NGram;
import info.debatty.java.stringsimilarity.NormalizedLevenshtein;
import info.debatty.java.stringsimilarity.OptimalStringAlignment;
import info.debatty.java.stringsimilarity.QGram;
import info.debatty.java.stringsimilarity.SorensenDice;
import info.debatty.java.stringsimilarity.interfaces.NormalizedStringSimilarity;
import info.debatty.java.stringsimilarity.interfaces.NormalizedStringDistance;
import info.debatty.java.stringsimilarity.interfaces.StringDistance;
import info.debatty.java.stringsimilarity.interfaces.StringSimilarity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.text.Normalizer;

/**
 * This class serves as the interface to the string similarity library which provides the string similarity
 * algorithms used by the plugin.
 */
public class MatcherService {

    private interface Scorer {
        double score(String left, String right);
    }

    private static class StringComparisonMatcher {
        private StringDistance distanceMatcher;
        private StringSimilarity similarityMatcher;
        private Scorer scorer;

        StringComparisonMatcher( StringSimilarity matcher ) {
            this.similarityMatcher = matcher;
            this.scorer = (String left, String right) -> this.similarityMatcher.similarity( left, right );
        }

        StringComparisonMatcher( StringDistance matcher ) {
            this.distanceMatcher = matcher;
            this.scorer = (String left, String right) -> this.distanceMatcher.distance( left, right );
        }

        public double score( String left, String right ) {
            return scorer.score( left, right );
        }
    }

    /**
     * A cache for any matchers that we've already loaded so that we do not need to load them each time.
     */
    private Map<String, StringComparisonMatcher> matchers = new HashMap<>();

    /**
     * Select the right matcher by its name, match the two strings provided and then return the match score. Passing
     * a name for which a matcher does not exist will result in an {@link IllegalArgumentException}.
     *
     * @param matcherName the name of the matcher to use. See @{@link NormalizedStringSimilarity}.
     * @param left        the first of the two strings to match.
     * @param right       the second of the two strings to match.
     *
     * @return the match score.
     */
    public double matchScore(String matcherName, String left, String right) {
        StringComparisonMatcher matcher = getMatcher(matcherName);
        String normalizedLeft = removeAccents(left);
        String normalizedRight = removeAccents(right);

        String cleanedLeft = normalizedLeft.replaceAll("[^a-zA-Z0-9]", "");
        String cleanedRight = normalizedRight.replaceAll("[^a-zA-Z0-9]", "");

        return matcher.score(cleanedLeft.trim().toLowerCase(Locale.getDefault()), cleanedRight.trim().toLowerCase(Locale.getDefault()));
    }

    private static String removeAccents(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
    /**
     * Check if the given matcher name is a distance measure.
     *
     * @param matcherName the name of the matcher to use.
     *
     * @return boolean
     */
    public boolean isDistance(String matcherName) {
        return !isSimilarity(matcherName);
        /*
        switch (matcherName) {
            case "levenshtein":
            case "normalized-levenshtein-distance":
            case "damerau-levenshtein":
            case "optimal-string-alignment":
            case "longest-common-subsequence":
            case "normalized-lcs-distance":
            case "metric-lcs":
            case "ngram":
            case "qgram":
            case "cosine-distance":
            case "dice-distance":
            case "jaccard-distance":
                return true;
                break;
            default:
                return false;
                break;
        }
        */
    }

    /**
     * Check if the given matcher name is a similarity measure.
     *
     * @param matcherName the name of the matcher to use.
     *
     * @return boolean
     */
    public boolean isSimilarity(String matcherName) {
        switch (matcherName) {
            case "cosine-similarity":
            case "dice-similarity":
            case "jaccard-similarity":
            case "jaro-winkler-similarity":
            case "normalized-levenshtein-similarity":
            case "normalized-lcs-similarity":
            case "dateDamerau":
                return true;
            default:
                return false;
         }
    }

    /*
     * Get a matcher by its name from the cache. If the matcher is not already in the cache, load it, place it in the
     * cache and then return it.
     */
    private StringComparisonMatcher getMatcher(String matcherName) {
        if (matchers.containsKey(matcherName)) {
            return matchers.get(matcherName);
        }
        StringSimilarity simMatcher;
        StringDistance disMatcher;
        switch (matcherName) {
            case "cosine-similarity":
                simMatcher = new Cosine();
                matchers.put(matcherName, new StringComparisonMatcher(simMatcher));
                break;
            case "dice-similarity":
                simMatcher = new SorensenDice();
                matchers.put(matcherName, new StringComparisonMatcher(simMatcher));
                break;
            case "jaccard-similarity":
                simMatcher = new Jaccard();
                matchers.put(matcherName, new StringComparisonMatcher(simMatcher));
                break;
            case "jaro-winkler-similarity":
                simMatcher = new JaroWinkler();
                matchers.put(matcherName, new StringComparisonMatcher(simMatcher));
                break;
            case "normalized-levenshtein-similarity":
                simMatcher = new NormalizedLevenshtein();
                matchers.put(matcherName, new StringComparisonMatcher(simMatcher));
                break;
            case "normalized-lcs-similarity":
                simMatcher = new NormalizedLongestCommonSubsequence();
                matchers.put(matcherName, new StringComparisonMatcher(simMatcher));
                break;
            case "levenshtein":
                disMatcher = new Levenshtein();
                matchers.put(matcherName, new StringComparisonMatcher(disMatcher));
                break;
            case "normalized-levenshtein-distance":
                disMatcher = new NormalizedLevenshtein();
                matchers.put(matcherName, new StringComparisonMatcher(disMatcher));
                break;
            case "damerau-levenshtein":
                disMatcher = new Damerau();
                matchers.put(matcherName, new StringComparisonMatcher(disMatcher));
                break;
            case "dateDamerau":
                simMatcher = new DateDamerau();
                matchers.put(matcherName, new StringComparisonMatcher(simMatcher));
                break;                     
            case "optimal-string-alignment":
                disMatcher = new OptimalStringAlignment();
                matchers.put(matcherName, new StringComparisonMatcher(disMatcher));
                break;
            case "jaro-winkler-distance":
                disMatcher = new JaroWinkler();
                matchers.put(matcherName, new StringComparisonMatcher(disMatcher));
                break;
            case "longest-common-subsequence":
                disMatcher = new LongestCommonSubsequence();
                matchers.put(matcherName, new StringComparisonMatcher(disMatcher));
                break;
            case "normalized-lcs-distance":
                disMatcher = new NormalizedLongestCommonSubsequence();
                matchers.put(matcherName, new StringComparisonMatcher(disMatcher));
                break;
            case "metric-lcs":
                disMatcher = new MetricLCS();
                matchers.put(matcherName, new StringComparisonMatcher(disMatcher));
                break;
            case "ngram":
                disMatcher = new NGram();
                matchers.put(matcherName, new StringComparisonMatcher(disMatcher));
                break;
            case "qgram":
                disMatcher = new QGram();
                matchers.put(matcherName, new StringComparisonMatcher(disMatcher));
                break;
            case "cosine-distance":
                disMatcher = new Cosine();
                matchers.put(matcherName, new StringComparisonMatcher(disMatcher));
                break;
            case "dice-distance":
                disMatcher = new SorensenDice();
                matchers.put(matcherName, new StringComparisonMatcher(disMatcher));
                break;
            case "jaccard-distance":
                disMatcher = new Jaccard();
                matchers.put(matcherName, new StringComparisonMatcher(disMatcher));
                break;
            default:
                throw new IllegalArgumentException("The matcher [" + matcherName + "] is not supported.");
        }

        return matchers.get(matcherName);
    }

    /*
     * This class exists to normalize the result returned by the @{@link LongestCommonSubsequence} 
     * and also "flip" for the similarity between the two input strings.
     */
    private static class NormalizedLongestCommonSubsequence implements NormalizedStringSimilarity, NormalizedStringDistance {

        LongestCommonSubsequence lcs = new LongestCommonSubsequence();

        @Override
        public double distance(String s1, String s2) {
            double distance = lcs.distance(s1, s2);
            return distance / Math.max(s1.length(), s2.length());
        }

        @Override
        public double similarity(String s1, String s2) {
            return 1 - distance(s1, s2);
        }

    }
}
