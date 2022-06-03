# Translate and analyze streaming data using SQL functions with Amazon Kinesis Data Analytics, Amazon Translate, and Amazon Comprehend

This example will show you how to leverage Amazon Kinesis Data Analytics Studio powered by Apache Zeppelin and Apache Flink to
interactively analyze, translate, and redact streaming using Amazon Translate & Amazon Comprehend APIs. The approach involves creation of [Apache Flink user-defined functions](https://nightlies.apache.org/flink/flink-docs-release-1.11/dev/table/functions/udfs.html#user-defined-functions) (UDFs) to trigger these APIs. For additional information, refer to blog post: www.amazon.com/kinesis-textanalyticsudf

<img width="917" alt="Architecture" src="https://user-images.githubusercontent.com/46901738/151387452-1c3683ca-7571-4997-83fc-d0a2029ec9d1.png">

Related AWS Blog : link-here
    
This repository contains following artifacts :

1. Example Apache Zeppelin notebooks under ``example-notebooks`` directory
2. Trimmed down version of [Amazon Product reviews](https://s3.amazonaws.com/amazon-reviews-pds/readme.html) dataset under ``example-trimmed-datasets`` directory
3. Java project for UDF under ``kinesis-udfs-textanalytics-linear`` directory
4. Cloudformation template to automate deployments under ``cloudformation-stack`` directory
    
Repository additionally contains ``custom_artifacts_helper.py`` python script to enable a customized setup (refer [below](https://github.com/aws-samples/kinesis-udfs-textanalytics#user-content-customized-setup-))
	
## Example output on Amazon Product Reviews data-set :

   Note : For example output on non-streaming static data, refer [below](https://github.com/aws-samples/kinesis-udfs-textanalytics/blob/main/README.md#user-content-example-output-on-non-streaming-static-data-) 

Sentiment detection and language translation :

<img width="907" alt="Screenshot 2022-04-25 at 9 44 19 PM" src="https://user-images.githubusercontent.com/46901738/165130215-434398f5-4826-423c-8d9e-e843c37875cd.png">

Entity detection :

<img width="917" alt="Architecture" src="https://user-images.githubusercontent.com/46901738/170194178-cf14aab4-078a-4d24-b9ec-7c5da6ab83b6.PNG">
   
Entity Redaction :

<img width="917" alt="Architecture" src="https://user-images.githubusercontent.com/46901738/164889236-0dba919d-333e-4e30-baf5-d73520149488.png">

PII entity Redaction :

<img width="917" alt="Architecture" src="https://user-images.githubusercontent.com/46901738/164889232-90045688-76cf-47f1-8ba1-fcb413bc81ae.png">

## Setup guide :

### Default setup  : 

1. Clone repo locally using ``git clone https://github.com/aws-samples/kinesis-udfs-textanalytics.git``  
2. Choose ``cloudformation-stack/KDA_StudioNotebook_with_UDF.yaml`` template [to execute a cloudformation stack in your AWS account](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-template.html). This approach will pull default artifacts from public AWS S3 bucket ``aws-blogs-artifacts-public`` which includes the pre-built JAR for the UDF JAVA project under ``kinesis-udfs-textanalytics-linear`` directory. 


### Customized setup :
If you need to deploy customized artifacts, perform following :

1. Clone repo locally using ``git clone https://github.com/aws-samples/kinesis-udfs-textanalytics.git``  
    
2. Modify the contents of artifacts locally as required. To create JAR, refer Create JAR section of [project README](https://github.com/aws-samples/kinesis-udfs-textanalytics/blob/main/kinesis-udfs-textanalytics-linear/README.md)

3. Once modifications are done, execute ``custom_artifacts_helper.py`` python script which programmatically uploads these modified artifacts to a specified S3 bucket within your account and also updates the [Parameter](https://github.com/aws-samples/kinesis-udfs-textanalytics/blob/2f54bc2ca83719c03d0565e49da864df2baebab9/cloudformation-stack/KDA_StudioNotebook_with_UDF.yaml#L8) section of Cloudformation template.

      Note : The ``custom_artifacts_helper.py`` python script scans for artifacts by their default names in current directory, so during content modification ensure the name of artifacts remain same. Also, the script requires the IAM entitiy to have [Read and Write access](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_examples_s3_rw-bucket.html) to S3 bucket. 

4. Confirm creation of modified Cloudformation stack template as ``/<your-specified-prefiex>/KDA_StudioNotebook_with_UDF.yaml`` along with other artifacts in your specified S3 bucket. Copy the Object URL of this template object from Amazon S3 console and [use it to execute a cloudformation stack](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-template.html) in your AWS account.

## Example output on non-streaming static data :

### Detect language :

This function uses the Amazon Comprehend [DetectDominantLanguage](https://docs.aws.amazon.com/comprehend/latest/dg/API_DetectDominantLanguage.html) API to identify the dominant language and return a language code, such as fr for French or en for English:

```
%flink.ssql(type=update)
SELECT
    message,
    TextAnalyticsUDF('detect_dominant_language', message) as detected_language
FROM 
	(
	VALUES
        ('I am very happy')
    )AS NameTable(message)
;

    message     | detected_language
=====================================
I am very happy | en
```

The following code returns a comma-separated string of language codes and corresponding confidence scores:

```
%flink.ssql(type=update)
SELECT
    message,
    TextAnalyticsUDF('detect_dominant_language_all', message) as detected_language
FROM 
	(
	VALUES
        ('I am very happy et joyeux')
    )AS NameTable(message)
;

              message     |               detected_language
============================================================================
I am very happy et joyeux | lang_code=fr,score=0.5603816,lang_code=en,score=0.30602336
```

### Detect sentiment : 

This function uses the Amazon Comprehend [DetectSentiment](https://docs.aws.amazon.com/comprehend/latest/dg/API_DetectSentiment.html) API to identify the sentiment and return results as POSITIVE, NEGATIVE, NEUTRAL, or MIXED:

```
%flink.ssql(type=update)
SELECT
message,
TextAnalyticsUDF(‘detect_sentiment’, message, ‘en’) as sentiment
FROM 
	(
	VALUES
               (‘I am very happy’)
)AS NameTable(message)
;

    message     | sentiment
================================
I am very happy | [POSITIVE]
```

The following code returns a comma-separated string containing detected sentiment and confidence scores for each sentiment value:

```
%flink.ssql(type=update)
SELECT
    message,
    TextAnalyticsUDF('detect_sentiment_all', message, 'en') as sentiment
FROM 
	(
	VALUES
        ('I am very happy')
    )AS NameTable(message)
;

    message     | sentiment
=============================================================================================================================================
I am very happy | [sentiment=POSITIVE,positiveScore=0.999519,negativetiveScore=7.407639E-5,neutralScore=2.7478999E-4,mixedScore=1.3210243E-4]
```

### Detect entities :

This function uses the Amazon Comprehend [DetectEntities](https://docs.aws.amazon.com/comprehend/latest/dg/how-entities.html) API to identify entities:
```
%flink.ssql(type=update)
SELECT
    message,
    TextAnalyticsUDF('detect_entities', message, 'en') as entities
FROM 
	(
	VALUES
        ('I am Bob, I live in Herndon VA, and I love cars')
    )AS NameTable(message)
;

		                     message    |              entities
=============================================================================================
I am Bob, I live in Herndon VA, and I love cars | [[["PERSON","Bob"],["LOCATION","Herndon VA"]]]
```

The following code returns a comma-separated string containing [entity types](https://docs.aws.amazon.com/comprehend/latest/dg/how-entities.html) and values:

```
%flink.ssql(type=update)
SELECT
    message,
    TextAnalyticsUDF('detect_entities_all', message, 'en') as entities
FROM 
	(
	VALUES
        ('I am Bob, I live in Herndon VA, and I love cars')
    )AS NameTable(message)
;

		                     message    |              entities
==========================================================================================================================================================================================
I am Bob, I live in Herndon VA, and I love cars |[score=0.9976127,type=PERSON,text=Bob,beginOffset=5,endOffset=8, score=0.995559,type=LOCATION,text=Herndon VA,beginOffset=20,endOffset=30]
```

### Detect PII entities :

This function uses the [DetectPiiEntities](https://docs.aws.amazon.com/comprehend/latest/dg/how-pii.html) API to identify PII:

```
%flink.ssql(type=update)
SELECT
    message,
    TextAnalyticsUDF('detect_pii_entities', message, 'en') as pii_entities
FROM 
	(
	VALUES
        ('I am Bob, I live in Herndon VA, and I love cars')
    )AS NameTable(message)
;

		       message    |              pii_entities
=============================================================================================
I am Bob, I live in Herndon VA, and I love cars | [[["NAME","Bob"],["ADDRESS","Herndon VA"]]]
```

The following code returns a comma-separated string containing PII entity types, with their scores and character offsets:

```
%flink.ssql(type=update)
SELECT
    message,
    TextAnalyticsUDF('detect_pii_entities_all', message, 'en') as pii_entities
FROM 
	(
	VALUES
        ('I am Bob, I live in Herndon VA, and I love cars')
    )AS NameTable(message)
;

		                     message    |              pii_entities
====================================================================================================================================================================================================================
I am Bob, I live in Herndon VA, and I love cars | [score=0.9999832,type=NAME,beginOffset=5,endOffset=8, score=0.9999931,type=ADDRESS,beginOffset=20,endOffset=30]
```

### Redact entities :

This function replaces entity values for the specified entity types with “[ENTITY_TYPE]”: 

```
%flink.ssql(type=update)
SELECT
    message,
    TextAnalyticsUDF('redact_entities', message, 'en') as redacted_entities
FROM 
	(
	VALUES
        ('I am Bob, I live in Herndon VA, and I love cars')
    )AS NameTable(message)
;
		
		        message                 |              redacted_entities
=========================================================================================================
I am Bob, I live in Herndon VA, and I love cars | [I am [PERSON], I live in [LOCATION], and I love cars]
```

### Redact PII entities :

This function replaces PII entity values for the specified entity types with “[PII_ENTITY_TYPE]”: 

```
%flink.ssql(type=update)
SELECT
    message,
    TextAnalyticsUDF('redact_pii_entities', message, 'en') as redacted_pii_entities
FROM 
	(
	VALUES
        ('I am Bob, I live in Herndon VA, and I love cars')
    )AS NameTable(message)
;
		
		        message                 |              redacted_pii_entities
=====================================================================================================
I am Bob, I live in Herndon VA, and I love cars | [I am [NAME], I live in [ADDRESS], and I love cars]
```

### Translate text :

This function translates text from the source language to the target language:

```
%flink.ssql(type=update)
SELECT
message,
TextAnalyticsUDF(‘translate_text’, message, ‘fr’, ‘null’) as translated_text_to_french
FROM 
	(
	VALUES
               (‘It is a beautiful day in neighborhood’)
)AS NameTable(message)
;

                 Message                |         translated_text_to_french
==================================================================================
It is a beautiful day in neighborhood   | C'est une belle journée dans le quartier
```
