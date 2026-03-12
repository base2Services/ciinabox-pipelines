/***********************************
s3 DSL

writes a file to an S3 bucket

example usage
s3
  file: 'myfile.yaml',
  bucket: 'mybucket',
  key: 'mydata/',
  region: ap-southeast-2,
  publicRead: true https://docs.aws.amazon.com/AmazonS3/latest/dev/acl-overview.html#canned-acl
)
************************************/

import com.amazonaws.services.s3.*
import com.amazonaws.services.s3.model.*
import com.amazonaws.regions.*

def call(body) {
  def config = body

  // NOTE: this Groovy runs on the controller, but the workspace lives on the agent.
  // Use Pipeline steps to read the file from the agent workspace.
  if (!fileExists(config.file)) {
    error "s3Put: file not found in workspace: ${pwd()}/${config.file}"
  }
  byte[] bytes = readFile(file: config.file).getBytes('UTF-8')

  AmazonS3 s3 = setupClient(config.region)
  putObject(s3, bytes, config)
}

def putObject(client, byte[] bytes, config) {
  def inputStream = new ByteArrayInputStream(bytes)
  def keyPrefix = config.prefix != null ? config.prefix : config.key
  def s3Key = keyPrefix != null ? "${keyPrefix}${config.file}" : config.file
  def metadata = new ObjectMetadata()
  metadata.setContentLength(bytes.length)

  PutObjectRequest request = new PutObjectRequest(
    config.bucket,
    s3Key,
    inputStream,
    metadata
  )

  if (config.publicRead) {
    request.withCannedAcl(CannedAccessControlList.PublicRead)
  }
  println "copying ${config.file} to s3://${config.bucket}/${s3Key}"
  client.putObject(request)

}

def setupClient(region) {
  return AmazonS3ClientBuilder.standard()
    .withRegion(region)
    .build()
}
