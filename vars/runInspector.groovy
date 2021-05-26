// @Grapes([
//       @Grab(group='com.amazonaws', module='aws-java-sdk-inspector', version='1.11.1020')
// ])
import com.amazonaws.services.inspector.AmazonInspector
import com.amazonaws.services.inspector.AmazonInspectorClientBuilder
import com.amazonaws.services.inspector.model.*
import java.util.concurrent.TimeUnit


def call(body) {
      def config = body
      // Query stack for inspector assessment template arn
      def template_arn = cloudformation(
        stackName: 'inspector-test',//config.stackName,
        queryType: 'output',
        query: 'TemplateArn',
        region: 'ap-southeast-2' //config.region,
      );
      println(template_arn)
      // Query stack for inspector assessment template test duration
      int testDuration = cloudformation(
        stackName: 'inspector-test',//config.stackName,
        queryType: 'output',
        query: 'TestDuration',
        region: 'ap-southeast-2' //config.region,
      ).toInteger();
      testDuration += 150 //pad the test length by 2 mins to account for startup/finshup time

      Date testStartTime = new Date()
      println(testStartTime)

      def assessmentRun = assessmentRun(template_arn)
      println(assessmentRun)

      // Display the reamining time in a realtively informative way
      while (testDuration > 0) {
            if (testDuration <= 60) {
                  println("The test has ${testDuration} seconds left to run")
                  TimeUnit.SECONDS.sleep(20);
                  testDuration -= 20
            }
            else if (testDuration <= 300) {
                  println("The test has ${(testDuration/60)} minutes left to run")
                  TimeUnit.SECONDS.sleep(60);
                  testDuration -= 60
            }
            else if (testDuration > 300) {
                  println ("The test has ${(testDuration/60)} minutes left to run")
                  TimeUnit.SECONDS.sleep(300);
                  testDuration -= 300
            }
      }

      Date testCompleteTime = new Date()
      println(testCompleteTime)

      def assessmentArn = assessmentArn(assessmentRun, testStartTime, testCompleteTime)

      // // This was a test to check the status of the test and then only get the result when the test is complete, this does not work as of current as querying a running test (this way) produces an error.
      def testRunning = true
      while (testRunning.equals(true)) {
            def getResults = getResults(assessmentArn).toString()
            println("Pre Regex: ${getResults}")
            if ((getResults.contains("WORK_IN_PROGRESS")).equals(false)) {
                  testRunning = false
            }
      }

      def getResults = getResults(assessmentArn)
      println(getResults)
      def urlRegex = /http.*/
      def resutlUrl = (getResults =~ urlRegex)
      def length = urlRegex.length()
      resutlUrl = urlRegex.substring(0, (length - 1))
      println(resutlUrl)
      println(urlRegex.toURL().test)
      pass(getResults)
}

def assessmentRun(String template_arn) {
      def client = AmazonInspectorClientBuilder.standard().build()
      def request = new StartAssessmentRunRequest().withAssessmentTemplateArn(template_arn)
      def response = client.startAssessmentRun(request)
      return request.getAssessmentTemplateArn()
}

def assessmentArn(String arn, Date testStartTime, Date testCompleteTime) {
      def client = AmazonInspectorClientBuilder.standard().build()
      def timeRange = new TimestampRange().withBeginDate(testStartTime).withEndDate(testCompleteTime)
      def filter = new AssessmentRunFilter().withStartTimeRange(timeRange)
      def request = new ListAssessmentRunsRequest().withAssessmentTemplateArns(arn).withFilter(filter)
      def response = client.listAssessmentRuns(request)
      println(response)

      // Get the first returned arn by itself
      def regex = /arn.*]/
      response = (response =~ regex)
      response = response[0].toString()
      def length = response.length()
      response = response.substring(0, (length - 1))
      println(response)

      return response
}

def getResults(String result_arn) {
      def client = AmazonInspectorClientBuilder.standard().build()
      def request = new GetAssessmentReportRequest()
                        .withAssessmentRunArn(result_arn)
                        .withReportFileFormat('HTML') // HTML OR PDF
                        .withReportType('FULL') //FINDING OR FULL
      def response = client.getAssessmentReport(request)
      println(response)
      return response
}

def pass(String fullResult) {
      def grepString = /regex to look for/
      def result = (fullResult =~ grepString)
      if (result.equals('Failed')) {
            throw new GroovyRuntimeException("AMI failed insecptor test, see above for test result, AMI not pushed out")
      }
}

// call([
//     region: 'ap-southeast-2',
//     stackName: 'inspector-test'
// ])
