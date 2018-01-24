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

def call(body) {
  def config = body
  File file = new File(config.file)
  def s3 = setupClient(config.region)
  putObject(s3,file,config)
}

def putObject(client,file,config) {

  PutObjectRequest request = new PutObjectRequest(
    config.bucket,
    "${config.prefix}${config.file}",
    file
  )

  if (config.publicRead) {
    request.withCannedAcl(CannedAccessControlList.PublicRead)
  }

  client.putObject(request)

}

def setupClient(region) {
  return AmazonS3ClientBuilder.standard()
    .withRegion(region)
    .build()
}
