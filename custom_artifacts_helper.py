import boto3
import os
from botocore.exceptions import ClientError
import fileinput

# Get bucket & region inputs
bucket_name = input("Enter S3 bucket name : ")
region_name = input("Enter region code for bucket, example us-east-1 : ")
prefix_name = input("Enter the S3 prefix under which the artifacts are to be stored : ")

# Create S3 client
client = boto3.client('s3', region_name=region_name)

# Method for S3 upload
def upload(client, local_file_name, s3_file_name, bucket_name):
    try:
        response = client.upload_file(local_file_name, bucket_name, prefix_name+'/'+s3_file_name)
        print(local_file_name +" uploaded to bucket " +bucket_name)
    except ClientError as e:
        logging.error(e)
        return False
    return True

# Get current working directory, expectation is to be inside kinesis-udfs-textanalytics folder
base_path = os.getcwd()

# Upload zepplin notebooks
notebook_list = ['0-UDF-notebook.zpln', '1-data-load-notebook.zpln', '2-base-SQL-notebook.zpln', '3-sentiments-notebook.zpln', '4-entities-notebook.zpln', '5-redact-entities-notebook.zpln', '6-redact-pii-entities-notebook.zpln']
for x in range(len(notebook_list)):
     upload(client, base_path+'/example-notebooks/'+notebook_list[x], notebook_list[x], bucket_name)

# Upload trimmed tsv dataset
dataset_list = ['amazon_reviews_us_Grocery_trimmed.tsv', 'amazon_reviews_us_Personal_Care_Appliances_trimmed.tsv']
for x in range(len(dataset_list)):
     upload(client, base_path+'/example-trimmed-datasets/'+dataset_list[x], dataset_list[x], bucket_name)

# Upload UDF Jar
upload(client, base_path+'/kinesis-udfs-textanalytics-linear/target/text-analytics-udfs-linear-1.0.jar', 'text-analytics-udfs-linear-1.0.jar', bucket_name)

# Modify CFN for new bucket name and Upload
for line in fileinput.input(base_path+"/cloudformation-stack/KDA_StudioNotebook_with_UDF.yaml", inplace=True):
    print(line.replace("aws-blogs-artifacts-public", bucket_name), end="")
    print(line.replace("artifacts/ML-4786", prefix_name), end="")
upload(client, base_path+'/cloudformation-stack/KDA_StudioNotebook_with_UDF.yaml', 'KDA_StudioNotebook_with_UDF.yaml', bucket_name)
