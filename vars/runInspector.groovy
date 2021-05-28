// @Grapes([
//       @Grab(group='com.amazonaws', module='aws-java-sdk-inspector', version='1.11.1020')
// ])
import com.amazonaws.services.inspector.AmazonInspector
import com.amazonaws.services.inspector.AmazonInspectorClientBuilder
import com.amazonaws.services.inspector.model.*
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model.*
import java.util.concurrent.TimeUnit


def call(body) {

      // Query stack for instance Id (must be an output) to install inspector agent if needed
      def instanceIds = cloudformation(
              stackName: body.stackName,
              queryType: 'output',
              query: 'InstanceId',
              region: body.region
      )
      println("instance id: ${instanceIds}")
      installInspectorAgent(instanceIds)


      // Query stack for inspector assessment template arn (must be an output)
      def template_arn = cloudformation(
              stackName: body.stackName,
              queryType: 'output',
              query: 'TemplateArn',
              region: body.region
      )

      def assessmentArn = assessmentRun(template_arn)

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

      getResults = getResults(assessmentArn)
      println(getResults)
      def urlRegex = /http.*[^}]/
      def resutlUrl = (getResults =~ urlRegex)
      resutlUrl = resutlUrl[0]
      def fullResult = resutlUrl.toURL().text
      formatedResults(fullResult)
}



def installInspectorAgent(String id) {
      println("instance id in function: *${id}*")
      def client = AWSSimpleSystemsManagementClientBuilder.standard().build()
      def request = new SendCommandRequest()
            .withInstanceIds(String id)
            .withDocumentName('AmazonInspector-ManageAWSAgent')
      println(request)
      def response = client.sendCommand(request)
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
            println("****************\nTest(s) not passed ${findings} issue found\nAMI failed insecptor test(s), see insepctor for details via the AWS CLI or console, AMI not pushed out\n****************")
            throw new GroovyRuntimeException("One or more interpector test(s)  failed on the AMI")
      }
      else {
            println('Test(s) passed')
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

// call([
//     region: 'ap-southeast-2',
//     stackName: 'inspector-test'
// ])
