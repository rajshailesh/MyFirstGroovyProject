package org.example

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

static void handleFileProcessing(File file, Map mainMap) {
  def jsonSlurper = new JsonSlurper()
  def json = jsonSlurper.parse(file)
  def percentage = (json.numPassedTests / json.numTotalTests) * 100 + "%"
  def summary = [resultFileNameName: file.name, percentageofPassedTests: [percentage: percentage]]
  mainMap.put(file.name, percentage)
}

def handleEvent(S3Event event, Context context) {
  def s3EventRecord = event.getRecords().get(0)
  def s3Bucket = s3EventRecord.getS3().getBucket().getName()
  def s3ObjectKey = s3EventRecord.getS3().getObject().getKey()

  def s3 = AmazonS3ClientBuilder.defaultClient()
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

  def jsonString = JsonOutput.toJson(mainMap)
  jsonString = JsonOutput.prettyPrint(jsonString)

  println("---------------------")
  println("JSON containing % successful test for corresponding build...\n")
  println("---------------------")
  println(jsonString)

  println("S3 Event: " + s3EventRecord)
  println("JSON Data: " + jsonObject)
  println("Function executed successfully.")
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