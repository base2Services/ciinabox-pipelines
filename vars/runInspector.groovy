@Grapes([
      @Grab(group='com.amazonaws', module='aws-java-sdk-inspector', version='1.11.1020')
])
import com.amazonaws.services.inspector.AmazonInspector
import com.amazonaws.services.inspector.AmazonInspectorClientBuilder
import com.amazonaws.services.inspector.model.StartAssessmentRunRequest
import com.amazonaws.services.inspector.model.StartAssessmentRunResult


def call(body) {
      def config = body
      // Query stack for inspector assessment template arn
      def arn = cloudformation(
        stackName: 'inspector-test',//config.stackName,
        queryType: 'output',
        query: 'TemplateArn',
        region: 'ap-southeast-2' //config.region,
      );
      print (arn)
      def assessmentRun = assessmentRun(arn)
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

// call([
//     region: 'ap-southeast-2',
//     stackName: 'inspector-test'
// ])
