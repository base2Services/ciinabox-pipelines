/*
Test AMI aginst Inspector rules

example usage in a pipeline
runInspector(
     region: 'ap-southeast-2',                      # Required
     amiId: 'ami-0186908e2fdeea8f3'                 # Required
     failonfinding: 'False',                        # Optional
     ruleArns: 'ruleARN1,ruleARN2'                  # Optional
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
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.*
import com.amazonaws.services.s3.model.*
import java.util.concurrent.TimeUnit


def call(body) {

      // Lunch AMI into cloudformaiton stack with sarrounding infrustructure to support scans
      def template = libraryResource('Inspector.yaml')
      def fileName = 'Inspector.yaml'
      def stackName = 'InspectorAmiTest' + UUID.randomUUID().toString()
      def bucketName = 'inspectortestbucket' + UUID.randomUUID().toString()
      createBucket(bucketName, body.region)
      println('Created temp bucket to store cloudformaiton template')
      uploadFile(bucketName, fileName, template, body.region)
      println('Cloudformaiton uploaded to bucket')
      def os = returnOs(body.amiId)
      println("The AMI is using ${os} based operating system")

      // Organise which parameters to send
      def params = ['amiId': body.amiId, 'os': os]
      if (body.ruleArns) {
          params['ruleArns'] = body.ruleArns
      }
      if (body.testTime) {
          params['testTime'] = body.testTime
      }
      else if (body.ruleArns) {
          def listOfArns = body.ruleArns.split(", ")
          def time = ((20+(size(listOfArns)*10))*60)
          params['testTime'] = time
          println('time: ')
          println(time)
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

      // Query stack for inspector assessment template arn (must be an output)
      def template_arn = cloudformation(
              stackName: body.stackName,
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
      println(getResults)
      def urlRegex = /http.*[^}]/
      def resutlUrl = (getResults =~ urlRegex)
      resutlUrl = resutlUrl[0]
      def fullResult = resutlUrl.toURL().text
      writeFile(file: 'Inspector_test_reults.html', text: fullResult)
      archiveArtifacts(artifacts: 'Inspector_test_reults.html', allowEmptyArchive: true)
      def testPassed = formatedResults(fullResult)

      // Pull down cloudformaiton stack and bucket hosting cloudformation template
      cloudformation(
            stackName: stackName,
            action: 'delete',
            region: body.region,
            waitUntilComplete: 'false',
      )
      cleanBucket(bucketName, body.region, fileName)
      destroyBucket(bucketName, body.region)

      // Fail the pipeline if insepctor tests did not pass and flag either set to true or not set
      if (body.failonfinding) {
          println("One or more interpector test(s) failed on the AMI however \'failonfinding\' is set to \'False\' and hence the pipeline has not failed")
      }
      else {
          throw new GroovyRuntimeException("One or more interpector test(s) failed on the AMI")
      }
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
      response = client.deleteBucket(name)
}


def returnOs(String ami) {
      def client = AmazonEC2ClientBuilder.standard().build()
      def request = new DescribeImagesRequest().withImageIds(ami)
      def response = client.describeImages(request)

      regex = /Windows/
      response = (response =~ regex)
      if (response.size() != 0){
            return('Windows')
      }
      else {
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


def formatedResults(fullResult) {
      // Check if there where Findings
      def regex = /A total of \d/
      def findings = (fullResult =~ regex)
      findings = findings[0]
      findings = findings.replaceAll(/A total of /, '').toInteger() // Just get the total number of findings

      if (findings >= 1) {
            println("****************\nTest(s) not passed ${findings} issue found\nAMI failed insecptor test(s), see insepctor for details via saved file in workspace, AWS CLI or consolet\n****************")
            return 1
      }
      else {
            println('Test(s) passed')
            return 0
      }
}


def getRunStatus (String arn) {
      def client = AmazonInspectorClientBuilder.standard().build()
      def request = new DescribeAssessmentRunsRequest()
        .withAssessmentRunArns(arn)
      def response = client.describeAssessmentRuns(request)
      // Pull out just the state of the test
      def regex = /State: [A-Z_]*,/
      def state = (response =~ regex)
      state = state[0]
      // Cleanup the state string so its just the current state (not 'State: (state),')
      def length = state.length()
      state = state.substring(0, (length - 1))
      state = state.replace('State: ', '')
      return state
}
