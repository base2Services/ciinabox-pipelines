/***********************************
changeSetDeploy DSL

changeSetDeploy orchestrates both createChangeSet and executeChangeSet to both create and execute a cloudformation changeset

example usage

changeSetDeploy(
  description: env.GIT_COMMIT, // (optional, add a description to your changeset)
  region: 'us-east-1', // (required, aws region to deploy the stack)
  stackName: 'my-stack', // (required, name of the CloudFormation stack)
  awsAccountId: '012345678901', // (optional, aws account the cloudformation stack is to be deployed to)
  role: 'iam-role-name', // (optional, IAM role to assume if deploying to a different aws account)
  roleArn: 'iam-cfn-service-role', // (optional, execution role arn for the cloudformation service)
  maxErrorRetry: 3, // (optional, defaults to 3)
  parameters: [ // (optional, map of key value pairs)
    key: value
  ],
  failOnEmptyChangeSet: true | false, // if set to false changeSetDeploy will continue if no changes are detected
  approveChanges: true | false, // if set to changeSetDeploy will automatically execute the changeset
  templateUrl: 'bucket/template.yaml' // (required, full s3 path of template),
  tags: [ // (optional, tags to add to the cloudformation stack)
    key: value
  ],
  capabilities: false, // (optional, set to false to remove capabilities or supply a list of capabilities. Defaults to [CAPABILITY_IAM,CAPABILITY_NAMED_IAM]),
  nestedStacks: true | false // (optional, defaults to false, set to true to add nestedStacks diff )
)
***********************************/

def call(body) {
  def config = body

  def failOnEmptyChangeSet = config.get('failOnEmptyChangeSet', false)
  def approveChanges = config.get('approveChanges', false)
  def stackNameUpper = config.stackName.toUpperCase().replaceAll("-", "_")

  createChangeSetProperties = [
    region: config.region,
    stackName: config.stackName,
    failOnEmptyChangeSet: failOnEmptyChangeSet,
    templateUrl: config.templateUrl
  ]

  optionalCreateChangeSetProperties = [
    'description',
    'awsAccountId',
    'role',
    'roleArn',
    'maxErrorRetry',
    'parameters',
    'tags',
    'capabilities',
    'nestedStacks'
  ]

  optionalCreateChangeSetProperties.each { property ->
    if (config[property]) {
      createChangeSetProperties[property] = config[property]
    }
  }
  
  createChangeSet(createChangeSetProperties)

  if (approveChanges) {
    input(
      message: "Execute ${config.stackName} changeset ${env["${stackNameUpper}_CHANGESET_NAME"]}?"
    )
  }

  executeChangeSetProperties = [
    region: config.region,
    stackName: config.stackName
  ]

  optionalExecuteChangeSetProperties = [
    'awsAccountId',
    'role',
    'roleArn',
    'maxErrorRetry'
  ]

  optionalExecuteChangeSetProperties.each { property ->
    if (config[property]) {
      executeChangeSetProperties[property] = config[property]
    }
  }

  executeChangeSet(executeChangeSetProperties)

}