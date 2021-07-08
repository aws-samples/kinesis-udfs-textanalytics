
- Create essentially a replica of (https://aws.amazon.com/blogs/machine-learning/translate-and-analyze-text-using-sql-functions-with-amazon-athena-amazon-translate-and-amazon-comprehend/) to cover real-time scenarios
- Achieve feature parity between Athena/KDA

- real-time text analytics 
- 

Make things production ready
- Splitting up payloads so that they don't exceed payload limits

- Batch api
   - You can't mix languages in the batch API
   - https://github.com/aws-samples/aws-athena-udfs-textanalytics/blob/main/athena-udfs-textanalytics/src/main/java/com/amazonaws/athena/connectors/textanalytics/TextAnalyticsUDFHandler.java
   - create an operator that batches records (say 50 per batch)
   - with this batch of records call Bob's function
   - pick one to start with (translate w/ auto detect): USING EXTERNAL FUNCTION translate_text(text_col VARCHAR, sourcelang VARCHAR, targetlang VARCHAR, customterminologyname VARCHAR) RETURNS VARCHAR LAMBDA 'textanalytics-udf' 
SELECT translate_text('It is a beautiful day in the neighborhood', 'auto', 'fr', NULL) as translated_text
