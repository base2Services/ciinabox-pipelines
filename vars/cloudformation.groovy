/***********************************
cloudformation DSL

performs cloudformation operations

example usage
cloudformation(
  stackName: 'dev',
  queryType: 'element' | 'output' | 'status' | 'parameter' | 'export', # either queryType or action should be supplied
  query: 'mysubstack.logicalname1' | 'outputKey' | 'parameterKey' | 'export-name', # depending on queryType
  action: 'create'|'update'|'delete'|'exists',
  region: 'ap-southeast-2',
  templateUrl: 'https://s3.amazonaws.com/mybucket/cloudformation/app/master.json',
  parameters: [
    'ENVIRONMENT_NAME' : 'dev',
  ],
  accountId: '1234567890', #the aws account Id you want the stack operation performed in
  role: 'myrole', # the role to assume from the account the pipeline is running from,
  tags: [
    'Environment': 'dev'
  ],
  snsTopics: [
    'arn:aws:sns:us-east-2:000000000000:notifications'
  ],
  maxErrorRetry: 3,
  waitUntilComplete: true,
  roleArn: 'arn:aws:iam::<accountid>:role/deploy' // (optional, specify cloudformation service role)
)

If you omit the templateUrl then for updates it will use the existing template

************************************/

import com.amazonaws.auth.*
import com.amazonaws.regions.*
import com.amazonaws.services.cloudformation.*
import com.amazonaws.services.cloudformation.model.*
import com.amazonaws.services.s3.*
import com.amazonaws.services.s3.model.*
import com.amazonaws.services.securitytoken.*
import com.amazonaws.services.securitytoken.model.*
import com.amazonaws.services.simplesystemsmanagement.*
import com.amazonaws.services.simplesystemsmanagement.model.*
import com.amazonaws.waiters.*
import com.amazonaws.ClientConfiguration
import com.amazonaws.retry.RetryPolicy
import com.amazonaws.retry.PredefinedRetryPolicies.SDKDefaultRetryCondition
import com.amazonaws.retry.PredefinedBackoffStrategies.SDKDefaultBackoffStrategy
import org.yaml.snakeyaml.Yaml
import groovy.json.JsonSlurperClassic
import java.util.concurrent.*
import java.io.InputStreamReader
import com.base2.ciinabox.aws.IntrinsicsYamlConstructor

def call(body) {
  def config = body
  def cf = setupCfClient(config.region, config.accountId, config.role, config.maxErrorRetry)

  if(!(config.action || config.queryType)){
    throw new GroovyRuntimeException("Either action or queryType (or both) must be specified")
  }

  if(config.action){
    handleActionRequest(cf, config)
  }

  if(config.queryType){
    return handleQueryRequest(cf, config)
  }

}

@NonCPS
def handleActionRequest(cf, config){
  def success = false

  switch(config.action) {
    case 'exists':
      if(doesStackExist(cf,config.stackName)) {
        println "Environment ${config.stackName} already exists"
        env["${config.stackName}_exists"] = 'true'
      } else {
        env["${config.stackName}_exists"] = 'false'
        println "Environment ${config.stackName} does not exist"
      }
      success = true
      break
    case 'create':
      if(!doesStackExist(cf,config.stackName)) {
        create(cf, config)
        if(config.waitUntilComplete != false) {
          success = wait(cf, config.stackName, StackStatus.CREATE_COMPLETE)
        } else {
          println "Not waiting for stack ${config.stackName} to Create"
          success = true
        }
      } else {
        println "Environment ${config.stackName} already Exists"
        success = true
      }
      break
    case 'delete':
      delete(cf, config)
      if(config.waitUntilComplete != false) {
        success = wait(cf, config.stackName, StackStatus.DELETE_COMPLETE)
      } else {
        println "Not waiting for stack ${config.stackName} to Delete"
        success = true
      }
      break
    case 'update':
      if(update(cf, config)) {
        if(config.waitUntilComplete != false) {
          success = wait(cf, config.stackName, StackStatus.UPDATE_COMPLETE)
        } else {
          println "Not waiting for stack ${config.stackName} to Update"
          success = true
        }
      } else {
        success = true
      }
    break
  }
  if(!success) {
    printFailedStackEvents(cf, config.stackName, config.region)
    throw new Exception("Stack ${config.stackName} failed to ${config.action}")
  }
}

@NonCPS
def handleQueryRequest(cf, config){
  if(!doesStackExist(cf,config.stackName)){
    throw new GroovyRuntimeException("Can't query stack ${config.stackName} as it does not exist")
  }
  switch(config.queryType){
    case 'element':
      return queryStackElement(cf, config)
    case 'output':
      return queryStackOutput(cf, config)
    case 'export':
      return queryStackExport(cf, config)
    case 'status':
      return queryStackStatus(cf, config)
    case 'parameter':
      return queryStackParams(cf, config)
    default:
      throw new GroovyRuntimeException("Unknown queryType '${config.queryType}'. Valid types are: element,output")
  }
}

@NonCPS
def queryStackElement(cf, config){
  try {
    def cfnPathElements = config.query.split("\\."),
        elementName = cfnPathElements[0],
        result = cf.describeStackResource(new DescribeStackResourceRequest()
                .withStackName(config.stackName)
                .withLogicalResourceId(elementName)
        )

        if(cfnPathElements.size() > 1){
          config.query = cfnPathElements[1..cfnPathElements.size()-1].join('.')
          config.stackName = result.stackResourceDetail.physicalResourceId
          return queryStackElement(cf, config)
        }

        if(cfnPathElements.size() == 1){
          return [
                  LogicalResourceId: result.stackResourceDetail.logicalResourceId,
                  PhysicalResourceId: result.stackResourceDetail.physicalResourceId,
                  ResourceStatus: result.stackResourceDetail.resourceStatus,
                  StackId: result.stackResourceDetail.stackId
          ]
        }
  } catch (AmazonCloudFormationException ex) {
    throw new GroovyRuntimeException("Couldn't describe stack resource ${config.query} on stack ${config.stackName}", ex)
  }
}

@NonCPS
def queryStackOutput(cf, config){
  try {
    def result = cf.describeStacks(new DescribeStacksRequest().withStackName(config.stackName))
    def stackInfo = result.getStacks().get(0),
        output = stackInfo.getOutputs().find { it.outputKey.equals(config.query) }

    if (output == null){
      throw new GroovyRuntimeException("Stack ${config.stackName} does not have output named '${config.query}'")
    }

    return output.outputValue

  } catch (AmazonCloudFormationException ex) {
    throw new GroovyRuntimeException("Couldn't describe stack ${config.stackName}", ex)
  }
}

@NonCPS
def queryStackExport(cf, config){
  def exports = []
  def result = cf.listExports(new ListExportsRequest())
  exports += result.getExports() 
  
  while(result.nextToken != null) {
    result = cf.listExports(new ListExportsRequest().withNextToken(result.nextToken));
    exports += result.getExports() 
  }

  def export = exports.find { it.getName().equals(config.query.toString()) }

  if (export == null){
    throw new GroovyRuntimeException("Unable to find cloudformation export ${config.query}")
  }

  return export.getValue()
}


@NonCPS
def queryStackStatus(cf, config){
  try {
    def result = cf.describeStacks(new DescribeStacksRequest().withStackName(config.stackName))
    def currentState = result.getStacks().get(0).getStackStatus().toString()

    if(config.waitUntilComplete != true) {
      return currentState
    }
    
    statePrefix = currentState.tokenize("_")[0]
    
    if (statePrefix in ["CREATE", "DELETE", "UPDATE"]) {
      successStatus = StackStatus.valueOf(statePrefix + "_COMPLETE")
      success = wait(cf, config.stackName, successStatus)
      if(!success) {
        printFailedStackEvents(cf, config.stackName, config.region)
        throw new Exception("Stack ${config.stackName} failed to reach state ${successStatus}")
      }
    } else {
      throw new Exception("Stack ${config.stackName} with status ${currentState} cannot be waited for")
    }
  } catch (AmazonCloudFormationException ex) {
    throw new GroovyRuntimeException("Couldn't describe stack ${config.stackName}", ex)
  }
}

@NonCPS
def queryStackParams(cf, config){
  try {
    def result = cf.describeStacks(new DescribeStacksRequest().withStackName(config.stackName))
    def stackInfo = result.getStacks().get(0),
        parameter = stackInfo.getParameters().find { it.getParameterKey().equals(config.query) }

    if (parameter == null){
      throw new GroovyRuntimeException("Stack ${config.stackName} does not have param named '${config.query}'")
    }

    return parameter.getParameterValue()

  } catch (AmazonCloudFormationException ex) {
    throw new GroovyRuntimeException("Couldn't describe stack ${config.stackName}", ex)
  }
}

@NonCPS
def create(cf, config) {
  println "Creating stack ${config.stackName}"
  if(config.stackState) {
    config.stackState = config.stackState.endsWith('/') ? config.stackState[0..-2] : config.stackState
    restoreStackState(cf, config)
  }
  def params = []
  config.parameters.each {
    params << new Parameter().withParameterKey(it.key).withParameterValue(it.value)
  }
  def request = new CreateStackRequest()
    .withStackName(config.stackName)
    .withCapabilities('CAPABILITY_IAM', 'CAPABILITY_NAMED_IAM', 'CAPABILITY_AUTO_EXPAND')
    .withParameters(params)
    .withTemplateURL(config.templateUrl)

  def tags = []
  config.tags.each {
    tags << new Tag().withKey(it.key).withValue(it.value)
  }
  if (tags.size() > 0) {
    request.withTags(tags)
  }
  
  if (config.snsTopics) {
    request.withNotificationARNs(config.snsTopics)
  }
  
  if (config.roleArn) {
    request.withRoleARN(config.roleArn)
  }
  
  cf.createStack(request)
}

@NonCPS
def delete(cf, config) {
  if(doesStackExist(cf, config.stackName)) {
    waitUntilComplete(cf, config.stackName)
    if(config.stackState) {
      config.stackState = config.stackState.endsWith('/') ? config.stackState[0..-2] : config.stackState
      saveStackState(cf,config)
    }
    cf.deleteStack(new DeleteStackRequest()
      .withStackName(config.stackName)
    )
  } else {
    println "ignoring delete since stack ${config.stackName} does not exist"
  }
}

@NonCPS
def update(cf, config) {
  if(doesStackExist(cf, config.stackName)) {
    waitUntilComplete(cf, config.stackName)
    def request = new UpdateStackRequest()
      .withStackName(config.stackName)
      .withParameters(getStackParams(cf, config))
      .withCapabilities('CAPABILITY_IAM', 'CAPABILITY_NAMED_IAM', 'CAPABILITY_AUTO_EXPAND')
      
    if(config['templateUrl']) {
      request.withTemplateURL(config.templateUrl)
    } else {
      request.withUsePreviousTemplate(true)
    }

    def tags = []
    config.tags.each {
      tags << new Tag().withKey(it.key).withValue(it.value)
    }
    if (tags.size() > 0) {
      request.withTags(tags)
    }
    
    if (config.snsTopics) {
      request.withNotificationARNs(config.snsTopics)
    }
    
    if (config.roleArn) {
      request.withRoleARN(config.roleArn)
    }

    try {
      cf.updateStack(request)
      return true
    } catch(AmazonCloudFormationException ex) {
      if(!ex.message.contains("No updates are to be performed")) {
        throw ex
      }
      return false
    }
  } else {
    throw new Exception("unable to update stack ${config.stackName} it does not exist")
  }
}


@NonCPS
def getStackParams(cf, config) {
  def stackName = config.stackName
  def overrideParams = config.parameters
  def stackParams = [:]
  def newTemplateParams = []
  def stacks = cf.describeStacks(new DescribeStacksRequest().withStackName(stackName)).getStacks()
  if(config.templateUrl != null){
    newTemplateParams = getTemplateParameterNames(config)
  }
  if (!stacks.isEmpty()) {
    for(Parameter param: stacks.get(0).getParameters()) {
      // if new template is part of stack update, we need to check for any
      // removed parameters in new template
      if(config.templateUrl != null){
        if(!newTemplateParams.contains(param.getParameterKey())){
          println "Stack parameter ${param.getParameterKey()} not present in template ${config.templateUrl}, thus " +
                  "removing it from stack update operation"
          continue
        }
      }
      stackParams.put(param.getParameterKey(), new Parameter().withParameterKey(param.getParameterKey()).withUsePreviousValue(true))
    }
    overrideParams.each {
      stackParams.put(it.key, new Parameter().withParameterKey(it.key).withParameterValue(it.value))
    }
  }
  println "stack params: ${stackParams.values()}"
  return stackParams.values()
}

@NonCPS
def wait(cf, stackName, successStatus)   {
  def waiter = null
  switch(successStatus) {
    case StackStatus.CREATE_COMPLETE:
      waiter = cf.waiters().stackCreateComplete()
    break
    case StackStatus.UPDATE_COMPLETE:
      waiter = cf.waiters().stackUpdateComplete()
    break
    case StackStatus.DELETE_COMPLETE:
      waiter = cf.waiters().stackDeleteComplete()
    break
  }
  try {
    Future future = waiter.runAsync(
      new WaiterParameters<>(new DescribeStacksRequest().withStackName(stackName)),
      new NoOpWaiterHandler()
    )
    while(!future.isDone()) {
      try {
        echo "waiting for stack operation to complete"
        Thread.sleep(10000)
      } catch(InterruptedException ex) {
          echo "We seem to be timing out ${ex}...ignoring"
      }
    }
    // confirm that end state equals requested state,
    // as sdk waiters will exit not only on success, but also on failure
    // states. e.g. UPDATE_ROLLBACK_COMPLETE for UPDATE_COMPLETE
    try {
      DescribeStacksResult result = cf.describeStacks(new DescribeStacksRequest().withStackName(stackName))
      currentState = result.getStacks().get(0).getStackStatus()
      def success = currentState.toString().equals(successStatus.toString())
      println "Stack ${stackName} end state: ${currentState}"
      println "Stack ${stackName} required state: ${successStatus}"
      println ">>>> ${success ? 'SUCCESS' : 'FAILURE'}"

      return success
    } catch (AmazonCloudFormationException ex){
      if(ex.getErrorMessage().contains("does not exist")){
        if(successStatus == StackStatus.DELETE_COMPLETE){
          return true
        } else {
          throw new GroovyRuntimeException("Stack ${stackName} does not exist!!")
        }
      } else {
        throw ex
      }
    }
   } catch(Exception e) {
     println "Stack: ${stackName} failed - ${e}"
     return false
   }
}

@NonCPS
def doesStackExist(cf, stackName) {
  try {
    DescribeStacksResult result = cf.describeStacks(new DescribeStacksRequest().withStackName(stackName))
    return result != null
  } catch (AmazonCloudFormationException ex) {
    if(ex.message.contains("does not exist")) {
      return false
    } else {
      throw ex
    }
  }
}

@NonCPS
def setupCfClient(region, awsAccountId = null, role =  null, maxErrorRetry=10) {
  ClientConfiguration clientConfiguration = new ClientConfiguration()
  maxErrorRetry = (maxErrorRetry == null)? 10 : maxErrorRetry
  clientConfiguration.withRetryPolicy(new RetryPolicy(new SDKDefaultRetryCondition(), new SDKDefaultBackoffStrategy(), maxErrorRetry, true))
  
  def cb = AmazonCloudFormationClientBuilder.standard()
    .withRegion(region)
    .withClientConfiguration(clientConfiguration)
  def creds = getCredentials(awsAccountId, region, role)
  if(creds != null) {
    cb.withCredentials(new AWSStaticCredentialsProvider(creds))
  }
  return cb.build()
}

@NonCPS
def setupS3Client(region, awsAccountId = null, role =  null) {
  def cb = AmazonS3ClientBuilder.standard().withRegion(region)
  def creds = getCredentials(awsAccountId, region, role)
  if(creds != null) {
    cb.withCredentials(new AWSStaticCredentialsProvider(creds))
  }
  return cb.build()
}

@NonCPS
def setupSSMClient(region, awsAccountId = null, role =  null) {
  def cb = AWSSimpleSystemsManagementClientBuilder.standard().withRegion(region)
  def creds = getCredentials(awsAccountId, region, role)
  if(creds != null) {
    cb.withCredentials(new AWSStaticCredentialsProvider(creds))
  }
  return cb.build()
}

@NonCPS
def getCredentials(awsAccountId, region, roleName) {
  if(env['AWS_SESSION_TOKEN'] != null) {
    return new BasicSessionCredentials(
      env['AWS_ACCESS_KEY_ID'],
      env['AWS_SECRET_ACCESS_KEY'],
      env['AWS_SESSION_TOKEN']
    )
  } else if(awsAccountId != null && roleName != null) {
    def stsCreds = assumeRole(awsAccountId, region, roleName)
    return new BasicSessionCredentials(
      stsCreds.getAccessKeyId(),
      stsCreds.getSecretAccessKey(),
      stsCreds.getSessionToken()
    )
  } else {
    return null
  }
}

@NonCPS
def assumeRole(awsAccountId, region, roleName) {
  def roleArn = "arn:aws:iam::" + awsAccountId + ":role/" + roleName
  def roleSessionName = "sts-session-" + awsAccountId
  println "assuming IAM role ${roleArn}"
  def sts = new AWSSecurityTokenServiceClient()
  if (!region.equals("us-east-1")) {
      sts.setEndpoint("sts." + region + ".amazonaws.com")
  }
  def assumeRoleResult = sts.assumeRole(new AssumeRoleRequest()
            .withRoleArn(roleArn).withDurationSeconds(3600)
            .withRoleSessionName(roleSessionName))
  return assumeRoleResult.getCredentials()
}

@NonCPS
def waitUntilComplete(cf, stackName) {
  DescribeStacksResult result = cf.describeStacks(new DescribeStacksRequest().withStackName(stackName))
  currentState = result.getStacks().get(0).getStackStatus()
  waitStatus = StackStatus.UPDATE_COMPLETE
  switch(currentState) {
    case 'CREATE_COMPLETE':
    case 'UPDATE_COMPLETE':
    case 'DELETE_FAILED':
      return
    break
    case 'CREATE_IN_PROGRESS':
      waitStatus = StackStatus.CREATE_COMPLETE
    break
  }
  print "waiting for stack ${stackName} to finish - ${currentState}"
  wait(cf, stackName, waitStatus)
}

@NonCPS
def getTemplateParameterNames(config){
  def newTemplateParams = [],
    s3location = s3bucketKeyFromUrl(config.templateUrl),

    // https://github.com/aws/aws-sdk-java/issues/1451#issuecomment-358742502
    // https://github.com/aws/aws-sdk-java/issues/1338
    // never using us-east-1 for getBucketLocation or it will yield weird `The authorization header is malformed`
    s3headClient = setupS3Client((config.region == "us-east-1")? "us-east-2" : config.region),
    newTemplate = null



    def headBucketRegion = s3headClient.getBucketLocation(s3location.bucket)
    if(headBucketRegion == '' || headBucketRegion == 'US'){
      headBucketRegion = 'us-east-1'
    } else if(headBucketRegion == 'EU'){
      headBucketRegion = 'eu-west-1'
    }

    println "Using region ${headBucketRegion} to grab template ${config.templateUrl}"
    def s3getClient = setupS3Client(headBucketRegion, config.accountId, config.role),
    templateBody = s3getClient.getObject(new GetObjectRequest(s3location.bucket, s3location.key)).getObjectContent()

  if(s3location.key.endsWith('yaml') || s3location.key.endsWith('yml')){
    Yaml yaml = new Yaml(new IntrinsicsYamlConstructor())
    newTemplate = yaml.load(templateBody)
  } else {
    //fallback on json
    newTemplate = parseJsonToMap(templateBody)
  }
  if(newTemplate.Parameters){
    newTemplate.Parameters.each { cfParamName, cfParamDef ->
      newTemplateParams << cfParamName
    }
  }
  return newTemplateParams
}

@NonCPS
def restoreStackState(cf, config) {
  def ssm = setupSSMClient(config.region, config.accountId, config.role)

  //no parameters passed in
  if(!config.parameters) {
    config.parameters = [:]
  }

  //set the config.templateUrl config
  setCfTemplateUrl(ssm, config, "${config.stackState}/${config.stackName}/")

  println "using template url: ${config.templateUrl}"

  //adds config.parameters unless they are passed in
  setStackParams(ssm, config, "${config.stackState}/${config.stackName}/parameters/")

  message = "Creating stack ${config.stackName} from ${config.templateUrl} using ssm params\n"
  config.parameters.each {
    message += "${it.key}=${it.value}\n"
  }
  println message
}

@NonCPS
def getSSMParams(ssm, path) {
  def ssmParams = []
  def result = ssm.getParametersByPath(new GetParametersByPathRequest()
    .withPath(path)
    .withRecursive(false)
    .withWithDecryption(true)
    .withMaxResults(10)
  )
  ssmParams += result.parameters
  while(result.nextToken != null) {
    result = ssm.getParametersByPath(new GetParametersByPathRequest()
        .withPath(path)
        .withRecursive(false)
        .withWithDecryption(true)
        .withMaxResults(10)
        .withNextToken(result.nextToken)
    )
    ssmParams += result.parameters
  }
  return ssmParams
}

@NonCPS
def setStackParams(ssm, config, basePath) {
  def ssmParams = getSSMParams(ssm, basePath)
  ssmParams.each { param ->
    paramName = param.name.split('/').last()
    if(!config.parameters[paramName]) {
      config.parameters[paramName] = param.value
    }
  }
  //set any parameters that weren't defined at the stack level
  if(basePath != "${config.stackState}/parameters") {
    setStackParams(ssm, config, "${config.stackState}/parameters")
  }
}

@NonCPS
def setCfTemplateUrl(ssm, config, basePath) {
  // the templateUrl is being passed in as part of the create stack action
  if(config.templateUrl) {
    return
  }

  def ssmParams = getSSMParams(ssm, basePath)
  ssmParams.each { param ->
    paramName = param.name.split('/').last()
    if(paramName == 'CfTemplateUrl') {
      config.templateUrl = param.value
    }
  }

  //try at the stack default level
  if(basePath != "${config.stackState}/") {
    setCfTemplateUrl(ssm, config, "${config.stackState}/")
  }
  if(!config.templateUrl) {
    throw new GroovyRuntimeException("Unable to load CfTemplateUrl ssm param for stack ${config.stackName} from ssm path ${basePath}")
  }
}

@NonCPS
def saveStackState(cf, config) {
  def stacks = cf.describeStacks(new DescribeStacksRequest().withStackName(config.stackName)).getStacks()
  def basePath = "${config.stackState}/${config.stackName}"
  def out = 'saving params:\n'
  if (!stacks.isEmpty()) {
    def ssm = setupSSMClient(config.region, config.accountId, config.role)
    for(Parameter param: stacks.get(0).getParameters()) {
      if(param.getParameterValue() != null && param.getParameterValue() != '') {
        def result = ssm.putParameter(new PutParameterRequest()
          .withName("${basePath}/parameters/${param.getParameterKey()}")
          .withType('String')
          .withValue(param.getParameterValue())
          .withOverwrite(true)
        )
        out += "${basePath}/${param.getParameterKey()}=${param.getParameterValue()}\n"
      }
    }
    for(Output output : stacks.get(0).getOutputs()) {
      if(output.outputKey.startsWith('CfTemplate')) {
        def result = ssm.putParameter(new PutParameterRequest()
          .withName("${basePath}/${output.outputKey}")
          .withType('String')
          .withValue(output.outputValue)
          .withOverwrite(true)
        )
      }
      out += "${basePath}/${output.outputKey}=${output.outputValue}\n"
    }
    println out
  }
}

@NonCPS
def parseJsonToMap(s3inputStream) {
  final slurper = new JsonSlurperClassic()
  return new HashMap<>(slurper.parse(new InputStreamReader(s3inputStream)))
}

@NonCPS
def s3bucketKeyFromUrl(String s3url) {
  def parts = s3url.split("/"),
      domain = parts[2],
      domainParts = domain.split('\\.')

  //format https://$bucket.s3.amazonaws.com/key (http://bucket.s3.amazonaws.com)
  if (domain.endsWith('.s3.amazonaws.com')) {
    return [
            bucket: domain.replace('.s3.amazonaws.com', ''),
            key   : parts[3..-1].join("/")
    ]
    //format https://$bucket.s3.$region.amazonaws.com/$key
  } else if (domain.matches('.+\\.s3\\..+\\.amazonaws\\.com')) {
    return [
            bucket: domain.split('.s3.')[0],
            key: parts[3..-1].join("/")
    ]
    //format https://bucket.s3-$region.amazonaws.com/key
  } else if (domain.endsWith('.amazonaws.com') && domainParts.size() > 3 && domainParts[-3].startsWith('s3-')) {
    return [
            bucket: domainParts[0..-4].join('.'),
            key   : parts[3..-1].join("/")
    ]
    //format https://s3-$region.amazonaws.com/$bucket/$key
    //format https://s3.amazonaws.com/$bucket/$key
  }else if ((domain.endsWith('.amazonaws.com') && domain.startsWith('s3-')) || (domain == 's3.amazonaws.com')) {
    return [
            bucket: parts[3],
            key   : parts[4..-1].join("/")
    ]
  }
}

@NonCPS
def getStackEvents(cf, stackName) {

   final DescribeStackEventsRequest request = new DescribeStackEventsRequest().withStackName(stackName)

   try {
    final DescribeStackEventsResult result = cf.describeStackEvents(request)
    return result.getStackEvents()
  } catch (AmazonCloudFormationException ex) {
    if(ex.message.contains("does not exist")) {
      return false
    } else {
      throw ex
    }
  }

   return Collections.emptyList();

}

@NonCPS
def printStackEvent(StackEvent event, stackName, region) {
  final StringBuilder text = new StringBuilder(128)

  def reason = event.getResourceStatusReason()
  if (reason) {
    if (!reason.matches("User Initiated")) {
      text.append("\nTIME: ${event.getTimestamp()}")
      text.append("\nTYPE: ${event.getResourceType()}")
      text.append("\nSTACK: ${stackName}")
      text.append("\nSTATUS: ${event.getResourceStatus()}")
      text.append("\nERROR: ${event.getResourceStatusReason()}")
      text.append("\nEVENT: ${getEventUrl(region,event.getStackId())}")
    }
  }
  println text
}

// Grabs all events for a stack and prints details of each failed resource
// including error message and link to event in web console
@NonCPS
def printFailedStackEvents(cf, stackName, region) {
  // grab all events
  def events = getStackEvents(cf,stackName)
  events.each { event ->
    def eventStatus = event.getResourceStatus()
    if (eventStatus.matches("CREATE_FAILED|ROLLBACK_IN_PROGRESS|UPDATE_FAILED|DELETE_FAILED")) {
      // If resource is a nested stack look through nested stack
      if (event.getResourceType() == "AWS::CloudFormation::Stack") {
        def nestStackName = event.getPhysicalResourceId()
        def nestedEvents = getStackEvents(cf,nestStackName)
        nestedEvents.each { nestedEvent ->
          def nestedEventStatus = nestedEvent.getResourceStatus()
          if (nestedEventStatus.matches("CREATE_FAILED|ROLLBACK_IN_PROGRESS|UPDATE_FAILED|DELETE_FAILED")) {
            printStackEvent(nestedEvent,nestStackName,region)
          }
        }
      } else {
        printStackEvent(event,stackName,region)
      }
    }
  }
}

// Generates the event url for the event in the cloudformation dashboard
@NonCPS
def getEventUrl(region,stackId) {
  def encodedStackId = java.net.URLEncoder.encode(stackId, "UTF-8")
  return "https://${region}.console.aws.amazon.com/cloudformation/home?region=${region}#/stacks/${encodedStackId}/events"
}