# Translate and analyze streaming data using SQL functions with Amazon Kinesis Data Analytics, Amazon Translate, and Amazon Comprehend

This example will show you how to leverage Amazon Kinesis Data Analytics Studio powered by Apache Zeppelin and Apache Flink to
interactively analyze, translate, and redact streaming using Amazon Translate & Amazon Comprehend APIs. The approach involves creation of [Apache Flink user-defined functions](https://nightlies.apache.org/flink/flink-docs-release-1.11/dev/table/functions/udfs.html#user-defined-functions) (UDFs) to trigger these APIs. 

<img width="917" alt="Architecture" src="https://user-images.githubusercontent.com/46901738/151387452-1c3683ca-7571-4997-83fc-d0a2029ec9d1.png">

Related AWS Blog : link-here
    
This repository contains following artifacts :

1. Example Apache Zeppelin notebooks under ``example-notebooks`` directory
2. Trimmed down version of [Amazon Product reviews](https://s3.amazonaws.com/amazon-reviews-pds/readme.html) dataset under ``example-trimmed-datasets`` directory
3. Java project for UDF under ``kinesis-udfs-textanalytics-linear`` directory
4. Cloudformation template to automate deployments under ``cloudformation-stack`` directory
    
Repository additionally contains ``custom_artifacts_helper.py`` python script to enable a customized setup (refer [below](https://github.com/aws-samples/kinesis-udfs-textanalytics#user-content-customized-setup-))
	
## Example output on Amazon Product Reviews data-set :

Sentiment analysis and dominant language detection :
<img width="917" alt="Architecture" src="https://user-images.githubusercontent.com/46901738/164889243-c5727ed9-4f77-49eb-8225-44ec5207c2d8.png">
    
Entity Redaction :
<img width="917" alt="Architecture" src="https://user-images.githubusercontent.com/46901738/164889236-0dba919d-333e-4e30-baf5-d73520149488.png">

PII entity Redaction :
<img width="917" alt="Architecture" src="https://user-images.githubusercontent.com/46901738/164889232-90045688-76cf-47f1-8ba1-fcb413bc81ae.png">
    
## Setup guide :

### Default setup  : 

1. Clone repo locally using ``git clone https://github.com/aws-samples/kinesis-udfs-textanalytics.git``  
2. Choose ``cloudformation-stack/KDA_StudioNotebook_with_UDF.yaml`` template [to execute a cloudformation stack in your AWS account](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-template.html). This approach will pull default artifacts from public AWS S3 bucket ``bkt-name-here`` which includes the pre-built JAR for the UDF JAVA project under ``kinesis-udfs-textanalytics-linear`` directory. 


### Customized setup :
If you need to deploy customized artifacts, perform following :

1. Clone repo locally using ``git clone https://github.com/aws-samples/kinesis-udfs-textanalytics.git``  
    
2. Modify the contents of artifacts locally as required. To create JAR, refer Create JAR section of [project README](https://github.com/aws-samples/kinesis-udfs-textanalytics/blob/main/kinesis-udfs-textanalytics-linear/README.md)

3. Once modifications are done, execute ``custom_artifacts_helper.py`` python script which programmatically uploads these modified artifacts to a specified S3 bucket within your account and also updates the [Parameter](https://github.com/aws-samples/kinesis-udfs-textanalytics/blob/2f54bc2ca83719c03d0565e49da864df2baebab9/cloudformation-stack/KDA_StudioNotebook_with_UDF.yaml#L8) section of Cloudformation template.

      Note : The ``custom_artifacts_helper.py`` python script scans for artifacts by their default names in current directory, so during content modification ensure the name of artifacts remain same. Also, the script requires the IAM entitiy to have [Read and Write access](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_examples_s3_rw-bucket.html) to S3 bucket. 

4. Confirm creation of modified Cloudformation stack template as ``/folder/KDA_StudioNotebook_with_UDF.yaml`` along with other artifacts in your specified S3 bucket. Copy the Object URL of this template object from Amazon S3 console and [use it to execute a cloudformation stack](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-template.html) in your AWS account.
