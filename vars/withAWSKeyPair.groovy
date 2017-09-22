/***********************************
withAWSKeyPair Step DSL

generates a temp AWS KeyPair

example usage
withAWSKeyPair(region) {
  sh """
  echo "KEYNAME=$KEYNAME"
  """
}
************************************/
@Grab(group='com.amazonaws', module='aws-java-sdk-ec2', version='1.11.198')

import com.amazonaws.services.ec2.*
import com.amazonaws.services.ec2.model.*
import com.amazonaws.regions.*

def call(region, name=null, body) {
    keyName = name
    if(keyName == null) {
      keyName = UUID.randomUUID().toString()
    }
    createKeyPair(region,keyName)
    withEnv(["REGION=$region", "KEYNAME=${keyName}"]) {
      body()
    }
    deleteKeyPair(region,keyName)
}

def createKeyPair(region, name) {
  def ec2 = AmazonEC2ClientBuilder.standard()
    .withRegion(region)
    .build()

  def keyPair= ec2.createKeyPair(new CreateKeyPairRequest().withKeyName(name))
  File keyFile = new File(name)
  keyFile.write(keyPair.keyMaterial)
  keyFile.close()
}

def deleteKeyPair(region, name) {
  def ec2 = AmazonEC2ClientBuilder.standard()
    .withRegion(region)
    .build()
  ec2.deleteKeyPair(new DeleteKeyPairRequest().withKeyName(name))
}
