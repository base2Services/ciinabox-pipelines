/***********************************
copyAndShareAMI DSL

# example usage
copyAndShareAMI(
  role: 'ECS',
  region: 'ap-southeast-2',
  amiId: 'ami-1234abcd',
  copyRegions: ['us-west-2'],
  shareAccounts: ['111111111111','222222222222'],
  amiName: 'my-base-ami-*',
  tags: ['status':'verifed'],
  owner: '999999999999'
)
************************************/

@Grab(group='com.amazonaws', module='aws-java-sdk-ec2', version='1.11.359')

import com.amazonaws.services.ec2.*
import com.amazonaws.services.ec2.model.*
import com.amazonaws.regions.*
import com.amazonaws.waiters.*
import java.util.concurrent.Future

def call(body) {
  def config = body
  def sourceAMI = null

  if(!(config.amiId) && !(config.amiName)){
    throw new GroovyRuntimeException("Either `amiName` or `amiId` must be specified for copyAndShareAMI method")
  }

  if(config.amiName) {
    println "Looking up AMI: ${config.amiName}"
    sourceAMI = lookupAMI config
    if(!(sourceAMI)){
      throw new GroovyRuntimeException("Unable to find AmiId for ${config.amiName} with filter options\ntags: ${config.tags}\nowner: ${config.owner}")
    }
  }

  if(config.amiId) {
    println "Using AMI Id: ${config.amiId}"
    sourceAMI = config.amiId
  }

  config.copyRegions.each { copyRegion ->
    println "Copying ${sourceAMI} to ${copyRegion}"
    def copiedAMI = copyAMI(
      region: config.region,
      ami: sourceAMI,
      copyRegion: copyRegion
    )

    AmazonEC2 client = setupClient(copyRegion)
    def copied = wait(client, copyRegion, copiedAMI)

    if (copied) {
      println "Sharing ${copiedAMI} in ${copyRegion} to account(s) ${config.shareAccounts}"
      shareAMI(
        region: copyRegion,
        ami: copiedAMI,
        accounts: config.shareAccounts
      )

      def regionENV = copyRegion.replaceAll('-','_').toUpperCase()
      def roleENV = config.role.toUpperCase()
      println "Setting environment variable `${roleENV}_${regionENV}` with value ${copiedAMI}"
      env["${roleENV}_${regionENV}"]=copiedAMI
    } else {
      throw new GroovyRuntimeException("Failed to copy ${copiedAMI} to ${copyRegion}!")
    }

  }

  println "Copy and share complete for AMI ${sourceAMI} for ${config.copyRegions} region(s) to accounts ${config.shareAccounts}"

}

def wait(client, region, ami)   {
  Waiter waiter = client.waiters().imageAvailable()

  try {
    Future future = waiter.runAsync(
      new WaiterParameters<>(new DescribeImagesRequest().withImageIds(ami)),
      new NoOpWaiterHandler()
    )
    while(!future.isDone()) {
      try {
        echo "waitng for ami ${ami} in ${region} to finish copying"
        Thread.sleep(10000)
      } catch(InterruptedException ex) {
          echo "We seem to be timing out ${ex}...ignoring"
      }
    }
    println "AMI: ${ami} in ${region} copy complete"
    return true
   } catch(Exception e) {
     println "AMI: ${ami} in ${region} copy failed"
     return false
   }
}

def setupClient(region) {
  return AmazonEC2ClientBuilder.standard()
    .withRegion(region)
    .build()
}
