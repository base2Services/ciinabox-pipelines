package com.base2.ciinabox

/***
 * Runs other jobs in batches.
 * @param jobConfigurations @see java.util.Map. Keys will be used as stage names. Values are maps with following keys
 *              - Jobs : List of jobs. Job can be either a closure (executed at runtime), or @see java.util.Map describing
 *                        job outside of pipeline to be started. If job is started outside of pipeline, following map keys
 *                        are being used to describe this job:
 *
 *                          - job: full path to job (including folder). e.g. MyBuildJobs/MyApp-Maven-Release
 *                          - wait: @see java.lang.Boolean determines whether pipeline waits for job execution to complete,
 *                                  or starts it asynchronously. If true, status of pipeline will be dependant on downstream job
 *                                   started. Otherwise, downstream job build status won't be collected
 *                          - parameters. Can be one of
 *                                - java.util.List of Strings. Each member represents job parameter in "key=value" form
 *                                - groovy.lang.Closure Closure returning above mentioned @see java.util.List of strings
 *                                  in "key=value" format
 *
 *              - Parallel: Determines whether jobs will be executed in parallel or serialized. If input of one job is used
 *                          as output of other, jobs should be running in parallel
 */
def batchRunJobs(jobConfigurations) {

  def pipelineUtils = new com.base2.ciinabox.PipelineUtils()
  println "Starting batch execution of jobs. ${jobConfigurations.size()} batches passed in configuration... "

  def configurationKeys = pipelineUtils.mapKeys(jobConfigurations)
  for (int i = 0; i < configurationKeys.length; i++) {
    def stageName = configurationKeys[i],
        jobConfiguration = jobConfigurations[stageName],
        jobClosures = collectJobClosures(jobConfiguration.jobs, jobConfiguration.parallel)


    echo "Processing $stageName ${jobConfiguration.parallel ? "in parallel " : " in serialized manner"}"

    if (jobConfiguration.parallel) {
      if (jobConfiguration.batchSize) {
        def jobBatches = pipelineUtils.partitionSteps(jobClosures, jobConfiguration.batchSize)

        echo "Number of batches to execute :${jobBatches.size()}"
        for (int j = 0; j < jobBatches.size(); j++) {

          stage("$stageName - Batch #$j") {
            parallel jobBatches[j]
          }
        }
      }
      else {
        stage(stageName){
          parallel jobClosures
        }
      }
    }
    else {
      stage(stageName) {
        pipelineUtils.runJobClosures(jobClosures)
      }
    }

  }
}

/***
 *
 * @param jobDefinitions @see java.util.List of job definitions. Job can be either a closure (executed at runtime), or @see java.util.Map describing
 *                        job outside of pipeline to be started. If job is started outside of pipeline, following map keys
 *                        are being used to describe this job:
 *
 *                          - job: full path to job (including folder). e.g. MyBuildJobs/MyApp-Maven-Release
 *                          - wait: @see java.lang.Boolean determines whether pipeline waits for job execution to complete,
 *                                  or starts it asynchronously. If true, status of pipeline will be dependant on downstream job
 *                                   started. Otherwise, downstream job build status won't be collected
 *                          - parameters. Can be one of
 *                                - java.util.List of Strings. Each member represents job parameter in "key=value" form
 *                                - groovy.lang.Closure Closure returning above mentioned @see java.util.List of strings
 *                                  in "key=value" format
 * @param runInParallel
 * @return if parallel execution required @see java.util.Map of parallel steps to execute using
 *         parallel pipeline statement
 *         if parallel execution not required @see java.util.List of executable closures, each
 *         representing single job definition
 */
def collectJobClosures(jobDefinitions, runInParallel) {
  def closures = runInParallel ? [:] : []

  echo "Transforming ${jobDefinitions.size()} job definitions to executable closures"

  for (int j = 0; j < jobDefinitions.size(); j++) {
    def jobConfig = jobDefinitions[j]

    def jobClosure,
        parallelBranchName

    if(!jobConfig.name){
      jobConfig.name = "Parallel Branch $j"
    }

    //dynamically binding jobs
    if (jobConfig.job instanceof Closure) {
      jobClosure = jobConfig.job
    }
    else {
      jobClosure = buildJobStep(jobConfig.job, jobConfig.wait, jobConfig.parameters)
    }

    parallelBranchName = jobConfig.name

    if (runInParallel) closures[parallelBranchName] = jobClosure
    if (!runInParallel) closures << jobClosure

  }
  return closures
}

/***
 *
 * @param jobLocation full path to job e.g. MyFolder/MyAppBuild
 * @param wait @see java.lang.Boolean flag indicating whether pipeline should wait for job execution to complete.
 *                  Also affecting whether job build status will be propagated to pipeline status
 * @param params Either @see groovy.lang.Closure returning @see java.util.List, or @see java.util.List itself, having String elements in
 *                  format "key=value"
 * @return executable @see groovy.lang.Closure that will start defined job using pipeline build step
 */
def buildJobStep(jobLocation, wait, params) {
  return {
    def jobParameters = []
    //late binding
    if (params instanceof Closure) {
      echo "Late binding parameters for job ${jobLocation}"
      params = params.call()
    }

    //con
    if (params instanceof List) {
      params = params.toArray()
    }

    //build out parameters array
    for (int i = 0; i < params.length; i++) {
      def splitParams = params[i].split('='),
          paramKey = splitParams[0],
          paramValue = splitParams[1]
      jobParameters << [$class: 'StringParameterValue', name: paramKey.toUpperCase(), value: paramValue]
    }

    //start job and optionally wait for completion
    node('docker') {
      build job: jobLocation,
              parameters: jobParameters,
              wait: wait,
              propagate: wait
    }
  }
}