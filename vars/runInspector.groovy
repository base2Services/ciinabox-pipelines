/*
Test AMI aginst Inspector rules

Addtional Inspector Rule Package ARN's
https://docs.aws.amazon.com/inspector/latest/userguide/inspector_rules-arns.html#ap-southeast-2

example usage in a pipeline
runInspector(
     region: 'ap-southeast-2',                      # Required
     amiId: 'ami-0186908e2fdeea8f3'                 # Required
     failonfinding: 'False',                        # Optional
     ruleArns: ['ruleARN1', 'ruleARN2']             # Optional
     testTime: '120',                               # Optional
)
*/
import com.amazonaws.services.inspector.AmazonInspector
import com.amazonaws.services.inspector.AmazonInspectorClientBuilder
import com.amazonaws.services.inspector.model.*
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model.*
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.*
import com.amazonaws.services.s3.model.*
import java.util.concurrent.TimeUnit
import java.util.*


def call(body) {
    def stackName = 'InspectorAmiTest' + UUID.randomUUID().toString()
    def bucketName = 'inspectortestbucket' + UUID.randomUUID().toString()
    def fileName = 'Inspector.yaml'
    try{
        def run = main(body, stackName, bucketName, fileName)
        return run
    } catch(Exception e) {
        println(e)
        cleanUp(stackName, body.region, bucketName, fileName)
        return 1
    }
}


def main(body, stackName, bucketName, fileName) {
    // Lunch AMI into cloudformaiton stack with sarrounding infrustructure to support scans
    def template = libraryResource('Inspector.yaml')
    createBucket(bucketName, body.region)
    println('Created temp bucket to store cloudformaiton template')
    uploadFile(bucketName, fileName, template, body.region)
    println('Cloudformaiton uploaded to bucket')
    def os = returnOs(body.amiId)
    println("The AMI is using ${os} based operating system")

    // Organise which parameters to send
    def params = ['amiId': body.amiId, 'os': os]
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
        templateUrl: "https://${bucketName}.s3-ap-southeast-2.amazonaws.com/Inspector.yaml",
        waitUntilComplete: 'true',
        parameters: params
    )
    println('Stack uploaded to CloudFormation')

    // Query the stack for instance ID
    def instanceId = cloudformation(
            stackName: stackName,
            queryType: 'output',
            query: 'InstanceId',
            region: body.region
    )

    // Check if instance is actually up, if not wait
    def instancesStatus = getIstanceStatus(instanceId)
    def timeout = 0
    while (instancesStatus != "running") {
        if (timeout <= 120) { // If the instance isn't up in 10 mins, skip waiting
            instancesStatus = getIstanceStatus(instanceId)
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
            region: body.region
    )

    // Check if the agent is up, if not wait
    def agentStatus = getAgentStatus(targetsArn)
    timeout = 0
    while (agentStatus != 'HEALTHY') {
        if (timeout <= 120) {
            agentStatus = getAgentStatus(targetsArn)
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
            region: body.region
    )

    // Run the inspector test
    def assessmentArn = assessmentRun(template_arn)
    println('Inspector test(s) started')

    // Wait for the inspector test to run
    def runStatus = getRunStatus(assessmentArn)
    while  (runStatus != "COMPLETED") {
          runStatus = getRunStatus(assessmentArn)
          println("Test Run Status: ${runStatus}")
          TimeUnit.SECONDS.sleep(5);
    }

    // This waits for inspector to finish up everything before an actaul result can be returned, this is not waiting for the test to finish
    def testRunning = true
    while (testRunning.equals(true)) {
          def getResults = getResults(assessmentArn).toString()
          println("Cleanup Status: ${getResults}")
          if ((getResults.contains("WORK_IN_PROGRESS")).equals(false)) {
                testRunning = false
          }
    }

    // Get the results of the test, write to jenkins and fromated the result to check if the test(s) passed
    getResults = getResults(assessmentArn)
    def urlRegex = /http.*[^}]/
    def resutlUrl = (getResults =~ urlRegex)
    resutlUrl = resutlUrl[0]
    def fullResult = resutlUrl.toURL().text
    writeFile(file: 'Inspector_test_reults.html', text: fullResult)
    archiveArtifacts(artifacts: 'Inspector_test_reults.html', allowEmptyArchive: true)
    def testPassed = formatedResults(assessmentArn)

    cleanUp(stackName, body.region, bucketName, fileName)

    // Fail the pipeline if insepctor tests did not pass and flag either set to true or not set
    def failonfinding = body.get('failonfinding', true)

    if (testPassed[1] >= 1){
        if (!failonfinding) {
            println("One or more interpector test(s) failed on the AMI however \'failonfinding\' is set to \'False\' and hence the pipeline has not failed")
            return testPassed[0]
        } else {
            throw new GroovyRuntimeException("One or more interpector test(s) failed on the AMI")
        }
    } else {
        return testPassed
    }
}


def getAgentStatus(String arn) {
     def client = AmazonInspectorClientBuilder.standard().build()
     def request = new PreviewAgentsRequest().withPreviewAgentsArn(arn)
     def response = client.previewAgents(request)
     def state = response.getAgentPreviews()[0].getAgentHealth()
     return state
}


def getIstanceStatus(String id) {
    def client = AmazonEC2ClientBuilder.standard().build()
    def request = new DescribeInstancesRequest().withInstanceIds(id)
    def response = client.describeInstances(request)
    def state = response.getReservations()[0].getInstances()[0].getState().getName()
    return state
}

def cleanUp(String stackName, String region, String bucketName, String fileName){
    // Pull down cloudformaiton stack and bucket hosting cloudformation template
    println('Cleaning up all created resources')
    try {
        cloudformation(
            stackName: stackName,
            action: 'delete',
            region: region,
            waitUntilComplete: 'false',
            )
    }
    cleanBucket(bucketName, region, fileName)
    destroyBucket(bucketName, region)
    println('All creted resources are now deleted')
}

def uploadFile(String bucket, String fileName, String file, String region) {
      def client = AmazonS3ClientBuilder.standard().withRegion(region).build()
      client.putObject(bucket, fileName, file)
}


def createBucket(String name, String region) {
      def client = AmazonS3ClientBuilder.standard().build()
      def request = new CreateBucketRequest(name, region)
      client.createBucket(request)
}


def cleanBucket(String bucketName, String region, String fileName) {
      def client = AmazonS3ClientBuilder.standard().withRegion(region).build()
      client.deleteObject(bucketName, fileName)
}


def destroyBucket(String name, String region) {
      def client = AmazonS3ClientBuilder.standard().withRegion(region).build()
      client.deleteBucket(name)
}


def returnOs(String ami) {
    def client = AmazonEC2ClientBuilder.standard().build()
    def request = new DescribeImagesRequest().withImageIds(ami)
    def response = client.describeImages(request)
    response = response.getImages()[0].getPlatformDetails()

    if (response == 'Windows') {
        return('Windows')
    } else {
        return('Linux')
    }
}


def assessmentRun(String template_arn) {
    def client = AmazonInspectorClientBuilder.standard().build()
    def request = new StartAssessmentRunRequest().withAssessmentTemplateArn(template_arn)
    def response = client.startAssessmentRun(request)
    return response.getAssessmentRunArn()
}


def getResults(String result_arn) {
    def client = AmazonInspectorClientBuilder.standard().build()
    def request = new GetAssessmentReportRequest()
    .withAssessmentRunArn(result_arn)
    .withReportFileFormat('HTML') // HTML OR PDF
    .withReportType('FULL') //FINDING OR FULL
    def response = client.getAssessmentReport(request)
    return response
}


def formatedResults(arn) {
    def client = AmazonInspectorClientBuilder.standard().build()
    def request = new DescribeAssessmentRunsRequest().withAssessmentRunArns(arn)
    def response = client.describeAssessmentRuns(request)
    def findings = response.getAssessmentRuns()[0].getFindingCounts()
    def total_findings = findings['High'] + findings['Low'] + findings['Medium'] + findings['Informational']

    if (total_findings >= 1) {
        println("****************\nTest(s) not passed ${total_findings} issue found\nAMI failed insecptor test(s), see insepctor for details via saved file in workspace, AWS CLI or consolet\nFindings by Risk\nHigh: ${findings['High']}\nMedium: ${findings['Medium']}\nLow: ${findings['Low']}\nInformational: ${findings['Informational']}\n****************")
        return [findings, total_findings]
    }
    else {
        println('Test(s) passed')
        return [findings, total_findings]
    }
}


def getRunStatus (String arn) {
      def client = AmazonInspectorClientBuilder.standard().build()
      def request = new DescribeAssessmentRunsRequest()
        .withAssessmentRunArns(arn)
      def response = client.describeAssessmentRuns(request)
      def state = response.getAssessmentRuns()[0].getState()
      return state
}
