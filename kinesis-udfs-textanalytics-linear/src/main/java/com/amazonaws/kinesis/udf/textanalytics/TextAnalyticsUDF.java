/*-
 * #%L
 * TextAnalyticsUDFHandler
 * %%
 * Copyright (C) 2019 - 2020 Amazon Web Services
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package com.amazonaws.kinesis.udf.textanalytics;

import org.apache.flink.table.functions.ScalarFunction;
import software.amazon.awssdk.core.retry.backoff.EqualJitterBackoffStrategy;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import java.time.Duration;

import software.amazon.awssdk.services.comprehend.model.*;
import software.amazon.awssdk.services.translate.TranslateClient;
import software.amazon.awssdk.services.translate.model.TranslateTextRequest;
import software.amazon.awssdk.services.translate.model.TranslateTextResponse;
import software.amazon.awssdk.services.comprehend.ComprehendClient;

import com.google.gson.Gson;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TextAnalyticsUDF extends ScalarFunction
{
    public static int maxTextBytes = 5000;  //utf8 bytes

    private TranslateClient translateClient;
    private ComprehendClient comprehendClient;

    /**
     * Methods to define AWS Client Configuration from JAR
     * The clients and their related configurations are rather passed from the Scala runtime of Kinesis Analytics Studio Notebooks instance
     * To use local clients, utilize getComprehendClient()/getTranslateClient() instead of comprehendClient/translateClient in below methods
     * */
    private ClientOverrideConfiguration createClientOverrideConfiguration() {
        int retryBaseDelay = 1;
        int retryMaxBackoffTime = 600;
        int maxRetries = 100;
        int timeout = 5;
        RetryPolicy retryPolicy = RetryPolicy.defaultRetryPolicy().toBuilder()
                .numRetries(maxRetries)
                .backoffStrategy(EqualJitterBackoffStrategy.builder()
                        .baseDelay(Duration.ofSeconds(retryBaseDelay))
                        .maxBackoffTime(Duration.ofSeconds(retryMaxBackoffTime))
                        .build())
                .build();
        ClientOverrideConfiguration clientOverrideConfiguration = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(timeout))
                .apiCallAttemptTimeout(Duration.ofSeconds(timeout))
                .retryPolicy(retryPolicy)
                .build();
        return clientOverrideConfiguration;
    }
    private ComprehendClient getComprehendClient() {
        // create client first time on demand
        if (this.comprehendClient == null) {
            System.out.println("Creating Comprehend client connection");
            this.comprehendClient = ComprehendClient.builder()
                    .overrideConfiguration(createClientOverrideConfiguration())
                    .build();
        }
        return this.comprehendClient;
    }
    private TranslateClient getTranslateClient() {
        // create client first time on demand
        if (this.translateClient == null) {
            System.out.println("Creating Translate client connection");
            this.translateClient = TranslateClient.builder()
                    .overrideConfiguration(createClientOverrideConfiguration())
                    .build();
        }
        return this.translateClient;
    }

    /**
     * Initialize clients for Amazon Translate and Amazon Comprehend
     * */
    private void initAWSClients(){
        comprehendClient = getComprehendClient();
        translateClient = getTranslateClient();
    }

    /**
     * The behavior of a ScalarFunction can be defined by implementing a custom evaluation method. An evaluation method must be declared publicly and named eval. Evaluation methods can also be overloaded by implementing multiple methods named eval.
     * See :
     *
     * Overloaded to support detect_dominant_language & detect_dominant_language_all use-case
     * */
    public String eval(String todo, String input) throws Exception{
        initAWSClients();
        String retVal = null;
        switch(todo) {
            case "detect_dominant_language": retVal = detect_dominant_language(input); break;
            case "detect_dominant_language_all": retVal = detect_dominant_language_all(input); break;
            default: retVal = "no match found";
        }
        return retVal;
    }

    /**
     * The behavior of a ScalarFunction can be defined by implementing a custom evaluation method. An evaluation method must be declared publicly and named eval. Evaluation methods can also be overloaded by implementing multiple methods named eval.
     * See :
     *
     * Overloaded to support detect_sentiment, detect_sentiment_all, detect_entities, detect_entities_all, detect_pii_entities, detect_pii_entities_all, redact_entities & redact_pii_entities use-case
     * */
    public String[] eval(String todo, String input, String lang) throws Exception{
        initAWSClients();
        String[] retVal = null;
        switch(todo) {
            case "detect_sentiment": retVal = detect_sentiment(input,lang); break;
            case "detect_sentiment_all":   retVal = detect_sentiment_all(input, lang); break;
            case "detect_entities" :   retVal         = detect_entities(input, lang); break;
            case "detect_entities_all" :   retVal    = detect_entities_all(input, lang); break;
            case "detect_pii_entities" :   retVal    = detect_pii_entities(input, lang); break;
            case "detect_pii_entities_all" :   retVal= detect_pii_entities_all(input, lang); break;
            case "redact_entities"     :   retVal    = redact_entities(input, lang, "ALL"); break;
            case "redact_pii_entities" :   retVal    = redact_pii_entities(input, lang, "ALL"); break;
            default: retVal = new String[1]; retVal[0] = "no match found";
        }
        return retVal;
    }

    /**
     * The behavior of a ScalarFunction can be defined by implementing a custom evaluation method. An evaluation method must be declared publicly and named eval. Evaluation methods can also be overloaded by implementing multiple methods named eval.
     * See :
     *
     * Overloaded to support translate_text use-case
     * */
    public String eval(String todo, String input, String targetLang, String terminologyNames) throws Exception{
        initAWSClients();
        String retVal = null;
        switch(todo) {
            case "translate_text": retVal = translate_text(input,"auto", targetLang, terminologyNames); break;
            default: retVal = "no match found";
        }
        return retVal;
    }



    /**
     * DETECT DOMINANT LANGUAGE
     **/
    /**
     * Given an input string, return a string of language code representing the detected dominant language
     * @param    input   an input string
     * @return   a string of language code
     */
    public String detect_dominant_language(String input) throws Exception {
        return detect_dominant_language(input, false);
    }

    /**
     * Given an input string, return a string of language code representing the detected dominant language and confidence score
     * @param    input   an input string
     * @return   a string of detectDominantLanguage results for input string
     */
    public String detect_dominant_language_all(String input) throws Exception {
        return detect_dominant_language(input, true);
    }

    private String detect_dominant_language(String input, boolean fullResponse) throws Exception {
        String result = "";
        input = truncateLargeStrings(input);
        DetectDominantLanguageRequest detectDominantLanguageRequest = DetectDominantLanguageRequest.builder().text(input).build();
        DetectDominantLanguageResponse detectDominantLanguageResponse = comprehendClient.detectDominantLanguage(detectDominantLanguageRequest);
        List<DominantLanguage> detectDominantLanguageItemResult = detectDominantLanguageResponse.languages();
        if(fullResponse) {
            for (int i = 0; i < detectDominantLanguageItemResult.size(); i++) {
                //result = result +"_"+ detectDominantLanguageItemResult.get(i).languageCode();
                result = result +","+ "lang_code="+detectDominantLanguageItemResult.get(i).languageCode()
                        +",score="+ detectDominantLanguageItemResult.get(i).score().toString();
            }
            result = result.substring(1);
        }else{
            result = detectDominantLanguageItemResult.get(0).languageCode();
        }
        return result;
    }

    /**
     * DETECT SENTIMENT
     **/
    /**
     * Given an input string, return a string of language code representing the detected sentiment of input string
     * @param    input    an input string
     * @param    language a string of language codes corresponding to input string
     * @return   a string array of detected sentiment
     */
    public String[] detect_sentiment(String input, String language) throws Exception {
        return detect_sentiment(input, language, false);
    }

    /**
     * Given an input string, return a string array of language code representing the detected sentiment and confidence scores of input string
     * @param    input    an input string
     * @param    language a string of language codes corresponding to input string
     * @return   a string array of detected sentiment
     */
    public String[] detect_sentiment_all(String input, String language) throws Exception {
        return detect_sentiment(input, language, true);
    }

    private String[] detect_sentiment(String input, String language, boolean fullResponse) throws Exception {
        String[] result;
        input = truncateLargeStrings(input);
        DetectSentimentRequest detectSentimentRequest = DetectSentimentRequest.builder().text(input).languageCode(language).build();
        DetectSentimentResponse detectSentimentResponse = comprehendClient.detectSentiment(detectSentimentRequest);
        result = new String[1];
        if(fullResponse) {
            result[0] = "sentiment="+ detectSentimentResponse.sentimentAsString()
                    +",positiveScore="+ detectSentimentResponse.sentimentScore().positive()
                    +",negativetiveScore="+ detectSentimentResponse.sentimentScore().negative()
                    +",neutralScore="+ detectSentimentResponse.sentimentScore().neutral()
                    +",mixedScore="+ detectSentimentResponse.sentimentScore().mixed();
        }else{
            result[0] = detectSentimentResponse.sentimentAsString();
        }
        return result;
    }

    /**
     * TRANSLATE TEXT
     */
    /**
     * Given an input string, source language, target language, and optional terminology names, return a translated string
     * @param    input    an input string
     * @param    sourcelanguage a string of source language code corresponding (source lang can be 'auto' if source language is unknown)
     * @param    targetlanguage a string of target language code
     * @param    terminologynames a string of custom terminology name in Amazon Translate (or 'NULL' if CT is not to be applied)
     * @return   a string of translated string values
     */
    public String translate_text(String input, String sourcelanguage, String targetlanguage, String terminologynames) throws Exception {
        String result = "";
        String[] inputArray = splitLargeStrings(input);
        TranslateTextRequest translateTextRequest;
        TranslateTextResponse translateTextResponse;
        for(int inputCounter=0 ; inputCounter<inputArray.length ; inputCounter++){
            String subInput = inputArray[inputCounter];
            translateTextRequest = TranslateTextRequest.builder().sourceLanguageCode(sourcelanguage).targetLanguageCode(targetlanguage).text(subInput).build();
            if(!terminologynames.equals("null")){
                translateTextRequest = translateTextRequest.toBuilder().terminologyNames().build();
            }
            translateTextResponse = translateClient.translateText(translateTextRequest);
            result = result +" "+ translateTextResponse.translatedText();
        }
        result = result.substring(1);
        return result;
    }


    /**
     * DETECT / REDACT ENTITIES
     **/

    /**
     * Given an input string returns a string array representing the detected entities (key/value pairs)
     * @param    input  an input string
     * @param    language an input string of language codes corresponding to each input string
     * @return   a string array of detected entities
     */
    public String[] detect_entities(String input, String language) throws Exception {
        return detect_entities(input, language, "[]", false);
    }

    /**
     * Given an input string returns a string array representing the detected entities (key/value pairs) and confidence scores
     * @param    input  an input string
     * @param    language an input string of language codes corresponding to each input string
     * @return   a string array of detected entities
     */
    public String[] detect_entities_all(String input, String language) throws Exception {
        return detect_entities(input, language, "[]", true);
    }

    /**
     * Given an input string with corresponding languages and entity types to redact, returns a string array of redacted strings
     * @param    input  an input string
     * @param    language a string of language codes corresponding to each input string
     * @param    redacttypes a strings with comma-separated Entity Types to redact for each input string (or 'ALL' for all entity types)
     * @return   a string array of detected entities
     */
    public String[] redact_entities(String input, String language, String redacttypes) throws Exception {
        return detect_entities(input, language, redacttypes, false);
    }

    private String[] detect_entities(String input, String language, String redacttypes, boolean fullResponse) throws Exception {
        //String[] result = new String[1];
        //if no split required then subInput == input
        String[] inputArray = splitLargeStrings(input);
        DetectEntitiesRequest detectEntitiesRequest;
        DetectEntitiesResponse detectEntitiesResponse;
        List<String> parentList = new ArrayList<String>();
        for(int inputCounter=0 ; inputCounter<inputArray.length ; inputCounter++){
            String subInput = inputArray[inputCounter];
            detectEntitiesRequest = DetectEntitiesRequest.builder().text(subInput).languageCode(language).build();
            detectEntitiesResponse = comprehendClient.detectEntities(detectEntitiesRequest);
            if(detectEntitiesResponse.hasEntities()) {
                List<Entity> entityListForSubInput = detectEntitiesResponse.entities();
                if (fullResponse) {
                    for (int i = 0; i < entityListForSubInput.size(); i++) {
                        //["score=1,type=a,text=b,beginOffset=1,endOffset=2","score=1,type=a,text=b,beginOffset=1,endOffset=2"]
                        parentList.add("score=" + entityListForSubInput.get(i).score().toString()
                                + ",type=" + entityListForSubInput.get(i).type().toString()
                                + ",text=" + entityListForSubInput.get(i).text()
                                + ",beginOffset=" + entityListForSubInput.get(i).beginOffset().toString()
                                + ",endOffset=" + entityListForSubInput.get(i).endOffset().toString());
                    }
                } else {
                    if (redacttypes.equals("[]")) {
                        //["ORGANIZATION","AWS"]
                        parentList.add(getEntityTypesAndValues(entityListForSubInput, input));
                    } else {
                        //["I work at [ORGANIZATION]"]
                        parentList.add(redactEntityTypes(entityListForSubInput, input, redacttypes));
                    }
                }
            }
        }
        String[] result = new String[parentList.size()];
        result = parentList.toArray(result);
        return result;
    }

    private String getEntityTypesAndValues(List<Entity> entities, String text) throws Exception {
        List<String[]> typesAndValues = new ArrayList<String[]>();
        for (Entity entity : entities) {
            String type = entity.type().toString();
            String value = text.substring(entity.beginOffset(), entity.endOffset());
            typesAndValues.add(new String[]{type, value});
        }
        return toJSON(typesAndValues);
    }
    private String redactEntityTypes(List<Entity> entities, String text, String redactTypes) throws Exception {
        // redactTypes contains comma or space separated list of types, e.g. "NAME, ADDRESS"
        List<String> redactTypeList = Arrays.asList(redactTypes.split("[\\s,]+"));
        String result = text;
        int deltaLength = 0;
        for (Entity entity : entities) {
            String type = entity.type().toString();
            if (redactTypes.contains(type) || redactTypes.contains("ALL")) {
                // this is a PII type we need to redact
                // Offset logic assumes piiEntity list is ordered by occurance in string
                int start = entity.beginOffset() + deltaLength;
                int end = entity.endOffset() + deltaLength;
                int length1 = result.length();
                result = new String(result.substring(0, start) + "[" + type + "]" + result.substring(end));
                deltaLength = deltaLength + (result.length() - length1);
            }
        }
        return result;
    }


    /**
     * DETECT / REDACT PII ENTITIES
     **/

    /**
     * Given an input string returns a string array representing the detected PII entities (key/value pairs)
     * @param    input  an input string
     * @param    language an input string of language codes corresponding to each input string
     * @return   a string array of detected PII entities
     */
    public String[] detect_pii_entities(String input, String language) throws Exception {
        return detect_pii_entities(input, language, "[]", false);
    }

    /**
     * Given an input string returns a string array representing the detected PII entities (key/value pairs) and confidence scores
     * @param    input  an input string
     * @param    language an input string of language codes corresponding to each input string
     * @return   a string array of detected PII entities
     */
    public String[] detect_pii_entities_all(String input, String language) throws Exception {
        return detect_pii_entities(input, language, "[]", true);
    }

    /**
     * Given an input string with corresponding languages and PII entity types to redact, returns a string array of redacted strings
     * @param    input  an input string
     * @param    language a string of language codes corresponding to each input string
     * @param    redacttypes a strings with comma-separated PII Entity Types to redact for each input string (or 'ALL' for all entity types)
     * @return   a string array of detected entities
     */
    public String[] redact_pii_entities(String input, String language, String redacttypes) throws Exception {
        return detect_pii_entities(input, language, redacttypes, false);
    }

    private String[] detect_pii_entities(String input, String language, String redacttypes, boolean fullResponse) throws Exception {
        //String[] result = new String[1];
        //if no split required then subInput == input
        String[] inputArray = splitLargeStrings(input);
        DetectPiiEntitiesRequest detectPiiEntitiesRequest;
        DetectPiiEntitiesResponse detectPiiEntitiesResponse;
        List<String> parentList = new ArrayList<String>();
        for(int inputCounter=0 ; inputCounter<inputArray.length ; inputCounter++) {
            String subInput = inputArray[inputCounter];
            detectPiiEntitiesRequest = DetectPiiEntitiesRequest.builder().text(subInput).languageCode(language).build();
            detectPiiEntitiesResponse = comprehendClient.detectPiiEntities(detectPiiEntitiesRequest);
            if(detectPiiEntitiesResponse.hasEntities()) {
                List<PiiEntity> piiEntityListForSubInput = detectPiiEntitiesResponse.entities();
                if (fullResponse) {
                    for (int i = 0; i < piiEntityListForSubInput.size(); i++) {
                        //["score=1,type=a,text=b,beginOffset=1,endOffset=2","score=1,type=a,text=b,beginOffset=1,endOffset=2"]
                        parentList.add("score=" + piiEntityListForSubInput.get(i).score().toString()
                                + ",type=" + piiEntityListForSubInput.get(i).type().toString()
                                + ",beginOffset=" + piiEntityListForSubInput.get(i).beginOffset().toString()
                                + ",endOffset=" + piiEntityListForSubInput.get(i).endOffset().toString());
                    }
                } else {
                    if (redacttypes.equals("[]")) {
                        //["ORGANIZATION","AWS"]
                        parentList.add(getPiiEntityTypesAndValues(piiEntityListForSubInput, input));
                    } else {
                        //["I work at [ORGANIZATION]"]
                        parentList.add(redactPiiEntityTypes(piiEntityListForSubInput, input, redacttypes));
                    }
                }
            }
        }
        String[] result = new String[parentList.size()];
        result = parentList.toArray(result);
        return result;
    }

    private String getPiiEntityTypesAndValues(List<PiiEntity> piiEntities, String text) throws Exception {
        List<String[]> typesAndValues = new ArrayList<String[]>();
        for (PiiEntity piiEntity : piiEntities) {
            String type = piiEntity.type().toString();
            String value = text.substring(piiEntity.beginOffset(), piiEntity.endOffset());
            typesAndValues.add(new String[]{type, value});
        }
        String resultjson = toJSON(typesAndValues);
        return resultjson;
    }
    private String redactPiiEntityTypes(List<PiiEntity> piiEntities, String text, String redactTypes) throws Exception {
        // redactTypes contains comma or space separated list of types, e.g. "NAME, ADDRESS"
        List<String> redactTypeList = Arrays.asList(redactTypes.split("[\\s,]+"));
        String result = text;
        int deltaLength = 0;
        for (PiiEntity piiEntity : piiEntities) {
            String type = piiEntity.type().toString();
            if (redactTypes.contains(type) || redactTypes.contains("ALL")) {
                // this is a PII type we need to redact
                // Offset logic assumes piiEntity list is ordered by occurance in string
                int start = piiEntity.beginOffset() + deltaLength;
                int end = piiEntity.endOffset() + deltaLength;
                int length1 = result.length();
                result = new String(result.substring(0, start) + "[" + type + "]" + result.substring(end));
                deltaLength = deltaLength + (result.length() - length1);
            }
        }
        return result;
    }

    private static String toJSON(Object obj) {
        Gson gson = new Gson();
        return gson.toJson(obj);
    }

    /**
     * For detect_dominant_language and detect_sentiment
     * */
    private static String truncateLargeStrings(String input) throws Exception{
        int textLength = getUtf8StringLength(input);
        boolean tooLong = textLength >= maxTextBytes;
        if (tooLong) {
            // truncate this row
            System.out.println("Truncating long text field (" + textLength + " bytes) to " + maxTextBytes + " bytes");
            input = truncateUtf8(input, maxTextBytes);
        }
        return input;
    }
    private static int getUtf8StringLength(String string) throws Exception {
        final byte[] utf8Bytes = string.getBytes("UTF-8");
        return (utf8Bytes.length);
    }
    /**
     * truncates a string to fit designated number of UTF-8 bytes
     * Needed to comply with Comprehend's input string limit of 5000 UTF-8 bytes
     * NOTE - not the same as String.length(), which counts (multi-byte) chars
     */
    private static String truncateUtf8(String string, int maxBytes) throws Exception {
        CharsetEncoder enc = StandardCharsets.UTF_8.newEncoder();
        ByteBuffer bb = ByteBuffer.allocate(maxBytes); // note the limit
        CharBuffer cb = CharBuffer.wrap(string);
        CoderResult r = enc.encode(cb, bb, true);
        if (r.isOverflow()) {
            string = cb.flip().toString();
        }
        return string;
    }

    /**
     * For remaining methods
     * */
    private static String[] splitLargeStrings(String input) throws Exception{
        String[] textSplit = new String[]{input};
        int textLength = getUtf8StringLength(input);
        boolean tooLong = textLength >= maxTextBytes;
        if(tooLong){
            textSplit = splitLongText(input, maxTextBytes);
            System.out.println("Split long text field (" + textLength + " bytes) into " + textSplit.length + " segments of under " + maxTextBytes + " bytes");
        }
        return textSplit;
    }
    private static String[] splitLongText(String longText, int maxTextBytes) throws Exception {
        String[] sentences = splitStringBySentence(longText);
        // recombine sentences up to maxTextBytes
        List<String> splitBatches = new ArrayList<String>();
        int bytesCnt = 0;
        int start = 0;
        for (int i = 0; i < sentences.length; i++) {
            int sentenceLength = getUtf8StringLength(sentences[i]);
            if (sentenceLength >= maxTextBytes) {
                System.out.println("DATA WARNING: sentence size (" + sentenceLength + " bytes) is larger than max (" + maxTextBytes + " bytes). Unsplittable.");
                System.out.println("Problematic sentence: " + sentences[i]);
                // TODO - Truncate, or drop?
            }
            bytesCnt += sentenceLength;
            if (bytesCnt >= maxTextBytes) {
                // join sentences prior to this one, and add to splitBatches. Reset counters.
                String splitBatch = String.join("", Arrays.copyOfRange(sentences, start, i));
                int splitBatchLength = getUtf8StringLength(splitBatch);
                if (splitBatchLength == 0 || splitBatchLength > maxTextBytes) {
                    System.out.println("DEBUG: Split size is " + splitBatchLength + " bytes - Skipping.");
                }
                else {
                    System.out.println("DEBUG: Split size (" + splitBatchLength + " bytes)");
                    splitBatches.add(splitBatch);
                }
                start = i;
                bytesCnt = getUtf8StringLength(sentences[i]);
            }
        }
        // last split
        if (start < sentences.length) {
            String splitBatch = String.join("", Arrays.copyOfRange(sentences, start, sentences.length));
            int splitBatchLength = getUtf8StringLength(splitBatch);
            if (splitBatchLength == 0 || splitBatchLength > maxTextBytes) {
                System.out.println("DEBUG: Split size is " + splitBatchLength + " bytes - Skipping.");
            }
            else {
                System.out.println("DEBUG: Split size (" + splitBatchLength + " bytes)");
                splitBatches.add(splitBatch);
            }
        }
        String[] splitArray = (String[]) splitBatches.toArray(new String[0]);
        return splitArray;
    }
    private static String[] splitStringBySentence(String longText) {
        BreakIterator boundary = BreakIterator.getSentenceInstance();
        boundary.setText(longText);
        List<String> sentencesList = new ArrayList<String>();
        int start = boundary.first();
        for (int end = boundary.next(); end != BreakIterator.DONE; start = end, end = boundary.next()) {
            sentencesList.add(longText.substring(start, end));
        }
        String[] sentenceArray = (String[]) sentencesList.toArray(new String[0]);
        return sentenceArray;
    }

    // java -cp target/text-analytics-udfs-linear-1.0.jar com.amazonaws.kinesis.udf.textanalytics.TextAnalyticsUDFHandler
    public static void main(String[] args) throws Exception {

        TextAnalyticsUDF textAnalyticsUDF = new TextAnalyticsUDF();

        System.out.println("\nDETECT DOMINANT LANGUAGE");
        String text = "I am Bob";
        System.out.println(textAnalyticsUDF.detect_dominant_language(text));
        text = "Je m'appelle Bob I am";
        System.out.println(textAnalyticsUDF.detect_dominant_language_all(text));

        System.out.println("\nDETECT SENTIMENT");
        text = "I am happy";
        String lang = "en";
        System.out.println(textAnalyticsUDF.detect_sentiment(text, lang));
        System.out.println(textAnalyticsUDF.detect_sentiment_all(text, lang));
        text = "She is sad";
        lang = "en";
        System.out.println(textAnalyticsUDF.detect_sentiment(text, lang));
        System.out.println(textAnalyticsUDF.detect_sentiment_all(text, lang));
        text = "ce n'est pas bon";
        lang = "fr";
        System.out.println(textAnalyticsUDF.detect_sentiment(text, lang));
        System.out.println(textAnalyticsUDF.detect_sentiment_all(text, lang));
        text = "Je l'aime beaucoup";
        lang = "fr";
        System.out.println(textAnalyticsUDF.detect_sentiment(text, lang));
        System.out.println(textAnalyticsUDF.detect_sentiment_all(text, lang));


        System.out.println("\nTRANSLATE TEXT");
        String sourcelang = "auto";
        String targetlang = "fr";
        String terminologyNames = "null";

        text = "I am Bob";
        System.out.println(textAnalyticsUDF.translate_text(text, sourcelang, targetlang, terminologyNames));

        text = "I live in Herndon";
        System.out.println(textAnalyticsUDF.translate_text(text, sourcelang, targetlang, terminologyNames));

        text = "I love to visit France";
        System.out.println(textAnalyticsUDF.translate_text(text, sourcelang, targetlang, terminologyNames));

        System.out.println("\nDETECT / REDACT ENTITIES");
        text = "I am Bob";
        lang = "en";
        System.out.println(textAnalyticsUDF.detect_entities(text, lang));
        System.out.println(textAnalyticsUDF.detect_entities_all(text, lang));
        System.out.println(textAnalyticsUDF.redact_entities(text, lang, "ALL"));

        System.out.println("\n==========");

        text =  "I live in Herndon";
        lang = "fr";
        System.out.println(textAnalyticsUDF.detect_entities(text, lang));
        System.out.println(textAnalyticsUDF.detect_entities_all(text, lang));
        System.out.println(textAnalyticsUDF.redact_entities(text, lang, "ALL"));

        System.out.println("\n==========");

        text = "Je suis Bob et j'habite à Herndon";
        lang = "es";
        System.out.println(textAnalyticsUDF.detect_entities(text, lang));
        System.out.println(textAnalyticsUDF.detect_entities_all(text, lang));
        System.out.println(textAnalyticsUDF.redact_entities(text, lang, "ALL"));

        System.out.println("\nDETECT / REDACT PII ENTITIES");
        text = "I am Bob";
        lang = "en";
        System.out.println(textAnalyticsUDF.detect_pii_entities(text, lang));
        System.out.println(textAnalyticsUDF.detect_pii_entities_all(text, lang));
        System.out.println(textAnalyticsUDF.redact_pii_entities(text, lang, "ALL"));

        text = "I live in Herndon";
        lang = "en";
        System.out.println(textAnalyticsUDF.detect_pii_entities(text, lang));
        System.out.println(textAnalyticsUDF.detect_pii_entities_all(text, lang));
        System.out.println(textAnalyticsUDF.redact_pii_entities(text, lang, "ALL"));

    }
}