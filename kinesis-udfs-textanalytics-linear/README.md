# Sample UDFs for text translation and analytics using Amazon Comprehend and Amazon Translate

This package provides a java class with public methods to be used in Kinesis Data Analytics User Defined Functions (i) text translation between languages using Amazon Translate, (ii) text analytics including detection of language, sentiment, entities and PII using Amazon Comprehend, and (iii) redaction of detected entities and PII.

#### Build from source

1. From the kinesis-udfs-textanalytics dir, run `mvn clean install`.
2. Run built-in tests using the class main method: run `java -cp target/text-analytics-udfs-linear-1.0.jar com.amazonaws.kinesis.udf.textanalytics.TextAnalyticsUDFHandler` 



#### How the UDF works

