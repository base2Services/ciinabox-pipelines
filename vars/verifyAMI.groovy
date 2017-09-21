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
   sh '''
     ls -al
     SOURCE_AMI=$(grep 'ami:' *-ami-*.yml | awk -F ':' {'print $2'})
     echo $SOURCE_AMI
     cat .kitchen.yml
   '''
}
