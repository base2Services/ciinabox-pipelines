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

def parseJsonToMap(String json) {
  final slurper = new JsonSlurperClassic()
  return new HashMap<>(slurper.parseText(json))
}

def call(body) {
  def config = body
  def shortCommit = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
  def applicionIDjson = sh(returnStdout: true, script:
    "curl -X GET 'https://api.newrelic.com/v2/applications.json' \
      -H 'X-Api-Key:${config.apiKey}' \
      -G -d 'filter[name]=${config.application}'")
  def applicionIDmap = parseJsonToMap(applicionIDjson)
  def applicionID = applicionIDmap.applications[0].id
  sh "curl -X POST 'https://api.newrelic.com/v2/applications/${applicionID}/deployments.json' \
    -H 'X-Api-Key:${config.apiKey}' \
    -H 'Content-Type: application/json' \
    -d '{\"deployment\": {\"revision\": \"${shortCommit}\", \"user\": \"${config.user}\"}}'"
}
