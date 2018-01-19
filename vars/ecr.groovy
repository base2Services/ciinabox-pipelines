/***********************************
 ecr DSL
 Creates ECR repository
 example usage
 ecr
   accountId: '1234567890',
   region: 'ap-southeast-2',
   image: 'myrepo/image',
   otherAccountIds: ['0987654321','5432167890']
 )
 ************************************/

import com.amazonaws.services.ecr.*
import com.amazonaws.services.ecr.model.*
import com.amazonaws.regions.*

def call(body) {
  def config = body
  def ecr = setupClient(config.region)
  createRepo(ecr,config.image)
  if (config.otherAccountIds) {
    setRepositoryPolicy(ecr,config)
  }
}

def createRepo(ecr,repo) {
  try{
    ecr.createRepository(new CreateRepositoryRequest()
      .withRepositoryName(repo)
    )
    println "Created repo ${repo}"
  } catch (RepositoryAlreadyExistsException e) {
    println "${e.getErrorMessage()} for ECR repository ${repo}"
  }
}

def setRepositoryPolicy(ecr,config) {
  def document = new groovy.json.JsonBuilder()
  document {
    Version("2008-10-17")
    Statement( config.otherAccountIds.collect { accountId ->
      [
        Sid: "AllowPull",
        Effect: "Allow",
        Principal: ({
          'AWS'(["arn:aws:iam::${accountId}:root"])
        }),
        Action:([
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:BatchCheckLayerAvailability"
        ])
      ]
    })
  }
  println "Applying ECR access policy\n${document.toPrettyString()}"
  ecr.setRepositoryPolicy(new SetRepositoryPolicyRequest()
    .withRepositoryName(config.image)
    .withRegistryId(config.accountId)
    .withPolicyText(document.toString())
  )
}

def setupClient(region) {
  return AmazonECRClientBuilder.standard()
    .withRegion(region)
    .build()
}
