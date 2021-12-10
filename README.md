# Sample Amazon Kinesis Data Analytics Studio UDFs for text translation and analytics using Amazon Comprehend and Amazon Translate

## Solution :

![Picture 1](https://user-images.githubusercontent.com/46901738/145456070-5c7f13e3-acac-43be-92ad-1ea04e2431bc.png)

## Setup Guide :

<strong> Note : Step 1 to 5 are temporary and would not be need once access to a public AWS bucket like `aws-bigdata-blog.s3.amazonaws.com` is granted </strong>

1. Run `git clone git@github.com:aws-samples/kinesis-udfs-textanalytics.git --branch linear-approach` to clone the repo locally
2. Run `cd kinesis-udfs-textanalytics/kinesis-udfs-textanalytics-linear` to navigate into UDF code directory 
3. Run `mvn clean install` to generate UDF JAR
4. Run `cd ..` to navigate back to `kinesis-udfs-textanalytics` base directory 
5. Run `python3 upload_artifacts_to_s3.py` to upload all repository artifacts (JAR, notebooks, datasets) to a local S3 bucket in your account

6. Create a Cloudformation stack using `kinesis-udfs-textanalytics/cloudformation-stack/KDA_StudioNotebook_with_UDF.yaml`. Modify only the `ArtifactsBucket` input parameter as per the bucket chosen in step 5.

7. Once Stack is in `CREATE_COMPLETE` state, navigate to the dynamically generated S3 bucket named as `amazon-reviews-bucket-<dynamic-stack-id>/artifacts`. 

8. For `0-data-load-notebook.json` ; `1-UDF-notebook.json` ; `2-base-SQL-notebook.json` & `3-sentiments-notebook.json` files within this S3 bucket, perform the following steps for **each** file:
    
- Click on file name

- Click on `Open` button on S3 console 
		
	<img width="954" alt="Screenshot 2021-12-09 at 11 53 23 PM" src="https://user-images.githubusercontent.com/46901738/145454209-bfe32f9c-ee2d-4709-83b9-87a5713eea2a.png">

		
- A new tab is opened in browser with a pre-signed URL for the file. 

	<img width="1440" alt="Screenshot 2021-12-09 at 11 54 56 PM" src="https://user-images.githubusercontent.com/46901738/145454239-57dc2daf-ec93-401c-8dc9-1a7c03d7e6ac.png">

		
-  Use this URL to import notebooks into the Studio application.
	
	<img width="1092" alt="Screenshot 2021-12-09 at 11 55 26 PM" src="https://user-images.githubusercontent.com/46901738/145454267-7705ff8a-79b5-43d3-938f-a19e651b8ed3.png">

	<img width="698" alt="Screenshot 2021-12-09 at 11 55 46 PM" src="https://user-images.githubusercontent.com/46901738/145454278-82b12265-93be-4faa-b6a1-b4b450e38fe9.png">


9. Run each cell of these notebooks in-order i.e. `0-data-load-notebook.json` then `1-UDF-notebook.json` then `2-base-SQL-notebook.json` then `3-sentiments-notebook.json`. 

10. Confirm creation of output files in `amazon-reviews-bucket-<dynamic-stack-id>/data` directory

<img width="1144" alt="Screenshot 2021-12-10 at 12 07 27 AM" src="https://user-images.githubusercontent.com/46901738/145455873-9c5bbe02-c8dd-4ebb-89e0-644a5a2242a7.png">

<img width="1188" alt="Screenshot 2021-12-10 at 12 07 54 AM" src="https://user-images.githubusercontent.com/46901738/145455899-07259c7f-c79f-405d-98e4-cef88f3dab91.png">


