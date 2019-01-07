/***********************************
 samDeploy DSL

 Packages sam template and pushes artefacts to S3

 example usage
 samDeploy(
   region: env.AWS_REGION,
   stackName: dev,
   template: template.yaml,
   source_bucket: source.bucket,
   prefix: cloudformation/${PROJECT}/${BRANCH_NAME}/${BUILD_NUMBER},
   uploadToS3: true||false, #if the template is too large the file has to be re-uploaded to s3. Requires PutObject permissions from the assumed account to the source bucket
   parameters: [
     'ENVIRONMENT_NAME' : 'dev',
   ],
   accountId: '1234567890', #the aws account Id you want the stack operation performed in
   role: 'myrole' # the role to assume from the account the pipeline is running from
 )
 ************************************/

def call(body) {
  def config = body
  def compiled_template = config.template.replace(".yaml", "-compiled.yaml")

  println("Copying s3://${config.source_bucket}/${config.prefix}/${compiled_template} to local")

  sh "aws s3 cp s3://${config.source_bucket}/${config.prefix}/${compiled_template} ${compiled_template}"

  def options = "--template-file ${compiled_template} --stack-name ${config.stackName} --capabilities CAPABILITY_IAM --region ${config.region}"

  if (config.parameters != null || !config.parameters.empty) {
    options = options.concat(" --parameter-overrides")
    config.parameters.each {
      options = options.concat(" ${it.key}=${it.value}")
    }
  }

  if (config.uploadToS3 != null && config.uploadToS3) {
    options = options.concat(" --s3-bucket ${config.source_bucket} --s3-prefix ${config.prefix}")
  }

  println("deploying ${compiled_template} to environment ${config.environment}")

  withAWS(roleAccount: config.accountId, region: config.region, role: config.role) {
    sh "aws cloudformation deploy ${options}"
  }
}
