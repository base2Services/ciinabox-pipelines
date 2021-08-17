/*
Test AMI aginst Inspector rules

Addtional Inspector Rule Package ARN's
https://docs.aws.amazon.com/inspector/latest/userguide/inspector_rules-arns.html#ap-southeast-2

example usage in a pipeline
runInspector(
     region: 'ap-southeast-2',                      # Required
     amiId: 'ami-0186908e2fdeea8f3'                 # Required
     failon: 'Informational|Low|Medium|High|Never', # Optional
     ruleArns: ['ruleARN1', 'ruleARN2']             # Optional
     testTime: '120',                               # Optional
)
*/

import com.amazonaws.services.inspector.AmazonInspectorClientBuilder
import com.amazonaws.services.inspector.model.PreviewAgentsRequest
import com.amazonaws.services.inspector.model.StartAssessmentRunRequest
import com.amazonaws.services.inspector.model.GetAssessmentReportRequest
import com.amazonaws.services.inspector.model.DescribeAssessmentRunsRequest
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.CreateBucketRequest
import com.base2.ciinabox.aws.Util
import com.base2.ciinabox.InstanceMetadata
import com.base2.ciinabox.GetInstanceDetails
import java.util.concurrent.TimeUnit

def call(body) {
    def stackName = 'InspectorAmiTest' + UUID.randomUUID().toString()
    def bucketName = 'inspectortestbucket' + UUID.randomUUID().toString()
    def fileName = 'Inspector.yaml'
    def findings = ''
    try{
        findings = main(body, stackName, bucketName, fileName)
    } catch(Exception e) {
        println("Error: ${e}")
        println("inspector failed to complete it's run, cleaning up resources before erroring out")
        throw e
    }
    // Fail the pipeline if insepctor tests did not pass considering passed in threshold
    def failon = body.get('failon', 'Medium').toString().toLowerCase().capitalize()
    def passed = checkFail(failon, findings[0])
    if (passed == false) {
        throw new GroovyRuntimeException("One or more interpector test(s) above or at ${failon} failed on the AMI")
    } else if((passed == true) & (findings[1] >= 1)) {
        println('Inspector failed on some test however they where under the treshold')
        return findings[1]
    } else {
        println('No inspector tests failed')
        return findings[1]
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
    def findings = formatedResults(assessmentArn)
    cleanUp(stackName, body.region, bucketName, fileName)
    return findings
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
            waitUntilComplete: 'false',
        )
    }
    catch (Exception e) {
        println("Unable to delete stack, error: ${e}")
    }
    try {
        cleanBucket(bucketName, region, fileName)
        destroyBucket(bucketName, region)
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
