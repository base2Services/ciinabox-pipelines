
/**************************
uploadLambdaToBuckets(
  bucket: 'base2.lambda.${region}',
  key: 'path/to/file,'    // default: ''
  regions: '*',           // or ['ap-southeast-2', 'us-east-1']
  file: 'handler.zip',
  createBucket: true,     // default: false
  publicBucket: true      // default: false
)

**************************/

def call(config) {

  def regions = config.get('regions', [])

  if (!regions && regions != '*') {
    println "Error - 'regions' must be either a list of AWS regions or '*'."
    sh "exit 1"
  }

  if (regions == '*') {
    def output = sh (script: 'aws ec2 describe-regions --query "Regions[*].RegionName" --output text | tr "\t" "\n" | sort', returnStdout: true)
    println "Current (${output.size()}) regions:\n${output}"

    regions = output.split()
  }

  def key = config.get('key', '')

  if (key) {
    if (!key.startsWith('/')) {
      key = '/' + key
    }
  }

  // For each region, try to upload the file to the regional bucket.
  regions.each { region ->
    def bucket = config.bucket.replace('${region}', region)
    def response = sh (script: "aws s3api head-bucket --bucket ${bucket} --region ${region} 2>&1 || exit 0", returnStdout: true)

    if (response.contains("Not Found")) {
      if (config.createBucket) {
        println "Creating S3 bucket as it does not exist: ${bucket} ..."
        sh "aws s3api create-bucket --bucket ${bucket} --create-bucket-configuration LocationConstraint=${region} --region ${region}"
      }

      if (config.publicBucket) {
        println "Setting bucket as public..."
        createPublicPolicy(bucket)
        sh "aws s3api put-bucket-policy --bucket ${bucket} --policy file://s3-policy.json --region ${region}"
      }

    }

    def path = "s3://${bucket}${key}/${config.file}"
    println "Copying file to S3: ${path} ..."
    sh "aws s3 cp ${config.file} ${path} --region ${region}"
  }
}

def createPublicPolicy(bucket) {
  sh """/bin/bash
  tee s3-policy.json <<EOF
{
  "Id": "StmtPubliclyReadable",
  "Statement": [
    {
      "Sid": "StmtPubliclyReadable",
      "Action": [
        "s3:GetObject",
        "s3:ListBucket"
      ],
      "Effect": "Allow",
      "Resource": [
        "arn:aws:s3:::${bucket}",
        "arn:aws:s3:::${bucket}/*"
      ],
      "Principal": {
        "AWS": [
          "*"
        ]
      }
    }
  ]
}
EOF
  """
}
