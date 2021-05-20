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
      def assessmentRun = assessmentRun(template_arn)
      println(assessmentRun)

      def assessmentArn = assessmentArn(assessmentRun)

      def assessmentResults = assessmentResults(assessmentArn)
      println(assessmentResults)
}

def assessmentRun(String template_arn) {
      def client = AmazonInspectorClientBuilder.standard().build()
      def request = new StartAssessmentRunRequest().withAssessmentTemplateArn(template_arn)
      def response = client.startAssessmentRun(request)
      return request.getAssessmentTemplateArn()
}

def assessmentArn(String arn) {
      println('start')
      def client = AmazonInspectorClientBuilder.standard().build()
      println('client coplete')
      def request = new ListAssessmentRunsResult().setAssessmentRunArns(arn)
      println('request completed')
      // def response = client.listAssessmentRunsResult(request)
      // println('response completed')
      println(request)
      return request.withAssessmentRunArn(arn)
}

def assessmentResults(String result_arn) {
      def client = AmazonInspectorClientBuilder.standard().build()
      def request = new GetAssessmentReportRequest().withAssessmentRunArn([result_arn])
      def response = client.getAssessmentReport(request)
      println(GetAssessmentReportResult)
      return GetAssessmentReportResult
}


// def client(String region) {
//     return AmazonInspectorClientBuilder.standard()
//         .withRegion(region)
//         .build()
//  }

// call([
//     region: 'ap-southeast-2',
//     stackName: 'inspector-test'
// ])
