// @Grapes([
//       @Grab(group='com.amazonaws', module='aws-java-sdk-inspector', version='1.11.1020')
// ])
import com.amazonaws.services.inspector.AmazonInspector
import com.amazonaws.services.inspector.AmazonInspectorClientBuilder
import com.amazonaws.services.inspector.model.StartAssessmentRunRequest
import com.amazonaws.services.inspector.model.StartAssessmentRunResult
import com.amazonaws.services.inspector.model.GetAssessmentReportRequest
import com.amazonaws.services.inspector.model.GetAssessmentReportResult
import com.amazonaws.services.inspector.model.ListAssessmentRunsResult
import com.amazonaws.services.inspector.model.ListAssessmentRunsRequest


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

      def testStartTime = java.time.LocalDateTime.now()
      println(testStartTime)

      def assessmentRun = assessmentRun(template_arn)
      println(assessmentRun)

      def testCompleteTime = java.time.LocalDateTime.now()
      println(testCompleteTime)

      def assessmentArn = assessmentArn(assessmentRun, testStartTime, testCompleteTime)

      def getResults = getResults(assessmentArn[0])
      println(getResults)
}

def assessmentRun(String template_arn) {
      def client = AmazonInspectorClientBuilder.standard().build()
      def request = new StartAssessmentRunRequest().withAssessmentTemplateArn(template_arn)
      def response = client.startAssessmentRun(request)
      return request.getAssessmentTemplateArn()
}

def assessmentArn(String arn, testStartTime, testCompleteTime) {
      def client = AmazonInspectorClientBuilder.standard().build()
      def request = new ListAssessmentRunsRequest().withAssessmentTemplateArns(arn).withMaxResults(1)
      def response = client.listAssessmentRuns(request)
      println(response)
      return response
}

def getResults(String result_arn) {
      def client = AmazonInspectorClientBuilder.standard().build()
      def request = new GetAssessmentReportRequest().withAssessmentRunArn(result_arn)
      def response = client.getAssessmentReport(request)
      println(response)
      return response
}


// def client(String region) {
//     return AmazonInspectorClientBuilder.standard()
//         .withRegion(region)
//         .build()
//  }
//
// call([
//     region: 'ap-southeast-2',
//     stackName: 'inspector-test'
// ])
