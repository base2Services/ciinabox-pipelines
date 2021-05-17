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
  File file = new File(config.file)
  AmazonS3 s3 = setupClient(config.region)
  putObject(s3,file,config)
}

def putObject(client,file,config) {

  def inputStream = new FileInputStream(file)

  PutObjectRequest request = new PutObjectRequest(
    config.bucket,
    "${config.prefix}${config.file}",
    inputStream,
    new ObjectMetadata()
  )

  if (config.publicRead) {
    request.withCannedAcl(CannedAccessControlList.PublicRead)
  }
  println "copying ${config.file} to s3://${config.bucket}/${config.prefix}${config.file}"
  client.putObject(request)

}

def setupClient(region) {
  return AmazonS3ClientBuilder.standard()
    .withRegion(region)
    .build()
}
