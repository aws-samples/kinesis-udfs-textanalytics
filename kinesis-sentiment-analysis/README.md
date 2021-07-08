Sample showcasing real-time sentiment analysis with Kinesis Data Streams, KDA Studio and Amazon Comprehend.

## Overall architecture

![Overall architecture](images/ss1.png)

### Kinesis Data Streams setup

Let's first set up a Kinesis Data Stream so we can use it to ingest data. In this sample, we'll be populating this Kinesis Data Stream with records from the [Amazon Customer Reviews public dataset](https://s3.amazonaws.com/amazon-reviews-pds/readme.html).

<<TODO: show how to create Kinesis Data Stream>>

Now that we've created a Kinesis Data Stream, let's create our Kinesis Data Analytics Studio Application.

### Kinesis Data Analytics Studio setup

<<TODO: Diagram>>

Ensure that we have the right permissions for our application. Please see [IAM permissions for Studio notebooks](https://docs.aws.amazon.com/kinesisanalytics/latest/java/how-zeppelin-iam.html) for more details.

### Publishing data to Kinesis
One of the benefits of using Kinesis Data Analytics Studio is that we have a convenient mechanism for publishing data into Kinesis.

```
%flink(parallelism=1)

import java.util.Properties
import java.util.UUID
import org.apache.flink.streaming.api.scala._
import org.apache.flink.api.common.serialization.SimpleStringSchema

// note the namespace for the kinesis connector
import software.amazon.kinesis.connectors.flink.FlinkKinesisProducer
import software.amazon.kinesis.connectors.flink.config.AWSConfigConstants
import software.amazon.kinesis.connectors.flink.KinesisPartitioner

// kinesis producer config
val producerConfig = new Properties()
producerConfig.put(AWSConfigConstants.AWS_REGION, "us-east-1")

val kinesis = new FlinkKinesisProducer[String](new SimpleStringSchema, producerConfig)
kinesis.setFailOnError(false)
kinesis.setDefaultStream("paperboatteststream2")

// spray data across all available shards
kinesis.setCustomPartitioner(new KinesisPartitioner[String]() {
    override def getPartitionId(s: String): String = {
        // we dont' care about shard affinity in this app
		UUID.randomUUID().toString()
    }
})

val bsSettings = EnvironmentSettings.newInstance().useBlinkPlanner().inStreamingMode().build()
val st_env = StreamTableEnvironment.create(senv, bsSettings)

// senv is the streaming environment
// Let's use the Amazon customer review dataset
val personalCareData = senv.readTextFile("s3://amazon-reviews-pds/tsv/amazon_reviews_us_Personal_Care_Appliances_v1_00.tsv.gz")
val bookData = senv.readTextFile("s3://amazon-reviews-pds/tsv/amazon_reviews_us_Books_v1_01.tsv.gz")
val groceryData = senv.readTextFile("s3://amazon-reviews-pds/tsv/amazon_reviews_us_Grocery_v1_00.tsv.gz")

// Union them into a single stream
val data = bookData.union(personalCareData, groceryData)

// Since metadata fields are not available in 1.11, we have to
// come up with a way to generate timestamps that we can use
// for watermarking
val dataWithIngestTime = data.map(v => {
    v + "\t" + System.currentTimeMillis()
})

dataWithIngestTime.addSink(kinesis)

senv.execute()
```

A few things about this Zeppelin paragragh. You'll notice that we're using Scala to read from multiple customer review tsv (tab separated value) files and then send that data to Kinesis. This allows us to simulate real-world scenarios where the data is ingested into Kinesis, say from a web-tier application, and we're expected to perform real-time analysis against that stream.

You'll also notice that we're unioning multiple tsv files; this essentially simulates a real-time stream that contains data from multiple product categories.

Once this data is in Kinesis, we can replay it at will - as described below.

## Replaying and processing data from Kinesis Data Stream
We'll use the following Flink table as the source for reading and processing our real-time customer review data:
```
%flink.ssql

DROP TABLE IF EXISTS source_kinesis_reviews;

-- Since metadata fields are not available in 1.11, we have to
-- come up with a way to generate timestamps that we can use
-- for watermarking
CREATE TABLE source_kinesis_reviews (
    marketplace STRING,
    customer_id STRING,
    review_id STRING,
    product_id STRING,
    product_parent STRING,
    product_title STRING,
    product_category STRING,
    star_rating INT,
    helpful_votes INT,
    total_votes INT,
    vine STRING,
    verified_purchase STRING,
    review_headline STRING,
    review_body STRING,
    review_date STRING,
    ingest_time BIGINT,
    --sentiment AS scala_sentiment(review_body),
    event_ts AS TO_TIMESTAMP(FROM_UNIXTIME(COALESCE(ingest_time, UNIX_TIMESTAMP())/1000, 'yyyy-MM-dd HH:mm:ss')),
    WATERMARK FOR event_ts AS event_ts - INTERVAL '5' SECOND
)
PARTITIONED BY (product_id)
WITH (
    'connector'= 'kinesis',
    'stream' = 'paperboatteststream2',
    'format' = 'csv',
    'csv.field-delimiter' = U&'\0009',
    'csv.ignore-parse-errors' = 'true',
    'scan.shard.getrecords.maxretries' = '100',
    'aws.region' = 'us-east-1',
    'scan.stream.initpos' = 'AT_TIMESTAMP',
    'scan.stream.initpos-timestamp' = '2021-05-23T14:35:25.000-00:00' -- we'll use a timestamp so we can keep replaying the data we published above
)

```

## Scala UDF for calling Amazon Comprehend
Here's the UDF
```
%flink(parallelism=1)

import java.util.Properties
import java.util.UUID
import org.apache.flink.streaming.api.scala._
import org.apache.flink.table.functions.FunctionContext
import org.apache.flink.api.common.serialization.SimpleStringSchema

// note the namespace for the kinesis connector
import software.amazon.kinesis.connectors.flink.FlinkKinesisProducer
import software.amazon.kinesis.connectors.flink.config.AWSConfigConstants
import software.amazon.kinesis.connectors.flink.KinesisPartitioner

// pull in awssdk
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.comprehend.model.ComprehendException;
import software.amazon.awssdk.services.comprehend.model.DetectSentimentRequest;
import software.amazon.awssdk.services.comprehend.model.DetectSentimentResponse;

class ScalaSentiment extends ScalarFunction {
  private var comprehend: ComprehendClient = null
  
  override def open(context: FunctionContext): Unit = {
      var region = Region.US_EAST_1;
      comprehend = ComprehendClient.builder()
                .region(region)
                .build()
  }
  
  override def close(): Unit = {
      if(comprehend != null) {
          comprehend.close()
          comprehend = null
      }
  }

  def eval(str: String): String = {
      var request = DetectSentimentRequest.builder()
                .text(str)
                .languageCode("en")
                .build()
    
      var retVal = "ERROR"
      try {
        var detectSentimentResult = comprehend.detectSentiment(request)
        retVal = detectSentimentResult.sentimentAsString()
      } catch { case _: Throwable => }
      
      retVal
  }
}
btenv.registerFunction("scala_sentiment", new ScalaSentiment())
```

## Referencing the AWS SDK
In order to reference the Amazon Comprehend SDK from a KDA Studio notebook, we need to package it in a jar and include it in our application. This involves
1. Creating a jar containing the Amazon Comprehend SDK - see [awssdk-for-kdastudio](awssdk-for-kdastudio/README.md).
2. Updating our KDA Studio application to include the custom jar - see [updateapp.json](updateapp.json). You can use the AWS CLI to include the update the KDA Studio application:

```aws kinesisanalyticsv2 update-application --application-name [your-app-name] --current-application-version-id [current-appid]  --application-configuration-update file://updateapp.json```
