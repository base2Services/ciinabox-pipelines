/***********************************
 verifyAMI DSL

 Verifies a baked AMI using Test Kitchen and InSpec

 example usage
 verifyAMI ......
 ************************************/
 def call(body) {
   def config = body
   deleteDir()
   unstash 'baked-ami'
   unstash 'cookbooks'
   withEnv(["ROLE=${config.get('role')}"]) {
     sh '''
       ls -al
       SOURCE_AMI=$(grep 'ami:' ${ROLE}-ami-*.yml | awk -F ':' {'print $2'})
       echo $SOURCE_AMI
       cat .kitchen.yml
     '''
   }
}
