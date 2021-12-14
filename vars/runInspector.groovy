/*
Test AMI aginst Inspector rules

Addtional Inspector Rule Package ARN's
https://docs.aws.amazon.com/inspector/latest/userguide/inspector_rules-arns.html#ap-southeast-2

example usage in a pipeline
runInspector(
    amiId: 'ami-0186908e2fdeea8f3',                 # Required
    region: 'ap-southeast-2',                       # Required
    role: 'roleName'                                # Required
    accountId: '0123456789012'                      # Required
    failon: 'Informational|Low|Medium|High|Never',  # Optional
    ruleArns: ['ruleARN1', 'ruleARN2'],             # Optional
    testTime: '3600', (in seconds)                  # Optional
    whitelist: ['CVE-2018-12126', 'CVE-2018-12127'] # Optional
)

Enviroment Variables Written:
    env.FAILED_TESTS   --   Number of tests failed
*/

import com.amazonaws.services.inspector.AmazonInspectorClientBuilder
import com.amazonaws.services.inspector.model.PreviewAgentsRequest
import com.amazonaws.services.inspector.model.StartAssessmentRunRequest
import com.amazonaws.services.inspector.model.GetAssessmentReportRequest
import com.amazonaws.services.inspector.model.DescribeAssessmentRunsRequest
import com.amazonaws.services.inspector.model.ListFindingsRequest
import com.amazonaws.services.inspector.model.DescribeFindingsRequest
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.CreateBucketRequest
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.base2.ciinabox.InstanceMetadata
import com.base2.ciinabox.GetInstanceDetails
import java.util.concurrent.TimeUnit

def call(body) {
    def client = setupEC2Client(body.region, body.accountId, body.role)
    def request = new DescribeImagesRequest().withImageIds(ami)
    def response = client.describeImages(request)

    def stackName = 'InspectorAmiTest' + UUID.randomUUID().toString()
    def bucketName = 'inspectortestbucket' + UUID.randomUUID().toString()
    def fileName = 'Inspector.yaml'
    def findings = ''
    try{
        findings = main(body, stackName, bucketName, fileName)
    } catch(Exception e) {
        println("Error: ${e}")
        println("inspector failed to complete it's run, cleaning up resources before erroring out")
        cleanUp(stackName, body.region, bucketName, fileName)
        throw e
    }
    // Fail the pipeline if insepctor tests did not pass considering passed in threshold
    def failon = body.get('failon', 'Medium').toString().toLowerCase().capitalize()
    def passed = checkFail(failon, findings[0])
    env.FAILED_TESTS = findings[1]
    if (passed == false) {
        throw new GroovyRuntimeException("Inspector found ${findings[1]} potential vulnerabilities")
    } else if((passed == true) & (findings[1] >= 1)) {
        println('Inspector failed on some test however they where under the threshold')
        return
    } else {
        println('No inspector tests failed')
        return
    }

}


def main(body, stackName, bucketName, fileName) {
    // Start sessions for insepctor, S3 and EC2
    def inspector = setupInspectorClient(body.region, body.accountId, body.role)
    println('Establish inspector client')
    def s3 = setupEC2Client(body.region, body.accountId, body.role)
    println('Establish S3 client')
    def ec2 = setupS3Client(body.region, body.accountId, body.role)
    println('Establish EC2 client')


    // Lunch AMI into cloudformaiton stack with sarrounding infrustructure to support scans
    def template = libraryResource('Inspector.yaml')
    createBucket(bucketName, body.region, ec2)
    println('Created temp bucket to store cloudformaiton template')
    s3.putObject(bucketName, fileName, template)
    println('Cloudformaiton uploaded to bucket')
    def os = returnOs(body.amiId, ec2)
    println("The AMI is using ${os} based operating system")

    // Organise which parameters to send
    def params = ['amiId': body.amiId, 'os': os, 'stackName': stackName[-6..-1]]
    if (body.subnetId) {
        params['subnetId'] = body.subnetId
    }
    else {
        params['subnetId'] = getsubnetId(body.region)
    }
    if (body.ruleArns) {
        params['ruleArns'] = body.ruleArns.join(',')
    }
    if (body.testTime) {
        params['testTime'] = body.testTime
    } else if (body.ruleArns) {
        def time = ((20+(body.ruleArns.size()*10))*60)
        params['testTime'] = time.toString()
    }

    cloudformation(
        stackName: stackName,
        action: 'create',
        region: body.region,
        templateUrl: "https://${bucketName}.s3-${body.region}.amazonaws.com/Inspector.yaml",
        waitUntilComplete: 'true',
        role: 'CiinaboxInspectorPipelineMethod',
        parameters: params
    )
    println('Stack uploaded to CloudFormation')

    // Query the stack for instance ID
    def instanceId = cloudformation(
        stackName: stackName,
        queryType: 'output',
        query: 'InstanceId',
        role: 'CiinaboxInspectorPipelineMethod',
        region: body.region
    )

    // Check if instance is actually up, if not wait
    def instancesStatus = getIstanceStatus(instanceId, ec2)
    def timeout = 0
    while (instancesStatus != "running") {
        if (timeout <= 120) { // If the instance isn't up in 10 mins, skip waiting
            instancesStatus = getIstanceStatus(instanceId, ec2)
            println("Insance is in state: ${instancesStatus}")
            timeout += 1
            TimeUnit.SECONDS.sleep(5);
        } else {
            println("Waited 10 minutes for insance to come up, skipping waiting")
            instancesStatus = 'running'
        }
    }

    // Query stack for inspector assessment targets arn (must be an output)
    def targetsArn = cloudformation(
        stackName: stackName,
        queryType: 'output',
        query: 'TargetsArn',
        role: 'CiinaboxInspectorPipelineMethod',
        region: body.region
    )

    println("targetsArn: ${targetsArn}")
    TimeUnit.SECONDS.sleep(120);

    // Check if the agent is up, if not wait
    def agentStatus = getAgentStatus(targetsArn, insepctor)
    timeout = 0
    while (agentStatus != 'HEALTHY') {
        if (timeout <= 120) {
            agentStatus = getAgentStatus(targetsArn, inspector)
            println("Agent health: ${agentStatus}")
            timeout += 1
            TimeUnit.SECONDS.sleep(5);
        } else {
            println("Waited 10 minutes for the agent to become healthy, skipping waiting")
            agentStatus = 'HEALTHY'
        }
    }

    // Query stack for inspector assessment template arn (must be an output)
    def template_arn = cloudformation(
        stackName: stackName,
        queryType: 'output',
        query: 'TemplateArn',
        role: 'CiinaboxInspectorPipelineMethod',
        region: body.region
    )

    // Run the inspector test
    def assessmentArn = assessmentRun(template_arn, inspector)
    println('Inspector test(s) started')

    // Wait for the inspector test to run
    def runStatus = getRunStatus(assessmentArn, inspector)
    while  (runStatus != "COMPLETED") {
          runStatus = getRunStatus(assessmentArn, inspector)
          println("Test Run Status: ${runStatus}")
          TimeUnit.SECONDS.sleep(60);
    }

    // This waits for inspector to finish up everything before an actaul result can be returned, this is not waiting for the test to finish
    def testRunning = true
    while (testRunning.equals(true)) {
          def getResults = getResults(assessmentArn, inspector).toString()
          println("Cleanup Status: ${getResults}")
          if ((getResults.contains("WORK_IN_PROGRESS")).equals(false)) {
                testRunning = false
          }
          TimeUnit.SECONDS.sleep(20);
    }

    // Get the results of the test, write to jenkins and fromated the result to check if the test(s) passed
    getResults = getResults(assessmentArn, inspector)
    def urlRegex = /http.*[^}]/
    def resutlUrl = (getResults =~ urlRegex)
    resutlUrl = resutlUrl[0]
    def fullResult = resutlUrl.toURL().text
    writeFile(file: 'Inspector_test_reults.html', text: fullResult)
    archiveArtifacts(artifacts: 'Inspector_test_reults.html', allowEmptyArchive: true)
    def findings = formatedResults(assessmentArn, body.get('whitelist', []), inspector)
    cleanUp(stackName, body.region, bucketName, fileName)
    return findings
}







def assumeRole(region, accountId, role) {
    def roleArn = "arn:aws:iam::" + accountId + ":role/" + role
    def roleSessionName = "sts-session-" + accountId
    def sts = AWSSecurityTokenServiceClientBuilder.standard()
        .withEndpointConfiguration(new EndpointConfiguration("sts.${region}.amazonaws.com", region))
        .build()

    println("Assuming role ${roleArn}")

    def assumeRoleResult = sts.assumeRole(new AssumeRoleRequest()
        .withRoleArn(roleArn)
        .withRoleSessionName(roleSessionName))

    return new BasicSessionCredentials(assumeRoleResult.getCredentials().getAccessKeyId(),
                                    assumeRoleResult.getCredentials().getSecretAccessKey(),
                                    assumeRoleResult.getCredentials().getSessionToken())
}


def setupEC2Client(region, accountId, role) {
    def client = AmazonEC2ClientBuilder.standard().withRegion(region)
    def creds = assumeRole(region, accountId, role)
    client.withCredentials(new AWSStaticCredentialsProvider(creds))
    return client.build()
}


def setupInspectorClient(region, accountId, role) {
    def client = AmazonInspectorClientBuilder.standard().withRegion(region)
    def creds = assumeRole(region, accountId, role)
    client.withCredentials(new AWSStaticCredentialsProvider(creds))
    return client.build()
}


def setupS3Client(region, accountId, role) {
    def client = AmazonS3ClientBuilder.standard().withRegion(region)
    def creds = assumeRole(region, accountId, role)
    client.withCredentials(new AWSStaticCredentialsProvider(creds))
    return client.build()
}


def getsubnetId(region) {
    println "looking up networking details to launch packer instance in"

    // if the node is a ec2 instance using the ec2 plugin
    def instanceId = env.NODE_NAME.find(/i-[a-zA-Z0-9]*/)

    // if node name is not an instance id, try getting the instance id from the instance metadata
    if (!instanceId) {
      println "retrieving the instance metadata"
      def metadata = new InstanceMetadata()
      if (!metadata.isEc2) {
        throw new GroovyRuntimeException("unable to lookup networking details, try specifing (vpcId: subnet: securityGroup: instanceProfile:) in your method")
      }
      instanceId = metadata.getInstanceId()
    }

    // get networking details from the instance
    def instance = new GetInstanceDetails(region, instanceId)
    return instance.subnet()
}


def cleanUp(String stackName, String region, String bucketName, String fileName){
    // Pull down cloudformaiton stack and bucket hosting cloudformation template
    println('Cleaning up all created resources')
    try {
        cloudformation(
            stackName: stackName,
            action: 'delete',
            region: region,
            waitUntilComplete: 'true',
            role: 'CiinaboxInspectorPipelineMethod',
        )
    }
    catch (Exception e) {
        println("Unable to delete stack, error: ${e}")
    }
    try {
        s3.deleteObject(bucketName, fileName)
        s3.deleteBucket(bucketName)
        println('All creted resources are now deleted')
    }
    catch (Exception e) {
        println("Unable to clean/destroy bucket, error: ${e}")
    }
}

def checkFail(failon, findings){
    // Make a list of severity levels with >= 1 nFindings
    def severties = findings.keySet()
    def severityFindings = []
    for (s in severties){
        if (findings[s].toInteger() > 0 ) {
            severityFindings.add(s)
        }
    }

    def validSeverity = ['']
    switch(failon) {
        case 'Informational':
            validSeverity = ['Informational', 'Low', 'Medium', 'High']
            break
        case 'Low':
            validSeverity = ['Low', 'Medium', 'High']
            break
        case 'Medium':
            validSeverity = ['Medium', 'High']
            break
        case 'High':
            validSeverity = ['High']
            break
        case 'Never':
            validSeveritgroovyy = ['']
            break
        default:
            println('Non-valid failon level given, valid options are \'Informational\', \'Low\', \'Medium\', \'High\' and \'Never.\' Medium is used as the default')
            validSeverity = ['Medium', 'High']

    }
    def testPassed = true
    validSeverity.each { severity ->
        if (severityFindings.contains(severity)) {
            testPassed = false
        }
    }
    return testPassed
}


def getAgentStatus(String arn, client) {
     def request = new PreviewAgentsRequest().withPreviewAgentsArn(arn)
     def response = client.previewAgents(request)
     println("response: ${response}")
     def state = response.getAgentPreviews()[0].getAgentHealth()
     return state
}


def getIstanceStatus(String id, client) {
    def request = new DescribeInstancesRequest().withInstanceIds(id)
    def response = client.describeInstances(request)
    def state = response.getReservations()[0].getInstances()[0].getState().getName()
    return state
}


def createBucket(String name, String region, client) {
      def request = new CreateBucketRequest(name, region)
      client.createBucket(request)
}


def returnOs(String ami, client) {
    def request = new DescribeImagesRequest().withImageIds(ami)
    def response = client.describeImages(request)
    response = response.getImages()[0].getPlatformDetails()

    if (response == 'Windows') {
        return('Windows')
    } else {
        return('Linux')
    }
}


def assessmentRun(String template_arn, client) {
    def request = new StartAssessmentRunRequest().withAssessmentTemplateArn(template_arn)
    def response = client.startAssessmentRun(request)
    return response.getAssessmentRunArn()
}


def getResults(String result_arn, client) {
    def request = new GetAssessmentReportRequest()
    .withAssessmentRunArn(result_arn)
    .withReportFileFormat('HTML') // HTML OR PDF
    .withReportType('FULL') //FINDING OR FULL
    def response = client.getAssessmentReport(request)
    return response
}


def formatedResults(arn, whitelist, client) {
    def finding_arns = []

    def request = new ListFindingsRequest().withAssessmentRunArns(arn)
    def response = client.listFindings(request)
    finding_arns += response.findingArns

    while (response.nextToken != null) {
        request = new ListFindingsRequest().withAssessmentRunArns(arn).withNextToken(response.nextToken)
        response = client.listFindings(request)
        finding_arns += response.findingArns
    }

    def severities = ['High': 0, 'Medium': 0, 'Low': 0, 'Informational': 0]

    finding_arns.each { finding ->
        request = new DescribeFindingsRequest().withFindingArns(finding)
        response = client.describeFindings(request).getFindings()
        cve = response.id[0]
        if (!whitelist.contains(cve)) {
            severities[response.severity[0]] += 1
        }
    }
    print("\nseverities: ${severities}")

    def total_findings = severities['High'] + severities['Low'] + severities['Medium'] + severities['Informational']

    if (total_findings >= 1) {
        println("****************\nTest(s) not passed ${total_findings} issue found\nAMI failed insecptor test(s), see insepctor for details via saved file in workspace, AWS CLI or consolet\nFindings by Risk\nHigh: ${severities['High']}\nMedium: ${severities['Medium']}\nLow: ${severities['Low']}\nInformational: ${severities['Informational']}\n****************")
        return [severities, total_findings]
    }
    else {
        println('Test(s) passed')
        return [severities, total_findings]
    }
}


def getRunStatus (String arn, client) {
      def request = new DescribeAssessmentRunsRequest()
        .withAssessmentRunArns(arn)
      def response = client.describeAssessmentRuns(request)
      def state = response.getAssessmentRuns()[0].getState()
      return state
}
