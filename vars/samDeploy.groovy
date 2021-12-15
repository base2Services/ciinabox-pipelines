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
   noFailOnEmptyChangeset: true||false, # if there are no changes then don't exit/fail
   uploadToS3: true||false, #if the template is too large the file has to be re-uploaded to s3. Requires PutObject permissions from the assumed account to the source bucket
   parameters: [
     'ENVIRONMENT_NAME' : 'dev',
   ],
   capabilities: [ # define cloudformation capabilities required by the stack or set value to false to disable the default capabilities
     'CAPABILITY_IAM',
     'CAPABILITY_NAMED_IAM',
     'CAPABILITY_AUTO_EXPAND'
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
 
  def options = "--template-file ${compiled_template} --stack-name ${config.stackName} --region ${config.region}"

  if (config.parameters != null && !config.parameters.empty) {
    options = options.concat(" --parameter-overrides")
    config.parameters.each {
      options = options.concat(" ${it.key}=${it.value}")
    }
  }

  if (config.uploadToS3 != null && config.uploadToS3) {
    options = options.concat(" --s3-bucket ${config.source_bucket} --s3-prefix ${config.prefix}")
  }

  if (config.noFailOnEmptyChangeset != null && config.noFailOnEmptyChangeset == "true") {
    options = options.concat(" --no-fail-on-empty-changeset")
  }
 
  def capabilities = config.get('capabilities', ['CAPABILITY_IAM'])
  if (capabilities) {
    options = options.concat(" --capabilities ${capabilities.join(' ')}")
  }

  println("deploying ${compiled_template} to environment ${config.environment}")

  withAWS(roleAccount: config.accountId, region: config.region, role: config.role) {
    sh "aws cloudformation deploy ${options}"
  }
}
