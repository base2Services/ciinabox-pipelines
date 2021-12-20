/***********************************
 tagAMI DSL

 Tags an AMI

 example usage
 tagAMI region: 'ap-southeast-2',  ami: 'xyz', tags: ['status':'verifed']
 ************************************/

import com.amazonaws.services.ec2.*
import com.amazonaws.services.ec2.model.*
import com.amazonaws.regions.*

def call(body) {
  def config = body
  node {
    println config
    if(config.ami) {
      addTags(config.region, config.ami, config.tags)
    } else {
      //Hack to read ALL env vars
      envVars = [:]
      sh 'env > env.txt'
      readFile('env.txt').split("\r?\n").each {
        e = it.split('=')
        envVars[e[0]] = e[1]
      }
      amis = findAMIsInEnvironment(envVars)
      amis.each { ami ->
        addTags(config.region, ami, config.tags)
      }
    }
  }
}

@NonCPS
def addTags(region, ami, tags) {
  println "adding tags to ami: ${ami}"
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

@NonCPS
def findAMIsInEnvironment(environment) {
  def amis = []
  environment.each { name, value ->
    if(name.endsWith('_BAKED_AMI')) {
      amis << value
    }
  }
  return amis
}