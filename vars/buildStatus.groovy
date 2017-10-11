/***********************************
 buildStatus DSL

 sets the build/commit/pr status on either bitbucket/github

 required jenkins plugins
  https://wiki.jenkins.io/display/JENKINS/Pipeline+Githubnotify+Step+Plugin
  https://wiki.jenkins.io/display/JENKINS/Bitbucket+Cloud+Build+Status+Notifier+Plugin

 example usage
 buildStatus
  buildState: "INPROGRESS"|"SUCCESSFUL"|"FAILED"
  buildId:
  buildName:
  buildDescription:
 ************************************/

 def call(body) {
   def config = body

   println "env:${env}"
 }
