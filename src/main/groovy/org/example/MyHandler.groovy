package org.example

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import org.json.JSONObject
import com.amazonaws.util.IOUtils
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.nio.charset.StandardCharsets

public class MyHandler{

    static void handleFileProcessing(File file, Map mainMap) {
    def jsonSlurper = new JsonSlurper()
    def json = jsonSlurper.parse(file)
    def percentage = (json.numPassedTests / json.numTotalTests) * 100 + "%"
    def summary = [resultFileNameName: file.name, percentageofPassedTests: [percentage: percentage]]
    mainMap.put(file.name, percentage)
  }

  public static String handleEvent(S3Event event, Context context) {
    def jsonString = "";


        if(event != null && event.getRecords().size() > 0){

          def s3EventRecord = event.getRecords().get(0)
          def s3Bucket = s3EventRecord.getS3().getBucket().getName()
          def s3ObjectKey = s3EventRecord.getS3().getObject().getKey()

          def s3 = AmazonS3ClientBuilder.standard().withRegion("us-east-2").build()
          def s3Object = s3.getObject(s3Bucket, s3ObjectKey)
          def s3InputStream = s3Object.getObjectContent()

          def objectMapper = new ObjectMapper()
          def jsonObject = objectMapper.readValue(s3InputStream, Object.class)

          def summary
          def mainMap = [:]
          def percentage

          percentage = (jsonObject.numPassedTests / jsonObject.numTotalTests) * 100 + "%"
          summary = [resultFileNameName: s3ObjectKey, percentageofPassedTests:[percentage: percentage]]
          mainMap.put(s3ObjectKey, percentage)

          jsonString = JsonOutput.toJson(mainMap)
          jsonString = JsonOutput.prettyPrint(jsonString)

          println("---------------------")
          println("JSON containing % successful test for corresponding build...\n")
          println("---------------------")
          println(jsonString)
          def summaryBucketName = System.getenv('SUMMARY_BUCKET_NAME')
          println("Bucket Name: " + summaryBucketName)
          def objectKey = "REPORT_SUMMARY"
          // Create the object metadata
          def metadata = new ObjectMetadata()
          metadata.setContentType("application/json") // Important for JSON files
          // Create a PutObjectRequest
          def putObjectRequest = new PutObjectRequest(summaryBucketName, objectKey, new ByteArrayInputStream(jsonString.getBytes()), metadata)
          def newObjectContent = new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8))

// Upload the JSON file to S3
          try {
            def objectExists = s3.doesObjectExist(summaryBucketName, objectKey)
            if (objectExists) {
              // Read the existing content
              def existingObject = s3.getObject(summaryBucketName, objectKey)
              def existingContent = existingObject.getObjectContent()
              //def existingContentStr = IOUtils.toString(existingContent, StandardCharsets.UTF_8)
              def existingContentStr = IOUtils.toString(existingContent)
              def existingJson = new JSONObject(existingContentStr)

              // Append the new content to the existing content
              existingJson.append("data", jsonString)  // Or add other logic to combine the data
              jsonString = existingJson.toString()
              newObjectContent = new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8))
            }
            s3.putObject(new PutObjectRequest(summaryBucketName, objectKey, newObjectContent, metadata));
            context.getLogger().log("Successfully processed and uploaded to: " + summaryBucketName + "/" + objectKey)
            return "Successfully processed S3 event"
          } catch (Exception e) {
            context.getLogger().log("Error processing S3 event: " + e.getMessage())
            return "Error: " + e.getMessage()
          } finally {
            s3.shutdown(); // Close the client
          }

          println("S3 Event: " + s3EventRecord)
          println("JSON Data: " + jsonObject)
          println("Function executed successfully.")
        }else {
          println("event object has no records")
        }


    return "return from method:: " + jsonString
  }

  static void main(String[] args) {

    def projectRoot = new File(".") // current directory
    def resourcesDir = new File(projectRoot.getAbsoluteFile(), "../resources")
    def jsonSlurper = new JsonSlurper()
    def object = new Object()
    def percentage
    def json
    def summary
    def mainMap = [:]
    if (resourcesDir.exists() && resourcesDir.isDirectory()) {
      File[] files = resourcesDir.listFiles()
      println("List of input files...\n")
      if (files != null) {
        files.each { file ->
          println file.name
          json = new JsonSlurper().parse(file)
          percentage = (json.numPassedTests / json.numTotalTests) * 100 + "%"
          summary = [resultFileNameName: file.name, percentageofPassedTests:[percentage: percentage]]
          mainMap.put(file.name, percentage)

        }
      } else {
        println "The resources directory is empty."
      }
    } else {
      println "Resources directory not found or is not a directory."
    }
    def jsonString = JsonOutput.toJson(mainMap)
    jsonString = JsonOutput.prettyPrint(jsonString)
    println("---------------------")
    println("JSON containing % successful test for corresponding build...\n")
    println("---------------------")
    println(jsonString)
    //def json1 = new JsonSlurper().parse(new File(getClass().getResource("test-results.json").toURI()))

    //println(json1.get('numTotalTests'))
    //println(json1)
  }

}

