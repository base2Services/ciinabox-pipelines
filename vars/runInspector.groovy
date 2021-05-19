@Grapes([
      @Grab(group='com.amazonaws', module='aws-java-sdk-inspector', version='1.11.1020')
])
import com.amazonaws.services.inspector.AmazonInspector
import com.amazonaws.services.inspector.AmazonInspectorClientBuilder
import com.amazonaws.services.inspector.model.StartAssessmentRunRequest
import com.amazonaws.services.inspector.model.StartAssessmentRunResult


def call(body) {
      // Query stack for inspector assessment template arn
      def arn = cloudformation(
        stackName: body.stackName,
        queryType: 'output',
        query: 'TemplateArn',
        region: body.region,
      );
      print (arn)
      def assessmentRun = assessmentRun(body.arn)
      println(assessmentRun)



    // def client = client(body.region)
    // def request = new StartAssessmentRunRequest()
    //     .withAssessmentTemplateArn(body.arn)
}

def assessmentRun(String arn) {
      AmazonInspector client = AmazonInspectorClientBuilder.standard().build()
      StartAssessmentRunRequest request = new StartAssessmentRunRequest().withAssessmentTemplateArn(arn)
      StartAssessmentRunResult response = client.startAssessmentRun(request)
      return StartAssessmentRunResult
}


// def client(String region) {
//     return AmazonInspectorClientBuilder.standard()
//         .withRegion(region)
//         .build()
//  }

call([
    region: 'ap-southeast-2',
    stackName: 'inspector-test'
])
