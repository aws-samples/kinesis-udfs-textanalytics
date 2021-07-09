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

import software.amazon.awssdk.core.retry.backoff.EqualJitterBackoffStrategy;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.translate.TranslateClient;
import software.amazon.awssdk.services.translate.model.TranslateTextRequest;
import software.amazon.awssdk.services.translate.model.TranslateTextResponse;
import software.amazon.awssdk.services.translate.model.TranslateException;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.BatchDetectDominantLanguageItemResult;
import software.amazon.awssdk.services.comprehend.model.BatchDetectDominantLanguageRequest;
import software.amazon.awssdk.services.comprehend.model.BatchDetectDominantLanguageResponse;
import software.amazon.awssdk.services.comprehend.model.BatchItemError;
import software.amazon.awssdk.services.comprehend.model.DominantLanguage;

import com.google.gson.Gson;
import org.apache.log4j.varia.NullAppender;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TextAnalyticsUDFHandler 
{
    public static int maxTextBytes = 5000;  //utf8 bytes
    public static int maxBatchSize = 25;
    
    private TranslateClient translateClient;
    private ComprehendClient comprehendClient;

    private ClientOverrideConfiguration createClientOverrideConfiguration()
    {
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
    private ComprehendClient getComprehendClient() 
    {
        // create client first time on demand
        if (this.comprehendClient == null) {
            System.out.println("Creating Comprehend client connection");
            this.comprehendClient = ComprehendClient.builder()
                .overrideConfiguration(createClientOverrideConfiguration())
                .build();
        }
        return this.comprehendClient;
    }
    private TranslateClient getTranslateClient() 
    {
        // create client first time on demand
        if (this.translateClient == null) {
            System.out.println("Creating Translate client connection");
            this.translateClient = TranslateClient.builder()
                .overrideConfiguration(createClientOverrideConfiguration())
                .build();
        }
        return this.translateClient;
    }

    public TextAnalyticsUDFHandler()
    {
        System.out.println("Initializing TextAnalyticsUDFHandler");
    }

    /**
     * DETECT DOMINANT LANGUAGE
     * ========================
     **/

    /**
    * Given a JSON array of input strings returns a JSON array of language codes representing the detected dominant language of each input string
    * @param    inputjson   a JSON array of input strings
    * @return   a JSON array of language code string values
    */
    public String detect_dominant_language(String inputjson) throws Exception
    {
        return detect_dominant_language(inputjson, false);
    }
    
    /**
    * Given a JSON array of input strings returns a JSON array of nested objects representing detected language code and confidence score for each input string
    * @param    inputjson   a JSON array of input strings
    * @return   a JSON array of nested objects with detect_dominant_language results for each input string
    */
    public String detect_dominant_language_all(String inputjson) throws Exception
    {
        return detect_dominant_language(inputjson, true);
    }   
    
    private String detect_dominant_language(String inputjson, boolean fullResponse) throws Exception
    {
        // convert input string to array
        String[] input = fromJSON(inputjson);
        
        // batch input records
        int rowCount = input.length;
        String[] result = new String[rowCount];
        int rowNum = 0;
        boolean splitLongText = false; // truncate, don't split long text fields.
        for (Object[] batch : getBatches(input, this.maxBatchSize, this.maxTextBytes, splitLongText)) {
            String[] textArray = (String[]) batch[0];
            String singleRowOrMultiRow = (String) batch[1];
            if (! singleRowOrMultiRow.equals("MULTI_ROW_BATCH")) {
                throw new RuntimeException("Error:  - Expected multirow batches only (truncate, not split): " + singleRowOrMultiRow);
            }
            System.out.println("DEBUG: Call comprehend BatchDetectDominantLanguage API - Split Batch => Records: " + textArray.length);
            // Call batchDetectDominantLanguage API
            BatchDetectDominantLanguageRequest batchDetectDominantLanguageRequest = BatchDetectDominantLanguageRequest.builder()
                    .textList(textArray)
                    .build();
            BatchDetectDominantLanguageResponse batchDetectDominantLanguageResponse = getComprehendClient().batchDetectDominantLanguage(batchDetectDominantLanguageRequest);
            // Throw exception if errorList is populated
            List<BatchItemError> batchItemError = batchDetectDominantLanguageResponse.errorList();
            if (! batchItemError.isEmpty()) {
                throw new RuntimeException("Error:  - ErrorList in batchDetectDominantLanguage result: " + batchItemError);
            }
            List<BatchDetectDominantLanguageItemResult> batchDetectDominantLanguageItemResult = batchDetectDominantLanguageResponse.resultList(); 
            for (int i = 0; i < batchDetectDominantLanguageItemResult.size(); i++) {
                if (fullResponse) {
                    // return JSON structure containing array of all detected languageCodes and scores
                    result[rowNum] = this.toJSON(batchDetectDominantLanguageItemResult.get(i).languages());
                }
                else {
                    // return simple string containing the languageCode of the first (most confident) language
                    result[rowNum] = batchDetectDominantLanguageItemResult.get(i).languages().get(0).languageCode();
                }
                rowNum++;
            }
        }
        // Convert output array to JSON string
        String resultjson = toJSON(result);
        return resultjson;
    }

    /**
     * TRANSLATE TEXT
     */
     
    /**
    * Given a JSON array of input strings, source language, target language, and optional terminaology names, returns a JSON array of translated strings
    * @param    inputjson    a JSON array of input strings
    * @param    sourcelanguagejson a JSON array of source language codes corresponding to each input string (source lang can be 'auto' if source language is unknown)
    * @param    targetlanguagejson a JSON array of target language codes
    * @param    terminologynamesjson a JSON array of custom terminology names in Amazon Translate (or 'NULL' if CT is not to be applied)
    * @return   a JSON array of translated string values
    */
    public String translate_text(String inputjson, String sourcelanguagejson, String targetlanguagejson, String terminologynamesjson) throws Exception
    {
        // convert input args to arrays
        String[] input = fromJSON(inputjson);
        String[] sourceLanguageCodes = fromJSON(sourcelanguagejson);
        String[] targetLanguageCodes = fromJSON(targetlanguagejson);
        String[] terminologyNames = fromJSON(terminologynamesjson);
        // batch input records
        int rowCount = input.length;
        String[] result = new String[rowCount];
        int rowNum = 0;
        boolean splitLongText = true; // split long text fields, don't truncate.
        for (Object[] batch : getBatches(input, this.maxBatchSize, this.maxTextBytes, splitLongText)) {
            String[] textArray = (String[]) batch[0];
            String singleRowOrMultiRow = (String) batch[1];
            if (singleRowOrMultiRow.equals("MULTI_ROW_BATCH")) {
                // batchArray represents multiple output rows, one element per output row
                System.out.println("DEBUG: Call MultiRowBatchTranslateText Translatetext API - Batch => Records: " + textArray.length);
                String[] sourceLanguageCodesSubset = Arrays.copyOfRange(sourceLanguageCodes, rowNum, rowNum + textArray.length);
                String[] targetLanguageCodesSubset = Arrays.copyOfRange(targetLanguageCodes, rowNum, rowNum + textArray.length);
                String[] terminologyNamesSubset = Arrays.copyOfRange(terminologyNames, rowNum, rowNum + textArray.length);
                String[] multiRowResults = MultiRowBatchTranslateText(textArray, sourceLanguageCodesSubset, targetLanguageCodesSubset, terminologyNamesSubset);
                for (int i = 0; i < multiRowResults.length; i++) {
                    result[rowNum++] = multiRowResults[i];
                }
            }
            else {
                // batchArray represents single output row (long text split)
                System.out.println("DEBUG: Call TextSplitBatchTranslateText Translatetext API - Batch => Records: " + textArray.length);
                String sourceLanguageCode = sourceLanguageCodes[rowNum];
                String targetLanguageCode = targetLanguageCodes[rowNum];
                String terminologyName = terminologyNames[rowNum];
                String singleRowResults = TextSplitBatchTranslateText(textArray, sourceLanguageCode, targetLanguageCode, terminologyName);
                result[rowNum++] = singleRowResults;
            }
        }
        // Convert output array to JSON string
        String resultjson = toJSON(result);
        return resultjson;
    }
    private String[] MultiRowBatchTranslateText(String[] batch, String[] sourceLanguageCodesSubset, String[] targetLanguageCodesSubset, String[] terminologyNamesSubset) throws Exception
    {
        String[] result = new String[batch.length];
        // Call translateText API in loop  (no multidocument batch API available)
        for (int i = 0; i < batch.length; i++) {
            TranslateTextRequest translateTextRequest = TranslateTextRequest.builder()
                .sourceLanguageCode(sourceLanguageCodesSubset[i])
                .targetLanguageCode(targetLanguageCodesSubset[i])
                .text(batch[i])
                .build();
            if (! terminologyNamesSubset[i].equals("null")) {
                translateTextRequest = translateTextRequest.toBuilder().terminologyNames(terminologyNamesSubset[i]).build();
            }
            try {
                TranslateTextResponse translateTextResponse = getTranslateClient().translateText(translateTextRequest);
                String translatedText = translateTextResponse.translatedText();  
                result[i] = translatedText;
            } 
            catch (Exception e) {
                System.out.println("ERROR: Translate API Exception.\nInput String size: " + getUtf8StringLength(batch[i]) + " bytes. String:\n" + batch[i]);
                System.out.println("EXCEPTION:\n" + e);
                // return input text untranslated
                result[i] = batch[i];
            }
        }
        return result;
    }
    private String TextSplitBatchTranslateText(String[] batch, String sourceLanguageCode, String targetLanguageCode, String terminologyName) throws Exception
    {
        String[] result = new String[batch.length];
        // Call translateText API in loop  (no multidocument Translate API available)
        for (int i = 0; i < batch.length; i++) {
            TranslateTextRequest translateTextRequest = TranslateTextRequest.builder()
                .sourceLanguageCode(sourceLanguageCode)
                .targetLanguageCode(targetLanguageCode)
                .text(batch[i])
                .build();
            if (! terminologyName.equals("null")) {
                translateTextRequest = translateTextRequest.toBuilder().terminologyNames(terminologyName).build();
            }
            try {
                TranslateTextResponse translateTextResponse = getTranslateClient().translateText(translateTextRequest);
                String translatedText = translateTextResponse.translatedText();  
                result[i] = translatedText;
            } 
            catch (Exception e) {
                System.out.println("ERROR: Translate API Exception.\nInput String size: " + getUtf8StringLength(batch[i]) + " bytes. String:\n" + batch[i]);
                System.out.println("EXCEPTION:\n" + e);
                // return input text untranslated
                result[i] = batch[i];
            }
        }
        // merge results to single output row
        String mergedResult = mergeText(result);
        return mergedResult;
    }       
    
    /**
     * PRIVATE HELPER METHODS
     * 
     */
     
    // merges multiple results from detectEntities or detectPiiEntities into a single string
    private static String mergeEntities(String[] arrayOfJson) throws Exception
    {
        JSONArray resultArray = new JSONArray();
        for (int i = 0; i < arrayOfJson.length; i++) {
            JSONArray entities = new JSONArray(arrayOfJson[i]);
            resultArray.putAll(entities);
        }
        return resultArray.toString();
    }
    // merges multiple results from detectEntities_all or detectPiiEntities_all into a single string
    // apply offsets to the beginOffset and endOffset members of each detected entity
    private static String mergeEntitiesAll(String[] arrayOfJson, int[] offset) throws Exception
    {
        JSONArray resultArray = new JSONArray();
        for (int i = 0; i < arrayOfJson.length; i++) {
            JSONArray entities = new JSONArray(arrayOfJson[i]);
            JSONArray entityResultWithOffset = applyOffset(entities, offset[i]);
            resultArray.putAll(entities);
        }
        return resultArray.toString();   
    }
    // merges multiple results from redactEntities or redactPiiEntities_all into a single string
    private static String mergeText(String[] arrayOfStrings) throws Exception
    {
        return (String.join("", arrayOfStrings));
    }
    // apply offset to the values of beginOffset and endOffset in each result, so that they match the original long input text
    private static JSONArray applyOffset(JSONArray entities, int offset) throws Exception
    {
        System.out.println("Entities DEBUG: " + entities);
        int size = entities.length();
        for (int i = 0; i < size; i++) {
            JSONObject entity = entities.getJSONObject(i);
            int beginOffset = entity.getInt("beginOffset");
            int endOffset = entity.getInt("endOffset");
            entity.put("beginOffset", beginOffset + offset);
            entity.put("endOffset", endOffset + offset);
        }
        return entities;
    }
    
    // splits input array into batches no larger than multiDocBatchSize
    private List<Object[]> getBatches(String[] input, int multiRowBatchSize)
        throws Exception
    {
        List<Object[]> batches = new ArrayList<Object[]>();
        int start = 0;
        int c = 0;
        for (int i = 0; i < input.length; i++) {
            if (c++ >= multiRowBatchSize) {
                // add a batch, and reset c
                batches.add(new Object[] {Arrays.copyOfRange(input, start, i), "MULTI_ROW_BATCH"});
                start = i;
                c = 1;
            }
        }
        // last split
        if (start < input.length) {
            batches.add(new Object[] {Arrays.copyOfRange(input, start, input.length), "MULTI_ROW_BATCH"});
        }
        return batches;        
    }
    // as above, but also checks utf-8 byte size for input and can return batch for single input record containing splits
    private List<Object[]> getBatches(String[] input, int multiRowBatchSize, int maxTextBytes, boolean splitLongText)
        throws Exception
    {
        List<Object[]> batches = new ArrayList<Object[]>();
        int start = 0;
        int c = 0;
        for (int i = 0; i < input.length; i++) {
            if (c++ >= multiRowBatchSize) {
                // add a batch (not including current row), and reset c
                batches.add(new Object[] {Arrays.copyOfRange(input, start, i), "MULTI_ROW_BATCH"});
                start = i;
                c = 1;
            }
            int textLength = getUtf8StringLength(input[i]);
            boolean tooLong = (textLength >= maxTextBytes) ? true : false;
            if (tooLong && !splitLongText) {
                // truncate this row
                System.out.println("Truncating long text field (" + textLength + " bytes) to " + maxTextBytes + " bytes");
                input[i] = truncateUtf8(input[i], maxTextBytes);
            }
            if (tooLong && splitLongText) {
                // close off current multi-record batch before making new single record batch
                if (start < i) {
                    batches.add(new Object[] {Arrays.copyOfRange(input, start, i), "MULTI_ROW_BATCH"});
                }
                // split this row and add the text splits as a new *TEXT_SPLIT_BATCH* batch
                String[] textSplit = splitLongText(input[i], maxTextBytes);
                System.out.println("Split long text field (" + textLength + " bytes) into " + textSplit.length + " segments of under " + maxTextBytes + " bytes");
                batches.add(new Object[] {textSplit, "TEXT_SPLIT_BATCH"});
                // increment counters for next row / next batch
                start = i + 1;
                c = 1;                 
            }            
        }
        // last multi-record split
        if (start < input.length) {
            batches.add(new Object[] {Arrays.copyOfRange(input, start, input.length), "MULTI_ROW_BATCH"});
        }
        return batches;         
    }

    // as above, but also splits input array into batches representing one language only
    private List<Object[]> getBatches(String[] input, String[] languageCodes, int multiRowBatchSize, int maxTextBytes, boolean splitLongText)
        throws Exception
    {
        List<Object[]> batches = new ArrayList<Object[]>();
        String languageCode = languageCodes[0];
        int start = 0;
        int c = 0;
        for (int i = 0; i < input.length; i++) {
            if (c++ >= multiRowBatchSize || ! languageCode.equals(languageCodes[i])) {
                // add a batch (not including current row), and reset c
                batches.add(new Object[] {Arrays.copyOfRange(input, start, i), "MULTI_ROW_BATCH", languageCode});
                languageCode = languageCodes[i];
                start = i;
                c = 1;
            }
            int textLength = getUtf8StringLength(input[i]);
            boolean tooLong = (textLength > maxTextBytes) ? true : false;
            if (tooLong && !splitLongText) {
                // truncate this row
                System.out.println("Truncating long text field (" + textLength + " bytes) to " + maxTextBytes + " bytes");
                input[i] = truncateUtf8(input[i], maxTextBytes);
            }
            if (tooLong && splitLongText) {
                // close off current multi-record batch before making new single record batch
                if (start < i) {
                    batches.add(new Object[] {Arrays.copyOfRange(input, start, i), "MULTI_ROW_BATCH", languageCode});
                }
                // split this row and add the text splits as a new *TEXT_SPLIT_BATCH* batch
                String[] textSplit = splitLongText(input[i], maxTextBytes);
                System.out.println("Split long text field (" + textLength + " bytes) into " + textSplit.length + " segments of under " + maxTextBytes + " bytes");
                batches.add(new Object[] {textSplit, "TEXT_SPLIT_BATCH", languageCode});
                // increment counters for next row / next batch
                start = i + 1;
                c = 1;
                if (i < input.length) {
                    languageCode = languageCodes[i];
                }
            } 
        }
        // last multi-record split
        if (start < input.length) {
            batches.add(new Object[] {Arrays.copyOfRange(input, start, input.length), "MULTI_ROW_BATCH", languageCode});
        }
        return batches;          
    }

    private static int getUtf8StringLength(String string) throws Exception
    {
        final byte[] utf8Bytes = string.getBytes("UTF-8");
        return (utf8Bytes.length);        
    }

    /**
     * truncates a string to fit designated number of UTF-8 bytes
     * Needed to comply with Comprehend's input string limit of 5000 UTF-8 bytes
     * NOTE - not the same as String.length(), which counts (multi-byte) chars
     */
    private static String truncateUtf8(String string, int maxBytes) throws Exception
    {
        CharsetEncoder enc = StandardCharsets.UTF_8.newEncoder();
        ByteBuffer bb = ByteBuffer.allocate(maxBytes); // note the limit
        CharBuffer cb = CharBuffer.wrap(string);
        CoderResult r = enc.encode(cb, bb, true);
        if (r.isOverflow()) {
            string = cb.flip().toString();
        }
        return string;
    }

    private static String[] splitLongText(String longText, int maxTextBytes) throws Exception
    {
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

    private static String[] splitStringBySentence(String longText) 
    {
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
 
    private static String toJSON(Object obj) 
    {
        Gson gson = new Gson();
        return gson.toJson(obj);
    }

    private static String[] fromJSON(String json) 
    {
        Gson gson = new Gson();
        return gson.fromJson(json, String[].class);
    }


    
    /**
     * Testing
     **/
     
    static void runStringLengthTests() throws Exception
    {
        String longText = "je déteste ça et je m'appelle Bob";
        System.out.println("Original text: " + longText + "\nOriginal length bytes: " + getUtf8StringLength(longText) + " Original length chars: " + longText.length());
        String truncated = truncateUtf8(longText, 20);
        System.out.println("Truncated text: " + truncated + "\nNew length bytes: " + getUtf8StringLength(truncated) + " New length chars: " + truncated.length());
    }
    
    static void runSplitLongTextTest() throws Exception
    {
        int maxTextBytes = 70;
        String longText = "My name is Jeremiah. I live in Anytown, USA. I am 35 years old. I am 5'7\" tall. I love cars, and dogs. My SSN is 123-45-6789. My cell is (707)555-1234.";
        System.out.println("Test slitting long text blocks to under " + maxTextBytes + " UTF-8 bytes");
        String[] splits = splitLongText(longText, maxTextBytes);
        System.out.println("Split of long text: \n" + String.join("\n", splits));
    }
    
    
    
    static void runMergeEntitiesTests() throws Exception
    {
        String[] arrayOfJsonObjects = new String[] {
            "[{\"type\":\"NAME\",\"beginOffset\":1,\"endOffset\":5},{\"type\":\"ADDRESS\",\"beginOffset\":5,\"endOffset\":10}]",
            "[{\"type\":\"NAME\",\"beginOffset\":1,\"endOffset\":5},{\"type\":\"ADDRESS\",\"beginOffset\":5,\"endOffset\":10}]",
            "[{\"type\":\"NAME\",\"beginOffset\":1,\"endOffset\":5}]"
        };
        int[] offset = new int[] {0, 10, 20};
        System.out.println(mergeEntitiesAll(arrayOfJsonObjects, offset));
        String[] arrayOfJsonArrays = new String[] {
            "[[\"PERSON\",\"Bob\"],[\"COMMERCIAL_ITEM\",\"Pixel 5\"]]",
            "[[\"PERSON\",\"Jim\"],[\"COMMERCIAL_ITEM\",\"Pixel 2XL\"]]",
            "[[\"PERSON\",\"Rob\"]]"
        };
        System.out.println(mergeEntities(arrayOfJsonArrays)); 
        int maxTextBytes = 70;
        String longText = "My name is Jeremiah. I live in Anytown, USA. I am 35 years old. I am 5'7\" tall. I love cars, and dogs. My SSN is 123-45-6789. My cell is (707)555-1234.";
        String[] arrayOfJsonStrings = splitLongText(longText, maxTextBytes);
        System.out.println(mergeText(arrayOfJsonStrings));  
    }
    
    static String makeJsonArray(String text, int len)
    {
        String[] textArray = new String[len];
        for (int i = 0; i < len; i++) {
            textArray[i] = text;
        } 
        return toJSON(textArray);
    }
    
    // java -cp target/textanalyticsudfs-1.0.jar com.amazonaws.kinesis.udf.textanalytics.TextAnalyticsUDFHandler
    public static void main(String[] args) throws Exception
    {
        org.apache.log4j.BasicConfigurator.configure(new NullAppender());
        
        TextAnalyticsUDFHandler textAnalyticsUDFHandler = new TextAnalyticsUDFHandler();

        System.out.println("\nSPLIT LONG TEXT BLOCKS");
        runSplitLongTextTest();
        
        System.out.println("\nUTF-8 STRING LENGTH TESTS");
        runStringLengthTests();
        
        System.out.println("\nMERGE RESULTS TESTS");
        runMergeEntitiesTests();
        
        String textJSON;
        String langJSON;

        String result;
        System.out.println("\nDETECT DOMINANT LANGUAGE");
        textJSON = toJSON(new String[]{"I am Bob", "Je m'appelle Bob"});
        // check logs for evidence of 1 batch with 2 items
        System.out.println("detect_dominant_language - 2 rows:" + textJSON);
        System.out.println(textAnalyticsUDFHandler.detect_dominant_language(textJSON));
        System.out.println("detect_dominant_language_all - 2 rows:" + textJSON);
        System.out.println(textAnalyticsUDFHandler.detect_dominant_language_all(textJSON));
        
        System.out.println("\nTRANSLATE TEXT");
        textJSON = toJSON(new String[]{"I am Bob, I live in Herndon", "I love to visit France"});
        String sourcelangJSON = toJSON(new String[]{"en", "en"});
        String targetlangJSON = toJSON(new String[]{"fr", "fr"});
        String terminologyNamesJSON = toJSON(new String[]{"null", "null"});
        System.out.println("translate_text - 1 row: " + textJSON);
        System.out.println(textAnalyticsUDFHandler.translate_text(textJSON, sourcelangJSON, targetlangJSON, terminologyNamesJSON));
        
    }
}
