package org.example

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification
import com.amazonaws.services.s3.AmazonS3ClientBuilder

import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.util.IOUtils
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonOutput
import org.json.JSONArray
import org.json.JSONObject

import java.nio.charset.StandardCharsets

// Lambda function handler
public String handleEvent(S3Event event, Context context) {
  def TARGET_BUCKET = System.getenv('SUMMARY_BUCKET_NAME')
  def OUTPUT_OBJECT_KEY = "REPORT_SUMMARY"
  // Get S3 client
  def s3Client = AmazonS3ClientBuilder.standard().build()
  // Extract details from S3 event
  event.getRecords().each { S3EventNotification.S3EventNotificationRecord record ->
    def sourceBucketName = record.getS3().getBucket().getName()
    def sourceObjectKey = record.getS3().getObject().getKey()

    // 1. Read data from source S3 object
    def s3Object = s3Client.getObject(sourceBucketName, sourceObjectKey)
    def s3InputStream = s3Object.getObjectContent()
    // 2. Process the data (e.g., add some details, create new JSON)
    def objectMapper = new ObjectMapper()
    def newJsonObject = objectMapper.readValue(s3InputStream, Object.class)

    def summaryJSON = new JSONObject()
    def percentage
    JSONArray arr = new JSONArray()

    percentage = (newJsonObject.numPassedTests / newJsonObject.numTotalTests) * 100 + "%"
    arr.put(percentage)
    summaryJSON.put(sourceObjectKey, arr)

    def jsonString = summaryJSON.toString()

    def newObjectContent = new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8))

    // 3. Create a new ObjectMetadata
    def objectMetadata = new ObjectMetadata()
    objectMetadata.setContentType("application/json")

    // 4. Write to a new S3 object (append if the target key exists)
    try {
      // Check if the object exists in the target bucket
      def objectExists = s3Client.doesObjectExist(TARGET_BUCKET, OUTPUT_OBJECT_KEY)

      // Append to the object if it exists, else create a new object
      if (objectExists) {
        // Read the existing content
        def existingObject = s3Client.getObject(TARGET_BUCKET, OUTPUT_OBJECT_KEY)
        def existingContent = existingObject.getObjectContent()
        def existingContentStr = IOUtils.toString(existingContent)
        def existingJson = new JSONObject(existingContentStr)

        // Append the new content to the existing content
        existingJson.append(sourceObjectKey, percentage)  // Or add other logic to combine the data
        println(existingJson)
        jsonString = existingJson.toString()
        newObjectContent = new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8))
      }

      // Create the new object with either the new or appended content
      s3Client.putObject(new PutObjectRequest(TARGET_BUCKET, OUTPUT_OBJECT_KEY, newObjectContent, objectMetadata))
      context.getLogger().log("Successfully processed and uploaded to: " + TARGET_BUCKET + "/" + OUTPUT_OBJECT_KEY)
      return "Successfully processed S3 event"
    } catch (Exception e) {
      context.getLogger().log("Error processing S3 event: " + e.getMessage())
      return "Error: " + e.getMessage()
    }

  }

  return "Completed processing S3 event"
}