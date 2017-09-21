/***********************************
 verifyAMI DSL

 Verifies a baked AMI using Test Kitchen and InSpec

 example usage
 verifyAMI ......
 ************************************/
 @Grab('org.yaml:snakeyaml:1.18')

 def call(body) {
   def config = body
   deleteDir()
   unstash 'baked-ami'
   unstash 'cookbook'
   withEnv(["ROLE=${config.get('role')}", "COOKBOOK=${config.get('cookbook')}"]) {
     sh '''
       tar xfz cookbooks.tar.gz
       ls -al
       SOURCE_AMI=$(grep 'ami:' ${ROLE}-ami-*.yml | awk -F ':' {'print $2'})
       echo $SOURCE_AMI
       cd cookbooks/$COOKBOOK
       cat .kitchen.yml
     '''
   }
}

def createKitchenLocalOverride() {
}
