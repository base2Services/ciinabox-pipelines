/***********************************
cloudformationValidate DSL

performs cloudformation validation operations

based upon a s3 bucket folder location, finds all templates in that path and validates them

example usage
cloudformationValidate (
  region: 'us-east-1',
  s3Location: 'my-bucket/s3/path' folder path that the templates are in
)

************************************/

import com.amazonaws.regions.*
import com.amazonaws.services.cloudformation.*
import com.amazonaws.services.cloudformation.model.*
import com.amazonaws.services.s3.*
import com.amazonaws.services.s3.model.*

def call(body) {
  def config = body
  def s3 = setupS3Client(config.region)
  def cf = setupCfClient(config.region)

  def s3Location = config.s3Location.split("/", 2)
  def bucket = s3Location.first()
  def prefix = s3Location.last()

  templates = listTemplatesInPath(s3,bucket,prefix)
  failures = validateCfnTemplatesS3(cf,templates)
  if (failures) {
    error("Found ${failures.size()} template validation errors")
  }
}

@NonCPS
def listTemplatesInPath(s3,bucket,prefix) {
  def results = []
  def list = s3.listObjects(new ListObjectsRequest()
    .withBucketName(bucket)
    .withPrefix(prefix)
  )
  list.getObjectSummaries().each {
    results << "https://${it.bucketName}.s3.amazonaws.com/${it.key}"
  }
  results.removeAll {
    !(it.endsWith('.yaml') || it.endsWith('.yml') || it.endsWith('.json'))
  }
  return results
}

@NonCPS
def validateCfnTemplatesS3(cf,templateList) {
  def failures = []
  println "Validating ${templateList.size()} cloudformation templates"
  templateList.each { template ->
    try {
      def request = cf.validateTemplate(new ValidateTemplateRequest()
        .withTemplateURL(template)
      )
      println "[PASS] ${template}"
    } catch (AmazonCloudFormationException ex) {
      println "[FAIL] ${template}"
      println "[ERROR] ${ex.message}"
      failures << [template: template, failure: ex.message]
    }
  }
  return failures
}

@NonCPS
def setupS3Client(region) {
  return AmazonS3ClientBuilder.standard()
    .withRegion(region)
    .build()
}

@NonCPS
def setupCfClient(region) {
  return AmazonCloudFormationClientBuilder.standard()
    .withRegion(region)
    .build()
}