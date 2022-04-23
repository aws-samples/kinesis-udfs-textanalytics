# Sample UDFs for text translation and analytics using Amazon Comprehend and Amazon Translate

This package provides a java class ``TextAnalyticsUDF`` with public methods to be used in Kinesis Data Analytics as User Defined Functions. The use-case acheived with these public methods are : 

- Detection of prevailing sentiment (positive/negative/neither/both),
- Detection of dominant language
- Language translation
- Detection and redaction of entities (such as items, places, or quantities)
- Detection and redaction of PII entities

## How the UDF works

The Java class [``TextAnalyticsUDF``](https://github.com/aws-samples/kinesis-udfs-textanalytics/blob/main/kinesis-udfs-textanalytics-linear/src/main/java/com/amazonaws/kinesis/udf/textanalytics/TextAnalyticsUDF.java) extends [``ScalarFunction``](https://nightlies.apache.org/flink/flink-docs-release-1.11/api/java/org/apache/flink/table/functions/ScalarFunction.html) to allow invocation from Kinesis Data Analytics for Flink on a per record basis. The required ``eval`` method is then overloaded to receive input records, identifier for the use-case to perform and other supporting meta-data for the use-case. A switch case within the eval methods then maps the input record to a corresponding public method. Within these public methods, use-case specific API calls of Amazon Comprehendand, AmazonTranslate are triggered, for example [DetectSentiment](https://docs.aws.amazon.com/comprehend/latest/dg/API_DetectSentiment.html), [DetectDominantLanguage](https://docs.aws.amazon.com/comprehend/latest/dg/API_DetectDominantLanguage.html), [TranslateText](https://docs.aws.amazon.com/translate/latest/dg/API_TranslateText.html) etc.

Amazon Comprehend and Amazon Translate each enforce a maximum input string length of 5,000 utf-8 bytes. Text fields that are longer than 5,000 utf-8 bytes are truncated to 5,000 bytes for language and sentiment detection, and split on sentence boundaries into multiple text blocks of under 5,000 bytes for translation and entity or PII detection and redaction. The results are then combined.

## Build from source

1. Navigate to ``kinesis-udfs-textanalytics/kinesis-udfs-textanalytics-linear`` directory
2. Execute `mvn clean install`

A successful build populates the JAR as ````kinesis-udfs-textanalytics/kinesis-udfs-textanalytics-linear/target/text-analytics-udfs-linear-1.0.jar```` 
