# Translate and analyze streaming data using SQL functions with Amazon Kinesis Data Analytics, Amazon Translate, and Amazon Comprehend

This example will show you how to leverage Amazon Kinesis Data Analytics Studio powered by Apache Zeppelin and Apache Flink to
interactively analyze, translate, and redact streaming data using Amazon Translate, Amazon Comprehend, as user-defined functions (UDFs). 

<img width="917" alt="Architecture" src="https://user-images.githubusercontent.com/46901738/151387452-1c3683ca-7571-4997-83fc-d0a2029ec9d1.png">

## Setup guide :

Note : Step 1 to 5 are temporary and would not be need once access to a public
AWS bucket like aws-bigdata-blog.s3.amazonaws.com is granted

1. Run ``git clone git@github.com:aws-samples/kinesis-udfstextanalytics.git``  to clone the repo locally

2. Run ``cd kinesis-udfs-textanalytics/kinesis-udfs-textanalytics-linear`` to
navigate into UDF code directory

3. Run ``mvn clean install`` to generate UDF JAR

4. Run ``cd ..`` to navigate back to kinesis-udfs-textanalytics base directory

5. Run ``python3 upload_artifacts_to_s3.py`` to upload all repository artifacts
(JAR, notebooks, datasets) to a local S3 bucket in your account

6. Create a Cloudformation stack using ``kinesis-udfstextanalytics/
cloudformation-stack/KDA_StudioNotebook_with_UDF.yaml`` . Modify only the ``ArtifactsBucket`` input parameter as per the bucket chosen in
step 5.

7. Once Stack is in ``CREATE_COMPLETE`` state, navigate to the dynamically generated
S3 bucket named as ``amazon-reviews-bucket-<dynamic-stack-id>/artifacts``

8. For ``0-data-load-notebook.json`` ; ``1-UDF-notebook.json`` ; ``2-base-SQLnotebook.
json`` & ``3-sentiments-notebook.json`` files within this S3 bucket,
perform the following steps for each file:

    - Click on file name
    - Click on Open button on S3 console

<img width="1219" alt="Screenshot 2022-01-27 at 8 51 04 PM" src="https://user-images.githubusercontent.com/46901738/151389000-021b16a4-35c0-4ce6-8489-76ce69c6f534.png">

    - A new tab is opened in browser with a pre-signed URL for the file.

  <img width="1218" alt="Screenshot 2022-01-27 at 8 51 15 PM" src="https://user-images.githubusercontent.com/46901738/151389068-af9cc476-48c3-482d-b6c4-b6ab93b38bf4.png">

    - Use this URL to import notebooks into the Studio application.

  <img width="855" alt="Screenshot 2022-01-27 at 8 51 33 PM" src="https://user-images.githubusercontent.com/46901738/151389173-ee034300-1085-41e3-924a-19ad4277d932.png">

  <img width="777" alt="Screenshot 2022-01-27 at 8 51 51 PM" src="https://user-images.githubusercontent.com/46901738/151389203-04b9d8ac-ce45-4f65-81fc-561456e6a831.png">

9. Run each cell of these notebooks in-order i.e. ``0-data-load-notebook.json`` then
``1-UDF-notebook.json`` then ``2-base-SQL-notebook.json`` then ``3-sentimentsnotebook.
json`` .

10. Confirm creation of output files in ``amazon-reviews-bucket-<dynamic-stackid>/
data`` directory

<img width="1074" alt="Screenshot 2022-01-27 at 8 52 59 PM" src="https://user-images.githubusercontent.com/46901738/151389391-626d7691-c183-47c3-8635-8442aad4447f.png">

<img width="1067" alt="Screenshot 2022-01-27 at 8 53 08 PM" src="https://user-images.githubusercontent.com/46901738/151389452-65d2f87c-9153-48df-bc37-89b2a793cc87.png">


