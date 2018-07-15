/***********************************

  newrelic DSL

  notifies newrelic of a deployment

  example usages:

  newrelic(
    apiKey: 'xxxxxxxxxxxxxxxxxxxx',
    application: 'myApp',
    user: 'jenkinsUser'
  )

  withCredentials([string(credentialsId: 'newrelic-api-key', variable: 'APIKEY')]) {
    wrap([$class: 'BuildUser']) {
      newrelic(
        apiKey: "${APIKEY}",
        application: 'myApp',
        user: "${BUILD_USER}"
      )
    }
  }

************************************/

import groovy.json.JsonSlurperClassic

@NonCPS
def parseJsonToMap(String json) {
  final slurper = new JsonSlurperClassic()
  return new HashMap<>(slurper.parseText(json))
}

def call(body) {
  def config = body
  def shortCommit = shellOut("git log -n 1 --pretty=format:'%h'")
  def applicationIDjson = shellOut("curl -X GET 'https://api.newrelic.com/v2/applications.json' \
      -H 'X-Api-Key:${config.apiKey}' \
      -G -d 'filter[name]=${config.application}'")
  def applicationIDmap = parseJsonToMap(applicationIDjson)
  if (applicationIDmap.applications.size() == 1) {
    def applicationID = applicationIDmap.applications[0].id
    sh "curl -X POST 'https://api.newrelic.com/v2/applications/${applicationID}/deployments.json' \
      -H 'X-Api-Key:${config.apiKey}' \
      -H 'Content-Type: application/json' \
      -d '{\"deployment\": {\"revision\": \"${shortCommit}\", \"user\": \"${config.user}\"}}'"
  }
}
