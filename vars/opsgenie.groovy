/***********************************

  opsgenie DSL

  notifies opsgenie of a deployment

  example usages:

  opsgenie(
    apiKey: ${OPSGENIE_APIKEY},
    close: true|false, // defaults to false
    priority: 'critical|warning|task|info', // defaults to info
    application: 'myApp',
    environment: 'dev',
    type: 'deployment|build', // defaults to deployment
    details: [ // provide any custom key:value keys to pass to opsgenie
      deploymentType: 'cloudformation',
      stackName: 'dev'
    ]
  )

************************************/

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def call(body) {
  def config = body

  def priorities = [
    critical: 'P1',
    warning: 'P4',
    task: 'P3',
    info: 'P5'
  ]

  def priority = priorities.get(config.get('priority','info'))

  if (priority == null) {
    priority = 'P5'
  }

  def type = config.get('type','deployment')
  def close = config.get('close',false)
  def jobName = env.JOB_NAME.replace("/", " ")

  def alias = "${type} nofication for ${config.environment} environment by ${jobName} build ${env.BUILD_NUMBER}"
  def message = "${type} for ${config.application} in ${config.environment} environment by job ${env.JOB_NAME} build ${env.BUILD_NUMBER}"

  if (type != 'deployment') {
    alias = "${type} nofication by ${jobName} build ${env.BUILD_NUMBER}"
    message = "${type} nofication for ${config.application} by job ${env.JOB_NAME} build ${env.BUILD_NUMBER}"
  }

  raiseAlert(config,alias,type,message,priority)

  if (close) {
    closeAlert(config,alias)
  }

}

@NonCPS
def raiseAlert(config,alias,type,message,priority) {

  def payload = [
    message: message,
    alias: alias,
    description: message,
    details: [
      type: type,
      application: config.application,
      environment: config.environment,
      jobName: env.JOB_NAME,
      buildNumber: env.BUILD_NUMBER,
      jobUrl: env.JOB_URL
    ],
    priority: priority,
    user: 'ciinabox',
    source: 'jenkins'
  ]

  config.details.each { key,value ->
    payload['details'][key] = value
  }

  def post = new URL("https://api.opsgenie.com/v2/alerts").openConnection();
  post.setRequestMethod("POST")
  post.setDoOutput(true)
  post.setRequestProperty("Content-Type", "application/json")
  post.setRequestProperty("Accept", "application/json")
  post.setRequestProperty("Authorization", "GenieKey ${config.apiKey}")
  post.getOutputStream().write(JsonOutput.toJson(payload).getBytes("UTF-8"));
  def postRC = post.getResponseCode();

  if(postRC.equals(202)) {
    def jsonSlurper = new JsonSlurper()
    def resp = jsonSlurper.parseText(post.getInputStream().getText())
    println "OpsGenie: raised a ${config.priority} alert with alias ${alias}"
  }

}

@NonCPS
def closeAlert(config,alias) {

  def payload = [
    user: 'ciinabox',
    source: 'jenkins',
    note: 'closed by Jenkins pipeline'
  ]

  def post = new URL("https://api.opsgenie.com/v2/alerts/${encodeAlias(alias)}/close?identifierType=alias").openConnection();
  post.setRequestMethod("POST")
  post.setDoOutput(true)
  post.setRequestProperty("Content-Type", "application/json")
  post.setRequestProperty("Accept", "application/json")
  post.setRequestProperty("Authorization", "GenieKey ${config.apiKey}")
  post.getOutputStream().write(JsonOutput.toJson(payload).getBytes("UTF-8"));
  def postRC = post.getResponseCode();

  if(postRC.equals(202)) {
    def jsonSlurper = new JsonSlurper()
    def resp = jsonSlurper.parseText(post.getInputStream().getText())
    println "OpsGenie: closed alert with details with alias ${alias}"
  }

}

@NonCPS
def encodeAlias(alias) {
  return java.net.URLEncoder.encode(alias, "UTF-8").replace("+", "%20")
}

@NonCPS
def mapToJson(HashMap map) {
  final builder = new groovy.json.JsonBuilder(map)
  return builder.toPrettyString()
}
