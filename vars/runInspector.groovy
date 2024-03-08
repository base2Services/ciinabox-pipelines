/*
Test AMI aginst Inspector rules

Addtional Inspector Rule Package ARN's
https://docs.aws.amazon.com/inspector/latest/userguide/inspector_rules-arns.html#ap-southeast-2

example usage in a pipeline
runInspector(
    amiId: 'ami-0186908e2fdeea8f3',                 // Required, ami to run inspector against
    region: 'ap-southeast-2',                       // Required, aws region
    bucket: 'my-s3-bucket',                         // Optional, s3 bucket to upload results to. if not set a bucket will be created
    instanceType: 't3.micro',                       // Optional, set the EC2 instancetype launched by the step to  execute the inspector assessment on. defaults to t3.micro
    failon: 'Informational|Low|Medium|High|Never',  // Optional, fail the pipeline if a finding is matched or higher. defaults to Medium
    ruleArns: ['ruleARN1', 'ruleARN2'],             // Optional, aws inspector rule sets to run against the ami
    testTime: '3600', (in seconds)                  // Optional, how long to run the tests for
    whitelist: ['CVE-2018-12126', 'CVE-2018-12127'] // Optional, list of CVEs to be ignored
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
import com.base2.ciinabox.aws.Util
import com.base2.ciinabox.InstanceMetadata
import com.base2.ciinabox.GetInstanceDetails
import java.util.concurrent.TimeUnit

def call(body) {
    def runId = UUID.randomUUID().toString()
    def cleanupBucket = false

    println("inspector run : ${runId}")

    // create s3 bucket if not set
    if (!body.bucket) {
        body.bucket = "inspectortestbucket-${runId}"
        println("no s3 bucket supplied, creating s3 bucket ${body.bucket}")
        createBucket(body.bucket, body.region)
        cleanupBucket = true
    }

    def stackName = "ciinabox-pipelines-inspector-${runId}"
    def fileName = "inspector/Inspector.yaml"
    def findings = ''

    try {
        findings = main(body, stackName, body.bucket, fileName, runId)
    } catch(Exception e) {
        println("Error: ${e}")
        println("inspector failed to complete it's run, cleaning up resources before erroring out")
        cleanUp(stackName, body.region, body.bucket, fileName, cleanupBucket)
        throw e
    }

    // cleanup resources
    cleanUp(stackName, body.region, body.bucket, fileName, cleanupBucket)

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


def main(body, stackName, bucketName, fileName, runId) {
    // Launch AMI into cloudformaiton stack with sarrounding infrustructure to support scans
    def template = libraryResource('Inspector.yaml')
    uploadFile(bucketName, fileName, template, body.region)
    println('Cloudformaiton uploaded to bucket')
    def os = returnOs(body.amiId)
    println("The AMI is using ${os} based operating system")

    // Organise which parameters to send
    def params = [
        'AmiId': body.amiId,
        'OperatingSystem': os,
        'RunId': runId
    ]
    if (body.subnetId) {
        params['SubnetId'] = body.subnetId
    } else {
        params['SubnetId'] = getsubnetId(body.region)
    }
    if (body.ruleArns) {
        params['RuleArns'] = body.ruleArns.join(',')
    }
    if (body.instanceType) {
        params['InstanceType'] = body.instanceType
    }
    if (body.testTime) {
        params['TestTime'] = body.testTime
    } else if (body.ruleArns) {
        def time = ((20+(body.ruleArns.size()*10))*60)
        params['TestTime'] = time.toString()
    }

    println('deploying inspector resources via cloudformation')

    cloudformation(
        stackName: stackName,
        action: 'create',
        region: body.region,
        templateUrl: "https://${bucketName}.s3.amazonaws.com/${fileName}",
        waitUntilComplete: 'true',
        parameters: params
    )

    println('retrieving inspector resources details')

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
    while ((runStatus != "COMPLETED") & (runStatus != "FAILED")) {
        runStatus = getRunStatus(assessmentArn)
        println("Test Run Status: ${runStatus}")
        TimeUnit.SECONDS.sleep(60);
    }

    // This waits for inspector to finish up everything before an actaul result can be returned, this is not waiting for the test to finish
    def testRunning = true
    while (testRunning.equals(true)) {
        def getResults = getResults(assessmentArn).toString()
        println("Cleanup Status: ${getResults}")
        if ((getResults.contains("WORK_IN_PROGRESS")).equals(false)) {
            testRunning = false
        }
        TimeUnit.SECONDS.sleep(20);
    }

    // Get the results of the test, write to jenkins and fromated the result to check if the test(s) passed
    htmlAssessmentResult = getResults(assessmentArn, 'HTML')
    def htmlAssessmentUrl = htmlAssessmentResult.getUrl()
    def htmlAssessment = htmlAssessmentUrl.toURL().text
    writeFile(file: 'Inspector_test_reults.html', text: htmlAssessment)
    archiveArtifacts(artifacts: 'Inspector_test_reults.html', allowEmptyArchive: true)

    // format the results to send to slack and display to the console
    def findings = formatedResults(assessmentArn, body.get('whitelist', []))

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


def cleanUp(String stackName, String region, String bucketName, String fileName, Boolean cleanupBucket){
    // Pull down cloudformaiton stack and bucket hosting cloudformation template
    println('Tearing down cloudfromation stack')
    try {
        cloudformation(
            stackName: stackName,
            action: 'delete',
            region: region
        )
    } catch (Exception e) {
        println("Unable to delete stack, error: ${e}")
    }

    if (cleanupBucket) {
        println('cleaning up created bucket')
        try {
            cleanBucket(bucketName, region, fileName)
            destroyBucket(bucketName, region)
        } catch (Exception e) {
            println("Unable to clean/destroy bucket, error: ${e}")
        }
    }

    println('resource cleanup completed')
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


def getResults(String result_arn, String fileFormat = 'HTML') {
    def client = AmazonInspectorClientBuilder.standard().build()
    def request = new GetAssessmentReportRequest()
                        .withAssessmentRunArn(result_arn)
                        .withReportFileFormat(fileFormat) // HTML OR PDF
                        .withReportType('FULL') //FINDING OR FULL
    def response = client.getAssessmentReport(request)
    return response
}


def formatedResults(arn, whitelist) {
    def finding_arns = []

    def client = AmazonInspectorClientBuilder.standard().build()
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


def getRunStatus (String arn) {
    def client = AmazonInspectorClientBuilder.standard().build()
    def request = new DescribeAssessmentRunsRequest()
                        .withAssessmentRunArns(arn)
    def response = client.describeAssessmentRuns(request)
    def state = response.getAssessmentRuns()[0].getState()
    return state
}
