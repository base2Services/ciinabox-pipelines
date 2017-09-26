/***********************************
 tagAMI DSL

 Tags an AMI

 example usage
 tagAMI region: 'ap-southeast-2',  ami: 'xyz', tags: ['status':'verifed']
 ************************************/
 @Grab(group='com.amazonaws', module='aws-java-sdk-ec2', version='1.11.198')

 import com.amazonaws.services.ec2.*
 import com.amazonaws.services.ec2.model.*
 import com.amazonaws.regions.*

 def call(body) {
   def config = body
   node {
     println config
     addTags(config.region, config.ami, config.tags)
   }
}

@NonCPS
def addTags(region, ami, tags) {
  def ec2 = AmazonEC2ClientBuilder.standard()
    .withRegion(region)
    .build()

  def newTags = []
  tags.each { key, value ->
    newTags << new Tag(key, value)
  }

  ec2.createTags(new CreateTagsRequest()
    .withResources(ami)
    .withTags(newTags)
  )
}
